package rmjarvis.ultiobserver

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/// Tests for live-game More actions dialogs and correction flows.
@RunWith(AndroidJUnit4::class)
class TestMoreActionsUi : MainActivityUiTestFixtures() {
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

        openMoreActionsDialog()
        composeRule.onNodeWithText("Swap pulling team").performClick()
        assertLiveScreen()

        // Manual halftime is only available between points, so score the opening point first.
        composeRule.activityRule.scenario.onActivity { activity ->
            val current = activity.appViewModel.liveState!!
            activity.appViewModel.updateLiveGame(
                current.recordGoalFromCurrentState(TeamId.TEAM_ONE, System.currentTimeMillis())
            )
        }
        composeRule.waitForIdle()

        // Manual halftime should be reachable and leave a visible result.
        openMoreActionsDialog()
        composeRule.onNodeWithText("Start halftime").performScrollTo().performClick()
        waitForText("Halftime")
        // Back dismissal and OK are equivalent acknowledgements for this prompt.
        dismissDialog(text = "OK")
        assertLiveScreen()
    }

    /**
     * Test that deleting the current game is guarded by the slide confirmation.
     */
    @Test
    fun deleteGame() {
        startLiveGameProgrammatically()

        openMoreActionsDialog()
        composeRule.onNodeWithText("Delete game").performScrollTo().performClick()
        waitForText("This cannot be undone", substring = true)
        dismissDialog(text = "Cancel")
        waitForText("Update game setup")

        composeRule.onNodeWithText("Delete game").performScrollTo().performClick()
        confirmDeleteWithSlider()
        waitForText("Start new game")
        assertTrue(composeRule.onAllNodesWithText("Current game").fetchSemanticsNodes().isEmpty())
    }

}
