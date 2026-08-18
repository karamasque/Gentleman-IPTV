package com.kaynanamtv.app.ui.components

private val progressRatioRegex = Regex("(\\d+)\\s*/\\s*(\\d+)")

data class SyncProgressDetails(
    val stageLabel: String,
    val detailMessage: String,
    val fraction: Float?,
    val percentage: Int?,
    val ratioText: String?
)

fun extractProgressFraction(message: String): Float? {
    val match = progressRatioRegex.find(message) ?: return null
    val current = match.groupValues[1].toIntOrNull() ?: return null
    val total = match.groupValues[2].toIntOrNull() ?: return null
    if (total <= 0) return null
    return (current.toFloat() / total.toFloat()).coerceIn(0f, 1f)
}

fun parseSyncProgressDetails(message: String): SyncProgressDetails {
    val match = progressRatioRegex.find(message)
    var fraction: Float? = null
    var percentage: Int? = null
    var ratioText: String? = null

    if (match != null) {
        val current = match.groupValues[1].toIntOrNull()
        val total = match.groupValues[2].toIntOrNull()
        if (current != null && total != null && total > 0) {
            val f = (current.toFloat() / total.toFloat()).coerceIn(0f, 1f)
            fraction = f
            percentage = (f * 100).toInt()
            ratioText = "$current / $total"
        }
    }

    val stageLabel = when {
        message.contains("Sunucu doğrulan", ignoreCase = true) ||
        message.contains("Authenticat", ignoreCase = true) ||
        message.contains("Connecting", ignoreCase = true) -> "1/4 • Sunucu Doğrulanıyor"
        message.contains("kategoril", ignoreCase = true) -> "2/4 • Kategoriler Alınıyor"
        message.contains("Canlı TV", ignoreCase = true) || message.contains("kanal", ignoreCase = true) -> "3/4 • Kanallar Hazırlanıyor"
        message.contains("Film", ignoreCase = true) -> "3/4 • Filmler"
        message.contains("Dizi", ignoreCase = true) -> "3/4 • Diziler"
        message.contains("Rehber", ignoreCase = true) || message.contains("EPG", ignoreCase = true) -> "4/4 • TV Rehberi"
        message.contains("aktiv", ignoreCase = true) || message.contains("Updating", ignoreCase = true) -> "4/4 • IPTV Aktif Ediliyor"
        message.contains("tamamland", ignoreCase = true) || message.contains("Completed", ignoreCase = true) -> "Tamamlandı"
        message.contains("bekleni", ignoreCase = true) -> "Sunucu bekleniyor…"
        message.contains("bağlan", ignoreCase = true) -> "Sunucuya Bağlanılıyor"
        else -> "Senkronize Ediliyor"
    }

    return SyncProgressDetails(
        stageLabel = if (percentage != null) "$stageLabel (%$percentage)" else stageLabel,
        detailMessage = message,
        fraction = fraction,
        percentage = percentage,
        ratioText = ratioText
    )
}
