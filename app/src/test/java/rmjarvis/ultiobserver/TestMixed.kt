package rmjarvis.ultiobserver

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/// Tests for mixed-division gender-ratio and majority-pull rules.
class TestMixed : GameDomainTestFixtures() {
    /**
     * Test default and observer-selected field-badge colors.
     */
    @Test
    fun genderRatioBadgeColors() {
        // New settings use the standard team blue and red for the two point ratios.
        val defaults = Settings()
        assertEquals(
            TeamColorChoice.BLUE.accentArgb,
            defaults.genderRatioBadgeColorArgb(GenderRatio.FOUR_MEN_THREE_WOMEN),
        )
        assertEquals(
            TeamColorChoice.RED.accentArgb,
            defaults.genderRatioBadgeColorArgb(GenderRatio.FOUR_WOMEN_THREE_MEN),
        )

        // Each ratio can independently use the same neutral black option.
        val blackBadges = defaults
            .withGenderRatioBadgeColor(
                GenderRatio.FOUR_MEN_THREE_WOMEN,
                TeamColorChoice.BLACK.accentArgb,
            )
            .withGenderRatioBadgeColor(
                GenderRatio.FOUR_WOMEN_THREE_MEN,
                TeamColorChoice.BLACK.accentArgb,
            )
        assertEquals(
            TeamColorChoice.BLACK.accentArgb,
            blackBadges.genderRatioBadgeColorArgb(GenderRatio.FOUR_MEN_THREE_WOMEN),
        )
        assertEquals(
            TeamColorChoice.BLACK.accentArgb,
            blackBadges.genderRatioBadgeColorArgb(GenderRatio.FOUR_WOMEN_THREE_MEN),
        )
    }

    /**
     * Test basic display details of the various gender ratio rules.
     */
    @Test
    fun genderRatioRuleDisplay() {
        // Gender-ratio values flip between 4M/3W and 4W/3M.
        assertEquals(GenderRatio.FOUR_WOMEN_THREE_MEN, GenderRatio.FOUR_MEN_THREE_WOMEN.flip())
        assertEquals(GenderRatio.FOUR_MEN_THREE_WOMEN, GenderRatio.FOUR_WOMEN_THREE_MEN.flip())

        // They expose compact display text.
        assertEquals("4W/3M", GenderRatio.FOUR_WOMEN_THREE_MEN.displayText)
        assertEquals("4M/3W", GenderRatio.FOUR_MEN_THREE_WOMEN.displayText)

        // The rules each have short display text.
        assertEquals("ABBA", GenderRatioRule.ABBA.displayText)
        assertEquals("4M/3W", GenderRatioRule.FIXED_4M_3W.displayText)
        assertEquals("4W/3M", GenderRatioRule.FIXED_4W_3M.displayText)
        assertEquals("Gen Zone", GenderRatioRule.GEN_ZONE.displayText)
        assertEquals("Offense Decides", GenderRatioRule.OFFENSE_DECIDES.displayText)
        assertEquals("N/A", GenderRatioRule.NA.displayText)

        // ABBA and the two fixed ratio rules display the ratio for each point.
        assertTrue(GenderRatioRule.ABBA.hasDisplayablePointRatio())
        assertTrue(GenderRatioRule.FIXED_4M_3W.hasDisplayablePointRatio())
        assertTrue(GenderRatioRule.FIXED_4W_3M.hasDisplayablePointRatio())
        assertFalse(GenderRatioRule.GEN_ZONE.hasDisplayablePointRatio())
        assertFalse(GenderRatioRule.OFFENSE_DECIDES.hasDisplayablePointRatio())
        assertFalse(GenderRatioRule.NA.hasDisplayablePointRatio())

        // Rules with first-point setup choices advertise that need to the setup screen.
        assertTrue(GenderRatioRule.ABBA.hasStartingPullChoice())
        assertTrue(GenderRatioRule.GEN_ZONE.hasStartingPullChoice())
        assertFalse(GenderRatioRule.OFFENSE_DECIDES.hasStartingPullChoice())
        assertFalse(GenderRatioRule.FIXED_4M_3W.hasStartingPullChoice())
        assertFalse(GenderRatioRule.FIXED_4W_3M.hasStartingPullChoice())
        assertFalse(GenderRatioRule.NA.hasStartingPullChoice())
    }

