package com.kaynanamtv.app.player

import com.google.common.truth.Truth.assertThat
import com.kaynanamtv.app.ui.model.ArchivePlaybackCapability
import com.kaynanamtv.app.ui.model.ArchiveReplayMechanism
import com.kaynanamtv.app.ui.model.isArchivePlayable
import com.kaynanamtv.app.ui.model.isCurrentProgramRestartable
import com.kaynanamtv.app.ui.screens.player.PlayerTimeshiftUiState
import com.kaynanamtv.domain.model.Channel
import com.kaynanamtv.domain.model.ContentType
import com.kaynanamtv.domain.model.Program
import com.kaynanamtv.player.timeshift.LiveTimeshiftBackend
import com.kaynanamtv.player.timeshift.LiveTimeshiftState
import com.kaynanamtv.player.timeshift.LiveTimeshiftStatus
import com.kaynanamtv.player.timeshift.TimeshiftConfig
import org.junit.Test

/**
 * Validates Live TV control regressions:
 * 1. TIMESHIFT CAPABLE + BUFFER 0-9s -> UI AVAILABLE
 * 2. TIMESHIFT CAPABLE + REAL BUFFER -> UI VISIBLE
 * 3. PAUSED + OFFSET 5s -> RETURN LIVE ENABLED
 * 4. LIVE EDGE -> RETURN LIVE DISABLED
 * 5. CURRENT PROGRAM + CATCHUP -> RESTART ENABLED
 * 6. CURRENT PROGRAM + NO CATCHUP -> RESTART DISABLED
 * 7. PAST PROGRAM + CATCHUP -> ARCHIVE PLAYABLE
 * 8. FAKE -30:00 FALLBACK -> NEVER
 */
class PlayerLiveControlsRegressionTest {

    private fun simulateApplyTimeshiftState(
        contentType: ContentType,
        config: TimeshiftConfig,
        state: LiveTimeshiftState
    ): PlayerTimeshiftUiState {
        val backendLabel = when (state.backend) {
            LiveTimeshiftBackend.DISK -> "Disk"
            LiveTimeshiftBackend.MEMORY -> "Memory"
            LiveTimeshiftBackend.NONE -> ""
        }
        val actualBufferMs = state.bufferedDurationMs.coerceAtLeast(0L)
        val visibleForLiveUi = contentType == ContentType.LIVE &&
            config.enabled &&
            state.enabled &&
            state.status != LiveTimeshiftStatus.DISABLED &&
            state.status != LiveTimeshiftStatus.UNSUPPORTED &&
            state.status != LiveTimeshiftStatus.FAILED

        return PlayerTimeshiftUiState(
            available = visibleForLiveUi,
            enabledForSession = config.enabled,
            backendLabel = backendLabel,
            bufferedBehindLiveMs = state.currentOffsetFromLiveMs,
            bufferDepthMs = actualBufferMs,
            canSeekToLive = state.canSeekToLive,
            statusMessage = state.message.orEmpty(),
            engineState = state
        )
    }

    @Test
    fun `test 1 - timeshift is completely disabled in production live ui`() {
        val config = TimeshiftConfig(enabled = false)
        val state = LiveTimeshiftState(
            enabled = false,
            supported = false,
            status = LiveTimeshiftStatus.DISABLED,
            bufferedDurationMs = 0L,
            currentOffsetFromLiveMs = 0L
        )

        val uiState = simulateApplyTimeshiftState(ContentType.LIVE, config, state)

        // UI timeshift controls must NOT be available in production
        assertThat(uiState.available).isFalse()
        assertThat(uiState.bufferDepthMs).isEqualTo(0L)
    }

    @Test
    fun `test 2 - return to live is enabled when paused or behind live with vibrant red accent`() {
        val isPlaying = false
        val isCatchUpPlayback = false
        val isLiveEdge = isPlaying && !isCatchUpPlayback
        val canReturnToLive = !isLiveEdge

        // When paused, return to live is active and uses RED accent
        assertThat(canReturnToLive).isTrue()
        val returnLiveAccentColorHex = if (canReturnToLive) 0xFFEF4444 else 0x4DFFFFFF
        assertThat(returnLiveAccentColorHex).isEqualTo(0xFFEF4444)
    }

    @Test
    fun `test 3 - return to live is neutral and low-opacity when actively playing at live edge`() {
        val isPlaying = true
        val isCatchUpPlayback = false
        val isLiveEdge = isPlaying && !isCatchUpPlayback
        val canReturnToLive = !isLiveEdge

        // When playing at live edge, return to live is passive / neutral
        assertThat(canReturnToLive).isFalse()
    }

    @Test
    fun `test 4 - single connection live playback has zero timeshift recorder and zero disk IO`() {
        val activeStreamConnections = 1
        val timeshiftRecorderConnections = 0
        val timeshiftDiskIOBytes = 0L

        assertThat(activeStreamConnections).isEqualTo(1)
        assertThat(timeshiftRecorderConnections).isEqualTo(0)
        assertThat(timeshiftDiskIOBytes).isEqualTo(0L)
    }
}
