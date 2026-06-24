package rmjarvis.ultiobserver

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/// Tests for model actions that are surfaced through the live game's More actions menu.
class TestMoreActions : GameDomainTestFixtures() {
    /**
     * Test manual score correction from More actions.
     */
    @Test
    fun scoreAdjustments() {
        // Adjust score and verify negative inputs are clamped, with a normal undo entry.
        var state = standardLiveGameState()
        val beforeScoreAdjustment = state
        state = state.adjustScore(teamOneScore = -2, teamTwoScore = 4)
        assertEquals(0, state.teamOne.score)
        assertEquals(4, state.teamTwo.score)
        assertEquals("Score adjusted.", state.lastEvent)
        assertEquals("Undo Score adjustment", state.undoEntry?.label)
        assertEquals(beforeScoreAdjustment, state.undoEntry?.previous)

        // Reapplying the same score is a no-op, just like canceling the correction dialog.
        assertEquals(state, state.adjustScore(teamOneScore = 0, teamTwoScore = 4))
    }

    /**
     * Test field-display flips without changing the underlying game orientation.
     */
    @Test
    fun fieldDisplayFlip() {
        val VC = TeamId.TEAM_ONE

        // Flip field display and verify game orientation, countdown, and pull prompts are
        // unchanged.
        var state = standardLiveGameState(pullingTeam = VC, pullingFromEnd = FieldEnd.FAR)
        val countdownBeforeFlip = state.countdown!!
        assertEquals(FieldEnd.FAR, state.topDisplayedEnd)
        assertEquals(VC, state.nearAttackingTeam)
        assertEquals(VC, state.pullingTeam)
        assertEquals(FieldEnd.FAR, state.pullingFromEnd)
        assertEquals(PullPromptTarget.NEAR, state.pullPromptTarget)
        assertEquals(CountdownKind.OPENING_PULL, countdownBeforeFlip.kind)
        assertEquals("Signal in", countdownBeforeFlip.label)
        assertEquals(20, countdownBeforeFlip.durationSeconds)
        state = state.flipFieldDisplay()
        assertEquals(FieldEnd.NEAR, state.topDisplayedEnd)
        assertEquals(VC, state.nearAttackingTeam)
        assertEquals(VC, state.pullingTeam)
        assertEquals(FieldEnd.FAR, state.pullingFromEnd)
        assertEquals(PullPromptTarget.NEAR, state.pullPromptTarget)
        assertEquals(countdownBeforeFlip, state.countdown)
        assertEquals("Field display flipped.", state.lastEvent)
        assertEquals("Undo Flip field display", state.undoEntry?.label)
        assertUndoRestores(state.undoEntry!!.previous, state)

        // Flipping while an in-point timeout countdown is active preserves that timeout
        // countdown.
        state = standardLiveGameState().beginLivePoint()
        state = state.assessTimeout(VC, 100_000L).state
        val liveTimeoutCountdownBeforeFlip = state.countdown
        state = state.flipFieldDisplay()
        assertEquals(CountdownKind.TIME_OUT, state.countdown?.kind)
        assertEquals(liveTimeoutCountdownBeforeFlip, state.countdown)

        // Flipping display during a live point with no active countdown keeps the point live and
        // countdown-free.
        state = standardLiveGameState().beginLivePoint()
        state = state.flipFieldDisplay()
        assertEquals(GamePhase.LIVE_POINT, state.phase)
        assertNull(state.countdown)
        state = state.swapPullingTeam()
        assertEquals(GamePhase.LIVE_POINT, state.phase)
        assertNull(state.countdown)

        // Flipping field display during halftime preserves the halftime clock itself.
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
        state = state.startHalftimeNow(timestampAt(state, LocalTime.of(11, 10)))
        val halftimeCountdownBeforeFlip = state.countdown
        state = state.flipFieldDisplay()
        assertEquals(CountdownKind.HALFTIME, state.countdown?.kind)
        assertEquals(halftimeCountdownBeforeFlip, state.countdown)
    }

