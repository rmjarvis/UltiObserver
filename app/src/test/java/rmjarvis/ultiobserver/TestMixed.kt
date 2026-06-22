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
    }

    /// Test majority-pull violations use their own label while sharing the pull-violation ladder.
    @Test
    fun majorityPullViolation() {
        val VC = TeamId.TEAM_ONE
        val ANIMAL = TeamId.TEAM_TWO
        var state = mixedLiveGameState(ratioRule = GenderRatioRule.ABBA)

        val preview = state.previewPullInfraction(VC, PullInfractionType.MAJORITY_PULL).event!!
        assertEquals("Majority pull rule violation", preview.formatPopupTitle())
        assertTrue(state.usesMajorityPullRule())

        val majorityPullResult = state.assessPullInfraction(
            VC,
            timestampAt(state, LocalTime.of(12, 0)),
            PullInfractionType.MAJORITY_PULL,
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
        assertNull(state.assessPullInfraction(VC).event)
        assertEquals(state, state.recordOffsides(timestampAt(state, LocalTime.of(12, 1))))

        val falseStartResult = state.assessPullInfraction(
            ANIMAL,
            timestampAt(state, LocalTime.of(12, 2)),
            PullInfractionType.FALSE_START,
        )
        assertEquals(PullInfractionType.FALSE_START, (falseStartResult.event as GameEvent.PullInfractionRecorded).infraction)
    }

    /// Test majority-pull corrections preserve their distinct event-log label.
    @Test
    fun majorityPullCorrection() {
        val initialState = mixedLiveGameState(ratioRule = GenderRatioRule.ABBA)
        val state = initialState
            .adjustPullInfractions(
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
     */
    private fun mixedLiveGameState(
        ratioRule: GenderRatioRule,
        initialRatio: GenderRatio = GenderRatio.FOUR_MEN_THREE_WOMEN,
        firstHalfGenZone: FieldEnd = FieldEnd.FAR,
        switchGenZoneAtHalftime: Boolean = true,
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
            ).copy(
                division = GameDivision.MIXED,
                initialGenderRatio = initialRatio,
                firstHalfGenZone = firstHalfGenZone,
                switchGenZoneAtHalftime = switchGenZoneAtHalftime,
            )
        )
    }
}
