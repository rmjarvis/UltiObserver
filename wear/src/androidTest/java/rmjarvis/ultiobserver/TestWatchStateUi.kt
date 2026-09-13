package rmjarvis.ultiobserver

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Rule
import org.junit.Test
import rmjarvis.ultiobserver.wearprotocol.WearSnapshotStatus
import rmjarvis.ultiobserver.wearprotocol.WearStateSnapshot
import rmjarvis.ultiobserver.wearprotocol.WearActiveGameSnapshot
import rmjarvis.ultiobserver.wearprotocol.WearPhoneCardEntrySnapshot
import rmjarvis.ultiobserver.wearprotocol.WearSnapshotPullDirection
import rmjarvis.ultiobserver.wearprotocol.WearTeamActionsSnapshot
import rmjarvis.ultiobserver.wearprotocol.WearTeamSnapshot
import rmjarvis.ultiobserver.wearprotocol.WearCapSnapshot
import rmjarvis.ultiobserver.wearprotocol.WearCountdownSnapshot
import rmjarvis.ultiobserver.wearprotocol.WearCueSnapshot
import rmjarvis.ultiobserver.wearprotocol.WearActionConfirmation
import rmjarvis.ultiobserver.wearprotocol.WearTeamActionPrompt
import rmjarvis.ultiobserver.wearprotocol.WearPromptSnapshot
import rmjarvis.ultiobserver.wearprotocol.WearGuidancePresentation

/**
 * Tests of the connection status between a watch and the phone.
 *
 * For these, we don't use an actual paired phone. Instead, we manually control the
 * connection and disconnection status so we can check that the watch handles this properly.
 */
class TestWatchStateUi {
    @get:Rule
    val composeRule = createComposeRule()

    /** Test what happens when there is no paired phone available. */
    @Test
    fun phoneDiscoveryAndRetry() {
        val retryRequested = AtomicBoolean(false)
        var connection by mutableStateOf(ConnectionState.CONNECTING)
        show(
            state = { null },
            connection = { connection },
            onRetry = { retryRequested.set(true) },
        )

        // On initial startup, the watch tries to connect to a paired phone.
        composeRule.onNodeWithText("Connecting…").assertIsDisplayed()

        // If it doesn't find one, it reports that.
        composeRule.runOnIdle { connection = ConnectionState.DISCONNECTED }
        composeRule.onNodeWithText(
            "Could not find a paired phone running UltiObserver."
        ).assertIsDisplayed()

        // The only real option for the user is to retry the connection.
        composeRule.onNodeWithText("Retry").performClick()
        assertTrue(retryRequested.get())
    }

    /** Test a connected phone with Wear OS disabled, then idle and disconnected states. */
    @Test
    fun disabledIdleAndDisconnectedPhoneStates() {
        val retryRequested = AtomicBoolean(false)
        var state by mutableStateOf<ReceivedState?>(null)
        var connection by mutableStateOf(ConnectionState.DISABLED)
        show(
            state = { state },
            connection = { connection },
            onRetry = { retryRequested.set(true) },
        )

        // If the watch connects to a phone, but the phone does not have the setting set to
        // use the Wear OS companion app, the watch says where to set that.
        composeRule.onNodeWithText(
            "To use, set Watch connection to Wear OS in the UltiObserver Settings."
        ).assertIsDisplayed()

        // Once it is enabled, the phone is still not showing an active game.
        // Until it does, the watch says that there isn't an active game yet.
        composeRule.runOnIdle {
            state = snapshot(WearSnapshotStatus.NO_ACTIVE_GAME)
            connection = ConnectionState.CONNECTED
        }
        composeRule.onNodeWithText("No active game").assertIsDisplayed()

        // If the connection is lost, but the phone had previously been connected, then
        // the message is a little different.
        composeRule.runOnIdle { connection = ConnectionState.DISCONNECTED }
        composeRule.onNodeWithText("Lost connection").assertIsDisplayed()

        // The user can retry.
        composeRule.onNodeWithText("Retry").performClick()
        assertTrue(retryRequested.get())
    }

