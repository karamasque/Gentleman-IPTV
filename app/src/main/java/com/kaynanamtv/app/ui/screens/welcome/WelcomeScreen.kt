package com.kaynanamtv.app.ui.screens.welcome

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.tv.material3.ButtonDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Surface
import androidx.tv.material3.SurfaceDefaults
import androidx.tv.material3.Text
import androidx.compose.animation.core.*
import androidx.compose.animation.Animatable
import androidx.compose.foundation.Image
import com.kaynanamtv.app.BuildConfig
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import com.kaynanamtv.app.R
import com.kaynanamtv.app.ui.components.shell.StatusPill
import com.kaynanamtv.app.ui.design.AppColors
import com.kaynanamtv.app.ui.interaction.TvButton
import com.kaynanamtv.data.sync.SyncProgressBus
import kotlinx.coroutines.tasks.await
import com.kaynanamtv.domain.model.*
import android.speech.tts.TextToSpeech
import java.util.Locale
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.DisposableEffect
import androidx.compose.foundation.Canvas
import androidx.compose.ui.platform.LocalContext
import com.kaynanamtv.domain.repository.AuthRepository
import com.kaynanamtv.domain.repository.ProviderRepository
import com.kaynanamtv.domain.sync.Section
import com.kaynanamtv.domain.sync.SyncProgress
import com.kaynanamtv.domain.usecase.M3uProviderSetupCommand
import com.kaynanamtv.domain.usecase.ValidateAndAddProvider
import com.kaynanamtv.domain.usecase.XtreamProviderSetupCommand
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class WelcomeViewModel @Inject constructor(
    private val providerRepository: ProviderRepository,
    private val validateAndAddProvider: ValidateAndAddProvider,
    private val authRepository: AuthRepository,
    syncProgressBus: SyncProgressBus,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _hasProviders = MutableStateFlow<Boolean?>(null)
    val hasProviders: StateFlow<Boolean?> = _hasProviders.asStateFlow()

    private val _trialStatus = MutableStateFlow<TrialStatus?>(null)
    val trialStatus: StateFlow<TrialStatus?> = _trialStatus.asStateFlow()

    private val acceptingProgress = MutableStateFlow(true)

    private val _remoteProviders = MutableStateFlow<List<Map<String, Any>>?>(null)
    val remoteProviders: StateFlow<List<Map<String, Any>>?> = _remoteProviders.asStateFlow()

    private val _isCheckingCloud = MutableStateFlow(false)
    val isCheckingCloud: StateFlow<Boolean> = _isCheckingCloud.asStateFlow()

    val syncProgress: StateFlow<SyncProgress?> =
        combine(syncProgressBus.flow, acceptingProgress) { progress, accept ->
            if (accept) progress else null
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        viewModelScope.launch {
            checkTrial()
        }
        viewModelScope.launch {
            launch {
                maybeSeedDevProvider()
            }
            providerRepository.getProviders()
                .map { it.isNotEmpty() }
                .collect { hasLocal ->
                    _hasProviders.value = hasLocal
                    if (hasLocal == false) {
                        checkForCloudProviders()
                    }
                }
        }
        viewModelScope.launch {
            kotlinx.coroutines.withTimeoutOrNull(3000) {
                _hasProviders
                    .filterNotNull()
                    .first()
            }
            acceptingProgress.value = false
        }
    }

    fun checkForCloudProviders() {
        val user = com.google.firebase.auth.FirebaseAuth.getInstance().currentUser
        if (user == null) {
            _remoteProviders.value = emptyList()
            return
        }
        viewModelScope.launch {
            _isCheckingCloud.value = true
            try {
                val snapshot = com.google.firebase.firestore.FirebaseFirestore.getInstance()
                    .collection("users").document(user.uid)
                    .collection("providers").get().await()
                
                val list = snapshot.documents.mapNotNull { it.data }
                _remoteProviders.value = list
                android.util.Log.d("WelcomeViewModel", "Found ${list.count()} cloud providers")
                if (list.isNotEmpty()) {
                    restoreAllRemoteProviders(list)
                }
            } catch (e: Exception) {
                android.util.Log.e("WelcomeViewModel", "Failed to check cloud providers", e)
                _remoteProviders.value = emptyList()
            } finally {
                _isCheckingCloud.value = false
            }
        }
    }

    fun restoreAllRemoteProviders(remoteProviders: List<Map<String, Any>>) {
        viewModelScope.launch {
            try {
                // De-duplicate remote providers by unique connection keys to prevent duplicates
                val uniqueProviders = remoteProviders.distinctBy {
                    val serverUrl = it["serverUrl"] as? String ?: ""
                    val username = it["username"] as? String ?: ""
                    val m3uUrl = it["m3uUrl"] as? String ?: ""
                    val stalkerMacAddress = it["stalkerMacAddress"] as? String ?: ""
                    val type = it["type"] as? String ?: ""
                    "$type|$serverUrl|$username|$m3uUrl|$stalkerMacAddress"
                }

                uniqueProviders.forEach { providerData ->
                    val type = ProviderType.valueOf(providerData["type"] as String)
                    val idVal = (providerData["id"] as? Long ?: (providerData["id"] as? String)?.toLongOrNull()) ?: 0L
                    val provider = com.kaynanamtv.domain.model.Provider(
                        id = idVal,
                        name = providerData["name"] as String,
                        type = type,
                        serverUrl = providerData["serverUrl"] as? String ?: "",
                        username = providerData["username"] as? String ?: "",
                        password = providerData["password"] as? String ?: "",
                        m3uUrl = providerData["m3uUrl"] as? String ?: "",
                        epgUrl = providerData["epgUrl"] as? String ?: "",
                        httpUserAgent = providerData["httpUserAgent"] as? String ?: "",
                        httpHeaders = providerData["httpHeaders"] as? String ?: "",
                        stalkerMacAddress = providerData["stalkerMacAddress"] as? String ?: "",
                        stalkerDeviceProfile = providerData["stalkerDeviceProfile"] as? String ?: "",
                        stalkerDeviceTimezone = providerData["stalkerDeviceTimezone"] as? String ?: "",
                        stalkerDeviceLocale = providerData["stalkerDeviceLocale"] as? String ?: "",
                        stalkerSerialNumber = providerData["stalkerSerialNumber"] as? String ?: "",
                        stalkerDeviceId = providerData["stalkerDeviceId"] as? String ?: "",
                        stalkerDeviceId2 = providerData["stalkerDeviceId2"] as? String ?: "",
                        stalkerSignature = providerData["stalkerSignature"] as? String ?: "",
                        stalkerAdvancedOptionsJson = providerData["stalkerAdvancedOptionsJson"] as? String ?: "",
                        stalkerAuthMode = StalkerAuthMode.valueOf(providerData["stalkerAuthMode"] as? String ?: "AUTO"),
                        stalkerPortalProfile = StalkerPortalProfile.valueOf(providerData["stalkerPortalProfile"] as? String ?: "MAG_BASIC"),
                        stalkerPortalFingerprint = StalkerPortalFingerprint.valueOf(providerData["stalkerPortalFingerprint"] as? String ?: "BASIC_MAC"),
                        stalkerMagPreset = StalkerMagPreset.valueOf(providerData["stalkerMagPreset"] as? String ?: "GENERIC_SAFE"),
                        stalkerLastBootstrapRecipe = StalkerBootstrapRecipe.valueOf(providerData["stalkerLastBootstrapRecipe"] as? String ?: "GENERIC_SAFE"),
                        stalkerEndpointPreference = StalkerEndpointPreference.valueOf(providerData["stalkerEndpointPreference"] as? String ?: "AUTO"),
                        stalkerCookieMode = StalkerCookieMode.valueOf(providerData["stalkerCookieMode"] as? String ?: "NONE"),
                        stalkerPlaybackBackendHint = StalkerPlaybackBackendHint.valueOf(providerData["stalkerPlaybackBackendHint"] as? String ?: "AUTO"),
                        stalkerLastPlaybackMode = providerData["stalkerLastPlaybackMode"] as? String,
                        stalkerCredentialsRequired = providerData["stalkerCredentialsRequired"] as? Boolean ?: false,
                        stalkerMacRequired = providerData["stalkerMacRequired"] as? Boolean ?: true,
                        stalkerUsesTemporaryLinks = providerData["stalkerUsesTemporaryLinks"] as? Boolean ?: false,
                        stalkerModuleRestricted = providerData["stalkerModuleRestricted"] as? Boolean ?: false,
                        stalkerStrictFingerprintRequired = providerData["stalkerStrictFingerprintRequired"] as? Boolean ?: false,
                        stalkerRecipeFallbackUsed = providerData["stalkerRecipeFallbackUsed"] as? Boolean ?: false,
                        stalkerRecipeRediscoveryAttempts = (providerData["stalkerRecipeRediscoveryAttempts"] as? Long)?.toInt() ?: 0,
                        isActive = providerData["isActive"] as? Boolean ?: true,
                        maxConnections = (providerData["maxConnections"] as? Long)?.toInt() ?: 1,
                        expirationDate = providerData["expirationDate"] as? Long,
                        apiVersion = providerData["apiVersion"] as? String,
                        allowedOutputFormats = providerData["allowedOutputFormats"] as? List<String> ?: emptyList(),
                        epgSyncMode = ProviderEpgSyncMode.valueOf(providerData["epgSyncMode"] as? String ?: "UPFRONT"),
                        guideSourcePolicy = GuideSourcePolicy.valueOf(providerData["guideSourcePolicy"] as? String ?: "AUTO"),
                        channelLogoSourcePolicy = ChannelLogoSourcePolicy.valueOf(providerData["channelLogoSourcePolicy"] as? String ?: "SUPPLIER_PREFERRED"),
                        xtreamFastSyncEnabled = providerData["xtreamFastSyncEnabled"] as? Boolean ?: true,
                        xtreamLiveSyncMode = ProviderXtreamLiveSyncMode.valueOf(providerData["xtreamLiveSyncMode"] as? String ?: "AUTO"),
                        m3uVodClassificationEnabled = providerData["m3uVodClassificationEnabled"] as? Boolean ?: false,
                        status = ProviderStatus.valueOf(providerData["status"] as? String ?: "UNKNOWN"),
                        lastSyncedAt = providerData["lastSyncedAt"] as? Long ?: 0L,
                        createdAt = providerData["createdAt"] as? Long ?: System.currentTimeMillis()
                    )
                    providerRepository.addProvider(provider)
                }
            } catch (e: Exception) {
                android.util.Log.e("WelcomeViewModel", "Failed to restore all providers", e)
            }
        }
    }

    suspend fun checkTrial() {
        _trialStatus.value = authRepository.checkTrialStatus()
    }

    fun logout() {
        viewModelScope.launch {
            authRepository.logout()
            checkTrial()
        }
    }

    fun onRefreshTrial() {
        viewModelScope.launch {
            checkTrial()
        }
    }


    private suspend fun maybeSeedDevProvider() {
        try {
            if (providerRepository.getProviders().first().isNotEmpty()) return

            val xtreamServer = BuildConfig.XTREAM_DEV_SERVER
            val xtreamUser = BuildConfig.XTREAM_DEV_USERNAME
            val xtreamPass = BuildConfig.XTREAM_DEV_PASSWORD
            if (xtreamServer.isNotBlank() && xtreamUser.isNotBlank() && xtreamPass.isNotBlank()) {
                kotlinx.coroutines.withTimeoutOrNull(5000) {
                    validateAndAddProvider.loginXtream(
                        XtreamProviderSetupCommand(
                            serverUrl = xtreamServer,
                            username = xtreamUser,
                            password = xtreamPass,
                            name = BuildConfig.XTREAM_DEV_NAME.ifBlank { "Dev (seeded)" },
                            xtreamFastSyncEnabled = true
                        )
                    )
                }
                return
            }

            val m3uUrl = BuildConfig.M3U_DEV_URL
            if (m3uUrl.isNotBlank()) {
                kotlinx.coroutines.withTimeoutOrNull(5000) {
                    validateAndAddProvider.addM3u(
                        M3uProviderSetupCommand(
                            url = m3uUrl,
                            name = BuildConfig.M3U_DEV_NAME.ifBlank { "Dev M3U (seeded)" }
                        )
                    )
                }
            }
        } catch (e: Exception) {
            android.util.Log.e("WelcomeViewModel", "Error seeding dev provider", e)
        }
    }
}

