package com.kaynanamtv.data.sync

import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import com.kaynanamtv.data.local.dao.FavoriteDao
import com.kaynanamtv.data.local.dao.PlaybackHistoryDao
import com.kaynanamtv.data.local.entity.FavoriteEntity
import com.kaynanamtv.data.local.entity.PlaybackHistoryEntity
import com.kaynanamtv.domain.model.ContentType
import java.util.concurrent.ConcurrentHashMap
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await

/**
 * Manages asynchronous, non-blocking multi-device user state synchronization for Premium users.
 *
 * Synchronizes:
 * - Favorites
 * - Movie & Series watch progress / resume points
 * - Watched state
 *
 * Architecture:
 * - Local-First: Local SQLite Room DB is the immediate source of truth.
 * - Non-Blocking: Cloud writes are dispatched asynchronously in the background.
 * - No Per-Second Firestore Spam: Playback progress is recorded only on pause, stop, or completion.
 */
@Singleton
class CloudUserStateSyncManager @Inject constructor(
    private val favoriteDao: FavoriteDao,
    private val playbackHistoryDao: PlaybackHistoryDao
) {
    private val TAG = "CloudUserStateSync"
    private val syncScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lastSyncedProgressKeys = ConcurrentHashMap<String, Long>()

    /**
     * Records watch progress to local Room DB immediately, and asynchronously syncs to Firestore for Premium accounts.
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
        forceCloudSync: Boolean = false
    ) {
        if (contentId <= 0L || providerId <= 0L) return

        syncScope.launch {
            val now = System.currentTimeMillis()
            val isCompleted = durationMs > 0L && positionMs >= (durationMs * 0.90) // 90% threshold for completed

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
            }.onFailure { Log.w(TAG, "Failed to persist local playback history", it) }

            // 2. Multi-device Cloud Sync (Async, event-driven)
            val user = FirebaseAuth.getInstance().currentUser ?: return@launch
            if (user.isAnonymous) return@launch
            val progressKey = "$providerId|$contentType|$contentId"
            val lastSynced = lastSyncedProgressKeys[progressKey] ?: 0L

            // Debounce: sync to cloud if forced (pause/stop/complete) or if more than 30s elapsed since last cloud write
            if (forceCloudSync || (now - lastSynced > 30_000L)) {
                lastSyncedProgressKeys[progressKey] = now
                runCatching {
                    val firestore = FirebaseFirestore.getInstance()
                    val cloudDocId = "$providerId-${contentType.name}-$contentId"
                    val data = hashMapOf(
                        "providerId" to providerId,
                        "contentId" to contentId,
                        "contentType" to contentType.name,
                        "resumePositionMs" to positionMs,
                        "totalDurationMs" to durationMs,
                        "seriesId" to (seriesId ?: 0L),
                        "seasonNumber" to (seasonNumber ?: 0),
                        "episodeNumber" to (episodeNumber ?: 0),
                        "isCompleted" to isCompleted,
                        "updatedAt" to now
                    )
                    firestore.collection("users").document(user.uid)
                        .collection("watch_history").document(cloudDocId)
                        .set(data, SetOptions.merge()).await()
                }.onFailure { Log.w(TAG, "Failed to sync watch progress to Firestore", it) }
            }
        }
    }

    /**
     * Syncs favorite status change to Firestore asynchronously.
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
            runCatching {
                val firestore = FirebaseFirestore.getInstance()
                val cloudDocId = "$providerId-${contentType.name}-$contentId"
                val docRef = firestore.collection("users").document(user.uid)
                    .collection("favorites").document(cloudDocId)

                if (isFavorite) {
                    val data = hashMapOf(
                        "providerId" to providerId,
                        "contentId" to contentId,
                        "contentType" to contentType.name,
                        "updatedAt" to System.currentTimeMillis()
                    )
                    docRef.set(data, SetOptions.merge()).await()
                } else {
                    docRef.delete().await()
                }
            }.onFailure { Log.w(TAG, "Failed to sync favorite change to Firestore", it) }
        }
    }

    /**
     * Reconciles remote watch history and favorites from Firestore into local Room DB.
     * Called on login or app start in the background without blocking UI or Player.
     */
    fun reconcileFromCloud() {
        val user = FirebaseAuth.getInstance().currentUser ?: return
        if (user.isAnonymous) return
        syncScope.launch {
            // Validate server auth session with forceRefresh to avoid stale/expired token PERMISSION_DENIED
            val token = runCatching { user.getIdToken(true).await() }.getOrNull()
            if (token == null || token.token.isNullOrBlank()) {
                Log.d(TAG, "Skipping cloud user state reconcile: active server auth session is not valid")
                return@launch
            }

            // 1. Reconcile Watch History
            runCatching {
                val firestore = FirebaseFirestore.getInstance()
                val historySnapshot = firestore.collection("users").document(user.uid)
                    .collection("watch_history").get().await()

                for (doc in historySnapshot.documents) {
                    val data = doc.data ?: continue
                    val providerId = (data["providerId"] as? Long) ?: continue
                    val contentId = (data["contentId"] as? Long) ?: continue
                    val contentTypeStr = (data["contentType"] as? String) ?: continue
                    val contentType = runCatching { ContentType.valueOf(contentTypeStr) }.getOrNull() ?: continue
                    val resumePositionMs = (data["resumePositionMs"] as? Long) ?: 0L
                    val totalDurationMs = (data["totalDurationMs"] as? Long) ?: 0L
                    val updatedAt = (data["updatedAt"] as? Long) ?: 0L
                    val seriesId = (data["seriesId"] as? Long)?.takeIf { it > 0L }
                    val seasonNumber = (data["seasonNumber"] as? Long)?.toInt()
                    val episodeNumber = (data["episodeNumber"] as? Long)?.toInt()
                    val isCompleted = (data["isCompleted"] as? Boolean) ?: false

                    val local = playbackHistoryDao.get(contentId, contentTypeStr, providerId)
                    if (local == null || updatedAt > local.lastWatchedAt) {
                        playbackHistoryDao.insertOrUpdate(
                            PlaybackHistoryEntity(
                                providerId = providerId,
                                contentId = contentId,
                                contentType = contentType,
                                resumePositionMs = resumePositionMs,
                                totalDurationMs = totalDurationMs,
                                lastWatchedAt = updatedAt,
                                seriesId = seriesId,
                                seasonNumber = seasonNumber,
                                episodeNumber = episodeNumber,
                                watchedStatus = if (isCompleted) "COMPLETED" else "IN_PROGRESS"
                            )
                        )
                    }
                }
            }.onFailure { Log.w(TAG, "Failed to reconcile watch history from cloud", it) }

            // 2. Reconcile Favorites
            runCatching {
                val firestore = FirebaseFirestore.getInstance()
                val favoritesSnapshot = firestore.collection("users").document(user.uid)
                    .collection("favorites").get().await()

                for (doc in favoritesSnapshot.documents) {
                    val data = doc.data ?: continue
                    val providerId = (data["providerId"] as? Long) ?: continue
                    val contentId = (data["contentId"] as? Long) ?: continue
                    val contentTypeStr = (data["contentType"] as? String) ?: continue
                    val contentType = runCatching { ContentType.valueOf(contentTypeStr) }.getOrNull() ?: continue

                    if (favoriteDao.get(providerId, contentId, contentTypeStr, null) == null) {
                        favoriteDao.insert(
                            FavoriteEntity(
                                providerId = providerId,
                                contentId = contentId,
                                contentType = contentType,
                                groupId = null,
                                position = 0
                            )
                        )
                    }
                }
            }.onFailure { Log.w(TAG, "Failed to reconcile favorites from cloud", it) }
        }
    }
}
