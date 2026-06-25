package rmjarvis.ultiobserver

import android.content.Context
import java.time.Duration
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/// Tests for timing cue selection, alert preferences, and alert delivery helpers.
class TestTimingCues : GameDomainTestFixtures() {
    /**
     * Test default timing-alert preferences.
     */
    @Test
    fun timingAlertPreferenceDefaults() {
        val defaultPreferences = TimingAlertPreferences()

        // Default preferences start in vibration-only mode with normal volume settings.
        assertEquals(TimingAlertGlobalMode.VIBRATION_ONLY, defaultPreferences.globalMode)
        assertEquals(0.5f, defaultPreferences.soundVolume, 0f)
        assertFalse(defaultPreferences.vibrateWithSounds)

        // Cap alerts are considered enabled only when relevant rules and cue settings allow them.
        assertTrue(GameRules().hasEnabledCapTimingAlerts(defaultPreferences))
        assertFalse(
            GameRules(
                useHalfCap = false,
                useSoftCap = false,
                useHardCap = false,
            ).hasEnabledCapTimingAlerts(defaultPreferences)
        )
        assertFalse(
            GameRules().hasEnabledCapTimingAlerts(
                defaultPreferences.copy(globalMode = TimingAlertGlobalMode.OFF)
            )
        )
        assertFalse(
            GameRules().hasEnabledCapTimingAlerts(
                defaultPreferences.copy(
                    cueModes = defaultPreferences.cueModes +
                        (TimingCueId.HALF_CAP to TimingAlertMode.NONE) +
                        (TimingCueId.SOFT_CAP to TimingAlertMode.NONE) +
                        (TimingCueId.HARD_CAP to TimingAlertMode.NONE),
                )
            )
        )

        // Default cue modes match the timing alert settings screen.
        val expectedDefaultModes = mapOf(
            TimingCueId.RECEIVING_TWENTY_FOR_HAND to TimingAlertMode.TICK,
            TimingCueId.RECEIVING_TEN_FOR_HAND to TimingAlertMode.TICK,
            TimingCueId.PULLING_TWENTY_TO_PULL to TimingAlertMode.VIBRATE,
            TimingCueId.TIMEOUT_CLEAR_FIELD to TimingAlertMode.BEEP,
            TimingCueId.OFFENSE_TWENTY to TimingAlertMode.TICK,
            TimingCueId.OFFENSE_TEN to TimingAlertMode.TICK,
            TimingCueId.TIMEOUT_BETWEEN_POINTS_ONE_MINUTE_FOR_HAND to TimingAlertMode.BEEP,
            TimingCueId.TIMEOUT_BETWEEN_POINTS_ONE_MINUTE_TO_PULL to TimingAlertMode.BEEP,
            TimingCueId.HALFTIME_FIVE_MINUTES to TimingAlertMode.KNOCK,
            TimingCueId.HALFTIME_TWO_MINUTES to TimingAlertMode.KNOCK,
            TimingCueId.HALF_CAP to TimingAlertMode.DING,
            TimingCueId.SOFT_CAP to TimingAlertMode.DING,
            TimingCueId.HARD_CAP to TimingAlertMode.DING,
        )
        TimingCueId.entries.forEach { cueId ->
            assertEquals(
                "Default alert mode for $cueId",
                expectedDefaultModes[cueId] ?: TimingAlertMode.NONE,
                defaultPreferences.settingsModeFor(cueId),
            )
        }

        // Default repeat counts are one unless a cue explicitly asks for repeated alerts.
        val expectedDefaultRepeatCounts = mapOf(
            TimingCueId.RECEIVING_TWENTY_FOR_HAND to 2,
            TimingCueId.OFFENSE_TWENTY to 2,
            TimingCueId.HALFTIME_FIVE_MINUTES to 2,
            TimingCueId.HALFTIME_TWO_MINUTES to 2,
            TimingCueId.HALF_CAP to 2,
            TimingCueId.SOFT_CAP to 2,
            TimingCueId.HARD_CAP to 3,
        )
        TimingCueId.entries.forEach { cueId ->
            assertEquals(
                "Default repeat count for $cueId",
                expectedDefaultRepeatCounts[cueId] ?: 1,
                defaultPreferences.repeatCountFor(cueId),
            )
        }
    }

