package com.kaynanamtv.player

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

import java.util.concurrent.ConcurrentHashMap

data class LearnedAudioCompatibility(
    val mediaId: String,
    val streamType: String,
    val audioMimeTypes: List<String>,
    val decision: String,
    val detail: String?,
    val updatedAtMs: Long
)

@Singleton
class AudioCompatibilityMemoryStore @Inject constructor(
    @ApplicationContext context: Context
) {
    // Session-only in-memory storage: every app restart and fresh session starts hardware-first
    private val sessionCache = ConcurrentHashMap<String, LearnedAudioCompatibility>()

    fun lookup(mediaId: String, streamType: String): LearnedAudioCompatibility? {
        return sessionCache[key(mediaId, streamType)]
    }

    fun rememberSoftwareAudioFallback(
        mediaId: String,
        streamType: String,
        audioMimeTypes: List<String>,
        detail: String?
    ) {
        val normalizedMimeTypes = audioMimeTypes
            .map(String::trim)
            .filter(String::isNotEmpty)
            .distinct()
        sessionCache[key(mediaId, streamType)] = LearnedAudioCompatibility(
            mediaId = mediaId,
            streamType = streamType,
            audioMimeTypes = normalizedMimeTypes,
            decision = DECISION_SOFTWARE_FFMPEG,
            detail = detail,
            updatedAtMs = System.currentTimeMillis()
        )
    }

    fun clear() {
        sessionCache.clear()
    }

    private fun key(mediaId: String, streamType: String): String {
        return "$streamType|$mediaId"
    }

    companion object {
        const val DECISION_SOFTWARE_FFMPEG = "software-ffmpeg"
    }
}
