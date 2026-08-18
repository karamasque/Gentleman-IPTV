package com.kaynanamtv.app.update

import com.kaynanamtv.app.BuildConfig
import java.time.Instant
import kotlin.math.max

enum class AppUpdateActionState {
    None,
    DownloadLatest,
    Downloading,
    InstallLatest,
    InstallPermissionRequired
}

fun isRemoteVersionNewer(
    remoteVersionCode: Int?,
    remoteVersionName: String,
    remotePublishedAt: String? = null
): Boolean {
    return isRemoteVersionNewerForBuild(
        remoteVersionCode = remoteVersionCode,
        remoteVersionName = remoteVersionName,
        remotePublishedAt = remotePublishedAt,
        currentVersionCode = BuildConfig.VERSION_CODE,
        currentVersionName = BuildConfig.VERSION_NAME,
        currentBuildTimestampUtc = BuildConfig.BUILD_TIMESTAMP_UTC,
        currentChannel = AppUpdateChannel.fromCurrentBuild()
    )
}

fun isRemoteVersionNewerForBuild(
    remoteVersionCode: Int?,
    remoteVersionName: String,
    remotePublishedAt: String?,
    currentVersionCode: Int,
    currentVersionName: String,
    currentBuildTimestampUtc: Long,
    currentChannel: AppUpdateChannel
): Boolean {
    val remoteDescriptor = parseAppVersionDescriptor(remoteVersionName)
    val currentDescriptor = parseAppVersionDescriptor(currentVersionName)

    if (remoteDescriptor.channel != currentChannel) {
        return false
    }

    if (remoteVersionCode != null && currentVersionCode > 0 && remoteVersionCode < currentVersionCode) {
        return false
    }

    val versionComparison = compareVersionNamesStatic(
        remoteDescriptor.baseVersionName,
        currentDescriptor.baseVersionName
    )
    if (versionComparison != 0) {
        return versionComparison > 0
    }

    if (remoteVersionCode != null && currentVersionCode > 0 && remoteVersionCode > currentVersionCode) {
        return true
    }

    if (currentChannel == AppUpdateChannel.Beta) {
        val remotePublishedAtMillis = remotePublishedAt.toEpochMillisOrNull() ?: return false
        return remotePublishedAtMillis > currentBuildTimestampUtc
    }

    return false
}

fun compareVersionNamesStatic(left: String, right: String): Int {
    val leftParts = left.removePrefix("v").split('.')
    val rightParts = right.removePrefix("v").split('.')
    val length = max(leftParts.size, rightParts.size)
    for (index in 0 until length) {
        val leftClean = leftParts.getOrNull(index)?.takeWhile { it.isDigit() }.orEmpty()
        val rightClean = rightParts.getOrNull(index)?.takeWhile { it.isDigit() }.orEmpty()
        val leftValue = leftClean.toIntOrNull() ?: 0
        val rightValue = rightClean.toIntOrNull() ?: 0
        if (leftValue != rightValue) {
            return leftValue.compareTo(rightValue)
        }
    }
    return 0
}

fun isLatestAppUpdateDownloaded(
    latestVersionName: String?,
    downloadState: AppUpdateDownloadState
): Boolean {
    return !latestVersionName.isNullOrBlank() &&
        downloadState.status == AppUpdateDownloadStatus.Downloaded &&
        downloadState.versionName == latestVersionName
}

fun latestAppUpdateAction(
    latestVersionName: String?,
    downloadUrl: String?,
    isUpdateAvailable: Boolean,
    downloadState: AppUpdateDownloadState
): AppUpdateActionState {
    if (latestVersionName.isNullOrBlank() || !isUpdateAvailable) {
        return AppUpdateActionState.None
    }

    val latestDownloaded = isLatestAppUpdateDownloaded(latestVersionName, downloadState)
    if (latestDownloaded) {
        return if (downloadState.installPermissionRequired) {
            AppUpdateActionState.InstallPermissionRequired
        } else {
            AppUpdateActionState.InstallLatest
        }
    }

    if (downloadState.status == AppUpdateDownloadStatus.Downloading &&
        downloadState.versionName == latestVersionName
    ) {
        return AppUpdateActionState.Downloading
    }

    return if (!downloadUrl.isNullOrBlank()) {
        AppUpdateActionState.DownloadLatest
    } else {
        AppUpdateActionState.None
    }
}

private data class ParsedAppVersionDescriptor(
    val baseVersionName: String,
    val channel: AppUpdateChannel
)

private fun parseAppVersionDescriptor(versionName: String): ParsedAppVersionDescriptor {
    val normalized = versionName.removePrefix("v").trim()
    val baseName = normalized.split('-').firstOrNull()?.trim() ?: normalized
    val betaIndex = normalized.indexOf("-beta", ignoreCase = true)
    return ParsedAppVersionDescriptor(
        baseVersionName = baseName,
        channel = if (betaIndex >= 0) AppUpdateChannel.Beta else AppUpdateChannel.Stable
    )
}

private fun String?.toEpochMillisOrNull(): Long? {
    val value = this?.trim().orEmpty()
    if (value.isEmpty()) return null
    return runCatching { Instant.parse(value).toEpochMilli() }.getOrNull()
}
