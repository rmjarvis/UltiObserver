package rmjarvis.ultiobserver

import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.SystemClock
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** A bound game session survives a sleeping display until its Activity is destroyed. */
class GameStateManager : Service() {
    internal var receivedState by mutableStateOf<ReceivedState?>(null)
        private set
    internal var connectionState by mutableStateOf<ConnectionState>(ConnectionState.CONNECTING)
        private set
    private val handler = Handler(Looper.getMainLooper())
    internal val countdownWakeLock by lazy {
        getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "UltiObserver:VibrationScheduler")
            .apply { setReferenceCounted(false) }
    }
    private val vibrations by lazy {
        VibrationScheduler(
            clock = { SystemClock.elapsedRealtime() },
            schedule = { delay, action ->
                val runnable = Runnable { action() }
                handler.postDelayed(runnable, delay)
                val cancel = { handler.removeCallbacks(runnable) }
                cancel
            },
            keepAwake = { awake ->
                if (awake) countdownWakeLock.acquire() else countdownWakeLock.release()
            },
            vibrate = { performVibration(it) },
        )
    }
    private val client by lazy {
        PhoneTransport(
            this,
            onStateReceived = {
                receivedState = it
                updateVibrations()
            },
            onConnectionStateChanged = {
                connectionState = it
                updateVibrations()
            },
        )
    }
    internal val connection get() = client.connection

    internal inner class ManagerBinder : Binder() {
        val manager get() = this@GameStateManager
    }

    override fun onCreate() {
        super.onCreate()
        client.start()
    }

    override fun onBind(intent: Intent): IBinder = ManagerBinder()

    private fun updateVibrations() {
        if (connectionState == ConnectionState.CONNECTED) {
            // PhoneConnectionController delivers the initial snapshot before announcing CONNECTED.
            val state = receivedState!!
            vibrations.update(
                state.snapshot.activeGame?.countdown,
                System.currentTimeMillis() + state.phoneClockOffsetMillis,
            )
        } else {
            vibrations.stop()
        }
    }

    override fun onDestroy() {
        client.stop()
        vibrations.stop()
        super.onDestroy()
    }
}
