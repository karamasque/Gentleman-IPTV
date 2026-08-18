package com.kaynanamtv.domain.repository

import com.kaynanamtv.domain.model.AppRemoteConfig
import com.kaynanamtv.domain.model.ForceUpdateDecision
import com.kaynanamtv.domain.model.Result
import kotlinx.coroutines.flow.Flow

interface RemoteConfigRepository {
    val remoteConfigFlow: Flow<AppRemoteConfig?>
    val forceUpdateDecisionFlow: Flow<ForceUpdateDecision>
    suspend fun checkRemoteConfig(force: Boolean = false): Result<AppRemoteConfig>
    suspend fun getCachedRemoteConfig(): AppRemoteConfig?
}
