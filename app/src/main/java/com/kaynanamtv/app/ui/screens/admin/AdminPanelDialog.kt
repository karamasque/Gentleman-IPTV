package com.kaynanamtv.app.ui.screens.admin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.kaynanamtv.app.ui.design.AppColors
import com.kaynanamtv.domain.model.BannedUserInfo
import com.kaynanamtv.domain.model.ChatReport
import com.kaynanamtv.domain.model.OnlineUserInfo
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun AdminPanelDialog(
    viewModel: AdminPanelViewModel,
    onDismiss: () -> Unit,
    onOpenDm: (userId: String, userName: String) -> Unit
) {
    val reports by viewModel.reports.collectAsStateWithLifecycle()
    val bannedUsers by viewModel.bannedUsers.collectAsStateWithLifecycle()
    val onlineUsersInfo by viewModel.onlineUsersInfo.collectAsStateWithLifecycle()
    val adminMessage by viewModel.adminMessage.collectAsStateWithLifecycle()

    var selectedTab by remember { mutableStateOf(0) }
    var announcementInput by remember { mutableStateOf("") }
    var showTimedBanForUser by remember { mutableStateOf<String?>(null) }
    val clipboardManager = LocalClipboardManager.current
    var copyToast by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(copyToast) {
        if (copyToast != null) {
            kotlinx.coroutines.delay(2000L)
            copyToast = null
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .fillMaxHeight(0.92f)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF0F172A))
                .border(1.5.dp, Color(0xFFFFD700), RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Shield, contentDescription = null, tint = Color(0xFFFFD700), modifier = Modifier.size(24.dp))
                        Text(text = "Yönetici Kontrol Paneli", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    var isCloseFocused by remember { mutableStateOf(false) }
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(32.dp)
                            .clip(CircleShape)
                            .background(if (isCloseFocused) Color(0xFF334155) else Color.Transparent)
                            .onFocusChanged { isCloseFocused = it.isFocused }
                    ) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Kapat", tint = Color.Gray, modifier = Modifier.size(20.dp))
                    }
                }

                // Copy Toast Notification
                copyToast?.let { toast ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF3B82F6))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Text(text = "📋 $toast", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Admin Message Banner
                adminMessage?.let { msg ->
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 8.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(Color(0xFF10B981).copy(alpha = 0.2f))
                            .border(1.dp, Color(0xFF10B981), RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(text = msg, color = Color(0xFFA7F3D0), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            IconButton(onClick = { viewModel.clearAdminMessage() }, modifier = Modifier.size(20.dp)) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }

                // Tabs Navigation
                Row(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val tabs = listOf(
                        "🚨 Şikayetler (${reports.size})",
                        "⛔ Engellenenler (${bannedUsers.size})",
                        "👥 Kullanıcılar (${onlineUsersInfo.size})",
                        "📢 Duyuru Yap"
                    )
                    tabs.forEachIndexed { index, title ->
                        val isSelected = selectedTab == index
                        var isTabFocused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .onFocusChanged { isTabFocused = it.isFocused }
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    if (isSelected) Color(0xFFFFD700)
                                    else if (isTabFocused) Color(0xFF334155)
                                    else Color(0xFF1E293B)
                                )
                                .clickable { selectedTab = index }
                                .padding(vertical = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = title,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = if (isSelected) Color.Black else Color.White
                            )
                        }
                    }
                }

                // Admin Panel Search Bar
                val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 10.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1E293B))
                        .border(1.dp, Color(0xFF334155), RoundedCornerShape(10.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Search, contentDescription = "Ara", tint = Color.LightGray, modifier = Modifier.size(16.dp))
                        BasicTextField(
                            value = searchQuery,
                            onValueChange = { viewModel.setSearchQuery(it) },
                            singleLine = true,
                            textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                            modifier = Modifier.weight(1f),
                            decorationBox = { innerTextField ->
                                if (searchQuery.isEmpty()) {
                                    Text("Rumuz, ID (SV-XXXXXX) veya e-posta ile kullanıcı/şikayet ara...", color = Color.Gray, fontSize = 12.sp)
                                }
                                innerTextField()
                            }
                        )
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { viewModel.setSearchQuery("") }, modifier = Modifier.size(20.dp)) {
                                Icon(imageVector = Icons.Default.Close, contentDescription = "Temizle", tint = Color.Gray, modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }

                // Tab Contents
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    when (selectedTab) {
                        0 -> ReportsTab(
                            reports = reports,
                            onDismiss = { viewModel.dismissReport(it) },
                            onDeleteMsg = { r, m, rep -> viewModel.deleteReportedMessage(r, m, rep) },
                            onBanUser = { showTimedBanForUser = it },
                            onOpenDm = { uId, uName -> onDismiss(); onOpenDm(uId, uName) },
                            onCopyId = { id ->
                                clipboardManager.setText(AnnotatedString(id))
                                copyToast = "Kullanıcı Kimliği Kopyalandı: $id"
                            }
                        )
                        1 -> BannedUsersTab(
                            bannedUsers = bannedUsers,
                            onUnban = { viewModel.unbanUser(it) },
                            onCopyId = { id ->
                                clipboardManager.setText(AnnotatedString(id))
                                copyToast = "Kullanıcı Kimliği Kopyalandı: $id"
                            }
                        )
                        2 -> UsersTab(
                            users = onlineUsersInfo,
                            onAssignBadge = { uId, badge -> viewModel.assignBadge(uId, badge) },
                            onBanUser = { showTimedBanForUser = it },
                            onOpenDm = { uId, uName -> onDismiss(); onOpenDm(uId, uName) },
                            onCopyId = { id ->
                                clipboardManager.setText(AnnotatedString(id))
                                copyToast = "Kullanıcı Kimliği Kopyalandı: $id"
                            }
                        )
                        3 -> AnnouncementTab(
                            input = announcementInput,
                            onInputChange = { announcementInput = it },
                            onPost = { viewModel.postAnnouncement(it); announcementInput = "" }
                        )
                    }
                }
            }
        }
    }

    // Timed Ban Dialog
    showTimedBanForUser?.let { targetId ->
        AlertDialog(
            onDismissRequest = { showTimedBanForUser = null },
            title = { Text("Kullanıcıyı Engelle", color = Color.White, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val options = listOf("1 Saat" to 1, "6 Saat" to 6, "24 Saat" to 24, "7 Gün" to 168, "Süresiz" to -1)
                    options.forEach { opt ->
                        var isOptFocused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { isOptFocused = it.isFocused }
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isOptFocused) Color(0xFFEF4444) else Color(0xFF334155))
                                .clickable {
                                    viewModel.banUser(targetId, opt.second)
                                    showTimedBanForUser = null
                                }
                                .padding(12.dp)
                        ) {
                            Text(text = opt.first, color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTimedBanForUser = null }) { Text("İptal", color = Color.Gray) }
            },
            containerColor = Color(0xFF1E293B)
        )
    }
}

