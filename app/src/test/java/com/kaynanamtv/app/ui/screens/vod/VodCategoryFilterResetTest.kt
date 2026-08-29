package com.kaynanamtv.app.ui.screens.vod

import com.google.common.truth.Truth.assertThat
import com.kaynanamtv.domain.model.LibraryFilterType
import com.kaynanamtv.domain.model.LibrarySortBy
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Test

class VodCategoryFilterResetTest {

    data class DummyVodState(
        val selectedCategory: String? = null,
        val selectedFilterType: LibraryFilterType = LibraryFilterType.ALL,
        val selectedSortBy: LibrarySortBy = LibrarySortBy.LIBRARY,
        val isLoading: Boolean = false
    )

    @Test
    fun `TEST 1 - opening continue watching sets IN_PROGRESS filter`() {
        val selectedCategoryLoadLimit = MutableStateFlow(20)
        val selectedLibraryFilterType = MutableStateFlow(LibraryFilterType.ALL)
        val selectedLibrarySortBy = MutableStateFlow(LibrarySortBy.LIBRARY)
        val uiState = MutableStateFlow(DummyVodState())

        // Continue watching shortcut executes:
        selectedLibraryFilterType.value = LibraryFilterType.IN_PROGRESS
        selectedLibrarySortBy.value = LibrarySortBy.LIBRARY
        selectVodCategory(
            categoryName = VodBrowseDefaults.FULL_LIBRARY_CATEGORY,
            selectedCategoryLoadLimit = selectedCategoryLoadLimit,
            selectedLibraryFilterType = selectedLibraryFilterType,
            selectedLibrarySortBy = selectedLibrarySortBy,
            uiState = uiState,
            resetFilterOnCategoryChange = false,
            getSelectedCategory = { it.selectedCategory }
        ) { category, filter, sort, loading ->
            copy(selectedCategory = category, selectedFilterType = filter, selectedSortBy = sort, isLoading = loading)
        }

        assertThat(uiState.value.selectedCategory).isEqualTo(VodBrowseDefaults.FULL_LIBRARY_CATEGORY)
        assertThat(uiState.value.selectedFilterType).isEqualTo(LibraryFilterType.IN_PROGRESS)
        assertThat(selectedLibraryFilterType.value).isEqualTo(LibraryFilterType.IN_PROGRESS)
    }

    @Test
    fun `TEST 2 - navigating from continue watching to normal category resets filter to ALL`() {
        val selectedCategoryLoadLimit = MutableStateFlow(20)
        val selectedLibraryFilterType = MutableStateFlow(LibraryFilterType.IN_PROGRESS)
        val selectedLibrarySortBy = MutableStateFlow(LibrarySortBy.LIBRARY)
        val uiState = MutableStateFlow(
            DummyVodState(
                selectedCategory = VodBrowseDefaults.FULL_LIBRARY_CATEGORY,
                selectedFilterType = LibraryFilterType.IN_PROGRESS
            )
        )

        // User clicks on "Aksiyon" category (normal category transition)
        selectVodCategory(
            categoryName = "Aksiyon",
            selectedCategoryLoadLimit = selectedCategoryLoadLimit,
            selectedLibraryFilterType = selectedLibraryFilterType,
            selectedLibrarySortBy = selectedLibrarySortBy,
            uiState = uiState,
            resetFilterOnCategoryChange = true,
            getSelectedCategory = { it.selectedCategory }
        ) { category, filter, sort, loading ->
            copy(selectedCategory = category, selectedFilterType = filter, selectedSortBy = sort, isLoading = loading)
        }

        assertThat(uiState.value.selectedCategory).isEqualTo("Aksiyon")
        assertThat(uiState.value.selectedFilterType).isEqualTo(LibraryFilterType.ALL)
        assertThat(selectedLibraryFilterType.value).isEqualTo(LibraryFilterType.ALL)
    }

