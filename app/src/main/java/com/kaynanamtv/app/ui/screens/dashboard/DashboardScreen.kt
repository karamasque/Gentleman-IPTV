package com.kaynanamtv.app.ui.screens.dashboard

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.collectAsState
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import com.kaynanamtv.app.ui.interaction.mouseClickable
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.tv.material3.Border
import androidx.tv.material3.Button
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import coil3.compose.AsyncImage
import com.kaynanamtv.app.R
import com.kaynanamtv.app.device.rememberIsTelevisionDevice
import com.kaynanamtv.app.ui.components.ChannelLogoBadge
import com.kaynanamtv.app.navigation.Routes
import com.kaynanamtv.app.ui.components.CategoryRow
import com.kaynanamtv.app.ui.components.ChannelCard
import com.kaynanamtv.app.ui.components.ContinueWatchingRow
import com.kaynanamtv.app.ui.components.MovieCard
import com.kaynanamtv.app.ui.components.rememberCrossfadeImageModel
import com.kaynanamtv.app.ui.components.SeriesCard
import com.kaynanamtv.app.ui.components.shell.AppNavigationChrome
import com.kaynanamtv.app.ui.components.shell.AppHeroHeader
import com.kaynanamtv.app.ui.components.shell.AppScreenScaffold
import com.kaynanamtv.app.ui.components.shell.StatusPill
import com.kaynanamtv.app.ui.design.AppColors
import com.kaynanamtv.app.ui.time.LocalAppTimeFormat
import com.kaynanamtv.app.ui.time.createDateTimeFormat
import com.kaynanamtv.app.ui.design.AppColors.Brand as Primary
import com.kaynanamtv.app.ui.design.AppColors.Focus as FocusBorder
import com.kaynanamtv.app.ui.design.AppColors.SurfaceElevated as SurfaceElevated
import com.kaynanamtv.app.ui.design.AppColors.SurfaceEmphasis as SurfaceHighlight
import com.kaynanamtv.app.ui.design.AppColors.TextPrimary as OnBackground
import com.kaynanamtv.app.ui.design.AppColors.TextPrimary as TextPrimary
import com.kaynanamtv.app.ui.design.AppColors.TextTertiary as OnSurfaceDim
import com.kaynanamtv.app.ui.design.AppColors.TextTertiary as TextTertiary
import com.kaynanamtv.domain.model.AppHomeDashboardShelf
import com.kaynanamtv.domain.model.Channel
import com.kaynanamtv.domain.model.Movie
import com.kaynanamtv.domain.model.PlaybackHistory
import com.kaynanamtv.domain.model.Series
import androidx.compose.ui.res.stringResource
import androidx.compose.foundation.BorderStroke
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import com.kaynanamtv.app.ui.interaction.TvClickableSurface
import com.kaynanamtv.app.ui.interaction.TvButton
import com.kaynanamtv.app.ui.interaction.TvIconButton

