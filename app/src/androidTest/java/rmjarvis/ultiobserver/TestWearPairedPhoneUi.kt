package rmjarvis.ultiobserver

import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextReplacement
import java.io.File
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Exclude paired-watch partners from ordinary Gradle-driven phone UI runs. */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
internal annotation class RequiresPairedWear

/**
 * Phone-side partners for UI narratives driven from a paired Wear emulator.
 *
 * Note: This test is run in conjunction with the wear test with the same name.
 *       The phone test indicates that it is ready for the watch test to start by writing
 *       to the PAIRED_TEST_READY_FILE.
 *       See signalReady defined below.
 *       Once the Python test runner for the watch tests sees the signal, it starts the
 *       corresponding watch test.
 */
@RequiresPairedWear
class TestWearPairedPhoneUi : MainActivityUiTestFixtures() {
    /** Host the test of scoring and undoing goals from the watch. */
    @Test
    fun goalAndUndo() {
        setTimingAlertPreferences(
            TimingAlertPreferences(watchConnectionMode = WatchConnectionMode.WEAR_OS)
        )
        startLiveGameProgrammatically(
            newSetupGameState(now = System.currentTimeMillis()).copy(
                division = GameDivision.MIXED,
                pullPromptTarget = PullPromptTarget.BOTH,
                rules = GameRules().withHeatLevel(HeatLevel.MANUAL),
            )
        )
        useStandardTeamNames()
        updateCurrentStateProgrammatically {
            copy(
                teamOne = teamOne.copy(coaches = "Alex", fieldCaptains = "Pat\nSam", spiritCaptains = "Lee"),
                countdown = buildBetweenPointsCountdown(
                    pullingFromEnd = pullingFromEnd,
                    sequenceStart = System.currentTimeMillis(),
                    kind = CountdownKind.OPENING_PULL,
                    promptTarget = pullPromptTarget,
                    rules = rules,
                )
            )
        }
        signalReady("goalAndUndo")

        // The watch records a goal for Animal then VC, then undoes the VC goal.

        // Wait for the result of the watch's actions, and validate the state:
        // Animal 1, Viscous Coupling 0 with a Redo available to reapply the goal by VC.
        waitForGame { game ->
            game.teamOne.score == 1 && game.teamTwo.score == 0 && game.redoEntry != null
        }
        val restoredGame = composeRule.activity.appState.currentGame!!
        assertEquals(1, restoredGame.teamOne.score)
        assertEquals(0, restoredGame.teamTwo.score)
        assertNotNull(restoredGame.redoEntry)
        val tiedGame = restoredGame.redoEntry!!
        assertEquals(1, tiedGame.teamOne.score)
        assertEquals(1, tiedGame.teamTwo.score)
        assertEquals("Undo Goal by Viscous Coupling", tiedGame.undoEntry?.label)

        // Score for Animal using the phone UI. The watch uses the new 2-0 score as its cue
        // that the phone has finished, then undoes this phone-recorded goal.
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "goal")).performClick()

        // Clear this phone-recorded goal's countdown so the watch can restart it, then undo
        // the restart before undoing the goal. No watch goal request is pending during this edit.
        updateCurrentStateProgrammatically {
            copy(countdown = null)
        }

        // The watch undoes the phone's goal for Animal. Validate the final state.
        waitForGame { game ->
            game.teamOne.score == 1 && game.teamTwo.score == 0 && game.redoEntry != null
        }
        val finalGame = composeRule.activity.appState.currentGame!!
        assertEquals(1, finalGame.teamOne.score)
        assertEquals(0, finalGame.teamTwo.score)
        val phoneScoredGame = finalGame.redoEntry!!
        assertEquals(2, phoneScoredGame.teamOne.score)
        assertEquals(0, phoneScoredGame.teamTwo.score)
        assertEquals("Undo Goal by Animal", phoneScoredGame.undoEntry?.label)
    }

    /** Host the test of time violations and pull violations recorded by the watch. */
    @Test
    fun timeAndPullViolations() {
        setTimingAlertPreferences(
            TimingAlertPreferences(watchConnectionMode = WatchConnectionMode.WEAR_OS)
        )
        startLiveGameProgrammatically(
            newSetupGameState(now = System.currentTimeMillis()).copy(
                division = GameDivision.MIXED,
            )
        )
        useStandardTeamNames()
        val initialGame = composeRule.activity.appState.currentGame!!
        assertEquals(TeamId.TEAM_ONE, initialGame.pullingTeam)
        signalReady("timeAndPullViolations")

        // The watch records a majority pull violation on Animal,
        // a false start on VC, and finally a time violation on Animal.

        // The time violation is the last watch action in this narrative.
        // Validate the final game state.
        waitForGame { game -> game.teamOne.timeViolations == 1 }
        val finalGame = composeRule.activity.appState.currentGame!!
        assertEquals(1, finalGame.teamOne.majorityPullViolations)
        assertEquals(0, finalGame.teamOne.offsides)
        assertEquals(1, finalGame.teamTwo.falseStarts)
        assertEquals(1, finalGame.teamOne.timeViolations)
        assertEquals("Undo Time violation warning on Animal", finalGame.undoEntry?.label)
    }

    /** Host the test of timeouts, techs and blue cards recorded by the watch. */
    @Test
    fun timeoutAndMisconduct() {
        setTimingAlertPreferences(
            TimingAlertPreferences(
                watchConnectionMode = WatchConnectionMode.WEAR_OS,
                vibrationDurationMillis = 420L,
                cueModes = TimingCueId.entries.associateWith {
                    if (it == TimingCueId.OFFENSE_TWENTY) TimingAlertMode.VIBRATE else TimingAlertMode.NONE
                },
                cueRepeatCounts = mapOf(TimingCueId.OFFENSE_TWENTY to 1),
            )
        )
        composeRule.runOnIdle {
            val appState = composeRule.activity.appState
            appState.updateSettings(appState.settings.copy(showDefenseCountdowns = true))
        }
        startLivePointProgrammatically()
        useStandardTeamNames()
        signalReady("timeoutAndMisconduct")

        // The watch records a timeout for VC, a tech on Animal, and a blue card on VC.

        // The watch continues through VC's third blue card and starts the misconduct countdown.
        waitForGame { game -> game.teamTwo.blueCards == 3 && game.countdown != null }
        val finalGame = composeRule.activity.appState.currentGame!!
        assertEquals(1, finalGame.teamTwo.timeoutsUsedThisHalf)
        assertEquals(1, finalGame.teamOne.technicalFouls)
        assertEquals(3, finalGame.teamTwo.blueCards)
        assertEquals(CountdownKind.TIME_OUT, finalGame.countdown!!.kind)
        assertEquals(30, finalGame.countdown.durationSeconds)
        assertTrue(!finalGame.pendingMisconductCountdown)
        assertEquals("Undo Blue card on Viscous Coupling", finalGame.undoEntry?.label)

        // Prime the countdown so the 20-second cue arrives in about one second.
        updateCurrentStateProgrammatically {
            copy(countdown = countdown!!.copy(targetEpoch = System.currentTimeMillis() + 22_000L))
        }

        // Keep the game running while the watch waits for the cue, then resumes play.
        waitForGame { game -> game.countdown == null }
    }

    /** Host the test of halftime, letting the halftime confirmation be done on the watch. */
    @Test
    fun halftimeConfirmation() {
        setTimingAlertPreferences(
            TimingAlertPreferences(watchConnectionMode = WatchConnectionMode.WEAR_OS)
        )
        startLiveGameWithDueCap("Half cap", "Half cap")
        useStandardTeamNames()
        signalReady("halftimeConfirmation")

        // The watch defers the half cap after Animal scores, applies it after VC scores, then
        // defers halftime after Animal scores. VC scores the next goal before confirming halftime
        // on the watch.

        // The phase is now halftime with a score of 2-2 and the half cap applied.
        waitForGame { game -> game.phase == GamePhase.HALFTIME }
        val halftime = composeRule.activity.appState.currentGame!!
        assertEquals(2, halftime.teamOne.score)
        assertEquals(2, halftime.teamTwo.score)
        assertEquals(2, halftime.halftimeTargetScore)
        assertTrue(halftime.halfCapApplied)
        assertEquals(GamePhase.HALFTIME, halftime.phase)
    }

    /** Host watch confirmations for soft cap, hard cap, and game over. */
    @Test
    fun gameWinningGoal() {
        setTimingAlertPreferences(
            TimingAlertPreferences(watchConnectionMode = WatchConnectionMode.WEAR_OS)
        )
        startLiveGameWithDueCap("Soft cap", "Soft cap")
        useStandardTeamNames()
        signalReady("gameWinningGoal")

        // The watch defers soft cap at 1-0 and applies it at 1-1, making the target 2.
        // It then defers game over at 2-1 and confirms it at 3-1.

        // The game is over with a score of 3-1.
        waitForGame { game -> game.phase == GamePhase.GAME_OVER }
        val softCapGame = composeRule.activity.appState.currentGame!!
        assertTrue(softCapGame.softCapApplied)
        assertEquals(2, softCapGame.winningScore)
        assertEquals(3, softCapGame.teamOne.score)
        assertEquals(1, softCapGame.teamTwo.score)

        // Prepare a separate hard-cap game without waiting for wall-clock time. The phone's
        // opening goal produces the hard-cap notice that tells the watch this setup is ready.
        startLiveGameWithDueCap("Hard cap", "Hard cap")
        useStandardTeamNames()
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "goal")).performClick()

        // The watch defers hard cap, ties the score, then applies it. Animal's next goal wins.

        // The game is over with a score of 2-1.
        waitForGame { game -> game.phase == GamePhase.GAME_OVER }
        val completed = composeRule.activity.appState.currentGame!!
        assertTrue(completed.hardCapApplied)
        assertEquals(2, completed.winningScore)
        assertEquals(2, completed.teamOne.score)
        assertEquals(1, completed.teamTwo.score)
        assertEquals(GamePhase.GAME_OVER, completed.phase)
    }

    /** Host the test of entering and recording a numbered player card entirely on the watch. */
    @Test
    fun playerCardEntryOnWatch() {
        setTimingAlertPreferences(
            TimingAlertPreferences(watchConnectionMode = WatchConnectionMode.WEAR_OS)
        )
        startLivePointProgrammatically()
        useStandardTeamNames()
        updateCurrentStateProgrammatically {
            copy(teamOnePlayers = listOf(playerRecordWithCards("8", yellows = 2)))
        }
        signalReady("playerCardEntryOnWatch")

        // The watch records a red card for Animal #9, then cancels both card-entry surfaces,
        // dismisses an invalid-card notice, and cancels a phone handoff.

        // Its final goal signals that the cancellation and invalid-card checks are also done.
        waitForGame { game -> game.teamOne.score == 1 }
        val finalGame = composeRule.activity.appState.currentGame!!
        assertNull(composeRule.activity.appState.state.value.activeCardEntry)
        val players = finalGame.playerCards(TeamId.TEAM_ONE)
        assertEquals(2, players.single { player -> player.jerseyNumber == "8" }.yellows)
        val player = players.single { candidate -> candidate.jerseyNumber == "9" }
        assertEquals("9", player.jerseyNumber)
        assertEquals("", player.playerName)
        assertEquals(1, player.reds)
        assertEquals(CardType.RED, player.cards.single().cardType)
        assertEquals(CardReason(), player.cards.single().reason)
        assertEquals("Undo Goal by Animal", finalGame.undoEntry?.label)
    }

    /** Test finishing a player-card entry on the phone that the observer begins on the watch. */
    @Test
    fun playerCardPhoneHandoff() {
        setTimingAlertPreferences(
            TimingAlertPreferences(watchConnectionMode = WatchConnectionMode.WEAR_OS)
        )
        startLivePointProgrammatically()
        updateCurrentStateProgrammatically {
            copy(
                countdown = null,
                teamOne = teamOne.copy(name = ANIMAL),
                teamTwo = teamTwo.copy(name = VISCOUS_COUPLING),
                teamOnePlayers = listOf(
                    playerRecordWithCards("3", yellows = 1, playerName = "John"),
                    PlayerRecord("3", playerName = "Mark", priorYellows = 1),
                ),
            )
        }
        signalReady("playerCardPhoneHandoff")

        // The watch starts recording a yellow card, then switches to the phone to record details.

        // This is our cue that the phone has a card dialog.
        waitForPairedCardEntry()

        // Finish recording the card details.
        waitForText("Yellow card")
        composeRule.onNodeWithTag("card-player-name").performTextReplacement("Watch Handler")
        composeRule.onNodeWithText("Reason").performClick()
        composeRule.onNodeWithText("Dangerous play").performScrollTo().performClick()
        composeRule.onNodeWithText("Set").performClick()
        composeRule.onNodeWithText("Record").performClick()
        waitForText("Yellow card on #17 Watch Handler.", substring = true)
        composeRule.onNodeWithText("OK").performClick()

        // Check that the card was recorded correctly.
        assertLiveScreen()
        assertNull(composeRule.activity.appState.state.value.activeCardEntry)
        val player = composeRule.activity.appState.currentGame!!
            .playerCards(TeamId.TEAM_TWO)
            .single()
        assertEquals("17", player.jerseyNumber)
        assertEquals("Watch Handler", player.playerName)
        assertEquals(1, player.yellows)
        assertEquals(CardReason(preset = "Dangerous play"), player.cards.single().reason)

        // Signal that the first card is verified, so the watch can start the duplicate-number case.
        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_TWO, "goal")).performClick()
        waitForPairedCardEntry()

        // Recording Animal #3 on the watch requires the phone to distinguish these two players.
        waitForText("Which player is #3?")
        waitForText("#3 John (Y 1)")
        waitForText("#3 Mark (prior Y 1)")
        composeRule.onNodeWithTag("card-number-select-3-Mark").performClick()
        waitForText("Red card on #3 Mark.", substring = true)
        composeRule.onNodeWithText("OK").performClick()

        // The red belongs to Mark; John's existing yellow and both identities are preserved.
        assertLiveScreen()
        assertNull(composeRule.activity.appState.state.value.activeCardEntry)
        val players = composeRule.activity.appState.currentGame!!.playerCards(TeamId.TEAM_ONE)
        assertEquals(2, players.size)
        assertEquals(
            playerRecordWithCards("3", yellows = 1, playerName = "John"),
            players.single { it.playerName == "John" },
        )
        val selected = players.single { it.playerName == "Mark" }
        assertEquals("3", selected.jerseyNumber)
        assertEquals(1, selected.priorYellows)
        assertEquals(1, selected.reds)
        assertEquals(CardType.RED, selected.cards.single().cardType)
    }

    /** Host watch actions and halftime with short Timed rule guidance. */
    @Test
    fun timedGuidance() {
        setTimingAlertPreferences(
            TimingAlertPreferences(watchConnectionMode = WatchConnectionMode.WEAR_OS)
        )
        setRuleGuidanceMode(RuleGuidanceMode.TIMED)
        setRuleGuidanceTimeoutForTest(1_000L)
        startLivePointProgrammatically(
            newSetupGameState(now = System.currentTimeMillis()).copy(
                rules = GameRules(gameTo = 3, useHalfCap = false,
                    useSoftCap = false, useHardCap = false),
            )
        )
        useStandardTeamNames()
        signalReady("timedGuidance")

        // The watch records a timeout and a technical foul, then two Animal goals. Verify that
        // the actions and halftime completed without the observer clicking confirmation controls.
        waitForGame { game -> game.phase == GamePhase.HALFTIME }
        val game = composeRule.activity.appState.currentGame!!
        assertEquals(1, game.teamTwo.firstHalfTimeoutsUsed)
        assertEquals(1, game.teamOne.technicalFouls)
        assertEquals(2, game.teamOne.score)
        assertEquals(0, game.teamTwo.score)
    }

    /** Host watch actions with None guidance, including a required mixed-pull notice. */
    @Test
    fun noGuidance() {
        setTimingAlertPreferences(
            TimingAlertPreferences(watchConnectionMode = WatchConnectionMode.WEAR_OS)
        )
        setRuleGuidanceMode(RuleGuidanceMode.NONE)
        setRuleGuidanceTimeoutForTest(1_000L)
        startLiveGameProgrammatically(
            newSetupGameState(now = System.currentTimeMillis()).copy(
                division = GameDivision.MIXED,
                rules = GameRules(gameTo = 3, useHalfCap = false,
                    useSoftCap = false, useHardCap = false),
            )
        )
        useStandardTeamNames()
        signalReady("noGuidance")

        // The watch records offsides and a time violation, then two Animal goals. None still
        // retains mixed-pull guidance briefly, but does not require manual confirmation.
        waitForGame { game -> game.phase == GamePhase.HALFTIME }
        val game = composeRule.activity.appState.currentGame!!
        assertEquals(1, game.teamOne.offsides)
        assertEquals(1, game.teamTwo.timeViolations)
        assertEquals(2, game.teamOne.score)
        assertEquals(0, game.teamTwo.score)
    }

    /** Keep a game available while the watch tests its system shortcut, then disable Wear OS. */
    @Test
    fun ongoingGame() {
        setTimingAlertPreferences(
            TimingAlertPreferences(watchConnectionMode = WatchConnectionMode.WEAR_OS)
        )
        startLivePointProgrammatically()
        useStandardTeamNames()
        signalReady("ongoingGame")

        // A goal from the watch confirms the user returned from the ongoing notification.
        waitForGame { game -> game.teamOne.score == 1 }

        // Turning off Wear OS removes its return-to-game shortcut too.
        tapTopBarHome()
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithTag("settings-watch-connection-OFF").performScrollTo().performClick()
    }

    /** Keep the game alive while the Wear request service is temporarily unavailable. */
    @Test
    fun connectionRecovery() {
        setTimingAlertPreferences(
            TimingAlertPreferences(watchConnectionMode = WatchConnectionMode.WEAR_OS)
        )
        startLivePointProgrammatically(
            newSetupGameState(now = System.currentTimeMillis()).copy(
                rules = GameRules(useHalfCap = false, useSoftCap = false, useHardCap = false),
            )
        )
        useStandardTeamNames()
        val activity = composeRule.activity
        val manager = activity.packageManager
        val service = android.content.ComponentName(activity, WearOSRequestService::class.java)
        val originalSetting = manager.getComponentEnabledSetting(service)
        signalReady("connectionRecovery")

        // Simulate a paired phone whose app stops responding, as if the app had hung. Disabling
        // only the Wear request service produces a missing acknowledgement while keeping the game
        // and phone test running. This tests request-timeout recovery, not Bluetooth loss, moving
        // out of range, or losing the phone capability. The pairing remains intact.
        try {
            waitForRecoveryStage("disconnect")
            manager.setComponentEnabledSetting(service,
                android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                android.content.pm.PackageManager.DONT_KILL_APP)

            // Uninstalling the phone app resets its DataItems. Exercise the same deletion event
            // directly, preserving the running fixture and pairing rather than uninstalling it.
            val deleted = com.google.android.gms.tasks.Tasks.await(
                com.google.android.gms.wearable.Wearable.getDataClient(activity).deleteDataItems(
                    android.net.Uri.parse("wear://*${rmjarvis.ultiobserver.wearprotocol.WEAR_STATE_PATH}"),
                    com.google.android.gms.wearable.DataClient.FILTER_LITERAL,
                ),
                30, java.util.concurrent.TimeUnit.SECONDS,
            )
            assertEquals(1, deleted.toInt())
            File(activity.filesDir, "paired-recovery-disabled").createNewFile()

            // After the watch reports the request timeout as Lost connection, restore delivery.
            // The runner releases the watch to tap Retry only after the service is available again.
            waitForRecoveryStage("restore")
            manager.setComponentEnabledSetting(service, originalSetting,
                android.content.pm.PackageManager.DONT_KILL_APP)
            File(activity.filesDir, "paired-recovery-restored").createNewFile()

            // Only the post-recovery goal should be recorded; the timed-out goal is not replayed.
            waitForGame { it.teamOne.score == 1 }
            val game = activity.appState.currentGame!!
            assertEquals(1, game.teamOne.score)
            assertEquals(0, game.teamTwo.score)
        } finally {
            manager.setComponentEnabledSetting(service, originalSetting,
                android.content.pm.PackageManager.DONT_KILL_APP)
        }
    }

    private fun waitForRecoveryStage(stage: String) {
        catchAndDiagnoseFailure {
            composeRule.waitUntil(timeoutMillis = 60_000L) {
                File(composeRule.activity.filesDir, "paired-recovery-$stage").exists()
            }
        }
    }

    private fun waitForPairedCardEntry() {
        catchAndDiagnoseFailure {
            composeRule.waitUntil(timeoutMillis = PAIRED_TEST_TIMEOUT_MILLIS) {
                composeRule.activity.appState.state.value.activeCardEntry != null
            }
        }
    }

    private fun waitForGame(condition: (GameState) -> Boolean) {
        catchAndDiagnoseFailure {
            composeRule.waitUntil(timeoutMillis = PAIRED_TEST_TIMEOUT_MILLIS) {
                composeRule.activity.appState.currentGame?.let(condition) == true
            }
        }
    }

    private fun useStandardTeamNames() {
        updateCurrentStateProgrammatically {
            copy(
                teamOne = teamOne.copy(name = ANIMAL),
                teamTwo = teamTwo.copy(name = VISCOUS_COUPLING),
            )
        }
    }

    private fun signalReady(narrative: String) {
        File(composeRule.activity.filesDir, PAIRED_TEST_READY_FILE).writeText(narrative)
    }
}

internal const val PAIRED_TEST_READY_FILE = "paired-test-ready"
internal const val PAIRED_TEST_TIMEOUT_MILLIS = 120_000L
private const val ANIMAL = "Animal"
private const val VISCOUS_COUPLING = "Viscous Coupling"
