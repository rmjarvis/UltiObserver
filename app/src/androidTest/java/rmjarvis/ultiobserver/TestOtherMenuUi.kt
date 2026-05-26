package rmjarvis.ultiobserver

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
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

/// Tests for live-game Other menu dialogs and correction flows.
@RunWith(AndroidJUnit4::class)
class TestOtherMenuUi : MainActivityUiTestFixtures() {
    /**
     * Test the less-common live-game actions behind the Other menu.
     * The goal is to catch broken dialogs, buttons, and return paths for observer-accessible tools.
     */
    @Test
    fun otherMenuPathways() {
        startLiveGameProgrammatically()

        // Manual correction dialogs should open and return to the Other sheet cleanly.
        openOtherSheet()
        if (shouldUsePlatformBackDismissalCoverage()) {
            pressDialogBack()
            composeRule.onAllNodesWithText("Update Game Setup").assertCountEquals(0)
            openOtherSheet()
        }
        composeRule.onNodeWithText("Event Log").performClick()
        waitForText("Event Log")
        waitForText("No events logged yet.")
        pressDialogBack()
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
        composeRule.onNodeWithText("Apply Half Cap Now").performClick()
        waitForText("Undo Half Cap Now")
        assertLiveScreen()

        openOtherSheet()
        composeRule.onNodeWithText("Apply Soft Cap Now").performClick()
        waitForText("Undo Soft Cap Now")
        assertLiveScreen()

        openOtherSheet()
        composeRule.onNodeWithText("Apply Hard Cap Now").performClick()
        waitForText("Undo Hard Cap Now")
        assertLiveScreen()

        openOtherSheet()
        composeRule.onNodeWithText("Start Halftime").performClick()
        waitForText("Halftime")
        // Back dismissal and OK are equivalent acknowledgements for this prompt.
        if (shouldUsePlatformBackDismissalCoverage()) {
            pressDialogBack()
        } else {
            composeRule.onNodeWithText("OK").performClick()
        }
        assertLiveScreen()
    }

    /// Test Other menu visibility for game states where cap and halftime actions no longer apply.
    @Test
    fun otherMenuHidesUnavailableCapActions() {
        startLiveGameProgrammatically()

        composeRule.activityRule.scenario.onActivity { activity ->
            val current = activity.appViewModel.liveState!!
            activity.appViewModel.updateLiveGame(
                current.copy(
                    halfCapApplied = true,
                    softCapApplied = true,
                    hardCapApplied = true,
                )
            )
        }
        composeRule.waitForIdle()

        openOtherSheet()
        assertTrue(composeRule.onAllNodesWithText("Apply Half Cap Now").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Apply Soft Cap Now").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Apply Hard Cap Now").fetchSemanticsNodes().isEmpty())

        composeRule.activityRule.scenario.onActivity { activity ->
            val current = activity.appViewModel.liveState!!
            activity.appViewModel.updateLiveGame(
                current.copy(
                    halftimeTaken = true,
                    halfCapApplied = false,
                    softCapApplied = false,
                    hardCapApplied = false,
                )
            )
        }
        composeRule.waitForIdle()

