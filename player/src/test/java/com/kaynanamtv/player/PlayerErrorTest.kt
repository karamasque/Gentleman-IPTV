package com.kaynanamtv.player

import com.google.common.truth.Truth.assertThat
import java.io.IOException
import org.junit.Test

class PlayerErrorTest {

    @Test
    fun `509 becomes source error instead of auth refresh network error`() {
        val error = PlayerError.fromException(IOException("HTTP 509"))

        assertThat(error).isInstanceOf(PlayerError.SourceError::class.java)
        assertThat(error.message).isEqualTo(
            "Provider rejected playback, likely max connections or bandwidth limit (HTTP 509)."
        )
    }

    @Test
    fun `MediaCodecVideoRenderer 4K HEVC HDR error produces clean informative message`() {
        val rawMessage = "MediaCodecVideoRenderer error, index=0, format=Format(1, null, video/x-matroska, video/hevc, hvc1.2.4.H150.90, -1, und, [3840, 2160, -1.0, ColorInfo(BT2020, Unset color range, ST2084 PQ, false, NA, NA)], [-1, -1]), format_supported=YES"
        val error = PlayerError.fromException(androidx.media3.common.PlaybackException(rawMessage, null, androidx.media3.common.PlaybackException.ERROR_CODE_DECODING_FAILED))

        assertThat(error).isInstanceOf(PlayerError.DecoderError::class.java)
        assertThat(error.message).contains("Donanım video kod çözücü")
        assertThat(error.message).contains("4K HEVC HDR")
    }
}
