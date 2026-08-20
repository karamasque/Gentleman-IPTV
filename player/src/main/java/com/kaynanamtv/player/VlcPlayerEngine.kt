package com.kaynanamtv.player

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import androidx.media3.common.text.Cue
import com.kaynanamtv.domain.model.AudioOutputPreference
import com.kaynanamtv.domain.model.DecoderMode
import com.kaynanamtv.domain.model.PlaybackBufferMode
import com.kaynanamtv.domain.model.PlayerSurfaceMode
import com.kaynanamtv.domain.model.StreamInfo
import com.kaynanamtv.domain.model.StreamType
import com.kaynanamtv.domain.model.VideoFormat
import com.kaynanamtv.domain.model.VodHttpProtocolMode
import com.kaynanamtv.player.timeshift.LiveTimeshiftState
import com.kaynanamtv.player.timeshift.TimeshiftConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.util.VLCVideoLayout
import java.io.OutputStream
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject

/**
 * Dahili LibVLC Oynatıcı Motoru (KaynanamTV Embedded VLC Player Engine)
 *
 * Harici uygulama veya Intent gerektirmeden doğrudan APK içinde çalışır.
 * MPEG-TS, HLS, Xtream, VOD, Dizi ve Canlı TV formatlarını yerel LibVLC kütüphanesiyle çözer.
 */
