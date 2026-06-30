package rmjarvis.ultiobserver

import java.time.Duration
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/// Tests for timeouts
class TestTimeouts : GameDomainTestFixtures() {
    /**
     * Test the behavior for a timeout called between points.
     */
    @Test
    fun timeoutBetweenPoints() {
        val VC = TeamId.TEAM_ONE
        val ANIMAL = TeamId.TEAM_TWO

        // Start with the normal case of two timeouts per half.
        // (This is actually the default rules, but be explicit here for clarity.)
        var state = recordGoalFromCurrentStateAt(
            createLiveGameState(
                setupWithRules(timeoutRules(timeoutsPerHalf = 2, hasFloaterTimeout = false))
            ).beginLivePoint(),
            ANIMAL,
            LocalTime.of(9, 5),
        )
        assertEquals(GamePhase.BETWEEN_POINTS, state.phase)
        assertEquals(CountdownKind.BETWEEN_POINTS, state.countdown?.kind)
        assertEquals("Signal in", state.countdown?.label)
        assertEquals(60, state.countdown?.durationSeconds)
        assertEquals(0, state.teamOne.timeoutsUsedThisHalf)
        assertEquals(2, state.timeoutsAllowedThisHalf(VC))
        assertEquals(2, state.timeoutsRemaining(VC))
        assertEquals(0, state.teamTwo.timeoutsUsedThisHalf)
        assertEquals(2, state.timeoutsAllowedThisHalf(ANIMAL))
        assertEquals(2, state.timeoutsRemaining(ANIMAL))

        // Calling a timeout between points does a few things:
        // * It reduces the number of timeouts available for that team by 1.
        // * It reports a message about how many timeouts are left for that team.
        // * It adds 70 seconds to the pull/ready countdown.
        // * It adds an additional cue at 1 minute until pull or ready.
        // In this case, the timeout is called by the offense.
        val originalCountdown = state.countdown!!
        val betweenPointsTimeoutTime = originalCountdown.targetEpoch - 1_000L
        val timeoutPreview = state.previewTimeout(VC, betweenPointsTimeoutTime)
        assertTrue(timeoutPreview.event is GameEvent.TimeoutCharged)
        assertEquals(0, state.teamOne.timeoutsUsedThisHalf)
        assertTrue(state.canRequestTimeout(betweenPointsTimeoutTime))
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
        assertEquals(130, state.countdown?.durationSeconds)
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

        // Everything is basically the same if the pulling team calls a timeout, except the
        // additional cue is at 1 minute before the pull, rather than 1 minute before ready.
        val pullSideState = recordGoalFromCurrentStateAt(
            standardLiveGameState().beginLivePoint(),
            VC,
            LocalTime.of(11, 5),
        )
        val originalPullCountdown = pullSideState.countdown!!
        assertEquals(GamePhase.BETWEEN_POINTS, pullSideState.phase)
        assertEquals("Pull in", originalPullCountdown.label)
        assertEquals(80, originalPullCountdown.durationSeconds)
        val pullSideTimeoutTime = pullSideState.countdown!!.targetEpoch - 1_000L
        timeoutResult = pullSideState.assessTimeout(
            VC,
            pullSideTimeoutTime,
        )
        assertEquals(
            "Timeout charged to Viscous Coupling. They have 1 timeout remaining in this half.",
            timeoutResult.message(),
        )
        val pullSideTimeoutState = timeoutResult.state
        assertEquals(1, pullSideTimeoutState.teamOne.timeoutsUsedThisHalf)
        assertEquals(0, pullSideTimeoutState.teamTwo.timeoutsUsedThisHalf)
        assertEquals("Pull in", pullSideTimeoutState.countdown?.label)
        assertEquals(150, pullSideTimeoutState.countdown?.durationSeconds)
        assertEquals(
            originalPullCountdown.targetEpoch + 70_000L,
            pullSideTimeoutState.countdown?.targetEpoch,
        )
        assertEquals(
            TimingCueId.TIMEOUT_BETWEEN_POINTS_ONE_MINUTE_TO_PULL,
            pullSideTimeoutState.countdown?.nextTimingCue(pullSideTimeoutTime)?.id,
        )
        assertEquals(
            Duration.ofSeconds(60),
            pullSideTimeoutState.countdown?.nextTimingCue(pullSideTimeoutTime)?.countdownTime,
        )

        // When we are giving prompt for both ends, just use the offense-ready timeout cue,
        // because that cue comes first, and the point is for the observer to just shout
        // "1 minute!" to the teams.
        val bothSideState = recordGoalFromCurrentStateAt(
            standardLiveGameState().withPullPromptTarget(PullPromptTarget.BOTH).beginLivePoint(),
            ANIMAL,
            LocalTime.of(11, 5),
        )
        assertEquals(GamePhase.BETWEEN_POINTS, bothSideState.phase)
        assertEquals("Pull in", bothSideState.countdown?.label)
        assertEquals(80, bothSideState.countdown?.durationSeconds)
        val bothSideTimeoutTime = bothSideState.countdown!!.targetEpoch - 1_000L
        val bothSideTimeoutState = bothSideState.assessTimeout(ANIMAL, bothSideTimeoutTime).state
        assertEquals("Pull in", bothSideTimeoutState.countdown?.label)
        assertEquals(150, bothSideTimeoutState.countdown?.durationSeconds)
        assertEquals(
            TimingCueId.TIMEOUT_BETWEEN_POINTS_ONE_MINUTE_FOR_HAND,
            bothSideTimeoutState.countdown?.nextTimingCue(bothSideTimeoutTime)?.id,
        )

        // A team that is out of timeouts cannot call one between points, and because play has not
        // started yet there is no live-point stall-count penalty.
        val outOfTimeoutsState = recordGoalFromCurrentStateAt(
            createLiveGameState(
                setupWithRules(timeoutRules(timeoutsPerHalf = 0, hasFloaterTimeout = false))
            ).beginLivePoint(),
            ANIMAL,
            LocalTime.of(9, 5),
        )
        val outOfTimeoutsTime = outOfTimeoutsState.countdown!!.targetEpoch - 1_000L
        val outOfTimeoutsPreview = outOfTimeoutsState.previewTimeout(VC, outOfTimeoutsTime)
        assertTrue(outOfTimeoutsPreview.event is GameEvent.TeamOutOfTimeouts)
        timeoutResult = outOfTimeoutsState.assessTimeout(VC, outOfTimeoutsTime)
        assertEquals("Viscous Coupling is out of timeouts.", timeoutResult.message())
        assertEquals("Invalid timeout", timeoutResult.event.formatPopupTitle())
        assertEquals(outOfTimeoutsState, timeoutResult.state)
        assertEquals(outOfTimeoutsState, outOfTimeoutsState.chargeTimeout(VC, outOfTimeoutsTime))

        // Before the opening pull, timeouts work the same way, but the countdowns start from the
        // shorter opening-pull times.  So adding 70 seconds gives 90 seconds to signal or
        // 110 seconds to pull, rather than the normal between-points 130 or 150 seconds.
        val openingSignalState = createLiveGameState(
            setupWithRules(timeoutRules(timeoutsPerHalf = 2, hasFloaterTimeout = false))
        )
        assertEquals(GamePhase.PRE_GAME, openingSignalState.phase)
        assertEquals(CountdownKind.OPENING_PULL, openingSignalState.countdown?.kind)
        assertEquals("Signal in", openingSignalState.countdown?.label)
        assertEquals(20, openingSignalState.countdown?.durationSeconds)
        val openingSignalTimeoutState = openingSignalState.assessTimeout(
            VC,
            openingSignalState.countdown!!.targetEpoch - 1_000L,
        ).state
        assertEquals("Signal in", openingSignalTimeoutState.countdown?.label)
        assertEquals(90, openingSignalTimeoutState.countdown?.durationSeconds)

        val openingPullState = createLiveGameState(
            setupWithRules(timeoutRules(timeoutsPerHalf = 2, hasFloaterTimeout = false))
                .copy(openingPullingFromEnd = FieldEnd.NEAR)
        )
        assertEquals(GamePhase.PRE_GAME, openingPullState.phase)
        assertEquals(CountdownKind.OPENING_PULL, openingPullState.countdown?.kind)
        assertEquals("Pull in", openingPullState.countdown?.label)
        assertEquals(40, openingPullState.countdown?.durationSeconds)
        val openingPullTimeoutState = openingPullState.assessTimeout(
            VC,
            openingPullState.countdown!!.targetEpoch - 1_000L,
        ).state
        assertEquals("Pull in", openingPullTimeoutState.countdown?.label)
        assertEquals(110, openingPullTimeoutState.countdown?.durationSeconds)
    }

