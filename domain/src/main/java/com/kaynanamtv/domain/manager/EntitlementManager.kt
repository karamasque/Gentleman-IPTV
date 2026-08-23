package com.kaynanamtv.domain.manager

import com.kaynanamtv.domain.model.EntitlementStatus
import com.kaynanamtv.domain.model.Feature
import com.kaynanamtv.domain.model.UserSession
import kotlinx.coroutines.flow.Flow

interface EntitlementManager {
    val sessionFlow: Flow<UserSession?>
    val entitlementStatusFlow: Flow<EntitlementStatus>
    val isPremiumFlow: Flow<Boolean>

    suspend fun getCurrentStatus(): EntitlementStatus
    suspend fun isPremium(): Boolean
    suspend fun canUse(feature: Feature): Boolean
    fun observeFeature(feature: Feature): Flow<Boolean>

    companion object {
        fun canUseFeature(feature: Feature, isPremium: Boolean): Boolean {
            return when (feature) {
                Feature.AUTO_IPTV -> isPremium
                Feature.BACKGROUND_PLAYLIST_UPDATE -> isPremium
                Feature.CUSTOM_GROUPS -> isPremium
                Feature.PIN_GROUPS -> isPremium
                Feature.CUSTOM_EPG -> isPremium
                Feature.ADVANCED_PLAYBACK -> isPremium
                Feature.AUDIO_PASSTHROUGH -> isPremium
                Feature.TIMESHIFT -> isPremium
                Feature.PVR -> isPremium
                Feature.MULTIVIEW_FULL -> isPremium
                Feature.CLOUD_SYNC -> isPremium
                Feature.TRAKT -> isPremium
            }
        }
    }
}
