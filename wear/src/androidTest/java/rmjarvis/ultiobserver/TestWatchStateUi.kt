package rmjarvis.ultiobserver

import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.media.AudioAttributes
import android.os.Build
import android.os.VibrationAttributes
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.service.notification.StatusBarNotification
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.DataItem
import java.util.concurrent.TimeUnit
import androidx.compose.runtime.getValue
import androidx.compose.runtime.CompositionLocalProvider
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toPixelMap
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
import org.mockito.Mockito.never
import org.mockito.Mockito.times
import org.mockito.Mockito.verify
import org.mockito.ArgumentCaptor
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.eq
import rmjarvis.ultiobserver.wearprotocol.WearSnapshotStatus
import rmjarvis.ultiobserver.wearprotocol.WearStateSnapshot
import rmjarvis.ultiobserver.wearprotocol.WearActiveGameSnapshot
import rmjarvis.ultiobserver.wearprotocol.WearSnapshotPullDirection
import rmjarvis.ultiobserver.wearprotocol.WearTeamActionsSnapshot
import rmjarvis.ultiobserver.wearprotocol.WearTeamSnapshot
import rmjarvis.ultiobserver.wearprotocol.WearCountdownSnapshot
import rmjarvis.ultiobserver.wearprotocol.WearCountdownAction
import rmjarvis.ultiobserver.wearprotocol.WearTimingControlsSnapshot
import rmjarvis.ultiobserver.wearprotocol.WearRatioChooserSnapshot
import rmjarvis.ultiobserver.wearprotocol.WearRatioSnapshot
import rmjarvis.ultiobserver.wearprotocol.WearRulesReferenceItemSnapshot
import rmjarvis.ultiobserver.wearprotocol.WATCH_VIBRATION_PATH
import rmjarvis.ultiobserver.wearprotocol.WearProtocolCodec
import rmjarvis.ultiobserver.wearprotocol.WearVibrationRequest
import rmjarvis.ultiobserver.wearprotocol.WearVibrationResponse
import rmjarvis.ultiobserver.wearprotocol.WearPromptSnapshot
import rmjarvis.ultiobserver.wearprotocol.WearGuidancePresentation
import rmjarvis.ultiobserver.wearprotocol.WearOngoingGameStatus
import rmjarvis.ultiobserver.wearprotocol.WearActionConfirmation
import rmjarvis.ultiobserver.wearprotocol.WearPhoneCardEntrySnapshot

/**
 * Tests of the connection status between a watch and the phone.
 *
 * For these, we don't use an actual paired phone. Instead, we manually control the
 * connection and disconnection status so we can check that the watch handles this properly.
 */
class TestWatchStateUi {
    @get:Rule
    val composeRule = createComposeRule()
    private var isAmbient by mutableStateOf(false)
    private var submittedCommands = 0

    /** Advance the displayed countdown locally without receiving another phone snapshot. */
    @Test
    fun countdown() {
        val initial = activeSnapshot()
        val state = initial.copy(snapshot = initial.snapshot.copy(
            activeGame = initial.snapshot.activeGame!!.copy(
                countdown = WearCountdownSnapshot(
                    label = "Between points",
                    targetEpochMillis = System.currentTimeMillis() + 45_000L,
                    pausedAtEpochMillis = null,
                    cues = emptyList(),
                ),
                rulesReference = listOf(
                    WearRulesReferenceItemSnapshot("Game to", "15", false),
                    WearRulesReferenceItemSnapshot("Timeout", "90 seconds", true),
                ),
            ),
        ))
        show(state = { state }, connection = { ConnectionState.CONNECTED })

        // Keep the received state unchanged. A smaller displayed value must therefore come from
        // the watch's own clock loop resuming after its delay, not a replacement phone snapshot.
        val firstValue = countdownSeconds()
        composeRule.waitUntil(timeoutMillis = 10_000L) {
            countdownSeconds() < firstValue
        }
        composeRule.onNodeWithText("Home").assertIsEnabled()

        // With no cap text, the paper icon still opens the rules and Back restores the game.
        composeRule.onNodeWithContentDescription("Game rules").performClick()
        composeRule.onNodeWithText("Game to 15").assertIsDisplayed()
        composeRule.onNodeWithText("Timeout 90 seconds").assertIsDisplayed()

        // Inactive mode is entirely black and has no controls.
        composeRule.runOnIdle { isAmbient = true }
        composeRule.onNodeWithText("Back").assertDoesNotExist()
        composeRule.onNodeWithText("Game to 15").assertDoesNotExist()
        val pixels = composeRule.onRoot().captureToImage().toPixelMap()
        for (y in 0 until pixels.height) {
            for (x in 0 until pixels.width) {
                assertEquals(Color.Black, pixels[x, y])
            }
        }

        // Long pressing the blank screen cannot activate any command.
        composeRule.onRoot().performTouchInput { longClick() }
        composeRule.runOnIdle { assertEquals(0, submittedCommands) }

        // Waking restores the selected screen; the countdown catches up to the current time.
        composeRule.runOnIdle { isAmbient = false }
        composeRule.onNodeWithText("Game to 15").assertIsDisplayed()
        composeRule.onNodeWithText("Timeout 90 seconds").assertIsDisplayed()
        composeRule.onNodeWithText("Back").performClick()
        composeRule.onNodeWithText("Home").assertIsDisplayed()
        assertTrue(countdownSeconds() < firstValue)
    }