@Composable
fun WelcomeScreen(
    onNavigateToHome: () -> Unit,
    startupReady: Boolean = true,
    onNavigateToSetup: () -> Unit,
    onNavigateToAuth: () -> Unit,
    viewModel: WelcomeViewModel = hiltViewModel()
) {
    val hasProviders by viewModel.hasProviders.collectAsStateWithLifecycle()
    val syncProgress by viewModel.syncProgress.collectAsStateWithLifecycle()
    val trialStatus by viewModel.trialStatus.collectAsStateWithLifecycle()
    val isCheckingCloud by viewModel.isCheckingCloud.collectAsStateWithLifecycle()
    val remoteProviders by viewModel.remoteProviders.collectAsStateWithLifecycle()

    val context = LocalContext.current
    var introQuote by remember { mutableStateOf("") }
    var tts by remember { mutableStateOf<TextToSpeech?>(null) }
    var showSplashIntro by remember { mutableStateOf(true) }

    val funnyQuotes = remember {
        listOf(
            "Kalk da bulaşıkları yıka! Bütün gün televizyon izliyorsun! Neyse, hadi açtım Kaynanam TV'yi.",
            "Çay demledin mi bari? Boş boş ekrana bakıyorsun! Al hadi, Kaynanam TV açıldı.",
            "Aman iyi! Yine geldin ekran başına. Kumandayı bana ver, benim dizim başlayacak!",
            "Benim gibi kaynanayı mumla ararsın mumla! Neyse, açtım hadi Kaynanam TV'yi, izle bakalım."
        )
    }

    LaunchedEffect(Unit) {
        introQuote = funnyQuotes.random()
        // TextToSpeech disabled as requested
        /*
        tts = TextToSpeech(context) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.let { textToSpeech ->
                    val result = textToSpeech.setLanguage(Locale("tr", "TR"))
                    if (result != TextToSpeech.LANG_MISSING_DATA && result != TextToSpeech.LANG_NOT_SUPPORTED) {
                        textToSpeech.setPitch(1.4f)
                        textToSpeech.setSpeechRate(1.15f)
                        textToSpeech.speak(introQuote, TextToSpeech.QUEUE_FLUSH, null, "kaynana_tts")
                    }
                }
            }
        }
        */
        kotlinx.coroutines.delay(3800)
        showSplashIntro = false
    }

    DisposableEffect(Unit) {
        onDispose {
            tts?.stop()
            tts?.shutdown()
        }
    }

    LaunchedEffect(trialStatus) {
        if (trialStatus == TrialStatus.NO_SESSION) {
            onNavigateToAuth()
        }
    }

    val lifecycle = androidx.compose.ui.platform.LocalLifecycleOwner.current.lifecycle
    LaunchedEffect(hasProviders, startupReady, trialStatus, showSplashIntro) {
        if (trialStatus == TrialStatus.ACTIVE && !showSplashIntro) {
            when (hasProviders) {
                true -> {
                    if (startupReady) {
                        while (!lifecycle.currentState.isAtLeast(androidx.lifecycle.Lifecycle.State.RESUMED)) {
                            kotlinx.coroutines.delay(50)
                        }
                        onNavigateToHome()
                    }
                }
                false -> Unit
                null -> Unit
            }
        }
    }

    PremiumBackground {
        if (showSplashIntro) {
            KaynanaSplashIntro(quote = introQuote)
        } else {
            when (trialStatus) {
                TrialStatus.ACTIVE -> {
                    when {
                        hasProviders == true -> {
                            WelcomeLoadingCard(
                                syncProgress = syncProgress,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(32.dp)
                            )
                        }
                        isCheckingCloud -> {
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.Center).size(48.dp),
                                color = AppColors.Brand
                            )
                        }
                        !remoteProviders.isNullOrEmpty() -> {
                            RestoringCloudProgressCard(
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(32.dp)
                            )
                        }
                        else -> {
                            WelcomeStartCard(
                                onNavigateToHome = onNavigateToHome,
                                onNavigateToSetup = onNavigateToSetup,
                                modifier = Modifier
                                    .align(Alignment.Center)
                                    .padding(32.dp)
                            )
                        }
                    }
                }
                TrialStatus.EXPIRED -> {
                    TrialExpiredCard(
                        onLogout = viewModel::logout,
                        onNavigateToHome = onNavigateToHome,
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(32.dp)
                    )
                }
                else -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center).size(48.dp),
                        color = AppColors.Brand
                    )
                }
            }
        }
    }
}

