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

class TestGameModel {
    private val testTimeZone: ZoneId = ZoneId.of("America/New_York")

    private fun standardGameSetup(
        startDate: LocalDate = LocalDate.of(2026, 1, 1),
        startTime: LocalTime,
        timeZone: ZoneId = testTimeZone,
        rules: GameRules = GameRules(
            gameTo = 5,
            useHalfCap = false,
            useSoftCap = false,
            useHardCap = false,
        ),
        pullingTeam: TeamId = TeamId.TEAM_ONE,
        pullingFromEnd: FieldEnd = FieldEnd.FAR,
    ): GameSetupState {
        return GameSetupState(
            startDate = startDate,
            startTime = startTime,
            timeZone = timeZone,
            rules = rules,
            teamOne = TeamSetup("Viscous Coupling", TeamColorChoice.WHITE),
            teamTwo = TeamSetup("Animal", TeamColorChoice.RED),
            pullingTeam = pullingTeam,
            pullingFromEnd = pullingFromEnd,
        )
    }

    private fun standardLiveGameState(
        startTime: LocalTime = LocalTime.of(11, 0),
        rules: GameRules = GameRules(
            gameTo = 5,
            useHalfCap = false,
            useSoftCap = false,
            useHardCap = false,
        ),
        startDate: LocalDate = LocalDate.of(2026, 1, 1),
        timeZone: ZoneId = testTimeZone,
        pullingTeam: TeamId = TeamId.TEAM_ONE,
        pullingFromEnd: FieldEnd = FieldEnd.FAR,
    ): LiveGameState {
        return createLiveGameState(
            standardGameSetup(
                startDate = startDate,
                startTime = startTime,
                timeZone = timeZone,
                rules = rules,
                pullingTeam = pullingTeam,
                pullingFromEnd = pullingFromEnd,
            )
        )
    }

    private fun timestampAfterStart(state: LiveGameState, minutes: Int): Long {
        return state.startEpoch + Duration.ofMinutes(minutes.toLong()).toMillis()
    }

    private fun timestampAt(date: LocalDate, time: LocalTime): Long {
        return LocalDateTime.of(date, time)
            .atZone(testTimeZone)
            .toInstant()
            .toEpochMilli()
    }

    private fun timestampAt(state: LiveGameState, time: LocalTime): Long {
        return LocalDateTime.of(state.startDate, time)
            .atZone(state.timeZone)
            .toInstant()
            .toEpochMilli()
    }

    private fun recordGoalAt(
        state: LiveGameState,
        scoringTeam: TeamId,
        time: LocalTime,
    ): LiveGameState {
        return state.recordGoal(scoringTeam, timestampAt(state, time))
    }

    private fun recordGoalFromCurrentStateAt(
        state: LiveGameState,
        scoringTeam: TeamId,
        time: LocalTime,
    ): LiveGameState {
        return state.recordGoalFromCurrentState(scoringTeam, timestampAt(state, time))
    }

    private fun startHalftimeNowAt(state: LiveGameState, time: LocalTime): LiveGameState {
        return state.startHalftimeNow(timestampAt(state, time))
    }

    private fun endGameNowAt(state: LiveGameState, time: LocalTime): LiveGameState {
        return state.endGameNow(timestampAt(state, time))
    }

    private fun applyPendingCapAt(state: LiveGameState, time: LocalTime): LiveGameState {
        return state.applyPendingCap(timestampAt(state, time))
    }

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
        assertEquals(CountdownKind.BETWEEN_POINTS, state.countdown?.kind)
        assertEquals("Pull in", state.countdown?.label)
        assertEquals(80, state.countdown?.durationSeconds)

        // The opening pull starts the first live point and clears the initial countdown.
        state = state.beginLivePoint()
        assertEquals(LivePhase.LIVE_POINT, state.phase)
        assertNull(state.countdown)
        assertEquals("Undo Start Point", state.undoEntry?.label)

        // Animal calls a live-point timeout; the point stays live but a thrower countdown starts.
        val firstTimeout = state.assessTimeout(ANIMAL, 1_000_000L)
        assertNull(firstTimeout.message)
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
        assertFalse(cardResult.needsLivePointMisconductChoice)
        assertEquals("Viscous Coupling has 1 card.", cardResult.message)
        assertEquals(1, state.teamYellowCards(VC))
        assertEquals(
            InGamePlayerCardRecord("17", yellows = 1),
            state.playerCards(VC).single { it.jerseyNumber == "17" },
        )

        cardResult = state.assessBlueCard(VC)
        state = cardResult.state
        assertFalse(cardResult.needsLivePointMisconductChoice)
        assertEquals("Viscous Coupling has 2 cards.", cardResult.message)
        assertEquals(1, state.teamOne.blueCards)

        // Viscous Coupling reaches three team card points with a yellow on #8 during a live point.
        // Since the app cannot infer possession, the model reports that a misconduct choice is needed.
        cardResult = state.assessYellowCard(VC, "8")
        state = cardResult.state
        assertTrue(cardResult.needsLivePointMisconductChoice)
        assertEquals("Viscous Coupling has 3 cards.", cardResult.message)
        assertEquals(2, state.teamYellowCards(VC))
        assertEquals("Undo Yellow Card on Viscous Coupling #8", state.undoEntry?.label)
        assertEquals(
            InGamePlayerCardRecord("8", yellows = 1),
            state.playerCards(VC).single { it.jerseyNumber == "8" },
        )
        assertTrue(
            livePointMisconductResolutionMessage(cardResult.message, againstOffense = true)
                .contains("Reverse brick"),
        )

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
        state = state.recordOffsides()
        assertEquals(1, state.teamOne.offsides)
        assertEquals(0, state.teamTwo.offsides)
        assertEquals(LivePhase.LIVE_POINT, state.phase)
        assertNull(state.countdown)
        assertEquals("Start at brick mark", state.offsidesResolutionMessage(VC))
        assertEquals("Undo Offsides on Viscous Coupling", state.undoEntry?.label)

        // Animal picks up yellow cards for #23 and #8
        cardResult = state.assessYellowCard(ANIMAL, "23")
        state = cardResult.state
        assertEquals("Animal has 1 card.", cardResult.message)
        assertEquals(1, state.teamYellowCards(ANIMAL))
        assertEquals(
            InGamePlayerCardRecord("23", yellows = 1),
            state.playerCards(ANIMAL).single { it.jerseyNumber == "23" },
        )

        cardResult = state.assessYellowCard(ANIMAL, "8")
        state = cardResult.state
        assertEquals("Animal has 2 cards.", cardResult.message)
        assertEquals(2, state.teamYellowCards(ANIMAL))
        assertEquals(
            InGamePlayerCardRecord("8", yellows = 1),
            state.playerCards(ANIMAL).single { it.jerseyNumber == "8" },
        )

        // Animal picks up two technical fouls during the live point.
        var technicalFoulResult = state.assessTechnicalFoul(ANIMAL)
        state = technicalFoulResult.state
        assertFalse(technicalFoulResult.needsLivePointMisconductChoice)
        assertEquals("Animal has 1 technical foul.", technicalFoulResult.message)

        technicalFoulResult = state.assessTechnicalFoul(ANIMAL)
        state = technicalFoulResult.state
        assertFalse(technicalFoulResult.needsLivePointMisconductChoice)
        assertEquals("Animal has 2 technical fouls.", technicalFoulResult.message)

        // Viscous Coupling calls a live-point timeout, starting an offense-set countdown.
        val secondTimeoutTime = timestampAt(state, LocalTime.of(10, 6))
        val secondTimeout = state.assessTimeout(VC, secondTimeoutTime)
        assertNull(secondTimeout.message)
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
        assertFalse(technicalFoulResult.needsLivePointMisconductChoice)
        assertEquals(3, state.teamTwo.technicalFouls)
        assertTrue(technicalFoulResult.message.contains("Animal has 3 technical fouls."))
        assertTrue(technicalFoulResult.message.contains("Penalty against pulling team."))
        assertTrue(technicalFoulResult.message.contains("Receiving team starts at attacking brick."))
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
        assertNull(thirdTimeout.message)
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
        assertEquals(LocalTime.of(10, 50), state.endTime)
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