    /** Keep early actions below, then expose the next action directly when the countdown expires. */
    @Test
    fun countdownActions() {
        val now = System.currentTimeMillis()
        val initial = activeSnapshot()
        val timedGame = initial.snapshot.activeGame!!.copy(
            countdown = WearCountdownSnapshot("Pull in", now + 120_000L, null, emptyList()),
            timingControls = WearTimingControlsSnapshot(
                listOf(WearCountdownAction.PAUSE, WearCountdownAction.MINUS_FIVE,
                    WearCountdownAction.PLUS_FIVE),
                WearCountdownAction.START_POINT, WearCountdownAction.RESTART_PULL,
            ),
        )
        var state by mutableStateOf(initial.copy(snapshot = initial.snapshot.copy(activeGame = timedGame)))
        show(state = { state }, connection = { ConnectionState.CONNECTED })

        // Before expiry the early action is reached by opening the timer controls.
        composeRule.onNodeWithText("Start point").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Countdown controls").performClick()
        composeRule.onNodeWithText("Start point").assertIsDisplayed()
        composeRule.onNodeWithText("Restart countdown").assertDoesNotExist()
        composeRule.onNodeWithText("Back").performClick()
        composeRule.onNodeWithText("Start point").assertDoesNotExist()

        // An expired pull offers Start point directly and Restart countdown in the lower panel.
        composeRule.runOnIdle {
            state = initial.copy(snapshot = initial.snapshot.copy(activeGame = timedGame.copy(
                countdown = timedGame.countdown!!.copy(targetEpochMillis = now - 1_000L),
            )))
        }
        composeRule.onNodeWithTag("countdown-value", useUnmergedTree = true).assertIsDisplayed()
        composeRule.onNodeWithText("Start point").assertIsDisplayed()
        composeRule.onNodeWithText("Restart countdown").assertDoesNotExist()
        composeRule.onNodeWithContentDescription("Countdown controls").performClick()
        composeRule.onNodeWithText("Start point").assertIsDisplayed()
        catchAndDiagnoseFailure(composeRule) {
            composeRule.onNodeWithText("Restart countdown").assertIsDisplayed().performClick()
        }
        composeRule.runOnIdle { assertEquals(1, submittedCommands) }

        // Offense timing uses the same placement, with Continue point as its secondary action.
        composeRule.runOnIdle {
            state = initial.copy(snapshot = initial.snapshot.copy(activeGame = timedGame.copy(
                stateToken = "expired-timeout",
                countdown = WearCountdownSnapshot("Offense set in", now - 1_000L, null, emptyList()),
                timingControls = timedGame.timingControls!!.copy(
                    pointAction = WearCountdownAction.OFFENSE_SET,
                    secondaryAction = WearCountdownAction.CONTINUE_POINT,
                ),
            )))
        }
        composeRule.onNodeWithText("Continue point").assertIsDisplayed()
        composeRule.onNodeWithText("Back").performClick()
        composeRule.onNodeWithText("Continue point").assertDoesNotExist()
        composeRule.onNodeWithText("Offense is set").assertIsDisplayed().performClick()
        composeRule.runOnIdle { assertEquals(2, submittedCommands) }
    }

    private fun countdownSeconds(): Int {
        val text = composeRule.onNodeWithTag("countdown-value", useUnmergedTree = true)
            .fetchSemanticsNode().config[SemanticsProperties.Text].single().text
        return text.substringAfter(":").toInt()
    }

