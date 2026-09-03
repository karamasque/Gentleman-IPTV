package com.kaynanamtv.app.ui.components.shell

import android.content.Context
import android.content.ContextWrapper
import androidx.activity.compose.BackHandler
import androidx.annotation.StringRes
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.luminance
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.layout.RowScope
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Extension
import androidx.compose.material.icons.outlined.Forum
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Subscriptions
import androidx.compose.material.icons.outlined.Tv
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.LinearEasing
import androidx.compose.foundation.Canvas
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.zIndex
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.Border
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.Icon
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import com.kaynanamtv.app.R
import com.kaynanamtv.app.MainActivity
import com.kaynanamtv.app.navigation.toAppRoute
import com.kaynanamtv.app.navigation.Routes
import com.kaynanamtv.app.ui.design.AppColors
import com.kaynanamtv.app.ui.design.AppMotion
import com.kaynanamtv.app.ui.design.FocusSpec
import com.kaynanamtv.app.ui.interaction.mouseClickable
import com.kaynanamtv.app.ui.interaction.rememberTvInteractionSounds
import com.kaynanamtv.app.ui.interaction.TvIconButton
import com.kaynanamtv.app.ui.design.LocalAppShapes
import com.kaynanamtv.app.ui.design.LocalAppSpacing
import com.kaynanamtv.domain.model.AppTopLevelDestination
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.hilt.android.EntryPointAccessors
import androidx.compose.runtime.collectAsState
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.widthIn
import com.kaynanamtv.app.ui.theme.Primary

@EntryPoint
@InstallIn(SingletonComponent::class)
interface AppShellEntryPoint {
    fun syncManager(): com.kaynanamtv.data.sync.SyncManager
    fun providerDao(): com.kaynanamtv.data.local.dao.ProviderDao
    fun communityChatRepository(): com.kaynanamtv.data.repository.CommunityChatRepository
}