    // Test timeout rules and timeout state transitions across both halves.
    // Cover ordinary rules, floater rules, no-timeout rules, and midgame rule updates.
    @Test
    fun timeouts() {
        val VC = TeamId.TEAM_ONE
        val ANIMAL = TeamId.TEAM_TWO

        fun setupWithRules(
            rules: GameRules,
            pullingFromEnd: FieldEnd = FieldEnd.FAR,
        ): GameSetupState {
            return standardGameSetup(
                startTime = LocalTime.of(9, 0),
                rules = rules,
                pullingTeam = VC,
                pullingFromEnd = pullingFromEnd,
            )
        }

        fun scoreToHalftime(
            startingState: LiveGameState,
            scoringTeam: TeamId,
            start: Long,
        ): LiveGameState {
            var current = startingState
            var pointNumber = 0
            while (current.phase != LivePhase.HALFTIME) {
                current = current.recordGoalFromCurrentState(
                    scoringTeam,
                    now = start + pointNumber * 10_000L,
                )
                pointNumber += 1
            }
            return current
        }

        // Start with the normal case of two timeouts per half.
        var state = createLiveGameState(
            setupWithRules(
                GameRules(
                    gameTo = 5,
                    useHalfCap = false,
                    useSoftCap = false,
                    useHardCap = false,
                    timeoutsPerHalf = 2,
                    hasFloaterTimeout = false,
                )
            )
        )
        assertEquals(0, state.teamOne.timeoutsUsedThisHalf)
        assertEquals(2, state.timeoutsAllowedThisHalf(VC))
        assertEquals(2, state.timeoutsRemaining(VC))
        assertEquals(0, state.teamTwo.timeoutsUsedThisHalf)
        assertEquals(2, state.timeoutsAllowedThisHalf(ANIMAL))
        assertEquals(2, state.timeoutsRemaining(ANIMAL))

        // A between-points timeout records a used timeout and extends the active countdown.
        val originalCountdown = state.countdown!!
        var timeoutResult = state.assessTimeout(VC, originalCountdown.targetEpoch - 1_000L)
        assertNull(timeoutResult.message)
        state = timeoutResult.state
        assertEquals(1, state.teamOne.timeoutsUsedThisHalf)
        assertEquals(1, state.timeoutsRemaining(VC))
        assertEquals("Signal in", state.countdown?.label)
        assertEquals(130, state.countdown?.durationSeconds)
        assertEquals(originalCountdown.targetEpoch + 70_000L, state.countdown?.targetEpoch)
        assertEquals("Undo Timeout by Viscous Coupling", state.undoEntry?.label)

        // A live-point timeout starts a fresh offense-set timeout countdown.
        state = state.beginLivePoint()
        timeoutResult = state.assessTimeout(VC, 1_000_000L)
        assertNull(timeoutResult.message)
        state = timeoutResult.state
        assertEquals(2, state.teamOne.timeoutsUsedThisHalf)
        assertEquals(0, state.timeoutsRemaining(VC))
        assertEquals(LivePhase.LIVE_POINT, state.phase)
        assertEquals(CountdownKind.TIME_OUT, state.countdown?.kind)
        assertEquals("Offense set in", state.countdown?.label)
        assertEquals(70, state.countdown?.durationSeconds)
        assertEquals(1_070_000L, state.countdown?.targetEpoch)

        // Once the live-point timeout countdown expires, the model automatically continues the point.
        assertEquals(state, state.advanceGameClock(1_070_000L - 1L))
        state = state.advanceGameClock(1_070_000L)
        assertEquals(LivePhase.LIVE_POINT, state.phase)
        assertNull(state.countdown)
        assertEquals("Point continued.", state.lastEvent)
        assertEquals("Undo Timeout by Viscous Coupling", state.undoEntry?.label)

        // With both first-half timeouts used, another timeout request leaves state unchanged and returns a message.
        timeoutResult = state.assessTimeout(VC, 1_010_000L)
        assertEquals("Viscous Coupling is out of timeouts.", timeoutResult.message)
        assertEquals(state, timeoutResult.state)
        assertEquals(state, state.chargeTimeout(VC, 1_010_000L))

        // In the ordinary two-per-half rules, both teams return to two timeouts at halftime.
        state = scoreToHalftime(state, VC, 1_100_000L)
        assertEquals(LivePhase.HALFTIME, state.phase)
        assertEquals(2, state.teamOne.firstHalfTimeoutsUsed)
        assertEquals(0, state.teamOne.timeoutsUsedThisHalf)
        assertEquals(2, state.timeoutsAllowedThisHalf(VC))
        assertEquals(2, state.timeoutsRemaining(VC))
        assertEquals(0, state.teamTwo.firstHalfTimeoutsUsed)
        assertEquals(0, state.teamTwo.timeoutsUsedThisHalf)
        assertEquals(2, state.timeoutsAllowedThisHalf(ANIMAL))
        assertEquals(2, state.timeoutsRemaining(ANIMAL))

        // A timeout is not available while the halftime countdown itself is still running.
        val halftimeEnd = state.countdown!!.targetEpoch
        timeoutResult = state.assessTimeout(VC, halftimeEnd - 1L)
        assertEquals("Timeouts are not available now.", timeoutResult.message)
        assertEquals(state, timeoutResult.state)

        // The UI hides timeout actions after game over; stale timeout commands are idempotent no-ops.
        val gameOverTimeoutState = standardLiveGameState(
            rules = GameRules(gameTo = 1, useHalfCap = false, useSoftCap = false, useHardCap = false),
        ).recordGoalFromCurrentState(
            VC,
            1_150_000L,
        )
        assertEquals(LivePhase.GAME_OVER, gameOverTimeoutState.phase)
        timeoutResult = gameOverTimeoutState.assessTimeout(VC, 1_160_000L)
        assertEquals("Timeouts are not available now.", timeoutResult.message)
        assertEquals(gameOverTimeoutState, timeoutResult.state)
        assertEquals(gameOverTimeoutState, gameOverTimeoutState.chargeTimeout(VC, 1_160_000L))

        // After halftime has elapsed but before the pull, a timeout behaves like a between-points timeout.
        timeoutResult = state.assessTimeout(VC, halftimeEnd + 1L)
        assertNull(timeoutResult.message)
        val afterHalftimeTimeoutState = timeoutResult.state
        assertEquals(LivePhase.BETWEEN_POINTS, afterHalftimeTimeoutState.phase)
        assertEquals(1, afterHalftimeTimeoutState.teamOne.timeoutsUsedThisHalf)
        assertEquals(1, afterHalftimeTimeoutState.timeoutsRemaining(VC))
        assertEquals("Signal in", afterHalftimeTimeoutState.countdown?.label)
        assertEquals(130, afterHalftimeTimeoutState.countdown?.durationSeconds)
        assertEquals(halftimeEnd + 130_000L, afterHalftimeTimeoutState.countdown?.targetEpoch)

        // When the pull countdown expires, the model automatically moves into live-point state.
        val expiredPullState = createLiveGameState(setupWithRules(GameRules(useHalfCap = false)))
        val expiredCountdownNow = expiredPullState.countdown!!.targetEpoch + 1L
        val advancedPullState = expiredPullState.advanceGameClock(expiredCountdownNow)
        assertEquals(LivePhase.LIVE_POINT, advancedPullState.phase)
        assertNull(advancedPullState.countdown)
        assertEquals("Point is live.", advancedPullState.lastEvent)
        assertNull(advancedPullState.undoEntry)

        // A timeout after the pull countdown has expired is therefore a live-point timeout, not a pull restart.
        timeoutResult = expiredPullState.assessTimeout(
            ANIMAL,
            expiredCountdownNow,
        )
        val expiredTimeoutState = timeoutResult.state
        assertNull(timeoutResult.message)
        assertEquals(LivePhase.LIVE_POINT, expiredTimeoutState.phase)
        assertEquals(CountdownKind.TIME_OUT, expiredTimeoutState.countdown?.kind)
        assertEquals("Offense set in", expiredTimeoutState.countdown?.label)
        assertEquals(70, expiredTimeoutState.countdown?.durationSeconds)
        assertEquals(expiredCountdownNow + 70_000L, expiredTimeoutState.countdown?.targetEpoch)

        // With one timeout per half plus a floater, using both first-half timeouts means no floater carries over.
        state = createLiveGameState(
            setupWithRules(
                GameRules(
                    gameTo = 5,
                    useHalfCap = false,
                    useSoftCap = false,
                    useHardCap = false,
                    timeoutsPerHalf = 1,
                    hasFloaterTimeout = true,
                )
            )
        )
        assertEquals(0, state.teamOne.timeoutsUsedThisHalf)
        assertEquals(2, state.timeoutsAllowedThisHalf(VC))
        assertEquals(2, state.timeoutsRemaining(VC))
        state = state.assessTimeout(VC, state.countdown!!.targetEpoch - 1_000L).state
        state = state.beginLivePoint()
        state = state.assessTimeout(VC, 2_000_000L).state
        state = state.continueLivePoint()
        state = scoreToHalftime(state, VC, 2_100_000L)
        assertEquals(2, state.teamOne.firstHalfTimeoutsUsed)
        assertEquals(0, state.teamOne.timeoutsUsedThisHalf)
        assertEquals(1, state.timeoutsAllowedThisHalf(VC))
        assertEquals(1, state.timeoutsRemaining(VC))
        assertEquals(0, state.teamTwo.firstHalfTimeoutsUsed)
        assertEquals(0, state.teamTwo.timeoutsUsedThisHalf)
        assertEquals(2, state.timeoutsAllowedThisHalf(ANIMAL))
        assertEquals(2, state.timeoutsRemaining(ANIMAL))

        // With one timeout per half plus a floater, using one or zero first-half timeouts carries the floater over.
        state = createLiveGameState(
            setupWithRules(
                GameRules(
                    gameTo = 5,
                    useHalfCap = false,
                    useSoftCap = false,
                    useHardCap = false,
                    timeoutsPerHalf = 1,
                    hasFloaterTimeout = true,
                )
            )
        )
        state = state.assessTimeout(VC, state.countdown!!.targetEpoch - 1_000L).state
        state = scoreToHalftime(state, VC, 2_200_000L)
        assertEquals(1, state.teamOne.firstHalfTimeoutsUsed)
        assertEquals(0, state.teamOne.timeoutsUsedThisHalf)
        assertEquals(2, state.timeoutsAllowedThisHalf(VC))
        assertEquals(2, state.timeoutsRemaining(VC))
        assertEquals(0, state.teamTwo.firstHalfTimeoutsUsed)
        assertEquals(0, state.teamTwo.timeoutsUsedThisHalf)
        assertEquals(2, state.timeoutsAllowedThisHalf(ANIMAL))
        assertEquals(2, state.timeoutsRemaining(ANIMAL))

        // Zero per half plus a floater gives one first-half timeout, and the floater carries only if unused.
        state = createLiveGameState(
            setupWithRules(
                GameRules(
                    gameTo = 5,
                    useHalfCap = false,
                    useSoftCap = false,
                    useHardCap = false,
                    timeoutsPerHalf = 0,
                    hasFloaterTimeout = true,
                )
            )
        )
        assertEquals(0, state.teamOne.timeoutsUsedThisHalf)
        assertEquals(1, state.timeoutsAllowedThisHalf(VC))
        assertEquals(1, state.timeoutsRemaining(VC))
        state = state.assessTimeout(VC, state.countdown!!.targetEpoch - 1_000L).state
        state = scoreToHalftime(state, VC, 2_300_000L)
        assertEquals(1, state.teamOne.firstHalfTimeoutsUsed)
        assertEquals(0, state.teamOne.timeoutsUsedThisHalf)
        assertEquals(0, state.timeoutsAllowedThisHalf(VC))
        assertEquals(0, state.timeoutsRemaining(VC))
        assertEquals(0, state.teamTwo.firstHalfTimeoutsUsed)
        assertEquals(0, state.teamTwo.timeoutsUsedThisHalf)
        assertEquals(1, state.timeoutsAllowedThisHalf(ANIMAL))
        assertEquals(1, state.timeoutsRemaining(ANIMAL))

        // Zero per half with no floater means timeout requests are never allowed.
        state = createLiveGameState(
            setupWithRules(
                GameRules(
                    gameTo = 5,
                    useHalfCap = false,
                    useSoftCap = false,
                    useHardCap = false,
                    timeoutsPerHalf = 0,
                    hasFloaterTimeout = false,
                )
            )
        )
        assertEquals(0, state.teamOne.timeoutsUsedThisHalf)
        assertEquals(0, state.timeoutsAllowedThisHalf(VC))
        assertEquals(0, state.timeoutsRemaining(VC))
        timeoutResult = state.assessTimeout(VC, 2_400_000L)
        assertEquals("Viscous Coupling is out of timeouts.", timeoutResult.message)
        assertEquals(state, timeoutResult.state)

        // Two per half plus a floater starts at three and carries the floater if any first-half timeout remains.
        state = createLiveGameState(
            setupWithRules(
                GameRules(
                    gameTo = 5,
                    useHalfCap = false,
                    useSoftCap = false,
                    useHardCap = false,
                    timeoutsPerHalf = 2,
                    hasFloaterTimeout = true,
                )
            )
        )
        assertEquals(0, state.teamOne.timeoutsUsedThisHalf)
        assertEquals(3, state.timeoutsAllowedThisHalf(VC))
        state = state.assessTimeout(VC, state.countdown!!.targetEpoch - 1_000L).state
        state = scoreToHalftime(state, VC, 2_500_000L)
        assertEquals(1, state.teamOne.firstHalfTimeoutsUsed)
        assertEquals(0, state.teamOne.timeoutsUsedThisHalf)
        assertEquals(3, state.timeoutsAllowedThisHalf(VC))
        assertEquals(3, state.timeoutsRemaining(VC))

        // If a team has already used more timeouts than a later rule set allows, remaining clamps to zero.
        // If the rules are then expanded again, remaining is recomputed from the same used count.
        state = createLiveGameState(
            setupWithRules(
                GameRules(
                    gameTo = 5,
                    useHalfCap = false,
                    useSoftCap = false,
                    useHardCap = false,
                    timeoutsPerHalf = 2,
                    hasFloaterTimeout = false,
                )
            )
        )
        state = state.assessTimeout(VC, state.countdown!!.targetEpoch - 1_000L).state
        state = state.beginLivePoint()
        state = state.assessTimeout(VC, 2_550_000L).state
        assertEquals(2, state.teamOne.timeoutsUsedThisHalf)
        assertEquals(0, state.timeoutsRemaining(VC))

        state = applySetupToLiveGame(
            state,
            setupWithRules(
                GameRules(
                    gameTo = 5,
                    useHalfCap = false,
                    useSoftCap = false,
                    useHardCap = false,
                    timeoutsPerHalf = 1,
                    hasFloaterTimeout = false,
                )
            ),
            2_560_000L,
        )
        assertEquals(1, state.timeoutsAllowedThisHalf(VC))
        assertEquals(2, state.teamOne.timeoutsUsedThisHalf)
        assertEquals(0, state.timeoutsRemaining(VC))

        state = applySetupToLiveGame(
            state,
            setupWithRules(
                GameRules(
                    gameTo = 5,
                    useHalfCap = false,
                    useSoftCap = false,
                    useHardCap = false,
                    timeoutsPerHalf = 3,
                    hasFloaterTimeout = false,
                )
            ),
            2_570_000L,
        )
        assertEquals(3, state.timeoutsAllowedThisHalf(VC))
        assertEquals(2, state.teamOne.timeoutsUsedThisHalf)
        assertEquals(1, state.timeoutsRemaining(VC))

        // Updating rules mid-half remaps remaining timeouts from the number already used.
        state = createLiveGameState(
            setupWithRules(
                GameRules(
                    gameTo = 5,
                    useHalfCap = false,
                    useSoftCap = false,
                    useHardCap = false,
                    timeoutsPerHalf = 2,
                    hasFloaterTimeout = false,
                )
            )
        )
        state = state.assessTimeout(VC, state.countdown!!.targetEpoch - 1_000L).state
        state = applySetupToLiveGame(
            state,
            setupWithRules(
                GameRules(
                    gameTo = 5,
                    useHalfCap = false,
                    useSoftCap = false,
                    useHardCap = false,
                    timeoutsPerHalf = 1,
                    hasFloaterTimeout = false,
                )
            ),
            2_600_000L,
        )
        assertEquals(1, state.timeoutsAllowedThisHalf(VC))
        assertEquals(1, state.teamOne.timeoutsUsedThisHalf)
        assertEquals(0, state.timeoutsRemaining(VC))
        assertEquals(0, state.teamTwo.timeoutsUsedThisHalf)
        assertEquals(1, state.timeoutsAllowedThisHalf(ANIMAL))
        assertEquals(1, state.timeoutsRemaining(ANIMAL))

        state = applySetupToLiveGame(
            state,
            setupWithRules(
                GameRules(
                    gameTo = 5,
                    useHalfCap = false,
                    useSoftCap = false,
                    useHardCap = false,
                    timeoutsPerHalf = 2,
                    hasFloaterTimeout = true,
                )
            ),
            2_700_000L,
        )
        assertEquals(3, state.timeoutsAllowedThisHalf(VC))
        assertEquals(1, state.teamOne.timeoutsUsedThisHalf)
        assertEquals(2, state.timeoutsRemaining(VC))
        assertEquals(0, state.teamTwo.timeoutsUsedThisHalf)
        assertEquals(3, state.timeoutsAllowedThisHalf(ANIMAL))
        assertEquals(3, state.timeoutsRemaining(ANIMAL))

        // Updating rules in the second half still remaps from the number used in the current half.
        state = scoreToHalftime(state, VC, 2_800_000L)
        state = state.beginLivePoint()
        state = state.assessTimeout(ANIMAL, 2_850_000L).state
        state = applySetupToLiveGame(
            state,
            setupWithRules(
                GameRules(
                    gameTo = 5,
                    useHalfCap = false,
                    useSoftCap = false,
                    useHardCap = false,
                    timeoutsPerHalf = 1,
                    hasFloaterTimeout = false,
                )
            ),
            2_900_000L,
        )
        assertEquals(0, state.teamOne.timeoutsUsedThisHalf)
        assertEquals(1, state.timeoutsAllowedThisHalf(VC))
        assertEquals(1, state.timeoutsRemaining(VC))
        assertEquals(1, state.teamTwo.timeoutsUsedThisHalf)
        assertEquals(1, state.timeoutsAllowedThisHalf(ANIMAL))
        assertEquals(0, state.timeoutsRemaining(ANIMAL))

        // In the second half, rule changes recompute the floater from first-half usage and current-half usage.
        state = createLiveGameState(
            setupWithRules(
                GameRules(
                    gameTo = 5,
                    useHalfCap = false,
                    useSoftCap = false,
                    useHardCap = false,
                    timeoutsPerHalf = 1,
                    hasFloaterTimeout = true,
                )
            )
        )
        state = state.assessTimeout(VC, state.countdown!!.targetEpoch - 1_000L).state
        state = scoreToHalftime(state, VC, 3_000_000L)
        assertEquals(1, state.teamOne.firstHalfTimeoutsUsed)
        assertEquals(0, state.teamOne.timeoutsUsedThisHalf)
        assertEquals(2, state.timeoutsAllowedThisHalf(VC))
        assertEquals(2, state.timeoutsRemaining(VC))

        state = state.beginLivePoint()
        state = state.assessTimeout(VC, 3_100_000L).state
        assertEquals(1, state.teamOne.timeoutsUsedThisHalf)
        assertEquals(1, state.timeoutsRemaining(VC))

        state = applySetupToLiveGame(
            state,
            setupWithRules(
                GameRules(
                    gameTo = 5,
                    useHalfCap = false,
                    useSoftCap = false,
                    useHardCap = false,
                    timeoutsPerHalf = 0,
                    hasFloaterTimeout = true,
                )
            ),
            3_200_000L,
        )
        assertEquals(1, state.teamOne.timeoutsUsedThisHalf)
        assertEquals(0, state.timeoutsAllowedThisHalf(VC))
        assertEquals(0, state.timeoutsRemaining(VC))

        state = applySetupToLiveGame(
            state,
            setupWithRules(
                GameRules(
                    gameTo = 5,
                    useHalfCap = false,
                    useSoftCap = false,
                    useHardCap = false,
                    timeoutsPerHalf = 2,
                    hasFloaterTimeout = true,
                )
            ),
            3_300_000L,
        )
        assertEquals(1, state.teamOne.timeoutsUsedThisHalf)
        assertEquals(3, state.timeoutsAllowedThisHalf(VC))
        assertEquals(2, state.timeoutsRemaining(VC))

        // Manual timeout correction sets the used counts directly and is undo-backed.
        val beforeTimeoutAdjustment = state
        state = state.adjustTimeouts(
            teamOneTimeoutsUsed = 4,
            teamTwoTimeoutsUsed = 1,
        )
        assertEquals(4, state.teamOne.timeoutsUsedThisHalf)
        assertEquals(3, state.timeoutsAllowedThisHalf(VC))
        assertEquals(0, state.timeoutsRemaining(VC))
        assertEquals(1, state.teamTwo.timeoutsUsedThisHalf)
        assertEquals(3, state.timeoutsAllowedThisHalf(ANIMAL))
        assertEquals(2, state.timeoutsRemaining(ANIMAL))
        assertEquals("Timeouts adjusted.", state.lastEvent)
        assertEquals("Undo Timeout Adjustment", state.undoEntry?.label)
        assertEquals(beforeTimeoutAdjustment, state.undoEntry?.previous)
    }

