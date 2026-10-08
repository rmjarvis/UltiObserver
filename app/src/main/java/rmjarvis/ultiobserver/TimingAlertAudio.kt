package rmjarvis.ultiobserver

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.VibrationEffect
import android.os.VibrationAttributes
import android.os.Vibrator
import android.os.VibratorManager

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
        soundPlayer = AndroidTimingAlertSoundPlayer(
            (context.applicationContext as UltiObserverApplication).soundOutputs,
        ),
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
     * Play a timing alert sound at the requested playback priority.
     *
     * @param sound The sound family to play.
     * @param repeatCount The number of repeats encoded in the clip to play.
     * @param volume The requested playback volume, clamped to 0–1.
     * @param priority Playback priority; higher-priority cues can interrupt previews.
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
     * @param playSettings The clamped playback volume and playback priority.
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

/** Sound-loading and playback interface for testing timing alerts without Android audio hardware. */
internal interface TimingAlertSoundPlayer {
    /**
     * Register a listener for sound-load completion.
     *
     * @param listener Callback receiving the loaded sound id and status (zero means success).
     */
    fun setOnLoadCompleteListener(listener: (sampleId: Int, status: Int) -> Unit)

    /**
     * Load one raw sound resource.
     *
     * @param context Android context used to read the raw resource.
     * @param resId Raw resource id to load.
     * @param priority Load priority; unused by the Android backend.
     */
    fun load(context: Context, resId: Int, priority: Int): Int

