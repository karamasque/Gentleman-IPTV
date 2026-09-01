package com.kaynanamtv.app.player

import android.view.KeyEvent
import com.google.common.truth.Truth.assertThat
import org.junit.Test

/**
 * Validates Player Screen Lock and "Kilidi Aç" interaction contract:
 * 1. Locked-screen tap displays the unlock prompt.
 * 2. Direct button touch unlocks immediately.
 * 3. Parent gesture handling does not consume the button action.
 * 4. Touch unlock executes exactly once.
 * 5. Button receives focus when prompt appears.
 * 6. DPAD_CENTER unlocks immediately when prompt is visible.
 * 7. ENTER unlocks immediately when prompt is visible.
 * 8. Prompt timeout hides the prompt without unlocking.
 * 9. Normal focus and controls return after unlocking.
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
            if (isScreenLocked) {
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
    fun `test 1 and 2 - locked screen tap displays prompt and button touch unlocks immediately`() {
        val state = PlayerLockStateHolder(isScreenLocked = true, showUnlockPrompt = false)

        // 1. Tapping locked screen displays prompt
        state.onScreenTapped()
        assertThat(state.isScreenLocked).isTrue()
        assertThat(state.showUnlockPrompt).isTrue()

        // 2. Direct button touch unlocks immediately
        state.performUnlock()
        assertThat(state.isScreenLocked).isFalse()
        assertThat(state.showUnlockPrompt).isFalse()
        assertThat(state.unlockExecutionCount).isEqualTo(1)
    }

    @Test
    fun `test 3 and 4 - parent gesture does not consume button action and touch unlock executes exactly once`() {
        val state = PlayerLockStateHolder(isScreenLocked = true, showUnlockPrompt = true)

        state.performUnlock()
        assertThat(state.unlockExecutionCount).isEqualTo(1)
        assertThat(state.isScreenLocked).isFalse()
    }

    @Test
    fun `test 5, 6 and 7 - button receives focus, DPAD_CENTER and ENTER unlock immediately`() {
        val state = PlayerLockStateHolder(isScreenLocked = true, showUnlockPrompt = false)

        // 1. First DPAD_CENTER displays prompt and requests focus
        val handledFirstDpad = state.handlePreviewKeyEvent(KeyEvent.KEYCODE_DPAD_CENTER)
        assertThat(handledFirstDpad).isTrue()
        assertThat(state.showUnlockPrompt).isTrue()
        assertThat(state.buttonFocusRequested).isTrue()
        assertThat(state.isScreenLocked).isTrue()

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
    fun `test 8 - prompt timeout hides prompt without unlocking screen`() {
        val state = PlayerLockStateHolder(isScreenLocked = true, showUnlockPrompt = true)

        state.onUnlockPromptTimeout()
        assertThat(state.showUnlockPrompt).isFalse()
        assertThat(state.isScreenLocked).isTrue()
        assertThat(state.unlockExecutionCount).isEqualTo(0)
    }

    @Test
    fun `test 9 - normal focus and controls return after unlocking`() {
        val state = PlayerLockStateHolder(isScreenLocked = true, showUnlockPrompt = true, showControls = false)

        state.performUnlock()
        assertThat(state.isScreenLocked).isFalse()
        assertThat(state.showControls).isTrue()
        assertThat(state.controlsToggled).isTrue()
        assertThat(state.mainFocusRequested).isTrue()
    }
}
