package rmjarvis.ultiobserver

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.swipeRight
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/// Tests for card and technical-foul UI flows from the live game screen.
@RunWith(AndroidJUnit4::class)
class TestCardsUi : MainActivityUiTestFixtures() {
    /**
     * Test the card and technical-foul bottom sheet from the live screen.
     * This covers the phone-facing dialog sequence, not the full card-accounting matrix.
     */
    @Test
    fun cardsAndTechnicalFoulSheetPath() {
        startLiveGameProgrammatically()

        // The Cards / TF sheet should show both team sections with their pull roles.
        openCardsSheet()
        composeRule.onNodeWithText("Team 1 (pulling)").assertIsDisplayed()
        composeRule.onNodeWithText("Team 2 (receiving)").assertIsDisplayed()
        if (shouldUsePlatformBackDismissalCoverage()) {
            pressDialogBack()
            composeRule.onAllNodesWithText("Cards / Technical Fouls").assertCountEquals(0)
            openCardsSheet()
            composeRule.onNodeWithText("Team 1 (pulling)").assertIsDisplayed()
            composeRule.onNodeWithText("Team 2 (receiving)").assertIsDisplayed()
        }

        // Blue cards and technical fouls should close the sheet and show the consequence cue.
        composeRule.onAllNodesWithText("Blue").onFirst().performClick()
        waitForText("Team 1 has 1 blue card.")
        composeRule.onNodeWithText("OK").performClick()

        openCardsSheet()
        composeRule.onAllNodesWithText("Tech").onFirst().performClick()
        waitForText("Team 1 has 1 technical foul.")
        composeRule.onNodeWithText("OK").performClick()

        openCardsSheet()
        tapCardSheetAction(TeamId.TEAM_TWO, "Blue")
        waitForText("Team 2 has 1 blue card.")
        composeRule.onNodeWithText("OK").performClick()

        openCardsSheet()
        tapCardSheetAction(TeamId.TEAM_TWO, "Tech")
        waitForText("Team 2 has 1 technical foul.")
        composeRule.onNodeWithText("OK").performClick()

        // Yellow cards should prompt for a player number while still allowing N/A.
        openCardsSheet()
        composeRule.onAllNodesWithText("Yellow").onFirst().performClick()
        composeRule.onNodeWithText("Yellow Card").assertIsDisplayed()
        composeRule.onNodeWithText("Player number").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        waitForText("Cards / Technical Fouls")
        composeRule.onAllNodesWithText("Yellow").onFirst().performClick()
        composeRule.onNodeWithText("Yellow Card").assertIsDisplayed()
        composeRule.onNodeWithText("Record").performClick()
        waitForText("Yellow card on player N/A.\nTeam 1 has 2 total blue cards.")
        composeRule.onNodeWithText("OK").performClick()

        // A red on a player with a yellow records as a red.
        openCardsSheet()
        composeRule.onAllNodesWithText("Red").onFirst().performClick()
        composeRule.onNodeWithText("Red Card").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        waitForText("Cards / Technical Fouls")
        composeRule.onAllNodesWithText("Red").onFirst().performClick()
        composeRule.onNodeWithText("Red Card").assertIsDisplayed()
        composeRule.onNodeWithText("N/A").performClick()
        waitForText("The player is suspended for the rest of the tournament.", substring = true)
        assertTrue(
            composeRule.onAllNodesWithText("The player receives a game suspension.")
                .fetchSemanticsNodes()
                .isEmpty(),
        )
        waitForText("Team 1 has 4 total blue cards.", substring = true)
        composeRule.onNodeWithText("OK").performClick()

        openCardsSheet()
        tapCardSheetAction(TeamId.TEAM_TWO, "Yellow")
        waitForText("Yellow Card")
        enterCardPlayerNumber("8")
        composeRule.onNodeWithText("Record").performClick()
        waitForText("Yellow card on player 8.\nTeam 2 has 2 total blue cards.")
        composeRule.onNodeWithText("OK").performClick()
    }

