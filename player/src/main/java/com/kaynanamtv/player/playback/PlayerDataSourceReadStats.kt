package com.kaynanamtv.player.playback

import android.net.Uri
import android.os.SystemClock
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.IOException

@UnstableApi
internal class PlayerDataSourceReadStatsFactory(
    private val upstream: DataSource.Factory,
    private val resolvedStreamType: ResolvedStreamType,
    private val initialTargetUrl: String,
    private val clockMs: () -> Long = SystemClock::elapsedRealtime
) : DataSource.Factory {
    override fun createDataSource(): DataSource =
        PlayerDataSourceReadStatsDataSource(
            upstream = upstream.createDataSource(),
            resolvedStreamType = resolvedStreamType,
            initialTargetUrl = initialTargetUrl,
            clockMs = clockMs
        )
}

internal fun shouldWrapDataSourceReadStats(resolvedStreamType: ResolvedStreamType): Boolean =
    resolvedStreamType == ResolvedStreamType.HLS ||
        resolvedStreamType == ResolvedStreamType.MPEG_TS_LIVE

@UnstableApi
object PlayerDataSourceReadStatsRegistry {
    private val activeTrackers = java.util.concurrent.ConcurrentHashMap<String, PlayerDataSourceReadStatsTracker>()

    internal fun register(target: String, tracker: PlayerDataSourceReadStatsTracker) {
        activeTrackers[target] = tracker
    }

    internal fun unregister(target: String) {
        activeTrackers.remove(target)
    }

    fun lastBytesAgoMs(url: String?, nowMs: Long = SystemClock.elapsedRealtime()): Long {
        if (url == null) return 0L
        val sanitized = PlaybackLogSanitizer.sanitizeUrl(url)
        return activeTrackers[sanitized]?.lastByteReceivedAgoMs(nowMs)
            ?: activeTrackers.values.minOfOrNull { it.lastByteReceivedAgoMs(nowMs) }
            ?: 0L
    }
}

@UnstableApi
private class PlayerDataSourceReadStatsDataSource(
    private val upstream: DataSource,
    private val resolvedStreamType: ResolvedStreamType,
    initialTargetUrl: String,
    private val clockMs: () -> Long
) : DataSource {
    private val tracker = PlayerDataSourceReadStatsTracker()
    private var target = PlaybackLogSanitizer.sanitizeUrl(initialTargetUrl)
    private var opened = false

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long {
        target = PlaybackLogSanitizer.sanitizeUrl(dataSpec.uri.toString())
        val length = upstream.open(dataSpec)
        tracker.open(clockMs())
        PlayerDataSourceReadStatsRegistry.register(target, tracker)
        opened = true
        if (readStatsLoggingEnabled()) {
            Log.d(
                TAG,
                "read-open streamType=$resolvedStreamType position=${dataSpec.position} length=$length target=$target"
            )
        }
        return length
    }

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val bytesRead = upstream.read(buffer, offset, length)
        if (bytesRead > 0) {
            val snapshot = tracker.recordRead(bytesRead, clockMs())
            if (snapshot != null && readStatsLoggingEnabled()) {
                logProgress(snapshot)
            }
        }
        return bytesRead
    }

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    @Throws(IOException::class)
    override fun close() {
        try {
            upstream.close()
        } finally {
            PlayerDataSourceReadStatsRegistry.unregister(target)
            if (opened && readStatsLoggingEnabled()) {
                logClose(tracker.snapshot(clockMs()))
            }
            opened = false
        }
    }

    private fun logProgress(snapshot: PlayerDataSourceReadStatsSnapshot) {
        Log.d(
            TAG,
            "read-progress streamType=$resolvedStreamType bytes=${snapshot.totalBytes} " +
                "deltaBytes=${snapshot.deltaBytes} elapsedMs=${snapshot.elapsedMs} " +
                "intervalMs=${snapshot.intervalMs} avgKbps=${snapshot.averageKbps} " +
                "intervalKbps=${snapshot.intervalKbps} target=$target"
        )
    }

    private fun logClose(snapshot: PlayerDataSourceReadStatsSnapshot) {
        Log.d(
            TAG,
            "read-close streamType=$resolvedStreamType bytes=${snapshot.totalBytes} " +
                "elapsedMs=${snapshot.elapsedMs} avgKbps=${snapshot.averageKbps} target=$target"
        )
    }

    private fun readStatsLoggingEnabled(): Boolean = Log.isLoggable(TAG, Log.DEBUG)

    private companion object {
        const val TAG = "PlayerDataReadStats"
    }
}

internal class PlayerDataSourceReadStatsTracker(
    private val minLogIntervalMs: Long = MIN_LOG_INTERVAL_MS,
    private val minLogDeltaBytes: Long = MIN_LOG_DELTA_BYTES
) {
    private var openedAtMs: Long = 0L
    private var lastLogAtMs: Long = 0L
    private var lastLogBytes: Long = 0L
    private var totalBytes: Long = 0L
    private var lastByteAtMs: Long = 0L

    fun open(nowMs: Long) {
        openedAtMs = nowMs
        lastLogAtMs = nowMs
        lastLogBytes = 0L
        totalBytes = 0L
        lastByteAtMs = nowMs
    }

    fun recordRead(bytesRead: Int, nowMs: Long): PlayerDataSourceReadStatsSnapshot? {
        if (bytesRead <= 0) return null

        lastByteAtMs = nowMs
        totalBytes += bytesRead.toLong()
        val intervalMs = nowMs - lastLogAtMs
        val deltaBytes = totalBytes - lastLogBytes
        if (intervalMs < minLogIntervalMs && deltaBytes < minLogDeltaBytes) {
            return null
        }

        return snapshot(nowMs).also {
            lastLogAtMs = nowMs
            lastLogBytes = totalBytes
        }
    }

    fun lastByteReceivedAgoMs(nowMs: Long): Long {
        val last = lastByteAtMs
        return if (last <= 0L) (nowMs - openedAtMs).coerceAtLeast(0L) else (nowMs - last).coerceAtLeast(0L)
    }

    fun snapshot(nowMs: Long): PlayerDataSourceReadStatsSnapshot {
        val elapsedMs = (nowMs - openedAtMs).coerceAtLeast(0L)
        val intervalMs = (nowMs - lastLogAtMs).coerceAtLeast(0L)
        val deltaBytes = totalBytes - lastLogBytes
        return PlayerDataSourceReadStatsSnapshot(
            totalBytes = totalBytes,
            deltaBytes = deltaBytes,
            elapsedMs = elapsedMs,
            intervalMs = intervalMs,
            averageKbps = bytesToKbps(totalBytes, elapsedMs),
            intervalKbps = bytesToKbps(deltaBytes, intervalMs)
        )
    }

    private fun bytesToKbps(bytes: Long, elapsedMs: Long): Long {
        if (bytes <= 0L || elapsedMs <= 0L) return 0L
        return (bytes * 8_000L) / elapsedMs / 1_000L
    }

    companion object {
        const val MIN_LOG_INTERVAL_MS = 2_000L
        const val MIN_LOG_DELTA_BYTES = 512L * 1024L
    }
}

internal data class PlayerDataSourceReadStatsSnapshot(
    val totalBytes: Long,
    val deltaBytes: Long,
    val elapsedMs: Long,
    val intervalMs: Long,
    val averageKbps: Long,
    val intervalKbps: Long
)
