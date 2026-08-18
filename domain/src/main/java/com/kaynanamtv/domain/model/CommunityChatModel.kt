package com.kaynanamtv.domain.model

enum class UserRole {
    USER,
    MODERATOR,
    ADMIN,
    VIP
}

data class BadgeStyle(
    val title: String,
    val backgroundColorHex: String,
    val textColorHex: String = "#FFFFFF",
    val borderColorHex: String? = null
)

data class OnlineUserInfo(
    val senderId: String = "",
    val senderName: String = "",
    val userEmail: String = "",
    val userRole: UserRole = UserRole.USER,
    val userCreatedAt: Long = 0L,
    val lastActive: Long = System.currentTimeMillis()
)

data class ChatReport(
    val id: String = "",
    val roomId: String = "genel",
    val messageId: String = "",
    val senderName: String = "",
    val senderId: String = "",
    val userEmail: String = "",
    val reporterId: String = "",
    val reason: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val messageText: String = ""
)

data class BannedUserInfo(
    val senderId: String = "",
    val senderName: String = "",
    val userEmail: String = "",
    val bannedAt: Long = System.currentTimeMillis(),
    val bannedUntil: Long = -1L,
    val durationHours: Int = -1
)

data class ChatMessage(
    val id: String = "",
    val roomId: String = "genel",
    val senderId: String = "",
    val senderName: String = "",
    val message: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val avatarColorHex: String = "#3B82F6",
    val userRole: UserRole = UserRole.USER,
    val isDeleted: Boolean = false,
    val imageUrl: String? = null,
    val replyToId: String? = null,
    val replyToSender: String? = null,
    val replyToText: String? = null,
    val userBadge: String? = null,
    val reactions: Map<String, Int> = emptyMap(),
    val mentions: List<String> = emptyList(),
    val seenBy: List<String> = emptyList(),
    val isEdited: Boolean = false,
    val editedAt: Long? = null,
    val userCreatedAt: Long = 0L,
    val customBadge: String? = null
)

data class PrivateChatMessage(
    val id: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val receiverId: String = "",
    val recipientId: String = "",
    val recipientName: String = "",
    val message: String = "",
    val timestamp: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,
    val imageUrl: String? = null,
    val avatarColorHex: String = "#3B82F6"
)

data class ChatRoom(
    val id: String,
    val name: String,
    val description: String,
    val iconName: String = "Forum",
    val isReadOnlyForUsers: Boolean = false,
    val isPrivate: Boolean = false
) {
    companion object {
        val GENERAL_ROOM = ChatRoom(
            id = "genel",
            name = "Genel Sohbet",
            description = "Tüm toplulukla sohbet edin",
            iconName = "Forum"
        )
        val ANNOUNCEMENTS_ROOM = ChatRoom(
            id = "duyurular",
            name = "Duyurular",
            description = "Resmi güncellemeler ve duyurular",
            iconName = "Campaign",
            isReadOnlyForUsers = true
        )
        val SPORTS_ROOM = ChatRoom(
            id = "spor",
            name = "Spor & Maçlar",
            description = "Canlı maçlar ve spor sohbetleri",
            iconName = "Sports"
        )
        val MOVIES_SERIES_ROOM = ChatRoom(
            id = "sinema",
            name = "Film & Dizi Tavsiyeleri",
            description = "İçerik önerileri ve incelemeler",
            iconName = "Movie"
        )
        val HELP_SUPPORT_ROOM = ChatRoom(
            id = "destek",
            name = "Yardım & Destek",
            description = "Teknik sorunlar ve sorular",
            iconName = "Help"
        )

        val ROOMS = listOf(
            GENERAL_ROOM,
            ANNOUNCEMENTS_ROOM,
            SPORTS_ROOM,
            MOVIES_SERIES_ROOM,
            HELP_SUPPORT_ROOM
        )
    }
}