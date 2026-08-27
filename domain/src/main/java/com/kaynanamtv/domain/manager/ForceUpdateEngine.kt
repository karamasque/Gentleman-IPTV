package com.kaynanamtv.domain.manager

import com.kaynanamtv.domain.model.AppRemoteConfig
import com.kaynanamtv.domain.model.ForceUpdateDecision

object ForceUpdateEngine {

    /**
     * Evaluates whether the application should be completely blocked by a mandatory force-update.
     *
     * @param currentVersionCode The running app's BuildConfig.VERSION_CODE
     * @param currentVersionName The running app's BuildConfig.VERSION_NAME
     * @param remoteConfig The central configuration loaded from server, GitHub, or cache
     * @param cachedForceUpdateBlocked Whether this device was previously flagged as obsolete
     */
    fun evaluate(
        currentVersionCode: Int,
        remoteConfig: AppRemoteConfig?,
        cachedForceUpdateBlocked: Boolean = false,
        currentVersionName: String = ""
    ): ForceUpdateDecision {
        // Fail-closed protection:
        // If the device already knows it is obsolete/blocked, keep it blocked offline
        // UNLESS the app has actually been updated to or above the required version.
        if (cachedForceUpdateBlocked) {
            if (remoteConfig != null && isAppVersionSufficient(currentVersionCode, currentVersionName, remoteConfig)) {
                return ForceUpdateDecision.ALLOWED
            }
            return ForceUpdateDecision.BLOCKED_FORCE_UPDATE_REQUIRED
        }

        // Temporary network downtime grace:
        // If remote config is unreachable and no previous block was recorded, allow access.
        if (remoteConfig == null) {
            return ForceUpdateDecision.ALLOWED
        }

        // Mandatory update check: block if current version is less than minimum required or latest released version
        if (remoteConfig.forceUpdate) {
            if (!isAppVersionSufficient(currentVersionCode, currentVersionName, remoteConfig)) {
                return ForceUpdateDecision.BLOCKED_FORCE_UPDATE_REQUIRED
            }
        }

        return ForceUpdateDecision.ALLOWED
    }

    /**
     * Returns true if the currently running app version satisfies all minimum and latest requirements.
     */
    fun isAppVersionSufficient(
        currentVersionCode: Int,
        currentVersionName: String,
        remoteConfig: AppRemoteConfig
    ): Boolean {
        // 1. Check minimum supported version code
        if (remoteConfig.minimumSupportedVersionCode > 0 && currentVersionCode > 0) {
            if (currentVersionCode < remoteConfig.minimumSupportedVersionCode) {
                return false
            }
        }

        // 2. Check minimum supported version name
        if (remoteConfig.minimumSupportedVersionName.isNotBlank() && currentVersionName.isNotBlank()) {
            if (compareVersionNames(remoteConfig.minimumSupportedVersionName, currentVersionName) > 0) {
                return false
            }
        }

        // 3. Check latest version code
        if (remoteConfig.latestVersionCode > 0 && currentVersionCode > 0) {
            if (currentVersionCode < remoteConfig.latestVersionCode) {
                return false
            }
        }

        // 4. Check latest version name
        if (remoteConfig.latestVersionName.isNotBlank() && currentVersionName.isNotBlank()) {
            if (compareVersionNames(remoteConfig.latestVersionName, currentVersionName) > 0) {
                return false
            }
        }

        return true
    }

    /**
     * Semantic version string comparison: returns >0 if left > right, <0 if left < right, 0 if equal.
     */
    fun compareVersionNames(left: String, right: String): Int {
        if (left.isBlank() && right.isBlank()) return 0
        if (left.isBlank()) return -1
        if (right.isBlank()) return 1
        val leftCleanStr = left.removePrefix("v").removePrefix("V").split('-').first().trim()
        val rightCleanStr = right.removePrefix("v").removePrefix("V").split('-').first().trim()
        val leftParts = leftCleanStr.split('.')
        val rightParts = rightCleanStr.split('.')
        val length = maxOf(leftParts.size, rightParts.size)
        for (index in 0 until length) {
            val leftPartDigits = leftParts.getOrNull(index)?.takeWhile { it.isDigit() }.orEmpty()
            val rightPartDigits = rightParts.getOrNull(index)?.takeWhile { it.isDigit() }.orEmpty()
            val leftVal = leftPartDigits.toIntOrNull() ?: 0
            val rightVal = rightPartDigits.toIntOrNull() ?: 0
            if (leftVal != rightVal) return leftVal.compareTo(rightVal)
        }
        return 0
    }
}
