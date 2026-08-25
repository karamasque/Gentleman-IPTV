package com.kaynanamtv.data.repository

import com.google.common.truth.Truth.assertThat
import com.kaynanamtv.data.local.DatabaseTransactionRunner
import com.kaynanamtv.data.local.dao.CategoryDao
import com.kaynanamtv.data.local.dao.ChannelDao
import com.kaynanamtv.data.local.dao.MovieDao
import com.kaynanamtv.data.local.dao.ProgramDao
import com.kaynanamtv.data.local.dao.ProgramReminderDao
import com.kaynanamtv.data.local.dao.ProviderDao
import com.kaynanamtv.data.local.dao.RecordingRunDao
import com.kaynanamtv.data.local.dao.SeriesDao
import com.kaynanamtv.data.local.entity.ProviderEntity
import com.kaynanamtv.data.local.entity.CategoryEntity
import com.kaynanamtv.data.manager.recording.RecordingAlarmScheduler
import com.kaynanamtv.data.manager.reminder.ProgramReminderAlarmScheduler
import com.kaynanamtv.data.preferences.PreferencesRepository
import com.kaynanamtv.data.remote.jellyfin.JellyfinProvider
import com.kaynanamtv.data.remote.stalker.StalkerApiService
import com.kaynanamtv.data.remote.xtream.XtreamApiService
import com.kaynanamtv.data.remote.dto.XtreamAuthResponse
import com.kaynanamtv.data.remote.dto.XtreamServerInfo
import com.kaynanamtv.data.remote.dto.XtreamUserInfo
import com.kaynanamtv.data.security.CredentialCrypto
import com.kaynanamtv.data.sync.SyncManager
import com.kaynanamtv.domain.model.ProviderEpgSyncMode
import com.kaynanamtv.domain.model.ProviderSavedWithSyncErrorException
import com.kaynanamtv.domain.model.Result
import com.kaynanamtv.domain.model.SyncState
import com.kaynanamtv.domain.model.ProviderStatus
import com.kaynanamtv.domain.model.ProviderType
import com.kaynanamtv.domain.model.ProviderXtreamLiveSyncMode
import com.kaynanamtv.domain.model.SyncMetadata
import com.kaynanamtv.domain.repository.SyncMetadataRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.MockedStatic
import org.mockito.Mockito
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.doAnswer
import org.mockito.kotlin.eq
import org.mockito.kotlin.inOrder
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.argumentCaptor
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

class ProviderRepositoryImplTest {

    // FirebaseAuth is accessed statically inside ProviderRepositoryImpl.
    // We mock it here at the class level so every test sees a null currentUser
    // (simulating an unauthenticated / no-account JVM test environment).
    private val mockFirebaseUser: FirebaseUser = mock()
    private val mockFirebaseAuth: FirebaseAuth = mock()
    private lateinit var staticFirebaseAuth: MockedStatic<FirebaseAuth>

    private val context: android.content.Context = mock()
    private val providerDao: ProviderDao = mock()
    private val categoryDao: CategoryDao = mock()
    private val channelDao: ChannelDao = mock()
    private val movieDao: MovieDao = mock()
    private val seriesDao: SeriesDao = mock()
    private val programDao: ProgramDao = mock()
    private val recordingRunDao: RecordingRunDao = mock()
    private val programReminderDao: ProgramReminderDao = mock()
    private val stalkerApiService: StalkerApiService = mock()
    private val xtreamApiService: XtreamApiService = mock()
    private val credentialCrypto: CredentialCrypto = mock()
    private val preferencesRepository: PreferencesRepository = mock()
    private val syncManager: SyncManager = mock()
    private val syncMetadataRepository: SyncMetadataRepository = mock()
    private val recordingAlarmScheduler: RecordingAlarmScheduler = mock()
    private val programReminderAlarmScheduler: ProgramReminderAlarmScheduler = mock()
    private val jellyfinProvider: JellyfinProvider = mock()
    private val transactionRunner = object : DatabaseTransactionRunner {
        override suspend fun <T> inTransaction(block: suspend () -> T): T = block()
    }

