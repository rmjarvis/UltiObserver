package rmjarvis.ultiobserver

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/// Tests for mixed-division gender-ratio and majority-pull rules.
class TestMixed : GameDomainTestFixtures() {
    /// Test ABBA and fixed gender-ratio rules for the upcoming point.
    @Test
    fun mixedPointRatios() {
        val VC = TeamId.TEAM_ONE
        val ANIMAL = TeamId.TEAM_TWO
        var state = mixedLiveGameState(
            ratioRule = GenderRatioRule.ABBA,
            initialRatio = GenderRatio.FOUR_MEN_THREE_WOMEN,
        )

        assertEquals(1, state.currentPointNumber())
        assertEquals(GenderRatio.FOUR_MEN_THREE_WOMEN, state.currentGenderRatio())
        state = state.recordGoal(VC, timestampAt(state, LocalTime.of(11, 1)))
        assertEquals(2, state.currentPointNumber())
        assertEquals(GenderRatio.FOUR_WOMEN_THREE_MEN, state.currentGenderRatio())
        state = state.recordGoal(ANIMAL, timestampAt(state, LocalTime.of(11, 2)))
        assertEquals(3, state.currentPointNumber())
        assertEquals(GenderRatio.FOUR_WOMEN_THREE_MEN, state.currentGenderRatio())
        state = state.recordGoal(VC, timestampAt(state, LocalTime.of(11, 3)))
        assertEquals(4, state.currentPointNumber())
        assertEquals(GenderRatio.FOUR_MEN_THREE_WOMEN, state.currentGenderRatio())

        val reversedInitial = mixedLiveGameState(
            ratioRule = GenderRatioRule.ABBA,
            initialRatio = GenderRatio.FOUR_WOMEN_THREE_MEN,
        )
        assertEquals(GenderRatio.FOUR_WOMEN_THREE_MEN, reversedInitial.currentGenderRatio())

        val fixedFourMen = mixedLiveGameState(ratioRule = GenderRatioRule.FIXED_4M_3W)
        val fixedFourWomen = mixedLiveGameState(ratioRule = GenderRatioRule.FIXED_4W_3M)
        assertEquals(GenderRatio.FOUR_MEN_THREE_WOMEN, fixedFourMen.currentGenderRatio())
        assertEquals(GenderRatio.FOUR_WOMEN_THREE_MEN, fixedFourWomen.currentGenderRatio())
        assertEquals(GenderRatio.FOUR_WOMEN_THREE_MEN, GenderRatio.FOUR_MEN_THREE_WOMEN.flip())
        assertEquals(GenderRatio.FOUR_MEN_THREE_WOMEN, GenderRatio.FOUR_WOMEN_THREE_MEN.flip())
        assertEquals("Gen Zone", GenderRatioRule.GEN_ZONE.displayText)
        assertEquals("4W/3M", GenderRatio.FOUR_WOMEN_THREE_MEN.displayText)
        assertTrue(GenderRatioRule.ABBA.hasDisplayablePointRatio())
        assertTrue(GenderRatioRule.FIXED_4M_3W.hasDisplayablePointRatio())
        assertTrue(GenderRatioRule.FIXED_4W_3M.hasDisplayablePointRatio())
        assertFalse(GenderRatioRule.GEN_ZONE.hasDisplayablePointRatio())
        assertFalse(GenderRatioRule.OFFENSE_DECIDES.hasDisplayablePointRatio())
        assertFalse(GenderRatioRule.NA.hasDisplayablePointRatio())
        assertTrue(GenderRatioRule.ABBA.hasStartingPullChoice())
        assertTrue(GenderRatioRule.GEN_ZONE.hasStartingPullChoice())
        assertFalse(GenderRatioRule.OFFENSE_DECIDES.hasStartingPullChoice())

        val noAppAssistance = mixedLiveGameState(ratioRule = GenderRatioRule.NA)
        assertNull(noAppAssistance.currentGenderRatio())
        assertNull(noAppAssistance.ratioChoosingTeam())
    }

