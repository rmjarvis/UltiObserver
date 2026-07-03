package rmjarvis.ultiobserver

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
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
        // Clear the archive so tests related to it don't get confused by previous runs.
        clearArchivedGamesProgrammatically()
        setAutomaticallyAdvanceCountdowns(true)
        setAutomaticallyLockLivePoint(true)
        val viscousCoupling = "Viscous Coupling"
        val animal = "Animal"

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

        // The opening pull starts the first live point; a short swipe should fail before a
        // full unlock.
        startPointWithFailedSwipeThenUnlock()

        // The field-strip Lock action should relock the same live layout.
        composeRule.onNodeWithTag("live-center-lock").performClick()
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

        // Viscous Coupling gets two early card points, then a third card that needs a
        // misconduct choice. Back from that choice should return to the player-number dialog
        // with the entered number intact.
        recordYellowCard(
            TeamId.TEAM_ONE,
            "17",
            "Yellow card on player 17.\n$viscousCoupling has 1 card total.",
        )
        recordBlueCard(
            TeamId.TEAM_ONE,
            "Blue card on $viscousCoupling.\n$viscousCoupling has 2 cards total.",
        )
        recordYellowCard(
            team = TeamId.TEAM_ONE,
            playerNumber = "8",
            expectedMessage = "$viscousCoupling has 3 cards total.",
            misconductChoice = "Offense",
            expectedMisconductMessage = "$viscousCoupling moves the disc to the reverse " +
                "brick in the end zone they are defending.",
            verifyMisconductBackReturnsToNumberDialog = true,
        )
        waitForText("Start misconduct countdown")
        composeRule.onNodeWithTag("live-center-lock").performClick()
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
            expectedMessage = "$viscousCoupling has 4 cards total.",
            misconductChoice = "Defense",
            expectedMisconductMessage = "$animal may move the disc to the brick mark nearest " +
                "the end zone they are attacking.",
        )
        recordRedCard(
            team = TeamId.TEAM_ONE,
            playerNumber = "12",
            expectedMessage = "$viscousCoupling has 6 cards total (red cards count as 2).",
            misconductChoice = "Defense",
            expectedMisconductMessage = "$animal may move the disc to the brick mark nearest " +
                "the end zone they are attacking.",
            verifyMisconductBackReturnsToNumberDialog = true,
        )

        // Viscous Coupling scores the first point, then Animal false-starts and that entry is
        // undone.
        recordGoal(TeamId.TEAM_ONE, "Undo Goal by $viscousCoupling")
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_TWO, "pull-violation")).performClick()
        waitForText("$viscousCoupling gets to set up on defense.", substring = true)
        composeRule.onNodeWithText("OK").performClick()
        composeRule.onNodeWithText("Undo False start on $animal").performClick()

        // Viscous Coupling then records an offsides; the duplicate offsides button is disabled.
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "pull-violation")).performClick()
        waitForText("$animal starts at the brick mark.", substring = true)
        composeRule.onNodeWithText("OK").performClick()
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "pull-violation"))
            .assertIsNotEnabled()

        // Animal picks up two yellows and two technical fouls during the live point.
        recordYellowCard(
            TeamId.TEAM_TWO,
            "23",
            "Yellow card on player 23.\n$animal has 1 card total.",
        )
        recordYellowCard(
            TeamId.TEAM_TWO,
            "8",
            "Yellow card on player 8.\n$animal has 2 cards total.",
        )
        recordTechnicalFoul(TeamId.TEAM_TWO, "This is $animal's first technical foul.")
        recordTechnicalFoul(TeamId.TEAM_TWO, "This is $animal's second technical foul.")

        // Viscous Coupling calls a live-point timeout before Animal finishes the point.
        recordTimeout(TeamId.TEAM_ONE, "Undo Timeout by $viscousCoupling")
        continuePointAndUnlock()
        recordGoal(TeamId.TEAM_TWO, "Undo Goal by $animal")

        // Animal uses its final first-half timeout, then gets the out-of-timeouts cue.
        recordTimeout(TeamId.TEAM_TWO, "Undo Timeout by $animal")
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_TWO, "timeout")).performClick()
        waitForText("$animal is out of timeouts.")
        composeRule.onNodeWithText("OK").performClick()

        // Animal reaches the technical-foul threshold between points, so the UI shows the
        // yardage cue.
        recordTechnicalFoul(
            team = TeamId.TEAM_TWO,
            expectedMessage = "$viscousCoupling starts at attacking brick.",
            substring = true,
        )
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "pull-violation"))
            .assertIsNotEnabled()
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_TWO, "pull-violation"))
            .assertIsNotEnabled()

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
        expireActiveCountdownProgrammatically()
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
        openMoreActionsDialog()
        composeRule.onNodeWithText("End game").performClick()
        waitForText("Game over")
        composeRule.onNodeWithText("OK").performClick()
        composeRule.onNodeWithText("Game summary").assertIsDisplayed()
        composeRule.onNodeWithText("$viscousCoupling 4").assertIsDisplayed()
        composeRule.onNodeWithText("$animal 5").assertIsDisplayed()

        // The finished game should go home from the top-bar Back action, archive, and then
        // reopen from Archived games.
        tapTopBarBack()
        waitForText("Completed game")
        composeRule.onNodeWithText("$viscousCoupling 4 - 5 $animal").performClick()
        waitForText("Game summary")
        assertTrue(composeRule.onAllNodesWithText("Game over").fetchSemanticsNodes().isEmpty())
        tapTopBarBack()
        waitForText("Completed game")
        composeRule.onNodeWithText("Archive completed game").performClick()
        waitForText("See archived/saved games")
        composeRule.onNodeWithText("See archived/saved games").performClick()
        waitForText("Archived/saved games")
        composeRule.onNodeWithText("Archived games", substring = true).performClick()
        waitForText("$viscousCoupling 4 - 5 $animal")
        composeRule.onNodeWithTag("archived-game-0").performClick()
        waitForText("Game summary")
        assertTrue(composeRule.onAllNodesWithText("Game over").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithText("$animal 5").assertIsDisplayed()
        tapTopBarBack()
        waitForText("Archived games")
        tapTopBarBack()
        waitForText("Archived/saved games")
        tapTopBarBack()
        waitForText("Start new game")
    }

    /**
     * Test the primary live screen actions that should be available directly from the phone.
     * Keep the assertions at the visible undo/message level; domain helpers own detailed state
     * checks.
     */
    @Test
    fun livePrimaryActionsAndUndoPath() {
        // Start from an unlocked live point so primary field buttons are visible.
        startLiveGameProgrammatically()

        // Each side can record only one pull violation of its type for the current pull sequence.
        // For team 2 (receiving) the pull violation is a False start.
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

        // For team 1 (pulling) it is Offsides.
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "pull-violation")).performClick()
        waitForText("Team 2 starts at the brick mark.", substring = true)
        composeRule.onNodeWithText("OK").performClick()
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "pull-violation"))
            .assertIsNotEnabled()

        // Timeout during a live point starts a timeout countdown.
        // Note -- the clock starts when the timeout button is pressed, not when the dialog
        // is confirmed.
        val timeoutRequestedAt = System.currentTimeMillis()
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "timeout")).performClick()
        waitForText("Timeout charged to Team 1.", substring = true)
        Thread.sleep(1200)
        val beforeConfirmingTimeout = System.currentTimeMillis()
        composeRule.onNodeWithText("OK").performClick()
        waitForText("Continue point")
        val timeoutCountdown = accessCurrentGameState().countdown!!
        assertEquals(CountdownKind.TIME_OUT, timeoutCountdown.kind)
        assertTrue(timeoutCountdown.targetEpoch >= timeoutRequestedAt + 69_000L)
        assertTrue(timeoutCountdown.targetEpoch < beforeConfirmingTimeout + 69_500L)
        continuePointAndUnlock()

        // A between-points goal implicitly starts the point and exposes a useful undo.
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "goal")).performClick()
        waitForText("Undo Goal by Team 1")
        composeRule.onNodeWithText("Undo Goal by Team 1").performClick()
        waitForTag("live-center-lock")
        waitForText("Redo")
        composeRule.onNodeWithText("Redo").performClick()
        waitForText("Undo Goal by Team 1")

        // Timeout should remain wired after the undo path, including undo when redo is also
        // available.
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "timeout")).performClick()
        waitForText("Timeout charged to Team 1.", substring = true)
        dismissDialog(text = "Cancel")
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "timeout")).assertIsDisplayed()
        recordTimeout(TeamId.TEAM_ONE, "Undo Timeout by Team 1")
        composeRule.onNodeWithText("Undo Timeout by Team 1").performClick()
        waitForText("Undo Goal by Team 1")
        waitForText("Redo")
        composeRule.onNodeWithText("Undo Goal by Team 1").performClick()
        waitForText("Redo")
    }

    /**
     * Test switching the pulling team's pull-violation dialog to the mixed majority-pull rule.
     */
    @Test
    fun majorityPullViolationDialog() {
        // In mixed games, the pulling team's Offsides button can instead be recorded as a
        // majority-pull violation.
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
     * Verify expired-pull restart controls are hidden while locked and undoable after use.
     */
    @Test
    fun expiredPullRestartCountdown() {
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
    }

    /**
     * Verify expired-pull time-violation warnings preview and start the right countdowns.
     */
    @Test
    fun timeViolationCountdowns() {
        startLiveGameProgrammatically()
        showExpiredPullSurface()

        // A pulling-team time violation doesn't start the shortened pull clock countdown
        // until the user confirms it.
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
        Thread.sleep(1200)
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
