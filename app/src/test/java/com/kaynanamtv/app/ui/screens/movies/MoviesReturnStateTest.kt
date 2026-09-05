package com.kaynanamtv.app.ui.screens.movies

import com.google.common.truth.Truth.assertThat
import com.kaynanamtv.app.ui.screens.vod.VodBrowseDefaults
import com.kaynanamtv.data.preferences.PreferencesRepository
import com.kaynanamtv.domain.manager.ParentalControlManager
import com.kaynanamtv.domain.model.Category
import com.kaynanamtv.domain.model.CategorySortMode
import com.kaynanamtv.domain.model.ContentType
import com.kaynanamtv.domain.model.Favorite
import com.kaynanamtv.domain.model.Movie
import com.kaynanamtv.domain.model.PagedResult
import com.kaynanamtv.domain.model.PlaybackHistory
import com.kaynanamtv.domain.model.Provider
import com.kaynanamtv.domain.model.ProviderType
import com.kaynanamtv.domain.repository.FavoriteRepository
import com.kaynanamtv.domain.repository.MovieRepository
import com.kaynanamtv.domain.repository.PlaybackHistoryRepository
import com.kaynanamtv.domain.repository.ProviderRepository
import com.kaynanamtv.domain.usecase.ContinueWatchingResult
import com.kaynanamtv.domain.usecase.GetContinueWatching
import com.kaynanamtv.domain.usecase.GetCustomCategories
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class MoviesReturnStateTest {

    private val testDispatcher = StandardTestDispatcher()
    private val providerRepository: ProviderRepository = mock()
    private val movieRepository: MovieRepository = mock()
    private val preferencesRepository: PreferencesRepository = mock()
    private val playbackHistoryRepository: PlaybackHistoryRepository = mock()
    private val favoriteRepository: FavoriteRepository = mock()
    private val getContinueWatching: GetContinueWatching = mock()
    private val getCustomCategories: GetCustomCategories = mock()
    private val parentalControlManager: ParentalControlManager = mock()

    private val testProvider = Provider(
        id = 1L,
        name = "Test Provider",
        type = ProviderType.XTREAM_CODES,
        serverUrl = "http://example.com"
    )

    private val actionCategory = Category(id = 10L, name = "Action", type = ContentType.MOVIE)
    private val comedyCategory = Category(id = 20L, name = "Comedy", type = ContentType.MOVIE)
    private val testCategories = listOf(actionCategory, comedyCategory)

    private val movie1 = Movie(id = 101L, name = "Action Movie 1", streamUrl = "http://example.com/1.mp4", providerId = 1L, categoryId = 10L)
    private val movie2 = Movie(id = 102L, name = "Action Movie 2", streamUrl = "http://example.com/2.mp4", providerId = 1L, categoryId = 10L)

    private val activeProviderFlow = MutableStateFlow<Provider?>(testProvider)
    private val favoritesFlow = MutableStateFlow<List<Favorite>>(emptyList())
    private val continueWatchingFlow = MutableStateFlow<ContinueWatchingResult>(ContinueWatchingResult.Items(emptyList()))

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)

        whenever(providerRepository.getProviders()).thenReturn(flowOf(listOf(testProvider)))
        whenever(providerRepository.getActiveProvider()).thenReturn(activeProviderFlow)
        whenever(favoriteRepository.getAllFavorites(eq(1L), eq(ContentType.MOVIE))).thenReturn(favoritesFlow)
        whenever(getCustomCategories.invoke(eq(1L), eq(ContentType.MOVIE))).thenReturn(flowOf(emptyList()))
        whenever(movieRepository.getCategories(1L)).thenReturn(flowOf(testCategories))
        whenever(movieRepository.getCategoryItemCounts(1L)).thenReturn(flowOf(mapOf(10L to 2, 20L to 0)))
        whenever(movieRepository.getLibraryCount(1L)).thenReturn(flowOf(2))
        whenever(preferencesRepository.getHiddenCategoryIds(eq(1L), eq(ContentType.MOVIE))).thenReturn(flowOf(emptySet()))
        whenever(preferencesRepository.getCategorySortMode(eq(1L), eq(ContentType.MOVIE))).thenReturn(flowOf(CategorySortMode.DEFAULT))
        whenever(preferencesRepository.vodViewMode).thenReturn(flowOf("MODERN"))
        whenever(preferencesRepository.vodInfiniteScroll).thenReturn(flowOf(true))
        whenever(preferencesRepository.parentalControlLevel).thenReturn(flowOf(0))
        whenever(parentalControlManager.unlockedCategoriesForProvider(1L)).thenReturn(flowOf(emptySet()))

        whenever(movieRepository.getCategoryPreviewRows(eq(1L), any(), any())).thenReturn(
            flowOf(mapOf(10L to listOf(movie1, movie2)))
        )
        whenever(movieRepository.getTopRatedPreview(eq(1L), any())).thenReturn(flowOf(emptyList()))
        whenever(movieRepository.getFreshPreview(eq(1L), any())).thenReturn(flowOf(emptyList()))
        whenever(getContinueWatching.invoke(eq(1L), any(), any(), any())).thenReturn(continueWatchingFlow)

        whenever(movieRepository.getMoviesByIds(any())).thenReturn(flowOf(listOf(movie1, movie2)))

        whenever(movieRepository.browseMovies(any())).thenReturn(
            flowOf(PagedResult(items = listOf(movie1, movie2), totalCount = 2, offset = 0, limit = 50, hasMoreRemote = false))
        )
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `when category is selected and external player updates progress, category and cards remain preserved`() = runTest {
        val viewModel = MoviesViewModel(
            providerRepository = providerRepository,
            movieRepository = movieRepository,
            preferencesRepository = preferencesRepository,
            playbackHistoryRepository = playbackHistoryRepository,
            favoriteRepository = favoriteRepository,
            getContinueWatching = getContinueWatching,
            getCustomCategories = getCustomCategories,
            parentalControlManager = parentalControlManager
        )
        advanceUntilIdle()

        // User selects "Action" category
        viewModel.selectCategory("Action")
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.selectedCategory).isEqualTo("Action")
        assertThat(viewModel.uiState.value.selectedCategoryItems).hasSize(2)
        assertThat(viewModel.uiState.value.selectedCategoryItems.map { it.name }).containsExactly("Action Movie 1", "Action Movie 2")

        // Simulate returning from External VLC: progress was saved, continueWatching emits update
        continueWatchingFlow.value = ContinueWatchingResult.Items(
            listOf(
                PlaybackHistory(
                    contentId = 101L,
                    contentType = ContentType.MOVIE,
                    providerId = 1L,
                    title = "Action Movie 1",
                    streamUrl = "http://example.com/1.mp4",
                    resumePositionMs = 5000L,
                    totalDurationMs = 10000L,
                    lastWatchedAt = System.currentTimeMillis()
                )
            )
        )
        // Also simulate favorites update
        favoritesFlow.value = listOf(
            Favorite(
                id = 1L,
                providerId = 1L,
                contentId = 101L,
                contentType = ContentType.MOVIE,
                position = 0,
                addedAt = System.currentTimeMillis()
            )
        )
        advanceUntilIdle()

        // Verify selected category and items are still intact and favorite flag is updated
        val state = viewModel.uiState.value
        assertThat(state.selectedCategory).isEqualTo("Action")
        assertThat(state.selectedCategoryItems).hasSize(2)
        assertThat(state.selectedCategoryItems.first { it.id == 101L }.isFavorite).isTrue()
        assertThat(state.hasActiveProvider).isTrue()
    }

    @Test
    fun `when favorites category is selected, catalog re-emission does not wipe selectedCategory`() = runTest {
        val viewModel = MoviesViewModel(
            providerRepository = providerRepository,
            movieRepository = movieRepository,
            preferencesRepository = preferencesRepository,
            playbackHistoryRepository = playbackHistoryRepository,
            favoriteRepository = favoriteRepository,
            getContinueWatching = getContinueWatching,
            getCustomCategories = getCustomCategories,
            parentalControlManager = parentalControlManager
        )
        advanceUntilIdle()

        whenever(movieRepository.getMoviesByIds(any())).thenReturn(flowOf(listOf(movie1)))

        viewModel.selectCategory(VodBrowseDefaults.FAVORITES_CATEGORY)
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.selectedCategory).isEqualTo(VodBrowseDefaults.FAVORITES_CATEGORY)

        // Simulate room emission
        favoritesFlow.value = listOf(
            Favorite(
                id = 1L,
                providerId = 1L,
                contentId = 101L,
                contentType = ContentType.MOVIE,
                position = 0,
                addedAt = System.currentTimeMillis()
            )
        )
        advanceUntilIdle()

        assertThat(viewModel.uiState.value.selectedCategory).isEqualTo(VodBrowseDefaults.FAVORITES_CATEGORY)
    }
}
