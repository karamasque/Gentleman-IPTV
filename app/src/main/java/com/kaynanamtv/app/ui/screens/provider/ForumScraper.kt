package com.kaynanamtv.app.ui.screens.provider

import android.net.Uri
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.supervisorScope
import kotlinx.coroutines.withContext
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.security.SecureRandom
import java.security.cert.X509Certificate
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager

data class ForumIPTVAccount(
    val host: String,
    val username: String,
    val password: String,
    var status: String = "Bilinmiyor",
    var expiry: String = "Bilinmiyor"
)

class MemoryCookieJar : CookieJar {
    private val cache = HashMap<String, MutableList<Cookie>>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val host = url.host
        val list = cache.getOrPut(host) { ArrayList() }
        cookies.forEach { cookie ->
            list.removeAll { it.name == cookie.name }
            list.add(cookie)
        }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val host = url.host
        val result = ArrayList<Cookie>()
        cache[host]?.let { result.addAll(it) }
        return result
    }
}

object ForumScraper {

    private fun buildSslSetup(): Pair<SSLContext?, TrustManager> {
        val trustAllCerts = arrayOf<TrustManager>(
            object : X509TrustManager {
                override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
                override fun getAcceptedIssuers(): Array<X509Certificate> = arrayOf()
            }
        )
        val sslCtx = runCatching {
            SSLContext.getInstance("TLS").apply { init(null, trustAllCerts, SecureRandom()) }
        }.getOrNull() ?: runCatching {
            SSLContext.getInstance("SSL").apply { init(null, trustAllCerts, SecureRandom()) }
        }.getOrNull()
        return Pair(sslCtx, trustAllCerts[0])
    }

    /** Giriş ve sayfa taraması için kullanılan istemci — cookie destekli, normal timeout. */
    private fun getUnsafeOkHttpClient(cookieJar: CookieJar): OkHttpClient {
        val (sslCtx, tm) = buildSslSetup()
        val builder = OkHttpClient.Builder()
            .connectTimeout(12, TimeUnit.SECONDS)
            .readTimeout(15, TimeUnit.SECONDS)
            .writeTimeout(15, TimeUnit.SECONDS)
            .followRedirects(true)
            .followSslRedirects(true)
            .addInterceptor { chain ->
                chain.proceed(
                    chain.request().newBuilder()
                        .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36")
                        .header("Accept-Language", "tr-TR,tr;q=0.9,en-US;q=0.8")
                        .build()
                )
            }
            .cookieJar(cookieJar)
        if (sslCtx != null) {
            builder.sslSocketFactory(sslCtx.socketFactory, tm as X509TrustManager)
            builder.hostnameVerifier { _, _ -> true }
        }
        return builder.build()
    }