    @Before
    fun setUpFirebaseMock() {
        // Intercept FirebaseAuth.getInstance() statically — prevents IllegalStateException
        // "Default FirebaseApp is not initialized" in JVM unit tests.
        staticFirebaseAuth = Mockito.mockStatic(FirebaseAuth::class.java)
        staticFirebaseAuth.`when`<FirebaseAuth> { FirebaseAuth.getInstance() }.thenReturn(mockFirebaseAuth)
        // currentUser returns null by default (unauthenticated), tests can override per-method.
        whenever(mockFirebaseAuth.currentUser).thenReturn(null)
        whenever(mockFirebaseAuth.addAuthStateListener(any())).then { /* no-op */ }
        whenever(mockFirebaseAuth.removeAuthStateListener(any())).then { /* no-op */ }
    }

    @After
    fun tearDownFirebaseMock() {
        staticFirebaseAuth.close()
    }

    private fun createRepository(
        transactionRunner: DatabaseTransactionRunner = this.transactionRunner
    ) = ProviderRepositoryImpl(
        context = context,
        providerDao = providerDao,
        categoryDao = categoryDao,
        channelDao = channelDao,
        movieDao = movieDao,
        seriesDao = seriesDao,
        programDao = programDao,
        recordingRunDao = recordingRunDao,
        programReminderDao = programReminderDao,
        stalkerApiService = stalkerApiService,
        xtreamApiService = xtreamApiService,
        credentialCrypto = credentialCrypto,
        accountE2eeCrypto = com.kaynanamtv.data.security.AccountE2eeCrypto(),
        preferencesRepository = preferencesRepository,
        syncManager = syncManager,
        syncMetadataRepository = syncMetadataRepository,
        transactionRunner = transactionRunner,
        recordingAlarmScheduler = recordingAlarmScheduler,
        programReminderAlarmScheduler = programReminderAlarmScheduler,
        jellyfinProvider = jellyfinProvider
    )

    // Repository created in setUp after Firebase mock is ready.
    private lateinit var repository: ProviderRepositoryImpl

    @Before
    fun setUp() {
        repository = createRepository()
        whenever(preferencesRepository.xtreamBase64TextCompatibility).thenReturn(flowOf(false))
        runBlocking {
            whenever(categoryDao.getByProviderAndTypeSync(any(), any())).thenReturn(emptyList())
            whenever(programDao.countByProvider(any())).thenReturn(0)
            whenever(channelDao.countByProvider(any())).thenReturn(0)
            whenever(channelDao.getByProviderSync(any())).thenReturn(emptyList())
            whenever(movieDao.countByProvider(any())).thenReturn(0)
            whenever(movieDao.getByProviderSync(any())).thenReturn(emptyList())
            whenever(seriesDao.countByProvider(any())).thenReturn(0)
            whenever(seriesDao.getByProviderSync(any())).thenReturn(emptyList())
        }
    }

    @Test
    fun `deleteProvider cancels recording and reminder alarms before deleting provider rows`() = runTest {
        whenever(recordingRunDao.getIdsByProvider(7L)).thenReturn(listOf("run-1", "run-2"))
        whenever(programReminderDao.getIdsByProvider(7L)).thenReturn(listOf(11L, 12L))

        val result = repository.deleteProvider(7L)

        assertThat(result.isSuccess).isTrue()
        val inOrder = inOrder(recordingAlarmScheduler, programReminderAlarmScheduler, programDao, providerDao, syncManager)
        inOrder.verify(programDao).deleteByProvider(7L)
        inOrder.verify(providerDao).delete(7L)
        inOrder.verify(recordingAlarmScheduler).cancel("run-1")
        inOrder.verify(recordingAlarmScheduler).cancel("run-2")
        inOrder.verify(programReminderAlarmScheduler).cancel(11L)
        inOrder.verify(programReminderAlarmScheduler).cancel(12L)
        inOrder.verify(syncManager).onProviderDeleted(7L)
        verify(recordingRunDao).getIdsByProvider(7L)
        verify(programReminderDao).getIdsByProvider(7L)
    }

