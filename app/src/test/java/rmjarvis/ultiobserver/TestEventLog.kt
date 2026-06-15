package rmjarvis.ultiobserver

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

/// Tests for persisted event-log entries and compact display formatting.
class TestEventLog : GameDomainTestFixtures() {
    /// Verify first-pull entries use nominal start time unless the first point starts early.
    @Test
    fun firstPullUsesNominalStartUnlessStartedEarly() {
        val ANIMAL = TeamId.TEAM_TWO
        var state = standardLiveGameState(
            startTime = LocalTime.of(12, 0),
            pullingTeam = ANIMAL,
            pullingFromEnd = FieldEnd.NEAR,
        )

        state = state.beginLivePoint(timestampAt(state, LocalTime.of(12, 3)))
        assertEquals(
            listOf("12:00  First pull by Animal"),
            state.formatEventLogLines(),
        )

        state = standardLiveGameState(
            startTime = LocalTime.of(12, 0),
            pullingTeam = ANIMAL,
            pullingFromEnd = FieldEnd.NEAR,
        )
        state = state.beginLivePoint(timestampAt(state, LocalTime.of(11, 58)))
        assertEquals(
            listOf("11:58  First pull by Animal"),
            state.formatEventLogLines(),
        )
    }

    /// Verify normal game actions append compact event-log entries in game-local time.
    @Test
    fun significantGameEventsAreLogged() {
        val VC = TeamId.TEAM_ONE
        val ANIMAL = TeamId.TEAM_TWO
        var state = standardLiveGameState(
            startTime = LocalTime.of(12, 0),
            rules = GameRules(
                gameTo = 5,
                useHalfCap = false,
                useSoftCap = false,
                useHardCap = false,
                timeoutsPerHalf = 1,
            ),
            pullingTeam = ANIMAL,
        )

        state = state.beginLivePoint(timestampAt(state, LocalTime.of(12, 0)))
        state = state.assessBlueCard(VC, timestampAt(state, LocalTime.of(12, 2))).state
        state = state.assessTimeout(VC, timestampAt(state, LocalTime.of(12, 3))).state.continueLivePoint()
        state = state.assessYellowCard(ANIMAL, "23", timestampAt(state, LocalTime.of(12, 4))).state
        state = state.assessYellowCard(ANIMAL, "23", timestampAt(state, LocalTime.of(12, 4))).state
        state = state.assessTechnicalFoul(VC, timestampAt(state, LocalTime.of(12, 5))).state
        state = recordGoalAt(state, ANIMAL, LocalTime.of(12, 7))
        state = state.recordOffsides(timestampAt(state, LocalTime.of(12, 8)))
        state = state.recordFalseStart(timestampAt(state, LocalTime.of(12, 9)))
        state = state.startPullSequence(timestampAt(state, LocalTime.of(12, 10)))
        state = state.expiredPullDecisionState()
            .assessTimeViolation(ANIMAL, timestampAt(state, LocalTime.of(12, 11))).state
        state = state.expiredPullDecisionState()
            .assessTimeViolation(ANIMAL, timestampAt(state, LocalTime.of(12, 12))).state
        state = state.expiredPullDecisionState()
            .assessTimeViolation(ANIMAL, timestampAt(state, LocalTime.of(12, 13))).state
        state = startHalftimeNowAt(state, LocalTime.of(12, 14))
        state = endGameNowAt(state, LocalTime.of(12, 15))

        assertEquals(
            listOf(
                "12:00  First pull by Animal",
                "12:02  Blue card on Viscous Coupling",
                "12:03  Timeout by Viscous Coupling",
                "12:04  Yellow card on Animal #23",
                "12:04  Yellow card on Animal #23",
                "12:05  Technical foul on Viscous Coupling",
                "12:07  Animal Goal",
                "12:08  Offsides on Animal",
                "12:09  False start on Viscous Coupling",
                "12:11  Time violation on Animal warning",
                "12:12  Time violation on Animal, timeout charged",
                "12:13  Time violation on Animal, no timeout remaining",
                "12:14  Halftime",
                "12:15  Game over",
            ),
            state.formatEventLogLines(),
        )
    }

    /// Verify manual corrections log the meaningful before/after deltas.
    @Test
    fun manualCorrectionsLogDeltas() {
        val VC = TeamId.TEAM_ONE
        val ANIMAL = TeamId.TEAM_TWO
        var state = standardLiveGameState(startTime = LocalTime.of(12, 0))
        state = state.assessYellowCard(ANIMAL, "17", timestampAt(state, LocalTime.of(12, 1))).state
        state = state.assessTechnicalFoul(VC, timestampAt(state, LocalTime.of(12, 1))).state
        state = state.adjustPullInfractions(
            teamOneOffsides = 0,
            teamOneFalseStarts = 1,
            teamTwoOffsides = 1,
            teamTwoFalseStarts = 0,
            now = timestampAt(state, LocalTime.of(12, 2)),
        )
        state = state.adjustCardsAndTf(
            teamOneBlues = 1,
            teamOneTechnicalFouls = 0,
            teamTwoBlues = 0,
            teamTwoTechnicalFouls = 0,
            teamOnePlayerCards = listOf(InGamePlayerCardRecord("11", reds = 1)),
            teamTwoPlayerCards = emptyList(),
            now = timestampAt(state, LocalTime.of(12, 3)),
        )
        state = state.adjustScore(
            teamOneScore = 3,
            teamTwoScore = 5,
            now = timestampAt(state, LocalTime.of(12, 4)),
        )
        state = state.adjustTimeouts(
            teamOneTimeoutsUsed = 1,
            teamTwoTimeoutsUsed = 0,
            now = timestampAt(state, LocalTime.of(12, 5)),
        )
        state = state.adjustTimeouts(
            teamOneTimeoutsUsed = 0,
            teamTwoTimeoutsUsed = 0,
            now = timestampAt(state, LocalTime.of(12, 6)),
        )
        state = state.adjustPullInfractions(
            teamOneOffsides = 0,
            teamOneFalseStarts = 0,
            teamTwoOffsides = 1,
            teamTwoFalseStarts = 0,
            now = timestampAt(state, LocalTime.of(12, 7)),
        )

        assertEquals(
            listOf(
                "12:01  Yellow card on Animal #17",
                "12:01  Technical foul on Viscous Coupling",
                "12:02  Adjusted Viscous Coupling false starts +1",
                "12:02  Adjusted Animal offsides +1",
                "12:03  Added blue card on Viscous Coupling",
                "12:03  Adjusted Viscous Coupling technical fouls -1",
                "12:03  Added red card on Viscous Coupling #11",
                "12:03  Removed yellow card on Animal #17",
                "12:04  Adjusted score: Viscous Coupling 3 - Animal 5",
                "12:05  Adjusted Viscous Coupling timeouts +1",
                "12:06  Adjusted Viscous Coupling timeouts -1",
                "12:07  Adjusted Viscous Coupling false starts -1",
            ),
            state.formatEventLogLines(),
        )
        assertEquals(VC, state.eventLog.last().team)
    }

    /// Verify undo restores the previous event log along with the previous game state.
    @Test
    fun undoRestoresPreviousEventLog() {
        val ANIMAL = TeamId.TEAM_TWO
        val state = standardLiveGameState(startTime = LocalTime.of(12, 0))
        val afterCard = state.assessRedCard(ANIMAL, "8", timestampAt(state, LocalTime.of(12, 10))).state

        assertEquals(listOf("12:10  Red card on Animal #8"), afterCard.formatEventLogLines())
        assertEquals(emptyList<String>(), afterCard.undoLastAction().formatEventLogLines())
    }
}
