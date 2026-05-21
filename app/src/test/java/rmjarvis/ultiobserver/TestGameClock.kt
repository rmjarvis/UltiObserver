package rmjarvis.ultiobserver

import android.content.Context
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/// Tests for clock, countdown, formatting, timing cue, and timing alert audio helpers.
class TestGameClock : GameDomainTestFixtures() {
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

    /**
     * Test deterministic clock and countdown helpers that are public model surface.
     * These tests should pin time behavior without relying on the wall clock.
     */
    @Test
    fun clockAndCountdownDisplays() {
        val VC = TeamId.TEAM_ONE

        // Verify simple model defaults and labels used by setup, settings, and timing display surfaces.
        assertEquals("Pink", TeamColorChoice.PINK.label)
        assertEquals(0xFFFF4FA3, TeamColorChoice.PINK.accentArgb)
        assertEquals(0xFF2F1022, TeamColorChoice.PINK.contentArgb)
        val defaultTeamSetup = TeamSetup()
        assertEquals("", defaultTeamSetup.name)
        assertEquals(TeamColorChoice.WHITE, defaultTeamSetup.color)
        val priorCardRecord = PlayerCardRecord(
            team = VC,
            jerseyNumber = "8",
            priorYellows = 1,
            priorReds = 0,
        )
        assertEquals(VC, priorCardRecord.team)
        assertEquals("8", priorCardRecord.jerseyNumber)
        assertEquals(1, priorCardRecord.priorYellows)
        assertEquals(0, priorCardRecord.priorReds)
        assertEquals("Yellow", CardType.YELLOW.label)
        assertEquals("Tick", TimingAlertSound.TICK.label)
        assertEquals("Sounds On", TimingAlertGlobalMode.SOUNDS_ON.label)
        assertEquals("N/A", displayPlayerNumber(UNKNOWN_PLAYER_NUMBER))
        assertEquals("#8", displayPlayerNumber("8"))
        val defaultSetupState = GameSetupState(
            startDate = LocalDate.of(2026, 1, 1),
            startTime = LocalTime.of(10, 0),
            timeZone = ZoneId.of("America/New_York"),
        )
        assertEquals(GameRules(), defaultSetupState.rules)
        val timeoutCountdownWithDefaultTarget = CountdownState(
            kind = CountdownKind.TIME_OUT,
            label = "Offense set in",
            durationSeconds = 70,
            targetEpoch = 70_000L,
        )
        assertNull(timeoutCountdownWithDefaultTarget.betweenPointsTarget)
        val defaultTimingAlertPreferences = TimingAlertPreferences()
        assertEquals(TimingAlertGlobalMode.VIBRATION_ONLY, defaultTimingAlertPreferences.globalMode)
        assertEquals(0.5f, defaultTimingAlertPreferences.soundVolume, 0f)
        assertFalse(defaultTimingAlertPreferences.vibrateWithSounds)
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

        // Verify the setup-time default helper rounds to the next half hour using a caller-supplied clock.
        assertEquals(LocalTime.of(9, 0), nextHalfHourFrom(LocalTime.of(9, 0)))
        assertEquals(LocalTime.of(9, 30), nextHalfHourFrom(LocalTime.of(9, 0, 1)))
        assertEquals(LocalTime.of(9, 30), nextHalfHourFrom(LocalTime.of(9, 1)))
        assertEquals(LocalTime.of(9, 30), nextHalfHourFrom(LocalTime.of(9, 29)))
        assertEquals(LocalTime.of(10, 0), nextHalfHourFrom(LocalTime.of(9, 30)))
        assertEquals(LocalTime.MIDNIGHT, nextHalfHourFrom(LocalTime.of(23, 45)))

        // Verify formatClockTime for midnight, noon, morning, and afternoon values.
        assertEquals("12:00 AM", formatClockTime(LocalTime.MIDNIGHT))
        assertEquals("12:00 PM", formatClockTime(LocalTime.NOON))
        assertEquals("9:05 AM", formatClockTime(LocalTime.of(9, 5)))
        assertEquals("3:30 PM", formatClockTime(LocalTime.of(15, 30)))

        // Verify formatDuration clamps negative durations to zero and formats minute/second boundaries.
        assertEquals("0:00", formatDuration(Duration.ofSeconds(-3)))
        assertEquals("0:00", formatDuration(Duration.ZERO))
        assertEquals("0:59", formatDuration(Duration.ofSeconds(59)))
        assertEquals("1:00", formatDuration(Duration.ofSeconds(60)))
        assertEquals("1:01", formatDuration(Duration.ofSeconds(61)))
        assertEquals("61:01", formatDuration(Duration.ofSeconds(3661)))

        // Verify computeNextCapStatus reports the next relevant enabled cap from an explicit LocalTime.
        var state = standardLiveGameState(
            startTime = LocalTime.of(10, 0),
            rules = GameRules(gameTo = 15, halfCapMinutes = 45, softCapMinutes = 90, hardCapMinutes = 100),
        )
        val halfCapStatus = state.computeNextCapStatus(timestampAfterStart(state, 15))!!
        assertEquals("Half cap", halfCapStatus.label)
        assertEquals(Duration.ofMinutes(30), halfCapStatus.remaining)
        assertEquals(CapStatus("Half cap", Duration.ofMinutes(30)), halfCapStatus)
        assertEquals(
            CapStatus("Soft cap", Duration.ofMinutes(30)),
            state.copy(halfCapApplied = true).computeNextCapStatus(timestampAfterStart(state, 60)),
        )
        assertEquals(
            CapStatus("Hard cap", Duration.ofMinutes(5)),
            state.copy(halfCapApplied = true, softCapApplied = true).computeNextCapStatus(timestampAfterStart(state, 95)),
        )
        assertNull(state.computeNextCapStatus(timestampAfterStart(state, 200)))
        assertEquals(
            CapStatus("Soft cap", Duration.ofMinutes(30)),
            state.copy(halftimeTaken = true).computeNextCapStatus(timestampAfterStart(state, 60)),
        )
        assertNull(
            state.copy(
                halfCapApplied = true,
                softCapApplied = true,
                hardCapApplied = true,
            ).computeNextCapStatus(timestampAfterStart(state, 95))
        )

        // Verify cap countdowns can wrap across midnight when a late-night game crosses dates.
        state = standardLiveGameState(
            startDate = LocalDate.of(2026, 1, 1),
            startTime = LocalTime.of(23, 30),
            rules = GameRules(gameTo = 15, halfCapMinutes = 45, useSoftCap = false, useHardCap = false),
        )
        assertEquals(CapStatus("Half cap", Duration.ofMinutes(30)), state.computeNextCapStatus(timestampAfterStart(state, 15)))

        // Verify computeNextCapStatus returns null when no cap is still available.
        state = state.copy(halfCapApplied = true, softCapApplied = true, hardCapApplied = true)
        assertNull(state.computeNextCapStatus(timestampAfterStart(state, 15)))

        state = standardLiveGameState(
            startTime = LocalTime.of(10, 0),
            rules = GameRules(gameTo = 15, useHalfCap = false, useSoftCap = false, useHardCap = false),
        )
        assertNull(state.computeNextCapStatus(timestampAfterStart(state, 15)))

        // Verify betweenPointsDisplay gives "Signal in" vs "Pull in" and clamps elapsed countdowns to zero.
        assertEquals("Signal in" to Duration.ofSeconds(60), betweenPointsDisplay(FieldEnd.FAR, 1_000L, 1_000L))
        assertEquals("Signal in" to Duration.ofSeconds(30), betweenPointsDisplay(FieldEnd.FAR, 1_000L, 31_000L))
        assertEquals("Signal in" to Duration.ZERO, betweenPointsDisplay(FieldEnd.FAR, 1_000L, 70_000L))
        assertEquals("Pull in" to Duration.ofSeconds(80), betweenPointsDisplay(FieldEnd.NEAR, 2_000L, 2_000L))
        val standardPullCountdown = buildBetweenPointsCountdown(FieldEnd.NEAR, 2_000L)
        assertEquals(TimingCueId.PULLING_TWENTY_TO_PULL, standardPullCountdown.nextTimingCue(2_000L)?.id)
        assertEquals(60, BetweenPointsCountdownTarget.OFFENSE_READY.baseDurationSeconds(CountdownKind.BETWEEN_POINTS))
        assertEquals(80, BetweenPointsCountdownTarget.PULL.baseDurationSeconds(CountdownKind.BETWEEN_POINTS))
        assertEquals(30, BetweenPointsCountdownTarget.OFFENSE_READY.baseDurationSeconds(CountdownKind.PULL_RESET))
        assertEquals(30, BetweenPointsCountdownTarget.PULL.baseDurationSeconds(CountdownKind.PULL_RESET))

        // Verify the opening pull uses the first-point timing from the observer manual.
        val openingReceiveCountdown = buildBetweenPointsCountdown(
            pullingFromEnd = FieldEnd.FAR,
            sequenceStart = 1_000L,
            kind = CountdownKind.OPENING_PULL,
        )
        assertEquals(CountdownKind.OPENING_PULL, openingReceiveCountdown.kind)
        assertEquals(20, openingReceiveCountdown.durationSeconds)
        assertEquals(1_000L, openingReceiveCountdown.nextTimingCue(1_000L)?.targetEpoch)
        assertEquals(TimingCueId.RECEIVING_TWENTY_FOR_HAND, openingReceiveCountdown.dueTimingCue(1_000L)?.id)
        assertEquals(TimingCueId.RECEIVING_TEN_FOR_HAND, openingReceiveCountdown.nextTimingCue(2_000L)?.id)
        assertNull(openingReceiveCountdown.dueTimingCue(999L))
        assertNull(openingReceiveCountdown.nextTimingCue(openingReceiveCountdown.targetEpoch + 1L))
        assertNull(openingReceiveCountdown.dueTimingCue(openingReceiveCountdown.targetEpoch + 1_101L))

        val openingPullCountdown = buildBetweenPointsCountdown(
            pullingFromEnd = FieldEnd.NEAR,
            sequenceStart = 1_000L,
            kind = CountdownKind.OPENING_PULL,
        )
        assertEquals(40, openingPullCountdown.durationSeconds)
        assertEquals(TimingCueId.PULLING_TWENTY_TO_PULL, openingPullCountdown.nextTimingCue(1_000L)?.id)
        assertEquals(Duration.ofSeconds(20), openingPullCountdown.nextTimingCue(1_000L)?.remaining)
        assertEquals(Duration.ofSeconds(20), openingPullCountdown.nextTimingCue(1_000L)?.countdownTime)

        val invalidBetweenPointsKindException = assertThrows(IllegalArgumentException::class.java) {
            buildBetweenPointsCountdown(
                pullingFromEnd = FieldEnd.NEAR,
                sequenceStart = 1_000L,
                kind = CountdownKind.TIME_OUT,
            )
        }
        assertEquals(
            "Countdown kind TIME_OUT does not use between-points timing.",
            invalidBetweenPointsKindException.message,
        )
        assertTrue(CountdownKind.OPENING_PULL.usesBetweenPointsTarget())
        assertTrue(CountdownKind.BETWEEN_POINTS.usesBetweenPointsTarget())
        assertTrue(CountdownKind.PULL_RESET.usesBetweenPointsTarget())
        assertFalse(CountdownKind.MISCONDUCT_BETWEEN_POINTS.usesBetweenPointsTarget())
        assertFalse(CountdownKind.MISCONDUCT_DEFENSE_CHECK.usesBetweenPointsTarget())
        assertFalse(CountdownKind.TIME_OUT.usesBetweenPointsTarget())
        assertFalse(CountdownKind.HALFTIME.usesBetweenPointsTarget())
        assertEquals("LocalDateAsString", LocalDateAsStringSerializer.descriptor.serialName)
        assertEquals("LocalTimeAsString", LocalTimeAsStringSerializer.descriptor.serialName)
        assertEquals("ZoneIdAsString", ZoneIdAsStringSerializer.descriptor.serialName)

        // Verify timeout cues stop at offense freeze, with the app's last reminder at countdown-from-five.
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
        val halftimeState = state.copy(phase = GamePhase.HALFTIME, countdown = halftimeCountdown)
        assertFalse(halftimeState.halftimeTransitionReady(halftimeCountdown.targetEpoch - 1L))
        assertTrue(halftimeState.halftimeTransitionReady(halftimeCountdown.targetEpoch))
        assertFalse(halftimeState.copy(countdown = standardPullCountdown).halftimeTransitionReady(halftimeCountdown.targetEpoch))
        val shortHalftimeCountdown = buildHalftimeCountdown(
            halftimeMinutes = 2,
            sequenceStart = 1_000L,
        )
        assertEquals(TimingCueId.HALFTIME_TWO_MINUTES, shortHalftimeCountdown.nextTimingCue(1_000L)?.id)

        // Verify manual countdown adjustments move only the target time and keep normal remaining-time math running.
        state = standardLiveGameState()
        val originalCountdown = state.countdown!!
        state = state.addTimeToCountdown(65)
        assertEquals(originalCountdown.targetEpoch + 65_000L, state.countdown?.targetEpoch)
        assertEquals(originalCountdown.durationSeconds, state.countdown?.durationSeconds)
        assertEquals("Adjusted timer by 1:05.", state.lastEvent)

        state = state.addTimeToCountdown(-5)
        assertEquals(originalCountdown.targetEpoch + 60_000L, state.countdown?.targetEpoch)
        assertEquals(originalCountdown.durationSeconds, state.countdown?.durationSeconds)
        assertEquals(Duration.ofMillis(state.countdown!!.targetEpoch - 10_000L), state.countdown!!.remainingDuration(10_000L))
        assertEquals("Adjusted timer by -0:05.", state.lastEvent)

        // Verify pausing freezes display time, suppresses cues/transitions, and resumes from the same countdown value.
        // Direct duplicate pause/resume calls are not normal UI paths, but can happen if callbacks race recomposition.
        state = state.toggleCountdownPaused(10_000L)
        val pausedCountdown = state.countdown!!
        assertTrue(pausedCountdown.isPaused())
        assertEquals(10_000L, pausedCountdown.pausedAtEpoch)
        assertEquals(pausedCountdown, pausedCountdown.pause(20_000L))
        assertEquals(originalCountdown.targetEpoch + 60_000L, pausedCountdown.targetEpoch)
        assertEquals(Duration.ofMillis(pausedCountdown.targetEpoch - 10_000L), pausedCountdown.remainingDuration(30_000L))
        assertEquals(Duration.ofMillis(pausedCountdown.targetEpoch - 10_000L), state.activeCountdownDisplay(30_000L)?.remaining)
        assertTrue(state.activeCountdownDisplay(30_000L)?.isPaused == true)
        assertNull(pausedCountdown.nextTimingCue(30_000L))
        assertTrue(state.dueTimingAlerts(pausedCountdown.targetEpoch).isEmpty())
        assertEquals(state, state.applyExpiredCountdownTransitions(pausedCountdown.targetEpoch + 1_000L))

        state = state.addTimeToCountdown(5)
        assertEquals(pausedCountdown.targetEpoch + 5_000L, state.countdown?.targetEpoch)
        assertEquals(10_000L, state.countdown?.pausedAtEpoch)

        state = state.toggleCountdownPaused(25_000L)
        assertFalse(state.countdown!!.isPaused())
        assertNull(state.countdown?.pausedAtEpoch)
        assertEquals(pausedCountdown.targetEpoch + 20_000L, state.countdown?.targetEpoch)
        assertEquals(state.countdown, state.countdown?.resume(30_000L))
        assertEquals("Timer resumed.", state.lastEvent)

        val livePointWithoutCountdown = state.beginLivePoint()
        assertEquals(livePointWithoutCountdown, livePointWithoutCountdown.addTimeToCountdown(5))
        assertEquals(livePointWithoutCountdown, livePointWithoutCountdown.toggleCountdownPaused(5_000L))

        // Countdown target swapping is a no-op for non-between-points countdowns and fails on malformed ones.
        val inPointTimeoutCountdown = livePointWithoutCountdown.assessTimeout(VC, 600_000L).state.countdown!!
        assertEquals(inPointTimeoutCountdown, inPointTimeoutCountdown.swapOD())
        val malformedCountdown = CountdownState(
            kind = CountdownKind.BETWEEN_POINTS,
            label = "Signal in",
            durationSeconds = 60,
            targetEpoch = 60_000L,
        )
        assertThrows(NullPointerException::class.java) {
            malformedCountdown.swapOD()
        }
        assertThrows(NullPointerException::class.java) {
            malformedCountdown.nextTimingCue(1_000L)
        }

        // A countdown kind that does not match the phase is an impossible model state, so fail loudly.
        val mismatchedCountdownState = standardLiveGameState().copy(phase = GamePhase.LIVE_POINT)
        val mismatchException = assertThrows(IllegalStateException::class.java) {
            mismatchedCountdownState.applyExpiredCountdownTransitions(mismatchedCountdownState.countdown!!.targetEpoch)
        }
        assertEquals(
            "Countdown OPENING_PULL is not valid while game phase is LIVE_POINT.",
            mismatchException.message,
        )
        val betweenPointsWithTimeoutCountdown = standardLiveGameState().copy(
            countdown = inPointTimeoutCountdown,
        )
        val betweenPointsMismatchException = assertThrows(IllegalStateException::class.java) {
            betweenPointsWithTimeoutCountdown.applyExpiredCountdownTransitions(inPointTimeoutCountdown.targetEpoch)
        }
        assertEquals(
            "Countdown TIME_OUT is not valid while game phase is BETWEEN_POINTS.",
            betweenPointsMismatchException.message,
        )
        val halftimeWithBetweenPointsCountdown = standardLiveGameState().copy(
            phase = GamePhase.HALFTIME,
        )
        val halftimeMismatchException = assertThrows(IllegalStateException::class.java) {
            halftimeWithBetweenPointsCountdown.applyExpiredCountdownTransitions(halftimeWithBetweenPointsCountdown.countdown!!.targetEpoch)
        }
        assertEquals(
            "Countdown OPENING_PULL is not valid while game phase is HALFTIME.",
            halftimeMismatchException.message,
        )

        // Verify between-points countdown expiration silently starts the point, but leaves an undo path.
        state = standardLiveGameState()
        val betweenPointsCountdown = state.countdown!!
        assertEquals(state, state.applyExpiredCountdownTransitions(betweenPointsCountdown.targetEpoch - 1L))
        val automaticStartState = state.applyExpiredCountdownTransitions(betweenPointsCountdown.targetEpoch)
        assertEquals(GamePhase.LIVE_POINT, automaticStartState.phase)
        assertNull(automaticStartState.countdown)
        assertEquals("Point is live.", automaticStartState.lastEvent)
        assertEquals("Undo Start Point", automaticStartState.undoEntry?.label)
        val expiredPullDecisionState = state.copy(
            countdown = null,
            pullCountdownExpired = true,
        )
        val undoneAutomaticStartState = assertUndoRestores(expiredPullDecisionState, automaticStartState)
        assertEquals(undoneAutomaticStartState, undoneAutomaticStartState.redoLastAction().undoLastAction())
        assertEquals(state, state.redoLastAction())
        assertEquals(undoneAutomaticStartState, undoneAutomaticStartState.applyExpiredCountdownTransitions(betweenPointsCountdown.targetEpoch))
        assertTrue(undoneAutomaticStartState.hasExpiredPullActions())
        assertFalse(state.hasExpiredPullActions())
        assertTrue(state.isInitialLivePreview())
        assertFalse(automaticStartState.isInitialLivePreview())
        assertFalse(state.copy(teamOne = state.teamOne.copy(score = 1)).isInitialLivePreview())
        assertFalse(state.copy(teamTwo = state.teamTwo.copy(score = 1)).isInitialLivePreview())
        assertFalse(
            state.copy(undoEntry = UndoEntry("Undo placeholder", state)).isInitialLivePreview()
        )
        assertFalse(state.copy(halftimeTaken = true).isInitialLivePreview())

        // In-point timeout countdowns still continue automatically.
        state = state.beginLivePoint()
        state = state.assessTimeout(VC, 500_000L).state
        val timeoutCountdown = state.countdown!!
        assertEquals(state, state.applyExpiredCountdownTransitions(timeoutCountdown.targetEpoch - 1L))
        state = state.applyExpiredCountdownTransitions(timeoutCountdown.targetEpoch)
        assertEquals(GamePhase.LIVE_POINT, state.phase)
        assertNull(state.countdown)

        val halftimePrompt = GamePrompt.HalftimeStarted(state)
        assertEquals("Halftime", halftimePrompt.formatTitle())
        assertEquals("Announce halftime.", halftimePrompt.formatMessage())
        val gameOverState = state.copy(
            phase = GamePhase.GAME_OVER,
            teamOne = state.teamOne.copy(score = 3),
            teamTwo = state.teamTwo.copy(score = 5),
        )
        val gameOverPrompt = GamePrompt.GameOver(gameOverState)
        assertEquals("Game Over", gameOverPrompt.formatTitle())
        assertEquals("Animal 5\nViscous Coupling 3", gameOverPrompt.formatMessage())
        // Game-over summaries show the winner first, or Team 1 first when tied.
        assertEquals(
            "Viscous Coupling 5\nAnimal 3",
            GamePrompt.GameOver(
                gameOverState.copy(
                    teamOne = gameOverState.teamOne.copy(score = 5),
                    teamTwo = gameOverState.teamTwo.copy(score = 3),
                )
            ).formatMessage(),
        )
        assertEquals(
            "Alpha 4\nBeta 4",
            GamePrompt.GameOver(
                gameOverState.copy(
                    teamOne = gameOverState.teamOne.copy(name = "Alpha", score = 4),
                    teamTwo = gameOverState.teamTwo.copy(name = "Beta", score = 4),
                )
            ).formatMessage(),
        )
        assertEquals(
            "Alpha 4\nBeta 4",
            GamePrompt.GameOver(
                gameOverState.copy(
                    teamOne = gameOverState.teamOne.copy(name = "Beta", score = 4),
                    teamTwo = gameOverState.teamTwo.copy(name = "Alpha", score = 4),
                )
            ).formatMessage(),
        )

        val invalidCardEventException = assertThrows(IllegalArgumentException::class.java) {
            GameEvent.TeamCardsChanged(
                state = state,
                team = VC,
                teamCardTotal = 1,
                playerCardType = PlayerCardEventType.YELLOW,
            )
        }
        assertEquals(
            "Failed requirement.",
            invalidCardEventException.message,
        )
    }

    /// Verify cap cue timing for half, soft, and hard caps around their scheduled wall-clock instants.
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
