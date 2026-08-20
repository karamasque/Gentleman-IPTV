package com.kaynanamtv.app.ui.screens.settings

import android.app.Application
import com.kaynanamtv.app.R
import com.kaynanamtv.app.ui.model.LiveTvChannelMode
import com.kaynanamtv.app.ui.model.LiveTvQuickFilterVisibilityMode
import com.kaynanamtv.app.ui.model.VodViewMode
import com.kaynanamtv.domain.model.AppColorTheme
import com.kaynanamtv.domain.model.AppTimeFormat
import com.kaynanamtv.domain.model.AppHomeDashboardShelf
import com.kaynanamtv.domain.model.AppLandingDestination
import com.kaynanamtv.domain.model.AppTopLevelDestination
import com.kaynanamtv.domain.model.AudioOutputPreference
import com.kaynanamtv.domain.model.Category
import com.kaynanamtv.domain.model.ExternalPlaybackMode
import com.kaynanamtv.domain.model.ChannelNumberingMode
import com.kaynanamtv.domain.model.DecoderMode
import com.kaynanamtv.domain.model.GroupedChannelLabelMode
import com.kaynanamtv.domain.model.LiveChannelGroupingMode
import com.kaynanamtv.domain.model.LiveVariantPreferenceMode
import com.kaynanamtv.domain.model.PlaybackBufferMode
import com.kaynanamtv.domain.model.VodDuplicateHandlingMode
import com.kaynanamtv.domain.model.VodHttpProtocolMode
import com.kaynanamtv.domain.model.VodVariantPreferenceMode
import com.kaynanamtv.domain.model.PlayerSurfaceMode
import com.kaynanamtv.domain.model.Provider
import com.kaynanamtv.domain.model.RemoteShortcutPreferences
import com.kaynanamtv.domain.model.TimeshiftBackendPreference

enum class ProviderWarningAction {
    EPG,
    MOVIES,
    SERIES
}

enum class ProviderSyncSelection {
    SYNC_NOW,
    REBUILD_INDEX,
    TV,
    MOVIES,
    SERIES,
    EPG
}

internal data class SettingsPreferenceSnapshot(
    val providers: List<Provider>,
    val activeProviderId: Long?,
    val parentalControlLevel: Int,
    val hasParentalPin: Boolean,
    val appLanguage: String,
    val appColorTheme: AppColorTheme,
    val appLandingDestination: AppLandingDestination,
    val appTopLevelDestinations: List<AppTopLevelDestination>,
    val appHomeDashboardShelves: List<AppHomeDashboardShelf>,
    val appTimeFormat: AppTimeFormat,
    val startupSoundOption: Int = 1,
    val preferredAudioLanguage: String,
    val playerMediaSessionEnabled: Boolean,
    val playerFastRetryOnTransientFailures: Boolean,
    val playerAudioDecoderMode: DecoderMode,
    val playerVideoDecoderMode: DecoderMode,
    val playerEnginePreference: com.kaynanamtv.domain.model.PlayerEnginePreference,
    val playerPlaybackBufferMode: PlaybackBufferMode,
    val playerAudioOutputPreference: AudioOutputPreference,
    val playerCompatibilityMemoryEnabled: Boolean,
    val playerSurfaceMode: PlayerSurfaceMode,
    val playerVodHttpProtocolMode: VodHttpProtocolMode,
    val playerPlaybackSpeed: Float,
    val playerExternalPlaybackMode: ExternalPlaybackMode,
    val playerAudioVideoSyncEnabled: Boolean,
    val playerAudioVideoOffsetMs: Int,
    val centerTwoSlotMultiviewLayout: Boolean,
    val multiViewRespectProviderConnectionLimit: Boolean,
    val playerControlsTimeoutSeconds: Int,
    val playerLiveOverlayTimeoutSeconds: Int,
    val playerNoticeTimeoutSeconds: Int,
    val playerDiagnosticsTimeoutSeconds: Int,
    val subtitleTextScale: Float,
    val subtitleTextColor: Int,
    val subtitleBackgroundColor: Int,
    val playerLiveTranslationEnabled: Boolean,
    val playerLiveTranslationEndpoint: String,
    val wifiMaxVideoHeight: Int?,
    val ethernetMaxVideoHeight: Int?,
    val playerTimeshiftEnabled: Boolean,
    val playerTimeshiftDepthMinutes: Int,
    val playerTimeshiftBackend: TimeshiftBackendPreference,
    val defaultStopPlaybackTimerMinutes: Int,
    val defaultIdleStandbyTimerMinutes: Int,
    val lastSpeedTestMegabits: Double?,
    val lastSpeedTestTimestamp: Long?,
    val lastSpeedTestTransport: String?,
    val lastSpeedTestRecommendedHeight: Int?,
    val lastSpeedTestEstimated: Boolean,
    val isIncognitoMode: Boolean,
    val useXtreamTextClassification: Boolean,
    val xtreamBase64TextCompatibility: Boolean,
    val liveTvChannelMode: LiveTvChannelMode,
    val showLiveSourceSwitcher: Boolean,
    val showFavoritesCategory: Boolean,
    val showAllChannelsCategory: Boolean,
    val showRecentChannelsCategory: Boolean,
    val remoteShortcutPreferences: RemoteShortcutPreferences,
    val liveTvCategoryFilters: List<String>,
    val liveTvQuickFilterVisibilityMode: LiveTvQuickFilterVisibilityMode,
    val hideDecorativeLiveRows: Boolean,
    val liveChannelNumberingMode: ChannelNumberingMode,
    val liveChannelGroupingMode: LiveChannelGroupingMode,
    val groupedChannelLabelMode: GroupedChannelLabelMode,
    val liveVariantPreferenceMode: LiveVariantPreferenceMode,
    val vodViewMode: VodViewMode,
    val vodInfiniteScroll: Boolean,
    val vodDuplicateHandlingMode: VodDuplicateHandlingMode,
    val vodVariantPreferenceMode: VodVariantPreferenceMode,
    val guideDefaultCategoryId: Long,
    val guideDefaultCategoryOptions: List<Category>,
    val preventStandbyDuringPlayback: Boolean,
    val zapAutoRevert: Boolean,
    val autoPlayNextEpisode: Boolean,
    val autoCheckAppUpdates: Boolean,
    val autoDownloadAppUpdates: Boolean,
    val lastAppUpdateCheckAt: Long?,
    val cachedAppUpdateVersionName: String?,
    val cachedAppUpdateVersionCode: Int?,
    val cachedAppUpdateReleaseUrl: String?,
    val cachedAppUpdateDownloadUrl: String?,
    val cachedAppUpdateDownloadSha256: String?,
    val cachedAppUpdateReleaseNotes: String,
    val cachedAppUpdatePublishedAt: String?
)

internal fun ProviderSyncSelection.label(application: Application): String = when (this) {
    ProviderSyncSelection.SYNC_NOW -> application.getString(R.string.settings_sync_option_sync_now)
    ProviderSyncSelection.REBUILD_INDEX -> application.getString(R.string.settings_sync_option_rebuild_index)
    ProviderSyncSelection.TV -> application.getString(R.string.settings_sync_option_tv)
    ProviderSyncSelection.MOVIES -> application.getString(R.string.settings_sync_option_movies)
    ProviderSyncSelection.SERIES -> application.getString(R.string.settings_sync_option_series)
    ProviderSyncSelection.EPG -> application.getString(R.string.settings_sync_option_epg)
}
