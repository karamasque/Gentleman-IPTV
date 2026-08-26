package com.kaynanamtv.app.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.darkColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.kaynanamtv.app.ui.design.AppColorPalette
import com.kaynanamtv.app.ui.design.AppShapes
import com.kaynanamtv.app.ui.design.LocalAppColors
import com.kaynanamtv.app.ui.design.LocalAppShapes
import com.kaynanamtv.app.ui.design.LocalAppSpacing
import com.kaynanamtv.app.ui.design.rememberAppTypography
import com.kaynanamtv.domain.model.AppColorTheme

@Composable
fun KaynanamTVTheme(
    colorTheme: AppColorTheme = AppColorTheme.DEFAULT,
    content: @Composable () -> Unit
) {
    val palette = remember(colorTheme) { AppColorPalette.forTheme(colorTheme) }
    com.kaynanamtv.app.ui.design.AppColors.currentPalette = palette
    androidx.compose.runtime.SideEffect {
        com.kaynanamtv.app.ui.design.AppColors.currentPalette = palette
    }
    val colorScheme = remember(palette) {
        darkColorScheme(
            primary = palette.Brand,
            onPrimary = if (palette.Brand.luminance() > 0.5f) Color(0xFF0A0E1A) else Color.White,
            surface = palette.Surface,
            onSurface = palette.TextPrimary,
            surfaceVariant = palette.SurfaceElevated,
            onSurfaceVariant = palette.TextSecondary,
            background = palette.CanvasElevated,
            onBackground = palette.TextPrimary,
            error = palette.Live,
            onError = if (palette.Live.luminance() > 0.5f) Color(0xFF0A0E1A) else Color.White
        )
    }

    val typography = rememberAppTypography()
    CompositionLocalProvider(
        LocalAppColors provides palette,
        LocalAppSpacing provides com.kaynanamtv.app.ui.design.AppSpacing(),
        LocalAppShapes provides AppShapes()
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = typography,
            content = content
        )
    }
}

