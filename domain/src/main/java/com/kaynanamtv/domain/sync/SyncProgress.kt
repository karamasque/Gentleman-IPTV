package com.kaynanamtv.domain.sync

/**
 * Snapshot immuable de la progression d'un cycle de synchronisation catalogue.
 */
data class SyncProgress(
    val section: Section,
    val current: Int,
    val total: Int,
    val currentLabel: String,
    val itemsIndexed: Int,
    val onboardingProgress: FullCatalogOnboardingProgress? = null
)

/**
 * Section du catalogue en cours de synchronisation.
 */
enum class Section {
    LIVE,
    VOD,
    SERIES
}

/**
 * Etat d'onboarding autonome pour chaque section (Canlı TV, Filmler, Diziler).
 */
enum class CatalogSectionState {
    PENDING,
    LOADING,
    PARTIAL_READY,
    READY,
    FAILED;

    val isUsable: Boolean
        get() = this == READY
}

data class SectionOnboardingStatus(
    val state: CatalogSectionState = CatalogSectionState.PENDING,
    val itemsIndexed: Int = 0,
    val totalItems: Int = 0,
    val message: String = "",
    val networkMs: Long = 0L,
    val parseMs: Long = 0L,
    val dbMs: Long = 0L,
    val firstUsableMs: Long = 0L,
    val fullReadyMs: Long = 0L,
    val requestCount: Int = 0
)

data class FullCatalogOnboardingProgress(
    val serverAuthVerified: Boolean = false,
    val live: SectionOnboardingStatus = SectionOnboardingStatus(),
    val movies: SectionOnboardingStatus = SectionOnboardingStatus(),
    val series: SectionOnboardingStatus = SectionOnboardingStatus()
) {
    val isLiveFullReady: Boolean
        get() = live.state == CatalogSectionState.READY

    val isAnyCatalogReady: Boolean
        get() = isLiveFullReady

    val isAllCatalogsFinished: Boolean
        get() = (live.state == CatalogSectionState.READY || live.state == CatalogSectionState.FAILED) &&
                (movies.state == CatalogSectionState.READY || movies.state == CatalogSectionState.FAILED) &&
                (series.state == CatalogSectionState.READY || series.state == CatalogSectionState.FAILED)
}
