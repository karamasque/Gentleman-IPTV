package com.kaynanamtv.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.kaynanamtv.app.ui.accessibility.rememberReducedMotionEnabled
import coil3.request.ImageRequest
import coil3.request.crossfade
import coil3.asDrawable
import androidx.compose.ui.graphics.Color
import androidx.core.graphics.drawable.toBitmap

@Composable
fun rememberCrossfadeImageModel(data: Any?): Any? {
    val context = LocalContext.current
    val reducedMotionEnabled = rememberReducedMotionEnabled()
    return remember(context, data, reducedMotionEnabled) {
        data?.let {
            ImageRequest.Builder(context)
                .data(it)
                .crossfade(!reducedMotionEnabled)
                .build()
        }
    }
}

fun extractDominantColor(coilImage: coil3.Image, context: android.content.Context): androidx.compose.ui.graphics.Color? {
    return try {
        val drawable = coilImage.asDrawable(context.resources) ?: return null
        val bitmap = if (drawable is android.graphics.drawable.BitmapDrawable) {
            drawable.bitmap
        } else {
            val b = android.graphics.Bitmap.createBitmap(50, 50, android.graphics.Bitmap.Config.ARGB_8888)
            val canvas = android.graphics.Canvas(b)
            drawable.setBounds(0, 0, canvas.width, canvas.height)
            drawable.draw(canvas)
            b
        }
        val scaledBitmap = if (bitmap.width > 24 || bitmap.height > 24) {
            android.graphics.Bitmap.createScaledBitmap(bitmap, 24, 24, false)
        } else {
            bitmap
        }
        var red = 0L
        var green = 0L
        var blue = 0L
        var count = 0
        for (x in 0 until scaledBitmap.width) {
            for (y in 0 until scaledBitmap.height) {
                val pixel = scaledBitmap.getPixel(x, y)
                val alpha = android.graphics.Color.alpha(pixel)
                if (alpha < 200) continue
                
                val r = android.graphics.Color.red(pixel)
                val g = android.graphics.Color.green(pixel)
                val b = android.graphics.Color.blue(pixel)
                
                val brightness = (r * 299 + g * 587 + b * 114) / 1000
                if (brightness < 30 || brightness > 220) continue
                
                red += r
                green += g
                blue += b
                count++
            }
        }
        if (scaledBitmap != bitmap) {
            scaledBitmap.recycle()
        }
        if (count > 0) {
            androidx.compose.ui.graphics.Color(
                red = (red / count).toInt(),
                green = (green / count).toInt(),
                blue = (blue / count).toInt()
            )
        } else null
    } catch (e: Exception) {
        null
    }
}