    /**
     * Test the behavior for a timeout called during a point.
     */
    @Test
    fun timeoutDuringPoint() {
        val VC = TeamId.TEAM_ONE

        // When a timeout is called during a point, it immediately starts a 70 second countdown
        // until the offense needs to be set.
        val livePointState = createLiveGameState(
            setupWithRules(timeoutRules(timeoutsPerHalf = 1, hasFloaterTimeout = false))
        ).beginLivePoint()
        val timeoutResult = livePointState.assessTimeout(VC, 1_000_000L)
        assertEquals(
            "Timeout charged to Viscous Coupling. They have 0 timeouts remaining in this half.",
            timeoutResult.message(),
        )
        val timeoutCountdownState = timeoutResult.state
        assertEquals(1, timeoutCountdownState.teamOne.timeoutsUsedThisHalf)
        assertEquals(0, timeoutCountdownState.timeoutsRemaining(VC))
        assertEquals(GamePhase.LIVE_POINT, timeoutCountdownState.phase)
        assertEquals(CountdownKind.TIME_OUT, timeoutCountdownState.countdown?.kind)
        assertEquals("Offense set in", timeoutCountdownState.countdown?.label)
        assertEquals(70, timeoutCountdownState.countdown?.durationSeconds)
        assertEquals(1_070_000L, timeoutCountdownState.countdown?.targetEpoch)

        // If defense countdowns are not enabled, then the game automatically transitions back
        // into live play at the end of the original timeout countdown.
        assertEquals(
            timeoutCountdownState,
            timeoutCountdownState.applyExpiredCountdownTransitions(
                1_070_000L - 1L,
                showDefenseCountdowns = false,
            ),
        )
        val continuedState = timeoutCountdownState.applyExpiredCountdownTransitions(
            1_070_000L,
            showDefenseCountdowns = false,
        )
        assertEquals(GamePhase.LIVE_POINT, continuedState.phase)
        assertNull(continuedState.countdown)
        assertEquals("Point continued.", continuedState.lastEvent)
        assertEquals("Undo Timeout by Viscous Coupling", continuedState.undoEntry?.label)

        // If the user has enabled defense countdowns, then the UI will have a button to report
        // when the offense is set. We show that here in the canReportOffenseSet helper.
        val explicitTimeoutDefenseState = timeoutCountdownState
        assertTrue(explicitTimeoutDefenseState.canReportOffenseSet(showDefenseCountdowns = true))
        assertFalse(explicitTimeoutDefenseState.canReportOffenseSet(showDefenseCountdowns = false))
        assertEquals(
            explicitTimeoutDefenseState,
            explicitTimeoutDefenseState.applyExpiredCountdownTransitions(
                now = 1_070_000L,
                showDefenseCountdowns = true,
            ),
        )

        // If the user presses Offense is set before the full time, the defense still has a
        // full 90 seconds from the start of the timeout until they need to be set.
        val earlyOffenseSetState = explicitTimeoutDefenseState.reportOffenseSet(1_060_000L)
        assertEquals(CountdownKind.DEFENSE_CHECK, earlyOffenseSetState.countdown?.kind)
        assertEquals("Defense check in", earlyOffenseSetState.countdown?.label)
        assertEquals(30, earlyOffenseSetState.countdown?.durationSeconds)
        assertEquals(1_090_000L, earlyOffenseSetState.countdown?.targetEpoch)
        assertEquals("Offense set; defense check started.", earlyOffenseSetState.lastEvent)

        // If the offense is a little late getting set, the defense has 20 seconds from
        // when the offense is set.
        val lateOffenseSetState = explicitTimeoutDefenseState.reportOffenseSet(1_075_000L)
        assertEquals(CountdownKind.DEFENSE_CHECK, lateOffenseSetState.countdown?.kind)
        assertEquals(20, lateOffenseSetState.countdown?.durationSeconds)
        assertEquals(1_095_000L, lateOffenseSetState.countdown?.targetEpoch)

        // At the end of the defense countdown, the state transitions to live play
        // with no countdown.
        assertEquals(
            lateOffenseSetState,
            lateOffenseSetState.applyExpiredCountdownTransitions(
                now = 1_095_000L - 1L,
                showDefenseCountdowns = true,
            ),
        )
        val expiredDefenseCountdownState = lateOffenseSetState.applyExpiredCountdownTransitions(
            now = 1_095_000L,
            showDefenseCountdowns = true,
        )
        assertEquals(GamePhase.LIVE_POINT, expiredDefenseCountdownState.phase)
        assertNull(expiredDefenseCountdownState.countdown)
        assertEquals("Point continued.", expiredDefenseCountdownState.lastEvent)
        assertEquals(1, expiredDefenseCountdownState.teamOne.timeoutsUsedThisHalf)
        assertEquals(0, expiredDefenseCountdownState.timeoutsRemaining(VC))
        assertEquals(
            "Undo Timeout by Viscous Coupling",
            expiredDefenseCountdownState.undoEntry?.label,
        )

        // If a timeout is called during a point, and the team is out of timeouts, there is
        // a 3 second penalty to the stall count.
        val noTimeoutsResult = timeoutCountdownState.assessTimeout(VC, 1_010_000L)
        assertEquals(
            "Viscous Coupling is out of timeouts.\n\n" +
                "Add three to the stall count. It is a turnover if that is 10 or more.",
            noTimeoutsResult.message(),
        )
        assertEquals("Invalid timeout", noTimeoutsResult.event?.formatPopupTitle())
        assertEquals(timeoutCountdownState, noTimeoutsResult.state)
        assertEquals(timeoutCountdownState, timeoutCountdownState.chargeTimeout(VC, 1_010_000L))
    }

