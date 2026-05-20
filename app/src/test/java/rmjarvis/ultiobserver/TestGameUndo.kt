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

/// Tests for undo and redo behavior across representative model actions.
class TestGameUndo : GameDomainTestFixtures() {
    /**
     * Test the undo mechanism through user-visible actions rather than private snapshots.
     * Include ordinary undo, corrections, cap application, halftime, and game-over cases.
     */
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
        val undoneStartPoint = assertUndoRestores(beforeStartPoint, state)
        val nudgedCountdown = undoneStartPoint.addTimeToCountdown(10)
        assertNotNull(nudgedCountdown.redoEntry)

        // Record a goal from a live point and verify undo restores the in-point state.
        val beforeLiveGoal = state
        state = recordGoalAt(state, VC, LocalTime.of(11, 5))
        assertEquals(1, state.teamOne.score)
        assertEquals("Undo Goal by Viscous Coupling", state.undoEntry?.label)
        val undoneLiveGoal = assertUndoRestores(beforeLiveGoal, state)
        state = undoneLiveGoal.assessTimeout(VC, 800_000L).state
        assertNull(state.redoEntry)

        // Record a goal from between points and verify undo returns to the implicit live-point state.
        state = standardLiveGameState()
        val betweenPointsBeforeGoal = state
        state = recordGoalFromCurrentStateAt(state, VC, LocalTime.of(11, 5))
        val implicitLiveState = state.undoLastAction()
        assertEquals(GamePhase.LIVE_POINT, implicitLiveState.phase)
        assertNull(implicitLiveState.countdown)
        assertEquals(0, implicitLiveState.teamOne.score)
        assertEquals(0, implicitLiveState.teamTwo.score)
        assertUndoRestores(betweenPointsBeforeGoal, implicitLiveState)

        // Undo timeout, card, technical foul, offsides, and false-start actions.
        state = standardLiveGameState().beginLivePoint()
        val beforeTimeout = state
        state = state.assessTimeout(ANIMAL, 300_000L).state
        assertUndoRestores(beforeTimeout, state)

        state = standardLiveGameState()
        val beforeCard = state
        state = state.assessYellowCard(VC, "17").state
        assertUndoRestores(beforeCard, state)

        state = standardLiveGameState()
        val beforeTf = state
        state = state.assessTechnicalFoul(ANIMAL).state
        assertUndoRestores(beforeTf, state)

        state = standardLiveGameState()
        val beforeOffsides = state
        state = state.recordOffsides()
        assertUndoRestores(beforeOffsides, state)

        state = standardLiveGameState()
        val beforeFalseStart = state
        state = state.recordFalseStart()
        assertUndoRestores(beforeFalseStart, state)

        // Undo manual score, timeout, card/TF, and pull-infraction corrections.
        state = standardLiveGameState()
        val beforeScoreCorrection = state
        state = state.adjustScore(2, 3)
        assertUndoRestores(beforeScoreCorrection, state)

        val beforeTimeoutCorrection = standardLiveGameState()
        state = beforeTimeoutCorrection.adjustTimeouts(2, 1)
        assertUndoRestores(beforeTimeoutCorrection, state)

        val beforeCardCorrection = standardLiveGameState()
        state = beforeCardCorrection.adjustCardsAndTf(
            teamOneBlues = 1,
            teamOneTechnicalFouls = 2,
            teamTwoBlues = 3,
            teamTwoTechnicalFouls = 4,
            teamOnePlayerCards = listOf(InGamePlayerCardRecord("17", yellows = 1)),
            teamTwoPlayerCards = listOf(InGamePlayerCardRecord("23", reds = 1)),
        )
        assertUndoRestores(beforeCardCorrection, state)

        val beforePullCorrection = standardLiveGameState()
        state = beforePullCorrection.adjustPullInfractions(1, 2, 3, 4)
        assertUndoRestores(beforePullCorrection, state)

        val beforeSetupUpdate = standardLiveGameState()
        state = applySetupToLiveGame(
            existing = beforeSetupUpdate,
            setup = beforeSetupUpdate.toSetupState().copy(
                rules = beforeSetupUpdate.rules.copy(gameTo = 17),
            ),
            now = 350_000L,
        )
        assertUndoRestores(beforeSetupUpdate, state)

        // Undo apply half cap, soft cap, hard cap, force cap now, manual halftime, and manual end game.
        state = standardLiveGameState(
            startTime = LocalTime.of(10, 0),
            rules = GameRules(gameTo = 15, halfCapMinutes = 10, useSoftCap = false, useHardCap = false),
        )
        state = state.recordGoalFromCurrentState(VC, timestampAfterStart(state, 11))
        val beforeApplyHalfCap = state
        state = applyPendingCapAt(state, LocalTime.of(10, 11))
        assertUndoRestores(beforeApplyHalfCap, state)

