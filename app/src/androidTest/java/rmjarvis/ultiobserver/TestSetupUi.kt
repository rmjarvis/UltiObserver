package rmjarvis.ultiobserver

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.time.LocalTime
import org.junit.Test
import org.junit.runner.RunWith

/// Tests for setup screen editors and setup-specific navigation.
@RunWith(AndroidJUnit4::class)
class TestSetupUi : MainActivityUiTestFixtures() {
    /**
     * Test start-date and start-time editing from the setup screen.
     */
    @Test
    fun setupStartTime() {
        openNewGameSetup()

        // The date dialog can accept its current value and return to the start-time editor.
        openStartTimeSetupEditor()
        composeRule.onNodeWithTag("setup-start-date-field").performClick()
        waitForText("Set")
        composeRule.onNodeWithText("Set").performClick()
        waitForText("Date")
        closeSetupEditor()

        // Canceling the date and time dialogs should return to the start-time editor.
        openStartTimeSetupEditor()
        composeRule.onNodeWithTag("setup-start-date-field").performClick()
        waitForText("Set")
        composeRule.onNodeWithText("Cancel").performClick()
        waitForText("Date")
        composeRule.onNodeWithTag("setup-start-time-field").performClick()
        waitForText("Cancel")
        composeRule.onNodeWithText("Cancel").performClick()
        waitForText("Date")
        closeSetupEditor()

        // The exact time helper should apply a new observer-entered time.
        setStartTime(LocalTime.of(11, 45))
    }

    /**
     * Test setup team names, team colors, and team-contact editing.
     */
    @Test
    fun setupTeamDetails() {
        val aardvarks = "Aardvarks"
        val beagles = "Beagles"

        openNewGameSetup()

        // Team names should update the setup screen labels used by the compact editors.
        replaceSetupTeamName("Team 1", aardvarks)
        replaceSetupTeamName("Team 2", beagles)

        // Team colors support both canceling and selecting from the compact color dialog.
        composeRule.onNodeWithTag("setup-Team 2-color-button").performScrollTo().performClick()
        waitForText("$beagles Color")
        composeRule.onNodeWithTag(
            "setup-Team 2-color-${TeamColorChoice.YELLOW.name}"
        ).performClick()
        waitForText("Start game")
        composeRule.onNodeWithTag("setup-Team 1-color-button").performScrollTo().performClick()
        waitForText("$aardvarks Color")
        composeRule.onNodeWithText("Cancel").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        waitForText("Start game")
        composeRule.onNodeWithTag("setup-Team 1-color-button").performScrollTo().performClick()
        waitForText("$aardvarks Color")
        composeRule.onNodeWithTag("setup-Team 1-color-${TeamColorChoice.BLUE.name}").performClick()
        waitForText("Start game")

        // Team-contact fields should accept multi-line coaches plus captain names.
        composeRule.onNodeWithTag("setup-Team 1-names-button").performScrollTo().performClick()
        waitForText("$aardvarks Names")
        composeRule.onNodeWithTag("setup-Team 1-coaches")
            .performTextReplacement("Coach Alpha\nCoach Beta")
        composeRule.onNodeWithTag("setup-Team 1-field-captains")
            .performTextReplacement("Field Captain")
        composeRule.onNodeWithTag("setup-Team 1-spirit-captains")
            .performTextReplacement("Spirit Captain")
        closeSetupEditor()

        // Starting a game should carry the edited team names to the live screen.
        startGameFromSetup()
        composeRule.onNodeWithText(aardvarks).assertIsDisplayed()
        composeRule.onNodeWithText(beagles).assertIsDisplayed()
    }

    /**
     * Test setup fields that describe the field ends and the opening pull.
     */
    @Test
    fun setupFieldLayout() {
        val aardvarks = "Aardvarks"

        openNewGameSetup()

        // A custom team name should be reflected in the starting-pull summary.
        replaceSetupTeamName("Team 1", aardvarks)

        // Starting-pull setup accepts custom end names, either team, either field end, and prompts.
        openStartingPullSetupEditor()
        composeRule.onNodeWithTag("setup-near-end-name").performTextReplacement("Road")
        composeRule.onNodeWithTag("setup-far-end-name").performTextReplacement("Trees")
        composeRule.onNodeWithTag("setup-far-end-name").performImeAction()
        composeRule.onNodeWithTag("setup-pulling-team-${TeamId.TEAM_TWO.name}")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("setup-pulling-team-${TeamId.TEAM_ONE.name}")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("setup-pulling-from-${FieldEnd.NEAR.name}")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("setup-pulling-from-${FieldEnd.FAR.name}")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("setup-pulling-from-${FieldEnd.NEAR.name}")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("setup-pull-prompts-${PullPromptTarget.FAR.name}")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("setup-pull-prompts-${PullPromptTarget.NEITHER.name}")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("setup-pull-prompts-${PullPromptTarget.BOTH.name}")
            .performScrollTo()
            .performClick()
        closeSetupEditor()

        // The compact summary should describe the chosen field labels, pulling team, and prompts.
        waitForText("Field ends are called:")
        waitForText("Road / Trees")
        waitForText("$aardvarks pulls from Road")
        waitForText("Pull prompts for both ends")
    }

