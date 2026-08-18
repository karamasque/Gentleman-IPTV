package com.kaynanamtv.data.sync

import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration
import android.content.res.Resources
import com.google.common.truth.Truth.assertThat
import com.kaynanamtv.data.local.DatabaseTransactionRunner
import com.kaynanamtv.data.local.dao.CatalogSyncDao
import com.kaynanamtv.data.local.dao.CategoryDao
import com.kaynanamtv.data.local.dao.ChannelDao
import com.kaynanamtv.data.local.dao.EpisodeDao
import com.kaynanamtv.data.local.dao.MovieCategoryHydrationDao
import com.kaynanamtv.data.local.dao.MovieDao
import com.kaynanamtv.data.local.dao.ProgramDao
import com.kaynanamtv.data.local.dao.ProviderDao
import com.kaynanamtv.data.local.dao.SeriesCategoryHydrationDao
import com.kaynanamtv.data.local.dao.SeriesDao
import com.kaynanamtv.data.local.dao.TmdbIdentityDao
import com.kaynanamtv.data.local.dao.XtreamContentIndexDao
import com.kaynanamtv.data.local.dao.XtreamIndexJobDao
import com.kaynanamtv.data.local.dao.XtreamLiveOnboardingDao
import com.kaynanamtv.data.local.entity.CategoryEntity
import com.kaynanamtv.data.local.entity.ProviderEntity
import com.kaynanamtv.data.parser.M3uParser
import com.kaynanamtv.data.preferences.PreferencesRepository
import com.kaynanamtv.data.remote.jellyfin.JellyfinProvider
import com.kaynanamtv.data.remote.stalker.StalkerApiService
import com.kaynanamtv.data.security.CredentialCrypto
import com.kaynanamtv.domain.model.ContentType
import com.kaynanamtv.domain.model.EpgResolutionSummary
import com.kaynanamtv.domain.model.ProviderEpgSyncMode
import com.kaynanamtv.domain.model.ProviderType
import com.kaynanamtv.domain.model.ProviderXtreamLiveSyncMode
import com.kaynanamtv.domain.model.Result
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

/**
 * Regression test for Fast Live Sync:
 * Verifies that initial onboarding sync returns immediately after Live TV streams are committed,
 * emitting COMPLETED state and releasing the UI to Live TV without waiting for Movies, Series, or EPG upfront.
 */
class FastLiveSyncRegressionTest {

    private class FakeXtreamBackend {
        fun okHttpClient(): OkHttpClient {
            return OkHttpClient.Builder()
                .addInterceptor { chain ->
                    val request = chain.request()
                    val action = request.url.queryParameter("action").orEmpty()
                    val body = when (action) {
                        "get_live_categories" -> """[{"category_id":"1","category_name":"Sports"}]"""
                        "get_live_streams" -> """[{"stream_id":101,"name":"Test Sports HD","category_id":"1","stream_type":"live"}]"""
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
    private val backend = FakeXtreamBackend()
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
                listOf(CategoryEntity(id = 1L, providerId = 1L, categoryId = 100L, name = "General", type = ContentType.LIVE))
            )
            whenever(channelDao.getCount(any())).thenReturn(flowOf(1))
            whenever(channelDao.getByProviderSync(any())).thenReturn(emptyList())
            whenever(movieDao.getCount(any())).thenReturn(flowOf(0))
            whenever(seriesDao.getCount(any())).thenReturn(flowOf(0))
        }
    }

    private fun sampleProvider(): ProviderEntity = ProviderEntity(
        id = 1L,
        name = "Test Provider",
        type = ProviderType.XTREAM_CODES,
        serverUrl = "http://test-provider.com:8080",
        username = "testuser",
        password = "testpass",
        epgSyncMode = ProviderEpgSyncMode.BACKGROUND,

        xtreamLiveSyncMode = ProviderXtreamLiveSyncMode.AUTO,
        isActive = true
    )

    @Test
    fun `initial onboarding sync completes immediately after live catalog without awaiting movies or series`() = runTest {
        val providerDao = FakeProviderDao(sampleProvider())
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
    }
}
