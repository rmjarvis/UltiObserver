package rmjarvis.ultiobserver

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.swipeRight
import java.time.LocalTime
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/// Tests for cap prompts and cap-related live UI pathways.
@RunWith(AndroidJUnit4::class)
class TestCapsUi : MainActivityUiTestFixtures() {
    /**
     * Test the cap confirmation prompts that appear after a point ends with a due cap.
     * Each cap gets its own short game because the prompt state blocks normal live interaction.
     */
    @Test
    fun capPromptPathways() {
        setRuleGuidanceMode(RuleGuidanceMode.FULL)

        // Half cap can be applied from its confirmation prompt.
        startLiveGameWithDueCap("Half cap", "Half cap")
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "goal")).performClick()
        waitForText("Half cap")
        composeRule.onNodeWithText("OK").performClick()
        waitForText("Undo Apply half cap")

        // Half cap can also be deferred from its confirmation prompt.
        startLiveGameWithDueCap("Half cap", "Half cap")
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "goal")).performClick()
        waitForText("Half cap")
        composeRule.onNodeWithText("Not yet").performClick()
        assertLiveScreen()

        // Soft cap can be applied from its confirmation prompt.
        startLiveGameWithDueCap("Soft cap", "Soft cap")
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "goal")).performClick()
        waitForText("Soft cap")
        composeRule.onNodeWithText("OK").performClick()
        waitForText("Undo Apply soft cap")

        // Soft cap can also be deferred.
        startLiveGameWithDueCap("Soft cap", "Soft cap")
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "goal")).performClick()
        waitForText("Soft cap")
        composeRule.onNodeWithText("Not yet").performClick()
        assertLiveScreen()

        // A future soft cap during halftime should say when it is scheduled and why it applies.
        startLiveGameWithCapDuringHalftime("Soft cap", "Soft cap")
        openMoreActionsDialog()
        selectMoreActionsCategory("Manual game transitions")
        composeRule.onNodeWithText("Start halftime").performClick()
        waitForText("Soft cap")
        waitForText("is scheduled for", substring = true)
        waitForText("which is during halftime, so we can apply it now", substring = true)
        waitForText("The new winning score is 2.", substring = true)
        composeRule.onNodeWithText("Not yet").performClick()
        waitForText("Halftime")
        composeRule.onAllNodesWithText("OK").onLast().performClick()

        // Hard cap can be deferred, then applied on a tied score to keep the game live.
        startLiveGameWithDueCap("Hard cap", "Hard cap")
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "goal")).performClick()
        waitForText("Hard cap")
        composeRule.onNodeWithText("Not yet").performClick()
        assertLiveScreen()
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_TWO, "goal")).performClick()
        waitForText("Hard cap")
        composeRule.onNodeWithText("OK").performClick()
        waitForText("Undo Apply hard cap")
        assertLiveScreen()
    }

    /**
     * Test that manually applying caps from More actions updates the live game.
     */
    @Test
    fun manualCapActions() {
        // Half cap can be applied manually from More actions.
        startLiveGameProgrammatically()
        openMoreActionsDialog()
        selectMoreActionsCategory("Manual game transitions")
        clickMoreActionsItem("Apply half cap now")
        waitForText("Undo Apply half cap now")
        assertLiveScreen()

        // Soft cap can be applied manually from More actions.
        startLiveGameProgrammatically()
        openMoreActionsDialog()
        selectMoreActionsCategory("Manual game transitions")
        clickMoreActionsItem("Apply soft cap now")
        waitForText("Undo Apply soft cap now")
        assertLiveScreen()

        // Hard cap can be applied manually from More actions.
        startLiveGameProgrammatically()
        openMoreActionsDialog()
        selectMoreActionsCategory("Manual game transitions")
        clickMoreActionsItem("Apply hard cap now")
        waitForText("Undo Apply hard cap now")
        assertLiveScreen()
    }

    /**
     * Test water-break prompts caused by applying soft cap.
     */
    @Test
    fun softCapWaterBreakPrompts() {
        setRuleGuidanceMode(RuleGuidanceMode.FULL)
        setAutomaticallyLockLivePoint(false)

        val rules = GameRules(
            gameTo = 15,
            useHalfCap = false,
            useSoftCap = true,
            nominalSoftCapMinutes = 90,
            useHardCap = true,
            heatLevel = HeatLevel.LEVEL_1,
            waterBreakMinutes = 3,
        )
        val testNow = System.currentTimeMillis()
        val gameStart = testNow - 91 * 60_000L
        val preGoalScoreTime = testNow
        val baseSetup = newSetupGameState(now = testNow)
        val start = localDateTimeFromEpoch(gameStart, baseSetup.timeZone)
        val setup = baseSetup.copy(
            startDate = start.toLocalDate(),
            startTime = start.toLocalTime(),
            rules = rules,
        )

        // Applying a pending soft cap before the first-quarter break score offers the water break.
        startLivePointProgrammatically(setup)
        updateCurrentStateProgrammatically {
            adjustScore(teamOneScore = 2, teamTwoScore = 3, now = preGoalScoreTime)
        }
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "goal")).performClick()
        waitForText("Soft cap")
        composeRule.onNodeWithText("OK").performClick()
        waitForText(
            "Soft cap triggers the first-quarter water break.\n" +
            "Take a 3-minute water break now."
        )
        composeRule.onNodeWithText("OK").performClick()
        waitForText("Undo Water break")

        // Applying soft cap from More actions uses the same water-break prompt but can be rejected.
        val moreActionsStart = localDateTimeFromEpoch(gameStart + 2 * 60_000L, setup.timeZone)
        val moreActionsSetup = setup.copy(
            startDate = moreActionsStart.toLocalDate(),
            startTime = moreActionsStart.toLocalTime(),
        )
        startBetweenPointsProgrammatically(moreActionsSetup)
        updateCurrentStateProgrammatically {
            adjustScore(teamOneScore = 3, teamTwoScore = 3, now = preGoalScoreTime)
        }
        openMoreActionsDialog()
        selectMoreActionsCategory("Manual game transitions")
        clickMoreActionsItem("Apply soft cap now")
        waitForText(
            "Soft cap triggers the first-quarter water break.\n" +
            "Take a 3-minute water break now."
        )
        composeRule.onNodeWithText("Not yet").performClick()
        waitForText("Undo Apply soft cap now")
    }

    /**
     * Test that the various "Apply cap now" buttons in the More actions menu are hidden
     * when it would be invalid to apply the cap at that time.  This includes because
     * the cap has already been applied, or the cap is not relevant anymore (half cap
     * after half time for instance), or the caps are not enabled in this game.
     */
    @Test
    fun capVisibilityInMoreActions() {
        // If the caps have already been applied, they cannot be applied again.
        startLiveGameProgrammatically()
        updateCurrentStateProgrammatically {
            copy(
                halfCapApplied = true,
                softCapApplied = true,
                hardCapApplied = true,
            )
        }
        openMoreActionsDialog()
        selectMoreActionsCategory("Manual game transitions")
        assertTrue(
            composeRule.onAllNodesWithText("Apply half cap now")
                .fetchSemanticsNodes()
                .isEmpty()
        )
        assertTrue(
            composeRule.onAllNodesWithText("Apply soft cap now")
                .fetchSemanticsNodes()
                .isEmpty()
        )
        assertTrue(
            composeRule.onAllNodesWithText("Apply hard cap now")
                .fetchSemanticsNodes()
                .isEmpty()
        )

        // Once halftime has already happened, half cap should stay hidden even if not applied.
        updateCurrentStateProgrammatically {
            copy(
                halftimeTaken = true,
                halfCapApplied = false,
                softCapApplied = false,
                hardCapApplied = false,
            )
        }
        assertTrue(
            composeRule.onAllNodesWithText("Apply half cap now")
                .fetchSemanticsNodes()
                .isEmpty()
        )

        // Disabled cap rules should not expose manual cap actions.
        updateCurrentStateProgrammatically {
            copy(
                rules = rules.copy(
                    useHalfCap = false,
                    useSoftCap = false,
                    useHardCap = false,
                ),
                halftimeTaken = false,
                halfCapApplied = false,
                softCapApplied = false,
                hardCapApplied = false,
            )
        }
        assertTrue(
            composeRule.onAllNodesWithText("Apply half cap now")
                .fetchSemanticsNodes()
                .isEmpty()
        )
        assertTrue(
            composeRule.onAllNodesWithText("Apply soft cap now")
                .fetchSemanticsNodes()
                .isEmpty()
        )
        assertTrue(
            composeRule.onAllNodesWithText("Apply hard cap now")
                .fetchSemanticsNodes()
                .isEmpty()
        )
    }
}
