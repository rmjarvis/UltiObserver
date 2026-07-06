package rmjarvis.ultiobserver

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/// Tests for representative live-game UI flows from setup through play.
@RunWith(AndroidJUnit4::class)
class TestLiveGameFlowUi : MainActivityUiTestFixtures() {
    /**
     * Test pull-violation actions and variants available from the live screen.
     */
    @Test
    fun pullViolations() {
        // Standard games allow one False start and one Offsides per pull sequence.
        startLiveGameProgrammatically()
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_TWO, "pull-violation")).performClick()
        waitForText("Team 1 gets to set up on defense.", substring = true)
        dismissDialog(text = "Cancel")
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_TWO, "pull-violation"))
            .assertIsDisplayed()
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_TWO, "pull-violation")).performClick()
        waitForText("Team 1 gets to set up on defense.", substring = true)
        composeRule.onNodeWithText("OK").performClick()
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_TWO, "pull-violation"))
            .assertIsNotEnabled()
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "pull-violation")).performClick()
        waitForText("Team 2 starts at the brick mark.", substring = true)
        composeRule.onNodeWithText("OK").performClick()
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "pull-violation"))
            .assertIsNotEnabled()

        // Mixed games let the pulling-team dialog switch between Offsides and Majority pull.
        val setup = newSetupGameState(now = System.currentTimeMillis()).copy(
            division = GameDivision.MIXED,
        )
        startBetweenPointsProgrammatically(setup, scoringTeam = TeamId.TEAM_ONE)

        // The initial dialog is Offsides, then the switch action toggles to majority pull and back.
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "pull-violation")).performClick()
        waitForText("Offsides")
        waitForText("This was a Majority pull rule violation")
        composeRule.onNodeWithText("This was a Majority pull rule violation").performClick()
        waitForText("Majority pull rule violation")
        waitForText("This was an Offsides")
        composeRule.onNodeWithText("This was an Offsides").performClick()
        waitForText("Offsides")
        composeRule.onNodeWithText("This was a Majority pull rule violation").performClick()
        waitForText("Majority pull rule violation")
        composeRule.onNodeWithText("OK").performClick()
        waitForText("Undo Majority pull violation on Team 1")
    }

    /**
     * Test timeout actions available from the live screen.
     */
    @Test
    fun timeouts() {
        setAutomaticallyAdvanceCountdowns(true)
        setAutomaticallyLockLivePoint(true)

        // Timeout during a live point starts a timeout countdown.
        // Note -- the clock starts when the timeout button is pressed, not when the dialog
        // is confirmed.
        startLivePointProgrammatically()
        val timeoutRequestedAt = System.currentTimeMillis()
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "timeout")).performClick()
        waitForText("Timeout charged to Team 1.", substring = true)
        Thread.sleep(500)
        val beforeConfirmingTimeout = System.currentTimeMillis()
        composeRule.onNodeWithText("OK").performClick()
        waitForText("Continue point")
        val timeoutCountdown = accessCurrentGameState().countdown!!
        assertEquals(CountdownKind.TIME_OUT, timeoutCountdown.kind)
        assertTrue(timeoutCountdown.targetEpoch >= timeoutRequestedAt + 69_000L)
        assertTrue(timeoutCountdown.targetEpoch < beforeConfirmingTimeout + 69_750L)

        // Manually continuing after a timeout clears the countdown and returns to locked live play.
        composeRule.onNodeWithText("Continue point").performClick()
        waitForText("Slide right to unlock")
        val manuallyContinuedState = accessCurrentGameState()
        assertEquals(GamePhase.LIVE_POINT, manuallyContinuedState.phase)
        assertNull(manuallyContinuedState.countdown)

        // Undoing the timeout removes the timeout charge.
        unlockLiveScreen()
        assertEquals(1, manuallyContinuedState.teamOne.timeoutsUsedThisHalf)
        composeRule.onNodeWithText("Undo Timeout by Team 1").performClick()
        waitForTag("live-center-lock")
        val undoneTimeoutState = accessCurrentGameState()
        assertNull(undoneTimeoutState.countdown)
        assertEquals(0, undoneTimeoutState.teamOne.timeoutsUsedThisHalf)

        // Recording another timeout and letting it expire reaches the same locked live-play state
        // through the automatic countdown path.
        recordTimeout(TeamId.TEAM_ONE, "Undo Timeout by Team 1")
        waitForText("Continue point")
        expireActiveCountdownProgrammatically()
        waitForText("Slide right to unlock")
        val autoContinuedState = accessCurrentGameState()
        assertEquals(GamePhase.LIVE_POINT, autoContinuedState.phase)
        assertNull(autoContinuedState.countdown)
        assertEquals(1, autoContinuedState.teamOne.timeoutsUsedThisHalf)
        unlockLiveScreen()

        // Timeout should remain wired after the undo path, including undo when redo is also
        // available.
        startLivePointProgrammatically()
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "goal")).performClick()
        waitForText("Undo Goal by Team 1")
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "timeout")).performClick()
        waitForText("Timeout charged to Team 1.", substring = true)
        dismissDialog(text = "Cancel")
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "timeout")).assertIsDisplayed()
        recordTimeout(TeamId.TEAM_ONE, "Undo Timeout by Team 1")
        composeRule.onNodeWithText("Undo Timeout by Team 1").performClick()
        waitForText("Undo Goal by Team 1")
        waitForText("Redo")
        composeRule.onNodeWithText("Undo Goal by Team 1").performClick()
        waitForTag("live-center-lock")
        waitForText("Redo")

        // The timeout button shows the invalid-timeout dialog when the team has none left.
        startLivePointProgrammatically(
            newSetupGameState(now = System.currentTimeMillis()).copy(
                rules = GameRules(timeoutsPerHalf = 1),
            )
        )
        updateCurrentStateProgrammatically {
            copy(teamOne = teamOne.copy(timeoutsUsedThisHalf = 1))
        }
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "timeout")).performClick()
        waitForText("Team 1 is out of timeouts.", substring = true)
        composeRule.onNodeWithText("OK").performClick()
    }

    /**
     * Test the ability to undo a goal, including one that prompts halftime.
     */
    @Test
    fun undoGoal() {
        // A between-points goal implicitly starts the point and exposes a useful undo.
        startLiveGameProgrammatically()
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "goal")).performClick()
        waitForText("Undo Goal by Team 1")
        composeRule.onNodeWithText("Undo Goal by Team 1").performClick()
        waitForTag("live-center-lock")
        waitForText("Redo")
        composeRule.onNodeWithText("Redo").performClick()
        waitForText("Undo Goal by Team 1")

        // Scoring into the halftime target shows the halftime prompt; undo removes it.
        startBetweenPointsProgrammatically(
            newSetupGameState(now = System.currentTimeMillis()).copy(
                rules = GameRules(
                    gameTo = 5,
                    useHalfCap = false,
                    useSoftCap = false,
                    useHardCap = false,
                ),
            )
        )
        updateCurrentStateProgrammatically {
            copy(teamOne = teamOne.copy(score = 2))
        }
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "goal")).performClick()
        waitForText("Halftime")
        composeRule.onNodeWithText("OK").performClick()
        composeRule.onNodeWithText("Undo Goal by Team 1").performClick()
        composeRule.onAllNodesWithText("Announce halftime.").assertCountEquals(0)
    }

    /**
     * Verify due timing-cue callbacks run for vibration, sound, and disabled configurations.
     */
    @Test
    fun timingCueCallbacks() {
        // Start a live game so the activity has a live countdown to inspect for timing cues.
        startLiveGameProgrammatically()

        // Exercise the activity's timing-cue callback for due vibration, sound, and disabled
        // cues. This checks that the callback runs without disrupting the countdown UI; it does
        // not assert that device sound or haptic hardware physically played.
        // A cue that is already due should be handled immediately in vibration-only mode.
        triggerDueTimeoutTwentyCue(
            globalMode = TimingAlertGlobalMode.VIBRATION_ONLY,
            cueMode = TimingAlertMode.VIBRATE,
            vibrateWithSounds = false,
            cueAlreadyDue = true,
            waitAfterDueMillis = 1_500L,
        )

        // A sound cue that is not due yet should be picked up by the activity's delayed check.
        triggerDueTimeoutTwentyCue(
            globalMode = TimingAlertGlobalMode.SOUNDS_ON,
            cueMode = TimingAlertMode.TICK,
            vibrateWithSounds = true,
            cueDueInMillis = 650L,
            waitAfterDueMillis = 700L,
        )

        // A sound cue with no extra vibration should still be accepted by the callback path.
        triggerDueTimeoutTwentyCue(
            globalMode = TimingAlertGlobalMode.SOUNDS_ON,
            cueMode = TimingAlertMode.BEEP,
            vibrateWithSounds = false,
        )

        // Disabled alerts should be a no-op even when the countdown reaches a cue time.
        triggerDueTimeoutTwentyCue(
            globalMode = TimingAlertGlobalMode.OFF,
            cueMode = TimingAlertMode.NONE,
            vibrateWithSounds = false,
        )
    }

    /**
     * Verify cap prompts can be resolved while the expired-pull controls are visible.
     */
    @Test
    fun capPromptsDuringExpiredPull() {
        startLiveGameProgrammatically()
        showExpiredPullSurface()

        // Half cap stays modal over the expired-pull surface until the observer answers it.
        showPendingCapOfferProgrammatically(CapType.HALF)
        waitForText("Apply half cap?")
        if (shouldUsePlatformBackDismissalCoverage()) {
            pressDialogBack()
            waitForText("Apply half cap?")
        }
        composeRule.onNodeWithText("No").performClick()

        // Soft cap can be applied from the same expired-pull surface.
        showPendingCapOfferProgrammatically(CapType.SOFT)
        waitForText("Apply soft cap?")
        composeRule.onNodeWithText("Apply").performClick()
        waitForText("Undo Apply soft cap")

        // Hard cap uses the same prompt surface but has distinct application logic.
        showPendingCapOfferProgrammatically(CapType.HARD)
        waitForText("Apply hard cap?")
        composeRule.onNodeWithText("Apply").performClick()
        waitForText("Undo Apply hard cap")
    }

    /**
     * Verify defense-check shortcuts for special live and between-points countdowns.
     */
    @Test
    fun defenseCheckCountdown() {
        startLiveGameProgrammatically()

        // A misconduct penalty between points skips the pull and has a special countdown.
        setShowDefenseCountdownsProgrammatically(true)
        startBetweenPointsMisconductCountdownProgrammatically(secondsRemaining = 90)

        // When the defense countdowns are enabled, it shows a prompt for when the offense is
        // set and then a countdown for the defensive check.
        waitForText("Offense is set")
        composeRule.onNodeWithTag("live-offense-set").performClick()
        waitForText("Defense check in", substring = true)

        // A live-point timeout countdown exposes the same pattern when defense countdowns are
        // enabled.
        setShowDefenseCountdownsProgrammatically(true)
        startTimeoutCountdownProgrammatically()
        waitForText("Offense is set")
        composeRule.onNodeWithTag("live-offense-set").performClick()
        waitForText("Defense check in", substring = true)
        setShowDefenseCountdownsProgrammatically(false)
    }

    /**
     * When the pull countdown expires, you can restart it or issue a time violation.
     */
    @Test
    fun expiredPull() {
        startLiveGameProgrammatically()
        showExpiredPullSurface()

        // Locking the screen hides expired-pull correction actions until the observer unlocks it.
        composeRule.onNodeWithTag("live-center-lock").performClick()
        waitForText("Slide right to unlock")
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "time-violation"))
            .assertIsNotEnabled()
        composeRule.onAllNodesWithTag("live-restart-pull-countdown").assertCountEquals(0)
        unlockLiveScreen()
        waitForText("Restart countdown")

        // Restart countdown is undoable and returns to the expired-pull action surface.
        composeRule.onNodeWithText("Restart countdown").performClick()
        waitForText("Undo Restart countdown")
        composeRule.onNodeWithText("Undo Restart countdown").performClick()
        waitForText("Redo")
        waitForText("Restart countdown")

        // Recording a pulling-team time violation doesn't start the shortened pull clock
        // countdown until the user confirms it, since there is probably stuff to talk
        // about with the teams before starting the countdown.
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "time-violation")).performClick()
        waitForText("now has 30 seconds to pull.", substring = true)
        dismissDialog(text = "Cancel")
        assertTrue(
            composeRule.onAllNodesWithText(
                "now has 30 seconds to pull.",
                substring = true,
            ).fetchSemanticsNodes().isEmpty()
        )
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "time-violation"))
            .performClick()
        waitForText("now has 30 seconds to pull.", substring = true)
        val beforeConfirmingTimeViolation = System.currentTimeMillis()
        composeRule.onNodeWithText("OK").performClick()
        waitForText("Undo Time violation warning on", substring = true)
        val timeViolationCountdown = accessCurrentGameState().countdown!!
        assertTrue(timeViolationCountdown.targetEpoch >= beforeConfirmingTimeViolation + 29_000L)
        assertTrue(timeViolationCountdown.targetEpoch <= System.currentTimeMillis() + 31_000L)

        // Reset the expired-pull surface so the receiving-team warning can be previewed and
        // canceled independently.
        showExpiredPullSurface()
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_TWO, "time-violation")).performClick()
        waitForText("now has 20 seconds to signal readiness.", substring = true)
        dismissDialog(text = "Cancel")
        assertTrue(
            composeRule.onAllNodesWithText(
                "now has 20 seconds to signal readiness.",
                substring = true,
            ).fetchSemanticsNodes().isEmpty()
        )
    }

    /**
     * Verify automatic countdown expiration enters live play and locks the screen when settings
     * allow it.
     */
    @Test
    fun automaticLockScreen() {
        // Enable automatic advancement and lock, then expire the opening pull countdown.
        startLiveGameProgrammatically()
        setAutomaticallyAdvanceCountdowns(true)
        setAutomaticallyLockLivePoint(true)

        // After pull countdown expires, the point starts and the screen locks.
        expireActiveCountdownProgrammatically()
        waitForText("Slide right to unlock")
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "goal")).assertIsNotEnabled()
        unlockLiveScreen()
        composeRule.onNodeWithText("Undo Start point").performClick()
        waitForTag("live-center-lock")
        composeRule.onAllNodesWithText("Slide right to unlock").assertCountEquals(0)
        composeRule.onNodeWithText("Redo").performClick()

        // For a time out during a point, the countdown expiring automatically continues
        // the point and locks the screen.
        startTimeoutCountdownProgrammatically(secondsRemaining = -1)
        waitForText("Slide right to unlock")
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_TWO, "timeout")).assertIsNotEnabled()
        unlockLiveScreen()

        // Halftime expiration automatically starts the next pull countdown, but that transition
        // does not make play live yet, so it should leave the screen unlocked.
        startBetweenPointsProgrammatically()
        setAutomaticallyAdvanceCountdowns(true)
        setAutomaticallyLockLivePoint(true)
        updateCurrentStateProgrammatically {
            startHalftimeNow(System.currentTimeMillis())
        }
        waitForText("Halftime")
        composeRule.onNodeWithText("OK").performClick()
        expireActiveCountdownProgrammatically()
        composeRule.waitUntil(timeoutMillis = 2_000) {
            accessCurrentGameState().phase == GamePhase.BETWEEN_POINTS
        }
        assertEquals(GamePhase.BETWEEN_POINTS, accessCurrentGameState().phase)
        waitForText("Start point")
        composeRule.onAllNodesWithText("Slide right to unlock").assertCountEquals(0)

        // If auto-advance is re-enabled before undoing a manual start from an expired countdown,
        // undo should keep the redo path intact rather than immediately auto-advancing again.
        startLiveGameProgrammatically()
        setAutomaticallyAdvanceCountdowns(false)
        setAutomaticallyLockLivePoint(true)
        expireActiveCountdownProgrammatically()
        waitForText("Start point")
        composeRule.onNodeWithText("Start point").performClick()
        waitForText("Slide right to unlock")
        unlockLiveScreen()
        setAutomaticallyAdvanceCountdowns(true)
        composeRule.onNodeWithText("Undo Start point").performClick()
        waitForText("Start point")
        waitForText("Redo")
        waitForTag("live-center-lock")
        composeRule.onAllNodesWithText("Slide right to unlock").assertCountEquals(0)
    }

    /**
     * Verify disabling automatic countdown advancement leaves expired countdowns on the
     * observer-facing controls.
     */
    @Test
    fun disableAutomaticTransitions() {
        // Start with a visible countdown before disabling automatic expiration.
        startLiveGameProgrammatically()
        val checkAfter = System.currentTimeMillis() + 1_200L

        // The visible countdown can be adjusted in either direction from the live controls.
        val initialCountdownTarget = accessCurrentGameState().countdown!!.targetEpoch
        composeRule.onNodeWithText("+5").performClick()
        assertEquals(
            initialCountdownTarget + 5_000L,
            accessCurrentGameState().countdown!!.targetEpoch,
        )
        composeRule.onNodeWithText("-5").performClick()
        assertEquals(initialCountdownTarget, accessCurrentGameState().countdown!!.targetEpoch)

        // The visible countdown can be paused and resumed before exercising expiration behavior.
        composeRule.onNodeWithTag("live-pause-countdown").performClick()
        waitForText("Paused")
        composeRule.onNodeWithTag("live-resume-countdown").assertIsDisplayed()
        assertTrue(accessCurrentGameState().countdown!!.isPaused())
        composeRule.onNodeWithTag("live-resume-countdown").performClick()
        composeRule.onAllNodesWithText("Paused").assertCountEquals(0)
        composeRule.onNodeWithTag("live-pause-countdown").assertIsDisplayed()
        assertTrue(!accessCurrentGameState().countdown!!.isPaused())

        // If the auto-advance setting is false, then when the opening countdown expires, it
        // doesn't automatically start the point.
        setAutomaticallyAdvanceCountdowns(false)
        expireActiveCountdownProgrammatically()
        composeRule.waitUntil(timeoutMillis = 2_000) {
            System.currentTimeMillis() >= checkAfter
        }
        assertEquals(GamePhase.PRE_GAME, accessCurrentGameState().phase)
        waitForText("Start point")
        composeRule.onAllNodesWithText("Slide right to unlock").assertCountEquals(0)

        // Locking while an in-point countdown is visible disables the countdown controls.
        startTimeoutCountdownProgrammatically()
        val lockedCountdownTarget = accessCurrentGameState().countdown!!.targetEpoch
        composeRule.onNodeWithTag("live-center-lock").performClick()
        waitForText("Slide right to unlock")
        composeRule.onNodeWithTag("live-pause-countdown").assertIsNotEnabled()
        composeRule.onNodeWithText("+5").assertIsNotEnabled()
        composeRule.onNodeWithText("-5").assertIsNotEnabled()
        assertEquals(lockedCountdownTarget, accessCurrentGameState().countdown!!.targetEpoch)
    }

    /**
     * Verify disabling automatic live-point locking leaves the point live but unlocked after timer
     * expiration.
     */
    @Test
    fun disableAutomaticLock() {
        setAutomaticallyAdvanceCountdowns(true)
        setAutomaticallyLockLivePoint(true)
        startLiveGameProgrammatically()

        // Disable live-point locking through Settings, then return to the current game.
        tapTopBarHome()
        waitForText("Current game")
        composeRule.onNodeWithText("Settings").performClick()
        waitForText("Automatically lock screen when play becomes live?")
        composeRule.onNodeWithTag("settings-auto-advance-countdowns-value").assertTextEquals("Yes")
        composeRule.onNodeWithTag("settings-auto-lock-live-point-value").assertTextEquals("Yes")
        composeRule.onNodeWithTag("settings-auto-lock-live-point").performClick()
        composeRule.onNodeWithTag("settings-auto-lock-live-point-value").assertTextEquals("No")
        tapTopBarBack()
        waitForText("Current game")
        composeRule.onNodeWithTag("current-game").performClick()
        assertLiveScreen()

        // Manual start-point and timeout paths stay unlocked when live-point locking is disabled.
        composeRule.onNodeWithText("Start point").performClick()
        waitForTag("live-center-lock")
        composeRule.onAllNodesWithText("Slide right to unlock").assertCountEquals(0)
        recordTimeout(TeamId.TEAM_ONE, "Undo Timeout by Team 1")
        composeRule.onNodeWithText("Continue point").performClick()
        waitForTag("live-center-lock")
        composeRule.onAllNodesWithText("Slide right to unlock").assertCountEquals(0)

        // Automatic countdown expiration should also leave the point unlocked when locking is
        // disabled.
        startLiveGameProgrammatically()
        expireActiveCountdownProgrammatically()
        composeRule.waitUntil(timeoutMillis = 2_000) {
            accessCurrentGameState().phase == GamePhase.LIVE_POINT
        }
        waitForText("Undo Start point")
        composeRule.onAllNodesWithText("Slide right to unlock").assertCountEquals(0)
        composeRule.onNodeWithTag("live-center-lock").assertIsDisplayed()
    }

    /// Put the current game on the between-points expired-pull action surface.
    private fun showExpiredPullSurface() {
        updateCurrentStateProgrammatically {
            copy(
                phase = GamePhase.BETWEEN_POINTS,
                countdown = null,
            )
        }
        waitForText("Restart countdown")
    }

    /// Put a cap prompt over the current live-game surface without reaching it through the clock.
    private fun showPendingCapOfferProgrammatically(capType: CapType) {
        updateCurrentStateProgrammatically {
            copy(pendingCapOffer = capType)
        }
    }

    /// Start the special between-points misconduct countdown.
    private fun startBetweenPointsMisconductCountdownProgrammatically(secondsRemaining: Int) {
        startPullStyleCountdownProgrammatically(
            phase = GamePhase.BETWEEN_POINTS,
            kind = CountdownKind.MISCONDUCT_BETWEEN_POINTS,
            durationSeconds = 100,
            secondsRemaining = secondsRemaining,
        )
    }

    /// Turn on showDefenseCountdowns setting.
    private fun setShowDefenseCountdownsProgrammatically(show: Boolean) {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.appViewModel.updateShowDefenseCountdowns(show)
        }
        composeRule.waitForIdle()
    }

    /**
     * Start a pull-style "Offense set in" countdown in the requested game phase.
     *
     * This lets UI tests exercise countdown surfaces that look like pull timing without going
     * through the game actions that normally create those countdowns.
     */
    private fun startPullStyleCountdownProgrammatically(
        phase: GamePhase,
        kind: CountdownKind,
        durationSeconds: Int,
        secondsRemaining: Int,
    ) {
        updateCurrentStateProgrammatically {
            copy(
                phase = phase,
                countdown = CountdownState(
                    kind = kind,
                    label = "Offense set in",
                    durationSeconds = durationSeconds,
                    targetEpoch = System.currentTimeMillis() + secondsRemaining * 1000L,
                ),
            )
        }
    }

    /**
     * Drive a timeout twenty-second cue through the activity with a specific alert configuration.
     *
     * @param globalMode The global timing alert mode to apply before the cue fires.
     * @param cueMode The per-cue alert mode for the timeout twenty-second cue.
     * @param vibrateWithSounds Whether sound cues should also vibrate.
     * @param cueAlreadyDue Whether the cue should already be due when the listener sees the
     * countdown.
     * @param cueDueInMillis How soon a scheduled cue should fire when it is not already due.
     * @param waitAfterDueMillis How long to wait after the cue's due time so asynchronous
     * delivery can run.
     */
    private fun triggerDueTimeoutTwentyCue(
        globalMode: TimingAlertGlobalMode,
        cueMode: TimingAlertMode,
        vibrateWithSounds: Boolean,
        cueAlreadyDue: Boolean = false,
        cueDueInMillis: Long = 500L,
        waitAfterDueMillis: Long = 300L,
    ) {
        val defaultPreferences = TimingAlertPreferences()
        setTimingAlertPreferences(
            defaultPreferences.copy(
                globalMode = globalMode,
                vibrateWithSounds = vibrateWithSounds,
                cueModes = defaultPreferences.cueModes + mapOf(
                    TimingCueId.OFFENSE_TWENTY to cueMode,
                ),
            )
        )

        val now = System.currentTimeMillis()
        val dueEpoch = if (cueAlreadyDue) now - 500L else now + cueDueInMillis
        updateCurrentStateProgrammatically {
            copy(
                phase = GamePhase.LIVE_POINT,
                countdown = CountdownState(
                    kind = CountdownKind.TIME_OUT,
                    label = "Offense set in",
                    durationSeconds = 70,
                    targetEpoch = dueEpoch + 20_000L,
                ),
            )
        }
        waitForText("Offense set in", substring = true)
        composeRule.waitUntil(timeoutMillis = 3_000) {
            System.currentTimeMillis() >= dueEpoch + waitAfterDueMillis
        }
        composeRule.waitForIdle()
    }

}
