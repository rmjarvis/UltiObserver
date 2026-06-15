package rmjarvis.ultiobserver
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.swipeRight
import java.time.LocalTime
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/// Tests for representative live-game UI flows from setup through play.
@RunWith(AndroidJUnit4::class)
class TestLiveGameFlowUi : MainActivityUiTestFixtures() {
    /**
     * Test a representative complete game from setup through halftime to final score.
     * Keep this as a user-visible UI story that checks flow, not detailed model accounting.
     */
    @Test
    fun normalGamePath() {
        clearArchivedGamesProgrammatically()
        val suffix = System.currentTimeMillis().toString().takeLast(6)
        val viscousCoupling = "VC$suffix"
        val animal = "AN$suffix"

        // Set up a short non-default game so the UI story covers setup editing,
        // halftime, and game over without a long repetitive scoring sequence.
        openNewGameSetup()
        replaceSetupTeamName("Team 1", viscousCoupling)
        replaceSetupTeamName("Team 2", animal)
        setIntegerSetupValue("Game to", "Game to", "Points", "5")
        setIntegerSetupValue("Halftime", "Halftime", "Minutes", "1")
        setCapRuleToNone("Half cap", "Half cap")
        setCapRuleToNone("Soft cap", "Soft cap")
        setCapRuleToNone("Hard cap", "Hard cap")
        openStartingPullSetupEditor()
        composeRule.onNodeWithTag("setup-pulling-from-${FieldEnd.NEAR.name}").performClick()
        closeSetupEditor()
        startGameFromSetup()
        composeRule.onNodeWithText(viscousCoupling).assertIsDisplayed()
        composeRule.onNodeWithText(animal).assertIsDisplayed()

        // The opening pull starts the first live point; a short swipe should fail before a full unlock.
        startPointWithFailedSwipeThenUnlock()

        // The top-right Lock action should relock the same live layout.
        composeRule.onNodeWithTag("live-top-lock").performClick()
        waitForText("Slide right to unlock")
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "goal")).assertIsNotEnabled()
        unlockLiveScreen()

        // The center field Lock action should also relock the screen during a live point.
        composeRule.onNodeWithTag("live-center-lock").performClick()
        waitForText("Slide right to unlock")
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_TWO, "timeout")).assertIsNotEnabled()
        unlockLiveScreen()

        // Animal calls a live-point timeout, then play resumes from the timeout countdown.
        recordTimeout(TeamId.TEAM_TWO, "Undo Timeout by $animal")
        continuePointAndUnlock()

        // Viscous Coupling gets two early card points, then a third card that needs a misconduct choice.
        // Back from that choice should return to the player-number dialog with the entered number intact.
        recordYellowCard(TeamId.TEAM_ONE, "17", "Yellow card on player 17.\n$viscousCoupling has 1 blue card.")
        recordBlueCard(TeamId.TEAM_ONE, "$viscousCoupling has 2 total blue cards.")
        recordYellowCard(
            team = TeamId.TEAM_ONE,
            playerNumber = "8",
            expectedMessage = "$viscousCoupling has 3 total blue cards.",
            misconductChoice = "Offense",
            expectedMisconductMessage = "Disc moves to the reverse brick in the end zone being defended.",
            verifyMisconductBackReturnsToNumberDialog = true,
        )
        waitForText("Start misconduct countdown")
        composeRule.onNodeWithTag("live-top-lock").performClick()
        waitForText("Slide right to unlock")
        composeRule.onAllNodesWithTag("live-start-misconduct-countdown").assertCountEquals(0)
        unlockLiveScreen()
        waitForText("Start misconduct countdown")
        composeRule.onNodeWithTag("live-start-misconduct-countdown").performClick()
        waitForText("Offense set in", substring = true)
        continuePointAndUnlock()
        recordYellowCard(
            team = TeamId.TEAM_ONE,
            playerNumber = "9",
            expectedMessage = "$viscousCoupling has 4 total blue cards.",
            misconductChoice = "Defense",
            expectedMisconductMessage = "Disc moves to the brick nearest the attacking end zone.",
        )
        recordRedCard(
            team = TeamId.TEAM_ONE,
            playerNumber = "12",
            expectedMessage = "$viscousCoupling has 6 total blue cards.",
            misconductChoice = "Defense",
            expectedMisconductMessage = "Disc moves to the brick nearest the attacking end zone.",
            verifyMisconductBackReturnsToNumberDialog = true,
        )

        // Viscous Coupling scores the first point, then Animal false-starts and that entry is undone.
        recordGoal(TeamId.TEAM_ONE, "Undo Goal by $viscousCoupling")
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_TWO, "pull-infraction")).performClick()
        waitForText("Defense gets to set up.")
        composeRule.onNodeWithText("OK").performClick()
        composeRule.onNodeWithText("Undo False start on $animal").performClick()

        // Viscous Coupling then records an offsides; the duplicate offsides button is disabled.
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "pull-infraction")).performClick()
        waitForText("Start at brick mark")
        composeRule.onNodeWithText("OK").performClick()
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "pull-infraction")).assertIsNotEnabled()

        // Animal picks up two yellows and two technical fouls during the live point.
        recordYellowCard(TeamId.TEAM_TWO, "23", "Yellow card on player 23.\n$animal has 1 blue card.")
        recordYellowCard(TeamId.TEAM_TWO, "8", "Yellow card on player 8.\n$animal has 2 total blue cards.")
        recordTechnicalFoul(TeamId.TEAM_TWO, "$animal has 1 technical foul.")
        recordTechnicalFoul(TeamId.TEAM_TWO, "$animal has 2 technical fouls.")

        // Viscous Coupling calls a live-point timeout before Animal finishes the point.
        recordTimeout(TeamId.TEAM_ONE, "Undo Timeout by $viscousCoupling")
        continuePointAndUnlock()
        recordGoal(TeamId.TEAM_TWO, "Undo Goal by $animal")

        // Animal uses its final first-half timeout, then gets the out-of-timeouts cue.
        recordTimeout(TeamId.TEAM_TWO, "Undo Timeout by $animal")
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_TWO, "timeout")).performClick()
        waitForText("$animal is out of timeouts.")
        composeRule.onNodeWithText("OK").performClick()

        // Animal reaches the technical-foul threshold between points, so the UI shows the yardage cue.
        recordTechnicalFoul(
            team = TeamId.TEAM_TWO,
            expectedMessage = "Receiving team starts at attacking brick.",
            substring = true,
        )
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "pull-infraction")).assertIsNotEnabled()
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_TWO, "pull-infraction")).assertIsNotEnabled()

        // Viscous Coupling scores the next two points, checking that halftime interrupts the flow.
        recordGoal(TeamId.TEAM_ONE, "Undo Goal by $viscousCoupling")
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "goal")).performClick()
        waitForText("Halftime")
        composeRule.onNodeWithText("OK").performClick()
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "goal")).performClick()
        waitForText("Undo Goal by $viscousCoupling")
        composeRule.onNodeWithText("Undo Goal by $viscousCoupling").performClick()
        composeRule.onAllNodesWithText("Announce halftime.").assertCountEquals(0)

        // Touch the visible correction controls, then jump the countdown to its expired state.
        composeRule.onAllNodesWithText("+5").onFirst().performClick()
        composeRule.onAllNodesWithText("-5").onFirst().performClick()
        setActiveCountdownRemainingProgrammatically(secondsRemaining = -1)
        waitForText("Start point")

        // After halftime, Animal scores and uses one second-half timeout before the next pull.
        startPointAndUnlock()
        recordGoal(TeamId.TEAM_TWO, "Undo Goal by $animal")
        recordTimeout(TeamId.TEAM_TWO, "Undo Timeout by $animal")

        // Animal ties the game, Viscous Coupling goes ahead, and Animal wins on universe.
        recordGoal(TeamId.TEAM_TWO, "Undo Goal by $animal")
        recordGoal(TeamId.TEAM_ONE, "Undo Goal by $viscousCoupling")
        recordGoal(TeamId.TEAM_TWO, "Undo Goal by $animal")
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_TWO, "goal")).performClick()
        waitForText("Game over")
        composeRule.onNodeWithText("OK").performClick()
        composeRule.onNodeWithText("Game summary").assertIsDisplayed()
        composeRule.onNodeWithText("$viscousCoupling 4").assertIsDisplayed()
        composeRule.onNodeWithText("$animal 5").assertIsDisplayed()
        composeRule.onNodeWithText(viscousCoupling).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(animal).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Undo End game").performClick()
        assertLiveScreen()

        // Manually ending from the restored final state should return to the same summary.
        openOtherSheet()
        composeRule.onNodeWithText("End game").performClick()
        waitForText("Game over")
        composeRule.onNodeWithText("OK").performClick()
        composeRule.onNodeWithText("Game summary").assertIsDisplayed()
        composeRule.onNodeWithText("$viscousCoupling 4").assertIsDisplayed()
        composeRule.onNodeWithText("$animal 5").assertIsDisplayed()

        // The finished game should go home from the visible Back action, archive, and then reopen from Archived games.
        composeRule.onNodeWithText("Back").performClick()
        waitForText("Completed game")
        composeRule.onNodeWithText("$viscousCoupling 4 - 5 $animal").performClick()
        waitForText("Game summary")
        assertNoGameOverDialog()
        composeRule.onNodeWithText("Back").performClick()
        waitForText("Completed game")
        composeRule.onNodeWithText("Archive completed game").performClick()
        waitForText("See archived games")
        composeRule.onNodeWithText("See archived games").performClick()
        waitForText("$viscousCoupling 4 - 5 $animal")
        composeRule.onNodeWithTag("archived-game-$viscousCoupling 4 - 5 $animal").performClick()
        waitForText("Game summary")
        assertNoGameOverDialog()
        composeRule.onNodeWithText("$animal 5").assertIsDisplayed()
        composeRule.onNodeWithText("Back").performClick()
        waitForText("Archived games")
        composeRule.onNodeWithText("Back").performClick()
        waitForText("Start new game")
    }

    /// Assert that the game-over confirmation dialog is not currently visible.
    private fun assertNoGameOverDialog() {
        assertTrue(composeRule.onAllNodesWithText("Game over").fetchSemanticsNodes().isEmpty())
    }

    /**
     * Test the primary live screen actions that should be available directly from the phone.
     * Keep the assertions at the visible undo/message level; domain helpers own detailed state checks.
     */
    @Test
    fun livePrimaryActionsAndUndoPath() {
        startLiveGameProgrammatically()

        // Each side can record only one pull infraction of its type for the current pull sequence.
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_TWO, "pull-infraction")).performClick()
        waitForText("Defense gets to set up.")
        composeRule.onNodeWithText("OK").performClick()
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_TWO, "pull-infraction")).assertIsNotEnabled()

        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "pull-infraction")).performClick()
        waitForText("Start at brick mark")
        composeRule.onNodeWithText("OK").performClick()
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "pull-infraction")).assertIsNotEnabled()

        // A between-points goal implicitly starts the point and exposes a useful undo.
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "goal")).performClick()
        waitForText("Undo Goal by Team 1")
        composeRule.onNodeWithText("Undo Goal by Team 1").performClick()
        waitForText("Lock")
        waitForText("Redo")
        composeRule.onNodeWithText("Redo").performClick()
        waitForText("Undo Goal by Team 1")

        // Timeout should remain wired after the undo path, including undo when redo is also available.
        recordTimeout(TeamId.TEAM_ONE, "Undo Timeout by Team 1")
        composeRule.onNodeWithText("Undo Timeout by Team 1").performClick()
        waitForText("Undo Goal by Team 1")
        waitForText("Redo")
        composeRule.onNodeWithText("Undo Goal by Team 1").performClick()
        waitForText("Redo")
    }

    /// Verify expired-pull actions and timing alert delivery are still wired through the live screen.
    @Test
    fun expiredPullActionsAndTimingAlertsAreWired() {
        startLiveGameProgrammatically()

        // Drive each global/cue combination directly to verify alert delivery stays wired.
        triggerDueTimeoutTwentyCue(
            globalMode = TimingAlertGlobalMode.VIBRATION_ONLY,
            cueMode = TimingAlertMode.VIBRATE,
            vibrateWithSounds = false,
            cueAlreadyDue = true,
            waitAfterDueMillis = 1_500L,
        )
        triggerDueTimeoutTwentyCue(
            globalMode = TimingAlertGlobalMode.SOUNDS_ON,
            cueMode = TimingAlertMode.TICK,
            vibrateWithSounds = true,
            // Start outside the direct-handling window, then let the listener re-check and wait until due.
            cueDueInMillis = 650L,
            waitAfterDueMillis = 700L,
        )
        triggerDueTimeoutTwentyCue(
            globalMode = TimingAlertGlobalMode.SOUNDS_ON,
            cueMode = TimingAlertMode.BEEP,
            vibrateWithSounds = false,
        )
        triggerDueTimeoutTwentyCue(
            globalMode = TimingAlertGlobalMode.OFF,
            cueMode = TimingAlertMode.NONE,
            vibrateWithSounds = false,
        )

        // Seed an expired-pull surface so the test can focus on the visible correction actions.
        composeRule.activityRule.scenario.onActivity { activity ->
            val current = activity.appViewModel.liveState!!
            activity.appViewModel.updateLiveGame(
                current.copy(
                    phase = GamePhase.BETWEEN_POINTS,
                    countdown = null,
                    pullCountdownExpired = true,
                )
            )
        }
        waitForText("Time violation")
        waitForText("Restart countdown")

        composeRule.activityRule.scenario.onActivity { activity ->
            val current = activity.appViewModel.liveState!!
            activity.appViewModel.updateLiveGame(current.copy(pendingCapOffer = CapType.HALF))
        }
        waitForText("Apply half cap?")
        if (shouldUsePlatformBackDismissalCoverage()) {
            pressDialogBack()
            waitForText("Apply half cap?")
        }
        composeRule.onNodeWithText("No").performClick()

        composeRule.activityRule.scenario.onActivity { activity ->
            val current = activity.appViewModel.liveState!!
            activity.appViewModel.updateLiveGame(current.copy(pendingCapOffer = CapType.SOFT))
        }
        waitForText("Apply soft cap?")
        composeRule.onNodeWithText("Apply").performClick()
        waitForText("Undo Apply soft cap")

        composeRule.activityRule.scenario.onActivity { activity ->
            val current = activity.appViewModel.liveState!!
            val targetEpoch = System.currentTimeMillis() + 90_000L
            activity.appViewModel.updateLiveGame(
                current.copy(
                    phase = GamePhase.BETWEEN_POINTS,
                    countdown = CountdownState(
                        kind = CountdownKind.MISCONDUCT_BETWEEN_POINTS,
                        label = "Offense set in",
                        durationSeconds = 100,
                        targetEpoch = targetEpoch,
                    ),
                    pullCountdownExpired = false,
                )
            )
        }
        waitForText("Offense is set")
        composeRule.onNodeWithText("Offense is set").performClick()
        waitForText("Defense check in", substring = true)

        composeRule.activityRule.scenario.onActivity { activity ->
            val current = activity.appViewModel.liveState!!
            activity.appViewModel.updateLiveGame(
                current.copy(
                    phase = GamePhase.BETWEEN_POINTS,
                    countdown = null,
                    pullCountdownExpired = true,
                )
            )
        }
        waitForText("Time violation")
        waitForText("Restart countdown")

        // Locking the live screen should hide expired-pull correction actions until unlocked.
        composeRule.onNodeWithTag("live-top-lock").performClick()
        waitForText("Slide right to unlock")
        composeRule.onAllNodesWithTag("live-time-violation").assertCountEquals(0)
        composeRule.onAllNodesWithTag("live-restart-pull-countdown").assertCountEquals(0)
        unlockLiveScreen()
        waitForText("Time violation")
        waitForText("Restart countdown")

        // Restart countdown should be undoable and return the expired-pull action surface.
        composeRule.onNodeWithText("Restart countdown").performClick()
        waitForText("Undo Restart pull countdown")
        composeRule.onNodeWithText("Undo Restart pull countdown").performClick()
        waitForText("Redo")
        waitForText("Time violation")

        // Time violation should ask for the violating team and report the warning consequence.
        composeRule.onNodeWithText("Time violation").performClick()
        waitForText("Which team committed the time violation?")
        if (shouldUsePlatformBackDismissalCoverage()) {
            pressDialogBack()
            composeRule.onAllNodesWithText("Which team committed the time violation?").assertCountEquals(0)
            composeRule.onNodeWithText("Time violation").performClick()
            waitForText("Which team committed the time violation?")
        }
        composeRule.onAllNodesWithText("Team 1").onLast().performClick()
        waitForText("now has 30 seconds", substring = true)
        composeRule.onNodeWithText("OK").performClick()

        composeRule.activityRule.scenario.onActivity { activity ->
            val current = activity.appViewModel.liveState!!
            activity.appViewModel.updateLiveGame(
                current.copy(
                    phase = GamePhase.BETWEEN_POINTS,
                    countdown = null,
                    pullCountdownExpired = true,
                )
            )
        }
        waitForText("Time violation")
        composeRule.onNodeWithText("Time violation").performClick()
        waitForText("Which team committed the time violation?")
        composeRule.onAllNodesWithText("Team 2").onLast().performClick()
        waitForText("now has 30 seconds", substring = true)
        if (shouldUsePlatformBackDismissalCoverage()) {
            pressDialogBack()
        } else {
            composeRule.onNodeWithText("OK").performClick()
        }
        composeRule.onAllNodesWithText("now has 30 seconds", substring = true).assertCountEquals(0)
    }

    /// Verify the reserved countdown row prevents field diagram movement when the countdown clears.
    @Test
    fun fieldDiagramDoesNotMoveWhenCountdownClears() {
        startLiveGameProgrammatically()

        val fieldTopBeforeStartPoint = composeRule.onNodeWithTag("live-field-diagram")
            .fetchSemanticsNode()
            .boundsInRoot
            .top
        composeRule.onNodeWithText("Start point").performClick()
        waitForText("Slide right to unlock")
        val fieldTopAfterStartPoint = composeRule.onNodeWithTag("live-field-diagram")
            .fetchSemanticsNode()
            .boundsInRoot
            .top
        assertEquals(fieldTopBeforeStartPoint, fieldTopAfterStartPoint, 0.5f)
    }

    /// Verify automatic countdown expiration enters live play and locks the screen when settings allow it.
    @Test
    fun automaticCountdownTransitionsLockScreen() {
        startLiveGameProgrammatically()
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.appViewModel.updateAutomaticallyAdvanceCountdowns(true)
            activity.appViewModel.updateAutomaticallyLockLivePoint(true)
        }
        composeRule.waitForIdle()

        composeRule.activityRule.scenario.onActivity { activity ->
            val current = activity.appViewModel.liveState!!
            activity.appViewModel.updateLiveGame(
                current.copy(
                    countdown = current.countdown!!.copy(targetEpoch = System.currentTimeMillis() - 1_000L),
                )
            )
        }
        waitForText("Slide right to unlock")
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "goal")).assertIsNotEnabled()
        unlockLiveScreen()
        composeRule.onNodeWithText("Undo Start point").performClick()
        waitForText("Lock")
        composeRule.onAllNodesWithText("Slide right to unlock").assertCountEquals(0)
        composeRule.onNodeWithText("Redo").performClick()

        composeRule.activityRule.scenario.onActivity { activity ->
            val current = activity.appViewModel.liveState!!
            activity.appViewModel.updateLiveGame(
                current.copy(
                    countdown = CountdownState(
                        kind = CountdownKind.TIME_OUT,
                        label = "Offense set in",
                        durationSeconds = 70,
                        targetEpoch = System.currentTimeMillis() - 1_000L,
                    ),
                )
            )
        }
        waitForText("Slide right to unlock")
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_TWO, "timeout")).assertIsNotEnabled()
        unlockLiveScreen()

        // If auto-advance is re-enabled before undoing a manual start from an expired countdown,
        // undo should not immediately relock the observer out of the restored live screen.
        startLiveGameProgrammatically()
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.appViewModel.updateAutomaticallyAdvanceCountdowns(false)
            activity.appViewModel.updateAutomaticallyLockLivePoint(true)
            val current = activity.appViewModel.liveState!!
            activity.appViewModel.updateLiveGame(
                current.copy(
                    countdown = current.countdown!!.copy(targetEpoch = System.currentTimeMillis() - 1_000L),
                )
            )
        }
        waitForText("Start point")
        composeRule.onNodeWithText("Start point").performClick()
        waitForText("Slide right to unlock")
        unlockLiveScreen()
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.appViewModel.updateAutomaticallyAdvanceCountdowns(true)
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Undo Start point").performClick()
        composeRule.waitUntil(timeoutMillis = 10_000) {
            composeRule.activity.appViewModel.liveState!!.phase == GamePhase.LIVE_POINT
        }
        waitForText("Lock")
        composeRule.onAllNodesWithText("Slide right to unlock").assertCountEquals(0)
    }

    /// Verify disabling automatic countdown advancement leaves expired countdowns on the observer-facing controls.
    @Test
    fun automaticCountdownTransitionsCanBeDisabled() {
        startLiveGameProgrammatically()
        val checkAfter = System.currentTimeMillis() + 1_200L

        // The visible countdown can be paused and resumed before exercising expiration behavior.
        composeRule.onNodeWithTag("live-pause-countdown").performClick()
        waitForText("Paused")
        composeRule.onNodeWithTag("live-resume-countdown").assertIsDisplayed()
        assertTrue(composeRule.activity.appViewModel.liveState!!.countdown!!.isPaused())
        composeRule.onNodeWithTag("live-resume-countdown").performClick()
        composeRule.onAllNodesWithText("Paused").assertCountEquals(0)
        composeRule.onNodeWithTag("live-pause-countdown").assertIsDisplayed()
        assertTrue(!composeRule.activity.appViewModel.liveState!!.countdown!!.isPaused())

        composeRule.activityRule.scenario.onActivity { activity ->
            activity.appViewModel.updateAutomaticallyAdvanceCountdowns(false)
            val current = activity.appViewModel.liveState!!
            activity.appViewModel.updateLiveGame(
                current.copy(
                    countdown = current.countdown!!.copy(targetEpoch = System.currentTimeMillis() - 1_000L),
                )
            )
        }

        composeRule.waitUntil(timeoutMillis = 2_000) {
            System.currentTimeMillis() >= checkAfter
        }
        assertEquals(GamePhase.BETWEEN_POINTS, composeRule.activity.appViewModel.liveState!!.phase)
        waitForText("Start point")
        composeRule.onAllNodesWithText("Slide right to unlock").assertCountEquals(0)

        composeRule.activityRule.scenario.onActivity { activity ->
            activity.appViewModel.updateAutomaticallyAdvanceCountdowns(true)
        }
    }

    /// Verify disabling automatic live-point locking leaves the point live but unlocked after timer expiration.
    @Test
    fun automaticLivePointLockCanBeDisabled() {
        startLiveGameProgrammatically()

        composeRule.activityRule.scenario.onActivity { activity ->
            activity.appViewModel.updateAutomaticallyLockLivePoint(false)
        }
        composeRule.onNodeWithText("Start point").performClick()
        waitForText("Lock")
        composeRule.onAllNodesWithText("Slide right to unlock").assertCountEquals(0)
        recordTimeout(TeamId.TEAM_ONE, "Undo Timeout by Team 1")
        composeRule.onNodeWithText("Continue point").performClick()
        waitForText("Lock")
        composeRule.onAllNodesWithText("Slide right to unlock").assertCountEquals(0)

        startLiveGameProgrammatically()
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.appViewModel.updateAutomaticallyAdvanceCountdowns(true)
            activity.appViewModel.updateAutomaticallyLockLivePoint(false)
            val current = activity.appViewModel.liveState!!
            activity.appViewModel.updateLiveGame(
                current.copy(
                    countdown = current.countdown!!.copy(targetEpoch = System.currentTimeMillis() - 1_000L),
                )
            )
        }

        composeRule.waitUntil(timeoutMillis = 2_000) {
            composeRule.activity.appViewModel.liveState!!.phase == GamePhase.LIVE_POINT
        }
        waitForText("Undo Start point")
        composeRule.onAllNodesWithText("Slide right to unlock").assertCountEquals(0)
        composeRule.onNodeWithTag("live-top-lock").assertIsDisplayed()

        composeRule.activityRule.scenario.onActivity { activity ->
            activity.appViewModel.updateAutomaticallyLockLivePoint(true)
        }
    }

    /**
     * Drive a timeout twenty-second cue through the activity with a specific alert configuration.
     *
     * @param globalMode The global timing alert mode to apply before the cue fires.
     * @param cueMode The per-cue alert mode for the timeout twenty-second cue.
     * @param vibrateWithSounds Whether sound cues should also vibrate.
     * @param cueAlreadyDue Whether the cue should already be due when the listener sees the countdown.
     * @param cueDueInMillis How soon a scheduled cue should fire when it is not already due.
     * @param waitAfterDueMillis How long to wait after the cue's due time so asynchronous delivery can run.
     */
    private fun triggerDueTimeoutTwentyCue(
        globalMode: TimingAlertGlobalMode,
        cueMode: TimingAlertMode,
        vibrateWithSounds: Boolean,
        cueAlreadyDue: Boolean = false,
        cueDueInMillis: Long = 500L,
        waitAfterDueMillis: Long = 300L,
    ) {
        var dueEpoch = 0L
        composeRule.activityRule.scenario.onActivity { activity ->
            val now = System.currentTimeMillis()
            dueEpoch = if (cueAlreadyDue) now - 500L else now + cueDueInMillis
            activity.appViewModel.updateTimingAlertGlobalMode(globalMode)
            activity.appViewModel.updateTimingAlertVibrateWithSounds(vibrateWithSounds)
            activity.appViewModel.updateTimingCueMode(TimingCueId.TIMEOUT_OFFENSE_TWENTY, cueMode)
            val current = activity.appViewModel.liveState!!
            activity.appViewModel.updateLiveGame(
                current.copy(
                    phase = GamePhase.LIVE_POINT,
                    countdown = CountdownState(
                        kind = CountdownKind.TIME_OUT,
                        label = "Offense set in",
                        durationSeconds = 70,
                        targetEpoch = dueEpoch + 20_000L,
                    ),
                    pullCountdownExpired = false,
                )
            )
        }
        waitForText("Offense set in", substring = true)
        composeRule.waitUntil(timeoutMillis = 3_000) {
            System.currentTimeMillis() >= dueEpoch + waitAfterDueMillis
        }
        composeRule.waitForIdle()
    }

}
