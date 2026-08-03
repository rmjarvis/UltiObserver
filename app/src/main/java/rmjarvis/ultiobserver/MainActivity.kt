package rmjarvis.ultiobserver

import android.content.pm.ActivityInfo
import android.database.ContentObserver
import android.hardware.display.DisplayManager
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.OrientationEventListener
import android.view.Surface
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.firebase.crashlytics.FirebaseCrashlytics
import kotlinx.coroutines.launch
import rmjarvis.ultiobserver.ui.theme.UltiObserverTheme

/// Android Activity entry point for the Compose app.
class MainActivity : ComponentActivity() {
    internal val appViewModel: AppViewModel by viewModels { appViewModelFactory(filesDir) }
    private val autoRotateOrientationLock = AutoRotateOrientationLock()
    private var autoRotateScreenActive = false
    private var systemAutoRotateEnabled = false
    private var lastRequestedOrientation: Int? = null
    private var lastUsesDisplayCutout: Boolean? = null
    private var displayOrientation by mutableStateOf(ActiveGameFullOrientation.PORTRAIT)

    private val orientationEventListener by lazy {
        object : OrientationEventListener(this, SensorManager.SENSOR_DELAY_NORMAL) {
            override fun onOrientationChanged(orientation: Int) {
                heldOrientation(orientation)?.let { heldOrientation ->
                    // Returning to an active game can beat the listener's first sensor sample.
                    // In that case entry leaves the orientation unlocked, and this first usable
                    // sample completes the lock rather than allowing later motion to rotate it.
                    if (autoRotateOrientationLock.recordHeldOrientation(
                            heldOrientation = heldOrientation,
                            autoRotateScreenActive = autoRotateScreenActive,
                            systemAutoRotateEnabled = systemAutoRotateEnabled,
                        )
                    ) {
                        applyRequestedActivityOrientation(appViewModel.state.value)
                    }
                }
            }
        }
    }

    private val displayManager by lazy { getSystemService(DisplayManager::class.java) }

    private val displayListener = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(displayId: Int) = Unit

        override fun onDisplayRemoved(displayId: Int) = Unit

        override fun onDisplayChanged(displayId: Int) {
            if (displayId == display?.displayId) {
                displayOrientation = currentDisplayOrientation()
                applyRequestedActivityOrientation(appViewModel.state.value)
            }
        }
    }

    private val autoRotateSettingObserver by lazy {
        object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                updateSystemAutoRotateSetting()
            }
        }
    }

    /**
     * Initialize edge-to-edge Compose content for the app.
     *
     * @param savedInstanceState Android activity state supplied during recreation.
     */
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val previousRunCrashed = FirebaseCrashlytics.getInstance().didCrashOnPreviousExecution()
        enableEdgeToEdge()
        systemAutoRotateEnabled = readSystemAutoRotateSetting()
        // This bit keeps the phone in the correct orientation to match the appViewModel's
        // intended rendering orientation.
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                appViewModel.state
                    .collect { state ->
                        val newAutoRotateScreenActive = state.viewingActiveGameScreen &&
                            state.settings.orientationPreference ==
                            OrientationPreference.AUTO_ROTATE
                        if (newAutoRotateScreenActive && !autoRotateScreenActive) {
                            // Entering active game with auto-rotate enabled.
                            // Apply the right orientation, maybe fixing the orientation to
                            // the current held orientation, depending on the system setting.
                            autoRotateOrientationLock.enterActiveGame(systemAutoRotateEnabled)
                        } else if (!newAutoRotateScreenActive) {
                            // Leaving active game -- clear any locked held orientation.
                            autoRotateOrientationLock.leaveActiveGame()
                        }
                        autoRotateScreenActive = newAutoRotateScreenActive
                        applyRequestedActivityOrientation(state)
                    }
            }
        }
        setContent {
            UltiObserverTheme(dynamicColor = false) {
                UltiObserverApp(
                    viewModel = appViewModel,
                    previousRunCrashed = previousRunCrashed,
                    displayOrientation = displayOrientation,
                )
            }
        }
    }

    /// Begin observing physical orientation and the Android auto-rotate setting.
    override fun onStart() {
        super.onStart()
        orientationEventListener.enable()
        displayOrientation = currentDisplayOrientation()
        displayManager.registerDisplayListener(displayListener, null)
        contentResolver.registerContentObserver(
            Settings.System.getUriFor(Settings.System.ACCELEROMETER_ROTATION),
            false,
            autoRotateSettingObserver,
        )
        updateSystemAutoRotateSetting()
    }

    /// Stop orientation observation while this activity is not visible.
    override fun onStop() {
        contentResolver.unregisterContentObserver(autoRotateSettingObserver)
        displayManager.unregisterDisplayListener(displayListener)
        orientationEventListener.disable()
        super.onStop()
    }

    /// Read whether Android currently allows sensor-driven display rotation.
    private fun readSystemAutoRotateSetting(): Boolean {
        return Settings.System.getInt(
            contentResolver,
            Settings.System.ACCELEROMETER_ROTATION,
            0,
        ) == 1
    }

    /// Apply a changed Android auto-rotate setting to an active Auto-rotate session.
    private fun updateSystemAutoRotateSetting() {
        val enabled = readSystemAutoRotateSetting()
        if (autoRotateScreenActive && enabled != systemAutoRotateEnabled) {
            autoRotateOrientationLock.systemAutoRotateChanged(
                enabled = enabled,
                currentDisplayOrientation = currentDisplayOrientation(),
            )
        }
        systemAutoRotateEnabled = enabled
        applyRequestedActivityOrientation(appViewModel.state.value)
    }

    /// Request the orientation required by the current screen and active-game preference.
    private fun applyRequestedActivityOrientation(state: AppUiState) {
        val activeGameVisible = state.viewingActiveGameScreen
        val orientation = if (!activeGameVisible) {
            ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        } else {
            when (state.settings.orientationPreference) {
                OrientationPreference.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
                OrientationPreference.LANDSCAPE -> {
                    ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                }
                OrientationPreference.AUTO_ROTATE -> if (systemAutoRotateEnabled) {
                    ActivityInfo.SCREEN_ORIENTATION_FULL_USER
                } else {
                    val lockedOrientation = autoRotateOrientationLock.lockedOrientation
                        ?: autoRotateOrientationLock.latestHeldOrientation
                        ?: currentDisplayOrientation()
                    lockedOrientation.requestedActivityOrientation
                }
            }
        }
        val usesDisplayCutout = activeGameVisible && when (
            state.settings.orientationPreference
        ) {
            OrientationPreference.PORTRAIT -> false
            OrientationPreference.LANDSCAPE -> true
            OrientationPreference.AUTO_ROTATE -> {
                displayOrientation.orientation == ActiveGameOrientation.LANDSCAPE
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P &&
            usesDisplayCutout != lastUsesDisplayCutout
        ) {
            // This bit just lets us color the area behind the camera our normal background
            // color, so it doesn't look like a harsh edge there.
            window.attributes = window.attributes.apply {
                layoutInDisplayCutoutMode = if (usesDisplayCutout) {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES
                } else {
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_DEFAULT
                }
            }
            lastUsesDisplayCutout = usesDisplayCutout
        }
        if (orientation != lastRequestedOrientation) {
            requestedOrientation = orientation
            lastRequestedOrientation = orientation
        }
    }

    /// Return the readable orientation currently shown by this activity's display.
    private fun currentDisplayOrientation(): ActiveGameFullOrientation {
        return displayOrientation(window.decorView.display?.rotation ?: Surface.ROTATION_0)
    }
}

