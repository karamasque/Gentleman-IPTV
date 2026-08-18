package com.kaynanamtv.app.ui.theme

import com.kaynanamtv.app.ui.design.AppSpacing
import com.kaynanamtv.app.ui.design.LocalAppSpacing

typealias Spacing = AppSpacing

val LocalSpacing = LocalAppSpacing

fun defaultSpacing(): Spacing = AppSpacing()
