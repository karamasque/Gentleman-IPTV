package com.kaynanamtv.domain.manager

import com.kaynanamtv.domain.model.AppRemoteConfig
import com.kaynanamtv.domain.model.ForceUpdateDecision

object ForceUpdateEngine {

    /**
     * Evaluates whether the application should be completely blocked by a mandatory force-update.
     *
     * @param currentVersionCode The running app's BuildConfig.VERSION_CODE
     * @param remoteConfig The central configuration loaded from server or cache
     * @param cachedForceUpdateBlocked Whether this device was previously flagged as obsolete
     */
    fun evaluate(
        currentVersionCode: Int,
        remoteConfig: AppRemoteConfig?,
        cachedForceUpdateBlocked: Boolean = false
    ): ForceUpdateDecision {
        // Fail-closed protection:
        // If the device already knows it is obsolete/blocked, keep it blocked offline unless server now allows it.
        if (cachedForceUpdateBlocked) {
            if (remoteConfig != null && currentVersionCode >= remoteConfig.minimumSupportedVersionCode) {
                return ForceUpdateDecision.ALLOWED
            }
            return ForceUpdateDecision.BLOCKED_FORCE_UPDATE_REQUIRED
        }

        // Temporary network / Firebase downtime grace:
        // If remote config is unreachable and no previous block was recorded, allow access.
        if (remoteConfig == null) {
            return ForceUpdateDecision.ALLOWED
        }

        // Mandatory update check
        if (remoteConfig.forceUpdate && currentVersionCode < remoteConfig.minimumSupportedVersionCode) {
            return ForceUpdateDecision.BLOCKED_FORCE_UPDATE_REQUIRED
        }

        return ForceUpdateDecision.ALLOWED
    }
}
