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
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.focusable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kaynanamtv.app.ui.components.ChannelLogoBadge
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Lock
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
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material.icons.filled.ViewSidebar
import androidx.compose.material.icons.filled.VolumeOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Dashboard
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.RadioButtonChecked
import androidx.compose.material.icons.filled.SyncAlt
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.withFrameNanos
import kotlinx.coroutines.isActive
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathParser
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

private val Replay10Icon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Replay10",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).addPath(
        pathData = PathParser().parsePathString("M12 5V1L7 6l5 5V7c3.31 0 6 2.69 6 6s-2.69 6-6 6-6-2.69-6-6H4c0 4.42 3.58 8 8 8s8-3.58 8-8-3.58-8-8-8z").toNodes(),
        fill = SolidColor(Color.White)
    ).addPath(
        pathData = PathParser().parsePathString("M10.95 14.53h-.91v-3.41h-.04l-.84.6v-.76l.96-.69h.83v4.26zm3.92-2.14c0 .4-.07.74-.21 1.01s-.34.47-.6.6-.56.19-.9.19-.64-.06-.9-.19-.46-.33-.6-.6-.21-.61-.21-1.01v-.98c0-.4.07-.74.21-1.01s.34-.47.6-.6.56-.19.9-.19.64.06.9.19.46.33.6.6.21.61.21 1.01v.98zm-.89-.96c0-.3-.04-.54-.12-.71s-.19-.3-.33-.38-.3-.12-.47-.12-.33.04-.47.12-.25.21-.33.38-.12.41-.12.71v1.01c0 .3.04.54.12.71s.19.3.33.38.3.12.47.12.33-.04.47-.12.25-.21.33-.38.12-.41.12-.71v-1.01z").toNodes(),
        fill = SolidColor(Color.White)
    ).build()
}

