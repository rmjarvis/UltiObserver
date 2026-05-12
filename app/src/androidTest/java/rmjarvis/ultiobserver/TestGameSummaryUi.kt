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
class TestGameSummaryUi : MainActivityUiTestFixtures() {
    // Test the game-over summary branch for teams with no player-specific cards.
    @Test
    fun gameSummaryShowsNoIssuedPlayerCards() {
        startLiveGameProgrammatically()

        endCurrentGameProgrammatically()
        composeRule.onNodeWithText("OK").performClick()
        waitForText("Game Summary")
        waitForText("No yellow or red cards issued.")
    }
}
