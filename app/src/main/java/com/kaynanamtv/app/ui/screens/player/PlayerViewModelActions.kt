package com.kaynanamtv.app.ui.screens.player

import androidx.lifecycle.viewModelScope
import com.kaynanamtv.app.R
import com.kaynanamtv.app.cast.CastMediaRequest
import com.kaynanamtv.app.cast.CastMediaRequestBuildResult
import com.kaynanamtv.app.cast.CastMediaRequestUnsupportedReason
import com.kaynanamtv.app.cast.CastPlaybackEvent
import com.kaynanamtv.app.cast.CastPlaybackReportMode
import com.kaynanamtv.app.cast.CastRewriteRequiredReason
import com.kaynanamtv.app.cast.CastStartResult
import com.kaynanamtv.domain.model.ContentType
import com.kaynanamtv.domain.model.RecordingRecurrence
import com.kaynanamtv.domain.model.RecordingRequest
import com.kaynanamtv.domain.model.Result
import com.kaynanamtv.domain.model.StreamInfo
import com.kaynanamtv.domain.usecase.ScheduleRecordingCommand
import kotlinx.coroutines.launch

fun PlayerViewModel.castCurrentMedia(onRouteSelectionRequired: () -> Unit) {
    viewModelScope.launch {
        castPlaybackReportMode = CastPlaybackReportMode.NONE
        val request = when (val result = buildCastRequestResult()) {
            is PlayerCastRequestResult.Success -> result.request
            is PlayerCastRequestResult.Failure -> {
                showPlayerNotice(
                    message = result.message,
                    recoveryType = PlayerRecoveryType.SOURCE
                )
                return@launch
            }
        }

        when (castPlaybackCoordinator.startCasting(request)) {
            CastStartResult.STARTED -> {
                castPlaybackReportMode = CastPlaybackReportMode.SUCCESS_AND_FAILURE
                showPlayerNotice(
                    message = appContext.getString(R.string.cast_started),
                    recoveryType = PlayerRecoveryType.NETWORK
                )
            }

            CastStartResult.ROUTE_SELECTION_REQUIRED -> {
                castPlaybackReportMode = CastPlaybackReportMode.SUCCESS_AND_FAILURE
                onRouteSelectionRequired()
            }

            CastStartResult.UNAVAILABLE -> {
                castPlaybackReportMode = CastPlaybackReportMode.NONE
                showPlayerNotice(
                    message = appContext.getString(R.string.cast_unavailable),
                    recoveryType = PlayerRecoveryType.SOURCE
                )
            }

            CastStartResult.UNSUPPORTED -> {
                castPlaybackReportMode = CastPlaybackReportMode.NONE
                showPlayerNotice(
                    message = toPlayerCastUnsupportedMessage(request),
                    recoveryType = PlayerRecoveryType.SOURCE
                )
            }
        }
    }
}

internal fun PlayerViewModel.observeCastPlaybackEvents() {
    viewModelScope.launch {
        castPlaybackCoordinator.playbackEvents.collect { event ->
            handleCastPlaybackEvent(event)
        }
    }
}

private fun PlayerViewModel.handleCastPlaybackEvent(event: CastPlaybackEvent) {
    val reportMode = castPlaybackReportMode
    if (reportMode == CastPlaybackReportMode.NONE) return
    if (event is CastPlaybackEvent.RouteSelectionCancelled) {
        castPlaybackReportMode = CastPlaybackReportMode.NONE
        return
    }
    val isSuccess = event is CastPlaybackEvent.MediaLoadSucceeded
    if (isSuccess && reportMode == CastPlaybackReportMode.FAILURES_ONLY) {
        castPlaybackReportMode = CastPlaybackReportMode.NONE
        return
    }
    castPlaybackReportMode = CastPlaybackReportMode.NONE
    if (isSuccess) {
        playerEngine.pause()
    }
    showPlayerNotice(
        message = toPlayerCastPlaybackMessage(event),
        recoveryType = if (isSuccess) PlayerRecoveryType.NETWORK else PlayerRecoveryType.SOURCE
    )
}

fun PlayerViewModel.stopCasting() {
    castManager.stopCasting()
    showPlayerNotice(
        message = appContext.getString(R.string.cast_disconnected),
        recoveryType = PlayerRecoveryType.NETWORK
    )
}

