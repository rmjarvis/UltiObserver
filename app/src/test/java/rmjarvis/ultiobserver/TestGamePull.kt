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

class TestGamePull : GameModelTestFixtures() {
    // Test pull infractions from the observer-facing actions.
    // Offsides belongs to the pulling team; false start belongs to the receiving team.
    @Test
    fun pullInfractions() {
        val VC = TeamId.TEAM_ONE
        val ANIMAL = TeamId.TEAM_TWO

        // Start from a pull sequence with Viscous Coupling pulling to Animal.
        var state = standardLiveGameState()
        assertEquals(LivePhase.BETWEEN_POINTS, state.phase)
        assertEquals(VC, state.pullingTeam)
        assertEquals(0, state.teamOne.offsides)
        assertEquals(0, state.teamOne.falseStarts)
        assertEquals(0, state.teamTwo.offsides)
        assertEquals(0, state.teamTwo.falseStarts)
        assertFalse(state.pullSequenceOffsidesRecorded)
        assertFalse(state.pullSequenceFalseStartRecorded)

        // Record offsides and verify only the pulling team's offsides count increments.
        var pullInfractionResult = state.assessPullInfraction(VC)
        state = pullInfractionResult.state
        assertEquals(LivePhase.LIVE_POINT, state.phase)
        assertNull(state.countdown)
        assertEquals(1, state.teamOne.offsides)
        assertEquals(0, state.teamOne.falseStarts)
        assertEquals(0, state.teamTwo.offsides)
        assertEquals(0, state.teamTwo.falseStarts)
        assertTrue(state.pullSequenceOffsidesRecorded)
        assertFalse(state.pullSequenceFalseStartRecorded)
        assertEquals("Offsides on Viscous Coupling.", state.lastEvent)
        assertEquals("Undo Offsides on Viscous Coupling", state.undoEntry?.label)

        // Verify the first pull-violation message sends play to the brick mark.
        assertEquals("Start at brick mark", pullInfractionResult.message())
        assertFalse(pullInfractionResult.event!!.needsMisconductChoice())

        // Verify the same pull sequence cannot record a second offsides for the same team.
        pullInfractionResult = state.assessPullInfraction(VC)
        assertEquals(state, pullInfractionResult.state)
        assertNull(pullInfractionResult.message())

        // Mirror the offsides pathway for a pull where Animal is the pulling team.
        state = standardLiveGameState(pullingTeam = ANIMAL)
        pullInfractionResult = state.assessPullInfraction(ANIMAL)
        state = pullInfractionResult.state
        assertEquals(LivePhase.LIVE_POINT, state.phase)
        assertEquals(0, state.teamOne.offsides)
        assertEquals(1, state.teamTwo.offsides)
        assertEquals("Offsides on Animal.", state.lastEvent)
        assertEquals("Start at brick mark", pullInfractionResult.message())

        // In a fresh pull sequence, record false start and verify only the receiving team's count increments.
        state = standardLiveGameState()
        pullInfractionResult = state.assessPullInfraction(ANIMAL)
        state = pullInfractionResult.state
        assertEquals(LivePhase.BETWEEN_POINTS, state.phase)
        assertNotNull(state.countdown)
        assertEquals(0, state.teamOne.offsides)
        assertEquals(0, state.teamOne.falseStarts)
        assertEquals(0, state.teamTwo.offsides)
        assertEquals(1, state.teamTwo.falseStarts)
        assertFalse(state.pullSequenceOffsidesRecorded)
        assertTrue(state.pullSequenceFalseStartRecorded)
        assertEquals("False start on Animal.", state.lastEvent)
        assertEquals("Undo False Start on Animal", state.undoEntry?.label)

        // Verify false-start guidance says the defense gets to set up.
        assertEquals("Defense gets to set up.", pullInfractionResult.message())

        // The same pull sequence cannot record a second false start.
        pullInfractionResult = state.assessPullInfraction(ANIMAL)
        assertEquals(state, pullInfractionResult.state)
        assertNull(pullInfractionResult.message())

        // If the receiving team is late, the first time violation is a warning and shortened pull reset.
        state = standardLiveGameState()
        val firstViolationMoment = state.countdown!!.targetEpoch
        assertFalse(state.hasExpiredPullActions())
        var timeViolationDecisionState = state.expiredPullDecisionState()
        assertTrue(timeViolationDecisionState.hasExpiredPullActions())
        var timeViolationResult = timeViolationDecisionState.assessTimeViolation(ANIMAL, firstViolationMoment)
        val warningEvent = timeViolationResult.event as GameEvent.TimeViolationRecorded
        var timeViolationState = timeViolationResult.state
        assertEquals(ANIMAL, warningEvent.team)
        assertEquals(TimeViolationOutcome.WARNING, warningEvent.outcome)
        assertTrue(timeViolationState.teamTwo.timeViolationWarningIssued)
        assertFalse(timeViolationState.teamOne.timeViolationWarningIssued)
        assertEquals(CountdownKind.PULL_RESET, timeViolationState.countdown?.kind)
        assertEquals("Signal in", timeViolationState.countdown?.label)
        assertEquals(30, timeViolationState.countdown?.durationSeconds)
        assertEquals(firstViolationMoment + 30_000L, timeViolationState.countdown?.targetEpoch)
        assertEquals("Animal now has 30 seconds to signal readiness.", timeViolationResult.message())
        assertUndoRestores(timeViolationDecisionState, timeViolationState)

        // The next offense time violation by that team charges a 70-second timeout clock.
        val secondViolationMoment = timeViolationState.countdown!!.targetEpoch
        timeViolationDecisionState = timeViolationState.expiredPullDecisionState()
        timeViolationResult = timeViolationDecisionState.assessTimeViolation(ANIMAL, secondViolationMoment)
        val timeoutEvent = timeViolationResult.event as GameEvent.TimeViolationRecorded
        timeViolationState = timeViolationResult.state
        assertEquals(ANIMAL, timeoutEvent.team)
        assertEquals(TimeViolationOutcome.TIMEOUT, timeoutEvent.outcome)
        assertEquals(1, timeViolationState.teamTwo.timeoutsUsedThisHalf)
        assertEquals(CountdownKind.BETWEEN_POINTS, timeViolationState.countdown?.kind)
        assertEquals("Signal in", timeViolationState.countdown?.label)
        assertEquals(70, timeViolationState.countdown?.durationSeconds)
        assertEquals(secondViolationMoment + 70_000L, timeViolationState.countdown?.targetEpoch)
        assertEquals("Timeout charged to Animal. Reset pull timing.", timeViolationResult.message())

        // A defense time violation warning also gets a 30-second reset, and the next timeout clock is 90 seconds.
        state = standardLiveGameState(pullingFromEnd = FieldEnd.NEAR)
        assertEquals("Pull in", state.countdown?.label)
        timeViolationResult = state.expiredPullDecisionState().assessTimeViolation(VC, state.countdown!!.targetEpoch)
        timeViolationState = timeViolationResult.state
        val defenseWarningEvent = timeViolationResult.event as GameEvent.TimeViolationRecorded
        assertEquals(VC, defenseWarningEvent.team)
        assertEquals(TimeViolationOutcome.WARNING, defenseWarningEvent.outcome)
        assertEquals(CountdownKind.PULL_RESET, timeViolationState.countdown?.kind)
        assertEquals("Pull in", timeViolationState.countdown?.label)
        assertEquals(30, timeViolationState.countdown?.durationSeconds)
        val defenseSecondViolationMoment = timeViolationState.countdown!!.targetEpoch
        timeViolationResult = timeViolationState.expiredPullDecisionState().assessTimeViolation(VC, defenseSecondViolationMoment)
        timeViolationState = timeViolationResult.state
        val defenseTimeoutEvent = timeViolationResult.event as GameEvent.TimeViolationRecorded
        assertEquals(TimeViolationOutcome.TIMEOUT, defenseTimeoutEvent.outcome)
        assertEquals(1, timeViolationState.teamOne.timeoutsUsedThisHalf)
        assertEquals("Pull in", timeViolationState.countdown?.label)
        assertEquals(90, timeViolationState.countdown?.durationSeconds)
        assertEquals(defenseSecondViolationMoment + 90_000L, timeViolationState.countdown?.targetEpoch)

        // A far-side warning records the team's warning without starting a countdown for this observer.
        state = standardLiveGameState()
        val farPullingWarningMoment = state.countdown!!.targetEpoch
        timeViolationResult = state.expiredPullDecisionState().assessTimeViolation(VC, farPullingWarningMoment)
        timeViolationState = timeViolationResult.state
        assertEquals(VC, (timeViolationResult.event as GameEvent.TimeViolationRecorded).team)
        assertEquals(TimeViolationOutcome.WARNING, timeViolationResult.event.outcome)
        assertTrue(timeViolationState.teamOne.timeViolationWarningIssued)
        assertNull(timeViolationState.countdown)
        assertFalse(timeViolationState.hasExpiredPullActions())
        assertEquals("Viscous Coupling now has 30 seconds to pull.", timeViolationResult.message())

        state = standardLiveGameState(pullingFromEnd = FieldEnd.NEAR)
        val farReceivingWarningMoment = state.countdown!!.targetEpoch
        timeViolationResult = state.expiredPullDecisionState().assessTimeViolation(ANIMAL, farReceivingWarningMoment)
        timeViolationState = timeViolationResult.state
        assertEquals(ANIMAL, (timeViolationResult.event as GameEvent.TimeViolationRecorded).team)
        assertEquals(TimeViolationOutcome.WARNING, timeViolationResult.event.outcome)
        assertTrue(timeViolationState.teamTwo.timeViolationWarningIssued)
        assertNull(timeViolationState.countdown)
        assertEquals("Animal now has 30 seconds to signal readiness.", timeViolationResult.message())

        // A far-side timeout still starts the countdown for this observer's side.
        state = standardLiveGameState()
        state = state.copy(teamOne = state.teamOne.copy(timeViolationWarningIssued = true))
        timeViolationResult = state.expiredPullDecisionState().assessTimeViolation(VC, state.countdown!!.targetEpoch)
        timeViolationState = timeViolationResult.state
        assertEquals(TimeViolationOutcome.TIMEOUT, (timeViolationResult.event as GameEvent.TimeViolationRecorded).outcome)
        assertEquals(1, timeViolationState.teamOne.timeoutsUsedThisHalf)
        assertEquals("Signal in", timeViolationState.countdown?.label)
        assertEquals(70, timeViolationState.countdown?.durationSeconds)

        // If the receiving team has no timeout left after its warning, the point starts with no pull.
        val noTimeoutRules = GameRules(
            gameTo = 5,
            useHalfCap = false,
            useSoftCap = false,
            useHardCap = false,
            timeoutsPerHalf = 0,
        )
        state = standardLiveGameState(rules = noTimeoutRules)
        state = state.copy(teamTwo = state.teamTwo.copy(timeViolationWarningIssued = true))
        timeViolationResult = state.expiredPullDecisionState().assessTimeViolation(ANIMAL, state.countdown!!.targetEpoch)
        val receivingNoTimeoutEvent = timeViolationResult.event as GameEvent.TimeViolationRecorded
        timeViolationState = timeViolationResult.state
        assertEquals(ANIMAL, receivingNoTimeoutEvent.team)
        assertEquals(TimeViolationOutcome.NO_TIMEOUT, receivingNoTimeoutEvent.outcome)
        assertEquals(LivePhase.BETWEEN_POINTS, timeViolationState.phase)
        assertNull(timeViolationState.countdown)
        assertTrue(timeViolationState.pullSkippedForCurrentPoint)
        assertEquals(
            "No timeouts remaining. No pull. Receiving team starts at midpoint of defending end zone.",
            timeViolationResult.message(),
        )

        // A pulling-team time violation with no timeout left sends the receiving team to midfield.
        state = standardLiveGameState(
            rules = noTimeoutRules,
            pullingFromEnd = FieldEnd.NEAR,
        )
        state = state.copy(teamOne = state.teamOne.copy(timeViolationWarningIssued = true))
        assertEquals("Pull in", state.countdown?.label)
        timeViolationResult = state.expiredPullDecisionState().assessTimeViolation(VC, state.countdown!!.targetEpoch)
        val pullingNoTimeoutEvent = timeViolationResult.event as GameEvent.TimeViolationRecorded
        timeViolationState = timeViolationResult.state
        assertEquals(VC, pullingNoTimeoutEvent.team)
        assertEquals(TimeViolationOutcome.NO_TIMEOUT, pullingNoTimeoutEvent.outcome)
        assertNull(timeViolationState.countdown)
        assertTrue(timeViolationState.pullSkippedForCurrentPoint)
        assertEquals(
            "No timeouts remaining. No pull. Receiving team starts at midfield.",
            timeViolationResult.message(),
        )

        // Restarting the expired pull countdown restores the ordinary countdown and clears the action surface.
        state = standardLiveGameState()
        timeViolationDecisionState = state.expiredPullDecisionState()
        timeViolationState = timeViolationDecisionState.restartPullCountdown(state.countdown!!.targetEpoch)
        assertEquals(CountdownKind.BETWEEN_POINTS, timeViolationState.countdown?.kind)
        assertEquals("Signal in", timeViolationState.countdown?.label)
        assertEquals(60, timeViolationState.countdown?.durationSeconds)
        assertFalse(timeViolationState.hasExpiredPullActions())
        assertEquals("Undo Restart Pull Countdown", timeViolationState.undoEntry?.label)

        // Record offsides and false start on the same pull and verify both counts and both consequences apply.
        state = standardLiveGameState()
        val offsidesResult = state.assessPullInfraction(VC)
        state = offsidesResult.state
        val falseStartResult = state.assessPullInfraction(ANIMAL)
        state = falseStartResult.state
        assertEquals(1, state.teamOne.offsides)
        assertEquals(0, state.teamOne.falseStarts)
        assertEquals(0, state.teamTwo.offsides)
        assertEquals(1, state.teamTwo.falseStarts)
        assertTrue(state.pullSequenceOffsidesRecorded)
        assertTrue(state.pullSequenceFalseStartRecorded)
        assertEquals("Start at brick mark", offsidesResult.message())
        assertEquals("Defense gets to set up.", falseStartResult.message())

        // Score the point and verify pull-sequence infraction locks reset for the next pull.
        state = recordGoalFromCurrentStateAt(state, VC, LocalTime.of(12, 5))
        assertEquals(LivePhase.BETWEEN_POINTS, state.phase)
        assertEquals(VC, state.pullingTeam)
        assertEquals(1, state.teamOne.score)
        assertEquals(0, state.teamTwo.score)
        assertEquals(1, state.teamOne.offsides)
        assertEquals(1, state.teamTwo.falseStarts)
        assertFalse(state.pullSequenceOffsidesRecorded)
        assertFalse(state.pullSequenceFalseStartRecorded)
        assertEquals("Pull in", state.countdown?.label)

        // Build a later pull where Viscous Coupling already has a violation and verify the guidance changes to midfield.
        pullInfractionResult = state.assessPullInfraction(VC)
        state = pullInfractionResult.state
        assertEquals(2, state.teamOne.offsides)
        assertEquals("Start at midfield", pullInfractionResult.message())

        // A previous false start by Viscous Coupling also stacks with a later Viscous Coupling offsides.
        state = standardLiveGameState(pullingTeam = ANIMAL)
        state = state.assessPullInfraction(VC).state
        assertEquals(0, state.teamOne.offsides)
        assertEquals(1, state.teamOne.falseStarts)
        assertEquals(0, state.teamTwo.offsides)
        assertEquals(0, state.teamTwo.falseStarts)
        state = recordGoalFromCurrentStateAt(state, VC, LocalTime.of(12, 10))
        assertEquals(VC, state.pullingTeam)
        pullInfractionResult = state.assessPullInfraction(VC)
        state = pullInfractionResult.state
        assertEquals(1, state.teamOne.offsides)
        assertEquals(1, state.teamOne.falseStarts)
        assertEquals("Start at midfield", pullInfractionResult.message())

        // Manually adjust pull infractions and verify values are clamped and undo-backed.
        state = state.adjustPullInfractions(
            teamOneOffsides = -1,
            teamOneFalseStarts = 2,
            teamTwoOffsides = 3,
            teamTwoFalseStarts = -4,
        )
        assertEquals(0, state.teamOne.offsides)
        assertEquals(2, state.teamOne.falseStarts)
        assertEquals(3, state.teamTwo.offsides)
        assertEquals(0, state.teamTwo.falseStarts)
        assertEquals("Pull infractions adjusted.", state.lastEvent)
        assertEquals("Undo Pull Infraction Adjustment", state.undoEntry?.label)
    }
}
