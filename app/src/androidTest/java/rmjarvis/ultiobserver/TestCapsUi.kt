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
import androidx.compose.ui.test.performScrollTo
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
        // Half cap can be applied from its confirmation prompt.
        startLiveGameWithDueCap("Half cap", "Half cap")
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "goal")).performClick()
        waitForText("Apply half cap?")
        composeRule.onNodeWithText("Apply").performClick()
        waitForText("Undo Apply half cap")

        // Half cap can also be deferred from its confirmation prompt.
        startLiveGameWithDueCap("Half cap", "Half cap")
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "goal")).performClick()
        waitForText("Apply half cap?")
        composeRule.onNodeWithText("No").performClick()
        assertLiveScreen()

        // Soft cap can be applied from its confirmation prompt.
        startLiveGameWithDueCap("Soft cap", "Soft cap")
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "goal")).performClick()
        waitForText("Apply soft cap?")
        composeRule.onNodeWithText("Apply").performClick()
        waitForText("Undo Apply soft cap")

        // Soft cap can also be deferred.
        startLiveGameWithDueCap("Soft cap", "Soft cap")
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "goal")).performClick()
        waitForText("Apply soft cap?")
        composeRule.onNodeWithText("No").performClick()
        assertLiveScreen()

        // A soft cap scheduled during halftime should say it is scheduled, not already past.
        startLiveGameWithCapDuringHalftime("Soft cap", "Soft cap")
        openMoreActionsDialog()
        composeRule.onNodeWithText("Start halftime").performClick()
        waitForText("Apply soft cap?")
        waitForText("is scheduled for", substring = true)
        composeRule.onNodeWithText("No").performClick()
        waitForText("Halftime")
        composeRule.onAllNodesWithText("OK").onLast().performClick()

        // Hard cap can be deferred, then applied on a tied score to keep the game live.
        startLiveGameWithDueCap("Hard cap", "Hard cap")
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "goal")).performClick()
        waitForText("Apply hard cap?")
        composeRule.onNodeWithText("No").performClick()
        assertLiveScreen()
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_TWO, "goal")).performClick()
        waitForText("Apply hard cap?")
        composeRule.onNodeWithText("Apply").performClick()
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
        composeRule.onNodeWithText("Apply half cap now").performScrollTo().performClick()
        waitForText("Undo Apply half cap now")
        assertLiveScreen()

        // Soft cap can be applied manually from More actions.
        startLiveGameProgrammatically()
        openMoreActionsDialog()
        composeRule.onNodeWithText("Apply soft cap now").performScrollTo().performClick()
        waitForText("Undo Apply soft cap now")
        assertLiveScreen()

        // Hard cap can be applied manually from More actions.
        startLiveGameProgrammatically()
        openMoreActionsDialog()
        composeRule.onNodeWithText("Apply hard cap now").performScrollTo().performClick()
        waitForText("Undo Apply hard cap now")
        assertLiveScreen()
    }

    /**
     * Test water-break prompts caused by applying soft cap.
     */
    @Test
    fun softCapWaterBreakPrompts() {
        setAutomaticallyLockLivePoint(false)

        val rules = GameRules(
            gameTo = 15,
            useHalfCap = false,
            useSoftCap = true,
            softCapMinutes = 90,
            useHardCap = true,
            waterBreakMode = WaterBreakMode.AUTOMATIC,
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
        waitForText("Apply soft cap?")
        composeRule.onNodeWithText("Apply").performClick()
        waitForText("Take a 3 minute water break now?")
        composeRule.onNodeWithText("Yes").performClick()
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
        composeRule.onNodeWithText("Apply soft cap now").performScrollTo().performClick()
        waitForText("Take a 3 minute water break now?")
        composeRule.onNodeWithText("No").performClick()
        waitForText("Undo Apply soft cap now")

        // Applying hard cap after soft cap is already applied should not offer another soft-cap
        // water break.
        openMoreActionsDialog()
        composeRule.onNodeWithText("Apply hard cap now").performScrollTo().performClick()
        waitForText("Undo Apply hard cap now")
        composeRule.onAllNodesWithText("Take a 3 minute water break now?").assertCountEquals(0)
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
