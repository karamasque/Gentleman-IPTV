package com.kaynanamtv.app.ui.screens.series

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.google.common.truth.Truth.assertThat
import com.kaynanamtv.app.MainDispatcherRule
import com.kaynanamtv.app.cast.CastMediaRequestFactory
import com.kaynanamtv.app.cast.CastPlaybackCoordinator
import com.kaynanamtv.app.cast.CastStartResult
import com.kaynanamtv.app.plugins.KaynanamTVPluginManager
import com.kaynanamtv.data.preferences.PreferencesRepository
import com.kaynanamtv.domain.model.Episode
import com.kaynanamtv.domain.model.ExternalRatings
import com.kaynanamtv.domain.model.Provider
import com.kaynanamtv.domain.model.ProviderType
import com.kaynanamtv.domain.model.Result
import com.kaynanamtv.domain.model.Season
import com.kaynanamtv.domain.model.Series
import com.kaynanamtv.domain.repository.DownloadManager
import com.kaynanamtv.domain.repository.ExternalRatingsRepository
import com.kaynanamtv.domain.repository.FavoriteRepository
import com.kaynanamtv.domain.repository.PlaybackHistoryRepository
import com.kaynanamtv.domain.repository.ProviderRepository
import com.kaynanamtv.domain.repository.SeriesRepository
import com.kaynanamtv.domain.util.isPlaybackComplete
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.mockito.kotlin.any
import org.mockito.kotlin.anyOrNull
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever

@RunWith(RobolectricTestRunner::class)
class SeriesProgressUxTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `episode states distinction - unwatched, in-progress, completed`() {
        val durationSeconds = 1800 // 30 minutes = 1_800_000 ms

        val unwatched = episode(id = 1L, episodeNumber = 1, durationSeconds = durationSeconds, watchProgress = 0L)
        val inProgress = episode(id = 2L, episodeNumber = 2, durationSeconds = durationSeconds, watchProgress = 600_000L) // 10 mins
        val completed = episode(id = 3L, episodeNumber = 3, durationSeconds = durationSeconds, watchProgress = 1_750_000L) // > 95% (1710s)

        assertThat(isPlaybackComplete(unwatched.watchProgress, unwatched.durationSeconds.toLong() * 1000L)).isFalse()
        assertThat(unwatched.watchProgress > 5000L).isFalse()

        assertThat(isPlaybackComplete(inProgress.watchProgress, inProgress.durationSeconds.toLong() * 1000L)).isFalse()
        assertThat(inProgress.watchProgress > 5000L).isTrue()

