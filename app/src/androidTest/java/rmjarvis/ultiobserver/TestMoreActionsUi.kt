package rmjarvis.ultiobserver

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
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
        startLiveGameProgrammatically()
        openMoreActionsDialog()
        composeRule.onNodeWithText("Set heat level").performClick()
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
        // Start from a live game so More actions exposes the observer-facing correction tools.
        startLiveGameProgrammatically()

        // Manual correction dialogs should open and return to More actions cleanly.
        openMoreActionsDialog()
        dismissDialog(text = "Close")
        composeRule.onAllNodesWithText("Update game setup").assertCountEquals(0)
        openMoreActionsDialog()
        composeRule.onNodeWithText("Event log").performClick()
        waitForText("Event log")
        waitForText("No events logged yet.")
        dismissDialog(text = "OK")
        openMoreActionsDialog()
        composeRule.onNodeWithText("Game summary").performClick()
        waitForText("Game summary")
        tapTopBarBack()
        assertLiveScreen()
        openMoreActionsDialog()
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
        openMoreActionsDialogAndCancel("Adjust score")
        openMoreActionsDialogAndCancel("Adjust timeouts")
        openMoreActionsDialogAndCancel("Adjust cards / techs")
        openMoreActionsDialogAndCancel("Adjust pull violations")

        // Manual correction dialogs should also apply their visible values.
        applyScoreAdjustment()
        applyTimeoutAdjustment()
        applyPullViolationAdjustment()
        applyCardTechAdjustment()

        // Orientation controls should update state without breaking the live screen.
        openMoreActionsDialog()
        composeRule.onNodeWithText("Flip field display").performClick()
        waitForText("Undo Flip field display")
        assertLiveScreen()

        // Changing pull prompts can be canceled before applying a new prompt target.
        openMoreActionsDialog()
        openMoreActionsDialogAndCancel("Change pull prompts")

        // Changing pull prompts can also apply a new target immediately.
        openMoreActionsDialog()
        composeRule.onNodeWithText("Change pull prompts").performClick()
        waitForText("Change pull prompts")
        composeRule.onNodeWithTag("more-actions-pull-prompts-BOTH").performClick()
        composeRule.onNodeWithText("Set").performClick()
        waitForText("Undo Change pull prompts")
        assertLiveScreen()

        // Heat level changes are directly available without reopening full setup.
        openMoreActionsDialog()
        openMoreActionsDialogAndCancel("Set heat level")
        openMoreActionsDialog()
        composeRule.onNodeWithText("Set heat level").performClick()
        composeRule.onNodeWithTag("heat-level-LEVEL_2").performClick()
        waitForText(
            "One 4-minute water break per half. Add 60 seconds between points. " +
                "Adjust soft/hard caps."
        )
        composeRule.onNodeWithTag("set-heat-level-confirm").performClick()
        waitForText("Undo Heat level 2")
        assertLiveScreen()

        openMoreActionsDialog()
        composeRule.onNodeWithText("Swap pulling team").performClick()
        assertLiveScreen()

        // Manual halftime is only available between points, so score the opening point first.
        updateCurrentStateProgrammatically {
            recordGoalFromCurrentState(TeamId.TEAM_ONE, System.currentTimeMillis())
        }

        // Manual halftime should be reachable and leave a visible result.
        openMoreActionsDialog()
        composeRule.onNodeWithText("Start halftime").performScrollTo().performClick()
        waitForText("Halftime")
        waitForText("Announce halftime.")
        // Back dismissal and OK are equivalent acknowledgements for this prompt.
        dismissDialog(text = "OK")
        assertLiveScreen()

        // Once halftime has been taken, timeout adjustment includes first-half rows too.
        openMoreActionsDialog()
        composeRule.onNodeWithText("Adjust timeouts").performScrollTo().performClick()
        waitForText("Adjust the number of timeouts used by each team in the first half.")
        composeRule.onAllNodesWithText("+1")[2].performClick()
        composeRule.onAllNodesWithText("+1")[3].performClick()
        composeRule.onAllNodesWithText("-1")[2].performClick()
        composeRule.onAllNodesWithText("-1")[3].performClick()
        composeRule.onAllNodesWithText("+1")[2].performClick()
        composeRule.onNodeWithText("Set").performClick()
        waitForText("Undo Timeout adjustment")

        // Timeout adjustment copy uses singular text when only one timeout is available.
        startLiveGameProgrammatically(
            newSetupGameState(now = System.currentTimeMillis()).copy(
                rules = GameRules(timeoutsPerHalf = 1),
            )
        )
        openMoreActionsDialog()
        composeRule.onNodeWithText("Adjust timeouts").performScrollTo().performClick()
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
        composeRule.onNodeWithText("Adjust pull violations").performScrollTo().performClick()
        waitForTag("adjust-pull-violations-confirm")
        assertPullViolationDialogFieldCount("Offsides", 2)
        assertPullViolationDialogFieldCount("False starts", 2)
        assertPullViolationDialogFieldCount("Time violations", 2)
        assertPullViolationDialogFieldCount("Majority pull", 0)
        dismissDialog(text = "Cancel")

        // Mixed games show one majority-pull row per team.
        startLiveGameProgrammatically(
            newSetupGameState(now = System.currentTimeMillis()).copy(
                division = GameDivision.MIXED,
            )
        )
        openMoreActionsDialog()
        composeRule.onNodeWithText("Adjust pull violations").performScrollTo().performClick()
        waitForTag("adjust-pull-violations-confirm")
        assertPullViolationDialogFieldCount("Offsides", 2)
        assertPullViolationDialogFieldCount("False starts", 2)
        assertPullViolationDialogFieldCount("Time violations", 2)
        assertPullViolationDialogFieldCount("Majority pull", 2)
        repeat(8) { index ->
            composeRule.onAllNodesWithText("+1")[index].performClick()
        }
        repeat(8) { index ->
            composeRule.onAllNodesWithText("-1")[index].performClick()
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