@Composable
fun DashboardScreen(
    onNavigate: (String) -> Unit,
    onAddProvider: () -> Unit,
    onRecentChannelClick: (Channel, Long?) -> Unit,
    onFavoriteChannelClick: (Channel, Long?) -> Unit,
    onMovieClick: (Movie) -> Unit,
    onSeriesClick: (Series) -> Unit,
    onPlaybackHistoryClick: (PlaybackHistory) -> Unit,
    currentRoute: String,
    viewModel: DashboardViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val recordingChannelIds by viewModel.recordingChannelIds.collectAsStateWithLifecycle()
    val scheduledChannelIds by viewModel.scheduledChannelIds.collectAsStateWithLifecycle()
    val provider = uiState.provider
    val snackbarHostState = remember { SnackbarHostState() }
    var showHomeCustomizationDialog by remember { mutableStateOf(false) }
    var showStartupUpdateDialog by remember { mutableStateOf(true) }

    LaunchedEffect(uiState.userMessage) {
        uiState.userMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.userMessageShown()
        }
    }

    com.kaynanamtv.app.ui.components.AmbientGlowBackground(glowColor = Primary) {
        Box(modifier = Modifier.fillMaxSize()) {
        AppScreenScaffold(
            currentRoute = currentRoute,
            onNavigate = onNavigate,
            title = stringResource(R.string.nav_home),
            subtitle = provider?.name,
            navigationChrome = AppNavigationChrome.TopBar,
            compactHeader = true,
            showScreenHeader = false
        ) {
            if (provider == null) {
                if (uiState.isLoading) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator(color = Primary)
                    }
                } else {
                    EmptyDashboard(
                        onAddProvider = onAddProvider,
                        onOpenSettings = { onNavigate(Routes.SETTINGS) }
                    )
                }
                return@AppScreenScaffold
            }
            val orderedSections = rememberDashboardSections(uiState)
            val onContinueWatchingItemClick: (PlaybackHistory) -> Unit = { history ->
                val rawSeriesId = history.seriesId ?: history.contentId
                val presentedSeries = if (
                    history.contentType == com.kaynanamtv.domain.model.ContentType.SERIES ||
                    history.contentType == com.kaynanamtv.domain.model.ContentType.SERIES_EPISODE
                ) {
                    uiState.continueWatchingSeries.firstOrNull { series ->
                        series.rawSeriesIdsForNavigation().contains(rawSeriesId)
                    }
                } else {
                    null
                }
                if (presentedSeries != null) {
                    onSeriesClick(presentedSeries)
                } else {
                    onPlaybackHistoryClick(history)
                }
            }

            androidx.compose.foundation.lazy.LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(bottom = 28.dp)
            ) {
                if (uiState.isLoading && orderedSections.isEmpty()) {
                    item(key = "dashboard_loading") {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 28.dp, bottom = 12.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator(color = Primary)
                        }
                    }
                }
                if (uiState.providerWarnings.isNotEmpty()) {
                    item(key = "provider_warnings") {
                        DashboardProviderWarningCard(
                            warnings = uiState.providerWarnings,
                            onOpenSettings = { onNavigate(Routes.SETTINGS) }
                        )
                    }
                }
                uiState.updateNotice?.let { updateNotice ->
                    item(key = "update_notice") {
                        DashboardUpdateCard(
                            notice = updateNotice,
                            onOpenSettings = { onNavigate(Routes.SETTINGS) },
                            onInstallUpdate = viewModel::installDownloadedUpdate,
                            onDownloadAndInstall = viewModel::downloadAndInstallUpdate
                        )
                    }
                }
                if (orderedSections.isEmpty()) {
                    item(key = "dashboard_welcome_hero") {
                        DashboardWelcomeHeroCard(
                            providerName = provider.name,
                            onNavigate = onNavigate
                        )
                    }
                }
                items(orderedSections, key = { it.storageValue }) { section ->
                    when (section) {
                    AppHomeDashboardShelf.LIVE_SHORTCUTS -> DashboardShortcutRow(
                        title = stringResource(R.string.dashboard_live_shortcuts),
                        subtitle = stringResource(R.string.dashboard_live_shortcuts_subtitle),
                        shortcuts = uiState.liveShortcuts,
                        onShortcutClick = { shortcut ->
                            shortcut.categoryId?.let { categoryId ->
                                onNavigate(Routes.liveTv(categoryId))
                            } ?: onNavigate(Routes.LIVE_TV)
                        }
                    )

                    AppHomeDashboardShelf.FAVORITE_CHANNELS -> FavoriteChannelsRow(
                        title = stringResource(R.string.dashboard_favorite_channels),
                        channels = uiState.favoriteChannels,
                        onSeeAll = { onNavigate(Routes.liveTv(com.kaynanamtv.domain.model.VirtualCategoryIds.FAVORITES)) },
                        onChannelClick = { channel ->
                            onFavoriteChannelClick(channel, uiState.currentCombinedProfileId)
                        }
                    )

                    AppHomeDashboardShelf.RECENT_CHANNELS -> CategoryRow(
                        title = stringResource(R.string.dashboard_recent_channels),
                        items = uiState.recentChannels,
                        keySelector = { it.id },
                        onSeeAll = { onNavigate(Routes.liveTv(com.kaynanamtv.domain.model.VirtualCategoryIds.RECENT)) }
                    ) { channel ->
                        ChannelCard(
                            channel = channel,
                            isRecording = channel.id in recordingChannelIds,
                            isScheduledRecording = channel.id in scheduledChannelIds,
                            onClick = { onRecentChannelClick(channel, uiState.currentCombinedProfileId) }
                        )
                    }

                    AppHomeDashboardShelf.CONTINUE_WATCHING -> ContinueWatchingRow(
                        items = uiState.continueWatching,
                        onItemClick = onContinueWatchingItemClick
                    )

                    AppHomeDashboardShelf.RECENT_MOVIES -> CategoryRow(
                        title = stringResource(R.string.dashboard_recent_movies),
                        items = uiState.recentMovies,
                        keySelector = { it.id },
                        onSeeAll = { onNavigate(Routes.MOVIES) }
                    ) { movie ->
                        MovieCard(
                            movie = movie,
                            onClick = { onMovieClick(movie) }
                        )
                    }

                    AppHomeDashboardShelf.RECENT_SERIES -> CategoryRow(
                        title = stringResource(R.string.dashboard_recent_series),
                        items = uiState.recentSeries,
                        keySelector = { it.id },
                        onSeeAll = { onNavigate(Routes.SERIES) }
                    ) { series ->
                        SeriesCard(
                            series = series,
                            subtitle = series.releaseDate ?: stringResource(R.string.dashboard_updated_series_badge),
                            onClick = { onSeriesClick(series) }
                        )
                    }

                    AppHomeDashboardShelf.FAVORITE_MOVIES -> CategoryRow(
                        title = stringResource(R.string.dashboard_favorite_movies),
                        items = uiState.favoriteMovies,
                        keySelector = { it.id },
                        onSeeAll = { onNavigate(Routes.MOVIES) }
                    ) { movie ->
                        MovieCard(movie = movie, onClick = { onMovieClick(movie) })
                    }

                    AppHomeDashboardShelf.FAVORITE_SERIES -> CategoryRow(
                        title = stringResource(R.string.dashboard_favorite_series),
                        items = uiState.favoriteSeries,
                        keySelector = { it.id },
                        onSeeAll = { onNavigate(Routes.SERIES) }
                    ) { series ->
                        SeriesCard(
                            series = series,
                            subtitle = series.releaseDate ?: stringResource(R.string.dashboard_updated_series_badge),
                            onClick = { onSeriesClick(series) }
                        )
                    }

                    AppHomeDashboardShelf.CONTINUE_WATCHING_MOVIES -> ContinueWatchingRow(
                        title = stringResource(R.string.dashboard_continue_watching_movies),
                        items = uiState.continueWatchingMovies,
                        onItemClick = onPlaybackHistoryClick
                    )

                    AppHomeDashboardShelf.CONTINUE_WATCHING_SERIES -> ContinueWatchingRow(
                        title = stringResource(R.string.dashboard_continue_watching_series),
                        items = uiState.continueWatchingSeriesItems,
                        onItemClick = onContinueWatchingItemClick
                    )

                    AppHomeDashboardShelf.TOP_RATED_MOVIES -> CategoryRow(
                        title = stringResource(R.string.dashboard_top_rated_movies),
                        items = uiState.topRatedMovies,
                        keySelector = { it.id },
                        onSeeAll = { onNavigate(Routes.MOVIES) }
                    ) { movie ->
                        MovieCard(movie = movie, onClick = { onMovieClick(movie) })
                    }

                    AppHomeDashboardShelf.RECOMMENDED_MOVIES -> CategoryRow(
                        title = stringResource(R.string.dashboard_recommended_movies),
                        items = uiState.recommendedMovies,
                        keySelector = { it.id },
                        onSeeAll = { onNavigate(Routes.MOVIES) }
                    ) { movie ->
                        MovieCard(movie = movie, onClick = { onMovieClick(movie) })
                    }
                }
            }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp)
        )
    }

    if (showHomeCustomizationDialog) {
        DashboardShelfCustomizationDialog(
            currentShelves = uiState.homeDashboardShelves,
            onDismiss = { showHomeCustomizationDialog = false },
            onSave = { shelves ->
                viewModel.setHomeDashboardShelves(shelves)
                showHomeCustomizationDialog = false
            }
        )
    }

    if (showStartupUpdateDialog && uiState.updateNotice != null) {
        val notice = uiState.updateNotice!!

        DashboardStartupUpdateDialog(
            notice = notice,
            onDismiss = { showStartupUpdateDialog = false },
            onOpenSettings = {
                showStartupUpdateDialog = false
                onNavigate(Routes.SETTINGS)
            },
            onInstallUpdate = {
                showStartupUpdateDialog = false
                viewModel.installDownloadedUpdate()
            },
            onDownloadAndInstall = {
                // Keep dialog open during download so user sees real-time progress
                viewModel.downloadAndInstallUpdate()
            }
        )
    }
}
}

