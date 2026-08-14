package rmjarvis.ultiobserver

import android.graphics.Bitmap
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.FileInputStream
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Generate the public documentation and Play Store screenshot narrative.
 *
 * This is deliberately separate from ordinary UI verification. It drives the real UI at a
 * changes the emulator wall clock to create the requested event-log times and
 * writes full-display PNG captures for the host-side screenshot driver to collect.
 */
@RunWith(AndroidJUnit4::class)
class GenerateReleaseScreenshots : MainActivityUiTestFixtures() {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val outputDirectory = File(
        instrumentation.targetContext.getExternalFilesDir(null),
        "release-screenshots",
    )

    /** Generate the complete July 2026 Red Fish Blue Fish vs Rippit screenshot set. */
    @Test
    fun generateScreenshots() {
        require(currentAvdName() == "Pixel_8") {
            "Release screenshots must run on Pixel_8, not ${currentAvdName()}."
        }
        outputDirectory.deleteRecursively()
        check(outputDirectory.mkdirs()) { "Could not create $outputDirectory" }

        establishInitialAppState()
        captureHomeProfileSettingsAndDrafts()
        enterGameSetup()
        enterStartingPullAndStartGame()
        playEarlyGame()
        playMisconductSequence()
        finishGame()
        captureArchiveScreens()
    }

