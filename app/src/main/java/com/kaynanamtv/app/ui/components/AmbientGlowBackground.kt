package com.kaynanamtv.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.kaynanamtv.app.ui.design.AppColors.Brand as Primary

@Composable
fun AmbientGlowBackground(
    modifier: Modifier = Modifier,
    glowColor: Color = Primary,
    content: @Composable () -> Unit
) {
    val animatedGlowColor by animateColorAsState(
        targetValue = glowColor,
        animationSpec = tween(durationMillis = 800),
        label = "AmbientGlowColorAnimation"
    )

    val ambientBrush = Brush.radialGradient(
        colors = listOf(
            animatedGlowColor.copy(alpha = 0.28f),
            animatedGlowColor.copy(alpha = 0.12f),
            Color(0xFF0F172A).copy(alpha = 0.95f),
            Color(0xFF0A0F1D)
        ),
        radius = 1400f
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(ambientBrush)
    ) {
        content()
    }
}
