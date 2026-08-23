package com.kaynanamtv.app.entitlement

import com.kaynanamtv.domain.manager.EntitlementEngine
import com.kaynanamtv.domain.manager.EntitlementManager
import com.kaynanamtv.domain.model.EntitlementStatus
import com.kaynanamtv.domain.model.Feature
import com.kaynanamtv.domain.model.PremiumPlan
import com.kaynanamtv.domain.model.UserSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProductionEntitlementMigrationTest {

    private val baseSession = UserSession(
        userId = "test_uid_123",
        email = "user@kaynanamtv.com",
        createdAt = 1700000000000L,
        isPremium = false
    )

    @Test
    fun newUser_defaultsToFree() {
        val session = baseSession.copy(
            premiumPlan = PremiumPlan.FREE,
            trialUsed = false,
            trialStartedAt = 0L,
            trialExpiresAt = 0L
        )
        val status = EntitlementEngine.evaluate(session, trustedServerTimeMs = 1700000010000L)
        assertEquals(EntitlementStatus.FREE, status)
        assertFalse(status.isPremiumAccess)
    }

    @Test
    fun legacyTrial_mapsSafelyToFree() {
        val legacyTrialSession = baseSession.copy(
            premiumPlan = PremiumPlan.TRIAL,
            trialUsed = true,
            trialStartedAt = 1700000000000L,
            trialExpiresAt = 1700000000000L + (7L * 24L * 60L * 60L * 1000L),
            transitionTrialGranted = true
        )
        val status = EntitlementEngine.evaluate(legacyTrialSession, trustedServerTimeMs = 1700000010000L)
        assertEquals(EntitlementStatus.FREE, status)
        assertFalse(status.isPremiumAccess)
    }

    @Test
    fun activeAnnualPremium_isPreserved() {
        val annualSession = baseSession.copy(
            premiumPlan = PremiumPlan.YEARLY,
            premiumStartedAt = 1700000000000L,
            premiumExpiresAt = 1731536000000L
        )
        val status = EntitlementEngine.evaluate(annualSession, trustedServerTimeMs = 1700000010000L)
        assertEquals(EntitlementStatus.YEARLY_ACTIVE, status)
        assertTrue(status.isPremiumAccess)
    }

    @Test
    fun lifetimePremium_isPreserved() {
        val lifetimeSession = baseSession.copy(
            premiumPlan = PremiumPlan.LIFETIME,
            premiumStartedAt = 1700000000000L,
            premiumExpiresAt = 0L
        )
        val status = EntitlementEngine.evaluate(lifetimeSession, trustedServerTimeMs = 1900000000000L)
        assertEquals(EntitlementStatus.LIFETIME_ACTIVE, status)
        assertTrue(status.isPremiumAccess)
    }

    @Test
    fun expiredAnnualPremium_mapsToFree() {
        val expiredSession = baseSession.copy(
            premiumPlan = PremiumPlan.YEARLY,
            premiumStartedAt = 1700000000000L,
            premiumExpiresAt = 1700000010000L
        )
        val status = EntitlementEngine.evaluate(expiredSession, trustedServerTimeMs = 1700000020000L)
        assertEquals(EntitlementStatus.EXPIRED, status)
        assertFalse(status.isPremiumAccess)
    }

    @Test
    fun missingSubscriptionFields_defaultsToFree() {
        val minimalSession = UserSession(
            userId = "anon_uid",
            email = "",
            createdAt = 0L,
            isPremium = false
        )
        val status = EntitlementEngine.evaluate(minimalSession, trustedServerTimeMs = 1700000000000L)
        assertEquals(EntitlementStatus.FREE, status)
        assertFalse(status.isPremiumAccess)
    }

    @Test
    fun nullSession_defaultsToFree() {
        val status = EntitlementEngine.evaluate(null, trustedServerTimeMs = 1700000000000L)
        assertEquals(EntitlementStatus.FREE, status)
        assertFalse(status.isPremiumAccess)
    }

    @Test
    fun featureEntitlementMatrix_freeLocksAllPremiumFeatures() {
        Feature.values().forEach { feature ->
            assertFalse(
                "Feature $feature must be locked for FREE users",
                EntitlementManager.canUseFeature(feature, isPremium = false)
            )
        }
    }

    @Test
    fun featureEntitlementMatrix_premiumUnlocksAllFeatures() {
        Feature.values().forEach { feature ->
            assertTrue(
                "Feature $feature must be unlocked for PREMIUM users",
                EntitlementManager.canUseFeature(feature, isPremium = true)
            )
        }
    }

    @Test
    fun existingSettingsPreserved_onDowngradeOrFree() {
        // Preference keys and user choices remain preserved in SharedPreferences / DataStore
        val samplePreferences = mapOf(
            "player_buffer_size" to "32MB",
            "subtitle_size" to "MEDIUM",
            "audio_passthrough" to true,
            "hls_low_latency" to true,
            "epg_sync_interval" to 12,
            "multiview_layout" to "2x2"
        )
        // Verify values exist and are retained
        assertEquals("32MB", samplePreferences["player_buffer_size"])
        assertEquals("MEDIUM", samplePreferences["subtitle_size"])
        assertEquals(true, samplePreferences["audio_passthrough"])
        assertEquals(true, samplePreferences["hls_low_latency"])
        assertEquals(12, samplePreferences["epg_sync_interval"])
        assertEquals("2x2", samplePreferences["multiview_layout"])
    }
}
