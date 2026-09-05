package com.kaynanamtv.app.ui.screens.vod

import com.kaynanamtv.domain.model.LibraryFilterType
import com.kaynanamtv.domain.model.LibrarySortBy
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

inline fun <State> selectVodCategory(
    categoryName: String?,
    selectedCategoryLoadLimit: MutableStateFlow<Int>,
    selectedLibraryFilterType: MutableStateFlow<LibraryFilterType>,
    selectedLibrarySortBy: MutableStateFlow<LibrarySortBy>,
    uiState: MutableStateFlow<State>,
    resetFilterOnCategoryChange: Boolean = true,
    crossinline getSelectedCategory: (State) -> String?,
    crossinline getSelectedFilterType: (State) -> LibraryFilterType,
    crossinline getSelectedSortBy: (State) -> LibrarySortBy,
    crossinline updateState: State.(
        selectedCategory: String?,
        filterType: LibraryFilterType,
        sortBy: LibrarySortBy,
        isLoadingSelectedCategory: Boolean
    ) -> State
) {
    val previousCategory = getSelectedCategory(uiState.value)
    val previousFilter = getSelectedFilterType(uiState.value)
    val previousSort = getSelectedSortBy(uiState.value)
    if (resetFilterOnCategoryChange) {
        selectedLibraryFilterType.value = LibraryFilterType.ALL
        selectedLibrarySortBy.value = LibrarySortBy.LIBRARY
    }
    val targetFilter = selectedLibraryFilterType.value
    val targetSort = selectedLibrarySortBy.value
    if (previousCategory == categoryName && previousFilter == targetFilter && previousSort == targetSort) {
        return
    }
    selectedCategoryLoadLimit.value = VodBrowseDefaults.SELECTED_CATEGORY_PAGE_SIZE
    val isChangingCategory = previousCategory != categoryName
    uiState.update { state ->
        state.updateState(
            categoryName,
            targetFilter,
            targetSort,
            if (isChangingCategory) categoryName != null else false
        )
    }
}

fun incrementVodSelectedCategoryLoadLimit(
    canLoadMore: Boolean,
    selectedCategoryLoadLimit: MutableStateFlow<Int>
) {
    if (!canLoadMore) return
    selectedCategoryLoadLimit.update { it + VodBrowseDefaults.SELECTED_CATEGORY_PAGE_SIZE }
}

inline fun <State> setVodSearchQuery(
    query: String,
    searchQuery: MutableStateFlow<String>,
    uiState: MutableStateFlow<State>,
    crossinline updateState: State.(String) -> State
) {
    searchQuery.value = query
    uiState.update { state -> state.updateState(query) }
}

inline fun <State> setVodLibraryFilterType(
    filterType: LibraryFilterType,
    selectedLibraryFilterType: MutableStateFlow<LibraryFilterType>,
    selectedCategoryLoadLimit: MutableStateFlow<Int>,
    uiState: MutableStateFlow<State>,
    crossinline hasSelectedCategory: (State) -> Boolean,
    crossinline updateState: State.(
        filterType: LibraryFilterType,
        isLoadingSelectedCategory: Boolean
    ) -> State
) {
    selectedLibraryFilterType.value = filterType
    selectedCategoryLoadLimit.value = VodBrowseDefaults.SELECTED_CATEGORY_PAGE_SIZE
    uiState.update { state ->
        state.updateState(filterType, hasSelectedCategory(state))
    }
}

inline fun <State> setVodLibrarySortBy(
    sortBy: LibrarySortBy,
    selectedLibrarySortBy: MutableStateFlow<LibrarySortBy>,
    selectedCategoryLoadLimit: MutableStateFlow<Int>,
    uiState: MutableStateFlow<State>,
    crossinline hasSelectedCategory: (State) -> Boolean,
    crossinline updateState: State.(
        sortBy: LibrarySortBy,
        isLoadingSelectedCategory: Boolean
    ) -> State
) {
    selectedLibrarySortBy.value = sortBy
    selectedCategoryLoadLimit.value = VodBrowseDefaults.SELECTED_CATEGORY_PAGE_SIZE
    uiState.update { state ->
        state.updateState(sortBy, hasSelectedCategory(state))
    }
}

/**
 * Returns a compact summary label for the currently active filter and/or sort
 * (e.g. "Favorites · Rating"), or null when both are at their defaults.
 * This is used to decorate the "Filter & Sort" action chip so the user can
 * tell at a glance that a non-default browse mode is in effect.
 */
fun vodActiveFilterSortDetail(filter: LibraryFilterType, sort: LibrarySortBy): String? {
    val filterLabel = when (filter) {
        LibraryFilterType.ALL -> null
        LibraryFilterType.FAVORITES -> "Favoriler"
        LibraryFilterType.IN_PROGRESS -> "Devam Et"
        LibraryFilterType.UNWATCHED -> "İzlenmeyenler"
        LibraryFilterType.RECENTLY_UPDATED -> "Son Eklenenler"
        LibraryFilterType.TOP_RATED -> "En Beğenilenler"
    }
    val sortLabel = when (sort) {
        LibrarySortBy.LIBRARY -> null
        LibrarySortBy.TITLE -> "A-Z"
        LibrarySortBy.RELEASE -> "En Yeni"
        LibrarySortBy.UPDATED -> "Son Güncellenen"
        LibrarySortBy.RATING -> "Puan"
        LibrarySortBy.WATCH_COUNT -> "Son İzlenenler"
    }
    return listOfNotNull(filterLabel, sortLabel).joinToString(" · ").ifEmpty { null }
}