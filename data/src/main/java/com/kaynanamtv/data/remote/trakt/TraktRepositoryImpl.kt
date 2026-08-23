package com.kaynanamtv.data.remote.trakt

import android.content.Context
import android.util.Log
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.kaynanamtv.data.preferences.PreferencesRepository
import com.kaynanamtv.domain.manager.EntitlementManager
import com.kaynanamtv.domain.model.ContentType
import com.kaynanamtv.domain.model.Feature
import com.kaynanamtv.domain.model.Result
import com.kaynanamtv.domain.repository.TraktAuthState
import com.kaynanamtv.domain.repository.TraktDeviceCodeResponse
import com.kaynanamtv.domain.repository.TraktRepository
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

@Singleton
class TraktRepositoryImpl @Inject constructor(
    @ApplicationContext private val context: Context,
    private val entitlementManager: EntitlementManager,
    private val preferencesRepository: PreferencesRepository,
    private val gson: Gson
) : TraktRepository {

    private val TAG = "TraktRepository"
    private val CLIENT_ID = "c50d4212ba9dc297fb26105f9cbdf268800163351d38865668db753905ca89e0"
    private val CLIENT_SECRET = "db95c8c6d48a044d03ce08cebeafca7bc28185c7bb5e56e4c7608ea77ad8ec9b"
    private val BASE_URL = "https://api.trakt.tv"
    private val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()

    private val httpClient = OkHttpClient.Builder()
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .build()

    private val _authState = MutableStateFlow(TraktAuthState(isAuthenticated = false))
    override val authState: StateFlow<TraktAuthState> = _authState.asStateFlow()

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var accessToken: String? = null

    init {
        scope.launch {
            val token = preferencesRepository.traktAccessToken.first()
            if (!token.isNullOrBlank()) {
                accessToken = token
                _authState.value = TraktAuthState(isAuthenticated = true)
            }
        }
    }

    override suspend fun getDeviceCode(): Result<TraktDeviceCodeResponse> = withContext(Dispatchers.IO) {
        if (!entitlementManager.canUse(Feature.TRAKT)) {
            return@withContext Result.error("Trakt entegrasyonu Premium bir özelliktir.")
        }

        try {
            val json = JsonObject().apply {
                addProperty("client_id", CLIENT_ID)
            }
            val request = Request.Builder()
                .url("$BASE_URL/oauth/device/code")
                .header("Content-Type", "application/json")
                .header("trakt-api-version", "2")
                .header("trakt-api-key", CLIENT_ID)
                .post(json.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return@withContext Result.error("Device code alınamadı: HTTP ${response.code}")
                }
                val obj = gson.fromJson(body, JsonObject::class.java)
                val deviceCode = obj.get("device_code").asString
                val userCode = obj.get("user_code").asString
                val verificationUrl = obj.get("verification_url").asString
                val expiresIn = obj.get("expires_in").asInt
                val interval = obj.get("interval").asInt

                Result.success(
                    TraktDeviceCodeResponse(
                        deviceCode = deviceCode,
                        userCode = userCode,
                        verificationUrl = verificationUrl,
                        expiresInSeconds = expiresIn,
                        intervalSeconds = interval
                    )
                )
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to get Trakt device code", e)
            Result.error("Trakt bağlantısı kurulamadı: ${e.message}", e)
        }
    }

    override suspend fun pollAccessToken(deviceCode: String): Result<Boolean> = withContext(Dispatchers.IO) {
        try {
            val json = JsonObject().apply {
                addProperty("code", deviceCode)
                addProperty("client_id", CLIENT_ID)
                addProperty("client_secret", CLIENT_SECRET)
            }
            val request = Request.Builder()
                .url("$BASE_URL/oauth/device/token")
                .header("Content-Type", "application/json")
                .header("trakt-api-version", "2")
                .header("trakt-api-key", CLIENT_ID)
                .post(json.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            httpClient.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                when (response.code) {
                    200 -> {
                        val obj = gson.fromJson(body, JsonObject::class.java)
                        val token = obj.get("access_token").asString
                        accessToken = token
                        preferencesRepository.setTraktAccessToken(token)
                        _authState.value = TraktAuthState(isAuthenticated = true)
                        Result.success(true)
                    }
                    400 -> Result.success(false) // Pending authorization
                    404 -> Result.error("Geçersiz veya süresi dolmuş kod.")
                    409 -> Result.error("Kullanıcı isteği onayladı veya reddetti.")
                    410 -> Result.error("Kod süresi doldu.")
                    418 -> Result.error("Kullanıcı isteği reddetti.")
                    429 -> Result.success(false) // Polling rate limit
                    else -> Result.error("Bilinmeyen yanıt: HTTP ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to poll Trakt token", e)
            Result.error(e.message ?: "Token sorgulanamadı", e)
        }
    }

    override suspend fun disconnect(): Result<Unit> = withContext(Dispatchers.IO) {
        accessToken = null
        preferencesRepository.setTraktAccessToken(null)
        _authState.value = TraktAuthState(isAuthenticated = false)
        Result.success(Unit)
    }

    override suspend fun scrobbleStart(
        title: String,
        year: Int?,
        tmdbId: Long?,
        contentType: ContentType,
        progressPercent: Float,
        seasonNumber: Int?,
        episodeNumber: Int?
    ): Result<Unit> = scrobbleInternal("start", title, year, tmdbId, contentType, progressPercent, seasonNumber, episodeNumber)

    override suspend fun scrobblePause(
        title: String,
        year: Int?,
        tmdbId: Long?,
        contentType: ContentType,
        progressPercent: Float,
        seasonNumber: Int?,
        episodeNumber: Int?
    ): Result<Unit> = scrobbleInternal("pause", title, year, tmdbId, contentType, progressPercent, seasonNumber, episodeNumber)

    override suspend fun scrobbleStop(
        title: String,
        year: Int?,
        tmdbId: Long?,
        contentType: ContentType,
        progressPercent: Float,
        seasonNumber: Int?,
        episodeNumber: Int?
    ): Result<Unit> = scrobbleInternal("stop", title, year, tmdbId, contentType, progressPercent, seasonNumber, episodeNumber)

    private suspend fun scrobbleInternal(
        action: String,
        title: String,
        year: Int?,
        tmdbId: Long?,
        contentType: ContentType,
        progressPercent: Float,
        seasonNumber: Int?,
        episodeNumber: Int?
    ): Result<Unit> = withContext(Dispatchers.IO) {
        val token = accessToken
        if (token.isNullOrBlank() || !entitlementManager.canUse(Feature.TRAKT)) {
            return@withContext Result.success(Unit) // Zero network traffic when not authenticated or not Premium
        }

        try {
            val payload = buildScrobblePayload(title, year, tmdbId, contentType, progressPercent, seasonNumber, episodeNumber)
            val request = Request.Builder()
                .url("$BASE_URL/scrobble/$action")
                .header("Content-Type", "application/json")
                .header("Authorization", "Bearer $token")
                .header("trakt-api-version", "2")
                .header("trakt-api-key", CLIENT_ID)
                .post(payload.toString().toRequestBody(JSON_MEDIA_TYPE))
                .build()

            httpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    Result.success(Unit)
                } else {
                    Result.error("Scrobble failed: HTTP ${response.code}")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Trakt scrobble $action error", e)
            Result.error(e.message ?: "Scrobble error", e)
        }
    }

    private fun buildScrobblePayload(
        title: String,
        year: Int?,
        tmdbId: Long?,
        contentType: ContentType,
        progressPercent: Float,
        seasonNumber: Int?,
        episodeNumber: Int?
    ): JsonObject {
        val root = JsonObject().apply {
            addProperty("progress", (progressPercent.coerceIn(0f, 100f)))
        }

        if (contentType == ContentType.MOVIE) {
            val movie = JsonObject().apply {
                addProperty("title", title)
                year?.let { addProperty("year", it) }
                if (tmdbId != null && tmdbId > 0L) {
                    val ids = JsonObject().apply { addProperty("tmdb", tmdbId) }
                    add("ids", ids)
                }
            }
            root.add("movie", movie)
        } else {
            val show = JsonObject().apply {
                addProperty("title", title)
                year?.let { addProperty("year", it) }
            }
            val episode = JsonObject().apply {
                addProperty("season", seasonNumber ?: 1)
                addProperty("number", episodeNumber ?: 1)
                if (tmdbId != null && tmdbId > 0L) {
                    val ids = JsonObject().apply { addProperty("tmdb", tmdbId) }
                    add("ids", ids)
                }
            }
            root.add("show", show)
            root.add("episode", episode)
        }
        return root
    }
}