    /**
     * Test timing-alert preference overrides and clamping.
     */
    @Test
    fun timingAlertPreferenceOverrides() {
        val defaultPreferences = TimingAlertPreferences()

        // Vibration-only global mode turns every configured alert into vibration.
        val vibrationDefaultCues = TimingCueId.entries.filter { cueId ->
            defaultPreferences.alertModeFor(cueId) == TimingAlertMode.VIBRATE
        }
        assertEquals(
            TimingCueId.entries.filter { cueId ->
                defaultPreferences.settingsModeFor(cueId) != TimingAlertMode.NONE
            },
            vibrationDefaultCues,
        )

        // The settings mode remains the cue-specific setting even in vibration-only mode.
        assertEquals(
            listOf(TimingCueId.PULLING_TWENTY_TO_PULL),
            TimingCueId.entries.filter { cueId ->
                defaultPreferences.settingsModeFor(cueId) == TimingAlertMode.VIBRATE
            },
        )
        assertEquals(
            TimingAlertMode.BEEP,
            defaultPreferences.copy(
                globalMode = TimingAlertGlobalMode.SOUNDS_ON,
                cueModes = defaultPreferences.cueModes +
                    (TimingCueId.PULLING_TIME_VIOLATION to TimingAlertMode.BEEP),
            )
                .alertModeFor(TimingCueId.PULLING_TIME_VIOLATION),
        )

        // Missing cue-mode overrides fall back to default settings.
        assertEquals(
            TimingAlertMode.VIBRATE,
            defaultPreferences.copy(cueModes = emptyMap())
                .settingsModeFor(TimingCueId.PULLING_TWENTY_TO_PULL),
        )
        assertEquals(
            TimingAlertMode.VIBRATE,
            defaultPreferences.copy(cueModes = emptyMap())
                .alertModeFor(TimingCueId.PULLING_TWENTY_TO_PULL),
        )

        // Cue-level NONE and global OFF both suppress alerts.
        assertEquals(
            TimingAlertMode.NONE,
            defaultPreferences.copy(
                cueModes = defaultPreferences.cueModes +
                    (TimingCueId.PULLING_TWENTY_TO_PULL to TimingAlertMode.NONE),
            )
                .alertModeFor(TimingCueId.PULLING_TWENTY_TO_PULL),
        )
        assertEquals(
            TimingAlertMode.NONE,
            defaultPreferences.copy(globalMode = TimingAlertGlobalMode.OFF)
                .alertModeFor(TimingCueId.PULLING_TWENTY_TO_PULL),
        )

        // Repeat-count overrides are clamped to the supported sound resources.
        // I.e. values can only end up between 1 and 3, inclusive.
        assertEquals(1, defaultPreferences.repeatCountFor(TimingCueId.PULLING_TWENTY_TO_PULL))
        assertEquals(
            3,
            defaultPreferences.copy(
                cueRepeatCounts = mapOf(TimingCueId.PULLING_TWENTY_TO_PULL to 3),
            )
                .repeatCountFor(TimingCueId.PULLING_TWENTY_TO_PULL),
        )
        assertEquals(
            3,
            defaultPreferences.copy(
                cueRepeatCounts = mapOf(TimingCueId.PULLING_TWENTY_TO_PULL to 99),
            )
                .repeatCountFor(TimingCueId.PULLING_TWENTY_TO_PULL),
        )
        assertEquals(
            1,
            defaultPreferences.copy(
                cueRepeatCounts = mapOf(TimingCueId.PULLING_TWENTY_TO_PULL to 0),
            )
                .repeatCountFor(TimingCueId.PULLING_TWENTY_TO_PULL),
        )
        assertEquals(
            1,
            defaultPreferences.copy(cueRepeatCounts = emptyMap())
                .repeatCountFor(TimingCueId.PULLING_TWENTY_TO_PULL),
        )
    }

    /**
     * Test timing-alert sound labels, mode mapping, and resources.
     */
    @Test
    fun timingAlertSounds() {
        // Sound and global-mode labels match the settings screen.
        assertEquals("Tick", TimingAlertSound.TICK.label)
        assertEquals("Sounds on", TimingAlertGlobalMode.SOUNDS_ON.label)

        // Sound modes map to their sound choice.
        assertEquals(TimingAlertSound.TICK, TimingAlertMode.TICK.toTimingAlertSound())
        assertEquals(TimingAlertSound.BEEP, TimingAlertMode.BEEP.toTimingAlertSound())
        assertEquals(TimingAlertSound.DING, TimingAlertMode.DING.toTimingAlertSound())
        assertEquals(TimingAlertSound.KNOCK, TimingAlertMode.KNOCK.toTimingAlertSound())
        assertEquals(
            listOf(
                TimingAlertSound.TICK,
                TimingAlertSound.BEEP,
                TimingAlertSound.KNOCK,
                TimingAlertSound.DING,
            ),
            TimingAlertSound.entries,
        )

        // Non-sound alert modes fail loudly if asked for a sound resource.
        val nonSoundModeException = assertThrows(IllegalStateException::class.java) {
            TimingAlertMode.VIBRATE.toTimingAlertSound()
        }
        assertEquals(
            "VIBRATE is not a sound timing alert mode.",
            nonSoundModeException.message,
        )

        // Every sound has raw resources for one, two, and three repeats.
        val expectedRawResources = mapOf(
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
            TimingAlertSound.KNOCK to listOf(
                R.raw.timing_knock,
                R.raw.timing_knock_x2,
                R.raw.timing_knock_x3,
            ),
            TimingAlertSound.DING to listOf(
                R.raw.timing_ding,
                R.raw.timing_ding_x2,
                R.raw.timing_ding_x3,
            ),
        )
        expectedRawResources.forEach { (sound, rawResources) ->
            rawResources.forEachIndexed { repeatIndex, rawResource ->
                assertEquals(
                    "Raw resource for $sound x${repeatIndex + MIN_TIMING_ALERT_REPEAT_COUNT}",
                    rawResource,
                    TimingAlertSoundClip(
                        sound,
                        repeatIndex + MIN_TIMING_ALERT_REPEAT_COUNT,
                    ).rawResourceId(),
                )
            }
        }
    }

