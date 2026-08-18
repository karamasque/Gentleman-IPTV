package com.kaynanamtv.app.ui.design

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.graphics.Color
import com.kaynanamtv.domain.model.AppColorTheme

// ── Dinamik AppColors Nesnesi ───────────────────────────────────────────────
// Artık Compose State (mutableStateOf) kullanıyor. Değiştiğinde tüm UI otomatik güncellenir.
object AppColors {
    var currentPalette by mutableStateOf(AppColorPalette.indigo())

    val Canvas: Color get() = currentPalette.Canvas
    val CanvasElevated: Color get() = currentPalette.CanvasElevated
    val Surface: Color get() = currentPalette.Surface
    val SurfaceElevated: Color get() = currentPalette.SurfaceElevated
    val SurfaceEmphasis: Color get() = currentPalette.SurfaceEmphasis
    val SurfaceAccent: Color get() = currentPalette.SurfaceAccent
    val Brand: Color get() = currentPalette.Brand
    val BrandMuted: Color get() = currentPalette.BrandMuted
    val BrandStrong: Color get() = currentPalette.BrandStrong
    val Focus: Color get() = currentPalette.Focus
    val NeonCyan: Color get() = currentPalette.NeonCyan
    val NeonGreen: Color get() = currentPalette.NeonGreen
    val NeonAmber: Color get() = currentPalette.NeonAmber
    val NeonRose: Color get() = currentPalette.NeonRose
    val TextPrimary: Color get() = currentPalette.TextPrimary
    val TextSecondary: Color get() = currentPalette.TextSecondary
    val TextTertiary: Color get() = currentPalette.TextTertiary
    val TextDisabled: Color get() = currentPalette.TextDisabled
    val Live: Color get() = currentPalette.Live
    val Success: Color get() = currentPalette.Success
    val Warning: Color get() = currentPalette.Warning
    val Info: Color get() = currentPalette.Info
    val Divider: Color get() = currentPalette.Divider
    val Outline: Color get() = currentPalette.Outline
    val HeroTop: Color get() = currentPalette.HeroTop
    val HeroBottom: Color get() = currentPalette.HeroBottom

    val GlassSurface: Color get() = Color(0x1AFFFFFF)
    val GlassBorder: Color get() = Color(0x26FFFFFF)
    val GlassHighlight: Color get() = Color(0x40FFFFFF)
    val FocusGlow: Color get() = currentPalette.Brand.copy(alpha = 0.45f)
}

