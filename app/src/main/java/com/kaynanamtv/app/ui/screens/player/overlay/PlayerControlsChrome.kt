package com.kaynanamtv.app.ui.screens.player.overlay

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.MicOff
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MovieFilter
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PictureInPicture
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Replay
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Snooze
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Subtitles
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.ViewSidebar
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.draw.scale
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.kaynanamtv.app.R
import com.kaynanamtv.app.ui.design.AppColors
import com.kaynanamtv.app.device.rememberIsTelevisionDevice
import com.kaynanamtv.app.ui.components.rememberCrossfadeImageModel
import com.kaynanamtv.app.ui.model.isArchivePlayable
import com.kaynanamtv.app.ui.screens.player.NumericChannelInputState
import com.kaynanamtv.app.ui.screens.player.PlayerTimeshiftUiState
import com.kaynanamtv.app.ui.screens.player.SeekPreviewState
import com.kaynanamtv.app.ui.screens.player.SleepTimerUiState
import com.kaynanamtv.app.ui.time.LocalAppTimeFormat
import com.kaynanamtv.app.ui.time.createTimeFormat
import com.kaynanamtv.app.ui.theme.ErrorColor
import com.kaynanamtv.app.ui.theme.Primary
import com.kaynanamtv.domain.model.Channel
import com.kaynanamtv.domain.model.Program
import com.kaynanamtv.domain.model.RecordingStatus
import coil3.compose.AsyncImage
import kotlinx.coroutines.delay
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.util.Date
import java.util.Locale
import com.kaynanamtv.app.ui.interaction.TvClickableSurface
import com.kaynanamtv.app.ui.interaction.TvButton
import com.kaynanamtv.app.ui.interaction.TvIconButton

private data class PlayerActionSpec(
    val label: String,
    val onClick: () -> Unit,
    val icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    val accentColor: Color = Color.White
)

@Composable
fun PlayerControlsOverlay(
    visible: Boolean,
    title: String,
    contentType: String,
    isCatchUpPlayback: Boolean = false,
    isPlaying: Boolean,
    currentProgram: Program?,
    currentChannel: Channel?,
    currentChannelName: String?,
    displayChannelNumber: Int,
    currentPosition: Long,
    duration: Long,
    aspectRatioLabel: String,
    subtitleTrackCount: Int,
    liveTranslationAvailable: Boolean = false,
    audioTrackCount: Int,
    videoQualityCount: Int,
    currentRecordingStatus: RecordingStatus?,
    isMuted: Boolean,
    playbackSpeed: Float = 1f,
    mediaTitle: String?,
    sleepTimerUiState: SleepTimerUiState = SleepTimerUiState(),
    timeshiftUiState: PlayerTimeshiftUiState = PlayerTimeshiftUiState(),
    playButtonFocusRequester: FocusRequester,
    quickActionsFocusRequester: FocusRequester = FocusRequester(),
    onClose: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    onRestartProgram: () -> Unit,
    onOpenArchive: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onScheduleRecording: () -> Unit,
    onScheduleDailyRecording: () -> Unit = {},
    onScheduleWeeklyRecording: () -> Unit = {},
    onToggleAspectRatio: () -> Unit,
    onOpenSubtitleTracks: () -> Unit,
    onOpenAudioTracks: () -> Unit,
    onOpenVideoTracks: () -> Unit,
    onOpenPlaybackSpeed: () -> Unit = {},
    onOpenStopPlaybackTimer: () -> Unit = {},
    onOpenIdleStandbyTimer: () -> Unit = {},
    onOpenAudioVideoSync: () -> Unit = {},
    audioVideoSyncEnabled: Boolean = false,
    showEpisodesAction: Boolean = false,
    onOpenEpisodes: () -> Unit = {},
    onOpenSplitScreen: () -> Unit,
    onEnterPictureInPicture: () -> Unit = {},
    onToggleMute: () -> Unit,
    isCastConnected: Boolean = false,
    onCast: () -> Unit = {},
    onStopCasting: () -> Unit = {},
    onOpenChannelList: () -> Unit = {},
    onSeekToLiveEdge: () -> Unit = {},
    onSeekToPosition: (Long) -> Unit = {},
    onSetScrubbingMode: (Boolean) -> Unit = {},
    showExternalPlayerAction: Boolean = false,
    onOpenExternalPlayer: () -> Unit = {},
    seekPreview: SeekPreviewState = SeekPreviewState(),
    onSeekPreviewPositionChanged: (Long?) -> Unit = {},
    clockLabelOverride: String? = null,
    onUserInteraction: () -> Unit = {},
    onOpenGuide: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    if (event.nativeKeyEvent.action != android.view.KeyEvent.ACTION_DOWN) return@onPreviewKeyEvent false
                    when (event.nativeKeyEvent.keyCode) {
                        android.view.KeyEvent.KEYCODE_DPAD_UP,
                        android.view.KeyEvent.KEYCODE_DPAD_DOWN,
                        android.view.KeyEvent.KEYCODE_DPAD_LEFT,
                        android.view.KeyEvent.KEYCODE_DPAD_RIGHT,
                        android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                        android.view.KeyEvent.KEYCODE_ENTER,
                        android.view.KeyEvent.KEYCODE_NUMPAD_ENTER -> onUserInteraction()
                    }
                    false
                }
        ) {
            PlayerTopBar(
                title = title,
                contentType = contentType,
                clockLabelOverride = clockLabelOverride,
                onClose = onClose,
                modifier = Modifier.align(Alignment.TopCenter)
            )

            PlayerBottomBar(
                title = title,
                contentType = contentType,
                isCatchUpPlayback = isCatchUpPlayback,
                isPlaying = isPlaying,
                currentProgram = currentProgram,
                currentChannel = currentChannel,
                currentChannelName = currentChannelName,
                displayChannelNumber = displayChannelNumber,
                currentPosition = currentPosition,
                duration = duration,
                aspectRatioLabel = aspectRatioLabel,
                subtitleTrackCount = subtitleTrackCount,
                liveTranslationAvailable = liveTranslationAvailable,
                audioTrackCount = audioTrackCount,
                videoQualityCount = videoQualityCount,
                currentRecordingStatus = currentRecordingStatus,
                isMuted = isMuted,
                playbackSpeed = playbackSpeed,
                mediaTitle = mediaTitle,
                sleepTimerUiState = sleepTimerUiState,
                timeshiftUiState = timeshiftUiState,
                playButtonFocusRequester = playButtonFocusRequester,
                quickActionsFocusRequester = quickActionsFocusRequester,
                modifier = Modifier.align(Alignment.BottomCenter),
                onRestartProgram = onRestartProgram,
                onOpenArchive = onOpenArchive,
                onStartRecording = onStartRecording,
                onStopRecording = onStopRecording,
                onScheduleRecording = onScheduleRecording,
                onScheduleDailyRecording = onScheduleDailyRecording,
                onScheduleWeeklyRecording = onScheduleWeeklyRecording,
                onToggleAspectRatio = onToggleAspectRatio,
                onOpenSubtitleTracks = onOpenSubtitleTracks,
                onOpenAudioTracks = onOpenAudioTracks,
                onOpenVideoTracks = onOpenVideoTracks,
                onOpenPlaybackSpeed = onOpenPlaybackSpeed,
                onOpenStopPlaybackTimer = onOpenStopPlaybackTimer,
                onOpenIdleStandbyTimer = onOpenIdleStandbyTimer,
                onOpenAudioVideoSync = onOpenAudioVideoSync,
                audioVideoSyncEnabled = audioVideoSyncEnabled,
                showEpisodesAction = showEpisodesAction,
                onOpenEpisodes = onOpenEpisodes,
                onOpenSplitScreen = onOpenSplitScreen,
                onEnterPictureInPicture = onEnterPictureInPicture,
                onToggleMute = onToggleMute,
                isCastConnected = isCastConnected,
                onCast = onCast,
                onStopCasting = onStopCasting,
                onOpenChannelList = onOpenChannelList,
                onSeekToLiveEdge = onSeekToLiveEdge,
                onTogglePlayPause = onTogglePlayPause,
                onSeekBackward = onSeekBackward,
                onSeekForward = onSeekForward,
                onSeekToPosition = onSeekToPosition,
                onSetScrubbingMode = onSetScrubbingMode,
                seekPreview = seekPreview,
                onSeekPreviewPositionChanged = onSeekPreviewPositionChanged,
                showExternalPlayerAction = showExternalPlayerAction,
                onOpenExternalPlayer = onOpenExternalPlayer,
                onOpenGuide = onOpenGuide
            )
        }
    }

    com.kaynanamtv.app.ui.components.PlaybackFadeOverlay(isPlaying = isPlaying)
}

