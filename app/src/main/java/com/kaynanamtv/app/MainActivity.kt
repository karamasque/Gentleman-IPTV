package com.kaynanamtv.app

import android.app.SearchManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.os.Build
import android.os.StrictMode
import android.util.Rational
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import com.kaynanamtv.app.cast.CastManager
import com.kaynanamtv.app.cast.CastRouteChooserActivity
import com.kaynanamtv.app.backup.BackupFileBridge
import com.kaynanamtv.app.device.isTelevisionDevice
import com.kaynanamtv.app.localization.resolveAppLocale
import com.kaynanamtv.app.navigation.AppNavigation
import com.kaynanamtv.app.navigation.ExternalDestination
import com.kaynanamtv.app.navigation.ExternalNavigationRequest
import com.kaynanamtv.app.navigation.PlayerNavigationRequest
import com.kaynanamtv.app.tv.LauncherRecommendationsManager
import com.kaynanamtv.app.tv.WatchNextManager
import com.kaynanamtv.app.tvinput.TvInputChannelSyncManager
import com.kaynanamtv.app.ui.theme.KaynanamTVTheme
import com.kaynanamtv.app.ui.time.LocalAppTimeFormat
import com.kaynanamtv.domain.repository.ChannelRepository
import com.kaynanamtv.domain.repository.CombinedM3uRepository
import com.kaynanamtv.domain.repository.FavoriteRepository
import com.kaynanamtv.domain.repository.PlaybackHistoryRepository
import com.kaynanamtv.domain.repository.ProviderRepository
import com.kaynanamtv.domain.model.Provider
import kotlinx.coroutines.flow.first
import dagger.hilt.android.AndroidEntryPoint

