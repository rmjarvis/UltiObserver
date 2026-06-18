package rmjarvis.ultiobserver

import android.content.Context
import java.time.Duration
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertThrows
import org.junit.Test

/// Tests for timing cue selection, alert preferences, and alert delivery helpers.
class TestTimingCues : GameDomainTestFixtures() {
    /// Verify timing-alert delivery-window waits are deterministic outside the Compose listener.
    @Test
    fun timingAlertDeliveryWindowWaitsDeterministicallyNearCueTime() {
        val checkMillis = 250L

        assertEquals(
            TimingAlertDeliveryWindowResult(false, listOf(250L)),
            captureTimingAlertDeliveryWindow(millisUntilNextAlert = 501L, scheduleCheckMillis = checkMillis),
        )
        assertEquals(
            TimingAlertDeliveryWindowResult(true, listOf(500L)),
            captureTimingAlertDeliveryWindow(millisUntilNextAlert = 500L, scheduleCheckMillis = checkMillis),
        )
        assertEquals(
            TimingAlertDeliveryWindowResult(true, listOf(1L)),
            captureTimingAlertDeliveryWindow(millisUntilNextAlert = 1L, scheduleCheckMillis = checkMillis),
        )
        assertEquals(
            TimingAlertDeliveryWindowResult(true, emptyList()),
            captureTimingAlertDeliveryWindow(millisUntilNextAlert = 0L, scheduleCheckMillis = checkMillis),
        )
        assertEquals(
            TimingAlertDeliveryWindowResult(true, emptyList()),
            captureTimingAlertDeliveryWindow(millisUntilNextAlert = -1L, scheduleCheckMillis = checkMillis),
        )
    }