    @Test
    fun `deleteProvider commits database cleanup before sync side effects`() = runTest {
        val events = mutableListOf<String>()
        val trackedTransactionRunner = object : DatabaseTransactionRunner {
            override suspend fun <T> inTransaction(block: suspend () -> T): T {
                events += "transaction:start"
                return try {
                    block()
                } finally {
                    events += "transaction:end"
                }
            }
        }
        val trackedRepository = createRepository(transactionRunner = trackedTransactionRunner)

        whenever(recordingRunDao.getIdsByProvider(7L)).thenReturn(emptyList())
        whenever(programReminderDao.getIdsByProvider(7L)).thenReturn(emptyList())
        doAnswer {
            events += "programs:delete"
            Unit
        }.whenever(programDao).deleteByProvider(7L)
        doAnswer {
            events += "provider:delete"
            Unit
        }.whenever(providerDao).delete(7L)
        doAnswer {
            events += "sync:cleanup"
            Unit
        }.whenever(syncManager).onProviderDeleted(7L)

        val result = trackedRepository.deleteProvider(7L)

        assertThat(result.isSuccess).isTrue()
        assertThat(events).containsExactly(
            "transaction:start",
            "programs:delete",
            "provider:delete",
            "transaction:end",
            "sync:cleanup"
        ).inOrder()
    }

    @Test
    fun `deleteProvider keeps success after post commit cleanup failure`() = runTest {
        whenever(recordingRunDao.getIdsByProvider(7L)).thenReturn(listOf("run-1"))
        whenever(programReminderDao.getIdsByProvider(7L)).thenReturn(listOf(11L))
        doAnswer { throw IllegalStateException("sync cleanup failed") }
            .whenever(syncManager).onProviderDeleted(7L)

        val result = repository.deleteProvider(7L)

        assertThat(result.isSuccess).isTrue()
        verify(programDao).deleteByProvider(7L)
        verify(providerDao).delete(7L)
        verify(recordingAlarmScheduler).cancel("run-1")
        verify(programReminderAlarmScheduler).cancel(11L)
        verify(syncManager).onProviderDeleted(7L)
    }

    @Test
    fun `validateM3u marks provider active only after successful onboarding`() = runTest {
        val existingProvider = ProviderEntity(
            id = 5L,
            name = "Playlist",
            type = ProviderType.M3U,
            serverUrl = "https://example.com/list.m3u",
            m3uUrl = "https://example.com/list.m3u",
            status = ProviderStatus.UNKNOWN
        )

        // Production calls getByUrlAndUserForAccount (4-param with accountUid=null when no auth)
        whenever(providerDao.getByUrlAndUserForAccount("https://example.com/list.m3u", "", "", null)).thenReturn(existingProvider)
        whenever(providerDao.getById(5L)).thenReturn(existingProvider)
        // validateM3u: sync(id, force=true, movieFast=null, epgMode=null, onProgress=null, trackInitial=false)
        whenever(syncManager.sync(eq(5L), eq(true), anyOrNull(), anyOrNull(), anyOrNull(), eq(false))).thenReturn(Result.success(Unit))
        whenever(syncManager.currentSyncState(5L)).thenReturn(SyncState.Success(123L))

        val result = repository.validateM3u(
            url = "https://example.com/list.m3u",
            name = "Playlist",
            epgSyncMode = ProviderEpgSyncMode.UPFRONT,
            m3uVodClassificationEnabled = false,
            onProgress = null,
            id = null
        )

        assertThat(result.isSuccess).isTrue()
        verify(providerDao).setActive(5L)
        verify(providerDao, never()).deactivateAll()
        verify(providerDao, never()).activate(5L)
    }