    @Test
    fun `TEST 3 - ALL and other filters are selectable after category change`() {
        val selectedCategoryLoadLimit = MutableStateFlow(20)
        val selectedLibraryFilterType = MutableStateFlow(LibraryFilterType.ALL)
        val uiState = MutableStateFlow(DummyVodState(selectedCategory = "Aksiyon", selectedFilterType = LibraryFilterType.ALL))

        // User manually selects FAVORITES inside "Aksiyon"
        setVodLibraryFilterType(
            filterType = LibraryFilterType.FAVORITES,
            selectedLibraryFilterType = selectedLibraryFilterType,
            selectedCategoryLoadLimit = selectedCategoryLoadLimit,
            uiState = uiState,
            hasSelectedCategory = { it.selectedCategory != null }
        ) { filter, loading ->
            copy(selectedFilterType = filter, isLoading = loading)
        }

        assertThat(uiState.value.selectedFilterType).isEqualTo(LibraryFilterType.FAVORITES)
        assertThat(selectedLibraryFilterType.value).isEqualTo(LibraryFilterType.FAVORITES)
    }

    @Test
    fun `TEST 4 - category A to category B transition resets filter to ALL`() {
        val selectedCategoryLoadLimit = MutableStateFlow(20)
        val selectedLibraryFilterType = MutableStateFlow(LibraryFilterType.TOP_RATED)
        val selectedLibrarySortBy = MutableStateFlow(LibrarySortBy.RATING)
        val uiState = MutableStateFlow(
            DummyVodState(
                selectedCategory = "Aksiyon",
                selectedFilterType = LibraryFilterType.TOP_RATED,
                selectedSortBy = LibrarySortBy.RATING
            )
        )

        // User changes from "Aksiyon" to "Komedi"
        selectVodCategory(
            categoryName = "Komedi",
            selectedCategoryLoadLimit = selectedCategoryLoadLimit,
            selectedLibraryFilterType = selectedLibraryFilterType,
            selectedLibrarySortBy = selectedLibrarySortBy,
            uiState = uiState,
            resetFilterOnCategoryChange = true,
            getSelectedCategory = { it.selectedCategory }
        ) { category, filter, sort, loading ->
            copy(selectedCategory = category, selectedFilterType = filter, selectedSortBy = sort, isLoading = loading)
        }

        assertThat(uiState.value.selectedCategory).isEqualTo("Komedi")
        assertThat(uiState.value.selectedFilterType).isEqualTo(LibraryFilterType.ALL)
        assertThat(selectedLibraryFilterType.value).isEqualTo(LibraryFilterType.ALL)
        assertThat(selectedLibrarySortBy.value).isEqualTo(LibrarySortBy.LIBRARY)
    }

    @Test
    fun `TEST 5 - manually selected filter inside same category is preserved across re-clicks`() {
        val selectedCategoryLoadLimit = MutableStateFlow(20)
        val selectedLibraryFilterType = MutableStateFlow(LibraryFilterType.RECENTLY_UPDATED)
        val selectedLibrarySortBy = MutableStateFlow(LibrarySortBy.RELEASE)
        val uiState = MutableStateFlow(
            DummyVodState(
                selectedCategory = "Aksiyon",
                selectedFilterType = LibraryFilterType.RECENTLY_UPDATED,
                selectedSortBy = LibrarySortBy.RELEASE
            )
        )

        // User re-selects "Aksiyon" (same category)
        selectVodCategory(
            categoryName = "Aksiyon",
            selectedCategoryLoadLimit = selectedCategoryLoadLimit,
            selectedLibraryFilterType = selectedLibraryFilterType,
            selectedLibrarySortBy = selectedLibrarySortBy,
            uiState = uiState,
            resetFilterOnCategoryChange = true,
            getSelectedCategory = { it.selectedCategory }
        ) { category, filter, sort, loading ->
            copy(selectedCategory = category, selectedFilterType = filter, selectedSortBy = sort, isLoading = loading)
        }

        // Must NOT reset because category did not change
        assertThat(uiState.value.selectedCategory).isEqualTo("Aksiyon")
        assertThat(uiState.value.selectedFilterType).isEqualTo(LibraryFilterType.RECENTLY_UPDATED)
        assertThat(selectedLibraryFilterType.value).isEqualTo(LibraryFilterType.RECENTLY_UPDATED)
    }
}