@Composable
private fun BoxScope.FloatingSyncIndicator(
    syncManager: com.kaynanamtv.data.sync.SyncManager,
    providerDao: com.kaynanamtv.data.local.dao.ProviderDao
) {
    val context = LocalContext.current
    val providers by providerDao.getAll().collectAsState(initial = emptyList())
    val syncStates by syncManager.syncStatesByProvider.collectAsState()

    val syncingInfos = remember(providers, syncStates) {
        syncStates.entries.mapNotNull { (providerId, state) ->
            if (state is com.kaynanamtv.domain.model.SyncState.Syncing) {
                val providerName = providers.firstOrNull { it.id == providerId }?.name ?: "IPTV Hesabı"
                providerName to state.phase
            } else {
                null
            }
        }
    }

    val hasActiveSync = syncingInfos.isNotEmpty()
    var isDismissed by remember { mutableStateOf(false) }

    LaunchedEffect(hasActiveSync) {
        if (!hasActiveSync) {
            isDismissed = false
        }
    }

    androidx.compose.animation.AnimatedVisibility(
        visible = syncingInfos.isNotEmpty() && !isDismissed,
        enter = androidx.compose.animation.fadeIn() + androidx.compose.animation.slideInHorizontally(
            initialOffsetX = { it }
        ),
        exit = androidx.compose.animation.fadeOut() + androidx.compose.animation.slideOutHorizontally(
            targetOffsetX = { it }
        ),
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .padding(bottom = 24.dp, end = 24.dp)
            .zIndex(100f)
    ) {
        androidx.compose.material3.Surface(
            shape = RoundedCornerShape(24.dp),
            color = Color(0xCC070A13),
            border = BorderStroke(
                width = 1.dp,
                color = AppColors.Brand.copy(alpha = 0.4f)
            ),
            modifier = Modifier.widthIn(max = 300.dp)
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                androidx.compose.material3.CircularProgressIndicator(
                    color = AppColors.Brand,
                    strokeWidth = 2.dp,
                    modifier = Modifier.size(16.dp)
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(1.dp)
                ) {
                    val activeSync = syncingInfos.firstOrNull()
                    val providerTitle = activeSync?.first?.let { "$it Güncelleniyor" } ?: "IPTV Güncelleniyor"
                    val phaseText = activeSync?.second?.takeIf { it.isNotBlank() } ?: "Veriler eşitleniyor..."

                    Text(
                        text = providerTitle,
                        style = MaterialTheme.typography.labelMedium.copy(
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        ),
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = phaseText,
                        style = MaterialTheme.typography.bodySmall,
                        color = AppColors.TextSecondary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Close / Dismiss button (✕)
                androidx.compose.material3.IconButton(
                    onClick = { isDismissed = true },
                    modifier = Modifier.size(22.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Kapat",
                        tint = Color.Gray,
                        modifier = Modifier.size(14.dp)
                    )
                }
            }
        }
    }
}

enum class AppNavigationChrome {
    Rail,
    TopBar
}

@Composable
fun AnimatedIptvBackground(modifier: Modifier = Modifier) {
    val tier = com.kaynanamtv.app.ui.theme.LocalVisualEffectsProfile.current.tier
    if (tier.isBackgroundAnimated) {
        DynamicIptvBackground(modifier)
    } else {
        StaticIptvBackground(modifier)
    }
}

@Composable
fun StaticIptvBackground(modifier: Modifier = Modifier) {
    val baseColor = AppColors.Canvas
    val brandGlow = AppColors.Brand.copy(alpha = 0.15f)
    val cyanGlow = AppColors.NeonCyan.copy(alpha = 0.14f)
    val heroOverlay = AppColors.HeroTop.copy(alpha = 0.5f)

    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(color = baseColor)

        val r1 = size.minDimension * 0.85f
        val cx1 = size.width * 0.2f
        val cy1 = size.height * 0.25f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(brandGlow, Color.Transparent),
                center = androidx.compose.ui.geometry.Offset(cx1, cy1),
                radius = r1
            ),
            radius = r1,
            center = androidx.compose.ui.geometry.Offset(cx1, cy1)
        )

        val r2 = size.minDimension * 0.95f
        val cx2 = size.width * 0.8f
        val cy2 = size.height * 0.75f
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(cyanGlow, Color.Transparent),
                center = androidx.compose.ui.geometry.Offset(cx2, cy2),
                radius = r2
            ),
            radius = r2,
            center = androidx.compose.ui.geometry.Offset(cx2, cy2)
        )

        drawRect(
            brush = Brush.verticalGradient(
                colors = listOf(heroOverlay, Color.Transparent, baseColor)
            )
        )
    }
}

@Composable
fun DynamicIptvBackground(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "background")

    val xOffset1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(22000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "x1"
    )
    val yOffset1 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(18000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "y1"
    )

    val xOffset2 by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(26000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "x2"
    )
    val yOffset2 by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "y2"
    )

    val baseColor = AppColors.Canvas
    val purpleGlow = AppColors.Brand.copy(alpha = 0.12f)
    val cyanGlow = AppColors.NeonCyan.copy(alpha = 0.12f)

    Canvas(modifier = modifier.fillMaxSize()) {
        drawRect(color = baseColor)

        val r1 = size.minDimension * 0.75f
        val cx1 = size.width * (0.15f + 0.7f * xOffset1)
        val cy1 = size.height * (0.15f + 0.7f * yOffset1)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(purpleGlow, Color.Transparent),
                center = androidx.compose.ui.geometry.Offset(cx1, cy1),
                radius = r1
            ),
            radius = r1,
            center = androidx.compose.ui.geometry.Offset(cx1, cy1)
        )

        val r2 = size.minDimension * 0.85f
        val cx2 = size.width * (0.1f + 0.8f * xOffset2)
        val cy2 = size.height * (0.1f + 0.8f * yOffset2)
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(cyanGlow, Color.Transparent),
                center = androidx.compose.ui.geometry.Offset(cx2, cy2),
                radius = r2
            ),
            radius = r2,
            center = androidx.compose.ui.geometry.Offset(cx2, cy2)
        )
    }
}

