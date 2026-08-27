package com.kaynanamtv.app.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kaynanamtv.app.ui.interaction.TvClickableSurface
import com.kaynanamtv.app.ui.theme.OnSurfaceDim
import com.kaynanamtv.app.ui.theme.Primary

@Composable
internal fun StorageCleanerCard(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val breakdown by viewModel.storageBreakdown.collectAsState()
    val isCleaning by viewModel.isCleaningStorage.collectAsState()
    val freedBytes by viewModel.lastFreedStorageBytes.collectAsState()

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1E293B))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Column {
            Text(
                text = "Depolama ve Önbellek Yönetimi",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "Uygulamanın hafızasını şişiren eski yayın akışı rehberlerini, resim önbelleklerini ve geçici dosyaları temizler.",
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceDim
            )
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(Color(0xFF0F172A))
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            StorageRow(label = "Veritabanı Boyutu", sizeBytes = breakdown.dbSizeBytes)
            StorageRow(label = "Görsel & Logo Önbelleği", sizeBytes = breakdown.imageCacheSizeBytes)
            StorageRow(label = "Geçici Dosyalar (Timeshift/Temp)", sizeBytes = breakdown.tempSizeBytes)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Toplam Depolama Kullanımı",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Text(
                    text = formatFileSize(breakdown.totalBytes),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF66BB6A)
                )
            }
        }

        AnimatedVisibility(visible = freedBytes != null && freedBytes!! > 0L) {
            Text(
                text = "✓ Başarıyla ${formatFileSize(freedBytes ?: 0L)} hafıza temizlendi!",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF4ADE80),
                fontWeight = FontWeight.SemiBold
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End
        ) {
            TvClickableSurface(
                onClick = { if (!isCleaning) viewModel.cleanAppStorageAndCache() },
                enabled = !isCleaning,
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Primary,
                    focusedContainerColor = Primary.copy(alpha = 0.8f)
                )
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (isCleaning) {
                        CircularProgressIndicator(
                            color = MaterialTheme.colorScheme.onPrimary,
                            modifier = Modifier.height(16.dp)
                        )
                    }
                    Text(
                        text = if (isCleaning) "Temizleniyor..." else "Önbelleği ve Geçici Verileri Temizle",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onPrimary,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun StorageRow(label: String, sizeBytes: Long) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = OnSurfaceDim
        )
        Text(
            text = formatFileSize(sizeBytes),
            style = MaterialTheme.typography.bodySmall,
            color = Color.White,
            fontWeight = FontWeight.Medium
        )
    }
}

private fun formatFileSize(bytes: Long): String {
    if (bytes <= 0L) return "0 MB"
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    return if (mb >= 1024.0) {
        String.format("%.2f GB", mb / 1024.0)
    } else {
        String.format("%.1f MB", mb)
    }
}
