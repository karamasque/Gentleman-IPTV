package com.kaynanamtv.player.playback

import com.kaynanamtv.domain.model.StreamInfo
import com.kaynanamtv.domain.model.StreamType
import java.net.URI
import java.util.Locale

internal fun buildLiveTsFallbackStreamInfo(streamInfo: StreamInfo): StreamInfo? {
    val fallbackUrl = buildLiveTsFallbackUrl(streamInfo.url) ?: return null
    return streamInfo.copy(
        url = fallbackUrl,
        streamType = StreamType.MPEG_TS,
        containerExtension = "ts"
    )
}

internal fun buildLiveHlsFallbackStreamInfo(streamInfo: StreamInfo): StreamInfo? {
    val fallbackUrl = buildLiveHlsFallbackUrl(streamInfo.url) ?: return null
    return streamInfo.copy(
        url = fallbackUrl,
        streamType = StreamType.HLS,
        containerExtension = "m3u8"
    )
}

internal fun shouldFallbackMalformedHlsToLiveTs(
    category: PlaybackErrorCategory,
    resolvedStreamType: ResolvedStreamType,
    playbackStarted: Boolean
): Boolean = false

internal fun shouldFallbackStalledHlsToLiveTs(
    resolvedStreamType: ResolvedStreamType,
    recoveryAttempt: Int
): Boolean = false

internal fun buildLiveTsFallbackUrl(url: String): String? {
    val uri = runCatching { URI(url) }.getOrNull() ?: return null
    val path = uri.path.orEmpty()
    if (!path.contains("/live/", ignoreCase = true)) return null

    val query = uri.rawQuery.orEmpty()
    val queryFallback = if (query.contains("ext=m3u8", ignoreCase = true)) {
        val updated = Regex("""(?i)(^|&)ext=m3u8(?=&|$)""").replace(query) { match ->
            "${match.groupValues[1]}ext=ts"
        }
        rebuildUri(uri, path = path, query = updated)
    } else {
        null
    }
    if (queryFallback != null) return queryFallback

    if (!path.lowercase(Locale.ROOT).endsWith(".m3u8")) return null
    return rebuildUri(
        uri = uri,
        path = path.dropLast(".m3u8".length) + ".ts",
        query = uri.rawQuery
    )
}

internal fun buildLiveHlsFallbackUrl(url: String): String? {
    val uri = runCatching { URI(url) }.getOrNull() ?: return null
    val path = uri.path.orEmpty()
    if (!path.contains("/live/", ignoreCase = true)) return null

    val query = uri.rawQuery.orEmpty()
    val queryFallback = if (query.contains("ext=ts", ignoreCase = true)) {
        val updated = Regex("""(?i)(^|&)ext=ts(?=&|$)""").replace(query) { match ->
            "${match.groupValues[1]}ext=m3u8"
        }
        rebuildUri(uri, path = path, query = updated)
    } else {
        null
    }
    if (queryFallback != null) return queryFallback

    if (!path.lowercase(Locale.ROOT).endsWith(".ts")) return null
    return rebuildUri(
        uri = uri,
        path = path.dropLast(".ts".length) + ".m3u8",
        query = uri.rawQuery
    )
}

private fun rebuildUri(uri: URI, path: String, query: String?): String {
    return URI(
        uri.scheme,
        uri.rawAuthority,
        path,
        query,
        uri.rawFragment
    ).toASCIIString()
}
