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

/// Tests for setup-state conversion and applying setup edits to live games.
class TestGameSetup : GameDomainTestFixtures() {
    /**
     * Test setup conversion and applying setup edits to a live game.
     * The setup form is public UI, but the model owns how edits reshape live state.
     */
    @Test
    fun setupRoundTripAndMidgameUpdates() {
        val VC = TeamId.TEAM_ONE
        val ANIMAL = TeamId.TEAM_TWO
        val teamOnePlayers = listOf(priorPlayerRecord("17", priorYellows = 1))
        val teamTwoPlayers = listOf(priorPlayerRecord("23", priorReds = 1))

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
            nearEndName = "Road",
            farEndName = "Trees",
            teamOne = TeamSetup(
                name = "Viscous Coupling",
                color = TeamColorChoice.CUSTOM,
                customColorArgb = 0xFF123456L,
                coaches = "Coach VC",
                fieldCaptains = "VC field captain",
                spiritCaptains = "VC spirit captain",
            ),
            teamTwo = TeamSetup(
                name = "Animal",
                color = TeamColorChoice.YELLOW,
                coaches = "Animal coaches",
                fieldCaptains = "Animal field captains",
                spiritCaptains = "Animal spirit captains",
            ),
            teamOnePlayers = teamOnePlayers,
            teamTwoPlayers = teamTwoPlayers,
            pullPromptTarget = PullPromptTarget.FAR,
        )
        var state = createLiveGameState(setup)
        assertEquals(setup, state.toSetupState())
        assertEquals("Road", state.nearEndName)
        assertEquals("Trees", state.farEndName)
        assertEquals(PullPromptTarget.FAR, state.pullPromptTarget)
        assertEquals(TeamColorChoice.CUSTOM, state.teamOne.color)
        assertEquals(0xFF123456L, state.teamOne.customColorArgb)
        assertNull(state.teamTwo.customColorArgb)
        assertEquals("Coach VC", state.teamOne.coaches)
        assertEquals("Animal field captains", state.teamTwo.fieldCaptains)
        assertEquals(VC, state.openingPullingTeam)
        assertEquals(FieldEnd.FAR, state.openingPullingFromEnd)
        assertEquals(VC, state.pullingTeam)
        assertEquals(FieldEnd.FAR, state.pullingFromEnd)
        assertEquals(VC, state.nearAttackingTeam)
        assertEquals(teamOnePlayers, state.teamOnePlayers)
        assertEquals(teamTwoPlayers, state.teamTwoPlayers)
        assertEquals("Pull in", state.countdown?.label)
        assertEquals(40, state.countdown?.durationSeconds)
        assertEquals(state.startEpoch + 40_000L, state.countdown?.targetEpoch)

        // Edit setup before the first point and verify opening pull changes resync current pull and field state.
        val editedBeforePlay = setup.copy(
            startTime = LocalTime.of(8, 45),
            nearEndName = "Parking",
            farEndName = "Scoreboard",
            rules = setup.rules.copy(gameTo = 15, timeoutsPerHalf = 2),
            teamOne = TeamSetup(
                name = "VC",
                color = TeamColorChoice.WHITE,
                customColorArgb = 0xFF123456L,
                coaches = "Coach edits",
                fieldCaptains = "Field captain edits",
                spiritCaptains = "Spirit captain edits",
            ),
            teamTwo = TeamSetup(
                name = "Animal Ultimate",
                color = TeamColorChoice.RED,
                coaches = "Other coach edits",
                fieldCaptains = "Other field captain edits",
                spiritCaptains = "Other spirit captain edits",
            ),
            teamOnePlayers = teamOnePlayers + priorPlayerRecord("8", priorYellows = 2),
            teamTwoPlayers = teamTwoPlayers,
            pullingTeam = ANIMAL,
            pullingFromEnd = FieldEnd.NEAR,
            pullPromptTarget = PullPromptTarget.BOTH,
        )
        val beforeSetupEditBeforePlay = state
        state = applySetupToLiveGame(state, editedBeforePlay, 10_000L)
        assertEquals(LocalTime.of(8, 45), state.startTime)
        assertEquals("Parking", state.nearEndName)
        assertEquals("Scoreboard", state.farEndName)
        assertEquals(PullPromptTarget.BOTH, state.pullPromptTarget)
        assertEquals(15, state.rules.gameTo)
        assertEquals(2, state.rules.timeoutsPerHalf)
        assertEquals("VC", state.teamOne.name)
        assertEquals(TeamColorChoice.WHITE, state.teamOne.color)
        assertEquals(0xFF123456L, state.teamOne.customColorArgb)
        assertEquals("Coach edits", state.teamOne.coaches)
        assertEquals("Field captain edits", state.teamOne.fieldCaptains)
        assertEquals("Spirit captain edits", state.teamOne.spiritCaptains)
        assertEquals("Animal Ultimate", state.teamTwo.name)
        assertEquals(TeamColorChoice.RED, state.teamTwo.color)
        assertEquals("Other coach edits", state.teamTwo.coaches)
        assertEquals("Other field captain edits", state.teamTwo.fieldCaptains)
        assertEquals("Other spirit captain edits", state.teamTwo.spiritCaptains)
        assertEquals(editedBeforePlay.teamOnePlayers, state.teamOnePlayers)
        assertEquals(editedBeforePlay.teamTwoPlayers, state.teamTwoPlayers)
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
        assertEquals("Undo Update game setup", state.undoEntry?.label)
        assertEquals(beforeSetupEditBeforePlay, state.undoEntry?.previous)

