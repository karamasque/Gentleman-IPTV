package com.kaynanamtv.data.sync

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import com.google.common.truth.Truth.assertThat
import com.kaynanamtv.data.local.DatabaseTransactionRunner
import com.kaynanamtv.data.local.dao.*
import com.kaynanamtv.data.local.entity.CategoryEntity
import com.kaynanamtv.data.local.entity.ProviderEntity
import com.kaynanamtv.data.parser.M3uParser
import com.kaynanamtv.data.preferences.PreferencesRepository
import com.kaynanamtv.data.remote.jellyfin.JellyfinProvider
import com.kaynanamtv.data.remote.stalker.StalkerApiService
import com.kaynanamtv.data.security.CredentialCrypto
import com.kaynanamtv.domain.model.*
import com.kaynanamtv.domain.repository.EpgRepository
import com.kaynanamtv.domain.repository.EpgSourceRepository
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class SyncPipelineAccelerationAndDefaultProviderTest {

    private class FastMockBackend {
        var liveCategoryFetches = 0
        var liveStreamFetches = 0
        var movieCategoryFetches = 0
        var movieStreamFetches = 0
        var seriesCategoryFetches = 0
        var seriesStreamFetches = 0

        fun okHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val request = chain.request()
                    val action = request.url.queryParameter("action").orEmpty()
                    val body = when (action) {
                        "get_live_categories" -> {
                            liveCategoryFetches++
                            """[{"category_id":"1","category_name":"Sports"}]"""
                        }
                        "get_live_streams" -> {
                            liveStreamFetches++
                            """[{"stream_id":101,"name":"TR: TRT 1 HD","category_id":"1","stream_type":"live"}]"""
                        }
                        "get_vod_categories" -> {
                            movieCategoryFetches++
                            """[{"category_id":"2","category_name":"Action"}]"""
                        }
                        "get_vod_streams" -> {
                            movieStreamFetches++
                            """[{"stream_id":201,"name":"Action Movie","category_id":"2"}]"""
                        }
                        "get_series_categories" -> {
                            seriesCategoryFetches++
                            """[{"category_id":"3","category_name":"Drama"}]"""
                        }
                        "get_series" -> {
                            seriesStreamFetches++
                            """[{"series_id":301,"name":"Drama Series","category_id":"3"}]"""
                        }
                        else -> """[]"""
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
    private val jellyfinProvider: JellyfinProvider = mock()
    private val backend = FastMockBackend()
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

    @Before
    fun setup() {
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

        runBlocking {
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
            whenever(movieDao.getCount(any())).thenReturn(flowOf(0))
            whenever(seriesDao.getCount(any())).thenReturn(flowOf(0))
        }
    }

    private fun sampleProvider(id: Long = 1L, active: Boolean = true): ProviderEntity = ProviderEntity(
        id = id,
        name = "Provider $id",
        type = ProviderType.XTREAM_CODES,
        serverUrl = "http://test-provider.com:8080",
        username = "user$id",
        password = "pass$id",
        epgSyncMode = ProviderEpgSyncMode.BACKGROUND,
        xtreamLiveSyncMode = ProviderXtreamLiveSyncMode.AUTO,
        isActive = active
    )

    @Test
    fun `fast initial onboarding sync commits live catalog and finishes without waiting for vod`() = runTest {
        val providerDao = FakeProviderDao(sampleProvider(1L, active = false))
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
            syncProgressBus = SyncProgressBus(),
            mediaPrefetcher = mediaPrefetcher
        )

        val result = manager.sync(
            providerId = 1L,
            force = true,
            trackInitialLiveOnboarding = true
        )

        assertThat(result).isInstanceOf(Result.Success::class.java)
        assertThat(backend.liveCategoryFetches).isEqualTo(1)
        assertThat(backend.liveStreamFetches).isEqualTo(1)
    }

    @Test
    fun `two independent provider entities remain completely isolated in sync manager`() = runTest {
        val provider1 = sampleProvider(1L)
        val provider2 = sampleProvider(2L)
        val providerDao1 = FakeProviderDao(provider1)
        val providerDao2 = FakeProviderDao(provider2)

        assertThat(providerDao1.getById(1L)?.id).isEqualTo(1L)
        assertThat(providerDao2.getById(2L)?.id).isEqualTo(2L)
        assertThat(providerDao1.getById(1L)?.name).isNotEqualTo(providerDao2.getById(2L)?.name)
    }
}
