package com.kaynanamtv.app.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import com.kaynanamtv.app.ui.design.AppColors
import com.kaynanamtv.app.ui.theme.LocalVisualEffectsProfile
import com.kaynanamtv.app.ui.theme.ResolvedVisualEffectsTier
import kotlin.math.cos
import kotlin.math.sin

/**
 * Adaptive Streaming & Broadcast Ambience Background for KaynanamTV.
 * Automatically scales fidelity (FULL / BALANCED / LITE / OFF) based on VisualEffectsProfile.
 */
@Composable
fun PremiumAnimatedHomeBackground(
    modifier: Modifier = Modifier,
    glowColor: Color = AppColors.Brand,
    content: @Composable () -> Unit
) {
    val tier = LocalVisualEffectsProfile.current.tier

    when (tier) {
        ResolvedVisualEffectsTier.FULL -> {
            FullAnimatedHomeBackground(modifier = modifier, glowColor = glowColor, content = content)
        }
        ResolvedVisualEffectsTier.BALANCED -> {
            BalancedAnimatedHomeBackground(modifier = modifier, glowColor = glowColor, content = content)
        }
        ResolvedVisualEffectsTier.LITE,
        ResolvedVisualEffectsTier.OFF -> {
            StaticObsidianHomeBackground(modifier = modifier, glowColor = glowColor, content = content)
        }
    }
}

@Composable
private fun StaticObsidianHomeBackground(
    modifier: Modifier = Modifier,
    glowColor: Color = AppColors.Brand,
    content: @Composable () -> Unit
) {
    val animatedThemeColor by animateColorAsState(
        targetValue = glowColor,
        animationSpec = tween(durationMillis = 600, easing = FastOutSlowInEasing),
        label = "StaticThemeGlowColor"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                val w = size.width
                val h = size.height

                // 1. Deep Obsidian Base
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF050811),
                            Color(0xFF080D1A),
                            Color(0xFF04060C)
                        ),
                        startY = 0f,
                        endY = h
                    )
                )

                // 2. Subtle Static Studio Glow
                val auraRadius = (w * 0.65f).coerceAtLeast(600f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF3730A3).copy(alpha = 0.08f),
                            Color.Transparent
                        ),
                        center = Offset(w * 0.35f, h * 0.28f),
                        radius = auraRadius
                    ),
                    radius = auraRadius,
                    center = Offset(w * 0.35f, h * 0.28f)
                )

                // 3. Subtle Static Theme Accent Glow
                val themeRadius = (w * 0.60f).coerceAtLeast(550f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            animatedThemeColor.copy(alpha = 0.09f),
                            Color.Transparent
                        ),
                        center = Offset(w * 0.78f, h * 0.65f),
                        radius = themeRadius
                    ),
                    radius = themeRadius,
                    center = Offset(w * 0.78f, h * 0.65f)
                )

                // 4. Subtle Static Vignette Edge
                val vignetteRadius = (w * 0.88f).coerceAtLeast(h * 0.88f)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0x55000000),
                            Color(0xCC000000)
                        ),
                        center = Offset(w * 0.5f, h * 0.5f),
                        radius = vignetteRadius
                    )
                )
            }
    ) {
        content()
    }
}

@Composable
private fun BalancedAnimatedHomeBackground(
    modifier: Modifier = Modifier,
    glowColor: Color = AppColors.Brand,
    content: @Composable () -> Unit
) {
    val animatedThemeColor by animateColorAsState(
        targetValue = glowColor,
        animationSpec = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
        label = "BalancedThemeGlowColor"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "BalancedAmbienceTransition")

    val auraPhase by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 28000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "BalancedAuraPhase"
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                val w = size.width
                val h = size.height

                // Base
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF050811),
                            Color(0xFF080D1A),
                            Color(0xFF04060C)
                        ),
                        startY = 0f,
                        endY = h
                    )
                )

                // Slow Ambient Aura
                val auraCenterX = w * (0.35f + 0.08f * sin(auraPhase * 2f * Math.PI.toFloat()))
                val auraCenterY = h * (0.28f + 0.05f * cos(auraPhase * 2f * Math.PI.toFloat()))
                val auraRadius = (w * 0.65f).coerceAtLeast(600f)

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF3730A3).copy(alpha = 0.10f),
                            Color.Transparent
                        ),
                        center = Offset(auraCenterX, auraCenterY),
                        radius = auraRadius
                    ),
                    radius = auraRadius,
                    center = Offset(auraCenterX, auraCenterY)
                )

                // Theme accent glow
                val themeAuraRadius = (w * 0.62f).coerceAtLeast(600f)
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            animatedThemeColor.copy(alpha = 0.10f),
                            Color.Transparent
                        ),
                        center = Offset(w * 0.75f, h * 0.65f),
                        radius = themeAuraRadius
                    ),
                    radius = themeAuraRadius,
                    center = Offset(w * 0.75f, h * 0.65f)
                )

                // Edge Vignette
                val vignetteRadius = (w * 0.88f).coerceAtLeast(h * 0.88f)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0x55000000),
                            Color(0xCC000000)
                        ),
                        center = Offset(w * 0.5f, h * 0.5f),
                        radius = vignetteRadius
                    )
                )
            }
    ) {
        content()
    }
}

