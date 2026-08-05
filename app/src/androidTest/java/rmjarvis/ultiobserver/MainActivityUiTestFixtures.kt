package rmjarvis.ultiobserver

import android.content.res.Configuration
import androidx.compose.ui.test.ComposeTimeoutException
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
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
import org.junit.After
import org.junit.Before
import org.junit.Rule

private const val LANDSCAPE_COVERAGE_AVD = "Pixel_5"

private val explicitControlDismissalCoverageAvds = setOf(
    // These devices cover explicit dialog controls such as OK and Cancel, while the rest of the
    // matrix covers platform Back dismissal through pressBackUnconditionally().
    "Small_Phone",
    "Nexus_4",
    "Pixel_7",
    "Pixel_Fold",
    "Pixel_10",
)

private const val ROOT_VIEW_WITHOUT_FOCUS_EXCEPTION_NAME =
    "androidx.test.espresso.base.RootViewPicker\$RootViewWithoutFocusException"

/// Shared Compose UI navigation and state-seeding helpers for instrumentation tests.
abstract class MainActivityUiTestFixtures {
    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    /// Restore the production guidance timeout so shortened tests cannot affect later narratives.
    @After
    fun resetRuleGuidanceTimeout() {
        ruleGuidanceTimeoutMillis = 5_000L
    }

    /// Use Landscape on Pixel 5 and Portrait elsewhere for each UI-test narrative.
    @Before
    fun setOrientationPreference() {
        updateOrientationPreference(testOrientationPreference())
    }

    /// Return the active-game orientation preference assigned to the current matrix device.
    internal fun testOrientationPreference(): OrientationPreference {
        return if (currentAvdName() == LANDSCAPE_COVERAGE_AVD) {
            OrientationPreference.LANDSCAPE
        } else {
            OrientationPreference.PORTRAIT
        }
    }

