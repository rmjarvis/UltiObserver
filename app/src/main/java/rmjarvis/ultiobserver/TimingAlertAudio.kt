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
                .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()
    private val soundIds = TimingAlertSound.entries.associateWith { sound ->
        soundPool.load(context, sound.rawResourceId(), 1)
    }

    fun play(sound: TimingAlertSound, volume: Float) {
        val soundId = soundIds[sound]!!
        soundPool.play(soundId, volume, volume, 1, 0, 1f)
    }

    fun release() {
        soundPool.release()
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
