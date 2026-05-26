package rmjarvis.ultiobserver

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.percentOffset
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.swipeRight
import java.time.LocalTime
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/// Tests for Home, top-level navigation, profile, settings, and archived-game UI pathways.
@RunWith(AndroidJUnit4::class)
class TestHomeAndNavigationUi : MainActivityUiTestFixtures() {
    /**
     * Test the basic launch story from home to setup to live play.
     * Keep this focused on the first-run path and the live-use lock affordance.
     */
    @Test
    fun launchHomeAndStartGame() {
        // Verify the app opens on the home screen with the primary navigation affordances.
        composeRule.onNodeWithText("UltiObserver").assertIsDisplayed()
        composeRule.onNodeWithTag("home-artwork").assertIsDisplayed()
        composeRule.onNodeWithText("Start New Game").assertIsDisplayed()
        composeRule.onNodeWithText("Profile").assertIsDisplayed()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule.onNodeWithText("See Archived Games").assertIsDisplayed()

        // Walk the default new-game path into the live screen.
        openNewGameSetup()
        composeRule.onNodeWithText("UltiObserver Setup").assertIsDisplayed()

        // A brand-new draft with blank setup names should use fallback names on Home.
        pressAppBack()
        waitForText("Current Game")
        composeRule.onNodeWithText("Team 1 0 - 0 Team 2").performClick()
        waitForText("Start Game")
        replaceSetupTeamName("Team 1", "Draft Team")
        replaceSetupTeamName("Team 2", "Draft Opponent")

        // Backing out of setup should keep a resumable setup draft on Home.
        pressAppBack()
        waitForText("Current Game")
        composeRule.onNodeWithText("Tap to resume").assertIsDisplayed()
        composeRule.onNodeWithText("Draft Team 0 - 0 Draft Opponent").performClick()
        waitForText("Start Game")

        // Before the first pull, Back should return to setup for quick field-layout corrections.
        startGameFromSetup()
        assertLiveScreen()
        pressAppBack()
        waitForText("Start Game")

        // Starting a point should immediately switch the phone into locked live-use mode.
        startGameFromSetup()
        composeRule.onNodeWithText("Start Point").performClick()
        waitForText("Slide right to unlock")
    }

    /// Test the home-screen and update-setup buttons that wire into app-level routing state.
    @Test
    fun homeCurrentGameResumeAndUpdateSetupPath() {
        startLiveGameProgrammatically()
        composeRule.onNodeWithText("Start Point").performClick()
        waitForText("Slide right to unlock")

        // After the first pull, Back navigation should expose the current-game resume path.
        pressAppBack()
        waitForText("Current Game")
        composeRule.onNodeWithText("Tap to resume").assertIsDisplayed()
        composeRule.onNodeWithText("Current Game").assertIsDisplayed()
        composeRule.onNodeWithText("Team 1 0 - 0 Team 2").performClick()
        assertLiveScreen()

        // The live Other menu should reopen setup in update mode and return to live.
        openOtherSheet()
        composeRule.onNodeWithText("Update Game Setup").performClick()
        waitForText("Back to Game Screen")
        composeRule.onNodeWithText("Back to Game Screen").performClick()
        assertLiveScreen()
    }

    /// Test the explicit app-bar Back buttons mirror Android back navigation on main screens.
    @Test
    fun topLevelScreensHaveVisibleBackButtons() {
        openNewGameSetup()
        composeRule.onNodeWithText("Back").performClick()
        waitForText("Start New Game")

        startLiveGameProgrammatically()
        composeRule.onNodeWithText("Back").performClick()
        waitForText("Start Game")
        startGameFromSetup()
        composeRule.onNodeWithText("Start Point").performClick()
        waitForText("Slide right to unlock")
        composeRule.onNodeWithText("Back").performClick()
        waitForText("Current Game")
    }