    /** Test losing the connection during a game, and reconnecting. */
    @Test
    fun disconnectedThenPausedGame() {
        val retryRequested = AtomicBoolean(false)
        val now = System.currentTimeMillis()
        val initial = activeSnapshot()
        var state by mutableStateOf(initial.copy(snapshot = initial.snapshot.copy(
            activeGame = initial.snapshot.activeGame!!.copy(
                upcomingCaps = listOf(
                    WearCapSnapshot("Half cap", now - 60_000L),
                    WearCapSnapshot("Hard cap", now + 600_000L),
                ),
                countdown = WearCountdownSnapshot(
                    label = "Between points",
                    targetEpochMillis = now + 45_000L,
                    pausedAtEpochMillis = now,
                    cues = listOf(
                        WearCueSnapshot("Earlier warning", now - 60_000L),
                        WearCueSnapshot("Next warning", now + 30_000L),
                    ),
                ),
            ),
        )))
        var connection by mutableStateOf(ConnectionState.CONNECTED)
        show(
            state = { state },
            connection = { connection },
            onRetry = { retryRequested.set(true) },
        )

        // A paused countdown retains its remaining time. Expired caps and cues must not hide
        // the next applicable ones.
        composeRule.onNodeWithText("0:45").assertIsDisplayed()
        composeRule.onNodeWithText("Hard cap in", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Half cap in", substring = true).assertDoesNotExist()
        composeRule.onNodeWithText("Next: Next warning").assertIsDisplayed()
        composeRule.onNodeWithText("Next: Earlier warning").assertDoesNotExist()

        // Losing the phone while its team actions are open must dismiss that surface, not leave
        // an enabled Goal button acting on stale state.
        composeRule.onNodeWithText("Home").performClick()
        composeRule.onNodeWithText("Goal").assertIsDisplayed()
        composeRule.runOnIdle { connection = ConnectionState.DISCONNECTED }

        // If the connection is lost, but the phone had previously been connected, then
        // let the user know about the lost connection.
        composeRule.onNodeWithText("Lost connection").assertIsDisplayed()

        // The game screen (with teams and score) is still visible behind the retry button.
        composeRule.onNodeWithText("Home").assertIsNotEnabled()
        composeRule.onNodeWithText("Away").assertIsNotEnabled()
        composeRule.onNodeWithText("3").assertIsDisplayed()
        composeRule.onNodeWithText("2").assertIsDisplayed()
        composeRule.onNodeWithText("Retry").performClick()
        assertTrue(retryRequested.get())

        // When connected, but the phone is not on the active game screen, it tells the user
        // to resume the current game.
        composeRule.runOnIdle {
            state = state.copy(snapshot = state.snapshot.copy(
                activeGame = state.snapshot.activeGame!!.copy(actionsAvailable = false),
            ))
            connection = ConnectionState.CONNECTED
        }
        composeRule.onNodeWithText(
            "Resume current game on phone to enable actions"
        ).assertIsDisplayed()

        // The team names and score are still visible, but not clickable.
        composeRule.onNodeWithText("Home").assertIsNotEnabled()
        composeRule.onNodeWithText("Away").assertIsNotEnabled()
        composeRule.onNodeWithText("3").assertIsDisplayed()
        composeRule.onNodeWithText("2").assertIsDisplayed()

        // Returning to the phone's game screen restores the paused countdown at the same value.
        composeRule.runOnIdle {
            state = state.copy(snapshot = state.snapshot.copy(
                activeGame = state.snapshot.activeGame!!.copy(actionsAvailable = true),
            ))
        }
        composeRule.onNodeWithText("0:45").assertIsDisplayed()
    }

    /** Clear pending watch actions when their rejection arrives with newer phone state. */
    @Test
    fun phoneChangesDuringRequests() {
        var state by mutableStateOf(activeSnapshot())
        var finishGoal: ((Boolean) -> Unit)? = null
        var finishPrompt: ((WearTeamActionPrompt?) -> Unit)? = null
        var finishConfirmation: ((Boolean) -> Unit)? = null
        var finishHandoff: ((Boolean) -> Unit)? = null
        var finishCancellation: ((Boolean) -> Unit)? = null
        composeRule.setContent {
            UltiObserverWearApp(
                receivedState = state,
                connectionState = ConnectionState.CONNECTED,
                onRetry = {},
                onGoal = { _, _, finished -> finishGoal = finished },
                onUndo = { _, _ -> },
                onDecision = { _, _, _ -> },
                onTeamAction = { _, _, _, finished -> finishPrompt = finished },
                onStartCardEntry = { _, _, _, _, finished -> finishHandoff = finished },
                onCancelCardEntry = { _, _, _, _, finished -> finishCancellation = finished },
                onConfirmAction = { _, finished -> finishConfirmation = finished },
            )
        }

        // The watch goal is in flight when the phone scores. The controller reports rejection
        // before exposing that newer snapshot. The watch keeps the phone's score and releases
        // its pending controls so the observer can choose another action.
        composeRule.onNodeWithText("Home").performClick()
        composeRule.onNodeWithText("Goal").performClick()
        assertNotNull(finishGoal)
        composeRule.runOnIdle {
            finishGoal!!(false)
            state = state.copy(snapshot = state.snapshot.copy(
                activeGame = state.snapshot.activeGame!!.copy(
                    stateToken = "phone-goal",
                    teamOne = state.snapshot.activeGame!!.teamOne.copy(score = 4),
                ),
            ))
        }
        composeRule.onNodeWithText("Goal").assertIsDisplayed()

        // Another phone goal overtakes a timeout-prompt request. No prompt is returned, so the
        // watch remains on team actions rather than opening obsolete timeout guidance.
        composeRule.onNodeWithText("Timeout (2)").performClick()
        assertNotNull(finishPrompt)
        composeRule.runOnIdle {
            finishPrompt!!(null)
            state = state.copy(snapshot = state.snapshot.copy(
                activeGame = state.snapshot.activeGame!!.copy(
                    stateToken = "second-phone-goal",
                    teamOne = state.snapshot.activeGame!!.teamOne.copy(score = 5),
                ),
            ))
        }
        composeRule.onNodeWithText("Goal").assertIsDisplayed()
        composeRule.onNodeWithText("OK").assertDoesNotExist()

        // A fresh timeout request opens its confirmation, but the phone moves on before OK
        // arrives. Rejection closes that prompt and leaves the updated team actions usable.
        composeRule.onNodeWithText("Timeout (2)").performClick()
        composeRule.runOnIdle {
            finishPrompt!!(WearActionConfirmation.Timeout(
                "second-phone-goal", TeamId.TEAM_ONE, System.currentTimeMillis(),
                WearPromptSnapshot(
                    "Timeout", emptyList(), "OK", "Cancel", WearGuidancePresentation.VISIBLE, null,
                ),
            ))
        }
        composeRule.onNodeWithText("OK").performClick()
        assertNotNull(finishConfirmation)
        composeRule.runOnIdle {
            finishConfirmation!!(false)
            state = state.copy(snapshot = state.snapshot.copy(
                activeGame = state.snapshot.activeGame!!.copy(
                    stateToken = "third-phone-goal",
                    teamOne = state.snapshot.activeGame!!.teamOne.copy(score = 6),
                ),
            ))
        }
        composeRule.onNodeWithText("OK").assertDoesNotExist()
        composeRule.onNodeWithText("Goal").assertIsDisplayed()

        // The phone scores before a card handoff is processed. Rejection and the new token
        // discard the watch's numbered-card workflow instead of showing Continue on phone.
        composeRule.onNodeWithText("Card").performClick()
        composeRule.onNodeWithText("Yellow").performClick()
        composeRule.onNodeWithText("Enter details on phone").performClick()
        assertNotNull(finishHandoff)
        composeRule.runOnIdle {
            finishHandoff!!(false)
            state = state.copy(snapshot = state.snapshot.copy(
                activeGame = state.snapshot.activeGame!!.copy(
                    stateToken = "fourth-phone-goal",
                    teamOne = state.snapshot.activeGame!!.teamOne.copy(score = 7),
                ),
            ))
        }
        composeRule.onNodeWithText("Continue on phone").assertDoesNotExist()
        composeRule.onNodeWithText("Yellow card").assertDoesNotExist()
        composeRule.onNodeWithText("Goal").assertIsDisplayed()

        // The observer retries the handoff successfully, then cancels just as the phone finishes
        // recording the card. A rejected cancellation must return to the game, not its picker.
        composeRule.onNodeWithText("Card").performClick()
        composeRule.onNodeWithText("Yellow").performClick()
        composeRule.onNodeWithText("Enter details on phone").performClick()
        composeRule.runOnIdle {
            finishHandoff!!(true)
            state = state.copy(snapshot = state.snapshot.copy(
                activeGame = state.snapshot.activeGame!!.copy(
                    actionsAvailable = false,
                    phoneCardEntry = WearPhoneCardEntrySnapshot(TeamId.TEAM_ONE, CardType.YELLOW, ""),
                ),
            ))
        }
        composeRule.onNodeWithText("Continue on phone").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        assertNotNull(finishCancellation)
        composeRule.runOnIdle {
            finishCancellation!!(false)
            state = state.copy(snapshot = state.snapshot.copy(
                activeGame = state.snapshot.activeGame!!.copy(
                    stateToken = "card-recorded",
                    phoneCardEntry = null,
                    actionsAvailable = true,
                ),
            ))
        }
        composeRule.onNodeWithText("Home").assertIsDisplayed()
        composeRule.onNodeWithText("7").assertIsDisplayed()
        composeRule.onNodeWithText("Continue on phone").assertDoesNotExist()
        composeRule.onNodeWithText("Assess a card").assertDoesNotExist()
    }

    /** Replace local card navigation when the phone changes game or handoff state. */
    @Test
    fun phoneChangesDuringCardEntry() {
        var state by mutableStateOf(activeSnapshot())
        show(state = { state }, connection = { ConnectionState.CONNECTED })

        // While the watch's card picker is open, the phone records a goal and begins a card.
        // The new token invalidates the local picker; the phone's active entry takes precedence.
        composeRule.onNodeWithText("Home").performClick()
        composeRule.onNodeWithText("Card").performClick()
        composeRule.onNodeWithText("Assess a card").assertIsDisplayed()
        composeRule.runOnIdle {
            state = state.copy(snapshot = state.snapshot.copy(
                activeGame = state.snapshot.activeGame!!.copy(
                    stateToken = "phone-goal",
                    teamOne = state.snapshot.activeGame!!.teamOne.copy(score = 4),
                    actionsAvailable = false,
                    phoneCardEntry = WearPhoneCardEntrySnapshot(TeamId.TEAM_TWO, CardType.YELLOW, "17"),
                ),
            ))
        }
        composeRule.onNodeWithText("Continue on phone").assertIsDisplayed()
        composeRule.onNodeWithText("Assess a card").assertDoesNotExist()

        // Cancelling on the phone returns the watch to the updated game, not its stale picker.
        composeRule.runOnIdle {
            state = state.copy(snapshot = state.snapshot.copy(
                activeGame = state.snapshot.activeGame!!.copy(
                    actionsAvailable = true, phoneCardEntry = null,
                ),
            ))
        }
        composeRule.onNodeWithText("Home").assertIsDisplayed()
        composeRule.onNodeWithText("4").assertIsDisplayed()
        composeRule.onNodeWithText("Assess a card").assertDoesNotExist()

        // Leaving the active phone screen during number entry closes that local workflow too.
        composeRule.onNodeWithText("Away").performClick()
        composeRule.onNodeWithText("Card").performClick()
        composeRule.onNodeWithText("Yellow").performClick()
        composeRule.onNodeWithText("Yellow card").assertIsDisplayed()
        composeRule.runOnIdle {
            state = state.copy(snapshot = state.snapshot.copy(
                activeGame = state.snapshot.activeGame!!.copy(actionsAvailable = false),
            ))
        }
        composeRule.onNodeWithText("Resume current game on phone to enable actions").assertIsDisplayed()
        composeRule.onNodeWithText("Yellow card").assertDoesNotExist()
        composeRule.onNodeWithText("Away").assertIsNotEnabled()

        // Disabling Wear after an established connection arrives as published state, not as the
        // disabled startup reply. The watch still explains how to enable the connection.
        composeRule.runOnIdle { state = snapshot(WearSnapshotStatus.DISABLED) }
        composeRule.onNodeWithText(
            "To use, set Watch connection to Wear OS in the UltiObserver Settings."
        ).assertIsDisplayed()
    }

    private fun snapshot(status: WearSnapshotStatus) = ReceivedState(
        snapshot = WearStateSnapshot(status = status, activeGame = null),
        phoneClockOffsetMillis = 0L,
    )

    private fun activeSnapshot(): ReceivedState {
        val actions = WearTeamActionsSnapshot(
            "Time viol.", "Offsides", "Card", "Tech", "Timeout (2)",
            true, true, true, true, true, true,
        )
        return ReceivedState(
            snapshot = WearStateSnapshot(
                status = WearSnapshotStatus.ACTIVE_GAME,
                activeGame = WearActiveGameSnapshot(
                    stateToken = "playing",
                    actionsAvailable = true,
                    officialClockOffsetMillis = 0,
                    officialTimeZoneId = "UTC",
                    countdown = null,
                    teamOne = WearTeamSnapshot("Home", 3, 0xFFFFFFFF, 0xFF000000, actions),
                    teamTwo = WearTeamSnapshot("Away", 2, 0xFF000000, 0xFFFFFFFF, actions),
                    pullDirection = WearSnapshotPullDirection.LEFT_TO_RIGHT,
                    ratio = null,
                    undoDescription = "Undo Goal by Home",
                    pendingDecision = null,
                    phoneCardEntry = null,
                ),
            ),
            phoneClockOffsetMillis = 0,
        )
    }

    private fun show(
        state: () -> ReceivedState?,
        connection: () -> ConnectionState,
        onRetry: () -> Unit = {},
    ) {
        composeRule.setContent {
            UltiObserverWearApp(
                receivedState = state(),
                connectionState = connection(),
                onRetry = onRetry,
                onGoal = { _, _, _ -> },
                onUndo = { _, _ -> },
                onDecision = { _, _, _ -> },
                onTeamAction = { _, _, _, _ -> },
                onStartCardEntry = { _, _, _, _, _ -> },
                onCancelCardEntry = { _, _, _, _, _ -> },
                onConfirmAction = { _, _ -> },
            )
        }
    }
}
