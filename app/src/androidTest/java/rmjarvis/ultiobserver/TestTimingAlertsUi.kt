package rmjarvis.ultiobserver

import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.espresso.Espresso.pressBackUnconditionally
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Test
import org.junit.runner.RunWith

/// Tests for timing-alert permission prompts that are reached through the app UI.
@RunWith(AndroidJUnit4::class)
class TestTimingAlertsUi : MainActivityUiTestFixtures() {
    /**
     * Test the cap-alert permission dialog actions when Android requires exact-alarm access.
     *
     * Some emulators, currently including Pixel 7 API 33, do not grant exact-alarm access by
     * default.  On those devices, starting a new game with cap alerts enabled shows a warning
     * before the game starts.  This test clicks the settings link, returns to UltiObserver, then
     * starts again and uses the Ignore path so the app still reaches the live game screen.  Devices
     * that already allow exact alarms skip the dialog-specific checks and simply prove that setup
     * can start normally.
     */
    @Test
    fun capAlertPermissionDialog() {
        openNewGameSetup()

        // Starting setup either shows the exact-alarm warning or goes directly to the live screen,
        // depending on this emulator's Android alarm policy.
        composeRule.onNodeWithText("Start game").performClick()
        composeRule.waitForIdle()
        if (
            composeRule.onAllNodesWithText("Cap alert permission")
                .fetchSemanticsNodes()
                .isEmpty()
        ) {
            assertLiveScreen()
            return
        }

        // The settings link leaves the app for Android's Alarms & reminders settings.  Some
        // Android versions return to the launcher when backing out of that Settings page, so bring
        // UltiObserver foreground again and verify setup state is still waiting.
        composeRule.onNodeWithText("Open settings").performClick()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        pressBackUnconditionally()
        returnToUltiObserver()
        waitForText("Setup game")
        composeRule.onNodeWithText("Start game").assertIsDisplayed()

        // If the user still has not enabled exact alarms, Ignore starts the game anyway.
        composeRule.onNodeWithText("Start game").performClick()
        waitForText("Cap alert permission")
        composeRule.onNodeWithText("Ignore").performClick()
        assertLiveScreen()
    }

    /// Bring the app back to the foreground after Android Settings has taken focus.
    private fun returnToUltiObserver() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val launchIntent = context.packageManager.getLaunchIntentForPackage(context.packageName)!!
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(launchIntent)
    }
}
