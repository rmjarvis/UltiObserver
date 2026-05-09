package rmjarvis.ultiobserver

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
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
import java.time.LocalTime
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
        setCapRuleToNone("Half cap", "Half Cap")
        setCapRuleToNone("Soft cap", "Soft Cap")
        setCapRuleToNone("Hard cap", "Hard Cap")
        composeRule.onNodeWithText("Near end").performScrollTo().performClick()
        startGameFromSetup()
        composeRule.onNodeWithText(viscousCoupling).assertIsDisplayed()
        composeRule.onNodeWithText(animal).assertIsDisplayed()

        // The opening pull starts the first live point; a short swipe should fail before a full unlock.
        startPointWithFailedSwipeThenUnlock()

        // The top-right Lock action should relock the same live layout.
        composeRule.onNodeWithTag("live-top-lock").performClick()
        waitForText("Slide right to unlock")
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "goal")).assertIsNotEnabled()
        unlockLiveScreen()

        // The center field Lock action should also relock the screen during a live point.
        composeRule.onNodeWithTag("live-center-lock").performClick()
        waitForText("Slide right to unlock")
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_TWO, "timeout")).assertIsNotEnabled()
        unlockLiveScreen()

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

        // Viscous Coupling scores the first point, then Animal false-starts and that entry is undone.
        recordGoal(TeamId.TEAM_ONE, "Undo Goal by $viscousCoupling")
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_TWO, "pull-infraction")).performClick()
        waitForText("Defense gets to set up.")
        composeRule.onNodeWithText("OK").performClick()
        composeRule.onNodeWithText("Undo False Start on $animal").performClick()

        // Viscous Coupling then records an offsides; the duplicate offsides button is disabled.
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "pull-infraction")).performClick()
        waitForText("Start at brick mark")
        composeRule.onNodeWithText("OK").performClick()
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "pull-infraction")).assertIsNotEnabled()

        // Animal picks up two yellows and two technical fouls during the live point.
        recordYellowCard(TeamId.TEAM_TWO, "23", "$animal has 1 card.")
        recordYellowCard(TeamId.TEAM_TWO, "8", "$animal has 2 cards.")
        recordTechnicalFoul(TeamId.TEAM_TWO, "$animal has 1 technical foul.")
        recordTechnicalFoul(TeamId.TEAM_TWO, "$animal has 2 technical fouls.")

        // Viscous Coupling calls a live-point timeout before Animal finishes the point.
        recordTimeout(TeamId.TEAM_ONE, "Undo Timeout by $viscousCoupling")
        continuePointAndUnlock()
        recordGoal(TeamId.TEAM_TWO, "Undo Goal by $animal")

        // Animal uses its final first-half timeout, then gets the out-of-timeouts cue.
        recordTimeout(TeamId.TEAM_TWO, "Undo Timeout by $animal")
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_TWO, "timeout")).performClick()
        waitForText("$animal is out of timeouts.")
        composeRule.onNodeWithText("OK").performClick()

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
        composeRule.onNodeWithText("$viscousCoupling 4").assertIsDisplayed()
        composeRule.onNodeWithText("$animal 5").assertIsDisplayed()
        composeRule.onNodeWithText(viscousCoupling).assertIsDisplayed()
        composeRule.onNodeWithText(animal).assertIsDisplayed()
        composeRule.onNodeWithText("Undo End Game").performClick()
        assertLiveScreen()

        // Manually ending from the restored final state should return to the same summary.
        openOtherSheet()
        composeRule.onNodeWithText("End Game").performClick()
        waitForText("Game is over", substring = true)
        composeRule.onNodeWithText("OK").performClick()
        composeRule.onNodeWithText("Game Summary").assertIsDisplayed()
        composeRule.onNodeWithText("$viscousCoupling 4").assertIsDisplayed()
        composeRule.onNodeWithText("$animal 5").assertIsDisplayed()

        // The finished game should reopen from home, archive, and then reopen from Previous Games.
        pressBack()
        waitForText("Completed Game")
        composeRule.onNodeWithText("Archive Completed Game").performClick()
        waitForText("Previous Games")
        composeRule.onNodeWithText("$viscousCoupling 4 - 5 $animal").performClick()
        composeRule.onNodeWithText("Game Summary").assertIsDisplayed()
        composeRule.onNodeWithText("$animal 5").assertIsDisplayed()
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

    // Test a comprehensive setup pass that changes every editable pregame section.
    // This protects the setup screen's user-facing editors without asserting model internals.
    @Test
    fun setupScreenCanEditEveryField() {
        val aardvarks = "Aardvarks"
        val beagles = "Beagles"

        openNewGameSetup()

        // Start time supports both quick nudges and the exact-time dialog cancel/set paths.
        composeRule.onNodeWithText("-5").performClick()
        composeRule.onNodeWithText("+5").performClick()
        composeRule.onNodeWithText("Start time").performClick()
        waitForText("Set Start Time")
        composeRule.onNodeWithText("Cancel").performClick()
        setStartTime(LocalTime.of(11, 45))

        // Team fields include name text and the compact color swatch rows.
        replaceSetupTeamName("Team 1", aardvarks)
        replaceSetupTeamName("Team 2", beagles)
        composeRule.onNodeWithTag("setup-Team 1-color-${TeamColorChoice.BLUE.name}").performScrollTo().performClick()
        composeRule.onNodeWithTag("setup-Team 2-color-${TeamColorChoice.ORANGE.name}").performScrollTo().performClick()

        // Starting-pull setup should accept either team and either field end.
        composeRule.onNodeWithTag("setup-pulling-team-${TeamId.TEAM_TWO.name}").performScrollTo().performClick()
        composeRule.onNodeWithText("Near end").performScrollTo().performClick()
        composeRule.onNodeWithText("Far end").performScrollTo().performClick()
        composeRule.onNodeWithText("Near end").performScrollTo().performClick()

        // Rule editors cover numeric fields, enabled caps, disabled caps, and timeout floaters.
        setIntegerSetupValue("Game to", "Game To", "Points", "7")
        setIntegerSetupValue("Halftime", "Halftime", "Minutes", "2")
        setCapRuleToNone("Half cap", "Half Cap")
        setCapRuleValue("Soft cap", "Soft Cap", "12")
        setCapRuleToNone("Hard cap", "Hard Cap")
        setCapRuleValue("Hard cap", "Hard Cap", "20", enableFromNone = true)
        setTimeoutRules(timeoutsPerHalf = "3", hasFloater = true)

        // Prior-card entry should support cancel, team selection, yellow/red counts, and removal.
        composeRule.onNodeWithText("Add Card Holder").performScrollTo().performClick()
        waitForText("Add player cards")
        composeRule.onNodeWithText("Cancel").performClick()
        waitForText("Start Game")
        addPriorCardHolder(teamName = beagles, jersey = "88", yellows = 2, reds = 1)
        composeRule.onNodeWithText("Remove").performScrollTo().performClick()
        waitForText("No prior cards recorded yet.")
        addPriorCardHolder(teamName = beagles, jersey = "88", yellows = 2, reds = 1)

        // The edited setup launches a live game carrying the visible team names forward.
        startGameFromSetup()
        composeRule.onNodeWithText(aardvarks).assertIsDisplayed()
        composeRule.onNodeWithText(beagles).assertIsDisplayed()
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

    // Test the card-specific edge cases that route through setup, Cards / TF, and Adjust Cards / TF.
    // This is still a UI-flow test; GameModel owns the detailed card-counting invariants.
    @Test
    fun cardEdgeCasesAndAdjustments() {
        openNewGameSetup()

        // Add a prior-card holder in setup and verify the compact prior-card summary renders.
        composeRule.onNodeWithText("Add Card Holder").performScrollTo().performClick()
        waitForText("Add player cards")
        composeRule.onNodeWithTag("setup-prior-card-jersey").performTextReplacement("42")
        composeRule.onAllNodesWithText("+1")[1].performClick()
        composeRule.onNodeWithText("Add").performClick()
        waitForText("Start Game")
        composeRule.onNodeWithText("Y 1  R 1").performScrollTo().assertIsDisplayed()
        startGameFromSetup()

        // A direct red without an existing yellow should record immediately.
        recordRedCard(TeamId.TEAM_ONE, "5", "Team 1 has 2 cards.")

        // A red on a player with yellow should allow the second-yellow path.
        recordYellowCard(TeamId.TEAM_TWO, "7", "Team 2 has 1 card.")
        openCardsSheet()
        composeRule.onAllNodesWithText("Red")[teamCardButtonIndex(TeamId.TEAM_TWO)].performClick()
        waitForText("Red Card")
        enterCardPlayerNumber("7")
        composeRule.onNodeWithText("Record").performClick()
        waitForText("Player Already Has Yellow")
        composeRule.onNodeWithText("Second Yellow").performClick()
        waitForText("Team 2 has 2 cards.", substring = true)
        composeRule.onNodeWithText("OK").performClick()

        // Reusing N/A for a yellow should ask whether it is the same unknown player.
        recordYellowCard(TeamId.TEAM_ONE, "", "Team 1 has 3 cards.", substring = true)
        openCardsSheet()
        composeRule.onAllNodesWithText("Yellow")[teamCardButtonIndex(TeamId.TEAM_ONE)].performClick()
        waitForText("Yellow Card")
        composeRule.onNodeWithText("N/A").performClick()
        waitForText("Unknown Player Number")
        composeRule.onNodeWithText("Yes").performClick()
        waitForText("Team 1 has 4 cards.", substring = true)
        composeRule.onNodeWithText("OK").performClick()

        // Apply a manual card correction that adds a player red, removes a player yellow, and changes a team count.
        openOtherSheet()
        composeRule.onNodeWithText("Adjust Cards / TF").performClick()
        waitForText("Adjust Cards / TF")
        composeRule.onAllNodesWithText("+1")[1].performClick()
        composeRule.onAllNodesWithText("+1")[2].performClick()
        composeRule.onAllNodesWithText("-1")[4].performClick()
        composeRule.onNodeWithText("Set").performClick()

        // The adjustment reconciles player-backed red/yellow totals through explicit prompts.
        waitForText("Add Red")
        enterCardPlayerNumber("9")
        composeRule.onNodeWithText("Record").performClick()
        waitForText("Remove Yellow")
        composeRule.onNodeWithText("#7 (Yellow 2)").performClick()
        waitForText("Undo Cards / TF Adjustment")

        // A fuller correction pass covers adding/removing player-backed cards on both teams.
        openOtherSheet()
        composeRule.onNodeWithText("Adjust Cards / TF").performClick()
        waitForText("Adjust Cards / TF")
        composeRule.onAllNodesWithText("+1")[0].performClick()
        composeRule.onAllNodesWithText("-1")[2].performClick()
        composeRule.onAllNodesWithText("+1")[5].performClick()
        composeRule.onAllNodesWithText("+1")[6].performClick()
        composeRule.onAllNodesWithText("+1")[7].performClick()
        composeRule.onNodeWithText("Set").performClick()
        waitForText("Add Yellow")
        enterCardPlayerNumber("11")
        composeRule.onNodeWithText("Record").performClick()
        waitForText("Remove Red")
        composeRule.onNodeWithText("#5 (Red 1)").performClick()
        waitForText("Add Red")
        enterCardPlayerNumber("12")
        composeRule.onNodeWithText("Record").performClick()
        waitForText("Undo Cards / TF Adjustment")

        // A final correction removes the just-added player cards and non-player team counts.
        openOtherSheet()
        composeRule.onNodeWithText("Adjust Cards / TF").performClick()
        waitForText("Adjust Cards / TF")
        composeRule.onAllNodesWithText("-1")[0].performClick()
        composeRule.onAllNodesWithText("-1")[5].performClick()
        composeRule.onAllNodesWithText("-1")[6].performClick()
        composeRule.onAllNodesWithText("-1")[7].performClick()
        composeRule.onNodeWithText("Set").performClick()
        waitForText("Remove Yellow")
        composeRule.onNodeWithText("#11 (Yellow 1)").performClick()
        waitForText("Remove Red")
        composeRule.onNodeWithText("#12 (Red 1)").performClick()
        waitForText("Undo Cards / TF Adjustment")
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

        // Manual correction dialogs should also apply their visible values.
        applyScoreAdjustment()
        applyTimeoutAdjustment()
        applyPullInfractionAdjustment()

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

    // Test the cap confirmation prompts that appear after a point ends with a due cap.
    // Each cap gets its own short game because the prompt state blocks normal live interaction.
    @Test
    fun capPromptPathways() {
        // Half cap can be applied from its confirmation prompt.
        startLiveGameWithDueCap("Half cap", "Half Cap")
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "goal")).performClick()
        waitForText("Apply half cap?")
        composeRule.onNodeWithText("Apply").performClick()
        waitForText("Undo Apply Half Cap")

        // Half cap can also be deferred from its confirmation prompt.
        returnHomeFromGame()
        startLiveGameWithDueCap("Half cap", "Half Cap")
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "goal")).performClick()
        waitForText("Apply half cap?")
        composeRule.onNodeWithText("No").performClick()
        assertLiveScreen()

        // Soft cap can be applied from its confirmation prompt.
        returnHomeFromGame()
        startLiveGameWithDueCap("Soft cap", "Soft Cap")
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "goal")).performClick()
        waitForText("Apply soft cap?")
        composeRule.onNodeWithText("Apply").performClick()
        waitForText("Undo Apply Soft Cap")

        // Soft cap can also be deferred.
        returnHomeFromGame()
        startLiveGameWithDueCap("Soft cap", "Soft Cap")
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "goal")).performClick()
        waitForText("Apply soft cap?")
        composeRule.onNodeWithText("No").performClick()
        assertLiveScreen()

        // A soft cap scheduled during halftime should say it is scheduled, not already past.
        returnHomeFromGame()
        startLiveGameWithCapDuringHalftime("Soft cap", "Soft Cap")
        openOtherSheet()
        composeRule.onNodeWithText("Start Halftime").performClick()
        waitForText("Apply soft cap?")
        waitForText("is scheduled for", substring = true)
        composeRule.onNodeWithText("No").performClick()
        waitForText("Halftime")
        composeRule.onNodeWithText("OK").performClick()

        // Hard cap can be deferred, then applied on a tied score to keep the game live.
        returnHomeFromGame()
        startLiveGameWithDueCap("Hard cap", "Hard Cap")
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "goal")).performClick()
        waitForText("Apply hard cap?")
        composeRule.onNodeWithText("No").performClick()
        assertLiveScreen()
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_TWO, "goal")).performClick()
        waitForText("Apply hard cap?")
        composeRule.onNodeWithText("Apply").performClick()
        waitForText("Undo Apply Hard Cap")
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

    private fun setStartTime(startTime: LocalTime) {
        val hour = startTime.hour % 12
        val hourText = (if (hour == 0) 12 else hour).toString()
        val minuteText = startTime.minute.toString().padStart(2, '0')
        val period = if (startTime.hour >= 12) "PM" else "AM"

        composeRule.onNodeWithText("Start time").performClick()
        waitForText("Set Start Time")
        composeRule.onNodeWithText("Hour").performTextReplacement(hourText)
        composeRule.onNodeWithText("Minute").performTextReplacement(minuteText)
        composeRule.onNodeWithText(period).performClick()
        composeRule.onNodeWithText("Set").performClick()
        waitForText("Start Game")
    }

    private fun setStartTimeToRecentPast() {
        setStartTime(LocalTime.MIDNIGHT)
    }

    private fun setStartTimeToFutureMinute() {
        setStartTime(LocalTime.now().plusMinutes(1))
    }

    private fun setCapRuleValue(
        rowLabel: String,
        dialogTitle: String,
        value: String,
        enableFromNone: Boolean = false,
    ) {
        composeRule.onNodeWithText(rowLabel).performScrollTo().performClick()
        waitForText(dialogTitle)
        if (enableFromNone) {
            composeRule.onNodeWithTag("setup-$dialogTitle-none").performClick()
        }
        composeRule.onNodeWithText("Minutes").performTextReplacement(value)
        composeRule.onNodeWithText("Set").performClick()
        waitForText("Start Game")
    }

    private fun setCapRuleToNone(rowLabel: String, dialogTitle: String) {
        composeRule.onNodeWithText(rowLabel).performScrollTo().performClick()
        waitForText(dialogTitle)
        composeRule.onNodeWithTag("setup-$dialogTitle-none").performClick()
        composeRule.onNodeWithText("Set").performClick()
        waitForText("Start Game")
    }

    private fun setTimeoutRules(timeoutsPerHalf: String, hasFloater: Boolean) {
        composeRule.onNodeWithText("Timeouts").performScrollTo().performClick()
        waitForText("Timeout Rules")
        composeRule.onNodeWithText("Timeouts per half").performTextReplacement(timeoutsPerHalf)
        if (hasFloater) {
            composeRule.onNodeWithTag("setup-timeouts-floater").performClick()
        }
        composeRule.onNodeWithText("Set").performClick()
        waitForText("Start Game")
    }

    private fun addPriorCardHolder(teamName: String, jersey: String, yellows: Int, reds: Int) {
        composeRule.onNodeWithText("Add Card Holder").performScrollTo().performClick()
        waitForText("Add player cards")
        composeRule.onNodeWithTag("setup-prior-card-team-${TeamId.TEAM_TWO.name}").performClick()
        composeRule.onNodeWithTag("setup-prior-card-jersey").performTextReplacement(jersey)
        repeat((yellows - 1).coerceAtLeast(0)) {
            composeRule.onAllNodesWithText("+1")[0].performClick()
        }
        repeat(reds.coerceAtLeast(0)) {
            composeRule.onAllNodesWithText("+1")[1].performClick()
        }
        composeRule.onNodeWithText("Add").performClick()
        waitForText("Start Game")
        composeRule.onNodeWithText("$teamName #$jersey").performScrollTo().assertIsDisplayed()
    }

    private fun startLiveGameWithDueCap(rowLabel: String, dialogTitle: String) {
        composeRule.onNodeWithText("Start New Game").performClick()
        waitForText("UltiObserver Setup")
        setStartTimeToRecentPast()
        setIntegerSetupValue("Game to", "Game To", "Points", "5")
        when (rowLabel) {
            "Half cap" -> {
                setCapRuleToNone("Soft cap", "Soft Cap")
                setCapRuleToNone("Hard cap", "Hard Cap")
            }
            "Soft cap" -> {
                setCapRuleToNone("Half cap", "Half Cap")
                setCapRuleToNone("Hard cap", "Hard Cap")
            }
            "Hard cap" -> {
                setCapRuleToNone("Half cap", "Half Cap")
                setCapRuleToNone("Soft cap", "Soft Cap")
            }
        }
        setCapRuleValue(rowLabel, dialogTitle, "0")
        startGameFromSetup()
    }

    private fun startLiveGameWithCapDuringHalftime(rowLabel: String, dialogTitle: String) {
        composeRule.onNodeWithText("Start New Game").performClick()
        waitForText("UltiObserver Setup")
        setStartTimeToFutureMinute()
        setIntegerSetupValue("Game to", "Game To", "Points", "5")
        setIntegerSetupValue("Halftime", "Halftime", "Minutes", "7")
        setCapRuleValue(rowLabel, dialogTitle, "0")
        startGameFromSetup()
    }

    private fun returnHomeFromGame() {
        pressBack()
        waitForText("Start New Game")
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

    private fun startPointWithFailedSwipeThenUnlock() {
        composeRule.onNodeWithText("Start Point").performClick()
        waitForText("Slide right to unlock")
        composeRule.onNodeWithTag("live-unlock-slider").performTouchInput {
            swipeRight(startX = centerX, endX = right)
        }
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

    private fun applyScoreAdjustment() {
        openOtherSheet()
        composeRule.onNodeWithText("Adjust Score").performClick()
        waitForText("Adjust Score")
        composeRule.onAllNodesWithText("+1")[0].performClick()
        composeRule.onAllNodesWithText("+1")[1].performClick()
        composeRule.onAllNodesWithText("-1")[0].performClick()
        composeRule.onNodeWithText("Set").performClick()
        waitForText("Undo Score Adjustment")
    }

    private fun applyTimeoutAdjustment() {
        openOtherSheet()
        composeRule.onNodeWithText("Adjust Timeouts").performClick()
        waitForText("Adjust Timeouts")
        composeRule.onAllNodesWithText("+1")[0].performClick()
        composeRule.onAllNodesWithText("+1")[1].performClick()
        composeRule.onAllNodesWithText("-1")[0].performClick()
        composeRule.onNodeWithText("Set").performClick()
        waitForText("Undo Timeout Adjustment")
    }

    private fun applyPullInfractionAdjustment() {
        openOtherSheet()
        composeRule.onNodeWithText("Adjust Pull Infractions").performClick()
        waitForText("Adjust Pull Infractions")
        composeRule.onAllNodesWithText("+1")[0].performClick()
        composeRule.onAllNodesWithText("+1")[1].performClick()
        composeRule.onAllNodesWithText("+1")[2].performClick()
        composeRule.onAllNodesWithText("+1")[3].performClick()
        composeRule.onAllNodesWithText("-1")[1].performClick()
        composeRule.onNodeWithText("Set").performClick()
        waitForText("Undo Pull Infraction Adjustment")
    }

    private fun recordYellowCard(
        team: TeamId,
        playerNumber: String,
        expectedMessage: String,
        misconductChoice: String? = null,
        expectedMisconductMessage: String? = null,
        substring: Boolean = false,
    ) {
        openCardsSheet()
        composeRule.onAllNodesWithText("Yellow")[teamCardButtonIndex(team)].performClick()
        waitForText("Yellow Card")
        if (playerNumber.isBlank()) {
            composeRule.onNodeWithText("N/A").performClick()
        } else {
            enterCardPlayerNumber(playerNumber)
            composeRule.onNodeWithText("Record").performClick()
        }

        if (misconductChoice == null) {
            waitForText(expectedMessage, substring = substring)
        } else {
            waitForText("Misconduct Penalty")
            composeRule.onNodeWithText(misconductChoice).performClick()
            waitForText(expectedMisconductMessage ?: expectedMessage, substring = true)
        }
        composeRule.onNodeWithText("OK").performClick()
    }

    private fun recordRedCard(team: TeamId, playerNumber: String, expectedMessage: String) {
        openCardsSheet()
        composeRule.onAllNodesWithText("Red")[teamCardButtonIndex(team)].performClick()
        waitForText("Red Card")
        enterCardPlayerNumber(playerNumber)
        composeRule.onNodeWithText("Record").performClick()
        waitForText(expectedMessage, substring = true)
        composeRule.onNodeWithText("OK").performClick()
    }

    private fun enterCardPlayerNumber(playerNumber: String) {
        composeRule.onNodeWithTag("card-player-number").performTextReplacement(playerNumber)
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