@Composable
fun AppScreenScaffold(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    navigationChrome: AppNavigationChrome = AppNavigationChrome.Rail,
    topBarVisible: Boolean = true,
    compactHeader: Boolean = false,
    showScreenHeader: Boolean = true,
    header: (@Composable ColumnScope.() -> Unit)? = null,
    topBarActions: (@Composable RowScope.() -> Unit)? = null,
    contentPadding: PaddingValues = PaddingValues(),
    content: @Composable ColumnScope.() -> Unit
) {
    val spacing = LocalAppSpacing.current
    val context = LocalContext.current
    val entryPoint = remember(context) {
        EntryPointAccessors.fromApplication(
            context.applicationContext,
            AppShellEntryPoint::class.java
        )
    }

    val isBanned by remember(entryPoint) {
        entryPoint.communityChatRepository().observeBannedStatus()
    }.collectAsState(initial = false)
    val deviceId = remember(entryPoint) {
        entryPoint.communityChatRepository().getDeviceSenderId()
    }

    if (isBanned) {
        com.kaynanamtv.app.ui.screens.ban.GlobalBanScreen(
            deviceId = deviceId,
            onRecheck = {}
        )
        return
    }

    val isHomeScreen = currentRoute == com.kaynanamtv.app.navigation.Routes.HOME || currentRoute == "home"
    var showExitConfirmDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = true) {
        if (isHomeScreen) {
            showExitConfirmDialog = true
        } else {
            onNavigate(com.kaynanamtv.app.navigation.Routes.HOME)
        }
    }

    Box(
        modifier = modifier.fillMaxSize()
    ) {
        AnimatedIptvBackground()
        if (navigationChrome == AppNavigationChrome.Rail) {
            Row(modifier = Modifier.fillMaxSize()) {
                DestinationRail(
                    currentRoute = currentRoute,
                    onNavigate = onNavigate,
                    modifier = Modifier
                        .fillMaxHeight()
                        .width(spacing.railWidth)
                )

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(
                            start = spacing.lg,
                            end = spacing.screenGutter,
                            top = spacing.safeTop,
                            bottom = spacing.safeBottom
                        )
                ) {
                    if (showScreenHeader) {
                        AppScreenHeader(
                            title = title,
                            subtitle = subtitle,
                            modifier = Modifier.fillMaxWidth(),
                            compact = compactHeader
                        )
                        if (header != null) {
                            Spacer(modifier = Modifier.height(spacing.lg))
                            header()
                        }
                        Spacer(modifier = Modifier.height(spacing.lg))
                    } else if (header != null) {
                        header()
                    }
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .imePadding()
                            .padding(contentPadding)
                    ) {
                        content()
                    }
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(
                        horizontal = 14.dp,
                        vertical = 10.dp
                    )
            ) {
                if (topBarVisible) {
                    TopNavigationBar(
                        currentRoute = currentRoute,
                        onNavigate = onNavigate,
                        actions = topBarActions,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(10.dp))
                }
                if (showScreenHeader) {
                    AppScreenHeader(
                        title = title,
                        subtitle = subtitle,
                        modifier = Modifier.fillMaxWidth(),
                        compact = true
                    )
                    if (header != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        header()
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                } else if (header != null) {
                    header()
                }
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .imePadding()
                        .padding(contentPadding)
                ) {
                    content()
                }
            }
        }

        FloatingSyncIndicator(
            syncManager = entryPoint.syncManager(),
            providerDao = entryPoint.providerDao()
        )

        if (showExitConfirmDialog) {
            val activity = context.findActivity()
            androidx.compose.material3.AlertDialog(
                onDismissRequest = { showExitConfirmDialog = false },
                title = {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
                            contentDescription = null,
                            tint = AppColors.Brand,
                            modifier = Modifier.size(24.dp)
                        )
                        Text(
                            text = "Uygulamadan Çıkış",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                },
                text = {
                    Text(
                        text = "Uygulamadan çıkmak istediğinize emin misiniz?",
                        color = Color.LightGray,
                        fontSize = 15.sp
                    )
                },
                confirmButton = {
                    var isFocused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .onFocusChanged { isFocused = it.isFocused }
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isFocused) Color(0xFFDC2626) else Color(0xFFEF4444))
                            .clickable {
                                showExitConfirmDialog = false
                                activity?.finishAndRemoveTask()
                                activity?.finishAffinity()
                            }
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Evet",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                },
                dismissButton = {
                    var isFocused by remember { mutableStateOf(false) }
                    Box(
                        modifier = Modifier
                            .onFocusChanged { isFocused = it.isFocused }
                            .clip(RoundedCornerShape(10.dp))
                            .background(if (isFocused) AppColors.Brand else Color(0xFF334155))
                            .clickable { showExitConfirmDialog = false }
                            .padding(horizontal = 20.dp, vertical = 8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Hayır",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                    }
                },
                containerColor = Color(0xFF1E293B)
            )
        }
    }
}

