package rmjarvis.ultiobserver

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for the live field's rules reference.
 */
class TestRulesReference : GameDomainTestFixtures() {
    /**
     * Test how the rules reference summarizes setup-time rule choices.
     */
    @Test
    fun setupRuleRows() {
        // A mixed game with all caps enabled shows every setup rule in dialog order, with cap
        // rows converted from start-relative rules into real clock times.
        val fullMixedState = standardLiveGameState(
            startTime = LocalTime.of(10, 0),
            rules = GameRules(
                gameTo = 15,
                halftimeMinutes = 7,
                useHalfCap = true,
                halfCapMinutes = 45,
                useSoftCap = true,
                nominalSoftCapMinutes = 90,
                useHardCap = true,
                nominalHardCapMinutes = 105,
                timeoutsPerHalf = 2,
                genderRatioRule = GenderRatioRule.ABBA,
            ),
        ).copy(division = GameDivision.MIXED)
        assertEquals(
            listOf(
                "Game to" to "15",
                "Half at" to "8",
                "Start time" to "10:00 AM",
                "Half cap" to "10:45 AM",
                "Soft cap" to "11:30 AM",
                "Hard cap" to "11:45 AM",
                "Timeouts" to "2/half",
                "Gender ratio" to "ABBA",
                "Time between points" to "60 sec",
                "Timeout duration" to "70 sec",
                "Halftime" to "7 min",
            ),
            fullMixedState.rulesReferenceItems().testDisplayPairs(),
        )

        // Disabled caps and non-mixed gender rules are omitted rather than shown as empty or
        // disabled-looking rows.
        val openNoCapState = standardLiveGameState(
            startTime = LocalTime.of(10, 0),
            rules = GameRules(
                gameTo = 15,
                halftimeMinutes = 7,
                useHalfCap = false,
                useSoftCap = false,
                useHardCap = false,
                timeoutsPerHalf = 1,
                hasFloaterTimeout = false,
                nominalTimeBetweenPointsSeconds = 50,
                timeoutSeconds = 80,
            ),
        ).copy(division = GameDivision.OPEN)
        assertEquals(
            listOf(
                "Game to" to "15",
                "Half at" to "8",
                "Start time" to "10:00 AM",
                "Timeouts" to "1/half",
                "Time between points" to "50 sec",
                "Timeout duration" to "80 sec",
                "Halftime" to "7 min",
            ),
            openNoCapState.rulesReferenceItems().testDisplayPairs(),
        )

        // Normal Gen Zone setup switches at half and can use the compact rule name alone.
        val genZoneState = fullMixedState.copy(
            rules = fullMixedState.rules.copy(
                genderRatioRule = GenderRatioRule.GEN_ZONE,
                switchGenZoneAtHalftime = true,
            ),
        )
        assertEquals(
            "Gen Zone",
            genZoneState.rulesReferenceItems().testDisplayMap().getValue("Gender ratio"),
        )

        // The unusual Gen Zone setup that does not switch at half calls that out explicitly.
        assertEquals(
            "Gen Zone, no switch at half",
            genZoneState.copy(
                rules = genZoneState.rules.copy(switchGenZoneAtHalftime = false),
            ).rulesReferenceItems().testDisplayMap().getValue("Gender ratio"),
        )

        // Enabled heat/water behavior is shown, including custom configurations.
        assertEquals(
            "Level 0",
            openNoCapState.copy(
                rules = openNoCapState.rules.copy(
                    heatLevel = HeatLevel.LEVEL_0,
                ),
            ).rulesReferenceItems().testDisplayMap().getValue("Heat level"),
        )
        assertEquals(
            "Level 1",
            openNoCapState.copy(
                rules = openNoCapState.rules.copy(
                    gameTo = 15,
                    heatLevel = HeatLevel.LEVEL_1,
                ),
            ).rulesReferenceItems().testDisplayMap().getValue("Heat level"),
        )

        // When using AQI guidelines, it displays as AQI level rather than Heat level.
        assertEquals(
            "Level 1",
            openNoCapState.copy(
                rules = openNoCapState.rules.copy(
                    useAirQualityGuidelines = true,
                    heatLevel = HeatLevel.LEVEL_1,
                    waterBreakMinutes = 4,
                ),
            ).rulesReferenceItems().testDisplayMap().getValue("AQI level"),
        )

        // Level 2 shows only effective cap times and connects affected values through color.
        val levelTwoRows = fullMixedState.copy(
            rules = fullMixedState.rules.withHeatLevel(HeatLevel.LEVEL_2),
        ).rulesReferenceItems()
        assertEquals(
            listOf(
                "Soft cap" to "11:10 AM",
                "Hard cap" to "11:30 AM",
                "Heat level" to "Level 2",
                "Time between points" to "120 sec",
            ),
            levelTwoRows
                .filter(RulesReferenceItem::heatAdjusted)
                .testDisplayPairs(),
        )

        // A cap already shorter than the Level 2 maximum is not marked as heat-adjusted.
        val shorterHardCapRows = fullMixedState.copy(
            rules = fullMixedState.rules.copy(
                nominalHardCapMinutes = 80,
            ).withHeatLevel(HeatLevel.LEVEL_2),
        ).rulesReferenceItems()
        assertEquals(
            listOf("Soft cap", "Heat level", "Time between points"),
            shorterHardCapRows
                .filter(RulesReferenceItem::heatAdjusted)
                .map(RulesReferenceItem::label),
        )

        // If caps were disabled, level 2 enables them and marks them as heat-adjusted.
        val heatEnabledCapRows = openNoCapState.copy(
            rules = openNoCapState.rules.withHeatLevel(HeatLevel.LEVEL_2),
        ).rulesReferenceItems()
        assertEquals(
            listOf("Soft cap", "Hard cap", "Heat level", "Time between points"),
            heatEnabledCapRows
                .filter(RulesReferenceItem::heatAdjusted)
                .map(RulesReferenceItem::label),
        )
    }