private fun formatUpdateBytes(bytes: Long): String {
    if (bytes <= 0) return "0 MB"
    val mb = bytes.toDouble() / (1024.0 * 1024.0)
    return String.format(java.util.Locale.US, "%.1f MB", mb)
}

private fun formatUpdateReleaseDateTime(publishedAt: String?): String {
    if (publishedAt.isNullOrBlank()) return ""
    return runCatching {
        val instant = java.time.Instant.parse(publishedAt)
        val zdt = instant.atZone(java.time.ZoneId.systemDefault())
        val formatter = java.time.format.DateTimeFormatter.ofPattern("dd MMMM yyyy, HH:mm", java.util.Locale("tr", "TR"))
        zdt.format(formatter)
    }.getOrElse {
        if (publishedAt.length >= 16) {
            publishedAt.substring(0, 16).replace("T", " ")
        } else {
            publishedAt
        }
    }
}

@Composable
private fun DashboardStartupUpdateDialog(
    notice: DashboardUpdateNotice,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
    onInstallUpdate: () -> Unit,
    onDownloadAndInstall: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape = RoundedCornerShape(24.dp),
            colors = SurfaceDefaults.colors(containerColor = Color(0xFF141824)),
            border = Border(BorderStroke(1.5.dp, Primary)),
            modifier = Modifier
                .widthIn(max = 540.dp)
                .fillMaxWidth(0.92f)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .background(Primary.copy(alpha = 0.16f), RoundedCornerShape(14.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (notice.isDownloading) "⏳" else "🚀",
                            style = MaterialTheme.typography.titleLarge
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Yeni Güncelleme Mevcut!",
                            style = MaterialTheme.typography.titleLarge,
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "KaynanamTV v${notice.latestVersionName}",
                            style = MaterialTheme.typography.bodyMedium,
                            color = Primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Metadata Card: Version, Date, Time
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF1C2234), RoundedCornerShape(12.dp))
                        .padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Mevcut Sürüm:",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceDim
                        )
                        Text(
                            text = "v${com.kaynanamtv.app.BuildConfig.VERSION_NAME}",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextPrimary,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Yeni Sürüm:",
                            style = MaterialTheme.typography.bodySmall,
                            color = OnSurfaceDim
                        )
                        Text(
                            text = "v${notice.latestVersionName}",
                            style = MaterialTheme.typography.bodySmall,
                            color = Primary,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    val formattedDate = formatUpdateReleaseDateTime(notice.publishedAt)
                    if (formattedDate.isNotBlank()) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Yayın Tarihi & Saati:",
                                style = MaterialTheme.typography.bodySmall,
                                color = OnSurfaceDim
                            )
                            Text(
                                text = formattedDate,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextPrimary,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }

                // Download Progress or Status Text
                if (notice.isDownloading) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "İndiriliyor... %${notice.progressPercentage}",
                                style = MaterialTheme.typography.bodyMedium,
                                color = Primary,
                                fontWeight = FontWeight.Bold
                            )
                            if (notice.totalBytes > 0) {
                                Text(
                                    text = "${formatUpdateBytes(notice.downloadedBytes)} / ${formatUpdateBytes(notice.totalBytes)}",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = OnSurfaceDim
                                )
                            }
                        }
                        LinearProgressIndicator(
                            progress = { notice.progressPercentage / 100f },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .background(Color.White.copy(alpha = 0.1f), RoundedCornerShape(4.dp)),
                            color = Primary,
                            trackColor = Color.White.copy(alpha = 0.1f)
                        )
                    }
                } else if (notice.installReady) {
                    Text(
                        text = "✅ İndirme tamamlandı! Güncellemeyi kurmaya hazırsınız.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFF4CAF50),
                        fontWeight = FontWeight.SemiBold
                    )
                } else if (notice.installPermissionRequired) {
                    Text(
                        text = "⚠️ Güncellemeyi yüklemek için harici uygulama yükleme izni vermelisiniz.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Color(0xFFFFC107)
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp, Alignment.End),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    TvButton(
                        onClick = onDismiss,
                        colors = ButtonDefaults.colors(
                            containerColor = SurfaceElevated,
                            focusedContainerColor = SurfaceHighlight,
                            contentColor = TextPrimary
                        )
                    ) {
                        Text(stringResource(R.string.epg_recording_conflict_cancel))
                    }

                    if (notice.isDownloading) {
                        TvButton(
                            onClick = {},
                            enabled = false,
                            colors = ButtonDefaults.colors(
                                containerColor = Primary.copy(alpha = 0.5f),
                                disabledContainerColor = Primary.copy(alpha = 0.4f),
                                disabledContentColor = TextPrimary
                            )
                        ) {
                            Text("İndiriliyor... %${notice.progressPercentage}")
                        }
                    } else {
                        DashboardActionButton(
                            label = if (notice.installPermissionRequired) {
                                "Yükleme İzni Ver ve Kur"
                            } else if (notice.installReady) {
                                "Güncellemeyi Kur"
                            } else {
                                "Güncellemeyi İndir"
                            },
                            onClick = {
                                if (notice.installReady || notice.installPermissionRequired) {
                                    onInstallUpdate()
                                } else {
                                    onDownloadAndInstall()
                                }
                            }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DashboardHero(
    providerName: String,
    feature: DashboardFeature,
    stats: DashboardStats,
    onOpenLiveTv: () -> Unit,
    onOpenGuide: () -> Unit,
    onOpenSearch: () -> Unit,
    onOpenSavedLibrary: () -> Unit,
    onFeatureAction: () -> Unit
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val isTelevisionDevice = rememberIsTelevisionDevice()
    val heroHeight = when {
        screenWidth < 700.dp -> 176.dp
        !isTelevisionDevice && screenWidth < 1280.dp -> 196.dp
        else -> 220.dp
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp)
    ) {
        if (!feature.artworkUrl.isNullOrBlank()) {
            AsyncImage(
                model = rememberCrossfadeImageModel(feature.artworkUrl),
                contentDescription = feature.title,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(heroHeight)
                    .clip(RoundedCornerShape(28.dp))
            )
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(heroHeight)
                    .clip(RoundedCornerShape(28.dp))
                    .background(
                        Brush.horizontalGradient(
                            colors = listOf(
                                Color.Black.copy(alpha = 0.88f),
                                Color.Black.copy(alpha = 0.72f),
                                Color.Black.copy(alpha = 0.34f)
                            )
                        )
                    )
            )
        }

        AppHeroHeader(
            eyebrow = providerName,
            title = feature.title.ifBlank { stringResource(R.string.dashboard_title) },
            subtitle = feature.summary.ifBlank { stringResource(R.string.dashboard_subtitle, providerName) },
            modifier = Modifier
                .fillMaxWidth()
                .height(heroHeight),
            footer = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        StatusPill(label = stringResource(R.string.nav_live_tv), containerColor = AppColors.BrandMuted)
                        StatusPill(label = stringResource(R.string.nav_epg), containerColor = AppColors.SurfaceEmphasis)
                        StatusPill(label = stringResource(R.string.favorites_title), containerColor = AppColors.Warning, contentColor = Color.Black)
                    }
                    DashboardStatRow(stats = stats)
                }
            },
            actions = {
                DashboardActionButton(label = stringResource(R.string.nav_live_tv), onClick = onOpenLiveTv)
                DashboardActionButton(label = stringResource(R.string.nav_epg), onClick = onOpenGuide)
                DashboardActionButton(label = stringResource(R.string.dashboard_search_library), onClick = onOpenSearch)
                DashboardActionButton(label = stringResource(R.string.favorites_title), onClick = onOpenSavedLibrary)
                if (feature.actionLabel.isNotBlank()) {
                    DashboardActionButton(
                        label = feature.actionLabel,
                        onClick = onFeatureAction
                    )
                }
            }
        )
    }
}

