package com.kaynanamtv.player.playback

import com.google.common.truth.Truth.assertThat
import com.kaynanamtv.domain.model.PlaybackBufferMode
import com.kaynanamtv.domain.model.StreamInfo
import com.kaynanamtv.domain.model.VideoFormat
import org.junit.Test

class PlaybackBufferPoliciesTest {

    @Test
    fun `normal live uses production live buffer baseline`() {
        val policy = PlaybackBufferPolicies.forPlayback(isLive = true, compatibilityMode = false)

        assertThat(policy.label).isEqualTo("stable-live")
        assertThat(policy.minBufferMs).isEqualTo(8_000)
        assertThat(policy.maxBufferMs).isEqualTo(25_000)
        assertThat(policy.playbackBufferMs).isEqualTo(750)
        assertThat(policy.rebufferMs).isEqualTo(3_000)
        assertThat(policy.targetBufferBytes).isEqualTo(-1)
        assertThat(policy.prioritizeTimeOverSizeThresholds).isTrue()
    }

    @Test
    fun `small live hls uses production live buffer baseline`() {
        val policy = PlaybackBufferPolicies.forPlayback(
            resolvedStreamType = ResolvedStreamType.HLS,
            compatibilityMode = false,
            lowMemoryDevice = false,
            bufferMode = PlaybackBufferMode.SMALL
        )

        assertThat(policy.label).isEqualTo("stable-live")
        assertThat(policy.minBufferMs).isEqualTo(8_000)
        assertThat(policy.maxBufferMs).isEqualTo(25_000)
        assertThat(policy.playbackBufferMs).isEqualTo(750)
        assertThat(policy.rebufferMs).isEqualTo(3_000)
        assertThat(policy.targetBufferBytes).isEqualTo(-1)
    }

    @Test
    fun `medium live hls uses deeper buffer and byte target`() {
        val policy = PlaybackBufferPolicies.forPlayback(
            resolvedStreamType = ResolvedStreamType.HLS,
            compatibilityMode = false,
            lowMemoryDevice = false,
            bufferMode = PlaybackBufferMode.MEDIUM
        )

        assertThat(policy.label).isEqualTo("medium-live")
        assertThat(policy.minBufferMs).isEqualTo(8_000)
        assertThat(policy.maxBufferMs).isEqualTo(25_000)
        assertThat(policy.playbackBufferMs).isEqualTo(750)
        assertThat(policy.rebufferMs).isEqualTo(3_000)
        assertThat(policy.targetBufferBytes).isEqualTo(24 * 1024 * 1024)
        assertThat(policy.qualityReason).isEqualTo("user-medium")
    }

    @Test
    fun `large live hls uses deepest buffer and byte target`() {
        val policy = PlaybackBufferPolicies.forPlayback(
            resolvedStreamType = ResolvedStreamType.HLS,
            compatibilityMode = false,
            lowMemoryDevice = false,
            bufferMode = PlaybackBufferMode.LARGE
        )

        assertThat(policy.label).isEqualTo("large-live")
        assertThat(policy.minBufferMs).isEqualTo(8_000)
        assertThat(policy.maxBufferMs).isEqualTo(25_000)
        assertThat(policy.playbackBufferMs).isEqualTo(750)
        assertThat(policy.rebufferMs).isEqualTo(3_000)
        assertThat(policy.targetBufferBytes).isEqualTo(32 * 1024 * 1024)
        assertThat(policy.qualityReason).isEqualTo("user-large")
    }

    @Test
    fun `auto live hls promotes to large when metadata hints uhd`() {
        val policy = PlaybackBufferPolicies.forPlayback(
            resolvedStreamType = ResolvedStreamType.HLS,
            compatibilityMode = false,
            lowMemoryDevice = false,
            bufferMode = PlaybackBufferMode.AUTO,
            streamInfo = StreamInfo(
                url = "https://example.com/live/sports_2160p/index.m3u8",
                title = "Sports 4K HDR",
                containerExtension = "m3u8"
            )
        )

        assertThat(policy.label).isEqualTo("auto-uhd-live")
        assertThat(policy.minBufferMs).isEqualTo(8_000)
        assertThat(policy.maxBufferMs).isEqualTo(20_000)
        assertThat(policy.playbackBufferMs).isEqualTo(1_000)
        assertThat(policy.rebufferMs).isEqualTo(2_000)
        assertThat(policy.targetBufferBytes).isEqualTo(32 * 1024 * 1024)
        assertThat(policy.qualityReason).isEqualTo("metadata-2160p")
    }

