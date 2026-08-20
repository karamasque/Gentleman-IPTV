package com.kaynanamtv.app.update

import android.app.DownloadManager
import android.content.ActivityNotFoundException
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.content.FileProvider
import com.kaynanamtv.app.BuildConfig
import com.kaynanamtv.data.preferences.PreferencesRepository
import com.kaynanamtv.domain.model.Result
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.net.URI
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

data class AppUpdateDownloadState(
    val status: AppUpdateDownloadStatus = AppUpdateDownloadStatus.Idle,
    val versionName: String? = null,
    val downloadId: Long? = null,
    val installPermissionRequired: Boolean = false,
    val bytesDownloaded: Long = 0L,
    val bytesTotal: Long = 0L
) {
    val progressPercentage: Int
        get() = if (bytesTotal > 0) ((bytesDownloaded * 100) / bytesTotal).toInt().coerceIn(0, 100) else 0
}

enum class AppUpdateDownloadStatus {
    Idle,
    Downloading,
    Downloaded,
    Failed
}

@Singleton
class AppUpdateInstaller @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: PreferencesRepository,
    private val okHttpClient: OkHttpClient
) {
    private val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as? DownloadManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _downloadState = MutableStateFlow(AppUpdateDownloadState())
    private var downloadPollingJob: Job? = null
    private val downloadCompleteReceiver = object : BroadcastReceiver() {
        override fun onReceive(receiverContext: Context?, intent: Intent?) {
            if (intent?.action != DownloadManager.ACTION_DOWNLOAD_COMPLETE) return
            val completedDownloadId = intent.getLongExtra(DownloadManager.EXTRA_DOWNLOAD_ID, -1L)
            val trackedDownloadId = _downloadState.value.downloadId ?: return
            if (completedDownloadId != trackedDownloadId) return
            scope.launch {
                refreshState()
            }
        }
    }

    val downloadState: StateFlow<AppUpdateDownloadState> = _downloadState.asStateFlow()

    init {
        registerDownloadReceiver()
        scope.launch {
            refreshState()
        }
    }

    suspend fun refreshState(): AppUpdateDownloadState = withContext(Dispatchers.IO) {
        val downloadId = preferencesRepository.appUpdateDownloadId.first()
        val downloadingVersionName = preferencesRepository.appUpdateDownloadVersionName.first()
        var downloadedVersionName = preferencesRepository.downloadedAppUpdateVersionName.first()

        if (downloadedVersionName != null && !isRemoteVersionNewer(null, downloadedVersionName)) {
            preferencesRepository.setDownloadedAppUpdateVersionName(null)
            downloadedVersionName?.let(::apkFileForVersion)?.delete()
            downloadedVersionName = null
        }
        val apkFile = downloadedVersionName?.let(::apkFileForVersion)

        val dm = downloadManager
        if (dm == null) {
            val restoredState = if (downloadedVersionName != null && apkFile?.exists() == true) {
                downloadedState(downloadedVersionName)
            } else {
                if (downloadedVersionName != null) {
                    preferencesRepository.setDownloadedAppUpdateVersionName(null)
                }
                AppUpdateDownloadState()
            }
            _downloadState.value = restoredState
            return@withContext restoredState
        }

        if (downloadId == null) {
            preferencesRepository.setAppUpdateDownloadVersionName(null)
            val restoredState = if (downloadedVersionName != null && apkFile?.exists() == true) {
                downloadedState(downloadedVersionName)
            } else {
                if (downloadedVersionName != null) {
                    preferencesRepository.setDownloadedAppUpdateVersionName(null)
                }
                AppUpdateDownloadState()
            }
            _downloadState.value = restoredState
            return@withContext restoredState
        }

        val trackedVersionName = downloadingVersionName ?: downloadedVersionName
        val query = DownloadManager.Query().setFilterById(downloadId)
        val cursor = runCatching { dm.query(query) }.getOrNull()
        if (cursor == null) {
            val fallbackState = AppUpdateDownloadState(status = AppUpdateDownloadStatus.Failed)
            _downloadState.value = fallbackState
            return@withContext fallbackState
        }
        cursor.use { c ->
            if (!c.moveToFirst()) {
                preferencesRepository.setAppUpdateDownloadId(null)
                preferencesRepository.setAppUpdateDownloadVersionName(null)
                val fallbackState = if (downloadedVersionName != null && apkFile?.exists() == true) {
                    downloadedState(downloadedVersionName)
                } else {
                    preferencesRepository.setDownloadedAppUpdateVersionName(null)
                    AppUpdateDownloadState(status = AppUpdateDownloadStatus.Failed)
                }
                _downloadState.value = fallbackState
                return@withContext fallbackState
            }

            val statusColumn = c.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS)
            val status = c.getInt(statusColumn)

            val downloadedBytesCol = c.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
            val totalBytesCol = c.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
            val downloadedBytes = if (downloadedBytesCol >= 0) c.getLong(downloadedBytesCol) else 0L
            val totalBytes = if (totalBytesCol >= 0) c.getLong(totalBytesCol) else 0L

            val state = when (status) {
                DownloadManager.STATUS_PENDING,
                DownloadManager.STATUS_PAUSED,
                DownloadManager.STATUS_RUNNING -> AppUpdateDownloadState(
                    status = AppUpdateDownloadStatus.Downloading,
                    versionName = trackedVersionName,
                    downloadId = downloadId,
                    bytesDownloaded = downloadedBytes,
                    bytesTotal = totalBytes
                )

                DownloadManager.STATUS_SUCCESSFUL -> {
                    preferencesRepository.setAppUpdateDownloadId(null)
                    preferencesRepository.setAppUpdateDownloadVersionName(null)
                    val completedApkFile = trackedVersionName?.let(::apkFileForVersion)
                    if (trackedVersionName != null && completedApkFile?.exists() == true) {
                        preferencesRepository.setDownloadedAppUpdateVersionName(trackedVersionName)
                        downloadedState(trackedVersionName)
                    } else {
                        preferencesRepository.setDownloadedAppUpdateVersionName(null)
                        AppUpdateDownloadState(
                            status = AppUpdateDownloadStatus.Failed,
                            versionName = trackedVersionName,
                            downloadId = null
                        )
                    }
                }

                else -> {
                    preferencesRepository.setAppUpdateDownloadId(null)
                    preferencesRepository.setAppUpdateDownloadVersionName(null)
                    if (trackedVersionName == downloadedVersionName) {
                        preferencesRepository.setDownloadedAppUpdateVersionName(null)
                    }
                    AppUpdateDownloadState(
                        status = AppUpdateDownloadStatus.Failed,
                        versionName = trackedVersionName,
                        downloadId = null
                    )
                }
            }
            _downloadState.value = state
            syncPollingForState(state)
            return@withContext state
        }
    }

    suspend fun startDownload(releaseInfo: GitHubReleaseInfo): Result<Unit> = withContext(Dispatchers.IO) {
        val downloadUrl = releaseInfo.downloadUrl
            ?: return@withContext Result.error("Update download is unavailable for this release")
        if (!isHttpsUrl(downloadUrl)) {
            return@withContext Result.error("Update download is unavailable because the download URL is not HTTPS")
        }

        val targetFile = apkFileForVersion(releaseInfo.versionName)
        targetFile.parentFile?.mkdirs()
        if (targetFile.exists()) {
            targetFile.delete()
        }

        val existingVersion = preferencesRepository.downloadedAppUpdateVersionName.first()
        if (existingVersion != null && existingVersion != releaseInfo.versionName) {
            apkFileForVersion(existingVersion).delete()
        }

        val dm = downloadManager
        if (dm == null) {
            return@withContext downloadViaOkHttp(releaseInfo, targetFile)
        }

        try {
            preferencesRepository.appUpdateDownloadId.first()?.let { oldDownloadId ->
                runCatching { dm.remove(oldDownloadId) }
            }

            val request = DownloadManager.Request(Uri.parse(downloadUrl))
                .setTitle("KaynanamTV ${releaseInfo.versionName}")
                .setDescription("KaynanamTV güncellemesi indiriliyor")
                .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
                .setMimeType("application/vnd.android.package-archive")
                .setAllowedOverMetered(true)
                .setAllowedOverRoaming(true)
                .setDestinationInExternalFilesDir(
                    context,
                    Environment.DIRECTORY_DOWNLOADS,
                    targetFile.name
                )

            val downloadId = dm.enqueue(request)
            preferencesRepository.setAppUpdateDownloadId(downloadId)
            preferencesRepository.setAppUpdateDownloadVersionName(releaseInfo.versionName)
            preferencesRepository.setDownloadedAppUpdateVersionName(null)
            val state = AppUpdateDownloadState(
                status = AppUpdateDownloadStatus.Downloading,
                versionName = releaseInfo.versionName,
                downloadId = downloadId
            )
            _downloadState.value = state
            syncPollingForState(state)
            Result.success(Unit)
        } catch (error: Exception) {
            android.util.Log.w("AppUpdateInstaller", "DownloadManager failed (${error.message}), falling back to OkHttp", error)
            downloadViaOkHttp(releaseInfo, targetFile)
        }
    }

    private suspend fun downloadViaOkHttp(releaseInfo: GitHubReleaseInfo, targetFile: File): Result<Unit> = withContext(Dispatchers.IO) {
        val downloadUrl = releaseInfo.downloadUrl ?: return@withContext Result.error("Download URL is missing")
        try {
            val request = Request.Builder()
                .url(downloadUrl)
                .header("User-Agent", "KaynanamTV-AppUpdate")
                .build()

            _downloadState.value = AppUpdateDownloadState(
                status = AppUpdateDownloadStatus.Downloading,
                versionName = releaseInfo.versionName,
                downloadId = null,
                bytesDownloaded = 0L,
                bytesTotal = 0L
            )

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    _downloadState.value = AppUpdateDownloadState(status = AppUpdateDownloadStatus.Failed)
                    return@withContext Result.error("İndirme başarısız oldu: HTTP ${response.code}")
                }
                val body = response.body ?: return@withContext Result.error("Boş yanıt alındı")
                val totalBytes = body.contentLength()
                var downloaded = 0L
                targetFile.parentFile?.mkdirs()
                if (targetFile.exists()) targetFile.delete()

                body.byteStream().use { input ->
                    targetFile.outputStream().use { output ->
                        val buffer = ByteArray(32 * 1024)
                        var read: Int
                        var lastEmittedPercent = -1
                        while (input.read(buffer).also { read = it } != -1) {
                            output.write(buffer, 0, read)
                            downloaded += read
                            val percent = if (totalBytes > 0) ((downloaded * 100) / totalBytes).toInt() else 0
                            if (percent != lastEmittedPercent) {
                                lastEmittedPercent = percent
                                _downloadState.value = AppUpdateDownloadState(
                                    status = AppUpdateDownloadStatus.Downloading,
                                    versionName = releaseInfo.versionName,
                                    bytesDownloaded = downloaded,
                                    bytesTotal = totalBytes
                                )
                            }
                        }
                    }
                }

                val pkgInfo = context.packageManager.getPackageArchiveInfo(targetFile.absolutePath, 0)
                if (pkgInfo == null) {
                    targetFile.delete()
                    _downloadState.value = AppUpdateDownloadState(status = AppUpdateDownloadStatus.Failed)
                    return@withContext Result.error("İndirilen dosya geçerli bir APK paketi değil")
                }

                preferencesRepository.setDownloadedAppUpdateVersionName(releaseInfo.versionName)
                preferencesRepository.setAppUpdateDownloadId(null)
                preferencesRepository.setAppUpdateDownloadVersionName(null)

                val state = AppUpdateDownloadState(
                    status = AppUpdateDownloadStatus.Downloaded,
                    versionName = releaseInfo.versionName,
                    bytesDownloaded = downloaded,
                    bytesTotal = totalBytes
                )
                _downloadState.value = state
                Result.success(Unit)
            }
        } catch (e: Exception) {
            _downloadState.value = AppUpdateDownloadState(status = AppUpdateDownloadStatus.Failed)
            Result.error("İndirme sırasında hata oluştu: ${e.message}", e)
        }
    }

    suspend fun installDownloadedUpdate(expectedSha256: String? = null): Result<Unit> = withContext(Dispatchers.IO) {
        val currentState = refreshState()
        if (currentState.status != AppUpdateDownloadStatus.Downloaded || currentState.versionName.isNullOrBlank()) {
            return@withContext Result.error("No downloaded update is ready to install")
        }

        if (requiresInstallPermission()) {
            val settingsIntent = Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = Uri.parse("package:${context.packageName}")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val permissionRequiredState = currentState.copy(installPermissionRequired = true)
            _downloadState.value = permissionRequiredState
            return@withContext try {
                context.startActivity(settingsIntent)
                Result.error("Bilinmeyen kaynaklardan kuruluma izin verin ve ardından Güncellemeyi Kur'a tekrar dokunun")
            } catch (error: ActivityNotFoundException) {
                Result.error("Ayarlardan bu uygulama için yükleme iznini açın ve tekrar deneyin", error)
            } catch (error: SecurityException) {
                Result.error("Yükleme izni ekranı açılamadı", error)
            }
        }

        val apkFile = apkFileForVersion(currentState.versionName)
        if (!apkFile.exists()) {
            preferencesRepository.setAppUpdateDownloadId(null)
            preferencesRepository.setAppUpdateDownloadVersionName(null)
            preferencesRepository.setDownloadedAppUpdateVersionName(null)
            return@withContext Result.error("Downloaded update file is missing")
        }

        val pkgInfo = context.packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)
        if (pkgInfo == null) {
            apkFile.delete()
            preferencesRepository.setAppUpdateDownloadId(null)
            preferencesRepository.setAppUpdateDownloadVersionName(null)
            preferencesRepository.setDownloadedAppUpdateVersionName(null)
            return@withContext Result.error("İndirilen APK dosyası geçersiz veya bozuk. Lütfen tekrar indirin.")
        }

        // SEC-L02: Verify SHA-256 integrity before handing the APK to the package manager.
        // This guards against a truncated download, a network MITM, or a tampered file in
        // the external storage directory (which is world-readable on unencrypted devices).
        if (!expectedSha256.isNullOrBlank()) {
            val actualHash = computeSha256Hex(apkFile)
            if (!actualHash.equals(expectedSha256.trim(), ignoreCase = true)) {
                android.util.Log.e(
                    "AppUpdateInstaller",
                    "APK SHA-256 mismatch for ${apkFile.name}: expected=${expectedSha256.trim()} actual=$actualHash"
                )
                apkFile.delete()
                preferencesRepository.setAppUpdateDownloadId(null)
                preferencesRepository.setAppUpdateDownloadVersionName(null)
                preferencesRepository.setDownloadedAppUpdateVersionName(null)
                return@withContext Result.error(
                    "Downloaded update failed integrity check. The file has been removed; please download again."
                )
            }
            android.util.Log.i("AppUpdateInstaller", "APK SHA-256 verified OK for ${apkFile.name}")
        }

        val apkUri = FileProvider.getUriForFile(
            context,
            "${BuildConfig.APPLICATION_ID}.fileprovider",
            apkFile
        )

        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(apkUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_GRANT_PREFIX_URI_PERMISSION)
        }

        val resInfoList = context.packageManager.queryIntentActivities(
            installIntent,
            android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
        )
        for (resolveInfo in resInfoList) {
            val packageName = resolveInfo.activityInfo.packageName
            context.grantUriPermission(
                packageName,
                apkUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

        try {
            context.startActivity(installIntent)
            Result.success(Unit)
        } catch (error: ActivityNotFoundException) {
            @Suppress("DEPRECATION")
            val fallbackIntent = Intent(Intent.ACTION_INSTALL_PACKAGE).apply {
                setDataAndType(apkUri, "application/vnd.android.package-archive")
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            try {
                context.startActivity(fallbackIntent)
                Result.success(Unit)
            } catch (fallbackError: Exception) {
                Result.error("No package installer is available on this device", fallbackError)
            }
        } catch (error: SecurityException) {
            Result.error("The package installer could not be launched", error)
        }
    }

    private fun isHttpsUrl(url: String): Boolean {
        val normalized = url.trim()
        if (normalized.isBlank()) return false
        return runCatching {
            val parsed = URI(normalized)
            parsed.scheme.equals("https", ignoreCase = true) && !parsed.host.isNullOrBlank()
        }.getOrDefault(false)
    }

    private fun computeSha256Hex(file: File): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        file.inputStream().buffered(DEFAULT_BUFFER_SIZE).use { stream ->
            val buffer = ByteArray(8192)
            var read: Int
            while (stream.read(buffer).also { read = it } != -1) {
                digest.update(buffer, 0, read)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }

    private fun apkFileForVersion(versionName: String): File {
        val sanitizedVersion = versionName.replace(Regex("[^A-Za-z0-9._-]"), "_")
        val downloadsDir = context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
            ?: File(context.cacheDir, "downloads")
        return File(downloadsDir, "KaynanamTV-$sanitizedVersion.apk")
    }

    private fun downloadedState(versionName: String): AppUpdateDownloadState {
        return AppUpdateDownloadState(
            status = AppUpdateDownloadStatus.Downloaded,
            versionName = versionName,
            downloadId = null,
            installPermissionRequired = requiresInstallPermission()
        )
    }

    private fun requiresInstallPermission(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            !context.packageManager.canRequestPackageInstalls()
    }

    private fun registerDownloadReceiver() {
        val filter = IntentFilter(DownloadManager.ACTION_DOWNLOAD_COMPLETE)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.registerReceiver(downloadCompleteReceiver, filter, Context.RECEIVER_NOT_EXPORTED)
        } else {
            @Suppress("DEPRECATION")
            context.registerReceiver(downloadCompleteReceiver, filter)
        }
    }

    /**
     * Unregisters the [DownloadManager] broadcast receiver.
     * Must be called when this singleton is torn down (e.g. in instrumentation tests that
     * destroy and recreate the DI graph) to prevent duplicate receiver registrations.
     */
    fun unregister() {
        runCatching { context.unregisterReceiver(downloadCompleteReceiver) }
    }

    private fun syncPollingForState(state: AppUpdateDownloadState) {
        if (state.status != AppUpdateDownloadStatus.Downloading || state.downloadId == null) {
            downloadPollingJob?.cancel()
            downloadPollingJob = null
            return
        }

        if (downloadPollingJob?.isActive == true) return

        downloadPollingJob = scope.launch {
            while (isActive) {
                delay(500)
                val refreshed = refreshState()
                if (refreshed.status != AppUpdateDownloadStatus.Downloading) {
                    break
                }
            }
        }
    }
}