private fun Series.rawSeriesIdsForNavigation(): List<Long> =
    variants.map { it.rawSeriesId }.ifEmpty { listOf(selectedVariantId ?: id) }

@Composable
private fun DashboardStatRow(
    stats: DashboardStats
) {
    val statItems = listOf(
        stringResource(R.string.dashboard_stat_live, stats.liveChannelCount),
        stringResource(R.string.dashboard_stat_favorites, stats.favoriteChannelCount),
        stringResource(R.string.dashboard_stat_recent, stats.recentChannelCount),
        stringResource(R.string.dashboard_stat_resume, stats.continueWatchingCount),
        stringResource(R.string.dashboard_stat_movies, stats.movieLibraryCount),
        stringResource(R.string.dashboard_stat_series, stats.seriesLibraryCount)
    )

    LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        items(statItems, key = { it }) { statLabel ->
            Surface(
                shape = RoundedCornerShape(999.dp),
                colors = SurfaceDefaults.colors(
                    containerColor = AppColors.Surface.copy(alpha = 0.64f)
                )
            ) {
                Text(
                    text = statLabel,
                    style = MaterialTheme.typography.labelLarge,
                    color = TextPrimary,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
                )
            }
        }
    }
}

@Composable
private fun DashboardShortcutRow(
    title: String,
    subtitle: String,
    shortcuts: List<DashboardLiveShortcut>,
    onShortcutClick: (DashboardLiveShortcut) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 24.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = TextPrimary
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceDim
            )
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(shortcuts, key = { "${it.type}:${it.categoryId}:${it.label}" }) { shortcut ->
                DashboardShortcutCard(
                    shortcut = shortcut,
                    onClick = { onShortcutClick(shortcut) }
                )
            }
        }
    }
}

