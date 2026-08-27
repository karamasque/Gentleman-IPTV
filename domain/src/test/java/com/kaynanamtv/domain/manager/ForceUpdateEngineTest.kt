package com.kaynanamtv.domain.manager

import com.kaynanamtv.domain.model.AppRemoteConfig
import com.kaynanamtv.domain.model.ForceUpdateDecision
import org.junit.Assert.assertEquals
import org.junit.Test

class ForceUpdateEngineTest {

    @Test
    fun evaluate_version116_minimum117_returnsBlocked() {
        val config = AppRemoteConfig(
            minimumSupportedVersionCode = 117,
            minimumSupportedVersionName = "1.1.17",
            latestVersionCode = 117,
            latestVersionName = "1.1.17",
            forceUpdate = true
        )

        val result = ForceUpdateEngine.evaluate(
            currentVersionCode = 116,
            currentVersionName = "1.1.16",
            remoteConfig = config,
            cachedForceUpdateBlocked = false
        )

        assertEquals(ForceUpdateDecision.BLOCKED_FORCE_UPDATE_REQUIRED, result)
    }

    @Test
    fun evaluate_version116_minimum116_returnsAllowed() {
        val config = AppRemoteConfig(
            minimumSupportedVersionCode = 116,
            minimumSupportedVersionName = "1.1.16",
            latestVersionCode = 116,
            latestVersionName = "1.1.16",
            forceUpdate = true
        )

        val result = ForceUpdateEngine.evaluate(
            currentVersionCode = 116,
            currentVersionName = "1.1.16",
            remoteConfig = config,
            cachedForceUpdateBlocked = false
        )

        assertEquals(ForceUpdateDecision.ALLOWED, result)
    }

    @Test
    fun evaluate_version117_minimum116_returnsAllowed() {
        val config = AppRemoteConfig(
            minimumSupportedVersionCode = 116,
            minimumSupportedVersionName = "1.1.16",
            latestVersionCode = 116,
            latestVersionName = "1.1.16",
            forceUpdate = true
        )

        val result = ForceUpdateEngine.evaluate(
            currentVersionCode = 117,
            currentVersionName = "1.1.17",
            remoteConfig = config,
            cachedForceUpdateBlocked = false
        )

        assertEquals(ForceUpdateDecision.ALLOWED, result)
    }

    @Test
    fun evaluate_semanticVersionOnly_withoutVersionCode_newerRemote_returnsBlocked() {
        val config = AppRemoteConfig(
            minimumSupportedVersionCode = 0,
            minimumSupportedVersionName = "1.1.17",
            latestVersionCode = 0,
            latestVersionName = "1.1.17",
            forceUpdate = true
        )

        val result = ForceUpdateEngine.evaluate(
            currentVersionCode = 116,
            currentVersionName = "1.1.16",
            remoteConfig = config,
            cachedForceUpdateBlocked = false
        )

        assertEquals(ForceUpdateDecision.BLOCKED_FORCE_UPDATE_REQUIRED, result)
    }

    @Test
    fun evaluate_forceUpdateFalse_olderVersion_returnsAllowed() {
        val config = AppRemoteConfig(
            minimumSupportedVersionCode = 117,
            minimumSupportedVersionName = "1.1.17",
            latestVersionCode = 117,
            latestVersionName = "1.1.17",
            forceUpdate = false
        )

        val result = ForceUpdateEngine.evaluate(
            currentVersionCode = 116,
            currentVersionName = "1.1.16",
            remoteConfig = config,
            cachedForceUpdateBlocked = false
        )

        assertEquals(ForceUpdateDecision.ALLOWED, result)
    }

    @Test
    fun evaluate_cachedBlockedTrue_offlineNullConfig_returnsBlocked() {
        val result = ForceUpdateEngine.evaluate(
            currentVersionCode = 116,
            currentVersionName = "1.1.16",
            remoteConfig = null,
            cachedForceUpdateBlocked = true
        )

        assertEquals(ForceUpdateDecision.BLOCKED_FORCE_UPDATE_REQUIRED, result)
    }

    @Test
    fun evaluate_cachedBlockedFalse_offlineNullConfig_returnsAllowed() {
        val result = ForceUpdateEngine.evaluate(
            currentVersionCode = 116,
            currentVersionName = "1.1.16",
            remoteConfig = null,
            cachedForceUpdateBlocked = false
        )

        assertEquals(ForceUpdateDecision.ALLOWED, result)
    }

    @Test
    fun evaluate_cachedBlockedTrue_nowUpdatedApp_returnsAllowed() {
        val config = AppRemoteConfig(
            minimumSupportedVersionCode = 117,
            minimumSupportedVersionName = "1.1.17",
            latestVersionCode = 117,
            latestVersionName = "1.1.17",
            forceUpdate = true
        )

        val result = ForceUpdateEngine.evaluate(
            currentVersionCode = 117,
            currentVersionName = "1.1.17",
            remoteConfig = config,
            cachedForceUpdateBlocked = true
        )

        assertEquals(ForceUpdateDecision.ALLOWED, result)
    }
}
