package rmjarvis.ultiobserver

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for the live field's compact game-rules quick reference.
 */
class TestGameRulesDialog : GameDomainTestFixtures() {
    /**
     * Test how the game-rules quick reference summarizes setup-time rule choices.
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
                softCapMinutes = 90,
                useHardCap = true,
                hardCapMinutes = 105,
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
                "Halftime" to "7 min",
                "Timeouts" to "2/half",
                "Time between points" to "60 sec",
                "Timeout duration" to "70 sec",
                "Gender ratio" to "ABBA",
            ),
            fullMixedState.gameRulesDialogRows().testDisplayPairs(),
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
                timeBetweenPointsSeconds = 50,
                timeoutSeconds = 80,
            ),
        ).copy(division = GameDivision.OPEN)
        assertEquals(
            listOf(
                "Game to" to "15",
                "Half at" to "8",
                "Start time" to "10:00 AM",
                "Halftime" to "7 min",
                "Timeouts" to "1/half",
                "Time between points" to "50 sec",
                "Timeout duration" to "80 sec",
            ),
            openNoCapState.gameRulesDialogRows().testDisplayPairs(),
        )

        // Normal Gen Zone setup switches at half and can use the compact rule name alone.
        val genZoneState = fullMixedState.copy(
            rules = fullMixedState.rules.copy(
                genderRatioRule = GenderRatioRule.GEN_ZONE,
                switchGenZoneAtHalftime = true,
            ),
        )
        assertEquals(
            "Gender ratio" to "Gen Zone",
            genZoneState.gameRulesDialogRows().testDisplayPairs().last(),
        )

        // The unusual Gen Zone setup that does not switch at half calls that out explicitly.
        assertEquals(
            "Gender ratio" to "Gen Zone, no switch at half",
            genZoneState.copy(
                rules = genZoneState.rules.copy(switchGenZoneAtHalftime = false),
            ).gameRulesDialogRows().testDisplayPairs().last(),
        )

        // Enabled water breaks appear as water-break rows, while None is omitted above.
        assertEquals(
            "Water breaks" to "3 min",
            openNoCapState.copy(
                rules = openNoCapState.rules.copy(
                    waterBreakMode = WaterBreakMode.MANUAL,
                ),
            ).gameRulesDialogRows().testDisplayPairs().last(),
        )
        assertEquals(
            "Water breaks" to "4/12, 3 min",
            openNoCapState.copy(
                rules = openNoCapState.rules.copy(
                    gameTo = 15,
                    waterBreakMode = WaterBreakMode.AUTOMATIC,
                ),
            ).gameRulesDialogRows().testDisplayPairs().last(),
        )
    }

    /**
     * Test how the game-rules quick reference displays live targets changed by caps.
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
                softCapMinutes = 90,
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
                "Halftime" to "7 min",
                "Timeouts" to "2/half + floater",
                "Time between points" to "60 sec",
                "Timeout duration" to "70 sec",
                "Gender ratio" to "Gen Zone, no switch at half",
            ),
            capAdjustedState.gameRulesDialogRows().testDisplayPairs(),
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
            unchangedExplicitState.gameRulesDialogRows().testDisplayPairs().take(2),
        )
    }
}

/// Return the visible label/value pairs for game-rules row assertions.
private fun List<GameRulesDialogRow>.testDisplayPairs(): List<Pair<String, String>> {
    return map { row -> row.label to row.value }
}