@Composable
fun AppScreenHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    compact: Boolean = false
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        if (!eyebrow.isNullOrBlank()) {
            Text(
                text = eyebrow,
                style = MaterialTheme.typography.labelMedium,
                color = AppColors.Brand
            )
        }
        Text(
            text = title,
            style = if (compact) MaterialTheme.typography.titleMedium else MaterialTheme.typography.displaySmall,
            color = AppColors.TextPrimary,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis
        )
        if (!subtitle.isNullOrBlank()) {
            Text(
                text = subtitle,
                style = if (compact) MaterialTheme.typography.bodySmall else MaterialTheme.typography.bodyLarge,
                color = AppColors.TextSecondary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun TopNavigationBar(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    actions: (@Composable RowScope.() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val items = rememberDestinationItems()
    val scrollState = rememberScrollState()
    val focusRequesters = remember { mutableMapOf<String, FocusRequester>() }

    Surface(
        modifier = modifier.focusProperties {
            onEnter = {
                val activeItem = findActiveDestinationItem(items, currentRoute)
                focusRequesters[activeItem?.route] ?: FocusRequester.Default
            }
        },
        shape = RoundedCornerShape(20.dp),
        border = Border(
            border = BorderStroke(1.dp, Color(0xFF242834)),
            shape = RoundedCornerShape(20.dp)
        ),
        colors = SurfaceDefaults.colors(
            containerColor = Color(0xF212141A)
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .padding(horizontal = 14.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Brand Logo & Name
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(start = 2.dp, end = 10.dp)
            ) {
                androidx.compose.foundation.Image(
                    painter = androidx.compose.ui.res.painterResource(id = R.drawable.kaynanamtv_brand_logo),
                    contentDescription = stringResource(R.string.app_name),
                    contentScale = androidx.compose.ui.layout.ContentScale.Fit,
                    modifier = Modifier.size(36.dp)
                )
                Text(
                    text = "KaynanamTV",
                    style = MaterialTheme.typography.titleMedium.copy(
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.3.sp
                    ),
                    color = AppColors.TextPrimary
                )
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            Brush.horizontalGradient(
                                colors = listOf(Color(0xFF9333EA), Color(0xFFE11D48))
                            )
                        )
                        .padding(horizontal = 7.dp, vertical = 3.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "CW-R4B",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // Navigation Tabs with smooth horizontal scroll support for all form factors
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(scrollState)
                    .padding(horizontal = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                items.forEachIndexed { index, item ->
                    val requester = focusRequesters.getOrPut(item.route) { FocusRequester() }
                    TopNavigationButton(
                        label = stringResource(item.labelRes),
                        icon = item.icon,
                        accentColor = item.accentColor,
                        selected = currentRoute.startsWith(item.route),
                        focusRequester = requester,
                        modifier = if (index == 0) {
                            Modifier.focusProperties {
                                left = FocusRequester.Cancel
                                up = FocusRequester.Cancel
                            }
                        } else {
                            Modifier.focusProperties {
                                up = FocusRequester.Cancel
                            }
                        },
                        onClick = {
                            if (!currentRoute.startsWith(item.route)) {
                                onNavigate(item.route)
                            }
                        }
                    )
                }
            }

            if (actions != null) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(start = 6.dp),
                    content = actions
                )
            }
        }
    }
}

@Composable
fun AppTopBarCloseAction(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    contentDescription: String = stringResource(R.string.settings_close_app)
) {
    TvIconButton(
        onClick = onClick,
        modifier = modifier,
        colors = androidx.tv.material3.IconButtonDefaults.colors(
            containerColor = Color.Transparent,
            focusedContainerColor = AppColors.SurfaceEmphasis,
            contentColor = AppColors.TextSecondary,
            focusedContentColor = AppColors.TextPrimary
        ),
        border = androidx.tv.material3.IconButtonDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(FocusSpec.BorderWidth, AppColors.Focus),
                shape = RoundedCornerShape(14.dp)
            )
        )
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ExitToApp,
            contentDescription = contentDescription,
            modifier = Modifier.size(18.dp)
        )
    }
}

