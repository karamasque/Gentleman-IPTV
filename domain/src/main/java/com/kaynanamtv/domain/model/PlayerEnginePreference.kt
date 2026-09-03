package com.kaynanamtv.domain.model

/**
 * User preference for playback engine selection.
 */
enum class PlayerEnginePreference {
    /**
     * Automatic engine resolution based on device type:
     * - Android TV / Mi Box / Fire TV / TV Box -> VLC
     * - Mobile / Tablet -> MEDIA3 (ExoPlayer)
     */
    AUTO,

    /**
     * Always use Media3 / ExoPlayer.
     */
    MEDIA3,

    /**
     * Always use internal LibVLC engine.
     */
    VLC,

    /**
     * Launch external VLC app (org.videolan.vlc).
     */
    EXTERNAL_VLC
}
