package rmjarvis.ultiobserver

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.serialization.Serializable
import kotlin.random.Random

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

@Serializable
internal enum class SetupMode {
    NEW_GAME,
    EDIT_CURRENT_GAME,
}

/**
 * Coordinate app-level state for the Android UI.
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

    var screen by mutableStateOf(AppScreen.HOME)
        private set
    var setupState by mutableStateOf(persistedCurrentGameState?.setupState ?: newGameSetupState())
        private set
    var liveState by mutableStateOf(persistedCurrentGameState?.liveState)
        private set
    var setupMode by mutableStateOf(persistedCurrentGameState?.setupMode ?: SetupMode.NEW_GAME)
        private set
    var profileName by mutableStateOf(persistedProfile?.profileName ?: "")
        private set
    var avatarPreference by mutableStateOf(
        persistedProfile?.avatarPreference ?: ObserverAvatarPreference.RANDOM
    )
        private set
    var homeAvatarPreference by mutableStateOf(resolveHomeAvatarPreference(avatarPreference))
        private set
    var timingAlertPreferences by mutableStateOf(
        persistedSettings?.timingAlertPreferences ?: TimingAlertPreferences()
    )
        private set
    var automaticallyAdvanceCountdowns by mutableStateOf(
        persistedSettings?.automaticallyAdvanceCountdowns ?: true
    )
        private set
    var automaticallyLockLivePoint by mutableStateOf(
        persistedSettings?.automaticallyLockLivePoint ?: true
    )
        private set
    var archivedGames by mutableStateOf(restoredArchivedGames)
        private set
    var viewingArchivedGame by mutableStateOf<ArchivedGame?>(null)
        private set
    var hasSetupDraft by mutableStateOf(restoredSetupDraft)
        private set
    var startupRecoveryNotice by mutableStateOf(
        recoveredPersistedDataAreas.takeIf { it.isNotEmpty() }?.let { resetAreas ->
            RecoveryNotice(resetAreas)
        }
    )
        private set

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
        screen = AppScreen.HOME
        clearViewedArchivedGame()
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
        avatarPreference = updatedPreference
        homeAvatarPreference = resolveHomeAvatarPreference(updatedPreference)
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
        clearViewedArchivedGame()
        screen = AppScreen.PROFILE
    }

    /// Open the About screen.
    fun openAbout() {
        clearViewedArchivedGame()
        screen = AppScreen.ABOUT
    }

    /// Open the settings screen.
    fun openSettings() {
        clearViewedArchivedGame()
        screen = AppScreen.SETTINGS
    }

    /// Open the timing cue settings screen.
    fun openTimingCueSettings() {
        clearViewedArchivedGame()
        screen = AppScreen.TIMING_CUE_SETTINGS
    }

    /// Open the archived games screen.
    fun openArchivedGames() {
        clearViewedArchivedGame()
        screen = AppScreen.ARCHIVED_GAMES
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
            clearViewedArchivedGame()
            screen = AppScreen.LIVE
        }
    }

    /// Resume a saved setup draft when no live game exists yet.
    fun resumeSetupDraft() {
        if (liveState == null && hasSetupDraft) {
            clearViewedArchivedGame()
            setupMode = SetupMode.NEW_GAME
            screen = AppScreen.SETUP
            persistCurrentGameState()
        }
    }

    /// Open the current completed game summary from Home.
    fun openCompletedGame() {
        val current = liveState ?: return
        if (current.phase == LivePhase.GAME_OVER) {
            clearViewedArchivedGame()
            screen = AppScreen.LIVE
        }
    }

    /**
     * Open one archived game as a read-only summary.
     *
     * @param index The archived-game index in the displayed Archived Games list.
     */
    fun openArchivedGame(index: Int) {
        val archived = archivedGames.getOrNull(index) ?: return
        viewingArchivedGame = archived
        screen = AppScreen.LIVE
    }

    /// Move the current completed game into the archived games list.
    fun archiveCompletedGame() {
        val completed = liveState ?: return
        if (completed.phase != LivePhase.GAME_OVER) {
            return
        }
        archivedGames = archivedGames + ArchivedGame(
            completed.pruneUndoHistory(),
            "",
        )
        liveState = null
        clearViewedArchivedGame()
        persistArchivedGames()
        persistCurrentGameState()
    }

    /// Delete the current live/setup/completed game state and return Home.
    fun deleteCurrentGame() {
        liveState = null
        clearViewedArchivedGame()
        setupMode = SetupMode.NEW_GAME
        hasSetupDraft = false
        screen = AppScreen.HOME
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
        archivedGames = archivedGames.toMutableList().also { it.removeAt(index) }
        clearViewedArchivedGame()
        persistArchivedGames()
    }

    /// Delete all archived games.
    fun deleteAllArchivedGames() {
        archivedGames = emptyList()
        clearViewedArchivedGame()
        persistArchivedGames()
    }

    /// Start a new game setup, archiving any existing current game first.
    fun startNewGame() {
        liveState?.let { existing ->
            archivedGames = archivedGames + ArchivedGame(
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
            persistArchivedGames()
        }
        setupState = newGameSetupState(rules = archivedGames.lastOrNull()?.state?.rules ?: GameRules())
        liveState = null
        clearViewedArchivedGame()
        setupMode = SetupMode.NEW_GAME
        hasSetupDraft = true
        screen = AppScreen.SETUP
        persistCurrentGameState()
    }

    /**
     * Finish setup and enter or update the live game.
     *
     * @param now The epoch millis used when applying setup edits to an existing pre-play countdown.
     */
    fun finishSetup(now: Long = System.currentTimeMillis()) {
        liveState = if (setupMode == SetupMode.NEW_GAME) {
            createLiveGameState(setupState)
        } else {
            applySetupToLiveGame(liveState!!, setupState, now)
        }
        clearViewedArchivedGame()
        hasSetupDraft = false
        setupMode = SetupMode.EDIT_CURRENT_GAME
        screen = AppScreen.LIVE
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
        setupState = currentGame.toSetupState()
        setupMode = SetupMode.EDIT_CURRENT_GAME
        screen = AppScreen.SETUP
        persistCurrentGameState()
    }

    /// Convert the initial live preview back into a resumable setup draft.
    private fun reopenSetupDraftFromInitialPreview() {
        liveState = null
        clearViewedArchivedGame()
        setupMode = SetupMode.NEW_GAME
        hasSetupDraft = true
        screen = AppScreen.SETUP
        persistCurrentGameState()
    }

    /// Clear any archived-game summary currently being viewed.
    private fun clearViewedArchivedGame() {
        viewingArchivedGame = null
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

/**
 * Build the default setup state for a new game.
 *
 * @param now The reference local date-time for choosing the next half-hour start; injectable for tests.
 * @param rules The rules to prefill, usually defaults or the most recent game's rules.
 */
internal fun newGameSetupState(
    now: LocalDateTime = LocalDateTime.now(),
    rules: GameRules = GameRules(),
): GameSetupState {
    val startTime = nextHalfHourFrom(now.toLocalTime())
    val startDate = if (startTime.isBefore(now.toLocalTime())) {
        now.toLocalDate().plusDays(1)
    } else {
        now.toLocalDate()
    }
    return GameSetupState(
        startDate = startDate,
        startTime = startTime,
        timeZone = ZoneId.systemDefault(),
        rules = rules,
    )
}
