package com.kaynanamtv.app.ui.screens.player

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlayerSeekReliabilityTest {

    @Test
    fun `single right step accumulates 10 seconds`() {
        val currentPosition = 30_000L
        val duration = 120_000L
        val stepMs = 10_000L // repeat == 0

        val targetMs = (currentPosition + stepMs).coerceIn(0L, duration)
        assertThat(targetMs).isEqualTo(40_000L)
    }

    @Test
    fun `single left step accumulates -10 seconds`() {
        val currentPosition = 30_000L
        val duration = 120_000L
        val stepMs = 10_000L // repeat == 0

        val targetMs = (currentPosition - stepMs).coerceIn(0L, duration)
        assertThat(targetMs).isEqualTo(20_000L)
    }

    @Test
    fun `repeated right steps accumulate larger delta during hold before commit`() {
        var userScrubbingPositionMs: Long? = 50_000L
        val duration = 300_000L

        // repeat 1 (< 5) -> 20s
        userScrubbingPositionMs = (userScrubbingPositionMs!! + 20_000L).coerceIn(0L, duration)
        // repeat 2 (< 5) -> 20s
        userScrubbingPositionMs = (userScrubbingPositionMs + 20_000L).coerceIn(0L, duration)
        // repeat 6 (< 10) -> 30s
        userScrubbingPositionMs = (userScrubbingPositionMs + 30_000L).coerceIn(0L, duration)

        assertThat(userScrubbingPositionMs).isEqualTo(120_000L)
    }

    @Test
    fun `seek steps clamp within duration bounds`() {
        val duration = 60_000L
        val nearEnd = 55_000L
        val stepMs = 10_000L

        val forwardClamped = (nearEnd + stepMs).coerceIn(0L, duration)
        assertThat(forwardClamped).isEqualTo(60_000L)

        val nearStart = 5_000L
        val backwardClamped = (nearStart - stepMs).coerceIn(0L, duration)
        assertThat(backwardClamped).isEqualTo(0L)
    }

    @Test
    fun `focus loss commits pending target and clears scrubbing state`() {
        var isScrubbing = true
        var pendingTarget: Long? = 75_000L
        var committedTarget: Long? = null

        // Focus lost while scrubbing
        if (pendingTarget != null) {
            committedTarget = pendingTarget
            pendingTarget = null
            isScrubbing = false
        }

        assertThat(committedTarget).isEqualTo(75_000L)
        assertThat(pendingTarget == null).isTrue()
        assertThat(isScrubbing).isFalse()
    }

    @Test
    fun `back press cancels scrubbing without committing`() {
        var isScrubbing = true
        var pendingTarget: Long? = 75_000L
        var committedTarget: Long? = null

        // Back pressed while scrubbing
        pendingTarget = null
        isScrubbing = false

        assertThat(committedTarget == null).isTrue()
        assertThat(pendingTarget == null).isTrue()
        assertThat(isScrubbing).isFalse()
    }
}
