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

    /// Test More actions visibility for game states where cap and halftime actions no longer apply.
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

        openMoreActionsDialog()
        assertTrue(composeRule.onAllNodesWithText("Apply half cap now").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Apply soft cap now").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Apply hard cap now").fetchSemanticsNodes().isEmpty())

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

        assertTrue(composeRule.onAllNodesWithText("Apply half cap now").fetchSemanticsNodes().isEmpty())
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

    /// Test that an active game accidentally archived by Start new game can be restored from Archived games.
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
        waitForText("Game summary")
        composeRule.onNodeWithText("Restore game").performClick()

        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "card")).assertIsDisplayed()
        waitForText("More actions")
        composeRule.onNodeWithText(teamOneName).assertIsDisplayed()
        composeRule.onNodeWithText(teamTwoName).assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText(currentTeamOneName, substring = true).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText(currentTeamTwoName, substring = true).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Undo Start point").fetchSemanticsNodes().isEmpty())

        composeRule.onNodeWithText("Back").performClick()
        waitForText("Current game")
        openArchivedGamesScreen()
        waitForText(currentArchivedTitle)
        composeRule.onNodeWithText(currentTeamOneName, substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(currentTeamTwoName, substring = true).assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText(archivedTitle).fetchSemanticsNodes().isEmpty())
    }

    /// Test that archived games can be deleted in bulk and one at a time from Archived games.
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
        waitForText("Game summary")
        composeRule.onNodeWithText("Share").assertIsDisplayed()
        composeRule.onNodeWithText("Event log").performClick()
        waitForText("Event log")
        waitForText("No events logged yet.")
        pressDialogBack()
        composeRule.onNodeWithText("Back").performClick()
        waitForText("Archived games")
        waitForText(firstArchivedTitle)
        waitForText(secondArchivedTitle)

        composeRule.onNodeWithTag("delete-all-archived-games").performClick()
        waitForText("This cannot be undone", substring = true)
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithText(firstArchivedTitle).assertIsDisplayed()
        composeRule.onNodeWithText(secondArchivedTitle).assertIsDisplayed()

        composeRule.onNodeWithTag("delete-all-archived-games").performClick()
        waitForText("This cannot be undone", substring = true)
        confirmDeleteWithSlider("Delete all games?")
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

        // Delete the last archived row and verify Archived games returns to its empty state.
        composeRule.onNodeWithTag("delete-archived-game-$secondArchivedTitle").performClick()
        confirmDeleteWithSlider()
        waitForText("No completed games yet.")
        assertTrue(composeRule.onAllNodesWithText(secondArchivedTitle).fetchSemanticsNodes().isEmpty())
    }

    /// Open Archived games from Home and wait until the page is visible.
    private fun openArchivedGamesScreen() {
        composeRule.onNodeWithText("See archived games").performClick()
        waitForText("Archived games")
    }

}
