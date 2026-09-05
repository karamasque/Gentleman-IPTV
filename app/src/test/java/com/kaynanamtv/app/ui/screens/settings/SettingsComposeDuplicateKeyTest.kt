package com.kaynanamtv.app.ui.screens.settings

import com.kaynanamtv.domain.model.Category
import com.kaynanamtv.domain.model.Channel
import com.kaynanamtv.domain.model.ContentType
import com.kaynanamtv.domain.model.PlayerEnginePreference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsComposeDuplicateKeyTest {

    @Test
    fun homeCategories_withDuplicateOrZeroIds_generateUniqueKeys() {
        val categories = listOf(
            Category(id = 0L, name = "Tüm Kanallar", type = ContentType.LIVE),
            Category(id = 0L, name = "Favoriler", type = ContentType.LIVE),
            Category(id = 0L, name = "Tüm Filmler", type = ContentType.MOVIE),
            Category(id = 100L, name = "Ulusal", type = ContentType.LIVE)
        )

        val keys = categories.indices.map { index ->
            val category = categories.getOrNull(index)
            "home_cat_${category?.id}_${category?.name}_${category?.type}_${index}"
        }

        assertEquals(4, keys.distinct().size)
    }

    @Test
    fun homeChannels_withDuplicateOrZeroIds_generateUniqueKeys() {
        val channels = listOf(
            Channel(id = 0L, name = "TRT 1", providerId = 1L, streamId = 101L, streamUrl = "http://test/1"),
            Channel(id = 0L, name = "ATV", providerId = 1L, streamId = 102L, streamUrl = "http://test/2"),
            Channel(id = 0L, name = "TRT 1", providerId = 2L, streamId = 101L, streamUrl = "http://test/3"),
            Channel(id = 50L, name = "Kanal D", providerId = 1L, streamId = 103L, streamUrl = "http://test/4")
        )

        val keys = channels.indices.map { index ->
            val channel = channels.getOrNull(index)
            "home_ch_${channel?.providerId}_${channel?.id}_${channel?.streamId}_${channel?.name}_${index}"
        }

        assertEquals(4, keys.distinct().size)
    }

    @Test
    fun overlayChannels_withZeroId_generateUniqueIndexedKeys() {
        val channels = listOf(
            Channel(id = 0L, name = "Ch 1", providerId = 1L, streamId = 1L, streamUrl = ""),
            Channel(id = 0L, name = "Ch 2", providerId = 1L, streamId = 2L, streamUrl = "")
        )

        val keys = channels.indices.map { index ->
            val ch = channels.getOrNull(index)
            "aux_ch_${ch?.providerId}_${ch?.id}_${ch?.streamId}_${index}"
        }

        assertEquals(2, keys.distinct().size)
    }

    @Test
    fun categoryRow_itemsWithZeroIds_generateUniqueKeys() {
        val movies = listOf("Movie A", "Movie A", "Movie B")
        val keys = movies.indices.map { index ->
            val item = movies.getOrNull(index)
            "cat_row_Favorites_${item}_${index}"
        }
        assertEquals(3, keys.distinct().size)
    }

    @Test
    fun playerEngineOptions_allPreferencesAreDistinct() {
        val preferences = listOf(
            PlayerEnginePreference.AUTO,
            PlayerEnginePreference.MEDIA3,
            PlayerEnginePreference.VLC,
            PlayerEnginePreference.EXTERNAL_VLC
        )

        val keys = preferences.map { it.name }
        assertEquals(4, keys.distinct().size)
        assertTrue(keys.contains("VLC"))
        assertTrue(keys.contains("MEDIA3"))
    }
}