    /**
     * Test setup rule editors for score targets, caps, timeouts, and USAU defaults.
     */
    @Test
    fun setupRules() {
        openNewGameSetup()

        // Start from USAU defaults so restored device state cannot leave cap rows disabled.
        openGameRulesSetupEditor()
        composeRule.onNodeWithTag("setup-usau-defaults").performScrollTo().performClick()
        waitForText("+105")
        closeSetupEditor()

        // Empty numeric rule entries should fall back to the current value.
        setIntegerSetupValue("Game to", "Game to", "Points", "")
        setTimeoutRules(timeoutsPerHalf = "", hasFloater = false)

        // Numeric rule editors should accept direct values.
        setIntegerSetupValue("Game to", "Game to", "Points", "7")
        setIntegerSetupValue("Halftime", "Halftime", "Minutes", "2")

        // Cap editors should support disabling existing caps and re-enabling a disabled cap.
        setCapRuleToNone("Half cap", "Half cap")
        setCapRuleValue("Soft cap", "Soft cap", "12")
        setCapRuleToNone("Soft cap", "Soft cap")
        setCapRuleToNone("Hard cap", "Hard cap")
        setCapRuleValue("Hard cap", "Hard cap", "20", enableFromNone = true)

        // Timeout rules should accept a floater timeout configuration.
        setTimeoutRules(timeoutsPerHalf = "3", hasFloater = true)

        // USAU defaults should restore the expected compact rule summary.
        openGameRulesSetupEditor()
        composeRule.onNodeWithTag("setup-usau-defaults").performScrollTo().performClick()
        waitForText("+105")
        closeSetupEditor()
    }

    /**
     * Test optional game-information fields and their setup summaries.
     */
    @Test
    fun setupGameInformation() {
        openNewGameSetup()

        // Unset optional game information should stay out of the compact setup summary.
        composeRule.onAllNodesWithText("N/A Division").assertCountEquals(0)

        // Optional game-information fields initially show placeholder values in the editor.
        openGameInformationSetupEditor()
        composeRule.onAllNodesWithText("N/A").assertCountEquals(2)
        composeRule.onNodeWithText("Level").assertIsDisplayed()
        closeSetupEditor()

        // Optional tournament, division, level, context, and observer fields should persist.
        openGameInformationSetupEditor()
        composeRule.onNodeWithTag("setup-tournament-name")
            .performTextReplacement("College Nationals")
        composeRule.onNodeWithTag("setup-game-division-${GameDivision.OPEN.name}").performClick()
        composeRule.onNodeWithTag("setup-game-level-Great-Grandmasters").performClick()
        composeRule.onNodeWithTag("setup-game-level-other").performClick()
        composeRule.onNodeWithTag("setup-game-level-other-text")
            .performTextReplacement("Community showcase")
        composeRule.onNodeWithTag("setup-game-context").performTextReplacement("Semifinals")
        composeRule.onNodeWithTag("setup-observers").performTextReplacement("Mike and Gary")
        composeRule.onNodeWithTag("setup-observers").performImeAction()
        closeSetupEditor()

        // The setup screen should show the populated game-information summary.
        waitForText("College Nationals")
        waitForText("Open Division")
        waitForText("Community showcase")
        waitForText("Semifinals")
        waitForText("Observers: Mike and Gary")
    }

