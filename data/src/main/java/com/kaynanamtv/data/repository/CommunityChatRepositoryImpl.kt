package com.kaynanamtv.data.repository

import android.content.Context
import android.util.Log
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.Query
import com.kaynanamtv.domain.model.BannedUserInfo
import com.kaynanamtv.domain.model.ChatMessage
import com.kaynanamtv.domain.model.ChatReport
import com.kaynanamtv.domain.model.ChatRoom
import com.kaynanamtv.domain.model.PrivateChatMessage
import com.kaynanamtv.domain.model.UserRole
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.tasks.await
import java.util.UUID
import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CommunityChatRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context
) : CommunityChatRepository {

    private companion object {
        private const val TAG = "CommunityChatRepo"
        private const val PREFS_NAME = "kaynanamtv_chat_prefs"
        private const val KEY_NICKNAME = "user_nickname"
        private const val KEY_HAS_CUSTOM_NICKNAME = "has_custom_nickname"
        private const val KEY_DEVICE_ID = "device_sender_id"
        private const val KEY_IS_ADMIN = "is_admin_mode_enabled"
        // SHA-256 hash of admin password — plain-text password is NOT in source code
        // To regenerate: echo -n 'YourPassword' | sha256sum
        private const val ADMIN_PASSWORD_HASH = "13d8cdaec67c9d10e36a3b145d006fa3ad9c80e9c5f88fb8839484ac871a88d0"
        private const val ACTIVE_THRESHOLD_MS = 180_000L
        private const val EDIT_WINDOW_MS = 5 * 60 * 1000L // 5 minutes
        private const val MESSAGE_COOLDOWN_MS = 1500L // Spam koruması
    }

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val avatarColors = listOf(
        "#3B82F6", "#10B981", "#F59E0B", "#EC4899",
        "#8B5CF6", "#06B6D4", "#EF4444", "#6366F1"
    )

    private val badWords = listOf(
        "amk", "aq", "amq", "sik", "sikerim", "orospu", "piç", "yarrak",
        "oç", "göt", "ibne", "kahpe", "yavşak", "amına", "fuck", "bitch",
        "shit", "asshole", "yarram", "sikti", "sikim", "götverene"
    )

    override fun getGeneralRoom(): ChatRoom = ChatRoom.GENERAL_ROOM
    override fun getAnnouncementsRoom(): ChatRoom = ChatRoom.ANNOUNCEMENTS_ROOM

    override fun getNickname(): String = prefs.getString(KEY_NICKNAME, "") ?: ""

    override fun hasCustomNickname(): Boolean {
        val saved = prefs.getString(KEY_NICKNAME, "")
        val hasCustom = prefs.getBoolean(KEY_HAS_CUSTOM_NICKNAME, false)
        return !saved.isNullOrBlank() && hasCustom
    }

    override suspend fun setNickname(nickname: String) {
        val trimmed = filterProfanity(nickname.trim()).take(20)
        if (trimmed.isNotBlank()) {
            prefs.edit()
                .putString(KEY_NICKNAME, trimmed)
                .putBoolean(KEY_HAS_CUSTOM_NICKNAME, true)
                .apply()
        }
    }

    private fun formatShortUserId(rawId: String): String {
        if (rawId.isBlank()) return "SV-ANONYM"
        if (rawId.startsWith("SV-")) {
            val clean = rawId.removePrefix("SV-").take(8).uppercase()
            return "SV-$clean"
        }
        val clean = rawId.take(8).uppercase()
        return "SV-$clean"
    }

    override fun getDeviceSenderId(): String {
        var id = prefs.getString(KEY_DEVICE_ID, null)
        if (id.isNullOrBlank() || !id.startsWith("SV-")) {
            val uid = runCatching { FirebaseAuth.getInstance().currentUser?.uid }.getOrNull()
            id = if (!uid.isNullOrBlank()) {
                "SV-${uid.take(8).uppercase()}"
            } else {
                val num = (100000..999999).random()
                "SV-$num"
            }
            prefs.edit().putString(KEY_DEVICE_ID, id).apply()
        }
        return id
    }

    override fun getUserEmail(): String {
        val firebaseEmail = runCatching { FirebaseAuth.getInstance().currentUser?.email }.getOrNull()
        if (!firebaseEmail.isNullOrBlank()) return firebaseEmail

        val savedEmail = prefs.getString("user_email", null)
            ?: prefs.getString("auth_email", null)
            ?: prefs.getString("active_account_username", null)
        if (!savedEmail.isNullOrBlank()) return savedEmail

        val uid = runCatching { FirebaseAuth.getInstance().currentUser?.uid }.getOrNull()
        return if (!uid.isNullOrBlank()) "Hesap (UID: ${uid.take(8)})" else "E-posta Belirtilmedi"
    }

    override fun getAccountCreatedAt(): Long {
        val currentUser = runCatching { FirebaseAuth.getInstance().currentUser }.getOrNull()
        val firebaseTimestamp = currentUser?.metadata?.creationTimestamp ?: 0L
        if (firebaseTimestamp > 0L) {
            prefs.edit().putLong("account_created_at", firebaseTimestamp).apply()
            return firebaseTimestamp
        }
        // Safely read from SharedPreferences only — no blocking network call on main thread
        val saved = prefs.getLong("account_created_at", 0L)
        return if (saved > 0L) saved else 0L
    }

    override fun isAdmin(): Boolean = prefs.getBoolean(KEY_IS_ADMIN, false) ||
        runCatching { FirebaseAuth.getInstance().currentUser?.email }.getOrNull() == "kilicemre3437@gmail.com"

    override fun verifyAdminPassword(password: String): Boolean {
        val inputHash = sha256Hex(password.trim())
        if (inputHash == ADMIN_PASSWORD_HASH) {
            prefs.edit().putBoolean(KEY_IS_ADMIN, true).apply()
            return true
        }
        return false
    }

    private fun sha256Hex(input: String): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun getAvatarColorHex(senderId: String): String {
        val hash = senderId.hashCode()
        val index = kotlin.math.abs(hash) % avatarColors.size
        return avatarColors[index]
    }

    private fun filterProfanity(text: String): String {
        var sanitized = text
        for (word in badWords) {
            runCatching {
                val pattern = Pattern.compile("(?i)\\b$word\\b|(?i)$word")
                val matcher = pattern.matcher(sanitized)
                if (matcher.find()) {
                    sanitized = matcher.replaceAll("*".repeat(word.length))
                }
            }
        }
        return sanitized
    }

    override suspend fun updatePresence() {
        val firestore = runCatching { FirebaseFirestore.getInstance() }.getOrNull() ?: return
        val senderId = getDeviceSenderId()
        val nickname = getNickname()
        val email = getUserEmail()

        val presenceData = hashMapOf(
            "senderId" to senderId,
            "senderName" to nickname,
            "userEmail" to email,
            "userCreatedAt" to getAccountCreatedAt(),
            "lastActive" to System.currentTimeMillis(),
            "userRole" to if (isAdmin()) UserRole.ADMIN else UserRole.USER
        )

        try {
            firestore.collection("active_users").document(senderId).set(presenceData).await()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to update presence: ${e.message}")
        }
    }

    override fun observeOnlineUsers(): Flow<List<String>> = flow {
        while (currentCoroutineContext().isActive) {
            emit(fetchOnlineUsersOnce())
            delay(60_000L)
        }
    }

    override fun observeOnlineUsersInfo(): Flow<List<com.kaynanamtv.domain.model.OnlineUserInfo>> = flow {
        val firestore = runCatching { FirebaseFirestore.getInstance() }.getOrNull()
        if (firestore == null) { emit(emptyList()); return@flow }
        while (currentCoroutineContext().isActive) {
            try {
                val cutoff = System.currentTimeMillis() - ACTIVE_THRESHOLD_MS
                val snapshot = firestore.collection("active_users").get().await()
                val list = snapshot.documents.mapNotNull { doc ->
                    val lastActive = doc.getLong("lastActive") ?: 0L
                    if (lastActive >= cutoff) {
                        val userCreatedAt = doc.getLong("userCreatedAt") ?: doc.getLong("accountCreatedAt") ?: 0L
                        val rawRole = doc.getString("userRole")
                        val userRole = UserRole.entries.firstOrNull { it.name == rawRole } ?: UserRole.USER
                        com.kaynanamtv.domain.model.OnlineUserInfo(
                            senderId = formatShortUserId(doc.getString("senderId") ?: doc.id),
                            senderName = doc.getString("senderName") ?: "Anonim",
                            userEmail = doc.getString("userEmail") ?: "E-posta Yok",
                            userRole = userRole,
                            userCreatedAt = userCreatedAt,
                            lastActive = lastActive
                        )
                    } else null
                }
                emit(list)
            } catch (e: Exception) {
                Log.w(TAG, "Error observing online users info: ${e.message}")
                emit(emptyList())
            }
            delay(30_000L)
        }
    }

    override fun getKnownChatPartners(): Flow<List<Pair<String, String>>> = callbackFlow {
        val firestore = runCatching { FirebaseFirestore.getInstance() }.getOrNull()
        val myId = getDeviceSenderId()
        if (firestore == null) { trySend(emptyList()); close(); return@callbackFlow }

        val listener = firestore.collection("private_chats")
            .whereArrayContains("participants", myId)
            .addSnapshotListener { snapshot, _ ->
                val partners = snapshot?.documents?.mapNotNull { doc ->
                    @Suppress("UNCHECKED_CAST")
                    val participants = doc.get("participants") as? List<String> ?: return@mapNotNull null
                    val otherId = participants.firstOrNull { it != myId } ?: return@mapNotNull null
                    val otherName = doc.getString("partnerName_$otherId") ?: "Bilinmiyor"
                    otherId to otherName
                } ?: emptyList()
                trySend(partners)
            }
        awaitClose { listener.remove() }
    }

    private suspend fun fetchOnlineUsersOnce(): List<String> {
        val firestore = runCatching { FirebaseFirestore.getInstance() }.getOrNull()
            ?: return listOf(getNickname())
        return try {
            val cutoff = System.currentTimeMillis() - ACTIVE_THRESHOLD_MS
            val snapshot = firestore.collection("active_users").get().await()
            snapshot.documents.mapNotNull { doc ->
                val lastActive = doc.getLong("lastActive") ?: 0L
                if (lastActive >= cutoff) doc.getString("senderName") ?: "Anonim" else null
            }.distinct().ifEmpty { listOf(getNickname()) }
        } catch (e: Exception) {
            Log.w(TAG, "Error fetching online presence: ${e.message}")
            listOf(getNickname())
        }
    }

    override fun observeBannedStatus(): Flow<Boolean> = callbackFlow {
        val firestore = runCatching { FirebaseFirestore.getInstance() }.getOrNull()
        val deviceId = getDeviceSenderId()
        if (firestore == null) { trySend(false); close(); return@callbackFlow }

        val listener = firestore.collection("banned_users")
            .document(deviceId)
            .addSnapshotListener { snapshot, _ ->
                if (snapshot?.exists() == true) {
                    val bannedUntil = snapshot.getLong("bannedUntil")
                    if (bannedUntil != null && bannedUntil > 0) {
                        // Timed ban
                        val stillBanned = System.currentTimeMillis() < bannedUntil
                        trySend(stillBanned)
                    } else {
                        // Permanent ban
                        trySend(true)
                    }
                } else {
                    trySend(false)
                }
            }
        awaitClose { listener.remove() }
    }

    override fun observeMessages(roomId: String): Flow<List<ChatMessage>> = callbackFlow {
        val firestore = runCatching { FirebaseFirestore.getInstance() }.getOrNull()
        if (firestore == null) { trySend(emptyList()); close(); return@callbackFlow }

        val listener = firestore.collection("chat_rooms")
            .document(roomId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .limitToLast(150)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.w(TAG, "Error observing messages: ${error.message}")
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val messages = snapshot.documents.mapNotNull { doc ->
                        val isDeleted = doc.getBoolean("isDeleted") ?: false
                        if (isDeleted) return@mapNotNull null

                        val text = doc.getString("message") ?: return@mapNotNull null
                        val senderId = doc.getString("senderId") ?: ""
                        val senderName = doc.getString("senderName") ?: "Anonim"
                        val timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis()
                        val color = doc.getString("avatarColorHex") ?: getAvatarColorHex(senderId)
                        val rawRole = doc.getString("userRole")
                        val role = UserRole.entries.firstOrNull { it.name == rawRole } ?: UserRole.USER
                        val imageUrl = doc.getString("imageUrl")
                        val replyToId = doc.getString("replyToId")
                        val replyToSender = doc.getString("replyToSender")
                        val replyToText = doc.getString("replyToText")
                        val userBadge = doc.getString("userBadge") 
                            ?: getUserBadge(senderId) 
                            ?: if (role == UserRole.ADMIN) "👑 Kurucu" else "⚡ Aktif Üye"
                        @Suppress("UNCHECKED_CAST")
                        val rawReactions = doc.get("reactions") as? Map<String, Long> ?: emptyMap()
                        val reactions = rawReactions.mapValues { it.value.toInt() }
                        @Suppress("UNCHECKED_CAST")
                        val mentions = doc.get("mentions") as? List<String> ?: emptyList()
                        @Suppress("UNCHECKED_CAST")
                        val seenBy = doc.get("seenBy") as? List<String> ?: emptyList()
                        val isEdited = doc.getBoolean("isEdited") ?: false
                        val editedAt = doc.getLong("editedAt")
                        val userCreatedAt = doc.getLong("userCreatedAt") 
                            ?: doc.getLong("senderCreatedAt") 
                            ?: doc.getLong("accountCreatedAt") 
                            ?: 0L

                        ChatMessage(
                            id = doc.id,
                            roomId = roomId,
                            senderId = senderId,
                            senderName = senderName,
                            message = text,
                            timestamp = timestamp,
                            avatarColorHex = color,
                            userRole = role,
                            isDeleted = false,
                            imageUrl = imageUrl,
                            replyToId = replyToId,
                            replyToSender = replyToSender,
                            replyToText = replyToText,
                            userBadge = userBadge,
                            reactions = reactions,
                            mentions = mentions,
                            seenBy = seenBy,
                            isEdited = isEdited,
                            editedAt = editedAt,
                            userCreatedAt = userCreatedAt
                        )
                    }
                    trySend(messages)
                }
            }
        awaitClose { listener.remove() }
    }

    override suspend fun sendMessage(
        roomId: String,
        text: String,
        imageUrl: String?,
        replyToMessage: ChatMessage?,
        mentions: List<String>
    ): Result<Unit> {
        val raw = text.trim()
        if (raw.isBlank() && imageUrl.isNullOrBlank()) return Result.success(Unit)

        val firestore = runCatching { FirebaseFirestore.getInstance() }.getOrNull()
            ?: return Result.failure(IllegalStateException("Firebase Firestore sunucusuna erişilemedi"))

        val senderId = getDeviceSenderId()
        val isBannedDoc = runCatching {
            firestore.collection("banned_users").document(senderId).get().await()
        }.getOrNull()

        if (isBannedDoc?.exists() == true) {
            val bannedUntil = isBannedDoc.getLong("bannedUntil")
            if (bannedUntil == null || bannedUntil <= 0 || System.currentTimeMillis() < bannedUntil) {
                return Result.failure(IllegalStateException("Sohbetten engellendiniz. Mesaj gönderemezsiniz."))
            }
        }

        if (roomId == ChatRoom.ANNOUNCEMENTS_ROOM.id && !isAdmin()) {
            return Result.failure(IllegalStateException("Duyurular kanalına sadece yöneticiler mesaj gönderebilir."))
        }

        val filteredText = filterProfanity(raw)
        val senderName = getNickname()
        val colorHex = getAvatarColorHex(senderId)
        val role = if (isAdmin()) UserRole.ADMIN else UserRole.USER
        val badge = resolveUserBadge(senderId)

        val msgData = mutableMapOf<String, Any?>(
            "roomId" to roomId,
            "senderId" to senderId,
            "senderName" to senderName,
            "userEmail" to getUserEmail(),
            "message" to filteredText,
            "timestamp" to System.currentTimeMillis(),
            "avatarColorHex" to colorHex,
            "userRole" to role.name,
            "isDeleted" to false,
            "imageUrl" to imageUrl,
            "userBadge" to badge,
            "mentions" to mentions,
            "isEdited" to false,
            "userCreatedAt" to getAccountCreatedAt(),
            "authUid" to FirebaseAuth.getInstance().currentUser?.uid
        )

        if (replyToMessage != null) {
            msgData["replyToId"] = replyToMessage.id
            msgData["replyToSender"] = replyToMessage.senderName
            msgData["replyToText"] = replyToMessage.message.take(60)
        }

        Log.d(TAG, """
            Firestore Send Message Payload:
            Collection Path: chat_rooms/$roomId/messages
            auth.currentUser.uid: ${FirebaseAuth.getInstance().currentUser?.uid}
            authUid: ${msgData["authUid"]}
            senderId: ${msgData["senderId"]}
            userRole: ${msgData["userRole"]} (Type: ${msgData["userRole"]?.javaClass?.name})
            userBadge: ${msgData["userBadge"]}
            isDeleted: ${msgData["isDeleted"]}
            message: ${msgData["message"]}
            userCreatedAt: ${msgData["userCreatedAt"]}
        """.trimIndent())

        return try {
            firestore.collection("chat_rooms").document(roomId).collection("messages").add(msgData).await()
            updatePresence()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "Error sending message: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun editMessage(roomId: String, messageId: String, newText: String): Result<Unit> {
        val firestore = runCatching { FirebaseFirestore.getInstance() }.getOrNull()
            ?: return Result.failure(IllegalStateException("Firestore erişilemedi"))

        val msgRef = firestore.collection("chat_rooms").document(roomId).collection("messages").document(messageId)

        return try {
            val doc = msgRef.get().await()
            val senderId = doc.getString("senderId") ?: ""
            val myId = getDeviceSenderId()

            if (senderId != myId && !isAdmin()) {
                return Result.failure(IllegalStateException("Yalnızca kendi mesajınızı düzenleyebilirsiniz."))
            }

            val ts = doc.getLong("timestamp") ?: 0L
            val now = System.currentTimeMillis()
            if (!isAdmin() && now - ts > EDIT_WINDOW_MS) {
                return Result.failure(IllegalStateException("Mesaj düzenleme süresi (5 dakika) dolmuştur."))
            }

            val filtered = filterProfanity(newText.trim())
            msgRef.update(mapOf("message" to filtered, "isEdited" to true, "editedAt" to now)).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error editing message: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun markMessageSeen(roomId: String, messageId: String): Result<Unit> {
        val firestore = runCatching { FirebaseFirestore.getInstance() }.getOrNull()
            ?: return Result.failure(IllegalStateException("Firestore erişilemedi"))
        val myId = getDeviceSenderId()
        return try {
            val msgRef = firestore.collection("chat_rooms").document(roomId).collection("messages").document(messageId)
            firestore.runTransaction { transaction ->
                val snap = transaction.get(msgRef)
                @Suppress("UNCHECKED_CAST")
                val current = (snap.get("seenBy") as? List<String> ?: emptyList()).toMutableList()
                if (!current.contains(myId)) {
                    current.add(myId)
                    transaction.update(msgRef, "seenBy", current)
                }
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun reportMessage(
        roomId: String,
        messageId: String,
        senderName: String,
        reason: String
    ): Result<Unit> {
        val firestore = runCatching { FirebaseFirestore.getInstance() }.getOrNull()
            ?: return Result.failure(IllegalStateException("Firestore erişilemedi"))
        val myId = getDeviceSenderId()
        val myName = getNickname()

        return try {
            val msgSnap = runCatching {
                firestore.collection("chat_rooms").document(roomId).collection("messages").document(messageId).get().await()
            }.getOrNull()

            val targetSenderId = formatShortUserId(msgSnap?.getString("senderId") ?: "")
            val targetEmail = msgSnap?.getString("userEmail") ?: ""
            val msgText = msgSnap?.getString("message") ?: ""

            val reportData = hashMapOf(
                "roomId" to roomId,
                "messageId" to messageId,
                "senderName" to senderName,
                "senderId" to targetSenderId,
                "userEmail" to targetEmail,
                "messageText" to msgText,
                "reportedBy" to myId,
                "reportedByName" to myName,
                "reason" to reason,
                "timestamp" to System.currentTimeMillis(),
                "resolved" to false,
                "reporterUid" to FirebaseAuth.getInstance().currentUser?.uid
            )
            firestore.collection("reports").add(reportData).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error reporting message: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun toggleReaction(roomId: String, messageId: String, emoji: String): Result<Unit> {
        val firestore = runCatching { FirebaseFirestore.getInstance() }.getOrNull()
            ?: return Result.failure(IllegalStateException("Firestore sunucusuna erişilemedi"))
        return try {
            val msgRef = firestore.collection("chat_rooms").document(roomId).collection("messages").document(messageId)
            firestore.runTransaction { transaction ->
                val snapshot = transaction.get(msgRef)
                @Suppress("UNCHECKED_CAST")
                val rawReactions = (snapshot.get("reactions") as? Map<String, Long>)?.toMutableMap() ?: mutableMapOf()
                val count = rawReactions[emoji] ?: 0L
                rawReactions[emoji] = count + 1L
                transaction.update(msgRef, "reactions", rawReactions)
            }.await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.w(TAG, "Error toggling reaction: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun setUserBadge(targetSenderId: String, badge: String?): Result<Unit> {
        if (!isAdmin()) return Result.failure(IllegalStateException("Bu işlem için yönetici yetkisi gereklidir."))
        if (badge.isNullOrBlank()) {
            prefs.edit().remove("badge_$targetSenderId").apply()
        } else {
            prefs.edit().putString("badge_$targetSenderId", badge).apply()
        }
        val firestore = runCatching { FirebaseFirestore.getInstance() }.getOrNull()
        if (firestore != null) {
            runCatching {
                firestore.collection("user_badges")
                    .document(targetSenderId)
                    .set(mapOf("badge" to badge, "updatedAt" to System.currentTimeMillis()))
                    .await()
            }
        }
        return Result.success(Unit)
    }

    private suspend fun resolveUserBadge(senderId: String): String {
        val customBadge = getUserBadge(senderId)
        if (!customBadge.isNullOrBlank()) return customBadge

        val auth = FirebaseAuth.getInstance()
        val user = auth.currentUser
        if (user != null) {
            if (user.email == "kilicemre3437@gmail.com" || isAdmin()) {
                return "👑 Kurucu"
            }
            val firestore = FirebaseFirestore.getInstance()
            val userDoc = runCatching { firestore.collection("users").document(user.uid).get().await() }.getOrNull()
            if (userDoc != null && userDoc.exists()) {
                val plan = userDoc.getString("premiumPlan")
                val isPremium = userDoc.getBoolean("isPremium") ?: false
                val trialExpiresAt = userDoc.getLong("trialExpiresAt") ?: 0L
                val now = System.currentTimeMillis()

                if (plan == "LIFETIME" || (isPremium && plan == "LIFETIME")) {
                    return "👑 Sınırsız VIP"
                } else if (plan == "YEARLY" && isPremium) {
                    val exp = userDoc.getLong("premiumExpiresAt") ?: 0L
                    if (exp == 0L || exp > now) {
                        return "💎 Yıllık Premium"
                    }
                } else if (trialExpiresAt > now) {
                    return "⏳ Deneme Üyesi"
                }
            }
        }
        return if (isAdmin()) "👑 Kurucu" else "⚡ Aktif Üye"
    }

    override fun getUserBadge(senderId: String): String? {
        val local = prefs.getString("badge_$senderId", null)
        if (!local.isNullOrBlank()) return local
        val user = runCatching { FirebaseAuth.getInstance().currentUser }.getOrNull()
        if (senderId == getDeviceSenderId() && (isAdmin() || user?.email == "kilicemre3437@gmail.com")) return "👑 Kurucu"
        return null
    }

    override suspend fun deleteMessage(roomId: String, messageId: String): Result<Unit> {
        if (!isAdmin()) return Result.failure(IllegalStateException("Yalnızca admin mesaj silebilir."))
        val firestore = runCatching { FirebaseFirestore.getInstance() }.getOrNull()
            ?: return Result.failure(IllegalStateException("Firestore erişilemedi"))
        return try {
            firestore.collection("chat_rooms").document(roomId).collection("messages").document(messageId)
                .update("isDeleted", true).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to delete message: ${e.message}", e)
            Result.failure(e)
        }
    }

    override suspend fun banUser(senderId: String, durationHours: Int): Result<Unit> {
        if (!isAdmin()) return Result.failure(IllegalStateException("Yalnızca admin kullanıcı banlayabilir."))
        val firestore = runCatching { FirebaseFirestore.getInstance() }.getOrNull()
            ?: return Result.failure(IllegalStateException("Firestore erişilemedi"))

        val bannedUntil: Long = if (durationHours <= 0) {
            -1L // Permanent
        } else {
            System.currentTimeMillis() + durationHours * 60 * 60 * 1000L
        }

        val banData = hashMapOf(
            "bannedId" to senderId,
            "bannedAt" to System.currentTimeMillis(),
            "bannedUntil" to bannedUntil,
            "durationHours" to durationHours
        )

        return try {
            firestore.collection("banned_users").document(senderId).set(banData).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to ban user: ${e.message}", e)
            Result.failure(e)
        }
    }

    // ── Private Chat (DM) ──────────────────────────────────────────────────────

    private fun dmChatId(userA: String, userB: String): String {
        val sorted = listOf(userA, userB).sorted()
        return "${sorted[0]}_${sorted[1]}"
    }

    override fun observePrivateMessages(otherUserId: String): Flow<List<PrivateChatMessage>> = callbackFlow {
        val firestore = runCatching { FirebaseFirestore.getInstance() }.getOrNull()
        val myId = getDeviceSenderId()
        if (firestore == null) { trySend(emptyList()); close(); return@callbackFlow }

        val chatId = dmChatId(myId, otherUserId)
        val listener = firestore.collection("private_chats")
            .document(chatId)
            .collection("messages")
            .orderBy("timestamp", Query.Direction.ASCENDING)
            .limitToLast(100)
            .addSnapshotListener { snapshot, error ->
                if (error != null) { Log.w(TAG, "DM observe error: ${error.message}"); return@addSnapshotListener }
                val msgs = snapshot?.documents?.mapNotNull { doc ->
                    PrivateChatMessage(
                        id = doc.id,
                        senderId = doc.getString("senderId") ?: "",
                        senderName = doc.getString("senderName") ?: "Anonim",
                        receiverId = doc.getString("receiverId") ?: "",
                        message = doc.getString("message") ?: "",
                        timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis(),
                        isRead = doc.getBoolean("isRead") ?: false,
                        imageUrl = doc.getString("imageUrl")
                    )
                } ?: emptyList()
                trySend(msgs)
            }
        awaitClose { listener.remove() }
    }

    override suspend fun sendPrivateMessage(toUserId: String, toUserName: String, text: String, imageUrl: String?): Result<Unit> {
        val firestore = runCatching { FirebaseFirestore.getInstance() }.getOrNull()
            ?: return Result.failure(IllegalStateException("Firestore erişilemedi"))
        val myId = getDeviceSenderId()
        val myName = getNickname()
        val chatId = dmChatId(myId, toUserId)
        val filtered = filterProfanity(text.trim())

        return try {
            val chatRef = firestore.collection("private_chats").document(chatId)
            // Ensure chat doc exists with participants
            chatRef.set(
                mapOf(
                    "participants" to listOf(myId, toUserId),
                    "partnerName_$myId" to myName,
                    "partnerName_$toUserId" to toUserName,
                    "lastMessageAt" to System.currentTimeMillis()
                ),
                com.google.firebase.firestore.SetOptions.merge()
            ).await()

            chatRef.collection("messages").add(
                mapOf(
                    "senderId" to myId,
                    "senderName" to myName,
                    "receiverId" to toUserId,
                    "message" to filtered,
                    "imageUrl" to imageUrl,
                    "timestamp" to System.currentTimeMillis(),
                    "isRead" to false,
                    "authUid" to FirebaseAuth.getInstance().currentUser?.uid
                )
            ).await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Error sending DM: ${e.message}")
            Result.failure(e)
        }
    }

    override suspend fun markPrivateMessagesRead(otherUserId: String) {
        val firestore = runCatching { FirebaseFirestore.getInstance() }.getOrNull() ?: return
        val myId = getDeviceSenderId()
        val chatId = dmChatId(myId, otherUserId)
        try {
            val unreadDocs = firestore.collection("private_chats")
                .document(chatId)
                .collection("messages")
                .whereEqualTo("receiverId", myId)
                .whereEqualTo("isRead", false)
                .get().await()
            val batch = firestore.batch()
            unreadDocs.documents.forEach { batch.update(it.reference, "isRead", true) }
            batch.commit().await()
        } catch (e: Exception) {
            Log.w(TAG, "Error marking DM read: ${e.message}")
        }
    }

    // ── Admin Dashboard ────────────────────────────────────────────────────────

    override suspend fun unbanUser(senderId: String): Result<Unit> {
        val firestore = runCatching { FirebaseFirestore.getInstance() }.getOrNull()
            ?: return Result.failure(IllegalStateException("Firestore erişilemedi"))
        return try {
            firestore.collection("banned_users").document(senderId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to unban user: ${e.message}", e)
            Result.failure(e)
        }
    }

    override fun observeAllReports(): Flow<List<ChatReport>> = callbackFlow {
        val firestore = runCatching { FirebaseFirestore.getInstance() }.getOrNull()
        if (firestore == null) { trySend(emptyList()); close(); return@callbackFlow }

        val listener = firestore.collection("reports")
            .orderBy("timestamp", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error observing reports: ${error.message}")
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val reports = snapshot.documents.mapNotNull { doc ->
                        val senderName = doc.getString("senderName")
                            ?: doc.getString("reportedSenderName")
                            ?: doc.getString("reportedByName")
                            ?: "Kullanıcı"
                        val rawId = doc.getString("senderId")
                            ?: doc.getString("reportedBy")
                            ?: doc.getString("reporterId")
                            ?: doc.id
                        val senderId = formatShortUserId(rawId)
                        val msgText = doc.getString("messageText")
                            ?: doc.getString("message")
                            ?: doc.getString("text")
                            ?: "Şikayet Edilen Mesaj İçeriği"
                        ChatReport(
                            id = doc.id,
                            roomId = doc.getString("roomId") ?: "genel",
                            messageId = doc.getString("messageId") ?: "",
                            senderName = senderName,
                            senderId = senderId,
                            userEmail = doc.getString("userEmail") ?: "",
                            reporterId = doc.getString("reporterId") ?: doc.getString("reportedBy") ?: "",
                            reason = doc.getString("reason") ?: "Uygunsuz İçerik",
                            timestamp = doc.getLong("timestamp") ?: System.currentTimeMillis(),
                            messageText = msgText
                        )
                    }
                    trySend(reports)
                }
            }
        awaitClose { listener.remove() }
    }

    override suspend fun dismissReport(reportId: String): Result<Unit> {
        val firestore = runCatching { FirebaseFirestore.getInstance() }.getOrNull()
            ?: return Result.failure(IllegalStateException("Firestore erişilemedi"))
        return try {
            firestore.collection("reports").document(reportId).delete().await()
            Result.success(Unit)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to dismiss report: ${e.message}", e)
            Result.failure(e)
        }
    }

    override fun observeAllBannedUsers(): Flow<List<BannedUserInfo>> = callbackFlow {
        val firestore = runCatching { FirebaseFirestore.getInstance() }.getOrNull()
        if (firestore == null) { trySend(emptyList()); close(); return@callbackFlow }

        val listener = firestore.collection("banned_users")
            .orderBy("bannedAt", Query.Direction.DESCENDING)
            .addSnapshotListener { snapshot, error ->
                if (error != null) {
                    Log.e(TAG, "Error observing banned users: ${error.message}")
                    trySend(emptyList())
                    return@addSnapshotListener
                }
                if (snapshot != null) {
                    val bannedList = snapshot.documents.mapNotNull { doc ->
                        val rawBannedId = doc.getString("bannedId") ?: doc.id
                        val senderName = doc.getString("senderName") ?: "Engellenen Kullanıcı"
                        val userEmail = doc.getString("userEmail") ?: ""
                        val bannedAt = doc.getLong("bannedAt") ?: System.currentTimeMillis()
                        val bannedUntil = doc.getLong("bannedUntil") ?: -1L
                        val durationHours = doc.getLong("durationHours")?.toInt() ?: -1
                        BannedUserInfo(
                            senderId = formatShortUserId(rawBannedId),
                            senderName = senderName,
                            userEmail = userEmail,
                            bannedAt = bannedAt,
                            bannedUntil = bannedUntil,
                            durationHours = durationHours
                        )
                    }
                    trySend(bannedList)
                }
            }
        awaitClose { listener.remove() }
    }
}

