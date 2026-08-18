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
                _remoteConfig
            ) { cachedBlocked, cachedMinVersion, testOverrideMinVersion, remoteCfg ->
                runCatching {
                    val currentVersionCode = getCurrentVersionCode()

                    val effectiveConfig = if (testOverrideMinVersion != null) {
                        (remoteCfg ?: AppRemoteConfig()).copy(
                            minimumSupportedVersionCode = testOverrideMinVersion,
                            forceUpdate = true
                        )
                    } else {
                        remoteCfg
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
            val firestore = runCatching { FirebaseFirestore.getInstance() }.getOrNull()
            if (firestore == null) {
                Log.w(TAG, "FirebaseFirestore instance unavailable, using cached state")
                val fallbackDecision = ForceUpdateEngine.evaluate(currentVersionCode, _remoteConfig.value, cachedBlocked)
                _forceUpdateDecision.value = fallbackDecision
                val cached = getCachedRemoteConfig()
                return if (cached != null) Result.Success(cached) else Result.Error("Firebase unavailable")
            }

            val docSnapshot = withTimeoutOrNull(FETCH_TIMEOUT_MS) {
                firestore.collection(CONFIG_COLLECTION).document(APP_CONFIG_DOC).get().await()
            }

            if (docSnapshot == null || !docSnapshot.exists()) {
                Log.w(TAG, "Remote app_config doc does not exist or timed out. Falling back.")
                val fallbackDecision = ForceUpdateEngine.evaluate(currentVersionCode, _remoteConfig.value, cachedBlocked)
                _forceUpdateDecision.value = fallbackDecision
                val cached = getCachedRemoteConfig()
                return if (cached != null) Result.Success(cached) else Result.Error("Config not found or timed out")
            }

            val data = docSnapshot.data.orEmpty()
            val minVersion = (data["minimumSupportedVersionCode"] as? Number)?.toInt() ?: 67
            val latestCode = (data["latestVersionCode"] as? Number)?.toInt() ?: 67
            val latestName = data["latestVersionName"] as? String ?: "1.0.67"
            val forceUpdate = data["forceUpdate"] as? Boolean ?: true
            val apkUrl = data["apkDownloadUrl"] as? String ?: "https://github.com/emreklc99/KaynanamTV/releases/latest"
            val releaseNotes = data["releaseNotes"] as? String ?: ""
            val updatedAt = (data["updatedAt"] as? Number)?.toLong() ?: 0L

            val config = AppRemoteConfig(
                minimumSupportedVersionCode = minVersion,
                latestVersionCode = latestCode,
                latestVersionName = latestName,
                forceUpdate = forceUpdate,
                apkDownloadUrl = apkUrl,
                releaseNotes = releaseNotes,
                updatedAt = updatedAt
            )

            _remoteConfig.value = config

            val testOverride = preferencesRepository.testOverrideMinVersionCode.first()
            val evaluatedConfig = if (testOverride != null) {
                config.copy(minimumSupportedVersionCode = testOverride, forceUpdate = true)
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
                if (cachedBlocked) {
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
