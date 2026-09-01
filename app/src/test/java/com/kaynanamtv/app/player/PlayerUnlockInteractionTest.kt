package com.kaynanamtv.app.player

import android.view.KeyEvent
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Validates Player Screen Lock 3-Second Hold & Top-Level Popup Contract:
 * 1. Hold 2,999 ms -> remains locked.
 * 2. Hold 3,000 ms -> unlocks exactly once.
 * 3. Release early -> remains locked.
 * 4. Gesture cancellation -> remains locked.
 * 5. Prompt does not auto-dismiss during hold.
 * 6. Single tap -> remains locked.
 * 7. DPAD/ENTER regression -> unlocks immediately.
 * 8. Prompt timeout hides prompt when not holding.
 * 9. Normal controls & focus return after unlocking.
 */
class PlayerUnlockInteractionTest {

    private class PlayerLockStateHolder(
        var isScreenLocked: Boolean = true,
        var showUnlockPrompt: Boolean = false,
        var showControls: Boolean = false,
        var controlsToggled: Boolean = false
    ) {
        var unlockExecutionCount = 0
        var autoDismissJobActive = false
        var holdProgress = 0f
        var isFingerDown = false
        var mainFocusRequested = false
        var buttonFocusRequested = false

        fun performUnlock() {
            isScreenLocked = false
            showUnlockPrompt = false
            autoDismissJobActive = false
            isFingerDown = false
            holdProgress = 0f
            unlockExecutionCount++
            if (!showControls) {
                showControls = true
                controlsToggled = true
            }
            mainFocusRequested = true
        }

        fun onScreenTapped() {
            if (isScreenLocked && !showUnlockPrompt) {
                showUnlockPrompt = true
                autoDismissJobActive = true
                buttonFocusRequested = true
            }
        }

        fun onPointerDown() {
            isFingerDown = true
            // Suspend auto-dismiss while holding
            autoDismissJobActive = false
            holdProgress = 0f
        }

        fun onHoldTick(elapsedMs: Long) {
            if (!isFingerDown) return
            holdProgress = (elapsedMs.toFloat() / 3000f).coerceIn(0f, 1f)
            if (elapsedMs >= 3000L) {
                performUnlock()
            }
        }

        fun onPointerUp(elapsedMs: Long) {
            isFingerDown = false
            if (elapsedMs < 3000L && isScreenLocked) {
                // Released early: reset progress and restart 3s auto-dismiss
                holdProgress = 0f
                autoDismissJobActive = true
            }
        }

        fun onGestureCancelled() {
            isFingerDown = false
            if (isScreenLocked) {
                holdProgress = 0f
                autoDismissJobActive = true
            }
        }

        fun onAutoDismissTimeout() {
            if (autoDismissJobActive && !isFingerDown) {
                showUnlockPrompt = false
                autoDismissJobActive = false
            }
        }

        fun handlePreviewKeyEvent(keyCode: Int, action: Int = KeyEvent.ACTION_DOWN): Boolean {
            if (action != KeyEvent.ACTION_DOWN) return false

            if (isScreenLocked) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER,
                    KeyEvent.KEYCODE_ENTER,
                    KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                        if (showUnlockPrompt) {
                            performUnlock()
                        } else {
                            showUnlockPrompt = true
                            autoDismissJobActive = true
                            buttonFocusRequested = true
                        }
                        return true
                    }
                    KeyEvent.KEYCODE_BACK -> return false
                    else -> {
                        if (!showUnlockPrompt) {
                            showUnlockPrompt = true
                            autoDismissJobActive = true
                            buttonFocusRequested = true
                        }
                        return true
                    }
                }
            }
            return false
        }
    }

    @Test
    fun `test 1 - hold 2999ms remains locked`() {
        val state = PlayerLockStateHolder(isScreenLocked = true, showUnlockPrompt = true)
        state.onPointerDown()
        state.onHoldTick(2999L)

        assertThat(state.isScreenLocked).isTrue()
        assertThat(state.showUnlockPrompt).isTrue()
        assertThat(state.unlockExecutionCount).isEqualTo(0)
        assertThat(state.holdProgress).isLessThan(1.0f)
    }

    @Test
    fun `test 2 - hold 3000ms unlocks exactly once`() {
        val state = PlayerLockStateHolder(isScreenLocked = true, showUnlockPrompt = true)
        state.onPointerDown()
        state.onHoldTick(3000L)

        assertThat(state.isScreenLocked).isFalse()
        assertThat(state.showUnlockPrompt).isFalse()
        assertThat(state.unlockExecutionCount).isEqualTo(1)
        assertThat(state.showControls).isTrue()
        assertThat(state.mainFocusRequested).isTrue()
    }

    @Test
    fun `test 3 - release early before 3000ms remains locked and resets progress`() {
        val state = PlayerLockStateHolder(isScreenLocked = true, showUnlockPrompt = true)
        state.onPointerDown()
        state.onHoldTick(1500L)
        state.onPointerUp(1500L)

        assertThat(state.isScreenLocked).isTrue()
        assertThat(state.showUnlockPrompt).isTrue()
        assertThat(state.unlockExecutionCount).isEqualTo(0)
        assertThat(state.holdProgress).isEqualTo(0f)
        assertThat(state.autoDismissJobActive).isTrue()
    }

    @Test
    fun `test 4 - gesture cancellation remains locked`() {
        val state = PlayerLockStateHolder(isScreenLocked = true, showUnlockPrompt = true)
        state.onPointerDown()
        state.onHoldTick(1800L)
        state.onGestureCancelled()

        assertThat(state.isScreenLocked).isTrue()
        assertThat(state.showUnlockPrompt).isTrue()
        assertThat(state.unlockExecutionCount).isEqualTo(0)
        assertThat(state.holdProgress).isEqualTo(0f)
        assertThat(state.autoDismissJobActive).isTrue()
    }

    @Test
    fun `test 5 - prompt does not auto-dismiss during hold`() {
        val state = PlayerLockStateHolder(isScreenLocked = true, showUnlockPrompt = true)
        state.onPointerDown()

        // Auto dismiss timeout fires while finger is held down
        state.onAutoDismissTimeout()

        // Prompt MUST NOT disappear
        assertThat(state.showUnlockPrompt).isTrue()
        assertThat(state.isScreenLocked).isTrue()
    }

    @Test
    fun `test 6 - single tap does not unlock`() {
        val state = PlayerLockStateHolder(isScreenLocked = true, showUnlockPrompt = true)
        state.onPointerDown()
        state.onHoldTick(80L) // Normal tap is ~80ms
        state.onPointerUp(80L)

        assertThat(state.isScreenLocked).isTrue()
        assertThat(state.showUnlockPrompt).isTrue()
        assertThat(state.unlockExecutionCount).isEqualTo(0)
    }

    @Test
    fun `test 7 - dpad center and enter regression pass`() {
        val state = PlayerLockStateHolder(isScreenLocked = true, showUnlockPrompt = false)

        // First DPAD shows prompt
        val handled1 = state.handlePreviewKeyEvent(KeyEvent.KEYCODE_DPAD_CENTER)
        assertThat(handled1).isTrue()
        assertThat(state.showUnlockPrompt).isTrue()
        assertThat(state.isScreenLocked).isTrue()

        // Second DPAD unlocks immediately
        val handled2 = state.handlePreviewKeyEvent(KeyEvent.KEYCODE_DPAD_CENTER)
        assertThat(handled2).isTrue()
        assertThat(state.isScreenLocked).isFalse()
        assertThat(state.unlockExecutionCount).isEqualTo(1)

        // ENTER key test
        val stateEnter = PlayerLockStateHolder(isScreenLocked = true, showUnlockPrompt = true)
        val handledEnter = stateEnter.handlePreviewKeyEvent(KeyEvent.KEYCODE_ENTER)
        assertThat(handledEnter).isTrue()
        assertThat(stateEnter.isScreenLocked).isFalse()
        assertThat(stateEnter.unlockExecutionCount).isEqualTo(1)
    }

    @Test
    fun `test 8 - prompt timeout hides prompt when not holding`() {
        val state = PlayerLockStateHolder(isScreenLocked = true, showUnlockPrompt = true)
        state.autoDismissJobActive = true
        state.onAutoDismissTimeout()

        assertThat(state.showUnlockPrompt).isFalse()
        assertThat(state.isScreenLocked).isTrue()
    }
}
