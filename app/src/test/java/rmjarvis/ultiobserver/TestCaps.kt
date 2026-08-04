package rmjarvis.ultiobserver

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for cap relevance, prompting, application, and halftime interactions.
 */
class TestCaps : GameDomainTestFixtures() {
    private val vc = TeamId.TEAM_ONE
    private val animal = TeamId.TEAM_TWO
    private val startTime = LocalTime.of(10, 0)
    private val capRules = GameRules(
        gameTo = 15,
        halfCapMinutes = 10,
        nominalSoftCapMinutes = 20,
        nominalHardCapMinutes = 30,
    )

    /**
     * Verify next-cap status reports the next relevant enabled cap from an explicit timestamp.
     */
    @Test
    fun nextCapStatus() {
        // Enabled caps are reported in chronological order, skipping caps that already applied.
        var state = standardLiveGameState(
            startTime = startTime,
            rules = GameRules(
                gameTo = 15,
                halfCapMinutes = 45,
                nominalSoftCapMinutes = 90,
                nominalHardCapMinutes = 100,
            ),
        )
        val halfCapStatus = state.computeNextCapStatus(timestampAfterStart(state, 15))!!
        assertEquals("Half cap", halfCapStatus.label)
        assertEquals(Duration.ofMinutes(30), halfCapStatus.remaining)
        assertEquals(CapStatus("Half cap", Duration.ofMinutes(30)), halfCapStatus)
        assertEquals(
            CapStatus("Soft cap", Duration.ofMinutes(30)),
            state.copy(halfCapApplied = true)
                .computeNextCapStatus(timestampAfterStart(state, 60)),
        )
        assertEquals(
            CapStatus("Hard cap", Duration.ofMinutes(5)),
            state.copy(halfCapApplied = true, softCapApplied = true)
                .computeNextCapStatus(timestampAfterStart(state, 95)),
        )
        assertNull(state.computeNextCapStatus(timestampAfterStart(state, 200)))
        assertEquals(
            CapStatus("Soft cap", Duration.ofMinutes(30)),
            state.copy(halftimeTaken = true).computeNextCapStatus(timestampAfterStart(state, 60)),
        )
        assertNull(
            state.copy(
                halfCapApplied = true,
                softCapApplied = true,
                hardCapApplied = true,
            ).computeNextCapStatus(timestampAfterStart(state, 95))
        )

        // Soft cap is skipped at 13-13, while hard cap is also skipped once the score reaches
        // 14-13 or 14-14.
        val afterHalftime = state.copy(halftimeTaken = true)
        val softIrrelevant = afterHalftime.copy(
            teamOne = afterHalftime.teamOne.copy(score = 13),
            teamTwo = afterHalftime.teamTwo.copy(score = 13),
        )
        assertEquals(
            CapStatus("Hard cap", Duration.ofMinutes(85)),
            softIrrelevant.computeNextCapStatus(timestampAfterStart(softIrrelevant, 15)),
        )
        val hardIrrelevant = softIrrelevant.copy(
            teamOne = softIrrelevant.teamOne.copy(score = 14),
            teamTwo = softIrrelevant.teamTwo.copy(score = 13),
        )
        assertNull(hardIrrelevant.computeNextCapStatus(timestampAfterStart(hardIrrelevant, 15)))

        // Cap countdowns can wrap across midnight when a late-night game crosses dates.
        state = standardLiveGameState(
            startDate = LocalDate.of(2026, 1, 1),
            startTime = LocalTime.of(23, 30),
            rules = GameRules(
                gameTo = 15,
                halfCapMinutes = 45,
                useSoftCap = false,
                useHardCap = false,
            ),
        )
        assertEquals(
            CapStatus("Half cap", Duration.ofMinutes(30)),
            state.computeNextCapStatus(timestampAfterStart(state, 15)),
        )
        state = state.copy(halfCapApplied = true, softCapApplied = true, hardCapApplied = true)
        assertNull(state.computeNextCapStatus(timestampAfterStart(state, 15)))

        // Disabled caps are completely omitted from next-cap status.
        state = standardLiveGameState(
            startTime = startTime,
            rules = GameRules(
                gameTo = 15,
                useHalfCap = false,
                useSoftCap = false,
                useHardCap = false,
            ),
        )
        assertNull(state.computeNextCapStatus(timestampAfterStart(state, 15)))
    }

