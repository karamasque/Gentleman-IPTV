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
    private val playbackSupportSnapshotStore: PlaybackSupportSnapshotStore,
    private val playbackContentionManager: com.kaynanamtv.domain.manager.PlaybackContentionManager? = null
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
     * Resolves the target player engine. Media3 (with bundled FFmpeg extension) is the sole unified engine.
     */
    fun resolveEngineType(preference: PlayerEnginePreference = PlayerEnginePreference.AUTO): PlayerEngineType {
        Log.i(TAG, "[PLAYER_ENGINE] Standardized on MEDIA3 (ExoPlayer + FFmpeg)")
        return PlayerEngineType.MEDIA3
    }

    fun createEngine(type: PlayerEngineType = PlayerEngineType.MEDIA3): PlayerEngine {
        return Media3PlayerEngine(
            context = context,
            okHttpClient = okHttpClient,
            playbackCompatibilityRepository = playbackCompatibilityRepository,
            audioCompatibilityMemoryStore = audioCompatibilityMemoryStore,
            playbackSupportSnapshotStore = playbackSupportSnapshotStore,
            playbackContentionManager = playbackContentionManager
        )
    }
}

enum class PlayerEngineType {
    MEDIA3
}
