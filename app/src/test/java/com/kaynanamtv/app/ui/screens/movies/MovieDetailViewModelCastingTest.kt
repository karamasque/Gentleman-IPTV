package com.kaynanamtv.app.ui.screens.movies

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.google.common.truth.Truth.assertThat
import com.kaynanamtv.app.MainDispatcherRule
import com.kaynanamtv.app.R
import com.kaynanamtv.app.cast.CastMediaRequest
import com.kaynanamtv.app.cast.CastMediaRequestFactory
import com.kaynanamtv.app.cast.CastPlaybackEvent
import com.kaynanamtv.app.cast.CastPlaybackCoordinator
import com.kaynanamtv.app.cast.CastStartResult
import com.kaynanamtv.app.cast.CastUiEvent
import com.kaynanamtv.app.plugins.KaynanamTVPluginManager
import com.kaynanamtv.data.preferences.PreferencesRepository
import com.kaynanamtv.domain.model.ExternalRatings
import com.kaynanamtv.domain.model.Movie
import com.kaynanamtv.domain.model.Provider
import com.kaynanamtv.domain.model.ProviderType
import com.kaynanamtv.domain.model.Result
import com.kaynanamtv.domain.model.StreamInfo
import com.kaynanamtv.domain.repository.DownloadManager
import com.kaynanamtv.domain.repository.ExternalRatingsRepository
import com.kaynanamtv.domain.repository.FavoriteRepository
import com.kaynanamtv.domain.repository.MovieRepository
import com.kaynanamtv.domain.repository.PlaybackHistoryRepository
import com.kaynanamtv.domain.repository.ProviderRepository
import kotlinx.coroutines.cancel
import kotlinx.coroutines.async
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
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
class MovieDetailViewModelCastingTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    @Test
    fun `castMovie emits route chooser and preserves resume position`() = runBlocking {
        val movie = movie(watchProgress = 42_000L)
        val coordinator = FakeCastPlaybackCoordinator(CastStartResult.ROUTE_SELECTION_REQUIRED)
        val viewModel = createViewModel(movie = movie, coordinator = coordinator)

        try {
            val event = async(start = CoroutineStart.UNDISPATCHED) { viewModel.castEvents.first() }
            viewModel.castMovie()

            assertThat(withTimeout(5_000L) { event.await() }).isEqualTo(CastUiEvent.OpenRouteChooser)
            assertThat(coordinator.lastRequest?.url).isEqualTo("https://example.test/movie.m3u8")
            assertThat(coordinator.lastRequest?.startPositionMs).isEqualTo(42_000L)
            assertThat(viewModel.uiState.value.isCasting).isTrue()
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun `castMovie emits unsupported message from coordinator result`() = runBlocking {
        val coordinator = FakeCastPlaybackCoordinator(CastStartResult.UNSUPPORTED)
        val viewModel = createViewModel(coordinator = coordinator)

        try {
            val event = async(start = CoroutineStart.UNDISPATCHED) { viewModel.castEvents.first() }
            viewModel.castMovie()

            assertThat(withTimeout(5_000L) { event.await() })
                .isEqualTo(CastUiEvent.ShowMessage(R.string.cast_stream_unsupported))
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun `castMovie reports started after route-selected media load succeeds`() = runBlocking {
        val coordinator = FakeCastPlaybackCoordinator(CastStartResult.ROUTE_SELECTION_REQUIRED)
        val viewModel = createViewModel(coordinator = coordinator)

        try {
            val routeEvent = async(start = CoroutineStart.UNDISPATCHED) { viewModel.castEvents.first() }
            viewModel.castMovie()
            assertThat(withTimeout(5_000L) { routeEvent.await() }).isEqualTo(CastUiEvent.OpenRouteChooser)

            val lifecycleEvent = async(start = CoroutineStart.UNDISPATCHED) { viewModel.castEvents.first() }
            coordinator.emit(CastPlaybackEvent.MediaLoadSucceeded("Movie"))

            assertThat(withTimeout(5_000L) { lifecycleEvent.await() })
                .isEqualTo(CastUiEvent.ShowMessage(R.string.cast_started))
            assertThat(viewModel.uiState.value.isCasting).isFalse()
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun `castMovie reports receiver load failure after immediate start`() = runBlocking {
        val coordinator = FakeCastPlaybackCoordinator(CastStartResult.STARTED)
        val viewModel = createViewModel(coordinator = coordinator)

        try {
            val startEvent = async(start = CoroutineStart.UNDISPATCHED) { viewModel.castEvents.first() }
            viewModel.castMovie()
            assertThat(withTimeout(5_000L) { startEvent.await() })
                .isEqualTo(CastUiEvent.ShowMessage(R.string.cast_started))

            val lifecycleEvent = async(start = CoroutineStart.UNDISPATCHED) { viewModel.castEvents.first() }
            coordinator.emit(CastPlaybackEvent.MediaLoadFailed(title = "Movie", statusCode = 2100))

            assertThat(withTimeout(5_000L) { lifecycleEvent.await() })
                .isEqualTo(CastUiEvent.ShowMessage(R.string.cast_load_failed))
            assertThat(viewModel.uiState.value.isCasting).isFalse()
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun `castMovie resets pending state when route selection is cancelled`() = runBlocking {
        val coordinator = FakeCastPlaybackCoordinator(CastStartResult.ROUTE_SELECTION_REQUIRED)
        val viewModel = createViewModel(coordinator = coordinator)

        try {
            val routeEvent = async(start = CoroutineStart.UNDISPATCHED) { viewModel.castEvents.first() }
            viewModel.castMovie()
            assertThat(withTimeout(5_000L) { routeEvent.await() }).isEqualTo(CastUiEvent.OpenRouteChooser)
            assertThat(viewModel.uiState.value.isCasting).isTrue()

            coordinator.emit(CastPlaybackEvent.RouteSelectionCancelled)

            withTimeout(5_000L) { viewModel.uiState.first { !it.isCasting } }
            assertThat(viewModel.uiState.value.isCasting).isFalse()
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun `castMovie ignores repeated launches while route selection is pending`() = runBlocking {
        val coordinator = FakeCastPlaybackCoordinator(CastStartResult.ROUTE_SELECTION_REQUIRED)
        val viewModel = createViewModel(coordinator = coordinator)

        try {
            val routeEvent = async(start = CoroutineStart.UNDISPATCHED) { viewModel.castEvents.first() }
            viewModel.castMovie()
            assertThat(withTimeout(5_000L) { routeEvent.await() }).isEqualTo(CastUiEvent.OpenRouteChooser)

            viewModel.castMovie()

            assertThat(coordinator.startCount).isEqualTo(1)
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    @Test
    fun `castMovie explains streams that need header rewrite when cast remains unsupported`() = runBlocking {
        val coordinator = FakeCastPlaybackCoordinator(CastStartResult.UNSUPPORTED)
        val viewModel = createViewModel(
            streamInfo = StreamInfo(
                url = "https://example.test/movie.m3u8",
                headers = mapOf("Cookie" to "session=abc")
            ),
            coordinator = coordinator
        )

        try {
            val event = async(start = CoroutineStart.UNDISPATCHED) { viewModel.castEvents.first() }
            viewModel.castMovie()

            assertThat(withTimeout(5_000L) { event.await() })
                .isEqualTo(CastUiEvent.ShowMessage(R.string.cast_headers_unsupported))
            assertThat(coordinator.lastRequest?.requiresCastRewrite).isTrue()
        } finally {
            viewModel.viewModelScope.cancel()
        }
    }

    private suspend fun createViewModel(
        movie: Movie = movie(),
        streamInfo: StreamInfo = StreamInfo(url = "https://example.test/movie.m3u8"),
        coordinator: FakeCastPlaybackCoordinator = FakeCastPlaybackCoordinator()
    ): MovieDetailViewModel {
        val provider = Provider(
            id = movie.providerId,
            name = "Provider",
            type = ProviderType.XTREAM_CODES,
            serverUrl = "https://provider.test"
        )
        val movieRepository: MovieRepository = mock()
        val providerRepository: ProviderRepository = mock()
        val playbackHistoryRepository: PlaybackHistoryRepository = mock()
        val externalRatingsRepository: ExternalRatingsRepository = mock()
        val favoriteRepository: FavoriteRepository = mock()

        whenever(movieRepository.getMovie(movie.id)).thenReturn(movie)
        whenever(movieRepository.getMovieDetails(eq(provider.id), eq(movie.id), anyOrNull()))
            .thenReturn(Result.success(movie))
        whenever(movieRepository.getRelatedContent(eq(provider.id), eq(movie.id), any()))
            .thenReturn(flowOf(emptyList()))
        whenever(movieRepository.getStreamInfo(any()))
            .thenReturn(Result.success(streamInfo))
        whenever(providerRepository.getActiveProvider()).thenReturn(flowOf(provider))
        whenever(
            playbackHistoryRepository.getPlaybackHistory(
                contentId = eq(movie.id),
                contentType = any(),
                providerId = eq(provider.id),
                seriesId = anyOrNull(),
                seasonNumber = anyOrNull(),
                episodeNumber = anyOrNull()
            )
        ).thenReturn(null)
        whenever(favoriteRepository.isFavorite(eq(provider.id), eq(movie.id), any()))
            .thenReturn(false)
        whenever(externalRatingsRepository.getRatings(any()))
            .thenReturn(Result.success(ExternalRatings.unavailable()))

        return MovieDetailViewModel(
            savedStateHandle = SavedStateHandle(mapOf("movieId" to movie.id)),
            movieRepository = movieRepository,
            providerRepository = providerRepository,
            playbackHistoryRepository = playbackHistoryRepository,
            externalRatingsRepository = externalRatingsRepository,
            favoriteRepository = favoriteRepository,
            preferencesRepository = mock<PreferencesRepository>(),
            pluginManager = mock<KaynanamTVPluginManager>(),
            downloadManager = mock<DownloadManager>(),
            castMediaRequestFactory = CastMediaRequestFactory(),
            castPlaybackCoordinator = coordinator
        )
    }

    private class FakeCastPlaybackCoordinator(
        var result: CastStartResult = CastStartResult.STARTED
    ) : CastPlaybackCoordinator {
        private val mutablePlaybackEvents = MutableSharedFlow<CastPlaybackEvent>(extraBufferCapacity = 8)
        override val playbackEvents: SharedFlow<CastPlaybackEvent> = mutablePlaybackEvents.asSharedFlow()
        override val remoteAudioTracks: kotlinx.coroutines.flow.StateFlow<List<com.kaynanamtv.app.cast.CastTrackInfo>> = kotlinx.coroutines.flow.MutableStateFlow(emptyList())
        override val remoteCurrentPosition: kotlinx.coroutines.flow.StateFlow<Long> = kotlinx.coroutines.flow.MutableStateFlow(0L)
        override val remoteDuration: kotlinx.coroutines.flow.StateFlow<Long> = kotlinx.coroutines.flow.MutableStateFlow(0L)
        override val isRemotePlaying: kotlinx.coroutines.flow.StateFlow<Boolean> = kotlinx.coroutines.flow.MutableStateFlow(false)
        override val isRemoteLiveSeekable: kotlinx.coroutines.flow.StateFlow<Boolean> = kotlinx.coroutines.flow.MutableStateFlow(false)
        var lastRequest: CastMediaRequest? = null

        override suspend fun startCasting(request: CastMediaRequest): CastStartResult {
            lastRequest = request
            startCount += 1
            return result
        }

        override fun stopCasting() {}
        override fun play() {}
        override fun pause() {}
        override fun seekTo(positionMs: Long) {}
        override fun seekRelative(offsetMs: Long) {}
        override fun setActiveAudioTrack(trackId: Long) {}

        var startCount: Int = 0

        suspend fun emit(event: CastPlaybackEvent) {
            mutablePlaybackEvents.emit(event)
        }
    }

    private companion object {
        fun movie(watchProgress: Long = 0L) = Movie(
            id = 10L,
            name = "Movie",
            providerId = 1L,
            posterUrl = "https://example.test/poster.jpg",
            watchProgress = watchProgress
        )
    }
}
