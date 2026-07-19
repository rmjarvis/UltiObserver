package rmjarvis.ultiobserver

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/// Tests for manual and automatic water-break timing.
class TestWaterBreaks : GameDomainTestFixtures() {
    /**
     * Test manual water breaks as undoable countdown extensions.
     */
    @Test
    fun manualWaterBreak() {
        val openingState = standardLiveGameState(
            startTime = LocalTime.of(10, 0),
            rules = GameRules(
                gameTo = 15,
                useHalfCap = false,
                useSoftCap = false,
                useHardCap = false,
                waterBreakMode = WaterBreakMode.MANUAL,
                waterBreakMinutes = 4,
            ),
        )

        // When water breaks are disabled, the water-break action is unavailable.
        // (Both before the first point and between points.)
        val disabledState = openingState.copy(
            rules = openingState.rules.copy(waterBreakMode = WaterBreakMode.NONE),
        ).startGame()
        assertFalse(disabledState.canApplyWaterBreak())
        val disabledBetweenPointsState = disabledState.beginLivePoint()
            .recordGoal(TeamId.TEAM_ONE, timestampAfterStart(disabledState, 1))
        assertFalse(disabledBetweenPointsState.canApplyWaterBreak())

        // When enabled, water breaks are available between points
        val state = openingState.beginLivePoint()
            .recordGoal(TeamId.TEAM_ONE, timestampAfterStart(openingState, 1))
        val countdown = state.countdown!!
        assertTrue(state.canApplyWaterBreak())

        // Applying the water break adds the configured minutes to the active pull countdown.
        val breakState = state.applyWaterBreak(timestampAt(state, LocalTime.of(10, 2)))
        assertEquals(countdown.durationSeconds + 240, breakState.countdown?.durationSeconds)
        assertEquals(countdown.targetEpoch + 240_000L, breakState.countdown?.targetEpoch)

        // The break shows up in the event log.
        assertEquals(
            "10:02  Water break (+4 min)",
            breakState.formatEventLogLines().last(),
        )

        // Undo restores the original countdown and removes the event-log entry.
        assertEquals("Undo Water break", breakState.undoEntry?.label)
        assertUndoRestores(state, breakState)

        // A live point has no between-points countdown, so water-break time is not available.
        val livePointState = state.beginLivePoint(timestampAt(state, LocalTime.of(10, 3)))
        assertFalse(livePointState.canApplyWaterBreak())

        // Halftime has its own countdown, but water-break time is still not available because
        // this is not before a point.
        val halftimeState = startHalftimeAt(state, LocalTime.of(10, 4))
        assertEquals(CountdownKind.HALFTIME, halftimeState.countdown?.kind)
        assertFalse(halftimeState.canApplyWaterBreak())

        // A between-points misconduct countdown is still before a point, so water-break time
        // extends it even though there will not be a pull.
        val misconductCountdownState = state.copy(
            teamOne = state.teamOne.copy(blueCards = 2),
        ).assessBlueCard(TeamId.TEAM_ONE).state
        assertEquals(
            CountdownKind.MISCONDUCT_BETWEEN_POINTS,
            misconductCountdownState.countdown?.kind,
        )
        assertTrue(misconductCountdownState.canApplyWaterBreak())
        val misconductCountdown = misconductCountdownState.countdown!!
        val misconductWaterBreakState = misconductCountdownState.applyWaterBreak(
            timestampAt(misconductCountdownState, LocalTime.of(10, 4)),
        )
        assertEquals(
            misconductCountdown.durationSeconds + 240,
            misconductWaterBreakState.countdown?.durationSeconds,
        )
        assertEquals(
            misconductCountdown.targetEpoch + 240_000L,
            misconductWaterBreakState.countdown?.targetEpoch,
        )

        // Water breaks are also available before the first point for visual consistency with
        // other pre-point countdowns, even though it would be weird to use it then.
        val preGameState = openingState.startGame()
        assertTrue(preGameState.canApplyWaterBreak())
        val preGameCountdown = preGameState.countdown!!
        val preGameBreakState = preGameState.applyWaterBreak(
            timestampAt(preGameState, LocalTime.of(10, 0)),
        )
        assertEquals(
            preGameCountdown.durationSeconds + 240,
            preGameBreakState.countdown?.durationSeconds,
        )
        assertEquals(
            preGameCountdown.targetEpoch + 240_000L,
            preGameBreakState.countdown?.targetEpoch,
        )
        assertUndoRestores(preGameState, preGameBreakState)

        // Automatic mode also allows the observer to apply a water break manually using the
        // water-drop action.
        val automaticModeState = state.copy(
            rules = state.rules.copy(
                waterBreakMode = WaterBreakMode.AUTOMATIC,
                waterBreakMinutes = 2,
            ),
        )
        val automaticModeCountdown = automaticModeState.countdown!!
        val automaticModeBreak = automaticModeState.applyWaterBreak(
            timestampAt(automaticModeState, LocalTime.of(10, 4)),
        )
        assertEquals(
            automaticModeCountdown.durationSeconds + 120,
            automaticModeBreak.countdown?.durationSeconds,
        )
        assertEquals(
            automaticModeCountdown.targetEpoch + 120_000L,
            automaticModeBreak.countdown?.targetEpoch,
        )
        assertEquals(
            "10:04  Water break (+2 min)",
            automaticModeBreak.formatEventLogLines().last(),
        )
    }