fun PlayerViewModel.startManualRecording() {
    val channel = currentChannel.value
    if (currentContentType != ContentType.LIVE || channel == null || currentProviderId <= 0) {
        showPlayerNotice(message = "Recording needs a valid live channel context.")
        return
    }
    viewModelScope.launch {
        val now = System.currentTimeMillis()
        val request = RecordingRequest(
            providerId = currentProviderId,
            channelId = channel.id,
            channelName = channel.name,
            streamUrl = currentStreamUrl,
            scheduledStartMs = now,
            scheduledEndMs = currentProgram.value?.endTime ?: (now + 30 * 60_000L),
            programTitle = currentProgram.value?.title
        )
        // Dosya yolunu hazırla
        val prepResult = recordingManager.prepareTeeRecordingOutput(request)
        if (prepResult is Result.Error) {
            showPlayerNotice(message = prepResult.message, recoveryType = PlayerRecoveryType.SOURCE)
            return@launch
        }
        val (recordingId, outputStream) = (prepResult as Result.Success).data
        // ExoPlayer'ın açık bağlantısına tee ekle (sunucuya ayrı istek atılmaz)
        val started = playerEngine.startTeeCapture(outputStream)
        if (!started) {
            runCatching { outputStream.close() }
            showPlayerNotice(message = "Kayıt başlatılamadı: player hazır değil.", recoveryType = PlayerRecoveryType.SOURCE)
            return@launch
        }
        // recordingId'yi kanalın aktif kaydı olarak sakla
        _activeTeeRecordingId = recordingId
        showPlayerNotice(message = "${channel.name} için kayıt başlatıldı.")
    }
}

fun PlayerViewModel.stopCurrentRecording() {
    val teeId = _activeTeeRecordingId
    if (teeId != null) {
        playerEngine.stopTeeCapture()
        _activeTeeRecordingId = null
        val channel = currentChannel.value
        viewModelScope.launch {
            recordingManager.finalizeTeeRecording(
                recordingId = teeId,
                channelName = channel?.name ?: "",
                programTitle = currentProgram.value?.title
            )
            showPlayerNotice(message = "Kayıt tamamlandı ve galeriye eklendi.")
        }
        return
    }
    // Fallback: zamanlanmış kayıt (TeeCapture yoksa)
    val recording = currentChannelRecording.value ?: return
    viewModelScope.launch {
        val result = recordingManager.stopRecording(recording.id)
        if (result is Result.Error) {
            showPlayerNotice(message = result.message)
        } else {
            showPlayerNotice(message = "Recording stopped.")
        }
    }
}

fun PlayerViewModel.scheduleRecording() {
    scheduleRecordingInternal(RecordingRecurrence.NONE)
}

fun PlayerViewModel.scheduleDailyRecording() {
    scheduleRecordingInternal(RecordingRecurrence.DAILY)
}

fun PlayerViewModel.scheduleWeeklyRecording() {
    scheduleRecordingInternal(RecordingRecurrence.WEEKLY)
}

private fun PlayerViewModel.scheduleRecordingInternal(recurrence: RecordingRecurrence) {
    viewModelScope.launch {
        val result = scheduleRecordingUseCase(
            ScheduleRecordingCommand(
                contentType = currentContentType,
                providerId = currentProviderId,
                channel = currentChannel.value,
                streamUrl = currentStreamUrl,
                currentProgram = currentProgram.value,
                nextProgram = nextProgram.value,
                recurrence = recurrence
            )
        )
        if (result is Result.Error) {
            showPlayerNotice(message = result.message, recoveryType = PlayerRecoveryType.SOURCE)
        } else {
            val recurrenceLabel = when (recurrence) {
                RecordingRecurrence.NONE -> ""
                RecordingRecurrence.DAILY -> " daily"
                RecordingRecurrence.WEEKLY -> " weekly"
            }
            val scheduledItem = (result as? Result.Success)?.data
            val title = scheduledItem?.programTitle ?: "Recording"
            showPlayerNotice(message = "$title scheduled$recurrenceLabel.")
        }
    }
}



internal suspend fun PlayerViewModel.buildCastRequest(): CastMediaRequest? {
    return (buildCastRequestResult() as? PlayerCastRequestResult.Success)?.request
}

