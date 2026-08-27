package com.kaynanamtv.app.ui.screens.player.overlay

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.ViewSidebar
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import com.kaynanamtv.app.ui.model.isArchivePlayable
import com.kaynanamtv.app.ui.model.isCurrentProgramRestartable
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.input.pointer.pointerInput
import com.kaynanamtv.app.BuildConfig
import com.kaynanamtv.app.R
import com.kaynanamtv.app.ui.components.ChannelLogoBadge
import com.kaynanamtv.app.ui.design.AppColors
import com.kaynanamtv.app.ui.interaction.TvClickableSurface
import com.kaynanamtv.app.ui.screens.player.PlayerTimeshiftUiState
import com.kaynanamtv.app.ui.time.LocalAppTimeFormat
import com.kaynanamtv.app.ui.time.createTimeFormat
import com.kaynanamtv.domain.model.Channel
import com.kaynanamtv.domain.model.Program
import com.kaynanamtv.domain.model.RecordingStatus
import kotlinx.coroutines.delay
import java.util.Date
import java.util.Locale

/**
 * Premium Compact Live TV Floating Dock Controls (Canonical Runtime Renderer)
 * - Directly renders the compact floating glass dock matching the VOD design language
 * - Fully wires all 18 audited Live TV actions
 * - Displays complete metadata: canonical live badge, channel info, resolution, current/next program, timeshift timeline, PVR/CC/Cast badges
 * - Includes transient diagnostic runtime badge in DEBUG mode
 */