    /**
     * Verify live-point cap messages report passed relevant caps and future irrelevant caps.
     */
    @Test
    fun capStatusMessages() {
        // Soft cap produces a live-point message after its scheduled time, until it is applied.
        val softOnly = newCapState(
            capRules.copy(
                useHalfCap = false,
                useHardCap = false,
            )
        ).beginLivePoint()
        assertNull(softOnly.capStatusMessage(timestampAfterStart(softOnly, 19)))
        assertEquals(
            "Soft cap passed. It will apply at the end of this point.",
            softOnly.capStatusMessage(timestampAfterStart(softOnly, 21)),
        )
        assertNull(
            softOnly.copy(softCapApplied = true)
                .capStatusMessage(timestampAfterStart(softOnly, 21))
        )

        // Hard cap uses the same priority as the point-end prompt when multiple caps have passed.
        val hardAndSoft = newCapState(
            capRules.copy(
                useHalfCap = false,
            )
        ).beginLivePoint()
        assertEquals(
            "Hard cap passed. It will apply at the end of this point.",
            hardAndSoft.capStatusMessage(timestampAfterStart(hardAndSoft, 31)),
        )

        // A relevant future hard cap does not produce a status message before its time.
        assertNull(hardAndSoft.capStatusMessage(timestampAfterStart(hardAndSoft, 19)))

        // Half cap messages only appear while half cap can still change halftime.
        val halfOnly = newCapState(
            capRules.copy(
                useSoftCap = false,
                useHardCap = false,
            )
        ).beginLivePoint()

        // A relevant future half cap does not produce a status message before its time.
        assertNull(halfOnly.capStatusMessage(timestampAfterStart(halfOnly, 9)))
        assertEquals(
            "Half cap passed. It will apply at the end of this point.",
            halfOnly.capStatusMessage(timestampAfterStart(halfOnly, 11)),
        )
        val irrelevantHalf = halfOnly.copy(
            teamOne = halfOnly.teamOne.copy(score = 6),
            teamTwo = halfOnly.teamTwo.copy(score = 6),
        )
        assertEquals(
            "Half cap is no longer relevant.",
            irrelevantHalf.capStatusMessage(timestampAfterStart(irrelevantHalf, 9)),
        )
        assertNull(
            irrelevantHalf.capStatusMessage(timestampAfterStart(irrelevantHalf, 11))
        )

        // A half cap already handled by application or halftime is not reported as irrelevant.
        assertNull(
            halfOnly.copy(halfCapApplied = true)
                .capStatusMessage(timestampAfterStart(halfOnly, 9))
        )
        assertNull(
            halfOnly.copy(halftimeTaken = true)
                .capStatusMessage(timestampAfterStart(halfOnly, 9))
        )

        // If soft and hard cap are both irrelevant before their scheduled times, report the
        // later hard-cap consequence rather than repeating the earlier soft-cap consequence.
        val irrelevantSoftAndHard = hardAndSoft.copy(
            teamOne = hardAndSoft.teamOne.copy(score = 14),
            teamTwo = hardAndSoft.teamTwo.copy(score = 13),
        )
        assertEquals(
            "Hard cap is no longer relevant.",
            irrelevantSoftAndHard.capStatusMessage(
                timestampAfterStart(irrelevantSoftAndHard, 19)
            ),
        )
        assertNull(
            irrelevantSoftAndHard.capStatusMessage(
                timestampAfterStart(irrelevantSoftAndHard, 31)
            )
        )

        // Cap status messages do not appear outside a live point.
        assertNull(
            halfOnly.copy(phase = GamePhase.BETWEEN_POINTS)
                .capStatusMessage(timestampAfterStart(halfOnly, 11))
        )
        assertNull(
            irrelevantSoftAndHard.copy(phase = GamePhase.BETWEEN_POINTS)
                .capStatusMessage(timestampAfterStart(irrelevantSoftAndHard, 19))
        )
    }

