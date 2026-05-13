package rmjarvis.ultiobserver

import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
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
        assertNull(viewModel.currentGameHomeSubtitle)

        viewModel.startNewGame()
        assertEquals(AppScreen.SETUP, viewModel.screen)
        assertTrue(viewModel.hasSetupDraft)
        assertEquals("Tap to resume", viewModel.currentGameHomeSubtitle)

        val namedSetup = viewModel.setupState.copy(
            teamOne = TeamSetup("Alpha", TeamColorChoice.BLUE),
            teamTwo = TeamSetup("Beta", TeamColorChoice.PINK),
        )
        viewModel.updateSetup(namedSetup)
        viewModel.finishSetup()
        assertFalse(viewModel.hasSetupDraft)

        val startedGame = viewModel.liveState
        assertNotNull(startedGame)
        assertEquals(AppScreen.LIVE, viewModel.screen)
        assertEquals("Tap to resume", viewModel.currentGameHomeSubtitle)
        assertEquals("Alpha", startedGame!!.teamOne.name)
        assertEquals("Beta", startedGame.teamTwo.name)

        viewModel.updateLiveGame(startedGame.beginLivePoint())
        assertEquals("Tap to resume", viewModel.currentGameHomeSubtitle)

        val adjustedGame = viewModel.liveState!!.adjustScore(teamOneScore = 2, teamTwoScore = 1)
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
        assertTrue(viewModel.hasSetupDraft)
        assertEquals(1, viewModel.archivedGames.size)
        assertEquals("Closed when new game started", viewModel.archivedGames.single().subtitle)
        assertEquals(LivePhase.GAME_OVER, viewModel.archivedGames.single().state.phase)
        assertNull(viewModel.liveState)
        assertEquals("Tap to resume", viewModel.currentGameHomeSubtitle)
    }

    @Test
    fun setupDraftCanResumeFromHomeBeforeFirstPull() {
        val viewModel = UltiObserverAppViewModel()
        viewModel.startNewGame()
        val draftedSetup = viewModel.setupState.copy(
            teamOne = TeamSetup("", TeamColorChoice.GREEN),
            teamTwo = TeamSetup("", TeamColorChoice.YELLOW),
        )
        viewModel.updateSetup(draftedSetup)
        viewModel.goHome()

        assertEquals(AppScreen.HOME, viewModel.screen)
        assertTrue(viewModel.hasSetupDraft)
        assertNull(viewModel.liveState)

        viewModel.resumeCurrentGame()
        assertEquals(AppScreen.SETUP, viewModel.screen)
        assertEquals(draftedSetup, viewModel.setupState)

        viewModel.finishSetup()
        assertEquals(AppScreen.LIVE, viewModel.screen)
        assertFalse(viewModel.hasSetupDraft)

        viewModel.goBackFromCurrentScreen()
        assertEquals(AppScreen.SETUP, viewModel.screen)
        assertTrue(viewModel.hasSetupDraft)
        assertNull(viewModel.liveState)
        assertEquals("", viewModel.setupState.teamOne.name)
        assertEquals("", viewModel.setupState.teamTwo.name)

        viewModel.finishSetup()
        val livePreview = viewModel.liveState!!
        viewModel.goHome()
        viewModel.resumeCurrentGame()
        assertEquals(AppScreen.SETUP, viewModel.screen)
        assertTrue(viewModel.hasSetupDraft)
        assertNull(viewModel.liveState)
        assertEquals("", viewModel.setupState.teamOne.name)
        assertEquals("", viewModel.setupState.teamTwo.name)

        viewModel.finishSetup()
        viewModel.editCurrentGame(viewModel.liveState!!)
        assertEquals(AppScreen.SETUP, viewModel.screen)
        assertTrue(viewModel.hasSetupDraft)
        assertNull(viewModel.liveState)
        assertEquals("", viewModel.setupState.teamOne.name)
        assertEquals("", viewModel.setupState.teamTwo.name)

        viewModel.finishSetup()
        viewModel.updateLiveGame(viewModel.liveState!!.beginLivePoint())
        viewModel.goHome()
        viewModel.resumeCurrentGame()
        assertEquals(AppScreen.LIVE, viewModel.screen)
        assertEquals(LivePhase.LIVE_POINT, viewModel.liveState!!.phase)
        assertFalse(viewModel.hasSetupDraft)
        assertEquals(livePreview.teamOne.name, viewModel.liveState!!.teamOne.name)
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
        assertEquals(SetupMode.EDIT_CURRENT_GAME, viewModel.setupMode)
        assertEquals(archivedGame, viewModel.currentLiveState)

        viewModel.goHome()
        viewModel.startNewGame()
        viewModel.finishSetup()
        val currentPreview = viewModel.liveState!!
        assertTrue(currentPreview.isInitialLivePreview())
        viewModel.openPreviousGame(0)
        viewModel.goBackFromCurrentScreen()
        assertEquals(AppScreen.HOME, viewModel.screen)
        assertEquals(currentPreview, viewModel.liveState)
    }

    @Test
    fun completedGameCanReopenFromHomeAndThenArchive() {
        val viewModel = UltiObserverAppViewModel()
        viewModel.startNewGame()
        viewModel.finishSetup()

        val completedGame = viewModel.liveState!!.copy(phase = LivePhase.GAME_OVER)
        viewModel.updateLiveGame(completedGame)
        assertNull(viewModel.currentGameHomeSubtitle)
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
    fun currentAndArchivedGamesCanBeDeleted() {
        val viewModel = UltiObserverAppViewModel()
        viewModel.startNewGame()
        viewModel.finishSetup()
        val currentGame = viewModel.liveState!!

        viewModel.deleteCurrentGame()
        assertEquals(AppScreen.HOME, viewModel.screen)
        assertNull(viewModel.liveState)
        assertNull(viewModel.currentLiveState)

        viewModel.updateLiveGame(currentGame.copy(phase = LivePhase.GAME_OVER))
        viewModel.archiveCompletedGame()
        assertEquals(1, viewModel.archivedGames.size)

        viewModel.openPreviousGame(0)
        assertTrue(viewModel.viewingReadOnlySummary)
        viewModel.deleteArchivedGame(0)
        assertTrue(viewModel.archivedGames.isEmpty())
        assertNull(viewModel.currentLiveState)
        viewModel.deleteArchivedGame(0)
        assertTrue(viewModel.archivedGames.isEmpty())

        viewModel.updateLiveGame(currentGame.copy(phase = LivePhase.GAME_OVER))
        viewModel.archiveCompletedGame()
        viewModel.updateLiveGame(
            currentGame.copy(
                phase = LivePhase.GAME_OVER,
                teamOne = currentGame.teamOne.copy(name = "Second archived game"),
            ),
        )
        viewModel.archiveCompletedGame()
        assertEquals(2, viewModel.archivedGames.size)

        viewModel.openPreviousGame(1)
        assertTrue(viewModel.viewingReadOnlySummary)
        viewModel.deleteAllArchivedGames()
        assertTrue(viewModel.archivedGames.isEmpty())
        assertNull(viewModel.currentLiveState)
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

        viewModel.openProfile()
        viewModel.goBackFromCurrentScreen()
        assertEquals(AppScreen.HOME, viewModel.screen)

        viewModel.updateLiveGame(activeGame.beginLivePoint())
        viewModel.resumeSetupDraft()
        assertEquals(AppScreen.HOME, viewModel.screen)
        assertEquals(LivePhase.LIVE_POINT, viewModel.liveState!!.phase)

        viewModel.resumeCurrentGame()
        assertEquals(AppScreen.LIVE, viewModel.screen)
        viewModel.goBackFromCurrentScreen()
        assertEquals(AppScreen.HOME, viewModel.screen)
    }

    @Test
    fun homeDestinationScreensAndProfileNameAreAppState() {
        val storeDir = temporaryFolder.newFolder()
        val viewModel = UltiObserverAppViewModel(FileAppStateStore(storeDir))

        viewModel.openProfile()
        assertEquals(AppScreen.PROFILE, viewModel.screen)
        viewModel.updateProfileName("Casey Observer")
        assertEquals("Casey Observer", viewModel.profileName)

        viewModel.openSettings()
        assertEquals(AppScreen.SETTINGS, viewModel.screen)
        viewModel.updateTimingAlertGlobalMode(TimingAlertGlobalMode.VIBRATION_ONLY)
        viewModel.updateTimingAlertSoundVolume(0.4f)
        viewModel.updateTimingAlertVibrationDuration(420L)
        viewModel.updateTimingAlertVibrateWithSounds(true)
        viewModel.updateTimingCueMode(TimingCueId.PULLING_TIME_VIOLATION, TimingAlertMode.DING)
        assertEquals(
            TimingAlertMode.VIBRATE,
            viewModel.timingAlertPreferences.alertModeFor(TimingCueId.PULLING_TIME_VIOLATION),
        )
        viewModel.updateTimingCueMode(TimingCueId.TIMEOUT_OFFENSE_TEN, TimingAlertMode.VIBRATE)
        viewModel.openTimingCueSettings()
        assertEquals(AppScreen.TIMING_CUE_SETTINGS, viewModel.screen)
        viewModel.goBackFromCurrentScreen()
        assertEquals(AppScreen.SETTINGS, viewModel.screen)
        viewModel.updateTimingAlertGlobalMode(TimingAlertGlobalMode.OFF)
        assertEquals(
            TimingAlertMode.NONE,
            viewModel.timingAlertPreferences.alertModeFor(TimingCueId.PULLING_TIME_VIOLATION),
        )
        assertEquals(
            TimingAlertMode.NONE,
            viewModel.timingAlertPreferences.alertModeFor(TimingCueId.TIMEOUT_OFFENSE_TEN),
        )
        viewModel.openPreviousGames()
        assertEquals(AppScreen.PREVIOUS_GAMES, viewModel.screen)

        val restored = UltiObserverAppViewModel(FileAppStateStore(storeDir))
        assertEquals(AppScreen.HOME, restored.screen)
        assertEquals("Casey Observer", restored.profileName)
        assertEquals(TimingAlertGlobalMode.OFF, restored.timingAlertPreferences.globalMode)
        assertEquals(0.4f, restored.timingAlertPreferences.soundVolume, 0f)
        assertEquals(420L, restored.timingAlertPreferences.vibrationDurationMillis)
        assertTrue(restored.timingAlertPreferences.vibrateWithSounds)
        assertEquals(
            TimingAlertMode.DING,
            restored.timingAlertPreferences.cueModes[TimingCueId.PULLING_TIME_VIOLATION],
        )
        assertEquals(
            TimingAlertMode.VIBRATE,
            restored.timingAlertPreferences.cueModes[TimingCueId.TIMEOUT_OFFENSE_TEN],
        )
        assertEquals(
            TimingAlertMode.NONE,
            restored.timingAlertPreferences.alertModeFor(TimingCueId.PULLING_TIME_VIOLATION),
        )
        assertEquals(
            TimingAlertMode.NONE,
            restored.timingAlertPreferences.alertModeFor(TimingCueId.TIMEOUT_OFFENSE_TEN),
        )
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
        assertTrue(draftRestored.hasSetupDraft)
        assertNull(draftRestored.liveState)
        draftRestored.resumeCurrentGame()
        assertEquals(AppScreen.SETUP, draftRestored.screen)

        // Finish setup, record an undo-backed game action, and verify it survives restart.
        draftRestored.finishSetup()
        assertFalse(draftRestored.hasSetupDraft)
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

    // Verify persistence handles ordinary empty/corrupt filesystem shapes without inventing app state.
    @Test
    fun persistentStoreLoadsOnlyCurrentStateAndJsonArchives() {
        val storeDir = temporaryFolder.newFolder()
        val store = FileAppStateStore(storeDir)

        assertNull(store.loadActiveState())
        assertTrue(store.loadArchivedGames().isEmpty())

        val setup = newGameSetupState(LocalDateTime.of(2026, 5, 11, 10, 0))
        val activeState = PersistedActiveAppState(
            screen = AppScreen.SETUP,
            setupState = setup,
            liveState = null,
            setupMode = SetupMode.NEW_GAME,
        )
        store.saveActiveState(activeState)
        assertEquals(activeState, store.loadActiveState())

        val activeStateFile = File(storeDir, "active_app_state.json")
        activeStateFile.writeText(activeStateFile.readText().replace("\"version\": 1", "\"version\": 99"))
        val versionException = assertThrows(IllegalArgumentException::class.java) {
            store.loadActiveState()
        }
        assertEquals("Unsupported active app state version 99.", versionException.message)

        val archivedOne = ArchivedGame(
            createLiveGameState(setup).copy(phase = LivePhase.GAME_OVER),
            "First",
        )
        val archivedTwo = archivedOne.copy(subtitle = "Second")
        store.saveArchivedGames(listOf(archivedOne, archivedTwo))
        val archiveDir = File(storeDir, "archived_games")
        File(archiveDir, "not-json.txt").writeText("ignored")
        assertTrue(File(archiveDir, "directory.json").mkdir())
        assertEquals(listOf(archivedOne, archivedTwo), store.loadArchivedGames())

        store.saveArchivedGames(listOf(archivedOne))
        assertEquals(listOf(archivedOne), store.loadArchivedGames())
        assertFalse(File(archiveDir, "00001.json").exists())

        assertTrue(archiveDir.deleteRecursively())
        File(storeDir, "archived_games").writeText("not a directory")
        assertTrue(store.loadArchivedGames().isEmpty())
        store.saveArchivedGames(emptyList())
        assertTrue(File(storeDir, "archived_games").isFile)
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
        val store = FileAppStateStore(
            rootDir = storeDir,
            moveFileAtomically = { source, target ->
                atomicMoveAttempts += 1
                throw AtomicMoveNotSupportedException(source.path, target.path, "forced fallback")
            },
        )
        val setup = newGameSetupState(LocalDateTime.of(2026, 5, 11, 10, 0))
        val savedState = PersistedActiveAppState(
            screen = AppScreen.SETUP,
            setupState = setup,
            liveState = null,
            setupMode = SetupMode.NEW_GAME,
        )

        // Force the atomic path to fail and verify the fallback path replaces the file.
        store.saveActiveState(savedState)
        assertEquals(1, atomicMoveAttempts)
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