    /**
     * Test timing-alert sound-player loading, playback, and release behavior.
     */
    @Test
    fun timingAlertSoundPlayer() {
        val soundIds = timingAlertSoundIds()
        val tickClip = TimingAlertSoundClip(TimingAlertSound.TICK, 1)
        val tickX3Clip = TimingAlertSoundClip(TimingAlertSound.TICK, 3)
        val beepClip = TimingAlertSoundClip(TimingAlertSound.BEEP, 1)
        val dingClip = TimingAlertSoundClip(TimingAlertSound.DING, 1)
        val soundPlayer = FakeTimingAlertSoundPlayer()
        val timingAlertPlayer = TimingAlertPlayer(
            soundPlayer = soundPlayer,
            loadSound = { _, clip -> soundIds.getValue(clip) },
        )

        // Sounds requested before loading are queued until their load completes.
        timingAlertPlayer.play(TimingAlertSound.TICK, 1.5f)
        timingAlertPlayer.play(TimingAlertSound.TICK, 0.5f)
        timingAlertPlayer.play(TimingAlertSound.TICK, 3, 0.75f)
        assertTrue(soundPlayer.playedSounds.isEmpty())
        soundPlayer.completeLoad(soundIds.getValue(tickClip))
        assertEquals(
            listOf(
                PlayedTimingAlertSound(soundIds.getValue(tickClip), 1f),
                PlayedTimingAlertSound(soundIds.getValue(tickClip), 0.5f),
            ),
            soundPlayer.playedSounds,
        )
        soundPlayer.completeLoad(soundIds.getValue(tickX3Clip))
        assertEquals(
            listOf(
                PlayedTimingAlertSound(soundIds.getValue(tickClip), 1f),
                PlayedTimingAlertSound(soundIds.getValue(tickClip), 0.5f),
                PlayedTimingAlertSound(soundIds.getValue(tickX3Clip), 0.75f),
            ),
            soundPlayer.playedSounds,
        )

        // Volumes are clamped to the SoundPool-supported range.
        timingAlertPlayer.play(TimingAlertSound.TICK, -0.5f)
        assertEquals(
            listOf(
                PlayedTimingAlertSound(soundIds.getValue(tickClip), 1f),
                PlayedTimingAlertSound(soundIds.getValue(tickClip), 0.5f),
                PlayedTimingAlertSound(soundIds.getValue(tickX3Clip), 0.75f),
                PlayedTimingAlertSound(soundIds.getValue(tickClip), 0f),
            ),
            soundPlayer.playedSounds,
        )

        // Repeat counts outside the available clip range fail loudly.
        val invalidRepeatCountException = assertThrows(IllegalArgumentException::class.java) {
            timingAlertPlayer.play(TimingAlertSound.TICK, 4, 0.5f)
        }
        assertThrows(IllegalArgumentException::class.java) {
            timingAlertPlayer.play(TimingAlertSound.TICK, 0, 0.5f)
        }
        assertEquals(
            "Timing alert repeat count must be between 1 and 3.",
            invalidRepeatCountException.message,
        )

        // Release suppresses pending sounds.
        val playedBeforeRelease = soundPlayer.playedSounds.toList()
        timingAlertPlayer.play(TimingAlertSound.BEEP, 0.25f)
        timingAlertPlayer.release()
        soundPlayer.completeLoad(soundIds.getValue(beepClip))
        assertEquals(playedBeforeRelease, soundPlayer.playedSounds)
        assertTrue(soundPlayer.released)

        // SoundPool reports failed loads with a nonzero status.  We do this manually here
        // with the FakeTimingAlertSoundPlayer by explicitly setting status=1 below.
        // A failed load should discard pending plays for that clip, and unrelated load
        // completions should not play sounds.
        // The failedTimingAlertPlayer we use here gives Ding a new sound id on each load
        // so the test can demonstrate that the retry requested a fresh load after the first
        // load failed.
        val failedSoundPlayer = FakeTimingAlertSoundPlayer()
        val dingSoundIds = mutableListOf<Int>()
        var nextDingSoundId = soundIds.getValue(dingClip)
        val failedTimingAlertPlayer = TimingAlertPlayer(
            soundPlayer = failedSoundPlayer,
            loadSound = { _, clip ->
                if (clip == dingClip) {
                    nextDingSoundId.also { soundId ->
                        dingSoundIds += soundId
                        nextDingSoundId += 1_000
                    }
                } else {
                    soundIds.getValue(clip)
                }
            },
        )
        failedTimingAlertPlayer.play(TimingAlertSound.DING, 0.5f)
        failedSoundPlayer.completeLoad(dingSoundIds[0], status = 1)
        failedSoundPlayer.completeLoad(999)
        assertTrue(failedSoundPlayer.playedSounds.isEmpty())

        // A later request for the same failed sound retries loading with a new sound id and
        // queues only the new play.
        // This time, we let the player complete the Load for the second Ding sound id,
        // and the sound shows up as having played.
        failedTimingAlertPlayer.play(TimingAlertSound.DING, 0.8f)
        failedSoundPlayer.completeLoad(dingSoundIds[1])
        assertEquals(
            listOf(PlayedTimingAlertSound(dingSoundIds[1], 0.8f)),
            failedSoundPlayer.playedSounds,
        )
    }