class VlcPlayerEngine @Inject constructor(
    private val context: Context
) : PlayerEngine {

    companion object {
        private const val TAG = "VlcPlayerEngine"
        private val nextInstanceId = AtomicLong(1L)
        private val activeInstances = java.util.Collections.newSetFromMap(ConcurrentHashMap<VlcPlayerEngine, Boolean>())

        fun stopAllActivePlayback() {
            Log.i(TAG, "[PLAYER_INSTANCE] VLC stopAllActivePlayback called across ${activeInstances.size} active instances")
            activeInstances.forEach { engine ->
                runCatching {
                    engine.stop()
                }.onFailure { Log.e(TAG, "Failed to stop VLC engine id=${engine.instanceId}", it) }
            }
        }

        fun getActivePlayingInstanceCount(): Int {
            return activeInstances.count { it.isPlaying.value || it.playbackState.value != PlaybackState.IDLE }
        }
    }

    val instanceId: Long = nextInstanceId.getAndIncrement()
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())

    private var libVLC: LibVLC? = null
    private var mediaPlayer: MediaPlayer? = null
    private var currentMedia: Media? = null

    // Views
    private var boundVideoLayout: VLCVideoLayout? = null
    private var boundSurfaceView: SurfaceView? = null
    private var boundTextureView: TextureView? = null
    private var activeResizeMode: PlayerSurfaceResizeMode = PlayerSurfaceResizeMode.FIT

    private var currentStreamInfo: StreamInfo? = null
    private var savedVolume: Float = 1.0f
    private var isMutedInternal: Boolean = false
    private var isHardwareAccel: Boolean = true

    // State Flows
    private val _playbackState = MutableStateFlow(PlaybackState.IDLE)
    override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    override val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    override val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _videoFormat = MutableStateFlow(VideoFormat(width = 0, height = 0))
    override val videoFormat: StateFlow<VideoFormat> = _videoFormat.asStateFlow()

    private val _error = MutableSharedFlow<PlayerError?>(replay = 1)
    override val error: Flow<PlayerError?> = _error.asSharedFlow()

    private val _retryStatus = MutableStateFlow<PlayerRetryStatus?>(null)
    override val retryStatus: StateFlow<PlayerRetryStatus?> = _retryStatus.asStateFlow()

    private val _playerStats = MutableStateFlow(PlayerStats())
    override val playerStats: StateFlow<PlayerStats> = _playerStats.asStateFlow()

    private val _availableAudioTracks = MutableStateFlow<List<PlayerTrack>>(emptyList())
    override val availableAudioTracks: StateFlow<List<PlayerTrack>> = _availableAudioTracks.asStateFlow()

    private val _availableSubtitleTracks = MutableStateFlow<List<PlayerTrack>>(emptyList())
    override val availableSubtitleTracks: StateFlow<List<PlayerTrack>> = _availableSubtitleTracks.asStateFlow()

    private val _availableVideoTracks = MutableStateFlow<List<PlayerTrack>>(emptyList())
    override val availableVideoTracks: StateFlow<List<PlayerTrack>> = _availableVideoTracks.asStateFlow()

    private val _playbackSpeed = MutableStateFlow(1.0f)
    override val playbackSpeed: StateFlow<Float> = _playbackSpeed.asStateFlow()

    private val _audioVideoOffsetMs = MutableStateFlow(0)
    override val audioVideoOffsetMs: StateFlow<Int> = _audioVideoOffsetMs.asStateFlow()

    private val _audioVideoSyncEnabled = MutableStateFlow(false)
    override val audioVideoSyncEnabled: StateFlow<Boolean> = _audioVideoSyncEnabled.asStateFlow()

    private val _timeshiftState = MutableStateFlow(LiveTimeshiftState())
    override val timeshiftState: StateFlow<LiveTimeshiftState> = _timeshiftState.asStateFlow()

    private val _renderSurfaceType = MutableStateFlow(PlayerRenderSurfaceType.AUTO)
    override val renderSurfaceType: StateFlow<PlayerRenderSurfaceType> = _renderSurfaceType.asStateFlow()

    private val _mediaTitle = MutableStateFlow<String?>(null)
    override val mediaTitle: StateFlow<String?> = _mediaTitle.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    override val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _audioFocusDenied = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val audioFocusDenied: Flow<Unit> = _audioFocusDenied.asSharedFlow()

    init {
        activeInstances.add(this)
        Log.i(TAG, "[PLAYER_INSTANCE] VLC created id=$instanceId activeCount=${activeInstances.size}")
    }

    private fun getOrCreateLibVLC(isLive: Boolean): LibVLC {
        val existing = libVLC
        if (existing != null && !existing.isReleased) {
            return existing
        }

        val isTv = PlayerEngineFactory.isTvDevice(context)
        val options = arrayListOf(
            "--no-drop-late-frames",
            "--no-skip-frames",
            "--audio-time-stretch"
        )

        if (isHardwareAccel) {
            options.add("--avcodec-hw=any")
            options.add("--codec=mediacodec_ndk,mediacodec_jni,all")
        }

        // Cache Değerleri (TV ve Mi Box cihazlarında donmaları ve arabelleğe alma takılmalarını engellemek için optimize edilmiştir)
        if (isLive) {
            val liveCache = if (isTv) "3000" else "1500"
            options.add("--network-caching=$liveCache")
            options.add("--live-caching=$liveCache")
            options.add("--clock-jitter=0")
            options.add("--clock-synchro=0")
        } else {
            val vodCache = if (isTv) "5000" else "3000"
            options.add("--network-caching=$vodCache")
            options.add("--file-caching=3000")
        }

        val newLibVlc = LibVLC(context.applicationContext, options)
        libVLC = newLibVlc
        return newLibVlc
    }

    private fun ensureMediaPlayer(isLive: Boolean): MediaPlayer {
        val existing = mediaPlayer
        if (existing != null && !existing.isReleased) {
            return existing
        }

        val vlc = getOrCreateLibVLC(isLive)
        val player = MediaPlayer(vlc)

        player.setEventListener { event ->
            mainHandler.post {
                handleVlcEvent(event)
            }
        }

        mediaPlayer = player
        attachCurrentViewsToPlayer(player)
        return player
    }

    private fun attachCurrentViewsToPlayer(player: MediaPlayer) {
        val layout = boundVideoLayout
        if (layout != null) {
            if (!player.vlcVout.areViewsAttached()) {
                player.attachViews(layout, null, true, false)
            }
            applyResizeModeToPlayer(player, activeResizeMode)
            return
        }

        val surfaceView = boundSurfaceView
        if (surfaceView != null) {
            if (!player.vlcVout.areViewsAttached()) {
                player.vlcVout.setVideoView(surfaceView)
                player.vlcVout.attachViews()
            }
            return
        }

        val textureView = boundTextureView
        if (textureView != null) {
            if (!player.vlcVout.areViewsAttached()) {
                player.vlcVout.setVideoView(textureView)
                player.vlcVout.attachViews()
            }
            return
        }
    }

    private fun handleVlcEvent(event: MediaPlayer.Event) {
        val player = mediaPlayer ?: return
        when (event.type) {
            MediaPlayer.Event.Opening -> {
                _playbackState.value = PlaybackState.BUFFERING
            }
            MediaPlayer.Event.Buffering -> {
                if (event.buffering < 100f) {
                    _playbackState.value = PlaybackState.BUFFERING
                } else if (player.isPlaying) {
                    _playbackState.value = PlaybackState.READY
                    _isPlaying.value = true
                }
            }
            MediaPlayer.Event.Playing -> {
                _playbackState.value = PlaybackState.READY
                _isPlaying.value = true
                updateTracksFromPlayer()
            }
            MediaPlayer.Event.Paused -> {
                _playbackState.value = PlaybackState.READY
                _isPlaying.value = false
            }
            MediaPlayer.Event.Stopped -> {
                _playbackState.value = PlaybackState.IDLE
                _isPlaying.value = false
            }
            MediaPlayer.Event.EndReached -> {
                _playbackState.value = PlaybackState.ENDED
                _isPlaying.value = false
                _currentPosition.value = _duration.value
            }
            MediaPlayer.Event.EncounteredError -> {
                _playbackState.value = PlaybackState.ERROR
                _isPlaying.value = false
                val err = PlayerError.SourceError("VLC Oynatma Hatası")
                _error.tryEmit(err)
            }
            MediaPlayer.Event.TimeChanged -> {
                _currentPosition.value = event.timeChanged
            }
            MediaPlayer.Event.LengthChanged -> {
                _duration.value = event.lengthChanged
            }
            MediaPlayer.Event.Vout -> {
                val currentTrack = player.currentVideoTrack
                if (currentTrack != null) {
                    val width = currentTrack.width
                    val height = currentTrack.height
                    val fps = if (currentTrack.frameRateNum > 0 && currentTrack.frameRateDen > 0) {
                        currentTrack.frameRateNum.toFloat() / currentTrack.frameRateDen.toFloat()
                    } else 0f

                    _videoFormat.value = VideoFormat(
                        width = width,
                        height = height,
                        frameRate = fps,
                        bitrate = currentTrack.bitrate,
                        codecV = currentTrack.codec
                    )
                }
            }
        }
    }

    private fun updateTracksFromPlayer() {
        val player = mediaPlayer ?: return
        try {
            // Audio Tracks
            val audioTracks = player.audioTracks?.mapIndexed { index, track ->
                PlayerTrack(
                    id = track.id.toString(),
                    name = track.name.ifBlank { "Ses #${index + 1}" },
                    language = track.name,
                    type = TrackType.AUDIO,
                    isSelected = (track.id == player.audioTrack)
                )
            } ?: emptyList()
            _availableAudioTracks.value = audioTracks

            // Subtitle Tracks
            val spuTracks = player.spuTracks?.mapIndexed { index, track ->
                PlayerTrack(
                    id = track.id.toString(),
                    name = track.name.ifBlank { "Altyazı #${index + 1}" },
                    language = track.name,
                    type = TrackType.TEXT,
                    isSelected = (track.id == player.spuTrack)
                )
            } ?: emptyList()
            _availableSubtitleTracks.value = spuTracks
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update tracks: ${e.message}")
        }
    }

    override fun prepare(streamInfo: StreamInfo) {
        // MUTUAL EXCLUSIVITY: Stop any active Media3 or other VLC playback immediately
        Media3PlayerEngine.stopAllActivePlayback()
        stop()

        currentStreamInfo = streamInfo
        val isLive = streamInfo.streamType == StreamType.HLS || streamInfo.streamType == StreamType.MPEG_TS || streamInfo.streamType == StreamType.RTSP
        val player = ensureMediaPlayer(isLive)

        val vlc = getOrCreateLibVLC(isLive)
        val mediaUri = Uri.parse(streamInfo.url)
        val media = Media(vlc, mediaUri).apply {
            if (isLive) {
                addOption(":network-caching=1500")
                addOption(":live-caching=1500")
                addOption(":clock-jitter=0")
                addOption(":clock-synchro=0")
            } else {
                addOption(":network-caching=3000")
                addOption(":file-caching=2000")
            }
            if (isHardwareAccel) {
                setHWDecoderEnabled(true, true)
            }
        }

        currentMedia?.release()
        currentMedia = media
        player.media = media

        _playbackState.value = PlaybackState.BUFFERING
        player.play()
    }

    override fun renewStreamUrl(streamInfo: StreamInfo) {
        prepare(streamInfo)
    }

    override fun play() {
        // Mutual Exclusivity
        Media3PlayerEngine.stopAllActivePlayback()
        mediaPlayer?.play()
    }

    override fun pause() {
        mediaPlayer?.pause()
    }

    override fun stop() {
        try {
            mediaPlayer?.stop()
            currentMedia?.release()
            currentMedia = null
            _playbackState.value = PlaybackState.IDLE
            _isPlaying.value = false
        } catch (e: Exception) {
            Log.w(TAG, "Error stopping VLC player: ${e.message}")
        }
    }

    override fun seekTo(positionMs: Long) {
        mediaPlayer?.let { player ->
            if (player.isSeekable) {
                player.time = positionMs
                _currentPosition.value = positionMs
            }
        }
    }

    override fun seekForward(ms: Long) {
        val target = (_currentPosition.value + ms).coerceAtMost(_duration.value.takeIf { it > 0 } ?: Long.MAX_VALUE)
        seekTo(target)
    }

    override fun seekBackward(ms: Long) {
        val target = (_currentPosition.value - ms).coerceAtLeast(0L)
        seekTo(target)
    }

    override fun setDecoderModes(audioMode: DecoderMode, videoMode: DecoderMode) {
        isHardwareAccel = (videoMode != DecoderMode.SOFTWARE)
    }

    override fun setPlaybackBufferMode(mode: PlaybackBufferMode) {
        // Buffer modes map into VLC network caching options during prepare
    }

    override fun setSurfaceMode(mode: PlayerSurfaceMode) {}

    override fun setVodHttpProtocolMode(mode: VodHttpProtocolMode) {}

    override fun setMediaSessionEnabled(enabled: Boolean) {}

    override fun setFastRetryOnTransientFailures(enabled: Boolean) {}

    override fun setVolume(volume: Float) {
        savedVolume = volume
        if (!isMutedInternal) {
            val vlcVol = (volume * 100).toInt().coerceIn(0, 100)
            mediaPlayer?.volume = vlcVol
        }
    }

    override fun setMuted(muted: Boolean) {
        isMutedInternal = muted
        _isMuted.value = muted
        if (muted) {
            mediaPlayer?.volume = 0
        } else {
            setVolume(savedVolume)
        }
    }

    override fun toggleMute() {
        setMuted(!isMutedInternal)
    }

    override fun setPlaybackSpeed(speed: Float) {
        _playbackSpeed.value = speed
        mediaPlayer?.rate = speed
    }

    override fun setAudioVideoSyncEnabled(enabled: Boolean) {
        _audioVideoSyncEnabled.value = enabled
    }

    override fun setAudioVideoOffsetMs(offsetMs: Int) {
        _audioVideoOffsetMs.value = offsetMs
        // VLC audio delay in microseconds
        mediaPlayer?.setAudioDelay(offsetMs * 1000L)
    }

    override fun setAudioOutputPreference(preference: AudioOutputPreference) {}

    override fun setCompatibilityMemoryEnabled(enabled: Boolean) {}

    override fun clearLearnedPlaybackCompatibility() {}

    override fun startLiveTimeshift(streamInfo: StreamInfo, channelKey: String, config: TimeshiftConfig) {}

    override fun stopLiveTimeshift() {}

    override fun seekToLiveEdge() {
        mediaPlayer?.let { player ->
            if (player.isSeekable) {
                player.time = player.length
            }
        }
    }

    override fun pauseTimeshift() {
        pause()
    }

    override fun resumeTimeshift() {
        play()
    }

    override fun setPreferredAudioLanguage(languageTag: String?) {}

    override fun setSubtitleStyle(style: PlayerSubtitleStyle) {}

    override fun setNetworkQualityPreferences(wifiMaxHeight: Int?, ethernetMaxHeight: Int?) {}

    override fun selectAudioTrack(trackId: String) {
        try {
            val id = trackId.toIntOrNull() ?: return
            mediaPlayer?.setAudioTrack(id)
            updateTracksFromPlayer()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to select audio track: $trackId")
        }
    }

    override fun selectVideoTrack(trackId: String) {}

    override fun selectSubtitleTrack(trackId: String?) {
        try {
            if (trackId == null) {
                mediaPlayer?.setSpuTrack(-1)
            } else {
                val id = trackId.toIntOrNull() ?: return
                mediaPlayer?.setSpuTrack(id)
            }
            updateTracksFromPlayer()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to select subtitle track: $trackId")
        }
    }

    override fun addExternalSubtitle(subtitleUri: Uri, language: String) {
        try {
            mediaPlayer?.addSlave(org.videolan.libvlc.interfaces.IMedia.Slave.Type.Subtitle, subtitleUri, true)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to add subtitle slave: ${e.message}")
        }
    }

    override fun setInjectedSubtitleCues(cues: List<Cue>) {}

    override fun clearInjectedSubtitleCues() {}

    override fun setLiveAudioTap(tap: LiveAudioTap?) {}

    override fun release() {
        activeInstances.remove(this)
        stop()
        try {
            mediaPlayer?.detachViews()
            mediaPlayer?.release()
            mediaPlayer = null
            libVLC?.release()
            libVLC = null
        } catch (e: Exception) {
            Log.w(TAG, "Error releasing VLC engine: ${e.message}")
        }
        engineScope.cancel()
    }

    override fun startTeeCapture(sink: OutputStream): Boolean = false

    override fun stopTeeCapture() {}

    override fun setScrubbingMode(enabled: Boolean) {}

    override fun preload(streamInfo: StreamInfo?) {}

    override fun createRenderView(
        context: Context,
        resizeMode: PlayerSurfaceResizeMode,
        surfaceType: PlayerRenderSurfaceType
    ): View {
        val container = FrameLayout(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }

        val videoLayout = VLCVideoLayout(context).apply {
            layoutParams = FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT
            )
        }
        container.addView(videoLayout)

        bindRenderView(videoLayout, resizeMode)
        return container
    }

    override fun bindRenderView(renderView: View, resizeMode: PlayerSurfaceResizeMode) {
        activeResizeMode = resizeMode

        if (renderView is VLCVideoLayout) {
            boundVideoLayout = renderView
            boundSurfaceView = null
            boundTextureView = null
        } else if (renderView is SurfaceView) {
            boundSurfaceView = renderView
            boundVideoLayout = null
            boundTextureView = null
        } else if (renderView is TextureView) {
            boundTextureView = renderView
            boundVideoLayout = null
            boundSurfaceView = null
        } else if (renderView is ViewGroup) {
            // Find VLCVideoLayout inside container
            for (i in 0 until renderView.childCount) {
                val child = renderView.getChildAt(i)
                if (child is VLCVideoLayout) {
                    boundVideoLayout = child
                    break
                }
            }
        }

        mediaPlayer?.let { player ->
            attachCurrentViewsToPlayer(player)
            if (player.isPlaying) {
                runCatching {
                    val currentTrack = player.videoTrack
                    if (currentTrack != -1) {
                        player.videoTrack = -1
                        player.videoTrack = currentTrack
                    }
                }
            }
        }
    }

    private fun applyResizeModeToPlayer(player: MediaPlayer, mode: PlayerSurfaceResizeMode) {
        when (mode) {
            PlayerSurfaceResizeMode.FIT -> {
                player.aspectRatio = null
                player.scale = 0f
            }
            PlayerSurfaceResizeMode.FILL -> {
                player.aspectRatio = "16:9"
                player.scale = 0f
            }
            PlayerSurfaceResizeMode.ZOOM -> {
                player.aspectRatio = null
                player.scale = 1.25f
            }
        }
    }

    override fun clearRenderBinding() {
        try {
            mediaPlayer?.detachViews()
        } catch (e: Exception) {
            Log.w(TAG, "Error detaching VLC views: ${e.message}")
        }
        boundVideoLayout = null
        boundSurfaceView = null
        boundTextureView = null
    }

    override fun releaseRenderView(renderView: View) {
        clearRenderBinding()
    }
}