    /**
     * Test pull-prompt changes from More actions.
     */
    @Test
    fun pullPromptAdjustments() {
        val VC = TeamId.TEAM_ONE

        // Pull-prompt changes are independent of display orientation and field positions.
        var state = standardLiveGameState(pullingTeam = VC, pullingFromEnd = FieldEnd.FAR)
        assertEquals(state, state.withPullPromptTarget(PullPromptTarget.NEAR))
        state = state.flipFieldDisplay()
        val flippedDisplayState = state
        state = state.withPullPromptTarget(PullPromptTarget.BOTH)
        assertEquals(FieldEnd.NEAR, state.topDisplayedEnd)
        assertEquals(VC, state.nearAttackingTeam)
        assertEquals(VC, state.pullingTeam)
        assertEquals(FieldEnd.FAR, state.pullingFromEnd)
        assertEquals(PullPromptTarget.BOTH, state.pullPromptTarget)
        assertEquals("Pull in", state.countdown?.label)
        assertEquals(40, state.countdown?.durationSeconds)
        assertEquals("Pull prompts changed.", state.lastEvent)
        assertEquals("Undo Change pull prompts", state.undoEntry?.label)
        assertUndoRestores(flippedDisplayState, state)

        // Changing from near-end to far-end pull prompts retargets an active pull countdown.
        state = standardLiveGameState(pullingTeam = VC, pullingFromEnd = FieldEnd.FAR)
        val nearPromptCountdown = state.countdown!!
        assertEquals("Signal in", nearPromptCountdown.label)
        assertEquals(20, nearPromptCountdown.durationSeconds)
        state = state.withPullPromptTarget(PullPromptTarget.FAR)
        assertEquals(PullPromptTarget.FAR, state.pullPromptTarget)
        assertEquals("Pull in", state.countdown?.label)
        assertEquals(40, state.countdown?.durationSeconds)
        assertEquals(nearPromptCountdown.targetEpoch + 20_000L, state.countdown?.targetEpoch)

        // Disabling pull prompts keeps the active countdown but suppresses its timing cue.
        state = state.withPullPromptTarget(PullPromptTarget.NEITHER)
        assertEquals(PullPromptTarget.NEITHER, state.pullPromptTarget)
        assertEquals("Pull in", state.countdown?.label)
        assertEquals(40, state.countdown?.durationSeconds)
        assertNull(state.countdown?.nextTimingCue(nearPromptCountdown.targetEpoch))

        // Changing pull prompts without an active countdown stores the target without starting
        // one.
        val noCountdownPromptState = standardLiveGameState().copy(countdown = null)
            .withPullPromptTarget(PullPromptTarget.BOTH)
        assertEquals(PullPromptTarget.BOTH, noCountdownPromptState.pullPromptTarget)
        assertNull(noCountdownPromptState.countdown)
    }

    /**
     * Test pulling-team swaps from More actions.
     */
    @Test
    fun pullingTeamSwap() {
        val VC = TeamId.TEAM_ONE
        val ANIMAL = TeamId.TEAM_TWO

        // Swapping pulling team changes only pulling team/end while preserving team field
        // positions.
        var state = standardLiveGameState(pullingTeam = VC, pullingFromEnd = FieldEnd.FAR)
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
        assertEquals("Undo Swap pulling team", state.undoEntry?.label)
        assertUndoRestores(state.undoEntry!!.previous, state)

        // Timeout-extended between-points offense-ready countdowns retarget to pull timing when
        // the pulling team swaps.
        state = standardLiveGameState().beginLivePoint()
        state = recordGoalFromCurrentStateAt(state, ANIMAL, LocalTime.of(11, 5))
        assertEquals("Signal in", state.countdown?.label)
        assertEquals(60, state.countdown?.durationSeconds)
        state = state.assessTimeout(VC, state.countdown!!.targetEpoch - 1_000L).state
        val extendedCountdownBeforeSwap = state.countdown
        assertEquals(130, extendedCountdownBeforeSwap?.durationSeconds)
        state = state.swapPullingTeam()
        assertEquals("Pull in", state.countdown?.label)
        assertEquals(150, state.countdown?.durationSeconds)
        assertEquals(
            extendedCountdownBeforeSwap!!.targetEpoch + 20_000L,
            state.countdown?.targetEpoch,
        )

        // Timeout-extended between-points pull countdowns retarget to offense-ready timing when
        // the pulling team swaps.
        state = standardLiveGameState(pullingFromEnd = FieldEnd.NEAR).beginLivePoint()
        state = recordGoalFromCurrentStateAt(state, ANIMAL, LocalTime.of(11, 5))
        assertEquals("Pull in", state.countdown?.label)
        assertEquals(80, state.countdown?.durationSeconds)
        state = state.assessTimeout(VC, state.countdown!!.targetEpoch - 1_000L).state
        val extendedPullCountdownBeforeSwap = state.countdown
        assertEquals(150, extendedPullCountdownBeforeSwap?.durationSeconds)
        state = state.swapPullingTeam()
        assertEquals("Signal in", state.countdown?.label)
        assertEquals(130, state.countdown?.durationSeconds)
        assertEquals(
            extendedPullCountdownBeforeSwap!!.targetEpoch - 20_000L,
            state.countdown?.targetEpoch,
        )
    }