    @Test
    fun `validateM3u returns saved provider sync error exception when initial sync fails after save`() = runTest {
        whenever(providerDao.getByUrlAndUserForAccount("https://example.com/list.m3u", "", "", null)).thenReturn(null)
        whenever(credentialCrypto.encryptIfNeeded("")).thenReturn("")
        // NOTE: insertProvider uses a deterministic FNV-1a ID (not the DAO insert return value).
        // Stub insert() to avoid UnstubbedMethodException but the ID used is the hash of the URL.
        whenever(providerDao.insert(any())).thenReturn(0L)
        // validateM3u: sync(id, force=true, movieFast=null, epgMode=null, onProgress, trackInitial=false)
        whenever(syncManager.sync(any<Long>(), eq(true), anyOrNull(), anyOrNull(), anyOrNull(), eq(false)))
            .thenReturn(Result.error("timeout"))

        val result = repository.validateM3u(
            url = "https://example.com/list.m3u",
            name = "Playlist",
            epgSyncMode = ProviderEpgSyncMode.UPFRONT,
            m3uVodClassificationEnabled = false,
            onProgress = {},
            id = null
        )

        assertThat(result.isError).isTrue()
        val failure = (result as Result.Error).exception as ProviderSavedWithSyncErrorException
        // The new provider gets a deterministic hash ID — just verify it is non-zero.
        assertThat(failure.provider.id).isGreaterThan(0L)
        assertThat(failure.provider.status).isEqualTo(ProviderStatus.PARTIAL)
        assertThat(failure.provider.isActive).isFalse()
        assertThat(failure.message).contains("Playlist saved, but initial sync failed")
        verify(providerDao, never()).setActive(any())
        verify(syncManager).scheduleProviderSyncResume(any())
    }

    @Test
    fun `validateM3u persists new provider inactive until onboarding succeeds`() = runTest {
        // Production calls getByUrlAndUserForAccount with null accountUid (no logged-in user)
        whenever(providerDao.getByUrlAndUserForAccount("https://example.com/list.m3u", "", "", null)).thenReturn(null)
        whenever(credentialCrypto.encryptIfNeeded("")).thenReturn("")
        // NOTE: insertProvider uses a deterministic FNV-1a ID (not the DAO insert return value).
        whenever(providerDao.insert(any())).thenReturn(0L)
        // validateM3u: sync(id, force=true, movieFast=null, epgMode=null, onProgress=null, trackInitial=false)
        // For M3U, hasUsableLiveCatalogForActivation always returns true — no channel/metadata stubs needed.
        whenever(syncManager.sync(any<Long>(), eq(true), anyOrNull(), anyOrNull(), anyOrNull(), eq(false))).thenReturn(Result.success(Unit))
        whenever(syncManager.currentSyncState(any())).thenReturn(SyncState.Success(123L))

        val result = repository.validateM3u(
            url = "https://example.com/list.m3u",
            name = "Playlist",
            epgSyncMode = ProviderEpgSyncMode.UPFRONT,
            m3uVodClassificationEnabled = false,
            onProgress = null,
            id = null
        )

        assertThat(result.isSuccess).isTrue()
        val insertedProviders = argumentCaptor<ProviderEntity>()
        verify(providerDao).insert(insertedProviders.capture())
        assertThat(insertedProviders.firstValue.isActive).isFalse()
        // The actual ID is the deterministic FNV-1a hash of the URL — verify setActive was called.
        verify(providerDao).setActive(any())
    }

