package com.kaynanamtv.data.repository

import com.kaynanamtv.domain.model.BannedUserInfo
import com.kaynanamtv.domain.model.ChatMessage
import com.kaynanamtv.domain.model.ChatReport
import com.kaynanamtv.domain.model.ChatRoom
import com.kaynanamtv.domain.model.OnlineUserInfo
import com.kaynanamtv.domain.model.PrivateChatMessage
import kotlinx.coroutines.flow.Flow

interface CommunityChatRepository {
    fun getGeneralRoom(): ChatRoom
    fun getAnnouncementsRoom(): ChatRoom
    fun observeMessages(roomId: String = "genel"): Flow<List<ChatMessage>>
    suspend fun loadOlderMessages(roomId: String = "genel", beforeTimestamp: Long, limit: Long = 50L): List<ChatMessage>
    suspend fun sendMessage(
        roomId: String = "genel",
        text: String,
        imageUrl: String? = null,
        replyToMessage: ChatMessage? = null,
        mentions: List<String> = emptyList()
    ): Result<Unit>
    fun getNickname(): String
    fun hasCustomNickname(): Boolean
    suspend fun setNickname(nickname: String)
    fun getDeviceSenderId(): String
    fun getUserEmail(): String
    fun getAccountCreatedAt(): Long
    fun isAdmin(): Boolean
    fun verifyAdminPassword(password: String): Boolean
    suspend fun deleteMessage(roomId: String, messageId: String): Result<Unit>

    // Timed ban: durationHours = -1 means permanent
    suspend fun banUser(senderId: String, durationHours: Int = -1): Result<Unit>
    suspend fun unbanUser(senderId: String): Result<Unit>
    fun observeBannedStatus(): Flow<Boolean>
    suspend fun updatePresence()
    fun observeOnlineUsers(): Flow<List<String>>
    fun observeOnlineUsersInfo(): Flow<List<OnlineUserInfo>>
    suspend fun toggleReaction(roomId: String, messageId: String, emoji: String): Result<Unit>
    suspend fun setUserBadge(targetSenderId: String, badge: String?): Result<Unit>
    fun getUserBadge(senderId: String): String?

    // Message editing (within 5 minutes)
    suspend fun editMessage(roomId: String, messageId: String, newText: String): Result<Unit>

    // Seen tracking for announcements
    suspend fun markMessageSeen(roomId: String, messageId: String): Result<Unit>

    // Report messages
    suspend fun reportMessage(
        roomId: String,
        messageId: String,
        senderName: String,
        reason: String
    ): Result<Unit>

    // Private chat (DM)
    fun observePrivateMessages(otherUserId: String): Flow<List<PrivateChatMessage>>
    suspend fun loadOlderPrivateMessages(otherUserId: String, beforeTimestamp: Long, limit: Long = 30L): List<PrivateChatMessage>
    suspend fun sendPrivateMessage(toUserId: String, toUserName: String, text: String, imageUrl: String? = null): Result<Unit>
    suspend fun markPrivateMessagesRead(otherUserId: String)
    fun getKnownChatPartners(limit: Long = 20L): Flow<List<Pair<String, String>>> // userId to nickname

    // Admin Dashboard
    suspend fun loadReports(beforeTimestamp: Long? = null, limit: Long = 30L): List<ChatReport>
    suspend fun dismissReport(reportId: String): Result<Unit>
    suspend fun loadBannedUsers(beforeTimestamp: Long? = null, limit: Long = 30L): List<BannedUserInfo>
    fun observeAllReports(): Flow<List<ChatReport>> = kotlinx.coroutines.flow.flowOf(emptyList())
    fun observeAllBannedUsers(): Flow<List<BannedUserInfo>> = kotlinx.coroutines.flow.flowOf(emptyList())
}