/** Track the held and fixed orientations used while Android auto-rotate is disabled. */
internal class AutoRotateOrientationLock {
    var latestHeldOrientation: ActiveGameFullOrientation? = null
        private set
    var lockedOrientation: ActiveGameFullOrientation? = null
        private set

    /// Lock entry to the latest sensor sample, if Android auto-rotate is disabled.
    fun enterActiveGame(systemAutoRotateEnabled: Boolean) {
        lockedOrientation = if (systemAutoRotateEnabled) null else latestHeldOrientation
    }

    /// Clear the session lock after leaving the active-game screen.
    fun leaveActiveGame() {
        lockedOrientation = null
    }

    /**
     * Record a usable sensor sample and complete a pending entry lock when necessary.
     *
     * @return Whether this sample established the fixed display orientation.
     */
    fun recordHeldOrientation(
        heldOrientation: ActiveGameFullOrientation,
        autoRotateScreenActive: Boolean,
        systemAutoRotateEnabled: Boolean,
    ): Boolean {
        latestHeldOrientation = heldOrientation
        if (autoRotateScreenActive &&
            !systemAutoRotateEnabled &&
            lockedOrientation == null
        ) {
            lockedOrientation = heldOrientation
            return true
        }
        return false
    }

    /// Follow Android when enabled, or freeze the currently rendered orientation when disabled.
    fun systemAutoRotateChanged(
        enabled: Boolean,
        currentDisplayOrientation: ActiveGameFullOrientation,
    ) {
        lockedOrientation = if (enabled) null else currentDisplayOrientation
    }
}

/// Convert Android's rendered-display rotation into the corresponding readable orientation.
private fun displayOrientation(rotation: Int): ActiveGameFullOrientation {
    return when (rotation) {
        Surface.ROTATION_0 -> ActiveGameFullOrientation.PORTRAIT
        Surface.ROTATION_90 -> {
            ActiveGameFullOrientation.LANDSCAPE_PHONE_TOP_AT_LEFT
        }
        Surface.ROTATION_180 -> ActiveGameFullOrientation.REVERSE_PORTRAIT
        Surface.ROTATION_270 -> {
            ActiveGameFullOrientation.LANDSCAPE_PHONE_TOP_AT_RIGHT
        }
        else -> error("Unexpected display rotation: $rotation")
    }
}

private val heldOrientationsByQuadrant = arrayOf(
    ActiveGameFullOrientation.PORTRAIT,
    ActiveGameFullOrientation.LANDSCAPE_PHONE_TOP_AT_RIGHT,
    ActiveGameFullOrientation.REVERSE_PORTRAIT,
    ActiveGameFullOrientation.LANDSCAPE_PHONE_TOP_AT_LEFT,
)

/// Convert sensor degrees into the readable orientation required when rotation is locked.
private fun heldOrientation(degrees: Int): ActiveGameFullOrientation? {
    if (degrees == OrientationEventListener.ORIENTATION_UNKNOWN) {
        return null
    }
    return heldOrientationsByQuadrant[((degrees + 45) / 90) % 4]
}

/// Return the fixed Android request matching this readable orientation.
private val ActiveGameFullOrientation.requestedActivityOrientation: Int
    get() = when (this) {
        ActiveGameFullOrientation.PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
        ActiveGameFullOrientation.LANDSCAPE_PHONE_TOP_AT_LEFT -> {
            ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
        }
        ActiveGameFullOrientation.REVERSE_PORTRAIT -> {
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_PORTRAIT
        }
        ActiveGameFullOrientation.LANDSCAPE_PHONE_TOP_AT_RIGHT -> {
            ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE
        }
    }
