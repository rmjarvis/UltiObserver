package rmjarvis.ultiobserver

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Keep active-game timing cues alive even when the observer leaves the live screen.
 *
 * @param liveState The active current-game state, or null when no audible/vibration cues should run.
 * @param timingAlertPreferences The current timing alert settings.
 */
@Composable
internal fun TimingAlertCueListener(
    liveState: LiveGameState?,
    timingAlertPreferences: TimingAlertPreferences,
) {
    var playedTimingAlertKeys by remember(liveState?.startEpoch) {
        mutableStateOf(emptySet<String>())
    }
    val currentLiveState by rememberUpdatedState(liveState)
    val context = LocalContext.current
    val timingAlertPlayer = remember(context) { TimingAlertPlayer(context) }
    val alertPlaybackScope = rememberCoroutineScope()

    DisposableEffect(timingAlertPlayer) {
        onDispose { timingAlertPlayer.release() }
    }

    LaunchedEffect(liveState?.startEpoch, timingAlertPreferences) {
        while (true) {
            val state = currentLiveState ?: return@LaunchedEffect
            val now = System.currentTimeMillis()
            val dueTimingAlerts = state.dueTimingAlerts(now)
            val unplayedTimingAlerts = dueTimingAlerts.filter { cue ->
                cue.alertKey() !in playedTimingAlertKeys
            }
            if (unplayedTimingAlerts.isNotEmpty()) {
                playTimingAlerts(
                    timingAlerts = unplayedTimingAlerts,
                    timingAlertPreferences = timingAlertPreferences,
                    timingAlertPlayer = timingAlertPlayer,
                    performHaptic = { durationMillis ->
                        context.performTimingCueHaptic(durationMillis)
                    },
                    alertPlaybackScope = alertPlaybackScope,
                    onAlertKeysPlayed = { alertKeys ->
                        playedTimingAlertKeys += alertKeys
                    },
                )
                continue
            }

            val nextTimingAlert = state.nextTimingAlert(now)
            if (nextTimingAlert == null) {
                delay(TIMING_ALERT_SCHEDULE_CHECK_MS)
                continue
            }

            val millisUntilNextAlert = nextTimingAlert.targetEpoch - now
            if (millisUntilNextAlert > TIMING_ALERT_SCHEDULE_CHECK_MS) {
                delay(TIMING_ALERT_SCHEDULE_CHECK_MS)
                continue
            }

            if (millisUntilNextAlert > 0L) {
                delay(millisUntilNextAlert)
            }
            val alertKey = nextTimingAlert.alertKey()
            if (alertKey !in playedTimingAlertKeys) {
                playTimingAlerts(
                    timingAlerts = listOf(nextTimingAlert),
                    timingAlertPreferences = timingAlertPreferences,
                    timingAlertPlayer = timingAlertPlayer,
                    performHaptic = { durationMillis -> context.performTimingCueHaptic(durationMillis) },
                    alertPlaybackScope = alertPlaybackScope,
                    onAlertKeysPlayed = { alertKeys -> playedTimingAlertKeys += alertKeys },
                )
            }
        }
    }
}

/**
 * Play all due timing alerts and mark them as played before async playback starts.
 *
 * @param timingAlerts The timing cues to play.
 * @param timingAlertPreferences The alert settings controlling sound, vibration, and repeat count.
 * @param timingAlertPlayer Sound player used for audible cues.
 * @param performHaptic Callback that performs one haptic pulse.
 * @param alertPlaybackScope Coroutine scope for background playback.
 * @param onAlertKeysPlayed Callback recording cue keys so recomposition does not replay them.
 */
private fun playTimingAlerts(
    timingAlerts: List<TimingCueDisplay>,
    timingAlertPreferences: TimingAlertPreferences,
    timingAlertPlayer: TimingAlertPlayer,
    performHaptic: suspend (Long) -> Unit,
    alertPlaybackScope: CoroutineScope,
    onAlertKeysPlayed: (Set<String>) -> Unit,
) {
    onAlertKeysPlayed(timingAlerts.map { cue -> cue.alertKey() }.toSet())
    alertPlaybackScope.launch(Dispatchers.Default) {
        timingAlerts.forEach { cue ->
            playTimingAlertOnce(
                cue = cue,
                timingAlertPreferences = timingAlertPreferences,
                timingAlertPlayer = timingAlertPlayer,
                performHaptic = performHaptic,
                playedTimingAlertKeys = emptySet(),
                onAlertKeyPlayed = {},
            )
        }
    }
}

private const val TIMING_ALERT_SCHEDULE_CHECK_MS = 250L
