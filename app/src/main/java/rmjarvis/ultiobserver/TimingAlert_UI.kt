package rmjarvis.ultiobserver

import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.core.content.ContextCompat

/**
 * Keep the timing-alert service synchronized with Compose app state.
 *
 * This side effect is the only bridge from the Compose tree into the Android service. Each relevant
 * state change sends a fresh ACTION_UPDATE Intent. When there is no active game, or alerts are off,
 * the same effect stops the service so wake locks and cap alarms are released.
 *
 * @param liveState Active game state whose alerts should be delivered, or null to stop service.
 * @param settings Current app settings controlling phone alerts and watch notifications.
 */
@Composable
internal fun TimingAlertForegroundServiceEffect(
    liveState: GameState?,
    settings: Settings,
) {
    val context = androidx.compose.ui.platform.LocalContext.current.applicationContext
    LaunchedEffect(liveState, settings) {
        if (liveState == null ||
            (settings.timingAlerts.globalMode == TimingAlertGlobalMode.OFF &&
                settings.timingAlerts.watchNotificationMode == WatchNotificationMode.OFF)
        ) {
            context.stopService(Intent(context, TimingAlertForegroundService::class.java))
            return@LaunchedEffect
        }
        ContextCompat.startForegroundService(
            context,
            TimingAlertForegroundService.updateIntent(
                context = context,
                liveState = liveState,
                settings = settings,
            ),
        )
    }
}
