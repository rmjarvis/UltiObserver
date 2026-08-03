package rmjarvis.ultiobserver

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/// Tests for live-game More actions dialogs and correction flows.
@RunWith(AndroidJUnit4::class)
class TestMoreActionsUi : MainActivityUiTestFixtures() {
    /**
     * Test the live-only Level 3 action and its suspension record.
     */
    @Test
    fun heatLevelThreeSuspendsGame() {
        setRuleGuidanceMode(RuleGuidanceMode.FULL)

        startLiveGameProgrammatically()
        openMoreActionsDialog()
        composeRule.onNodeWithText("Set heat/AQI level").performClick()
        waitForText("Set heat level")
        composeRule.onAllNodesWithText("Close").assertCountEquals(0)
        composeRule.onNodeWithTag("heat-level-LEVEL_3").performClick()
        composeRule.onNodeWithTag("set-heat-level-confirm").performClick()

        waitForText("Game suspended")
        dismissDialog(text = "OK")
        waitForText("Game summary")
        composeRule.onNodeWithText("Undo Heat level 3 — game suspended").assertExists()
        composeRule.onNodeWithText("Event log").performClick()
        waitForText("Heat level 3 — game suspended", substring = true)
    }

    /**
     * Test the less-common live-game actions behind More actions.
     * The goal is to catch broken dialogs, buttons, and return paths for observer-accessible tools.
     */
    @Test
    fun moreActionsPathways() {
        setRuleGuidanceMode(RuleGuidanceMode.FULL)
        setAutomaticallyLockLivePoint(true)

        // Start from a live game so More actions exposes the observer-facing correction tools.
        startLiveGameProgrammatically()

        // Manual correction dialogs should open and return to More actions cleanly.
        openMoreActionsDialog()
        dismissDialog(text = "Close")
        composeRule.onAllNodesWithText("Update game setup").assertCountEquals(0)
        openMoreActionsDialog()
        selectMoreActionsCategory("Game details")
        composeRule.onNodeWithText("Event log").performClick()
        waitForText("Event log")
        waitForText("No events logged yet.")
        dismissDialog(text = "OK")
        openMoreActionsDialog()
        selectMoreActionsCategory("Game details")
        composeRule.onNodeWithText("Game summary").performClick()
        waitForText("Game summary")
        tapTopBarBack()
        assertLiveScreen()
        openMoreActionsDialog()
        selectMoreActionsCategory("Game details")
        composeRule.onNodeWithText("Game summary").performClick()
        waitForText("Game summary")
        tapTopBarHome()
        waitForText("Current game")
        composeRule.onNodeWithTag("current-game").performClick()
        assertLiveScreen()

        // The unlock slider should ignore interrupted, short, and wrong-start swipes first.
        startPointWithFailedSwipeThenUnlock()

        // Once the observer starts the opening point, update setup mode edits the current game.
        openMoreActionsDialog()
        composeRule.onNodeWithText("Update game setup").performClick()
        waitForText("Done")
        replaceSetupTeamName("Team 1", "Updated Team")
        composeRule.onNodeWithText("Done").performClick()
        waitForText("Updated Team")

        // Update setup mode can cancel a draft setup change without changing the current game.
        openMoreActionsDialog()
        composeRule.onNodeWithText("Update game setup").performClick()
        waitForText("Done")
        replaceSetupTeamName("Team 1", "Canceled Team")
        composeRule.onNodeWithText("Cancel").performClick()
        waitForText("Updated Team")
        composeRule.onAllNodesWithText("Canceled Team").assertCountEquals(0)

        // Update setup mode can also return directly Home, preserving the current game.
        openMoreActionsDialog()
        composeRule.onNodeWithText("Update game setup").performClick()
        waitForText("Done")
        tapTopBarHome()
        waitForText("Current game")
        composeRule.onNodeWithTag("current-game").performClick()
        assertLiveScreen()

        // Run through all the adjust pathways, but just cancel them for now.
        openMoreActionsDialog()
        openMoreActionsDialogAndCancel("Corrections", "Adjust score")
        openMoreActionsDialogAndCancel("Corrections", "Adjust timeouts")
        openMoreActionsDialogAndCancel("Corrections", "Adjust cards / techs")
        openMoreActionsDialogAndCancel("Corrections", "Adjust pull violations")
        dismissDialog(text = "Close")
        assertLiveScreen()

        // Manual correction dialogs should also apply their visible values.
        applyScoreAdjustment()
        applyTimeoutAdjustment()
        applyPullViolationAdjustment()
        applyCardTechAdjustment()

        // Orientation controls should update state without breaking the live screen.
        openMoreActionsDialog()
        selectMoreActionsCategory("Field and pull controls")
        composeRule.onNodeWithText("Flip field display").performClick()
        waitForText("Undo Flip field display")
        assertLiveScreen()

        // Changing pull prompts can be canceled before applying a new prompt target.
        openMoreActionsDialog()
        openMoreActionsDialogAndCancel("Field and pull controls", "Change pull prompts")

        // Changing pull prompts can also apply a new target immediately.
        composeRule.onNodeWithText("Change pull prompts").performClick()
        waitForText("Change pull prompts")
        composeRule.onNodeWithTag("more-actions-pull-prompts-BOTH").performClick()
        composeRule.onNodeWithText("Set").performClick()
        waitForText("Undo Change pull prompts")
        assertEquals(PullPromptTarget.BOTH, accessCurrentGameState().pullPromptTarget)
        assertLiveScreen()

        // Heat level changes are directly available without reopening full setup.
        openMoreActionsDialog()
        openMoreActionsDialogAndCancel("Setup changes", "Set heat/AQI level")
        composeRule.onNodeWithText("Set heat/AQI level").performClick()
        waitForText("Set heat level")
        composeRule.onNodeWithTag("air-quality-guidelines").performScrollTo().performClick()
        waitForText("Set AQI level")
        composeRule.onNodeWithTag("heat-level-LEVEL_2").performClick()
        composeRule.onNodeWithText("Water break minutes").performTextReplacement("6")
        waitForText(
            "One 6-minute water break per half",
            substring = true,
        )
        composeRule.onNodeWithTag("set-heat-level-confirm").performClick()
        waitForText("Undo AQI level 2")
        assertTrue(accessCurrentGameState().rules.useAirQualityGuidelines)
        assertEquals(6, accessCurrentGameState().rules.waterBreakMinutes)
        assertLiveScreen()

        // Reselecting the active level preserves its customized duration. If the minutes field is
        // then left blank, the visible guidance and confirmed rules keep that existing duration.
        openMoreActionsDialog()
        composeRule.onNodeWithText("Set heat/AQI level").performClick()
        waitForText("Set AQI level")
        composeRule.onNodeWithTag("heat-level-LEVEL_2").performClick()
        composeRule.onNodeWithText("Water break minutes").assertTextContains("6")
        composeRule.onNodeWithText("Water break minutes").performTextReplacement("")
        waitForText(
            "One 6-minute water break per half",
            substring = true,
        )
        composeRule.onNodeWithTag("set-heat-level-confirm").performClick()
        assertTrue(accessCurrentGameState().rules.useAirQualityGuidelines)
        assertEquals(6, accessCurrentGameState().rules.waterBreakMinutes)
        assertLiveScreen()

        openMoreActionsDialog()
        selectMoreActionsCategory("Field and pull controls")
        composeRule.onNodeWithText("Swap pulling team").performClick()
        assertLiveScreen()

        // Manual halftime is only available between points, so score the opening point first.
        updateCurrentStateProgrammatically {
            recordGoalFromCurrentState(TeamId.TEAM_ONE, System.currentTimeMillis())
        }

        // Manual halftime should be reachable and leave a visible result.
        openMoreActionsDialog()
        selectMoreActionsCategory("Manual game transitions")
        clickMoreActionsItem("Start halftime")
        waitForText("Halftime")
        waitForText("Announce halftime.")
        // Back dismissal and OK are equivalent acknowledgements for this prompt.
        dismissDialog(text = "OK")
        assertLiveScreen()

        // Once halftime has been taken, timeout adjustment includes first-half rows too.
        val teamOneName = accessCurrentGameState().teamOne.name
        val teamTwoName = accessCurrentGameState().teamTwo.name
        openMoreActionsDialog()
        selectMoreActionsCategory("Corrections")
        clickMoreActionsItem("Adjust timeouts")
        waitForText("Adjust the number of timeouts used by each team in the first half.")
        listOf(
            "timeout-current-team-one-increment",
            "timeout-current-team-two-increment",
            "timeout-first-half-team-one-increment",
            "timeout-first-half-team-two-increment",
        ).forEach { tag ->
            composeRule.onNodeWithTag(tag).performScrollTo().performClick()
        }
        composeRule.onAllNodesWithText("$teamOneName: 1").assertCountEquals(2)
        composeRule.onAllNodesWithText("$teamTwoName: 1").assertCountEquals(2)
        listOf(
            "timeout-current-team-one-decrement",
            "timeout-current-team-two-decrement",
            "timeout-first-half-team-one-decrement",
            "timeout-first-half-team-two-decrement",
        ).forEach { tag ->
            composeRule.onNodeWithTag(tag).performScrollTo().performClick()
        }
        composeRule.onAllNodesWithText("$teamOneName: 0").assertCountEquals(2)
        composeRule.onAllNodesWithText("$teamTwoName: 0").assertCountEquals(2)
        composeRule.onNodeWithTag("timeout-first-half-team-one-increment").performClick()
        composeRule.onNodeWithText("Set").performClick()
        waitForText("Undo Timeout adjustment")

        // Timeout adjustment copy uses singular text when only one timeout is available.
        startLiveGameProgrammatically(
            newSetupGameState(now = System.currentTimeMillis()).copy(
                rules = GameRules(timeoutsPerHalf = 1),
            )
        )
        openMoreActionsDialog()
        selectMoreActionsCategory("Corrections")
        clickMoreActionsItem("Adjust timeouts")
        waitForText("Team 1 is allowed to use 1 timeout")
        dismissDialog(text = "Cancel")
    }

