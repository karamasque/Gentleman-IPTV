package com.kaynanamtv.app.ui.screens.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mail
import androidx.compose.material.icons.filled.People
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.CalendarToday
import androidx.compose.material.icons.filled.ZoomIn
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import com.kaynanamtv.app.ui.components.rememberCrossfadeImageModel
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.kaynanamtv.app.navigation.Routes
import com.kaynanamtv.app.ui.components.shell.AppScreenScaffold
import com.kaynanamtv.app.ui.design.AppColors
import com.kaynanamtv.domain.model.ChatMessage
import com.kaynanamtv.domain.model.ChatRoom
import com.kaynanamtv.domain.model.UserRole
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun CommunityChatScreen(
    currentRoute: String = Routes.COMMUNITY_CHAT,
    onNavigate: (String) -> Unit,
    viewModel: CommunityChatViewModel = hiltViewModel()
) {
    val nickname by viewModel.userNickname.collectAsStateWithLifecycle()
    val hasCustomNickname by viewModel.hasCustomNickname.collectAsStateWithLifecycle()
    val isAdmin by viewModel.isAdmin.collectAsStateWithLifecycle()
    val isBanned by viewModel.isBanned.collectAsStateWithLifecycle()
    val filteredMessages by viewModel.filteredMessages.collectAsStateWithLifecycle()
    val isLoadingOlder by viewModel.isLoadingOlder.collectAsStateWithLifecycle()
    val hasMoreOlder by viewModel.hasMoreOlder.collectAsStateWithLifecycle()
    val isSending by viewModel.isSending.collectAsStateWithLifecycle()
    val userError by viewModel.userMessageError.collectAsStateWithLifecycle()
    val selectedRoom by viewModel.selectedRoom.collectAsStateWithLifecycle()
    val replyingToMessage by viewModel.replyingToMessage.collectAsStateWithLifecycle()
    val editingMessage by viewModel.editingMessage.collectAsStateWithLifecycle()
    val searchQuery by viewModel.searchQuery.collectAsStateWithLifecycle()
    val showDmScreen by viewModel.showDmScreen.collectAsStateWithLifecycle()
    val privateMessages by viewModel.privateMessages.collectAsStateWithLifecycle()
    val isLoadingOlderPrivate by viewModel.isLoadingOlderPrivate.collectAsStateWithLifecycle()
    val hasMoreOlderPrivate by viewModel.hasMoreOlderPrivate.collectAsStateWithLifecycle()
    val activeDmUserName by viewModel.activeDmUserName.collectAsStateWithLifecycle()
    val dmPartners by viewModel.dmPartners.collectAsStateWithLifecycle()
    val deviceId = viewModel.currentDeviceId

    var inputText by remember { mutableStateOf("") }
    var showSearchBar by remember { mutableStateOf(false) }

    var showNicknameDialog by remember { mutableStateOf(false) }
    var showAdminLoginDialog by remember { mutableStateOf(false) }
    var showRulesDialog by remember { mutableStateOf(false) }
    var showTimedBanDialog by remember { mutableStateOf<String?>(null) }
    var showReportDialog by remember { mutableStateOf<Pair<String, String>?>(null) } // messageId to senderName
    var showDmListDialog by remember { mutableStateOf(false) }
    var showAdminPanelDialog by remember { mutableStateOf(false) }

    var selectedMessageForAction by remember { mutableStateOf<ChatMessage?>(null) }
    var showBadgeAssignForUser by remember { mutableStateOf<String?>(null) }
    var selectedImageForLightbox by remember { mutableStateOf<String?>(null) }
    var selectedUserProfile by remember { mutableStateOf<UserProfileData?>(null) }

    val context = LocalContext.current
    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let { selectedUri ->
            val cachedPath = runCatching {
                val file = java.io.File(context.cacheDir, "shared_img_${System.currentTimeMillis()}.jpg")
                context.contentResolver.openInputStream(selectedUri)?.use { input ->
                    file.outputStream().use { output -> input.copyTo(output) }
                }
                file.absolutePath
            }.getOrNull() ?: selectedUri.toString()
            viewModel.sendMessage(text = "", imageUrl = cachedPath)
        }
    }

    LaunchedEffect(hasCustomNickname) {
        if (!hasCustomNickname) showNicknameDialog = true
    }

    val listState = rememberLazyListState()
    var previousOldestMessageId by remember { mutableStateOf<String?>(null) }
    var previousLatestMessageId by remember { mutableStateOf<String?>(null) }
    var isInitialScrollDone by remember { mutableStateOf(false) }

    // Smart scroll management: auto-scroll to bottom only on initial load or new incoming/sent messages
    LaunchedEffect(filteredMessages) {
        if (filteredMessages.isNotEmpty()) {
            val currentOldestId = filteredMessages.firstOrNull()?.id
            val currentLatestId = filteredMessages.lastOrNull()?.id

            if (!isInitialScrollDone) {
                listState.scrollToItem(filteredMessages.size - 1)
                isInitialScrollDone = true
            } else if (currentLatestId != previousLatestMessageId && currentOldestId == previousOldestMessageId) {
                // New incoming or sent message at the bottom
                listState.animateScrollToItem(filteredMessages.size - 1)
            }

            previousOldestMessageId = currentOldestId
            previousLatestMessageId = currentLatestId
        }
    }

    // Pagination trigger when user scrolls near the top
    val shouldTriggerPagination by remember {
        derivedStateOf {
            val firstVisibleIndex = listState.firstVisibleItemIndex
            firstVisibleIndex <= 3 && !isLoadingOlder && hasMoreOlder && searchQuery.isBlank() && filteredMessages.size >= 50
        }
    }

    LaunchedEffect(shouldTriggerPagination) {
        if (shouldTriggerPagination) {
            viewModel.loadOlderMessages()
        }
    }

    AppScreenScaffold(
        currentRoute = currentRoute,
        onNavigate = onNavigate,
        title = "Topluluk Sohbeti",
        showScreenHeader = false
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .imePadding()
                .padding(2.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(Color(0xFF0F172A))
                .padding(10.dp)
        ) {
            // Header Bar with Room Switcher
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                // Room Selection Tabs - scrollable horizontally
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    viewModel.rooms.forEach { room ->
                        val isSelected = selectedRoom.id == room.id
                        var isFocused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .onFocusChanged { isFocused = it.isFocused }
                                .clip(RoundedCornerShape(10.dp))
                                .background(
                                    when {
                                        isSelected -> Color(0xFF2563EB)
                                        isFocused -> Color(0xFF334155)
                                        else -> Color(0xFF1E293B)
                                    }
                                )
                                .border(
                                    width = if (isSelected) 1.5.dp else 0.dp,
                                    color = if (isSelected) Color(0xFF60A5FA) else Color.Transparent,
                                    shape = RoundedCornerShape(10.dp)
                                )
                                .clickable { viewModel.selectRoom(room) }
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = room.name,
                                fontSize = 12.sp,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                color = Color.White,
                                maxLines = 1
                            )
                        }
                    }
                }
            }

            // Controls Bar - Scrollable Horizontally
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp)
                    .horizontalScroll(androidx.compose.foundation.rememberScrollState()),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Search button
                var isSearchFocused by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .onFocusChanged { isSearchFocused = it.isFocused }
                        .clip(CircleShape)
                        .background(if (showSearchBar) AppColors.Brand else if (isSearchFocused) Color(0xFF334155) else Color(0xFF1E293B))
                        .clickable { showSearchBar = !showSearchBar; if (!showSearchBar) viewModel.setSearchQuery("") },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Default.Search, contentDescription = "Ara", tint = Color.White, modifier = Modifier.size(14.dp))
                }

                // DM button
                var isDmFocused by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .onFocusChanged { isDmFocused = it.isFocused }
                        .clip(CircleShape)
                        .background(if (isDmFocused) AppColors.Brand else Color(0xFF1E293B))
                        .clickable { showDmListDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Default.Mail, contentDescription = "Özel Mesaj", tint = Color(0xFF60A5FA), modifier = Modifier.size(14.dp))
                }

                // Info button
                var isInfoFocused by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .size(28.dp)
                        .onFocusChanged { isInfoFocused = it.isFocused }
                        .clip(CircleShape)
                        .background(if (isInfoFocused) Color(0xFF334155) else Color(0xFF1E293B))
                        .clickable { showRulesDialog = true },
                    contentAlignment = Alignment.Center
                ) {
                    Icon(imageVector = Icons.Default.Info, contentDescription = "Kurallar", tint = Color.LightGray, modifier = Modifier.size(14.dp))
                }

                var isNickFocused by remember { mutableStateOf(false) }
                Box(
                    modifier = Modifier
                        .onFocusChanged { isNickFocused = it.isFocused }
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (isNickFocused) Color(0xFF334155) else Color(0xFF1E293B))
                        .clickable {
                            selectedUserProfile = UserProfileData(
                                senderId = deviceId,
                                senderName = nickname.ifBlank { "Belirlenmedi" },
                                userBadge = viewModel.getUserBadge(deviceId),
                                userEmail = viewModel.getUserEmail(),
                                avatarColorHex = "#3B82F6",
                                createdAt = viewModel.getAccountCreatedAt()
                            )
                        }
                        .padding(horizontal = 10.dp, vertical = 5.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Rumuz: ",
                            fontSize = 11.sp,
                            color = Color.Gray,
                            maxLines = 1
                        )
                        Text(
                            text = nickname.ifBlank { "Belirlenmedi" },
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = AppColors.Brand,
                            maxLines = 1
                        )
                        Icon(
                            imageVector = Icons.Default.Edit,
                            contentDescription = null,
                            tint = Color.Gray,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }

                if (!isAdmin) {
                    var isAdminBtnFocused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .onFocusChanged { isAdminBtnFocused = it.isFocused }
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isAdminBtnFocused) AppColors.Brand else Color(0xFF334155))
                            .clickable { showAdminLoginDialog = true }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = null,
                                tint = Color(0xFFFFD700),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Admin Girişi",
                                fontSize = 11.sp,
                                color = Color.White,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1
                            )
                        }
                    }
                } else {
                    var isAdminPanelFocused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .onFocusChanged { isAdminPanelFocused = it.isFocused }
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isAdminPanelFocused) Color(0xFFEAB308) else Color(0xFF854D0E))
                            .border(1.dp, Color(0xFFFFD700), RoundedCornerShape(8.dp))
                            .clickable { showAdminPanelDialog = true }
                            .padding(horizontal = 10.dp, vertical = 5.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Shield,
                                contentDescription = null,
                                tint = Color(0xFFFFD700),
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = "Admin Paneli",
                                fontSize = 11.sp,
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }
            } // end Controls Bar Row

            // Banned status banner
            if (isBanned) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFEF4444).copy(alpha = 0.2f))
                        .border(1.dp, Color(0xFFEF4444), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Block,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Sohbet erişiminiz yönetici tarafından engellenmiştir.",
                            color = Color(0xFFFECACA),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            // Read-Only Warning Banner for Announcements
            if (selectedRoom.isReadOnlyForUsers && !isAdmin) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 2.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFFF59E0B).copy(alpha = 0.15f))
                        .border(1.dp, Color(0xFFF59E0B), RoundedCornerShape(8.dp))
                        .padding(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Campaign,
                            contentDescription = null,
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = "Duyurular kanalında yalnızca yöneticiler paylaşım yapabilir.",
                            color = Color(0xFFFDE68A),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            }

            // Search Bar
            AnimatedVisibility(visible = showSearchBar) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 6.dp)
                        .clip(RoundedCornerShape(10.dp))
                        .background(Color(0xFF1E293B))
                        .border(1.dp, AppColors.Brand, RoundedCornerShape(10.dp))
                        .padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Search, contentDescription = null, tint = AppColors.Brand, modifier = Modifier.size(16.dp))
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { viewModel.setSearchQuery(it) },
                        modifier = Modifier.weight(1f),
                        textStyle = TextStyle(color = Color.White, fontSize = 13.sp),
                        cursorBrush = SolidColor(AppColors.Brand),
                        singleLine = true,
                        decorationBox = { inner ->
                            if (searchQuery.isEmpty()) Text("Mesajlarda ara...", color = Color.Gray, fontSize = 13.sp)
                            inner()
                        }
                    )
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { viewModel.setSearchQuery("") }, modifier = Modifier.size(20.dp)) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(14.dp))
                        }
                    }
                }
            }

            // Messages List Container
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(vertical = 4.dp)
            ) {
                if (filteredMessages.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (searchQuery.isNotBlank()) "'$searchQuery' için sonuç bulunamadı."
                                   else if (selectedRoom.isReadOnlyForUsers) "Henüz yayınlanmış duyuru yok."
                                   else "Henüz mesaj yok. İlk mesajı sen yaz!",
                            color = Color.Gray,
                            fontSize = 15.sp
                        )
                    }
                } else {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        if (isLoadingOlder) {
                            item(key = "loading_indicator_older_chat") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 6.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    androidx.compose.material3.CircularProgressIndicator(
                                        modifier = Modifier.size(20.dp),
                                        strokeWidth = 2.dp,
                                        color = AppColors.Brand
                                    )
                                }
                            }
                        }
                        items(
                            items = filteredMessages,
                            key = { it.id }
                        ) { message ->
                            val isMe = message.senderId == deviceId
                            // Mark seen for announcement messages
                            if (selectedRoom.id == ChatRoom.ANNOUNCEMENTS_ROOM.id && !message.seenBy.contains(deviceId)) {
                                LaunchedEffect(message.id) { viewModel.markMessageSeen(message.id) }
                            }
                            SpaciousChatBubble(
                                message = message,
                                isMe = isMe,
                                onMessageClick = {
                                    selectedMessageForAction = message
                                },
                                onAvatarClick = {
                                    val accountDate = if (message.userCreatedAt > 0L) {
                                        message.userCreatedAt
                                    } else if (isMe) {
                                        viewModel.getAccountCreatedAt()
                                    } else {
                                        0L
                                    }
                                    selectedUserProfile = UserProfileData(
                                        senderId = message.senderId,
                                        senderName = message.senderName,
                                        userBadge = message.userBadge,
                                        avatarColorHex = message.avatarColorHex,
                                        createdAt = accountDate
                                    )
                                },
                                onImageClick = { imgUrl ->
                                    selectedImageForLightbox = imgUrl
                                },
                                onReactionClick = { emoji ->
                                    viewModel.toggleReaction(message.id, emoji)
                                }
                            )
                        }
                    }
                }
            }

            // Editing Preview Bar
            editingMessage?.let { editTarget ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E293B))
                        .border(1.dp, Color(0xFFF59E0B), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.Default.Edit, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(16.dp))
                        Column {
                            Text(text = "Mesaj düzenleniyor:", color = Color(0xFFF59E0B), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(text = editTarget.message, color = Color.LightGray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    IconButton(onClick = { viewModel.setEditingMessage(null); inputText = "" }, modifier = Modifier.size(24.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "İptal", tint = Color.Gray, modifier = Modifier.size(16.dp))
                    }
                }
                // Prefill input for editing
                LaunchedEffect(editTarget.id) { inputText = editTarget.message }
            }
            // Replying Preview Bar
            replyingToMessage?.let { replyTarget ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 4.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xFF1E293B))
                        .border(1.dp, AppColors.Brand, RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(imageVector = Icons.AutoMirrored.Filled.Reply, contentDescription = null, tint = AppColors.Brand, modifier = Modifier.size(16.dp))
                        Column {
                            Text(text = "${replyTarget.senderName} kişisine yanıt veriliyor:", color = AppColors.Brand, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            Text(text = replyTarget.message, color = Color.LightGray, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        }
                    }
                    IconButton(onClick = { viewModel.setReplyToMessage(null) }, modifier = Modifier.size(24.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "İptal", tint = Color.Gray, modifier = Modifier.size(16.dp))
                    }
                }
            }


            // Quick replies row
            if (!selectedRoom.isReadOnlyForUsers || isAdmin) {
                val quickReplies = listOf(
                    "Selam herkese! 👋",
                    "İyi seyirler! 🍿",
                    "Teşekkürler 👍",
                    "Sistem harika çalışıyor! 🚀"
                )
                // NOTE: Use Row+horizontalScroll instead of LazyRow — LazyRow causes
                // IllegalStateException when parent provides unbounded width constraints.
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(androidx.compose.foundation.rememberScrollState())
                        .padding(bottom = 6.dp)
                ) {
                    quickReplies.forEach { reply ->
                        var isReplyFocused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .onFocusChanged { isReplyFocused = it.isFocused }
                                .clip(RoundedCornerShape(16.dp))
                                .background(if (isReplyFocused) AppColors.Brand else Color(0xFF1E293B))
                                .clickable {
                                    if (!hasCustomNickname) {
                                        showNicknameDialog = true
                                    } else if (!isBanned) {
                                        viewModel.sendMessage(reply)
                                    }
                                }
                                .padding(horizontal = 12.dp, vertical = 5.dp)
                        ) {
                            Text(
                                text = reply,
                                fontSize = 12.sp,
                                color = Color.White,
                                maxLines = 1,
                                softWrap = false
                            )
                        }
                    }
                }
            }

            // Input Bar with Photo Picker
            val canSendInCurrentRoom = !selectedRoom.isReadOnlyForUsers || isAdmin
            val focusManager = LocalFocusManager.current

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                if (canSendInCurrentRoom) {
                    IconButton(
                        onClick = {
                            if (!hasCustomNickname) {
                                showNicknameDialog = true
                            } else {
                                imagePickerLauncher.launch("image/*")
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(Color(0xFF1E293B))
                    ) {
                        Icon(
                            imageVector = Icons.Default.AddPhotoAlternate,
                            contentDescription = "Resim Ekle",
                            tint = AppColors.Brand,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                }

                ChatInputField(
                    value = inputText,
                    onValueChange = { inputText = it },
                    placeholder = when {
                        editingMessage != null -> "Mesajı düzenleyin..."
                        !canSendInCurrentRoom -> "Bu kanala yalnızca yöneticiler mesaj yazabilir..."
                        !hasCustomNickname -> "Sohbet etmek için kullanıcı adı belirleyin..."
                        else -> "Bir mesaj yazın... (@isim ile bahsedin)"
                    },
                    enabled = !isBanned && canSendInCurrentRoom,
                    onSend = {
                        if (editingMessage != null) {
                            // Editing mode - update existing message
                            viewModel.editMessage(editingMessage!!.id, inputText)
                            inputText = ""
                            focusManager.clearFocus()
                        } else if (!hasCustomNickname) {
                            showNicknameDialog = true
                        } else if (inputText.isNotBlank() && !isBanned && canSendInCurrentRoom) {
                            viewModel.sendMessage(inputText)
                            inputText = ""
                            focusManager.clearFocus()
                        }
                    },
                    modifier = Modifier.weight(1f)
                )

                IconButton(
                    onClick = {
                        if (editingMessage != null) {
                            viewModel.editMessage(editingMessage!!.id, inputText)
                            inputText = ""
                            focusManager.clearFocus()
                        } else if (!hasCustomNickname) {
                            showNicknameDialog = true
                        } else if (inputText.isNotBlank() && !isBanned && canSendInCurrentRoom) {
                            viewModel.sendMessage(inputText)
                            inputText = ""
                            focusManager.clearFocus()
                        }
                    },
                    enabled = !isSending && !isBanned && (editingMessage != null || canSendInCurrentRoom) && (inputText.isNotBlank() || !hasCustomNickname),
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .background(if (hasCustomNickname && inputText.isNotBlank() && !isBanned && canSendInCurrentRoom) AppColors.Brand else Color(0xFF334155))
                ) {
                    Icon(
                        imageVector = if (editingMessage != null) Icons.Default.Edit else Icons.AutoMirrored.Filled.Send,
                        contentDescription = if (editingMessage != null) "Düzenlemeyi Kaydet" else "Gönder",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }

    // Full-Screen Image Lightbox Viewer
    selectedImageForLightbox?.let { imageUrl ->
        Dialog(onDismissRequest = { selectedImageForLightbox = null }) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.95f))
                    .clickable { selectedImageForLightbox = null },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = imageUrl,
                    contentDescription = "Büyütülmüş Görsel",
                    modifier = Modifier
                        .fillMaxWidth(0.9f)
                        .wrapContentHeight(),
                    contentScale = ContentScale.Fit
                )
            }
        }
    }

    // Message Action Dialog (Emoji Reactions, Reply, Admin Badge Assign, Delete, Ban)
    selectedMessageForAction?.let { targetMsg ->
        AlertDialog(
            onDismissRequest = { selectedMessageForAction = null },
            title = {
                Text(
                    text = "${targetMsg.senderName} mesajı",
                    color = Color.White,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "\"${targetMsg.message.take(80)}\"",
                        color = Color.LightGray,
                        fontSize = 13.sp
                    )

                    // Emoji Reaction Quick Bar
                    Text(text = "Hızlı Tepki Ver:", color = Color.Gray, fontSize = 12.sp)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        listOf("👍", "❤️", "😂", "🔥", "😮", "👏").forEach { emoji ->
                            var isEmojiFocused by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .onFocusChanged { isEmojiFocused = it.isFocused }
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isEmojiFocused) AppColors.Brand else Color(0xFF334155))
                                    .clickable {
                                        viewModel.toggleReaction(targetMsg.id, emoji)
                                        selectedMessageForAction = null
                                    }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(text = emoji, fontSize = 18.sp)
                            }
                        }
                    }

                    // Reply Option
                    var isReplyFocused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { isReplyFocused = it.isFocused }
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isReplyFocused) AppColors.Brand else Color(0xFF334155))
                            .clickable {
                                viewModel.setReplyToMessage(targetMsg)
                                selectedMessageForAction = null
                            }
                            .padding(12.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.Reply, contentDescription = null, tint = Color.White)
                            Text(text = "Yanıtla (Reply)", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Edit own message (within 5 minutes)
                    val canEdit = targetMsg.senderId == deviceId &&
                        (System.currentTimeMillis() - targetMsg.timestamp) < 5 * 60 * 1000L
                    if (canEdit || isAdmin) {
                        var isEditFocused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { isEditFocused = it.isFocused }
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isEditFocused) Color(0xFFF59E0B) else Color(0xFFF59E0B).copy(alpha = 0.15f))
                                .clickable {
                                    viewModel.setEditingMessage(targetMsg)
                                    inputText = targetMsg.message
                                    selectedMessageForAction = null
                                }
                                .padding(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Edit, contentDescription = null, tint = Color(0xFFF59E0B))
                                Text(text = "Mesajı Düzenle${if (!isAdmin && canEdit) " (5 dk içinde)" else ""}", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // DM (Özel Mesaj) - only for other users
                    if (targetMsg.senderId != deviceId) {
                        var isDmActionFocused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { isDmActionFocused = it.isFocused }
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isDmActionFocused) Color(0xFF60A5FA) else Color(0xFF60A5FA).copy(alpha = 0.15f))
                                .clickable {
                                    viewModel.openDm(targetMsg.senderId, targetMsg.senderName)
                                    selectedMessageForAction = null
                                }
                                .padding(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Mail, contentDescription = null, tint = Color(0xFF60A5FA))
                                Text(text = "Özel Mesaj Gönder (DM)", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Report option (non-admin, other user's message)
                    if (targetMsg.senderId != deviceId) {
                        var isReportFocused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { isReportFocused = it.isFocused }
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isReportFocused) Color(0xFFEF4444) else Color(0xFFEF4444).copy(alpha = 0.1f))
                                .clickable {
                                    showReportDialog = Pair(targetMsg.id, targetMsg.senderName)
                                    selectedMessageForAction = null
                                }
                                .padding(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Flag, contentDescription = null, tint = Color(0xFFEF4444))
                                Text(text = "Şikayet Et", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Admin Badge Manager Option
                    if (isAdmin) {
                        var isBadgeBtnFocused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { isBadgeBtnFocused = it.isFocused }
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isBadgeBtnFocused) Color(0xFFFFD700) else Color(0xFF1E293B))
                                .clickable {
                                    showBadgeAssignForUser = targetMsg.senderId
                                    selectedMessageForAction = null
                                }
                                .padding(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Star, contentDescription = null, tint = Color(0xFFFFD700))
                                Text(text = "Kullanıcıya Rozet Ver / Yönet", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Delete message
                        var isDeleteBtnFocused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { isDeleteBtnFocused = it.isFocused }
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isDeleteBtnFocused) Color(0xFFDC2626) else Color(0xFFDC2626).copy(alpha = 0.2f))
                                .clickable {
                                    viewModel.deleteMessage(targetMsg.id)
                                    selectedMessageForAction = null
                                }
                                .padding(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(imageVector = Icons.Default.Delete, contentDescription = null, tint = Color(0xFFEF4444))
                                Text(text = "Mesajı Sil", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        }

                        // Timed ban (replaces permanent ban)
                        if (targetMsg.userRole != UserRole.ADMIN) {
                            var isBanBtnFocused by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { isBanBtnFocused = it.isFocused }
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(if (isBanBtnFocused) Color(0xFFEF4444) else Color(0xFF991B1B).copy(alpha = 0.4f))
                                    .clickable {
                                        showTimedBanDialog = targetMsg.senderId
                                        selectedMessageForAction = null
                                    }
                                    .padding(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(imageVector = Icons.Default.Block, contentDescription = null, tint = Color(0xFFEF4444))
                                    Text(text = "Kullanıcıyı Engelle (Süreli)", color = Color.White, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedMessageForAction = null }) {
                    Text("Kapat", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    // Admin Badge Assignment Dialog
    showBadgeAssignForUser?.let { targetSenderId ->
        val badges = listOf(
            "👑 Kurucu",
            "⭐ VIP",
            "🔥 Moderatör",
            "🏆 Topluluk Lideri",
            "🍿 Sinema Sever",
            "⚡ Aktif Üye"
        )
        AlertDialog(
            onDismissRequest = { showBadgeAssignForUser = null },
            title = {
                Text(text = "Rozet Atama Paneli", color = Color.White, fontWeight = FontWeight.Bold)
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 380.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = "Bu kullanıcı için atanacak rozeti seçiniz:", color = Color.LightGray, fontSize = 13.sp)
                    badges.forEach { badge ->
                        val style = getBadgeStyle(badge)
                        var isItemFocused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { isItemFocused = it.isFocused }
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isItemFocused) AppColors.Brand else style.backgroundColor)
                                .border(1.dp, style.borderColor, RoundedCornerShape(8.dp))
                                .clickable {
                                    viewModel.assignUserBadge(targetSenderId, badge)
                                    showBadgeAssignForUser = null
                                }
                                .padding(10.dp)
                        ) {
                            Text(text = badge, color = style.textColor, fontWeight = FontWeight.Bold)
                        }
                    }
                    var isRemoveFocused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .onFocusChanged { isRemoveFocused = it.isFocused }
                            .clip(RoundedCornerShape(8.dp))
                            .background(if (isRemoveFocused) Color(0xFFDC2626) else Color(0xFF991B1B).copy(alpha = 0.3f))
                            .clickable {
                                viewModel.assignUserBadge(targetSenderId, null)
                                showBadgeAssignForUser = null
                            }
                            .padding(10.dp)
                    ) {
                        Text(text = "❌ Rozeti Kaldır", color = Color(0xFFFECACA), fontWeight = FontWeight.Bold)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showBadgeAssignForUser = null }) {
                    Text("Kapat", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    // Community Rules Dialog
    if (showRulesDialog) {
        AlertDialog(
            onDismissRequest = { showRulesDialog = false },
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Info, contentDescription = null, tint = AppColors.Brand)
                    Text("Topluluk Kuralları", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("1. Küfür, hakaret ve argo ifadeler kesinlikle yasaktır (Otomatik filtrelenmektedir).", color = Color.LightGray, fontSize = 13.sp)
                    Text("2. Reklam, kanal paylaşımı veya ticari mesajlar engellenir.", color = Color.LightGray, fontSize = 13.sp)
                    Text("3. Saygılı ve seviyeli bir sohbet ortamı sürdürünüz.", color = Color.LightGray, fontSize = 13.sp)
                    Text("4. Kuralları ihlal eden kullanıcılar yöneticiler tarafından süresiz banlanır.", color = Color(0xFFEF4444), fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            },
            confirmButton = {
                TextButton(onClick = { showRulesDialog = false }) {
                    Text("Anladım", color = AppColors.Brand)
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    // Nickname Edit / Setup Dialog
    if (showNicknameDialog) {
        var tempName by remember { mutableStateOf(nickname) }
        var errorMessage by remember { mutableStateOf<String?>(null) }

        AlertDialog(
            onDismissRequest = {
                if (hasCustomNickname) {
                    showNicknameDialog = false
                }
            },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth(0.92f)
                .padding(vertical = 4.dp),
            title = {
                Text(
                    text = if (hasCustomNickname) "Kullanıcı Adınızı Değiştirin" else "Kullanıcı Adı Belirleyin",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 160.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = if (hasCustomNickname)
                            "Sohbette görünecek adınızı değiştirebilirsiniz."
                        else
                            "Topluluk sohbetinde mesaj yazabilmek için lütfen önce kendinize bir kullanıcı adı belirleyin.",
                        color = Color.LightGray,
                        fontSize = 12.sp
                    )
                    ChatInputField(
                        value = tempName,
                        onValueChange = {
                            tempName = it
                            errorMessage = null
                        },
                        placeholder = "Kullanıcı Adınız",
                        enabled = true,
                        height = 40.dp,
                        onSend = {
                            if (tempName.trim().length >= 2) {
                                viewModel.updateNickname(tempName)
                                showNicknameDialog = false
                            } else {
                                errorMessage = "Kullanıcı adı en az 2 karakter olmalıdır."
                            }
                        }
                    )
                    errorMessage?.let { err ->
                        Text(text = err, color = Color(0xFFEF4444), fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (tempName.trim().length >= 2) {
                            viewModel.updateNickname(tempName)
                            showNicknameDialog = false
                        } else {
                            errorMessage = "Kullanıcı adı en az 2 karakter olmalıdır."
                        }
                    }
                ) {
                    Text("Kaydet", color = AppColors.Brand, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                if (hasCustomNickname) {
                    TextButton(onClick = { showNicknameDialog = false }) {
                        Text("İptal", color = Color.Gray)
                    }
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    // Admin Login Dialog
    if (showAdminLoginDialog) {
        var passInput by remember { mutableStateOf("") }
        var passError by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showAdminLoginDialog = false },
            properties = DialogProperties(usePlatformDefaultWidth = false),
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth(0.92f)
                .padding(vertical = 4.dp),
            title = {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(imageVector = Icons.Default.Shield, contentDescription = null, tint = Color(0xFFFFD700))
                    Text("Admin Yetkilendirmesi", color = Color.White, fontSize = 15.sp)
                }
            },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 160.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(text = "Yönetici modunu aktifleştirmek için admin şifresini girin:", color = Color.LightGray, fontSize = 12.sp)
                    ChatInputField(
                        value = passInput,
                        onValueChange = {
                            passInput = it
                            passError = false
                        },
                        placeholder = "Admin Şifresi",
                        enabled = true,
                        height = 40.dp,
                        onSend = {
                            if (viewModel.loginAdmin(passInput)) {
                                showAdminLoginDialog = false
                            } else {
                                passError = true
                            }
                        }
                    )
                    if (passError) {
                        Text(text = "Hatalı admin şifresi!", color = Color(0xFFEF4444), fontSize = 12.sp)
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    if (viewModel.loginAdmin(passInput)) {
                        showAdminLoginDialog = false
                    } else {
                        passError = true
                    }
                }) {
                    Text("Giriş Yap", color = Color(0xFFFFD700), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showAdminLoginDialog = false }) {
                    Text("İptal", color = Color.Gray)
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    // User Error Dialog
    if (userError != null) {
        AlertDialog(
            onDismissRequest = { viewModel.clearError() },
            title = { Text("Sohbet Bildirimi", color = Color.White) },
            text = { Text(userError ?: "", color = Color.LightGray) },
            confirmButton = {
                TextButton(onClick = { viewModel.clearError() }) {
                    Text("Tamam", color = AppColors.Brand)
                }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    // Timed Ban Dialog
    showTimedBanDialog?.let { targetBanId ->
        val banOptions = listOf(
            "1 Saat" to 1,
            "6 Saat" to 6,
            "24 Saat" to 24,
            "3 Gün" to 72,
            "7 Gün" to 168,
            "Süresiz" to -1
        )
        AlertDialog(
            onDismissRequest = { showTimedBanDialog = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(imageVector = Icons.Default.Block, contentDescription = null, tint = Color(0xFFEF4444))
                    Text("Ban Süresi Seçin", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("Kullanıcı ne kadar süre engellensin?", color = Color.LightGray, fontSize = 13.sp)
                    banOptions.forEach { option ->
                        val label = option.first
                        val hours = option.second
                        var isItemFocused by remember { mutableStateOf(false) }
                        val bg = when {
                            hours == -1 -> if (isItemFocused) Color(0xFFDC2626) else Color(0xFF991B1B).copy(alpha = 0.5f)
                            else -> if (isItemFocused) Color(0xFFEF4444) else Color(0xFF334155)
                        }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { isItemFocused = it.isFocused }
                                .clip(RoundedCornerShape(8.dp))
                                .background(bg)
                                .clickable {
                                    viewModel.banUser(targetBanId, hours)
                                    showTimedBanDialog = null
                                }
                                .padding(12.dp)
                        ) {
                            Text(
                                text = if (hours == -1) "⛔ $label" else "⏰ $label",
                                color = Color.White,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTimedBanDialog = null }) { Text("İptal", color = Color.Gray) }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    // Report Dialog
    showReportDialog?.let { reportPair ->
        val msgId = reportPair.first
        val senderName = reportPair.second
        val reportReasons = listOf("Spam", "Küfür / Hakaret", "Uygunsuz İçerik", "Yanıltıcı Bilgi", "Diğer")
        AlertDialog(
            onDismissRequest = { showReportDialog = null },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(imageVector = Icons.Default.Flag, contentDescription = null, tint = Color(0xFFEF4444))
                    Text("Şikayet Et", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("\"$senderName\" kullanıcısının mesajını şikayet etme nedeniniz:", color = Color.LightGray, fontSize = 13.sp)
                    reportReasons.forEach { reason ->
                        var isItemFocused by remember { mutableStateOf(false) }
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .onFocusChanged { isItemFocused = it.isFocused }
                                .clip(RoundedCornerShape(8.dp))
                                .background(if (isItemFocused) Color(0xFFEF4444) else Color(0xFF334155))
                                .clickable {
                                    viewModel.reportMessage(msgId, senderName, reason)
                                    showReportDialog = null
                                }
                                .padding(12.dp)
                        ) {
                            Text(text = reason, color = Color.White, fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showReportDialog = null }) { Text("İptal", color = Color.Gray) }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    // DM Partner List Dialog
    if (showDmListDialog) {
        AlertDialog(
            onDismissRequest = { showDmListDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Icon(imageVector = Icons.Default.Mail, contentDescription = null, tint = Color(0xFF60A5FA))
                    Text("Özel Mesajlar", color = Color.White, fontWeight = FontWeight.Bold)
                }
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().heightIn(max = 360.dp).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (dmPartners.isEmpty()) {
                        Text(
                            "Henüz özel mesajlaşma geçmişiniz yok.\nBir kullanıcının mesajına tıklayarak 'Özel Mesaj Gönder' seçeneğini kullanın.",
                            color = Color.Gray, fontSize = 13.sp
                        )
                    } else {
                        Text("Önceki sohbetler:", color = Color.LightGray, fontSize = 12.sp)
                        dmPartners.forEach { partner ->
                            val userId = partner.first
                            val userName = partner.second
                            var isItemFocused by remember { mutableStateOf(false) }
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .onFocusChanged { isItemFocused = it.isFocused }
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(if (isItemFocused) Color(0xFF60A5FA) else Color(0xFF1E293B))
                                    .border(1.dp, Color(0xFF60A5FA).copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                                    .clickable {
                                        viewModel.openDm(userId, userName)
                                        showDmListDialog = false
                                    }
                                    .padding(12.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier.size(32.dp).clip(CircleShape).background(Color(0xFF60A5FA).copy(alpha = 0.3f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(text = userName.take(1).uppercase(), color = Color(0xFF60A5FA), fontWeight = FontWeight.Bold)
                                    }
                                    Text(text = userName, color = Color.White, fontWeight = FontWeight.SemiBold)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showDmListDialog = false }) { Text("Kapat", color = Color.Gray) }
            },
            containerColor = Color(0xFF1E293B)
        )
    }

    // DM Chat Screen Overlay
    if (showDmScreen && activeDmUserName != null) {
        Dialog(onDismissRequest = { viewModel.closeDm() }) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.85f)
                    .clip(RoundedCornerShape(16.dp))
                    .background(Color(0xFF0F172A))
            ) {
                Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                    // DM Header
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Box(
                                modifier = Modifier.size(36.dp).clip(CircleShape).background(Color(0xFF60A5FA).copy(alpha = 0.3f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(text = (activeDmUserName ?: "?").take(1).uppercase(), color = Color(0xFF60A5FA), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            }
                            Column {
                                Text(text = activeDmUserName ?: "?", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                Text(text = "Özel Sohbet", color = Color(0xFF60A5FA), fontSize = 11.sp)
                            }
                        }
                        var isCloseDmFocused by remember { mutableStateOf(false) }
                        IconButton(
                            onClick = { viewModel.closeDm() },
                            modifier = Modifier.size(32.dp).clip(CircleShape).background(if (isCloseDmFocused) Color(0xFF334155) else Color.Transparent)
                                .onFocusChanged { isCloseDmFocused = it.isFocused }
                        ) {
                            Icon(imageVector = Icons.Default.Close, contentDescription = "Kapat", tint = Color.Gray, modifier = Modifier.size(18.dp))
                        }
                    }

                    // DM Messages
                    val dmListState = rememberLazyListState()
                    var previousOldestDmId by remember { mutableStateOf<String?>(null) }
                    var previousLatestDmId by remember { mutableStateOf<String?>(null) }
                    var isDmInitialScrollDone by remember { mutableStateOf(false) }

                    LaunchedEffect(privateMessages) {
                        if (privateMessages.isNotEmpty()) {
                            val currentOldestId = privateMessages.firstOrNull()?.id
                            val currentLatestId = privateMessages.lastOrNull()?.id

                            if (!isDmInitialScrollDone) {
                                dmListState.scrollToItem(privateMessages.size - 1)
                                isDmInitialScrollDone = true
                            } else if (currentLatestId != previousLatestDmId && currentOldestId == previousOldestDmId) {
                                dmListState.animateScrollToItem(privateMessages.size - 1)
                            }
                            previousOldestDmId = currentOldestId
                            previousLatestDmId = currentLatestId
                        }
                    }

                    val shouldTriggerDmPagination by remember {
                        derivedStateOf {
                            val firstVisibleIndex = dmListState.firstVisibleItemIndex
                            firstVisibleIndex <= 3 && !isLoadingOlderPrivate && hasMoreOlderPrivate && privateMessages.size >= 30
                        }
                    }
                    LaunchedEffect(shouldTriggerDmPagination) {
                        if (shouldTriggerDmPagination) {
                            viewModel.loadOlderPrivateMessages()
                        }
                    }

                    LazyColumn(
                        state = dmListState,
                        modifier = Modifier.weight(1f).fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(6.dp),
                        contentPadding = PaddingValues(vertical = 4.dp)
                    ) {
                        if (isLoadingOlderPrivate) {
                            item(key = "loading_indicator_older_dm") {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 4.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    androidx.compose.material3.CircularProgressIndicator(
                                        modifier = Modifier.size(18.dp),
                                        strokeWidth = 2.dp,
                                        color = Color(0xFF60A5FA)
                                    )
                                }
                            }
                        }
                        items(
                            items = privateMessages,
                            key = { it.id }
                        ) { msg ->
                            val isMe = msg.senderId == deviceId
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start
                            ) {
                                Column(
                                    modifier = Modifier
                                        .widthIn(max = 260.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(if (isMe) AppColors.Brand else Color(0xFF1E293B))
                                        .padding(horizontal = 12.dp, vertical = 8.dp)
                                ) {
                                    if (!isMe) {
                                        Text(text = msg.senderName, color = Color(0xFF60A5FA), fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Text(text = msg.message, color = Color.White, fontSize = 13.sp)
                                    val dmTime = SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(msg.timestamp))
                                    Text(text = dmTime, color = Color.White.copy(alpha = 0.5f), fontSize = 10.sp, modifier = Modifier.align(Alignment.End))
                                }
                            }
                        }
                    }

                    // DM Input
                    var dmInput by remember { mutableStateOf("") }
                    val dmFocusManager = LocalFocusManager.current
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        ChatInputField(
                            value = dmInput,
                            onValueChange = { dmInput = it },
                            placeholder = "Özel mesaj yaz...",
                            enabled = true,
                            onSend = {
                                if (dmInput.isNotBlank()) {
                                    viewModel.sendDmMessage(dmInput)
                                    dmInput = ""
                                    dmFocusManager.clearFocus()
                                }
                            },
                            modifier = Modifier.weight(1f)
                        )
                        var isDmSendFocused by remember { mutableStateOf(false) }
                        IconButton(
                            onClick = {
                                if (dmInput.isNotBlank()) {
                                    viewModel.sendDmMessage(dmInput)
                                    dmInput = ""
                                    dmFocusManager.clearFocus()
                                }
                            },
                            enabled = dmInput.isNotBlank(),
                            modifier = Modifier.size(48.dp).clip(RoundedCornerShape(12.dp))
                                .background(if (dmInput.isNotBlank()) Color(0xFF60A5FA) else Color(0xFF334155))
                                .onFocusChanged { isDmSendFocused = it.isFocused }
                        ) {
                            Icon(imageVector = Icons.AutoMirrored.Filled.Send, contentDescription = "Gönder", tint = Color.White, modifier = Modifier.size(20.dp))
                        }
                    }
                }
            }
        }
    }

    // Admin Control Panel Dialog
    if (showAdminPanelDialog) {
        com.kaynanamtv.app.ui.screens.admin.AdminPanelDialog(
            viewModel = hiltViewModel(),
            onDismiss = { showAdminPanelDialog = false },
            onOpenDm = { targetId, targetName ->
                viewModel.openDm(targetId, targetName)
            }
        )
    }

    // User Profile Dialog
    selectedUserProfile?.let { profile ->
        UserProfileDialog(
            profile = profile,
            isSelf = profile.senderId == deviceId,
            onDismiss = { selectedUserProfile = null },
            onOpenDm = { uId, uName -> viewModel.openDm(uId, uName) },
            onChangeNickname = { showNicknameDialog = true }
        )
    }

    // Fullscreen Image Lightbox Dialog
    selectedImageForLightbox?.let { imageUrl ->
        val path = remember(imageUrl) {
            when {
                imageUrl.startsWith("content://") || imageUrl.startsWith("http") -> imageUrl
                imageUrl.startsWith("/") -> "file://$imageUrl"
                else -> imageUrl
            }
        }
        val imageModel = rememberCrossfadeImageModel(path)
        Dialog(
            onDismissRequest = { selectedImageForLightbox = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.94f))
                    .clickable { selectedImageForLightbox = null },
                contentAlignment = Alignment.Center
            ) {
                AsyncImage(
                    model = imageModel,
                    contentDescription = "Büyük Görsel",
                    modifier = Modifier
                        .fillMaxWidth(0.95f)
                        .fillMaxHeight(0.90f)
                        .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Fit
                )
                IconButton(
                    onClick = { selectedImageForLightbox = null },
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(16.dp)
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(Color(0xFF334155))
                ) {
                    Icon(imageVector = Icons.Default.Close, contentDescription = "Kapat", tint = Color.White)
                }
            }
        }
    }
}


@Composable
private fun ChatInputField(
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    enabled: Boolean,
    onSend: () -> Unit,
    modifier: Modifier = Modifier,
    height: Dp = 48.dp
) {
    var textFieldValue by remember {
        mutableStateOf(TextFieldValue(text = value, selection = TextRange(value.length)))
    }

    LaunchedEffect(value) {
        if (value != textFieldValue.text) {
            val start = textFieldValue.selection.start.coerceIn(0, value.length)
            val end = textFieldValue.selection.end.coerceIn(0, value.length)
            val comp = textFieldValue.composition?.let { composition ->
                val cs = composition.start.coerceIn(0, value.length)
                val ce = composition.end.coerceIn(0, value.length)
                if (cs <= ce) TextRange(cs, ce) else null
            }
            textFieldValue = TextFieldValue(
                text = value,
                selection = TextRange(start, end),
                composition = comp
            )
        }
    }

    Box(
        modifier = modifier
            .height(height)
            .clip(RoundedCornerShape(12.dp))
            .background(Color(0xFF1E293B))
            .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
            .padding(horizontal = 14.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        if (value.isEmpty()) {
            Text(
                text = placeholder,
                color = Color.Gray,
                fontSize = 14.sp
            )
        }
        BasicTextField(
            value = textFieldValue,
            onValueChange = { newTv ->
                textFieldValue = newTv
                onValueChange(newTv.text)
            },
            textStyle = TextStyle(
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Normal
            ),
            singleLine = true,
            cursorBrush = SolidColor(AppColors.Brand),
            enabled = enabled,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
            keyboardActions = KeyboardActions(onSend = { onSend() }),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

data class UserProfileData(
    val senderId: String,
    val senderName: String,
    val userBadge: String? = null,
    val userEmail: String? = null,
    val avatarColorHex: String = "#3B82F6",
    val createdAt: Long = System.currentTimeMillis()
)

@Composable
private fun UserProfileDialog(
    profile: UserProfileData,
    isSelf: Boolean,
    onDismiss: () -> Unit,
    onOpenDm: (userId: String, userName: String) -> Unit,
    onChangeNickname: () -> Unit
) {
    val clipboardManager = LocalClipboardManager.current
    var copyToast by remember { mutableStateOf(false) }
    val avatarColor = remember(profile.avatarColorHex) {
        runCatching { Color(android.graphics.Color.parseColor(profile.avatarColorHex)) }.getOrDefault(Color(0xFF3B82F6))
    }
    val createdDateStr = remember(profile.createdAt) {
        if (profile.createdAt > 0L) {
            SimpleDateFormat("dd MMMM yyyy, HH:mm", Locale("tr")).format(Date(profile.createdAt))
        } else {
            "Bilinmiyor"
        }
    }

    val displayId = remember(profile.senderId) {
        if (profile.senderId.isBlank()) "SV-ANONYM"
        else if (profile.senderId.startsWith("SV-")) {
            "SV-" + profile.senderId.removePrefix("SV-").take(8).uppercase()
        } else {
            "SV-" + profile.senderId.take(8).uppercase()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .widthIn(max = 420.dp)
                .fillMaxWidth(0.90f)
                .heightIn(max = 480.dp)
                .clip(RoundedCornerShape(20.dp))
                .background(Color(0xFF0F172A))
                .border(1.5.dp, Color(0xFF334155), RoundedCornerShape(20.dp))
                .padding(16.dp)
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
            ) {
                // Header Close Button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    IconButton(onClick = onDismiss, modifier = Modifier.size(28.dp)) {
                        Icon(imageVector = Icons.Default.Close, contentDescription = "Kapat", tint = Color.Gray)
                    }
                }

                // User Avatar
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(avatarColor)
                        .border(2.dp, Color(0xFFFFD700), CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = profile.senderName.take(1).uppercase(),
                        color = Color.White,
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // User Nickname
                Text(
                    text = profile.senderName,
                    color = Color.White,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )

                // Badge Tag (if any)
                profile.userBadge?.let { badgeText ->
                    val style = getBadgeStyle(badgeText)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .background(style.backgroundColor)
                            .border(1.dp, style.borderColor, RoundedCornerShape(8.dp))
                            .padding(horizontal = 10.dp, vertical = 3.dp)
                    ) {
                        Text(text = badgeText, color = style.textColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                }

                HorizontalDivider(color = Color(0xFF334155), thickness = 1.dp)

                // Unique User ID (Single-tap copy)
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(text = "Benzersiz Kullanıcı Kimliği (ID):", color = Color.Gray, fontSize = 11.sp)

                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1E293B))
                            .border(1.dp, if (copyToast) Color(0xFF10B981) else Color(0xFFFFD700), RoundedCornerShape(10.dp))
                            .clickable {
                                clipboardManager.setText(AnnotatedString(profile.senderId))
                                copyToast = true
                            }
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (copyToast) "📋 Kopyalandı!" else displayId,
                                color = if (copyToast) Color(0xFF10B981) else Color(0xFFFFD700),
                                fontSize = 14.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Icon(imageVector = Icons.Default.ContentCopy, contentDescription = "Kopyala", tint = Color.Gray, modifier = Modifier.size(16.dp))
                        }
                    }
                }

                // Account Creation Date
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(text = "Hesap Oluşturma Tarihi:", color = Color.Gray, fontSize = 11.sp)
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0xFF1E293B))
                            .padding(horizontal = 12.dp, vertical = 10.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(imageVector = Icons.Default.CalendarToday, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(14.dp))
                            Text(text = createdDateStr, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        }
                    }
                }

                // Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (isSelf) {
                        Button(
                            onClick = { onDismiss(); onChangeNickname() },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("✏️ Rumuzu Değiştir", color = Color.White, fontWeight = FontWeight.Bold)
                        }
                    } else {
                        Button(
                            onClick = { onDismiss(); onOpenDm(profile.senderId, profile.senderName) },
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2563EB)),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("💬 Özel Mesaj Gönder", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        }
                    }
                }
            }
        }
    }

    LaunchedEffect(copyToast) {
        if (copyToast) {
            kotlinx.coroutines.delay(2000L)
            copyToast = false
        }
    }
}

private data class BadgeStyle(val backgroundColor: Color, val borderColor: Color, val textColor: Color)

private fun getBadgeStyle(badgeText: String): BadgeStyle {
    return when {
        badgeText.contains("Kurucu") -> BadgeStyle(
            backgroundColor = Color(0xFFFFD700).copy(alpha = 0.25f),
            borderColor = Color(0xFFFFD700),
            textColor = Color(0xFFFFD700)
        )
        badgeText.contains("Sınırsız") -> BadgeStyle(
            backgroundColor = Color(0xFFF59E0B).copy(alpha = 0.25f),
            borderColor = Color(0xFFFBBF24),
            textColor = Color(0xFFFDE68A)
        )
        badgeText.contains("Yıllık") || badgeText.contains("Diamond") -> BadgeStyle(
            backgroundColor = Color(0xFF06B6D4).copy(alpha = 0.25f),
            borderColor = Color(0xFF38BDF8),
            textColor = Color(0xFFE0F2FE)
        )
        badgeText.contains("VIP") -> BadgeStyle(
            backgroundColor = Color(0xFFA855F7).copy(alpha = 0.25f),
            borderColor = Color(0xFFC084FC),
            textColor = Color(0xFFE9D5FF)
        )
        badgeText.contains("Moderatör") -> BadgeStyle(
            backgroundColor = Color(0xFF10B981).copy(alpha = 0.25f),
            borderColor = Color(0xFF34D399),
            textColor = Color(0xFFA7F3D0)
        )
        badgeText.contains("Ücretsiz") || badgeText.contains("Free") || badgeText.contains("Deneme") -> BadgeStyle(
            backgroundColor = Color(0xFF0D9488).copy(alpha = 0.25f),
            borderColor = Color(0xFF2DD4BF),
            textColor = Color(0xFFCCFBF1)
        )
        badgeText.contains("Lideri") -> BadgeStyle(
            backgroundColor = Color(0xFFF97316).copy(alpha = 0.25f),
            borderColor = Color(0xFFFB923C),
            textColor = Color(0xFFFFEDD5)
        )
        badgeText.contains("Sinema") -> BadgeStyle(
            backgroundColor = Color(0xFFF43F5E).copy(alpha = 0.25f),
            borderColor = Color(0xFFFB7185),
            textColor = Color(0xFFFFE4E6)
        )
        else -> BadgeStyle(
            backgroundColor = Color(0xFF3B82F6).copy(alpha = 0.2f),
            borderColor = Color(0xFF60A5FA),
            textColor = Color(0xFFDBEAFE)
        )
    }
}

@Composable
private fun SpaciousChatBubble(
    message: ChatMessage,
    isMe: Boolean,
    onMessageClick: () -> Unit,
    onAvatarClick: () -> Unit,
    onImageClick: (String) -> Unit,
    onReactionClick: (String) -> Unit
) {
    val isAdminMessage = message.userRole == UserRole.ADMIN || message.userBadge?.contains("Kurucu") == true
    val isLifetimeMessage = message.userBadge?.contains("Sınırsız") == true
    val isYearlyMessage = message.userBadge?.contains("Yıllık") == true

    val avatarColor = remember(message.avatarColorHex) {
        runCatching { Color(android.graphics.Color.parseColor(message.avatarColorHex)) }.getOrDefault(Color(0xFF3B82F6))
    }
    val timeFormatted = remember(message.timestamp) {
        SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(message.timestamp))
    }

    val badgeStyle = remember(message.userBadge) {
        message.userBadge?.let { getBadgeStyle(it) }
    }
    val badgeColor = badgeStyle?.borderColor ?: if (isAdminMessage) Color(0xFFFFD700) else null

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onMessageClick() },
        horizontalArrangement = if (isMe) Arrangement.End else Arrangement.Start,
        verticalAlignment = Alignment.Top
    ) {
        if (!isMe) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(CircleShape)
                    .background(if (isAdminMessage || isLifetimeMessage) Color(0xFF78350F) else if (isYearlyMessage) Color(0xFF0C4A6E) else (badgeColor ?: avatarColor))
                    .border(
                        width = if (badgeColor != null) 2.dp else 0.dp,
                        color = badgeColor ?: Color.Transparent,
                        shape = CircleShape
                    )
                    .clickable { onAvatarClick() },
                contentAlignment = Alignment.Center
            ) {
                if (isAdminMessage || isLifetimeMessage) {
                    Text(text = "👑", fontSize = 16.sp)
                } else if (isYearlyMessage) {
                    Text(text = "💎", fontSize = 16.sp)
                } else {
                    Text(
                        text = message.senderName.take(1).uppercase(),
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
        }

        Column(
            horizontalAlignment = if (isMe) Alignment.End else Alignment.Start
        ) {
            // Header Info (Name + Custom Badge Tag)
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .clickable { onAvatarClick() }
                    .padding(bottom = 2.dp, start = 4.dp, end = 4.dp)
            ) {
                Text(
                    text = message.senderName,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = badgeColor ?: (if (isMe) Color(0xFF60A5FA) else avatarColor)
                )

                // Custom User Badge Tag (e.g. 👍‘ Kurucu, ⭐ VIP)
                message.userBadge?.let { badgeText ->
                    val style = badgeStyle ?: getBadgeStyle(badgeText)
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(6.dp))
                            .background(style.backgroundColor)
                            .border(1.dp, style.borderColor, RoundedCornerShape(6.dp))
                            .padding(horizontal = 6.dp, vertical = 1.dp)
                    ) {
                        Text(
                            text = badgeText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold,
                            color = style.textColor
                        )
                    }
                }
            }

            // Message Bubble
            Box(
                modifier = Modifier
                    .widthIn(max = 600.dp)
                    .clip(
                        RoundedCornerShape(
                            topStart = 14.dp,
                            topEnd = 14.dp,
                            bottomStart = if (isMe) 14.dp else 3.dp,
                            bottomEnd = if (isMe) 3.dp else 14.dp
                        )
                    )
                    .background(
                        when {
                            isAdminMessage -> Color(0xFF1E1B4B)
                            isMe -> Color(0xFF1D4ED8)
                            else -> Color(0xFF1E293B)
                        }
                    )
                    .border(
                        width = if (badgeColor != null) 1.5.dp else 0.dp,
                        color = badgeColor ?: Color.Transparent,
                        shape = RoundedCornerShape(
                            topStart = 14.dp,
                            topEnd = 14.dp,
                            bottomStart = if (isMe) 14.dp else 3.dp,
                            bottomEnd = if (isMe) 3.dp else 14.dp
                        )
                    )
                    .padding(horizontal = 14.dp, vertical = 8.dp)
            ) {
                Column {
                    // Quoted Reply Preview
                    if (!message.replyToSender.isNullOrEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(bottom = 6.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(Color.Black.copy(alpha = 0.3f))
                                .border(1.dp, AppColors.Brand, RoundedCornerShape(6.dp))
                                .padding(6.dp)
                        ) {
                            Column {
                                Text(text = message.replyToSender ?: "", color = AppColors.Brand, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text(text = message.replyToText ?: "", color = Color.LightGray, fontSize = 12.sp, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            }
                        }
                    }

                    // Shared Image Preview
                    val imgUrl = message.imageUrl
                    if (!imgUrl.isNullOrBlank()) {
                        val path = remember(imgUrl) {
                            when {
                                imgUrl.startsWith("content://") || imgUrl.startsWith("http") -> imgUrl
                                imgUrl.startsWith("/") -> "file://$imgUrl"
                                else -> imgUrl
                            }
                        }
                        val imageModel = rememberCrossfadeImageModel(path)
                        Box(
                            modifier = Modifier
                                .widthIn(max = 260.dp)
                                .height(160.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color.Black.copy(alpha = 0.4f))
                                .clickable { onImageClick(imgUrl) }
                        ) {
                            AsyncImage(
                                model = imageModel,
                                contentDescription = "Paylaşılan Görsel",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = ContentScale.Crop
                            )
                            Box(
                                modifier = Modifier
                                    .align(Alignment.BottomEnd)
                                    .padding(6.dp)
                                    .clip(CircleShape)
                                    .background(Color.Black.copy(alpha = 0.6f))
                                    .padding(4.dp)
                            ) {
                                Icon(imageVector = Icons.Default.ZoomIn, contentDescription = "Büyüt", tint = Color.White, modifier = Modifier.size(14.dp))
                            }
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                    }

                    // Message Text with @mention highlighting
                    if (message.message.isNotBlank()) {
                        val mentionColor = Color(0xFF60A5FA)
                        val annotated = buildAnnotatedString {
                            val text = message.message
                            val mentionRegex = Regex("@([\\w\\u00C0-\\u024F]+)")
                            var lastIndex = 0
                            mentionRegex.findAll(text).forEach { match ->
                                if (match.range.first > lastIndex) {
                                    append(text.substring(lastIndex, match.range.first))
                                }
                                withStyle(SpanStyle(color = mentionColor, fontWeight = FontWeight.Bold)) {
                                    append(match.value)
                                }
                                lastIndex = match.range.last + 1
                            }
                            if (lastIndex < text.length) append(text.substring(lastIndex))
                        }
                        Text(text = annotated, color = Color.White, fontSize = 15.sp, lineHeight = 20.sp)
                    }

                    // Time Timestamp + Edited badge
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        modifier = Modifier.align(Alignment.End).padding(top = 2.dp)
                    ) {
                        if (message.isEdited) {
                            Text(text = "düzenlenmiş", color = Color.White.copy(alpha = 0.4f), fontSize = 9.sp)
                        }
                        Text(text = timeFormatted, color = Color.White.copy(alpha = 0.6f), fontSize = 10.sp)
                    }
                }
            }

            // Emoji Reactions Display Bar
            if (message.reactions.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(top = 2.dp, start = 4.dp, end = 4.dp)
                ) {
                    message.reactions.forEach { (emoji, count) ->
                        if (count > 0) {
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(Color(0xFF1E293B))
                                    .border(1.dp, Color(0xFF334155), RoundedCornerShape(12.dp))
                                    .clickable { onReactionClick(emoji) }
                                    .padding(horizontal = 6.dp, vertical = 2.dp)
                            ) {
                                Text(text = "$emoji $count", fontSize = 11.sp, color = Color.White)
                            }
                        }
                    }
                }
            }

            // "X kişi gördü" for announcement messages
            if (message.roomId == "duyurular" && message.seenBy.isNotEmpty()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(3.dp),
                    modifier = Modifier.padding(top = 2.dp, start = 4.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.People,
                        contentDescription = null,
                        tint = Color.Gray,
                        modifier = Modifier.size(11.dp)
                    )
                    Text(
                        text = "${message.seenBy.size} kişi gördü",
                        fontSize = 10.sp,
                        color = Color.Gray
                    )
                }
            }
        }
    }
}
