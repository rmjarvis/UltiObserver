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
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TestHomeAndNavigationUi : MainActivityUiTestFixtures() {
    // Test the basic launch story from home to setup to live play.
    // Keep this focused on the first-run path and the live-use lock affordance.
    @Test
    fun launchHomeAndStartGame() {
        // Verify the app opens on the home screen with the primary navigation affordances.
        composeRule.onNodeWithText("UltiObserver").assertIsDisplayed()
        composeRule.onNodeWithTag("home-artwork").assertIsDisplayed()
        composeRule.onNodeWithText("Start New Game").assertIsDisplayed()
        composeRule.onNodeWithText("Profile").assertIsDisplayed()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule.onNodeWithText("Previous Games").assertIsDisplayed()

        // Walk the default new-game path into the live screen.
        openNewGameSetup()
        composeRule.onNodeWithText("UltiObserver Setup").assertIsDisplayed()
        replaceSetupTeamName("Team 1", "Draft Team")

        // Backing out of setup should keep a resumable setup draft on Home.
        pressAppBack()
        waitForText("Current Game")
        composeRule.onNodeWithText("Tap to resume setup.").assertIsDisplayed()
        composeRule.onNodeWithText("Draft Team 0 - 0 Team 2").performClick()
        waitForText("Start Game")

        // Before the first pull, Back should return to setup for quick field-layout corrections.
        startGameFromSetup()
        assertLiveScreen()
        pressAppBack()
        waitForText("Start Game")

        // Starting a point should immediately switch the phone into locked live-use mode.
        startGameFromSetup()
        composeRule.onNodeWithText("Start Point").performClick()
        waitForText("Slide right to unlock")
    }

    // Test the home-screen and update-setup buttons that wire into app-level routing state.
    @Test
    fun homeCurrentGameResumeAndUpdateSetupPath() {
        startLiveGame()
        composeRule.onNodeWithText("Start Point").performClick()
        waitForText("Slide right to unlock")

        // After the first pull, Back navigation should expose the current-game resume path.
        pressAppBack()
        waitForText("Current Game")
        composeRule.onNodeWithText("Tap to resume the active game.").assertIsDisplayed()
        composeRule.onNodeWithText("Current Game").assertIsDisplayed()
        composeRule.onNodeWithText("Team 1 0 - 0 Team 2").performClick()
        assertLiveScreen()

        // The live Other menu should reopen setup in update mode and return to live.
        openOtherSheet()
        composeRule.onNodeWithText("Update Game Setup").performClick()
        waitForText("Back to Game Screen")
        composeRule.onNodeWithText("Back to Game Screen").performClick()
        assertLiveScreen()
    }

    // Test the explicit app-bar Back buttons mirror Android back navigation on main screens.
    @Test
    fun topLevelScreensHaveVisibleBackButtons() {
        openNewGameSetup()
        composeRule.onNodeWithText("Back").performClick()
        waitForText("Start New Game")

        startLiveGame()
        composeRule.onNodeWithText("Back").performClick()
        waitForText("Start Game")
        startGameFromSetup()
        composeRule.onNodeWithText("Start Point").performClick()
        waitForText("Slide right to unlock")
        composeRule.onNodeWithText("Back").performClick()
        waitForText("Current Game")
    }

    // Test the new home destinations that are present before their full feature work exists.
    @Test
    fun homeDestinationButtonsOpenStubPages() {
        composeRule.onNodeWithText("Profile").performClick()
        waitForText("Name")
        composeRule.onNodeWithTag("profile-name-field").performTextReplacement("Casey Observer")
        composeRule.onNodeWithText("Casey Observer").assertIsDisplayed()

        pressAppBack()
        waitForText("Start New Game")
        composeRule.onNodeWithText("Settings").performClick()
        waitForText("No settings available yet.")

        pressAppBack()
        waitForText("Start New Game")
        composeRule.onNodeWithText("Previous Games").performClick()
        composeRule.onNodeWithTag("previous-games-screen").assertIsDisplayed()
    }
}