@Composable
private fun TopNavigationButton(
    label: String,
    icon: ImageVector,
    accentColor: Color,
    selected: Boolean,
    focusRequester: FocusRequester,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    var isFocused by remember { mutableStateOf(false) }
    val sounds = rememberTvInteractionSounds()
    val tier = com.kaynanamtv.app.ui.theme.LocalVisualEffectsProfile.current.tier
    val scale = if (tier.isFocusScaleEnabled) {
        animateFloatAsState(
            targetValue = if (isFocused) 1.04f else 1f,
            animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
            label = "topNavScale"
        ).value
    } else {
        1f
    }

    Column(
        modifier = modifier
            .zIndex(if (isFocused) 2f else if (selected) 1f else 0f)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            onClick = {
                sounds.playSelect()
                onClick()
            },
            modifier = Modifier
                .focusRequester(focusRequester)
                .mouseClickable(
                    focusRequester = focusRequester,
                    onClick = {
                        sounds.playSelect()
                        onClick()
                    }
                )
                .onFocusChanged {
                    if (it.isFocused && !isFocused) {
                        sounds.playNavigate()
                    }
                    isFocused = it.isFocused
                },
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(12.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = if (isFocused) {
                    if (selected) accentColor.copy(alpha = 0.24f) else accentColor.copy(alpha = 0.12f)
                } else if (selected) {
                    accentColor.copy(alpha = 0.14f)
                } else {
                    Color.Transparent
                },
                focusedContainerColor = if (selected) accentColor.copy(alpha = 0.24f) else accentColor.copy(alpha = 0.12f)
            ),
            border = ClickableSurfaceDefaults.border(
                border = if (selected && !isFocused) {
                    Border(
                        border = BorderStroke(1.dp, accentColor.copy(alpha = 0.30f)),
                        shape = RoundedCornerShape(12.dp)
                    )
                } else {
                    Border.None
                },
                focusedBorder = Border(
                    border = BorderStroke(1.5.dp, AppColors.Focus),
                    shape = RoundedCornerShape(12.dp)
                )
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val iconTint = if (selected || isFocused) {
                    accentColor
                } else {
                    accentColor.copy(alpha = 0.85f)
                }

                val textTint = if (selected || isFocused) {
                    AppColors.TextPrimary
                } else {
                    AppColors.TextSecondary
                }

                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = iconTint,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium.copy(
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium
                    ),
                    color = textTint,
                    maxLines = 1
                )
            }
        }

        // Active indicator bar at bottom
        Box(
            modifier = Modifier
                .height(3.dp)
                .padding(top = 1.dp),
            contentAlignment = Alignment.Center
        ) {
            if (selected) {
                Box(
                    modifier = Modifier
                        .size(width = 18.dp, height = 2.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(accentColor)
                )
            }
        }
    }
}

