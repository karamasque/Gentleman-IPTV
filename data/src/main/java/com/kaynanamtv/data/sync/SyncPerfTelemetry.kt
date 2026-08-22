package com.kaynanamtv.data.sync

import android.util.Log
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicLong

/**
 * Thread-safe runtime telemetry for IPTV synchronization performance analysis.
 * Log prefix: [SYNC_PERF]
 * Summary prefix: [SYNC_PERF_SUMMARY]
 *
 * Captures granular timings, concurrency milestones, network overhead, and database insertion latency
 * while ensuring sensitive credentials/tokens are never leaked.
 */
class SyncPerfTelemetry(
    val providerId: Long,
    val providerType: String
) {
    val syncStartTime = System.currentTimeMillis()

    val authMs = AtomicLong(0)

    val liveCategoriesMs = AtomicLong(0)
    val liveTtfbMs = AtomicLong(0)
    val liveDownloadMs = AtomicLong(0)
    val liveParseMs = AtomicLong(0)
    val liveDbMs = AtomicLong(0)
    val liveTotalMs = AtomicLong(0)
    val liveStartTime = AtomicLong(0)
    val liveEndTime = AtomicLong(0)

    val moviesCategoriesMs = AtomicLong(0)
    val moviesTtfbMs = AtomicLong(0)
    val moviesDownloadMs = AtomicLong(0)
    val moviesParseMs = AtomicLong(0)
    val moviesDbMs = AtomicLong(0)
    val moviesTotalMs = AtomicLong(0)
    val moviesStartTime = AtomicLong(0)
    val moviesEndTime = AtomicLong(0)

    val seriesCategoriesMs = AtomicLong(0)
    val seriesTtfbMs = AtomicLong(0)
    val seriesDownloadMs = AtomicLong(0)
    val seriesParseMs = AtomicLong(0)
    val seriesDbMs = AtomicLong(0)
    val seriesTotalMs = AtomicLong(0)
    val seriesStartTime = AtomicLong(0)
    val seriesEndTime = AtomicLong(0)

    val httpRequestCount = AtomicInteger(0)
    val retryCount = AtomicInteger(0)
    val retryWaitMs = AtomicLong(0)

    val ftsMs = AtomicLong(0)
    val epgMs = AtomicLong(0)
    val tmdbMs = AtomicLong(0)
    val postProcessMs = AtomicLong(0)
    val fullSyncTotalMs = AtomicLong(0)

    fun logEvent(event: String, details: String = "") {
        val detailSuffix = if (details.isNotBlank()) " | $details" else ""
        Log.i(TAG, "[SYNC_PERF] provider=$providerId type=$providerType event=$event t=${System.currentTimeMillis() - syncStartTime}ms$detailSuffix")
    }

    fun recordAuth(ms: Long) {
        authMs.set(ms)
        Log.i(TAG, "[SYNC_PERF] AUTH_MS=$ms (provider=$providerId)")
    }

    fun markLiveStart() {
        val now = System.currentTimeMillis()
        liveStartTime.set(now)
        Log.i(TAG, "[SYNC_PERF] LIVE_START ts=$now (provider=$providerId)")
    }

    fun markLiveEnd(totalMs: Long) {
        val now = System.currentTimeMillis()
        liveEndTime.set(now)
        liveTotalMs.set(totalMs)
        Log.i(
            TAG,
            "[SYNC_PERF] LIVE_FINISHED ts=$now LIVE_CATEGORIES_MS=${liveCategoriesMs.get()} " +
                "LIVE_TTFB_MS=${liveTtfbMs.get()} LIVE_DOWNLOAD_MS=${liveDownloadMs.get()} " +
                "LIVE_PARSE_MS=${liveParseMs.get()} LIVE_DB_MS=${liveDbMs.get()} " +
                "LIVE_TOTAL_MS=$totalMs (provider=$providerId)"
        )
    }

    fun markMoviesStart() {
        val now = System.currentTimeMillis()
        moviesStartTime.set(now)
        Log.i(TAG, "[SYNC_PERF] MOVIES_START ts=$now (provider=$providerId)")
    }

    fun markMoviesEnd(totalMs: Long) {
        val now = System.currentTimeMillis()
        moviesEndTime.set(now)
        moviesTotalMs.set(totalMs)
        Log.i(
            TAG,
            "[SYNC_PERF] MOVIES_FINISHED ts=$now MOVIES_CATEGORIES_MS=${moviesCategoriesMs.get()} " +
                "MOVIES_TTFB_MS=${moviesTtfbMs.get()} MOVIES_DOWNLOAD_MS=${moviesDownloadMs.get()} " +
                "MOVIES_PARSE_MS=${moviesParseMs.get()} MOVIES_DB_MS=${moviesDbMs.get()} " +
                "MOVIES_TOTAL_MS=$totalMs (provider=$providerId)"
        )
    }

    fun markSeriesStart() {
        val now = System.currentTimeMillis()
        seriesStartTime.set(now)
        Log.i(TAG, "[SYNC_PERF] SERIES_START ts=$now (provider=$providerId)")
    }

    fun markSeriesEnd(totalMs: Long) {
        val now = System.currentTimeMillis()
        seriesEndTime.set(now)
        seriesTotalMs.set(totalMs)
        Log.i(
            TAG,
            "[SYNC_PERF] SERIES_FINISHED ts=$now SERIES_CATEGORIES_MS=${seriesCategoriesMs.get()} " +
                "SERIES_TTFB_MS=${seriesTtfbMs.get()} SERIES_DOWNLOAD_MS=${seriesDownloadMs.get()} " +
                "SERIES_PARSE_MS=${seriesParseMs.get()} SERIES_DB_MS=${seriesDbMs.get()} " +
                "SERIES_TOTAL_MS=$totalMs (provider=$providerId)"
        )
    }

    fun recordHttpRequest(url: String? = null, durationMs: Long? = null) {
        httpRequestCount.incrementAndGet()
        val safeUrl = maskCredentials(url)
        val timing = if (durationMs != null) " duration=${durationMs}ms" else ""
        Log.d(TAG, "[SYNC_PERF] HTTP_REQ #${httpRequestCount.get()}$timing url=$safeUrl")
    }

    fun recordRetry(waitMs: Long) {
        retryCount.incrementAndGet()
        retryWaitMs.addAndGet(waitMs)
        Log.w(TAG, "[SYNC_PERF] RETRY #${retryCount.get()} wait=${waitMs}ms (totalRetryWait=${retryWaitMs.get()}ms)")
    }

    fun recordFts(ms: Long) {
        ftsMs.set(ms)
        Log.i(TAG, "[SYNC_PERF] FTS_MS=$ms")
    }

    fun recordEpg(ms: Long) {
        epgMs.set(ms)
        Log.i(TAG, "[SYNC_PERF] EPG_MS=$ms")
    }

    fun recordTmdb(ms: Long) {
        tmdbMs.set(ms)
        Log.i(TAG, "[SYNC_PERF] TMDB_MS=$ms")
    }

    fun recordPostProcess(ms: Long) {
        postProcessMs.set(ms)
        Log.i(TAG, "[SYNC_PERF] POST_PROCESS_MS=$ms")
    }

    fun logSummary(totalMs: Long) {
        fullSyncTotalMs.set(totalMs)
        val lStart = liveStartTime.get()
        val mStart = moviesStartTime.get()
        val sStart = seriesStartTime.get()
        val parallelInfo = if (mStart > 0 && sStart > 0 && Math.abs(mStart - sStart) < 5000) {
            "PARALLEL_VOD_SERIES=TRUE"
        } else {
            "PARALLEL_VOD_SERIES=FALSE"
        }

        Log.i(
            TAG,
            "[SYNC_PERF_SUMMARY] provider=$providerId type=$providerType " +
                "AUTH_MS=${authMs.get()} " +
                "LIVE_CATEGORIES_MS=${liveCategoriesMs.get()} LIVE_TTFB_MS=${liveTtfbMs.get()} " +
                "LIVE_DOWNLOAD_MS=${liveDownloadMs.get()} LIVE_PARSE_MS=${liveParseMs.get()} " +
                "LIVE_DB_MS=${liveDbMs.get()} LIVE_TOTAL_MS=${liveTotalMs.get()} " +
                "MOVIES_CATEGORIES_MS=${moviesCategoriesMs.get()} MOVIES_TTFB_MS=${moviesTtfbMs.get()} " +
                "MOVIES_DOWNLOAD_MS=${moviesDownloadMs.get()} MOVIES_PARSE_MS=${moviesParseMs.get()} " +
                "MOVIES_DB_MS=${moviesDbMs.get()} MOVIES_TOTAL_MS=${moviesTotalMs.get()} " +
                "SERIES_CATEGORIES_MS=${seriesCategoriesMs.get()} SERIES_TTFB_MS=${seriesTtfbMs.get()} " +
                "SERIES_DOWNLOAD_MS=${seriesDownloadMs.get()} SERIES_PARSE_MS=${seriesParseMs.get()} " +
                "SERIES_DB_MS=${seriesDbMs.get()} SERIES_TOTAL_MS=${seriesTotalMs.get()} " +
                "HTTP_REQUEST_COUNT=${httpRequestCount.get()} RETRY_COUNT=${retryCount.get()} " +
                "RETRY_WAIT_MS=${retryWaitMs.get()} FTS_MS=${ftsMs.get()} EPG_MS=${epgMs.get()} " +
                "TMDB_MS=${tmdbMs.get()} POST_PROCESS_MS=${postProcessMs.get()} " +
                "FULL_SYNC_TOTAL_MS=$totalMs $parallelInfo"
        )
    }

    companion object {
        private const val TAG = "SYNC_PERF"

        fun maskCredentials(url: String?): String {
            if (url == null) return ""
            return url.replace(Regex("(username|password|token|pass)=([^&]+)", RegexOption.IGNORE_CASE)) { matchResult ->
                "${matchResult.groupValues[1]}=***"
            }
        }
    }
}
