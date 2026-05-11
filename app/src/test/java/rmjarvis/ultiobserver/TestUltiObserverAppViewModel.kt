package rmjarvis.ultiobserver

import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TestUltiObserverAppViewModel {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun appStateHolderOwnsTopLevelGameFlow() {
        val viewModel = UltiObserverAppViewModel()
        assertEquals(AppScreen.HOME, viewModel.screen)
        assertNull(viewModel.liveState)
        assertTrue(viewModel.archivedGames.isEmpty())

        viewModel.startNewGame()
        assertEquals(AppScreen.SETUP, viewModel.screen)

        val namedSetup = viewModel.setupState.copy(
            teamOne = TeamSetup("Alpha", TeamColorChoice.BLUE),
            teamTwo = TeamSetup("Beta", TeamColorChoice.PINK),
        )
        viewModel.updateSetup(namedSetup)
        viewModel.finishSetup()

        val startedGame = viewModel.liveState
        assertNotNull(startedGame)
        assertEquals(AppScreen.LIVE, viewModel.screen)
        assertEquals("Alpha", startedGame!!.teamOne.name)
        assertEquals("Beta", startedGame.teamTwo.name)

        val adjustedGame = startedGame.adjustScore(teamOneScore = 2, teamTwoScore = 1)
        viewModel.updateLiveGame(adjustedGame)
        assertEquals(2, viewModel.liveState!!.teamOne.score)

        viewModel.editCurrentGame(viewModel.liveState!!)
        assertEquals(AppScreen.SETUP, viewModel.screen)
        assertEquals(SetupMode.EDIT_CURRENT_GAME, viewModel.setupMode)
        viewModel.updateSetup(
            viewModel.setupState.copy(
                teamOne = viewModel.setupState.teamOne.copy(name = "Alpha Prime"),
            )
        )
        viewModel.finishSetup()
        assertEquals(AppScreen.LIVE, viewModel.screen)
        assertEquals("Alpha Prime", viewModel.liveState!!.teamOne.name)
        assertEquals(2, viewModel.liveState!!.teamOne.score)

        viewModel.goHome()
        assertEquals(AppScreen.HOME, viewModel.screen)
        viewModel.startNewGame()
        assertEquals(AppScreen.SETUP, viewModel.screen)
        assertEquals(1, viewModel.archivedGames.size)
        assertEquals("Closed when new game started", viewModel.archivedGames.single().subtitle)
        assertEquals(LivePhase.GAME_OVER, viewModel.archivedGames.single().state.phase)
        assertNull(viewModel.liveState)
    }

    @Test
    fun archivedGamesOpenReadOnlyAndIgnoreLiveUpdates() {
        val viewModel = UltiObserverAppViewModel()
        viewModel.startNewGame()
        viewModel.finishSetup()

        val finishedGame = viewModel.liveState!!.copy(phase = LivePhase.GAME_OVER)
        viewModel.updateLiveGame(finishedGame)
        viewModel.goHome()
        viewModel.archiveCompletedGame()

        assertNull(viewModel.liveState)
        assertEquals(1, viewModel.archivedGames.size)
        val archivedGame = viewModel.archivedGames.single().state
        assertEquals(LivePhase.GAME_OVER, archivedGame.phase)

        viewModel.openPreviousGame(0)
        assertEquals(AppScreen.LIVE, viewModel.screen)
        assertTrue(viewModel.viewingReadOnlySummary)
        assertEquals(archivedGame, viewModel.currentLiveState)

        val changedArchivedGame = archivedGame.copy(teamOne = archivedGame.teamOne.copy(score = 99))
        viewModel.updateLiveGame(changedArchivedGame)
        assertNull(viewModel.liveState)
        assertEquals(archivedGame, viewModel.currentLiveState)

        viewModel.editCurrentGame(archivedGame)
        assertEquals(AppScreen.LIVE, viewModel.screen)
        assertEquals(SetupMode.NEW_GAME, viewModel.setupMode)
        assertEquals(archivedGame, viewModel.currentLiveState)
    }

    @Test
    fun completedGameCanReopenFromHomeAndThenArchive() {
        val viewModel = UltiObserverAppViewModel()
        viewModel.startNewGame()
        viewModel.finishSetup()

        val completedGame = viewModel.liveState!!.copy(phase = LivePhase.GAME_OVER)
        viewModel.updateLiveGame(completedGame)
        viewModel.goHome()

        viewModel.openCompletedGame()
        assertEquals(AppScreen.LIVE, viewModel.screen)
        assertEquals(completedGame, viewModel.currentLiveState)
        assertFalse(viewModel.viewingReadOnlySummary)

        viewModel.goHome()
        viewModel.archiveCompletedGame()
        assertNull(viewModel.liveState)
        assertEquals(1, viewModel.archivedGames.size)
        assertEquals("", viewModel.archivedGames.single().subtitle)

        viewModel.openPreviousGame(0)
        assertEquals(AppScreen.LIVE, viewModel.screen)
        assertTrue(viewModel.viewingReadOnlySummary)
        assertEquals(viewModel.archivedGames.single().state, viewModel.currentLiveState)
    }

    @Test
    fun currentGameResumeAndSetupUpdatePreserveLiveState() {
        val viewModel = UltiObserverAppViewModel()
        viewModel.startNewGame()
        viewModel.finishSetup()
        val scoredGame = viewModel.liveState!!.adjustScore(teamOneScore = 3, teamTwoScore = 2)
        viewModel.updateLiveGame(scoredGame)

        viewModel.goHome()
        assertEquals(AppScreen.HOME, viewModel.screen)
        assertEquals(scoredGame, viewModel.liveState)

        viewModel.resumeCurrentGame()
        assertEquals(AppScreen.LIVE, viewModel.screen)
        assertEquals(scoredGame, viewModel.currentLiveState)

        viewModel.editCurrentGame(viewModel.currentLiveState!!)
        assertEquals(AppScreen.SETUP, viewModel.screen)
        assertEquals(SetupMode.EDIT_CURRENT_GAME, viewModel.setupMode)
        viewModel.updateSetup(
            viewModel.setupState.copy(
                rules = viewModel.setupState.rules.copy(gameTo = 17),
            )
        )
        viewModel.finishSetup()

        assertEquals(AppScreen.LIVE, viewModel.screen)
        assertEquals(17, viewModel.liveState!!.rules.gameTo)
        assertEquals(3, viewModel.liveState!!.teamOne.score)
        assertEquals(2, viewModel.liveState!!.teamTwo.score)
    }

    @Test
    fun unavailableHomeActionsLeaveStateAlone() {
        val viewModel = UltiObserverAppViewModel()

        viewModel.resumeCurrentGame()
        assertEquals(AppScreen.HOME, viewModel.screen)
        assertNull(viewModel.liveState)

        viewModel.openCompletedGame()
        assertEquals(AppScreen.HOME, viewModel.screen)
        assertNull(viewModel.liveState)

        viewModel.openPreviousGame(0)
        assertEquals(AppScreen.HOME, viewModel.screen)
        assertNull(viewModel.currentLiveState)

        viewModel.archiveCompletedGame()
        assertTrue(viewModel.archivedGames.isEmpty())

        viewModel.startNewGame()
        viewModel.finishSetup()
        val activeGame = viewModel.liveState!!
        viewModel.goHome()

        viewModel.openCompletedGame()
        assertEquals(AppScreen.HOME, viewModel.screen)
        assertEquals(activeGame, viewModel.liveState)

        viewModel.archiveCompletedGame()
        assertTrue(viewModel.archivedGames.isEmpty())
        assertEquals(activeGame, viewModel.liveState)

        val completedGame = activeGame.copy(phase = LivePhase.GAME_OVER)
        viewModel.updateLiveGame(completedGame)
        viewModel.resumeCurrentGame()
        assertEquals(AppScreen.HOME, viewModel.screen)
        assertEquals(completedGame, viewModel.liveState)
    }

    @Test
    fun startingNewGameArchivesCompletedGameWithoutClosingItAgain() {
        val viewModel = UltiObserverAppViewModel()
        viewModel.startNewGame()
        viewModel.finishSetup()

        val beforeUndoAction = viewModel.liveState!!
        val completedGame = beforeUndoAction.copy(
            phase = LivePhase.GAME_OVER,
            countdown = CountdownState(
                kind = CountdownKind.BETWEEN_POINTS,
                label = "Pull in",
                durationSeconds = 80,
                targetEpoch = 80_000L,
                betweenPointsTarget = BetweenPointsCountdownTarget.PULL,
            ),
            undoEntry = UndoEntry("Undo End Game", beforeUndoAction),
        )
        viewModel.updateLiveGame(completedGame)
        viewModel.startNewGame()

        assertEquals(AppScreen.SETUP, viewModel.screen)
        assertNull(viewModel.liveState)
        assertEquals(1, viewModel.archivedGames.size)
        assertEquals("", viewModel.archivedGames.single().subtitle)
        assertEquals(LivePhase.GAME_OVER, viewModel.archivedGames.single().state.phase)
        assertNull(viewModel.archivedGames.single().state.countdown)
        assertNull(viewModel.archivedGames.single().state.undoEntry)
        assertNull(viewModel.archivedGames.single().state.redoEntry)
    }

    @Test
    fun newGameSetupDefaultsUseNextHalfHourAndAdvanceDateAcrossMidnight() {
        val sameDaySetup = newGameSetupState(LocalDateTime.of(2026, 1, 1, 23, 0))
        assertEquals(LocalDate.of(2026, 1, 1), sameDaySetup.startDate)
        assertEquals(LocalTime.of(23, 0), sameDaySetup.startTime)

        val nextDaySetup = newGameSetupState(LocalDateTime.of(2026, 1, 1, 23, 45))
        assertEquals(LocalDate.of(2026, 1, 2), nextDaySetup.startDate)
        assertEquals(LocalTime.MIDNIGHT, nextDaySetup.startTime)
    }

    // Verify persisted app state restores both setup drafts and active-game undo history.
    @Test
    fun persistentStoreRestoresSetupDraftAndActiveGameUndoHistory() {
        val storeDir = temporaryFolder.newFolder()
        val store = FileAppStateStore(storeDir)
        val viewModel = UltiObserverAppViewModel(store)

        // Start a new-game setup draft and verify a fresh ViewModel keeps the draft but opens at Home.
        viewModel.startNewGame()
        val draftedSetup = viewModel.setupState.copy(
            teamOne = TeamSetup("Viscous Coupling", TeamColorChoice.BLUE),
            teamTwo = TeamSetup("Animal", TeamColorChoice.PINK),
        )
        viewModel.updateSetup(draftedSetup)

        val draftRestored = UltiObserverAppViewModel(FileAppStateStore(storeDir))
        assertEquals(AppScreen.HOME, draftRestored.screen)
        assertEquals(draftedSetup, draftRestored.setupState)
        assertNull(draftRestored.liveState)

        // Finish setup, record an undo-backed game action, and verify it survives restart.
        draftRestored.finishSetup()
        val livePointState = draftRestored.liveState!!.beginLivePoint()
        val scoredState = livePointState.recordGoal(
            scoringTeam = TeamId.TEAM_ONE,
            now = livePointState.startEpoch + 5 * 60_000L,
        )
        draftRestored.updateLiveGame(scoredState)

        val gameRestored = UltiObserverAppViewModel(FileAppStateStore(storeDir))
        assertEquals(AppScreen.HOME, gameRestored.screen)
        assertEquals(scoredState, gameRestored.liveState)
        assertNotNull(gameRestored.liveState!!.undoEntry)
        gameRestored.resumeCurrentGame()
        assertEquals(AppScreen.LIVE, gameRestored.screen)
        val undoRestoredState = gameRestored.liveState!!.undoLastAction()
        assertEquals(livePointState, undoRestoredState.copy(redoEntry = null))
        assertNotNull(undoRestoredState.redoEntry)

        gameRestored.updateLiveGame(undoRestoredState)
        val redoRestored = UltiObserverAppViewModel(FileAppStateStore(storeDir))
        assertEquals(undoRestoredState, redoRestored.liveState)
        assertEquals(scoredState, redoRestored.liveState!!.redoLastAction())
    }

    // Verify live event updates persist at the ViewModel boundary.
    @Test
    fun liveGameEventsPersistThroughUpdateLiveGame() {
        val store = RecordingAppStateStore()
        val viewModel = UltiObserverAppViewModel(store)

        // Start a live game and clear the setup saves so the event assertion is focused.
        viewModel.startNewGame()
        viewModel.finishSetup()
        store.savedActiveStates.clear()

        // Record an ordinary user-visible event through the same callback used by live UI actions.
        val livePointState = viewModel.liveState!!.beginLivePoint()
        viewModel.updateLiveGame(livePointState)

        assertEquals("Point is live.", store.savedActiveStates.single().liveState!!.lastEvent)
        assertEquals(livePointState, store.savedActiveStates.single().liveState)
    }

    // Verify archived games are persisted as pruned summaries separate from active state.
    @Test
    fun persistentStoreWritesArchivedSummariesSeparatelyFromActiveSnapshot() {
        val storeDir = temporaryFolder.newFolder()
        val viewModel = UltiObserverAppViewModel(FileAppStateStore(storeDir))

        // Complete and archive a game that still has live-only countdown and undo state.
        viewModel.startNewGame()
        viewModel.finishSetup()
        val beforeEndGame = viewModel.liveState!!
        val completedGame = beforeEndGame.copy(
            phase = LivePhase.GAME_OVER,
            countdown = CountdownState(
                kind = CountdownKind.BETWEEN_POINTS,
                label = "Pull in",
                durationSeconds = 80,
                targetEpoch = 80_000L,
                betweenPointsTarget = BetweenPointsCountdownTarget.PULL,
            ),
            undoEntry = UndoEntry("Undo End Game", beforeEndGame),
        )
        viewModel.updateLiveGame(completedGame)
        viewModel.goHome()
        viewModel.archiveCompletedGame()

        // Verify active state and archived summaries are written to separate files.
        assertTrue(File(storeDir, "active_app_state.json").exists())
        assertTrue(File(File(storeDir, "archived_games"), "00000.json").exists())

        // Restore from disk and verify the archived game keeps only summary-relevant state.
        val restored = UltiObserverAppViewModel(FileAppStateStore(storeDir))
        assertEquals(AppScreen.HOME, restored.screen)
        assertNull(restored.liveState)
        assertEquals(1, restored.archivedGames.size)
        assertNull(restored.archivedGames.single().state.countdown)
        assertNull(restored.archivedGames.single().state.undoEntry)
        assertNull(restored.archivedGames.single().state.redoEntry)
        assertEquals(LivePhase.GAME_OVER, restored.archivedGames.single().state.phase)
    }

    // Verify failed active-state writes do not leave stale temporary files behind.
    @Test
    fun persistentStoreCleansTemporaryFileAfterFailedWrite() {
        val storeDir = temporaryFolder.newFolder()
        val activeStatePath = File(storeDir, "active_app_state.json")

        // Make the destination an undeletable non-empty directory so replacement fails.
        assertTrue(activeStatePath.mkdir())
        assertTrue(File(activeStatePath, "blocking-child").writeText("blocker").let { true })

        val store = FileAppStateStore(storeDir)
        val setup = newGameSetupState(LocalDateTime.of(2026, 5, 11, 10, 0))

        assertThrows(IOException::class.java) {
            store.saveActiveState(
                PersistedActiveAppState(
                    screen = AppScreen.SETUP,
                    setupState = setup,
                    liveState = null,
                    setupMode = SetupMode.NEW_GAME,
                    viewingArchivedGameIndex = null,
                )
            )
        }
        assertFalse(File(storeDir, ".active_app_state.json.tmp").exists())
    }

    // Verify the non-atomic replace fallback still writes readable state.
    @Test
    fun persistentStoreFallsBackWhenAtomicMoveIsUnavailable() {
        val storeDir = temporaryFolder.newFolder()
        var atomicMoveAttempts = 0
        var replaceMoveAttempts = 0
        val store = FileAppStateStore(
            rootDir = storeDir,
            moveFileAtomically = { source, target ->
                atomicMoveAttempts += 1
                throw AtomicMoveNotSupportedException(source.path, target.path, "forced fallback")
            },
            replaceFile = { source, target ->
                replaceMoveAttempts += 1
                Files.move(source.toPath(), target.toPath(), REPLACE_EXISTING)
            },
        )
        val setup = newGameSetupState(LocalDateTime.of(2026, 5, 11, 10, 0))
        val savedState = PersistedActiveAppState(
            screen = AppScreen.SETUP,
            setupState = setup,
            liveState = null,
            setupMode = SetupMode.NEW_GAME,
            viewingArchivedGameIndex = null,
        )

        // Force the atomic path to fail and verify the fallback path replaces the file.
        store.saveActiveState(savedState)
        assertEquals(1, atomicMoveAttempts)
        assertEquals(1, replaceMoveAttempts)
        assertFalse(File(storeDir, ".active_app_state.json.tmp").exists())

        // Load through a normal store to verify the fallback wrote valid serialized state.
        val restoredState = FileAppStateStore(storeDir).loadActiveState()
        assertEquals(savedState, restoredState)
    }
}

private class RecordingAppStateStore : AppStateStore {
    val savedActiveStates = mutableListOf<PersistedActiveAppState>()
    val savedArchivedGames = mutableListOf<List<ArchivedGame>>()

    override fun loadActiveState(): PersistedActiveAppState? = null

    override fun saveActiveState(state: PersistedActiveAppState) {
        savedActiveStates += state
    }

    override fun loadArchivedGames(): List<ArchivedGame> = emptyList()

    override fun saveArchivedGames(games: List<ArchivedGame>) {
        savedArchivedGames += games
    }
}
