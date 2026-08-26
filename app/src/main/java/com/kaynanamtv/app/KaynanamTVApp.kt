package com.kaynanamtv.app

import android.app.Application
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.kaynanamtv.app.device.TvLightweightProfile
import com.kaynanamtv.app.diagnostics.CrashReportStore
import com.kaynanamtv.app.diagnostics.RuntimeDiagnosticsManager
import com.kaynanamtv.app.ui.accessibility.isReducedMotionEnabled
import com.kaynanamtv.app.update.GitHubReleaseChecker
import com.kaynanamtv.app.update.isRemoteVersionNewer
import com.kaynanamtv.data.preferences.PreferencesRepository
import com.kaynanamtv.data.remote.jellyfin.JellyfinImageAuthInterceptor
import com.kaynanamtv.domain.model.Result
import com.kaynanamtv.player.timeshift.TimeshiftDiskManager
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okio.Path.Companion.toOkioPath
import javax.inject.Inject

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
    lateinit var entitlementManager: com.kaynanamtv.domain.manager.EntitlementManager

    @Inject
    lateinit var cloudUserStateSyncManager: com.kaynanamtv.data.sync.CloudUserStateSyncManager

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
        TvLightweightProfile.initialize(this)
        CrashReportStore.install(this)
        runtimeDiagnosticsManager.start()

        try {
            val savedTheme = preferencesRepository.getAppColorThemeSynchronously()
            com.kaynanamtv.app.ui.design.AppColors.currentPalette =
                com.kaynanamtv.app.ui.design.AppColorPalette.forTheme(savedTheme)
            android.util.Log.i("KaynanamTV_Theme", "[THEME_APPLY] applied=${savedTheme.name} in KaynanamTVApp.onCreate")
        } catch (e: Exception) {
            android.util.Log.e("KaynanamTV_Theme", "[THEME_APPLY_ERR] failed to apply theme on app start", e)
        }
        applicationScope.launch {
            TimeshiftDiskManager(applicationContext).cleanupStaleDirectories(activeSessionDir = null)
        }
        applicationScope.launch {
            refreshCachedAppUpdateIfNeeded(force = true)
        }

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
            // Asynchronously reconcile favorites and watch progress without blocking startup
            cloudUserStateSyncManager.reconcileFromCloud()

            // Lifecycle-safe periodic background sync scheduling (strictly gated by AUTOMATIC_REFRESH feature)
            kotlinx.coroutines.flow.combine(
                preferencesRepository.backgroundSyncEnabled,
                preferencesRepository.backgroundSyncIntervalHours,
                preferencesRepository.backgroundSyncWifiOnly,
                entitlementManager.observeFeature(com.kaynanamtv.domain.model.Feature.BACKGROUND_PLAYLIST_UPDATE)
            ) { enabled, interval, wifiOnly, isEntitled ->
                val shouldSchedule = enabled && isEntitled
                Triple(shouldSchedule, interval, wifiOnly)
            }.collect { (shouldSchedule, interval, wifiOnly) ->
                com.kaynanamtv.data.sync.BackgroundSyncScheduler.updateSchedule(
                    this@KaynanamTVApp,
                    enabled = shouldSchedule,
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

    suspend fun checkForAppUpdates(force: Boolean = false) {
        refreshCachedAppUpdateIfNeeded(force)
    }

    private suspend fun refreshCachedAppUpdateIfNeeded(force: Boolean = false) {
        val autoCheckEnabled = preferencesRepository.autoCheckAppUpdates.first()
        if (!autoCheckEnabled && !force) {
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
                preferencesRepository.setCachedAppUpdateRelease(
                    versionName = result.data.versionName,
                    versionCode = result.data.versionCode,
                    releaseUrl = result.data.releaseUrl,
                    downloadUrl = result.data.downloadUrl,
                    downloadSha256 = result.data.downloadSha256,
                    releaseNotes = result.data.releaseNotes,
                    publishedAt = result.data.publishedAt
                )
                val isNewer = isRemoteVersionNewer(
                    remoteVersionCode = result.data.versionCode,
                    remoteVersionName = result.data.versionName,
                    remotePublishedAt = result.data.publishedAt
                )
                if (isNewer) {
                    preferencesRepository.setForceUpdateBlockedState(true, result.data.versionCode)
                } else {
                    preferencesRepository.setForceUpdateBlockedState(false, null)
                }
            }
            else -> Unit
        }
    }

    override fun newImageLoader(context: PlatformContext): ImageLoader {
        val tvProfile = TvLightweightProfile
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
                    .maxSizePercent(context, tvProfile.maxCoilMemoryCachePercent)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(this.cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(if (tvProfile.isEnabled) 50L * 1024L * 1024L else 100L * 1024L * 1024L)
                    .build()
            }
            .fetcherCoroutineContext(Dispatchers.IO.limitedParallelism(tvProfile.coilFetcherParallelism))
            .decoderCoroutineContext(Dispatchers.Default.limitedParallelism(tvProfile.coilDecoderParallelism))
            .crossfade(!tvProfile.reduceAnimations && !isReducedMotionEnabled(context))
            .build()
    }
}
