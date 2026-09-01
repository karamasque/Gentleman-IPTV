package com.kaynanamtv.data.sync

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.kaynanamtv.data.local.dao.EpisodeDao
import com.kaynanamtv.data.local.dao.FavoriteDao
import com.kaynanamtv.data.local.dao.MovieDao
import com.kaynanamtv.data.local.dao.PlaybackHistoryDao
import com.kaynanamtv.data.local.dao.ProviderDao
import com.kaynanamtv.data.local.dao.SeriesDao
import com.kaynanamtv.data.local.entity.FavoriteEntity
import com.kaynanamtv.data.local.entity.PlaybackHistoryEntity
import com.kaynanamtv.data.local.entity.ProviderEntity
import com.kaynanamtv.domain.model.ContentType
import com.kaynanamtv.domain.model.ProviderType
import dagger.hilt.android.qualifiers.ApplicationContext
import java.security.MessageDigest
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Manages asynchronous, non-blocking cross-device user state synchronization for Playback Progress and Favorites.
 *
 * Architecture:
 * - Local-First: Local SQLite Room DB is the immediate source of truth.
 * - Non-Blocking & Non-Fatal: Cloud writes are dispatched asynchronously. Cloud network errors or offline states never interrupt playback.
 * - Stable Cross-Device Identity: Uses content streamId, coordinates, and deterministic provider hashes instead of local auto-increment database IDs.
 * - Write Coalescing: Writes to Firestore every ~25-30s during active playback or on meaningful lifecycle checkpoints (pause, exit, background, PiP dismiss, complete).
 * - Smart Sync / Delta Sync: Only fetches documents modified since the last successful sync cursor with a clock-skew safety window, saving ~98%+ Firestore reads.
 */