    /// Test Gen Zone and Offense Decides chooser-team rules.
    @Test
    fun mixedRatioChooserTeams() {
        val VC = TeamId.TEAM_ONE
        val ANIMAL = TeamId.TEAM_TWO
        val genZoneState = mixedLiveGameState(
            ratioRule = GenderRatioRule.GEN_ZONE,
            firstHalfGenZone = FieldEnd.FAR,
        )
        assertNull(genZoneState.currentGenderRatio())
        assertEquals(VC, genZoneState.ratioChoosingTeam())
        assertEquals(ANIMAL, genZoneState.copy(halftimeTaken = true).ratioChoosingTeam())
        assertEquals(
            VC,
            genZoneState.copy(
                halftimeTaken = true,
                switchGenZoneAtHalftime = false,
            ).ratioChoosingTeam(),
        )

        val offenseDecidesState = mixedLiveGameState(ratioRule = GenderRatioRule.OFFENSE_DECIDES)
        assertNull(offenseDecidesState.currentGenderRatio())
        assertEquals(ANIMAL, offenseDecidesState.ratioChoosingTeam())

        val openDivisionState = standardLiveGameState()
        assertNull(openDivisionState.currentGenderRatio())
        assertNull(openDivisionState.ratioChoosingTeam())
        assertFalse(openDivisionState.usesMajorityPullRule())
        assertFalse(
            mixedLiveGameState(ratioRule = GenderRatioRule.ABBA)
                .copy(rules = GameRules(useMajorityPullRule = false))
                .usesMajorityPullRule()
        )
    }

    /// Test majority-pull violations use their own label while sharing the pull-violation ladder.
    @Test
    fun majorityPullViolation() {
        val VC = TeamId.TEAM_ONE
        val ANIMAL = TeamId.TEAM_TWO
        var state = mixedLiveGameState(ratioRule = GenderRatioRule.ABBA)

        val preview = state.previewPullViolation(VC, PullViolationType.MAJORITY_PULL).event!!
        assertEquals("Majority pull rule violation", preview.formatPopupTitle())
        assertTrue(state.usesMajorityPullRule())

        val majorityPullResult = state.assessPullViolation(
            VC,
            timestampAt(state, LocalTime.of(12, 0)),
            PullViolationType.MAJORITY_PULL,
        )
        state = majorityPullResult.state
        assertEquals(GamePhase.LIVE_POINT, state.phase)
        assertEquals(0, state.teamOne.offsides)
        assertEquals(1, state.teamOne.majorityPullViolations)
        assertEquals(1, state.teamOne.pullViolationCount())
        assertTrue(state.pullSequenceOffsidesRecorded)
        assertEquals("Majority pull violation on Viscous Coupling.", state.lastEvent)
        assertEquals("Undo Majority pull violation on Viscous Coupling", state.undoEntry?.label)
        assertEquals(EventLogType.MAJORITY_PULL, state.eventLog.last().type)
        assertEquals("12:00  Majority pull violation on Viscous Coupling", state.formatEventLogLine(state.eventLog.last()))
        assertEquals(
            "This is Viscous Coupling's first pull violation.\n\n" +
                "Animal starts at the brick mark.\n\n" +
                "The disc is live -- no defensive check is required.",
            majorityPullResult.message(),
        )
        assertNull(state.assessPullViolation(VC).event)
        assertEquals(state, state.recordMajorityPullViolation(timestampAt(state, LocalTime.of(12, 1))))
        assertEquals(state, state.recordOffsides(timestampAt(state, LocalTime.of(12, 1))))

        // False-start selection still resolves to the ordinary receiving-team pull violation.
        val falseStartResult = state.assessPullViolation(
            ANIMAL,
            timestampAt(state, LocalTime.of(12, 2)),
            PullViolationType.FALSE_START,
        )
        assertEquals(
            PullViolationType.FALSE_START,
            (falseStartResult.event as GameEvent.PullViolationRecorded).violation,
        )

        // Majority-pull counts are tracked on whichever team pulled the current point.
        val animalPullingState = mixedLiveGameState(
            ratioRule = GenderRatioRule.ABBA,
            pullingTeam = ANIMAL,
        )
        val animalPreview = animalPullingState.previewPullViolation(ANIMAL, PullViolationType.MAJORITY_PULL)
            .event as GameEvent.PullViolationRecorded
        assertEquals(1, animalPreview.state.teamTwo.majorityPullViolations)
        val animalMajorityPullResult = animalPullingState.assessPullViolation(
            ANIMAL,
            timestampAt(animalPullingState, LocalTime.of(12, 3)),
            PullViolationType.MAJORITY_PULL,
        )
        assertEquals(1, animalMajorityPullResult.state.teamTwo.majorityPullViolations)
        assertEquals("Majority pull violation on Animal.", animalMajorityPullResult.state.lastEvent)

        // Direct helper calls can arrive after stale UI or test setup states; normal UI disables these paths.
        val openDivisionState = standardLiveGameState()
        assertEquals(openDivisionState, openDivisionState.recordMajorityPullViolation(timestampAt(state, LocalTime.of(12, 4))))
        val skippedPullState = mixedLiveGameState(ratioRule = GenderRatioRule.ABBA)
            .copy(pullSkippedForCurrentPoint = true)
        assertEquals(skippedPullState, skippedPullState.recordMajorityPullViolation(timestampAt(state, LocalTime.of(12, 5))))
    }

