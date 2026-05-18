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
 * @param setupState The current setup form state, including drafts and setup edits.
 * @param liveState The mutable current game, or null when only a setup draft exists.
 * @param setupMode Whether setup is creating a new game or editing the current game.
 * @param profileName The observer profile name.
 * @param avatarPreference The stored observer avatar preference.
 * @param homeAvatarPreference The concrete avatar shown on Home after resolving random choices.
 * @param timingAlertPreferences The current timing cue alert settings.
 * @param automaticallyAdvanceCountdowns Whether active countdowns transition automatically at expiry.
 * @param automaticallyLockLivePoint Whether automatic live-point transitions enable lock mode.
 * @param archivedGames The archived game summaries loaded into the app session.
 * @param viewingArchivedGame The archived game currently open as a read-only summary.
 * @param hasSetupDraft Whether Home should expose a resumable setup draft.
 * @param startupRecoveryNotice The startup data-recovery notice, if corrupted app data was reset.
 */
internal data class AppUiState(
    val screen: AppScreen = AppScreen.HOME,
    val setupState: GameSetupState,
    val liveState: LiveGameState?,
    val setupMode: SetupMode,
    val profileName: String,
    val avatarPreference: ObserverAvatarPreference,
    val homeAvatarPreference: ObserverAvatarPreference,
    val timingAlertPreferences: TimingAlertPreferences,
    val automaticallyAdvanceCountdowns: Boolean,
    val automaticallyLockLivePoint: Boolean,
    val archivedGames: List<ArchivedGame>,
    val viewingArchivedGame: ArchivedGame?,
    val hasSetupDraft: Boolean,
    val startupRecoveryNotice: RecoveryNotice?,
)

