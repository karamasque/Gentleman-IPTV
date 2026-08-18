package com.kaynanamtv.app.ui.screens.update

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kaynanamtv.app.update.AppUpdateInstaller
import com.kaynanamtv.app.update.GitHubReleaseInfo
import com.kaynanamtv.data.preferences.PreferencesRepository
import com.kaynanamtv.domain.model.AppRemoteConfig
import com.kaynanamtv.domain.model.Result
import com.kaynanamtv.domain.repository.RemoteConfigRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ForceUpdateViewModel @Inject constructor(
    private val remoteConfigRepository: RemoteConfigRepository,
    private val appUpdateInstaller: AppUpdateInstaller,
    private val preferencesRepository: PreferencesRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    val remoteConfig: StateFlow<AppRemoteConfig?> = remoteConfigRepository.remoteConfigFlow
        .stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val downloadState = appUpdateInstaller.downloadState

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    fun downloadAndInstall() {
        viewModelScope.launch {
            val config = remoteConfig.value ?: run {
                openBrowserDownloadFallback()
                return@launch
            }

            val apkUrl = config.apkDownloadUrl.ifBlank {
                "https://github.com/emreklc99/KaynanamTV/releases/latest"
            }

            val releaseInfo = GitHubReleaseInfo(
                versionName = config.latestVersionName,
                versionCode = config.latestVersionCode,
                releaseUrl = apkUrl,
                downloadUrl = apkUrl,
                downloadSha256 = null,
                releaseNotes = config.releaseNotes,
                publishedAt = null
            )

            _userMessage.value = "Güncelleme indiriliyor..."
            when (val res = appUpdateInstaller.startDownload(releaseInfo)) {
                is Result.Error -> {
                    _userMessage.value = "İndirme başlatılamadı, tarayıcıya yönlendiriliyor..."
                    openBrowserDownloadFallback()
                }
                else -> Unit
            }
        }
    }

    fun installDownloadedUpdate() {
        viewModelScope.launch {
            when (val res = appUpdateInstaller.installDownloadedUpdate(null)) {
                is Result.Error -> {
                    _userMessage.value = res.message
                }
                else -> Unit
            }
        }
    }

    fun openBrowserDownloadFallback() {
        val config = remoteConfig.value
        val url = config?.apkDownloadUrl?.takeIf { it.isNotBlank() }
            ?: "https://github.com/emreklc99/KaynanamTV/releases/latest"
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            _userMessage.value = "Tarayıcı açılamadı: ${e.message}"
        }
    }

    fun clearMessage() {
        _userMessage.value = null
    }
}