@Composable
private fun DashboardShortcutCard(
    shortcut: DashboardLiveShortcut,
    onClick: () -> Unit
) {
    val screenWidth = LocalConfiguration.current.screenWidthDp.dp
    val isTelevisionDevice = rememberIsTelevisionDevice()
    val cardWidth = when {
        screenWidth < 700.dp -> 148.dp
        !isTelevisionDevice && screenWidth < 1280.dp -> 160.dp
        else -> 170.dp
    }
    val accentColor = when (shortcut.type) {
        DashboardShortcutType.FAVORITES -> Color(0xFFFFC857)
        DashboardShortcutType.RECENT -> Color(0xFF4FD1C5)
        DashboardShortcutType.LAST_GROUP -> Color(0xFF60A5FA)
        DashboardShortcutType.CUSTOM_GROUP -> Primary
    }

    TvClickableSurface(
        onClick = onClick,
        modifier = Modifier
            .width(cardWidth)
            .height(76.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(16.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = SurfaceElevated,
            focusedContainerColor = SurfaceHighlight
        ),
        border = ClickableSurfaceDefaults.border(
            border = Border(
                border = BorderStroke(1.dp, accentColor.copy(alpha = 0.28f)),
                shape = RoundedCornerShape(16.dp)
            ),
            focusedBorder = Border(
                border = BorderStroke(2.dp, FocusBorder),
                shape = RoundedCornerShape(16.dp)
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(12.dp)
                        .clip(RoundedCornerShape(999.dp))
                        .background(accentColor)
                )
                Text(
                    text = shortcut.label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = TextPrimary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                text = shortcut.detail,
                style = MaterialTheme.typography.bodySmall,
                color = OnSurfaceDim,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DashboardActionButton(
    label: String,
    onClick: () -> Unit
) {
    TvButton(
        onClick = onClick,
        colors = ButtonDefaults.colors(
            containerColor = Primary.copy(alpha = 0.18f),
            focusedContainerColor = Primary.copy(alpha = 0.32f),
            contentColor = TextPrimary
        )
    ) {
        Text(text = label)
    }
}

@Composable
private fun DashboardProviderHealthCard(
    providerName: String,
    health: DashboardProviderHealth,
    onOpenDiagnostics: () -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val appTimeFormat = LocalAppTimeFormat.current
    val dateTimeFormat = remember(appTimeFormat) { appTimeFormat.createDateTimeFormat() }
    val syncLabel = remember(health.lastSyncedAt, dateTimeFormat) {
        if (health.lastSyncedAt <= 0L) {
            context.getString(R.string.dashboard_provider_no_sync)
        } else {
            context.getString(R.string.dashboard_provider_synced_at, dateTimeFormat.format(Date(health.lastSyncedAt)))
        }
    }
    val expiryLabel = remember(health.expirationDate) {
        health.expirationDate?.let {
            val format = SimpleDateFormat("MMM d, yyyy", Locale.getDefault())
            context.getString(R.string.dashboard_provider_expires_at, format.format(Date(it)))
        } ?: context.getString(R.string.dashboard_provider_no_expiry)
    }
    val statusLabel = when (health.status) {
        com.kaynanamtv.domain.model.ProviderStatus.ACTIVE -> stringResource(R.string.settings_status_active)
        com.kaynanamtv.domain.model.ProviderStatus.PARTIAL -> stringResource(R.string.settings_status_partial)
        com.kaynanamtv.domain.model.ProviderStatus.ERROR -> stringResource(R.string.settings_status_error)
        com.kaynanamtv.domain.model.ProviderStatus.EXPIRED -> stringResource(R.string.settings_status_expired)
        com.kaynanamtv.domain.model.ProviderStatus.DISABLED -> stringResource(R.string.settings_status_disabled)
        com.kaynanamtv.domain.model.ProviderStatus.UNKNOWN -> stringResource(R.string.settings_status_unknown)
    }
    val sourceLabel = when (health.type) {
        com.kaynanamtv.domain.model.ProviderType.XTREAM_CODES -> stringResource(R.string.dashboard_provider_xtream)
        com.kaynanamtv.domain.model.ProviderType.M3U -> stringResource(R.string.dashboard_provider_m3u)
        com.kaynanamtv.domain.model.ProviderType.STALKER_PORTAL -> "Stalker/MAG Portal"
        com.kaynanamtv.domain.model.ProviderType.JELLYFIN -> "Jellyfin"
    }

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp, vertical = 4.dp),
        shape = RoundedCornerShape(22.dp),
        colors = SurfaceDefaults.colors(containerColor = SurfaceHighlight)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 18.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(
                    text = stringResource(R.string.dashboard_provider_health_title),
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary
                )
                Text(
                    text = providerName,
                    style = MaterialTheme.typography.bodyLarge,
                    color = OnSurfaceDim
                )
                Text(
                    text = "$syncLabel | $expiryLabel",
                    style = MaterialTheme.typography.bodySmall,
                    color = OnSurfaceDim
                )
            }

            LazyRow(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                item {
                    DashboardHealthPill(
                        label = statusLabel,
                        value = stringResource(R.string.dashboard_provider_status)
                    )
                }
                item {
                    DashboardHealthPill(
                        label = sourceLabel,
                        value = stringResource(R.string.dashboard_provider_source)
                    )
                }
                item {
                    DashboardHealthPill(
                        label = health.maxConnections.toString(),
                        value = stringResource(R.string.dashboard_provider_connections)
                    )
                }
            }
        }
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 22.dp, vertical = 0.dp),
            horizontalArrangement = Arrangement.End
        ) {
            DashboardActionButton(
                label = stringResource(R.string.dashboard_warning_review),
                onClick = onOpenDiagnostics
            )
        }
    }
}

