package com.kaynanamtv.data.sync

import android.content.Context
import android.database.sqlite.SQLiteException
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkRequest
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.kaynanamtv.data.local.dao.ProviderDao
import com.kaynanamtv.data.local.dao.ChannelDao
import com.kaynanamtv.data.local.dao.CategoryDao
import com.kaynanamtv.data.local.dao.XtreamIndexJobDao
import com.kaynanamtv.data.local.dao.XtreamLiveOnboardingDao
import com.kaynanamtv.data.remote.stalker.StalkerTrafficCoordinator
import com.kaynanamtv.domain.model.ProviderStatus
import com.kaynanamtv.domain.model.ContentType
import com.kaynanamtv.domain.model.ProviderEpgSyncMode
import com.kaynanamtv.domain.model.ProviderType
import com.kaynanamtv.domain.model.SyncState
import com.kaynanamtv.domain.repository.SyncMetadataRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import java.util.concurrent.TimeUnit

internal suspend fun reconcileTargetedProviderStatus(
    providerDao: ProviderDao,
    channelDao: ChannelDao,
    categoryDao: CategoryDao,
    syncMetadataRepository: SyncMetadataRepository,
    syncManager: SyncManager,
    provider: com.kaynanamtv.data.local.entity.ProviderEntity,
    result: com.kaynanamtv.domain.model.Result<Unit>,
    currentTimeMillis: Long = System.currentTimeMillis()
) {
    when (result) {
        is com.kaynanamtv.domain.model.Result.Success -> {
            val finalStatus = if (syncManager.currentSyncState(provider.id) is SyncState.Partial) {
                ProviderStatus.PARTIAL
            } else {
                ProviderStatus.ACTIVE
            }
            if (!hasUsableLiveCatalogForActivation(
                    provider.id,
                    provider.type,
                    channelDao,
                    categoryDao,
                    syncMetadataRepository
                )) {
                providerDao.update(
                    provider.copy(
                        isActive = false,
                        status = ProviderStatus.PARTIAL,
                        lastSyncedAt = currentTimeMillis
                    )
                )
                return
            }
            providerDao.update(
                provider.copy(
                    isActive = true,
                    status = finalStatus,
                    lastSyncedAt = currentTimeMillis
                )
            )
        }
        is com.kaynanamtv.domain.model.Result.Error -> {
            if (provider.status != ProviderStatus.PARTIAL) {
                providerDao.update(provider.copy(isActive = false, status = ProviderStatus.ERROR))
            }
        }
        is com.kaynanamtv.domain.model.Result.Loading -> Unit
    }
}

internal suspend fun shouldTrackInitialLiveOnboarding(
    provider: com.kaynanamtv.data.local.entity.ProviderEntity,
    onboardingDao: XtreamLiveOnboardingDao
): Boolean = provider.type == ProviderType.XTREAM_CODES &&
    onboardingDao.getIncompleteByProvider(provider.id) != null

class ProviderSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface ProviderSyncWorkerEntryPoint {
        fun providerDao(): ProviderDao
        fun channelDao(): ChannelDao
        fun categoryDao(): CategoryDao
        fun syncManager(): SyncManager
        fun syncMetadataRepository(): SyncMetadataRepository
        fun xtreamIndexJobDao(): XtreamIndexJobDao
        fun xtreamLiveOnboardingDao(): XtreamLiveOnboardingDao
        fun preferencesRepository(): com.kaynanamtv.data.preferences.PreferencesRepository
        fun playbackContentionManager(): com.kaynanamtv.domain.manager.PlaybackContentionManager
    }

    override suspend fun doWork(): Result {
        return try {
            val entryPoint = EntryPointAccessors.fromApplication(
                applicationContext,
                ProviderSyncWorkerEntryPoint::class.java
            )
            if (entryPoint.playbackContentionManager().shouldDeferBackgroundWork()) {
                Log.d(TAG, "Deferring background sync worker: playback is currently active (P0 priority)")
                return Result.retry()
            }
            val requestedProviderId = inputData.getLong(KEY_PROVIDER_ID, INVALID_PROVIDER_ID)
            val providers = if (requestedProviderId != INVALID_PROVIDER_ID) {
                entryPoint.providerDao().getById(requestedProviderId)?.let(::listOf).orEmpty()
            } else {
                entryPoint.providerDao().getAllSync()
            }
            if (providers.isEmpty()) {
                return Result.success()
            }

            var sawRetryableFailure = false
            providers.forEach { provider ->
                if (entryPoint.playbackContentionManager().shouldDeferBackgroundWork() ||
                    StalkerTrafficCoordinator.shouldDeferCatalogFetch(provider.id) ||
                    StalkerTrafficCoordinator.isAnyPlaybackActive()
                ) {
                    Log.d(TAG, "Deferring background sync worker for provider ${provider.id} due to active playback")
                    sawRetryableFailure = true
                    return@forEach
                }
                val trackInitialLiveOnboarding = shouldTrackInitialLiveOnboarding(
                    provider = provider,
                    onboardingDao = entryPoint.xtreamLiveOnboardingDao()
                )
                val force = inputData.getBoolean(KEY_FORCE, false)
                val result = if (force && requestedProviderId == provider.id) {
                    // Manuel yenileme — staleness kontrolü atlanır
                    entryPoint.syncManager().sync(
                        provider.id,
                        force = true,
                        trackInitialLiveOnboarding = trackInitialLiveOnboarding
                    )
                } else if (provider.type == ProviderType.XTREAM_CODES) {
                    syncXtreamProviderIfStale(entryPoint, provider)
                } else if (provider.type == ProviderType.STALKER_PORTAL) {
                    syncStalkerProviderIfStale(entryPoint, provider)
                } else if (provider.type == ProviderType.M3U) {
                    syncM3uProviderIfStale(entryPoint, provider)
                } else {
                    syncJellyfinProviderIfStale(entryPoint, provider)
                }
                if (requestedProviderId == provider.id) {
                    reconcileTargetedProviderStatus(entryPoint, provider, result)
                }
                when (result) {
                    is com.kaynanamtv.domain.model.Result.Error -> {
                        Log.w(TAG, "Provider sync worker failed for provider ${provider.id}: ${result.message}")
                        if (shouldRetry(result.exception)) {
                            sawRetryableFailure = true
                        }
                    }
                    else -> Unit
                }
            }

            if (sawRetryableFailure) {
                Result.retry()
            } else {
                entryPoint.preferencesRepository().setLastBackgroundSyncTimestamp(System.currentTimeMillis())
                Result.success()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Provider sync worker failed", e)
            if (shouldRetry(e)) Result.retry() else Result.failure()
        }
    }

    private fun shouldRetry(error: Throwable?): Boolean {
        return when (error) {
            is java.io.IOException -> true
            is SQLiteException -> error.message.orEmpty().contains("locked", ignoreCase = true) ||
                error.message.orEmpty().contains("busy", ignoreCase = true)
            else -> false
        }
    }

    companion object {
        private const val TAG = "ProviderSyncWorker"
        private const val STALE_RUNNING_JOB_MILLIS = 15 * 60 * 1000L
        private const val UNIQUE_WORK_NAME = "provider-sync-worker"
        private const val UNIQUE_LAUNCH_STALE_WORK_NAME = "provider-sync-launch-stale-check"
        private const val UNIQUE_PROVIDER_WORK_PREFIX = "provider-sync-provider-"
        private const val KEY_PROVIDER_ID = "provider_id"
        // force=true → staleness guard atlanır (manuel yenileme için)
        private const val KEY_FORCE = "force"
        private const val INVALID_PROVIDER_ID = -1L

        fun enqueuePeriodic(context: Context) {
            val request = PeriodicWorkRequestBuilder<ProviderSyncWorker>(6, TimeUnit.HOURS)
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    10,
                    TimeUnit.MINUTES
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UNIQUE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }

        fun enqueueLaunchStaleCheck(context: Context) {
            val request = OneTimeWorkRequestBuilder<ProviderSyncWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setInitialDelay(10, TimeUnit.SECONDS)
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_LAUNCH_STALE_WORK_NAME,
                ExistingWorkPolicy.KEEP,
                request
            )
        }

        /**
         * Onboarding/resume sonrası yeniden deneme — staleness guard geçerli.
         * Zaten başarıyla senkronize edilmiş bir provider üzeri tekrar çıkmaz.
         */
        fun enqueueProvider(context: Context, providerId: Long) {
            val request = OneTimeWorkRequestBuilder<ProviderSyncWorker>()
                .setInputData(workDataOf(KEY_PROVIDER_ID to providerId, KEY_FORCE to false))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_PROVIDER_WORK_PREFIX + providerId,
                ExistingWorkPolicy.KEEP, // KEEP → onboarding zaten yapıldıysa queue'ya girmez
                request
            )
        }

        /**
         * Manuel kullanıcı yenileme — staleness guard atlanır.
         */
        fun enqueueProviderForce(context: Context, providerId: Long) {
            val request = OneTimeWorkRequestBuilder<ProviderSyncWorker>()
                .setInputData(workDataOf(KEY_PROVIDER_ID to providerId, KEY_FORCE to true))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            WorkManager.getInstance(context).enqueueUniqueWork(
                UNIQUE_PROVIDER_WORK_PREFIX + providerId,
                ExistingWorkPolicy.REPLACE,
                request
            )
        }

        fun enqueueProvidersSequentially(context: Context, providerIds: List<Long>) {
            if (providerIds.isEmpty()) return
            val workManager = WorkManager.getInstance(context)
            
            val firstId = providerIds.first()
            val firstRequest = OneTimeWorkRequestBuilder<ProviderSyncWorker>()
                .setInputData(workDataOf(KEY_PROVIDER_ID to firstId))
                .setConstraints(
                    Constraints.Builder()
                        .setRequiredNetworkType(NetworkType.CONNECTED)
                        .build()
                )
                .setBackoffCriteria(
                    BackoffPolicy.EXPONENTIAL,
                    WorkRequest.MIN_BACKOFF_MILLIS,
                    TimeUnit.MILLISECONDS
                )
                .build()

            var continuation = workManager.beginUniqueWork(
                UNIQUE_PROVIDER_WORK_PREFIX + firstId,
                ExistingWorkPolicy.REPLACE,
                firstRequest
            )

            for (i in 1 until providerIds.size) {
                val nextId = providerIds[i]
                val nextRequest = OneTimeWorkRequestBuilder<ProviderSyncWorker>()
                    .setInputData(workDataOf(KEY_PROVIDER_ID to nextId))
                    .setConstraints(
                        Constraints.Builder()
                            .setRequiredNetworkType(NetworkType.CONNECTED)
                            .build()
                    )
                    .setBackoffCriteria(
                        BackoffPolicy.EXPONENTIAL,
                        WorkRequest.MIN_BACKOFF_MILLIS,
                        TimeUnit.MILLISECONDS
                    )
                    .build()
                continuation = continuation.then(nextRequest)
            }
            continuation.enqueue()
        }
    }

    private suspend fun syncXtreamProviderIfStale(
        entryPoint: ProviderSyncWorkerEntryPoint,
        provider: com.kaynanamtv.data.local.entity.ProviderEntity
    ): com.kaynanamtv.domain.model.Result<Unit> {
        val now = System.currentTimeMillis()
        if (shouldTrackInitialLiveOnboarding(provider, entryPoint.xtreamLiveOnboardingDao())) {
            return entryPoint.syncManager().sync(
                provider.id,
                force = false,
                trackInitialLiveOnboarding = true
            )
        }
        val metadata = entryPoint.syncMetadataRepository().getMetadata(provider.id)
        val lastSyncAttempt = metadata?.lastLiveSync ?: 0L
        if ((now - lastSyncAttempt) < 15L * 60 * 1000L) {
            return com.kaynanamtv.domain.model.Result.success(Unit)
        }
        val liveStale = ContentCachePolicy.shouldRefresh(
            metadata?.lastLiveSuccess ?: 0L,
            ContentCachePolicy.CATALOG_TTL_MILLIS,
            now
        )
        val epgStale = provider.epgSyncMode != ProviderEpgSyncMode.SKIP &&
            ContentCachePolicy.shouldRefresh(
                metadata?.lastEpgSuccess ?: 0L,
                ContentCachePolicy.EPG_TTL_MILLIS,
                now
            )
        val movieIndexDue = shouldRunIndexJob(entryPoint, provider.id, ContentType.MOVIE, now)
        val seriesIndexDue = shouldRunIndexJob(entryPoint, provider.id, ContentType.SERIES, now)

        if (!provider.isActive) {
            return com.kaynanamtv.domain.model.Result.success(Unit)
        }

        if (liveStale) {
            when (val liveResult = entryPoint.syncManager().retrySection(
                provider.id,
                SyncRepairSection.LIVE,
                syncReason = XtreamLiveSyncReason.BACKGROUND_STALE
            )) {
                is com.kaynanamtv.domain.model.Result.Error -> return liveResult
                else -> Unit
            }
        }
        if (epgStale) {
            when (val epgResult = entryPoint.syncManager().syncEpg(provider.id, force = false)) {
                is com.kaynanamtv.domain.model.Result.Error -> return epgResult
                else -> Unit
            }
        }
        if (movieIndexDue) {
            entryPoint.syncManager().scheduleXtreamIndexSync(provider.id, ContentType.MOVIE)
        }
        if (seriesIndexDue) {
            entryPoint.syncManager().scheduleXtreamIndexSync(provider.id, ContentType.SERIES)
        }
        return com.kaynanamtv.domain.model.Result.success(Unit)
    }

    private suspend fun shouldRunIndexJob(
        entryPoint: ProviderSyncWorkerEntryPoint,
        providerId: Long,
        section: ContentType,
        now: Long
    ): Boolean {
        val job = entryPoint.xtreamIndexJobDao().get(providerId, section.name) ?: return true
        if (job.state in setOf("QUEUED", "STALE", "FAILED_RETRYABLE")) return true
        if (job.state == "RUNNING" && (now - job.updatedAt) < STALE_RUNNING_JOB_MILLIS) return false
        return ContentCachePolicy.shouldRefresh(job.lastSuccessAt, ContentCachePolicy.CATALOG_TTL_MILLIS, now)
    }

    private suspend fun syncStalkerProviderIfStale(
        entryPoint: ProviderSyncWorkerEntryPoint,
        provider: com.kaynanamtv.data.local.entity.ProviderEntity
    ): com.kaynanamtv.domain.model.Result<Unit> {
        val now = System.currentTimeMillis()
        val metadata = entryPoint.syncMetadataRepository().getMetadata(provider.id)
        val lastSyncAttempt = metadata?.lastLiveSync ?: 0L
        if ((now - lastSyncAttempt) < 15L * 60 * 1000L) {
            return com.kaynanamtv.domain.model.Result.success(Unit)
        }
        val liveStale = ContentCachePolicy.shouldRefresh(
            metadata?.lastLiveSuccess ?: 0L,
            ContentCachePolicy.CATALOG_TTL_MILLIS,
            now
        )
        val epgStale = provider.epgSyncMode != ProviderEpgSyncMode.SKIP &&
            ContentCachePolicy.shouldRefresh(
                metadata?.lastEpgSuccess ?: 0L,
                ContentCachePolicy.EPG_TTL_MILLIS,
                now
            )
        val movieIndexDue = shouldRunIndexJob(entryPoint, provider.id, ContentType.MOVIE, now)
        val seriesIndexDue = shouldRunIndexJob(entryPoint, provider.id, ContentType.SERIES, now)

        if (!provider.isActive) {
            return com.kaynanamtv.domain.model.Result.success(Unit)
        }

        if (liveStale) {
            when (val liveResult = entryPoint.syncManager().retrySection(provider.id, SyncRepairSection.LIVE)) {
                is com.kaynanamtv.domain.model.Result.Error -> return liveResult
                else -> Unit
            }
        }
        if (movieIndexDue) {
            entryPoint.syncManager().scheduleStalkerIndexSync(provider.id, ContentType.MOVIE)
        }
        if (seriesIndexDue) {
            entryPoint.syncManager().scheduleStalkerIndexSync(provider.id, ContentType.SERIES)
        }
        if (epgStale) {
            entryPoint.syncManager().scheduleBackgroundEpgSync(provider.id)
        }
        return com.kaynanamtv.domain.model.Result.success(Unit)
    }

    private suspend fun reconcileTargetedProviderStatus(
        entryPoint: ProviderSyncWorkerEntryPoint,
        provider: com.kaynanamtv.data.local.entity.ProviderEntity,
        result: com.kaynanamtv.domain.model.Result<Unit>
    ) {
        reconcileTargetedProviderStatus(
            providerDao = entryPoint.providerDao(),
            channelDao = entryPoint.channelDao(),
            categoryDao = entryPoint.categoryDao(),
            syncMetadataRepository = entryPoint.syncMetadataRepository(),
            syncManager = entryPoint.syncManager(),
            provider = provider,
            result = result
        )
    }

    private suspend fun syncM3uProviderIfStale(
        entryPoint: ProviderSyncWorkerEntryPoint,
        provider: com.kaynanamtv.data.local.entity.ProviderEntity
    ): com.kaynanamtv.domain.model.Result<Unit> {
        if (!provider.isActive) {
            return com.kaynanamtv.domain.model.Result.success(Unit)
        }
        val now = System.currentTimeMillis()
        val metadata = entryPoint.syncMetadataRepository().getMetadata(provider.id)
        val lastSyncAttempt = metadata?.lastLiveSync ?: 0L
        if ((now - lastSyncAttempt) < 15L * 60 * 1000L) {
            return com.kaynanamtv.domain.model.Result.success(Unit)
        }
        val liveStale = ContentCachePolicy.shouldRefresh(
            metadata?.lastLiveSuccess ?: 0L,
            ContentCachePolicy.CATALOG_TTL_MILLIS,
            now
        )
        val epgStale = provider.epgSyncMode != ProviderEpgSyncMode.SKIP &&
            ContentCachePolicy.shouldRefresh(
                metadata?.lastEpgSuccess ?: 0L,
                ContentCachePolicy.EPG_TTL_MILLIS,
                now
            )

        if (liveStale || epgStale) {
            return entryPoint.syncManager().sync(provider.id, force = false)
        }
        return com.kaynanamtv.domain.model.Result.success(Unit)
    }

    private suspend fun syncJellyfinProviderIfStale(
        entryPoint: ProviderSyncWorkerEntryPoint,
        provider: com.kaynanamtv.data.local.entity.ProviderEntity
    ): com.kaynanamtv.domain.model.Result<Unit> {
        if (!provider.isActive) {
            return com.kaynanamtv.domain.model.Result.success(Unit)
        }
        val now = System.currentTimeMillis()
        val metadata = entryPoint.syncMetadataRepository().getMetadata(provider.id)
        val stale = ContentCachePolicy.shouldRefresh(
            metadata?.lastLiveSuccess ?: 0L,
            ContentCachePolicy.CATALOG_TTL_MILLIS,
            now
        )
        if (stale) {
            return entryPoint.syncManager().sync(provider.id, force = false)
        }
        return com.kaynanamtv.domain.model.Result.success(Unit)
    }
}
