package rmjarvis.ultiobserver

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import java.time.LocalDateTime
import java.time.ZoneId
import kotlinx.serialization.Serializable

@Serializable
internal enum class AppScreen {
    HOME,
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

@Serializable
internal data class ArchivedGame(
    val state: LiveGameState,
    val subtitle: String,
)

internal class UltiObserverAppViewModel(
    private val appStateStore: AppStateStore = NoOpAppStateStore,
) : ViewModel() {
    private val persistedActiveState = appStateStore.loadActiveState()
    private val restoredSetupDraft = persistedActiveState?.hasSetupDraft ?: false

    var screen by mutableStateOf(AppScreen.HOME)
        private set
    var setupState by mutableStateOf(persistedActiveState?.setupState ?: newGameSetupState())
        private set
    var liveState by mutableStateOf(persistedActiveState?.liveState)
        private set
    var setupMode by mutableStateOf(persistedActiveState?.setupMode ?: SetupMode.NEW_GAME)
        private set
    var profileName by mutableStateOf(persistedActiveState?.profileName ?: "")
        private set
    var timingAlertPreferences by mutableStateOf(
        persistedActiveState?.timingAlertPreferences ?: TimingAlertPreferences()
    )
        private set
    var archivedGames by mutableStateOf(appStateStore.loadArchivedGames())
        private set
    var viewingArchivedGame by mutableStateOf<ArchivedGame?>(null)
        private set
    var hasSetupDraft by mutableStateOf(restoredSetupDraft)
        private set

    val currentLiveState: LiveGameState?
        get() = viewingArchivedGame?.state ?: liveState

    val viewingReadOnlySummary: Boolean
        get() = viewingArchivedGame != null

    val currentGameHomeSubtitle: String?
        get() {
            val current = liveState
            return when {
                current?.isInitialLivePreview() == true -> "Tap to resume setup."
                current == null && hasSetupDraft -> "Tap to resume setup."
                current != null && current.phase != LivePhase.GAME_OVER -> "Tap to resume the active game."
                else -> null
            }
        }

    fun goHome() {
        screen = AppScreen.HOME
        clearViewedArchivedGame()
        persistActiveState()
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
        persistActiveState()
    }

    fun updateLiveGame(updatedGame: LiveGameState) {
        if (viewingArchivedGame == null) {
            // All live game event logging flows through this ViewModel boundary.
            liveState = updatedGame
            persistActiveState()
        }
    }

    fun updateProfileName(updatedName: String) {
        profileName = updatedName
        persistActiveState()
    }

    fun updateTimingAlertGlobalMode(mode: TimingAlertGlobalMode) {
        timingAlertPreferences = timingAlertPreferences.copy(globalMode = mode)
        persistActiveState()
    }

    fun updateTimingAlertSoundVolume(volume: Float) {
        timingAlertPreferences = timingAlertPreferences.copy(soundVolume = volume)
        persistActiveState()
    }

    fun updateTimingAlertVibrationDuration(durationMillis: Long) {
        timingAlertPreferences = timingAlertPreferences.copy(vibrationDurationMillis = durationMillis)
        persistActiveState()
    }

    fun updateTimingAlertVibrateWithSounds(vibrateWithSounds: Boolean) {
        timingAlertPreferences = timingAlertPreferences.copy(vibrateWithSounds = vibrateWithSounds)
        persistActiveState()
    }

    fun updateTimingCueMode(cueId: TimingCueId, mode: TimingAlertMode) {
        timingAlertPreferences = timingAlertPreferences.copy(
            cueModes = timingAlertPreferences.cueModes + (cueId to mode),
        )
        persistActiveState()
    }

    fun openProfile() {
        clearViewedArchivedGame()
        screen = AppScreen.PROFILE
        persistActiveState()
    }

    fun openSettings() {
        clearViewedArchivedGame()
        screen = AppScreen.SETTINGS
        persistActiveState()
    }

    fun openTimingCueSettings() {
        clearViewedArchivedGame()
        screen = AppScreen.TIMING_CUE_SETTINGS
        persistActiveState()
    }

    fun openPreviousGames() {
        clearViewedArchivedGame()
        screen = AppScreen.PREVIOUS_GAMES
        persistActiveState()
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
            persistActiveState()
        }
    }

    fun resumeSetupDraft() {
        if (liveState == null && hasSetupDraft) {
            clearViewedArchivedGame()
            setupMode = SetupMode.NEW_GAME
            screen = AppScreen.SETUP
            persistActiveState()
        }
    }

    fun openCompletedGame() {
        val current = liveState ?: return
        if (current.phase == LivePhase.GAME_OVER) {
            clearViewedArchivedGame()
            screen = AppScreen.LIVE
            persistActiveState()
        }
    }

    fun openPreviousGame(index: Int) {
        val archived = archivedGames.getOrNull(index) ?: return
        viewingArchivedGame = archived
        screen = AppScreen.LIVE
        persistActiveState()
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
        persistActiveState()
    }

    fun deleteCurrentGame() {
        liveState = null
        clearViewedArchivedGame()
        setupMode = SetupMode.NEW_GAME
        hasSetupDraft = false
        screen = AppScreen.HOME
        persistActiveState()
    }

    fun deleteArchivedGame(index: Int) {
        if (archivedGames.getOrNull(index) == null) {
            return
        }
        archivedGames = archivedGames.toMutableList().also { it.removeAt(index) }
        clearViewedArchivedGame()
        persistArchivedGames()
        persistActiveState()
    }

    fun deleteAllArchivedGames() {
        archivedGames = emptyList()
        clearViewedArchivedGame()
        persistArchivedGames()
        persistActiveState()
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
        persistActiveState()
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
        persistActiveState()
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
        persistActiveState()
    }

    private fun reopenSetupDraftFromInitialPreview() {
        liveState = null
        clearViewedArchivedGame()
        setupMode = SetupMode.NEW_GAME
        hasSetupDraft = true
        screen = AppScreen.SETUP
        persistActiveState()
    }

    private fun clearViewedArchivedGame() {
        viewingArchivedGame = null
    }

    private fun persistActiveState() {
        appStateStore.saveActiveState(
            PersistedActiveAppState(
                screen = screen,
                setupState = setupState,
                liveState = liveState,
                setupMode = setupMode,
                profileName = profileName,
                hasSetupDraft = hasSetupDraft,
                timingAlertPreferences = timingAlertPreferences,
            )
        )
    }

    private fun persistArchivedGames() {
        appStateStore.saveArchivedGames(archivedGames)
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
