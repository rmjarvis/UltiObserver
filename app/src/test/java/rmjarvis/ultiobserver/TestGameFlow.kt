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

class TestGameFlow : GameModelTestFixtures() {
    // Test a representative complete game from setup through halftime to final score.
    // Keep this as a user-visible story that exercises common actions between scoring events.
    @Test
    fun normalGamePath() {
        val VC = TeamId.TEAM_ONE
        val ANIMAL = TeamId.TEAM_TWO

        // Set up a short game so the test can cover opening pull, halftime, and game over
        // without needing a long repetitive scoring sequence.
        val rules = GameRules(
            gameTo = 5,
            halftimeMinutes = 7,
            useHalfCap = false,
            useSoftCap = false,
            useHardCap = false,
        )
        val setup = standardGameSetup(
            startTime = LocalTime.of(10, 0),
            rules = rules,
            pullingTeam = VC,
            pullingFromEnd = FieldEnd.NEAR,
        )

        // Start the game and verify the first between-points sequence matches the setup.
        var state = createLiveGameState(setup)
        assertEquals(LivePhase.BETWEEN_POINTS, state.phase)
        assertEquals("Viscous Coupling", state.teamOne.name)
        assertEquals("Animal", state.teamTwo.name)
        assertEquals(0, state.teamOne.score)
        assertEquals(0, state.teamTwo.score)
        assertEquals(0, state.teamOne.timeoutsUsedThisHalf)
        assertEquals(2, state.timeoutsAllowedThisHalf(VC))
        assertEquals(2, state.timeoutsRemaining(VC))
        assertEquals(0, state.teamTwo.timeoutsUsedThisHalf)
        assertEquals(2, state.timeoutsAllowedThisHalf(ANIMAL))
        assertEquals(2, state.timeoutsRemaining(ANIMAL))
        assertEquals(VC, state.pullingTeam)
        assertEquals(FieldEnd.NEAR, state.pullingFromEnd)
        assertEquals(ANIMAL, state.nearAttackingTeam)
        assertEquals(CountdownKind.OPENING_PULL, state.countdown?.kind)
        assertEquals("Pull in", state.countdown?.label)
        assertEquals(40, state.countdown?.durationSeconds)

        // The opening pull starts the first live point and clears the initial countdown.
        state = state.beginLivePoint()
        assertEquals(LivePhase.LIVE_POINT, state.phase)
        assertNull(state.countdown)
        assertEquals("Undo Start Point", state.undoEntry?.label)

        // Animal calls a live-point timeout; the point stays live but a thrower countdown starts.
        val firstTimeout = state.assessTimeout(ANIMAL, 1_000_000L)
        assertNull(firstTimeout.message())
        state = firstTimeout.state
        assertEquals(1, state.teamTwo.timeoutsUsedThisHalf)
        assertEquals(1, state.timeoutsRemaining(ANIMAL))
        assertEquals(LivePhase.LIVE_POINT, state.phase)
        assertEquals(CountdownKind.TIME_OUT, state.countdown?.kind)
        assertEquals("Offense set in", state.countdown?.label)
        assertEquals(70, state.countdown?.durationSeconds)
        assertEquals(1_070_000L, state.countdown?.targetEpoch)
        assertEquals("Undo Timeout by Animal", state.undoEntry?.label)

        state = state.continueLivePoint()
        assertEquals(LivePhase.LIVE_POINT, state.phase)
        assertNull(state.countdown)

        // Viscous Coupling gets a yellow on #17, then a blue card.  No yardage penalty yet.
        var cardResult = state.assessYellowCard(VC, "17")
        state = cardResult.state
        assertFalse(cardResult.needsMisconductChoice)
        assertEquals("Yellow card on player 17.\nViscous Coupling has 1 card.", cardResult.message())
        assertEquals(1, state.teamYellowCards(VC))
        assertEquals(
            InGamePlayerCardRecord("17", yellows = 1),
            state.playerCards(VC).single { it.jerseyNumber == "17" },
        )

        cardResult = state.assessBlueCard(VC)
        state = cardResult.state
        assertFalse(cardResult.needsMisconductChoice)
        assertEquals("Viscous Coupling has 2 cards.", cardResult.message())
        assertEquals(1, state.teamOne.blueCards)

        // Viscous Coupling reaches three team card points with a yellow on #8 during a live point.
        // Since the app cannot infer possession, the model reports that a misconduct choice is needed.
        cardResult = state.assessYellowCard(VC, "8")
        state = cardResult.state
        assertTrue(cardResult.needsMisconductChoice)
        assertEquals("Yellow card on player 8.\nViscous Coupling has 3 cards.", cardResult.message())
        assertEquals(2, state.teamYellowCards(VC))
        assertEquals("Undo Yellow on #8 of Viscous Coupling", state.undoEntry?.label)
        assertEquals(
            InGamePlayerCardRecord("8", yellows = 1),
            state.playerCards(VC).single { it.jerseyNumber == "8" },
        )
        assertTrue(
            cardResult.misconductPrompt().resolutionMessage(againstOffense = true)
                .contains("Reverse brick"),
        )
        assertTrue(
            cardResult.misconductPrompt().resolutionMessage(againstOffense = true)
                .contains("Offense has 30 seconds to set. Then defense has 20 seconds to check the disc in."),
        )
        state = state.withPendingMisconductCountdown()
        assertTrue(state.pendingMisconductCountdown)
        assertNull(state.countdown)
        state = state.startMisconductCountdown(1_010_000L)
        assertFalse(state.pendingMisconductCountdown)
        assertEquals(CountdownKind.TIME_OUT, state.countdown?.kind)
        assertEquals("Offense set in", state.countdown?.label)
        assertEquals(30, state.countdown?.durationSeconds)
        assertEquals(1_040_000L, state.countdown?.targetEpoch)
        state = state.advanceGameClock(1_040_000L)
        assertEquals(LivePhase.LIVE_POINT, state.phase)
        assertNull(state.countdown)
        assertEquals("Point continued.", state.lastEvent)

        // Viscous Coupling scores the first point, so they pull the next point from the far end.
        val firstGoalTime = timestampAt(state, LocalTime.of(10, 5))
        state = state.recordGoal(VC, firstGoalTime)
        assertEquals(LivePhase.BETWEEN_POINTS, state.phase)
        assertEquals(1, state.teamOne.score)
        assertEquals(0, state.teamTwo.score)
        assertEquals(VC, state.pullingTeam)
        assertEquals(FieldEnd.FAR, state.pullingFromEnd)
        assertEquals(VC, state.nearAttackingTeam)
        assertEquals("Signal in", state.countdown?.label)
        assertEquals(60, state.countdown?.durationSeconds)
        assertEquals(firstGoalTime + 60_000L, state.countdown?.targetEpoch)
        assertNull(state.pendingCapOffer)

        // During the next pull sequence, Viscous Coupling records an offsides as the pulling team.
        val pullInfractionResult = state.assessPullInfraction(VC)
        state = pullInfractionResult.state
        assertEquals(1, state.teamOne.offsides)
        assertEquals(0, state.teamTwo.offsides)
        assertEquals(LivePhase.LIVE_POINT, state.phase)
        assertNull(state.countdown)
        assertEquals("Start at brick mark", pullInfractionResult.message())
        assertEquals("Undo Offsides on Viscous Coupling", state.undoEntry?.label)

        // Animal picks up yellow cards for #23 and #8
        cardResult = state.assessYellowCard(ANIMAL, "23")
        state = cardResult.state
        assertEquals("Yellow card on player 23.\nAnimal has 1 card.", cardResult.message())
        assertEquals(1, state.teamYellowCards(ANIMAL))
        assertEquals(
            InGamePlayerCardRecord("23", yellows = 1),
            state.playerCards(ANIMAL).single { it.jerseyNumber == "23" },
        )

        cardResult = state.assessYellowCard(ANIMAL, "8")
        state = cardResult.state
        assertEquals("Yellow card on player 8.\nAnimal has 2 cards.", cardResult.message())
        assertEquals(2, state.teamYellowCards(ANIMAL))
        assertEquals(
            InGamePlayerCardRecord("8", yellows = 1),
            state.playerCards(ANIMAL).single { it.jerseyNumber == "8" },
        )

        // Animal picks up two technical fouls during the live point.
        var technicalFoulResult = state.assessTechnicalFoul(ANIMAL)
        state = technicalFoulResult.state
        assertFalse(technicalFoulResult.needsMisconductChoice)
        assertEquals("Animal has 1 technical foul.", technicalFoulResult.message())

        technicalFoulResult = state.assessTechnicalFoul(ANIMAL)
        state = technicalFoulResult.state
        assertFalse(technicalFoulResult.needsMisconductChoice)
        assertEquals("Animal has 2 technical fouls.", technicalFoulResult.message())

        // Viscous Coupling calls a live-point timeout, starting an offense-set countdown.
        val secondTimeoutTime = timestampAt(state, LocalTime.of(10, 6))
        val secondTimeout = state.assessTimeout(VC, secondTimeoutTime)
        assertNull(secondTimeout.message())
        state = secondTimeout.state
        assertEquals(1, state.timeoutsRemaining(VC))
        assertEquals(LivePhase.LIVE_POINT, state.phase)
        assertEquals(CountdownKind.TIME_OUT, state.countdown?.kind)
        assertEquals("Offense set in", state.countdown?.label)
        assertEquals(70, state.countdown?.durationSeconds)
        assertEquals(secondTimeoutTime + 70_000L, state.countdown?.targetEpoch)

        state = state.continueLivePoint()
        assertEquals(LivePhase.LIVE_POINT, state.phase)
        assertNull(state.countdown)

        // Animal scores next to finish the live point.
        state = recordGoalAt(state, ANIMAL, LocalTime.of(10, 10))
        assertEquals(LivePhase.BETWEEN_POINTS, state.phase)
        assertEquals(1, state.teamOne.score)
        assertEquals(1, state.teamTwo.score)
        assertEquals(ANIMAL, state.pullingTeam)

        // Animal reaches the technical-foul threshold between points, producing the yardage message directly.
        technicalFoulResult = state.assessTechnicalFoul(ANIMAL)
        state = technicalFoulResult.state
        assertFalse(technicalFoulResult.needsMisconductChoice)
        assertEquals(3, state.teamTwo.technicalFouls)
        assertTrue(technicalFoulResult.message()!!.contains("Animal has 3 technical fouls."))
        assertTrue(technicalFoulResult.message()!!.contains("Penalty against pulling team."))
        assertTrue(technicalFoulResult.message()!!.contains("Receiving team starts at attacking brick."))
        assertEquals("Undo Technical Foul on Animal", state.undoEntry?.label)

        // Viscous Coupling scores the next two points, reaching halftime in this game-to-5 setup.
        state = recordGoalFromCurrentStateAt(state, VC, LocalTime.of(10, 15))
        assertEquals(LivePhase.BETWEEN_POINTS, state.phase)
        assertEquals(2, state.teamOne.score)
        assertEquals(1, state.teamTwo.score)

        val halftimeGoalTime = timestampAt(state, LocalTime.of(10, 20))
        state = state.recordGoalFromCurrentState(VC, halftimeGoalTime)
        assertEquals(LivePhase.HALFTIME, state.phase)
        assertEquals(3, state.teamOne.score)
        assertEquals(1, state.teamTwo.score)
        assertTrue(state.halftimeTaken)
        assertEquals(ANIMAL, state.pullingTeam)
        assertEquals(FieldEnd.NEAR, state.pullingFromEnd)
        assertEquals(VC, state.nearAttackingTeam)
        assertEquals(CountdownKind.HALFTIME, state.countdown?.kind)
        assertEquals("Halftime", state.countdown?.label)
        assertEquals(420, state.countdown?.durationSeconds)
        assertEquals(halftimeGoalTime + 420_000L, state.countdown?.targetEpoch)
        assertEquals(0, state.teamOne.timeoutsUsedThisHalf)
        assertEquals(2, state.timeoutsAllowedThisHalf(VC))
        assertEquals(2, state.timeoutsRemaining(VC))
        assertEquals(0, state.teamTwo.timeoutsUsedThisHalf)
        assertEquals(2, state.timeoutsAllowedThisHalf(ANIMAL))
        assertEquals(2, state.timeoutsRemaining(ANIMAL))
        assertEquals("Undo Goal by Viscous Coupling", state.undoEntry?.label)

        // After halftime, the next pull can start and should behave like a normal live point.
        state = state.beginLivePoint()
        assertEquals(LivePhase.LIVE_POINT, state.phase)
        assertNull(state.countdown)

        // Animal scores after halftime, then uses one second-half timeout before the next pull.
        state = recordGoalAt(state, ANIMAL, LocalTime.of(10, 30))
        assertEquals(LivePhase.BETWEEN_POINTS, state.phase)
        assertEquals(3, state.teamOne.score)
        assertEquals(2, state.teamTwo.score)

        val thirdTimeout = state.assessTimeout(ANIMAL, 1_810_000L)
        assertNull(thirdTimeout.message())
        state = thirdTimeout.state
        assertEquals(1, state.teamTwo.timeoutsUsedThisHalf)
        assertEquals(1, state.timeoutsRemaining(ANIMAL))
        assertEquals("Signal in", state.countdown?.label)
        assertEquals(130, state.countdown?.durationSeconds)

        // Animal keeps pushing after halftime and ties the game.
        state = recordGoalFromCurrentStateAt(state, ANIMAL, LocalTime.of(10, 35))
        assertEquals(3, state.teamOne.score)
        assertEquals(3, state.teamTwo.score)
        assertEquals(LivePhase.BETWEEN_POINTS, state.phase)

        // Viscous Coupling gets one more point, but Animal answers and then wins on universe.
        state = recordGoalFromCurrentStateAt(state, VC, LocalTime.of(10, 40))
        assertEquals(4, state.teamOne.score)
        assertEquals(3, state.teamTwo.score)
        assertEquals(LivePhase.BETWEEN_POINTS, state.phase)

        state = recordGoalFromCurrentStateAt(state, ANIMAL, LocalTime.of(10, 45))
        assertEquals(4, state.teamOne.score)
        assertEquals(4, state.teamTwo.score)
        assertEquals(LivePhase.BETWEEN_POINTS, state.phase)

        // The final Animal goal ends the game and clears live-only timing state.
        state = recordGoalFromCurrentStateAt(state, ANIMAL, LocalTime.of(10, 50))
        assertEquals(LivePhase.GAME_OVER, state.phase)
        assertEquals(4, state.teamOne.score)
        assertEquals(5, state.teamTwo.score)
        assertEquals(timestampAt(state, LocalTime.of(10, 50)), state.endEpoch)
        assertEquals(5, state.winningScore)
        assertNull(state.countdown)
        assertNull(state.pendingCapOffer)
        assertEquals("Game over.", state.lastEvent)
        assertNotNull(state.undoEntry)
        assertEquals("Undo End Game", state.undoEntry?.label)
        assertEquals(LivePhase.BETWEEN_POINTS, state.undoEntry?.previous?.phase)
        assertEquals(4, state.undoEntry?.previous?.teamOne?.score)
        assertEquals(5, state.undoEntry?.previous?.teamTwo?.score)
    }
}
