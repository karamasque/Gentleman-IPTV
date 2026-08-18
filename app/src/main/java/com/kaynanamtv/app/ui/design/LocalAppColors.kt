package com.kaynanamtv.app.ui.design

import androidx.compose.runtime.compositionLocalOf
import com.kaynanamtv.domain.model.AppColorTheme

val LocalAppColors = compositionLocalOf { AppColorPalette.forTheme(AppColorTheme.DEFAULT) }