@Composable
private fun TrialExpiredCard(
    onLogout: () -> Unit,
    onNavigateToHome: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderBrush = Brush.linearGradient(
        colors = listOf(Color.Red.copy(alpha = 0.5f), AppColors.Brand.copy(alpha = 0.5f))
    )
    Box(
        modifier = modifier
            .widthIn(max = 500.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xE60A0E1A))
            .border(androidx.compose.foundation.BorderStroke(1.5.dp, borderBrush), RoundedCornerShape(24.dp))
            .padding(horizontal = 40.dp, vertical = 34.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            StatusPill(
                label = "DENEME SÜRESİ BİTTİ",
                containerColor = Color.Red.copy(alpha = 0.2f),
                contentColor = Color.Red
            )
            Text(
                text = "Kullanım Süreniz Sona Erdi",
                style = MaterialTheme.typography.headlineMedium.copy(
                    brush = Brush.linearGradient(
                        colors = listOf(Color.Red, AppColors.BrandStrong)
                    )
                ),
                fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "KaynanamTV 7 günlük ücretsiz deneme süreniz dolmuştur. Uygulamayı Free olarak kullanmaya devam edebilir veya Ayarlar > Üyelik menüsünden Premium pakete geçiş yapabilirsiniz.",
                style = MaterialTheme.typography.bodyLarge,
                color = AppColors.TextSecondary,
                textAlign = TextAlign.Center
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TvButton(
                    onClick = onNavigateToHome,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = "Ana Ekrana Geç (Ücretsiz)")
                }
                TvButton(
                    onClick = onLogout,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.colors(
                        containerColor = AppColors.SurfaceElevated,
                        focusedContainerColor = Color.White,
                        contentColor = AppColors.TextPrimary,
                        focusedContentColor = Color(0xFF0A0E1A)
                    )
                ) {
                    Text(text = "Çıkış Yap")
                }
            }
        }
    }
}

