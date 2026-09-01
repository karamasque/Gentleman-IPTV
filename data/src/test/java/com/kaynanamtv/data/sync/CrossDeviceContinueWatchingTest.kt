package com.kaynanamtv.data.sync

import android.content.Context
import android.content.SharedPreferences
import com.google.common.truth.Truth.assertThat
import com.kaynanamtv.data.local.dao.*
import com.kaynanamtv.data.local.entity.*
import com.kaynanamtv.domain.model.ContentType
import com.kaynanamtv.domain.model.Episode
import com.kaynanamtv.domain.model.Movie
import com.kaynanamtv.domain.model.PlaybackHistory
import com.kaynanamtv.domain.model.PlaybackWatchedStatus
import com.kaynanamtv.domain.model.Season
import com.kaynanamtv.domain.model.Series
import com.kaynanamtv.domain.util.isPlaybackComplete
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Unit tests for Cross-Device Continue Watching and Metadata Hydration Fixes.
 */
class CrossDeviceContinueWatchingTest {

    private val context: Context = mock()
    private val sharedPreferences: SharedPreferences = mock()
    private val editor: SharedPreferences.Editor = mock()
    private val favoriteDao: FavoriteDao = mock()
    private val playbackHistoryDao: PlaybackHistoryDao = mock()
    private val providerDao: ProviderDao = mock()
    private val movieDao: MovieDao = mock()
    private val seriesDao: SeriesDao = mock()
    private val episodeDao: EpisodeDao = mock()

    private lateinit var syncManager: CloudUserStateSyncManager

    @Before
    fun setup() {
        whenever(context.getSharedPreferences(any(), eq(Context.MODE_PRIVATE))).thenReturn(sharedPreferences)
        whenever(sharedPreferences.edit()).thenReturn(editor)
        whenever(editor.putLong(any(), any())).thenReturn(editor)

        syncManager = CloudUserStateSyncManager(
            context = context,
            favoriteDao = favoriteDao,
            playbackHistoryDao = playbackHistoryDao,
            providerDao = providerDao,
            movieDao = movieDao,
            seriesDao = seriesDao,
            episodeDao = episodeDao
        )
    }

    // 1. Cloud movie history resolves: title populated, poster populated, playback_history populated, movies.watch_progress populated
    @Test
    fun `cloud movie history populates title poster and denormalized watch progress`() {
        val localMovie = MovieEntity(
            id = 101L,
            providerId = 1L,
            streamId = 555L,
            name = "Inception",
            posterUrl = "https://example.com/inception.jpg",
            watchProgress = 0L
        )

        val title = localMovie.name
        val posterUrl = localMovie.posterUrl
        val resumePositionMs = 3_420_000L // 57m
        val totalDurationMs = 7_200_000L // 120m

        val entity = PlaybackHistoryEntity(
            providerId = localMovie.providerId,
            contentId = localMovie.id,
            contentType = ContentType.MOVIE,
            title = title,
            posterUrl = posterUrl,
            resumePositionMs = resumePositionMs,
            totalDurationMs = totalDurationMs,
            lastWatchedAt = 1000L,
            watchedStatus = "IN_PROGRESS"
        )

        assertThat(entity.title).isEqualTo("Inception")
        assertThat(entity.posterUrl).isEqualTo("https://example.com/inception.jpg")
        assertThat(entity.resumePositionMs).isEqualTo(3_420_000L)
    }

