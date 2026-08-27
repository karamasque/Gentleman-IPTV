package com.kaynanamtv.app.ui.screens.player

import androidx.lifecycle.viewModelScope
import com.kaynanamtv.data.security.CredentialDecryptionException
import com.kaynanamtv.domain.model.ContentType
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

internal fun PlayerViewModel.finalizePreparedPlaybackContext(
    requestVersion: Long,
    streamUrl: String,
    providerId: Long,
    categoryId: Long,
    isVirtual: Boolean,
    internalChannelId: Long,
    epgChannelId: String?,
    shouldReloadPlaylist: Boolean,
    hasArchiveRequest: Boolean,
    archiveStartMs: Long?,
    archiveEndMs: Long?,
    archiveTitle: String?
) {
    if (currentContentType == ContentType.LIVE) {
        if (shouldReloadPlaylist) {
            currentCategoryId = categoryId
            activeCategoryIdFlow.value = categoryId
            isVirtualCategory = isVirtual
            loadPlaylist(categoryId, providerId, isVirtual, internalChannelId)
        } else {
            if (channelList.isNotEmpty() && internalChannelId != -1L) {
                currentChannelIndex = channelList.indexOfFirst { it.id == internalChannelId }
                if (currentChannelIndex == -1) {
                    currentChannelIndex = channelList.indexOfFirst { it.streamUrl == streamUrl }
                }
            }
        }
    } else {
        playlistJob?.cancel()
        playlistJob = null
        channelList = emptyList()
        currentChannelFlowList.value = emptyList()
        currentChannelFlow.value = null
        currentChannelIndex = -1
        previousChannelIndex = -1
    }

    if (currentContentType == ContentType.LIVE && hasArchiveRequest) {
        playerEngine.stopLiveTimeshift()
        viewModelScope.launch {
            val catchUpUrls = try {
                providerRepository.buildCatchUpUrls(
                    providerId = currentProviderId,
                    streamId = currentContentId,
                    start = (archiveStartMs ?: 0L) / 1000L,
                    end = (archiveEndMs ?: 0L) / 1000L
                )
            } catch (e: CredentialDecryptionException) {
                if (!isActivePlaybackSession(requestVersion, streamUrl)) return@launch
                setLastFailureReason(e.message ?: CredentialDecryptionException.MESSAGE)
                showPlayerNotice(
                    message = e.message ?: CredentialDecryptionException.MESSAGE,
                    recoveryType = PlayerRecoveryType.SOURCE
                )
                return@launch
            }
            if (!isActivePlaybackSession(requestVersion, streamUrl)) return@launch
            if (catchUpUrls.isNotEmpty()) {
                startCatchUpPlayback(
                    urls = catchUpUrls,
                    title = archiveTitle?.takeIf { it.isNotBlank() } ?: currentTitle,
                    recoveryAction = "Opened catch-up stream",
                    requestVersionOverride = requestVersion
                )
            } else {
                val reason = resolveCatchUpFailureMessage(currentChannelFlow.value, archiveRequested = true, programHasArchive = true)
                setLastFailureReason(reason)
                showPlayerNotice(
                    message = reason,
                    recoveryType = PlayerRecoveryType.CATCH_UP,
                    actions = buildRecoveryActions(PlayerRecoveryType.CATCH_UP)
                )
            }
        }
    }

    if (currentContentType == ContentType.LIVE) {
        requestEpg(
            providerId = currentProviderId,
            epgChannelId = epgChannelId,
            internalChannelId = internalChannelId
        )
        observeRecentChannels()
        observeLastVisitedCategory()
    } else {
        requestEpg(providerId = -1L, epgChannelId = null)
        currentChannelFlow.value = null
    }

    aspectRatioJob?.cancel()
    _aspectRatio.value = AspectRatio.FIT
    if (currentContentType == ContentType.LIVE && shouldResolveChannelPlaybackContext(currentContentType.name, internalChannelId)) {
        aspectRatioJob = viewModelScope.launch {
            preferencesRepository.getAspectRatioForChannel(internalChannelId).collect { savedRatio ->
                _aspectRatio.value = AspectRatio.fromPersisted(savedRatio)
            }
        }

        viewModelScope.launch {
            val channel = channelRepository.getChannel(internalChannelId)
            if (!isActivePlaybackSession(requestVersion, streamUrl) || currentContentType != ContentType.LIVE) return@launch
            currentChannelFlow.value = channel
            refreshCurrentChannelRecording()
            if (channel != null) {
                currentTitle = channel.name.ifBlank { currentTitle }
                playbackTitleFlow.value = currentTitle
                currentStreamUrl = if (isCatchUpPlayback()) currentStreamUrl else channel.streamUrl
                updateStreamClass(
                    when {
                        isCatchUpPlayback() -> "Catch-up"
                        streamUrl == channel.streamUrl -> "Primary"
                        channel.alternativeStreams.contains(streamUrl) -> "Alternate"
                        else -> "Direct"
                    }
                )
                if (currentContentType == ContentType.LIVE) {
                    requestEpg(
                        providerId = currentProviderId,
                        epgChannelId = channel.epgChannelId,
                        streamId = channel.streamId,
                        internalChannelId = channel.id
                    )
                }
                updateChannelDiagnostics(channel)
            }
        }
    }

    startProgressTracking()
}
