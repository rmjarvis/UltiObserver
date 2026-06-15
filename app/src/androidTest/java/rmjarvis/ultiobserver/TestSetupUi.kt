package rmjarvis.ultiobserver

import androidx.compose.ui.test.assertIsDisplayed
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
import java.time.LocalTime
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Test
import org.junit.runner.RunWith

/// Tests for setup screen editors and setup-specific navigation.
@RunWith(AndroidJUnit4::class)
class TestSetupUi : MainActivityUiTestFixtures() {
    /**
     * Test the setup form's modal editors and prior-card entry point.
     * These are broad wiring checks rather than detailed rule-state assertions.
     */
    @Test
    fun setupEditorsOpenAndReturnToSetup() {
        openNewGameSetup()

        // Android back from setup should return home without exiting the app.
        pressAppBack()
        waitForText("Start new game")
        openNewGameSetup()

        // Exercise the standard start-date picker.
        openStartTimeSetupEditor()
        composeRule.onNodeWithTag("setup-start-date-field").performClick()
        waitForText("Set")
        composeRule.onNodeWithText("Cancel").performClick()
        waitForText("Date")
        closeSetupEditor()

        // Exercise the exact start-time dialog without depending on the current clock.
        openStartTimeSetupEditor()
        composeRule.onNodeWithTag("setup-start-time-field").performClick()
        composeRule.onNodeWithText("Cancel").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        waitForText("Date")
        closeSetupEditor()

        // Open each compact rule editor to catch broken setup dialog wiring.
        openSetupDialog("Game to", "Game to")
        openSetupDialog("Halftime", "Halftime")
        openSetupDialog("Half cap", "Half cap")
        openSetupDialog("Soft cap", "Soft cap")
        openSetupDialog("Hard cap", "Hard cap")
        openSetupDialog("Timeouts", "Timeout rules")

        // Invalid numeric entry should fall back to the current value without trapping the observer.
        setIntegerSetupValue("Game to", "Game to", "Points", "")
        setCapRuleValue("Half cap", "Half cap", "")
        setTimeoutRules(timeoutsPerHalf = "", hasFloater = false)

        // Game information is optional and initially does not display an unset division.
        composeRule.onAllNodesWithText("N/A Division").assertCountEquals(0)
        openGameInformationSetupEditor()
        composeRule.onNodeWithText("N/A").assertIsDisplayed()
        closeSetupEditor()

        // Add a prior-card holder and make sure the form remains usable afterwards.
        openPriorCardsSetupEditor()
        composeRule.onNodeWithText("Add card holder").performClick()
        composeRule.onNodeWithText("Add previous game card holder").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        waitForText("Add card holder")
        closeSetupEditor()
        waitForText("Start game")

        // The edited setup should still launch a live game.
        startGameFromSetup()
        assertLiveScreen()
    }

