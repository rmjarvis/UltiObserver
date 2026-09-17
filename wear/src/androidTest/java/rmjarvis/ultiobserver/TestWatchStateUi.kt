package rmjarvis.ultiobserver

import android.Manifest
import android.app.Notification
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.Vibrator
import android.os.VibratorManager
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.Tasks
import java.util.concurrent.TimeUnit
import androidx.compose.runtime.getValue
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
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertTrue
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
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

/**
 * Tests of the connection status between a watch and the phone.
 *
 * For these, we don't use an actual paired phone. Instead, we manually control the
 * connection and disconnection status so we can check that the watch handles this properly.
 */
class TestWatchStateUi {
    @get:Rule
    val composeRule = createComposeRule()

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
        composeRule.onNodeWithText("Back").performClick()
        composeRule.onNodeWithText("Home").assertIsDisplayed()
    }

    private fun countdownSeconds(): Int {
        val text = composeRule.onNodeWithTag("countdown-value", useUnmergedTree = true)
            .fetchSemanticsNode().config[SemanticsProperties.Text].single().text
        return text.substringAfter(":").toInt()
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
                redoAvailable = true,
                timingControls = WearTimingControlsSnapshot(
                    adjustments = listOf(WearCountdownAction.RESUME),
                    pointAction = WearCountdownAction.START_POINT,
                ),
                countdown = WearCountdownSnapshot(
                    label = "Between points",
                    targetEpochMillis = now + 45_000L,
                    pausedAtEpochMillis = now,
                    cues = emptyList(),
                ),
            ),
        )))
        var connection by mutableStateOf(ConnectionState.CONNECTED)
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
        var finishGoal: ((Boolean) -> Unit)? = null
        composeRule.setContent {
            UltiObserverWearApp(
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

    /** Exercise notification updates for active, completed, and idle states. */
    @Test
    fun ongoingGameNotification() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            instrumentation.uiAutomation.grantRuntimePermission(
                context.packageName, Manifest.permission.POST_NOTIFICATIONS,
            )
        }
        val notifications = context.getSystemService(NotificationManager::class.java)
        // Match our allocated notification ID and the null tag used by notify(id, notification).
        fun gameNotifications() = notifications.activeNotifications.filter {
            it.id == ONGOING_GAME_NOTIFICATION_ID && it.tag == null
        }
        val game = activeSnapshot().snapshot
        notifications.cancel(ONGOING_GAME_NOTIFICATION_ID)
        composeRule.waitUntil {
            gameNotifications().isEmpty()
        }
        try {
            // An unrelated notification must not suppress the game's shortcut.
            val otherId = 99
            val channel = android.app.NotificationChannel(
                "service-test", "Service test", NotificationManager.IMPORTANCE_LOW,
            )
            notifications.createNotificationChannel(channel)
            notifications.notify(otherId, Notification.Builder(context, channel.id)
                .setSmallIcon(R.drawable.ic_ongoing_game).setContentTitle("Other notification").build())
            composeRule.waitUntil { notifications.activeNotifications.any { it.id == otherId } }
            context.updateOngoingGame(game)
            composeRule.waitUntil {
                gameNotifications().isNotEmpty()
            }
            val posted = gameNotifications().single()
            assertEquals("Return to game", posted.notification.extras.getString(Notification.EXTRA_TEXT))

            // Repeated snapshots and the final score retain the existing notification.
            context.updateOngoingGame(game)
            context.updateOngoingGame(game.copy(activeGame = game.activeGame!!.copy(gameOver = true)))
            assertEquals(1, gameNotifications().size)

            // Clearing the current game removes only its shortcut and is safe to repeat.
            val idle = WearStateSnapshot(status = WearSnapshotStatus.NO_ACTIVE_GAME, activeGame = null)
            context.updateOngoingGame(idle)
            composeRule.waitUntil {
                gameNotifications().isEmpty()
            }
            context.updateOngoingGame(idle)
            assertTrue(notifications.activeNotifications.any { it.id == otherId })
        } finally {
            notifications.cancel(ONGOING_GAME_NOTIFICATION_ID)
            notifications.cancel(99)
            notifications.deleteNotificationChannel("service-test")
        }
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
            val request = WearProtocolCodec.encode(WearVibrationRequest.serializer(), WearVibrationRequest(420L))
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
                receivedState = state(),
                connectionState = connection(),
                onRetry = onRetry,
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
}
