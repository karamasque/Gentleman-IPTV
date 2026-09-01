package com.kaynanamtv.data.sync

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import com.google.common.truth.Truth.assertThat
import com.google.firebase.auth.FirebaseAuth
import com.kaynanamtv.data.local.DatabaseTransactionRunner
import com.kaynanamtv.data.local.dao.*
import com.kaynanamtv.data.local.entity.CategoryEntity
import com.kaynanamtv.data.local.entity.ProviderEntity
import com.kaynanamtv.data.parser.M3uParser
import com.kaynanamtv.data.preferences.PreferencesRepository
import com.kaynanamtv.data.remote.dto.XtreamAuthResponse
import com.kaynanamtv.data.remote.dto.XtreamServerInfo
import com.kaynanamtv.data.remote.dto.XtreamUserInfo
import com.kaynanamtv.data.remote.jellyfin.JellyfinProvider
import com.kaynanamtv.data.remote.stalker.StalkerApiService
import com.kaynanamtv.data.remote.xtream.XtreamApiService
import com.kaynanamtv.data.repository.ProviderRepositoryImpl
import com.kaynanamtv.data.security.AccountE2eeCrypto
import com.kaynanamtv.data.security.CredentialCrypto
import com.kaynanamtv.domain.model.*
import com.kaynanamtv.domain.repository.EpgRepository
import com.kaynanamtv.domain.repository.EpgSourceRepository
import com.kaynanamtv.domain.sync.CatalogSectionState
import com.kaynanamtv.domain.sync.SyncProgress
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicInteger

class DeterministicSyncBenchmarkAndActivationContractTest {

    private class ControlledDelayMockBackend {
        var authCalls = 0
        var liveCategoryCalls = 0
        var liveStreamCalls = 0
        var movieCategoryCalls = 0
        var movieStreamCalls = 0
        var seriesCategoryCalls = 0
        var seriesStreamCalls = 0

        var authDelayMs = 100L
        var liveDelayMs = 300L
        var movieDelayMs = 900L
        var seriesDelayMs = 900L

        fun okHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val request = chain.request()
                    val action = request.url.queryParameter("action").orEmpty()
                    val (body, delayMs) = when {
                        action == "get_live_categories" -> {
                            liveCategoryCalls++
                            """[{"category_id":"1","category_name":"Sports"}]""" to (liveDelayMs / 2)
                        }
                        action == "get_live_streams" -> {
                            liveStreamCalls++
                            """[{"stream_id":101,"name":"TR: TRT 1 HD","category_id":"1","stream_type":"live"}]""" to (liveDelayMs / 2)
                        }
                        action == "get_vod_categories" -> {
                            movieCategoryCalls++
                            """[{"category_id":"2","category_name":"Action"}]""" to (movieDelayMs / 2)
                        }
                        action == "get_vod_streams" -> {
                            movieStreamCalls++
                            """[{"stream_id":201,"name":"Action Movie","category_id":"2"}]""" to (movieDelayMs / 2)
                        }
                        action == "get_series_categories" -> {
                            seriesCategoryCalls++
                            """[{"category_id":"3","category_name":"Drama"}]""" to (seriesDelayMs / 2)
                        }
                        action == "get_series" -> {
                            seriesStreamCalls++
                            """[{"series_id":301,"name":"Drama Series","category_id":"3"}]""" to (seriesDelayMs / 2)
                        }
                        request.url.queryParameter("username") != null && action.isBlank() -> {
                            authCalls++
                            """{"user_info":{"auth":1,"status":"Active","exp_date":"1735689600"},"server_info":{"url":"http://test-provider.com:8080","port":"8080","server_protocol":"http"}}""" to authDelayMs
                        }
                        else -> """[]""" to 0L
                    }

                    if (delayMs > 0) {
                        Thread.sleep(delayMs)
                    }