    // Test yellow, red, blue, and technical-foul handling from public card assessment APIs.
    // Emphasize team card points, per-player records, and misconduct-threshold messages.
    @Test
    fun cardsAndTechnicalFouls() {
        val VC = TeamId.TEAM_ONE
        val ANIMAL = TeamId.TEAM_TWO

        fun playerRecord(
            state: LiveGameState,
            team: TeamId,
            jerseyNumber: String,
        ): InGamePlayerCardRecord {
            return state.playerCards(team).single { it.jerseyNumber == jerseyNumber }
        }

        // Record a first yellow for a numbered Viscous Coupling player and verify team and player state.
        var state = standardLiveGameState()
        var cardResult = state.assessYellowCard(VC, "17")
        state = cardResult.state
        assertFalse(cardResult.needsLivePointMisconductChoice)
        assertEquals("Viscous Coupling has 1 card.", cardResult.message)
        assertEquals(1, state.teamYellowCards(VC))
        assertEquals(0, state.teamRedCards(VC))
        assertEquals(1, state.teamCardTotal(VC))
        assertEquals(InGamePlayerCardRecord("17", yellows = 1), playerRecord(state, VC, "17"))
        assertEquals("Undo Yellow Card on Viscous Coupling #17", state.undoEntry?.label)

        // A second yellow to the same player acts as a red card, but adds only one more team card point.
        cardResult = state.assessYellowCard(VC, "17")
        state = cardResult.state
        assertFalse(cardResult.needsLivePointMisconductChoice)
        assertEquals("Second yellow acts as a red card. Player 17 is ejected.\nViscous Coupling has 2 cards.", cardResult.message)
        assertEquals(2, state.teamYellowCards(VC))
        assertEquals(0, state.teamRedCards(VC))
        assertEquals(2, state.teamCardTotal(VC))
        assertEquals(InGamePlayerCardRecord("17", yellows = 2), playerRecord(state, VC, "17"))
        assertEquals("Undo Second Yellow on Viscous Coupling #17", state.undoEntry?.label)

        // A third team-card point between points gives the pulling-team misconduct field-position cue.
        cardResult = state.assessBlueCard(VC)
        state = cardResult.state
        assertFalse(cardResult.needsLivePointMisconductChoice)
        assertEquals(1, state.teamOne.blueCards)
        assertEquals(3, state.teamCardTotal(VC))
        assertEquals(1, state.playerCards(VC).size)
        assertEquals(
            "Viscous Coupling has 3 cards.\n\nPenalty against pulling team. No pull. Receiving team starts at attacking brick.",
            cardResult.message,
        )

        state = standardLiveGameState()
        state = state.copy(teamOne = state.teamOne.copy(blueCards = 2))
        cardResult = state.assessYellowCard(VC, "14")
        assertFalse(cardResult.needsLivePointMisconductChoice)
        assertEquals(
            "Viscous Coupling has 3 cards.\n\nPenalty against pulling team. No pull. Receiving team starts at attacking brick.",
            cardResult.message,
        )

        // During a live point, a standalone yellow that reaches the misconduct threshold needs an offense/defense choice.
        state = standardLiveGameState().beginLivePoint()
        state = state.assessBlueCard(VC).state
        state = state.assessBlueCard(VC).state
        cardResult = state.assessYellowCard(VC, "14")
        state = cardResult.state
        assertTrue(cardResult.needsLivePointMisconductChoice)
        assertEquals("Viscous Coupling has 3 cards.", cardResult.message)
        assertEquals(3, state.teamCardTotal(VC))

        // A direct red for a player with no prior yellow counts as two team card points and records a direct red.
        state = standardLiveGameState()
        cardResult = state.assessRedCard(ANIMAL, "23", RedCardMode.DIRECT_RED)
        state = cardResult.state
        assertFalse(cardResult.needsLivePointMisconductChoice)
        assertEquals("Animal has 2 cards.", cardResult.message)
        assertEquals(0, state.teamYellowCards(ANIMAL))
        assertEquals(1, state.teamRedCards(ANIMAL))
        assertEquals(2, state.teamCardTotal(ANIMAL))
        assertEquals(InGamePlayerCardRecord("23", directReds = 1), playerRecord(state, ANIMAL, "23"))

        // During a live point, a direct red that reaches the misconduct threshold needs an offense/defense choice.
        state = standardLiveGameState().beginLivePoint()
        state = state.assessYellowCard(ANIMAL, "8").state
        cardResult = state.assessRedCard(ANIMAL, "23", RedCardMode.DIRECT_RED)
        state = cardResult.state
        assertTrue(cardResult.needsLivePointMisconductChoice)
        assertEquals("Animal has 3 cards.", cardResult.message)
        assertEquals(3, state.teamCardTotal(ANIMAL))

        // A direct red for a player who already has a yellow is distinct from recording the red as a second yellow.
        state = standardLiveGameState()
        state = state.assessYellowCard(ANIMAL, "8").state
        cardResult = state.assessRedCard(ANIMAL, "8", RedCardMode.DIRECT_RED)
        state = cardResult.state
        assertFalse(cardResult.needsLivePointMisconductChoice)
        assertEquals(1, state.teamYellowCards(ANIMAL))
        assertEquals(1, state.teamRedCards(ANIMAL))
        assertEquals(3, state.teamCardTotal(ANIMAL))
        assertEquals(InGamePlayerCardRecord("8", yellows = 1, directReds = 1), playerRecord(state, ANIMAL, "8"))
        assertEquals(
            "Animal has 3 cards.\n\nPenalty against receiving team. No pull. Disc at negative brick in defending end zone.",
            cardResult.message,
        )

        state = standardLiveGameState()
        state = state.assessYellowCard(ANIMAL, "8").state
        cardResult = state.assessRedCard(ANIMAL, "8", RedCardMode.SECOND_YELLOW)
        state = cardResult.state
        assertFalse(cardResult.needsLivePointMisconductChoice)
        assertEquals(2, state.teamYellowCards(ANIMAL))
        assertEquals(0, state.teamRedCards(ANIMAL))
        assertEquals(2, state.teamCardTotal(ANIMAL))
        assertEquals(InGamePlayerCardRecord("8", yellows = 2), playerRecord(state, ANIMAL, "8"))
        assertEquals("Second yellow acts as a red card. Player 8 is ejected.\nAnimal has 2 cards.", cardResult.message)

        // The N/A pathways distinguish same-unknown-player second yellow from a standalone yellow.
        state = standardLiveGameState()
        state = state.assessYellowCard(VC, UNKNOWN_PLAYER_NUMBER).state
        assertTrue(state.playerHasYellowThisGame(VC, UNKNOWN_PLAYER_NUMBER))
        cardResult = state.assessRedCard(VC, UNKNOWN_PLAYER_NUMBER, RedCardMode.SECOND_YELLOW)
        state = cardResult.state
        assertEquals(2, state.teamYellowCards(VC))
        assertEquals(0, state.teamRedCards(VC))
        assertEquals(InGamePlayerCardRecord(UNKNOWN_PLAYER_NUMBER, yellows = 2), playerRecord(state, VC, UNKNOWN_PLAYER_NUMBER))
        assertEquals("Second yellow acts as a red card. The player is ejected.\nViscous Coupling has 2 cards.", cardResult.message)

        state = standardLiveGameState()
        state = state.assessYellowCard(VC, UNKNOWN_PLAYER_NUMBER).state
        cardResult = state.assessStandaloneYellowCard(VC, UNKNOWN_PLAYER_NUMBER)
        state = cardResult.state
        assertEquals(2, state.teamYellowCards(VC))
        assertEquals(0, state.teamRedCards(VC))
        assertEquals(2, state.teamCardTotal(VC))
        assertFalse(cardResult.message.startsWith("Second yellow acts as a red card."))

        // Blue cards count as one team card point each and do not create per-player card records.
        state = standardLiveGameState()
        cardResult = state.assessBlueCard(ANIMAL)
        state = cardResult.state
        assertFalse(cardResult.needsLivePointMisconductChoice)
        assertEquals("Animal has 1 card.", cardResult.message)
        assertEquals(1, state.teamTwo.blueCards)
        assertEquals(1, state.teamCardTotal(ANIMAL))
        assertTrue(state.playerCards(ANIMAL).isEmpty())

        cardResult = state.assessBlueCard(ANIMAL)
        state = cardResult.state
        assertFalse(cardResult.needsLivePointMisconductChoice)
        assertEquals("Animal has 2 cards.", cardResult.message)
        assertEquals(2, state.teamTwo.blueCards)
        assertEquals(2, state.teamCardTotal(ANIMAL))

        cardResult = state.assessBlueCard(ANIMAL)
        state = cardResult.state
        assertFalse(cardResult.needsLivePointMisconductChoice)
        assertEquals(3, state.teamTwo.blueCards)
        assertEquals(3, state.teamCardTotal(ANIMAL))
        assertEquals(
            "Animal has 3 cards.\n\nPenalty against receiving team. No pull. Disc at negative brick in defending end zone.",
            cardResult.message,
        )

        cardResult = state.assessBlueCard(ANIMAL)
        state = cardResult.state
        assertFalse(cardResult.needsLivePointMisconductChoice)
        assertEquals(4, state.teamTwo.blueCards)
        assertEquals(4, state.teamCardTotal(ANIMAL))
        assertEquals(
            "Animal has 4 cards.\n\nPenalty against receiving team. No pull. Disc at negative brick in defending end zone.",
            cardResult.message,
        )

        // Technical fouls use a separate count, with the same third-and-later misconduct handling.
        state = standardLiveGameState()
        var technicalFoulResult = state.assessTechnicalFoul(ANIMAL)
        state = technicalFoulResult.state
        assertFalse(technicalFoulResult.needsLivePointMisconductChoice)
        assertEquals("Animal has 1 technical foul.", technicalFoulResult.message)
        assertEquals(1, state.teamTwo.technicalFouls)
        assertEquals(0, state.teamCardTotal(ANIMAL))

        technicalFoulResult = state.assessTechnicalFoul(ANIMAL)
        state = technicalFoulResult.state
        assertFalse(technicalFoulResult.needsLivePointMisconductChoice)
        assertEquals("Animal has 2 technical fouls.", technicalFoulResult.message)
        assertEquals(2, state.teamTwo.technicalFouls)

        technicalFoulResult = state.assessTechnicalFoul(ANIMAL)
        state = technicalFoulResult.state
        assertFalse(technicalFoulResult.needsLivePointMisconductChoice)
        assertEquals(3, state.teamTwo.technicalFouls)
        assertEquals(
            "Animal has 3 technical fouls.\n\nPenalty against receiving team. No pull. Disc at negative brick in defending end zone.",
            technicalFoulResult.message,
        )

        // After Animal scores, they are the pulling team, so the next technical foul uses the pulling-team cue.
        state = recordGoalFromCurrentStateAt(state, ANIMAL, LocalTime.of(11, 5))
        assertEquals(ANIMAL, state.pullingTeam)

        technicalFoulResult = state.assessTechnicalFoul(ANIMAL)
        state = technicalFoulResult.state
        assertFalse(technicalFoulResult.needsLivePointMisconductChoice)
        assertEquals(4, state.teamTwo.technicalFouls)
        assertEquals(
            "Animal has 4 technical fouls.\n\nPenalty against pulling team. No pull. Receiving team starts at attacking brick.",
            technicalFoulResult.message,
        )

        // During a live point, third-and-later misconduct asks for offense/defense context instead of guessing.
        state = standardLiveGameState().beginLivePoint()
        state = state.assessBlueCard(VC).state
        state = state.assessBlueCard(VC).state
        cardResult = state.assessBlueCard(VC)
        state = cardResult.state
        assertEquals(LivePhase.LIVE_POINT, state.phase)
        assertTrue(cardResult.needsLivePointMisconductChoice)
        assertEquals("Viscous Coupling has 3 cards.", cardResult.message)

        val prompt = livePointMisconductPrompt(cardResult.message)
        assertTrue(prompt.contains("Was this against the offense or defense?"))
        assertTrue(
            livePointMisconductResolutionMessage(cardResult.message, againstOffense = true)
                .contains("Reverse brick"),
        )
        assertTrue(
            livePointMisconductResolutionMessage(cardResult.message, againstOffense = false)
                .contains("Brick nearest attacking end zone"),
        )

        // Technical fouls hit the same live-point misconduct choice when Viscous Coupling reaches the threshold.
        state = standardLiveGameState().beginLivePoint()
        state = state.assessTechnicalFoul(VC).state
        state = state.assessTechnicalFoul(VC).state
        technicalFoulResult = state.assessTechnicalFoul(VC)
        state = technicalFoulResult.state
        assertEquals(LivePhase.LIVE_POINT, state.phase)
        assertTrue(technicalFoulResult.needsLivePointMisconductChoice)
        assertEquals("Viscous Coupling has 3 technical fouls.", technicalFoulResult.message)
        assertTrue(
            livePointMisconductResolutionMessage(technicalFoulResult.message, againstOffense = true)
                .contains("Reverse brick"),
        )

        // Exercise the player-card assignment helpers used by the UI reconciliation prompts.
        var cardAssignments = emptyList<InGamePlayerCardRecord>()
        cardAssignments = addPlayerCardAssignment(cardAssignments, "17", CardType.YELLOW)
        assertEquals(listOf(InGamePlayerCardRecord("17", yellows = 1)), cardAssignments)
        cardAssignments = addPlayerCardAssignment(cardAssignments, "17", CardType.RED)
        assertEquals(listOf(InGamePlayerCardRecord("17", yellows = 1, directReds = 1)), cardAssignments)
        cardAssignments = removePlayerCardAssignment(cardAssignments, "8", CardType.YELLOW)
        assertEquals(listOf(InGamePlayerCardRecord("17", yellows = 1, directReds = 1)), cardAssignments)
        cardAssignments = removePlayerCardAssignment(cardAssignments, "17", CardType.YELLOW)
        assertEquals(listOf(InGamePlayerCardRecord("17", directReds = 1)), cardAssignments)
        cardAssignments = addPlayerCardAssignment(cardAssignments, "17", CardType.YELLOW)
        cardAssignments = removePlayerCardAssignment(cardAssignments, "17", CardType.RED)
        assertEquals(listOf(InGamePlayerCardRecord("17", yellows = 1)), cardAssignments)
        cardAssignments = addPlayerCardAssignment(cardAssignments, "17", CardType.RED)
        cardAssignments = removePlayerCardAssignment(cardAssignments, "17", CardType.RED)
        assertEquals(listOf(InGamePlayerCardRecord("17", yellows = 1)), cardAssignments)
        cardAssignments = listOf(
            InGamePlayerCardRecord("17", yellows = 1),
            InGamePlayerCardRecord("8", directReds = 1),
        )
        cardAssignments = removePlayerCardAssignment(cardAssignments, "17", CardType.YELLOW)
        assertEquals(listOf(InGamePlayerCardRecord("8", directReds = 1)), cardAssignments)
        cardAssignments = addPlayerCardAssignment(cardAssignments, "23", CardType.YELLOW)
        cardAssignments = addPlayerCardAssignment(cardAssignments, "8", CardType.YELLOW)
        assertEquals(
            listOf(
                InGamePlayerCardRecord("8", yellows = 1, directReds = 1),
                InGamePlayerCardRecord("23", yellows = 1),
            ),
            cardAssignments,
        )

        assertTrue(
            canAddPlayerCardAssignment(
                emptyList(),
                "99",
                CardType.YELLOW,
            ),
        )
        assertTrue(
            canAddPlayerCardAssignment(
                listOf(InGamePlayerCardRecord("17", yellows = 1)),
                "17",
                CardType.YELLOW,
            ),
        )
        assertTrue(
            canAddPlayerCardAssignment(
                listOf(InGamePlayerCardRecord("17", yellows = 1)),
                "17",
                CardType.RED,
            ),
        )
        assertFalse(
            canAddPlayerCardAssignment(
                listOf(InGamePlayerCardRecord("17", yellows = 2)),
                "17",
                CardType.RED,
            ),
        )
        assertFalse(
            canAddPlayerCardAssignment(
                listOf(InGamePlayerCardRecord("17", directReds = 1)),
                "17",
                CardType.RED,
            ),
        )

        assertFalse(standardLiveGameState().playerHasYellowThisGame(VC, "99"))
        assertFalse(
            standardLiveGameState().copy(
                teamOnePlayerCards = listOf(InGamePlayerCardRecord("99")),
            ).playerHasYellowThisGame(
                VC,
                "99",
            )
        )

        val adjustmentStepState = standardLiveGameState().copy(
            teamOnePlayerCards = listOf(
                InGamePlayerCardRecord("17", yellows = 1),
                InGamePlayerCardRecord("23", directReds = 1),
            ),
            teamTwoPlayerCards = listOf(
                InGamePlayerCardRecord("8", yellows = 2),
            ),
        )
        assertEquals(
            listOf(
                PlayerCardAdjustmentStep(VC, CardType.YELLOW, PlayerCardAdjustmentMode.ADD),
                PlayerCardAdjustmentStep(VC, CardType.RED, PlayerCardAdjustmentMode.REMOVE),
                PlayerCardAdjustmentStep(ANIMAL, CardType.YELLOW, PlayerCardAdjustmentMode.REMOVE),
            ),
            adjustmentStepState.buildPlayerCardAdjustmentSteps(
                teamOneYellows = 2,
                teamOneReds = 0,
                teamTwoYellows = 1,
                teamTwoReds = 0,
            ),
        )
        assertEquals(
            listOf(PlayerCardRemovalCandidate("8", cardCount = 2)),
            playerCardRemovalCandidates(adjustmentStepState.teamTwoPlayerCards, CardType.YELLOW),
        )
        assertEquals(
            emptyList<PlayerCardRemovalCandidate>(),
            playerCardRemovalCandidates(adjustmentStepState.teamTwoPlayerCards, CardType.RED),
        )

        // The UI reconciliation flow should prevent invalid records; if one reaches the model anyway, fail loudly.
        val invalidPlayerCardMessage =
            "Player card records must be no cards, one yellow, second yellow, direct red, or one yellow plus direct red."
        val invalidAssignmentException = assertThrows(IllegalArgumentException::class.java) {
            addPlayerCardAssignment(
                listOf(InGamePlayerCardRecord("17", directReds = 1)),
                "17",
                CardType.RED,
            )
        }
        assertEquals(invalidPlayerCardMessage, invalidAssignmentException.message)

        val negativeCardException = assertThrows(IllegalArgumentException::class.java) {
            standardLiveGameState().adjustCardsAndTf(
                teamOneBlues = 0,
                teamOneTechnicalFouls = 0,
                teamTwoBlues = 0,
                teamTwoTechnicalFouls = 0,
                teamOnePlayerCards = listOf(InGamePlayerCardRecord("17", yellows = -1)),
                teamTwoPlayerCards = emptyList(),
            )
        }
        assertEquals("Player card records cannot have negative card counts.", negativeCardException.message)

        val negativeRedException = assertThrows(IllegalArgumentException::class.java) {
            standardLiveGameState().adjustCardsAndTf(
                teamOneBlues = 0,
                teamOneTechnicalFouls = 0,
                teamTwoBlues = 0,
                teamTwoTechnicalFouls = 0,
                teamOnePlayerCards = listOf(InGamePlayerCardRecord("17", directReds = -1)),
                teamTwoPlayerCards = emptyList(),
            )
        }
        assertEquals("Player card records cannot have negative card counts.", negativeRedException.message)

        val tooManyYellowsException = assertThrows(IllegalArgumentException::class.java) {
            standardLiveGameState().adjustCardsAndTf(
                teamOneBlues = 0,
                teamOneTechnicalFouls = 0,
                teamTwoBlues = 0,
                teamTwoTechnicalFouls = 0,
                teamOnePlayerCards = listOf(InGamePlayerCardRecord("17", yellows = 3)),
                teamTwoPlayerCards = emptyList(),
            )
        }
        assertEquals(invalidPlayerCardMessage, tooManyYellowsException.message)

        val tooManyDirectRedsException = assertThrows(IllegalArgumentException::class.java) {
            standardLiveGameState().adjustCardsAndTf(
                teamOneBlues = 0,
                teamOneTechnicalFouls = 0,
                teamTwoBlues = 0,
                teamTwoTechnicalFouls = 0,
                teamOnePlayerCards = listOf(InGamePlayerCardRecord("17", directReds = 2)),
                teamTwoPlayerCards = emptyList(),
            )
        }
        assertEquals(invalidPlayerCardMessage, tooManyDirectRedsException.message)

        val secondYellowAndDirectRedException = assertThrows(IllegalArgumentException::class.java) {
            standardLiveGameState().adjustCardsAndTf(
                teamOneBlues = 0,
                teamOneTechnicalFouls = 0,
                teamTwoBlues = 0,
                teamTwoTechnicalFouls = 0,
                teamOnePlayerCards = listOf(InGamePlayerCardRecord("17", yellows = 2, directReds = 1)),
                teamTwoPlayerCards = emptyList(),
            )
        }
        assertEquals(invalidPlayerCardMessage, secondYellowAndDirectRedException.message)

        val duplicateCardException = assertThrows(IllegalArgumentException::class.java) {
            standardLiveGameState().adjustCardsAndTf(
                teamOneBlues = 0,
                teamOneTechnicalFouls = 0,
                teamTwoBlues = 0,
                teamTwoTechnicalFouls = 0,
                teamOnePlayerCards = listOf(
                    InGamePlayerCardRecord("17", yellows = 1),
                    InGamePlayerCardRecord("17", directReds = 1),
                ),
                teamTwoPlayerCards = emptyList(),
            )
        }
        assertEquals("Player card records cannot contain duplicate player entries.", duplicateCardException.message)

        // Manual cards/TF correction clamps visible team counts and derives yellow/red totals from player records.
        val correctedTeamOnePlayerCards = listOf(
            InGamePlayerCardRecord("17", yellows = 1),
            InGamePlayerCardRecord(UNKNOWN_PLAYER_NUMBER, yellows = 1, directReds = 1),
        )
        val correctedTeamTwoPlayerCards = listOf(
            InGamePlayerCardRecord("23", directReds = 1),
        )
        val beforeCardsAdjustment = state
        state = state.adjustCardsAndTf(
            teamOneBlues = -1,
            teamOneTechnicalFouls = 3,
            teamTwoBlues = 4,
            teamTwoTechnicalFouls = -3,
            teamOnePlayerCards = correctedTeamOnePlayerCards,
            teamTwoPlayerCards = correctedTeamTwoPlayerCards,
        )
        assertEquals(2, state.teamYellowCards(VC))
        assertEquals(0, state.teamOne.blueCards)
        assertEquals(1, state.teamRedCards(VC))
        assertEquals(3, state.teamOne.technicalFouls)
        assertEquals(4, state.teamCardTotal(VC))
        assertEquals(0, state.teamYellowCards(ANIMAL))
        assertEquals(4, state.teamTwo.blueCards)
        assertEquals(1, state.teamRedCards(ANIMAL))
        assertEquals(0, state.teamTwo.technicalFouls)
        assertEquals(6, state.teamCardTotal(ANIMAL))
        assertEquals(correctedTeamOnePlayerCards, state.playerCards(VC))
        assertEquals(correctedTeamTwoPlayerCards, state.playerCards(ANIMAL))
        assertEquals("Cards and technical fouls adjusted.", state.lastEvent)
        assertEquals("Undo Cards / TF Adjustment", state.undoEntry?.label)
        assertEquals(beforeCardsAdjustment, state.undoEntry?.previous)
    }

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
        state = state.recordOffsides()
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
        assertEquals("Start at brick mark", state.offsidesResolutionMessage(VC))

