package com.david.nightkitchen.audio

import com.david.nightkitchen.game.KitchenEvent
import com.david.nightkitchen.game.KitchenState
import com.david.nightkitchen.game.Station

internal enum class StallSound(val asset: String, val volume: Float) {
    MUSIC("night_stall_music.wav", .38f),
    PAN("sizzle.wav", .62f),
    BELL("serve_bell.wav", .34f);

    companion object {
        fun forAction(before: KitchenState, after: KitchenState, event: KitchenEvent): StallSound? =
            when {
                after.servedCount > before.servedCount -> BELL
                event is KitchenEvent.Drop && event.station == Station.WOK &&
                    after.cooking.mask != before.cooking.mask -> PAN
                else -> null
            }
    }
}

internal interface StallAudio {
    fun setAudible(audible: Boolean)
    fun cue(sound: StallSound)
    fun release()
}

/** One loop per application; a late load or a completed order must never undo a mute. */
internal class StallMix(private val start: (StallSound) -> Int, private val stop: (Int) -> Unit) {
    private val ready = mutableSetOf<StallSound>()
    private val streams = mutableMapOf<StallSound, Int>()
    private var audible = false

    fun loaded(sound: StallSound) {
        ready.add(sound)
        startMusicIfReady()
    }

    fun setAudible(value: Boolean) {
        audible = value
        if (value) {
            startMusicIfReady()
        } else {
            streams.values.forEach(stop)
            streams.clear()
        }
    }

    fun cue(sound: StallSound) {
        if (!audible || sound == StallSound.MUSIC || sound !in ready) return
        streams.remove(sound)?.let(stop)
        start(sound).takeIf { it != 0 }?.let { streams[sound] = it }
    }

    private fun startMusicIfReady() {
        if (audible && StallSound.MUSIC in ready && StallSound.MUSIC !in streams) {
            start(StallSound.MUSIC).takeIf { it != 0 }?.let { streams[StallSound.MUSIC] = it }
        }
    }

    fun clear() {
        setAudible(false)
        ready.clear()
    }
}