internal suspend fun PlayerViewModel.buildCastRequestResult(): PlayerCastRequestResult {
    return when (currentContentType) {
        ContentType.LIVE -> {
            val channel = currentChannel.value
            val repositoryStreamInfo = channel?.let {
                channelRepository.getStreamInfo(it, preferStableUrl = true).getOrNull()
            }
            val rawStreamInfo = repositoryStreamInfo
                ?: currentResolvedStreamInfo?.takeIf { currentContentType == ContentType.LIVE }
                ?: currentStreamUrl.takeIf { it.isNotBlank() }?.let { url ->
                    StreamInfo(url = url, title = mediaTitle.value ?: channel?.name ?: currentTitle)
                }
                ?: return PlayerCastRequestResult.Failure(
                    toPlayerCastMessage(CastMediaRequestUnsupportedReason.STREAM_UNAVAILABLE)
                )

            // Convert raw MPEG-TS (.ts) Xtream live URL to HLS (.m3u8) for Google Cast receiver compatibility
            val castStreamInfo = if (rawStreamInfo.url.contains(".ts", ignoreCase = true) &&
                (rawStreamInfo.url.contains("/live/", ignoreCase = true) || rawStreamInfo.url.contains("live.php", ignoreCase = true))
            ) {
                rawStreamInfo.copy(
                    url = rawStreamInfo.url.replace(Regex("\\.ts($|\\?)", RegexOption.IGNORE_CASE)) { matchResult ->
                        ".m3u8" + matchResult.groupValues[1]
                    }
                )
            } else {
                rawStreamInfo
            }

            val selectedAudio = availableAudioTracks.value.firstOrNull { it.isSelected }
            toPlayerCastRequestResult(
                castMediaRequestFactory.buildFromStreamInfo(
                    streamInfo = castStreamInfo,
                    title = mediaTitle.value ?: channel?.name ?: currentTitle,
                    subtitle = currentProgram.value?.title,
                    artworkUrl = channel?.logoUrl ?: currentArtworkUrl,
                    isLive = true,
                    startPositionMs = 0L,
                    preferredAudioLanguage = selectedAudio?.language,
                    preferredAudioLabel = selectedAudio?.name
                )
            )
        }

        ContentType.MOVIE -> {
            val movie = movieRepository.getMovie(currentContentId)
            val repositoryStreamInfo = movie?.let { movieRepository.getStreamInfo(it).getOrNull() }
            val streamInfo = selectPreferredVodCastStreamInfo(
                activeStreamInfo = currentResolvedStreamInfo.takeIf { currentContentType == ContentType.MOVIE },
                activePlaybackUrl = currentResolvedPlaybackUrl,
                fallbackStreamInfo = repositoryStreamInfo
            )
                ?: return directCastRequest()
            val selectedAudio = availableAudioTracks.value.firstOrNull { it.isSelected }
            toPlayerCastRequestResult(
                castMediaRequestFactory.buildFromStreamInfo(
                    streamInfo = streamInfo,
                    title = currentTitle.ifBlank { movie?.name.orEmpty() },
                    subtitle = movie?.genre,
                    artworkUrl = currentArtworkUrl ?: movie?.posterUrl ?: movie?.backdropUrl,
                    isLive = false,
                    startPositionMs = playerEngine.currentPosition.value,
                    preferredAudioLanguage = selectedAudio?.language,
                    preferredAudioLabel = selectedAudio?.name
                )
            )
        }

        ContentType.SERIES,
        ContentType.SERIES_EPISODE -> buildSeriesCastRequestResult()
    }
}

private suspend fun PlayerViewModel.buildSeriesCastRequestResult(): PlayerCastRequestResult {
    val resolution = resolvePlayerPlaybackStreamInfo(
        logicalUrl = currentStreamUrl,
        internalContentId = currentStableEpisodeId?.takeIf { it > 0L } ?: currentContentId,
        providerId = currentProviderId,
        contentType = currentContentType,
        currentTitle = currentTitle,
        currentSeries = currentSeries.value,
        currentEpisode = currentEpisode.value,
        channelRepository = channelRepository,
        movieRepository = movieRepository,
        seriesRepository = seriesRepository,
        xtreamStreamUrlResolver = xtreamStreamUrlResolver
    )
    resolution.credentialFailureMessage?.let { message ->
        return PlayerCastRequestResult.Failure(message)
    }
    resolution.resolutionFailureMessage?.let { message ->
        return PlayerCastRequestResult.Failure(message)
    }
    val streamInfo = selectPreferredVodCastStreamInfo(
        activeStreamInfo = currentResolvedStreamInfo.takeIf {
            currentContentType == ContentType.SERIES || currentContentType == ContentType.SERIES_EPISODE
        },
        activePlaybackUrl = currentResolvedPlaybackUrl,
        fallbackStreamInfo = resolution.streamInfo
    )
        ?: return PlayerCastRequestResult.Failure(
            toPlayerCastMessage(CastMediaRequestUnsupportedReason.STREAM_UNAVAILABLE)
        )
    val episode = currentEpisode.value
    val series = currentSeries.value
    val castTitle = if (series != null && episode != null) {
        "${series.name} - S${episode.seasonNumber}E${episode.episodeNumber}"
    } else {
        currentTitle.ifBlank { episode?.let(::buildEpisodePlaybackTitle).orEmpty() }
    }
    val selectedAudio = availableAudioTracks.value.firstOrNull { it.isSelected }
    return toPlayerCastRequestResult(
        castMediaRequestFactory.buildFromStreamInfo(
            streamInfo = streamInfo,
            title = castTitle,
            subtitle = episode?.title,
            artworkUrl = currentArtworkUrl ?: episode?.coverUrl ?: series?.posterUrl ?: series?.backdropUrl,
            isLive = false,
            startPositionMs = playerEngine.currentPosition.value,
            preferredAudioLanguage = selectedAudio?.language,
            preferredAudioLabel = selectedAudio?.name
        )
    )
}