@Composable
private fun DashboardHealthPill(
    label: String,
    value: String
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        colors = SurfaceDefaults.colors(containerColor = Color.White.copy(alpha = 0.08f))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.labelSmall,
                color = OnSurfaceDim
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = TextPrimary
            )
        }
    }
}

@Composable
private fun DashboardProviderWarningCard(
    warnings: List<String>,
    onOpenSettings: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        colors = SurfaceDefaults.colors(containerColor = SurfaceElevated)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.dashboard_warning_title),
                style = MaterialTheme.typography.titleMedium,
                color = Primary
            )
            Text(
                text = warnings.take(3).joinToString(" | "),
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceDim
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DashboardActionButton(
                    label = stringResource(R.string.dashboard_warning_review),
                    onClick = onOpenSettings
                )
            }
        }
    }
}

@Composable
private fun DashboardUpdateCard(
    notice: DashboardUpdateNotice,
    onOpenSettings: () -> Unit,
    onInstallUpdate: () -> Unit,
    onDownloadAndInstall: () -> Unit
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 48.dp, vertical = 6.dp),
        shape = RoundedCornerShape(20.dp),
        colors = SurfaceDefaults.colors(containerColor = Primary.copy(alpha = 0.16f)),
        border = Border(BorderStroke(1.dp, Primary.copy(alpha = 0.45f)))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 22.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.dashboard_update_title, notice.latestVersionName),
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            Text(
                text = stringResource(
                    if (notice.installPermissionRequired) {
                        R.string.dashboard_update_install_permission_required
                    } else if (notice.installReady) {
                        R.string.dashboard_update_install_ready
                    } else {
                        R.string.dashboard_update_available
                    }
                ),
                style = MaterialTheme.typography.bodyMedium,
                color = OnSurfaceDim
            )
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                DashboardActionButton(
                    label = if (notice.installPermissionRequired) {
                        stringResource(R.string.dashboard_update_allow_installs)
                    } else if (notice.installReady) {
                        stringResource(R.string.dashboard_update_open_installer)
                    } else {
                        "Güncellemeyi İndir ve Kur"
                    },
                    onClick = {
                        if (notice.installReady || notice.installPermissionRequired) {
                            onInstallUpdate()
                        } else {
                            onDownloadAndInstall()
                        }
                    }
                )
                TvButton(
                    onClick = onOpenSettings,
                    colors = ButtonDefaults.colors(
                        containerColor = SurfaceElevated,
                        focusedContainerColor = SurfaceHighlight,
                        contentColor = TextPrimary
                    )
                ) {
                    Text("Ayarlar'ı Aç")
                }
            }
        }
    }
}

