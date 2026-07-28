package rmjarvis.ultiobserver

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/// Tests for undo and redo behavior across representative model actions.
class TestUndo : GameDomainTestFixtures() {
    /**
     * Test ordinary game-flow undo and redo behavior through user-visible actions.
     */
    @Test
    fun gameFlowUndo() {
        val VC = TeamId.TEAM_ONE

        // With no undo entry, the undo action is a no-op.
        var state = standardLiveGameState()
        assertEquals(state, state.undoLastAction())

        // Start a point and verify undo returns to the previous between-points state.
        state = standardLiveGameState()
        val beforeStartPoint = state
        state = state.beginLivePoint()
        assertEquals("Undo Start point", state.undoEntry?.label)
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

        // Record a goal from between points and verify undo returns to the implicit live-point
        // state.
        state = standardLiveGameState()
        val betweenPointsBeforeGoal = state
        state = recordGoalFromCurrentStateAt(state, VC, LocalTime.of(11, 5))
        val implicitLiveState = state.undoLastAction()
        assertEquals(GamePhase.LIVE_POINT, implicitLiveState.phase)
        assertNull(implicitLiveState.countdown)
        assertEquals(0, implicitLiveState.teamOne.score)
        assertEquals(0, implicitLiveState.teamTwo.score)
        assertUndoRestores(betweenPointsBeforeGoal, implicitLiveState)

        // Verify the latest undo entry is exposed when actions are chained.
        state = standardLiveGameState().beginLivePoint()
        val afterStartPoint = state
        state = state.assessTimeout(VC, 800_000L).state
        assertEquals("Undo Timeout by Viscous Coupling", state.undoEntry?.label)
        assertUndoRestores(afterStartPoint, state)
    }

    /**
     * Test undo for rule actions assessed directly during normal play.
     */
    @Test
    fun ruleActionUndo() {
        val VC = TeamId.TEAM_ONE
        val ANIMAL = TeamId.TEAM_TWO

        // Timeout undo restores the live point that existed before the timeout was called.
        var state = standardLiveGameState().beginLivePoint()
        val beforeTimeout = state
        state = state.assessTimeout(ANIMAL, 300_000L).state
        assertUndoRestores(beforeTimeout, state)

        // Card and technical-foul undo restores the misconduct counts and event state.
        state = standardLiveGameState()
        val beforeCard = state
        state = state.assessYellowCard(VC, "17").state
        assertUndoRestores(beforeCard, state)
        state = standardLiveGameState()
        val beforeTf = state
        state = state.assessTechnicalFoul(ANIMAL).state
        assertUndoRestores(beforeTf, state)

        // Pull-violation undo restores the pull sequence and violation counts.
        state = standardLiveGameState()
        val beforeOffsides = state
        state = state.recordOffsides()
        assertUndoRestores(beforeOffsides, state)
        state = standardLiveGameState()
        val beforeFalseStart = state
        state = state.recordFalseStart()
        assertUndoRestores(beforeFalseStart, state)
    }

    /**
     * Test undo for manual corrections from More actions and setup updates.
     */
    @Test
    fun correctionUndo() {
        // Score correction undo restores the previous score.
        var state = standardLiveGameState()
        val beforeScoreCorrection = state
        state = state.adjustScore(2, 3)
        assertUndoRestores(beforeScoreCorrection, state)

        // Timeout correction undo restores the previous timeout counts.
        val beforeTimeoutCorrection = standardLiveGameState()
        state = beforeTimeoutCorrection.adjustTimeouts(2, 1)
        assertUndoRestores(beforeTimeoutCorrection, state)

        // Card and technical-foul correction undo restores team counts and player-card records.
        val beforeCardCorrection = standardLiveGameState()
        state = beforeCardCorrection.adjustCardsAndTf(
            teamOneBlues = 1,
            teamOneTechnicalFouls = 2,
            teamTwoBlues = 3,
            teamTwoTechnicalFouls = 4,
            teamOnePlayers = listOf(playerRecordWithCards("17", yellows = 1)),
            teamTwoPlayers = listOf(playerRecordWithCards("23", reds = 1)),
        )
        assertUndoRestores(beforeCardCorrection, state)

        // Pull-violation correction undo restores all pull-related correction counts.
        val beforePullCorrection = standardLiveGameState()
        state = beforePullCorrection.adjustPullViolations(
            teamOneOffsides = 1,
            teamOneFalseStarts = 2,
            teamOneMajorityPulls = 0,
            teamOneTimeViolations = 3,
            teamTwoOffsides = 4,
            teamTwoFalseStarts = 5,
            teamTwoMajorityPulls = 0,
            teamTwoTimeViolations = 6,
            now = 0L,
        )
        assertUndoRestores(beforePullCorrection, state)

        // Setup update undo restores the live game exactly as it was before the edit.
        val beforeSetupUpdate = standardLiveGameState()
        state = applySetupToLiveGame(
            existing = beforeSetupUpdate,
            setup = beforeSetupUpdate.copy(
                rules = beforeSetupUpdate.rules.copy(gameTo = 17),
            ),
            now = 350_000L,
        )
        assertUndoRestores(beforeSetupUpdate, state)
    }

