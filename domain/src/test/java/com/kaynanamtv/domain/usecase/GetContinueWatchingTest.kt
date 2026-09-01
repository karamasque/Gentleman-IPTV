package com.kaynanamtv.domain.usecase

import com.google.common.truth.Truth.assertThat
import com.kaynanamtv.domain.model.ContentType
import com.kaynanamtv.domain.model.PlaybackHistory
import com.kaynanamtv.domain.repository.PlaybackHistoryRepository
import java.io.IOException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

class GetContinueWatchingTest {

    @Test
    fun collapses_multiple_episode_entries_into_one_series_resume() = runTest {
        val useCase = GetContinueWatching(
            playbackHistoryRepository = FakePlaybackHistoryRepository(
                history = listOf(
                    history(contentId = 21L, type = ContentType.SERIES_EPISODE, seriesId = 7L, lastWatchedAt = 300L, resumePositionMs = 50_000L),
                    history(contentId = 20L, type = ContentType.SERIES_EPISODE, seriesId = 7L, lastWatchedAt = 200L, resumePositionMs = 40_000L),
                    history(contentId = 11L, type = ContentType.MOVIE, lastWatchedAt = 100L, resumePositionMs = 10_000L)
                )
            )
        )

        val result = useCase(providerId = 1L, limit = 5).collectOnce()

        assertThat(result).hasSize(2)
        assertThat(result.first().contentId).isEqualTo(21L)
        assertThat(result.last().contentId).isEqualTo(11L)
    }

    @Test
    fun movie_scope_keeps_only_movies() = runTest {
        val useCase = GetContinueWatching(
            playbackHistoryRepository = FakePlaybackHistoryRepository(
                history = listOf(
                    history(contentId = 1L, type = ContentType.MOVIE, lastWatchedAt = 300L, resumePositionMs = 15_000L),
                    history(contentId = 2L, type = ContentType.SERIES_EPISODE, seriesId = 9L, lastWatchedAt = 200L, resumePositionMs = 15_000L)
                )
            )
        )

        val result = useCase(providerId = 1L, scope = ContinueWatchingScope.MOVIES).collectOnce()

        assertThat(result.map { it.contentId }).containsExactly(1L)
    }

    @Test
    fun require_resume_position_filters_out_unstarted_movies_and_episodes() = runTest {
        val useCase = GetContinueWatching(
            playbackHistoryRepository = FakePlaybackHistoryRepository(
                history = listOf(
                    history(contentId = 1L, type = ContentType.MOVIE, lastWatchedAt = 400L, resumePositionMs = 0L),
                    history(contentId = 2L, type = ContentType.SERIES, seriesId = 2L, lastWatchedAt = 300L, resumePositionMs = 0L),
                    history(contentId = 3L, type = ContentType.SERIES_EPISODE, seriesId = 3L, lastWatchedAt = 200L, resumePositionMs = 0L),
                    history(contentId = 4L, type = ContentType.MOVIE, lastWatchedAt = 100L, resumePositionMs = 25_000L)
                )
            )
        )

        val result = useCase(providerId = 1L, requireResumePosition = true).collectOnce()

        assertThat(result.map { it.contentId }).containsExactly(2L, 4L).inOrder()
    }

    @Test
    fun aggregates_selected_provider_ids_only() = runTest {
        val useCase = GetContinueWatching(
            playbackHistoryRepository = FakePlaybackHistoryRepository(
                history = listOf(
                    history(contentId = 20L, type = ContentType.SERIES_EPISODE, providerId = 2L, seriesId = 5L, lastWatchedAt = 300L, resumePositionMs = 15_000L),
                    history(contentId = 10L, type = ContentType.MOVIE, providerId = 1L, lastWatchedAt = 200L, resumePositionMs = 12_000L),
                    history(contentId = 30L, type = ContentType.MOVIE, providerId = 3L, lastWatchedAt = 100L, resumePositionMs = 9_000L)
                )
            )
        )

        val result = useCase(setOf(1L, 2L), limit = 5).collectOnce()

        assertThat(result.map { it.providerId to it.contentId }).containsExactly(
            2L to 20L,
            1L to 10L
        ).inOrder()
    }

    @Test
    fun multi_provider_requests_use_provider_set_repository_path() = runTest {
        val repository = FakePlaybackHistoryRepository(
            history = listOf(
                history(contentId = 99L, type = ContentType.MOVIE, providerId = 99L, lastWatchedAt = 500L, resumePositionMs = 12_000L)
            ),
            multiProviderHistory = listOf(
                history(contentId = 20L, type = ContentType.SERIES_EPISODE, providerId = 2L, seriesId = 5L, lastWatchedAt = 300L, resumePositionMs = 15_000L),
                history(contentId = 10L, type = ContentType.MOVIE, providerId = 1L, lastWatchedAt = 200L, resumePositionMs = 12_000L)
            )
        )
        val useCase = GetContinueWatching(playbackHistoryRepository = repository)

        val result = useCase(setOf(1L, 2L), limit = 5).collectOnce()

        assertThat(result.map { it.providerId to it.contentId }).containsExactly(
            2L to 20L,
            1L to 10L
        ).inOrder()
        assertThat(repository.lastRequestedProviderIds).containsExactly(1L, 2L)
    }