private val Forward10Icon: ImageVector by lazy {
    ImageVector.Builder(
        name = "Forward10",
        defaultWidth = 24.dp,
        defaultHeight = 24.dp,
        viewportWidth = 24f,
        viewportHeight = 24f
    ).addPath(
        pathData = PathParser().parsePathString("M12 5V1l5 5-5 5V7c-3.31 0-6 2.69-6 6s2.69 6 6 6 6-2.69 6-6h2c0 4.42-3.58 8-8 8s-8-3.58-8-8 3.58-8 8-8z").toNodes(),
        fill = SolidColor(Color.White)
    ).addPath(
        pathData = PathParser().parsePathString("M10.95 14.53h-.91v-3.41h-.04l-.84.6v-.76l.96-.69h.83v4.26zm3.92-2.14c0 .4-.07.74-.21 1.01s-.34.47-.6.6-.56.19-.9.19-.64-.06-.9-.19-.46-.33-.6-.6-.21-.61-.21-1.01v-.98c0-.4.07-.74.21-1.01s.34-.47.6-.6.56-.19.9-.19.64.06.9.19.46.33.6.6.21.61.21 1.01v.98zm-.89-.96c0-.3-.04-.54-.12-.71s-.19-.3-.33-.38-.3-.12-.47-.12-.33.04-.47.12-.25.21-.33.38-.12.41-.12.71v1.01c0 .3.04.54.12.71s.19.3.33.38.3.12.47.12.33-.04.47-.12.25-.21.33-.38.12-.41.12-.71v-1.01z").toNodes(),
        fill = SolidColor(Color.White)
    ).build()
}

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
    onCloseControls: () -> Unit = {},
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
    nextProgram: Program? = null,
    onToggleDiagnostics: () -> Unit = {},
    onUserInteraction: () -> Unit = {},
    onOpenGuide: () -> Unit = {},
    onLockScreen: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(animationSpec = androidx.compose.animation.core.tween(150)),
        exit = fadeOut(animationSpec = androidx.compose.animation.core.tween(150)),
        modifier = modifier
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectTapGestures {
                        onCloseControls()
                    }
                }
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
                mediaTitle = mediaTitle,
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
                nextProgram = nextProgram,
                onToggleDiagnostics = onToggleDiagnostics,
                showExternalPlayerAction = showExternalPlayerAction,
                onOpenExternalPlayer = onOpenExternalPlayer,
                onOpenGuide = onOpenGuide,
                onLockScreen = onLockScreen,
                onClose = onClose
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
    mediaTitle: String? = null,
    onClose: () -> Unit,
    modifier: Modifier = Modifier
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val isTelevisionDevice = rememberIsTelevisionDevice()
    val appTimeFormat = LocalAppTimeFormat.current
    val timeFormat = remember(appTimeFormat) { appTimeFormat.createTimeFormat() }
    val topBarHeight = when {
        screenWidth < 700.dp -> 96.dp
        !isTelevisionDevice && screenWidth < 1280.dp -> 110.dp
        else -> 124.dp
    }
    val horizontalPadding = when {
        screenWidth < 700.dp -> 18.dp
        !isTelevisionDevice && screenWidth < 1280.dp -> 24.dp
        else -> 32.dp
    }
    val verticalPadding = when {
        screenWidth < 700.dp -> 14.dp
        !isTelevisionDevice && screenWidth < 1280.dp -> 18.dp
        else -> 22.dp
    }
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(topBarHeight)
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Black.copy(alpha = 0.82f), Color.Transparent)
                )
            )
            .padding(horizontal = horizontalPadding, vertical = verticalPadding)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .background(
                                color = if (contentType == "LIVE") Color.Red else Color(0xFF4F46E5),
                                shape = RoundedCornerShape(999.dp)
                            )
                            .padding(horizontal = 14.dp, vertical = 5.dp)
                    ) {
                        Text(
                            text = when (contentType) {
                                "LIVE" -> "Canlı"
                                "MOVIE" -> "Film"
                                else -> "Dizi"
                            },
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Bold),
                            color = Color.White
                        )
                    }

                    Text(
                        text = "|",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White.copy(alpha = 0.35f)
                    )

                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                        color = Color.White,
                        maxLines = 1
                    )
                }

                if (!mediaTitle.isNullOrBlank()) {
                    Text(
                        text = mediaTitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = Color.White.copy(alpha = 0.65f),
                        modifier = Modifier.padding(start = 4.dp)
                    )
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                val currentTime = remember(clockLabelOverride, timeFormat) {
                    mutableStateOf(clockLabelOverride ?: timeFormat.format(Date()))
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
                    style = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold),
                    color = Color.White.copy(alpha = 0.85f),
                    modifier = Modifier.padding(end = 18.dp)
                )

                TvClickableSurface(
                    onClick = onClose,
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(999.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.White.copy(alpha = 0.12f),
                        focusedContainerColor = Color(0xFF4F46E5).copy(alpha = 0.85f)
                    ),
                    border = ClickableSurfaceDefaults.border(
                        border = Border(
                            border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                            shape = RoundedCornerShape(999.dp)
                        ),
                        focusedBorder = Border(
                            border = BorderStroke(2.dp, Color(0xFF818CF8)),
                            shape = RoundedCornerShape(999.dp)
                        )
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = stringResource(R.string.player_close),
                            style = MaterialTheme.typography.labelMedium.copy(fontWeight = FontWeight.Medium),
                            color = Color.White
                        )
                    }
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
    nextProgram: Program? = null,
    onToggleDiagnostics: () -> Unit = {},
    showExternalPlayerAction: Boolean,
    onOpenExternalPlayer: () -> Unit,
    onOpenGuide: () -> Unit = {},
    onLockScreen: () -> Unit = {},
    onClose: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val isVod = contentType != "LIVE" || isCatchUpPlayback
    val bottomBarWidthFraction = if (isVod) 0.82f else 1f
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.84f))
                )
            )
            .padding(horizontal = 32.dp, vertical = 20.dp),
        contentAlignment = Alignment.BottomCenter
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(bottomBarWidthFraction),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            if (isVod) {
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
                    onOpenExternalPlayer = onOpenExternalPlayer,
                    onLockScreen = onLockScreen,
                    onClose = onClose
                )
            } else {
                PlayerLiveInfo(
                    currentProgram = currentProgram,
                    nextProgram = nextProgram,
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
                    onToggleDiagnostics = onToggleDiagnostics,
                    showExternalPlayerAction = showExternalPlayerAction,
                    onOpenExternalPlayer = onOpenExternalPlayer,
                    isCatchUpPlayback = isCatchUpPlayback,
                    onOpenGuide = onOpenGuide
                )
            }
        }
    }
}

