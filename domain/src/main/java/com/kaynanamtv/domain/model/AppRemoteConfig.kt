package com.kaynanamtv.domain.model

data class AppRemoteConfig(
    val minimumSupportedVersionCode: Int = 0,
    val minimumSupportedVersionName: String = "",
    val latestVersionCode: Int = 0,
    val latestVersionName: String = "",
    val forceUpdate: Boolean = true,
    val apkDownloadUrl: String = "",
    val releaseNotes: String = "",
    val updatedAt: Long = 0L
)

enum class ForceUpdateDecision {
    ALLOWED,
    BLOCKED_FORCE_UPDATE_REQUIRED
}