    /**
     * Test timeout availability in various special cases, including:
     * - after a pull countdown has expired
     * - during halftime
     * - after the game is over.
     */
    @Test
    fun timeoutAvailability() {
        val VC = TeamId.TEAM_ONE
        val ANIMAL = TeamId.TEAM_TWO

        // In ordinary two-per-half rules, both teams return to two timeouts at halftime.
        var state = createLiveGameState(
            setupWithRules(timeoutRules(timeoutsPerHalf = 2, hasFloaterTimeout = false))
        )
        state = state.assessTimeout(VC, state.countdown!!.targetEpoch - 1_000L).state
        state = state.beginLivePoint()
        state = state.assessTimeout(VC, 1_000_000L).state
        state = state.applyExpiredCountdownTransitions(1_070_000L, showDefenseCountdowns = false)
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

        // A timeout called when the pull countdown has expired is still before the pull,
        // so it is treated as a between-points timeout rather than an in-point timeout.
        val expiredPullState = recordGoalFromCurrentStateAt(
            standardLiveGameState().beginLivePoint(),
            ANIMAL,
            LocalTime.of(11, 5),
        )
        assertEquals(GamePhase.BETWEEN_POINTS, expiredPullState.phase)
        assertEquals(CountdownKind.BETWEEN_POINTS, expiredPullState.countdown?.kind)
        val expiredCountdownNow = expiredPullState.countdown!!.targetEpoch + 1L
        var timeoutResult = expiredPullState.assessTimeout(
            VC,
            expiredCountdownNow,
        )
        val expiredTimeoutState = timeoutResult.state
        assertEquals(
            "Timeout charged to Viscous Coupling. They have 1 timeout remaining in this half.",
            timeoutResult.message(),
        )
        assertEquals(GamePhase.BETWEEN_POINTS, expiredTimeoutState.phase)
        assertEquals(CountdownKind.BETWEEN_POINTS, expiredTimeoutState.countdown?.kind)
        assertEquals(1, expiredTimeoutState.teamOne.timeoutsUsedThisHalf)
        assertEquals("Signal in", expiredTimeoutState.countdown?.label)
        assertEquals(130, expiredTimeoutState.countdown?.durationSeconds)
        assertEquals(
            expiredPullState.countdown!!.targetEpoch + 70_000L,
            expiredTimeoutState.countdown?.targetEpoch,
        )

        // If the UI has already switched to the expired-pull action surface, the timeout still
        // represents a call at the deadline.  The countdown keeps the normal timeout-extension
        // duration metadata for cue selection, but only 70 seconds remain on the visible timer.
        val expiredDecisionState = expiredPullState.expiredPullDecisionState()
        assertTrue(expiredDecisionState.hasExpiredPullActions())
        assertTrue(expiredDecisionState.canRequestTimeout(expiredCountdownNow))
        assertTrue(
            expiredDecisionState.previewTimeout(
                VC,
                expiredCountdownNow,
            ).event is GameEvent.TimeoutCharged
        )
        timeoutResult = expiredDecisionState.assessTimeout(VC, expiredCountdownNow)
        val expiredDecisionTimeoutState = timeoutResult.state
        assertEquals(GamePhase.BETWEEN_POINTS, expiredDecisionTimeoutState.phase)
        assertEquals(CountdownKind.BETWEEN_POINTS, expiredDecisionTimeoutState.countdown?.kind)
        assertEquals("Signal in", expiredDecisionTimeoutState.countdown?.label)
        assertEquals(130, expiredDecisionTimeoutState.countdown?.durationSeconds)
        assertEquals(
            expiredCountdownNow + 70_000L,
            expiredDecisionTimeoutState.countdown?.targetEpoch,
        )
        assertEquals(
            Duration.ofSeconds(70),
            expiredDecisionTimeoutState.countdown?.remainingDuration(expiredCountdownNow),
        )
        assertFalse(expiredDecisionTimeoutState.hasExpiredPullActions())

        // The same expired-pull action surface before the opening pull keeps opening-pull
        // timing metadata while showing only the timeout's 70 seconds.
        val expiredOpeningPullState = standardLiveGameState()
        val expiredOpeningPullNow = expiredOpeningPullState.countdown!!.targetEpoch + 1L
        val expiredOpeningDecisionState = expiredOpeningPullState.expiredPullDecisionState()
        timeoutResult = expiredOpeningDecisionState.assessTimeout(VC, expiredOpeningPullNow)
        val expiredOpeningTimeoutState = timeoutResult.state
        assertEquals(GamePhase.PRE_GAME, expiredOpeningTimeoutState.phase)
        assertEquals(CountdownKind.OPENING_PULL, expiredOpeningTimeoutState.countdown?.kind)
        assertEquals("Signal in", expiredOpeningTimeoutState.countdown?.label)
        assertEquals(90, expiredOpeningTimeoutState.countdown?.durationSeconds)
        assertEquals(
            Duration.ofSeconds(70),
            expiredOpeningTimeoutState.countdown?.remainingDuration(expiredOpeningPullNow),
        )
        assertFalse(expiredOpeningTimeoutState.hasExpiredPullActions())

        // A timeout is not available while the halftime countdown itself is still running.
        val halftimeEnd = state.countdown!!.targetEpoch
        timeoutResult = state.assessTimeout(VC, halftimeEnd - 1L)
        assertEquals("Timeouts are not available now.", timeoutResult.message())
        assertEquals("Timeout not possible now", timeoutResult.event?.formatPopupTitle())
        assertEquals(state, (timeoutResult.event as GameEvent.TimeoutUnavailable).state)
        assertEquals(state, timeoutResult.state)

        // After halftime has elapsed but before the pull, a timeout extends the pull countdown.
        // Here we have to manually transition the countdown that would happen automatically
        // at the end of halftime.
        val afterHalftimeState = state.applyExpiredCountdownTransitions(
            halftimeEnd + 1L,
            showDefenseCountdowns = false,
        )
        assertEquals(GamePhase.BETWEEN_POINTS, afterHalftimeState.phase)
        timeoutResult = afterHalftimeState.assessTimeout(VC, halftimeEnd + 1L)
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

        // The UI hides timeout actions after game over; stale timeout commands are no-ops.
        val gameOverTimeoutState = standardLiveGameState(
            rules = GameRules(
                gameTo = 1,
                useHalfCap = false,
                useSoftCap = false,
                useHardCap = false,
            ),
        ).recordGoalFromCurrentState(
            VC,
            1_150_000L,
        )
        assertEquals(GamePhase.GAME_OVER, gameOverTimeoutState.phase)
        timeoutResult = gameOverTimeoutState.assessTimeout(VC, 1_160_000L)
        assertEquals("Timeouts are not available now.", timeoutResult.message())
        assertEquals(gameOverTimeoutState, timeoutResult.state)
        assertEquals(gameOverTimeoutState, gameOverTimeoutState.chargeTimeout(VC, 1_160_000L))
        assertTrue(
            gameOverTimeoutState.previewTimeout(
                VC,
                1_160_000L,
            ).event is GameEvent.TimeoutUnavailable
        )
        assertFalse(gameOverTimeoutState.canRequestTimeout(1_160_000L))
    }

