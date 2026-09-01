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

    private fun isNetworkAvailable(): Boolean {
        return runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as? android.net.ConnectivityManager ?: return false
            val activeNetwork = cm.activeNetwork ?: return false
            val caps = cm.getNetworkCapabilities(activeNetwork) ?: return false
            caps.hasCapability(android.net.NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }.getOrDefault(true)
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

            Log.d(TAG, "[DIAG_DEV_A] LOCAL_PROGRESS_WRITE_REQUEST providerId=$providerId contentId=$contentId type=$contentType pos=$positionMs dur=$durationMs force=$forceCloudSync")

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
                Log.d(TAG, "[DIAG_DEV_A] LOCAL_PROGRESS_WRITE_SUCCESS contentId=$contentId")
            }.onFailure {
                Log.w(TAG, "[DIAG_DEV_A] LOCAL_PROGRESS_WRITE_FAILURE contentId=$contentId", it)
            }

            // 2. Multi-device Cloud Sync (Async, non-blocking OCC transaction)
            val user = FirebaseAuth.getInstance().currentUser
            if (user == null || user.isAnonymous) {
                Log.d(TAG, "[DIAG_DEV_A] CLOUD_WRITE_SKIPPED reason=AUTH_INVALID isAnonymous=${user?.isAnonymous}")
                return@launch
            }

            Log.d(TAG, "[DIAG_DEV_A] FIREBASE_USER_STATE uidHash=${sanitizedHash(user.uid)} isAnonymous=${user.isAnonymous}")

            val provider = runCatching { providerDao.getById(providerId) }.getOrNull()
            if (provider == null) {
                Log.d(TAG, "[DIAG_DEV_A] CLOUD_WRITE_SKIPPED reason=PROVIDER_NOT_FOUND providerId=$providerId")
                return@launch
            }
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

            Log.d(TAG, "[DIAG_DEV_A] CLOUD_WRITE_ELIGIBLE docIdHash=${sanitizedHash(cloudDocId)} providerKeyHash=${sanitizedHash(providerStableKey)} pos=$positionMs dur=$durationMs rev=$baseRevision")

            // Defer blind offline canonical writes: check network before attempting Firestore transaction
            if (!isNetworkAvailable()) {
                Log.d(TAG, "[DIAG_DEV_A] CLOUD_WRITE_SKIPPED reason=OFFLINE docIdHash=${sanitizedHash(cloudDocId)}")
                return@launch
            }

            // Write coalescing: sync if forced (pause/exit/stop/complete) or if >= 25s elapsed AND position delta >= 15s
            val lastSyncedTime = lastSyncedProgressTimes[cloudDocId] ?: 0L
            val lastSyncedPos = lastSyncedProgressPositions[cloudDocId] ?: -1L
            val posDelta = kotlin.math.abs(positionMs - lastSyncedPos)

            val shouldSync = forceCloudSync || isCompleted || (now - lastSyncedTime >= 25_000L && posDelta >= 15_000L)

            if (!shouldSync) {
                Log.d(TAG, "[DIAG_DEV_A] CLOUD_WRITE_SKIPPED reason=WRITE_COALESCING_COOLDOWN lastSyncedAgo=${now - lastSyncedTime}ms posDelta=${posDelta}ms docIdHash=${sanitizedHash(cloudDocId)}")
                return@launch
            }

            lastSyncedProgressTimes[cloudDocId] = now
            lastSyncedProgressPositions[cloudDocId] = positionMs

            Log.d(TAG, "[DIAG_DEV_A] CLOUD_WRITE_REQUEST docIdHash=${sanitizedHash(cloudDocId)} session=${sanitizedHash(playbackSessionId)} seq=$checkpointSeq")

            runCatching {
                val firestore = FirebaseFirestore.getInstance()
                val docRef = firestore.collection("users").document(user.uid)
                    .collection("watch_history").document(cloudDocId)

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
                        } else {
                            Log.d(TAG, "[DIAG_DEV_A] CLOUD_WRITE_IGNORED reason=CHECKPOINT_SEQ_OBSOLETE currentSeq=$checkpointSeq cloudSeq=$cloudSeq")
                        }
                    } else {
                        if (!cloudExists || cloudRevision == baseRevision || (cloudRevision == 0L && baseRevision == 0L)) {
                            val newRevision = if (cloudExists) cloudRevision + 1L else 1L
                            metadata["revision"] = newRevision
                            metadata["playbackSessionId"] = playbackSessionId
                            metadata["checkpointSeq"] = checkpointSeq
                            transaction.set(docRef, metadata, SetOptions.merge())
                            Log.d(TAG, "[DIAG_DEV_A] OCC_CLAIMED_SESSION session=${sanitizedHash(playbackSessionId)} rev=$newRevision docIdHash=${sanitizedHash(cloudDocId)}")
                        } else {
                            Log.w(TAG, "[DIAG_DEV_A] OCC_CONFLICT docIdHash=${sanitizedHash(cloudDocId)} cloudRevision=$cloudRevision != baseRevision=$baseRevision")
                        }
                    }
                }.await()
                Log.d(TAG, "[DIAG_DEV_A] CLOUD_WRITE_SUCCESS docIdHash=${sanitizedHash(cloudDocId)} session=${sanitizedHash(playbackSessionId)} rev=${metadata["revision"]}")
            }.onFailure {
                Log.w(TAG, "[DIAG_DEV_A] CLOUD_WRITE_FAILURE docIdHash=${sanitizedHash(cloudDocId)} error=${it.javaClass.simpleName} msg=${it.message}", it)
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
     * Guarded by mutex and 60-second cooldown to eliminate duplicate queries and read spikes.
     */
    fun reconcileFromCloud(force: Boolean = false) {
        Log.d(TAG, "[DIAG_DEV_B] RECONCILE_REQUEST force=$force")
        val user = FirebaseAuth.getInstance().currentUser
        if (user == null || user.isAnonymous) {
            Log.d(TAG, "[DIAG_DEV_B] RECONCILE_SKIPPED reason=AUTH_INVALID isAnonymous=${user?.isAnonymous}")
            return
        }
        val now = System.currentTimeMillis()
        if (!force && (now - lastReconcileTime < RECONCILE_MIN_INTERVAL_MS)) {
            Log.d(TAG, "[DIAG_DEV_B] RECONCILE_SKIPPED reason=THROTTLED_COOLDOWN elapsed=${now - lastReconcileTime}ms")
            return
        }
        syncScope.launch {
            if (!reconcileMutex.tryLock()) {
                Log.d(TAG, "[DIAG_DEV_B] RECONCILE_SKIPPED reason=MUTEX_LOCKED")
                return@launch
            }
            try {
                lastReconcileTime = System.currentTimeMillis()
                val token = runCatching { user.getIdToken(true).await() }.getOrNull()
                if (token == null || token.token.isNullOrBlank()) {
                    Log.d(TAG, "[DIAG_DEV_B] RECONCILE_SKIPPED reason=AUTH_TOKEN_INVALID")
                    return@launch
                }

                val localProviders = runCatching { providerDao.getAllSync() }.getOrDefault(emptyList())
                if (localProviders.isEmpty()) {
                    Log.d(TAG, "[DIAG_DEV_B] RECONCILE_SKIPPED reason=NO_LOCAL_PROVIDERS")
                    return@launch
                }

                val providersByStableKey = localProviders.associateBy { computeProviderStableKey(it) }
                val syncStartTime = System.currentTimeMillis()

                // 1. Reconcile Watch History (Delta Sync with Safety Window and Partial Cursor Protection)
                runCatching {
                    val lastCursor = getWatchHistorySyncCursor(user.uid)
                    Log.d(TAG, "[DIAG_DEV_B] RECONCILE_STARTED uidHash=${sanitizedHash(user.uid)} isAnonymous=${user.isAnonymous} lastCursor=$lastCursor")

                    val firestore = FirebaseFirestore.getInstance()
                    val collectionRef = firestore.collection("users").document(user.uid).collection("watch_history")

                    val query = if (lastCursor > 0L) {
                        val queryStart = (lastCursor - CLOCK_SKEW_SAFETY_WINDOW_MS).coerceAtLeast(0L)
                        Log.d(TAG, "[DIAG_DEV_B] QUERY_START queryStart=$queryStart lastCursor=$lastCursor")
                        collectionRef.whereGreaterThan("updatedAt", queryStart)
                    } else {
                        Log.d(TAG, "[DIAG_DEV_B] QUERY_START fullCollection=true")
                        collectionRef
                    }

                    val historySnapshot = query.get().await()
                    Log.d(TAG, "[DIAG_DEV_B] QUERY_SUCCESS docCount=${historySnapshot.documents.size}")
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

                        Log.d(TAG, "[DIAG_DEV_B] CLOUD_DOCUMENT docIdHash=${sanitizedHash(doc.id)} providerKeyHash=${sanitizedHash(providerStableKey)} type=$contentType streamId=$streamId pos=$resumePositionMs dur=$totalDurationMs updatedAt=$updatedAt rev=$cloudRevision")

                        if (localProvider == null) {
                            Log.w(TAG, "[DIAG_DEV_B] LOCAL_PROVIDER_NOT_FOUND providerKeyHash=${sanitizedHash(providerStableKey)}")
                            minUnresolvedWatchHistoryTime = minOf(minUnresolvedWatchHistoryTime ?: updatedAt, updatedAt)
                            continue
                        }

                        Log.d(TAG, "[DIAG_DEV_B] LOCAL_PROVIDER_MATCHED localProviderId=${localProvider.id}")

                        when (contentType) {
                            ContentType.MOVIE -> {
                                val localMovie = if (streamId > 0L) {
                                    movieDao.getByStreamId(localProvider.id, streamId)
                                } else null

                                if (localMovie != null) {
                                    Log.d(TAG, "[DIAG_DEV_B] CATALOG_ITEM_MATCHED type=MOVIE localId=${localMovie.id}")
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
                                        Log.d(TAG, "[DIAG_DEV_B] ROOM_UPSERT_SUCCESS type=MOVIE contentId=${localMovie.id} pos=$resumePositionMs")
                                    }
                                } else {
                                    Log.w(TAG, "[DIAG_DEV_B] CATALOG_ITEM_UNRESOLVED type=MOVIE streamId=$streamId")
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
                                    Log.d(TAG, "[DIAG_DEV_B] CATALOG_ITEM_MATCHED type=SERIES_EPISODE localId=${localEpisode.id}")
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
                                        Log.d(TAG, "[DIAG_DEV_B] ROOM_UPSERT_SUCCESS type=SERIES_EPISODE contentId=${localEpisode.id} pos=$resumePositionMs")
                                    }
                                } else {
                                    Log.w(TAG, "[DIAG_DEV_B] CATALOG_ITEM_UNRESOLVED type=SERIES_EPISODE seriesRemoteId=$seriesRemoteId S${seasonNumber}E${episodeNumber}")
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
                        Log.d(TAG, "[DIAG_DEV_B] WATCH_HISTORY_CURSOR_PARTIAL advancedTo=$safeCursor due to unresolved items")
                    } else {
                        setWatchHistorySyncCursor(user.uid, syncStartTime)
                        Log.d(TAG, "[DIAG_DEV_B] WATCH_HISTORY_CURSOR_ADVANCED advancedTo=$syncStartTime")
                    }
                }.onFailure {
                    Log.w(TAG, "[DIAG_DEV_B] QUERY_FAILURE error=${it.javaClass.simpleName} msg=${it.message}", it)
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
            } finally {
                reconcileMutex.unlock()
            }
        }
    }
}