import javax.inject.Inject
import com.kaynanamtv.data.preferences.PreferencesRepository
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.unit.LayoutDirection
import android.content.res.Configuration
import android.text.TextUtils
import android.view.View
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import java.util.Locale
import android.content.Context
import android.content.ContextWrapper
import android.net.Uri
import android.content.res.AssetManager
import android.content.res.Resources
import android.speech.RecognizerIntent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    companion object {
        const val EXTRA_PLAYER_REQUEST = "com.kaynanamtv.app.extra.PLAYER_REQUEST"
        const val EXTRA_EXTERNAL_DESTINATION = "com.kaynanamtv.app.extra.EXTERNAL_DESTINATION"
        const val EXTRA_EXTERNAL_ROUTE = "com.kaynanamtv.app.extra.EXTERNAL_ROUTE"
        private const val MAX_PIP_ASPECT_RATIO = 2.39f
        private const val MIN_PIP_ASPECT_RATIO = 1f / MAX_PIP_ASPECT_RATIO
    }

    private data class PlayerPictureInPictureState(
        val enabled: Boolean = false,
        val isPlaying: Boolean = false,
        val aspectRatio: Rational? = null
    )

    @Inject
    lateinit var preferencesRepository: PreferencesRepository

    @Inject
    lateinit var authRepository: com.kaynanamtv.domain.repository.AuthRepository

    @Inject
    lateinit var combinedM3uRepository: CombinedM3uRepository

    @Inject
    lateinit var favoriteRepository: FavoriteRepository

    @Inject
    lateinit var playbackHistoryRepository: PlaybackHistoryRepository

    @Inject
    lateinit var channelRepository: ChannelRepository

    @Inject
    lateinit var providerRepository: ProviderRepository

    @Inject
    lateinit var watchNextManager: WatchNextManager

    @Inject
    lateinit var launcherRecommendationsManager: LauncherRecommendationsManager

    @Inject
    lateinit var tvInputChannelSyncManager: TvInputChannelSyncManager

    @Inject
    lateinit var castManager: CastManager

    @Inject
    lateinit var remoteConfigRepository: com.kaynanamtv.domain.repository.RemoteConfigRepository

    private val _pictureInPictureModeFlow = MutableStateFlow(false)
    val pictureInPictureModeFlow: StateFlow<Boolean> = _pictureInPictureModeFlow.asStateFlow()

    private val _externalNavigationRequestFlow = MutableStateFlow<ExternalNavigationRequest?>(null)
    val externalNavigationRequestFlow: StateFlow<ExternalNavigationRequest?> =
        _externalNavigationRequestFlow.asStateFlow()

    private var playerPictureInPictureState = PlayerPictureInPictureState()

    override fun onCreate(savedInstanceState: Bundle?) {
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(
                StrictMode.ThreadPolicy.Builder().detectAll().penaltyLog().build()
            )
            StrictMode.setVmPolicy(
                StrictMode.VmPolicy.Builder().detectAll().penaltyLog().build()
            )
        }
        super.onCreate(savedInstanceState)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            window.attributes.layoutInDisplayCutoutMode =
                android.view.WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
        }
        // Set decor fits system windows to false for edge-to-edge display and full-width IME keyboard
        WindowCompat.setDecorFitsSystemWindows(window, false)
        applyImmersiveSystemUi()
        _pictureInPictureModeFlow.value = isInPictureInPictureMode
        handleExternalIntent(intent)
        lifecycleScope.launch {
            com.kaynanamtv.app.sound.StartupSoundManager.playStartupSound(this@MainActivity)
        }
        if (isTelevisionDevice()) {
            lifecycleScope.launch {
                watchNextManager.refreshWatchNext()
                launcherRecommendationsManager.refreshRecommendations()
                tvInputChannelSyncManager.refreshTvInputCatalog()
            }
        }
        lifecycleScope.launch {
            var lastSyncedUserId: String? = null
            authRepository.getSessionFlow().collect { session ->
                if (session != null) {
                    val isFirstCollect = lastSyncedUserId == null
                    if (session.userId != lastSyncedUserId) {
                        lastSyncedUserId = session.userId
                        if (!isFirstCollect) {
                            launch {
                                val providers = providerRepository.getProviders().first()
                                val providerIds = providers.map { it.id }
                                com.kaynanamtv.data.sync.ProviderSyncWorker.enqueueProvidersSequentially(applicationContext, providerIds)
                            }
                        }
                    }
                } else {
                    lastSyncedUserId = null
                }
            }
        }
        lifecycleScope.launch {
            (application as? KaynanamTVApp)?.checkForAppUpdates(force = true)
            remoteConfigRepository.checkRemoteConfig(force = true)
        }
        val initialTheme = preferencesRepository.getAppColorThemeSynchronously()
        android.util.Log.i("KaynanamTV_Theme", "[THEME_APPLY] applied=${initialTheme.name} in MainActivity.onCreate")
        setContent {
            val appLanguage by preferencesRepository.appLanguage.collectAsState(initial = "system")
            val appTimeFormat by preferencesRepository.appTimeFormat.collectAsState(initial = com.kaynanamtv.domain.model.AppTimeFormat.SYSTEM)
            val appColorTheme by preferencesRepository.appColorTheme.collectAsState(initial = initialTheme)
            val visualEffectsMode by preferencesRepository.visualEffectsMode.collectAsState(initial = com.kaynanamtv.domain.model.VisualEffectsMode.AUTO)
            val initialForceUpdateBlocked = remember { preferencesRepository.getForceUpdateBlockedSynchronously() }
            val forceUpdateDecision by remoteConfigRepository.forceUpdateDecisionFlow
                .collectAsState(
                    initial = if (initialForceUpdateBlocked) {
                        com.kaynanamtv.domain.model.ForceUpdateDecision.BLOCKED_FORCE_UPDATE_REQUIRED
                    } else {
                        com.kaynanamtv.domain.model.ForceUpdateDecision.ALLOWED
                    }
                )
            val currentContext = LocalContext.current
            val isTelevision = com.kaynanamtv.app.device.rememberIsTelevisionDevice()
            
            val configuration = remember(appLanguage) {
                val locale = resolveAppLocale(
                    preferredLanguageTag = appLanguage,
                    baseConfiguration = this@MainActivity.resources.configuration
                )
                val conf = Configuration(this@MainActivity.resources.configuration)
                Locale.setDefault(locale)
                conf.setLocale(locale)
                conf.setLayoutDirection(locale)
                conf
            }
            val localizedContext = remember(configuration, currentContext) {
                val configurationContext = currentContext.createConfigurationContext(configuration)
                object : ContextWrapper(currentContext) {
                    override fun getResources(): Resources = configurationContext.resources
                    override fun getAssets(): AssetManager = configurationContext.assets
                    override fun getSystemService(name: String): Any? {
                        return if (name == Context.LAYOUT_INFLATER_SERVICE) {
                            configurationContext.getSystemService(name)
                        } else {
                            super.getSystemService(name)
                        }
                    }
                }
            }

            val visualEffectsProfile = remember(visualEffectsMode, isTelevision, localizedContext) {
                com.kaynanamtv.app.ui.theme.VisualEffectsResolver.resolve(
                    mode = visualEffectsMode,
                    context = localizedContext,
                    isTelevision = isTelevision
                )
            }

            val layoutDirection = remember(configuration) {
                if (TextUtils.getLayoutDirectionFromLocale(configuration.locales[0]) == View.LAYOUT_DIRECTION_RTL) {
                    LayoutDirection.Rtl
                } else {
                    LayoutDirection.Ltr
                }
            }

            CompositionLocalProvider(
                LocalContext provides localizedContext,
                LocalLayoutDirection provides layoutDirection,
                LocalAppTimeFormat provides appTimeFormat,
                com.kaynanamtv.app.ui.theme.LocalVisualEffectsProfile provides visualEffectsProfile
            ) {
                KaynanamTVTheme(colorTheme = appColorTheme) {
                    if (forceUpdateDecision == com.kaynanamtv.domain.model.ForceUpdateDecision.BLOCKED_FORCE_UPDATE_REQUIRED) {
                        com.kaynanamtv.app.ui.screens.update.ForceUpdateScreen()
                    } else {
                        AppNavigation(mainActivity = this@MainActivity)
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        applyImmersiveSystemUi()
        lifecycleScope.launch {
            (application as? KaynanamTVApp)?.checkForAppUpdates(force = true)
            remoteConfigRepository.checkRemoteConfig(force = true)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            applyImmersiveSystemUi()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleExternalIntent(intent)
    }

    var onPictureInPictureDismissed: (() -> Unit)? = null
    private var wasInPictureInPictureMode: Boolean = false

    override fun onUserLeaveHint() {
        super.onUserLeaveHint()
        enterPlayerPictureInPictureModeIfEligible()
    }

    override fun onPictureInPictureModeChanged(
        isInPictureInPictureMode: Boolean,
        newConfig: Configuration
    ) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        _pictureInPictureModeFlow.value = isInPictureInPictureMode
        if (isInPictureInPictureMode) {
            wasInPictureInPictureMode = true
        } else if (wasInPictureInPictureMode) {
            wasInPictureInPictureMode = false
            // When PiP is dismissed by user (X button or swipe away), the Activity is stopped/finishing.
            // When PiP is expanded back to fullscreen, the Activity transitions to STARTED / RESUMED.
            lifecycleScope.launch {
                delay(500)
                if (!lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED) || isFinishing) {
                    onPictureInPictureDismissed?.invoke()
                }
            }
        }
    }

    override fun onStop() {
        super.onStop()
        if (isFinishing && wasInPictureInPictureMode) {
            wasInPictureInPictureMode = false
            onPictureInPictureDismissed?.invoke()
        }
    }

    fun updatePlayerPictureInPictureState(
        enabled: Boolean,
        isPlaying: Boolean,
        videoWidth: Int,
        videoHeight: Int,
        pixelWidthHeightRatio: Float = 1f
    ) {
        if (!supportsPictureInPicture()) return
        playerPictureInPictureState = PlayerPictureInPictureState(
            enabled = enabled,
            isPlaying = isPlaying,
            aspectRatio = videoAspectRatioOrNull(videoWidth, videoHeight, pixelWidthHeightRatio)
        )
        applyPlayerPictureInPictureParams()
    }

    fun clearPlayerPictureInPictureState() {
        if (!supportsPictureInPicture()) return
        playerPictureInPictureState = PlayerPictureInPictureState()
        applyPlayerPictureInPictureParams()
    }

    fun clearExternalNavigationRequest() {
        _externalNavigationRequestFlow.value = null
    }

    fun openPlayer(request: PlayerNavigationRequest) {
        _externalNavigationRequestFlow.value = ExternalNavigationRequest.Player(request)
    }

    fun enterPlayerPictureInPictureModeFromPlayer(): Boolean {
        return enterPlayerPictureInPictureModeIfEligible(requirePlaying = false)
    }

    @Suppress("DEPRECATION")
    private fun applyImmersiveSystemUi() {
        val decorView = window.decorView
        WindowCompat.getInsetsController(window, decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }

    fun openCastRouteChooser() {
        startActivity(Intent(this, CastRouteChooserActivity::class.java))
    }

    private fun enterPlayerPictureInPictureModeIfEligible(requirePlaying: Boolean = true): Boolean {
        if (!supportsPictureInPicture() || isInPictureInPictureMode) {
            return false
        }
        val state = playerPictureInPictureState
        if (!state.enabled || (requirePlaying && !state.isPlaying)) {
            return false
        }
        return runCatching {
            PictureInPictureCompat.enter(this, state)
        }.getOrDefault(false)
    }

    private fun applyPlayerPictureInPictureParams() {
        if (!supportsPictureInPicture()) return
        runCatching {
            PictureInPictureCompat.apply(this, playerPictureInPictureState)
        }
    }

    private fun videoAspectRatioOrNull(
        videoWidth: Int,
        videoHeight: Int,
        pixelWidthHeightRatio: Float = 1f
    ): Rational? {
        if (videoWidth <= 0 || videoHeight <= 0) return null
        val safePixelRatio = pixelWidthHeightRatio.takeIf { it.isFinite() && it > 0f } ?: 1f
        val rawAspectRatio = (videoWidth * safePixelRatio) / videoHeight.toFloat()
        val clampedAspectRatio = rawAspectRatio
            .coerceIn(MIN_PIP_ASPECT_RATIO, MAX_PIP_ASPECT_RATIO)
        val numerator = (clampedAspectRatio * 10_000).toInt().coerceAtLeast(1)
        return Rational(numerator, 10_000)
    }

    private fun supportsPictureInPicture(): Boolean {
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.O &&
            packageManager.hasSystemFeature(PackageManager.FEATURE_PICTURE_IN_PICTURE) &&
            !isTelevisionDevice()
    }

    @androidx.annotation.RequiresApi(Build.VERSION_CODES.O)
    private object PictureInPictureCompat {
        fun enter(activity: MainActivity, state: PlayerPictureInPictureState): Boolean {
            val params = build(state)
            activity.setPictureInPictureParams(params)
            return activity.enterPictureInPictureMode(params)
        }

        fun apply(activity: MainActivity, state: PlayerPictureInPictureState) {
            activity.setPictureInPictureParams(build(state))
        }

        private fun build(
            state: PlayerPictureInPictureState
        ): android.app.PictureInPictureParams {
            val builder = android.app.PictureInPictureParams.Builder()
            state.aspectRatio?.let { builder.setAspectRatio(it) }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                builder.setAutoEnterEnabled(state.enabled && state.isPlaying)
            }
            return builder.build()
        }
    }

    private fun handleExternalIntent(intent: Intent?) {
        val request = intent?.toExternalNavigationRequest() ?: return
        _externalNavigationRequestFlow.value = request
    }

    private fun Intent.toExternalNavigationRequest(): ExternalNavigationRequest? {
        readPlayerRequestExtra()?.let { return ExternalNavigationRequest.Player(it) }
        readExternalDestinationExtra()?.let { return ExternalNavigationRequest.Destination(it) }
        getStringExtra(EXTRA_EXTERNAL_ROUTE)
            ?.let(ExternalDestination::fromLegacyRoute)
            ?.let { return ExternalNavigationRequest.Destination(it) }
        if (hasExtra(EXTRA_EXTERNAL_ROUTE)) {
            return ExternalNavigationRequest.Destination(ExternalDestination.Home)
        }
        readImportedPlaylistUri()?.let { return ExternalNavigationRequest.ImportM3u(it) }
        readImportedBackupUri()?.let { return ExternalNavigationRequest.ImportBackup(it) }

        val query = when (action) {
            Intent.ACTION_SEARCH,
            Intent.ACTION_ASSIST,
            RecognizerIntent.ACTION_VOICE_SEARCH_HANDS_FREE -> {
                getStringExtra(SearchManager.QUERY)
                    ?: getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            }

            else -> null
        }?.trim().orEmpty()

        query.takeIf { it.isNotBlank() }?.let(ExternalNavigationRequest::Search)?.let { return it }

        return if (action == Intent.ACTION_VIEW) {
            ExternalNavigationRequest.Destination(ExternalDestination.Home)
        } else {
            null
        }
    }

    private fun Intent.readImportedPlaylistUri(): String? {
        if (action != Intent.ACTION_VIEW) return null
        val targetUri = data ?: return null
        val normalizedPath = targetUri.toString().substringBefore('?').lowercase(Locale.ROOT)
        val mimeType = type?.lowercase(Locale.ROOT).orEmpty()
        val isPlaylistMime = mimeType in setOf(
            "audio/x-mpegurl",
            "audio/mpegurl",
            "application/x-mpegurl",
            "application/vnd.apple.mpegurl",
            "application/mpegurl"
        )
        val isPlaylistPath = normalizedPath.endsWith(".m3u") || normalizedPath.endsWith(".m3u8")
        if (!isPlaylistMime && !isPlaylistPath) return null
        return when (targetUri.scheme?.lowercase(Locale.ROOT)) {
            "content", "file" -> targetUri.toString()
            else -> null
        }
    }

    private fun Intent.readImportedBackupUri(): String? {
        val targetUri = when (action) {
            Intent.ACTION_VIEW -> data
            Intent.ACTION_SEND -> readStreamUriExtra()
            else -> null
        } ?: return null
        if (!isBackupJsonCandidate(targetUri)) return null
        return BackupFileBridge.copyToImportInbox(this@MainActivity, targetUri)?.toString()
            ?: targetUri.toString()
    }

    @Suppress("DEPRECATION")
    private fun Intent.readStreamUriExtra(): Uri? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        } else {
            getParcelableExtra(Intent.EXTRA_STREAM) as? Uri
        } ?: clipData?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.uri
    }

    private fun Intent.isBackupJsonCandidate(uri: Uri): Boolean {
        val normalizedPath = uri.toString().substringBefore('?').lowercase(Locale.ROOT)
        val mimeType = type?.lowercase(Locale.ROOT).orEmpty()
        val isJsonMime = mimeType in setOf("application/json", "text/json", "application/x-json")
        val isJsonPath = normalizedPath.endsWith(".json")
        val isGenericJsonFile = mimeType == "application/octet-stream" && isJsonPath
        if (!isJsonMime && !isJsonPath && !isGenericJsonFile) return false
        return uri.scheme?.lowercase(Locale.ROOT) in setOf("content", "file")
    }

    @Suppress("DEPRECATION")
    private fun Intent.readPlayerRequestExtra(): PlayerNavigationRequest? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getSerializableExtra(EXTRA_PLAYER_REQUEST, PlayerNavigationRequest::class.java)
        } else {
            getSerializableExtra(EXTRA_PLAYER_REQUEST) as? PlayerNavigationRequest
        }
    }

    @Suppress("DEPRECATION")
    private fun Intent.readExternalDestinationExtra(): ExternalDestination? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getSerializableExtra(EXTRA_EXTERNAL_DESTINATION, ExternalDestination::class.java)
        } else {
            getSerializableExtra(EXTRA_EXTERNAL_DESTINATION) as? ExternalDestination
        }
    }
}
