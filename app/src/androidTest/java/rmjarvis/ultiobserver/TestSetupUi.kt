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
        waitForText("Start New Game")
        openNewGameSetup()

        // Exercise the standard start-date picker.
        openStartTimeSetupEditor()
        composeRule.onNodeWithTag("setup-start-date-field").performClick()
        waitForText("Set Start Date")
        composeRule.onNodeWithText("Cancel").performClick()
        waitForText("Date")
        closeSetupEditor()

        // Exercise the exact start-time dialog without depending on the current clock.
        openStartTimeSetupEditor()
        composeRule.onNodeWithTag("setup-start-time-field").performClick()
        composeRule.onNodeWithText("Set Start Time").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        waitForText("Date")
        closeSetupEditor()

        // Open each compact rule editor to catch broken setup dialog wiring.
        openSetupDialog("Game to", "Game To")
        openSetupDialog("Halftime", "Halftime")
        openSetupDialog("Half cap", "Half Cap")
        openSetupDialog("Soft cap", "Soft Cap")
        openSetupDialog("Hard cap", "Hard Cap")
        openSetupDialog("Timeouts", "Timeout Rules")

        // Invalid numeric entry should fall back to the current value without trapping the observer.
        setIntegerSetupValue("Game to", "Game To", "Points", "")
        setCapRuleValue("Half cap", "Half Cap", "")
        setTimeoutRules(timeoutsPerHalf = "", hasFloater = false)

        // Add a prior-card holder and make sure the form remains usable afterwards.
        openPriorCardsSetupEditor()
        composeRule.onNodeWithText("Add Card Holder").performScrollTo().performClick()
        composeRule.onNodeWithText("Add player cards").assertIsDisplayed()
        composeRule.onNodeWithText("Add").performClick()
        waitForText("Add Card Holder")
        closeSetupEditor()
        waitForText("Start Game")

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

        // Start time supports both quick nudges and the exact-time dialog cancel/set paths.
        openStartTimeSetupEditor()
        composeRule.onNodeWithText("-1d").performClick()
        composeRule.onNodeWithText("+1d").performClick()
        composeRule.onNodeWithTag("setup-start-date-field").performClick()
        waitForText("Set Start Date")
        composeRule.onNodeWithText("Set").performClick()
        waitForText("Date")
        composeRule.onNodeWithText("-5m").performClick()
        composeRule.onNodeWithText("+5m").performClick()
        composeRule.onNodeWithTag("setup-start-time-field").performClick()
        waitForText("Set Start Time")
        composeRule.onNodeWithText("Cancel").performClick()
        waitForText("Date")
        closeSetupEditor()
        setStartTime(LocalTime.of(11, 45))

        // Team fields include names plus compact buttons for color, contacts, and prior cards.
        replaceSetupTeamName("Team 1", aardvarks)
        replaceSetupTeamName("Team 2", beagles)
        composeRule.onNodeWithTag("setup-Team 1-color-button").performScrollTo().performClick()
        waitForText("$aardvarks Color")
        composeRule.onNodeWithTag("setup-Team 1-color-${TeamColorChoice.BLUE.name}").performScrollTo().performClick()
        closeSetupEditor()
        composeRule.onNodeWithTag("setup-Team 2-color-button").performScrollTo().performClick()
        waitForText("$beagles Color")
        composeRule.onNodeWithTag("setup-Team 2-color-${TeamColorChoice.YELLOW.name}").performScrollTo().performClick()
        closeSetupEditor()
        composeRule.onNodeWithTag("setup-Team 1-names-button").performScrollTo().performClick()
        waitForText("$aardvarks Names")
        composeRule.onNodeWithTag("setup-Team 1-coaches").performTextReplacement("Coach Alpha\nCoach Beta")
        composeRule.onNodeWithTag("setup-Team 1-field-captains").performTextReplacement("Field Captain")
        composeRule.onNodeWithTag("setup-Team 1-spirit-captains").performTextReplacement("Spirit Captain")
        closeSetupEditor()

        // Starting-pull setup should accept either team and either field end.
        openStartingPullSetupEditor()
        composeRule.onNodeWithTag("setup-pulling-team-${TeamId.TEAM_TWO.name}").performClick()
        composeRule.onNodeWithTag("setup-pulling-team-${TeamId.TEAM_ONE.name}").performClick()
        composeRule.onNodeWithText("Near end").performClick()
        composeRule.onNodeWithText("Far end").performClick()
        composeRule.onNodeWithText("Near end").performClick()
        closeSetupEditor()

        // Rule editors cover numeric fields, enabled caps, disabled caps, and timeout floaters.
        setIntegerSetupValue("Game to", "Game To", "Points", "7")
        setIntegerSetupValue("Halftime", "Halftime", "Minutes", "2")
        setCapRuleToNone("Half cap", "Half Cap")
        setCapRuleValue("Soft cap", "Soft Cap", "12")
        setCapRuleToNone("Soft cap", "Soft Cap")
        setCapRuleToNone("Hard cap", "Hard Cap")
        setCapRuleValue("Hard cap", "Hard Cap", "20", enableFromNone = true)
        setTimeoutRules(timeoutsPerHalf = "3", hasFloater = true)
        openGameRulesSetupEditor()
        composeRule.onNodeWithTag("setup-usau-defaults").performScrollTo().performClick()
        waitForText("+105")
        closeSetupEditor()

        // Tournament name is optional but should accept a simple text value near prior-card setup.
        composeRule.onNodeWithTag("setup-tournament-name").performScrollTo().performTextReplacement("Philly Open")

        // Prior-card entry should support cancel, team selection, yellow/red counts, and removal.
        openPriorCardsSetupEditor()
        composeRule.onNodeWithText("Add Card Holder").performScrollTo().performClick()
        waitForText("Add player cards")
        composeRule.onNodeWithText("Cancel").performClick()
        waitForText("Add Card Holder")
        closeSetupEditor()
        waitForText("Start Game")

        // Add one card holder through a realistic correction path: switch the team back and reduce counts.
        openPriorCardsSetupEditor()
        composeRule.onNodeWithText("Add Card Holder").performScrollTo().performClick()
        waitForText("Add player cards")
        composeRule.onNodeWithTag("setup-prior-card-team-${TeamId.TEAM_TWO.name}").performClick()
        composeRule.onNodeWithTag("setup-prior-card-team-${TeamId.TEAM_ONE.name}").performClick()
        enterPriorCardJersey("66")
        composeRule.onAllNodesWithText("+1")[0].performClick()
        composeRule.onAllNodesWithText("-1")[0].performClick()
        composeRule.onAllNodesWithText("+1")[0].performClick()
        composeRule.onAllNodesWithText("-1")[1].performClick()
        composeRule.onAllNodesWithText("+1")[1].performClick()
        composeRule.onAllNodesWithText("-1")[1].performClick()
        composeRule.onNodeWithText("Add").performClick()
        composeRule.onNodeWithText("$aardvarks #66").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Y 2").assertIsDisplayed()
        closeSetupEditor()
        waitForText("Start Game")
        openPriorCardsSetupEditor()
        composeRule.onNodeWithText("Remove").performScrollTo().performClick()
        waitForText("No prior cards recorded yet.")
        closeSetupEditor()
        addPriorCardHolder(teamName = beagles, jersey = "88", yellows = 2, reds = 1)
        addPriorCardHolder(teamName = beagles, jersey = "77", yellows = 1, reds = 0)
        waitForText("2 players carry cards.")
        openPriorCardsSetupEditor()
        composeRule.onAllNodesWithText("Remove").onFirst().performScrollTo().performClick()
        composeRule.onNodeWithText("$beagles #77").performScrollTo().assertIsDisplayed()
        closeSetupEditor()
        waitForText("1 player carries cards.")

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
