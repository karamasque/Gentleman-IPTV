package com.kaynanamtv.domain.manager

import kotlinx.coroutines.flow.StateFlow

/**
 * Manages zero-contention coordination between active media playback (P0 priority)
 * and background operations (catalog sync, EPG sync/parsing, TMDB enrichment,
 * FTS indexing, and heavy database transactions).
 */
interface PlaybackContentionManager {
    /**
     * Whether any player instance is currently active (playing, ready, or buffering).
     */
    val isPlaybackActive: StateFlow<Boolean>

    /**
     * Number of concurrently active player instances.
     */
    val activePlaybackCount: StateFlow<Int>

    /**
     * Updates the playback activity state from player engine instances.
     */
    fun setPlaybackActive(active: Boolean, instanceCount: Int)

    /**
     * Fast non-blocking check whether background jobs should yield, defer, or pause.
     */
    fun shouldDeferBackgroundWork(): Boolean

    /**
     * Suspends the calling coroutine until all active playback sessions have ended.
     */
    suspend fun awaitPlaybackIdle()
}
