package com.kaynanamtv.app.ui.screens.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlayerSeekAutoHideTest {

    @Test
    fun `shouldCancelControlsAutoHide returns true when scrubbing is active`() {
        // Controls visible, no active dialog, but user is scrubbing -> MUST cancel auto-hide
        val cancel = shouldCancelControlsAutoHide(
            showControls = true,
            isScrubbing = true,
            hasActiveDialog = false
        )
        assertThat(cancel).isTrue()
    }

    @Test
    fun `shouldCancelControlsAutoHide returns true when controls are not visible`() {
        val cancel = shouldCancelControlsAutoHide(
            showControls = false,
            isScrubbing = false,
            hasActiveDialog = false
        )
        assertThat(cancel).isTrue()
    }

    @Test
    fun `shouldCancelControlsAutoHide returns true when dialog is open`() {
        val cancel = shouldCancelControlsAutoHide(
            showControls = true,
            isScrubbing = false,
            hasActiveDialog = true
        )
        assertThat(cancel).isTrue()
    }

    @Test
    fun `shouldCancelControlsAutoHide returns false only when controls are visible and idle`() {
        // Controls visible, not scrubbing, no dialog -> schedule/run auto-hide
        val cancel = shouldCancelControlsAutoHide(
            showControls = true,
            isScrubbing = false,
            hasActiveDialog = false
        )
        assertThat(cancel).isFalse()
    }

    @Test
    fun `scrubbing cancels auto-hide even if dialog is closed and controls are visible`() {
        assertThat(shouldCancelControlsAutoHide(showControls = true, isScrubbing = true, hasActiveDialog = false)).isTrue()
        // Scrub ends -> auto-hide is permitted to run
        assertThat(shouldCancelControlsAutoHide(showControls = true, isScrubbing = false, hasActiveDialog = false)).isFalse()
    }
}
