package rmjarvis.ultiobserver

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.VibrationAttributes
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.delay

/**
 * Timing alert sound preloader and player for the platform sound backend.
 *
 * @param soundPlayer The platform-specific sound backend.
 * @param loadSound Function used to load one clip into the backend; injectable for tests.
 */
internal class TimingAlertPlayer internal constructor(
    private val soundPlayer: TimingAlertSoundPlayer,
    private val loadSound: (TimingAlertSoundPlayer, TimingAlertSoundClip) -> Int,
) {
    constructor(context: Context) : this(
        soundPlayer = AndroidTimingAlertSoundPlayer(),
        loadSound = { soundPlayer, clip -> soundPlayer.load(context, clip.rawResourceId(), 1) },
    )

    private enum class SoundLoadState {
        LOADING,
        LOADED,
        FAILED,
    }

    private data class PlaySettings(
        val volume: Float,
        val priority: Int,
    )

    private val soundLoadStates = mutableMapOf<TimingAlertSoundClip, SoundLoadState>()
    private val pendingPlays = mutableMapOf<TimingAlertSoundClip, MutableList<PlaySettings>>()
    private val soundsById = mutableMapOf<Int, TimingAlertSoundClip>()
    private val soundIds = mutableMapOf<TimingAlertSoundClip, Int>()

    init {
        soundPlayer.setOnLoadCompleteListener { sampleId, status ->
            val clip = soundsById[sampleId] ?: return@setOnLoadCompleteListener
            if (status == 0) {
                soundLoadStates[clip] = SoundLoadState.LOADED
                pendingPlays.remove(clip)?.forEach { playSettings ->
                    playLoaded(clip, playSettings)
                }
            } else {
                soundLoadStates[clip] = SoundLoadState.FAILED
                pendingPlays.remove(clip)
            }
        }
        timingAlertSoundClips().forEach { clip ->
            loadClip(clip)
        }
    }

    /**
     * Play a timing alert sound at the requested SoundPool priority.
     *
     * @param sound The sound family to play.
     * @param repeatCount The number of repeats encoded in the clip to play.
     * @param volume The requested playback volume, clamped to the SoundPool range.
     * @param priority SoundPool stream priority for this play.
     */
    fun play(
        sound: TimingAlertSound,
        repeatCount: Int,
        volume: Float,
        priority: Int,
    ) {
        val clip = TimingAlertSoundClip(sound, repeatCount)
        val playSettings = PlaySettings(
            volume = volume.coerceIn(0f, 1f),
            priority = priority,
        )
        when (soundLoadStates[clip]) {
            SoundLoadState.LOADED -> playLoaded(clip, playSettings)
            SoundLoadState.FAILED -> {
                pendingPlays.remove(clip)
                loadClip(clip)
                pendingPlays[clip] = mutableListOf(playSettings)
            }
            SoundLoadState.LOADING, null -> {
                pendingPlays.getOrPut(clip) { mutableListOf() } += playSettings
            }
        }
    }

    /**
     * Play a sound clip that has already finished loading.
     *
     * @param clip The loaded sound clip to play.
     * @param playSettings The clamped playback volume and SoundPool stream priority.
     */
    private fun playLoaded(clip: TimingAlertSoundClip, playSettings: PlaySettings) {
        val soundId = soundIds[clip]!!
        soundPlayer.play(
            soundId = soundId,
            leftVolume = playSettings.volume,
            rightVolume = playSettings.volume,
            priority = playSettings.priority,
            loop = 0,
            rate = 1f,
        )
    }

    /// Start or retry loading a sound clip.
    private fun loadClip(clip: TimingAlertSoundClip) {
        val soundId = loadSound(soundPlayer, clip)
        soundIds[clip] = soundId
        soundsById[soundId] = clip
        soundLoadStates[clip] = SoundLoadState.LOADING
    }
}

internal const val TIMING_ALERT_PREVIEW_PRIORITY = 0
internal const val TIMING_ALERT_CUE_PRIORITY = 1

/**
 * Pre-rendered timing alert sound clip.
 *
 * @param sound The base sound family.
 * @param repeatCount The number of repeated cues encoded in the clip.
 */
