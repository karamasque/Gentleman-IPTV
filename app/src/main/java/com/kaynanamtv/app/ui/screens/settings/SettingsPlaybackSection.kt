package com.kaynanamtv.app.ui.screens.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.tv.material3.ClickableSurfaceDefaults
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.kaynanamtv.app.R
import com.kaynanamtv.app.ui.interaction.TvClickableSurface
import com.kaynanamtv.app.ui.theme.OnBackground
import com.kaynanamtv.app.ui.theme.OnSurface
import com.kaynanamtv.app.ui.theme.Primary
import com.kaynanamtv.domain.model.LiveStreamFormatMode

internal fun LazyListScope.settingsPlaybackSection(
    uiState: SettingsUiState,
    viewModel: SettingsViewModel,
    timeshiftDepthLabel: String,
    timeshiftBackendLabel: String,
    audioDecoderModeLabel: String,
    videoDecoderModeLabel: String,
    playerEnginePreferenceLabel: String,
    playbackBufferModeLabel: String,
    audioOutputPreferenceLabel: String,
    surfaceModeLabel: String,
    vodHttpProtocolLabel: String,
    playbackSpeedLabel: String,
    defaultStopTimerLabel: String,
    defaultIdleTimerLabel: String,
    audioVideoOffsetLabel: String,
    controlsTimeoutLabel: String,
    liveOverlayTimeoutLabel: String,
    noticeTimeoutLabel: String,
    diagnosticsTimeoutLabel: String,
    preferredAudioLanguageLabel: String,
    subtitleSizeLabel: String,
    subtitleTextColorLabel: String,
    subtitleBackgroundLabel: String,
    liveTranslationEndpointLabel: String,
    wifiQualityLabel: String,
    ethernetQualityLabel: String,
    lastSpeedTestLabel: String,
    lastSpeedTestSummary: String,
    speedTestRecommendationLabel: String,
    onShowTimeshiftDepthDialogChange: (Boolean) -> Unit,
    onShowTimeshiftBackendDialogChange: (Boolean) -> Unit,
    onShowAudioDecoderModeDialogChange: (Boolean) -> Unit,
    onShowVideoDecoderModeDialogChange: (Boolean) -> Unit,
    onShowPlaybackBufferModeDialogChange: (Boolean) -> Unit,
    onShowAudioOutputPreferenceDialogChange: (Boolean) -> Unit,
    onShowSurfaceModeDialogChange: (Boolean) -> Unit,
    onShowVodHttpProtocolDialogChange: (Boolean) -> Unit,
    onShowPlaybackSpeedDialogChange: (Boolean) -> Unit,
    onShowDefaultStopTimerDialogChange: (Boolean) -> Unit,
    onShowDefaultIdleTimerDialogChange: (Boolean) -> Unit,
    onShowAudioVideoOffsetDialogChange: (Boolean) -> Unit,
    onShowControlsTimeoutDialogChange: (Boolean) -> Unit,
    onShowLiveOverlayTimeoutDialogChange: (Boolean) -> Unit,
    onShowNoticeTimeoutDialogChange: (Boolean) -> Unit,
    onShowDiagnosticsTimeoutDialogChange: (Boolean) -> Unit,
    onShowAudioLanguageDialogChange: (Boolean) -> Unit,
    onShowSubtitleSizeDialogChange: (Boolean) -> Unit,
    onShowSubtitleTextColorDialogChange: (Boolean) -> Unit,
    onShowSubtitleBackgroundDialogChange: (Boolean) -> Unit,
    onShowLiveTranslationEndpointDialogChange: (Boolean) -> Unit,
    onShowWifiQualityDialogChange: (Boolean) -> Unit,
    onShowEthernetQualityDialogChange: (Boolean) -> Unit
) {
    item(key = "settings_playback_section_content") {
        val liveStreamFormatMode by viewModel.playerLiveStreamFormatMode.collectAsStateWithLifecycle()
        var showLiveStreamFormatDialog by rememberSaveable { mutableStateOf(false) }
        var showPlayerEngineDialog by rememberSaveable { mutableStateOf(false) }
        var showAdvancedSettings by rememberSaveable { mutableStateOf(false) }
        val liveStreamFormatOptions = remember {
            listOf(
                LiveStreamFormatMode.AUTO,
                LiveStreamFormatMode.HLS,
                LiveStreamFormatMode.MPEG_TS
            )
        }
        val playerEngineOptions = remember {
            listOf(
                com.kaynanamtv.domain.model.PlayerEnginePreference.AUTO to "Otomatik",
                com.kaynanamtv.domain.model.PlayerEnginePreference.MEDIA3 to "Media3",
                com.kaynanamtv.domain.model.PlayerEnginePreference.VLC to "Dahili VLC",
                com.kaynanamtv.domain.model.PlayerEnginePreference.EXTERNAL_VLC to "Harici VLC"
            )
        }

        if (showPlayerEngineDialog) {
            PremiumSelectionDialog(
                title = "Oynatıcı",
                onDismiss = { showPlayerEngineDialog = false }
            ) {
                playerEngineOptions.forEachIndexed { index, (pref, label) ->
                    LevelOption(
                        level = index,
                        text = label,
                        currentLevel = if (uiState.playerEnginePreference == pref) index else -1,
                        onSelect = {
                            viewModel.setPlayerEnginePreference(pref)
                            showPlayerEngineDialog = false
                        }
                    )
                }
            }
        }

        if (showLiveStreamFormatDialog) {
            PremiumSelectionDialog(
                title = "Canlı Yayın Formatı",
                onDismiss = { showLiveStreamFormatDialog = false }
            ) {
                liveStreamFormatOptions.forEachIndexed { index, mode ->
                    LevelOption(
                        level = index,
                        text = formatLiveStreamFormatModeLabel(mode),
                        currentLevel = if (liveStreamFormatMode == mode) index else -1,
                        onSelect = {
                            viewModel.setPlayerLiveStreamFormatMode(mode)
                            showLiveStreamFormatDialog = false
                        }
                    )
                }
            }
        }

        // ==========================================
        // 1. TEMEL OYNATMA AYARLARI (ESSENTIAL SETTINGS)
        // ==========================================
        Text(
            text = "Temel Oynatma Ayarları",
            style = MaterialTheme.typography.titleMedium,
            color = Primary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        )

        // Oynatıcı (Otomatik / Media3 / Dahili VLC / Harici VLC)
        ClickableSettingsRow(
            label = "Oynatıcı",
            value = playerEnginePreferenceLabel,
            onClick = { showPlayerEngineDialog = true }
        )

        // Canlı Yayın Formatı (HLS / MPEG-TS)
        ClickableSettingsRow(
            label = "Canlı Yayın Formatı",
            value = formatLiveStreamFormatModeLabel(liveStreamFormatMode),
            onClick = { showLiveStreamFormatDialog = true }
        )

        // Canlı Buffer Boyutu / Modu
        ClickableSettingsRow(
            label = stringResource(R.string.settings_live_buffer_size),
            value = playbackBufferModeLabel,
            onClick = { onShowPlaybackBufferModeDialogChange(true) }
        )

        // Video Decoder (Hardware / Software)
        ClickableSettingsRow(
            label = stringResource(R.string.settings_video_decoder_mode),
            value = videoDecoderModeLabel,
            onClick = { onShowVideoDecoderModeDialogChange(true) }
        )

        // Ses Decoder (Hardware / Software)
        ClickableSettingsRow(
            label = stringResource(R.string.settings_audio_decoder_mode),
            value = audioDecoderModeLabel,
            onClick = { onShowAudioDecoderModeDialogChange(true) }
        )

        // Ses Çıkış Modu (Stereo / Passthrough / Surround)
        ClickableSettingsRow(
            label = stringResource(R.string.settings_audio_output_mode),
            value = audioOutputPreferenceLabel,
            onClick = { onShowAudioOutputPreferenceDialogChange(true) }
        )

        // Tercih Edilen Ses Dili
        ClickableSettingsRow(
            label = stringResource(R.string.settings_preferred_audio_language),
            value = preferredAudioLanguageLabel,
            onClick = { onShowAudioLanguageDialogChange(true) }
        )

        // Altyazı Boyutu
        ClickableSettingsRow(
            label = stringResource(R.string.settings_subtitle_size),
            value = subtitleSizeLabel,
            onClick = { onShowSubtitleSizeDialogChange(true) }
        )

        // Sonraki Bölümü Otomatik Oynat
        HorizontalDivider(color = Color.White.copy(alpha = 0.07f), modifier = Modifier.padding(vertical = 4.dp))
        TvClickableSurface(
            onClick = { viewModel.setAutoPlayNextEpisode(!uiState.autoPlayNextEpisode) },
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Color.Transparent,
                focusedContainerColor = Primary.copy(alpha = 0.15f)
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = stringResource(R.string.settings_auto_play_next_episode), style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                    Text(text = stringResource(R.string.settings_auto_play_next_episode_subtitle), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(alpha = 0.6f))
                }
                Switch(checked = uiState.autoPlayNextEpisode, onCheckedChange = { viewModel.setAutoPlayNextEpisode(it) })
            }
        }

        HorizontalDivider(color = Color.White.copy(alpha = 0.07f), modifier = Modifier.padding(vertical = 8.dp))

        // ==========================================
        // 2. GELİŞMİŞ AYARLAR (ADVANCED SETTINGS TOGGLE)
        // ==========================================
        TvClickableSurface(
            onClick = { showAdvancedSettings = !showAdvancedSettings },
            shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(10.dp)),
            colors = ClickableSurfaceDefaults.colors(
                containerColor = Primary.copy(alpha = 0.10f),
                focusedContainerColor = Primary.copy(alpha = 0.25f)
            ),
            scale = ClickableSurfaceDefaults.scale(focusedScale = 1.01f),
            modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (showAdvancedSettings) "⚙️ Gelişmiş Ayarları Gizle" else "⚙️ Gelişmiş Ayarlar (Surface, Protokol, AV Senkron)",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = OnSurface
                    )
                    Text(
                        text = "SurfaceView, VOD HTTP modu, zap revert, AV offset ve zamanlayıcı ayarları",
                        style = MaterialTheme.typography.bodySmall,
                        color = OnBackground.copy(alpha = 0.6f)
                    )
                }
                Text(
                    text = if (showAdvancedSettings) "▲" else "▼",
                    color = Primary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }

        AnimatedVisibility(visible = showAdvancedSettings) {
            Column(modifier = Modifier.fillMaxWidth().padding(top = 4.dp)) {
                // SurfaceView / TextureView Modu
                ClickableSettingsRow(
                    label = stringResource(R.string.settings_surface_mode),
                    value = surfaceModeLabel,
                    onClick = { onShowSurfaceModeDialogChange(true) }
                )

                // VOD HTTP Protokol Modu
                ClickableSettingsRow(
                    label = stringResource(R.string.settings_vod_http_protocol_mode),
                    value = vodHttpProtocolLabel,
                    onClick = { onShowVodHttpProtocolDialogChange(true) }
                )

                // Zap Auto Revert
                TvClickableSurface(
                    onClick = { viewModel.setZapAutoRevert(!uiState.zapAutoRevert) },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.Transparent,
                        focusedContainerColor = Primary.copy(alpha = 0.15f)
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = stringResource(R.string.settings_zap_auto_revert), style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                            Text(text = stringResource(R.string.settings_zap_auto_revert_subtitle), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(alpha = 0.6f))
                        }
                        Switch(checked = uiState.zapAutoRevert, onCheckedChange = { viewModel.setZapAutoRevert(it) })
                    }
                }

                // AV Senkronizasyonu
                TvClickableSurface(
                    onClick = { viewModel.setPlayerAudioVideoSyncEnabled(!uiState.playerAudioVideoSyncEnabled) },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.Transparent,
                        focusedContainerColor = Primary.copy(alpha = 0.15f)
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = stringResource(R.string.settings_audio_video_sync_enabled), style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                            Text(text = stringResource(R.string.settings_audio_video_sync_enabled_subtitle), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(alpha = 0.6f))
                        }
                        Switch(checked = uiState.playerAudioVideoSyncEnabled, onCheckedChange = { viewModel.setPlayerAudioVideoSyncEnabled(it) })
                    }
                }
                if (uiState.playerAudioVideoSyncEnabled) {
                    ClickableSettingsRow(
                        label = stringResource(R.string.settings_audio_video_sync_default),
                        value = audioVideoOffsetLabel,
                        onClick = { onShowAudioVideoOffsetDialogChange(true) }
                    )
                }

                // Uyumluluk Hafızası
                TvClickableSurface(
                    onClick = {
                        viewModel.setPlayerCompatibilityMemoryEnabled(!uiState.playerCompatibilityMemoryEnabled)
                    },
                    shape = ClickableSurfaceDefaults.shape(RoundedCornerShape(8.dp)),
                    colors = ClickableSurfaceDefaults.colors(
                        containerColor = Color.Transparent,
                        focusedContainerColor = Primary.copy(alpha = 0.15f)
                    ),
                    scale = ClickableSurfaceDefaults.scale(focusedScale = 1f),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(text = stringResource(R.string.settings_ffmpeg_compatibility_memory), style = MaterialTheme.typography.bodyMedium, color = OnSurface)
                            Text(text = stringResource(R.string.settings_ffmpeg_compatibility_memory_subtitle), style = MaterialTheme.typography.bodySmall, color = OnBackground.copy(alpha = 0.6f))
                        }
                        Switch(
                            checked = uiState.playerCompatibilityMemoryEnabled,
                            onCheckedChange = { viewModel.setPlayerCompatibilityMemoryEnabled(it) }
                        )
                    }
                }

                // Varsayılan Oynatma Hızı
                ClickableSettingsRow(
                    label = stringResource(R.string.settings_default_playback_speed),
                    value = playbackSpeedLabel,
                    onClick = { onShowPlaybackSpeedDialogChange(true) }
                )

                // Zamanlayıcılar (Otomatik Kapanma / Boşta Kalma)
                ClickableSettingsRow(
                    label = stringResource(R.string.settings_default_stop_timer),
                    value = defaultStopTimerLabel,
                    onClick = { onShowDefaultStopTimerDialogChange(true) }
                )
                ClickableSettingsRow(
                    label = stringResource(R.string.settings_default_idle_standby_timer),
                    value = defaultIdleTimerLabel,
                    onClick = { onShowDefaultIdleTimerDialogChange(true) }
                )

                // Kontrol ve Bilgi Çubuğu Süreleri
                ClickableSettingsRow(
                    label = stringResource(R.string.settings_player_controls_timeout),
                    value = controlsTimeoutLabel,
                    onClick = { onShowControlsTimeoutDialogChange(true) }
                )
                ClickableSettingsRow(
                    label = stringResource(R.string.settings_live_overlay_timeout),
                    value = liveOverlayTimeoutLabel,
                    onClick = { onShowLiveOverlayTimeoutDialogChange(true) }
                )

                // Altyazı Renk ve Arka Plan
                ClickableSettingsRow(
                    label = stringResource(R.string.settings_subtitle_text_color),
                    value = subtitleTextColorLabel,
                    onClick = { onShowSubtitleTextColorDialogChange(true) }
                )
                ClickableSettingsRow(
                    label = stringResource(R.string.settings_subtitle_background),
                    value = subtitleBackgroundLabel,
                    onClick = { onShowSubtitleBackgroundDialogChange(true) }
                )
            }
        }
    }
}
