package com.kaynanamtv.domain.model

data class AppRemoteConfig(
    val minimumSupportedVersionCode: Int = 67,
    val latestVersionCode: Int = 67,
    val latestVersionName: String = "1.0.67",
    val forceUpdate: Boolean = true,
    val apkDownloadUrl: String = "",
    val releaseNotes: String = "",
    val updatedAt: Long = 0L
)

enum class ForceUpdateDecision {
    ALLOWED,
    BLOCKED_FORCE_UPDATE_REQUIRED
}
