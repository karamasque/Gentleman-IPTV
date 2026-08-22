package com.kaynanamtv.player.playback

import com.google.common.truth.Truth.assertThat
import com.kaynanamtv.player.PlaybackState
import org.junit.Test

class PlaybackStallRecoveryPolicyTest {
    @Test
    fun `ready stalls are recovered for live transport streams`() {
        assertThat(shouldRecoverReadyStalls(ResolvedStreamType.MPEG_TS_LIVE)).isTrue()
    }

    @Test
    fun `position advancing ready stalls are not recovered for live streams`() {
        assertThat(shouldRecoverPositionAdvancingReadyStalls(ResolvedStreamType.MPEG_TS_LIVE)).isFalse()
        assertThat(shouldRecoverPositionAdvancingReadyStalls(ResolvedStreamType.HLS)).isFalse()
        assertThat(shouldRecoverPositionAdvancingReadyStalls(ResolvedStreamType.PROGRESSIVE)).isTrue()
    }

    @Test
    fun `frame silent ready stalls are recovered for live streams`() {
        assertThat(shouldRecoverFrameSilentReadyStalls(ResolvedStreamType.MPEG_TS_LIVE)).isTrue()
        assertThat(shouldRecoverFrameSilentReadyStalls(ResolvedStreamType.HLS)).isTrue()
        assertThat(shouldRecoverFrameSilentReadyStalls(ResolvedStreamType.PROGRESSIVE)).isFalse()
    }

    @Test
    fun `live ready stalls reconnect the current stream`() {
        assertThat(
            shouldReconnectLiveStall(
                playbackState = PlaybackState.READY,
                resolvedStreamType = ResolvedStreamType.MPEG_TS_LIVE,
                recoveryAttempt = 1
            )
        ).isTrue()
    }

    @Test
    fun `live ready stalls stop reconnecting after first recovery attempt`() {
        assertThat(
            shouldReconnectLiveStall(
                playbackState = PlaybackState.READY,
                resolvedStreamType = ResolvedStreamType.MPEG_TS_LIVE,
                recoveryAttempt = 2
            )
        ).isFalse()
    }

    @Test
    fun `vod ready stalls do not reconnect as live streams`() {
        assertThat(
            shouldReconnectLiveStall(
                playbackState = PlaybackState.READY,
                resolvedStreamType = ResolvedStreamType.PROGRESSIVE,
                recoveryAttempt = 1
            )
        ).isFalse()
    }

    @Test
    fun `classifyPlaybackStall identifies behind live window for HLS`() {
        val category = classifyPlaybackStall(
            bufferedDurationMs = 0L,
            lastFrameAgoMs = 0L,
            lastBytesAgoMs = 0L,
            playbackState = PlaybackState.BUFFERING,
            lastError = androidx.media3.exoplayer.source.BehindLiveWindowException()
        )
        assertThat(category).isEqualTo(PlaybackStallCategory.HLS_LIVE_WINDOW)
    }

    @Test
    fun `classifyPlaybackStall identifies server no bytes starvation`() {
        val category = classifyPlaybackStall(
            bufferedDurationMs = 200L,
            lastFrameAgoMs = 1000L,
            lastBytesAgoMs = 4000L,
            playbackState = PlaybackState.BUFFERING,
            lastError = null
        )
        assertThat(category).isEqualTo(PlaybackStallCategory.SERVER_NO_BYTES)
    }

    @Test
    fun `classifyPlaybackStall identifies decoder stall when buffer is full but frames not rendering`() {
        val category = classifyPlaybackStall(
            bufferedDurationMs = 5000L,
            lastFrameAgoMs = 7000L,
            lastBytesAgoMs = 100L,
            playbackState = PlaybackState.READY,
            lastError = null
        )
        assertThat(category).isEqualTo(PlaybackStallCategory.DECODER_STALL)
    }
}
