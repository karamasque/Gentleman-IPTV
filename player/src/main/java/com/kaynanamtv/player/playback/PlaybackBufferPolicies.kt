package com.kaynanamtv.player.playback

import com.kaynanamtv.domain.model.PlaybackBufferMode
import com.kaynanamtv.domain.model.StreamInfo
import com.kaynanamtv.domain.model.VideoFormat
import java.util.Locale

internal data class PlaybackBufferPolicy(
    val label: String,
    val minBufferMs: Int,
    val maxBufferMs: Int,
    val playbackBufferMs: Int,
    val rebufferMs: Int,
    val targetBufferBytes: Int,
    val prioritizeTimeOverSizeThresholds: Boolean,
    val qualityReason: String = "baseline",
    val lowMemoryCapped: Boolean = false
)

internal object PlaybackBufferPolicies {
    private const val DEFAULT_TARGET_BUFFER_BYTES = -1
    private const val MPEG_TS_LIVE_TARGET_BUFFER_BYTES = 48 * 1024 * 1024  // 48 MB
    private const val MEDIUM_LIVE_TARGET_BUFFER_BYTES = 48 * 1024 * 1024   // 48 MB
    private const val LARGE_LIVE_TARGET_BUFFER_BYTES = 96 * 1024 * 1024    // 96 MB
    private const val MPEG_TS_LIVE_MIN_BUFFER_MS = 10_000  // 10s Stable IPTV
    private const val MPEG_TS_LIVE_MAX_BUFFER_MS = 30_000  // 30s Stable IPTV

    private const val LOW_MEMORY_LIVE_MIN_BUFFER_MS = 8_000
    private const val LOW_MEMORY_LIVE_MAX_BUFFER_MS = 20_000
    private const val LOW_MEMORY_COMPAT_LIVE_MIN_BUFFER_MS = 10_000
    private const val LOW_MEMORY_COMPAT_LIVE_MAX_BUFFER_MS = 25_000
    private const val LOW_MEMORY_VOD_MIN_BUFFER_MS = 20_000
    private const val LOW_MEMORY_VOD_MAX_BUFFER_MS = 60_000
    private const val LOW_MEMORY_PLAYBACK_BUFFER_MS = 2_000
    private const val LOW_MEMORY_REBUFFER_MS = 4_000

    private const val LIVE_MIN_BUFFER_MS = 10_000   // 10s Stable IPTV
    private const val LIVE_MAX_BUFFER_MS = 30_000   // 30s Stable IPTV
    private const val COMPAT_LIVE_MIN_BUFFER_MS = 12_000
    private const val COMPAT_LIVE_MAX_BUFFER_MS = 30_000
    private const val VOD_MIN_BUFFER_MS = 12_000
    private const val VOD_MAX_BUFFER_MS = 35_000
    private const val PLAYBACK_BUFFER_MS = 1_500    // 1.5s
    private const val REBUFFER_MS = 3_000          // 3.0s
    private const val VOD_PLAYBACK_BUFFER_MS = 1_500
    private const val VOD_REBUFFER_MS = 3_000
    private const val MEDIUM_LIVE_MIN_BUFFER_MS = 10_000
    private const val MEDIUM_LIVE_MAX_BUFFER_MS = 25_000
    private const val MEDIUM_LIVE_PLAYBACK_BUFFER_MS = 1_500
    private const val MEDIUM_LIVE_REBUFFER_MS = 3_000
    private const val LARGE_LIVE_MIN_BUFFER_MS = 15_000
    private const val LARGE_LIVE_MAX_BUFFER_MS = 40_000
    private const val LARGE_LIVE_PLAYBACK_BUFFER_MS = 1_500
    private const val LARGE_LIVE_REBUFFER_MS = 3_000
    private const val UHD_MIN_WIDTH = 3_840
    private const val UHD_MIN_HEIGHT = 2_160
    private const val HIGH_BITRATE_THRESHOLD_BPS = 20_000_000

    private const val UHD_LIVE_TARGET_BUFFER_BYTES = 128 * 1024 * 1024 // 128 MB
    private const val UHD_LIVE_MIN_BUFFER_MS = 15_000
    private const val UHD_LIVE_MAX_BUFFER_MS = 40_000
    private const val UHD_LIVE_PLAYBACK_BUFFER_MS = 1_000
    private const val UHD_LIVE_REBUFFER_MS = 2_000

    private const val UHD_VOD_TARGET_BUFFER_BYTES = 192 * 1024 * 1024 // 192 MB
    private const val UHD_VOD_MIN_BUFFER_MS = 25_000
    private const val UHD_VOD_MAX_BUFFER_MS = 60_000
    private const val UHD_VOD_PLAYBACK_BUFFER_MS = 1_200
    private const val UHD_VOD_REBUFFER_MS = 2_500

