package com.kaynanamtv.player.playback

import com.kaynanamtv.domain.model.StreamInfo
import com.kaynanamtv.domain.model.StreamType
import java.net.URI
import java.util.Locale

enum class ResolvedStreamType {
    HLS,
    DASH,
    SMOOTH_STREAMING,
    PROGRESSIVE,
    MPEG_TS_LIVE,
    RTSP,
    UNKNOWN
}

object StreamTypeResolver {
    private val progressiveExtensions = listOf(
        ".mp4", ".mkv", ".avi", ".mov", ".mp3", ".aac", ".m4a", ".flv", ".webm", ".wmv", ".3gp", ".m4v", ".mpg", ".mpeg"
    )
    private val hlsMimeHints = listOf(
        "application/vnd.apple.mpegurl",
        "application/x-mpegurl"
    )
    private val dashMimeHints = listOf("application/dash+xml")
    private val smoothStreamingMimeHints = listOf("application/vnd.ms-sstr+xml", "text/xml")
    private val hlsQueryHints = listOf("ext=m3u8", "output=m3u8", "format=m3u8", "type=m3u8")
    private val tsQueryHints = listOf("ext=ts", "output=ts", "format=ts", "type=ts")
    private val hlsLiveAliases = setOf("sd", "hd", "fhd", "uhd", "4k", "playlist", "master", "index")

    fun resolve(streamInfo: StreamInfo, mimeType: String? = null): ResolvedStreamType {
        val live = isLive(streamInfo)
        return when (streamInfo.streamType) {
            StreamType.HLS -> ResolvedStreamType.HLS
            StreamType.DASH -> ResolvedStreamType.DASH
            StreamType.SMOOTH_STREAMING -> ResolvedStreamType.SMOOTH_STREAMING
            StreamType.MPEG_TS -> if (live) ResolvedStreamType.MPEG_TS_LIVE else ResolvedStreamType.PROGRESSIVE
            StreamType.PROGRESSIVE -> ResolvedStreamType.PROGRESSIVE
            StreamType.RTSP -> ResolvedStreamType.RTSP
            else -> resolve(url = streamInfo.url, mimeType = mimeType, isLive = live)
        }
    }

    fun resolve(url: String, mimeType: String? = null, isLive: Boolean = false): ResolvedStreamType {
        val normalizedMimeType = mimeType?.trim()?.lowercase(Locale.ROOT)
        val uri = runCatching { URI(url) }.getOrNull()
        val scheme = uri?.scheme?.lowercase(Locale.ROOT)
        val path = uri?.path.orEmpty().ifBlank { url }
            .substringBefore('?')
            .substringBefore('#')
            .lowercase(Locale.ROOT)
        val query = uri?.rawQuery
            ?.lowercase(Locale.ROOT)
            ?: url.substringAfter('?', "")
                .substringBefore('#')
                .lowercase(Locale.ROOT)
        val lastSegment = path.substringAfterLast('/').trim()
        val isVodPath = path.contains("/movie/") || path.contains("/series/")
        return when {
            scheme in setOf("rtsp", "rtsps") -> ResolvedStreamType.RTSP
            scheme in setOf("file", "content") && path.endsWith(".m3u8") -> ResolvedStreamType.HLS
            scheme in setOf("file", "content") -> ResolvedStreamType.PROGRESSIVE
            normalizedMimeType != null && hlsMimeHints.any(normalizedMimeType::contains) -> ResolvedStreamType.HLS
            normalizedMimeType != null && dashMimeHints.any(normalizedMimeType::contains) -> ResolvedStreamType.DASH
            normalizedMimeType != null && smoothStreamingMimeHints.any(normalizedMimeType::contains) && path.contains(".ism") ->
                ResolvedStreamType.SMOOTH_STREAMING
            hlsQueryHints.any(query::contains) -> ResolvedStreamType.HLS
            tsQueryHints.any(query::contains) -> if (isLive && !isVodPath) ResolvedStreamType.MPEG_TS_LIVE else ResolvedStreamType.PROGRESSIVE
            path.contains(".m3u8") -> ResolvedStreamType.HLS
            path.contains(".mpd") -> ResolvedStreamType.DASH
            path.contains(".isml/manifest") || path.contains(".ism/manifest") || path.endsWith(".ism") || path.endsWith(".isml") ->
                ResolvedStreamType.SMOOTH_STREAMING
            path.endsWith(".ts") -> if (isLive && !isVodPath) ResolvedStreamType.MPEG_TS_LIVE else ResolvedStreamType.PROGRESSIVE
            isLive && path.contains("/live/") && lastSegment in hlsLiveAliases -> ResolvedStreamType.HLS
            isLive && path.contains("/live/") && progressiveExtensions.none(path::endsWith) ->
                ResolvedStreamType.MPEG_TS_LIVE
            progressiveExtensions.any(path::endsWith) || isVodPath -> ResolvedStreamType.PROGRESSIVE
            else -> ResolvedStreamType.UNKNOWN
        }
    }

    private fun isLive(streamInfo: StreamInfo): Boolean {
        if (streamInfo.url.contains("/movie/", ignoreCase = true) || streamInfo.url.contains("/series/", ignoreCase = true)) {
            return false
        }
        return streamInfo.url.contains("/live/", ignoreCase = true) ||
            streamInfo.catchUpUrl != null ||
            streamInfo.streamType == StreamType.MPEG_TS ||
            streamInfo.streamType == StreamType.RTSP
    }
}
