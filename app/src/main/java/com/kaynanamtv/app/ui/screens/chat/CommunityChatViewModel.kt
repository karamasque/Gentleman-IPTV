package com.kaynanamtv.app.ui.screens.chat

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kaynanamtv.data.repository.CommunityChatRepository
import com.kaynanamtv.domain.model.ChatMessage
import com.kaynanamtv.domain.model.ChatRoom
import com.kaynanamtv.domain.model.PrivateChatMessage
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CommunityChatViewModel @Inject constructor(
    private val chatRepository: CommunityChatRepository
) : ViewModel() {

    val generalRoom: ChatRoom = chatRepository.getGeneralRoom()

    private val _userNickname = MutableStateFlow(chatRepository.getNickname())
    val userNickname: StateFlow<String> = _userNickname.asStateFlow()

    private val _hasCustomNickname = MutableStateFlow(chatRepository.hasCustomNickname())
    val hasCustomNickname: StateFlow<Boolean> = _hasCustomNickname.asStateFlow()

    private val _isAdmin = MutableStateFlow(chatRepository.isAdmin())
    val isAdmin: StateFlow<Boolean> = _isAdmin.asStateFlow()

    val currentDeviceId: String = chatRepository.getDeviceSenderId()

    fun getUserBadge(senderId: String): String? = chatRepository.getUserBadge(senderId)
    fun getUserEmail(): String = chatRepository.getUserEmail()
    fun getAccountCreatedAt(): Long = chatRepository.getAccountCreatedAt()

    val isBanned: StateFlow<Boolean> = chatRepository.observeBannedStatus()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

    val onlineUsers: StateFlow<List<String>> = chatRepository.observeOnlineUsers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), listOf(chatRepository.getNickname()))

    val rooms: List<ChatRoom> = ChatRoom.ROOMS

    private val _selectedRoom = MutableStateFlow<ChatRoom>(ChatRoom.GENERAL_ROOM)
    val selectedRoom: StateFlow<ChatRoom> = _selectedRoom.asStateFlow()

    private val _replyingToMessage = MutableStateFlow<ChatMessage?>(null)
    val replyingToMessage: StateFlow<ChatMessage?> = _replyingToMessage.asStateFlow()

    private val _editingMessage = MutableStateFlow<ChatMessage?>(null)
    val editingMessage: StateFlow<ChatMessage?> = _editingMessage.asStateFlow()

    // Search
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _allMessages = MutableStateFlow<List<ChatMessage>>(emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val messages: StateFlow<List<ChatMessage>> = _selectedRoom
        .flatMapLatest { room -> chatRepository.observeMessages(room.id) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val filteredMessages: StateFlow<List<ChatMessage>> = combine(messages, _searchQuery) { msgs, query ->
        if (query.isBlank()) msgs
        else msgs.filter {
            it.message.contains(query, ignoreCase = true) ||
            it.senderName.contains(query, ignoreCase = true)
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _isSending = MutableStateFlow(false)
    val isSending: StateFlow<Boolean> = _isSending.asStateFlow()

    private val _userMessageError = MutableStateFlow<String?>(null)
    val userMessageError: StateFlow<String?> = _userMessageError.asStateFlow()

    // Spam koruması
    private var _lastMessageSentAt = 0L
    private val MESSAGE_COOLDOWN_MS = 1500L

    // DM
    private val _activeDmUserId = MutableStateFlow<String?>(null)
    private val _activeDmUserName = MutableStateFlow<String?>(null)
    val activeDmUserName: StateFlow<String?> = _activeDmUserName.asStateFlow()

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val privateMessages: StateFlow<List<PrivateChatMessage>> = _activeDmUserId
        .flatMapLatest { otherId ->
            if (otherId != null) chatRepository.observePrivateMessages(otherId)
            else kotlinx.coroutines.flow.flowOf(emptyList())
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _showDmScreen = MutableStateFlow(false)
    val showDmScreen: StateFlow<Boolean> = _showDmScreen.asStateFlow()

    val dmPartners: StateFlow<List<Pair<String, String>>> = chatRepository.getKnownChatPartners()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        viewModelScope.launch {
            while (isActive) {
                chatRepository.updatePresence()
                delay(40_000L)
            }
        }
        // Update all messages for search
        viewModelScope.launch {
            messages.collect { _allMessages.value = it }
        }
    }

    fun selectRoom(room: ChatRoom) { _selectedRoom.value = room }
    fun setReplyToMessage(message: ChatMessage?) { _replyingToMessage.value = message }
    fun setEditingMessage(message: ChatMessage?) { _editingMessage.value = message }
    fun setSearchQuery(query: String) { _searchQuery.value = query }

    fun openDm(userId: String, userName: String) {
        _activeDmUserId.value = userId
        _activeDmUserName.value = userName
        _showDmScreen.value = true
        viewModelScope.launch { chatRepository.markPrivateMessagesRead(userId) }
    }

    fun closeDm() {
        _showDmScreen.value = false
        _activeDmUserId.value = null
        _activeDmUserName.value = null
    }

    fun sendMessage(text: String, imageUrl: String? = null) {
        if (text.isBlank() && imageUrl.isNullOrBlank()) return
        if (isBanned.value) {
            _userMessageError.value = "Sohbetten engellendiniz. Mesaj gönderemezsiniz."
            return
        }
        val currentRoom = _selectedRoom.value
        if (currentRoom.isReadOnlyForUsers && !isAdmin.value) {
            _userMessageError.value = "Duyurular kanalına sadece yöneticiler mesaj gönderebilir."
            return
        }
        // Spam koruması — 1.5 saniyede bir mesaj
        val now = System.currentTimeMillis()
        if (now - _lastMessageSentAt < MESSAGE_COOLDOWN_MS) {
            _userMessageError.value = "Çok hızlı mesaj gönderiyorsunuz, lütfen bekleyin."
            return
        }
        _lastMessageSentAt = now
        val mentionRegex = Regex("@([\\w\\u00C0-\\u024F]+)")
        val mentions = mentionRegex.findAll(text).map { it.groupValues[1] }.toList()

        viewModelScope.launch {
            _isSending.value = true
            val replyTarget = _replyingToMessage.value
            val result = chatRepository.sendMessage(
                roomId = currentRoom.id,
                text = text,
                imageUrl = imageUrl,
                replyToMessage = replyTarget,
                mentions = mentions
            )
            _isSending.value = false
            if (result.isSuccess) {
                _replyingToMessage.value = null
            } else {
                _userMessageError.value = result.exceptionOrNull()?.message ?: "Mesaj gönderilemedi."
            }
        }
    }

    fun sendDmMessage(text: String, imageUrl: String? = null) {
        val toId = _activeDmUserId.value ?: return
        val toName = _activeDmUserName.value ?: return
        if (text.isBlank() && imageUrl.isNullOrBlank()) return
        viewModelScope.launch {
            val result = chatRepository.sendPrivateMessage(toId, toName, text, imageUrl)
            if (result.isFailure) {
                _userMessageError.value = result.exceptionOrNull()?.message ?: "Mesaj gönderilemedi."
            }
        }
    }

    fun editMessage(messageId: String, newText: String) {
        viewModelScope.launch {
            val result = chatRepository.editMessage(_selectedRoom.value.id, messageId, newText)
            if (result.isSuccess) {
                _editingMessage.value = null
            } else {
                _userMessageError.value = result.exceptionOrNull()?.message ?: "Mesaj düzenlenemedi."
            }
        }
    }

    fun markMessageSeen(messageId: String) {
        viewModelScope.launch {
            chatRepository.markMessageSeen(_selectedRoom.value.id, messageId)
        }
    }

    fun reportMessage(messageId: String, senderName: String, reason: String) {
        viewModelScope.launch {
            val result = chatRepository.reportMessage(_selectedRoom.value.id, messageId, senderName, reason)
            if (result.isSuccess) {
                _userMessageError.value = "Şikayetiniz alındı. Yöneticilerimiz inceleyecek."
            } else {
                _userMessageError.value = result.exceptionOrNull()?.message ?: "Şikayet gönderilemedi."
            }
        }
    }

    fun toggleReaction(messageId: String, emoji: String) {
        viewModelScope.launch {
            chatRepository.toggleReaction(_selectedRoom.value.id, messageId, emoji)
        }
    }

    fun assignUserBadge(targetSenderId: String, badge: String?) {
        viewModelScope.launch { chatRepository.setUserBadge(targetSenderId, badge) }
    }

    fun updateNickname(newNickname: String) {
        viewModelScope.launch {
            chatRepository.setNickname(newNickname)
            _userNickname.value = chatRepository.getNickname()
            _hasCustomNickname.value = chatRepository.hasCustomNickname()
            chatRepository.updatePresence()
        }
    }

    fun loginAdmin(password: String): Boolean {
        val success = chatRepository.verifyAdminPassword(password)
        if (success) {
            _isAdmin.value = true
            viewModelScope.launch { chatRepository.updatePresence() }
        }
        return success
    }

    fun deleteMessage(messageId: String) {
        viewModelScope.launch { chatRepository.deleteMessage(_selectedRoom.value.id, messageId) }
    }

    fun banUser(senderId: String, durationHours: Int = -1) {
        viewModelScope.launch { chatRepository.banUser(senderId, durationHours) }
    }

    fun clearError() { _userMessageError.value = null }
}
