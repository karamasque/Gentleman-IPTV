package com.kaynanamtv.player

import android.content.Context
import android.content.pm.PackageManager
import com.google.common.truth.Truth.assertThat
import com.kaynanamtv.domain.model.PlayerEnginePreference
import org.junit.Before
import org.junit.Test
import org.mockito.kotlin.doReturn
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

class PlayerEngineFactoryTest {

    private lateinit var mockContext: Context
    private lateinit var mockPackageManager: PackageManager
    private lateinit var factory: PlayerEngineFactory

    @Before
    fun setUp() {
        mockContext = mock()
        mockPackageManager = mock()
        whenever(mockContext.packageManager).thenReturn(mockPackageManager)
        whenever(mockContext.applicationContext).thenReturn(mockContext)

        val mockResources = mock<android.content.res.Resources>()
        val mockConfig = mock<android.content.res.Configuration>()
        mockConfig.screenWidthDp = 1920
        whenever(mockResources.configuration).thenReturn(mockConfig)
        whenever(mockContext.resources).thenReturn(mockResources)

        factory = PlayerEngineFactory(
            context = mockContext,
            okHttpClient = mock(),
            playbackCompatibilityRepository = mock(),
            audioCompatibilityMemoryStore = mock(),
            playbackSupportSnapshotStore = mock()
        )
    }

    @Test
    fun autoMode_onTvDevice_resolvesToMedia3() {
        whenever(mockPackageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)).thenReturn(true)

        val resolved = factory.resolveEngineType(PlayerEnginePreference.AUTO)

        assertThat(resolved).isEqualTo(PlayerEngineType.MEDIA3)
    }

    @Test
    fun autoMode_onMobileDevice_resolvesToMedia3() {
        whenever(mockPackageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)).thenReturn(false)
        whenever(mockPackageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)).thenReturn(true)

        val resolved = factory.resolveEngineType(PlayerEnginePreference.AUTO)

        assertThat(resolved).isEqualTo(PlayerEngineType.MEDIA3)
    }

    @Test
    fun manualMode_media3_resolvesToMedia3_regardlessOfDevice() {
        whenever(mockPackageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)).thenReturn(true)

        val resolved = factory.resolveEngineType(PlayerEnginePreference.MEDIA3)

        assertThat(resolved).isEqualTo(PlayerEngineType.MEDIA3)
    }

    @Test
    fun manualMode_vlcPreference_stillResolvesToUnifiedMedia3() {
        whenever(mockPackageManager.hasSystemFeature(PackageManager.FEATURE_LEANBACK)).thenReturn(false)
        whenever(mockPackageManager.hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN)).thenReturn(true)

        val resolved = factory.resolveEngineType(PlayerEnginePreference.VLC)

        assertThat(resolved).isEqualTo(PlayerEngineType.MEDIA3)
    }
}
