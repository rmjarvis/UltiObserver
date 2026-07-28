package rmjarvis.ultiobserver

import android.app.Application

/**
 * Process-wide owner of Android resources shared by UltiObserver components.
 *
 * The timing-alert player is created on first use and intentionally remains alive until Android
 * terminates the app process. Sharing it between sound previews and the foreground service avoids
 * overlapping SoundPool creation and asynchronous release operations.
 */
class UltiObserverApplication : Application() {
    internal val timingAlertPlayer by lazy {
        TimingAlertPlayer(this)
    }
}