    /// The Cards / TF sheet should keep pull/receive role labels visible during halftime.
    @Test
    fun cardsSheetShowsPullRolesDuringHalftime() {
        startLiveGameProgrammatically()
        composeRule.activityRule.scenario.onActivity { activity ->
            val current = activity.appViewModel.liveState!!
            activity.appViewModel.updateLiveGame(current.startHalftimeNow(System.currentTimeMillis()))
        }
        composeRule.waitForIdle()
        waitForText("Halftime")
        composeRule.onNodeWithText("OK").performClick()

        openCardsSheet()
        composeRule.onNodeWithText("Team 1 (receiving)").assertIsDisplayed()
        composeRule.onNodeWithText("Team 2 (pulling)").assertIsDisplayed()
    }

    /**
     * Test the card-specific edge cases that route through setup, Cards / TF, and Adjust Cards / TF.
     * This is still a UI-flow test; domain helpers own the detailed card-counting invariants.
     */
    @Test
    fun cardEdgeCasesAndAdjustments() {
        openNewGameSetup()

        // Add a prior-card holder in setup and verify the compact prior-card summary renders.
        openPriorCardsSetupEditor()
        composeRule.onNodeWithText("Add Card Holder").performScrollTo().performClick()
        waitForText("Add player cards")
        enterPriorCardJersey("42")
        composeRule.onAllNodesWithText("+1")[1].performClick()
        composeRule.onNodeWithText("Add").performClick()
        composeRule.onNodeWithText("Y 1  R 1").performScrollTo().assertIsDisplayed()
        closeSetupEditor()
        waitForText("Start Game")
        composeRule.onNodeWithText("1 player carries cards.").performScrollTo().assertIsDisplayed()
        startGameFromSetup()

        // A red without an existing yellow should record immediately.
        recordRedCard(
            TeamId.TEAM_ONE,
            "5",
            "Red card on player 5.\nPlayer 5 receives a game suspension.\nTeam 1 has 2 total blue cards.",
        )

        // A second yellow comes from issuing another yellow, not from pressing Red.
        recordYellowCard(TeamId.TEAM_TWO, "7", "Yellow card on player 7.\nTeam 2 has 1 blue card.")
        recordYellowCard(TeamId.TEAM_TWO, "7", "Second yellow on player 7.", substring = true)

        // Reusing N/A for a yellow should support recording a different unknown player.
        recordYellowCard(TeamId.TEAM_ONE, "", "Team 1 has 3 total blue cards.", substring = true)
        openCardsSheet()
        tapCardSheetAction(TeamId.TEAM_ONE, "Yellow")
        waitForText("Yellow Card")
        composeRule.onNodeWithText("N/A").performClick()
        waitForText("Unknown Player Number")
        composeRule.onNodeWithText("No").performClick()
        waitForText("Team 1 has 4 total blue cards.", substring = true)
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

        // A small team-count-only correction covers Team 1 blue and technical-foul edits.
        openOtherSheet()
        composeRule.onNodeWithText("Adjust Cards / TF").performClick()
        waitForText("Adjust Cards / TF")
        composeRule.onAllNodesWithText("-1")[1].performClick()
        composeRule.onAllNodesWithText("+1")[3].performClick()
        composeRule.onAllNodesWithText("-1")[3].performClick()
        composeRule.onNodeWithText("Set").performClick()
        waitForText("Undo Cards / TF Adjustment")

        // A fuller correction pass covers adding/removing player-backed cards on both teams.
        openOtherSheet()
        composeRule.onNodeWithText("Adjust Cards / TF").performClick()
        waitForText("Adjust Cards / TF")
        composeRule.onAllNodesWithText("+1")[0].performClick()
        composeRule.onAllNodesWithText("-1")[2].performClick()
        composeRule.onAllNodesWithText("+1")[4].performClick()
        composeRule.onAllNodesWithText("+1")[5].performClick()
        composeRule.onAllNodesWithText("+1")[6].performClick()
        composeRule.onAllNodesWithText("+1")[7].performClick()
        composeRule.onNodeWithText("Set").performClick()
        waitForText("Add Yellow")
        enterCardPlayerNumber("11")
        composeRule.onNodeWithText("Record").performClick()
        waitForText("Remove Red")
        composeRule.onNodeWithText("#5 (Red 1)").performClick()
        waitForText("Add Yellow")
        enterCardPlayerNumber("14")
        composeRule.onNodeWithText("Record").performClick()
        waitForText("Add Red")
        enterCardPlayerNumber("12")
        composeRule.onNodeWithText("Record").performClick()
        waitForText("Undo Cards / TF Adjustment")

        // A final correction removes the just-added player cards and non-player team counts.
        openOtherSheet()
        composeRule.onNodeWithText("Adjust Cards / TF").performClick()
        waitForText("Adjust Cards / TF")
        composeRule.onAllNodesWithText("-1")[0].performClick()
        composeRule.onAllNodesWithText("-1")[4].performClick()
        composeRule.onAllNodesWithText("-1")[5].performClick()
        composeRule.onAllNodesWithText("-1")[6].performClick()
        composeRule.onAllNodesWithText("-1")[7].performClick()
        composeRule.onNodeWithText("Set").performClick()
        waitForText("Remove Yellow")
        composeRule.onNodeWithText("#11 (Yellow 1)").performClick()
        waitForText("Remove Yellow")
        composeRule.onNodeWithText("#14 (Yellow 1)").performClick()
        waitForText("Remove Red")
        composeRule.onNodeWithText("#12 (Red 1)").performClick()
        waitForText("Undo Cards / TF Adjustment")

        // Add a clean second-yellow record after the correction matrix so summary text can show that form.
        recordYellowCard(TeamId.TEAM_TWO, "21", "Team 2 has", substring = true)
        recordYellowCard(TeamId.TEAM_TWO, "21", "Second yellow on player 21.", substring = true)

        // Removing a player-backed card can be canceled without applying a partial correction.
        openOtherSheet()
        composeRule.onNodeWithText("Adjust Cards / TF").performClick()
        waitForText("Adjust Cards / TF")
        composeRule.onAllNodesWithText("-1")[4].performClick()
        composeRule.onNodeWithText("Set").performClick()
        waitForText("Remove Yellow")
        composeRule.onAllNodesWithText("Cancel")[1].performClick()
        composeRule.onNodeWithText("Cancel").performClick()
        waitForText("Update Game Setup")

        // Trying to add another yellow to the maxed-out player should show the invalid assignment warning.
        openOtherSheet()
        composeRule.onNodeWithText("Adjust Cards / TF").performClick()
        waitForText("Adjust Cards / TF")
        composeRule.onAllNodesWithText("+1")[4].performClick()
        composeRule.onNodeWithText("Set").performClick()
        waitForText("Add Yellow")
        enterCardPlayerNumber("21")
        composeRule.onNodeWithText("Record").performClick()
        waitForText("Invalid Card Assignment")
        if (shouldUsePlatformBackDismissalCoverage()) {
            pressDialogBack()
        } else {
            composeRule.onNodeWithText("OK").performClick()
        }
        waitForText("Add Yellow")
        composeRule.onNodeWithText("Record").performClick()
        waitForText("Invalid Card Assignment")
        composeRule.onNodeWithText("OK").performClick()
        waitForText("Add Yellow")
        composeRule.onAllNodesWithText("Cancel")[1].performClick()
        composeRule.onNodeWithText("Cancel").performClick()
        waitForText("Update Game Setup")

        // Ending the game renders the summary forms for second-yellow and repeated-yellow records.
        openOtherSheet()
        composeRule.onNodeWithText("End Game").performClick()
        waitForText("Game Over")
        composeRule.onNodeWithText("OK").performClick()
        waitForText("#21: Two yellow cards")
        waitForText("N/A: Two yellow cards")
    }

