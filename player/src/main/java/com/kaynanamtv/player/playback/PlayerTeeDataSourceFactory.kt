@file:androidx.media3.common.util.UnstableApi

package com.kaynanamtv.player.playback

import android.net.Uri
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.TransferListener
import java.io.IOException
import java.io.OutputStream
import java.util.concurrent.atomic.AtomicReference

private const val TAG = "PlayerTeeRecording"

/**
 * ExoPlayer'in veri kaynagini saran bir DataSource.Factory.
 *
 * ExoPlayer stream verisi okudukca ayni byte'lari [sinkRef]'e de yazar.
 * Bu sayede sunucuya tek baglanti uzerinden hem oynatma hem kayit yapilir --
 * sunucuya ayri bir HTTP istegi atilmadigindan 403 hatasi olusmuyor.
 *
 * [sinkRef] null iken tee devre disidir; normal DataSource gibi calisir.
 */
@UnstableApi
internal class PlayerTeeDataSourceFactory(
    private val upstream: DataSource.Factory,
    internal val sinkRef: AtomicReference<OutputStream?>
) : DataSource.Factory {

    override fun createDataSource(): DataSource =
        PlayerTeeDataSource(upstream.createDataSource(), sinkRef)
}

@UnstableApi
private class PlayerTeeDataSource(
    private val upstream: DataSource,
    private val sinkRef: AtomicReference<OutputStream?>
) : DataSource {

    private var shouldWrite = false

    override fun addTransferListener(transferListener: TransferListener) {
        upstream.addTransferListener(transferListener)
    }

    @Throws(IOException::class)
    override fun open(dataSpec: DataSpec): Long {
        val uriString = dataSpec.uri.toString().lowercase()
        // Manifest (m3u8, mpd, json, xml) ve Key/DRM (key, license, drm, auth) isteklerini filtreliyoruz.
        val isManifest = uriString.contains(".m3u8") || uriString.contains(".mpd") || uriString.contains(".json") || uriString.contains(".xml")
        val isKeyOrDrm = uriString.contains(".key") || uriString.contains("license") || uriString.contains("drm") || uriString.contains("auth")
        
        shouldWrite = !isManifest && !isKeyOrDrm
        Log.d(TAG, "Tee open: shouldWrite=$shouldWrite, uri=$uriString")
        
        return upstream.open(dataSpec)
    }

    @Throws(IOException::class)
    override fun read(buffer: ByteArray, offset: Int, length: Int): Int {
        val bytesRead = upstream.read(buffer, offset, length)
        if (bytesRead > 0 && shouldWrite) {
            try {
                sinkRef.get()?.write(buffer, offset, bytesRead)
            } catch (e: IOException) {
                Log.w(TAG, "Tee write failed, recording may be incomplete: ${e.message}")
                runCatching { sinkRef.getAndSet(null)?.close() }
            }
        }
        return bytesRead
    }

    override fun getUri(): Uri? = upstream.uri

    override fun getResponseHeaders(): Map<String, List<String>> = upstream.responseHeaders

    @Throws(IOException::class)
    override fun close() = upstream.close()
}
