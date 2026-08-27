package com.kaynanamtv.app.ui.screens.player

import android.os.SystemClock
import androidx.lifecycle.viewModelScope
import com.kaynanamtv.domain.model.ContentType
import com.kaynanamtv.domain.model.Episode
import com.kaynanamtv.player.timeshift.LiveTimeshiftStatus
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val PLAYBACK_CONTROL_MUTE_TOGGLE_DEBOUNCE_MS = 250L

fun PlayerViewModel.coalescedSeek(deltaMs: Long) {
    notifyUserActivity()
    if (currentContentType != ContentType.LIVE || isCatchUpPlayback.value) {
        val duration = playerEngine.duration.value
        if (duration <= 0L) return
        val currentBase = pendingSeekTargetMs ?: playerEngine.currentPosition.value
        val targetPos = (currentBase + deltaMs).coerceIn(0L, duration)
        pendingSeekTargetMs = targetPos
        updateSeekPreview(targetPos)

        coalescedSeekJob?.cancel()
        coalescedSeekJob = viewModelScope.launch {
            delay(220)
            val finalTarget = pendingSeekTargetMs ?: targetPos
            pendingSeekTargetMs = null
            seekTo(finalTarget)
            delay(600)
            updateSeekPreview(null)
        }
    } else {
        if (deltaMs >= 0) playerEngine.seekForward() else playerEngine.seekBackward()
    }
}

fun PlayerViewModel.seekForward(deltaMs: Long = 10_000L) {
    notifyUserActivity()
    if (currentContentType != ContentType.LIVE || isCatchUpPlayback.value) {
        val duration = playerEngine.duration.value
        if (duration <= 0L) return
        val currentPos = playerEngine.currentPosition.value
        val targetPos = (currentPos + deltaMs).coerceIn(0L, duration)
        seekTo(targetPos)
    } else {
        playerEngine.seekForward()
    }
}

fun PlayerViewModel.seekBackward(deltaMs: Long = 10_000L) {
    notifyUserActivity()
    if (currentContentType != ContentType.LIVE || isCatchUpPlayback.value) {
        val duration = playerEngine.duration.value
        if (duration <= 0L) return
        val currentPos = playerEngine.currentPosition.value
        val targetPos = (currentPos - deltaMs).coerceIn(0L, duration)
        seekTo(targetPos)
    } else {
        playerEngine.seekBackward()
    }
}

fun PlayerViewModel.seekToLiveEdge() {
    notifyUserActivity()
    playerEngine.seekToLiveEdge()
}

fun PlayerViewModel.playEpisode(episode: Episode, showResumePrompt: Boolean = true) {
    prepare(
        streamUrl = episode.streamUrl,
        epgChannelId = null,
        internalChannelId = episode.id,
        categoryId = -1,
        providerId = episode.providerId,
        isVirtual = false,
        contentType = ContentType.SERIES_EPISODE.name,
        title = buildEpisodePlaybackTitle(episode),
        artworkUrl = episode.coverUrl ?: currentSeries.value?.posterUrl ?: currentSeries.value?.backdropUrl,
        seriesId = currentSeriesId ?: episode.seriesId.takeIf { it > 0L },
        seasonNumber = episode.seasonNumber,
        episodeNumber = episode.episodeNumber,
        showResumePrompt = showResumePrompt
    )
}

fun PlayerViewModel.toggleMute() {
    val now = SystemClock.elapsedRealtime()
    if (now - lastMuteToggleAtMs < PLAYBACK_CONTROL_MUTE_TOGGLE_DEBOUNCE_MS) return
    lastMuteToggleAtMs = now
    playerEngine.toggleMute()
    val muted = playerEngine.isMuted.value
    mutePersistJob?.cancel()
    mutePersistJob = viewModelScope.launch {
        preferencesRepository.setPlayerMuted(muted)
    }
}

fun PlayerViewModel.toggleControls() {
    closeChannelInfoOverlay()
    showControlsFlow.value = !showControlsFlow.value
    if (!showControlsFlow.value) {
        clearSeekPreview()
    }
}

fun PlayerViewModel.toggleAspectRatio() {
    val nextRatio = when (_aspectRatio.value) {
        AspectRatio.FIT -> AspectRatio.FILL
        AspectRatio.FILL -> AspectRatio.ZOOM
        AspectRatio.ZOOM -> AspectRatio.FIT
    }
    _aspectRatio.value = nextRatio

    if (currentContentId != -1L && currentContentType == ContentType.LIVE) {
        viewModelScope.launch {
            runCatching {
                preferencesRepository.setAspectRatioForChannel(currentContentId, nextRatio.name)
            }
        }
    }
}

fun PlayerViewModel.dismissResumePrompt(resume: Boolean) {
    val prompt = _resumePrompt.value
    _resumePrompt.value = ResumePromptState()
    if (resume && prompt.positionMs > 0) {
        playerEngine.seekTo(prompt.positionMs)
    }
    playerEngine.play()
}

fun PlayerViewModel.play() {
    notifyUserActivity()
    playerEngine.play()
}

fun PlayerViewModel.pause() {
    notifyUserActivity()
    playerEngine.pause()
}