    /**
     * Play one loaded sound id.
     *
     * @param soundId Sound id returned by [load].
     * @param leftVolume Playback volume for the Android backend's mono clips.
     * @param rightVolume Right channel volume; unused by the Android mono backend.
     * @param priority Playback priority; a lower-priority request cannot interrupt an active cue.
     * @param loop Loop count; callers use zero because repeats are encoded in the clip.
     * @param rate Playback rate multiplier; 1 means normal speed.
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

/**
 * AudioTrack playback with output selection for bundled mono, 44.1 kHz, 16-bit PCM WAV cues.
 *
 * @param outputs Device discovery and routing for the currently playing cue.
 * @param createTrack Creates a track loaded with the supplied PCM samples. The single-argument
 * constructor supplies Android audio tracks; tests supply mocks to verify interruption priorities
 * and muting without depending on real playback timing.
 */
internal class AndroidTimingAlertSoundPlayer(
    private val outputs: SoundOutputManager,
    private val createTrack: (ByteArray) -> AudioTrack,
) : TimingAlertSoundPlayer {
    constructor(outputs: SoundOutputManager) : this(outputs, { pcm ->
        AudioTrack.Builder()
            .setAudioAttributes(timingSoundAudioAttributes())
            .setAudioFormat(AudioFormat.Builder()
                .setSampleRate(44_100)
                .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                .build())
            .setTransferMode(AudioTrack.MODE_STATIC)
            .setBufferSizeInBytes(pcm.size)
            .build().also { loaded ->
                loaded.write(pcm, 0, pcm.size)
            }
    })

    private val handler = Handler(Looper.getMainLooper())
    private lateinit var loadListener: (Int, Int) -> Unit
    private val samples = mutableMapOf<Int, ByteArray>()
    private val tracks = mutableMapOf<Int, AudioTrack>()
    private var activeTrack: AudioTrack? = null
    private var activePriority = TIMING_ALERT_PREVIEW_PRIORITY
    private var activeFrames = 0

    override fun setOnLoadCompleteListener(listener: (sampleId: Int, status: Int) -> Unit) {
        loadListener = listener
    }

    override fun load(context: Context, resId: Int, priority: Int): Int {
        // These app-owned WAV resources have a 44-byte header; their format is checked in tests.
        samples[resId] = context.resources.openRawResource(resId).use { stream ->
            stream.readBytes().let { it.copyOfRange(44, it.size) }
        }
        handler.post { loadListener(resId, 0) }
        return resId
    }

    override fun play(
        soundId: Int,
        leftVolume: Float,
        rightVolume: Float,
        priority: Int,
        loop: Int,
        rate: Float,
    ) {
        handler.post {
            if (outputs.state.value.muted) return@post
            val previous = activeTrack
            if (previous != null && previous.playState == AudioTrack.PLAYSTATE_PLAYING &&
                previous.playbackHeadPosition < activeFrames && priority < activePriority
            ) return@post

            previous?.stop()
            val pcm = samples.getValue(soundId)
            val track = tracks.getOrPut(soundId) {
                createTrack(pcm).also { loaded ->
                    loaded.setPlaybackPositionUpdateListener(
                        object : AudioTrack.OnPlaybackPositionUpdateListener {
                            override fun onMarkerReached(completed: AudioTrack) {
                                // A queued completion can outlive an interruption or replay.
                                if (completed == activeTrack &&
                                    completed.playbackHeadPosition >= activeFrames
                                ) {
                                    completed.stop()
                                    activeTrack = null
                                    outputs.detach()
                                }
                            }

                            // Coverage: Unused but required.
                            // Android requires this method, but we never enable periodic playback
                            // notifications or call it ourselves; only the completion marker is used.
                            override fun onPeriodicNotification(track: AudioTrack) = Unit
                        },
                        handler,
                    )
                }
            }
            track.reloadStaticData()
            track.setVolume(leftVolume)
            track.setPlaybackRate((44_100 * rate).toInt())
            activeFrames = pcm.size / 2
            track.setLoopPoints(0, activeFrames, loop)
            track.notificationMarkerPosition = activeFrames
            activeTrack = track
            activePriority = priority
            outputs.attach(track)
            track.play()
        }
    }
}

private val timingAlertRawResources = mapOf(
    // Repeated cues are pre-rendered raw resources so each sequence plays as one audio clip.
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
 * @param performHaptic Callback that submits a complete vibration pattern.
 * @param playedTimingAlertKeys Cue keys already played by the caller.
 * @param onAlertKeyPlayed Callback recording this cue key before playback.
 */
internal suspend fun playTimingAlertOnce(
    cue: TimingCueDisplay,
    timingAlertPreferences: TimingAlertPreferences,
    timingAlertPlayer: TimingAlertPlayer,
    performHaptic: suspend (VibrationPattern) -> Unit,
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
        performHaptic(VibrationPattern(
            timingAlertPreferences.vibrationDurationMillis,
            repeatCount,
            TIMING_ALERT_REPEAT_HAPTIC_GAP_MS,
        ))
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
 * @param performHaptic Callback that submits a complete vibration pattern.
 */
private suspend fun playTimingSound(
    sound: TimingAlertSound,
    repeatCount: Int,
    timingAlertPreferences: TimingAlertPreferences,
    timingAlertPlayer: TimingAlertPlayer,
    performHaptic: suspend (VibrationPattern) -> Unit,
) {
    timingAlertPlayer.play(
        sound = sound,
        repeatCount = repeatCount,
        volume = timingAlertPreferences.soundVolume,
        priority = TIMING_ALERT_CUE_PRIORITY,
    )
    if (timingAlertPreferences.vibrateWithSounds) {
        performHaptic(VibrationPattern(
            timingAlertPreferences.vibrationDurationMillis, 1, gapMillis = 0L,
        ))
    }
}

/**
 * Return whether the device reports usable timing-cue haptics.
 *
 * @param sdkInt Android API version. Production code always uses the default device version;
 * tests can override it to exercise behavior for different Android versions.
 */
internal fun Context.hasTimingCueHaptics(sdkInt: Int = Build.VERSION.SDK_INT): Boolean {
    val vibrator = if (sdkInt >= Build.VERSION_CODES.S) {
        getSystemService(VibratorManager::class.java).defaultVibrator
    } else {
        getSystemService(Vibrator::class.java)
    }
    return vibrator.hasVibrator()
}

/**
 * Submit a complete timing-cue vibration pattern when the device supports vibration.
 *
 * @param pattern The pulses and spacing to submit as one native vibration effect.
 * @param sdkInt Android API version. Production code always uses the default device version;
 * tests can override it to exercise behavior for different Android versions.
 */
internal fun Context.performTimingCueHaptic(
    pattern: VibrationPattern,
    sdkInt: Int = Build.VERSION.SDK_INT,
) {
    // Devices without usable vibration hardware should ignore haptic cues without crashing.
    if (!hasTimingCueHaptics(sdkInt)) {
        return
    }
    val vibrator = if (sdkInt >= Build.VERSION_CODES.S) {
        getSystemService(VibratorManager::class.java).defaultVibrator
    } else {
        getSystemService(Vibrator::class.java)
    }
    // -1 here means don't repeat the pattern.
    val effect = VibrationEffect.createWaveform(pattern.timings(), -1)
    if (sdkInt >= Build.VERSION_CODES.TIRAMISU) {
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

/** Route a complete vibration pattern to the watch when selected, falling back to the phone. */
internal suspend fun deliverTimingVibration(
    preferences: TimingAlertPreferences,
    pattern: VibrationPattern,
    vibrateOnWatch: suspend (VibrationPattern) -> Boolean,
    vibrateOnPhone: (VibrationPattern) -> Unit,
) {
    val accepted = preferences.usesWatchVibration() && vibrateOnWatch(pattern)
    if (!accepted) {
        vibrateOnPhone(pattern)
    }
}

internal const val TIMING_ALERT_REPEAT_HAPTIC_GAP_MS = 120L
