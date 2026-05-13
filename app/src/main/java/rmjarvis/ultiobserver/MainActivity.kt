package rmjarvis.ultiobserver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import rmjarvis.ultiobserver.ui.theme.UltiObserverTheme

class MainActivity : ComponentActivity() {
    internal val appViewModel: UltiObserverAppViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return modelClass.cast(UltiObserverAppViewModel(FileAppStateStore(filesDir)))!!
            }
        }
    }

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

// Switch between home, setup, and live screens from the app ViewModel state.
@Composable
internal fun UltiObserverApp(viewModel: UltiObserverAppViewModel) {
    // Back returns to setup from the initial live preview, otherwise to home.
    BackHandler(enabled = viewModel.screen != AppScreen.HOME) {
        viewModel.goBackFromCurrentScreen()
    }

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
                currentGame = currentGame,
                currentGameSectionSubtitle = viewModel.currentGameHomeSubtitle,
                completedGamePendingArchive = completedGamePendingArchive,
                onResumeCurrentGame = viewModel::resumeCurrentGame,
                onOpenCompletedGame = viewModel::openCompletedGame,
                onArchiveCompletedGame = viewModel::archiveCompletedGame,
                onStartNewGame = viewModel::startNewGame,
                onOpenProfile = viewModel::openProfile,
                onOpenSettings = viewModel::openSettings,
                onOpenPreviousGames = viewModel::openPreviousGames,
            )
        }

        AppScreen.PROFILE -> {
            ProfileScreen(
                name = viewModel.profileName,
                onNameChange = viewModel::updateProfileName,
                onBackHome = viewModel::goHome,
            )
        }

        AppScreen.SETTINGS -> {
            SettingsScreen(
                timingAlertPreferences = viewModel.timingAlertPreferences,
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
                timingAlertPreferences = viewModel.timingAlertPreferences,
                onStateChange = viewModel::updateLiveGame,
                onUpdateGameSetup = {
                    viewModel.editCurrentGame(currentLiveState)
                },
                onDeleteGame = viewModel::deleteCurrentGame,
                onBackHome = viewModel::goBackFromCurrentScreen,
            )
        }
    }
}
