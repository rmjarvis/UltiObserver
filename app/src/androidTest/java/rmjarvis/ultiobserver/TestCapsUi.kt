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

@RunWith(AndroidJUnit4::class)
class TestCapsUi : MainActivityUiTestFixtures() {
    /**
     * Test the cap confirmation prompts that appear after a point ends with a due cap.
     * Each cap gets its own short game because the prompt state blocks normal live interaction.
     */
    @Test
    fun capPromptPathways() {
        // Half cap can be applied from its confirmation prompt.
        startLiveGameWithDueCap("Half cap", "Half Cap")
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "goal")).performClick()
        waitForText("Apply half cap?")
        composeRule.onNodeWithText("Apply").performClick()
        waitForText("Undo Apply Half Cap")

        // Half cap can also be deferred from its confirmation prompt.
        startLiveGameWithDueCap("Half cap", "Half Cap")
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "goal")).performClick()
        waitForText("Apply half cap?")
        composeRule.onNodeWithText("No").performClick()
        assertLiveScreen()

        // Soft cap can be applied from its confirmation prompt.
        startLiveGameWithDueCap("Soft cap", "Soft Cap")
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "goal")).performClick()
        waitForText("Apply soft cap?")
        composeRule.onNodeWithText("Apply").performClick()
        waitForText("Undo Apply Soft Cap")

        // Soft cap can also be deferred.
        startLiveGameWithDueCap("Soft cap", "Soft Cap")
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "goal")).performClick()
        waitForText("Apply soft cap?")
        composeRule.onNodeWithText("No").performClick()
        assertLiveScreen()

        // A soft cap scheduled during halftime should say it is scheduled, not already past.
        startLiveGameWithCapDuringHalftime("Soft cap", "Soft Cap")
        openOtherSheet()
        composeRule.onNodeWithText("Start Halftime").performClick()
        waitForText("Apply soft cap?")
        waitForText("is scheduled for", substring = true)
        composeRule.onNodeWithText("No").performClick()
        waitForText("Halftime")
        composeRule.onNodeWithText("OK").performClick()

        // Hard cap can be deferred, then applied on a tied score to keep the game live.
        startLiveGameWithDueCap("Hard cap", "Hard Cap")
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "goal")).performClick()
        waitForText("Apply hard cap?")
        composeRule.onNodeWithText("No").performClick()
        assertLiveScreen()
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_TWO, "goal")).performClick()
        waitForText("Apply hard cap?")
        composeRule.onNodeWithText("Apply").performClick()
        waitForText("Undo Apply Hard Cap")
        assertLiveScreen()
    }
}
