package com.kaynanamtv.app.ui.screens.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kaynanamtv.app.R
import com.kaynanamtv.app.ui.components.GlassmorphicBadge
import com.kaynanamtv.app.ui.interaction.TvButton
import com.kaynanamtv.app.ui.theme.OnSurface
import com.kaynanamtv.app.ui.theme.OnSurfaceDim
import com.kaynanamtv.app.ui.theme.Primary

@Composable
fun SyncFinishedSummaryOverlay(
    isVisible: Boolean,
    channelCount: Int = 0,
    movieCount: Int = 0,
    seriesCount: Int = 0,
    onDismiss: () -> Unit
) {
    if (!isVisible) return

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        )
    ) {
        val buttonFocusRequester = remember { FocusRequester() }

        Box(
            modifier = Modifier
                .widthIn(max = 440.dp)
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            Color(0xFF1E293B),
                            Color(0xFF0F172A)
                        )
                    ),
                    shape = RoundedCornerShape(20.dp)
                )
                .border(
                    width = 1.dp,
                    brush = Brush.linearGradient(
                        colors = listOf(
                            Primary.copy(alpha = 0.6f),
                            Color.White.copy(alpha = 0.1f)
                        )
                    ),
                    shape = RoundedCornerShape(20.dp)
                )
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Text(
                    text = "🎉",
                    style = MaterialTheme.typography.displaySmall
                )

                Text(
                    text = "Kataloğunuz Güncellendi!",
                    style = MaterialTheme.typography.titleLarge,
                    color = OnSurface,
                    fontWeight = FontWeight.Bold
                )

                Text(
                    text = "KaynanamTV içerik listeniz başarıyla senkronize edildi ve izlemeye hazır.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = OnSurfaceDim,
                    fontWeight = FontWeight.Normal
                )

                Spacer(modifier = Modifier.height(4.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    if (channelCount > 0) {
                        GlassmorphicBadge(
                            text = "📺 $channelCount Canlı",
                            isHighlighted = true,
                            accentColor = Color(0xFF6366F1)
                        )
                    }
                    if (movieCount > 0) {
                        GlassmorphicBadge(
                            text = "🎬 $movieCount Film",
                            isHighlighted = true,
                            accentColor = Color(0xFF10B981)
                        )
                    }
                    if (seriesCount > 0) {
                        GlassmorphicBadge(
                            text = "🍿 $seriesCount Dizi",
                            isHighlighted = true,
                            accentColor = Color(0xFFF59E0B)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                TvButton(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusRequester(buttonFocusRequester)
                ) {
                    Text(
                        text = "İzlemeye Başla",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
