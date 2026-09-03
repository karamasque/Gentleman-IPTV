package com.kaynanamtv.player

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import androidx.media3.common.text.Cue
import com.kaynanamtv.domain.model.AudioOutputPreference
import com.kaynanamtv.domain.model.DecoderMode
import com.kaynanamtv.domain.model.PlaybackBufferMode
import com.kaynanamtv.domain.model.PlayerSurfaceMode
import com.kaynanamtv.domain.model.StreamInfo
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
import kotlinx.coroutines.flow.update
import org.videolan.libvlc.LibVLC
import org.videolan.libvlc.Media
import org.videolan.libvlc.MediaPlayer
import org.videolan.libvlc.interfaces.IMedia
import java.io.OutputStream

class VlcPlayerEngine(
    private val context: Context
) : PlayerEngine {

    companion object {
        private const val TAG = "VlcPlayerEngine"
    }

    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private var libVlc: LibVLC? = null
    private var mediaPlayer: MediaPlayer? = null
    private var attachedRenderView: View? = null

    private var isDisposed = false
    private var rememberMuted = false
    private var rememberedVolumeLevel = 1.0f

    private val _playbackState = MutableStateFlow(PlaybackState.IDLE)
    override val playbackState: StateFlow<PlaybackState> = _playbackState.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _currentPosition = MutableStateFlow(0L)
    override val currentPosition: StateFlow<Long> = _currentPosition.asStateFlow()

    private val _duration = MutableStateFlow(0L)
    override val duration: StateFlow<Long> = _duration.asStateFlow()

    private val _videoFormat = MutableStateFlow(VideoFormat(0, 0, 0f))
    override val videoFormat: StateFlow<VideoFormat> = _videoFormat.asStateFlow()

    private val _error = MutableSharedFlow<PlayerError?>(replay = 1)
    override val error: Flow<PlayerError?> = _error.asSharedFlow()

    private val _retryStatus = MutableStateFlow<PlayerRetryStatus?>(null)
    override val retryStatus: StateFlow<PlayerRetryStatus?> = _retryStatus.asStateFlow()

    private val _playerStats = MutableStateFlow(PlayerStats(renderSurfaceType = "SURFACE_VIEW"))
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

    private val _renderSurfaceType = MutableStateFlow(PlayerRenderSurfaceType.SURFACE_VIEW)
    override val renderSurfaceType: StateFlow<PlayerRenderSurfaceType> = _renderSurfaceType.asStateFlow()

    private val _mediaTitle = MutableStateFlow<String?>(null)
    override val mediaTitle: StateFlow<String?> = _mediaTitle.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    override val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _audioFocusDenied = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val audioFocusDenied: Flow<Unit> = _audioFocusDenied.asSharedFlow()

    private var isSurfaceReady = false
    private val voutCallback = object : org.videolan.libvlc.interfaces.IVLCVout.Callback {
        override fun onSurfacesCreated(vlcVout: org.videolan.libvlc.interfaces.IVLCVout?) {
            Log.i(TAG, "[VLC] onSurfacesCreated: Surface attached and ready for video rendering")
            isSurfaceReady = true
        }

        override fun onSurfacesDestroyed(vlcVout: org.videolan.libvlc.interfaces.IVLCVout?) {
            Log.i(TAG, "[VLC] onSurfacesDestroyed: Surface detached")
            isSurfaceReady = false
        }
    }

    private fun getOrCreatePlayer(): MediaPlayer {
        mediaPlayer?.let { return it }

        val options = ArrayList<String>().apply {
            add("--no-sub-autodetect-file")
            add("--network-caching=2000")
            add("--live-caching=2000")
            add("--file-caching=2000")
            add("--sout-mux-caching=2000")
            add("--http-reconnect")
            add("--no-drop-late-frames")
            add("--no-skip-frames")
            add("-v")
        }

        val vlc = LibVLC(context.applicationContext, options).also { libVlc = it }
        val player = MediaPlayer(vlc).also { mediaPlayer = it }

        player.vlcVout.addCallback(voutCallback)

        player.setEventListener { event ->
            if (isDisposed) return@setEventListener
            when (event.type) {
                MediaPlayer.Event.Buffering -> {
                    _playerStats.update { it.copy(bufferedDurationMs = (event.buffering * 20).toLong()) }
                    if (event.buffering < 100f) {
                        _playbackState.value = PlaybackState.BUFFERING
                    } else {
                        _playbackState.value = PlaybackState.READY
                    }
                }
                MediaPlayer.Event.Playing -> {
                    _isPlaying.value = true
                    _playbackState.value = PlaybackState.READY
                    refreshTracks()
                }
                MediaPlayer.Event.Paused -> {
                    _isPlaying.value = false
                }
                MediaPlayer.Event.Stopped -> {
                    _isPlaying.value = false
                    _playbackState.value = PlaybackState.IDLE
                }
                MediaPlayer.Event.EndReached -> {
                    _isPlaying.value = false
                    _playbackState.value = PlaybackState.ENDED
                }
                MediaPlayer.Event.EncounteredError -> {
                    _isPlaying.value = false
                    _playbackState.value = PlaybackState.ERROR
                    _error.tryEmit(PlayerError.SourceError("VLC oynatma hatası"))
                }
                MediaPlayer.Event.TimeChanged -> {
                    _currentPosition.value = event.timeChanged.coerceAtLeast(0L)
                }
                MediaPlayer.Event.LengthChanged -> {
                    _duration.value = event.lengthChanged.coerceAtLeast(0L)
                }
                MediaPlayer.Event.Vout -> {
                    refreshTracks()
                }
            }
        }

        attachedRenderView?.let { bindRenderView(it, PlayerSurfaceResizeMode.FIT) }
        return player
    }

    private fun refreshTracks() {
        val player = mediaPlayer ?: return
        runCatching {
            val audioTracks = player.audioTracks?.map { track ->
                PlayerTrack(
                    id = track.id.toString(),
                    name = track.name ?: "Ses #${track.id}",
                    language = null,
                    type = TrackType.AUDIO,
                    isSelected = track.id == player.audioTrack
                )
            } ?: emptyList()
            _availableAudioTracks.value = audioTracks

            val spuTracks = player.spuTracks?.map { track ->
                PlayerTrack(
                    id = track.id.toString(),
                    name = track.name ?: "Altyazı #${track.id}",
                    language = null,
                    type = TrackType.TEXT,
                    isSelected = track.id == player.spuTrack
                )
            } ?: emptyList()
            _availableSubtitleTracks.value = spuTracks

            val currentVTrack = player.currentVideoTrack
            if (currentVTrack != null) {
                _videoFormat.value = VideoFormat(
                    width = currentVTrack.width,
                    height = currentVTrack.height,
                    frameRate = currentVTrack.frameRateNum.toFloat() / (currentVTrack.frameRateDen.takeIf { it > 0 } ?: 1)
                )
                _playerStats.update {
                    it.copy(
                        width = currentVTrack.width,
                        height = currentVTrack.height,
                        videoCodec = currentVTrack.codec ?: "libvlc"
                    )
                }
            }
        }
    }

    override fun prepare(streamInfo: StreamInfo) {
        if (isDisposed) return
        val player = getOrCreatePlayer()
        val vlc = libVlc ?: return

        _playbackState.value = PlaybackState.BUFFERING
        _isPlaying.value = false
        _currentPosition.value = 0L
        _duration.value = 0L

        val mediaUri = Uri.parse(streamInfo.url)
        val media = Media(vlc, mediaUri).apply {
            streamInfo.userAgent?.takeIf { it.isNotBlank() }?.let { ua ->
                addOption(":http-user-agent=$ua")
            }
            val referer = streamInfo.headers["Referer"] ?: streamInfo.headers["referer"]
            referer?.takeIf { it.isNotBlank() }?.let { ref ->
                addOption(":http-referrer=$ref")
            }
            streamInfo.headers.forEach { (key, value) ->
                if (!key.equals("Referer", ignoreCase = true) && !key.equals("User-Agent", ignoreCase = true)) {
                    addOption(":http-header=$key: $value")
                }
            }
            // Auto hardware acceleration with automatic software fallback if codec/surface is incompatible
            setHWDecoderEnabled(true, false)
        }

        player.media = media
        media.release()
        player.play()
    }

    override fun renewStreamUrl(streamInfo: StreamInfo) {
        prepare(streamInfo)
    }

    override fun play() {
        if (isDisposed) return
        mediaPlayer?.play()
    }

    override fun pause() {
        if (isDisposed) return
        mediaPlayer?.pause()
    }

    override fun stop() {
        if (isDisposed) return
        mediaPlayer?.stop()
        _playbackState.value = PlaybackState.IDLE
        _isPlaying.value = false
    }

    override fun seekTo(positionMs: Long) {
        if (isDisposed) return
        mediaPlayer?.time = positionMs.coerceAtLeast(0L)
    }

    override fun seekForward(ms: Long) {
        if (isDisposed) return
        val current = _currentPosition.value
        val dur = _duration.value
        val target = if (dur > 0) (current + ms).coerceAtMost(dur) else current + ms
        seekTo(target)
    }

    override fun seekBackward(ms: Long) {
        if (isDisposed) return
        val current = _currentPosition.value
        val target = (current - ms).coerceAtLeast(0L)
        seekTo(target)
    }

    override fun setDecoderModes(audioMode: DecoderMode, videoMode: DecoderMode) {
        // LibVLC automatically handles hardware decoding based on availability.
    }

    override fun setPlaybackBufferMode(mode: PlaybackBufferMode) {
        // Buffer modes managed through caching options.
    }

    override fun setSurfaceMode(mode: PlayerSurfaceMode) {
        // Managed in view creation.
    }

    override fun setVodHttpProtocolMode(mode: VodHttpProtocolMode) {
        // Managed through standard HTTP options.
    }

    override fun setMediaSessionEnabled(enabled: Boolean) {
        // Isolated engine, session optional.
    }

    override fun setFastRetryOnTransientFailures(enabled: Boolean) {
        // LibVLC has internal reconnect.
    }

    override fun setVolume(volume: Float) {
        if (isDisposed) return
        rememberedVolumeLevel = volume.coerceIn(0f, 1f)
        if (!_isMuted.value) {
            mediaPlayer?.volume = (rememberedVolumeLevel * 100).toInt()
        }
    }

    override fun setMuted(muted: Boolean) {
        if (isDisposed) return
        _isMuted.value = muted
        if (muted) {
            mediaPlayer?.volume = 0
        } else {
            mediaPlayer?.volume = (rememberedVolumeLevel * 100).toInt()
        }
    }

    override fun toggleMute() {
        setMuted(!_isMuted.value)
    }

    override fun setPlaybackSpeed(speed: Float) {
        if (isDisposed) return
        val safeSpeed = speed.coerceIn(0.25f, 4.0f)
        _playbackSpeed.value = safeSpeed
        mediaPlayer?.rate = safeSpeed
    }

    override fun setAudioVideoSyncEnabled(enabled: Boolean) {
        _audioVideoSyncEnabled.value = enabled
    }

    override fun setAudioVideoOffsetMs(offsetMs: Int) {
        val clamped = offsetMs.coerceIn(AUDIO_VIDEO_OFFSET_MIN_MS, AUDIO_VIDEO_OFFSET_MAX_MS)
        _audioVideoOffsetMs.value = clamped
        mediaPlayer?.setAudioDelay(clamped * 1000L) // microseconds
    }

    override fun setAudioOutputPreference(preference: AudioOutputPreference) {
        // Default output path.
    }

    override fun setCompatibilityMemoryEnabled(enabled: Boolean) {}

    override fun clearLearnedPlaybackCompatibility() {}

    override fun startLiveTimeshift(streamInfo: StreamInfo, channelKey: String, config: TimeshiftConfig) {
        // Timeshift not active in VLC fallback.
    }

    override fun stopLiveTimeshift() {}

    override fun seekToLiveEdge() {
        val dur = _duration.value
        if (dur > 0) seekTo(dur)
    }

    override fun pauseTimeshift() {}

    override fun resumeTimeshift() {}

    override fun setPreferredAudioLanguage(languageTag: String?) {}

    override fun setSubtitleStyle(style: PlayerSubtitleStyle) {}

    override fun setNetworkQualityPreferences(wifiMaxHeight: Int?, ethernetMaxHeight: Int?) {}

    override fun selectAudioTrack(trackId: String) {
        val id = trackId.toIntOrNull() ?: return
        mediaPlayer?.setAudioTrack(id)
        refreshTracks()
    }

    override fun selectVideoTrack(trackId: String) {
        // Handled automatically.
    }

    override fun selectSubtitleTrack(trackId: String?) {
        val id = trackId?.toIntOrNull() ?: -1
        mediaPlayer?.setSpuTrack(id)
        refreshTracks()
    }

    override fun addExternalSubtitle(subtitleUri: Uri, language: String) {
        mediaPlayer?.addSlave(IMedia.Slave.Type.Subtitle, subtitleUri, true)
    }

    override fun setInjectedSubtitleCues(cues: List<Cue>) {}

    override fun clearInjectedSubtitleCues() {}

    override fun setLiveAudioTap(tap: LiveAudioTap?) {}

    override fun startTeeCapture(sink: OutputStream): Boolean = false

    override fun stopTeeCapture() {}

    override fun setScrubbingMode(enabled: Boolean) {}

    override fun preload(streamInfo: StreamInfo?) {}

    override fun createRenderView(
        context: Context,
        resizeMode: PlayerSurfaceResizeMode,
        surfaceType: PlayerRenderSurfaceType
    ): View {
        return if (surfaceType == PlayerRenderSurfaceType.TEXTURE_VIEW) {
            TextureView(context)
        } else {
            SurfaceView(context)
        }
    }

    override fun bindRenderView(renderView: View, resizeMode: PlayerSurfaceResizeMode) {
        val player = mediaPlayer ?: getOrCreatePlayer()
        val vout = player.vlcVout
        if (attachedRenderView === renderView && vout.areViewsAttached()) {
            return
        }
        if (vout.areViewsAttached()) {
            vout.detachViews()
        }
        attachedRenderView = renderView
        if (renderView is SurfaceView) {
            vout.setVideoView(renderView)
        } else if (renderView is TextureView) {
            vout.setVideoView(renderView)
        }
        if (!vout.areViewsAttached()) {
            vout.attachViews()
        }
    }

    override fun clearRenderBinding() {
        attachedRenderView = null
        runCatching {
            mediaPlayer?.vlcVout?.detachViews()
        }
    }

    override fun releaseRenderView(renderView: View) {
        if (attachedRenderView === renderView) {
            clearRenderBinding()
        }
    }

    override fun release() {
        if (isDisposed) return
        isDisposed = true
        runCatching {
            mediaPlayer?.let { player ->
                player.setEventListener(null)
                val vout = player.vlcVout
                vout?.removeCallback(voutCallback)
                if (vout?.areViewsAttached() == true) {
                    vout.detachViews()
                }
                player.stop()
                player.release()
            }
            mediaPlayer = null
            libVlc?.release()
            libVlc = null
        }
        attachedRenderView = null
        isSurfaceReady = false
        engineScope.cancel()
        _isPlaying.value = false
        _playbackState.value = PlaybackState.IDLE
    }

    override fun resetForReuse() {
        release()
        isDisposed = false
    }
}
