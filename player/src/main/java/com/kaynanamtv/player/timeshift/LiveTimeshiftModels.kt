package com.kaynanamtv.player.timeshift

import com.kaynanamtv.domain.model.TimeshiftBackendPreference

data class TimeshiftConfig(
    val enabled: Boolean = false,
    val depthMinutes: Int = 30,
    val backendPreference: TimeshiftBackendPreference = TimeshiftBackendPreference.AUTOMATIC
) {
    val depthMs: Long = depthMinutes.coerceIn(15, 60) * 60_000L

    fun effectiveDepthMs(backend: LiveTimeshiftBackend): Long =
        if (backend == LiveTimeshiftBackend.MEMORY)
            depthMinutes.coerceIn(1, MAX_MEMORY_BACKEND_DEPTH_MINUTES) * 60_000L
        else
            depthMs

    companion object {
        const val MAX_MEMORY_BACKEND_DEPTH_MINUTES = 5
    }
}

enum class LiveTimeshiftBackend {
    NONE,
    DISK,
    MEMORY
}

internal fun resolveLiveTimeshiftBackend(
    preference: TimeshiftBackendPreference,
    snapshotStorageAvailable: Boolean,
    diskStorageAvailable: Boolean
): LiveTimeshiftBackend? {
    if (!snapshotStorageAvailable) return null

    return when (preference) {
        TimeshiftBackendPreference.AUTOMATIC ->
            if (diskStorageAvailable) LiveTimeshiftBackend.DISK else LiveTimeshiftBackend.MEMORY

        TimeshiftBackendPreference.STORAGE ->
            LiveTimeshiftBackend.DISK.takeIf { diskStorageAvailable }

        TimeshiftBackendPreference.MEMORY -> LiveTimeshiftBackend.MEMORY
    }
}

enum class LiveTimeshiftStatus {
    DISABLED,
    UNSUPPORTED,
    PREPARING,
    LIVE,
    LIVE_PAUSED,
    SNAPSHOT_PLAYING,
    SNAPSHOT_PAUSED,
    RETURNING_TO_LIVE,
    PAUSED_BEHIND_LIVE,
    PLAYING_BEHIND_LIVE,
    BUFFERING,
    FAILED
}

object LiveTimeshiftSeekConverter {
    fun offsetToAbsolute(offsetBehindLiveMs: Long, bufferDepthMs: Long): Long {
        if (bufferDepthMs <= 0L) return 0L
        return (bufferDepthMs - offsetBehindLiveMs).coerceIn(0L, bufferDepthMs)
    }

    fun absoluteToOffset(absolutePositionMs: Long, bufferDepthMs: Long): Long {
        if (bufferDepthMs <= 0L) return 0L
        return (bufferDepthMs - absolutePositionMs).coerceIn(0L, bufferDepthMs)
    }
}

data class LiveTimeshiftState(
    val enabled: Boolean = false,
    val supported: Boolean = false,
    val backend: LiveTimeshiftBackend = LiveTimeshiftBackend.NONE,
    val status: LiveTimeshiftStatus = LiveTimeshiftStatus.DISABLED,
    val bufferStartMs: Long = 0L,
    val bufferEndMs: Long = 0L,
    val liveEdgePositionMs: Long = 0L,
    val currentOffsetFromLiveMs: Long = 0L,
    val bufferedDurationMs: Long = 0L,
    val message: String? = null
) {
    val canSeekToLive: Boolean = supported && (
        status == LiveTimeshiftStatus.LIVE_PAUSED ||
        status == LiveTimeshiftStatus.SNAPSHOT_PLAYING ||
        status == LiveTimeshiftStatus.SNAPSHOT_PAUSED ||
        status == LiveTimeshiftStatus.PAUSED_BEHIND_LIVE ||
        status == LiveTimeshiftStatus.PLAYING_BEHIND_LIVE ||
        currentOffsetFromLiveMs > 1_000L
    )
    val isActive: Boolean = enabled && supported && status != LiveTimeshiftStatus.DISABLED && status != LiveTimeshiftStatus.UNSUPPORTED
}

internal data class LiveTimeshiftSnapshot(
    val url: String,
    val durationMs: Long,
    val backend: LiveTimeshiftBackend
)
