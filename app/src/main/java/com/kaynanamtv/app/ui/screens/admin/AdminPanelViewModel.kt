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

    private val _rawReports = MutableStateFlow<List<ChatReport>>(emptyList())
    private val _isLoadingReports = MutableStateFlow(false)
    val isLoadingReports: StateFlow<Boolean> = _isLoadingReports.asStateFlow()
    private val _hasMoreReports = MutableStateFlow(true)
    val hasMoreReports: StateFlow<Boolean> = _hasMoreReports.asStateFlow()

    private val _rawBannedUsers = MutableStateFlow<List<BannedUserInfo>>(emptyList())
    private val _isLoadingBannedUsers = MutableStateFlow(false)
    val isLoadingBannedUsers: StateFlow<Boolean> = _isLoadingBannedUsers.asStateFlow()
    private val _hasMoreBannedUsers = MutableStateFlow(true)
    val hasMoreBannedUsers: StateFlow<Boolean> = _hasMoreBannedUsers.asStateFlow()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    val reports: StateFlow<List<ChatReport>> = combine(
        _rawReports,
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
        _rawBannedUsers,
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

    init {
        loadInitialReports()
        loadInitialBannedUsers()
    }

    fun refresh() {
        loadInitialReports()
        loadInitialBannedUsers()
    }

    fun loadInitialReports() {
        viewModelScope.launch {
            _isLoadingReports.value = true
            _hasMoreReports.value = true
            val initial = chatRepository.loadReports(limit = 30L)
            _rawReports.value = initial
            _hasMoreReports.value = initial.size >= 30
            _isLoadingReports.value = false
        }
    }

    fun loadOlderReports() {
        if (_isLoadingReports.value || !_hasMoreReports.value) return
        val currentList = _rawReports.value
        if (currentList.isEmpty() || currentList.size < 30) {
            _hasMoreReports.value = false
            return
        }
        val oldestTimestamp = currentList.lastOrNull()?.timestamp ?: return

        viewModelScope.launch {
            _isLoadingReports.value = true
            val older = chatRepository.loadReports(beforeTimestamp = oldestTimestamp, limit = 30L)
            if (older.isEmpty()) {
                _hasMoreReports.value = false
            } else {
                val existingIds = currentList.map { it.id }.toSet()
                val newOlder = older.filter { it.id !in existingIds }
                if (newOlder.isEmpty()) {
                    _hasMoreReports.value = false
                } else {
                    _rawReports.value = currentList + newOlder
                    if (older.size < 30) {
                        _hasMoreReports.value = false
                    }
                }
            }
            _isLoadingReports.value = false
        }
    }

    fun loadInitialBannedUsers() {
        viewModelScope.launch {
            _isLoadingBannedUsers.value = true
            _hasMoreBannedUsers.value = true
            val initial = chatRepository.loadBannedUsers(limit = 30L)
            _rawBannedUsers.value = initial
            _hasMoreBannedUsers.value = initial.size >= 30
            _isLoadingBannedUsers.value = false
        }
    }

    fun loadOlderBannedUsers() {
        if (_isLoadingBannedUsers.value || !_hasMoreBannedUsers.value) return
        val currentList = _rawBannedUsers.value
        if (currentList.isEmpty() || currentList.size < 30) {
            _hasMoreBannedUsers.value = false
            return
        }
        val oldestTimestamp = currentList.lastOrNull()?.bannedAt ?: return

        viewModelScope.launch {
            _isLoadingBannedUsers.value = true
            val older = chatRepository.loadBannedUsers(beforeTimestamp = oldestTimestamp, limit = 30L)
            if (older.isEmpty()) {
                _hasMoreBannedUsers.value = false
            } else {
                val existingIds = currentList.map { it.senderId }.toSet()
                val newOlder = older.filter { it.senderId !in existingIds }
                if (newOlder.isEmpty()) {
                    _hasMoreBannedUsers.value = false
                } else {
                    _rawBannedUsers.value = currentList + newOlder
                    if (older.size < 30) {
                        _hasMoreBannedUsers.value = false
                    }
                }
            }
            _isLoadingBannedUsers.value = false
        }
    }

    fun dismissReport(reportId: String) {
        viewModelScope.launch {
            val res = chatRepository.dismissReport(reportId)
            if (res.isSuccess) {
                _rawReports.value = _rawReports.value.filterNot { it.id == reportId }
                _adminMessage.value = "Şikayet kapatıldı."
            }
        }
    }

    fun deleteReportedMessage(roomId: String, messageId: String, reportId: String) {
        viewModelScope.launch {
            chatRepository.deleteMessage(roomId, messageId)
            chatRepository.dismissReport(reportId)
            _rawReports.value = _rawReports.value.filterNot { it.id == reportId }
            _adminMessage.value = "Mesaj silindi ve şikayet kaldırıldı."
        }
    }

    fun banUserFromReport(targetSenderId: String, hours: Int, reportId: String) {
        viewModelScope.launch {
            chatRepository.banUser(targetSenderId, hours)
            chatRepository.dismissReport(reportId)
            _rawReports.value = _rawReports.value.filterNot { it.id == reportId }
            _adminMessage.value = "Kullanıcı engellendi."
            loadInitialBannedUsers()
        }
    }

    fun banUser(targetSenderId: String, hours: Int) {
        viewModelScope.launch {
            chatRepository.banUser(targetSenderId, hours)
            _adminMessage.value = "Kullanıcı engellendi."
            loadInitialBannedUsers()
        }
    }

    fun unbanUser(targetSenderId: String) {
        viewModelScope.launch {
            val res = chatRepository.unbanUser(targetSenderId)
            if (res.isSuccess) {
                _rawBannedUsers.value = _rawBannedUsers.value.filterNot {
                    it.senderId.equals(targetSenderId, ignoreCase = true) ||
                    it.senderId.replace("SV-", "").equals(targetSenderId.replace("SV-", ""), ignoreCase = true)
                }
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