@Composable
fun PlayerZapOverlay(
    visible: Boolean,
    displayChannelNumber: Int,
    channelName: String?,
    programTitle: String?,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInHorizontally(),
        exit = fadeOut() + slideOutHorizontally(),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .padding(32.dp)
                .background(
                    brush = Brush.horizontalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.84f), Color.Transparent)
                    ),
                    shape = RoundedCornerShape(12.dp)
                )
                .padding(18.dp)
                .widthIn(min = 320.dp, max = 460.dp)
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (displayChannelNumber > 0) {
                    Text(
                        text = displayChannelNumber.toString(),
                        style = MaterialTheme.typography.headlineMedium,
                        color = Primary,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(16.dp))
                }
                Column {
                    Text(
                        text = channelName.orEmpty(),
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (!programTitle.isNullOrBlank()) {
                        Text(
                            text = programTitle,
                            style = MaterialTheme.typography.bodyMedium,
                            color = Color.White.copy(alpha = 0.78f),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun PlayerNumericInputOverlay(
    state: NumericChannelInputState?,
    visible: Boolean,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible && state != null,
        enter = fadeIn() + slideInVertically(initialOffsetY = { -it / 2 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { -it / 2 }),
        modifier = modifier
    ) {
        val inputState = state ?: return@AnimatedVisibility
        Box(
            modifier = Modifier
                .background(Color.Black.copy(alpha = 0.82f), RoundedCornerShape(14.dp))
                .padding(horizontal = 22.dp, vertical = 12.dp)
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = inputState.input,
                    style = MaterialTheme.typography.headlineMedium,
                    color = if (inputState.invalid) ErrorColor else Primary,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = when {
                        inputState.invalid -> stringResource(R.string.player_channel_not_found)
                        !inputState.matchedChannelName.isNullOrBlank() -> inputState.matchedChannelName
                        else -> stringResource(R.string.player_type_channel_number)
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White.copy(alpha = 0.85f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun PlayerAspectRatioToast(
    aspectRatioLabel: String,
    controlsVisible: Boolean,
    modifier: Modifier = Modifier
) {
    var show by remember { mutableStateOf(false) }
    LaunchedEffect(aspectRatioLabel) {
        show = true
        delay(2000)
        show = false
    }

    AnimatedVisibility(
        visible = show && !controlsVisible,
        enter = fadeIn() + slideInVertically(initialOffsetY = { -it }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { -it }),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .background(Primary.copy(alpha = 0.9f), RoundedCornerShape(24.dp))
                .padding(horizontal = 24.dp, vertical = 12.dp)
        ) {
            Text(
                text = stringResource(R.string.player_aspect_ratio_label, aspectRatioLabel),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                fontWeight = FontWeight.Bold
            )
        }
    }
}

@Composable
fun PlayerResolutionBadge(
    visible: Boolean,
    resolutionLabel: String,
    modifier: Modifier = Modifier
) {
    if (!visible) return

    Box(
        modifier = modifier
            .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(6.dp))
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = resolutionLabel,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
private fun PlayerTopBar(
    title: String,
    contentType: String,
    clockLabelOverride: String?,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val isTelevisionDevice = rememberIsTelevisionDevice()
    val appTimeFormat = LocalAppTimeFormat.current
    val timeFormat = remember(appTimeFormat) { appTimeFormat.createTimeFormat() }
    val topBarHeight = when {
        screenWidth < 700.dp -> 100.dp
        !isTelevisionDevice && screenWidth < 1280.dp -> 116.dp
        else -> 132.dp
    }
    val horizontalPadding = when {
        screenWidth < 700.dp -> 18.dp
        !isTelevisionDevice && screenWidth < 1280.dp -> 24.dp
        else -> 32.dp
    }
    val verticalPadding = when {
        screenWidth < 700.dp -> 16.dp
        !isTelevisionDevice && screenWidth < 1280.dp -> 20.dp
        else -> 24.dp
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(topBarHeight)
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)
                )
            )
            .padding(horizontal = horizontalPadding, vertical = verticalPadding)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                PlayerMetaPill(
                    text = when (contentType) {
                        "LIVE" -> stringResource(R.string.nav_live_tv)
                        "MOVIE" -> stringResource(R.string.player_type_movie)
                        else -> stringResource(R.string.player_type_series)
                    }
                )
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleLarge,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                if (contentType != "LIVE") {
                    Text(
                        text = if (contentType == "MOVIE") {
                            stringResource(R.string.player_type_movie)
                        } else {
                            stringResource(R.string.player_type_series)
                        },
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.6f)
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                val currentTime = remember(clockLabelOverride, timeFormat) {
                    mutableStateOf(
                        clockLabelOverride ?: timeFormat.format(Date())
                    )
                }
                LaunchedEffect(clockLabelOverride, timeFormat) {
                    if (clockLabelOverride == null) {
                        while (true) {
                            currentTime.value = timeFormat.format(Date())
                            delay(10_000)
                        }
                    } else {
                        currentTime.value = clockLabelOverride
                    }
                }
                Text(
                    text = currentTime.value,
                    style = MaterialTheme.typography.titleMedium,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier.padding(end = 16.dp)
                )

                TvClickableSurface(
                    onClick = onClose,
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(999.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.12f),
                        focusedContainerColor = Primary.copy(alpha = 0.9f)
                    )
                ) {
                    Text(
                        text = stringResource(R.string.player_close),
                        color = Color.White,
                        modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerBottomBar(
    title: String,
    contentType: String,
    isCatchUpPlayback: Boolean = false,
    isPlaying: Boolean,
    currentProgram: Program?,
    currentChannel: Channel?,
    currentChannelName: String?,
    displayChannelNumber: Int,
    currentPosition: Long,
    duration: Long,
    aspectRatioLabel: String,
    subtitleTrackCount: Int,
    liveTranslationAvailable: Boolean = false,
    audioTrackCount: Int,
    videoQualityCount: Int,
    currentRecordingStatus: RecordingStatus?,
    isMuted: Boolean,
    playbackSpeed: Float,
    mediaTitle: String?,
    sleepTimerUiState: SleepTimerUiState,
    timeshiftUiState: PlayerTimeshiftUiState,
    playButtonFocusRequester: FocusRequester,
    quickActionsFocusRequester: FocusRequester,
    onRestartProgram: () -> Unit,
    onOpenArchive: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onScheduleRecording: () -> Unit,
    onScheduleDailyRecording: () -> Unit,
    onScheduleWeeklyRecording: () -> Unit,
    onToggleAspectRatio: () -> Unit,
    onOpenSubtitleTracks: () -> Unit,
    onOpenAudioTracks: () -> Unit,
    onOpenVideoTracks: () -> Unit,
    onOpenPlaybackSpeed: () -> Unit,
    onOpenStopPlaybackTimer: () -> Unit,
    onOpenIdleStandbyTimer: () -> Unit,
    onOpenAudioVideoSync: () -> Unit,
    audioVideoSyncEnabled: Boolean,
    showEpisodesAction: Boolean,
    onOpenEpisodes: () -> Unit,
    onOpenSplitScreen: () -> Unit,
    onEnterPictureInPicture: () -> Unit,
    onToggleMute: () -> Unit,
    isCastConnected: Boolean,
    onCast: () -> Unit,
    onStopCasting: () -> Unit,
    onOpenChannelList: () -> Unit,
    onSeekToLiveEdge: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekToPosition: (Long) -> Unit,
    onSetScrubbingMode: (Boolean) -> Unit,
    seekPreview: SeekPreviewState,
    onSeekPreviewPositionChanged: (Long?) -> Unit,
    showExternalPlayerAction: Boolean,
    onOpenExternalPlayer: () -> Unit,
    onOpenGuide: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isVod = contentType != "LIVE" || isCatchUpPlayback
    val bottomBarWidthFraction = if (isVod) 0.78f else 1f
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.84f))
                )
            )
            .padding(
                horizontal = if (isVod) 14.dp else 32.dp,
                vertical = if (isVod) 10.dp else 24.dp
            )
    ) {
        val borderBrush = Brush.linearGradient(
            colors = listOf(
                Primary.copy(alpha = 0.55f),
                AppColors.NeonCyan.copy(alpha = 0.55f),
                Primary.copy(alpha = 0.12f)
            )
        )
        Surface(
            modifier = if (isVod) {
                Modifier
                    .fillMaxWidth(bottomBarWidthFraction)
                    .widthIn(max = 980.dp)
                    .align(Alignment.Center)
            } else {
                Modifier.fillMaxWidth()
            },
            shape = RoundedCornerShape(if (isVod) 20.dp else 28.dp),
            colors = SurfaceDefaults.colors(containerColor = Color(0xFF060B12).copy(alpha = 0.86f)),
            border = Border(
                border = BorderStroke(1.2.dp, borderBrush),
                shape = RoundedCornerShape(if (isVod) 20.dp else 28.dp)
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Primary.copy(alpha = 0.10f),
                                Color.Transparent
                            )
                        )
                    )
                    .padding(
                        horizontal = if (isVod) 14.dp else 24.dp,
                        vertical = if (isVod) 12.dp else 22.dp
                    )
            ) {
                if (contentType == "LIVE") {
                    PlayerLiveInfo(
                        currentProgram = currentProgram,
                        currentChannel = currentChannel,
                        currentChannelName = currentChannelName,
                        displayChannelNumber = displayChannelNumber,
                        aspectRatioLabel = aspectRatioLabel,
                        subtitleTrackCount = subtitleTrackCount,
                        liveTranslationAvailable = liveTranslationAvailable,
                        audioTrackCount = audioTrackCount,
                        videoQualityCount = videoQualityCount,
                        currentRecordingStatus = currentRecordingStatus,
                        isMuted = isMuted,
                        mediaTitle = mediaTitle,
                        sleepTimerUiState = sleepTimerUiState,
                        timeshiftUiState = timeshiftUiState,
                        playButtonFocusRequester = playButtonFocusRequester,
                        quickActionsFocusRequester = quickActionsFocusRequester,
                        onRestartProgram = onRestartProgram,
                        onOpenArchive = onOpenArchive,
                        onStartRecording = onStartRecording,
                        onStopRecording = onStopRecording,
                        onScheduleRecording = onScheduleRecording,
                        onScheduleDailyRecording = onScheduleDailyRecording,
                        onScheduleWeeklyRecording = onScheduleWeeklyRecording,
                        onToggleAspectRatio = onToggleAspectRatio,
                        onOpenSubtitleTracks = onOpenSubtitleTracks,
                        onOpenAudioTracks = onOpenAudioTracks,
                        onOpenVideoTracks = onOpenVideoTracks,
                        onOpenStopPlaybackTimer = onOpenStopPlaybackTimer,
                        onOpenIdleStandbyTimer = onOpenIdleStandbyTimer,
                        onOpenAudioVideoSync = onOpenAudioVideoSync,
                        audioVideoSyncEnabled = audioVideoSyncEnabled,
                        onOpenSplitScreen = onOpenSplitScreen,
                        onEnterPictureInPicture = onEnterPictureInPicture,
                        onToggleMute = onToggleMute,
                        isCastConnected = isCastConnected,
                        onCast = onCast,
                        onStopCasting = onStopCasting,
                        onOpenChannelList = onOpenChannelList,
                        isPlaying = isPlaying,
                        onTogglePlayPause = onTogglePlayPause,
                        onSeekBackward = onSeekBackward,
                        onSeekForward = onSeekForward,
                        onSeekToLiveEdge = onSeekToLiveEdge,
                        onSeekToPosition = onSeekToPosition,
                        onSetScrubbingMode = onSetScrubbingMode,
                        showExternalPlayerAction = showExternalPlayerAction,
                        onOpenExternalPlayer = onOpenExternalPlayer,
                        isCatchUpPlayback = isCatchUpPlayback,
                        onOpenGuide = onOpenGuide
                    )
                } else {
                    PlayerVodInfo(
                        title = title,
                        contentType = contentType,
                        isPlaying = isPlaying,
                        currentPosition = currentPosition,
                        duration = duration,
                        aspectRatioLabel = aspectRatioLabel,
                        subtitleTrackCount = subtitleTrackCount,
                        audioTrackCount = audioTrackCount,
                        videoQualityCount = videoQualityCount,
                        isMuted = isMuted,
                        playbackSpeed = playbackSpeed,
                        sleepTimerUiState = sleepTimerUiState,
                        playButtonFocusRequester = playButtonFocusRequester,
                        quickActionsFocusRequester = quickActionsFocusRequester,
                        onSeekToPosition = onSeekToPosition,
                        onSetScrubbingMode = onSetScrubbingMode,
                        onToggleAspectRatio = onToggleAspectRatio,
                        onOpenSubtitleTracks = onOpenSubtitleTracks,
                        onOpenAudioTracks = onOpenAudioTracks,
                        onOpenVideoTracks = onOpenVideoTracks,
                        onOpenPlaybackSpeed = onOpenPlaybackSpeed,
                        onOpenStopPlaybackTimer = onOpenStopPlaybackTimer,
                        onOpenIdleStandbyTimer = onOpenIdleStandbyTimer,
                        onOpenAudioVideoSync = onOpenAudioVideoSync,
                        audioVideoSyncEnabled = audioVideoSyncEnabled,
                        showEpisodesAction = showEpisodesAction,
                        onOpenEpisodes = onOpenEpisodes,
                        onEnterPictureInPicture = onEnterPictureInPicture,
                        onToggleMute = onToggleMute,
                        isCastConnected = isCastConnected,
                        onCast = onCast,
                        onStopCasting = onStopCasting,
                        onTogglePlayPause = onTogglePlayPause,
                        onSeekBackward = onSeekBackward,
                        onSeekForward = onSeekForward,
                        seekPreview = seekPreview,
                        onSeekPreviewPositionChanged = onSeekPreviewPositionChanged,
                        showExternalPlayerAction = showExternalPlayerAction,
                        onOpenExternalPlayer = onOpenExternalPlayer
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerLiveInfo(
    currentProgram: Program?,
    currentChannel: Channel?,
    currentChannelName: String?,
    displayChannelNumber: Int,
    aspectRatioLabel: String,
    subtitleTrackCount: Int,
    liveTranslationAvailable: Boolean,
    audioTrackCount: Int,
    videoQualityCount: Int,
    currentRecordingStatus: RecordingStatus?,
    isMuted: Boolean,
    mediaTitle: String?,
    sleepTimerUiState: SleepTimerUiState,
    timeshiftUiState: PlayerTimeshiftUiState,
    playButtonFocusRequester: FocusRequester,
    quickActionsFocusRequester: FocusRequester,
    onRestartProgram: () -> Unit,
    onOpenArchive: () -> Unit,
    onStartRecording: () -> Unit,
    onStopRecording: () -> Unit,
    onScheduleRecording: () -> Unit,
    onScheduleDailyRecording: () -> Unit,
    onScheduleWeeklyRecording: () -> Unit,
    onToggleAspectRatio: () -> Unit,
    onOpenSubtitleTracks: () -> Unit,
    onOpenAudioTracks: () -> Unit,
    onOpenVideoTracks: () -> Unit,
    onOpenStopPlaybackTimer: () -> Unit,
    onOpenIdleStandbyTimer: () -> Unit,
    onOpenAudioVideoSync: () -> Unit,
    audioVideoSyncEnabled: Boolean,
    onOpenSplitScreen: () -> Unit,
    onEnterPictureInPicture: () -> Unit,
    onToggleMute: () -> Unit,
    isCastConnected: Boolean,
    onCast: () -> Unit,
    onStopCasting: () -> Unit,
    onOpenChannelList: () -> Unit,
    isPlaying: Boolean,
    onTogglePlayPause: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekToLiveEdge: () -> Unit,
    onSeekToPosition: (Long) -> Unit,
    onSetScrubbingMode: (Boolean) -> Unit,
    showExternalPlayerAction: Boolean,
    onOpenExternalPlayer: () -> Unit,
    isCatchUpPlayback: Boolean = false,
    onOpenGuide: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val showTimeshiftControls = timeshiftUiState.available && !isCastConnected
    val appTimeFormat = LocalAppTimeFormat.current
    val timeFormat = remember(appTimeFormat) { appTimeFormat.createTimeFormat() }

    // ── Live state: LIVE_EDGE | TIMESHIFT | ARCHIVE ────────────────
    val liveState = when {
        isCatchUpPlayback -> TvLiveState.ARCHIVE
        showTimeshiftControls && timeshiftUiState.bufferedBehindLiveMs >= 2_000L -> TvLiveState.TIMESHIFT
        else -> TvLiveState.LIVE_EDGE
    }

    // Pulsing dot for LIVE edge badge
    var liveDotVisible by remember { mutableStateOf(true) }
    LaunchedEffect(liveState) {
        if (liveState == TvLiveState.LIVE_EDGE) {
            while (true) { delay(700L); liveDotVisible = !liveDotVisible }
        } else {
            liveDotVisible = true
        }
    }

    Spacer(modifier = Modifier.height(8.dp))

    // ── Row 1: Live state badge ────────────────────────────────────
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        when (liveState) {
            TvLiveState.LIVE_EDGE -> {
                Box(
                    modifier = Modifier
                        .size(8.dp)
                        .background(
                            color = Color.Red.copy(alpha = if (liveDotVisible) 0.95f else 0.22f),
                            shape = RoundedCornerShape(99.dp)
                        )
                )
                Text(
                    text = "CANLI",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.Red
                )
            }
            TvLiveState.TIMESHIFT -> {
                val offsetMin = (timeshiftUiState.bufferedBehindLiveMs / 60_000L).coerceAtLeast(1L)
                Text(
                    text = "\u21B6  -${offsetMin} dk",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFFFFB347)
                )
            }
            TvLiveState.ARCHIVE -> {
                Text(
                    text = "AR\u015E\u0130V",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = AppColors.NeonCyan
                )
            }
        }
    }

    Spacer(modifier = Modifier.height(10.dp))

    // ── Row 2: Program info + progress bar ────────────────────────
    val progStart = currentProgram?.startTime ?: 0L
    val progEnd = currentProgram?.endTime ?: 0L
    val nowMs = System.currentTimeMillis()

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = currentProgram?.title
                    ?: currentChannelName?.let {
                        stringResource(R.string.channel_number_name_format, displayChannelNumber, it)
                    }.orEmpty(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (progStart > 0L && progEnd > 0L) {
                val remainMs = (progEnd - nowMs).coerceAtLeast(0L)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${timeFormat.format(java.util.Date(progStart))} \u2013 ${timeFormat.format(java.util.Date(progEnd))}",
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.58f)
                    )
                    if (remainMs > 60_000L) {
                        Text(
                            text = "(${formatTimeshiftDuration(remainMs)} kald\u0131)",
                            style = MaterialTheme.typography.labelSmall,
                            color = Primary.copy(alpha = 0.85f),
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }
        }
        if (progStart > 0L && progEnd > 0L) {
            Spacer(modifier = Modifier.height(6.dp))
            val progProgress = ((nowMs - progStart).toFloat() / (progEnd - progStart)).coerceIn(0f, 1f)
            LinearProgressIndicator(
                progress = { progProgress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(3.dp)
                    .clip(RoundedCornerShape(99.dp)),
                color = Primary,
                trackColor = Color.White.copy(alpha = 0.12f)
            )
        }
    }

    // ── Row 3: Timeshift scrubber (when available) ─────────────────
    if (showTimeshiftControls) {
        Spacer(modifier = Modifier.height(10.dp))
        val bufferDepthMs = timeshiftUiState.bufferDepthMs.coerceAtLeast(1L)
        val bufferedBehindLive = timeshiftUiState.bufferedBehindLiveMs
        val oldestWallMs = timeshiftUiState.engineState.bufferStartMs
        val oldestLabel = when {
            oldestWallMs > 0L -> timeFormat.format(java.util.Date(oldestWallMs))
            bufferDepthMs > 1_000L -> "-${formatTimeshiftDuration(bufferDepthMs)}"
            else -> ""
        }
        var sliderValue by remember(bufferedBehindLive, bufferDepthMs) {
            mutableStateOf(1f - (bufferedBehindLive.toFloat() / bufferDepthMs.toFloat()).coerceIn(0f, 1f))
        }
        var isScrubbing by remember { mutableStateOf(false) }
        val latestSeekCallback by rememberUpdatedState(onSeekToPosition)
        val latestScrubbingCallback by rememberUpdatedState(onSetScrubbingMode)
        LaunchedEffect(bufferedBehindLive, bufferDepthMs, isScrubbing) {
            if (!isScrubbing) {
                sliderValue = 1f - (bufferedBehindLive.toFloat() / bufferDepthMs.toFloat()).coerceIn(0f, 1f)
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = oldestLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.55f)
                )
                Text(
                    text = if (bufferedBehindLive > 1_000L)
                        "-${formatTimeshiftDuration(bufferedBehindLive)}"
                    else stringResource(R.string.player_live_ready),
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.SemiBold,
                    color = if (bufferedBehindLive <= 1_000L) Primary else Color(0xFFFFB347)
                )
                Text(
                    text = stringResource(R.string.player_live_now),
                    style = MaterialTheme.typography.labelSmall,
                    color = Primary,
                    fontWeight = FontWeight.Bold
                )
            }
            Slider(
                value = sliderValue,
                onValueChange = { newValue ->
                    val clamped = newValue.coerceIn(0f, 1f)
                    if (!isScrubbing) { isScrubbing = true; latestScrubbingCallback(true) }
                    sliderValue = clamped
                },
                onValueChangeFinished = {
                    latestSeekCallback(((1f - sliderValue) * bufferDepthMs.toFloat()).toLong())
                    if (isScrubbing) { latestScrubbingCallback(false); isScrubbing = false }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .focusProperties { down = quickActionsFocusRequester },
                colors = SliderDefaults.colors(
                    activeTrackColor = AppColors.NeonCyan,
                    inactiveTrackColor = Color.White.copy(alpha = 0.12f)
                )
            )
        }
    }

    Spacer(modifier = Modifier.height(18.dp))

    // ── Row 4: TV-first 7-button control row ──────────────────────
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // 1. Play / Pause (primary)
        TvLiveControlButton(
            icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
            label = if (isPlaying) stringResource(R.string.player_pause)
                    else stringResource(R.string.player_play),
            onClick = onTogglePlayPause,
            isPrimary = true,
            modifier = Modifier
                .focusRequester(playButtonFocusRequester)
                .focusProperties { down = quickActionsFocusRequester }
        )

        // 2. Canlıya Dön — only when timeshift active
        if (showTimeshiftControls) {
            TvLiveControlButton(
                icon = Icons.Default.FiberManualRecord,
                label = stringResource(R.string.player_jump_to_live),
                onClick = onSeekToLiveEdge,
                accentColor = if (liveState == TvLiveState.LIVE_EDGE) Color.Red else Color.White,
                modifier = Modifier.focusProperties { down = quickActionsFocusRequester }
            )
        }

        // 3. Altyazı
        TvLiveControlButton(
            icon = Icons.Default.Subtitles,
            label = stringResource(R.string.player_subs),
            onClick = onOpenSubtitleTracks,
            badgeActive = subtitleTrackCount > 0,
            modifier = Modifier.focusProperties { down = quickActionsFocusRequester }
        )

        // 4. Ses — mute state shown via icon; opens audio tracks panel
        TvLiveControlButton(
            icon = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
            label = if (isMuted) stringResource(R.string.player_muted_badge)
                    else stringResource(R.string.player_audio),
            onClick = onOpenAudioTracks,
            accentColor = if (isMuted) Color(0xFFFF6B6B) else Color.White,
            modifier = Modifier.focusProperties { down = quickActionsFocusRequester }
        )

        // 5. Görüntü (Quality)
        TvLiveControlButton(
            icon = Icons.Default.HighQuality,
            label = stringResource(R.string.player_video_quality),
            onClick = onOpenVideoTracks,
            badgeActive = videoQualityCount > 1,
            modifier = Modifier.focusProperties { down = quickActionsFocusRequester }
        )

        // 6. Kanallar
        TvLiveControlButton(
            icon = Icons.Default.Tv,
            label = stringResource(R.string.channel_list),
            onClick = onOpenChannelList,
            modifier = Modifier.focusProperties { down = quickActionsFocusRequester }
        )

        // 7. Rehber (EPG)
        TvLiveControlButton(
            icon = Icons.Default.ViewSidebar,
            label = stringResource(R.string.player_open_guide_action),
            onClick = onOpenGuide,
            modifier = Modifier.focusProperties { down = quickActionsFocusRequester }
        )
    }
}

@Composable
private fun PlayerVodInfo(
    title: String,
    contentType: String,
    isPlaying: Boolean,
    currentPosition: Long,
    duration: Long,
    aspectRatioLabel: String,
    subtitleTrackCount: Int,
    audioTrackCount: Int,
    videoQualityCount: Int,
    isMuted: Boolean,
    playbackSpeed: Float,
    sleepTimerUiState: SleepTimerUiState,
    audioVideoSyncEnabled: Boolean,
    playButtonFocusRequester: FocusRequester,
    quickActionsFocusRequester: FocusRequester,
    onSeekToPosition: (Long) -> Unit,
    onSetScrubbingMode: (Boolean) -> Unit,
    onToggleAspectRatio: () -> Unit,
    onOpenSubtitleTracks: () -> Unit,
    onOpenAudioTracks: () -> Unit,
    onOpenVideoTracks: () -> Unit,
    onOpenPlaybackSpeed: () -> Unit,
    onOpenStopPlaybackTimer: () -> Unit,
    onOpenIdleStandbyTimer: () -> Unit,
    onOpenAudioVideoSync: () -> Unit,
    showEpisodesAction: Boolean,
    onOpenEpisodes: () -> Unit,
    onEnterPictureInPicture: () -> Unit,
    onToggleMute: () -> Unit,
    isCastConnected: Boolean,
    onCast: () -> Unit,
    onStopCasting: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    seekPreview: SeekPreviewState,
    onSeekPreviewPositionChanged: (Long?) -> Unit,
    showExternalPlayerAction: Boolean,
    onOpenExternalPlayer: () -> Unit
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val isTelevisionDevice = rememberIsTelevisionDevice()
    val compactControls = screenWidth < 700.dp
    val tabletControls = !isTelevisionDevice && screenWidth >= 700.dp && screenWidth < 1280.dp
    val transportButtonSize = when {
        compactControls -> 42.dp
        tabletControls -> 46.dp
        else -> 50.dp
    }
    val seekPreviewWidth = when {
        compactControls -> 148.dp
        tabletControls -> 168.dp
        else -> 188.dp
    }
    val outerSpacing = when {
        compactControls -> 8.dp
        tabletControls -> 10.dp
        else -> 12.dp
    }

    val playbackLabel = stringResource(R.string.player_playback_label)

    var sliderValue by remember(duration, currentPosition) {
        mutableStateOf(if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f)
    }
    var isScrubbing by remember { mutableStateOf(false) }
    val latestSeekCallback by rememberUpdatedState(onSeekToPosition)
    val latestScrubbingCallback by rememberUpdatedState(onSetScrubbingMode)
    val latestSeekPreviewPositionChanged by rememberUpdatedState(onSeekPreviewPositionChanged)

    LaunchedEffect(duration, currentPosition, isScrubbing) {
        if (!isScrubbing) {
            sliderValue = if (duration > 0) currentPosition.toFloat() / duration.toFloat() else 0f
        }
    }

    LaunchedEffect(sliderValue, isScrubbing) {
        if (isScrubbing && duration > 0) {
            kotlinx.coroutines.delay(1000L)
            latestSeekCallback((sliderValue * duration).toLong())
            latestScrubbingCallback(false)
            isScrubbing = false
            latestSeekPreviewPositionChanged(null)
        }
    }

    // Title / Meta Info Row
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        PlayerMetaPill(
            text = if (contentType == "MOVIE") {
                stringResource(R.string.player_type_movie)
            } else {
                stringResource(R.string.player_type_series)
            },
            accent = true
        )
        if (isMuted) {
            PlayerMetaPill(text = stringResource(R.string.player_muted_badge))
        }
        Text(
            text = title,
            style = MaterialTheme.typography.bodyMedium,
            color = Color.White.copy(alpha = 0.82f),
            maxLines = 1
        )
    }

    Spacer(modifier = Modifier.height(10.dp))

    // Unified VOD Player bar (Pill surface container)
    Surface(
        shape = RoundedCornerShape(18.dp),
        colors = SurfaceDefaults.colors(containerColor = Color(0xFF0D1426).copy(alpha = 0.65f)),
        border = Border(
            border = BorderStroke(1.2.dp, Color.White.copy(alpha = 0.12f)),
            shape = RoundedCornerShape(18.dp)
        ),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Seek bar Slider section
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                AnimatedVisibility(visible = seekPreview.visible) {
                    PlayerSeekPreviewCard(
                        preview = seekPreview,
                        previewHeight = when {
                            compactControls -> 96.dp
                            tabletControls -> 106.dp
                            else -> 118.dp
                        },
                        modifier = Modifier.width(seekPreviewWidth)
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = formatDuration(
                            if (isScrubbing && duration > 0) {
                                (sliderValue * duration).toLong()
                            } else {
                                currentPosition
                            }
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White
                    )
                    // D-pad seek: 10 seconds per step, confirm with Center/OK
                    val seekStepMs = 10_000L
                    Slider(
                        value = sliderValue,
                        onValueChange = { newValue ->
                            val clampedValue = newValue.coerceIn(0f, 1f)
                            if (!isScrubbing) {
                                isScrubbing = true
                                latestScrubbingCallback(true)
                            }
                            sliderValue = clampedValue
                            if (duration > 0) {
                                latestSeekPreviewPositionChanged((clampedValue * duration).toLong())
                            }
                        },
                        onValueChangeFinished = {
                            if (duration > 0) {
                                latestSeekCallback((sliderValue.coerceIn(0f, 1f) * duration).toLong())
                            }
                            if (isScrubbing) {
                                latestScrubbingCallback(false)
                                isScrubbing = false
                            }
                            latestSeekPreviewPositionChanged(null)
                        },
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 12.dp)
                            .focusProperties { down = playButtonFocusRequester }
                            .semantics { contentDescription = playbackLabel }
                            .onPreviewKeyEvent { event ->
                                if (duration <= 0L) return@onPreviewKeyEvent false
                                val native = event.nativeKeyEvent
                                val repeat = native.repeatCount
                                val stepMs = when {
                                    repeat == 0 -> 10_000L
                                    repeat < 5 -> 15_000L
                                    repeat < 10 -> 30_000L
                                    else -> 60_000L
                                }
                                when {
                                    // RIGHT arrow -> seek forward
                                    event.type == KeyEventType.KeyDown && event.key == Key.DirectionRight -> {
                                        val currentMs = (sliderValue * duration).toLong()
                                        val newMs = (currentMs + stepMs).coerceIn(0L, duration)
                                        val newValue = newMs.toFloat() / duration.toFloat()
                                        if (!isScrubbing) {
                                            isScrubbing = true
                                            latestScrubbingCallback(true)
                                        }
                                        sliderValue = newValue
                                        latestSeekPreviewPositionChanged(newMs)
                                        true
                                    }
                                    // LEFT arrow -> seek backward
                                    event.type == KeyEventType.KeyDown && event.key == Key.DirectionLeft -> {
                                        val currentMs = (sliderValue * duration).toLong()
                                        val newMs = (currentMs - stepMs).coerceIn(0L, duration)
                                        val newValue = newMs.toFloat() / duration.toFloat()
                                        if (!isScrubbing) {
                                            isScrubbing = true
                                            latestScrubbingCallback(true)
                                        }
                                        sliderValue = newValue
                                        latestSeekPreviewPositionChanged(newMs)
                                        true
                                    }
                                    // Center / OK -> commit seek
                                    event.type == KeyEventType.KeyUp &&
                                        (event.key == Key.DirectionCenter || event.key == Key.Enter) &&
                                        isScrubbing -> {
                                        latestSeekCallback((sliderValue * duration).toLong())
                                        latestScrubbingCallback(false)
                                        isScrubbing = false
                                        latestSeekPreviewPositionChanged(null)
                                        true
                                    }
                                    else -> false
                                }
                            },
                        enabled = duration > 0,
                        colors = SliderDefaults.colors(
                            activeTrackColor = AppColors.NeonCyan,
                            inactiveTrackColor = Color.White.copy(alpha = 0.12f)
                        )
                    )
                    Text(
                        text = formatDuration(duration),
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White
                    )
                }
            }

            // Buttons Layer Row (Playback on left, Quick Settings on right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left Side: Playback controls
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Previous / Rewind
                    CapsuleIconButton(
                        icon = Icons.Default.SkipPrevious,
                        contentDescription = stringResource(R.string.player_rewind),
                        onClick = onSeekBackward,
                        modifier = Modifier.focusProperties { down = quickActionsFocusRequester }
                    )

                    // Play/Pause (Capsule)
                    CapsulePlayPauseButton(
                        isPlaying = isPlaying,
                        onClick = onTogglePlayPause,
                        playButtonFocusRequester = playButtonFocusRequester,
                        modifier = Modifier.focusProperties { down = quickActionsFocusRequester }
                    )

                    // Stop / Seek to start
                    CapsuleIconButton(
                        icon = Icons.Default.Stop,
                        contentDescription = "Stop",
                        onClick = { latestSeekCallback(0L) },
                        modifier = Modifier.focusProperties { down = quickActionsFocusRequester }
                    )

                    // Next / Forward
                    CapsuleIconButton(
                        icon = Icons.Default.SkipNext,
                        contentDescription = stringResource(R.string.player_forward),
                        onClick = onSeekForward,
                        modifier = Modifier.focusProperties { down = quickActionsFocusRequester }
                    )

                    // Volume / Mute
                    CapsuleIconButton(
                        icon = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        contentDescription = stringResource(if (isMuted) R.string.player_unmute else R.string.player_mute),
                        onClick = onToggleMute,
                        focusedColor = if (isMuted) Color(0xFFFF6B6B) else Primary,
                        modifier = Modifier.focusProperties { down = quickActionsFocusRequester }
                    )
                }

                // Right Side: Settings & Extras
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.horizontalScroll(rememberScrollState())
                ) {
                    // External Player
                    if (showExternalPlayerAction) {
                        QuickActionButton(
                            icon = "harici",
                            label = stringResource(R.string.player_open_in_external_player),
                            onClick = onOpenExternalPlayer,
                            compact = true,
                            modifier = Modifier.focusProperties { down = quickActionsFocusRequester }
                        )
                    }

                    // Audio Tracks
                    QuickActionButton(
                        icon = "ses",
                        label = stringResource(R.string.player_audio),
                        onClick = onOpenAudioTracks,
                        compact = true,
                        modifier = Modifier.focusProperties { down = quickActionsFocusRequester }
                    )

                    // Subtitles
                    QuickActionButton(
                        icon = "altyazı",
                        label = stringResource(R.string.player_subs),
                        onClick = onOpenSubtitleTracks,
                        compact = true,
                        modifier = Modifier.focusProperties { down = quickActionsFocusRequester }
                    )

                    // Video Quality
                    QuickActionButton(
                        icon = "kalite",
                        label = stringResource(R.string.player_video_quality),
                        onClick = onOpenVideoTracks,
                        compact = true,
                        modifier = Modifier.focusProperties { down = quickActionsFocusRequester }
                    )

                    // Episodes (for Series)
                    if (showEpisodesAction) {
                        QuickActionButton(
                            icon = "bölümler",
                            label = stringResource(R.string.player_episodes),
                            onClick = onOpenEpisodes,
                            compact = true,
                            modifier = Modifier.focusProperties { down = quickActionsFocusRequester }
                        )
                    }

                    // Playback Speed
                    QuickActionButton(
                        icon = "hız",
                        label = formatPlaybackSpeedLabel(playbackSpeed),
                        onClick = onOpenPlaybackSpeed,
                        compact = true,
                        modifier = Modifier.focusProperties { down = quickActionsFocusRequester }
                    )

                    // Audio-Video Sync
                    if (audioVideoSyncEnabled && !isCastConnected) {
                        QuickActionButton(
                            icon = "a/v",
                            label = stringResource(R.string.player_av_sync_short),
                            onClick = onOpenAudioVideoSync,
                            compact = true,
                            modifier = Modifier.focusProperties { down = quickActionsFocusRequester }
                        )
                    }

                    // Sleep Timer
                    QuickActionButton(
                        icon = "süre",
                        label = stringResource(R.string.player_stop_playback_after),
                        onClick = onOpenStopPlaybackTimer,
                        compact = true,
                        modifier = Modifier.focusProperties { down = quickActionsFocusRequester }
                    )

                    // Idle Standby
                    QuickActionButton(
                        icon = "c-up",
                        label = stringResource(R.string.player_idle_standby_after),
                        onClick = onOpenIdleStandbyTimer,
                        compact = true,
                        modifier = Modifier.focusProperties { down = quickActionsFocusRequester }
                    )

                    // Chromecast / Screen Cast (Ekran Paylaşımı)
                    QuickActionButton(
                        icon = "yansıt",
                        label = if (isCastConnected) stringResource(R.string.player_stop_casting) else stringResource(R.string.player_cast),
                        onClick = if (isCastConnected) onStopCasting else onCast,
                        compact = true,
                        modifier = Modifier.focusProperties { down = quickActionsFocusRequester }
                    )

                    // PiP
                    QuickActionButton(
                        icon = "pip",
                        label = stringResource(R.string.player_picture_in_picture),
                        onClick = onEnterPictureInPicture,
                        compact = true,
                        modifier = Modifier.focusProperties { down = quickActionsFocusRequester }
                    )

                    // Aspect Ratio
                    QuickActionButton(
                        icon = "en-boy",
                        label = aspectRatioLabel,
                        onClick = onToggleAspectRatio,
                        compact = true,
                        modifier = Modifier.focusProperties { down = quickActionsFocusRequester }
                    )
                }
            }
        }
    }
}

