package com.kaynanamtv.data.repository

import com.google.common.truth.Truth.assertThat
import com.kaynanamtv.data.local.DatabaseTransactionRunner
import com.kaynanamtv.data.local.dao.EpisodeDao
import com.kaynanamtv.data.local.dao.MovieDao
import com.kaynanamtv.data.local.dao.PlaybackHistoryDao
import com.kaynanamtv.data.local.dao.SeriesDao
import com.kaynanamtv.data.local.entity.MovieEntity
import com.kaynanamtv.data.local.entity.PlaybackHistoryLiteEntity
import com.kaynanamtv.data.preferences.PreferencesRepository
import com.kaynanamtv.domain.model.ContentType
import com.kaynanamtv.domain.model.PlaybackHistory
import com.kaynanamtv.domain.model.PlaybackWatchedStatus
import com.kaynanamtv.domain.usecase.ContinueWatchingResult
import com.kaynanamtv.domain.usecase.ContinueWatchingScope
import com.kaynanamtv.domain.usecase.GetContinueWatching
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class ContinueWatchingStreamIdUnificationTest {

    private val historyDao: PlaybackHistoryDao = mock()
    private val preferencesRepository: PreferencesRepository = mock()
    private val movieDao: MovieDao = mock()
    private val episodeDao: EpisodeDao = mock()
    private val seriesDao: SeriesDao = mock()
    private val transactionRunner = object : DatabaseTransactionRunner {
        override suspend fun <T> inTransaction(block: suspend () -> T): T = block()
    }

    private fun repository(): PlaybackHistoryRepositoryImpl {
        whenever(preferencesRepository.isIncognitoMode).thenReturn(flowOf(false))
        return PlaybackHistoryRepositoryImpl(
            dao = historyDao,
            preferencesRepository = preferencesRepository,
            movieDao = movieDao,
            episodeDao = episodeDao,
            seriesDao = seriesDao,
            transactionRunner = transactionRunner
        )
    }

    @Test
    fun `test 1 - same movie from playback_history and catalog progress deduplicates to single card with streamId`() = runTest {
        val providerId = 1L
        val streamId = 998877L
        val localRoomId = 42L

        val persistedHistory = PlaybackHistoryLiteEntity(
            contentId = streamId,
            contentType = ContentType.MOVIE,
            providerId = providerId,
            title = "Efes'in Sirri (2026)",
            posterUrl = "https://server.test/poster1.jpg",
            streamUrl = "http://server.test/movie/user/pass/998877.mp4",
            resumePositionMs = 500_000L,
            totalDurationMs = 6_000_000L,
            lastWatchedAt = 1000L,
            watchedStatus = "IN_PROGRESS"
        )

        val catalogMovie = MovieEntity(
            id = localRoomId,
            streamId = streamId,
            name = "Efes'in Sirri (2026)",
            posterUrl = "https://server.test/poster1.jpg",
            backdropUrl = null,
            streamUrl = "http://server.test/movie/user/pass/998877.mp4",
            watchProgress = 480_000L,
            durationSeconds = 6000,
            lastWatchedAt = 900L,
            addedAt = 100L,
            providerId = providerId
        )

        whenever(historyDao.getContinueWatchingCandidatesByProvider(providerId, 24))
            .thenReturn(flowOf(listOf(persistedHistory)))
        whenever(movieDao.observeMoviesWithWatchProgressByProvider(providerId, 24))
            .thenReturn(flowOf(listOf(catalogMovie)))
        whenever(episodeDao.observeEpisodesWithWatchProgressByProvider(providerId, 24))
            .thenReturn(flowOf(emptyList()))

        val repo = repository()
        val getCw = GetContinueWatching(repo)

        val result = getCw(providerId = providerId, limit = 24, scope = ContinueWatchingScope.MOVIES).first()
        assertThat(result).isInstanceOf(ContinueWatchingResult.Items::class.java)

        val items = (result as ContinueWatchingResult.Items).items
        assertThat(items).hasSize(1)
        assertThat(items.first().contentId).isEqualTo(streamId)
        assertThat(items.first().title).isEqualTo("Efes'in Sirri (2026)")
        assertThat(items.first().resumePositionMs).isEqualTo(500_000L)
    }

    @Test
    fun `test 2 - different streamIds for TR dub vs original remain two distinct cards`() = runTest {
        val providerId = 1L
        val streamIdTr = 1001L
        val streamIdOrg = 1002L

        val trHistory = PlaybackHistoryLiteEntity(
            contentId = streamIdTr,
            contentType = ContentType.MOVIE,
            providerId = providerId,
            title = "Gladiator (TR Dublaj)",
            posterUrl = "https://server.test/poster_tr.jpg",
            streamUrl = "http://server.test/movie/user/pass/1001.mp4",
            resumePositionMs = 200_000L,
            totalDurationMs = 7_000_000L,
            lastWatchedAt = 2000L,
            watchedStatus = "IN_PROGRESS"
        )

        val orgHistory = PlaybackHistoryLiteEntity(
            contentId = streamIdOrg,
            contentType = ContentType.MOVIE,
            providerId = providerId,
            title = "Gladiator (Orijinal)",
            posterUrl = "https://server.test/poster_org.jpg",
            streamUrl = "http://server.test/movie/user/pass/1002.mp4",
            resumePositionMs = 150_000L,
            totalDurationMs = 7_000_000L,
            lastWatchedAt = 1000L,
            watchedStatus = "IN_PROGRESS"
        )

        whenever(historyDao.getContinueWatchingCandidatesByProvider(providerId, 24))
            .thenReturn(flowOf(listOf(trHistory, orgHistory)))
        whenever(movieDao.observeMoviesWithWatchProgressByProvider(providerId, 24))
            .thenReturn(flowOf(emptyList()))
        whenever(episodeDao.observeEpisodesWithWatchProgressByProvider(providerId, 24))
            .thenReturn(flowOf(emptyList()))

        val repo = repository()
        val getCw = GetContinueWatching(repo)

        val result = getCw(providerId = providerId, limit = 24, scope = ContinueWatchingScope.MOVIES).first()
        val items = (result as ContinueWatchingResult.Items).items
        assertThat(items).hasSize(2)
        assertThat(items.map { it.contentId }).containsExactly(streamIdTr, streamIdOrg).inOrder()
    }

    @Test
    fun `test 3 - Efes in Sirri 4 candidate sources dedup to exactly 1 card`() = runTest {
        val providerId = 1L
        val streamId = 554433L

        // Source 1: persisted history with stream_id in URL and contentId
        val s1 = PlaybackHistoryLiteEntity(
            contentId = streamId,
            contentType = ContentType.MOVIE,
            providerId = providerId,
            title = "Efes'in Sirri (2026)",
            posterUrl = "https://server.test/p.jpg",
            streamUrl = "http://server.test/movie/user/pass/554433.mp4",
            resumePositionMs = 600_000L,
            totalDurationMs = 5_400_000L,
            lastWatchedAt = 3000L,
            watchedStatus = "IN_PROGRESS"
        )

        // Source 2: catalog movie watch progress
        val s2 = MovieEntity(
            id = 99L,
            streamId = streamId,
            name = "Efes'in Sirri (2026)",
            posterUrl = "https://server.test/p.jpg",
            backdropUrl = null,
            streamUrl = "http://server.test/movie/user/pass/554433.mp4",
            watchProgress = 590_000L,
            durationSeconds = 5400,
            lastWatchedAt = 2900L,
            addedAt = 100L,
            providerId = providerId
        )

        // Source 3: persisted history with streamUrl empty (contentId is streamId)
        val s3 = PlaybackHistoryLiteEntity(
            contentId = streamId,
            contentType = ContentType.MOVIE,
            providerId = providerId,
            title = "Efes'in Sirri (2026)",
            posterUrl = "https://server.test/p.jpg",
            streamUrl = "",
            resumePositionMs = 580_000L,
            totalDurationMs = 5_400_000L,
            lastWatchedAt = 2500L,
            watchedStatus = "IN_PROGRESS"
        )

        whenever(historyDao.getContinueWatchingCandidatesByProvider(providerId, 24))
            .thenReturn(flowOf(listOf(s1, s3)))
        whenever(movieDao.observeMoviesWithWatchProgressByProvider(providerId, 24))
            .thenReturn(flowOf(listOf(s2)))
        whenever(episodeDao.observeEpisodesWithWatchProgressByProvider(providerId, 24))
            .thenReturn(flowOf(emptyList()))

        val repo = repository()
        val getCw = GetContinueWatching(repo)

        val result = getCw(providerId = providerId, limit = 24, scope = ContinueWatchingScope.MOVIES).first()
        val items = (result as ContinueWatchingResult.Items).items
        assertThat(items).hasSize(1)
        assertThat(items.first().contentId).isEqualTo(streamId)
        assertThat(items.first().resumePositionMs).isEqualTo(600_000L)
    }
}
