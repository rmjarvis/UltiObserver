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

class TestGameOtherActions : GameModelTestFixtures() {
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
}
