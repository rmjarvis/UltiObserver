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

    internal val wearStatePublisher by lazy {
        WearStatePublisher(this)
    }

    internal val timingAlertPlayer by lazy {
        TimingAlertPlayer(this)
    }
}
