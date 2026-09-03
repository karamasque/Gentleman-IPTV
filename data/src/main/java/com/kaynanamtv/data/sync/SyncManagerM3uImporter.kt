package com.kaynanamtv.data.sync

import android.util.Log
import com.kaynanamtv.data.local.entity.ChannelEntity
import com.kaynanamtv.data.local.entity.MovieEntity
import com.kaynanamtv.data.local.entity.SeriesEntity
import com.kaynanamtv.data.parser.M3uParser
import com.kaynanamtv.data.remote.http.HttpRequestProfile
import com.kaynanamtv.data.remote.http.safeRequestIdentitySummary
import com.kaynanamtv.data.remote.http.toGenericRequestProfile
import com.kaynanamtv.data.remote.http.withRequestProfile
import com.kaynanamtv.data.util.AdultContentClassifier
import com.kaynanamtv.data.util.UrlSecurityPolicy
import com.kaynanamtv.domain.model.ContentType
import com.kaynanamtv.domain.model.Provider
import com.kaynanamtv.domain.sync.Section
import com.kaynanamtv.domain.sync.SyncProgress
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import java.io.BufferedInputStream
import java.io.IOException
import java.io.InputStream
import java.net.URI
import java.util.concurrent.TimeUnit
import java.util.zip.GZIPInputStream

private const val M3U_PROGRESS_INTERVAL = 5_000
private const val M3U_IMPORTER_TAG = "SyncManagerM3u"