    /**
     * Test automatic water-break prompts from scores and soft cap.
     */
    @Test
    fun automaticWaterBreakOffers() {
        val rules = GameRules(
            gameTo = 15,
            useHalfCap = false,
            useSoftCap = true,
            softCapMinutes = 90,
            useHardCap = false,
            waterBreakMode = WaterBreakMode.AUTOMATIC,
            waterBreakMinutes = 3,
        )
        val initialState = standardLiveGameState(
            startTime = LocalTime.of(10, 0),
            rules = rules,
        )
        var state: GameState

        // The first-half break is prompted when a team reaches the first quarter score.
        state = initialState.adjustScore(teamOneScore = 4, teamTwoScore = 0)
        assertTrue(state.automaticWaterBreakReached(TeamId.TEAM_ONE))
        assertFalse(state.automaticWaterBreakReached(TeamId.TEAM_TWO))
        val firstBreakCountdown = state.countdown!!
        state = state.applyWaterBreak(timestampAt(state, LocalTime.of(10, 8)))
        assertEquals(firstBreakCountdown.durationSeconds + 180, state.countdown?.durationSeconds)

        // Manual mode never offers an automatic water break, even at a break score.
        val manualModeState = state.copy(
            rules = state.rules.copy(waterBreakMode = WaterBreakMode.MANUAL),
        )
        assertFalse(manualModeState.automaticWaterBreakReached(TeamId.TEAM_ONE))

        // The second-half break is prompted when a team reaches the third quarter score.
        state = initialState.adjustScore(teamOneScore = 12, teamTwoScore = 0).copy(
            halftimeTaken = true,
        )
        assertTrue(state.automaticWaterBreakReached(TeamId.TEAM_ONE))
        assertFalse(state.automaticWaterBreakReached(TeamId.TEAM_TWO))
        val secondBreakCountdown = state.countdown!!
        state = state.applyWaterBreak(timestampAt(state, LocalTime.of(10, 28)))
        assertEquals(secondBreakCountdown.durationSeconds + 180, state.countdown?.durationSeconds)

        // A manual break before the first- or third-quarter score does not suppress the automatic
        // prompt.
        state = initialState.adjustScore(teamOneScore = 2, teamTwoScore = 0)
        state = state.applyWaterBreak(timestampAt(state, LocalTime.of(10, 2)))
        state = state.adjustScore(teamOneScore = 4, teamTwoScore = 0)
        assertTrue(state.automaticWaterBreakReached(TeamId.TEAM_ONE))

        state = initialState.adjustScore(teamOneScore = 10, teamTwoScore = 0).copy(
            halftimeTaken = true,
        )
        state = state.applyWaterBreak(timestampAt(state, LocalTime.of(10, 16)))
        state = state.adjustScore(teamOneScore = 12, teamTwoScore = 0)
        assertTrue(state.automaticWaterBreakReached(TeamId.TEAM_ONE))

        // The scoring team must be the one reaching the break score.
        state = state.adjustScore(teamOneScore = 12, teamTwoScore = 1)
        assertFalse(state.automaticWaterBreakReached(TeamId.TEAM_TWO))

        // The second team also reaching the break score doesn't trigger a second water break.
        state = state.adjustScore(teamOneScore = 12, teamTwoScore = 12)
        assertFalse(state.automaticWaterBreakReached(TeamId.TEAM_TWO))

        // A completed game never offers a water break, because there is no next pull countdown.
        val beforeWinningGoal = initialState.adjustScore(teamOneScore = 14, teamTwoScore = 0)
            .beginLivePoint()
        val gameOverState = beforeWinningGoal.recordGoal(
            TeamId.TEAM_ONE,
            timestampAt(beforeWinningGoal, LocalTime.of(10, 30)),
        )
        assertEquals(GamePhase.GAME_OVER, gameOverState.phase)
        assertFalse(gameOverState.automaticWaterBreakReached(TeamId.TEAM_ONE))

        // Applying soft cap before the third-quarter score should prompt for a water break.
        val beforeSoftCap = initialState.adjustScore(teamOneScore = 10, teamTwoScore = 0).copy(
            halftimeTaken = true,
            softCapApplied = false,
            pendingCapOffer = CapType.SOFT,
        )
        assertTrue(beforeSoftCap.softCapWaterBreakReached())

        // A first-half soft cap before the first-quarter break score should prompt for a water
        // break, because the capped game might end before the scheduled break score.
        val earlySoftCap = initialState.adjustScore(teamOneScore = 3, teamTwoScore = 3).copy(
            softCapApplied = false,
            pendingCapOffer = CapType.SOFT,
        )
        assertTrue(earlySoftCap.softCapWaterBreakReached())

        // A first-half soft cap after the first-quarter break score does not prompt another
        // first-half water break.
        val beforeHalftimeSoftCap = initialState.adjustScore(teamOneScore = 6, teamTwoScore = 0).copy(
            softCapApplied = false,
            pendingCapOffer = CapType.SOFT,
        )
        assertFalse(beforeHalftimeSoftCap.softCapWaterBreakReached())

        // A soft-cap water break is not prompted when water breaks are not automatic.
        assertFalse(
            beforeSoftCap.copy(
                rules = beforeSoftCap.rules.copy(waterBreakMode = WaterBreakMode.MANUAL),
            ).softCapWaterBreakReached()
        )

        // A soft-cap water break is not prompted without an eligible pull countdown.
        assertFalse(
            beforeSoftCap.beginLivePoint(timestampAt(beforeSoftCap, LocalTime.of(10, 50)))
                .softCapWaterBreakReached()
        )

        // Soft cap after the third-quarter score does not prompt for a water break.
        val lateSoftCap = initialState.adjustScore(teamOneScore = 13, teamTwoScore = 0).copy(
            halftimeTaken = true,
            softCapApplied = false,
            pendingCapOffer = CapType.SOFT,
        )
        assertFalse(lateSoftCap.softCapWaterBreakReached())
    }
}
