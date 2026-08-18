package com.kaynanamtv.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kaynanamtv.app.ui.interaction.TvClickableSurface
import com.kaynanamtv.app.ui.theme.OnSurfaceDim
import com.kaynanamtv.app.ui.theme.Primary
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
internal fun BackgroundSyncSettingsCard(
    viewModel: SettingsViewModel,
    modifier: Modifier = Modifier
) {
    val enabled by viewModel.backgroundSyncEnabled.collectAsState()
    val intervalHours by viewModel.backgroundSyncIntervalHours.collectAsState()
    val wifiOnly by viewModel.backgroundSyncWifiOnly.collectAsState()
    val lastSyncTs by viewModel.lastBackgroundSyncTimestamp.collectAsState()

    var showIntervalDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1E293B))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Arka Plan EPG & Kanal Senkronizasyonu",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "Uygulama kapalıyken kanal listesini ve yayın akışını arka planda otomatik günceller.",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceDim
                )
            }
            Switch(
                checked = enabled,
                onCheckedChange = { viewModel.setBackgroundSyncEnabled(it) },
                colors = SwitchDefaults.colors(
                    checkedThumbColor = Color.White,
                    checkedTrackColor = Primary
                )
            )
        }

        if (enabled) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Güncelleme Sıklığı",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                    Text(
                        text = "Senkronizasyon her $intervalHours saatte bir gerçekleşir.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceDim
                    )
                }

                TvClickableSurface(
                    onClick = { showIntervalDialog = true },
                    modifier = Modifier.clip(RoundedCornerShape(8.dp))
                ) {
                    Text(
                        text = "$intervalHours Saat",
                        style = MaterialTheme.typography.labelLarge,
                        color = Primary,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Sadece Wi-Fi İle Güncelle",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color.White
                    )
                    Text(
                        text = "Mobil veri kullanımını önlemek için yalnızca Wi-Fi ağında çalışır.",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnSurfaceDim
                    )
                }
                Switch(
                    checked = wifiOnly,
                    onCheckedChange = { viewModel.setBackgroundSyncWifiOnly(it) },
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Color.White,
                        checkedTrackColor = Primary
                    )
                )
            }

            val lastSyncFormatted = remember(lastSyncTs) {
                val ts = lastSyncTs
                if (ts != null && ts > 0L) {
                    val sdf = SimpleDateFormat("dd MMMM yyyy HH:mm", Locale("tr", "TR"))
                    sdf.format(Date(ts))
                } else {
                    "Henüz yapılmadı"
                }
            }

            Text(
                text = "Son Arka Plan Senkronizasyonu: $lastSyncFormatted",
                style = MaterialTheme.typography.bodySmall,
                color = Color(0xFF64748B),
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }

    if (showIntervalDialog) {
        val options = listOf(3, 6, 12, 24)
        PremiumSelectionDialog(
            title = "Güncelleme Sıklığı Seçin",
            onDismiss = { showIntervalDialog = false }
        ) {
            options.forEachIndexed { index, hours ->
                LevelOption(
                    level = index,
                    text = "$hours Saat",
                    currentLevel = if (hours == intervalHours) index else -1,
                    onSelect = {
                        viewModel.setBackgroundSyncIntervalHours(hours)
                        showIntervalDialog = false
                    }
                )
            }
        }
    }
}
