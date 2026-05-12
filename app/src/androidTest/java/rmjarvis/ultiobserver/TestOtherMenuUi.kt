package rmjarvis.ultiobserver

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.swipeRight
import java.time.LocalTime
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TestOtherMenuUi : MainActivityUiTestFixtures() {
    // Test the less-common live-game actions behind the Other menu.
    // The goal is to catch broken dialogs, buttons, and return paths for observer-accessible tools.
    @Test
    fun otherMenuPathways() {
        startLiveGameProgrammatically()

        // Manual correction dialogs should open and return to the Other sheet cleanly.
        openOtherSheet()
        openOtherDialogAndCancel("Adjust Score")
        openOtherDialogAndCancel("Adjust Timeouts")
        openOtherDialogAndCancel("Adjust Cards / TF")
        openOtherDialogAndCancel("Adjust Pull Infractions")

        // Manual correction dialogs should also apply their visible values.
        applyScoreAdjustment()
        applyTimeoutAdjustment()
        applyPullInfractionAdjustment()
        applyNoOpCardAdjustment()

        // Orientation controls should update state without breaking the live screen.
        openOtherSheet()
        composeRule.onNodeWithText("Swap Ends of Field").performClick()
        assertLiveScreen()

        openOtherSheet()
        composeRule.onNodeWithText("Swap Pulling Team").performClick()
        assertLiveScreen()

        // Less-common game-state actions should be reachable and leave a visible result.
        openOtherSheet()
        composeRule.onNodeWithText("Apply Soft Cap Now").performClick()
        waitForText("Undo Soft Cap Now")
        assertLiveScreen()

        openOtherSheet()
        composeRule.onNodeWithText("Start Halftime").performClick()
        waitForText("Halftime")
        composeRule.onNodeWithText("OK").performClick()
        assertLiveScreen()
    }

    // Test that deleting the current game is guarded by the slide confirmation.
    @Test
    fun otherMenuCanDeleteCurrentGameAfterSliderConfirmation() {
        startLiveGameProgrammatically()

        openOtherSheet()
        composeRule.onNodeWithText("Delete Game").performClick()
        waitForText("This cannot be undone", substring = true)
        composeRule.onNodeWithText("Cancel").performClick()
        waitForText("Update Game Setup")

        composeRule.onNodeWithText("Delete Game").performClick()
        confirmDeleteWithSlider()
        waitForText("Start New Game")
        assertTrue(composeRule.onAllNodesWithText("Current Game").fetchSemanticsNodes().isEmpty())
    }

    // Test that archived games can be deleted in bulk and one at a time from Previous Games.
    @Test
    fun previousGamesCanDeleteArchivedGameAfterSliderConfirmation() {
        clearArchivedGamesProgrammatically()
        val suffix = System.currentTimeMillis().toString().takeLast(6)
        val firstTeamOne = "DelA$suffix"
        val firstTeamTwo = "DelB$suffix"
        val secondTeamOne = "DelC$suffix"
        val secondTeamTwo = "DelD$suffix"
        val firstArchivedTitle = "$firstTeamOne 0 - 0 $firstTeamTwo"
        val secondArchivedTitle = "$secondTeamOne 0 - 0 $secondTeamTwo"

        seedArchivedGameProgrammatically(firstTeamOne, firstTeamTwo)
        seedArchivedGameProgrammatically(secondTeamOne, secondTeamTwo)
        openPreviousGamesScreen()
        waitForText(firstArchivedTitle)
        waitForText(secondArchivedTitle)
        composeRule.onNodeWithTag("delete-all-archived-games").performClick()
        waitForText("This cannot be undone", substring = true)
        confirmDeleteWithSlider("Delete All Games?")
        waitForText("No completed games yet.")
        assertTrue(composeRule.onAllNodesWithText(firstArchivedTitle).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText(secondArchivedTitle).fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithText("Back").performClick()

        seedArchivedGameProgrammatically(firstTeamOne, firstTeamTwo)
        seedArchivedGameProgrammatically(secondTeamOne, secondTeamTwo)
        openPreviousGamesScreen()
        waitForText(firstArchivedTitle)
        waitForText(secondArchivedTitle)
        composeRule.onNodeWithTag("delete-archived-game-$firstArchivedTitle").performClick()
        waitForText("This cannot be undone", substring = true)
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithText(firstArchivedTitle).assertIsDisplayed()
        composeRule.onNodeWithText(secondArchivedTitle).assertIsDisplayed()

        composeRule.onNodeWithTag("delete-archived-game-$firstArchivedTitle").performClick()
        confirmDeleteWithSlider()
        waitForText(secondArchivedTitle)
        assertTrue(composeRule.onAllNodesWithText(firstArchivedTitle).fetchSemanticsNodes().isEmpty())

        composeRule.onNodeWithTag("delete-archived-game-$secondArchivedTitle").performClick()
        confirmDeleteWithSlider()
        waitForText("No completed games yet.")
        assertTrue(composeRule.onAllNodesWithText(secondArchivedTitle).fetchSemanticsNodes().isEmpty())
    }

    private fun openPreviousGamesScreen() {
        composeRule.onNodeWithText("Previous Games").performClick()
        waitForText("Previous Games")
    }

}