    /**
     * Verify future soft and hard caps are skipped only when the current score makes them
     * irrelevant, without suppressing a cap that already became due during the completed point.
     */
    @Test
    fun irrelevantSoftAndHardCaps() {
        val afterHalftime = newCapState(
            capRules.copy(useHalfCap = false)
        ).copy(halftimeTaken = true)

        // At 13-13, soft cap can no longer lower the target after another point, so the next
        // relevant cap is hard cap.
        val tiedAtThirteen = afterHalftime.copy(
            teamOne = afterHalftime.teamOne.copy(score = 13),
            teamTwo = afterHalftime.teamTwo.copy(score = 13),
        )
        assertFalse(tiedAtThirteen.softCapRelevant())
        assertTrue(tiedAtThirteen.hardCapRelevant())

        // At 14-13 and 14-14, neither cap can change what happens after the next point.
        val fourteenThirteen = tiedAtThirteen.copy(
            teamOne = tiedAtThirteen.teamOne.copy(score = 14),
        )
        assertFalse(fourteenThirteen.softCapRelevant())
        assertFalse(fourteenThirteen.hardCapRelevant())
        assertFalse(
            fourteenThirteen.copy(
                teamTwo = fourteenThirteen.teamTwo.copy(score = 14),
            ).hardCapRelevant()
        )
        assertTrue(
            fourteenThirteen.copy(
                teamTwo = fourteenThirteen.teamTwo.copy(score = 12),
            ).hardCapRelevant()
        )

        // If soft cap already lowered the winning score, hard-cap relevance uses that target.
        val softCappedState = fourteenThirteen.copy(
            teamOne = fourteenThirteen.teamOne.copy(score = 9),
            teamTwo = fourteenThirteen.teamTwo.copy(score = 8),
            softCapApplied = true,
            winningScore = 10,
        )
        assertFalse(softCappedState.hardCapRelevant())
        assertFalse(
            softCappedState.copy(
                teamTwo = softCappedState.teamTwo.copy(score = 9),
            ).hardCapRelevant()
        )
        assertTrue(
            softCappedState.copy(
                teamTwo = softCappedState.teamTwo.copy(score = 7),
            ).hardCapRelevant()
        )

        // Hard cap is enabled and due when the next goal produces 9-9. Because soft cap already
        // lowered the winning score to 10, that is universe point and needs no hard-cap offer.
        val afterHardCapTime = timestampAfterStart(softCappedState, 31)
        assertTrue(afterHardCapTime >= softCappedState.capEpoch(CapType.HARD))
        val softCappedUniversePoint = softCappedState.beginLivePoint()
            .recordGoalFromCurrentState(
                scoringTeam = animal,
                now = afterHardCapTime,
            )
        assertEquals(9, softCappedUniversePoint.teamOne.score)
        assertEquals(9, softCappedUniversePoint.teamTwo.score)
        assertNull(softCappedUniversePoint.pendingCapOffer)

        // A soft cap that already passed can still matter when the completed point produces
        // 13-13, even though a future soft cap would not matter from that score onward.
        var pointState = afterHalftime.copy(
            teamOne = afterHalftime.teamOne.copy(score = 12),
            teamTwo = afterHalftime.teamTwo.copy(score = 13),
        ).beginLivePoint()
        pointState = pointState.recordGoalFromCurrentState(
            scoringTeam = vc,
            now = timestampAfterStart(pointState, 21),
        )
        assertEquals(13, pointState.teamOne.score)
        assertEquals(13, pointState.teamTwo.score)
        assertEquals(CapType.SOFT, pointState.pendingCapOffer)

        // The same distinction lets an already-passed hard cap apply to a completed 14-13 score,
        // while a completed universe-point score needs no hard-cap offer.
        pointState = afterHalftime.copy(
            teamOne = afterHalftime.teamOne.copy(score = 14),
            teamTwo = afterHalftime.teamTwo.copy(score = 12),
        ).beginLivePoint()
        pointState = pointState.recordGoalFromCurrentState(
            scoringTeam = animal,
            now = timestampAfterStart(pointState, 31),
        )
        assertEquals(CapType.HARD, pointState.pendingCapOffer)

        pointState = afterHalftime.copy(
            teamOne = afterHalftime.teamOne.copy(score = 14),
            teamTwo = afterHalftime.teamTwo.copy(score = 13),
        ).beginLivePoint()
        pointState = pointState.recordGoalFromCurrentState(
            scoringTeam = animal,
            now = timestampAfterStart(pointState, 31),
        )
        assertEquals(14, pointState.teamOne.score)
        assertEquals(14, pointState.teamTwo.score)
        assertNull(pointState.pendingCapOffer)
    }

    /**
     * Verify half-cap prompting, deferral, disabled caps, and after-midnight eligibility.
     */
    @Test
    fun halfCapPrompts() {
        // Start with an ordinary first point before any cap time and verify no cap is offered.
        var state = newCapState()
        assertEquals(
            CapStatus("Half cap", Duration.ofMinutes(5)),
            state.computeNextCapStatus(timestampAfterStart(state, 5)),
        )
        state = scoreAt(state, vc, 5)
        assertEquals(1, state.teamOne.score)
        assertEquals(0, state.teamTwo.score)
        assertNull(state.pendingCapOffer)
        assertFalse(state.halfCapApplied)

        // Score after half-cap time and verify the pending prompt is explicit.
        state = scoreAt(state, animal, 11)
        assertEquals(1, state.teamOne.score)
        assertEquals(1, state.teamTwo.score)
        assertEquals(CapType.HALF, state.pendingCapOffer)
        assertEquals("Half cap", state.capPrompt().formatTitle())
        val capPrompt: GamePrompt = state.capPrompt()
        assertEquals("Half cap", capPrompt.formatTitle())
        assertEquals(
            "Half cap was at 10:10 AM, so it applies now. The new halftime target is 2.",
            state.capPrompt().formatMessage().plainText,
        )
        assertEquals(
            "Half cap was at 10:10 AM, so it applies now. The new halftime target is 2.",
            capPrompt.formatMessage().plainText,
        )

        // Applying half cap sets the target, clears the offer, and keeps an undo path.
        val beforeHalfCap = state
        state = applyPendingCapAt(state, LocalTime.of(10, 11))
        assertTrue(state.halfCapApplied)
        assertEquals(2, state.halftimeTargetScore)
        assertNull(state.pendingCapOffer)
        assertEquals("Undo Apply half cap", state.undoEntry?.label)
        assertEquals(beforeHalfCap, state.undoEntry?.previous)

        // The half-cap target becomes the live halftime target, so the next point starts halftime.
        state = scoreAt(state, vc, 12)
        assertEquals(2, state.teamOne.score)
        assertEquals(1, state.teamTwo.score)
        assertEquals(GamePhase.HALFTIME, state.phase)
        assertTrue(state.halftimeTaken)
        assertEquals(2, state.halftimeTargetScore)
        assertNull(state.pendingCapOffer)
        val halftimePrompt: GamePrompt = GamePrompt.HalftimeStarted(state)
        assertEquals("Halftime", halftimePrompt.formatTitle())
        assertEquals("Announce halftime.", halftimePrompt.formatMessage().plainText)
        assertEquals(state, (halftimePrompt as GamePrompt.HalftimeStarted).state)

        // If the observer defers a pending half cap, the offer clears but the cap is not applied.
        state = newCapState()
        state = scoreAt(state, vc, 11)
        assertEquals(CapType.HALF, state.pendingCapOffer)
        state = state.deferPendingCap()
        assertNull(state.pendingCapOffer)
        assertFalse(state.halfCapApplied)
        assertNull(state.halftimeTargetScore)

        // Disabled caps do not show up in the countdown helper and do not create pending offers.
        state = newCapState(
            capRules.copy(
                useHalfCap = false,
                useSoftCap = false,
                useHardCap = false,
            )
        )
        assertNull(state.computeNextCapStatus(timestampAfterStart(state, 5)))
        state = scoreAt(state, vc, 35)
        assertNull(state.pendingCapOffer)
        assertFalse(state.halfCapApplied)
        assertFalse(state.softCapApplied)
        assertFalse(state.hardCapApplied)

        // A cap after midnight should not be eligible before midnight because its clock is earlier.
        val lateStartDate = LocalDate.of(2026, 1, 1)
        state = standardLiveGameState(
            startDate = lateStartDate,
            startTime = LocalTime.of(23, 30),
            rules = GameRules(
                gameTo = 15,
                useHalfCap = false,
                useSoftCap = false,
                nominalHardCapMinutes = 100,
            ),
        )
        state = state.recordGoalFromCurrentState(
            vc,
            now = epochTimestamp(lateStartDate, LocalTime.of(23, 50), testTimeZone),
        )
        assertNull(state.pendingCapOffer)
        state = state.recordGoalFromCurrentState(
            animal,
            now = epochTimestamp(lateStartDate.plusDays(1), LocalTime.of(1, 11), testTimeZone),
        )
        assertEquals(CapType.HARD, state.pendingCapOffer)
    }