        // Verify the same pull sequence cannot record a second offsides for the same team.
        val afterDuplicateOffsides = state.recordOffsides()
        assertEquals(state, afterDuplicateOffsides)

        // Mirror the offsides pathway for a pull where Animal is the pulling team.
        state = standardLiveGameState(pullingTeam = ANIMAL)
        state = state.recordOffsides()
        assertEquals(LivePhase.LIVE_POINT, state.phase)
        assertEquals(0, state.teamOne.offsides)
        assertEquals(1, state.teamTwo.offsides)
        assertEquals("Offsides on Animal.", state.lastEvent)
        assertEquals("Start at brick mark", state.offsidesResolutionMessage(ANIMAL))

        // In a fresh pull sequence, record false start and verify only the receiving team's count increments.
        state = standardLiveGameState()
        state = state.recordFalseStart()
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
        assertEquals("Defense gets to set up.", falseStartResolutionMessage())

        // The same pull sequence cannot record a second false start.
        val afterDuplicateFalseStart = state.recordFalseStart()
        assertEquals(state, afterDuplicateFalseStart)

        // Record offsides and false start on the same pull and verify both counts and both consequences apply.
        state = standardLiveGameState()
        state = state.recordOffsides()
        state = state.recordFalseStart()
        assertEquals(1, state.teamOne.offsides)
        assertEquals(0, state.teamOne.falseStarts)
        assertEquals(0, state.teamTwo.offsides)
        assertEquals(1, state.teamTwo.falseStarts)
        assertTrue(state.pullSequenceOffsidesRecorded)
        assertTrue(state.pullSequenceFalseStartRecorded)
        assertEquals("Start at brick mark", state.offsidesResolutionMessage(VC))
        assertEquals("Defense gets to set up.", falseStartResolutionMessage())

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
        state = state.recordOffsides()
        assertEquals(2, state.teamOne.offsides)
        assertEquals("Start at midfield", state.offsidesResolutionMessage(VC))

