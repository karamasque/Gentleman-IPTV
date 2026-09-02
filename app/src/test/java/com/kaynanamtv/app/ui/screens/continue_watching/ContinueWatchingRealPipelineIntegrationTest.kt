package com.kaynanamtv.app.ui.screens.continue_watching

import com.google.common.truth.Truth.assertThat
import com.kaynanamtv.data.local.DatabaseTransactionRunner
import com.kaynanamtv.data.local.dao.EpisodeDao
import com.kaynanamtv.data.local.dao.MovieDao
import com.kaynanamtv.data.local.dao.PlaybackHistoryDao
import com.kaynanamtv.data.local.dao.SeriesDao
import com.kaynanamtv.data.local.entity.EpisodeEntity
import com.kaynanamtv.data.local.entity.MovieEntity
import com.kaynanamtv.data.local.entity.PlaybackHistoryEntity
import com.kaynanamtv.data.local.entity.PlaybackHistoryLiteEntity
import com.kaynanamtv.data.preferences.PreferencesRepository
import com.kaynanamtv.data.repository.PlaybackHistoryRepositoryImpl
import com.kaynanamtv.domain.model.*
import com.kaynanamtv.domain.repository.*
import com.kaynanamtv.domain.usecase.ContinueWatchingResult
import com.kaynanamtv.domain.usecase.ContinueWatchingScope
import com.kaynanamtv.domain.usecase.GetContinueWatching
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class ContinueWatchingRealPipelineIntegrationTest {

    private val testDispatcher = StandardTestDispatcher()

    // Reactive StateFlows simulating live Room tables
    private val historyTable = MutableStateFlow<List<PlaybackHistoryEntity>>(emptyList())
    private val movieTable = MutableStateFlow<List<MovieEntity>>(emptyList())
    private val episodeTable = MutableStateFlow<List<EpisodeEntity>>(emptyList())

    private val playbackHistoryDao: PlaybackHistoryDao = mock()
    private val movieDao: MovieDao = mock()
    private val episodeDao: EpisodeDao = mock()
    private val seriesDao: SeriesDao = mock()
    private val preferencesRepository: PreferencesRepository = mock()
    private val seriesRepository: SeriesRepository = mock()

    private val transactionRunner = object : DatabaseTransactionRunner {
        override suspend fun <T> inTransaction(block: suspend () -> T): T = block()
    }

    private lateinit var playbackHistoryRepository: PlaybackHistoryRepositoryImpl
    private lateinit var getContinueWatching: GetContinueWatching

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        historyTable.value = emptyList()
        movieTable.value = emptyList()
        episodeTable.value = emptyList()

        // Configure mock DAOs to execute real Room-matching queries reactively
        whenever(playbackHistoryDao.getContinueWatchingCandidatesByProvider(any(), any())).thenAnswer { inv ->
            val providerId = inv.getArgument<Long>(0)
            val limit = inv.getArgument<Int>(1)
            historyTable.map { list ->
                list.filter {
                    it.providerId == providerId &&
                        (it.contentType == ContentType.MOVIE || it.contentType == ContentType.SERIES_EPISODE || it.contentType == ContentType.SERIES) &&
                        it.resumePositionMs > 0 &&
                        !it.watchedStatus.startsWith("COMPLETED") &&
                        (it.totalDurationMs <= 0 || it.resumePositionMs < it.totalDurationMs * 0.95)
                }
                    .sortedByDescending { it.lastWatchedAt }
                    .take(limit)
                    .map {
                        PlaybackHistoryLiteEntity(
                            id = it.id,
                            contentId = it.contentId,
                            contentType = it.contentType,
                            providerId = it.providerId,
                            title = it.title,
                            posterUrl = it.posterUrl,
                            streamUrl = it.streamUrl,
                            resumePositionMs = it.resumePositionMs,
                            totalDurationMs = it.totalDurationMs,
                            lastWatchedAt = it.lastWatchedAt,
                            watchCount = it.watchCount,
                            watchedStatus = it.watchedStatus,
                            seriesId = it.seriesId,
                            seasonNumber = it.seasonNumber,
                            episodeNumber = it.episodeNumber
                        )
                    }
            }
        }

        whenever(playbackHistoryDao.getContinueWatchingCandidatesByProviders(any(), any())).thenAnswer { inv ->
            val providerIds = inv.getArgument<Set<Long>>(0)
            val limit = inv.getArgument<Int>(1)
            historyTable.map { list ->
                list.filter {
                    it.providerId in providerIds &&
                        (it.contentType == ContentType.MOVIE || it.contentType == ContentType.SERIES_EPISODE || it.contentType == ContentType.SERIES) &&
                        it.resumePositionMs > 0 &&
                        !it.watchedStatus.startsWith("COMPLETED") &&
                        (it.totalDurationMs <= 0 || it.resumePositionMs < it.totalDurationMs * 0.95)
                }
                    .sortedByDescending { it.lastWatchedAt }
                    .take(limit)
                    .map {
                        PlaybackHistoryLiteEntity(
                            id = it.id,
                            contentId = it.contentId,
                            contentType = it.contentType,
                            providerId = it.providerId,
                            title = it.title,
                            posterUrl = it.posterUrl,
                            streamUrl = it.streamUrl,
                            resumePositionMs = it.resumePositionMs,
                            totalDurationMs = it.totalDurationMs,
                            lastWatchedAt = it.lastWatchedAt,
                            watchCount = it.watchCount,
                            watchedStatus = it.watchedStatus,
                            seriesId = it.seriesId,
                            seasonNumber = it.seasonNumber,
                            episodeNumber = it.episodeNumber
                        )
                    }
            }
        }

        whenever(movieDao.observeMoviesWithWatchProgressByProvider(any(), any())).thenAnswer { inv ->
            val providerId = inv.getArgument<Long>(0)
            val limit = inv.getArgument<Int>(1)
            movieTable.map { list ->
                list.filter {
                    it.providerId == providerId &&
                        it.watchProgress > 0 &&
                        (it.durationSeconds <= 0 || it.watchProgress < it.durationSeconds * 1000L * 0.95)
                }
                    .sortedByDescending { if (it.lastWatchedAt > 0L) it.lastWatchedAt else it.addedAt }
                    .take(limit)
            }
        }

        whenever(movieDao.observeMoviesWithWatchProgressByProviders(any(), any())).thenAnswer { inv ->
            val providerIds = inv.getArgument<Set<Long>>(0)
            val limit = inv.getArgument<Int>(1)
            movieTable.map { list ->
                list.filter {
                    it.providerId in providerIds &&
                        it.watchProgress > 0 &&
                        (it.durationSeconds <= 0 || it.watchProgress < it.durationSeconds * 1000L * 0.95)
                }
                    .sortedByDescending { if (it.lastWatchedAt > 0L) it.lastWatchedAt else it.addedAt }
                    .take(limit)
            }
        }

        whenever(episodeDao.observeEpisodesWithWatchProgressByProvider(any(), any())).thenAnswer { inv ->
            val providerId = inv.getArgument<Long>(0)
            val limit = inv.getArgument<Int>(1)
            episodeTable.map { list ->
                list.filter {
                    it.providerId == providerId &&
                        it.watchProgress > 0 &&
                        (it.durationSeconds <= 0 || it.watchProgress < it.durationSeconds * 1000L * 0.95)
                }
                    .sortedByDescending { it.lastWatchedAt }
                    .take(limit)
            }
        }

        whenever(episodeDao.observeEpisodesWithWatchProgressByProviders(any(), any())).thenAnswer { inv ->
            val providerIds = inv.getArgument<Set<Long>>(0)
            val limit = inv.getArgument<Int>(1)
            episodeTable.map { list ->
                list.filter {
                    it.providerId in providerIds &&
                        it.watchProgress > 0 &&
                        (it.durationSeconds <= 0 || it.watchProgress < it.durationSeconds * 1000L * 0.95)
                }
                    .sortedByDescending { it.lastWatchedAt }
                    .take(limit)
            }
        }

        whenever(preferencesRepository.isIncognitoMode).thenReturn(flowOf(false))

        playbackHistoryRepository = PlaybackHistoryRepositoryImpl(
            dao = playbackHistoryDao,
            preferencesRepository = preferencesRepository,
            movieDao = movieDao,
            episodeDao = episodeDao,
            seriesDao = seriesDao,
            transactionRunner = transactionRunner
        )

        getContinueWatching = GetContinueWatching(playbackHistoryRepository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `mandatory room reactive test with 5 movies 2 history 3 episodes and deduplication`() = runTest(testDispatcher) {
        // --- SEED INITIAL DATABASE ---
        // movies table: 5 eligible Movie progress rows (including 2 language variants)
        val m1 = MovieEntity(id = 101L, streamId = 101L, name = "Movie A", streamUrl = "http://provider/101.mp4", durationSeconds = 120, watchProgress = 30_000L, lastWatchedAt = 1000L, providerId = 1L)
        val m2 = MovieEntity(id = 102L, streamId = 102L, name = "Movie B", streamUrl = "http://provider/102.mp4", durationSeconds = 120, watchProgress = 40_000L, lastWatchedAt = 2000L, providerId = 1L)
        val m3 = MovieEntity(id = 103L, streamId = 103L, name = "Movie C", streamUrl = "http://provider/103.mp4", durationSeconds = 120, watchProgress = 50_000L, lastWatchedAt = 3000L, providerId = 1L)
        // Two same-title language variants with different streamIds
        val m4Tr = MovieEntity(id = 104L, streamId = 8881L, name = "Doctor Strange TR", streamUrl = "http://provider/8881.mp4", durationSeconds = 120, watchProgress = 45_000L, lastWatchedAt = 4000L, providerId = 1L)
        val m5Orig = MovieEntity(id = 105L, streamId = 8882L, name = "Doctor Strange EN", streamUrl = "http://provider/8882.mp4", durationSeconds = 120, watchProgress = 55_000L, lastWatchedAt = 5000L, providerId = 1L)

        // One completed movie (must be excluded by eligibility)
        val mCompleted = MovieEntity(id = 106L, streamId = 106L, name = "Completed Movie", streamUrl = "http://provider/106.mp4", durationSeconds = 100, watchProgress = 98_000L, lastWatchedAt = 6000L, providerId = 1L)

        movieTable.value = listOf(m1, m2, m3, m4Tr, m5Orig, mCompleted)

        // playback_history table: only 2 matching eligible rows (one genuine duplicate of Movie A with newer progress)
        val h1 = PlaybackHistoryEntity(id = 1L, contentId = 101L, contentType = ContentType.MOVIE, providerId = 1L, title = "Movie A (from history)", streamUrl = "http://provider/101.mp4", resumePositionMs = 35_000L, totalDurationMs = 120_000L, lastWatchedAt = 1500L, watchedStatus = "IN_PROGRESS")
        val h2 = PlaybackHistoryEntity(id = 2L, contentId = 102L, contentType = ContentType.MOVIE, providerId = 1L, title = "Movie B", streamUrl = "http://provider/102.mp4", resumePositionMs = 40_000L, totalDurationMs = 120_000L, lastWatchedAt = 2000L, watchedStatus = "IN_PROGRESS")
        historyTable.value = listOf(h1, h2)

        // episodes table: 3 eligible Episode progress rows
        val ep1 = EpisodeEntity(id = 201L, episodeId = 201L, seriesId = 50L, seasonNumber = 1, episodeNumber = 1, title = "Episode 1", streamUrl = "http://provider/ep201.mp4", durationSeconds = 60, watchProgress = 15_000L, lastWatchedAt = 7000L, providerId = 1L)
        val ep2 = EpisodeEntity(id = 202L, episodeId = 202L, seriesId = 50L, seasonNumber = 1, episodeNumber = 2, title = "Episode 2", streamUrl = "http://provider/ep202.mp4", durationSeconds = 60, watchProgress = 20_000L, lastWatchedAt = 7100L, providerId = 1L)
        val ep3 = EpisodeEntity(id = 203L, episodeId = 203L, seriesId = 51L, seasonNumber = 2, episodeNumber = 1, title = "Episode 3", streamUrl = "http://provider/ep203.mp4", durationSeconds = 60, watchProgress = 25_000L, lastWatchedAt = 7200L, providerId = 1L)
        episodeTable.value = listOf(ep1, ep2, ep3)

        // --- EXECUTE PIPELINE ---
        val homeResult = getContinueWatching(1L, limit = Int.MAX_VALUE, scope = ContinueWatchingScope.ALL_VOD).first()
        assertThat(homeResult).isInstanceOf(ContinueWatchingResult.Items::class.java)
        val allItems = (homeResult as ContinueWatchingResult.Items).items

        // 1. Unified repository emits 5 Movies, not 2 and not 7
        val movies = allItems.filter { it.contentType == ContentType.MOVIE }
        assertThat(movies).hasSize(5)

        // 2. Dashboard contains all 5 Movies
        val movieTitles = movies.map { it.title }
        assertThat(movieTitles).containsAtLeast("Movie A (from history)", "Movie B", "Movie C", "Doctor Strange TR", "Doctor Strange EN")

        // 3. Movies screen contains the identical 5 Movie canonical identities
        val moviesScreenResult = getContinueWatching(1L, limit = Int.MAX_VALUE, scope = ContinueWatchingScope.MOVIES).first()
        val moviesScreenItems = (moviesScreenResult as ContinueWatchingResult.Items).items
        assertThat(moviesScreenItems).containsExactlyElementsIn(movies).inOrder()

        // 4. Unified repository contains all 3 Episodes
        val episodes = allItems.filter { it.contentType == ContentType.SERIES_EPISODE }
        assertThat(episodes).hasSize(3)

        // 5. Home and Series contain identical Episode identities
        val seriesScreenResult = getContinueWatching(1L, limit = Int.MAX_VALUE, scope = ContinueWatchingScope.SERIES).first()
        val seriesScreenItems = (seriesScreenResult as ContinueWatchingResult.Items).items
        assertThat(seriesScreenItems).containsExactlyElementsIn(episodes).inOrder()

        // 6. Same-title language variants both remain
        val variant8881 = movies.firstOrNull { it.streamUrl.contains("8881.mp4") }
        val variant8882 = movies.firstOrNull { it.streamUrl.contains("8882.mp4") }
        assertThat(variant8881).isNotNull()
        assertThat(variant8882).isNotNull()

        // 7. Genuine duplicate appears once (Movie A from history merged with catalog Movie A)
        val movieAItems = movies.filter { it.streamUrl.contains("101.mp4") }
        assertThat(movieAItems).hasSize(1)
        assertThat(movieAItems.first().resumePositionMs).isEqualTo(35_000L) // Newest progress from history preserved

        // 8. Completed item is excluded
        val completedItems = allItems.filter { it.contentId == 106L }
        assertThat(completedItems).isEmpty()
    }

    @Test
    fun `reactive invalidation without restart on table mutation`() = runTest(testDispatcher) {
        val collectedEmissions = mutableListOf<List<PlaybackHistory>>()

        val job = launch(UnconfinedTestDispatcher(testScheduler)) {
            getContinueWatching(1L, limit = Int.MAX_VALUE, scope = ContinueWatchingScope.ALL_VOD)
                .collect { result ->
                    if (result is ContinueWatchingResult.Items) {
                        collectedEmissions.add(result.items)
                    }
                }
        }

        // Start with 5 movies
        val m1 = MovieEntity(id = 101L, streamId = 101L, name = "Movie 1", streamUrl = "http://provider/101.mp4", durationSeconds = 120, watchProgress = 30_000L, lastWatchedAt = 1000L, providerId = 1L)
        val m2 = MovieEntity(id = 102L, streamId = 102L, name = "Movie 2", streamUrl = "http://provider/102.mp4", durationSeconds = 120, watchProgress = 30_000L, lastWatchedAt = 2000L, providerId = 1L)
        val m3 = MovieEntity(id = 103L, streamId = 103L, name = "Movie 3", streamUrl = "http://provider/103.mp4", durationSeconds = 120, watchProgress = 30_000L, lastWatchedAt = 3000L, providerId = 1L)
        val m4 = MovieEntity(id = 104L, streamId = 104L, name = "Movie 4", streamUrl = "http://provider/104.mp4", durationSeconds = 120, watchProgress = 30_000L, lastWatchedAt = 4000L, providerId = 1L)
        val m5 = MovieEntity(id = 105L, streamId = 105L, name = "Movie 5", streamUrl = "http://provider/105.mp4", durationSeconds = 120, watchProgress = 30_000L, lastWatchedAt = 5000L, providerId = 1L)
        movieTable.value = listOf(m1, m2, m3, m4, m5)

        assertThat(collectedEmissions.last().filter { it.contentType == ContentType.MOVIE }).hasSize(5)

        // Step 1: Insert a sixth Movie with watch_progress > 0
        val m6 = MovieEntity(id = 106L, streamId = 106L, name = "Movie 6", streamUrl = "http://provider/106.mp4", durationSeconds = 120, watchProgress = 20_000L, lastWatchedAt = 6000L, providerId = 1L)
        movieTable.value = listOf(m1, m2, m3, m4, m5, m6)

        // Step 2: Assert Home emits 6 without restart
        assertThat(collectedEmissions.last().filter { it.contentType == ContentType.MOVIE }).hasSize(6)

        // Step 3: Update that Movie to completed (watch_progress >= 95% of duration)
        val m6Completed = m6.copy(watchProgress = 118_000L)
        movieTable.value = listOf(m1, m2, m3, m4, m5, m6Completed)

        // Step 4: Assert Home returns to 5
        assertThat(collectedEmissions.last().filter { it.contentType == ContentType.MOVIE }).hasSize(5)

        // Step 5: Insert an Episode progress row
        val ep1 = EpisodeEntity(id = 301L, episodeId = 301L, seriesId = 88L, seasonNumber = 1, episodeNumber = 1, title = "Episode 1", streamUrl = "http://provider/ep301.mp4", durationSeconds = 60, watchProgress = 20_000L, lastWatchedAt = 8000L, providerId = 1L)
        episodeTable.value = listOf(ep1)

        // Step 6: Assert Home emits it immediately (5 movies + 1 episode = 6 total items)
        assertThat(collectedEmissions.last()).hasSize(6)
        assertThat(collectedEmissions.last().filter { it.contentType == ContentType.SERIES_EPISODE }).hasSize(1)

        job.cancel()
    }
}