    /**
     * Verify soft and hard cap application outside halftime.
     */
    @Test
    fun softAndHardCaps() {
        // Soft cap sets the winning score to the current higher score plus one.
        var state = newCapState(capRules.copy(useHalfCap = false))
        state = scoreAt(state, vc, 5)
        state = scoreAt(state, animal, 21)
        assertEquals(1, state.teamOne.score)
        assertEquals(1, state.teamTwo.score)
        assertEquals(CapType.SOFT, state.pendingCapOffer)
        assertEquals("Soft cap", state.capPrompt().formatTitle())
        assertEquals(
            "Soft cap was at 10:20 AM, so it applies now. The new winning score is 2.",
            state.capPrompt().formatMessage().plainText,
        )
        state = applyPendingCapAt(state, LocalTime.of(10, 21))
        assertTrue(state.softCapApplied)
        assertEquals(2, state.winningScore)
        assertNull(state.pendingCapOffer)
        assertEquals("Undo Apply soft cap", state.undoEntry?.label)
        state = scoreAt(state, vc, 22)
        assertEquals(GamePhase.GAME_OVER, state.phase)
        assertEquals(2, state.teamOne.score)
        assertEquals(1, state.teamTwo.score)
        assertEquals(2, state.winningScore)

        // Once soft cap has applied, a later non-winning point does not offer it again.
        state = newCapState(
            capRules.copy(
                useHalfCap = false,
                useHardCap = false,
            )
        ).let { freshState ->
            freshState.copy(
                teamOne = freshState.teamOne.copy(score = 9),
                halftimeTaken = true,
            )
        }
        state = scoreAt(state, animal, 21)
        assertEquals(CapType.SOFT, state.pendingCapOffer)
        state = applyPendingCapAt(state, LocalTime.of(10, 21))
        assertEquals(10, state.winningScore)
        state = scoreAt(state, animal, 22)
        assertEquals(9, state.teamOne.score)
        assertEquals(2, state.teamTwo.score)
        assertNull(state.pendingCapOffer)

        // Hard cap while the score is not tied ends the game immediately when applied.
        state = newCapState(capRules.copy(useHalfCap = false, useSoftCap = false))
        state = scoreAt(state, vc, 5)
        state = scoreAt(state, animal, 6)
        state = scoreAt(state, vc, 31)
        assertEquals(2, state.teamOne.score)
        assertEquals(1, state.teamTwo.score)
        assertEquals(CapType.HARD, state.pendingCapOffer)
        assertEquals("Hard cap", state.capPrompt().formatTitle())
        assertEquals(
            "Hard cap was at 10:30 AM, so it applies now. " +
                "Score is not tied, so the game is over.",
            state.capPrompt().formatMessage().plainText,
        )
        state = applyPendingCapAt(state, LocalTime.of(10, 31))
        assertTrue(state.hardCapApplied)
        assertEquals(GamePhase.GAME_OVER, state.phase)
        assertEquals(timestampAt(state, LocalTime.of(10, 31)), state.endEpoch)
        assertNull(state.countdown)
        assertNull(state.pendingCapOffer)
        assertEquals("Undo Apply hard cap", state.undoEntry?.label)
        assertEquals("Game over", GamePrompt.GameOver(state).formatTitle())

        // Hard cap while tied triggers universe point, rather than ending.
        state = newCapState(capRules.copy(useHalfCap = false))
        state = scoreAt(state, vc, 5)
        state = scoreAt(state, vc, 6)
        state = scoreAt(state, animal, 7)
        state = scoreAt(state, animal, 31)
        assertEquals(2, state.teamOne.score)
        assertEquals(2, state.teamTwo.score)
        assertEquals(CapType.HARD, state.pendingCapOffer)
        assertEquals(
            "Hard cap was at 10:30 AM, so it applies now. " +
                "Score is tied, so play one more point.",
            state.capPrompt().formatMessage().plainText,
        )
        state = applyPendingCapAt(state, LocalTime.of(10, 31))
        assertTrue(state.hardCapApplied)
        assertEquals(GamePhase.BETWEEN_POINTS, state.phase)
        assertEquals(3, state.winningScore)
        assertNull(state.pendingCapOffer)

        // The applied hard cap is now the final target: the earlier soft cap is no longer relevant,
        // and the deciding point does not show another cap status message.
        assertFalse(state.softCapRelevant())
        val decidingPoint = state.beginLivePoint()
        assertNull(decidingPoint.capStatusMessage(timestampAfterStart(decidingPoint, 31)))
    }

