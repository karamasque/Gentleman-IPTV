package com.kaynanamtv.app.ui.screens.player

import com.kaynanamtv.app.ui.model.archivePlaybackCapability
import com.kaynanamtv.domain.model.Channel
import java.util.Locale

internal fun isAuthExpiryPlaybackError(message: String?): Boolean {
    val normalized = message.orEmpty().lowercase(Locale.ROOT)
    return "401" in normalized ||
        "403" in normalized ||
        "unauthorized" in normalized ||
        "forbidden" in normalized ||
        "authentication" in normalized ||
        "token" in normalized ||
        "expired" in normalized
}

internal fun resolveCatchUpFailureMessage(
    channel: Channel?,
    archiveRequested: Boolean,
    programHasArchive: Boolean
): String {
    if (!archiveRequested || channel == null) {
        return "Geriye dönük oynatma geçerli bir canlı kanal bağlamı gerektirir."
    }
    val archiveCapability = channel.archivePlaybackCapability()
    return when {
        !archiveCapability.advertisedByProvider && !programHasArchive ->
            "Bu kanal mevcut yayın sunucusunda arşiv desteği sunmuyor."
        !archiveCapability.canBuildReplayCandidate ->
            "Yayın sunucusu geriye dönük izleme bildiriyor ancak bu kanal için yeterli tekrar üst verisi sağlamadı."
        else ->
            "Seçilen program için geriye dönük izleme şu anda mevcut değil."
    }
}

internal fun resolvePlaybackFormatLabel(
    currentResolvedPlaybackUrl: String,
    currentStreamUrl: String
): String {
    val url = currentResolvedPlaybackUrl.ifBlank { currentStreamUrl }.lowercase(Locale.ROOT)
    return when {
        url.contains("ext=m3u8") || url.endsWith(".m3u8") -> "HLS"
        url.contains("ext=ts") || url.endsWith(".ts") -> "TS"
        else -> "stream"
    }
}