    @Test
    fun `auto live hls promotes to large when observed format is uhd`() {
        val policy = PlaybackBufferPolicies.forPlayback(
            resolvedStreamType = ResolvedStreamType.HLS,
            compatibilityMode = false,
            lowMemoryDevice = false,
            bufferMode = PlaybackBufferMode.AUTO,
            observedVideoFormat = VideoFormat(width = 3_840, height = 2_160)
        )

        assertThat(policy.label).isEqualTo("auto-uhd-live")
        assertThat(policy.targetBufferBytes).isEqualTo(32 * 1024 * 1024)
        assertThat(policy.qualityReason).isEqualTo("observed-width-3840")
    }

    @Test
    fun `auto live hls promotes to large when observed format is high bitrate`() {
        val policy = PlaybackBufferPolicies.forPlayback(
            resolvedStreamType = ResolvedStreamType.HLS,
            compatibilityMode = false,
            lowMemoryDevice = false,
            bufferMode = PlaybackBufferMode.AUTO,
            observedVideoFormat = VideoFormat(width = 1_920, height = 1_080, bitrate = 20_000_000)
        )

        assertThat(policy.label).isEqualTo("auto-uhd-live")
        assertThat(policy.targetBufferBytes).isEqualTo(32 * 1024 * 1024)
        assertThat(policy.qualityReason).isEqualTo("observed-bitrate-20000000")
    }

    @Test
    fun `auto live hls promotes to large when observed format is hdr`() {
        val policy = PlaybackBufferPolicies.forPlayback(
            resolvedStreamType = ResolvedStreamType.HLS,
            compatibilityMode = false,
            lowMemoryDevice = false,
            bufferMode = PlaybackBufferMode.AUTO,
            observedVideoFormat = VideoFormat(width = 1_920, height = 1_080, isHdr = true)
        )

        assertThat(policy.label).isEqualTo("auto-uhd-live")
        assertThat(policy.targetBufferBytes).isEqualTo(32 * 1024 * 1024)
        assertThat(policy.qualityReason).isEqualTo("observed-hdr")
    }

    @Test
    fun `auto live hls caps uhd promotion to medium on low memory devices`() {
        val policy = PlaybackBufferPolicies.forPlayback(
            resolvedStreamType = ResolvedStreamType.HLS,
            compatibilityMode = false,
            lowMemoryDevice = true,
            bufferMode = PlaybackBufferMode.AUTO,
            streamInfo = StreamInfo(
                url = "https://example.com/live/uhd/index.m3u8",
                title = "Nature UHD"
            )
        )

        assertThat(policy.label).isEqualTo("auto-uhd-live-capped")
        assertThat(policy.minBufferMs).isEqualTo(8_000)
        assertThat(policy.maxBufferMs).isEqualTo(25_000)
        assertThat(policy.playbackBufferMs).isEqualTo(750)
        assertThat(policy.rebufferMs).isEqualTo(3_000)
        assertThat(policy.targetBufferBytes).isEqualTo(24 * 1024 * 1024)
        assertThat(policy.lowMemoryCapped).isTrue()
        assertThat(policy.qualityReason).isEqualTo("metadata-uhd")
    }

    @Test
    fun `compatibility live uses larger live buffer`() {
        val policy = PlaybackBufferPolicies.forPlayback(isLive = true, compatibilityMode = true)

        assertThat(policy.label).isEqualTo("compat-live")
        assertThat(policy.minBufferMs).isEqualTo(8_000)
        assertThat(policy.maxBufferMs).isEqualTo(25_000)
        assertThat(policy.playbackBufferMs).isEqualTo(750)
        assertThat(policy.rebufferMs).isEqualTo(3_000)
        assertThat(policy.targetBufferBytes).isEqualTo(-1)
        assertThat(policy.prioritizeTimeOverSizeThresholds).isTrue()
    }

    @Test
    fun `compatibility live hls in auto can still promote from metadata`() {
        val policy = PlaybackBufferPolicies.forPlayback(
            resolvedStreamType = ResolvedStreamType.HLS,
            compatibilityMode = true,
            lowMemoryDevice = false,
            bufferMode = PlaybackBufferMode.AUTO,
            streamInfo = StreamInfo(url = "https://example.com/live/4k/index.m3u8")
        )

        assertThat(policy.label).isEqualTo("auto-uhd-live")
        assertThat(policy.minBufferMs).isEqualTo(8_000)
        assertThat(policy.targetBufferBytes).isEqualTo(32 * 1024 * 1024)
    }

