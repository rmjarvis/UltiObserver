package rmjarvis.ultiobserver

import android.os.Bundle
import androidx.activity.compose.BackHandler
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** Launcher activity for the Wear companion app. */
internal class MainActivity : ComponentActivity() {
    private var receivedState by mutableStateOf<ReceivedState?>(null)
    private var connectionState by mutableStateOf(ConnectionState.CONNECTING)
    private val stateClient by lazy {
        StateClient(
            context = applicationContext,
            onStateReceived = { state ->
                receivedState = state
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
                    stateClient.retry()
                },
                onGoal = { scoringTeam, stateToken, onFinished ->
                    stateClient.recordGoal(scoringTeam, stateToken, onFinished)
                },
                onDecision = { stateToken, accept, onFinished ->
                    stateClient.resolveDecision(stateToken, accept, onFinished)
                },
                onTimeout = { team, stateToken, onFinished ->
                    stateClient.previewTimeout(team, stateToken, onFinished)
                },
                onConfirmAction = { confirmation, onFinished ->
                    stateClient.confirmAction(confirmation, onFinished)
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

    override fun onStop() {
        stateClient.stop()
        super.onStop()
    }
}
