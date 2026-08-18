package com.kaynanamtv.domain.model

data class UserSession(
    val userId: String,
    val email: String,
    val createdAt: Long,
    val isPremium: Boolean,
    val premiumPlan: PremiumPlan = PremiumPlan.FREE,
    val premiumStartedAt: Long = 0L,
    val premiumExpiresAt: Long = 0L,
    val trialUsed: Boolean = false,
    val trialStartedAt: Long = 0L,
    val trialExpiresAt: Long = 0L,
    val transitionTrialGranted: Boolean = false,
    val entitlementVersion: Int = 1,
    val role: String = "USER",
    val isAdmin: Boolean = false,
    val lastVerifiedServerTime: Long = 0L
)
