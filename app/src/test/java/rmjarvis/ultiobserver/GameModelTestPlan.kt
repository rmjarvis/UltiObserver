package rmjarvis.ultiobserver

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Ignore
import org.junit.Test

class GameModelTestPlan {
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
            pullingFromEnd = FieldEnd.FAR,
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
        assertEquals(FieldEnd.FAR, state.pullingFromEnd)
        assertEquals(VC, state.nearAttackingTeam)
        assertEquals(CountdownKind.BETWEEN_POINTS, state.countdown?.kind)
        assertEquals("Signal in", state.countdown?.label)
        assertEquals(60, state.countdown?.durationSeconds)

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
        assertEquals(1, state.teamOne.yellowCards)
        assertEquals(
            InGamePlayerCardRecord(VC, "17", yellows = 1),
            state.playerCardsThisGame.single { it.team == VC && it.jerseyNumber == "17" },
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
        assertEquals(2, state.teamOne.yellowCards)
        assertEquals("Undo Yellow Card on Viscous Coupling #8", state.undoEntry?.label)
        assertEquals(
            InGamePlayerCardRecord(VC, "8", yellows = 1),
            state.playerCardsThisGame.single { it.team == VC && it.jerseyNumber == "8" },
        )
        assertTrue(
            livePointMisconductResolutionMessage(cardResult.message, againstOffense = true)
                .contains("Reverse brick"),
        )

        // Viscous Coupling scores the first point, so they pull the next point from the near end.
        state = recordGoal(state, VC, LocalTime.of(10, 5), 1_010_000L)
        assertEquals(LivePhase.BETWEEN_POINTS, state.phase)
        assertEquals(1, state.teamOne.score)
        assertEquals(0, state.teamTwo.score)
        assertEquals(VC, state.pullingTeam)
        assertEquals(FieldEnd.NEAR, state.pullingFromEnd)
        assertEquals(ANIMAL, state.nearAttackingTeam)
        assertEquals("Pull in", state.countdown?.label)
        assertEquals(80, state.countdown?.durationSeconds)
        assertEquals(1_090_000L, state.countdown?.targetEpochMillis)
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
        assertEquals(1, state.teamTwo.yellowCards)
        assertEquals(
            InGamePlayerCardRecord(ANIMAL, "23", yellows = 1),
            state.playerCardsThisGame.single { it.team == ANIMAL && it.jerseyNumber == "23" },
        )

        cardResult = assessYellowCard(state, ANIMAL, "8")
        state = cardResult.state
        assertEquals("Animal has 2 cards.", cardResult.message)
        assertEquals(2, state.teamTwo.yellowCards)
        assertEquals(
            InGamePlayerCardRecord(ANIMAL, "8", yellows = 1),
            state.playerCardsThisGame.single { it.team == ANIMAL && it.jerseyNumber == "8" },
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
        assertEquals("Undo Goal by Animal", state.undoEntry?.label)
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
        assertEquals("Pull in", afterHalftimeTimeoutState.countdown?.label)
        assertEquals(150, afterHalftimeTimeoutState.countdown?.durationSeconds)
        assertEquals(halftimeEndMillis + 150_000L, afterHalftimeTimeoutState.countdown?.targetEpochMillis)

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

        fun cardPoints(team: TeamLiveState): Int {
            return team.yellowCards + team.blueCards + (2 * team.redCards)
        }

        fun playerRecord(
            state: LiveGameState,
            team: TeamId,
            jerseyNumber: String,
        ): InGamePlayerCardRecord {
            return state.playerCardsThisGame.single { it.team == team && it.jerseyNumber == jerseyNumber }
        }

        // Record a first yellow for a numbered Viscous Coupling player and verify team and player state.
        var state = standardLiveGameState()
        var cardResult = assessYellowCard(state, VC, "17")
        state = cardResult.state
        assertFalse(cardResult.needsLivePointMisconductChoice)
        assertEquals("Viscous Coupling has 1 card.", cardResult.message)
        assertEquals(1, state.teamOne.yellowCards)
        assertEquals(0, state.teamOne.redCards)
        assertEquals(1, cardPoints(state.teamOne))
        assertEquals(InGamePlayerCardRecord(VC, "17", yellows = 1), playerRecord(state, VC, "17"))
        assertEquals("Undo Yellow Card on Viscous Coupling #17", state.undoEntry?.label)

        // A second yellow to the same player acts as a red card, but adds only one more team card point.
        cardResult = assessYellowCard(state, VC, "17")
        state = cardResult.state
        assertFalse(cardResult.needsLivePointMisconductChoice)
        assertEquals("Second yellow acts as a red card. Player 17 is ejected.\nViscous Coupling has 2 cards.", cardResult.message)
        assertEquals(2, state.teamOne.yellowCards)
        assertEquals(0, state.teamOne.redCards)
        assertEquals(2, cardPoints(state.teamOne))
        assertEquals(InGamePlayerCardRecord(VC, "17", yellows = 2), playerRecord(state, VC, "17"))
        assertEquals("Undo Second Yellow on Viscous Coupling #17", state.undoEntry?.label)

        // A third team-card point between points gives the pulling-team misconduct field-position cue.
        cardResult = assessBlueCard(state, VC)
        state = cardResult.state
        assertFalse(cardResult.needsLivePointMisconductChoice)
        assertEquals(1, state.teamOne.blueCards)
        assertEquals(3, cardPoints(state.teamOne))
        assertEquals(1, state.playerCardsThisGame.size)
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
        assertEquals(0, state.teamTwo.yellowCards)
        assertEquals(1, state.teamTwo.redCards)
        assertEquals(2, cardPoints(state.teamTwo))
        assertEquals(InGamePlayerCardRecord(ANIMAL, "23", directReds = 1), playerRecord(state, ANIMAL, "23"))

        // A direct red for a player who already has a yellow is distinct from recording the red as a second yellow.
        state = standardLiveGameState()
        state = assessYellowCard(state, ANIMAL, "8").state
        cardResult = assessRedCard(state, ANIMAL, "8", RedCardMode.DIRECT_RED)
        state = cardResult.state
        assertFalse(cardResult.needsLivePointMisconductChoice)
        assertEquals(1, state.teamTwo.yellowCards)
        assertEquals(1, state.teamTwo.redCards)
        assertEquals(3, cardPoints(state.teamTwo))
        assertEquals(InGamePlayerCardRecord(ANIMAL, "8", yellows = 1, directReds = 1), playerRecord(state, ANIMAL, "8"))
        assertEquals(
            "Animal has 3 cards.\n\nPenalty against receiving team. No pull. Disc at negative brick in defending end zone.",
            cardResult.message,
        )

        state = standardLiveGameState()
        state = assessYellowCard(state, ANIMAL, "8").state
        cardResult = assessRedCard(state, ANIMAL, "8", RedCardMode.SECOND_YELLOW)
        state = cardResult.state
        assertFalse(cardResult.needsLivePointMisconductChoice)
        assertEquals(2, state.teamTwo.yellowCards)
        assertEquals(0, state.teamTwo.redCards)
        assertEquals(2, cardPoints(state.teamTwo))
        assertEquals(InGamePlayerCardRecord(ANIMAL, "8", yellows = 2), playerRecord(state, ANIMAL, "8"))
        assertEquals("Second yellow acts as a red card. Player 8 is ejected.\nAnimal has 2 cards.", cardResult.message)

        // The N/A pathways distinguish same-unknown-player second yellow from a standalone yellow.
        state = standardLiveGameState()
        state = assessYellowCard(state, VC, UNKNOWN_PLAYER_NUMBER).state
        assertTrue(playerHasYellowThisGame(state, VC, UNKNOWN_PLAYER_NUMBER))
        cardResult = assessRedCard(state, VC, UNKNOWN_PLAYER_NUMBER, RedCardMode.SECOND_YELLOW)
        state = cardResult.state
        assertEquals(2, state.teamOne.yellowCards)
        assertEquals(0, state.teamOne.redCards)
        assertEquals(InGamePlayerCardRecord(VC, UNKNOWN_PLAYER_NUMBER, yellows = 2), playerRecord(state, VC, UNKNOWN_PLAYER_NUMBER))
        assertEquals("Second yellow acts as a red card. The player is ejected.\nViscous Coupling has 2 cards.", cardResult.message)

        state = standardLiveGameState()
        state = assessYellowCard(state, VC, UNKNOWN_PLAYER_NUMBER).state
        cardResult = assessStandaloneYellowCard(state, VC, UNKNOWN_PLAYER_NUMBER)
        state = cardResult.state
        assertEquals(2, state.teamOne.yellowCards)
        assertEquals(0, state.teamOne.redCards)
        assertEquals(2, cardPoints(state.teamOne))
        assertFalse(cardResult.message.startsWith("Second yellow acts as a red card."))

        // Blue cards count as one team card point each and do not create per-player card records.
        state = standardLiveGameState()
        cardResult = assessBlueCard(state, ANIMAL)
        state = cardResult.state
        assertFalse(cardResult.needsLivePointMisconductChoice)
        assertEquals("Animal has 1 card.", cardResult.message)
        assertEquals(1, state.teamTwo.blueCards)
        assertEquals(1, cardPoints(state.teamTwo))
        assertTrue(state.playerCardsThisGame.isEmpty())

        cardResult = assessBlueCard(state, ANIMAL)
        state = cardResult.state
        assertFalse(cardResult.needsLivePointMisconductChoice)
        assertEquals("Animal has 2 cards.", cardResult.message)
        assertEquals(2, state.teamTwo.blueCards)
        assertEquals(2, cardPoints(state.teamTwo))

        cardResult = assessBlueCard(state, ANIMAL)
        state = cardResult.state
        assertFalse(cardResult.needsLivePointMisconductChoice)
        assertEquals(3, state.teamTwo.blueCards)
        assertEquals(3, cardPoints(state.teamTwo))
        assertEquals(
            "Animal has 3 cards.\n\nPenalty against receiving team. No pull. Disc at negative brick in defending end zone.",
            cardResult.message,
        )

        cardResult = assessBlueCard(state, ANIMAL)
        state = cardResult.state
        assertFalse(cardResult.needsLivePointMisconductChoice)
        assertEquals(4, state.teamTwo.blueCards)
        assertEquals(4, cardPoints(state.teamTwo))
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
        assertEquals(0, cardPoints(state.teamTwo))

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
        // Score a point before any cap time and verify no pending cap offer appears.

        // Score a point after half-cap time and verify the pending offer is for half cap.

        // Apply half cap and verify halftime target becomes current higher score plus one.

        // Defer a pending cap and verify play continues without applying the cap.

        // Verify disabled half, soft, or hard caps do not offer, count down, or apply.

        // Score after soft-cap time and verify applying soft cap sets winning score to current higher score plus one.

        // Score after hard-cap time while untied and verify applying hard cap ends the game immediately.

        // Score after hard-cap time while tied and verify applying hard cap sets a one-point winning score.

        // Verify force-cap-now actions adjust start time and enable the relevant cap.

        // Verify half cap becomes irrelevant once both teams are one below the normal halftime target.
    }

    // Test setup conversion and applying setup edits to a live game.
    // The setup form is public UI, but the model owns how edits reshape live state.
    @Test
    fun setupRoundTripAndMidgameUpdates() {
        // Create a live game from setup and convert it back to setup state.

        // Verify start time, rules, team names, colors, prior-card holders, and opening pull round-trip.

        // Edit setup before the first point and verify opening pull changes resync current pull and field state.

        // Edit setup after play has begun and verify opening pull metadata changes without rewriting current field state.

        // Verify setup edits preserve score, cards, pull infractions, pending caps, and current phase.

        // Verify prior-card holders from setup are preserved when updating an existing game.

        // Verify updating rules midgame does not implicitly restart countdowns except in the documented pre-point resync path.
    }

    // Test manual correction and less-common actions that are surfaced through the Other menu.
    // These are model actions even though the menu is just one UI access path.
    @Test
    fun otherMenuModelActions() {
        // Adjust score and verify non-negative clamping, last event, and undo entry.

        // Adjust timeouts and verify values are clamped to the current half allowance.

        // Adjust cards and technical fouls, including explicit player-card record reconciliation inputs.

        // Swap field ends and verify near-attacking team, pulling end, countdown label, and undo entry.

        // Swap pulling team and verify only pulling team/end changes while team field positions are preserved.

        // Manually start halftime and verify second-half pull orientation, timeout reset, countdown, and undo entry.

        // Verify manual halftime is rejected once halftime has already happened or the game is over.

        // Manually end the game and verify end time, phase, countdown clearing, and undo entry.

        // Undo game over and verify the current behavior for restoring a between-points sequence.
    }

    // Test the undo mechanism through user-visible actions rather than private snapshots.
    // Include ordinary undo, corrections, cap application, halftime, and game-over cases.
    @Test
    fun undoMechanism() {
        // Start a point and verify undo returns to the previous between-points state.

        // Record a goal from a live point and verify undo restores the in-point state.

        // Record a goal from between points and verify implicit start-point behavior makes undo return to live-point state.

        // Undo timeout, card, technical foul, offsides, and false-start actions.

        // Undo manual score, timeout, card/TF, and pull-infraction corrections.

        // Undo apply half cap, soft cap, hard cap, force cap now, manual halftime, and manual end game.

        // Verify only the latest undo entry is exposed and old undo chains are not accidentally reused.

        // Verify undo from game-over summary takes the expected path for score-ended and manually-ended games.
    }

    // Test game-over and summary-relevant state without depending on UI rendering.
    // Completed-game archival should preserve summary data and drop live-only state.
    @Test
    fun gameOverSummaryAndArchiveState() {
        // End a game by reaching the winning score and verify summary fields are complete.

        // End a game manually and verify final score, nominal start time, and actual end time are retained.

        // Verify player yellow/red records, blue-card counts, and technical-foul counts are summary-ready.

        // Verify live countdown and pending cap state are cleared on game over.

        // Verify pruning undo history for archived games keeps summary data while removing undo state.
    }

    // Test deterministic clock and countdown helpers that are public model surface.
    // These tests should pin time behavior without relying on the wall clock.
    @Test
    fun clockAndCountdownDisplays() {
        // Verify formatClockTime for midnight, noon, morning, and afternoon values.

        // Verify formatDuration clamps negative durations to zero and formats minute/second boundaries.

        // Verify computeNextCapStatus reports the next relevant enabled cap from an explicit LocalTime.

        // Verify computeNextCapStatus skips applied, disabled, or irrelevant caps.

        // Verify betweenPointsDisplay gives "Signal in" vs "Pull in" based on pulling end.

        // Verify between-points countdown durations are 60 seconds from far end and 80 seconds from near end.
    }
}