@Composable
private fun WelcomeLoadingCard(
    syncProgress: SyncProgress?,
    modifier: Modifier = Modifier
) {
    val borderBrush = Brush.linearGradient(
        colors = listOf(AppColors.Brand.copy(alpha = 0.5f), AppColors.NeonCyan.copy(alpha = 0.5f))
    )
    Box(
        modifier = modifier
            .widthIn(max = 480.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xE60A0E1A)) // Glassmorphic ultra-dark navy
            .border(androidx.compose.foundation.BorderStroke(1.5.dp, borderBrush), RoundedCornerShape(24.dp))
            .padding(horizontal = 36.dp, vertical = 28.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            val pillLabel = if (syncProgress != null) {
                stringResource(sectionLabelRes(syncProgress.section))
            } else {
                stringResource(R.string.app_name)
            }
            val pillColor = if (syncProgress != null) {
                sectionColor(syncProgress.section)
            } else {
                AppColors.BrandMuted
            }
            StatusPill(
                label = pillLabel,
                containerColor = pillColor
            )
            Spacer(modifier = Modifier.height(18.dp))
            if (syncProgress == null) {
                CircularProgressIndicator(
                    modifier = Modifier.size(36.dp),
                    color = AppColors.Brand
                )
                Spacer(modifier = Modifier.height(18.dp))
            }
            Text(
                text = stringResource(R.string.welcome_loading_title),
                style = MaterialTheme.typography.titleLarge,
                color = AppColors.TextPrimary,
                textAlign = TextAlign.Center
            )
            Spacer(modifier = Modifier.height(6.dp))
            val subtitle = if (syncProgress != null && syncProgress.currentLabel.isNotBlank()) {
                syncProgress.currentLabel
            } else {
                stringResource(R.string.welcome_loading_subtitle)
            }
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = AppColors.TextSecondary,
                textAlign = TextAlign.Center
            )
            if (syncProgress != null) {
                Spacer(modifier = Modifier.height(14.dp))
                if (syncProgress.total > 0) {
                    LinearProgressIndicator(
                        progress = { syncProgress.current.toFloat() / syncProgress.total.toFloat() },
                        modifier = Modifier.width(260.dp),
                        color = AppColors.Brand,
                        trackColor = AppColors.BrandMuted
                    )
                } else {
                    LinearProgressIndicator(
                        modifier = Modifier.width(260.dp),
                        color = AppColors.Brand,
                        trackColor = AppColors.BrandMuted
                    )
                }
                Spacer(modifier = Modifier.height(10.dp))
                Text(
                    text = stringResource(
                        R.string.sync_items_indexed_format,
                        syncProgress.itemsIndexed
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    color = AppColors.TextSecondary
                )
            }
        }
    }
}

