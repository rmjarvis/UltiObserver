package rmjarvis.ultiobserver

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

/// Tests for manual and automatic water-break timing and heat-level rules.
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
                heatLevel = HeatLevel.LEVEL_0,
                waterBreakMinutes = 4,
            ),
        )

        // When water breaks are disabled, the water-break action is unavailable.
        // (Both before the first point and between points.)
        val disabledState = openingState.copy(
            rules = openingState.rules.copy(heatLevel = HeatLevel.NONE),
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
                heatLevel = HeatLevel.LEVEL_1,
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
            nominalSoftCapMinutes = 90,
            useHardCap = false,
            heatLevel = HeatLevel.LEVEL_1,
            waterBreakMinutes = 3,
        )
        val initialState = standardLiveGameState(
            startTime = LocalTime.of(10, 0),
            rules = rules,
        )
        var state: GameState

        // The first-half break is prompted when a team reaches the first quarter score.
        state = initialState.adjustScore(teamOneScore = 3, teamTwoScore = 0)
        assertTrue(state.goalTriggersAutomaticWaterBreak(TeamId.TEAM_ONE))
        assertFalse(state.goalTriggersAutomaticWaterBreak(TeamId.TEAM_TWO))
        val scheduledOffer = state
            .beginLivePoint()
            .recordGoal(
                TeamId.TEAM_ONE,
                timestampAt(initialState, LocalTime.of(10, 7)),
            )
        assertTrue(scheduledOffer.pendingWaterBreakOffer)
        assertEquals(
            "First quarter score reached.\nTake a 3-minute water break now.",
            scheduledOffer.waterBreakPromptMessage().plainText,
        )

        // If soft cap is also active at the scheduled score, the score still triggered this
        // already-pending break.
        assertEquals(
            "First quarter score reached.\nTake a 3-minute water break now.",
            scheduledOffer.copy(softCapApplied = true)
                .waterBreakPromptMessage().plainText,
        )
        val firstBreakCountdown = scheduledOffer.countdown!!
        state = scheduledOffer.applyWaterBreak(timestampAt(scheduledOffer, LocalTime.of(10, 8)))
        assertEquals(firstBreakCountdown.durationSeconds + 180, state.countdown?.durationSeconds)

        // Manual mode never offers an automatic water break, even at a break score.
        val manualModeState = initialState.adjustScore(teamOneScore = 3, teamTwoScore = 0).copy(
            rules = initialState.rules.copy(heatLevel = HeatLevel.LEVEL_0),
        )
        assertFalse(manualModeState.goalTriggersAutomaticWaterBreak(TeamId.TEAM_ONE))

        // The second-half break is prompted when a team reaches the third quarter score.
        val beforeThirdQuarterBreak = initialState.adjustScore(
            teamOneScore = 11,
            teamTwoScore = 0,
        ).copy(
            halftimeTaken = true,
        )
        assertTrue(beforeThirdQuarterBreak.goalTriggersAutomaticWaterBreak(TeamId.TEAM_ONE))
        assertFalse(beforeThirdQuarterBreak.goalTriggersAutomaticWaterBreak(TeamId.TEAM_TWO))
        val thirdQuarterOffer = beforeThirdQuarterBreak
            .beginLivePoint()
            .recordGoal(
                TeamId.TEAM_ONE,
                timestampAt(beforeThirdQuarterBreak, LocalTime.of(10, 27)),
            )
        assertTrue(thirdQuarterOffer.pendingWaterBreakOffer)
        assertEquals(
            "Third quarter score reached.\nTake a 3-minute water break now.",
            thirdQuarterOffer.waterBreakPromptMessage().plainText,
        )
        val secondBreakCountdown = thirdQuarterOffer.countdown!!
        state = thirdQuarterOffer.applyWaterBreak(
            timestampAt(thirdQuarterOffer, LocalTime.of(10, 28))
        )
        assertEquals(secondBreakCountdown.durationSeconds + 180, state.countdown?.durationSeconds)

        // A manual break before the first- or third-quarter score does not suppress the automatic
        // prompt.
        state = initialState.adjustScore(teamOneScore = 2, teamTwoScore = 0)
        state = state.applyWaterBreak(timestampAt(state, LocalTime.of(10, 2)))
        state = state.adjustScore(teamOneScore = 3, teamTwoScore = 0)
        assertTrue(state.goalTriggersAutomaticWaterBreak(TeamId.TEAM_ONE))

        state = initialState.adjustScore(teamOneScore = 10, teamTwoScore = 0).copy(
            halftimeTaken = true,
        )
        state = state.applyWaterBreak(timestampAt(state, LocalTime.of(10, 16)))
        state = state.adjustScore(teamOneScore = 11, teamTwoScore = 0)
        assertTrue(state.goalTriggersAutomaticWaterBreak(TeamId.TEAM_ONE))

        // The scoring team must be the one reaching the break score.
        state = state.adjustScore(teamOneScore = 12, teamTwoScore = 1)
        assertFalse(state.goalTriggersAutomaticWaterBreak(TeamId.TEAM_TWO))

        // The second team also reaching the break score doesn't trigger a second water break.
        state = state.adjustScore(teamOneScore = 12, teamTwoScore = 11)
        assertFalse(state.goalTriggersAutomaticWaterBreak(TeamId.TEAM_TWO))

        // A completed game never offers a water break, because there is no next pull countdown.
        val beforeWinningGoal = initialState.adjustScore(teamOneScore = 14, teamTwoScore = 0)
            .beginLivePoint()
        val gameOverState = beforeWinningGoal.recordGoal(
            TeamId.TEAM_ONE,
            timestampAt(beforeWinningGoal, LocalTime.of(10, 30)),
        )
        assertEquals(GamePhase.GAME_OVER, gameOverState.phase)
        assertFalse(beforeWinningGoal.goalTriggersAutomaticWaterBreak(TeamId.TEAM_ONE))
        assertFalse(gameOverState.goalTriggersAutomaticWaterBreak(TeamId.TEAM_ONE))

        // A cap-adjusted winning score takes precedence over a scheduled water break.
        val beforeCappedWinningGoal = initialState
            .adjustScore(teamOneScore = 11, teamTwoScore = 0)
            .copy(
                halftimeTaken = true,
                winningScore = 12,
            )
        assertFalse(beforeCappedWinningGoal.goalTriggersAutomaticWaterBreak(TeamId.TEAM_ONE))

        // A cap-adjusted halftime target also takes precedence over a scheduled water break.
        val beforeCappedHalftime = initialState
            .adjustScore(teamOneScore = 3, teamTwoScore = 0)
            .copy(halftimeTargetScore = 4)
        assertFalse(beforeCappedHalftime.goalTriggersAutomaticWaterBreak(TeamId.TEAM_ONE))

        // Applying soft cap before the third-quarter score should prompt for a water break.
        val beforeSoftCap = initialState.adjustScore(teamOneScore = 10, teamTwoScore = 0).copy(
            halftimeTaken = true,
            softCapApplied = false,
            pendingCapOffer = CapType.SOFT,
        )
        assertTrue(beforeSoftCap.softCapWaterBreakReached())
        val softCapOffer = beforeSoftCap.applyPendingCap(
            timestampAt(beforeSoftCap, LocalTime.of(10, 40)),
        )
        assertTrue(softCapOffer.pendingWaterBreakOffer)
        assertEquals(
            "Soft cap triggers the third-quarter water break.\n" +
                "Take a 3-minute water break now.",
            softCapOffer.waterBreakPromptMessage().plainText,
        )

        // Applying soft cap preserves a water-break offer that was already pending.
        assertTrue(
            beforeSoftCap.copy(pendingWaterBreakOffer = true)
                .applyPendingCap(timestampAt(beforeSoftCap, LocalTime.of(10, 41)))
                .pendingWaterBreakOffer
        )

        // A first-half soft cap before the first-quarter break score should prompt for a water
        // break, because the capped game might end before the scheduled break score.
        val earlySoftCap = initialState.adjustScore(teamOneScore = 3, teamTwoScore = 3).copy(
            softCapApplied = false,
            pendingCapOffer = CapType.SOFT,
        )
        assertTrue(earlySoftCap.softCapWaterBreakReached())
        val firstHalfSoftCapOffer = earlySoftCap.applyPendingCap(
            timestampAt(earlySoftCap, LocalTime.of(10, 42)),
        )
        assertEquals(
            "Soft cap triggers the first-quarter water break.\n" +
                "Take a 3-minute water break now.",
            firstHalfSoftCapOffer.waterBreakPromptMessage().plainText,
        )

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
                rules = beforeSoftCap.rules.copy(heatLevel = HeatLevel.LEVEL_0),
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

    /**
     * Test how the heat level impacts the other rules.
     */
    @Test
    fun effectiveHeatRules() {
        // Water-break behavior is derived from the selected heat level.
        assertEquals(
            listOf(
                WaterBreakMode.NONE,
                WaterBreakMode.MANUAL,
                WaterBreakMode.AUTOMATIC,
                WaterBreakMode.AUTOMATIC,
                WaterBreakMode.NONE,
            ),
            HeatLevel.entries.map { heatLevel ->
                GameRules(heatLevel = heatLevel).waterBreakMode
            },
        )

        // Default water break for level 0, 1 is 3 minutes
        assertEquals(3, DEFAULT_WATER_BREAK_MINUTES)
        assertEquals(
            GameRules().copy(
                heatLevel = HeatLevel.LEVEL_1,
                waterBreakMinutes = 3,
            ),
            GameRules().withHeatLevel(HeatLevel.LEVEL_1),
        )
        assertEquals(
            DEFAULT_WATER_BREAK_MINUTES,
            GameRules().withHeatLevel(HeatLevel.LEVEL_2)
                .withHeatLevel(HeatLevel.LEVEL_0)
                .waterBreakMinutes,
        )

        // Suspending the game for level 3 leaves the configured water-break duration untouched.
        assertEquals(
            6,
            GameRules(waterBreakMinutes = 6)
                .withHeatLevel(HeatLevel.LEVEL_3)
                .waterBreakMinutes,
        )

        // Level 2 adjusts the soft/hard cap times and time between points.
        val levelTwoRules = GameRules().withHeatLevel(HeatLevel.LEVEL_2)
        assertEquals(120, levelTwoRules.timeBetweenPointsSeconds)
        assertEquals("120 sec", levelTwoRules.formatTimeBetweenPoints(compact = true))
        assertEquals("60 +60 sec", levelTwoRules.formatTimeBetweenPoints(compact = false))
        assertTrue(levelTwoRules.capEnabled(CapType.SOFT))
        assertTrue(levelTwoRules.capEnabled(CapType.HARD))
        assertEquals(70, levelTwoRules.softCapMinutes)
        assertEquals(90, levelTwoRules.hardCapMinutes)
        assertEquals(
            "+45/+70/+90",
            levelTwoRules.formatCaps(),
        )
        assertEquals(
            "Heat level 2 is shortening this to 70 minutes.",
            levelTwoRules.heatLevelTwoCapEffectNote(CapType.SOFT),
        )
        assertEquals(
            "Heat level 2 is shortening this to 90 minutes.",
            levelTwoRules.heatLevelTwoCapEffectNote(CapType.HARD),
        )
        assertEquals(
            "The soft/hard caps are shortened to 70/90 minutes.",
            levelTwoRules.heatLevelTwoCapGuidance(),
        )
        assertEquals("+70 (was +90)", levelTwoRules.formatCap(CapType.SOFT))
        assertEquals("+90 (was +105)", levelTwoRules.formatCap(CapType.HARD))
        assertNull(levelTwoRules.heatLevelTwoCapEffectNote(CapType.HALF))

        // Rules without heat adjustments use their nominal timing and cap summaries.
        val levelOneRules = GameRules().withHeatLevel(HeatLevel.LEVEL_1)
        assertEquals("60 sec", levelOneRules.formatTimeBetweenPoints(compact = false))
        assertEquals("+90", levelOneRules.formatCap(CapType.SOFT))
        assertEquals("+105", levelOneRules.formatCap(CapType.HARD))
        assertEquals(
            "None",
            GameRules(useSoftCap = false).formatCap(CapType.SOFT),
        )
        assertNull(levelOneRules.heatLevelTwoCapEffectNote(CapType.SOFT))
        assertNull(levelOneRules.heatLevelTwoCapEffectNote(CapType.HARD))

        // For non-standard time between points, level 2 adds 60 seconds
        // Also for shorter caps that are still more than the level two adjusted times.
        val shortGame = GameRules(
            nominalTimeBetweenPointsSeconds = 50,
            nominalSoftCapMinutes = 80,
            nominalHardCapMinutes = 95,
        ).withHeatLevel(HeatLevel.LEVEL_2)
        assertEquals(110, shortGame.timeBetweenPointsSeconds)
        assertEquals(70, shortGame.softCapMinutes)
        assertEquals(90, shortGame.hardCapMinutes)

        // When there are no soft/hard caps, level 2 forces them.
        val noCaps = GameRules(
            useHalfCap = false,
            useSoftCap = false,
            useHardCap = false,
        ).withHeatLevel(HeatLevel.LEVEL_2)
        assertFalse(noCaps.capEnabled(CapType.HALF))
        assertTrue(noCaps.capEnabled(CapType.SOFT))
        assertTrue(noCaps.capEnabled(CapType.HARD))
        assertEquals(70, noCaps.softCapMinutes)
        assertEquals(90, noCaps.hardCapMinutes)
        assertEquals(
            "Heat level 2 is overriding this to 70 minutes.",
            noCaps.heatLevelTwoCapEffectNote(CapType.SOFT),
        )
        assertEquals(
            "Heat level 2 is overriding this to 90 minutes.",
            noCaps.heatLevelTwoCapEffectNote(CapType.HARD),
        )
        assertEquals(
            "The soft/hard caps are set to 70/90 minutes.",
            noCaps.heatLevelTwoCapGuidance(),
        )
        assertEquals("+70 (was none)", noCaps.formatCap(CapType.SOFT))
        assertEquals("+90 (was none)", noCaps.formatCap(CapType.HARD))

        // The "set to" syntax is used if either one is not normally enabled.
        assertEquals(
            "The soft/hard caps are set to 70/90 minutes.",
            noCaps.copy(useSoftCap = true).heatLevelTwoCapGuidance(),
        )
        assertEquals(
            "The soft/hard caps are set to 70/90 minutes.",
            noCaps.copy(useHardCap = true).heatLevelTwoCapGuidance(),
        )

        // A shorter nominal hard cap stays in force, and soft cap remains 20 minutes earlier.
        val shorterHardCap = GameRules(
            nominalSoftCapMinutes = 70,
            nominalHardCapMinutes = 80,
        ).withHeatLevel(HeatLevel.LEVEL_2)
        assertEquals(80, shorterHardCap.hardCapMinutes)
        assertEquals(60, shorterHardCap.softCapMinutes)
        assertNull(shorterHardCap.heatLevelTwoCapEffectNote(CapType.HARD))
        assertEquals(
            "The soft cap is shortened to 60 minutes.",
            shorterHardCap.heatLevelTwoCapGuidance()
        )

        // If soft cap is already short enough, then it isn't edited.
        val shorterSoftCap = GameRules(
            nominalSoftCapMinutes = 60,
            nominalHardCapMinutes = 95,
        ).withHeatLevel(HeatLevel.LEVEL_2)
        assertEquals(90, shorterSoftCap.hardCapMinutes)
        assertEquals(60, shorterSoftCap.softCapMinutes)
        assertNull(shorterSoftCap.heatLevelTwoCapEffectNote(CapType.SOFT))
        assertEquals(
            "The hard cap is shortened to 90 minutes.",
            shorterSoftCap.heatLevelTwoCapGuidance()
        )

        // If both are already short enough, they aren't changed.
        val veryShortGame = GameRules(
            nominalSoftCapMinutes = 60,
            nominalHardCapMinutes = 80,
        ).withHeatLevel(HeatLevel.LEVEL_2)
        assertEquals(60, veryShortGame.softCapMinutes)
        assertEquals(80, veryShortGame.hardCapMinutes)
        assertNull(veryShortGame.heatLevelTwoCapGuidance())

        // Dropping the heat level restores the persisted nominal values and enabled flags.
        val shortGameLevel1 = shortGame.withHeatLevel(HeatLevel.LEVEL_1)
        assertEquals(50, shortGameLevel1.timeBetweenPointsSeconds)
        assertEquals(80, shortGameLevel1.softCapMinutes)
        assertEquals(95, shortGameLevel1.hardCapMinutes)
        val noCapsLevel1 = noCaps.withHeatLevel(HeatLevel.LEVEL_1)
        assertFalse(noCapsLevel1.capEnabled(CapType.HALF))
        assertFalse(noCapsLevel1.capEnabled(CapType.SOFT))
        assertFalse(noCapsLevel1.capEnabled(CapType.HARD))
    }

    /**
     * Test heat-level descriptions from the More actions Set heat level dialog
     * Especially, the level 2 descriptions depend on the cap times.
     */
    @Test
    fun heatLevelDescriptions() {
        // Level 0 description with a custom break time.
        val levelZero = GameRules(
            heatLevel = HeatLevel.LEVEL_0,
            waterBreakMinutes = 5,
        )
        assertEquals(
            "Use normal timing with 5-minute manual water breaks available.",
            levelZero.heatLevelSelectionDescription(HeatLevel.LEVEL_0),
        )

        // Level 1 description with a custom break time.
        val levelOne = GameRules(
            heatLevel = HeatLevel.LEVEL_1,
            waterBreakMinutes = 5,
        )
        assertEquals(
            "One 5-minute water break per half.",
            levelOne.heatLevelSelectionDescription(HeatLevel.LEVEL_1),
        )

        // Level 2 description with a custom break time depends on the cap times.
        // First the USAU defaults.
        val levelTwo = GameRules(
            heatLevel = HeatLevel.LEVEL_2,
            waterBreakMinutes = 6,
        )
        assertEquals(
            "One 6-minute water break per half. Add 60 seconds between points. " +
                "Adjust soft/hard caps.",
            levelTwo.heatLevelSelectionDescription(HeatLevel.LEVEL_2),
        )

        // Level 3 descriptions is about suspending the game.
        assertEquals(
            "Suspend this game because play should not continue.",
            levelTwo.heatLevelSelectionDescription(HeatLevel.LEVEL_3),
        )

        // None has a very simple description.
        assertEquals(
            "Disable water breaks.",
            levelTwo.heatLevelSelectionDescription(HeatLevel.NONE),
        )

        // The description for level 0 when switching from a different level always shows
        // the default water break time.
        assertEquals(
            "Use normal timing with 3-minute manual water breaks available.",
            levelOne.heatLevelSelectionDescription(HeatLevel.LEVEL_0),
        )
        assertEquals(
            "Use normal timing with 3-minute manual water breaks available.",
            levelTwo.heatLevelSelectionDescription(HeatLevel.LEVEL_0),
        )

        // The description for level 1 when switching from a different level always shows
        // the default water break time.
        assertEquals(
            "One 3-minute water break per half.",
            levelZero.heatLevelSelectionDescription(HeatLevel.LEVEL_1),
        )
        assertEquals(
            "One 3-minute water break per half.",
            levelTwo.heatLevelSelectionDescription(HeatLevel.LEVEL_1),
        )

        // The description for level 2 when switching from a different level always shows
        // the default water break time.
        assertEquals(
            "One 4-minute water break per half. Add 60 seconds between points. " +
                "Adjust soft/hard caps.",
            levelZero.heatLevelSelectionDescription(HeatLevel.LEVEL_2),
        )
        assertEquals(
            "One 4-minute water break per half. Add 60 seconds between points. " +
                "Adjust soft/hard caps.",
            levelOne.heatLevelSelectionDescription(HeatLevel.LEVEL_2),
        )

        // When only soft cap is affected, the Level 2 option names only soft cap.
        val onlySoftAffected = levelOne.copy(
            nominalSoftCapMinutes = 70,
            nominalHardCapMinutes = 80,
        )
        assertEquals(
            "One 4-minute water break per half. Add 60 seconds between points. " +
                "Adjust soft cap.",
            onlySoftAffected.heatLevelSelectionDescription(HeatLevel.LEVEL_2),
        )

        // When only hard cap is affected, the Level 2 option names only hard cap.
        val onlyHardAffected = levelOne.copy(
            nominalSoftCapMinutes = 70,
            nominalHardCapMinutes = 105,
        )
        assertEquals(
            "One 4-minute water break per half. Add 60 seconds between points. " +
                "Adjust hard cap.",
            onlyHardAffected.heatLevelSelectionDescription(HeatLevel.LEVEL_2),
        )

        // When neither cap is affected, the Level 2 option omits cap guidance.
        val neitherCapAffected = levelOne.copy(
            nominalSoftCapMinutes = 60,
            nominalHardCapMinutes = 80,
        )
        assertEquals(
            "One 4-minute water break per half. Add 60 seconds between points.",
            neitherCapAffected.heatLevelSelectionDescription(HeatLevel.LEVEL_2),
        )

        // When both caps are disabled, the Level 2 option says that both will be adjusted.
        val capsDisabled = GameRules(
            heatLevel = HeatLevel.LEVEL_1,
            useSoftCap = false,
            useHardCap = false,
        )
        assertEquals(
            "One 4-minute water break per half. Add 60 seconds between points. " +
                "Adjust soft/hard caps.",
            capsDisabled.heatLevelSelectionDescription(HeatLevel.LEVEL_2),
        )
    }

    /**
     * Test water-break targets based on game length and the actual halftime score.
     */
    @Test
    fun waterBreakTargets() {
        // Run these tests for a few different gameTo values.
        listOf(
            11 to Triple(3, 9, 8),
            13 to Triple(4, 10, 8),
            15 to Triple(4, 12, 9),
            17 to Triple(5, 13, 9),
        ).forEach { (gameTo, breaks) ->
            val (firstHalfBreak, secondHalfBreak, altSecondHalfBreak) = breaks
            // Normal water breaks are at the first point after "quarter".
            val rules = GameRules(gameTo = gameTo).withHeatLevel(HeatLevel.LEVEL_1)
            val state = standardLiveGameState(rules = rules)
            assertEquals(listOf(firstHalfBreak, secondHalfBreak), state.waterBreakScores())

            // Scoring a goal at Q1 or Q3 triggers an automatic water break.
            val q1State = state.copy(
                halftimeTaken = false,
            ).adjustScore(teamOneScore = firstHalfBreak - 1, teamTwoScore = 2)
            assertEquals(listOf(firstHalfBreak, secondHalfBreak), q1State.waterBreakScores())
            assertEquals(firstHalfBreak, q1State.waterBreakScore())
            assertTrue(q1State.goalTriggersAutomaticWaterBreak(TeamId.TEAM_ONE))

            val q3State = state.copy(
                halftimeTaken = true,
            ).adjustScore(teamOneScore = firstHalfBreak, teamTwoScore = secondHalfBreak - 1)
            assertEquals(listOf(firstHalfBreak, secondHalfBreak), q3State.waterBreakScores())
            assertEquals(secondHalfBreak, q3State.waterBreakScore())
            assertTrue(q3State.goalTriggersAutomaticWaterBreak(TeamId.TEAM_TWO))

            // If halftime happens early, then the second half water break is the normal
            // number of points after the actual halftime score.
            val earlyHalfState = state.copy(
                halftimeTaken = true,
                halftimeHighScore = 5,
            )
            assertEquals(
                listOf(firstHalfBreak, altSecondHalfBreak),
                earlyHalfState.waterBreakScores()
            )
            assertEquals(altSecondHalfBreak, earlyHalfState.waterBreakScore())
        }

        // The halftime high score is automatically recorded when starting halftime early.
        val manualHalftimeState = standardLiveGameState()
            .adjustScore(teamOneScore = 5, teamTwoScore = 3)
            .copy(phase = GamePhase.BETWEEN_POINTS)
            .startHalftimeNow(System.currentTimeMillis())
        assertEquals(5, manualHalftimeState.halftimeHighScore)
    }

    /**
     * Test changing the heat level mid-game.
     */
    @Test
    fun liveHeatLevelChanges() {
        // Upgrading to Level 2 immediately extends the active countdown and its pull timing.
        val now = System.currentTimeMillis()
        var state = standardLiveGameState(
            rules = GameRules().withHeatLevel(HeatLevel.LEVEL_1),
        ).recordGoalFromCurrentState(TeamId.TEAM_ONE, now)
        val originalCountdown = state.countdown!!

        state = state.setHeatLevel(HeatLevel.LEVEL_2, now + 1_000L)
        assertEquals(
            originalCountdown.durationSeconds + 60,
            state.countdown?.durationSeconds,
        )
        assertEquals(
            originalCountdown.targetEpoch + 60_000L,
            state.countdown?.targetEpoch,
        )
        assertEquals(
            originalCountdown.pullTiming!!.offenseReadySeconds + 60,
            state.countdown?.pullTiming?.offenseReadySeconds,
        )
        assertEquals(120, state.rules.timeBetweenPointsSeconds)
        assertEquals("Undo Heat level 2", state.undoEntry?.label)
        assertTrue(state.formatEventLogLines().last().endsWith("Heat level changed to 2"))

        // Reselecting the active heat level returns the current state unchanged.
        assertSame(state, state.setHeatLevel(HeatLevel.LEVEL_2, now + 1_500L))

        // An existing water-break offer survives another automatic heat-level selection.
        val alreadyPendingOffer = state.copy(pendingWaterBreakOffer = true)
            .setHeatLevel(HeatLevel.LEVEL_1, now + 1_500L)
            .setHeatLevel(HeatLevel.LEVEL_2, now + 1_600L)
        assertTrue(alreadyPendingOffer.pendingWaterBreakOffer)

        // A downgrade preserves this countdown but changes future effective timing.
        val extendedCountdown = state.countdown
        state = state.setHeatLevel(HeatLevel.LEVEL_1, now + 2_000L)
        assertEquals(extendedCountdown, state.countdown)
        assertEquals(60, state.rules.timeBetweenPointsSeconds)

        // Level 0 retains the manual action, while None removes it.
        val levelZero = state.setHeatLevel(HeatLevel.LEVEL_0, now + 3_000L)
        assertTrue(levelZero.canApplyWaterBreak())
        val disabled = levelZero.setHeatLevel(HeatLevel.NONE, now + 4_000L)
        assertFalse(disabled.canApplyWaterBreak())
        assertEquals(
            "Take a 3-minute water break now.",
            levelZero.waterBreakPromptMessage().plainText,
        )

        // Activating level 1 after the quarter score offers a break at that point.
        val lateState = disabled.adjustScore(teamOneScore = 5, teamTwoScore = 0)
        assertTrue(lateState.shouldOfferLateWaterBreak(HeatLevel.LEVEL_1))
        val atScheduledScore = disabled.adjustScore(teamOneScore = 4, teamTwoScore = 0)
            .setHeatLevel(HeatLevel.LEVEL_1, now + 4_100L)
        assertTrue(atScheduledScore.pendingWaterBreakOffer)
        assertEquals(
            "First quarter score reached.\nTake a 3-minute water break now.",
            atScheduledScore.waterBreakPromptMessage().plainText,
        )

        // ... But not if there was already a water break taken earlier in the half.
        val withEarlierBreak = lateState.copy(
            eventLog = lateState.eventLog + EventLogEntry(
                timestampEpoch = now,
                type = EventLogType.WATER_BREAK,
                delta = 3,
            )
        )
        assertFalse(withEarlierBreak.shouldOfferLateWaterBreak(HeatLevel.LEVEL_1))

        // A completed game cannot acquire a late water-break offer.
        assertFalse(
            lateState.copy(phase = GamePhase.GAME_OVER)
                .shouldOfferLateWaterBreak(HeatLevel.LEVEL_1)
        )

        // A water break from the first half does not suppress a late second-half offer.
        val secondHalfState = standardLiveGameState().copy(
            halftimeTaken = true,
            halftimeHighScore = 8,
            eventLog = listOf(
                EventLogEntry(now, EventLogType.WATER_BREAK, delta = 3),
                EventLogEntry(now + 1_000L, EventLogType.HALFTIME),
            ),
        ).adjustScore(teamOneScore = 12, teamTwoScore = 8)
            .copy(phase = GamePhase.BETWEEN_POINTS)
        assertFalse(secondHalfState.hasWaterBreakThisHalf())
        assertTrue(secondHalfState.shouldOfferLateWaterBreak(HeatLevel.LEVEL_1))

        // A level change during a live point carries the offer to the next between-points state.
        val changedDuringPoint = disabled.adjustScore(teamOneScore = 5, teamTwoScore = 0)
            .copy(phase = GamePhase.LIVE_POINT, countdown = null)
            .setHeatLevel(HeatLevel.LEVEL_1, now + 4_500L)
        assertTrue(changedDuringPoint.pendingWaterBreakOffer)
        assertEquals(
            "Level 1 is now in effect, and no water break has been taken this half.\n" +
                "Take a 3-minute water break now.",
            changedDuringPoint.waterBreakPromptMessage().plainText,
        )
        val afterPoint = changedDuringPoint.recordGoal(TeamId.TEAM_ONE, now + 4_600L)
        assertTrue(afterPoint.pendingWaterBreakOffer)

        // Water break offers can be declined, which just clear the pending offer.
        assertFalse(afterPoint.declinePendingWaterBreak().pendingWaterBreakOffer)

        // Level 3 suspends the game and preserves its distinct undo and event-log wording.
        val suspended = disabled.setHeatLevel(HeatLevel.LEVEL_3, now + 5_000L)
        assertEquals(GamePhase.GAME_OVER, suspended.phase)
        assertEquals(now + 5_000L, suspended.endEpoch)
        assertNull(suspended.countdown)
        assertEquals("Undo Heat level 3 — game suspended", suspended.undoEntry?.label)
        assertEquals("Game suspended", GamePrompt.GameOver(suspended).formatTitle())
        assertEquals(
            "Undo Heat level 3 — game suspended",
            suspended.pruneUndoHistory().undoEntry?.label,
        )
        assertTrue(
            suspended.formatEventLogLines().last()
                .endsWith("Heat level 3 — game suspended")
        )
    }
}
