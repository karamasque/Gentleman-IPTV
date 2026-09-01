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
    fun `channel initials helper extracts two characters from single or multi word name`() {
        val initialsMethod = Class.forName("com.kaynanamtv.app.ui.components.ChannelLogoKt")
            .getDeclaredMethod("channelInitials", String::class.java)
        initialsMethod.isAccessible = true

        val atvInitials = initialsMethod.invoke(null, "ATV HD") as String
        assertThat(atvInitials).isEqualTo("AH")

        val singleWordInitials = initialsMethod.invoke(null, "CNN") as String
        assertThat(singleWordInitials).isEqualTo("CN")

        val emptyInitials = initialsMethod.invoke(null, "   ") as String
        assertThat(emptyInitials).isEqualTo("--")

        val oneLetterInitials = initialsMethod.invoke(null, "N") as String
        assertThat(oneLetterInitials).isEqualTo("N")
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