@Composable
private fun WelcomeStartCard(
    onNavigateToHome: () -> Unit,
    onNavigateToSetup: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderBrush = Brush.linearGradient(
        colors = listOf(AppColors.Brand.copy(alpha = 0.5f), AppColors.NeonCyan.copy(alpha = 0.5f))
    )
    Box(
        modifier = modifier
            .widthIn(max = 640.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xE60A0E1A))
            .border(androidx.compose.foundation.BorderStroke(1.5.dp, borderBrush), RoundedCornerShape(24.dp))
            .padding(horizontal = 40.dp, vertical = 34.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            StatusPill(
                label = "KAYNANAM TV",
                containerColor = AppColors.BrandMuted,
                contentColor = AppColors.BrandStrong
            )
            Text(
                text = "Televizyon Keyfinizi Katlayın",
                style = MaterialTheme.typography.headlineMedium.copy(
                    brush = Brush.linearGradient(
                        colors = listOf(AppColors.BrandStrong, AppColors.NeonCyan)
                    )
                ),
                fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
            Text(
                text = stringResource(R.string.welcome_subtitle),
                style = MaterialTheme.typography.bodyLarge,
                color = AppColors.TextSecondary,
                textAlign = TextAlign.Center
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                TvButton(
                    onClick = onNavigateToSetup,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(text = stringResource(R.string.welcome_setup_provider))
                }
                TvButton(
                    onClick = onNavigateToHome,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.colors(
                        containerColor = AppColors.SurfaceElevated,
                        focusedContainerColor = Color.White,
                        contentColor = AppColors.TextPrimary,
                        focusedContentColor = Color(0xFF0A0E1A)
                    )
                ) {
                    Text(text = stringResource(R.string.welcome_setup_later))
                }
            }
        }
    }
}