    @Test
    fun multi_provider_isolation_preserves_same_content_id_on_different_providers() = runTest {
        val useCase = GetContinueWatching(
            playbackHistoryRepository = FakePlaybackHistoryRepository(
                multiProviderHistory = listOf(
                    history(contentId = 100L, type = ContentType.MOVIE, providerId = 1L, lastWatchedAt = 300L, resumePositionMs = 20_000L),
                    history(contentId = 100L, type = ContentType.MOVIE, providerId = 2L, lastWatchedAt = 200L, resumePositionMs = 30_000L),
                    history(contentId = 50L, type = ContentType.SERIES_EPISODE, providerId = 1L, seriesId = 5L, lastWatchedAt = 150L, resumePositionMs = 15_000L),
                    history(contentId = 50L, type = ContentType.SERIES_EPISODE, providerId = 2L, seriesId = 5L, lastWatchedAt = 100L, resumePositionMs = 25_000L)
                )
            )
        )

        val result = useCase(setOf(1L, 2L), limit = 10).collectOnce()

        // Distinct by providerId + contentId ensures both provider entries exist without colliding
        assertThat(result.map { it.providerId to it.contentId }).containsExactly(
            1L to 100L,
            2L to 100L,
            1L to 50L,
            2L to 50L
        ).inOrder()
    }

    @Test
    fun completed_items_above_threshold_are_excluded() = runTest {
        val useCase = GetContinueWatching(
            playbackHistoryRepository = FakePlaybackHistoryRepository(
                history = listOf(
                    // 96% completed -> excluded
                    PlaybackHistory(
                        contentId = 1L,
                        contentType = ContentType.MOVIE,
                        providerId = 1L,
                        title = "Completed Movie",
                        streamUrl = "https://example.com/1",
                        resumePositionMs = 115_000L,
                        totalDurationMs = 120_000L,
                        lastWatchedAt = 300L
                    ),
                    // 50% completed -> included
                    PlaybackHistory(
                        contentId = 2L,
                        contentType = ContentType.MOVIE,
                        providerId = 1L,
                        title = "In Progress Movie",
                        streamUrl = "https://example.com/2",
                        resumePositionMs = 60_000L,
                        totalDurationMs = 120_000L,
                        lastWatchedAt = 200L
                    )
                )
            )
        )

        val result = useCase(providerId = 1L, limit = 5).collectOnce()

        assertThat(result.map { it.contentId }).containsExactly(2L)
    }

    @Test
    fun all_eligible_items_returned_without_cap_newest_first() = runTest {
        val items = (1L..25L).map { id ->
            history(contentId = id, type = ContentType.MOVIE, providerId = 1L, lastWatchedAt = 1000L - id, resumePositionMs = 10_000L)
        }
        val useCase = GetContinueWatching(
            playbackHistoryRepository = FakePlaybackHistoryRepository(history = items)
        )

        val result = useCase(providerId = 1L).collectOnce()

        // Verifies no 12-item cap: all 25 items are returned in newest-first order
        assertThat(result).hasSize(25)
        assertThat(result.first().contentId).isEqualTo(1L)
        assertThat(result.last().contentId).isEqualTo(25L)
    }

    @Test
    fun explicit_limit_is_respected_when_provided() = runTest {
        val items = (1L..20L).map { id ->
            history(contentId = id, type = ContentType.MOVIE, providerId = 1L, lastWatchedAt = 1000L - id, resumePositionMs = 10_000L)
        }
        val useCase = GetContinueWatching(
            playbackHistoryRepository = FakePlaybackHistoryRepository(history = items)
        )

        val result = useCase(providerId = 1L, limit = 15).collectOnce()

        assertThat(result).hasSize(15)
        assertThat(result.first().contentId).isEqualTo(1L)
        assertThat(result.last().contentId).isEqualTo(15L)
    }

    @Test
    fun returns_degraded_on_recoverable_io_failure() = runTest {
        val useCase = GetContinueWatching(
            playbackHistoryRepository = FakePlaybackHistoryRepository(
                historyFlow = flow { throw IOException("network failure") }
            )
        )

        val result = useCase(providerId = 1L).first()

        assertThat(result).isEqualTo(ContinueWatchingResult.Degraded)
    }