    /// Test About, Profile, and Settings navigation plus their persisted UI state.
    @Test
    fun homeDestinationsSupportAboutProfileAndSettings() {
        // About should behave like a quiet informational destination that returns cleanly to Home.
        composeRule.onNodeWithText("About").performClick()
        composeRule.onNodeWithTag("about-screen").assertIsDisplayed()
        composeRule.onNodeWithText("Version ${BuildConfig.VERSION_NAME}").assertIsDisplayed()
        val sourceCodeUrl = "https://github.com/rmjarvis/UltiObserver"
        val privacyPolicyUrl = "https://github.com/rmjarvis/UltiObserver/blob/main/PRIVACY.md"
        composeRule.onNodeWithText(sourceCodeUrl).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(privacyPolicyUrl).performScrollTo().assertIsDisplayed()

        // The app should hand external links to Android without actually leaving this test.
        fun assertOpensUrl(url: String) {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            var openedIntent: Intent? = null
            val monitor = object : Instrumentation.ActivityMonitor() {
                override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                    if (intent.action == Intent.ACTION_VIEW && intent.dataString == url) {
                        openedIntent = intent
                        return Instrumentation.ActivityResult(Activity.RESULT_OK, null)
                    }
                    return null
                }
            }
            instrumentation.addMonitor(monitor)
            try {
                composeRule.onNodeWithText(url).performScrollTo().performClick()
                composeRule.waitUntil(timeoutMillis = 5_000) { openedIntent != null }
            } finally {
                instrumentation.removeMonitor(monitor)
            }
            assertEquals(Intent.ACTION_VIEW, openedIntent?.action)
            assertEquals(url, openedIntent?.dataString)
        }

        assertOpensUrl(sourceCodeUrl)
        assertOpensUrl(privacyPolicyUrl)
        pressAppBack()
        waitForText("Start New Game")

        // Profile should save both the observer name and selected avatar across navigation.
        composeRule.onNodeWithText("Profile").performClick()
        waitForText("Name")
        waitForText("Home avatar")
        waitForText("Use a random avatar")
        waitForText("Or choose a specific avatar:")
        composeRule.onNodeWithTag("profile-name-field").performTextReplacement("Casey Observer")
        composeRule.onNodeWithText("Casey Observer").assertIsDisplayed()
        composeRule.onNodeWithTag("profile-avatar-BLUE").performScrollTo().performClick()
        waitForText("Start New Game")
        composeRule.onNodeWithText("Profile").performClick()
        composeRule.onNodeWithTag("profile-avatar-BLUE").performScrollTo()
        composeRule.onNode(hasContentDescription("Man with blue ponytail and glasses")).assertIsDisplayed()
        composeRule.onNodeWithTag("profile-avatar-RANDOM").performScrollTo().performClick()
        waitForText("Start New Game")

