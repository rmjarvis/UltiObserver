package rmjarvis.ultiobserver

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class TestGameClock : GameModelTestFixtures() {
    // Test deterministic clock and countdown helpers that are public model surface.
    // These tests should pin time behavior without relying on the wall clock.
    @Test
    fun clockAndCountdownDisplays() {
        val VC = TeamId.TEAM_ONE

        // Verify simple model defaults and labels used by setup display surfaces.
        assertEquals("Pink", TeamColorChoice.PINK.label)
        val defaultTeamSetup = TeamSetup()
        assertEquals("", defaultTeamSetup.name)
        assertEquals(TeamColorChoice.WHITE, defaultTeamSetup.color)
        val timeoutCountdownWithDefaultTarget = CountdownState(
            kind = CountdownKind.TIME_OUT,
            label = "Offense set in",
            durationSeconds = 70,
            targetEpoch = 70_000L,
        )
        assertNull(timeoutCountdownWithDefaultTarget.betweenPointsTarget)

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
        assertEquals(CapStatus("Half cap", Duration.ofMinutes(30)), state.computeNextCapStatus(timestampAfterStart(state, 15)))
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

        val openingPullCountdown = buildBetweenPointsCountdown(
            pullingFromEnd = FieldEnd.NEAR,
            sequenceStart = 1_000L,
            kind = CountdownKind.OPENING_PULL,
        )
        assertEquals(40, openingPullCountdown.durationSeconds)
        assertEquals(TimingCueId.PULLING_TWENTY_TO_PULL, openingPullCountdown.nextTimingCue(1_000L)?.id)
        assertEquals(Duration.ofSeconds(20), openingPullCountdown.nextTimingCue(1_000L)?.remaining)

        // Verify timeout cues stop at offense freeze, with the app's last reminder at countdown-from-five.
        assertEquals(TimingCueId.TIMEOUT_CLEAR_FIELD, timeoutCountdownWithDefaultTarget.nextTimingCue(40_000L)?.id)
        assertEquals(TimingCueId.TIMEOUT_COUNTDOWN_FROM_FIVE, timeoutCountdownWithDefaultTarget.nextTimingCue(65_000L)?.id)
        assertEquals(TimingCueId.TIMEOUT_OFFENSE_FREEZE, timeoutCountdownWithDefaultTarget.dueTimingCue(70_000L)?.id)

        // Verify manual countdown adjustments move only the target time and format positive/negative changes.
        state = standardLiveGameState()
        val originalCountdown = state.countdown!!
        state = state.addTimeToCountdown(65)
        assertEquals(originalCountdown.targetEpoch + 65_000L, state.countdown?.targetEpoch)
        assertEquals(originalCountdown.durationSeconds, state.countdown?.durationSeconds)
        assertEquals("Adjusted timer by 1:05.", state.lastEvent)

        state = state.addTimeToCountdown(-5)
        assertEquals(originalCountdown.targetEpoch + 60_000L, state.countdown?.targetEpoch)
        assertEquals(originalCountdown.durationSeconds, state.countdown?.durationSeconds)
        assertEquals("Adjusted timer by -0:05.", state.lastEvent)

        val livePointWithoutCountdown = state.beginLivePoint()
        assertEquals(livePointWithoutCountdown, livePointWithoutCountdown.addTimeToCountdown(5))

        // Countdown target swapping is a no-op for non-between-points countdowns and fails on malformed ones.
        val inPointTimeoutCountdown = livePointWithoutCountdown.assessTimeout(VC, 600_000L).state.countdown!!
        assertEquals(inPointTimeoutCountdown, inPointTimeoutCountdown.swapOD())
        val malformedCountdown = CountdownState(
            kind = CountdownKind.BETWEEN_POINTS,
            label = "Signal in",
            durationSeconds = 60,
            targetEpoch = 60_000L,
        )
        val malformedCountdownException = assertThrows(IllegalStateException::class.java) {
            malformedCountdown.swapOD()
        }
        assertEquals("Between-points countdown is missing its target side.", malformedCountdownException.message)

        // A countdown kind that does not match the phase is an impossible model state, so fail loudly.
        val mismatchedCountdownState = standardLiveGameState().copy(phase = LivePhase.LIVE_POINT)
        val mismatchException = assertThrows(IllegalStateException::class.java) {
            mismatchedCountdownState.advanceGameClock(mismatchedCountdownState.countdown!!.targetEpoch)
        }
        assertEquals(
            "Countdown OPENING_PULL is not valid while game phase is LIVE_POINT.",
            mismatchException.message,
        )
        val betweenPointsWithTimeoutCountdown = standardLiveGameState().copy(
            countdown = inPointTimeoutCountdown,
        )
        val betweenPointsMismatchException = assertThrows(IllegalStateException::class.java) {
            betweenPointsWithTimeoutCountdown.advanceGameClock(inPointTimeoutCountdown.targetEpoch)
        }
        assertEquals(
            "Countdown TIME_OUT is not valid while game phase is BETWEEN_POINTS.",
            betweenPointsMismatchException.message,
        )
        val halftimeWithBetweenPointsCountdown = standardLiveGameState().copy(
            phase = LivePhase.HALFTIME,
        )
        val halftimeMismatchException = assertThrows(IllegalStateException::class.java) {
            halftimeWithBetweenPointsCountdown.advanceGameClock(halftimeWithBetweenPointsCountdown.countdown!!.targetEpoch)
        }
        assertEquals(
            "Countdown OPENING_PULL is not valid while game phase is HALFTIME.",
            halftimeMismatchException.message,
        )

        // Verify countdown helpers advance automatically at the exact target time, not before.
        state = standardLiveGameState()
        val betweenPointsCountdown = state.countdown!!
        assertEquals(state, state.advanceGameClock(betweenPointsCountdown.targetEpoch - 1L))
        state = state.advanceGameClock(betweenPointsCountdown.targetEpoch)
        assertEquals(LivePhase.LIVE_POINT, state.phase)
        assertNull(state.countdown)

        state = state.assessTimeout(VC, 500_000L).state
        val timeoutCountdown = state.countdown!!
        assertEquals(state, state.advanceGameClock(timeoutCountdown.targetEpoch - 1L))
        state = state.advanceGameClock(timeoutCountdown.targetEpoch)
        assertEquals(LivePhase.LIVE_POINT, state.phase)
        assertNull(state.countdown)
    }
}