    /**
     * Test undo for cap, halftime, and manual game-state actions.
     */
    @Test
    fun capAndManualStateUndo() {
        val VC = TeamId.TEAM_ONE

        // Applying half cap is undo-backed.
        var state = standardLiveGameState(
            startTime = LocalTime.of(10, 0),
            rules = GameRules(
                gameTo = 15,
                halfCapMinutes = 10,
                useSoftCap = false,
                useHardCap = false,
            ),
        )
        state = state.recordGoalFromCurrentState(VC, timestampAfterStart(state, 11))
        val beforeApplyHalfCap = state
        state = applyPendingCapAt(state, LocalTime.of(10, 11))
        assertUndoRestores(beforeApplyHalfCap, state)

        // Applying soft cap is undo-backed.
        state = standardLiveGameState(
            startTime = LocalTime.of(10, 0),
            rules = GameRules(
                gameTo = 15,
                useHalfCap = false,
                nominalSoftCapMinutes = 10,
                useHardCap = false,
            ),
        )
        state = state.recordGoalFromCurrentState(VC, timestampAfterStart(state, 11))
        val beforeApplySoftCap = state
        state = applyPendingCapAt(state, LocalTime.of(10, 11))
        assertUndoRestores(beforeApplySoftCap, state)

        // Applying hard cap is undo-backed.
        state = standardLiveGameState(
            startTime = LocalTime.of(10, 0),
            rules = GameRules(
                gameTo = 15,
                useHalfCap = false,
                useSoftCap = false,
                nominalHardCapMinutes = 10,
            ),
        )
        state = state.recordGoalFromCurrentState(VC, timestampAfterStart(state, 11))
        val beforeApplyHardCap = state
        state = applyPendingCapAt(state, LocalTime.of(10, 11))
        assertUndoRestores(beforeApplyHardCap, state)

        // Force-cap-now is undo-backed.
        val beforeForceCap = standardLiveGameState()
        state = beforeForceCap.makeCapNow(CapType.SOFT, timestampAfterStart(beforeForceCap, 30))
        assertUndoRestores(beforeForceCap, state)

        // Manual halftime is undo-backed.
        val beforeManualHalftime = standardLiveGameState(pullingFromEnd = FieldEnd.NEAR)
        state = startHalftimeAt(beforeManualHalftime, LocalTime.of(11, 10))
        assertUndoRestores(beforeManualHalftime, state)

        // Manual end game is undo-backed.
        val beforeManualEnd = standardLiveGameState()
        state = endGameNowAt(beforeManualEnd, LocalTime.of(11, 20))
        assertUndoRestores(beforeManualEnd, state)
    }

    /**
     * Test undo and no-op behavior for score-ended games.
     */
    @Test
    fun gameOverUndo() {
        val VC = TeamId.TEAM_ONE
        val ANIMAL = TeamId.TEAM_TWO

        // Verify undo from game-over summary restores a score-ended game without undoing the score.
        val state = standardLiveGameState(
            rules = GameRules(
                gameTo = 1,
                useHalfCap = false,
                useSoftCap = false,
                useHardCap = false,
            ),
        )
        val gameOverByScore = recordGoalFromCurrentStateAt(state, VC, LocalTime.of(11, 25))
        assertEquals(GamePhase.GAME_OVER, gameOverByScore.phase)
        val scoreEndedUndo = gameOverByScore.undoLastAction()
        assertEquals(GamePhase.BETWEEN_POINTS, scoreEndedUndo.phase)
        assertEquals(1, scoreEndedUndo.teamOne.score)
        assertEquals(0, scoreEndedUndo.teamTwo.score)
        assertNull(scoreEndedUndo.endEpoch)
        assertEquals(GamePhase.LIVE_POINT, scoreEndedUndo.undoLastAction().phase)

        // Unavailable game-over commands are idempotent no-ops; the UI normally hides these
        // pathways.
        assertEquals(gameOverByScore, recordGoalAt(gameOverByScore, ANIMAL, LocalTime.of(11, 26)))
        assertEquals(gameOverByScore, endGameNowAt(gameOverByScore, LocalTime.of(11, 26)))

        // If the observer applies End game again, the summary-relevant state matches the
        // automatic game-over.
        val reappliedGameOver = endGameNowAt(scoreEndedUndo, LocalTime.of(11, 26))
        assertEquals(gameOverByScore.phase, reappliedGameOver.phase)
        assertEquals(gameOverByScore.teamOne.score, reappliedGameOver.teamOne.score)
        assertEquals(gameOverByScore.teamTwo.score, reappliedGameOver.teamTwo.score)
        assertEquals(
            timestampAt(reappliedGameOver, LocalTime.of(11, 26)),
            reappliedGameOver.endEpoch,
        )
        assertEquals(gameOverByScore.countdown, reappliedGameOver.countdown)
        assertEquals(gameOverByScore.pendingCapOffer, reappliedGameOver.pendingCapOffer)
        assertEquals(scoreEndedUndo.copy(redoEntry = null), reappliedGameOver.undoEntry?.previous)
    }
}
