@file:OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)

package com.kaynanamtv.app.ui.screens.settings

import android.app.Application
import com.kaynanamtv.app.R
import com.kaynanamtv.data.local.dao.ProgramDao
import com.kaynanamtv.domain.model.Category
import com.kaynanamtv.domain.model.CategorySortMode
import com.kaynanamtv.domain.model.ContentType
import com.kaynanamtv.domain.model.Provider
import com.kaynanamtv.domain.model.ProviderType
import com.kaynanamtv.domain.model.VodSyncMode
import com.kaynanamtv.domain.repository.CategoryRepository
import com.kaynanamtv.domain.repository.ChannelRepository
import com.kaynanamtv.domain.repository.MovieRepository
import com.kaynanamtv.domain.repository.ProviderRepository
import com.kaynanamtv.domain.repository.SeriesRepository
import com.kaynanamtv.domain.repository.SyncMetadataRepository
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

internal fun observeProviderDiagnostics(
    providerRepository: ProviderRepository,
    syncMetadataRepository: SyncMetadataRepository,
    channelRepository: ChannelRepository,
    movieRepository: MovieRepository,
    seriesRepository: SeriesRepository,
    programDao: ProgramDao,
    application: Application
): Flow<Map<Long, ProviderDiagnosticsUiModel>> {
    return providerRepository.getProviders()
        .flatMapLatest { providers ->
            if (providers.isEmpty()) {
                flowOf(emptyMap())
            } else {
                combine(
                    providers.map { provider ->
                        combine(
                            syncMetadataRepository.observeMetadata(provider.id),
                            channelRepository.getChannelCount(provider.id),
                            movieRepository.getLibraryCount(provider.id),
                            seriesRepository.getLibraryCount(provider.id),
                            programDao.observeCountByProvider(provider.id)
                        ) { metadata, liveCount, movieCount, seriesCount, epgCount ->
                            provider.id to ProviderDiagnosticsUiModel(
                                lastSyncStatus = metadata?.lastSyncStatus ?: "NONE",
                                lastLiveSync = metadata?.lastLiveSync ?: 0L,
                                lastLiveSuccess = metadata?.lastLiveSuccess ?: 0L,
                                lastMovieSync = metadata?.lastMovieSync ?: 0L,
                                lastMovieAttempt = metadata?.lastMovieAttempt ?: 0L,
                                lastMovieSuccess = metadata?.lastMovieSuccess ?: 0L,
                                lastMoviePartial = metadata?.lastMoviePartial ?: 0L,
                                lastSeriesSync = metadata?.lastSeriesSync ?: 0L,
                                lastSeriesSuccess = metadata?.lastSeriesSuccess ?: 0L,
                                lastEpgSync = metadata?.lastEpgSync ?: 0L,
                                lastEpgSuccess = metadata?.lastEpgSuccess ?: 0L,
                                liveCount = liveCount,
                                movieCount = movieCount,
                                seriesCount = seriesCount,
                                epgCount = epgCount,
                                movieSyncMode = metadata?.movieSyncMode ?: VodSyncMode.UNKNOWN,
                                movieWarningsCount = metadata?.movieWarningsCount ?: 0,
                                movieCatalogStale = metadata?.movieCatalogStale ?: false,
                                liveSequentialFailuresRemembered = metadata?.liveSequentialFailuresRemembered ?: false,
                                liveHealthySyncStreak = metadata?.liveHealthySyncStreak ?: 0,
                                movieParallelFailuresRemembered = metadata?.movieParallelFailuresRemembered ?: false,
                                movieHealthySyncStreak = metadata?.movieHealthySyncStreak ?: 0,
                                seriesSequentialFailuresRemembered = metadata?.seriesSequentialFailuresRemembered ?: false,
                                seriesHealthySyncStreak = metadata?.seriesHealthySyncStreak ?: 0,
                                capabilitySummary = buildCapabilitySummary(application, provider),
                                sourceLabel = provider.sourceLabel(),
                                expirySummary = provider.expirySummary(),
                                connectionSummary = "${provider.maxConnections} bağlantı",
                                archiveSummary = provider.archiveSummary()
                            )
                        }
                    }
                ) { pairs ->
                    pairs.toMap()
                }
            }
        }
}

internal fun observeCategoryManagement(
    activeProviderIdFlow: Flow<Long?>,
    preferencesRepository: com.kaynanamtv.data.preferences.PreferencesRepository,
    categoryRepository: CategoryRepository
): Flow<CategoryManagementSnapshot> {
    return activeProviderIdFlow.flatMapLatest { providerId ->
        if (providerId == null) {
            flowOf(CategoryManagementSnapshot())
        } else {
            combine(
                observeCategorySortModes(providerId, preferencesRepository),
                categoryRepository.getCategories(providerId),
                observeHiddenCategoryIdsByType(providerId, preferencesRepository)
            ) { sortModes, categories, hiddenByType ->
                CategoryManagementSnapshot(
                    categorySortModes = sortModes,
                    hiddenCategories = categories
                        .filter { category -> category.id in hiddenByType[category.type].orEmpty() }
                        .sortedWith(compareBy<Category>({ it.type.ordinal }, { it.name.lowercase() }))
                )
            }
        }
    }
}