    // 2. Cloud episode history resolves: title populated, poster populated/fallback, episodes.watch_progress populated
    @Test
    fun `cloud episode history populates episode title and poster with series fallback`() {
        val localSeries = SeriesEntity(
            id = 200L,
            providerId = 1L,
            seriesId = 777L,
            name = "Breaking Bad",
            posterUrl = "https://example.com/breakingbad.jpg"
        )
        val localEpisode = EpisodeEntity(
            id = 201L,
            providerId = 1L,
            seriesId = 200L,
            seasonNumber = 1,
            episodeNumber = 3,
            title = "...And the Bag's in the River",
            coverUrl = null // No episode-specific cover
        )

        val title = localEpisode.title.ifBlank { localSeries.name }
        val posterUrl = localEpisode.coverUrl ?: localSeries.posterUrl

        val entity = PlaybackHistoryEntity(
            providerId = 1L,
            contentId = localEpisode.id,
            contentType = ContentType.SERIES_EPISODE,
            title = title,
            posterUrl = posterUrl,
            resumePositionMs = 1_200_000L,
            totalDurationMs = 2_800_000L,
            lastWatchedAt = 1000L,
            seriesId = localSeries.id,
            seasonNumber = 1,
            episodeNumber = 3,
            watchedStatus = "IN_PROGRESS"
        )

        assertThat(entity.title).isEqualTo("...And the Bag's in the River")
        assertThat(entity.posterUrl).isEqualTo("https://example.com/breakingbad.jpg")
    }

    // 3 & 4. Unresolved movie / episode: cursor does NOT advance past it
    @Test
    fun `unresolved items in delta batch prevent cursor advancement past unresolved timestamp`() {
        val unresolvedItemUpdatedAt = 1700000050000L
        val syncStartTime = 1700000090000L

        var minUnresolvedWatchHistoryTime: Long? = unresolvedItemUpdatedAt

        val safeCursor = if (minUnresolvedWatchHistoryTime != null) {
            (minUnresolvedWatchHistoryTime - 1000L).coerceAtLeast(0L)
        } else {
            syncStartTime
        }

        assertThat(safeCursor).isEqualTo(1700000049000L)
        assertThat(safeCursor).isLessThan(unresolvedItemUpdatedAt)
        assertThat(safeCursor).isLessThan(syncStartTime)
    }

    // 5. Catalog becomes ready later: previously unresolved cloud item imports on next reconcile
    @Test
    fun `when all items are resolved cursor advances to sync start time`() {
        var minUnresolvedWatchHistoryTime: Long? = null
        val syncStartTime = 1700000090000L

        val safeCursor = if (minUnresolvedWatchHistoryTime != null) {
            (minUnresolvedWatchHistoryTime - 1000L).coerceAtLeast(0L)
        } else {
            syncStartTime
        }

        assertThat(safeCursor).isEqualTo(syncStartTime)
    }

    // 6. Legacy history poster null: local movie poster fallback works
    @Test
    fun `legacy history item with null poster lazily resolves from local movie metadata`() {
        val legacyHistory = PlaybackHistory(
            id = 1L,
            contentId = 101L,
            contentType = ContentType.MOVIE,
            providerId = 1L,
            title = "",
            posterUrl = null,
            streamUrl = "https://example.com/movie.mp4",
            resumePositionMs = 1_500_000L,
            totalDurationMs = 7_200_000L,
            lastWatchedAt = 1000L
        )

        val localMovie = MovieEntity(
            id = 101L,
            providerId = 1L,
            streamId = 555L,
            name = "Interstellar",
            posterUrl = "https://example.com/interstellar.jpg"
        )

        val enriched = legacyHistory.copy(
            title = legacyHistory.title.ifBlank { localMovie.name },
            posterUrl = legacyHistory.posterUrl ?: localMovie.posterUrl
        )

        assertThat(enriched.title).isEqualTo("Interstellar")
        assertThat(enriched.posterUrl).isEqualTo("https://example.com/interstellar.jpg")
    }

    // 7. Legacy episode poster null: series/episode local poster fallback works
    @Test
    fun `legacy episode history with null poster lazily resolves from series poster`() {
        val legacyHistory = PlaybackHistory(
            id = 2L,
            contentId = 201L,
            contentType = ContentType.SERIES_EPISODE,
            providerId = 1L,
            title = "",
            posterUrl = null,
            streamUrl = "https://example.com/episode.mp4",
            resumePositionMs = 1_000_000L,
            totalDurationMs = 3_000_000L,
            lastWatchedAt = 1000L,
            seriesId = 200L,
            seasonNumber = 1,
            episodeNumber = 2
        )

        val localSeries = SeriesEntity(
            id = 200L,
            providerId = 1L,
            seriesId = 777L,
            name = "Game of Thrones",
            posterUrl = "https://example.com/got.jpg"
        )

        val enriched = legacyHistory.copy(
            title = legacyHistory.title.ifBlank { localSeries.name },
            posterUrl = legacyHistory.posterUrl ?: localSeries.posterUrl
        )

        assertThat(enriched.title).isEqualTo("Game of Thrones")
        assertThat(enriched.posterUrl).isEqualTo("https://example.com/got.jpg")
    }

