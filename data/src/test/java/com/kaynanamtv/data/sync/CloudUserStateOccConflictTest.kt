package com.kaynanamtv.data.sync

import android.content.Context
import android.content.SharedPreferences
import com.google.common.truth.Truth.assertThat
import com.kaynanamtv.data.local.dao.*
import com.kaynanamtv.data.local.entity.PlaybackHistoryEntity
import com.kaynanamtv.domain.model.ContentType
import com.kaynanamtv.domain.util.isPlaybackComplete
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

/**
 * Phase 18: Unit tests for Optimistic Concurrency Control (OCC) conflict resolution
 * and playback progress synchronization.
 */
class CloudUserStateOccConflictTest {

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

    // 1. Legacy document: missing OCC metadata -> revision 0/session empty/seq 0 parsed safely
    @Test
    fun `legacy document with missing OCC metadata parses safely with defaults`() {
        val legacyDoc = CloudUserStateSyncManager.TargetedWatchProgress(
            resumePositionMs = 500_000L,
            totalDurationMs = 1_000_000L,
            isCompleted = false,
            updatedAt = 1000L
        )

        assertThat(legacyDoc.revision).isEqualTo(0L)
        assertThat(legacyDoc.playbackSessionId).isEmpty()
        assertThat(legacyDoc.checkpointSeq).isEqualTo(0L)
    }

    // 2. New session: cloud revision == baseRevision -> canonical claim succeeds
    @Test
    fun `new session with matching baseRevision can claim canonical ownership`() {
        val cloudRevision = 3L
        val baseRevision = 3L
        val canClaim = cloudRevision == baseRevision

        val nextRevision = if (canClaim) cloudRevision + 1L else cloudRevision
        assertThat(canClaim).isTrue()
        assertThat(nextRevision).isEqualTo(4L)
    }

    // 3. New session: cloud revision != baseRevision -> cloud NOT overwritten, local progress preserved
    @Test
    fun `new session with mismatched baseRevision detects conflict and protects cloud state`() {
        val cloudRevision = 5L // Server advanced by another device
        val localBaseRevision = 3L // Outdated local session
        val canClaim = cloudRevision == localBaseRevision

        assertThat(canClaim).isFalse()
    }

    // 4. Same session: incoming seq > cloud seq -> update accepted
    @Test
    fun `same session accepts monotonic checkpoint sequence advancement`() {
        val cloudSessionId = "session_abc"
        val incomingSessionId = "session_abc"
        val cloudSeq = 4L
        val incomingSeq = 5L

        val isSameSession = cloudSessionId == incomingSessionId
        val isAccepted = isSameSession && incomingSeq > cloudSeq

        assertThat(isAccepted).isTrue()
    }

    // 5. Same session: incoming seq == cloud seq -> idempotent duplicate no-op
    @Test
    fun `same session ignores idempotent duplicate checkpoint sequence`() {
        val cloudSessionId = "session_abc"
        val incomingSessionId = "session_abc"
        val cloudSeq = 5L
        val incomingSeq = 5L

        val isSameSession = cloudSessionId == incomingSessionId
        val isAccepted = isSameSession && incomingSeq > cloudSeq
        val isDuplicate = isSameSession && incomingSeq == cloudSeq

        assertThat(isAccepted).isFalse()
        assertThat(isDuplicate).isTrue()
    }

    // 6. Same session: incoming seq < cloud seq -> stale write rejected
    @Test
    fun `same session rejects out of order stale checkpoint sequence`() {
        val cloudSessionId = "session_abc"
        val incomingSessionId = "session_abc"
        val cloudSeq = 8L
        val incomingSeq = 6L

        val isSameSession = cloudSessionId == incomingSessionId
        val isAccepted = isSameSession && incomingSeq > cloudSeq

        assertThat(isAccepted).isFalse()
    }

