package com.kaynanamtv.data.sync

import android.content.Context
import android.util.Log
import androidx.work.BackoffPolicy
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import java.util.concurrent.TimeUnit

object BackgroundSyncScheduler {
    private const val TAG = "BackgroundSyncScheduler"
    const val UNIQUE_WORK_NAME = "provider-sync-worker"

    fun updateSchedule(
        context: Context,
        enabled: Boolean,
        intervalHours: Int = 6,
        wifiOnly: Boolean = false
    ) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) {
            Log.d(TAG, "Cancelling periodic background sync work")
            wm.cancelUniqueWork(UNIQUE_WORK_NAME)
            return
        }

        val networkType = if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(networkType)
            .build()

        val safeHours = intervalHours.coerceAtLeast(1)
        val request = PeriodicWorkRequestBuilder<ProviderSyncWorker>(safeHours.toLong(), TimeUnit.HOURS)
            .setConstraints(constraints)
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                10,
                TimeUnit.MINUTES
            )
            .build()

        Log.d(TAG, "Scheduling periodic background sync: interval=$safeHours hours, wifiOnly=$wifiOnly")
        wm.enqueueUniquePeriodicWork(
            UNIQUE_WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request
        )
    }

    fun cancel(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(UNIQUE_WORK_NAME)
    }
}
