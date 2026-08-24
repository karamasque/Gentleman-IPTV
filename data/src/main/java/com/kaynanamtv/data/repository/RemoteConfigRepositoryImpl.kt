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

    private val _forceUpdateDecision = MutableStateFlow(ForceUpdateDecision.ALLOWED)
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
            67
        }
    }

    init {
        coroutineScope.launch {
            combine(
                preferencesRepository.cachedForceUpdateBlocked,
                preferencesRepository.cachedMinimumSupportedVersionCode,
                preferencesRepository.testOverrideMinVersionCode,
                preferencesRepository.cachedAppUpdateVersionCode,
                preferencesRepository.cachedAppUpdateVersionName,
                preferencesRepository.cachedAppUpdateDownloadUrl,
                preferencesRepository.cachedAppUpdateReleaseNotes,
                _remoteConfig
            ) { values ->
                val cachedBlocked = values[0] as Boolean
                val cachedMinVersion = values[1] as Int?
                val testOverrideMinVersion = values[2] as Int?
                val cachedGitCode = values[3] as Int?
                val cachedGitName = values[4] as String?
                val cachedGitUrl = values[5] as String?
                val cachedGitNotes = values[6] as String?
                val remoteCfg = values[7] as AppRemoteConfig?

                runCatching {
                    val currentVersionCode = getCurrentVersionCode()

                    val baseConfig = remoteCfg ?: AppRemoteConfig(
                        minimumSupportedVersionCode = cachedMinVersion ?: 67,
                        latestVersionCode = cachedGitCode ?: 67,
                        latestVersionName = cachedGitName ?: "1.0.67",
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
                        cachedGitCode != null && cachedGitCode > currentVersionCode -> {
                            baseConfig.copy(
                                minimumSupportedVersionCode = maxOf(baseConfig.minimumSupportedVersionCode, cachedGitCode),
                                latestVersionCode = maxOf(baseConfig.latestVersionCode, cachedGitCode),
                                latestVersionName = cachedGitName ?: baseConfig.latestVersionName,
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
                        remoteConfig = effectiveConfig,
                        cachedForceUpdateBlocked = cachedBlocked
                    )
                }.getOrDefault(ForceUpdateDecision.ALLOWED)
            }.collect { decision ->
                _forceUpdateDecision.value = decision
            }
        }
    }

    override suspend fun checkRemoteConfig(force: Boolean): Result<AppRemoteConfig> {
        val currentVersionCode = getCurrentVersionCode()
        var cachedBlocked = false

        try {
            cachedBlocked = runCatching { preferencesRepository.cachedForceUpdateBlocked.first() }.getOrDefault(false)
            val cachedGitCode = runCatching { preferencesRepository.cachedAppUpdateVersionCode.first() }.getOrNull()
            val cachedGitName = runCatching { preferencesRepository.cachedAppUpdateVersionName.first() }.getOrNull()
            val cachedGitUrl = runCatching { preferencesRepository.cachedAppUpdateDownloadUrl.first() }.getOrNull()
            val cachedGitNotes = runCatching { preferencesRepository.cachedAppUpdateReleaseNotes.first() }.getOrNull()

            val firestore = runCatching { FirebaseFirestore.getInstance() }.getOrNull()
            var firestoreMinVersion = 67
            var firestoreLatestCode = 67
            var firestoreLatestName = "1.0.67"
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
                    firestoreMinVersion = (data["minimumSupportedVersionCode"] as? Number)?.toInt() ?: 67
                    firestoreLatestCode = (data["latestVersionCode"] as? Number)?.toInt() ?: 67
                    firestoreLatestName = data["latestVersionName"] as? String ?: "1.0.67"
                    firestoreForceUpdate = data["forceUpdate"] as? Boolean ?: true
                    firestoreApkUrl = (data["apkDownloadUrl"] as? String)?.takeIf { it.isNotBlank() && it.endsWith(".apk", ignoreCase = true) }
                        ?: com.kaynanamtv.domain.model.AppUpdateConstants.DEFAULT_DOWNLOAD_URL
                    firestoreNotes = data["releaseNotes"] as? String ?: ""
                    updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0L
                }
            }

            // Always resolve effective versions prioritizing GitHub's newer release if available:
            val isGitNameNewer = !cachedGitName.isNullOrBlank() && (
                (cachedGitCode != null && cachedGitCode >= firestoreLatestCode) ||
                compareVersionNames(cachedGitName, firestoreLatestName) >= 0
            )
            val effectiveLatestCode = maxOf(firestoreLatestCode, cachedGitCode ?: 0, currentVersionCode)
            val effectiveMinVersion = maxOf(
                firestoreMinVersion,
                if (cachedGitCode != null && cachedGitCode > currentVersionCode) cachedGitCode else 0
            )
            val effectiveLatestName = if (isGitNameNewer && !cachedGitName.isNullOrBlank()) {
                cachedGitName
            } else {
                firestoreLatestName
            }
            val effectiveApkUrl = if (isGitNameNewer && !cachedGitUrl.isNullOrBlank()) {
                cachedGitUrl
            } else {
                firestoreApkUrl
            }
            val effectiveNotes = if (!cachedGitNotes.isNullOrBlank()) cachedGitNotes else firestoreNotes
            val effectiveForceUpdate = firestoreForceUpdate || (cachedGitCode != null && cachedGitCode > currentVersionCode) || cachedBlocked

            val config = AppRemoteConfig(
                minimumSupportedVersionCode = effectiveMinVersion,
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
                remoteConfig = evaluatedConfig,
                cachedForceUpdateBlocked = cachedBlocked
            )
            _forceUpdateDecision.value = decision

            if (decision == ForceUpdateDecision.BLOCKED_FORCE_UPDATE_REQUIRED) {
                preferencesRepository.setForceUpdateBlockedState(true, evaluatedConfig.minimumSupportedVersionCode)
            } else {
                if (cachedBlocked && currentVersionCode >= evaluatedConfig.minimumSupportedVersionCode && currentVersionCode >= evaluatedConfig.latestVersionCode) {
                    preferencesRepository.setForceUpdateBlockedState(false, null)
                }
            }

            preferencesRepository.setCachedRemoteConfigData(
                minVersionCode = config.minimumSupportedVersionCode,
                latestVersionCode = config.latestVersionCode,
                latestVersionName = config.latestVersionName,
                forceUpdate = config.forceUpdate,
                apkDownloadUrl = config.apkDownloadUrl,
                releaseNotes = config.releaseNotes
            )

            return Result.Success(config)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to fetch remote config from Firestore: ${e.message}", e)
            val fallbackDecision = ForceUpdateEngine.evaluate(currentVersionCode, _remoteConfig.value, cachedBlocked)
            _forceUpdateDecision.value = fallbackDecision
            val cached = getCachedRemoteConfig()
            return if (cached != null) Result.Success(cached) else Result.Error("Network error: ${e.message}")
        }
    }

    override suspend fun getCachedRemoteConfig(): AppRemoteConfig? {
        val minVersion = preferencesRepository.cachedMinimumSupportedVersionCode.first() ?: return null
        val latestCode = preferencesRepository.cachedAppUpdateVersionCode.first() ?: minVersion
        val latestName = preferencesRepository.cachedAppUpdateVersionName.first() ?: "1.0.67"
        val apkUrl = preferencesRepository.cachedAppUpdateDownloadUrl.first().orEmpty()
        val releaseNotes = preferencesRepository.cachedAppUpdateReleaseNotes.first()

        return AppRemoteConfig(
            minimumSupportedVersionCode = minVersion,
            latestVersionCode = latestCode,
            latestVersionName = latestName,
            forceUpdate = true,
            apkDownloadUrl = apkUrl,
            releaseNotes = releaseNotes,
            updatedAt = 0L
        )
    }
}

private fun compareVersionNames(left: String, right: String): Int {
    val leftParts = left.removePrefix("v").removePrefix("V").split('.')
    val rightParts = right.removePrefix("v").removePrefix("V").split('.')
    val length = maxOf(leftParts.size, rightParts.size)
    for (index in 0 until length) {
        val leftClean = leftParts.getOrNull(index)?.takeWhile { it.isDigit() }.orEmpty()
        val rightClean = rightParts.getOrNull(index)?.takeWhile { it.isDigit() }.orEmpty()
        val leftVal = leftClean.toIntOrNull() ?: 0
        val rightVal = rightClean.toIntOrNull() ?: 0
        if (leftVal != rightVal) return leftVal.compareTo(rightVal)
    }
    return 0
}
