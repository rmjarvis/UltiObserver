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

/// Tests for game phase transitions from setup through live play, halftime, and game over.
class TestGameTransitions : GameDomainTestFixtures() {
    /**
     * Test a representative complete game from setup through halftime to final score.
     * Keep this as a user-visible story that exercises common actions between scoring events.
     */
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
        assertEquals(GamePhase.BETWEEN_POINTS, state.phase)
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
        assertEquals(GamePhase.LIVE_POINT, state.phase)
        assertNull(state.countdown)
        assertEquals("Undo Start point", state.undoEntry?.label)

        // Animal calls a live-point timeout; the point stays live but a thrower countdown starts.
        val firstTimeout = state.assessTimeout(ANIMAL, 1_000_000L)
        assertEquals("Timeout charged to Animal. They have 1 timeout remaining in this half.", firstTimeout.message())
        assertEquals("Timeout", firstTimeout.event?.formatPopupTitle())
        state = firstTimeout.state
        assertEquals(1, state.teamTwo.timeoutsUsedThisHalf)
        assertEquals(1, state.timeoutsRemaining(ANIMAL))
        assertEquals(GamePhase.LIVE_POINT, state.phase)
        assertEquals(CountdownKind.TIME_OUT, state.countdown?.kind)
        assertEquals("Offense set in", state.countdown?.label)
        assertEquals(70, state.countdown?.durationSeconds)
        assertEquals(1_070_000L, state.countdown?.targetEpoch)
        assertEquals("Undo Timeout by Animal", state.undoEntry?.label)

        state = state.continueLivePoint()
        assertEquals(GamePhase.LIVE_POINT, state.phase)
        assertNull(state.countdown)

        // Viscous Coupling gets a yellow on #17, then a blue card.  No yardage penalty yet.
        var cardResult = state.assessYellowCard(VC, "17")
        state = cardResult.state
        assertFalse(cardResult.needsMisconductChoice)
        assertEquals("Yellow card on player 17.\nViscous Coupling has 1 blue card.", cardResult.message())
        assertEquals(1, state.teamYellowCards(VC))
        assertEquals(
            playerRecordWithCards("17", yellows = 1),
            state.playerCards(VC).single { it.jerseyNumber == "17" },
        )

        cardResult = state.assessBlueCard(VC)
        state = cardResult.state
        assertFalse(cardResult.needsMisconductChoice)
        assertEquals("This is Viscous Coupling's second blue card.", cardResult.message())
        assertEquals(1, state.teamOne.blueCards)

        // Viscous Coupling reaches three team card points with a yellow on #8 during a live point.
        // Since the app cannot infer possession, the model reports that a misconduct choice is needed.
        cardResult = state.assessYellowCard(VC, "8")
        state = cardResult.state
        assertTrue(cardResult.needsMisconductChoice)
        assertEquals("Yellow card on player 8.\nViscous Coupling has 3 total blue cards.", cardResult.message())
        assertEquals(2, state.teamYellowCards(VC))
        assertEquals("Undo Yellow on #8 of Viscous Coupling", state.undoEntry?.label)
        assertEquals(
            playerRecordWithCards("8", yellows = 1),
            state.playerCards(VC).single { it.jerseyNumber == "8" },
        )
        assertTrue(
            cardResult.misconductPrompt().resolutionMessage(againstOffense = true)
                .contains("Viscous Coupling moves the disc to the reverse brick in the end zone they are defending."),
        )
        assertTrue(
            cardResult.misconductPrompt().resolutionMessage(againstOffense = true)
                .contains("Offense has 30 seconds to set. Then defense has 20 seconds to check the disc in."),
        )
        assertEquals(state, state.startMisconductCountdown(1_010_000L))
        state = state.withPendingMisconductCountdown()
        assertTrue(state.pendingMisconductCountdown)
        assertNull(state.countdown)
        val timeoutDuringPendingMisconduct = state.assessTimeout(ANIMAL, 1_011_000L).state
        assertFalse(timeoutDuringPendingMisconduct.pendingMisconductCountdown)
        assertEquals(CountdownKind.TIME_OUT, timeoutDuringPendingMisconduct.countdown?.kind)
        assertEquals(70, timeoutDuringPendingMisconduct.countdown?.durationSeconds)
        assertEquals(1_081_000L, timeoutDuringPendingMisconduct.countdown?.targetEpoch)
        state = state.startMisconductCountdown(1_010_000L)
        assertFalse(state.pendingMisconductCountdown)
        assertEquals(CountdownKind.TIME_OUT, state.countdown?.kind)
        assertEquals("Offense set in", state.countdown?.label)
        assertEquals(30, state.countdown?.durationSeconds)
        assertEquals(1_040_000L, state.countdown?.targetEpoch)
        state = state.applyExpiredCountdownTransitions(1_040_000L)
        assertEquals(GamePhase.LIVE_POINT, state.phase)
        assertNull(state.countdown)
        assertEquals("Point continued.", state.lastEvent)

