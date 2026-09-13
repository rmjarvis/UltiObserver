package rmjarvis.ultiobserver

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import rmjarvis.ultiobserver.wearprotocol.WearSnapshotStatus
import rmjarvis.ultiobserver.wearprotocol.WearStateSnapshot

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
        var display by mutableStateOf(gameDisplay(connected = false))
        composeRule.setContent {
            GameScreen(
                display = display,
                onTeamOne = {},
                onTeamTwo = {},
                onRetry = { retryRequested.set(true) },
                onUndo = {},
            )
        }

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
            display = gameDisplay(connected = true, actionsAvailable = false)
        }
        composeRule.onNodeWithText(
            "Resume current game on phone to enable actions"
        ).assertIsDisplayed()

        // The team names and score are still visible, but not clickable.
        composeRule.onNodeWithText("Home").assertIsNotEnabled()
        composeRule.onNodeWithText("Away").assertIsNotEnabled()
        composeRule.onNodeWithText("3").assertIsDisplayed()
        composeRule.onNodeWithText("2").assertIsDisplayed()
    }

    private fun snapshot(status: WearSnapshotStatus) = ReceivedState(
        snapshot = WearStateSnapshot(status = status, activeGame = null),
        phoneClockOffsetMillis = 0L,
    )

    private fun gameDisplay(
        connected: Boolean,
        actionsAvailable: Boolean = true,
    ) = GameDisplay(
        officialTime = "7:15",
        capStatus = "Hard cap in 8:00",
        countdownLabel = "Between points",
        countdownValue = "0:45",
        nextCue = "Next: First warning",
        statusMessage = null,
        teamOne = TeamDisplay("Home", 3, Color.White, Color.Black),
        teamTwo = TeamDisplay("Away", 2, Color.Black, Color.White),
        pullDirection = PullDirection.LEFT_TO_RIGHT,
        ratioBadge = RatioBadgeDisplay("A", Color.White, Color.Black),
        connected = connected,
        actionsAvailable = actionsAvailable,
        gameOver = false,
        undoDescription = "Undo Goal by Home",
    )

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