    /**
     * Test displayed gender ratios for the upcoming point.
     * This applies to ABBA and the two fixed ratio rules.
     */
    @Test
    fun pointGenderRatios() {
        val VC = TeamId.TEAM_ONE
        val ANIMAL = TeamId.TEAM_TWO

        // ABBA starts with the chosen ratio for the first point and then alternates two
        // points at a time: ABBAABBAABB...
        var state = mixedLiveGameState(
            ratioRule = GenderRatioRule.ABBA,
            initialRatio = GenderRatio.FOUR_MEN_THREE_WOMEN,
        )
        assertEquals(1, state.currentPointNumber())
        assertEquals(GenderRatio.FOUR_MEN_THREE_WOMEN, state.currentGenderRatio())
        assertEquals("M2", state.currentGenderRatioBadgeText(showAbbaRatioAsSequence = true))
        assertEquals("4M/3W", state.currentGenderRatioBadgeText(showAbbaRatioAsSequence = false))
        state = state.recordGoal(VC, timestampAt(state, LocalTime.of(11, 1)))
        assertEquals(2, state.currentPointNumber())
        assertEquals(GenderRatio.FOUR_WOMEN_THREE_MEN, state.currentGenderRatio())
        assertEquals("W1", state.currentGenderRatioBadgeText(showAbbaRatioAsSequence = true))
        assertEquals("4W/3M", state.currentGenderRatioBadgeText(showAbbaRatioAsSequence = false))
        state = state.recordGoal(ANIMAL, timestampAt(state, LocalTime.of(11, 2)))
        assertEquals(3, state.currentPointNumber())
        assertEquals(GenderRatio.FOUR_WOMEN_THREE_MEN, state.currentGenderRatio())
        assertEquals("W2", state.currentGenderRatioBadgeText(showAbbaRatioAsSequence = true))
        assertEquals("4W/3M", state.currentGenderRatioBadgeText(showAbbaRatioAsSequence = false))
        state = state.recordGoal(VC, timestampAt(state, LocalTime.of(11, 3)))
        assertEquals(4, state.currentPointNumber())
        assertEquals(GenderRatio.FOUR_MEN_THREE_WOMEN, state.currentGenderRatio())
        assertEquals("M1", state.currentGenderRatioBadgeText(showAbbaRatioAsSequence = true))
        assertEquals("4M/3W", state.currentGenderRatioBadgeText(showAbbaRatioAsSequence = false))

        // Reversing the rule for the first point changes the ratio for subsequent points.
        state = mixedLiveGameState(
            ratioRule = GenderRatioRule.ABBA,
            initialRatio = GenderRatio.FOUR_WOMEN_THREE_MEN,
        )
        assertEquals(GenderRatio.FOUR_WOMEN_THREE_MEN, state.currentGenderRatio())
        assertEquals("W2", state.currentGenderRatioBadgeText(showAbbaRatioAsSequence = true))
        state = state.recordGoal(VC, timestampAt(state, LocalTime.of(11, 1)))
        assertEquals("M1", state.currentGenderRatioBadgeText(showAbbaRatioAsSequence = true))
        state = state.recordGoal(ANIMAL, timestampAt(state, LocalTime.of(11, 2)))
        assertEquals("M2", state.currentGenderRatioBadgeText(showAbbaRatioAsSequence = true))
        state = state.recordGoal(VC, timestampAt(state, LocalTime.of(11, 3)))
        assertEquals("W1", state.currentGenderRatioBadgeText(showAbbaRatioAsSequence = true))

        // The two fixed point rules always show their corresponding ratio.
        val fixedFourMen = mixedLiveGameState(ratioRule = GenderRatioRule.FIXED_4M_3W)
        val fixedFourWomen = mixedLiveGameState(ratioRule = GenderRatioRule.FIXED_4W_3M)
        assertEquals(GenderRatio.FOUR_MEN_THREE_WOMEN, fixedFourMen.currentGenderRatio())
        assertEquals(GenderRatio.FOUR_WOMEN_THREE_MEN, fixedFourWomen.currentGenderRatio())
        assertEquals(
            "4M/3W",
            fixedFourMen.currentGenderRatioBadgeText(showAbbaRatioAsSequence = true),
        )
        assertEquals(
            "4W/3M",
            fixedFourWomen.currentGenderRatioBadgeText(showAbbaRatioAsSequence = true),
        )

        // The other rules don't show a ratio.
        assertNull(mixedLiveGameState(ratioRule = GenderRatioRule.GEN_ZONE).currentGenderRatio())
        assertNull(mixedLiveGameState(ratioRule = GenderRatioRule.NA).currentGenderRatio())
        assertNull(
            mixedLiveGameState(
                ratioRule = GenderRatioRule.OFFENSE_DECIDES,
            ).currentGenderRatio()
        )

        // If the division isn't mixed, then no gender ratio is shown.
        val openDivisionState = standardLiveGameState()
        assertNull(openDivisionState.currentGenderRatio())
        assertThrows(IllegalStateException::class.java) {
            openDivisionState.currentGenderRatioBadgeText(showAbbaRatioAsSequence = true)
        }
    }

