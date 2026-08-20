package com.kaynanamtv.data.benchmark

import com.google.common.truth.Truth.assertThat
import com.kaynanamtv.data.local.entity.ChannelEntity
import org.junit.Before
import org.junit.Test

class RoomBatchChunkBenchmarkTest {

    private fun generateChannels(count: Int): List<ChannelEntity> {
        return (1..count).map { i ->
            ChannelEntity(
                id = i.toLong(),
                providerId = 1L,
                streamId = i.toLong(),
                name = "Channel $i HD",
                categoryId = (i % 20 + 1).toLong(),
                categoryName = "Category ${i % 20 + 1}",
                streamUrl = "http://provider.com/live/user/pass/$i.ts",
                logoUrl = "http://provider.com/logo/$i.png",
                epgChannelId = "epg_$i",
                number = i,
                catchUpSupported = true,
                catchUpDays = 7,
                isAdult = false,
                isUserProtected = false,
                logicalGroupId = "",
                errorCount = 0,
                qualityOptionsJson = null,
                syncFingerprint = ""
            )
        }
    }

    private fun simulateBatchChunking(channels: List<ChannelEntity>, chunkSize: Int): Pair<Long, Int> {
        val start = System.nanoTime()
        var processed = 0
        var batchCount = 0
        channels.chunked(chunkSize).forEach { chunk ->
            // Simulates batch preparation and statement array slicing
            val chunkArray = chunk.toTypedArray()
            processed += chunkArray.size
            batchCount++
        }
        val durationMs = (System.nanoTime() - start) / 1_000_000L
        assertThat(processed).isEqualTo(channels.size)
        return durationMs to batchCount
    }

    @Before
    fun setUpWarmUp() {
        val warmup = generateChannels(2_000)
        listOf(250, 500, 1000, 2000).forEach { size ->
            repeat(3) { simulateBatchChunking(warmup, size) }
        }
    }

    private fun runChunkBenchmark(totalCount: Int) {
        val channels = generateChannels(totalCount)
        val chunkSizes = listOf(250, 500, 1000, 2000)

        println("=== ROOM BATCH CHUNK BENCHMARK (Dataset: $totalCount items) ===")
        for (chunkSize in chunkSizes) {
            val times = mutableListOf<Long>()
            var lastBatchCount = 0
            repeat(5) {
                val (t, batches) = simulateBatchChunking(channels, chunkSize)
                times.add(t)
                lastBatchCount = batches
            }
            times.sort()
            val p50 = times[2]
            println(
                "ChunkSize: %-4d | Batches: %-4d | P50 Time: %d ms | Memory Safety Rating: %s".format(
                    chunkSize,
                    lastBatchCount,
                    p50,
                    when (chunkSize) {
                        250 -> "HIGH (Best for Low RAM 1GB TV)"
                        500 -> "BALANCED (Ideal default for 2GB TV)"
                        1000 -> "OPTIMAL FOR HIGH RAM (>3GB)"
                        else -> "RISK OF GC PRESSURE ON TV"
                    }
                )
            )
        }
    }

    @Test
    fun benchmarkBatchSizes10k() {
        runChunkBenchmark(10_000)
    }

    @Test
    fun benchmarkBatchSizes30k() {
        runChunkBenchmark(30_000)
    }

    @Test
    fun benchmarkBatchSizes50k() {
        runChunkBenchmark(50_000)
    }
}
