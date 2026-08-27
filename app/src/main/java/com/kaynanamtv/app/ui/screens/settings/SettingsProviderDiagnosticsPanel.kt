package com.kaynanamtv.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kaynanamtv.app.R
import com.kaynanamtv.app.ui.theme.ErrorColor
import com.kaynanamtv.app.ui.theme.OnSurface
import com.kaynanamtv.app.ui.theme.OnSurfaceDim
import com.kaynanamtv.app.ui.theme.Primary
import com.kaynanamtv.app.ui.theme.Secondary
import com.kaynanamtv.app.ui.time.LocalAppTimeFormat
import com.kaynanamtv.app.ui.time.createDateTimeFormat
import com.kaynanamtv.domain.model.Provider
import com.kaynanamtv.domain.model.ProviderType
import java.text.DateFormat
import java.util.Locale

@Composable
internal fun ProviderDiagnosticsPanel(
    provider: Provider,
    diagnostics: ProviderDiagnosticsUiModel,
    movieIndexInProgress: Boolean,
    databaseMaintenance: DatabaseMaintenanceUiModel?,
    showDatabaseHealth: Boolean = false,
    syncWarnings: List<String> = emptyList()
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        val appTimeFormat = LocalAppTimeFormat.current
        val dateTimeFormat = remember(appTimeFormat) { appTimeFormat.createDateTimeFormat() }
        val translatedStatus = when (diagnostics.lastSyncStatus.uppercase()) {
            "SUCCESS" -> "BAŞARILI"
            "PARTIAL" -> "KISMİ"
            "FAILURE" -> "BAŞARISIZ"
            "RUNNING" -> "ÇALIŞIYOR"
            "PENDING" -> "BEKLİYOR"
            else -> diagnostics.lastSyncStatus
        }
        val lastSyncTime = formatDiagnosticTimestamp(provider.lastSyncedAt, dateTimeFormat)
        val statusText = if (lastSyncTime != null) {
            "$translatedStatus ($lastSyncTime)"
        } else {
            translatedStatus
        }
        Text(
            text = stringResource(R.string.settings_diagnostic_status, statusText),
            style = MaterialTheme.typography.labelSmall,
            color = OnSurface
        )

        val activeProgressMessages = remember(syncWarnings) {
            syncWarnings.filter { it.startsWith("Arka plan indeksi:") }
                .map { it.removePrefix("Arka plan indeksi:").trim() }
        }

        if (activeProgressMessages.isNotEmpty()) {
            activeProgressMessages.forEach { msg ->
                Text(
                    text = msg,
                    style = MaterialTheme.typography.bodySmall,
                    color = Secondary,
                    fontWeight = FontWeight.Medium
                )
            }
        } else {
            diagnostics.healthSummary(provider.type, movieIndexInProgress)?.let { summary ->
                val color = if (summary.contains("bekliyor", ignoreCase = true) || summary.contains("güncelleniyor", ignoreCase = true)) {
                    Secondary
                } else {
                    ErrorColor
                }
                Text(
                    text = summary,
                    style = MaterialTheme.typography.bodySmall,
                    color = color,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        if (showDatabaseHealth) {
            databaseMaintenance?.let { report ->
                DatabaseMaintenancePanel(report = report)
            }
        }
    }
}

@Composable
private fun DatabaseMaintenancePanel(report: DatabaseMaintenanceUiModel) {
    val appTimeFormat = LocalAppTimeFormat.current
    val dateTimeFormat = remember(appTimeFormat) { appTimeFormat.createDateTimeFormat() }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(10.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = "Veritabanı Sağlık Bilgileri",
            style = MaterialTheme.typography.titleSmall,
            color = Primary
        )
        Text(
            text = "Son Bakım: ${formatDiagnosticTimestamp(report.ranAt, dateTimeFormat)}",
            style = MaterialTheme.typography.bodySmall,
            color = OnSurface
        )
        Text(
            text = "Veritabanı: ${formatMaintenanceBytes(report.mainDbBytes)} • Önbellek: ${formatMaintenanceBytes(report.walBytes)} • Boşaltılabilir: ${formatMaintenanceBytes(report.reclaimableBytes)}",
            style = MaterialTheme.typography.bodySmall,
            color = OnSurfaceDim
        )
        Text(
            text = "Kayıtlar: Kanallar ${formatMaintenanceCount(report.channelRows)}, Filmler ${formatMaintenanceCount(report.movieRows)}, Diziler ${formatMaintenanceCount(report.seriesRows)}, Bölümler ${formatMaintenanceCount(report.episodeRows)}, Rehber ${formatMaintenanceCount(report.epgProgrammeRows)}",
            style = MaterialTheme.typography.bodySmall,
            color = OnSurfaceDim
        )
    }
}

private fun ProviderDiagnosticsUiModel.healthSummary(
    providerType: ProviderType,
    movieIndexInProgress: Boolean
): String? {
    val warnings = buildList {
        if (liveSequentialFailuresRemembered) {
            add("Canlı TV senkronizasyonu kontrol edilmeli")
        }
        if (movieParallelFailuresRemembered) {
            add(
                if (movieWarningsCount > 0) {
                    "Film kataloğunda $movieWarningsCount kayıtlı uyarı var"
                } else {
                    "Film senkronizasyonunda kayıtlı uyarılar var"
                }
            )
        }
        if (movieCatalogStale && !movieIndexInProgress) {
            add("Film kataloğu güncellenmeyi bekliyor ('Yenile' butonuna basabilirsiniz)")
        }
        if (providerType == ProviderType.XTREAM_CODES && seriesSequentialFailuresRemembered) {
            add("Dizi senkronizasyonu kontrol edilmeli")
        }
    }
    if (warnings.isEmpty()) {
        val streakParts = buildList {
            if (liveHealthySyncStreak > 0) add("Canlı TV durumu: $liveHealthySyncStreak")
            if (movieHealthySyncStreak > 0) add("Film durumu: $movieHealthySyncStreak")
            if (providerType == ProviderType.XTREAM_CODES && seriesHealthySyncStreak > 0) {
                add("Dizi durumu: $seriesHealthySyncStreak")
            }
        }
        return streakParts.takeIf { it.isNotEmpty() }?.joinToString(" • ")
    }
    return warnings.joinToString(" • ")
}

private fun formatDiagnosticTimestamp(timestamp: Long, dateTimeFormat: DateFormat): String? =
    if (timestamp <= 0L) {
        null
    } else {
        dateTimeFormat.format(java.util.Date(timestamp))
    }

private fun formatMaintenanceBytes(bytes: Long): String {
    if (bytes <= 0L) return "0 B"
    val units = arrayOf("B", "KB", "MB", "GB", "TB")
    var value = bytes.toDouble()
    var unitIndex = 0
    while (value >= 1024.0 && unitIndex < units.lastIndex) {
        value /= 1024.0
        unitIndex++
    }
    val formatted = if (value >= 10 || unitIndex == 0) {
        value.toInt().toString()
    } else {
        String.format(Locale.US, "%.1f", value)
    }
    return "$formatted ${units[unitIndex]}"
}

private fun formatMaintenanceCount(value: Long): String = when {
    value >= 1_000_000L -> String.format(Locale.US, "%.1fM", value / 1_000_000.0)
    value >= 1_000L -> String.format(Locale.US, "%.1fk", value / 1_000.0)
    else -> value.toString()
}