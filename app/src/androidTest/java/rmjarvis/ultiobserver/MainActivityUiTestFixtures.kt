package rmjarvis.ultiobserver

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.swipeRight
import androidx.test.espresso.Espresso.pressBackUnconditionally
import androidx.test.platform.app.InstrumentationRegistry
import java.io.FileInputStream
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Rule

private val platformBackDismissalUnstableAvds = setOf(
    "Pixel_7_Emulator",
    "Pixel_10",
    "Pixel_Fold",
    "Pixel_10_Pro_XL",
)

/// Shared Compose UI navigation and state-seeding helpers for instrumentation tests.
abstract class MainActivityUiTestFixtures {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    /// Open the new-game setup screen from Home.
    protected fun openNewGameSetup() {
        composeRule.onNodeWithText("Start New Game").performClick()
        waitForText("UltiObserver Setup")
    }

    /// Start a live game through the public Home and setup UI path.
    protected fun startLiveGame() {
        openNewGameSetup()
        startGameFromSetup()
    }

    /**
     * Start a live game by seeding ViewModel state directly.
     *
     * This lets UI test functions start with a well-defined game state rather than having to get to that
     * state via UI actions.
     *
     * @param setup The setup state to use; direct injection keeps slow prerequisites out of UI-focused tests.
     */
    protected fun startLiveGameProgrammatically(setup: GameSetupState = newGameSetupState()) {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.appViewModel.deleteCurrentGame()
        }
        composeRule.waitForIdle()
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.appViewModel.startNewGame()
            activity.appViewModel.updateSetup(setup)
            activity.appViewModel.finishSetup()
        }
        composeRule.waitForIdle()
        assertLiveScreen()
    }

    /// Finish the currently visible setup form and verify the live screen appears.
    protected fun startGameFromSetup() {
        composeRule.onNodeWithText("Start Game").performClick()
        assertLiveScreen()
    }

    /**
     * Replace one setup team-name field.
     *
     * @param fieldLabel The setup field label used in the team's test tag.
     * @param teamName The new team name to enter.
     */
    protected fun replaceSetupTeamName(fieldLabel: String, teamName: String) {
        composeRule.onNodeWithTag("setup-$fieldLabel-name").performScrollTo().performTextReplacement(teamName)
    }

    /**
     * Set an integer rule through the setup game-rules editor.
     *
     * @param buttonText The row text that opens the specific rule dialog.
     * @param dialogTitle The title expected after opening the rule dialog.
     * @param fieldLabel The numeric text-field label to replace.
     * @param value The integer text to enter.
     */
    protected fun setIntegerSetupValue(
        buttonText: String,
        dialogTitle: String,
        fieldLabel: String,
        value: String,
    ) {
        openGameRulesSetupEditor()
        composeRule.onNodeWithText(buttonText).performScrollTo().performClick()
        waitForText(dialogTitle)
        composeRule.onNodeWithText(fieldLabel).performTextReplacement(value)
        composeRule.onNodeWithText("Set").performClick()
        waitForText("Game to")
        closeSetupEditor()
        waitForText("Start Game")
    }

    /**
     * Set setup start time through the Material time input.
     *
     * @param startTime The local time to enter.
     */
    protected fun setStartTime(startTime: LocalTime) {
        val hour = startTime.hour % 12
        val hourText = (if (hour == 0) 12 else hour).toString()
        val minuteText = startTime.minute.toString().padStart(2, '0')
        val period = if (startTime.hour >= 12) "PM" else "AM"

        openStartTimeSetupEditor()
        composeRule.onNodeWithTag("setup-start-time-field").performClick()
        waitForText("Set Start Time")
        composeRule.onNode(
            hasSetTextAction() and hasContentDescription("hour", substring = true, ignoreCase = true),
            useUnmergedTree = true,
        ).performTextReplacement(hourText)
        composeRule.onNode(
            hasSetTextAction() and hasContentDescription("minute", substring = true, ignoreCase = true),
            useUnmergedTree = true,
        ).performTextReplacement(minuteText)
        composeRule.onNodeWithText(period).performClick()
        composeRule.onNodeWithText("Set").performClick()
        waitForText("Date")
        closeSetupEditor()
        waitForText("Start Game")
    }

    /// Set setup start time to midnight so tests can create a cap-due state without waiting.
    protected fun setStartTimeToRecentPast() {
        setStartTime(LocalTime.MIDNIGHT)
    }

    /// Set setup start time to the next minute so tests can exercise near-future timing.
    protected fun setStartTimeToFutureMinute() {
        setStartTime(LocalTime.now().plusMinutes(1))
    }

    /**
     * Set a cap rule's minute offset through setup.
     *
     * @param rowLabel The rules row label that opens the cap dialog.
     * @param dialogTitle The cap dialog title and None-toggle test-tag stem.
     * @param value The minute offset text to enter.
     * @param enableFromNone Whether the helper should first toggle the cap back on from None.
     */
    protected fun setCapRuleValue(
        rowLabel: String,
        dialogTitle: String,
        value: String,
        enableFromNone: Boolean = false,
    ) {
        openGameRulesSetupEditor()
        composeRule.onNodeWithText(rowLabel).performScrollTo().performClick()
        waitForText(dialogTitle)
        if (enableFromNone) {
            composeRule.onNodeWithTag("setup-$dialogTitle-none").performClick()
        }
        composeRule.onNodeWithText("Minutes").performTextReplacement(value)
        composeRule.onNodeWithText("Set").performClick()
        waitForText("Game to")
        closeSetupEditor()
        waitForText("Start Game")
    }

    /**
     * Disable a cap rule through setup.
     *
     * @param rowLabel The rules row label that opens the cap dialog.
     * @param dialogTitle The cap dialog title and None-toggle test-tag stem.
     */
    protected fun setCapRuleToNone(rowLabel: String, dialogTitle: String) {
        openGameRulesSetupEditor()
        composeRule.onNodeWithText(rowLabel).performScrollTo().performClick()
        waitForText(dialogTitle)
        composeRule.onNodeWithTag("setup-$dialogTitle-none").performClick()
        composeRule.onNodeWithText("Set").performClick()
        waitForText("Game to")
        closeSetupEditor()
        waitForText("Start Game")
    }

    /**
     * Set timeout rules through setup.
     *
     * @param timeoutsPerHalf The timeout count text to enter.
     * @param hasFloater Whether to enable the optional floater timeout.
     */
    protected fun setTimeoutRules(timeoutsPerHalf: String, hasFloater: Boolean) {
        openGameRulesSetupEditor()
        composeRule.onNodeWithText("Timeouts").performScrollTo().performClick()
        waitForText("Timeout Rules")
        composeRule.onNodeWithText("Timeouts per half").performTextReplacement(timeoutsPerHalf)
        if (hasFloater) {
            composeRule.onNodeWithTag("setup-timeouts-floater").performClick()
        }
        composeRule.onNodeWithText("Set").performClick()
        waitForText("Game to")
        closeSetupEditor()
        waitForText("Start Game")
    }

    /**
     * Add a prior-card holder through setup.
     *
     * @param team The team whose Cards button was pressed.
     * @param jersey The jersey number to enter.
     * @param playerName The player name to enter.
     * @param yellows The prior yellow count to set.
     * @param reds The prior red count to set.
     */
    protected fun addPriorCardHolder(team: TeamId, jersey: String, playerName: String, yellows: Int, reds: Int) {
        openPriorCardsSetupEditor(team)
        composeRule.onNodeWithText("Add Card Holder").performClick()
        waitForText("Add Previous Game Card Holder")
        enterPriorCardJersey(jersey)
        enterPriorCardName(playerName)
        repeat((yellows - 1).coerceAtLeast(0)) {
            composeRule.onAllNodesWithText("+1")[0].performClick()
        }
        repeat(reds.coerceAtLeast(0)) {
            composeRule.onAllNodesWithText("+1")[1].performClick()
        }
        composeRule.onNodeWithText("Add").performClick()
        composeRule.onNodeWithText(priorCardIdentity(jersey, playerName)).performScrollTo().assertIsDisplayed()
        closeSetupEditor()
        waitForText("Start Game")
    }

    /**
     * Enter a jersey number in the setup prior-card holder dialog.
     *
     * @param jersey The jersey number text to enter.
     */
    protected fun enterPriorCardJersey(jersey: String) {
        composeRule.onNodeWithTag("setup-prior-card-jersey").performTextReplacement(jersey)
        composeRule.onNodeWithTag("setup-prior-card-jersey").performImeAction()
    }

    /**
     * Enter a player name in the setup prior-card holder dialog.
     *
     * @param playerName The player name text to enter.
     */
    protected fun enterPriorCardName(playerName: String) {
        composeRule.onNodeWithTag("setup-prior-card-name").performTextReplacement(playerName)
        composeRule.onNodeWithTag("setup-prior-card-name").performImeAction()
    }

    /// Return the setup display identity for a prior-card holder.
    protected fun priorCardIdentity(jersey: String, playerName: String): String {
        return when {
            jersey.isNotBlank() && playerName.isNotBlank() -> "#$jersey $playerName"
            jersey.isNotBlank() -> "#$jersey"
            else -> playerName
        }
    }

    /**
     * Start a live game whose selected cap is already due.
     *
     * This lets UI tests open cap-prompt flows from a known cap state without waiting for wall-clock time.
     *
     * @param rowLabel The setup row label identifying the cap.
     * @param dialogTitle The setup dialog title identifying the cap.
     */
    protected fun startLiveGameWithDueCap(rowLabel: String, dialogTitle: String) {
        val capType = capTypeForSetupLabels(rowLabel, dialogTitle)
        val start = LocalDateTime.now().minusSeconds(5)
        startLiveGameProgrammatically(
            newGameSetupState().copy(
                startDate = start.toLocalDate(),
                startTime = start.toLocalTime(),
                rules = singleEnabledCapRules(capType, capMinutes = 0),
            )
        )
    }

    /**
     * Start a live game where the selected cap becomes due during halftime.
     *
     * This lets UI tests validate halftime cap prompts without playing through real elapsed cap time.
     *
     * @param rowLabel The setup row label identifying the cap.
     * @param dialogTitle The setup dialog title identifying the cap.
     */
    protected fun startLiveGameWithCapDuringHalftime(rowLabel: String, dialogTitle: String) {
        val capType = capTypeForSetupLabels(rowLabel, dialogTitle)
        val start = LocalDateTime.now().plusMinutes(1)
        startLiveGameProgrammatically(
            newGameSetupState().copy(
                startDate = start.toLocalDate(),
                startTime = start.toLocalTime(),
                rules = singleEnabledCapRules(capType, capMinutes = 2).copy(halftimeMinutes = 7),
            )
        )
    }

    /// Return from the game screen to Home using app back navigation.
    protected fun returnHomeFromGame() {
        pressAppBack()
        waitForText("Start New Game")
    }

    /// Send platform Back to the currently focused app window.
    protected fun pressDialogBack() {
        pressBackUnconditionally()
        composeRule.waitForIdle()
    }

    /// Return whether this AVD should run platform-Back dismissal coverage paths.
    protected fun shouldUsePlatformBackDismissalCoverage(): Boolean {
        return currentAvdName() !in platformBackDismissalUnstableAvds
    }

    /// Return the configured AVD name for the current emulator.
    protected fun currentAvdName(): String {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.executeShellCommand("getprop ro.boot.qemu.avd_name").use { descriptor ->
            return FileInputStream(descriptor.fileDescriptor).bufferedReader().use { reader ->
                reader.readText().trim()
            }
        }
    }

    /// Tap Back from a misconduct offense/defense choice to the card step that opened it.
    protected fun tapBackFromMisconductODChoice() {
        composeRule.onNodeWithTag("misconduct-choice-back").performClick()
    }

    /**
     * Tap a team's goal button and wait for the expected undo label.
     *
     * @param team The team that should score.
     * @param undoLabel The undo text expected after the goal.
     */
    protected fun recordGoal(team: TeamId, undoLabel: String) {
        composeRule.onNodeWithTag(teamActionTag(team, "goal")).performClick()
        waitForText(undoLabel)
    }

    /// Start the point, wait for lock mode, and unlock the live screen.
    protected fun startPointAndUnlock() {
        composeRule.onNodeWithText("Start Point").performClick()
        waitForText("Slide right to unlock")
        unlockLiveScreen()
    }

    /// Verify interrupted and too-short unlock swipes do not unlock, then complete the unlock flow.
    protected fun startPointWithFailedSwipeThenUnlock() {
        composeRule.onNodeWithText("Start Point").performClick()
        waitForText("Slide right to unlock")
        composeRule.onNodeWithTag("live-unlock-slider").performTouchInput {
            down(center)
            moveBy(androidx.compose.ui.geometry.Offset(40f, 0f))
            cancel()
        }
        waitForText("Slide right to unlock")
        composeRule.onNodeWithTag("live-unlock-slider").performTouchInput {
            swipeRight(startX = left, endX = left + 40f)
        }
        waitForText("Slide right to unlock")
        composeRule.onNodeWithTag("live-unlock-slider").performTouchInput {
            swipeRight(startX = centerX, endX = right)
        }
        waitForText("Slide right to unlock")
        unlockLiveScreen()
    }

    /// Continue play after an in-point countdown and unlock the live screen.
    protected fun continuePointAndUnlock() {
        composeRule.onNodeWithText("Continue Point").performClick()
        waitForText("Slide right to unlock")
        unlockLiveScreen()
    }

    /**
     * Clear archived games by calling the ViewModel directly.
     *
     * This lets UI tests start from a known archive state without deleting existing rows through the UI.
     */
    protected fun clearArchivedGamesProgrammatically() {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.appViewModel.deleteAllArchivedGames()
        }
        composeRule.waitForIdle()
    }

    /**
     * Seed one archived completed game by calling the ViewModel directly.
     *
     * This lets UI tests validate Archived Games behavior without first playing and archiving a game
     * through UI actions.
     *
     * @param teamOneName The archived game Team 1 name.
     * @param teamTwoName The archived game Team 2 name.
     */
    protected fun seedArchivedGameProgrammatically(teamOneName: String, teamTwoName: String) {
        composeRule.activityRule.scenario.onActivity { activity ->
            val setup = newGameSetupState().copy(
                teamOne = TeamSetup(name = teamOneName, color = TeamColorChoice.WHITE),
                teamTwo = TeamSetup(name = teamTwoName, color = TeamColorChoice.BLUE),
            )
            val completed = createLiveGameState(setup).copy(
                phase = GamePhase.GAME_OVER,
                endEpoch = System.currentTimeMillis(),
                countdown = null,
            )
            activity.appViewModel.updateLiveGame(completed)
            activity.appViewModel.archiveCompletedGame()
        }
        composeRule.waitForIdle()
    }

    /**
     * Seed in-game player card records by calling the ViewModel directly.
     *
     * This lets UI tests open card adjustment paths from a precise card history without recording every
     * prerequisite card through the UI.
     *
     * @param teamOneCards The Team 1 player-card records to install.
     * @param teamTwoCards The Team 2 player-card records to install.
     */
    protected fun seedInGamePlayerCardsProgrammatically(
        teamOneCards: List<InGamePlayerCardRecord> = emptyList(),
        teamTwoCards: List<InGamePlayerCardRecord> = emptyList(),
    ) {
        composeRule.activityRule.scenario.onActivity { activity ->
            val current = activity.appViewModel.liveState!!
            activity.appViewModel.updateLiveGame(
                current.copy(
                    teamOnePlayerCards = teamOneCards,
                    teamTwoPlayerCards = teamTwoCards,
                )
            )
        }
        composeRule.waitForIdle()
    }

    /**
     * End the current game through the model and wait for the game-over prompt.
     *
     * This lets UI tests reach summary/delete/archive flows without scoring through a full game in the UI.
     */
    protected fun endCurrentGameProgrammatically() {
        composeRule.activityRule.scenario.onActivity { activity ->
            val current = activity.appViewModel.liveState!!
            activity.appViewModel.updateLiveGame(current.endGameNow(System.currentTimeMillis()))
        }
        waitForText("Game Over")
    }

    /**
     * Move the active countdown target relative to now by direct ViewModel state update.
     *
     * This lets UI tests exercise expired-countdown behavior deterministically without waiting for real time to pass.
     *
     * @param secondsRemaining The desired countdown seconds remaining; negative values force expiry.
     */
    protected fun setActiveCountdownRemainingProgrammatically(secondsRemaining: Int) {
        composeRule.activityRule.scenario.onActivity { activity ->
            val current = activity.appViewModel.liveState!!
            val countdown = current.countdown!!
            activity.appViewModel.updateLiveGame(
                current.copy(
                    countdown = countdown.copy(
                        targetEpoch = System.currentTimeMillis() + secondsRemaining * 1000L,
                    )
                )
            )
        }
        composeRule.waitForIdle()
    }

    /// Complete the live-screen unlock gesture and wait for unlocked controls.
    protected fun unlockLiveScreen() {
        composeRule.onNodeWithTag("live-unlock-slider").performTouchInput {
            swipeRight()
        }
        waitForText("Lock")
    }

    /**
     * Confirm a destructive delete dialog with its slide control.
     *
     * @param dialogTitle The dialog title to wait for before sliding.
     */
    protected fun confirmDeleteWithSlider(dialogTitle: String = "Delete Game?") {
        waitForText(dialogTitle)
        composeRule.onNodeWithTag("confirm-delete-slider").performTouchInput {
            swipeRight()
        }
    }

    /**
     * Charge a timeout to a team and dismiss the confirmation popup.
     *
     * @param team The team requesting the timeout.
     * @param undoLabel The undo text expected after the timeout is recorded.
     */
    protected fun recordTimeout(team: TeamId, undoLabel: String) {
        composeRule.onNodeWithTag(teamActionTag(team, "timeout")).performClick()
        waitForText("Timeout charged to", substring = true)
        composeRule.onNodeWithText("OK").performClick()
        waitForText(undoLabel)
    }

    /// Exercise the score adjustment dialog with a small nonzero correction.
    protected fun applyScoreAdjustment() {
        openOtherSheet()
        composeRule.onNodeWithText("Adjust Score").performClick()
        waitForText("Adjust Score")
        composeRule.onAllNodesWithText("+1")[0].performClick()
        composeRule.onAllNodesWithText("+1")[1].performClick()
        composeRule.onAllNodesWithText("-1")[0].performClick()
        composeRule.onNodeWithText("Set").performClick()
        waitForText("Undo Score Adjustment")
    }

    /// Exercise the timeout adjustment dialog with a small nonzero correction.
    protected fun applyTimeoutAdjustment() {
        openOtherSheet()
        composeRule.onNodeWithText("Adjust Timeouts").performClick()
        waitForText("Adjust Timeouts")
        composeRule.onAllNodesWithText("+1")[0].performClick()
        composeRule.onAllNodesWithText("+1")[1].performClick()
        composeRule.onAllNodesWithText("-1")[0].performClick()
        composeRule.onNodeWithText("Set").performClick()
        waitForText("Undo Timeout Adjustment")
    }

    /// Exercise the pull-infraction adjustment dialog with a small nonzero correction.
    protected fun applyPullInfractionAdjustment() {
        openOtherSheet()
        composeRule.onNodeWithText("Adjust Pull Infractions").performClick()
        waitForText("Adjust Pull Infractions")
        composeRule.onAllNodesWithText("+1")[0].performClick()
        composeRule.onAllNodesWithText("+1")[1].performClick()
        composeRule.onAllNodesWithText("+1")[2].performClick()
        composeRule.onAllNodesWithText("+1")[3].performClick()
        composeRule.onAllNodesWithText("-1")[1].performClick()
        composeRule.onNodeWithTag("adjust-pull-infractions-confirm").performTouchInput {
            click()
        }
        waitForText("Undo Pull Infraction Adjustment")
    }

    /// Exercise the Cards / TF adjustment dialog without changing counts.
    protected fun applyNoOpCardAdjustment() {
        openOtherSheet()
        composeRule.onNodeWithText("Adjust Cards / TF").performClick()
        waitForText("Adjust Cards / TF")
        composeRule.onNodeWithText("Set").performClick()
        waitForText("Undo Cards / TF Adjustment")
    }

    /**
     * Record a yellow card through the Cards / TF sheet.
     *
     * @param team The team receiving the yellow.
     * @param playerNumber The player number to enter; blank chooses `N/A`.
     * @param expectedMessage The popup text expected after recording.
     * @param misconductChoice Optional misconduct choice to tap when the card reaches a live-point threshold.
     * @param expectedMisconductMessage Optional threshold-resolution text expected after choosing offense/defense.
     * @param verifyMisconductBackReturnsToNumberDialog Whether to exercise Back from misconduct choice and verify number-dialog restoration.
     * @param substring Whether expected-message matching should allow substring matches.
     */
    protected fun recordYellowCard(
        team: TeamId,
        playerNumber: String,
        expectedMessage: String,
        misconductChoice: String? = null,
        expectedMisconductMessage: String? = null,
        verifyMisconductBackReturnsToNumberDialog: Boolean = false,
        substring: Boolean = false,
    ) {
        openCardsSheet()
        tapCardSheetAction(team, "Yellow")
        waitForText("Yellow Card")
        if (playerNumber.isBlank()) {
            composeRule.onNodeWithText("N/A").performClick()
        } else {
            enterCardPlayerNumber(playerNumber)
            composeRule.onNodeWithText("Record").performClick()
        }

        if (misconductChoice == null) {
            waitForText(expectedMessage, substring = substring)
        } else {
            waitForText("Misconduct Penalty")
            if (verifyMisconductBackReturnsToNumberDialog) {
                tapBackFromMisconductODChoice()
                waitForText("Yellow Card")
                if (playerNumber.isNotBlank()) {
                    val restoredNumber = composeRule.onNodeWithTag("card-player-number")
                        .fetchSemanticsNode()
                        .config[SemanticsProperties.EditableText]
                        .text
                    assertEquals(playerNumber, restoredNumber)
                }
                composeRule.onNodeWithText("Record").performClick()
                waitForText("Misconduct Penalty")
            }
            composeRule.onNodeWithText(misconductChoice).performClick()
            waitForText(expectedMisconductMessage ?: expectedMessage, substring = true)
        }
        composeRule.onNodeWithText("OK").performClick()
    }

    /**
     * Record a red card through the Cards / TF sheet.
     *
     * @param team The team receiving the red.
     * @param playerNumber The player number to enter.
     * @param expectedMessage The popup text expected after recording.
     * @param misconductChoice Optional misconduct choice to tap when the card reaches a live-point threshold.
     * @param expectedMisconductMessage Optional threshold-resolution text expected after choosing offense/defense.
     * @param verifyMisconductBackReturnsToNumberDialog Whether to exercise Back from misconduct choice and verify number-dialog restoration.
     */
    protected fun recordRedCard(
        team: TeamId,
        playerNumber: String,
        expectedMessage: String,
        misconductChoice: String? = null,
        expectedMisconductMessage: String? = null,
        verifyMisconductBackReturnsToNumberDialog: Boolean = false,
    ) {
        openCardsSheet()
        tapCardSheetAction(team, "Red")
        waitForText("Red Card")
        enterCardPlayerNumber(playerNumber)
        composeRule.onNodeWithText("Record").performClick()

        if (misconductChoice == null) {
            waitForText(expectedMessage, substring = true)
        } else {
            waitForText("Misconduct Penalty")
            if (verifyMisconductBackReturnsToNumberDialog) {
                tapBackFromMisconductODChoice()
                waitForText("Red Card")
                val restoredNumber = composeRule.onNodeWithTag("card-player-number")
                    .fetchSemanticsNode()
                    .config[SemanticsProperties.EditableText]
                    .text
                assertEquals(playerNumber, restoredNumber)
                composeRule.onNodeWithText("Record").performClick()
                waitForText("Misconduct Penalty")
            }
            composeRule.onNodeWithText(misconductChoice).performClick()
            waitForText(expectedMisconductMessage ?: expectedMessage, substring = true)
        }
        composeRule.onNodeWithText("OK").performClick()
    }

    /**
     * Enter a player number into the active card dialog.
     *
     * @param playerNumber The player number text to enter.
     */
    protected fun enterCardPlayerNumber(playerNumber: String) {
        composeRule.onNodeWithTag("card-player-number").performTextReplacement(playerNumber)
        composeRule.onNodeWithTag("card-player-number").performImeAction()
    }

    /**
     * Record a blue card through the Cards / TF sheet.
     *
     * @param team The team receiving the blue card.
     * @param expectedMessage The popup text expected after recording.
     */
    protected fun recordBlueCard(team: TeamId, expectedMessage: String) {
        openCardsSheet()
        tapCardSheetAction(team, "Blue")
        waitForText(expectedMessage)
        composeRule.onNodeWithText("OK").performClick()
    }

    /**
     * Record a technical foul through the Cards / TF sheet.
     *
     * @param team The team receiving the technical foul.
     * @param expectedMessage The popup text expected after recording.
     * @param substring Whether expected-message matching should allow substring matches.
     */
    protected fun recordTechnicalFoul(
        team: TeamId,
        expectedMessage: String,
        substring: Boolean = false,
    ) {
        openCardsSheet()
        tapCardSheetAction(team, "Tech")
        waitForText(expectedMessage, substring = substring)
        composeRule.onNodeWithText("OK").performClick()
    }

    /**
     * Tap one team action in the Cards / TF sheet, scrolling it into view when needed.
     *
     * @param team The team whose action should be tapped.
     * @param label The action label to tap.
     */
    protected fun tapCardSheetAction(team: TeamId, label: String) {
        composeRule.onAllNodesWithText(label)[teamCardButtonIndex(team)].performScrollTo().performClick()
    }

    /**
     * Return the zero-based card-button index for a team in the Cards / TF sheet.
     *
     * @param team The team whose row button index is needed.
     */
    protected fun teamCardButtonIndex(team: TeamId): Int {
        return if (team == TeamId.TEAM_ONE) 0 else 1
    }

    /// Assert that the live screen's main controls are visible.
    protected fun assertLiveScreen() {
        waitForText("Cards / TF")
        composeRule.onNodeWithText("Cards / TF").assertIsDisplayed()
        composeRule.onNodeWithText("Other").assertIsDisplayed()
    }

    /**
     * Open a setup dialog from Game Rules, verify it appears, then cancel it.
     *
     * @param buttonText The row text that opens the dialog.
     * @param dialogTitle The title expected after opening the dialog.
     */
    protected fun openSetupDialog(buttonText: String, dialogTitle: String) {
        openGameRulesSetupEditor()
        composeRule.onNodeWithText(buttonText).performScrollTo().performClick()
        waitForText(dialogTitle)
        composeRule.onNodeWithText("Cancel").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        waitForText("Game to")
        closeSetupEditor()
    }

    /// Open the setup start-time editor.
    protected fun openStartTimeSetupEditor() {
        composeRule.onNodeWithTag("setup-edit-start-time").performScrollTo().performClick()
        waitForText("Date")
    }

    /// Open the setup starting-pull editor.
    protected fun openStartingPullSetupEditor() {
        composeRule.onNodeWithTag("setup-edit-starting-pull").performScrollTo().performClick()
        waitForText("Pulling team")
    }

    /// Open the setup game-rules editor.
    protected fun openGameRulesSetupEditor() {
        composeRule.onNodeWithTag("setup-edit-game-rules").performScrollTo().performClick()
        waitForText("Game to")
    }

    /**
     * Open the setup prior-cards editor from one team's setup button.
     *
     * @param team The team whose Cards button should open the editor.
     */
    protected fun openPriorCardsSetupEditor(team: TeamId = TeamId.TEAM_TWO) {
        val fieldLabel = if (team == TeamId.TEAM_ONE) "Team 1" else "Team 2"
        composeRule.onNodeWithTag("setup-$fieldLabel-cards-button").performScrollTo().performClick()
        waitForText("Add Card Holder")
    }

    /// Close the current setup overview editor dialog.
    protected fun closeSetupEditor() {
        composeRule.onNodeWithText("Done").performClick()
    }

    /// Open the live Cards / TF sheet.
    protected fun openCardsSheet() {
        composeRule.onNodeWithText("Cards / TF").performClick()
        waitForText("Cards / Technical Fouls")
    }

    /// Open the live Other sheet.
    protected fun openOtherSheet() {
        composeRule.onAllNodesWithText("Other").onFirst().performClick()
        waitForText("Update Game Setup")
    }

    /**
     * Open a dialog from the Other sheet and cancel it.
     *
     * @param label The Other-sheet action label that opens the dialog.
     */
    protected fun openOtherDialogAndCancel(label: String) {
        composeRule.onNodeWithText(label).performClick()
        composeRule.onNodeWithText("Cancel").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        waitForText("Update Game Setup")
    }

    /// Trigger app-level Android back handling from the activity.
    protected fun pressAppBack() {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
    }

    /**
     * Wait until text appears in the Compose semantics tree.
     *
     * @param text The text to wait for.
     * @param substring Whether substring matching should be used.
     */
    protected fun waitForText(text: String, substring: Boolean = false) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /**
     * Build the test tag for one team action on the live field.
     *
     * @param team The team whose action button is targeted.
     * @param action The action suffix, such as `goal`, `timeout`, or `pull-infraction`.
     */
    protected fun teamActionTag(team: TeamId, action: String): String {
        return "live-${team.name}-$action"
    }

    /**
     * Map setup labels to the cap type they identify.
     *
     * @param rowLabel The setup row label.
     * @param dialogTitle The setup dialog title.
     */
    private fun capTypeForSetupLabels(rowLabel: String, dialogTitle: String): CapType {
        return when (rowLabel to dialogTitle) {
            "Half cap" to "Half Cap" -> CapType.HALF
            "Soft cap" to "Soft Cap" -> CapType.SOFT
            "Hard cap" to "Hard Cap" -> CapType.HARD
            else -> error("Unexpected cap setup labels: $rowLabel / $dialogTitle")
        }
    }

    /**
     * Build game rules with exactly one cap enabled.
     *
     * @param capType The cap type to enable.
     * @param capMinutes The enabled cap's offset in minutes.
     */
    private fun singleEnabledCapRules(capType: CapType, capMinutes: Int): GameRules {
        return GameRules(
            gameTo = 5,
            useHalfCap = capType == CapType.HALF,
            halfCapMinutes = capMinutes,
            useSoftCap = capType == CapType.SOFT,
            softCapMinutes = capMinutes,
            useHardCap = capType == CapType.HARD,
            hardCapMinutes = capMinutes,
        )
    }
}
