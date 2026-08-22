package com.kaynanamtv.data.manager

import com.kaynanamtv.domain.manager.PlaybackContentionManager
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

@Singleton
class DefaultPlaybackContentionManager @Inject constructor() : PlaybackContentionManager {
    private val _isPlaybackActive = MutableStateFlow(false)
    override val isPlaybackActive: StateFlow<Boolean> = _isPlaybackActive.asStateFlow()

    private val _activePlaybackCount = MutableStateFlow(0)
    override val activePlaybackCount: StateFlow<Int> = _activePlaybackCount.asStateFlow()

    override fun setPlaybackActive(active: Boolean, instanceCount: Int) {
        val normalizedCount = instanceCount.coerceAtLeast(0)
        val normalizedActive = active || normalizedCount > 0
        _activePlaybackCount.value = if (normalizedActive) normalizedCount.coerceAtLeast(1) else 0
        _isPlaybackActive.value = normalizedActive
    }

    override fun shouldDeferBackgroundWork(): Boolean = _isPlaybackActive.value

    override suspend fun awaitPlaybackIdle() {
        if (!_isPlaybackActive.value) return
        _isPlaybackActive.first { !it }
    }
}
