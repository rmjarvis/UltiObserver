package rmjarvis.ultiobserver

import android.graphics.Bitmap
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import org.junit.Rule
import org.junit.Test

/** Capture the real paired watch at two stages of the phone's release screenshot narrative. */
class GenerateWatchReleaseScreenshots {
    @get:Rule
    val composeRule = createAndroidComposeRule<WatchActivity>()

    /** Show the between-points score, cap time, pull direction, ratio, and timing cue. */
    @Test
    fun captureMainScreen() {
        waitForText("Red Fish Blue Fish")
        composeRule.onNodeWithText("4").assertIsDisplayed()
        composeRule.onNodeWithText("5").assertIsDisplayed()
        composeRule.onNodeWithTag("countdown-value", useUnmergedTree = true).assertIsDisplayed()
        capture("WatchMainScreen.png")
    }

    /** Open the team's card chooser through the watch's normal controls. */
    @Test
    fun captureCardChoices() {
        waitForText("Red Fish Blue Fish")
        composeRule.onNodeWithText("Red Fish Blue Fish").performClick()
        waitForText("Card (1)")
        capture("WatchTeamActions.png")
        composeRule.onNodeWithText("Card (1)").performClick()
        waitForText("Yellow")
        composeRule.onNodeWithText("Red").assertIsDisplayed()
        composeRule.onNodeWithText("Blue").assertIsDisplayed()
        capture("WatchCardChoices.png")
    }

    private fun waitForText(text: String) {
        catchAndDiagnoseFailure(composeRule) {
            composeRule.waitUntil(timeoutMillis = 30_000) {
                composeRule.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty()
            }
        }
    }

    /** Save the full square display; the host removes the alpha channel for Play assets. */
    private fun capture(filename: String) {
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        Thread.sleep(500)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "release-screenshots")
        directory.mkdirs()
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        File(directory, filename).outputStream().use { stream ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                "Could not write $filename"
            }
        }
        bitmap.recycle()
    }
}