    /**
     * Test floater-timeout allowance before and after halftime.
     */
    @Test
    fun floaterTimeoutCarryover() {
        val VC = TeamId.TEAM_ONE
        val ANIMAL = TeamId.TEAM_TWO

        // With one timeout per half plus a floater, using both first-half timeouts means
        // the team only has 1 timeout in the second half.
        var state = createLiveGameState(
            setupWithRules(timeoutRules(timeoutsPerHalf = 1, hasFloaterTimeout = true))
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

        // If the team only uses one in the first half, the floater carries over to the
        // second half.
        state = createLiveGameState(
            setupWithRules(timeoutRules(timeoutsPerHalf = 1, hasFloaterTimeout = true))
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

        // The rest of these don't really happen in practice, but make sure the app
        // handles them correctly:
        // Zero per half plus a floater is one timeout per game.
        state = createLiveGameState(
            setupWithRules(timeoutRules(timeoutsPerHalf = 0, hasFloaterTimeout = true))
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

        // Zero per half (no floater) means no timeouts at all.
        state = createLiveGameState(
            setupWithRules(timeoutRules(timeoutsPerHalf = 0, hasFloaterTimeout = false))
        )
        assertEquals(0, state.teamOne.timeoutsUsedThisHalf)
        assertEquals(0, state.timeoutsAllowedThisHalf(VC))
        assertEquals(0, state.timeoutsRemaining(VC))
        val timeoutResult = state.assessTimeout(VC, 2_400_000L)
        assertEquals("Viscous Coupling is out of timeouts.", timeoutResult.message())
        assertEquals(state, timeoutResult.state)

        // Two per half plus a floater
        state = createLiveGameState(
            setupWithRules(timeoutRules(timeoutsPerHalf = 2, hasFloaterTimeout = true))
        )
        assertEquals(0, state.teamOne.timeoutsUsedThisHalf)
        assertEquals(3, state.timeoutsAllowedThisHalf(VC))
        state = state.assessTimeout(VC, state.countdown!!.targetEpoch - 1_000L).state
        state = scoreToHalftime(state, VC, 2_500_000L)
        assertEquals(1, state.teamOne.firstHalfTimeoutsUsed)
        assertEquals(0, state.teamOne.timeoutsUsedThisHalf)
        assertEquals(3, state.timeoutsAllowedThisHalf(VC))
        assertEquals(3, state.timeoutsRemaining(VC))
    }

    /**
     * Test updating timeout rules after timeouts have already been used.
     */
    @Test
    fun timeoutRuleEdits() {
        val VC = TeamId.TEAM_ONE
        val ANIMAL = TeamId.TEAM_TWO

        // If a later rule set allows fewer timeouts than a team has already used, then
        // make sure the number available doesn't go negative.
        var state = createLiveGameState(
            setupWithRules(timeoutRules(timeoutsPerHalf = 2, hasFloaterTimeout = false))
        )
        state = state.assessTimeout(VC, state.countdown!!.targetEpoch - 1_000L).state
        state = state.beginLivePoint()
        state = state.assessTimeout(VC, 2_550_000L).state
        assertEquals(2, state.teamOne.timeoutsUsedThisHalf)
        assertEquals(0, state.timeoutsRemaining(VC))

        // Here, VC used 2 already, but then we change it to 1/half.
        // They now still have 0 left, not -1.
        state = applySetupToLiveGame(
            state,
            state.copy(rules = timeoutRules(timeoutsPerHalf = 1, hasFloaterTimeout = false)),
            2_560_000L,
        )
        assertEquals(1, state.timeoutsAllowedThisHalf(VC))
        assertEquals(2, state.teamOne.timeoutsUsedThisHalf)
        assertEquals(0, state.timeoutsRemaining(VC))

        // Expanding the rule set again recomputes remaining from the same used count.
        state = applySetupToLiveGame(
            state,
            state.copy(
                rules = timeoutRules(
                    timeoutsPerHalf = 3,
                    hasFloaterTimeout = false,
                )
            ),
            2_570_000L,
        )
        assertEquals(3, state.timeoutsAllowedThisHalf(VC))
        assertEquals(2, state.teamOne.timeoutsUsedThisHalf)
        assertEquals(1, state.timeoutsRemaining(VC))

        // Updating rules mid-half remaps both teams from the number already used.
        state = createLiveGameState(
            setupWithRules(timeoutRules(timeoutsPerHalf = 2, hasFloaterTimeout = false))
        )
        state = state.assessTimeout(VC, state.countdown!!.targetEpoch - 1_000L).state
        state = applySetupToLiveGame(
            state,
            state.copy(rules = timeoutRules(timeoutsPerHalf = 1, hasFloaterTimeout = false)),
            2_600_000L,
        )
        assertEquals(1, state.timeoutsAllowedThisHalf(VC))
        assertEquals(1, state.teamOne.timeoutsUsedThisHalf)
        assertEquals(0, state.timeoutsRemaining(VC))
        assertEquals(0, state.teamTwo.timeoutsUsedThisHalf)
        assertEquals(1, state.timeoutsAllowedThisHalf(ANIMAL))
        assertEquals(1, state.timeoutsRemaining(ANIMAL))

        // Adding a floater mid-half also remaps both teams from the same used counts.
        state = applySetupToLiveGame(
            state,
            state.copy(rules = timeoutRules(timeoutsPerHalf = 2, hasFloaterTimeout = true)),
            2_700_000L,
        )
        assertEquals(3, state.timeoutsAllowedThisHalf(VC))
        assertEquals(1, state.teamOne.timeoutsUsedThisHalf)
        assertEquals(2, state.timeoutsRemaining(VC))
        assertEquals(0, state.teamTwo.timeoutsUsedThisHalf)
        assertEquals(3, state.timeoutsAllowedThisHalf(ANIMAL))
        assertEquals(3, state.timeoutsRemaining(ANIMAL))

        // Updating rules in the second half remaps from the current-half used counts.
        state = scoreToHalftime(state, VC, 2_800_000L)
        state = state.beginLivePoint()
        state = state.assessTimeout(ANIMAL, 2_850_000L).state
        state = applySetupToLiveGame(
            state,
            state.copy(rules = timeoutRules(timeoutsPerHalf = 1, hasFloaterTimeout = false)),
            2_900_000L,
        )
        assertEquals(0, state.teamOne.timeoutsUsedThisHalf)
        assertEquals(1, state.timeoutsAllowedThisHalf(VC))
        assertEquals(1, state.timeoutsRemaining(VC))
        assertEquals(1, state.teamTwo.timeoutsUsedThisHalf)
        assertEquals(1, state.timeoutsAllowedThisHalf(ANIMAL))
        assertEquals(0, state.timeoutsRemaining(ANIMAL))

        // In the second half, floater rule edits also account for first-half timeout usage.
        state = createLiveGameState(
            setupWithRules(timeoutRules(timeoutsPerHalf = 1, hasFloaterTimeout = true))
        )
        state = state.assessTimeout(VC, state.countdown!!.targetEpoch - 1_000L).state
        state = scoreToHalftime(state, VC, 3_000_000L)
        assertEquals(1, state.teamOne.firstHalfTimeoutsUsed)
        assertEquals(0, state.teamOne.timeoutsUsedThisHalf)
        assertEquals(2, state.timeoutsAllowedThisHalf(VC))
        assertEquals(2, state.timeoutsRemaining(VC))

        // Using a second-half timeout consumes one of the carried second-half allowances.
        state = state.beginLivePoint()
        state = state.assessTimeout(VC, 3_100_000L).state
        assertEquals(1, state.teamOne.timeoutsUsedThisHalf)
        assertEquals(1, state.timeoutsRemaining(VC))

        // Shrinking to zero per-half timeouts removes the carried floater once one has been used.
        state = applySetupToLiveGame(
            state,
            state.copy(rules = timeoutRules(timeoutsPerHalf = 0, hasFloaterTimeout = true)),
            3_200_000L,
        )
        assertEquals(1, state.teamOne.timeoutsUsedThisHalf)
        assertEquals(0, state.timeoutsAllowedThisHalf(VC))
        assertEquals(0, state.timeoutsRemaining(VC))

        // Expanding to two per-half plus a floater restores remaining from the used count.
        state = applySetupToLiveGame(
            state,
            state.copy(rules = timeoutRules(timeoutsPerHalf = 2, hasFloaterTimeout = true)),
            3_300_000L,
        )
        assertEquals(1, state.teamOne.timeoutsUsedThisHalf)
        assertEquals(3, state.timeoutsAllowedThisHalf(VC))
        assertEquals(2, state.timeoutsRemaining(VC))
    }

    /**
     * Test manual timeout-count corrections.
     */
    @Test
    fun timeoutCorrections() {
        val VC = TeamId.TEAM_ONE
        val ANIMAL = TeamId.TEAM_TWO

        // Manual timeout correction sets the used counts directly and is undo-backed.
        var state = createLiveGameState(
            setupWithRules(timeoutRules(timeoutsPerHalf = 2, hasFloaterTimeout = true))
        )
        state = scoreToHalftime(state, VC, 3_000_000L).beginLivePoint()
        state = state.assessTimeout(VC, 3_100_000L).state
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

        // After halftime, the correction can also change first-half counts used for floater carryover.
        val eventLogSizeBeforeFirstHalfAdjustment = state.eventLog.size
        state = state.adjustTimeouts(
            teamOneTimeoutsUsed = state.teamOne.timeoutsUsedThisHalf,
            teamTwoTimeoutsUsed = state.teamTwo.timeoutsUsedThisHalf,
            teamOneFirstHalfTimeoutsUsed = 3,
            teamTwoFirstHalfTimeoutsUsed = 1,
            now = 3_200_000L,
        )
        assertEquals(3, state.teamOne.firstHalfTimeoutsUsed)
        assertEquals(1, state.teamTwo.firstHalfTimeoutsUsed)
        assertEquals(2, state.timeoutsAllowedThisHalf(VC))
        assertEquals(3, state.timeoutsAllowedThisHalf(ANIMAL))
        assertEquals(eventLogSizeBeforeFirstHalfAdjustment, state.eventLog.size)

        // The dialog uses the same rule helper to update second-half allowance live while editing.
        assertEquals(
            3,
            state.rules.timeoutsAllowedThisHalf(halftimeTaken = true, firstHalfTimeoutsUsed = 2),
        )
        assertEquals(
            2,
            state.rules.timeoutsAllowedThisHalf(halftimeTaken = true, firstHalfTimeoutsUsed = 3),
        )
    }

    /// Return game rules with cap timing disabled for focused timeout scenarios.
    private fun timeoutRules(
        timeoutsPerHalf: Int = 2,
        hasFloaterTimeout: Boolean = false,
    ): GameRules {
        return GameRules(
            gameTo = 5,
            useHalfCap = false,
            useSoftCap = false,
            useHardCap = false,
            timeoutsPerHalf = timeoutsPerHalf,
            hasFloaterTimeout = hasFloaterTimeout,
        )
    }

    /**
     * Build setup state for a timeout-rule scenario.
     *
     * @param rules The timeout rules to install in the setup.
     * @param pullingFromEnd The opening pulling end, used to exercise both countdown targets.
     */
    private fun setupWithRules(
        rules: GameRules,
        pullingFromEnd: FieldEnd = FieldEnd.FAR,
    ): GameState {
        return standardGameSetup(
            startTime = LocalTime.of(9, 0),
            rules = rules,
            pullingTeam = TeamId.TEAM_ONE,
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
    private fun scoreToHalftime(
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
}
