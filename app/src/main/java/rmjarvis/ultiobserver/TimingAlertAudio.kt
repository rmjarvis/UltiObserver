package rmjarvis.ultiobserver

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.delay

internal class TimingAlertPlayer internal constructor(
    private val soundPlayer: TimingAlertSoundPlayer,
    loadSound: (TimingAlertSoundPlayer, TimingAlertSoundClip) -> Int,
) {
    constructor(context: Context) : this(
        soundPlayer = AndroidTimingAlertSoundPlayer(),
        loadSound = { soundPlayer, clip -> soundPlayer.load(context, clip.rawResourceId(), 1) },
    )

    private val loadedSounds = mutableSetOf<TimingAlertSoundClip>()
    private val pendingPlays = mutableMapOf<TimingAlertSoundClip, MutableList<Float>>()
    private val soundsById = mutableMapOf<Int, TimingAlertSoundClip>()
    private val soundIds: Map<TimingAlertSoundClip, Int>

    init {
        soundPlayer.setOnLoadCompleteListener { sampleId, status ->
            val clip = soundsById[sampleId] ?: return@setOnLoadCompleteListener
            if (status == 0) {
                loadedSounds += clip
                pendingPlays.remove(clip)?.forEach { volume ->
                    playLoaded(clip, volume)
                }
            }
        }
        soundIds = timingAlertSoundClips().associateWith { clip ->
            loadSound(soundPlayer, clip).also { soundId ->
                soundsById[soundId] = clip
            }
        }
    }

    fun play(sound: TimingAlertSound, volume: Float) {
        play(sound, DEFAULT_TIMING_ALERT_REPEAT_COUNT, volume)
    }

    fun play(sound: TimingAlertSound, repeatCount: Int, volume: Float) {
        require(repeatCount in MIN_TIMING_ALERT_REPEAT_COUNT..MAX_TIMING_ALERT_REPEAT_COUNT) {
            "Timing alert repeat count must be between $MIN_TIMING_ALERT_REPEAT_COUNT and " +
                "$MAX_TIMING_ALERT_REPEAT_COUNT."
        }
        val clip = TimingAlertSoundClip(sound, repeatCount)
        val playVolume = volume.coerceIn(0f, 1f)
        if (clip in loadedSounds) {
            playLoaded(clip, playVolume)
        } else {
            pendingPlays.getOrPut(clip) { mutableListOf() } += playVolume
        }
    }

    fun release() {
        pendingPlays.clear()
        soundPlayer.release()
    }

    private fun playLoaded(clip: TimingAlertSoundClip, volume: Float) {
        val soundId = soundIds[clip]!!
        soundPlayer.play(soundId, volume, volume, 1, 0, 1f)
    }
}

internal data class TimingAlertSoundClip(val sound: TimingAlertSound, val repeatCount: Int)

private fun timingAlertSoundClips(): List<TimingAlertSoundClip> {
    return TimingAlertSound.entries.flatMap { sound ->
        (MIN_TIMING_ALERT_REPEAT_COUNT..MAX_TIMING_ALERT_REPEAT_COUNT).map { repeatCount ->
            TimingAlertSoundClip(sound, repeatCount)
        }
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

private fun TimingAlertSoundClip.rawResourceId(): Int {
    return when (sound) {
        // CC0 excerpts from Wikimedia Commons: Clicker_sound.ogg.
        TimingAlertSound.TICK -> when (repeatCount) {
            1 -> R.raw.timing_tick
            2 -> R.raw.timing_tick_x2
            3 -> R.raw.timing_tick_x3
            else -> error("Unsupported repeat count: $repeatCount")
        }
        TimingAlertSound.BEEP -> when (repeatCount) {
            1 -> R.raw.timing_beep
            2 -> R.raw.timing_beep_x2
            3 -> R.raw.timing_beep_x3
            else -> error("Unsupported repeat count: $repeatCount")
        }
        // Excerpts from Pixabay Content License sound: Ding~ by u_31vnwfmzt6.
        TimingAlertSound.DING -> when (repeatCount) {
            1 -> R.raw.timing_ding
            2 -> R.raw.timing_ding_x2
            3 -> R.raw.timing_ding_x3
            else -> error("Unsupported repeat count: $repeatCount")
        }
        // Public-domain excerpts from Wikimedia Commons: Knocking_on_wood_or_door.ogg.
        TimingAlertSound.KNOCK -> when (repeatCount) {
            1 -> R.raw.timing_knock
            2 -> R.raw.timing_knock_x2
            3 -> R.raw.timing_knock_x3
            else -> error("Unsupported repeat count: $repeatCount")
        }
    }
}

internal suspend fun playTimingAlertOnce(
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
    if (alertMode == TimingAlertMode.NONE) {
        return
    }

    val repeatCount = timingAlertPreferences.repeatCountFor(cue.id)
    when (alertMode) {
        TimingAlertMode.NONE -> Unit
        TimingAlertMode.VIBRATE -> {
            repeat(repeatCount) { pulseIndex ->
                context.performTimingCueHaptic(
                    timingAlertPreferences.vibrationDurationMillis,
                )
                if (pulseIndex < repeatCount - 1) {
                    delay(timingAlertPreferences.vibrationRepeatSpacingMillis())
                }
            }
        }
        TimingAlertMode.TICK,
        TimingAlertMode.BEEP,
        TimingAlertMode.DING,
        TimingAlertMode.KNOCK -> playTimingSound(
            alertMode.toTimingAlertSound(),
            repeatCount,
            timingAlertPreferences,
            context,
            timingAlertPlayer,
        )
    }
}

private suspend fun playTimingSound(
    sound: TimingAlertSound,
    repeatCount: Int,
    timingAlertPreferences: TimingAlertPreferences,
    context: Context,
    timingAlertPlayer: TimingAlertPlayer,
) {
    timingAlertPlayer.play(sound, repeatCount, timingAlertPreferences.soundVolume)
    if (timingAlertPreferences.vibrateWithSounds) {
        repeat(repeatCount) { pulseIndex ->
            context.performTimingCueHaptic(
                timingAlertPreferences.vibrationDurationMillis,
            )
            if (pulseIndex < repeatCount - 1) {
                delay(timingAlertPreferences.vibrationRepeatSpacingMillis())
            }
        }
    }
}

private fun TimingAlertPreferences.vibrationRepeatSpacingMillis(): Long {
    return vibrationDurationMillis + TIMING_ALERT_REPEAT_HAPTIC_GAP_MS
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

private const val TIMING_ALERT_REPEAT_HAPTIC_GAP_MS = 120L
