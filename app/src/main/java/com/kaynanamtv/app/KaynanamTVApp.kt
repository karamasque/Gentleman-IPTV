package com.kaynanamtv.app

import android.app.Application
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.kaynanamtv.app.diagnostics.CrashReportStore
import com.kaynanamtv.app.diagnostics.RuntimeDiagnosticsManager
import com.kaynanamtv.app.update.GitHubReleaseChecker
import com.kaynanamtv.app.update.isRemoteVersionNewer
import com.kaynanamtv.app.ui.accessibility.isReducedMotionEnabled
import com.kaynanamtv.data.remote.jellyfin.JellyfinImageAuthInterceptor
import com.kaynanamtv.data.preferences.PreferencesRepository
import com.kaynanamtv.domain.model.Result
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okio.Path.Companion.toOkioPath

import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.kaynanamtv.data.manager.recording.RecordingReconcileWorker
import com.kaynanamtv.data.sync.ProviderSyncWorker
import com.kaynanamtv.data.sync.XtreamIndexWorker
import com.kaynanamtv.player.timeshift.TimeshiftDiskManager
import javax.inject.Inject
import okhttp3.OkHttpClient

@HiltAndroidApp
class KaynanamTVApp : Application(), SingletonImageLoader.Factory {
    private val runtimeDiagnosticsManager by lazy { RuntimeDiagnosticsManager(this) }
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @Inject
    lateinit var preferencesRepository: PreferencesRepository

    @Inject
    lateinit var gitHubReleaseChecker: GitHubReleaseChecker

    @Inject
    lateinit var okHttpClient: OkHttpClient

    @Inject
    lateinit var jellyfinImageAuthInterceptor: JellyfinImageAuthInterceptor

    private val imageOkHttpClient: OkHttpClient by lazy {
        okHttpClient.newBuilder()
            .addInterceptor(jellyfinImageAuthInterceptor)
            .build()
    }

    companion object {
        @Volatile
        @JvmStatic
        lateinit var instance: KaynanamTVApp
            private set
    }

    override fun attachBaseContext(base: android.content.Context) {
        super.attachBaseContext(base)
        androidx.multidex.MultiDex.install(this)
    }

    override fun onCreate() {
        instance = this
        super.onCreate()
        CrashReportStore.install(this)
        runtimeDiagnosticsManager.start()
        applicationScope.launch {
            // Clean up any timeshift temp directories left behind by crashes, OOM kills, or
            // force-stops from the previous run. activeSessionDir = null means wipe everything.
            TimeshiftDiskManager(applicationContext).cleanupStaleDirectories(activeSessionDir = null)
        }
        applicationScope.launch {
            refreshCachedAppUpdateIfNeeded(force = true)
        }
        
        // Schedule daily data maintenance: EPG pruning, stale-favorite cleanup, and DB compaction checks.
        // BLD-H02: Require network + device idle so the worker doesn't drain battery.
        val gcConstraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .setRequiresBatteryNotLow(true)
            .setRequiresDeviceIdle(true)
            .build()

        val gcWorkRequest = PeriodicWorkRequestBuilder<com.kaynanamtv.data.sync.SyncWorker>(24, java.util.concurrent.TimeUnit.HOURS)
            .setConstraints(gcConstraints)
            .build()
            
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            "DataMaintenanceWorker",
            ExistingPeriodicWorkPolicy.KEEP,
            gcWorkRequest
        )

        runCatching {
            val wm = WorkManager.getInstance(this)
            wm.cancelUniqueWork("provider-sync-launch-stale-check")
            wm.cancelUniqueWork("xtream-index-worker")
            wm.cancelUniqueWork("xtream-index-launch-stale-check")
        }
        
        applicationScope.launch {
            kotlinx.coroutines.flow.combine(
                preferencesRepository.backgroundSyncEnabled,
                preferencesRepository.backgroundSyncIntervalHours,
                preferencesRepository.backgroundSyncWifiOnly
            ) { enabled, interval, wifiOnly ->
                Triple(enabled, interval, wifiOnly)
            }.collect { (enabled, interval, wifiOnly) ->
                com.kaynanamtv.data.sync.BackgroundSyncScheduler.updateSchedule(
                    this@KaynanamTVApp,
                    enabled = enabled,
                    intervalHours = interval,
                    wifiOnly = wifiOnly
                )
            }
        }
    }

    override fun onTerminate() {
        runtimeDiagnosticsManager.stop()
        super.onTerminate()
    }

    private suspend fun refreshCachedAppUpdateIfNeeded(force: Boolean = false) {
        val autoCheckEnabled = preferencesRepository.autoCheckAppUpdates.first()
        if (!autoCheckEnabled) {
            return
        }

        val lastCheckedAt = preferencesRepository.lastAppUpdateCheckTimestamp.first()
        val now = System.currentTimeMillis()
        val checkIntervalMs = 5L * 60L * 1000L // 5 minutes safety cooldown
        if (!force && lastCheckedAt != null && now - lastCheckedAt < checkIntervalMs) {
            return
        }

        preferencesRepository.setLastAppUpdateCheckTimestamp(now)
        when (val result = gitHubReleaseChecker.fetchLatestRelease()) {
            is Result.Success -> {
                val isNewer = isRemoteVersionNewer(
                    remoteVersionCode = result.data.versionCode,
                    remoteVersionName = result.data.versionName,
                    remotePublishedAt = result.data.publishedAt
                )
                if (isNewer) {
                    preferencesRepository.setCachedAppUpdateRelease(
                        versionName = result.data.versionName,
                        versionCode = result.data.versionCode,
                        releaseUrl = result.data.releaseUrl,
                        downloadUrl = result.data.downloadUrl,
                        downloadSha256 = result.data.downloadSha256,
                        releaseNotes = result.data.releaseNotes,
                        publishedAt = result.data.publishedAt
                    )
                } else {
                    preferencesRepository.setCachedAppUpdateRelease(
                        versionName = null,
                        versionCode = null,
                        releaseUrl = null,
                        downloadUrl = null,
                        downloadSha256 = null,
                        releaseNotes = "",
                        publishedAt = null
                    )
                }
            }
            else -> Unit
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        return ImageLoader.Builder(context)
            .components {
                add(com.kaynanamtv.app.manager.PermanentCacheInterceptor(context))
                add(
                    OkHttpNetworkFetcherFactory(
                        callFactory = { imageOkHttpClient }
                    )
                )
            }
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, 0.25) // Conservative TV memory cache increased to 25%
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(this.cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(1024L * 1024L * 100L) // 100MB disk cache cap
                    .build()
            }
            // Limit concurrent decoding and fetching to 6 for TV hardware constraints
            .fetcherCoroutineContext(Dispatchers.IO.limitedParallelism(6))
            .decoderCoroutineContext(Dispatchers.Default.limitedParallelism(4))
            .crossfade(!isReducedMotionEnabled(context))
            .build()
    }
}