    /// Test card dialogs that require follow-up choices for already-carded players.
    @Test
    fun repeatedPlayerCardChoiceDialogs() {
        startLiveGameProgrammatically()
        seedInGamePlayerCardsProgrammatically(
            teamOneCards = listOf(
                InGamePlayerCardRecord(UNKNOWN_PLAYER_NUMBER, yellows = 1),
                InGamePlayerCardRecord("10", yellows = 1),
            ),
            teamTwoCards = listOf(InGamePlayerCardRecord("6", yellows = 1, reds = 1)),
        )
        composeRule.activityRule.scenario.onActivity { activity ->
            val current = activity.appViewModel.liveState!!
            activity.appViewModel.updateLiveGame(current.beginLivePoint(0L))
        }
        composeRule.waitForIdle()

        // A second yellow on N/A can be recorded as the same unknown player.
        openCardsSheet()
        composeRule.onAllNodesWithText("Team 1").onFirst().assertIsDisplayed()
        composeRule.onAllNodesWithText("Team 2").onFirst().assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("Team 1 (pulling)").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Team 2 (receiving)").fetchSemanticsNodes().isEmpty())
        tapCardSheetAction(TeamId.TEAM_ONE, "Yellow")
        waitForText("Yellow Card")
        composeRule.onNodeWithText("N/A").performClick()
        waitForText("Unknown Player Number")
        composeRule.onNodeWithText("Cancel").performClick()
        waitForText("Cards / Technical Fouls")
        tapCardSheetAction(TeamId.TEAM_ONE, "Yellow")
        waitForText("Yellow Card")
        composeRule.onNodeWithText("N/A").performClick()
        waitForText("Unknown Player Number")
        composeRule.onNodeWithText("Yes").performClick()
        waitForText("Misconduct Penalty")
        tapBackFromMisconductODChoice()
        waitForText("Unknown Player Number")
        composeRule.onNodeWithText("Yes").performClick()
        waitForText("Misconduct Penalty")
        composeRule.onNodeWithText("Offense").performClick()
        waitForText("Disc moves to the reverse brick in the end zone being defended.", substring = true)
        composeRule.onNodeWithText("OK").performClick()
        waitForText("Start Misconduct Countdown")

        // Back from a blue-card misconduct choice should cancel that card and return to Cards / TF.
        openCardsSheet()
        tapCardSheetAction(TeamId.TEAM_ONE, "Blue")
        waitForText("Misconduct Penalty")
        tapBackFromMisconductODChoice()
        waitForText("Cards / Technical Fouls")
        composeRule.onAllNodesWithText("Misconduct Penalty").assertCountEquals(0)
        tapCardSheetAction(TeamId.TEAM_ONE, "Blue")
        waitForText("Misconduct Penalty")
        composeRule.onNodeWithText("Defense").performClick()
        waitForText("Disc moves to the brick nearest the attacking end zone.", substring = true)
        composeRule.onNodeWithText("OK").performClick()
        waitForText("Start Misconduct Countdown")

        // Back from a red-card N/A misconduct choice should restore a blank number dialog.
        openCardsSheet()
        tapCardSheetAction(TeamId.TEAM_TWO, "Red")
        waitForText("Red Card")
        composeRule.onNodeWithText("N/A").performClick()
        waitForText("Misconduct Penalty")
        tapBackFromMisconductODChoice()
        waitForText("Red Card")
        assertEquals(
            "",
            composeRule.onNodeWithTag("card-player-number")
                .fetchSemanticsNode()
                .config[SemanticsProperties.EditableText]
                .text,
        )
        composeRule.onNodeWithText("N/A").performClick()
        waitForText("Misconduct Penalty")
        composeRule.onNodeWithText("Defense").performClick()
        waitForText("Disc moves to the brick nearest the attacking end zone.", substring = true)
        composeRule.onNodeWithText("OK").performClick()
        waitForText("Start Misconduct Countdown")

        // A player with both a yellow and red has no valid additional red card.
        openCardsSheet()
        tapCardSheetAction(TeamId.TEAM_TWO, "Red")
        waitForText("Red Card")
        enterCardPlayerNumber("6")
        composeRule.onNodeWithText("Record").performClick()
        waitForText("maximum valid card combination", substring = true)
        if (shouldUsePlatformBackDismissalCoverage()) {
            pressDialogBack()
        } else {
            composeRule.onNodeWithText("OK").performClick()
        }
        waitForText("Cards / Technical Fouls")
        tapCardSheetAction(TeamId.TEAM_TWO, "Red")
        waitForText("Red Card")
        enterCardPlayerNumber("6")
        composeRule.onNodeWithText("Record").performClick()
        waitForText("maximum valid card combination", substring = true)
        composeRule.onNodeWithText("OK").performClick()
    }
}
