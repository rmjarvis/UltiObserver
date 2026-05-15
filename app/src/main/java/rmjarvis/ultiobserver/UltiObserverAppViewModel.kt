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
    PREVIOUS_GAMES,
    SETUP,
    LIVE,
}

@Serializable
internal enum class SetupMode {
    NEW_GAME,
    EDIT_CURRENT_GAME,
}

internal class UltiObserverAppViewModel(
    private val appStateStore: AppStateStore = NoOpAppStateStore,
    // Injected so tests can make random avatar selection deterministic.
    private val chooseAvatarIndex: (Int) -> Int = { size -> Random.nextInt(size) },
) : ViewModel() {
    private val persistedCurrentGameState = appStateStore.loadCurrentGameState()
    private val persistedProfile = appStateStore.loadProfile()
    private val persistedSettings = appStateStore.loadSettings()
    private val restoredSetupDraft = persistedCurrentGameState?.hasSetupDraft ?: false
    private val restoredArchivedGames = appStateStore.loadArchivedGames()

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
    var archivedGames by mutableStateOf(restoredArchivedGames)
        private set
    var viewingArchivedGame by mutableStateOf<ArchivedGame?>(null)
        private set
    var hasSetupDraft by mutableStateOf(restoredSetupDraft)
        private set
    var startupRecoveryNotice by mutableStateOf(
        appStateStore.resetPersistedDataAreas.takeIf { it.isNotEmpty() }?.let { resetAreas ->
            PersistedDataRecoveryNotice(resetAreas)
        }
    )
        private set

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

    fun goHome() {
        screen = AppScreen.HOME
        clearViewedArchivedGame()
    }

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

    fun updateSetup(updatedSetup: GameSetupState) {
        setupState = updatedSetup
        if (setupMode == SetupMode.NEW_GAME) {
            hasSetupDraft = true
        }
        persistCurrentGameState()
    }

    fun updateLiveGame(updatedGame: LiveGameState) {
        if (viewingArchivedGame == null) {
            // All live game event logging flows through this ViewModel boundary.
            liveState = updatedGame
            persistCurrentGameState()
        }
    }

    fun updateProfileName(updatedName: String) {
        profileName = updatedName
        persistProfileState()
    }

    fun updateAvatarPreference(updatedPreference: ObserverAvatarPreference) {
        avatarPreference = updatedPreference
        homeAvatarPreference = resolveHomeAvatarPreference(updatedPreference)
        persistProfileState()
    }

    fun updateTimingAlertGlobalMode(mode: TimingAlertGlobalMode) {
        timingAlertPreferences = timingAlertPreferences.copy(globalMode = mode)
        persistSettingsState()
    }

    fun updateTimingAlertSoundVolume(volume: Float) {
        timingAlertPreferences = timingAlertPreferences.copy(soundVolume = volume)
        persistSettingsState()
    }

    fun updateTimingAlertVibrationDuration(durationMillis: Long) {
        timingAlertPreferences = timingAlertPreferences.copy(vibrationDurationMillis = durationMillis)
        persistSettingsState()
    }

    fun updateTimingAlertVibrateWithSounds(vibrateWithSounds: Boolean) {
        timingAlertPreferences = timingAlertPreferences.copy(vibrateWithSounds = vibrateWithSounds)
        persistSettingsState()
    }

    fun updateTimingCueMode(cueId: TimingCueId, mode: TimingAlertMode) {
        timingAlertPreferences = timingAlertPreferences.copy(
            cueModes = timingAlertPreferences.cueModes + (cueId to mode),
        )
        persistSettingsState()
    }

    fun dismissStartupRecoveryNotice() {
        startupRecoveryNotice = null
    }

    fun openProfile() {
        clearViewedArchivedGame()
        screen = AppScreen.PROFILE
    }

    fun openAbout() {
        clearViewedArchivedGame()
        screen = AppScreen.ABOUT
    }

    fun openSettings() {
        clearViewedArchivedGame()
        screen = AppScreen.SETTINGS
    }

    fun openTimingCueSettings() {
        clearViewedArchivedGame()
        screen = AppScreen.TIMING_CUE_SETTINGS
    }

    fun openPreviousGames() {
        clearViewedArchivedGame()
        screen = AppScreen.PREVIOUS_GAMES
    }

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

    fun resumeSetupDraft() {
        if (liveState == null && hasSetupDraft) {
            clearViewedArchivedGame()
            setupMode = SetupMode.NEW_GAME
            screen = AppScreen.SETUP
            persistCurrentGameState()
        }
    }

    fun openCompletedGame() {
        val current = liveState ?: return
        if (current.phase == LivePhase.GAME_OVER) {
            clearViewedArchivedGame()
            screen = AppScreen.LIVE
        }
    }

    fun openPreviousGame(index: Int) {
        val archived = archivedGames.getOrNull(index) ?: return
        viewingArchivedGame = archived
        screen = AppScreen.LIVE
    }

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

    fun deleteCurrentGame() {
        liveState = null
        clearViewedArchivedGame()
        setupMode = SetupMode.NEW_GAME
        hasSetupDraft = false
        screen = AppScreen.HOME
        persistCurrentGameState()
    }

    fun deleteArchivedGame(index: Int) {
        if (archivedGames.getOrNull(index) == null) {
            return
        }
        archivedGames = archivedGames.toMutableList().also { it.removeAt(index) }
        clearViewedArchivedGame()
        persistArchivedGames()
    }

    fun deleteAllArchivedGames() {
        archivedGames = emptyList()
        clearViewedArchivedGame()
        persistArchivedGames()
    }

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
        setupState = newGameSetupState()
        liveState = null
        clearViewedArchivedGame()
        setupMode = SetupMode.NEW_GAME
        hasSetupDraft = true
        screen = AppScreen.SETUP
        persistCurrentGameState()
    }

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

    private fun reopenSetupDraftFromInitialPreview() {
        liveState = null
        clearViewedArchivedGame()
        setupMode = SetupMode.NEW_GAME
        hasSetupDraft = true
        screen = AppScreen.SETUP
        persistCurrentGameState()
    }

    private fun clearViewedArchivedGame() {
        viewingArchivedGame = null
    }

    private fun persistCurrentGameState() {
        appStateStore.saveCurrentGameState(
            PersistedCurrentGameState(
                setupState = setupState,
                liveState = liveState,
                setupMode = setupMode,
                hasSetupDraft = hasSetupDraft,
            )
        )
    }

    private fun persistProfileState() {
        appStateStore.saveProfile(
            PersistedProfile(
                profileName = profileName,
                avatarPreference = avatarPreference,
            )
        )
    }

    private fun persistSettingsState() {
        appStateStore.saveSettings(
            PersistedSettings(timingAlertPreferences = timingAlertPreferences)
        )
    }

    private fun persistArchivedGames() {
        appStateStore.saveArchivedGames(archivedGames)
    }

    private fun resolveHomeAvatarPreference(preference: ObserverAvatarPreference): ObserverAvatarPreference {
        if (preference != ObserverAvatarPreference.RANDOM) {
            return preference
        }
        return concreteObserverAvatarPreferences[chooseAvatarIndex(concreteObserverAvatarPreferences.size)]
    }
}

internal fun newGameSetupState(now: LocalDateTime = LocalDateTime.now()): GameSetupState {
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
    )
}