        // Viscous Coupling scores the first point, so they pull the next point from the far end.
        val firstGoalTime = timestampAt(state, LocalTime.of(10, 5))
        state = state.recordGoal(VC, firstGoalTime)
        assertEquals(GamePhase.BETWEEN_POINTS, state.phase)
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
        assertEquals(GamePhase.LIVE_POINT, state.phase)
        assertNull(state.countdown)
        assertEquals(
            "This is Viscous Coupling's first pull violation.\n\n" +
                "Animal starts at the brick mark.\n\n" +
                "The disc is live -- no defensive check is required.",
            pullInfractionResult.message(),
        )
        assertEquals("Undo Offsides on Viscous Coupling", state.undoEntry?.label)

        // Animal picks up yellow cards for #23 and #8
        cardResult = state.assessYellowCard(ANIMAL, "23")
        state = cardResult.state
        assertEquals("Yellow card on player 23.\nAnimal has 1 blue card.", cardResult.message())
        assertEquals(1, state.teamYellowCards(ANIMAL))
        assertEquals(
            playerRecordWithCards("23", yellows = 1),
            state.playerCards(ANIMAL).single { it.jerseyNumber == "23" },
        )

        cardResult = state.assessYellowCard(ANIMAL, "8")
        state = cardResult.state
        assertEquals("Yellow card on player 8.\nAnimal has 2 total blue cards.", cardResult.message())
        assertEquals(2, state.teamYellowCards(ANIMAL))
        assertEquals(
            playerRecordWithCards("8", yellows = 1),
            state.playerCards(ANIMAL).single { it.jerseyNumber == "8" },
        )

        // Animal picks up two technical fouls during the live point.
        var technicalFoulResult = state.assessTechnicalFoul(ANIMAL)
        state = technicalFoulResult.state
        assertFalse(technicalFoulResult.needsMisconductChoice)
        assertEquals("This is Animal's first technical foul.", technicalFoulResult.message())

        technicalFoulResult = state.assessTechnicalFoul(ANIMAL)
        state = technicalFoulResult.state
        assertFalse(technicalFoulResult.needsMisconductChoice)
        assertEquals("This is Animal's second technical foul.", technicalFoulResult.message())

        // Viscous Coupling calls a live-point timeout, starting an offense-set countdown.
        val secondTimeoutTime = timestampAt(state, LocalTime.of(10, 6))
        val secondTimeout = state.assessTimeout(VC, secondTimeoutTime)
        assertEquals(
            "Timeout charged to Viscous Coupling. They have 1 timeout remaining in this half.",
            secondTimeout.message(),
        )
        state = secondTimeout.state
        assertEquals(1, state.timeoutsRemaining(VC))
        assertEquals(GamePhase.LIVE_POINT, state.phase)
        assertEquals(CountdownKind.TIME_OUT, state.countdown?.kind)
        assertEquals("Offense set in", state.countdown?.label)
        assertEquals(70, state.countdown?.durationSeconds)
        assertEquals(secondTimeoutTime + 70_000L, state.countdown?.targetEpoch)

        state = state.continueLivePoint()
        assertEquals(GamePhase.LIVE_POINT, state.phase)
        assertNull(state.countdown)

        // Animal scores next to finish the live point.
        state = recordGoalAt(state, ANIMAL, LocalTime.of(10, 10))
        assertEquals(GamePhase.BETWEEN_POINTS, state.phase)
        assertEquals(1, state.teamOne.score)
        assertEquals(1, state.teamTwo.score)
        assertEquals(ANIMAL, state.pullingTeam)