    /// Test majority-pull corrections preserve their distinct event-log label.
    @Test
    fun majorityPullCorrection() {
        val initialState = mixedLiveGameState(ratioRule = GenderRatioRule.ABBA)
        val state = initialState
            .adjustPullViolations(
                teamOneOffsides = 0,
                teamOneFalseStarts = 0,
                teamOneMajorityPulls = 2,
                teamTwoOffsides = 1,
                teamTwoFalseStarts = 0,
                teamTwoMajorityPulls = 0,
                now = timestampAt(initialState, LocalTime.of(12, 0)),
            )

        assertEquals(2, state.teamOne.majorityPullViolations)
        assertEquals(2, state.teamOne.pullViolationCount())
        assertEquals("12:00  Adjusted Viscous Coupling majority pull violations +2", state.formatEventLogLine(state.eventLog[0]))
        assertEquals("12:00  Adjusted Animal offsides +1", state.formatEventLogLine(state.eventLog[1]))
    }

    /**
     * Build a mixed live state with only the relevant mixed-rule values varied.
     *
     * @param ratioRule The mixed gender-ratio rule to install.
     * @param initialRatio The first-point ABBA ratio.
     * @param firstHalfGenZone The Gen Zone end for the first half.
     * @param switchGenZoneAtHalftime Whether Gen Zone switches after halftime.
     * @param pullingTeam The team pulling the opening pull.
     */
    private fun mixedLiveGameState(
        ratioRule: GenderRatioRule,
        initialRatio: GenderRatio = GenderRatio.FOUR_MEN_THREE_WOMEN,
        firstHalfGenZone: FieldEnd = FieldEnd.FAR,
        switchGenZoneAtHalftime: Boolean = true,
        pullingTeam: TeamId = TeamId.TEAM_ONE,
    ): GameState {
        return createLiveGameState(
            standardGameSetup(
                startTime = LocalTime.of(12, 0),
                rules = GameRules(
                    gameTo = 7,
                    useHalfCap = false,
                    useSoftCap = false,
                    useHardCap = false,
                    genderRatioRule = ratioRule,
                    useMajorityPullRule = true,
                ),
                pullingTeam = pullingTeam,
            ).copy(
                division = GameDivision.MIXED,
                initialGenderRatio = initialRatio,
                firstHalfGenZone = firstHalfGenZone,
                switchGenZoneAtHalftime = switchGenZoneAtHalftime,
            )
        )
    }
}
