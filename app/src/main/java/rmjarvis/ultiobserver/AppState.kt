package rmjarvis.ultiobserver

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
    OFFICIAL_CLOCK,
    SETTINGS,
    TIMING_CUE_SETTINGS,
    ARCHIVED_GAMES,
    SETUP,
    LIVE,
}

/** Card workflow currently active on the phone, with a selected player-card type when present. */
internal data class ActiveCardEntry(
    val team: TeamId,
    val cardType: CardType?,
)

/**
 * Immutable snapshot of the app-level state owned by AppState.
 *
 * @param screen The top-level app screen currently routed by the app shell.
 * @param currentGame The mutable current game, including setup drafts and completed games.
 * @param setupEditDraft Uncommitted setup edits for an existing current game.
 * @param editingSavedSetupIndex Archive index of the saved setup draft currently open for edit.
 * @param profile Observer profile data edited on the Profile screen.
 * @param currentHomeAvatar The concrete avatar shown on Home after resolving random choices.
 * @param settings User settings edited on the Settings screens.
 * @param archivedGames The archived game summaries loaded into the app session.
 * @param selectedArchiveCategory The archive category currently open from the category landing page.
 * @param viewingArchivedGame The archived game currently open as a summary.
 * @param viewingCurrentGameSummary Whether the current game is open as a summary.
 * @param archiveFilterSelections Selected filters applied to the archive list.
 * @param archiveSortMode Sort order applied to the archive list.
 * @param startupRecoveryNotice The startup data-recovery notice, if corrupted app data was reset.
 * @param activeCardEntry Card workflow currently active on the phone.
 */
