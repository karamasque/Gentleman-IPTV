 package com.kaynanamtv.app.navigation
 
 import android.net.Uri
 import android.util.Log
 import androidx.compose.runtime.Composable
 import androidx.compose.runtime.LaunchedEffect
 import androidx.compose.runtime.getValue
 import androidx.compose.runtime.mutableStateOf
 import androidx.compose.runtime.produceState
 import androidx.compose.runtime.remember
 import androidx.compose.runtime.setValue
 import androidx.lifecycle.Lifecycle
 import androidx.lifecycle.LifecycleEventObserver
 import androidx.lifecycle.compose.collectAsStateWithLifecycle
 import androidx.lifecycle.compose.dropUnlessResumed
 import androidx.navigation.NavHostController
 import androidx.navigation.NavOptionsBuilder
 import androidx.navigation.NavType
 import androidx.navigation.compose.NavHost
 import androidx.navigation.compose.composable
 import androidx.navigation.compose.currentBackStackEntryAsState
 import androidx.navigation.compose.rememberNavController
 import androidx.navigation.navArgument
 import com.kaynanamtv.app.ui.model.isArchivePlayable
 import com.kaynanamtv.domain.model.Channel
  
 import com.kaynanamtv.domain.model.Movie
 import com.kaynanamtv.domain.repository.ChannelRepository
 import com.kaynanamtv.app.ui.screens.dashboard.DashboardScreen
 import com.kaynanamtv.app.ui.screens.multiview.MultiViewScreen
 import com.kaynanamtv.app.ui.screens.home.HomeScreen
 import com.kaynanamtv.app.ui.screens.movies.MoviesScreen
 import com.kaynanamtv.app.ui.screens.player.PlayerScreen
 import com.kaynanamtv.app.ui.screens.plugins.PluginsScreen
 import com.kaynanamtv.app.ui.screens.chat.CommunityChatScreen
 import com.kaynanamtv.app.ui.screens.provider.ProviderSetupScreen
 import com.kaynanamtv.app.ui.screens.series.SeriesScreen
 import com.kaynanamtv.app.ui.screens.settings.SettingsScreen
 import com.kaynanamtv.app.ui.screens.welcome.WelcomeScreen
 import com.kaynanamtv.app.ui.screens.auth.AuthScreen
 import com.kaynanamtv.app.ui.screens.downloads.DownloadsScreen
 import com.kaynanamtv.app.MainActivity
 import com.kaynanamtv.domain.model.AppLandingDestination
 import com.kaynanamtv.domain.model.AppTopLevelDestination
 import com.kaynanamtv.domain.model.ContentType
 import com.kaynanamtv.domain.model.MovieDetailPresentationHint
 import com.kaynanamtv.domain.model.ActiveLiveSource
 import com.kaynanamtv.domain.model.Series
 import com.kaynanamtv.domain.model.SeriesDetailPresentationHint
 import com.kaynanamtv.domain.model.VirtualCategoryIds
 import com.kaynanamtv.app.ui.screens.epg.FullEpgScreen
 import com.kaynanamtv.app.ui.screens.search.SearchScreen
 import com.kaynanamtv.app.ui.screens.movies.MovieDetailScreen
 import com.kaynanamtv.app.ui.screens.series.SeriesDetailScreen
 import com.kaynanamtv.domain.model.Episode
 import com.kaynanamtv.domain.model.Program
 import com.kaynanamtv.domain.model.PlaybackHistory
 import java.io.Serializable
 import kotlin.coroutines.resume
 import kotlinx.coroutines.flow.first
 import kotlinx.coroutines.suspendCancellableCoroutine
 
 
 private const val PLAYER_REQUEST_KEY = "player_request"
 internal const val MOVIE_DETAIL_PRESENTATION_HINT_KEY = "movie_detail_presentation_hint"
 internal const val SERIES_DETAIL_PRESENTATION_HINT_KEY = "series_detail_presentation_hint"
 private const val TAG = "AppNavigation"
 
 private fun requiresResolvedStartupTarget(landingDestination: AppLandingDestination): Boolean =
     landingDestination == AppLandingDestination.FIRST_FAVORITE_LIVE ||
         landingDestination == AppLandingDestination.LAST_WATCHED_LIVE
 
 data class PlayerNavigationRequest(
     val streamUrl: String,
     val title: String,
     val channelId: String? = null,
     val internalId: Long = -1L,
     val categoryId: Long? = null,
     val providerId: Long? = null,
     val isVirtual: Boolean = false,
     val combinedProfileId: Long? = null,
     val combinedSourceFilterProviderId: Long? = null,
     val contentType: String = "LIVE",
     val artworkUrl: String? = null,
     val archiveStartMs: Long? = null,
     val archiveEndMs: Long? = null,
     val archiveTitle: String? = null,
     val returnRoute: String? = null,
     val seriesId: Long? = null,
     val seasonNumber: Int? = null,
     val episodeNumber: Int? = null,
     val episodeId: Long? = null
 ) : Serializable
 
 object Routes {
     const val PROVIDER_SETUP = "provider_setup?providerId={providerId}&importUri={importUri}"
     const val HOME = "home"
     const val LIVE_TV = "live_tv"
     const val LIVE_TV_DESTINATION = "live_tv?categoryId={categoryId}"
     const val MOVIES = "movies"
     const val SERIES = "series"
     const val DOWNLOADS = "downloads"
     const val EPG = "epg"
     const val EPG_DESTINATION = "epg?categoryId={categoryId}&anchorTime={anchorTime}&favoritesOnly={favoritesOnly}"
     const val SETTINGS = "settings"
     const val SETTINGS_DESTINATION = "settings?backupUri={backupUri}&initialSection={initialSection}"
     const val PLUGINS = "plugins"
     const val COMMUNITY_CHAT = "chat"
     const val PLAYER = "player"
     const val SEARCH = "search"
     const val SEARCH_DESTINATION = "search?query={query}"
     const val MOVIE_DETAIL = "movie_detail/{movieId}?returnRoute={returnRoute}"
     const val SERIES_DETAIL = "series_detail/{seriesId}?returnRoute={returnRoute}"
     const val WELCOME = "welcome"
     const val AUTH = "auth"
     const val PARENTAL_CONTROL_GROUPS = "parental_control_groups/{providerId}"
     const val MULTI_VIEW = "multi_view"
 
 
     fun providerSetup(providerId: Long? = null, importUri: String? = null): String {
         val encodedImportUri = Uri.encode(importUri ?: "")
         return "provider_setup?providerId=${providerId ?: -1L}&importUri=$encodedImportUri"
     }
     fun liveTv(categoryId: Long? = null) = if (categoryId == null) LIVE_TV else "$LIVE_TV?categoryId=$categoryId"
     fun epg(categoryId: Long? = null, anchorTime: Long? = null, favoritesOnly: Boolean? = null): String {
         val resolvedCategoryId = categoryId ?: -1L
         val resolvedAnchorTime = anchorTime ?: -1L
         val resolvedFavoritesOnly = favoritesOnly ?: false
         return "$EPG?categoryId=$resolvedCategoryId&anchorTime=$resolvedAnchorTime&favoritesOnly=$resolvedFavoritesOnly"
     }
 
     fun livePlayer(
         channel: Channel,
         categoryId: Long? = channel.categoryId,
         providerId: Long? = channel.providerId,
         isVirtual: Boolean = false,
         combinedProfileId: Long? = null,
         combinedSourceFilterProviderId: Long? = null,
         returnRoute: String? = null
     ): PlayerNavigationRequest {
         val effectiveCategoryId = categoryId ?: ChannelRepository.ALL_CHANNELS_ID
         return player(
             streamUrl = channel.streamUrl,
             title = channel.name,
             channelId = channel.epgChannelId,
             internalId = channel.id,
             categoryId = effectiveCategoryId,
             providerId = providerId,
             isVirtual = isVirtual,
             combinedProfileId = combinedProfileId,
             combinedSourceFilterProviderId = combinedSourceFilterProviderId,
             contentType = "LIVE",
             returnRoute = returnRoute
         )
     }
 
     fun moviePlayer(movie: Movie): PlayerNavigationRequest {
         return player(
             streamUrl = movie.streamUrl,
             title = movie.name,
             internalId = movie.id,
             categoryId = movie.categoryId,
             providerId = movie.providerId,
             contentType = "MOVIE",
             artworkUrl = movie.posterUrl ?: movie.backdropUrl
         )
     }
 
     fun episodePlayer(episode: Episode): PlayerNavigationRequest {
         return player(
             streamUrl = episode.streamUrl,
             title = "${episode.title} - S${episode.seasonNumber}E${episode.episodeNumber}",
             internalId = episode.id,
             providerId = episode.providerId,
             contentType = "SERIES_EPISODE",
             artworkUrl = episode.coverUrl,
             seriesId = episode.seriesId.takeIf { it > 0L },
             seasonNumber = episode.seasonNumber,
             episodeNumber = episode.episodeNumber,
             episodeId = episode.episodeId.takeIf { it > 0L }
         )
     }
 
     fun search(query: String? = null): String =
         if (query.isNullOrBlank()) SEARCH else "$SEARCH?query=${Uri.encode(query)}"
 
     fun settings(backupUri: String? = null, initialSection: Int? = null): String {
         val uriPart = if (backupUri.isNullOrBlank()) "" else "backupUri=${Uri.encode(backupUri)}"
         val sectionPart = if (initialSection == null || initialSection < 0) "" else "initialSection=$initialSection"
         val query = listOf(uriPart, sectionPart).filter { it.isNotBlank() }.joinToString("&")
         return if (query.isBlank()) SETTINGS else "$SETTINGS?$query"
     }
 
     fun player(
         streamUrl: String,
         title: String,
         channelId: String? = null,
         internalId: Long = -1L,
         categoryId: Long? = null,
         providerId: Long? = null,
         isVirtual: Boolean = false,
         combinedProfileId: Long? = null,
         combinedSourceFilterProviderId: Long? = null,
         contentType: String = "LIVE",
         artworkUrl: String? = null,
         archiveStartMs: Long? = null,
         archiveEndMs: Long? = null,
         archiveTitle: String? = null,
         returnRoute: String? = null,
         seriesId: Long? = null,
         seasonNumber: Int? = null,
         episodeNumber: Int? = null,
         episodeId: Long? = null
     ): PlayerNavigationRequest {
         return PlayerNavigationRequest(
             streamUrl = streamUrl,
             title = title,
             channelId = channelId,
             internalId = internalId,
             categoryId = categoryId,
             providerId = providerId,
             isVirtual = isVirtual,
             combinedProfileId = combinedProfileId,
             combinedSourceFilterProviderId = combinedSourceFilterProviderId,
             contentType = contentType,
             artworkUrl = artworkUrl,
             archiveStartMs = archiveStartMs,
             archiveEndMs = archiveEndMs,
             archiveTitle = archiveTitle,
             returnRoute = returnRoute,
             seriesId = seriesId,
             seasonNumber = seasonNumber,
             episodeNumber = episodeNumber,
             episodeId = episodeId
         )
     }
 
     fun movieDetail(movieId: Long, returnRoute: String? = null) =
         "movie_detail/$movieId?returnRoute=${Uri.encode(returnRoute ?: "")}"
     fun seriesDetail(seriesId: Long, returnRoute: String? = null) =
         "series_detail/$seriesId?returnRoute=${Uri.encode(returnRoute ?: "")}"
     fun parentalControlGroups(providerId: Long) = "parental_control_groups/$providerId"
 }
 
 /** Accepts app-supported media schemes while still rejecting obviously unsafe ones. */
 private fun isStreamUrlSafe(url: String?): Boolean {
     if (url.isNullOrBlank()) return false
     val scheme = url.substringBefore("://").lowercase()
     return scheme in setOf("http", "https", "rtsp", "rtmp", "rtsps", "mms", "xtream", "stalker", "content", "file")
 }
 
 internal fun safePlayerNavigationRequest(request: PlayerNavigationRequest?): PlayerNavigationRequest? =
     request?.takeIf { isStreamUrlSafe(it.streamUrl) }
 
 private fun NavHostController.navigateIfResumed(route: String, builder: NavOptionsBuilder.() -> Unit = {}): Boolean {
     if (currentBackStackEntry?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) != true) return false
     navigate(route, builder)
     return true
 }
 
 private suspend fun Lifecycle.awaitResumed() {
     if (currentState.isAtLeast(Lifecycle.State.RESUMED)) return
     suspendCancellableCoroutine { continuation ->
         lateinit var observer: LifecycleEventObserver
         observer = LifecycleEventObserver { _, _ ->
             when {
                 currentState.isAtLeast(Lifecycle.State.RESUMED) -> {
                     removeObserver(observer)
                     if (continuation.isActive) continuation.resume(Unit)
                 }
                 currentState == Lifecycle.State.DESTROYED -> {
                     removeObserver(observer)
                     continuation.cancel()
                 }
             }
         }
         addObserver(observer)
         continuation.invokeOnCancellation { removeObserver(observer) }
     }
 }
 
 private fun NavHostController.navigateToPlayer(request: PlayerNavigationRequest): Boolean {
     if (currentBackStackEntry?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) != true) return false
     currentBackStackEntry?.savedStateHandle?.set(PLAYER_REQUEST_KEY, request)
     navigate(Routes.PLAYER) { launchSingleTop = true }
     return true
 }
 
 private fun NavHostController.navigateToMovieDetail(movie: Movie, returnRoute: String? = null): Boolean {
     if (currentBackStackEntry?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) != true) return false
     currentBackStackEntry?.savedStateHandle?.set(MOVIE_DETAIL_PRESENTATION_HINT_KEY, movie.toMovieDetailPresentationHint())
     navigate(Routes.movieDetail(movie.id, returnRoute))
     return true
 }
 
 private fun Movie.toMovieDetailPresentationHint(): MovieDetailPresentationHint? {
     if (variants.isEmpty()) return null
     return MovieDetailPresentationHint(
         providerId = providerId,
         logicalGroupId = logicalGroupId,
         variants = variants,
         duplicateConfidence = duplicateConfidence
     )
 }
 
 private fun NavHostController.navigateToSeriesDetail(series: Series, returnRoute: String? = null): Boolean {
     if (currentBackStackEntry?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) != true) return false
     currentBackStackEntry?.savedStateHandle?.set(SERIES_DETAIL_PRESENTATION_HINT_KEY, series.toSeriesDetailPresentationHint())
     navigate(Routes.seriesDetail(series.id, returnRoute))
     return true
 }
 
 private fun Series.toSeriesDetailPresentationHint(): SeriesDetailPresentationHint? {
     if (variants.isEmpty()) return null
     return SeriesDetailPresentationHint(
         providerId = providerId,
         logicalGroupId = logicalGroupId,
         variants = variants,
         duplicateConfidence = duplicateConfidence
     )
 }
 
 private fun NavHostController.navigateToExternalPlayer(request: PlayerNavigationRequest): Boolean {
     if (currentBackStackEntry?.lifecycle?.currentState?.isAtLeast(Lifecycle.State.RESUMED) != true) return false
     currentBackStackEntry?.savedStateHandle?.set(PLAYER_REQUEST_KEY, request)
     navigate(Routes.PLAYER) { launchSingleTop = true }
     return true
 }
 
 internal fun AppLandingDestination.toAppRoute(): String = when (this) {
     AppLandingDestination.HOME -> Routes.HOME
     AppLandingDestination.LIVE_TV -> Routes.LIVE_TV
     AppLandingDestination.FIRST_FAVORITE_LIVE -> Routes.LIVE_TV
     AppLandingDestination.LAST_WATCHED_LIVE -> Routes.LIVE_TV
     AppLandingDestination.MOVIES -> Routes.MOVIES
     AppLandingDestination.SERIES -> Routes.SERIES
     AppLandingDestination.GUIDE -> Routes.EPG
     AppLandingDestination.DOWNLOADS -> Routes.DOWNLOADS
     AppLandingDestination.PLUGINS -> Routes.PLUGINS
     AppLandingDestination.SETTINGS -> Routes.SETTINGS
 }
 
 internal fun AppTopLevelDestination.toAppRoute(): String = when (this) {
     AppTopLevelDestination.HOME -> Routes.HOME
     AppTopLevelDestination.LIVE_TV -> Routes.LIVE_TV
     AppTopLevelDestination.MOVIES -> Routes.MOVIES
     AppTopLevelDestination.SERIES -> Routes.SERIES
     AppTopLevelDestination.COMMUNITY_CHAT -> Routes.COMMUNITY_CHAT
     AppTopLevelDestination.DOWNLOADS -> Routes.DOWNLOADS
     AppTopLevelDestination.GUIDE -> Routes.EPG
     AppTopLevelDestination.SEARCH -> Routes.SEARCH
     AppTopLevelDestination.PLUGINS -> Routes.PLUGINS
     AppTopLevelDestination.SETTINGS -> Routes.SETTINGS
 }
 
 @Composable
 fun AppNavigation(mainActivity: MainActivity) {
     val navController = rememberNavController()
     val currentBackStackEntry = navController.currentBackStackEntryAsState().value
     val externalNavigationRequest = mainActivity.externalNavigationRequestFlow.collectAsStateWithLifecycle().value
     val topLevelDestinations = mainActivity.preferencesRepository.appTopLevelDestinations
         .collectAsStateWithLifecycle(initialValue = AppTopLevelDestination.defaultOrder)
         .value
     val appLandingDestination = mainActivity.preferencesRepository.appLandingDestination
         .collectAsStateWithLifecycle(initialValue = null)
         .value
     val resolvedLandingDestination = appLandingDestination?.let { landingDestination ->
         AppTopLevelDestination.resolveLandingDestination(
             preferred = landingDestination,
             destinations = topLevelDestinations
         )
     }
     // Route to land on when leaving the Welcome screen. For the "first favorite" / "last watched"
     // landings this resolves to the Live TV tab (via toAppRoute); the channel itself is opened on top
     // afterwards (see startupPlayerRequest below). This is computed immediately with no channel
     // lookup, so Welcome is never held open long enough for a quick Back press to fall through the
     // start destination and exit the app.
     val startupRoute: String? = resolvedLandingDestination?.toAppRoute()
 
     // Deferred auto-play request for the live landings. Resolved off the Welcome screen so the app
     // stays interactive on Live TV while the channel is looked up.
     val startupPlayerRequest by produceState<PlayerNavigationRequest?>(
         initialValue = null,
         resolvedLandingDestination
     ) {
         val landing = resolvedLandingDestination
         value = if (landing != null && requiresResolvedStartupTarget(landing)) {
             resolveStartupPlayerRequest(mainActivity, landing)
         } else {
             null
         }
     }
     var startupPlayerHandled by remember { mutableStateOf(false) }
 
     fun navigateToStartupTarget(popUpRoute: String): Boolean {
         val route = startupRoute ?: Routes.HOME
         return navController.navigateIfResumed(route) {
             popUpTo(popUpRoute) { inclusive = true }
         }
     }
 
     // Once the live landing has placed us on the Live TV tab, open the resolved channel on top of it.
     // Opening on top of Live TV (instead of replacing it) means Back from the player returns into the
     // app rather than exiting. Guarded so it fires once, and only while still on the freshly-landed
     // Live TV tab, to avoid hijacking navigation after the user has started interacting.
     LaunchedEffect(startupPlayerRequest, currentBackStackEntry) {
         if (startupPlayerHandled) return@LaunchedEffect
         val request = startupPlayerRequest ?: return@LaunchedEffect
         val entry = currentBackStackEntry ?: return@LaunchedEffect
         val route = entry.destination?.route
         if (route != Routes.LIVE_TV_DESTINATION && route != Routes.LIVE_TV) return@LaunchedEffect
         entry.lifecycle.awaitResumed()
         if (navController.navigateToPlayer(request)) {
             startupPlayerHandled = true
         }
     }
 
     LaunchedEffect(externalNavigationRequest, currentBackStackEntry) {
         val entry = currentBackStackEntry ?: return@LaunchedEffect
         entry.lifecycle.awaitResumed()
         when (val request = externalNavigationRequest) {
             is ExternalNavigationRequest.Player -> {
                 if (navController.navigateToExternalPlayer(request.request)) {
                     mainActivity.clearExternalNavigationRequest()
                 }
             }
 
             is ExternalNavigationRequest.Destination -> {
                 if (navController.navigateIfResumed(request.destination.toRoute()) { launchSingleTop = true }) {
                     mainActivity.clearExternalNavigationRequest()
                 }
             }
 
             is ExternalNavigationRequest.ImportM3u -> {
                 if (navController.navigateIfResumed(Routes.providerSetup(importUri = request.uri)) { launchSingleTop = true }) {
                     mainActivity.clearExternalNavigationRequest()
                 }
             }
 
             is ExternalNavigationRequest.ImportBackup -> {
                 if (navController.navigateIfResumed(Routes.settings(backupUri = request.uri)) { launchSingleTop = true }) {
                     mainActivity.clearExternalNavigationRequest()
                 }
             }
 
             is ExternalNavigationRequest.Search -> {
                 if (navController.navigateIfResumed(Routes.search(request.query)) { launchSingleTop = true }) {
                     mainActivity.clearExternalNavigationRequest()
                 }
             }
 
             null -> Unit
         }
     }
 
     // NAV-M02/NAV-H02: Single helper replacing repeated tab lambdas without serializing
     // each tab's full UI tree into saved state on every switch.
     fun tabNavigate(route: String) {
         val entry = navController.currentBackStackEntry ?: return
         if (!entry.lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) return
         val currentRoute = entry.destination?.route
         if (currentRoute == route || currentRoute?.startsWith("$route?") == true) return
 
         navController.navigate(route) {
             popUpTo(navController.graph.startDestinationId) {
                 saveState = true
             }
             launchSingleTop = true
             restoreState = true
         }
     }
 
     NavHost(
         navController = navController,
         startDestination = Routes.WELCOME
     ) {
         composable(Routes.WELCOME) {
             WelcomeScreen(
                 onNavigateToHome = {
                     navigateToStartupTarget(Routes.WELCOME)
                 },
                 startupReady = true,
                 onNavigateToSetup = dropUnlessResumed {
                     navController.navigate(Routes.providerSetup(null)) {
                         popUpTo(Routes.WELCOME) { inclusive = true }
                     }
                 },
                 onNavigateToAuth = {
                     navController.navigate(Routes.AUTH) {
                         popUpTo(Routes.WELCOME) { inclusive = true }
                     }
                 }
             )
         }
 
         composable(Routes.AUTH) {
             AuthScreen(
                 onNavigateToWelcome = {
                     navController.navigate(Routes.WELCOME) {
                         popUpTo(Routes.AUTH) { inclusive = true }
                     }
                 }
             )
         }
 
         composable(
             route = Routes.PROVIDER_SETUP,
             arguments = listOf(
                 navArgument("providerId") { type = NavType.LongType; defaultValue = -1L },
                 navArgument("importUri") { type = NavType.StringType; defaultValue = "" }
             )
         ) { backStackEntry ->
             val providerId = backStackEntry.arguments?.getLong("providerId")?.takeIf { it != -1L }
             val importUri = backStackEntry.arguments?.getString("importUri")?.takeIf { it.isNotBlank() }
             
             ProviderSetupScreen(
                 editProviderId = providerId,
                 initialImportUri = importUri,
                 onBack = { navController.popBackStack() },
                 onProviderAdded = {
                     navigateToStartupTarget(Routes.PROVIDER_SETUP)
                 },
                 onNavigateToMembership = {
                     navController.navigate(Routes.settings(initialSection = 7))
                 }
             )
         }
 
         composable(Routes.HOME) {
             DashboardScreen(
                 onNavigate = { route -> tabNavigate(route) },
                 onAddProvider = dropUnlessResumed {
                     navController.navigate(Routes.providerSetup(null))
                 },
                 onRecentChannelClick = { channel, combinedProfileId ->
                     navController.navigateToPlayer(
                         Routes.livePlayer(
                             channel = channel,
                             combinedProfileId = combinedProfileId,
                             returnRoute = Routes.HOME
                         )
                     )
                 },
                 onFavoriteChannelClick = { channel, combinedProfileId ->
                     navController.navigateToPlayer(
                         Routes.livePlayer(
                             channel = channel,
                             combinedProfileId = combinedProfileId,
                             returnRoute = Routes.HOME
                         )
                     )
                 },
                 onMovieClick = { movie ->
                     navController.navigateToMovieDetail(movie, returnRoute = Routes.HOME)
                 },
                 onSeriesClick = { series ->
                     navController.navigateToSeriesDetail(series, returnRoute = Routes.HOME)
                 },
                 onPlaybackHistoryClick = { history ->
                     navController.navigateToPlayer(
                         Routes.player(
                             streamUrl = history.streamUrl,
                             title = history.title,
                             channelId = null,
                             internalId = history.contentId,
                             categoryId = null,
                             providerId = history.providerId,
                             contentType = history.contentType.name,
                             artworkUrl = history.posterUrl,
                             seriesId = history.seriesId,
                             seasonNumber = history.seasonNumber,
                             episodeNumber = history.episodeNumber,
                             episodeId = null,
                             returnRoute = Routes.HOME
                         )
                     )
                 },
                 currentRoute = Routes.HOME
             )
         }
 
         composable(
             route = Routes.LIVE_TV_DESTINATION,
             arguments = listOf(
                 navArgument("categoryId") { type = NavType.LongType; defaultValue = -1L }
             )
         ) { backStackEntry ->
             val categoryId = backStackEntry.arguments?.getLong("categoryId")?.takeIf { it != -1L }
             HomeScreen(
                 currentRoute = Routes.LIVE_TV,
                 onNavigate = { route -> tabNavigate(route) },
                 initialCategoryId = categoryId,
                 onChannelClick = { channel, category, provider, archiveStartMs, archiveEndMs ->
                     navController.navigateToPlayer(
                         Routes.livePlayer(
                             channel = channel,
                             categoryId = category?.id,
                             providerId = provider?.id,
                             returnRoute = Routes.liveTv(category?.id)
                         )
                     )
                 }
             )
         }
 
         composable(Routes.MOVIES) {
             MoviesScreen(
                 currentRoute = Routes.MOVIES,
                 onNavigate = { route -> tabNavigate(route) },
                 onMovieClick = { movie ->
                     navController.navigateToMovieDetail(movie, returnRoute = Routes.MOVIES)
                 },
                 onContinueWatchingPlay = { history ->
                     navController.navigateToPlayer(
                         Routes.player(
                             streamUrl = history.streamUrl,
                             title = history.title,
                             channelId = null,
                             internalId = history.contentId,
                             categoryId = null,
                             providerId = history.providerId,
                             contentType = history.contentType.name,
                             artworkUrl = history.posterUrl,
                             returnRoute = Routes.MOVIES
                         )
                     )
                 }
             )
         }
 
         composable(Routes.SERIES) {
             SeriesScreen(
                 currentRoute = Routes.SERIES,
                 onNavigate = { route -> tabNavigate(route) },
                 onSeriesClick = { series ->
                     navController.navigateToSeriesDetail(series, returnRoute = Routes.SERIES)
                 },
                 onSeriesIdClick = { seriesId ->
                     navController.navigate(Routes.seriesDetail(seriesId, returnRoute = Routes.SERIES))
                 }
             )
         }
 
         composable(Routes.DOWNLOADS) {
             DownloadsScreen(
                 currentRoute = Routes.DOWNLOADS,
                 onNavigate = { route -> tabNavigate(route) }
             )
         }
 
         composable(
             route = Routes.EPG_DESTINATION,
             arguments = listOf(
                 navArgument("categoryId") { type = NavType.LongType; defaultValue = -1L },
                 navArgument("anchorTime") { type = NavType.LongType; defaultValue = -1L },
                 navArgument("favoritesOnly") { type = NavType.BoolType; defaultValue = false }
             )
         ) { backStackEntry ->
             val categoryId = backStackEntry.arguments?.getLong("categoryId")?.takeIf { it != -1L }
             val anchorTime = backStackEntry.arguments?.getLong("anchorTime")?.takeIf { it != -1L }
             val favoritesOnly = backStackEntry.arguments?.getBoolean("favoritesOnly") ?: false
             FullEpgScreen(
                 currentRoute = Routes.EPG,
                 onNavigate = { route -> tabNavigate(route) },
                 initialCategoryId = categoryId,
                 initialAnchorTime = anchorTime,
                 initialFavoritesOnly = favoritesOnly,
                 onPlayChannel = { channel: Channel, categoryId: Long, isVirtual: Boolean, combinedProfileId: Long?, returnRoute: String ->
                     navController.navigateToPlayer(
                         Routes.livePlayer(
                             channel = channel,
                             categoryId = categoryId,
                             isVirtual = isVirtual,
                             combinedProfileId = combinedProfileId,
                             returnRoute = returnRoute
                         )
                     )
                 },
                 onPlayArchive = { channel: Channel, program: Program, startMs: Long, isVirtual: Boolean, combinedProfileId: Long?, returnRoute: String ->
                     navController.navigateToPlayer(
                         Routes.player(
                             streamUrl = channel.streamUrl,
                             title = channel.name,
                             channelId = channel.epgChannelId,
                             internalId = channel.id,
                             categoryId = channel.categoryId,
                             providerId = channel.providerId,
                             isVirtual = isVirtual,
                             combinedProfileId = combinedProfileId,
                             contentType = "ARCHIVE",
                             artworkUrl = channel.logoUrl,
                             archiveStartMs = program.startTime,
                             archiveEndMs = program.endTime,
                             archiveTitle = program.title,
                             returnRoute = returnRoute
                         )
                     )
                 }
             )
         }
 
         composable(
             route = Routes.SETTINGS_DESTINATION,
             arguments = listOf(
                 navArgument("backupUri") { type = NavType.StringType; defaultValue = "" },
                 navArgument("initialSection") { type = NavType.IntType; defaultValue = -1 }
             )
         ) { backStackEntry ->
             val backupUri = backStackEntry.arguments?.getString("backupUri")?.takeIf { it.isNotBlank() }
             val initialSection = backStackEntry.arguments?.getInt("initialSection")?.takeIf { it >= 0 }
             SettingsScreen(
                 onNavigate = { route -> tabNavigate(route) },
                 onAddProvider = dropUnlessResumed {
                     navController.navigate(Routes.providerSetup(null))
                 },
                 onEditProvider = { provider ->
                     navController.navigateIfResumed(Routes.providerSetup(provider.id))
                 },
                 onNavigateToParentalControl = { providerId ->
                     navController.navigateIfResumed(Routes.parentalControlGroups(providerId))
                 },
                 onLogout = {
                     navController.navigate(Routes.WELCOME) {
                         popUpTo(0) { inclusive = true }
                     }
                 },
                 currentRoute = Routes.SETTINGS,
                 initialBackupImportUri = backupUri,
                 initialSection = initialSection
             )
         }
 
         composable(Routes.PLUGINS) {
             PluginsScreen(
                 currentRoute = Routes.PLUGINS,
                 onNavigate = { route -> tabNavigate(route) }
             )
         }
 
         composable(Routes.COMMUNITY_CHAT) {
             CommunityChatScreen(
                 currentRoute = Routes.COMMUNITY_CHAT,
                 onNavigate = { route -> tabNavigate(route) }
             )
         }
 
         composable(
             route = Routes.PARENTAL_CONTROL_GROUPS,
             arguments = listOf(
                 navArgument("providerId") { type = NavType.LongType }
             )
         ) {
             com.kaynanamtv.app.ui.screens.settings.parental.ParentalControlGroupScreen(
                 currentRoute = Routes.SETTINGS,
                 onNavigate = { route -> tabNavigate(route) },
                 onBack = { navController.popBackStack() }
             )
         }
 
         composable(
            route = Routes.SEARCH_DESTINATION,
            arguments = listOf(
                navArgument("query") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val query = backStackEntry.arguments?.getString("query") ?: ""
            SearchScreen(
                initialQuery = query,
                onChannelClick = { channel ->
                    navController.navigateToPlayer(
                        Routes.livePlayer(
                            channel = channel,
                            returnRoute = Routes.SEARCH
                        )
                    )
                },
                onMovieClick = { movie ->
                    navController.navigate(Routes.movieDetail(movie.id, returnRoute = Routes.SEARCH))
                },
                onSeriesClick = { series ->
                    navController.navigate(Routes.seriesDetail(series.id, returnRoute = Routes.SEARCH))
                },
                onNavigate = { route -> tabNavigate(route) },
                currentRoute = Routes.SEARCH
            )
        }

        composable(route = Routes.PLAYER) { backStackEntry ->
            val playerRequest = backStackEntry.savedStateHandle.get<PlayerNavigationRequest>(PLAYER_REQUEST_KEY)
                ?: navController.previousBackStackEntry?.savedStateHandle?.get<PlayerNavigationRequest>(PLAYER_REQUEST_KEY)?.also {
                    backStackEntry.savedStateHandle[PLAYER_REQUEST_KEY] = it
                }
            val safePlayerRequest = safePlayerNavigationRequest(playerRequest)
            if (safePlayerRequest == null) {
                LaunchedEffect(playerRequest) {
                    Log.w(TAG, "Missing or invalid player request; returning to previous destination")
                    if (!navController.popBackStack()) {
                        navController.navigate(Routes.HOME) {
                            popUpTo(Routes.PLAYER) { inclusive = true }
                            launchSingleTop = true
                        }
                    }
                }
            } else {
                PlayerScreen(
                    streamUrl = safePlayerRequest.streamUrl,
                    title = safePlayerRequest.title,
                    epgChannelId = safePlayerRequest.channelId,
                    internalChannelId = safePlayerRequest.internalId,
                    categoryId = safePlayerRequest.categoryId,
                    providerId = safePlayerRequest.providerId,
                    isVirtual = safePlayerRequest.isVirtual,
                    combinedProfileId = safePlayerRequest.combinedProfileId,
                    combinedSourceFilterProviderId = safePlayerRequest.combinedSourceFilterProviderId,
                    contentType = safePlayerRequest.contentType,
                    artworkUrl = safePlayerRequest.artworkUrl,
                    archiveStartMs = safePlayerRequest.archiveStartMs,
                    archiveEndMs = safePlayerRequest.archiveEndMs,
                    archiveTitle = safePlayerRequest.archiveTitle,
                    returnRoute = safePlayerRequest.returnRoute,
                    seriesId = safePlayerRequest.seriesId,
                    seasonNumber = safePlayerRequest.seasonNumber,
                    episodeNumber = safePlayerRequest.episodeNumber,
                    episodeId = safePlayerRequest.episodeId,
                    onBack = {
                        val returnRoute = safePlayerRequest.returnRoute
                        if (returnRoute != null) {
                            navController.navigate(returnRoute) {
                                popUpTo(returnRoute) { inclusive = true }
                                launchSingleTop = true
                            }
                        } else {
                            navController.popBackStack()
                        }
                    }
                )
            }
        }

        composable(
            route = Routes.MOVIE_DETAIL,
            arguments = listOf(
                navArgument("movieId") { type = NavType.LongType },
                navArgument("returnRoute") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val movieId = backStackEntry.arguments?.getLong("movieId") ?: -1L
            val returnRoute = backStackEntry.arguments?.getString("returnRoute")?.takeIf { it.isNotBlank() }
            MovieDetailScreen(
                onPlay = { movie ->
                    navController.navigateToPlayer(Routes.moviePlayer(movie))
                },
                onBack = {
                    if (returnRoute != null) {
                        navController.navigate(returnRoute) {
                            popUpTo(returnRoute) { inclusive = true }
                            launchSingleTop = true
                        }
                    } else {
                        navController.popBackStack()
                    }
                }
            )
        }

        composable(
            route = Routes.SERIES_DETAIL,
            arguments = listOf(
                navArgument("seriesId") { type = NavType.LongType },
                navArgument("returnRoute") { type = NavType.StringType; defaultValue = "" }
            )
        ) { backStackEntry ->
            val seriesId = backStackEntry.arguments?.getLong("seriesId") ?: -1L
            val returnRoute = backStackEntry.arguments?.getString("returnRoute")?.takeIf { it.isNotBlank() }
            SeriesDetailScreen(
                onEpisodeClick = { episode ->
                    navController.navigateToPlayer(Routes.episodePlayer(episode))
                },
                onResumeClick = { episode ->
                    navController.navigateToPlayer(Routes.episodePlayer(episode))
                },
                onBack = {
                    if (returnRoute != null) {
                        navController.navigate(returnRoute) {
                            popUpTo(returnRoute) { inclusive = true }
                            launchSingleTop = true
                        }
                    } else {
                        navController.popBackStack()
                    }
                }
            )
        }
 
         composable(Routes.MULTI_VIEW) {
             MultiViewScreen(
                 onBack = { navController.popBackStack() }
             )
         }
     }
 }
 
 private suspend fun resolveStartupPlayerRequest(
     mainActivity: MainActivity,
     landingDestination: AppLandingDestination
 ): PlayerNavigationRequest? = when (landingDestination) {
     AppLandingDestination.FIRST_FAVORITE_LIVE -> resolveFirstFavoriteStartupTarget(mainActivity)
     AppLandingDestination.LAST_WATCHED_LIVE -> resolveLastWatchedStartupTarget(mainActivity)
     else -> null
 }
 
 private suspend fun resolveFirstFavoriteStartupTarget(
     mainActivity: MainActivity
 ): PlayerNavigationRequest? {
     if (!mainActivity.preferencesRepository.showFavoritesCategory.first()) return null
     val context = resolveLiveStartupContext(mainActivity) ?: return null
     val favorites = when (context) {
         is LiveStartupContext.Provider -> mainActivity.favoriteRepository.getFavorites(context.providerId, ContentType.LIVE).first()
         is LiveStartupContext.Combined -> mainActivity.favoriteRepository.getFavorites(context.providerIds, ContentType.LIVE).first()
     }.sortedBy { it.position }
     return resolveStartupChannelTarget(
         mainActivity = mainActivity,
         channelIds = favorites.map { it.contentId },
         sourceContext = context,
         virtualCategoryId = VirtualCategoryIds.FAVORITES
     )
 }
 
 private suspend fun resolveLastWatchedStartupTarget(
     mainActivity: MainActivity
 ): PlayerNavigationRequest? {
     val context = resolveLiveStartupContext(mainActivity) ?: return null
     val recentHistory = when (context) {
         is LiveStartupContext.Provider -> mainActivity.playbackHistoryRepository.getRecentlyWatchedByProvider(context.providerId, limit = 24).first()
         is LiveStartupContext.Combined -> mainActivity.playbackHistoryRepository.getRecentlyWatchedByProviders(context.providerIds.toSet(), limit = 24).first()
     }
     return resolveStartupChannelTarget(
         mainActivity = mainActivity,
         channelIds = recentHistory
             .filter { it.contentType == ContentType.LIVE }
             .sortedByDescending { it.lastWatchedAt }
             .map { it.contentId },
         sourceContext = context,
         virtualCategoryId = VirtualCategoryIds.RECENT
     )
 }
 
 private suspend fun resolveStartupChannelTarget(
     mainActivity: MainActivity,
     channelIds: List<Long>,
     sourceContext: LiveStartupContext,
     virtualCategoryId: Long
 ): PlayerNavigationRequest? {
     if (channelIds.isEmpty()) return null
     val hiddenChannelIdsByProvider = sourceContext.providerIds.associateWith { providerId ->
         mainActivity.preferencesRepository.getHiddenChannelIds(providerId).first()
     }
     for (channelId in channelIds.distinct()) {
         val channel = mainActivity.channelRepository.getChannel(channelId) ?: continue
         if (channel.providerId !in sourceContext.providerIds) continue
         if (channel.id in hiddenChannelIdsByProvider[channel.providerId].orEmpty()) continue
         return Routes.livePlayer(
             channel = channel,
             categoryId = virtualCategoryId,
             providerId = channel.providerId,
             isVirtual = true,
             combinedProfileId = (sourceContext as? LiveStartupContext.Combined)?.profileId,
             returnRoute = Routes.LIVE_TV
         )
     }
     return null
 }
 
 private suspend fun resolveLiveStartupContext(
    mainActivity: MainActivity
): LiveStartupContext? {
    return when (val activeSource = mainActivity.combinedM3uRepository.getActiveLiveSource().first()) {
        is ActiveLiveSource.ProviderSource -> LiveStartupContext.Provider(activeSource.providerId)
        is ActiveLiveSource.CombinedM3uSource -> {
            val providerIds = mainActivity.combinedM3uRepository.getProfile(activeSource.profileId)
                ?.members
                .orEmpty()
                .filter { it.enabled }
                .map { it.providerId }
                .distinct()
            if (providerIds.isEmpty()) null else LiveStartupContext.Combined(activeSource.profileId, providerIds)
        }
        null -> {
            mainActivity.providerRepository.getActiveProvider().first()?.id?.let { providerId ->
                LiveStartupContext.Provider(providerId)
            }
        }
    }
}

private sealed interface LiveStartupContext {
    val providerIds: List<Long>

    data class Provider(val providerId: Long) : LiveStartupContext {
        override val providerIds: List<Long> = listOf(providerId)
    }

    data class Combined(val profileId: Long, override val providerIds: List<Long>) : LiveStartupContext
}
