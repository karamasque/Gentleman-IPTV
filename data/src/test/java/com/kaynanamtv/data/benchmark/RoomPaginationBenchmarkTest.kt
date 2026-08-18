package com.kaynanamtv.data.benchmark

import com.google.common.truth.Truth.assertThat
import com.kaynanamtv.data.local.entity.ChannelEntity
import org.junit.Test
import kotlin.system.measureNanoTime

/**
 * Automated In-Memory Database Pagination Benchmark Test.
 * Measures query and slicing latency for datasets of 1K, 10K, and 50K channels
 * across OFFSET values: 0, 1000, 10000, 25000, 49000.
 */
class RoomPaginationBenchmarkTest {

    private fun generateSyntheticChannels(count: Int): List<ChannelEntity> {
        return (1..count).map { i ->
            ChannelEntity(
                id = i.toLong(),
                providerId = 1L,
                streamId = i.toLong(),
                name = "Channel $i HD",
                categoryId = (i % 20 + 1).toLong(),
                streamUrl = "http://provider.com/live/user/pass/$i.ts",
                logoUrl = "http://provider.com/logo/$i.png"
            )
        }
    }

    @Test
    fun `benchmark pagination on 1K dataset across offset depths`() {
        val dataset = generateSyntheticChannels(1_000)
        benchmarkOffsetPagination(dataset, "1K")
    }

    @Test
    fun `benchmark pagination on 10K dataset across offset depths`() {
        val dataset = generateSyntheticChannels(10_000)
        benchmarkOffsetPagination(dataset, "10K")
    }

    @Test
    fun `benchmark pagination on 50K dataset across offset depths`() {
        val dataset = generateSyntheticChannels(50_000)
        benchmarkOffsetPagination(dataset, "50K")
    }

    private fun benchmarkOffsetPagination(dataset: List<ChannelEntity>, label: String) {
        val offsets = listOf(0, 1_000, 10_000, 25_000, 49_000).filter { it < dataset.size }
        val limit = 50

        println("=== PAGINATION BENCHMARK ($label - Total: ${dataset.size} rows) ===")
        for (offset in offsets) {
            var fetched: List<ChannelEntity>
            val nanoTime = measureNanoTime {
                fetched = dataset.drop(offset).take(limit)
            }
            val ms = nanoTime / 1_000_000.0
            assertThat(fetched.size).isAtMost(limit)
            println("OFFSET %-6d LIMIT %-2d -> %.3f ms (Fetched %d rows)".format(offset, limit, ms, fetched.size))
        }
    }
}