        // Animal reaches the technical-foul threshold between points, producing the yardage message directly.
        technicalFoulResult = state.assessTechnicalFoul(ANIMAL)
        state = technicalFoulResult.state
        assertFalse(technicalFoulResult.needsMisconductChoice)
        assertEquals(3, state.teamTwo.technicalFouls)
        assertTrue(technicalFoulResult.message()!!.contains("This is Animal's third technical foul."))
        assertTrue(technicalFoulResult.message()!!.contains("Penalty against Animal."))
        assertTrue(technicalFoulResult.message()!!.contains("Viscous Coupling starts at attacking brick."))
        assertEquals("Undo Technical foul on Animal", state.undoEntry?.label)

        // Viscous Coupling scores the next two points, reaching halftime in this game-to-5 setup.
        state = recordGoalFromCurrentStateAt(state, VC, LocalTime.of(10, 15))
        assertEquals(GamePhase.BETWEEN_POINTS, state.phase)
        assertEquals(2, state.teamOne.score)
        assertEquals(1, state.teamTwo.score)

        val halftimeGoalTime = timestampAt(state, LocalTime.of(10, 20))
        state = state.recordGoalFromCurrentState(VC, halftimeGoalTime)
        assertEquals(GamePhase.HALFTIME, state.phase)
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
        assertEquals(GamePhase.LIVE_POINT, state.phase)
        assertNull(state.countdown)

        // Animal scores after halftime, then uses one second-half timeout before the next pull.
        state = recordGoalAt(state, ANIMAL, LocalTime.of(10, 30))
        assertEquals(GamePhase.BETWEEN_POINTS, state.phase)
        assertEquals(3, state.teamOne.score)
        assertEquals(2, state.teamTwo.score)

        val thirdTimeout = state.assessTimeout(ANIMAL, 1_810_000L)
        assertEquals("Timeout charged to Animal. They have 1 timeout remaining in this half.", thirdTimeout.message())
        state = thirdTimeout.state
        assertEquals(1, state.teamTwo.timeoutsUsedThisHalf)
        assertEquals(1, state.timeoutsRemaining(ANIMAL))
        assertEquals("Signal in", state.countdown?.label)
        assertEquals(130, state.countdown?.durationSeconds)

        // Animal keeps pushing after halftime and ties the game.
        state = recordGoalFromCurrentStateAt(state, ANIMAL, LocalTime.of(10, 35))
        assertEquals(3, state.teamOne.score)
        assertEquals(3, state.teamTwo.score)
        assertEquals(GamePhase.BETWEEN_POINTS, state.phase)

        // Viscous Coupling gets one more point, but Animal answers and then wins on universe.
        state = recordGoalFromCurrentStateAt(state, VC, LocalTime.of(10, 40))
        assertEquals(4, state.teamOne.score)
        assertEquals(3, state.teamTwo.score)
        assertEquals(GamePhase.BETWEEN_POINTS, state.phase)

        state = recordGoalFromCurrentStateAt(state, ANIMAL, LocalTime.of(10, 45))
        assertEquals(4, state.teamOne.score)
        assertEquals(4, state.teamTwo.score)
        assertEquals(GamePhase.BETWEEN_POINTS, state.phase)

        // The final Animal goal ends the game and clears live-only timing state.
        state = recordGoalFromCurrentStateAt(state, ANIMAL, LocalTime.of(10, 50))
        assertEquals(GamePhase.GAME_OVER, state.phase)
        assertEquals(4, state.teamOne.score)
        assertEquals(5, state.teamTwo.score)
        assertEquals(timestampAt(state, LocalTime.of(10, 50)), state.endEpoch)
        assertEquals(5, state.winningScore)
        assertNull(state.countdown)
        assertNull(state.pendingCapOffer)
        assertEquals("Game over.", state.lastEvent)
        assertNotNull(state.undoEntry)
        assertEquals("Undo End game", state.undoEntry?.label)
        assertEquals(GamePhase.BETWEEN_POINTS, state.undoEntry?.previous?.phase)
        assertEquals(4, state.undoEntry?.previous?.teamOne?.score)
        assertEquals(5, state.undoEntry?.previous?.teamTwo?.score)
    }
}