    /**
     * Test the helper that turns due timing cues into one-shot sound and haptic actions.
     *
     * The app-level timing-alert listener is responsible for deciding which cue is due.  Once it
     * has one, this helper records the cue's alert key, checks user preferences, dispatches sound
     * or haptics, and avoids retry loops for cues whose selected mode is silent.
     */
    @Test
    fun timingAlertDelivery() {
        val soundIds = timingAlertSoundIds()
        val tickClip = TimingAlertSoundClip(TimingAlertSound.TICK, 1)
        val dingX3Clip = TimingAlertSoundClip(TimingAlertSound.DING, 3)
        val soundPlayer = FakeTimingAlertSoundPlayer()
        val timingAlertPlayer = TimingAlertPlayer(
            soundPlayer = soundPlayer,
            loadSound = { _, clip -> soundIds.getValue(clip) },
        )
        val soundCue = TimingCueDisplay(
            id = TimingCueId.HALF_CAP,
            message = "Half cap",
            remaining = Duration.ZERO,
            countdownTime = Duration.ZERO,
            targetEpoch = 123_000L,
        )

        // A sound cue records its alert key before playback, uses the selected repeated sound
        // clip, and can still perform a haptic pulse when "vibrate with sounds" is enabled.
        val alertKeys = mutableListOf<String>()
        val performedHaptics = mutableListOf<Long>()
        runBlocking {
            playTimingAlertOnce(
                cue = soundCue,
                timingAlertPreferences = TimingAlertPreferences(
                    globalMode = TimingAlertGlobalMode.SOUNDS_ON,
                    soundVolume = 0.25f,
                    vibrateWithSounds = true,
                    vibrationDurationMillis = 80L,
                    cueModes = mapOf(TimingCueId.HALF_CAP to TimingAlertMode.DING),
                    cueRepeatCounts = mapOf(TimingCueId.HALF_CAP to 3),
                ),
                timingAlertPlayer = timingAlertPlayer,
                performHaptic = { durationMillis -> performedHaptics += durationMillis },
                playedTimingAlertKeys = emptySet(),
                onAlertKeyPlayed = { alertKey -> alertKeys += alertKey },
            )
        }
        assertEquals(listOf("HALF_CAP:123000"), alertKeys)
        soundPlayer.completeLoad(soundIds.getValue(dingX3Clip))
        assertEquals(
            PlayedTimingAlertSound(soundIds.getValue(dingX3Clip), 0.25f),
            soundPlayer.playedSounds.last(),
        )

        // The next cue uses the current preferences independently: here the same cue switches to
        // a single Tick at a louder volume and no extra haptic pulse.
        runBlocking {
            playTimingAlertOnce(
                cue = soundCue.copy(targetEpoch = 123_500L),
                timingAlertPreferences = TimingAlertPreferences(
                    globalMode = TimingAlertGlobalMode.SOUNDS_ON,
                    soundVolume = 0.6f,
                    vibrateWithSounds = false,
                    cueModes = mapOf(TimingCueId.HALF_CAP to TimingAlertMode.TICK),
                    cueRepeatCounts = mapOf(TimingCueId.HALF_CAP to 1),
                ),
                timingAlertPlayer = timingAlertPlayer,
                performHaptic = { durationMillis -> performedHaptics += durationMillis },
                playedTimingAlertKeys = emptySet(),
                onAlertKeyPlayed = {},
            )
        }
        soundPlayer.completeLoad(soundIds.getValue(tickClip))
        assertEquals(
            PlayedTimingAlertSound(soundIds.getValue(tickClip), 0.6f),
            soundPlayer.playedSounds.last(),
        )

        // Muted alerts still mark their alert key.  Otherwise the listener would keep seeing the
        // same due cue and repeatedly try to deliver an intentionally silent alert.
        val mutedAlertKeys = mutableListOf<String>()
        runBlocking {
            playTimingAlertOnce(
                cue = soundCue.copy(targetEpoch = 124_000L),
                timingAlertPreferences = TimingAlertPreferences(
                    cueModes = mapOf(TimingCueId.HALF_CAP to TimingAlertMode.NONE),
                ),
                timingAlertPlayer = timingAlertPlayer,
                performHaptic = { durationMillis -> performedHaptics += durationMillis },
                playedTimingAlertKeys = emptySet(),
                onAlertKeyPlayed = { alertKey -> mutedAlertKeys += alertKey },
            )
        }
        assertEquals(listOf("HALF_CAP:124000"), mutedAlertKeys)
        assertEquals(
            PlayedTimingAlertSound(soundIds.getValue(tickClip), 0.6f),
            soundPlayer.playedSounds.last(),
        )

        // Pure vibration alerts request one haptic pulse plus the configured repeats.  A zero
        // duration still goes through the callback so user-selected "silent" vibration is visible
        // to this helper.
        runBlocking {
            playTimingAlertOnce(
                cue = soundCue.copy(id = TimingCueId.DEFENSE_TWENTY, targetEpoch = 125_000L),
                timingAlertPreferences = TimingAlertPreferences(
                    cueModes = mapOf(TimingCueId.DEFENSE_TWENTY to TimingAlertMode.VIBRATE),
                    cueRepeatCounts = mapOf(TimingCueId.DEFENSE_TWENTY to 2),
                    vibrationDurationMillis = 0L,
                ),
                timingAlertPlayer = timingAlertPlayer,
                performHaptic = { durationMillis -> performedHaptics += durationMillis },
                playedTimingAlertKeys = emptySet(),
                onAlertKeyPlayed = {},
            )
        }
        assertEquals(listOf(80L, 0L, 0L), performedHaptics)
    }

