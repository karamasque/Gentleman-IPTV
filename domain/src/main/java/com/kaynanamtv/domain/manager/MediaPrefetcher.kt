package com.kaynanamtv.domain.manager

interface MediaPrefetcher {
    suspend fun prefetchMediaImages(urls: List<String>)
}
