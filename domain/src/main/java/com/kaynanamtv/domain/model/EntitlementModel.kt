package com.kaynanamtv.domain.model

enum class PremiumPlan {
    FREE,
    TRIAL,
    YEARLY,
    LIFETIME;

    companion object {
        fun fromString(value: String?): PremiumPlan {
            return when (value?.uppercase()) {
                "LIFETIME" -> LIFETIME
                "YEARLY" -> YEARLY
                "TRIAL" -> TRIAL
                else -> FREE
            }
        }
    }
}

object PremiumPricingConfig {
    const val PRICE_YEARLY = "349 TL"
    const val PRICE_LIFETIME = "749 TL"
    const val AMOUNT_YEARLY_NUMERIC = 349
    const val AMOUNT_LIFETIME_NUMERIC = 749

    fun getPrice(plan: PremiumPlan): String {
        return when (plan) {
            PremiumPlan.YEARLY -> PRICE_YEARLY
            PremiumPlan.LIFETIME -> PRICE_LIFETIME
            else -> "0 TL"
        }
    }
}

object PremiumBankConfig {
    const val ACCOUNT_HOLDER = "Emre Kılıç"
    const val BANK_NAME = "QNB Finansbank"
    const val IBAN_FORMATTED = "TR64 0015 7000 0000 0068 7735 18"
    const val IBAN_CLEAN = "TR640015700000000068773518"
}

enum class EntitlementStatus {
    FREE,
    TRIAL_ACTIVE,
    YEARLY_ACTIVE,
    LIFETIME_ACTIVE,
    EXPIRED;

    val isPremiumAccess: Boolean
        get() = this == TRIAL_ACTIVE || this == YEARLY_ACTIVE || this == LIFETIME_ACTIVE
}

enum class PaymentRequestStatus {
    PENDING,
    APPROVED,
    REJECTED,
    CANCELLED;

    companion object {
        fun fromString(value: String?): PaymentRequestStatus {
            return when (value?.uppercase()) {
                "APPROVED" -> APPROVED
                "REJECTED" -> REJECTED
                "CANCELLED" -> CANCELLED
                else -> PENDING
            }
        }
    }
}

data class PaymentRequest(
    val requestId: String,
    val uid: String,
    val email: String,
    val plan: PremiumPlan,
    val expectedPrice: String,
    val paymentCode: String,
    val createdAt: Long,
    val status: PaymentRequestStatus = PaymentRequestStatus.PENDING,
    val approvedAt: Long? = null,
    val approvedBy: String? = null,
    val notes: String? = null
)

data class PremiumAuditEntry(
    val eventId: String,
    val targetUid: String,
    val action: String,
    val oldPlan: String,
    val newPlan: String,
    val oldExpiry: Long,
    val newExpiry: Long,
    val performedBy: String,
    val timestamp: Long,
    val paymentRequestId: String? = null,
    val reason: String? = null
)