@Composable
private fun FullAnimatedHomeBackground(
    modifier: Modifier = Modifier,
    glowColor: Color = AppColors.Brand,
    content: @Composable () -> Unit
) {
    val animatedThemeColor by animateColorAsState(
        targetValue = glowColor,
        animationSpec = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
        label = "FullThemeGlowColor"
    )

    val infiniteTransition = rememberInfiniteTransition(label = "FullBroadcastAmbienceTransition")

    val wavePhase1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 24000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "FullWavePhase1"
    )

    val wavePhase2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 36000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "FullWavePhase2"
    )

    val glassDrift by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 32000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "FullGlassDrift"
    )

    val projectorSweep by infiniteTransition.animateFloat(
        initialValue = -0.3f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 30000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "FullProjectorSweep"
    )

    val ambientPulse by infiniteTransition.animateFloat(
        initialValue = 0.90f,
        targetValue = 1.10f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 20000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "FullAmbientPulse"
    )

    val wavePath1 = remember { Path() }
    val wavePath2 = remember { Path() }

    Box(
        modifier = modifier
            .fillMaxSize()
            .drawBehind {
                val w = size.width
                val h = size.height

                // 1. Deep Obsidian Canvas
                drawRect(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF050811),
                            Color(0xFF080D1A),
                            Color(0xFF04060C)
                        ),
                        startY = 0f,
                        endY = h
                    )
                )

                // 2. Ambient Studio Aura
                val auraCenterX = w * (0.35f + 0.10f * sin(wavePhase1 * 2f * Math.PI.toFloat()))
                val auraCenterY = h * (0.28f + 0.06f * cos(wavePhase1 * 2f * Math.PI.toFloat()))
                val auraRadius = (w * 0.65f * ambientPulse).coerceAtLeast(600f)

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color(0xFF3730A3).copy(alpha = 0.12f),
                            Color(0xFF0369A1).copy(alpha = 0.05f),
                            Color.Transparent
                        ),
                        center = Offset(auraCenterX, auraCenterY),
                        radius = auraRadius
                    ),
                    radius = auraRadius,
                    center = Offset(auraCenterX, auraCenterY)
                )

                // Theme accent glow
                val themeAuraX = w * (0.78f - 0.12f * glassDrift)
                val themeAuraY = h * (0.65f - 0.08f * (1f - glassDrift))
                val themeAuraRadius = (w * 0.68f).coerceAtLeast(650f)

                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            animatedThemeColor.copy(alpha = 0.14f),
                            animatedThemeColor.copy(alpha = 0.03f),
                            Color.Transparent
                        ),
                        center = Offset(themeAuraX, themeAuraY),
                        radius = themeAuraRadius
                    ),
                    radius = themeAuraRadius,
                    center = Offset(themeAuraX, themeAuraY)
                )

                // 3. Digital Glass Parallax
                val glass1Y = h * (0.15f + 0.08f * glassDrift)
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xFF1E293B).copy(alpha = 0.06f),
                            Color.Transparent
                        ),
                        start = Offset(0f, glass1Y),
                        end = Offset(w, glass1Y + h * 0.40f)
                    )
                )

                // 4. Signal Waves
                wavePath1.reset()
                val wave1BaseY = h * 0.38f
                val wave1Amplitude = h * 0.035f
                val wave1Freq = 2.5f * Math.PI.toFloat() / w
                val wave1Offset = wavePhase1 * 2f * Math.PI.toFloat()

                wavePath1.moveTo(0f, wave1BaseY + wave1Amplitude * sin(wave1Offset))
                var stepX = 0f
                val stepSize = (w / 32f).coerceAtLeast(20f)
                while (stepX <= w + stepSize) {
                    val y = wave1BaseY + wave1Amplitude * sin(stepX * wave1Freq + wave1Offset)
                    wavePath1.lineTo(stepX, y)
                    stepX += stepSize
                }

                drawPath(
                    path = wavePath1,
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xFF6366F1).copy(alpha = 0.07f),
                            Color(0xFF38BDF8).copy(alpha = 0.09f),
                            Color.Transparent
                        )
                    ),
                    style = Stroke(width = 1.8f)
                )

                // 5. Projector Light Sweep
                val sweepX = w * projectorSweep
                drawRect(
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color(0xFF818CF8).copy(alpha = 0.035f),
                            Color(0xFF38BDF8).copy(alpha = 0.025f),
                            Color.Transparent
                        ),
                        start = Offset(sweepX, 0f),
                        end = Offset(sweepX + w * 0.45f, h)
                    )
                )

                // 6. Cinematic Radial Vignette
                val vignetteRadius = (w * 0.88f).coerceAtLeast(h * 0.88f)
                drawRect(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            Color.Transparent,
                            Color.Transparent,
                            Color(0x66000000),
                            Color(0xDE000000)
                        ),
                        center = Offset(w * 0.5f, h * 0.5f),
                        radius = vignetteRadius
                    )
                )
            }
    ) {
        content()
    }
}

/**
 * Backwards compatible delegating component for existing callers.
 */
@Composable
fun AmbientGlowBackground(
    modifier: Modifier = Modifier,
    glowColor: Color = AppColors.Brand,
    content: @Composable () -> Unit
) {
    PremiumAnimatedHomeBackground(
        modifier = modifier,
        glowColor = glowColor,
        content = content
    )
}