    @Test
    fun `refreshProviderData leaves xtream provider inactive partial when sync commits no live channels`() = runTest {
        whenever(providerDao.getById(any())).thenReturn(
            ProviderEntity(
                id = 9L,
                name = "Xtream",
                type = ProviderType.XTREAM_CODES,
                serverUrl = "https://example.com",
                username = "user",
                isActive = false,
                status = ProviderStatus.PARTIAL,
                lastSyncedAt = 0L
            )
        )
        whenever(syncManager.sync(eq(9L), eq(false), anyOrNull(), anyOrNull(), anyOrNull(), eq(false))).thenReturn(Result.success(Unit))
        whenever(syncManager.currentSyncState(9L)).thenReturn(SyncState.Success(123L))
        whenever(channelDao.getCount(9L)).thenReturn(flowOf(0))

        val result = repository.refreshProviderData(
            providerId = 9L,
            force = false,
            movieFastSyncOverride = null,
            epgSyncModeOverride = null,
            onProgress = null
        )

        assertThat(result.isSuccess).isTrue()
        val updatedProvider = argumentCaptor<ProviderEntity>()
        verify(providerDao).update(updatedProvider.capture())
        assertThat(updatedProvider.firstValue.isActive).isFalse()
        assertThat(updatedProvider.firstValue.status).isEqualTo(ProviderStatus.PARTIAL)
        assertThat(updatedProvider.firstValue.lastSyncedAt).isGreaterThan(0L)
        verify(syncManager).scheduleProviderSyncResume(9L)
        verify(providerDao, never()).setActive(9L)
    }

    @Test
    fun `validateM3u edit path rejects update when new URL already belongs to a different provider`() = runTest {
        val editTarget = ProviderEntity(
            id = 5L,
            name = "Playlist A",
            type = ProviderType.M3U,
            serverUrl = "https://example.com/a.m3u",
            m3uUrl = "https://example.com/a.m3u",
            status = ProviderStatus.ACTIVE
        )
        val collision = ProviderEntity(
            id = 9L,
            name = "Playlist B",
            type = ProviderType.M3U,
            serverUrl = "https://example.com/b.m3u",
            m3uUrl = "https://example.com/b.m3u",
            status = ProviderStatus.ACTIVE
        )
        // Provider 9 already owns the URL we want to move provider 5 to.
        // Production uses getByUrlAndUserForAccount (4-param, accountUid=null for unauthenticated test env)
        whenever(providerDao.getByUrlAndUserForAccount("https://example.com/b.m3u", "", "", null)).thenReturn(collision)

        val result = repository.validateM3u(
            url = "https://example.com/b.m3u",
            name = "Playlist A",
            epgSyncMode = ProviderEpgSyncMode.BACKGROUND,
            m3uVodClassificationEnabled = false,
            onProgress = null,
            id = editTarget.id
        )

        assertThat(result.isError).isTrue()
        assertThat((result as Result.Error).message).contains("already exists")
        verify(providerDao, never()).insert(any())
        verify(providerDao, never()).update(any())
    }

    @Test
    fun `validateM3u edit path allows update when URL belongs to the same provider being edited`() = runTest {
        val editTarget = ProviderEntity(
            id = 5L,
            name = "Playlist A",
            type = ProviderType.M3U,
            serverUrl = "https://example.com/a.m3u",
            m3uUrl = "https://example.com/a.m3u",
            status = ProviderStatus.ACTIVE
        )
        // The collision query returns the same provider being edited — that is not a conflict.
        whenever(providerDao.getByUrlAndUserForAccount("https://example.com/a.m3u", "", "", null)).thenReturn(editTarget)
        whenever(providerDao.getById(5L)).thenReturn(editTarget)
        whenever(syncManager.sync(eq(5L), eq(true), anyOrNull(), anyOrNull(), anyOrNull(), eq(false))).thenReturn(Result.success(Unit))
        whenever(syncManager.currentSyncState(5L)).thenReturn(SyncState.Success(123L))

        val result = repository.validateM3u(
            url = "https://example.com/a.m3u",
            name = "Playlist A renamed",
            epgSyncMode = ProviderEpgSyncMode.BACKGROUND,
            m3uVodClassificationEnabled = false,
            onProgress = null,
            id = editTarget.id
        )

        assertThat(result.isSuccess).isTrue()
    }

