package com.kaynanamtv.data.repository

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException
import com.google.firebase.auth.FirebaseAuthInvalidUserException
import com.google.firebase.auth.FirebaseAuthUserCollisionException
import com.google.firebase.auth.FirebaseAuthWeakPasswordException
import com.google.firebase.firestore.FirebaseFirestore
import com.kaynanamtv.domain.model.Result
import com.kaynanamtv.domain.model.TrialStatus
import com.kaynanamtv.domain.model.UserSession
import com.kaynanamtv.domain.repository.AuthRepository
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class AuthRepositoryImpl @Inject constructor(
    private val preferencesRepository: com.kaynanamtv.data.preferences.PreferencesRepository
) : AuthRepository {

    private val auth: FirebaseAuth = FirebaseAuth.getInstance()
    private val firestore: FirebaseFirestore = FirebaseFirestore.getInstance()

    private var lastKnownServerTime: Long = 0L
    private var lastKnownServerRealtime: Long = 0L

    private fun getTrustedServerTime(): Long {
        return if (lastKnownServerTime > 0L && lastKnownServerRealtime > 0L) {
            val elapsed = android.os.SystemClock.elapsedRealtime() - lastKnownServerRealtime
            lastKnownServerTime + elapsed
        } else {
            System.currentTimeMillis()
        }
    }

    private fun parseUserSession(user: com.google.firebase.auth.FirebaseUser, doc: com.google.firebase.firestore.DocumentSnapshot): UserSession {
        val planStr = doc.getString("premiumPlan")
        val plan = com.kaynanamtv.domain.model.PremiumPlan.fromString(planStr)
        val trialUsed = doc.getBoolean("trialUsed") ?: (doc.getLong("trialExpiresAt") != null)
        val trialStartedAt = doc.getLong("trialStartedAt") ?: (doc.getLong("createdAt") ?: 0L)
        val trialExpiresAt = doc.getLong("trialExpiresAt") ?: 0L
        val premiumStartedAt = doc.getLong("premiumStartedAt") ?: 0L
        val premiumExpiresAt = doc.getLong("premiumExpiresAt") ?: 0L
        val transitionTrialGranted = doc.getBoolean("transitionTrialGranted") ?: false
        val entitlementVersion = doc.getLong("entitlementVersion")?.toInt() ?: 1
        val role = doc.getString("role") ?: (if (user.email == "kilicemre3437@gmail.com") "ADMIN" else "USER")
        val isAdmin = doc.getBoolean("isAdmin") ?: (role == "ADMIN" || user.email == "kilicemre3437@gmail.com")
        val docUpdatedAt = doc.getLong("updatedAt") ?: System.currentTimeMillis()

        lastKnownServerTime = docUpdatedAt
        lastKnownServerRealtime = android.os.SystemClock.elapsedRealtime()

        val rawSession = UserSession(
            userId = user.uid,
            email = user.email ?: "",
            createdAt = doc.getLong("createdAt") ?: 0L,
            isPremium = false,
            premiumPlan = plan,
            premiumStartedAt = premiumStartedAt,
            premiumExpiresAt = premiumExpiresAt,
            trialUsed = trialUsed,
            trialStartedAt = trialStartedAt,
            trialExpiresAt = trialExpiresAt,
            transitionTrialGranted = transitionTrialGranted,
            entitlementVersion = entitlementVersion,
            role = role,
            isAdmin = isAdmin,
            lastVerifiedServerTime = lastKnownServerTime
        )

        val entitlementStatus = com.kaynanamtv.domain.manager.EntitlementEngine.evaluate(
            session = rawSession,
            trustedServerTimeMs = getTrustedServerTime()
        )

        return rawSession.copy(isPremium = entitlementStatus.isPremiumAccess)
    }

    override fun getSessionFlow(): Flow<UserSession?> = callbackFlow {
        var firestoreListener: com.google.firebase.firestore.ListenerRegistration? = null

        val authListener = FirebaseAuth.AuthStateListener { firebaseAuth ->
            val user = firebaseAuth.currentUser
            if (user == null) {
                firestoreListener?.remove()
                firestoreListener = null
                trySend(null)
            } else {
                firestoreListener?.remove()
                firestoreListener = firestore.collection("users").document(user.uid)
                    .addSnapshotListener { doc, error ->
                        if (error != null) {
                            android.util.Log.w("AuthRepository", "Firestore snapshot error, keeping session", error)
                            return@addSnapshotListener
                        }
                        if (doc != null && doc.exists()) {
                            @Suppress("UNCHECKED_CAST")
                            val settingsMap = doc.get("settings") as? Map<String, Any>
                            if (settingsMap != null) {
                                @OptIn(kotlinx.coroutines.DelicateCoroutinesApi::class)
                                kotlinx.coroutines.CoroutineScope(
                                    kotlinx.coroutines.SupervisorJob() + kotlinx.coroutines.Dispatchers.IO
                                ).launch {
                                    try {
                                        preferencesRepository.restorePreferencesMap(settingsMap)
                                    } catch (e: Exception) {
                                        android.util.Log.e("AuthRepository", "Failed to restore settings", e)
                                    }
                                }
                            }
                            val session = parseUserSession(user, doc)
                            trySend(session)
                        } else {
                            trySend(null)
                        }
                    }
            }
        }
        auth.addAuthStateListener(authListener)
        awaitClose {
            firestoreListener?.remove()
            auth.removeAuthStateListener(authListener)
        }
    }

    override suspend fun getCurrentSession(): UserSession? {
        val user = auth.currentUser ?: return null
        return try {
            kotlinx.coroutines.withTimeoutOrNull(5000) {
                val doc = firestore.collection("users").document(user.uid).get().await()
                if (doc.exists()) {
                    @Suppress("UNCHECKED_CAST")
                    val settingsMap = doc.get("settings") as? Map<String, Any>
                    if (settingsMap != null) {
                        preferencesRepository.restorePreferencesMap(settingsMap)
                    }
                    parseUserSession(user, doc)
                } else null
            } ?: run {
                android.util.Log.w("AuthRepository", "Firestore timeout in getCurrentSession, returning safe default")
                UserSession(
                    userId = user.uid,
                    email = user.email ?: "",
                    createdAt = 0L,
                    isPremium = false
                )
            }
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "getCurrentSession exception", e)
            UserSession(
                userId = user.uid,
                email = user.email ?: "",
                createdAt = 0L,
                isPremium = false
            )
        }
    }

    override suspend fun login(email: String, pass: String): Result<UserSession> {
        return try {
            val result = auth.signInWithEmailAndPassword(email, pass).await()
            val user = result.user ?: return Result.error("Giriş başarısız. Lütfen tekrar deneyin.")
            val doc = firestore.collection("users").document(user.uid).get().await()
            if (!doc.exists()) {
                return Result.error("Kullanıcı verisi bulunamadı.")
            }
            @Suppress("UNCHECKED_CAST")
            val settingsMap = doc.get("settings") as? Map<String, Any>
            if (settingsMap != null) {
                preferencesRepository.restorePreferencesMap(settingsMap)
            }
            val session = parseUserSession(user, doc)
            Result.success(session)
        } catch (e: Exception) {
            val message = when (e) {
                is FirebaseAuthInvalidUserException -> when (e.errorCode) {
                    "ERROR_USER_NOT_FOUND" -> "Bu e-posta adresiyle kayıtlı bir hesap bulunamadı."
                    "ERROR_USER_DISABLED" -> "Bu hesap devre dışı bırakılmış."
                    else -> "Kullanıcı bulunamadı."
                }
                is FirebaseAuthInvalidCredentialsException -> when (e.errorCode) {
                    "ERROR_WRONG_PASSWORD" -> "Şifre hatalı. Lütfen tekrar deneyin."
                    "ERROR_INVALID_EMAIL" -> "Geçersiz e-posta adresi formatı."
                    "ERROR_INVALID_CREDENTIAL" -> "E-posta veya şifre hatalı."
                    else -> "Giriş bilgileri hatalı."
                }
                else -> when {
                    e.message?.contains("no user record") == true -> "Bu e-posta ile kayıtlı hesap bulunamadı."
                    e.message?.contains("INVALID_LOGIN_CREDENTIALS") == true -> "E-posta veya şifre hatalı."
                    e.message?.contains("password is invalid") == true -> "Şifre hatalı."
                    e.message?.contains("badly formatted") == true -> "Geçersiz e-posta adresi."
                    e.message?.contains("too many requests") == true -> "Çok fazla başarısız deneme. Lütfen bir süre bekleyin."
                    e.message?.contains("TOO_MANY_ATTEMPTS_TRY_LATER") == true -> "Çok fazla deneme yapıldı. Lütfen bir süre bekleyin."
                    e.message?.contains("network") == true || e.message?.contains("Network") == true -> "İnternet bağlantısı yok. Lütfen bağlantınızı kontrol edin."
                    else -> "Giriş yapılamadı. Lütfen tekrar deneyin."
                }
            }
            Result.error(message)
        }
    }

    override suspend fun register(email: String, pass: String): Result<UserSession> {
        if (pass.length < 6) {
            return Result.error("Şifre en az 6 karakter olmalıdır.")
        }
        return try {
            val result = auth.createUserWithEmailAndPassword(email, pass).await()
            val user = result.user ?: return Result.error("Kayıt başarısız.")

            val now = System.currentTimeMillis()
            val trialExpiresAt = now + com.kaynanamtv.domain.manager.EntitlementEngine.TRIAL_DURATION_MS // 7 gün tek seferlik deneme süresi

            val userData = hashMapOf(
                "userId" to user.uid,
                "email" to email,
                "createdAt" to now,
                "isPremium" to true,
                "premiumPlan" to com.kaynanamtv.domain.model.PremiumPlan.TRIAL.name,
                "premiumStartedAt" to now,
                "premiumExpiresAt" to 0L,
                "trialUsed" to true,
                "trialStartedAt" to now,
                "trialExpiresAt" to trialExpiresAt,
                "transitionTrialGranted" to false,
                "entitlementVersion" to 1,
                "role" to "USER",
                "isAdmin" to false,
                "updatedAt" to now
            )
            firestore.collection("users").document(user.uid).set(userData).await()

            val session = UserSession(
                userId = user.uid,
                email = email,
                createdAt = now,
                isPremium = true,
                premiumPlan = com.kaynanamtv.domain.model.PremiumPlan.TRIAL,
                premiumStartedAt = now,
                premiumExpiresAt = 0L,
                trialUsed = true,
                trialStartedAt = now,
                trialExpiresAt = trialExpiresAt,
                transitionTrialGranted = false,
                entitlementVersion = 1,
                role = "USER",
                isAdmin = false,
                lastVerifiedServerTime = now
            )
            Result.success(session)
        } catch (e: Exception) {
            val message = when (e) {
                is FirebaseAuthUserCollisionException -> "Bu e-posta adresiyle zaten bir hesap mevcut. Giriş yapmayı deneyin."
                is FirebaseAuthWeakPasswordException -> "Şifre çok zayıf. En az 6 karakter kullanın."
                is FirebaseAuthInvalidCredentialsException -> "Geçersiz e-posta adresi formatı."
                else -> when {
                    e.message?.contains("email address is already") == true -> "Bu e-posta ile zaten bir hesap mevcut."
                    e.message?.contains("badly formatted") == true -> "Geçersiz e-posta adresi."
                    e.message?.contains("network") == true || e.message?.contains("Network") == true -> "İnternet bağlantısı yok. Lütfen bağlantınızı kontrol edin."
                    else -> "Kayıt yapılamadı. Lütfen tekrar deneyin."
                }
            }
            Result.error(message)
        }
    }

    override suspend fun logout(): Result<Unit> {
        return try {
            auth.signOut()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.error("Çıkış yapılamadı: ${e.message}")
        }
    }

    override suspend fun checkTrialStatus(): TrialStatus {
        val session = getCurrentSession() ?: return TrialStatus.NO_SESSION
        val trustedTime = getTrustedServerTime()
        val entitlement = com.kaynanamtv.domain.manager.EntitlementEngine.evaluate(session, trustedTime)
        return if (entitlement.isPremiumAccess) TrialStatus.ACTIVE else TrialStatus.EXPIRED
    }

    override suspend fun submitPaymentRequest(
        plan: com.kaynanamtv.domain.model.PremiumPlan,
        expectedPrice: String
    ): Result<com.kaynanamtv.domain.model.PaymentRequest> {
        val user = auth.currentUser ?: return Result.error("Oturum açılmamış.")
        return try {
            val charset = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"
            val randomSuffix = (1..6).map { charset.random() }.joinToString("")
            val paymentCode = "KTV-$randomSuffix"
            val now = System.currentTimeMillis()
            val requestId = firestore.collection("payment_requests").document().id

            val requestData = hashMapOf(
                "requestId" to requestId,
                "uid" to user.uid,
                "userId" to user.uid,
                "email" to (user.email ?: ""),
                "userEmail" to (user.email ?: ""),
                "plan" to plan.name,
                "expectedPrice" to expectedPrice,
                "amount" to expectedPrice,
                "paymentCode" to paymentCode,
                "createdAt" to now,
                "status" to com.kaynanamtv.domain.model.PaymentRequestStatus.PENDING.name
            )

            firestore.collection("payment_requests").document(requestId).set(requestData).await()

            val paymentRequest = com.kaynanamtv.domain.model.PaymentRequest(
                requestId = requestId,
                uid = user.uid,
                email = user.email ?: "",
                plan = plan,
                expectedPrice = expectedPrice,
                paymentCode = paymentCode,
                createdAt = now,
                status = com.kaynanamtv.domain.model.PaymentRequestStatus.PENDING
            )
            Result.success(paymentRequest)
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "Payment request submission failed", e)
            Result.error("Ödeme talebi iletilemedi: ${e.message}")
        }
    }

    override fun getPaymentRequestsFlow(): Flow<List<com.kaynanamtv.domain.model.PaymentRequest>> = callbackFlow {
        val user = auth.currentUser
        if (user == null) {
            trySend(emptyList())
            close()
            return@callbackFlow
        }

        val listener = firestore.collection("payment_requests")
            .whereEqualTo("uid", user.uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    android.util.Log.w("AuthRepository", "Payment requests snapshot error", error)
                    return@addSnapshotListener
                }
                val requests = snapshot?.documents?.mapNotNull { doc ->
                    try {
                        com.kaynanamtv.domain.model.PaymentRequest(
                            requestId = doc.getString("requestId") ?: doc.id,
                            uid = doc.getString("uid") ?: "",
                            email = doc.getString("email") ?: "",
                            plan = com.kaynanamtv.domain.model.PremiumPlan.fromString(doc.getString("plan")),
                            expectedPrice = doc.getString("expectedPrice") ?: "",
                            paymentCode = doc.getString("paymentCode") ?: "",
                            createdAt = doc.getLong("createdAt") ?: 0L,
                            status = com.kaynanamtv.domain.model.PaymentRequestStatus.fromString(doc.getString("status")),
                            approvedAt = doc.getLong("approvedAt"),
                            approvedBy = doc.getString("approvedBy"),
                            notes = doc.getString("notes")
                        )
                    } catch (e: Exception) {
                        null
                    }
                }?.sortedByDescending { it.createdAt } ?: emptyList()

                trySend(requests)
            }

        awaitClose {
            listener.remove()
        }
    }

    override suspend fun sendPasswordResetEmail(email: String): Result<Unit> {
        val trimmed = email.trim()
        if (trimmed.isBlank()) return Result.error("Lütfen önce e-posta adresinizi girin.")
        android.util.Log.d("AuthRepository", "sendPasswordResetEmail: starting for $trimmed")
        return try {
            auth.sendPasswordResetEmail(trimmed).await()
            android.util.Log.i("AuthRepository", "sendPasswordResetEmail: success for $trimmed")
            Result.success(Unit)
        } catch (e: Exception) {
            android.util.Log.e("AuthRepository", "sendPasswordResetEmail: failed for $trimmed", e)
            val message = when (e) {
                is FirebaseAuthInvalidUserException -> "Bu e-posta adresiyle kayıtlı bir hesap bulunamadı."
                is FirebaseAuthInvalidCredentialsException -> "Geçersiz e-posta adresi formatı."
                else -> when {
                    e.message?.contains("no user record", ignoreCase = true) == true -> "Bu e-posta ile kayıtlı bir hesap bulunamadı."
                    e.message?.contains("badly formatted", ignoreCase = true) == true -> "Geçersiz e-posta adresi formatı."
                    e.message?.contains("too many requests", ignoreCase = true) == true -> "Çok fazla deneme yapıldı. Lütfen birkaç dakika sonra tekrar deneyin."
                    e.message?.contains("network", ignoreCase = true) == true -> "İnternet bağlantısı kurulamadı. Lütfen ağ bağlantınızı kontrol edin."
                    else -> e.localizedMessage ?: "Şifre sıfırlama e-postası gönderilemedi. Lütfen tekrar deneyin."
                }
            }
            Result.error(message)
        }
    }

    override suspend fun getDeviceCode(): Result<Pair<String, String>> {
        // TV activation via device code (Firestore-based polling)
        return try {
            val code = "KNM-" + (100000..999999).random().toString()
            val url = "kaynanamtv.app/activate"
            val expiry = System.currentTimeMillis() + 10 * 60 * 1000L // 10 min expiry
            firestore.collection("device_codes").document(code).set(
                hashMapOf(
                    "code" to code,
                    "url" to url,
                    "createdAt" to System.currentTimeMillis(),
                    "expiresAt" to expiry,
                    "activated" to false,
                    "userId" to null
                )
            ).await()
            Result.success(Pair(code, url))
        } catch (e: Exception) {
            Result.error("Aktivasyon kodu oluşturulamadı: ${e.message}")
        }
    }

    override suspend fun watchDeviceActivation(deviceCode: String): Flow<Result<UserSession>> = callbackFlow {
        val docRef = firestore.collection("device_codes").document(deviceCode)
        val listener = docRef.addSnapshotListener { snapshot, error ->
            if (error != null) {
                trySend(Result.error("Bağlantı hatası: ${error.message}"))
                return@addSnapshotListener
            }
            val activated = snapshot?.getBoolean("activated") ?: false
            if (activated) {
                val userId = snapshot?.getString("userId")
                if (userId != null) {
                    firestore.collection("users").document(userId).get()
                        .addOnSuccessListener { userDoc ->
                            if (userDoc.exists()) {
                                val session = UserSession(
                                    userId = userId,
                                    email = userDoc.getString("email") ?: "",
                                    createdAt = userDoc.getLong("createdAt") ?: 0L,
                                    trialExpiresAt = userDoc.getLong("trialExpiresAt") ?: 0L,
                                    isPremium = userDoc.getBoolean("isPremium") ?: false
                                )
                                trySend(Result.success(session))
                            }
                        }
                }
            }
        }
        awaitClose { listener.remove() }
    }
}
