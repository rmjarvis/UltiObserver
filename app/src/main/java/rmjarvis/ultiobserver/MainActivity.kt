package rmjarvis.ultiobserver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import rmjarvis.ultiobserver.ui.theme.UltiObserverTheme

/// Android Activity entry point for the Compose app.
class MainActivity : ComponentActivity() {
    internal val appViewModel: AppViewModel by viewModels { appViewModelFactory(filesDir) }

    /**
     * Initialize edge-to-edge Compose content for the app.
     *
     * @param savedInstanceState Android activity state supplied during recreation.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UltiObserverTheme(dynamicColor = false) {
                UltiObserverApp(appViewModel)
            }
        }
    }
}
