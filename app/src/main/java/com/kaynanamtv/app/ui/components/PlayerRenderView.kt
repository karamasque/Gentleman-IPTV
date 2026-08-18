package com.kaynanamtv.app.ui.components

import android.view.View
import android.view.ViewGroup
import android.view.TextureView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.key
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.viewinterop.AndroidView
import com.kaynanamtv.player.PlayerEngine
import com.kaynanamtv.player.PlayerRenderSurfaceType
import com.kaynanamtv.player.PlayerSurfaceResizeMode
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

@Composable
fun PlayerRenderView(
    playerEngine: PlayerEngine,
    resizeMode: PlayerSurfaceResizeMode,
    modifier: Modifier = Modifier,
    surfaceType: PlayerRenderSurfaceType = PlayerRenderSurfaceType.AUTO,
    onColorDetected: ((Color) -> Unit)? = null,
    configureView: (View.() -> Unit)? = null
) {
    var renderViewRef by remember { mutableStateOf<View?>(null) }

    LaunchedEffect(renderViewRef, onColorDetected) {
        val currentView = renderViewRef ?: return@LaunchedEffect
        if (onColorDetected == null) return@LaunchedEffect

        try {
            while (isActive && renderViewRef === currentView) {
                delay(480) // Sample color every 480ms for responsive but smooth ambilight changes
                if (!isActive || renderViewRef !== currentView) break
                val textureView = findTextureView(currentView)
                if (textureView != null && textureView.isAvailable && textureView.windowToken != null) {
                    runCatching {
                        if (!textureView.isAvailable) return@runCatching
                        // Extract a small 8x8 frame to minimize CPU/GPU load (takes < 1ms)
                        val bitmap = textureView.getBitmap(8, 8)
                        if (bitmap != null && !bitmap.isRecycled) {
                            var redSum = 0L
                            var greenSum = 0L
                            var blueSum = 0L
                            val count = bitmap.width * bitmap.height
                            val pixels = IntArray(count)
                            bitmap.getPixels(pixels, 0, bitmap.width, 0, 0, bitmap.width, bitmap.height)
                            
                            for (pixel in pixels) {
                                redSum += android.graphics.Color.red(pixel)
                                greenSum += android.graphics.Color.green(pixel)
                                blueSum += android.graphics.Color.blue(pixel)
                            }
                            bitmap.recycle()

                            val avgRed = (redSum / count).toInt()
                            val avgGreen = (greenSum / count).toInt()
                            val avgBlue = (blueSum / count).toInt()

                            if (isActive && renderViewRef === currentView) {
                                // Ignore very dark colors/black frames to keep the ambient aura subtle
                                if (avgRed > 12 || avgGreen > 12 || avgBlue > 12) {
                                    onColorDetected(Color(avgRed, avgGreen, avgBlue))
                                } else {
                                    onColorDetected(Color.Transparent)
                                }
                            }
                        }
                    }
                }
            }
        } finally {
            onColorDetected?.invoke(Color.Transparent)
        }
    }

    key(playerEngine, surfaceType, resizeMode) {
        AndroidView(
            factory = { context ->
                playerEngine.createRenderView(context, resizeMode, surfaceType).apply {
                    isFocusable = false
                    isFocusableInTouchMode = false
                    renderViewRef = this
                    configureView?.invoke(this)
                }
            },
            update = { renderView ->
                renderViewRef = renderView
                playerEngine.bindRenderView(renderView, resizeMode)
            },
            onRelease = { renderView ->
                renderViewRef = null
                playerEngine.clearRenderBinding()
                playerEngine.releaseRenderView(renderView)
            },
            modifier = modifier
        )
    }
}

private fun findTextureView(view: View): TextureView? {
    if (view is TextureView) return view
    if (view is ViewGroup) {
        for (i in 0 until view.childCount) {
            val child = view.getChildAt(i)
            val result = findTextureView(child)
            if (result != null) return result
        }
    }
    return null
}