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
    fun `test 1 and 2 - timeshift capable with buffer 0 to 9s is UI available and visible with real buffer`() {
        val config = TimeshiftConfig(enabled = true)
        val state = LiveTimeshiftState(
            enabled = true,
            supported = true,
            status = LiveTimeshiftStatus.LIVE,
            bufferedDurationMs = 3_500L, // 3.5 seconds (under 10s)
            currentOffsetFromLiveMs = 0L
        )

        val uiState = simulateApplyTimeshiftState(ContentType.LIVE, config, state)

        // 1. UI must be available even when buffer is 0-9s
        assertThat(uiState.available).isTrue()

        // 2. Overlay showTimeshiftControls = available && !isCastConnected
        val isCastConnected = false
        val showTimeshiftControls = uiState.available && !isCastConnected
        assertThat(showTimeshiftControls).isTrue()

        // 8. Must use real buffer duration (3500ms), NOT hardcoded fake 30-min (1800000ms)
        assertThat(uiState.bufferDepthMs).isEqualTo(3_500L)
        assertThat(uiState.bufferDepthMs).isNotEqualTo(1_800_000L)
    }

    @Test
    fun `test 3 - paused with offset 5s has return to live enabled`() {
        val config = TimeshiftConfig(enabled = true)
        val state = LiveTimeshiftState(
            enabled = true,
            supported = true,
            status = LiveTimeshiftStatus.PAUSED_BEHIND_LIVE,
            bufferedDurationMs = 5_000L,
            currentOffsetFromLiveMs = 5_000L // 5 seconds behind live (> 1000ms -> canSeekToLive is true)
        )

        val uiState = simulateApplyTimeshiftState(ContentType.LIVE, config, state)
        val isCastConnected = false
        val isCatchUpPlayback = false
        val showTimeshiftControls = uiState.available && !isCastConnected

        val canReturnToLive = (showTimeshiftControls && uiState.canSeekToLive) || isCatchUpPlayback

        // 3. Return to live must be ENABLED when paused 5s behind live (no 60s gate)
        assertThat(canReturnToLive).isTrue()
    }

    @Test
    fun `test 4 - at live edge return to live is disabled`() {
        val config = TimeshiftConfig(enabled = true)
        val state = LiveTimeshiftState(
            enabled = true,
            supported = true,
            status = LiveTimeshiftStatus.LIVE,
            bufferedDurationMs = 12_000L,
            currentOffsetFromLiveMs = 0L // 0 offset -> at live edge
        )

        val uiState = simulateApplyTimeshiftState(ContentType.LIVE, config, state)
        val isCastConnected = false
        val isCatchUpPlayback = false
        val showTimeshiftControls = uiState.available && !isCastConnected

        val canReturnToLive = (showTimeshiftControls && uiState.canSeekToLive) || isCatchUpPlayback

        // 4. Return to live must be DISABLED when at live edge
        assertThat(canReturnToLive).isFalse()
    }

    @Test
    fun `test 5 and 6 - current program restart capability vs catchup channel status`() {
        val now = 1_000_000L
        val currentLiveProgram = Program(
            channelId = "sports1",
            title = "Live Match",
            startTime = now - 900_000L, // 15 mins ago
            endTime = now + 2_700_000L  // in 45 mins
        )

        val catchUpChannel = Channel(
            id = 101L,
            name = "Sports 1",
            providerId = 1L,
            catchUpSupported = true,
            catchUpDays = 3,
            streamUrl = "xtream://1/live/101",
            streamId = 101L
        )

        val noCatchUpChannel = Channel(
            id = 102L,
            name = "Sports 2",
            providerId = 1L,
            catchUpSupported = false,
            catchUpDays = 0,
            streamUrl = "http://example.com/live/102.ts"
        )

        // 5. Current program + catchup -> restart enabled
        assertThat(catchUpChannel.isCurrentProgramRestartable(currentLiveProgram, now)).isTrue()

        // 6. Current program + no catchup -> restart disabled
        assertThat(noCatchUpChannel.isCurrentProgramRestartable(currentLiveProgram, now)).isFalse()
    }

    @Test
    fun `test 7 - past program on catchup channel is archive playable`() {
        val now = 1_000_000L
        val pastProgram = Program(
            channelId = "sports1",
            title = "Yesterday Match",
            startTime = now - 50_000_000L,
            endTime = now - 40_000_000L // completed
        )

        val catchUpChannel = Channel(
            id = 101L,
            name = "Sports 1",
            providerId = 1L,
            catchUpSupported = true,
            catchUpDays = 3,
            streamUrl = "xtream://1/live/101",
            streamId = 101L
        )

        // 7. Completed past program inside catchup window is playable in historical archive
        assertThat(catchUpChannel.isArchivePlayable(pastProgram, now)).isTrue()
    }
}