@Composable
private fun EmptyDashboard(
    onAddProvider: () -> Unit,
    onOpenSettings: () -> Unit
) {
    BoxWithConstraints(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        val isTelevisionDevice = rememberIsTelevisionDevice()
        val contentModifier = if (maxWidth < 900.dp) {
            Modifier.fillMaxWidth(0.9f)
        } else if (!isTelevisionDevice && maxWidth < 1280.dp) {
            Modifier.fillMaxWidth(0.76f)
        } else {
            Modifier.width(720.dp)
        }

        Surface(
            shape = RoundedCornerShape(28.dp),
            colors = SurfaceDefaults.colors(
                containerColor = SurfaceHighlight
            )
        ) {
            Column(
                modifier = contentModifier
                    .padding(horizontal = 32.dp, vertical = 28.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
                horizontalAlignment = Alignment.Start
            ) {
                Text(
                    text = stringResource(R.string.dashboard_empty_title),
                    style = MaterialTheme.typography.headlineMedium,
                    color = OnBackground
                )
                Text(
                    text = stringResource(R.string.dashboard_empty_body),
                    style = MaterialTheme.typography.titleMedium,
                    color = OnSurfaceDim
                )
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TvButton(onClick = onAddProvider) {
                        Text(stringResource(R.string.settings_add_provider))
                    }
                    TvButton(
                        onClick = onOpenSettings,
                        colors = ButtonDefaults.colors(
                            containerColor = SurfaceElevated,
                            focusedContainerColor = Primary.copy(alpha = 0.24f),
                            contentColor = TextPrimary
                        )
                    ) {
                        Text(stringResource(R.string.nav_settings))
                    }
                }
            }
        }
    }
}

