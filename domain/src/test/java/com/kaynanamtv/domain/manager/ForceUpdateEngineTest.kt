package com.kaynanamtv.domain.manager

import com.kaynanamtv.domain.model.AppRemoteConfig
import com.kaynanamtv.domain.model.ForceUpdateDecision
import org.junit.Assert.assertEquals
import org.junit.Test

class ForceUpdateEngineTest {

    @Test
    fun evaluate_version66_minimum66_returnsAllowed() {
        val config = AppRemoteConfig(
            minimumSupportedVersionCode = 66,
            latestVersionCode = 66,
            latestVersionName = "1.0.66",
            forceUpdate = true
        )

        val result = ForceUpdateEngine.evaluate(
            currentVersionCode = 66,
            remoteConfig = config,
            cachedForceUpdateBlocked = false
        )

        assertEquals(ForceUpdateDecision.ALLOWED, result)
    }

    @Test
    fun evaluate_version66_minimum67_returnsBlocked() {
        val config = AppRemoteConfig(
            minimumSupportedVersionCode = 67,
            latestVersionCode = 67,
            latestVersionName = "1.0.67",
            forceUpdate = true
        )

        val result = ForceUpdateEngine.evaluate(
            currentVersionCode = 66,
            remoteConfig = config,
            cachedForceUpdateBlocked = false
        )

        assertEquals(ForceUpdateDecision.BLOCKED_FORCE_UPDATE_REQUIRED, result)
    }

    @Test
    fun evaluate_version67_minimum66_returnsAllowed() {
        val config = AppRemoteConfig(
            minimumSupportedVersionCode = 66,
            latestVersionCode = 66,
            latestVersionName = "1.0.66",
            forceUpdate = true
        )

        val result = ForceUpdateEngine.evaluate(
            currentVersionCode = 67,
            remoteConfig = config,
            cachedForceUpdateBlocked = false
        )

        assertEquals(ForceUpdateDecision.ALLOWED, result)
    }

    @Test
    fun evaluate_forceUpdateFalse_olderVersion_returnsAllowed() {
        val config = AppRemoteConfig(
            minimumSupportedVersionCode = 67,
            latestVersionCode = 67,
            latestVersionName = "1.0.67",
            forceUpdate = false
        )

        val result = ForceUpdateEngine.evaluate(
            currentVersionCode = 66,
            remoteConfig = config,
            cachedForceUpdateBlocked = false
        )

        assertEquals(ForceUpdateDecision.ALLOWED, result)
    }

    @Test
    fun evaluate_cachedBlockedTrue_offlineNullConfig_returnsBlocked() {
        val result = ForceUpdateEngine.evaluate(
            currentVersionCode = 66,
            remoteConfig = null,
            cachedForceUpdateBlocked = true
        )

        assertEquals(ForceUpdateDecision.BLOCKED_FORCE_UPDATE_REQUIRED, result)
    }

    @Test
    fun evaluate_cachedBlockedFalse_offlineNullConfig_returnsAllowed() {
        val result = ForceUpdateEngine.evaluate(
            currentVersionCode = 66,
            remoteConfig = null,
            cachedForceUpdateBlocked = false
        )

        assertEquals(ForceUpdateDecision.ALLOWED, result)
    }

    @Test
    fun evaluate_cachedBlockedTrue_nowUpdatedServerConfig_returnsAllowed() {
        val config = AppRemoteConfig(
            minimumSupportedVersionCode = 66,
            latestVersionCode = 66,
            latestVersionName = "1.0.66",
            forceUpdate = true
        )

        val result = ForceUpdateEngine.evaluate(
            currentVersionCode = 66,
            remoteConfig = config,
            cachedForceUpdateBlocked = true
        )

        assertEquals(ForceUpdateDecision.ALLOWED, result)
    }
}
