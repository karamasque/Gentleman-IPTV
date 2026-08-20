package com.kaynanamtv.domain.model

object AppUpdateConstants {
    const val GITHUB_OWNER = "karamasque"
    const val GITHUB_REPO = "Gentleman-IPTV"
    const val RELEASE_ASSET_NAME = "KaynanamTV.apk"
    const val DEFAULT_DOWNLOAD_URL = "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases/latest/download/$RELEASE_ASSET_NAME"
    const val OFFICIAL_RELEASES_PAGE = "https://github.com/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
    const val GITHUB_RELEASES_LATEST_API = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases/latest"
    const val GITHUB_RELEASES_LIST_API = "https://api.github.com/repos/$GITHUB_OWNER/$GITHUB_REPO/releases?per_page=20"
}