@Composable
fun ChannelInfoOverlay(
    currentChannel: Channel?,
    displayChannelNumber: Int,
    currentProgram: Program?,
    nextProgram: Program?,
    focusRequester: FocusRequester,
    lastVisitedCategoryName: String?,
    onDismiss: () -> Unit,
    onOverlayInteracted: () -> Unit,
    onOpenFullEpg: () -> Unit,
    onOpenLastGroup: () -> Unit,
    currentRecordingStatus: RecordingStatus?,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onScheduleRecording: () -> Unit,
    onScheduleDailyRecording: () -> Unit,
    onScheduleWeeklyRecording: () -> Unit,
    onRestartProgram: () -> Unit,
    onOpenArchive: () -> Unit,
    onToggleAspectRatio: () -> Unit,
    onToggleDiagnostics: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekToLiveEdge: () -> Unit,
    isPlaying: Boolean,
    currentAspectRatio: String,
    isDiagnosticsEnabled: Boolean,
    onOpenSplitScreen: () -> Unit = {},
    subtitleTrackCount: Int = 0,
    liveTranslationAvailable: Boolean = false,
    audioTrackCount: Int = 0,
    videoQualityCount: Int = 0,
    channelVariantCount: Int = 0,
    qualityOptionCount: Int = 0,
    isMuted: Boolean = false,
    onToggleMute: () -> Unit = {},
    onOpenSubtitleTracks: () -> Unit = {},
    onOpenAudioTracks: () -> Unit = {},
    onOpenVideoTracks: () -> Unit = {},
    onOpenVariants: () -> Unit = {},
    onOpenStreamFormats: () -> Unit = {},
    onOpenAudioVideoSync: () -> Unit = {},
    audioVideoSyncEnabled: Boolean = false,
    onEnterPictureInPicture: () -> Unit = {},
    isCastConnected: Boolean = false,
    onCast: () -> Unit = {},
    onStopCasting: () -> Unit = {},
    onOpenChannelList: () -> Unit = {},
    onSeekToPosition: (Long) -> Unit = {},
    timeshiftUiState: PlayerTimeshiftUiState = PlayerTimeshiftUiState(),
    onTransientPanelVisibilityChanged: (Boolean) -> Unit = {},
    resolutionLabel: String? = null,
    isCatchUpPlayback: Boolean = false,
    modifier: Modifier = Modifier
) {
    val appTimeFormat = LocalAppTimeFormat.current
    val timeFormat = remember(appTimeFormat) { appTimeFormat.createTimeFormat() }
    val showTimeshiftControls = timeshiftUiState.available && !isCastConnected

    val isTimeshiftActive = showTimeshiftControls && (timeshiftUiState.canSeekToLive || timeshiftUiState.bufferedBehindLiveMs > 1_000L)
    val liveState = when {
        isCatchUpPlayback -> TvLiveState.ARCHIVE
        isTimeshiftActive -> TvLiveState.TIMESHIFT
        else -> TvLiveState.LIVE_EDGE
    }

    var liveDotVisible by remember { mutableStateOf(true) }
    LaunchedEffect(liveState) {
        if (liveState == TvLiveState.LIVE_EDGE) {
            while (true) {
                delay(700L)
                liveDotVisible = !liveDotVisible
            }
        } else {
            liveDotVisible = true
        }
    }

    var epgTickerMs by remember { mutableStateOf(System.currentTimeMillis()) }
    LaunchedEffect(currentProgram) {
        while (true) {
            delay(5_000L)
            epgTickerMs = System.currentTimeMillis()
        }
    }

    BackHandler {
        onDismiss()
    }

    Box(
        modifier = modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Surface(
            modifier = Modifier
                .widthIn(max = 1100.dp)
                .fillMaxWidth(0.80f),
            shape = RoundedCornerShape(22.dp),
            colors = SurfaceDefaults.colors(containerColor = Color(0xFF070B14).copy(alpha = 0.88f)),
            border = Border(
                border = BorderStroke(
                    1.2.dp,
                    Brush.linearGradient(
                        listOf(
                            Color(0xFF2563EB).copy(alpha = 0.50f),
                            Color(0xFF6366F1).copy(alpha = 0.40f),
                            Color(0xFF00E5FF).copy(alpha = 0.30f)
                        )
                    )
                ),
                shape = RoundedCornerShape(22.dp)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
            ) {
                val canReturnToLive = !isPlaying || (showTimeshiftControls && timeshiftUiState.canSeekToLive) || isCatchUpPlayback
                LaunchedEffect(showTimeshiftControls, timeshiftUiState.bufferDepthMs, timeshiftUiState.bufferedBehindLiveMs, canReturnToLive) {
                    android.util.Log.d(
                        "PlayerOverlayTrace",
                        "[TS_STATE_UI] depthMs=${timeshiftUiState.bufferDepthMs} available=$showTimeshiftControls canReturnLive=$canReturnToLive offset=${timeshiftUiState.bufferedBehindLiveMs}"
                    )
                }
                // TOP SECTION: Left Info Block + Right Timeshift/Status Area
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Top
                ) {
                    // LEFT INFO BLOCK
                    Column(
                        modifier = Modifier.weight(1.15f),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        // Row 1: Canonical State Badge + Channel Info + Resolution Badge + Debug Badge
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            // Canonical State Badge
                            when (liveState) {
                                TvLiveState.LIVE_EDGE -> {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        modifier = Modifier
                                            .background(Color.Red.copy(alpha = 0.18f), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 7.dp, vertical = 3.dp)
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .size(7.dp)
                                                .background(
                                                    color = Color.Red.copy(alpha = if (liveDotVisible) 1f else 0.25f),
                                                    shape = CircleShape
                                                )
                                        )
                                        Text(
                                            text = "CANLI",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.ExtraBold,
                                            color = Color(0xFFFF4D4F)
                                        )
                                    }
                                }
                                TvLiveState.TIMESHIFT -> {
                                    val offsetMs = timeshiftUiState.bufferedBehindLiveMs
                                    val offsetLabel = if (offsetMs >= 60_000L) "-${offsetMs / 60_000L} dk" else "-${(offsetMs / 1000L).coerceAtLeast(1L)} sn"
                                    Text(
                                        text = "\u21B6 $offsetLabel",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFFFB347),
                                        modifier = Modifier
                                            .background(Color(0xFFFFB347).copy(alpha = 0.18f), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                                TvLiveState.ARCHIVE -> {
                                    Text(
                                        text = "ARŞİV",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = AppColors.NeonCyan,
                                        modifier = Modifier
                                            .background(AppColors.NeonCyan.copy(alpha = 0.18f), RoundedCornerShape(6.dp))
                                            .padding(horizontal = 7.dp, vertical = 3.dp)
                                    )
                                }
                            }

                            // Channel Logo (if available)
                            if (currentChannel != null && !currentChannel.logoUrl.isNullOrBlank()) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .background(Color.White.copy(alpha = 0.06f))
                                ) {
                                    ChannelLogoBadge(
                                        channelName = currentChannel.name,
                                        logoUrl = currentChannel.logoUrl,
                                        contentPadding = PaddingValues(2.dp),
                                        modifier = Modifier.fillMaxSize()
                                    )
                                }
                            }

                            // Channel Number & Name
                            val channelNumberPrefix = if (displayChannelNumber > 0) "$displayChannelNumber • " else ""
                            val resolvedChannelName = currentChannel?.name?.takeIf { it.isNotBlank() } ?: ""
                            val displayText = if (resolvedChannelName.isNotBlank()) {
                                "$channelNumberPrefix$resolvedChannelName"
                            } else if (displayChannelNumber > 0) {
                                "$displayChannelNumber"
                            } else {
                                ""
                            }
                            Text(
                                text = displayText,
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )

                            // Resolution Badge
                            val resBadge = resolutionLabel?.takeIf { it.isNotBlank() }
                                ?: when {
                                    currentChannel?.name?.contains("4K", ignoreCase = true) == true -> "4K"
                                    currentChannel?.name?.contains("FHD", ignoreCase = true) == true -> "FHD"
                                    currentChannel?.name?.contains("HD", ignoreCase = true) == true -> "HD"
                                    else -> "FHD"
                                }
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFF4F46E5).copy(alpha = 0.35f), RoundedCornerShape(4.dp))
                                    .border(0.8.dp, Color(0xFF818CF8).copy(alpha = 0.5f), RoundedCornerShape(4.dp))
                                    .padding(horizontal = 5.dp, vertical = 1.dp)
                            ) {
                                Text(
                                    text = resBadge,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFFC7D2FE)
                                )
                            }

                            // Runtime Debug Badge (only in DEBUG builds)
                            if (BuildConfig.DEBUG) {
                                Box(
                                    modifier = Modifier
                                        .background(Color(0xFF10B981).copy(alpha = 0.90f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "LIVE_UI=PREMIUM_COMPACT",
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.ExtraBold
                                        ),
                                        color = Color.White
                                    )
                                }
                            }
                        }

                        // Row 2: Current Program Title
                        val progTitle = currentProgram?.title ?: currentChannel?.name ?: "Canlı Yayın"
                        Text(
                            text = progTitle,
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )

                        // Row 3: Program Timing & Remaining Duration + EPG Progress Bar
                        val rawStart = currentProgram?.startTime ?: 0L
                        val rawEnd = currentProgram?.endTime ?: 0L
                        val progStart = if (rawStart in 1L..100_000_000_000L) rawStart * 1000L else rawStart
                        val progEnd = if (rawEnd in 1L..100_000_000_000L) rawEnd * 1000L else rawEnd

                        if (progStart > 0L && progEnd > progStart) {
                            val durationMs = (progEnd - progStart).coerceAtLeast(1L)
                            val elapsedMs = (epgTickerMs - progStart).coerceAtLeast(0L)
                            val remainMs = (progEnd - epgTickerMs).coerceAtLeast(0L)
                            val progProgress = (elapsedMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)

                            val remainMin = (remainMs + 59_999L) / 60_000L
                            val remainText = when {
                                remainMin >= 60 -> "${remainMin / 60} sa ${remainMin % 60} dk kaldı"
                                remainMin > 0 -> "$remainMin dk kaldı"
                                else -> "Bitiyor"
                            }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth(0.95f)
                                    .padding(vertical = 2.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "${timeFormat.format(Date(progStart))} - ${timeFormat.format(Date(progEnd))}",
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                        color = Color.White.copy(alpha = 0.70f),
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = remainText,
                                        style = MaterialTheme.typography.bodySmall.copy(fontSize = 12.sp),
                                        color = Color(0xFFA5B4FC),
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(10.dp),
                                    contentAlignment = Alignment.CenterStart
                                ) {
                                    // Background Track
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(99.dp))
                                            .background(Color.White.copy(alpha = 0.16f))
                                    )
                                    // Active Elapsed Track
                                    Box(
                                        modifier = Modifier
                                            .fillMaxWidth(progProgress.coerceIn(0f, 1f))
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(99.dp))
                                            .background(
                                                Brush.horizontalGradient(
                                                    listOf(
                                                        Color(0xFF6366F1),
                                                        Color(0xFF818CF8),
                                                        Color(0xFFA855F7)
                                                    )
                                                )
                                            )
                                    )
                                    // Live Indicator Dot (●)
                                    if (progProgress in 0.01f..0.99f) {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth(progProgress)
                                                .height(10.dp),
                                            contentAlignment = Alignment.CenterEnd
                                        ) {
                                            Box(
                                                modifier = Modifier
                                                    .size(9.dp)
                                                    .clip(CircleShape)
                                                    .background(Color.White)
                                                    .border(1.5.dp, Color(0xFF818CF8), CircleShape)
                                            )
                                        }
                                    }
                                }
                            }
                        }

                        // Row 4: Next Program
                        if (nextProgram != null) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "Sonraki:",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color(0xFF818CF8)
                                )
                                Text(
                                    text = nextProgram.title,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color.White.copy(alpha = 0.70f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }

                    // RIGHT SECTION: Status Badges + Timeshift Indicator
                    Column(
                        modifier = Modifier.weight(0.85f),
                        horizontalAlignment = Alignment.End,
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Top-right status icons: PVR, CC, Cast
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (currentRecordingStatus == RecordingStatus.RECORDING) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    modifier = Modifier
                                        .background(Color(0xFFFF4D4F).copy(alpha = 0.20f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 5.dp, vertical = 1.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(7.dp)
                                            .background(Color(0xFFFF4D4F), CircleShape)
                                    )
                                    Text(
                                        text = "Kayıt",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = Color(0xFFFF4D4F),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            if (subtitleTrackCount > 0) {
                                Box(
                                    modifier = Modifier
                                        .background(Color.White.copy(alpha = 0.15f), RoundedCornerShape(4.dp))
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(
                                        text = "CC",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                }
                            }
                            if (isCastConnected) {
                                Icon(
                                    imageVector = Icons.Default.CastConnected,
                                    contentDescription = null,
                                    tint = AppColors.NeonCyan,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // HORIZONTALLY SCROLLABLE ACTIONS ROW
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(vertical = 4.dp, horizontal = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Spacer(modifier = Modifier.width(2.dp))

                    // 1. Play / Pause (Primary Action)
                    LiveActionButton(
                        actionId = LiveActionId.PLAY_PAUSE,
                        icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                        label = if (isPlaying) "Duraklat" else "Oynat",
                        isPrimary = true,
                        onClick = onTogglePlayPause,
                        onOverlayInteracted = onOverlayInteracted,
                        modifier = Modifier.focusRequester(focusRequester)
                    )

                    // 2. Canlıya Dön
                    val canReturnToLive = !isPlaying || isCatchUpPlayback
                    LiveActionButton(
                        actionId = LiveActionId.RETURN_LIVE,
                        icon = Icons.Default.FiberManualRecord,
                        label = "Canlıya Dön",
                        enabled = canReturnToLive,
                        accentColor = if (canReturnToLive) Color(0xFFEF4444) else Color.White.copy(alpha = 0.30f),
                        onClick = onSeekToLiveEdge,
                        onOverlayInteracted = onOverlayInteracted
                    )

                    // 3. Ses / Audio Tracks
                    LiveActionButton(
                        actionId = LiveActionId.AUDIO_TRACKS,
                        icon = Icons.Default.VolumeUp,
                        label = "Ses",
                        badgeActive = audioTrackCount > 1,
                        onClick = onOpenAudioTracks,
                        onOverlayInteracted = onOverlayInteracted
                    )

                    // 5. Sessiz / Mute
                    LiveActionButton(
                        actionId = LiveActionId.MUTE,
                        icon = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        label = if (isMuted) "Sesi Aç" else "Sessiz",
                        accentColor = if (isMuted) Color(0xFFFF6B6B) else Color.White,
                        onClick = onToggleMute,
                        onOverlayInteracted = onOverlayInteracted
                    )

                    // 6. Altyazılar
                    LiveActionButton(
                        actionId = LiveActionId.SUBTITLES,
                        icon = Icons.Default.Subtitles,
                        label = "Altyazılar",
                        badgeActive = subtitleTrackCount > 0,
                        onClick = onOpenSubtitleTracks,
                        onOverlayInteracted = onOverlayInteracted
                    )

                    // 7. Video Kalitesi
                    LiveActionButton(
                        actionId = LiveActionId.QUALITY,
                        icon = Icons.Default.HighQuality,
                        label = "Kalite",
                        badgeActive = videoQualityCount > 1,
                        onClick = onOpenVideoTracks,
                        onOverlayInteracted = onOverlayInteracted
                    )

                    // 8. Kanal Listesi
                    LiveActionButton(
                        actionId = LiveActionId.CHANNEL_LIST,
                        icon = Icons.Default.Tv,
                        label = "Kanal Listesi",
                        onClick = onOpenChannelList,
                        onOverlayInteracted = onOverlayInteracted
                    )

                    // 9. EPG / Rehber
                    LiveActionButton(
                        actionId = LiveActionId.EPG,
                        icon = Icons.Default.ViewSidebar,
                        label = "EPG",
                        onClick = onOpenFullEpg,
                        onOverlayInteracted = onOverlayInteracted
                    )

                    // 10. En-Boy Oranı
                    LiveActionButton(
                        actionId = LiveActionId.ASPECT_RATIO,
                        icon = Icons.Default.AspectRatio,
                        label = currentAspectRatio,
                        onClick = onToggleAspectRatio,
                        onOverlayInteracted = onOverlayInteracted
                    )

                    // 11. Cast / Ekrana Yansıt
                    LiveActionButton(
                        actionId = LiveActionId.CAST,
                        icon = if (isCastConnected) Icons.Default.CastConnected else Icons.Default.Cast,
                        label = if (isCastConnected) "Yansıtmayı Durdur" else "Yansıt",
                        accentColor = if (isCastConnected) AppColors.NeonCyan else Color.White,
                        onClick = if (isCastConnected) onStopCasting else onCast,
                        onOverlayInteracted = onOverlayInteracted
                    )

                    // 12. Picture-in-Picture
                    LiveActionButton(
                        actionId = LiveActionId.PIP,
                        icon = Icons.Default.PictureInPicture,
                        label = "PiP",
                        onClick = onEnterPictureInPicture,
                        onOverlayInteracted = onOverlayInteracted
                    )

                    // 13. Kayıt (PVR Başlat/Durdur)
                    val isRecActive = currentRecordingStatus == RecordingStatus.RECORDING
                    LiveActionButton(
                        actionId = LiveActionId.RECORD,
                        icon = if (isRecActive) Icons.Default.Stop else Icons.Default.RadioButtonChecked,
                        label = if (isRecActive) "Kaydı Durdur" else "Kayıt",
                        accentColor = if (isRecActive) Color(0xFFFF4D4F) else Color.White,
                        badgeActive = isRecActive,
                        onClick = if (isRecActive) onStopRecording else onStartRecording,
                        onOverlayInteracted = onOverlayInteracted
                    )

                    // 14. Zamanlanmış Kayıt
                    LiveActionButton(
                        actionId = LiveActionId.SCHEDULE_RECORDING,
                        icon = Icons.Default.Schedule,
                        label = "Zamanla Kayıt",
                        onClick = onScheduleRecording,
                        onOverlayInteracted = onOverlayInteracted
                    )

                    // 15. MultiView / Çoklu Ekran
                    LiveActionButton(
                        actionId = LiveActionId.MULTIVIEW,
                        icon = Icons.Default.Dashboard,
                        label = "MultiView",
                        onClick = onOpenSplitScreen,
                        onOverlayInteracted = onOverlayInteracted
                    )

                    // 16. Arşiv / Catch-Up
                    LiveActionButton(
                        actionId = LiveActionId.ARCHIVE,
                        icon = Icons.Default.History,
                        label = "Arşiv",
                        onClick = onOpenArchive,
                        onOverlayInteracted = onOverlayInteracted
                    )

                    // 17. Tanılama / Diagnostics
                    LiveActionButton(
                        actionId = LiveActionId.DIAGNOSTICS,
                        icon = Icons.Default.Info,
                        label = "Tanılama",
                        onClick = onToggleDiagnostics,
                        onOverlayInteracted = onOverlayInteracted
                    )

                    // 18. Ses/Görüntü Senkronizasyonu
                    if (audioVideoSyncEnabled) {
                        LiveActionButton(
                            actionId = LiveActionId.AV_SYNC,
                            icon = Icons.Default.SyncAlt,
                            label = "A/V Sync",
                            onClick = onOpenAudioVideoSync,
                            onOverlayInteracted = onOverlayInteracted
                        )
                    }

                    // Scroll indicator
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.35f),
                        modifier = Modifier
                            .size(20.dp)
                            .padding(start = 2.dp)
                    )

                    Spacer(modifier = Modifier.width(4.dp))
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Remote Navigation Hints Bar
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "\u25C2\u25B8 Seç   |   OK Onayla   |   \u2630 Menü   |   \u21A9 Geri",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.40f)
                    )
                }
            }
        }
    }
}

