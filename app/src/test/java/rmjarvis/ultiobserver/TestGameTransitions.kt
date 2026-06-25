package rmjarvis.ultiobserver

import java.time.Duration
import java.time.LocalTime
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

        // Start the game and verify the opening-pull sequence matches the setup.
        var state = createLiveGameState(setup)
        assertEquals(GamePhase.PRE_GAME, state.phase)
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

        // Animal calls a live-point timeout; the point stays live but a timeout countdown starts.
        val firstTimeout = state.assessTimeout(ANIMAL, 1_000_000L)
        assertEquals(
            "Timeout charged to Animal. They have 1 timeout remaining in this half.",
            firstTimeout.message(),
        )
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

        // The timeout countdown can be cleared to resume the point.
        state = state.continueLivePoint()
        assertEquals(GamePhase.LIVE_POINT, state.phase)
        assertNull(state.countdown)

        // Viscous Coupling gets a yellow on #17, then a blue card.  No yardage penalty yet.
        var cardResult = state.assessYellowCard(VC, "17")
        state = cardResult.state
        assertFalse(cardResult.needsMisconductChoice)
        assertEquals(
            "Yellow card on player 17.\nViscous Coupling has 1 blue card.",
            cardResult.message(),
        )
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
        // Since the app cannot infer possession, the model reports that a misconduct choice is
        // needed.
        cardResult = state.assessYellowCard(VC, "8")
        state = cardResult.state
        assertTrue(cardResult.needsMisconductChoice)
        assertEquals(
            "Yellow card on player 8.\nViscous Coupling has 3 total blue cards.",
            cardResult.message(),
        )
        assertEquals(2, state.teamYellowCards(VC))
        assertEquals("Undo Yellow on #8 of Viscous Coupling", state.undoEntry?.label)
        assertEquals(
            PlayerRecord(
                jerseyNumber = "8",
                cards = listOf(InGamePlayerCardEvent(CardType.YELLOW, index = 1)),
            ),
            state.playerCards(VC).single { it.jerseyNumber == "8" },
        )
        assertTrue(
            cardResult.misconductPrompt().resolutionMessage(againstOffense = true)
                .contains(
                    "Viscous Coupling moves the disc to the reverse brick in the end zone " +
                        "they are defending."
                ),
        )
        assertTrue(
            cardResult.misconductPrompt().resolutionMessage(againstOffense = true)
                .contains(
                    "Offense has 30 seconds to set. Then defense has 20 seconds to check " +
                        "the disc in."
                ),
        )

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
        val pullViolationResult = state.assessPullViolation(VC)
        state = pullViolationResult.state
        assertEquals(1, state.teamOne.offsides)
        assertEquals(0, state.teamTwo.offsides)
        assertEquals(GamePhase.LIVE_POINT, state.phase)
        assertNull(state.countdown)
        assertEquals(
            "This is Viscous Coupling's first pull violation.\n\n" +
                "Animal starts at the brick mark.\n\n" +
                "The disc is live -- no defensive check is required.",
            pullViolationResult.message(),
        )
        assertEquals("Undo Offsides on Viscous Coupling", state.undoEntry?.label)

        // Animal picks up yellow cards for #23 and #8
        cardResult = state.assessYellowCard(ANIMAL, "23")
        state = cardResult.state
        assertEquals("Yellow card on player 23.\nAnimal has 1 blue card.", cardResult.message())
        assertEquals(1, state.teamYellowCards(ANIMAL))
        assertEquals(
            PlayerRecord(
                jerseyNumber = "23",
                cards = listOf(InGamePlayerCardEvent(CardType.YELLOW, index = 2)),
            ),
            state.playerCards(ANIMAL).single { it.jerseyNumber == "23" },
        )
        cardResult = state.assessYellowCard(ANIMAL, "8")
        state = cardResult.state
        assertEquals(
            "Yellow card on player 8.\nAnimal has 2 total blue cards.",
            cardResult.message(),
        )
        assertEquals(2, state.teamYellowCards(ANIMAL))
        assertEquals(
            PlayerRecord(
                jerseyNumber = "8",
                cards = listOf(InGamePlayerCardEvent(CardType.YELLOW, index = 3)),
            ),
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

        // Viscous Coupling calls a timeout, starting a countdown.
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

        // The point can resume after the timeout countdown is cleared.
        state = state.continueLivePoint()
        assertEquals(GamePhase.LIVE_POINT, state.phase)
        assertNull(state.countdown)

        // Animal scores next to finish the live point.
        state = recordGoalAt(state, ANIMAL, LocalTime.of(10, 10))
        assertEquals(GamePhase.BETWEEN_POINTS, state.phase)
        assertEquals(1, state.teamOne.score)
        assertEquals(1, state.teamTwo.score)
        assertEquals(ANIMAL, state.pullingTeam)

        // Animal reaches the technical-foul threshold between points, producing a yardage
        // penalty for the restart.  No pull.
        technicalFoulResult = state.assessTechnicalFoul(ANIMAL)
        state = technicalFoulResult.state
        assertFalse(technicalFoulResult.needsMisconductChoice)
        assertEquals(3, state.teamTwo.technicalFouls)
        assertTrue(
            technicalFoulResult.message()!!.contains("This is Animal's third technical foul.")
        )
        assertTrue(technicalFoulResult.message()!!.contains("Penalty against Animal."))
        assertTrue(
            technicalFoulResult.message()!!.contains(
                "Viscous Coupling starts at attacking brick."
            )
        )
        assertEquals("Undo Technical foul on Animal", state.undoEntry?.label)

        // Viscous Coupling scores the next point.
        state = recordGoalFromCurrentStateAt(state, VC, LocalTime.of(10, 15))
        assertEquals(GamePhase.BETWEEN_POINTS, state.phase)
        assertEquals(2, state.teamOne.score)
        assertEquals(1, state.teamTwo.score)

        // Viscous Coupling scores again, reaching halftime in this game-to-5 setup.
        // Halftime resets the current-half timeout counts.
        val halftimeGoalTime = timestampAt(state, LocalTime.of(10, 20))
        state = state.recordGoalFromCurrentState(VC, halftimeGoalTime)
        assertEquals(GamePhase.HALFTIME, state.phase)
        assertEquals(3, state.teamOne.score)
        assertEquals(1, state.teamTwo.score)
        assertTrue(state.halftimeTaken)
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
        assertFalse(state.halftimeTransitionReady(halftimeGoalTime + 419_999L))
        assertTrue(state.halftimeTransitionReady(halftimeGoalTime + 420_000L))

        // After halftime, Animal is pulling, since they received for the start of the game.
        assertEquals(ANIMAL, state.pullingTeam)
        assertEquals(FieldEnd.NEAR, state.pullingFromEnd)
        assertEquals(VC, state.nearAttackingTeam)
        state = state.beginLivePoint()
        assertEquals(GamePhase.LIVE_POINT, state.phase)
        assertNull(state.countdown)

        // Animal scores after halftime, then uses one second-half timeout before the next pull.
        state = recordGoalAt(state, ANIMAL, LocalTime.of(10, 30))
        assertEquals(GamePhase.BETWEEN_POINTS, state.phase)
        assertEquals(3, state.teamOne.score)
        assertEquals(2, state.teamTwo.score)
        val thirdTimeout = state.assessTimeout(ANIMAL, 1_810_000L)
        assertEquals(
            "Timeout charged to Animal. They have 1 timeout remaining in this half.",
            thirdTimeout.message(),
        )
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

        // Viscous Coupling gets one more point, but Animal answers. Universe point!
        state = recordGoalFromCurrentStateAt(state, VC, LocalTime.of(10, 40))
        assertEquals(4, state.teamOne.score)
        assertEquals(3, state.teamTwo.score)
        assertEquals(GamePhase.BETWEEN_POINTS, state.phase)
        state = recordGoalFromCurrentStateAt(state, ANIMAL, LocalTime.of(10, 45))
        assertEquals(4, state.teamOne.score)
        assertEquals(4, state.teamTwo.score)
        assertEquals(GamePhase.BETWEEN_POINTS, state.phase)

        // Animal wins on universe, which ends the game and clears live-only timing state.
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

    /**
     * Test the ability to manually adjust a countdown by +/- 5 seconds.
     */
    @Test
    fun countdownAdjustments() {
        // Clicking the +5 adjutment moves the target time while preserving the original duration.
        var state = standardLiveGameState()
        val originalCountdown = state.countdown!!
        state = state.addTimeToCountdown(5)
        assertEquals(originalCountdown.targetEpoch + 5_000L, state.countdown?.targetEpoch)
        assertEquals(originalCountdown.durationSeconds, state.countdown?.durationSeconds)
        assertEquals("Adjusted timer by 0:05.", state.lastEvent)

        // Cicking -5 goes the other way.
        state = state.addTimeToCountdown(-5)
        assertEquals(originalCountdown.targetEpoch, state.countdown?.targetEpoch)
        assertEquals(originalCountdown.durationSeconds, state.countdown?.durationSeconds)
        assertEquals(
            Duration.ofMillis(state.countdown!!.targetEpoch - 10_000L),
            state.countdown!!.remainingDuration(10_000L),
        )
        assertEquals("Adjusted timer by -0:05.", state.lastEvent)

        // Countdown actions are no-ops when there is no active countdown to adjust.
        val livePointWithoutCountdown = state.beginLivePoint()
        assertEquals(livePointWithoutCountdown, livePointWithoutCountdown.addTimeToCountdown(5))
        assertEquals(
            livePointWithoutCountdown,
            livePointWithoutCountdown.toggleCountdownPaused(5_000L),
        )
    }

    /**
     * Test countdown pause and resume behavior, including defensive duplicate calls.
     */
    @Test
    fun countdownPauseAndResume() {
        // Pausing freezes display time, suppresses cues/transitions, and records the pause time.
        var state = standardLiveGameState().addTimeToCountdown(60)
        val adjustedCountdown = state.countdown!!
        state = state.toggleCountdownPaused(10_000L)
        val pausedCountdown = state.countdown!!
        assertTrue(pausedCountdown.isPaused())
        assertEquals(10_000L, pausedCountdown.pausedAtEpoch)
        assertEquals(adjustedCountdown.targetEpoch, pausedCountdown.targetEpoch)
        val remainingWhenPaused = Duration.ofMillis(pausedCountdown.targetEpoch - 10_000L)
        assertEquals(remainingWhenPaused, pausedCountdown.remainingDuration(30_000L))
        assertEquals(remainingWhenPaused, state.activeCountdownDisplay(30_000L)?.remaining)
        assertTrue(state.activeCountdownDisplay(30_000L)?.isPaused == true)
        assertNull(pausedCountdown.nextTimingCue(30_000L))
        assertTrue(state.dueTimingAlerts(pausedCountdown.targetEpoch).isEmpty())
        assertEquals(
            state,
            state.applyExpiredCountdownTransitions(
                pausedCountdown.targetEpoch + 1_000L,
                showDefenseCountdowns = false,
            ),
        )

        // Direct duplicate pause calls are not normal UI paths, but can happen if callbacks race
        // recomposition; they keep the original pause point.
        assertEquals(pausedCountdown, pausedCountdown.pause(20_000L))

        // Countdown adjustments while paused keep the paused display time frozen.
        state = state.addTimeToCountdown(5)
        assertEquals(pausedCountdown.targetEpoch + 5_000L, state.countdown?.targetEpoch)
        assertEquals(10_000L, state.countdown?.pausedAtEpoch)

        // Resuming shifts the target by the pause duration.
        state = state.toggleCountdownPaused(25_000L)
        assertFalse(state.countdown!!.isPaused())
        assertNull(state.countdown?.pausedAtEpoch)
        assertEquals(pausedCountdown.targetEpoch + 20_000L, state.countdown?.targetEpoch)

        // Duplicate resume calls are also possible from callback races, and are no-ops.
        assertEquals(state.countdown, state.countdown?.resume(30_000L))
        assertEquals("Timer resumed.", state.lastEvent)
    }

    /**
     * Test countdown helper guardrails for malformed or phase-inconsistent model states.
     */
    @Test
    fun countdownGuardrails() {
        val VC = TeamId.TEAM_ONE

        // Countdown target swapping is a no-op for non-between-points countdowns and fails on
        // malformed ones.
        val livePointWithoutCountdown = standardLiveGameState().beginLivePoint()
        val inPointTimeoutCountdown = livePointWithoutCountdown.assessTimeout(
            VC,
            600_000L,
        ).state.countdown!!
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

        // A countdown kind that does not match the phase is an impossible model state, so fail
        // loudly.
        val mismatchedCountdownState = standardLiveGameState().copy(phase = GamePhase.LIVE_POINT)
        val mismatchException = assertThrows(IllegalStateException::class.java) {
            mismatchedCountdownState.applyExpiredCountdownTransitions(
                mismatchedCountdownState.countdown!!.targetEpoch,
                showDefenseCountdowns = false,
            )
        }
        assertEquals(
            "Countdown OPENING_PULL is not valid while game phase is LIVE_POINT.",
            mismatchException.message,
        )
        val betweenPointsWithTimeoutCountdown = standardLiveGameState().copy(
            phase = GamePhase.BETWEEN_POINTS,
            countdown = inPointTimeoutCountdown,
        )
        val betweenPointsMismatchException = assertThrows(IllegalStateException::class.java) {
            betweenPointsWithTimeoutCountdown.applyExpiredCountdownTransitions(
                inPointTimeoutCountdown.targetEpoch,
                showDefenseCountdowns = false,
            )
        }
        assertEquals(
            "Countdown TIME_OUT is not valid while game phase is BETWEEN_POINTS.",
            betweenPointsMismatchException.message,
        )
        val halftimeWithBetweenPointsCountdown = standardLiveGameState().copy(
            phase = GamePhase.HALFTIME,
        )
        val halftimeMismatchException = assertThrows(IllegalStateException::class.java) {
            halftimeWithBetweenPointsCountdown.applyExpiredCountdownTransitions(
                halftimeWithBetweenPointsCountdown.countdown!!.targetEpoch,
                showDefenseCountdowns = false,
            )
        }
        assertEquals(
            "Countdown OPENING_PULL is not valid while game phase is HALFTIME.",
            halftimeMismatchException.message,
        )

        // Defensive guard for malformed halftime states: without a halftime countdown, or outside
        // halftime, the helper should simply say the second half is not ready.
        assertFalse(standardLiveGameState().copy(countdown = null).halftimeTransitionReady(1_000L))
        assertFalse(standardLiveGameState().halftimeTransitionReady(1_000L))
    }

    /**
     * Test the unusual case where a timeout interrupts live-point misconduct penalty timing.
     *
     * A third or later card creates a misconduct penalty.  While the observer is sorting
     * out the yardage and restart, the offense could technically call a timeout.  That is a
     * weird edge case, but it should implicitly start the 30-second misconduct restart and
     * add the 70-second timeout allowance, rather than becoming a normal 70-second timeout.
     */
    @Test
    fun timeoutDuringMisconductPenalty() {
        val VC = TeamId.TEAM_ONE
        val ANIMAL = TeamId.TEAM_TWO

        // First set up a defensive misconduct penalty.  Viscous Coupling is pulling, so Animal is
        // the offense and is the team that can legally call a timeout before the restart.
        var state = standardLiveGameState().beginLivePoint()
        state = state.assessBlueCard(VC).state
        state = state.assessBlueCard(VC).state
        val misconductResult = state.assessYellowCard(VC, "8")
        assertTrue(misconductResult.needsMisconductChoice)

        // The observer resolves the misconduct prompt as a penalty against the defense.  Pressing
        // OK on that resolved dialog is what exposes the "Start misconduct countdown" button.
        val misconductMessage = misconductResult.misconductPrompt()
            .resolutionMessage(againstOffense = false)
        assertTrue(
            misconductMessage.contains(
                "Animal may move the disc to the brick mark nearest the end zone they are " +
                    "attacking."
            )
        )
        val misconductState = misconductResult.state.withPendingMisconductCountdown()

        // This is when the user has a "Start misconduct countdown" button, but hasn't pressed it
        // yet.
        assertTrue(misconductState.pendingMisconductCountdown)
        assertNull(misconductState.countdown)

        // Case 1: The user starts the misconduct countdown before the timeout.
        // Note that the CountdownKind here is TIME_OUT, which is normal for live point
        // misconduct. The restart sequence works the same for both, so we use the TIME_OUT
        // label for both.
        val activeMisconductCountdownState = misconductState.startMisconductCountdown(
            1_010_000L,
        )
        assertFalse(activeMisconductCountdownState.pendingMisconductCountdown)
        assertEquals(CountdownKind.TIME_OUT, activeMisconductCountdownState.countdown?.kind)
        assertEquals("Offense set in", activeMisconductCountdownState.countdown?.label)
        assertEquals(30, activeMisconductCountdownState.countdown?.durationSeconds)
        assertEquals(1_040_000L, activeMisconductCountdownState.countdown?.targetEpoch)

        // Now while that countdown is going, Animal (on offense) chooses to call a timeout.
        // This adds 70 seconds to the ongoing countdown, rather than restarting it when
        // the timeout is called.
        val timeoutDuringActiveMisconduct = activeMisconductCountdownState.assessTimeout(
            ANIMAL,
            1_011_000L,
        ).state
        assertFalse(timeoutDuringActiveMisconduct.pendingMisconductCountdown)
        assertEquals(CountdownKind.TIME_OUT, timeoutDuringActiveMisconduct.countdown?.kind)
        assertEquals("Offense set in", timeoutDuringActiveMisconduct.countdown?.label)
        assertEquals(100, timeoutDuringActiveMisconduct.countdown?.durationSeconds)
        assertEquals(1_110_000L, timeoutDuringActiveMisconduct.countdown?.targetEpoch)
        assertEquals(1, timeoutDuringActiveMisconduct.teamTwo.timeoutsUsedThisHalf)

        // Case 2: Animal calls a timeout while the misconduct countdown was still pending.
        // This causes the misconduct countdown to be implicitly started, and then it gets
        // an additional 70 seconds for the timeout.
        val timeoutDuringPendingMisconduct = misconductState.assessTimeout(
            ANIMAL,
            1_011_000L,
        ).state
        assertFalse(timeoutDuringPendingMisconduct.pendingMisconductCountdown)
        assertEquals(CountdownKind.TIME_OUT, timeoutDuringPendingMisconduct.countdown?.kind)
        assertEquals("Offense set in", timeoutDuringPendingMisconduct.countdown?.label)
        assertEquals(100, timeoutDuringPendingMisconduct.countdown?.durationSeconds)
        assertEquals(1_111_000L, timeoutDuringPendingMisconduct.countdown?.targetEpoch)
        assertEquals(1, timeoutDuringPendingMisconduct.teamTwo.timeoutsUsedThisHalf)

        // When that longer countdown expires, play finally restarts.
        val continuedState = timeoutDuringActiveMisconduct.applyExpiredCountdownTransitions(
            1_110_000L,
            showDefenseCountdowns = false,
        )
        assertEquals(GamePhase.LIVE_POINT, continuedState.phase)
        assertNull(continuedState.countdown)
        assertEquals("Point continued.", continuedState.lastEvent)
    }

    /**
     * Test automatic countdown expiry for pre-pull and live-point countdowns.
     */
    @Test
    fun countdownExpiry() {
        val VC = TeamId.TEAM_ONE

        // Between-points countdown expiration silently starts the point, but leaves an undo path.
        val state = standardLiveGameState()
        val betweenPointsCountdown = state.countdown!!
        assertEquals(
            state,
            state.applyExpiredCountdownTransitions(
                betweenPointsCountdown.targetEpoch - 1L,
                showDefenseCountdowns = false,
            ),
        )
        val automaticStartState = state.applyExpiredCountdownTransitions(
            betweenPointsCountdown.targetEpoch,
            showDefenseCountdowns = false,
        )
        assertEquals(GamePhase.LIVE_POINT, automaticStartState.phase)
        assertNull(automaticStartState.countdown)
        assertEquals("Point is live.", automaticStartState.lastEvent)
        assertEquals("Undo Start point", automaticStartState.undoEntry?.label)
        val expiredPullDecisionState = state.copy(
            countdown = null,
            pullCountdownExpired = true,
        )
        val undoneAutomaticStartState = assertUndoRestores(
            expiredPullDecisionState,
            automaticStartState,
        )
        assertEquals(
            undoneAutomaticStartState,
            undoneAutomaticStartState.redoLastAction().undoLastAction(),
        )
        assertEquals(state, state.redoLastAction())
        assertEquals(
            undoneAutomaticStartState,
            undoneAutomaticStartState.applyExpiredCountdownTransitions(
                betweenPointsCountdown.targetEpoch,
                showDefenseCountdowns = false,
            ),
        )
        assertTrue(undoneAutomaticStartState.hasExpiredPullActions())
        assertFalse(state.hasExpiredPullActions())

        // In-point timeout countdowns return to live game when expiring.
        var inPointState = state.beginLivePoint()
        inPointState = inPointState.assessTimeout(VC, 500_000L).state
        val timeoutCountdown = inPointState.countdown!!
        assertEquals(
            inPointState,
            inPointState.applyExpiredCountdownTransitions(
                timeoutCountdown.targetEpoch - 1L,
                showDefenseCountdowns = false,
            ),
        )
        inPointState = inPointState.applyExpiredCountdownTransitions(
            timeoutCountdown.targetEpoch,
            showDefenseCountdowns = false,
        )
        assertEquals(GamePhase.LIVE_POINT, inPointState.phase)
        assertNull(inPointState.countdown)
    }

    /// Test game-prompt formatting for halftime and game-over prompts.
    @Test
    fun gamePromptFormatting() {
        // Halftime prompt exposes a stable title.
        val state = standardLiveGameState().beginLivePoint()
        val halftimePrompt = GamePrompt.HalftimeStarted(state)
        assertEquals("Halftime", halftimePrompt.formatTitle())
        assertEquals("Announce halftime.", halftimePrompt.formatMessage())

        // Game-over prompt exposes a stable title and score summary text.
        val gameOverState = state.copy(
            phase = GamePhase.GAME_OVER,
            teamOne = state.teamOne.copy(score = 3),
            teamTwo = state.teamTwo.copy(score = 5),
        )
        val gameOverPrompt = GamePrompt.GameOver(gameOverState)
        assertEquals("Game over", gameOverPrompt.formatTitle())
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
    }

}
