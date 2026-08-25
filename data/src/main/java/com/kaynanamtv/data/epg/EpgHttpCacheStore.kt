package com.kaynanamtv.data.epg

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Thread-safe persistent cache for EPG HTTP conditional request validators (ETag and Last-Modified).
 * Ensures bandwidth and CPU are saved by enabling 304 Not Modified responses on repeated EPG refreshes.
 */
@Singleton
class EpgHttpCacheStore @Inject constructor(
    @param:ApplicationContext private val context: Context
) {
    private val prefs: SharedPreferences by lazy {
        context.getSharedPreferences("epg_http_conditional_cache", Context.MODE_PRIVATE)
    }

    data class CacheEntry(
        val url: String,
        val etag: String?,
        val lastModified: String?
    )

    /**
     * Retrieves cached validator headers for the given provider and current URL.
     * Returns null if the URL has changed, preventing stale validators from being sent to a new source.
     */
    fun getCache(providerId: Long, currentUrl: String): CacheEntry? {
        val storedUrl = prefs.getString("epg_${providerId}_url", null) ?: return null
        if (storedUrl != currentUrl) return null
        val etag = prefs.getString("epg_${providerId}_etag", null)
        val lastModified = prefs.getString("epg_${providerId}_last_modified", null)
        if (etag == null && lastModified == null) return null
        return CacheEntry(storedUrl, etag, lastModified)
    }

    /**
     * Stores or updates the ETag and Last-Modified headers for a provider's EPG feed URL.
     */
    fun putCache(providerId: Long, url: String, etag: String?, lastModified: String?) {
        val trimmedEtag = etag?.trim()?.takeIf { it.isNotEmpty() }
        val trimmedLastModified = lastModified?.trim()?.takeIf { it.isNotEmpty() }

        prefs.edit().apply {
            putString("epg_${providerId}_url", url)
            if (trimmedEtag != null) {
                putString("epg_${providerId}_etag", trimmedEtag)
            } else {
                remove("epg_${providerId}_etag")
            }
            if (trimmedLastModified != null) {
                putString("epg_${providerId}_last_modified", trimmedLastModified)
            } else {
                remove("epg_${providerId}_last_modified")
            }
            apply()
        }
    }

    /**
     * Clears cached validator headers when a provider is deleted or reset.
     */
    fun clearCache(providerId: Long) {
        prefs.edit().apply {
            remove("epg_${providerId}_url")
            remove("epg_${providerId}_etag")
            remove("epg_${providerId}_last_modified")
            apply()
        }
    }
}