    /**
     * Hesap doğrulama için ayrı istemci:
     * — cookie gerekmez
     * — host başına bağlantı limiti yükseltildi (varsayılan 5 → 20)
     *   Böylece 100 paralel isteğin büyük çoğunluğu timeout almadan yanıt alır.
     * — kısa timeout: sadece /player_api.php yanıtı bekliyoruz
     */
    private fun getValidationOkHttpClient(): OkHttpClient {
        val (sslCtx, tm) = buildSslSetup()
        val dispatcher = okhttp3.Dispatcher().apply {
            maxRequests = 200
            maxRequestsPerHost = 20
        }
        val builder = OkHttpClient.Builder()
            .dispatcher(dispatcher)
            .connectTimeout(8, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(5, TimeUnit.SECONDS)
            .followRedirects(false)
            .followSslRedirects(false)
        if (sslCtx != null) {
            builder.sslSocketFactory(sslCtx.socketFactory, tm as X509TrustManager)
            builder.hostnameVerifier { _, _ -> true }
        }
        return builder.build()
    }

    private fun unescapeHtml(text: String): String {
        return text.replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
    }

    private fun safeUrlDecode(value: String): String {
        return try {
            java.net.URLDecoder.decode(value, "UTF-8")
        } catch (_: Throwable) {
            value
        }
    }

    private fun normalizeHost(rawHost: String): String {
        var trimmed = rawHost.trim().removeSuffix("/")
        if (!trimmed.startsWith("http://", ignoreCase = true) && !trimmed.startsWith("https://", ignoreCase = true)) {
            trimmed = "http://$trimmed"
        }
        return trimmed
    }

    fun parseXtreamUrl(url: String): ForumIPTVAccount? {
        return try {
            val unescaped = unescapeHtml(url.trim())
            if (unescaped.length < 10) return null
            val urlLower = unescaped.lowercase(Locale.ROOT)

            // 1. Live/Movie/Series/Play path format (e.g., http://host:port/live/user/pass/123.ts)
            for (kw in listOf("/live/", "/movie/", "/series/", "/play/")) {
                if (urlLower.contains(kw)) {
                    val idx = urlLower.indexOf(kw)
                    if (idx <= 0) continue
                    val rawHost = unescaped.substring(0, idx).trim().removeSuffix("/")
                    val host = normalizeHost(rawHost)
                    val rest = unescaped.substring(idx + kw.length)
                    val parts = rest.split("/").filter { it.isNotEmpty() }
                    if (parts.size >= 2) {
                        val username = parts[0].trim()
                        var password = parts[1].trim()
                        if (password.contains("?")) password = password.substringBefore("?")
                        if (password.contains(".")) password = password.substringBefore(".")
                        if (username.isNotEmpty() && password.isNotEmpty()) {
                            return ForumIPTVAccount(
                                host = host,
                                username = safeUrlDecode(username),
                                password = safeUrlDecode(password)
                            )
                        }
                    }
                }
            }

            // 2. Query parameters format (get.php, player_api.php, xmltv.php)
            for (kw in listOf("/get.php", "/player_api.php", "/xmltv.php")) {
                if (urlLower.contains(kw)) {
                    val idx = urlLower.indexOf(kw)
                    if (idx <= 0) continue
                    val rawHost = unescaped.substring(0, idx).trim().removeSuffix("/")
                    val host = normalizeHost(rawHost)
                    val userMatch = Regex("""[?&](?:username|user|auth)=([^&#\s]+)""", RegexOption.IGNORE_CASE).find(unescaped)
                    val passMatch = Regex("""[?&](?:password|pass)=([^&#\s]+)""", RegexOption.IGNORE_CASE).find(unescaped)
                    if (userMatch != null && passMatch != null) {
                        val u = safeUrlDecode(userMatch.groupValues[1].trim())
                        val p = safeUrlDecode(passMatch.groupValues[1].trim())
                        if (u.isNotEmpty() && p.isNotEmpty()) {
                            return ForumIPTVAccount(
                                host = host,
                                username = u,
                                password = p
                            )
                        }
                    }
                }
            }

            // 3. Plain text line format: http://host:port user pass
            val lineMatch = Regex("""(https?://[^\s/:,]+:\d+)[/\s,;|]+([^\s:,;|]+)[\s:,;|]+([^\s:,;|]+)""").find(unescaped)
            if (lineMatch != null) {
                val host = normalizeHost(lineMatch.groupValues[1])
                val u = safeUrlDecode(lineMatch.groupValues[2].trim())
                val p = safeUrlDecode(lineMatch.groupValues[3].trim())
                if (u.isNotEmpty() && p.isNotEmpty()) {
                    return ForumIPTVAccount(
                        host = host,
                        username = u,
                        password = p
                    )
                }
            }

            null
        } catch (_: Throwable) {
            null
        }
    }

    fun parseText(text: String): List<ForumIPTVAccount> {
        val list = ArrayList<ForumIPTVAccount>()
        val seen = HashSet<String>()
        val textUnescaped = unescapeHtml(text)

        // 1. Scan URLs
        val urlPattern = runCatching { Pattern.compile("https?://[^\\s\"'<>]+") }.getOrNull()
        if (urlPattern != null) {
            val matcher = urlPattern.matcher(textUnescaped)
            while (matcher.find()) {
                val url = matcher.group(0) ?: continue
                val parsed = parseXtreamUrl(url)
                if (parsed != null && parsed.host.isNotBlank() && parsed.username.isNotBlank() && parsed.password.isNotBlank()) {
                    val key = "${parsed.host}_${parsed.username}_${parsed.password}"
                    if (seen.add(key)) {
                        list.add(parsed)
                    }
                }
            }
        }

        // 2. Scan text lines
        val lines = textUnescaped.split('\n', '\r')
        for (line in lines) {
            val trimmed = line.trim()
            if (trimmed.length < 10) continue
            val parsed = parseXtreamUrl(trimmed)
            if (parsed != null && parsed.host.isNotBlank() && parsed.username.isNotBlank() && parsed.password.isNotBlank()) {
                val key = "${parsed.host}_${parsed.username}_${parsed.password}"
                if (seen.add(key)) {
                    list.add(parsed)
                }
            }
        }
        return list
    }

    private const val MASK_KEY = 0x5A
    // "mR.Gentleman" XOR 0x5A
    private val OBF_USER = byteArrayOf(0x37, 0x08, 0x74, 0x1D, 0x3F, 0x34, 0x2E, 0x36, 0x3F, 0x37, 0x3B, 0x34)
    // "Th3nexus92" XOR 0x5A
    private val OBF_PASS = byteArrayOf(0x1E, 0x32, 0x69, 0x34, 0x3F, 0x22, 0x2F, 0x29, 0x63, 0x68)

    fun getInternalUser(): String =
        String(OBF_USER.map { (it.toInt() xor MASK_KEY).toByte() }.toByteArray(), Charsets.UTF_8)

    fun getInternalPass(): String =
        String(OBF_PASS.map { (it.toInt() xor MASK_KEY).toByte() }.toByteArray(), Charsets.UTF_8)

    private val TARGET_THREADS = listOf(
        "https://forumsitesi.com.tr/konular/byxm-herkese-acik-iptv-m3u-panel-linkleri-2026.1285748/",
        "https://forumsitesi.com.tr/konular/atmaca-iptv-linkleri.1208873/",
        "https://forumsitesi.com.tr/konular/byxm-premium-uyelere-ozel-iptv-m3u-panel-linkleri-2026.1285769/",
        "https://forumsitesi.com.tr/konular/atmaca-premium-linkleri.1286421/",
        "https://forumsitesi.com.tr/konular/abidin-002.1284212/"
    )

    suspend fun scrapeAndValidate(
        user: String = "",
        pass: String = "",
        depthVal: String = "Son 1 Sayfa",
        onlyActive: Boolean = true,
        progressCallback: (Float, String) -> Unit
    ): Map<String, Any> = withContext(Dispatchers.IO) {
        val result = HashMap<String, Any>()
        val effectiveUser = user.takeIf { it.isNotBlank() } ?: getInternalUser()
        val effectivePass = pass.takeIf { it.isNotBlank() } ?: getInternalPass()

        try {
            progressCallback(0.05f, "🌐 Forum bağlantısı kuruluyor...")
            val cookieJar = MemoryCookieJar()
            val client = getUnsafeOkHttpClient(cookieJar)

            // Step 1: Get CSRF token from login page
            val rLogin = Request.Builder().url("https://forumsitesi.com.tr/login/").build()
            var token = ""
            runCatching {
                client.newCall(rLogin).execute().use { response ->
                    if (response.isSuccessful || response.code in 200..399) {
                        val body = response.body?.string().orEmpty()
                        val regex1 = Regex("""name="_xfToken"\s*value="([^"]+)"""")
                        val regex2 = Regex("""data-csrf="([^"]+)"""")
                        val match = regex1.find(body) ?: regex2.find(body)
                        if (match != null && match.groupValues.size > 1) {
                            token = match.groupValues[1]
                        }
                    }
                }
            }

            if (token.isBlank()) {
                result["success"] = false
                result["message"] = "Kaynak taraması başarısız oldu, forum erişilemez veya korumada olabilir. Lütfen daha sonra tekrar deneyin."
                result["accounts"] = emptyList<ForumIPTVAccount>()
                return@withContext result
            }

            // Step 2: XHR/AJAX login
            progressCallback(0.15f, "🔑 Otomatik güvenli giriş yapılıyor...")
            val formBody = FormBody.Builder()
                .add("login", effectiveUser.trim())
                .add("password", effectivePass.trim())
                .add("_xfToken", token)
                .add("remember", "1")
                .add("_xfRedirect", "https://forumsitesi.com.tr/")
                .add("_xfResponseType", "json")
                .build()

            val rPost = Request.Builder()
                .url("https://forumsitesi.com.tr/login/login")
                .header("X-Requested-With", "XMLHttpRequest")
                .header("Accept", "application/json, text/javascript, */*; q=0.01")
                .header("Referer", "https://forumsitesi.com.tr/login/")
                .header("Origin", "https://forumsitesi.com.tr")
                .post(formBody)
                .build()

            var loginSuccess = false
            runCatching {
                client.newCall(rPost).execute().use { response ->
                    val body = response.body?.string().orEmpty()
                    val hasUserCookie = cookieJar.loadForRequest(
                        "https://forumsitesi.com.tr".toHttpUrlOrNull() ?: return@use
                    ).any { it.name == "xf_user" }

                    if (hasUserCookie) {
                        loginSuccess = true
                    } else {
                        val json = runCatching { JSONObject(body) }.getOrNull()
                        if (json != null) {
                            val status = json.optString("status", "")
                            if (status == "ok") {
                                loginSuccess = true
                            } else if (status == "error") {
                                val errors = json.optJSONArray("errors")
                                val errorMsg = if (errors != null && errors.length() > 0) errors.optString(0, "Giriş başarısız") else "Bilinmeyen hata"
                                result["success"] = false
                                result["message"] = "Forum giriş hatası: $errorMsg"
                                result["accounts"] = emptyList<ForumIPTVAccount>()
                                return@withContext result
                            }
                        } else {
                            loginSuccess = body.contains("data-logged-in=\"true\"") || body.contains("log-out")
                        }
                    }
                }
            }

            if (!loginSuccess) {
                runCatching {
                    val rCheck = Request.Builder().url("https://forumsitesi.com.tr/").build()
                    client.newCall(rCheck).execute().use { response ->
                        val body = response.body?.string().orEmpty()
                        loginSuccess = body.contains("data-logged-in=\"true\"") || body.contains("log-out") ||
                                cookieJar.loadForRequest("https://forumsitesi.com.tr".toHttpUrlOrNull() ?: return@use).any { it.name == "xf_user" }
                    }
                }
            }

            if (!loginSuccess) {
                result["success"] = false
                result["message"] = "Giriş başarısız oldu. Lütfen daha sonra tekrar deneyin."
                result["accounts"] = emptyList<ForumIPTVAccount>()
                return@withContext result
            }

            // Step 3: Fetch thread info and detect pagination across all target threads
            progressCallback(0.25f, "🔍 Forum konuları inceleniyor (${TARGET_THREADS.size} kaynak)...")

            val allPageUrls = mutableListOf<String>()
            supervisorScope {
                val threadJobs = TARGET_THREADS.map { threadUrl ->
                    async {
                        val rThread = Request.Builder().url(threadUrl).build()
                        var lastPage = 1
                        runCatching {
                            client.newCall(rThread).execute().use { response ->
                                val body = response.body?.string().orEmpty()
                                val pageRegex = Regex("""page-(\d+)""")
                                val pageMatches = pageRegex.findAll(body).mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }.toList()
                                if (pageMatches.isNotEmpty()) {
                                    lastPage = pageMatches.maxOrNull() ?: 1
                                }
                            }
                        }
                        val pages = when (depthVal) {
                            "Son 1 Sayfa" -> listOf(lastPage)
                            "Son 3 Sayfa" -> (maxOf(1, lastPage - 2)..lastPage).toList()
                            "Son 5 Sayfa" -> (maxOf(1, lastPage - 4)..lastPage).toList()
                            else -> listOf(lastPage)
                        }
                        pages.map { p -> if (p <= 1) threadUrl else "${threadUrl}page-$p" }
                    }
                }
                threadJobs.awaitAll().forEach { allPageUrls.addAll(it) }
            }

            progressCallback(0.35f, "🌐 Konu sayfaları taranıyor...")

            val filteredAccs = ArrayList<ForumIPTVAccount>()
            val seen = HashSet<String>()
            val MAX_CANDIDATES = 100

            for (pageUrl in allPageUrls) {
                if (filteredAccs.size >= MAX_CANDIDATES) break
                val request = runCatching { Request.Builder().url(pageUrl).build() }.getOrNull() ?: continue
                runCatching {
                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val pageHtml = response.body?.string().orEmpty()
                            val hrefs = Regex("""href="([^"]+)"""").findAll(pageHtml).mapNotNull { it.groupValues.getOrNull(1) }.joinToString("\n")
                            val pageText = pageHtml + "\n" + hrefs
                            val pageAccounts = parseText(pageText)
                            for (acc in pageAccounts) {
                                val hostLower = acc.host.lowercase(Locale.ROOT)
                                if (hostLower.contains("forumsitesi.com.tr") || hostLower.contains("uyduportal.com") ||
                                    hostLower.contains("google") || hostLower.contains("yandex") ||
                                    hostLower.contains("github") || hostLower.contains("facebook") ||
                                    hostLower.contains("t.me") || hostLower.contains("telegram")) {
                                    continue
                                }
                                val key = "${acc.host}_${acc.username}_${acc.password}"
                                if (seen.add(key)) {
                                    filteredAccs.add(acc)
                                    if (filteredAccs.size >= MAX_CANDIDATES) break
                                }
                            }
                        }
                    }
                }
            }

            if (filteredAccs.isEmpty()) {
                result["success"] = false
                result["message"] = "Şu anda otomatik IPTV bulunamadı."
                result["accounts"] = emptyList<ForumIPTVAccount>()
                return@withContext result
            }

            val totalCandidatesCount = filteredAccs.size
            result["totalCandidates"] = totalCandidatesCount
            progressCallback(0.6f, "⚡ $totalCandidatesCount adet aday bulundu, doğrulanıyor...")

            // Ayrı doğrulama istemcisi: host başına bağlantı limiti yüksek,
            // timeout kısa. Tüm adayları 20'lik gruplar halinde paralel kontrol et.
            val validationClient = getValidationOkHttpClient()

            fun validateAccount(acc: ForumIPTVAccount): ForumIPTVAccount? {
                if (acc.host.toHttpUrlOrNull() == null) return null
                val apiUrl = "${acc.host}/player_api.php?username=${acc.username}&password=${acc.password}"
                val req = runCatching { Request.Builder().url(apiUrl).build() }.getOrNull() ?: return null
                return runCatching {
                    validationClient.newCall(req).execute().use { resp ->
                        if (resp.isSuccessful) {
                            val body = resp.body?.string().orEmpty()
                            if (body.contains("user_info")) {
                                val json = runCatching { JSONObject(body) }.getOrNull()
                                val userInfo = json?.optJSONObject("user_info")
                                if (userInfo != null) {
                                    val status = userInfo.optString("status", "")
                                    val auth = userInfo.optInt("auth", 1)
                                    val isActive = auth != 0 && (status.equals("active", ignoreCase = true) ||
                                        status.equals("aktif", ignoreCase = true) ||
                                        status.equals("Active", ignoreCase = false))
                                    if (isActive) {
                                        acc.status = "Aktif"
                                        val exp = userInfo.optString("exp_date", "")
                                        acc.expiry = if (exp.isBlank() || exp == "null") {
                                            "Limitsiz"
                                        } else {
                                            val ts = exp.toLongOrNull()
                                            if (ts != null) {
                                                runCatching {
                                                    SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(ts * 1000L))
                                                }.getOrDefault(exp)
                                            } else exp
                                        }
                                        acc
                                    } else {
                                        if (!onlyActive) { acc.status = "Pasif / Süresi Dolmuş"; acc } else null
                                    }
                                } else {
                                    if (!onlyActive) acc else null
                                }
                            } else {
                                if (!onlyActive) acc else null
                            }
                        } else {
                            if (!onlyActive) acc else null
                        }
                    }
                }.getOrNull()
            }

            // 20'lik gruplar halinde doğrula — aşırı paralel bağlantıdan kaçın
            val BATCH_SIZE = 20
            val allCandidates = filteredAccs.take(100)
            val totalBatches = (allCandidates.size + BATCH_SIZE - 1) / BATCH_SIZE
            val validatedAccs = mutableListOf<ForumIPTVAccount>()

            for ((batchIdx, batch) in allCandidates.chunked(BATCH_SIZE).withIndex()) {
                val batchProgress = 0.6f + (batchIdx.toFloat() / totalBatches) * 0.35f
                progressCallback(batchProgress, "⚡ Doğrulanıyor: ${validatedAccs.size} aktif / ${(batchIdx + 1) * BATCH_SIZE} kontrol edildi")
                val batchResults = supervisorScope {
                    batch.map { acc -> async { validateAccount(acc) } }.awaitAll()
                }
                validatedAccs.addAll(batchResults.filterNotNull())
            }

            if (validatedAccs.isEmpty()) {
                result["success"] = false
                result["message"] = "Şu anda aktif otomatik IPTV bulunamadı."
                result["accounts"] = emptyList<ForumIPTVAccount>()
                return@withContext result
            }

            progressCallback(1.0f, "✔️ Tarama tamamlandı!")
            result["success"] = true
            result["accounts"] = validatedAccs
        } catch (e: Throwable) {
            result["success"] = false
            result["message"] = "Kaynak taraması başarısız oldu, daha sonra tekrar deneyin."
            result["accounts"] = emptyList<ForumIPTVAccount>()
        }
        return@withContext result
    }
}