    /**
     * Soft and hard caps have quite a few tricky edge cases when they occur just before, during,
     * or just after halftime, so check these carefully.
     */
    @Test
    fun capsAtHalftime() {
        // If soft cap and halftime are both due at point end, halftime starts with a cap offer.
        var state = newCapState(
            capRules.copy(
                gameTo = 5,
                halfCapMinutes = 10,
                nominalSoftCapMinutes = 10,
                useHardCap = false,
            )
        )
        state = scoreAt(state, vc, 1)
        state = scoreAt(state, vc, 2)
        val beforeSoftCapHalftimeGoal = state.beginLivePoint()
        val softCapHalftimeGoalTime = timestampAfterStart(beforeSoftCapHalftimeGoal, 10)
        state = beforeSoftCapHalftimeGoal.recordGoal(vc, softCapHalftimeGoalTime)
        assertEquals(3, state.teamOne.score)
        assertEquals(0, state.teamTwo.score)
        assertEquals(GamePhase.HALFTIME, state.phase)
        assertTrue(state.halftimeTaken)
        assertEquals(CapType.SOFT, state.pendingCapOffer)
        assertEquals(CountdownKind.HALFTIME, state.countdown?.kind)
        assertEquals(420, state.countdown?.durationSeconds)
        assertEquals(softCapHalftimeGoalTime + 420_000L, state.countdown?.targetEpoch)
        assertEquals(0, state.teamOne.firstHalfTimeoutsUsed)
        assertEquals(0, state.teamOne.timeoutsUsedThisHalf)
        assertEquals(0, state.teamTwo.firstHalfTimeoutsUsed)
        assertEquals(0, state.teamTwo.timeoutsUsedThisHalf)
        assertEquals(animal, state.pullingTeam)
        assertEquals(FieldEnd.FAR, state.pullingFromEnd)
        assertEquals(animal, state.teamDefendingEnd(FieldEnd.FAR))
        assertEquals("Undo Goal by Viscous Coupling", state.undoEntry?.label)
        assertEquals(beforeSoftCapHalftimeGoal, state.undoEntry?.previous)
        state = applyPendingCapAt(state, LocalTime.of(10, 10))
        assertEquals(GamePhase.HALFTIME, state.phase)
        assertTrue(state.softCapApplied)
        assertEquals(4, state.winningScore)
        assertNull(state.pendingCapOffer)

        // If hard cap and halftime are both due at point end, applying the prompt can end the game.
        state = newCapState(
            capRules.copy(
                gameTo = 5,
                useHalfCap = false,
                useSoftCap = false,
                nominalHardCapMinutes = 10,
            )
        )
        state = scoreAt(state, vc, 1)
        state = scoreAt(state, vc, 2)
        state = scoreAt(state, vc, 10)
        assertEquals(3, state.teamOne.score)
        assertEquals(0, state.teamTwo.score)
        assertEquals(GamePhase.HALFTIME, state.phase)
        assertTrue(state.halftimeTaken)
        assertEquals(CapType.HARD, state.pendingCapOffer)
        assertEquals(
            "Hard cap was at 10:10 AM, so it applies now. " +
                "Score is not tied, so the game is over.",
            state.capPrompt().formatMessage().plainText,
        )
        state = applyPendingCapAt(state, LocalTime.of(10, 10))
        assertEquals(GamePhase.GAME_OVER, state.phase)
        assertTrue(state.hardCapApplied)
        assertEquals(timestampAt(state, LocalTime.of(10, 10)), state.endEpoch)
        assertNull(state.pendingCapOffer)

        // Manual halftime catches a cap that became due after the last point.
        state = newCapState(
            capRules.copy(
                gameTo = 7,
                useHalfCap = false,
                nominalSoftCapMinutes = 9,
                useHardCap = false,
            )
        )
        state = scoreAt(state, vc, 8)
        assertEquals(GamePhase.BETWEEN_POINTS, state.phase)
        assertNull(state.pendingCapOffer)
        state = state.startHalftimeNow(timestampAfterStart(state, 10))
        assertEquals(GamePhase.HALFTIME, state.phase)
        assertEquals(CapType.SOFT, state.pendingCapOffer)
        assertEquals(
            "Soft cap was at 10:09 AM, so it applies now. The new winning score is 2.",
            state.capPrompt().formatMessage().plainText,
        )
        state = applyPendingCapAt(state, LocalTime.of(10, 10))
        assertTrue(state.softCapApplied)
        assertEquals(2, state.winningScore)
        assertNull(state.pendingCapOffer)

        // Manual halftime catches a hard cap that became due before halftime was started.
        state = newCapState(
            capRules.copy(
                gameTo = 7,
                useHalfCap = false,
                useSoftCap = false,
                nominalHardCapMinutes = 9,
            )
        )
        state = scoreAt(state, vc, 8)
        assertEquals(GamePhase.BETWEEN_POINTS, state.phase)
        assertNull(state.pendingCapOffer)
        state = state.startHalftimeNow(timestampAfterStart(state, 10))
        assertEquals(GamePhase.HALFTIME, state.phase)
        assertEquals(CapType.HARD, state.pendingCapOffer)
        assertEquals(
            "Hard cap was at 10:09 AM, so it applies now. " +
                "Score is not tied, so the game is over.",
            state.capPrompt().formatMessage().plainText,
        )

        // With hard cap disabled, manual halftime can catch soft cap during halftime proper.
        state = newCapState(
            capRules.copy(
                gameTo = 7,
                useHalfCap = false,
                nominalSoftCapMinutes = 12,
                useHardCap = false,
            )
        )
        state = scoreAt(state, vc, 8)
        state = state.startHalftimeNow(timestampAfterStart(state, 10))
        assertEquals(GamePhase.HALFTIME, state.phase)
        assertEquals(CapType.SOFT, state.pendingCapOffer)
        assertEquals(
            "Soft cap is scheduled for 10:12 AM, which is during halftime, " +
                "so we can apply it now. The new winning score is 2.",
            state.capPrompt().formatMessage().plainText,
        )

        // Caps scheduled after halftime ends should wait for the next point.
        state = newCapState(
            capRules.copy(
                gameTo = 7,
                halftimeMinutes = 7,
                useHalfCap = false,
                useSoftCap = false,
                nominalHardCapMinutes = 20,
            )
        )
        state = scoreAt(state, vc, 8)
        state = state.startHalftimeNow(timestampAfterStart(state, 10))
        assertEquals(GamePhase.HALFTIME, state.phase)
        assertNull(state.pendingCapOffer)
        state = newCapState(
            capRules.copy(
                gameTo = 7,
                halftimeMinutes = 7,
                useHalfCap = false,
                nominalSoftCapMinutes = 20,
                useHardCap = false,
            )
        )
        state = scoreAt(state, vc, 8)
        state = state.startHalftimeNow(timestampAfterStart(state, 10))
        assertEquals(GamePhase.HALFTIME, state.phase)
        assertNull(state.pendingCapOffer)

        // A hard cap scheduled during halftime takes precedence over an already-due soft cap.
        state = newCapState(
            capRules.copy(
                gameTo = 7,
                useHalfCap = false,
                nominalSoftCapMinutes = 9,
                nominalHardCapMinutes = 12,
            )
        )
        state = scoreAt(state, vc, 8)
        assertNull(state.pendingCapOffer)
        state = state.startHalftimeNow(timestampAfterStart(state, 10))
        assertEquals(GamePhase.HALFTIME, state.phase)
        assertEquals(CapType.HARD, state.pendingCapOffer)
        assertEquals(
            "Hard cap is scheduled for 10:12 AM, which is during halftime, " +
                "so we can apply it now. Score is not tied, so the game is over.",
            state.capPrompt().formatMessage().plainText,
        )

        // Soft cap during halftime proper is applied immediately, before the next point starts.
        state = newCapState(
            capRules.copy(
                gameTo = 5,
                halftimeMinutes = 7,
                useHalfCap = false,
                nominalSoftCapMinutes = 12,
                useHardCap = false,
            )
        )
        state = scoreAt(state, vc, 1)
        state = scoreAt(state, vc, 2)
        state = scoreAt(state, vc, 10)
        assertEquals(GamePhase.HALFTIME, state.phase)
        assertEquals(CapType.SOFT, state.pendingCapOffer)
        assertEquals(
            "Soft cap is scheduled for 10:12 AM, which is during halftime, " +
                "so we can apply it now. The new winning score is 4.",
            state.capPrompt().formatMessage().plainText,
        )
        val halftimeCountdown = state.countdown!!
        state = applyPendingCapAt(state, LocalTime.of(10, 12))
        assertEquals(GamePhase.HALFTIME, state.phase)
        assertTrue(state.softCapApplied)
        assertEquals(4, state.winningScore)
        assertNull(state.pendingCapOffer)
        state = state.applyExpiredCountdownTransitions(
            halftimeCountdown.targetEpoch,
            showDefenseCountdowns = false,
        )
        assertEquals(GamePhase.BETWEEN_POINTS, state.phase)
        assertTrue(state.softCapApplied)
        assertEquals(4, state.winningScore)

        // If soft and hard cap both fall inside halftime, hard cap takes precedence.
        state = newCapState(
            capRules.copy(
                gameTo = 5,
                halftimeMinutes = 7,
                useHalfCap = false,
                nominalSoftCapMinutes = 15,
                nominalHardCapMinutes = 20,
            )
        )
        state = scoreAt(state, vc, 1)
        state = scoreAt(state, vc, 2)
        state = scoreAt(state, vc, 14)
        assertEquals(GamePhase.HALFTIME, state.phase)
        assertEquals(CapType.HARD, state.pendingCapOffer)
        assertEquals(
            "Hard cap is scheduled for 10:20 AM, which is during halftime, " +
                "so we can apply it now. Score is not tied, so the game is over.",
            state.capPrompt().formatMessage().plainText,
        )
        state = applyPendingCapAt(state, LocalTime.of(10, 14))
        assertEquals(GamePhase.GAME_OVER, state.phase)
        assertTrue(state.hardCapApplied)
        assertFalse(state.softCapApplied)
        assertEquals(timestampAt(state, LocalTime.of(10, 14)), state.endEpoch)
        assertNull(state.countdown)
        assertNull(state.pendingCapOffer)

        // A tied hard cap during halftime means one more point after halftime.
        state = newCapState(
            capRules.copy(
                gameTo = 7,
                halftimeMinutes = 7,
                useHalfCap = false,
                useSoftCap = false,
                nominalHardCapMinutes = 12,
            )
        )
        state = scoreAt(state, vc, 1)
        state = scoreAt(state, animal, 2)
        state = scoreAt(state, vc, 3)
        state = scoreAt(state, animal, 4)
        assertEquals(2, state.teamOne.score)
        assertEquals(2, state.teamTwo.score)
        assertNull(state.pendingCapOffer)
        state = state.startHalftimeNow(timestampAfterStart(state, 10))
        assertEquals(GamePhase.HALFTIME, state.phase)
        assertEquals(CapType.HARD, state.pendingCapOffer)
        assertEquals(
            "Hard cap is scheduled for 10:12 AM, which is during halftime, " +
                "so we can apply it now. Score is tied, so play one more point.",
            state.capPrompt().formatMessage().plainText,
        )
        val tiedHardCapHalftimeCountdown = state.countdown
        state = applyPendingCapAt(state, LocalTime.of(10, 12))
        assertEquals(GamePhase.HALFTIME, state.phase)
        assertTrue(state.hardCapApplied)
        assertEquals(3, state.winningScore)
        assertEquals(tiedHardCapHalftimeCountdown, state.countdown)
        assertNull(state.endEpoch)
        assertNull(state.pendingCapOffer)

        // A cap after halftime expires but before the pull waits until the next point completes.
        state = newCapState(
            capRules.copy(
                gameTo = 5,
                halftimeMinutes = 7,
                useHalfCap = false,
                nominalSoftCapMinutes = 18,
                useHardCap = false,
            )
        )
        state = scoreAt(state, vc, 1)
        state = scoreAt(state, vc, 2)
        state = scoreAt(state, vc, 10)
        assertEquals(GamePhase.HALFTIME, state.phase)
        state = state.applyExpiredCountdownTransitions(
            state.countdown!!.targetEpoch + 30_000L,
            showDefenseCountdowns = false,
        )
        assertEquals(GamePhase.BETWEEN_POINTS, state.phase)
        assertFalse(state.softCapApplied)
        assertNull(state.pendingCapOffer)
        state = scoreAt(state, animal, 19)
        assertEquals(CapType.SOFT, state.pendingCapOffer)
        assertFalse(state.softCapApplied)
    }

