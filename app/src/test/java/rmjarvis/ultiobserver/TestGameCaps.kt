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

class TestGameCaps : GameModelTestFixtures() {
    /**
     * Test cap prompting and cap application as rule-visible state transitions.
     * Caps should become eligible only after point end and should be deterministic from supplied clock values.
     */
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

        /**
         * Build a fresh cap-focused live state with the supplied rules.
         *
         * @param rules The cap rules to install for this scenario.
         */
        fun newCapState(rules: GameRules = capRules): LiveGameState {
            return standardLiveGameState(startTime = startTime, rules = rules)
        }

        /**
         * Score a point for a team at a specific minute after game start.
         *
         * @param state The current live state before the point is scored.
         * @param scoringTeam The team that scores the point.
         * @param minute The minute after game start assigned to the goal.
         */
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
        assertEquals("Apply half cap?", state.capPrompt().formatTitle())
        assertEquals(
            "Half cap was at 10:10 AM. Halftime target would become 2. Apply now?",
            state.capPrompt().formatMessage(),
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
        assertEquals("Halftime", GamePrompt.HalftimeStarted(state).formatTitle())

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
        assertEquals("Apply soft cap?", state.capPrompt().formatTitle())
        assertEquals(
            "Soft cap was at 10:20 AM. Winning score would become 2. Apply now?",
            state.capPrompt().formatMessage(),
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
        assertEquals("Apply hard cap?", state.capPrompt().formatTitle())
        assertEquals(
            "Hard cap was at 10:30 AM. Score is not tied, so the game would end now. Apply now?",
            state.capPrompt().formatMessage(),
        )
        state = applyPendingCapAt(state, LocalTime.of(10, 31))
        assertTrue(state.hardCapApplied)
        assertEquals(LivePhase.GAME_OVER, state.phase)
        assertEquals(timestampAt(state, LocalTime.of(10, 31)), state.endEpoch)
        assertNull(state.countdown)
        assertNull(state.pendingCapOffer)
        assertEquals("Undo Apply Hard Cap", state.undoEntry?.label)
        assertEquals("Game Over", GamePrompt.GameOver(state).formatTitle())

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
            state.capPrompt().formatMessage(),
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
            state.capPrompt().formatMessage(),
        )
        state = applyPendingCapAt(state, LocalTime.of(10, 10))
        assertEquals(LivePhase.GAME_OVER, state.phase)
        assertTrue(state.hardCapApplied)
        assertEquals(timestampAt(state, LocalTime.of(10, 10)), state.endEpoch)
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
            state.capPrompt().formatMessage(),
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
            state.capPrompt().formatMessage(),
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
            state.capPrompt().formatMessage(),
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
            state.capPrompt().formatMessage(),
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
            state.capPrompt().formatMessage(),
        )
        val halftimeCountdown = state.countdown!!
        state = applyPendingCapAt(state, LocalTime.of(10, 12))
        assertEquals(LivePhase.HALFTIME, state.phase)
        assertTrue(state.softCapApplied)
        assertEquals(4, state.winningScore)
        assertNull(state.pendingCapOffer)
        assertEquals("Soft cap applied.", state.lastEvent)

        state = state.applyExpiredCountdownTransitions(halftimeCountdown.targetEpoch)
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
            state.capPrompt().formatMessage(),
        )
        state = applyPendingCapAt(state, LocalTime.of(10, 14))
        assertEquals(LivePhase.GAME_OVER, state.phase)
        assertTrue(state.hardCapApplied)
        assertFalse(state.softCapApplied)
        assertEquals(timestampAt(state, LocalTime.of(10, 14)), state.endEpoch)
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
            state.capPrompt().formatMessage(),
        )
        val tiedHardCapHalftimeCountdown = state.countdown
        state = applyPendingCapAt(state, LocalTime.of(10, 12))
        assertEquals(LivePhase.HALFTIME, state.phase)
        assertTrue(state.hardCapApplied)
        assertEquals(3, state.winningScore)
        assertEquals(tiedHardCapHalftimeCountdown, state.countdown)
        assertNull(state.endEpoch)
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
        state = state.applyExpiredCountdownTransitions(state.countdown!!.targetEpoch + 30_000L)
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
}