    /**
     * Test how the timing-alert listener waits as an upcoming cue gets close.
     *
     * The real listener polls periodically so it does not keep the app awake continuously.  Once
     * the next cue is close enough, this helper switches from normal polling to a targeted delay
     * that wakes at the cue time.
     */
    @Test
    fun timingAlertDeliveryWindow() {
        val checkMillis = 250L

        // With a 250 ms polling cadence, the delivery window starts at 500 ms.  An alert 501 ms
        // away is still outside that window, so the listener should sleep for one normal poll and
        // report that nothing is ready to play yet.
        assertEquals(
            TimingAlertDeliveryWindowResult(false, 250L),
            timingAlertDeliveryWindow(
                millisUntilNextAlert = 501L,
                scheduleCheckMillis = checkMillis,
            ),
        )

        // Exactly on the delivery-window boundary, the listener commits to this cue and sleeps
        // until the target time rather than taking another normal polling step.
        assertEquals(
            TimingAlertDeliveryWindowResult(true, 500L),
            timingAlertDeliveryWindow(
                millisUntilNextAlert = 500L,
                scheduleCheckMillis = checkMillis,
            ),
        )

        // The same targeted-delay rule applies when the cue is only barely in the future.
        assertEquals(
            TimingAlertDeliveryWindowResult(true, 1L),
            timingAlertDeliveryWindow(
                millisUntilNextAlert = 1L,
                scheduleCheckMillis = checkMillis,
            ),
        )

        // A cue that is due right now is ready immediately, with no wait needed.
        assertEquals(
            TimingAlertDeliveryWindowResult(true, 0L),
            timingAlertDeliveryWindow(
                millisUntilNextAlert = 0L,
                scheduleCheckMillis = checkMillis,
            ),
        )

        // Slightly overdue cues also play immediately, because there is no useful wait left.
        assertEquals(
            TimingAlertDeliveryWindowResult(true, 0L),
            timingAlertDeliveryWindow(
                millisUntilNextAlert = -1L,
                scheduleCheckMillis = checkMillis,
            ),
        )
    }

    /**
     * Test timeout timing cues, including the optional defense-check sequence.
     */
    @Test
    fun timeoutTimingCues() {
        // Standard timeout cues stop at offense freeze, with a final countdown-from-five alert.
        val timeoutCountdown = CountdownState(
            kind = CountdownKind.TIME_OUT,
            label = "Offense set in",
            durationSeconds = 70,
            targetEpoch = 70_000L,
        )
        assertEquals(
            TimingCueId.TIMEOUT_CLEAR_FIELD,
            timeoutCountdown.nextTimingCue(40_000L)?.id
        )
        assertEquals(
            TimingCueId.OFFENSE_COUNTDOWN_FROM_FIVE,
            timeoutCountdown.nextTimingCue(65_000L)?.id,
        )
        assertEquals(Duration.ofSeconds(5), timeoutCountdown.nextTimingCue(65_000L)?.countdownTime)
        assertEquals(
            TimingCueId.OFFENSE_SET_LIMIT,
            timeoutCountdown.dueTimingCue(70_000L)?.id
        )

        // When the user enables defense-check countdowns and reports offense set, the timeout
        // flow continues with defense reminders before play restarts.
        val timeoutDefenseCountdown = CountdownState(
            kind = CountdownKind.DEFENSE_CHECK,
            label = "Defense check in",
            durationSeconds = 30,
            targetEpoch = 100_000L,
        )
        assertEquals(
            TimingCueId.DEFENSE_TWENTY,
            timeoutDefenseCountdown.nextTimingCue(70_000L)?.id,
        )
        assertEquals(
            TimingCueId.DEFENSE_TEN,
            timeoutDefenseCountdown.nextTimingCue(81_000L)?.id,
        )
        assertEquals(
            TimingCueId.DEFENSE_CHECK_LIMIT,
            timeoutDefenseCountdown.dueTimingCue(100_000L)?.id,
        )
    }

