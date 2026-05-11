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
        assertEquals("Start at brick mark", eventMessage(pullInfractionResult))

        // Verify the same pull sequence cannot record a second offsides for the same team.
        pullInfractionResult = state.assessPullInfraction(VC)
        assertEquals(state, pullInfractionResult.state)
        assertNull(eventMessage(pullInfractionResult))

        // Mirror the offsides pathway for a pull where Animal is the pulling team.
        state = standardLiveGameState(pullingTeam = ANIMAL)
        pullInfractionResult = state.assessPullInfraction(ANIMAL)
        state = pullInfractionResult.state
        assertEquals(LivePhase.LIVE_POINT, state.phase)
        assertEquals(0, state.teamOne.offsides)
        assertEquals(1, state.teamTwo.offsides)
        assertEquals("Offsides on Animal.", state.lastEvent)
        assertEquals("Start at brick mark", eventMessage(pullInfractionResult))

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
        assertEquals("Defense gets to set up.", eventMessage(pullInfractionResult))

        // The same pull sequence cannot record a second false start.
        pullInfractionResult = state.assessPullInfraction(ANIMAL)
        assertEquals(state, pullInfractionResult.state)
        assertNull(eventMessage(pullInfractionResult))

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
        assertEquals("Start at brick mark", eventMessage(offsidesResult))
        assertEquals("Defense gets to set up.", eventMessage(falseStartResult))

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
        assertEquals("Start at midfield", eventMessage(pullInfractionResult))

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
        assertEquals("Start at midfield", eventMessage(pullInfractionResult))

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