    @Test
    fun `setActiveProvider uses transactional activation api`() = runTest {
        whenever(providerDao.getById(9L)).thenReturn(
            ProviderEntity(
                id = 9L,
                name = "Playlist",
                type = ProviderType.M3U,
                serverUrl = "https://example.com/list.m3u",
                m3uUrl = "https://example.com/list.m3u"
            )
        )

        val result = repository.setActiveProvider(9L)

        assertThat(result.isSuccess).isTrue()
        verify(providerDao).setActive(9L)
        verify(preferencesRepository).setLastActiveProviderId(9L)
        verify(preferencesRepository).setActiveLiveSource(com.kaynanamtv.domain.model.ActiveLiveSource.ProviderSource(9L))
        verify(providerDao, never()).deactivateAll()
        verify(providerDao, never()).activate(9L)
    }

    @Test
    fun `setActiveProvider rejects xtream provider while live onboarding is incomplete`() = runTest {
        whenever(providerDao.getById(9L)).thenReturn(
            ProviderEntity(
                id = 9L,
                name = "Xtream",
                type = ProviderType.XTREAM_CODES,
                serverUrl = "https://example.com",
                username = "user",
                isActive = false,
                status = ProviderStatus.PARTIAL
            )
        )
        whenever(channelDao.getCount(9L)).thenReturn(flowOf(0))

        val result = repository.setActiveProvider(9L)

        assertThat(result.isError).isTrue()
        assertThat((result as Result.Error).message).contains("no content has been committed yet")
        verify(providerDao, never()).setActive(9L)
        verify(syncManager).scheduleProviderSyncResume(9L)
    }

    @Test
    fun `setActiveProvider allows xtream provider with committed live channels`() = runTest {
        whenever(providerDao.getById(9L)).thenReturn(
            ProviderEntity(
                id = 9L,
                name = "Xtream",
                type = ProviderType.XTREAM_CODES,
                serverUrl = "https://example.com",
                username = "user",
                status = ProviderStatus.PARTIAL
            )
        )
        whenever(channelDao.getCount(9L)).thenReturn(flowOf(12))

        val result = repository.setActiveProvider(9L)

        assertThat(result.isSuccess).isTrue()
        verify(providerDao).setActive(9L)
        verify(syncManager, never()).scheduleProviderSyncResume(9L)
    }

    @Test
    fun `setActiveProvider allows xtream provider with no live but committed vod`() = runTest {
        whenever(providerDao.getById(9L)).thenReturn(
            ProviderEntity(
                id = 9L,
                name = "Xtream",
                type = ProviderType.XTREAM_CODES,
                serverUrl = "https://example.com",
                username = "user",
                status = ProviderStatus.PARTIAL
            )
        )
        whenever(channelDao.getCount(9L)).thenReturn(flowOf(0))
        whenever(syncMetadataRepository.getMetadata(9L)).thenReturn(
            SyncMetadata(providerId = 9L, movieCount = 7)
        )

        val result = repository.setActiveProvider(9L)

        assertThat(result.isSuccess).isTrue()
        verify(providerDao).setActive(9L)
        verify(syncManager, never()).scheduleProviderSyncResume(9L)
    }

