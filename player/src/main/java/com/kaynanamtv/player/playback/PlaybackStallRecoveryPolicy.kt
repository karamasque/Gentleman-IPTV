package com.kaynanamtv.player.playback

import com.kaynanamtv.player.PlaybackState

enum class PlaybackStallCategory {
    NETWORK_STARVATION,
    SERVER_NO_BYTES,
    EOF,
    HTTP_TIMEOUT,
    HLS_LIVE_WINDOW,
    DECODER_STALL,
    UNKNOWN
}

internal fun classifyPlaybackStall(
    bufferedDurationMs: Long,
    lastFrameAgoMs: Long,
    lastBytesAgoMs: Long,
    playbackState: PlaybackState,
    lastError: Throwable?
): PlaybackStallCategory {
    val errorName = lastError?.javaClass?.simpleName.orEmpty()
    val errorMessage = lastError?.message.orEmpty()

    return when {
        lastError is androidx.media3.exoplayer.source.BehindLiveWindowException ||
            errorMessage.contains("BehindLiveWindowException", ignoreCase = true) ->
            PlaybackStallCategory.HLS_LIVE_WINDOW

        errorName.contains("SocketTimeout", ignoreCase = true) ||
            errorMessage.contains("timeout", ignoreCase = true) ->
            PlaybackStallCategory.HTTP_TIMEOUT

        errorName.contains("EOF", ignoreCase = true) ||
            errorMessage.contains("unexpected end of stream", ignoreCase = true) ->
            PlaybackStallCategory.EOF

        bufferedDurationMs > 3_000L && lastFrameAgoMs > 6_000L ->
            PlaybackStallCategory.DECODER_STALL

        bufferedDurationMs <= 500L && lastBytesAgoMs > 3_000L ->
            PlaybackStallCategory.SERVER_NO_BYTES

        bufferedDurationMs <= 500L ->
            PlaybackStallCategory.NETWORK_STARVATION

        else -> PlaybackStallCategory.UNKNOWN
    }
}

internal fun shouldRecoverReadyStalls(resolvedStreamType: ResolvedStreamType): Boolean =
    true

internal fun shouldRecoverPositionAdvancingReadyStalls(resolvedStreamType: ResolvedStreamType): Boolean =
    !resolvedStreamType.isLiveForStallRecovery

internal fun shouldRecoverFrameSilentReadyStalls(resolvedStreamType: ResolvedStreamType): Boolean =
    resolvedStreamType.isLiveForStallRecovery

internal fun shouldReconnectLiveStall(
    playbackState: PlaybackState,
    resolvedStreamType: ResolvedStreamType,
    recoveryAttempt: Int
): Boolean =
    recoveryAttempt <= 1 &&
        (
            (playbackState == PlaybackState.BUFFERING || playbackState == PlaybackState.READY) &&
                resolvedStreamType.isLiveForStallRecovery
        )

private val ResolvedStreamType.isLiveForStallRecovery: Boolean
    get() = this == ResolvedStreamType.HLS ||
        this == ResolvedStreamType.SMOOTH_STREAMING ||
        this == ResolvedStreamType.MPEG_TS_LIVE ||
        this == ResolvedStreamType.RTSP
