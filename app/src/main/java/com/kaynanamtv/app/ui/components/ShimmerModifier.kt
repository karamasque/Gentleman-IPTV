package com.kaynanamtv.app.ui.components

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.unit.IntSize

fun Modifier.shimmer(
    visible: Boolean = true,
    shimmerColor: Color = Color(0xFF1B2435),
    highlightColor: Color = Color(0xFF2C394F)
): Modifier = composed {
    if (!visible) return@composed this

    var size by remember { mutableStateOf(IntSize.Zero) }
    val transition = rememberInfiniteTransition(label = "shimmer")
    val translateAnim by transition.animateFloat(
        initialValue = -500f,
        targetValue = 1500f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1400, easing = androidx.compose.animation.core.LinearEasing)
        ),
        label = "shimmer_translation"
    )

    val brush = remember(translateAnim, size) {
        if (size.width == 0 || size.height == 0) {
            Brush.linearGradient(
                colors = listOf(shimmerColor, shimmerColor),
                start = Offset.Zero,
                end = Offset.Zero
            )
        } else {
            val offset = translateAnim * (size.width.toFloat() / 500f)
            Brush.linearGradient(
                colors = listOf(
                    shimmerColor,
                    highlightColor,
                    shimmerColor
                ),
                start = Offset(x = offset - size.width.toFloat(), y = 0f),
                end = Offset(x = offset, y = size.height.toFloat())
            )
        }
    }

    Modifier
        .onGloballyPositioned { size = it.size }
        .background(brush)
}