private fun observeCategorySortModes(
    providerId: Long,
    preferencesRepository: com.kaynanamtv.data.preferences.PreferencesRepository
): Flow<Map<ContentType, CategorySortMode>> {
    return combine(
        preferencesRepository.getCategorySortMode(providerId, ContentType.LIVE),
        preferencesRepository.getCategorySortMode(providerId, ContentType.MOVIE),
        preferencesRepository.getCategorySortMode(providerId, ContentType.SERIES)
    ) { liveSort, movieSort, seriesSort ->
        mapOf(
            ContentType.LIVE to liveSort,
            ContentType.MOVIE to movieSort,
            ContentType.SERIES to seriesSort
        )
    }
}

private fun observeHiddenCategoryIdsByType(
    providerId: Long,
    preferencesRepository: com.kaynanamtv.data.preferences.PreferencesRepository
): Flow<Map<ContentType, Set<Long>>> {
    return combine(
        preferencesRepository.getHiddenCategoryIds(providerId, ContentType.LIVE),
        preferencesRepository.getHiddenCategoryIds(providerId, ContentType.MOVIE),
        preferencesRepository.getHiddenCategoryIds(providerId, ContentType.SERIES)
    ) { hiddenLive, hiddenMovies, hiddenSeries ->
        mapOf(
            ContentType.LIVE to hiddenLive,
            ContentType.MOVIE to hiddenMovies,
            ContentType.SERIES to hiddenSeries
        )
    }
}

private fun buildCapabilitySummary(application: Application, provider: Provider): String {
    return when (provider.type) {
        ProviderType.XTREAM_CODES -> {
            if (provider.epgUrl.isNotBlank()) {
                application.getString(R.string.settings_capability_xtream_with_epg)
            } else {
                application.getString(R.string.settings_capability_xtream_without_epg)
            }
        }
        ProviderType.M3U -> {
            if (provider.epgUrl.isNotBlank()) {
                application.getString(R.string.settings_capability_m3u_with_epg)
            } else {
                application.getString(R.string.settings_capability_m3u_without_epg)
            }
        }
        ProviderType.STALKER_PORTAL -> {
            if (provider.epgUrl.isNotBlank()) {
                "MAC kimlik doğrulamalı portal kataloğu, XMLTV içe aktarımı ve isteğe bağlı oynatma bağlantısı çözümü."
            } else {
                "MAC kimlik doğrulamalı portal kataloğu ve isteğe bağlı rehber/oynatma çözümü."
            }
        }
        ProviderType.JELLYFIN -> {
            if (provider.epgUrl.isNotBlank()) {
                "Doğrudan akışlı Jellyfin kataloğu, isteğe bağlı XMLTV içe aktarımı."
            } else {
                "Doğrudan akışlı Jellyfin kataloğu ve sunucudan alınan rehber verileri."
            }
        }
    }
}

private fun Provider.sourceLabel(): String = when (type) {
    ProviderType.XTREAM_CODES -> "Xtream Codes"
    ProviderType.M3U -> "M3U Çalma Listesi"
    ProviderType.STALKER_PORTAL -> "Stalker/MAG Portalı"
    ProviderType.JELLYFIN -> "Jellyfin"
}

private fun Provider.expirySummary(): String {
    val expirationDate = expirationDate
    return when {
        expirationDate == null -> "Sona erme tarihi bilinmiyor"
        expirationDate == Long.MAX_VALUE -> "Sona erme tarihi bildirilmedi"
        expirationDate < System.currentTimeMillis() -> "Süresi doldu"
        else -> {
            val formatter = SimpleDateFormat("d MMM yyyy", Locale.getDefault())
            "Sona erme tarihi: ${formatter.format(Date(expirationDate))}"
        }
    }
}

private fun Provider.archiveSummary(): String = when (type) {
    ProviderType.XTREAM_CODES -> "Geri alma (Catch-up) desteği, yayın sağlayıcınızın arşiv işaretlerine ve tekrar akış kimliklerine bağlıdır."
    ProviderType.M3U -> {
        if (epgUrl.isBlank()) {
            "M3U geri alma desteği rehber kapsamı olmadan sınırlıdır."
        } else {
            "M3U geri alma desteği kanal şablonlarına ve rehber uyumuna bağlıdır."
        }
    }
    ProviderType.STALKER_PORTAL -> {
        if (epgUrl.isBlank()) {
            "Stalker geri alma desteği portal desteğine bağlıdır; rehber portal verilerine dayanır."
        } else {
            "Stalker geri alma desteği isteğe bağlı XMLTV kapsamı ile birlikte portal desteğine bağlıdır."
        }
    }
    ProviderType.JELLYFIN -> {
        if (epgUrl.isBlank()) {
            "Jellyfin geri alma desteği sunucu rehber verilerine bağlıdır."
        } else {
            "Jellyfin geri alma desteği sunucu rehber verilerini isteğe bağlı XMLTV kapsamı ile birleştirir."
        }
    }
}
