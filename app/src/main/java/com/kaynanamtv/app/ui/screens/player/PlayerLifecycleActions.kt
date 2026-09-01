package com.kaynanamtv.app.ui.screens.player

import androidx.lifecycle.viewModelScope
import com.kaynanamtv.domain.model.ContentType
import com.kaynanamtv.player.PlaybackState
import com.kaynanamtv.player.PlayerEngine
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val LIFECYCLE_TOKEN_RENEWAL_LEAD_MS = 60_000L
private const val LIFECYCLE_TOKEN_RENEWAL_CHECK_INTERVAL_MS = 10_000L

internal fun PlayerViewModel.startProgressTracking() {
    progressTrackingJob?.cancel()
    if (currentContentType == ContentType.LIVE) return

    progressTrackingJob = viewModelScope.launch {
        while (true) {
            delay(5000)
            if (!isAppInForeground || !playerEngine.isPlaying.value) continue
            persistPlaybackProgress()
        }
    }
}

internal suspend fun PlayerViewModel.persistPlaybackProgress(forceCloudSync: Boolean = false) {
    val pos = playerEngine.currentPosition.value
    val dur = playerEngine.duration.value

    if (pos > 0 && dur > 0) {
        val history = buildPlaybackHistorySnapshot(pos, dur) ?: return
        logRepositoryFailure(
            operation = "Persist playback resume position",
            result = playbackHistoryRepository.updateResumePosition(history)
        )
        watchNextManager.refreshWatchNext()
        launcherRecommendationsManager.refreshRecommendations()

        // Asynchronous non-blocking Cloud User State Sync (OCC multi-device)
        currentCheckpointSeq++
        cloudUserStateSyncManager.recordPlaybackProgress(
            providerId = currentProviderId,
            contentId = currentContentId,
            contentType = currentContentType,
            positionMs = pos,
            durationMs = dur,
            seriesId = currentSeriesId,
            seasonNumber = currentEpisode.value?.seasonNumber,
            episodeNumber = currentEpisode.value?.episodeNumber,
            forceCloudSync = forceCloudSync,
            playbackSessionId = currentPlaybackSessionId,
            baseRevision = currentBaseRevision,
            checkpointSeq = currentCheckpointSeq
        )

        // Trakt.tv scrobble pause/progress update (Fire-and-forget, zero traffic if disconnected)
        val progressPercent = (pos.toFloat() / dur.toFloat() * 100f).coerceIn(0f, 100f)
        traktRepository.scrobblePause(
            title = playbackTitleFlow.value.ifBlank { "Unknown" },
            contentType = currentContentType,
            progressPercent = progressPercent,
            seasonNumber = currentEpisode.value?.seasonNumber,
            episodeNumber = currentEpisode.value?.episodeNumber
        )
    }
}

internal fun PlayerViewModel.startTokenRenewalMonitoring(expirationTime: Long?) {
    tokenRenewalJob?.cancel()
    tokenRenewalJob = null
    val expiry = expirationTime?.takeIf { it > 0L } ?: return
    val requestVersion = prepareRequestVersion
    tokenRenewalJob = viewModelScope.launch {
        while (true) {
            delay(LIFECYCLE_TOKEN_RENEWAL_CHECK_INTERVAL_MS)
            if (!playerEngine.isPlaying.value) continue
            val remaining = expiry - System.currentTimeMillis()
            if (remaining > LIFECYCLE_TOKEN_RENEWAL_LEAD_MS) continue
            if (!isActivePlaybackSession(requestVersion)) return@launch
            val refreshed = resolvePlaybackStreamInfo(
                logicalUrl = currentStreamUrl,
                internalContentId = currentContentId,
                providerId = currentProviderId,
                contentType = currentContentType
            ) ?: return@launch
            if (!isActivePlaybackSession(requestVersion)) return@launch
            currentResolvedPlaybackUrl = refreshed.url
            currentResolvedStreamInfo = refreshed
            playerEngine.renewStreamUrl(refreshed)
            startTokenRenewalMonitoring(refreshed.expirationTime)
            return@launch
        }
    }
}

fun PlayerViewModel.onAppBackgrounded() {
    if (!isAppInForeground) return
    isAppInForeground = false
    shouldResumeAfterForeground = playerEngine.isPlaying.value
    if (shouldResumeAfterForeground) {
        playerEngine.pause()
    }
    if (currentContentType != ContentType.LIVE) {
        viewModelScope.launch {
            persistPlaybackProgress(forceCloudSync = true)
            playbackHistoryRepository.flushPendingProgress()
        }
    }
}

