package com.kaynanamtv.data.remote.xtream

import com.google.common.truth.Truth.assertThat
import com.google.gson.JsonParser
import com.google.gson.stream.JsonReader
import com.kaynanamtv.data.remote.dto.XtreamLiveStreamRow
import java.io.ByteArrayInputStream
import java.io.InputStreamReader
import kotlinx.serialization.json.Json
import org.junit.Before
import org.junit.Test

class XtreamStreamingParserBenchmarkTest {

    private val json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        explicitNulls = false
        coerceInputValues = true
    }

    private fun generateLiveJsonPayload(count: Int): ByteArray {
        val sb = StringBuilder(count * 160)
        sb.append("[")
        for (i in 1..count) {
            if (i > 1) sb.append(",")
            val streamIdVal = when (i % 4) {
                0 -> "\"$i\""
                1 -> "$i"
                2 -> "\"\""
                else -> "null"
            }
            sb.append("""{"num":$i,"name":"Channel $i","stream_id":$streamIdVal,"stream_icon":"http://icon.test/$i.png","category_id":"${i % 20}","tv_archive":1}""")
        }
        sb.append("]")
        return sb.toString().toByteArray(Charsets.UTF_8)
    }

    private fun parseLegacy(bytes: ByteArray): Triple<Long, Long, Int> {
        val runtime = Runtime.getRuntime()
        System.gc()
        val memBefore = runtime.totalMemory() - runtime.freeMemory()
        val start = System.nanoTime()

        val reader = JsonReader(InputStreamReader(ByteArrayInputStream(bytes), Charsets.UTF_8))
        reader.isLenient = true
        var count = 0
        reader.beginArray()
        while (reader.hasNext()) {
            val element = JsonParser.parseReader(reader)
            val item = json.decodeFromString(XtreamLiveStreamRow.serializer(), element.toString())
            if (item.num > 0) count++
        }
        reader.endArray()
        val durationMs = (System.nanoTime() - start) / 1_000_000L
        val memAfter = runtime.totalMemory() - runtime.freeMemory()
        val heapDeltaBytes = (memAfter - memBefore).coerceAtLeast(0L)
        return Triple(durationMs, heapDeltaBytes, count)
    }

    private fun parseDirect(bytes: ByteArray): Triple<Long, Long, Int> {
        val runtime = Runtime.getRuntime()
        System.gc()
        val memBefore = runtime.totalMemory() - runtime.freeMemory()
        val start = System.nanoTime()

        val reader = JsonReader(InputStreamReader(ByteArrayInputStream(bytes), Charsets.UTF_8))
        reader.isLenient = true
        var count = 0
        reader.beginArray()
        while (reader.hasNext()) {
            val item = XtreamStreamJsonReader.readLiveStreamRow(reader)
            if (item != null && item.num > 0) count++
        }
        reader.endArray()
        val durationMs = (System.nanoTime() - start) / 1_000_000L
        val memAfter = runtime.totalMemory() - runtime.freeMemory()
        val heapDeltaBytes = (memAfter - memBefore).coerceAtLeast(0L)
        return Triple(durationMs, heapDeltaBytes, count)
    }

    @Before
    fun setUpWarmUp() {
        val warmupBytes = generateLiveJsonPayload(3_000)
        repeat(5) {
            parseLegacy(warmupBytes)
            parseDirect(warmupBytes)
        }
    }

    private fun measureBenchmark(count: Int, iterations: Int = 5) {
        val bytes = generateLiveJsonPayload(count)
        val legacyTimes = mutableListOf<Long>()
        val legacyHeaps = mutableListOf<Long>()
        val directTimes = mutableListOf<Long>()
        val directHeaps = mutableListOf<Long>()

        repeat(iterations) {
            val (tLeg, hLeg, cLeg) = parseLegacy(bytes)
            assertThat(cLeg).isEqualTo(count)
            legacyTimes.add(tLeg)
            legacyHeaps.add(hLeg)

            val (tDir, hDir, cDir) = parseDirect(bytes)
            assertThat(cDir).isEqualTo(count)
            directTimes.add(tDir)
            directHeaps.add(hDir)
        }

        legacyTimes.sort()
        legacyHeaps.sort()
        directTimes.sort()
        directHeaps.sort()

        val medianLegTime = legacyTimes[iterations / 2]
        val medianLegHeapMb = legacyHeaps[iterations / 2] / (1024 * 1024)
        val medianDirTime = directTimes[iterations / 2]
        val medianDirHeapMb = directHeaps[iterations / 2] / (1024 * 1024)

        println(
            "[BENCHMARK_P50] items=$count " +
                "Legacy: time=${medianLegTime}ms heapDelta=${medianLegHeapMb}MB | " +
                "Direct: time=${medianDirTime}ms heapDelta=${medianDirHeapMb}MB | " +
                "Speedup=${String.format("%.1f", if (medianDirTime > 0) medianLegTime.toDouble() / medianDirTime else 1.0)}x | " +
                "HeapReductionMb=${(medianLegHeapMb - medianDirHeapMb).coerceAtLeast(0)}MB"
        )
    }

    @Test
    fun benchmarkComparison10k() {
        measureBenchmark(10_000)
    }

    @Test
    fun benchmarkComparison30k() {
        measureBenchmark(30_000)
    }

    @Test
    fun benchmarkComparison50k() {
        measureBenchmark(50_000)
    }
}