internal data class TimingAlertSoundClip(val sound: TimingAlertSound, val repeatCount: Int) {
    init {
        require(repeatCount in MIN_TIMING_ALERT_REPEAT_COUNT..MAX_TIMING_ALERT_REPEAT_COUNT) {
            "Timing alert repeat count must be between $MIN_TIMING_ALERT_REPEAT_COUNT and " +
                "$MAX_TIMING_ALERT_REPEAT_COUNT."
        }
    }
}

/// List every sound clip that should be preloaded for timing alerts.
private fun timingAlertSoundClips(): List<TimingAlertSoundClip> {
    return TimingAlertSound.entries.flatMap { sound ->
        (MIN_TIMING_ALERT_REPEAT_COUNT..MAX_TIMING_ALERT_REPEAT_COUNT).map { repeatCount ->
            TimingAlertSoundClip(sound, repeatCount)
        }
    }
}

/// Abstraction over SoundPool operations so timing alert audio can be tested without Android audio
/// hardware.
internal interface TimingAlertSoundPlayer {
    /**
     * Register a listener for sound-load completion.
     *
     * @param listener Callback receiving SoundPool sample id and load status.
     */
    fun setOnLoadCompleteListener(listener: (sampleId: Int, status: Int) -> Unit)

    /**
     * Load one raw sound resource.
     *
     * @param context Android context used by SoundPool.
     * @param resId Raw resource id to load.
     * @param priority SoundPool load priority.
     */
    fun load(context: Context, resId: Int, priority: Int): Int

    /**
     * Play one loaded sound id.
     *
     * @param soundId Loaded sound id returned by SoundPool.
     * @param leftVolume Left channel volume.
     * @param rightVolume Right channel volume.
     * @param priority SoundPool playback priority.
     * @param loop SoundPool loop count.
     * @param rate SoundPool playback rate.
     */
    fun play(
        soundId: Int,
        leftVolume: Float,
        rightVolume: Float,
        priority: Int,
        loop: Int,
        rate: Float,
    )
}

/// Android SoundPool adapter for the TimingAlertSoundPlayer interface.
internal class AndroidTimingAlertSoundPlayer : TimingAlertSoundPlayer {
    private val soundPool = SoundPool.Builder()
        .setMaxStreams(1)
        .setAudioAttributes(
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_MEDIA)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build()
        )
        .build()

    /**
     * Register the Android SoundPool load-complete listener.
     *
     * @param listener Callback receiving loaded sample id and status.
     */
    override fun setOnLoadCompleteListener(listener: (sampleId: Int, status: Int) -> Unit) {
        soundPool.setOnLoadCompleteListener { _, sampleId, status ->
            listener(sampleId, status)
        }
    }

    /**
     * Load one Android raw sound resource into SoundPool.
     *
     * @param context Android context used to resolve the raw resource.
     * @param resId Raw resource id to load.
     * @param priority SoundPool load priority.
     */
    override fun load(context: Context, resId: Int, priority: Int): Int {
        return soundPool.load(context, resId, priority)
    }

    /**
     * Play one loaded SoundPool sound.
     *
     * @param soundId Loaded SoundPool sound id.
     * @param leftVolume Left channel volume.
     * @param rightVolume Right channel volume.
     * @param priority SoundPool playback priority.
     * @param loop SoundPool loop count.
     * @param rate SoundPool playback rate.
     */
    override fun play(
        soundId: Int,
        leftVolume: Float,
        rightVolume: Float,
        priority: Int,
        loop: Int,
        rate: Float,
    ) {
        soundPool.play(soundId, leftVolume, rightVolume, priority, loop, rate)
    }
}

private val timingAlertRawResources = mapOf(
    // Repeated cues are pre-rendered raw resources so playback uses one SoundPool call at runtime.
    // CC0 excerpts from Wikimedia Commons: Clicker_sound.ogg.
    TimingAlertSound.TICK to listOf(
        R.raw.timing_tick,
        R.raw.timing_tick_x2,
        R.raw.timing_tick_x3,
    ),
    TimingAlertSound.BEEP to listOf(
        R.raw.timing_beep,
        R.raw.timing_beep_x2,
        R.raw.timing_beep_x3,
    ),
    // Excerpts from Pixabay Content License sound: Ding~ by u_31vnwfmzt6.
    TimingAlertSound.DING to listOf(
        R.raw.timing_ding,
        R.raw.timing_ding_x2,
        R.raw.timing_ding_x3,
    ),
    // Pixabay Content License excerpt from freesound_community/ripper351: wood door knock.
    TimingAlertSound.KNOCK to listOf(
        R.raw.timing_knock,
        R.raw.timing_knock_x2,
        R.raw.timing_knock_x3,
    ),
)