    /**
     * Test misconduct offense-set timing cues before and during a live point.
     */
    @Test
    fun misconductTimingCues() {
        // Short live-point misconduct-restart countdowns use the offense-set cue family without
        // the timeout-only clear-field alert.
        val livePointMisconductCountdown = CountdownState(
            kind = CountdownKind.TIME_OUT,
            label = "Offense set in",
            durationSeconds = 30,
            targetEpoch = 30_000L,
        )
        assertEquals(
            TimingCueId.OFFENSE_TWENTY,
            livePointMisconductCountdown.nextTimingCue(1_000L)?.id,
        )
        assertEquals(
            TimingCueId.OFFENSE_TEN,
            livePointMisconductCountdown.nextTimingCue(20_000L)?.id,
        )
        assertEquals(
            TimingCueId.OFFENSE_COUNTDOWN_FROM_FIVE,
            livePointMisconductCountdown.nextTimingCue(25_000L)?.id,
        )
        assertEquals(
            TimingCueId.OFFENSE_SET_LIMIT,
            livePointMisconductCountdown.dueTimingCue(30_000L)?.id,
        )

        // Between-points misconduct countdowns use offense reminders before offense is set.
        val betweenPointsMisconductCountdown = CountdownState(
            kind = CountdownKind.MISCONDUCT_BETWEEN_POINTS,
            label = "Offense set in",
            durationSeconds = 90,
            targetEpoch = 90_000L,
        )
        assertEquals(
            TimingCueId.OFFENSE_TWENTY,
            betweenPointsMisconductCountdown.nextTimingCue(1_000L)?.id,
        )
        assertEquals(
            TimingCueId.OFFENSE_TEN,
            betweenPointsMisconductCountdown.nextTimingCue(80_000L)?.id,
        )
        assertEquals(
            TimingCueId.OFFENSE_COUNTDOWN_FROM_FIVE,
            betweenPointsMisconductCountdown.nextTimingCue(85_000L)?.id,
        )
        assertEquals(
            TimingCueId.OFFENSE_SET_LIMIT,
            betweenPointsMisconductCountdown.dueTimingCue(90_000L)?.id,
        )

        // When the observer reports offense set after a between-points misconduct penalty, the
        // same defense-check cue sequence gives the defense its final restart window.
        val misconductDefenseCountdown = CountdownState(
            kind = CountdownKind.DEFENSE_CHECK,
            label = "Defense check in",
            durationSeconds = 30,
            targetEpoch = 100_000L,
        )
        assertEquals(
            TimingCueId.DEFENSE_TWENTY,
            misconductDefenseCountdown.nextTimingCue(70_000L)?.id,
        )
        assertEquals(
            TimingCueId.DEFENSE_TEN,
            misconductDefenseCountdown.nextTimingCue(81_000L)?.id,
        )
        assertEquals(
            TimingCueId.DEFENSE_CHECK_LIMIT,
            misconductDefenseCountdown.dueTimingCue(100_000L)?.id,
        )
    }

    /**
     * Test halftime countdown timing cues and transition readiness.
     */
    @Test
    fun halftimeTimingCues() {
        // Longer halftimes include five-minute and two-minute reminders.
        val halftimeCountdown = buildHalftimeCountdown(
            halftimeMinutes = 7,
            sequenceStart = 1_000L,
        )
        assertEquals(
            TimingCueId.HALFTIME_FIVE_MINUTES,
            halftimeCountdown.nextTimingCue(1_000L)?.id,
        )
        assertEquals(
            TimingCueId.HALFTIME_TWO_MINUTES,
            halftimeCountdown.dueTimingCue(301_000L)?.id,
        )

        // Halftime transition readiness only applies to an active halftime countdown.
        val halftimeState = standardLiveGameState().copy(
            phase = GamePhase.HALFTIME,
            countdown = halftimeCountdown,
        )
        assertFalse(halftimeState.halftimeTransitionReady(halftimeCountdown.targetEpoch - 1L))
        assertTrue(halftimeState.halftimeTransitionReady(halftimeCountdown.targetEpoch))
        assertFalse(
            halftimeState.copy(
                countdown = buildBetweenPointsCountdown(
                    pullingFromEnd = FieldEnd.NEAR,
                    sequenceStart = 2_000L,
                    promptTarget = PullPromptTarget.NEAR,
                ),
            ).halftimeTransitionReady(halftimeCountdown.targetEpoch)
        )

        // Short halftimes skip the five-minute cue.
        val shortHalftimeCountdown = buildHalftimeCountdown(
            halftimeMinutes = 2,
            sequenceStart = 1_000L,
        )
        assertEquals(
            TimingCueId.HALFTIME_TWO_MINUTES,
            shortHalftimeCountdown.nextTimingCue(1_000L)?.id,
        )
    }