@Composable
private fun PlayerLiveInfo(
    currentProgram: Program?,
    nextProgram: Program? = null,
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
    onToggleDiagnostics: () -> Unit = {},
    showExternalPlayerAction: Boolean,
    onOpenExternalPlayer: () -> Unit,
    isCatchUpPlayback: Boolean = false,
    onOpenGuide: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val showTimeshiftControls = timeshiftUiState.available && !isCastConnected
    val appTimeFormat = LocalAppTimeFormat.current
    val timeFormat = remember(appTimeFormat) { appTimeFormat.createTimeFormat() }

    val liveState = when {
        isCatchUpPlayback -> TvLiveState.ARCHIVE
        showTimeshiftControls && timeshiftUiState.bufferedBehindLiveMs >= 2_000L -> TvLiveState.TIMESHIFT
        else -> TvLiveState.LIVE_EDGE
    }
    var liveDotVisible by remember { mutableStateOf(true) }
    LaunchedEffect(liveState) {
        if (liveState == TvLiveState.LIVE_EDGE) {
            while (true) { delay(700L); liveDotVisible = !liveDotVisible }
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

    Surface(
        modifier = modifier
            .widthIn(max = 1100.dp)
            .fillMaxWidth(0.80f),
        shape = RoundedCornerShape(22.dp),
        colors = SurfaceDefaults.colors(containerColor = VodControlsColors.DockBackground),
        border = Border(
            border = BorderStroke(1.2.dp, Brush.linearGradient(VodControlsColors.DockBorderGradient)),
            shape = RoundedCornerShape(22.dp)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 14.dp)
        ) {
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
                    // Row 1: Canonical State Badge + Channel Info + Resolution Badge
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
                        val resolvedChannelName = currentChannel?.name?.takeIf { it.isNotBlank() }
                            ?: currentChannelName?.takeIf { it.isNotBlank() }
                            ?: ""
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
                        val resolutionText = when {
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
                                text = resolutionText,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = Color(0xFFC7D2FE)
                            )
                        }
                    }

                    // Row 2: Current Program Title
                    val progTitle = currentProgram?.title
                        ?: currentChannelName
                        ?: "Canlı Yayın"
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
                                // Background Track (clearly visible neutral slate)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(4.dp)
                                        .clip(RoundedCornerShape(99.dp))
                                        .background(Color.White.copy(alpha = 0.20f))
                                )
                                // Active Elapsed Track (vibrant purple/violet gradient)
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
                            Box(
                                modifier = Modifier
                                    .size(9.dp)
                                    .background(Color(0xFFFF4D4F), CircleShape)
                            )
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
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.Start),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. Play/Pause (Primary Action)
                TvVodControlButton(
                    icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    label = if (isPlaying) "Duraklat" else "Oynat",
                    isPrimary = true,
                    onClick = onTogglePlayPause,
                    modifier = Modifier
                        .focusRequester(playButtonFocusRequester)
                        .focusProperties { down = quickActionsFocusRequester }
                )

                // 2. Canlıya Dön
                val isLiveEdge = isPlaying && liveState == TvLiveState.LIVE_EDGE
                TvVodControlButton(
                    icon = Icons.Default.FiberManualRecord,
                    label = "Canlıya Dön",
                    accentColor = if (!isLiveEdge) Color(0xFFEF4444) else Color.White.copy(alpha = 0.35f),
                    onClick = onSeekToLiveEdge,
                    modifier = Modifier.focusProperties { down = quickActionsFocusRequester }
                )

                // 3. Ses / Audio Tracks
                TvVodControlButton(
                    icon = Icons.Default.VolumeUp,
                    label = "Ses",
                    badgeActive = audioTrackCount > 1,
                    onClick = onOpenAudioTracks,
                    modifier = Modifier.focusProperties { down = quickActionsFocusRequester }
                )

                // 4. Sessiz / Mute
                TvVodControlButton(
                    icon = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                    label = if (isMuted) "Sesi Aç" else "Sessiz",
                    accentColor = if (isMuted) Color(0xFFFF6B6B) else Color.White,
                    onClick = onToggleMute,
                    modifier = Modifier.focusProperties { down = quickActionsFocusRequester }
                )

                // 6. Altyazılar
                TvVodControlButton(
                    icon = Icons.Default.Subtitles,
                    label = "Altyazılar",
                    badgeActive = subtitleTrackCount > 0,
                    onClick = onOpenSubtitleTracks,
                    modifier = Modifier.focusProperties { down = quickActionsFocusRequester }
                )

                // 7. Video Kalitesi
                TvVodControlButton(
                    icon = Icons.Default.HighQuality,
                    label = "Kalite",
                    badgeActive = videoQualityCount > 1,
                    onClick = onOpenVideoTracks,
                    modifier = Modifier.focusProperties { down = quickActionsFocusRequester }
                )

                // 8. Kanal Listesi
                TvVodControlButton(
                    icon = Icons.Default.Tv,
                    label = "Kanal Listesi",
                    onClick = onOpenChannelList,
                    modifier = Modifier.focusProperties { down = quickActionsFocusRequester }
                )

                // 9. EPG / Rehber
                TvVodControlButton(
                    icon = Icons.Default.ViewSidebar,
                    label = "EPG",
                    onClick = onOpenGuide,
                    modifier = Modifier.focusProperties { down = quickActionsFocusRequester }
                )

                // 10. En-Boy Oranı
                TvVodControlButton(
                    icon = Icons.Default.AspectRatio,
                    label = aspectRatioLabel,
                    onClick = onToggleAspectRatio,
                    modifier = Modifier.focusProperties { down = quickActionsFocusRequester }
                )

                // 11. Cast / Ekrana Yansıt
                TvVodControlButton(
                    icon = if (isCastConnected) Icons.Default.CastConnected else Icons.Default.Cast,
                    label = if (isCastConnected) "Yansıtmayı Durdur" else "Yansıt",
                    accentColor = if (isCastConnected) AppColors.NeonCyan else Color.White,
                    onClick = if (isCastConnected) onStopCasting else onCast,
                    modifier = Modifier.focusProperties { down = quickActionsFocusRequester }
                )

                // 12. Picture-in-Picture
                TvVodControlButton(
                    icon = Icons.Default.PictureInPicture,
                    label = "PiP",
                    onClick = onEnterPictureInPicture,
                    modifier = Modifier.focusProperties { down = quickActionsFocusRequester }
                )

                // 13. Kayıt (PVR Başlat/Durdur)
                val isRecActive = currentRecordingStatus == RecordingStatus.RECORDING
                TvVodControlButton(
                    icon = if (isRecActive) Icons.Default.Stop else Icons.Default.RadioButtonChecked,
                    label = if (isRecActive) "Kaydı Durdur" else "Kayıt",
                    accentColor = if (isRecActive) Color(0xFFFF4D4F) else Color.White,
                    badgeActive = isRecActive,
                    onClick = if (isRecActive) onStopRecording else onStartRecording,
                    modifier = Modifier.focusProperties { down = quickActionsFocusRequester }
                )

                // 14. Zamanlanmış Kayıt
                TvVodControlButton(
                    icon = Icons.Default.Schedule,
                    label = "Zamanla Kayıt",
                    onClick = onScheduleRecording,
                    modifier = Modifier.focusProperties { down = quickActionsFocusRequester }
                )

                // 15. MultiView / Çoklu Ekran
                TvVodControlButton(
                    icon = Icons.Default.Dashboard,
                    label = "MultiView",
                    onClick = onOpenSplitScreen,
                    modifier = Modifier.focusProperties { down = quickActionsFocusRequester }
                )

                // 16. Arşiv / Catch-Up
                TvVodControlButton(
                    icon = Icons.Default.History,
                    label = "Arşiv",
                    onClick = onOpenArchive,
                    modifier = Modifier.focusProperties { down = quickActionsFocusRequester }
                )

                // 17. Tanılama / Diagnostics
                TvVodControlButton(
                    icon = Icons.Default.Info,
                    label = "Tanılama",
                    onClick = onToggleDiagnostics,
                    modifier = Modifier.focusProperties { down = quickActionsFocusRequester }
                )

                // 18. Ses/Görüntü Senkronizasyonu
                if (audioVideoSyncEnabled) {
                    TvVodControlButton(
                        icon = Icons.Default.SyncAlt,
                        label = "A/V Sync",
                        onClick = onOpenAudioVideoSync,
                        modifier = Modifier.focusProperties { down = quickActionsFocusRequester }
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
    audioVideoSyncEnabled: Boolean = false,
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
    onOpenAudioVideoSync: () -> Unit = {},
    showEpisodesAction: Boolean = false,
    onOpenEpisodes: () -> Unit = {},
    onEnterPictureInPicture: () -> Unit = {},
    onToggleMute: () -> Unit = {},
    isCastConnected: Boolean = false,
    onCast: () -> Unit = {},
    onStopCasting: () -> Unit = {},
    onTogglePlayPause: () -> Unit,
    onSeekBackward: () -> Unit,
    onSeekForward: () -> Unit,
    seekPreview: SeekPreviewState,
    onSeekPreviewPositionChanged: (Long?) -> Unit,
    showExternalPlayerAction: Boolean = false,
    onOpenExternalPlayer: () -> Unit = {},
    onLockScreen: () -> Unit = {},
    onClose: () -> Unit = {}
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
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
        modifier = Modifier.fillMaxWidth(),
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
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
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
                        previewHeight = if (screenWidth < 700.dp) 96.dp else 114.dp,
                        modifier = Modifier
                            .width(if (screenWidth < 700.dp) 148.dp else 180.dp)
                            .padding(bottom = 8.dp)
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
                                    event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                                    (event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_RIGHT) -> {
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
                                    event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_DOWN &&
                                    (event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_LEFT) -> {
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
                                    event.nativeKeyEvent.action == android.view.KeyEvent.ACTION_UP &&
                                    (event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_DPAD_CENTER ||
                                     event.nativeKeyEvent.keyCode == android.view.KeyEvent.KEYCODE_ENTER) &&
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

            // TV-First Control Buttons Row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // 1. -10 sn Rewind
                TvVodControlButton(
                    icon = Replay10Icon,
                    label = "-10 sn",
                    onClick = onSeekBackward,
                    modifier = Modifier.focusProperties { down = quickActionsFocusRequester }
                )

                // 2. Play / Pause (Primary)
                TvVodControlButton(
                    icon = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                    label = if (isPlaying) stringResource(R.string.player_pause) else stringResource(R.string.player_play),
                    onClick = onTogglePlayPause,
                    isPrimary = true,
                    focusRequester = playButtonFocusRequester,
                    modifier = Modifier.focusProperties { down = quickActionsFocusRequester }
                )

                // 3. +10 sn Forward
                TvVodControlButton(
                    icon = Forward10Icon,
                    label = "+10 sn",
                    onClick = onSeekForward,
                    modifier = Modifier.focusProperties { down = quickActionsFocusRequester }
                )

                // 4. Durdur
                TvVodControlButton(
                    icon = Icons.Default.Stop,
                    label = "Durdur",
                    onClick = onClose,
                    modifier = Modifier.focusProperties { down = quickActionsFocusRequester }
                )

                // 5. Altyazı
                TvVodControlButton(
                    icon = Icons.Default.Subtitles,
                    label = stringResource(R.string.player_subs),
                    onClick = onOpenSubtitleTracks,
                    badgeActive = subtitleTrackCount > 0,
                    modifier = Modifier.focusProperties { down = quickActionsFocusRequester }
                )

                // 6. Ses (Audio Tracks & Mute Indicator)
                TvVodControlButton(
                    icon = if (isMuted) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                    label = if (isMuted) stringResource(R.string.player_muted_badge) else stringResource(R.string.player_audio),
                    onClick = onOpenAudioTracks,
                    accentColor = if (isMuted) Color(0xFFFF6B6B) else Color.White,
                    modifier = Modifier.focusProperties { down = quickActionsFocusRequester }
                )

                // 7. Görüntü Kalitesi
                TvVodControlButton(
                    icon = Icons.Default.HighQuality,
                    label = stringResource(R.string.player_video_quality),
                    onClick = onOpenVideoTracks,
                    badgeActive = videoQualityCount > 1,
                    modifier = Modifier.focusProperties { down = quickActionsFocusRequester }
                )

                // 8. Oynatma Hızı
                TvVodControlButton(
                    icon = Icons.Default.Speed,
                    label = formatPlaybackSpeedLabel(playbackSpeed),
                    onClick = onOpenPlaybackSpeed,
                    modifier = Modifier.focusProperties { down = quickActionsFocusRequester }
                )

                // 9. En-Boy Oranı
                TvVodControlButton(
                    icon = Icons.Default.AspectRatio,
                    label = aspectRatioLabel.ifBlank { "Fit" },
                    onClick = onToggleAspectRatio,
                    modifier = Modifier.focusProperties { down = quickActionsFocusRequester }
                )

                // 10. PiP
                TvVodControlButton(
                    icon = Icons.Default.PictureInPicture,
                    label = "PiP",
                    onClick = onEnterPictureInPicture,
                    modifier = Modifier.focusProperties { down = quickActionsFocusRequester }
                )

                // 11. Kilit
                TvVodControlButton(
                    icon = Icons.Default.Lock,
                    label = stringResource(R.string.player_lock_screen),
                    onClick = onLockScreen,
                    modifier = Modifier.focusProperties { down = quickActionsFocusRequester }
                )

                // 12. Uyku Zamanlayıcı
                TvVodControlButton(
                    icon = Icons.Default.Timer,
                    label = "Uyku",
                    onClick = onOpenStopPlaybackTimer,
                    badgeActive = sleepTimerUiState.stopTimerActive,
                    accentColor = if (sleepTimerUiState.stopTimerActive) Color(0xFF6366F1) else Color.White,
                    modifier = Modifier.focusProperties { down = quickActionsFocusRequester }
                )

                // 13. Boşta Bekleme
                TvVodControlButton(
                    icon = Icons.Default.Snooze,
                    label = "Boşta",
                    onClick = onOpenIdleStandbyTimer,
                    badgeActive = sleepTimerUiState.idleTimerActive,
                    accentColor = if (sleepTimerUiState.idleTimerActive) Color(0xFF6366F1) else Color.White,
                    modifier = Modifier.focusProperties { down = quickActionsFocusRequester }
                )

                // 14. Bölümler (Dizi ise)
                if (showEpisodesAction) {
                    TvVodControlButton(
                        icon = Icons.Default.Tv,
                        label = stringResource(R.string.player_episodes),
                        onClick = onOpenEpisodes,
                        accentColor = AppColors.NeonCyan,
                        modifier = Modifier.focusProperties { down = quickActionsFocusRequester }
                    )
                }

                // 15. Harici Oynatıcı (opsiyonel)
                if (showExternalPlayerAction) {
                    TvVodControlButton(
                        icon = Icons.Default.Cast,
                        label = stringResource(R.string.player_open_in_external_player),
                        onClick = onOpenExternalPlayer,
                        modifier = Modifier.focusProperties { down = quickActionsFocusRequester }
                    )
                }
            }
        }
    }
}