@Composable
private fun PlayerSeekPreviewCard(
    preview: SeekPreviewState,
    previewHeight: androidx.compose.ui.unit.Dp = 118.dp,
    modifier: Modifier = Modifier
) {
    val artworkModel = rememberCrossfadeImageModel(preview.artworkUrl)

    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = SurfaceDefaults.colors(containerColor = Color.White.copy(alpha = 0.08f))
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(previewHeight)
                    .clip(RoundedCornerShape(14.dp))
                    .background(Color.Black.copy(alpha = 0.38f)),
                contentAlignment = Alignment.Center
            ) {
                when {
                    preview.frameBitmap != null -> {
                        Image(
                            bitmap = preview.frameBitmap.asImageBitmap(),
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    artworkModel != null -> {
                        AsyncImage(
                            model = artworkModel,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }

                    else -> {
                        Text(
                            text = preview.title.ifBlank { formatDuration(preview.positionMs) },
                            style = MaterialTheme.typography.labelLarge,
                            color = Color.White.copy(alpha = 0.72f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatDuration(preview.positionMs),
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = preview.title,
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White.copy(alpha = 0.64f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(start = 10.dp)
                )
            }
        }
    }
}

private fun formatPlaybackSpeedLabel(speed: Float): String {
    return if (speed % 1f == 0f) {
        "${speed.toInt()}x"
    } else {
        "${("%.2f".format(Locale.US, speed)).trimEnd('0').trimEnd('.')}x"
    }
}

private fun sleepTimerActionLabel(title: String, activeLabel: String, active: Boolean): String =
    if (active) activeLabel else title

private fun formatTimerRemaining(ms: Long): String {
    val totalSeconds = (ms.coerceAtLeast(0L) + 999L) / 1000L
    if (totalSeconds < 60L) return "${totalSeconds}s"
    val totalMinutes = (totalSeconds + 59L) / 60L
    return if (totalMinutes < 60L) {
        "${totalMinutes}m"
    } else {
        val hours = totalMinutes / 60L
        val minutes = totalMinutes % 60L
        if (minutes == 0L) "${hours}h" else "${hours}h ${minutes}m"
    }
}

@Composable
private fun CapsuleIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    focusedColor: Color = Color(0xFF00D2FF),
    accent: Boolean = false,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    TvClickableSurface(
        onClick = onClick,
        enabled = enabled,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (accent) Color(0xFFFF416C).copy(alpha = 0.15f) else Color.White.copy(alpha = 0.04f),
            focusedContainerColor = focusedColor.copy(alpha = 0.25f)
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.12f)),
                shape = RoundedCornerShape(12.dp)
            ),
            focusedBorder = Border(
                border = BorderStroke(1.5.dp, focusedColor),
                shape = RoundedCornerShape(12.dp)
            )
        ),
        modifier = modifier
            .height(42.dp)
            .width(52.dp)
            .semantics { this.contentDescription = contentDescription }
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = if (enabled) Color.White else Color.White.copy(alpha = 0.3f),
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
private fun CapsulePlayPauseButton(
    isPlaying: Boolean,
    onClick: () -> Unit,
    playButtonFocusRequester: FocusRequester,
    modifier: Modifier = Modifier
) {
    TvClickableSurface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Primary.copy(alpha = 0.18f),
            focusedContainerColor = Primary.copy(alpha = 0.35f)
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                border = BorderStroke(1.dp, Primary.copy(alpha = 0.3f)),
                shape = RoundedCornerShape(12.dp)
            ),
            focusedBorder = Border(
                border = BorderStroke(1.8.dp, Primary),
                shape = RoundedCornerShape(12.dp)
            )
        ),
        modifier = modifier
            .height(42.dp)
            .width(72.dp)
            .focusRequester(playButtonFocusRequester)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            if (isPlaying) {
                Text(
                    text = "II",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            } else {
                Icon(
                    imageVector = Icons.Default.PlayArrow,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(22.dp)
                )
            }
        }
    }
}