    @Test
    fun `mpeg ts live prioritizes playable time over byte target`() {
        val policy = PlaybackBufferPolicies.forPlayback(
            resolvedStreamType = ResolvedStreamType.MPEG_TS_LIVE,
            compatibilityMode = false
        )

        assertThat(policy.label).isEqualTo("mpeg-ts-live")
        assertThat(policy.minBufferMs).isEqualTo(8_000)
        assertThat(policy.maxBufferMs).isEqualTo(25_000)
        assertThat(policy.playbackBufferMs).isEqualTo(750)
        assertThat(policy.rebufferMs).isEqualTo(3_000)
        assertThat(policy.targetBufferBytes).isEqualTo(16 * 1024 * 1024)
        assertThat(policy.prioritizeTimeOverSizeThresholds).isTrue()
    }

    @Test
    fun `mpeg ts live promotes to auto-uhd-live in auto mode with uhd metadata`() {
        val policy = PlaybackBufferPolicies.forPlayback(
            resolvedStreamType = ResolvedStreamType.MPEG_TS_LIVE,
            compatibilityMode = false,
            lowMemoryDevice = false,
            bufferMode = PlaybackBufferMode.AUTO,
            streamInfo = StreamInfo(url = "https://example.com/live/4k/channel.ts")
        )

        assertThat(policy.label).isEqualTo("auto-uhd-live")
        assertThat(policy.minBufferMs).isEqualTo(8_000)
        assertThat(policy.maxBufferMs).isEqualTo(20_000)
        assertThat(policy.targetBufferBytes).isEqualTo(32 * 1024 * 1024)
    }

    @Test
    fun `mpeg ts live uses medium only when user selects medium`() {
        val policy = PlaybackBufferPolicies.forPlayback(
            resolvedStreamType = ResolvedStreamType.MPEG_TS_LIVE,
            compatibilityMode = false,
            lowMemoryDevice = false,
            bufferMode = PlaybackBufferMode.MEDIUM
        )

        assertThat(policy.label).isEqualTo("medium-live")
        assertThat(policy.minBufferMs).isEqualTo(8_000)
        assertThat(policy.maxBufferMs).isEqualTo(25_000)
        assertThat(policy.targetBufferBytes).isEqualTo(24 * 1024 * 1024)
    }

    @Test
    fun `vod uses deeper movie buffer`() {
        val policy = PlaybackBufferPolicies.forPlayback(isLive = false, compatibilityMode = false)

        assertThat(policy.label).isEqualTo("stable-vod")
        assertThat(policy.minBufferMs).isEqualTo(10_000)
        assertThat(policy.maxBufferMs).isEqualTo(25_000)
        assertThat(policy.playbackBufferMs).isEqualTo(750)
        assertThat(policy.rebufferMs).isEqualTo(2_000)
        assertThat(policy.targetBufferBytes).isEqualTo(-1)
        assertThat(policy.prioritizeTimeOverSizeThresholds).isTrue()
    }

    @Test
    fun `auto progressive vod promotes to large when metadata hints 4k`() {
        val policy = PlaybackBufferPolicies.forPlayback(
            resolvedStreamType = ResolvedStreamType.PROGRESSIVE,
            compatibilityMode = false,
            lowMemoryDevice = false,
            bufferMode = PlaybackBufferMode.AUTO,
            streamInfo = StreamInfo(
                url = "https://example.com/vod/movie_4k.mp4",
                title = "Awesome Movie 4K"
            )
        )

        assertThat(policy.label).isEqualTo("auto-uhd-vod")
        assertThat(policy.minBufferMs).isEqualTo(10_000)
        assertThat(policy.maxBufferMs).isEqualTo(30_000)
        assertThat(policy.playbackBufferMs).isEqualTo(1_000)
        assertThat(policy.rebufferMs).isEqualTo(2_000)
        assertThat(policy.targetBufferBytes).isEqualTo(32 * 1024 * 1024)
    }

    @Test
    fun `auto progressive vod promotes to medium capped on low memory devices when metadata hints 4k`() {
        val policy = PlaybackBufferPolicies.forPlayback(
            resolvedStreamType = ResolvedStreamType.PROGRESSIVE,
            compatibilityMode = false,
            lowMemoryDevice = true,
            bufferMode = PlaybackBufferMode.AUTO,
            streamInfo = StreamInfo(
                url = "https://example.com/vod/movie_4k.mp4",
                title = "Awesome Movie 4K"
            )
        )

        assertThat(policy.label).isEqualTo("auto-uhd-vod-capped")
        assertThat(policy.minBufferMs).isEqualTo(10_000)
        assertThat(policy.maxBufferMs).isEqualTo(25_000)
        assertThat(policy.playbackBufferMs).isEqualTo(1_000)
        assertThat(policy.rebufferMs).isEqualTo(2_000)
        assertThat(policy.targetBufferBytes).isEqualTo(16 * 1024 * 1024)
    }
}