// ── Dinamik tema paleti ───────────────────────────────────────────────────────
data class AppColorPalette(
    val Canvas: Color,
    val CanvasElevated: Color,
    val Surface: Color,
    val SurfaceElevated: Color,
    val SurfaceEmphasis: Color,
    val SurfaceAccent: Color,
    val Brand: Color,
    val BrandMuted: Color,
    val BrandStrong: Color,
    val Focus: Color,
    val NeonCyan: Color,
    val NeonGreen: Color,
    val NeonAmber: Color,
    val NeonRose: Color,
    val TextPrimary: Color,
    val TextSecondary: Color,
    val TextTertiary: Color,
    val TextDisabled: Color,
    val Live: Color,
    val Success: Color,
    val Warning: Color,
    val Info: Color,
    val Divider: Color,
    val Outline: Color,
    val HeroTop: Color,
    val HeroBottom: Color,
) {
    companion object {
        fun forTheme(theme: AppColorTheme): AppColorPalette = when (theme) {
            AppColorTheme.INDIGO -> indigo()
            AppColorTheme.BLACK  -> black()
            AppColorTheme.BLUE   -> blue()
            AppColorTheme.RED    -> red()
            AppColorTheme.YELLOW -> yellow()
            AppColorTheme.GREEN  -> green()
            AppColorTheme.PURPLE -> purple()
            AppColorTheme.PINK   -> pink()
        }

        fun indigo() = AppColorPalette(
            Canvas = Color(0xFF0E1929), CanvasElevated = Color(0xFF132032),
            Surface = Color(0xFF1A2744), SurfaceElevated = Color(0xFF213052),
            SurfaceEmphasis = Color(0xFF283A5E), SurfaceAccent = Color(0xFF2A3F6B),
            Brand = Color(0xFF6366F1), BrandMuted = Color(0x336366F1),
            BrandStrong = Color(0xFFA78BFA), Focus = Color(0xFFEEF2FF),
            NeonCyan = Color(0xFF22D3EE), NeonGreen = Color(0xFF10B981),
            NeonAmber = Color(0xFFF59E0B), NeonRose = Color(0xFFF43F5E),
            TextPrimary = Color(0xFFF1F5F9), TextSecondary = Color(0xFFCBD5E1),
            TextTertiary = Color(0xFF7A8FA6), TextDisabled = Color(0xFF4B5563),
            Live = Color(0xFFF43F5E), Success = Color(0xFF10B981),
            Warning = Color(0xFFF59E0B), Info = Color(0xFF22D3EE),
            Divider = Color(0x1AFFFFFF), Outline = Color(0x266366F1),
            HeroTop = Color(0xCC0E1929), HeroBottom = Color(0xF20E1929),
        )

        fun black() = AppColorPalette(
            Canvas = Color(0xFF000000), CanvasElevated = Color(0xFF0A0A0A),
            Surface = Color(0xFF111111), SurfaceElevated = Color(0xFF1A1A1A),
            SurfaceEmphasis = Color(0xFF222222), SurfaceAccent = Color(0xFF2A2A2A),
            Brand = Color(0xFFE2E2E2), BrandMuted = Color(0x33E2E2E2),
            BrandStrong = Color(0xFFFFFFFF), Focus = Color(0xFFFFFFFF),
            NeonCyan = Color(0xFF22D3EE), NeonGreen = Color(0xFF10B981),
            NeonAmber = Color(0xFFF59E0B), NeonRose = Color(0xFFF43F5E),
            TextPrimary = Color(0xFFF1F5F9), TextSecondary = Color(0xFFCBD5E1),
            TextTertiary = Color(0xFF7A8FA6), TextDisabled = Color(0xFF4B5563),
            Live = Color(0xFFF43F5E), Success = Color(0xFF10B981),
            Warning = Color(0xFFF59E0B), Info = Color(0xFF22D3EE),
            Divider = Color(0x1AFFFFFF), Outline = Color(0x33FFFFFF),
            HeroTop = Color(0xCC000000), HeroBottom = Color(0xF2000000),
        )

        fun blue() = AppColorPalette(
            Canvas = Color(0xFF07101E), CanvasElevated = Color(0xFF0B1526),
            Surface = Color(0xFF0F1F3A), SurfaceElevated = Color(0xFF142548),
            SurfaceEmphasis = Color(0xFF1A2F58), SurfaceAccent = Color(0xFF1E3568),
            Brand = Color(0xFF3B82F6), BrandMuted = Color(0x333B82F6),
            BrandStrong = Color(0xFF93C5FD), Focus = Color(0xFFEFF6FF),
            NeonCyan = Color(0xFF22D3EE), NeonGreen = Color(0xFF10B981),
            NeonAmber = Color(0xFFF59E0B), NeonRose = Color(0xFFF43F5E),
            TextPrimary = Color(0xFFF1F5F9), TextSecondary = Color(0xFFCBD5E1),
            TextTertiary = Color(0xFF7A8FA6), TextDisabled = Color(0xFF4B5563),
            Live = Color(0xFFF43F5E), Success = Color(0xFF10B981),
            Warning = Color(0xFFF59E0B), Info = Color(0xFF22D3EE),
            Divider = Color(0x1AFFFFFF), Outline = Color(0x333B82F6),
            HeroTop = Color(0xCC07101E), HeroBottom = Color(0xF207101E),
        )

        fun red() = AppColorPalette(
            Canvas = Color(0xFF1A0A0A), CanvasElevated = Color(0xFF220D0D),
            Surface = Color(0xFF2E1111), SurfaceElevated = Color(0xFF3A1515),
            SurfaceEmphasis = Color(0xFF461919), SurfaceAccent = Color(0xFF501E1E),
            Brand = Color(0xFFEF4444), BrandMuted = Color(0x33EF4444),
            BrandStrong = Color(0xFFFCA5A5), Focus = Color(0xFFFFF1F2),
            NeonCyan = Color(0xFF22D3EE), NeonGreen = Color(0xFF10B981),
            NeonAmber = Color(0xFFF59E0B), NeonRose = Color(0xFFFB7185),
            TextPrimary = Color(0xFFF1F5F9), TextSecondary = Color(0xFFCBD5E1),
            TextTertiary = Color(0xFF7A8FA6), TextDisabled = Color(0xFF4B5563),
            Live = Color(0xFFFB7185), Success = Color(0xFF10B981),
            Warning = Color(0xFFF59E0B), Info = Color(0xFF22D3EE),
            Divider = Color(0x1AFFFFFF), Outline = Color(0x33EF4444),
            HeroTop = Color(0xCC1A0A0A), HeroBottom = Color(0xF21A0A0A),
        )

        fun yellow() = AppColorPalette(
            Canvas = Color(0xFF161106), CanvasElevated = Color(0xFF1E1709),
            Surface = Color(0xFF28200D), SurfaceElevated = Color(0xFF342A11),
            SurfaceEmphasis = Color(0xFF403315), SurfaceAccent = Color(0xFF4A3C18),
            Brand = Color(0xFFF59E0B), BrandMuted = Color(0x33F59E0B),
            BrandStrong = Color(0xFFFDE68A), Focus = Color(0xFFFFFBEB),
            NeonCyan = Color(0xFF22D3EE), NeonGreen = Color(0xFF10B981),
            NeonAmber = Color(0xFFFBBF24), NeonRose = Color(0xFFF43F5E),
            TextPrimary = Color(0xFFF1F5F9), TextSecondary = Color(0xFFCBD5E1),
            TextTertiary = Color(0xFF7A8FA6), TextDisabled = Color(0xFF4B5563),
            Live = Color(0xFFF43F5E), Success = Color(0xFF10B981),
            Warning = Color(0xFFFBBF24), Info = Color(0xFF22D3EE),
            Divider = Color(0x1AFFFFFF), Outline = Color(0x33F59E0B),
            HeroTop = Color(0xCC161106), HeroBottom = Color(0xF2161106),
        )

        fun green() = AppColorPalette(
            Canvas = Color(0xFF071510), CanvasElevated = Color(0xFF0A1D15),
            Surface = Color(0xFF0F261C), SurfaceElevated = Color(0xFF143023),
            SurfaceEmphasis = Color(0xFF1A3A2A), SurfaceAccent = Color(0xFF1E4230),
            Brand = Color(0xFF10B981), BrandMuted = Color(0x3310B981),
            BrandStrong = Color(0xFF6EE7B7), Focus = Color(0xFFECFDF5),
            NeonCyan = Color(0xFF22D3EE), NeonGreen = Color(0xFF34D399),
            NeonAmber = Color(0xFFF59E0B), NeonRose = Color(0xFFF43F5E),
            TextPrimary = Color(0xFFF1F5F9), TextSecondary = Color(0xFFCBD5E1),
            TextTertiary = Color(0xFF7A8FA6), TextDisabled = Color(0xFF4B5563),
            Live = Color(0xFFF43F5E), Success = Color(0xFF34D399),
            Warning = Color(0xFFF59E0B), Info = Color(0xFF22D3EE),
            Divider = Color(0x1AFFFFFF), Outline = Color(0x3310B981),
            HeroTop = Color(0xCC071510), HeroBottom = Color(0xF2071510),
        )

        fun purple() = AppColorPalette(
            Canvas = Color(0xFF100A1A), CanvasElevated = Color(0xFF160D23),
            Surface = Color(0xFF1E1030), SurfaceElevated = Color(0xFF26143C),
            SurfaceEmphasis = Color(0xFF2E1848), SurfaceAccent = Color(0xFF361C54),
            Brand = Color(0xFFA855F7), BrandMuted = Color(0x33A855F7),
            BrandStrong = Color(0xFFD8B4FE), Focus = Color(0xFFFAF5FF),
            NeonCyan = Color(0xFF22D3EE), NeonGreen = Color(0xFF10B981),
            NeonAmber = Color(0xFFF59E0B), NeonRose = Color(0xFFF43F5E),
            TextPrimary = Color(0xFFF1F5F9), TextSecondary = Color(0xFFCBD5E1),
            TextTertiary = Color(0xFF7A8FA6), TextDisabled = Color(0xFF4B5563),
            Live = Color(0xFFF43F5E), Success = Color(0xFF10B981),
            Warning = Color(0xFFF59E0B), Info = Color(0xFF22D3EE),
            Divider = Color(0x1AFFFFFF), Outline = Color(0x33A855F7),
            HeroTop = Color(0xCC100A1A), HeroBottom = Color(0xF2100A1A),
        )

        fun pink() = AppColorPalette(
            Canvas = Color(0xFF1A0A12), CanvasElevated = Color(0xFF220D18),
            Surface = Color(0xFF2E1122), SurfaceElevated = Color(0xFF3A152B),
            SurfaceEmphasis = Color(0xFF461933), SurfaceAccent = Color(0xFF501E3B),
            Brand = Color(0xFFEC4899), BrandMuted = Color(0x33EC4899),
            BrandStrong = Color(0xFFF9A8D4), Focus = Color(0xFFFDF2F8),
            NeonCyan = Color(0xFF22D3EE), NeonGreen = Color(0xFF10B981),
            NeonAmber = Color(0xFFF59E0B), NeonRose = Color(0xFFFB7185),
            TextPrimary = Color(0xFFF1F5F9), TextSecondary = Color(0xFFCBD5E1),
            TextTertiary = Color(0xFF7A8FA6), TextDisabled = Color(0xFF4B5563),
            Live = Color(0xFFFB7185), Success = Color(0xFF10B981),
            Warning = Color(0xFFF59E0B), Info = Color(0xFF22D3EE),
            Divider = Color(0x1AFFFFFF), Outline = Color(0x33EC4899),
            HeroTop = Color(0xCC1A0A12), HeroBottom = Color(0xF21A0A12),
        )
    }
}