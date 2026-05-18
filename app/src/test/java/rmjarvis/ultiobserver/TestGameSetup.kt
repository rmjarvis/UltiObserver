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

class TestGameSetup : GameModelTestFixtures() {
    /**
     * Test setup conversion and applying setup edits to a live game.
     * The setup form is public UI, but the model owns how edits reshape live state.
     */
    @Test
    fun setupRoundTripAndMidgameUpdates() {
        val VC = TeamId.TEAM_ONE
        val ANIMAL = TeamId.TEAM_TWO
        val priorCards = listOf(
            PlayerCardRecord(VC, "17", priorYellows = 1, priorReds = 0),
            PlayerCardRecord(ANIMAL, "23", priorYellows = 0, priorReds = 1),
        )

        // Create a live game from setup and verify the setup form can be reconstructed from live state.
        val setup = standardGameSetup(
            startTime = LocalTime.of(8, 30),
            rules = GameRules(
                gameTo = 13,
                halftimeMinutes = 8,
                halfCapMinutes = 40,
                softCapMinutes = 80,
                hardCapMinutes = 95,
                timeoutsPerHalf = 1,
                hasFloaterTimeout = true,
            ),
            pullingTeam = VC,
            pullingFromEnd = FieldEnd.FAR,
        ).copy(
            teamOne = TeamSetup("Viscous Coupling", TeamColorChoice.GREEN),
            teamTwo = TeamSetup("Animal", TeamColorChoice.YELLOW),
            priorCards = priorCards,
        )
        var state = createLiveGameState(setup)
        assertEquals(setup, state.toSetupState())
        assertEquals(VC, state.openingPullingTeam)
        assertEquals(FieldEnd.FAR, state.openingPullingFromEnd)
        assertEquals(VC, state.pullingTeam)
        assertEquals(FieldEnd.FAR, state.pullingFromEnd)
        assertEquals(VC, state.nearAttackingTeam)
        assertEquals(priorCards, state.priorCards)

        // Edit setup before the first point and verify opening pull changes resync current pull and field state.
        val editedBeforePlay = setup.copy(
            startTime = LocalTime.of(8, 45),
            rules = setup.rules.copy(gameTo = 15, timeoutsPerHalf = 2),
            teamOne = TeamSetup("VC", TeamColorChoice.WHITE),
            teamTwo = TeamSetup("Animal Ultimate", TeamColorChoice.RED),
            priorCards = priorCards + PlayerCardRecord(VC, "8", priorYellows = 2, priorReds = 0),
            pullingTeam = ANIMAL,
            pullingFromEnd = FieldEnd.NEAR,
        )
        val beforeSetupEditBeforePlay = state
        state = applySetupToLiveGame(state, editedBeforePlay, 10_000L)
        assertEquals(LocalTime.of(8, 45), state.startTime)
        assertEquals(15, state.rules.gameTo)
        assertEquals(2, state.rules.timeoutsPerHalf)
        assertEquals("VC", state.teamOne.name)
        assertEquals(TeamColorChoice.WHITE, state.teamOne.color)
        assertEquals("Animal Ultimate", state.teamTwo.name)
        assertEquals(TeamColorChoice.RED, state.teamTwo.color)
        assertEquals(editedBeforePlay.priorCards, state.priorCards)
        assertEquals(ANIMAL, state.openingPullingTeam)
        assertEquals(FieldEnd.NEAR, state.openingPullingFromEnd)
        assertEquals(ANIMAL, state.pullingTeam)
        assertEquals(FieldEnd.NEAR, state.pullingFromEnd)
        assertEquals(VC, state.nearAttackingTeam)
        assertEquals("Pull sequence started.", state.lastEvent)
        assertEquals(CountdownKind.BETWEEN_POINTS, state.countdown?.kind)
        assertEquals("Pull in", state.countdown?.label)
        assertEquals(80, state.countdown?.durationSeconds)
        assertEquals(90_000L, state.countdown?.targetEpoch)
        assertEquals("Undo Update Game Setup", state.undoEntry?.label)
        assertEquals(beforeSetupEditBeforePlay, state.undoEntry?.previous)

        // The raw live-state defaults represent a pregame state before the setup-to-live transition.
        val pregameState = LiveGameState(
            startDate = setup.startDate,
            startTime = setup.startTime,
            timeZone = setup.timeZone,
            startEpoch = 0L,
            rules = setup.rules,
            teamOne = TeamLiveState("Team 1", TeamColorChoice.WHITE),
            teamTwo = TeamLiveState("Team 2", TeamColorChoice.BLUE),
            priorCards = emptyList(),
            nearAttackingTeam = VC,
            pullingTeam = VC,
            pullingFromEnd = FieldEnd.FAR,
            openingPullingTeam = VC,
            openingPullingFromEnd = FieldEnd.FAR,
        )
        assertEquals(LivePhase.PRE_GAME, pregameState.phase)
        assertNull(pregameState.countdown)

        // Edit setup after play has begun and verify opening pull metadata changes without rewriting current field state.
        state = state.beginLivePoint()
        state = state.assessYellowCard(VC, "17").state
        state = recordGoalAt(state, VC, LocalTime.of(8, 50))
        val fieldStateAfterGoal = state

        val editedAfterPlay = editedBeforePlay.copy(
            startTime = LocalTime.of(9, 0),
            rules = editedBeforePlay.rules.copy(gameTo = 17, hasFloaterTimeout = false),
            teamOne = TeamSetup("Viscous", TeamColorChoice.BLACK),
            teamTwo = TeamSetup("Animal", TeamColorChoice.BLUE),
            priorCards = emptyList(),
            pullingTeam = VC,
            pullingFromEnd = FieldEnd.FAR,
        )
        val beforeSetupEditAfterPlay = state
        state = applySetupToLiveGame(state, editedAfterPlay, 200_000L)
        assertEquals(LocalTime.of(9, 0), state.startTime)
        assertEquals(17, state.rules.gameTo)
        assertFalse(state.rules.hasFloaterTimeout)
        assertEquals("Viscous", state.teamOne.name)
        assertEquals(TeamColorChoice.BLACK, state.teamOne.color)
        assertEquals("Animal", state.teamTwo.name)
        assertEquals(TeamColorChoice.BLUE, state.teamTwo.color)
        assertEquals(fieldStateAfterGoal.teamOne.score, state.teamOne.score)
        assertEquals(fieldStateAfterGoal.teamTwo.score, state.teamTwo.score)
        assertEquals(fieldStateAfterGoal.playerCards(VC), state.playerCards(VC))
        assertEquals(fieldStateAfterGoal.playerCards(ANIMAL), state.playerCards(ANIMAL))
        assertEquals(VC, state.openingPullingTeam)
        assertEquals(FieldEnd.FAR, state.openingPullingFromEnd)
        assertEquals(fieldStateAfterGoal.pullingTeam, state.pullingTeam)
        assertEquals(fieldStateAfterGoal.pullingFromEnd, state.pullingFromEnd)
        assertEquals(fieldStateAfterGoal.nearAttackingTeam, state.nearAttackingTeam)
        assertEquals(fieldStateAfterGoal.phase, state.phase)
        assertEquals(fieldStateAfterGoal.countdown, state.countdown)
        assertEquals(fieldStateAfterGoal.pendingCapOffer, state.pendingCapOffer)
        assertEquals(emptyList<PlayerCardRecord>(), state.priorCards)
        assertEquals("Undo Update Game Setup", state.undoEntry?.label)
        assertEquals(beforeSetupEditAfterPlay, state.undoEntry?.previous)

        // A game with only Team 2 on the scoreboard has still started, so setup edits
        // should preserve the current pull and field state rather than resyncing from opening pull settings.
        val animalScoredState = recordGoalAt(createLiveGameState(setup), ANIMAL, LocalTime.of(8, 40))
        val animalScoredUpdate = applySetupToLiveGame(
            animalScoredState,
            editedBeforePlay.copy(pullingTeam = VC, pullingFromEnd = FieldEnd.FAR),
            250_000L,
        )
        assertEquals(0, animalScoredUpdate.teamOne.score)
        assertEquals(1, animalScoredUpdate.teamTwo.score)
        assertEquals(animalScoredState.pullingTeam, animalScoredUpdate.pullingTeam)
        assertEquals(animalScoredState.pullingFromEnd, animalScoredUpdate.pullingFromEnd)
        assertEquals(animalScoredState.nearAttackingTeam, animalScoredUpdate.nearAttackingTeam)

        // Verify setup edits preserve pending cap prompts and do not restart an in-progress countdown.
        state = fieldStateAfterGoal.copy(pendingCapOffer = CapType.SOFT)
        val pendingCountdown = state.countdown
        state = applySetupToLiveGame(state, editedAfterPlay.copy(rules = editedAfterPlay.rules.copy(gameTo = 19)), 300_000L)
        assertEquals(CapType.SOFT, state.pendingCapOffer)
        assertEquals(pendingCountdown, state.countdown)
        assertEquals(19, state.rules.gameTo)
        assertEquals("Undo Update Game Setup", state.undoEntry?.label)

        // Blank team names are normalized to default display names when setup is applied.
        state = applySetupToLiveGame(
            state,
            editedAfterPlay.copy(teamOne = TeamSetup(""), teamTwo = TeamSetup("")),
            400_000L,
        )
        assertEquals("Team 1", state.teamOne.name)
        assertEquals("Team 2", state.teamTwo.name)
    }
}