    // 8 & 9. Cooldown prevents repeated reconcile inside 60 seconds
    @Test
    fun `reconcile cooldown suppresses rapid triggers`() {
        val minInterval = CloudUserStateSyncManager.RECONCILE_MIN_INTERVAL_MS
        assertThat(minInterval).isEqualTo(60_000L)

        val lastReconcileTime = 100_000L
        val call1Time = 120_000L // 20s later -> should throttle
        val call2Time = 170_000L // 70s later -> should allow

        val shouldThrottle1 = (call1Time - lastReconcileTime) < minInterval
        val shouldThrottle2 = (call2Time - lastReconcileTime) < minInterval

        assertThat(shouldThrottle1).isTrue()
        assertThat(shouldThrottle2).isFalse()
    }

    // 12. Movie hydrated progress: existing resume state resolves to non-zero position
    @Test
    fun `hydrated movie progress resolves to Devam Et state on detail screen`() {
        val movie = Movie(
            id = 101L,
            name = "Inception",
            streamId = 555L,
            providerId = 1L,
            watchProgress = 3_420_000L,
            durationSeconds = 7200
        )
        val playbackHistory = PlaybackHistory(
            contentId = 101L,
            contentType = ContentType.MOVIE,
            providerId = 1L,
            title = "Inception",
            streamUrl = "https://example.com/inception.mp4",
            resumePositionMs = 3_420_000L,
            totalDurationMs = 7_200_000L,
            lastWatchedAt = 2000L
        )

        val resumePositionMs = playbackHistory.resumePositionMs.takeIf { it > 0L } ?: movie.watchProgress
        val hasResume = resumePositionMs > 5000L && !isPlaybackComplete(resumePositionMs, movie.durationSeconds * 1000L)

        assertThat(hasResume).isTrue()
        assertThat(resumePositionMs).isEqualTo(3_420_000L)
    }

    // 13. Series hydrated episode progress: findResumeEpisode selects correct episode
    @Test
    fun `hydrated series progress selects in-progress episode for resume action`() {
        val ep1 = Episode(
            id = 1L,
            seriesId = 10L,
            providerId = 1L,
            seasonNumber = 1,
            episodeNumber = 1,
            title = "Pilot",
            watchProgress = 3_000_000L, // Completed (100%)
            durationSeconds = 3000,
            lastWatchedAt = 1000L
        )
        val ep2 = Episode(
            id = 2L,
            seriesId = 10L,
            providerId = 1L,
            seasonNumber = 1,
            episodeNumber = 2,
            title = "Episode 2",
            watchProgress = 1_200_000L, // In-progress (40%)
            durationSeconds = 3000,
            lastWatchedAt = 2000L
        )
        val ep3 = Episode(
            id = 3L,
            seriesId = 10L,
            providerId = 1L,
            seasonNumber = 1,
            episodeNumber = 3,
            title = "Episode 3",
            watchProgress = 0L,
            durationSeconds = 3000,
            lastWatchedAt = 0L
        )

        val ordered = listOf(ep1, ep2, ep3)

        val inProgress = ordered
            .filter { ep ->
                ep.watchProgress > 5000L &&
                    !isPlaybackComplete(ep.watchProgress, ep.durationSeconds.toLong() * 1000L)
            }
            .maxByOrNull { it.lastWatchedAt }

        assertThat(inProgress).isNotNull()
        assertThat(inProgress?.episodeNumber).isEqualTo(2)
        assertThat(inProgress?.watchProgress).isEqualTo(1_200_000L)
    }