/**
 * Centralized VOD Color Tokens:
 * - Primary: Indigo / Violet
 * - Secondary / Border: Slim Cyan / Blue
 * - Track & Background: Deep navy / black glass
 */
private object VodControlsColors {
    val DockBackground = Color(0xFF070B14).copy(alpha = 0.88f)
    val DockBorderGradient = listOf(
        Color(0xFF2563EB).copy(alpha = 0.50f),
        Color(0xFF6366F1).copy(alpha = 0.40f),
        Color(0xFF00E5FF).copy(alpha = 0.30f)
    )
    val PrimaryIndigo = Color(0xFF6366F1)
    val PrimaryViolet = Color(0xFF8B5CF6)
    val ProgressGradient = listOf(
        Color(0xFF4F46E5),
        Color(0xFF6366F1),
        Color(0xFF8B5CF6)
    )
    val TrackBackground = Color(0xFF1E293B).copy(alpha = 0.70f)
    val ThumbWhite = Color(0xFFFFFFFF)
    val ThumbGlow = Color(0xFF818CF8)

    val ButtonNormalContainer = Color(0xFF0F172A).copy(alpha = 0.60f)
    val ButtonNormalBorder = Color.White.copy(alpha = 0.10f)
    val ButtonFocusedContainer = Color(0xFF4338CA).copy(alpha = 0.35f)
    val ButtonFocusedBorder = Color(0xFF818CF8)