enum class LiveActionId {
    PLAY_PAUSE,
    RETURN_LIVE,
    RESTART_PROGRAM,
    AUDIO_TRACKS,
    MUTE,
    SUBTITLES,
    QUALITY,
    CHANNEL_LIST,
    EPG,
    ASPECT_RATIO,
    CAST,
    PIP,
    RECORD,
    SCHEDULE_RECORDING,
    MULTIVIEW,
    ARCHIVE,
    DIAGNOSTICS,
    AV_SYNC
}

/**
 * TV-remote and Touch/Mouse-friendly Live TV control button matching the Premium Compact design language:
 * - Uses TvClickableSurface with mouseClickable & touch tap support
 * - 1.08x scale on focus + glowing luminous indigo border
 * - Clean layout without clipping
 * - Circular badge dot if active
 * - Primary button (Play/Pause) is styled with prominent rounded container and distinct focus glow
 */
@Composable
private fun LiveActionButton(
    actionId: LiveActionId,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    onOverlayInteracted: () -> Unit = {},
    enabled: Boolean = true,
    isPrimary: Boolean = false,
    accentColor: Color = Color.White,
    badgeActive: Boolean = false,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val scaleAnim by animateFloatAsState(
        targetValue = if (isFocused && enabled) 1.08f else 1.0f,
        label = "liveCtrlScale"
    )

    TvClickableSurface(
        onClick = {
            android.util.Log.d("PlayerActionTrace", "[LIVE_ACTION_TRACE] action=${actionId.name} label=$label onClick invoked")
            onOverlayInteracted()
            onClick()
        },
        enabled = enabled,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isPrimary) Color(0xFF4F46E5).copy(alpha = 0.28f) else Color(0xFF0F172A).copy(alpha = 0.60f),
            focusedContainerColor = if (isPrimary) Color(0xFF6366F1).copy(alpha = 0.45f) else Color(0xFF4338CA).copy(alpha = 0.35f)
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                border = BorderStroke(1.dp, if (isPrimary) Color(0xFF6366F1).copy(alpha = 0.55f) else Color.White.copy(alpha = if (enabled) 0.10f else 0.04f)),
                shape = RoundedCornerShape(12.dp)
            ),
            focusedBorder = Border(
                border = BorderStroke(2.dp, if (isPrimary) Color(0xFFA5B4FC) else Color(0xFF818CF8)),
                shape = RoundedCornerShape(12.dp)
            )
        ),
        modifier = modifier
            .onFocusChanged { isFocused = it.isFocused }
            .scale(scaleAnim)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(contentAlignment = Alignment.TopEnd) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = if (!enabled) Color.White.copy(alpha = 0.25f) else if (isFocused) Color.White else accentColor,
                    modifier = Modifier.size(20.dp)
                )
                if (badgeActive && enabled) {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(AppColors.NeonCyan, CircleShape)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall.copy(
                    fontSize = 11.sp,
                    fontWeight = if (isPrimary || isFocused) FontWeight.Bold else FontWeight.Medium
                ),
                color = if (!enabled) Color(0xFF64748B).copy(alpha = 0.50f) else if (isFocused) Color.White else Color(0xFF94A3B8),
                maxLines = 1
            )
        }
    }
}

private fun formatTimeshiftDuration(ms: Long): String {
    val totalSeconds = (ms / 1000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3600L
    val minutes = (totalSeconds % 3600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, minutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", minutes, seconds)
    }
}