    private const val LOW_MEMORY_UHD_VOD_TARGET_BUFFER_BYTES = 64 * 1024 * 1024 // 64 MB
    private const val LOW_MEMORY_UHD_VOD_MIN_BUFFER_MS = 15_000
    private const val LOW_MEMORY_UHD_VOD_MAX_BUFFER_MS = 35_000
    private const val LOW_MEMORY_UHD_VOD_PLAYBACK_BUFFER_MS = 1_200
    private const val LOW_MEMORY_UHD_VOD_REBUFFER_MS = 2_500

    fun forPlayback(
        isLive: Boolean,
        compatibilityMode: Boolean,
        lowMemoryDevice: Boolean
    ): PlaybackBufferPolicy = forPlayback(
        resolvedStreamType = if (isLive) ResolvedStreamType.HLS else ResolvedStreamType.PROGRESSIVE,
        compatibilityMode = compatibilityMode,
        lowMemoryDevice = lowMemoryDevice
    )

    fun forPlayback(
        isLive: Boolean,
        compatibilityMode: Boolean
    ): PlaybackBufferPolicy = forPlayback(
        isLive = isLive,
        compatibilityMode = compatibilityMode,
        lowMemoryDevice = false
    )

    fun forPlayback(
        resolvedStreamType: ResolvedStreamType,
        compatibilityMode: Boolean
    ): PlaybackBufferPolicy = forPlayback(
        resolvedStreamType = resolvedStreamType,
        compatibilityMode = compatibilityMode,
        lowMemoryDevice = false
    )

    fun forPlayback(
        resolvedStreamType: ResolvedStreamType,
        compatibilityMode: Boolean,
        lowMemoryDevice: Boolean
    ): PlaybackBufferPolicy = forPlayback(
        resolvedStreamType = resolvedStreamType,
        compatibilityMode = compatibilityMode,
        lowMemoryDevice = lowMemoryDevice,
        bufferMode = PlaybackBufferMode.AUTO,
        streamInfo = null,
        observedVideoFormat = null
    )

    fun forPlayback(
        resolvedStreamType: ResolvedStreamType,
        compatibilityMode: Boolean,
        lowMemoryDevice: Boolean,
        bufferMode: PlaybackBufferMode,
        streamInfo: StreamInfo? = null,
        observedVideoFormat: VideoFormat? = null,
        qualityReasonOverride: String? = null
    ): PlaybackBufferPolicy {
        val qualityReason = qualityReasonOverride ?: detectHighQualityReason(streamInfo, observedVideoFormat)
        val isLive = resolvedStreamType.isLive

        return when {
            bufferMode == PlaybackBufferMode.MEDIUM && isLive ->
                mediumLivePolicy(label = "medium-live", qualityReason = "user-medium")
            bufferMode == PlaybackBufferMode.LARGE && isLive ->
                largeLivePolicy(label = "large-live", qualityReason = "user-large")
            bufferMode == PlaybackBufferMode.AUTO && qualityReason != null -> {
                when {
                    isLive -> {
                        if (lowMemoryDevice) {
                            mediumLivePolicy(
                                label = "auto-uhd-live-capped",
                                qualityReason = qualityReason,
                                lowMemoryCapped = true
                            )
                        } else {
                            PlaybackBufferPolicy(
                                label = "auto-uhd-live",
                                minBufferMs = UHD_LIVE_MIN_BUFFER_MS,
                                maxBufferMs = UHD_LIVE_MAX_BUFFER_MS,
                                playbackBufferMs = UHD_LIVE_PLAYBACK_BUFFER_MS,
                                rebufferMs = UHD_LIVE_REBUFFER_MS,
                                targetBufferBytes = UHD_LIVE_TARGET_BUFFER_BYTES,
                                prioritizeTimeOverSizeThresholds = true,
                                qualityReason = qualityReason
                            )
                        }
                    }
                    else -> {
                        if (lowMemoryDevice) {
                            mediumVodPolicy(
                                label = "auto-uhd-vod-capped",
                                qualityReason = qualityReason,
                                lowMemoryCapped = true
                            )
                        } else {
                            largeVodPolicy(
                                label = "auto-uhd-vod",
                                qualityReason = qualityReason
                            )
                        }
                    }
                }
            }
            else -> baselineLivePolicy(
                resolvedStreamType = resolvedStreamType,
                compatibilityMode = compatibilityMode,
                lowMemoryDevice = lowMemoryDevice
            )
        }
    }

