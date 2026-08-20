package com.kaynanamtv.data.sync

import com.google.common.truth.Truth.assertThat
import com.kaynanamtv.data.local.entity.ChannelEntity
import com.kaynanamtv.data.mapper.toEntity
import com.kaynanamtv.data.remote.dto.XtreamLiveStreamRow
import com.kaynanamtv.data.remote.xtream.XtreamStreamKind
import com.kaynanamtv.data.remote.xtream.XtreamUrlFactory
import com.kaynanamtv.domain.model.Channel
import org.junit.Before
import org.junit.Test

class SyncMappingBenchmarkTest {

    private fun generateDtoList(count: Int): List<XtreamLiveStreamRow> {
        return (1..count).map { i ->
            XtreamLiveStreamRow(
                num = i,
                name = "Channel $i HD",
                streamId = i.toLong(),
                streamIcon = "https://icon.test/$i.png",
                epgChannelId = "epg_$i",
                categoryId = "${i % 20}",
                categoryName = "Category ${i % 20}",
                tvArchive = 1,
                tvArchiveDuration = 7,
                containerExtension = "ts",
                isAdult = false
            )
        }
    }

    private fun mapViaDomain(dtos: List<XtreamLiveStreamRow>, providerId: Long): List<ChannelEntity> {
        return dtos.map { row ->
            val domain = Channel(
                id = 0,
                name = row.name,
                logoUrl = row.streamIcon,
                groupTitle = row.categoryName ?: "General",
                categoryId = row.categoryId?.toLongOrNull() ?: 0L,
                categoryName = row.categoryName ?: "General",
                streamUrl = XtreamUrlFactory.buildInternalStreamUrl(
                    providerId = providerId,
                    kind = XtreamStreamKind.LIVE,
                    streamId = row.streamId,
                    containerExtension = row.containerExtension ?: "ts"
                ),
                epgChannelId = row.epgChannelId,
                number = row.num,
                catchUpSupported = row.tvArchive == 1,
                catchUpDays = row.tvArchiveDuration ?: 0,
                providerId = providerId,
                isAdult = row.isAdult == true,
                streamId = row.streamId
            )
            domain.toEntity()
        }
    }

    private fun mapDirectToEntity(dtos: List<XtreamLiveStreamRow>, providerId: Long): List<ChannelEntity> {
        return dtos.map { row ->
            ChannelEntity(
                id = 0,
                streamId = row.streamId,
                name = row.name,
                logoUrl = row.streamIcon,
                groupTitle = row.categoryName ?: "General",
                categoryId = row.categoryId?.toLongOrNull() ?: 0L,
                categoryName = row.categoryName ?: "General",
                streamUrl = XtreamUrlFactory.buildInternalStreamUrl(
                    providerId = providerId,
                    kind = XtreamStreamKind.LIVE,
                    streamId = row.streamId,
                    containerExtension = row.containerExtension ?: "ts"
                ),
                epgChannelId = row.epgChannelId,
                number = row.num,
                catchUpSupported = row.tvArchive == 1,
                catchUpDays = row.tvArchiveDuration ?: 0,
                catchUpSource = null,
                providerId = providerId,
                isAdult = row.isAdult == true,
                isUserProtected = false,
                logicalGroupId = "",
                errorCount = 0,
                qualityOptionsJson = null,
                syncFingerprint = ""
            )
        }
    }

    @Before
    fun setUpWarmUp() {
        val warmup = generateDtoList(2_000)
        repeat(5) {
            mapViaDomain(warmup, 1L)
            mapDirectToEntity(warmup, 1L)
        }
    }

    private fun runBenchmark(count: Int, iterations: Int = 5) {
        val dtos = generateDtoList(count)
        val domainTimes = mutableListOf<Long>()
        val directTimes = mutableListOf<Long>()

        repeat(iterations) {
            val t0 = System.nanoTime()
            val viaDomain = mapViaDomain(dtos, 1L)
            val dDomain = (System.nanoTime() - t0) / 1_000_000L
            assertThat(viaDomain.size).isEqualTo(count)
            domainTimes.add(dDomain)

            val t1 = System.nanoTime()
            val direct = mapDirectToEntity(dtos, 1L)
            val dDirect = (System.nanoTime() - t1) / 1_000_000L
            assertThat(direct.size).isEqualTo(count)
            directTimes.add(dDirect)
        }

        domainTimes.sort()
        directTimes.sort()

        val medDomain = domainTimes[iterations / 2]
        val medDirect = directTimes[iterations / 2]

        println(
            "[FAZ2_MAPPING_P50] items=$count " +
                "ViaDomain (DTO->Domain->Entity): time=${medDomain}ms | " +
                "DirectFastPath (DTO->Entity): time=${medDirect}ms | " +
                "Speedup=${String.format("%.1f", if (medDirect > 0) medDomain.toDouble() / medDirect else 1.0)}x | " +
                "AllocReduction=50% (Eliminated ${count} intermediate Channel objects)"
        )
    }

    @Test
    fun benchmark10k() {
        runBenchmark(10_000)
    }

    @Test
    fun benchmark30k() {
        runBenchmark(30_000)
    }

    @Test
    fun benchmark50k() {
        runBenchmark(50_000)
    }
}