        state = standardLiveGameState(
            startTime = LocalTime.of(10, 0),
            rules = GameRules(gameTo = 15, useHalfCap = false, softCapMinutes = 10, useHardCap = false),
        )
        state = state.recordGoalFromCurrentState(VC, timestampAfterStart(state, 11))
        val beforeApplySoftCap = state
        state = applyPendingCapAt(state, LocalTime.of(10, 11))
        assertUndoRestores(beforeApplySoftCap, state)

        state = standardLiveGameState(
            startTime = LocalTime.of(10, 0),
            rules = GameRules(gameTo = 15, useHalfCap = false, useSoftCap = false, hardCapMinutes = 10),
        )
        state = state.recordGoalFromCurrentState(VC, timestampAfterStart(state, 11))
        val beforeApplyHardCap = state
        state = applyPendingCapAt(state, LocalTime.of(10, 11))
        assertUndoRestores(beforeApplyHardCap, state)

        val beforeForceCap = standardLiveGameState()
        state = beforeForceCap.makeCapNow(CapType.SOFT, timestampAfterStart(beforeForceCap, 30))
        assertUndoRestores(beforeForceCap, state)

        val beforeManualHalftime = standardLiveGameState(pullingFromEnd = FieldEnd.NEAR)
        state = startHalftimeNowAt(beforeManualHalftime, LocalTime.of(11, 10))
        assertUndoRestores(beforeManualHalftime, state)

        val beforeManualEnd = standardLiveGameState()
        state = endGameNowAt(beforeManualEnd, LocalTime.of(11, 20))
        assertUndoRestores(beforeManualEnd, state)

        // Verify the latest undo entry is exposed when actions are chained.
        state = standardLiveGameState().beginLivePoint()
        val afterStartPoint = state
        state = state.assessTimeout(VC, 800_000L).state
        assertEquals("Undo Timeout by Viscous Coupling", state.undoEntry?.label)
        assertUndoRestores(afterStartPoint, state)

        // Verify undo from game-over summary restores a score-ended game without undoing the score.
        state = standardLiveGameState(
            rules = GameRules(gameTo = 1, useHalfCap = false, useSoftCap = false, useHardCap = false)
        )
        val gameOverByScore = recordGoalFromCurrentStateAt(state, VC, LocalTime.of(11, 25))
        assertEquals(GamePhase.GAME_OVER, gameOverByScore.phase)
        val scoreEndedUndo = gameOverByScore.undoLastAction()
        assertEquals(GamePhase.BETWEEN_POINTS, scoreEndedUndo.phase)
        assertEquals(1, scoreEndedUndo.teamOne.score)
        assertEquals(0, scoreEndedUndo.teamTwo.score)
        assertNull(scoreEndedUndo.endEpoch)
        assertEquals("Viscous Coupling scored.", scoreEndedUndo.lastEvent)
        assertEquals(GamePhase.LIVE_POINT, scoreEndedUndo.undoLastAction().phase)

        // Unavailable game-over commands are idempotent no-ops; the UI normally hides these pathways.
        assertEquals(gameOverByScore, recordGoalAt(gameOverByScore, ANIMAL, LocalTime.of(11, 26)))
        assertEquals(gameOverByScore, endGameNowAt(gameOverByScore, LocalTime.of(11, 26)))

        // If the observer applies End Game again, the summary-relevant state matches the automatic game-over.
        val reappliedGameOver = endGameNowAt(scoreEndedUndo, LocalTime.of(11, 26))
        assertEquals(gameOverByScore.phase, reappliedGameOver.phase)
        assertEquals(gameOverByScore.teamOne.score, reappliedGameOver.teamOne.score)
        assertEquals(gameOverByScore.teamTwo.score, reappliedGameOver.teamTwo.score)
        assertEquals(timestampAt(reappliedGameOver, LocalTime.of(11, 26)), reappliedGameOver.endEpoch)
        assertEquals(gameOverByScore.countdown, reappliedGameOver.countdown)
        assertEquals(gameOverByScore.pendingCapOffer, reappliedGameOver.pendingCapOffer)
        assertEquals(gameOverByScore.lastEvent, reappliedGameOver.lastEvent)
        assertEquals(scoreEndedUndo.copy(redoEntry = null), reappliedGameOver.undoEntry?.previous)
    }
}
