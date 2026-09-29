package rmjarvis.ultiobserver

import android.Manifest
import android.app.NotificationManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.IBinder
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.wear.compose.foundation.AmbientMode
import androidx.wear.compose.foundation.rememberAmbientModeManager

/** Launcher activity for the Wear companion app. */
class WatchActivity : ComponentActivity() {
    internal var boundManager by mutableStateOf<GameStateManager?>(null)
        private set
    private val receivedState get() = boundManager?.receivedState
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName, binder: IBinder) {
            val manager = (binder as GameStateManager.ManagerBinder).manager
            manager.onStateUpdated = { updateGameShortcut() }
            boundManager = manager
            updateGameShortcut()
        }

        override fun onServiceDisconnected(name: ComponentName) {
            boundManager = null
        }
    }
    private val notificationPermission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) {
        receivedState?.let { state ->
            updateOngoingGame(state.snapshot)
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        bindService(Intent(this, GameStateManager::class.java), serviceConnection, Context.BIND_AUTO_CREATE)
        setContent {
            val manager = boundManager ?: run {
                // Wait for Android to bind the service before creating the interactive UI.
                BlankScreen()
                return@setContent
            }
            val ambientModeManager = rememberAmbientModeManager()
            UltiObserverWearApp(
                isAmbient = ambientModeManager.currentAmbientMode is AmbientMode.Ambient,
                receivedState = manager.receivedState,
                connectionState = manager.connectionState,
                onRetry = {
                    manager.connection.retry()
                },
                onGoal = { scoringTeam, stateToken, onFinished ->
                    manager.connection.recordGoal(scoringTeam, stateToken, onFinished)
                },
                onCountdownAction = { stateToken, action, onFinished ->
                    manager.connection.countdownAction(stateToken, action, onFinished)
                },
                onUndo = { stateToken, onFinished ->
                    manager.connection.undo(stateToken, onFinished)
                },
                onRedo = { stateToken, onFinished ->
                    manager.connection.redo(stateToken, onFinished)
                },
                onDecision = { stateToken, accept, onFinished ->
                    manager.connection.resolveDecision(stateToken, accept, onFinished)
                },
                onTeamAction = { team, stateToken, action, onFinished ->
                    manager.connection.requestTeamAction(team, stateToken, action, onFinished)
                },
                onStartCardEntry = { team, stateToken, cardType, jerseyNumber, onFinished ->
                    manager.connection.startCardEntry(
                        team,
                        stateToken,
                        cardType,
                        jerseyNumber,
                        onFinished,
                    )
                },
                onCancelCardEntry = { team, stateToken, cardType, jerseyNumber, onFinished ->
                    manager.connection.cancelCardEntry(
                        team,
                        stateToken,
                        cardType,
                        jerseyNumber,
                        onFinished,
                    )
                },
                onConfirmAction = { confirmation, onFinished ->
                    manager.connection.confirmAction(confirmation, onFinished)
                },
            )
        }
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

    override fun onDestroy() {
        boundManager?.onStateUpdated = null
        unbindService(serviceConnection)
        boundManager = null
        super.onDestroy()
    }
}
