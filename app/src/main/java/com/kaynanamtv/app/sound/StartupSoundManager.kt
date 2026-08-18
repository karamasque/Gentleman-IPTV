package com.kaynanamtv.app.sound

import android.content.Context
import android.media.MediaPlayer
import com.kaynanamtv.app.R

object StartupSoundManager {

    fun playStartupSound(context: Context) {
        runCatching {
            val mediaPlayer = MediaPlayer.create(context, R.raw.startup_sound_1)
            mediaPlayer?.setOnCompletionListener { mp ->
                mp.release()
            }
            mediaPlayer?.start()
        }
    }
}
