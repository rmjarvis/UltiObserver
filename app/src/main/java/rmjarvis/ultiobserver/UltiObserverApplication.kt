package rmjarvis.ultiobserver

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

/**
 * Process-wide owner of state and Android resources shared by UltiObserver components.
 *
 * The app state lets the Activity and Wear request service serialize game actions
 * through one authoritative path. The timing-alert player is also shared between sound previews
 * and the foreground service to avoid overlapping SoundPool creation and release operations.
 */
class UltiObserverApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    internal val soundOutputs by lazy { SoundOutputManager(this) }

    override fun onCreate() {
        super.onCreate()
        // Reconcile a remembered output even before the first sound or dialog is used.
        soundOutputs.refresh()
        applicationScope.launch {
            // Keep the watch's vibration behavior consistent with the global mute option.
            // Coverage: completion not expected
            soundOutputs.state.map { it.vibrationsMuted }.distinctUntilChanged().collect {
                wearCoordinator.publishCurrentState()
            }
        }
    }

    internal val appState by lazy {
        AppState(FileAppStateStorage(filesDir))
    }

    internal val wearCoordinator by lazy {
        val publisher = WearStatePublisher(this)
        WearPhoneCoordinator(
            appState = appState,
            publish = { update -> publisher.publish(update) },
            publishOngoingGame = { status -> publisher.publishOngoingGame(status) },
            clock = { System.currentTimeMillis() },
            vibrationsMuted = { soundOutputs.state.value.vibrationsMuted },
        )
    }

    internal val timingAlertPlayer by lazy {
        TimingAlertPlayer(this)
    }

    private val wearVibrationSender by lazy { WearVibrationSender(this) }

    /** Deliver a complete vibration pattern to the currently selected vibration destination. */
    internal suspend fun vibrateTimingCue(pattern: VibrationPattern) {
        if (!soundOutputs.state.value.vibrationsMuted) {
            deliverTimingVibration(
                preferences = appState.settings.timingAlerts,
                pattern = pattern,
                vibrateOnWatch = { wearVibrationSender.vibrate(it) },
                vibrateOnPhone = { performTimingCueHaptic(it) },
            )
        }
    }
}
