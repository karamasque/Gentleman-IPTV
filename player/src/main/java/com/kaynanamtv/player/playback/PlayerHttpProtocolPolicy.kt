package com.kaynanamtv.player.playback

import com.kaynanamtv.domain.model.VodHttpProtocolMode

object PlayerHttpProtocolPolicy {
    fun forceHttp1(
        resolvedStreamType: ResolvedStreamType,
        vodHttpProtocolMode: VodHttpProtocolMode
    ): Boolean {
        return resolvedStreamType == ResolvedStreamType.PROGRESSIVE &&
            vodHttpProtocolMode == VodHttpProtocolMode.COMPATIBILITY_HTTP1
    }
}
