package com.kaynanamtv.app.update

import com.kaynanamtv.app.BuildConfig
import com.kaynanamtv.domain.model.Result
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.net.URI
import javax.inject.Inject
import com.kaynanamtv.domain.model.AppUpdateConstants
import javax.inject.Singleton

import com.kaynanamtv.domain.repository.RemoteConfigRepository

private const val GITHUB_RELEASES_LATEST_URL = AppUpdateConstants.GITHUB_RELEASES_LATEST_API
private const val GITHUB_RELEASES_LIST_URL = AppUpdateConstants.GITHUB_RELEASES_LIST_API

data class GitHubReleaseInfo(
    val versionName: String,
    val versionCode: Int?,
    val releaseUrl: String,
    val downloadUrl: String?,
    val downloadSha256: String?,
    val releaseNotes: String,
    val publishedAt: String?
)

@Singleton
class GitHubReleaseChecker @Inject constructor(
    private val okHttpClient: OkHttpClient,
    private val remoteConfigRepository: RemoteConfigRepository
) {
    private companion object {
        private const val MAX_RESPONSE_BYTES = 512 * 1024L
        private val STRUCTURED_TAG_REGEX = Regex("""^v?(.+?)\+(\d+)$""", RegexOption.IGNORE_CASE)
    }

    suspend fun fetchLatestRelease(): Result<GitHubReleaseInfo> = withContext(Dispatchers.IO) {
        try {
            val updateChannel = AppUpdateChannel.fromCurrentBuild()
            val request = Request.Builder()
                .url(updateChannel.releaseApiUrl)
                .header("Accept", "application/vnd.github+json")
                .header("User-Agent", "KaynanamTV-Update-Checker")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val fallback = fetchFromRemoteConfigFallback()
                    if (fallback is Result.Success) return@withContext fallback
                    return@withContext Result.error("Update check failed: HTTP ${response.code}")
                }

                val body = when (val bodyResult = response.body?.let(::readResponseBodyCapped)) {
                    is Result.Success -> bodyResult.data
                    is Result.Error -> return@withContext Result.error(bodyResult.message, bodyResult.exception)
                    null,
                    Result.Loading -> ""
                }
                if (body.isBlank()) {
                    val fallback = fetchFromRemoteConfigFallback()
                    if (fallback is Result.Success) return@withContext fallback
                    return@withContext Result.error("Update check failed: empty GitHub release response")
                }

                val json = selectReleaseJson(body, updateChannel)
                if (json == null) {
                    val fallback = fetchFromRemoteConfigFallback()
                    if (fallback is Result.Success) return@withContext fallback
                    return@withContext Result.error(
                        if (updateChannel == AppUpdateChannel.Beta) {
                            "Update check failed: no beta release found"
                        } else {
                            "Update check failed: latest release response was invalid"
                        }
                    )
                }
                val parsedTag = parseTagVersionInfo(json.optString("tag_name"))
                if (parsedTag.versionName.isBlank()) {
                    val fallback = fetchFromRemoteConfigFallback()
                    if (fallback is Result.Success) return@withContext fallback
                    return@withContext Result.error("Update check failed: latest release tag is missing")
                }

                val notes = json.optString("body").trim()
                val assets = json.optJSONArray("assets")
                val releaseUrl = json.optString("html_url").takeIf(::isHttpsUrl).orEmpty()
                if (releaseUrl.isBlank()) {
                    return@withContext Result.error("Update check failed: latest release URL is not HTTPS")
                }
                val downloadAsset = findApkAsset(assets, updateChannel)

                return@withContext Result.success(
                    GitHubReleaseInfo(
                        versionName = parsedTag.versionName,
                        versionCode = parsedTag.versionCode,
                        releaseUrl = releaseUrl,
                        downloadUrl = downloadAsset?.downloadUrl,
                        downloadSha256 = downloadAsset?.sha256,
                        releaseNotes = notes,
                        publishedAt = json.optString("published_at").takeIf { it.isNotBlank() }
                    )
                )
            }
        } catch (error: IOException) {
            val fallback = fetchFromRemoteConfigFallback()
            if (fallback is Result.Success) return@withContext fallback
            Result.error("Update check failed: network error", error)
        } catch (error: Exception) {
            val fallback = fetchFromRemoteConfigFallback()
            if (fallback is Result.Success) return@withContext fallback
            Result.error("Update check failed: ${error.message}", error)
        }
    }

    private suspend fun fetchFromRemoteConfigFallback(): Result<GitHubReleaseInfo> {
        return try {
            when (val remoteResult = remoteConfigRepository.checkRemoteConfig()) {
                is Result.Success -> {
                    val config = remoteResult.data
                    val releaseUrl = config.apkDownloadUrl.takeIf { it.isNotBlank() }
                        ?: AppUpdateConstants.OFFICIAL_RELEASES_PAGE
                    Result.success(
                        GitHubReleaseInfo(
                            versionName = config.latestVersionName,
                            versionCode = config.latestVersionCode,
                            releaseUrl = releaseUrl,
                            downloadUrl = config.apkDownloadUrl.takeIf { it.isNotBlank() },
                            downloadSha256 = null,
                            releaseNotes = config.releaseNotes,
                            publishedAt = null
                        )
                    )
                }
                is Result.Error -> {
                    val cached = remoteConfigRepository.getCachedRemoteConfig()
                    if (cached != null) {
                        val releaseUrl = cached.apkDownloadUrl.takeIf { it.isNotBlank() }
                            ?: AppUpdateConstants.OFFICIAL_RELEASES_PAGE
                        Result.success(
                            GitHubReleaseInfo(
                                versionName = cached.latestVersionName,
                                versionCode = cached.latestVersionCode,
                                releaseUrl = releaseUrl,
                                downloadUrl = cached.apkDownloadUrl.takeIf { it.isNotBlank() },
                                downloadSha256 = null,
                                releaseNotes = cached.releaseNotes,
                                publishedAt = null
                            )
                        )
                    } else {
                        Result.error("Güncelleme sunucusuna bağlanılamadı")
                    }
                }
                Result.Loading -> Result.error("Güncelleme kontrol ediliyor")
            }
        } catch (e: Exception) {
            Result.error("Güncelleme kontrol hatası: ${e.message}", e)
        }
    }

    private fun selectReleaseJson(body: String, updateChannel: AppUpdateChannel): JSONObject? {
        val releases = runCatching { org.json.JSONArray(body) }.getOrNull() ?: return null
        if (releases.length() == 0) return null
        
        return when (updateChannel) {
            AppUpdateChannel.Stable -> {
                for (index in 0 until releases.length()) {
                    val release = releases.optJSONObject(index) ?: continue
                    if (release.optBoolean("draft")) continue
                    if (release.optBoolean("prerelease")) continue
                    return release
                }
                releases.optJSONObject(0)
            }
            AppUpdateChannel.Beta -> {
                for (index in 0 until releases.length()) {
                    val release = releases.optJSONObject(index) ?: continue
                    if (release.optBoolean("draft")) continue
                    if (!release.optBoolean("prerelease")) continue
                    val tagName = release.optString("tag_name")
                    if (!tagName.contains("-beta", ignoreCase = true)) continue
                    val downloadAsset = findApkAsset(release.optJSONArray("assets"), updateChannel)
                    if (downloadAsset != null) {
                        return release
                    }
                }
                for (index in 0 until releases.length()) {
                    val release = releases.optJSONObject(index) ?: continue
                    if (release.optBoolean("draft")) continue
                    if (release.optBoolean("prerelease")) return release
                }
                null
            }
        }
    }

    private fun readResponseBodyCapped(body: ResponseBody): Result<String> {
        val contentLength = body.contentLength()
        if (contentLength > MAX_RESPONSE_BYTES) {
            return Result.error("Update check failed: GitHub release response exceeded 512 KB")
        }

        val charset = body.contentType()?.charset(Charsets.UTF_8) ?: Charsets.UTF_8
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var totalBytesRead = 0L

        body.byteStream().use { input ->
            while (true) {
                val bytesRead = input.read(buffer)
                if (bytesRead == -1) break

                totalBytesRead += bytesRead
                if (totalBytesRead > MAX_RESPONSE_BYTES) {
                    return Result.error("Update check failed: GitHub release response exceeded 512 KB")
                }

                output.write(buffer, 0, bytesRead)
            }
        }

        return Result.success(output.toString(charset.name()))
    }

    private fun findApkAsset(assets: org.json.JSONArray?, updateChannel: AppUpdateChannel): ReleaseApkAsset? {
        if (assets == null) return null
        var fallback: ReleaseApkAsset? = null
        for (index in 0 until assets.length()) {
            val asset = assets.optJSONObject(index) ?: continue
            val name = asset.optString("name")
            val url = asset.optString("browser_download_url").takeIf { it.isNotBlank() } ?: continue
            if (!isHttpsUrl(url)) continue
            val releaseAsset = ReleaseApkAsset(
                downloadUrl = url,
                sha256 = parseReleaseAssetSha256Digest(asset.optString("digest"))
            )
            when (updateChannel) {
                AppUpdateChannel.Stable -> {
                    if (name.equals("KaynanamTV.apk", ignoreCase = true) ||
                        name.equals("GentlemanTV.apk", ignoreCase = true) ||
                        name.equals("Gentleman-IPTV.apk", ignoreCase = true) ||
                        name.startsWith("KaynanamTV", ignoreCase = true) && name.endsWith(".apk", ignoreCase = true) ||
                        name.startsWith("Gentleman", ignoreCase = true) && name.endsWith(".apk", ignoreCase = true) ||
                        name.equals("app-release.apk", ignoreCase = true)) {
                        return releaseAsset
                    }
                    if (fallback == null &&
                        name.endsWith(".apk", ignoreCase = true) &&
                        !name.contains("beta", ignoreCase = true)
                    ) {
                        fallback = releaseAsset
                    }
                }
                AppUpdateChannel.Beta -> {
                    if (name.equals("KaynanamTV-beta.apk", ignoreCase = true) ||
                        name.equals("GentlemanTV-beta.apk", ignoreCase = true) ||
                        name.equals("Gentleman-IPTV-beta.apk", ignoreCase = true) ||
                        name.startsWith("KaynanamTV-beta", ignoreCase = true) && name.endsWith(".apk", ignoreCase = true) ||
                        name.equals("KaynanamTV-beta.apk", ignoreCase = true)) {
                        return releaseAsset
                    }
                    if (fallback == null &&
                        name.endsWith(".apk", ignoreCase = true) &&
                        name.contains("beta", ignoreCase = true)
                    ) {
                        fallback = releaseAsset
                    }
                }
            }
        }
        return fallback
    }


    private fun parseTagVersionInfo(rawTagName: String): ParsedTagVersion {
        val normalizedTag = rawTagName.trim()
        val structuredMatch = STRUCTURED_TAG_REGEX.matchEntire(normalizedTag)
        if (structuredMatch != null) {
            return ParsedTagVersion(
                versionName = structuredMatch.groupValues[1].trim(),
                versionCode = structuredMatch.groupValues[2].toIntOrNull()
            )
        }

        val name = normalizedTag.removePrefix("v").removePrefix("V").trim()
        val codeFromTag = name.substringAfterLast('.').takeWhile { it.isDigit() }.toIntOrNull()
        return ParsedTagVersion(
            versionName = name,
            versionCode = codeFromTag
        )
    }

    private fun isHttpsUrl(url: String): Boolean {
        val normalized = url.trim()
        if (normalized.isBlank()) return false
        return runCatching {
            val parsed = URI(normalized)
            parsed.scheme.equals("https", ignoreCase = true) && !parsed.host.isNullOrBlank()
        }.getOrDefault(false)
    }
}