    /** Stop the display timer when hidden, even without an ambient-mode transition. */
    @Test
    fun hiddenCountdown() {
        val owner = object : LifecycleOwner {
            override val lifecycle = LifecycleRegistry(this)
        }
        composeRule.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        val initial = activeSnapshot()
        val state = initial.copy(snapshot = initial.snapshot.copy(
            activeGame = initial.snapshot.activeGame!!.copy(
                countdown = WearCountdownSnapshot(
                    label = "Between points",
                    targetEpochMillis = System.currentTimeMillis() + 45_000L,
                    pausedAtEpochMillis = null,
                    cues = emptyList(),
                ),
            ),
        ))
        composeRule.setContent {
            CompositionLocalProvider(LocalLifecycleOwner provides owner) {
                UltiObserverWearApp(
                    isAmbient = false,
                    receivedState = state,
                    connectionState = ConnectionState.CONNECTED,
                    onRetry = {},
                    onGoal = { _, _, _ -> },
                    onUndo = { _, _ -> },
                    onRedo = { _, _ -> },
                    onCountdownAction = { _, _, _ -> },
                    onDecision = { _, _, _ -> },
                    onTeamAction = { _, _, _, _ -> },
                    onStartCardEntry = { _, _, _, _, _ -> },
                    onCancelCardEntry = { _, _, _, _, _ -> },
                    onConfirmAction = { _, _ -> },
                )
            }
        }

        // The visible countdown advances using the watch's clock without another phone update.
        val initialSeconds = countdownSeconds()
        composeRule.waitUntil(timeoutMillis = 10_000L) {
            countdownSeconds() < initialSeconds
        }

        // Keep composition alive while reporting the app stopped, without entering ambient mode.
        // Let real time pass, then advance the test scheduler enough for another timer update.
        // The displayed value must stay unchanged because the app is stopped.
        composeRule.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.CREATED }
        composeRule.mainClock.autoAdvance = false
        val stoppedSeconds = countdownSeconds()
        android.os.SystemClock.sleep(1_100L)
        composeRule.mainClock.advanceTimeBy(2_000L)
        assertEquals(stoppedSeconds, countdownSeconds())