@Composable
fun AppHeroHeader(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    eyebrow: String? = null,
    actions: (@Composable RowScope.() -> Unit)? = null,
    footer: (@Composable ColumnScope.() -> Unit)? = null
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(28.dp),
        colors = SurfaceDefaults.colors(containerColor = AppColors.SurfaceElevated)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(28.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            AppColors.Canvas,
                            AppColors.SurfaceAccent,
                            AppColors.SurfaceEmphasis
                        )
                    )
                )
                .padding(32.dp)
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(18.dp)) {
                AppScreenHeader(
                    title = title,
                    subtitle = subtitle,
                    eyebrow = eyebrow
                )
                if (actions != null) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        content = actions
                    )
                }
                if (footer != null) {
                    footer()
                }
            }
        }
    }
}

@Composable
fun AppSectionHeader(
    title: String,
    subtitle: String? = null,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    onActionClick: (() -> Unit)? = null,
    actionContentColor: Color = AppColors.TextTertiary
) {
    val shapes = LocalAppShapes.current
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = if (onActionClick != null && !actionLabel.isNullOrBlank()) Modifier.weight(1f) else Modifier,
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = AppColors.TextPrimary,
                modifier = Modifier.semantics { heading() }
            )
            if (!subtitle.isNullOrBlank()) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = AppColors.TextTertiary
                )
            }
        }

        if (onActionClick != null && !actionLabel.isNullOrBlank()) {
            val actionFocusRequester = remember { FocusRequester() }
            Surface(
                onClick = onActionClick,
                modifier = Modifier
                    .focusRequester(actionFocusRequester)
                    .mouseClickable(
                        focusRequester = actionFocusRequester,
                        onClick = onActionClick
                    ),
                shape = ClickableSurfaceDefaults.shape(shapes.pill),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = AppColors.Brand.copy(alpha = 0.12f),
                    focusedContainerColor = AppColors.Brand.copy(alpha = 0.22f),
                    contentColor = actionContentColor
                )
            ) {
                Text(
                    text = actionLabel,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
fun StatusPill(
    label: String,
    modifier: Modifier = Modifier,
    containerColor: Color = AppColors.SurfaceEmphasis,
    contentColor: Color = if (containerColor.luminance() > 0.5f) Color(0xFF0A0E1A) else AppColors.TextPrimary,
    cornerRadius: Dp = 999.dp,
    horizontalPadding: Dp = 10.dp,
    verticalPadding: Dp = 4.dp
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(containerColor)
            .padding(horizontal = horizontalPadding, vertical = verticalPadding)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = contentColor
        )
    }
}

@Composable
fun AppMessageState(
    title: String,
    subtitle: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    shape: RoundedCornerShape? = null,
    containerBrush: Brush? = null,
    borderColor: Color? = null,
    titleStyle: TextStyle = MaterialTheme.typography.titleLarge,
    subtitleStyle: TextStyle = MaterialTheme.typography.bodySmall,
    titleColor: Color = AppColors.TextPrimary,
    subtitleColor: Color = AppColors.TextSecondary,
    titleTextAlign: TextAlign = TextAlign.Start,
    subtitleTextAlign: TextAlign = TextAlign.Start
) {
    val resolvedShape = shape ?: LocalAppShapes.current.large
    Surface(
        modifier = modifier.semantics { liveRegion = LiveRegionMode.Polite },
        shape = resolvedShape,
        border = Border(
            border = BorderStroke(
                width = if (borderColor != null) 1.dp else 0.dp,
                color = borderColor ?: Color.Transparent
            ),
            shape = resolvedShape
        ),
        colors = SurfaceDefaults.colors(containerColor = AppColors.SurfaceElevated)
    ) {
        Column(
            modifier = Modifier
                .then(if (containerBrush != null) Modifier.background(containerBrush) else Modifier)
                .padding(horizontal = 18.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = title,
                style = titleStyle,
                color = titleColor,
                textAlign = titleTextAlign,
                modifier = Modifier.fillMaxWidth()
            )
            Text(
                text = subtitle,
                style = subtitleStyle,
                color = subtitleColor,
                textAlign = subtitleTextAlign,
                modifier = Modifier.fillMaxWidth()
            )
            if (action != null) {
                Spacer(modifier = Modifier.height(8.dp))
                action()
            }
        }
    }
}

