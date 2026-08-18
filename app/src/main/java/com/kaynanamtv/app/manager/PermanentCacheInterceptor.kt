package com.kaynanamtv.app.manager

import android.content.Context
import coil3.intercept.Interceptor
import coil3.request.ImageResult
import com.kaynanamtv.data.sync.PermanentImageCache

class PermanentCacheInterceptor(private val context: Context) : Interceptor {
    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        val data = request.data
        if (data is String && (data.startsWith("http://") || data.startsWith("https://"))) {
            val file = PermanentImageCache.getCacheFile(context, data)
            if (file.exists() && file.length() > 0) {
                val newRequest = request.newBuilder()
                    .data(file)
                    .build()
                return chain.withRequest(newRequest).proceed()
            }
        }
        return chain.proceed()
    }
}
