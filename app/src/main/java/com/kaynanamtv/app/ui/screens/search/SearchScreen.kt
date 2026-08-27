package com.kaynanamtv.app.ui.screens.search

import androidx.annotation.StringRes
import com.kaynanamtv.app.ui.interaction.TvClickableSurface

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Star
import androidx.tv.material3.*
import com.kaynanamtv.app.R
import com.kaynanamtv.app.ui.components.CategoryRow
import com.kaynanamtv.app.ui.components.SearchInput
import com.kaynanamtv.app.ui.components.ChannelCard
import com.kaynanamtv.app.ui.components.MovieCard
import com.kaynanamtv.app.ui.components.SeriesCard
import com.kaynanamtv.app.ui.components.TvEmptyState
import com.kaynanamtv.app.ui.components.shell.AppNavigationChrome
import com.kaynanamtv.app.ui.components.shell.AppScreenScaffold
import com.kaynanamtv.app.ui.design.AppColors
import com.kaynanamtv.app.ui.design.requestFocusSafely
import com.kaynanamtv.app.ui.interaction.mouseClickable
import com.kaynanamtv.app.ui.theme.*
import com.kaynanamtv.domain.manager.ParentalControlManager
import com.kaynanamtv.domain.model.Channel
import com.kaynanamtv.domain.model.ContentType
import com.kaynanamtv.domain.model.Movie
import com.kaynanamtv.domain.model.SearchHistoryScope
import com.kaynanamtv.domain.model.Series
import com.kaynanamtv.domain.repository.CategoryRepository
import com.kaynanamtv.domain.repository.FavoriteRepository
import com.kaynanamtv.domain.repository.ProviderRepository
import com.kaynanamtv.domain.usecase.SearchContent
import com.kaynanamtv.domain.usecase.SearchContentScope
import com.kaynanamtv.domain.manager.RecordingManager
import com.kaynanamtv.domain.model.RecordingStatus
import com.kaynanamtv.domain.util.AdultContentVisibilityPolicy
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
@OptIn(ExperimentalCoroutinesApi::class)
class SearchViewModel @Inject constructor(
    private val providerRepository: ProviderRepository,
    private val searchContent: SearchContent,
    private val preferencesRepository: com.kaynanamtv.data.preferences.PreferencesRepository,
    private val parentalControlManager: ParentalControlManager,
    private val favoriteRepository: FavoriteRepository,
    private val categoryRepository: CategoryRepository,
    private val recordingManager: RecordingManager
) : ViewModel() {
    private companion object {
        const val MAX_RESULTS_PER_SECTION = 120
        const val MAX_RECENT_QUERIES = 6
    }

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    private val _selectedTab = MutableStateFlow(SearchTab.ALL)
    val selectedTab: StateFlow<SearchTab> = _selectedTab.asStateFlow()

    private val _parentalControlLevel = MutableStateFlow(0)
    private val _activeProviderId = MutableStateFlow<Long?>(null)

    private val _recordingChannelIds = MutableStateFlow<Set<Long>>(emptySet())
    val recordingChannelIds: StateFlow<Set<Long>> = _recordingChannelIds.asStateFlow()

    private val _scheduledChannelIds = MutableStateFlow<Set<Long>>(emptySet())
    val scheduledChannelIds: StateFlow<Set<Long>> = _scheduledChannelIds.asStateFlow()

    private val unlockedCategoryIds = providerRepository.getProviders()
        .onEach { providers ->
            viewModelScope.launch {
                val active = providerRepository.getActiveProvider().first()
                _activeProviderId.value = active?.id ?: providers.firstOrNull()?.id
            }
        }
        .flatMapLatest { providers ->
            if (providers.isEmpty()) {
                flowOf(emptyMap<Long, Set<Long>>())
            } else {
                val flows = providers.map { provider ->
                    parentalControlManager.unlockedCategoriesForProvider(provider.id)
                        .map { unlockedSet -> provider.id to unlockedSet }
                }
                combine(flows) { pairs -> pairs.toMap() }
            }
        }

    init {
        viewModelScope.launch {
            preferencesRepository.parentalControlLevel.collect { level ->
                _parentalControlLevel.value = level
            }
        }
        viewModelScope.launch {
            recordingManager.observeRecordingItems().collect { items ->
                _recordingChannelIds.value = items
                    .filter { it.status == RecordingStatus.RECORDING }
                    .map { it.channelId }.toSet()
                _scheduledChannelIds.value = items
                    .filter { it.status == RecordingStatus.SCHEDULED }
                    .map { it.channelId }.toSet()
            }
        }
    }

    val recentQueries: StateFlow<List<String>> = combine(
        _selectedTab,
        _activeProviderId
    ) { tab, providerId ->
        tab.toSearchHistoryScope() to providerId
    }.flatMapLatest { (scope, providerId) ->
        preferencesRepository.getRecentSearchQueries(
            scope = scope,
            providerId = providerId,
            limit = MAX_RECENT_QUERIES
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(FlowPreview::class)
    val uiState: StateFlow<SearchUiState> = combine(
        providerRepository.getProviders(),
        _query.debounce(300),
        _selectedTab,
        _parentalControlLevel,
        unlockedCategoryIds
    ) { providers, query, tab, level, unlockedIds ->
        SearchFilterParams(providers, query, tab, level, unlockedIds)
    }.distinctUntilChanged().flatMapLatest { params ->
        val providers = params.providers
        val query = params.query
        val tab = params.tab
        val level = params.level
        val unlockedIds = params.unlockedCategoryIds

        val trimmedQueryLength = query.trim().length
        if (providers.isEmpty() || trimmedQueryLength < 2) {
            flowOf(
                SearchUiState(
                    parentalControlLevel = level,
                    hasActiveProvider = providers.isNotEmpty(),
                    queryLength = trimmedQueryLength,
                    unlockedCategoryIds = unlockedIds,
                    providerNames = providers.associate { it.id to it.name }
                )
            )
        } else {
            val searchFlows = providers.map { provider ->
                searchContent(
                    providerId = provider.id,
                    query = query,
                    scope = tab.toSearchScope(),
                    maxResultsPerSection = MAX_RESULTS_PER_SECTION
                ).map { result -> provider to result }
            }
            combine(searchFlows) { providerResults ->
                val allChannels = mutableListOf<Channel>()
                val allMovies = mutableListOf<Movie>()
                val allSeries = mutableListOf<Series>()
                var isPartial = false
                
                providerResults.forEach { (provider, result) ->
                    val filterAdult = !AdultContentVisibilityPolicy.showInAggregatedSurfaces(level)
                    val filteredChannels = if (filterAdult)
                        result.channels.filterNot { it.isAdult || it.isUserProtected }
                    else result.channels
                    val filteredMovies = if (filterAdult)
                        result.movies.filterNot { it.isAdult || it.isUserProtected }
                    else result.movies
                    val filteredSeries = if (filterAdult)
                        result.series.filterNot { it.isAdult || it.isUserProtected }
                    else result.series
                    
                    allChannels.addAll(filteredChannels)
                    allMovies.addAll(filteredMovies)
                    allSeries.addAll(filteredSeries)
                    if (result.isPartialResult) {
                        isPartial = true
                    }
                }
                
                SearchUiState(
                    channels = allChannels,
                    movies = allMovies,
                    series = allSeries,
                    isLoading = false,
                    hasSearched = true,
                    hasSearchError = isPartial,
                    parentalControlLevel = level,
                    hasActiveProvider = providers.isNotEmpty(),
                    queryLength = trimmedQueryLength,
                    unlockedCategoryIds = unlockedIds,
                    providerNames = providers.associate { it.id to it.name }
                )
            }.onStart {
                emit(
                    SearchUiState(
                        isLoading = true,
                        hasSearched = true,
                        parentalControlLevel = level,
                        hasActiveProvider = providers.isNotEmpty(),
                        queryLength = trimmedQueryLength,
                        unlockedCategoryIds = unlockedIds,
                        providerNames = providers.associate { it.id to it.name }
                    )
                )
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SearchUiState())

    fun onQueryChange(newQuery: String) {
        _query.value = newQuery
    }

    fun onSearchSubmitted() {
        val normalizedQuery = _query.value.trim()
        if (normalizedQuery.length < 2) return

        _query.value = normalizedQuery
        viewModelScope.launch {
            preferencesRepository.recordRecentSearchQuery(
                query = normalizedQuery,
                scope = _selectedTab.value.toSearchHistoryScope(),
                providerId = _activeProviderId.value
            )
        }
    }

    fun onRecentQuerySelected(query: String) {
        _query.value = query
        onSearchSubmitted()
    }

    fun submitExternalQuery(query: String) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.length < 2) return

        _query.value = normalizedQuery
        onSearchSubmitted()
    }

    fun clearRecentQueries() {
        viewModelScope.launch {
            preferencesRepository.clearRecentSearchQueries(
                scope = _selectedTab.value.toSearchHistoryScope(),
                providerId = _activeProviderId.value
            )
        }
    }

    fun onTabSelected(tab: SearchTab) {
        _selectedTab.value = tab
    }

    suspend fun verifyPin(pin: String): Boolean {
        return preferencesRepository.verifyParentalPin(pin)
    }

    fun unlockCategory(providerId: Long, categoryId: Long?) {
        val resolvedCategoryId = categoryId ?: return
        parentalControlManager.unlockCategory(providerId, resolvedCategoryId)
    }

    // ── Search item long-press actions ────────────────────────────────

    fun toggleFavorite(providerId: Long, contentId: Long, contentType: ContentType, currentlyFavorite: Boolean) {
        viewModelScope.launch {
            if (currentlyFavorite) {
                favoriteRepository.removeFavorite(providerId, contentId, contentType)
            } else {
                favoriteRepository.addFavorite(providerId, contentId, contentType)
            }
        }
    }

    fun hideItemCategory(providerId: Long, categoryId: Long, contentType: ContentType) {
        viewModelScope.launch {
            preferencesRepository.setCategoryHidden(
                providerId = providerId,
                type = contentType,
                categoryId = categoryId,
                hidden = true
            )
        }
    }

    fun toggleCategoryProtection(providerId: Long, categoryId: Long, contentType: ContentType, currentlyProtected: Boolean) {
        viewModelScope.launch {
            categoryRepository.setCategoryProtection(
                providerId = providerId,
                categoryId = categoryId,
                type = contentType,
                isProtected = !currentlyProtected
            )
        }
    }
}

private data class SearchFilterParams(
    val providers: List<com.kaynanamtv.domain.model.Provider>,
    val query: String,
    val tab: SearchTab,
    val level: Int,
    val unlockedCategoryIds: Map<Long, Set<Long>>
)

enum class SearchTab(@get:StringRes val titleRes: Int) {
    ALL(R.string.search_all),
    LIVE(R.string.search_live_tv),
    MOVIES(R.string.search_movies),
    SERIES(R.string.search_series)
}

private fun SearchTab.toSearchScope(): SearchContentScope = when (this) {
    SearchTab.ALL -> SearchContentScope.ALL
    SearchTab.LIVE -> SearchContentScope.LIVE
    SearchTab.MOVIES -> SearchContentScope.MOVIES
    SearchTab.SERIES -> SearchContentScope.SERIES
}

private fun SearchTab.toSearchHistoryScope(): SearchHistoryScope = when (this) {
    SearchTab.ALL -> SearchHistoryScope.ALL
    SearchTab.LIVE -> SearchHistoryScope.LIVE
    SearchTab.MOVIES -> SearchHistoryScope.MOVIE
    SearchTab.SERIES -> SearchHistoryScope.SERIES
}

data class SearchUiState(
    val channels: List<Channel> = emptyList(),
    val movies: List<Movie> = emptyList(),
    val series: List<Series> = emptyList(),
    val isLoading: Boolean = false,
    val hasSearched: Boolean = false,
    val hasSearchError: Boolean = false,
    val parentalControlLevel: Int = 0,
    val hasActiveProvider: Boolean = false,
    val queryLength: Int = 0,
    val unlockedCategoryIds: Map<Long, Set<Long>> = emptyMap(),
    val providerNames: Map<Long, String> = emptyMap()
) {
    val isEmpty: Boolean get() = hasSearched && channels.isEmpty() && movies.isEmpty() && series.isEmpty()
    val totalResults: Int get() = channels.size + movies.size + series.size
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
fun SearchScreen(
    initialQuery: String = "",
    onChannelClick: (Channel) -> Unit,
    onMovieClick: (Movie) -> Unit,
    onSeriesClick: (Series) -> Unit,
    onNavigate: (String) -> Unit,
    currentRoute: String,
    viewModel: SearchViewModel = hiltViewModel()
) {
    val query by viewModel.query.collectAsStateWithLifecycle()
    val selectedTab by viewModel.selectedTab.collectAsStateWithLifecycle()
    val recentQueries by viewModel.recentQueries.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val recordingChannelIds by viewModel.recordingChannelIds.collectAsStateWithLifecycle()
    val scheduledChannelIds by viewModel.scheduledChannelIds.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    val searchFocusRequester = remember { FocusRequester() }
    val context = androidx.compose.ui.platform.LocalContext.current
    var showPinDialog by remember { mutableStateOf(false) }
    var pinError by remember { mutableStateOf<String?>(null) }
    var pendingChannel by remember { mutableStateOf<Channel?>(null) }
    var pendingMovie by remember { mutableStateOf<Movie?>(null) }
    var pendingSeries by remember { mutableStateOf<Series?>(null) }
    val scope = rememberCoroutineScope()
    val selectedStateLabel = stringResource(R.string.a11y_selected)

    // ── Long-press actions dialog state ───────────────────────────────
    var showActionsDialog by remember { mutableStateOf(false) }
    var actionsChannel by remember { mutableStateOf<Channel?>(null) }
    var actionsMovie by remember { mutableStateOf<Movie?>(null) }
    var actionsSeries by remember { mutableStateOf<Series?>(null) }
    var actionsIsFavorite by remember { mutableStateOf(false) }
    // Separate PIN purpose: unlock-to-play vs toggle-parental-protection
    var pinIsForProtectionToggle by remember { mutableStateOf(false) }
    var pendingProtectionCategoryId by remember { mutableStateOf<Long?>(null) }
    var pendingProtectionContentType by remember { mutableStateOf<ContentType?>(null) }
    var pendingProtectionCurrentlyProtected by remember { mutableStateOf(false) }
    var pendingProtectionProviderId by remember { mutableStateOf<Long?>(null) }

    fun showChannelActions(channel: Channel) {
        actionsChannel = channel; actionsMovie = null; actionsSeries = null
        actionsIsFavorite = channel.isFavorite
        showActionsDialog = true
    }
    fun showMovieActions(movie: Movie) {
        actionsChannel = null; actionsMovie = movie; actionsSeries = null
        actionsIsFavorite = movie.isFavorite
        showActionsDialog = true
    }
    fun showSeriesActions(series: Series) {
        actionsChannel = null; actionsMovie = null; actionsSeries = series
        actionsIsFavorite = series.isFavorite
        showActionsDialog = true
    }
    val channelRows = remember(uiState.channels) { uiState.channels.chunked(4) }
    val movieRows = remember(uiState.movies) { uiState.movies.chunked(6) }
    val seriesRows = remember(uiState.series) { uiState.series.chunked(6) }

    fun isLocked(providerId: Long, categoryId: Long?, isAdult: Boolean, isUserProtected: Boolean): Boolean {
        if (uiState.parentalControlLevel != 1) {
            return false
        }
        if (!isAdult && !isUserProtected) {
            return false
        }
        val unlockedIds = uiState.unlockedCategoryIds[providerId] ?: emptySet()
        return categoryId == null || categoryId !in unlockedIds
    }

    LaunchedEffect(Unit) {
        searchFocusRequester.requestFocusSafely(tag = "SearchScreen", target = "Search field")
    }

    LaunchedEffect(initialQuery) {
        if (initialQuery.isNotBlank()) {
            viewModel.submitExternalQuery(initialQuery)
            focusManager.clearFocus()
        }
    }

    LaunchedEffect(showPinDialog) {
        if (!showPinDialog) {
            searchFocusRequester.requestFocusSafely(tag = "SearchScreen", target = "Search field")
        }
    }

    if (showPinDialog) {
        com.kaynanamtv.app.ui.components.dialogs.PinDialog(
            onDismissRequest = {
                showPinDialog = false
                pinError = null
                pendingChannel = null
                pendingMovie = null
                pendingSeries = null
                pinIsForProtectionToggle = false
                pendingProtectionCategoryId = null
                pendingProtectionContentType = null
                pendingProtectionProviderId = null
            },
            onPinEntered = { pin ->
                scope.launch {
                    if (viewModel.verifyPin(pin)) {
                        if (pinIsForProtectionToggle) {
                            val providerId = pendingProtectionProviderId
                            val catId = pendingProtectionCategoryId
                            val ct = pendingProtectionContentType
                            if (providerId != null && catId != null && ct != null) {
                                viewModel.toggleCategoryProtection(providerId, catId, ct, pendingProtectionCurrentlyProtected)
                            }
                            showPinDialog = false
                            pinError = null
                            pinIsForProtectionToggle = false
                            pendingProtectionCategoryId = null
                            pendingProtectionContentType = null
                            pendingProtectionProviderId = null
                        } else {
                            val pendingProvId = pendingChannel?.providerId ?: pendingMovie?.providerId ?: pendingSeries?.providerId
                            if (pendingProvId != null) {
                                pendingChannel?.categoryId?.let { viewModel.unlockCategory(pendingProvId, it) }
                                pendingMovie?.categoryId?.let { viewModel.unlockCategory(pendingProvId, it) }
                                pendingSeries?.categoryId?.let { viewModel.unlockCategory(pendingProvId, it) }
                            }
                            showPinDialog = false
                            pinError = null
                            pendingChannel?.let { onChannelClick(it) }
                            pendingMovie?.let { onMovieClick(it) }
                            pendingSeries?.let { onSeriesClick(it) }
                            pendingChannel = null
                            pendingMovie = null
                            pendingSeries = null
                        }
                    } else {
                        pinError = context.getString(R.string.search_incorrect_pin)
                    }
                }
            },
            error = pinError
        )
    }

    val selectedTabDescription = selectedStateLabel

    // ── Long-press actions dialog ─────────────────────────────────────
    if (showActionsDialog) {
        val actionsTitle = actionsChannel?.name ?: actionsMovie?.name ?: actionsSeries?.name ?: ""
        val actionsCategoryId = actionsChannel?.categoryId ?: actionsMovie?.categoryId ?: actionsSeries?.categoryId
        val actionsIsProtected = actionsChannel?.isUserProtected ?: actionsMovie?.isUserProtected ?: actionsSeries?.isUserProtected ?: false
        val actionsContentType = when {
            actionsChannel != null -> ContentType.LIVE
            actionsMovie != null -> ContentType.MOVIE
            else -> ContentType.SERIES
        }
        val actionsItemId = actionsChannel?.id ?: actionsMovie?.id ?: actionsSeries?.id ?: 0L
        val itemProviderId = actionsChannel?.providerId ?: actionsMovie?.providerId ?: actionsSeries?.providerId
        SearchItemActionsDialog(
            title = actionsTitle,
            isFavorite = actionsIsFavorite,
            isProtected = actionsIsProtected,
            hasCategoryId = actionsCategoryId != null,
            onDismiss = { showActionsDialog = false },
            onToggleFavorite = {
                if (itemProviderId != null) {
                    viewModel.toggleFavorite(itemProviderId, actionsItemId, actionsContentType, actionsIsFavorite)
                }
                actionsIsFavorite = !actionsIsFavorite
            },
            onHide = if (actionsCategoryId != null && itemProviderId != null) {
                {
                    viewModel.hideItemCategory(itemProviderId, actionsCategoryId, actionsContentType)
                    showActionsDialog = false
                }
            } else null,
            onToggleLock = if (actionsCategoryId != null && itemProviderId != null) {
                {
                    showActionsDialog = false
                    pendingProtectionCategoryId = actionsCategoryId
                    pendingProtectionContentType = actionsContentType
                    pendingProtectionCurrentlyProtected = actionsIsProtected
                    pendingProtectionProviderId = itemProviderId
                    pinIsForProtectionToggle = true
                    showPinDialog = true
                }
            } else null
        )
    }

    AppScreenScaffold(
        currentRoute = currentRoute,
        onNavigate = onNavigate,
        title = stringResource(R.string.search_title),
        subtitle = stringResource(R.string.search_screen_subtitle),
        navigationChrome = AppNavigationChrome.TopBar,
        compactHeader = true,
        showScreenHeader = false
    ) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(18.dp),
            contentPadding = PaddingValues(bottom = 32.dp)
        ) {
            item {
                SearchHeroPanel(
                    query = query,
                    selectedTab = selectedTab,
                    recentQueries = recentQueries,
                    totalResults = uiState.totalResults,
                    onQueryChange = viewModel::onQueryChange,
                    onSearch = {
                        viewModel.onSearchSubmitted()
                    },
                    onTabSelected = viewModel::onTabSelected,
                    onRecentQuerySelected = {
                        viewModel.onRecentQuerySelected(it)
                    },
                    onClearRecentQueries = viewModel::clearRecentQueries,
                    focusRequester = searchFocusRequester,
                    selectedStateLabel = selectedTabDescription
                )
            }

            val isTypingPending = query.trim().length >= 2 && (uiState.queryLength != query.trim().length || uiState.isLoading)
            when {
                !uiState.hasActiveProvider -> {
                    item {
                        SearchMessageState(
                            title = stringResource(R.string.search_no_provider_title),
                            subtitle = stringResource(R.string.search_no_provider_subtitle)
                        )
                    }
                }

                query.trim().length < 2 -> {
                    item {
                        SearchMessageState(
                            title = stringResource(R.string.search_ready_title),
                            subtitle = stringResource(R.string.search_type_to_search)
                        )
                    }
                }

                isTypingPending -> {
                    item {
                        SearchMessageState(
                            title = stringResource(R.string.search_loading_title),
                            subtitle = stringResource(R.string.search_loading_subtitle)
                        )
                    }
                }

                uiState.isEmpty && uiState.hasSearchError -> {
                    item {
                        SearchMessageState(
                            title = "Arama indeksi hazırlanıyor…",
                            subtitle = "İçerikler ve arama veritabanı indeksleniyor, lütfen birkaç saniye sonra tekrar deneyin."
                        )
                    }
                }

                uiState.isEmpty -> {
                    item {
                        SearchMessageState(
                            title = stringResource(R.string.search_no_results_title),
                            subtitle = stringResource(R.string.search_no_results, query)
                        )
                    }
                }

                else -> {
                    item {
                        SearchResultsSummaryRow(
                            uiState = uiState
                        )
                    }

                    if (selectedTab == SearchTab.ALL) {
                        if (uiState.channels.isNotEmpty()) {
                            item {
                                SearchResultRail(
                                    title = stringResource(R.string.search_live_tv),
                                    items = uiState.channels.take(18),
                                    keySelector = { it.id }
                                ) { channel ->
                                    val channelLocked = isLocked(
                                        providerId = channel.providerId,
                                        categoryId = channel.categoryId,
                                        isAdult = channel.isAdult,
                                        isUserProtected = channel.isUserProtected
                                    )
                                    SearchResultCardWrapper(channel.providerId, uiState.providerNames) {
                                        ChannelCard(
                                            channel = channel,
                                            isLocked = channelLocked,
                                            isRecording = channel.id in recordingChannelIds,
                                            isScheduledRecording = channel.id in scheduledChannelIds,
                                            onClick = {
                                                if (channelLocked) {
                                                    pendingChannel = channel
                                                    showPinDialog = true
                                                } else {
                                                    onChannelClick(channel)
                                                }
                                            },
                                            onLongClick = { showChannelActions(channel) }
                                        )
                                    }
                                }
                            }
                        }

                        if (uiState.movies.isNotEmpty()) {
                            item {
                                SearchResultRail(
                                    title = stringResource(R.string.search_movies),
                                    items = uiState.movies.take(18),
                                    keySelector = { it.id }
                                ) { movie ->
                                    val movieLocked = isLocked(
                                        providerId = movie.providerId,
                                        categoryId = movie.categoryId,
                                        isAdult = movie.isAdult,
                                        isUserProtected = movie.isUserProtected
                                    )
                                    SearchResultCardWrapper(movie.providerId, uiState.providerNames) {
                                        MovieCard(
                                            movie = movie,
                                            isLocked = movieLocked,
                                            onClick = {
                                                if (movieLocked) {
                                                    pendingMovie = movie
                                                    showPinDialog = true
                                                } else {
                                                    onMovieClick(movie)
                                                }
                                            },
                                            onLongClick = { showMovieActions(movie) }
                                        )
                                    }
                                }
                            }
                        }

                        if (uiState.series.isNotEmpty()) {
                            item {
                                SearchResultRail(
                                    title = stringResource(R.string.search_series),
                                    items = uiState.series.take(18),
                                    keySelector = { it.id }
                                ) { seriesItem ->
                                    val seriesLocked = isLocked(
                                        providerId = seriesItem.providerId,
                                        categoryId = seriesItem.categoryId,
                                        isAdult = seriesItem.isAdult,
                                        isUserProtected = seriesItem.isUserProtected
                                    )
                                    SearchResultCardWrapper(seriesItem.providerId, uiState.providerNames) {
                                        SeriesCard(
                                            series = seriesItem,
                                            isLocked = seriesLocked,
                                            onClick = {
                                                if (seriesLocked) {
                                                    pendingSeries = seriesItem
                                                    showPinDialog = true
                                                } else {
                                                    onSeriesClick(seriesItem)
                                                }
                                            },
                                            onLongClick = { showSeriesActions(seriesItem) }
                                        )
                                    }
                                }
                            }
                        }
                    } else {
                        item {
                            SectionHeader(
                                title = when (selectedTab) {
                                    SearchTab.ALL -> stringResource(R.string.search_all)
                                    SearchTab.LIVE -> stringResource(R.string.search_live_tv)
                                    SearchTab.MOVIES -> stringResource(R.string.search_movies)
                                    SearchTab.SERIES -> stringResource(R.string.search_series)
                                }
                            )
                        }

                        when (selectedTab) {
                            SearchTab.ALL -> Unit
                            SearchTab.LIVE -> items(channelRows, key = { row ->
                                row.joinToString("-") { it.id.toString() }
                            }) { row ->
                                SearchChannelGridRow(
                                    channels = row,
                                    recordingChannelIds = recordingChannelIds,
                                    scheduledChannelIds = scheduledChannelIds,
                                    isLocked = { channel ->
                                        isLocked(
                                            providerId = channel.providerId,
                                            categoryId = channel.categoryId,
                                            isAdult = channel.isAdult,
                                            isUserProtected = channel.isUserProtected
                                        )
                                    },
                                    onChannelClick = { channel, locked ->
                                        if (locked) {
                                            pendingChannel = channel
                                            showPinDialog = true
                                        } else {
                                            onChannelClick(channel)
                                        }
                                    },
                                    onChannelLongClick = { channel -> showChannelActions(channel) },
                                    providerNames = uiState.providerNames
                                )
                            }

                            SearchTab.MOVIES -> items(movieRows, key = { row ->
                                row.joinToString("-") { it.id.toString() }
                            }) { row ->
                                SearchMovieGridRow(
                                    movies = row,
                                    isLocked = { movie ->
                                        isLocked(
                                            providerId = movie.providerId,
                                            categoryId = movie.categoryId,
                                            isAdult = movie.isAdult,
                                            isUserProtected = movie.isUserProtected
                                        )
                                    },
                                    onMovieClick = { movie, locked ->
                                        if (locked) {
                                            pendingMovie = movie
                                            showPinDialog = true
                                        } else {
                                            onMovieClick(movie)
                                        }
                                    },
                                    onMovieLongClick = { movie -> showMovieActions(movie) },
                                    providerNames = uiState.providerNames
                                )
                            }

                            SearchTab.SERIES -> items(seriesRows, key = { row ->
                                row.joinToString("-") { it.id.toString() }
                            }) { row ->
                                SearchSeriesGridRow(
                                    seriesItems = row,
                                    isLocked = { seriesItem ->
                                        isLocked(
                                            providerId = seriesItem.providerId,
                                            categoryId = seriesItem.categoryId,
                                            isAdult = seriesItem.isAdult,
                                            isUserProtected = seriesItem.isUserProtected
                                        )
                                    },
                                    onSeriesClick = { seriesItem, locked ->
                                        if (locked) {
                                            pendingSeries = seriesItem
                                            showPinDialog = true
                                        } else {
                                            onSeriesClick(seriesItem)
                                        }
                                    },
                                    onSeriesLongClick = { seriesItem -> showSeriesActions(seriesItem) },
                                    providerNames = uiState.providerNames
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SearchHeroPanel(
    query: String,
    selectedTab: SearchTab,
    recentQueries: List<String>,
    totalResults: Int,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    onTabSelected: (SearchTab) -> Unit,
    onRecentQuerySelected: (String) -> Unit,
    onClearRecentQueries: () -> Unit,
    focusRequester: FocusRequester,
    selectedStateLabel: String
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(24.dp),
        colors = SurfaceDefaults.colors(containerColor = SurfaceElevated.copy(alpha = 0.92f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.search_title),
                        style = MaterialTheme.typography.headlineMedium,
                        color = TextPrimary,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        text = stringResource(R.string.search_command_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.widthIn(max = 640.dp)
                    )
                }

                SearchStatusCard(
                    title = if (query.length >= 2) {
                        stringResource(R.string.search_results_title, totalResults)
                    } else {
                        stringResource(R.string.search_ready_title)
                    },
                    body = if (query.length >= 2) {
                        stringResource(R.string.search_screen_subtitle)
                    } else {
                        stringResource(R.string.search_type_to_search)
                    },
                    modifier = Modifier.widthIn(min = 220.dp, max = 360.dp)
                )
            }

            SearchInput(
                value = query,
                onValueChange = onQueryChange,
                placeholder = stringResource(R.string.search_hint),
                focusRequester = focusRequester,
                onSearch = onSearch,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
            )

            LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(SearchTab.values().toList(), key = { it.name }) { tab ->
                    SearchPill(
                        text = stringResource(tab.titleRes),
                        selected = tab == selectedTab,
                        onClick = { onTabSelected(tab) },
                        modifier = Modifier.semantics {
                            selected = tab == selectedTab
                            if (tab == selectedTab) {
                                stateDescription = selectedStateLabel
                            }
                        }
                    )
                }
            }

            if (recentQueries.isNotEmpty()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.search_recent_title),
                        style = MaterialTheme.typography.labelLarge,
                        color = TextSecondary
                    )
                    LazyRow(
                        modifier = Modifier.weight(1f),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(recentQueries, key = { it }) { recentQuery ->
                            val recentQueryDescription = stringResource(R.string.a11y_recent_search, recentQuery)
                            SearchPill(
                                text = recentQuery,
                                selected = recentQuery.equals(query, ignoreCase = true),
                                onClick = { onRecentQuerySelected(recentQuery) },
                                modifier = Modifier.semantics {
                                    contentDescription = recentQueryDescription
                                    if (recentQuery.equals(query, ignoreCase = true)) {
                                        selected = true
                                        stateDescription = selectedStateLabel
                                    }
                                }
                            )
                        }
                    }
                    SearchPill(
                        text = stringResource(R.string.search_clear_history),
                        selected = false,
                        compact = true,
                        onClick = onClearRecentQueries
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SearchPill(
    text: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    TvClickableSurface(
        modifier = modifier,
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(CircleShape),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (selected) Primary.copy(alpha = 0.22f) else Surface.copy(alpha = 0.72f),
            focusedContainerColor = if (selected) Primary.copy(alpha = 0.30f) else SurfaceHighlight,
            contentColor = if (selected) Color.White else TextSecondary,
            focusedContentColor = TextPrimary
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                border = BorderStroke(
                    1.dp,
                    if (selected) Primary.copy(alpha = 0.65f) else FocusBorder.copy(alpha = 0.28f)
                ),
                shape = CircleShape
            ),
            focusedBorder = Border(
                border = BorderStroke(2.dp, FocusBorder),
                shape = CircleShape
            )
        )
    ) {
        Text(
            text = text,
            modifier = Modifier.padding(
                horizontal = if (compact) 12.dp else 16.dp,
                vertical = if (compact) 8.dp else 10.dp
            ),
            style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SearchStatusCard(
    title: String,
    body: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(18.dp),
        colors = SurfaceDefaults.colors(containerColor = Surface.copy(alpha = 0.78f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                color = TextPrimary
            )
            Text(
                text = body,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun <T : Any> SearchResultRail(
    title: String,
    items: List<T>,
    keySelector: (T) -> Any,
    itemContent: @Composable (T) -> Unit
) {
    CategoryRow(
        title = title,
        items = items,
        keySelector = keySelector
    ) { item ->
        itemContent(item)
    }
}

@Composable
private fun SearchResultCardWrapper(
    providerId: Long,
    providerNames: Map<Long, String>,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(modifier = modifier.wrapContentSize()) {
        content()
        providerNames[providerId]?.let { name ->
            Text(
                text = name,
                style = MaterialTheme.typography.labelSmall.copy(fontWeight = androidx.compose.ui.text.font.FontWeight.Bold),
                color = androidx.compose.ui.graphics.Color.White,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier
                    .padding(6.dp)
                    .background(Primary.copy(alpha = 0.85f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 6.dp, vertical = 2.dp)
            )
        }
    }
}

@Composable
private fun SearchChannelGridRow(
    channels: List<Channel>,
    recordingChannelIds: Set<Long> = emptySet(),
    scheduledChannelIds: Set<Long> = emptySet(),
    isLocked: (Channel) -> Boolean,
    onChannelClick: (Channel, Boolean) -> Unit,
    onChannelLongClick: (Channel) -> Unit,
    providerNames: Map<Long, String> = emptyMap()
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        channels.forEach { channel ->
            val locked = isLocked(channel)
            SearchResultCardWrapper(channel.providerId, providerNames) {
                ChannelCard(
                    channel = channel,
                    isLocked = locked,
                    isRecording = channel.id in recordingChannelIds,
                    isScheduledRecording = channel.id in scheduledChannelIds,
                    onClick = { onChannelClick(channel, locked) },
                    onLongClick = { onChannelLongClick(channel) }
                )
            }
        }
    }
}

@Composable
private fun SearchMovieGridRow(
    movies: List<Movie>,
    isLocked: (Movie) -> Boolean,
    onMovieClick: (Movie, Boolean) -> Unit,
    onMovieLongClick: (Movie) -> Unit,
    providerNames: Map<Long, String> = emptyMap()
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        movies.forEach { movie ->
            val locked = isLocked(movie)
            SearchResultCardWrapper(movie.providerId, providerNames) {
                MovieCard(
                    movie = movie,
                    isLocked = locked,
                    onClick = { onMovieClick(movie, locked) },
                    onLongClick = { onMovieLongClick(movie) }
                )
            }
        }
    }
}

@Composable
private fun SearchSeriesGridRow(
    seriesItems: List<Series>,
    isLocked: (Series) -> Boolean,
    onSeriesClick: (Series, Boolean) -> Unit,
    onSeriesLongClick: (Series) -> Unit,
    providerNames: Map<Long, String> = emptyMap()
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        seriesItems.forEach { seriesItem ->
            val locked = isLocked(seriesItem)
            SearchResultCardWrapper(seriesItem.providerId, providerNames) {
                SeriesCard(
                    series = seriesItem,
                    isLocked = locked,
                    onClick = { onSeriesClick(seriesItem, locked) },
                    onLongClick = { onSeriesLongClick(seriesItem) }
                )
            }
        }
    }
}


@Composable
fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = Primary,
        modifier = Modifier
            .padding(vertical = 8.dp)
            .semantics { heading() }
    )
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SearchResultsSummaryRow(
    uiState: SearchUiState
) {
    val countsSummary = listOf(
        stringResource(R.string.search_results_count, stringResource(R.string.search_live_tv), uiState.channels.size),
        stringResource(R.string.search_results_count, stringResource(R.string.search_movies), uiState.movies.size),
        stringResource(R.string.search_results_count, stringResource(R.string.search_series), uiState.series.size)
    ).joinToString("  •  ")
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .semantics { liveRegion = LiveRegionMode.Polite },
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = stringResource(R.string.search_results_title, uiState.totalResults),
            style = MaterialTheme.typography.titleMedium,
            color = OnSurface,
            modifier = Modifier.semantics { heading() }
        )
        Text(
            text = countsSummary,
            style = MaterialTheme.typography.bodySmall,
            color = TextSecondary,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun SearchMessageState(
    title: String,
    subtitle: String
) {
    TvEmptyState(
        title = title,
        subtitle = subtitle,
        modifier = Modifier.semantics { liveRegion = LiveRegionMode.Polite }
    )
}

// ── Long-press actions dialog ─────────────────────────────────────────────

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SearchItemActionsDialog(
    title: String,
    isFavorite: Boolean,
    isProtected: Boolean,
    hasCategoryId: Boolean,
    onDismiss: () -> Unit,
    onToggleFavorite: () -> Unit,
    onHide: (() -> Unit)?,
    onToggleLock: (() -> Unit)?
) {
    // Ghost-click debounce: ignore select key-up inherited from the long-press gesture
    var canInteract by remember { mutableStateOf(false) }
    val firstFocusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        runCatching { firstFocusRequester.requestFocus() }
        delay(500)
        canInteract = true
    }

    val safeDismiss = { if (canInteract) onDismiss() }

    Dialog(
        onDismissRequest = safeDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            shape = RoundedCornerShape(20.dp),
            colors = SurfaceDefaults.colors(containerColor = AppColors.SurfaceElevated),
            modifier = Modifier
                .width(360.dp)
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.padding(20.dp)) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = AppColors.TextPrimary,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(
                        onClick = safeDismiss,
                        modifier = Modifier.mouseClickable(onClick = safeDismiss)
                    ) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.search_actions_dismiss))
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    // ── Favorite toggle ──────────────────────────────
                    SearchActionButton(
                        icon = Icons.Default.Star,
                        label = if (isFavorite) stringResource(R.string.search_actions_remove_favorite)
                                else stringResource(R.string.search_actions_add_favorite),
                        isActive = isFavorite,
                        focusRequester = firstFocusRequester,
                        onClick = { if (canInteract) onToggleFavorite() }
                    )

                    // ── Hide category ────────────────────────────────
                    if (onHide != null) {
                        SearchActionButton(
                            icon = Icons.Default.Close,
                            label = stringResource(R.string.search_actions_hide_category),
                            isActive = false,
                            onClick = { if (canInteract) onHide() }
                        )
                    } else {
                        SearchActionButton(
                            icon = Icons.Default.Close,
                            label = stringResource(R.string.search_actions_hide_no_category),
                            isActive = false,
                            enabled = false,
                            onClick = {}
                        )
                    }

                    // ── Parental lock toggle ─────────────────────────
                    if (onToggleLock != null) {
                        SearchActionButton(
                            icon = Icons.Default.Lock,
                            label = if (isProtected) stringResource(R.string.search_actions_unlock)
                                    else stringResource(R.string.search_actions_lock),
                            isActive = isProtected,
                            onClick = { if (canInteract) onToggleLock() }
                        )
                    } else {
                        SearchActionButton(
                            icon = Icons.Default.Lock,
                            label = stringResource(R.string.search_actions_lock_no_category),
                            isActive = false,
                            enabled = false,
                            onClick = {}
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalTvMaterial3Api::class)
@Composable
private fun SearchActionButton(
    icon: ImageVector,
    label: String,
    isActive: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    focusRequester: FocusRequester? = null,
    enabled: Boolean = true
) {
    var isFocused by remember { mutableStateOf(false) }
    val baseModifier = modifier
        .fillMaxWidth()
        .onFocusChanged { isFocused = it.isFocused }
        .then(
            if (focusRequester != null) Modifier.focusRequester(focusRequester) else Modifier
        )
        .border(
            width = if (isFocused) 2.dp else 0.dp,
            color = if (isFocused) AppColors.Focus else Color.Transparent,
            shape = RoundedCornerShape(12.dp)
        )
        .mouseClickable(onClick = onClick)

    Button(
        onClick = onClick,
        enabled = enabled,
        modifier = baseModifier,
        colors = ButtonDefaults.colors(
            containerColor = when {
                !enabled -> AppColors.Surface.copy(alpha = 0.3f)
                isFocused -> AppColors.Focus
                isActive -> AppColors.Warning.copy(alpha = 0.85f)
                else -> AppColors.Brand.copy(alpha = 0.70f)
            },
            contentColor = if (!enabled) AppColors.TextSecondary else Color.Black,
            disabledContainerColor = AppColors.Surface.copy(alpha = 0.3f),
            disabledContentColor = AppColors.TextSecondary
        ),
        shape = ButtonDefaults.shape(shape = RoundedCornerShape(12.dp))
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            modifier = Modifier.size(20.dp)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}