@Composable
private fun PlayerQuickSettingsButton(
    text: String,
    onClick: () -> Unit,
    icon: androidx.compose.ui.graphics.vector.ImageVector? = null,
    accentColor: Color = Color.White,
    compact: Boolean = false,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    TvClickableSurface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = accentColor.copy(alpha = if (isFocused) 0.22f else 0.12f),
            focusedContainerColor = accentColor.copy(alpha = 0.30f)
        ),
        modifier = modifier.onFocusChanged { isFocused = it.isFocused }
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (compact) 10.dp else 12.dp,
                vertical = if (compact) 6.dp else 8.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = accentColor,
                    modifier = Modifier.size(if (compact) 13.dp else 15.dp)
                )
            }
            Text(
                text = text,
                color = Color.White,
                style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TV-First Live Control Primitives
// ─────────────────────────────────────────────────────────────────────────────

/** State machine for live TV stream mode displayed in the status badge. */
private enum class TvLiveState { LIVE_EDGE, TIMESHIFT, ARCHIVE }

/**
 * Large, TV-remote-first control button with scale + border focus feedback.
 *
 * - Focused: 1.10× scale + bright border + accent icon/label color
 * - [isPrimary]: renders with a tinted background (play/pause principal action)
 * - [badgeActive]: renders a small accent dot to indicate an available feature
 * - [accentColor]: overrides the focus/indicator color (e.g. red for muted)
 */
@Composable
private fun TvLiveControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    isPrimary: Boolean = false,
    accentColor: Color = Color.White,
    badgeActive: Boolean = false,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val scaleAnim by animateFloatAsState(
        targetValue = if (isFocused) 1.10f else 1.0f,
        label = "tvCtrlScale"
    )
    val focusColor = if (isPrimary) Primary else accentColor

    TvClickableSurface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(14.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isPrimary) Primary.copy(alpha = 0.18f) else Color.White.copy(alpha = 0.06f),
            focusedContainerColor = focusColor.copy(alpha = 0.28f)
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.14f)),
                shape = RoundedCornerShape(14.dp)
            ),
            focusedBorder = Border(
                border = BorderStroke(2.2.dp, focusColor),
                shape = RoundedCornerShape(14.dp)
            )
        ),
        modifier = modifier
            .scale(scaleAnim)
            .onFocusChanged { isFocused = it.isFocused }
            .semantics { this.contentDescription = label }
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isFocused) focusColor else Color.White.copy(alpha = 0.88f),
                        modifier = Modifier.size(if (isPrimary) 26.dp else 22.dp)
                    )
                    // Badge dot for feature availability indicator
                    if (badgeActive) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(6.dp)
                                .background(Primary, RoundedCornerShape(99.dp))
                        )
                    }
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (isFocused) focusColor else Color.White.copy(alpha = 0.72f),
                    fontWeight = if (isFocused) FontWeight.Bold else FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun PlayerTransportButton(
    label: String,
    contentDescription: String,
    onClick: () -> Unit,
    buttonSize: androidx.compose.ui.unit.Dp = 56.dp,
    modifier: Modifier = Modifier
) {
    val coroutineScope = rememberCoroutineScope()
    val latestOnClick by rememberUpdatedState(onClick)
    var repeatJob by remember { mutableStateOf<Job?>(null) }

    fun stopRepeating() {
        repeatJob?.cancel()
        repeatJob = null
    }

    fun performSeekStep() {
        latestOnClick()
    }

    fun startRepeating() {
        if (repeatJob?.isActive == true) return
        performSeekStep()
        repeatJob = coroutineScope.launch {
            delay(350L)
            while (true) {
                performSeekStep()
                delay(180L)
            }
        }
    }

    TvClickableSurface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.13f),
            focusedContainerColor = Color(0xFF00D2FF).copy(alpha = 0.35f)
        ),
        modifier = modifier
            .size(buttonSize)
            .onPreviewKeyEvent { event ->
                when (event.nativeKeyEvent.keyCode) {
                    android.view.KeyEvent.KEYCODE_DPAD_CENTER,
                    android.view.KeyEvent.KEYCODE_ENTER,
                    android.view.KeyEvent.KEYCODE_NUMPAD_ENTER -> {
                        when (event.nativeKeyEvent.action) {
                            android.view.KeyEvent.ACTION_DOWN -> {
                                startRepeating()
                                true
                            }
                            android.view.KeyEvent.ACTION_UP -> {
                                stopRepeating()
                                true
                            }
                            else -> false
                        }
                    }
                    else -> false
                }
            }
            .semantics { this.contentDescription = contentDescription }
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Text(
                text = label,
                style = MaterialTheme.typography.headlineSmall,
                color = Color(0xFF00D2FF)
            )
        }
    }

    DisposableEffect(Unit) {
        onDispose { stopRepeating() }
    }
}