    @Test
    fun rethrows_non_io_upstream_failures() = runTest {
        val expected = IllegalStateException("database broken")
        val useCase = GetContinueWatching(
            playbackHistoryRepository = FakePlaybackHistoryRepository(
                historyFlow = flow { throw expected }
            )
        )

        val thrown = try {
            useCase(providerId = 1L).first()
            null
        } catch (error: IllegalStateException) {
            error
        }

        assertThat(thrown).isNotNull()
        assertThat(thrown?.message).isEqualTo(expected.message)
    }

    private suspend fun Flow<ContinueWatchingResult>.collectOnce(): List<PlaybackHistory> =
        first().let { result ->
            check(result is ContinueWatchingResult.Items) { "Expected Items but got $result" }
            result.items
        }

    private fun history(
        contentId: Long,
        type: ContentType,
        providerId: Long = 1L,
        seriesId: Long? = null,
        lastWatchedAt: Long,
        resumePositionMs: Long
    ) = PlaybackHistory(
        contentId = contentId,
        contentType = type,
        providerId = providerId,
        title = "$type-$contentId",
        streamUrl = "https://example.com/$contentId",
        resumePositionMs = resumePositionMs,
        totalDurationMs = 120_000L,
        lastWatchedAt = lastWatchedAt,
        seriesId = seriesId
    )

    private class FakePlaybackHistoryRepository(
        private val history: List<PlaybackHistory> = emptyList(),
        private val multiProviderHistory: List<PlaybackHistory> = history,
        private val historyFlow: Flow<List<PlaybackHistory>>? = null
    ) : PlaybackHistoryRepository {
        var lastRequestedProviderIds: Set<Long>? = null

        override fun getRecentlyWatched(limit: Int): Flow<List<PlaybackHistory>> = historyFlow ?: flowOf(history.take(limit))
        override fun getRecentlyWatchedByProvider(providerId: Long, limit: Int): Flow<List<PlaybackHistory>> =
            historyFlow ?: flowOf(history.filter { it.providerId == providerId }.take(limit))
        override fun getRecentlyWatchedByProviders(providerIds: Set<Long>, limit: Int): Flow<List<PlaybackHistory>> {
            lastRequestedProviderIds = providerIds
            return historyFlow ?: flowOf(multiProviderHistory.filter { it.providerId in providerIds }.take(limit))
        }
        override fun getContinueWatchingCandidatesByProvider(providerId: Long, limit: Int): Flow<List<PlaybackHistory>> =
            historyFlow ?: flowOf(history.filter { it.providerId == providerId && it.contentType != ContentType.LIVE }.take(limit))
        override fun getContinueWatchingCandidatesByProviders(providerIds: Set<Long>, limit: Int): Flow<List<PlaybackHistory>> {
            lastRequestedProviderIds = providerIds
            return historyFlow ?: flowOf(multiProviderHistory.filter { it.providerId in providerIds && it.contentType != ContentType.LIVE }.take(limit))
        }
        override fun getRecentLiveHistoryByProvider(providerId: Long, limit: Int): Flow<List<PlaybackHistory>> =
            historyFlow ?: flowOf(history.filter { it.providerId == providerId && it.contentType == ContentType.LIVE }.take(limit))
        override fun getRecentLiveHistoryByProviders(providerIds: Set<Long>, limit: Int): Flow<List<PlaybackHistory>> =
            historyFlow ?: flowOf(multiProviderHistory.filter { it.providerId in providerIds && it.contentType == ContentType.LIVE }.take(limit))
        override fun getUnwatchedCount(providerId: Long, seriesId: Long): Flow<Int> = flowOf(0)
        override suspend fun getPlaybackHistory(
            contentId: Long,
            contentType: ContentType,
            providerId: Long,
            seriesId: Long?,
            seasonNumber: Int?,
            episodeNumber: Int?
        ): PlaybackHistory? = null
        override suspend fun markAsWatched(history: PlaybackHistory) = com.kaynanamtv.domain.model.Result.success(Unit)
        override suspend fun recordPlayback(history: PlaybackHistory) = com.kaynanamtv.domain.model.Result.success(Unit)
        override suspend fun updateResumePosition(history: PlaybackHistory) = com.kaynanamtv.domain.model.Result.success(Unit)
        override suspend fun flushPendingProgress() = com.kaynanamtv.domain.model.Result.success(Unit)
        override suspend fun removeFromHistory(contentId: Long, contentType: ContentType, providerId: Long) = com.kaynanamtv.domain.model.Result.success(Unit)
        override suspend fun clearAllHistory() = com.kaynanamtv.domain.model.Result.success(Unit)
        override suspend fun clearHistoryForProvider(providerId: Long) = com.kaynanamtv.domain.model.Result.success(Unit)
        override suspend fun clearLiveHistoryForProvider(providerId: Long) = com.kaynanamtv.domain.model.Result.success(Unit)
    }
}
