package rmjarvis.ultiobserver

import android.Manifest
import android.app.NotificationManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle

/** Launcher activity for the Wear companion app. */
class WatchActivity : ComponentActivity() {
    private var receivedState by mutableStateOf<ReceivedState?>(null)
    private var connectionState by mutableStateOf(ConnectionState.CONNECTING)
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        receivedState?.let { state ->
            updateOngoingGame(state.snapshot)
        }
    }
    private val stateClient by lazy {
        StateClient(
            context = applicationContext,
            onStateReceived = { state ->
                receivedState = state
                updateGameShortcut()
            },
            onConnectionStateChanged = { state ->
                connectionState = state
            },
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UltiObserverWearApp(
                receivedState = receivedState,
                connectionState = connectionState,
                onRetry = {
                    stateClient.connection.retry()
                },
                onGoal = { scoringTeam, stateToken, onFinished ->
                    stateClient.connection.recordGoal(scoringTeam, stateToken, onFinished)
                },
                onCountdownAction = { stateToken, action, onFinished ->
                    stateClient.connection.countdownAction(stateToken, action, onFinished)
                },
                onUndo = { stateToken, onFinished ->
                    stateClient.connection.undo(stateToken, onFinished)
                },
                onRedo = { stateToken, onFinished ->
                    stateClient.connection.redo(stateToken, onFinished)
                },
                onDecision = { stateToken, accept, onFinished ->
                    stateClient.connection.resolveDecision(stateToken, accept, onFinished)
                },
                onTeamAction = { team, stateToken, action, onFinished ->
                    stateClient.connection.requestTeamAction(team, stateToken, action, onFinished)
                },
                onStartCardEntry = { team, stateToken, cardType, jerseyNumber, onFinished ->
                    stateClient.connection.startCardEntry(
                        team,
                        stateToken,
                        cardType,
                        jerseyNumber,
                        onFinished,
                    )
                },
                onCancelCardEntry = { team, stateToken, cardType, jerseyNumber, onFinished ->
                    stateClient.connection.cancelCardEntry(
                        team,
                        stateToken,
                        cardType,
                        jerseyNumber,
                        onFinished,
                    )
                },
                onConfirmAction = { confirmation, onFinished ->
                    stateClient.connection.confirmAction(confirmation, onFinished)
                },
            )
        }
    }

    override fun onStart() {
        super.onStart()
        receivedState = null
        connectionState = ConnectionState.CONNECTING
        stateClient.start()
    }

    override fun onResume() {
        super.onResume()
        updateGameShortcut()
    }

    /** Ask once for the notification permission needed by the ongoing-game shortcut. */
    private fun updateGameShortcut() {
        val snapshot = receivedState?.snapshot ?: return
        updateOngoingGame(snapshot)
        if (snapshot.activeGame != null &&
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) &&
            !getSystemService(NotificationManager::class.java).areNotificationsEnabled()
        ) {
            val preferences = getPreferences(MODE_PRIVATE)
            if (!preferences.getBoolean("notification_permission_requested", false)) {
                preferences.edit().putBoolean("notification_permission_requested", true).apply()
                notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
    }

    override fun onStop() {
        stateClient.stop()
        super.onStop()
    }
}