@Composable
private fun PlayerTransportIconButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    contentDescription: String,
    onClick: () -> Unit,
    buttonSize: androidx.compose.ui.unit.Dp = 56.dp,
    tint: Color = Color.White,
    modifier: Modifier = Modifier
) {
    TvClickableSurface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Color.White.copy(alpha = 0.13f),
            focusedContainerColor = Color(0xFF00D2FF).copy(alpha = 0.35f)
        ),
        modifier = modifier
            .size(buttonSize)
            .semantics { this.contentDescription = contentDescription }
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
            Icon(
                imageVector = icon,
                contentDescription = contentDescription,
                tint = tint,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
private fun PlayerMetaPill(
    text: String,
    accent: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(999.dp),
        colors = SurfaceDefaults.colors(
            containerColor = if (accent) Primary.copy(alpha = 0.22f) else Color.White.copy(alpha = 0.10f)
        )
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun PlayerQuickActionRows(
    primaryActions: List<PlayerActionSpec>,
    secondaryActions: List<PlayerActionSpec>,
    firstActionFocusRequester: FocusRequester,
    primaryActionsUpFocusRequester: FocusRequester? = null,
    singleRow: Boolean = false,
    compactButtons: Boolean = false
) {
    val rows = listOf(primaryActions, secondaryActions).filter { it.isNotEmpty() }
    if (rows.isEmpty()) return

    Spacer(modifier = Modifier.height(if (compactButtons) 10.dp else 14.dp))

    if (singleRow) {
        val actions = rows.flatten()
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(end = 4.dp)
        ) {
            itemsIndexed(actions) { actionIndex, action ->
                PlayerQuickSettingsButton(
                    text = action.label,
                    onClick = action.onClick,
                    icon = action.icon,
                    accentColor = action.accentColor,
                    compact = compactButtons,
                    modifier = Modifier
                        .then(
                            if (actionIndex == 0) {
                                Modifier.focusRequester(firstActionFocusRequester)
                            } else {
                                Modifier
                            }
                        )
                        .focusProperties {
                            if (actionIndex == 0 && primaryActionsUpFocusRequester != null) {
                                up = primaryActionsUpFocusRequester
                            }
                        }
                )
            }
        }
        return
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        rows.forEachIndexed { index, row ->
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (index == 1) {
                    PlayerMetaPill(text = stringResource(R.string.player_more_controls))
                }
                row.forEachIndexed { actionIndex, action ->
                    PlayerQuickSettingsButton(
                        text = action.label,
                        onClick = action.onClick,
                        icon = action.icon,
                        accentColor = action.accentColor,
                        compact = compactButtons,
                        modifier = Modifier
                            .then(
                                if (index == 0 && actionIndex == 0) {
                                    Modifier.focusRequester(firstActionFocusRequester)
                                } else {
                                    Modifier
                                }
                            )
                            .focusProperties {
                                if (index == 0 && actionIndex == 0 && primaryActionsUpFocusRequester != null) {
                                    up = primaryActionsUpFocusRequester
                                }
                            }
                    )
                }
            }
        }
    }
}