    /// Verify default alert settings, sound resources, and playback behavior.
    @Test
    fun timingAlertPreferencesAndPlayer() {
        assertEquals("Tick", TimingAlertSound.TICK.label)
        assertEquals("Sounds on", TimingAlertGlobalMode.SOUNDS_ON.label)
        val defaultTimingAlertPreferences = TimingAlertPreferences()
        assertEquals(TimingAlertGlobalMode.VIBRATION_ONLY, defaultTimingAlertPreferences.globalMode)
        assertEquals(0.5f, defaultTimingAlertPreferences.soundVolume, 0f)
        assertFalse(defaultTimingAlertPreferences.vibrateWithSounds)
        assertTrue(GameRules().hasEnabledCapTimingAlerts(defaultTimingAlertPreferences))
        assertFalse(
            GameRules(useHalfCap = false, useSoftCap = false, useHardCap = false)
                .hasEnabledCapTimingAlerts(defaultTimingAlertPreferences)
        )
        assertFalse(
            GameRules().hasEnabledCapTimingAlerts(
                defaultTimingAlertPreferences.copy(globalMode = TimingAlertGlobalMode.OFF)
            )
        )
        assertFalse(
            GameRules().hasEnabledCapTimingAlerts(
                defaultTimingAlertPreferences.copy(
                    cueModes = defaultTimingAlertPreferences.cueModes +
                        (TimingCueId.HALF_CAP to TimingAlertMode.NONE) +
                        (TimingCueId.SOFT_CAP to TimingAlertMode.NONE) +
                        (TimingCueId.HARD_CAP to TimingAlertMode.NONE),
                )
            )
        )
        val expectedDefaultModes = mapOf(
            TimingCueId.RECEIVING_TWENTY_FOR_HAND to TimingAlertMode.TICK,
            TimingCueId.RECEIVING_TEN_FOR_HAND to TimingAlertMode.TICK,
            TimingCueId.PULLING_TWENTY_TO_PULL to TimingAlertMode.VIBRATE,
            TimingCueId.TIMEOUT_CLEAR_FIELD to TimingAlertMode.BEEP,
            TimingCueId.TIMEOUT_OFFENSE_TWENTY to TimingAlertMode.TICK,
            TimingCueId.TIMEOUT_OFFENSE_TEN to TimingAlertMode.TICK,
            TimingCueId.TIMEOUT_BETWEEN_POINTS_ONE_MINUTE_FOR_HAND to TimingAlertMode.BEEP,
            TimingCueId.TIMEOUT_BETWEEN_POINTS_ONE_MINUTE_TO_PULL to TimingAlertMode.BEEP,
            TimingCueId.MISCONDUCT_OFFENSE_TWENTY to TimingAlertMode.TICK,
            TimingCueId.MISCONDUCT_OFFENSE_TEN to TimingAlertMode.TICK,
            TimingCueId.MISCONDUCT_DEFENSE_TWENTY to TimingAlertMode.VIBRATE,
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
                defaultTimingAlertPreferences.settingsModeFor(cueId),
            )
        }
        val expectedDefaultRepeatCounts = mapOf(
            TimingCueId.RECEIVING_TWENTY_FOR_HAND to 2,
            TimingCueId.TIMEOUT_OFFENSE_TWENTY to 2,
            TimingCueId.MISCONDUCT_OFFENSE_TWENTY to 2,
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
                defaultTimingAlertPreferences.repeatCountFor(cueId),
            )
        }
        val vibrationDefaultCues = TimingCueId.entries.filter { cueId ->
            defaultTimingAlertPreferences.alertModeFor(cueId) == TimingAlertMode.VIBRATE
        }
        assertEquals(
            TimingCueId.entries.filter { cueId ->
                defaultTimingAlertPreferences.settingsModeFor(cueId) != TimingAlertMode.NONE
            },
            vibrationDefaultCues,
        )
        assertEquals(
            listOf(
                TimingCueId.PULLING_TWENTY_TO_PULL,
                TimingCueId.MISCONDUCT_DEFENSE_TWENTY,
            ),
            TimingCueId.entries.filter { cueId ->
                defaultTimingAlertPreferences.settingsModeFor(cueId) == TimingAlertMode.VIBRATE
            },
        )
        assertEquals(
            TimingAlertMode.BEEP,
            defaultTimingAlertPreferences.copy(
                globalMode = TimingAlertGlobalMode.SOUNDS_ON,
                cueModes = defaultTimingAlertPreferences.cueModes +
                    (TimingCueId.PULLING_TIME_VIOLATION to TimingAlertMode.BEEP),
            )
                .alertModeFor(TimingCueId.PULLING_TIME_VIOLATION),
        )
        assertEquals(
            TimingAlertMode.VIBRATE,
            defaultTimingAlertPreferences.copy(cueModes = emptyMap())
                .settingsModeFor(TimingCueId.PULLING_TWENTY_TO_PULL),
        )
        assertEquals(
            TimingAlertMode.VIBRATE,
            defaultTimingAlertPreferences.copy(cueModes = emptyMap())
                .alertModeFor(TimingCueId.PULLING_TWENTY_TO_PULL),
        )
        assertEquals(
            TimingAlertMode.NONE,
            defaultTimingAlertPreferences.copy(
                cueModes = defaultTimingAlertPreferences.cueModes +
                    (TimingCueId.PULLING_TWENTY_TO_PULL to TimingAlertMode.NONE),
            )
                .alertModeFor(TimingCueId.PULLING_TWENTY_TO_PULL),
        )
        assertEquals(
            TimingAlertMode.NONE,
            defaultTimingAlertPreferences.copy(globalMode = TimingAlertGlobalMode.OFF)
                .alertModeFor(TimingCueId.PULLING_TWENTY_TO_PULL),
        )
        assertEquals(1, defaultTimingAlertPreferences.repeatCountFor(TimingCueId.PULLING_TWENTY_TO_PULL))
        assertEquals(
            3,
            defaultTimingAlertPreferences.copy(
                cueRepeatCounts = mapOf(TimingCueId.PULLING_TWENTY_TO_PULL to 3),
            )
                .repeatCountFor(TimingCueId.PULLING_TWENTY_TO_PULL),
        )
        assertEquals(
            3,
            defaultTimingAlertPreferences.copy(
                cueRepeatCounts = mapOf(TimingCueId.PULLING_TWENTY_TO_PULL to 99),
            )
                .repeatCountFor(TimingCueId.PULLING_TWENTY_TO_PULL),
        )
        assertEquals(
            1,
            defaultTimingAlertPreferences.copy(
                cueRepeatCounts = mapOf(TimingCueId.PULLING_TWENTY_TO_PULL to 0),
            )
                .repeatCountFor(TimingCueId.PULLING_TWENTY_TO_PULL),
        )
        assertEquals(
            1,
            defaultTimingAlertPreferences.copy(cueRepeatCounts = emptyMap())
                .repeatCountFor(TimingCueId.PULLING_TWENTY_TO_PULL),
        )
        assertEquals(TimingAlertSound.TICK, TimingAlertMode.TICK.toTimingAlertSound())
        assertEquals(TimingAlertSound.BEEP, TimingAlertMode.BEEP.toTimingAlertSound())
        assertEquals(TimingAlertSound.DING, TimingAlertMode.DING.toTimingAlertSound())
        assertEquals(TimingAlertSound.KNOCK, TimingAlertMode.KNOCK.toTimingAlertSound())
        assertEquals(
            listOf(TimingAlertSound.TICK, TimingAlertSound.BEEP, TimingAlertSound.KNOCK, TimingAlertSound.DING),
            TimingAlertSound.entries,
        )
        val expectedRawResources = mapOf(
            TimingAlertSound.TICK to listOf(R.raw.timing_tick, R.raw.timing_tick_x2, R.raw.timing_tick_x3),
            TimingAlertSound.BEEP to listOf(R.raw.timing_beep, R.raw.timing_beep_x2, R.raw.timing_beep_x3),
            TimingAlertSound.KNOCK to listOf(R.raw.timing_knock, R.raw.timing_knock_x2, R.raw.timing_knock_x3),
            TimingAlertSound.DING to listOf(R.raw.timing_ding, R.raw.timing_ding_x2, R.raw.timing_ding_x3),
        )
        expectedRawResources.forEach { (sound, rawResources) ->
            rawResources.forEachIndexed { repeatIndex, rawResource ->
                assertEquals(
                    "Raw resource for $sound x${repeatIndex + MIN_TIMING_ALERT_REPEAT_COUNT}",
                    rawResource,
                    TimingAlertSoundClip(sound, repeatIndex + MIN_TIMING_ALERT_REPEAT_COUNT).rawResourceId(),
                )
            }
        }
        val nonSoundModeException = assertThrows(IllegalStateException::class.java) {
            TimingAlertMode.VIBRATE.toTimingAlertSound()
        }
        assertEquals(
            "VIBRATE is not a sound timing alert mode.",
            nonSoundModeException.message,
        )
        val soundIds = TimingAlertSound.entries
            .flatMap { sound ->
                (MIN_TIMING_ALERT_REPEAT_COUNT..MAX_TIMING_ALERT_REPEAT_COUNT).map { repeatCount ->
                    TimingAlertSoundClip(sound, repeatCount)
                }
            }
            .withIndex()
            .associate { (index, clip) ->
                clip to index + 1
            }
        val tickClip = TimingAlertSoundClip(TimingAlertSound.TICK, 1)
        val tickX3Clip = TimingAlertSoundClip(TimingAlertSound.TICK, 3)
        val beepClip = TimingAlertSoundClip(TimingAlertSound.BEEP, 1)
        val dingClip = TimingAlertSoundClip(TimingAlertSound.DING, 1)
        val soundPlayer = FakeTimingAlertSoundPlayer()
        val timingAlertPlayer = TimingAlertPlayer(soundPlayer) { _, clip -> soundIds.getValue(clip) }
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
        // Deliver cue alerts through the same helper used by the app-level listener.
        val alertKeys = mutableListOf<String>()
        val performedHaptics = mutableListOf<Long>()
        val soundCue = TimingCueDisplay(
            id = TimingCueId.HALF_CAP,
            message = "Half cap",
            remaining = Duration.ZERO,
            countdownTime = Duration.ZERO,
            targetEpoch = 123_000L,
        )
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
        soundPlayer.completeLoad(soundIds.getValue(TimingAlertSoundClip(TimingAlertSound.DING, 3)))
        assertEquals(
            PlayedTimingAlertSound(soundIds.getValue(TimingAlertSoundClip(TimingAlertSound.DING, 3)), 0.25f),
            soundPlayer.playedSounds.last(),
        )

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
        assertEquals(
            PlayedTimingAlertSound(soundIds.getValue(tickClip), 0.6f),
            soundPlayer.playedSounds.last(),
        )

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

        runBlocking {
            playTimingAlertOnce(
                cue = soundCue.copy(id = TimingCueId.MISCONDUCT_DEFENSE_TWENTY, targetEpoch = 125_000L),
                timingAlertPreferences = TimingAlertPreferences(
                    cueModes = mapOf(TimingCueId.MISCONDUCT_DEFENSE_TWENTY to TimingAlertMode.VIBRATE),
                    cueRepeatCounts = mapOf(TimingCueId.MISCONDUCT_DEFENSE_TWENTY to 2),
                    vibrationDurationMillis = 0L,
                ),
                timingAlertPlayer = timingAlertPlayer,
                performHaptic = { durationMillis -> performedHaptics += durationMillis },
                playedTimingAlertKeys = emptySet(),
                onAlertKeyPlayed = {},
            )
        }
        assertEquals(listOf(80L, 0L, 0L), performedHaptics)

        val playedBeforeRelease = soundPlayer.playedSounds.toList()
        timingAlertPlayer.play(TimingAlertSound.BEEP, 0.25f)
        timingAlertPlayer.release()
        soundPlayer.completeLoad(soundIds.getValue(beepClip))
        assertEquals(playedBeforeRelease, soundPlayer.playedSounds)
        assertTrue(soundPlayer.released)

        val failedSoundPlayer = FakeTimingAlertSoundPlayer()
        val failedTimingAlertPlayer = TimingAlertPlayer(failedSoundPlayer) { _, clip -> soundIds.getValue(clip) }
        failedTimingAlertPlayer.play(TimingAlertSound.DING, 0.5f)
        failedSoundPlayer.completeLoad(soundIds.getValue(dingClip), status = 1)
        failedSoundPlayer.completeLoad(999)
        assertTrue(failedSoundPlayer.playedSounds.isEmpty())
    }

    /// Verify timeout, misconduct, and halftime countdown cue selection.
    @Test
    fun countdownTimingCuesUseTheRelevantWorkflowSchedule() {
        val timeoutCountdownWithDefaultTarget = CountdownState(
            kind = CountdownKind.TIME_OUT,
            label = "Offense set in",
            durationSeconds = 70,
            targetEpoch = 70_000L,
        )

        // Timeout cues stop at offense freeze, with the app's last reminder at countdown-from-five.
        assertEquals(TimingCueId.TIMEOUT_CLEAR_FIELD, timeoutCountdownWithDefaultTarget.nextTimingCue(40_000L)?.id)
        assertEquals(TimingCueId.TIMEOUT_COUNTDOWN_FROM_FIVE, timeoutCountdownWithDefaultTarget.nextTimingCue(65_000L)?.id)
        assertEquals(
            Duration.ofSeconds(5),
            timeoutCountdownWithDefaultTarget.nextTimingCue(65_000L)?.countdownTime,
        )
        assertEquals(
            TimingCueId.TIMEOUT_OFFENSE_FREEZE_DEFENSE_TWENTY,
            timeoutCountdownWithDefaultTarget.dueTimingCue(70_000L)?.id,
        )
        val misconductCountdown = CountdownState(
            kind = CountdownKind.TIME_OUT,
            label = "Offense set in",
            durationSeconds = 30,
            targetEpoch = 30_000L,
        )
        assertEquals(TimingCueId.TIMEOUT_OFFENSE_TWENTY, misconductCountdown.nextTimingCue(1_000L)?.id)
        assertEquals(TimingCueId.TIMEOUT_OFFENSE_TEN, misconductCountdown.nextTimingCue(20_000L)?.id)
        assertEquals(TimingCueId.TIMEOUT_COUNTDOWN_FROM_FIVE, misconductCountdown.nextTimingCue(25_000L)?.id)
        assertEquals(TimingCueId.TIMEOUT_OFFENSE_FREEZE_DEFENSE_TWENTY, misconductCountdown.dueTimingCue(30_000L)?.id)
        val betweenPointsMisconductCountdown = CountdownState(
            kind = CountdownKind.MISCONDUCT_BETWEEN_POINTS,
            label = "Offense set in",
            durationSeconds = 90,
            targetEpoch = 90_000L,
        )
        assertEquals(TimingCueId.MISCONDUCT_OFFENSE_TWENTY, betweenPointsMisconductCountdown.nextTimingCue(1_000L)?.id)
        assertEquals(TimingCueId.MISCONDUCT_OFFENSE_TEN, betweenPointsMisconductCountdown.nextTimingCue(80_000L)?.id)
        assertEquals(TimingCueId.MISCONDUCT_COUNTDOWN_FROM_FIVE, betweenPointsMisconductCountdown.nextTimingCue(85_000L)?.id)
        assertEquals(
            TimingCueId.MISCONDUCT_OFFENSE_FREEZE_DEFENSE_TWENTY,
            betweenPointsMisconductCountdown.dueTimingCue(90_000L)?.id,
        )
        val misconductDefenseCountdown = CountdownState(
            kind = CountdownKind.MISCONDUCT_DEFENSE_CHECK,
            label = "Defense check in",
            durationSeconds = 30,
            targetEpoch = 100_000L,
        )
        assertEquals(TimingCueId.MISCONDUCT_DEFENSE_TWENTY, misconductDefenseCountdown.nextTimingCue(70_000L)?.id)
        assertNull(misconductDefenseCountdown.nextTimingCue(81_000L))
        assertNull(misconductDefenseCountdown.dueTimingCue(100_000L))

        val halftimeCountdown = buildHalftimeCountdown(
            halftimeMinutes = 7,
            sequenceStart = 1_000L,
        )
        assertEquals(TimingCueId.HALFTIME_FIVE_MINUTES, halftimeCountdown.nextTimingCue(1_000L)?.id)
        assertEquals(TimingCueId.HALFTIME_TWO_MINUTES, halftimeCountdown.dueTimingCue(301_000L)?.id)
        val halftimeState = standardLiveGameState().copy(phase = GamePhase.HALFTIME, countdown = halftimeCountdown)
        assertFalse(halftimeState.halftimeTransitionReady(halftimeCountdown.targetEpoch - 1L))
        assertTrue(halftimeState.halftimeTransitionReady(halftimeCountdown.targetEpoch))
        assertFalse(
            halftimeState.copy(
                countdown = buildBetweenPointsCountdown(FieldEnd.NEAR, 2_000L),
            ).halftimeTransitionReady(halftimeCountdown.targetEpoch)
        )
        val shortHalftimeCountdown = buildHalftimeCountdown(
            halftimeMinutes = 2,
            sequenceStart = 1_000L,
        )
        assertEquals(TimingCueId.HALFTIME_TWO_MINUTES, shortHalftimeCountdown.nextTimingCue(1_000L)?.id)
    }

    /// Verify cap cue timing and merged next/due alert selection around scheduled cap instants.
    @Test
    fun capTimingCuesFireAtScheduledCapTimes() {
        var state = standardLiveGameState(
            rules = GameRules(
                gameTo = 15,
                halfCapMinutes = 45,
                softCapMinutes = 90,
                hardCapMinutes = 105,
            )
        )
        assertNull(state.dueCapTimingCue(state.startEpoch + 45 * 60_000L - 1L))
        assertEquals(TimingCueId.HALF_CAP, state.dueCapTimingCue(state.startEpoch + 45 * 60_000L)?.id)
        assertEquals(TimingCueId.HALF_CAP, state.dueCapTimingCue(state.startEpoch + 45 * 60_000L + 1_100L)?.id)
        assertNull(state.dueCapTimingCue(state.startEpoch + 45 * 60_000L + 1_101L))

        val halfCapTime = state.startEpoch + 45 * 60_000L
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
        assertTrue(TimingCueId.TIMEOUT_OFFENSE_FREEZE_DEFENSE_TWENTY in dueAlertIds)
        assertTrue(TimingCueId.HALF_CAP in dueAlertIds)

        // Pin the cue payload used by the alert player and its deduplication key.
        val dueCountdownCue = stateWithDueCountdown.countdown!!.dueTimingCue(halfCapTime)!!
        assertEquals(TimingCueId.TIMEOUT_OFFENSE_FREEZE_DEFENSE_TWENTY.label, dueCountdownCue.message)
        assertEquals("TIMEOUT_OFFENSE_FREEZE_DEFENSE_TWENTY:$halfCapTime", dueCountdownCue.alertKey())

        // Exercise alert merging when only zero, only cap, or only countdown cues are available.
        assertTrue(state.copy(halfCapApplied = true).dueTimingAlerts(halfCapTime).isEmpty())
        assertEquals(listOf(TimingCueId.HALF_CAP), state.copy(countdown = null).dueTimingAlerts(halfCapTime).map { it.id })
        assertEquals(TimingCueId.HALF_CAP, state.copy(countdown = null).nextTimingAlert(halfCapTime - 10_000L)?.id)

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
        // If cap cues are not relevant, the next countdown cue is still reported.
        assertEquals(
            TimingCueId.TIMEOUT_CLEAR_FIELD,
            countdownCueBeforeCap.copy(halfCapApplied = true).nextTimingAlert(halfCapTime - 10_000L)?.id,
        )
        assertEquals(
            TimingCueId.TIMEOUT_CLEAR_FIELD,
            countdownCueBeforeCap.copy(
                halfCapApplied = true,
                softCapApplied = true,
                hardCapApplied = true,
            ).nextTimingAlert(halfCapTime - 10_000L)?.id,
        )

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

        val dueAlertsWithEarlierCap = state.copy(
            countdown = CountdownState(
                kind = CountdownKind.TIME_OUT,
                label = "Offense set in",
                durationSeconds = 70,
                targetEpoch = halfCapTime + 20_500L,
            )
        ).dueTimingAlerts(halfCapTime + 500L).map { cue -> cue.id }
        assertEquals(
            listOf(TimingCueId.HALF_CAP, TimingCueId.TIMEOUT_OFFENSE_TWENTY),
            dueAlertsWithEarlierCap,
        )

        val dueAlertsWithoutCap = stateWithDueCountdown.copy(halfCapApplied = true)
            .dueTimingAlerts(halfCapTime)
            .map { cue -> cue.id }
        assertEquals(listOf(TimingCueId.TIMEOUT_OFFENSE_FREEZE_DEFENSE_TWENTY), dueAlertsWithoutCap)

        state = state.copy(halfCapApplied = true)
        assertEquals(TimingCueId.SOFT_CAP, state.dueCapTimingCue(state.startEpoch + 90 * 60_000L)?.id)

        state = state.copy(softCapApplied = true)
        assertEquals(TimingCueId.HARD_CAP, state.dueCapTimingCue(state.startEpoch + 105 * 60_000L)?.id)

        state = state.copy(hardCapApplied = true)
        assertNull(state.dueCapTimingCue(state.startEpoch + 105 * 60_000L))
        state = state.copy(halfCapApplied = false, softCapApplied = false, hardCapApplied = false)

        // Future cap cue lookup skips past elapsed caps and returns null once all scheduled caps are in the past.
        assertEquals(TimingCueId.SOFT_CAP, state.nextCapTimingCue(state.startEpoch + 60 * 60_000L)?.id)
        assertNull(state.nextCapTimingCue(state.startEpoch + 106 * 60_000L))
    }
}