    /**
     * Test rules that dictate which team chooses the gender ratio for each point.
     * This applies to Gen Zone and Offense Decides rules.
     */
    @Test
    fun ratioChoosingTeams() {
        val VC = TeamId.TEAM_ONE
        val ANIMAL = TeamId.TEAM_TWO

        // For Offense Decides, whichever team is receiving the pull chooses the ratio.
        var state = mixedLiveGameState(ratioRule = GenderRatioRule.OFFENSE_DECIDES)
        assertEquals(ANIMAL, state.pullingTeam.flip())
        assertEquals(ANIMAL, state.ratioChoosingTeam())
        state = state.recordGoal(VC, timestampAt(state, LocalTime.of(11, 1)))
        assertEquals(ANIMAL, state.pullingTeam.flip())
        assertEquals(ANIMAL, state.ratioChoosingTeam())
        state = state.recordGoal(ANIMAL, timestampAt(state, LocalTime.of(11, 2)))
        assertEquals(VC, state.pullingTeam.flip())
        assertEquals(VC, state.ratioChoosingTeam())
        state = state.recordGoal(ANIMAL, timestampAt(state, LocalTime.of(11, 3)))
        assertEquals(VC, state.pullingTeam.flip())
        assertEquals(VC, state.ratioChoosingTeam())
        state = state.recordGoal(VC, timestampAt(state, LocalTime.of(11, 4)))
        assertEquals(ANIMAL, state.pullingTeam.flip())
        assertEquals(ANIMAL, state.ratioChoosingTeam())

        // For Gen Zone, the team at a particular end zone chooses the gender ratio.
        // This means the choosing team alternates each point regardless of who scores.
        state = mixedLiveGameState(
            ratioRule = GenderRatioRule.GEN_ZONE,
            firstHalfGenZone = FieldEnd.FAR,
        )
        assertEquals(VC, state.teamDefendingEnd(FieldEnd.FAR))
        assertEquals(VC, state.ratioChoosingTeam())
        state = state.recordGoal(VC, timestampAt(state, LocalTime.of(11, 1)))
        assertEquals(ANIMAL, state.teamDefendingEnd(FieldEnd.FAR))
        assertEquals(ANIMAL, state.ratioChoosingTeam())
        state = state.recordGoal(ANIMAL, timestampAt(state, LocalTime.of(11, 2)))
        assertEquals(VC, state.teamDefendingEnd(FieldEnd.FAR))
        assertEquals(VC, state.ratioChoosingTeam())
        state = state.recordGoal(ANIMAL, timestampAt(state, LocalTime.of(11, 3)))
        assertEquals(ANIMAL, state.teamDefendingEnd(FieldEnd.FAR))
        assertEquals(ANIMAL, state.ratioChoosingTeam())
        state = state.recordGoal(VC, timestampAt(state, LocalTime.of(11, 4)))
        assertEquals(VC, state.teamDefendingEnd(FieldEnd.FAR))
        assertEquals(VC, state.ratioChoosingTeam())

        // After halftime, the gen zone switches (typically).
        state = state.recordGoal(VC, timestampAt(state, LocalTime.of(11, 5)))
        state = state.copy(halftimeTaken = true)
        assertEquals(ANIMAL, state.teamDefendingEnd(FieldEnd.FAR))
        assertEquals(VC, state.ratioChoosingTeam())
        state = state.recordGoal(ANIMAL, timestampAt(state, LocalTime.of(11, 6)))
        assertEquals(VC, state.teamDefendingEnd(FieldEnd.FAR))
        assertEquals(ANIMAL, state.ratioChoosingTeam())

        // However, it is an option to not switch the gen zone at halftime.
        state = state.copy(
            rules = state.rules.copy(switchGenZoneAtHalftime = false),
        )
        assertEquals(VC, state.teamDefendingEnd(FieldEnd.FAR))
        assertEquals(VC, state.ratioChoosingTeam())

        // The other rules don't have a team to choose a ratio.
        assertNull(mixedLiveGameState(ratioRule = GenderRatioRule.ABBA).ratioChoosingTeam())
        assertNull(mixedLiveGameState(ratioRule = GenderRatioRule.FIXED_4M_3W).ratioChoosingTeam())
        assertNull(mixedLiveGameState(ratioRule = GenderRatioRule.FIXED_4W_3M).ratioChoosingTeam())
        assertNull(mixedLiveGameState(ratioRule = GenderRatioRule.NA).ratioChoosingTeam())

        // If the division isn't mixed, then neither team chooses a ratio.
        val openDivisionState = standardLiveGameState()
        assertNull(openDivisionState.ratioChoosingTeam())
    }

