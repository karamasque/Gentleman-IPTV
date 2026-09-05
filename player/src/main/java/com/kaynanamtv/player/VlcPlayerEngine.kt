package com.kaynanamtv.player

import android.content.Context
import android.net.Uri
import android.util.Log
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
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
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
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
    private val context: Context,
    coroutineContext: kotlin.coroutines.CoroutineContext = Dispatchers.Main.immediate
) : PlayerEngine {

    companion object {
        private const val TAG = "VlcPlayerEngine"
        private val instanceCounter = java.util.concurrent.atomic.AtomicInteger(0)
    }

    val instanceTag: String = "VLC-Inst-${instanceCounter.incrementAndGet()}"

    private val engineScope = CoroutineScope(SupervisorJob() + coroutineContext)

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

    private val _renderSurfaceType = MutableStateFlow(PlayerRenderSurfaceType.TEXTURE_VIEW)
    override val renderSurfaceType: StateFlow<PlayerRenderSurfaceType> = _renderSurfaceType.asStateFlow()

    private val _mediaTitle = MutableStateFlow<String?>(null)
    override val mediaTitle: StateFlow<String?> = _mediaTitle.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    override val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _audioFocusDenied = MutableSharedFlow<Unit>(extraBufferCapacity = 1)
    override val audioFocusDenied: Flow<Unit> = _audioFocusDenied.asSharedFlow()

    private var currentVideoDecoderMode: DecoderMode = DecoderMode.AUTO
    private var currentAudioDecoderMode: DecoderMode = DecoderMode.AUTO
    private var pendingSeekPositionMs: Long? = null
    private var currentStreamInfo: StreamInfo? = null

    private val isSurfaceReady = java.util.concurrent.atomic.AtomicBoolean(false)
    private val pendingStartPlayback = java.util.concurrent.atomic.AtomicBoolean(false)
    private val isPlaybackStarted = java.util.concurrent.atomic.AtomicBoolean(false)
    internal val seekInProgress = java.util.concurrent.atomic.AtomicBoolean(false)
    private val seekTokenCounter = java.util.concurrent.atomic.AtomicLong(0L)
    internal var currentSeekToken = 0L
    internal var seekRecoveryAttemptedForToken = 0L
    internal var seekTargetMs = 0L
    internal var seekWasPlaying = false
    internal var seekSavedSpeed = 1.0f
    internal var seekSavedAudioTrack = -1
    internal var seekSavedSpuTrack = -1
    internal var seekStallJob: kotlinx.coroutines.Job? = null
    private var currentResizeMode: PlayerSurfaceResizeMode = PlayerSurfaceResizeMode.FIT
    private val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())

    private fun checkSurfaceReady(): Boolean {
        if (isSurfaceReady.get()) return true
        val player = mediaPlayer
        if (player != null && player.vlcVout.areViewsAttached()) {
            isSurfaceReady.set(true)
            return true
        }
        val view = attachedRenderView ?: return false
        val valid = when (view) {
            is SurfaceView -> view.holder.surface?.isValid == true
            is TextureView -> view.isAvailable
            else -> false
        }
        if (valid) {
            isSurfaceReady.set(true)
        }
        return isSurfaceReady.get()
    }

    private fun startPendingPlaybackIfReady(triggerSource: String) {
        if (isDisposed) return
        val player = mediaPlayer ?: return
        if (player.media == null) return

        if (checkSurfaceReady()) {
            if (pendingStartPlayback.compareAndSet(true, false) || !isPlaybackStarted.get()) {
                isPlaybackStarted.set(true)
                Log.i(TAG, "[$instanceTag] Starting playback (source=$triggerSource, ready=${isSurfaceReady.get()})")
                player.play()
            }
        }
    }

    private val voutCallback = object : org.videolan.libvlc.interfaces.IVLCVout.Callback {
        override fun onSurfacesCreated(vlcVout: org.videolan.libvlc.interfaces.IVLCVout?) {
            Log.i(TAG, "[$instanceTag] onSurfacesCreated: Surface attached and ready for video rendering (thread=${Thread.currentThread().name})")
            isSurfaceReady.set(true)
            mainHandler.post {
                startPendingPlaybackIfReady("onSurfacesCreated")
            }
        }

        override fun onSurfacesDestroyed(vlcVout: org.videolan.libvlc.interfaces.IVLCVout?) {
            Log.i(TAG, "[$instanceTag] [SET_FALSE] onSurfacesDestroyed: Surface detached (thread=${Thread.currentThread().name})")
            isSurfaceReady.set(false)
            pendingStartPlayback.set(false)
            isPlaybackStarted.set(false)
        }
    }

    internal fun handlePlayerEvent(event: MediaPlayer.Event, player: MediaPlayer? = mediaPlayer) {
        if (isDisposed) return
        when (event.type) {
            MediaPlayer.Event.Buffering -> {
                _playerStats.update { it.copy(bufferedDurationMs = (event.buffering * 20).toLong()) }
                if (event.buffering < 100f) {
                    _playbackState.value = PlaybackState.BUFFERING
                } else {
                    seekInProgress.set(false)
                    seekStallJob?.cancel()
                    _playbackState.value = PlaybackState.READY
                }
            }
            MediaPlayer.Event.Playing -> {
                Log.i(TAG, "[$instanceTag] [VLC_EVENT] Playing: isSeekable=${player?.isSeekable} length=${player?.length}ms viewsAttached=${player?.vlcVout?.areViewsAttached()}")
                _isPlaying.value = true
                _playbackState.value = PlaybackState.READY
                seekInProgress.set(false)
                seekStallJob?.cancel()
                val len = player?.length ?: 0L
                if (len > 0L) {
                    _duration.value = len
                }
                pendingSeekPositionMs?.let { targetMs ->
                    pendingSeekPositionMs = null
                    Log.i(TAG, "[$instanceTag] Applying pending seek to ${targetMs}ms")
                    player?.time = targetMs
                    _currentPosition.value = targetMs
                    if (!seekWasPlaying) {
                        player?.pause()
                        _isPlaying.value = false
                    }
                }
                if (seekSavedAudioTrack != -1 && player != null) {
                    runCatching { player.audioTrack = seekSavedAudioTrack }
                }
                if (seekSavedSpuTrack != -1 && player != null) {
                    runCatching { player.spuTrack = seekSavedSpuTrack }
                }
                refreshTracks()
            }
            MediaPlayer.Event.Paused -> {
                Log.i(TAG, "[$instanceTag] [VLC_EVENT] Paused")
                _isPlaying.value = false
            }
            MediaPlayer.Event.Stopped -> {
                Log.i(TAG, "[$instanceTag] [VLC_EVENT] Stopped")
                seekInProgress.set(false)
                seekStallJob?.cancel()
                _isPlaying.value = false
                _playbackState.value = PlaybackState.IDLE
            }
            MediaPlayer.Event.EndReached -> {
                Log.i(TAG, "[$instanceTag] [VLC_EVENT] EndReached")
                seekInProgress.set(false)
                seekStallJob?.cancel()
                _isPlaying.value = false
                _playbackState.value = PlaybackState.ENDED
            }
            MediaPlayer.Event.EncounteredError -> {
                Log.e(TAG, "[$instanceTag] [VLC_EVENT] EncounteredError")
                seekInProgress.set(false)
                seekStallJob?.cancel()
                _isPlaying.value = false
                _playbackState.value = PlaybackState.ERROR
                _error.tryEmit(PlayerError.SourceError("VLC oynatma hatası"))
            }
            MediaPlayer.Event.TimeChanged -> {
                val currentTime = event.timeChanged.coerceAtLeast(0L)
                _currentPosition.value = currentTime
                if (_duration.value <= 0L) {
                    val len = player?.length ?: 0L
                    if (len > 0L) {
                        _duration.value = len
                    }
                }
                if (seekInProgress.get()) {
                    val isCloseToTarget = seekTargetMs <= 0L || kotlin.math.abs(currentTime - seekTargetMs) <= 3500L
                    if (isCloseToTarget) {
                        seekInProgress.set(false)
                        seekStallJob?.cancel()
                        if (_playbackState.value == PlaybackState.BUFFERING) {
                            _playbackState.value = PlaybackState.READY
                        }
                    }
                } else if (_playbackState.value == PlaybackState.BUFFERING && _isPlaying.value) {
                    _playbackState.value = PlaybackState.READY
                }
            }
            MediaPlayer.Event.PositionChanged -> {
                if (seekInProgress.get()) {
                    val dur = _duration.value.takeIf { it > 0L } ?: (player?.length ?: 0L)
                    val expectedPos = if (dur > 0L) (seekTargetMs.toFloat() / dur.toFloat()).coerceIn(0f, 1f) else 0f
                    val isCloseToTarget = seekTargetMs <= 0L || dur <= 0L || kotlin.math.abs(event.positionChanged - expectedPos) <= 0.05f
                    if (isCloseToTarget) {
                        seekInProgress.set(false)
                        seekStallJob?.cancel()
                        if (_playbackState.value == PlaybackState.BUFFERING) {
                            _playbackState.value = PlaybackState.READY
                        }
                    }
                } else if (_playbackState.value == PlaybackState.BUFFERING && _isPlaying.value) {
                    _playbackState.value = PlaybackState.READY
                }
            }
            MediaPlayer.Event.LengthChanged -> {
                Log.i(TAG, "[$instanceTag] [VLC_EVENT] LengthChanged: ${event.lengthChanged}ms")
                _duration.value = event.lengthChanged.coerceAtLeast(0L)
            }
            MediaPlayer.Event.Vout -> {
                Log.i(TAG, "[$instanceTag] [VLC_EVENT] Vout: count=${event.voutCount}")
                refreshTracks()
            }
        }
    }

    private fun getOrCreatePlayer(): MediaPlayer? {
        if (isDisposed) return null
        mediaPlayer?.let { return it }

        return runCatching {
            val options = ArrayList<String>().apply {
                add("--no-sub-autodetect-file")
                add("--network-caching=2000")
                add("--live-caching=2000")
                add("--file-caching=2000")
                add("--sout-mux-caching=2000")
                add("--http-reconnect")
                add("--no-drop-late-frames")
                add("--no-skip-frames")
                add("--aout=opensles,android_audiotrack,any")
                add("-v")
            }

            val vlc = LibVLC(context.applicationContext, options).also { libVlc = it }
            val player = MediaPlayer(vlc).also { mediaPlayer = it }

            // Scale video output to fit container window rather than 1:1 pixel crop
            player.scale = 0f
            player.aspectRatio = null

            runCatching {
                player.vlcVout.addCallback(voutCallback)
            }

            player.setEventListener { event ->
                handlePlayerEvent(event, player)
            }

            attachedRenderView?.let { bindRenderView(it, PlayerSurfaceResizeMode.FIT) }
            player
        }.onFailure { e ->
            Log.e(TAG, "[$instanceTag] Failed to initialize LibVLC MediaPlayer: ${e.message}", e)
        }.getOrNull()
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
        currentStreamInfo = streamInfo
        currentSeekToken = 0L
        seekRecoveryAttemptedForToken = 0L
        seekStallJob?.cancel()

        val player = getOrCreatePlayer() ?: run {
            _playbackState.value = PlaybackState.ERROR
            _error.tryEmit(PlayerError.SourceError("Dahili VLC başlatılamadı"))
            return
        }
        val vlc = libVlc ?: run {
            _playbackState.value = PlaybackState.ERROR
            _error.tryEmit(PlayerError.SourceError("Dahili VLC kütüphanesi yüklenemedi"))
            return
        }

        val maskedTarget = streamInfo.title ?: ("stream#" + (streamInfo.url.hashCode() and 0xffff).toString(16))
        Log.i(TAG, "[$instanceTag] prepare: target=$maskedTarget videoDecoder=$currentVideoDecoderMode audioDecoder=$currentAudioDecoderMode")

        _playbackState.value = PlaybackState.BUFFERING
        _isPlaying.value = false
        _currentPosition.value = 0L
        _duration.value = 0L

        runCatching {
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
                when (currentVideoDecoderMode) {
                    DecoderMode.HARDWARE -> setHWDecoderEnabled(true, true)
                    DecoderMode.SOFTWARE -> setHWDecoderEnabled(false, false)
                    else -> setHWDecoderEnabled(true, false)
                }
            }

            player.media = media
            media.release()

            val view = attachedRenderView
            val surfaceReadyVal = isSurfaceReady.get()
            val attachedNull = view == null
            val viewType = view?.javaClass?.simpleName ?: "null"
            val texAvailable = (view as? TextureView)?.isAvailable
            val surfValid = (view as? SurfaceView)?.holder?.surface?.isValid
            val voutAttached = player.vlcVout.areViewsAttached()
            val playerNull = mediaPlayer == null
            val mediaNull = player.media == null
            val isDisp = isDisposed
            val checkResult = checkSurfaceReady()

            Log.i(TAG, "[$instanceTag] [DIAG_PREPARE] surfaceReadyAtomic=$surfaceReadyVal attachedRenderViewNull=$attachedNull renderViewType=$viewType textureViewAvailable=$texAvailable surfaceViewValid=$surfValid voutViewsAttached=$voutAttached mediaPlayerNull=$playerNull mediaNull=$mediaNull isDisposed=$isDisp checkSurfaceReadyResult=$checkResult")

            pendingStartPlayback.set(true)
            if (checkResult) {
                Log.i(TAG, "[$instanceTag] prepare: Surface is ready; triggering playback")
                startPendingPlaybackIfReady("prepare")
            } else {
                Log.i(TAG, "[$instanceTag] prepare: Surface not ready yet; pending playback until onSurfacesCreated")
            }
        }.onFailure { e ->
            Log.e(TAG, "[$instanceTag] Failed to prepare VLC media: ${e.message}", e)
            _playbackState.value = PlaybackState.ERROR
            _error.tryEmit(PlayerError.SourceError(e.message ?: "VLC medya hazırlama hatası"))
        }
    }

    override fun renewStreamUrl(streamInfo: StreamInfo) {
        prepare(streamInfo)
    }

    override fun play() {
        if (isDisposed) return
        val player = mediaPlayer ?: getOrCreatePlayer() ?: return
        val view = attachedRenderView
        val checkResult = checkSurfaceReady()
        Log.i(TAG, "[$instanceTag] [DIAG_PLAY] surfaceReadyAtomic=${isSurfaceReady.get()} attachedRenderViewNull=${view == null} renderViewType=${view?.javaClass?.simpleName ?: "null"} textureViewAvailable=${(view as? TextureView)?.isAvailable} voutViewsAttached=${player.vlcVout.areViewsAttached()} checkSurfaceReadyResult=$checkResult")
        pendingStartPlayback.set(true)
        if (checkResult) {
            Log.i(TAG, "[$instanceTag] play: Surface is ready; triggering playback")
            startPendingPlaybackIfReady("play")
        } else {
            Log.i(TAG, "[$instanceTag] play: Surface not ready yet; pending playback")
        }
    }

    override fun pause() {
        if (isDisposed) return
        pendingStartPlayback.set(false)
        mediaPlayer?.pause()
    }

    override fun stop() {
        if (isDisposed) return
        seekStallJob?.cancel()
        pendingStartPlayback.set(false)
        isPlaybackStarted.set(false)
        seekInProgress.set(false)
        pendingSeekPositionMs = null
        currentSeekToken = 0L
        mediaPlayer?.stop()
        _playbackState.value = PlaybackState.IDLE
        _isPlaying.value = false
    }

    override fun seekTo(positionMs: Long) {
        if (isDisposed) return
        val target = positionMs.coerceAtLeast(0L)
        val player = mediaPlayer
        val token = seekTokenCounter.incrementAndGet()
        currentSeekToken = token
        seekTargetMs = target
        seekWasPlaying = _isPlaying.value
        seekSavedSpeed = _playbackSpeed.value
        seekSavedAudioTrack = player?.audioTrack ?: -1
        seekSavedSpuTrack = player?.spuTrack ?: -1

        Log.i(TAG, "[$instanceTag] seekTo: token=$token targetMs=$target isPlaying=$seekWasPlaying isSeekable=${player?.isSeekable} duration=${_duration.value}")
        _currentPosition.value = target

        seekStallJob?.cancel()

        if (player != null && (player.isSeekable || player.length > 0L)) {
            seekInProgress.set(true)
            pendingSeekPositionMs = null
            player.time = target
            startSeekStallWatchdog(token, target)
        } else {
            pendingSeekPositionMs = target
        }
    }

    override fun seekForward(ms: Long) {
        if (isDisposed) return
        val current = _currentPosition.value
        val dur = _duration.value.takeIf { it > 0 } ?: (mediaPlayer?.length ?: 0L)
        val target = if (dur > 0) (current + ms).coerceAtMost(dur) else current + ms
        seekTo(target)
    }

    override fun seekBackward(ms: Long) {
        if (isDisposed) return
        val current = _currentPosition.value
        val target = (current - ms).coerceAtLeast(0L)
        seekTo(target)
    }

    internal fun startSeekStallWatchdog(token: Long, targetMs: Long) {
        seekStallJob?.cancel()
        seekStallJob = engineScope.launch {
            delay(5000L)
            if (isDisposed) return@launch
            if (currentSeekToken != token) return@launch
            if (!seekInProgress.get()) return@launch

            Log.w(TAG, "[$instanceTag] [SEEK_STALL] Seek stall detected for token=$token targetMs=$targetMs after 5s watchdog")

            seekInProgress.set(false)
            _isPlaying.value = false
            _playbackState.value = PlaybackState.ERROR
            _error.tryEmit(PlayerError.SourceError("Bu içerik Dahili VLC ile sarılamıyor. Media3 kullanın."))
        }
    }

    internal fun performCleanSessionRecovery(token: Long, targetMs: Long) {
        if (isDisposed) return
        val streamInfo = currentStreamInfo ?: run {
            Log.e(TAG, "[$instanceTag] [RECOVERY] Cannot recover: currentStreamInfo is null")
            seekInProgress.set(false)
            _isPlaying.value = false
            _playbackState.value = PlaybackState.ERROR
            _error.tryEmit(PlayerError.SourceError("Bu konumdan devam edilemedi. Media3 ile deneyin."))
            return
        }
        val vlc = libVlc ?: run {
            seekInProgress.set(false)
            _isPlaying.value = false
            _playbackState.value = PlaybackState.ERROR
            _error.tryEmit(PlayerError.SourceError("Bu konumdan devam edilemedi. Media3 ile deneyin."))
            return
        }
        val view = attachedRenderView

        Log.i(TAG, "[$instanceTag] [RECOVERY] Executing single-shot session recovery for token=$token targetMs=$targetMs attachedView=${view?.javaClass?.simpleName}")

        runCatching {
            // 1. Cleanly tear down stalled MediaPlayer instance without releasing LibVLC or render view
            mediaPlayer?.let { oldPlayer ->
                oldPlayer.setEventListener(null)
                runCatching {
                    val vout = oldPlayer.vlcVout
                    vout?.removeCallback(voutCallback)
                    if (vout?.areViewsAttached() == true) {
                        vout.detachViews()
                    }
                }
                oldPlayer.stop()
                oldPlayer.release()
            }
            mediaPlayer = null

            // 2. Create fresh MediaPlayer instance
            val newPlayer = MediaPlayer(vlc).apply {
                setEventListener { event -> handlePlayerEvent(event, this) }
                vlcVout.addCallback(voutCallback)
                if (!_isMuted.value) {
                    volume = (rememberedVolumeLevel * 100).toInt()
                } else {
                    volume = 0
                }
                rate = seekSavedSpeed
            }
            mediaPlayer = newPlayer

            // 3. Attach existing render view if available
            if (view != null) {
                if (view is SurfaceView) {
                    if (view.holder.surface?.isValid == true) {
                        newPlayer.vlcVout.setVideoSurface(view.holder.surface, view.holder)
                        isSurfaceReady.set(true)
                    } else {
                        newPlayer.vlcVout.setVideoView(view)
                    }
                } else if (view is TextureView) {
                    if (view.isAvailable && view.surfaceTexture != null) {
                        newPlayer.vlcVout.setVideoSurface(view.surfaceTexture)
                        isSurfaceReady.set(true)
                    } else {
                        newPlayer.vlcVout.setVideoView(view)
                    }
                }
                val curW = view.width
                val curH = view.height
                if (curW > 0 && curH > 0) {
                    newPlayer.vlcVout.setWindowSize(curW, curH)
                }
                if (!newPlayer.vlcVout.areViewsAttached()) {
                    newPlayer.vlcVout.attachViews()
                }
                applyResizeMode(currentResizeMode)
            }

            // 4. Prepare same stream URI & options
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
                when (currentVideoDecoderMode) {
                    DecoderMode.HARDWARE -> setHWDecoderEnabled(true, true)
                    DecoderMode.SOFTWARE -> setHWDecoderEnabled(false, false)
                    else -> setHWDecoderEnabled(true, false)
                }
            }
            newPlayer.media = media
            media.release()

            // 5. Setup target position and play/pause preservation
            pendingSeekPositionMs = targetMs
            _playbackState.value = PlaybackState.BUFFERING
            isPlaybackStarted.set(false)

            if (seekWasPlaying) {
                pendingStartPlayback.set(true)
                if (checkSurfaceReady()) {
                    startPendingPlaybackIfReady("recovery-play")
                }
            } else {
                pendingStartPlayback.set(false)
                _isPlaying.value = false
            }

            // 6. Restore track selection if applicable
            if (seekSavedAudioTrack >= 0) {
                runCatching { newPlayer.setAudioTrack(seekSavedAudioTrack) }
            }
            if (seekSavedSpuTrack >= 0) {
                runCatching { newPlayer.setSpuTrack(seekSavedSpuTrack) }
            }

            // 7. Arm secondary 5s stall watchdog
            startSeekStallWatchdog(token, targetMs)
        }.onFailure { e ->
            Log.e(TAG, "[$instanceTag] [RECOVERY] Failed to recover session: ${e.message}", e)
            seekInProgress.set(false)
            _isPlaying.value = false
            _playbackState.value = PlaybackState.ERROR
            _error.tryEmit(PlayerError.SourceError("Bu konumdan devam edilemedi. Media3 ile deneyin."))
        }
    }

    override fun setDecoderModes(audioMode: DecoderMode, videoMode: DecoderMode) {
        currentAudioDecoderMode = audioMode
        currentVideoDecoderMode = videoMode
        Log.i(TAG, "[VLC] setDecoderModes: audio=$audioMode video=$videoMode")
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
        val id = trackId.toIntOrNull() ?: -1
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

    private var layoutChangeListener: View.OnLayoutChangeListener? = null

    override fun createRenderView(
        context: Context,
        resizeMode: PlayerSurfaceResizeMode,
        surfaceType: PlayerRenderSurfaceType
    ): View {
        Log.i(TAG, "[$instanceTag] createRenderView called with surfaceType=$surfaceType isDisposed=$isDisposed")
        val view = TextureView(context).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
        }
        return view
    }

    override fun bindRenderView(renderView: View, resizeMode: PlayerSurfaceResizeMode) {
        if (isDisposed) return
        val player = mediaPlayer ?: getOrCreatePlayer() ?: run {
            Log.e(TAG, "[$instanceTag] bindRenderView failed: mediaPlayer is null and getOrCreatePlayer returned null")
            return
        }

        val vout = player.vlcVout
        if (attachedRenderView === renderView) {
            Log.d(TAG, "[$instanceTag] bindRenderView: same view -> no-op")
            applyResizeMode(resizeMode)
            if (renderView is TextureView && renderView.isAvailable) {
                isSurfaceReady.set(true)
            } else if (renderView is SurfaceView && renderView.holder.surface?.isValid == true) {
                isSurfaceReady.set(true)
            }
            if (checkSurfaceReady()) {
                if (pendingStartPlayback.get() || player.media != null) {
                    if (!_isPlaying.value) {
                        Log.i(TAG, "[$instanceTag] Starting playback from same-view bindRenderView")
                        startPendingPlaybackIfReady("same-view bindRenderView")
                    }
                }
            }
            return
        }

        Log.i(TAG, "[$instanceTag] bindRenderView called: view=${renderView.javaClass.simpleName} isDisposed=$isDisposed")
        runCatching {
            attachedRenderView?.let { oldView ->
                layoutChangeListener?.let { listener ->
                    oldView.removeOnLayoutChangeListener(listener)
                }
            }
            layoutChangeListener = null

            if (vout.areViewsAttached()) {
                vout.detachViews()
            }
            attachedRenderView = renderView
            Log.i(TAG, "[$instanceTag] [SET_FALSE] bindRenderView: fresh renderView attached")
            isSurfaceReady.set(false)

            if (renderView.layoutParams == null) {
                renderView.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
            }

            if (renderView is SurfaceView) {
                if (renderView.holder.surface?.isValid == true) {
                    vout.setVideoSurface(renderView.holder.surface, renderView.holder)
                    isSurfaceReady.set(true)
                } else {
                    vout.setVideoView(renderView)
                }
            } else if (renderView is TextureView) {
                if (renderView.isAvailable && renderView.surfaceTexture != null) {
                    vout.setVideoSurface(renderView.surfaceTexture)
                    isSurfaceReady.set(true)
                } else {
                    vout.setVideoView(renderView)
                    renderView.surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                        override fun onSurfaceTextureAvailable(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {
                            Log.i(TAG, "[$instanceTag] onSurfaceTextureAvailable: w=$width h=$height")
                            isSurfaceReady.set(true)
                            mainHandler.post {
                                startPendingPlaybackIfReady("onSurfaceTextureAvailable")
                            }
                        }
                        override fun onSurfaceTextureSizeChanged(surface: android.graphics.SurfaceTexture, width: Int, height: Int) {
                            if (width > 0 && height > 0) {
                                vout.setWindowSize(width, height)
                            }
                        }
                        override fun onSurfaceTextureDestroyed(surface: android.graphics.SurfaceTexture): Boolean {
                            Log.i(TAG, "[$instanceTag] [SET_FALSE] onSurfaceTextureDestroyed")
                            isSurfaceReady.set(false)
                            return true
                        }
                        override fun onSurfaceTextureUpdated(surface: android.graphics.SurfaceTexture) {}
                    }
                }
            }

            val currentWidth = renderView.width
            val currentHeight = renderView.height
            if (currentWidth > 0 && currentHeight > 0) {
                vout.setWindowSize(currentWidth, currentHeight)
            }

            val newListener = View.OnLayoutChangeListener { _, left, top, right, bottom, oldLeft, oldTop, oldRight, oldBottom ->
                val newW = right - left
                val newH = bottom - top
                if (newW > 0 && newH > 0 && (newW != (oldRight - oldLeft) || newH != (oldBottom - oldTop))) {
                    vout.setWindowSize(newW, newH)
                }
            }
            layoutChangeListener = newListener
            renderView.addOnLayoutChangeListener(newListener)

            if (!vout.areViewsAttached()) {
                vout.attachViews()
            }

            applyResizeMode(resizeMode)

            if (checkSurfaceReady()) {
                Log.i(TAG, "[$instanceTag] Surface is valid on bindRenderView: pendingStartPlayback=${pendingStartPlayback.get()} hasMedia=${player.media != null}")
                if (pendingStartPlayback.get() || player.media != null) {
                    startPendingPlaybackIfReady("bindRenderView")
                }
            }
        }.onFailure { e ->
            Log.e(TAG, "[$instanceTag] Failed to bind VLC render view: ${e.message}", e)
        }
    }

    private fun applyResizeMode(mode: PlayerSurfaceResizeMode) {
        val player = mediaPlayer ?: return
        runCatching {
            when (mode) {
                PlayerSurfaceResizeMode.FIT -> {
                    player.scale = 0f
                    player.aspectRatio = null
                }
                PlayerSurfaceResizeMode.FILL -> {
                    player.scale = 0f
                    val vTrack = player.currentVideoTrack
                    if (vTrack != null && vTrack.width > 0 && vTrack.height > 0) {
                        player.aspectRatio = "${vTrack.width}:${vTrack.height}"
                    } else {
                        player.aspectRatio = null
                    }
                }
                PlayerSurfaceResizeMode.ZOOM -> {
                    player.scale = 0f
                    player.aspectRatio = null
                }
            }
        }
    }

    override fun clearRenderBinding() {
        attachedRenderView?.let { oldView ->
            layoutChangeListener?.let { listener ->
                oldView.removeOnLayoutChangeListener(listener)
            }
        }
        layoutChangeListener = null
        attachedRenderView = null
        Log.i(TAG, "[$instanceTag] [SET_FALSE] clearRenderBinding called")
        isSurfaceReady.set(false)
        pendingStartPlayback.set(false)
        isPlaybackStarted.set(false)
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
                runCatching {
                    val vout = player.vlcVout
                    vout?.removeCallback(voutCallback)
                    if (vout?.areViewsAttached() == true) {
                        vout.detachViews()
                    }
                }
                player.stop()
                player.release()
            }
            mediaPlayer = null
            libVlc?.release()
            libVlc = null
        }
        attachedRenderView = null
        Log.i(TAG, "[$instanceTag] [SET_FALSE] release called from:\n" + android.util.Log.getStackTraceString(Throwable()))
        seekStallJob?.cancel()
        isSurfaceReady.set(false)
        pendingStartPlayback.set(false)
        isPlaybackStarted.set(false)
        seekInProgress.set(false)
        pendingSeekPositionMs = null
        currentStreamInfo = null
        currentSeekToken = 0L
        seekRecoveryAttemptedForToken = 0L
        engineScope.coroutineContext.cancelChildren()
        _isPlaying.value = false
        _playbackState.value = PlaybackState.IDLE
    }

    override fun resetForReuse() {
        stop()
    }
}
