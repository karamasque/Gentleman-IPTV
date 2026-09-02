package com.kaynanamtv.data.repository

import com.kaynanamtv.data.local.DatabaseTransactionRunner
import com.kaynanamtv.data.local.dao.EpisodeDao
import com.kaynanamtv.data.local.dao.MovieDao
import com.kaynanamtv.data.local.dao.PlaybackHistoryDao
import com.kaynanamtv.data.local.dao.SeriesDao
import com.kaynanamtv.data.mapper.toDomain
import com.kaynanamtv.data.mapper.toEntity
import com.kaynanamtv.data.local.entity.PlaybackHistoryEntity
import com.kaynanamtv.domain.model.ContentType
import com.kaynanamtv.domain.model.PlaybackHistory
import com.kaynanamtv.domain.model.PlaybackWatchedStatus
import com.kaynanamtv.domain.model.Result
import com.kaynanamtv.domain.repository.PlaybackHistoryRepository
import com.kaynanamtv.domain.util.DEFAULT_PLAYBACK_COMPLETION_THRESHOLD
import com.kaynanamtv.domain.util.isPlaybackComplete
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject
import javax.inject.Singleton
import com.kaynanamtv.data.preferences.PreferencesRepository
import com.kaynanamtv.data.remote.xtream.XtreamUrlFactory