        assertTrue(composeRule.onAllNodesWithText("Apply Half Cap Now").fetchSemanticsNodes().isEmpty())
    }

    /// Test that deleting the current game is guarded by the slide confirmation.
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

    /// Test that an active game accidentally archived by Start New Game can be restored from Archived Games.
    @Test
    fun archivedGamesCanRestoreAccidentallyArchivedActiveGame() {
        clearArchivedGamesProgrammatically()
        val suffix = System.currentTimeMillis().toString().takeLast(6)
        val teamOneName = "RestoreA$suffix"
        val teamTwoName = "RestoreB$suffix"
        val archivedTitle = "$teamOneName 0 - 0 $teamTwoName"
        val currentTeamOneName = "CurrentA$suffix"
        val currentTeamTwoName = "CurrentB$suffix"
        val currentArchivedTitle = "$currentTeamOneName 0 - 0 $currentTeamTwoName"

        // Archive one active live-point game, then start another current game before restoring the first.
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.appViewModel.deleteCurrentGame()
            activity.appViewModel.startNewGame()
            activity.appViewModel.updateSetup(
                newGameSetupState().copy(
                    teamOne = TeamSetup(name = teamOneName, color = TeamColorChoice.WHITE),
                    teamTwo = TeamSetup(name = teamTwoName, color = TeamColorChoice.BLUE),
                )
            )
            activity.appViewModel.finishSetup()
            activity.appViewModel.updateLiveGame(activity.appViewModel.liveState!!.beginLivePoint(0L))
            activity.appViewModel.startNewGame()
            activity.appViewModel.updateSetup(
                newGameSetupState().copy(
                    teamOne = TeamSetup(name = currentTeamOneName, color = TeamColorChoice.WHITE),
                    teamTwo = TeamSetup(name = currentTeamTwoName, color = TeamColorChoice.BLUE),
                )
            )
            activity.appViewModel.finishSetup()
            activity.appViewModel.updateLiveGame(activity.appViewModel.liveState!!.beginLivePoint(0L))
            activity.appViewModel.goHome()
        }
        composeRule.waitForIdle()

        openArchivedGamesScreen()
        waitForText(archivedTitle)
        composeRule.onNodeWithText(archivedTitle).performClick()
        waitForText("Game Summary")
        composeRule.onNodeWithText("Restore Game").performClick()

        waitForText("Cards / TF")
        waitForText("Other")
        composeRule.onNodeWithText(teamOneName).assertIsDisplayed()
        composeRule.onNodeWithText(teamTwoName).assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText(currentTeamOneName, substring = true).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText(currentTeamTwoName, substring = true).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Undo Start Point").fetchSemanticsNodes().isEmpty())

        composeRule.onNodeWithText("Back").performClick()
        waitForText("Current Game")
        openArchivedGamesScreen()
        waitForText(currentArchivedTitle)
        composeRule.onNodeWithText(currentTeamOneName, substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(currentTeamTwoName, substring = true).assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText(archivedTitle).fetchSemanticsNodes().isEmpty())
    }

    /// Test that archived games can be deleted in bulk and one at a time from Archived Games.
    @Test
    fun archivedGamesCanDeleteArchivedGameAfterSliderConfirmation() {
        // Build two uniquely named archived rows so delete assertions cannot match stale test data.
        clearArchivedGamesProgrammatically()
        val suffix = System.currentTimeMillis().toString().takeLast(6)
        val firstTeamOne = "DelA$suffix"
        val firstTeamTwo = "DelB$suffix"
        val secondTeamOne = "DelC$suffix"
        val secondTeamTwo = "DelD$suffix"
        val firstArchivedTitle = "$firstTeamOne 0 - 0 $firstTeamTwo"
        val secondArchivedTitle = "$secondTeamOne 0 - 0 $secondTeamTwo"

        // Cancel once from bulk delete, then confirm it removes every archived row.
        seedArchivedGameProgrammatically(firstTeamOne, firstTeamTwo)
        seedArchivedGameProgrammatically(secondTeamOne, secondTeamTwo)
        openArchivedGamesScreen()
        waitForText(firstArchivedTitle)
        waitForText(secondArchivedTitle)

        // Opening an archived row should expose the read-only summary and its persisted event log.
        composeRule.onNodeWithText(firstArchivedTitle).performClick()
        waitForText("Game Summary")
        composeRule.onNodeWithText("Share").assertIsDisplayed()
        composeRule.onNodeWithText("Event Log").performClick()
        waitForText("Event Log")
        waitForText("No events logged yet.")
        pressDialogBack()
        composeRule.onNodeWithText("Back").performClick()
        waitForText("Archived Games")
        waitForText(firstArchivedTitle)
        waitForText(secondArchivedTitle)

        composeRule.onNodeWithTag("delete-all-archived-games").performClick()
        waitForText("This cannot be undone", substring = true)
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithText(firstArchivedTitle).assertIsDisplayed()
        composeRule.onNodeWithText(secondArchivedTitle).assertIsDisplayed()

        composeRule.onNodeWithTag("delete-all-archived-games").performClick()
        waitForText("This cannot be undone", substring = true)
        confirmDeleteWithSlider("Delete All Games?")
        waitForText("No completed games yet.")
        assertTrue(composeRule.onAllNodesWithText(firstArchivedTitle).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText(secondArchivedTitle).fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithText("Back").performClick()

        // Re-seed the archive and verify cancelling a single-game delete leaves both rows intact.
        seedArchivedGameProgrammatically(firstTeamOne, firstTeamTwo)
        seedArchivedGameProgrammatically(secondTeamOne, secondTeamTwo)
        openArchivedGamesScreen()
        waitForText(firstArchivedTitle)
        waitForText(secondArchivedTitle)
        composeRule.onNodeWithTag("delete-archived-game-$firstArchivedTitle").performClick()
        waitForText("This cannot be undone", substring = true)
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithText(firstArchivedTitle).assertIsDisplayed()
        composeRule.onNodeWithText(secondArchivedTitle).assertIsDisplayed()

        // Confirm a single-game delete removes only the selected archived row.
        composeRule.onNodeWithTag("delete-archived-game-$firstArchivedTitle").performClick()
        confirmDeleteWithSlider()
        waitForText(secondArchivedTitle)
        assertTrue(composeRule.onAllNodesWithText(firstArchivedTitle).fetchSemanticsNodes().isEmpty())

        // Delete the last archived row and verify Archived Games returns to its empty state.
        composeRule.onNodeWithTag("delete-archived-game-$secondArchivedTitle").performClick()
        confirmDeleteWithSlider()
        waitForText("No completed games yet.")
        assertTrue(composeRule.onAllNodesWithText(secondArchivedTitle).fetchSemanticsNodes().isEmpty())
    }

    /// Open Archived Games from Home and wait until the page is visible.
    private fun openArchivedGamesScreen() {
        composeRule.onNodeWithText("See Archived Games").performClick()
        waitForText("Archived Games")
    }

}