    private fun baselineLivePolicy(
        resolvedStreamType: ResolvedStreamType,
        compatibilityMode: Boolean,
        lowMemoryDevice: Boolean
    ): PlaybackBufferPolicy = when {
        lowMemoryDevice && resolvedStreamType == ResolvedStreamType.MPEG_TS_LIVE ->
            PlaybackBufferPolicy(
                label = "lowmem-mpeg-ts-live",
                minBufferMs = LOW_MEMORY_LIVE_MIN_BUFFER_MS,
                maxBufferMs = LOW_MEMORY_LIVE_MAX_BUFFER_MS,
                playbackBufferMs = LOW_MEMORY_PLAYBACK_BUFFER_MS,
                rebufferMs = LOW_MEMORY_REBUFFER_MS,
                targetBufferBytes = MPEG_TS_LIVE_TARGET_BUFFER_BYTES,
                prioritizeTimeOverSizeThresholds = true
            )
        lowMemoryDevice && compatibilityMode && resolvedStreamType.isLive ->
            PlaybackBufferPolicy(
                label = "lowmem-compat-live",
                minBufferMs = LOW_MEMORY_COMPAT_LIVE_MIN_BUFFER_MS,
                maxBufferMs = LOW_MEMORY_COMPAT_LIVE_MAX_BUFFER_MS,
                playbackBufferMs = LOW_MEMORY_PLAYBACK_BUFFER_MS,
                rebufferMs = LOW_MEMORY_REBUFFER_MS,
                targetBufferBytes = DEFAULT_TARGET_BUFFER_BYTES,
                prioritizeTimeOverSizeThresholds = true
            )
        lowMemoryDevice && resolvedStreamType.isLive ->
            PlaybackBufferPolicy(
                label = "lowmem-live",
                minBufferMs = LOW_MEMORY_LIVE_MIN_BUFFER_MS,
                maxBufferMs = LOW_MEMORY_LIVE_MAX_BUFFER_MS,
                playbackBufferMs = LOW_MEMORY_PLAYBACK_BUFFER_MS,
                rebufferMs = LOW_MEMORY_REBUFFER_MS,
                targetBufferBytes = DEFAULT_TARGET_BUFFER_BYTES,
                prioritizeTimeOverSizeThresholds = true
            )
        lowMemoryDevice ->
            PlaybackBufferPolicy(
                label = "lowmem-vod",
                minBufferMs = LOW_MEMORY_VOD_MIN_BUFFER_MS,
                maxBufferMs = LOW_MEMORY_VOD_MAX_BUFFER_MS,
                playbackBufferMs = LOW_MEMORY_PLAYBACK_BUFFER_MS,
                rebufferMs = LOW_MEMORY_REBUFFER_MS,
                targetBufferBytes = DEFAULT_TARGET_BUFFER_BYTES,
                prioritizeTimeOverSizeThresholds = true
            )
        resolvedStreamType == ResolvedStreamType.MPEG_TS_LIVE ->
            PlaybackBufferPolicy(
                label = "mpeg-ts-live",
                minBufferMs = MPEG_TS_LIVE_MIN_BUFFER_MS,
                maxBufferMs = MPEG_TS_LIVE_MAX_BUFFER_MS,
                playbackBufferMs = PLAYBACK_BUFFER_MS,
                rebufferMs = REBUFFER_MS,
                targetBufferBytes = MPEG_TS_LIVE_TARGET_BUFFER_BYTES,
                prioritizeTimeOverSizeThresholds = true
            )
        compatibilityMode && resolvedStreamType.isLive ->
            PlaybackBufferPolicy(
                label = "compat-live",
                minBufferMs = COMPAT_LIVE_MIN_BUFFER_MS,
                maxBufferMs = COMPAT_LIVE_MAX_BUFFER_MS,
                playbackBufferMs = PLAYBACK_BUFFER_MS,
                rebufferMs = REBUFFER_MS,
                targetBufferBytes = DEFAULT_TARGET_BUFFER_BYTES,
                prioritizeTimeOverSizeThresholds = true
            )
        resolvedStreamType.isLive ->
            PlaybackBufferPolicy(
                label = "stable-live",
                minBufferMs = LIVE_MIN_BUFFER_MS,
                maxBufferMs = LIVE_MAX_BUFFER_MS,
                playbackBufferMs = PLAYBACK_BUFFER_MS,
                rebufferMs = REBUFFER_MS,
                targetBufferBytes = DEFAULT_TARGET_BUFFER_BYTES,
                prioritizeTimeOverSizeThresholds = true
            )
        else ->
            PlaybackBufferPolicy(
                label = "stable-vod",
                minBufferMs = VOD_MIN_BUFFER_MS,
                maxBufferMs = VOD_MAX_BUFFER_MS,
                playbackBufferMs = VOD_PLAYBACK_BUFFER_MS,
                rebufferMs = VOD_REBUFFER_MS,
                targetBufferBytes = DEFAULT_TARGET_BUFFER_BYTES,
                prioritizeTimeOverSizeThresholds = true
            )
    }

