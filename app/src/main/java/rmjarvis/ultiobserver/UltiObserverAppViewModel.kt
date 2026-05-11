package rmjarvis.ultiobserver

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId

internal enum class AppScreen {
    HOME,
    SETUP,
    LIVE,
}

internal enum class SetupMode {
    NEW_GAME,
    EDIT_CURRENT_GAME,
}

internal data class ArchivedGame(
    val state: LiveGameState,
    val subtitle: String,
)

internal class UltiObserverAppViewModel : ViewModel() {
    var screen by mutableStateOf(AppScreen.HOME)
        private set
    var setupState by mutableStateOf(newGameSetupState())
        private set
    var liveState by mutableStateOf<LiveGameState?>(null)
        private set
    var setupMode by mutableStateOf(SetupMode.NEW_GAME)
        private set
    var archivedGames by mutableStateOf(listOf<ArchivedGame>())
        private set
    var viewingArchivedGame by mutableStateOf<ArchivedGame?>(null)
        private set

    val currentLiveState: LiveGameState?
        get() = viewingArchivedGame?.state ?: liveState

    val viewingReadOnlySummary: Boolean
        get() = viewingArchivedGame != null

    fun goHome() {
        screen = AppScreen.HOME
    }

    fun updateSetup(updatedSetup: GameSetupState) {
        setupState = updatedSetup
    }

    fun updateLiveGame(updatedGame: LiveGameState) {
        if (viewingArchivedGame == null) {
            liveState = updatedGame
        }
    }

    fun resumeCurrentGame() {
        val current = liveState ?: return
        if (current.phase != LivePhase.GAME_OVER) {
            viewingArchivedGame = null
            screen = AppScreen.LIVE
        }
    }

    fun openCompletedGame() {
        val current = liveState ?: return
        if (current.phase == LivePhase.GAME_OVER) {
            viewingArchivedGame = null
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
            pruneUndoHistory(completed),
            "",
        )
        liveState = null
        viewingArchivedGame = null
    }

    fun startNewGame() {
        liveState?.let { existing ->
            archivedGames = archivedGames + ArchivedGame(
                pruneUndoHistory(
                    if (existing.phase == LivePhase.GAME_OVER) {
                        existing
                    } else {
                        existing.copy(
                            phase = LivePhase.GAME_OVER,
                            endTime = LocalTime.now(existing.timeZone),
                        )
                    }
                ),
                if (existing.phase == LivePhase.GAME_OVER) "" else "Closed when new game started",
            )
        }
        setupState = newGameSetupState()
        liveState = null
        viewingArchivedGame = null
        setupMode = SetupMode.NEW_GAME
        screen = AppScreen.SETUP
    }

    fun finishSetup(now: Long = System.currentTimeMillis()) {
        liveState = if (setupMode == SetupMode.NEW_GAME) {
            createLiveGameState(setupState)
        } else {
            applySetupToLiveGame(liveState!!, setupState, now)
        }
        viewingArchivedGame = null
        screen = AppScreen.LIVE
    }

    fun editCurrentGame(currentGame: LiveGameState) {
        if (viewingArchivedGame != null) {
            return
        }
        setupState = liveGameToSetupState(currentGame)
        setupMode = SetupMode.EDIT_CURRENT_GAME
        screen = AppScreen.SETUP
    }
}

// Archived/completed games keep summary data but drop live countdown/undo state.
private fun pruneUndoHistory(state: LiveGameState): LiveGameState {
    return state.copy(
        countdown = null,
        undoEntry = null,
    )
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
