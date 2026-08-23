package com.kaynanamtv.domain.manager

import com.kaynanamtv.domain.model.EntitlementStatus
import com.kaynanamtv.domain.model.PremiumPlan
import com.kaynanamtv.domain.model.UserSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EntitlementEngineTest {

    private val baseSession = UserSession(
        userId = "test_user",
        email = "test@kaynanamtv.com",
        createdAt = 1000000L,
        isPremium = false
    )

    @Test
    fun nullSession_returnsFree() {
        val status = EntitlementEngine.evaluate(null, trustedServerTimeMs = 1000000L)
        assertEquals(EntitlementStatus.FREE, status)
        assertFalse(status.isPremiumAccess)
    }

    @Test
    fun lifetimePlan_returnsLifetimeActive() {
        val session = baseSession.copy(premiumPlan = PremiumPlan.LIFETIME)
        val status = EntitlementEngine.evaluate(session, trustedServerTimeMs = 5000000L)
        assertEquals(EntitlementStatus.LIFETIME_ACTIVE, status)
        assertTrue(status.isPremiumAccess)
    }

    @Test
    fun yearlyPlan_unexpired_returnsYearlyActive() {
        val session = baseSession.copy(
            premiumPlan = PremiumPlan.YEARLY,
            premiumStartedAt = 1000L,
            premiumExpiresAt = 2000L
        )
        val status = EntitlementEngine.evaluate(session, trustedServerTimeMs = 1500L)
        assertEquals(EntitlementStatus.YEARLY_ACTIVE, status)
        assertTrue(status.isPremiumAccess)
    }

    @Test
    fun yearlyPlan_expired_returnsExpired() {
        val session = baseSession.copy(
            premiumPlan = PremiumPlan.YEARLY,
            premiumStartedAt = 1000L,
            premiumExpiresAt = 2000L
        )
        val status = EntitlementEngine.evaluate(session, trustedServerTimeMs = 2500L)
        assertEquals(EntitlementStatus.EXPIRED, status)
        assertFalse(status.isPremiumAccess)
    }

    @Test
    fun legacyTrialPlan_returnsFree() {
        val session = baseSession.copy(
            premiumPlan = PremiumPlan.TRIAL,
            trialUsed = true,
            trialStartedAt = 1000L,
            trialExpiresAt = 999999999L
        )
        val status = EntitlementEngine.evaluate(session, trustedServerTimeMs = 2000L)
        assertEquals(EntitlementStatus.FREE, status)
        assertFalse(status.isPremiumAccess)
    }

    @Test
    fun freePlan_returnsFree() {
        val session = baseSession.copy(premiumPlan = PremiumPlan.FREE)
        val status = EntitlementEngine.evaluate(session, trustedServerTimeMs = 2000L)
        assertEquals(EntitlementStatus.FREE, status)
        assertFalse(status.isPremiumAccess)
    }

    @Test
    fun featureEntitlement_freeUser_locksPremiumFeatures() {
        com.kaynanamtv.domain.model.Feature.values().forEach { feature ->
            assertFalse(
                "Feature $feature should be locked for Free users",
                EntitlementManager.canUseFeature(feature, isPremium = false)
            )
        }
    }

    @Test
    fun featureEntitlement_premiumUser_unlocksAllFeatures() {
        com.kaynanamtv.domain.model.Feature.values().forEach { feature ->
            assertTrue(
                "Feature $feature should be unlocked for Premium users",
                EntitlementManager.canUseFeature(feature, isPremium = true)
            )
        }
    }

    @Test
    fun offlineGrace_exceeded_returnsExpired() {
        val session = baseSession.copy(
            premiumPlan = PremiumPlan.YEARLY,
            premiumStartedAt = 1000L,
            premiumExpiresAt = 999999999L,
            lastVerifiedServerTime = 1000L
        )
        // 73 hours offline (> 72 hours limit)
        val offlineElapsed = EntitlementEngine.OFFLINE_GRACE_PERIOD_MS + 3600000L
        val status = EntitlementEngine.evaluate(
            session = session,
            trustedServerTimeMs = 2000L,
            lastVerifiedServerTimeMs = 1000L,
            offlineElapsedMs = offlineElapsed
        )
        assertEquals(EntitlementStatus.EXPIRED, status)
        assertFalse(status.isPremiumAccess)
    }
}
