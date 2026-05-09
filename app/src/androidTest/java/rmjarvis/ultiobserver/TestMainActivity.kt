package rmjarvis.ultiobserver

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.espresso.Espresso.pressBack
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    // Test the basic launch story from home to setup to live play.
    // Keep this focused on the first-run path and the live-use lock affordance.
    @Test
    fun launchHomeAndStartGame() {
        // Verify the app opens on the home screen with the primary navigation affordances.
        composeRule.onNodeWithText("UltiObserver").assertIsDisplayed()
        composeRule.onNodeWithText("Start New Game").assertIsDisplayed()
        composeRule.onNodeWithText("Previous Games").assertIsDisplayed()

        // Walk the default new-game path into the live screen.
        openNewGameSetup()
        composeRule.onNodeWithText("UltiObserver Setup").assertIsDisplayed()

        // Starting a point should immediately switch the phone into locked live-use mode.
        startGameFromSetup()
        assertLiveScreen()
        composeRule.onNodeWithText("Start Point").performClick()
        waitForText("Slide right to unlock")
    }

    // Test the setup form's modal editors and prior-card entry point.
    // These are broad wiring checks rather than detailed rule-state assertions.
    @Test
    fun setupEditorsOpenAndReturnToSetup() {
        openNewGameSetup()

        // Exercise the exact start-time dialog without depending on the current clock.
        composeRule.onNodeWithText("Start time").performClick()
        composeRule.onNodeWithText("Set Start Time").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()

        // Open each compact rule editor to catch broken setup dialog wiring.
        openSetupDialog("Game to", "Game To")
        openSetupDialog("Halftime", "Halftime")
        openSetupDialog("Half cap", "Half Cap")
        openSetupDialog("Soft cap", "Soft Cap")
        openSetupDialog("Hard cap", "Hard Cap")
        openSetupDialog("Timeouts", "Timeout Rules")

        // Add a prior-card holder and make sure the form remains usable afterwards.
        composeRule.onNodeWithText("Add Card Holder").performScrollTo().performClick()
        composeRule.onNodeWithText("Add player cards").assertIsDisplayed()
        composeRule.onNodeWithText("Add").performClick()
        waitForText("Start Game")

        // The edited setup should still launch a live game.
        startGameFromSetup()
        assertLiveScreen()
    }

    // Test the home-screen path for preserving and resuming an active game.
    // Also cover the live-to-setup update flow because it shares the same app-level routing state.
    @Test
    fun homeCurrentGameResumeAndUpdateSetupPath() {
        startLiveGame()

        // Back navigation should preserve the active game and expose the resume path.
        pressBack()
        waitForText("Current Game")
        composeRule.onNodeWithText("Current Game").assertIsDisplayed()
        composeRule.onNodeWithText("Team 1 0 - 0 Team 2").performClick()
        assertLiveScreen()

        // The live Other menu should reopen setup in update mode and return to the same game.
        openOtherSheet()
        composeRule.onNodeWithText("Update Game Setup").performClick()
        waitForText("Back to Game Screen")
        composeRule.onNodeWithText("+5").performScrollTo().performClick()
        composeRule.onNodeWithText("Back to Game Screen").performScrollTo().performClick()
        assertLiveScreen()
    }

    // Test the primary live screen actions that should be available directly from the phone.
    // Keep the assertions at the visible undo/message level; GameModel owns detailed state checks.
    @Test
    fun livePrimaryActionsAndUndoPath() {
        startLiveGame()

        // A between-points goal implicitly starts the point and exposes a useful undo.
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "goal")).performClick()
        waitForText("Undo Goal by Team 1")
        composeRule.onNodeWithText("Undo Goal by Team 1").performClick()
        waitForText("Lock")

        // Timeout and pull-infraction buttons should remain wired after the undo path.
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "timeout")).performClick()
        waitForText("Undo Timeout by Team 1")

        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "pull-infraction")).performClick()
        waitForText("Start at brick mark")
        composeRule.onNodeWithText("OK").performClick()
        composeRule.onNodeWithText("Undo Offsides on Team 1").assertIsDisplayed()
    }

    // Test the card and technical-foul bottom sheet from the live screen.
    // This covers the phone-facing dialog sequence, not the full card-accounting matrix.
    @Test
    fun cardsAndTechnicalFoulSheetPath() {
        startLiveGame()

        // The Cards / TF sheet should show both team sections with their pull roles.
        openCardsSheet()
        composeRule.onNodeWithText("Team 1 (pulling)").assertIsDisplayed()
        composeRule.onNodeWithText("Team 2 (receiving)").assertIsDisplayed()

        // Blue cards and technical fouls should close the sheet and show the consequence cue.
        composeRule.onAllNodesWithText("Blue").onFirst().performClick()
        waitForText("Team 1 has 1 card.")
        composeRule.onNodeWithText("OK").performClick()

        openCardsSheet()
        composeRule.onAllNodesWithText("Tech").onFirst().performClick()
        waitForText("Team 1 has 1 technical foul.")
        composeRule.onNodeWithText("OK").performClick()

        // Yellow cards should prompt for a player number while still allowing N/A.
        openCardsSheet()
        composeRule.onAllNodesWithText("Yellow").onFirst().performClick()
        composeRule.onNodeWithText("Yellow Card").assertIsDisplayed()
        composeRule.onNodeWithText("Player number").assertIsDisplayed()
        composeRule.onNodeWithText("N/A").performClick()
        waitForText("Team 1 has 2 cards.")
        composeRule.onNodeWithText("OK").performClick()

        // A red on a player with a yellow should ask direct red vs second yellow.
        openCardsSheet()
        composeRule.onAllNodesWithText("Red").onFirst().performClick()
        composeRule.onNodeWithText("Red Card").assertIsDisplayed()
        composeRule.onNodeWithText("N/A").performClick()
        waitForText("Player Already Has Yellow")
        composeRule.onNodeWithText("Direct Red").performClick()
        waitForText("Team 1 has 4 cards.", substring = true)
    }

    // Test the less-common live-game actions behind the Other menu.
    // The goal is to catch broken dialogs, buttons, and return paths for observer-accessible tools.
    @Test
    fun otherMenuPathways() {
        startLiveGame()

        // Manual correction dialogs should open and return to the Other sheet cleanly.
        openOtherSheet()
        openOtherDialogAndCancel("Adjust Score")
        openOtherDialogAndCancel("Adjust Timeouts")
        openOtherDialogAndCancel("Adjust Cards / TF")
        openOtherDialogAndCancel("Adjust Pull Infractions")

        // Orientation controls should update state without breaking the live screen.
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

    // Test the completed-game flow from live game over to home archive to previous-game summary.
    // This protects the top-level navigation state around finished games.
    @Test
    fun completedAndPreviousGamePaths() {
        startLiveGame()

        // Manual game end should transition to the read-only summary surface with undo available.
        openOtherSheet()
        composeRule.onNodeWithText("End Game").performClick()
        waitForText("Game is over", substring = true)
        composeRule.onNodeWithText("OK").performClick()
        composeRule.onNodeWithText("Game Summary").assertIsDisplayed()
        composeRule.onNodeWithText("Undo End Game").assertIsDisplayed()

        // Completed games should be archivable from home and reopen from Previous Games.
        pressBack()
        waitForText("Completed Game")
        composeRule.onNodeWithText("Archive Completed Game").performClick()
        waitForText("Previous Games")
        composeRule.onNodeWithText("Team 1 0 - 0 Team 2").performClick()
        composeRule.onNodeWithText("Game Summary").assertIsDisplayed()
    }

    private fun openNewGameSetup() {
        composeRule.onNodeWithText("Start New Game").performClick()
        waitForText("UltiObserver Setup")
    }

    private fun startLiveGame() {
        openNewGameSetup()
        startGameFromSetup()
    }

    private fun startGameFromSetup() {
        composeRule.onNodeWithText("Start Game").performScrollTo().performClick()
        assertLiveScreen()
    }

    private fun assertLiveScreen() {
        waitForText("Cards / TF")
        composeRule.onNodeWithText("Cards / TF").assertIsDisplayed()
        composeRule.onNodeWithText("Other").assertIsDisplayed()
    }

    private fun openSetupDialog(buttonText: String, dialogTitle: String) {
        composeRule.onNodeWithText(buttonText).performScrollTo().performClick()
        waitForText(dialogTitle)
        composeRule.onNodeWithText("Cancel").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
    }

    private fun openCardsSheet() {
        composeRule.onNodeWithText("Cards / TF").performClick()
        waitForText("Cards / Technical Fouls")
    }

    private fun openOtherSheet() {
        composeRule.onAllNodesWithText("Other").onFirst().performClick()
        waitForText("Update Game Setup")
    }

    private fun openOtherDialogAndCancel(label: String) {
        composeRule.onNodeWithText(label).performClick()
        composeRule.onNodeWithText("Cancel").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        waitForText("Update Game Setup")
    }

    private fun waitForText(text: String, substring: Boolean = false) {
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun teamActionTag(team: TeamId, action: String): String {
        return "live-${team.name}-$action"
    }
}
