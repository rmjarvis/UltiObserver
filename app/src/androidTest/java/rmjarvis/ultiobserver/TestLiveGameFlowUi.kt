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

@RunWith(AndroidJUnit4::class)
class TestLiveGameFlowUi : MainActivityUiTestFixtures() {
    // Test a representative complete game from setup through halftime to final score.
    // Keep this as a user-visible UI story that checks flow, not detailed model accounting.
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
        setIntegerSetupValue("Game to", "Game To", "Points", "5")
        setIntegerSetupValue("Halftime", "Halftime", "Minutes", "1")
        setCapRuleToNone("Half cap", "Half Cap")
        setCapRuleToNone("Soft cap", "Soft Cap")
        setCapRuleToNone("Hard cap", "Hard Cap")
        openStartingPullSetupEditor()
        composeRule.onNodeWithText("Near end").performClick()
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
        recordYellowCard(TeamId.TEAM_ONE, "17", "Yellow card on player 17.\n$viscousCoupling has 1 card.")
        recordBlueCard(TeamId.TEAM_ONE, "$viscousCoupling has 2 cards.")
        recordYellowCard(
            team = TeamId.TEAM_ONE,
            playerNumber = "8",
            expectedMessage = "$viscousCoupling has 3 cards.",
            misconductChoice = "Offense",
            expectedMisconductMessage = "Reverse brick",
        )
        waitForText("Start Misconduct Countdown")
        composeRule.onNodeWithTag("live-start-misconduct-countdown").performClick()
        waitForText("Offense set in", substring = true)
        continuePointAndUnlock()
        recordYellowCard(
            team = TeamId.TEAM_ONE,
            playerNumber = "9",
            expectedMessage = "$viscousCoupling has 4 cards.",
            misconductChoice = "Defense",
            expectedMisconductMessage = "Brick nearest attacking end zone",
        )