    /**
     * Verify force-cap-now actions enable and immediately apply the selected cap.
     */
    @Test
    fun capNowActions() {
        // With caps disabled, each cap-now action enables and applies one cap without changing
        // the scheduled start time.
        val state = newCapState(
            capRules.copy(
                useHalfCap = false,
                useSoftCap = false,
                useHardCap = false,
            )
        )
        val halfNow = state.makeCapNow(CapType.HALF, timestampAfterStart(state, 42))
        assertTrue(halfNow.rules.useHalfCap)
        assertTrue(halfNow.halfCapApplied)
        assertEquals(1, halfNow.halftimeTargetScore)
        assertEquals(state.startDate, halfNow.startDate)
        assertEquals(state.startTime, halfNow.startTime)
        assertEquals(state.startEpoch, halfNow.startEpoch)
        assertEquals("Undo Apply half cap now", halfNow.undoEntry?.label)
        val softNow = state.makeCapNow(CapType.SOFT, timestampAfterStart(state, 42))
        assertTrue(softNow.rules.useSoftCap)
        assertTrue(softNow.softCapApplied)
        assertEquals(1, softNow.winningScore)
        assertEquals(state.startDate, softNow.startDate)
        assertEquals(state.startTime, softNow.startTime)
        assertEquals(state.startEpoch, softNow.startEpoch)
        assertEquals("Undo Apply soft cap now", softNow.undoEntry?.label)
        val hardNow = state.makeCapNow(CapType.HARD, timestampAfterStart(state, 42))
        assertTrue(hardNow.rules.useHardCap)
        assertTrue(hardNow.hardCapApplied)
        assertEquals(1, hardNow.winningScore)
        assertEquals(state.startDate, hardNow.startDate)
        assertEquals(state.startTime, hardNow.startTime)
        assertEquals(state.startEpoch, hardNow.startEpoch)
        assertEquals("Undo Apply hard cap now", hardNow.undoEntry?.label)
    }