    /**
     * Test adding, editing, zeroing, and deleting prior-card holders during setup.
     */
    @Test
    fun priorCardEditing() {
        openNewGameSetup()

        // The add-player dialog can be canceled without leaving the prior-card editor.
        openPriorCardsSetupEditor()
        composeRule.onNodeWithText("Add card holder").performClick()
        waitForText("Add previous game card holder")
        composeRule.onNodeWithText("Cancel").performClick()
        waitForText("Add card holder")
        closeSetupEditor()
        waitForText("Start game")

        // A Team 1 card holder can be added through a realistic count-correction path.
        openPriorCardsSetupEditor(TeamId.TEAM_ONE)
        composeRule.onNodeWithText("Add card holder").performClick()
        waitForText("Add previous game card holder")
        enterPriorCardJersey("66")
        composeRule.onAllNodesWithText("+1")[0].performClick()
        composeRule.onAllNodesWithText("-1")[0].performClick()
        composeRule.onAllNodesWithText("+1")[0].performClick()
        composeRule.onAllNodesWithText("-1")[1].performClick()
        composeRule.onAllNodesWithText("+1")[1].performClick()
        composeRule.onAllNodesWithText("-1")[1].performClick()
        composeRule.onNodeWithText("Add").performClick()
        composeRule.onNodeWithText("#66").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Y 2").assertIsDisplayed()
        closeSetupEditor()
        waitForText("Start game")

        // Editing should allow changing both jersey and name on an existing prior-card holder.
        openPriorCardsSetupEditor(TeamId.TEAM_ONE)
        composeRule.onNodeWithTag("setup-prior-card-edit-0").performScrollTo().performClick()
        waitForText("Edit previous game card holder")
        enterPriorCardJersey("67")
        enterPriorCardName("Sideline Caller")
        composeRule.onNodeWithText("Update").performClick()
        composeRule.onNodeWithText("#67 Sideline Caller").performScrollTo().assertIsDisplayed()

        // Reducing the prior-card count to zero should keep the player row but show no prior cards.
        composeRule.onNodeWithTag("setup-prior-card-edit-0").performScrollTo().performClick()
        waitForText("Edit previous game card holder")
        composeRule.onAllNodesWithText("-1")[0].performClick()
        composeRule.onAllNodesWithText("-1")[0].performClick()
        composeRule.onNodeWithText("Update").performClick()
        composeRule.onNodeWithText("#67 Sideline Caller").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("No prior cards").assertIsDisplayed()

        // Removing the zero-prior player should empty the prior-card editor.
        composeRule.onNodeWithTag("setup-prior-card-remove-0").performScrollTo().performClick()
        waitForText("No card holders added yet")
    }

    /**
     * Test duplicate and possible-match handling for prior-card holders during setup.
     */
    @Test
    fun priorCardMatching() {
        openNewGameSetup()

        // A normalized duplicate number/name should match the existing row rather than add a copy.
        addPriorCardHolder(
            team = TeamId.TEAM_TWO,
            jersey = "88",
            playerName = "Numbered Player",
            yellows = 2,
            reds = 1,
        )
        openPriorCardsSetupEditor()
        composeRule.onNodeWithText("Add card holder").performClick()
        waitForText("Add previous game card holder")
        enterPriorCardJersey("88")
        enterPriorCardName("  numbered   player ")
        composeRule.onNodeWithText("Add").performClick()
        waitForText("Card holder already listed")
        waitForText("with 2 yellow cards and 1 red card.", substring = true)
        composeRule.onNodeWithText("OK").performClick()
        composeRule.onNodeWithText("#88 Numbered Player").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Y 2  R 1").assertIsDisplayed()
        closeSetupEditor()

        // A number-only existing row should reject silently replacing it with a new named row.
        addPriorCardHolder(
            team = TeamId.TEAM_TWO,
            jersey = "77",
            playerName = "",
            yellows = 1,
            reds = 0,
        )
        openPriorCardsSetupEditor()
        composeRule.onNodeWithText("Add card holder").performClick()
        waitForText("Add previous game card holder")
        enterPriorCardJersey("77")
        enterPriorCardName("Named Later")
        composeRule.onAllNodesWithText("-1")[0].performClick()
        composeRule.onAllNodesWithText("+1")[1].performClick()
        composeRule.onNodeWithText("Add").performClick()
        waitForText("Card holder already listed")
        waitForText("with 1 yellow card.", substring = true)
        composeRule.onNodeWithTag("setup-existing-card-holder-back").performClick()
        waitForText("Add previous game card holder")
        composeRule.onNodeWithText("Add").performClick()
        waitForText("Card holder already listed")
        composeRule.onNodeWithText("OK").performClick()
        waitForText("#77")
        composeRule.onAllNodesWithText("#77 Named Later").assertCountEquals(0)
        closeSetupEditor()

        // Separate number-only and name-only rows should be treated as one existing player match.
        addPriorCardHolder(
            team = TeamId.TEAM_TWO,
            jersey = "",
            playerName = "Jarvis",
            yellows = 1,
            reds = 0,
        )
        addPriorCardHolder(
            team = TeamId.TEAM_TWO,
            jersey = "23",
            playerName = "",
            yellows = 1,
            reds = 0,
        )
        openPriorCardsSetupEditor()
        composeRule.onNodeWithText("Add card holder").performClick()
        waitForText("Add previous game card holder")
        enterPriorCardJersey("23")
        enterPriorCardName("jarvis")
        composeRule.onAllNodesWithText("-1")[0].performClick()
        composeRule.onAllNodesWithText("+1")[1].performClick()
        composeRule.onNodeWithText("Add").performClick()
        waitForText("Card holder already listed")
        waitForText("with 1 yellow card.", substring = true)
        composeRule.onNodeWithText("OK").performClick()
        waitForText("#23")
        composeRule.onNodeWithText("Jarvis").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText("#23 jarvis").assertCountEquals(0)
        closeSetupEditor()

        // A possible number match should support canceling before adding a separate player.
        openPriorCardsSetupEditor()
        composeRule.onNodeWithText("Add card holder").performClick()
        waitForText("Add previous game card holder")
        enterPriorCardJersey("88")
        enterPriorCardName("Other Player")
        composeRule.onNodeWithText("Add").performClick()
        waitForText("Possible player match")
        composeRule.onNodeWithText("Cancel").performClick()
        waitForText("Add previous game card holder")
        composeRule.onNodeWithText("Add").performClick()
        waitForText("Possible player match")
        composeRule.onNodeWithText("Add").performClick()
        composeRule.onNodeWithText("#88 Other Player").performScrollTo().assertIsDisplayed()
        closeSetupEditor()

        // Removing the numbered row should leave unrelated name-only rows visible.
        addPriorCardHolder(
            team = TeamId.TEAM_TWO,
            jersey = "",
            playerName = "A Very Long Player Name With No Number",
            yellows = 1,
            reds = 0,
        )
        waitForText("#88: Y 1", substring = true)
        openPriorCardsSetupEditor()
        composeRule.onNodeWithText("#88 Numbered Player").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("setup-prior-card-remove-0").performScrollTo().performClick()
        composeRule.onNodeWithText("A Very Long Player Name With No Number")
            .performScrollTo()
            .assertIsDisplayed()
        closeSetupEditor()
        waitForText("A Very Long Player Name With No Number: Y 1", substring = true)
    }

