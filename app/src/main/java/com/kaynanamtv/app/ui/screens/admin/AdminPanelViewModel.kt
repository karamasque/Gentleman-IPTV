package com.kaynanamtv.app.ui.screens.admin

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.kaynanamtv.data.repository.CommunityChatRepository
import com.kaynanamtv.domain.model.BannedUserInfo
import com.kaynanamtv.domain.model.ChatReport
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

import com.kaynanamtv.domain.model.OnlineUserInfo

import kotlinx.coroutines.flow.combine

@HiltViewModel
class AdminPanelViewModel @Inject constructor(
    private val chatRepository: CommunityChatRepository
) : ViewModel() {

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    val reports: StateFlow<List<ChatReport>> = combine(
        chatRepository.observeAllReports(),
        _searchQuery
    ) { list, query ->
        if (query.isBlank()) list
        else {
            val q = query.trim().lowercase()
            list.filter {
                it.senderName.lowercase().contains(q) ||
                it.senderId.lowercase().contains(q) ||
                it.userEmail.lowercase().contains(q) ||
                it.messageText.lowercase().contains(q) ||
                it.reason.lowercase().contains(q)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val bannedUsers: StateFlow<List<BannedUserInfo>> = combine(
        chatRepository.observeAllBannedUsers(),
        _searchQuery
    ) { list, query ->
        if (query.isBlank()) list
        else {
            val q = query.trim().lowercase()
            list.filter {
                it.senderName.lowercase().contains(q) ||
                it.senderId.lowercase().contains(q) ||
                it.userEmail.lowercase().contains(q)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val onlineUsers: StateFlow<List<String>> = chatRepository.observeOnlineUsers()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val onlineUsersInfo: StateFlow<List<OnlineUserInfo>> = combine(
        chatRepository.observeOnlineUsersInfo(),
        _searchQuery
    ) { list, query ->
        if (query.isBlank()) list
        else {
            val q = query.trim().lowercase()
            list.filter {
                it.senderName.lowercase().contains(q) ||
                it.senderId.lowercase().contains(q) ||
                it.userEmail.lowercase().contains(q)
            }
        }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val _adminMessage = MutableStateFlow<String?>(null)
    val adminMessage: StateFlow<String?> = _adminMessage.asStateFlow()

    fun dismissReport(reportId: String) {
        viewModelScope.launch {
            val res = chatRepository.dismissReport(reportId)
            if (res.isSuccess) {
                _adminMessage.value = "Şikayet kapatıldı."
            }
        }
    }

    fun deleteReportedMessage(roomId: String, messageId: String, reportId: String) {
        viewModelScope.launch {
            chatRepository.deleteMessage(roomId, messageId)
            chatRepository.dismissReport(reportId)
            _adminMessage.value = "Mesaj silindi ve şikayet kaldırıldı."
        }
    }

    fun banUserFromReport(targetSenderId: String, hours: Int, reportId: String) {
        viewModelScope.launch {
            chatRepository.banUser(targetSenderId, hours)
            chatRepository.dismissReport(reportId)
            _adminMessage.value = "Kullanıcı engellendi."
        }
    }

    fun banUser(targetSenderId: String, hours: Int) {
        viewModelScope.launch {
            chatRepository.banUser(targetSenderId, hours)
            _adminMessage.value = "Kullanıcı engellendi."
        }
    }

    fun unbanUser(targetSenderId: String) {
        viewModelScope.launch {
            val res = chatRepository.unbanUser(targetSenderId)
            if (res.isSuccess) {
                _adminMessage.value = "Kullanıcının engeli kaldırıldı."
            }
        }
    }

    fun assignBadge(targetSenderId: String, badge: String?) {
        viewModelScope.launch {
            chatRepository.setUserBadge(targetSenderId, badge)
            _adminMessage.value = "Rozet güncellendi."
        }
    }

    fun postAnnouncement(text: String) {
        if (text.isBlank()) return
        viewModelScope.launch {
            val res = chatRepository.sendMessage("duyurular", text)
            if (res.isSuccess) {
                _adminMessage.value = "Duyuru başarıyla yayınlandı!"
            } else {
                _adminMessage.value = res.exceptionOrNull()?.message ?: "Duyuru yayınlanamadı."
            }
        }
    }

    fun clearAdminMessage() {
        _adminMessage.value = null
    }
}