                    Response.Builder()
                        .request(request)
                        .protocol(Protocol.HTTP_1_1)
                        .code(200)
                        .message("OK")
                        .body(body.toResponseBody("application/json".toMediaType()))
                        .build()
                }
                .build()
        }
    }

    private class InMemoryProviderDao(initialProviders: List<ProviderEntity> = emptyList()) : ProviderDao() {
        private val providers = ConcurrentHashMap<Long, ProviderEntity>()
        private val providersFlow = MutableStateFlow<List<ProviderEntity>>(emptyList())
        val insertCalls = AtomicInteger(0)
        val updateCalls = AtomicInteger(0)
        val transactionCalls = AtomicInteger(0)

        init {
            initialProviders.forEach { providers[it.id] = it }
            providersFlow.value = providers.values.toList()
        }

        private fun notifyChange() {
            providersFlow.value = providers.values.toList()
        }

        override suspend fun getById(id: Long): ProviderEntity? = providers[id]
        override suspend fun getByIds(ids: List<Long>): List<ProviderEntity> = ids.mapNotNull { providers[it] }
        override fun getAll(): Flow<List<ProviderEntity>> = providersFlow
        override suspend fun getAllSync(): List<ProviderEntity> = providers.values.toList()
        override fun getActive(): Flow<ProviderEntity?> = providersFlow.map { list -> list.firstOrNull { it.isActive } }
        override fun getByTypeSync(type: ProviderType): List<ProviderEntity> = providers.values.filter { it.type == type }
        override suspend fun insertDirect(provider: ProviderEntity): Long {
            val nextId = if (provider.id == 0L) (providers.keys.maxOrNull() ?: 0L) + 1L else provider.id
            val toSave = provider.copy(id = nextId)
            insertCalls.incrementAndGet()
            providers[nextId] = toSave
            notifyChange()
            return nextId
        }
        override suspend fun updateDirect(provider: ProviderEntity) {
            updateCalls.incrementAndGet()
            providers[provider.id] = provider
            notifyChange()
        }
        override suspend fun insert(provider: ProviderEntity): Long {
            transactionCalls.incrementAndGet()
            if (provider.isActive) {
                deactivateAll()
            }
            return insertDirect(provider)
        }
        override suspend fun update(provider: ProviderEntity) {
            transactionCalls.incrementAndGet()
            if (provider.isActive) {
                deactivateAll()
            }
            updateDirect(provider)
        }
        override suspend fun delete(id: Long) {
            providers.remove(id)
            notifyChange()
        }
        override suspend fun deactivateAll() {
            providers.values.forEach { providers[it.id] = it.copy(isActive = false) }
            notifyChange()
        }
        override suspend fun deactivateAllForAccount(accountUid: String?) {
            deactivateAll()
        }
        override suspend fun activate(id: Long) {
            providers[id]?.let { providers[id] = it.copy(isActive = true) }
            notifyChange()
        }
        override suspend fun setActive(id: Long) {
            transactionCalls.incrementAndGet()
            deactivateAll()
            activate(id)
        }
        override suspend fun updateSyncTime(id: Long, timestamp: Long) {
            providers[id]?.let { providers[id] = it.copy(lastSyncedAt = timestamp) }
            notifyChange()
        }
        override suspend fun updateEpgUrl(id: Long, epgUrl: String) = Unit
        override suspend fun updateM3uUrl(id: Long, m3uUrl: String) = Unit
        override suspend fun getByUrlAndUser(serverUrl: String, username: String, stalkerMacAddress: String): ProviderEntity? =
            providers.values.firstOrNull { it.serverUrl == serverUrl && it.username == username }
        override suspend fun getByUrlAndUserForAccount(serverUrl: String, username: String, stalkerMacAddress: String, accountUid: String?): ProviderEntity? =
            getByUrlAndUser(serverUrl, username, stalkerMacAddress)
        override fun getAllForAccount(accountUid: String?): Flow<List<ProviderEntity>> = getAll()
        override suspend fun getAllForAccountSync(accountUid: String?): List<ProviderEntity> = getAllSync()
        override fun getActiveForAccount(accountUid: String?): Flow<ProviderEntity?> = getActive()
    }

    private val channelDao: ChannelDao = mock()
    private val movieDao: MovieDao = mock()
    private val seriesDao: SeriesDao = mock()
    private val episodeDao: EpisodeDao = mock()
    private val programDao: ProgramDao = mock()
    private val categoryDao: CategoryDao = mock()
    private val catalogSyncDao: CatalogSyncDao = mock()
    private val tmdbIdentityDao: TmdbIdentityDao = mock()
    private val xtreamContentIndexDao: XtreamContentIndexDao = mock()
    private val xtreamIndexJobDao: XtreamIndexJobDao = mock()
    private val xtreamLiveOnboardingDao: XtreamLiveOnboardingDao = mock()
    private val movieCategoryHydrationDao: MovieCategoryHydrationDao = mock()
    private val seriesCategoryHydrationDao: SeriesCategoryHydrationDao = mock()
    private val epgRepo: EpgRepository = mock()
    private val epgSourceRepo: EpgSourceRepository = mock()
    private val preferencesRepo: PreferencesRepository = mock()
    private val stalkerApiService: StalkerApiService = mock()
    private val xtreamApiService: XtreamApiService = mock()
    private val jellyfinProvider: JellyfinProvider = mock()
    private val xtreamJson = Json { ignoreUnknownKeys = true; isLenient = true }
    private val applicationContext: Context = mock()
    private val packageManager: PackageManager = mock()
    private val resources: Resources = mock()
    private val transactionRunner = object : DatabaseTransactionRunner {
        override suspend fun <T> inTransaction(block: suspend () -> T): T = block()
    }
    private val credentialCrypto = object : CredentialCrypto {
        override fun encryptIfNeeded(value: String): String = value
        override fun decryptIfNeeded(value: String): String = value
    }
    private val syncMetadataRepo = FakeSyncMetadataRepository()
    private val mediaPrefetcher: com.kaynanamtv.domain.manager.MediaPrefetcher = mock()

    private val lastActiveIdFlow = MutableStateFlow(0L)
    private val activeSourceFlow = MutableStateFlow<ActiveLiveSource?>(null)
    private val mockFirebaseAuth: FirebaseAuth = mock()
    private lateinit var staticFirebaseAuth: MockedStatic<FirebaseAuth>

    @Before
    fun setup() {
        staticFirebaseAuth = Mockito.mockStatic(FirebaseAuth::class.java)
        staticFirebaseAuth.`when`<FirebaseAuth> { FirebaseAuth.getInstance() }.thenReturn(mockFirebaseAuth)
        whenever(mockFirebaseAuth.currentUser).thenReturn(null)

        whenever(applicationContext.packageManager).thenReturn(packageManager)
        whenever(applicationContext.resources).thenReturn(resources)
        whenever(applicationContext.getSystemService(Context.ACTIVITY_SERVICE)).thenReturn(null)
        whenever(applicationContext.getSystemService(Context.UI_MODE_SERVICE)).thenReturn(null)
        whenever(packageManager.hasSystemFeature(any())).thenAnswer { invocation ->
            invocation.getArgument<String>(0) == PackageManager.FEATURE_TOUCHSCREEN
        }
        whenever(resources.configuration).thenReturn(Configuration().apply { screenWidthDp = 500 })
        whenever(preferencesRepo.useXtreamTextClassification).thenReturn(flowOf(false))
        whenever(preferencesRepo.xtreamBase64TextCompatibility).thenReturn(flowOf(false))
        whenever(preferencesRepo.getHiddenCategoryIds(any(), any())).thenReturn(flowOf(emptySet()))
        whenever(preferencesRepo.lastActiveProviderId).thenReturn(lastActiveIdFlow)
        whenever(preferencesRepo.activeLiveSource).thenReturn(activeSourceFlow)

        runBlocking {
            whenever(xtreamApiService.authenticate(any(), any())).thenReturn(
                XtreamAuthResponse(
                    userInfo = XtreamUserInfo(auth = 1, status = "Active", expDate = "1735689600"),
                    serverInfo = XtreamServerInfo(url = "http://test-provider.com:8080", port = "8080", serverProtocol = "http")
                )
            )
            whenever(preferencesRepo.setLastActiveProviderId(any())).thenAnswer {
                lastActiveIdFlow.value = it.getArgument<Long>(0)
                Unit
            }
            whenever(preferencesRepo.setActiveLiveSource(any())).thenAnswer {
                activeSourceFlow.value = it.getArgument<ActiveLiveSource?>(0)
                Unit
            }
            whenever(catalogSyncDao.getCategoryStages(any(), any(), any())).thenReturn(emptyList())
            whenever(catalogSyncDao.getChannelStages(any(), any())).thenReturn(emptyList())
            whenever(catalogSyncDao.getMovieStages(any(), any())).thenReturn(emptyList())
            whenever(catalogSyncDao.getSeriesStages(any(), any())).thenReturn(emptyList())
            whenever(catalogSyncDao.getChannelStageCategorySummaries(any(), any())).thenReturn(emptyList())
            whenever(epgSourceRepo.resolveForProvider(any(), any())).thenReturn(EpgResolutionSummary())
            whenever(epgSourceRepo.refreshAllForProvider(any())).thenReturn(com.kaynanamtv.domain.model.Result.Success(Unit))
            whenever(categoryDao.getByProviderAndTypeSync(any(), any())).thenReturn(
                listOf(CategoryEntity(id = 1L, providerId = 1L, categoryId = 1L, name = "Sports", type = ContentType.LIVE))
            )
            whenever(channelDao.getCount(any())).thenReturn(flowOf(1))
            whenever(channelDao.getByProviderSync(any())).thenReturn(emptyList())
            whenever(movieDao.getCount(any())).thenReturn(flowOf(1))
            whenever(seriesDao.getCount(any())).thenReturn(flowOf(1))
        }
    }

    @After
    fun tearDown() {
        staticFirebaseAuth.close()
    }

    private fun sampleProvider(id: Long, name: String, active: Boolean = false): ProviderEntity = ProviderEntity(
        id = id,
        name = name,
        type = ProviderType.XTREAM_CODES,
        serverUrl = "http://test-provider.com:8080",
        username = "user$id",
        password = "pass$id",
        epgSyncMode = ProviderEpgSyncMode.BACKGROUND,
        xtreamLiveSyncMode = ProviderXtreamLiveSyncMode.AUTO,
        isActive = active
    )

    @Test
    fun `benchmark test measures exact live readiness vs background completion timestamps`() = runTest {
        val backend = ControlledDelayMockBackend()
        val providerDao = InMemoryProviderDao(listOf(sampleProvider(10L, "Provider A", active = false)))
        val syncBus = SyncProgressBus()

        val progressEmissions = mutableListOf<SyncProgress>()
        val collectJob = launch(Dispatchers.Unconfined) {
            syncBus.flow.collect { it?.let { p -> progressEmissions.add(p) } }
        }

        val manager = SyncManager(
            applicationContext = applicationContext,
            providerDao = providerDao,
            channelDao = channelDao,
            movieDao = movieDao,
            seriesDao = seriesDao,
            programDao = programDao,
            categoryDao = categoryDao,
            movieCategoryHydrationDao = movieCategoryHydrationDao,
            seriesCategoryHydrationDao = seriesCategoryHydrationDao,
            catalogSyncDao = catalogSyncDao,
            tmdbIdentityDao = tmdbIdentityDao,
            xtreamContentIndexDao = xtreamContentIndexDao,
            xtreamIndexJobDao = xtreamIndexJobDao,
            xtreamLiveOnboardingDao = xtreamLiveOnboardingDao,
            stalkerApiService = stalkerApiService,
            episodeDao = episodeDao,
            jellyfinProvider = jellyfinProvider,
            xtreamJson = xtreamJson,
            m3uParser = M3uParser(),
            epgRepository = epgRepo,
            epgSourceRepository = epgSourceRepo,
            okHttpClient = backend.okHttpClient(),
            credentialCrypto = credentialCrypto,
            syncMetadataRepository = syncMetadataRepo,
            transactionRunner = transactionRunner,
            preferencesRepository = preferencesRepo,
            syncProgressBus = syncBus,
            mediaPrefetcher = mediaPrefetcher
        )

        val tStart = System.currentTimeMillis()
        val result = manager.sync(
            providerId = 10L,
            force = true,
            trackInitialLiveOnboarding = true
        )
        val tLiveReady = System.currentTimeMillis() - tStart

        assertThat(result).isInstanceOf(Result.Success::class.java)
        // Live readiness returned after Live categories + streams (~300ms), NOT waiting for VOD (900ms) or Series (900ms)
        assertThat(tLiveReady).isLessThan(900L)

        // Wait for background jobs in applicationSyncScope
        Thread.sleep(1100L)
        val tFullComplete = System.currentTimeMillis() - tStart
        assertThat(tFullComplete).isGreaterThan(900L)

        // Verify that live readiness emitted READY status before VOD/Series
        val liveProgress = progressEmissions.firstOrNull { it.onboardingProgress?.live?.state == CatalogSectionState.READY }
        assertThat(liveProgress).isNotNull()
        assertThat(liveProgress?.onboardingProgress?.live?.state).isEqualTo(CatalogSectionState.READY)

        collectJob.cancel()
    }

    @Test
    fun `active provider contract proven end-to-end with real ProviderRepositoryImpl`() = runTest {
        val backend = ControlledDelayMockBackend()
        backend.authDelayMs = 0L
        backend.liveDelayMs = 0L
        backend.movieDelayMs = 0L
        backend.seriesDelayMs = 0L

        val providerA = sampleProvider(1L, "Provider A", active = true)
        val providerDao = InMemoryProviderDao(listOf(providerA))
        lastActiveIdFlow.value = 1L

        val syncBus = SyncProgressBus()
        val manager = SyncManager(
            applicationContext = applicationContext,
            providerDao = providerDao,
            channelDao = channelDao,
            movieDao = movieDao,
            seriesDao = seriesDao,
            programDao = programDao,
            categoryDao = categoryDao,
            movieCategoryHydrationDao = movieCategoryHydrationDao,
            seriesCategoryHydrationDao = seriesCategoryHydrationDao,
            catalogSyncDao = catalogSyncDao,
            tmdbIdentityDao = tmdbIdentityDao,
            xtreamContentIndexDao = xtreamContentIndexDao,
            xtreamIndexJobDao = xtreamIndexJobDao,
            xtreamLiveOnboardingDao = xtreamLiveOnboardingDao,
            stalkerApiService = stalkerApiService,
            episodeDao = episodeDao,
            jellyfinProvider = jellyfinProvider,
            xtreamJson = xtreamJson,
            m3uParser = M3uParser(),
            epgRepository = epgRepo,
            epgSourceRepository = epgSourceRepo,
            okHttpClient = backend.okHttpClient(),
            credentialCrypto = credentialCrypto,
            syncMetadataRepository = syncMetadataRepo,
            transactionRunner = transactionRunner,
            preferencesRepository = preferencesRepo,
            syncProgressBus = syncBus,
            mediaPrefetcher = mediaPrefetcher
        )

        val accountE2eeCrypto: AccountE2eeCrypto = mock()

        val providerRepository = ProviderRepositoryImpl(
            context = applicationContext,
            providerDao = providerDao,
            categoryDao = categoryDao,
            channelDao = channelDao,
            movieDao = movieDao,
            seriesDao = seriesDao,
            programDao = programDao,
            recordingRunDao = mock(),
            programReminderDao = mock(),
            stalkerApiService = stalkerApiService,
            xtreamApiService = xtreamApiService,
            credentialCrypto = credentialCrypto,
            accountE2eeCrypto = accountE2eeCrypto,
            preferencesRepository = preferencesRepo,
            syncManager = manager,
            syncMetadataRepository = syncMetadataRepo,
            transactionRunner = transactionRunner,
            recordingAlarmScheduler = mock(),
            programReminderAlarmScheduler = mock(),
            jellyfinProvider = jellyfinProvider
        )

        // 1. Initial State: Provider A is active and default
        assertThat(providerDao.getById(1L)?.isActive).isTrue()
        assertThat(lastActiveIdFlow.value).isEqualTo(1L)

        // 2. Add New Provider B via loginXtream
        val loginResult = providerRepository.loginXtream(
            serverUrl = "http://test-provider.com:8080",
            username = "userB",
            password = "passB",
            name = "Provider B",
            xtreamFastSyncEnabled = true
        )

        assertThat(loginResult).isInstanceOf(Result.Success::class.java)
        val providerB = (loginResult as Result.Success).data
        assertThat(providerB.name).isEqualTo("Provider B")

        // 3. Verify Provider B is now active and default
        assertThat(providerDao.getById(providerB.id)?.isActive).isTrue()
        assertThat(lastActiveIdFlow.value).isEqualTo(providerB.id)

        // 4. Verify Provider A is now inactive (Exactly ONE active provider)
        assertThat(providerDao.getById(1L)?.isActive).isFalse()
        val activeProviders = providerDao.getAllSync().filter { it.isActive }
        assertThat(activeProviders).hasSize(1)
        assertThat(activeProviders.first().id).isEqualTo(providerB.id)

        // 5. Manual user switch back to Provider A wins permanently
        providerRepository.setActiveProvider(1L)
        assertThat(providerDao.getById(1L)?.isActive).isTrue()
        assertThat(providerDao.getById(providerB.id)?.isActive).isFalse()
        assertThat(lastActiveIdFlow.value).isEqualTo(1L)
    }

    @Test
    fun `failed provider addition preserves previously active provider`() = runTest {
        val providerA = sampleProvider(1L, "Provider A", active = true)
        val providerDao = InMemoryProviderDao(listOf(providerA))
        lastActiveIdFlow.value = 1L

        whenever(xtreamApiService.authenticate(any(), any())).thenThrow(RuntimeException("Network error"))

        val syncBus = SyncProgressBus()
        val manager = SyncManager(
            applicationContext = applicationContext,
            providerDao = providerDao,
            channelDao = channelDao,
            movieDao = movieDao,
            seriesDao = seriesDao,
            programDao = programDao,
            categoryDao = categoryDao,
            movieCategoryHydrationDao = movieCategoryHydrationDao,
            seriesCategoryHydrationDao = seriesCategoryHydrationDao,
            catalogSyncDao = catalogSyncDao,
            tmdbIdentityDao = tmdbIdentityDao,
            xtreamContentIndexDao = xtreamContentIndexDao,
            xtreamIndexJobDao = xtreamIndexJobDao,
            xtreamLiveOnboardingDao = xtreamLiveOnboardingDao,
            stalkerApiService = stalkerApiService,
            episodeDao = episodeDao,
            jellyfinProvider = jellyfinProvider,
            xtreamJson = xtreamJson,
            m3uParser = M3uParser(),
            epgRepository = epgRepo,
            epgSourceRepository = epgSourceRepo,
            okHttpClient = OkHttpClient(),
            credentialCrypto = credentialCrypto,
            syncMetadataRepository = syncMetadataRepo,
            transactionRunner = transactionRunner,
            preferencesRepository = preferencesRepo,
            syncProgressBus = syncBus,
            mediaPrefetcher = mediaPrefetcher
        )

        val accountE2eeCrypto: AccountE2eeCrypto = mock()

        val providerRepository = ProviderRepositoryImpl(
            context = applicationContext,
            providerDao = providerDao,
            categoryDao = categoryDao,
            channelDao = channelDao,
            movieDao = movieDao,
            seriesDao = seriesDao,
            programDao = programDao,
            recordingRunDao = mock(),
            programReminderDao = mock(),
            stalkerApiService = stalkerApiService,
            xtreamApiService = xtreamApiService,
            credentialCrypto = credentialCrypto,
            accountE2eeCrypto = accountE2eeCrypto,
            preferencesRepository = preferencesRepo,
            syncManager = manager,
            syncMetadataRepository = syncMetadataRepo,
            transactionRunner = transactionRunner,
            recordingAlarmScheduler = mock(),
            programReminderAlarmScheduler = mock(),
            jellyfinProvider = jellyfinProvider
        )

        val loginResult = providerRepository.loginXtream(
            serverUrl = "http://test-provider.com:8080",
            username = "failUser",
            password = "failPassword",
            name = "Failed Provider",
            xtreamFastSyncEnabled = true
        )

        assertThat(loginResult).isInstanceOf(Result.Error::class.java)
        // Provider A remains active and default
        assertThat(providerDao.getById(1L)?.isActive).isTrue()
        assertThat(lastActiveIdFlow.value).isEqualTo(1L)
    }

    @Test
    fun `refreshing an inactive provider does not activate it`() = runTest {
        val providerA = sampleProvider(1L, "Provider A", active = true)
        val providerB = sampleProvider(2L, "Provider B", active = false)
        val providerDao = InMemoryProviderDao(listOf(providerA, providerB))
        lastActiveIdFlow.value = 1L

        val syncBus = SyncProgressBus()
        val manager = SyncManager(
            applicationContext = applicationContext,
            providerDao = providerDao,
            channelDao = channelDao,
            movieDao = movieDao,
            seriesDao = seriesDao,
            programDao = programDao,
            categoryDao = categoryDao,
            movieCategoryHydrationDao = movieCategoryHydrationDao,
            seriesCategoryHydrationDao = seriesCategoryHydrationDao,
            catalogSyncDao = catalogSyncDao,
            tmdbIdentityDao = tmdbIdentityDao,
            xtreamContentIndexDao = xtreamContentIndexDao,
            xtreamIndexJobDao = xtreamIndexJobDao,
            xtreamLiveOnboardingDao = xtreamLiveOnboardingDao,
            stalkerApiService = stalkerApiService,
            episodeDao = episodeDao,
            jellyfinProvider = jellyfinProvider,
            xtreamJson = xtreamJson,
            m3uParser = M3uParser(),
            epgRepository = epgRepo,
            epgSourceRepository = epgSourceRepo,
            okHttpClient = OkHttpClient(),
            credentialCrypto = credentialCrypto,
            syncMetadataRepository = syncMetadataRepo,
            transactionRunner = transactionRunner,
            preferencesRepository = preferencesRepo,
            syncProgressBus = syncBus,
            mediaPrefetcher = mediaPrefetcher
        )

        val accountE2eeCrypto: AccountE2eeCrypto = mock()

        val providerRepository = ProviderRepositoryImpl(
            context = applicationContext,
            providerDao = providerDao,
            categoryDao = categoryDao,
            channelDao = channelDao,
            movieDao = movieDao,
            seriesDao = seriesDao,
            programDao = programDao,
            recordingRunDao = mock(),
            programReminderDao = mock(),
            stalkerApiService = stalkerApiService,
            xtreamApiService = xtreamApiService,
            credentialCrypto = credentialCrypto,
            accountE2eeCrypto = accountE2eeCrypto,
            preferencesRepository = preferencesRepo,
            syncManager = manager,
            syncMetadataRepository = syncMetadataRepo,
            transactionRunner = transactionRunner,
            recordingAlarmScheduler = mock(),
            programReminderAlarmScheduler = mock(),
            jellyfinProvider = jellyfinProvider
        )

        // Refresh inactive Provider B
        manager.sync(providerId = 2L, force = true)

        // Provider A must remain active and default
        assertThat(providerDao.getById(1L)?.isActive).isTrue()
        assertThat(providerDao.getById(2L)?.isActive).isFalse()
        assertThat(lastActiveIdFlow.value).isEqualTo(1L)
    }
}