internal fun parseReleaseAssetSha256Digest(rawDigest: String): String? {
    val value = rawDigest.trim()
    if (!value.startsWith("sha256:", ignoreCase = true)) return null
    val hex = value.substringAfter(':')
    return hex.takeIf { SHA256_HEX_REGEX.matches(it) }?.lowercase()
}

private val SHA256_HEX_REGEX = Regex("""^[a-fA-F0-9]{64}$""")

private data class ReleaseApkAsset(
    val downloadUrl: String,
    val sha256: String?
)

enum class AppUpdateChannel(val id: String, val releaseApiUrl: String) {
    Stable(id = "stable", releaseApiUrl = GITHUB_RELEASES_LIST_URL),
    Beta(id = "beta", releaseApiUrl = GITHUB_RELEASES_LIST_URL);

    companion object {
        fun fromCurrentBuild(): AppUpdateChannel {
            return fromBuildConfig(BuildConfig.APP_UPDATE_CHANNEL, BuildConfig.VERSION_NAME)
        }

        fun fromBuildConfig(channelId: String?, versionName: String): AppUpdateChannel {
            return when {
                channelId.equals(Beta.id, ignoreCase = true) -> Beta
                versionName.contains("-beta", ignoreCase = true) -> Beta
                else -> Stable
            }
        }
    }
}

private data class ParsedTagVersion(
    val versionName: String,
    val versionCode: Int?
)
