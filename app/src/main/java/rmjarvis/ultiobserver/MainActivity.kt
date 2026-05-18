package rmjarvis.ultiobserver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import rmjarvis.ultiobserver.ui.theme.UltiObserverTheme

class MainActivity : ComponentActivity() {
    internal val appViewModel: UltiObserverAppViewModel by viewModels {
        object : ViewModelProvider.Factory {
            /**
             * Create the app ViewModel with file-backed persistence.
             *
             * @param modelClass The ViewModel class requested by the Android lifecycle owner.
             */
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return modelClass.cast(UltiObserverAppViewModel(FileAppStateStore(filesDir)))!!
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
        enableEdgeToEdge()
        setContent {
            UltiObserverTheme(dynamicColor = false) {
                UltiObserverApp(appViewModel)
            }
        }
    }
}

/**
 * Switch between home, setup, and live screens from the app ViewModel state.
 *
 * @param viewModel The app-level ViewModel owning navigation and persisted state.
 */
@Composable
internal fun UltiObserverApp(viewModel: UltiObserverAppViewModel) {
    // Back returns to setup from the initial live preview, otherwise to home.
    BackHandler(enabled = viewModel.screen != AppScreen.HOME) {
        viewModel.goBackFromCurrentScreen()
    }

    TimingAlertCueListener(
        liveState = viewModel.liveState.takeUnless { state -> state?.phase == LivePhase.GAME_OVER },
        timingAlertPreferences = viewModel.timingAlertPreferences,
    )

    // Route to the current top-level screen.
    when (viewModel.screen) {
        AppScreen.HOME -> {
            val liveState = viewModel.liveState
            val currentGame: GameListEntry?
            val completedGamePendingArchive: GameListEntry?
            if (liveState == null) {
                currentGame = if (viewModel.hasSetupDraft) {
                    viewModel.setupState.gameListEntry()
                } else {
                    null
                }
                completedGamePendingArchive = null
            } else if (liveState.phase == LivePhase.GAME_OVER) {
                currentGame = null
                completedGamePendingArchive = liveState.gameListEntry()
            } else {
                currentGame = liveState.gameListEntry()
                completedGamePendingArchive = null
            }
            HomeScreen(
                avatarPreference = viewModel.homeAvatarPreference,
                currentGame = currentGame,
                currentGameSectionSubtitle = viewModel.currentGameHomeSubtitle,
                completedGamePendingArchive = completedGamePendingArchive,
                onResumeCurrentGame = viewModel::resumeCurrentGame,
                onOpenCompletedGame = viewModel::openCompletedGame,
                onArchiveCompletedGame = viewModel::archiveCompletedGame,
                onStartNewGame = viewModel::startNewGame,
                onOpenAbout = viewModel::openAbout,
                onOpenProfile = viewModel::openProfile,
                onOpenSettings = viewModel::openSettings,
                onOpenPreviousGames = viewModel::openPreviousGames,
            )
        }

        AppScreen.ABOUT -> {
            AboutScreen(
                versionName = BuildConfig.VERSION_NAME,
                onBackHome = viewModel::goHome,
            )
        }

        AppScreen.PROFILE -> {
            ProfileScreen(
                name = viewModel.profileName,
                avatarPreference = viewModel.avatarPreference,
                onNameChange = viewModel::updateProfileName,
                onAvatarPreferenceChange = viewModel::updateAvatarPreference,
                onBackHome = viewModel::goHome,
            )
        }

        AppScreen.SETTINGS -> {
            SettingsScreen(
                automaticallyAdvanceCountdowns = viewModel.automaticallyAdvanceCountdowns,
                automaticallyLockLivePoint = viewModel.automaticallyLockLivePoint,
                timingAlertPreferences = viewModel.timingAlertPreferences,
                onAutomaticallyAdvanceCountdownsChange = viewModel::updateAutomaticallyAdvanceCountdowns,
                onAutomaticallyLockLivePointChange = viewModel::updateAutomaticallyLockLivePoint,
                onGlobalModeChange = viewModel::updateTimingAlertGlobalMode,
                onSoundVolumeChange = viewModel::updateTimingAlertSoundVolume,
                onVibrationDurationChange = viewModel::updateTimingAlertVibrationDuration,
                onVibrateWithSoundsChange = viewModel::updateTimingAlertVibrateWithSounds,
                onOpenTimingCueSettings = viewModel::openTimingCueSettings,
                onBackHome = viewModel::goHome,
            )
        }

        AppScreen.TIMING_CUE_SETTINGS -> {
            TimingCueSettingsScreen(
                timingAlertPreferences = viewModel.timingAlertPreferences,
                onTimingCueModeChange = viewModel::updateTimingCueMode,
                onTimingCueRepeatCountChange = viewModel::updateTimingCueRepeatCount,
                onResetTimingCueSettings = viewModel::resetTimingCueSettingsToDefaults,
                onBackSettings = viewModel::openSettings,
            )
        }

        AppScreen.PREVIOUS_GAMES -> {
            PreviousGamesScreen(
                previousGames = viewModel.archivedGames.map { it.state.archivedGameListEntry() },
                onOpenPreviousGame = viewModel::openPreviousGame,
                onDeletePreviousGame = viewModel::deleteArchivedGame,
                onDeleteAllPreviousGames = viewModel::deleteAllArchivedGames,
                onBackHome = viewModel::goHome,
            )
        }

        AppScreen.SETUP -> {
            SetupScreen(
                state = viewModel.setupState,
                onStateChange = viewModel::updateSetup,
                primaryButtonLabel = if (viewModel.setupMode == SetupMode.NEW_GAME) "Start Game" else "Back to Game Screen",
                onPrimaryAction = { viewModel.finishSetup() },
                onBackHome = viewModel::goHome,
            )
        }

        AppScreen.LIVE -> {
            // Archived games reuse the live-game screen, but in a read-only summary mode.
            val currentLiveState = viewModel.currentLiveState!!
            LiveGameScreen(
                state = currentLiveState,
                readOnlySummary = viewModel.viewingReadOnlySummary,
                automaticallyAdvanceCountdowns = viewModel.automaticallyAdvanceCountdowns,
                automaticallyLockLivePoint = viewModel.automaticallyLockLivePoint,
                onStateChange = viewModel::updateLiveGame,
                onUpdateGameSetup = {
                    viewModel.editCurrentGame(currentLiveState)
                },
                onDeleteGame = viewModel::deleteCurrentGame,
                onBackHome = viewModel::goBackFromCurrentScreen,
            )
        }
    }

    viewModel.startupRecoveryNotice?.let { notice ->
        AlertDialog(
            onDismissRequest = viewModel::dismissStartupRecoveryNotice,
            title = { Text(notice.title) },
            text = { Text(notice.message) },
            confirmButton = {
                TextButton(onClick = viewModel::dismissStartupRecoveryNotice) {
                    Text("OK")
                }
            },
        )
    }
}

/**
 * Keep active-game timing cues alive even when the observer leaves the live screen.
 *
 * @param liveState The active current-game state, or null when no audible/vibration cues should run.
 * @param timingAlertPreferences The current timing alert settings.
 */
@Composable
private fun TimingAlertCueListener(
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
                    context = context,
                    timingAlertPlayer = timingAlertPlayer,
                    alertPlaybackScope = alertPlaybackScope,
                    onAlertKeysPlayed = { alertKeys -> playedTimingAlertKeys += alertKeys },
                )
                continue
            }

            val nextTimingAlert = state.nextTimingAlert(now)
            if (nextTimingAlert == null) {
                kotlinx.coroutines.delay(TIMING_ALERT_SCHEDULE_CHECK_MS)
                continue
            }

            val millisUntilNextAlert = nextTimingAlert.targetEpoch - now
            if (millisUntilNextAlert > TIMING_ALERT_SCHEDULE_CHECK_MS) {
                kotlinx.coroutines.delay(TIMING_ALERT_SCHEDULE_CHECK_MS)
                continue
            }

            if (millisUntilNextAlert > 0L) {
                kotlinx.coroutines.delay(millisUntilNextAlert)
            }
            val alertKey = nextTimingAlert.alertKey()
            if (alertKey !in playedTimingAlertKeys) {
                playTimingAlerts(
                    timingAlerts = listOf(nextTimingAlert),
                    timingAlertPreferences = timingAlertPreferences,
                    context = context,
                    timingAlertPlayer = timingAlertPlayer,
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
 * @param context Android context used for haptics.
 * @param timingAlertPlayer Sound player used for audible cues.
 * @param alertPlaybackScope Coroutine scope for background playback.
 * @param onAlertKeysPlayed Callback recording cue keys so recomposition does not replay them.
 */
private fun playTimingAlerts(
    timingAlerts: List<TimingCueDisplay>,
    timingAlertPreferences: TimingAlertPreferences,
    context: android.content.Context,
    timingAlertPlayer: TimingAlertPlayer,
    alertPlaybackScope: kotlinx.coroutines.CoroutineScope,
    onAlertKeysPlayed: (Set<String>) -> Unit,
) {
    onAlertKeysPlayed(timingAlerts.map { cue -> cue.alertKey() }.toSet())
    alertPlaybackScope.launch(Dispatchers.Default) {
        timingAlerts.forEach { cue ->
            playTimingAlertOnce(
                cue = cue,
                timingAlertPreferences = timingAlertPreferences,
                context = context,
                timingAlertPlayer = timingAlertPlayer,
                playedTimingAlertKeys = emptySet(),
                onAlertKeyPlayed = {},
            )
        }
    }
}

/// Build a stable deduplication key for a timing cue.
private fun TimingCueDisplay.alertKey(): String {
    return "${id.name}:$targetEpoch"
}

private const val TIMING_ALERT_SCHEDULE_CHECK_MS = 250L
