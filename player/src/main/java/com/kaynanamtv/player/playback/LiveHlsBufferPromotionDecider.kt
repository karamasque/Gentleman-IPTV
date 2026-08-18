package com.kaynanamtv.player.playback

import com.kaynanamtv.domain.model.PlaybackBufferMode
import com.kaynanamtv.domain.model.StreamInfo
import com.kaynanamtv.domain.model.VideoFormat

internal data class LiveHlsBufferPromotionDecision(
    val policy: PlaybackBufferPolicy,
    val qualityReason: String
)

internal object LiveHlsBufferPromotionDecider {

    fun decide(
        bufferMode: PlaybackBufferMode,
        resolvedStreamType: ResolvedStreamType,
        isLive: Boolean,
        mediaAlreadyPromoted: Boolean,
        currentPolicyLabel: String?,
        streamInfo: StreamInfo?,
        observedVideoFormat: VideoFormat,
        compatibilityMode: Boolean,
        lowMemoryDevice: Boolean
    ): LiveHlsBufferPromotionDecision? {
        if (bufferMode != PlaybackBufferMode.AUTO) return null
        if (mediaAlreadyPromoted) return null
        streamInfo ?: return null
        if (observedVideoFormat.isEmpty) return null

        val qualityReason = PlaybackBufferPolicies.detectHighQualityReason(
            streamInfo = null,
            observedVideoFormat = observedVideoFormat
        ) ?: return null
        val promotedPolicy = PlaybackBufferPolicies.forPlayback(
            resolvedStreamType = resolvedStreamType,
            compatibilityMode = compatibilityMode,
            lowMemoryDevice = lowMemoryDevice,
            bufferMode = bufferMode,
            streamInfo = streamInfo,
            observedVideoFormat = observedVideoFormat,
            qualityReasonOverride = qualityReason
        )
        if (promotedPolicy.label == currentPolicyLabel) return null

        return LiveHlsBufferPromotionDecision(
            policy = promotedPolicy,
            qualityReason = qualityReason
        )
    }
}
