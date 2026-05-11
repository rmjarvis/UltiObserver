package rmjarvis.ultiobserver

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.Composable
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import rmjarvis.ultiobserver.ui.theme.UltiObserverTheme

class MainActivity : ComponentActivity() {
    private val appViewModel: UltiObserverAppViewModel by viewModels {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                require(modelClass == UltiObserverAppViewModel::class.java) {
                    "Unknown ViewModel class ${modelClass.name}."
                }
                return modelClass.cast(UltiObserverAppViewModel(FileAppStateStore(filesDir)))
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            UltiObserverTheme(dynamicColor = false) {
                UltiObserverApp(appViewModel)
            }
        }
    }
}

// Switch between home, setup, and live screens from the app ViewModel state.
@Composable
internal fun UltiObserverApp(viewModel: UltiObserverAppViewModel) {
    // Back should always return to the home screen rather than walking back through setup/live.
    BackHandler(enabled = viewModel.screen != AppScreen.HOME) {
        viewModel.goHome()
    }

    // Route to the current top-level screen.
    when (viewModel.screen) {
        AppScreen.HOME -> {
            HomeScreen(
                currentGame = viewModel.liveState?.takeIf { it.phase != LivePhase.GAME_OVER }?.let {
                    it.gameListEntry("Current game")
                },
                completedGamePendingArchive = viewModel.liveState?.takeIf { it.phase == LivePhase.GAME_OVER }?.let {
                    it.gameListEntry("")
                },
                previousGames = viewModel.archivedGames.map { it.state.gameListEntry(it.subtitle) },
                onResumeCurrentGame = viewModel::resumeCurrentGame,
                onOpenCompletedGame = viewModel::openCompletedGame,
                onOpenPreviousGame = viewModel::openPreviousGame,
                onArchiveCompletedGame = viewModel::archiveCompletedGame,
                onStartNewGame = viewModel::startNewGame,
            )
        }

        AppScreen.SETUP -> {
            SetupScreen(
                state = viewModel.setupState,
                onStateChange = viewModel::updateSetup,
                primaryButtonLabel = if (viewModel.setupMode == SetupMode.NEW_GAME) "Start Game" else "Back to Game Screen",
                onPrimaryAction = { viewModel.finishSetup() },
            )
        }

        AppScreen.LIVE -> {
            // Archived games reuse the live-game screen, but in a read-only summary mode.
            val currentLiveState = viewModel.currentLiveState
            if (currentLiveState != null) {
                LiveGameScreen(
                    state = currentLiveState,
                    readOnlySummary = viewModel.viewingReadOnlySummary,
                    onStateChange = viewModel::updateLiveGame,
                    onUpdateGameSetup = {
                        viewModel.editCurrentGame(currentLiveState)
                    },
                )
            }
        }
    }
}
