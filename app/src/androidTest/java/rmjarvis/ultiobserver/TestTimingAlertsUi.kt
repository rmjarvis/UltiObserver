package rmjarvis.ultiobserver

import android.accessibilityservice.AccessibilityService
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
     * In our test matrix, we explicitly set the Pixel 7 API 33 device to have
     * SCHEDULE_EXACT_ALARM deny, so it will trigger the permission dialog.
     * Starting a new game with cap alerts enabled shows a warning before the game starts.
     * This test clicks the settings link, returns to UltiObserver, then starts again and
     * uses the Ignore path so the app still reaches the live game screen.
     * Devices that already allow exact alarms skip the dialog-specific checks and simply
     * prove that setup can start normally.
     */
    @Test
    fun capAlertPermissionDialog() {
        openNewGameSetup()

        // Set up the settings and game rules so that alarms are required.
        // * global mode needs to be vibration or sound.
        // * at least one cap needs to have a sound/vibrate action and be enabled in the rules.
        // Here we turn on all three and use vibration.
        var hasExactAlarmAccess = true
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.appViewModel.updateSetup(
                activity.appViewModel.setupState.copy(
                    rules = GameRules(
                        useHalfCap = true,
                        useSoftCap = true,
                        useHardCap = true,
                    ),
                ),
            )
            activity.appViewModel.updateTimingAlertGlobalMode(
                TimingAlertGlobalMode.VIBRATION_ONLY,
            )
            activity.appViewModel.resetTimingCueSettingsToDefaults()
            activity.appViewModel.updateTimingCueMode(
                TimingCueId.HALF_CAP,
                TimingAlertMode.VIBRATE,
            )
            activity.appViewModel.updateTimingCueMode(
                TimingCueId.SOFT_CAP,
                TimingAlertMode.VIBRATE,
            )
            activity.appViewModel.updateTimingCueMode(
                TimingCueId.HARD_CAP,
                TimingAlertMode.VIBRATE,
            )
            hasExactAlarmAccess = activity.hasExactTimingAlertAlarmAccess()
        }

        // Starting setup either shows the exact-alarm warning or goes directly to the live screen,
        // according to the Android permission state captured after the test seeded alert settings.
        composeRule.onNodeWithText("Start game").performClick()
        if (hasExactAlarmAccess) {
            assertLiveScreen()
            return
        }
        waitForText("Cap alert permission")

        // Platform dismissal is a cancel path; it leaves setup waiting so the observer can either
        // change alert settings or try starting the game again.
        pressBackUnconditionally()
        waitForText("Setup game")
        composeRule.onNodeWithText("Start game").assertIsDisplayed()
        composeRule.onNodeWithText("Start game").performClick()
        waitForText("Cap alert permission")

        // The settings link leaves the app for Android's Alarms & reminders settings.  Use a
        // system Back action so Android returns to the same activity.
        composeRule.onNodeWithText("Open settings").performClick()
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        InstrumentationRegistry.getInstrumentation().uiAutomation.performGlobalAction(
            AccessibilityService.GLOBAL_ACTION_BACK,
        )
        waitForText("Setup game")
        composeRule.onNodeWithText("Start game").assertIsDisplayed()

        // If the user still has not enabled exact alarms, Ignore starts the game anyway.
        composeRule.onNodeWithText("Start game").performClick()
        waitForText("Cap alert permission")
        composeRule.onNodeWithText("Ignore").performClick()
        assertLiveScreen()
    }

}
