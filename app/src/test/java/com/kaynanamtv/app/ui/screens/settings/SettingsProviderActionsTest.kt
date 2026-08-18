package com.kaynanamtv.app.ui.screens.settings

import com.google.common.truth.Truth.assertThat
import com.kaynanamtv.app.tv.LauncherRecommendationsManager
import com.kaynanamtv.app.tv.WatchNextManager
import com.kaynanamtv.app.tvinput.TvInputChannelSyncManager
import com.kaynanamtv.data.preferences.PreferencesRepository
import com.kaynanamtv.data.sync.SyncManager
import com.kaynanamtv.domain.model.ActiveLiveSource
import com.kaynanamtv.domain.model.Provider
import com.kaynanamtv.domain.model.ProviderType
import com.kaynanamtv.domain.model.Result
import com.kaynanamtv.domain.repository.CombinedM3uRepository
import com.kaynanamtv.domain.repository.ProviderRepository
import com.kaynanamtv.domain.repository.SyncMetadataRepository
import com.kaynanamtv.domain.usecase.SyncProvider
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import org.mockito.kotlin.any
import org.mockito.kotlin.doThrow
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.never
import org.mockito.kotlin.verify
import org.mockito.kotlin.whenever

@OptIn(ExperimentalCoroutinesApi::class)
class SettingsProviderActionsTest {

    private val providerRepository: ProviderRepository = mock()
    private val combinedM3uRepository: CombinedM3uRepository = mock()
    private val preferencesRepository: PreferencesRepository = mock()
    private val syncProvider: SyncProvider = mock()
    private val syncManager: SyncManager = mock()
    private val syncMetadataRepository: SyncMetadataRepository = mock()
    private val watchNextManager: WatchNextManager = mock()
    private val launcherRecommendationsManager: LauncherRecommendationsManager = mock()
    private val tvInputChannelSyncManager: TvInputChannelSyncManager = mock()
    private val uiState = MutableStateFlow(SettingsUiState())

    private val actions = SettingsProviderActions(
        providerRepository = providerRepository,
        combinedM3uRepository = combinedM3uRepository,
        preferencesRepository = preferencesRepository,
        syncProvider = syncProvider,
        syncManager = syncManager,
        syncMetadataRepository = syncMetadataRepository,
        watchNextManager = watchNextManager,
        launcherRecommendationsManager = launcherRecommendationsManager,
        tvInputChannelSyncManager = tvInputChannelSyncManager,
        uiState = uiState
    )

    @Test
    fun setActiveProvider_refreshesProviderScopedTvSurfaces() = runTest(StandardTestDispatcher()) {
        val provider = Provider(
            id = 7L,
            name = "Provider Seven",
            type = ProviderType.M3U,
            serverUrl = "https://example.com",
            lastSyncedAt = System.currentTimeMillis()
        )
        whenever(providerRepository.getProvider(7L)).thenReturn(provider)

        actions.setActiveProvider(this, 7L)
        advanceUntilIdle()

        verify(preferencesRepository).setLastActiveProviderId(7L)
        verify(combinedM3uRepository).setActiveLiveSource(ActiveLiveSource.ProviderSource(7L))
        verify(providerRepository).setActiveProvider(7L)
        verify(watchNextManager).refreshWatchNext()
        verify(launcherRecommendationsManager).refreshRecommendations(force = true)
        verify(tvInputChannelSyncManager).refreshTvInputCatalog()
        verify(syncProvider, never()).invoke(any(), any())
    }

    @Test
    fun deleteProvider_refreshesProviderScopedTvSurfaces() = runTest(StandardTestDispatcher()) {
        whenever(providerRepository.deleteProvider(eq(7L), any())).thenReturn(Result.success(Unit))

        actions.deleteProvider(this, 7L)
        advanceUntilIdle()

        verify(providerRepository).deleteProvider(eq(7L), any())
        verify(watchNextManager).refreshWatchNext()
        verify(launcherRecommendationsManager).refreshRecommendations(force = true)
        verify(tvInputChannelSyncManager).refreshTvInputCatalog()
        assertThat(uiState.value.userMessage).isEqualTo("Sağlayıcı silindi")
    }

    @Test
    fun deleteProvider_stillCompletesSuccessWhenFollowUpRefreshFails() = runTest(StandardTestDispatcher()) {
        whenever(providerRepository.deleteProvider(eq(7L), any())).thenReturn(Result.success(Unit))
        doThrow(IllegalStateException("refresh boom")).whenever(launcherRecommendationsManager)
            .refreshRecommendations(force = true)
        var onSuccessCalled = false

        actions.deleteProvider(this, 7L, onSuccess = { onSuccessCalled = true })
        advanceUntilIdle()

        verify(providerRepository).deleteProvider(eq(7L), any())
        verify(watchNextManager).refreshWatchNext()
        verify(launcherRecommendationsManager).refreshRecommendations(force = true)
        verify(tvInputChannelSyncManager).refreshTvInputCatalog()
        assertThat(onSuccessCalled).isTrue()
        assertThat(uiState.value.isDeletingProvider).isFalse()
        assertThat(uiState.value.userMessage).isEqualTo("Sağlayıcı silindi")
    }
}
