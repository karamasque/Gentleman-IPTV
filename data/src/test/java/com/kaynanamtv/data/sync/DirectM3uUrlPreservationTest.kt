package com.kaynanamtv.data.sync

import com.google.common.truth.Truth.assertThat
import com.kaynanamtv.data.parser.M3uParser
import com.kaynanamtv.data.util.ProviderInputSanitizer
import com.kaynanamtv.data.util.UrlSecurityPolicy
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.After
import org.junit.Before
import org.junit.Test
import java.io.BufferedReader
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import java.net.ServerSocket
import java.nio.charset.StandardCharsets
import kotlin.concurrent.thread

class DirectM3uUrlPreservationTest {

    private lateinit var serverSocket: ServerSocket
    private var serverPort: Int = 0
    @Volatile
    private var lastReceivedPath: String? = null
    @Volatile
    private var running = true
    private val m3uParser = M3uParser()

    @Before
    fun setUp() {
        serverSocket = ServerSocket(0)
        serverPort = serverSocket.localPort
        running = true
        thread(isDaemon = true) {
            while (running && !serverSocket.isClosed) {
                try {
                    val socket = serverSocket.accept()
                    val reader = BufferedReader(InputStreamReader(socket.getInputStream()))
                    val requestLine = reader.readLine()
                    if (requestLine != null) {
                        val parts = requestLine.split(" ")
                        if (parts.size >= 2) {
                            lastReceivedPath = parts[1]
                        }
                    }
                    val body = """
                        #EXTM3U
                        #EXTINF:-1 tvg-id="ch1" group-title="News",News 24
                        http://example.com/live/ch1.ts
                    """.trimIndent()
                    val bodyBytes = body.toByteArray(StandardCharsets.UTF_8)
                    val out = socket.getOutputStream()
                    val responseHeader = "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: ${bodyBytes.size}\r\nConnection: close\r\n\r\n"
                    out.write(responseHeader.toByteArray(StandardCharsets.UTF_8))
                    out.write(bodyBytes)
                    out.flush()
                    socket.close()
                } catch (_: Exception) {
                }
            }
        }
    }

    @After
    fun tearDown() {
        running = false
        try {
            serverSocket.close()
        } catch (_: Exception) {
        }
    }

    @Test
    fun `normalizeUrl preserves exact get php query string with type=m3u and does not inject output or m3u_plus`() {
        val input = "https://example.com/get.php?username=testuser&password=testpass&type=m3u"
        val normalized = ProviderInputSanitizer.normalizeUrl(input)

        assertThat(normalized).isEqualTo("https://example.com/get.php?username=testuser&password=testpass&type=m3u")
        assertThat(normalized).doesNotContain("type=m3u_plus")
        assertThat(normalized).doesNotContain("output=")
    }

    @Test
    fun `normalizeUrl preserves explicit type=m3u_plus without modification`() {
        val input = "https://example.com/get.php?username=testuser&password=testpass&type=m3u_plus"
        val normalized = ProviderInputSanitizer.normalizeUrl(input)

        assertThat(normalized).isEqualTo("https://example.com/get.php?username=testuser&password=testpass&type=m3u_plus")
    }

    @Test
    fun `normalizeUrl preserves explicit output=m3u8 without overriding or injecting output=ts`() {
        val input = "https://example.com/get.php?username=testuser&password=testpass&type=m3u&output=m3u8"
        val normalized = ProviderInputSanitizer.normalizeUrl(input)

        assertThat(normalized).isEqualTo("https://example.com/get.php?username=testuser&password=testpass&type=m3u&output=m3u8")
        assertThat(normalized).doesNotContain("output=ts")
    }

    @Test
    fun `normalizeUrl preserves get php without type or output parameter without injecting defaults`() {
        val input = "https://example.com/get.php?username=testuser&password=testpass"
        val normalized = ProviderInputSanitizer.normalizeUrl(input)

        assertThat(normalized).isEqualTo("https://example.com/get.php?username=testuser&password=testpass")
        assertThat(normalized).doesNotContain("type=")
        assertThat(normalized).doesNotContain("output=")
    }

    @Test
    fun `normalizeUrl preserves unknown query parameters and url encoding`() {
        val input = "https://example.com/get.php?username=testuser&password=testpass&custom_param=val%201&token=abc%2B123"
        val normalized = ProviderInputSanitizer.normalizeUrl(input)

        assertThat(normalized).isEqualTo("https://example.com/get.php?username=testuser&password=testpass&custom_param=val%201&token=abc%2B123")
    }

    @Test
    fun `upgradeXtreamM3uUrl returns input URL untouched for direct get php M3U`() {
        val importer = SyncManagerM3uImporter(
            context = org.mockito.kotlin.mock(),
            m3uParser = m3uParser,
            okHttpClient = OkHttpClient(),
            syncCatalogStore = org.mockito.kotlin.mock(),
            retryTransient = {},
            progress = { _, _, _ -> },
            syncProgressBus = org.mockito.kotlin.mock()
        )

        val directM3uUrl = "https://example.com/get.php?username=testuser&password=testpass&type=m3u"
        assertThat(importer.upgradeXtreamM3uUrl(directM3uUrl)).isEqualTo(directM3uUrl)

        val directNoTypeUrl = "https://example.com/get.php?username=testuser&password=testpass"
        assertThat(importer.upgradeXtreamM3uUrl(directNoTypeUrl)).isEqualTo(directNoTypeUrl)
    }

    @Test
    fun `mock HTTP server receives exact requested get php query string without mutations`() {
        val targetUrl = "http://127.0.0.1:$serverPort/get.php?username=testuser&password=testpass&type=m3u&custom=1"
        val client = OkHttpClient()
        val req = Request.Builder().url(targetUrl).build()

        client.newCall(req).execute().use { response ->
            assertThat(response.isSuccessful).isTrue()
        }

        assertThat(lastReceivedPath).isEqualTo("/get.php?username=testuser&password=testpass&type=m3u&custom=1")
        assertThat(lastReceivedPath).doesNotContain("type=m3u_plus")
        assertThat(lastReceivedPath).doesNotContain("output=ts")
    }

    @Test
    fun `direct M3U playlist stream parsing handles EXTM3U and extracts channels correctly`() {
        val sampleM3u = """
            #EXTM3U
            #EXTINF:-1 tvg-id="ch1" tvg-name="Channel 1" tvg-logo="http://example.com/logo1.png" group-title="General",Channel 1 HD
            http://example.com/stream1.ts
            #EXTINF:-1 tvg-id="mov1" group-title="Movies HD",Sample Movie (2024)
            http://example.com/movie/sample.mp4
        """.trimIndent()

        val parseResult = m3uParser.parse(ByteArrayInputStream(sampleM3u.toByteArray(StandardCharsets.UTF_8)))
        assertThat(parseResult.entries).hasSize(2)
        assertThat(parseResult.entries[0].name).isEqualTo("Channel 1 HD")
        assertThat(parseResult.entries[0].groupTitle).isEqualTo("General")
        assertThat(parseResult.entries[1].name).isEqualTo("Sample Movie (2024)")
        assertThat(M3uParser.isVodEntry(parseResult.entries[1])).isTrue()
    }

    @Test
    fun `validatePlaylistSourceUrl accepts get php with query parameters`() {
        val url = "https://example.com/get.php?username=user&password=pass&type=m3u"
        val error = UrlSecurityPolicy.validatePlaylistSourceUrl(url)
        assertThat(error).isNull()
    }
}