internal fun PlayerViewModel.directCastRequest(): PlayerCastRequestResult {
    val url = currentStreamUrl.takeIf { it.isNotBlank() }
        ?: return PlayerCastRequestResult.Failure(
            toPlayerCastMessage(CastMediaRequestUnsupportedReason.EMPTY_URL)
        )
    return toPlayerCastRequestResult(
        castMediaRequestFactory.buildFromStreamInfo(
            streamInfo = StreamInfo(url = url),
            title = currentTitle,
            subtitle = null,
            artworkUrl = currentArtworkUrl,
            isLive = false,
            startPositionMs = playerEngine.currentPosition.value
        )
    )
}

private fun PlayerViewModel.toPlayerCastRequestResult(
    result: CastMediaRequestBuildResult
): PlayerCastRequestResult = when (result) {
    is CastMediaRequestBuildResult.Success -> PlayerCastRequestResult.Success(result.request)
    is CastMediaRequestBuildResult.Unsupported -> PlayerCastRequestResult.Failure(toPlayerCastMessage(result.reason))
}

sealed interface PlayerCastRequestResult {
    data class Success(val request: CastMediaRequest) : PlayerCastRequestResult
    data class Failure(val message: String) : PlayerCastRequestResult
}

private fun PlayerViewModel.toPlayerCastMessage(
    reason: CastMediaRequestUnsupportedReason
): String = appContext.getString(
    when (reason) {
        CastMediaRequestUnsupportedReason.STREAM_UNAVAILABLE,
        CastMediaRequestUnsupportedReason.EMPTY_URL -> R.string.cast_item_unavailable
        CastMediaRequestUnsupportedReason.UNSUPPORTED_PROTOCOL -> R.string.cast_protocol_unsupported
        CastMediaRequestUnsupportedReason.DRM_PROTECTED -> R.string.cast_drm_unsupported
    }
)

private fun PlayerViewModel.toPlayerCastUnsupportedMessage(request: CastMediaRequest): String = appContext.getString(
    when (request.rewriteRequiredReason) {
        CastRewriteRequiredReason.LOCAL_URI -> R.string.cast_local_url_unsupported
        CastRewriteRequiredReason.CUSTOM_HEADERS -> R.string.cast_headers_unsupported
        CastRewriteRequiredReason.CUSTOM_USER_AGENT -> R.string.cast_user_agent_unsupported
        CastRewriteRequiredReason.PROXY -> R.string.cast_proxy_unsupported
        CastRewriteRequiredReason.INVALID_SSL -> R.string.cast_invalid_ssl_unsupported
        null -> R.string.cast_stream_unsupported
    }
)

private fun PlayerViewModel.toPlayerCastPlaybackMessage(event: CastPlaybackEvent): String = appContext.getString(
    when (event) {
        is CastPlaybackEvent.MediaLoadSucceeded -> R.string.cast_started
        is CastPlaybackEvent.MediaLoadFailed -> R.string.cast_load_failed
        is CastPlaybackEvent.SessionStartFailed -> R.string.cast_session_failed
        is CastPlaybackEvent.ReceiverUnavailable -> R.string.cast_receiver_unavailable
        CastPlaybackEvent.RouteSelectionCancelled -> R.string.cast_selection_cancelled
    }
)
