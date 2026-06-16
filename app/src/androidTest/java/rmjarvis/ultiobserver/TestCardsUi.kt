package rmjarvis.ultiobserver

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/// Tests for card and technical-foul UI flows from the live game screen.
@RunWith(AndroidJUnit4::class)
class TestCardsUi : MainActivityUiTestFixtures() {
    /**
     * Test the card and technical-foul dialogs from the live screen.
     * This covers the phone-facing dialog sequence, not the full card-accounting matrix.
     */
    @Test
    fun cardsAndTechnicalFoulDialogPath() {
        startLiveGameProgrammatically()

        // The Card dialog should show the selected team, its pull role, and its current totals.
        openCardsDialog()
        composeRule.onNodeWithText("Team 1 (pulling)").assertIsDisplayed()
        waitForText("Current cards: 0 yellow, 0 red, 0 blue. Team total: 0.")
        if (shouldUsePlatformBackDismissalCoverage()) {
            pressDialogBack()
            composeRule.onAllNodesWithText("Current cards: 0 yellow, 0 red, 0 blue. Team total: 0.").assertCountEquals(0)
            openCardsDialog()
            composeRule.onNodeWithText("Team 1 (pulling)").assertIsDisplayed()
        }
        composeRule.onNodeWithText("Close").performClick()
        openCardsDialog(TeamId.TEAM_TWO)
        composeRule.onNodeWithText("Team 2 (receiving)").assertIsDisplayed()
        composeRule.onNodeWithText("Close").performClick()

        // Blue cards and technical fouls should close their dialogs and show the consequence cue.
        openCardsDialog()
        tapCardDialogAction(TeamId.TEAM_ONE, "Blue")
        waitForText("Blue Card")
        waitForText("This is Team 1's first blue card.")
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithTag("card-dialog-${TeamId.TEAM_ONE.name}-blue").assertIsDisplayed()
        tapCardDialogAction(TeamId.TEAM_ONE, "Blue")
        waitForText("This is Team 1's first blue card.")
        composeRule.onNodeWithText("OK").performClick()

        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "tech")).performClick()
        waitForText("This is Team 1's first technical foul.")
        composeRule.onNodeWithText("Cancel").performClick()

        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "tech")).performClick()
        waitForText("This is Team 1's first technical foul.")
        composeRule.onNodeWithText("OK").performClick()

        openCardsDialog(TeamId.TEAM_TWO)
        tapCardDialogAction(TeamId.TEAM_TWO, "Blue")
        waitForText("This is Team 2's first blue card.")
        composeRule.onNodeWithText("OK").performClick()

        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_TWO, "tech")).performClick()
        waitForText("This is Team 2's first technical foul.")
        composeRule.onNodeWithText("OK").performClick()

        // Yellow cards should prompt for a player number while still allowing N/A.
        openCardsDialog()
        tapCardDialogAction(TeamId.TEAM_ONE, "Yellow")
        composeRule.onNodeWithText("Yellow card").assertIsDisplayed()
        composeRule.onNodeWithText("Player number").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithTag("card-dialog-${TeamId.TEAM_ONE.name}-yellow").assertIsDisplayed()
        tapCardDialogAction(TeamId.TEAM_ONE, "Yellow")
        composeRule.onNodeWithText("Yellow card").assertIsDisplayed()
        composeRule.onNodeWithText("Record").performClick()
        waitForText("Yellow card on player N/A.\nTeam 1 has 2 total blue cards.")
        composeRule.onNodeWithText("OK").performClick()

        // A red on a player with a yellow records as a red.
        openCardsDialog()
        tapCardDialogAction(TeamId.TEAM_ONE, "Red")
        composeRule.onNodeWithText("Red card").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithTag("card-dialog-${TeamId.TEAM_ONE.name}-red").assertIsDisplayed()
        tapCardDialogAction(TeamId.TEAM_ONE, "Red")
        composeRule.onNodeWithText("Red card").assertIsDisplayed()
        composeRule.onNodeWithText("N/A").performClick()
        waitForText("The player is suspended for the rest of the tournament.", substring = true)
        assertTrue(
            composeRule.onAllNodesWithText("The player receives a game suspension.")
                .fetchSemanticsNodes()
                .isEmpty(),
        )
        waitForText("Team 1 has 4 total blue cards.", substring = true)
        composeRule.onNodeWithText("OK").performClick()

        openCardsDialog(TeamId.TEAM_TWO)
        tapCardDialogAction(TeamId.TEAM_TWO, "Yellow")
        waitForText("Yellow card")
        enterCardPlayerNumber("8")
        composeRule.onNodeWithText("Record").performClick()
        waitForText("Yellow card on player 8.\nTeam 2 has 2 total blue cards.")
        composeRule.onNodeWithText("OK").performClick()
    }

    /// The Card dialog should keep pull/receive role labels visible during halftime.
    @Test
    fun cardDialogShowsPullRolesDuringHalftime() {
        startLiveGameProgrammatically()
        composeRule.activityRule.scenario.onActivity { activity ->
            val current = activity.appViewModel.liveState!!
            activity.appViewModel.updateLiveGame(current.startHalftimeNow(System.currentTimeMillis()))
        }
        composeRule.waitForIdle()
        waitForText("Halftime")
        composeRule.onNodeWithText("OK").performClick()

        openCardsDialog()
        composeRule.onNodeWithText("Team 1 (receiving)").assertIsDisplayed()
        composeRule.onNodeWithText("Close").performClick()
        openCardsDialog(TeamId.TEAM_TWO)
        composeRule.onNodeWithText("Team 2 (pulling)").assertIsDisplayed()
    }

    /**
     * Test the card-specific edge cases that route through setup, Card dialog, and Adjust cards / techs.
     * This is still a UI-flow test; domain helpers own the detailed card-counting invariants.
     */
    @Test
    fun cardEdgeCasesAndAdjustments() {
        openNewGameSetup()

        // Add a prior-card holder in setup and verify the compact prior-card summary renders.
        openPriorCardsSetupEditor()
        composeRule.onNodeWithText("Add card holder").performClick()
        waitForText("Add previous game card holder")
        enterPriorCardJersey("42")
        composeRule.onAllNodesWithText("-1")[0].performClick()
        composeRule.onNodeWithText("Cancel").assertIsDisplayed()
        composeRule.onAllNodesWithText("+1")[1].performClick()
        composeRule.onNodeWithText("Add").performClick()
        composeRule.onNodeWithText("R 1").performScrollTo().assertIsDisplayed()
        closeSetupEditor()
        waitForText("Start game")
        composeRule.onNodeWithText("#42: R 1").performScrollTo().assertIsDisplayed()
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
        openCardsDialog()
        tapCardDialogAction(TeamId.TEAM_ONE, "Yellow")
        waitForText("Yellow card")
        composeRule.onNodeWithText("N/A").performClick()
        waitForText("Unknown player number")
        composeRule.onNodeWithText("No").performClick()
        waitForText("Team 1 has 4 total blue cards.", substring = true)
        composeRule.onNodeWithText("OK").performClick()

        // Apply a manual card correction that adds a player red, removes a player yellow, and changes a team count.
        openMoreActionsDialog()
        composeRule.onNodeWithText("Adjust cards / techs").performClick()
        waitForText("Adjust cards / techs")
        composeRule.onAllNodesWithText("+1")[1].performClick()
        composeRule.onAllNodesWithText("+1")[2].performClick()
        composeRule.onAllNodesWithText("-1")[4].performClick()
        composeRule.onNodeWithText("Set").performClick()

        // The adjustment reconciles player-backed red/yellow totals through explicit prompts.
        waitForText("Add red")
        enterCardPlayerNumber("9")
        composeRule.onNodeWithText("Record").performClick()
        waitForText("Remove yellow")
        composeRule.onNodeWithText("#7 (Yellow 2)").performClick()
        waitForText("Undo Cards / techs adjustment")

        // A small team-count-only correction covers Team 1 blue and technical-foul edits.
        openMoreActionsDialog()
        composeRule.onNodeWithText("Adjust cards / techs").performClick()
        waitForText("Adjust cards / techs")
        composeRule.onAllNodesWithText("-1")[1].performClick()
        composeRule.onAllNodesWithText("+1")[3].performClick()
        composeRule.onAllNodesWithText("-1")[3].performClick()
        composeRule.onNodeWithText("Set").performClick()
        waitForText("Undo Cards / techs adjustment")

        // A fuller correction pass covers adding/removing player-backed cards on both teams.
        openMoreActionsDialog()
        composeRule.onNodeWithText("Adjust cards / techs").performClick()
        waitForText("Adjust cards / techs")
        composeRule.onAllNodesWithText("+1")[0].performClick()
        composeRule.onAllNodesWithText("-1")[2].performClick()
        composeRule.onAllNodesWithText("+1")[4].performClick()
        composeRule.onAllNodesWithText("+1")[5].performClick()
        composeRule.onAllNodesWithText("+1")[6].performClick()
        composeRule.onAllNodesWithText("+1")[7].performClick()
        composeRule.onNodeWithText("Set").performClick()
        waitForText("Add yellow")
        enterCardPlayerNumber("11")
        composeRule.onNodeWithText("Record").performClick()
        waitForText("Remove red")
        composeRule.onNodeWithText("#5 (Red 1)").performClick()
        waitForText("Add yellow")
        enterCardPlayerNumber("14")
        composeRule.onNodeWithText("Record").performClick()
        waitForText("Add red")
        enterCardPlayerNumber("12")
        composeRule.onNodeWithText("Record").performClick()
        waitForText("Undo Cards / techs adjustment")

        // A final correction removes the just-added player cards and non-player team counts.
        openMoreActionsDialog()
        composeRule.onNodeWithText("Adjust cards / techs").performClick()
        waitForText("Adjust cards / techs")
        composeRule.onAllNodesWithText("-1")[0].performClick()
        composeRule.onAllNodesWithText("-1")[4].performClick()
        composeRule.onAllNodesWithText("-1")[5].performClick()
        composeRule.onAllNodesWithText("-1")[6].performClick()
        composeRule.onAllNodesWithText("-1")[7].performClick()
        composeRule.onNodeWithText("Set").performClick()
        waitForText("Remove yellow")
        composeRule.onNodeWithText("#11 (Yellow 1)").performClick()
        waitForText("Remove yellow")
        composeRule.onNodeWithText("#14 (Yellow 1)").performClick()
        waitForText("Remove red")
        composeRule.onNodeWithText("#12 (Red 1)").performClick()
        waitForText("Undo Cards / techs adjustment")

        // Add a clean second-yellow record after the correction matrix so summary text can show that form.
        recordYellowCard(TeamId.TEAM_TWO, "21", "Team 2 has", substring = true)
        recordYellowCard(TeamId.TEAM_TWO, "21", "Second yellow on player 21.", substring = true)

        // Removing a player-backed card can be canceled without applying a partial correction.
        openMoreActionsDialog()
        composeRule.onNodeWithText("Adjust cards / techs").performClick()
        waitForText("Adjust cards / techs")
        composeRule.onAllNodesWithText("-1")[4].performClick()
        composeRule.onNodeWithText("Set").performClick()
        waitForText("Remove yellow")
        composeRule.onAllNodesWithText("Cancel")[1].performClick()
        composeRule.onNodeWithText("Cancel").performClick()
        waitForText("Update game setup")

        // Trying to add another yellow to the maxed-out player should show the invalid assignment warning.
        openMoreActionsDialog()
        composeRule.onNodeWithText("Adjust cards / techs").performClick()
        waitForText("Adjust cards / techs")
        composeRule.onAllNodesWithText("+1")[4].performClick()
        composeRule.onNodeWithText("Set").performClick()
        waitForText("Add yellow")
        enterCardPlayerNumber("21")
        composeRule.onNodeWithText("Record").performClick()
        waitForText("Invalid card assignment")
        if (shouldUsePlatformBackDismissalCoverage()) {
            pressDialogBack()
        } else {
            composeRule.onNodeWithText("OK").performClick()
        }
        waitForText("Add yellow")
        composeRule.onNodeWithText("Record").performClick()
        waitForText("Invalid card assignment")
        composeRule.onNodeWithText("OK").performClick()
        waitForText("Add yellow")
        composeRule.onAllNodesWithText("Cancel")[1].performClick()
        composeRule.onNodeWithText("Cancel").performClick()
        waitForText("Update game setup")

        // Ending the game renders the summary forms for second-yellow and repeated-yellow records.
        openMoreActionsDialog()
        composeRule.onNodeWithText("End game").performClick()
        waitForText("Game over")
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
                playerRecordWithCards(UNKNOWN_PLAYER_NUMBER, yellows = 1),
                playerRecordWithCards("10", yellows = 1),
            ),
            teamTwoCards = listOf(playerRecordWithCards("6", yellows = 1, reds = 1)),
        )
        composeRule.activityRule.scenario.onActivity { activity ->
            val current = activity.appViewModel.liveState!!
            activity.appViewModel.updateLiveGame(current.beginLivePoint(0L))
        }
        composeRule.waitForIdle()

        // A second yellow on N/A can be recorded as the same unknown player.
        openCardsDialog()
        composeRule.onAllNodesWithText("Team 1").onFirst().assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("Team 1 (pulling)").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Team 2 (receiving)").fetchSemanticsNodes().isEmpty())
        tapCardDialogAction(TeamId.TEAM_ONE, "Yellow")
        waitForText("Yellow card")
        composeRule.onNodeWithText("N/A").performClick()
        waitForText("Unknown player number")
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithTag("card-dialog-${TeamId.TEAM_ONE.name}-yellow").assertIsDisplayed()
        tapCardDialogAction(TeamId.TEAM_ONE, "Yellow")
        waitForText("Yellow card")
        composeRule.onNodeWithText("N/A").performClick()
        waitForText("Unknown player number")
        composeRule.onNodeWithText("Yes").performClick()
        waitForText("Misconduct penalty")
        tapBackFromMisconductODChoice()
        waitForText("Unknown player number")
        composeRule.onNodeWithText("Yes").performClick()
        waitForText("Misconduct penalty")
        composeRule.onNodeWithText("Offense").performClick()
        waitForText("Team 1 moves the disc to the reverse brick in the end zone they are defending.", substring = true)
        composeRule.onNodeWithText("OK").performClick()
        waitForText("Start misconduct countdown")

        // Back from a blue-card misconduct choice should cancel that card and return to Card.
        if (shouldUsePlatformBackDismissalCoverage()) {
            openCardsDialog()
            tapCardDialogAction(TeamId.TEAM_ONE, "Blue")
            waitForText("Blue Card")
            waitForText("Was this against the offense or defense?", substring = true)
            pressDialogBack()
            composeRule.onNodeWithTag("card-dialog-${TeamId.TEAM_ONE.name}-blue").assertIsDisplayed()
            composeRule.onAllNodesWithText("Misconduct penalty").assertCountEquals(0)
            composeRule.onNodeWithText("Close").performClick()
        }
        openCardsDialog()
        tapCardDialogAction(TeamId.TEAM_ONE, "Blue")
        waitForText("Blue Card")
        waitForText("Was this against the offense or defense?", substring = true)
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithTag("card-dialog-${TeamId.TEAM_ONE.name}-blue").assertIsDisplayed()
        composeRule.onAllNodesWithText("Misconduct penalty").assertCountEquals(0)
        tapCardDialogAction(TeamId.TEAM_ONE, "Blue")
        waitForText("Was this against the offense or defense?", substring = true)
        composeRule.onNodeWithText("Defense").performClick()
        waitForText("Team 2 may move the disc to the brick mark nearest the end zone they are attacking.", substring = true)
        composeRule.onAllNodesWithText("Back").onLast().performClick()
        waitForText("Blue Card")
        waitForText("Was this against the offense or defense?", substring = true)
        composeRule.onNodeWithText("Defense").performClick()
        waitForText("Team 2 may move the disc to the brick mark nearest the end zone they are attacking.", substring = true)
        composeRule.onNodeWithText("OK").performClick()
        waitForText("Start misconduct countdown")

        // A threshold technical foul should ask offense/defense immediately, with Cancel undoing the pending Tech.
        composeRule.activityRule.scenario.onActivity { activity ->
            val current = activity.appViewModel.liveState!!
            activity.appViewModel.updateLiveGame(
                current.copy(
                    countdown = null,
                    pendingMisconductCountdown = false,
                    teamOne = current.teamOne.copy(technicalFouls = 2),
                )
            )
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "tech")).performClick()
        waitForText("Technical Foul")
        waitForText("Was this against the offense or defense?", substring = true)
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onAllNodesWithText("Misconduct penalty").assertCountEquals(0)
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "tech")).performClick()
        waitForText("Was this against the offense or defense?", substring = true)
        composeRule.onNodeWithText("Offense").performClick()
        waitForText("Team 1 moves the disc to the reverse brick in the end zone they are defending.", substring = true)
        composeRule.onAllNodesWithText("Back").onLast().performClick()
        waitForText("Was this against the offense or defense?", substring = true)
        composeRule.onNodeWithText("Defense").performClick()
        waitForText("Team 2 may move the disc to the brick mark nearest the end zone they are attacking.", substring = true)
        composeRule.onNodeWithText("OK").performClick()
        waitForText("Start misconduct countdown")

        // Back from a red-card N/A misconduct choice should restore a blank number dialog.
        openCardsDialog(TeamId.TEAM_TWO)
        tapCardDialogAction(TeamId.TEAM_TWO, "Red")
        waitForText("Red card")
        composeRule.onNodeWithText("N/A").performClick()
        waitForText("Misconduct penalty")
        tapBackFromMisconductODChoice()
        waitForText("Red card")
        assertEquals(
            "",
            composeRule.onNodeWithTag("card-player-number")
                .fetchSemanticsNode()
                .config[SemanticsProperties.EditableText]
                .text,
        )
        composeRule.onNodeWithText("N/A").performClick()
        waitForText("Misconduct penalty")
        composeRule.onNodeWithText("Defense").performClick()
        waitForText("Team 1 may move the disc to the brick mark nearest the end zone they are attacking.", substring = true)
        composeRule.onNodeWithText("OK").performClick()
        waitForText("Start misconduct countdown")

        // A player with both a yellow and red has no valid additional red card.
        openCardsDialog(TeamId.TEAM_TWO)
        tapCardDialogAction(TeamId.TEAM_TWO, "Red")
        waitForText("Red card")
        enterCardPlayerNumber("6")
        composeRule.onNodeWithText("Record").performClick()
        waitForText("maximum valid card combination", substring = true)
        if (shouldUsePlatformBackDismissalCoverage()) {
            pressDialogBack()
        } else {
            composeRule.onNodeWithText("OK").performClick()
        }
        composeRule.onNodeWithTag("card-dialog-${TeamId.TEAM_TWO.name}-red").assertIsDisplayed()
        tapCardDialogAction(TeamId.TEAM_TWO, "Red")
        waitForText("Red card")
        enterCardPlayerNumber("6")
        composeRule.onNodeWithText("Record").performClick()
        waitForText("maximum valid card combination", substring = true)
        composeRule.onNodeWithText("OK").performClick()
    }
}
