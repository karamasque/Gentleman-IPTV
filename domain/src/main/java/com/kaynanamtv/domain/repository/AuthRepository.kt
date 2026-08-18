package com.kaynanamtv.domain.repository

import com.kaynanamtv.domain.model.Result
import com.kaynanamtv.domain.model.TrialStatus
import com.kaynanamtv.domain.model.UserSession
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    fun getSessionFlow(): Flow<UserSession?>
    suspend fun getCurrentSession(): UserSession?
    suspend fun login(email: String, pass: String): Result<UserSession>
    suspend fun register(email: String, pass: String): Result<UserSession>
    suspend fun logout(): Result<Unit>
    suspend fun checkTrialStatus(): TrialStatus
    suspend fun getDeviceCode(): Result<Pair<String, String>> // Returns Pair(DeviceCode, ActivationUrl)
    suspend fun watchDeviceActivation(deviceCode: String): Flow<Result<UserSession>>
    suspend fun sendPasswordResetEmail(email: String): Result<Unit>
    suspend fun submitPaymentRequest(plan: com.kaynanamtv.domain.model.PremiumPlan, expectedPrice: String): Result<com.kaynanamtv.domain.model.PaymentRequest>
    fun getPaymentRequestsFlow(): Flow<List<com.kaynanamtv.domain.model.PaymentRequest>>
}
