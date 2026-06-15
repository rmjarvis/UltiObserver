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

/// Tests for model actions that are surfaced through the live game's Other menu.
class TestGameOtherActions : GameDomainTestFixtures() {
    /**
     * Test manual correction and less-common actions that are surfaced through the Other menu.
     * These are model actions even though the menu is just one UI access path.
     */
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
        assertEquals("Undo Score adjustment", state.undoEntry?.label)
        assertEquals(beforeScoreAdjustment, state.undoEntry?.previous)

        // Reapplying the same score is still undo-backed, but should not create a correction log entry.
        val eventLogBeforeNoopScoreAdjustment = state.eventLog
        state = state.adjustScore(teamOneScore = 0, teamTwoScore = 4)
        assertEquals(eventLogBeforeNoopScoreAdjustment, state.eventLog)
        assertEquals("Score adjusted.", state.lastEvent)

        // Flip field display and verify game orientation, countdown, and pull prompts are unchanged.
        state = standardLiveGameState(pullingTeam = VC, pullingFromEnd = FieldEnd.FAR)
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

        // Flipping while an in-point timeout countdown is active should preserve that timeout countdown.
        state = standardLiveGameState().beginLivePoint()
        state = state.assessTimeout(VC, 100_000L).state
        val liveTimeoutCountdownBeforeFlip = state.countdown
        state = state.flipFieldDisplay()
        assertEquals(CountdownKind.TIME_OUT, state.countdown?.kind)
        assertEquals(liveTimeoutCountdownBeforeFlip, state.countdown)

        // Flipping display during a live point with no active countdown keeps the point live and countdown-free.
        state = standardLiveGameState().beginLivePoint()
        state = state.flipFieldDisplay()
        assertEquals(GamePhase.LIVE_POINT, state.phase)
        assertNull(state.countdown)
        state = state.swapPullingTeam()
        assertEquals(GamePhase.LIVE_POINT, state.phase)
        assertNull(state.countdown)

        // Pull-prompt changes are independent of display orientation and field positions.
        state = standardLiveGameState(pullingTeam = VC, pullingFromEnd = FieldEnd.FAR)
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
        state = state.withPullPromptTarget(PullPromptTarget.NEITHER)
        assertEquals(PullPromptTarget.NEITHER, state.pullPromptTarget)
        assertEquals("Pull in", state.countdown?.label)
        assertEquals(40, state.countdown?.durationSeconds)
        assertNull(state.countdown?.nextTimingCue(nearPromptCountdown.targetEpoch))

        // Timeout-extended between-points countdowns still swap between offense-ready and pull timing.
        state = standardLiveGameState()
        state = state.assessTimeout(VC, state.countdown!!.targetEpoch - 1_000L).state
        val extendedCountdownBeforeSwap = state.countdown
        assertEquals(90, extendedCountdownBeforeSwap?.durationSeconds)
        state = state.swapPullingTeam()
        assertEquals("Pull in", state.countdown?.label)
        assertEquals(110, state.countdown?.durationSeconds)
        assertEquals(extendedCountdownBeforeSwap!!.targetEpoch + 20_000L, state.countdown?.targetEpoch)

        state = standardLiveGameState(pullingFromEnd = FieldEnd.NEAR)
        state = state.assessTimeout(VC, state.countdown!!.targetEpoch - 1_000L).state
        val extendedPullCountdownBeforeSwap = state.countdown
        assertEquals(110, extendedPullCountdownBeforeSwap?.durationSeconds)
        state = state.swapPullingTeam()
        assertEquals("Signal in", state.countdown?.label)
        assertEquals(90, state.countdown?.durationSeconds)
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
        assertEquals("Undo Swap pulling team", state.undoEntry?.label)
        assertUndoRestores(state.undoEntry!!.previous, state)

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
        assertEquals(GamePhase.HALFTIME, state.phase)
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
        assertEquals("Undo Start halftime", state.undoEntry?.label)
        assertEquals(beforeManualHalftime, state.undoEntry?.previous)

        // Flipping field display during halftime preserves the halftime clock itself.
        val halftimeCountdownBeforeFlip = state.countdown
        state = state.flipFieldDisplay()
        assertEquals(CountdownKind.HALFTIME, state.countdown?.kind)
        assertEquals(halftimeCountdownBeforeFlip, state.countdown)

        // The UI hides Start halftime outside between-points state; the model rejects those calls too.
        assertEquals(state, state.startHalftimeNow(timestampAt(state, LocalTime.of(11, 11))))
        val livePointState = standardLiveGameState().beginLivePoint()
        assertEquals(livePointState, livePointState.startHalftimeNow(timestampAt(livePointState, LocalTime.of(11, 11))))
        val gameOverState = endGameNowAt(state, LocalTime.of(11, 12))
        assertEquals(gameOverState, gameOverState.startHalftimeNow(timestampAt(gameOverState, LocalTime.of(11, 13))))

        // Manually end the game and verify end time, phase, countdown clearing, and undo entry.
        val beforeManualEnd = standardLiveGameState()
        state = endGameNowAt(beforeManualEnd, LocalTime.of(11, 40))
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