        // The raw live-state defaults represent a pregame state before the setup-to-live transition.
        val pregameState = GameState(
            startDate = setup.startDate,
            startTime = setup.startTime,
            timeZone = setup.timeZone,
            startEpoch = 0L,
            rules = setup.rules,
            teamOne = TeamLiveState("Team 1", TeamColorChoice.WHITE),
            teamTwo = TeamLiveState("Team 2", TeamColorChoice.BLUE),
            teamOnePlayers = emptyList(),
            teamTwoPlayers = emptyList(),
            nearAttackingTeam = VC,
            pullingTeam = VC,
            pullingFromEnd = FieldEnd.FAR,
            openingPullingTeam = VC,
            openingPullingFromEnd = FieldEnd.FAR,
        )
        assertEquals(GamePhase.PRE_GAME, pregameState.phase)
        assertNull(pregameState.countdown)

        // Edit setup after play has begun and verify opening pull metadata changes without rewriting current field state.
        state = state.beginLivePoint()
        state = state.assessYellowCard(VC, "17").state
        state = recordGoalAt(state, VC, LocalTime.of(8, 50))
        val fieldStateAfterGoal = state

        val editedAfterPlay = editedBeforePlay.copy(
            startTime = LocalTime.of(9, 0),
            nearEndName = "South",
            farEndName = "North",
            rules = editedBeforePlay.rules.copy(gameTo = 17, hasFloaterTimeout = false),
            teamOne = TeamSetup(
                name = "Viscous",
                color = TeamColorChoice.BLACK,
                coaches = "Post-play coach",
                fieldCaptains = "Post-play field captain",
                spiritCaptains = "Post-play spirit captain",
            ),
            teamTwo = TeamSetup(
                name = "Animal",
                color = TeamColorChoice.CUSTOM,
                customColorArgb = 0xFFABCDEFL,
                coaches = "Post-play other coach",
                fieldCaptains = "Post-play other field captain",
                spiritCaptains = "Post-play other spirit captain",
            ),
            teamOnePlayers = emptyList(),
            teamTwoPlayers = emptyList(),
            pullingTeam = VC,
            pullingFromEnd = FieldEnd.FAR,
            pullPromptTarget = PullPromptTarget.NEITHER,
        )
        val beforeSetupEditAfterPlay = state
        state = applySetupToLiveGame(state, editedAfterPlay, 200_000L)
        assertEquals(LocalTime.of(9, 0), state.startTime)
        assertEquals("South", state.nearEndName)
        assertEquals("North", state.farEndName)
        assertEquals(PullPromptTarget.NEITHER, state.pullPromptTarget)
        assertEquals(17, state.rules.gameTo)
        assertFalse(state.rules.hasFloaterTimeout)
        assertEquals("Viscous", state.teamOne.name)
        assertEquals(TeamColorChoice.BLACK, state.teamOne.color)
        assertEquals("Post-play coach", state.teamOne.coaches)
        assertEquals("Post-play field captain", state.teamOne.fieldCaptains)
        assertEquals("Post-play spirit captain", state.teamOne.spiritCaptains)
        assertEquals("Animal", state.teamTwo.name)
        assertEquals(TeamColorChoice.CUSTOM, state.teamTwo.color)
        assertEquals(0xFFABCDEFL, state.teamTwo.customColorArgb)
        assertEquals("Post-play other coach", state.teamTwo.coaches)
        assertEquals("Post-play other field captain", state.teamTwo.fieldCaptains)
        assertEquals("Post-play other spirit captain", state.teamTwo.spiritCaptains)
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
        assertEquals(fieldStateAfterGoal.countdown?.targetEpoch, state.countdown?.targetEpoch)
        assertEquals("Pull in", state.countdown?.label)
        assertEquals(80, state.countdown?.durationSeconds)
        assertNull(state.countdown?.nextTimingCue(state.countdown!!.targetEpoch - 20_000L))
        assertEquals(fieldStateAfterGoal.pendingCapOffer, state.pendingCapOffer)
        assertEquals(editedAfterPlay.teamOnePlayers, state.teamOnePlayers)
        assertEquals(editedAfterPlay.teamTwoPlayers, state.teamTwoPlayers)
        assertEquals("Undo Update game setup", state.undoEntry?.label)
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
        assertEquals(pendingCountdown?.targetEpoch, state.countdown?.targetEpoch)
        assertEquals("Pull in", state.countdown?.label)
        assertNull(state.countdown?.nextTimingCue(state.countdown!!.targetEpoch - 20_000L))
        assertEquals(19, state.rules.gameTo)
        assertEquals("Undo Update game setup", state.undoEntry?.label)

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
