package com.kaynanamtv.app.manager

import android.util.Log
import com.kaynanamtv.domain.manager.MediaPrefetcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import javax.inject.Inject
import javax.inject.Singleton
import com.kaynanamtv.app.KaynanamTVApp
import com.kaynanamtv.data.sync.PermanentImageCache
import okhttp3.OkHttpClient

/**
 * Coil 3 tabanlı medya görsel önbellekleyici.
 *
 * Görselleri kalıcı disk klasöründe depolar. Playlist silindiğinde bu klasörden de silinir.
 * Büyük kataloglarda sistem yükünü dengelemek için:
 * - En fazla [MAX_CONCURRENT] eşzamanlı indirme
 * - [BATCH_SIZE] URL'lik parçalar halinde işlem
 * - Parçalar arasında [BATCH_DELAY_MS] ms bekleme
 *
 * Bu sayede arka plan indirme TV izlemeyi kesmez.
 */
@Singleton
class CoilMediaPrefetcher @Inject constructor(
    private val okHttpClient: OkHttpClient
) : MediaPrefetcher {

    companion object {
        private const val TAG = "CoilMediaPrefetcher"
        /** Aynı anda en fazla bu kadar görsel indirilir. */
        private const val MAX_CONCURRENT = 3
        /** Her batch için URL sayısı. */
        private const val BATCH_SIZE = 20
        /** Batch'ler arasında bekleme (ms). */
        private const val BATCH_DELAY_MS = 250L
    }

    override suspend fun prefetchMediaImages(urls: List<String>) = coroutineScope {
        val appContext = try { KaynanamTVApp.instance } catch (e: Exception) { null }
            ?: return@coroutineScope
        if (urls.isEmpty()) return@coroutineScope

        try {
            // Kalıcı önbellekte olmayan URL'leri filtrele.
            val notInDisk = urls.filter { url ->
                val file = PermanentImageCache.getCacheFile(appContext, url)
                !file.exists() || file.length() <= 0
            }

            if (notInDisk.isEmpty()) {
                Log.d(TAG, "Tüm ${urls.size} görsel kalıcı önbellekte zaten var. Atlanıyor.")
                return@coroutineScope
            }

            Log.d(TAG, "${urls.size} URL'den ${notInDisk.size} tanesi kalıcı diskte yok, indiriliyor.")

            val semaphore = Semaphore(MAX_CONCURRENT)

            // Büyük listeleri batch'ler halinde işle
            notInDisk.chunked(BATCH_SIZE).forEach { batch ->
                val deferreds = batch.map { url ->
                    async(Dispatchers.IO) {
                        semaphore.withPermit {
                            PermanentImageCache.downloadAndCache(appContext, okHttpClient, url)
                        }
                    }
                }
                deferreds.awaitAll()
                // Bir sonraki batch'e geçmeden önce kısa bekle
                delay(BATCH_DELAY_MS)
            }
        } catch (e: Throwable) {
            Log.w(TAG, "Görsel önbellekleme genel hata", e)
        }
    }
}
