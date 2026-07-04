package rmjarvis.ultiobserver

import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
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
 * @param previousRunCrashed Whether Crashlytics recorded a fatal crash in the previous app run.
 */
@Composable
internal fun UltiObserverApp(
    viewModel: AppViewModel,
    previousRunCrashed: Boolean,
) {
    val appState by viewModel.state.collectAsState()
    val context = LocalContext.current
    var showMissingExactAlarmAccessDialog by remember { mutableStateOf(false) }
    var showPreviousCrashDialog by rememberSaveable { mutableStateOf(previousRunCrashed) }

    // Back returns to setup from the initial live preview, otherwise to home.
    BackHandler(enabled = appState.screen != AppScreen.HOME) {
        viewModel.goBackFromCurrentScreen()
    }

    TimingAlertForegroundServiceEffect(
        liveState = appState.currentGame?.takeUnless { state ->
            state.phase == GamePhase.SETUP || state.phase == GamePhase.GAME_OVER
        },
        timingAlertPreferences = appState.timingAlertPreferences,
    )

    // No else branch: every AppScreen value is handled.
    when (appState.screen) {
        AppScreen.HOME -> {
            val currentState = appState.currentGame
            val currentGame: GameListEntry?
            val completedGamePendingArchive: GameListEntry?
            if (currentState == null) {
                currentGame = null
                completedGamePendingArchive = null
            } else if (currentState.phase == GamePhase.GAME_OVER) {
                currentGame = null
                completedGamePendingArchive = currentState.gameListEntry()
            } else {
                currentGame = currentState.gameListEntry()
                completedGamePendingArchive = null
            }
            HomeScreen(
                avatarPreference = appState.homeAvatarPreference,
                currentGame = currentGame,
                currentGameSectionSubtitle = viewModel.currentGameHomeSubtitle,
                completedGamePendingArchive = completedGamePendingArchive,
                onResumeCurrentGame = {
                    viewModel.resumeCurrentGame()
                },
                onOpenCompletedGame = {
                    viewModel.openCompletedGame()
                },
                onArchiveCompletedGame = {
                    viewModel.archiveCompletedGame()
                },
                onStartNewGame = { viewModel.startNewGame(System.currentTimeMillis()) },
                onOpenAbout = {
                    viewModel.openAbout()
                },
                onOpenProfile = {
                    viewModel.openProfile()
                },
                onOpenSettings = {
                    viewModel.openSettings()
                },
                onOpenArchivedGames = {
                    viewModel.openArchivedGames()
                },
            )
        }

        AppScreen.ABOUT -> {
            AboutScreen(
                versionName = BuildConfig.VERSION_NAME,
                onBackHome = {
                    viewModel.goHome()
                },
                onHome = {
                    viewModel.goHome()
                },
            )
        }

        AppScreen.PROFILE -> {
            ProfileScreen(
                name = appState.profileName,
                avatarPreference = appState.avatarPreference,
                onNameChange = { name ->
                    viewModel.updateProfileName(name)
                },
                onAvatarPreferenceChange = { preference ->
                    viewModel.updateAvatarPreference(preference)
                },
                onBackHome = {
                    viewModel.goHome()
                },
                onHome = {
                    viewModel.goHome()
                },
            )
        }

        AppScreen.SETTINGS -> {
            SettingsScreen(
                automaticallyAdvanceCountdowns = appState.automaticallyAdvanceCountdowns,
                automaticallyLockLivePoint = appState.automaticallyLockLivePoint,
                showDefenseCountdowns = appState.showDefenseCountdowns,
                timingAlertPreferences = appState.timingAlertPreferences,
                onAutomaticallyAdvanceCountdownsChange = { enabled ->
                    viewModel.updateAutomaticallyAdvanceCountdowns(enabled)
                },
                onAutomaticallyLockLivePointChange = { enabled ->
                    viewModel.updateAutomaticallyLockLivePoint(enabled)
                },
                onShowDefenseCountdownsChange = { enabled ->
                    viewModel.updateShowDefenseCountdowns(enabled)
                },
                onGlobalModeChange = { mode ->
                    viewModel.updateTimingAlertGlobalMode(mode)
                },
                onSoundVolumeChange = { volume ->
                    viewModel.updateTimingAlertSoundVolume(volume)
                },
                onVibrationDurationChange = { duration ->
                    viewModel.updateTimingAlertVibrationDuration(duration)
                },
                onVibrateWithSoundsChange = { enabled ->
                    viewModel.updateTimingAlertVibrateWithSounds(enabled)
                },
                onOpenTimingCueSettings = {
                    viewModel.openTimingCueSettings()
                },
                onBackHome = {
                    viewModel.goHome()
                },
                onHome = {
                    viewModel.goHome()
                },
            )
        }

        AppScreen.TIMING_CUE_SETTINGS -> {
            TimingCueSettingsScreen(
                timingAlertPreferences = appState.timingAlertPreferences,
                showDefenseCountdowns = appState.showDefenseCountdowns,
                onTimingCueModeChange = { cueType, mode ->
                    viewModel.updateTimingCueMode(cueType, mode)
                },
                onTimingCueRepeatCountChange = { cueType, count ->
                    viewModel.updateTimingCueRepeatCount(cueType, count)
                },
                onResetTimingCueSettings = {
                    viewModel.resetTimingCueSettingsToDefaults()
                },
                onBackSettings = {
                    viewModel.openSettings()
                },
                onHome = {
                    viewModel.goHome()
                },
            )
        }

        AppScreen.ARCHIVED_GAMES -> {
            val archivedGame = appState.viewingArchivedGame
            if (archivedGame != null) {
                val isInProgressArchive = archivedGame.archiveCategory ==
                    ArchivedGameCategory.IN_PROGRESS
                GameOverSummaryScreen(
                    state = archivedGame,
                    completed = !isInProgressArchive,
                    summaryActionText = if (isInProgressArchive) {
                        "Make current"
                    } else {
                        "Restore game"
                    },
                    onSummaryAction = {
                        viewModel.restoreCompletedGame()
                    },
                    secondarySummaryActionText = if (isInProgressArchive) {
                        "Archive game"
                    } else {
                        null
                    },
                    onSecondarySummaryAction = if (isInProgressArchive) {
                        {
                            viewModel.archiveSavedInProgressGame(System.currentTimeMillis())
                        }
                    } else {
                        null
                    },
                    onBack = {
                        viewModel.goBackFromCurrentScreen()
                    },
                    onHome = {
                        viewModel.goHome()
                    },
                    gameOverPrompt = null,
                    onDismissGameOverPrompt = {},
                )
            } else {
                val currentInProgressGame = appState.currentGame
                    ?.takeUnless { it.phase == GamePhase.SETUP }
                    ?.gameListEntry()
                val currentSetupDraft = appState.currentGame
                    ?.takeIf { it.phase == GamePhase.SETUP }
                    ?.gameListEntry()
                val archiveCategoryCounts = remember(
                    appState.archivedGames,
                    currentInProgressGame,
                    currentSetupDraft,
                ) {
                    ArchivedGameCategory.entries.associateWith { category ->
                        val currentCount = when {
                            category == ArchivedGameCategory.IN_PROGRESS &&
                                currentInProgressGame != null -> 1
                            category == ArchivedGameCategory.SETUP &&
                                currentSetupDraft != null -> 1
                            else -> 0
                        }
                        currentCount + appState.archivedGames.count {
                            it.archiveCategory == category
                        }
                    }
                }
                ArchivedGamesScreen(
                    categoryCounts = archiveCategoryCounts,
                    hasSavedOrArchivedGames = appState.archivedGames.isNotEmpty(),
                    selectedCategory = appState.selectedArchiveCategory,
                    archiveFilterSelections = appState.archiveFilterSelections,
                    archiveSortMode = appState.archiveSortMode,
                    filteredArchiveState = appState.filteredArchiveState(),
                    currentInProgressGame = currentInProgressGame,
                    currentSetupDraft = currentSetupDraft,
                    onOpenCategory = { category ->
                        viewModel.openArchivedGameCategory(category)
                    },
                    onUpdateArchiveFilterSelections = { field, values ->
                        viewModel.updateArchiveFilterSelections(field, values)
                    },
                    onUpdateArchiveDateFilter = { dateFilter ->
                        viewModel.updateArchiveDateFilter(dateFilter)
                    },
                    onClearArchiveFilter = { field ->
                        viewModel.clearArchiveFilter(field)
                    },
                    onClearArchiveFilterSelections = {
                        viewModel.clearArchiveFilterSelections()
                    },
                    onUpdateArchiveSortMode = { sortMode ->
                        viewModel.updateArchiveSortMode(sortMode)
                    },
                    onOpenCurrentGame = {
                        viewModel.openCurrentGameSummary()
                    },
                    onOpenCurrentSetup = {
                        viewModel.resumeCurrentGame()
                    },
                    onOpenArchivedGame = { index ->
                        viewModel.openArchivedGame(index, System.currentTimeMillis())
                    },
                    onDeleteArchivedGame = { index ->
                        viewModel.deleteArchivedGame(index)
                    },
                    onDeleteAllArchivedGames = {
                        viewModel.deleteAllArchivedGames()
                    },
                    onDeleteSelectedArchivedGames = { indices ->
                        viewModel.deleteSelectedArchivedGames(indices)
                    },
                    onDeleteAllInSelectedCategory = {
                        viewModel.deleteArchivedGamesInSelectedCategory()
                    },
                    onBackHome = {
                        viewModel.goHome()
                    },
                    onBackCategories = {
                        viewModel.returnToArchivedGameCategories()
                    },
                    onHome = {
                        viewModel.goHome()
                    },
                )
            }
        }

        AppScreen.SETUP -> {
            val setupGame = appState.setupState
            val setupMode = appState.setupMode
            fun finishSetup() {
                if (
                    setupMode == SetupMode.NEW_GAME &&
                    setupGame.rules.hasEnabledCapTimingAlerts(
                        appState.timingAlertPreferences,
                    ) &&
                    !context.hasExactTimingAlertAlarmAccess()
                ) {
                    showMissingExactAlarmAccessDialog = true
                    return
                }
                viewModel.finishSetup(System.currentTimeMillis())
            }

            SetupScreen(
                state = setupGame,
                onStateChange = { updatedState ->
                    viewModel.updateSetup(updatedState)
                },
                title = when (setupMode) {
                    SetupMode.EDIT_CURRENT_GAME -> "Update game setup"
                    SetupMode.EDIT_SAVED_SETUP -> "Saved setup draft"
                    SetupMode.NEW_GAME -> "Setup game"
                },
                primaryButtonLabel = when (setupMode) {
                    SetupMode.EDIT_CURRENT_GAME -> "Done"
                    SetupMode.EDIT_SAVED_SETUP -> "Make current"
                    SetupMode.NEW_GAME -> "Start game"
                },
                onPrimaryAction = {
                    if (setupMode == SetupMode.EDIT_SAVED_SETUP) {
                        viewModel.makeEditedSetupCurrent()
                    } else {
                        finishSetup()
                    }
                },
                // No else branch: every SetupMode value is handled.
                onSecondaryAction = when (setupMode) {
                    SetupMode.EDIT_CURRENT_GAME -> {
                        { viewModel.cancelSetupEdit() }
                    }
                    SetupMode.EDIT_SAVED_SETUP -> {
                        { viewModel.openSavedSetupDrafts() }
                    }
                    SetupMode.NEW_GAME -> null
                },
                secondaryButtonLabel = if (setupMode == SetupMode.EDIT_SAVED_SETUP) {
                    "Save draft"
                } else {
                    "Cancel"
                },
                secondaryButtonColors = if (setupMode == SetupMode.EDIT_SAVED_SETUP) {
                    secondaryButtonColors()
                } else {
                    resetButtonColors()
                },
                secondaryActionFullWidth = setupMode == SetupMode.EDIT_SAVED_SETUP,
                onSaveGameForLater = if (setupMode == SetupMode.NEW_GAME) {
                    {
                        viewModel.saveSetupForLater()
                    }
                } else {
                    null
                },
                onBackHome = {
                    if (setupMode == SetupMode.EDIT_SAVED_SETUP) {
                        viewModel.openSavedSetupDrafts()
                    } else {
                        viewModel.goHome()
                    }
                },
                onHome = {
                    viewModel.goHome()
                },
            )
            if (showMissingExactAlarmAccessDialog) {
                AlertDialog(
                    onDismissRequest = {
                        showMissingExactAlarmAccessDialog = false
                    },
                    title = { Text("Cap alert permission") },
                    text = {
                        Text(
                            "UltiObserver uses an alarm for cap notifications so they work even if your screen is asleep. " +
                                "Please enable access in the Alarms & reminders settings for your device."
                        )
                    },
                    confirmButton = {
                        TextActionButton(
                            label = "Open settings",
                            onClick = {
                                showMissingExactAlarmAccessDialog = false
                                context.openExactAlarmSettings()
                            },
                        )
                    },
                    dismissButton = {
                        TextActionButton(
                            label = "Ignore",
                            onClick = {
                                showMissingExactAlarmAccessDialog = false
                                viewModel.finishSetup(System.currentTimeMillis())
                            },
                        )
                    },
                )
            }
        }

        AppScreen.LIVE -> {
            val currentSummaryGame = appState.currentGame.takeIf {
                appState.viewingCurrentGameSummary
            }
            if (currentSummaryGame != null) {
                GameOverSummaryScreen(
                    state = currentSummaryGame,
                    completed = false,
                    summaryActionText = "Back to game",
                    onSummaryAction = {
                        viewModel.resumeCurrentGame()
                    },
                    onBack = {
                        viewModel.goBackFromCurrentScreen()
                    },
                    onHome = {
                        viewModel.goHome()
                    },
                    gameOverPrompt = null,
                    onDismissGameOverPrompt = {},
                )
            } else {
                val currentLiveState = appState.currentGame!!
                LiveGameScreen(
                    state = currentLiveState,
                    automaticallyAdvanceCountdowns = appState.automaticallyAdvanceCountdowns,
                    automaticallyLockLivePoint = appState.automaticallyLockLivePoint,
                    showDefenseCountdowns = appState.showDefenseCountdowns,
                    onStateChange = { updatedState ->
                        viewModel.updateLiveGame(updatedState)
                    },
                    onUpdateGameSetup = {
                        viewModel.editCurrentGame(currentLiveState)
                    },
                    onOpenGameSummary = {
                        viewModel.openCurrentGameSummary()
                    },
                    onArchiveCompletedGame = {
                        viewModel.archiveCompletedGame()
                    },
                    onDeleteGame = {
                        viewModel.deleteCurrentGame()
                    },
                    onBackHome = {
                        viewModel.goBackFromCurrentScreen()
                    },
                    onHome = {
                        viewModel.goHome()
                    },
                )
            }
        }
    }

    val startupRecoveryNotice = appState.startupRecoveryNotice
    if (startupRecoveryNotice != null) {
        val notice = startupRecoveryNotice
        AlertDialog(
            onDismissRequest = {
                viewModel.dismissStartupRecoveryNotice()
            },
            title = { Text(notice.title) },
            text = { Text(notice.message) },
            confirmButton = {
                TextActionButton(
                    label = "OK",
                    onClick = {
                        viewModel.dismissStartupRecoveryNotice()
                    },
                )
            },
        )
    } else if (showPreviousCrashDialog) {
        AlertDialog(
            onDismissRequest = {
                showPreviousCrashDialog = false
            },
            title = { Text("Sorry, UltiObserver crashed") },
            text = {
                Text(
                    "UltiObserver closed unexpectedly last time it ran. A crash report was sent " +
                        "to the developers automatically so we can fix the problem."
                )
            },
            confirmButton = {
                TextActionButton(
                    label = "OK",
                    onClick = {
                        showPreviousCrashDialog = false
                    },
                )
            },
        )
    }
}