@Composable
private fun ReportsTab(
    reports: List<ChatReport>,
    onDismiss: (String) -> Unit,
    onDeleteMsg: (String, String, String) -> Unit,
    onBanUser: (String) -> Unit,
    onOpenDm: (String, String) -> Unit,
    onCopyId: (String) -> Unit
) {
    if (reports.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Henüz bildirilmiş şikayet bulunmuyor. Her şey yolunda! ✨", color = Color.Gray, fontSize = 14.sp)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(reports) { report ->
                val time = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(report.timestamp))
                val displayName = report.senderName.ifBlank { "Aktif Üye" }
                val displayId = if (report.senderId.isNotBlank() && report.senderId != "SV-ANONYM") report.senderId else "SV-" + report.id.take(8).uppercase()

                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E293B))
                        .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.4f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Şikayet Edilen: $displayName", color = Color(0xFF60A5FA), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                            Text(text = time, color = Color.Gray, fontSize = 11.sp)
                        }

                        // User ID & Email info with single-tap copy
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF334155))
                                    .clickable { onCopyId(displayId) }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(text = "ID: $displayId", color = Color(0xFFFFD700), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Kopyala", tint = Color.LightGray, modifier = Modifier.size(11.dp))
                            }

                            if (report.userEmail.isNotBlank()) {
                                Text(text = "📧 ${report.userEmail}", color = Color.LightGray, fontSize = 11.sp)
                            }
                        }

                        // Reported message card
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .background(Color(0xFF0F172A))
                                .border(1.dp, Color(0xFF60A5FA).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                .padding(10.dp)
                        ) {
                            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                Text(text = "Şikayet Edilen Mesaj İçeriği:", color = Color(0xFF60A5FA), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                Text(
                                    text = if (report.messageText.isNotBlank()) "\"${report.messageText}\"" else "(Mesaj kaydı inceleniyor)",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        Text(text = "Sebep: ${report.reason.ifBlank { "Uygunsuz İçerik" }}", color = Color(0xFFF59E0B), fontSize = 12.sp, fontWeight = FontWeight.Bold)

                        // Action buttons row
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.padding(top = 4.dp)
                        ) {
                            // DM button
                            var isDmFocused by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .onFocusChanged { isDmFocused = it.isFocused }
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isDmFocused) Color(0xFF3B82F6) else Color(0xFF1D4ED8))
                                    .clickable { if (report.senderId.isNotBlank()) onOpenDm(report.senderId, report.senderName) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("💬 Özel Mesaj", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            // Delete message button
                            var isDelFocused by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .onFocusChanged { isDelFocused = it.isFocused }
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isDelFocused) Color(0xFFDC2626) else Color(0xFFDC2626).copy(alpha = 0.3f))
                                    .clickable { onDeleteMsg(report.roomId, report.messageId, report.id) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("🗑️ Mesajı Sil", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            // Ban user button
                            var isBanFocused by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .onFocusChanged { isBanFocused = it.isFocused }
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isBanFocused) Color(0xFF991B1B) else Color(0xFF7F1D1D))
                                    .clickable { if (report.senderId.isNotBlank()) onBanUser(report.senderId) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("⛔ Banla", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }

                            // Dismiss button
                            var isDismissFocused by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .onFocusChanged { isDismissFocused = it.isFocused }
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(if (isDismissFocused) AppColors.Brand else Color(0xFF334155))
                                    .clickable { onDismiss(report.id) }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text("✅ Kapat", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BannedUsersTab(
    bannedUsers: List<BannedUserInfo>,
    onUnban: (String) -> Unit,
    onCopyId: (String) -> Unit
) {
    if (bannedUsers.isEmpty()) {
        Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("Engellenmiş kullanıcı bulunmuyor. 👍", color = Color.Gray, fontSize = 14.sp)
        }
    } else {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(bannedUsers) { user ->
                val bannedAtStr = SimpleDateFormat("dd/MM HH:mm", Locale.getDefault()).format(Date(user.bannedAt))
                val isPermanent = user.durationHours == -1 || user.bannedUntil <= 0
                val durationStr = if (isPermanent) "Süresiz" else "${user.durationHours} Saat"
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(Color(0xFF1E293B))
                        .border(1.dp, Color(0xFFEF4444).copy(alpha = 0.3f), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(text = user.senderName.ifBlank { "Engellenen Kullanıcı" }, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)

                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF334155))
                                    .clickable { onCopyId(user.senderId) }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(text = "ID: ${user.senderId}", color = Color(0xFFFFD700), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Kopyala", tint = Color.LightGray, modifier = Modifier.size(11.dp))
                            }

                            if (user.userEmail.isNotBlank()) {
                                Text(text = "📧 E-posta: ${user.userEmail}", color = Color.LightGray, fontSize = 11.sp)
                            }
                            Text(text = "Tarih: $bannedAtStr • Süre: $durationStr", color = Color.Gray, fontSize = 11.sp)
                        }

                        var isUnbanFocused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .onFocusChanged { isUnbanFocused = it.isFocused }
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isUnbanFocused) Color(0xFF10B981) else Color(0xFF10B981).copy(alpha = 0.2f))
                                .clickable { onUnban(user.senderId) }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text("🔓 Engeli Kaldır", color = Color(0xFFA7F3D0), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun UsersTab(
    users: List<OnlineUserInfo>,
    onAssignBadge: (String, String?) -> Unit,
    onBanUser: (String) -> Unit,
    onOpenDm: (String, String) -> Unit,
    onCopyId: (String) -> Unit
) {
    val badges = listOf(
        "👑 Kurucu",
        "⭐ VIP",
        "🔥 Moderatör",
        "🏆 Topluluk Lideri",
        "🍿 Sinema Sever",
        "⚡ Aktif Üye"
    )
    var selectedUser by remember { mutableStateOf("") }
    var selectedUserName by remember { mutableStateOf("") }

    Column(modifier = Modifier.fillMaxSize(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Aktif / Çevrimiçi Kullanıcılar (${users.size}):", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)

        LazyColumn(
            modifier = Modifier.fillMaxWidth().weight(1f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            items(users) { user ->
                val isSelected = selectedUser == user.senderId
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(if (isSelected) Color(0xFF334155) else Color(0xFF1E293B))
                        .border(1.dp, if (isSelected) Color(0xFFFFD700) else Color.Transparent, RoundedCornerShape(10.dp))
                        .clickable {
                            selectedUser = user.senderId
                            selectedUserName = user.senderName
                        }
                        .padding(10.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text(text = user.senderName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(Color.Black.copy(alpha = 0.3f))
                                        .clickable { onCopyId(user.senderId) }
                                        .padding(horizontal = 4.dp, vertical = 1.dp)
                                ) {
                                    Text(text = user.senderId, color = Color(0xFFFFD700), fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Kopyala", tint = Color.Gray, modifier = Modifier.size(10.dp))
                                }
                            }
                            Text(text = "📧 ${user.userEmail}", color = Color.LightGray, fontSize = 11.sp)
                        }

                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            // DM button
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF1D4ED8))
                                    .clickable { onOpenDm(user.senderId, user.senderName) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("💬 DM", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                            // Ban button
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF991B1B))
                                    .clickable { onBanUser(user.senderId) }
                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                            ) {
                                Text("⛔ Ban", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }

        // Badge Assignment Box for Selected User
        if (selectedUser.isNotBlank()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .background(Color(0xFF1E293B))
                    .border(1.dp, Color(0xFFFFD700), RoundedCornerShape(12.dp))
                    .padding(10.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(text = "Seçili: \"$selectedUserName\" ($selectedUser) için Rozet Seçin:", color = Color(0xFFFFD700), fontWeight = FontWeight.Bold, fontSize = 12.sp)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        badges.take(3).forEach { badge ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF334155))
                                    .clickable { onAssignBadge(selectedUser, badge) }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = badge, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        badges.drop(3).forEach { badge ->
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(Color(0xFF334155))
                                    .clickable { onAssignBadge(selectedUser, badge) }
                                    .padding(vertical = 6.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = badge, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(Color(0xFF991B1B).copy(alpha = 0.4f))
                            .clickable { onAssignBadge(selectedUser, null) }
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(text = "❌ Rozeti Kaldır", color = Color(0xFFFECACA), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun AnnouncementTab(
    input: String,
    onInputChange: (String) -> Unit,
    onPost: (String) -> Unit
) {
    Column(
        modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Duyuru metnini yazın. Tüm kullanıcılara bildirim kanalı üzerinden yayınlanacaktır:", color = Color.LightGray, fontSize = 12.sp)

        OutlinedTextField(
            value = input,
            onValueChange = onInputChange,
            placeholder = { Text("Resmi duyuru metni...", color = Color.Gray, fontSize = 13.sp) },
            modifier = Modifier.fillMaxWidth().height(140.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Color(0xFFFFD700),
                unfocusedBorderColor = Color(0xFF334155),
                focusedTextColor = Color.White,
                unfocusedTextColor = Color.White
            )
        )

        var isPostFocused by remember { mutableStateOf(false) }
        Button(
            onClick = { onPost(input) },
            enabled = input.isNotBlank(),
            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFD700)),
            shape = RoundedCornerShape(10.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(48.dp)
                .onFocusChanged { isPostFocused = it.isFocused }
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(imageVector = Icons.Default.Campaign, contentDescription = null, tint = Color.Black)
                Text("📢 Duyuruyu Yayınla", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 14.sp)
            }
        }
    }
}