    /// Seed deterministic app-owned preferences while retaining the host-restored archive rows.
    private fun establishInitialAppState() {
        check(
            composeRule.onAllNodesWithText("Phone data reset")
                .fetchSemanticsNodes()
                .isEmpty()
        ) { "The screenshot seed triggered a persistence recovery notice." }
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.appViewModel.updateProfile(
                Profile(
                    name = "Mike Jarvis",
                    avatarPreference = ObserverAvatarPreference.SPIKY,
                ),
            )
            activity.appViewModel.updateSettings(Settings())
            activity.appViewModel.deleteCurrentGame()
            activity.appViewModel.goHome()
        }
        composeRule.waitForIdle()
    }

    /// Capture Home, Profile, Settings, cue settings, and the four saved setup drafts.
    private fun captureHomeProfileSettingsAndDrafts() {
        setClock(2026, 7, 5, 8, 30)
        waitForText("Start new game")

        clickText("Profile")
        waitForText("Home avatar")
        capture("Profile.png")
        tapTopBarHome()
        composeRule.waitForIdle()

        clickText("Settings")
        waitForText("Use sounds and vibration for timing cues?")
        capture("SettingsTop.png")
        clickTag("settings-open-timing-cue-settings", scroll = true)
        waitForText("Cue sound settings")
        capture("CueSoundSettings.png")
        tapTopBarHome()
        composeRule.waitForIdle()

        clickText("Archived/saved games")
        waitForText("Saved setup drafts", substring = true)
        clickText("Saved setup drafts", substring = true)
        waitForText("Saved setup drafts")
        capture("SavedSetupDrafts.png")
        tapTopBarHome()
        composeRule.waitForIdle()
    }

    /// Enter and capture the semifinal's team, game-information, and default-rule setup.
    private fun enterGameSetup() {
        setClock(2026, 7, 6, 11, 15)
        clickText("Start new game")
        waitForText("Setup game")

        replaceSetupText("setup-Team 1-name", "Red Fish Blue Fish")
        chooseTeamColor("Team 1", TeamColorChoice.WHITE)
        replaceSetupText("setup-Team 2-name", "Rippit")
        chooseTeamColor("Team 2", TeamColorChoice.GREEN)

        setStartTime(java.time.LocalTime.of(11, 30))
        openGameInformationSetupEditor()
        replaceText("setup-observer-0", "Mike Jarvis")
        clickTag("setup-add-observer", scroll = true)
        replaceText("setup-observer-1", "Chris Watcher")
        replaceText("setup-field-name", "17")
        replaceText("setup-tournament-name", "Potlatch Revived")
        clickTag("setup-game-division-${GameDivision.MIXED.name}", scroll = true)
        clickTag("setup-game-level-Legends", scroll = true)
        replaceText("setup-game-context", "semi-final")
        composeRule.onNodeWithTag("setup-start-date-field").performScrollTo()
        capture("GameInformationTop.png")
        closeSetupEditor()
        composeRule.waitForIdle()

        openGameRulesSetupEditor()
        clickTag("setup-usau-defaults", scroll = true)
        composeRule.onAllNodesWithText("Game to").onLast().performScrollTo()
        capture("GameRules.png")
        closeSetupEditor()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("setup-Team 1-name").performScrollTo()
        capture("SetupTop.png")
    }

    /// Enter the field layout, capture it, and stop on the exact-alarm permission dialog.
    private fun enterStartingPullAndStartGame() {
        setClock(2026, 7, 6, 11, 23)
        openStartingPullSetupEditor()
        replaceText("setup-far-end-name", "Trees")
        replaceText("setup-near-end-name", "Parking lot")
        clickTag("setup-pulling-team-${TeamId.TEAM_ONE.name}", scroll = true)
        clickTag("setup-pulling-from-${FieldEnd.NEAR.name}", scroll = true)
        clickTag(
            "setup-initial-gender-ratio-${GenderRatio.FOUR_WOMEN_THREE_MEN.name}",
            scroll = true,
        )
        clickTag("setup-pull-prompts-${PullPromptTarget.FAR.name}", scroll = true)
        composeRule.onNodeWithText("Pulling team").performScrollTo()
        capture("FieldStartingPullTop.png")
        closeSetupEditor()
        composeRule.waitForIdle()

        clickText("Start game")
        waitForText("Cap alert permission")
        capture("CapAlertPermission.png")
        clickText("Ignore")
        assertLiveScreen()
    }

    /// Play through the opening goals, automatic lock, offsides, timeout, and time violation.
    private fun playEarlyGame() {
        advanceToUnlockedPoint(LocalDateTime.of(2026, 7, 6, 11, 30, 25))

        setClock(2026, 7, 6, 11, 31)
        goal(TeamId.TEAM_ONE)
        advanceToUnlockedPoint(LocalDateTime.of(2026, 7, 6, 11, 34))
        goal(TeamId.TEAM_ONE)
        setClock(2026, 7, 6, 11, 34, 20)
        refreshActivityAfterClockJump()
        capture("OffenseSignalTimer.png")

        advanceToUnlockedPoint(LocalDateTime.of(2026, 7, 6, 11, 38))
        goal(TeamId.TEAM_TWO)
        advanceToUnlockedPoint(LocalDateTime.of(2026, 7, 6, 11, 45))
        goal(TeamId.TEAM_TWO)

        setClock(2026, 7, 6, 11, 46, 30)
        waitForText("Slide right to unlock")
        capture("LockedScreen.png")
        unlockLiveScreen()
        composeRule.waitForIdle()

        clickTag(teamActionTag(TeamId.TEAM_TWO, "pull-violation"))
        waitForText("Offsides")
        capture("Offsides.png")
        clickText("OK")

        setClock(2026, 7, 6, 11, 47)
        clickTag(teamActionTag(TeamId.TEAM_ONE, "timeout"))
        waitForText("Timeout charged to", substring = true)
        clickText("OK")
        waitForText("Continue point")
        composeRule.waitForIdle()
        capture("TimeoutCountdown.png")

        advanceToUnlockedPoint(LocalDateTime.of(2026, 7, 6, 11, 49))
        goal(TeamId.TEAM_ONE)
        refreshActivityAfterClockJump()
        recordBlueCard(TeamId.TEAM_ONE)

        setClock(2026, 7, 6, 11, 50)
        clickTag(teamActionTag(TeamId.TEAM_ONE, "time-violation"))
        waitForText("Time violation")
        capture("TimeViolation.png")
        clickText("OK")
    }

    /// Record the requested fouls and card details through 12:06.
    private fun playMisconductSequence() {
        advanceToUnlockedPoint(LocalDateTime.of(2026, 7, 6, 11, 51))
        goal(TeamId.TEAM_TWO)
        recordTechnicalFoul(TeamId.TEAM_TWO)

        advanceToUnlockedPoint(LocalDateTime.of(2026, 7, 6, 11, 54))
        openCardsDialog(TeamId.TEAM_ONE)
        tapCardDialogAction(TeamId.TEAM_ONE, "Yellow")
        waitForText("Yellow card")
        replaceText("card-player-number", "77")
        replaceText("card-player-name", "Hank Puller")
        capture("YellowCardPlayer.png")
        clickText("Reason")
        waitForText("Yellow card reason")
        clickText("Dangerous play", scroll = true)
        capture("YellowCardReason.png")
        clickText("Set")
        clickText("Record")
        waitForText("Yellow card on", substring = true)
        clickText("OK")

        advanceToUnlockedPoint(LocalDateTime.of(2026, 7, 6, 11, 55))
        goal(TeamId.TEAM_TWO)
        clickTag(teamActionTag(TeamId.TEAM_ONE, "timeout"))
        waitForText("Timeout charged to", substring = true)
        clickText("OK")

        advanceToUnlockedPoint(LocalDateTime.of(2026, 7, 6, 11, 59))
        recordTechnicalFoul(TeamId.TEAM_TWO)
        setClock(2026, 7, 6, 12, 0)
        goal(TeamId.TEAM_TWO)
        advanceToUnlockedPoint(LocalDateTime.of(2026, 7, 6, 12, 6))
        goal(TeamId.TEAM_ONE)
    }

    /// Fill the remaining score, apply the third-card penalty and soft cap, and capture summary UI.
    private fun finishGame() {
        val firstHalfFillerGoals = listOf(
            LocalDateTime.of(2026, 7, 6, 12, 9) to TeamId.TEAM_TWO,
            LocalDateTime.of(2026, 7, 6, 12, 12) to TeamId.TEAM_ONE,
            LocalDateTime.of(2026, 7, 6, 12, 15) to TeamId.TEAM_ONE,
        )
        firstHalfFillerGoals.forEach { (time, team) ->
            advanceToUnlockedPoint(time)
            goal(team)
        }

        waitForText("Half cap")
        clickText("OK")

        advanceToUnlockedPoint(LocalDateTime.of(2026, 7, 6, 12, 18))
        goal(TeamId.TEAM_TWO)
        waitForText("Halftime")
        clickText("OK")

        val secondHalfGoals = listOf(
            LocalDateTime.of(2026, 7, 6, 12, 27) to TeamId.TEAM_ONE,
            LocalDateTime.of(2026, 7, 6, 12, 30) to TeamId.TEAM_ONE,
            LocalDateTime.of(2026, 7, 6, 12, 33) to TeamId.TEAM_TWO,
            LocalDateTime.of(2026, 7, 6, 12, 35) to TeamId.TEAM_ONE,
            LocalDateTime.of(2026, 7, 6, 12, 37) to TeamId.TEAM_ONE,
            LocalDateTime.of(2026, 7, 6, 12, 39) to TeamId.TEAM_TWO,
            LocalDateTime.of(2026, 7, 6, 12, 40) to TeamId.TEAM_ONE,
            LocalDateTime.of(2026, 7, 6, 12, 41) to TeamId.TEAM_TWO,
            LocalDateTime.of(2026, 7, 6, 12, 42) to TeamId.TEAM_ONE,
        )
        secondHalfGoals.forEach { (time, team) ->
            advanceToUnlockedPoint(time)
            goal(team)
        }

        advanceToUnlockedPoint(LocalDateTime.of(2026, 7, 6, 12, 43, 30))
        setClock(2026, 7, 6, 12, 44)
        openCardsDialog(TeamId.TEAM_ONE)
        tapCardDialogAction(TeamId.TEAM_ONE, "Blue")
        waitForText("Blue Card")
        clickText("Offense")
        waitForText("Misconduct penalty")
        capture("ThirdCardPenalty.png")
        clickText("OK")

        advanceToUnlockedPoint(LocalDateTime.of(2026, 7, 6, 12, 48))
        goal(TeamId.TEAM_TWO)
        advanceToUnlockedPoint(LocalDateTime.of(2026, 7, 6, 12, 52))
        goal(TeamId.TEAM_TWO)

        advanceToUnlockedPoint(LocalDateTime.of(2026, 7, 6, 13, 5))
        goal(TeamId.TEAM_ONE)
        waitForText("Soft cap")
        clickText("OK")
        advanceToUnlockedPoint(LocalDateTime.of(2026, 7, 6, 13, 6, 30))
        goal(TeamId.TEAM_ONE)
        waitForText("Game over")
        clickText("OK")
        waitForText("Game summary")
        capture("GameSummary.png")

        clickText("Share")
        composeRule.waitForIdle()
        capture("ShareSummary.png")
        shell("input keyevent KEYCODE_BACK")
        composeRule.waitForIdle()

        clickText("Event log")
        waitForText("Event log")
        capture("EventLog.png")
        clickText("OK")
    }

    /// Capture archive categories, filters, and the final filtered/sorted result.
    private fun captureArchiveScreens() {
        setClock(2026, 7, 6, 13, 30)
        tapTopBarHome()
        composeRule.waitForIdle()
        capture("HomePage.png")
        clickText("Archived/saved games")
        waitForText("Archived games", substring = true)
        capture("ArchiveCategories.png")

        clickText("Archived games", substring = true)
        waitForText("Archived games")
        capture("AllArchiveGames.png")

        clickTag("archive-filter-button")
        clickTag("archive-filter-field-LEVEL")
        clickTag("archive-filter-value-College")
        capture("FilterLevel.png")
        clickText("Back")

        clickTag("archive-filter-field-TOURNAMENT")
        clickTag("archive-filter-value-D-I College Championships")
        clickText("Back")
        clickTag("archive-filter-field-DIVISION")
        clickTag("archive-filter-value-Open")
        clickText("Back")
        clickText("Done")
        waitForText("Tournament: D-I College Championships", substring = true)

        clickTag("archive-sort-button")
        clickTag("archive-sort-${ArchiveSortMode.DATE_OLDEST.name}")
        waitForText("Sorted by date, oldest first", substring = true)
        capture("FilteredSortedArchive.png")
    }

    /// Move to a point time, allow countdown transitions, and unlock if necessary.
    private fun advanceToUnlockedPoint(time: LocalDateTime) {
        setClock(time)
        composeRule.waitUntil(timeoutMillis = 10_000) {
            accessCurrentGameState().phase == GamePhase.LIVE_POINT
        }
        if (composeRule.onAllNodesWithText("Slide right to unlock").fetchSemanticsNodes().isNotEmpty()) {
            unlockLiveScreen()
            composeRule.waitForIdle()
        }
    }

    /// Record a goal through the visible team action and wait for persistence/UI settlement.
    private fun goal(team: TeamId) {
        clickTag(teamActionTag(team, "goal"))
        waitForText("Undo Goal by", substring = true)
    }

    /// Record a blue card without a third-card consequence choice.
    private fun recordBlueCard(team: TeamId) {
        openCardsDialog(team)
        tapCardDialogAction(team, "Blue")
        waitForText("Blue Card")
        clickText("OK")
    }

    /// Record and confirm a technical foul through the visible team action.
    private fun recordTechnicalFoul(team: TeamId) {
        clickTag(teamActionTag(team, "tech"))
        waitForText("technical foul", substring = true)
        clickText("OK")
    }

    /// Replace a setup field and close its keyboard as a user would.
    private fun replaceSetupText(tag: String, value: String) {
        composeRule.onNodeWithTag(tag).performScrollTo().performTextReplacement(value)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(tag).performImeAction()
        composeRule.waitForIdle()
    }

    /// Replace a dialog text field and close its keyboard as a user would.
    private fun replaceText(tag: String, value: String) {
        composeRule.onNodeWithTag(tag).performScrollTo().performTextReplacement(value)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag(tag).performImeAction()
        composeRule.waitForIdle()
    }

    /// Choose one team's preset setup color.
    private fun chooseTeamColor(fieldLabel: String, color: TeamColorChoice) {
        clickTag("setup-$fieldLabel-color-button", scroll = true)
        clickTag("setup-$fieldLabel-color-${color.name}")
    }

    /// Click one exact or substring-matched text node and wait for Compose to settle.
    private fun clickText(text: String, substring: Boolean = false, scroll: Boolean = false) {
        val node = composeRule.onAllNodesWithText(text, substring = substring).onFirst()
        if (scroll) {
            node.performScrollTo()
        }
        node.performClick()
        composeRule.waitForIdle()
    }

    /// Click one tagged node and wait for Compose to settle.
    private fun clickTag(tag: String, scroll: Boolean = false) {
        val node = composeRule.onNodeWithTag(tag)
        if (scroll) {
            node.performScrollTo()
        }
        node.performClick()
        composeRule.waitForIdle()
    }

    /// Set the emulator wall clock through the userdebug image's root shell.
    private fun setClock(
        year: Int,
        month: Int,
        day: Int,
        hour: Int,
        minute: Int,
        second: Int = 0,
    ) {
        setClock(LocalDateTime.of(year, month, day, hour, minute, second))
    }

    /// Set the emulator wall clock through the userdebug image's root shell.
    private fun setClock(time: LocalDateTime) {
        val value = time.format(DateTimeFormatter.ofPattern("MMddHHmmyyyy.ss"))
        val output = shell("su 0 date $value")
        check(!output.contains("cannot set date")) { output }
        composeRule.waitForIdle()
    }

    /// Refresh Compose's wall-clock state after a synthetic jump; real phone time needs no refresh.
    private fun refreshActivityAfterClockJump() {
        composeRule.activityRule.scenario.recreate()
        assertLiveScreen()
        composeRule.waitForIdle()
    }

    /// Run a shell command through UiAutomation and return its complete output.
    private fun shell(command: String): String {
        return instrumentation.uiAutomation.executeShellCommand(command).use { descriptor ->
            FileInputStream(descriptor.fileDescriptor).bufferedReader().use { reader ->
                reader.readText().trim()
            }
        }
    }

    /// Capture the complete native Pixel 8 display for host-side system-bar cropping.
    private fun capture(filename: String) {
        // waitForIdle() settles semantics but can leave the emulator Surface on the preceding
        // frame. Advance Compose time without sleeping or changing the scripted wall clock so
        // scroll positions are presented, then give Android's rendered Surface one brief interval
        // to finish transient press indications before capture.
        composeRule.mainClock.advanceTimeBy(1_000)
        composeRule.waitForIdle()
        Thread.sleep(500)
        val bitmap = instrumentation.uiAutomation.takeScreenshot()
        check(bitmap.width == 1080 && bitmap.height == 2400) {
            "Expected native Pixel 8 screenshot, got ${bitmap.width}x${bitmap.height}."
        }
        val output = File(outputDirectory, filename)
        output.outputStream().use { stream ->
            check(bitmap.compress(Bitmap.CompressFormat.PNG, 100, stream)) {
                "Could not write $output"
            }
        }
        bitmap.recycle()
    }

}