/**
 * App-level state coordinator for the Android UI.
 *
 * An Android ViewModel is a lifecycle-aware state holder scoped to an Activity or UI flow.
 * It survives normal Activity recreation, such as rotation, so the UI can be rebuilt without
 * losing in-memory state; process restart recovery still comes from the app's storage layer.
 *
 * UltiObserver's AppViewModel owns top-level navigation, setup/live-game state, profile state,
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
    private val persistedCurrentGameState = appStateStorage.loadCurrentGameState()
    private val persistedProfile = appStateStorage.loadProfile()
    private val persistedSettings = appStateStorage.loadSettings()
    private val restoredSetupDraft = persistedCurrentGameState?.hasSetupDraft ?: false
    private val restoredArchivedGames = appStateStorage.loadArchivedGames()
    private val recoveredPersistedDataAreas = appStateStorage.resetPersistedDataAreas

    private val _state = MutableStateFlow(
        AppUiState(
            setupState = persistedCurrentGameState?.setupState ?: newGameSetupState(),
            liveState = persistedCurrentGameState?.liveState,
            setupMode = persistedCurrentGameState?.setupMode ?: SetupMode.NEW_GAME,
            profileName = persistedProfile?.profileName ?: "",
            avatarPreference = persistedProfile?.avatarPreference ?: ObserverAvatarPreference.RANDOM,
            homeAvatarPreference = resolveHomeAvatarPreference(
                persistedProfile?.avatarPreference ?: ObserverAvatarPreference.RANDOM
            ),
            timingAlertPreferences = persistedSettings?.timingAlertPreferences ?: TimingAlertPreferences(),
            automaticallyAdvanceCountdowns = persistedSettings?.automaticallyAdvanceCountdowns ?: true,
            automaticallyLockLivePoint = persistedSettings?.automaticallyLockLivePoint ?: true,
            archivedGames = restoredArchivedGames,
            viewingArchivedGame = null,
            hasSetupDraft = restoredSetupDraft,
            startupRecoveryNotice = recoveredPersistedDataAreas.takeIf { it.isNotEmpty() }?.let { resetAreas ->
                RecoveryNotice(resetAreas)
            },
        )
    )

    val state: StateFlow<AppUiState> = _state.asStateFlow()

    var screen: AppScreen
        get() = state.value.screen
        private set(value) {
            _state.update { it.copy(screen = value) }
        }
    var setupState: GameSetupState
        get() = state.value.setupState
        private set(value) {
            _state.update { it.copy(setupState = value) }
        }
    var liveState: LiveGameState?
        get() = state.value.liveState
        private set(value) {
            _state.update { it.copy(liveState = value) }
        }
    var setupMode: SetupMode
        get() = state.value.setupMode
        private set(value) {
            _state.update { it.copy(setupMode = value) }
        }
    var profileName: String
        get() = state.value.profileName
        private set(value) {
            _state.update { it.copy(profileName = value) }
        }
    var avatarPreference: ObserverAvatarPreference
        get() = state.value.avatarPreference
        private set(value) {
            _state.update { it.copy(avatarPreference = value) }
        }
    var homeAvatarPreference: ObserverAvatarPreference
        get() = state.value.homeAvatarPreference
        private set(value) {
            _state.update { it.copy(homeAvatarPreference = value) }
        }
    var timingAlertPreferences: TimingAlertPreferences
        get() = state.value.timingAlertPreferences
        private set(value) {
            _state.update { it.copy(timingAlertPreferences = value) }
        }
    var automaticallyAdvanceCountdowns: Boolean
        get() = state.value.automaticallyAdvanceCountdowns
        private set(value) {
            _state.update { it.copy(automaticallyAdvanceCountdowns = value) }
        }
    var automaticallyLockLivePoint: Boolean
        get() = state.value.automaticallyLockLivePoint
        private set(value) {
            _state.update { it.copy(automaticallyLockLivePoint = value) }
        }
    var archivedGames: List<ArchivedGame>
        get() = state.value.archivedGames
        private set(value) {
            _state.update { it.copy(archivedGames = value) }
        }
    var viewingArchivedGame: ArchivedGame?
        get() = state.value.viewingArchivedGame
        private set(value) {
            _state.update { it.copy(viewingArchivedGame = value) }
        }
    var hasSetupDraft: Boolean
        get() = state.value.hasSetupDraft
        private set(value) {
            _state.update { it.copy(hasSetupDraft = value) }
        }
    var startupRecoveryNotice: RecoveryNotice?
        get() = state.value.startupRecoveryNotice
        private set(value) {
            _state.update { it.copy(startupRecoveryNotice = value) }
        }

    init {
        persistRecoveredDataAreas(recoveredPersistedDataAreas)
    }

    val currentLiveState: LiveGameState?
        get() = viewingArchivedGame?.state ?: liveState

    val viewingReadOnlySummary: Boolean
        get() = viewingArchivedGame != null

    val currentGameHomeSubtitle: String?
        get() {
            val current = liveState
            return when {
                current?.isInitialLivePreview() == true -> "Tap to resume"
                current == null && hasSetupDraft -> "Tap to resume"
                current != null && current.phase != LivePhase.GAME_OVER -> "Tap to resume"
                else -> null
            }
        }

    /// Navigate to Home and clear any read-only archived-game view.
    fun goHome() {
        _state.update {
            it.copy(
                screen = AppScreen.HOME,
                viewingArchivedGame = null,
            )
        }
    }

    /// Navigate back according to the current top-level screen and live-game state.
    fun goBackFromCurrentScreen() {
        if (screen == AppScreen.TIMING_CUE_SETTINGS) {
            openSettings()
            return
        }

        if (screen != AppScreen.LIVE || viewingArchivedGame != null) {
            goHome()
            return
        }

        val current = liveState!!
        if (current.isInitialLivePreview()) {
            reopenSetupDraftFromInitialPreview()
            return
        }

        goHome()
    }

    /**
     * Replace setup form state and preserve a new-game draft when appropriate.
     *
     * @param updatedSetup The setup form state produced by the UI.
     */
    fun updateSetup(updatedSetup: GameSetupState) {
        setupState = updatedSetup
        if (setupMode == SetupMode.NEW_GAME) {
            hasSetupDraft = true
        }
        persistCurrentGameState()
    }

    /**
     * Replace the mutable live game when the app is not viewing an archived summary.
     *
     * @param updatedGame The live-game state returned from a model action.
     */
    fun updateLiveGame(updatedGame: LiveGameState) {
        if (viewingArchivedGame == null) {
            // All live game event logging flows through this ViewModel boundary.
            liveState = updatedGame
            persistCurrentGameState()
        }
    }

    /**
     * Update the observer profile name.
     *
     * @param updatedName The profile name entered by the user.
     */
    fun updateProfileName(updatedName: String) {
        profileName = updatedName
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
                homeAvatarPreference = resolveHomeAvatarPreference(updatedPreference),
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
        timingAlertPreferences = timingAlertPreferences.copy(globalMode = mode)
        persistSettingsState()
    }

    /**
     * Update timing-alert playback volume.
     *
     * @param volume The new sound volume value from settings.
     */
    fun updateTimingAlertSoundVolume(volume: Float) {
        timingAlertPreferences = timingAlertPreferences.copy(soundVolume = volume)
        persistSettingsState()
    }

    /**
     * Update timing-alert vibration length.
     *
     * @param durationMillis The requested vibration duration in milliseconds.
     */
    fun updateTimingAlertVibrationDuration(durationMillis: Long) {
        timingAlertPreferences = timingAlertPreferences.copy(vibrationDurationMillis = durationMillis)
        persistSettingsState()
    }

    /**
     * Update whether sound cues should also vibrate.
     *
     * @param vibrateWithSounds Whether vibration should accompany sound alerts.
     */
    fun updateTimingAlertVibrateWithSounds(vibrateWithSounds: Boolean) {
        timingAlertPreferences = timingAlertPreferences.copy(vibrateWithSounds = vibrateWithSounds)
        persistSettingsState()
    }

    /**
     * Update whether countdowns advance automatically when their timers expire.
     *
     * @param automaticallyAdvance Whether timer expiry should drive model transitions.
     */
    fun updateAutomaticallyAdvanceCountdowns(automaticallyAdvance: Boolean) {
        automaticallyAdvanceCountdowns = automaticallyAdvance
        persistSettingsState()
    }

    /**
     * Update whether automatic transitions into live play should lock the live screen.
     *
     * @param automaticallyLock Whether automatic live-point entry should enable lock mode.
     */
    fun updateAutomaticallyLockLivePoint(automaticallyLock: Boolean) {
        automaticallyLockLivePoint = automaticallyLock
        persistSettingsState()
    }

    /**
     * Update the alert mode for one timing cue.
     *
     * @param cueId The cue whose alert mode should change.
     * @param mode The cue-specific alert mode selected in settings.
     */
    fun updateTimingCueMode(cueId: TimingCueId, mode: TimingAlertMode) {
        timingAlertPreferences = timingAlertPreferences.copy(
            cueModes = timingAlertPreferences.cueModes + (cueId to mode),
            cueRepeatCounts = if (mode == TimingAlertMode.NONE) {
                timingAlertPreferences.cueRepeatCounts + (cueId to DEFAULT_TIMING_ALERT_REPEAT_COUNT)
            } else {
                timingAlertPreferences.cueRepeatCounts
            },
        )
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
        timingAlertPreferences = timingAlertPreferences.copy(
            cueRepeatCounts = timingAlertPreferences.cueRepeatCounts + (cueId to repeatCount),
        )
        persistSettingsState()
    }

    /// Restore all per-cue timing alert modes and repeat counts to defaults.
    fun resetTimingCueSettingsToDefaults() {
        timingAlertPreferences = timingAlertPreferences.copy(
            cueModes = defaultTimingCueModes(),
            cueRepeatCounts = defaultTimingCueRepeatCounts(),
        )
        persistSettingsState()
    }

    /// Clear the startup recovery notice after the user dismisses it.
    fun dismissStartupRecoveryNotice() {
        startupRecoveryNotice = null
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
        openScreen(AppScreen.ARCHIVED_GAMES)
    }

    /// Resume the current setup draft, initial live preview, or active live game from Home.
    fun resumeCurrentGame() {
        val current = liveState
        if (current == null) {
            resumeSetupDraft()
            return
        }
        if (current.isInitialLivePreview()) {
            reopenSetupDraftFromInitialPreview()
            return
        }
        if (current.phase != LivePhase.GAME_OVER) {
            openScreen(AppScreen.LIVE)
        }
    }

    /// Resume a saved setup draft when no live game exists yet.
    fun resumeSetupDraft() {
        if (liveState == null && hasSetupDraft) {
            _state.update {
                it.copy(
                    viewingArchivedGame = null,
                    setupMode = SetupMode.NEW_GAME,
                    screen = AppScreen.SETUP,
                )
            }
            persistCurrentGameState()
        }
    }

    /// Open the current completed game summary from Home.
    fun openCompletedGame() {
        val current = liveState ?: return
        if (current.phase == LivePhase.GAME_OVER) {
            openScreen(AppScreen.LIVE)
        }
    }

    /**
     * Open one archived game as a read-only summary.
     *
     * @param index The archived-game index in the displayed Archived Games list.
     */
    fun openArchivedGame(index: Int) {
        val archived = archivedGames.getOrNull(index) ?: return
        _state.update {
            it.copy(
                viewingArchivedGame = archived,
                screen = AppScreen.LIVE,
            )
        }
    }

    /// Move the current completed game into the archived games list.
    fun archiveCompletedGame() {
        val completed = liveState ?: return
        if (completed.phase != LivePhase.GAME_OVER) {
            return
        }
        val updatedArchivedGames = archivedGames + ArchivedGame(
            completed.pruneUndoHistory(),
            "",
        )
        _state.update {
            it.copy(
                archivedGames = updatedArchivedGames,
                liveState = null,
                viewingArchivedGame = null,
            )
        }
        persistArchivedGames()
        persistCurrentGameState()
    }

    /// Delete the current live/setup/completed game state and return Home.
    fun deleteCurrentGame() {
        _state.update {
            it.copy(
                liveState = null,
                viewingArchivedGame = null,
                setupMode = SetupMode.NEW_GAME,
                hasSetupDraft = false,
                screen = AppScreen.HOME,
            )
        }
        persistCurrentGameState()
    }

    /**
     * Delete one archived game by index.
     *
     * @param index The archived-game index to remove.
     */
    fun deleteArchivedGame(index: Int) {
        if (archivedGames.getOrNull(index) == null) {
            return
        }
        val updatedArchivedGames = archivedGames.toMutableList().also { it.removeAt(index) }
        _state.update {
            it.copy(
                archivedGames = updatedArchivedGames,
                viewingArchivedGame = null,
            )
        }
        persistArchivedGames()
    }

    /// Delete all archived games.
    fun deleteAllArchivedGames() {
        _state.update {
            it.copy(
                archivedGames = emptyList(),
                viewingArchivedGame = null,
            )
        }
        persistArchivedGames()
    }

    /// Start a new game setup, archiving any existing current game first.
    fun startNewGame() {
        var shouldPersistArchivedGames = false
        var updatedArchivedGames = archivedGames
        liveState?.let { existing ->
            updatedArchivedGames = updatedArchivedGames + ArchivedGame(
                if (existing.phase == LivePhase.GAME_OVER) {
                    existing
                } else {
                    existing.copy(
                        phase = LivePhase.GAME_OVER,
                        endEpoch = System.currentTimeMillis(),
                    )
                }.pruneUndoHistory(),
                if (existing.phase == LivePhase.GAME_OVER) "" else "Closed when new game started",
            )
            shouldPersistArchivedGames = true
        }
        _state.update {
            it.copy(
                archivedGames = updatedArchivedGames,
                setupState = newGameSetupState(rules = updatedArchivedGames.lastOrNull()?.state?.rules ?: GameRules()),
                liveState = null,
                viewingArchivedGame = null,
                setupMode = SetupMode.NEW_GAME,
                hasSetupDraft = true,
                screen = AppScreen.SETUP,
            )
        }
        if (shouldPersistArchivedGames) {
            persistArchivedGames()
        }
        persistCurrentGameState()
    }

    /**
     * Finish setup and enter or update the live game.
     *
     * @param now The epoch millis used when applying setup edits to an existing pre-play countdown.
     */
    fun finishSetup(now: Long = System.currentTimeMillis()) {
        val updatedLiveState = if (setupMode == SetupMode.NEW_GAME) {
            createLiveGameState(setupState)
        } else {
            applySetupToLiveGame(liveState!!, setupState, now)
        }
        _state.update {
            it.copy(
                liveState = updatedLiveState,
                viewingArchivedGame = null,
                hasSetupDraft = false,
                setupMode = SetupMode.EDIT_CURRENT_GAME,
                screen = AppScreen.LIVE,
            )
        }
        persistCurrentGameState()
    }

    /**
     * Reopen setup for the current live game when editing is allowed.
     *
     * @param currentGame The live-game state whose setup fields should be edited.
     */
    fun editCurrentGame(currentGame: LiveGameState) {
        if (viewingArchivedGame != null) {
            return
        }
        if (currentGame.isInitialLivePreview()) {
            reopenSetupDraftFromInitialPreview()
            return
        }
        _state.update {
            it.copy(
                setupState = currentGame.toSetupState(),
                setupMode = SetupMode.EDIT_CURRENT_GAME,
                screen = AppScreen.SETUP,
            )
        }
        persistCurrentGameState()
    }

    /// Convert the initial live preview back into a resumable setup draft.
    private fun reopenSetupDraftFromInitialPreview() {
        _state.update {
            it.copy(
                liveState = null,
                viewingArchivedGame = null,
                setupMode = SetupMode.NEW_GAME,
                hasSetupDraft = true,
                screen = AppScreen.SETUP,
            )
        }
        persistCurrentGameState()
    }

    /**
     * Open a normal app screen and clear any archived summary left from read-only viewing.
     *
     * @param targetScreen The destination screen to show.
     */
    private fun openScreen(targetScreen: AppScreen) {
        _state.update {
            it.copy(
                viewingArchivedGame = null,
                screen = targetScreen,
            )
        }
    }

    /// Persist the current/setup game bucket.
    private fun persistCurrentGameState() {
        appStateStorage.saveCurrentGameState(
            CurrentGameSnapshot(
                setupState = setupState,
                liveState = liveState,
                setupMode = setupMode,
                hasSetupDraft = hasSetupDraft,
            )
        )
    }

    /// Persist the profile bucket.
    private fun persistProfileState() {
        appStateStorage.saveProfile(
            Profile(
                profileName = profileName,
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
            persistCurrentGameState()
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
    private fun resolveHomeAvatarPreference(preference: ObserverAvatarPreference): ObserverAvatarPreference {
        if (preference != ObserverAvatarPreference.RANDOM) {
            return preference
        }
        return concreteObserverAvatarPreferences[chooseAvatarIndex(concreteObserverAvatarPreferences.size)]
    }
}