        // A previous false start by Viscous Coupling also stacks with a later Viscous Coupling offsides.
        state = standardLiveGameState(pullingTeam = ANIMAL)
        state = state.recordFalseStart()
        assertEquals(0, state.teamOne.offsides)
        assertEquals(1, state.teamOne.falseStarts)
        assertEquals(0, state.teamTwo.offsides)
        assertEquals(0, state.teamTwo.falseStarts)
        state = recordGoalFromCurrentStateAt(state, VC, LocalTime.of(12, 10))
        assertEquals(VC, state.pullingTeam)
        state = state.recordOffsides()
        assertEquals(1, state.teamOne.offsides)
        assertEquals(1, state.teamOne.falseStarts)
        assertEquals("Start at midfield", state.offsidesResolutionMessage(VC))

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

    // Test cap prompting and cap application as rule-visible state transitions.
    // Caps should become eligible only after point end and should be deterministic from supplied clock values.
    @Test
    fun caps() {
        val VC = TeamId.TEAM_ONE
        val ANIMAL = TeamId.TEAM_TWO
        val startTime = LocalTime.of(10, 0)
        val capRules = GameRules(
            gameTo = 15,
            halfCapMinutes = 10,
            softCapMinutes = 20,
            hardCapMinutes = 30,
        )

        fun newCapState(rules: GameRules = capRules): LiveGameState {
            return standardLiveGameState(startTime = startTime, rules = rules)
        }

        fun scoreAt(
            state: LiveGameState,
            scoringTeam: TeamId,
            minute: Int,
        ): LiveGameState {
            return state.recordGoalFromCurrentState(
                scoringTeam = scoringTeam,
                now = timestampAfterStart(state, minute),
            )
        }

        // Start with an ordinary first point before any cap time and verify no cap is offered.
        var state = newCapState()
        assertEquals(CapStatus("Half cap", Duration.ofMinutes(5)), state.computeNextCapStatus(timestampAfterStart(state, 5)))
        state = scoreAt(state, VC, 5)
        assertEquals(1, state.teamOne.score)
        assertEquals(0, state.teamTwo.score)
        assertNull(state.pendingCapOffer)
        assertFalse(state.halfCapApplied)

        // Score after half-cap time and verify the pending prompt is explicit and undo-backed when applied.
        state = scoreAt(state, ANIMAL, 11)
        assertEquals(1, state.teamOne.score)
        assertEquals(1, state.teamTwo.score)
        assertEquals(CapType.HALF, state.pendingCapOffer)
        assertEquals("half cap", capOfferLabel(state.pendingCapOffer!!))
        assertEquals(
            "Half cap was at 10:10 AM. Halftime target would become 2. Apply now?",
            state.capOfferExplanation(),
        )

        val beforeHalfCap = state
        state = applyPendingCapAt(state, LocalTime.of(10, 11))
        assertTrue(state.halfCapApplied)
        assertEquals(2, state.halftimeTargetScore)
        assertNull(state.pendingCapOffer)
        assertEquals("Half cap applied.", state.lastEvent)
        assertEquals("Undo Apply Half Cap", state.undoEntry?.label)
        assertEquals(beforeHalfCap, state.undoEntry?.previous)

        // The half-cap target becomes the live halftime target, so the next point starts halftime.
        state = scoreAt(state, VC, 12)
        assertEquals(2, state.teamOne.score)
        assertEquals(1, state.teamTwo.score)
        assertEquals(LivePhase.HALFTIME, state.phase)
        assertTrue(state.halftimeTaken)
        assertEquals(2, state.halftimeTargetScore)
        assertNull(state.pendingCapOffer)

        // If the observer defers a pending half cap, the offer clears but the cap is not applied.
        state = newCapState()
        state = scoreAt(state, VC, 11)
        assertEquals(CapType.HALF, state.pendingCapOffer)
        state = state.deferPendingCap()
        assertNull(state.pendingCapOffer)
        assertFalse(state.halfCapApplied)
        assertNull(state.halftimeTargetScore)
        assertEquals("Cap offer deferred.", state.lastEvent)

        // Disabled caps do not show up in the countdown helper and do not create pending offers.
        state = newCapState(
            capRules.copy(
                useHalfCap = false,
                useSoftCap = false,
                useHardCap = false,
            )
        )
        assertNull(state.computeNextCapStatus(timestampAfterStart(state, 5)))
        state = scoreAt(state, VC, 35)
        assertNull(state.pendingCapOffer)
        assertFalse(state.halfCapApplied)
        assertFalse(state.softCapApplied)
        assertFalse(state.hardCapApplied)

        // A cap after midnight should not be eligible before midnight just because its clock time is earlier.
        val lateStartDate = LocalDate.of(2026, 1, 1)
        state = standardLiveGameState(
            startDate = lateStartDate,
            startTime = LocalTime.of(23, 30),
            rules = GameRules(
                gameTo = 15,
                useHalfCap = false,
                useSoftCap = false,
                hardCapMinutes = 100,
            ),
        )
        state = state.recordGoalFromCurrentState(
            VC,
            now = timestampAt(lateStartDate, LocalTime.of(23, 50)),
        )
        assertNull(state.pendingCapOffer)
        state = state.recordGoalFromCurrentState(
            ANIMAL,
            now = timestampAt(lateStartDate.plusDays(1), LocalTime.of(1, 11)),
        )
        assertEquals(CapType.HARD, state.pendingCapOffer)

        // Soft cap can be applied independently and sets the winning score to the current higher score plus one.
        state = newCapState(capRules.copy(useHalfCap = false))
        state = scoreAt(state, VC, 5)
        state = scoreAt(state, ANIMAL, 21)
        assertEquals(1, state.teamOne.score)
        assertEquals(1, state.teamTwo.score)
        assertEquals(CapType.SOFT, state.pendingCapOffer)
        assertEquals("soft cap", capOfferLabel(state.pendingCapOffer!!))
        assertEquals(
            "Soft cap was at 10:20 AM. Winning score would become 2. Apply now?",
            state.capOfferExplanation(),
        )
        state = applyPendingCapAt(state, LocalTime.of(10, 21))
        assertTrue(state.softCapApplied)
        assertEquals(2, state.winningScore)
        assertNull(state.pendingCapOffer)
        assertEquals("Undo Apply Soft Cap", state.undoEntry?.label)
        state = scoreAt(state, VC, 22)
        assertEquals(LivePhase.GAME_OVER, state.phase)
        assertEquals(2, state.teamOne.score)
        assertEquals(1, state.teamTwo.score)
        assertEquals(2, state.winningScore)

        // Hard cap while the score is not tied ends the game immediately when applied.
        state = newCapState(capRules.copy(useHalfCap = false, useSoftCap = false))
        state = scoreAt(state, VC, 5)
        state = scoreAt(state, ANIMAL, 6)
        state = scoreAt(state, VC, 31)
        assertEquals(2, state.teamOne.score)
        assertEquals(1, state.teamTwo.score)
        assertEquals(CapType.HARD, state.pendingCapOffer)
        assertEquals("hard cap", capOfferLabel(state.pendingCapOffer!!))
        assertEquals(
            "Hard cap was at 10:30 AM. Score is not tied, so the game would end now. Apply now?",
            state.capOfferExplanation(),
        )
        state = applyPendingCapAt(state, LocalTime.of(10, 31))
        assertTrue(state.hardCapApplied)
        assertEquals(LivePhase.GAME_OVER, state.phase)
        assertEquals(LocalTime.of(10, 31), state.endTime)
        assertNull(state.countdown)
        assertNull(state.pendingCapOffer)
        assertEquals("Undo Apply Hard Cap", state.undoEntry?.label)

        // Hard cap while tied sets a one-point winning score instead of ending immediately.
        state = newCapState(capRules.copy(useHalfCap = false, useSoftCap = false))
        state = scoreAt(state, VC, 5)
        state = scoreAt(state, VC, 6)
        state = scoreAt(state, ANIMAL, 7)
        state = scoreAt(state, ANIMAL, 31)
        assertEquals(2, state.teamOne.score)
        assertEquals(2, state.teamTwo.score)
        assertEquals(CapType.HARD, state.pendingCapOffer)
        assertEquals(
            "Hard cap was at 10:30 AM. Score is tied, so one more point would be played. Apply now?",
            state.capOfferExplanation(),
        )
        state = applyPendingCapAt(state, LocalTime.of(10, 31))
        assertTrue(state.hardCapApplied)
        assertEquals(LivePhase.BETWEEN_POINTS, state.phase)
        assertEquals(3, state.winningScore)
        assertNull(state.pendingCapOffer)

        // If soft cap and halftime are both due at the same point end, halftime still starts
        // and the soft-cap prompt carries into halftime.
        state = newCapState(
            capRules.copy(
                gameTo = 5,
                halfCapMinutes = 10,
                softCapMinutes = 10,
                useHardCap = false,
            )
        )
        state = scoreAt(state, VC, 1)
        state = scoreAt(state, VC, 2)
        val beforeSoftCapHalftimeGoal = state.beginLivePoint()
        val softCapHalftimeGoalTime = timestampAfterStart(beforeSoftCapHalftimeGoal, 10)
        state = beforeSoftCapHalftimeGoal.recordGoal(VC, softCapHalftimeGoalTime)
        assertEquals(3, state.teamOne.score)
        assertEquals(0, state.teamTwo.score)
        assertEquals(LivePhase.HALFTIME, state.phase)
        assertTrue(state.halftimeTaken)
        assertEquals(CapType.SOFT, state.pendingCapOffer)
        assertEquals(CountdownKind.HALFTIME, state.countdown?.kind)
        assertEquals(420, state.countdown?.durationSeconds)
        assertEquals(softCapHalftimeGoalTime + 420_000L, state.countdown?.targetEpoch)
        assertEquals(0, state.teamOne.firstHalfTimeoutsUsed)
        assertEquals(0, state.teamOne.timeoutsUsedThisHalf)
        assertEquals(0, state.teamTwo.firstHalfTimeoutsUsed)
        assertEquals(0, state.teamTwo.timeoutsUsedThisHalf)
        assertEquals(ANIMAL, state.pullingTeam)
        assertEquals(FieldEnd.FAR, state.pullingFromEnd)
        assertEquals(ANIMAL, state.nearAttackingTeam)
        assertEquals("Halftime.", state.lastEvent)
        assertEquals("Undo Goal by Viscous Coupling", state.undoEntry?.label)
        assertEquals(beforeSoftCapHalftimeGoal, state.undoEntry?.previous)

        state = applyPendingCapAt(state, LocalTime.of(10, 10))
        assertEquals(LivePhase.HALFTIME, state.phase)
        assertTrue(state.softCapApplied)
        assertEquals(4, state.winningScore)
        assertNull(state.pendingCapOffer)

        // If hard cap and halftime are both due at the same point end, halftime starts
        // with the hard-cap prompt pending; applying the prompt can still end the game.
        state = newCapState(
            capRules.copy(
                gameTo = 5,
                useHalfCap = false,
                useSoftCap = false,
                hardCapMinutes = 10,
            )
        )
        state = scoreAt(state, VC, 1)
        state = scoreAt(state, VC, 2)
        state = scoreAt(state, VC, 10)
        assertEquals(3, state.teamOne.score)
        assertEquals(0, state.teamTwo.score)
        assertEquals(LivePhase.HALFTIME, state.phase)
        assertTrue(state.halftimeTaken)
        assertEquals(CapType.HARD, state.pendingCapOffer)
        assertEquals(
            "Hard cap is scheduled for 10:10 AM. Score is not tied, so the game would end during halftime. Apply now?",
            state.capOfferExplanation(),
        )
        state = applyPendingCapAt(state, LocalTime.of(10, 10))
        assertEquals(LivePhase.GAME_OVER, state.phase)
        assertTrue(state.hardCapApplied)
        assertEquals(LocalTime.of(10, 10), state.endTime)
        assertNull(state.pendingCapOffer)

        // Manual halftime also catches a cap that became due after the last point
        // but before the observer pressed Start Halftime.
        state = newCapState(
            capRules.copy(
                gameTo = 7,
                useHalfCap = false,
                softCapMinutes = 9,
                useHardCap = false,
            )
        )
        state = scoreAt(state, VC, 8)
        assertEquals(LivePhase.BETWEEN_POINTS, state.phase)
        assertNull(state.pendingCapOffer)
        state = state.startHalftimeNow(timestampAfterStart(state, 10))
        assertEquals(LivePhase.HALFTIME, state.phase)
        assertEquals(CapType.SOFT, state.pendingCapOffer)
        assertEquals(
            "Soft cap is scheduled for 10:09 AM. Winning score would become 2. Apply now?",
            state.capOfferExplanation(),
        )
        state = applyPendingCapAt(state, LocalTime.of(10, 10))
        assertTrue(state.softCapApplied)
        assertEquals(2, state.winningScore)
        assertNull(state.pendingCapOffer)

        // Manual halftime also catches a hard cap that became due before halftime was started.
        state = newCapState(
            capRules.copy(
                gameTo = 7,
                useHalfCap = false,
                useSoftCap = false,
                hardCapMinutes = 9,
            )
        )
        state = scoreAt(state, VC, 8)
        assertEquals(LivePhase.BETWEEN_POINTS, state.phase)
        assertNull(state.pendingCapOffer)
        state = state.startHalftimeNow(timestampAfterStart(state, 10))
        assertEquals(LivePhase.HALFTIME, state.phase)
        assertEquals(CapType.HARD, state.pendingCapOffer)
        assertEquals(
            "Hard cap is scheduled for 10:09 AM. Score is not tied, so the game would end during halftime. Apply now?",
            state.capOfferExplanation(),
        )

        // With hard cap disabled, manual halftime can catch a soft cap scheduled during halftime proper.
        state = newCapState(
            capRules.copy(
                gameTo = 7,
                useHalfCap = false,
                softCapMinutes = 12,
                useHardCap = false,
            )
        )
        state = scoreAt(state, VC, 8)
        state = state.startHalftimeNow(timestampAfterStart(state, 10))
        assertEquals(LivePhase.HALFTIME, state.phase)
        assertEquals(CapType.SOFT, state.pendingCapOffer)
        assertEquals(
            "Soft cap is scheduled for 10:12 AM. Winning score would become 2. Apply now?",
            state.capOfferExplanation(),
        )

        // Caps scheduled after halftime ends should wait for the next point rather than prompting from halftime.
        state = newCapState(
            capRules.copy(
                gameTo = 7,
                halftimeMinutes = 7,
                useHalfCap = false,
                useSoftCap = false,
                hardCapMinutes = 20,
            )
        )
        state = scoreAt(state, VC, 8)
        state = state.startHalftimeNow(timestampAfterStart(state, 10))
        assertEquals(LivePhase.HALFTIME, state.phase)
        assertNull(state.pendingCapOffer)

        state = newCapState(
            capRules.copy(
                gameTo = 7,
                halftimeMinutes = 7,
                useHalfCap = false,
                softCapMinutes = 20,
                useHardCap = false,
            )
        )
        state = scoreAt(state, VC, 8)
        state = state.startHalftimeNow(timestampAfterStart(state, 10))
        assertEquals(LivePhase.HALFTIME, state.phase)
        assertNull(state.pendingCapOffer)

        // A hard cap scheduled during halftime takes precedence over a soft cap
        // that was already due before manual halftime started.
        state = newCapState(
            capRules.copy(
                gameTo = 7,
                useHalfCap = false,
                softCapMinutes = 9,
                hardCapMinutes = 12,
            )
        )
        state = scoreAt(state, VC, 8)
        assertNull(state.pendingCapOffer)
        state = state.startHalftimeNow(timestampAfterStart(state, 10))
        assertEquals(LivePhase.HALFTIME, state.phase)
        assertEquals(CapType.HARD, state.pendingCapOffer)
        assertEquals(
            "Hard cap is scheduled for 10:12 AM. Score is not tied, so the game would end during halftime. Apply now?",
            state.capOfferExplanation(),
        )

        // Soft cap during halftime proper is applied immediately, before the next point starts.
        state = newCapState(
            capRules.copy(
                gameTo = 5,
                halftimeMinutes = 7,
                useHalfCap = false,
                softCapMinutes = 12,
                useHardCap = false,
            )
        )
        state = scoreAt(state, VC, 1)
        state = scoreAt(state, VC, 2)
        state = scoreAt(state, VC, 10)
        assertEquals(LivePhase.HALFTIME, state.phase)
        assertEquals(CapType.SOFT, state.pendingCapOffer)
        assertEquals(
            "Soft cap is scheduled for 10:12 AM. Winning score would become 4. Apply now?",
            state.capOfferExplanation(),
        )
        val halftimeCountdown = state.countdown!!
        state = applyPendingCapAt(state, LocalTime.of(10, 12))
        assertEquals(LivePhase.HALFTIME, state.phase)
        assertTrue(state.softCapApplied)
        assertEquals(4, state.winningScore)
        assertNull(state.pendingCapOffer)
        assertEquals("Soft cap applied.", state.lastEvent)

        state = state.advanceGameClock(halftimeCountdown.targetEpoch)
        assertEquals(LivePhase.BETWEEN_POINTS, state.phase)
        assertTrue(state.softCapApplied)
        assertEquals(4, state.winningScore)

        // With custom timing, if soft and hard cap both fall inside halftime, hard cap takes precedence.
        state = newCapState(
            capRules.copy(
                gameTo = 5,
                halftimeMinutes = 7,
                useHalfCap = false,
                softCapMinutes = 15,
                hardCapMinutes = 20,
            )
        )
        state = scoreAt(state, VC, 1)
        state = scoreAt(state, VC, 2)
        state = scoreAt(state, VC, 14)
        assertEquals(LivePhase.HALFTIME, state.phase)
        assertEquals(CapType.HARD, state.pendingCapOffer)
        assertEquals(
            "Hard cap is scheduled for 10:20 AM. Score is not tied, so the game would end during halftime. Apply now?",
            state.capOfferExplanation(),
        )
        state = applyPendingCapAt(state, LocalTime.of(10, 14))
        assertEquals(LivePhase.GAME_OVER, state.phase)
        assertTrue(state.hardCapApplied)
        assertFalse(state.softCapApplied)
        assertEquals(LocalTime.of(10, 14), state.endTime)
        assertNull(state.countdown)
        assertNull(state.pendingCapOffer)

        // Hard cap during halftime while tied can only happen after a manual halftime trigger.
        // If it does, hard cap means one more point after halftime, not an immediate game over.
        state = newCapState(
            capRules.copy(
                gameTo = 7,
                halftimeMinutes = 7,
                useHalfCap = false,
                useSoftCap = false,
                hardCapMinutes = 12,
            )
        )
        state = scoreAt(state, VC, 1)
        state = scoreAt(state, ANIMAL, 2)
        state = scoreAt(state, VC, 3)
        state = scoreAt(state, ANIMAL, 4)
        assertEquals(2, state.teamOne.score)
        assertEquals(2, state.teamTwo.score)
        assertNull(state.pendingCapOffer)
        state = state.startHalftimeNow(timestampAfterStart(state, 10))
        assertEquals(LivePhase.HALFTIME, state.phase)
        assertEquals(CapType.HARD, state.pendingCapOffer)
        assertEquals(
            "Hard cap is scheduled for 10:12 AM. Score is tied, so one more point would be played. Apply now?",
            state.capOfferExplanation(),
        )
        val tiedHardCapHalftimeCountdown = state.countdown
        state = applyPendingCapAt(state, LocalTime.of(10, 12))
        assertEquals(LivePhase.HALFTIME, state.phase)
        assertTrue(state.hardCapApplied)
        assertEquals(3, state.winningScore)
        assertEquals(tiedHardCapHalftimeCountdown, state.countdown)
        assertNull(state.endTime)
        assertNull(state.pendingCapOffer)

        // A cap after halftime expires but before the pull waits until the next point is complete.
        state = newCapState(
            capRules.copy(
                gameTo = 5,
                halftimeMinutes = 7,
                useHalfCap = false,
                softCapMinutes = 18,
                useHardCap = false,
            )
        )
        state = scoreAt(state, VC, 1)
        state = scoreAt(state, VC, 2)
        state = scoreAt(state, VC, 10)
        assertEquals(LivePhase.HALFTIME, state.phase)
        state = state.advanceGameClock(state.countdown!!.targetEpoch + 30_000L)
        assertEquals(LivePhase.BETWEEN_POINTS, state.phase)
        assertFalse(state.softCapApplied)
        assertNull(state.pendingCapOffer)
        state = scoreAt(state, ANIMAL, 19)
        assertEquals(CapType.SOFT, state.pendingCapOffer)
        assertFalse(state.softCapApplied)

        // Force-cap-now actions enable the selected cap and move the nominal start time.
        state = newCapState(
            capRules.copy(
                useHalfCap = false,
                useSoftCap = false,
                useHardCap = false,
            )
        )
        val halfNow = state.makeCapNow(CapType.HALF, timestampAfterStart(state, 42))
        assertTrue(halfNow.rules.useHalfCap)
        assertEquals(state.startDate, halfNow.startDate)
        assertEquals(LocalTime.of(10, 32), halfNow.startTime)
        assertEquals("Half cap set to now.", halfNow.lastEvent)
        assertEquals("Undo Half Cap Now", halfNow.undoEntry?.label)

        val softNow = state.makeCapNow(CapType.SOFT, timestampAfterStart(state, 42))
        assertTrue(softNow.rules.useSoftCap)
        assertEquals(state.startDate, softNow.startDate)
        assertEquals(LocalTime.of(10, 22), softNow.startTime)
        assertEquals("Soft cap set to now.", softNow.lastEvent)
        assertEquals("Undo Soft Cap Now", softNow.undoEntry?.label)

        val hardNow = state.makeCapNow(CapType.HARD, timestampAfterStart(state, 42))
        assertTrue(hardNow.rules.useHardCap)
        assertEquals(state.startDate, hardNow.startDate)
        assertEquals(LocalTime.of(10, 12), hardNow.startTime)
        assertEquals("Hard cap set to now.", hardNow.lastEvent)
        assertEquals("Undo Hard Cap Now", hardNow.undoEntry?.label)

        // Once the next half-cap target would equal normal halftime, half cap should not prompt.
        state = newCapState(
            capRules.copy(
                useHardCap = false,
            )
        )
        repeat(6) {
            state = scoreAt(state, VC, 1)
            state = scoreAt(state, ANIMAL, 1)
        }
        assertEquals(6, state.teamOne.score)
        assertEquals(6, state.teamTwo.score)
        assertNull(state.pendingCapOffer)
        assertEquals(CapStatus("Soft cap", Duration.ofMinutes(19)), state.computeNextCapStatus(timestampAfterStart(state, 1)))
        state = scoreAt(state, VC, 11)
        assertEquals(7, state.teamOne.score)
        assertEquals(6, state.teamTwo.score)
        assertNull(state.pendingCapOffer)
        assertFalse(state.halfCapApplied)

        state = newCapState(
            capRules.copy(
                useHardCap = false,
            )
        )
        repeat(7) {
            state = scoreAt(state, VC, 1)
        }
        repeat(3) {
            state = scoreAt(state, ANIMAL, 1)
        }
        assertEquals(7, state.teamOne.score)
        assertEquals(3, state.teamTwo.score)
        assertNull(state.pendingCapOffer)
        assertEquals(CapStatus("Soft cap", Duration.ofMinutes(19)), state.computeNextCapStatus(timestampAfterStart(state, 1)))
        state = scoreAt(state, ANIMAL, 11)
        assertEquals(7, state.teamOne.score)
        assertEquals(4, state.teamTwo.score)
        assertNull(state.pendingCapOffer)
        assertFalse(state.halfCapApplied)
    }