    /**
     * Test cap timing cues at their scheduled times.
     */
    @Test
    fun capTimingCues() {
        var state = capTimingState()

        // Cap cues are due at the cap instant and inside the short due-alert tolerance window.
        assertNull(state.dueCapTimingCue(state.startEpoch + 45 * 60_000L - 1L))
        assertEquals(TimingCueId.HALF_CAP, state.dueCapTimingCue(halfCapTime(state))?.id)
        assertEquals(TimingCueId.HALF_CAP, state.dueCapTimingCue(halfCapTime(state) + 1_100L)?.id)
        assertNull(state.dueCapTimingCue(halfCapTime(state) + 1_101L))

        // Once one cap has been applied, later cap cues become the next relevant cap alerts.
        state = state.copy(halfCapApplied = true)
        assertEquals(TimingCueId.SOFT_CAP, state.dueCapTimingCue(softCapTime(state))?.id)
        state = state.copy(softCapApplied = true)
        assertEquals(TimingCueId.HARD_CAP, state.dueCapTimingCue(hardCapTime(state))?.id)
        state = state.copy(hardCapApplied = true)
        assertNull(state.dueCapTimingCue(hardCapTime(state)))

        // Future cap cue lookup skips past elapsed caps and ends after all scheduled caps pass.
        state = state.copy(
            halfCapApplied = false,
            softCapApplied = false,
            hardCapApplied = false,
        )
        assertEquals(
            TimingCueId.SOFT_CAP,
            state.nextCapTimingCue(state.startEpoch + 60 * 60_000L)?.id,
        )
        assertNull(state.nextCapTimingCue(state.startEpoch + 106 * 60_000L))
    }

    /**
     * Test how the listener merges cap alerts with active countdown alerts.
     *
     * Cap timing alerts are scheduled from the game clock, while countdown alerts are scheduled
     * from the active timeout, misconduct, or pull countdown.  The foreground listener asks for
     * one merged view of those sources, so this verifies that simultaneous cues are not dropped
     * and that "next alert" selection picks whichever source happens first.
     */
    @Test
    fun mergedTimingAlerts() {
        val state = capTimingState()
        val halfCapTime = halfCapTime(state)

        // A cap cue and a countdown cue can become due on the same listener pass.  Both need to
        // be returned so the service can deliver both alerts rather than letting one source hide
        // the other.
        val stateWithDueCountdown = state.copy(
            countdown = CountdownState(
                kind = CountdownKind.TIME_OUT,
                label = "Offense set in",
                durationSeconds = 70,
                targetEpoch = halfCapTime,
            )
        )
        val dueAlertIds = stateWithDueCountdown.dueTimingAlerts(halfCapTime).map { cue -> cue.id }
        assertEquals(2, dueAlertIds.size)
        assertTrue(TimingCueId.OFFENSE_SET_LIMIT in dueAlertIds)
        assertTrue(TimingCueId.HALF_CAP in dueAlertIds)

        // Countdown alert payloads carry the user-facing cue label and a stable key.  The service
        // records that key after delivery so a still-due cue is not played again on the next pass.
        val dueCountdownCue = stateWithDueCountdown.countdown!!.dueTimingCue(halfCapTime)!!
        assertEquals(TimingCueId.OFFENSE_SET_LIMIT.label, dueCountdownCue.message)
        assertEquals("OFFENSE_SET_LIMIT:$halfCapTime", dueCountdownCue.alertKey())

        // If the relevant cap is already handled and there is no countdown cue due at this time,
        // the merged due-alert list is empty.
        assertTrue(state.copy(halfCapApplied = true).dueTimingAlerts(halfCapTime).isEmpty())

        // With no active countdown, the merged lookup still reports cap alerts for both "due now"
        // and "next future alert" requests.
        assertEquals(
            listOf(TimingCueId.HALF_CAP),
            state.copy(countdown = null).dueTimingAlerts(halfCapTime).map { it.id },
        )
        assertEquals(
            TimingCueId.HALF_CAP,
            state.copy(countdown = null).nextTimingAlert(halfCapTime - 10_000L)?.id,
        )

        // If the countdown's next cue comes before the next cap time, the service should wake for
        // the countdown cue first.
        val countdownCueBeforeCap = state.copy(
            countdown = CountdownState(
                kind = CountdownKind.TIME_OUT,
                label = "Offense set in",
                durationSeconds = 70,
                targetEpoch = halfCapTime + 25_000L,
            )
        )
        assertEquals(
            TimingCueId.TIMEOUT_CLEAR_FIELD,
            countdownCueBeforeCap.nextTimingAlert(halfCapTime - 10_000L)?.id,
        )

        // Conversely, if the next cap time comes before the countdown's next cue, the service
        // should wake for the cap cue first.
        val capCueBeforeCountdown = state.copy(
            countdown = CountdownState(
                kind = CountdownKind.TIME_OUT,
                label = "Offense set in",
                durationSeconds = 70,
                targetEpoch = halfCapTime + 60_000L,
            )
        )
        assertEquals(
            TimingCueId.HALF_CAP,
            capCueBeforeCountdown.nextTimingAlert(halfCapTime - 10_000L)?.id,
        )

        // When multiple alerts are due on one pass but have different target times inside the
        // due-alert tolerance window, they are returned in chronological order.
        val dueAlertsWithEarlierCap = state.copy(
            countdown = CountdownState(
                kind = CountdownKind.TIME_OUT,
                label = "Offense set in",
                durationSeconds = 70,
                targetEpoch = halfCapTime + 20_500L,
            )
        ).dueTimingAlerts(halfCapTime + 500L).map { cue -> cue.id }
        assertEquals(
            listOf(TimingCueId.HALF_CAP, TimingCueId.OFFENSE_TWENTY),
            dueAlertsWithEarlierCap,
        )

        // Cap state should not suppress countdown alerts.  Once the half cap has already been
        // applied, the countdown cue is still selected even though later cap alerts may exist.
        assertEquals(
            TimingCueId.TIMEOUT_CLEAR_FIELD,
            countdownCueBeforeCap.copy(halfCapApplied = true)
                .nextTimingAlert(halfCapTime - 10_000L)
                ?.id,
        )
        assertEquals(
            TimingCueId.TIMEOUT_CLEAR_FIELD,
            countdownCueBeforeCap.copy(
                halfCapApplied = true,
                softCapApplied = true,
                hardCapApplied = true,
            ).nextTimingAlert(halfCapTime - 10_000L)?.id,
        )

        // Already-applied caps drop out of the merged due list, leaving any due countdown cue to
        // be delivered by itself.
        val dueAlertsWithoutCap = stateWithDueCountdown.copy(halfCapApplied = true)
            .dueTimingAlerts(halfCapTime)
            .map { cue -> cue.id }
        assertEquals(listOf(TimingCueId.OFFENSE_SET_LIMIT), dueAlertsWithoutCap)
    }