/// Return the raw resource id for a timing alert sound clip.
internal fun TimingAlertSoundClip.rawResourceId(): Int {
    return timingAlertRawResources.getValue(sound)[repeatCount - MIN_TIMING_ALERT_REPEAT_COUNT]
}

/**
 * Play one timing alert cue through sound and/or haptics.
 *
 * @param cue The cue to play.
 * @param timingAlertPreferences The current alert settings.
 * @param timingAlertPlayer Sound player used for audible cues.
 * @param performHaptic Callback that performs one haptic pulse.
 * @param playedTimingAlertKeys Cue keys already played by the caller.
 * @param onAlertKeyPlayed Callback recording this cue key before playback.
 */
internal suspend fun playTimingAlertOnce(
    cue: TimingCueDisplay,
    timingAlertPreferences: TimingAlertPreferences,
    timingAlertPlayer: TimingAlertPlayer,
    performHaptic: suspend (Long) -> Unit,
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
    if (alertMode == TimingAlertMode.VIBRATE) {
        repeat(repeatCount) { pulseIndex ->
            performHaptic(
                timingAlertPreferences.vibrationDurationMillis,
            )
            if (pulseIndex < repeatCount - 1) {
                delay(timingAlertPreferences.vibrationRepeatSpacingMillis())
            }
        }
    } else {
        playTimingSound(
            alertMode.toTimingAlertSound(),
            repeatCount,
            timingAlertPreferences,
            timingAlertPlayer,
            performHaptic,
        )
    }
}

/**
 * Play the sound portion of a timing alert and optional paired haptic.
 *
 * @param sound The sound family to play.
 * @param repeatCount The configured repeat count.
 * @param timingAlertPreferences Current alert preferences for volume and haptic pairing.
 * @param timingAlertPlayer Sound player used for audible cues.
 * @param performHaptic Callback that performs one haptic pulse.
 */
private suspend fun playTimingSound(
    sound: TimingAlertSound,
    repeatCount: Int,
    timingAlertPreferences: TimingAlertPreferences,
    timingAlertPlayer: TimingAlertPlayer,
    performHaptic: suspend (Long) -> Unit,
) {
    timingAlertPlayer.play(
        sound = sound,
        repeatCount = repeatCount,
        volume = timingAlertPreferences.soundVolume,
        priority = TIMING_ALERT_CUE_PRIORITY,
    )
    if (timingAlertPreferences.vibrateWithSounds) {
        performHaptic(timingAlertPreferences.vibrationDurationMillis)
    }
}

/// Return spacing between repeated haptic pulses for this preference set.
private fun TimingAlertPreferences.vibrationRepeatSpacingMillis(): Long {
    return vibrationDurationMillis + TIMING_ALERT_REPEAT_HAPTIC_GAP_MS
}

/// Return whether the device reports usable timing-cue haptics.
internal fun Context.hasTimingCueHaptics(): Boolean {
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        getSystemService(VibratorManager::class.java).defaultVibrator
    } else {
        getSystemService(Vibrator::class.java)
    }
    return vibrator.hasVibrator()
}

/**
 * Perform a timing-cue haptic pulse when the device supports vibration.
 *
 * @param durationMillis The requested vibration duration in milliseconds.
 */
internal fun Context.performTimingCueHaptic(durationMillis: Long) {
    // Devices without usable vibration hardware should ignore haptic cues without crashing.
    if (!hasTimingCueHaptics()) {
        return
    }
    val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        getSystemService(VibratorManager::class.java).defaultVibrator
    } else {
        getSystemService(Vibrator::class.java)
    }
    val effect = VibrationEffect.createOneShot(durationMillis, VibrationEffect.DEFAULT_AMPLITUDE)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        vibrator.vibrate(
            effect,
            VibrationAttributes.createForUsage(VibrationAttributes.USAGE_NOTIFICATION),
        )
    } else {
        vibrator.vibrate(
            effect,
            AudioAttributes.Builder()
                .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build(),
        )
    }
}

internal const val TIMING_ALERT_REPEAT_HAPTIC_GAP_MS = 120L
