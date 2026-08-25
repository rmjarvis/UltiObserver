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
    private var phoneReachable by mutableStateOf(false)
    private val stateClient by lazy {
        StateClient(
            context = applicationContext,
            onStateReceived = { state ->
                receivedState = state
            },
            onPhoneReachabilityChanged = { reachable ->
                phoneReachable = reachable
            },
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            UltiObserverWearApp(
                receivedState = receivedState,
                phoneReachable = phoneReachable,
            )
        }
    }

    override fun onStart() {
        super.onStart()
        stateClient.start()
    }

    override fun onStop() {
        stateClient.stop()
        super.onStop()
    }
}
