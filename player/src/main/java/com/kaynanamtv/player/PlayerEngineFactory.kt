package com.kaynanamtv.player

import android.content.Context
import android.util.Log
import com.kaynanamtv.domain.model.PlayerEnginePreference
import okhttp3.OkHttpClient
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class PlayerEngineFactory @Inject constructor(
    private val context: Context,
    private val okHttpClient: OkHttpClient,
    private val playbackCompatibilityRepository: com.kaynanamtv.domain.repository.PlaybackCompatibilityRepository,
    private val audioCompatibilityMemoryStore: AudioCompatibilityMemoryStore,
    private val playbackSupportSnapshotStore: PlaybackSupportSnapshotStore
) {

    companion object {
        private const val TAG = "PlayerEngineFactory"

        fun isTvDevice(context: Context): Boolean {
            val pm = context.packageManager
            if (pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_LEANBACK)) return true
            if (pm.hasSystemFeature("android.software.leanback_only")) return true
            if (pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_TELEVISION)) return true
            if (pm.hasSystemFeature("amazon.hardware.fire_tv")) return true
            val uiModeManager = context.getSystemService(Context.UI_MODE_SERVICE) as? android.app.UiModeManager
            if (uiModeManager?.currentModeType == android.content.res.Configuration.UI_MODE_TYPE_TELEVISION) return true
            val screenWidthDp = context.resources.configuration.screenWidthDp
            return !pm.hasSystemFeature(android.content.pm.PackageManager.FEATURE_TOUCHSCREEN) && screenWidthDp >= 900
        }
    }

    /**
     * Resolves the target player engine based on user preference, hardware profile, and device type.
     * Respects explicit manual user selection.
     */
    fun resolveEngineType(preference: PlayerEnginePreference): PlayerEngineType {
        val isTv = isTvDevice(context)
        return when (preference) {
            PlayerEnginePreference.AUTO -> {
                if (isTv) {
                    val model = android.os.Build.MODEL.orEmpty().uppercase()
                    val board = android.os.Build.BOARD.orEmpty().uppercase()
                    val isMiBoxLowRam = model.contains("MIBOX") || model.contains("MDZ-16") || model.contains("MDZ-22") || board.contains("MIBOX")
                    if (isMiBoxLowRam) {
                        Log.i(TAG, "[PLAYER_ENGINE] device=MiBox mode=AUTO selected=VLC (Hardware-tuned LibVLC)")
                        PlayerEngineType.VLC
                    } else {
                        Log.i(TAG, "[PLAYER_ENGINE] device=TV mode=AUTO selected=VLC")
                        PlayerEngineType.VLC
                    }
                } else {
                    Log.i(TAG, "[PLAYER_ENGINE] device=MOBILE mode=AUTO selected=MEDIA3")
                    PlayerEngineType.MEDIA3
                }
            }
            PlayerEnginePreference.MEDIA3 -> {
                Log.i(TAG, "[PLAYER_ENGINE] mode=MANUAL selected=MEDIA3")
                PlayerEngineType.MEDIA3
            }
            PlayerEnginePreference.VLC -> {
                Log.i(TAG, "[PLAYER_ENGINE] mode=MANUAL selected=VLC")
                PlayerEngineType.VLC
            }
        }
    }

    fun createEngine(type: PlayerEngineType): PlayerEngine {
        return when (type) {
            PlayerEngineType.MEDIA3 -> {
                Media3PlayerEngine(
                    context,
                    okHttpClient,
                    playbackCompatibilityRepository,
                    audioCompatibilityMemoryStore,
                    playbackSupportSnapshotStore
                )
            }
            PlayerEngineType.VLC -> {
                VlcPlayerEngine(context)
            }
        }
    }
}

enum class PlayerEngineType {
    MEDIA3,
    VLC
}
