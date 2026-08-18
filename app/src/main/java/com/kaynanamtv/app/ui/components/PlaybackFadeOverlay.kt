package com.kaynanamtv.app.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput

/**
 * Kanal veya video geçişlerinde ekranın siyahtan yumuşakça (fade-in) yayına açılmasını sağlayan katman.
 * Oynatıcı oynatılıyor (isPlaying = true) durumuna geçtiğinde 600ms içinde kaybolur.
 */
@Composable
fun PlaybackFadeOverlay(
    isPlaying: Boolean,
    modifier: Modifier = Modifier
) {
    var hasPlayerStarted by remember { mutableStateOf(false) }

    LaunchedEffect(isPlaying) {
        if (isPlaying) {
            hasPlayerStarted = true
        }
    }

    val alpha by animateFloatAsState(
        targetValue = if (hasPlayerStarted) 0f else 1f,
        animationSpec = tween(durationMillis = 600),
        label = "playbackFadeAlpha"
    )

    if (alpha > 0.01f) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = alpha))
                .pointerInput(Unit) {}
        )
    }
}