    /**
     * Test how the rules reference displays live targets changed by caps.
     */
    @Test
    fun liveTargetRows() {
        // A half cap or soft cap changes the live targets, so the dialog shows both the current
        // target and the original setup target.
        val capAdjustedState = standardLiveGameState(
            startTime = LocalTime.of(10, 0),
            rules = GameRules(
                gameTo = 15,
                halftimeMinutes = 7,
                useHalfCap = true,
                halfCapMinutes = 45,
                useSoftCap = true,
                nominalSoftCapMinutes = 90,
                useHardCap = false,
                timeoutsPerHalf = 2,
                hasFloaterTimeout = true,
                genderRatioRule = GenderRatioRule.GEN_ZONE,
                switchGenZoneAtHalftime = false,
            ),
        ).copy(
            division = GameDivision.MIXED,
            winningScore = 13,
            halftimeTargetScore = 6,
            halftimeTaken = true,
            halfCapApplied = true,
            softCapApplied = true,
        )
        assertEquals(
            listOf(
                "Game to" to "13 (was 15)",
                "Half at" to "6 (was 8)",
                "Start time" to "10:00 AM",
                "Half cap" to "10:45 AM",
                "Soft cap" to "11:30 AM",
                "Timeouts" to "2/half + floater",
                "Gender ratio" to "Gen Zone, no switch at half",
                "Time between points" to "60 sec",
                "Timeout duration" to "70 sec",
                "Halftime" to "7 min",
            ),
            capAdjustedState.rulesReferenceItems().testDisplayPairs(),
        )

        // Explicit live targets that still equal the setup targets do not get noisy "was" text.
        val unchangedExplicitState = standardLiveGameState(
            startTime = LocalTime.of(10, 0),
            rules = GameRules(gameTo = 15),
        ).copy(
            halftimeTargetScore = 8,
            winningScore = 15,
        )
        assertEquals(
            listOf(
                "Game to" to "15",
                "Half at" to "8",
            ),
            unchangedExplicitState.rulesReferenceItems().testDisplayPairs().take(2),
        )
    }
}

/// Return the visible label/value pairs for rules-reference assertions.
private fun List<RulesReferenceItem>.testDisplayPairs(): List<Pair<String, String>> {
    return map { item -> item.label to item.value }
}

/// Return the visible rules-reference values keyed by label.
private fun List<RulesReferenceItem>.testDisplayMap(): Map<String, String> {
    return associate { item -> item.label to item.value }
}
