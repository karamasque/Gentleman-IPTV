package com.kaynanamtv.data.sync

import android.content.Context
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.FileOutputStream
import java.security.MessageDigest

object PermanentImageCache {
    private const val TAG = "PermanentImageCache"
    private const val MAX_CACHE_SIZE_BYTES = 100L * 1024L * 1024L // 100 MB limit

    private fun getCacheDir(context: Context): File {
        val dir = File(context.filesDir, "permanent_image_cache")
        if (!dir.exists()) {
            dir.mkdirs()
        }
        return dir
    }

    fun getCacheFile(context: Context, url: String): File {
        val dir = getCacheDir(context)
        val hash = md5(url)
        return File(dir, "$hash.bin")
    }

    fun getCacheSizeBytes(context: Context): Long {
        val dir = getCacheDir(context)
        return dir.listFiles()?.sumOf { if (it.isFile) it.length() else 0L } ?: 0L
    }

    suspend fun downloadAndCache(context: Context, okHttpClient: OkHttpClient, url: String) {
        if (url.isBlank()) return
        val file = getCacheFile(context, url)
        if (file.exists() && file.length() > 0) {
            file.setLastModified(System.currentTimeMillis())
            return
        }

        trimCacheIfNeeded(context)

        val request = Request.Builder()
            .url(url)
            .build()

        try {
            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body
                    if (body != null) {
                        val tempFile = File(file.parentFile, "${file.name}.tmp")
                        FileOutputStream(tempFile).use { output ->
                            body.byteStream().copyTo(output)
                        }
                        if (tempFile.renameTo(file)) {
                            Log.d(TAG, "Cached permanently: $url -> ${file.absolutePath}")
                        } else {
                            tempFile.delete()
                        }
                    }
                } else {
                    Log.w(TAG, "Failed to download image (HTTP ${response.code}): $url")
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to download image: $url", e)
        }
    }

    fun trimCacheIfNeeded(context: Context) {
        try {
            val dir = getCacheDir(context)
            val files = dir.listFiles()?.filter { it.isFile } ?: return
            var currentSize = files.sumOf { it.length() }
            if (currentSize <= MAX_CACHE_SIZE_BYTES) return

            Log.i(TAG, "Trimming permanent image cache: currentSize=${currentSize / 1024 / 1024}MB > max=100MB")
            // Sort by last modified time (oldest first)
            val sortedFiles = files.sortedBy { it.lastModified() }
            for (file in sortedFiles) {
                val size = file.length()
                if (file.delete()) {
                    currentSize -= size
                    if (currentSize <= MAX_CACHE_SIZE_BYTES * 0.8) { // Trim to 80MB
                        break
                    }
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error trimming permanent image cache", e)
        }
    }

    fun clearAllCache(context: Context): Long {
        var bytesFreed = 0L
        try {
            val dir = getCacheDir(context)
            dir.listFiles()?.forEach { file ->
                if (file.isFile) {
                    val length = file.length()
                    if (file.delete()) {
                        bytesFreed += length
                    }
                }
            }
            Log.i(TAG, "Cleared permanent image cache: freed ${bytesFreed / 1024 / 1024}MB")
        } catch (e: Exception) {
            Log.w(TAG, "Error clearing permanent image cache", e)
        }
        return bytesFreed
    }

    fun deleteCachedFiles(context: Context, urls: List<String>) {
        urls.forEach { url ->
            if (url.isNotBlank()) {
                val file = getCacheFile(context, url)
                if (file.exists()) {
                    file.delete()
                    Log.d(TAG, "Deleted permanent cache file for URL: $url")
                }
            }
        }
    }

    private fun md5(input: String): String {
        val md = MessageDigest.getInstance("MD5")
        return md.digest(input.toByteArray()).joinToString("") { "%02x".format(it) }
    }
}