    /**
     * Test a comprehensive setup pass that changes every editable pregame section.
     * This protects the setup screen's user-facing editors without asserting model internals.
     */
    @Test
    fun setupScreenCanEditEveryField() {
        val aardvarks = "Aardvarks"
        val beagles = "Beagles"

        openNewGameSetup()

        // Start time supports exact date and time dialog cancel/set paths.
        openStartTimeSetupEditor()
        composeRule.onNodeWithTag("setup-start-date-field").performClick()
        waitForText("Set")
        composeRule.onNodeWithText("Set").performClick()
        waitForText("Date")
        composeRule.onNodeWithTag("setup-start-time-field").performClick()
        waitForText("Cancel")
        composeRule.onNodeWithText("Cancel").performClick()
        waitForText("Date")
        closeSetupEditor()
        setStartTime(LocalTime.of(11, 45))

        // Team fields include names plus compact buttons for color, contacts, and prior cards.
        replaceSetupTeamName("Team 1", aardvarks)
        replaceSetupTeamName("Team 2", beagles)
        composeRule.onNodeWithTag("setup-Team 1-color-button").performScrollTo().performClick()
        waitForText("$aardvarks Color")
        composeRule.onNodeWithText("Cancel").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        waitForText("Start game")
        composeRule.onNodeWithTag("setup-Team 1-color-button").performScrollTo().performClick()
        waitForText("$aardvarks Color")
        composeRule.onNodeWithTag("setup-Team 1-color-${TeamColorChoice.BLUE.name}").performClick()
        waitForText("Start game")
        composeRule.onNodeWithTag("setup-Team 2-color-button").performScrollTo().performClick()
        waitForText("$beagles Color")
        composeRule.onNodeWithTag("setup-Team 2-color-${TeamColorChoice.YELLOW.name}").performClick()
        waitForText("Start game")
        composeRule.onNodeWithTag("setup-Team 1-names-button").performScrollTo().performClick()
        waitForText("$aardvarks Names")
        composeRule.onNodeWithTag("setup-Team 1-coaches").performTextReplacement("Coach Alpha\nCoach Beta")
        composeRule.onNodeWithTag("setup-Team 1-field-captains").performTextReplacement("Field Captain")
        composeRule.onNodeWithTag("setup-Team 1-spirit-captains").performTextReplacement("Spirit Captain")
        closeSetupEditor()

        // Field/starting pull setup should accept custom end names, either team, either field end, and prompt choices.
        openStartingPullSetupEditor()
        composeRule.onNodeWithTag("setup-near-end-name").performTextReplacement("Road")
        composeRule.onNodeWithTag("setup-far-end-name").performTextReplacement("Trees")
        composeRule.onNodeWithTag("setup-pulling-team-${TeamId.TEAM_TWO.name}").performClick()
        composeRule.onNodeWithTag("setup-pulling-team-${TeamId.TEAM_ONE.name}").performClick()
        composeRule.onNodeWithTag("setup-pulling-from-${FieldEnd.NEAR.name}").performClick()
        composeRule.onNodeWithTag("setup-pulling-from-${FieldEnd.FAR.name}").performClick()
        composeRule.onNodeWithTag("setup-pulling-from-${FieldEnd.NEAR.name}").performClick()
        composeRule.onNodeWithTag("setup-pull-prompts-${PullPromptTarget.FAR.name}").performClick()
        composeRule.onNodeWithTag("setup-pull-prompts-${PullPromptTarget.NEITHER.name}").performClick()
        composeRule.onNodeWithTag("setup-pull-prompts-${PullPromptTarget.BOTH.name}").performClick()
        closeSetupEditor()
        waitForText("Field ends are called:")
        waitForText("Road / Trees")
        waitForText("$aardvarks pulls from Road")
        waitForText("Pull prompts for both ends")

        // Rule editors cover numeric fields, enabled caps, disabled caps, and timeout floaters.
        setIntegerSetupValue("Game to", "Game to", "Points", "7")
        setIntegerSetupValue("Halftime", "Halftime", "Minutes", "2")
        setCapRuleToNone("Half cap", "Half cap")
        setCapRuleValue("Soft cap", "Soft cap", "12")
        setCapRuleToNone("Soft cap", "Soft cap")
        setCapRuleToNone("Hard cap", "Hard cap")
        setCapRuleValue("Hard cap", "Hard cap", "20", enableFromNone = true)
        setTimeoutRules(timeoutsPerHalf = "3", hasFloater = true)
        openGameRulesSetupEditor()
        composeRule.onNodeWithTag("setup-usau-defaults").performScrollTo().performClick()
        waitForText("+105")
        closeSetupEditor()

        // Game information is optional.
        openGameInformationSetupEditor()
        composeRule.onNodeWithTag("setup-tournament-name").performTextReplacement("College Nationals")
        composeRule.onNodeWithTag("setup-game-division-${GameDivision.OPEN.name}").performClick()
        composeRule.onNodeWithTag("setup-game-context").performTextReplacement("Semifinals")
        closeSetupEditor()
        waitForText("College Nationals")
        waitForText("Open Division")
        waitForText("Semifinals")

        // Prior-card entry should support cancel, team-scoped entry, editing, zero-card removal, and name-only rows.
        openPriorCardsSetupEditor()
        composeRule.onNodeWithText("Add card holder").performClick()
        waitForText("Add previous game card holder")
        composeRule.onNodeWithText("Cancel").performClick()
        waitForText("Add card holder")
        closeSetupEditor()
        waitForText("Start game")

        // Add one Team 1 card holder through a realistic correction path that reduces counts.
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
        openPriorCardsSetupEditor(TeamId.TEAM_ONE)
        composeRule.onNodeWithTag("setup-prior-card-edit-0").performScrollTo().performClick()
        waitForText("Edit previous game card holder")
        enterPriorCardJersey("67")
        enterPriorCardName("Sideline Caller")
        composeRule.onNodeWithText("Update").performClick()
        composeRule.onNodeWithText("#67 Sideline Caller").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("setup-prior-card-edit-0").performScrollTo().performClick()
        waitForText("Edit previous game card holder")
        composeRule.onAllNodesWithText("-1")[0].performClick()
        composeRule.onAllNodesWithText("-1")[0].performClick()
        composeRule.onNodeWithText("Remove").performClick()
        waitForText("No prior cards recorded yet.")
        closeSetupEditor()
        addPriorCardHolder(team = TeamId.TEAM_TWO, jersey = "88", playerName = "Numbered Player", yellows = 2, reds = 1)
        openPriorCardsSetupEditor()
        composeRule.onNodeWithText("Add card holder").performClick()
        waitForText("Add previous game card holder")
        enterPriorCardJersey("88")
        enterPriorCardName("  numbered   player ")
        composeRule.onNodeWithText("Add").performClick()
        waitForText("Card holder already listed")
        waitForText("with 2 yellow cards and 1 red card.", substring = true)
        composeRule.onNodeWithText("OK").performClick()
        waitForText("Edit previous game card holder")
        composeRule.onNodeWithText("Update").performClick()
        composeRule.onNodeWithText("#88 Numbered Player").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Y 1").assertIsDisplayed()
        closeSetupEditor()
        addPriorCardHolder(team = TeamId.TEAM_TWO, jersey = "77", playerName = "", yellows = 1, reds = 0)
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
        composeRule.onNodeWithText("OK").performClick()
        waitForText("Edit previous game card holder")
        composeRule.onNodeWithText("Update").performClick()
        composeRule.onNodeWithText("#77 Named Later").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("R 1").assertIsDisplayed()
        closeSetupEditor()
        addPriorCardHolder(team = TeamId.TEAM_TWO, jersey = "", playerName = "Jarvis", yellows = 1, reds = 0)
        addPriorCardHolder(team = TeamId.TEAM_TWO, jersey = "23", playerName = "", yellows = 1, reds = 0)
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
        waitForText("Edit previous game card holder")
        composeRule.onNodeWithText("Update").performClick()
        waitForText("Card holder entries merged")
        waitForText("#23 matched #23 Jarvis", substring = true)
        composeRule.onNodeWithText("Cancel").performClick()
        waitForText("Edit previous game card holder")
        composeRule.onNodeWithText("Update").performClick()
        waitForText("Card holder entries merged")
        composeRule.onNodeWithText("OK").performClick()
        composeRule.onNodeWithText("#23 Jarvis").performScrollTo().assertIsDisplayed()
        composeRule.onAllNodesWithText("#23").assertCountEquals(0)
        closeSetupEditor()
        openPriorCardsSetupEditor()
        composeRule.onNodeWithText("Add card holder").performClick()
        waitForText("Add previous game card holder")
        enterPriorCardJersey("88")
        enterPriorCardName("Other Player")
        composeRule.onNodeWithText("Add").performClick()
        waitForText("Same number, different names")
        composeRule.onNodeWithText("Cancel").performClick()
        waitForText("Add previous game card holder")
        composeRule.onNodeWithText("Add").performClick()
        waitForText("Same number, different names")
        composeRule.onNodeWithText("Add").performClick()
        composeRule.onNodeWithText("#88 Other Player").performScrollTo().assertIsDisplayed()
        closeSetupEditor()
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
        composeRule.onNodeWithText("A Very Long Player Name With No Number").performScrollTo().assertIsDisplayed()
        closeSetupEditor()
        waitForText("A Very Long Player Name With No Number: Y 1", substring = true)

        // The edited setup launches a live game carrying the visible team names forward.
        startGameFromSetup()
        composeRule.onNodeWithText(aardvarks).assertIsDisplayed()
        composeRule.onNodeWithText(beagles).assertIsDisplayed()
    }

    /**
     * Test the live screen fallback labels for blank setup team names.
     * This covers the same public setup route an observer would use, not helper-only state.
     */
    @Test
    fun blankTeamNamesUseDefaultLabels() {
        openNewGameSetup()

        // Blank team names are allowed in setup and should display as Team 1 / Team 2 in live use.
        replaceSetupTeamName("Team 1", " ")
        replaceSetupTeamName("Team 2", " ")
        composeRule.onNodeWithText("Team 1 pulls from Far end").performScrollTo().assertIsDisplayed()
        openStartingPullSetupEditor()
        composeRule.onNodeWithTag("setup-pulling-team-${TeamId.TEAM_TWO.name}").performClick()
        closeSetupEditor()
        composeRule.onNodeWithText("Team 2 pulls from Far end").performScrollTo().assertIsDisplayed()

        startGameFromSetup()
        composeRule.onNodeWithText("Team 1").assertIsDisplayed()
        composeRule.onNodeWithText("Team 2").assertIsDisplayed()
    }
}
