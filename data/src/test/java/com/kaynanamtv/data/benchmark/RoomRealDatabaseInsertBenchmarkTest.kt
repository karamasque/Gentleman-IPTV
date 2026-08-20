package com.kaynanamtv.data.benchmark

import android.content.Context
import androidx.room.Room
import androidx.room.withTransaction
import androidx.test.core.app.ApplicationProvider
import com.google.common.truth.Truth.assertThat
import com.kaynanamtv.data.local.KaynanamTVDatabase
import com.kaynanamtv.data.local.entity.ChannelEntity
import com.kaynanamtv.data.local.entity.ProviderEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class RoomRealDatabaseInsertBenchmarkTest {

    private lateinit var db: KaynanamTVDatabase
    private val testProviderId = 1L

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, KaynanamTVDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        runBlocking {
            db.providerDao().insert(
                ProviderEntity(
                    id = testProviderId,
                    name = "Benchmark Provider",
                    type = com.kaynanamtv.domain.model.ProviderType.XTREAM_CODES,
                    serverUrl = "http://test.com",
                    username = "user",
                    password = "pass"
                )
            )
        }
    }

    @After
    fun tearDown() {
        db.close()
    }

    private fun generateChannelEntities(count: Int): List<ChannelEntity> {
        return (1..count).map { i ->
            ChannelEntity(
                id = 0,
                streamId = i.toLong(),
                name = "Channel $i HD",
                logoUrl = "http://icon.test/$i.png",
                groupTitle = "Category ${i % 20}",
                categoryId = (i % 20 + 1).toLong(),
                categoryName = "Category ${i % 20}",
                streamUrl = "http://test.com/live/user/pass/$i.ts",
                epgChannelId = "epg_$i",
                number = i,
                catchUpSupported = true,
                catchUpDays = 7,
                catchUpSource = null,
                providerId = testProviderId,
                isAdult = false,
                isUserProtected = false,
                logicalGroupId = "",
                errorCount = 0,
                qualityOptionsJson = null,
                syncFingerprint = ""
            )
        }
    }

    private fun measureRealDbInsert(
        dataset: List<ChannelEntity>,
        chunkSize: Int
    ): InsertMetrics = runBlocking {
        db.channelDao().deleteByProvider(testProviderId)

        val runtime = Runtime.getRuntime()
        System.gc()
        val memBefore = runtime.totalMemory() - runtime.freeMemory()

        val batchTimes = mutableListOf<Long>()
        val chunks = dataset.chunked(chunkSize)
        val transactionCount = chunks.size

        val totalStart = System.nanoTime()
        chunks.forEach { chunk ->
            val bStart = System.nanoTime()
            db.withTransaction {
                db.channelDao().insertAll(chunk)
            }
            val bDurationMs = (System.nanoTime() - bStart) / 1_000_000L
            batchTimes.add(bDurationMs)
        }
        val totalMs = (System.nanoTime() - totalStart) / 1_000_000L
        val memAfter = runtime.totalMemory() - runtime.freeMemory()
        val peakHeapMb = (memAfter - memBefore).coerceAtLeast(0L) / (1024 * 1024)

        val totalInserted = db.channelDao().countByProvider(testProviderId)
        assertThat(totalInserted).isEqualTo(dataset.size)

        batchTimes.sort()
        val p50 = batchTimes[batchTimes.size / 2]
        val p95 = batchTimes[((batchTimes.size - 1) * 0.95).toInt()]

        InsertMetrics(
            totalInsertMs = totalMs,
            transactionCount = transactionCount,
            p50BatchMs = p50,
            p95BatchMs = p95,
            peakHeapMb = peakHeapMb
        )
    }

    private data class InsertMetrics(
        val totalInsertMs: Long,
        val transactionCount: Int,
        val p50BatchMs: Long,
        val p95BatchMs: Long,
        val peakHeapMb: Long
    )

    private fun runSuite(count: Int) {
        val dataset = generateChannelEntities(count)
        val chunkSizes = listOf(250, 500, 1000, 2000)

        println("\n========================================================")
        println("=== REAL ROOM/SQLITE INSERT BENCHMARK ($count items) ===")
        println("========================================================")
        for (chunk in chunkSizes) {
            val metrics = measureRealDbInsert(dataset, chunk)
            println(
                "CHUNK: %-4d | TOTAL_INSERT_MS: %-5d | TRANSACTIONS: %-4d | P50_BATCH: %-3d ms | P95_BATCH: %-3d ms | PEAK_HEAP: %d MB (ESTIMATED)".format(
                    chunk,
                    metrics.totalInsertMs,
                    metrics.transactionCount,
                    metrics.p50BatchMs,
                    metrics.p95BatchMs,
                    metrics.peakHeapMb
                )
            )
        }
    }

    @Test
    fun benchmarkRealRoomInsert10k() {
        runSuite(10_000)
    }

    @Test
    fun benchmarkRealRoomInsert30k() {
        runSuite(30_000)
    }

    @Test
    fun benchmarkRealRoomInsert50k() {
        runSuite(50_000)
    }
}
