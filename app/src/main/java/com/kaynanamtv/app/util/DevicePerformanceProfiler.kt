package com.kaynanamtv.app.util

import android.app.ActivityManager
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import com.kaynanamtv.domain.model.PerformanceModePreference

/**
 * Intelligent hardware capability analyzer and Rocket Mode resolver.
 * Safely determines whether UI and image decoders should operate in ultra-lightweight high-FPS mode.
 */
object DevicePerformanceProfiler {

    private const val TAG = "DevicePerformanceProfiler"

    @Volatile
    private var cachedTotalRamMb: Long? = null

    fun getTotalRamMb(context: Context): Long {
        cachedTotalRamMb?.let { return it }
        return try {
            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            val memInfo = ActivityManager.MemoryInfo()
            actManager?.getMemoryInfo(memInfo)
            val ramMb = memInfo.totalMem / (1024 * 1024)
            cachedTotalRamMb = ramMb
            ramMb
        } catch (e: Exception) {
            Log.w(TAG, "Failed to inspect total RAM", e)
            2048L
        }
    }

    fun isLowRamDevice(context: Context): Boolean {
        return try {
            val actManager = context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
            actManager?.isLowRamDevice ?: false
        } catch (e: Exception) {
            false
        }
    }

    fun getCpuCoreCount(): Int {
        return try {
            Runtime.getRuntime().availableProcessors().coerceAtLeast(1)
        } catch (e: Exception) {
            4
        }
    }

    fun isTvEnvironment(context: Context): Boolean {
        return try {
            val pm = context.packageManager
            pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK) ||
                    pm.hasSystemFeature("android.hardware.type.television") ||
                    pm.hasSystemFeature("amazon.hardware.fire_tv")
        } catch (e: Exception) {
            false
        }
    }

    /**
     * Resolves whether Rocket Mode optimizations (lightweight UI, no live ambilight video readback, 16-bit bitmaps)
     * should be applied for the current device and preference.
     */
    fun isRocketModeActive(preference: PerformanceModePreference, context: Context): Boolean {
        return when (preference) {
            PerformanceModePreference.ALWAYS_ON -> true
            PerformanceModePreference.OFF -> false
            PerformanceModePreference.AUTO -> {
                val totalRam = getTotalRamMb(context)
                val isLowRam = isLowRamDevice(context)
                val isTv = isTvEnvironment(context)
                val cores = getCpuCoreCount()

                // TV boxes and entry/mid-tier devices with <= 2.5GB RAM (or <= 3.5GB on TV) or <= 4 cores benefit hugely from Rocket Mode
                val shouldEnable = isLowRam || totalRam <= 2560L || (isTv && totalRam <= 3584L) || cores <= 4
                Log.d(TAG, "Auto Rocket Mode resolution: shouldEnable=$shouldEnable (RAM=${totalRam}MB, isLowRam=$isLowRam, isTv=$isTv, cores=$cores)")
                shouldEnable
            }
        }
    }

    /**
     * Recommends optimal bitmap configuration for Coil image decoding.
     * RGB_565 halves bitmap memory footprint and prevents garbage collection stutters on TV devices.
     */
    fun recommendedBitmapConfig(isRocketMode: Boolean): Bitmap.Config {
        return if (isRocketMode) {
            Bitmap.Config.RGB_565
        } else {
            Bitmap.Config.ARGB_8888
        }
    }
}
