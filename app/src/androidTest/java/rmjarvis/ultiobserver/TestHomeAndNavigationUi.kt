package rmjarvis.ultiobserver

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.percentOffset
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextReplacement
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
     */
    @Test
    fun launchHomeAndStartGame() {
        // Verify the app opens on the home screen with the primary navigation buttons.
        composeRule.onNodeWithText("UltiObserver").assertIsDisplayed()
        composeRule.onNodeWithTag("home-artwork").assertIsDisplayed()
        composeRule.onNodeWithText("Start new game").assertIsDisplayed()
        composeRule.onNodeWithText("Profile").assertIsDisplayed()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule.onNodeWithText("See archived games").assertIsDisplayed()

        // New game opens the setup screen.
        openNewGameSetup()
        composeRule.onNodeWithText("Setup game").assertIsDisplayed()

        // A brand-new draft with blank setup names should use fallback names on Home.
        pressAppBack()
        waitForText("Current game")
        composeRule.onNodeWithText("Team 1 0 - 0 Team 2").performClick()
        waitForText("Start game")
        replaceSetupTeamName("Team 1", "Draft Team")
        replaceSetupTeamName("Team 2", "Draft Opponent")

        // Can also use the visible Back button to go back.
        // This time with the updated team names in the current game section.
        composeRule.onNodeWithText("Back").performClick()
        waitForText("Start new game")
        waitForText("Current game")
        composeRule.onNodeWithText("Tap to resume").assertIsDisplayed()
        composeRule.onNodeWithText("Draft Team 0 - 0 Draft Opponent").performClick()
        waitForText("Start game")

        // Before the first pull, Back should return to setup for quick field-layout corrections.
        startGameFromSetup()
        assertLiveScreen()
        pressAppBack()
        waitForText("Start game")

        // Starting a point should immediately switch the phone into locked live-use mode.
        startGameFromSetup()
        composeRule.onNodeWithText("Start point").performClick()
        waitForText("Slide right to unlock")

        // After the first pull, Back navigation should expose the current-game resume path.
        pressAppBack()
        waitForText("Current game")
        composeRule.onNodeWithText("Tap to resume").assertIsDisplayed()
        composeRule.onNodeWithText("Current game").assertIsDisplayed()
        composeRule.onNodeWithText("Draft Team 0 - 0 Draft Opponent").performClick()
        assertLiveScreen()

        // More actions should reopen setup in update mode and return to live.
        openMoreActionsDialog()
        composeRule.onNodeWithText("Update game setup").performClick()
        waitForText("Back to game screen")
        composeRule.onNodeWithText("Back to game screen").performClick()
        assertLiveScreen()

        // Live screen also has a visible Back button, which returns to Home now.
        composeRule.onNodeWithText("Back").performClick()
        waitForText("Current game")
    }

    /**
     * Test that an active game accidentally archived by Start new game can be restored
     * back into a live game state.
     */
    @Test
    fun archivedGamesRestoreActiveGame() {
        clearArchivedGamesProgrammatically()
        val suffix = System.currentTimeMillis().toString().takeLast(6)
        val teamOneName = "RestoreA$suffix"
        val teamTwoName = "RestoreB$suffix"
        val archivedTitle = "$teamOneName 0 - 0 $teamTwoName"
        val currentTeamOneName = "CurrentA$suffix"
        val currentTeamTwoName = "CurrentB$suffix"
        val currentArchivedTitle = "$currentTeamOneName 0 - 0 $currentTeamTwoName"

        // Archive one active live-point game, then start another current game before restoring
        // the first.
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.appViewModel.deleteCurrentGame()
            activity.appViewModel.startNewGame()
            activity.appViewModel.updateSetup(
                newGameSetupState().copy(
                    teamOne = TeamSetup(name = teamOneName, color = TeamColorChoice.WHITE),
                    teamTwo = TeamSetup(name = teamTwoName, color = TeamColorChoice.BLUE),
                )
            )
            activity.appViewModel.finishSetup()
            activity.appViewModel.updateLiveGame(
                activity.appViewModel.liveState!!.beginLivePoint(0L)
            )
            activity.appViewModel.startNewGame()
            activity.appViewModel.updateSetup(
                newGameSetupState().copy(
                    teamOne = TeamSetup(name = currentTeamOneName, color = TeamColorChoice.WHITE),
                    teamTwo = TeamSetup(name = currentTeamTwoName, color = TeamColorChoice.BLUE),
                )
            )
            activity.appViewModel.finishSetup()
            activity.appViewModel.updateLiveGame(
                activity.appViewModel.liveState!!.beginLivePoint(0L)
            )
            activity.appViewModel.goHome()
        }
        composeRule.waitForIdle()

        // Restoring the archived game replaces the current game and returns to the live screen.
        openArchivedGamesScreen()
        waitForText(archivedTitle)
        composeRule.onNodeWithText(archivedTitle).performClick()
        waitForText("Game summary")
        composeRule.onNodeWithText("Restore game").performClick()

        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "card")).assertIsDisplayed()
        waitForText("More actions")
        composeRule.onNodeWithText(teamOneName).assertIsDisplayed()
        composeRule.onNodeWithText(teamTwoName).assertIsDisplayed()
        assertTrue(
            composeRule.onAllNodesWithText(currentTeamOneName, substring = true)
                .fetchSemanticsNodes()
                .isEmpty()
        )
        assertTrue(
            composeRule.onAllNodesWithText(currentTeamTwoName, substring = true)
                .fetchSemanticsNodes()
                .isEmpty()
        )
        assertTrue(
            composeRule.onAllNodesWithText("Undo Start point").fetchSemanticsNodes().isEmpty()
        )

        // The replaced current game moves into the archive, and the restored game is no longer
        // listed there.
        composeRule.onNodeWithText("Back").performClick()
        waitForText("Current game")
        openArchivedGamesScreen()
        waitForText(currentArchivedTitle)
        composeRule.onNodeWithText(currentTeamOneName, substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(currentTeamTwoName, substring = true).assertIsDisplayed()
        assertTrue(composeRule.onAllNodesWithText(archivedTitle).fetchSemanticsNodes().isEmpty())
    }

    /**
     * Test that the Archived games screen can delete games that are listed there.
     * Both single game deletion and delete all are possible.
     */
    @Test
    fun archivedGamesCanDeleteArchivedGameAfterSliderConfirmation() {
        // Archived games should be reachable from Home before archived-game flows are populated.
        clearArchivedGamesProgrammatically()
        openArchivedGamesScreen()
        composeRule.onNodeWithTag("archived-games-screen").assertIsDisplayed()
        waitForText("No completed games yet.")
        composeRule.onNodeWithText("Back").performClick()

        // Build two uniquely named archived rows so delete assertions cannot match stale test data.
        val suffix = System.currentTimeMillis().toString().takeLast(6)
        val firstTeamOne = "DelA$suffix"
        val firstTeamTwo = "DelB$suffix"
        val secondTeamOne = "DelC$suffix"
        val secondTeamTwo = "DelD$suffix"
        val firstArchivedTitle = "$firstTeamOne 0 - 0 $firstTeamTwo"
        val secondArchivedTitle = "$secondTeamOne 0 - 0 $secondTeamTwo"

        // Cancel once from bulk delete, then confirm it removes every archived row.
        seedArchivedGameProgrammatically(firstTeamOne, firstTeamTwo)
        seedArchivedGameProgrammatically(secondTeamOne, secondTeamTwo)
        openArchivedGamesScreen()
        waitForText(firstArchivedTitle)
        waitForText(secondArchivedTitle)

        // Opening an archived row should expose the read-only summary and its persisted event log.
        composeRule.onNodeWithText(firstArchivedTitle).performClick()
        waitForText("Game summary")
        composeRule.onNodeWithText("Share").assertIsDisplayed()
        composeRule.onNodeWithText("Event log").performClick()
        waitForText("Event log")
        waitForText("No events logged yet.")
        pressDialogBack()
        composeRule.onNodeWithText("Back").performClick()
        waitForText("Archived games")
        waitForText(firstArchivedTitle)
        waitForText(secondArchivedTitle)

        // Delete all gives a warning and requires a slide to confirm.
        // This time cancel to get out of the dialog.
        composeRule.onNodeWithTag("delete-all-archived-games").performClick()
        waitForText("This cannot be undone", substring = true)
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithText(firstArchivedTitle).assertIsDisplayed()
        composeRule.onNodeWithText(secondArchivedTitle).assertIsDisplayed()

        // This time go ahead and delete all the games.
        // We should end up with no games listed.
        composeRule.onNodeWithTag("delete-all-archived-games").performClick()
        waitForText("This cannot be undone", substring = true)
        confirmDeleteWithSlider("Delete all games?")
        waitForText("No completed games yet.")
        assertTrue(
            composeRule.onAllNodesWithText(firstArchivedTitle).fetchSemanticsNodes().isEmpty()
        )
        assertTrue(
            composeRule.onAllNodesWithText(secondArchivedTitle).fetchSemanticsNodes().isEmpty()
        )
        composeRule.onNodeWithText("Back").performClick()

        // Re-seed the archive and verify cancelling a single-game delete leaves both rows intact.
        seedArchivedGameProgrammatically(firstTeamOne, firstTeamTwo)
        seedArchivedGameProgrammatically(secondTeamOne, secondTeamTwo)
        openArchivedGamesScreen()
        waitForText(firstArchivedTitle)
        waitForText(secondArchivedTitle)
        composeRule.onNodeWithTag("delete-archived-game-$firstArchivedTitle").performClick()
        waitForText("This cannot be undone", substring = true)
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithText(firstArchivedTitle).assertIsDisplayed()
        composeRule.onNodeWithText(secondArchivedTitle).assertIsDisplayed()

        // Confirm a single-game delete removes only the selected archived row.
        composeRule.onNodeWithTag("delete-archived-game-$firstArchivedTitle").performClick()
        confirmDeleteWithSlider()
        waitForText(secondArchivedTitle)
        assertTrue(
            composeRule.onAllNodesWithText(firstArchivedTitle).fetchSemanticsNodes().isEmpty()
        )

        // Delete the last archived row and verify Archived games returns to its empty state.
        composeRule.onNodeWithTag("delete-archived-game-$secondArchivedTitle").performClick()
        confirmDeleteWithSlider()
        waitForText("No completed games yet.")
        assertTrue(
            composeRule.onAllNodesWithText(secondArchivedTitle).fetchSemanticsNodes().isEmpty()
        )
    }

    /**
     * Test the About screen and its external links.
     */
    @Test
    fun launchAbout() {
        // About should behave like a quiet informational destination that returns cleanly to Home.
        composeRule.onNodeWithText("About").performClick()
        composeRule.onNodeWithTag("about-screen").assertIsDisplayed()
        composeRule.onNodeWithText("Version ${BuildConfig.VERSION_NAME}").assertIsDisplayed()
        val sourceCodeUrl = "https://github.com/rmjarvis/UltiObserver"
        val privacyPolicyUrl = "https://github.com/rmjarvis/UltiObserver/blob/main/PRIVACY.md"
        composeRule.onNodeWithText(sourceCodeUrl).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(privacyPolicyUrl).performScrollTo().assertIsDisplayed()

        // A helper to check a URL link without actually executing the link.
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

        // Check that the source code URL and the privacy policy are active links.
        assertOpensUrl(sourceCodeUrl)
        assertOpensUrl(privacyPolicyUrl)
        pressAppBack()
        waitForText("Start new game")
    }

    /**
     * Test the Settings screen and persisted timing-cue preferences.
     */
    @Test
    fun launchSettings() {
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

        // Settings should expose automatic live-play options.
        composeRule.onNodeWithText("Settings").performClick()
        val hasTimingCueHaptics = deviceHasTimingCueHaptics()
        waitForText("Automatically start live play when a countdown expires?")
        composeRule.onNodeWithTag("settings-auto-advance-countdowns-value").assertTextEquals("Yes")
        composeRule.onNodeWithTag("settings-auto-lock-live-point-value").assertTextEquals("Yes")
        composeRule.onNodeWithTag("settings-show-defense-countdowns-value")
            .assertTextEquals("No")
        composeRule.onNodeWithTag("settings-auto-advance-countdowns").performClick()
        composeRule.onNodeWithTag("settings-auto-lock-live-point").performClick()
        composeRule.onNodeWithTag("settings-show-defense-countdowns").performClick()
        composeRule.onNodeWithTag("settings-auto-advance-countdowns-value").assertTextEquals("No")
        composeRule.onNodeWithTag("settings-auto-lock-live-point-value").assertTextEquals("No")
        composeRule.onNodeWithTag("settings-show-defense-countdowns-value")
            .assertTextEquals("Yes")
        composeRule.onNodeWithTag("settings-auto-advance-countdowns").performClick()
        composeRule.onNodeWithTag("settings-auto-lock-live-point").performClick()
        composeRule.onNodeWithTag("settings-show-defense-countdowns").performClick()
        waitForText(
            "most observers will count this off themselves with arm chops",
            substring = true,
        )

        // With vibration-only mode selected, sound-specific settings should be hidden.
        waitForText("Use sounds and vibration for timing cues?")
        waitForText("Vibration only")
        composeRule.onNodeWithText("Vibration only").performClick()
        if (hasTimingCueHaptics) {
            waitForText("Vibration will be used for any cues that are set to use sound.")
            composeRule.onNodeWithTag("settings-vibration-length")
                .performScrollTo()
                .performTouchInput {
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
            composeRule.onNodeWithTag("settings-test-vibration")
                .performScrollTo()
                .assertIsEnabled()
                .performClick()
        } else {
            waitForText("This phone reports that vibration is unavailable.", substring = true)
            composeRule.onNodeWithTag("settings-vibration-length")
                .performScrollTo()
                .assertIsNotEnabled()
            composeRule.onNodeWithTag("settings-test-vibration")
                .performScrollTo()
                .assertIsNotEnabled()
        }
        composeRule.onAllNodesWithTag("settings-sound-volume").assertCountEquals(0)
        composeRule.onAllNodesWithTag("settings-vibrate-with-sounds").assertCountEquals(0)
        composeRule.onNodeWithTag("settings-open-timing-cue-settings")
            .performScrollTo()
            .performClick()
        waitForText("Cue sound settings")
        waitForText("Note — these cues are not currently enabled.", substring = true)

        // Cue settings should show disabled-sound context, support default reset, and
        // persist per-cue edits.
        waitForText("Reset all to defaults")
        if (hasTimingCueHaptics) {
            waitForText(
                "The phone will currently vibrate instead for any cues with sounds.",
                substring = true,
            )
        } else {
            waitForText(
                "Note — sounds are currently not enabled. If you want sounds",
                substring = true,
            )
        }
        composeRule.onNodeWithTag("settings-RECEIVING_TWENTY_FOR_HAND-NONE").assertIsSelected()
        composeRule.onNodeWithTag("settings-reset-timing-cue-defaults").performClick()
        composeRule.onNodeWithTag("settings-RECEIVING_TWENTY_FOR_HAND-TICK").assertIsSelected()
        composeRule.onNodeWithTag("settings-RECEIVING_TWENTY_FOR_HAND-REPEAT_2").assertIsSelected()
        composeRule.onNodeWithTag("settings-OFFENSE_TWENTY-NONE").performScrollTo().performClick()
        pressAppBack()
        waitForText("Use sounds and vibration for timing cues?")
        composeRule.onNodeWithTag("settings-global-alert-SOUNDS_ON")
            .performScrollTo()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.activity.appViewModel.timingAlertPreferences.globalMode ==
                TimingAlertGlobalMode.SOUNDS_ON
        }
        composeRule.onNodeWithTag("settings-sound-volume").performScrollTo()
        waitForText("Ear buds are recommended when using sounds with UltiObserver.")
        if (!hasTimingCueHaptics) {
            waitForText("This phone reports that vibration is unavailable.", substring = true)
            composeRule.onNodeWithTag("settings-vibration-length")
                .performScrollTo()
                .assertIsNotEnabled()
            composeRule.onNodeWithTag("settings-test-vibration")
                .performScrollTo()
                .assertIsNotEnabled()
        }
        waitForText("Sound volume 50%")
        waitForText("Also vibrate on cues that use sound?")

        // Re-enabled sound settings should expose vibration, preview, and repeat-count controls.
        composeRule.onNodeWithTag("settings-vibrate-with-sounds").performScrollTo()
        composeRule.onNodeWithTag("settings-vibrate-with-sounds-value").assertTextEquals("Yes")
        composeRule.onNodeWithTag("settings-sound-volume").assertIsEnabled()
        if (hasTimingCueHaptics) {
            composeRule.onNodeWithTag("settings-vibrate-with-sounds").assertIsEnabled()
            composeRule.onNodeWithTag("settings-vibrate-with-sounds").performClick()
            composeRule.onNodeWithTag("settings-vibrate-with-sounds-value").assertTextEquals("No")
        } else {
            composeRule.onNodeWithTag("settings-vibrate-with-sounds").assertIsNotEnabled()
        }
        composeRule.onNodeWithText("Off").performClick()
        waitForText("No sound or vibration will be used for any timing cues.")
        composeRule.onAllNodesWithTag("settings-sound-volume").assertCountEquals(0)
        composeRule.onAllNodesWithTag("settings-vibrate-with-sounds").assertCountEquals(0)
        composeRule.onNodeWithTag("settings-open-timing-cue-settings")
            .performScrollTo()
            .performClick()
        waitForText("Cue sound settings")
        waitForText(
            "Note — sounds are currently not enabled. If you want sounds",
            substring = true,
        )
        pressAppBack()
        waitForText("Use sounds and vibration for timing cues?")
        composeRule.onNodeWithTag("settings-global-alert-SOUNDS_ON").performClick()
        waitForText("Sound settings for individual cues")
        composeRule.onNodeWithTag("settings-open-timing-cue-settings")
            .performScrollTo()
            .performClick()
        waitForText("Cue sound settings")
        waitForText("Sound previews")
        composeRule.onAllNodesWithText("Note — sounds are currently not enabled.")
            .assertCountEquals(0)
        waitForText("Knock")
        composeRule.onNodeWithTag("settings-sound-preview-TICK").performClick()
        waitForText("x2")
        waitForText("x3")
        composeRule.onNodeWithTag("settings-HALF_CAP-REPEAT_3").performScrollTo().performClick()
        composeRule.onNodeWithTag("settings-HALF_CAP-REPEAT_3").assertIsSelected()
        composeRule.onNodeWithTag("settings-HALF_CAP-REPEAT_3").performClick()
        composeRule.onNodeWithTag("settings-HALF_CAP-REPEAT_3").assertIsNotSelected()
        waitForText("Before pull - offense")
        waitForText("Timeout between points")
        waitForText("Caps")
        waitForText("Half cap")
        waitForText("Soft cap")
        waitForText("Hard cap")
        pressAppBack()
        waitForText("Use sounds and vibration for timing cues?")
        pressAppBack()
        waitForText("Start new game")
    }

    /**
     * Test the Profile screen and persisted observer identity.
     */
    @Test
    fun launchProfile() {
        // Profile should save both the observer name and selected avatar across navigation.
        composeRule.onNodeWithText("Profile").performClick()
        waitForText("Name")
        waitForText("Home avatar")
        waitForText("Use a random avatar")
        waitForText("Or choose a specific avatar:")
        composeRule.onNodeWithTag("profile-name-field").performTextReplacement("Casey Observer")
        composeRule.onNodeWithText("Casey Observer").assertIsDisplayed()
        composeRule.onNodeWithTag("profile-avatar-BLUE").performScrollTo().performClick()
        waitForText("Start new game")
        composeRule.onNodeWithText("Profile").performClick()
        composeRule.onNodeWithTag("profile-avatar-BLUE").performScrollTo()
        composeRule.onNode(hasContentDescription("Man with blue ponytail and glasses"))
            .assertIsDisplayed()
        composeRule.onNodeWithTag("profile-avatar-RANDOM").performScrollTo().performClick()
        waitForText("Start new game")
    }

    /// Open Archived games from Home and wait until the page is visible.
    private fun openArchivedGamesScreen() {
        composeRule.onNodeWithText("See archived games").performClick()
        waitForText("Archived games")
    }
}
