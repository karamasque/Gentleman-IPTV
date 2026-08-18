package com.kaynanamtv.app.ui.screens.player.overlay

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.VolumeMute
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.SettingsBackupRestore
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.Text
import androidx.tv.material3.Icon
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.ViewSidebar
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.ListAlt
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.AspectRatio
import com.kaynanamtv.app.R
import com.kaynanamtv.app.ui.design.AppColors
import com.kaynanamtv.app.ui.interaction.TvClickableSurface
import java.util.Locale

@Composable
internal fun PlayerOverlayPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    val borderBrush = Brush.linearGradient(
        colors = listOf(
            AppColors.Brand.copy(alpha = 0.55f),
            AppColors.NeonCyan.copy(alpha = 0.55f),
            AppColors.Brand.copy(alpha = 0.12f)
        )
    )
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(26.dp),
        border = Border(
            border = androidx.compose.foundation.BorderStroke(
                1.2.dp,
                borderBrush
            ),
            shape = RoundedCornerShape(26.dp)
        ),
        colors = androidx.tv.material3.SurfaceDefaults.colors(
            containerColor = Color(0xFF060B12).copy(alpha = 0.86f)
        )
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content
        )
    }
}

val Primary: Color get() = AppColors.Brand
private val SurfaceHighlight = Color(0xFF1E2E4E)
private val Canvas = Color(0xFF0E1929)

@Composable
internal fun PlayerMetaRow(label: String, value: String, maxLines: Int = 1) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = AppColors.TextTertiary,
            modifier = Modifier.weight(0.44f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
            color = AppColors.TextPrimary,
            modifier = Modifier.weight(0.56f),
            maxLines = maxLines,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
internal fun PlayerOverlaySectionLabel(text: String) {
    Text(
        text = text,
        color = Primary,
        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
        fontWeight = FontWeight.Bold
    )
}

internal fun formatTimeLabel(ms: Long): String {
    val totalSeconds = (ms / 1_000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }
}

internal fun formatTimeshiftOffsetLabel(offsetMs: Long): String {
    val totalSeconds = offsetMs / 1000L
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }
}

