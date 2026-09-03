package com.kaynanamtv.player

import android.content.Context
import android.view.SurfaceView
import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class VlcPlayerEngineTest {

    private val context: Context = mock()
    private val appContext: Context = mock()

    @Before
    fun setup() {
        whenever(context.applicationContext).thenReturn(appContext)
    }

    @Test
    fun `release is idempotent and does not throw when called multiple times`() {
        val engine = VlcPlayerEngine(context)

        // Initial state before play
        assertThat(engine.isPlaying.value).isFalse()
        assertThat(engine.playbackState.value).isEqualTo(PlaybackState.IDLE)

        // First release
        engine.release()
        assertThat(engine.isPlaying.value).isFalse()
        assertThat(engine.playbackState.value).isEqualTo(PlaybackState.IDLE)

        // Second release must not throw
        engine.release()
        assertThat(engine.isPlaying.value).isFalse()
        assertThat(engine.playbackState.value).isEqualTo(PlaybackState.IDLE)
    }

    @Test
    fun `clearRenderBinding and releaseRenderView reset attached view reference safely`() {
        val engine = VlcPlayerEngine(context)
        val surfaceView: SurfaceView = mock()

        engine.clearRenderBinding()
        engine.releaseRenderView(surfaceView)

        engine.release()
    }
}
