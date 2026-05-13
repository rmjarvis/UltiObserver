package rmjarvis.ultiobserver

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

internal class TimingAlertPlayer(
    context: Context,
) {
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(1)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
    private val loadedSounds = mutableSetOf<TimingAlertSound>()
    private val pendingPlays = mutableMapOf<TimingAlertSound, Float>()
    private val soundsById = mutableMapOf<Int, TimingAlertSound>()
    private val soundIds: Map<TimingAlertSound, Int>

    init {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            val sound = soundsById[sampleId] ?: return@setOnLoadCompleteListener
            if (status == 0) {
                loadedSounds += sound
                pendingPlays.remove(sound)?.let { volume ->
                    playLoaded(sound, volume)
                }
            }
        }
        soundIds = TimingAlertSound.entries.associateWith { sound ->
            soundPool.load(context, sound.rawResourceId(), 1).also { soundId ->
                soundsById[soundId] = sound
            }
        }
    }

    fun play(sound: TimingAlertSound, volume: Float) {
        val playVolume = volume.coerceIn(0f, 1f)
        if (sound in loadedSounds) {
            playLoaded(sound, playVolume)
        } else {
            pendingPlays[sound] = playVolume
        }
    }

    fun release() {
        pendingPlays.clear()
        soundPool.release()
    }

    private fun playLoaded(sound: TimingAlertSound, volume: Float) {
        val soundId = soundIds[sound]!!
        soundPool.play(soundId, volume, volume, 1, 0, 1f)
    }
}

private fun TimingAlertSound.rawResourceId(): Int {
    return when (this) {
        TimingAlertSound.TICK -> R.raw.timing_tick
        TimingAlertSound.BEEP -> R.raw.timing_beep
        TimingAlertSound.DING -> R.raw.timing_ding
        TimingAlertSound.DOUBLE_TICK -> R.raw.timing_double_tick
    }
}
