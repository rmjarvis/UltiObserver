package rmjarvis.ultiobserver

import androidx.lifecycle.ViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.serialization.Serializable
import kotlin.random.Random

/// Identity of the top-level app screen currently routed by the app shell.
@Serializable
internal enum class AppScreen {
    HOME,
    ABOUT,
    PROFILE,
    SETTINGS,
    TIMING_CUE_SETTINGS,
    ARCHIVED_GAMES,
    SETUP,
    LIVE,
}

/**
 * Snapshot of app-level UI/session state owned by AppViewModel.
 *
 * @param screen The top-level app screen currently routed by the app shell.
 * @param currentGame The mutable current game, including setup drafts and completed games.
 * @param setupEditDraft Uncommitted setup edits for an existing current game.
 * @param editingSavedSetupIndex Archive index of the saved setup draft currently open for edit.
 * @param profileName The observer profile name.
 * @param avatarPreference The stored observer avatar preference.
 * @param currentHomeAvatar The concrete avatar shown on Home after resolving random choices.
 * @param timingAlertPreferences The current timing cue alert settings.
 * @param automaticallyAdvanceCountdowns Whether active countdowns transition automatically at expiry.
 * @param automaticallyLockLivePoint Whether automatic live-point transitions enable lock mode.
 * @param showDefenseCountdowns Whether timeout offense-set expirations wait for defense.
 * @param showAbbaRatioAsSequence Whether ABBA field badges show sequence shorthand.
 * @param archivedGames The archived game summaries loaded into the app session.
 * @param selectedArchiveCategory The archive category currently open from the category landing page.
 * @param viewingArchivedGame The archived game currently open as a summary.
 * @param viewingCurrentGameSummary Whether the current game is open as a summary.
 * @param archiveFilterSelections Selected filters applied to the archive list.
 * @param archiveSortMode Sort order applied to the archive list.
 * @param startupRecoveryNotice The startup data-recovery notice, if corrupted app data was reset.
 */
internal data class AppUiState(
    val screen: AppScreen = AppScreen.HOME,
    val currentGame: GameState?,
    val setupEditDraft: GameState?,
    val editingSavedSetupIndex: Int?,
    val profileName: String,
    val avatarPreference: ObserverAvatarPreference,
    val currentHomeAvatar: ObserverAvatarPreference,
    val timingAlertPreferences: TimingAlertPreferences,
    val automaticallyAdvanceCountdowns: Boolean,
    val automaticallyLockLivePoint: Boolean,
    val showDefenseCountdowns: Boolean,
    val showAbbaRatioAsSequence: Boolean,
    val archivedGames: List<GameState>,
    val selectedArchiveCategory: ArchivedGameCategory?,
    val viewingArchivedGame: GameState?,
    val viewingCurrentGameSummary: Boolean,
    val archiveFilterSelections: ArchiveFilterSelections,
    val archiveSortMode: ArchiveSortMode,
    val startupRecoveryNotice: RecoveryNotice?,
) {
    val setupGame: GameState
        get() = editingSavedSetupIndex?.let { archivedGames[it] }
            ?: setupEditDraft
            ?: currentGame
            ?: error("No setup game is active.")

    val setupMode: SetupMode
        get() {
            val game = currentGame
            return when {
                editingSavedSetupIndex != null -> SetupMode.EDIT_SAVED_SETUP
                setupEditDraft != null || (game != null && game.phase != GamePhase.SETUP) ->
                    SetupMode.EDIT_CURRENT_GAME
                else -> SetupMode.NEW_GAME
            }
        }

    /**
     * Build the archive rows and filter choices for archive navigation.
     *
     * The return value has two items:
     *
     * selectedGames are the games to show on the screen given the category and active filters.
     *
     * availableFilterValues is a map from each filter category to a list of values to include
     * as possible filter choices (given the other active filters besides that one).
     */
    fun filteredArchiveState(): FilteredArchiveState {
        return getFilteredArchiveState(
            archivedGames = archivedGames,
            selectedCategory = selectedArchiveCategory,
            filterSelections = archiveFilterSelections,
            sortMode = archiveSortMode,
        )
    }
}

/**
 * App-level state coordinator for the Android UI.
 *
 * An Android ViewModel is a lifecycle-aware state holder scoped to an Activity or UI flow.
 * It survives normal Activity recreation, such as rotation, so the UI can be rebuilt without
 * losing in-memory state; process restart recovery still comes from the app's storage layer.
 *
 * UltiObserver's AppViewModel owns top-level navigation, current-game state, profile state,
 * settings, archived-game lists, startup recovery notices, and the app actions that persist or
 * move between those states. Domain rules stay in the model helpers; this class coordinates the
 * app session around those model results.
 *
 * @param appStateStorage The persistence boundary used to load and save app state buckets.
 * @param chooseAvatarIndex Random-avatar chooser injected so tests can make selection deterministic.
 */