private fun sectionColor(section: Section): Color = when (section) {
    Section.LIVE -> AppColors.Brand
    Section.VOD -> AppColors.Success
    Section.SERIES -> AppColors.Warning
}

private fun sectionLabelRes(section: Section): Int = when (section) {
    Section.LIVE -> R.string.sync_section_live
    Section.VOD -> R.string.sync_section_vod
    Section.SERIES -> R.string.sync_section_series
}

private val POSTER_DRAWABLES = listOf(
    R.drawable.poster_1,
    R.drawable.poster_2,
    R.drawable.poster_3,
    R.drawable.poster_4,
    R.drawable.poster_5,
    R.drawable.poster_6,
    R.drawable.poster_7,
    R.drawable.poster_8,
    R.drawable.poster_9,
    R.drawable.poster_10,
    R.drawable.poster_11,
    R.drawable.poster_12,
    R.drawable.poster_13,
    R.drawable.poster_14,
    R.drawable.poster_15,
    R.drawable.poster_16,
    R.drawable.poster_17,
    R.drawable.poster_18,
    R.drawable.poster_19,
    R.drawable.poster_20,
    R.drawable.poster_21,
    R.drawable.poster_22,
    R.drawable.poster_23,
    R.drawable.poster_24,
    R.drawable.poster_25,
    R.drawable.poster_26,
    R.drawable.poster_27,
    R.drawable.poster_28,
    R.drawable.poster_29,
    R.drawable.poster_30,
    R.drawable.poster_31,
    R.drawable.poster_32,
    R.drawable.poster_33,
    R.drawable.poster_34,
    R.drawable.poster_35,
    R.drawable.poster_36,
    R.drawable.poster_37,
    R.drawable.poster_38,
    R.drawable.poster_39,
    R.drawable.poster_40,
    R.drawable.poster_41,
    R.drawable.poster_42,
    R.drawable.poster_43,
    R.drawable.poster_44,
    R.drawable.poster_45
)

@Composable
fun PremiumBackground(
    content: @Composable BoxScope.() -> Unit
) {
    val entranceAlpha = remember { Animatable(0f) }
    val entranceScale = remember { Animatable(1.08f) }
    LaunchedEffect(Unit) {
        entranceAlpha.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 2500, easing = EaseInOutCubic)
        )
    }
    LaunchedEffect(Unit) {
        entranceScale.animateTo(
            targetValue = 1.0f,
            animationSpec = tween(durationMillis = 2800, easing = EaseOutCubic)
        )
    }

    val bgColor = Color(0xFF0E1929)

    Box(modifier = Modifier.fillMaxSize().background(bgColor)) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    alpha = entranceAlpha.value * 0.58f
                    scaleX = entranceScale.value * 1.25f
                    scaleY = entranceScale.value * 1.25f
                    rotationZ = -6f
                },
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Spacer(modifier = Modifier.height(10.dp))
            StaticPosterRow(POSTER_DRAWABLES.subList(0, 9))
            StaticPosterRow(POSTER_DRAWABLES.subList(9, 18))
            StaticPosterRow(POSTER_DRAWABLES.subList(18, 27))
            StaticPosterRow(POSTER_DRAWABLES.subList(27, 36))
            StaticPosterRow(POSTER_DRAWABLES.subList(36, 45))
        }

        // Lacivert hafif gradient overlay
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            bgColor.copy(alpha = 0.20f),
                            bgColor.copy(alpha = 0.40f),
                            bgColor.copy(alpha = 0.65f),
                            bgColor.copy(alpha = 0.82f)
                        )
                    )
                )
        )

        // Top-left purple/indigo glow
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0x2E8B5CF6), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(0f, 0f),
                        radius = 1200f
                    )
                )
        )

        // Bottom-right cyan/brand glow
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Color(0x2400D2FF), Color.Transparent),
                        center = androidx.compose.ui.geometry.Offset(1000f, 1500f),
                        radius = 1200f
                    )
                )
        )

        Box(modifier = Modifier.fillMaxSize()) {
            content()
        }
    }
}

