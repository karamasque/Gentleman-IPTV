package com.kaynanamtv.player.playback

import android.content.Context
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultDataSource
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.okhttp.OkHttpDataSource
import com.kaynanamtv.domain.model.VodHttpProtocolMode
import com.kaynanamtv.domain.model.StreamInfo
import java.io.OutputStream
import java.net.InetSocketAddress
import java.net.Proxy
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol

internal fun shouldUsePlatformHttpDataSource(resolvedStreamType: ResolvedStreamType): Boolean =
    false

@UnstableApi
class PlayerDataSourceFactoryProvider(
    private val context: Context,
    private val baseClient: OkHttpClient
) {
    private companion object {
        private const val TAG = "PlayerDataSource"
    }

    private data class ClientKey(
        val profile: PlayerTimeoutProfile,
        val forceHttp1: Boolean,
        val port: Int,
        val allowInvalidSsl: Boolean,
        val proxyHost: String,
        val proxyPort: Int?
    )

    private val addressHealthStore = PlayerAddressHealthStore()
    private val clientsByKey = ConcurrentHashMap<ClientKey, OkHttpClient>()

    fun createFactory(
        streamInfo: StreamInfo,
        resolvedStreamType: ResolvedStreamType,
        vodHttpProtocolMode: VodHttpProtocolMode = VodHttpProtocolMode.COMPATIBILITY_HTTP1,
        preload: Boolean = false,
        teeOutputStream: AtomicReference<OutputStream?> = AtomicReference(null)
    ): Pair<PlayerTimeoutProfile, DataSource.Factory> {
        val profile = PlayerTimeoutProfile.resolve(streamInfo, resolvedStreamType, preload)
        val headers = effectivePlaybackRequestProperties(
            headers = streamInfo.headers,
            userAgent = streamInfo.userAgent
        )
        logRequestShape(streamInfo, headers, preload)
        val forceHttp1 = PlayerHttpProtocolPolicy.forceHttp1(
            resolvedStreamType = resolvedStreamType,
            vodHttpProtocolMode = vodHttpProtocolMode
        )
        val port = streamPort(streamInfo.url)
        val clientKey = ClientKey(
            profile = profile,
            forceHttp1 = forceHttp1,
            port = port,
            allowInvalidSsl = streamInfo.allowInvalidSsl,
            proxyHost = streamInfo.proxyHost.trim(),
            proxyPort = streamInfo.proxyPort
        )
        val client = clientsByKey.computeIfAbsent(clientKey) {
            val builder = if (streamInfo.allowInvalidSsl) {
                baseClient.newBuilder().applyUnsafeTlsBypass()
            } else {
                baseClient.newBuilder()
            }
            builder
                .connectionPool(baseClient.connectionPool)
                .dispatcher(baseClient.dispatcher)
                .followRedirects(true)
                .followSslRedirects(true)
                .addInterceptor(CrossProtocolRedirectHeaderInterceptor(headers))
                .addInterceptor(StalkerPlaybackRequestLoggingInterceptor)
                .connectTimeout(profile.connectTimeoutMs, TimeUnit.MILLISECONDS)
                .readTimeout(profile.readTimeoutMs, TimeUnit.MILLISECONDS)
                .writeTimeout(profile.writeTimeoutMs, TimeUnit.MILLISECONDS)
                .dns(okhttp3.Dns.SYSTEM)
                .apply {
                    if (forceHttp1) {
                        protocols(listOf(Protocol.HTTP_1_1))
                    }
                    streamInfo.httpProxy()?.let { proxy(it) }
                }
                .build()
        }
        Log.i(
            TAG,
            "data-source streamType=$resolvedStreamType timeout=$profile httpProtocol=${if (forceHttp1) "HTTP_1_1" else "DEFAULT"} " +
                "headers=[${maskHeadersForLog(headers)}] target=${PlaybackLogSanitizer.sanitizeUrl(streamInfo.url)}"
        )
        val upstreamFactory = OkHttpDataSource.Factory(client).apply {
            if (headers.isNotEmpty()) {
                setDefaultRequestProperties(headers)
            }
        }
        val defaultFactory = DefaultDataSource.Factory(context, upstreamFactory)
        val statsWrapped = if (shouldWrapDataSourceReadStats(resolvedStreamType)) {
            PlayerDataSourceReadStatsFactory(
                upstream = defaultFactory,
                resolvedStreamType = resolvedStreamType,
                initialTargetUrl = streamInfo.url
            )
        } else {
            defaultFactory
        }
        // Tee: kayit aktifse byte'lar ayni anda dosyaya da yazilir
        val factory: DataSource.Factory = if (teeOutputStream.get() != null) {
            PlayerTeeDataSourceFactory(upstream = statsWrapped, sinkRef = teeOutputStream)
        } else {
            statsWrapped
        }
        return profile to factory
    }

    private fun logRequestShape(
        streamInfo: StreamInfo,
        headers: Map<String, String>,
        preload: Boolean
    ) {
        val hasStalkerHeaders = headers.containsKey("X-User-Agent") ||
            headers.containsKey("Authorization") ||
            headers["Cookie"]?.contains("mac=", ignoreCase = true) == true
        if (!hasStalkerHeaders) {
            return
        }
        val uri = runCatching { URI(streamInfo.url) }.getOrNull()
        Log.d(
            TAG,
            "Playback request headers preload=$preload host=${uri?.host.orEmpty()} path=${uri?.path.orEmpty()} " +
                "ua=${!streamInfo.userAgent.isNullOrBlank()} referer=${headers.containsKey("Referer")} " +
                "cookie=${headers.containsKey("Cookie")} auth=${headers.containsKey("Authorization")} " +
                "xua=${headers.containsKey("X-User-Agent")}"
        )
    }

    private fun streamPort(url: String): Int {
        val uri = runCatching { URI(url) }.getOrNull()
        uri?.port?.takeIf { it > 0 }?.let { return it }
        return when (uri?.scheme?.lowercase()) {
            "http" -> 80
            "https" -> 443
            else -> -1
        }
    }

    private fun StreamInfo.httpProxy(): Proxy? {
        val host = proxyHost.trim().takeIf { it.isNotBlank() } ?: return null
        val port = proxyPort ?: return null
        return Proxy(Proxy.Type.HTTP, InetSocketAddress(host, port))
    }
}