internal class SyncManagerM3uImporter(
    private val context: android.content.Context,
    private val m3uParser: M3uParser,
    private val okHttpClient: OkHttpClient,
    private val syncCatalogStore: SyncCatalogStore,
    private val retryTransient: suspend (suspend () -> Unit) -> Unit,
    private val progress: (Long, ((String) -> Unit)?, String) -> Unit,
    private val syncProgressBus: SyncProgressBus
) {
    suspend fun importPlaylist(
        provider: Provider,
        onProgress: ((String) -> Unit)?,
        includeLive: Boolean = true,
        includeMovies: Boolean = true,
        batchSize: Int = 1000
    ): M3uImportStats {
        UrlSecurityPolicy.validatePlaylistSourceUrl(provider.m3uUrl.ifBlank { provider.serverUrl })?.let { message ->
            throw IllegalStateException(message)
        }
        progress(provider.id, onProgress, "Oynatma listesi indiriliyor (M3U)...")
        // D14 — emission M3U etape Downloading : Section.LIVE par convention (le M3U
        // peut contenir un melange Live/VOD, mais l'UI ne distingue pas). Mode
        // indetermine (total = 0) puisqu'on ne connait pas encore la taille du flux.
        syncProgressBus.emit(
            SyncProgress(
                section = Section.LIVE,
                current = 0,
                total = 0,
                currentLabel = "",
                itemsIndexed = 0
            )
        )
        syncCatalogStore.clearProviderStaging(provider.id)
        val sessionId = syncCatalogStore.newSessionId()
        val stableLongHasher = StableLongHasher()
        val liveCategories = CategoryAccumulator(provider.id, ContentType.LIVE, stableLongHasher)
        val movieCategories = CategoryAccumulator(provider.id, ContentType.MOVIE, stableLongHasher)
        val seriesCategories = CategoryAccumulator(provider.id, ContentType.SERIES, stableLongHasher)
        val channelBatch = ArrayList<ChannelEntity>(batchSize)
        val movieBatch = ArrayList<MovieEntity>(batchSize)
        val seriesBatch = ArrayList<SeriesEntity>(batchSize)
        val seenLiveStreamIds = if (includeLive) mutableSetOf<Long>() else null
        val seenMovieStreamIds = if (includeMovies) mutableSetOf<Long>() else null
        val seenSeriesStreamIds = if (includeMovies) mutableSetOf<Long>() else null
        var header = M3uParser.M3uHeader()
        var liveCount = 0
        var movieCount = 0
        var seriesCount = 0
        var parsedCount = 0
        var nextMilestone = M3U_PROGRESS_INTERVAL
        val warnings = mutableListOf<String>()
        var insecureStreamCount = 0

        try {
            openPlaylistStream(provider) { streamed ->
                progress(provider.id, onProgress, "Oynatma listesi ayrıştırılıyor...")
                syncProgressBus.emit(
                    SyncProgress(
                        section = Section.LIVE,
                        current = 0,
                        total = 0,
                        currentLabel = "",
                        itemsIndexed = 0
                    )
                )
                maybeDecompressPlaylist(streamed).use { input ->
                    m3uParser.parseStreaming(
                        inputStream = input,
                        playlistUrl = streamed.sourceName,
                        onHeader = { parsedHeader ->
                            val validEpgUrl = parsedHeader.tvgUrl?.takeIf { UrlSecurityPolicy.validateOptionalEpgUrl(it) == null }
                            if (parsedHeader.tvgUrl != null && validEpgUrl == null) {
                                warnings += "Playlist başlığındaki desteklenmeyen EPG URL'si yoksayıldı."
                            }
                            header = parsedHeader.copy(tvgUrl = validEpgUrl)
                        }
                    ) { entry ->
                        parsedCount++
                        if (parsedCount >= nextMilestone) {
                            progress(provider.id, onProgress, "Aktarılan oynatma listesi öğesi: $parsedCount...")
                            syncProgressBus.emit(
                                SyncProgress(
                                    section = Section.LIVE,
                                    current = parsedCount,
                                    total = 0,
                                    currentLabel = "",
                                    itemsIndexed = parsedCount
                                )
                            )
                            nextMilestone += M3U_PROGRESS_INTERVAL
                        }
                        if (!UrlSecurityPolicy.isAllowedStreamEntryUrl(entry.url)) {
                            insecureStreamCount++
                            return@parseStreaming
                        }

                        val safeLogoUrl = UrlSecurityPolicy.sanitizeImportedAssetUrl(entry.tvgLogo)
                        val safeCatchUpSource = UrlSecurityPolicy.sanitizeImportedAssetUrl(entry.catchUpSource)

                        val urlLower = entry.url.lowercase(java.util.Locale.ROOT)
                        val isSeries = urlLower.contains("/series/")
                        val isXtreamVod = urlLower.contains("/movie/") || urlLower.contains("/movies/") || isSeries
                        val vodClassificationActive = true

                        if (isSeries) {
                            if (!includeMovies) return@parseStreaming
                            val groupTitle = entry.groupTitle.ifBlank { "Kategorilendirilmemiş" }
                            val stableStreamId = stableId(
                                providerId = provider.id,
                                contentType = ContentType.SERIES,
                                tvgId = entry.tvgId,
                                url = entry.url,
                                title = entry.name,
                                groupTitle = groupTitle,
                                hasher = stableLongHasher
                            )
                            if (seenSeriesStreamIds?.add(stableStreamId) != true) return@parseStreaming
                            val categoryId = seriesCategories.idFor(groupTitle)
                            val isAdult = AdultContentClassifier.isAdultCategoryName(groupTitle)
                            seriesBatch.add(
                                SeriesEntity(
                                    seriesId = stableStreamId,
                                    name = entry.name,
                                    posterUrl = safeLogoUrl,
                                    categoryId = categoryId,
                                    categoryName = groupTitle,
                                    providerId = provider.id,
                                    genre = entry.genre,
                                    rating = entry.rating?.toFloatOrNull() ?: 0f,
                                    isAdult = isAdult
                                )
                            )
                            seriesCount++
                            if (seriesBatch.size >= batchSize) {
                                flushSeriesBatch(provider.id, sessionId, seriesBatch)
                            }
                        } else if (vodClassificationActive && M3uParser.isVodEntry(entry)) {
                            if (!includeMovies) return@parseStreaming
                            val groupTitle = entry.groupTitle.ifBlank { "Kategorilendirilmemiş" }
                            val stableStreamId = stableId(
                                providerId = provider.id,
                                contentType = ContentType.MOVIE,
                                tvgId = entry.tvgId,
                                url = entry.url,
                                title = entry.name,
                                groupTitle = groupTitle,
                                hasher = stableLongHasher
                            )
                            if (seenMovieStreamIds?.add(stableStreamId) != true) return@parseStreaming
                            val categoryId = movieCategories.idFor(groupTitle)
                            val isAdult = AdultContentClassifier.isAdultCategoryName(groupTitle)
                            movieBatch.add(
                                MovieEntity(
                                    streamId = stableStreamId,
                                    name = entry.name,
                                    posterUrl = safeLogoUrl,
                                    categoryId = categoryId,
                                    categoryName = groupTitle,
                                    streamUrl = entry.url,
                                    providerId = provider.id,
                                    rating = entry.rating?.toFloatOrNull() ?: 0f,
                                    year = entry.year,
                                    genre = entry.genre,
                                    isAdult = isAdult
                                )
                            )
                            movieCount++
                            if (movieBatch.size >= batchSize) {
                                flushMovieBatch(provider.id, sessionId, movieBatch)
                            }
                        } else {
                            if (!includeLive) return@parseStreaming
                            val groupTitle = entry.groupTitle.ifBlank { "Kategorilendirilmemiş" }
                            val stableStreamId = stableId(
                                providerId = provider.id,
                                contentType = ContentType.LIVE,
                                tvgId = entry.tvgId,
                                url = entry.url,
                                title = entry.name,
                                groupTitle = groupTitle,
                                hasher = stableLongHasher
                            )
                            if (seenLiveStreamIds?.add(stableStreamId) != true) return@parseStreaming
                            val categoryId = liveCategories.idFor(groupTitle)
                            val isAdult = AdultContentClassifier.isAdultCategoryName(groupTitle)
                            channelBatch.add(
                                ChannelEntity(
                                    streamId = stableStreamId,
                                    name = entry.name,
                                    logoUrl = safeLogoUrl,
                                    groupTitle = groupTitle,
                                    categoryId = categoryId,
                                    categoryName = groupTitle,
                                    epgChannelId = entry.tvgId ?: entry.tvgName,
                                    number = entry.tvgChno ?: 0,
                                    streamUrl = entry.url,
                                    catchUpSupported = !entry.catchUp.isNullOrBlank() ||
                                        !entry.catchUpSource.isNullOrBlank() ||
                                        !entry.timeshift.isNullOrBlank(),
                                    catchUpDays = entry.catchUpDays ?: 0,
                                    catchUpSource = safeCatchUpSource,
                                    providerId = provider.id,
                                    isAdult = isAdult
                                )
                            )
                            liveCount++
                            if (channelBatch.size >= batchSize) {
                                flushChannelBatch(provider.id, sessionId, channelBatch)
                            }
                        }
                    }
                }
            }

            flushChannelBatch(provider.id, sessionId, channelBatch)
            flushMovieBatch(provider.id, sessionId, movieBatch)
            flushSeriesBatch(provider.id, sessionId, seriesBatch)
            val effectiveLive = includeLive && liveCount > 0
            val effectiveMovies = includeMovies && movieCount > 0
            val effectiveSeries = includeMovies && seriesCount > 0
            syncCatalogStore.finalizeStagedImport(
                providerId = provider.id,
                sessionId = sessionId,
                liveCategories = if (effectiveLive) liveCategories.entities() else null,
                movieCategories = if (effectiveMovies) movieCategories.entities() else null,
                seriesCategories = if (effectiveSeries) seriesCategories.entities() else null,
                includeLive = effectiveLive,
                includeMovies = effectiveMovies,
                includeSeries = effectiveSeries
            )
        } finally {
            syncCatalogStore.discardStagedImport(provider.id, sessionId)
        }

        if (insecureStreamCount > 0) {
            warnings += "Güvensiz $insecureStreamCount oynatma listesi akış URL'si yoksayıldı."
        }

        return M3uImportStats(
            header = header,
            liveCount = liveCount,
            movieCount = movieCount,
            seriesCount = seriesCount,
            warnings = warnings
        )
    }

    private suspend fun openPlaylistStream(
        provider: Provider,
        block: suspend (StreamedPlaylist) -> Unit
    ) {
        val rawUrl = provider.m3uUrl.ifBlank { provider.serverUrl }
        val urlStr = rawUrl
        if (urlStr.startsWith("file:")) {
            java.io.File(java.net.URI(urlStr)).inputStream().use { input ->
                block(StreamedPlaylist(inputStream = input, sourceName = urlStr))
            }
            return
        }

        val requestProfile = provider.toGenericRequestProfile(ownerTag = "provider:${provider.id}/m3u")
        val m3uClient = okHttpClient.newBuilder()
            .readTimeout(300, TimeUnit.SECONDS)
            .callTimeout(360, TimeUnit.SECONDS)
            .build()
        val cacheFile = java.io.File(context.cacheDir, "m3u_cached_${provider.id}.m3u")
        val etagFile = java.io.File(context.cacheDir, "m3u_cached_${provider.id}.etag")
        val cachedEtag = if (cacheFile.exists() && cacheFile.length() > 0L && etagFile.exists()) {
            etagFile.readText().trim().takeIf { it.isNotBlank() }
        } else null

        val tempFile = java.io.File.createTempFile("m3u_import_${provider.id}_", ".tmp", context.cacheDir)
        var contentEncoding: String? = null
        var usedCache = false
        try {
            retryTransient {
                val reqBuilder = Request.Builder().url(urlStr)
                if (!cachedEtag.isNullOrBlank()) {
                    reqBuilder.header("If-None-Match", cachedEtag)
                }
                val request = reqBuilder.build().withRequestProfile(requestProfile)
                m3uClient.newCall(request).execute().use { response ->
                    if (response.code == 304 && cacheFile.exists() && cacheFile.length() > 0L) {
                        usedCache = true
                        return@retryTransient
                    }
                    ensureSuccessfulPlaylistResponse(response, requestProfile)
                    val body = response.body ?: throw IllegalStateException("Empty M3U response")
                    contentEncoding = response.header("Content-Encoding")
                    val etag = response.header("ETag")
                    tempFile.outputStream().use { output ->
                        body.byteStream().copyTo(output)
                    }
                    if (!etag.isNullOrBlank()) {
                        runCatching { etagFile.writeText(etag) }
                    }
                }
            }

            val targetFile = if (usedCache) {
                cacheFile
            } else {
                if (tempFile.exists() && tempFile.length() > 0L) {
                    tempFile.copyTo(cacheFile, overwrite = true)
                }
                tempFile
            }

            targetFile.inputStream().use { input ->
                block(
                    StreamedPlaylist(
                        inputStream = input,
                        contentEncoding = contentEncoding,
                        sourceName = urlStr
                    )
                )
            }
        } finally {
            if (tempFile.exists()) {
                tempFile.delete()
            }
        }
    }

    private fun ensureSuccessfulPlaylistResponse(response: Response, requestProfile: HttpRequestProfile) {
        if (response.isSuccessful) return
        Log.w(
            M3U_IMPORTER_TAG,
            "Playlist request failed (${response.request.safeRequestIdentitySummary(requestProfile)}): HTTP ${response.code}"
        )
        if (response.code in 500..599 || response.code == 429) {
            // Transient — the retry wrapper will attempt again automatically.
            throw IOException("Transient HTTP ${response.code}")
        }
        // Non-transient failures: produce an actionable message so the user understands
        // exactly why this source was skipped (especially relevant for CombinedM3U profiles).
        val reason = when (response.code) {
            401 -> "abonelik bilgileri reddedildi (HTTP 401 Yetkisiz) — kullanıcı adı ve şifrenizi kontrol edin"
            403 -> "sağlayıcı tarafından erişim reddedildi (HTTP 403 Yasaklandı) — aboneliğiniz sona ermiş veya IP adresiniz engellenmiş olabilir"
            404 -> "oynatma listesi URL'si sunucuda bulunamadı (HTTP 404 Bulunamadı) — sağlayıcı URL'si değişmiş olabilir"
            407 -> "bir proxy kimlik doğrulama hatası oluştu (HTTP 407) — ağ ayarlarınızı kontrol edin"
            else -> "sunucu beklenmedik bir hata döndürdü (HTTP ${response.code})"
        }
        throw IllegalStateException("M3U oynatma listesi indirilemedi: $reason")
    }

    private fun maybeDecompressPlaylist(streamed: StreamedPlaylist): InputStream {
        val buffered = if (streamed.inputStream is BufferedInputStream) {
            streamed.inputStream
        } else {
            BufferedInputStream(streamed.inputStream, 64 * 1024)
        }
        buffered.mark(2)
        val first = buffered.read()
        val second = buffered.read()
        buffered.reset()
        val gzipMagic = first == 0x1f && second == 0x8b
        val encodedGzip = streamed.contentEncoding?.contains("gzip", ignoreCase = true) == true
        val namedGzip = streamed.sourceName?.lowercase()?.endsWith(".gz") == true
        return if (gzipMagic || encodedGzip || namedGzip) {
            GZIPInputStream(buffered, 64 * 1024)
        } else {
            buffered
        }
    }

    private suspend fun flushChannelBatch(providerId: Long, sessionId: Long, batch: MutableList<ChannelEntity>) {
        if (batch.isEmpty()) return
        syncCatalogStore.stageChannelBatch(providerId, sessionId, batch)
        batch.clear()
    }

    private suspend fun flushMovieBatch(providerId: Long, sessionId: Long, batch: MutableList<MovieEntity>) {
        if (batch.isEmpty()) return
        syncCatalogStore.stageMovieBatch(providerId, sessionId, batch)
        batch.clear()
    }

    private suspend fun flushSeriesBatch(providerId: Long, sessionId: Long, batch: MutableList<SeriesEntity>) {
        if (batch.isEmpty()) return
        syncCatalogStore.stageSeriesBatch(providerId, sessionId, batch)
        batch.clear()
    }

    private fun stableId(
        providerId: Long,
        contentType: ContentType,
        tvgId: String?,
        url: String,
        title: String,
        groupTitle: String?,
        hasher: StableLongHasher
    ): Long {
        val normalizedUrl = normalizeUrlForIdentity(url)
        val normalizedTvgId = tvgId?.trim()?.lowercase().orEmpty()
        val normalizedTitle = normalizeTextForIdentity(title)
        val normalizedGroup = normalizeTextForIdentity(groupTitle)
        val identity = if (normalizedTvgId.isNotBlank()) {
            "$providerId|${contentType.name}|tvg=$normalizedTvgId|url=$normalizedUrl"
        } else {
            "$providerId|${contentType.name}|url=$normalizedUrl|title=$normalizedTitle|group=$normalizedGroup"
        }
        return hasher.hash(identity)
    }

    private fun normalizeUrlForIdentity(url: String): String {
        val trimmed = url.trim().lowercase(java.util.Locale.ROOT)
        val parsed = runCatching { URI(trimmed.replace(" ", "%20")) }.getOrNull()
        if (parsed == null || parsed.host.isNullOrBlank()) {
            return trimmed
        }
        val scheme = parsed.scheme.orEmpty().lowercase(java.util.Locale.ROOT)
        val host = parsed.host.orEmpty().lowercase(java.util.Locale.ROOT)
        val path = parsed.path.orEmpty().trimEnd('/')
        val query = parsed.query
            ?.split('&')
            ?.mapNotNull { pair ->
                val key = pair.substringBefore('=').lowercase(java.util.Locale.ROOT)
                val value = pair.substringAfter('=', "")
                when (key) {
                    "token", "auth", "password", "username" -> null
                    else -> "$key=$value"
                }
            }
            ?.sorted()
            ?.joinToString("&")
            .orEmpty()
        return "$scheme|$host|$path|$query"
    }

    private fun normalizeTextForIdentity(value: String?): String {
        return value.orEmpty().lowercase().replace(Regex("\\s+"), " ").trim()
    }

    fun upgradeXtreamM3uUrl(url: String): String = url

    fun deriveXtreamEpgUrl(m3uUrl: String): String? {
        if (!m3uUrl.contains("/get.php", ignoreCase = true)) return null
        val uri = runCatching { java.net.URI(m3uUrl) }.getOrNull() ?: return null
        val queryMap = uri.rawQuery?.split('&')?.mapNotNull {
            val parts = it.split('=')
            if (parts.size == 2) parts[0].lowercase() to parts[1] else null
        }?.toMap().orEmpty()
        val username = queryMap["username"] ?: return null
        val password = queryMap["password"] ?: return null
        val scheme = uri.scheme ?: "http"
        val host = uri.host ?: return null
        val portStr = if (uri.port != -1) ":${uri.port}" else ""
        return "$scheme://$host$portStr/xmltv.php?username=$username&password=$password"
    }
}