/**
 * Capture the listener-ready result and requested delays from the timing-alert delivery-window helper.
 *
 * @param millisUntilNextAlert Milliseconds between now and the next alert target.
 * @param scheduleCheckMillis Normal listener polling cadence in milliseconds.
 */
private fun captureTimingAlertDeliveryWindow(
    millisUntilNextAlert: Long,
    scheduleCheckMillis: Long,
): TimingAlertDeliveryWindowResult {
    val delays = mutableListOf<Long>()
    val readyToPlay = runBlocking {
        waitForTimingAlertDeliveryWindow(
            millisUntilNextAlert = millisUntilNextAlert,
            scheduleCheckMillis = scheduleCheckMillis,
            delayMillis = { millis -> delays += millis },
        )
    }
    return TimingAlertDeliveryWindowResult(readyToPlay, delays)
}

/**
 * Captured timing-alert delivery-window behavior.
 *
 * @param readyToPlay Whether the caller should deliver the alert after the helper returns.
 * @param delays The delay durations requested before returning.
 */
private data class TimingAlertDeliveryWindowResult(
    val readyToPlay: Boolean,
    val delays: List<Long>,
)

/// Fake timing-alert sound backend that records plays and exposes manual load completion for unit tests.
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
    override fun play(soundId: Int, leftVolume: Float, rightVolume: Float, priority: Int, loop: Int, rate: Float) {
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
