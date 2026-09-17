package com.kaynanamtv.app.ui.screens.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.kaynanamtv.domain.model.PlayerHudTheme

val LocalPlayerHudTheme = compositionLocalOf { PlayerHudTheme.DEFAULT }

/**
 * UI styling tokens resolved dynamically from [PlayerHudTheme].
 */
data class PlayerHudThemeUiTokens(
    val theme: PlayerHudTheme,
    val primary: Color,
    val accent: Color,
    val glow: Color,
    val background: Color,
    val dockBackground: Color,
    val dockBorderGradient: List<Color>,
    val progressGradient: List<Color>,
    val trackBackground: Color,
    val thumbColor: Color,
    val thumbGlow: Color,
    val buttonNormalContainer: Color,
    val buttonNormalBorder: Color,
    val buttonFocusedContainer: Color,
    val buttonFocusedBorder: Color,
    val primaryButtonContainer: Color,
    val primaryButtonBorder: Color,
    val primaryButtonFocusedContainer: Color,
    val primaryButtonFocusedBorder: Color,
    val badgeContainer: Color,
    val badgeTextColor: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val hintText: Color,
    val isGlassmorphism: Boolean,
    val isMinimalist: Boolean
) {
    val dockBorderBrush: Brush
        get() = Brush.linearGradient(dockBorderGradient)

    val progressBrush: Brush
        get() = Brush.horizontalGradient(progressGradient)
}

