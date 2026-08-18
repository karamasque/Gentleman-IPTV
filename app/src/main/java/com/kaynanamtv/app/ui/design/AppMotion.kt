package com.kaynanamtv.app.ui.design

import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.tween

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring

object AppMotion {
    const val Fast = 120
    const val Standard = 160
    const val Emphasis = 220
    const val Slow = 300

    val FocusSpec: FiniteAnimationSpec<Float> = tween(
        durationMillis = Standard,
        easing = LinearOutSlowInEasing
    )

    val SmoothSpring = spring<Float>(
        dampingRatio = Spring.DampingRatioLowBouncy,
        stiffness = Spring.StiffnessMediumLow
    )

    val ScaleFocused = 1.05f
    val ScaleUnfocused = 1.0f
}