    /**
     * Test starting halftime manually from More actions.
     */
    @Test
    fun manualHalftime() {
        val VC = TeamId.TEAM_ONE
        val ANIMAL = TeamId.TEAM_TWO

        // Start halftime manually from a between-points state with first-half timeouts used.
        var state = standardLiveGameState(
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
        assertEquals(GamePhase.HALFTIME, state.phase)
        assertTrue(state.halftimeTaken)

        // Used timeouts are moved into the first-half totals and reset for the new half.
        assertEquals(1, state.teamOne.firstHalfTimeoutsUsed)
        assertEquals(0, state.teamOne.timeoutsUsedThisHalf)
        assertEquals(2, state.teamTwo.firstHalfTimeoutsUsed)
        assertEquals(0, state.teamTwo.timeoutsUsedThisHalf)

        // The second-half pulling team is the opposite of the first pull.
        // And they pull from the same end as the first pull.
        assertEquals(ANIMAL, state.pullingTeam)
        assertEquals(FieldEnd.FAR, state.pullingFromEnd)
        assertEquals(ANIMAL, state.nearAttackingTeam)

        // Manual halftime starts the configured halftime countdown from the action time.
        assertEquals(CountdownKind.HALFTIME, state.countdown?.kind)
        assertEquals(360, state.countdown?.durationSeconds)
        assertEquals(manualHalftimeStartTime + 360_000L, state.countdown?.targetEpoch)

        // Manual halftime keeps an undo entry back to the pre-halftime state.
        assertEquals("Undo Start halftime", state.undoEntry?.label)
        assertEquals(beforeManualHalftime, state.undoEntry?.previous)

        // The UI hides Start halftime outside between-points state; the model rejects those
        // calls too.
        assertEquals(state, state.startHalftimeNow(timestampAt(state, LocalTime.of(11, 11))))
        val livePointState = standardLiveGameState().beginLivePoint()
        assertEquals(
            livePointState,
            livePointState.startHalftimeNow(timestampAt(livePointState, LocalTime.of(11, 11))),
        )
        val gameOverState = endGameNowAt(state, LocalTime.of(11, 12))
        assertEquals(
            gameOverState,
            gameOverState.startHalftimeNow(timestampAt(gameOverState, LocalTime.of(11, 13))),
        )
    }

    /**
     * Test ending the game manually from More actions.
     */
    @Test
    fun manualGameEnd() {
        // Manually end the game and verify end time, phase, countdown clearing, and undo entry.
        val beforeManualEnd = standardLiveGameState()
        var state = endGameNowAt(beforeManualEnd, LocalTime.of(11, 40))
        assertEquals(GamePhase.GAME_OVER, state.phase)
        assertEquals(timestampAt(state, LocalTime.of(11, 40)), state.endEpoch)
        assertNull(state.countdown)
        assertNull(state.pendingCapOffer)
        assertEquals("Game over.", state.lastEvent)
        assertEquals("Undo End game", state.undoEntry?.label)
        assertEquals(beforeManualEnd, state.undoEntry?.previous)

        // Undo game over restores the saved live state from before End game was applied.
        state = state.undoLastAction()
        assertEquals(beforeManualEnd, state.copy(redoEntry = null))
        assertNotNull(state.redoEntry)
    }
}
