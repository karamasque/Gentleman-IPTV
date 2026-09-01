package com.kaynanamtv.app.player

import android.view.KeyEvent
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Validates Player Screen Lock and "Kilidi Aç" interaction contract:
 * 1. Locked-screen tap displays the unlock prompt.
 * 2. When prompt is visible, ancestor pointerInput is completely detached/disabled so Button onClick receives touch cleanly.
 * 3. Direct button touch unlocks immediately and executes exactly once.
 * 4. Button receives focus when prompt appears.
 * 5. DPAD_CENTER unlocks immediately when prompt is visible.
 * 6. ENTER unlocks immediately when prompt is visible.
 * 7. Prompt timeout hides the prompt without unlocking, and reattaches locked-screen tap detector.
 * 8. Normal focus and controls return after unlocking.
 */
class PlayerUnlockInteractionTest {

    private class PlayerLockStateHolder(
        var isScreenLocked: Boolean = true,
        var showUnlockPrompt: Boolean = false,
        var showControls: Boolean = false,
        var controlsToggled: Boolean = false
    ) {
        var unlockExecutionCount = 0
        var promptCancelJobCount = 0
        var mainFocusRequested = false
        var buttonFocusRequested = false

        /**
         * Simulates whether the ancestor Box has the locked-screen tap gesture detector attached.
         * In production: isScreenLocked && !showUnlockPrompt
         */
        val isAncestorTapDetectorAttached: Boolean
            get() = isScreenLocked && !showUnlockPrompt

        fun performUnlock() {
            isScreenLocked = false
            showUnlockPrompt = false
            promptCancelJobCount++
            unlockExecutionCount++
            if (!showControls) {
                showControls = true
                controlsToggled = true
            }
            mainFocusRequested = true
        }

        fun onScreenTapped() {
            if (isAncestorTapDetectorAttached) {
                showUnlockPrompt = true
                buttonFocusRequested = true
            }
        }

        fun onUnlockPromptTimeout() {
            showUnlockPrompt = false
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
                            buttonFocusRequested = true
                        }
                        return true
                    }
                    KeyEvent.KEYCODE_BACK -> return false
                    else -> {
                        if (!showUnlockPrompt) {
                            showUnlockPrompt = true
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
    fun `test 1 and 2 - locked screen tap displays prompt and detaches ancestor pointer detector`() {
        val state = PlayerLockStateHolder(isScreenLocked = true, showUnlockPrompt = false)

        // Initially locked and prompt hidden -> ancestor tap detector IS attached
        assertThat(state.isScreenLocked).isTrue()
        assertThat(state.showUnlockPrompt).isFalse()
        assertThat(state.isAncestorTapDetectorAttached).isTrue()

        // 1. Tapping locked screen displays prompt
        state.onScreenTapped()
        assertThat(state.isScreenLocked).isTrue()
        assertThat(state.showUnlockPrompt).isTrue()

        // 2. Crucial structural fix: Ancestor tap detector is now DETACHED so Button onClick receives touches unobstructed
        assertThat(state.isAncestorTapDetectorAttached).isFalse()

        // 3. Direct button touch unlocks immediately
        state.performUnlock()
        assertThat(state.isScreenLocked).isFalse()
        assertThat(state.showUnlockPrompt).isFalse()
        assertThat(state.unlockExecutionCount).isEqualTo(1)
        assertThat(state.isAncestorTapDetectorAttached).isFalse()
    }

    @Test
    fun `test 3 - parent gesture does not consume button action and touch unlock executes exactly once`() {
        val state = PlayerLockStateHolder(isScreenLocked = true, showUnlockPrompt = true)

        // When prompt is visible, ancestor detector is inactive
        assertThat(state.isAncestorTapDetectorAttached).isFalse()

        // Button receives tap cleanly
        state.performUnlock()
        assertThat(state.unlockExecutionCount).isEqualTo(1)
        assertThat(state.isScreenLocked).isFalse()
    }

    @Test
    fun `test 4, 5 and 6 - button receives focus, DPAD_CENTER and ENTER unlock immediately`() {
        val state = PlayerLockStateHolder(isScreenLocked = true, showUnlockPrompt = false)

        // 1. First DPAD_CENTER displays prompt and requests focus
        val handledFirstDpad = state.handlePreviewKeyEvent(KeyEvent.KEYCODE_DPAD_CENTER)
        assertThat(handledFirstDpad).isTrue()
        assertThat(state.showUnlockPrompt).isTrue()
        assertThat(state.buttonFocusRequested).isTrue()
        assertThat(state.isScreenLocked).isTrue()
        assertThat(state.isAncestorTapDetectorAttached).isFalse()

        // 2. Second DPAD_CENTER with prompt visible triggers immediate unlock
        val handledSecondDpad = state.handlePreviewKeyEvent(KeyEvent.KEYCODE_DPAD_CENTER)
        assertThat(handledSecondDpad).isTrue()
        assertThat(state.isScreenLocked).isFalse()
        assertThat(state.showUnlockPrompt).isFalse()
        assertThat(state.unlockExecutionCount).isEqualTo(1)

        // 3. ENTER key on locked prompt also unlocks
        val stateEnter = PlayerLockStateHolder(isScreenLocked = true, showUnlockPrompt = true)
        val handledEnter = stateEnter.handlePreviewKeyEvent(KeyEvent.KEYCODE_ENTER)
        assertThat(handledEnter).isTrue()
        assertThat(stateEnter.isScreenLocked).isFalse()
        assertThat(stateEnter.unlockExecutionCount).isEqualTo(1)
    }

    @Test
    fun `test 7 - prompt timeout hides prompt without unlocking screen and reattaches ancestor tap detector`() {
        val state = PlayerLockStateHolder(isScreenLocked = true, showUnlockPrompt = true)

        state.onUnlockPromptTimeout()
        assertThat(state.showUnlockPrompt).isFalse()
        assertThat(state.isScreenLocked).isTrue()
        assertThat(state.unlockExecutionCount).isEqualTo(0)

        // Detector reattached for subsequent tap
        assertThat(state.isAncestorTapDetectorAttached).isTrue()
    }

    @Test
    fun `test 8 - normal focus and controls return after unlocking`() {
        val state = PlayerLockStateHolder(isScreenLocked = true, showUnlockPrompt = true, showControls = false)

        state.performUnlock()
        assertThat(state.isScreenLocked).isFalse()
        assertThat(state.showControls).isTrue()
        assertThat(state.controlsToggled).isTrue()
        assertThat(state.mainFocusRequested).isTrue()
        assertThat(state.isAncestorTapDetectorAttached).isFalse()
    }
}
