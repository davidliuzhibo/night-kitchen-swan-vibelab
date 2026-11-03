package com.david.nightkitchen.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.SoundPool
import android.os.Handler
import android.os.Looper
import android.util.Log

/** Application-scoped game audio. The 20-second mono loop is 882000 bytes decoded. */
internal class AndroidStallAudio(private val context: Context) : StallAudio {
    private val manager = context.getSystemService(AudioManager::class.java)
    private val main = Handler(Looper.getMainLooper())
    private val attributes = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME)
        .setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build()
    private val samples = mutableMapOf<StallSound, Int>()
    private var pool: SoundPool? = null
    private var focus: AudioFocusRequest? = null
    private var epoch = 0L
    private var wanted = false
    private var granted = false
    private var focusTaken = false
    private var retries = 0
    private val mix = StallMix(::startStream) { stream -> pool?.stop(stream) }

    override fun setAudible(audible: Boolean) {
        wanted = audible
        if (!audible) {
            mix.setAudible(false)
            abandonFocus()
            focusTaken = false
            retries = 0
            return
        }
        if (focus == null && !focusTaken) requestFocus()
        if (granted) {
            preparePool()
            mix.setAudible(true)
        }
    }

    private fun requestFocus() {
        val token = ++epoch
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
            .setAudioAttributes(attributes).setAcceptsDelayedFocusGain(true).setWillPauseWhenDucked(true)
            .setOnAudioFocusChangeListener({ change ->
                if (token == epoch && wanted) {
                    granted = change == AudioManager.AUDIOFOCUS_GAIN
                    if (granted) preparePool()
                    mix.setAudible(granted)
                    if (change == AudioManager.AUDIOFOCUS_LOSS) {
                        focusTaken = true
                        abandonFocus()
                    }
                }
            }, main).build()
        focus = request
        val result = try { manager?.requestAudioFocus(request) } catch (e: RuntimeException) {
            Log.w(TAG, "Could not request foreground game audio", e)
            AudioManager.AUDIOFOCUS_REQUEST_FAILED
        }
        granted = result == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
        if (!granted && result != AudioManager.AUDIOFOCUS_REQUEST_DELAYED) {
            val retryEpoch = ++epoch
            focus = null
            if (retries < 2) {
                retries++
                main.postDelayed({
                    if (wanted && epoch == retryEpoch && focus == null) setAudible(true)
                }, 350L * retries)
            } else Log.w(TAG, "Could not obtain foreground audio focus")
        }
    }

    private fun preparePool() {
        if (pool != null) return
        val created = try {
            SoundPool.Builder().setAudioAttributes(attributes).setMaxStreams(3).build()
        } catch (e: RuntimeException) {
            Log.w(TAG, "Game mixer unavailable", e)
            return
        }
        pool = created
        created.setOnLoadCompleteListener { source, id, status ->
            if (source === pool) {
                val sound = samples.entries.firstOrNull { it.value == id }?.key
                if (status == 0 && sound != null) mix.loaded(sound)
                else Log.w(TAG, "Sound load failed: sample=$id status=$status")
            }
        }
        for (sound in StallSound.entries) {
            try {
                val id = context.assets.openFd(sound.asset).use { created.load(it, 1) }
                if (id != 0) samples[sound] = id else Log.w(TAG, "Could not queue ${sound.asset}")
            } catch (e: java.io.IOException) {
                Log.w(TAG, "Could not read ${sound.asset}", e)
            }
        }
    }

    private fun startStream(sound: StallSound): Int {
        val sample = samples[sound] ?: return 0
        val loop = sound == StallSound.MUSIC
        return pool?.play(sample, sound.volume, sound.volume, if (loop) 2 else 1,
            if (loop) -1 else 0, 1f) ?: 0
    }

    override fun cue(sound: StallSound) = mix.cue(sound)

    private fun abandonFocus() {
        val old = focus
        ++epoch
        granted = false
        focus = null
        if (old != null) manager?.abandonAudioFocusRequest(old)
    }

    override fun release() {
        setAudible(false)
        mix.clear()
        val old = pool
        pool = null
        samples.clear()
        old?.setOnLoadCompleteListener(null)
        old?.release()
    }

    private companion object { const val TAG = "NightStallAudio" }
}
