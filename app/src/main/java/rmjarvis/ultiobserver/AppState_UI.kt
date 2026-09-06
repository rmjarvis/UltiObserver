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

/**
 * Switch between home, setup, and live screens from the current app state snapshot.
 *
 * @param appState The process-wide app state owning navigation and persisted state.
 * @param previousRunCrashed Whether Crashlytics recorded a fatal crash in the previous app run.
 * @param displayOrientation Readable orientation currently shown by Android.
 * @param wearWatchAvailable Whether a Wear OS node is reachable, or null before the check finishes.
 */
@Composable
internal fun UltiObserverApp(
    appState: AppState,
    previousRunCrashed: Boolean,
    displayOrientation: ActiveGameFullOrientation,
    wearWatchAvailable: Boolean?,
) {
    val snapshot by appState.state.collectAsState()
    val context = LocalContext.current
    var showMissingExactAlarmAccessDialog by remember { mutableStateOf(false) }
    var showPreviousCrashDialog by rememberSaveable { mutableStateOf(previousRunCrashed) }

    // Back returns to setup from the pre-pull preview, otherwise to home.
    BackHandler(enabled = snapshot.screen != AppScreen.HOME) {
        appState.goBackFromCurrentScreen()
    }

    TimingAlertForegroundServiceEffect(
        liveState = snapshot.currentGame?.takeUnless { state ->
            state.phase == GamePhase.SETUP || state.phase == GamePhase.GAME_OVER
        },
        settings = snapshot.settings,
    )

    // No else branch: every AppScreen value is handled.
    when (snapshot.screen) {
        AppScreen.HOME -> {
            val currentState = snapshot.currentGame
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
                avatar = snapshot.currentHomeAvatar,
                currentGame = currentGame,
                currentGameSectionSubtitle = appState.currentGameHomeSubtitle,
                completedGamePendingArchive = completedGamePendingArchive,
                onResumeCurrentGame = {
                    appState.resumeCurrentGame()
                },
                onArchiveCompletedGame = {
                    appState.archiveCompletedGame()
                },
                onStartNewGame = { appState.startNewGame(System.currentTimeMillis()) },
                onOpenAbout = {
                    appState.openAbout()
                },
                onOpenOfficialClock = {
                    appState.openOfficialClock()
                },
                officialClockAdjusted = snapshot.settings.officialClockOffsetMillis != 0L,
                onOpenProfile = {
                    appState.openProfile()
                },
                onOpenSettings = {
                    appState.openSettings()
                },
                onOpenArchivedGames = {
                    appState.openArchivedGames()
                },
            )
        }

        AppScreen.ABOUT -> {
            AboutScreen(
                versionName = BuildConfig.VERSION_NAME,
                onBackHome = {
                    appState.goHome()
                },
                onHome = {
                    appState.goHome()
                },
            )
        }

        AppScreen.OFFICIAL_CLOCK -> {
            OfficialClockScreen(
                currentOffsetMillis = snapshot.settings.officialClockOffsetMillis,
                onOffsetChange = { updatedOffsetMillis ->
                    appState.updateOfficialClockOffset(updatedOffsetMillis)
                },
                onBackHome = {
                    appState.goHome()
                },
                onHome = {
                    appState.goHome()
                },
            )
        }

        AppScreen.PROFILE -> {
            ProfileScreen(
                profile = snapshot.profile,
                onProfileChange = { updatedProfile ->
                    appState.updateProfile(updatedProfile)
                },
                onBackHome = {
                    appState.goHome()
                },
                onHome = {
                    appState.goHome()
                },
            )
        }

        AppScreen.SETTINGS -> {
            SettingsScreen(
                settings = snapshot.settings,
                onSettingsChange = { updatedSettings ->
                    appState.updateSettings(updatedSettings)
                },
                onOpenTimingCueSettings = {
                    appState.openTimingCueSettings()
                },
                onBackHome = {
                    appState.goHome()
                },
                onHome = {
                    appState.goHome()
                },
                wearWatchAvailable = wearWatchAvailable,
            )
        }

        AppScreen.TIMING_CUE_SETTINGS -> {
            TimingCueSettingsScreen(
                settings = snapshot.settings,
                onSettingsChange = { updatedSettings ->
                    appState.updateSettings(updatedSettings)
                },
                onBackSettings = {
                    appState.openSettings()
                },
                onHome = {
                    appState.goHome()
                },
            )
        }

        AppScreen.ARCHIVED_GAMES -> {
            val archivedGame = snapshot.viewingArchivedGame
            if (archivedGame != null) {
                val isInProgressArchive = archivedGame.archiveCategory ==
                    ArchivedGameCategory.IN_PROGRESS
                val archiveSavedInProgressAction: () -> Unit = {
                    appState.archiveSavedInProgressGame(System.currentTimeMillis())
                }
                GameOverSummaryScreen(
                    state = archivedGame,
                    completed = !isInProgressArchive,
                    guidanceMode = snapshot.settings.ruleGuidanceMode,
                    onStateChange = { updatedGame ->
                        appState.updateViewingArchivedGame(updatedGame)
                    },
                    summaryActionText = if (isInProgressArchive) {
                        "Make current"
                    } else {
                        "Restore game"
                    },
                    onSummaryAction = {
                        appState.makeArchivedGameCurrent()
                    },
                    secondarySummaryActionText = if (isInProgressArchive) {
                        "Archive game"
                    } else {
                        null
                    },
                    onSecondarySummaryAction = if (isInProgressArchive) {
                        archiveSavedInProgressAction
                    } else {
                        null
                    },
                    onBack = {
                        appState.goBackFromCurrentScreen()
                    },
                    onHome = {
                        appState.goHome()
                    },
                )
            } else {
                val currentInProgressGame = snapshot.currentGame
                    ?.takeUnless { it.phase == GamePhase.SETUP }
                    ?.gameListEntry()
                val currentSetupDraft = snapshot.currentGame
                    ?.takeIf { it.phase == GamePhase.SETUP }
                    ?.gameListEntry()
                val archiveCategoryCounts = remember(
                    snapshot.archivedGames,
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
                        currentCount + snapshot.archivedGames.count {
                            it.archiveCategory == category
                        }
                    }
                }
                ArchivedGamesScreen(
                    categoryCounts = archiveCategoryCounts,
                    hasSavedOrArchivedGames = snapshot.archivedGames.isNotEmpty(),
                    selectedCategory = snapshot.selectedArchiveCategory,
                    archiveFilterSelections = snapshot.archiveFilterSelections,
                    archiveSortMode = snapshot.archiveSortMode,
                    filteredArchiveState = snapshot.filteredArchiveState(),
                    currentInProgressGame = currentInProgressGame,
                    currentSetupDraft = currentSetupDraft,
                    onOpenCategory = { category ->
                        appState.openArchivedGameCategory(category)
                    },
                    onUpdateArchiveFilterSelections = { field, values ->
                        appState.updateArchiveFilterSelections(field, values)
                    },
                    onUpdateArchiveDateFilter = { dateFilter ->
                        appState.updateArchiveDateFilter(dateFilter)
                    },
                    onClearArchiveFilter = { field ->
                        appState.clearArchiveFilter(field)
                    },
                    onClearArchiveFilterSelections = {
                        appState.clearArchiveFilterSelections()
                    },
                    onUpdateArchiveSortMode = { sortMode ->
                        appState.updateArchiveSortMode(sortMode)
                    },
                    onOpenCurrentGame = {
                        appState.openCurrentGameSummary()
                    },
                    onOpenCurrentSetup = {
                        appState.resumeCurrentGame()
                    },
                    onOpenArchivedGame = { index ->
                        appState.openArchivedGame(index, System.currentTimeMillis())
                    },
                    onDeleteCurrentGame = {
                        appState.deleteCurrentGame()
                    },
                    onDeleteArchivedGame = { index ->
                        appState.deleteArchivedGame(index)
                    },
                    onDeleteAllArchivedGames = {
                        appState.deleteAllArchivedGames()
                    },
                    onDeleteSelectedArchivedGames = { indices ->
                        appState.deleteSelectedArchivedGames(indices)
                    },
                    onDeleteAllInSelectedCategory = {
                        appState.deleteArchivedGamesInSelectedCategory()
                    },
                    onBackHome = {
                        appState.goHome()
                    },
                    onBackCategories = {
                        appState.returnToArchivedGameCategories()
                    },
                    onHome = {
                        appState.goHome()
                    },
                )
            }
        }

        AppScreen.SETUP -> {
            val setupGame = snapshot.setupGame
            val setupMode = snapshot.setupMode
            fun finishSetup() {
                if (
                    setupMode == SetupMode.NEW_GAME &&
                    setupGame.rules.hasEnabledCapTimingAlerts(
                        snapshot.settings.timingAlerts,
                    ) &&
                    !context.hasExactTimingAlertAlarmAccess()
                ) {
                    showMissingExactAlarmAccessDialog = true
                    return
                }
                appState.finishSetup(System.currentTimeMillis())
            }

            val cancelSetupEditAction: () -> Unit = {
                appState.cancelSetupEdit()
            }
            val openSavedSetupDraftsAction: () -> Unit = {
                appState.openSavedSetupDrafts()
            }
            val saveSetupForLaterAction: () -> Unit = {
                appState.saveSetupForLater()
            }

            SetupScreen(
                state = setupGame,
                orientationPreference = snapshot.settings.orientationPreference,
                onStateChange = { updatedState ->
                    appState.updateSetup(updatedState)
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
                        appState.makeEditedSetupCurrent()
                    } else {
                        finishSetup()
                    }
                },
                // No else branch: every SetupMode value is handled.
                onSecondaryAction = when (setupMode) {
                    SetupMode.EDIT_CURRENT_GAME -> {
                        cancelSetupEditAction
                    }
                    SetupMode.EDIT_SAVED_SETUP -> {
                        openSavedSetupDraftsAction
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
                    saveSetupForLaterAction
                } else {
                    null
                },
                onBackHome = {
                    if (setupMode == SetupMode.EDIT_SAVED_SETUP) {
                        appState.openSavedSetupDrafts()
                    } else {
                        appState.goHome()
                    }
                },
                onHome = {
                    appState.goHome()
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
                                appState.finishSetup(System.currentTimeMillis())
                            },
                        )
                    },
                )
            }
        }

        AppScreen.LIVE -> {
            val currentSummaryGame = snapshot.currentGame.takeIf {
                snapshot.viewingCurrentGameSummary
            }
            if (currentSummaryGame != null) {
                val completed = currentSummaryGame.phase == GamePhase.GAME_OVER
                val summaryActionText: String
                val onSummaryAction: () -> Unit
                val secondarySummaryActionText: String?
                val onSecondarySummaryAction: (() -> Unit)?
                val onBack: () -> Unit
                if (completed) {
                    summaryActionText = currentSummaryGame.undoEntry!!.label
                    onSummaryAction = {
                        if (
                            appState.updateCurrentGame(
                                currentSummaryGame,
                                currentSummaryGame.undoLastAction(),
                            )
                        ) {
                            appState.resumeCurrentGame()
                        }
                    }
                    secondarySummaryActionText = "Archive game"
                    onSecondarySummaryAction = {
                        appState.archiveCompletedGame()
                    }
                    onBack = {
                        appState.goHome()
                    }
                } else {
                    summaryActionText = "Back to game"
                    onSummaryAction = {
                        appState.resumeCurrentGame()
                    }
                    secondarySummaryActionText = null
                    onSecondarySummaryAction = null
                    onBack = {
                        appState.goBackFromCurrentScreen()
                    }
                }
                GameOverSummaryScreen(
                    state = currentSummaryGame,
                    completed = completed,
                    guidanceMode = snapshot.settings.ruleGuidanceMode,
                    onStateChange = { updatedGame ->
                        appState.updateCurrentGame(currentSummaryGame, updatedGame)
                    },
                    summaryActionText = summaryActionText,
                    onSummaryAction = onSummaryAction,
                    secondarySummaryActionText = secondarySummaryActionText,
                    onSecondarySummaryAction = onSecondarySummaryAction,
                    onBack = onBack,
                    onHome = {
                        appState.goHome()
                    },
                )
            } else {
                val currentGame = snapshot.currentGame!!
                ActiveGameScreen(
                    state = currentGame,
                    settings = snapshot.settings,
                    displayOrientation = displayOrientation,
                    activeCardEntry = snapshot.activeCardEntry,
                    onStateChange = { updatedState ->
                        appState.updateCurrentGame(currentGame, updatedState)
                    },
                    onGoal = { scoringTeam ->
                        appState.recordGoal(
                            currentGame = currentGame,
                            scoringTeam = scoringTeam,
                            now = System.currentTimeMillis(),
                        )
                    },
                    onDecision = { accept ->
                        appState.resolveDecision(
                            currentGame = currentGame,
                            accept = accept,
                            now = System.currentTimeMillis(),
                        )
                    },
                    onConfirmation = { confirmation ->
                        appState.confirmAction(confirmation)
                    },
                    onCardEntryChange = { currentEntry, updatedEntry ->
                        appState.updateCardEntry(currentGame, currentEntry, updatedEntry)
                    },
                    onCardEntryCompleted = { entry, updatedGame ->
                        appState.completeCardEntry(currentGame, updatedGame, entry)
                    },
                    onUpdateGameSetup = {
                        appState.editCurrentGame(currentGame)
                    },
                    onOpenGameSummary = {
                        appState.openCurrentGameSummary()
                    },
                    onBackHome = {
                        appState.goBackFromCurrentScreen()
                    },
                    onHome = {
                        appState.goHome()
                    },
                )
            }
        }
    }

    val startupRecoveryNotice = snapshot.startupRecoveryNotice
    if (startupRecoveryNotice != null) {
        val notice = startupRecoveryNotice
        AlertDialog(
            onDismissRequest = {
                appState.dismissStartupRecoveryNotice()
            },
            title = { Text(notice.title) },
            text = { Text(notice.message) },
            confirmButton = {
                TextActionButton(
                    label = "OK",
                    onClick = {
                        appState.dismissStartupRecoveryNotice()
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
