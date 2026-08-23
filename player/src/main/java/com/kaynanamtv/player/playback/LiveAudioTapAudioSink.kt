package com.kaynanamtv.player.playback

import androidx.media3.common.C
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.audio.AudioSink
import com.kaynanamtv.player.LiveAudioPcmBuffer
import com.kaynanamtv.player.LiveAudioTap
import java.nio.ByteBuffer

@UnstableApi
internal class LiveAudioTapAudioSink(
    private val delegate: AudioSink,
    private val tapProvider: () -> LiveAudioTap?
) : AudioSink by delegate {
    private var sampleRate: Int = Format.NO_VALUE
    private var channelCount: Int = Format.NO_VALUE
    private var encoding: Int = C.ENCODING_INVALID

    override fun configure(inputFormat: Format, specifiedBufferSize: Int, outputChannels: IntArray?) {
        sampleRate = inputFormat.sampleRate
        channelCount = inputFormat.channelCount
        encoding = inputFormat.pcmEncoding
        delegate.configure(inputFormat, specifiedBufferSize, outputChannels)
    }

    override fun handleBuffer(
        buffer: ByteBuffer,
        presentationTimeUs: Long,
        encodedAccessUnitCount: Int
    ): Boolean {
        val tap = tapProvider()
        if (tap == null) {
            return delegate.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        }
        val startPosition = buffer.position()
        val copySource = buffer.asReadOnlyBuffer()
        val handled = delegate.handleBuffer(buffer, presentationTimeUs, encodedAccessUnitCount)
        copyConsumedForTap(tap, copySource, startPosition, buffer.position(), presentationTimeUs)
        return handled
    }

    private fun copyConsumedForTap(
        tap: LiveAudioTap,
        buffer: ByteBuffer,
        startPosition: Int,
        endPosition: Int,
        presentationTimeUs: Long
    ) {
        if (encoding != C.ENCODING_PCM_16BIT || sampleRate <= 0 || channelCount <= 0 || endPosition <= startPosition) {
            return
        }
        val adjustedPresentationTimeUs = presentationTimeUs + consumedOffsetUs(startPosition)
        val duplicate = buffer.duplicate().apply {
            position(startPosition)
            limit(endPosition)
        }
        val data = ByteArray(duplicate.remaining())
        duplicate.get(data)
        tap.onPcmAudio(
            LiveAudioPcmBuffer(
                data = data,
                presentationTimeUs = adjustedPresentationTimeUs,
                sampleRate = sampleRate,
                channelCount = channelCount,
                encoding = encoding
            )
        )
    }

    private fun consumedOffsetUs(bytePosition: Int): Long {
        val frameSize = channelCount * 2
        if (frameSize <= 0 || sampleRate <= 0) return 0L
        val frames = bytePosition / frameSize
        return frames * 1_000_000L / sampleRate
    }
}