@Composable
private fun LiveTimeshiftScrubber(
    timeshiftUiState: PlayerTimeshiftUiState,
    isPlaying: Boolean,
    playButtonFocusRequester: FocusRequester,
    quickActionsFocusRequester: FocusRequester,
    onTogglePlayPause: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    onSeekToPosition: (Long) -> Unit,
    onSeekToLiveEdge: () -> Unit,
    onSetScrubbingMode: (Boolean) -> Unit,
    isCastConnected: Boolean,
    onCast: () -> Unit,
    onStopCasting: () -> Unit,
    modifier: Modifier = Modifier
) {
    val bufferDepthMs = timeshiftUiState.bufferDepthMs.coerceAtLeast(1L)
    val bufferedBehindLive = timeshiftUiState.bufferedBehindLiveMs
    val targetFraction = remember(bufferedBehindLive, bufferDepthMs) {
        1f - (bufferedBehindLive.toFloat() / bufferDepthMs.toFloat()).coerceIn(0f, 1f)
    }
    var scrubberFraction by remember { mutableStateOf(targetFraction) }
    var isScrubbing by remember { mutableStateOf(false) }
    val latestSeekCallback by rememberUpdatedState(onSeekToPosition)
    val latestScrubbingCallback by rememberUpdatedState(onSetScrubbingMode)
    val latestSeekBackward by rememberUpdatedState(onSeekBackward)
    val latestSeekForward by rememberUpdatedState(onSeekForward)
    val latestSeekToLiveEdge by rememberUpdatedState(onSeekToLiveEdge)

    LaunchedEffect(targetFraction, isScrubbing) {
        if (!isScrubbing) scrubberFraction = targetFraction
    }

    val engineState = timeshiftUiState.engineState
    val oldestWallMs = engineState.bufferStartMs
    val appTimeFormat = LocalAppTimeFormat.current
    val timeFormat = remember(appTimeFormat) { appTimeFormat.createTimeFormat() }
    val oldestLabel = if (oldestWallMs > 0L) {
        timeFormat.format(Date(oldestWallMs))
    } else if (bufferDepthMs > 1_000L) {
        "-${formatTimeshiftDuration(bufferDepthMs)}"
    } else {
        ""
    }

    val currentOffsetMs = if (isScrubbing) {
        ((1f - scrubberFraction) * bufferDepthMs.toFloat()).toLong()
    } else {
        bufferedBehindLive
    }

    var liveDotVisible by remember { mutableStateOf(true) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(700L)
            liveDotVisible = !liveDotVisible
        }
    }

    val scrubberCd = stringResource(R.string.player_live_scrubber_cd)

    Surface(
        shape = RoundedCornerShape(20.dp),
        colors = SurfaceDefaults.colors(containerColor = Color.White.copy(alpha = 0.06f)),
        modifier = modifier
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Transport controls pill
            Surface(
                shape = RoundedCornerShape(999.dp),
                colors = SurfaceDefaults.colors(containerColor = Color.Black.copy(alpha = 0.24f))
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    PlayerTransportButton(
                        label = "\u23EA",
                        contentDescription = stringResource(R.string.player_rewind),
                        onClick = onSeekBackward,
                        modifier = Modifier.focusProperties { down = quickActionsFocusRequester }
                    )
                    TvClickableSurface(
                        onClick = onTogglePlayPause,
                        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(50)),
                        colors = ClickableSurfaceDefaults.colors(
                            containerColor = Primary.copy(alpha = 0.84f),
                            focusedContainerColor = Primary
                        ),
                        modifier = Modifier
                            .size(62.dp)
                            .focusRequester(playButtonFocusRequester)
                            .focusProperties { down = quickActionsFocusRequester }
                    ) {
                        Box(contentAlignment = Alignment.Center, modifier = Modifier.fillMaxSize()) {
                            if (isPlaying) {
                                Text(text = "II", style = MaterialTheme.typography.headlineMedium, color = Color.White)
                            } else {
                                Icon(
                                    imageVector = Icons.Default.PlayArrow,
                                    contentDescription = stringResource(R.string.player_play),
                                    tint = Color.White,
                                    modifier = Modifier.size(30.dp)
                                )
                            }
                        }
                    }
                    PlayerTransportButton(
                        label = "\u23E9",
                        contentDescription = stringResource(R.string.player_forward),
                        onClick = onSeekForward,
                        modifier = Modifier.focusProperties { down = quickActionsFocusRequester }
                    )
                    PlayerTransportIconButton(
                        icon = if (isCastConnected) Icons.Default.CastConnected else Icons.Default.Cast,
                        contentDescription = stringResource(if (isCastConnected) R.string.player_stop_casting else R.string.player_cast),
                        onClick = if (isCastConnected) onStopCasting else onCast,
                        tint = if (isCastConnected) Primary else Color.White,
                        buttonSize = 56.dp,
                        modifier = Modifier.focusProperties { down = quickActionsFocusRequester }
                    )
                }
            }

            // Scrubber section
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Labels: oldest time | current offset | LIVE badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = oldestLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White.copy(alpha = 0.55f)
                    )
                    Text(
                        text = if (currentOffsetMs < 2_000L) {
                            stringResource(R.string.player_live_ready)
                        } else {
                            stringResource(R.string.player_live_offset, formatTimeshiftDuration(currentOffsetMs))
                        },
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = if (currentOffsetMs < 2_000L) FontWeight.Bold else FontWeight.Normal,
                        color = if (currentOffsetMs < 2_000L) Primary else Color.White
                    )
                    // LIVE edge badge with pulsing dot
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(7.dp)
                                .background(
                                    color = Color.Red.copy(alpha = if (liveDotVisible) 0.95f else 0.30f),
                                    shape = RoundedCornerShape(999.dp)
                                )
                        )
                        Text(
                            text = stringResource(R.string.player_jump_to_live_short),
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = Color.White.copy(alpha = 0.85f)
                        )
                    }
                }

                // Timeline slider
                Slider(
                    value = scrubberFraction,
                    onValueChange = { newValue ->
                        val clamped = newValue.coerceIn(0f, 1f)
                        if (!isScrubbing) {
                            isScrubbing = true
                            latestScrubbingCallback(true)
                        }
                        scrubberFraction = clamped
                    },
                    onValueChangeFinished = {
                        val finalFraction = scrubberFraction.coerceIn(0f, 1f)
                        if (finalFraction >= 0.995f) {
                            latestSeekToLiveEdge()
                        } else {
                            latestSeekCallback((finalFraction * bufferDepthMs.toFloat()).toLong())
                        }
                        if (isScrubbing) {
                            latestScrubbingCallback(false)
                            isScrubbing = false
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .focusProperties {
                            down = quickActionsFocusRequester
                            up = playButtonFocusRequester
                        }
                        .onPreviewKeyEvent { event ->
                            if (event.nativeKeyEvent.action != android.view.KeyEvent.ACTION_DOWN) {
                                return@onPreviewKeyEvent false
                            }
                            when (event.nativeKeyEvent.keyCode) {
                                android.view.KeyEvent.KEYCODE_DPAD_LEFT -> { latestSeekBackward(); true }
                                android.view.KeyEvent.KEYCODE_DPAD_RIGHT -> {
                                    if (timeshiftUiState.canSeekToLive) latestSeekForward() else latestSeekToLiveEdge()
                                    true
                                }
                                else -> false
                            }
                        }
                        .semantics { contentDescription = scrubberCd },
                    enabled = bufferDepthMs > 2_000L,
                    colors = SliderDefaults.colors(
                        activeTrackColor = AppColors.NeonCyan,
                        inactiveTrackColor = Color.White.copy(alpha = 0.22f),
                        thumbColor = AppColors.NeonCyan
                    )
                )
            }
        }
    }
}

private fun formatTimeshiftDuration(ms: Long): String {
    val totalSeconds = ms / 1_000L
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    val hours = minutes / 60L
    val remainingMinutes = minutes % 60L
    return if (hours > 0L) {
        String.format(Locale.getDefault(), "%d:%02d:%02d", hours, remainingMinutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%d:%02d", minutes, seconds)
    }
}

private fun formatDuration(ms: Long): String {
    val totalSeconds = ms / 1000
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    val hours = minutes / 60
    val remainingMinutes = minutes % 60

    return if (hours > 0) {
        String.format(Locale.getDefault(), "%02d:%02d:%02d", hours, remainingMinutes, seconds)
    } else {
        String.format(Locale.getDefault(), "%02d:%02d", remainingMinutes, seconds)
    }
}
