package com.kaynanamtv.app.ui.interaction

import android.view.InputDevice
import android.view.MotionEvent
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.LocalContext
import com.kaynanamtv.app.device.isTelevisionDevice

/**
 * Makes a TV-Surface-wrapped composable respond correctly to both remote-pointer
 * (mouse/trackpad) and finger-touch input.
 *
 * - **TV / no touchscreen**: existing `pointerInteropFilter` path (mouse pointer clicks).
 * - **Phone / tablet**: `pointerInput + detectTapGestures` fires [onClick] on the FIRST
 *   tap (and [onLongClick] on a long-press) by intercepting the pointer event before the
 *   TV Surface's focus-first machinery can suppress it.  `detectTapGestures` consumes
 *   the event so the inner TV Surface's `combinedClickable` does not double-fire.
 */
fun Modifier.mouseClickable(
    enabled: Boolean = true,
    focusRequester: FocusRequester? = null,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
): Modifier = composed {
    if (!enabled) return@composed this
    var pressedFromPointer by remember { mutableStateOf(false) }

    if (onLongClick != null) {
        this.pointerInput(onClick, onLongClick) {
            detectTapGestures(
                onTap = { _ ->
                    android.util.Log.d("LiveActionTrace", "[LIVE_ACTION_TRACE] input=TOUCH onTap firing onClick")
                    onClick()
                },
                onLongPress = { _ -> onLongClick() }
            )
        }
    } else {
        this.pointerInteropFilter { event ->
            when (event.actionMasked) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_BUTTON_PRESS -> {
                    pressedFromPointer = true
                    focusRequester?.requestFocus()
                    true
                }
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_BUTTON_RELEASE -> {
                    val shouldClick = pressedFromPointer
                    pressedFromPointer = false
                    if (shouldClick) {
                        val inputType = when {
                            event.isFromSource(InputDevice.SOURCE_MOUSE) -> "MOUSE"
                            event.isFromSource(InputDevice.SOURCE_TOUCHSCREEN) -> "TOUCH"
                            event.isFromSource(InputDevice.SOURCE_TOUCHPAD) -> "TOUCHPAD"
                            else -> "POINTER"
                        }
                        android.util.Log.d("LiveActionTrace", "[LIVE_ACTION_TRACE] input=$inputType firing onClick")
                        onClick()
                    }
                    shouldClick
                }
                MotionEvent.ACTION_CANCEL -> {
                    pressedFromPointer = false
                    false
                }
                else -> false
            }
        }
    }
}