@Composable
fun StaticPosterRow(postersList: List<Int>) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .height(140.dp)
    ) {
        for (drawableId in postersList) {
            Box(
                modifier = Modifier
                    .width(100.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(8.dp))
                    .background(Color(0xFF1A2744))
            ) {
                Image(
                    painter = painterResource(id = drawableId),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

@Composable
private fun RestoringCloudProgressCard(
    modifier: Modifier = Modifier
) {
    val borderBrush = Brush.linearGradient(
        colors = listOf(AppColors.Brand.copy(alpha = 0.5f), AppColors.NeonCyan.copy(alpha = 0.5f))
    )
    Box(
        modifier = modifier
            .widthIn(max = 480.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Color(0xE60A0E1A))
            .border(androidx.compose.foundation.BorderStroke(1.5.dp, borderBrush), RoundedCornerShape(24.dp))
            .padding(horizontal = 40.dp, vertical = 34.dp)
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            StatusPill(
                label = "KAYNANAM TV - BULUT YEDEĞİ",
                containerColor = AppColors.BrandMuted,
                contentColor = AppColors.BrandStrong
            )
            Text(
                text = "Kayıtlı Kütüphaneleriniz Yükleniyor",
                style = MaterialTheme.typography.headlineMedium.copy(
                    brush = Brush.linearGradient(
                        colors = listOf(AppColors.BrandStrong, AppColors.NeonCyan)
                    )
                ),
                fontWeight = androidx.compose.ui.text.font.FontWeight.ExtraBold,
                textAlign = TextAlign.Center
            )
            Text(
                text = "Hesabınızdaki IPTV listeleri algılandı ve bu cihaza otomatik olarak geri yükleniyor. Lütfen bekleyin...",
                style = MaterialTheme.typography.bodyLarge,
                color = AppColors.TextSecondary,
                textAlign = TextAlign.Center
            )
            CircularProgressIndicator(
                modifier = Modifier.size(36.dp),
                color = AppColors.Brand
            )
        }
    }
}

@Composable
fun KaynanaSplashIntro(quote: String) {
    val infiniteTransition = rememberInfiniteTransition(label = "oklava")
    val oklavaRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "oklava_rotation"
    )

    val sunburstRotation by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = -360f,
        animationSpec = infiniteRepeatable(
            animation = tween(20000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "sunburst_rotation"
    )

    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.06f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = EaseInOutSine),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    var visibleTextLength by remember { mutableStateOf(0) }
    LaunchedEffect(quote) {
        if (quote.isNotEmpty()) {
            visibleTextLength = 0
            for (i in 1..quote.length) {
                visibleTextLength = i
                kotlinx.coroutines.delay(45)
            }
        }
    }

    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        ComicSunburstBackground(rotationAngle = sunburstRotation)

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (quote.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .padding(bottom = 24.dp)
                        .widthIn(max = 380.dp)
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0xFFFFF2CC))
                        .border(2.5.dp, Color(0xFFF1C232), RoundedCornerShape(16.dp))
                        .padding(16.dp)
                        .graphicsLayer {
                            scaleX = pulseScale
                            scaleY = pulseScale
                        }
                ) {
                    Text(
                        text = quote.take(visibleTextLength),
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = Color(0xFF7F6000),
                            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold
                        ),
                        textAlign = TextAlign.Center
                    )
                }
            }

            Box(
                modifier = Modifier.size(240.dp),
                contentAlignment = Alignment.Center
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .graphicsLayer {
                            rotationZ = oklavaRotation
                        }
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        
                        drawRoundRect(
                            color = Color(0xFFD7CCC8),
                            topLeft = androidx.compose.ui.geometry.Offset(w * 0.1f, h * 0.44f),
                            size = androidx.compose.ui.geometry.Size(w * 0.8f, h * 0.12f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f)
                        )
                        drawRoundRect(
                            color = Color(0xFF8D6E63),
                            topLeft = androidx.compose.ui.geometry.Offset(w * 0.01f, h * 0.47f),
                            size = androidx.compose.ui.geometry.Size(w * 0.09f, h * 0.06f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                        )
                        drawRoundRect(
                            color = Color(0xFF8D6E63),
                            topLeft = androidx.compose.ui.geometry.Offset(w * 0.9f, h * 0.47f),
                            size = androidx.compose.ui.geometry.Size(w * 0.09f, h * 0.06f),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(4f, 4f)
                        )
                    }
                }

                Canvas(modifier = Modifier.size(160.dp)) {
                    val w = size.width
                    val h = size.height
                    val cx = w / 2f
                    val cy = h / 2f
                    
                    drawCircle(
                        color = Color(0xFF673AB7),
                        radius = h * 0.44f,
                        center = androidx.compose.ui.geometry.Offset(cx, cy - h * 0.04f)
                    )
                    
                    drawCircle(
                        color = Color(0xFFFFD180),
                        radius = h * 0.35f,
                        center = androidx.compose.ui.geometry.Offset(cx, cy)
                    )
                    
                    val scarfPath = androidx.compose.ui.graphics.Path().apply {
                        moveTo(cx - h * 0.35f, cy - h * 0.05f)
                        quadraticTo(cx, cy - h * 0.42f, cx + h * 0.35f, cy - h * 0.05f)
                        lineTo(cx + h * 0.28f, cy - h * 0.35f)
                        quadraticTo(cx, cy - h * 0.48f, cx - h * 0.28f, cy - h * 0.35f)
                        close()
                    }
                    drawPath(path = scarfPath, color = Color(0xFF512DA8))
                    
                    val glassRadius = h * 0.095f
                    val glassY = cy - h * 0.03f
                    val leftGlassX = cx - h * 0.12f
                    val rightGlassX = cx + h * 0.12f
                    
                    drawCircle(
                        color = Color(0xFF212121),
                        radius = glassRadius,
                        center = androidx.compose.ui.geometry.Offset(leftGlassX, glassY),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 8f)
                    )
                    drawCircle(
                        color = Color(0xFF212121),
                        radius = glassRadius,
                        center = androidx.compose.ui.geometry.Offset(rightGlassX, glassY),
                        style = androidx.compose.ui.graphics.drawscope.Stroke(width = 8f)
                    )
                    drawLine(
                        color = Color(0xFF212121),
                        start = androidx.compose.ui.geometry.Offset(leftGlassX + glassRadius, glassY),
                        end = androidx.compose.ui.geometry.Offset(rightGlassX - glassRadius, glassY),
                        strokeWidth = 10f
                    )
                    
                    drawCircle(
                        color = Color(0xFF000000),
                        radius = h * 0.022f,
                        center = androidx.compose.ui.geometry.Offset(leftGlassX, glassY)
                    )
                    drawCircle(
                        color = Color(0xFF000000),
                        radius = h * 0.022f,
                        center = androidx.compose.ui.geometry.Offset(rightGlassX, glassY)
                    )
                    
                    drawLine(
                        color = Color(0xFF212121),
                        start = androidx.compose.ui.geometry.Offset(leftGlassX - h * 0.08f, glassY - h * 0.13f),
                        end = androidx.compose.ui.geometry.Offset(leftGlassX + h * 0.04f, glassY - h * 0.08f),
                        strokeWidth = 9f
                    )
                    drawLine(
                        color = Color(0xFF212121),
                        start = androidx.compose.ui.geometry.Offset(rightGlassX + h * 0.08f, glassY - h * 0.13f),
                        end = androidx.compose.ui.geometry.Offset(rightGlassX - h * 0.04f, glassY - h * 0.08f),
                        strokeWidth = 9f
                    )
                    
                    val mouthPath = androidx.compose.ui.graphics.Path().apply {
                        moveTo(cx - h * 0.09f, cy + h * 0.13f)
                        quadraticTo(cx, cy + h * 0.24f, cx + h * 0.09f, cy + h * 0.13f)
                        quadraticTo(cx, cy + h * 0.06f, cx - h * 0.09f, cy + h * 0.13f)
                        close()
                    }
                    drawPath(path = mouthPath, color = Color(0xFFD32F2F))
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            Text(
                text = "KAYNANAM TV",
                style = MaterialTheme.typography.displaySmall.copy(
                    brush = Brush.linearGradient(
                        colors = listOf(Color(0xFFE040FB), Color(0xFF00E5FF))
                    )
                ),
                fontWeight = androidx.compose.ui.text.font.FontWeight.Black,
                modifier = Modifier.graphicsLayer {
                    scaleX = pulseScale
                    scaleY = pulseScale
                }
            )
            
            Spacer(modifier = Modifier.height(6.dp))
            
            Text(
                text = "Oklava gücüyle açılıyor...",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextSecondary
            )
        }
    }
}

@Composable
fun ComicSunburstBackground(rotationAngle: Float) {
    Canvas(modifier = Modifier.fillMaxSize()) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f
        val radius = maxOf(w, h) * 1.5f
        val numWedges = 20
        val angleStep = 360f / numWedges
        
        rotate(rotationAngle, pivot = androidx.compose.ui.geometry.Offset(cx, cy)) {
            for (i in 0 until numWedges) {
                if (i % 2 == 0) {
                    val startAngle = i * angleStep
                    val path = androidx.compose.ui.graphics.Path().apply {
                        moveTo(cx, cy)
                        lineTo(
                            cx + radius * kotlin.math.cos(Math.toRadians(startAngle.toDouble())).toFloat(),
                            cy + radius * kotlin.math.sin(Math.toRadians(startAngle.toDouble())).toFloat()
                        )
                        lineTo(
                            cx + radius * kotlin.math.cos(Math.toRadians((startAngle + angleStep).toDouble())).toFloat(),
                            cy + radius * kotlin.math.sin(Math.toRadians((startAngle + angleStep).toDouble())).toFloat()
                        )
                        close()
                    }
                    drawPath(
                        path = path,
                        color = Color(0x0600D2FF)
                    )
                }
            }
        }
    }
}