@Composable
fun LoadMoreCard(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shapes = LocalAppShapes.current
    val focusRequester = remember { FocusRequester() }
    Surface(
        onClick = onClick,
        modifier = modifier
            .focusRequester(focusRequester)
            .mouseClickable(
                focusRequester = focusRequester,
                onClick = onClick
            ),
        shape = ClickableSurfaceDefaults.shape(shapes.medium),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = AppColors.SurfaceElevated,
            focusedContainerColor = AppColors.SurfaceEmphasis
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(FocusSpec.BorderWidth, AppColors.Focus),
                shape = shapes.medium
            )
        )
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Info,
                contentDescription = label,
                tint = AppColors.Brand,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.titleMedium,
                color = AppColors.TextPrimary
            )
        }
    }
}

@Composable
fun ContentMetadataStrip(
    values: List<String>,
    modifier: Modifier = Modifier
) {
    val filteredValues = values.filter { it.isNotBlank() }
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        filteredValues.forEachIndexed { index, value ->
            Text(
                text = value,
                style = MaterialTheme.typography.labelMedium,
                color = AppColors.TextSecondary
            )
            if (index < filteredValues.lastIndex) {
                Box(
                    modifier = Modifier
                        .size(4.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(AppColors.TextTertiary)
                )
            }
        }
    }
}

@OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class)
@Composable
private fun DestinationRail(
    currentRoute: String,
    onNavigate: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val spacing = LocalAppSpacing.current
    val items = rememberDestinationItems()
    val focusRequesters = remember { mutableMapOf<String, FocusRequester>() }

    Box(
        modifier = modifier
            .padding(start = spacing.lg, top = spacing.safeTop, bottom = spacing.safeBottom)
            .clip(RoundedCornerShape(28.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        AppColors.SurfaceElevated,
                        AppColors.Surface
                    )
                )
            )
            .focusProperties {
                onEnter = {
                    val activeItem = findActiveDestinationItem(items, currentRoute)
                    focusRequesters[activeItem?.route] ?: FocusRequester.Default
                }
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 8.dp, vertical = 20.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            items.forEach { item ->
                val requester = focusRequesters.getOrPut(item.route) { FocusRequester() }
                RailButton(
                    label = stringResource(item.labelRes),
                    icon = item.icon,
                    selected = currentRoute.startsWith(item.route),
                    modifier = Modifier.focusRequester(requester),
                    onClick = {
                        if (!currentRoute.startsWith(item.route)) {
                            onNavigate(item.route)
                        }
                    }
                )
            }
        }
    }
}

@Composable
private fun RailButton(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var isFocused by remember { mutableStateOf(false) }
    val focusRequester = remember { FocusRequester() }
    val scale by animateFloatAsState(
        targetValue = if (isFocused) 1.05f else 1f,
        animationSpec = tween(durationMillis = 200, easing = FastOutSlowInEasing),
        label = "railButtonScale"
    )

    Surface(
        onClick = onClick,
        modifier = modifier
            .focusRequester(focusRequester)
            .mouseClickable(
                focusRequester = focusRequester,
                onClick = onClick
            )
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .onFocusChanged { isFocused = it.isFocused },
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = if (isFocused) {
                if (selected) AppColors.Brand.copy(alpha = 0.28f) else AppColors.SurfaceEmphasis
            } else if (selected) {
                AppColors.Brand.copy(alpha = 0.16f)
            } else {
                Color.Transparent
            },
            focusedContainerColor = if (selected) AppColors.Brand.copy(alpha = 0.28f) else AppColors.SurfaceEmphasis
        ),
        border = ClickableSurfaceDefaults.border(
            border = if (selected && !isFocused) {
                Border(
                    border = BorderStroke(1.dp, AppColors.Brand.copy(alpha = 0.35f)),
                    shape = RoundedCornerShape(16.dp)
                )
            } else {
                Border.None
            },
            focusedBorder = Border(
                border = BorderStroke(1.5.dp, AppColors.Focus),
                shape = RoundedCornerShape(16.dp)
            )
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            contentAlignment = Alignment.Center
        ) {
            val iconTint = if (selected) {
                AppColors.Brand
            } else if (isFocused) {
                AppColors.TextPrimary
            } else {
                AppColors.TextSecondary
            }
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )
        }
    }
}

