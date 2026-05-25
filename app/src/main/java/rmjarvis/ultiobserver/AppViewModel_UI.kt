package rmjarvis.ultiobserver

import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import java.io.File

/**
 * Build the production AppViewModel factory for Android lifecycle creation.
 *
 * @param filesDir The app-private storage directory supplied by MainActivity.
 */
internal fun appViewModelFactory(filesDir: File): ViewModelProvider.Factory {
    return object : ViewModelProvider.Factory {
        /**
         * Create the app ViewModel with file-backed persistence.
         *
         * @param modelClass The ViewModel class requested by the Android lifecycle owner.
         */
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            return modelClass.cast(AppViewModel(FileAppStateStorage(filesDir)))!!
        }
    }
}

/**
 * Switch between home, setup, and live screens from the app ViewModel state.
 *
 * @param viewModel The app-level ViewModel owning navigation and persisted state.
 */
@Composable
internal fun UltiObserverApp(viewModel: AppViewModel) {
    val appState by viewModel.state.collectAsState()

    // Back returns to setup from the initial live preview, otherwise to home.
    BackHandler(enabled = appState.screen != AppScreen.HOME) {
        viewModel.goBackFromCurrentScreen()
    }

    TimingAlertCueListener(
        liveState = appState.liveState.takeUnless { state -> state?.phase == GamePhase.GAME_OVER },
        timingAlertPreferences = appState.timingAlertPreferences,
    )

    // No else branch: every AppScreen value is handled.
    when (appState.screen) {
        AppScreen.HOME -> {
            val liveState = appState.liveState
            val currentGame: GameListEntry?
            val completedGamePendingArchive: GameListEntry?
            if (liveState == null) {
                currentGame = if (appState.hasSetupDraft) {
                    appState.setupState.gameListEntry()
                } else {
                    null
                }
                completedGamePendingArchive = null
            } else if (liveState.phase == GamePhase.GAME_OVER) {
                currentGame = null
                completedGamePendingArchive = liveState.gameListEntry()
            } else {
                currentGame = liveState.gameListEntry()
                completedGamePendingArchive = null
            }
            HomeScreen(
                avatarPreference = appState.homeAvatarPreference,
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
                onOpenArchivedGames = viewModel::openArchivedGames,
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
                name = appState.profileName,
                avatarPreference = appState.avatarPreference,
                onNameChange = viewModel::updateProfileName,
                onAvatarPreferenceChange = viewModel::updateAvatarPreference,
                onBackHome = viewModel::goHome,
            )
        }

        AppScreen.SETTINGS -> {
            SettingsScreen(
                automaticallyAdvanceCountdowns = appState.automaticallyAdvanceCountdowns,
                automaticallyLockLivePoint = appState.automaticallyLockLivePoint,
                timingAlertPreferences = appState.timingAlertPreferences,
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
                timingAlertPreferences = appState.timingAlertPreferences,
                onTimingCueModeChange = viewModel::updateTimingCueMode,
                onTimingCueRepeatCountChange = viewModel::updateTimingCueRepeatCount,
                onResetTimingCueSettings = viewModel::resetTimingCueSettingsToDefaults,
                onBackSettings = viewModel::openSettings,
            )
        }

        AppScreen.ARCHIVED_GAMES -> {
            val archivedGameEntries = remember(appState.archivedGames) {
                appState.archivedGames.map { it.state.archivedGameListEntry() }
            }
            ArchivedGamesScreen(
                archivedGames = archivedGameEntries,
                onOpenArchivedGame = viewModel::openArchivedGame,
                onDeleteArchivedGame = viewModel::deleteArchivedGame,
                onDeleteAllArchivedGames = viewModel::deleteAllArchivedGames,
                onBackHome = viewModel::goHome,
            )
        }

        AppScreen.SETUP -> {
            SetupScreen(
                state = appState.setupState,
                onStateChange = viewModel::updateSetup,
                primaryButtonLabel = if (appState.setupMode == SetupMode.NEW_GAME) "Start Game" else "Back to Game Screen",
                onPrimaryAction = { viewModel.finishSetup() },
                onBackHome = viewModel::goHome,
            )
        }

        AppScreen.LIVE -> {
            val archivedGame = appState.viewingArchivedGame
            if (archivedGame != null) {
                GameOverSummaryScreen(
                    state = archivedGame.state,
                    summaryActionText = "Restore Game",
                    onSummaryAction = viewModel::restoreViewingArchivedGame,
                    onBack = viewModel::goBackFromCurrentScreen,
                    gameOverPrompt = null,
                    onDismissGameOverPrompt = {},
                )
            } else {
                val currentLiveState = appState.liveState!!
                LiveGameScreen(
                    state = currentLiveState,
                    automaticallyAdvanceCountdowns = appState.automaticallyAdvanceCountdowns,
                    automaticallyLockLivePoint = appState.automaticallyLockLivePoint,
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

    appState.startupRecoveryNotice?.let { notice ->
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
