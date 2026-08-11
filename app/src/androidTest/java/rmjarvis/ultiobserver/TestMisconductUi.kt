package rmjarvis.ultiobserver

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/// Tests for card and technical-foul UI flows from the active-game screen.
@RunWith(AndroidJUnit4::class)
class TestMisconductUi : MainActivityUiTestFixtures() {
    /**
     * Test the card and technical-foul dialogs from the live screen.
     * This covers the phone-facing dialog sequence, not the full card-accounting matrix.
     */
    @Test
    fun cardsAndTechDialogPath() {
        setRuleGuidanceMode(RuleGuidanceMode.FULL)

        // This long narrative is about card dialogs, not countdown transitions. Keep countdowns
        // from auto-advancing and locking the field if a slow emulator reaches zero mid-test.
        setAutomaticallyAdvanceCountdowns(false)

        // Start between points so the Card dialog shows the pull/receive role suffixes.
        startBetweenPointsProgrammatically()

        // The Card dialog should show the selected team, its pull role, and its current totals.
        openCardsDialog()
        composeRule.onNodeWithText("Team 1 (pulling)").assertIsDisplayed()
        waitForText("Current cards:")
        waitForText("0 yellow / 0 red / 0 blue")
        waitForText("Team card total: 0")
        composeRule.onNodeWithText("No existing cards").assertIsDisplayed()
        dismissDialog(text = "Close")
        composeRule.onAllNodesWithText("Current cards:").assertCountEquals(0)
        openCardsDialog()
        composeRule.onNodeWithText("Team 1 (pulling)").assertIsDisplayed()
        composeRule.onNodeWithText("Close").performClick()
        openCardsDialog(TeamId.TEAM_TWO)
        composeRule.onNodeWithText("Team 2 (receiving)").assertIsDisplayed()
        composeRule.onNodeWithText("Close").performClick()

        // Choosing Blue assesses a blue card to the relevant team.
        // It shows a dialog with information and possibly consequences.
        openCardsDialog()
        tapCardDialogAction(TeamId.TEAM_ONE, "Blue")
        waitForText("Blue Card")
        waitForText("Blue card on Team 1.\nTeam 1 has 1 card total.")
        dismissDialog(text = "Cancel")
        composeRule.onNodeWithTag("card-dialog-${TeamId.TEAM_ONE.name}-blue").assertIsDisplayed()
        tapCardDialogAction(TeamId.TEAM_ONE, "Blue")
        waitForText("Blue card on Team 1.\nTeam 1 has 1 card total.")
        composeRule.onNodeWithText("OK").performClick()

        // Same for Tech
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "tech")).performClick()
        waitForText("This is Team 1's first technical foul.")
        dismissDialog(text = "Cancel")
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "tech")).performClick()
        waitForText("This is Team 1's first technical foul.")
        composeRule.onNodeWithText("OK").performClick()

        // Do these on team two as well to cover those cases.
        openCardsDialog(TeamId.TEAM_TWO)
        tapCardDialogAction(TeamId.TEAM_TWO, "Blue")
        waitForText("Blue card on Team 2.\nTeam 2 has 1 card total.")
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
        dismissDialog(text = "Cancel")
        composeRule.onNodeWithTag("card-dialog-${TeamId.TEAM_ONE.name}-yellow").assertIsDisplayed()
        tapCardDialogAction(TeamId.TEAM_ONE, "Yellow")
        composeRule.onNodeWithText("Yellow card").assertIsDisplayed()
        composeRule.onNodeWithText("Record").performClick()
        waitForText("Enter a player number or name before recording this card.")
        composeRule.onNodeWithText("OK").performClick()

        // Name-only entries are valid for a player without a number.
        composeRule.onNodeWithTag("card-player-name").performTextReplacement("Name Only Handler")
        composeRule.onNodeWithTag("card-player-name").performImeAction()
        composeRule.onNodeWithText("Record").performClick()
        waitForText("Yellow card on Name Only Handler.", substring = true)
        dismissDialog(text = "OK")

        // Seed the next player yellow directly; later assertions only need the card count.
        seedInGamePlayerCardsProgrammatically(
            teamOneCards = listOf(
                playerRecordWithCards("", yellows = 1, playerName = "Name Only Handler"),
                playerRecordWithCards("4", yellows = 1),
            ),
        )

        // A red without an existing yellow records immediately and includes the game-suspension
        // notice.
        openCardsDialog()
        tapCardDialogAction(TeamId.TEAM_ONE, "Red")
        waitForText("Red card")
        enterCardPlayerNumber("5")
        composeRule.onNodeWithText("Record").performClick()
        waitForText("Red card on player 5.", substring = true)
        waitForText("Player 5 receives a game suspension.", substring = true)
        waitForText("Team 1 has 5 cards total (red cards count as 2).", substring = true)
        composeRule.onNodeWithText("OK").performClick()

        // Choosing Red for a player with a yellow records as a red, not as a second yellow.
        openCardsDialog()
        tapCardDialogAction(TeamId.TEAM_ONE, "Red")
        composeRule.onNodeWithText("Red card").assertIsDisplayed()
        dismissDialog(text = "Cancel")
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
        waitForText("Team 1 has 7 cards total (red cards count as 2).", substring = true)
        composeRule.onNodeWithText("OK").performClick()

        // Clicking Edit existing cards allows you to edit information for a previously
        // assessed card.
        // Editing a red card to add a reason should not repeat the suspension notice.
        openCardsDialog()
        composeRule.onNodeWithText("Edit existing cards").performClick()
        waitForText("Edit existing cards")
        composeRule.onAllNodes(hasContentDescription("Edit #5", substring = true))
            .onFirst()
            .performClick()
        waitForText("Edit red card")
        composeRule.onNodeWithText("Reason").performClick()
        waitForText("Red card reason")
        // Battery/fighting is in the right column of the landscape reason picker.
        composeRule.onNodeWithText("Battery/fighting").performClick()
        composeRule.onNodeWithText("Set").performClick()
        composeRule.onNodeWithText("Record").performClick()
        composeRule.onAllNodesWithText(
            "Team 1 #5 now has a red card and has been suspended."
        ).assertCountEquals(0)
        waitForText("Edit existing cards")
        composeRule.onNodeWithText("Done").performClick()
        waitForText("Current cards:")
        composeRule.onNodeWithText("Close").performClick()
        waitForText("Undo Edit red on #5 of Team 1")

        // Yellow-card reason details should round-trip through the custom reason dialog.
        openCardsDialog(TeamId.TEAM_TWO)
        tapCardDialogAction(TeamId.TEAM_TWO, "Yellow")
        waitForText("Yellow card")
        enterCardPlayerNumber("8")
        composeRule.onNodeWithTag("card-player-name").performTextReplacement("Alex Cutter")

        // Backing out of the reason picker leaves the card entry open without changing the reason.
        composeRule.onNodeWithText("Reason").performClick()
        waitForText("Yellow card reason")
        composeRule.onNodeWithText("Other").performScrollTo().performClick()
        composeRule.onNodeWithTag("card-other-reason").performTextReplacement("Discarded reason")
        composeRule.onNodeWithTag("card-other-reason").performImeAction()
        composeRule.onAllNodesWithText("Back").onLast().performClick()
        waitForText("Yellow card")
        composeRule.onNodeWithText("Reason").assertIsDisplayed()

        // Switching from Other to a preset and back keeps the custom reason draft.
        composeRule.onNodeWithText("Reason").performClick()
        waitForText("Yellow card reason")
        composeRule.onNodeWithText("Other").performScrollTo().performClick()
        composeRule.onNodeWithTag("card-other-reason").performTextReplacement("Temporary reason")
        composeRule.onNodeWithTag("card-other-reason").performImeAction()
        composeRule.onNodeWithText("Dangerous play").performScrollTo().performClick()
        composeRule.onNodeWithText("Other").performScrollTo().performClick()
        assertEquals(
            "Temporary reason",
            composeRule.onNodeWithTag("card-other-reason")
                .fetchSemanticsNode()
                .config[SemanticsProperties.EditableText]
                .text,
        )
        composeRule.onNodeWithText("Set").performClick()
        waitForText("Yellow card")
        composeRule.onNodeWithText("Temporary reason").assertIsDisplayed()
        composeRule.onNodeWithText("Temporary reason").performClick()
        waitForText("Yellow card reason")
        composeRule.onNodeWithText("Other").performScrollTo().performClick()
        composeRule.onNodeWithTag("card-other-reason").performTextReplacement("Sideline language")
        composeRule.onNodeWithTag("card-other-reason").performImeAction()
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
        waitForText("Yellow card on #8 Alex Cutter.", substring = true)
        composeRule.onNodeWithText("OK").performClick()

        // Seed later existing-card prerequisites directly so this UI narrative can focus on the
        // edit/list behaviors rather than replaying more card-entry setup.
        seedInGamePlayerCardsProgrammatically(
            teamTwoCards = listOf(
                playerRecordWithCards("7", yellows = 2),
                playerRecordWithCards("8", yellows = 1, playerName = "Alex Cutter"),
                playerRecordWithCards("11", yellows = 1),
            ),
        )

        // Existing-card edits reject blank identity and reassignment to a suspended player.
        openCardsDialog(TeamId.TEAM_TWO)
        composeRule.onNodeWithText("Edit existing cards").performClick()
        waitForText("Edit existing cards")
        composeRule.onAllNodes(hasContentDescription("Edit #11", substring = true))
            .onFirst()
            .performScrollTo()
            .performClick()
        waitForText("Edit yellow card")
        composeRule.onNodeWithTag("card-player-number").performTextReplacement("")
        composeRule.onNodeWithText("Record").performClick()
        waitForText("Enter a player number or name before recording this card.")
        dismissDialog(text = "OK", waitForText = "Edit yellow card")
        enterCardPlayerNumber("7")
        composeRule.onNodeWithText("Record").performClick()
        waitForText("Team 2 #7 already has two yellow cards and has been suspended.")
        dismissDialog(text = "OK", waitForText = "Edit yellow card")

        // Reassigning an existing card to a new unsuspended player saves without a suspension
        // notice.
        composeRule.onNodeWithTag("card-player-number").performTextReplacement("")
        composeRule.onNodeWithTag("card-player-name").performTextReplacement("Name Only Cutter")
        composeRule.onNodeWithText("Record").performClick()
        composeRule.onAllNodesWithText("Card suspension").assertCountEquals(0)
        waitForText("Edit existing cards")
        composeRule.onNodeWithText("Done").performClick()

        // The Edit existing cards pathway from the field screen doesn't allow you to delete
        // a previous card (unlike the More actions version of this).
        openCardsDialog(TeamId.TEAM_TWO)
        composeRule.onNodeWithText("Edit existing cards").performClick()
        waitForText("Edit existing cards")
        composeRule.onNodeWithText("#8 Alex Cutter").assertIsDisplayed()
        composeRule.onAllNodes(hasContentDescription("Edit #8 Alex Cutter", substring = true))
            .assertCountEquals(1)
        composeRule.onAllNodes(
            hasContentDescription("Remove", substring = true)
        ).assertCountEquals(0)

        // Canceling an existing-card edit returns to the editable-card list.
        composeRule.onAllNodes(hasContentDescription("Edit #8 Alex Cutter", substring = true))
            .onFirst()
            .performClick()
        waitForText("Edit yellow card")
        dismissDialog(tag = "card-entry-cancel", waitForText = "Edit existing cards")
        dismissDialog(text = "Done", waitForText = "Current cards:")
        composeRule.onNodeWithText("Close").performClick()
        assertLiveScreen()
        composeRule.onAllNodesWithText("Edit existing cards").assertCountEquals(0)

        // Additional cards for the same player should show the existing-card summary in the dialog.
        openCardsDialog(TeamId.TEAM_TWO)
        tapCardDialogAction(TeamId.TEAM_TWO, "Red")
        waitForText("#8 Alex Cutter (Y 1)")
        dismissDialog(text = "Cancel")

        // Reassigning an existing yellow onto a player with one yellow should show that the edit
        // created the suspension.
        openCardsDialog(TeamId.TEAM_TWO)
        composeRule.onNodeWithText("Edit existing cards").performClick()
        waitForText("Edit existing cards")
        composeRule.onAllNodes(hasContentDescription("Edit Name Only Cutter", substring = true))
            .onFirst()
            .performScrollTo()
            .performClick()
        waitForText("Edit yellow card")
        enterCardPlayerNumber("8")
        composeRule.onNodeWithTag("card-player-name").performTextReplacement("Alex Cutter")
        composeRule.onNodeWithText("Record").performClick()
        waitForText("Team 2 #8 now has two yellow cards and has been suspended.")
        dismissDialog(text = "OK")
        composeRule.onNodeWithText("Done").performClick()
        waitForText("Current cards:")
        composeRule.onNodeWithText("Close").performClick()
        assertLiveScreen()

        // Repeat the same live Card edit with Timed guidance. The suspension notice should
        // automatically return to the editable-card list.
        setRuleGuidanceTimeoutForTest(1_000L)
        setRuleGuidanceMode(RuleGuidanceMode.TIMED)
        seedInGamePlayerCardsProgrammatically(
            teamOneCards = emptyList(),
            teamTwoCards = listOf(
                playerRecordWithCards("14", yellows = 1, playerName = "Timed Cutter"),
                playerRecordWithCards("", yellows = 1, playerName = "Timed Name Only Cutter"),
            ),
        )
        openCardsDialog(TeamId.TEAM_TWO)
        composeRule.onNodeWithText("Edit existing cards").performClick()
        composeRule.onAllNodes(
            hasContentDescription("Edit Timed Name Only Cutter", substring = true)
        ).onFirst().performClick()
        waitForText("Edit yellow card")
        enterCardPlayerNumber("14")
        composeRule.onNodeWithTag("card-player-name").performTextReplacement("Timed Cutter")
        composeRule.onNodeWithText("Record").performClick()
        waitForText("Team 2 #14 now has two yellow cards and has been suspended.")
        waitForText("Edit existing cards")
        composeRule.onNodeWithText("Done").performClick()
        waitForText("Current cards:")
        composeRule.onNodeWithText("Close").performClick()
        assertLiveScreen()

        // A new player red uses the general action-info dialog, which also accepts itself in
        // Timed mode.
        openCardsDialog(TeamId.TEAM_ONE)
        tapCardDialogAction(TeamId.TEAM_ONE, "Red")
        enterCardPlayerNumber("16")
        composeRule.onNodeWithText("Record").performClick()
        waitForText("Player 16 receives a game suspension.", substring = true)
        waitForNoText("Player 16 receives a game suspension.", substring = true)
        setRuleGuidanceMode(RuleGuidanceMode.FULL)

        // Ending the game renders the already-recorded player cards on the summary.
        seedInGamePlayerCardsProgrammatically(
            teamTwoCards = listOf(
                playerRecordWithCards("7", yellows = 2),
                playerRecordWithCards("8", yellows = 2, playerName = "Alex Cutter"),
            ),
        )
        openMoreActionsDialog()
        selectMoreActionsCategory("Manual game transitions")
        clickMoreActionsItem("End game")
        waitForText("Game summary")
        waitForText("#7: Yellow card")
        waitForText("#8 Alex Cutter: Yellow card", substring = true)
    }

    /**
     * Test that the live Card dialog warns before creating another player with the same number.
     */
    @Test
    fun sameNumberDifferentNameWarning() {
        setRuleGuidanceMode(RuleGuidanceMode.FULL)

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
        dismissDialog(tag = "same-number-warning-cancel", waitForText = "Yellow card")

        // If the user is sure that this is correct, they can make two number 6 players,
        // each with a yellow card and different names.
        composeRule.onNodeWithText("Record").performClick()
        waitForText("Same number, different names")
        composeRule.onNodeWithText("Record").performClick()
        waitForText("Yellow card on #6 Bob Cutter.\nTeam 2 has 2 cards total.")
        composeRule.onNodeWithText("OK").performClick()
    }

    /**
     * The Card dialog should keep pull/receive role labels visible during halftime.
     */
    @Test
    fun cardDialogShowsPullRolesDuringHalftime() {
        setRuleGuidanceMode(RuleGuidanceMode.FULL)

        // Start between points with Team 1 pulling next.
        startBetweenPointsProgrammatically()
        assertEquals(TeamId.TEAM_ONE, accessCurrentGameState().pullingTeam)

        // Put the game in halftime and verify Team 2 will pull next.
        updateCurrentStateProgrammatically {
            startHalftimeNow(System.currentTimeMillis())
        }
        waitForText("Halftime")
        assertEquals(TeamId.TEAM_TWO, accessCurrentGameState().pullingTeam)

        // Assessing a card during halftime indicates whether the team is pulling or
        // receiving for the first point in the second half.
        openCardsDialog()
        composeRule.onNodeWithText("Team 1 (receiving)").assertIsDisplayed()
        composeRule.onNodeWithText("Close").performClick()
        openCardsDialog(TeamId.TEAM_TWO)
        composeRule.onNodeWithText("Team 2 (pulling)").assertIsDisplayed()
    }

    /**
     * Test basic manual card-count and add-card corrections from More actions.
     */
    @Test
    fun cardCorrectionCountsAndBasicActions() {
        setRuleGuidanceMode(RuleGuidanceMode.FULL)

        // With no player cards yet, both teams expose disabled existing-card actions.
        startLiveGameProgrammatically()
        openAdjustCardsDialog()
        composeRule.onNodeWithTag("cards-adjust-team-one-edit-existing")
            .performScrollTo()
            .assertIsNotEnabled()
        composeRule.onNodeWithTag("cards-adjust-team-two-edit-existing")
            .performScrollTo()
            .assertIsNotEnabled()
        composeRule.onAllNodesWithText("Add yellow (0)").assertCountEquals(2)
        composeRule.onAllNodesWithText("Add red (0)").assertCountEquals(2)
        composeRule.onAllNodesWithTag("cards-adjust-team-one-add-yellow").assertCountEquals(1)
        composeRule.onAllNodesWithTag("cards-adjust-team-one-add-red").assertCountEquals(1)
        composeRule.onAllNodesWithTag("cards-adjust-team-two-add-yellow").assertCountEquals(1)
        composeRule.onAllNodesWithTag("cards-adjust-team-two-add-red").assertCountEquals(1)

        // Team-count controls can adjust blue cards and technical fouls.
        composeRule.onNodeWithTag("cards-adjust-team-one-blue-increment").performClick()
        composeRule.onNodeWithTag("cards-adjust-team-one-blue-decrement").performClick()
        composeRule.onNodeWithTag("cards-adjust-team-one-tech-increment").performClick()
        composeRule.onNodeWithTag("cards-adjust-team-one-tech-decrement").performClick()
        composeRule.onNodeWithTag("cards-adjust-team-one-tech-increment").performClick()
        composeRule.onNodeWithTag("cards-adjust-team-two-blue-increment").performClick()
        composeRule.onNodeWithTag("cards-adjust-team-two-blue-decrement").performClick()
        composeRule.onNodeWithTag("cards-adjust-team-two-tech-increment").performClick()
        composeRule.onNodeWithTag("cards-adjust-team-two-tech-decrement").performClick()
        composeRule.onNodeWithTag("cards-adjust-team-two-tech-increment").performClick()

        // A name-only yellow is valid for a player without a number.
        composeRule.onNodeWithTag("cards-adjust-team-one-add-yellow")
            .performScrollTo()
            .performClick()
        waitForText("Add yellow card")
        composeRule.onNodeWithTag("card-player-name").performTextReplacement("Name Only Cutter")
        composeRule.onNodeWithText("Record").performClick()
        composeRule.onAllNodesWithText("Card suspension").assertCountEquals(0)

        // The correction dialog can add a player red, then Timed guidance automatically returns
        // from its suspension notice to the correction dialog.
        setRuleGuidanceTimeoutForTest(1_000L)
        setRuleGuidanceMode(RuleGuidanceMode.TIMED)
        composeRule.onNodeWithTag("cards-adjust-team-one-add-red")
            .performScrollTo()
            .performClick()
        waitForText("Add red card")
        enterCardPlayerNumber("9")
        composeRule.onNodeWithText("Record").performClick()
        waitForText("Team 1 #9 now has a red card and has been suspended.")
        waitForNoText("Team 1 #9 now has a red card and has been suspended.")
        composeRule.onNodeWithText("Done").performClick()
        waitForText("Undo Adjust blue card/tech counts")
    }

    /**
     * Test manual edit and remove card corrections from More actions.
     */
    @Test
    fun cardCorrectionEditAndRemoveDialogs() {
        setRuleGuidanceMode(RuleGuidanceMode.FULL)

        // Seed precise card history so the test can focus on edit/remove dialog behavior.
        startLiveGameProgrammatically()
        seedInGamePlayerCardsProgrammatically(
            teamOneCards = listOf(
                playerRecordWithCards("12", yellows = 1),
                playerRecordWithCards("9", reds = 1),
            ),
            teamTwoCards = listOf(
                playerRecordWithCards("23", yellows = 1),
            ),
        )

        // The correction dialog can edit a Team 1 player card before saving the correction.
        openAdjustCardsDialog()
        composeRule.onNodeWithTag("cards-adjust-team-one-edit-existing")
            .performScrollTo()
            .performClick()
        waitForText("Edit existing cards")
        composeRule.onAllNodes(
            hasContentDescription("Edit #12", substring = true)
        ).onFirst().performClick()
        waitForText("Edit yellow card")

        // Canceling an existing-card edit returns to the editable-card list.
        composeRule.onNodeWithTag("card-entry-cancel").performClick()
        waitForText("Edit existing cards")
        composeRule.onAllNodes(
            hasContentDescription("Edit #12", substring = true)
        ).onFirst().performClick()
        waitForText("Edit yellow card")

        // Editing an existing card to no player identity is rejected and leaves the editor open.
        composeRule.onNodeWithTag("card-player-number").performTextReplacement("")
        composeRule.onNodeWithTag("card-player-number").performImeAction()
        composeRule.onNodeWithText("Record").performClick()
        waitForText("Enter a player number or name before recording this card.")
        dismissDialog(text = "OK", waitForText = "Edit yellow card")

        // Reassigning an existing card to a suspended player is rejected.
        enterCardPlayerNumber("9")
        composeRule.onNodeWithText("Record").performClick()
        waitForText("Team 1 #9 already has a red card and has been suspended.")
        dismissDialog(text = "OK", waitForText = "Edit yellow card")

        // Filling the name for the same player should save without treating it as a reassignment.
        composeRule.onNodeWithTag("card-player-number").performTextReplacement("12")
        composeRule.onNodeWithTag("card-player-name").performTextReplacement("Casey Handler")
        composeRule.onNodeWithText("Record").performClick()

        // Reassigning an existing card to a name-only player should also save normally.
        composeRule.onAllNodes(
            hasContentDescription("Edit #12", substring = true)
        ).onFirst().performClick()
        waitForText("Edit yellow card")
        composeRule.onNodeWithTag("card-player-number").performTextReplacement("")
        composeRule.onNodeWithText("Record").performClick()
        composeRule.onNodeWithTag("editable-player-cards-done").performClick()

        // The correction dialog can remove an existing player yellow.
        composeRule.onNodeWithTag("cards-adjust-team-two-edit-existing")
            .performScrollTo()
            .performClick()
        waitForText("Edit existing cards")
        composeRule.onAllNodes(
            hasContentDescription("Remove", substring = true)
        ).onFirst().performClick()
        waitForText("Remove card?")
        composeRule.onNodeWithText("Remove").performClick()
        waitForText("Undo Remove yellow on #23", substring = true)
        composeRule.onNodeWithTag("editable-player-cards-done").performClick()
        composeRule.onNodeWithText("Done").performClick()
        assertLiveScreen()

        // Editing an existing card onto a player who then has two yellows shows the suspension
        // notice before returning to the editable-card list.
        seedInGamePlayerCardsProgrammatically(
            teamOneCards = listOf(
                playerRecordWithCards("12", yellows = 1),
                playerRecordWithCards("13", yellows = 1),
            ),
        )
        openAdjustCardsDialog()
        composeRule.onNodeWithTag("cards-adjust-team-one-edit-existing")
            .performScrollTo()
            .performClick()
        waitForText("Edit existing cards")
        composeRule.onAllNodes(
            hasContentDescription("Edit #12", substring = true)
        ).onFirst().performClick()
        waitForText("Edit yellow card")
        composeRule.onNodeWithTag("card-player-number").performTextReplacement("13")
        composeRule.onNodeWithText("Record").performClick()
        waitForText("Team 1 #13 now has two yellow cards and has been suspended.")
        dismissDialog(text = "OK", waitForText = "Edit existing cards")
        composeRule.onNodeWithTag("editable-player-cards-done").performClick()
        composeRule.onNodeWithText("Done").performClick()
        assertLiveScreen()

        // Removing a player-backed card can be canceled without applying a partial correction.
        seedInGamePlayerCardsProgrammatically(
            teamTwoCards = listOf(
                playerRecordWithCards("23", yellows = 1),
            ),
        )
        openAdjustCardsDialog()
        composeRule.onNodeWithTag("cards-adjust-team-two-edit-existing")
            .performScrollTo()
            .performClick()
        waitForText("Edit existing cards")
        composeRule.onAllNodes(
            hasContentDescription("Remove", substring = true)
        ).onFirst().performClick()
        waitForText("Remove card?")
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithTag("editable-player-cards-done").performClick()
        composeRule.onNodeWithText("Cancel").performClick()
        waitForText("Adjust cards / techs")
        dismissDialog(text = "Close")
        assertLiveScreen()
    }

    /**
     * Test manual add-card validation dialogs from More actions.
     */
    @Test
    fun cardCorrectionValidationDialogs() {
        setRuleGuidanceMode(RuleGuidanceMode.FULL)

        // Seed players needed for same-number and suspended-player validation branches.
        startLiveGameProgrammatically()
        seedInGamePlayerCardsProgrammatically(
            teamTwoCards = listOf(
                playerRecordWithCards("21", yellows = 1, playerName = "Alex Handler"),
                playerRecordWithCards("22", yellows = 2),
            ),
        )

        // A blank added-card correction is rejected and leaves the add dialog open.
        openAdjustCardsDialog()
        composeRule.onNodeWithTag("cards-adjust-team-one-add-yellow")
            .performScrollTo()
            .performClick()
        waitForText("Add yellow card")
        composeRule.onNodeWithText("Record").performClick()
        waitForText("Enter a player number or name before recording this card.")
        dismissDialog(text = "OK", waitForText = "Add yellow card")
        composeRule.onAllNodesWithText("Cancel").onLast().performClick()
        composeRule.onNodeWithText("Cancel").performClick()
        waitForText("Adjust cards / techs")
        dismissDialog(text = "Close")
        assertLiveScreen()

        // Same-number corrections ask before creating a distinct player with the same number.
        openAdjustCardsDialog()
        composeRule.onNodeWithTag("cards-adjust-team-two-add-red")
            .performScrollTo()
            .performClick()
        waitForText("Add red card")
        enterCardPlayerNumber("21")
        composeRule.onNodeWithTag("card-player-name").performTextReplacement("Different Handler")
        composeRule.onNodeWithText("Record").performClick()
        waitForText("Same number, different names")
        dismissDialog(tag = "same-number-warning-cancel", waitForText = "Add red card")
        composeRule.onNodeWithText("Record").performClick()
        waitForText("Same number, different names")
        composeRule.onNodeWithText("Record").performClick()
        waitForText("now has a red card and has been suspended.", substring = true)
        composeRule.onNodeWithText("OK").performClick()
        composeRule.onNodeWithText("Done").performClick()
        assertLiveScreen()

        // Trying another yellow on a maxed-out player should show the invalid assignment warning.
        openAdjustCardsDialog()
        composeRule.onNodeWithTag("cards-adjust-team-two-add-yellow")
            .performScrollTo()
            .performClick()
        waitForText("Add yellow card")
        enterCardPlayerNumber("22")
        composeRule.onNodeWithText("Record").performClick()
        waitForText("Invalid card assignment")
        dismissDialog(text = "OK", waitForText = "Add yellow card")
        composeRule.onNodeWithText("Record").performClick()
        waitForText("Invalid card assignment")
        composeRule.onNodeWithText("OK").performClick()
        waitForText("Add yellow card")
        composeRule.onAllNodesWithText("Cancel").onLast().performClick()
        composeRule.onNodeWithText("Cancel").performClick()
        waitForText("Adjust cards / techs")
        dismissDialog(text = "Close")
        assertLiveScreen()
    }

    /**
     * Test card dialogs that require follow-up choices for already-carded players.
     */
    @Test
    fun repeatedPlayerCardChoiceDialogs() {
        setRuleGuidanceMode(RuleGuidanceMode.FULL)

        // Seed a live point with already-carded players who need choice and rejection dialogs.
        startLivePointProgrammatically()
        seedInGamePlayerCardsProgrammatically(
            teamOneCards = listOf(
                playerRecordWithCards("11", playerName = "Practice Player"),
                playerRecordWithCards("9", yellows = 1),
                playerRecordWithCards("10", yellows = 1, playerName = "Existing Cutter"),
            ),
            teamTwoCards = listOf(playerRecordWithCards("6", yellows = 1, reds = 1)),
        )

        // Canceling a same-number warning for a red card restores the red-card entry.
        openCardsDialog()
        tapCardDialogAction(TeamId.TEAM_ONE, "Red")
        waitForText("Red card")
        waitForText("#11 Practice Player")
        composeRule.onNodeWithTag("card-candidate-copy-11-Practice Player").performClick()
        assertEquals(
            "11",
            composeRule.onNodeWithTag("card-player-number")
                .fetchSemanticsNode()
                .config[SemanticsProperties.EditableText]
                .text,
        )
        assertEquals(
            "Practice Player",
            composeRule.onNodeWithTag("card-player-name")
                .fetchSemanticsNode()
                .config[SemanticsProperties.EditableText]
                .text,
        )
        waitForText("#10 Existing Cutter (Y 1)")
        composeRule.onNodeWithTag("card-candidate-copy-10-Existing Cutter")
            .performScrollTo()
            .performClick()
        assertEquals(
            "10",
            composeRule.onNodeWithTag("card-player-number")
                .fetchSemanticsNode()
                .config[SemanticsProperties.EditableText]
                .text,
        )
        assertEquals(
            "Existing Cutter",
            composeRule.onNodeWithTag("card-player-name")
                .fetchSemanticsNode()
                .config[SemanticsProperties.EditableText]
                .text,
        )
        composeRule.onNodeWithTag("card-player-name").performTextReplacement("Different Cutter")
        composeRule.onNodeWithTag("card-player-name").performImeAction()
        composeRule.onNodeWithText("Record").performClick()
        waitForText("Same number, different names")
        dismissDialog(tag = "same-number-warning-cancel", waitForText = "Red card")
        assertEquals(
            "10",
            composeRule.onNodeWithTag("card-player-number")
                .fetchSemanticsNode()
                .config[SemanticsProperties.EditableText]
                .text,
        )
        assertEquals(
            "Different Cutter",
            composeRule.onNodeWithTag("card-player-name")
                .fetchSemanticsNode()
                .config[SemanticsProperties.EditableText]
                .text,
        )
        composeRule.onNodeWithText("Cancel").performClick()

        // A second yellow can restore the player entry after backing out of misconduct choice.
        openCardsDialog()
        tapCardDialogAction(TeamId.TEAM_ONE, "Yellow")
        waitForText("Yellow card")
        enterCardPlayerNumber("9")
        composeRule.onNodeWithText("Record").performClick()
        waitForText("Misconduct penalty")
        dismissDialog(tag = "misconduct-choice-back", waitForText = "Yellow card")
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
        dismissDialog(tag = "misconduct-resolution-back", waitForText = "Misconduct penalty")
        waitForText("Was this against the offense or defense?", substring = true)
        composeRule.onNodeWithText("Offense").performClick()
        waitForText(
            "Team 1 moves the disc to the reverse brick in the end zone they are defending.",
            substring = true,
        )
        composeRule.onNodeWithText("OK").performClick()
        waitForText("Start misconduct countdown")
        composeRule.onNodeWithTag("live-center-lock").performClick()
        waitForText("Slide right to unlock")
        composeRule.onAllNodesWithTag("live-start-misconduct-countdown").assertCountEquals(0)
        unlockLiveScreen()
        waitForText("Start misconduct countdown")

        // A third yellow for the same player should be rejected without crashing.
        openCardsDialog()
        tapCardDialogAction(TeamId.TEAM_ONE, "Yellow")
        waitForText("Yellow card")
        enterCardPlayerNumber("9")
        composeRule.onNodeWithText("Record").performClick()
        waitForText("Team 1 #9 already has two yellow cards and has been suspended.")
        dismissDialog(text = "OK")
        composeRule.onNodeWithText("Yellow card").assertIsDisplayed()
        dismissDialog(text = "Cancel")

        // Back from a blue-card misconduct choice should cancel that card and return to Card.
        openCardsDialog()
        tapCardDialogAction(TeamId.TEAM_ONE, "Blue")
        waitForText("Blue Card")
        waitForText("Was this against the offense or defense?", substring = true)
        dismissDialog(text = "Cancel")
        composeRule.onNodeWithTag("card-dialog-${TeamId.TEAM_ONE.name}-blue")
            .assertIsDisplayed()
        composeRule.onAllNodesWithText("Misconduct penalty").assertCountEquals(0)
        composeRule.onNodeWithText("Close").performClick()

        // Assessing a 3rd+ blue card during a point asks about offense or defense to
        // give proper advice about the restart.
        openCardsDialog()
        tapCardDialogAction(TeamId.TEAM_ONE, "Blue")
        waitForText("Blue Card")
        waitForText("Was this against the offense or defense?", substring = true)
        dismissDialog(text = "Cancel")
        composeRule.onNodeWithTag("card-dialog-${TeamId.TEAM_ONE.name}-blue").assertIsDisplayed()
        composeRule.onAllNodesWithText("Misconduct penalty").assertCountEquals(0)
        tapCardDialogAction(TeamId.TEAM_ONE, "Blue")
        waitForText("Was this against the offense or defense?", substring = true)
        composeRule.onNodeWithText("Offense").performClick()
        waitForText(
            "Team 1 moves the disc to the reverse brick in the end zone they are defending.",
            substring = true,
        )
        dismissDialog(tag = "misconduct-resolution-back", waitForText = "Blue Card")
        waitForText("Was this against the offense or defense?", substring = true)
        composeRule.onNodeWithText("Defense").performClick()
        waitForText(
            "Team 2 may move the disc to the brick mark nearest the end zone they are attacking.",
            substring = true,
        )
        dismissDialog(tag = "misconduct-resolution-back", waitForText = "Blue Card")
        waitForText("Was this against the offense or defense?", substring = true)
        composeRule.onNodeWithText("Defense").performClick()
        waitForText(
            "Team 2 may move the disc to the brick mark nearest the end zone they are attacking.",
            substring = true,
        )
        composeRule.onNodeWithText("OK").performClick()
        waitForText("Start misconduct countdown")

        // Same for the 3rd or later tech.
        updateCurrentStateProgrammatically {
            copy(
                countdown = null,
                pendingMisconductCountdown = false,
                teamOne = teamOne.copy(technicalFouls = 2),
            )
        }
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "tech")).performClick()
        waitForText("Technical Foul")
        waitForText("Was this against the offense or defense?", substring = true)
        dismissDialog(text = "Cancel")
        composeRule.onAllNodesWithText("Misconduct penalty").assertCountEquals(0)
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "tech")).performClick()
        waitForText("Was this against the offense or defense?", substring = true)
        composeRule.onNodeWithText("Offense").performClick()
        waitForText(
            "Team 1 moves the disc to the reverse brick in the end zone they are defending.",
            substring = true,
        )
        dismissDialog(tag = "misconduct-resolution-back")
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
        dismissDialog(tag = "misconduct-choice-back", waitForText = "Red card")
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

        // The pending misconduct countdown starts only when the observer is ready for it.
        composeRule.onNodeWithTag("live-start-misconduct-countdown").performClick()
        val misconductCountdownState = accessCurrentGameState()
        val misconductCountdown = misconductCountdownState.countdown!!
        assertEquals(CountdownKind.TIME_OUT, misconductCountdown.kind)
        assertEquals(30, misconductCountdown.durationSeconds)
        assertTrue(!misconductCountdownState.pendingMisconductCountdown)
        assertTrue(misconductCountdown.targetEpoch > System.currentTimeMillis())
        composeRule.onAllNodesWithText("Start misconduct countdown").assertCountEquals(0)

        // A player with both a yellow and red has no valid additional red card.
        openCardsDialog(TeamId.TEAM_TWO)
        tapCardDialogAction(TeamId.TEAM_TWO, "Red")
        waitForText("Red card")
        enterCardPlayerNumber("6")
        composeRule.onNodeWithText("Record").performClick()
        waitForText("Team 2 #6 already has a red card and has been suspended.")
        dismissDialog(text = "OK")
        composeRule.onNodeWithText("Red card").assertIsDisplayed()
        composeRule.onNodeWithText("Record").performClick()
        waitForText("Team 2 #6 already has a red card and has been suspended.")
        composeRule.onNodeWithText("OK").performClick()
        dismissDialog(text = "Cancel")
        dismissDialog(text = "Close")
        assertLiveScreen()

        // A threshold technical foul between points gives no-pull yardage guidance.
        startBetweenPointsProgrammatically()
        updateCurrentStateProgrammatically {
            copy(teamOne = teamOne.copy(technicalFouls = 2))
        }
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "tech")).performClick()
        waitForText("Team 2 starts at attacking brick.", substring = true)
        composeRule.onNodeWithText("OK").performClick()
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "pull-violation"))
            .assertIsNotEnabled()
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_TWO, "pull-violation"))
            .assertIsNotEnabled()
    }

    /// Open the More actions card/tech correction dialog.
    private fun openAdjustCardsDialog() {
        openMoreActionsDialog()
        selectMoreActionsCategory("Corrections")
        clickMoreActionsItem("Adjust cards / techs")
        waitForTag("cards-adjust-team-one-blue-increment")
    }
}
