package rmjarvis.ultiobserver

import android.content.pm.ActivityInfo
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
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
        val previousRunCrashed = FirebaseCrashlytics.getInstance().didCrashOnPreviousExecution()
        enableEdgeToEdge()
        // This bit keeps the phone in the correct orientation to match the appViewModel's
        // intended rendering orientation.
        // The layoutInDisplayCutoutMode bit just lets us color the area behind the camera
        // our normal background color, so it doesn't look like a harsh edge there.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                appViewModel.state
                    .map { it.requestedActivityOrientation() }
                    .distinctUntilChanged()
                    .collect { orientation ->
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            window.attributes = window.attributes.apply {
                                layoutInDisplayCutoutMode = if (
                                    orientation == ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                                ) {
                                    WindowManager.LayoutParams
                                        .LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                                } else {
                                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                                }
                            }
                        }
                        requestedOrientation = orientation
                    }
            }
        }
        setContent {
            UltiObserverTheme(dynamicColor = false) {
                UltiObserverApp(
                    viewModel = appViewModel,
                    previousRunCrashed = previousRunCrashed,
                )
            }
        }
    }
}

/// Return the Android activity orientation required by the currently visible app surface.
private fun AppUiState.requestedActivityOrientation(): Int {
    val activeGameUsesLandscape = screen == AppScreen.LIVE &&
        !viewingCurrentGameSummary &&
        currentGame?.phase != GamePhase.GAME_OVER &&
        settings.activeGameOrientation == ActiveGameOrientation.LANDSCAPE
    return if (activeGameUsesLandscape) {
        ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
    } else {
        ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
    }
}
