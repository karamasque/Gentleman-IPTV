package com.kaynanamtv.data.manager

import com.kaynanamtv.domain.manager.EntitlementEngine
import com.kaynanamtv.domain.manager.EntitlementManager
import com.kaynanamtv.domain.model.EntitlementStatus
import com.kaynanamtv.domain.model.Feature
import com.kaynanamtv.domain.model.UserSession
import com.kaynanamtv.domain.repository.AuthRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class DefaultEntitlementManager @Inject constructor(
    private val authRepository: AuthRepository
) : EntitlementManager {

    override val sessionFlow: Flow<UserSession?> = authRepository.getSessionFlow()

    override val entitlementStatusFlow: Flow<EntitlementStatus> = sessionFlow.map { session ->
        EntitlementEngine.evaluate(session, System.currentTimeMillis())
    }

    override val isPremiumFlow: Flow<Boolean> = entitlementStatusFlow.map { it.isPremiumAccess }

    override suspend fun getCurrentStatus(): EntitlementStatus {
        val session = authRepository.getCurrentSession()
        return EntitlementEngine.evaluate(session, System.currentTimeMillis())
    }

    override suspend fun isPremium(): Boolean {
        return getCurrentStatus().isPremiumAccess
    }

    override suspend fun canUse(feature: Feature): Boolean {
        val isPrem = isPremium()
        return EntitlementManager.canUseFeature(feature, isPrem)
    }

    override fun observeFeature(feature: Feature): Flow<Boolean> {
        return isPremiumFlow.map { isPrem ->
            EntitlementManager.canUseFeature(feature, isPrem)
        }
    }
}
