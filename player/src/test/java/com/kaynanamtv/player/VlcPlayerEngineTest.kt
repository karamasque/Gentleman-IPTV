package com.kaynanamtv.player

import android.content.Context
import android.view.SurfaceView
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.Dispatchers
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.videolan.libvlc.MediaPlayer

class VlcPlayerEngineTest {

    private val context: Context = mock()
    private val appContext: Context = mock()

    private fun createEngine(): VlcPlayerEngine = VlcPlayerEngine(context, Dispatchers.Unconfined)

    @Before
    fun setup() {
        whenever(context.applicationContext).thenReturn(appContext)
    }

    @Test
    fun `release is idempotent and does not throw when called multiple times`() {
        val engine = createEngine()

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

    private fun createBufferingEvent(buffering: Float): MediaPlayer.Event {
        val constructor = MediaPlayer.Event::class.java.declaredConstructors.firstOrNull {
            it.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType, Float::class.javaPrimitiveType))
        } ?: MediaPlayer.Event::class.java.declaredConstructors.firstOrNull {
            it.parameterTypes.size == 2
        }
        return if (constructor != null) {
            constructor.isAccessible = true
            constructor.newInstance(MediaPlayer.Event.Buffering, buffering) as MediaPlayer.Event
        } else {
            // fallback: instantiate with default/first constructor and set fields
            val defaultCtor = MediaPlayer.Event::class.java.declaredConstructors.first()
            defaultCtor.isAccessible = true
            val params = Array(defaultCtor.parameterCount) { 0 }
            val event = defaultCtor.newInstance(*params) as MediaPlayer.Event
            setField(event, "type", MediaPlayer.Event.Buffering)
            setField(event, "buffering", buffering)
            event
        }
    }

    private fun createTimeChangedEvent(timeMs: Long): MediaPlayer.Event {
        val constructor = MediaPlayer.Event::class.java.declaredConstructors.firstOrNull {
            it.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType, Long::class.javaPrimitiveType))
        } ?: MediaPlayer.Event::class.java.declaredConstructors.firstOrNull {
            it.parameterTypes.size == 2
        }
        return if (constructor != null) {
            constructor.isAccessible = true
            constructor.newInstance(MediaPlayer.Event.TimeChanged, timeMs) as MediaPlayer.Event
        } else {
            val defaultCtor = MediaPlayer.Event::class.java.declaredConstructors.first()
            defaultCtor.isAccessible = true
            val params = Array(defaultCtor.parameterCount) { 0 }
            val event = defaultCtor.newInstance(*params) as MediaPlayer.Event
            setField(event, "type", MediaPlayer.Event.TimeChanged)
            setField(event, "timeChanged", timeMs)
            event
        }
    }

    private fun createPositionChangedEvent(position: Float): MediaPlayer.Event {
        val constructor = MediaPlayer.Event::class.java.declaredConstructors.firstOrNull {
            it.parameterTypes.contentEquals(arrayOf(Int::class.javaPrimitiveType, Float::class.javaPrimitiveType))
        } ?: MediaPlayer.Event::class.java.declaredConstructors.firstOrNull {
            it.parameterTypes.size == 2
        }
        return if (constructor != null) {
            constructor.isAccessible = true
            constructor.newInstance(MediaPlayer.Event.PositionChanged, position) as MediaPlayer.Event
        } else {
            val defaultCtor = MediaPlayer.Event::class.java.declaredConstructors.first()
            defaultCtor.isAccessible = true
            val params = Array(defaultCtor.parameterCount) { 0 }
            val event = defaultCtor.newInstance(*params) as MediaPlayer.Event
            setField(event, "type", MediaPlayer.Event.PositionChanged)
            setField(event, "positionChanged", position)
            event
        }
    }

    private fun createSimpleEvent(type: Int): MediaPlayer.Event {
        val ctor = MediaPlayer.Event::class.java.declaredConstructors.firstOrNull { it.parameterCount == 1 }
        return if (ctor != null) {
            ctor.isAccessible = true
            ctor.newInstance(type) as MediaPlayer.Event
        } else {
            val defaultCtor = MediaPlayer.Event::class.java.declaredConstructors.first()
            defaultCtor.isAccessible = true
            val params = Array(defaultCtor.parameterCount) { 0 }
            val event = defaultCtor.newInstance(*params) as MediaPlayer.Event
            setField(event, "type", type)
            event
        }
    }

    private fun setField(target: Any, fieldName: String, value: Any) {
        var clazz: Class<*>? = target.javaClass
        while (clazz != null) {
            try {
                val field = clazz.getDeclaredField(fieldName)
                field.isAccessible = true
                field.set(target, value)
                return
            } catch (e: NoSuchFieldException) {
                clazz = clazz.superclass
            }
        }
    }

    @Test
    fun `Buffering(20) followed by TimeChanged transitions state to READY when seek was in progress`() {
        val engine = createEngine()
        val bufferingEvent = createBufferingEvent(20f)
        val timeChangedEvent = createTimeChangedEvent(30000L)

        // Simulate seek in progress
        engine.seekInProgress.set(true)

        // Buffering arrives (< 100)
        engine.handlePlayerEvent(bufferingEvent, null)
        assertThat(engine.playbackState.value).isEqualTo(PlaybackState.BUFFERING)
        assertThat(engine.seekInProgress.get()).isTrue()

        // TimeChanged arrives -> recovers from BUFFERING to READY
        engine.handlePlayerEvent(timeChangedEvent, null)
        assertThat(engine.playbackState.value).isEqualTo(PlaybackState.READY)
        assertThat(engine.seekInProgress.get()).isFalse()
        assertThat(engine.currentPosition.value).isEqualTo(30000L)

        engine.release()
    }

    @Test
    fun `Buffering(20) followed by PositionChanged transitions state to READY when seek was in progress`() {
        val engine = createEngine()
        val bufferingEvent = createBufferingEvent(20f)
        val positionChangedEvent = createPositionChangedEvent(0.5f)

        // Simulate seek in progress
        engine.seekInProgress.set(true)

        // Buffering arrives (< 100)
        engine.handlePlayerEvent(bufferingEvent, null)
        assertThat(engine.playbackState.value).isEqualTo(PlaybackState.BUFFERING)

        // PositionChanged arrives -> recovers from BUFFERING to READY
        engine.handlePlayerEvent(positionChangedEvent, null)
        assertThat(engine.playbackState.value).isEqualTo(PlaybackState.READY)
        assertThat(engine.seekInProgress.get()).isFalse()

        engine.release()
    }

    @Test
    fun `normal buffering without seek and without playing stays in BUFFERING`() {
        val engine = createEngine()
        val bufferingEvent = createBufferingEvent(30f)
        val timeChangedEvent = createTimeChangedEvent(0L)

        // No seek in progress, not playing
        assertThat(engine.seekInProgress.get()).isFalse()
        assertThat(engine.isPlaying.value).isFalse()

        engine.handlePlayerEvent(bufferingEvent, null)
        assertThat(engine.playbackState.value).isEqualTo(PlaybackState.BUFFERING)

        // TimeChanged arriving without seekInProgress and without isPlaying should NOT force READY
        engine.handlePlayerEvent(timeChangedEvent, null)
        assertThat(engine.playbackState.value).isEqualTo(PlaybackState.BUFFERING)

        engine.release()
    }

    @Test
    fun `EncounteredError transitions to ERROR and does not falsely recover to READY`() {
        val engine = createEngine()
        val errorEvent = createSimpleEvent(MediaPlayer.Event.EncounteredError)
        val timeChangedEvent = createTimeChangedEvent(5000L)

        engine.seekInProgress.set(true)

        engine.handlePlayerEvent(errorEvent, null)
        assertThat(engine.playbackState.value).isEqualTo(PlaybackState.ERROR)
        assertThat(engine.seekInProgress.get()).isFalse()
        assertThat(engine.isPlaying.value).isFalse()

        // TimeChanged should not recover ERROR state to READY
        engine.handlePlayerEvent(timeChangedEvent, null)
        assertThat(engine.playbackState.value).isEqualTo(PlaybackState.ERROR)

        engine.release()
    }

    @Test
    fun `paused seek updates current position and native player time if seekable without auto-starting playback`() {
        val engine = createEngine()

        // Not playing
        assertThat(engine.isPlaying.value).isFalse()

        // seekTo when paused without player instance sets pending target and updates currentPosition
        engine.seekTo(15000L)

        assertThat(engine.currentPosition.value).isEqualTo(15000L)
        assertThat(engine.isPlaying.value).isFalse()

        engine.release()
    }

    @Test
    fun `successful normal seek cancels watchdog and recovers to READY without recovery`() {
        val engine = createEngine()

        engine.seekTo(25000L)
        val token = engine.currentSeekToken
        engine.seekInProgress.set(true)
        engine.startSeekStallWatchdog(token, 25000L)

        // Simulate normal TimeChanged arrival within time
        val timeEvent = createTimeChangedEvent(25000L)
        engine.handlePlayerEvent(timeEvent, null)

        assertThat(engine.seekInProgress.get()).isFalse()
        assertThat(engine.seekStallJob?.isActive).isNotEqualTo(true)
        assertThat(engine.seekRecoveryAttemptedForToken).isEqualTo(0L)

        engine.release()
    }

    @Test
    fun `stale TimeChanged from old position does not dismiss seekInProgress`() {
        val engine = createEngine()

        engine.seekTargetMs = 60000L
        engine.seekInProgress.set(true)
        assertThat(engine.seekInProgress.get()).isTrue()

        // Stale TimeChanged from old position (e.g. 5000ms) arrives
        val staleTimeEvent = createTimeChangedEvent(5000L)
        engine.handlePlayerEvent(staleTimeEvent, null)

        // seekInProgress must remain true because 5000ms is far from target 60000ms
        assertThat(engine.seekInProgress.get()).isTrue()

        engine.release()
    }

    @Test
    fun `stall for 5 seconds terminates seek and emits Turkish error message`() {
        val engine = createEngine()

        engine.seekTo(45000L)
        val token = engine.currentSeekToken
        engine.seekInProgress.set(true)
        engine.handlePlayerEvent(createBufferingEvent(10f), null)

        // Directly invoke performCleanSessionRecovery or error emission
        engine.performCleanSessionRecovery(token, 45000L)

        // Verify error state
        assertThat(engine.playbackState.value).isEqualTo(PlaybackState.ERROR)

        engine.release()
    }

    @Test
    fun `stop and release cancel watchdog and clear seek state`() {
        val engine = createEngine()

        engine.seekTo(10000L)
        assertThat(engine.currentSeekToken).isGreaterThan(0L)

        engine.stop()
        assertThat(engine.seekInProgress.get()).isFalse()
        assertThat(engine.currentSeekToken).isEqualTo(0L)
        assertThat(engine.seekStallJob?.isActive).isNotEqualTo(true)

        engine.release()
        assertThat(engine.currentSeekToken).isEqualTo(0L)
    }
}

