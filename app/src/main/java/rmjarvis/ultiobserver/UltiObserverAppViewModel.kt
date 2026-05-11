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
    private val restoredSetupDraft = persistedActiveState?.hasSetupDraft ?: (
        persistedActiveState?.screen == AppScreen.SETUP &&
            persistedActiveState.liveState == null &&
            persistedActiveState.setupMode == SetupMode.NEW_GAME
        )
    private var viewingArchivedGameIndex: Int? = null

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
    var archivedGames by mutableStateOf(appStateStore.loadArchivedGames())
        private set
    var viewingArchivedGame by mutableStateOf(viewingArchivedGameIndex?.let { archivedGames.getOrNull(it) })
        private set
    var hasSetupDraft by mutableStateOf(restoredSetupDraft)
        private set

    val currentLiveState: LiveGameState?
        get() = viewingArchivedGame?.state ?: liveState

    val viewingReadOnlySummary: Boolean
        get() = viewingArchivedGame != null

    val currentGameHomeSubtitle: String?
        get() = when {
            liveState?.isInitialLivePreview() == true -> "Tap to resume setup."
            liveState == null && hasSetupDraft -> "Tap to resume setup."
            liveState?.phase != null && liveState?.phase != LivePhase.GAME_OVER -> "Tap to resume the active game."
            else -> null
        }

    fun goHome() {
        screen = AppScreen.HOME
        clearViewedArchivedGame()
        persistActiveState()
    }

    fun goBackFromCurrentScreen() {
        val previewState = liveState
        if (screen == AppScreen.LIVE && previewState?.isInitialLivePreview() == true && viewingArchivedGame == null) {
            reopenSetupDraftFromInitialPreview()
            return
        }

        goHome()
    }

    fun updateSetup(updatedSetup: GameSetupState) {
        setupState = updatedSetup
        if (setupMode == SetupMode.NEW_GAME && liveState == null) {
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
        viewingArchivedGameIndex = index
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
        viewingArchivedGameIndex = null
        viewingArchivedGame = null
    }

    private fun persistActiveState() {
        appStateStore.saveActiveState(
            PersistedActiveAppState(
                screen = screen,
                setupState = setupState,
                liveState = liveState,
                setupMode = setupMode,
                viewingArchivedGameIndex = viewingArchivedGameIndex,
                profileName = profileName,
                hasSetupDraft = hasSetupDraft,
            )
        )
    }

    private fun persistArchivedGames() {
        appStateStore.saveArchivedGames(archivedGames)
    }
}

// Archived/completed games keep summary data but drop live countdown and undo/redo state.
private fun LiveGameState.pruneUndoHistory(): LiveGameState {
    return copy(
        countdown = null,
        undoEntry = null,
        redoEntry = null,
    )
}

private fun LiveGameState.isInitialLivePreview(): Boolean {
    return phase == LivePhase.BETWEEN_POINTS &&
        teamOne.score == 0 &&
        teamTwo.score == 0 &&
        undoEntry == null &&
        !halftimeTaken
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
