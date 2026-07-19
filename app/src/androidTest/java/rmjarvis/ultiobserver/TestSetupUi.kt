package rmjarvis.ultiobserver

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.click
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.percentOffset
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
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
        composeRule.onNodeWithTag("setup-start-date-set").performClick()
        waitForText("Date")
        closeSetupEditor()

        // Canceling the date and time dialogs should return to the start-time editor.
        openStartTimeSetupEditor()
        composeRule.onNodeWithTag("setup-start-date-field").performClick()
        waitForText("Set")
        composeRule.onAllNodesWithText("Cancel").onLast().performClick()
        waitForText("Date")
        composeRule.onNodeWithTag("setup-start-time-field").performClick()
        waitForText("Cancel")
        composeRule.onAllNodesWithText("Cancel").onLast().performClick()
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
        dismissDialog(text = "Cancel")
        waitForText("Start game")
        composeRule.onNodeWithTag("setup-Team 1-color-button").performScrollTo().performClick()
        waitForText("$aardvarks Color")
        composeRule.onNodeWithTag("setup-Team 1-color-${TeamColorChoice.BLUE.name}").performClick()
        waitForText("Start game")

        // The expanded color picker can save a custom color, which then appears as a reusable
        // custom swatch in the compact color dialog.
        composeRule.onNodeWithTag("setup-Team 1-color-button").performScrollTo().performClick()
        waitForText("$aardvarks Color")
        composeRule.onNodeWithTag("setup-Team 1-color-more").performClick()
        waitForText("Use this color")
        dismissDialog(text = "Cancel")
        waitForText("Start game")
        composeRule.onNodeWithTag("setup-Team 1-color-button").performScrollTo().performClick()
        waitForText("$aardvarks Color")
        composeRule.onNodeWithTag("setup-Team 1-color-more").performClick()
        waitForText("Use this color")
        composeRule.onNodeWithTag("setup-Team 1-color-custom-picker")
            .performTouchInput {
                click(percentOffset(0.75f, 0.35f))
            }
        composeRule.onNodeWithTag("setup-Team 1-color-custom-preview").performClick()
        composeRule.onNodeWithText("Use this color").performClick()
        waitForText("Start game")

        // Saved custom colors can be reselected, and remain available after switching back to a
        // preset color.
        composeRule.onNodeWithTag("setup-Team 1-color-button").performScrollTo().performClick()
        waitForText("$aardvarks Color")
        composeRule.onNodeWithTag("setup-Team 1-color-custom").assertIsDisplayed()
        composeRule.onNodeWithTag("setup-Team 1-color-custom").performClick()
        waitForText("Start game")
        composeRule.onNodeWithTag("setup-Team 1-color-button").performScrollTo().performClick()
        waitForText("$aardvarks Color")
        composeRule.onNodeWithTag("setup-Team 1-color-${TeamColorChoice.BLUE.name}").performClick()
        waitForText("Start game")
        composeRule.onNodeWithTag("setup-Team 1-color-button").performScrollTo().performClick()
        waitForText("$aardvarks Color")
        composeRule.onNodeWithTag("setup-Team 1-color-custom").assertIsDisplayed()
        composeRule.onNodeWithTag("setup-Team 1-color-more").performClick()
        waitForText("Use this color")
        dismissDialog(text = "Use this color")
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

        // Team 2 uses the same names editor path, including staff fields.
        composeRule.onNodeWithTag("setup-Team 2-names-button").performScrollTo().performClick()
        waitForText("$beagles Names")
        composeRule.onNodeWithTag("setup-Team 2-coaches").performTextReplacement("Coach Gamma")
        composeRule.onNodeWithTag("setup-Team 2-field-captains")
            .performTextReplacement("Second Field Captain")
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
        val beagles = "Beagles"

        openNewGameSetup()

        // Custom team names should be reflected in the starting-pull editor and summary.
        replaceSetupTeamName("Team 1", aardvarks)
        replaceSetupTeamName("Team 2", beagles)

        // Mixed setup makes gender-ratio choices visible in the starting-pull editor.
        openGameInformationSetupEditor()
        composeRule.onNodeWithTag("setup-game-division-${GameDivision.MIXED.name}")
            .performScrollTo()
            .performClick()
        closeSetupEditor()

        // Starting-pull setup accepts custom end names, committing them through focus loss to
        // another text field and through tapping an ordinary choice control.
        openStartingPullSetupEditor()
        composeRule.onNodeWithTag("setup-far-end-name").performTextReplacement("Trees")
        composeRule.onNodeWithTag("setup-near-end-name").performClick()
        composeRule.onNodeWithTag("setup-pulling-from-${FieldEnd.FAR.name}")
            .assertTextContains("Trees", substring = true)
        composeRule.onNodeWithTag("setup-near-end-name").performTextReplacement("Road")
        composeRule.onNodeWithTag("setup-pulling-team-${TeamId.TEAM_TWO.name}")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("setup-pulling-from-${FieldEnd.NEAR.name}")
            .assertTextContains("Road", substring = true)

        // The same editor also accepts either team, either field end, prompts, and the ABBA
        // first-point gender ratio.
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
        composeRule.onNodeWithTag("setup-pull-prompts-${PullPromptTarget.NEAR.name}")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("setup-pull-prompts-${PullPromptTarget.BOTH.name}")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag(
            "setup-initial-gender-ratio-${GenderRatio.FOUR_WOMEN_THREE_MEN.name}"
        )
            .performScrollTo()
            .performClick()
        closeSetupEditor()

        // The compact summary should describe the chosen field labels, pulling team, and prompts.
        waitForText("Field ends are called:")
        waitForText("Road / Trees")
        waitForText("$aardvarks pulls from Road")
        waitForText("Pull prompts for both ends")
        waitForText("First point ratio: 4W/3M")

        // Cancel is the explicit discard path for this editor.
        openStartingPullSetupEditor()
        composeRule.onNodeWithTag("setup-near-end-name")
            .performTextReplacement("Canceled end")
        composeRule.onNodeWithTag("setup-near-end-name").performImeAction()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onAllNodesWithText("Canceled end").assertCountEquals(0)
        waitForText("Road / Trees")

        // Dismissal follows Done so accidental outside taps keep entered setup edits.
        openStartingPullSetupEditor()
        composeRule.onNodeWithTag("setup-far-end-name")
            .performTextReplacement("River")
        composeRule.onNodeWithTag("setup-far-end-name").performImeAction()
        dismissDialog(text = "Done", clearKeyboard = true)
        waitForText("Road / River")

        // Dismissal also follows Done when no text field has focus.
        openStartingPullSetupEditor()
        dismissDialog(text = "Done")
        waitForText("Road / River")

        // Canceling the mixed gender-ratio popup keeps the existing ABBA setup rule.
        openMixedGenderRatioEditor()
        composeRule.onNodeWithTag("setup-gender-ratio-rule-${GenderRatioRule.GEN_ZONE.name}")
            .performClick()
        dismissDialog(text = "Cancel")
        waitForText("Game to")
        closeSetupEditor()
        openStartingPullSetupEditor()
        waitForText("First point gender ratio")
        composeRule.onAllNodesWithText("End for gen zone in first half").assertCountEquals(0)
        closeSetupEditor()

        // Gen Zone setup uses field-end labels and the rules say whether the zone switches.
        openMixedGenderRatioEditor()
        waitForText("Alternate two at a time after the first point", substring = true)
        composeRule.onNodeWithTag("setup-gender-ratio-rule-${GenderRatioRule.GEN_ZONE.name}")
            .performClick()
        waitForText(
            "The team in a particular end zone decides the ratio each point.",
            substring = true,
        )
        composeRule.onNodeWithTag("setup-gender-ratio-set").performClick()
        waitForText("Game to")
        closeSetupEditor()
        openStartingPullSetupEditor()
        waitForText("End for gen zone in first half")
        composeRule.onNodeWithTag("setup-first-half-gen-zone-${FieldEnd.NEAR.name}")
            .performScrollTo()
            .performClick()
        closeSetupEditor()
        waitForText("First-half Gen Zone: Road")
        waitForText("Gen Zone switches at halftime")

        // Gen Zone setup can also keep the same zone for the whole game.
        openMixedGenderRatioEditor()
        composeRule.onNodeWithTag("setup-switch-gen-zone-at-halftime")
            .performClick()
        composeRule.onNodeWithTag("setup-gender-ratio-set").performClick()
        waitForText("Game to")
        closeSetupEditor()
        waitForText("Gen Zone: Road")
        composeRule.onAllNodesWithText("First-half Gen Zone: Road").assertCountEquals(0)
        waitForText("Gen Zone stays the same all game")
        openStartingPullSetupEditor()
        composeRule.onNodeWithText("End for gen zone")
            .performScrollTo()
            .assertIsDisplayed()
        composeRule.onAllNodesWithText("End for gen zone in first half")
            .assertCountEquals(0)
        closeSetupEditor()

        // Fixed mixed ratios do not add a starting-pull choice to the field editor.
        openMixedGenderRatioEditor()
        composeRule.onNodeWithTag("setup-gender-ratio-rule-${GenderRatioRule.FIXED_4W_3M.name}")
            .performClick()
        waitForText("Fixed gender ratio.")
        composeRule.onNodeWithTag("setup-gender-ratio-set").performClick()
        waitForText("Game to")
        closeSetupEditor()
        openStartingPullSetupEditor()
        composeRule.onAllNodesWithText("First point gender ratio").assertCountEquals(0)
        composeRule.onAllNodesWithText("gen zone").assertCountEquals(0)
        closeSetupEditor()
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
        waitForText("Start game")

        // Mixed games expose gender-ratio controls in the game-rules editor.
        openGameInformationSetupEditor()
        composeRule.onNodeWithTag("setup-game-division-${GameDivision.MIXED.name}")
            .performScrollTo()
            .performClick()
        closeSetupEditor()
        waitForText("Start game")

        // Cancel on the game-rules editor should discard draft rule changes.
        openGameRulesSetupEditor()
        composeRule.onNodeWithText("Cancel").assertIsDisplayed()
        composeRule.onNodeWithText("Game to").performScrollTo().performClick()
        waitForText("Points")
        composeRule.onNodeWithText("Points").performTextReplacement("19")
        composeRule.onNodeWithTag("setup-integer-set").performClick()
        waitForText("Game to")
        composeRule.onNodeWithText("Cancel").performClick()
        waitForText("Start game")
        waitForText("Game to 15")
        composeRule.onAllNodesWithText("Game to 19").assertCountEquals(0)

        // Dismissal follows Done so edited setup rules are kept.
        openGameRulesSetupEditor()
        composeRule.onNodeWithText("Game to").performScrollTo().performClick()
        waitForText("Points")
        composeRule.onNodeWithText("Points").performTextReplacement("19")
        composeRule.onNodeWithTag("setup-integer-set").performClick()
        waitForText("Game to")
        dismissDialog(text = "Done")
        waitForText("Game to 19")

        // Empty numeric rule entries should fall back to the current value.
        setIntegerSetupValue("Game to", "Game to", "Points", "")
        setIntegerSetupValue("Time between points", "Time between points", "Seconds", "")
        setIntegerSetupValue("Timeout duration", "Timeout duration", "Seconds", "")
        setCapRuleValue("Half cap", "Half cap", "")
        setTimeoutRules(timeoutsPerHalf = "", hasFloater = false)

        // Focused rule dialogs can be canceled without closing the game-rules editor.
        openGameRulesSetupEditor()
        listOf(
            "Game to",
            "Half cap",
            "Soft cap",
            "Hard cap",
            "Halftime",
            "Timeouts",
            "Time between points",
            "Timeout duration",
            "Water breaks",
        )
            .forEach { ruleLabel ->
                composeRule.onNodeWithText(ruleLabel).performScrollTo().performClick()
                waitForText("Cancel")
                if (ruleLabel == "Time between points") {
                    waitForText(
                        "Defense has up to 20 seconds after this time to pull.",
                        substring = true,
                    )
                }
                if (ruleLabel == "Timeout duration") {
                    waitForText(
                        "Defense has up to 20 seconds after this time to check the disc in.",
                        substring = true,
                    )
                }
                composeRule.onAllNodesWithText("Cancel").onLast().performClick()
                waitForText("Game to")
            }
        closeSetupEditor()
        waitForText("Start game")

        // Numeric rule editors should accept direct values.
        setIntegerSetupValue("Game to", "Game to", "Points", "7")
        setIntegerSetupValue("Halftime", "Halftime", "Minutes", "2")
        setIntegerSetupValue("Time between points", "Time between points", "Seconds", "50")
        setIntegerSetupValue("Timeout duration", "Timeout duration", "Seconds", "55")
        assertSetupSummaryTextVisible("Game to 7")
        assertSetupSummaryTextVisible("Half: 2 min")
        assertSetupSummaryTextVisible("Time between points: 50 sec")
        assertSetupSummaryTextVisible("Timeout duration: 55 sec")

        // Cap editors should support changing, disabling, and re-enabling cap values.
        setCapRuleValue("Half cap", "Half cap", "30")
        setCapRuleToNone("Half cap", "Half cap")
        setCapRuleValue("Soft cap", "Soft cap", "12")
        setCapRuleToNone("Soft cap", "Soft cap")
        setCapRuleToNone("Hard cap", "Hard cap")
        setCapRuleValue("Hard cap", "Hard cap", "20", enableFromNone = true)

        // Mixed gender-ratio controls should update the compact game-rules summary.
        openMixedGenderRatioEditor()
        composeRule.onNodeWithTag("setup-gender-ratio-rule-${GenderRatioRule.GEN_ZONE.name}")
            .performClick()
        composeRule.onNodeWithTag("setup-switch-gen-zone-at-halftime")
            .performClick()
        composeRule.onNodeWithTag("setup-gender-ratio-set").performClick()
        waitForText("Game to")
        closeSetupEditor()
        waitForText("Ratio: Gen Zone")
        waitForText("Gen Zone stays the same all game")

        // Timeout rules should accept a floater timeout configuration.
        setTimeoutRules(timeoutsPerHalf = "3", hasFloater = true)

        // Water-break rules should cover disabled, manual, and automatic choices.
        openGameRulesSetupEditor()
        composeRule.onNodeWithText("Water breaks").performScrollTo().performClick()
        waitForText("Allow extra time between points for hydration or shade.", substring = true)
        composeRule.onNodeWithTag("setup-water-break-NONE").assertIsSelected()
        composeRule.onNodeWithText("Minutes").assertIsNotEnabled()
        composeRule.onNodeWithTag("setup-water-break-MANUAL").performClick()
        composeRule.onNodeWithText("Minutes").performTextReplacement("4")
        composeRule.onNodeWithTag("setup-water-breaks-set").performClick()
        waitForText("4 min")
        closeSetupEditor()
        assertSetupSummaryTextVisible("Water breaks: 4 min")

        openGameRulesSetupEditor()
        composeRule.onNodeWithText("Water breaks").performScrollTo().performClick()
        composeRule.onNodeWithTag("setup-water-break-AUTOMATIC").performClick()
        waitForText("Breaks will be offered when a team reaches 2 or 6 points.")
        composeRule.onNodeWithText("Minutes").performTextReplacement("5")
        composeRule.onNodeWithTag("setup-water-breaks-set").performClick()
        waitForText("2/6, 5 min")
        closeSetupEditor()
        assertSetupSummaryTextVisible("Water breaks: 2/6, 5 min")

        openGameRulesSetupEditor()
        composeRule.onNodeWithText("Water breaks").performScrollTo().performClick()
        composeRule.onNodeWithText("Minutes").performTextReplacement("")
        composeRule.onNodeWithTag("setup-water-breaks-set").performClick()
        waitForText("2/6, 5 min")
        closeSetupEditor()
        assertSetupSummaryTextVisible("Water breaks: 2/6, 5 min")

        // USAU defaults should restore the expected compact rule summary.
        openGameRulesSetupEditor()
        composeRule.onNodeWithTag("setup-usau-defaults").performScrollTo().performClick()
        waitForText("+105")
        closeSetupEditor()
        assertSetupSummaryTextVisible("Time between points: 60 sec")
        assertSetupSummaryTextVisible("Timeout duration: 70 sec")
        composeRule.onAllNodesWithText("Water breaks:", substring = true).assertCountEquals(0)
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
        composeRule.onNodeWithText("Level").performScrollTo().assertIsDisplayed()
        closeSetupEditor()

        // Level changes follow USAU between-points defaults while the timing is still default.
        openGameInformationSetupEditor()
        composeRule.onNodeWithTag("setup-game-level-Youth")
            .performScrollTo()
            .performClick()
        closeSetupEditor()
        assertSetupSummaryTextVisible("Time between points: 80 sec")
        openGameRulesSetupEditor()
        waitForText("Reset to USAU (Youth) defaults")
        composeRule.onNodeWithText("Reset to USAU (Youth) defaults")
            .performScrollTo()
            .assertIsDisplayed()
        closeSetupEditor()
        openGameInformationSetupEditor()
        composeRule.onNodeWithTag("setup-game-level-College")
            .performScrollTo()
            .performClick()
        closeSetupEditor()
        assertSetupSummaryTextVisible("Time between points: 60 sec")

        // Optional tournament, division, level, context, and observer fields should persist.
        openGameInformationSetupEditor()
        composeRule.onNodeWithTag("setup-tournament-name")
            .performTextReplacement("College Nationals")
        composeRule.onNodeWithTag("setup-tournament-name").performImeAction()
        composeRule.onNodeWithTag("setup-game-division-${GameDivision.OPEN.name}")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("setup-game-level-Great-Grandmasters")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("setup-game-level-other")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("setup-game-level-other-text")
            .performTextReplacement("Community showcase")
        composeRule.onNodeWithTag("setup-game-level-other-text").performImeAction()
        composeRule.onNodeWithTag("setup-game-level-other")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("setup-game-level-NA")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("setup-game-level-other")
            .performScrollTo()
            .performClick()

        // If custom level text happens to match a preset, Other stays selected rather than
        // making the matching preset look active.
        composeRule.onNodeWithTag("setup-game-level-other-text")
            .performTextReplacement("College")
        composeRule.onNodeWithTag("setup-game-level-other")
            .performScrollTo()
            .assertIsSelected()
        composeRule.onNodeWithTag("setup-game-level-College")
            .performScrollTo()
            .assertIsNotSelected()

        composeRule.onNodeWithTag("setup-game-level-other-text")
            .performTextReplacement("Community showcase")
        composeRule.onNodeWithTag("setup-game-context").performTextReplacement("Semifinals")
        composeRule.onNodeWithTag("setup-game-context").performImeAction()

        // Observer rows can add another row, but only the last blank row can be removed.
        composeRule.onAllNodesWithTag("setup-remove-observer-0").assertCountEquals(0)
        composeRule.onAllNodesWithTag("setup-remove-observer-1").assertCountEquals(1)
        assertSingleSetupAddObserver()
        composeRule.onNodeWithTag("setup-remove-observer-1")
            .performScrollTo()
            .performClick()
        composeRule.onAllNodesWithTag("setup-remove-observer-0").assertCountEquals(0)
        composeRule.onAllNodesWithTag("setup-observer-1").assertCountEquals(0)
        assertSingleSetupAddObserver()
        composeRule.onNodeWithTag("setup-observer-0").performTextReplacement("Mike")
        composeRule.onNodeWithTag("setup-observer-0").performImeAction()
        composeRule.onNodeWithTag("setup-add-observer")
            .performScrollTo()
            .performClick()
        assertSingleSetupAddObserver()
        composeRule.onNodeWithTag("setup-observer-1").performTextReplacement("Gary")
        composeRule.onNodeWithTag("setup-observer-1").performImeAction()
        composeRule.onNodeWithTag("setup-add-observer")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("setup-observer-2")
            .performScrollTo()
            .performTextReplacement("Bill")
        composeRule.onNodeWithTag("setup-observer-2").performImeAction()
        composeRule.onAllNodesWithTag("setup-remove-observer-2").assertCountEquals(0)
        assertSingleSetupAddObserver()
        composeRule.onNodeWithTag("setup-field-name").performTextReplacement("Field 7")
        composeRule.onNodeWithTag("setup-field-name").performImeAction()
        closeSetupEditor()

        // The setup screen should show the populated game-information summary.
        waitForText("College Nationals")
        waitForText("Open Division")
        waitForText("Community showcase")
        waitForText("Semifinals")
        waitForText("Observers: Mike, Gary, Bill")
        waitForText("Field: Field 7")

        // Tapping inert dialog body space should defocus the text field, but Cancel remains the
        // explicit discard path for this editor.
        openGameInformationSetupEditor()
        composeRule.onNodeWithTag("setup-tournament-name")
            .performTextReplacement("Canceled Tournament")
        composeRule.onNodeWithText("Level").performTouchInput { click() }
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onAllNodesWithText("Canceled Tournament").assertCountEquals(0)
        waitForText("College Nationals")

        // Dismissal follows Done so text-entry setup edits survive accidental outside taps.
        openGameInformationSetupEditor()
        composeRule.onNodeWithTag("setup-tournament-name")
            .performTextReplacement("Dismissed Tournament")
        dismissDialog(text = "Done", clearKeyboard = true)
        waitForText("Dismissed Tournament")

        // Dismissal also follows Done when no text field has focus.
        openGameInformationSetupEditor()
        dismissDialog(text = "Done")
        waitForText("Dismissed Tournament")
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
        composeRule.onNodeWithText("Add").assertIsNotEnabled()
        dismissDialog(text = "Cancel")
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
        composeRule.onNodeWithText("Yellows: 2").assertIsDisplayed()
        closeSetupEditor()
        waitForText("Start game")

        // The edit dialog can be canceled back to the prior-card editor.
        openPriorCardsSetupEditor(TeamId.TEAM_ONE)
        composeRule.onNodeWithTag("setup-prior-card-edit-0").performScrollTo().performClick()
        waitForText("Edit previous game card holder")
        dismissDialog(text = "Cancel")
        waitForText("Add card holder")

        // Editing should allow changing both jersey and name on an existing prior-card holder.
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

        // Removing the zero-prior player asks for confirmation and can be canceled.
        composeRule.onNodeWithTag("setup-prior-card-remove-0").performScrollTo().performClick()
        waitForText("Remove card holder?")
        dismissDialog(text = "Cancel")
        composeRule.onNodeWithText("#67 Sideline Caller").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("setup-prior-card-remove-0").performScrollTo().performClick()
        waitForText("Remove card holder?")
        composeRule.onNodeWithText("Remove").performClick()
        waitForText("No card holders added yet")

        // A card holder can be identified by name alone when no jersey number is known.
        composeRule.onNodeWithText("Add card holder").performClick()
        waitForText("Add previous game card holder")
        enterPriorCardName("No Number Yet")
        composeRule.onNodeWithText("Add").performClick()
        composeRule.onNodeWithText("No Number Yet").performScrollTo().assertIsDisplayed()
    }

    /**
     * Test duplicate and possible-match handling for prior-card holders during setup.
     */
    @Test
    fun priorCardMatching() {
        openNewGameSetup()

        // A normalized duplicate number/name should match the existing row rather than add a copy.
        addPriorCardHolderProgrammatically(
            TeamId.TEAM_TWO,
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
        composeRule.onNodeWithText("Yellows: 2, Reds: 1").assertIsDisplayed()
        closeSetupEditor()

        // A number-only existing row should reject silently replacing it with a new named row.
        addPriorCardHolderProgrammatically(
            TeamId.TEAM_TWO,
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
        dismissDialog(tag = "setup-existing-card-holder-back")
        waitForText("Add previous game card holder")
        composeRule.onNodeWithText("Add").performClick()
        waitForText("Card holder already listed")
        composeRule.onNodeWithText("OK").performClick()
        waitForText("#77")
        composeRule.onAllNodesWithText("#77 Named Later").assertCountEquals(0)
        closeSetupEditor()

        // Separate number-only and name-only rows should be treated as one existing player match.
        addPriorCardHolderProgrammatically(
            TeamId.TEAM_TWO,
            jersey = "",
            playerName = "Jarvis",
            yellows = 1,
            reds = 0,
        )
        addPriorCardHolderProgrammatically(
            TeamId.TEAM_TWO,
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

        // A possible number match should support backing out before adding a separate player.
        openPriorCardsSetupEditor()
        composeRule.onNodeWithText("Add card holder").performClick()
        waitForText("Add previous game card holder")
        enterPriorCardJersey("88")
        enterPriorCardName("Other Player")
        composeRule.onNodeWithText("Add").performClick()
        waitForText("Possible player match")
        dismissDialog(text = "Cancel")
        waitForText("Add previous game card holder")
        composeRule.onNodeWithText("Add").performClick()
        waitForText("Possible player match")
        composeRule.onNodeWithText("Add").performClick()
        composeRule.onNodeWithText("#88 Other Player").performScrollTo().assertIsDisplayed()
        closeSetupEditor()

        // Removing the numbered row should leave unrelated name-only rows visible.
        addPriorCardHolderProgrammatically(
            TeamId.TEAM_TWO,
            jersey = "",
            playerName = "A Very Long Player Name With No Number",
            yellows = 1,
            reds = 0,
        )
        waitForText("#88: Y 1", substring = true)
        openPriorCardsSetupEditor()
        composeRule.onNodeWithText("#88 Numbered Player").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("setup-prior-card-remove-0").performScrollTo().performClick()
        waitForText("Remove card holder?")
        composeRule.onNodeWithText("Remove").performClick()
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
        updateCurrentStateProgrammatically {
            copy(
                teamOnePlayers = listOf(playerRecordWithCards("9", yellows = 1)),
            )
        }

        // Removing that row should be blocked and should leave the player visible.
        openPriorCardsSetupEditor(TeamId.TEAM_ONE)
        composeRule.onNodeWithText("#9").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("setup-prior-card-remove-0").performScrollTo().performClick()
        waitForText("Remove card holder?")
        composeRule.onNodeWithText("Remove").performClick()

        waitForText("Player not deleted")
        waitForText("#9 has an in-game card and cannot be deleted.")
        dismissDialog(text = "OK")
        composeRule.onNodeWithText("#9").performScrollTo().assertIsDisplayed()
    }

    /**
     * Test setup protection against merging rows that already have current-game cards.
     */
    @Test
    fun rejectMergingCardedRows() {
        openNewGameSetup()

        // Seed separate number-only and name-only rows that both have current-game card state.
        updateCurrentStateProgrammatically {
            copy(
                teamOnePlayers = listOf(
                    playerRecordWithCards("23", yellows = 1),
                    playerRecordWithCards("", yellows = 1, playerName = "Jarvis"),
                ),
            )
        }

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

    /// Assert that only one add-observer action is currently visible.
    private fun assertSingleSetupAddObserver() {
        composeRule.onAllNodesWithTag("setup-add-observer").assertCountEquals(1)
    }

    /// Scroll to a setup summary line and assert it is visible.
    private fun assertSetupSummaryTextVisible(text: String) {
        waitForText(text)
        composeRule.onNodeWithText(text).performScrollTo().assertIsDisplayed()
    }

    /// Open the focused mixed gender-ratio rule editor from the game-rules dialog.
    private fun openMixedGenderRatioEditor() {
        openGameRulesSetupEditor()
        composeRule.onNodeWithText("Mixed gender ratio").performScrollTo().performClick()
        waitForText("Set")
    }
}
