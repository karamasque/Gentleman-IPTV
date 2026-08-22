package com.kaynanamtv.data.sync

import android.content.Context
import android.database.sqlite.SQLiteException
import android.util.Log
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.kaynanamtv.data.preferences.DatabaseMaintenanceSnapshot
import com.kaynanamtv.data.preferences.PreferencesRepository
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent

class SyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    @EntryPoint
    @InstallIn(SingletonComponent::class)
    interface SyncWorkerEntryPoint {
        fun databaseMaintenanceManager(): DatabaseMaintenanceManager
        fun preferencesRepository(): PreferencesRepository
        fun playbackContentionManager(): com.kaynanamtv.domain.manager.PlaybackContentionManager
    }

    override suspend fun doWork(): Result {
        val entryPoint = EntryPointAccessors.fromApplication(applicationContext, SyncWorkerEntryPoint::class.java)
        if (entryPoint.playbackContentionManager().shouldDeferBackgroundWork() ||
            com.kaynanamtv.data.remote.stalker.StalkerTrafficCoordinator.isAnyPlaybackActive()
        ) {
            Log.w("SyncWorker", "Deferring data maintenance: playback is currently active (P0 priority)")
            return Result.retry()
        }
        Log.d("SyncWorker", "Starting data maintenance...")
        return try {
            val report = entryPoint.databaseMaintenanceManager().runDailyMaintenance()
            entryPoint.preferencesRepository().setLastMaintenanceSnapshot(
                DatabaseMaintenanceSnapshot(
                    ranAt = System.currentTimeMillis(),
                    deletedPrograms = report.deletedPrograms,
                    deletedExternalProgrammes = report.deletedExternalProgrammes,
                    deletedOrphanEpisodes = report.deletedOrphanEpisodes,
                    deletedStaleFavorites = report.deletedStaleFavorites,
                    vacuumRan = report.vacuumRan,
                    mainDbBytes = report.statsAfterVacuum.mainDbBytes,
                    walBytes = report.statsAfterVacuum.walBytes,
                    reclaimableBytes = report.statsAfterVacuum.reclaimableBytes,
                    channelRows = report.tableStats.channels,
                    movieRows = report.tableStats.movies,
                    seriesRows = report.tableStats.series,
                    episodeRows = report.tableStats.episodes,
                    programRows = report.tableStats.programs,
                    epgProgrammeRows = report.tableStats.epgProgrammes,
                    playbackHistoryRows = report.tableStats.playbackHistory,
                    favoriteRows = report.tableStats.favorites
                )
            )
            Log.d(
                "SyncWorker",
                "Maintenance complete: oldPrograms=${report.deletedPrograms}, orphanEpisodes=${report.deletedOrphanEpisodes}, " +
                    "staleFavorites=${report.deletedStaleFavorites}, vacuumRan=${report.vacuumRan}, " +
                    "dbBytes=${report.statsAfterVacuum.mainDbBytes}, walBytes=${report.statsAfterVacuum.walBytes}, " +
                    "reclaimableBytes=${report.statsAfterVacuum.reclaimableBytes}"
            )
            Result.success()
        } catch (e: Exception) {
            Log.e("SyncWorker", "Failed to run data maintenance", e)
            if (shouldRetry(e)) Result.retry() else Result.failure()
        }
    }

    private fun shouldRetry(error: Throwable): Boolean {
        return when (error) {
            is java.io.IOException -> true
            is SQLiteException -> error.message.orEmpty().contains("locked", ignoreCase = true) ||
                error.message.orEmpty().contains("busy", ignoreCase = true)
            else -> false
        }
    }
}