@Composable
private fun rememberDashboardSections(
    uiState: DashboardUiState
): List<AppHomeDashboardShelf> {
    return remember(
        uiState.homeDashboardShelves,
        uiState.liveShortcuts,
        uiState.favoriteChannels,
        uiState.recentChannels,
        uiState.continueWatching,
        uiState.continueWatchingMovies,
        uiState.continueWatchingSeriesItems,
        uiState.favoriteMovies,
        uiState.favoriteSeries,
        uiState.recentMovies,
        uiState.recentSeries,
        uiState.topRatedMovies,
        uiState.recommendedMovies
    ) {
        resolveVisibleDashboardShelves(uiState)
    }
}

@Composable
private fun FavoriteChannelsRow(
    title: String,
    channels: List<Channel>,
    onSeeAll: () -> Unit,
    onChannelClick: (Channel) -> Unit
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = TextPrimary
            )
            TvClickableSurface(
                onClick = onSeeAll,
                shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(999.dp)),
                colors = ClickableSurfaceDefaults.colors(
                    containerColor = Primary.copy(alpha = 0.12f),
                    focusedContainerColor = Primary.copy(alpha = 0.22f),
                    contentColor = TextTertiary
                )
            ) {
                Text(
                    text = stringResource(R.string.category_see_all),
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        LazyRow(
            contentPadding = PaddingValues(horizontal = 20.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            items(channels, key = { it.id }) { channel ->
                FavoriteChannelLogoCard(
                    channel = channel,
                    onClick = { onChannelClick(channel) }
                )
            }
        }
    }
}

@Composable
private fun FavoriteChannelLogoCard(
    channel: Channel,
    onClick: () -> Unit
) {
    TvClickableSurface(
        onClick = onClick,
        modifier = Modifier.width(86.dp),
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(18.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = SurfaceElevated,
            focusedContainerColor = SurfaceHighlight
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(
                border = BorderStroke(2.dp, FocusBorder),
                shape = RoundedCornerShape(18.dp)
            )
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .size(54.dp)
                    .clip(RoundedCornerShape(999.dp))
            ) {
                ChannelLogoBadge(
                    channelName = channel.name,
                    logoUrl = channel.logoUrl,
                    shape = RoundedCornerShape(999.dp),
                    backgroundColor = AppColors.SurfaceEmphasis,
                    contentPadding = PaddingValues(8.dp),
                    textStyle = MaterialTheme.typography.labelLarge,
                    textColor = TextPrimary,
                    modifier = Modifier.fillMaxSize()
                )
            }

            Text(
                text = channel.name,
                style = MaterialTheme.typography.bodySmall,
                color = TextPrimary,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun DashboardWelcomeHeroCard(
    providerName: String,
    onNavigate: (String) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = SurfaceDefaults.colors(containerColor = SurfaceHighlight)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        text = "KaynanamTV'ye Hoş Geldiniz! 👋",
                        style = MaterialTheme.typography.headlineSmall,
                        color = OnBackground,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Yayın kaynağınız ($providerName) hazır. Aşağıdaki hızlı erişim butonlarından yayınları izlemeye başlayabilirsiniz.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = OnSurfaceDim
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    DashboardHeroActionChip(
                        label = "Canlı TV İzle",
                        icon = "📺",
                        onClick = { onNavigate(Routes.LIVE_TV) }
                    )
                    DashboardHeroActionChip(
                        label = "Filmler",
                        icon = "🎬",
                        onClick = { onNavigate(Routes.MOVIES) }
                    )
                    DashboardHeroActionChip(
                        label = "Diziler",
                        icon = "🍿",
                        onClick = { onNavigate(Routes.SERIES) }
                    )
                    DashboardHeroActionChip(
                        label = "TV Rehberi",
                        icon = "🗓️",
                        onClick = { onNavigate(Routes.EPG) }
                    )
                }
            }
        }
    }
}

@Composable
private fun DashboardHeroActionChip(
    label: String,
    icon: String,
    onClick: () -> Unit
) {
    val focusRequester = remember { FocusRequester() }
    TvClickableSurface(
        onClick = onClick,
        shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
        colors = ClickableSurfaceDefaults.colors(
            containerColor = Primary.copy(alpha = 0.16f),
            focusedContainerColor = Primary.copy(alpha = 0.35f)
        ),
        border = ClickableSurfaceDefaults.border(
            focusedBorder = Border(BorderStroke(2.dp, Color.White), shape = RoundedCornerShape(10.dp))
        ),
        scale = ClickableSurfaceDefaults.scale(focusedScale = 1.03f),
        modifier = Modifier
            .focusRequester(focusRequester)
            .mouseClickable(focusRequester = focusRequester, onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(text = icon, style = MaterialTheme.typography.titleMedium)
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = OnBackground,
                fontWeight = FontWeight.Bold
            )
        }
    }
}