internal class AppViewModel(
    private val appStateStorage: AppStateStorage,
    // Injected so tests can make random avatar selection deterministic.
    private val chooseAvatarIndex: (Int) -> Int = { size -> Random.nextInt(size) },
) : ViewModel() {
    private val persistedCurrentGame = appStateStorage.loadCurrentGame()
    private val persistedProfile = appStateStorage.loadProfile()
    private val persistedSettings = appStateStorage.loadSettings()
    private val restoredArchivedGames = appStateStorage.loadArchivedGames()
    private val recoveredPersistedDataAreas = appStateStorage.resetPersistedDataAreas

    private val _state = MutableStateFlow(
        AppUiState(
            currentGame = persistedCurrentGame,
            setupEditDraft = null,
            editingSavedSetupIndex = null,
            profileName = persistedProfile?.name ?: "",
            avatarPreference = persistedProfile?.avatarPreference ?: ObserverAvatarPreference.RANDOM,
            currentHomeAvatar = resolveCurrentHomeAvatar(
                persistedProfile?.avatarPreference ?: ObserverAvatarPreference.RANDOM
            ),
            timingAlertPreferences = persistedSettings?.timingAlertPreferences ?: TimingAlertPreferences(),
            automaticallyAdvanceCountdowns = persistedSettings?.automaticallyAdvanceCountdowns ?: true,
            automaticallyLockLivePoint = persistedSettings?.automaticallyLockLivePoint ?: true,
            showDefenseCountdowns = persistedSettings?.showDefenseCountdowns ?: false,
            showAbbaRatioAsSequence = persistedSettings?.showAbbaRatioAsSequence ?: true,
            archivedGames = restoredArchivedGames,
            selectedArchiveCategory = null,
            viewingArchivedGame = null,
            viewingCurrentGameSummary = false,
            archiveFilterSelections = ArchiveFilterSelections(),
            archiveSortMode = ArchiveSortMode.DATE_NEWEST,
            startupRecoveryNotice = recoveredPersistedDataAreas.takeIf { it.isNotEmpty() }?.let { resetAreas ->
                RecoveryNotice(resetAreas)
            },
        )
    )

    val state: StateFlow<AppUiState> = _state.asStateFlow()

    val screen: AppScreen
        get() = state.value.screen
    val currentGame: GameState?
        get() = state.value.currentGame
    val setupEditDraft: GameState?
        get() = state.value.setupEditDraft
    val editingSavedSetupIndex: Int?
        get() = state.value.editingSavedSetupIndex
    val setupGame: GameState
        get() = state.value.setupGame
    val setupMode: SetupMode
        get() = state.value.setupMode
    val profileName: String
        get() = state.value.profileName
    val avatarPreference: ObserverAvatarPreference
        get() = state.value.avatarPreference
    val currentHomeAvatar: ObserverAvatarPreference
        get() = state.value.currentHomeAvatar
    val timingAlertPreferences: TimingAlertPreferences
        get() = state.value.timingAlertPreferences
    val automaticallyAdvanceCountdowns: Boolean
        get() = state.value.automaticallyAdvanceCountdowns
    val automaticallyLockLivePoint: Boolean
        get() = state.value.automaticallyLockLivePoint
    val showDefenseCountdowns: Boolean
        get() = state.value.showDefenseCountdowns
    val showAbbaRatioAsSequence: Boolean
        get() = state.value.showAbbaRatioAsSequence
    val archivedGames: List<GameState>
        get() = state.value.archivedGames
    val selectedArchiveCategory: ArchivedGameCategory?
        get() = state.value.selectedArchiveCategory
    val viewingArchivedGame: GameState?
        get() = state.value.viewingArchivedGame
    val viewingCurrentGameSummary: Boolean
        get() = state.value.viewingCurrentGameSummary
    val archiveFilterSelections: ArchiveFilterSelections
        get() = state.value.archiveFilterSelections
    val archiveSortMode: ArchiveSortMode
        get() = state.value.archiveSortMode
    val hasSetupDraft: Boolean
        get() = currentGame?.phase == GamePhase.SETUP
    val startupRecoveryNotice: RecoveryNotice?
        get() = state.value.startupRecoveryNotice

    init {
        persistRecoveredDataAreas(recoveredPersistedDataAreas)
    }

    val displayedGame: GameState?
        get() = viewingArchivedGame ?: currentGame

    val currentGameHomeSubtitle: String?
        get() {
            val current = currentGame
            return when {
                current != null && current.phase != GamePhase.GAME_OVER -> "Tap to resume"
                else -> null
            }
        }

    /// Navigate to Home and clear any summary view.
    fun goHome() {
        _state.update {
            it.copy(
                screen = AppScreen.HOME,
                selectedArchiveCategory = null,
                viewingArchivedGame = null,
                viewingCurrentGameSummary = false,
                setupEditDraft = null,
                editingSavedSetupIndex = null,
            )
        }
    }

    /// Navigate back according to the current top-level screen and current-game state.
    fun goBackFromCurrentScreen() {
        if (screen == AppScreen.TIMING_CUE_SETTINGS) {
            openSettings()
            return
        }

        if (viewingArchivedGame != null) {
            _state.update {
                it.copy(
                    screen = AppScreen.ARCHIVED_GAMES,
                    viewingArchivedGame = null,
                    viewingCurrentGameSummary = false,
                )
            }
            return
        }

        if (viewingCurrentGameSummary) {
            _state.update {
                it.copy(
                    screen = if (selectedArchiveCategory == null) {
                        AppScreen.LIVE
                    } else {
                        AppScreen.ARCHIVED_GAMES
                    },
                    viewingCurrentGameSummary = false,
                )
            }
            return
        }

        if (screen == AppScreen.ARCHIVED_GAMES && selectedArchiveCategory != null) {
            _state.update { it.copy(selectedArchiveCategory = null) }
            return
        }

        if (screen == AppScreen.SETUP && setupEditDraft != null) {
            cancelSetupEdit()
            return
        }

        if (screen == AppScreen.SETUP && editingSavedSetupIndex != null) {
            openSavedSetupDrafts()
            return
        }

        if (screen != AppScreen.LIVE) {
            goHome()
            return
        }

        if (currentGame?.hasStarted() == false) {
            reopenSetupDraftFromInitialPreview()
            return
        }

        goHome()
    }

    /**
     * Replace the setup game being edited.
     *
     * @param updatedGame The setup-edited game state produced by the UI.
     */
    fun updateSetup(updatedGame: GameState) {
        val savedIndex = editingSavedSetupIndex
        if (savedIndex != null) {
            _state.update {
                it.copy(
                    archivedGames = archivedGamesWith(savedIndex, updatedGame)
                )
            }
            persistArchivedGames()
            return
        }
        if (setupEditDraft != null) {
            _state.update { it.copy(setupEditDraft = updatedGame) }
            return
        }
        _state.update { it.copy(currentGame = updatedGame) }
        persistCurrentGame()
    }

    /**
     * Replace the mutable current game.
     *
     * @param updatedGame The current-game state returned from a model action.
     */
    fun updateCurrentGame(updatedGame: GameState) {
        // All current-game event logging flows through this ViewModel boundary.
        _state.update { it.copy(currentGame = updatedGame) }
        persistCurrentGame()
    }

    /**
     * Update the observer profile name.
     *
     * @param updatedName The profile name entered by the user.
     */
    fun updateProfileName(updatedName: String) {
        _state.update { it.copy(profileName = updatedName) }
        persistProfileState()
    }

    /**
     * Update the preferred observer avatar and resolve a home-screen avatar if random is selected.
     *
     * @param updatedPreference The newly selected avatar preference.
     */
    fun updateAvatarPreference(updatedPreference: ObserverAvatarPreference) {
        _state.update {
            it.copy(
                avatarPreference = updatedPreference,
                currentHomeAvatar = resolveCurrentHomeAvatar(updatedPreference),
            )
        }
        persistProfileState()
    }

    /**
     * Update the global timing-alert mode.
     *
     * @param mode The global mode controlling whether cues are off, vibration-only, or sound-enabled.
     */
    fun updateTimingAlertGlobalMode(mode: TimingAlertGlobalMode) {
        _state.update { it.copy(timingAlertPreferences = it.timingAlertPreferences.copy(globalMode = mode)) }
        persistSettingsState()
    }

    /**
     * Update timing-alert playback volume.
     *
     * @param volume The new sound volume value from settings.
     */
    fun updateTimingAlertSoundVolume(volume: Float) {
        _state.update { it.copy(timingAlertPreferences = it.timingAlertPreferences.copy(soundVolume = volume)) }
        persistSettingsState()
    }

    /**
     * Update timing-alert vibration length.
     *
     * @param durationMillis The requested vibration duration in milliseconds.
     */
    fun updateTimingAlertVibrationDuration(durationMillis: Long) {
        _state.update {
            it.copy(
                timingAlertPreferences = it.timingAlertPreferences.copy(
                    vibrationDurationMillis = durationMillis,
                ),
            )
        }
        persistSettingsState()
    }

    /**
     * Update whether sound cues should also vibrate.
     *
     * @param vibrateWithSounds Whether vibration should accompany sound alerts.
     */
    fun updateTimingAlertVibrateWithSounds(vibrateWithSounds: Boolean) {
        _state.update {
            it.copy(
                timingAlertPreferences = it.timingAlertPreferences.copy(
                    vibrateWithSounds = vibrateWithSounds,
                ),
            )
        }
        persistSettingsState()
    }

    /**
     * Update whether countdowns advance automatically when their timers expire.
     *
     * @param automaticallyAdvance Whether timer expiry should drive model transitions.
     */
    fun updateAutomaticallyAdvanceCountdowns(automaticallyAdvance: Boolean) {
        _state.update { it.copy(automaticallyAdvanceCountdowns = automaticallyAdvance) }
        persistSettingsState()
    }

    /**
     * Update whether automatic transitions into live play should lock the live screen.
     *
     * @param automaticallyLock Whether automatic live-point entry should enable lock mode.
     */
    fun updateAutomaticallyLockLivePoint(automaticallyLock: Boolean) {
        _state.update { it.copy(automaticallyLockLivePoint = automaticallyLock) }
        persistSettingsState()
    }

    /**
     * Update whether live-point timeout defense checks use an explicit countdown.
     *
     * @param showDefenseCountdowns Whether to require the observer to start the defense countdown.
     */
    fun updateShowDefenseCountdowns(showDefenseCountdowns: Boolean) {
        _state.update { it.copy(showDefenseCountdowns = showDefenseCountdowns) }
        persistSettingsState()
    }

    /**
     * Update whether ABBA field badges use sequence shorthand.
     *
     * @param showAsSequence Whether ABBA badges should display M1/M2/W1/W2 shorthand.
     */
    fun updateShowAbbaRatioAsSequence(showAsSequence: Boolean) {
        _state.update { it.copy(showAbbaRatioAsSequence = showAsSequence) }
        persistSettingsState()
    }

    /**
     * Update the alert mode for one timing cue.
     *
     * @param cueId The cue whose alert mode should change.
     * @param mode The cue-specific alert mode selected in settings.
     */
    fun updateTimingCueMode(cueId: TimingCueId, mode: TimingAlertMode) {
        _state.update {
            val currentPreferences = it.timingAlertPreferences
            it.copy(
                timingAlertPreferences = currentPreferences.copy(
                    cueModes = currentPreferences.cueModes + (cueId to mode),
                    cueRepeatCounts = if (mode == TimingAlertMode.NONE) {
                        currentPreferences.cueRepeatCounts + (cueId to 1)
                    } else {
                        currentPreferences.cueRepeatCounts
                    },
                ),
            )
        }
        persistSettingsState()
    }

    /**
     * Update the repeat count for one timing cue.
     *
     * @param cueId The cue whose repeat count should change.
     * @param repeatCount The requested repeat count, required to be within the supported range.
     */
    fun updateTimingCueRepeatCount(cueId: TimingCueId, repeatCount: Int) {
        require(repeatCount in MIN_TIMING_ALERT_REPEAT_COUNT..MAX_TIMING_ALERT_REPEAT_COUNT) {
            "Timing alert repeat count must be between $MIN_TIMING_ALERT_REPEAT_COUNT and " +
                "$MAX_TIMING_ALERT_REPEAT_COUNT."
        }
        _state.update {
            it.copy(
                timingAlertPreferences = it.timingAlertPreferences.copy(
                    cueRepeatCounts = it.timingAlertPreferences.cueRepeatCounts + (cueId to repeatCount),
                ),
            )
        }
        persistSettingsState()
    }

    /// Restore all per-cue timing alert modes and repeat counts to defaults.
    fun resetTimingCueSettingsToDefaults() {
        _state.update {
            it.copy(
                timingAlertPreferences = it.timingAlertPreferences.copy(
                    cueModes = defaultTimingCueModes(),
                    cueRepeatCounts = defaultTimingCueRepeatCounts(),
                ),
            )
        }
        persistSettingsState()
    }

    /// Clear the startup recovery notice after the user dismisses it.
    fun dismissStartupRecoveryNotice() {
        _state.update { it.copy(startupRecoveryNotice = null) }
    }

    /// Open the profile screen.
    fun openProfile() {
        openScreen(AppScreen.PROFILE)
    }

    /// Open the About screen.
    fun openAbout() {
        openScreen(AppScreen.ABOUT)
    }

    /// Open the settings screen.
    fun openSettings() {
        openScreen(AppScreen.SETTINGS)
    }

    /// Open the timing cue settings screen.
    fun openTimingCueSettings() {
        openScreen(AppScreen.TIMING_CUE_SETTINGS)
    }

    /// Open the archived games screen.
    fun openArchivedGames() {
        _state.update {
            it.copy(
                viewingArchivedGame = null,
                viewingCurrentGameSummary = false,
                selectedArchiveCategory = null,
                editingSavedSetupIndex = null,
                archiveFilterSelections = ArchiveFilterSelections(),
                archiveSortMode = ArchiveSortMode.DATE_NEWEST,
                screen = AppScreen.ARCHIVED_GAMES,
            )
        }
    }

    /// Return to the archive category landing page while preserving archive filter/sort state.
    fun returnToArchivedGameCategories() {
        _state.update {
            it.copy(
                viewingArchivedGame = null,
                viewingCurrentGameSummary = false,
                selectedArchiveCategory = null,
                editingSavedSetupIndex = null,
            )
        }
    }

    /**
     * Open one category within the archived/saved games screen.
     *
     * @param category The archive category to list.
     */
    fun openArchivedGameCategory(category: ArchivedGameCategory) {
        _state.update { it.copy(selectedArchiveCategory = category) }
    }

    /**
     * Replace one archive checkbox filter's selected values.
     *
     * @param field The filter field to replace.
     * @param values The selected values for that filter.
     */
    fun updateArchiveFilterSelections(field: ArchiveFilterField, values: Set<String>) {
        _state.update {
            it.copy(archiveFilterSelections = it.archiveFilterSelections.withValues(field, values))
        }
    }

    /**
     * Replace the archive date filter.
     *
     * @param dateFilter The date filter to apply, or null to clear it.
     */
    fun updateArchiveDateFilter(dateFilter: ArchiveDateFilter?) {
        _state.update {
            it.copy(
                archiveFilterSelections = it.archiveFilterSelections.copy(dateRange = dateFilter),
            )
        }
    }

    /// Clear one archive filter field.
    fun clearArchiveFilter(field: ArchiveFilterField) {
        _state.update {
            it.copy(archiveFilterSelections = it.archiveFilterSelections.without(field))
        }
    }

    /// Clear all archive filters.
    fun clearArchiveFilterSelections() {
        _state.update { it.copy(archiveFilterSelections = ArchiveFilterSelections()) }
    }

    /**
     * Replace the archive sort mode.
     *
     * @param sortMode The sort mode to apply.
     */
    fun updateArchiveSortMode(sortMode: ArchiveSortMode) {
        _state.update { it.copy(archiveSortMode = sortMode) }
    }

    /// Resume the current setup draft, pre-pull preview, or in-progress game from Home.
    fun resumeCurrentGame() {
        val current = currentGame ?: return
        if (current.phase == GamePhase.SETUP) {
            _state.update {
                it.copy(
                    viewingArchivedGame = null,
                    viewingCurrentGameSummary = false,
                    screen = AppScreen.SETUP,
                )
            }
            return
        }
        if (viewingCurrentGameSummary) {
            _state.update {
                it.copy(
                    viewingArchivedGame = null,
                    viewingCurrentGameSummary = false,
                    selectedArchiveCategory = null,
                    screen = AppScreen.LIVE,
                )
            }
            return
        }
        if (current.phase != GamePhase.GAME_OVER) {
            openScreen(AppScreen.LIVE)
        }
    }

    /// Open the current completed game summary from Home.
    fun openCompletedGame() {
        val current = currentGame ?: return
        if (current.phase == GamePhase.GAME_OVER) {
            openScreen(AppScreen.LIVE)
        }
    }

    /// Open the current game summary from in-progress game navigation.
    fun openCurrentGameSummary() {
        val current = currentGame ?: return
        if (current.phase == GamePhase.GAME_OVER) {
            openCompletedGame()
            return
        }
        _state.update {
            it.copy(
                viewingArchivedGame = null,
                viewingCurrentGameSummary = true,
                screen = AppScreen.LIVE,
            )
        }
    }

    /**
     * Open one archived game summary from archive navigation.
     *
     * @param index The archived-game storage index.
     */
    fun openArchivedGame(index: Int, now: Long) {
        val archived = archivedGames[index]
        if (archived.archiveCategory == ArchivedGameCategory.SETUP) {
            openSavedSetupDraft(index)
            return
        }
        _state.update {
            it.copy(
                viewingArchivedGame = archived,
                viewingCurrentGameSummary = false,
                editingSavedSetupIndex = null,
                screen = AppScreen.ARCHIVED_GAMES,
            )
        }
    }

    /**
     * Open one saved setup draft for in-place editing.
     *
     * @param index The archived-game index to edit.
     */
    private fun openSavedSetupDraft(index: Int) {
        _state.update {
            it.copy(
                setupEditDraft = null,
                editingSavedSetupIndex = index,
                viewingArchivedGame = null,
                viewingCurrentGameSummary = false,
                screen = AppScreen.SETUP,
            )
        }
    }

    /// Move the current completed game into the archived games list.
    fun archiveCompletedGame() {
        val completed = currentGame ?: return
        if (completed.phase != GamePhase.GAME_OVER) {
            return
        }
        val updatedArchivedGames = archivedGames + completed.pruneUndoHistory()
        _state.update {
            it.copy(
                archivedGames = updatedArchivedGames,
                currentGame = null,
                viewingArchivedGame = null,
                viewingCurrentGameSummary = false,
                editingSavedSetupIndex = null,
                selectedArchiveCategory = null,
                screen = AppScreen.HOME,
            )
        }
        persistArchivedGames()
        persistCurrentGame()
    }

    /// Restore the archived game currently open as a summary.
    fun restoreCompletedGame() {
        val archived = viewingArchivedGame ?: return
        val index = archivedGames.indexOfFirst { it === archived }
        val updatedArchivedGames = archivedGamesWithout(index, appendCurrent = true)
        _state.update {
            it.copy(
                archivedGames = updatedArchivedGames,
                currentGame = archived,
                setupEditDraft = null,
                editingSavedSetupIndex = null,
                viewingArchivedGame = null,
                viewingCurrentGameSummary = false,
                selectedArchiveCategory = null,
                screen = AppScreen.LIVE,
            )
        }
        persistArchivedGames()
        persistCurrentGame()
    }

    /// Save the current new-game setup as a phase=SETUP GameState, archive it, and return Home.
    fun saveSetupForLater() {
        // This action is only exposed while creating a setup-phase game.
        val setupGame = currentGame!!
        _state.update {
            it.copy(
                archivedGames = archivedGames + setupGame,
                currentGame = null,
                setupEditDraft = null,
                editingSavedSetupIndex = null,
                viewingArchivedGame = null,
                viewingCurrentGameSummary = false,
                selectedArchiveCategory = null,
                screen = AppScreen.HOME,
            )
        }
        persistArchivedGames()
        persistCurrentGame()
    }

    /**
     * Convert the viewed saved in-progress game to a completed archive and return to its list.
     *
     * @param now The epoch millis to store as the manual game-over time.
     */
    fun archiveSavedInProgressGame(now: Long) {
        // This action is only exposed while viewing a saved in-progress game.
        val archived = viewingArchivedGame!!
        val index = archivedGames.indexOfFirst { it === archived }
        val updatedArchivedGames = archivedGamesWith(index, archived.asCompletedArchive(now))
        _state.update {
            it.copy(
                archivedGames = updatedArchivedGames,
                viewingArchivedGame = null,
                viewingCurrentGameSummary = false,
                editingSavedSetupIndex = null,
                selectedArchiveCategory = ArchivedGameCategory.IN_PROGRESS,
                screen = AppScreen.ARCHIVED_GAMES,
            )
        }
        persistArchivedGames()
    }

    /// Open the saved setup drafts list, leaving any saved-draft edit screen.
    fun openSavedSetupDrafts() {
        _state.update {
            it.copy(
                editingSavedSetupIndex = null,
                setupEditDraft = null,
                viewingArchivedGame = null,
                viewingCurrentGameSummary = false,
                selectedArchiveCategory = ArchivedGameCategory.SETUP,
                screen = AppScreen.ARCHIVED_GAMES,
            )
        }
    }

    /// Promote the edited saved setup draft into the current-game slot.
    fun makeEditedSetupCurrent() {
        val savedIndex = editingSavedSetupIndex!!
        val savedSetup = archivedGames[savedIndex]
        val updatedArchivedGames = archivedGamesWithout(savedIndex, appendCurrent = true)
        _state.update {
            it.copy(
                archivedGames = updatedArchivedGames,
                currentGame = savedSetup,
                setupEditDraft = null,
                editingSavedSetupIndex = null,
                viewingArchivedGame = null,
                viewingCurrentGameSummary = false,
                selectedArchiveCategory = null,
                screen = AppScreen.SETUP,
            )
        }
        persistArchivedGames()
        persistCurrentGame()
    }

    /// Delete the current setup/in-progress/completed game state.
    fun deleteCurrentGame() {
        _state.update { state ->
            val screenAfterDelete = if (state.screen == AppScreen.ARCHIVED_GAMES) {
                AppScreen.ARCHIVED_GAMES
            } else {
                AppScreen.HOME
            }
            val categoryAfterDelete = if (state.screen == AppScreen.ARCHIVED_GAMES) {
                state.selectedArchiveCategory
            } else {
                null
            }
            state.copy(
                currentGame = null,
                setupEditDraft = null,
                editingSavedSetupIndex = null,
                viewingArchivedGame = null,
                viewingCurrentGameSummary = false,
                selectedArchiveCategory = categoryAfterDelete,
                screen = screenAfterDelete,
            )
        }
        persistCurrentGame()
    }

    /**
     * Delete one archived game by index.
     *
     * @param index The archived-game storage index to remove.
     */
    fun deleteArchivedGame(index: Int) {
        val updatedArchivedGames = archivedGamesWithout(index)
        _state.update {
            it.copy(
                archivedGames = updatedArchivedGames,
                viewingArchivedGame = null,
                viewingCurrentGameSummary = false,
            )
        }
        persistArchivedGames()
    }

    /// Delete all games in the currently selected archive category.
    fun deleteArchivedGamesInSelectedCategory() {
        // This action is only exposed from within a selected archive category.
        val category = selectedArchiveCategory!!
        _state.update {
            it.copy(
                archivedGames = archivedGames.filterNot { game ->
                    game.archiveCategory == category
                },
                viewingArchivedGame = null,
                viewingCurrentGameSummary = false,
            )
        }
        persistArchivedGames()
    }

    /**
     * Delete archived games by archive storage index.
     *
     * @param archiveIndices Full archived-game storage indices to delete.
     */
    fun deleteSelectedArchivedGames(archiveIndices: Set<Int>) {
        _state.update {
            it.copy(
                archivedGames = it.archivedGames.filterIndexed { index, _ ->
                    index !in archiveIndices
                },
                viewingArchivedGame = null,
                viewingCurrentGameSummary = false,
            )
        }
        persistArchivedGames()
    }

    /// Delete all archived/saved games.
    fun deleteAllArchivedGames() {
        _state.update {
            it.copy(
                archivedGames = emptyList(),
                viewingArchivedGame = null,
                viewingCurrentGameSummary = false,
                editingSavedSetupIndex = null,
                selectedArchiveCategory = null,
            )
        }
        persistArchivedGames()
    }

    /// Start a new game setup, archiving any existing current game first.
    fun startNewGame(now: Long) {
        val archivedCurrent = archiveCurrentGame()
        val updatedArchivedGames = archivedCurrent?.let { archivedGames + it } ?: archivedGames
        val previousSetupDefaults = updatedArchivedGames.lastOrNull()
        _state.update {
            it.copy(
                archivedGames = updatedArchivedGames,
                currentGame = newSetupGameState(
                    now = now,
                    defaultsFrom = previousSetupDefaults,
                    defaultObserverName = profileName,
                ),
                setupEditDraft = null,
                editingSavedSetupIndex = null,
                viewingArchivedGame = null,
                viewingCurrentGameSummary = false,
                selectedArchiveCategory = null,
                screen = AppScreen.SETUP,
            )
        }
        if (archivedCurrent != null) {
            persistArchivedGames()
        }
        persistCurrentGame()
    }

    /**
     * Finish setup and enter or update the in-progress game.
     *
     * @param now The epoch millis used when applying setup edits to an existing pre-play countdown.
     */
    fun finishSetup(now: Long) {
        val setupEdit = setupEditDraft
        val current = currentGame!!
        val updatedCurrentGame = if (setupEdit == null) {
            current.startGame()
        } else {
            applySetupEditToLiveGame(current, setupEdit, now)
        }
        _state.update {
            it.copy(
                currentGame = updatedCurrentGame,
                setupEditDraft = null,
                editingSavedSetupIndex = null,
                viewingArchivedGame = null,
                viewingCurrentGameSummary = false,
                selectedArchiveCategory = null,
                screen = AppScreen.LIVE,
            )
        }
        persistCurrentGame()
    }

    /**
     * Reopen setup for the current in-progress game when editing is allowed.
     *
     * @param currentGame The current-game state whose setup fields should be edited.
     */
    fun editCurrentGame(currentGame: GameState) {
        if (!currentGame.hasStarted()) {
            reopenSetupDraftFromInitialPreview()
            return
        }
        _state.update {
            it.copy(
                setupEditDraft = currentGame,
                editingSavedSetupIndex = null,
                screen = AppScreen.SETUP,
            )
        }
    }

    /// Discard setup edits for the current game and return to the live screen.
    fun cancelSetupEdit() {
        _state.update {
            it.copy(
                setupEditDraft = null,
                editingSavedSetupIndex = null,
                viewingArchivedGame = null,
                viewingCurrentGameSummary = false,
                selectedArchiveCategory = null,
                screen = AppScreen.LIVE,
            )
        }
    }

    /// Convert the pre-pull preview back into a resumable setup draft.
    private fun reopenSetupDraftFromInitialPreview() {
        _state.update {
            val current = it.currentGame!!
            it.copy(
                currentGame = current.copy(
                    teamOne = current.teamOne.withBlankDefaultSetupName(TeamId.TEAM_ONE),
                    teamTwo = current.teamTwo.withBlankDefaultSetupName(TeamId.TEAM_TWO),
                    phase = GamePhase.SETUP,
                    countdown = null,
                    undoEntry = null,
                    redoEntry = null,
                ),
                setupEditDraft = null,
                editingSavedSetupIndex = null,
                viewingArchivedGame = null,
                viewingCurrentGameSummary = false,
                selectedArchiveCategory = null,
                screen = AppScreen.SETUP,
            )
        }
        persistCurrentGame()
    }

    /// Return this team with a setup placeholder restored to blank.
    private fun TeamState.withBlankDefaultSetupName(teamId: TeamId): TeamState {
        return if (name == teamId.defaultName()) copy(name = "") else this
    }

    /**
     * Open a normal app screen and clear any summary view.
     *
     * @param targetScreen The destination screen to show.
     */
    private fun openScreen(targetScreen: AppScreen) {
        _state.update {
            it.copy(
                viewingArchivedGame = null,
                viewingCurrentGameSummary = false,
                editingSavedSetupIndex = null,
                selectedArchiveCategory = null,
                screen = targetScreen,
            )
        }
    }

    /**
     * Build the archive entry for a current game without losing active restore state.
     */
    private fun archivedGameFor(current: GameState): GameState {
        if (current.phase == GamePhase.GAME_OVER) {
            return current.pruneUndoHistory()
        }
        return current
    }

    /**
     * Return an archive for the current game when it is replaced.
     */
    private fun archiveCurrentGame(): GameState? {
        val current = currentGame ?: return null
        return archivedGameFor(current)
    }

    /// Return an archive copy with one storage index removed.
    private fun archivedGamesWithout(
        index: Int,
        appendCurrent: Boolean = false,
    ): List<GameState> {
        return archivedGames.toMutableList().also { games ->
            games.removeAt(index)
            if (appendCurrent) {
                archiveCurrentGame()?.let { games += it }
            }
        }
    }

    /// Return an archive copy with one storage index replaced.
    private fun archivedGamesWith(
        index: Int,
        updatedGame: GameState,
    ): List<GameState> {
        return archivedGames.toMutableList().also { games ->
            games[index] = updatedGame
        }
    }

    /// Persist the current/setup game bucket.
    private fun persistCurrentGame() {
        appStateStorage.saveCurrentGame(currentGame)
    }

    /// Persist the profile bucket.
    private fun persistProfileState() {
        appStateStorage.saveProfile(
            Profile(
                name = profileName,
                avatarPreference = avatarPreference,
            )
        )
    }

    /// Persist the settings bucket.
    private fun persistSettingsState() {
        appStateStorage.saveSettings(
            Settings(
                automaticallyAdvanceCountdowns = automaticallyAdvanceCountdowns,
                automaticallyLockLivePoint = automaticallyLockLivePoint,
                showDefenseCountdowns = showDefenseCountdowns,
                showAbbaRatioAsSequence = showAbbaRatioAsSequence,
                timingAlertPreferences = timingAlertPreferences,
            )
        )
    }

    /// Persist the archived-games bucket.
    private fun persistArchivedGames() {
        appStateStorage.saveArchivedGames(archivedGames)
    }

    /**
     * Rewrite any buckets that were reset during startup recovery.
     *
     * @param resetAreas The app-data buckets that were repaired to defaults or readable subsets.
     */
    private fun persistRecoveredDataAreas(resetAreas: Set<PersistedData>) {
        if (PersistedData.GAME_STATE in resetAreas) {
            persistCurrentGame()
        }
        if (PersistedData.PROFILE in resetAreas) {
            persistProfileState()
        }
        if (PersistedData.SETTINGS in resetAreas) {
            persistSettingsState()
        }
        if (PersistedData.ARCHIVED_GAMES in resetAreas) {
            persistArchivedGames()
        }
    }

    /**
     * Resolve the avatar shown on Home for a stored avatar preference.
     *
     * @param preference The stored avatar preference; random is resolved through the injected chooser for tests.
     */
    private fun resolveCurrentHomeAvatar(preference: ObserverAvatarPreference): ObserverAvatarPreference {
        if (preference != ObserverAvatarPreference.RANDOM) {
            return preference
        }
        return concreteObserverAvatarPreferences[chooseAvatarIndex(concreteObserverAvatarPreferences.size)]
    }
}