    private fun mediumLivePolicy(
        label: String,
        qualityReason: String,
        lowMemoryCapped: Boolean = false
    ): PlaybackBufferPolicy = PlaybackBufferPolicy(
        label = label,
        minBufferMs = MEDIUM_LIVE_MIN_BUFFER_MS,
        maxBufferMs = MEDIUM_LIVE_MAX_BUFFER_MS,
        playbackBufferMs = MEDIUM_LIVE_PLAYBACK_BUFFER_MS,
        rebufferMs = MEDIUM_LIVE_REBUFFER_MS,
        targetBufferBytes = MEDIUM_LIVE_TARGET_BUFFER_BYTES,
        prioritizeTimeOverSizeThresholds = true,
        qualityReason = qualityReason,
        lowMemoryCapped = lowMemoryCapped
    )

    private fun largeLivePolicy(label: String, qualityReason: String): PlaybackBufferPolicy = PlaybackBufferPolicy(
        label = label,
        minBufferMs = LARGE_LIVE_MIN_BUFFER_MS,
        maxBufferMs = LARGE_LIVE_MAX_BUFFER_MS,
        playbackBufferMs = LARGE_LIVE_PLAYBACK_BUFFER_MS,
        rebufferMs = LARGE_LIVE_REBUFFER_MS,
        targetBufferBytes = LARGE_LIVE_TARGET_BUFFER_BYTES,
        prioritizeTimeOverSizeThresholds = true,
        qualityReason = qualityReason
    )

    private fun largeVodPolicy(label: String, qualityReason: String): PlaybackBufferPolicy = PlaybackBufferPolicy(
        label = label,
        minBufferMs = UHD_VOD_MIN_BUFFER_MS,
        maxBufferMs = UHD_VOD_MAX_BUFFER_MS,
        playbackBufferMs = UHD_VOD_PLAYBACK_BUFFER_MS,
        rebufferMs = UHD_VOD_REBUFFER_MS,
        targetBufferBytes = UHD_VOD_TARGET_BUFFER_BYTES,
        prioritizeTimeOverSizeThresholds = true,
        qualityReason = qualityReason
    )

    private fun mediumVodPolicy(
        label: String,
        qualityReason: String,
        lowMemoryCapped: Boolean = false
    ): PlaybackBufferPolicy = PlaybackBufferPolicy(
        label = label,
        minBufferMs = LOW_MEMORY_UHD_VOD_MIN_BUFFER_MS,
        maxBufferMs = LOW_MEMORY_UHD_VOD_MAX_BUFFER_MS,
        playbackBufferMs = LOW_MEMORY_UHD_VOD_PLAYBACK_BUFFER_MS,
        rebufferMs = LOW_MEMORY_UHD_VOD_REBUFFER_MS,
        targetBufferBytes = LOW_MEMORY_UHD_VOD_TARGET_BUFFER_BYTES,
        prioritizeTimeOverSizeThresholds = true,
        qualityReason = qualityReason,
        lowMemoryCapped = lowMemoryCapped
    )

    fun detectHighQualityReason(streamInfo: StreamInfo?, observedVideoFormat: VideoFormat?): String? {
        observedVideoFormat?.let { format ->
            when {
                format.width >= UHD_MIN_WIDTH -> return "observed-width-${format.width}"
                format.height >= UHD_MIN_HEIGHT -> return "observed-height-${format.height}"
                format.bitrate >= HIGH_BITRATE_THRESHOLD_BPS -> return "observed-bitrate-${format.bitrate}"
                format.isHdr -> return "observed-hdr"
            }
        }
        val searchable = listOfNotNull(
            streamInfo?.title,
            streamInfo?.url,
            streamInfo?.containerExtension
        ).joinToString(" ").lowercase(Locale.ROOT)
        if (searchable.isBlank()) return null
        return when {
            "2160p" in searchable -> "metadata-2160p"
            "2160" in searchable -> "metadata-2160"
            "uhd" in searchable -> "metadata-uhd"
            "4k" in searchable -> "metadata-4k"
            "hdr" in searchable -> "metadata-hdr"
            else -> null
        }
    }

    @Deprecated("Use detectHighQualityReason instead", ReplaceWith("detectHighQualityReason(streamInfo, observedVideoFormat)"))
    fun highQualityLiveHlsReason(streamInfo: StreamInfo?, observedVideoFormat: VideoFormat?): String? =
        detectHighQualityReason(streamInfo, observedVideoFormat)

    private val ResolvedStreamType.isLive: Boolean
        get() = this == ResolvedStreamType.HLS ||
            this == ResolvedStreamType.SMOOTH_STREAMING ||
            this == ResolvedStreamType.MPEG_TS_LIVE ||
            this == ResolvedStreamType.RTSP
}