@Singleton
class PlaybackHistoryRepositoryImpl @Inject constructor(
    private val dao: PlaybackHistoryDao,
    private val preferencesRepository: PreferencesRepository,
    private val movieDao: MovieDao,
    private val episodeDao: EpisodeDao,
    private val seriesDao: SeriesDao,
    private val transactionRunner: DatabaseTransactionRunner
) : PlaybackHistoryRepository {
    private val repositoryScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pendingResumeUpdates = ConcurrentHashMap<PlaybackKey, PlaybackHistory>()
    private val pendingResumeUpdatesState = MutableStateFlow<Map<PlaybackKey, PlaybackHistory>>(emptyMap())
    private val isIncognito: StateFlow<Boolean> =
        preferencesRepository.isIncognitoMode
            .stateIn(repositoryScope, SharingStarted.Eagerly, false)

    init {
        repositoryScope.launch {
            while (true) {
                delay(RESUME_POSITION_FLUSH_INTERVAL_MS)
                flushPendingResumeUpdates()
            }
        }
    }

    override fun getRecentlyWatched(limit: Int): Flow<List<PlaybackHistory>> {
        return mergedRecentHistory(
            persisted = dao.getRecentlyWatched(limit).map { list -> list.map { it.toDomain() } },
            limit = limit
        ) { true }
    }

    override fun getRecentlyWatchedByProvider(providerId: Long, limit: Int): Flow<List<PlaybackHistory>> {
        return mergedRecentHistory(
            persisted = dao.getRecentlyWatchedByProvider(providerId, limit).map { list -> list.map { it.toDomain() } },
            limit = limit
        ) { history -> history.providerId == providerId }
    }

    override fun getRecentlyWatchedByProviders(providerIds: Set<Long>, limit: Int): Flow<List<PlaybackHistory>> {
        if (providerIds.isEmpty()) {
            return kotlinx.coroutines.flow.flowOf(emptyList())
        }
        return mergedRecentHistory(
            persisted = dao.getRecentlyWatchedByProviders(providerIds, limit).map { list -> list.map { it.toDomain() } },
            limit = limit
        ) { history -> history.providerId in providerIds }
    }

    override fun getContinueWatchingCandidatesByProvider(providerId: Long, limit: Int): Flow<List<PlaybackHistory>> {
        repositoryScope.launch {
            reconcileCatalogWatchProgress(providerId)
        }
        return mergedRecentHistory(
            persisted = dao.getContinueWatchingCandidatesByProvider(providerId, limit).map { list -> list.map { it.toDomain() } },
            limit = limit
        ) { history ->
            history.providerId == providerId &&
                history.contentType != ContentType.LIVE &&
                history.resumePositionMs > 0L &&
                !com.kaynanamtv.domain.util.isPlaybackComplete(history.resumePositionMs, history.totalDurationMs)
        }
    }

    override fun getContinueWatchingCandidatesByProviders(providerIds: Set<Long>, limit: Int): Flow<List<PlaybackHistory>> {
        if (providerIds.isEmpty()) {
            return kotlinx.coroutines.flow.flowOf(emptyList())
        }
        repositoryScope.launch {
            providerIds.forEach { reconcileCatalogWatchProgress(it) }
        }
        return mergedRecentHistory(
            persisted = dao.getContinueWatchingCandidatesByProviders(providerIds, limit).map { list -> list.map { it.toDomain() } },
            limit = limit
        ) { history ->
            history.providerId in providerIds &&
                history.contentType != ContentType.LIVE &&
                history.resumePositionMs > 0L &&
                !com.kaynanamtv.domain.util.isPlaybackComplete(history.resumePositionMs, history.totalDurationMs)
        }
    }

    override fun getRecentLiveHistoryByProvider(providerId: Long, limit: Int): Flow<List<PlaybackHistory>> {
        return mergedRecentHistory(
            persisted = dao.getRecentLiveHistoryByProvider(providerId, limit).map { list -> list.map { it.toDomain() } },
            limit = limit
        ) { history -> history.providerId == providerId && history.contentType == ContentType.LIVE }
    }

    override fun getRecentLiveHistoryByProviders(providerIds: Set<Long>, limit: Int): Flow<List<PlaybackHistory>> {
        if (providerIds.isEmpty()) {
            return kotlinx.coroutines.flow.flowOf(emptyList())
        }
        return mergedRecentHistory(
            persisted = dao.getRecentLiveHistoryByProviders(providerIds, limit).map { list -> list.map { it.toDomain() } },
            limit = limit
        ) { history -> history.providerId in providerIds && history.contentType == ContentType.LIVE }
    }

    override fun getUnwatchedCount(providerId: Long, seriesId: Long): Flow<Int> {
        return episodeDao.getUnwatchedCount(
            providerId = providerId,
            seriesId = seriesId,
            completionThreshold = DEFAULT_PLAYBACK_COMPLETION_THRESHOLD
        )
    }

    override suspend fun getPlaybackHistory(
        contentId: Long,
        contentType: ContentType,
        providerId: Long,
        seriesId: Long?,
        seasonNumber: Int?,
        episodeNumber: Int?
    ): PlaybackHistory? {
        val key = PlaybackKey(contentId, contentType, providerId)
        pendingResumeUpdates[key]?.let { return it }

        val directMatch = dao.get(contentId, contentType.name, providerId)?.toDomain()
        if (directMatch != null) {
            return directMatch
        }

        return when (contentType) {
            ContentType.MOVIE -> dao.getLatestMovieHistoryBySharedTmdb(contentId, providerId)?.toDomain()
            ContentType.SERIES -> dao.getLatestSeriesHistoryBySharedTmdb(contentId, providerId)?.toDomain()
            ContentType.SERIES_EPISODE -> {
                if (seriesId != null && seasonNumber != null && episodeNumber != null) {
                    dao.getLatestEpisodeHistoryByCoordinates(providerId, seriesId, seasonNumber, episodeNumber)
                        ?.toDomain()
                } else null
            }
            ContentType.LIVE -> null
        }
    }

    override suspend fun markAsWatched(history: PlaybackHistory): Result<Unit> {
        return try {
            if (isIncognito.value) {
                return Result.success(Unit)
            }

            val key = history.playbackKey()
            val existing = pendingResumeUpdates[history.playbackKey()]
                ?: dao.get(history.contentId, history.contentType.name, history.providerId)?.toDomain()
            val resolvedTotalDuration = history.totalDurationMs.takeIf { it > 0L } ?: existing?.totalDurationMs ?: 0L
            val resolvedResumePosition = when {
                resolvedTotalDuration > 0L -> resolvedTotalDuration
                history.resumePositionMs > 0L -> history.resumePositionMs
                else -> existing?.resumePositionMs ?: 0L
            }
            val updatedHistory = history.copy(
                streamUrl = XtreamUrlFactory.sanitizePersistedStreamUrl(history.streamUrl, history.providerId),
                resumePositionMs = resolvedResumePosition,
                totalDurationMs = resolvedTotalDuration,
                watchCount = existing?.watchCount ?: history.watchCount,
                watchedStatus = PlaybackWatchedStatus.COMPLETED_MANUAL,
                lastWatchedAt = System.currentTimeMillis()
            )
            transactionRunner.inTransaction {
                dao.insertOrUpdate(updatedHistory.toEntity())
                syncDenormalizedProgress(updatedHistory.contentId, updatedHistory.contentType, updatedHistory.providerId)
            }
            clearPendingResumeUpdate(key)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.error("Failed to mark content as watched", e)
        }
    }

    override suspend fun recordPlayback(history: PlaybackHistory): Result<Unit> {
        return try {
            if (isIncognito.value) {
                return Result.success(Unit)
            }

            val key = history.playbackKey()
            val existing = pendingResumeUpdates[key]
                ?: dao.get(history.contentId, history.contentType.name, history.providerId)?.toDomain()
            val updatedHistory = history.copy(
                streamUrl = XtreamUrlFactory.sanitizePersistedStreamUrl(history.streamUrl, history.providerId),
                resumePositionMs = history.resumePositionMs.takeIf { it > 0L } ?: existing?.resumePositionMs ?: 0L,
                totalDurationMs = history.totalDurationMs.takeIf { it > 0L } ?: existing?.totalDurationMs ?: 0L,
                watchCount = (existing?.watchCount ?: 0) + 1,
                watchedStatus = resolveWatchedStatus(
                    resumePositionMs = history.resumePositionMs.takeIf { it > 0L } ?: existing?.resumePositionMs ?: 0L,
                    totalDurationMs = history.totalDurationMs.takeIf { it > 0L } ?: existing?.totalDurationMs ?: 0L,
                    fallback = history.watchedStatus
                ),
                lastWatchedAt = System.currentTimeMillis()
            )
            transactionRunner.inTransaction {
                dao.insertOrUpdate(updatedHistory.toEntity())
                syncDenormalizedProgress(updatedHistory.contentId, updatedHistory.contentType, updatedHistory.providerId)
            }
            clearPendingResumeUpdate(key)
            Result.success(Unit)
        } catch (e: Exception) {
            Result.error("Failed to record playback history", e)
        }
    }

    override suspend fun updateResumePosition(history: PlaybackHistory): Result<Unit> {
        return try {
            if (isIncognito.value) {
                return Result.success(Unit)
            }

            val key = history.playbackKey()
            val existing = pendingResumeUpdates[key]
                ?: dao.get(history.contentId, history.contentType.name, history.providerId)?.toDomain()

            val updatedHistory = history.copy(
                streamUrl = XtreamUrlFactory.sanitizePersistedStreamUrl(history.streamUrl, history.providerId),
                watchCount = existing?.watchCount ?: 1,
                watchedStatus = resolveWatchedStatus(
                    resumePositionMs = history.resumePositionMs,
                    totalDurationMs = history.totalDurationMs,
                    fallback = existing?.watchedStatus ?: history.watchedStatus
                ),
                lastWatchedAt = System.currentTimeMillis()
            )
            pendingResumeUpdates[history.playbackKey()] = updatedHistory
            publishPendingResumeUpdates()
            transactionRunner.inTransaction {
                dao.insertOrUpdate(updatedHistory.toEntity())
                syncDenormalizedProgress(updatedHistory.contentId, updatedHistory.contentType, updatedHistory.providerId)
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.error("Failed to update playback resume position", e)
        }
    }

    override suspend fun removeFromHistory(contentId: Long, contentType: ContentType, providerId: Long): Result<Unit> = try {
        pendingResumeUpdates.remove(PlaybackKey(contentId, contentType, providerId))
        transactionRunner.inTransaction {
            dao.delete(contentId, contentType.name, providerId)
            syncDenormalizedProgress(contentId, contentType, providerId)
        }
        Result.success(Unit)
    } catch (e: Exception) {
        Result.error("Failed to remove playback history item", e)
    }

    override suspend fun clearAllHistory(): Result<Unit> = try {
        pendingResumeUpdates.clear()
        transactionRunner.inTransaction {
            dao.deleteAll()
            movieDao.resetAllWatchProgress()
            episodeDao.resetAllWatchProgress()
        }
        Result.success(Unit)
    } catch (e: Exception) {
        Result.error("Failed to clear playback history", e)
    }

    override suspend fun clearHistoryForProvider(providerId: Long): Result<Unit> = try {
        pendingResumeUpdates.keys.removeIf { it.providerId == providerId }
        transactionRunner.inTransaction {
            dao.deleteByProvider(providerId)
            syncDenormalizedProgressForProvider(providerId)
        }
        Result.success(Unit)
    } catch (e: Exception) {
        Result.error("Failed to clear provider playback history", e)
    }

    override suspend fun clearLiveHistoryForProvider(providerId: Long): Result<Unit> = try {
        pendingResumeUpdates.keys.removeIf { it.providerId == providerId && it.contentType == ContentType.LIVE }
        dao.deleteByProviderAndType(providerId, ContentType.LIVE.name)
        Result.success(Unit)
    } catch (e: Exception) {
        Result.error("Failed to clear live playback history", e)
    }

    override suspend fun flushPendingProgress(): Result<Unit> = try {
        flushPendingResumeUpdates()
        Result.success(Unit)
    } catch (e: Exception) {
        Result.error("Failed to flush pending progress", e)
    }

    private suspend fun flushPendingResumeUpdates() {
        val snapshot = pendingResumeUpdates.entries.toList()
        var changed = false
        snapshot.forEach { (key, history) ->
            if (pendingResumeUpdates.remove(key, history)) {
                changed = true
                transactionRunner.inTransaction {
                    dao.insertOrUpdate(history.toEntity())
                    syncDenormalizedProgress(history.contentId, history.contentType, history.providerId)
                }
            }
        }
        if (changed) {
            publishPendingResumeUpdates()
        }
    }

    private fun clearPendingResumeUpdate(key: PlaybackKey) {
        if (pendingResumeUpdates.remove(key) != null) {
            publishPendingResumeUpdates()
        }
    }

    private fun publishPendingResumeUpdates() {
        pendingResumeUpdatesState.value = pendingResumeUpdates.toMap()
    }

    private fun mergedRecentHistory(
        persisted: Flow<List<PlaybackHistory>>,
        limit: Int,
        includePending: (PlaybackHistory) -> Boolean
    ): Flow<List<PlaybackHistory>> = combine(persisted, pendingResumeUpdatesState) { persistedItems, pendingItems ->
        val mergedByKey = LinkedHashMap<PlaybackKey, PlaybackHistory>()
        persistedItems.forEach { history ->
            val enriched = if (history.posterUrl.isNullOrBlank() || history.title.isBlank()) {
                enrichPlaybackHistoryMetadata(history)
            } else {
                history
            }
            mergedByKey[enriched.playbackKey()] = enriched
        }
        pendingItems.values
            .asSequence()
            .filter(includePending)
            .forEach { history ->
                mergedByKey[history.playbackKey()] = history
            }
        mergedByKey.values
            .sortedByDescending(PlaybackHistory::lastWatchedAt)
            .take(limit)
    }

    private suspend fun enrichPlaybackHistoryMetadata(history: PlaybackHistory): PlaybackHistory {
        return runCatching {
            when (history.contentType) {
                ContentType.MOVIE -> {
                    val movie = movieDao.getById(history.contentId)
                    val resolvedPoster = history.posterUrl?.takeIf { it.isNotBlank() }
                        ?: movie?.posterUrl?.takeIf { it.isNotBlank() }
                        ?: movie?.backdropUrl?.takeIf { it.isNotBlank() }
                    val resolvedStreamUrl = history.streamUrl.ifBlank { movie?.streamUrl ?: "" }
                    val resolvedStreamId = movie?.streamId?.takeIf { it > 0L } ?: history.streamId
                    history.copy(
                        title = history.title.ifBlank { movie?.name ?: "" },
                        posterUrl = resolvedPoster,
                        streamUrl = resolvedStreamUrl,
                        streamId = resolvedStreamId
                    )
                }
                ContentType.SERIES_EPISODE -> {
                    val episode = episodeDao.getById(history.contentId)
                    val sId = history.seriesId?.takeIf { it > 0L } ?: episode?.seriesId?.takeIf { it > 0L }
                    val series = if (sId != null && sId > 0L) seriesDao.getById(sId) else null
                    val resolvedPoster = history.posterUrl?.takeIf { it.isNotBlank() }
                        ?: episode?.coverUrl?.takeIf { it.isNotBlank() }
                        ?: series?.posterUrl?.takeIf { it.isNotBlank() }
                        ?: series?.backdropUrl?.takeIf { it.isNotBlank() }
                    val resolvedStreamUrl = history.streamUrl.ifBlank { episode?.streamUrl ?: "" }
                    val resolvedStreamId = episode?.episodeId?.takeIf { it > 0L } ?: history.streamId
                    history.copy(
                        title = history.title.ifBlank { episode?.title ?: series?.name ?: "" },
                        posterUrl = resolvedPoster,
                        streamUrl = resolvedStreamUrl,
                        streamId = resolvedStreamId
                    )
                }
                ContentType.SERIES -> {
                    val series = seriesDao.getById(history.contentId)
                    val resolvedPoster = history.posterUrl?.takeIf { it.isNotBlank() }
                        ?: series?.posterUrl?.takeIf { it.isNotBlank() }
                        ?: series?.backdropUrl?.takeIf { it.isNotBlank() }
                    val resolvedStreamUrl = history.streamUrl.ifBlank { series?.backdropUrl ?: "" }
                    history.copy(
                        title = history.title.ifBlank { series?.name ?: "" },
                        posterUrl = resolvedPoster,
                        streamUrl = resolvedStreamUrl
                    )
                }
                ContentType.LIVE -> history
            }
        }.getOrDefault(history)
    }

    override suspend fun reconcileCatalogWatchProgress(providerId: Long): Result<Unit> {
        return try {
            val moviesWithProgress = movieDao.getMoviesWithWatchProgress(providerId)
            for (movie in moviesWithProgress) {
                val existing = dao.get(movie.id, ContentType.MOVIE.name, providerId)
                val durationMs = (movie.durationSeconds * 1000L).coerceAtLeast(0L)
                val isCompleted = durationMs > 0L && movie.watchProgress >= (durationMs * 0.95)
                if (existing == null) {
                    val entity = PlaybackHistoryEntity(
                        providerId = providerId,
                        contentId = movie.id,
                        contentType = ContentType.MOVIE,
                        title = movie.name,
                        posterUrl = movie.posterUrl ?: movie.backdropUrl,
                        streamUrl = movie.streamUrl,
                        resumePositionMs = movie.watchProgress,
                        totalDurationMs = durationMs,
                        lastWatchedAt = movie.lastWatchedAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
                        watchedStatus = if (isCompleted) "COMPLETED" else "IN_PROGRESS"
                    )
                    dao.insertOrUpdate(entity)
                } else if (movie.watchProgress > existing.resumePositionMs || (movie.lastWatchedAt > existing.lastWatchedAt)) {
                    val updated = existing.copy(
                        resumePositionMs = maxOf(existing.resumePositionMs, movie.watchProgress),
                        lastWatchedAt = maxOf(existing.lastWatchedAt, movie.lastWatchedAt),
                        watchedStatus = if (isCompleted) "COMPLETED" else existing.watchedStatus
                    )
                    dao.insertOrUpdate(updated)
                }
            }

            val episodesWithProgress = episodeDao.getEpisodesWithWatchProgress(providerId)
            for (episode in episodesWithProgress) {
                val existing = dao.get(episode.id, ContentType.SERIES_EPISODE.name, providerId)
                val durationMs = (episode.durationSeconds * 1000L).coerceAtLeast(0L)
                val isCompleted = durationMs > 0L && episode.watchProgress >= (durationMs * 0.95)
                if (existing == null) {
                    val series = if (episode.seriesId > 0L) seriesDao.getById(episode.seriesId) else null
                    val entity = PlaybackHistoryEntity(
                        providerId = providerId,
                        contentId = episode.id,
                        contentType = ContentType.SERIES_EPISODE,
                        title = episode.title.ifBlank { series?.name ?: "" },
                        posterUrl = episode.coverUrl ?: series?.posterUrl ?: series?.backdropUrl,
                        streamUrl = episode.streamUrl,
                        resumePositionMs = episode.watchProgress,
                        totalDurationMs = durationMs,
                        lastWatchedAt = episode.lastWatchedAt.takeIf { it > 0L } ?: System.currentTimeMillis(),
                        seriesId = episode.seriesId,
                        seasonNumber = episode.seasonNumber,
                        episodeNumber = episode.episodeNumber,
                        watchedStatus = if (isCompleted) "COMPLETED" else "IN_PROGRESS"
                    )
                    dao.insertOrUpdate(entity)
                } else if (episode.watchProgress > existing.resumePositionMs || (episode.lastWatchedAt > existing.lastWatchedAt)) {
                    val updated = existing.copy(
                        resumePositionMs = maxOf(existing.resumePositionMs, episode.watchProgress),
                        lastWatchedAt = maxOf(existing.lastWatchedAt, episode.lastWatchedAt),
                        watchedStatus = if (isCompleted) "COMPLETED" else existing.watchedStatus
                    )
                    dao.insertOrUpdate(updated)
                }
            }
            Result.success(Unit)
        } catch (e: Exception) {
            Result.error("Failed to reconcile catalog watch progress", e)
        }
    }

    private suspend fun syncDenormalizedProgress(contentId: Long, contentType: ContentType, providerId: Long) {
        when (contentType) {
            ContentType.MOVIE -> movieDao.syncWatchProgressFromHistory(contentId, providerId)
            ContentType.SERIES_EPISODE -> episodeDao.syncWatchProgressFromHistory(contentId, providerId)
            else -> Unit
        }
    }


    private suspend fun syncDenormalizedProgressForProvider(providerId: Long) {
        movieDao.syncWatchProgressFromHistoryByProvider(providerId)
        episodeDao.syncWatchProgressFromHistoryByProvider(providerId)
    }
    private fun resolveWatchedStatus(
        resumePositionMs: Long,
        totalDurationMs: Long,
        fallback: PlaybackWatchedStatus
    ): PlaybackWatchedStatus {
        if (totalDurationMs <= 0L) {
            return fallback
        }
        return if (isPlaybackComplete(resumePositionMs, totalDurationMs, DEFAULT_PLAYBACK_COMPLETION_THRESHOLD)) {
            PlaybackWatchedStatus.COMPLETED_AUTO
        } else {
            PlaybackWatchedStatus.IN_PROGRESS
        }
    }
}

private const val RESUME_POSITION_FLUSH_INTERVAL_MS = 30_000L

private data class PlaybackKey(
    val contentId: Long,
    val contentType: ContentType,
    val providerId: Long
)

private fun PlaybackHistory.playbackKey(): PlaybackKey =
    PlaybackKey(contentId = contentId, contentType = contentType, providerId = providerId)