        assertThat(isPlaybackComplete(completed.watchProgress, completed.durationSeconds.toLong() * 1000L)).isTrue()
    }

    @Test
    fun `season watched counts and full season indicator calculations`() {
        val durationSeconds = 1000
        val durMs = durationSeconds.toLong() * 1000L

        val ep1 = episode(id = 1L, seasonNumber = 1, episodeNumber = 1, durationSeconds = durationSeconds, watchProgress = 960_000L) // completed (>95%)
        val ep2 = episode(id = 2L, seasonNumber = 1, episodeNumber = 2, durationSeconds = durationSeconds, watchProgress = 960_000L) // completed (>95%)
        val ep3 = episode(id = 3L, seasonNumber = 1, episodeNumber = 3, durationSeconds = durationSeconds, watchProgress = 500_000L) // in progress
        val ep4 = episode(id = 4L, seasonNumber = 1, episodeNumber = 4, durationSeconds = durationSeconds, watchProgress = 0L) // unwatched

        val seasonPartial = Season(seasonNumber = 1, episodes = listOf(ep1, ep2, ep3, ep4))
        val watchedCountPartial = seasonPartial.episodes.count { isPlaybackComplete(it.watchProgress, durMs) }
        assertThat(watchedCountPartial).isEqualTo(2)
        assertThat(seasonPartial.episodes.size).isEqualTo(4)
        assertThat(watchedCountPartial == seasonPartial.episodes.size).isFalse()

        val seasonFull = Season(seasonNumber = 1, episodes = listOf(ep1, ep2))
        val watchedCountFull = seasonFull.episodes.count { isPlaybackComplete(it.watchProgress, durMs) }
        assertThat(watchedCountFull).isEqualTo(2)
        assertThat(seasonFull.episodes.size).isEqualTo(2)
        assertThat(watchedCountFull == seasonFull.episodes.size).isTrue()

        val seasonZero = Season(seasonNumber = 0, name = "Specials", episodes = listOf(ep1))
        val watchedCountZero = seasonZero.episodes.count { isPlaybackComplete(it.watchProgress, durMs) }
        assertThat(watchedCountZero).isEqualTo(1)
    }

    @Test
    fun `series resume selects in-progress episode when available`() = runBlocking {
        val durationSeconds = 1800
        val ep1 = episode(id = 1L, seasonNumber = 1, episodeNumber = 1, durationSeconds = durationSeconds, watchProgress = 1_750_000L, lastWatchedAt = 100L) // completed
        val ep2 = episode(id = 2L, seasonNumber = 1, episodeNumber = 2, durationSeconds = durationSeconds, watchProgress = 500_000L, lastWatchedAt = 200L) // in-progress
        val ep3 = episode(id = 3L, seasonNumber = 1, episodeNumber = 3, durationSeconds = durationSeconds, watchProgress = 0L, lastWatchedAt = 0L) // unwatched

        val series = series(episodes = listOf(ep1, ep2, ep3))
        val viewModel = createViewModel(series = series)

        try {
            val state = viewModel.uiState.value
            assertThat(state.resumeEpisode).isEqualTo(ep2)
            assertThat(state.isAllEpisodesCompleted).isFalse()
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun `series resume selects first unwatched episode when no in-progress episode`() = runBlocking {
        val durationSeconds = 1800
        val ep1 = episode(id = 1L, seasonNumber = 1, episodeNumber = 1, durationSeconds = durationSeconds, watchProgress = 1_750_000L, lastWatchedAt = 100L) // completed
        val ep2 = episode(id = 2L, seasonNumber = 1, episodeNumber = 2, durationSeconds = durationSeconds, watchProgress = 0L, lastWatchedAt = 0L) // unwatched
        val ep3 = episode(id = 3L, seasonNumber = 1, episodeNumber = 3, durationSeconds = durationSeconds, watchProgress = 0L, lastWatchedAt = 0L) // unwatched

        val series = series(episodes = listOf(ep1, ep2, ep3))
        val viewModel = createViewModel(series = series)

        try {
            val state = viewModel.uiState.value
            assertThat(state.resumeEpisode).isEqualTo(ep2)
            assertThat(state.isAllEpisodesCompleted).isFalse()
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun `series resume returns first ordered episode as restart candidate when all episodes completed`() = runBlocking {
        val durationSeconds = 1800
        val ep1 = episode(id = 1L, seasonNumber = 1, episodeNumber = 1, durationSeconds = durationSeconds, watchProgress = 1_750_000L, lastWatchedAt = 100L) // completed
        val ep2 = episode(id = 2L, seasonNumber = 1, episodeNumber = 2, durationSeconds = durationSeconds, watchProgress = 1_750_000L, lastWatchedAt = 200L) // completed
        val ep3 = episode(id = 3L, seasonNumber = 2, episodeNumber = 1, durationSeconds = durationSeconds, watchProgress = 1_750_000L, lastWatchedAt = 300L) // completed

        val series = Series(
            id = 10L,
            name = "Test Series",
            providerId = 1L,
            seasons = listOf(
                Season(seasonNumber = 1, episodes = listOf(ep1, ep2)),
                Season(seasonNumber = 2, episodes = listOf(ep3))
            )
        )
        val viewModel = createViewModel(series = series)

        try {
            val state = viewModel.uiState.value
            assertThat(state.resumeEpisode).isEqualTo(ep1)
            assertThat(state.isAllEpisodesCompleted).isTrue()
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun `empty series handled gracefully without crash`() = runBlocking {
        val series = Series(
            id = 10L,
            name = "Empty Series",
            providerId = 1L,
            seasons = emptyList()
        )
        val viewModel = createViewModel(series = series)

        try {
            val state = viewModel.uiState.value
            assertThat(state.resumeEpisode).isNull()
            assertThat(state.isAllEpisodesCompleted).isFalse()
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    private suspend fun createViewModel(
        series: Series = series(),
        provider: Provider = provider()
    ): SeriesDetailViewModel {
        val seriesRepository: SeriesRepository = mock()
        val providerRepository: ProviderRepository = mock()
        val playbackHistoryRepository: PlaybackHistoryRepository = mock()
        val externalRatingsRepository: ExternalRatingsRepository = mock()
        val favoriteRepository: FavoriteRepository = mock()
        val preferencesRepository: PreferencesRepository = mock()
        val pluginManager: KaynanamTVPluginManager = mock()
        val downloadManager: DownloadManager = mock()
        val castMediaRequestFactory: CastMediaRequestFactory = mock()
        val castPlaybackCoordinator: CastPlaybackCoordinator = mock()

        whenever(providerRepository.getActiveProvider()).thenReturn(flowOf(provider))
        whenever(seriesRepository.getSeriesById(series.id)).thenReturn(series)
        whenever(seriesRepository.getSeriesDetails(eq(provider.id), eq(series.id), anyOrNull()))
            .thenReturn(Result.success(series))
        whenever(playbackHistoryRepository.getUnwatchedCount(eq(provider.id), eq(series.id)))
            .thenReturn(flowOf(0))
        whenever(favoriteRepository.isFavorite(eq(provider.id), eq(series.id), any()))
            .thenReturn(false)
        whenever(externalRatingsRepository.getRatings(any()))
            .thenReturn(Result.success(ExternalRatings.unavailable()))

        return SeriesDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("seriesId" to series.id)),
            seriesRepository = seriesRepository,
            providerRepository = providerRepository,
            playbackHistoryRepository = playbackHistoryRepository,
            externalRatingsRepository = externalRatingsRepository,
            favoriteRepository = favoriteRepository,
            preferencesRepository = preferencesRepository,
            pluginManager = pluginManager,
            downloadManager = downloadManager,
            castMediaRequestFactory = castMediaRequestFactory,
            castPlaybackCoordinator = castPlaybackCoordinator
        )
    }

    private fun provider(id: Long = 1L): Provider =
        Provider(id = id, name = "Test Provider", type = ProviderType.XTREAM_CODES, serverUrl = "https://example.com")

    private fun series(
        id: Long = 10L,
        episodes: List<Episode> = listOf(episode())
    ): Series = Series(
        id = id,
        name = "Test Series",
        providerId = 1L,
        seasons = listOf(Season(seasonNumber = 1, episodes = episodes))
    )

    private fun episode(
        id: Long = 21L,
        seasonNumber: Int = 1,
        episodeNumber: Int = 1,
        durationSeconds: Int = 1800,
        watchProgress: Long = 0L,
        lastWatchedAt: Long = 0L
    ): Episode = Episode(
        id = id,
        title = "Episode $episodeNumber",
        seasonNumber = seasonNumber,
        episodeNumber = episodeNumber,
        durationSeconds = durationSeconds,
        watchProgress = watchProgress,
        lastWatchedAt = lastWatchedAt,
        providerId = 1L,
        seriesId = 10L
    )
}