    val PrimaryButtonContainer = Color(0xFF4F46E5).copy(alpha = 0.28f)
    val PrimaryButtonBorder = Color(0xFF6366F1).copy(alpha = 0.55f)
    val PrimaryButtonFocusedContainer = Color(0xFF6366F1).copy(alpha = 0.45f)
    val PrimaryButtonFocusedBorder = Color(0xFFA5B4FC)

    val TextPrimary = Color(0xFFF8FAFC)
    val TextSecondary = Color(0xFF94A3B8)
    val HintText = Color(0xFF94A3B8).copy(alpha = 0.70f)
}

/**
 * TV-remote-first VOD control button matching the reference design:
 * - 1.08x scale on focus + glowing luminous purple/indigo border
 * - Clean layout without jumps
 * - Circular badge dot if active
 * - Primary button (Play/Pause) is styled with prominent rounded container and distinct focus glow
 */
@Composable
private fun TvVodControlButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
    isPrimary: Boolean = false,
    accentColor: Color = Color.White,
    badgeActive: Boolean = false,
    focusRequester: FocusRequester? = null,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val tier = com.kaynanamtv.app.ui.theme.LocalVisualEffectsProfile.current.tier
    val scaleAnim = if (tier.isFocusScaleEnabled) {
        animateFloatAsState(
            targetValue = if (isFocused) 1.08f else 1.0f,
            label = "vodCtrlScale"
        ).value
    } else {
        1.0f
    }
    val focusColor = if (isPrimary) VodControlsColors.PrimaryIndigo else accentColor

    val surfaceModifier = modifier
        .scale(scaleAnim)
        .let { if (focusRequester != null) it.focusRequester(focusRequester) else it }
        .onFocusChanged { isFocused = it.isFocused }
        .semantics { this.contentDescription = label }

    TvClickableSurface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isPrimary) VodControlsColors.PrimaryButtonContainer else VodControlsColors.ButtonNormalContainer,
            focusedContainerColor = if (isPrimary) VodControlsColors.PrimaryButtonFocusedContainer else VodControlsColors.ButtonFocusedContainer
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                border = BorderStroke(
                    1.dp,
                    if (isPrimary) VodControlsColors.PrimaryButtonBorder else VodControlsColors.ButtonNormalBorder
                ),
                shape = RoundedCornerShape(16.dp)
            ),
            focusedBorder = Border(
                border = BorderStroke(
                    2.2.dp,
                    if (isPrimary) VodControlsColors.PrimaryButtonFocusedBorder else VodControlsColors.ButtonFocusedBorder
                ),
                shape = RoundedCornerShape(16.dp)
            )
        ),
        modifier = surfaceModifier
    ) {
        Box(contentAlignment = Alignment.Center) {
            Column(
                modifier = Modifier.padding(
                    horizontal = if (isPrimary) 22.dp else 14.dp,
                    vertical = if (isPrimary) 13.dp else 10.dp
                ),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = if (isFocused) (if (isPrimary) Color.White else focusColor) else (if (isPrimary) Color.White else VodControlsColors.TextPrimary),
                        modifier = Modifier.size(if (isPrimary) 26.dp else 22.dp)
                    )
                    if (badgeActive) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(6.dp)
                                .background(VodControlsColors.PrimaryIndigo, CircleShape)
                        )
                    }
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall.copy(
                        fontWeight = if (isFocused || isPrimary) FontWeight.Bold else FontWeight.Medium
                    ),
                    color = if (isFocused) (if (isPrimary) Color.White else focusColor) else VodControlsColors.TextSecondary,
                    maxLines = 1
                )
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
    val tier = com.kaynanamtv.app.ui.theme.LocalVisualEffectsProfile.current.tier
    val scaleAnim = if (tier.isFocusScaleEnabled) {
        animateFloatAsState(
            targetValue = if (isFocused) 1.10f else 1.0f,
            label = "tvCtrlScale"
        ).value
    } else {
        1.0f
    }
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