        // Seed settings directly so this UI-focused test can start at a meaningful cue state.
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.appViewModel.updateTimingAlertGlobalMode(TimingAlertGlobalMode.VIBRATION_ONLY)
            activity.appViewModel.updateTimingAlertSoundVolume(0.5f)
            activity.appViewModel.updateTimingAlertVibrateWithSounds(true)
            activity.appViewModel.updateTimingCueMode(
                TimingCueId.RECEIVING_TWENTY_FOR_HAND,
                TimingAlertMode.NONE,
            )
        }

        // Settings should expose automatic live-play options and hide sound controls when sounds are off.
        composeRule.onNodeWithText("Settings").performClick()
        waitForText("Automatically start live play when a countdown expires?")
        composeRule.onNodeWithTag("settings-auto-advance-countdowns-value").assertTextEquals("Yes")
        composeRule.onNodeWithTag("settings-auto-lock-live-point-value").assertTextEquals("Yes")
        composeRule.onNodeWithTag("settings-auto-advance-countdowns").performClick()
        composeRule.onNodeWithTag("settings-auto-lock-live-point").performClick()
        composeRule.onNodeWithTag("settings-auto-advance-countdowns-value").assertTextEquals("No")
        composeRule.onNodeWithTag("settings-auto-lock-live-point-value").assertTextEquals("No")
        composeRule.onNodeWithTag("settings-auto-advance-countdowns").performClick()
        composeRule.onNodeWithTag("settings-auto-lock-live-point").performClick()
        waitForText("Use sounds and vibration for timing cues?")
        waitForText("Vibration Only")
        composeRule.onNodeWithText("Vibration Only").performClick()
        waitForText("Vibration will be used for any cues that are set to use sound.")
        composeRule.onNodeWithTag("settings-vibration-length").performScrollTo().performTouchInput {
            click(percentOffset(0.95f, 0.5f))
        }
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.activity.appViewModel.timingAlertPreferences.vibrationDurationMillis >
                DEFAULT_TIMING_CUE_VIBRATION_MS
        }
        assertTrue(
            composeRule.activity.appViewModel.timingAlertPreferences.vibrationDurationMillis >
                DEFAULT_TIMING_CUE_VIBRATION_MS
        )
        composeRule.onAllNodesWithTag("settings-sound-volume").assertCountEquals(0)
        composeRule.onAllNodesWithTag("settings-vibrate-with-sounds").assertCountEquals(0)
        composeRule.onNodeWithTag("settings-open-timing-cue-settings").performScrollTo().performClick()
        waitForText("Cue Sound Settings")

        // Cue settings should show disabled-sound context, support default reset, and persist per-cue edits.
        waitForText("Reset all to defaults")
        waitForText("The phone will currently vibrate instead for any cues with sounds.", substring = true)
        composeRule.onNodeWithTag("settings-RECEIVING_TWENTY_FOR_HAND-NONE").assertIsSelected()
        composeRule.onNodeWithTag("settings-reset-timing-cue-defaults").performClick()
        composeRule.onNodeWithTag("settings-RECEIVING_TWENTY_FOR_HAND-TICK").assertIsSelected()
        composeRule.onNodeWithTag("settings-RECEIVING_TWENTY_FOR_HAND-REPEAT_2").assertIsSelected()
        composeRule.onAllNodesWithText("Tick").onFirst().performClick()
        composeRule.onNodeWithTag("settings-TIMEOUT_OFFENSE_TWENTY-NONE").performScrollTo().performClick()
        pressAppBack()
        waitForText("Use sounds and vibration for timing cues?")
        composeRule.onNodeWithTag("settings-global-alert-SOUNDS_ON").performClick()
        waitForText("Ear buds are recommended when using sounds with UltiObserver.")
        waitForText("Sound volume 50%")
        waitForText("Also vibrate on cues that use sound?")

        // Re-enabled sound settings should expose vibration, preview, and repeat-count controls.
        composeRule.onNodeWithTag("settings-vibrate-with-sounds").performScrollTo()
        composeRule.onNodeWithTag("settings-vibrate-with-sounds-value").assertTextEquals("Yes")
        composeRule.onNodeWithTag("settings-sound-volume").assertIsEnabled()
        composeRule.onNodeWithTag("settings-vibrate-with-sounds").assertIsEnabled()
        composeRule.onNodeWithTag("settings-vibrate-with-sounds").performClick()
        composeRule.onNodeWithTag("settings-vibrate-with-sounds-value").assertTextEquals("No")
        composeRule.onNodeWithText("Off").performClick()
        waitForText("No sound or vibration will be used for any timing cues.")
        composeRule.onAllNodesWithTag("settings-sound-volume").assertCountEquals(0)
        composeRule.onAllNodesWithTag("settings-vibrate-with-sounds").assertCountEquals(0)
        composeRule.onNodeWithTag("settings-open-timing-cue-settings").performScrollTo().performClick()
        waitForText("Cue Sound Settings")
        waitForText("Note -- sounds are currently not enabled. If you want sounds", substring = true)
        pressAppBack()
        waitForText("Use sounds and vibration for timing cues?")
        composeRule.onNodeWithTag("settings-global-alert-SOUNDS_ON").performClick()
        waitForText("Sound Settings for Individual Cues")
        composeRule.onNodeWithTag("settings-open-timing-cue-settings").performScrollTo().performClick()
        waitForText("Cue Sound Settings")
        waitForText("Sound previews")
        composeRule.onAllNodesWithText("Note -- sounds are currently not enabled.").assertCountEquals(0)
        waitForText("Knock")
        composeRule.onAllNodesWithText("Tick").onFirst().performClick()
        waitForText("x2")
        waitForText("x3")
        composeRule.onNodeWithTag("settings-HALF_CAP-REPEAT_3").performScrollTo().performClick()
        composeRule.onNodeWithTag("settings-HALF_CAP-REPEAT_3").assertIsSelected()
        composeRule.onNodeWithTag("settings-HALF_CAP-REPEAT_3").performClick()
        composeRule.onNodeWithTag("settings-HALF_CAP-REPEAT_3").assertIsNotSelected()
        waitForText("Before Pull - Offense")
        waitForText("Timeout Between Points")
        waitForText("Caps")
        waitForText("Half cap")
        waitForText("Soft cap")
        waitForText("Hard cap")

        pressAppBack()
        waitForText("Use sounds and vibration for timing cues?")
        pressAppBack()
        waitForText("Start New Game")

        // Archived Games should be reachable from Home even before archived-game flows are populated.
        composeRule.onNodeWithText("See Archived Games").performClick()
        composeRule.onNodeWithTag("archived-games-screen").assertIsDisplayed()
    }
}