internal data class AppStateSnapshot(
    val screen: AppScreen = AppScreen.HOME,
    val currentGame: GameState?,
    val setupEditDraft: GameState?,
    val editingSavedSetupIndex: Int?,
    val profile: Profile,
    val currentHomeAvatar: ObserverAvatarPreference,
    val settings: Settings,
    val archivedGames: List<GameState>,
    val selectedArchiveCategory: ArchivedGameCategory?,
    val viewingArchivedGame: GameState?,
    val viewingCurrentGameSummary: Boolean,
    val archiveFilterSelections: ArchiveFilterSelections,
    val archiveSortMode: ArchiveSortMode,
    val startupRecoveryNotice: RecoveryNotice?,
    val activeCardEntry: ActiveCardEntry?,
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

    /// Return whether the regular interactive active-game screen is currently visible.
    val viewingActiveGameScreen: Boolean
        get() = screen == AppScreen.LIVE &&
            !viewingCurrentGameSummary

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
 * Process-wide holder of app state shared by the Android UI and background services.
 *
 * This process-wide state holder is scoped to UltiObserverApplication.
 * It survives normal Activity recreation, such as rotation, so the UI can be rebuilt without
 * losing in-memory state; process restart recovery still comes from the app's storage layer.
 *
 * AppState owns top-level navigation, current-game state, profile state,
 * settings, archived-game lists, startup recovery notices, and the app actions that persist or
 * move between those states. Domain rules stay in the model helpers.
 *
 * @param appStateStorage The persistence boundary used to load and save app state buckets.
 * @param chooseAvatarIndex Random-avatar chooser injected so tests can make selection deterministic.
 */
internal class AppState(
    private val appStateStorage: AppStateStorage,
    // Injected so tests can make random avatar selection deterministic.
    private val chooseAvatarIndex: (Int) -> Int = { size -> Random.nextInt(size) },
) {
    private val persistedCurrentGame = appStateStorage.loadCurrentGame()
    private val persistedProfile = appStateStorage.loadProfile() ?: Profile()
    private val persistedSettings = appStateStorage.loadSettings() ?: Settings()
    private val restoredArchivedGames = appStateStorage.loadArchivedGames()
    private val recoveredPersistedDataAreas = appStateStorage.resetPersistedDataAreas

    private val _state = MutableStateFlow(
        AppStateSnapshot(
            currentGame = persistedCurrentGame,
            setupEditDraft = null,
            editingSavedSetupIndex = null,
            profile = persistedProfile,
            currentHomeAvatar = resolveCurrentHomeAvatar(persistedProfile.avatarPreference),
            settings = persistedSettings,
            archivedGames = restoredArchivedGames,
            selectedArchiveCategory = null,
            viewingArchivedGame = null,
            viewingCurrentGameSummary = false,
            archiveFilterSelections = ArchiveFilterSelections(),
            archiveSortMode = ArchiveSortMode.DATE_NEWEST,
            startupRecoveryNotice = recoveredPersistedDataAreas.takeIf { it.isNotEmpty() }?.let { resetAreas ->
                RecoveryNotice(resetAreas)
            },
            activeCardEntry = null,
        )
    )

    val state: StateFlow<AppStateSnapshot> = _state.asStateFlow()

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
    val profile: Profile
        get() = state.value.profile
    val currentHomeAvatar: ObserverAvatarPreference
        get() = state.value.currentHomeAvatar
    val settings: Settings
        get() = state.value.settings
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
    @Synchronized
    fun updateCurrentGame(updatedGame: GameState) {
        // All current-game event logging flows through this AppState boundary.
        _state.update { it.copy(currentGame = updatedGame) }
        persistCurrentGame()
    }

    /**
     * Replace the current game only if the action was based on the latest committed state.
     *
     * Phone UI callbacks and Wear OS requests can calculate updates from the same immutable game
     * while running on different threads. Checking the base game before committing prevents a
     * later stale update from overwriting a game change already recorded by the other surface.
     *
     * @param expectedCurrentGame The current game from which updatedGame was calculated.
     * @param updatedGame The resulting game state to commit.
     * @return Whether expectedCurrentGame was still current and the update was applied.
     */
    @Synchronized
    fun updateCurrentGame(
        expectedCurrentGame: GameState,
        updatedGame: GameState,
    ): Boolean {
        if (currentGame != expectedCurrentGame) {
            return false
        }
        updateCurrentGame(updatedGame)
        return true
    }

    /**
     * Record a goal calculated from the exact current game seen by the initiating surface.
     *
     * Both the phone UI and Wear OS use this action so countdown adjustment, goal rules,
     * conditional state replacement, and persistence follow one path.
     *
     * This function returns false if the goal could not be recorded, e.g. because some other
     * thread already updated the game some other way, causing currentGame to no longer be
     * current.
     *
     * @param currentGame The current game displayed when the goal action was initiated.
     * @param scoringTeam The team credited with the goal.
     * @param now Current unadjusted phone epoch millis when the action is handled.
     * @return Whether the goal was successfully recorded.
     */
    @Synchronized
    fun recordGoal(
        currentGame: GameState,
        scoringTeam: TeamId,
        now: Long,
    ): Boolean {
        val goalEpoch = settings.adjustedCountdownStartEpoch(now)
        val updatedGame = currentGame.recordGoalFromCurrentState(scoringTeam, goalEpoch)
        return updateCurrentGame(
            expectedCurrentGame = currentGame,
            updatedGame = updatedGame,
        )
    }

    /**
     * Resolve the pending game decision displayed by the initiating surface.
     *
     * Both the phone UI and Wear OS use this action so accepting or deferring a decision,
     * conditional state replacement, and persistence follow one path.
     *
     * @param currentGame The current game containing the decision being resolved.
     * @param accept Whether to accept the decision rather than defer it.
     * @param now Current unadjusted phone epoch millis when the decision is resolved.
     * @return Whether the decision was still current and was successfully resolved.
     */
    @Synchronized
    fun resolveDecision(
        currentGame: GameState,
        accept: Boolean,
        now: Long,
    ): Boolean {
        val decision = currentGame.pendingGameDecision() ?: return false
        val updatedGame = if (accept) decision.accept(now) else decision.defer()
        return updateCurrentGame(
            expectedCurrentGame = currentGame,
            updatedGame = updatedGame,
        )
    }

    /**
     * Commit an action after the initiating surface receives the observer's confirmation.
     *
     * Both the phone UI and Wear OS submit the same confirmation object so the action is derived
     * from the game on which its preview was based. The conditional update prevents a confirmation
     * from overwriting an intervening game action from either surface.
     *
     * @param confirmation The pending action, its request time, and the game state from which it
     * was requested.
     * @return Whether that game was still current and the confirmed action was committed.
     */
    @Synchronized
    fun confirmAction(
        confirmation: GamePrompt.ActionConfirmation,
    ): Boolean {
        return updateCurrentGame(
            expectedCurrentGame = confirmation.state,
            updatedGame = confirmation.confirm(),
        )
    }

    /** Conditionally replace the active card workflow shared by the phone UI and Wear OS. */
    @Synchronized
    fun updateCardEntry(
        currentGame: GameState,
        expectedCardEntry: ActiveCardEntry?,
        updatedCardEntry: ActiveCardEntry?,
    ): Boolean {
        val snapshot = state.value
        if (
            snapshot.currentGame != currentGame ||
            snapshot.activeCardEntry != expectedCardEntry
        ) {
            return false
        }
        _state.update {
            it.copy(activeCardEntry = updatedCardEntry)
        }
        return true
    }

    /** Commit a card action and close its exact active phone workflow atomically. */
    @Synchronized
    fun completeCardEntry(
        currentGame: GameState,
        updatedGame: GameState,
        entry: ActiveCardEntry,
    ): Boolean {
        val snapshot = state.value
        if (snapshot.currentGame != currentGame || snapshot.activeCardEntry != entry) {
            return false
        }
        _state.update {
            it.copy(
                currentGame = updatedGame,
                activeCardEntry = null,
            )
        }
        persistCurrentGame()
        return true
    }

    /// Replace the profile bucket and refresh derived profile state.
    fun updateProfile(updatedProfile: Profile) {
        _state.update {
            it.copy(
                profile = updatedProfile,
                currentHomeAvatar = if (updatedProfile.avatarPreference == it.profile.avatarPreference) {
                    it.currentHomeAvatar
                } else {
                    resolveCurrentHomeAvatar(updatedProfile.avatarPreference)
                },
            )
        }
        persistProfileState()
    }

    /// Replace the settings bucket.
    fun updateSettings(updatedSettings: Settings) {
        _state.update { it.copy(settings = updatedSettings) }
        persistSettingsState()
    }

    /**
     * Turn off watch notifications when Android notifications are unavailable.
     *
     * This reconciles persisted app settings with Android's notification state when the Activity
     * starts. An unchanged setting is not rewritten.
     *
     * @param notificationsEnabled Whether Android currently allows UltiObserver notifications.
     */
    fun disableWatchNotificationsIfUnavailable(notificationsEnabled: Boolean) {
        if (
            !notificationsEnabled &&
            settings.timingAlerts.watchConnectionMode.usesNotifications()
        ) {
            updateSettings(
                settings.withTimingAlerts(
                    settings.timingAlerts.withWatchConnectionMode(WatchConnectionMode.OFF)
                )
            )
        }
    }

    /// Update the persisted official-clock offset and the current game's clock mapping.
    fun updateOfficialClockOffset(updatedOffsetMillis: Long) {
        if (updatedOffsetMillis == settings.officialClockOffsetMillis) {
            return
        }
        _state.update {
            it.copy(
                settings = it.settings.copy(
                    officialClockOffsetMillis = updatedOffsetMillis,
                ),
                currentGame = it.currentGame?.withOfficialClockOffset(updatedOffsetMillis),
            )
        }
        persistSettingsState()
        if (currentGame != null) {
            persistCurrentGame()
        }
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

    /// Open the official tournament clock screen.
    fun openOfficialClock() {
        openScreen(AppScreen.OFFICIAL_CLOCK)
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

    /**
     * Resume the current game from Home or from its Game summary page.
     */
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
        if (current.phase == GamePhase.GAME_OVER) {
            openCurrentGameSummary()
            return
        }
        openScreen(AppScreen.LIVE)
    }

    /// Open the current game summary from in-progress game navigation.
    fun openCurrentGameSummary() {
        if (currentGame == null) {
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
     * Replace the archived game currently open as a summary and persist that archive row.
     *
     * @param updatedGame The edited game state returned by the summary workflow.
     */
    fun updateViewingArchivedGame(updatedGame: GameState) {
        val archived = viewingArchivedGame!!
        val index = archivedGames.indexOfFirst { it === archived }
        _state.update {
            it.copy(
                archivedGames = archivedGamesWith(index, updatedGame),
                viewingArchivedGame = updatedGame,
            )
        }
        persistArchivedGames()
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
                activeCardEntry = null,
                screen = AppScreen.HOME,
            )
        }
        persistArchivedGames()
        persistCurrentGame()
    }

    /// Make the archived game currently open as a summary the current game.
    fun makeArchivedGameCurrent() {
        val archived = viewingArchivedGame ?: return
        val index = archivedGames.indexOfFirst { it === archived }
        val updatedArchivedGames = archivedGamesWithout(index, appendCurrent = true)
        _state.update {
            it.copy(
                archivedGames = updatedArchivedGames,
                currentGame = archived.withOfficialClockOffset(
                    settings.officialClockOffsetMillis,
                ),
                setupEditDraft = null,
                editingSavedSetupIndex = null,
                viewingArchivedGame = null,
                viewingCurrentGameSummary = archived.phase == GamePhase.GAME_OVER,
                selectedArchiveCategory = null,
                activeCardEntry = null,
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
                activeCardEntry = null,
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
                currentGame = savedSetup.withOfficialClockOffset(
                    settings.officialClockOffsetMillis,
                ),
                setupEditDraft = null,
                editingSavedSetupIndex = null,
                viewingArchivedGame = null,
                viewingCurrentGameSummary = false,
                selectedArchiveCategory = null,
                activeCardEntry = null,
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
                activeCardEntry = null,
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
                    officialClockOffsetMillis = settings.officialClockOffsetMillis,
                    defaultsFrom = previousSetupDefaults,
                    defaultObserverName = profile.name,
                ),
                setupEditDraft = null,
                editingSavedSetupIndex = null,
                viewingArchivedGame = null,
                viewingCurrentGameSummary = false,
                selectedArchiveCategory = null,
                activeCardEntry = null,
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
            current.startGame(settings.orientationPreference)
        } else {
            applySetupEditToActiveGame(current, setupEdit, now)
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
        appStateStorage.saveProfile(profile)
    }

    /// Persist the settings bucket.
    private fun persistSettingsState() {
        appStateStorage.saveSettings(settings)
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
