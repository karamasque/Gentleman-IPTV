package com.kaynanamtv.app.ui.screens.update

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kaynanamtv.app.BuildConfig
import com.kaynanamtv.app.ui.design.AppColors
import com.kaynanamtv.app.ui.interaction.TvButton
import com.kaynanamtv.app.update.AppUpdateDownloadStatus

private tailrec fun Context.findActivity(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}

@Composable
fun ForceUpdateScreen(
    viewModel: ForceUpdateViewModel = hiltViewModel()
) {
    val currentContext = LocalContext.current
    val exitApp: () -> Unit = {
        val activity = currentContext.findActivity()
        if (activity != null) {
            activity.finishAffinity()
        } else {
            kotlin.system.exitProcess(0)
        }
    }

    // ── Hard Block: Back button exits the app instead of bypassing force update ──
    BackHandler(enabled = true) {
        exitApp()
    }
    val remoteConfig by viewModel.remoteConfig.collectAsStateWithLifecycle()
    val downloadState by viewModel.downloadState.collectAsStateWithLifecycle()
    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()

    val currentVersionName = BuildConfig.VERSION_NAME
    val currentVersionCode = BuildConfig.VERSION_CODE
    val latestVersionName = remoteConfig?.latestVersionName ?: "1.0.67"
    val minVersionCode = remoteConfig?.minimumSupportedVersionCode ?: 67
    val releaseNotes = remoteConfig?.releaseNotes.orEmpty()

    val bgBrush = Brush.verticalGradient(
        colors = listOf(
            Color(0xFF0F172A),
            Color(0xFF020617)
        )
    )

    val borderGlow = Brush.linearGradient(
        colors = listOf(
            Color(0xFFEF4444),
            Color(0xFFF97316)
        )
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(bgBrush),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .widthIn(max = 680.dp)
                .fillMaxWidth(0.92f)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0xFF0B0F19))
                .border(2.dp, borderGlow, RoundedCornerShape(24.dp))
                .padding(32.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // ── Header Icon & Title ──────────────────────────────────────────
            Box(
                modifier = Modifier
                    .size(72.dp)
                    .background(Color(0xFFEF4444).copy(alpha = 0.15f), CircleShape)
                    .border(2.dp, Color(0xFFEF4444).copy(alpha = 0.5f), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "🛑",
                    fontSize = 36.sp
                )
            }

            Text(
                text = "KaynanamTV — Güncelleme Gerekli",
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                fontWeight = FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )

            Text(
                text = "Kullandığınız uygulama sürümü (v$currentVersionName) artık desteklenmemektedir. Kesintisiz yayın ve güvenlik standartları gereği devam edebilmek için en yeni sürüme (v$latestVersionName) güncellemeniz zorunludur.",
                style = MaterialTheme.typography.bodyMedium,
                color = Color(0xFF94A3B8),
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            // ── Sürüm Bilgi Kartı ───────────────────────────────────────────
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color(0xFF1E293B).copy(alpha = 0.7f))
                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(14.dp))
                    .padding(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Mevcut Sürümünüz:",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8)
                        )
                        Text(
                            text = "v$currentVersionName (Kod: $currentVersionCode)",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFEF4444),
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Gerekli Minimum Sürüm:",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8)
                        )
                        Text(
                            text = "v$latestVersionName (Kod: $minVersionCode)",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF22C55E),
                            fontWeight = FontWeight.Bold
                        )
                    }

                    if (releaseNotes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Yenilikler / Notlar:",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFF94A3B8),
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = releaseNotes,
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFE2E8F0)
                        )
                    }
                }
            }

            // ── Download Progress Bar ───────────────────────────────────────
            if (downloadState.status == AppUpdateDownloadStatus.Downloading) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "İndiriliyor... %${downloadState.progressPercentage}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color(0xFF38BDF8),
                            fontWeight = FontWeight.Bold
                        )
                        if (downloadState.bytesTotal > 0) {
                            val downloadedMb = downloadState.bytesDownloaded / (1024.0 * 1024.0)
                            val totalMb = downloadState.bytesTotal / (1024.0 * 1024.0)
                            Text(
                                text = String.format(java.util.Locale.US, "%.1f MB / %.1f MB", downloadedMb, totalMb),
                                style = MaterialTheme.typography.bodySmall,
                                color = Color(0xFF94A3B8)
                            )
                        }
                    }

                    LinearProgressIndicator(
                        progress = { downloadState.progressPercentage / 100f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(8.dp)
                            .clip(RoundedCornerShape(4.dp)),
                        color = Color(0xFF38BDF8),
                        trackColor = Color(0xFF334155)
                    )
                }
            } else if (downloadState.status == AppUpdateDownloadStatus.Downloaded) {
                Text(
                    text = "✅ İndirme tamamlandı! Güncellemeyi kurabilirsiniz.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF22C55E),
                    fontWeight = FontWeight.Bold
                )
            }

            AnimatedVisibility(visible = userMessage != null) {
                userMessage?.let { msg ->
                    Text(
                        text = msg,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color(0xFFFBBF24),
                        textAlign = TextAlign.Center
                    )
                }
            }

            // ── Action Buttons ──────────────────────────────────────────────
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                when {
                    downloadState.status == AppUpdateDownloadStatus.Downloaded -> {
                        TvButton(
                            onClick = { viewModel.installDownloadedUpdate() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = ButtonDefaults.shape(RoundedCornerShape(12.dp)),
                            colors = ButtonDefaults.colors(
                                containerColor = Color(0xFF22C55E),
                                focusedContainerColor = Color(0xFF16A34A),
                                contentColor = Color.White
                            )
                        ) {
                            Text(
                                text = "🚀 GÜNCELLEMEYİ KUR",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }

                    downloadState.status == AppUpdateDownloadStatus.Downloading -> {
                        TvButton(
                            onClick = {},
                            enabled = false,
                            modifier = Modifier.fillMaxWidth(),
                            shape = ButtonDefaults.shape(RoundedCornerShape(12.dp)),
                            colors = ButtonDefaults.colors(
                                containerColor = Color(0xFF475569),
                                disabledContainerColor = Color(0xFF334155),
                                disabledContentColor = Color(0xFF94A3B8)
                            )
                        ) {
                            Text(
                                text = "⏳ İndiriliyor... %${downloadState.progressPercentage}",
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }

                    else -> {
                        TvButton(
                            onClick = { viewModel.downloadAndInstall() },
                            modifier = Modifier.fillMaxWidth(),
                            shape = ButtonDefaults.shape(RoundedCornerShape(12.dp)),
                            colors = ButtonDefaults.colors(
                                containerColor = Color(0xFF2563EB),
                                focusedContainerColor = Color(0xFF1D4ED8),
                                contentColor = Color.White
                            )
                        ) {
                            Text(
                                text = "⬇️ GÜNCELLEMEYİ İNDİR VE KUR",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                        }
                    }
                }

                // Exit App Button
                TvButton(
                    onClick = exitApp,
                    modifier = Modifier.fillMaxWidth(),
                    shape = ButtonDefaults.shape(RoundedCornerShape(12.dp)),
                    colors = ButtonDefaults.colors(
                        containerColor = Color(0xFF3F1D1D).copy(alpha = 0.6f),
                        focusedContainerColor = Color(0xFFDC2626),
                        contentColor = Color(0xFFFCA5A5),
                        focusedContentColor = Color.White
                    ),
                    border = ButtonDefaults.border(
                        border = androidx.tv.material3.Border(
                            border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f)),
                            shape = RoundedCornerShape(12.dp)
                        ),
                        focusedBorder = androidx.tv.material3.Border(
                            border = androidx.compose.foundation.BorderStroke(2.dp, Color.White),
                            shape = RoundedCornerShape(12.dp)
                        )
                    )
                ) {
                    Text(
                        text = "🚪 UYGULAMADAN ÇIK",
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        modifier = Modifier.padding(vertical = 4.dp)
                    )
                }
            }
        }
    }
}