    // Test setup conversion and applying setup edits to a live game.
    // The setup form is public UI, but the model owns how edits reshape live state.
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
        assertEquals(setup, liveGameToSetupState(state))
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

    // Test manual correction and less-common actions that are surfaced through the Other menu.
    // These are model actions even though the menu is just one UI access path.
    @Test
    fun otherMenuModelActions() {
        val VC = TeamId.TEAM_ONE
        val ANIMAL = TeamId.TEAM_TWO

        // Adjust score and verify negative inputs are clamped, with a normal undo entry.
        var state = standardLiveGameState()
        val beforeScoreAdjustment = state
        state = state.adjustScore(teamOneScore = -2, teamTwoScore = 4)
        assertEquals(0, state.teamOne.score)
        assertEquals(4, state.teamTwo.score)
        assertEquals("Score adjusted.", state.lastEvent)
        assertEquals("Undo Score Adjustment", state.undoEntry?.label)
        assertEquals(beforeScoreAdjustment, state.undoEntry?.previous)

        // Swap field ends and verify near-attacking team, pulling end, countdown label, and undo entry.
        state = standardLiveGameState(pullingTeam = VC, pullingFromEnd = FieldEnd.FAR)
        val countdownBeforeSwapEnds = state.countdown!!
        assertEquals(VC, state.nearAttackingTeam)
        assertEquals(VC, state.pullingTeam)
        assertEquals(FieldEnd.FAR, state.pullingFromEnd)
        assertEquals("Signal in", countdownBeforeSwapEnds.label)
        assertEquals(60, countdownBeforeSwapEnds.durationSeconds)
        state = state.swapFieldEnds()
        assertEquals(ANIMAL, state.nearAttackingTeam)
        assertEquals(VC, state.pullingTeam)
        assertEquals(FieldEnd.NEAR, state.pullingFromEnd)
        assertEquals("Pull in", state.countdown?.label)
        assertEquals(80, state.countdown?.durationSeconds)
        assertEquals(countdownBeforeSwapEnds.targetEpoch + 20_000L, state.countdown?.targetEpoch)
        assertEquals("Field ends swapped.", state.lastEvent)
        assertEquals("Undo Swap Ends of Field", state.undoEntry?.label)

        // Swapping while an in-point timeout countdown is active should preserve that timeout countdown.
        state = standardLiveGameState().beginLivePoint()
        state = state.assessTimeout(VC, 100_000L).state
        val liveTimeoutCountdownBeforeSwap = state.countdown
        state = state.swapFieldEnds()
        assertEquals(CountdownKind.TIME_OUT, state.countdown?.kind)
        assertEquals(liveTimeoutCountdownBeforeSwap, state.countdown)

        // Swapping during a live point with no active countdown keeps the point live and countdown-free.
        state = standardLiveGameState().beginLivePoint()
        state = state.swapFieldEnds()
        assertEquals(LivePhase.LIVE_POINT, state.phase)
        assertNull(state.countdown)
        state = state.swapPullingTeam()
        assertEquals(LivePhase.LIVE_POINT, state.phase)
        assertNull(state.countdown)

        // Timeout-extended between-points countdowns still swap between offense-ready and pull timing.
        state = standardLiveGameState()
        state = state.assessTimeout(VC, state.countdown!!.targetEpoch - 1_000L).state
        val extendedCountdownBeforeSwap = state.countdown
        assertEquals(130, extendedCountdownBeforeSwap?.durationSeconds)
        state = state.swapPullingTeam()
        assertEquals("Pull in", state.countdown?.label)
        assertEquals(150, state.countdown?.durationSeconds)
        assertEquals(extendedCountdownBeforeSwap!!.targetEpoch + 20_000L, state.countdown?.targetEpoch)

        state = standardLiveGameState(pullingFromEnd = FieldEnd.NEAR)
        state = state.assessTimeout(VC, state.countdown!!.targetEpoch - 1_000L).state
        val extendedPullCountdownBeforeSwap = state.countdown
        assertEquals(150, extendedPullCountdownBeforeSwap?.durationSeconds)
        state = state.swapPullingTeam()
        assertEquals("Signal in", state.countdown?.label)
        assertEquals(130, state.countdown?.durationSeconds)
        assertEquals(extendedPullCountdownBeforeSwap!!.targetEpoch - 20_000L, state.countdown?.targetEpoch)

        // Swap pulling team and verify only pulling team/end changes while team field positions are preserved.
        state = standardLiveGameState(pullingTeam = VC, pullingFromEnd = FieldEnd.FAR)
        assertEquals(VC, state.nearAttackingTeam)
        assertEquals(VC, state.pullingTeam)
        assertEquals(FieldEnd.FAR, state.pullingFromEnd)
        assertEquals("Signal in", state.countdown?.label)
        state = state.swapPullingTeam()
        assertEquals(VC, state.nearAttackingTeam)
        assertEquals(ANIMAL, state.pullingTeam)
        assertEquals(FieldEnd.NEAR, state.pullingFromEnd)
        assertEquals("Pull in", state.countdown?.label)
        assertEquals("Pulling team swapped.", state.lastEvent)
        assertEquals("Undo Swap Pulling Team", state.undoEntry?.label)

        // Manually start halftime and verify second-half pull orientation, timeout reset, countdown, and undo entry.
        state = standardLiveGameState(
            rules = GameRules(
                gameTo = 7,
                halftimeMinutes = 6,
                useHalfCap = false,
                useSoftCap = false,
                useHardCap = false,
            ),
            pullingTeam = VC,
            pullingFromEnd = FieldEnd.FAR,
        )
        state = state.adjustTimeouts(teamOneTimeoutsUsed = 1, teamTwoTimeoutsUsed = 2)
        val beforeManualHalftime = state
        val manualHalftimeStartTime = timestampAt(state, LocalTime.of(11, 10))
        state = state.startHalftimeNow(manualHalftimeStartTime)
        assertEquals(LivePhase.HALFTIME, state.phase)
        assertTrue(state.halftimeTaken)
        assertEquals(1, state.teamOne.firstHalfTimeoutsUsed)
        assertEquals(0, state.teamOne.timeoutsUsedThisHalf)
        assertEquals(2, state.teamTwo.firstHalfTimeoutsUsed)
        assertEquals(0, state.teamTwo.timeoutsUsedThisHalf)
        assertEquals(ANIMAL, state.pullingTeam)
        assertEquals(FieldEnd.FAR, state.pullingFromEnd)
        assertEquals(ANIMAL, state.nearAttackingTeam)
        assertEquals(CountdownKind.HALFTIME, state.countdown?.kind)
        assertEquals(360, state.countdown?.durationSeconds)
        assertEquals(manualHalftimeStartTime + 360_000L, state.countdown?.targetEpoch)
        assertEquals("Undo Start Halftime", state.undoEntry?.label)
        assertEquals(beforeManualHalftime, state.undoEntry?.previous)

        // Swapping field ends during halftime changes field metadata but preserves the halftime clock itself.
        val halftimeCountdownBeforeSwap = state.countdown
        state = state.swapFieldEnds()
        assertEquals(CountdownKind.HALFTIME, state.countdown?.kind)
        assertEquals(halftimeCountdownBeforeSwap, state.countdown)

        // The UI hides Start Halftime outside between-points state; the model rejects those calls too.
        assertEquals(state, state.startHalftimeNow(timestampAt(state, LocalTime.of(11, 11))))
        val livePointState = standardLiveGameState().beginLivePoint()
        assertEquals(livePointState, livePointState.startHalftimeNow(timestampAt(livePointState, LocalTime.of(11, 11))))
        val gameOverState = endGameNowAt(state, LocalTime.of(11, 12))
        assertEquals(gameOverState, gameOverState.startHalftimeNow(timestampAt(gameOverState, LocalTime.of(11, 13))))

        // Manually end the game and verify end time, phase, countdown clearing, and undo entry.
        val beforeManualEnd = standardLiveGameState()
        state = endGameNowAt(beforeManualEnd, LocalTime.of(11, 40))
        assertEquals(LivePhase.GAME_OVER, state.phase)
        assertEquals(LocalTime.of(11, 40), state.endTime)
        assertNull(state.countdown)
        assertNull(state.pendingCapOffer)
        assertEquals("Game over.", state.lastEvent)
        assertEquals("Undo End Game", state.undoEntry?.label)
        assertEquals(beforeManualEnd, state.undoEntry?.previous)

        // Undo game over restores the saved live state from before End Game was applied.
        state = state.undoLastAction()
        assertEquals(beforeManualEnd, state)
    }

