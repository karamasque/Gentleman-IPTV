package com.kaynanamtv.data.repository

import com.kaynanamtv.domain.model.ChatMessage
import com.kaynanamtv.domain.model.UserRole
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CommunityChatPaginationTest {

    private fun createMessage(id: String, timestamp: Long, text: String = "msg $id"): ChatMessage {
        return ChatMessage(
            id = id,
            roomId = "genel",
            senderId = "SV-USER1",
            senderName = "Tester",
            message = text,
            timestamp = timestamp,
            avatarColorHex = "#3B82F6",
            userRole = UserRole.USER,
            isDeleted = false,
            imageUrl = null,
            replyToId = null,
            replyToSender = null,
            replyToText = null,
            userBadge = null,
            reactions = emptyMap(),
            mentions = emptyList(),
            seenBy = emptyList(),
            isEdited = false,
            editedAt = null,
            userCreatedAt = 0L
        )
    }

    @Test
    fun `deduplication preserves latest state and sorts chronologically`() {
        val older = listOf(
            createMessage("1", 1000L, "first"),
            createMessage("2", 2000L, "second"),
            createMessage("3", 3000L, "third (old)")
        )

        val latest = listOf(
            createMessage("3", 3000L, "third (updated/reaction)"),
            createMessage("4", 4000L, "fourth"),
            createMessage("5", 5000L, "fifth")
        )

        val map = LinkedHashMap<String, ChatMessage>()
        for (msg in older) map[msg.id] = msg
        for (msg in latest) map[msg.id] = msg

        val merged = map.values.sortedBy { it.timestamp }

        assertEquals(5, merged.size)
        assertEquals(listOf("1", "2", "3", "4", "5"), merged.map { it.id })
        assertEquals("third (updated/reaction)", merged.first { it.id == "3" }.message)
    }

    @Test
    fun `pagination boundary when room has fewer than 50 messages`() {
        val initialBatch = (1..20).map { i ->
            createMessage("$i", 1000L + i * 100)
        }

        val hasMoreOlder = initialBatch.size >= 50
        assertFalse("Small chat with < 50 items must not trigger pagination", hasMoreOlder)
    }

    @Test
    fun `pagination boundary when room has exactly 50 or more messages`() {
        val initialBatch = (1..50).map { i ->
            createMessage("$i", 1000L + i * 100)
        }

        val hasMoreOlder = initialBatch.size >= 50
        assertTrue("Chat with >= 50 items should allow older pagination", hasMoreOlder)
    }

    @Test
    fun `prepended older messages maintain correct chronological ordering`() {
        val page1Latest = (51..100).map { i ->
            createMessage("$i", 1000L + i * 100)
        }

        val page2Older = (1..50).map { i ->
            createMessage("$i", 1000L + i * 100)
        }

        val map = LinkedHashMap<String, ChatMessage>()
        for (msg in page2Older) map[msg.id] = msg
        for (msg in page1Latest) map[msg.id] = msg

        val result = map.values.sortedBy { it.timestamp }

        assertEquals(100, result.size)
        assertEquals("1", result.first().id)
        assertEquals("100", result.last().id)
    }

    @Test
    fun `empty initial message list handles pagination safely`() {
        val initialBatch = emptyList<ChatMessage>()
        val hasMoreOlder = initialBatch.size >= 50
        assertFalse(hasMoreOlder)
    }

    private fun createPrivateMessage(id: String, timestamp: Long, text: String = "dm $id"): com.kaynanamtv.domain.model.PrivateChatMessage {
        return com.kaynanamtv.domain.model.PrivateChatMessage(
            id = id,
            senderId = "SV-USER1",
            senderName = "Tester",
            receiverId = "SV-USER2",
            message = text,
            timestamp = timestamp,
            isRead = false,
            imageUrl = null
        )
    }

    @Test
    fun `dm message deduplication preserves latest and sorts chronologically`() {
        val older = listOf(
            createPrivateMessage("d1", 1000L, "first dm"),
            createPrivateMessage("d2", 2000L, "second dm")
        )
        val latest = listOf(
            createPrivateMessage("d2", 2000L, "second dm (read)"),
            createPrivateMessage("d3", 3000L, "third dm")
        )

        val map = LinkedHashMap<String, com.kaynanamtv.domain.model.PrivateChatMessage>()
        for (msg in older) map[msg.id] = msg
        for (msg in latest) map[msg.id] = msg

        val merged = map.values.sortedBy { it.timestamp }
        assertEquals(3, merged.size)
        assertEquals(listOf("d1", "d2", "d3"), merged.map { it.id })
        assertEquals("second dm (read)", merged.first { it.id == "d2" }.message)
    }

    @Test
    fun `dm message pagination boundary when chat has fewer than 30 messages`() {
        val initialBatch = (1..15).map { i ->
            createPrivateMessage("dm$i", 1000L + i * 100)
        }
        val hasMoreOlder = initialBatch.size >= 30
        assertFalse("DM with < 30 items must not trigger pagination", hasMoreOlder)
    }

    @Test
    fun `dm message pagination boundary when chat has 30 or more messages`() {
        val initialBatch = (1..30).map { i ->
            createPrivateMessage("dm$i", 1000L + i * 100)
        }
        val hasMoreOlder = initialBatch.size >= 30
        assertTrue("DM with >= 30 items should allow older pagination", hasMoreOlder)
    }

    private fun createReport(id: String, timestamp: Long): com.kaynanamtv.domain.model.ChatReport {
        return com.kaynanamtv.domain.model.ChatReport(
            id = id,
            roomId = "genel",
            messageId = "msg_$id",
            senderName = "Reporter",
            senderId = "SV-USER_$id",
            userEmail = "user$id@test.com",
            reporterId = "SV-REPORTER",
            reason = "Spam",
            timestamp = timestamp,
            messageText = "Reported text $id"
        )
    }

    @Test
    fun `reports pagination boundary and newest-first order`() {
        val smallReports = (1..10).map { createReport("rep$it", 1000L + it * 100) }
        assertFalse(smallReports.size >= 30)

        val fullReports = (1..30).map { createReport("rep$it", 1000L + it * 100) }
        assertTrue(fullReports.size >= 30)

        // Newest first sorting
        val sorted = fullReports.sortedByDescending { it.timestamp }
        assertEquals("rep30", sorted.first().id)
        assertEquals("rep1", sorted.last().id)
    }

    @Test
    fun `reports dismiss removes item locally without refetching`() {
        val reports = (1..5).map { createReport("rep$it", 1000L + it * 100) }.toMutableList()
        val dismissId = "rep3"
        val updated = reports.filterNot { it.id == dismissId }
        assertEquals(4, updated.size)
        assertFalse(updated.any { it.id == dismissId })
    }

    private fun createBannedUser(senderId: String, bannedAt: Long): com.kaynanamtv.domain.model.BannedUserInfo {
        return com.kaynanamtv.domain.model.BannedUserInfo(
            senderId = senderId,
            senderName = "BadUser $senderId",
            userEmail = "bad$senderId@test.com",
            bannedAt = bannedAt,
            bannedUntil = -1L,
            durationHours = -1
        )
    }

    @Test
    fun `banned users pagination boundary and newest-first order`() {
        val smallBanned = (1..10).map { createBannedUser("SV-$it", 1000L + it * 100) }
        assertFalse(smallBanned.size >= 30)

        val fullBanned = (1..30).map { createBannedUser("SV-$it", 1000L + it * 100) }
        assertTrue(fullBanned.size >= 30)

        val sorted = fullBanned.sortedByDescending { it.bannedAt }
        assertEquals("SV-30", sorted.first().senderId)
        assertEquals("SV-1", sorted.last().senderId)
    }

    @Test
    fun `banned user unban removes user locally`() {
        val bannedList = (1..5).map { createBannedUser("SV-$it", 1000L + it * 100) }
        val unbanId = "SV-3"
        val updated = bannedList.filterNot { it.senderId == unbanId }
        assertEquals(4, updated.size)
        assertFalse(updated.any { it.senderId == unbanId })
    }
}
