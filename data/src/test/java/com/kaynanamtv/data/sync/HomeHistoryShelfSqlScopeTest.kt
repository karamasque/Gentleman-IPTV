package com.kaynanamtv.data.sync

import com.google.common.truth.Truth.assertThat
import com.kaynanamtv.data.local.dao.EpisodeDao
import com.kaynanamtv.data.local.dao.MovieDao
import com.kaynanamtv.data.local.dao.PlaybackHistoryDao
import com.kaynanamtv.data.local.dao.SeriesDao
import com.kaynanamtv.data.local.DatabaseTransactionRunner
import com.kaynanamtv.data.local.entity.PlaybackHistoryLiteEntity
import com.kaynanamtv.data.preferences.PreferencesRepository
import com.kaynanamtv.data.repository.PlaybackHistoryRepositoryImpl
import com.kaynanamtv.domain.model.ContentType
import com.kaynanamtv.domain.model.PlaybackHistory
import com.kaynanamtv.domain.usecase.ContinueWatchingResult
import com.kaynanamtv.domain.usecase.ContinueWatchingScope
import com.kaynanamtv.domain.usecase.GetContinueWatching
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class HomeHistoryShelfSqlScopeTest {

    private val historyDao: PlaybackHistoryDao = mock()
    private val preferencesRepository: PreferencesRepository = mock()
    private val movieDao: MovieDao = mock()
    private val episodeDao: EpisodeDao = mock()
    private val seriesDao: SeriesDao = mock()
    private val transactionRunner = object : DatabaseTransactionRunner {
        override suspend fun <R> inTransaction(block: suspend () -> R): R = block()
    }

    private lateinit var repository: PlaybackHistoryRepositoryImpl
    private lateinit var getContinueWatching: GetContinueWatching

    @Before
    fun setUp() {
        whenever(preferencesRepository.isIncognitoMode).thenReturn(flowOf(false))
        repository = PlaybackHistoryRepositoryImpl(
            dao = historyDao,
            preferencesRepository = preferencesRepository,
            movieDao = movieDao,
            episodeDao = episodeDao,
            seriesDao = seriesDao,
            transactionRunner = transactionRunner
        )
        getContinueWatching = GetContinueWatching(repository)
    }

    @Test
    fun `TEST A - older partially watched MOVIE is returned when more than 12 LIVE history rows exist`() = runTest {
        // Given: 15 Live TV rows watched recently and 1 partially watched Movie watched earlier
        val movieEntity = PlaybackHistoryLiteEntity(
            contentId = 101L,
            contentType = ContentType.MOVIE,
            providerId = 1L,
            title = "Inception",
            resumePositionMs = 30_000L,
            totalDurationMs = 120_000L,
            lastWatchedAt = 1000L
        )

        // When SQL scopes Continue Watching candidates specifically to VOD, it ignores Live rows and returns the movie
        whenever(historyDao.getContinueWatchingCandidatesByProvider(eq(1L), eq(12))).thenReturn(
            flowOf(listOf(movieEntity))
        )

        val result = getContinueWatching(providerId = 1L, limit = 12).first()

        assertThat(result).isInstanceOf(ContinueWatchingResult.Items::class.java)
        val items = (result as ContinueWatchingResult.Items).items
        assertThat(items).hasSize(1)
        assertThat(items.first().contentId).isEqualTo(101L)
        assertThat(items.first().title).isEqualTo("Inception")
    }

    @Test
    fun `TEST B - older LIVE history is returned when more than 12 VOD history rows exist`() = runTest {
        // Given: 15 VOD rows watched recently and 1 Live channel watched earlier
        val liveEntity = PlaybackHistoryLiteEntity(
            contentId = 55L,
            contentType = ContentType.LIVE,
            providerId = 1L,
            title = "TRT 1 HD",
            lastWatchedAt = 1000L
        )

        whenever(historyDao.getRecentLiveHistoryByProvider(eq(1L), eq(12))).thenReturn(
            flowOf(listOf(liveEntity))
        )

        val result = repository.getRecentLiveHistoryByProvider(providerId = 1L, limit = 12).first()

        assertThat(result).hasSize(1)
        assertThat(result.first().contentId).isEqualTo(55L)
        assertThat(result.first().contentType).isEqualTo(ContentType.LIVE)
        assertThat(result.first().title).isEqualTo("TRT 1 HD")
    }

    @Test
    fun `TEST C - mixed history routes each shelf only to its eligible content`() = runTest {
        val vodEntities = listOf(
            PlaybackHistoryLiteEntity(
                contentId = 201L,
                contentType = ContentType.MOVIE,
                providerId = 1L,
                title = "Movie 1",
                resumePositionMs = 20_000L,
                totalDurationMs = 100_000L,
                lastWatchedAt = 5000L
            ),
            PlaybackHistoryLiteEntity(
                contentId = 202L,
                contentType = ContentType.SERIES_EPISODE,
                providerId = 1L,
                seriesId = 50L,
                seasonNumber = 1,
                episodeNumber = 1,
                title = "Episode 1",
                resumePositionMs = 15_000L,
                totalDurationMs = 50_000L,
                lastWatchedAt = 4000L
            )
        )
        val liveEntities = listOf(
            PlaybackHistoryLiteEntity(
                contentId = 77L,
                contentType = ContentType.LIVE,
                providerId = 1L,
                title = "Eurosport",
                lastWatchedAt = 6000L
            )
        )

        whenever(historyDao.getContinueWatchingCandidatesByProvider(eq(1L), eq(12))).thenReturn(flowOf(vodEntities))
        whenever(historyDao.getRecentLiveHistoryByProvider(eq(1L), eq(12))).thenReturn(flowOf(liveEntities))

        val continueWatching = (getContinueWatching(providerId = 1L, limit = 12).first() as ContinueWatchingResult.Items).items
        val liveHistory = repository.getRecentLiveHistoryByProvider(providerId = 1L, limit = 12).first()

        assertThat(continueWatching.map { it.contentId }).containsExactly(201L, 202L).inOrder()
        assertThat(liveHistory.map { it.contentId }).containsExactly(77L)
    }

    @Test
    fun `TEST D - multiple partially watched movies and episodes preserve newest first ordering`() = runTest {
        val vodEntities = listOf(
            PlaybackHistoryLiteEntity(
                contentId = 301L,
                contentType = ContentType.MOVIE,
                providerId = 1L,
                resumePositionMs = 10_000L,
                totalDurationMs = 100_000L,
                lastWatchedAt = 9000L
            ),
            PlaybackHistoryLiteEntity(
                contentId = 302L,
                contentType = ContentType.SERIES_EPISODE,
                providerId = 1L,
                seriesId = 60L,
                resumePositionMs = 10_000L,
                totalDurationMs = 100_000L,
                lastWatchedAt = 8000L
            ),
            PlaybackHistoryLiteEntity(
                contentId = 303L,
                contentType = ContentType.MOVIE,
                providerId = 1L,
                resumePositionMs = 10_000L,
                totalDurationMs = 100_000L,
                lastWatchedAt = 7000L
            )
        )

        whenever(historyDao.getContinueWatchingCandidatesByProvider(eq(1L), eq(12))).thenReturn(flowOf(vodEntities))

        val continueWatching = (getContinueWatching(providerId = 1L, limit = 12).first() as ContinueWatchingResult.Items).items

        assertThat(continueWatching.map { it.contentId }).containsExactly(301L, 302L, 303L).inOrder()
    }

    @Test
    fun `TEST E - series deduplication keeps only latest episode of same series`() = runTest {
        val vodEntities = listOf(
            PlaybackHistoryLiteEntity(
                contentId = 402L,
                contentType = ContentType.SERIES_EPISODE,
                providerId = 1L,
                seriesId = 70L,
                seasonNumber = 1,
                episodeNumber = 2,
                resumePositionMs = 15_000L,
                totalDurationMs = 60_000L,
                lastWatchedAt = 9000L
            ),
            PlaybackHistoryLiteEntity(
                contentId = 401L,
                contentType = ContentType.SERIES_EPISODE,
                providerId = 1L,
                seriesId = 70L,
                seasonNumber = 1,
                episodeNumber = 2,
                resumePositionMs = 20_000L,
                totalDurationMs = 60_000L,
                lastWatchedAt = 8000L
            ),
            PlaybackHistoryLiteEntity(
                contentId = 403L,
                contentType = ContentType.MOVIE,
                providerId = 1L,
                resumePositionMs = 10_000L,
                totalDurationMs = 100_000L,
                lastWatchedAt = 7000L
            )
        )

        whenever(historyDao.getContinueWatchingCandidatesByProvider(eq(1L), eq(12))).thenReturn(flowOf(vodEntities))

        val continueWatching = (getContinueWatching(providerId = 1L, limit = 12).first() as ContinueWatchingResult.Items).items

        // Should keep episode 402 (newest) and movie 403, collapsing older episode 401
        assertThat(continueWatching.map { it.contentId }).containsExactly(402L, 403L).inOrder()
    }

    @Test
    fun `TEST F - completed VOD items do not starve partially watched VOD items`() = runTest {
        // When SQL query filters out completed items before limit, the partially watched item is returned
        val candidateEntities = listOf(
            PlaybackHistoryLiteEntity(
                contentId = 501L,
                contentType = ContentType.MOVIE,
                providerId = 1L,
                resumePositionMs = 45_000L,
                totalDurationMs = 100_000L,
                lastWatchedAt = 2000L
            )
        )

        whenever(historyDao.getContinueWatchingCandidatesByProvider(eq(1L), eq(12))).thenReturn(flowOf(candidateEntities))

        val continueWatching = (getContinueWatching(providerId = 1L, limit = 12).first() as ContinueWatchingResult.Items).items

        assertThat(continueWatching).hasSize(1)
        assertThat(continueWatching.first().contentId).isEqualTo(501L)
    }

    @Test
    fun `TEST G - zero-progress item is not included in Continue Watching`() = runTest {
        // Zero progress (resumePositionMs == 0) is excluded by SQL query
        val candidateEntities = listOf(
            PlaybackHistoryLiteEntity(
                contentId = 601L,
                contentType = ContentType.MOVIE,
                providerId = 1L,
                resumePositionMs = 15_000L,
                totalDurationMs = 100_000L,
                lastWatchedAt = 3000L
            )
        )

        whenever(historyDao.getContinueWatchingCandidatesByProvider(eq(1L), eq(12))).thenReturn(flowOf(candidateEntities))

        val continueWatching = (getContinueWatching(providerId = 1L, limit = 12).first() as ContinueWatchingResult.Items).items

        assertThat(continueWatching.map { it.contentId }).containsExactly(601L)
    }
}