private const val DEFAULT_STREAM_USER_AGENT = "IPTVSmarters/3.1.2 (Linux; Android 12)"

internal fun effectivePlaybackRequestProperties(
    headers: Map<String, String>,
    userAgent: String?
): Map<String, String> {
    val existingUa = headers.entries.firstOrNull { it.key.equals("User-Agent", ignoreCase = true) }?.value
    val targetUserAgent = userAgent?.trim()?.takeIf { it.isNotBlank() }
        ?: existingUa?.takeIf { it.isNotBlank() }
        ?: DEFAULT_STREAM_USER_AGENT

    return buildMap(headers.size + 1) {
        headers.forEach { (name, value) ->
            if (!name.equals("User-Agent", ignoreCase = true)) {
                put(name, value)
            }
        }
        put("User-Agent", targetUserAgent)
    }
}

private object StalkerPlaybackRequestLoggingInterceptor : Interceptor {
    private const val TAG = "PlayerDataSource"

    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        val request = chain.request()
        if (!request.hasStalkerPlaybackShape()) {
            return chain.proceed(request)
        }
        Log.d(
            TAG,
            "Playback request actual method=${request.method} target=${PlaybackLogSanitizer.sanitizeUrl(request.url.toString())} " +
                "ua=${request.header("User-Agent") != null} referer=${request.header("Referer") != null} " +
                "cookie=${request.header("Cookie") != null} auth=${request.header("Authorization") != null} " +
                "xua=${request.header("X-User-Agent") != null} range=${request.header("Range") != null} " +
                "acceptEncoding=${request.header("Accept-Encoding")?.take(24).orEmpty()} cookieKeys=${request.cookieKeySummary()}"
        )
        val response = chain.proceed(request)
        Log.d(
            TAG,
            "Playback response actual target=${PlaybackLogSanitizer.sanitizeUrl(request.url.toString())} " +
                "code=${response.code} length=${response.header("Content-Length").orEmpty()} " +
                "type=${response.header("Content-Type").orEmpty()}"
        )
        return response
    }

    private fun okhttp3.Request.hasStalkerPlaybackShape(): Boolean {
        val path = url.encodedPath.lowercase()
        return header("X-User-Agent") != null ||
            header("Authorization") != null ||
            header("Cookie")?.contains("mac=", ignoreCase = true) == true ||
            path.endsWith("/play/live.php") ||
            path.endsWith("/play/movie.php")
    }

    private fun okhttp3.Request.cookieKeySummary(): String {
        val cookie = header("Cookie") ?: return ""
        return cookie.split(';')
            .mapNotNull { part -> part.substringBefore('=', missingDelimiterValue = "").trim().takeIf(String::isNotBlank) }
            .take(12)
            .joinToString("|")
    }
}

internal class CrossProtocolRedirectHeaderInterceptor(
    private val defaultHeaders: Map<String, String>
) : Interceptor {
    private companion object {
        private const val TAG = "PlayerDataSource"
        private const val MAX_REDIRECTS = 5
    }

    override fun intercept(chain: Interceptor.Chain): okhttp3.Response {
        var request = chain.request()
        var response = chain.proceed(request)
        var redirectCount = 0

        while (response.isRedirect && redirectCount < MAX_REDIRECTS) {
            val location = response.header("Location") ?: break
            val redirectUrl = request.url.resolve(location) ?: break

            Log.i(
                TAG,
                "playback-redirect from=${PlaybackLogSanitizer.sanitizeUrl(request.url.toString())} " +
                    "to=${PlaybackLogSanitizer.sanitizeUrl(redirectUrl.toString())} code=${response.code} " +
                    "headers=[${maskHeadersForLog(defaultHeaders)}]"
            )

            response.close()
            redirectCount++

            val requestBuilder = request.newBuilder().url(redirectUrl)
            defaultHeaders.forEach { (name, value) ->
                if (request.header(name) == null) {
                    requestBuilder.header(name, value)
                }
            }

            request = requestBuilder.build()
            response = chain.proceed(request)
        }
        return response
    }
}

internal fun maskHeadersForLog(headers: Map<String, String>): String {
    return headers.entries.joinToString(", ") { (k, v) ->
        val masked = when {
            k.equals("Authorization", ignoreCase = true) -> "<auth-redacted>"
            k.equals("Cookie", ignoreCase = true) -> "<cookie-redacted>"
            k.contains("token", ignoreCase = true) || k.contains("password", ignoreCase = true) || k.contains("secret", ignoreCase = true) -> "<redacted>"
            else -> v.take(30)
        }
        "$k=$masked"
    }
}