        // Viscous Coupling scores the first point, then Animal false-starts and that entry is undone.
        recordGoal(TeamId.TEAM_ONE, "Undo Goal by $viscousCoupling")
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_TWO, "pull-infraction")).performClick()
        waitForText("Defense gets to set up.")
        composeRule.onNodeWithText("OK").performClick()
        composeRule.onNodeWithText("Undo False Start on $animal").performClick()

        // Viscous Coupling then records an offsides; the duplicate offsides button is disabled.
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "pull-infraction")).performClick()
        waitForText("Start at brick mark")
        composeRule.onNodeWithText("OK").performClick()
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "pull-infraction")).assertIsNotEnabled()

        // Animal picks up two yellows and two technical fouls during the live point.
        recordYellowCard(TeamId.TEAM_TWO, "23", "Yellow card on player 23.\n$animal has 1 card.")
        recordYellowCard(TeamId.TEAM_TWO, "8", "Yellow card on player 8.\n$animal has 2 cards.")
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

        // Touch the visible correction controls, then jump the countdown to its expired state.
        composeRule.onAllNodesWithText("+5").onFirst().performClick()
        composeRule.onAllNodesWithText("-5").onFirst().performClick()
        setActiveCountdownRemainingProgrammatically(secondsRemaining = -1)
        waitForText("Start Point")

        // After halftime, Animal scores and uses one second-half timeout before the next pull.
        startPointAndUnlock()
        recordGoal(TeamId.TEAM_TWO, "Undo Goal by $animal")
        recordTimeout(TeamId.TEAM_TWO, "Undo Timeout by $animal")

        // Animal ties the game, Viscous Coupling goes ahead, and Animal wins on universe.
        recordGoal(TeamId.TEAM_TWO, "Undo Goal by $animal")
        recordGoal(TeamId.TEAM_ONE, "Undo Goal by $viscousCoupling")
        recordGoal(TeamId.TEAM_TWO, "Undo Goal by $animal")
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_TWO, "goal")).performClick()
        waitForText("Game Over")
        composeRule.onNodeWithText("OK").performClick()
        composeRule.onNodeWithText("Game Summary").assertIsDisplayed()
        composeRule.onNodeWithText("$viscousCoupling 4").assertIsDisplayed()
        composeRule.onNodeWithText("$animal 5").assertIsDisplayed()
        composeRule.onNodeWithText(viscousCoupling).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(animal).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Undo End Game").performClick()
        assertLiveScreen()

        // Manually ending from the restored final state should return to the same summary.
        openOtherSheet()
        composeRule.onNodeWithText("End Game").performClick()
        waitForText("Game Over")
        composeRule.onNodeWithText("OK").performClick()
        composeRule.onNodeWithText("Game Summary").assertIsDisplayed()
        composeRule.onNodeWithText("$viscousCoupling 4").assertIsDisplayed()
        composeRule.onNodeWithText("$animal 5").assertIsDisplayed()

        // The finished game should go home from the visible Back action, archive, and then reopen from Previous Games.
        composeRule.onNodeWithText("Back").performClick()
        waitForText("Completed Game")
        composeRule.onNodeWithText("$viscousCoupling 4 - 5 $animal").performClick()
        waitForText("Game Summary")
        assertNoGameOverDialog()
        composeRule.onNodeWithText("Back").performClick()
        waitForText("Completed Game")
        composeRule.onNodeWithText("Archive Completed Game").performClick()
        waitForText("Previous Games")
        composeRule.onNodeWithText("Previous Games").performClick()
        waitForText("$viscousCoupling 4 - 5 $animal")
        composeRule.onNodeWithTag("archived-game-$viscousCoupling 4 - 5 $animal").performClick()
        waitForText("Game Summary")
        assertNoGameOverDialog()
        composeRule.onNodeWithText("$animal 5").assertIsDisplayed()
        composeRule.onNodeWithText("Back").performClick()
        waitForText("Start New Game")
    }

    private fun assertNoGameOverDialog() {
        assertTrue(composeRule.onAllNodesWithText("Game Over").fetchSemanticsNodes().isEmpty())
    }

    // Test the primary live screen actions that should be available directly from the phone.
    // Keep the assertions at the visible undo/message level; GameModel owns detailed state checks.
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

        // Timeout should remain wired after the undo path.
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "timeout")).performClick()
        waitForText("Undo Timeout by Team 1")
    }

    @Test
    fun expiredPullActionsAndTimingAlertsAreWired() {
        startLiveGameProgrammatically()

        triggerDueTimeoutTwentyCue(
            globalMode = TimingAlertGlobalMode.VIBRATION_ONLY,
            cueMode = TimingAlertMode.VIBRATE,
            vibrateWithSounds = false,
            waitAfterDueMillis = 1_500L,
        )
        triggerDueTimeoutTwentyCue(
            globalMode = TimingAlertGlobalMode.SOUNDS_ON,
            cueMode = TimingAlertMode.TICK,
            vibrateWithSounds = true,
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

        composeRule.activityRule.scenario.onActivity { activity ->
            val current = activity.appViewModel.liveState!!
            activity.appViewModel.updateLiveGame(
                current.copy(
                    phase = LivePhase.BETWEEN_POINTS,
                    countdown = null,
                    pullCountdownExpired = true,
                )
            )
        }
        waitForText("Time Violation")
        waitForText("Restart Countdown")
        composeRule.onNodeWithTag("live-top-lock").performClick()
        waitForText("Slide right to unlock")
        composeRule.onAllNodesWithTag("live-time-violation").assertCountEquals(0)
        composeRule.onAllNodesWithTag("live-restart-pull-countdown").assertCountEquals(0)
        unlockLiveScreen()
        waitForText("Time Violation")
        waitForText("Restart Countdown")

        composeRule.onNodeWithText("Restart Countdown").performClick()
        waitForText("Undo Restart Pull Countdown")
        composeRule.onNodeWithText("Undo Restart Pull Countdown").performClick()
        waitForText("Redo")
        waitForText("Time Violation")

        composeRule.onNodeWithText("Time Violation").performClick()
        waitForText("Which team committed the time violation?")
        composeRule.onAllNodesWithText("Team 2").onLast().performClick()
        waitForText("now has 30 seconds", substring = true)
        composeRule.onNodeWithText("OK").performClick()
    }

    @Test
    fun fieldDiagramDoesNotMoveWhenCountdownClears() {
        startLiveGameProgrammatically()

        val fieldTopBeforeStartPoint = composeRule.onNodeWithTag("live-field-diagram")
            .fetchSemanticsNode()
            .boundsInRoot
            .top
        composeRule.onNodeWithText("Start Point").performClick()
        waitForText("Slide right to unlock")
        val fieldTopAfterStartPoint = composeRule.onNodeWithTag("live-field-diagram")
            .fetchSemanticsNode()
            .boundsInRoot
            .top
        assertEquals(fieldTopBeforeStartPoint, fieldTopAfterStartPoint, 0.5f)
    }

    @Test
    fun automaticCountdownTransitionsLockScreen() {
        startLiveGameProgrammatically()

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
    }

    private fun triggerDueTimeoutTwentyCue(
        globalMode: TimingAlertGlobalMode,
        cueMode: TimingAlertMode,
        vibrateWithSounds: Boolean,
        waitAfterDueMillis: Long = 300L,
    ) {
        var dueEpoch = 0L
        composeRule.activityRule.scenario.onActivity { activity ->
            val now = System.currentTimeMillis()
            dueEpoch = now + 500L
            activity.appViewModel.updateTimingAlertGlobalMode(globalMode)
            activity.appViewModel.updateTimingAlertVibrateWithSounds(vibrateWithSounds)
            activity.appViewModel.updateTimingCueMode(TimingCueId.TIMEOUT_OFFENSE_TWENTY, cueMode)
            val current = activity.appViewModel.liveState!!
            activity.appViewModel.updateLiveGame(
                current.copy(
                    phase = LivePhase.LIVE_POINT,
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