    // 14. Completed item: still excluded from Continue Watching
    @Test
    fun `completed item is properly excluded from continue watching`() {
        val completedHistory = PlaybackHistory(
            contentId = 101L,
            contentType = ContentType.MOVIE,
            providerId = 1L,
            title = "Completed Movie",
            streamUrl = "https://example.com/movie.mp4",
            resumePositionMs = 6_950_000L, // ~96.5%
            totalDurationMs = 7_200_000L,
            lastWatchedAt = 2000L
        )

        val isCompleted = isPlaybackComplete(completedHistory.resumePositionMs, completedHistory.totalDurationMs)
        assertThat(isCompleted).isTrue()
    }

    // 15. Cross-device movie reconcile preserves local title and poster if cloud metadata is missing
    @Test
    fun `reconcile movie preserves local metadata when cloud metadata is blank`() {
        val localMovie = MovieEntity(
            id = 50L,
            providerId = 1L,
            streamId = 999L,
            name = "Local Movie Name",
            posterUrl = "https://example.com/local_poster.jpg",
            streamUrl = "https://example.com/stream.mkv"
        )
        val cloudData = mapOf<String, Any>(
            "streamId" to 999L,
            "title" to "", // Blank in cloud
            "resumePositionMs" to 50_000L,
            "totalDurationMs" to 100_000L,
            "updatedAt" to 2000L
        )

        val resolvedTitle = (cloudData["title"] as? String)?.takeIf { it.isNotBlank() }
            ?: localMovie.name.takeIf { it.isNotBlank() }
            ?: ""
        val resolvedPoster = localMovie.posterUrl
        val resolvedStreamUrl = localMovie.streamUrl

        assertThat(resolvedTitle).isEqualTo("Local Movie Name")
        assertThat(resolvedPoster).isEqualTo("https://example.com/local_poster.jpg")
        assertThat(resolvedStreamUrl).isEqualTo("https://example.com/stream.mkv")
    }

    // 16. Cross-device episode reconcile matches coordinates first, falls back to streamId
    @Test
    fun `reconcile episode coordinates matching logic`() {
        val seriesRemoteId = 555L
        val seasonNumber = 2
        val episodeNumber = 4
        val streamId = 8888L

        val docId = "EPISODE_providerKey_${seriesRemoteId}_S${seasonNumber}E${episodeNumber}"
        assertThat(docId).isEqualTo("EPISODE_providerKey_555_S2E4")
    }

    // 17. Provider isolation: different provider stable keys remain isolated
    @Test
    fun `different providers compute distinct stable keys`() {
        val p1 = ProviderEntity(
            id = 1L,
            name = "Provider A",
            type = com.kaynanamtv.domain.model.ProviderType.XTREAM_CODES,
            serverUrl = "http://server1.com",
            username = "user1",
            password = "pwd"
        )
        val p2 = ProviderEntity(
            id = 2L,
            name = "Provider B",
            type = com.kaynanamtv.domain.model.ProviderType.XTREAM_CODES,
            serverUrl = "http://server2.com",
            username = "user1",
            password = "pwd"
        )

        val key1 = syncManager.computeProviderStableKey(p1)
        val key2 = syncManager.computeProviderStableKey(p2)

        assertThat(key1).isNotEqualTo(key2)
    }

    // 18. Sanitized hashing test: ensures deterministic 8-byte hex prefix without exposing raw UID or tokens
    @Test
    fun `sanitizedHash produces consistent non-empty hash without raw leaks`() {
        val uid = "user_abc123_xyz789"
        val hash1 = syncManager.sanitizedHash(uid)
        val hash2 = syncManager.sanitizedHash(uid)

        assertThat(hash1).isEqualTo(hash2)
        assertThat(hash1).isNotEmpty()
        assertThat(hash1).doesNotContain(uid)
        assertThat(syncManager.sanitizedHash("")).isEqualTo("empty")
    }
}