@Composable
internal fun QuickActionButton(
    icon: String,
    label: String,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    circular: Boolean = false,
    colors: androidx.tv.material3.ClickableSurfaceColors? = null,
    onInteraction: () -> Unit = {},
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val isRecording = label == "Kayıt" || label == "Record" || icon.lowercase().trim() == "rec" || icon.lowercase().trim() == "kayıt"
    
    val baseIconColor = getColorFor(icon)
    
    // Theme harmonization: Focused background is Brand (active theme color), content is Canvas (dark theme background)
    val focusedBg = if (isRecording) Color(0xFFFF3B30) else AppColors.Brand
    val defaultBg = Color.White.copy(alpha = 0.04f)
    
    val currentColors = colors ?: ClickableSurfaceDefaults.colors(
        containerColor = defaultBg,
        focusedContainerColor = focusedBg,
        pressedContainerColor = focusedBg.copy(alpha = 0.8f)
    )

    val shape = if (circular) CircleShape else RoundedCornerShape(if (compact) 12.dp else 14.dp)

    TvClickableSurface(
        onClick = {
            onInteraction()
            onClick()
        },
        modifier = modifier
            .then(
                if (circular) {
                    Modifier.size(46.dp)
                } else {
                    Modifier
                        .widthIn(min = if (compact) 82.dp else 114.dp, max = if (compact) 100.dp else 138.dp)
                        .height(if (compact) 44.dp else 54.dp)
                }
            )
            .onFocusChanged {
                isFocused = it.isFocused
                if (it.isFocused) onInteraction()
            },
        shape = ClickableSurfaceDefaults.shape(shape),
        colors = currentColors,
        border = ClickableSurfaceDefaults.border(
            border = Border(
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)),
                shape = shape
            ),
            focusedBorder = Border(
                border = BorderStroke(2.dp, focusedBg),
                shape = shape
            )
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.05f)
    ) {
        if (circular) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                val iconVector = getIconFor(icon)
                if (iconVector != null) {
                    Icon(
                        imageVector = iconVector,
                        contentDescription = label,
                        tint = if (isFocused) AppColors.Canvas else baseIconColor,
                        modifier = Modifier.size(20.dp)
                    )
                } else {
                    Text(
                        text = icon.take(2).uppercase(Locale.getDefault()),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 11.sp),
                        color = if (isFocused) AppColors.Canvas else baseIconColor,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier.fillMaxSize().padding(horizontal = 6.dp, vertical = 2.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                val iconVector = getIconFor(icon)
                if (iconVector != null) {
                    Icon(
                        imageVector = iconVector,
                        contentDescription = label,
                        tint = if (isFocused) AppColors.Canvas else baseIconColor,
                        modifier = Modifier.size(if (compact) 16.dp else 18.dp)
                    )
                } else {
                    Text(
                        text = icon.uppercase(Locale.getDefault()),
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = if (compact) 8.sp else 10.sp),
                        color = if (isFocused) AppColors.Canvas else baseIconColor,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Spacer(modifier = Modifier.height(if (compact) 1.dp else 3.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodySmall.copy(fontSize = if (compact) 8.sp else 10.sp),
                    color = if (isFocused) AppColors.Canvas else Color.White.copy(alpha = 0.8f),
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

private fun getColorFor(targetIcon: String): Color {
    val term = targetIcon.lowercase(Locale.getDefault()).trim()
    return when {
        term == "oynatma" || term == "playback" || term == "dvr" || term == "duraklat" || term == "play" || term == "pause" -> Color(0xFF00D2FF) // Cyan
        term == "sessiz" || term == "mute" -> Color(0xFFFF416C) // Neon Red
        term == "altyazılar" || term == "subs" || term == "subtitles" || term == "altyazı" -> Color(0xFFFFD700) // Gold
        term == "kalite" || term == "quality" -> Color(0xFFA78BFA) // Purple/Lavender
        term == "varyantlar" || term == "variants" -> Color(0xFF8B5CF6) // Deep Purple
        term == "biçim" || term == "format" || term == "en-boy" || term == "en-boy oranı" -> Color(0xFF06B6D4) // Teal
        term == "ses" || term == "audio" -> Color(0xFF10B981) // Green
        term == "a/v" || term == "a/v senkr" -> Color(0xFF94A3B8) // Slate
        term == "rehber" || term == "guide" || term == "epg" -> Color(0xFFF59E0B) // Amber
        term == "böl" || term == "split" || term == "multiview" || term == "bölümler" -> Color(0xFF6366F1) // Indigo
        term == "tanılama" || term == "diagnostics" || term == "istatistikler" -> Color(0xFF60A5FA) // Sky Blue
        term == "grup" || term == "group" || term.startsWith("tr ") || term.contains("✦") -> Color(0xFF34D399) // Mint
        term == "rec" || term == "kayıt" || term == "record" -> Color(0xFFFF3B30) // Solid Red
        term == "c-up" || term == "geri sarma" || term == "catchup" || term == "süre" -> Color(0xFFEC4899) // Pink
        term == "ekrana yansıt" || term == "cast" || term == "oyuncular" || term == "yansıt" -> Color(0xFF60A5FA) // Blue
        term == "pip" -> Color(0xFF06B6D4) // Teal
        term == "kanal" || term == "kanal listesi" || term == "channels" -> Color(0xFF60A5FA) // Blue
        term == "görüntüle" || term == "view" || term == "external" || term == "tv" || term == "harici" || term == "hız" -> Color(0xFF34D399) // Mint
        else -> Color.White
    }
}

private fun getIconFor(targetIcon: String): ImageVector? {
    val term = targetIcon.lowercase(Locale.getDefault()).trim()
    return when {
        term == "oynatma" || term == "playback" || term == "dvr" || term == "duraklat" || term == "play" || term == "pause" -> Icons.Default.PlayArrow
        term == "sessiz" || term == "mute" -> Icons.Default.VolumeOff
        term == "altyazılar" || term == "subs" || term == "subtitles" -> Icons.Default.Subtitles
        term == "kalite" || term == "quality" -> Icons.Default.Settings
        term == "varyantlar" || term == "variants" -> Icons.Default.List
        term == "biçim" || term == "format" -> Icons.Default.AspectRatio
        term == "ses" || term == "audio" -> Icons.Default.VolumeUp
        term == "a/v" || term == "a/v senkr" -> Icons.Default.Refresh
        term == "rehber" || term == "guide" || term == "epg" -> Icons.Default.ListAlt
        term == "böl" || term == "split" || term == "multiview" -> Icons.Default.ViewSidebar
        term == "tanılama" || term == "diagnostics" || term == "istatistikler" -> Icons.Default.Info
        term == "grup" || term == "group" || term.startsWith("tr ") || term.contains("✦") -> Icons.Default.Group
        term == "rec" || term == "kayıt" || term == "record" -> Icons.Default.FiberManualRecord
        term == "c-up" || term == "geri sarma" || term == "catchup" -> Icons.Default.SettingsBackupRestore
        term == "ekrana yansıt" || term == "cast" || term == "oyuncular" -> Icons.Default.Cast
        term == "pip" -> Icons.Default.PictureInPicture
        term == "kanal" || term == "kanal listesi" || term == "channels" -> Icons.Default.Menu
        term == "görüntüle" || term == "view" || term == "external" || term == "tv" -> Icons.Default.Tv
        else -> null
    }
}
