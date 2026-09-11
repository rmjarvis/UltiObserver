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
            )
        )
        useStandardTeamNames()
        updateCurrentStateProgrammatically {
            copy(
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
            TimingAlertPreferences(watchConnectionMode = WatchConnectionMode.WEAR_OS)
        )
        startLivePointProgrammatically()
        useStandardTeamNames()
        signalReady("timeoutAndMisconduct")

        // The watch records a timeout for VC, a tech on Animal, and a blue card on VC.

        // The blue card is the last watch action in this narrative.
        // Validate the final game state.
        waitForGame { game -> game.teamTwo.blueCards == 1 }
        val finalGame = composeRule.activity.appState.currentGame!!
        assertEquals(1, finalGame.teamTwo.timeoutsUsedThisHalf)
        assertEquals(1, finalGame.teamOne.technicalFouls)
        assertEquals(1, finalGame.teamTwo.blueCards)
        assertEquals("Undo Blue card on Viscous Coupling", finalGame.undoEntry?.label)
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

    private fun waitForPairedCardEntry() {
        composeRule.waitUntil(timeoutMillis = PAIRED_TEST_TIMEOUT_MILLIS) {
            composeRule.activity.appState.state.value.activeCardEntry != null
        }
    }

    private fun waitForGame(condition: (GameState) -> Boolean) {
        composeRule.waitUntil(timeoutMillis = PAIRED_TEST_TIMEOUT_MILLIS) {
            composeRule.activity.appState.currentGame?.let(condition) == true
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
