package rmjarvis.ultiobserver

import android.app.Application

/**
 * Process-wide owner of state and Android resources shared by UltiObserver components.
 *
 * The app state lets the Activity and Wear request service serialize game actions
 * through one authoritative path. The timing-alert player is also shared between sound previews
 * and the foreground service to avoid overlapping SoundPool creation and release operations.
 */
class UltiObserverApplication : Application() {
    internal val appState by lazy {
        AppState(FileAppStateStorage(filesDir))
    }

    internal val wearCoordinator by lazy {
        val publisher = WearStatePublisher(this)
        WearPhoneCoordinator(
            appState = appState,
            publish = { update -> publisher.publish(update) },
            clock = { System.currentTimeMillis() },
        )
    }

    internal val timingAlertPlayer by lazy {
        TimingAlertPlayer(this)
    }

    private val wearVibrationSender by lazy { WearVibrationSender(this) }

    /** Deliver haptic pulse to the currently selected vibration destination. */
    internal suspend fun vibrateTimingCue(durationMillis: Long) {
        deliverTimingVibration(
            preferences = appState.settings.timingAlerts,
            durationMillis = durationMillis,
            vibrateOnWatch = { wearVibrationSender.vibrate(it) },
            vibrateOnPhone = { performTimingCueHaptic(it) },
        )
    }
}