        // Returning to the app immediately catches up rather than resuming a stale countdown.
        composeRule.runOnIdle { owner.lifecycle.currentState = Lifecycle.State.RESUMED }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.mainClock.advanceTimeByFrame()
        assertTrue(countdownSeconds() < stoppedSeconds)
        composeRule.onNodeWithText("Home").assertIsEnabled()
    }

    /** Keep automatic confirmations running while their controls are hidden in ambient mode. */
    @Test
    fun ambientGuidance() {
        val initial = activeSnapshot()
        var state by mutableStateOf(initial.copy(snapshot = initial.snapshot.copy(
            activeGame = initial.snapshot.activeGame!!.copy(
                pendingDecision = WearPromptSnapshot(
                    title = "Half cap",
                    messageLines = emptyList(),
                    confirmLabel = "OK",
                    dismissLabel = "Not yet",
                    presentation = WearGuidancePresentation.VISIBLE_TIMED,
                    autoAcceptDelayMillis = 1_000L,
                ),
            ),
        )))
        var confirmations = 0
        var finishDecision: ((Boolean) -> Unit)? = null
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            UltiObserverWearApp(
                isAmbient = isAmbient,
                receivedState = state,
                connectionState = ConnectionState.CONNECTED,
                onRetry = {},
                onGoal = { _, _, _ -> },
                onUndo = { _, _ -> },
                onRedo = { _, _ -> },
                onCountdownAction = { _, _, _ -> },
                onDecision = { _, accept, finished ->
                    assertTrue(accept)
                    confirmations++
                    // Hold the phone's reply: sending acceptance alone does not close the dialog.
                    finishDecision = finished
                },
                onTeamAction = { _, _, _, _ -> },
                onStartCardEntry = { _, _, _, _, _ -> },
                onCancelCardEntry = { _, _, _, _, _ -> },
                onConfirmAction = { _, _ -> },
            )
        }

        // The timer starts while visible and completes on schedule after the screen goes blank.
        composeRule.mainClock.advanceTimeBy(500L)
        composeRule.onNodeWithText("OK").assertIsDisplayed()
        composeRule.runOnIdle { isAmbient = true }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithText("OK").assertDoesNotExist()
        composeRule.mainClock.advanceTimeBy(600L)
        composeRule.runOnIdle { assertEquals(1, confirmations) }

        // Waking while the phone is still answering does not submit a second confirmation.
        // Without completion or a new phone snapshot, the dialog stays visible with OK disabled.
        composeRule.runOnIdle { isAmbient = false }
        composeRule.mainClock.advanceTimeBy(1_100L)
        composeRule.onNodeWithText("OK").assertIsNotEnabled()
        composeRule.runOnIdle { assertEquals(1, confirmations) }

        // The phone acknowledges acceptance and publishes state with no pending confirmation.
        composeRule.runOnIdle {
            finishDecision!!(true)
            state = state.copy(snapshot = state.snapshot.copy(
                activeGame = state.snapshot.activeGame!!.copy(
                    stateToken = "half-cap-accepted",
                    pendingDecision = null,
                ),
            ))
        }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithText("OK").assertDoesNotExist()
        composeRule.onNodeWithText("Half cap").assertDoesNotExist()
        composeRule.onNodeWithText("Home").assertIsDisplayed().assertIsEnabled()
        composeRule.runOnIdle { assertEquals(1, confirmations) }
    }

    /** Preserve a timed action confirmation while asleep and awaiting the phone's response. */
    @Test
    fun ambientActionConfirmation() {
        val state = activeSnapshot()
        val confirmation = WearActionConfirmation.Timeout(
            stateToken = state.snapshot.activeGame!!.stateToken,
            team = TeamId.TEAM_ONE,
            requestedAtPhoneEpochMillis = 1_000L,
            prompt = WearPromptSnapshot(
                title = "Timeout",
                messageLines = emptyList(),
                confirmLabel = "OK",
                dismissLabel = "Cancel",
                presentation = WearGuidancePresentation.VISIBLE_TIMED,
                autoAcceptDelayMillis = 1_000L,
            ),
        )
        var confirmations = 0
        var finishConfirmation: ((Boolean) -> Unit)? = null
        composeRule.mainClock.autoAdvance = false
        composeRule.setContent {
            UltiObserverWearApp(
                isAmbient = isAmbient,
                receivedState = state,
                connectionState = ConnectionState.CONNECTED,
                onRetry = {},
                onGoal = { _, _, _ -> },
                onUndo = { _, _ -> },
                onRedo = { _, _ -> },
                onCountdownAction = { _, _, _ -> },
                onDecision = { _, _, _ -> },
                onTeamAction = { _, _, _, finished -> finished(confirmation) },
                onStartCardEntry = { _, _, _, _, _ -> },
                onCancelCardEntry = { _, _, _, _, _ -> },
                onConfirmAction = { submitted, finished ->
                    assertEquals(confirmation, submitted)
                    confirmations++
                    finishConfirmation = finished
                },
            )
        }

        // Requesting a timeout displays the phone's confirmation before its timer expires.
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithText("Home").performClick()
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithText("Timeout (2)").performClick()
        composeRule.mainClock.advanceTimeBy(100L)
        composeRule.onNodeWithText("OK").assertIsEnabled()

        // Sleeping removes the controls, but the timer still submits the complete confirmation.
        composeRule.runOnIdle { isAmbient = true }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithText("OK").assertDoesNotExist()
        composeRule.onNodeWithText("Cancel").assertDoesNotExist()
        composeRule.mainClock.advanceTimeBy(1_100L)
        composeRule.runOnIdle { assertEquals(1, confirmations) }

        // Waking before the phone replies preserves the pending request and disabled controls.
        composeRule.runOnIdle { isAmbient = false }
        composeRule.mainClock.advanceTimeBy(1_100L)
        composeRule.onNodeWithText("OK").assertIsNotEnabled()
        composeRule.onNodeWithText("Cancel").assertIsNotEnabled()
        composeRule.runOnIdle { assertEquals(1, confirmations) }

        // The successful phone response closes the confirmation and restores game controls.
        composeRule.runOnIdle { finishConfirmation!!(true) }
        composeRule.mainClock.advanceTimeByFrame()
        composeRule.onNodeWithText("OK").assertDoesNotExist()
        composeRule.onNodeWithText("Home").assertIsEnabled()
    }

    /** Display the ratio-choice badge beside whichever team the phone identifies. */
    @Test
    fun ratioChooser() {
        val initial = activeSnapshot()
        val chooser = WearRatioChooserSnapshot(
            TeamId.TEAM_ONE,
            WearRatioSnapshot("M", 0xFF000000, 0xFFFFFFFF),
            WearRatioSnapshot("W", 0xFFFFFFFF, 0xFF000000),
        )
        var state by mutableStateOf(initial.copy(snapshot = initial.snapshot.copy(
            activeGame = initial.snapshot.activeGame!!.copy(ratioChooser = chooser),
        )))
        show(state = { state }, connection = { ConnectionState.CONNECTED })

        // Both ratio choices identify the team responsible for the current point.
        composeRule.onNodeWithContentDescription("Home chooses ratio").assertIsDisplayed()
        composeRule.onNodeWithText("M").assertIsDisplayed()
        composeRule.onNodeWithText("W").assertIsDisplayed()

        // The next phone snapshot assigns the choice to the opposite team.
        composeRule.runOnIdle {
            state = state.copy(snapshot = state.snapshot.copy(
                activeGame = state.snapshot.activeGame!!.copy(
                    ratioChooser = chooser.copy(team = TeamId.TEAM_TWO),
                ),
            ))
        }
        composeRule.onNodeWithContentDescription("Away chooses ratio").assertIsDisplayed()
        composeRule.onNodeWithContentDescription("Home chooses ratio").assertDoesNotExist()
    }

    /** Test what happens when there is no paired phone available. */
    @Test
    fun phoneDiscoveryAndRetry() {
        val retryRequested = AtomicBoolean(false)
        var connection by mutableStateOf<ConnectionState>(ConnectionState.CONNECTING)
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

    /** An incompatible phone replaces old controls with an actionable update message. */
    @Test
    fun updateRequired() {
        var connection by mutableStateOf<ConnectionState>(ConnectionState.UpdateRequired(
            updatePhone = false, phoneVersion = "1.5.0", watchVersion = "1.4.0",
        ))
        val retryRequested = AtomicBoolean(false)
        show(
            state = { activeSnapshot() },
            connection = { connection },
            onRetry = { retryRequested.set(true) },
        )

        // A retained game cannot hide the update instruction or leave actions available.
        composeRule.onNodeWithText(
            "Update UltiObserver on your watch in Google Play.\n\nPhone: 1.5.0\nWatch: 1.4.0"
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Home").assertDoesNotExist()
        composeRule.onNodeWithText("Retry").performClick()
        assertTrue(retryRequested.get())

        // The opposite mismatch specifically identifies the phone as needing the update.
        composeRule.runOnIdle {
            connection = ConnectionState.UpdateRequired(
                updatePhone = true, phoneVersion = "1.4.0", watchVersion = "1.5.0",
            )
        }
        composeRule.onNodeWithText(
            "Update UltiObserver on your phone in Google Play.\n\nPhone: 1.4.0\nWatch: 1.5.0"
        ).assertIsDisplayed()
    }

    /** Test a connected phone with Wear OS disabled, then idle and disconnected states. */
    @Test
    fun disabledIdleAndDisconnectedPhoneStates() {
        val retryRequested = AtomicBoolean(false)
        var state by mutableStateOf<ReceivedState?>(null)
        var connection by mutableStateOf<ConnectionState>(ConnectionState.DISABLED)
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
                redoAvailable = true,
                timingControls = WearTimingControlsSnapshot(
                    adjustments = listOf(WearCountdownAction.RESUME),
                    pointAction = WearCountdownAction.START_POINT,
                    secondaryAction = WearCountdownAction.RESTART_PULL,
                ),
                countdown = WearCountdownSnapshot(
                    label = "Between points",
                    targetEpochMillis = now + 45_000L,
                    pausedAtEpochMillis = now,
                    cues = emptyList(),
                ),
            ),
        )))
        var connection by mutableStateOf<ConnectionState>(ConnectionState.CONNECTED)
        show(
            state = { state },
            connection = { connection },
            onRetry = { retryRequested.set(true) },
        )

        // The countdown is visible before losing the connection.
        composeRule.onNodeWithText("0:45").assertIsDisplayed()
        composeRule.onNodeWithText("Redo").assertIsDisplayed()

        // Losing the phone while its team actions are open must dismiss that surface, not leave
        // an enabled Goal button acting on stale state.
        composeRule.onNodeWithText("Home").performClick()
        composeRule.onNodeWithText("Goal").assertIsDisplayed()
        composeRule.runOnIdle { connection = ConnectionState.DISCONNECTED }

        // If the connection is lost, but the phone had previously been connected, then
        // let the user know about the lost connection.
        composeRule.onNodeWithText("Lost connection").assertIsDisplayed()
        composeRule.onNodeWithText("Redo").assertDoesNotExist()

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
        composeRule.onNodeWithText("Redo").assertIsDisplayed()

        // Losing the connection with timing controls open also returns to the disabled scores.
        composeRule.onNodeWithContentDescription("Countdown controls").performClick()
        composeRule.onNodeWithText("Start point").assertIsEnabled()
        composeRule.runOnIdle { connection = ConnectionState.DISCONNECTED }
        composeRule.onNodeWithText("Start point").assertDoesNotExist()
        composeRule.onNodeWithText("Home").assertIsNotEnabled()
        composeRule.onNodeWithText("Redo").assertDoesNotExist()

        // Leaving the phone's active game also closes timing controls on a connected watch.
        composeRule.runOnIdle { connection = ConnectionState.CONNECTED }
        composeRule.onNodeWithContentDescription("Countdown controls").performClick()
        composeRule.onNodeWithText("Start point").assertIsEnabled()
        composeRule.runOnIdle {
            state = state.copy(snapshot = state.snapshot.copy(
                activeGame = state.snapshot.activeGame!!.copy(actionsAvailable = false),
            ))
        }
        composeRule.onNodeWithText("Start point").assertDoesNotExist()
        composeRule.onNodeWithText("Home").assertIsNotEnabled()
        composeRule.onNodeWithText("Redo").assertDoesNotExist()
    }

    /** Release disabled action controls after a rejected request and newer phone state. */
    @Test
    fun phoneChangesDuringRequests() {
        var state by mutableStateOf(activeSnapshot())
        var isAmbient by mutableStateOf(false)
        var finishGoal: ((Boolean) -> Unit)? = null
        composeRule.setContent {
            UltiObserverWearApp(
                isAmbient = isAmbient,
                receivedState = state,
                connectionState = ConnectionState.CONNECTED,
                onRetry = {},
                onGoal = { _, _, finished -> finishGoal = finished },
                onUndo = { _, _ -> },
                onRedo = { _, _ -> },
                onCountdownAction = { _, _, _ -> },
                onDecision = { _, _, _ -> },
                onTeamAction = { _, _, _, _ -> },
                onStartCardEntry = { _, _, _, _, _ -> },
                onCancelCardEntry = { _, _, _, _, _ -> },
                onConfirmAction = { _, _ -> },
            )
        }

        // The watch goal is in flight when the phone scores. The controller reports rejection
        // before exposing that newer snapshot. The watch keeps the phone's score and releases
        // its pending controls so the observer can choose another action.
        composeRule.onNodeWithText("Home").performClick()
        composeRule.onNodeWithText("Goal").performClick()
        composeRule.onNodeWithText("Goal").assertIsNotEnabled()
        assertNotNull(finishGoal)

        // Sleeping removes every control, but waking preserves the pending request and screen.
        composeRule.runOnIdle { isAmbient = true }
        composeRule.onNodeWithText("Goal").assertDoesNotExist()
        composeRule.runOnIdle { isAmbient = false }
        composeRule.onNodeWithText("Goal").assertIsNotEnabled()
        composeRule.runOnIdle {
            finishGoal!!(false)
            state = state.copy(snapshot = state.snapshot.copy(
                activeGame = state.snapshot.activeGame!!.copy(
                    stateToken = "phone-goal",
                    teamOne = state.snapshot.activeGame!!.teamOne.copy(score = 4),
                ),
            ))
        }
        composeRule.onNodeWithText("Goal").assertIsEnabled()
        composeRule.onNodeWithText("Timeout (2)").assertIsEnabled()
    }

    /** Prevent repeated cancellation while the phone is still closing its card-entry workflow. */
    @Test
    fun pendingPhoneCancellation() {
        val initial = activeSnapshot()
        var state by mutableStateOf(initial.copy(snapshot = initial.snapshot.copy(
            activeGame = initial.snapshot.activeGame!!.copy(
                phoneCardEntry = WearPhoneCardEntrySnapshot(TeamId.TEAM_ONE, CardType.YELLOW, "12"),
            ),
        )))
        var cancellations = 0
        var finishCancellation: ((Boolean) -> Unit)? = null
        composeRule.setContent {
            UltiObserverWearApp(
                isAmbient = isAmbient,
                receivedState = state,
                connectionState = ConnectionState.CONNECTED,
                onRetry = {},
                onGoal = { _, _, _ -> },
                onUndo = { _, _ -> },
                onRedo = { _, _ -> },
                onCountdownAction = { _, _, _ -> },
                onDecision = { _, _, _ -> },
                onTeamAction = { _, _, _, _ -> },
                onStartCardEntry = { _, _, _, _, _ -> },
                onCancelCardEntry = { _, _, _, _, finished ->
                    cancellations++
                    finishCancellation = finished
                },
                onConfirmAction = { _, _ -> },
            )
        }

        // Cancel submits one request and disables the control while the phone is answering.
        composeRule.onNodeWithText("Continue on phone").assertIsDisplayed()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithText("Cancel").assertIsNotEnabled().performClick()
        composeRule.runOnIdle { assertEquals(1, cancellations) }

        // Sleeping and waking preserve the pending cancellation and its disabled control.
        composeRule.runOnIdle { isAmbient = true }
        composeRule.onNodeWithText("Cancel").assertDoesNotExist()
        composeRule.runOnIdle { isAmbient = false }
        composeRule.onNodeWithText("Cancel").assertIsNotEnabled()
        composeRule.runOnIdle { assertEquals(1, cancellations) }

        // A rejected cancellation allows a new attempt instead of leaving the screen disabled.
        composeRule.runOnIdle { finishCancellation!!(false) }
        composeRule.onNodeWithText("Cancel").assertIsEnabled()

        // A successful retry closes the phone workflow and restores the watch's card entry.
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.runOnIdle {
            finishCancellation!!(true)
            state = state.copy(snapshot = state.snapshot.copy(
                activeGame = state.snapshot.activeGame!!.copy(phoneCardEntry = null),
            ))
        }
        composeRule.onNodeWithText("Continue on phone").assertDoesNotExist()
        composeRule.onNodeWithText("12").assertIsDisplayed()
        composeRule.runOnIdle { assertEquals(2, cancellations) }
    }

    /** Exercise notification updates when a current game is available or absent. */
    @Test
    fun ongoingGameNotification() {
        val notifications = mock(NotificationManager::class.java)
        doReturn(true).`when`(notifications).areNotificationsEnabled()
        val context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
            override fun getSystemService(name: String): Any? = if (name == Context.NOTIFICATION_SERVICE) {
                notifications
            } else {
                super.getSystemService(name)
            }
        }
        // An unrelated notification must not suppress the game's shortcut.
        val otherId = 99
        val other = mock(StatusBarNotification::class.java)
        doReturn(otherId).`when`(other).id
        doReturn(arrayOf(other)).`when`(notifications).activeNotifications
        context.updateOngoingGame(hasCurrentGame = true)
        val posted = ArgumentCaptor.forClass(Notification::class.java)
        verify(notifications).notify(eq(ONGOING_GAME_NOTIFICATION_ID), posted.capture())
        assertEquals("Return to game", posted.value.extras.getString(Notification.EXTRA_TEXT))

        // Repeated checks retain the existing notification.
        val ongoing = mock(StatusBarNotification::class.java)
        doReturn(ONGOING_GAME_NOTIFICATION_ID).`when`(ongoing).id
        doReturn(arrayOf(other, ongoing)).`when`(notifications).activeNotifications
        context.updateOngoingGame(hasCurrentGame = true)
        context.updateOngoingGame(hasCurrentGame = true)
        verify(notifications, times(1)).notify(eq(ONGOING_GAME_NOTIFICATION_ID), any(Notification::class.java))

        // Clearing the current game removes only its shortcut and is safe to repeat.
        context.updateOngoingGame(hasCurrentGame = false)
        context.updateOngoingGame(hasCurrentGame = false)
        verify(notifications, times(2)).cancel(ONGOING_GAME_NOTIFICATION_ID)
        verify(notifications, never()).cancel(otherId)
    }

    /** The background listener creates and removes the shortcut using only its small status item. */
    @Test
    fun ongoingGameUpdates() {
        val notifications = mock(NotificationManager::class.java)
        doReturn(true).`when`(notifications).areNotificationsEnabled()
        doReturn(emptyArray<StatusBarNotification>()).`when`(notifications).activeNotifications
        val context = object : ContextWrapper(InstrumentationRegistry.getInstrumentation().targetContext) {
            override fun getSystemService(name: String): Any? = if (name == Context.NOTIFICATION_SERVICE) {
                notifications
            } else {
                super.getSystemService(name)
            }
        }
        val service = OngoingGameService()
        ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java).apply {
            isAccessible = true
        }.invoke(service, context)

        // A current game followed by no current game posts and then cancels the shortcut.
        val events = listOf(true, false).map { hasCurrentGame ->
            val item = mock(DataItem::class.java)
            doReturn(WearProtocolCodec.encode(
                WearOngoingGameStatus.serializer(), WearOngoingGameStatus(hasCurrentGame),
            )).`when`(item).data
            val event = mock(DataEvent::class.java)
            doReturn(DataEvent.TYPE_CHANGED).`when`(event).type
            doReturn(item).`when`(event).dataItem
            event
        }
        val changes = mock(DataEventBuffer::class.java)
        doReturn(events.iterator()).`when`(changes).iterator()
        service.onDataChanged(changes)
        verify(notifications).notify(eq(ONGOING_GAME_NOTIFICATION_ID), any(Notification::class.java))
        verify(notifications).cancel(ONGOING_GAME_NOTIFICATION_ID)

        // Deleting the status item also clears the shortcut without decoding a payload.
        val deleted = mock(DataEvent::class.java)
        doReturn(DataEvent.TYPE_DELETED).`when`(deleted).type
        val deletions = mock(DataEventBuffer::class.java)
        doReturn(listOf(deleted).iterator()).`when`(deletions).iterator()
        service.onDataChanged(deletions)
        verify(notifications, times(2)).cancel(ONGOING_GAME_NOTIFICATION_ID)
    }

    /** Exercise the vibration service's acceptance response and request-path validation. */
    @Test
    fun vibrationRequest() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val service = VibrationService()
        // Attach the real watch context without starting a Data Layer connection or an Activity.
        ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java).apply {
            isAccessible = true
        }.invoke(service, context)
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(VibratorManager::class.java).defaultVibrator
        } else {
            context.getSystemService(Vibrator::class.java)
        }
        try {
            // The service replies according to actual hardware availability on this watch.
            val request = WearProtocolCodec.encode(WearVibrationRequest.serializer(), WearVibrationRequest(VibrationPattern(420L, 3, 120L)))
            val bytes = Tasks.await(service.onRequest("phone", WATCH_VIBRATION_PATH, request), 5, TimeUnit.SECONDS)
            val response = WearProtocolCodec.decode(WearVibrationResponse.serializer(), bytes)
            assertEquals(vibrator.hasVibrator(), response.accepted)

            // Other request paths violate the external service contract and never start a pulse.
            assertThrows(IllegalArgumentException::class.java) {
                service.onRequest("phone", "/unknown", request)
            }
        } finally {
            vibrator.cancel()
        }

        // A watch without vibration hardware rejects the cue so the phone can handle it instead.
        val missingVibrator = mock(Vibrator::class.java)
        doReturn(false).`when`(missingVibrator).hasVibrator()
        val missingVibrationHardware = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            mock(VibratorManager::class.java).also {
                doReturn(missingVibrator).`when`(it).defaultVibrator
            }
        } else {
            missingVibrator
        }
        val noVibratorContext = object : ContextWrapper(context) {
            override fun getSystemService(name: String): Any? = when (name) {
                Context.VIBRATOR_SERVICE, Context.VIBRATOR_MANAGER_SERVICE -> missingVibrationHardware
                else -> super.getSystemService(name)
            }
        }
        val noVibratorService = VibrationService()
        ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java).apply {
            isAccessible = true
        }.invoke(noVibratorService, noVibratorContext)
        val request = WearProtocolCodec.encode(WearVibrationRequest.serializer(), WearVibrationRequest(VibrationPattern(420L, 3, 120L)))
        val bytes = Tasks.await(
            noVibratorService.onRequest("phone", WATCH_VIBRATION_PATH, request), 5, TimeUnit.SECONDS,
        )
        val response = WearProtocolCodec.decode(WearVibrationResponse.serializer(), bytes)
        assertFalse(response.accepted)

        // With hardware available, submit the entire sequence in one native vibration call.
        val availableVibrator = mock(Vibrator::class.java)
        doReturn(true).`when`(availableVibrator).hasVibrator()
        val availableVibrationHardware = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            mock(VibratorManager::class.java).also {
                doReturn(availableVibrator).`when`(it).defaultVibrator
            }
        } else {
            availableVibrator
        }
        val availableVibratorContext = object : ContextWrapper(context) {
            override fun getSystemService(name: String): Any? = when (name) {
                Context.VIBRATOR_SERVICE, Context.VIBRATOR_MANAGER_SERVICE -> availableVibrationHardware
                else -> super.getSystemService(name)
            }
        }
        val availableVibratorService = VibrationService()
        ContextWrapper::class.java.getDeclaredMethod("attachBaseContext", Context::class.java).apply {
            isAccessible = true
        }.invoke(availableVibratorService, availableVibratorContext)
        val acceptedBytes = Tasks.await(
            availableVibratorService.onRequest("phone", WATCH_VIBRATION_PATH, request), 5, TimeUnit.SECONDS,
        )
        assertTrue(WearProtocolCodec.decode(WearVibrationResponse.serializer(), acceptedBytes).accepted)
        val effect = VibrationEffect.createWaveform(longArrayOf(0L, 420L, 120L, 420L, 120L, 420L), -1)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            verify(availableVibrator).vibrate(
                effect, VibrationAttributes.createForUsage(VibrationAttributes.USAGE_ALARM),
            )
        } else {
            verify(availableVibrator).vibrate(
                effect,
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build(),
            )
        }
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
                    leftTeam = TeamId.TEAM_ONE,
                    stateToken = "playing",
                    actionsAvailable = true,
                    officialClockOffsetMillis = 0,
                    officialTimeZoneId = "UTC",
                    rulesReference = listOf(WearRulesReferenceItemSnapshot("Game to", "15", false)),
                    countdownActions = emptyList(),
                    timingControls = null,
                    countdown = null,
                    teamOne = WearTeamSnapshot("Home", 3, "Far end", 0xFFFFFFFF, 0xFF000000, actions, emptyList()),
                    teamTwo = WearTeamSnapshot("Away", 2, "Near end", 0xFF000000, 0xFFFFFFFF, actions, emptyList()),
                    pullDirection = WearSnapshotPullDirection.LEFT_TO_RIGHT,
                    ratio = null,
                    ratioChooser = null,
                    redoAvailable = false,
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
                isAmbient = isAmbient,
                receivedState = state(),
                connectionState = connection(),
                onRetry = onRetry,
                onGoal = { _, _, _ -> submittedCommands++ },
                onUndo = { _, _ -> submittedCommands++ },
                onRedo = { _, _ -> submittedCommands++ },
                onCountdownAction = { _, _, _ -> submittedCommands++ },
                onDecision = { _, _, _ -> submittedCommands++ },
                onTeamAction = { _, _, _, _ -> submittedCommands++ },
                onStartCardEntry = { _, _, _, _, _ -> submittedCommands++ },
                onCancelCardEntry = { _, _, _, _, _ -> submittedCommands++ },
                onConfirmAction = { _, _ -> submittedCommands++ },
            )
        }
    }
}
