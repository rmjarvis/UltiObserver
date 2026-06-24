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

/// Tests for timeout allowance, timeout charging, timeout corrections, and timeout event messages.
class TestGameTimeouts : GameDomainTestFixtures() {
    /**
     * Test timeout rules and timeout state transitions across both halves.
     * Cover ordinary rules, floater rules, no-timeout rules, and midgame rule updates.
     */
    @Test
    fun timeouts() {
        val VC = TeamId.TEAM_ONE
        val ANIMAL = TeamId.TEAM_TWO

        /**
         * Build setup state for a timeout-rule scenario.
         *
         * @param rules The timeout rules to install in the setup.
         * @param pullingFromEnd The opening pulling end, used to exercise both countdown targets.
         */
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

        /**
         * Score enough points to enter halftime in a game-to-five scenario.
         *
         * @param startingState The live state to advance toward halftime.
         * @param scoringTeam The team that scores each point in the halftime setup sequence.
         * @param start The epoch millis assigned to the first goal in the sequence.
         */
        fun scoreToHalftime(
            startingState: GameState,
            scoringTeam: TeamId,
            start: Long,
        ): GameState {
            var current = startingState
            var pointNumber = 0
            while (current.phase != GamePhase.HALFTIME) {
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
        val betweenPointsTimeoutTime = originalCountdown.targetEpoch - 1_000L
        var timeoutResult = state.assessTimeout(VC, betweenPointsTimeoutTime)
        assertEquals(
            "Timeout charged to Viscous Coupling. They have 1 timeout remaining in this half.",
            timeoutResult.message(),
        )
        assertEquals("Timeout", timeoutResult.event?.formatPopupTitle())
        state = timeoutResult.state
        assertEquals(1, state.teamOne.timeoutsUsedThisHalf)
        assertEquals(1, state.timeoutsRemaining(VC))
        assertEquals("Signal in", state.countdown?.label)
        assertEquals(90, state.countdown?.durationSeconds)
        assertEquals(originalCountdown.targetEpoch + 70_000L, state.countdown?.targetEpoch)
        assertEquals(
            TimingCueId.TIMEOUT_BETWEEN_POINTS_ONE_MINUTE_FOR_HAND,
            state.countdown?.nextTimingCue(betweenPointsTimeoutTime)?.id,
        )
        assertEquals(
            Duration.ofSeconds(60),
            state.countdown?.nextTimingCue(betweenPointsTimeoutTime)?.countdownTime,
        )
        assertEquals("Undo Timeout by Viscous Coupling", state.undoEntry?.label)
        val timeoutUnavailablePreview = state.copy(phase = GamePhase.GAME_OVER).previewTimeout(VC, betweenPointsTimeoutTime)
        assertTrue(timeoutUnavailablePreview.event is GameEvent.TimeoutUnavailable)

        // Pull-side between-points timeouts use the pull-specific 1-minute cue.
        val pullSideState = standardLiveGameState().copy(
            pullingFromEnd = FieldEnd.NEAR,
            countdown = buildBetweenPointsCountdown(
                pullingFromEnd = FieldEnd.NEAR,
                sequenceStart = 1_000L,
                promptTarget = PullPromptTarget.NEAR,
            ),
        )
        val pullSideTimeoutTime = pullSideState.countdown!!.targetEpoch - 1_000L
        val pullSideTimeoutState = pullSideState.assessTimeout(ANIMAL, pullSideTimeoutTime).state
        assertEquals(
            TimingCueId.TIMEOUT_BETWEEN_POINTS_ONE_MINUTE_TO_PULL,
            pullSideTimeoutState.countdown?.nextTimingCue(pullSideTimeoutTime)?.id,
        )
        val bothSideState = standardLiveGameState().withPullPromptTarget(PullPromptTarget.BOTH)
        val bothSideTimeoutTime = bothSideState.countdown!!.targetEpoch - 1_000L
        val bothSideTimeoutState = bothSideState.assessTimeout(ANIMAL, bothSideTimeoutTime).state
        assertEquals(
            TimingCueId.TIMEOUT_BETWEEN_POINTS_ONE_MINUTE_FOR_HAND,
            bothSideTimeoutState.countdown?.nextTimingCue(bothSideTimeoutTime)?.id,
        )

        // A live-point timeout starts a fresh offense-set timeout countdown.
        state = state.beginLivePoint()
        timeoutResult = state.assessTimeout(VC, 1_000_000L)
        assertEquals(
            "Timeout charged to Viscous Coupling. They have 0 timeouts remaining in this half.",
            timeoutResult.message(),
        )
        state = timeoutResult.state
        assertEquals(2, state.teamOne.timeoutsUsedThisHalf)
        assertEquals(0, state.timeoutsRemaining(VC))
        assertEquals(GamePhase.LIVE_POINT, state.phase)
        assertEquals(CountdownKind.TIME_OUT, state.countdown?.kind)
        assertEquals("Offense set in", state.countdown?.label)
        assertEquals(70, state.countdown?.durationSeconds)
        assertEquals(1_070_000L, state.countdown?.targetEpoch)
        val explicitTimeoutDefenseState = state

        // If enabled, the timeout offense-set countdown waits for the observer to start defense.
        assertTrue(explicitTimeoutDefenseState.canReportOffenseSet(true))
        assertFalse(explicitTimeoutDefenseState.canReportOffenseSet(false))
        assertEquals(
            explicitTimeoutDefenseState,
            explicitTimeoutDefenseState.applyExpiredCountdownTransitions(
                now = 1_070_000L,
                showDefenseCountdowns = true,
            ),
        )
        val earlyOffenseSetState = explicitTimeoutDefenseState.reportOffenseSet(1_060_000L)
        assertEquals(CountdownKind.DEFENSE_CHECK, earlyOffenseSetState.countdown?.kind)
        assertEquals("Defense check in", earlyOffenseSetState.countdown?.label)
        assertEquals(30, earlyOffenseSetState.countdown?.durationSeconds)
        assertEquals(1_090_000L, earlyOffenseSetState.countdown?.targetEpoch)
        val lateOffenseSetState = explicitTimeoutDefenseState.reportOffenseSet(1_075_000L)
        assertEquals(CountdownKind.DEFENSE_CHECK, lateOffenseSetState.countdown?.kind)
        assertEquals(20, lateOffenseSetState.countdown?.durationSeconds)
        assertEquals(1_095_000L, lateOffenseSetState.countdown?.targetEpoch)
        assertNull(
            lateOffenseSetState.applyExpiredCountdownTransitions(
                now = 1_095_000L,
                showDefenseCountdowns = true,
            ).countdown,
        )

        // Once the live-point timeout countdown expires, the model automatically continues the point.
        assertEquals(
            state,
            state.applyExpiredCountdownTransitions(
                1_070_000L - 1L,
                showDefenseCountdowns = false,
            ),
        )
        state = state.applyExpiredCountdownTransitions(1_070_000L, showDefenseCountdowns = false)
        assertEquals(GamePhase.LIVE_POINT, state.phase)
        assertNull(state.countdown)
        assertEquals("Point continued.", state.lastEvent)
        assertEquals("Undo Timeout by Viscous Coupling", state.undoEntry?.label)

        // With both first-half timeouts used, another timeout request leaves state unchanged and returns a message.
        timeoutResult = state.assessTimeout(VC, 1_010_000L)
        assertEquals(
            "Viscous Coupling is out of timeouts.\n\n" +
                "Add three to the stall count. It is a turnover if that is 10 or more.",
            timeoutResult.message(),
        )
        assertEquals("Invalid timeout", timeoutResult.event?.formatPopupTitle())
        assertEquals(state, timeoutResult.state)
        assertEquals(state, state.chargeTimeout(VC, 1_010_000L))

        // In the ordinary two-per-half rules, both teams return to two timeouts at halftime.
        state = scoreToHalftime(state, VC, 1_100_000L)
        assertEquals(GamePhase.HALFTIME, state.phase)
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
        assertEquals("Timeouts are not available now.", timeoutResult.message())
        assertEquals("Timeout not possible now", timeoutResult.event?.formatPopupTitle())
        assertEquals(state, (timeoutResult.event as GameEvent.TimeoutUnavailable).state)
        assertEquals(state, timeoutResult.state)

        // The UI hides timeout actions after game over; stale timeout commands are idempotent no-ops.
        val gameOverTimeoutState = standardLiveGameState(
            rules = GameRules(gameTo = 1, useHalfCap = false, useSoftCap = false, useHardCap = false),
        ).recordGoalFromCurrentState(
            VC,
            1_150_000L,
        )
        assertEquals(GamePhase.GAME_OVER, gameOverTimeoutState.phase)
        timeoutResult = gameOverTimeoutState.assessTimeout(VC, 1_160_000L)
        assertEquals("Timeouts are not available now.", timeoutResult.message())
        assertEquals(gameOverTimeoutState, timeoutResult.state)
        assertEquals(gameOverTimeoutState, gameOverTimeoutState.chargeTimeout(VC, 1_160_000L))

        // After halftime has elapsed but before the pull, a timeout behaves like a between-points timeout.
        timeoutResult = state.assessTimeout(VC, halftimeEnd + 1L)
        assertEquals(
            "Timeout charged to Viscous Coupling. They have 1 timeout remaining in this half.",
            timeoutResult.message(),
        )
        val afterHalftimeTimeoutState = timeoutResult.state
        assertEquals(GamePhase.BETWEEN_POINTS, afterHalftimeTimeoutState.phase)
        assertEquals(1, afterHalftimeTimeoutState.teamOne.timeoutsUsedThisHalf)
        assertEquals(1, afterHalftimeTimeoutState.timeoutsRemaining(VC))
        assertEquals("Signal in", afterHalftimeTimeoutState.countdown?.label)
        assertEquals(130, afterHalftimeTimeoutState.countdown?.durationSeconds)
        assertEquals(halftimeEnd + 130_000L, afterHalftimeTimeoutState.countdown?.targetEpoch)

        // When the pull countdown expires, timeout handling transitions to the live point unless the observer undoes it.
        val expiredPullState = createLiveGameState(setupWithRules(GameRules(useHalfCap = false)))
        val expiredCountdownNow = expiredPullState.countdown!!.targetEpoch + 1L
        val transitionedPullState = expiredPullState.applyExpiredCountdownTransitions(expiredCountdownNow, showDefenseCountdowns = false)
        assertEquals(GamePhase.LIVE_POINT, transitionedPullState.phase)
        assertNull(transitionedPullState.countdown)
        assertEquals("Undo Start point", transitionedPullState.undoEntry?.label)
        val expiredPullDecisionState = expiredPullState.copy(
            countdown = null,
            pullCountdownExpired = true,
        )
        val undoneExpiredPullState = assertUndoRestores(expiredPullDecisionState, transitionedPullState)
        timeoutResult = undoneExpiredPullState.assessTimeout(ANIMAL, expiredCountdownNow)
        assertEquals("Timeouts are not available now.", timeoutResult.message())
        assertEquals(undoneExpiredPullState, timeoutResult.state)

        // A timeout after the pull countdown has expired behaves as a live-point timeout.
        timeoutResult = expiredPullState.assessTimeout(
            ANIMAL,
            expiredCountdownNow,
        )
        val expiredTimeoutState = timeoutResult.state
        assertEquals("Timeout charged to Animal. They have 1 timeout remaining in this half.", timeoutResult.message())
        assertEquals(GamePhase.LIVE_POINT, expiredTimeoutState.phase)
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
        assertEquals("Viscous Coupling is out of timeouts.", timeoutResult.message())
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
        assertEquals("Undo Timeout adjustment", state.undoEntry?.label)
        assertEquals(beforeTimeoutAdjustment, state.undoEntry?.previous)

        // If only one team's timeout count changes, only that team's correction is logged.
        val beforeSingleTeamTimeoutAdjustment = state
        state = state.adjustTimeouts(
            teamOneTimeoutsUsed = state.teamOne.timeoutsUsedThisHalf,
            teamTwoTimeoutsUsed = 0,
        )
        assertEquals(4, state.teamOne.timeoutsUsedThisHalf)
        assertEquals(0, state.teamTwo.timeoutsUsedThisHalf)
        val timeoutAdjustmentEntry = state.eventLog.last()
        assertEquals(EventLogType.TIMEOUT, timeoutAdjustmentEntry.type)
        assertEquals(TeamId.TEAM_TWO, timeoutAdjustmentEntry.team)
        assertEquals(-1, timeoutAdjustmentEntry.delta)
        assertEquals(beforeSingleTeamTimeoutAdjustment, state.undoEntry?.previous)
    }
}
