package rmjarvis.ultiobserver

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.swipeRight
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

    // Test a representative complete game from setup through halftime to final score.
    // Keep this as a user-visible UI story that checks flow, not detailed model accounting.
    @Test
    fun normalGamePath() {
        val viscousCoupling = "Viscous Coupling"
        val animal = "Animal"

        // Set up a short non-default game so the UI story covers setup editing,
        // halftime, and game over without a long repetitive scoring sequence.
        openNewGameSetup()
        replaceSetupTeamName("Team 1", viscousCoupling)
        replaceSetupTeamName("Team 2", animal)
        setIntegerSetupValue("Game to", "Game To", "Points", "5")
        setIntegerSetupValue("Halftime", "Halftime", "Minutes", "1")
        composeRule.onNodeWithText("Near end").performScrollTo().performClick()
        startGameFromSetup()
        composeRule.onNodeWithText(viscousCoupling).assertIsDisplayed()
        composeRule.onNodeWithText(animal).assertIsDisplayed()

        // The opening pull starts the first live point and the observer unlocks for live actions.
        startPointAndUnlock()

        // Animal calls a live-point timeout, then play resumes from the timeout countdown.
        recordTimeout(TeamId.TEAM_TWO, "Undo Timeout by $animal")
        continuePointAndUnlock()

        // Viscous Coupling gets two early card points, then a third card that needs a misconduct choice.
        recordYellowCard(TeamId.TEAM_ONE, "17", "$viscousCoupling has 1 card.")
        recordBlueCard(TeamId.TEAM_ONE, "$viscousCoupling has 2 cards.")
        recordYellowCard(
            team = TeamId.TEAM_ONE,
            playerNumber = "8",
            expectedMessage = "$viscousCoupling has 3 cards.",
            misconductChoice = "Offense",
            expectedMisconductMessage = "Reverse brick",
        )

        // Viscous Coupling scores the first point, then records an offsides on the next pull.
        recordGoal(TeamId.TEAM_ONE, "Undo Goal by $viscousCoupling")
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "pull-infraction")).performClick()
        waitForText("Start at brick mark")
        composeRule.onNodeWithText("OK").performClick()

        // Animal picks up two yellows and two technical fouls during the live point.
        recordYellowCard(TeamId.TEAM_TWO, "23", "$animal has 1 card.")
        recordYellowCard(TeamId.TEAM_TWO, "8", "$animal has 2 cards.")
        recordTechnicalFoul(TeamId.TEAM_TWO, "$animal has 1 technical foul.")
        recordTechnicalFoul(TeamId.TEAM_TWO, "$animal has 2 technical fouls.")

        // Viscous Coupling calls a live-point timeout before Animal finishes the point.
        recordTimeout(TeamId.TEAM_ONE, "Undo Timeout by $viscousCoupling")
        continuePointAndUnlock()
        recordGoal(TeamId.TEAM_TWO, "Undo Goal by $animal")

        // Animal reaches the technical-foul threshold between points, so the UI shows the yardage cue.
        recordTechnicalFoul(
            team = TeamId.TEAM_TWO,
            expectedMessage = "Receiving team starts at attacking brick.",
            substring = true,
        )

        // Viscous Coupling scores the next two points, checking that halftime interrupts the flow.
        recordGoal(TeamId.TEAM_ONE, "Undo Goal by $viscousCoupling")
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "goal")).performClick()
        waitForText("Halftime")
        composeRule.onNodeWithText("OK").performClick()

        // Advance the visible halftime countdown using the same correction control an observer has.
        repeat(13) {
            composeRule.onAllNodesWithText("-5").onFirst().performClick()
        }
        waitForText("Start Point")

        // After halftime, Animal scores and uses one second-half timeout before the next pull.
        startPointAndUnlock()
        recordGoal(TeamId.TEAM_TWO, "Undo Goal by $animal")
        recordTimeout(TeamId.TEAM_TWO, "Undo Timeout by $animal")

        // Animal ties the game, Viscous Coupling goes ahead, and Animal wins on universe.
        recordGoal(TeamId.TEAM_TWO, "Undo Goal by $animal")
        recordGoal(TeamId.TEAM_ONE, "Undo Goal by $viscousCoupling")
        recordGoal(TeamId.TEAM_TWO, "Undo Goal by $animal")
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_TWO, "goal")).performClick()
        waitForText("Game is over", substring = true)
        composeRule.onNodeWithText("OK").performClick()
        composeRule.onNodeWithText("Game Summary").assertIsDisplayed()
        composeRule.onNodeWithText(viscousCoupling).assertIsDisplayed()
        composeRule.onNodeWithText(animal).assertIsDisplayed()
        composeRule.onNodeWithText("Undo End Game").assertIsDisplayed()
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

    private fun replaceSetupTeamName(fieldLabel: String, teamName: String) {
        composeRule.onNodeWithTag("setup-$fieldLabel-name").performScrollTo().performTextReplacement(teamName)
    }

    private fun setIntegerSetupValue(
        buttonText: String,
        dialogTitle: String,
        fieldLabel: String,
        value: String,
    ) {
        composeRule.onNodeWithText(buttonText).performScrollTo().performClick()
        waitForText(dialogTitle)
        composeRule.onNodeWithText(fieldLabel).performTextReplacement(value)
        composeRule.onNodeWithText("Set").performClick()
        waitForText("Start Game")
    }

    private fun recordGoal(team: TeamId, undoLabel: String) {
        composeRule.onNodeWithTag(teamActionTag(team, "goal")).performClick()
        waitForText(undoLabel)
    }

    private fun startPointAndUnlock() {
        composeRule.onNodeWithText("Start Point").performClick()
        waitForText("Slide right to unlock")
        unlockLiveScreen()
    }

    private fun continuePointAndUnlock() {
        composeRule.onNodeWithText("Continue Point").performClick()
        waitForText("Slide right to unlock")
        unlockLiveScreen()
    }

    private fun unlockLiveScreen() {
        composeRule.onNodeWithTag("live-unlock-slider").performTouchInput {
            swipeRight()
        }
        waitForText("Lock")
    }

    private fun recordTimeout(team: TeamId, undoLabel: String) {
        composeRule.onNodeWithTag(teamActionTag(team, "timeout")).performClick()
        waitForText(undoLabel)
    }

    private fun recordYellowCard(
        team: TeamId,
        playerNumber: String,
        expectedMessage: String,
        misconductChoice: String? = null,
        expectedMisconductMessage: String? = null,
    ) {
        openCardsSheet()
        composeRule.onAllNodesWithText("Yellow")[teamCardButtonIndex(team)].performClick()
        waitForText("Yellow Card")
        composeRule.onNodeWithText("Player number").performTextReplacement(playerNumber)
        composeRule.onNodeWithText("Record").performClick()

        if (misconductChoice == null) {
            waitForText(expectedMessage)
        } else {
            waitForText("Misconduct Penalty")
            composeRule.onNodeWithText(misconductChoice).performClick()
            waitForText(expectedMisconductMessage ?: expectedMessage, substring = true)
        }
        composeRule.onNodeWithText("OK").performClick()
    }

    private fun recordBlueCard(team: TeamId, expectedMessage: String) {
        openCardsSheet()
        composeRule.onAllNodesWithText("Blue")[teamCardButtonIndex(team)].performClick()
        waitForText(expectedMessage)
        composeRule.onNodeWithText("OK").performClick()
    }

    private fun recordTechnicalFoul(
        team: TeamId,
        expectedMessage: String,
        substring: Boolean = false,
    ) {
        openCardsSheet()
        composeRule.onAllNodesWithText("Tech")[teamCardButtonIndex(team)].performClick()
        waitForText(expectedMessage, substring = substring)
        composeRule.onNodeWithText("OK").performClick()
    }

    private fun teamCardButtonIndex(team: TeamId): Int {
        return if (team == TeamId.TEAM_ONE) 0 else 1
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