fun PlayerViewModel.onAppForegrounded() {
    if (isAppInForeground) {
        if (shouldResumeAfterForeground && !resumePrompt.value.show && !playerEngine.isPlaying.value) {
            playerEngine.play()
            shouldResumeAfterForeground = false
        }
        return
    }
    isAppInForeground = true
    if (shouldResumeAfterForeground && !resumePrompt.value.show) {
        playerEngine.play()
    }
    shouldResumeAfterForeground = false
}

fun PlayerViewModel.onPictureInPictureDismissed() {
    isAppInForeground = false
    shouldResumeAfterForeground = false
    playerEngine.pause()
    if (currentContentType != ContentType.LIVE) {
        viewModelScope.launch {
            persistPlaybackProgress(forceCloudSync = true)
            playbackHistoryRepository.flushPendingProgress()
        }
    }
    playerEngine.stopLiveTimeshift()
    stopLiveTranslationSession()
    clearPlaybackTimers()
}

fun PlayerViewModel.onPlayerScreenDisposed() {
    val activeEngine = playerEngine
    activeEngine.pause()
    activeEngine.stop()
    if (activeEngine !== mainPlayerEngine) {
        livePreviewHandoffManager.clear(activeEngine)
        activeEngine.release()
        setActivePlayerEngine(mainPlayerEngine)
    } else {
        mainPlayerEngine.resetForReuse()
    }
    if (currentContentType != ContentType.LIVE) {
        viewModelScope.launch {
            persistPlaybackProgress(forceCloudSync = true)
            playbackHistoryRepository.flushPendingProgress()
        }
    }
    playerEngine.stopLiveTimeshift()
    stopLiveTranslationSession()
    clearPlaybackTimers()
    livePreviewHandoffManager.clear(playerEngine)
}

internal fun PlayerViewModel.clearPlaybackTimers() {
    stopPlaybackTimerJob?.cancel()
    idleStandbyTimerJob?.cancel()
    stopPlaybackTimerJob = null
    idleStandbyTimerJob = null
    stopPlaybackTimerEndsAtMs = 0L
    idleStandbyTimerEndsAtMs = 0L
    playbackTimerDefaultsApplied = false
    sleepTimerExitEmitted = false
    _sleepTimerUiState.value = SleepTimerUiState()
}

fun PlayerViewModel.handOffPlaybackToMultiView() {
    if (currentContentType != ContentType.LIVE) {
        viewModelScope.launch { persistPlaybackProgress() }
    }
    playerEngine.stopLiveTimeshift()
    stopLiveTranslationSession()
    livePreviewHandoffManager.clear(playerEngine)
}

internal fun PlayerViewModel.cleanupAfterCleared(mainPlayerEngine: PlayerEngine) {
    onPlayerScreenDisposed()
    channelInfoHideJob?.cancel()
    liveOverlayHideJob?.cancel()
    diagnosticsHideJob?.cancel()
    numericInputCommitJob?.cancel()
    numericInputFeedbackJob?.cancel()
    playerNoticeHideJob?.cancel()
    epgJob?.cancel()
    playlistJob?.cancel()
    controlsHideJob?.cancel()
    zapOverlayJob?.cancel()
    zapBufferWatchdogJob?.cancel()
    progressTrackingJob?.cancel()
    tokenRenewalJob?.cancel()
    aspectRatioJob?.cancel()
    recentChannelsJob?.cancel()
    lastVisitedCategoryJob?.cancel()
    thumbnailPreloadJob?.cancel()
    inFlightThumbnailPreloadKey = null
    lastCompletedThumbnailPreloadKey = null
    seekThumbnailProvider.clearCache()

    val activeEngine = playerEngine
    val channel = currentChannel.value
    val streamInfo = currentResolvedStreamInfo
    val canReverseHandoff = currentContentType == ContentType.LIVE
        && !isCatchUpPlayback.value
        && activeEngine !== mainPlayerEngine
        && channel != null
        && streamInfo != null
        && activeEngine.playbackState.value != PlaybackState.ERROR

    if (canReverseHandoff) {
        val safeChannel = channel ?: return
        val safeStreamInfo = streamInfo ?: return
        livePreviewHandoffManager.beginReverseHandoff(
            channel = safeChannel,
            streamInfo = safeStreamInfo,
            engine = activeEngine,
            source = com.kaynanamtv.app.player.PreviewHandoffSource.HOME
        )
        mainPlayerEngine.resetForReuse()
    } else {
        livePreviewHandoffManager.clear(activeEngine)
        if (activeEngine === mainPlayerEngine) {
            mainPlayerEngine.resetForReuse()
        } else {
            activeEngine.release()
            mainPlayerEngine.resetForReuse()
        }
    }
}