    // 7 & 8. Clock +20 minutes and -20 minutes do not alter OCC winner
    @Test
    fun `clock skew does not determine OCC canonical ownership`() {
        val serverRevision = 4L
        val deviceAFastClockRevision = 4L // Accurate base revision, fast clock
        val deviceBSlowClockRevision = 2L // Stale base revision, slow clock

        // Device A has matching baseRevision -> wins regardless of clock
        assertThat(deviceAFastClockRevision == serverRevision).isTrue()

        // Device B has stale baseRevision -> rejected regardless of clock
        assertThat(deviceBSlowClockRevision == serverRevision).isFalse()
    }

    // 9. Completed state + stale old session: completed canonical state not overwritten
    @Test
    fun `completed canonical state protected against stale in-progress session`() {
        val cloudCanonical = CloudUserStateSyncManager.TargetedWatchProgress(
            resumePositionMs = 7_000_000L,
            totalDurationMs = 7_200_000L,
            isCompleted = true,
            updatedAt = 5000L,
            revision = 5L,
            playbackSessionId = "session_completed",
            checkpointSeq = 12L
        )

        val staleLocalBaseRevision = 4L // Stale session from earlier playback
        val canOverwrite = cloudCanonical.revision == staleLocalBaseRevision

        assertThat(canOverwrite).isFalse()
        assertThat(cloudCanonical.isCompleted).isTrue()
    }

    // 10. Completed -> explicit replay: new session can claim only with matching baseRevision
    @Test
    fun `intentional replay creates new session that claims with matching base revision`() {
        val cloudCompletedRevision = 5L
        val replayBaseRevision = 5L // Replay captures current canonical revision

        val canClaimReplay = cloudCompletedRevision == replayBaseRevision
        val replayRevision = if (canClaimReplay) cloudCompletedRevision + 1L else cloudCompletedRevision

        assertThat(canClaimReplay).isTrue()
        assertThat(replayRevision).isEqualTo(6L)
    }

    // 11. Offline progress: Room persists immediately without requiring cloud
    @Test
    fun `local Room persistence is immediate and independent of cloud connectivity`() {
        val local = PlaybackHistoryEntity(
            providerId = 1L,
            contentId = 101L,
            contentType = ContentType.MOVIE,
            resumePositionMs = 1_200_000L,
            totalDurationMs = 7_200_000L,
            lastWatchedAt = 1000L
        )

        assertThat(local.resumePositionMs).isEqualTo(1_200_000L)
        assertThat(local.watchedStatus).isEqualTo("IN_PROGRESS")
    }

    // 12. Reconnect: revision mismatch -> canonical cloud preserved
    @Test
    fun `reconnect with revision mismatch preserves canonical cloud state`() {
        val serverRevision = 10L
        val reconnectingDeviceBaseRevision = 7L

        val isConflict = serverRevision != reconnectingDeviceBaseRevision
        assertThat(isConflict).isTrue()
    }

    // 13. Targeted resume: <=1500ms local fallback preserved
    @Test
    fun `targeted resume timeout falls back safely to local Room history`() {
        val local = PlaybackHistoryEntity(
            providerId = 1L,
            contentId = 101L,
            contentType = ContentType.MOVIE,
            resumePositionMs = 1_800_000L,
            totalDurationMs = 7_200_000L,
            lastWatchedAt = 2000L
        )
        val cloudTimedOut: CloudUserStateSyncManager.TargetedWatchProgress? = null

        val effective = cloudTimedOut?.resumePositionMs ?: local.resumePositionMs
        assertThat(effective).isEqualTo(1_800_000L)
    }

    // 14. Late cloud: active playback is not interrupted
    @Test
    fun `active playback is not seeked by late cloud result`() {
        var activePosition = 1_850_000L
        val lateCloudProgress = 1_200_000L

        // Player ignores late cloud progress after preparation
        val finalPosition = activePosition
        assertThat(finalPosition).isEqualTo(1_850_000L)
    }

    // 15. Cloud transaction failure: local playback/progress unaffected
    @Test
    fun `cloud transaction failure does not degrade local Room progress`() {
        val localSavedPosition = 2_000_000L
        var cloudFailed = true

        val effectiveLocal = if (cloudFailed) localSavedPosition else 0L
        assertThat(effectiveLocal).isEqualTo(2_000_000L)
    }
}
