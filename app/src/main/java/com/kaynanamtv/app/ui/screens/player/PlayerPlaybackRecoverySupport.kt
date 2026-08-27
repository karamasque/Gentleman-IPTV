package com.kaynanamtv.app.ui.screens.player

import com.kaynanamtv.player.PlayerError

internal fun classifyPlaybackError(error: PlayerError): PlayerRecoveryType = when (error) {
    is PlayerError.NetworkError -> {
        if (error.message.contains("timeout", ignoreCase = true)) {
            PlayerRecoveryType.BUFFER_TIMEOUT
        } else if (
            error.message.contains("HTTP 456", ignoreCase = true) ||
            error.message.contains("HTTP 509", ignoreCase = true) ||
            error.message.contains("access denied", ignoreCase = true) ||
            error.message.contains("temporary link", ignoreCase = true) ||
            error.message.contains("playback path", ignoreCase = true)
        ) {
            PlayerRecoveryType.SOURCE
        } else {
            PlayerRecoveryType.NETWORK
        }
    }

    is PlayerError.SourceError -> PlayerRecoveryType.SOURCE
    is PlayerError.DecoderError -> PlayerRecoveryType.DECODER
    is PlayerError.DrmError -> PlayerRecoveryType.DRM
    is PlayerError.UnknownError -> {
        if (error.message.contains("timeout", ignoreCase = true)) {
            PlayerRecoveryType.BUFFER_TIMEOUT
        } else {
            PlayerRecoveryType.UNKNOWN
        }
    }
}

internal fun resolvePlaybackErrorMessage(error: PlayerError): String = when (classifyPlaybackError(error)) {
    PlayerRecoveryType.NETWORK -> "Bu yayın şu anda yanıt vermiyor. Yeniden deneyebilir veya başka bir kaynak seçebilirsiniz."
    PlayerRecoveryType.SOURCE -> when {
        error.message.contains("HTTP 456", ignoreCase = true) ||
            error.message.contains("access denied", ignoreCase = true) ->
            "Yayın sunucusu bu kanalın oynatılmasını reddetti. MAC adresi veya abonelik bu yayına erişim iznine sahip olmayabilir."

        error.message.contains("HTTP 509", ignoreCase = true) ->
            "Yayın sunucusu oynatmayı reddetti; muhtemelen maksimum bağlantı sınırı veya bant genişliği limiti aşıldı."

        error.message.contains("temporary link", ignoreCase = true) ->
            "Bu portal oynatma için boş veya geçersiz bir geçici bağlantı oluşturdu."

        error.message.contains("playback path", ignoreCase = true) ->
            "Bu portal varsayılan yayın komutundan farklı bir oynatma yolu gerektiriyor."

        else -> "Bu yayın mevcut yollar üzerinden başlatılamadı."
    }

    PlayerRecoveryType.DECODER -> "Bu video biçimi cihazınızda oynatılamıyor."
    PlayerRecoveryType.DRM -> "Oynatma geçerli DRM kimlik bilgileri veya desteklenen bir cihaz güvenlik seviyesi gerektiriyor."
    PlayerRecoveryType.BUFFER_TIMEOUT -> "Oynatma bu yayında çok uzun süre arabelleğe alma aşamasında takılı kaldı."
    PlayerRecoveryType.CATCH_UP -> "Seçilen program için geriye dönük izleme mevcut değil."
    PlayerRecoveryType.UNKNOWN -> error.message.ifBlank { "Oynatma bilinmeyen bir nedenden dolayı başarısız oldu." }
}