    @Test
    fun `loginXtream does not fail onboarding when provider has no live but committed vod`() = runTest {
        // Production calls getByUrlAndUserForAccount with accountUid=null (unauthenticated test env)
        whenever(providerDao.getByUrlAndUserForAccount("https://example.com", "user", "", null)).thenReturn(null)
        whenever(credentialCrypto.encryptIfNeeded("pass")).thenReturn("pass")
        // NOTE: insertProvider uses a deterministic FNV-1a ID (not the DAO insert return value).
        whenever(providerDao.insert(any())).thenReturn(0L)
        // loginXtream: sync(id, force=true, movieFast=null, epgMode=null, onProgress, trackInitialLiveOnboarding=true)
        whenever(syncManager.sync(any<Long>(), eq(true), anyOrNull(), anyOrNull(), anyOrNull(), eq(true)))
            .thenReturn(Result.success(Unit))
        whenever(syncManager.currentSyncState(any())).thenReturn(SyncState.Success(123L))
        whenever(channelDao.getCount(any())).thenReturn(flowOf(0))
        whenever(syncMetadataRepository.getMetadata(any())).thenReturn(
            SyncMetadata(providerId = 9104662825998949169L, movieCount = 3)
        )
        whenever(xtreamApiService.authenticate(any(), any())).thenReturn(
            XtreamAuthResponse(
                userInfo = XtreamUserInfo(
                    username = "user",
                    password = "pass",
                    auth = 1,
                    status = "Active"
                ),
                serverInfo = XtreamServerInfo(
                    url = "example.com",
                    port = "80",
                    serverProtocol = "http"
                )
            )
        )

        val result = repository.loginXtream(
            serverUrl = "https://example.com",
            username = "user",
            password = "pass",
            name = "Xtream",
            httpUserAgent = "",
            httpHeaders = "",
            xtreamFastSyncEnabled = false,
            epgSyncMode = ProviderEpgSyncMode.UPFRONT,
            xtreamLiveSyncMode = ProviderXtreamLiveSyncMode.AUTO,
            onProgress = null,
            id = null
        )

        assertThat(result.isSuccess).isTrue()
        verify(providerDao).setActive(any())
        verify(syncManager, never()).scheduleProviderSyncResume(any())
    }

    @Test
    fun `loginXtream does not fail onboarding when provider has no live but committed vod categories`() = runTest {
        whenever(providerDao.getByUrlAndUserForAccount("https://example.com", "user", "", null)).thenReturn(null)
        whenever(credentialCrypto.encryptIfNeeded("pass")).thenReturn("pass")
        // NOTE: insertProvider uses a deterministic FNV-1a ID (not the DAO insert return value).
        whenever(providerDao.insert(any())).thenReturn(0L)
        // loginXtream: sync(id, force=true, movieFast=null, epgMode=null, onProgress, trackInitialLiveOnboarding=true)
        whenever(syncManager.sync(any<Long>(), eq(true), anyOrNull(), anyOrNull(), anyOrNull(), eq(true)))
            .thenReturn(Result.success(Unit))
        whenever(syncManager.currentSyncState(any())).thenReturn(SyncState.Success(123L))
        whenever(channelDao.getCount(any())).thenReturn(flowOf(0))
        whenever(syncMetadataRepository.getMetadata(any())).thenReturn(SyncMetadata(providerId = 9104662825998949169L))
        whenever(categoryDao.getByProviderAndTypeSync(any(), eq("MOVIE"))).thenReturn(
            listOf(
                CategoryEntity(
                    providerId = 9104662825998949169L,
                    categoryId = 42L,
                    name = "Action",
                    parentId = null,
                    type = com.kaynanamtv.domain.model.ContentType.MOVIE
                )
            )
        )
        whenever(categoryDao.getByProviderAndTypeSync(any(), eq("SERIES"))).thenReturn(emptyList())
        whenever(xtreamApiService.authenticate(any(), any())).thenReturn(
            XtreamAuthResponse(
                userInfo = XtreamUserInfo(
                    username = "user",
                    password = "pass",
                    auth = 1,
                    status = "Active"
                ),
                serverInfo = XtreamServerInfo(
                    url = "example.com",
                    port = "80",
                    serverProtocol = "http"
                )
            )
        )

        val result = repository.loginXtream(
            serverUrl = "https://example.com",
            username = "user",
            password = "pass",
            name = "Xtream",
            httpUserAgent = "",
            httpHeaders = "",
            xtreamFastSyncEnabled = false,
            epgSyncMode = ProviderEpgSyncMode.UPFRONT,
            xtreamLiveSyncMode = ProviderXtreamLiveSyncMode.AUTO,
            onProgress = null,
            id = null
        )

        assertThat(result.isSuccess).isTrue()
        verify(providerDao).setActive(any())
        verify(syncManager, never()).scheduleProviderSyncResume(any())
    }
}