    /**
     * Test assessing a majority-pull violation.
     * It works basically the same as offsides.  Indeed to call it on the app, we use the
     * offsides button and include an option to switch it to a majority pull violation.
     */
    @Test
    fun majorityPullViolations() {
        val VC = TeamId.TEAM_ONE
        val ANIMAL = TeamId.TEAM_TWO

        // A majority-pull preview uses the majority-pull title without mutating the current state.
        var state = mixedLiveGameState()
        val preview = state.previewPullViolation(VC, PullViolationType.MAJORITY_PULL)!!.event
        assertEquals("Majority pull violation", preview.formatPopupTitle())

        // Recording a majority-pull violation uses its own event labels and the shared
        // pull-violation ladder.
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
        assertEquals("Undo Majority pull violation on Viscous Coupling", state.undoEntry?.label)
        assertEquals(EventLogType.MAJORITY_PULL, state.eventLog.last().type)
        assertEquals(
            "12:00  Majority pull violation on Viscous Coupling",
            state.formatEventLogLine(state.eventLog.last()),
        )
        assertEquals(
            "This is Viscous Coupling's first pull violation.\n\n" +
                "Animal starts at the brick mark.\n\n" +
                "The disc is live -- no defensive check is required.",
            majorityPullResult.message(),
        )
        assertEquals(
            "Animal starts at the brick mark.",
            (majorityPullResult.event as GameEvent.PullViolationRecorded)
                .formatBriefMessage().plainText,
        )

        // Further pulling-team violations in the same sequence are ignored.
        assertNull(state.assessPullViolation(VC).event)
        assertEquals(
            state,
            state.recordMajorityPullViolation(timestampAt(state, LocalTime.of(12, 1))),
        )
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
        val animalPullingState = mixedLiveGameState(pullingTeam = ANIMAL)
        val animalPreviewResult = animalPullingState.previewPullViolation(
            ANIMAL,
            PullViolationType.MAJORITY_PULL,
        )!!
        val animalPreview = animalPreviewResult.event
        assertEquals(1, animalPreview.state.teamTwo.majorityPullViolations)
        val animalMajorityPullResult = animalPullingState.assessPullViolation(
            ANIMAL,
            timestampAt(animalPullingState, LocalTime.of(12, 3)),
            PullViolationType.MAJORITY_PULL,
        )
        assertEquals(1, animalMajorityPullResult.state.teamTwo.majorityPullViolations)

        // Direct helper calls can arrive after stale UI or test setup states; normal UI disables
        // these paths.
        val openDivisionState = standardLiveGameState()
        assertEquals(
            openDivisionState,
            openDivisionState.recordMajorityPullViolation(timestampAt(state, LocalTime.of(12, 4))),
        )
        val skippedPullState = mixedLiveGameState().copy(pullSkippedForCurrentPoint = true)
        assertEquals(
            skippedPullState,
            skippedPullState.recordMajorityPullViolation(timestampAt(state, LocalTime.of(12, 5))),
        )
    }

    /**
     * Test manual majority pull adjustments.
     * When doing mixed division, these are available to be adjusted alongside offsides and false
     * starts.
     */
    @Test
    fun majorityPullAdjustments() {
        // Manually adjust majority pull violations and verify values are clamped and undo-backed.
        var state = mixedLiveGameState()
        state = state.adjustPullViolations(
            teamOneOffsides = -1,
            teamOneFalseStarts = 2,
            teamOneMajorityPulls = 3,
            teamOneTimeViolations = state.teamOne.timeViolations,
            teamTwoOffsides = 3,
            teamTwoFalseStarts = -4,
            teamTwoMajorityPulls = -2,
            teamTwoTimeViolations = state.teamTwo.timeViolations,
            now = timestampAt(state, LocalTime.of(12, 20)),
        )
        assertEquals(0, state.teamOne.offsides)
        assertEquals(2, state.teamOne.falseStarts)
        assertEquals(3, state.teamOne.majorityPullViolations)
        assertEquals(3, state.teamTwo.offsides)
        assertEquals(0, state.teamTwo.falseStarts)
        assertEquals(0, state.teamTwo.majorityPullViolations)
        assertEquals("Undo Pull violation adjustment", state.undoEntry?.label)

        // The event log includes the specific majority pull adjustment.
        assertEquals(
            "12:20  Adjusted Viscous Coupling majority pull violations +3",
            state.formatEventLogLine(state.eventLog[1]),
        )

        // Majority pull violations count as part of the total pull violations.
        assertEquals(5, state.teamOne.pullViolationCount())
        assertEquals(3, state.teamTwo.pullViolationCount())
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
        ratioRule: GenderRatioRule = GenderRatioRule.ABBA,
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
                    switchGenZoneAtHalftime = switchGenZoneAtHalftime,
                ),
                pullingTeam = pullingTeam,
            ).copy(
                division = GameDivision.MIXED,
                initialGenderRatio = initialRatio,
                firstHalfGenZone = firstHalfGenZone,
            )
        )
    }
}
