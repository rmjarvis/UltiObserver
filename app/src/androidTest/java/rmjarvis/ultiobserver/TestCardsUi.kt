package rmjarvis.ultiobserver

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
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
        waitForText("Current cards:")
        waitForText("0 yellow")
        waitForText("0 red")
        waitForText("0 blue")
        waitForText("Team total: 0")
        composeRule.onNodeWithText("No existing cards").assertIsDisplayed()
        if (shouldUsePlatformBackDismissalCoverage()) {
            pressDialogBack()
            composeRule.onAllNodesWithText("Current cards:").assertCountEquals(0)
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

        // Yellow cards should require at least one player identity field before recording.
        openCardsDialog()
        tapCardDialogAction(TeamId.TEAM_ONE, "Yellow")
        composeRule.onNodeWithText("Yellow card").assertIsDisplayed()
        composeRule.onNodeWithText("Player number").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithTag("card-dialog-${TeamId.TEAM_ONE.name}-yellow").assertIsDisplayed()
        tapCardDialogAction(TeamId.TEAM_ONE, "Yellow")
        composeRule.onNodeWithText("Yellow card").assertIsDisplayed()
        composeRule.onNodeWithText("Record").performClick()
        waitForText("Enter a player number or name before recording this card.")
        composeRule.onNodeWithText("OK").performClick()
        enterCardPlayerNumber("4")
        composeRule.onNodeWithText("Record").performClick()
        waitForText("Yellow card on player 4.\nTeam 1 has 2 total blue cards.")
        composeRule.onNodeWithText("OK").performClick()

        // A red on a player with a yellow records as a red.
        openCardsDialog()
        tapCardDialogAction(TeamId.TEAM_ONE, "Red")
        composeRule.onNodeWithText("Red card").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithTag("card-dialog-${TeamId.TEAM_ONE.name}-red").assertIsDisplayed()
        tapCardDialogAction(TeamId.TEAM_ONE, "Red")
        composeRule.onNodeWithText("Red card").assertIsDisplayed()
        enterCardPlayerNumber("4")
        composeRule.onNodeWithText("Record").performClick()
        waitForText("Player 4 is suspended for the rest of the tournament.", substring = true)
        assertTrue(
            composeRule.onAllNodesWithText("Player 4 receives a game suspension.")
                .fetchSemanticsNodes()
                .isEmpty(),
        )
        waitForText("Team 1 has 4 total blue cards.", substring = true)
        composeRule.onNodeWithText("OK").performClick()

        // Editing a red card to add a reason should not repeat the suspension notice.
        openCardsDialog()
        composeRule.onNodeWithText("Edit existing cards").performClick()
        waitForText("Edit existing cards")
        composeRule.onAllNodes(hasContentDescription("Edit", substring = true))[1].performClick()
        waitForText("Edit red card")
        composeRule.onNodeWithText("Reason").performClick()
        waitForText("Red card reason")
        composeRule.onNodeWithText("Egregious dangerous play").performClick()
        composeRule.onNodeWithText("Set").performClick()
        composeRule.onNodeWithText("Record").performClick()
        composeRule.onAllNodesWithText("Team 1 #4 now has a red card and has been suspended.").assertCountEquals(0)
        waitForText("Edit existing cards")
        composeRule.onNodeWithText("Done").performClick()
        waitForText("Undo Edit red on #4 of Team 1")

        openCardsDialog(TeamId.TEAM_TWO)
        tapCardDialogAction(TeamId.TEAM_TWO, "Yellow")
        waitForText("Yellow card")
        enterCardPlayerNumber("8")
        composeRule.onNodeWithTag("card-player-name").performTextReplacement("Alex Cutter")
        composeRule.onNodeWithText("Reason").performClick()
        waitForText("Yellow card reason")
        composeRule.onNodeWithText("Other").performClick()
        composeRule.onNodeWithTag("card-other-reason").performTextReplacement("Sideline language")
        composeRule.onNodeWithTag("card-reason-details").performTextReplacement("after warning")
        composeRule.onNodeWithText("Set").performClick()
        composeRule.onNodeWithText("Sideline language: after warning").assertIsDisplayed()
        composeRule.onNodeWithText("Sideline language: after warning").performClick()
        waitForText("Yellow card reason")
        assertEquals(
            "Sideline language",
            composeRule.onNodeWithTag("card-other-reason")
                .fetchSemanticsNode()
                .config[SemanticsProperties.EditableText]
                .text,
        )
        assertEquals(
            "after warning",
            composeRule.onNodeWithTag("card-reason-details")
                .fetchSemanticsNode()
                .config[SemanticsProperties.EditableText]
                .text,
        )
        composeRule.onNodeWithText("Set").performClick()
        composeRule.onNodeWithText("Record").performClick()
        waitForText("Yellow card on #8 Alex Cutter.\nTeam 2 has 2 total blue cards.")
        composeRule.onNodeWithText("OK").performClick()

        openCardsDialog(TeamId.TEAM_TWO)
        composeRule.onNodeWithText("Edit existing cards").performClick()
        waitForText("Edit existing cards")
        composeRule.onNodeWithText("#8 Alex Cutter").assertIsDisplayed()
        composeRule.onNodeWithText("Yellow card").assertIsDisplayed()
        composeRule.onAllNodes(hasContentDescription("Edit", substring = true)).assertCountEquals(1)
        composeRule.onAllNodes(hasContentDescription("Remove", substring = true)).assertCountEquals(0)
        composeRule.onNodeWithText("Done").performClick()
        composeRule.onAllNodesWithText("Edit existing cards").assertCountEquals(0)
        openCardsDialog(TeamId.TEAM_TWO)
        tapCardDialogAction(TeamId.TEAM_TWO, "Red")
        waitForText("#8 Alex Cutter (Y 1)")
        composeRule.onNodeWithText("Cancel").performClick()
    }

    /// The live card dialog warns before treating a same-number, different-name entry as a different player.
    @Test
    fun sameNumberDifferentNameWarningForLivePlayerCards() {
        startLiveGameProgrammatically()
        seedInGamePlayerCardsProgrammatically(
            teamTwoCards = listOf(playerRecordWithCards("6", yellows = 1, playerName = "Alex Cutter")),
        )

        openCardsDialog(TeamId.TEAM_TWO)
        tapCardDialogAction(TeamId.TEAM_TWO, "Yellow")
        waitForText("Yellow card")
        enterCardPlayerNumber("6")
        composeRule.onNodeWithTag("card-player-name").performTextReplacement("Bob Cutter")
        composeRule.onNodeWithText("Record").performClick()

        waitForText("Same number, different names")
        waitForText("#6 Alex Cutter is already listed. Record #6 Bob Cutter as a different player with the same number?")
        composeRule.onNodeWithText("Cancel").performClick()
        waitForText("Yellow card")

        composeRule.onNodeWithText("Record").performClick()
        waitForText("Same number, different names")
        composeRule.onNodeWithText("Record").performClick()
        waitForText("Yellow card on #6 Bob Cutter.\nTeam 2 has 2 total blue cards.")
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

        // Apply a manual card correction that adds a player red, removes a player yellow, and changes team counts.
        openMoreActionsDialog()
        composeRule.onNodeWithText("Adjust cards / techs").performClick()
        waitForText("Adjust cards / techs")
        composeRule.onAllNodesWithText("+1")[0].performClick()
        composeRule.onAllNodesWithText("+1")[1].performClick()
        composeRule.onAllNodesWithText("Add red").onFirst().performClick()
        waitForText("Add red card")
        enterCardPlayerNumber("9")
        composeRule.onNodeWithText("Record").performClick()
        waitForText("Team 1 #9 now has a red card and has been suspended.")
        composeRule.onNodeWithText("OK").performClick()
        composeRule.onNodeWithTag("cards-adjust-team-two-edit-existing").performScrollTo().performClick()
        waitForText("Edit existing cards")
        composeRule.onAllNodes(hasContentDescription("Remove", substring = true)).onFirst().performClick()
        waitForText("Remove card?")
        composeRule.onAllNodesWithText("Remove").onLast().performClick()
        composeRule.onAllNodesWithText("Back").onLast().performClick()
        composeRule.onNodeWithText("Done").performClick()
        waitForText("Undo Adjust blue card/tech counts")

        // Add a clean second-yellow record after the correction matrix so summary text can show that form.
        recordYellowCard(TeamId.TEAM_TWO, "21", "Team 2 has", substring = true)
        recordYellowCard(TeamId.TEAM_TWO, "21", "Second yellow on player 21.", substring = true)

        // Removing a player-backed card can be canceled without applying a partial correction.
        openMoreActionsDialog()
        composeRule.onNodeWithText("Adjust cards / techs").performClick()
        waitForText("Adjust cards / techs")
        composeRule.onNodeWithTag("cards-adjust-team-two-edit-existing").performScrollTo().performClick()
        waitForText("Edit existing cards")
        composeRule.onAllNodes(hasContentDescription("Remove", substring = true)).onFirst().performClick()
        waitForText("Remove card?")
        composeRule.onAllNodesWithText("Cancel").onLast().performClick()
        composeRule.onAllNodesWithText("Back").onLast().performClick()
        composeRule.onNodeWithText("Cancel").performClick()
        waitForText("Update game setup")

        // Trying to add another yellow to the maxed-out player should show the invalid assignment warning.
        openMoreActionsDialog()
        composeRule.onNodeWithText("Adjust cards / techs").performClick()
        waitForText("Adjust cards / techs")
        composeRule.onAllNodesWithText("Add yellow")[1].performClick()
        waitForText("Add yellow card")
        enterCardPlayerNumber("21")
        composeRule.onNodeWithText("Record").performClick()
        waitForText("Invalid card assignment")
        if (shouldUsePlatformBackDismissalCoverage()) {
            pressDialogBack()
        } else {
            composeRule.onNodeWithText("OK").performClick()
        }
        waitForText("Add yellow card")
        composeRule.onNodeWithText("Record").performClick()
        waitForText("Invalid card assignment")
        composeRule.onNodeWithText("OK").performClick()
        waitForText("Add yellow card")
        composeRule.onAllNodesWithText("Cancel").onLast().performClick()
        composeRule.onNodeWithText("Cancel").performClick()
        waitForText("Update game setup")

        // Ending the game renders the summary forms for second-yellow and repeated-yellow records.
        openMoreActionsDialog()
        composeRule.onNodeWithText("End game").performClick()
        waitForText("Game over")
        composeRule.onNodeWithText("OK").performClick()
        waitForText("#21: Yellow card")
    }

    /// Test card dialogs that require follow-up choices for already-carded players.
    @Test
    fun repeatedPlayerCardChoiceDialogs() {
        startLiveGameProgrammatically()
        seedInGamePlayerCardsProgrammatically(
            teamOneCards = listOf(
                playerRecordWithCards("9", yellows = 1),
                playerRecordWithCards("10", yellows = 1),
            ),
            teamTwoCards = listOf(playerRecordWithCards("6", yellows = 1, reds = 1)),
        )
        composeRule.activityRule.scenario.onActivity { activity ->
            val current = activity.appViewModel.liveState!!
            activity.appViewModel.updateLiveGame(current.beginLivePoint(0L))
        }
        composeRule.waitForIdle()

        // A second yellow can restore the player entry after backing out of misconduct choice.
        openCardsDialog()
        composeRule.onAllNodesWithText("Team 1").onFirst().assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText("Team 1 (pulling)").fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText("Team 2 (receiving)").fetchSemanticsNodes().isEmpty())
        tapCardDialogAction(TeamId.TEAM_ONE, "Yellow")
        waitForText("Yellow card")
        enterCardPlayerNumber("9")
        composeRule.onNodeWithText("Record").performClick()
        waitForText("Misconduct penalty")
        tapBackFromMisconductODChoice()
        waitForText("Yellow card")
        assertEquals(
            "9",
            composeRule.onNodeWithTag("card-player-number")
                .fetchSemanticsNode()
                .config[SemanticsProperties.EditableText]
                .text,
        )
        composeRule.onNodeWithText("Record").performClick()
        waitForText("Misconduct penalty")
        composeRule.onNodeWithText("Offense").performClick()
        waitForText("Team 1 moves the disc to the reverse brick in the end zone they are defending.", substring = true)
        composeRule.onNodeWithText("OK").performClick()
        waitForText("Start misconduct countdown")

        // A third yellow for the same player should be rejected without crashing.
        openCardsDialog()
        tapCardDialogAction(TeamId.TEAM_ONE, "Yellow")
        waitForText("Yellow card")
        enterCardPlayerNumber("9")
        composeRule.onNodeWithText("Record").performClick()
        waitForText("Team 1 #9 already has two yellow cards and has been suspended.")
        composeRule.onNodeWithText("OK").performClick()
        composeRule.onNodeWithText("Yellow card").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()

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

        // Back from a red-card misconduct choice should restore the entered player number.
        openCardsDialog(TeamId.TEAM_TWO)
        tapCardDialogAction(TeamId.TEAM_TWO, "Red")
        waitForText("Red card")
        enterCardPlayerNumber("11")
        composeRule.onNodeWithText("Record").performClick()
        waitForText("Misconduct penalty")
        tapBackFromMisconductODChoice()
        waitForText("Red card")
        assertEquals(
            "11",
            composeRule.onNodeWithTag("card-player-number")
                .fetchSemanticsNode()
                .config[SemanticsProperties.EditableText]
                .text,
        )
        composeRule.onNodeWithText("Record").performClick()
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
        waitForText("Team 2 #6 already has a red card and has been suspended.")
        if (shouldUsePlatformBackDismissalCoverage()) {
            pressDialogBack()
        } else {
            composeRule.onNodeWithText("OK").performClick()
        }
        composeRule.onNodeWithText("Red card").assertIsDisplayed()
        composeRule.onNodeWithText("Record").performClick()
        waitForText("Team 2 #6 already has a red card and has been suspended.")
        composeRule.onNodeWithText("OK").performClick()
    }
}