private data class DestinationItem(
    val route: String,
    @param:StringRes val labelRes: Int,
    val icon: ImageVector,
    val accentColor: Color
)

private fun findActiveDestinationItem(
    items: List<DestinationItem>,
    currentRoute: String
): DestinationItem? =
    items
        .filter { currentRoute.startsWith(it.route) }
        .maxByOrNull { it.route.length }
        ?: items.firstOrNull { it.route == currentRoute }

private fun buildDestinationItems(): List<DestinationItem> =
    AppTopLevelDestination.defaultOrder.map { it.toDestinationItem() }

@Composable
private fun rememberDestinationItems(): List<DestinationItem> {
    val context = LocalContext.current
    val mainActivity = remember(context) { context.findMainActivity() }
    val configuredDestinations = mainActivity?.preferencesRepository?.appTopLevelDestinations
        ?.collectAsStateWithLifecycle(initialValue = AppTopLevelDestination.defaultOrder)
        ?.value
        ?: AppTopLevelDestination.defaultOrder
    return remember(configuredDestinations) {
        configuredDestinations.map { it.toDestinationItem() }
    }
}

private fun AppTopLevelDestination.toDestinationItem(): DestinationItem = when (this) {
    AppTopLevelDestination.HOME -> DestinationItem(Routes.HOME, R.string.nav_home, Icons.Outlined.Home, Color(0xFF7C83FF))
    AppTopLevelDestination.LIVE_TV -> DestinationItem(Routes.LIVE_TV, R.string.nav_live_tv, Icons.Outlined.Tv, Color(0xFF38BDF8))
    AppTopLevelDestination.MOVIES -> DestinationItem(Routes.MOVIES, R.string.nav_movies, Icons.Outlined.Movie, Color(0xFFFBBF24))
    AppTopLevelDestination.SERIES -> DestinationItem(Routes.SERIES, R.string.nav_series, Icons.Outlined.Subscriptions, Color(0xFFF472B6))
    AppTopLevelDestination.COMMUNITY_CHAT -> DestinationItem(Routes.COMMUNITY_CHAT, R.string.nav_chat, Icons.Outlined.Forum, Color(0xFF2DD4BF))
    AppTopLevelDestination.DOWNLOADS -> DestinationItem(Routes.DOWNLOADS, R.string.nav_downloads, Icons.Outlined.Download, Color(0xFF34D399))
    AppTopLevelDestination.GUIDE -> DestinationItem(Routes.EPG, R.string.nav_epg, Icons.Outlined.Info, Color(0xFFFB923C))
    AppTopLevelDestination.SEARCH -> DestinationItem(Routes.SEARCH, R.string.search_title, Icons.Outlined.Search, Color(0xFF60A5FA))
    AppTopLevelDestination.PLUGINS -> DestinationItem(Routes.PLUGINS, R.string.nav_plugins, Icons.Outlined.Extension, Color(0xFFC084FC))
    AppTopLevelDestination.SETTINGS -> DestinationItem(Routes.SETTINGS, R.string.nav_settings, Icons.Outlined.Settings, Color(0xFFA78BFA))
}

private fun Context.findMainActivity(): MainActivity? {
    var current: Context? = this
    while (current is ContextWrapper) {
        if (current is MainActivity) return current
        current = current.baseContext
    }
    return null
}

private tailrec fun Context.findActivity(): android.app.Activity? = when (this) {
    is android.app.Activity -> this
    is ContextWrapper -> baseContext.findActivity()
    else -> null
}
