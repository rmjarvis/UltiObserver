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
        val setup = GameSetupState(
            startTime = LocalTime.of(10, 0),
            rules = rules,
            teamOne = TeamSetup("Viscous Coupling", TeamColorChoice.WHITE),
            teamTwo = TeamSetup("Animal", TeamColorChoice.RED),
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
        assertEquals(2, state.teamOne.timeoutsAllowedThisHalf)
        assertEquals(2, state.teamOne.timeoutsRemaining)
        assertEquals(2, state.teamTwo.timeoutsAllowedThisHalf)
        assertEquals(2, state.teamTwo.timeoutsRemaining)
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
        assertEquals(1, state.teamTwo.timeoutsRemaining)
        assertEquals(LivePhase.LIVE_POINT, state.phase)
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
        assertEquals(1, state.teamOne.timeoutsRemaining)
        assertEquals(LivePhase.LIVE_POINT, state.phase)
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
        assertEquals(2, state.teamOne.timeoutsAllowedThisHalf)
        assertEquals(2, state.teamOne.timeoutsRemaining)
        assertEquals(2, state.teamTwo.timeoutsAllowedThisHalf)
        assertEquals(2, state.teamTwo.timeoutsRemaining)
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
        assertEquals(1, state.teamTwo.timeoutsRemaining)
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
        // Test the normal case of two timeouts per half.

        // When a timeout is called, the number remaining is decremented by one.

        // A between-points timeout extends or restarts the between-points countdown as appropriate.

        // A live-point timeout starts an "Offense set in" countdown and leaves the point live for continuation.

        // At halftime, the second half goes back to two timeouts.

        // If two timeouts have been called, a third timeout is not allowed and returns an out-of-timeouts message.

        // Test the common case of one timeout per half plus a floater.

        // If both first-half timeouts are used, only one timeout is allowed in the second half.

        // If zero or one first-half timeout is used, two timeouts are allowed in the second half.

        // Test less-common rules: zero per half plus floater, zero per half with no floater, and two per half plus floater.

        // Update timeout rules midgame and verify remaining timeouts are remapped from already-used timeouts.

        // Update timeout rules after halftime and verify floater carry-forward behavior is preserved correctly.
    }

    // Test yellow, red, blue, and technical-foul handling from public card assessment APIs.
    // Emphasize team card points, per-player records, and misconduct-threshold messages.
    @Test
    fun cardsAndTechnicalFouls() {
        // Record a first yellow for a numbered player and verify team yellow count and player-card record.

        // Record a second yellow for the same player and verify it acts as a red while adding one more team card point.

        // Record a direct red for a player with no prior yellow and verify direct-red count and two team card points.

        // Record a red for a player who already has yellow through both direct-red and second-yellow pathways.

        // Record cards for UNKNOWN_PLAYER_NUMBER and cover same-player vs different-player yellow behavior at the model boundary.

        // Record blue cards and verify they count as one team card point without player-card records.

        // Record technical fouls and verify they use the separate technical-foul count.

        // Verify first and second card/technical-foul warnings do not create misconduct consequences.

        // Verify third-and-later cards and technical fouls create between-points misconduct messages.

        // Verify third-and-later live-point misconduct reports that an offense/defense choice is needed.

        // Verify live-point offense and defense misconduct resolution messages.
    }

    // Test pull infractions from the observer-facing actions.
    // Offsides belongs to the pulling team; false start belongs to the receiving team.
    @Test
    fun pullInfractions() {
        // Start from a pull sequence with a known pulling team and pulling end.

        // Record offsides and verify only the pulling team's offsides count increments.

        // Verify the first pull-violation message sends play to the brick mark.

        // Verify the same pull sequence cannot record a second offsides for the same team.

        // Record false start and verify only the receiving team's false-start count increments.

        // Verify false-start guidance says the defense gets to set up.

        // Score the point and verify pull-sequence infraction locks reset for the next pull.

        // Build a later pull where the same team already has a violation and verify the guidance changes to midfield.

        // Manually adjust pull infractions and verify values are clamped and undo-backed.
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