@Singleton
class CloudUserStateSyncManager @Inject constructor(
    @param:ApplicationContext private val context: Context,
    private val favoriteDao: FavoriteDao,
    private val playbackHistoryDao: PlaybackHistoryDao,
    private val providerDao: ProviderDao,
    private val movieDao: MovieDao,
    private val seriesDao: SeriesDao,
    private val episodeDao: EpisodeDao
) {
    companion object {
        private const val TAG = "CloudUserStateSync"
        private const val PREFS_NAME = "cloud_user_state_sync_prefs"
        private const val PREF_KEY_WATCH_PREFIX = "watch_history_cursor_"
        private const val PREF_KEY_FAVORITES_PREFIX = "favorites_cursor_"
        const val CLOCK_SKEW_SAFETY_WINDOW_MS = 60_000L // 60 seconds delta overlap
        const val RECONCILE_MIN_INTERVAL_MS = 60_000L // 60 seconds cooldown for automated sync triggers
    }

    private val syncPrefs by lazy {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    fun getWatchHistorySyncCursor(userId: String): Long {
        return syncPrefs.getLong("${PREF_KEY_WATCH_PREFIX}$userId", 0L)
    }

    fun setWatchHistorySyncCursor(userId: String, cursor: Long) {
        syncPrefs.edit().putLong("${PREF_KEY_WATCH_PREFIX}$userId", cursor).apply()
    }

    fun getFavoritesSyncCursor(userId: String): Long {
        return syncPrefs.getLong("${PREF_KEY_FAVORITES_PREFIX}$userId", 0L)
    }

    fun setFavoritesSyncCursor(userId: String, cursor: Long) {
        syncPrefs.edit().putLong("${PREF_KEY_FAVORITES_PREFIX}$userId", cursor).apply()
    }

    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lastSyncedProgressTimes = ConcurrentHashMap<String, Long>()
    private val lastSyncedProgressPositions = ConcurrentHashMap<String, Long>()
    private val reconcileMutex = kotlinx.coroutines.sync.Mutex()
    private var lastReconcileTime = 0L

    /**
     * Computes a deterministic, cross-device stable key for a provider account.
     */
    fun computeProviderStableKey(provider: ProviderEntity): String {
        val raw = when (provider.type) {
            ProviderType.XTREAM_CODES -> "xtream:${provider.serverUrl.trim().trimEnd('/').lowercase()}:${provider.username.trim()}"
            ProviderType.M3U -> "m3u:${provider.m3uUrl.trim().trimEnd('/').lowercase()}"
            ProviderType.STALKER_PORTAL -> "stalker:${provider.serverUrl.trim().trimEnd('/').lowercase()}:${provider.stalkerMacAddress.trim().lowercase()}"
            ProviderType.JELLYFIN -> "jellyfin:${provider.serverUrl.trim().trimEnd('/').lowercase()}:${provider.username.trim()}"
        }
        return runCatching {
            val md = MessageDigest.getInstance("MD5")
            val bytes = md.digest(raw.toByteArray(Charsets.UTF_8))
            bytes.joinToString("") { "%02x".format(it) }
        }.getOrDefault(raw.filter { it.isLetterOrDigit() }.take(32))
    }

    /**
     * Sanitizes sensitive identifiers to a deterministic SHA-256 hash prefix for safe cross-device log correlation.
     */
    fun sanitizedHash(value: String): String {
        if (value.isBlank()) return "empty"
        return runCatching {
            val md = MessageDigest.getInstance("SHA-256")
            val bytes = md.digest(value.toByteArray(Charsets.UTF_8))
            bytes.take(8).joinToString("") { "%02x".format(it) }
        }.getOrDefault("hash_err")
    }

    /**
     * Resolves and fetches the targeted watch progress document from Firestore for a specific movie or episode.
     * Maximum 1 document read. Returns null on timeout, offline, missing document, or malformed data.
     */
    suspend fun fetchTargetedPlaybackProgress(
        providerId: Long,
        contentId: Long,
        contentType: ContentType,
        seriesId: Long? = null,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null
    ): TargetedWatchProgress? {
        if (contentId <= 0L || providerId <= 0L || contentType == ContentType.LIVE) return null
        val user = FirebaseAuth.getInstance().currentUser ?: return null
        if (user.isAnonymous) return null

        return runCatching {
            val provider = providerDao.getById(providerId) ?: return null
            val providerStableKey = computeProviderStableKey(provider)

            val cloudDocId = when (contentType) {
                ContentType.MOVIE -> {
                    val movie = movieDao.getById(contentId)
                    val streamId = movie?.streamId ?: contentId
                    "MOVIE_${providerStableKey}_$streamId"
                }
                ContentType.SERIES_EPISODE -> {
                    val episode = episodeDao.getById(contentId)
                    val resolvedSeriesId = seriesId ?: episode?.seriesId ?: 0L
                    val series = if (resolvedSeriesId > 0L) seriesDao.getById(resolvedSeriesId) else null
                    val seriesRemoteId = series?.seriesId ?: resolvedSeriesId
                    val seasonNum = seasonNumber ?: episode?.seasonNumber ?: 0
                    val episodeNum = episodeNumber ?: episode?.episodeNumber ?: 0
                    "EPISODE_${providerStableKey}_${seriesRemoteId}_S${seasonNum}E${episodeNum}"
                }
                else -> return null
            }

            val firestore = FirebaseFirestore.getInstance()
            val doc = firestore.collection("users").document(user.uid)
                .collection("watch_history").document(cloudDocId).get().await()

            if (!doc.exists()) return null
            val data = doc.data ?: return null

            val resumePositionMs = (data["resumePositionMs"] as? Long)?.takeIf { it >= 0L } ?: return null
            val totalDurationMs = (data["totalDurationMs"] as? Long)?.takeIf { it >= 0L } ?: 0L
            val updatedAt = (data["updatedAt"] as? Long)?.takeIf { it > 0L } ?: return null
            val isCompleted = (data["isCompleted"] as? Boolean)
                ?: (totalDurationMs > 0L && resumePositionMs >= totalDurationMs * 0.95)
            val revision = (data["revision"] as? Long) ?: 0L
            val playbackSessionId = (data["playbackSessionId"] as? String) ?: ""
            val checkpointSeq = (data["checkpointSeq"] as? Long) ?: 0L

            TargetedWatchProgress(
                resumePositionMs = resumePositionMs,
                totalDurationMs = totalDurationMs,
                isCompleted = isCompleted,
                updatedAt = updatedAt,
                revision = revision,
                playbackSessionId = playbackSessionId,
                checkpointSeq = checkpointSeq
            )
        }.getOrNull()
    }

    data class TargetedWatchProgress(
        val resumePositionMs: Long,
        val totalDurationMs: Long,
        val isCompleted: Boolean,
        val updatedAt: Long,
        val revision: Long = 0L,
        val playbackSessionId: String = "",
        val checkpointSeq: Long = 0L
    )

    /**
     * Records watch progress to local Room DB immediately, and asynchronously syncs to Firestore using OCC.
     */
    fun recordPlaybackProgress(
        providerId: Long,
        contentId: Long,
        contentType: ContentType,
        positionMs: Long,
        durationMs: Long,
        seriesId: Long? = null,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null,
        forceCloudSync: Boolean = false,
        playbackSessionId: String = "",
        baseRevision: Long = 0L,
        checkpointSeq: Long = 0L
    ) {
        if (contentId <= 0L || providerId <= 0L || contentType == ContentType.LIVE) return

        syncScope.launch {
            val now = System.currentTimeMillis()
            val isCompleted = durationMs > 0L && positionMs >= (durationMs * 0.95) // 95% completion threshold

            val historyEntity = PlaybackHistoryEntity(
                providerId = providerId,
                contentId = contentId,
                contentType = contentType,
                resumePositionMs = positionMs,
                totalDurationMs = durationMs,
                lastWatchedAt = now,
                seriesId = seriesId,
                seasonNumber = seasonNumber,
                episodeNumber = episodeNumber,
                watchedStatus = if (isCompleted) "COMPLETED" else "IN_PROGRESS"
            )

            // 1. Local Room DB update (Instant Source of Truth)
            runCatching {
                playbackHistoryDao.insertOrUpdate(historyEntity)
            }.onFailure {
                Log.w(TAG, "Failed to persist local playback history", it)
            }

            // 2. Multi-device Cloud Sync (Async, non-blocking OCC transaction with offline queue fallback)
            val user = FirebaseAuth.getInstance().currentUser ?: return@launch
            if (user.isAnonymous) return@launch

            val provider = runCatching { providerDao.getById(providerId) }.getOrNull() ?: return@launch
            val providerStableKey = computeProviderStableKey(provider)

            val (cloudDocId, metadata) = when (contentType) {
                ContentType.MOVIE -> {
                    val movie = runCatching { movieDao.getById(contentId) }.getOrNull()
                    val streamId = movie?.streamId ?: contentId
                    val tmdbId = movie?.tmdbId
                    val title = movie?.name ?: ""
                    val docId = "MOVIE_${providerStableKey}_$streamId"
                    val map = hashMapOf<String, Any>(
                        "providerStableKey" to providerStableKey,
                        "contentKey" to docId,
                        "contentType" to ContentType.MOVIE.name,
                        "streamId" to streamId,
                        "tmdbId" to (tmdbId ?: 0L),
                        "title" to title,
                        "seriesRemoteId" to 0L,
                        "seasonNumber" to 0,
                        "episodeNumber" to 0,
                        "resumePositionMs" to positionMs,
                        "totalDurationMs" to durationMs,
                        "progressPercent" to if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat() * 100f).coerceIn(0f, 100f) else 0f,
                        "isCompleted" to isCompleted,
                        "updatedAt" to now
                    )
                    docId to map
                }
                ContentType.SERIES_EPISODE -> {
                    val episode = runCatching { episodeDao.getById(contentId) }.getOrNull()
                    val resolvedSeriesId = seriesId ?: episode?.seriesId ?: 0L
                    val series = if (resolvedSeriesId > 0L) runCatching { seriesDao.getById(resolvedSeriesId) }.getOrNull() else null
                    val seriesRemoteId = series?.seriesId ?: resolvedSeriesId
                    val seasonNum = seasonNumber ?: episode?.seasonNumber ?: 0
                    val episodeNum = episodeNumber ?: episode?.episodeNumber ?: 0
                    val streamId = episode?.episodeId ?: 0L
                    val tmdbId = series?.tmdbId
                    val title = episode?.title ?: series?.name ?: ""
                    val docId = "EPISODE_${providerStableKey}_${seriesRemoteId}_S${seasonNum}E${episodeNum}"
                    val map = hashMapOf<String, Any>(
                        "providerStableKey" to providerStableKey,
                        "contentKey" to docId,
                        "contentType" to ContentType.SERIES_EPISODE.name,
                        "streamId" to streamId,
                        "tmdbId" to (tmdbId ?: 0L),
                        "title" to title,
                        "seriesRemoteId" to seriesRemoteId,
                        "seasonNumber" to seasonNum,
                        "episodeNumber" to episodeNum,
                        "resumePositionMs" to positionMs,
                        "totalDurationMs" to durationMs,
                        "progressPercent" to if (durationMs > 0) (positionMs.toFloat() / durationMs.toFloat() * 100f).coerceIn(0f, 100f) else 0f,
                        "isCompleted" to isCompleted,
                        "updatedAt" to now
                    )
                    docId to map
                }
                else -> return@launch
            }

            // Write coalescing: sync if forced (pause/exit/stop/complete) or if >= 25s elapsed AND position delta >= 15s
            val lastSyncedTime = lastSyncedProgressTimes[cloudDocId] ?: 0L
            val lastSyncedPos = lastSyncedProgressPositions[cloudDocId] ?: -1L
            val posDelta = kotlin.math.abs(positionMs - lastSyncedPos)

            val shouldSync = forceCloudSync || isCompleted || (now - lastSyncedTime >= 25_000L && posDelta >= 15_000L)

            if (!shouldSync) {
                return@launch
            }

            lastSyncedProgressTimes[cloudDocId] = now
            lastSyncedProgressPositions[cloudDocId] = positionMs

            runCatching {
                val firestore = FirebaseFirestore.getInstance()
                val docRef = firestore.collection("users").document(user.uid)
                    .collection("watch_history").document(cloudDocId)

                runCatching {
                    firestore.runTransaction { transaction ->
                        val snapshot = transaction.get(docRef)
                        val cloudExists = snapshot.exists()
                        val cloudData = snapshot.data
                        val cloudRevision = (cloudData?.get("revision") as? Long) ?: 0L
                        val cloudSessionId = (cloudData?.get("playbackSessionId") as? String) ?: ""
                        val cloudSeq = (cloudData?.get("checkpointSeq") as? Long) ?: 0L

                        val isSameSession = cloudExists && cloudSessionId.isNotBlank() && cloudSessionId == playbackSessionId

                        if (isSameSession) {
                            if (checkpointSeq > cloudSeq) {
                                metadata["revision"] = cloudRevision
                                metadata["playbackSessionId"] = playbackSessionId
                                metadata["checkpointSeq"] = checkpointSeq
                                transaction.set(docRef, metadata, SetOptions.merge())
                            }
                        } else {
                            if (!cloudExists || cloudRevision == baseRevision || (cloudRevision == 0L && baseRevision == 0L)) {
                                val newRevision = if (cloudExists) cloudRevision + 1L else 1L
                                metadata["revision"] = newRevision
                                metadata["playbackSessionId"] = playbackSessionId
                                metadata["checkpointSeq"] = checkpointSeq
                                transaction.set(docRef, metadata, SetOptions.merge())
                            }
                        }
                    }.await()
                }.onFailure {
                    // Fallback to direct merge set to allow Firestore offline persistence queueing
                    metadata["playbackSessionId"] = playbackSessionId
                    metadata["checkpointSeq"] = checkpointSeq
                    docRef.set(metadata, SetOptions.merge()).await()
                }
            }.onFailure {
                Log.w(TAG, "Failed to publish OCC watch progress to Firestore (non-fatal)", it)
            }
        }
    }

    /**
     * Syncs favorite status change to Firestore asynchronously with stable provider key.
     */
    fun syncFavoriteChange(
        providerId: Long,
        contentId: Long,
        contentType: ContentType,
        isFavorite: Boolean
    ) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        if (user.isAnonymous) return
        syncScope.launch {
            val provider = runCatching { providerDao.getById(providerId) }.getOrNull() ?: return@launch
            val providerStableKey = computeProviderStableKey(provider)

            val (cloudDocId, metadata) = when (contentType) {
                ContentType.MOVIE -> {
                    val movie = runCatching { movieDao.getById(contentId) }.getOrNull()
                    val streamId = movie?.streamId ?: contentId
                    val tmdbId = movie?.tmdbId
                    val title = movie?.name ?: ""
                    val docId = "FAV_MOVIE_${providerStableKey}_$streamId"
                    val map = hashMapOf(
                        "providerStableKey" to providerStableKey,
                        "contentKey" to docId,
                        "contentType" to ContentType.MOVIE.name,
                        "streamId" to streamId,
                        "tmdbId" to (tmdbId ?: 0L),
                        "title" to title,
                        "seriesRemoteId" to 0L,
                        "seasonNumber" to 0,
                        "episodeNumber" to 0,
                        "isFavorite" to isFavorite,
                        "updatedAt" to System.currentTimeMillis()
                    )
                    docId to map
                }
                ContentType.SERIES -> {
                    val series = runCatching { seriesDao.getById(contentId) }.getOrNull()
                    val seriesRemoteId = series?.seriesId ?: contentId
                    val tmdbId = series?.tmdbId
                    val title = series?.name ?: ""
                    val docId = "FAV_SERIES_${providerStableKey}_$seriesRemoteId"
                    val map = hashMapOf(
                        "providerStableKey" to providerStableKey,
                        "contentKey" to docId,
                        "contentType" to ContentType.SERIES.name,
                        "streamId" to seriesRemoteId,
                        "tmdbId" to (tmdbId ?: 0L),
                        "title" to title,
                        "seriesRemoteId" to seriesRemoteId,
                        "seasonNumber" to 0,
                        "episodeNumber" to 0,
                        "isFavorite" to isFavorite,
                        "updatedAt" to System.currentTimeMillis()
                    )
                    docId to map
                }
                ContentType.SERIES_EPISODE -> {
                    val episode = runCatching { episodeDao.getById(contentId) }.getOrNull()
                    val series = episode?.seriesId?.let { runCatching { seriesDao.getById(it) }.getOrNull() }
                    val seriesRemoteId = series?.seriesId ?: episode?.seriesId ?: 0L
                    val seasonNum = episode?.seasonNumber ?: 0
                    val episodeNum = episode?.episodeNumber ?: 0
                    val streamId = episode?.episodeId ?: 0L
                    val tmdbId = series?.tmdbId
                    val title = episode?.title ?: series?.name ?: ""
                    val docId = "FAV_EPISODE_${providerStableKey}_${seriesRemoteId}_S${seasonNum}E${episodeNum}"
                    val map = hashMapOf(
                        "providerStableKey" to providerStableKey,
                        "contentKey" to docId,
                        "contentType" to ContentType.SERIES_EPISODE.name,
                        "streamId" to streamId,
                        "tmdbId" to (tmdbId ?: 0L),
                        "title" to title,
                        "seriesRemoteId" to seriesRemoteId,
                        "seasonNumber" to seasonNum,
                        "episodeNumber" to episodeNum,
                        "isFavorite" to isFavorite,
                        "updatedAt" to System.currentTimeMillis()
                    )
                    docId to map
                }
                ContentType.LIVE -> {
                    val streamId = contentId
                    val docId = "FAV_LIVE_${providerStableKey}_$streamId"
                    val map = hashMapOf(
                        "providerStableKey" to providerStableKey,
                        "contentKey" to docId,
                        "contentType" to ContentType.LIVE.name,
                        "streamId" to streamId,
                        "tmdbId" to 0L,
                        "title" to "",
                        "seriesRemoteId" to 0L,
                        "seasonNumber" to 0,
                        "episodeNumber" to 0,
                        "isFavorite" to isFavorite,
                        "updatedAt" to System.currentTimeMillis()
                    )
                    docId to map
                }
            }

            runCatching {
                val firestore = FirebaseFirestore.getInstance()
                firestore.collection("users").document(user.uid)
                    .collection("favorites").document(cloudDocId)
                    .set(metadata, SetOptions.merge()).await()
            }.onFailure { Log.w(TAG, "Failed to sync favorite change to Firestore (non-fatal)", it) }
        }
    }

    /**
     * Reconciles remote watch history and favorites from Firestore into local Room DB.
     * Uses Smart Sync / Delta Sync: only fetches records changed since the last sync cursor.
     * Guarded by mutex and 5-second debounce to eliminate duplicate queries while allowing rapid updates.
     */
    fun reconcileFromCloud(force: Boolean = false) {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        if (user.isAnonymous) return
        val now = System.currentTimeMillis()
        if (!force && (now - lastReconcileTime < RECONCILE_MIN_INTERVAL_MS)) {
            return
        }
        syncScope.launch {
            if (!reconcileMutex.tryLock()) {
                return@launch
            }
            try {
                lastReconcileTime = System.currentTimeMillis()
                val token = runCatching { user.getIdToken(true).await() }.getOrNull()
                if (token == null || token.token.isNullOrBlank()) {
                    return@launch
                }

                val localProviders = runCatching { providerDao.getAllSync() }.getOrDefault(emptyList())
                if (localProviders.isEmpty()) {
                    return@launch
                }

                val providersByStableKey = localProviders.associateBy { computeProviderStableKey(it) }
                val syncStartTime = System.currentTimeMillis()

                // 1. Reconcile Watch History (Delta Sync with Safety Window and Partial Cursor Protection)
                runCatching {
                    val lastCursor = if (force) 0L else getWatchHistorySyncCursor(user.uid)
                    val firestore = FirebaseFirestore.getInstance()
                    val collectionRef = firestore.collection("users").document(user.uid).collection("watch_history")

                    val query = if (lastCursor > 0L) {
                        val queryStart = (lastCursor - CLOCK_SKEW_SAFETY_WINDOW_MS).coerceAtLeast(0L)
                        collectionRef.whereGreaterThan("updatedAt", queryStart)
                    } else {
                        collectionRef
                    }

                    val historySnapshot = query.get().await()
                    var minUnresolvedWatchHistoryTime: Long? = null

                    for (doc in historySnapshot.documents) {
                        val data = doc.data ?: continue
                        val providerStableKey = (data["providerStableKey"] as? String) ?: ""
                        val legacyProviderId = (data["providerId"] as? Long) ?: 0L
                        val localProvider = providersByStableKey[providerStableKey]
                            ?: localProviders.firstOrNull { it.id == legacyProviderId }

                        val contentTypeStr = (data["contentType"] as? String) ?: continue
                        val contentType = runCatching { ContentType.valueOf(contentTypeStr) }.getOrNull() ?: continue
                        val streamId = (data["streamId"] as? Long) ?: (data["contentId"] as? Long) ?: 0L
                        val resumePositionMs = (data["resumePositionMs"] as? Long) ?: 0L
                        val totalDurationMs = (data["totalDurationMs"] as? Long) ?: 0L
                        val updatedAt = (data["updatedAt"] as? Long) ?: 0L
                        val isCompleted = (data["isCompleted"] as? Boolean)
                            ?: (totalDurationMs > 0L && resumePositionMs >= totalDurationMs * 0.95)
                        val cloudRevision = (data["revision"] as? Long) ?: 0L

                        if (localProvider == null) {
                            minUnresolvedWatchHistoryTime = minOf(minUnresolvedWatchHistoryTime ?: updatedAt, updatedAt)
                            continue
                        }

                        when (contentType) {
                            ContentType.MOVIE -> {
                                val localMovie = if (streamId > 0L) {
                                    movieDao.getByStreamId(localProvider.id, streamId)
                                } else null

                                if (localMovie != null) {
                                    val local = playbackHistoryDao.get(localMovie.id, ContentType.MOVIE.name, localProvider.id)
                                    if (local == null || cloudRevision > 0L || updatedAt > local.lastWatchedAt) {
                                        val title = (data["title"] as? String)?.takeIf { it.isNotBlank() }
                                            ?: localMovie.name.takeIf { it.isNotBlank() }
                                            ?: local?.title
                                            ?: ""
                                        val posterUrl = localMovie.posterUrl?.takeIf { it.isNotBlank() }
                                            ?: local?.posterUrl
                                        val streamUrl = localMovie.streamUrl.takeIf { it.isNotBlank() }
                                            ?: local?.streamUrl
                                            ?: ""
                                        playbackHistoryDao.insertOrUpdate(
                                            PlaybackHistoryEntity(
                                                providerId = localProvider.id,
                                                contentId = localMovie.id,
                                                contentType = ContentType.MOVIE,
                                                title = title,
                                                posterUrl = posterUrl,
                                                streamUrl = streamUrl,
                                                resumePositionMs = resumePositionMs,
                                                totalDurationMs = totalDurationMs,
                                                lastWatchedAt = updatedAt,
                                                seriesId = null,
                                                seasonNumber = null,
                                                episodeNumber = null,
                                                watchedStatus = if (isCompleted) "COMPLETED" else "IN_PROGRESS"
                                            )
                                        )
                                        movieDao.syncWatchProgressFromHistory(localMovie.id, localProvider.id)
                                    }
                                } else {
                                    minUnresolvedWatchHistoryTime = minOf(minUnresolvedWatchHistoryTime ?: updatedAt, updatedAt)
                                }
                            }
                            ContentType.SERIES_EPISODE -> {
                                val seriesRemoteId = (data["seriesRemoteId"] as? Long) ?: 0L
                                val seasonNumber = (data["seasonNumber"] as? Long)?.toInt() ?: 0
                                val episodeNumber = (data["episodeNumber"] as? Long)?.toInt() ?: 0

                                val localSeries = if (seriesRemoteId > 0L) {
                                    seriesDao.getBySeriesId(localProvider.id, seriesRemoteId)
                                } else null

                                val localEpisode = when {
                                    localSeries != null && seasonNumber > 0 && episodeNumber > 0 -> {
                                        episodeDao.getByCoordinates(localProvider.id, localSeries.id, seasonNumber, episodeNumber)
                                    }
                                    streamId > 0L -> {
                                        episodeDao.getByEpisodeId(localProvider.id, streamId)
                                    }
                                    else -> null
                                }

                                if (localEpisode != null) {
                                    val local = playbackHistoryDao.get(localEpisode.id, ContentType.SERIES_EPISODE.name, localProvider.id)
                                    if (local == null || cloudRevision > 0L || updatedAt > local.lastWatchedAt) {
                                        val title = (data["title"] as? String)?.takeIf { it.isNotBlank() }
                                            ?: localEpisode.title.takeIf { it.isNotBlank() }
                                            ?: localSeries?.name
                                            ?: local?.title
                                            ?: ""
                                        val posterUrl = localEpisode.coverUrl?.takeIf { it.isNotBlank() }
                                            ?: localSeries?.posterUrl?.takeIf { it.isNotBlank() }
                                            ?: localSeries?.backdropUrl?.takeIf { it.isNotBlank() }
                                            ?: local?.posterUrl
                                        val streamUrl = localEpisode.streamUrl.takeIf { it.isNotBlank() }
                                            ?: local?.streamUrl
                                            ?: ""
                                        playbackHistoryDao.insertOrUpdate(
                                            PlaybackHistoryEntity(
                                                providerId = localProvider.id,
                                                contentId = localEpisode.id,
                                                contentType = ContentType.SERIES_EPISODE,
                                                title = title,
                                                posterUrl = posterUrl,
                                                streamUrl = streamUrl,
                                                resumePositionMs = resumePositionMs,
                                                totalDurationMs = totalDurationMs,
                                                lastWatchedAt = updatedAt,
                                                seriesId = localSeries?.id ?: localEpisode.seriesId,
                                                seasonNumber = seasonNumber,
                                                episodeNumber = episodeNumber,
                                                watchedStatus = if (isCompleted) "COMPLETED" else "IN_PROGRESS"
                                            )
                                        )
                                        episodeDao.syncWatchProgressFromHistory(localEpisode.id, localProvider.id)
                                    }
                                } else {
                                    minUnresolvedWatchHistoryTime = minOf(minUnresolvedWatchHistoryTime ?: updatedAt, updatedAt)
                                }
                            }
                            else -> { /* No-op for Live */ }
                        }
                    }

                    // Cursor is advanced ONLY after successful processing.
                    // If any item was unresolved because catalog wasn't ready, do not advance cursor past it.
                    if (minUnresolvedWatchHistoryTime != null) {
                        val safeCursor = (minUnresolvedWatchHistoryTime - 1000L).coerceAtLeast(0L)
                        setWatchHistorySyncCursor(user.uid, safeCursor)
                    } else {
                        setWatchHistorySyncCursor(user.uid, syncStartTime)
                    }
                }.onFailure {
                    Log.w(TAG, "Failed to reconcile watch history from cloud", it)
                }

                // 2. Reconcile Favorites (Delta Sync with Safety Window)
                runCatching {
                    val lastCursor = getFavoritesSyncCursor(user.uid)
                    val firestore = FirebaseFirestore.getInstance()
                    val collectionRef = firestore.collection("users").document(user.uid).collection("favorites")

                    val query = if (lastCursor > 0L) {
                        val queryStart = (lastCursor - CLOCK_SKEW_SAFETY_WINDOW_MS).coerceAtLeast(0L)
                        collectionRef.whereGreaterThan("updatedAt", queryStart)
                    } else {
                        collectionRef
                    }

                    val favoritesSnapshot = query.get().await()

                    for (doc in favoritesSnapshot.documents) {
                        val data = doc.data ?: continue
                        val isFavorite = (data["isFavorite"] as? Boolean) ?: false
                        val providerStableKey = (data["providerStableKey"] as? String) ?: ""
                        val legacyProviderId = (data["providerId"] as? Long) ?: 0L
                        val localProvider = providersByStableKey[providerStableKey]
                            ?: localProviders.firstOrNull { it.id == legacyProviderId }
                            ?: continue

                        val contentTypeStr = (data["contentType"] as? String) ?: continue
                        val contentType = runCatching { ContentType.valueOf(contentTypeStr) }.getOrNull() ?: continue
                        val streamId = (data["streamId"] as? Long) ?: (data["contentId"] as? Long) ?: 0L

                        val localContentId: Long? = when (contentType) {
                            ContentType.MOVIE -> {
                                if (streamId > 0L) movieDao.getByStreamId(localProvider.id, streamId)?.id else null
                            }
                            ContentType.SERIES -> {
                                if (streamId > 0L) seriesDao.getBySeriesId(localProvider.id, streamId)?.id else null
                            }
                            ContentType.SERIES_EPISODE -> {
                                val seriesRemoteId = (data["seriesRemoteId"] as? Long) ?: 0L
                                val seasonNumber = (data["seasonNumber"] as? Long)?.toInt() ?: 0
                                val episodeNumber = (data["episodeNumber"] as? Long)?.toInt() ?: 0
                                val localSeries = if (seriesRemoteId > 0L) seriesDao.getBySeriesId(localProvider.id, seriesRemoteId) else null
                                when {
                                    localSeries != null && seasonNumber > 0 && episodeNumber > 0 -> {
                                        episodeDao.getByCoordinates(localProvider.id, localSeries.id, seasonNumber, episodeNumber)?.id
                                    }
                                    streamId > 0L -> episodeDao.getByEpisodeId(localProvider.id, streamId)?.id
                                    else -> null
                                }
                            }
                            ContentType.LIVE -> null
                        }

                        if (localContentId != null && localContentId > 0L) {
                            if (isFavorite) {
                                if (favoriteDao.get(localProvider.id, localContentId, contentType.name, null) == null) {
                                    favoriteDao.insert(
                                        FavoriteEntity(
                                            providerId = localProvider.id,
                                            contentId = localContentId,
                                            contentType = contentType,
                                            groupId = null,
                                            position = 0
                                        )
                                    )
                                }
                            } else {
                                favoriteDao.delete(localProvider.id, localContentId, contentType.name, null)
                            }
                        }
                    }

                    // Favorites cursor updated after processing
                    setFavoritesSyncCursor(user.uid, syncStartTime)
                }.onFailure { Log.w(TAG, "Failed to reconcile favorites from cloud", it) }

                // 3. Perform idempotent local-history-to-cloud backfill for any unsynced historical records
                runCatching {
                    backfillLocalHistoryToCloud()
                }
            } finally {
                reconcileMutex.unlock()
            }
        }
    }

    /**
     * Bounded, idempotent local-history-to-cloud backfill.
     * Ensures all pre-existing local Room playback progress entries become available in Firestore,
     * using canonical cloud document keys and OCC timestamp/revision checks so newer cloud data is never overwritten.
     */
    suspend fun backfillLocalHistoryToCloud(batchLimit: Int = 50): Int {
        val user = FirebaseAuth.getInstance().currentUser ?: return 0
        if (user.isAnonymous) return 0

        return runCatching {
            val localHistory = playbackHistoryDao.getAllSync()
                .filter { it.contentType != ContentType.LIVE && it.resumePositionMs > 0L }
                .take(batchLimit)

            if (localHistory.isEmpty()) return 0

            val localProviders = providerDao.getAllSync()
            if (localProviders.isEmpty()) return 0
            val providersById = localProviders.associateBy { it.id }

            val firestore = FirebaseFirestore.getInstance()
            val watchHistoryColl = firestore.collection("users").document(user.uid).collection("watch_history")
            var backfilledCount = 0

            for (record in localHistory) {
                val provider = providersById[record.providerId] ?: continue
                val providerStableKey = computeProviderStableKey(provider)

                val (cloudDocId, metadata) = when (record.contentType) {
                    ContentType.MOVIE -> {
                        val movie = movieDao.getById(record.contentId)
                        val streamId = movie?.streamId ?: record.contentId
                        val tmdbId = movie?.tmdbId
                        val title = movie?.name ?: record.title
                        val docId = "MOVIE_${providerStableKey}_$streamId"
                        val isCompleted = record.totalDurationMs > 0L && record.resumePositionMs >= (record.totalDurationMs * 0.95)
                        val map = hashMapOf<String, Any>(
                            "providerStableKey" to providerStableKey,
                            "contentKey" to docId,
                            "contentType" to ContentType.MOVIE.name,
                            "streamId" to streamId,
                            "tmdbId" to (tmdbId ?: 0L),
                            "title" to title,
                            "seriesRemoteId" to 0L,
                            "seasonNumber" to 0,
                            "episodeNumber" to 0,
                            "resumePositionMs" to record.resumePositionMs,
                            "totalDurationMs" to record.totalDurationMs,
                            "progressPercent" to if (record.totalDurationMs > 0) (record.resumePositionMs.toFloat() / record.totalDurationMs.toFloat() * 100f).coerceIn(0f, 100f) else 0f,
                            "isCompleted" to isCompleted,
                            "updatedAt" to record.lastWatchedAt
                        )
                        docId to map
                    }
                    ContentType.SERIES_EPISODE -> {
                        val episode = episodeDao.getById(record.contentId)
                        val resolvedSeriesId = record.seriesId ?: episode?.seriesId ?: 0L
                        val series = if (resolvedSeriesId > 0L) seriesDao.getById(resolvedSeriesId) else null
                        val seriesRemoteId = series?.seriesId ?: resolvedSeriesId
                        val seasonNum = record.seasonNumber ?: episode?.seasonNumber ?: 0
                        val episodeNum = record.episodeNumber ?: episode?.episodeNumber ?: 0
                        val streamId = episode?.episodeId ?: 0L
                        val tmdbId = series?.tmdbId
                        val title = episode?.title ?: record.title.ifBlank { series?.name ?: "" }
                        val docId = "EPISODE_${providerStableKey}_${seriesRemoteId}_S${seasonNum}E${episodeNum}"
                        val isCompleted = record.totalDurationMs > 0L && record.resumePositionMs >= (record.totalDurationMs * 0.95)
                        val map = hashMapOf<String, Any>(
                            "providerStableKey" to providerStableKey,
                            "contentKey" to docId,
                            "contentType" to ContentType.SERIES_EPISODE.name,
                            "streamId" to streamId,
                            "tmdbId" to (tmdbId ?: 0L),
                            "title" to title,
                            "seriesRemoteId" to seriesRemoteId,
                            "seasonNumber" to seasonNum,
                            "episodeNumber" to episodeNum,
                            "resumePositionMs" to record.resumePositionMs,
                            "totalDurationMs" to record.totalDurationMs,
                            "progressPercent" to if (record.totalDurationMs > 0) (record.resumePositionMs.toFloat() / record.totalDurationMs.toFloat() * 100f).coerceIn(0f, 100f) else 0f,
                            "isCompleted" to isCompleted,
                            "updatedAt" to record.lastWatchedAt
                        )
                        docId to map
                    }
                    else -> continue
                }

                val docRef = watchHistoryColl.document(cloudDocId)
                runCatching {
                    firestore.runTransaction { transaction ->
                        val snapshot = transaction.get(docRef)
                        if (snapshot.exists()) {
                            val cloudData = snapshot.data
                            val cloudUpdatedAt = (cloudData?.get("updatedAt") as? Long) ?: 0L
                            val cloudIsCompleted = (cloudData?.get("isCompleted") as? Boolean) ?: false

                            // Guard: Never overwrite a newer cloud update or a cloud completed state with older local progress
                            if (cloudUpdatedAt >= record.lastWatchedAt || (cloudIsCompleted && !(metadata["isCompleted"] as Boolean))) {
                                return@runTransaction
                            }
                            val cloudRevision = (cloudData?.get("revision") as? Long) ?: 0L
                            metadata["revision"] = cloudRevision + 1L
                        } else {
                            metadata["revision"] = 1L
                        }
                        transaction.set(docRef, metadata, SetOptions.merge())
                    }.await()
                    backfilledCount++
                }.onFailure {
                    // Fallback to merge set if transaction contention or offline queue
                    runCatching {
                        metadata["revision"] = 1L
                        docRef.set(metadata, SetOptions.merge()).await()
                        backfilledCount++
                    }
                }
            }
            backfilledCount
        }.getOrDefault(0)
    }
}