    // Test the undo mechanism through user-visible actions rather than private snapshots.
    // Include ordinary undo, corrections, cap application, halftime, and game-over cases.
    @Test
    fun undoMechanism() {
        val VC = TeamId.TEAM_ONE
        val ANIMAL = TeamId.TEAM_TWO

        // With no undo entry, the undo action is a no-op.
        var state = standardLiveGameState()
        assertEquals(state, state.undoLastAction())

        // Start a point and verify undo returns to the previous between-points state.
        state = standardLiveGameState()
        val beforeStartPoint = state
        state = state.beginLivePoint()
        assertEquals("Undo Start Point", state.undoEntry?.label)
        assertEquals(beforeStartPoint, state.undoLastAction())

        // Record a goal from a live point and verify undo restores the in-point state.
        val beforeLiveGoal = state
        state = recordGoalAt(state, VC, LocalTime.of(11, 5))
        assertEquals(1, state.teamOne.score)
        assertEquals("Undo Goal by Viscous Coupling", state.undoEntry?.label)
        assertEquals(beforeLiveGoal, state.undoLastAction())

        // Record a goal from between points and verify undo returns to the implicit live-point state.
        state = standardLiveGameState()
        val betweenPointsBeforeGoal = state
        state = recordGoalFromCurrentStateAt(state, VC, LocalTime.of(11, 5))
        val implicitLiveState = state.undoLastAction()
        assertEquals(LivePhase.LIVE_POINT, implicitLiveState.phase)
        assertNull(implicitLiveState.countdown)
        assertEquals(0, implicitLiveState.teamOne.score)
        assertEquals(0, implicitLiveState.teamTwo.score)
        assertEquals(betweenPointsBeforeGoal, implicitLiveState.undoLastAction())

        // Undo timeout, card, technical foul, offsides, and false-start actions.
        state = standardLiveGameState().beginLivePoint()
        val beforeTimeout = state
        state = state.assessTimeout(ANIMAL, 300_000L).state
        assertEquals(beforeTimeout, state.undoLastAction())

        state = standardLiveGameState()
        val beforeCard = state
        state = state.assessYellowCard(VC, "17").state
        assertEquals(beforeCard, state.undoLastAction())

        state = standardLiveGameState()
        val beforeTf = state
        state = state.assessTechnicalFoul(ANIMAL).state
        assertEquals(beforeTf, state.undoLastAction())

        state = standardLiveGameState()
        val beforeOffsides = state
        state = state.recordOffsides()
        assertEquals(beforeOffsides, state.undoLastAction())

        state = standardLiveGameState()
        val beforeFalseStart = state
        state = state.recordFalseStart()
        assertEquals(beforeFalseStart, state.undoLastAction())

        // Undo manual score, timeout, card/TF, and pull-infraction corrections.
        state = standardLiveGameState()
        val beforeScoreCorrection = state
        state = state.adjustScore(2, 3)
        assertEquals(beforeScoreCorrection, state.undoLastAction())

        val beforeTimeoutCorrection = standardLiveGameState()
        state = beforeTimeoutCorrection.adjustTimeouts(2, 1)
        assertEquals(beforeTimeoutCorrection, state.undoLastAction())

        val beforeCardCorrection = standardLiveGameState()
        state = beforeCardCorrection.adjustCardsAndTf(
            teamOneBlues = 1,
            teamOneTechnicalFouls = 2,
            teamTwoBlues = 3,
            teamTwoTechnicalFouls = 4,
            teamOnePlayerCards = listOf(InGamePlayerCardRecord("17", yellows = 1)),
            teamTwoPlayerCards = listOf(InGamePlayerCardRecord("23", directReds = 1)),
        )
        assertEquals(beforeCardCorrection, state.undoLastAction())

        val beforePullCorrection = standardLiveGameState()
        state = beforePullCorrection.adjustPullInfractions(1, 2, 3, 4)
        assertEquals(beforePullCorrection, state.undoLastAction())

        val beforeSetupUpdate = standardLiveGameState()
        state = applySetupToLiveGame(
            existing = beforeSetupUpdate,
            setup = liveGameToSetupState(beforeSetupUpdate).copy(
                rules = beforeSetupUpdate.rules.copy(gameTo = 17),
            ),
            now = 350_000L,
        )
        assertEquals(beforeSetupUpdate, state.undoLastAction())

        // Undo apply half cap, soft cap, hard cap, force cap now, manual halftime, and manual end game.
        state = standardLiveGameState(
            startTime = LocalTime.of(10, 0),
            rules = GameRules(gameTo = 15, halfCapMinutes = 10, useSoftCap = false, useHardCap = false),
        )
        state = state.recordGoalFromCurrentState(VC, timestampAfterStart(state, 11))
        val beforeApplyHalfCap = state
        state = applyPendingCapAt(state, LocalTime.of(10, 11))
        assertEquals(beforeApplyHalfCap, state.undoLastAction())

        state = standardLiveGameState(
            startTime = LocalTime.of(10, 0),
            rules = GameRules(gameTo = 15, useHalfCap = false, softCapMinutes = 10, useHardCap = false),
        )
        state = state.recordGoalFromCurrentState(VC, timestampAfterStart(state, 11))
        val beforeApplySoftCap = state
        state = applyPendingCapAt(state, LocalTime.of(10, 11))
        assertEquals(beforeApplySoftCap, state.undoLastAction())

        state = standardLiveGameState(
            startTime = LocalTime.of(10, 0),
            rules = GameRules(gameTo = 15, useHalfCap = false, useSoftCap = false, hardCapMinutes = 10),
        )
        state = state.recordGoalFromCurrentState(VC, timestampAfterStart(state, 11))
        val beforeApplyHardCap = state
        state = applyPendingCapAt(state, LocalTime.of(10, 11))
        assertEquals(beforeApplyHardCap, state.undoLastAction())

        val beforeForceCap = standardLiveGameState()
        state = beforeForceCap.makeCapNow(CapType.SOFT, timestampAfterStart(beforeForceCap, 30))
        assertEquals(beforeForceCap, state.undoLastAction())

        val beforeManualHalftime = standardLiveGameState(pullingFromEnd = FieldEnd.NEAR)
        state = startHalftimeNowAt(beforeManualHalftime, LocalTime.of(11, 10))
        assertEquals(beforeManualHalftime, state.undoLastAction())

        val beforeManualEnd = standardLiveGameState()
        state = endGameNowAt(beforeManualEnd, LocalTime.of(11, 20))
        assertEquals(beforeManualEnd, state.undoLastAction())

        // Verify the latest undo entry is exposed when actions are chained.
        state = standardLiveGameState().beginLivePoint()
        val afterStartPoint = state
        state = state.assessTimeout(VC, 800_000L).state
        assertEquals("Undo Timeout by Viscous Coupling", state.undoEntry?.label)
        assertEquals(afterStartPoint, state.undoLastAction())

        // Verify undo from game-over summary restores a score-ended game without undoing the score.
        state = standardLiveGameState(
            rules = GameRules(gameTo = 1, useHalfCap = false, useSoftCap = false, useHardCap = false)
        )
        val gameOverByScore = recordGoalFromCurrentStateAt(state, VC, LocalTime.of(11, 25))
        assertEquals(LivePhase.GAME_OVER, gameOverByScore.phase)
        val scoreEndedUndo = gameOverByScore.undoLastAction()
        assertEquals(LivePhase.BETWEEN_POINTS, scoreEndedUndo.phase)
        assertEquals(1, scoreEndedUndo.teamOne.score)
        assertEquals(0, scoreEndedUndo.teamTwo.score)
        assertNull(scoreEndedUndo.endTime)
        assertEquals("Viscous Coupling scored.", scoreEndedUndo.lastEvent)
        assertEquals(LivePhase.LIVE_POINT, scoreEndedUndo.undoLastAction().phase)

        // Unavailable game-over commands are idempotent no-ops; the UI normally hides these pathways.
        assertEquals(gameOverByScore, recordGoalAt(gameOverByScore, ANIMAL, LocalTime.of(11, 26)))
        assertEquals(gameOverByScore, endGameNowAt(gameOverByScore, LocalTime.of(11, 26)))

        // If the observer applies End Game again, the summary-relevant state matches the automatic game-over.
        val reappliedGameOver = endGameNowAt(scoreEndedUndo, LocalTime.of(11, 26))
        assertEquals(gameOverByScore.phase, reappliedGameOver.phase)
        assertEquals(gameOverByScore.teamOne.score, reappliedGameOver.teamOne.score)
        assertEquals(gameOverByScore.teamTwo.score, reappliedGameOver.teamTwo.score)
        assertEquals(LocalTime.of(11, 26), reappliedGameOver.endTime)
        assertEquals(gameOverByScore.countdown, reappliedGameOver.countdown)
        assertEquals(gameOverByScore.pendingCapOffer, reappliedGameOver.pendingCapOffer)
        assertEquals(gameOverByScore.lastEvent, reappliedGameOver.lastEvent)
        assertEquals(scoreEndedUndo, reappliedGameOver.undoEntry?.previous)
    }

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
            "Countdown BETWEEN_POINTS is not valid while game phase is LIVE_POINT.",
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
            "Countdown BETWEEN_POINTS is not valid while game phase is HALFTIME.",
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