    /**
     * Verify half cap does not prompt when it is no longer relevant based on the current
     * score. If the half cap application cannot move the halftime target lower than the
     * normal halftime target, then it is irrelevant and should not prompt for application.
     */
    @Test
    fun irrelevantHalfCap() {
        // Once the next half-cap target would equal normal halftime, half cap should not prompt.
        var state = newCapState(
            capRules.copy(
                useHardCap = false,
            )
        )
        repeat(6) {
            state = scoreAt(state, vc, 1)
            state = scoreAt(state, animal, 1)
        }
        assertEquals(6, state.teamOne.score)
        assertEquals(6, state.teamTwo.score)
        assertNull(state.pendingCapOffer)
        assertFalse(state.halfCapRelevant())
        assertEquals(
            CapStatus("Soft cap", Duration.ofMinutes(19)),
            state.computeNextCapStatus(timestampAfterStart(state, 1)),
        )
        state = scoreAt(state, vc, 11)
        assertEquals(7, state.teamOne.score)
        assertEquals(6, state.teamTwo.score)
        assertNull(state.pendingCapOffer)
        assertFalse(state.halfCapApplied)

        // A half cap that already passed can still matter when the completed point produces 6-6,
        // even though a future half cap would not matter from that score onward.
        state = newCapState(
            capRules.copy(
                useSoftCap = false,
                useHardCap = false,
            )
        )
        state = state.copy(
            teamOne = state.teamOne.copy(score = 5),
            teamTwo = state.teamTwo.copy(score = 6),
        )
        state = scoreAt(state, vc, 11)
        assertEquals(6, state.teamOne.score)
        assertEquals(6, state.teamTwo.score)
        assertEquals(CapType.HALF, state.pendingCapOffer)

        // The same rule applies when one team has already reached the normal halftime target.
        state = newCapState(
            capRules.copy(
                useHardCap = false,
            )
        )
        repeat(7) {
            state = scoreAt(state, vc, 1)
        }
        repeat(3) {
            state = scoreAt(state, animal, 1)
        }
        assertEquals(7, state.teamOne.score)
        assertEquals(3, state.teamTwo.score)
        assertNull(state.pendingCapOffer)
        assertEquals(
            CapStatus("Soft cap", Duration.ofMinutes(19)),
            state.computeNextCapStatus(timestampAfterStart(state, 1)),
        )
        state = scoreAt(state, animal, 11)
        assertEquals(7, state.teamOne.score)
        assertEquals(4, state.teamTwo.score)
        assertNull(state.pendingCapOffer)
        assertFalse(state.halfCapApplied)

        // Once halftime has already occurred, an enabled half cap is not offered after its time.
        state = newCapState(
            capRules.copy(
                useSoftCap = false,
                useHardCap = false,
            )
        ).let { freshState ->
            freshState.copy(
                teamOne = freshState.teamOne.copy(score = 8),
                teamTwo = freshState.teamTwo.copy(score = 7),
                halftimeTaken = true,
            )
        }
        state = scoreAt(state, vc, 11)
        assertEquals(9, state.teamOne.score)
        assertEquals(7, state.teamTwo.score)
        assertNull(state.pendingCapOffer)
    }

    /**
     * Build a fresh cap-focused live state with the supplied rules.
     *
     * @param rules The cap rules to install for this scenario.
     */
    private fun newCapState(rules: GameRules = capRules): GameState {
        return standardLiveGameState(startTime = startTime, rules = rules)
    }

    /**
     * Score a point for a team at a specific minute after game start.
     *
     * @param state The current live state before the point is scored.
     * @param scoringTeam The team that scores the point.
     * @param minute The minute after game start assigned to the goal.
     */
    private fun scoreAt(
        state: GameState,
        scoringTeam: TeamId,
        minute: Int,
    ): GameState {
        return state.recordGoalFromCurrentState(
            scoringTeam = scoringTeam,
            now = timestampAfterStart(state, minute),
        )
    }
}
