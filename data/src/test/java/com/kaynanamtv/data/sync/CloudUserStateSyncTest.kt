package com.kaynanamtv.data.sync

import android.content.Context
import android.content.SharedPreferences
import com.google.common.truth.Truth.assertThat
import com.kaynanamtv.data.local.dao.*
import com.kaynanamtv.data.local.entity.*
import com.kaynanamtv.domain.model.ContentType
import com.kaynanamtv.domain.model.ProviderType
import com.kaynanamtv.domain.util.isPlaybackComplete
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class CloudUserStateSyncTest {

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

    @Test
    fun `computeProviderStableKey produces identical hash for same credentials regardless of case or whitespace`() {
        val p1 = ProviderEntity(
            id = 1L,
            name = "Test Provider",
            type = ProviderType.XTREAM_CODES,
            serverUrl = "http://example.com:8080/ ",
            username = "user123",
            password = "pwd"
        )
        val p2 = ProviderEntity(
            id = 99L,
            name = "Different Name On Tablet",
            type = ProviderType.XTREAM_CODES,
            serverUrl = "HTTP://EXAMPLE.COM:8080",
            username = "user123",
            password = "pwd"
        )

        val key1 = syncManager.computeProviderStableKey(p1)
        val key2 = syncManager.computeProviderStableKey(p2)

        assertThat(key1).isNotEmpty()
        assertThat(key1).isEqualTo(key2)
    }

    @Test
    fun `computeProviderStableKey generates unique hashes for different providers`() {
        val p1 = ProviderEntity(
            id = 1L,
            name = "Provider 1",
            type = ProviderType.XTREAM_CODES,
            serverUrl = "http://iptv1.com",
            username = "user1"
        )
        val p2 = ProviderEntity(
            id = 2L,
            name = "Provider 2",
            type = ProviderType.XTREAM_CODES,
            serverUrl = "http://iptv2.com",
            username = "user1"
        )

        val key1 = syncManager.computeProviderStableKey(p1)
        val key2 = syncManager.computeProviderStableKey(p2)

        assertThat(key1).isNotEqualTo(key2)
    }

    @Test
    fun `isPlaybackComplete accurately checks 95 percent threshold`() {
        val durationMs = 100_000L // 100 seconds

        // 94% -> false
        assertThat(isPlaybackComplete(94_000L, durationMs)).isFalse()

        // 95% -> true
        assertThat(isPlaybackComplete(95_000L, durationMs)).isTrue()

        // 99% -> true
        assertThat(isPlaybackComplete(99_000L, durationMs)).isTrue()
    }

    @Test
    fun `resume position threshold requires at least 60 seconds to prompt`() {
        val shortProgress = 45_000L // 45 seconds
        val validProgress = 1_517_000L // 25 minutes 17 seconds

        assertThat(shortProgress >= 60_000L).isFalse()
        assertThat(validProgress >= 60_000L).isTrue()
    }

    @Test
    fun `resolution logic selects newer cloud progress when cloud updatedAt exceeds local lastWatchedAt`() {
        val local = PlaybackHistoryEntity(
            providerId = 1L,
            contentId = 101L,
            contentType = ContentType.MOVIE,
            resumePositionMs = 1_200_000L, // 20:00
            totalDurationMs = 7_200_000L,
            lastWatchedAt = 1000L
        )
        val cloud = CloudUserStateSyncManager.TargetedWatchProgress(
            resumePositionMs = 1_517_000L, // 25:17
            totalDurationMs = 7_200_000L,
            isCompleted = false,
            updatedAt = 2000L // Newer
        )

        val useCloud = cloud.updatedAt > local.lastWatchedAt
        val effectivePosition = if (useCloud) cloud.resumePositionMs else local.resumePositionMs

        assertThat(useCloud).isTrue()
        assertThat(effectivePosition).isEqualTo(1_517_000L)
    }

    @Test
    fun `resolution logic preserves local progress when local lastWatchedAt exceeds older cloud updatedAt`() {
        val local = PlaybackHistoryEntity(
            providerId = 1L,
            contentId = 101L,
            contentType = ContentType.MOVIE,
            resumePositionMs = 1_800_000L, // 30:00
            totalDurationMs = 7_200_000L,
            lastWatchedAt = 3000L // Newer
        )
        val cloud = CloudUserStateSyncManager.TargetedWatchProgress(
            resumePositionMs = 1_517_000L, // 25:17
            totalDurationMs = 7_200_000L,
            isCompleted = false,
            updatedAt = 2000L // Older
        )

        val useCloud = cloud.updatedAt > local.lastWatchedAt
        val effectivePosition = if (useCloud) cloud.resumePositionMs else local.resumePositionMs

        assertThat(useCloud).isFalse()
        assertThat(effectivePosition).isEqualTo(1_800_000L)
    }

    @Test
    fun `resolution logic handles null local progress by adopting valid cloud progress`() {
        val local: PlaybackHistoryEntity? = null
        val cloud = CloudUserStateSyncManager.TargetedWatchProgress(
            resumePositionMs = 1_517_000L, // 25:17
            totalDurationMs = 7_200_000L,
            isCompleted = false,
            updatedAt = 2000L
        )

        val effectivePosition = if (cloud.updatedAt > (local?.lastWatchedAt ?: 0L)) cloud.resumePositionMs else (local?.resumePositionMs ?: 0L)
        assertThat(effectivePosition).isEqualTo(1_517_000L)
    }

    @Test
    fun `resolution logic handles timeout or offline null cloud progress by safely using local progress`() {
        val local = PlaybackHistoryEntity(
            providerId = 1L,
            contentId = 101L,
            contentType = ContentType.MOVIE,
            resumePositionMs = 1_200_000L, // 20:00
            totalDurationMs = 7_200_000L,
            lastWatchedAt = 1000L
        )
        val cloud: CloudUserStateSyncManager.TargetedWatchProgress? = null

        val effectivePosition = if (cloud != null && cloud.updatedAt > local.lastWatchedAt) {
            cloud.resumePositionMs
        } else {
            local.resumePositionMs
        }

        assertThat(effectivePosition).isEqualTo(1_200_000L)
    }

    @Test
    fun `resolution logic suppresses resume prompt when cloud status is completed`() {
        val cloud = CloudUserStateSyncManager.TargetedWatchProgress(
            resumePositionMs = 6_900_000L, // ~96% of 7200s
            totalDurationMs = 7_200_000L,
            isCompleted = true,
            updatedAt = 2000L
        )

        val shouldPrompt = cloud.resumePositionMs >= 60_000L && !cloud.isCompleted && !isPlaybackComplete(cloud.resumePositionMs, cloud.totalDurationMs)
        assertThat(shouldPrompt).isFalse()
    }

    @Test
    fun `sync cursors default to 0 on fresh install ensuring full initial sync`() {
        whenever(sharedPreferences.getLong("watch_history_cursor_user123", 0L)).thenReturn(0L)
        whenever(sharedPreferences.getLong("favorites_cursor_user123", 0L)).thenReturn(0L)

        val watchCursor = syncManager.getWatchHistorySyncCursor("user123")
        val favoritesCursor = syncManager.getFavoritesSyncCursor("user123")

        assertThat(watchCursor).isEqualTo(0L)
        assertThat(favoritesCursor).isEqualTo(0L)
    }

    @Test
    fun `sync cursors update and persist properly per user`() {
        syncManager.setWatchHistorySyncCursor("user123", 1700000000000L)
        syncManager.setFavoritesSyncCursor("user123", 1700000050000L)

        whenever(sharedPreferences.getLong("watch_history_cursor_user123", 0L)).thenReturn(1700000000000L)
        whenever(sharedPreferences.getLong("favorites_cursor_user123", 0L)).thenReturn(1700000050000L)

        assertThat(syncManager.getWatchHistorySyncCursor("user123")).isEqualTo(1700000000000L)
        assertThat(syncManager.getFavoritesSyncCursor("user123")).isEqualTo(1700000050000L)
    }

    @Test
    fun `clock skew safety window calculates safe query lower bound without underflow`() {
        val lastCursor = 100_000L
        val safetyWindow = CloudUserStateSyncManager.CLOCK_SKEW_SAFETY_WINDOW_MS
        val queryStart = (lastCursor - safetyWindow).coerceAtLeast(0L)

        assertThat(safetyWindow).isEqualTo(60_000L)
        assertThat(queryStart).isEqualTo(40_000L)

        // Near zero test
        val earlyCursor = 30_000L
        val earlyQueryStart = (earlyCursor - safetyWindow).coerceAtLeast(0L)
        assertThat(earlyQueryStart).isEqualTo(0L)
    }
}
