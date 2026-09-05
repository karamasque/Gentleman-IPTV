package com.kaynanamtv.app.ui.components

import com.google.common.truth.Truth.assertThat
import com.kaynanamtv.data.remote.http.DefaultUserAgentInterceptor
import com.kaynanamtv.data.remote.http.buildAppUserAgent
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Test
import java.security.MessageDigest

class LogoNetworkDiagnosticTest {

    @Test
    fun testLogoFetchWithAppOkHttpClient() {
        val appUserAgent = buildAppUserAgent("1.1.47")
        val client = OkHttpClient.Builder()
            .addInterceptor(DefaultUserAgentInterceptor(appUserAgent))
            .followRedirects(true)
            .followSslRedirects(true)
            .build()

        val urls = listOf(
            "SHOW TV" to "http://bluelogo8991.duckdns.org:2095/LOGO.YENI/TR/ULUSALL/SHOW.TV.png",
            "TRT 1" to "http://bluelogo8991.duckdns.org:2095/LOGO.YENI/TR/ULUSALL/TRT.1.png",
            "EURO STAR" to "http://bluelogo8991.duckdns.org:2095/LOGO.YENI/TR/ULUSALL/EURO.STAR.png",
            "BAYRAK" to "http://bluelogo8991.duckdns.org:2095/LOGO/TR/BAYRAK.png"
        )

        for ((name, url) in urls) {
            val request = Request.Builder()
                .url(url)
                .build()

            try {
                client.newCall(request).execute().use { response ->
                    val code = response.code
                    val bodyBytes = response.body?.bytes() ?: byteArrayOf()
                    val sha = MessageDigest.getInstance("SHA-256").digest(bodyBytes).joinToString("") { "%02x".format(it) }
                    val contentType = response.header("Content-Type")
                    println("[$name] URL: $url -> Code: $code, Size: ${bodyBytes.size}, Type: $contentType, SHA: $sha")
                }
            } catch (e: Exception) {
                println("[$name] URL: $url -> Exception: ${e.javaClass.simpleName} - ${e.message}")
            }
        }
    }
}
