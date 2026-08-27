package com.kaynanamtv.app.cast

import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject
import javax.inject.Singleton

interface CastPlaybackCoordinator {
    val playbackEvents: SharedFlow<CastPlaybackEvent>
    val remoteAudioTracks: StateFlow<List<CastTrackInfo>>
    val remoteCurrentPosition: StateFlow<Long>
    val remoteDuration: StateFlow<Long>
    val isRemotePlaying: StateFlow<Boolean>
    val isRemoteLiveSeekable: StateFlow<Boolean>

    suspend fun startCasting(request: CastMediaRequest): CastStartResult
    fun stopCasting()
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun seekRelative(offsetMs: Long)
    fun setActiveAudioTrack(trackId: Long)
}

@Singleton
class DefaultCastPlaybackCoordinator @Inject constructor(
    private val castManager: CastManager
) : CastPlaybackCoordinator {
    override val playbackEvents: SharedFlow<CastPlaybackEvent> = castManager.playbackEvents
    override val remoteAudioTracks: StateFlow<List<CastTrackInfo>> = castManager.remoteAudioTracks
    override val remoteCurrentPosition: StateFlow<Long> = castManager.remoteCurrentPosition
    override val remoteDuration: StateFlow<Long> = castManager.remoteDuration
    override val isRemotePlaying: StateFlow<Boolean> = castManager.isRemotePlaying
    override val isRemoteLiveSeekable: StateFlow<Boolean> = castManager.isRemoteLiveSeekable

    override suspend fun startCasting(request: CastMediaRequest): CastStartResult {
        return castManager.startCasting(request)
    }

    override fun stopCasting() {
        castManager.stopCasting()
    }

    override fun play() {
        castManager.play()
    }

    override fun pause() {
        castManager.pause()
    }

    override fun seekTo(positionMs: Long) {
        castManager.seekTo(positionMs)
    }

    override fun seekRelative(offsetMs: Long) {
        castManager.seekRelative(offsetMs)
    }

    override fun setActiveAudioTrack(trackId: Long) {
        castManager.setActiveAudioTrack(trackId)
    }
}
