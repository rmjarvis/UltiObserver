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
class TestOtherMenuUi : MainActivityUiTestFixtures() {
    /**
     * Test the less-common live-game actions behind More actions.
     * The goal is to catch broken dialogs, buttons, and return paths for observer-accessible tools.
     */
    @Test
    fun otherMenuPathways() {
        startLiveGameProgrammatically()

        // Manual correction dialogs should open and return to More actions cleanly.
        openMoreActionsDialog()
        if (shouldUsePlatformBackDismissalCoverage()) {
            pressDialogBack()
            composeRule.onAllNodesWithText("Update game setup").assertCountEquals(0)
            openMoreActionsDialog()
        }
        composeRule.onNodeWithText("Event log").performClick()
        waitForText("Event log")
        waitForText("No events logged yet.")
        if (shouldUsePlatformBackDismissalCoverage()) {
            pressDialogBack()
        } else {
            composeRule.onNodeWithText("OK").performClick()
        }
        openMoreActionsDialog()
        openMoreActionsDialogAndCancel("Adjust score")
        openMoreActionsDialogAndCancel("Adjust timeouts")
        openMoreActionsDialogAndCancel("Adjust cards / techs")
        openMoreActionsDialogAndCancel("Adjust pull infractions")

        // Manual correction dialogs should also apply their visible values.
        applyScoreAdjustment()
        applyTimeoutAdjustment()
        applyPullInfractionAdjustment()
        applyCardTechAdjustment()

        // Orientation controls should update state without breaking the live screen.
        openMoreActionsDialog()
        composeRule.onNodeWithText("Flip field display").performClick()
        waitForText("Undo Flip field display")
        assertLiveScreen()

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

        // Less-common game-state actions should be reachable and leave a visible result.
        openMoreActionsDialog()
        composeRule.onNodeWithText("Apply half cap now").performScrollTo().performClick()
        waitForText("Undo Half cap now")
        assertLiveScreen()

        openMoreActionsDialog()
        composeRule.onNodeWithText("Apply soft cap now").performScrollTo().performClick()
        waitForText("Undo Soft cap now")
        assertLiveScreen()

        openMoreActionsDialog()
        composeRule.onNodeWithText("Apply hard cap now").performScrollTo().performClick()
        waitForText("Undo Hard cap now")
        assertLiveScreen()

        openMoreActionsDialog()
        composeRule.onNodeWithText("Start halftime").performScrollTo().performClick()
        waitForText("Halftime")
        // Back dismissal and OK are equivalent acknowledgements for this prompt.
        if (shouldUsePlatformBackDismissalCoverage()) {
            pressDialogBack()
        } else {
            composeRule.onNodeWithText("OK").performClick()
        }
        assertLiveScreen()
    }

    /// Test that deleting the current game is guarded by the slide confirmation.
    @Test
    fun otherMenuCanDeleteCurrentGameAfterSliderConfirmation() {
        startLiveGameProgrammatically()

        openMoreActionsDialog()
        composeRule.onNodeWithText("Delete game").performScrollTo().performClick()
        waitForText("This cannot be undone", substring = true)
        composeRule.onNodeWithText("Cancel").performClick()
        waitForText("Update game setup")

        composeRule.onNodeWithText("Delete game").performScrollTo().performClick()
        confirmDeleteWithSlider()
        waitForText("Start new game")
        assertTrue(composeRule.onAllNodesWithText("Current game").fetchSemanticsNodes().isEmpty())
    }

}
