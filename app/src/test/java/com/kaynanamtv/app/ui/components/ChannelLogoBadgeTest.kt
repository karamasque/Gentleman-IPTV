package com.kaynanamtv.app.ui.components

import coil3.request.ImageRequest
import com.google.common.truth.Truth.assertThat
import com.kaynanamtv.domain.model.Channel
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

@RunWith(RobolectricTestRunner::class)
class ChannelLogoBadgeTest {

    @Test
    fun `channel clean name helper extracts readable channel name stripping numbers, tags and quality`() {
        val initialsMethod = Class.forName("com.kaynanamtv.app.ui.components.ChannelLogoKt")
            .getDeclaredMethod("extractCleanChannelName", String::class.java)
        initialsMethod.isAccessible = true

        val showTv = initialsMethod.invoke(null, "12 TR: SHOW TV FHD") as String
        assertThat(showTv).isEqualTo("SHOW TV")

        val trt1 = initialsMethod.invoke(null, "03 TR: TRT 1 FHD") as String
        assertThat(trt1).isEqualTo("TRT 1")

        val atv = initialsMethod.invoke(null, "ATV HD") as String
        assertThat(atv).isEqualTo("ATV")

        val singleWord = initialsMethod.invoke(null, "CNN") as String
        assertThat(singleWord).isEqualTo("CNN")

        val empty = initialsMethod.invoke(null, "   ") as String
        assertThat(empty).isEqualTo("TV")

        val oneLetter = initialsMethod.invoke(null, "N") as String
        assertThat(oneLetter).isEqualTo("N")

        // Test decorative emojis and country prefix stripping
        val ulusal = initialsMethod.invoke(null, "⚜️⚜️ ULUSAL ⚜️⚜️") as String
        assertThat(ulusal).isEqualTo("ULUSAL")

        val polis = initialsMethod.invoke(null, "TR: POLİS KAMERASI HD") as String
        assertThat(polis).isEqualTo("POLİS KAMERASI")

        val tgrt = initialsMethod.invoke(null, "TR: TGRT EU HD") as String
        assertThat(tgrt).isEqualTo("TGRT EU")
    }

    @Test
    fun `same channel name from two different providers retains independent channel models and logos`() {
        val channelProviderA = Channel(
            id = 101L,
            name = "Eurosport 1",
            logoUrl = "https://provider-a.example/logos/eurosport.png",
            providerId = 1L,
            streamUrl = "http://provider-a.example/stream1.m3u8"
        )
        val channelProviderB = Channel(
            id = 201L,
            name = "Eurosport 1",
            logoUrl = "https://provider-b.example/logos/eurosport_alt.png",
            providerId = 2L,
            streamUrl = "http://provider-b.example/stream2.m3u8"
        )

        assertThat(channelProviderA.name).isEqualTo(channelProviderB.name)
        assertThat(channelProviderA.logoUrl).isNotEqualTo(channelProviderB.logoUrl)
        assertThat(channelProviderA.providerId).isNotEqualTo(channelProviderB.providerId)
    }

    @Test
    fun `ImageRequest with cache keys is correctly constructed for valid URL`() {
        val context = RuntimeEnvironment.getApplication()
        val validUrl = "https://cdn.example.com/channel_logo.png"

        val request = ImageRequest.Builder(context)
            .data(validUrl)
            .memoryCacheKey(validUrl)
            .diskCacheKey(validUrl)
            .build()

        assertThat(request.data).isEqualTo(validUrl)
        assertThat(request.memoryCacheKey).isNotNull()
        assertThat(request.diskCacheKey).isEqualTo(validUrl)
    }
}
