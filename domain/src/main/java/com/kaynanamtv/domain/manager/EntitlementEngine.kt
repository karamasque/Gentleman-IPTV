package com.kaynanamtv.domain.manager

import com.kaynanamtv.domain.model.EntitlementStatus
import com.kaynanamtv.domain.model.PremiumPlan
import com.kaynanamtv.domain.model.UserSession

object EntitlementEngine {
    const val OFFLINE_GRACE_PERIOD_MS = 72L * 60L * 60L * 1000L // 72 hours

    fun evaluate(
        session: UserSession?,
        trustedServerTimeMs: Long,
        lastVerifiedServerTimeMs: Long = session?.lastVerifiedServerTime ?: 0L,
        offlineElapsedMs: Long = 0L
    ): EntitlementStatus {
        if (session == null) return EntitlementStatus.FREE

        // Lifetime plan: Always active once granted by server/admin
        if (session.premiumPlan == PremiumPlan.LIFETIME) {
            return EntitlementStatus.LIFETIME_ACTIVE
        }

        // Offline grace expiration check:
        // If the client has been completely offline without server contact for > 72 hours, fallback to FREE
        if (lastVerifiedServerTimeMs > 0L && offlineElapsedMs > OFFLINE_GRACE_PERIOD_MS) {
            return EntitlementStatus.EXPIRED
        }

        // Yearly plan: Check expiration against trusted server time
        if (session.premiumPlan == PremiumPlan.YEARLY) {
            return if (session.premiumExpiresAt > trustedServerTimeMs) {
                EntitlementStatus.YEARLY_ACTIVE
            } else {
                EntitlementStatus.EXPIRED
            }
        }

        // Legacy TRIAL or FREE users default safely to FREE
        return EntitlementStatus.FREE
    }
}
