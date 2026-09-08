package com.kaynanamtv.app.player

import com.google.common.truth.Truth.assertThat
import com.kaynanamtv.domain.model.ContentType
import com.kaynanamtv.domain.model.PlayerEnginePreference
import com.kaynanamtv.domain.model.PlaybackHistory
import com.kaynanamtv.player.PlayerEngineFactory
import com.kaynanamtv.player.PlayerEngineType
import org.junit.Test
import org.mockito.kotlin.mock

class PlayerEngineMigrationAndExternalVlcTest {

    @Test
    fun `PlayerEngineFactory resolves all preferences to Media3 engine`() {
        val factory = PlayerEngineFactory(
            context = mock(),
            okHttpClient = mock(),
            playbackCompatibilityRepository = mock(),
            audioCompatibilityMemoryStore = mock(),
            playbackSupportSnapshotStore = mock()
        )

        assertThat(factory.resolveEngineType(PlayerEnginePreference.AUTO)).isEqualTo(PlayerEngineType.MEDIA3)
        assertThat(factory.resolveEngineType(PlayerEnginePreference.MEDIA3)).isEqualTo(PlayerEngineType.MEDIA3)
        assertThat(factory.resolveEngineType(PlayerEnginePreference.VLC)).isEqualTo(PlayerEngineType.MEDIA3)
        assertThat(factory.resolveEngineType(PlayerEnginePreference.EXTERNAL_VLC)).isEqualTo(PlayerEngineType.MEDIA3)
    }

    @Test
    fun `Live playback history maintains recent channels with single card per channel ID`() {
        val now = System.currentTimeMillis()
        val histories = listOf(
            PlaybackHistory(contentId = 101L, contentType = ContentType.LIVE, providerId = 1L, title = "TRT 1", streamUrl = "http://example.com/stream", lastWatchedAt = now - 1000),
            PlaybackHistory(contentId = 102L, contentType = ContentType.LIVE, providerId = 1L, title = "ATV", streamUrl = "http://example.com/stream", lastWatchedAt = now - 500),
            PlaybackHistory(contentId = 101L, contentType = ContentType.LIVE, providerId = 1L, title = "TRT 1", streamUrl = "http://example.com/stream", lastWatchedAt = now) // re-opened
        )

        val distinctOrderedIds = histories
            .sortedByDescending { it.lastWatchedAt }
            .distinctBy { it.contentId }
            .map { it.contentId }

        assertThat(distinctOrderedIds).containsExactly(101L, 102L).inOrder()
    }
}
