package com.kaynanamtv.app.cast

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.mediarouter.media.MediaRouteSelector
import com.google.android.gms.cast.CastMediaControlIntent
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.MediaStatus
import com.google.android.gms.cast.MediaTrack
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import com.google.android.gms.cast.framework.media.RemoteMediaClient
import com.kaynanamtv.app.plugins.KaynanamTVPluginManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CastManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val pluginManager: KaynanamTVPluginManager
) {

    private val _connectionState = MutableStateFlow(CastConnectionState.UNAVAILABLE)
    val connectionState: StateFlow<CastConnectionState> = _connectionState.asStateFlow()

    private val _playbackEvents = MutableSharedFlow<CastPlaybackEvent>(extraBufferCapacity = 8)
    val playbackEvents: SharedFlow<CastPlaybackEvent> = _playbackEvents.asSharedFlow()

    private val _remoteAudioTracks = MutableStateFlow<List<CastTrackInfo>>(emptyList())
    val remoteAudioTracks: StateFlow<List<CastTrackInfo>> = _remoteAudioTracks.asStateFlow()

    private val _remoteCurrentPosition = MutableStateFlow(0L)
    val remoteCurrentPosition: StateFlow<Long> = _remoteCurrentPosition.asStateFlow()

    private val _remoteDuration = MutableStateFlow(0L)
    val remoteDuration: StateFlow<Long> = _remoteDuration.asStateFlow()

    private val _isRemotePlaying = MutableStateFlow(false)
    val isRemotePlaying: StateFlow<Boolean> = _isRemotePlaying.asStateFlow()

    private val _isRemoteLiveSeekable = MutableStateFlow(false)
    val isRemoteLiveSeekable: StateFlow<Boolean> = _isRemoteLiveSeekable.asStateFlow()

    private var castContext: CastContext? = null
    private var initialized = false
    private var pendingRequest: CastMediaRequest? = null
    private var currentCastMediaRequest: CastMediaRequest? = null
    private var activeRemoteMediaClient: RemoteMediaClient? = null
    private var hasAutoSyncedAudioForCurrentMedia: Boolean = false
    private val mainHandler = Handler(Looper.getMainLooper())

    private val remoteMediaClientCallback = object : RemoteMediaClient.Callback() {
        override fun onStatusUpdated() {
            updateRemotePlaybackState()
        }

        override fun onMediaError(mediaError: com.google.android.gms.cast.MediaError) {
            Log.w(TAG, "Cast media error: ${mediaError.detailedErrorCode}")
        }
    }

    private val remoteProgressListener = RemoteMediaClient.ProgressListener { progressMs, durationMs ->
        _remoteCurrentPosition.value = progressMs
        if (durationMs > 0L) {
            _remoteDuration.value = durationMs
        }
    }

    private val sessionManagerListener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarting(session: CastSession) {
            _connectionState.value = CastConnectionState.CONNECTING
        }

        override fun onSessionStarted(session: CastSession, sessionId: String) {
            _connectionState.value = CastConnectionState.CONNECTED
            bindRemoteMediaClient(session.remoteMediaClient)
            loadPendingRequest(session)
        }

        override fun onSessionStartFailed(session: CastSession, error: Int) {
            _connectionState.value = CastConnectionState.DISCONNECTED
            unbindRemoteMediaClient()
            val request = pendingRequest
            pendingRequest = null
            if (request != null) {
                _playbackEvents.tryEmit(CastPlaybackEvent.SessionStartFailed(error))
            }
            Log.w(TAG, "Cast session failed to start: $error")
        }

        override fun onSessionEnding(session: CastSession) {
            _connectionState.value = CastConnectionState.DISCONNECTED
        }

        override fun onSessionEnded(session: CastSession, error: Int) {
            _connectionState.value = CastConnectionState.DISCONNECTED
            unbindRemoteMediaClient()
            pendingRequest = null
            currentCastMediaRequest = null
        }

        override fun onSessionResuming(session: CastSession, sessionId: String) {
            _connectionState.value = CastConnectionState.CONNECTING
        }

        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) {
            _connectionState.value = CastConnectionState.CONNECTED
            bindRemoteMediaClient(session.remoteMediaClient)
            loadPendingRequest(session)
        }

        override fun onSessionResumeFailed(session: CastSession, error: Int) {
            _connectionState.value = CastConnectionState.DISCONNECTED
            unbindRemoteMediaClient()
            val request = pendingRequest
            pendingRequest = null
            if (request != null) {
                _playbackEvents.tryEmit(CastPlaybackEvent.SessionStartFailed(error))
            }
        }

        override fun onSessionSuspended(session: CastSession, reason: Int) {
            _connectionState.value = CastConnectionState.CONNECTING
        }
    }

    fun ensureInitialized() {
        if (initialized) return
        initialized = true
        runCatching {
            CastContext.getSharedInstance(context)
        }.onSuccess { resolvedContext ->
            castContext = resolvedContext
            resolvedContext.sessionManager.addSessionManagerListener(sessionManagerListener, CastSession::class.java)
            _connectionState.value = currentConnectionState(resolvedContext)
            resolvedContext.sessionManager.currentCastSession?.remoteMediaClient?.let(::bindRemoteMediaClient)
        }.onFailure { throwable ->
            _connectionState.value = CastConnectionState.UNAVAILABLE
            Log.w(TAG, "Google Cast is unavailable on this device", throwable)
        }
    }

    fun buildRouteSelector(): MediaRouteSelector = MediaRouteSelector.Builder()
        .addControlCategory(
            CastMediaControlIntent.categoryForCast(
                CastMediaControlIntent.DEFAULT_MEDIA_RECEIVER_APPLICATION_ID
            )
        )
        .build()

    suspend fun startCasting(request: CastMediaRequest): CastStartResult {
        ensureInitialized()
        val resolvedContext = castContext ?: return CastStartResult.UNAVAILABLE
        if (!isRequestSupported(request)) {
            return CastStartResult.UNSUPPORTED
        }
        val rewrittenUrl = pluginManager.rewriteCastUrl(request) ?: return CastStartResult.UNSUPPORTED
        if (request.rewriteRequiredReason == CastRewriteRequiredReason.LOCAL_URI && rewrittenUrl.trim() == request.url.trim()) {
            Log.w(TAG, "Cast request requires URL rewrite for local URI but no receiver-safe URL was returned")
            return CastStartResult.UNSUPPORTED
        }
        val resolvedRequest = request.copy(url = rewrittenUrl)

        pendingRequest = resolvedRequest
        currentCastMediaRequest = resolvedRequest
        hasAutoSyncedAudioForCurrentMedia = false

        val activeSession = resolvedContext.sessionManager.currentCastSession
        return if (activeSession?.isConnected == true) {
            bindRemoteMediaClient(activeSession.remoteMediaClient)
            if (!loadMedia(activeSession, resolvedRequest)) {
                pendingRequest = null
                return CastStartResult.UNAVAILABLE
            }
            pendingRequest = null
            CastStartResult.STARTED
        } else {
            CastStartResult.ROUTE_SELECTION_REQUIRED
        }
    }

    fun stopCasting() {
        ensureInitialized()
        pendingRequest = null
        currentCastMediaRequest = null
        unbindRemoteMediaClient()
        castContext?.sessionManager?.endCurrentSession(true)
        _connectionState.value = CastConnectionState.DISCONNECTED
    }

    fun play() {
        activeRemoteMediaClient?.play()
    }

    fun pause() {
        activeRemoteMediaClient?.pause()
    }

    fun seekTo(positionMs: Long) {
        val client = activeRemoteMediaClient ?: return
        client.seek(positionMs.coerceAtLeast(0L))
    }

    fun seekRelative(offsetMs: Long) {
        val client = activeRemoteMediaClient ?: return
        val current = client.approximateStreamPosition
        val target = (current + offsetMs).coerceAtLeast(0L)
        client.seek(target)
    }

    fun setActiveAudioTrack(trackId: Long) {
        val client = activeRemoteMediaClient ?: return
        client.setActiveMediaTracks(longArrayOf(trackId))
    }

    fun onRouteChooserClosed() {
        val request = pendingRequest ?: return
        mainHandler.postDelayed({
            if (pendingRequest == request && _connectionState.value == CastConnectionState.DISCONNECTED) {
                pendingRequest = null
                _playbackEvents.tryEmit(CastPlaybackEvent.RouteSelectionCancelled)
            }
        }, ROUTE_SELECTION_CANCEL_CHECK_DELAY_MS)
    }

    private fun bindRemoteMediaClient(client: RemoteMediaClient?) {
        if (activeRemoteMediaClient === client) return
        unbindRemoteMediaClient()
        if (client != null) {
            activeRemoteMediaClient = client
            client.registerCallback(remoteMediaClientCallback)
            client.addProgressListener(remoteProgressListener, 500L)
            updateRemotePlaybackState()
        }
    }

    private fun unbindRemoteMediaClient() {
        activeRemoteMediaClient?.let { client ->
            client.unregisterCallback(remoteMediaClientCallback)
            client.removeProgressListener(remoteProgressListener)
        }
        activeRemoteMediaClient = null
        _isRemotePlaying.value = false
        _remoteAudioTracks.value = emptyList()
        _remoteCurrentPosition.value = 0L
        _remoteDuration.value = 0L
        _isRemoteLiveSeekable.value = false
    }

    private fun updateRemotePlaybackState() {
        val client = activeRemoteMediaClient ?: return
        val status = client.mediaStatus
        if (status == null) {
            _isRemotePlaying.value = false
            return
        }

        _isRemotePlaying.value = status.playerState == MediaStatus.PLAYER_STATE_PLAYING
        val streamPos = status.streamPosition
        if (streamPos >= 0L) {
            _remoteCurrentPosition.value = streamPos
        }
        val duration = status.mediaInfo?.streamDuration ?: 0L
        if (duration > 0L) {
            _remoteDuration.value = duration
        }
        _isRemoteLiveSeekable.value = status.liveSeekableRange != null

        // Parse remote audio tracks
        val mediaTracks = status.mediaInfo?.mediaTracks
        val activeTrackIds = status.activeTrackIds?.toSet() ?: emptySet()
        val audioTracks = mediaTracks?.filter { it.type == MediaTrack.TYPE_AUDIO }?.map { track ->
            CastTrackInfo(
                id = track.id,
                name = track.name?.takeIf { it.isNotBlank() }
                    ?: track.language?.let { formatLanguageDisplayName(it) }
                    ?: "Ses ${track.id}",
                language = track.language,
                isSelected = activeTrackIds.contains(track.id)
            )
        } ?: emptyList()

        _remoteAudioTracks.value = audioTracks

        // Auto-sync audio language if local preferred language is set and not yet synced
        val targetLang = currentCastMediaRequest?.preferredAudioLanguage
        val targetLabel = currentCastMediaRequest?.preferredAudioLabel
        if (!hasAutoSyncedAudioForCurrentMedia && audioTracks.isNotEmpty() && (!targetLang.isNullOrBlank() || !targetLabel.isNullOrBlank())) {
            hasAutoSyncedAudioForCurrentMedia = true
            val matchingTrack = findBestMatchingAudioTrack(audioTracks, targetLang, targetLabel)
            if (matchingTrack != null && !matchingTrack.isSelected) {
                Log.i(TAG, "Auto-syncing remote audio track to: ${matchingTrack.name} (id=${matchingTrack.id})")
                client.setActiveMediaTracks(longArrayOf(matchingTrack.id))
            }
        }
    }

    private fun findBestMatchingAudioTrack(
        tracks: List<CastTrackInfo>,
        targetLanguage: String?,
        targetLabel: String?
    ): CastTrackInfo? {
        val normTargetLang = normalizeAudioLanguage(targetLanguage)
        val normTargetLabel = normalizeAudioLanguage(targetLabel)

        // 1. Match by normalized language code
        if (normTargetLang.isNotBlank()) {
            tracks.firstOrNull { normalizeAudioLanguage(it.language) == normTargetLang }?.let { return it }
        }
        // 2. Match by normalized label in name
        if (normTargetLabel.isNotBlank()) {
            tracks.firstOrNull { normalizeAudioLanguage(it.name) == normTargetLabel }?.let { return it }
            tracks.firstOrNull { it.name.contains(targetLabel.orEmpty(), ignoreCase = true) }?.let { return it }
        }
        if (normTargetLang.isNotBlank()) {
            tracks.firstOrNull { normalizeAudioLanguage(it.name) == normTargetLang }?.let { return it }
        }
        return null
    }

    private fun normalizeAudioLanguage(raw: String?): String {
        if (raw.isNullOrBlank()) return ""
        val trimmed = raw.trim().lowercase(Locale.ROOT)
        return when {
            trimmed.startsWith("tr") || trimmed.contains("turk") || trimmed == "tur" -> "tr"
            trimmed.startsWith("en") || trimmed.contains("eng") -> "en"
            trimmed.startsWith("de") || trimmed.contains("ger") || trimmed.contains("deu") -> "de"
            trimmed.startsWith("fr") || trimmed.contains("fra") || trimmed.contains("fre") -> "fr"
            trimmed.startsWith("es") || trimmed.contains("spa") -> "es"
            trimmed.startsWith("it") || trimmed.contains("ita") -> "it"
            trimmed.startsWith("ru") || trimmed.contains("rus") -> "ru"
            trimmed.startsWith("ar") || trimmed.contains("ara") -> "ar"
            else -> trimmed.substringBefore('-').substringBefore('_')
        }
    }

    private fun formatLanguageDisplayName(code: String): String {
        return runCatching {
            val locale = Locale.forLanguageTag(code)
            locale.getDisplayName(Locale("tr")).replaceFirstChar { it.uppercase() }
        }.getOrNull()?.takeIf { it.isNotBlank() } ?: code
    }

    private fun loadPendingRequest(session: CastSession) {
        val request = pendingRequest ?: return
        loadMedia(session, request)
        pendingRequest = null
    }

    private fun loadMedia(session: CastSession, request: CastMediaRequest): Boolean {
        val remoteMediaClient = session.remoteMediaClient ?: run {
            _playbackEvents.tryEmit(CastPlaybackEvent.ReceiverUnavailable(request.title))
            return false
        }
        bindRemoteMediaClient(remoteMediaClient)
        val metadataType = if (request.isLive) MediaMetadata.MEDIA_TYPE_TV_SHOW else MediaMetadata.MEDIA_TYPE_MOVIE
        val metadata = MediaMetadata(metadataType).apply {
            putString(MediaMetadata.KEY_TITLE, request.title)
            request.subtitle?.takeIf { it.isNotBlank() }?.let {
                putString(MediaMetadata.KEY_SUBTITLE, it)
            }
            request.artworkUrl?.takeIf { it.isNotBlank() }?.let {
                addImage(com.google.android.gms.common.images.WebImage(Uri.parse(it)))
            }
        }

        val mediaInfo = MediaInfo.Builder(request.url)
            .setContentType(request.mimeType ?: DEFAULT_CONTENT_TYPE)
            .setStreamType(if (request.isLive) MediaInfo.STREAM_TYPE_LIVE else MediaInfo.STREAM_TYPE_BUFFERED)
            .setMetadata(metadata)
            .build()

        remoteMediaClient.load(
            MediaLoadRequestData.Builder()
                .setMediaInfo(mediaInfo)
                .setAutoplay(true)
                .setCurrentTime(request.startPositionMs)
                .build()
        ).setResultCallback { result ->
            if (result.status.isSuccess) {
                _playbackEvents.tryEmit(CastPlaybackEvent.MediaLoadSucceeded(request.title))
            } else {
                Log.w(TAG, "Cast media load failed: ${result.status.statusCode}")
                _playbackEvents.tryEmit(
                    CastPlaybackEvent.MediaLoadFailed(
                        title = request.title,
                        statusCode = result.status.statusCode
                    )
                )
            }
        }
        return true
    }

    private fun currentConnectionState(castContext: CastContext): CastConnectionState {
        val activeSession = castContext.sessionManager.currentCastSession
        return when {
            activeSession?.isConnected == true -> CastConnectionState.CONNECTED
            activeSession != null -> CastConnectionState.CONNECTING
            else -> CastConnectionState.DISCONNECTED
        }
    }

    private fun isRequestSupported(request: CastMediaRequest): Boolean {
        val normalizedUrl = request.url.trim().lowercase()
        val mimeType = request.mimeType.orEmpty().lowercase()
        if (
            normalizedUrl.startsWith("rtsp://") ||
            normalizedUrl.startsWith("rtsps://") ||
            normalizedUrl.startsWith("rtmp://") ||
            normalizedUrl.startsWith("rtmps://")
        ) {
            return false
        }
        return mimeType.isBlank() || mimeType != "application/x-rtsp"
    }

    private companion object {
        const val TAG = "CastManager"
        const val DEFAULT_CONTENT_TYPE = "application/x-mpegURL"
        const val ROUTE_SELECTION_CANCEL_CHECK_DELAY_MS = 1_000L
    }
}
