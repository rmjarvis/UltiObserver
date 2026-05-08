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

class TestGameModel {
    private fun standardGameSetup(
        startTime: LocalTime,
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
            startTime = startTime,
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
        pullingTeam: TeamId = TeamId.TEAM_ONE,
        pullingFromEnd: FieldEnd = FieldEnd.FAR,
    ): LiveGameState {
        return createLiveGameState(
            standardGameSetup(
                startTime = startTime,
                rules = rules,
                pullingTeam = pullingTeam,
                pullingFromEnd = pullingFromEnd,
            )
        )
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
        assertEquals(2, timeoutsAllowedThisHalf(state, VC))
        assertEquals(2, timeoutsRemaining(state, VC))
        assertEquals(0, state.teamTwo.timeoutsUsedThisHalf)
        assertEquals(2, timeoutsAllowedThisHalf(state, ANIMAL))
        assertEquals(2, timeoutsRemaining(state, ANIMAL))
        assertEquals(VC, state.pullingTeam)
        assertEquals(FieldEnd.NEAR, state.pullingFromEnd)
        assertEquals(ANIMAL, state.nearAttackingTeam)
        assertEquals(CountdownKind.BETWEEN_POINTS, state.countdown?.kind)
        assertEquals("Pull in", state.countdown?.label)
        assertEquals(80, state.countdown?.durationSeconds)

        // The opening pull starts the first live point and clears the initial countdown.
        state = beginLivePoint(state)
        assertEquals(LivePhase.LIVE_POINT, state.phase)
        assertNull(state.countdown)
        assertEquals("Undo Start Point", state.undoEntry?.label)

        // Animal calls a live-point timeout; the point stays live but a thrower countdown starts.
        val firstTimeout = assessTimeout(state, ANIMAL, 1_000_000L)
        assertNull(firstTimeout.message)
        state = firstTimeout.state
        assertEquals(1, state.teamTwo.timeoutsUsedThisHalf)
        assertEquals(1, timeoutsRemaining(state, ANIMAL))
        assertEquals(LivePhase.LIVE_POINT, state.phase)
        assertEquals(CountdownKind.TIME_OUT, state.countdown?.kind)
        assertEquals("Offense set in", state.countdown?.label)
        assertEquals(70, state.countdown?.durationSeconds)
        assertEquals(1_070_000L, state.countdown?.targetEpochMillis)
        assertEquals("Undo Timeout by Animal", state.undoEntry?.label)

        state = continueLivePoint(state)
        assertEquals(LivePhase.LIVE_POINT, state.phase)
        assertNull(state.countdown)

        // Viscous Coupling gets a yellow on #17, then a blue card.  No yardage penalty yet.
        var cardResult = assessYellowCard(state, VC, "17")
        state = cardResult.state
        assertFalse(cardResult.needsLivePointMisconductChoice)
        assertEquals("Viscous Coupling has 1 card.", cardResult.message)
        assertEquals(1, teamYellowCards(state, VC))
        assertEquals(
            InGamePlayerCardRecord("17", yellows = 1),
            playerCards(state, VC).single { it.jerseyNumber == "17" },
        )

        cardResult = assessBlueCard(state, VC)
        state = cardResult.state
        assertFalse(cardResult.needsLivePointMisconductChoice)
        assertEquals("Viscous Coupling has 2 cards.", cardResult.message)
        assertEquals(1, state.teamOne.blueCards)

        // Viscous Coupling reaches three team card points with a yellow on #8 during a live point.
        // Since the app cannot infer possession, the model reports that a misconduct choice is needed.
        cardResult = assessYellowCard(state, VC, "8")
        state = cardResult.state
        assertTrue(cardResult.needsLivePointMisconductChoice)
        assertEquals("Viscous Coupling has 3 cards.", cardResult.message)
        assertEquals(2, teamYellowCards(state, VC))
        assertEquals("Undo Yellow Card on Viscous Coupling #8", state.undoEntry?.label)
        assertEquals(
            InGamePlayerCardRecord("8", yellows = 1),
            playerCards(state, VC).single { it.jerseyNumber == "8" },
        )
        assertTrue(
            livePointMisconductResolutionMessage(cardResult.message, againstOffense = true)
                .contains("Reverse brick"),
        )

        // Viscous Coupling scores the first point, so they pull the next point from the far end.
        state = recordGoal(state, VC, LocalTime.of(10, 5), 1_010_000L)
        assertEquals(LivePhase.BETWEEN_POINTS, state.phase)
        assertEquals(1, state.teamOne.score)
        assertEquals(0, state.teamTwo.score)
        assertEquals(VC, state.pullingTeam)
        assertEquals(FieldEnd.FAR, state.pullingFromEnd)
        assertEquals(VC, state.nearAttackingTeam)
        assertEquals("Signal in", state.countdown?.label)
        assertEquals(60, state.countdown?.durationSeconds)
        assertEquals(1_070_000L, state.countdown?.targetEpochMillis)
        assertNull(state.pendingCapOffer)

        // During the next pull sequence, Viscous Coupling records an offsides as the pulling team.
        state = recordOffsides(state)
        assertEquals(1, state.teamOne.offsides)
        assertEquals(0, state.teamTwo.offsides)
        assertEquals(LivePhase.LIVE_POINT, state.phase)
        assertNull(state.countdown)
        assertEquals("Start at brick mark", offsidesResolutionMessage(state, VC))
        assertEquals("Undo Offsides on Viscous Coupling", state.undoEntry?.label)

        // Animal picks up yellow cards for #23 and #8
        cardResult = assessYellowCard(state, ANIMAL, "23")
        state = cardResult.state
        assertEquals("Animal has 1 card.", cardResult.message)
        assertEquals(1, teamYellowCards(state, ANIMAL))
        assertEquals(
            InGamePlayerCardRecord("23", yellows = 1),
            playerCards(state, ANIMAL).single { it.jerseyNumber == "23" },
        )

        cardResult = assessYellowCard(state, ANIMAL, "8")
        state = cardResult.state
        assertEquals("Animal has 2 cards.", cardResult.message)
        assertEquals(2, teamYellowCards(state, ANIMAL))
        assertEquals(
            InGamePlayerCardRecord("8", yellows = 1),
            playerCards(state, ANIMAL).single { it.jerseyNumber == "8" },
        )

        // Animal picks up two technical fouls during the live point.
        var technicalFoulResult = assessTechnicalFoul(state, ANIMAL)
        state = technicalFoulResult.state
        assertFalse(technicalFoulResult.needsLivePointMisconductChoice)
        assertEquals("Animal has 1 technical foul.", technicalFoulResult.message)

        technicalFoulResult = assessTechnicalFoul(state, ANIMAL)
        state = technicalFoulResult.state
        assertFalse(technicalFoulResult.needsLivePointMisconductChoice)
        assertEquals("Animal has 2 technical fouls.", technicalFoulResult.message)

        // Viscous Coupling calls a live-point timeout, starting an offense-set countdown.
        val secondTimeout = assessTimeout(state, VC, 1_020_000L)
        assertNull(secondTimeout.message)
        state = secondTimeout.state
        assertEquals(1, timeoutsRemaining(state, VC))
        assertEquals(LivePhase.LIVE_POINT, state.phase)
        assertEquals(CountdownKind.TIME_OUT, state.countdown?.kind)
        assertEquals("Offense set in", state.countdown?.label)
        assertEquals(70, state.countdown?.durationSeconds)
        assertEquals(1_090_000L, state.countdown?.targetEpochMillis)

        state = continueLivePoint(state)
        assertEquals(LivePhase.LIVE_POINT, state.phase)
        assertNull(state.countdown)

        // Animal scores next to finish the live point.
        state = recordGoal(state, ANIMAL, LocalTime.of(10, 10), 1_100_000L)
        assertEquals(LivePhase.BETWEEN_POINTS, state.phase)
        assertEquals(1, state.teamOne.score)
        assertEquals(1, state.teamTwo.score)
        assertEquals(ANIMAL, state.pullingTeam)

        // Animal reaches the technical-foul threshold between points, producing the yardage message directly.
        technicalFoulResult = assessTechnicalFoul(state, ANIMAL)
        state = technicalFoulResult.state
        assertFalse(technicalFoulResult.needsLivePointMisconductChoice)
        assertEquals(3, state.teamTwo.technicalFouls)
        assertTrue(technicalFoulResult.message.contains("Animal has 3 technical fouls."))
        assertTrue(technicalFoulResult.message.contains("Penalty against pulling team."))
        assertTrue(technicalFoulResult.message.contains("Receiving team starts at attacking brick."))
        assertEquals("Undo Technical Foul on Animal", state.undoEntry?.label)

        // Viscous Coupling scores the next two points, reaching halftime in this game-to-5 setup.
        state = recordGoalFromCurrentState(state, VC, LocalTime.of(10, 15), 1_200_000L)
        assertEquals(LivePhase.BETWEEN_POINTS, state.phase)
        assertEquals(2, state.teamOne.score)
        assertEquals(1, state.teamTwo.score)

        state = recordGoalFromCurrentState(state, VC, LocalTime.of(10, 20), 1_300_000L)
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
        assertEquals(1_720_000L, state.countdown?.targetEpochMillis)
        assertEquals(0, state.teamOne.timeoutsUsedThisHalf)
        assertEquals(2, timeoutsAllowedThisHalf(state, VC))
        assertEquals(2, timeoutsRemaining(state, VC))
        assertEquals(0, state.teamTwo.timeoutsUsedThisHalf)
        assertEquals(2, timeoutsAllowedThisHalf(state, ANIMAL))
        assertEquals(2, timeoutsRemaining(state, ANIMAL))
        assertEquals("Undo Goal by Viscous Coupling", state.undoEntry?.label)

        // After halftime, the next pull can start and should behave like a normal live point.
        state = beginLivePoint(state)
        assertEquals(LivePhase.LIVE_POINT, state.phase)
        assertNull(state.countdown)

        // Animal scores after halftime, then uses one second-half timeout before the next pull.
        state = recordGoal(state, ANIMAL, LocalTime.of(10, 30), 1_800_000L)
        assertEquals(LivePhase.BETWEEN_POINTS, state.phase)
        assertEquals(3, state.teamOne.score)
        assertEquals(2, state.teamTwo.score)

        val thirdTimeout = assessTimeout(state, ANIMAL, 1_810_000L)
        assertNull(thirdTimeout.message)
        state = thirdTimeout.state
        assertEquals(1, state.teamTwo.timeoutsUsedThisHalf)
        assertEquals(1, timeoutsRemaining(state, ANIMAL))
        assertEquals("Signal in", state.countdown?.label)
        assertEquals(130, state.countdown?.durationSeconds)

        // Animal keeps pushing after halftime and ties the game.
        state = recordGoalFromCurrentState(state, ANIMAL, LocalTime.of(10, 35), 1_900_000L)
        assertEquals(3, state.teamOne.score)
        assertEquals(3, state.teamTwo.score)
        assertEquals(LivePhase.BETWEEN_POINTS, state.phase)

        // Viscous Coupling gets one more point, but Animal answers and then wins on universe.
        state = recordGoalFromCurrentState(state, VC, LocalTime.of(10, 40), 2_000_000L)
        assertEquals(4, state.teamOne.score)
        assertEquals(3, state.teamTwo.score)
        assertEquals(LivePhase.BETWEEN_POINTS, state.phase)

        state = recordGoalFromCurrentState(state, ANIMAL, LocalTime.of(10, 45), 2_100_000L)
        assertEquals(4, state.teamOne.score)
        assertEquals(4, state.teamTwo.score)
        assertEquals(LivePhase.BETWEEN_POINTS, state.phase)

        // The final Animal goal ends the game and clears live-only timing state.
        state = recordGoalFromCurrentState(state, ANIMAL, LocalTime.of(10, 50), 2_200_000L)
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
            startMillis: Long,
        ): LiveGameState {
            var current = startingState
            var pointNumber = 0
            while (current.phase != LivePhase.HALFTIME) {
                current = recordGoalFromCurrentState(
                    current,
                    scoringTeam,
                    LocalTime.of(9, 10 + pointNumber),
                    startMillis + pointNumber * 10_000L,
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
        assertEquals(2, timeoutsAllowedThisHalf(state, VC))
        assertEquals(2, timeoutsRemaining(state, VC))
        assertEquals(0, state.teamTwo.timeoutsUsedThisHalf)
        assertEquals(2, timeoutsAllowedThisHalf(state, ANIMAL))
        assertEquals(2, timeoutsRemaining(state, ANIMAL))

        // A between-points timeout records a used timeout and extends the active countdown.
        val originalCountdown = state.countdown!!
        var timeoutResult = assessTimeout(state, VC, originalCountdown.targetEpochMillis - 1_000L)
        assertNull(timeoutResult.message)
        state = timeoutResult.state
        assertEquals(1, state.teamOne.timeoutsUsedThisHalf)
        assertEquals(1, timeoutsRemaining(state, VC))
        assertEquals("Signal in", state.countdown?.label)
        assertEquals(130, state.countdown?.durationSeconds)
        assertEquals(originalCountdown.targetEpochMillis + 70_000L, state.countdown?.targetEpochMillis)
        assertEquals("Undo Timeout by Viscous Coupling", state.undoEntry?.label)

        // A live-point timeout starts a fresh offense-set timeout countdown.
        state = beginLivePoint(state)
        timeoutResult = assessTimeout(state, VC, 1_000_000L)
        assertNull(timeoutResult.message)
        state = timeoutResult.state
        assertEquals(2, state.teamOne.timeoutsUsedThisHalf)
        assertEquals(0, timeoutsRemaining(state, VC))
        assertEquals(LivePhase.LIVE_POINT, state.phase)
        assertEquals(CountdownKind.TIME_OUT, state.countdown?.kind)
        assertEquals("Offense set in", state.countdown?.label)
        assertEquals(70, state.countdown?.durationSeconds)
        assertEquals(1_070_000L, state.countdown?.targetEpochMillis)

        // Once the live-point timeout countdown expires, the model automatically continues the point.
        assertEquals(state, advanceGameClock(state, 1_070_000L - 1L))
        state = advanceGameClock(state, 1_070_000L)
        assertEquals(LivePhase.LIVE_POINT, state.phase)
        assertNull(state.countdown)
        assertEquals("Point continued.", state.lastEvent)
        assertEquals("Undo Timeout by Viscous Coupling", state.undoEntry?.label)

        // With both first-half timeouts used, another timeout request leaves state unchanged and returns a message.
        timeoutResult = assessTimeout(state, VC, 1_010_000L)
        assertEquals("Viscous Coupling is out of timeouts.", timeoutResult.message)
        assertEquals(state, timeoutResult.state)

        // In the ordinary two-per-half rules, both teams return to two timeouts at halftime.
        state = scoreToHalftime(state, VC, 1_100_000L)
        assertEquals(LivePhase.HALFTIME, state.phase)
        assertEquals(2, state.teamOne.firstHalfTimeoutsUsed)
        assertEquals(0, state.teamOne.timeoutsUsedThisHalf)
        assertEquals(2, timeoutsAllowedThisHalf(state, VC))
        assertEquals(2, timeoutsRemaining(state, VC))
        assertEquals(0, state.teamTwo.firstHalfTimeoutsUsed)
        assertEquals(0, state.teamTwo.timeoutsUsedThisHalf)
        assertEquals(2, timeoutsAllowedThisHalf(state, ANIMAL))
        assertEquals(2, timeoutsRemaining(state, ANIMAL))

        // A timeout is not available while the halftime countdown itself is still running.
        val halftimeEndMillis = state.countdown!!.targetEpochMillis
        timeoutResult = assessTimeout(state, VC, halftimeEndMillis - 1L)
        assertEquals("Timeouts are not available now.", timeoutResult.message)
        assertEquals(state, timeoutResult.state)

        // After halftime has elapsed but before the pull, a timeout behaves like a between-points timeout.
        timeoutResult = assessTimeout(state, VC, halftimeEndMillis + 1L)
        assertNull(timeoutResult.message)
        val afterHalftimeTimeoutState = timeoutResult.state
        assertEquals(LivePhase.BETWEEN_POINTS, afterHalftimeTimeoutState.phase)
        assertEquals(1, afterHalftimeTimeoutState.teamOne.timeoutsUsedThisHalf)
        assertEquals(1, timeoutsRemaining(afterHalftimeTimeoutState, VC))
        assertEquals("Signal in", afterHalftimeTimeoutState.countdown?.label)
        assertEquals(130, afterHalftimeTimeoutState.countdown?.durationSeconds)
        assertEquals(halftimeEndMillis + 130_000L, afterHalftimeTimeoutState.countdown?.targetEpochMillis)

        // When the pull countdown expires, the model automatically moves into live-point state.
        val expiredPullState = createLiveGameState(setupWithRules(GameRules(useHalfCap = false)))
        val expiredCountdownNow = expiredPullState.countdown!!.targetEpochMillis + 1L
        val advancedPullState = advanceGameClock(expiredPullState, expiredCountdownNow)
        assertEquals(LivePhase.LIVE_POINT, advancedPullState.phase)
        assertNull(advancedPullState.countdown)
        assertEquals("Point is live.", advancedPullState.lastEvent)
        assertNull(advancedPullState.undoEntry)

        // A timeout after the pull countdown has expired is therefore a live-point timeout, not a pull restart.
        timeoutResult = assessTimeout(
            expiredPullState,
            ANIMAL,
            expiredCountdownNow,
        )
        val expiredTimeoutState = timeoutResult.state
        assertNull(timeoutResult.message)
        assertEquals(LivePhase.LIVE_POINT, expiredTimeoutState.phase)
        assertEquals(CountdownKind.TIME_OUT, expiredTimeoutState.countdown?.kind)
        assertEquals("Offense set in", expiredTimeoutState.countdown?.label)
        assertEquals(70, expiredTimeoutState.countdown?.durationSeconds)
        assertEquals(expiredCountdownNow + 70_000L, expiredTimeoutState.countdown?.targetEpochMillis)

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
        assertEquals(2, timeoutsAllowedThisHalf(state, VC))
        assertEquals(2, timeoutsRemaining(state, VC))
        state = assessTimeout(state, VC, state.countdown!!.targetEpochMillis - 1_000L).state
        state = beginLivePoint(state)
        state = assessTimeout(state, VC, 2_000_000L).state
        state = continueLivePoint(state)
        state = scoreToHalftime(state, VC, 2_100_000L)
        assertEquals(2, state.teamOne.firstHalfTimeoutsUsed)
        assertEquals(0, state.teamOne.timeoutsUsedThisHalf)
        assertEquals(1, timeoutsAllowedThisHalf(state, VC))
        assertEquals(1, timeoutsRemaining(state, VC))
        assertEquals(0, state.teamTwo.firstHalfTimeoutsUsed)
        assertEquals(0, state.teamTwo.timeoutsUsedThisHalf)
        assertEquals(2, timeoutsAllowedThisHalf(state, ANIMAL))
        assertEquals(2, timeoutsRemaining(state, ANIMAL))

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
        state = assessTimeout(state, VC, state.countdown!!.targetEpochMillis - 1_000L).state
        state = scoreToHalftime(state, VC, 2_200_000L)
        assertEquals(1, state.teamOne.firstHalfTimeoutsUsed)
        assertEquals(0, state.teamOne.timeoutsUsedThisHalf)
        assertEquals(2, timeoutsAllowedThisHalf(state, VC))
        assertEquals(2, timeoutsRemaining(state, VC))
        assertEquals(0, state.teamTwo.firstHalfTimeoutsUsed)
        assertEquals(0, state.teamTwo.timeoutsUsedThisHalf)
        assertEquals(2, timeoutsAllowedThisHalf(state, ANIMAL))
        assertEquals(2, timeoutsRemaining(state, ANIMAL))

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
        assertEquals(1, timeoutsAllowedThisHalf(state, VC))
        assertEquals(1, timeoutsRemaining(state, VC))
        state = assessTimeout(state, VC, state.countdown!!.targetEpochMillis - 1_000L).state
        state = scoreToHalftime(state, VC, 2_300_000L)
        assertEquals(1, state.teamOne.firstHalfTimeoutsUsed)
        assertEquals(0, state.teamOne.timeoutsUsedThisHalf)
        assertEquals(0, timeoutsAllowedThisHalf(state, VC))
        assertEquals(0, timeoutsRemaining(state, VC))
        assertEquals(0, state.teamTwo.firstHalfTimeoutsUsed)
        assertEquals(0, state.teamTwo.timeoutsUsedThisHalf)
        assertEquals(1, timeoutsAllowedThisHalf(state, ANIMAL))
        assertEquals(1, timeoutsRemaining(state, ANIMAL))

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
        assertEquals(0, timeoutsAllowedThisHalf(state, VC))
        assertEquals(0, timeoutsRemaining(state, VC))
        timeoutResult = assessTimeout(state, VC, 2_400_000L)
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
        assertEquals(3, timeoutsAllowedThisHalf(state, VC))
        state = assessTimeout(state, VC, state.countdown!!.targetEpochMillis - 1_000L).state
        state = scoreToHalftime(state, VC, 2_500_000L)
        assertEquals(1, state.teamOne.firstHalfTimeoutsUsed)
        assertEquals(0, state.teamOne.timeoutsUsedThisHalf)
        assertEquals(3, timeoutsAllowedThisHalf(state, VC))
        assertEquals(3, timeoutsRemaining(state, VC))

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
        state = assessTimeout(state, VC, state.countdown!!.targetEpochMillis - 1_000L).state
        state = beginLivePoint(state)
        state = assessTimeout(state, VC, 2_550_000L).state
        assertEquals(2, state.teamOne.timeoutsUsedThisHalf)
        assertEquals(0, timeoutsRemaining(state, VC))

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
        assertEquals(1, timeoutsAllowedThisHalf(state, VC))
        assertEquals(2, state.teamOne.timeoutsUsedThisHalf)
        assertEquals(0, timeoutsRemaining(state, VC))

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
        assertEquals(3, timeoutsAllowedThisHalf(state, VC))
        assertEquals(2, state.teamOne.timeoutsUsedThisHalf)
        assertEquals(1, timeoutsRemaining(state, VC))

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
        state = assessTimeout(state, VC, state.countdown!!.targetEpochMillis - 1_000L).state
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
        assertEquals(1, timeoutsAllowedThisHalf(state, VC))
        assertEquals(1, state.teamOne.timeoutsUsedThisHalf)
        assertEquals(0, timeoutsRemaining(state, VC))
        assertEquals(0, state.teamTwo.timeoutsUsedThisHalf)
        assertEquals(1, timeoutsAllowedThisHalf(state, ANIMAL))
        assertEquals(1, timeoutsRemaining(state, ANIMAL))

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
        assertEquals(3, timeoutsAllowedThisHalf(state, VC))
        assertEquals(1, state.teamOne.timeoutsUsedThisHalf)
        assertEquals(2, timeoutsRemaining(state, VC))
        assertEquals(0, state.teamTwo.timeoutsUsedThisHalf)
        assertEquals(3, timeoutsAllowedThisHalf(state, ANIMAL))
        assertEquals(3, timeoutsRemaining(state, ANIMAL))

        // Updating rules in the second half still remaps from the number used in the current half.
        state = scoreToHalftime(state, VC, 2_800_000L)
        state = beginLivePoint(state)
        state = assessTimeout(state, ANIMAL, 2_850_000L).state
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
        assertEquals(1, timeoutsAllowedThisHalf(state, VC))
        assertEquals(1, timeoutsRemaining(state, VC))
        assertEquals(1, state.teamTwo.timeoutsUsedThisHalf)
        assertEquals(1, timeoutsAllowedThisHalf(state, ANIMAL))
        assertEquals(0, timeoutsRemaining(state, ANIMAL))

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
        state = assessTimeout(state, VC, state.countdown!!.targetEpochMillis - 1_000L).state
        state = scoreToHalftime(state, VC, 3_000_000L)
        assertEquals(1, state.teamOne.firstHalfTimeoutsUsed)
        assertEquals(0, state.teamOne.timeoutsUsedThisHalf)
        assertEquals(2, timeoutsAllowedThisHalf(state, VC))
        assertEquals(2, timeoutsRemaining(state, VC))

        state = beginLivePoint(state)
        state = assessTimeout(state, VC, 3_100_000L).state
        assertEquals(1, state.teamOne.timeoutsUsedThisHalf)
        assertEquals(1, timeoutsRemaining(state, VC))

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
        assertEquals(0, timeoutsAllowedThisHalf(state, VC))
        assertEquals(0, timeoutsRemaining(state, VC))

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
        assertEquals(3, timeoutsAllowedThisHalf(state, VC))
        assertEquals(2, timeoutsRemaining(state, VC))

        // Manual timeout correction sets the used counts directly and is undo-backed.
        val beforeTimeoutAdjustment = state
        state = adjustTimeouts(
            state,
            teamOneTimeoutsUsed = 4,
            teamTwoTimeoutsUsed = 1,
        )
        assertEquals(4, state.teamOne.timeoutsUsedThisHalf)
        assertEquals(3, timeoutsAllowedThisHalf(state, VC))
        assertEquals(0, timeoutsRemaining(state, VC))
        assertEquals(1, state.teamTwo.timeoutsUsedThisHalf)
        assertEquals(3, timeoutsAllowedThisHalf(state, ANIMAL))
        assertEquals(2, timeoutsRemaining(state, ANIMAL))
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
            return playerCards(state, team).single { it.jerseyNumber == jerseyNumber }
        }

        // Record a first yellow for a numbered Viscous Coupling player and verify team and player state.
        var state = standardLiveGameState()
        var cardResult = assessYellowCard(state, VC, "17")
        state = cardResult.state
        assertFalse(cardResult.needsLivePointMisconductChoice)
        assertEquals("Viscous Coupling has 1 card.", cardResult.message)
        assertEquals(1, teamYellowCards(state, VC))
        assertEquals(0, teamRedCards(state, VC))
        assertEquals(1, teamCardTotal(state, VC))
        assertEquals(InGamePlayerCardRecord("17", yellows = 1), playerRecord(state, VC, "17"))
        assertEquals("Undo Yellow Card on Viscous Coupling #17", state.undoEntry?.label)

        // A second yellow to the same player acts as a red card, but adds only one more team card point.
        cardResult = assessYellowCard(state, VC, "17")
        state = cardResult.state
        assertFalse(cardResult.needsLivePointMisconductChoice)
        assertEquals("Second yellow acts as a red card. Player 17 is ejected.\nViscous Coupling has 2 cards.", cardResult.message)
        assertEquals(2, teamYellowCards(state, VC))
        assertEquals(0, teamRedCards(state, VC))
        assertEquals(2, teamCardTotal(state, VC))
        assertEquals(InGamePlayerCardRecord("17", yellows = 2), playerRecord(state, VC, "17"))
        assertEquals("Undo Second Yellow on Viscous Coupling #17", state.undoEntry?.label)

        // A third team-card point between points gives the pulling-team misconduct field-position cue.
        cardResult = assessBlueCard(state, VC)
        state = cardResult.state
        assertFalse(cardResult.needsLivePointMisconductChoice)
        assertEquals(1, state.teamOne.blueCards)
        assertEquals(3, teamCardTotal(state, VC))
        assertEquals(1, playerCards(state, VC).size)
        assertEquals(
            "Viscous Coupling has 3 cards.\n\nPenalty against pulling team. No pull. Receiving team starts at attacking brick.",
            cardResult.message,
        )

        // A direct red for a player with no prior yellow counts as two team card points and records a direct red.
        state = standardLiveGameState()
        cardResult = assessRedCard(state, ANIMAL, "23", RedCardMode.DIRECT_RED)
        state = cardResult.state
        assertFalse(cardResult.needsLivePointMisconductChoice)
        assertEquals("Animal has 2 cards.", cardResult.message)
        assertEquals(0, teamYellowCards(state, ANIMAL))
        assertEquals(1, teamRedCards(state, ANIMAL))
        assertEquals(2, teamCardTotal(state, ANIMAL))
        assertEquals(InGamePlayerCardRecord("23", directReds = 1), playerRecord(state, ANIMAL, "23"))

        // A direct red for a player who already has a yellow is distinct from recording the red as a second yellow.
        state = standardLiveGameState()
        state = assessYellowCard(state, ANIMAL, "8").state
        cardResult = assessRedCard(state, ANIMAL, "8", RedCardMode.DIRECT_RED)
        state = cardResult.state
        assertFalse(cardResult.needsLivePointMisconductChoice)
        assertEquals(1, teamYellowCards(state, ANIMAL))
        assertEquals(1, teamRedCards(state, ANIMAL))
        assertEquals(3, teamCardTotal(state, ANIMAL))
        assertEquals(InGamePlayerCardRecord("8", yellows = 1, directReds = 1), playerRecord(state, ANIMAL, "8"))
        assertEquals(
            "Animal has 3 cards.\n\nPenalty against receiving team. No pull. Disc at negative brick in defending end zone.",
            cardResult.message,
        )

        state = standardLiveGameState()
        state = assessYellowCard(state, ANIMAL, "8").state
        cardResult = assessRedCard(state, ANIMAL, "8", RedCardMode.SECOND_YELLOW)
        state = cardResult.state
        assertFalse(cardResult.needsLivePointMisconductChoice)
        assertEquals(2, teamYellowCards(state, ANIMAL))
        assertEquals(0, teamRedCards(state, ANIMAL))
        assertEquals(2, teamCardTotal(state, ANIMAL))
        assertEquals(InGamePlayerCardRecord("8", yellows = 2), playerRecord(state, ANIMAL, "8"))
        assertEquals("Second yellow acts as a red card. Player 8 is ejected.\nAnimal has 2 cards.", cardResult.message)

        // The N/A pathways distinguish same-unknown-player second yellow from a standalone yellow.
        state = standardLiveGameState()
        state = assessYellowCard(state, VC, UNKNOWN_PLAYER_NUMBER).state
        assertTrue(playerHasYellowThisGame(state, VC, UNKNOWN_PLAYER_NUMBER))
        cardResult = assessRedCard(state, VC, UNKNOWN_PLAYER_NUMBER, RedCardMode.SECOND_YELLOW)
        state = cardResult.state
        assertEquals(2, teamYellowCards(state, VC))
        assertEquals(0, teamRedCards(state, VC))
        assertEquals(InGamePlayerCardRecord(UNKNOWN_PLAYER_NUMBER, yellows = 2), playerRecord(state, VC, UNKNOWN_PLAYER_NUMBER))
        assertEquals("Second yellow acts as a red card. The player is ejected.\nViscous Coupling has 2 cards.", cardResult.message)

        state = standardLiveGameState()
        state = assessYellowCard(state, VC, UNKNOWN_PLAYER_NUMBER).state
        cardResult = assessStandaloneYellowCard(state, VC, UNKNOWN_PLAYER_NUMBER)
        state = cardResult.state
        assertEquals(2, teamYellowCards(state, VC))
        assertEquals(0, teamRedCards(state, VC))
        assertEquals(2, teamCardTotal(state, VC))
        assertFalse(cardResult.message.startsWith("Second yellow acts as a red card."))

        // Blue cards count as one team card point each and do not create per-player card records.
        state = standardLiveGameState()
        cardResult = assessBlueCard(state, ANIMAL)
        state = cardResult.state
        assertFalse(cardResult.needsLivePointMisconductChoice)
        assertEquals("Animal has 1 card.", cardResult.message)
        assertEquals(1, state.teamTwo.blueCards)
        assertEquals(1, teamCardTotal(state, ANIMAL))
        assertTrue(playerCards(state, ANIMAL).isEmpty())

        cardResult = assessBlueCard(state, ANIMAL)
        state = cardResult.state
        assertFalse(cardResult.needsLivePointMisconductChoice)
        assertEquals("Animal has 2 cards.", cardResult.message)
        assertEquals(2, state.teamTwo.blueCards)
        assertEquals(2, teamCardTotal(state, ANIMAL))

        cardResult = assessBlueCard(state, ANIMAL)
        state = cardResult.state
        assertFalse(cardResult.needsLivePointMisconductChoice)
        assertEquals(3, state.teamTwo.blueCards)
        assertEquals(3, teamCardTotal(state, ANIMAL))
        assertEquals(
            "Animal has 3 cards.\n\nPenalty against receiving team. No pull. Disc at negative brick in defending end zone.",
            cardResult.message,
        )

        cardResult = assessBlueCard(state, ANIMAL)
        state = cardResult.state
        assertFalse(cardResult.needsLivePointMisconductChoice)
        assertEquals(4, state.teamTwo.blueCards)
        assertEquals(4, teamCardTotal(state, ANIMAL))
        assertEquals(
            "Animal has 4 cards.\n\nPenalty against receiving team. No pull. Disc at negative brick in defending end zone.",
            cardResult.message,
        )

        // Technical fouls use a separate count, with the same third-and-later misconduct handling.
        state = standardLiveGameState()
        var technicalFoulResult = assessTechnicalFoul(state, ANIMAL)
        state = technicalFoulResult.state
        assertFalse(technicalFoulResult.needsLivePointMisconductChoice)
        assertEquals("Animal has 1 technical foul.", technicalFoulResult.message)
        assertEquals(1, state.teamTwo.technicalFouls)
        assertEquals(0, teamCardTotal(state, ANIMAL))

        technicalFoulResult = assessTechnicalFoul(state, ANIMAL)
        state = technicalFoulResult.state
        assertFalse(technicalFoulResult.needsLivePointMisconductChoice)
        assertEquals("Animal has 2 technical fouls.", technicalFoulResult.message)
        assertEquals(2, state.teamTwo.technicalFouls)

        technicalFoulResult = assessTechnicalFoul(state, ANIMAL)
        state = technicalFoulResult.state
        assertFalse(technicalFoulResult.needsLivePointMisconductChoice)
        assertEquals(3, state.teamTwo.technicalFouls)
        assertEquals(
            "Animal has 3 technical fouls.\n\nPenalty against receiving team. No pull. Disc at negative brick in defending end zone.",
            technicalFoulResult.message,
        )

        // After Animal scores, they are the pulling team, so the next technical foul uses the pulling-team cue.
        state = recordGoalFromCurrentState(state, ANIMAL, LocalTime.of(11, 5), 1_500_000L)
        assertEquals(ANIMAL, state.pullingTeam)

        technicalFoulResult = assessTechnicalFoul(state, ANIMAL)
        state = technicalFoulResult.state
        assertFalse(technicalFoulResult.needsLivePointMisconductChoice)
        assertEquals(4, state.teamTwo.technicalFouls)
        assertEquals(
            "Animal has 4 technical fouls.\n\nPenalty against pulling team. No pull. Receiving team starts at attacking brick.",
            technicalFoulResult.message,
        )

        // During a live point, third-and-later misconduct asks for offense/defense context instead of guessing.
        state = beginLivePoint(standardLiveGameState())
        state = assessBlueCard(state, VC).state
        state = assessBlueCard(state, VC).state
        cardResult = assessBlueCard(state, VC)
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
        state = beginLivePoint(standardLiveGameState())
        state = assessTechnicalFoul(state, VC).state
        state = assessTechnicalFoul(state, VC).state
        technicalFoulResult = assessTechnicalFoul(state, VC)
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
        cardAssignments = removePlayerCardAssignment(cardAssignments, "17", CardType.RED)
        assertTrue(cardAssignments.isEmpty())
        cardAssignments = listOf(
            InGamePlayerCardRecord("17", yellows = 1),
            InGamePlayerCardRecord("8", directReds = 1),
        )
        cardAssignments = removePlayerCardAssignment(cardAssignments, "17", CardType.YELLOW)
        assertEquals(listOf(InGamePlayerCardRecord("8", directReds = 1)), cardAssignments)

        // The UI reconciliation flow should prevent invalid records; if one reaches the model anyway, fail loudly.
        val negativeCardException = assertThrows(IllegalArgumentException::class.java) {
            adjustCardsAndTf(
                state = standardLiveGameState(),
                teamOneBlues = 0,
                teamOneTechnicalFouls = 0,
                teamTwoBlues = 0,
                teamTwoTechnicalFouls = 0,
                teamOnePlayerCards = listOf(InGamePlayerCardRecord("17", yellows = -1)),
                teamTwoPlayerCards = emptyList(),
            )
        }
        assertEquals("Player card records cannot have negative card counts.", negativeCardException.message)

        val duplicateCardException = assertThrows(IllegalArgumentException::class.java) {
            adjustCardsAndTf(
                state = standardLiveGameState(),
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
        state = adjustCardsAndTf(
            state = state,
            teamOneBlues = -1,
            teamOneTechnicalFouls = 3,
            teamTwoBlues = 4,
            teamTwoTechnicalFouls = -3,
            teamOnePlayerCards = correctedTeamOnePlayerCards,
            teamTwoPlayerCards = correctedTeamTwoPlayerCards,
        )
        assertEquals(2, teamYellowCards(state, VC))
        assertEquals(0, state.teamOne.blueCards)
        assertEquals(1, teamRedCards(state, VC))
        assertEquals(3, state.teamOne.technicalFouls)
        assertEquals(4, teamCardTotal(state, VC))
        assertEquals(0, teamYellowCards(state, ANIMAL))
        assertEquals(4, state.teamTwo.blueCards)
        assertEquals(1, teamRedCards(state, ANIMAL))
        assertEquals(0, state.teamTwo.technicalFouls)
        assertEquals(6, teamCardTotal(state, ANIMAL))
        assertEquals(correctedTeamOnePlayerCards, playerCards(state, VC))
        assertEquals(correctedTeamTwoPlayerCards, playerCards(state, ANIMAL))
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
        state = recordOffsides(state)
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
        assertEquals("Start at brick mark", offsidesResolutionMessage(state, VC))

        // Verify the same pull sequence cannot record a second offsides for the same team.
        val afterDuplicateOffsides = recordOffsides(state)
        assertEquals(state, afterDuplicateOffsides)

        // Mirror the offsides pathway for a pull where Animal is the pulling team.
        state = standardLiveGameState(pullingTeam = ANIMAL)
        state = recordOffsides(state)
        assertEquals(LivePhase.LIVE_POINT, state.phase)
        assertEquals(0, state.teamOne.offsides)
        assertEquals(1, state.teamTwo.offsides)
        assertEquals("Offsides on Animal.", state.lastEvent)
        assertEquals("Start at brick mark", offsidesResolutionMessage(state, ANIMAL))

        // In a fresh pull sequence, record false start and verify only the receiving team's count increments.
        state = standardLiveGameState()
        state = recordFalseStart(state)
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
        val afterDuplicateFalseStart = recordFalseStart(state)
        assertEquals(state, afterDuplicateFalseStart)

        // Record offsides and false start on the same pull and verify both counts and both consequences apply.
        state = standardLiveGameState()
        state = recordOffsides(state)
        state = recordFalseStart(state)
        assertEquals(1, state.teamOne.offsides)
        assertEquals(0, state.teamOne.falseStarts)
        assertEquals(0, state.teamTwo.offsides)
        assertEquals(1, state.teamTwo.falseStarts)
        assertTrue(state.pullSequenceOffsidesRecorded)
        assertTrue(state.pullSequenceFalseStartRecorded)
        assertEquals("Start at brick mark", offsidesResolutionMessage(state, VC))
        assertEquals("Defense gets to set up.", falseStartResolutionMessage())

        // Score the point and verify pull-sequence infraction locks reset for the next pull.
        state = recordGoalFromCurrentState(state, VC, LocalTime.of(12, 5), 1_000_000L)
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
        state = recordOffsides(state)
        assertEquals(2, state.teamOne.offsides)
        assertEquals("Start at midfield", offsidesResolutionMessage(state, VC))

        // A previous false start by Viscous Coupling also stacks with a later Viscous Coupling offsides.
        state = standardLiveGameState(pullingTeam = ANIMAL)
        state = recordFalseStart(state)
        assertEquals(0, state.teamOne.offsides)
        assertEquals(1, state.teamOne.falseStarts)
        assertEquals(0, state.teamTwo.offsides)
        assertEquals(0, state.teamTwo.falseStarts)
        state = recordGoalFromCurrentState(state, VC, LocalTime.of(12, 10), 1_100_000L)
        assertEquals(VC, state.pullingTeam)
        state = recordOffsides(state)
        assertEquals(1, state.teamOne.offsides)
        assertEquals(1, state.teamOne.falseStarts)
        assertEquals("Start at midfield", offsidesResolutionMessage(state, VC))

        // Manually adjust pull infractions and verify values are clamped and undo-backed.
        state = adjustPullInfractions(
            state,
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
            nowMillis: Long,
        ): LiveGameState {
            return recordGoalFromCurrentState(
                state = state,
                scoringTeam = scoringTeam,
                now = LocalTime.of(10, minute),
                nowMillis = nowMillis,
            )
        }

        // Start with an ordinary first point before any cap time and verify no cap is offered.
        var state = newCapState()
        assertEquals(CapStatus("Half cap", Duration.ofMinutes(5)), computeNextCapStatus(state, LocalTime.of(10, 5)))
        state = scoreAt(state, VC, 5, 1_000_000L)
        assertEquals(1, state.teamOne.score)
        assertEquals(0, state.teamTwo.score)
        assertNull(state.pendingCapOffer)
        assertFalse(state.halfCapApplied)

        // Score after half-cap time and verify the pending prompt is explicit and undo-backed when applied.
        state = scoreAt(state, ANIMAL, 11, 1_100_000L)
        assertEquals(1, state.teamOne.score)
        assertEquals(1, state.teamTwo.score)
        assertEquals(CapType.HALF, state.pendingCapOffer)
        assertEquals("half cap", capOfferLabel(state.pendingCapOffer!!))
        assertEquals(
            "Half cap was at 10:10 AM. Halftime target would become 2. Apply now?",
            capOfferExplanation(state),
        )

        val beforeHalfCap = state
        state = applyPendingCap(state, LocalTime.of(10, 11))
        assertTrue(state.halfCapApplied)
        assertEquals(2, state.halftimeTargetScore)
        assertNull(state.pendingCapOffer)
        assertEquals("Half cap applied.", state.lastEvent)
        assertEquals("Undo Apply Half Cap", state.undoEntry?.label)
        assertEquals(beforeHalfCap, state.undoEntry?.previous)

        // The half-cap target becomes the live halftime target, so the next point starts halftime.
        state = scoreAt(state, VC, 12, 1_200_000L)
        assertEquals(2, state.teamOne.score)
        assertEquals(1, state.teamTwo.score)
        assertEquals(LivePhase.HALFTIME, state.phase)
        assertTrue(state.halftimeTaken)
        assertEquals(2, state.halftimeTargetScore)
        assertNull(state.pendingCapOffer)

        // If the observer defers a pending half cap, the offer clears but the cap is not applied.
        state = newCapState()
        state = scoreAt(state, VC, 11, 1_300_000L)
        assertEquals(CapType.HALF, state.pendingCapOffer)
        state = deferPendingCap(state)
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
        assertNull(computeNextCapStatus(state, LocalTime.of(10, 5)))
        state = scoreAt(state, VC, 35, 1_400_000L)
        assertNull(state.pendingCapOffer)
        assertFalse(state.halfCapApplied)
        assertFalse(state.softCapApplied)
        assertFalse(state.hardCapApplied)

        // Soft cap can be applied independently and sets the winning score to the current higher score plus one.
        state = newCapState(capRules.copy(useHalfCap = false))
        state = scoreAt(state, VC, 5, 1_500_000L)
        state = scoreAt(state, ANIMAL, 21, 1_600_000L)
        assertEquals(1, state.teamOne.score)
        assertEquals(1, state.teamTwo.score)
        assertEquals(CapType.SOFT, state.pendingCapOffer)
        assertEquals("soft cap", capOfferLabel(state.pendingCapOffer!!))
        assertEquals(
            "Soft cap was at 10:20 AM. Winning score would become 2. Apply now?",
            capOfferExplanation(state),
        )
        state = applyPendingCap(state, LocalTime.of(10, 21))
        assertTrue(state.softCapApplied)
        assertEquals(2, state.winningScore)
        assertNull(state.pendingCapOffer)
        assertEquals("Undo Apply Soft Cap", state.undoEntry?.label)

        // Hard cap while the score is not tied ends the game immediately when applied.
        state = newCapState(capRules.copy(useHalfCap = false, useSoftCap = false))
        state = scoreAt(state, VC, 5, 1_700_000L)
        state = scoreAt(state, ANIMAL, 6, 1_800_000L)
        state = scoreAt(state, VC, 31, 1_900_000L)
        assertEquals(2, state.teamOne.score)
        assertEquals(1, state.teamTwo.score)
        assertEquals(CapType.HARD, state.pendingCapOffer)
        assertEquals("hard cap", capOfferLabel(state.pendingCapOffer!!))
        assertEquals(
            "Hard cap was at 10:30 AM. Score is not tied, so the game would end now. Apply now?",
            capOfferExplanation(state),
        )
        state = applyPendingCap(state, LocalTime.of(10, 31))
        assertTrue(state.hardCapApplied)
        assertEquals(LivePhase.GAME_OVER, state.phase)
        assertEquals(LocalTime.of(10, 31), state.endTime)
        assertNull(state.countdown)
        assertNull(state.pendingCapOffer)
        assertEquals("Undo Apply Hard Cap", state.undoEntry?.label)

        // Hard cap while tied sets a one-point winning score instead of ending immediately.
        state = newCapState(capRules.copy(useHalfCap = false, useSoftCap = false))
        state = scoreAt(state, VC, 5, 2_000_000L)
        state = scoreAt(state, VC, 6, 2_100_000L)
        state = scoreAt(state, ANIMAL, 7, 2_200_000L)
        state = scoreAt(state, ANIMAL, 31, 2_300_000L)
        assertEquals(2, state.teamOne.score)
        assertEquals(2, state.teamTwo.score)
        assertEquals(CapType.HARD, state.pendingCapOffer)
        assertEquals(
            "Hard cap was at 10:30 AM. Score is tied, so one more point would be played. Apply now?",
            capOfferExplanation(state),
        )
        state = applyPendingCap(state, LocalTime.of(10, 31))
        assertTrue(state.hardCapApplied)
        assertEquals(LivePhase.BETWEEN_POINTS, state.phase)
        assertEquals(3, state.winningScore)
        assertNull(state.pendingCapOffer)

        // If soft cap and halftime are both due at the same point end, soft cap is the relevant cap.
        state = newCapState(
            capRules.copy(
                gameTo = 5,
                halfCapMinutes = 10,
                softCapMinutes = 10,
                useHardCap = false,
            )
        )
        state = scoreAt(state, VC, 1, 2_400_000L)
        state = scoreAt(state, VC, 2, 2_500_000L)
        state = scoreAt(state, VC, 10, 2_600_000L)
        assertEquals(3, state.teamOne.score)
        assertEquals(0, state.teamTwo.score)
        assertEquals(LivePhase.BETWEEN_POINTS, state.phase)
        assertFalse(state.halftimeTaken)
        assertEquals(CapType.SOFT, state.pendingCapOffer)

        // The observer can still manually start halftime from Other when teams choose to take half anyway.
        val beforeManualHalftime = state
        state = startHalftimeNow(state, LocalTime.of(10, 10), 2_610_000L)
        assertEquals(LivePhase.HALFTIME, state.phase)
        assertTrue(state.halftimeTaken)
        assertEquals(CapType.SOFT, state.pendingCapOffer)
        assertEquals(CountdownKind.HALFTIME, state.countdown?.kind)
        assertEquals(420, state.countdown?.durationSeconds)
        assertEquals(3_030_000L, state.countdown?.targetEpochMillis)
        assertEquals(0, state.teamOne.firstHalfTimeoutsUsed)
        assertEquals(0, state.teamOne.timeoutsUsedThisHalf)
        assertEquals(0, state.teamTwo.firstHalfTimeoutsUsed)
        assertEquals(0, state.teamTwo.timeoutsUsedThisHalf)
        assertEquals(ANIMAL, state.pullingTeam)
        assertEquals(FieldEnd.FAR, state.pullingFromEnd)
        assertEquals(ANIMAL, state.nearAttackingTeam)
        assertEquals("Halftime.", state.lastEvent)
        assertEquals("Undo Start Halftime", state.undoEntry?.label)
        assertEquals(beforeManualHalftime, state.undoEntry?.previous)

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
        state = scoreAt(state, VC, 1, 2_700_000L)
        state = scoreAt(state, VC, 2, 2_800_000L)
        state = scoreAt(state, VC, 10, 3_000_000L)
        assertEquals(LivePhase.HALFTIME, state.phase)
        assertEquals(CapType.SOFT, state.pendingCapOffer)
        assertEquals(
            "Soft cap is scheduled for 10:12 AM. Winning score would become 4. Apply now?",
            capOfferExplanation(state),
        )
        val halftimeCountdown = state.countdown!!
        state = applyPendingCap(state, LocalTime.of(10, 12))
        assertEquals(LivePhase.HALFTIME, state.phase)
        assertTrue(state.softCapApplied)
        assertEquals(4, state.winningScore)
        assertNull(state.pendingCapOffer)
        assertEquals("Soft cap applied.", state.lastEvent)

        state = advanceGameClock(state, halftimeCountdown.targetEpochMillis)
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
        state = scoreAt(state, VC, 1, 3_100_000L)
        state = scoreAt(state, VC, 2, 3_200_000L)
        state = scoreAt(state, VC, 14, 3_300_000L)
        assertEquals(LivePhase.HALFTIME, state.phase)
        assertEquals(CapType.HARD, state.pendingCapOffer)
        assertEquals(
            "Hard cap is scheduled for 10:20 AM. Score is not tied, so the game would end during halftime. Apply now?",
            capOfferExplanation(state),
        )
        state = applyPendingCap(state, LocalTime.of(10, 14))
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
        state = scoreAt(state, VC, 1, 2_350_000L)
        state = scoreAt(state, ANIMAL, 2, 2_360_000L)
        state = scoreAt(state, VC, 3, 2_370_000L)
        state = scoreAt(state, ANIMAL, 4, 2_380_000L)
        assertEquals(2, state.teamOne.score)
        assertEquals(2, state.teamTwo.score)
        assertNull(state.pendingCapOffer)
        state = startHalftimeNow(state, LocalTime.of(10, 10), 2_390_000L)
        assertEquals(LivePhase.HALFTIME, state.phase)
        assertEquals(CapType.HARD, state.pendingCapOffer)
        assertEquals(
            "Hard cap is scheduled for 10:12 AM. Score is tied, so one more point would be played. Apply now?",
            capOfferExplanation(state),
        )
        val tiedHardCapHalftimeCountdown = state.countdown
        state = applyPendingCap(state, LocalTime.of(10, 12))
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
        state = scoreAt(state, VC, 1, 3_500_000L)
        state = scoreAt(state, VC, 2, 3_600_000L)
        state = scoreAt(state, VC, 10, 3_700_000L)
        assertEquals(LivePhase.HALFTIME, state.phase)
        state = advanceGameClock(state, state.countdown!!.targetEpochMillis + 30_000L)
        assertEquals(LivePhase.BETWEEN_POINTS, state.phase)
        assertFalse(state.softCapApplied)
        assertNull(state.pendingCapOffer)
        state = scoreAt(state, ANIMAL, 19, 3_900_000L)
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
        val halfNow = makeCapNow(state, CapType.HALF, LocalTime.of(10, 42))
        assertTrue(halfNow.rules.useHalfCap)
        assertEquals(LocalTime.of(10, 32), halfNow.startTime)
        assertEquals("Half cap set to now.", halfNow.lastEvent)
        assertEquals("Undo Half Cap Now", halfNow.undoEntry?.label)

        val softNow = makeCapNow(state, CapType.SOFT, LocalTime.of(10, 42))
        assertTrue(softNow.rules.useSoftCap)
        assertEquals(LocalTime.of(10, 22), softNow.startTime)
        assertEquals("Soft cap set to now.", softNow.lastEvent)
        assertEquals("Undo Soft Cap Now", softNow.undoEntry?.label)

        val hardNow = makeCapNow(state, CapType.HARD, LocalTime.of(10, 42))
        assertTrue(hardNow.rules.useHardCap)
        assertEquals(LocalTime.of(10, 12), hardNow.startTime)
        assertEquals("Hard cap set to now.", hardNow.lastEvent)
        assertEquals("Undo Hard Cap Now", hardNow.undoEntry?.label)

        // Once the next half-cap target would equal normal halftime, half cap should not prompt.
        state = newCapState(
            capRules.copy(
                useHardCap = false,
            )
        )
        repeat(6) { index ->
            state = scoreAt(state, VC, 1, 3_000_000L + index * 20_000L)
            state = scoreAt(state, ANIMAL, 1, 3_010_000L + index * 20_000L)
        }
        assertEquals(6, state.teamOne.score)
        assertEquals(6, state.teamTwo.score)
        assertNull(state.pendingCapOffer)
        assertEquals(CapStatus("Soft cap", Duration.ofMinutes(19)), computeNextCapStatus(state, LocalTime.of(10, 1)))
        state = scoreAt(state, VC, 11, 3_200_000L)
        assertEquals(7, state.teamOne.score)
        assertEquals(6, state.teamTwo.score)
        assertNull(state.pendingCapOffer)
        assertFalse(state.halfCapApplied)

        state = newCapState(
            capRules.copy(
                useHardCap = false,
            )
        )
        repeat(7) { index ->
            state = scoreAt(state, VC, 1, 3_300_000L + index * 10_000L)
        }
        repeat(3) { index ->
            state = scoreAt(state, ANIMAL, 1, 3_400_000L + index * 10_000L)
        }
        assertEquals(7, state.teamOne.score)
        assertEquals(3, state.teamTwo.score)
        assertNull(state.pendingCapOffer)
        assertEquals(CapStatus("Soft cap", Duration.ofMinutes(19)), computeNextCapStatus(state, LocalTime.of(10, 1)))
        state = scoreAt(state, ANIMAL, 11, 3_500_000L)
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
            teamTwo = TeamSetup("Animal", TeamColorChoice.ORANGE),
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
        assertEquals(90_000L, state.countdown?.targetEpochMillis)

        // Edit setup after play has begun and verify opening pull metadata changes without rewriting current field state.
        state = beginLivePoint(state)
        state = assessYellowCard(state, VC, "17").state
        state = recordGoal(state, VC, LocalTime.of(8, 50), 100_000L)
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
        assertEquals(playerCards(fieldStateAfterGoal, VC), playerCards(state, VC))
        assertEquals(playerCards(fieldStateAfterGoal, ANIMAL), playerCards(state, ANIMAL))
        assertEquals(VC, state.openingPullingTeam)
        assertEquals(FieldEnd.FAR, state.openingPullingFromEnd)
        assertEquals(fieldStateAfterGoal.pullingTeam, state.pullingTeam)
        assertEquals(fieldStateAfterGoal.pullingFromEnd, state.pullingFromEnd)
        assertEquals(fieldStateAfterGoal.nearAttackingTeam, state.nearAttackingTeam)
        assertEquals(fieldStateAfterGoal.phase, state.phase)
        assertEquals(fieldStateAfterGoal.countdown, state.countdown)
        assertEquals(fieldStateAfterGoal.pendingCapOffer, state.pendingCapOffer)
        assertEquals(emptyList<PlayerCardRecord>(), state.priorCards)

        // Verify setup edits preserve pending cap prompts and do not restart an in-progress countdown.
        state = fieldStateAfterGoal.copy(pendingCapOffer = CapType.SOFT)
        val pendingCountdown = state.countdown
        state = applySetupToLiveGame(state, editedAfterPlay.copy(rules = editedAfterPlay.rules.copy(gameTo = 19)), 300_000L)
        assertEquals(CapType.SOFT, state.pendingCapOffer)
        assertEquals(pendingCountdown, state.countdown)
        assertEquals(19, state.rules.gameTo)

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
        state = adjustScore(state, teamOneScore = -2, teamTwoScore = 4)
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
        state = swapFieldEnds(state)
        assertEquals(ANIMAL, state.nearAttackingTeam)
        assertEquals(VC, state.pullingTeam)
        assertEquals(FieldEnd.NEAR, state.pullingFromEnd)
        assertEquals("Pull in", state.countdown?.label)
        assertEquals(80, state.countdown?.durationSeconds)
        assertEquals(countdownBeforeSwapEnds.targetEpochMillis + 20_000L, state.countdown?.targetEpochMillis)
        assertEquals("Field ends swapped.", state.lastEvent)
        assertEquals("Undo Swap Ends of Field", state.undoEntry?.label)

        // Swap pulling team and verify only pulling team/end changes while team field positions are preserved.
        state = standardLiveGameState(pullingTeam = VC, pullingFromEnd = FieldEnd.FAR)
        assertEquals(VC, state.nearAttackingTeam)
        assertEquals(VC, state.pullingTeam)
        assertEquals(FieldEnd.FAR, state.pullingFromEnd)
        assertEquals("Signal in", state.countdown?.label)
        state = swapPullingTeam(state)
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
        state = adjustTimeouts(state, teamOneTimeoutsUsed = 1, teamTwoTimeoutsUsed = 2)
        val beforeManualHalftime = state
        state = startHalftimeNow(state, LocalTime.of(11, 10), 500_000L)
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
        assertEquals(860_000L, state.countdown?.targetEpochMillis)
        assertEquals("Undo Start Halftime", state.undoEntry?.label)
        assertEquals(beforeManualHalftime, state.undoEntry?.previous)

        // The UI hides Start Halftime after halftime or game over; the model rejects those calls too.
        assertEquals(state, startHalftimeNow(state, LocalTime.of(11, 11), 600_000L))
        val gameOverState = endGameNow(state, LocalTime.of(11, 12))
        assertEquals(gameOverState, startHalftimeNow(gameOverState, LocalTime.of(11, 13), 700_000L))

        // Manually end the game and verify end time, phase, countdown clearing, and undo entry.
        val beforeManualEnd = standardLiveGameState()
        state = endGameNow(beforeManualEnd, LocalTime.of(11, 40))
        assertEquals(LivePhase.GAME_OVER, state.phase)
        assertEquals(LocalTime.of(11, 40), state.endTime)
        assertNull(state.countdown)
        assertNull(state.pendingCapOffer)
        assertEquals("Game over.", state.lastEvent)
        assertEquals("Undo End Game", state.undoEntry?.label)
        assertEquals(beforeManualEnd, state.undoEntry?.previous)

        // Undo game over restores the saved live state from before End Game was applied.
        state = undoGameOver(state)
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
        assertEquals(state, undoLastAction(state))

        // Start a point and verify undo returns to the previous between-points state.
        state = standardLiveGameState()
        val beforeStartPoint = state
        state = beginLivePoint(state)
        assertEquals("Undo Start Point", state.undoEntry?.label)
        assertEquals(beforeStartPoint, undoLastAction(state))

        // Record a goal from a live point and verify undo restores the in-point state.
        val beforeLiveGoal = state
        state = recordGoal(state, VC, LocalTime.of(11, 5), 100_000L)
        assertEquals(1, state.teamOne.score)
        assertEquals("Undo Goal by Viscous Coupling", state.undoEntry?.label)
        assertEquals(beforeLiveGoal, undoLastAction(state))

        // Record a goal from between points and verify undo returns to the implicit live-point state.
        state = standardLiveGameState()
        val betweenPointsBeforeGoal = state
        state = recordGoalFromCurrentState(state, VC, LocalTime.of(11, 5), 200_000L)
        val implicitLiveState = undoLastAction(state)
        assertEquals(LivePhase.LIVE_POINT, implicitLiveState.phase)
        assertNull(implicitLiveState.countdown)
        assertEquals(0, implicitLiveState.teamOne.score)
        assertEquals(0, implicitLiveState.teamTwo.score)
        assertEquals(betweenPointsBeforeGoal, undoLastAction(implicitLiveState))

        // Undo timeout, card, technical foul, offsides, and false-start actions.
        state = beginLivePoint(standardLiveGameState())
        val beforeTimeout = state
        state = assessTimeout(state, ANIMAL, 300_000L).state
        assertEquals(beforeTimeout, undoLastAction(state))

        state = standardLiveGameState()
        val beforeCard = state
        state = assessYellowCard(state, VC, "17").state
        assertEquals(beforeCard, undoLastAction(state))

        state = standardLiveGameState()
        val beforeTf = state
        state = assessTechnicalFoul(state, ANIMAL).state
        assertEquals(beforeTf, undoLastAction(state))

        state = standardLiveGameState()
        val beforeOffsides = state
        state = recordOffsides(state)
        assertEquals(beforeOffsides, undoLastAction(state))

        state = standardLiveGameState()
        val beforeFalseStart = state
        state = recordFalseStart(state)
        assertEquals(beforeFalseStart, undoLastAction(state))

        // Undo manual score, timeout, card/TF, and pull-infraction corrections.
        state = standardLiveGameState()
        val beforeScoreCorrection = state
        state = adjustScore(state, 2, 3)
        assertEquals(beforeScoreCorrection, undoLastAction(state))

        val beforeTimeoutCorrection = standardLiveGameState()
        state = adjustTimeouts(beforeTimeoutCorrection, 2, 1)
        assertEquals(beforeTimeoutCorrection, undoLastAction(state))

        val beforeCardCorrection = standardLiveGameState()
        state = adjustCardsAndTf(
            state = beforeCardCorrection,
            teamOneBlues = 1,
            teamOneTechnicalFouls = 2,
            teamTwoBlues = 3,
            teamTwoTechnicalFouls = 4,
            teamOnePlayerCards = listOf(InGamePlayerCardRecord("17", yellows = 1)),
            teamTwoPlayerCards = listOf(InGamePlayerCardRecord("23", directReds = 1)),
        )
        assertEquals(beforeCardCorrection, undoLastAction(state))

        val beforePullCorrection = standardLiveGameState()
        state = adjustPullInfractions(beforePullCorrection, 1, 2, 3, 4)
        assertEquals(beforePullCorrection, undoLastAction(state))

        // Undo apply half cap, soft cap, hard cap, force cap now, manual halftime, and manual end game.
        state = standardLiveGameState(
            startTime = LocalTime.of(10, 0),
            rules = GameRules(gameTo = 15, halfCapMinutes = 10, useSoftCap = false, useHardCap = false),
        )
        state = recordGoalFromCurrentState(state, VC, LocalTime.of(10, 11), 400_000L)
        val beforeApplyHalfCap = state
        state = applyPendingCap(state, LocalTime.of(10, 11))
        assertEquals(beforeApplyHalfCap, undoLastAction(state))

        state = standardLiveGameState(
            startTime = LocalTime.of(10, 0),
            rules = GameRules(gameTo = 15, useHalfCap = false, softCapMinutes = 10, useHardCap = false),
        )
        state = recordGoalFromCurrentState(state, VC, LocalTime.of(10, 11), 500_000L)
        val beforeApplySoftCap = state
        state = applyPendingCap(state, LocalTime.of(10, 11))
        assertEquals(beforeApplySoftCap, undoLastAction(state))

        state = standardLiveGameState(
            startTime = LocalTime.of(10, 0),
            rules = GameRules(gameTo = 15, useHalfCap = false, useSoftCap = false, hardCapMinutes = 10),
        )
        state = recordGoalFromCurrentState(state, VC, LocalTime.of(10, 11), 600_000L)
        val beforeApplyHardCap = state
        state = applyPendingCap(state, LocalTime.of(10, 11))
        assertEquals(beforeApplyHardCap, undoLastAction(state))

        val beforeForceCap = standardLiveGameState()
        state = makeCapNow(beforeForceCap, CapType.SOFT, LocalTime.of(11, 30))
        assertEquals(beforeForceCap, undoLastAction(state))

        val beforeManualHalftime = standardLiveGameState(pullingFromEnd = FieldEnd.NEAR)
        state = startHalftimeNow(beforeManualHalftime, LocalTime.of(11, 10), 700_000L)
        assertEquals(beforeManualHalftime, undoLastAction(state))

        val beforeManualEnd = standardLiveGameState()
        state = endGameNow(beforeManualEnd, LocalTime.of(11, 20))
        assertEquals(beforeManualEnd, undoLastAction(state))

        // Verify the latest undo entry is exposed when actions are chained.
        state = beginLivePoint(standardLiveGameState())
        val afterStartPoint = state
        state = assessTimeout(state, VC, 800_000L).state
        assertEquals("Undo Timeout by Viscous Coupling", state.undoEntry?.label)
        assertEquals(afterStartPoint, undoLastAction(state))

        // Verify undo from game-over summary restores a score-ended game without undoing the score.
        state = standardLiveGameState(
            rules = GameRules(gameTo = 1, useHalfCap = false, useSoftCap = false, useHardCap = false)
        )
        val gameOverByScore = recordGoalFromCurrentState(state, VC, LocalTime.of(11, 25), 900_000L)
        assertEquals(LivePhase.GAME_OVER, gameOverByScore.phase)
        val scoreEndedUndo = undoGameOver(gameOverByScore)
        assertEquals(LivePhase.BETWEEN_POINTS, scoreEndedUndo.phase)
        assertEquals(1, scoreEndedUndo.teamOne.score)
        assertEquals(0, scoreEndedUndo.teamTwo.score)
        assertNull(scoreEndedUndo.endTime)
        assertEquals("Viscous Coupling scored.", scoreEndedUndo.lastEvent)
        assertEquals(LivePhase.LIVE_POINT, undoLastAction(scoreEndedUndo).phase)

        // Unavailable game-over commands are idempotent no-ops; the UI normally hides these pathways.
        assertEquals(scoreEndedUndo, undoGameOver(scoreEndedUndo))
        assertEquals(gameOverByScore, recordGoal(gameOverByScore, ANIMAL, LocalTime.of(11, 26), 910_000L))
        assertEquals(gameOverByScore, endGameNow(gameOverByScore, LocalTime.of(11, 26)))

        // If the observer applies End Game again, the summary-relevant state matches the automatic game-over.
        val reappliedGameOver = endGameNow(scoreEndedUndo, LocalTime.of(11, 26))
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
        assertEquals(CapStatus("Half cap", Duration.ofMinutes(30)), computeNextCapStatus(state, LocalTime.of(10, 15)))
        assertEquals(CapStatus("Soft cap", Duration.ofMinutes(30)), computeNextCapStatus(state.copy(halfCapApplied = true), LocalTime.of(11, 0)))
        assertEquals(
            CapStatus("Hard cap", Duration.ofMinutes(5)),
            computeNextCapStatus(state.copy(halfCapApplied = true, softCapApplied = true), LocalTime.of(11, 35)),
        )

        // Verify cap countdowns can wrap across midnight when a late-night game crosses dates.
        state = standardLiveGameState(
            startTime = LocalTime.of(23, 30),
            rules = GameRules(gameTo = 15, halfCapMinutes = 45, useSoftCap = false, useHardCap = false),
        )
        assertEquals(CapStatus("Half cap", Duration.ofMinutes(30)), computeNextCapStatus(state, LocalTime.of(23, 45)))

        // Verify computeNextCapStatus returns null when no cap is still available.
        state = state.copy(halfCapApplied = true, softCapApplied = true, hardCapApplied = true)
        assertNull(computeNextCapStatus(state, LocalTime.of(10, 15)))

        state = standardLiveGameState(
            startTime = LocalTime.of(10, 0),
            rules = GameRules(gameTo = 15, useHalfCap = false, useSoftCap = false, useHardCap = false),
        )
        assertNull(computeNextCapStatus(state, LocalTime.of(10, 15)))

        // Verify betweenPointsDisplay gives "Signal in" vs "Pull in" and clamps elapsed countdowns to zero.
        assertEquals("Signal in" to Duration.ofSeconds(60), betweenPointsDisplay(FieldEnd.FAR, 1_000L, 1_000L))
        assertEquals("Signal in" to Duration.ofSeconds(30), betweenPointsDisplay(FieldEnd.FAR, 1_000L, 31_000L))
        assertEquals("Signal in" to Duration.ZERO, betweenPointsDisplay(FieldEnd.FAR, 1_000L, 70_000L))
        assertEquals("Pull in" to Duration.ofSeconds(80), betweenPointsDisplay(FieldEnd.NEAR, 2_000L, 2_000L))

        // Verify manual countdown adjustments move only the target time and format positive/negative changes.
        state = standardLiveGameState()
        val originalCountdown = state.countdown!!
        state = addTimeToCountdown(state, 65)
        assertEquals(originalCountdown.targetEpochMillis + 65_000L, state.countdown?.targetEpochMillis)
        assertEquals(originalCountdown.durationSeconds, state.countdown?.durationSeconds)
        assertEquals("Adjusted timer by 1:05.", state.lastEvent)

        state = addTimeToCountdown(state, -5)
        assertEquals(originalCountdown.targetEpochMillis + 60_000L, state.countdown?.targetEpochMillis)
        assertEquals(originalCountdown.durationSeconds, state.countdown?.durationSeconds)
        assertEquals("Adjusted timer by -0:05.", state.lastEvent)

        val livePointWithoutCountdown = beginLivePoint(state)
        assertEquals(livePointWithoutCountdown, addTimeToCountdown(livePointWithoutCountdown, 5))

        // Verify countdown helpers advance automatically at the exact target time, not before.
        state = standardLiveGameState()
        val betweenPointsCountdown = state.countdown!!
        assertEquals(state, advanceGameClock(state, betweenPointsCountdown.targetEpochMillis - 1L))
        state = advanceGameClock(state, betweenPointsCountdown.targetEpochMillis)
        assertEquals(LivePhase.LIVE_POINT, state.phase)
        assertNull(state.countdown)

        state = assessTimeout(state, VC, 500_000L).state
        val timeoutCountdown = state.countdown!!
        assertEquals(state, advanceGameClock(state, timeoutCountdown.targetEpochMillis - 1L))
        state = advanceGameClock(state, timeoutCountdown.targetEpochMillis)
        assertEquals(LivePhase.LIVE_POINT, state.phase)
        assertNull(state.countdown)
    }
}