    /**
     * Test the fields shown by the pull-violation adjustment dialog.
     */
    @Test
    fun pullViolationAdjustmentFields() {
        // Standard non-mixed games show the common rows for both teams, but not majority pull.
        startLiveGameProgrammatically()
        openMoreActionsDialog()
        selectMoreActionsCategory("Corrections")
        clickMoreActionsItem("Adjust pull violations")
        waitForTag("adjust-pull-violations-confirm")
        assertPullViolationDialogFieldCount("Offsides", 2)
        assertPullViolationDialogFieldCount("False starts", 2)
        assertPullViolationDialogFieldCount("Time violations", 2)
        assertPullViolationDialogFieldCount("Majority pull", 0)
        dismissDialog(text = "Cancel")
        dismissDialog(text = "Close")
        assertLiveScreen()

        // Mixed games show one majority-pull row per team.
        startLiveGameProgrammatically(
            newSetupGameState(now = System.currentTimeMillis()).copy(
                division = GameDivision.MIXED,
            )
        )
        openMoreActionsDialog()
        selectMoreActionsCategory("Corrections")
        clickMoreActionsItem("Adjust pull violations")
        waitForTag("adjust-pull-violations-confirm")
        assertPullViolationDialogFieldCount("Offsides", 2)
        assertPullViolationDialogFieldCount("False starts", 2)
        assertPullViolationDialogFieldCount("Time violations", 2)
        assertPullViolationDialogFieldCount("Majority pull", 2)
        val rowTags = listOf(
            "team-one-offsides",
            "team-one-false-starts",
            "team-one-majority-pull",
            "team-one-time-violations",
            "team-two-offsides",
            "team-two-false-starts",
            "team-two-majority-pull",
            "team-two-time-violations",
        )
        rowTags.forEach { rowTag ->
            composeRule.onNodeWithTag("pull-violation-$rowTag-increment")
                .performScrollTo()
                .performClick()
        }
        listOf("Offsides", "False starts", "Majority pull", "Time violations").forEach { label ->
            composeRule.onAllNodesWithText("$label: 1").assertCountEquals(2)
        }
        rowTags.forEach { rowTag ->
            composeRule.onNodeWithTag("pull-violation-$rowTag-decrement")
                .performScrollTo()
                .performClick()
        }
        listOf("Offsides", "False starts", "Majority pull", "Time violations").forEach { label ->
            composeRule.onAllNodesWithText("$label: 0").assertCountEquals(2)
        }
        dismissDialog(text = "Cancel")
    }

    /// Assert how many labeled correction rows are visible inside the pull-violation dialog body.
    private fun assertPullViolationDialogFieldCount(label: String, expectedCount: Int) {
        composeRule.onAllNodes(
            hasText(label, substring = true) and
                hasAnyAncestor(hasTestTag("adjust-pull-violations-content"))
        ).assertCountEquals(expectedCount)
    }

}