    /// Return fake SoundPool ids for every timing-alert sound clip.
    private fun timingAlertSoundIds(): Map<TimingAlertSoundClip, Int> {
        return TimingAlertSound.entries
            .flatMap { sound ->
                (MIN_TIMING_ALERT_REPEAT_COUNT..MAX_TIMING_ALERT_REPEAT_COUNT).map { repeatCount ->
                    TimingAlertSoundClip(sound, repeatCount)
                }
            }
            .withIndex()
            .associate { (index, clip) ->
                clip to index + 1
            }
    }

    /// Build a live game with all cap timing cues enabled.
    private fun capTimingState(): GameState {
        return standardLiveGameState(
            rules = GameRules(
                gameTo = 15,
                halfCapMinutes = 45,
                softCapMinutes = 90,
                hardCapMinutes = 105,
            )
        )
    }

    /// Return the half-cap cue epoch for the supplied state.
    private fun halfCapTime(state: GameState): Long = state.startEpoch + 45 * 60_000L

    /// Return the soft-cap cue epoch for the supplied state.
    private fun softCapTime(state: GameState): Long = state.startEpoch + 90 * 60_000L

    /// Return the hard-cap cue epoch for the supplied state.
    private fun hardCapTime(state: GameState): Long = state.startEpoch + 105 * 60_000L
}

/**
 * Fake timing-alert sound backend that records plays and exposes manual load completion for unit
 * tests.
 */
private class FakeTimingAlertSoundPlayer : TimingAlertSoundPlayer {
    private lateinit var listener: (sampleId: Int, status: Int) -> Unit

    val playedSounds = mutableListOf<PlayedTimingAlertSound>()
    var released = false

    /**
     * Capture the sound-load completion listener installed by the timing alert player.
     *
     * @param listener The listener the fake should call when a test completes loading.
     */
    override fun setOnLoadCompleteListener(listener: (sampleId: Int, status: Int) -> Unit) {
        this.listener = listener
    }

    /**
     * Fail if production resource loading is used by these unit tests.
     *
     * @param context Unused Android context from the interface.
     * @param resId Unused sound resource id from the interface.
     * @param priority Unused load priority from the interface.
     */
    override fun load(context: Context, resId: Int, priority: Int): Int {
        error("Unit tests provide sound ids directly.")
    }

    /**
     * Record a played sound and volume.
     *
     * @param soundId The loaded sound id requested by the player.
     * @param leftVolume The left-channel volume, mirrored by production code to the right channel.
     * @param rightVolume The right-channel volume from the interface.
     * @param priority The unused playback priority from the interface.
     * @param loop The unused loop setting from the interface.
     * @param rate The unused playback rate from the interface.
     */
    override fun play(
        soundId: Int,
        leftVolume: Float,
        rightVolume: Float,
        priority: Int,
        loop: Int,
        rate: Float,
    ) {
        playedSounds += PlayedTimingAlertSound(soundId, leftVolume)
    }

    /// Record that the fake timing-alert sound-player was released.
    override fun release() {
        released = true
    }

    /**
     * Simulate SoundPool completing a sound load.
     *
     * @param soundId The sound id whose load should complete.
     * @param status The load status to report; zero means success.
     */
    fun completeLoad(soundId: Int, status: Int = 0) {
        listener(soundId, status)
    }
}

/**
 * Recorded sound play requested by the fake timing-alert sound backend.
 *
 * @param soundId The fake loaded sound id.
 * @param volume The clamped volume requested by production code.
 */
private data class PlayedTimingAlertSound(
    val soundId: Int,
    val volume: Float,
)
