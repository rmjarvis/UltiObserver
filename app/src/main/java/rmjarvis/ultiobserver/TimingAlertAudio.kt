package rmjarvis.ultiobserver

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

internal class TimingAlertPlayer internal constructor(
    private val soundPlayer: TimingAlertSoundPlayer,
    loadSound: (TimingAlertSoundPlayer, TimingAlertSound) -> Int,
) {
    constructor(context: Context) : this(
        soundPlayer = AndroidTimingAlertSoundPlayer(),
        loadSound = { soundPlayer, sound -> soundPlayer.load(context, sound.rawResourceId(), 1) },
    )

    private val loadedSounds = mutableSetOf<TimingAlertSound>()
    private val pendingPlays = mutableMapOf<TimingAlertSound, Float>()
    private val soundsById = mutableMapOf<Int, TimingAlertSound>()
    private val soundIds: Map<TimingAlertSound, Int>

    init {
        soundPlayer.setOnLoadCompleteListener { sampleId, status ->
            val sound = soundsById[sampleId] ?: return@setOnLoadCompleteListener
            if (status == 0) {
                loadedSounds += sound
                pendingPlays.remove(sound)?.let { volume ->
                    playLoaded(sound, volume)
                }
            }
        }
        soundIds = TimingAlertSound.entries.associateWith { sound ->
            loadSound(soundPlayer, sound).also { soundId ->
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
        soundPlayer.release()
    }

    private fun playLoaded(sound: TimingAlertSound, volume: Float) {
        val soundId = soundIds[sound]!!
        soundPlayer.play(soundId, volume, volume, 1, 0, 1f)
    }
}

internal interface TimingAlertSoundPlayer {
    fun setOnLoadCompleteListener(listener: (sampleId: Int, status: Int) -> Unit)
    fun load(context: Context, resId: Int, priority: Int): Int
    fun play(soundId: Int, leftVolume: Float, rightVolume: Float, priority: Int, loop: Int, rate: Float)
    fun release()
}

private class AndroidTimingAlertSoundPlayer : TimingAlertSoundPlayer {
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(1)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    override fun setOnLoadCompleteListener(listener: (sampleId: Int, status: Int) -> Unit) {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            listener(sampleId, status)
        }
    }

    override fun load(context: Context, resId: Int, priority: Int): Int {
        return soundPool.load(context, resId, priority)
    }

    override fun play(soundId: Int, leftVolume: Float, rightVolume: Float, priority: Int, loop: Int, rate: Float) {
        soundPool.play(soundId, leftVolume, rightVolume, priority, loop, rate)
    }

    override fun release() {
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

internal fun playTimingAlertOnce(
    cue: TimingCueDisplay,
    timingAlertPreferences: TimingAlertPreferences,
    context: Context,
    timingAlertPlayer: TimingAlertPlayer,
    playedTimingAlertKeys: Set<String>,
    onAlertKeyPlayed: (String) -> Unit,
) {
    val alertKey = "${cue.id.name}:${cue.targetEpoch}"
    // Defensive timing guard so recomposition does not replay the same cue.
    if (alertKey in playedTimingAlertKeys) {
        return
    }
    onAlertKeyPlayed(alertKey)
    val alertMode = timingAlertPreferences.alertModeFor(cue.id)
    when (alertMode) {
        TimingAlertMode.NONE -> Unit
        TimingAlertMode.VIBRATE -> context.performTimingCueHaptic(
            timingAlertPreferences.vibrationDurationMillis,
        )
        TimingAlertMode.TICK,
        TimingAlertMode.BEEP,
        TimingAlertMode.DING,
        TimingAlertMode.DOUBLE_TICK -> playTimingSound(
            alertMode.toTimingAlertSound(),
            timingAlertPreferences,
            context,
            timingAlertPlayer,
        )
    }
}

private fun playTimingSound(
    sound: TimingAlertSound,
    timingAlertPreferences: TimingAlertPreferences,
    context: Context,
    timingAlertPlayer: TimingAlertPlayer,
) {
    if (timingAlertPreferences.vibrateWithSounds) {
        context.performTimingCueHaptic(timingAlertPreferences.vibrationDurationMillis)
    }
    timingAlertPlayer.play(sound, timingAlertPreferences.soundVolume)
}

internal fun Context.performTimingCueHaptic(durationMillis: Long) {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        getSystemService(VibratorManager::class.java)?.defaultVibrator
    } else {
        getSystemService(Vibrator::class.java)
    }
    // Devices without usable vibration hardware should ignore haptic cues without crashing.
    if (vibrator == null || !vibrator.hasVibrator()) {
        return
    }
    vibrator.vibrate(VibrationEffect.createOneShot(durationMillis, VibrationEffect.DEFAULT_AMPLITUDE))
}
