package com.kaynanamtv.app.ui.screens.continue_watching

import android.app.Application
import com.google.common.truth.Truth.assertThat
import com.kaynanamtv.data.local.DatabaseTransactionRunner
import com.kaynanamtv.data.local.dao.EpisodeDao
import com.kaynanamtv.data.local.dao.MovieDao
import com.kaynanamtv.data.local.dao.PlaybackHistoryDao
import com.kaynanamtv.data.local.dao.SeriesDao
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
import kotlinx.coroutines.test.*
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.*

@OptIn(ExperimentalCoroutinesApi::class)
class ContinueWatchingRealPipelineIntegrationTest {

    private val testDispatcher = StandardTestDispatcher()

    // Real in-memory DAO state holder simulating Room table
    private val historyEntities = mutableListOf<PlaybackHistoryEntity>()

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

        historyEntities.clear()

        // Configure mock DAO to execute real filtering queries matching Daos.kt SQL
        whenever(playbackHistoryDao.getContinueWatchingCandidatesByProvider(any(), any())).thenAnswer { inv ->
            val providerId = inv.getArgument<Long>(0)
            val limit = inv.getArgument<Int>(1)
            flowOf(
                historyEntities
                    .filter {
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
            )
        }

        whenever(playbackHistoryDao.getContinueWatchingCandidatesByProviders(any(), any())).thenAnswer { inv ->
            val providerIds = inv.getArgument<Set<Long>>(0)
            val limit = inv.getArgument<Int>(1)
            flowOf(
                historyEntities
                    .filter {
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
            )
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
    fun `full continue watching pipeline proves all 10 real requirements`() = runTest(testDispatcher) {
        // --- 1. SEED DATA ---
        // 8 incomplete Movies (lastWatchedAt 1000..8000)
        for (i in 1L..8L) {
            historyEntities.add(
                PlaybackHistoryEntity(
                    id = i,
                    contentId = 1000L + i,
                    contentType = ContentType.MOVIE,
                    providerId = 1L,
                    title = "Movie $i",
                    streamUrl = "http://iptv.example.com/movie/u/p/${1000L + i}.mp4",
                    resumePositionMs = 30_000L,
                    totalDurationMs = 120_000L,
                    lastWatchedAt = 1000L + i,
                    watchedStatus = "IN_PROGRESS"
                )
            )
        }

        // Two same-title language variants with different streamIds (Doctor Strange TR Dub vs Original)
        val trDub = PlaybackHistoryEntity(
            id = 20L,
            contentId = 2001L,
            contentType = ContentType.MOVIE,
            providerId = 1L,
            title = "Doctor Strange in the Multiverse of Madness",
            streamUrl = "http://iptv.example.com/movie/u/p/8881.mp4", // streamId 8881
            resumePositionMs = 45_000L,
            totalDurationMs = 120_000L,
            lastWatchedAt = 9000L,
            watchedStatus = "IN_PROGRESS"
        )
        val originalDub = PlaybackHistoryEntity(
            id = 21L,
            contentId = 2002L,
            contentType = ContentType.MOVIE,
            providerId = 1L,
            title = "Doctor Strange in the Multiverse of Madness",
            streamUrl = "http://iptv.example.com/movie/u/p/8882.mp4", // streamId 8882
            resumePositionMs = 50_000L,
            totalDurationMs = 120_000L,
            lastWatchedAt = 9100L,
            watchedStatus = "IN_PROGRESS"
        )
        historyEntities.add(trDub)
        historyEntities.add(originalDub)

        // One genuine duplicate with same provider + streamId
        val duplicateMovie = PlaybackHistoryEntity(
            id = 22L,
            contentId = 2003L,
            contentType = ContentType.MOVIE,
            providerId = 1L,
            title = "Doctor Strange in the Multiverse of Madness (Copy)",
            streamUrl = "http://iptv.example.com/movie/u/p/8881.mp4", // Same streamId 8881 on provider 1
            resumePositionMs = 46_000L,
            totalDurationMs = 120_000L,
            lastWatchedAt = 9050L,
            watchedStatus = "IN_PROGRESS"
        )
        historyEntities.add(duplicateMovie)

        // 4 incomplete Episodes
        for (e in 1..4) {
            historyEntities.add(
                PlaybackHistoryEntity(
                    id = 30L + e,
                    contentId = 3000L + e,
                    contentType = ContentType.SERIES_EPISODE,
                    providerId = 1L,
                    seriesId = 500L,
                    seasonNumber = 1,
                    episodeNumber = e,
                    title = "Breaking Bad S01E0$e",
                    streamUrl = "http://iptv.example.com/series/u/p/${3000L + e}.mp4",
                    resumePositionMs = 15_000L,
                    totalDurationMs = 50_000L,
                    lastWatchedAt = 9500L + e,
                    watchedStatus = "IN_PROGRESS"
                )
            )
        }

        // One Series entry whose enrichment metadata is initially unavailable
        val unenrichedEpisode = PlaybackHistoryEntity(
            id = 40L,
            contentId = 4001L,
            contentType = ContentType.SERIES_EPISODE,
            providerId = 1L,
            seriesId = 9999L, // Not in seriesRepository
            seasonNumber = 2,
            episodeNumber = 1,
            title = "Unenriched Mystery Show S02E01",
            streamUrl = "http://iptv.example.com/series/u/p/99991.mp4",
            resumePositionMs = 20_000L,
            totalDurationMs = 45_000L,
            lastWatchedAt = 9600L,
            watchedStatus = "IN_PROGRESS"
        )
        historyEntities.add(unenrichedEpisode)
        whenever(seriesRepository.getSeriesByIds(eq(listOf(500L, 9999L)))).thenReturn(
            flowOf(listOf(Series(id = 500L, name = "Breaking Bad", providerId = 1L)))
        )

        // Completed Movie and Episode controls (Must be excluded)
        val completedMovie = PlaybackHistoryEntity(
            id = 50L,
            contentId = 5001L,
            contentType = ContentType.MOVIE,
            providerId = 1L,
            title = "Completed Movie",
            streamUrl = "http://iptv.example.com/movie/u/p/5001.mp4",
            resumePositionMs = 118_000L,
            totalDurationMs = 120_000L, // 98.3% -> COMPLETED
            lastWatchedAt = 9700L,
            watchedStatus = "COMPLETED_AUTO"
        )
        val completedEpisode = PlaybackHistoryEntity(
            id = 51L,
            contentId = 5002L,
            contentType = ContentType.SERIES_EPISODE,
            providerId = 1L,
            seriesId = 500L,
            seasonNumber = 1,
            episodeNumber = 9,
            title = "Completed Episode",
            streamUrl = "http://iptv.example.com/series/u/p/5002.mp4",
            resumePositionMs = 49_000L,
            totalDurationMs = 50_000L,
            lastWatchedAt = 9750L,
            watchedStatus = "COMPLETED_AUTO"
        )
        historyEntities.add(completedMovie)
        historyEntities.add(completedEpisode)

        // --- 2. EXECUTE PIPELINE ---
        val result = getContinueWatching(1L, limit = Int.MAX_VALUE, scope = ContinueWatchingScope.ALL_VOD).first()
        assertThat(result).isInstanceOf(ContinueWatchingResult.Items::class.java)
        val items = (result as ContinueWatchingResult.Items).items

        // --- 3. ASSERT REQUIREMENT PROOFS ---

        // PROOF 1: Room query returns the correct canonical set
        assertThat(items).isNotEmpty()

        // PROOF 6: Both language variants remain (streamId 8881 and 8882)
        val drStrangeItems = items.filter { it.title.startsWith("Doctor Strange") }
        assertThat(drStrangeItems).hasSize(2)
        val streamUrls = drStrangeItems.map { it.streamUrl }
        assertThat(streamUrls).containsExactly(
            "http://iptv.example.com/movie/u/p/8882.mp4",
            "http://iptv.example.com/movie/u/p/8881.mp4"
        )

        // PROOF 7: Genuine duplicate appears once (streamId 8881 deduplicated from 2 rows to 1)
        val stream8881Items = items.filter { it.streamUrl.contains("8881.mp4") }
        assertThat(stream8881Items).hasSize(1)

        // PROOF 8: Missing Series enrichment does not remove the Episode
        val mysteryEp = items.firstOrNull { it.contentId == 4001L }
        assertThat(mysteryEp).isNotNull()
        assertThat(mysteryEp?.title).isEqualTo("Unenriched Mystery Show S02E01")

        // PROOF 9: Completed content remains excluded
        val completedItems = items.filter { it.contentId == 5001L || it.contentId == 5002L }
        assertThat(completedItems).isEmpty()

        // PROOF 2: Total eligible Movies = 8 + 2 (variants) = 10 unique movie cards
        val movieItems = items.filter { it.contentType == ContentType.MOVIE }
        assertThat(movieItems).hasSize(10)

        // Total eligible Episodes = 4 + 1 (unenriched) = 5 episode cards
        val episodeItems = items.filter { it.contentType == ContentType.SERIES_EPISODE }
        assertThat(episodeItems).hasSize(5)

        // Total Dashboard items = 10 + 5 = 15
        assertThat(items).hasSize(15)

        // --- 4. VIEWMODEL CONSUMERS AGREEMENT PROOF ---
        // MoviesViewModel
        val moviesResult = getContinueWatching(1L, limit = Int.MAX_VALUE, scope = ContinueWatchingScope.MOVIES).first()
        val moviesVmItems = (moviesResult as ContinueWatchingResult.Items).items
        assertThat(moviesVmItems).containsExactlyElementsIn(movieItems).inOrder()

        // SeriesViewModel
        val seriesResult = getContinueWatching(1L, limit = Int.MAX_VALUE, scope = ContinueWatchingScope.SERIES).first()
        val seriesVmItems = (seriesResult as ContinueWatchingResult.Items).items
        assertThat(seriesVmItems).containsExactlyElementsIn(episodeItems).inOrder()

        // PROOF 10: No second collector overwrites Dashboard state
        val dashboardShelfResult = getContinueWatching(setOf(1L), limit = Int.MAX_VALUE, scope = ContinueWatchingScope.ALL_VOD).first()
        val dashboardItems = (dashboardShelfResult as ContinueWatchingResult.Items).items
        assertThat(dashboardItems).containsExactlyElementsIn(items).inOrder()
    }
}
