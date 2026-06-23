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
    fun cardsAndTechDialogPath() {
        // Start from a fresh live game so both teams have empty card totals.
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

        // Choosing Blue assesses a blue card to the relevant team.
        // It shows a dialog with information and possibly consequences.
        openCardsDialog()
        tapCardDialogAction(TeamId.TEAM_ONE, "Blue")
        waitForText("Blue Card")
        waitForText("This is Team 1's first blue card.")
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithTag("card-dialog-${TeamId.TEAM_ONE.name}-blue").assertIsDisplayed()
        tapCardDialogAction(TeamId.TEAM_ONE, "Blue")
        waitForText("This is Team 1's first blue card.")
        composeRule.onNodeWithText("OK").performClick()

        // Same for Tech
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "tech")).performClick()
        waitForText("This is Team 1's first technical foul.")
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "tech")).performClick()
        waitForText("This is Team 1's first technical foul.")
        composeRule.onNodeWithText("OK").performClick()

        // Do these on team two as well to cover those cases.
        openCardsDialog(TeamId.TEAM_TWO)
        tapCardDialogAction(TeamId.TEAM_TWO, "Blue")
        waitForText("This is Team 2's first blue card.")
        composeRule.onNodeWithText("OK").performClick()
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_TWO, "tech")).performClick()
        waitForText("This is Team 2's first technical foul.")
        composeRule.onNodeWithText("OK").performClick()

        // Choosing yellow assesses a yellow card to the relevant team.
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

        // A red without an existing yellow records immediately and includes the game-suspension
        // notice.
        // Note -- recordRedCard and recordYellowCard are shorthand helper functions that
        // record the card when we don't need any extra actions around the normal path.
        recordRedCard(
            TeamId.TEAM_ONE,
            "5",
            "Red card on player 5.\n" +
                "Player 5 receives a game suspension.\n" +
                "Team 1 has 4 total blue cards.",
        )

        // Choosing Red for a player with a yellow records as a red, not as a second yellow.
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
        waitForText("Team 1 has 6 total blue cards.", substring = true)
        composeRule.onNodeWithText("OK").performClick()

        // A second yellow comes from issuing another yellow, not from pressing Red.
        recordYellowCard(
            TeamId.TEAM_TWO,
            "7",
            "Yellow card on player 7.\nTeam 2 has 3 total blue cards.",
        )
        recordYellowCard(
            TeamId.TEAM_TWO,
            "7",
            "Second yellow on player 7.",
            substring = true,
        )

        // Clicking Edit existing cards allows you to edit information for a previously
        // assessed card.
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
        composeRule.onAllNodesWithText(
            "Team 1 #4 now has a red card and has been suspended."
        ).assertCountEquals(0)
        waitForText("Edit existing cards")
        composeRule.onNodeWithText("Done").performClick()
        waitForText("Undo Edit red on #4 of Team 1")

        // Yellow-card reason details should round-trip through the custom reason dialog.
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

        // The Edit existing cards pathway from the field screen doesn't allow you to delete
        // a previous card (unlike the More actions version of this).
        openCardsDialog(TeamId.TEAM_TWO)
        composeRule.onNodeWithText("Edit existing cards").performClick()
        waitForText("Edit existing cards")
        composeRule.onNodeWithText("#8 Alex Cutter").assertIsDisplayed()
        composeRule.onNodeWithText("Yellow card").assertIsDisplayed()
        composeRule.onAllNodes(hasContentDescription("Edit", substring = true)).assertCountEquals(1)
        composeRule.onAllNodes(
            hasContentDescription("Remove", substring = true)
        ).assertCountEquals(0)
        composeRule.onNodeWithText("Done").performClick()
        composeRule.onAllNodesWithText("Edit existing cards").assertCountEquals(0)

        // Additional cards for the same player should show the existing-card summary in the dialog.
        openCardsDialog(TeamId.TEAM_TWO)
        tapCardDialogAction(TeamId.TEAM_TWO, "Red")
        waitForText("#8 Alex Cutter (Y 1)")
        composeRule.onNodeWithText("Cancel").performClick()

        // Ending the game renders the already-recorded player cards on the summary.
        openMoreActionsDialog()
        composeRule.onNodeWithText("End game").performClick()
        waitForText("Game over")
        composeRule.onNodeWithText("OK").performClick()
        waitForText("#7: Yellow card")
        waitForText("#8 Alex Cutter: Yellow card")
    }

    /**
     * Test that the live Card dialog warns before creating another player with the same number.
     */
    @Test
    fun sameNumberDifferentNameWarning() {
        // Start with a player with number 6, who has a yellow card.
        startLiveGameProgrammatically()
        seedInGamePlayerCardsProgrammatically(
            teamTwoCards = listOf(
                playerRecordWithCards("6", yellows = 1, playerName = "Alex Cutter")
            ),
        )

        // Try to give a yellow card to another number 6 on the same team with a different name.
        // This should give a warning dialog highlighting the possible error.
        openCardsDialog(TeamId.TEAM_TWO)
        tapCardDialogAction(TeamId.TEAM_TWO, "Yellow")
        waitForText("Yellow card")
        enterCardPlayerNumber("6")
        composeRule.onNodeWithTag("card-player-name").performTextReplacement("Bob Cutter")
        composeRule.onNodeWithText("Record").performClick()
        waitForText("Same number, different names")
        waitForText(
            "#6 Alex Cutter is already listed. Record #6 Bob Cutter as a different player " +
                "with the same number?"
        )
        composeRule.onNodeWithText("Cancel").performClick()
        waitForText("Yellow card")

        // If the user is sure that this is correct, they can make two number 6 players,
        // each with a yellow card and different names.
        composeRule.onNodeWithText("Record").performClick()
        waitForText("Same number, different names")
        composeRule.onNodeWithText("Record").performClick()
        waitForText("Yellow card on #6 Bob Cutter.\nTeam 2 has 2 total blue cards.")
        composeRule.onNodeWithText("OK").performClick()
    }

    /**
     * The Card dialog should keep pull/receive role labels visible during halftime.
     */
    @Test
    fun cardDialogShowsPullRolesDuringHalftime() {
        // Start with Team 1 pulling the opening pull.
        startLiveGameProgrammatically()
        assertEquals(TeamId.TEAM_ONE, composeRule.activity.appViewModel.liveState!!.pullingTeam)

        // Put the game in halftime and verify Team 2 will pull next.
        composeRule.activityRule.scenario.onActivity { activity ->
            val current = activity.appViewModel.liveState!!
            activity.appViewModel.updateLiveGame(
                current.startHalftimeNow(System.currentTimeMillis())
            )
        }
        composeRule.waitForIdle()
        waitForText("Halftime")
        composeRule.onNodeWithText("OK").performClick()
        assertEquals(TeamId.TEAM_TWO, composeRule.activity.appViewModel.liveState!!.pullingTeam)

        // Assessing a card during halftime indicates whether the team is pulling or
        // receiving for the first point in the second half.
        openCardsDialog()
        composeRule.onNodeWithText("Team 1 (receiving)").assertIsDisplayed()
        composeRule.onNodeWithText("Close").performClick()
        openCardsDialog(TeamId.TEAM_TWO)
        composeRule.onNodeWithText("Team 2 (pulling)").assertIsDisplayed()
    }

    /**
     * Test manual player-card corrections from the More actions Adjust cards / techs dialog.
     * This covers adding, removing, canceling, and rejecting invalid corrected card rows.
     */
    @Test
    fun cardCorrectionsFromMoreActions() {
        // Seed existing player cards so the correction dialog can focus on adjustment behavior.
        startLiveGameProgrammatically()
        seedInGamePlayerCardsProgrammatically(
            teamTwoCards = listOf(
                playerRecordWithCards("21", yellows = 2),
                playerRecordWithCards("22", yellows = 2),
            ),
        )

        // Team-count controls can adjust blue cards and technical fouls.
        openMoreActionsDialog()
        composeRule.onNodeWithText("Adjust cards / techs").performClick()
        waitForText("Adjust cards / techs")
        composeRule.onAllNodesWithText("+1")[0].performClick()
        composeRule.onAllNodesWithText("+1")[1].performClick()

        // The correction dialog can add a player red.
        composeRule.onAllNodesWithText("Add red").onFirst().performClick()
        waitForText("Add red card")
        enterCardPlayerNumber("9")
        composeRule.onNodeWithText("Record").performClick()
        waitForText("Team 1 #9 now has a red card and has been suspended.")
        composeRule.onNodeWithText("OK").performClick()

        // The correction dialog can remove an existing player yellow.
        composeRule.onNodeWithTag("cards-adjust-team-two-edit-existing")
            .performScrollTo()
            .performClick()
        waitForText("Edit existing cards")
        composeRule.onAllNodes(
            hasContentDescription("Remove", substring = true)
        ).onFirst().performClick()
        waitForText("Remove card?")
        composeRule.onAllNodesWithText("Remove").onLast().performClick()
        composeRule.onAllNodesWithText("Back").onLast().performClick()
        composeRule.onNodeWithText("Done").performClick()
        waitForText("Undo Adjust blue card/tech counts")

        // Removing a player-backed card can be canceled without applying a partial correction.
        openMoreActionsDialog()
        composeRule.onNodeWithText("Adjust cards / techs").performClick()
        waitForText("Adjust cards / techs")
        composeRule.onNodeWithTag("cards-adjust-team-two-edit-existing")
            .performScrollTo()
            .performClick()
        waitForText("Edit existing cards")
        composeRule.onAllNodes(
            hasContentDescription("Remove", substring = true)
        ).onFirst().performClick()
        waitForText("Remove card?")
        composeRule.onAllNodesWithText("Cancel").onLast().performClick()
        composeRule.onAllNodesWithText("Back").onLast().performClick()
        composeRule.onNodeWithText("Cancel").performClick()
        waitForText("Update game setup")

        // Trying another yellow on a maxed-out player should show the invalid assignment warning.
        openMoreActionsDialog()
        composeRule.onNodeWithText("Adjust cards / techs").performClick()
        waitForText("Adjust cards / techs")
        composeRule.onAllNodesWithText("Add yellow")[1].performClick()
        waitForText("Add yellow card")
        enterCardPlayerNumber("22")
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
    }

    /**
     * Test card dialogs that require follow-up choices for already-carded players.
     */
    @Test
    fun repeatedPlayerCardChoiceDialogs() {
        // Seed a live point with already-carded players who need choice and rejection dialogs.
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
        composeRule.onNodeWithText("Team 1").assertIsDisplayed()
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
        waitForText(
            "Team 1 moves the disc to the reverse brick in the end zone they are defending.",
            substring = true,
        )
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
            composeRule.onNodeWithTag("card-dialog-${TeamId.TEAM_ONE.name}-blue")
                .assertIsDisplayed()
            composeRule.onAllNodesWithText("Misconduct penalty").assertCountEquals(0)
            composeRule.onNodeWithText("Close").performClick()
        }

        // Assessing a 3rd+ blue card during a point asks about offense or defense to
        // give proper advice about the restart.
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
        waitForText(
            "Team 2 may move the disc to the brick mark nearest the end zone they are attacking.",
            substring = true,
        )
        composeRule.onAllNodesWithText("Back").onLast().performClick()
        waitForText("Blue Card")
        waitForText("Was this against the offense or defense?", substring = true)
        composeRule.onNodeWithText("Defense").performClick()
        waitForText(
            "Team 2 may move the disc to the brick mark nearest the end zone they are attacking.",
            substring = true,
        )
        composeRule.onNodeWithText("OK").performClick()
        waitForText("Start misconduct countdown")

        // Same for the 3rd or later tech.
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
        waitForText(
            "Team 1 moves the disc to the reverse brick in the end zone they are defending.",
            substring = true,
        )
        composeRule.onAllNodesWithText("Back").onLast().performClick()
        waitForText("Was this against the offense or defense?", substring = true)
        composeRule.onNodeWithText("Defense").performClick()
        waitForText(
            "Team 2 may move the disc to the brick mark nearest the end zone they are attacking.",
            substring = true,
        )
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
        waitForText(
            "Team 1 may move the disc to the brick mark nearest the end zone they are attacking.",
            substring = true,
        )
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
