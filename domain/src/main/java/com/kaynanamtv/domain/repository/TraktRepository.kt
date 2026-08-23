package com.kaynanamtv.domain.repository

import com.kaynanamtv.domain.model.ContentType
import com.kaynanamtv.domain.model.Result
import kotlinx.coroutines.flow.StateFlow

data class TraktDeviceCodeResponse(
    val deviceCode: String,
    val userCode: String,
    val verificationUrl: String,
    val expiresInSeconds: Int,
    val intervalSeconds: Int
)

data class TraktAuthState(
    val isAuthenticated: Boolean,
    val username: String? = null,
    val lastSyncedAt: Long = 0L
)

interface TraktRepository {
    val authState: StateFlow<TraktAuthState>

    suspend fun getDeviceCode(): Result<TraktDeviceCodeResponse>
    suspend fun pollAccessToken(deviceCode: String): Result<Boolean>
    suspend fun disconnect(): Result<Unit>

    suspend fun scrobbleStart(
        title: String,
        year: Int? = null,
        tmdbId: Long? = null,
        contentType: ContentType,
        progressPercent: Float,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null
    ): Result<Unit>

    suspend fun scrobblePause(
        title: String,
        year: Int? = null,
        tmdbId: Long? = null,
        contentType: ContentType,
        progressPercent: Float,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null
    ): Result<Unit>

    suspend fun scrobbleStop(
        title: String,
        year: Int? = null,
        tmdbId: Long? = null,
        contentType: ContentType,
        progressPercent: Float,
        seasonNumber: Int? = null,
        episodeNumber: Int? = null
    ): Result<Unit>
}
