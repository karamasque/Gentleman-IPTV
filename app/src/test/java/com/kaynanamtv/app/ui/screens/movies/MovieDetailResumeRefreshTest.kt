package com.kaynanamtv.app.ui.screens.movies

import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import com.kaynanamtv.app.cast.CastMediaRequestFactory
import com.kaynanamtv.app.cast.CastPlaybackCoordinator
import com.kaynanamtv.app.plugins.KaynanamTVPluginManager
import com.kaynanamtv.data.preferences.PreferencesRepository
import com.kaynanamtv.domain.model.ContentType
import com.kaynanamtv.domain.model.ExternalRatings
import com.kaynanamtv.domain.model.Movie
import com.kaynanamtv.domain.model.PlaybackHistory
import com.kaynanamtv.domain.model.Result
import com.kaynanamtv.domain.repository.DownloadManager
import com.kaynanamtv.domain.repository.ExternalRatingsRepository
import com.kaynanamtv.domain.repository.FavoriteRepository
import com.kaynanamtv.domain.repository.MovieRepository
import com.kaynanamtv.domain.repository.PlaybackHistoryRepository
import com.kaynanamtv.domain.repository.ProviderRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.kotlin.any
import org.mockito.kotlin.eq
import org.mockito.kotlin.mock
import org.mockito.kotlin.whenever
import org.robolectric.RobolectricTestRunner

import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MovieDetailResumeRefreshTest {

    private val testDispatcher = StandardTestDispatcher()
    private val movieRepository: MovieRepository = mock()
    private val providerRepository: ProviderRepository = mock()
    private val playbackHistoryRepository: PlaybackHistoryRepository = mock()
    private val externalRatingsRepository: ExternalRatingsRepository = mock()
    private val favoriteRepository: FavoriteRepository = mock()
    private val preferencesRepository: PreferencesRepository = mock()
    private val pluginManager: KaynanamTVPluginManager = mock()
    private val downloadManager: DownloadManager = mock()
    private val castMediaRequestFactory: CastMediaRequestFactory = mock()
    private val castPlaybackCoordinator: CastPlaybackCoordinator = mock()
    private val castEventsFlow = MutableSharedFlow<com.kaynanamtv.app.cast.CastPlaybackEvent>()

    private val testMovie = Movie(
        id = 101L,
        name = "Test Movie",
        streamUrl = "http://example.com/movie.mp4",
        providerId = 1L,
        durationSeconds = 7200, // 2 hours = 7,200,000 ms
        watchProgress = 0L
    )

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
        whenever(castPlaybackCoordinator.playbackEvents).thenReturn(castEventsFlow.asSharedFlow())
        runTest(testDispatcher) {
            whenever(favoriteRepository.isFavorite(any(), any(), any())).thenReturn(false)
            whenever(externalRatingsRepository.getRatings(any())).thenReturn(Result.success(ExternalRatings.unavailable()))
        }
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    private fun createViewModel(): MovieDetailViewModel {
        val savedStateHandle = SavedStateHandle(mapOf("movieId" to 101L, "providerId" to 1L))
        return MovieDetailViewModel(
            savedStateHandle = savedStateHandle,
            movieRepository = movieRepository,
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

    @Test
    fun `initial load with zero progress shows no resume`() = runTest(testDispatcher) {
        whenever(movieRepository.getMovie(101L)).thenReturn(testMovie)
        whenever(movieRepository.getMovieDetails(eq(1L), eq(101L), org.mockito.kotlin.anyOrNull())).thenReturn(Result.success(testMovie))
        whenever(playbackHistoryRepository.getPlaybackHistory(101L, ContentType.MOVIE, 1L)).thenReturn(null)
        whenever(movieRepository.getRelatedContent(eq(1L), eq(101L), eq(10))).thenReturn(flowOf(emptyList()))

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.hasResume).isFalse()
        assertThat(viewModel.uiState.value.resumePositionMs).isEqualTo(0L)
    }

    @Test
    fun `refreshPlaybackState updates resume position when Room progress changes to 15 min`() = runTest(testDispatcher) {
        // Initial load: 0 progress
        whenever(movieRepository.getMovie(101L)).thenReturn(testMovie)
        whenever(movieRepository.getMovieDetails(eq(1L), eq(101L), org.mockito.kotlin.anyOrNull())).thenReturn(Result.success(testMovie))
        whenever(playbackHistoryRepository.getPlaybackHistory(101L, ContentType.MOVIE, 1L)).thenReturn(null)
        whenever(movieRepository.getRelatedContent(eq(1L), eq(101L), eq(10))).thenReturn(flowOf(emptyList()))

        val viewModel = createViewModel()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.hasResume).isFalse()

        // User watched 15 minutes (900_000 ms) in player, then player exited -> Room updated
        val updatedHistory = PlaybackHistory(
            contentId = 101L,
            contentType = ContentType.MOVIE,
            providerId = 1L,
            title = "Test Movie",
            streamUrl = "http://example.com/movie.mp4",
            resumePositionMs = 900_000L,
            totalDurationMs = 7_200_000L,
            lastWatchedAt = System.currentTimeMillis()
        )
        whenever(playbackHistoryRepository.getPlaybackHistory(101L, ContentType.MOVIE, 1L)).thenReturn(updatedHistory)

        // Screen resumes -> refreshPlaybackState()
        viewModel.refreshPlaybackState()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.hasResume).isTrue()
        assertThat(viewModel.uiState.value.resumePositionMs).isEqualTo(900_000L)
    }

    @Test
    fun `refreshPlaybackState does not show resume for completed content`() = runTest(testDispatcher) {
        whenever(movieRepository.getMovie(101L)).thenReturn(testMovie)
        whenever(movieRepository.getMovieDetails(eq(1L), eq(101L), org.mockito.kotlin.anyOrNull())).thenReturn(Result.success(testMovie))
        whenever(movieRepository.getRelatedContent(eq(1L), eq(101L), eq(10))).thenReturn(flowOf(emptyList()))

        val viewModel = createViewModel()
        advanceUntilIdle()

        // User watched 98% (completed)
        val completedHistory = PlaybackHistory(
            contentId = 101L,
            contentType = ContentType.MOVIE,
            providerId = 1L,
            title = "Test Movie",
            streamUrl = "http://example.com/movie.mp4",
            resumePositionMs = 7_100_000L,
            totalDurationMs = 7_200_000L,
            lastWatchedAt = System.currentTimeMillis()
        )
        whenever(playbackHistoryRepository.getPlaybackHistory(101L, ContentType.MOVIE, 1L)).thenReturn(completedHistory)

        viewModel.refreshPlaybackState()
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.hasResume).isFalse()
        assertThat(viewModel.uiState.value.resumePositionMs).isEqualTo(0L)
    }
}
