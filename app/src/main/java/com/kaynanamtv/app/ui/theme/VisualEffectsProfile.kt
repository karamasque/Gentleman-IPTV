package com.kaynanamtv.app.ui.theme

import android.app.ActivityManager
import android.content.Context
import androidx.compose.runtime.compositionLocalOf
import com.kaynanamtv.domain.model.VisualEffectsMode

enum class ResolvedVisualEffectsTier {
    FULL,
    BALANCED,
    LITE,
    OFF;

    val isBackgroundAnimated: Boolean
        get() = this == FULL || this == BALANCED

    val isFullAmbientGlowEnabled: Boolean
        get() = this == FULL

    val isDecorativeAnimationEnabled: Boolean
        get() = this != OFF

    val isFocusScaleEnabled: Boolean
        get() = this == FULL || this == BALANCED

    val isGlowShimmerEnabled: Boolean
        get() = this == FULL
}

data class VisualEffectsProfile(
    val mode: VisualEffectsMode,
    val tier: ResolvedVisualEffectsTier
)

val LocalVisualEffectsProfile = compositionLocalOf {
    VisualEffectsProfile(
        mode = VisualEffectsMode.AUTO,
        tier = ResolvedVisualEffectsTier.LITE
    )
}

object VisualEffectsResolver {
    fun resolve(
        mode: VisualEffectsMode,
        context: Context,
        isTelevision: Boolean
    ): VisualEffectsProfile {
        val tier = when (mode) {
            VisualEffectsMode.FULL -> ResolvedVisualEffectsTier.FULL
            VisualEffectsMode.BALANCED -> ResolvedVisualEffectsTier.BALANCED
            VisualEffectsMode.LITE -> ResolvedVisualEffectsTier.LITE
            VisualEffectsMode.OFF -> ResolvedVisualEffectsTier.OFF
            VisualEffectsMode.AUTO -> {
                if (isTelevision) {
                    val memoryInfo = ActivityManager.MemoryInfo()
                    val am = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
                    am?.getMemoryInfo(memoryInfo)
                    val totalRamGb = memoryInfo.totalMem / (1024L * 1024L * 1024L)
                    val isLowRam = memoryInfo.lowMemory || (am?.isLowRamDevice == true) || totalRamGb <= 2L
                    if (isLowRam) {
                        ResolvedVisualEffectsTier.LITE
                    } else if (totalRamGb <= 3L) {
                        ResolvedVisualEffectsTier.BALANCED
                    } else {
                        ResolvedVisualEffectsTier.BALANCED
                    }
                } else {
                    ResolvedVisualEffectsTier.FULL
                }
            }
        }
        return VisualEffectsProfile(mode = mode, tier = tier)
    }
}