    /// Remove any current game and wait for the UI to observe the empty current-game state.
    protected fun clearCurrentGameProgrammatically() {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.appViewModel.deleteCurrentGame()
            activity.appViewModel.goHome()
        }
        composeRule.waitForIdle()
        waitForConfigurationOrientation(Configuration.ORIENTATION_PORTRAIT)
    }

    /// Open the new-game setup screen from Home.
    protected fun openNewGameSetup() {
        clearCurrentGameProgrammatically()
        composeRule.onNodeWithText("Start new game").performClick()
        waitForText("Setup game")
    }

    /// Start a live game through the public Home and setup UI path.
    protected fun startLiveGame() {
        openNewGameSetup()
        startGameFromSetup()
    }

    /**
     * Start a live game by seeding ViewModel state directly.
     *
     * This lets UI test functions start with a well-defined game state rather than having to get
     * to that state via UI actions.
     *
     * @param setup The setup-stage game to use; direct injection keeps slow prerequisites out of
     * UI-focused tests.
     */
    protected fun startLiveGameProgrammatically(
        setup: GameState = newSetupGameState(now = System.currentTimeMillis())
    ) {
        var activeGameAlreadyVisible = false
        composeRule.activityRule.scenario.onActivity { activity ->
            activeGameAlreadyVisible = activity.appViewModel.state.value.viewingActiveGameScreen
        }
        if (!activeGameAlreadyVisible) {
            clearCurrentGameProgrammatically()
        }
        // When reseeding an active narrative, keep the active-game screen visible. Going through
        // Home would briefly request Portrait before the replacement game requests Landscape.
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.appViewModel.updateSetup(setup)
            activity.appViewModel.finishSetup(now = 123_000L)
        }
        composeRule.waitForIdle()
        assertLiveScreen()
    }

    /**
     * Start a seeded game and mark the opening point live.
     *
     * This keeps UI tests out of the opening-pull countdown when the test story is about actions
     * during the point rather than starting the point.
     *
     * @param setup The setup-stage game to use.
     */
    protected fun startLivePointProgrammatically(
        setup: GameState = newSetupGameState(now = System.currentTimeMillis())
    ) {
        startLiveGameProgrammatically(setup)
        composeRule.activityRule.scenario.onActivity { activity ->
            val current = activity.appViewModel.currentGame!!
            activity.appViewModel.updateCurrentGame(current.beginLivePoint(System.currentTimeMillis()))
        }
        composeRule.waitForIdle()
        assertLiveScreen()
    }

    /**
     * Start a seeded game and record one point so the next pull sequence is waiting.
     *
     * This gives UI tests a real between-points state without playing through the opening point.
     *
     * @param setup The setup-stage game to use.
     * @param scoringTeam The team to record as scoring the first point.
     */
    protected fun startBetweenPointsProgrammatically(
        setup: GameState = newSetupGameState(now = System.currentTimeMillis()),
        scoringTeam: TeamId = TeamId.TEAM_ONE,
    ) {
        startLiveGameProgrammatically(setup)
        composeRule.activityRule.scenario.onActivity { activity ->
            val current = activity.appViewModel.currentGame!!
            activity.appViewModel.updateCurrentGame(
                current.recordGoalFromCurrentState(scoringTeam, System.currentTimeMillis())
            )
        }
        composeRule.waitForIdle()
        assertLiveScreen()
    }

    /// Finish the currently visible setup form and verify the live screen appears.
    protected fun startGameFromSetup() {
        composeRule.onNodeWithText("Start game").performClick()
        if (composeRule.onAllNodesWithText("Cap alert permission").fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithText("Ignore").performClick()
        }
        assertLiveScreen()
    }

    /**
     * Replace one setup team-name field.
     *
     * @param fieldLabel The setup field label used in the team's test tag.
     * @param teamName The new team name to enter.
     */
    protected fun replaceSetupTeamName(fieldLabel: String, teamName: String) {
        composeRule.onNodeWithTag("setup-$fieldLabel-name")
            .performScrollTo()
            .performTextReplacement(teamName)
        composeRule.onNodeWithTag("setup-$fieldLabel-name").performImeAction()
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
        composeRule.onNodeWithTag("setup-integer-set").performClick()
        waitForText("Game to")
        closeSetupEditor()
        waitForText("Start game")
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
        waitForText("Cancel")
        composeRule.onNode(
            hasSetTextAction() and hasContentDescription("hour", substring = true, ignoreCase = true),
            useUnmergedTree = true,
        ).performTextReplacement(hourText)
        composeRule.onNode(
            hasSetTextAction() and hasContentDescription("minute", substring = true, ignoreCase = true),
            useUnmergedTree = true,
        ).performTextReplacement(minuteText)
        composeRule.onNodeWithText(period).performClick()
        composeRule.onNodeWithTag("setup-start-time-set").performClick()
        waitForText("Date")
        closeSetupEditor()
        waitForText("Start game")
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
        composeRule.onNodeWithTag("setup-$dialogTitle-set").performClick()
        waitForText("Game to")
        closeSetupEditor()
        waitForText("Start game")
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
        composeRule.onNodeWithTag("setup-$dialogTitle-set").performClick()
        waitForText("Game to")
        closeSetupEditor()
        waitForText("Start game")
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
        waitForText("Timeout rules")
        composeRule.onNodeWithText("Timeouts per half").performTextReplacement(timeoutsPerHalf)
        if (hasFloater) {
            composeRule.onNodeWithTag("setup-timeouts-floater").performClick()
        }
        composeRule.onNodeWithTag("setup-timeouts-set").performClick()
        waitForText("Game to")
        closeSetupEditor()
        waitForText("Start game")
    }

    /**
     * Add a setup prior-card holder by seeding ViewModel state directly.
     *
     * This lets UI tests start from existing prior-card rows without replaying the add-holder
     * dialog when that dialog is not the behavior under test.
     *
     * @param team The team receiving the prior-card holder.
     * @param jersey The player's jersey number, or blank for a name-only identity.
     * @param playerName The player's name, or blank when unknown.
     * @param yellows The prior yellow count to set.
     * @param reds The prior red count to set.
     */
    protected fun addPriorCardHolderProgrammatically(
        team: TeamId,
        jersey: String,
        playerName: String,
        yellows: Int,
        reds: Int,
    ) {
        val record = PlayerRecord(
            jerseyNumber = jersey,
            playerName = playerName,
            priorYellows = yellows,
            priorReds = reds,
        )
        updateCurrentStateProgrammatically {
            when (team) {
                TeamId.TEAM_ONE -> copy(teamOnePlayers = teamOnePlayers + record)
                TeamId.TEAM_TWO -> copy(teamTwoPlayers = teamTwoPlayers + record)
            }
        }
    }

    /**
     * Build a player record from card-count summaries for UI tests.
     *
     * @param jerseyNumber The player's jersey number, or blank for a name-only identity.
     * @param yellows The number of yellow-card events to create.
     * @param reds The number of red-card events to create.
     * @param playerName The player's name, or blank when unknown.
     */
    protected fun playerRecordWithCards(
        jerseyNumber: String,
        yellows: Int = 0,
        reds: Int = 0,
        playerName: String = "",
    ): PlayerRecord {
        require(yellows >= 0 && reds >= 0) {
            "Player records cannot have negative card counts."
        }
        return PlayerRecord(
            jerseyNumber = jerseyNumber,
            playerName = playerName,
            cards = buildList {
                repeat(yellows) { add(InGamePlayerCardEvent(CardType.YELLOW, index = size)) }
                repeat(reds) { add(InGamePlayerCardEvent(CardType.RED, index = size)) }
            },
        )
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
        startLivePointProgrammatically(
            newSetupGameState(now = 123_000L).copy(
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
        startBetweenPointsProgrammatically(
            newSetupGameState(now = 123_000L).copy(
                startDate = start.toLocalDate(),
                startTime = start.toLocalTime(),
                rules = singleEnabledCapRules(capType, capMinutes = 2).copy(halftimeMinutes = 7),
            )
        )
    }

    /// Return from the game screen to Home using app back navigation.
    protected fun returnHomeFromGame() {
        pressAppBack()
        waitForText("Start new game")
    }

    /// Tap the current screen's top-bar Back navigation icon.
    protected fun tapTopBarBack() {
        composeRule.onNodeWithTag("top-bar-back").performClick()
    }

    /// Tap the current screen's top-bar Home navigation icon.
    protected fun tapTopBarHome() {
        composeRule.onNodeWithTag("top-bar-home").performClick()
    }

    /// Send platform Back to the currently focused app window.
    protected fun pressDialogBack() {
        pressBackUnconditionally()
        composeRule.waitForIdle()
    }

    /// Return whether this AVD should run platform-Back dismissal coverage paths.
    protected fun shouldUsePlatformBackDismissalCoverage(): Boolean {
        return currentAvdName() !in explicitControlDismissalCoverageAvds
    }

    /**
     * Dismiss the current dialog through the coverage path assigned to this device.
     *
     * @param text Visible dialog button text for devices that cover explicit controls.
     * @param tag Visible dialog control tag for devices that cover explicit controls.
     * @param waitForText Exact text that should appear after the dialog closes, when applicable.
     */
    protected fun dismissDialog(
        text: String? = null,
        tag: String? = null,
        waitForText: String? = null,
    ) {
        require((text == null) != (tag == null)) {
            "dismissDialog requires exactly one fallback text or tag."
        }

        fun explicitControlIsPresent(): Boolean {
            return if (tag != null) {
                composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
            } else {
                composeRule.onAllNodesWithText(text!!).fetchSemanticsNodes().isNotEmpty()
            }
        }

        fun clickExplicitControl() {
            if (tag != null) {
                composeRule.onNodeWithTag(tag).performClick()
            } else {
                composeRule.onNodeWithText(text!!).performClick()
            }
        }

        var shouldUseExplicitControl = !shouldUsePlatformBackDismissalCoverage()
        if (!shouldUseExplicitControl) {
            if (tag != null) {
                waitForTag(tag)
            } else {
                waitForText(text!!)
            }
            try {
                pressDialogBack()
            } catch (failure: RuntimeException) {
                // Espresso can occasionally select an app root that has lost window focus before
                // sending Back. Use this dialog's explicit control rather than retrying that root.
                if (failure.javaClass.name != ROOT_VIEW_WITHOUT_FOCUS_EXCEPTION_NAME) {
                    throw failure
                }
                shouldUseExplicitControl = true
            }
        }
        if (shouldUseExplicitControl) {
            clickExplicitControl()
        }
        if (waitForText != null) {
            try {
                // Occasionally pressDialogBack doesn't seem to find the right thing.
                // E.g. a keyboard might not be fully closed, and the back acts on that
                // instead of the intended dialog.
                // If this times out and the explicit control is still present, then
                // assume something like this happened, and just click that now to
                // move on with the tests.
                this.waitForText(waitForText)
            } catch (failure: ComposeTimeoutException) {
                if (shouldUseExplicitControl || !explicitControlIsPresent()) {
                    throw failure
                }
                clickExplicitControl()
                this.waitForText(waitForText)
            }
        }
    }

    /// Return whether the test device reports usable timing-cue haptics.
    protected fun deviceHasTimingCueHaptics(): Boolean {
        return composeRule.activity.hasTimingCueHaptics()
    }

    /**
     * Return the configured AVD name for the current emulator.
     *
     * Most emulators report this through `ro.boot.qemu.avd_name`, but `Small_Phone` has reported
     * that property as blank while still exposing its name through `ro.kernel.qemu.avd_name`.
     */
    protected fun currentAvdName(): String {
        return shellProperty("ro.boot.qemu.avd_name")
            .ifBlank { shellProperty("ro.kernel.qemu.avd_name") }
    }

    /// Return an Android system property from the test device.
    private fun shellProperty(name: String): String {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.uiAutomation.executeShellCommand("getprop $name").use { descriptor ->
            return FileInputStream(descriptor.fileDescriptor).bufferedReader().use { reader ->
                reader.readText().trim()
            }
        }
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
        composeRule.onNodeWithText("Start point").performClick()
        waitForText("Slide right to unlock")
        unlockLiveScreen()
    }

    /// Verify interrupted and too-short unlock swipes do not unlock, then complete the unlock flow.
    protected fun startPointWithFailedSwipeThenUnlock() {
        composeRule.onNodeWithText("Start point").performClick()
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
        composeRule.onNodeWithText("Continue point").performClick()
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
     * This lets UI tests validate Archived games behavior without first playing and archiving a game
     * through UI actions.
     *
     * @param teamOneName The archived game Team 1 name.
     * @param teamTwoName The archived game Team 2 name.
     */
    protected fun seedArchivedGameProgrammatically(teamOneName: String, teamTwoName: String) {
        composeRule.activityRule.scenario.onActivity { activity ->
            val setup = newSetupGameState(now = 123_000L).copy(
                teamOne = TeamState(name = teamOneName, color = TeamColorChoice.WHITE),
                teamTwo = TeamState(name = teamTwoName, color = TeamColorChoice.BLUE),
            )
            val completed = setup.startGameInTestOrientation(activity).copy(
                phase = GamePhase.GAME_OVER,
                endEpoch = System.currentTimeMillis(),
                countdown = null,
            )
            activity.appViewModel.updateCurrentGame(completed)
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
     * @param teamOneCards The Team 1 player records to install.
     * @param teamTwoCards The Team 2 player records to install.
     */
    protected fun seedInGamePlayerCardsProgrammatically(
        teamOneCards: List<PlayerRecord> = emptyList(),
        teamTwoCards: List<PlayerRecord> = emptyList(),
    ) {
        composeRule.activityRule.scenario.onActivity { activity ->
            val current = activity.appViewModel.currentGame!!
            activity.appViewModel.updateCurrentGame(
                current.copy(
                    teamOnePlayers = teamOneCards.withUniqueInGameCardIndexes(),
                    teamTwoPlayers = teamTwoCards.withUniqueInGameCardIndexes(),
                )
            )
        }
        composeRule.waitForIdle()
    }

    /// Return player records whose in-game card rows have unique editable indexes.
    private fun List<PlayerRecord>.withUniqueInGameCardIndexes(): List<PlayerRecord> {
        var nextIndex = 0
        return map { player ->
            player.copy(
                cards = player.cards.map { card ->
                    card.copy(index = nextIndex++)
                }
            )
        }
    }

    /**
     * End the current game through the model and wait for the game-over prompt.
     *
     * This lets UI tests reach summary/delete/archive flows without scoring through a full game in the UI.
     */
    protected fun endCurrentGameProgrammatically() {
        composeRule.activityRule.scenario.onActivity { activity ->
            val current = activity.appViewModel.currentGame!!
            activity.appViewModel.updateCurrentGame(current.endGameNow(System.currentTimeMillis()))
        }
        waitForText("Game over")
    }

    /**
     * Update the current game state directly.
     *
     * This keeps UI-focused tests from spelling out Activity/ViewModel plumbing when they need a
     * specific model prerequisite before exercising behavior through Compose.
     */
    protected fun updateCurrentStateProgrammatically(update: GameState.() -> GameState) {
        composeRule.activityRule.scenario.onActivity { activity ->
            val current = activity.appViewModel.currentGame!!
            activity.appViewModel.updateCurrentGame(current.update())
        }
        composeRule.waitForIdle()
    }

    /// Read the current game state directly for model assertions or wait conditions.
    protected fun accessCurrentGameState(): GameState {
        return composeRule.activity.appViewModel.currentGame!!
    }

    /**
     * Move the active countdown target relative to now by direct ViewModel state update.
     *
     * This lets UI tests exercise expired-countdown behavior deterministically without waiting for
     * real time to pass.
     *
     * @param secondsRemaining The desired countdown seconds remaining; negative values force expiry.
     */
    protected fun setActiveCountdownRemainingProgrammatically(secondsRemaining: Int) {
        composeRule.activityRule.scenario.onActivity { activity ->
            val current = activity.appViewModel.currentGame!!
            val countdown = current.countdown!!
            activity.appViewModel.updateCurrentGame(
                current.copy(
                    countdown = countdown.copy(
                        targetEpoch = System.currentTimeMillis() + secondsRemaining * 1000L,
                    )
                )
            )
        }
        composeRule.waitForIdle()
    }

    /// Force the active countdown to its expired state without waiting for real time to pass.
    protected fun expireActiveCountdownProgrammatically() {
        setActiveCountdownRemainingProgrammatically(secondsRemaining = -1)
    }

    /**
     * Start a live-point timeout countdown whose target is relative to now.
     *
     * This lets UI tests exercise timeout-countdown behavior without charging a team timeout,
     * handling confirmation dialogs, or setting up per-team timeout availability. The current game
     * is put in a live point, since timeout countdowns are live-point countdowns.
     *
     * @param secondsRemaining The desired countdown seconds remaining; the default is the normal
     * timeout duration.
     */
    protected fun startTimeoutCountdownProgrammatically(secondsRemaining: Int = 70) {
        composeRule.activityRule.scenario.onActivity { activity ->
            val current = activity.appViewModel.currentGame!!
            activity.appViewModel.updateCurrentGame(
                current.copy(
                    phase = GamePhase.LIVE_POINT,
                    countdown = CountdownState(
                        kind = CountdownKind.TIME_OUT,
                        label = "Offense set in",
                        durationSeconds = 70,
                        targetEpoch = System.currentTimeMillis() + secondsRemaining * 1000L,
                    ),
                )
            )
        }
        composeRule.waitForIdle()
    }

    /**
     * Establish whether countdown expiry should automatically advance live game state.
     *
     * Tests that depend on this persisted setting should set it explicitly rather than relying on
     * cleanup from earlier tests.
     *
     * @param automaticallyAdvance Whether expired countdowns should drive model transitions.
     */
    protected fun setAutomaticallyAdvanceCountdowns(automaticallyAdvance: Boolean) {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.appViewModel.updateSettings(
                activity.appViewModel.settings.withAutomaticallyAdvanceCountdowns(automaticallyAdvance)
            )
        }
        composeRule.waitForIdle()
    }

    /// Establish Portrait for a narrative that specifically depends on the Portrait setting.
    internal fun setPortraitOrientationPreference() {
        updateOrientationPreference(OrientationPreference.PORTRAIT)
    }

    /// Establish Landscape for a narrative that specifically depends on the Landscape setting.
    internal fun setLandscapeOrientationPreference() {
        updateOrientationPreference(OrientationPreference.LANDSCAPE)
    }

    /// Select Auto-rotate and wait for the activity orientation controller to observe it.
    internal fun setAutoRotateOrientationPreference() {
        updateOrientationPreference(OrientationPreference.AUTO_ROTATE)
    }

    /// Start this setup using the active-game orientation configured for the current UI test.
    protected fun GameState.startGameInTestOrientation(activity: MainActivity): GameState {
        return startGame(activity.appViewModel.settings.orientationPreference)
    }

    /// Set the active-game orientation and wait for the UI to observe it.
    private fun updateOrientationPreference(preference: OrientationPreference) {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.appViewModel.updateSettings(
                activity.appViewModel.settings.withOrientationPreference(preference)
            )
        }
        composeRule.waitForIdle()
    }

    /**
     * Establish the live-game rule-guidance mode.
     *
     * Tests that depend on dialog duration or automatic acceptance should set this explicitly.
     *
     * @param mode Amount and duration of rule guidance shown during games.
     */
    internal fun setRuleGuidanceMode(mode: RuleGuidanceMode) {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.appViewModel.updateSettings(
                activity.appViewModel.settings.withRuleGuidanceMode(mode)
            )
        }
        composeRule.waitForIdle()
    }

    /**
     * Shorten automatic guidance delays for tests that must exercise timeout behavior.
     *
     * The shared teardown restores the production five-second value after each test.
     *
     * @param timeoutMillis Delay the current test should use.
     */
    protected fun setRuleGuidanceTimeoutForTest(timeoutMillis: Long) {
        require(timeoutMillis > 0L)
        ruleGuidanceTimeoutMillis = timeoutMillis
    }

    /**
     * Establish whether automatic live-point transitions should lock the live screen.
     *
     * Tests that depend on this persisted setting should set it explicitly rather than relying on
     * cleanup from earlier tests.
     *
     * @param automaticallyLock Whether automatic live-point entry should enable lock mode.
     */
    protected fun setAutomaticallyLockLivePoint(automaticallyLock: Boolean) {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.appViewModel.updateSettings(
                activity.appViewModel.settings.withAutomaticallyLockLivePoint(automaticallyLock)
            )
        }
        composeRule.waitForIdle()
    }

    /** Establish the automatic advancement used for newly started countdowns. */
    protected fun setNewCountdownAdvanceSettings(enabled: Boolean, seconds: Int) {
        composeRule.activityRule.scenario.onActivity { activity ->
            val settings = activity.appViewModel.settings
                .withAutomaticallyAdvanceNewCountdowns(enabled)
                .withNewCountdownAdvanceSeconds(seconds)
            activity.appViewModel.updateSettings(settings)
        }
        composeRule.waitForIdle()
    }

    /// Establish the official-clock offset for a test that depends on persisted clock state.
    protected fun setOfficialClockOffset(offsetMillis: Long) {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.appViewModel.updateOfficialClockOffset(offsetMillis)
        }
        composeRule.waitForIdle()
    }

    /**
     * Establish whether ABBA field badges should use sequence shorthand.
     *
     * Tests that depend on this persisted setting should set it explicitly rather than relying on
     * cleanup from earlier tests.
     *
     * @param showAsSequence Whether ABBA badges should display M1/M2/W1/W2 shorthand.
     */
    protected fun setShowAbbaRatioAsSequence(showAsSequence: Boolean) {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.appViewModel.updateSettings(
                activity.appViewModel.settings.withShowAbbaRatioAsSequence(showAsSequence)
            )
        }
        composeRule.waitForIdle()
    }

    /**
     * Establish the timing-alert preferences for tests that depend on persisted alert settings.
     *
     * @param preferences The timing-alert preferences the test expects.
     */
    protected fun setTimingAlertPreferences(preferences: TimingAlertPreferences) {
        composeRule.activityRule.scenario.onActivity { activity ->
            val viewModel = activity.appViewModel
            viewModel.updateSettings(viewModel.settings.withTimingAlerts(preferences))
        }
        composeRule.waitForIdle()
    }

    /// Complete the live-screen unlock gesture and wait for unlocked controls.
    protected fun unlockLiveScreen() {
        composeRule.onNodeWithTag("live-unlock-slider").performTouchInput {
            swipeRight()
        }
        waitForTag("live-center-lock")
    }

    /**
     * Confirm a destructive delete dialog with its slide control.
     *
     * @param dialogTitle The dialog title to wait for before sliding.
     */
    protected fun confirmDeleteWithSlider(dialogTitle: String = "Delete game?") {
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
        openMoreActionsDialog()
        selectMoreActionsCategory("Corrections")
        composeRule.onNodeWithText("Adjust score").performClick()
        waitForText("Adjust score")
        composeRule.onAllNodesWithText("+1")[0].performClick()
        composeRule.onAllNodesWithText("+1")[1].performClick()
        composeRule.onAllNodesWithText("-1")[0].performClick()
        composeRule.onAllNodesWithText("-1")[1].performClick()
        composeRule.onAllNodesWithText("+1")[0].performClick()
        composeRule.onNodeWithText("Set").performClick()
        waitForText("Undo Score adjustment")
    }

    /// Exercise both timeout adjustment controls and restore their initial values.
    protected fun applyTimeoutAdjustment() {
        val teamOneName = accessCurrentGameState().teamOne.name
        val teamTwoName = accessCurrentGameState().teamTwo.name
        openMoreActionsDialog()
        selectMoreActionsCategory("Corrections")
        clickMoreActionsItem("Adjust timeouts")
        waitForText("Adjust the number of timeouts used by each team this half.")
        composeRule.onAllNodesWithText("+1")[0].performClick()
        composeRule.onAllNodesWithText("+1")[1].performClick()
        composeRule.onAllNodesWithText("$teamOneName: 1").assertCountEquals(1)
        composeRule.onAllNodesWithText("$teamTwoName: 1").assertCountEquals(1)
        composeRule.onAllNodesWithText("-1")[0].performClick()
        composeRule.onAllNodesWithText("-1")[1].performClick()
        composeRule.onAllNodesWithText("$teamOneName: 0").assertCountEquals(1)
        composeRule.onAllNodesWithText("$teamTwoName: 0").assertCountEquals(1)
        composeRule.onNodeWithText("Set").performClick()
        waitForText("Undo Timeout adjustment")
    }

    /// Exercise the pull-violation adjustment dialog with a small nonzero correction.
    protected fun applyPullViolationAdjustment() {
        openMoreActionsDialog()
        selectMoreActionsCategory("Corrections")
        clickMoreActionsItem("Adjust pull violations")
        waitForTag("adjust-pull-violations-confirm")
        repeat(6) { index ->
            composeRule.onAllNodesWithText("+1")[index].performClick()
        }
        repeat(6) { index ->
            composeRule.onAllNodesWithText("-1")[index].performClick()
        }
        composeRule.onAllNodesWithText("+1")[0].performClick()
        composeRule.onNodeWithTag("adjust-pull-violations-confirm").performTouchInput {
            click()
        }
        waitForText("Undo Pull violation adjustment")
    }

    /// Exercise the Cards / techs adjustment dialog by changing a visible count.
    protected fun applyCardTechAdjustment() {
        openMoreActionsDialog()
        selectMoreActionsCategory("Corrections")
        clickMoreActionsItem("Adjust cards / techs")
        waitForTag("cards-adjust-team-one-blue-increment")
        composeRule.onNodeWithTag("cards-adjust-team-one-blue-increment").performClick()
        composeRule.onNodeWithText("Done").performClick()
        waitForText("Undo Adjust blue card/tech counts")
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
     * Record a blue card through the live Card dialog.
     *
     * @param team The team receiving the blue card.
     * @param expectedMessage The popup text expected after recording.
     */
    protected fun recordBlueCard(team: TeamId, expectedMessage: String) {
        openCardsDialog(team)
        tapCardDialogAction(team, "Blue")
        waitForText("Blue Card")
        waitForText(expectedMessage)
        composeRule.onNodeWithText("OK").performClick()
    }

    /**
     * Record a technical foul through the live field action.
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
        composeRule.onNodeWithTag(teamActionTag(team, "tech")).performClick()
        waitForText(expectedMessage, substring = substring)
        composeRule.onNodeWithText("OK").performClick()
    }

    /**
     * Tap one action in the live Card dialog.
     *
     * @param team The team whose action should be tapped.
     * @param label The action label to tap.
     */
    protected fun tapCardDialogAction(team: TeamId, label: String) {
        composeRule.onNodeWithTag("card-dialog-${team.name}-${label.lowercase()}").performClick()
    }

    /// Assert that the live screen's main controls are visible.
    protected fun assertLiveScreen() {
        val fixedOrientation = when (
            composeRule.activity.appViewModel.settings.orientationPreference
        ) {
            OrientationPreference.PORTRAIT -> Configuration.ORIENTATION_PORTRAIT
            OrientationPreference.LANDSCAPE -> Configuration.ORIENTATION_LANDSCAPE
            OrientationPreference.AUTO_ROTATE -> null
        }
        if (fixedOrientation != null) {
            waitForConfigurationOrientation(fixedOrientation)
        }
        waitForText("More actions")
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "card")).assertIsDisplayed()
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_TWO, "tech")).assertIsDisplayed()
        composeRule.onNodeWithText("More actions").assertIsDisplayed()
    }

    /// Wait until Android has applied a requested portrait or landscape configuration.
    private fun waitForConfigurationOrientation(orientation: Int) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.activity.resources.configuration.orientation == orientation
        }
    }

    /**
     * Open a setup dialog from Game rules, verify it appears, then cancel it.
     *
     * @param buttonText The row text that opens the dialog.
     * @param dialogTitle The title expected after opening the dialog.
     */
    protected fun openSetupDialog(buttonText: String, dialogTitle: String) {
        openGameRulesSetupEditor()
        composeRule.onNodeWithText(buttonText).performScrollTo().performClick()
        waitForText(dialogTitle)
        composeRule.onNodeWithText("Cancel").assertIsDisplayed()
        dismissDialog(text = "Cancel", waitForText = "Game to")
        closeSetupEditor()
    }

    /// Open the setup start-time editor.
    protected fun openStartTimeSetupEditor() {
        composeRule.onNodeWithTag("setup-edit-game-information").performScrollTo().performClick()
        waitForTag("setup-start-date-field")
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

    /// Open the setup game-information editor.
    protected fun openGameInformationSetupEditor() {
        composeRule.onNodeWithTag("setup-edit-game-information").performScrollTo().performClick()
        waitForText("Tournament name")
    }

    /**
     * Open the setup prior-cards editor from one team's setup button.
     *
     * @param team The team whose Cards button should open the editor.
     */
    protected fun openPriorCardsSetupEditor(team: TeamId = TeamId.TEAM_TWO) {
        val fieldLabel = if (team == TeamId.TEAM_ONE) "Team 1" else "Team 2"
        composeRule.onNodeWithTag("setup-$fieldLabel-cards-button").performScrollTo().performClick()
        waitForText("Add card holder")
    }

    /// Close the current setup overview editor dialog.
    protected fun closeSetupEditor() {
        composeRule.onNodeWithText("Done").performClick()
    }

    /// Open the live Card dialog for one team.
    protected fun openCardsDialog(team: TeamId = TeamId.TEAM_ONE) {
        composeRule.onNodeWithTag(teamActionTag(team, "card")).performClick()
        waitForText("Assess a card")
        composeRule.onNodeWithTag("card-dialog-${team.name}-yellow").assertExists()
    }

    /// Open the live More actions dialog.
    protected fun openMoreActionsDialog() {
        composeRule.onAllNodesWithText("More actions").onFirst().performClick()
        waitForText("Update game setup")
    }

    /// Select a category in the open More actions dialog.
    protected fun selectMoreActionsCategory(title: String) {
        composeRule.onNodeWithText(title).performClick()
    }

    /** Click an item in the open More actions category for the matrix device's menu geometry. */
    protected fun clickMoreActionsItem(label: String) {
        val item = composeRule.onNodeWithText(label)
        if (testOrientationPreference() == OrientationPreference.LANDSCAPE) {
            item.performClick()
        } else {
            item.performScrollTo().performClick()
        }
    }

    /**
     * Open a dialog from More actions and cancel it.
     *
     * @param category The More actions category containing the item.
     * @param label The More actions item label that opens the dialog.
     */
    protected fun openMoreActionsDialogAndCancel(category: String, label: String) {
        selectMoreActionsCategory(category)
        clickMoreActionsItem(label)
        composeRule.onAllNodesWithText("Close").assertCountEquals(0)
        dismissDialog(text = "Cancel", waitForText = label)
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
     * Wait until text leaves the Compose semantics tree.
     *
     * @param text The text to wait for removal of.
     * @param substring Whether substring matching should be used.
     */
    protected fun waitForNoText(text: String, substring: Boolean = false) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithText(text, substring = substring).fetchSemanticsNodes().isEmpty()
        }
    }

    /**
     * Wait until a test tag appears in the Compose semantics tree.
     *
     * @param testTag The test tag to wait for.
     */
    protected fun waitForTag(testTag: String) {
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.onAllNodesWithTag(testTag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    /**
     * Build the test tag for one team action on the live field.
     *
     * @param team The team whose action button is targeted.
     * @param action The action suffix, such as `goal`, `timeout`, or `pull-violation`.
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
            "Half cap" to "Half cap" -> CapType.HALF
            "Soft cap" to "Soft cap" -> CapType.SOFT
            "Hard cap" to "Hard cap" -> CapType.HARD
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
            nominalSoftCapMinutes = capMinutes,
            useHardCap = capType == CapType.HARD,
            nominalHardCapMinutes = capMinutes,
        )
    }
}