fun PlayerHudTheme.toUiTokens(): PlayerHudThemeUiTokens {
    val prim = Color(primaryColorHex)
    val acc = Color(accentColorHex)
    val gl = Color(glowColorHex)
    val bg = Color(backgroundColorHex)

    return when (this) {
        PlayerHudTheme.MODERN_GLASS -> PlayerHudThemeUiTokens(
            theme = this,
            primary = prim,
            accent = acc,
            glow = gl,
            background = bg,
            dockBackground = bg,
            dockBorderGradient = listOf(
                prim.copy(alpha = 0.55f),
                acc.copy(alpha = 0.45f),
                prim.copy(alpha = 0.25f)
            ),
            progressGradient = listOf(prim, Color(0xFF4FACFE), acc),
            trackBackground = Color(0xFF1E293B).copy(alpha = 0.65f),
            thumbColor = Color.White,
            thumbGlow = prim,
            buttonNormalContainer = Color(0xFF1E293B).copy(alpha = 0.50f),
            buttonNormalBorder = Color.White.copy(alpha = 0.12f),
            buttonFocusedContainer = acc.copy(alpha = 0.35f),
            buttonFocusedBorder = prim,
            primaryButtonContainer = prim.copy(alpha = 0.28f),
            primaryButtonBorder = prim.copy(alpha = 0.60f),
            primaryButtonFocusedContainer = prim.copy(alpha = 0.50f),
            primaryButtonFocusedBorder = Color.White,
            badgeContainer = prim.copy(alpha = 0.18f),
            badgeTextColor = prim,
            textPrimary = Color(0xFFF8FAFC),
            textSecondary = Color(0xFF94A3B8),
            hintText = Color(0xFF94A3B8).copy(alpha = 0.70f),
            isGlassmorphism = true,
            isMinimalist = false
        )

        PlayerHudTheme.NEON_CYBER -> PlayerHudThemeUiTokens(
            theme = this,
            primary = prim,
            accent = acc,
            glow = gl,
            background = bg,
            dockBackground = bg,
            dockBorderGradient = listOf(
                acc.copy(alpha = 0.70f),
                prim.copy(alpha = 0.70f),
                acc.copy(alpha = 0.35f)
            ),
            progressGradient = listOf(acc, prim),
            trackBackground = Color(0xFF111118).copy(alpha = 0.80f),
            thumbColor = Color.White,
            thumbGlow = prim,
            buttonNormalContainer = Color(0xFF0F0F1A).copy(alpha = 0.70f),
            buttonNormalBorder = prim.copy(alpha = 0.25f),
            buttonFocusedContainer = acc.copy(alpha = 0.35f),
            buttonFocusedBorder = prim,
            primaryButtonContainer = acc.copy(alpha = 0.30f),
            primaryButtonBorder = acc.copy(alpha = 0.70f),
            primaryButtonFocusedContainer = acc.copy(alpha = 0.55f),
            primaryButtonFocusedBorder = prim,
            badgeContainer = acc.copy(alpha = 0.20f),
            badgeTextColor = acc,
            textPrimary = Color.White,
            textSecondary = Color(0xFF00FFFF).copy(alpha = 0.75f),
            hintText = Color(0xFFA0AEC0).copy(alpha = 0.70f),
            isGlassmorphism = false,
            isMinimalist = false
        )

        PlayerHudTheme.MINIMALIST_CLEAN -> PlayerHudThemeUiTokens(
            theme = this,
            primary = prim,
            accent = acc,
            glow = gl,
            background = bg,
            dockBackground = bg,
            dockBorderGradient = listOf(
                Color.White.copy(alpha = 0.22f),
                Color.White.copy(alpha = 0.08f)
            ),
            progressGradient = listOf(Color.White, Color(0xFFE2E8F0)),
            trackBackground = Color(0xFF2D3748).copy(alpha = 0.50f),
            thumbColor = Color.White,
            thumbGlow = Color.White.copy(alpha = 0.5f),
            buttonNormalContainer = Color.White.copy(alpha = 0.06f),
            buttonNormalBorder = Color.White.copy(alpha = 0.12f),
            buttonFocusedContainer = Color.White.copy(alpha = 0.22f),
            buttonFocusedBorder = Color.White,
            primaryButtonContainer = Color.White.copy(alpha = 0.18f),
            primaryButtonBorder = Color.White.copy(alpha = 0.40f),
            primaryButtonFocusedContainer = Color.White.copy(alpha = 0.35f),
            primaryButtonFocusedBorder = Color.White,
            badgeContainer = Color.White.copy(alpha = 0.15f),
            badgeTextColor = Color.White,
            textPrimary = Color.White,
            textSecondary = Color.White.copy(alpha = 0.70f),
            hintText = Color.White.copy(alpha = 0.50f),
            isGlassmorphism = false,
            isMinimalist = true
        )

        PlayerHudTheme.CINEMA_GOLD -> PlayerHudThemeUiTokens(
            theme = this,
            primary = prim,
            accent = acc,
            glow = gl,
            background = bg,
            dockBackground = bg,
            dockBorderGradient = listOf(
                prim.copy(alpha = 0.65f),
                Color(0xFFB8860B).copy(alpha = 0.45f),
                Color(0xFFFFE082).copy(alpha = 0.25f)
            ),
            progressGradient = listOf(acc, prim, Color(0xFFFFF9C4)),
            trackBackground = Color(0xFF262626).copy(alpha = 0.75f),
            thumbColor = Color.White,
            thumbGlow = prim,
            buttonNormalContainer = Color(0xFF1F1D16).copy(alpha = 0.65f),
            buttonNormalBorder = prim.copy(alpha = 0.20f),
            buttonFocusedContainer = acc.copy(alpha = 0.35f),
            buttonFocusedBorder = prim,
            primaryButtonContainer = acc.copy(alpha = 0.30f),
            primaryButtonBorder = prim.copy(alpha = 0.65f),
            primaryButtonFocusedContainer = prim.copy(alpha = 0.45f),
            primaryButtonFocusedBorder = Color(0xFFFFF9C4),
            badgeContainer = prim.copy(alpha = 0.18f),
            badgeTextColor = prim,
            textPrimary = Color(0xFFFFFDF5),
            textSecondary = Color(0xFFD4AF37).copy(alpha = 0.85f),
            hintText = Color(0xFFBDB7A4).copy(alpha = 0.70f),
            isGlassmorphism = false,
            isMinimalist = false
        )

        PlayerHudTheme.EMERALD_MATRIX -> PlayerHudThemeUiTokens(
            theme = this,
            primary = prim,
            accent = acc,
            glow = gl,
            background = bg,
            dockBackground = bg,
            dockBorderGradient = listOf(
                prim.copy(alpha = 0.65f),
                Color(0xFF00897B).copy(alpha = 0.45f),
                prim.copy(alpha = 0.25f)
            ),
            progressGradient = listOf(Color(0xFF00BFA5), acc, prim),
            trackBackground = Color(0xFF0D2818).copy(alpha = 0.75f),
            thumbColor = Color.White,
            thumbGlow = prim,
            buttonNormalContainer = Color(0xFF081C10).copy(alpha = 0.65f),
            buttonNormalBorder = prim.copy(alpha = 0.20f),
            buttonFocusedContainer = acc.copy(alpha = 0.35f),
            buttonFocusedBorder = prim,
            primaryButtonContainer = acc.copy(alpha = 0.30f),
            primaryButtonBorder = prim.copy(alpha = 0.60f),
            primaryButtonFocusedContainer = prim.copy(alpha = 0.45f),
            primaryButtonFocusedBorder = Color.White,
            badgeContainer = prim.copy(alpha = 0.18f),
            badgeTextColor = prim,
            textPrimary = Color(0xFFF0FFF4),
            textSecondary = Color(0xFF81E6D9),
            hintText = Color(0xFF68D391).copy(alpha = 0.70f),
            isGlassmorphism = false,
            isMinimalist = false
        )

        PlayerHudTheme.CRIMSON_RED -> PlayerHudThemeUiTokens(
            theme = this,
            primary = prim,
            accent = acc,
            glow = gl,
            background = bg,
            dockBackground = bg,
            dockBorderGradient = listOf(
                prim.copy(alpha = 0.75f),
                Color(0xFF8B0000).copy(alpha = 0.50f),
                acc.copy(alpha = 0.30f)
            ),
            progressGradient = listOf(Color(0xFF8B0000), prim, acc),
            trackBackground = Color(0xFF261010).copy(alpha = 0.75f),
            thumbColor = Color.White,
            thumbGlow = prim,
            buttonNormalContainer = Color(0xFF1E0A0A).copy(alpha = 0.65f),
            buttonNormalBorder = prim.copy(alpha = 0.22f),
            buttonFocusedContainer = prim.copy(alpha = 0.35f),
            buttonFocusedBorder = acc,
            primaryButtonContainer = prim.copy(alpha = 0.35f),
            primaryButtonBorder = prim.copy(alpha = 0.70f),
            primaryButtonFocusedContainer = prim.copy(alpha = 0.55f),
            primaryButtonFocusedBorder = Color.White,
            badgeContainer = prim.copy(alpha = 0.22f),
            badgeTextColor = acc,
            textPrimary = Color(0xFFFFF5F5),
            textSecondary = Color(0xFFFEB2B2),
            hintText = Color(0xFFE2E8F0).copy(alpha = 0.65f),
            isGlassmorphism = false,
            isMinimalist = false
        )

        PlayerHudTheme.AURORA_NORDIC -> PlayerHudThemeUiTokens(
            theme = this,
            primary = prim,
            accent = acc,
            glow = gl,
            background = bg,
            dockBackground = bg,
            dockBorderGradient = listOf(
                prim.copy(alpha = 0.65f),
                acc.copy(alpha = 0.50f),
                Color(0xFF00838F).copy(alpha = 0.25f)
            ),
            progressGradient = listOf(Color(0xFF00838F), acc, prim),
            trackBackground = Color(0xFF132F3C).copy(alpha = 0.70f),
            thumbColor = Color.White,
            thumbGlow = prim,
            buttonNormalContainer = Color(0xFF0F232D).copy(alpha = 0.60f),
            buttonNormalBorder = prim.copy(alpha = 0.20f),
            buttonFocusedContainer = acc.copy(alpha = 0.35f),
            buttonFocusedBorder = prim,
            primaryButtonContainer = prim.copy(alpha = 0.28f),
            primaryButtonBorder = prim.copy(alpha = 0.60f),
            primaryButtonFocusedContainer = prim.copy(alpha = 0.48f),
            primaryButtonFocusedBorder = Color.White,
            badgeContainer = prim.copy(alpha = 0.18f),
            badgeTextColor = prim,
            textPrimary = Color(0xFFF0FDF4),
            textSecondary = Color(0xFF80DEEA),
            hintText = Color(0xFF94A3B8).copy(alpha = 0.70f),
            isGlassmorphism = true,
            isMinimalist = false
        )

        PlayerHudTheme.SUNSET_ORANGE -> PlayerHudThemeUiTokens(
            theme = this,
            primary = prim,
            accent = acc,
            glow = gl,
            background = bg,
            dockBackground = bg,
            dockBorderGradient = listOf(
                prim.copy(alpha = 0.70f),
                acc.copy(alpha = 0.50f),
                Color(0xFFFFB300).copy(alpha = 0.25f)
            ),
            progressGradient = listOf(acc, prim, Color(0xFFFFB300)),
            trackBackground = Color(0xFF2D1820).copy(alpha = 0.75f),
            thumbColor = Color.White,
            thumbGlow = prim,
            buttonNormalContainer = Color(0xFF23101E).copy(alpha = 0.65f),
            buttonNormalBorder = prim.copy(alpha = 0.22f),
            buttonFocusedContainer = acc.copy(alpha = 0.35f),
            buttonFocusedBorder = prim,
            primaryButtonContainer = prim.copy(alpha = 0.32f),
            primaryButtonBorder = prim.copy(alpha = 0.65f),
            primaryButtonFocusedContainer = prim.copy(alpha = 0.50f),
            primaryButtonFocusedBorder = Color(0xFFFFD54F),
            badgeContainer = prim.copy(alpha = 0.20f),
            badgeTextColor = prim,
            textPrimary = Color(0xFFFFF5F5),
            textSecondary = Color(0xFFFFCCBC),
            hintText = Color(0xFFD1D5DB).copy(alpha = 0.70f),
            isGlassmorphism = false,
            isMinimalist = false
        )

        PlayerHudTheme.RETRO_SYNTHWAVE -> PlayerHudThemeUiTokens(
            theme = this,
            primary = prim,
            accent = acc,
            glow = gl,
            background = bg,
            dockBackground = bg,
            dockBorderGradient = listOf(
                prim.copy(alpha = 0.75f),
                acc.copy(alpha = 0.55f),
                Color(0xFF00F2FE).copy(alpha = 0.35f)
            ),
            progressGradient = listOf(acc, prim, Color(0xFFFF85A1)),
            trackBackground = Color(0xFF2A1035).copy(alpha = 0.75f),
            thumbColor = Color.White,
            thumbGlow = prim,
            buttonNormalContainer = Color(0xFF1E0A28).copy(alpha = 0.65f),
            buttonNormalBorder = prim.copy(alpha = 0.25f),
            buttonFocusedContainer = acc.copy(alpha = 0.40f),
            buttonFocusedBorder = prim,
            primaryButtonContainer = prim.copy(alpha = 0.35f),
            primaryButtonBorder = prim.copy(alpha = 0.70f),
            primaryButtonFocusedContainer = prim.copy(alpha = 0.55f),
            primaryButtonFocusedBorder = Color.White,
            badgeContainer = prim.copy(alpha = 0.22f),
            badgeTextColor = prim,
            textPrimary = Color(0xFFFFF0F5),
            textSecondary = Color(0xFFF472B6),
            hintText = Color(0xFFE9D5FF).copy(alpha = 0.70f),
            isGlassmorphism = false,
            isMinimalist = false
        )

        PlayerHudTheme.TITANIUM_STEEL -> PlayerHudThemeUiTokens(
            theme = this,
            primary = prim,
            accent = acc,
            glow = gl,
            background = bg,
            dockBackground = bg,
            dockBorderGradient = listOf(
                Color(0xFF94A3B8).copy(alpha = 0.55f),
                acc.copy(alpha = 0.45f),
                prim.copy(alpha = 0.25f)
            ),
            progressGradient = listOf(Color(0xFF475569), acc, prim),
            trackBackground = Color(0xFF1E293B).copy(alpha = 0.75f),
            thumbColor = Color.White,
            thumbGlow = acc,
            buttonNormalContainer = Color(0xFF1E293B).copy(alpha = 0.60f),
            buttonNormalBorder = Color.White.copy(alpha = 0.15f),
            buttonFocusedContainer = acc.copy(alpha = 0.30f),
            buttonFocusedBorder = prim,
            primaryButtonContainer = acc.copy(alpha = 0.25f),
            primaryButtonBorder = acc.copy(alpha = 0.55f),
            primaryButtonFocusedContainer = acc.copy(alpha = 0.45f),
            primaryButtonFocusedBorder = Color.White,
            badgeContainer = acc.copy(alpha = 0.18f),
            badgeTextColor = acc,
            textPrimary = Color(0xFFF8FAFC),
            textSecondary = Color(0xFF94A3B8),
            hintText = Color(0xFF64748B).copy(alpha = 0.70f),
            isGlassmorphism = false,
            isMinimalist = false
        )
    }
}