    /**
     * Test the live screen fallback labels for blank setup team names.
     * This covers the same public setup route an observer would use, not helper-only state.
     */
    @Test
    fun defaultTeamNames() {
        openNewGameSetup()

        // Blank team names are allowed in setup and should display as Team 1 / Team 2 in live use.
        replaceSetupTeamName("Team 1", " ")
        replaceSetupTeamName("Team 2", " ")
        composeRule.onNodeWithText("Team 1 pulls from Far end")
            .performScrollTo()
            .assertIsDisplayed()
        openStartingPullSetupEditor()
        composeRule.onNodeWithTag("setup-pulling-team-${TeamId.TEAM_TWO.name}").performClick()
        closeSetupEditor()
        composeRule.onNodeWithText("Team 2 pulls from Far end")
            .performScrollTo()
            .assertIsDisplayed()

        startGameFromSetup()
        composeRule.onNodeWithText("Team 1").assertIsDisplayed()
        composeRule.onNodeWithText("Team 2").assertIsDisplayed()
    }

    /**
     * Test setup protection against deleting a known player with current-game cards.
     */
    @Test
    fun rejectDeletingCardedPlayer() {
        openNewGameSetup()

        // Seed a setup player row that already has current-game card state.
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.appViewModel.updateSetup(
                activity.appViewModel.setupState.copy(
                    teamOnePlayers = listOf(playerRecordWithCards("9", yellows = 1)),
                )
            )
        }
        composeRule.waitForIdle()

        // Removing that row should be blocked and should leave the player visible.
        openPriorCardsSetupEditor(TeamId.TEAM_ONE)
        composeRule.onNodeWithText("#9").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("setup-prior-card-remove-0").performScrollTo().performClick()

        waitForText("Player not deleted")
        waitForText("#9 has an in-game card and cannot be deleted.")
        composeRule.onNodeWithText("OK").performClick()
        composeRule.onNodeWithText("#9").performScrollTo().assertIsDisplayed()
    }

    /**
     * Test setup protection against merging rows that already have current-game cards.
     */
    @Test
    fun rejectMergingCardedRows() {
        openNewGameSetup()

        // Seed separate number-only and name-only rows that both have current-game card state.
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.appViewModel.updateSetup(
                activity.appViewModel.setupState.copy(
                    teamOnePlayers = listOf(
                        playerRecordWithCards("23", yellows = 1),
                        playerRecordWithCards("", yellows = 1, playerName = "Jarvis"),
                    ),
                )
            )
        }
        composeRule.waitForIdle()

        // Adding a holder that would cleanly match both rows should warn instead of merging them.
        openPriorCardsSetupEditor(TeamId.TEAM_ONE)
        composeRule.onNodeWithText("Add card holder").performClick()
        waitForText("Add previous game card holder")
        enterPriorCardJersey("23")
        enterPriorCardName("Jarvis")
        composeRule.onNodeWithText("Add").performClick()

        waitForText("Card holder already listed")
        waitForText("with no prior cards.", substring = true)
        composeRule.onNodeWithText("OK").performClick()
        composeRule.onNodeWithText("#23").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Jarvis").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText("#23 Jarvis").assertCountEquals(0)
    }
}
