package com.kaynanamtv.data.repository

import com.google.common.truth.Truth.assertThat
import com.kaynanamtv.data.local.entity.ChannelBrowseEntity
import com.kaynanamtv.domain.model.ChannelLogoSourcePolicy
import org.junit.Test

class ChannelLogoResolutionTest {

    private fun resolveLogo(entity: ChannelBrowseEntity): String? {
        val supplierLogo = entity.logoUrl?.takeIf { it.isNotBlank() }
        val epgLogo = entity.epgIconUrl?.takeIf { it.isNotBlank() }
        return when (entity.channelLogoSourcePolicy) {
            ChannelLogoSourcePolicy.SUPPLIER_PREFERRED -> supplierLogo ?: epgLogo
            ChannelLogoSourcePolicy.EPG_PREFERRED -> epgLogo ?: supplierLogo
            ChannelLogoSourcePolicy.SUPPLIER_ONLY -> supplierLogo
            ChannelLogoSourcePolicy.EPG_ONLY -> epgLogo
        }
    }

    @Test
    fun `provider logo is preferred and never replaced with null`() {
        val entity = ChannelBrowseEntity(
            id = 1,
            name = "TR: ATV HD",
            logoUrl = "http://provider.example.com/logos/atv.png",
            epgIconUrl = "http://epg.example.com/icons/atv_epg.png",
            channelLogoSourcePolicy = ChannelLogoSourcePolicy.SUPPLIER_PREFERRED
        )
        assertThat(resolveLogo(entity)).isEqualTo("http://provider.example.com/logos/atv.png")
    }

    @Test
    fun `when provider logo is null epg logo is used as fallback in SUPPLIER_PREFERRED`() {
        val entity = ChannelBrowseEntity(
            id = 2,
            name = "TR: STAR TV",
            logoUrl = null,
            epgIconUrl = "http://epg.example.com/icons/star_epg.png",
            channelLogoSourcePolicy = ChannelLogoSourcePolicy.SUPPLIER_PREFERRED
        )
        assertThat(resolveLogo(entity)).isEqualTo("http://epg.example.com/icons/star_epg.png")
    }

    @Test
    fun `when both provider logo and epg logo are missing resolved is null`() {
        val entity = ChannelBrowseEntity(
            id = 3,
            name = "TR: UNKNOWN",
            logoUrl = "   ",
            epgIconUrl = null,
            channelLogoSourcePolicy = ChannelLogoSourcePolicy.SUPPLIER_PREFERRED
        )
        assertThat(resolveLogo(entity)).isNull()
    }

    @Test
    fun `null or blank EPG icon cannot overwrite provider logo`() {
        val entityWithNullEpg = ChannelBrowseEntity(
            id = 4,
            name = "TR: SHOW TV",
            logoUrl = "http://provider.example.com/logos/show.png",
            epgIconUrl = null,
            channelLogoSourcePolicy = ChannelLogoSourcePolicy.SUPPLIER_PREFERRED
        )
        assertThat(resolveLogo(entityWithNullEpg)).isEqualTo("http://provider.example.com/logos/show.png")

        val entityWithBlankEpg = ChannelBrowseEntity(
            id = 5,
            name = "TR: SHOW TV",
            logoUrl = "http://provider.example.com/logos/show.png",
            epgIconUrl = "   ",
            channelLogoSourcePolicy = ChannelLogoSourcePolicy.SUPPLIER_PREFERRED
        )
        assertThat(resolveLogo(entityWithBlankEpg)).isEqualTo("http://provider.example.com/logos/show.png")
    }

    @Test
    fun `SUPPLIER_ONLY policy ignores EPG icon even when provider logo is missing`() {
        val entity = ChannelBrowseEntity(
            id = 6,
            name = "TR: KANAL D",
            logoUrl = null,
            epgIconUrl = "http://epg.example.com/icons/kanald.png",
            channelLogoSourcePolicy = ChannelLogoSourcePolicy.SUPPLIER_ONLY
        )
        assertThat(resolveLogo(entity)).isNull()
    }

    @Test
    fun `EPG_PREFERRED policy prioritizes non-blank EPG icon over provider logo`() {
        val entity = ChannelBrowseEntity(
            id = 7,
            name = "TR: FOX",
            logoUrl = "http://provider.example.com/logos/fox.png",
            epgIconUrl = "http://epg.example.com/icons/fox_epg.png",
            channelLogoSourcePolicy = ChannelLogoSourcePolicy.EPG_PREFERRED
        )
        assertThat(resolveLogo(entity)).isEqualTo("http://epg.example.com/icons/fox_epg.png")
    }
}
