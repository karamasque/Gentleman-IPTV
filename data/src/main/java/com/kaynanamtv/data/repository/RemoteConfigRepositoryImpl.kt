package com.kaynanamtv.data.repository

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.util.Log
import androidx.core.content.pm.PackageInfoCompat
import com.google.firebase.firestore.FirebaseFirestore
import com.kaynanamtv.data.preferences.PreferencesRepository
import com.kaynanamtv.domain.manager.ForceUpdateEngine
import com.kaynanamtv.domain.model.AppRemoteConfig
import com.kaynanamtv.domain.model.ForceUpdateDecision
import com.kaynanamtv.domain.model.Result
import com.kaynanamtv.domain.repository.RemoteConfigRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

private const val TAG = "RemoteConfigRepo"
private const val CONFIG_COLLECTION = "config"
private const val APP_CONFIG_DOC = "app_config"
private const val FETCH_TIMEOUT_MS = 6000L

@Singleton
class RemoteConfigRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val preferencesRepository: PreferencesRepository,
    private val coroutineScope: CoroutineScope
) : RemoteConfigRepository {

    private val _remoteConfig = MutableStateFlow<AppRemoteConfig?>(null)
    override val remoteConfigFlow: Flow<AppRemoteConfig?> = _remoteConfig.asStateFlow()

    private val _forceUpdateDecision = MutableStateFlow(
        if (preferencesRepository.getForceUpdateBlockedSynchronously()) {
            ForceUpdateDecision.BLOCKED_FORCE_UPDATE_REQUIRED
        } else {
            ForceUpdateDecision.ALLOWED
        }
    )
    override val forceUpdateDecisionFlow: Flow<ForceUpdateDecision> = _forceUpdateDecision.asStateFlow()

    private fun getCurrentVersionCode(): Int {
        return try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            PackageInfoCompat.getLongVersionCode(pInfo).toInt()
        } catch (e: Exception) {
            0
        }
    }

    private fun getCurrentVersionName(): String {
        return try {
            val pInfo = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(context.packageName, PackageManager.PackageInfoFlags.of(0))
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
            pInfo.versionName ?: ""
        } catch (e: Exception) {
            ""
        }
    }

    init {
        coroutineScope.launch {
            combine(
                preferencesRepository.cachedForceUpdateBlocked,
                preferencesRepository.cachedMinimumSupportedVersionCode,
                preferencesRepository.cachedMinimumSupportedVersionName,
                preferencesRepository.testOverrideMinVersionCode,
                preferencesRepository.cachedAppUpdateVersionCode,
                preferencesRepository.cachedAppUpdateVersionName,
                preferencesRepository.cachedAppUpdateDownloadUrl,
                preferencesRepository.cachedAppUpdateReleaseNotes,
                _remoteConfig
            ) { values ->
                val cachedBlocked = values[0] as Boolean
                val cachedMinVersionCode = values[1] as Int?
                val cachedMinVersionName = values[2] as String?
                val testOverrideMinVersion = values[3] as Int?
                val cachedGitCode = values[4] as Int?
                val cachedGitName = values[5] as String?
                val cachedGitUrl = values[6] as String?
                val cachedGitNotes = values[7] as String?
                val remoteCfg = values[8] as AppRemoteConfig?

                runCatching {
                    val currentVersionCode = getCurrentVersionCode()
                    val currentVersionName = getCurrentVersionName()

                    val baseConfig = remoteCfg ?: AppRemoteConfig(
                        minimumSupportedVersionCode = cachedMinVersionCode ?: 0,
                        minimumSupportedVersionName = cachedMinVersionName.orEmpty(),
                        latestVersionCode = cachedGitCode ?: 0,
                        latestVersionName = cachedGitName.orEmpty(),
                        forceUpdate = true,
                        apkDownloadUrl = cachedGitUrl.orEmpty(),
                        releaseNotes = cachedGitNotes.orEmpty()
                    )

                    val effectiveConfig = when {
                        testOverrideMinVersion != null -> {
                            baseConfig.copy(
                                minimumSupportedVersionCode = testOverrideMinVersion,
                                latestVersionCode = maxOf(baseConfig.latestVersionCode, testOverrideMinVersion),
                                forceUpdate = true
                            )
                        }
                        !cachedGitName.isNullOrBlank() && ForceUpdateEngine.compareVersionNames(cachedGitName, currentVersionName) > 0 -> {
                            baseConfig.copy(
                                minimumSupportedVersionCode = maxOf(baseConfig.minimumSupportedVersionCode, cachedGitCode ?: 0),
                                minimumSupportedVersionName = cachedGitName,
                                latestVersionCode = maxOf(baseConfig.latestVersionCode, cachedGitCode ?: 0),
                                latestVersionName = cachedGitName,
                                forceUpdate = true,
                                apkDownloadUrl = if (!cachedGitUrl.isNullOrBlank()) cachedGitUrl else baseConfig.apkDownloadUrl,
                                releaseNotes = if (!cachedGitNotes.isNullOrBlank()) cachedGitNotes else baseConfig.releaseNotes
                            )
                        }
                        else -> {
                            baseConfig
                        }
                    }

                    ForceUpdateEngine.evaluate(
                        currentVersionCode = currentVersionCode,
                        currentVersionName = currentVersionName,
                        remoteConfig = effectiveConfig,
                        cachedForceUpdateBlocked = cachedBlocked
                    )
                }.getOrDefault(if (cachedBlocked) ForceUpdateDecision.BLOCKED_FORCE_UPDATE_REQUIRED else ForceUpdateDecision.ALLOWED)
            }.collect { decision ->
                _forceUpdateDecision.value = decision
            }
        }
    }

    override suspend fun checkRemoteConfig(force: Boolean): Result<AppRemoteConfig> {
        val currentVersionCode = getCurrentVersionCode()
        val currentVersionName = getCurrentVersionName()
        var cachedBlocked = false

        try {
            cachedBlocked = runCatching { preferencesRepository.cachedForceUpdateBlocked.first() }.getOrDefault(false)
            val cachedGitCode = runCatching { preferencesRepository.cachedAppUpdateVersionCode.first() }.getOrNull()
            val cachedGitName = runCatching { preferencesRepository.cachedAppUpdateVersionName.first() }.getOrNull()
            val cachedGitUrl = runCatching { preferencesRepository.cachedAppUpdateDownloadUrl.first() }.getOrNull()
            val cachedGitNotes = runCatching { preferencesRepository.cachedAppUpdateReleaseNotes.first() }.getOrNull()

            val firestore = runCatching { FirebaseFirestore.getInstance() }.getOrNull()
            var firestoreMinVersionCode = 0
            var firestoreMinVersionName = ""
            var firestoreLatestCode = 0
            var firestoreLatestName = ""
            var firestoreForceUpdate = true
            var firestoreApkUrl = com.kaynanamtv.domain.model.AppUpdateConstants.DEFAULT_DOWNLOAD_URL
            var firestoreNotes = ""
            var updatedAt = 0L

            if (firestore != null) {
                val docSnapshot = withTimeoutOrNull(FETCH_TIMEOUT_MS) {
                    firestore.collection(CONFIG_COLLECTION).document(APP_CONFIG_DOC).get().await()
                }
                if (docSnapshot != null && docSnapshot.exists()) {
                    val data = docSnapshot.data.orEmpty()
                    firestoreMinVersionCode = (data["minimumSupportedVersionCode"] as? Number)?.toInt() ?: 0
                    firestoreMinVersionName = data["minimumSupportedVersionName"] as? String ?: ""
                    firestoreLatestCode = (data["latestVersionCode"] as? Number)?.toInt() ?: 0
                    firestoreLatestName = data["latestVersionName"] as? String ?: ""
                    firestoreForceUpdate = data["forceUpdate"] as? Boolean ?: true
                    firestoreApkUrl = (data["apkDownloadUrl"] as? String)?.takeIf { it.isNotBlank() && it.endsWith(".apk", ignoreCase = true) }
                        ?: com.kaynanamtv.domain.model.AppUpdateConstants.DEFAULT_DOWNLOAD_URL
                    firestoreNotes = data["releaseNotes"] as? String ?: ""
                    updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0L
                }
            }

            // Always resolve effective versions prioritizing GitHub's newer release if available:
            val isGitNewer = !cachedGitName.isNullOrBlank() && (
                ForceUpdateEngine.compareVersionNames(cachedGitName, currentVersionName) > 0 ||
                ForceUpdateEngine.compareVersionNames(cachedGitName, firestoreLatestName) >= 0
            )

            val effectiveLatestCode = maxOf(firestoreLatestCode, cachedGitCode ?: 0, currentVersionCode)
            val effectiveMinVersionCode = maxOf(
                firestoreMinVersionCode,
                if (cachedGitCode != null && cachedGitCode > currentVersionCode) cachedGitCode else 0
            )
            val effectiveMinVersionName = if (isGitNewer && !cachedGitName.isNullOrBlank()) {
                cachedGitName
            } else {
                firestoreMinVersionName
            }
            val effectiveLatestName = if (isGitNewer && !cachedGitName.isNullOrBlank()) {
                cachedGitName
            } else if (firestoreLatestName.isNotBlank()) {
                firestoreLatestName
            } else {
                currentVersionName
            }
            val effectiveApkUrl = if (isGitNewer && !cachedGitUrl.isNullOrBlank()) {
                cachedGitUrl
            } else {
                firestoreApkUrl
            }
            val effectiveNotes = if (!cachedGitNotes.isNullOrBlank()) cachedGitNotes else firestoreNotes
            val effectiveForceUpdate = firestoreForceUpdate || isGitNewer || cachedBlocked

            val config = AppRemoteConfig(
                minimumSupportedVersionCode = effectiveMinVersionCode,
                minimumSupportedVersionName = effectiveMinVersionName,
                latestVersionCode = effectiveLatestCode,
                latestVersionName = effectiveLatestName,
                forceUpdate = effectiveForceUpdate,
                apkDownloadUrl = effectiveApkUrl,
                releaseNotes = effectiveNotes,
                updatedAt = updatedAt
            )

            _remoteConfig.value = config

            val testOverride = preferencesRepository.testOverrideMinVersionCode.first()
            val evaluatedConfig = if (testOverride != null) {
                config.copy(
                    minimumSupportedVersionCode = testOverride,
                    latestVersionCode = maxOf(config.latestVersionCode, testOverride),
                    forceUpdate = true
                )
            } else {
                config
            }

            val decision = ForceUpdateEngine.evaluate(
                currentVersionCode = currentVersionCode,
                currentVersionName = currentVersionName,
                remoteConfig = evaluatedConfig,
                cachedForceUpdateBlocked = cachedBlocked
            )
            _forceUpdateDecision.value = decision

            if (decision == ForceUpdateDecision.BLOCKED_FORCE_UPDATE_REQUIRED) {
                preferencesRepository.setForceUpdateBlockedState(
                    blocked = true,
                    minVersionCode = evaluatedConfig.minimumSupportedVersionCode,
                    minVersionName = evaluatedConfig.minimumSupportedVersionName,
                    latestVersionName = evaluatedConfig.latestVersionName,
                    latestVersionCode = evaluatedConfig.latestVersionCode,
                    downloadUrl = evaluatedConfig.apkDownloadUrl,
                    releaseNotes = evaluatedConfig.releaseNotes
                )
            } else {
                if (cachedBlocked && ForceUpdateEngine.isAppVersionSufficient(currentVersionCode, currentVersionName, evaluatedConfig)) {
                    preferencesRepository.setForceUpdateBlockedState(false, null)
                }
            }

            preferencesRepository.setCachedRemoteConfigData(
                minVersionCode = config.minimumSupportedVersionCode,
                minVersionName = config.minimumSupportedVersionName,
                latestVersionCode = config.latestVersionCode,
                latestVersionName = config.latestVersionName,
                forceUpdate = config.forceUpdate,
                apkDownloadUrl = config.apkDownloadUrl,
                releaseNotes = config.releaseNotes
            )

            return Result.Success(config)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch remote config: ${e.message}", e)
            val fallbackDecision = ForceUpdateEngine.evaluate(
                currentVersionCode = currentVersionCode,
                currentVersionName = currentVersionName,
                remoteConfig = _remoteConfig.value,
                cachedForceUpdateBlocked = cachedBlocked
            )
            _forceUpdateDecision.value = fallbackDecision
            val cached = getCachedRemoteConfig()
            return if (cached != null) Result.Success(cached) else Result.Error("Network error: ${e.message}")
        }
    }

    override suspend fun getCachedRemoteConfig(): AppRemoteConfig? {
        val minVersionCode = preferencesRepository.cachedMinimumSupportedVersionCode.first() ?: 0
        val minVersionName = preferencesRepository.cachedMinimumSupportedVersionName.first().orEmpty()
        val latestCode = preferencesRepository.cachedAppUpdateVersionCode.first() ?: minVersionCode
        val latestName = preferencesRepository.cachedAppUpdateVersionName.first().orEmpty()
        val apkUrl = preferencesRepository.cachedAppUpdateDownloadUrl.first().orEmpty()
        val releaseNotes = preferencesRepository.cachedAppUpdateReleaseNotes.first().orEmpty()

        if (minVersionCode <= 0 && minVersionName.isBlank() && latestCode <= 0 && latestName.isBlank()) {
            return null
        }

        return AppRemoteConfig(
            minimumSupportedVersionCode = minVersionCode,
            minimumSupportedVersionName = minVersionName,
            latestVersionCode = latestCode,
            latestVersionName = latestName,
            forceUpdate = true,
            apkDownloadUrl = apkUrl,
            releaseNotes = releaseNotes,
            updatedAt = 0L
        )
    }
}
