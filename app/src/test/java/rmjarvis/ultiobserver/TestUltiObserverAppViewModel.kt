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
    fun profileAndSettingsPersistAcrossRestart() {
        val storeDir = temporaryFolder.newFolder()
        val viewModel = UltiObserverAppViewModel(FileAppStateStore(storeDir))

        viewModel.openProfile()
        assertEquals(AppScreen.PROFILE, viewModel.screen)
        viewModel.updateProfileName("Casey Observer")
        assertEquals("Casey Observer", viewModel.profileName)
        viewModel.updateAvatarPreference(ObserverAvatarPreference.BLUE)
        assertEquals(ObserverAvatarPreference.BLUE, viewModel.avatarPreference)
        assertEquals(ObserverAvatarPreference.BLUE, viewModel.homeAvatarPreference)

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
        assertTrue(File(storeDir, "profile.json").exists())
        assertTrue(File(storeDir, "settings.json").exists())

        val restored = UltiObserverAppViewModel(FileAppStateStore(storeDir))
        assertEquals(AppScreen.HOME, restored.screen)
        assertEquals("Casey Observer", restored.profileName)
        assertEquals(ObserverAvatarPreference.BLUE, restored.avatarPreference)
        assertEquals(ObserverAvatarPreference.BLUE, restored.homeAvatarPreference)
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
    fun randomAvatarPreferenceResolvesHomeAvatarOnStartup() {
        // Use a fixed chooser to verify random-avatar timing without relying on randomness.
        val viewModel = UltiObserverAppViewModel(
            chooseAvatarIndex = { size ->
                assertEquals(concreteObserverAvatarPreferences.size, size)
                2
            },
        )

        assertEquals(ObserverAvatarPreference.RANDOM, viewModel.avatarPreference)
        assertEquals(concreteObserverAvatarPreferences[2], viewModel.homeAvatarPreference)

        viewModel.updateAvatarPreference(ObserverAvatarPreference.GREY)
        assertEquals(ObserverAvatarPreference.GREY, viewModel.avatarPreference)
        assertEquals(ObserverAvatarPreference.GREY, viewModel.homeAvatarPreference)

        viewModel.updateAvatarPreference(ObserverAvatarPreference.RANDOM)
        assertEquals(ObserverAvatarPreference.RANDOM, viewModel.avatarPreference)
        assertEquals(concreteObserverAvatarPreferences[2], viewModel.homeAvatarPreference)
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
        store.savedCurrentGameStates.clear()

        // Record an ordinary user-visible event through the same callback used by live UI actions.
        val livePointState = viewModel.liveState!!.beginLivePoint()
        viewModel.updateLiveGame(livePointState)

        assertEquals("Point is live.", store.savedCurrentGameStates.single().liveState!!.lastEvent)
        assertEquals(livePointState, store.savedCurrentGameStates.single().liveState)
        assertTrue(store.savedProfiles.isEmpty())
        assertTrue(store.savedSettings.isEmpty())
    }

    // Verify profile, settings, and current-game changes persist through their own buckets.
    @Test
    fun appDataBucketsPersistIndependently() {
        val store = RecordingAppStateStore()
        val viewModel = UltiObserverAppViewModel(store)

        viewModel.updateProfileName("Casey Observer")
        assertEquals("Casey Observer", store.savedProfiles.single().profileName)
        assertTrue(store.savedCurrentGameStates.isEmpty())
        assertTrue(store.savedSettings.isEmpty())

        viewModel.updateAvatarPreference(ObserverAvatarPreference.BLUE)
        assertEquals(ObserverAvatarPreference.BLUE, store.savedProfiles.last().avatarPreference)
        assertTrue(store.savedCurrentGameStates.isEmpty())
        assertTrue(store.savedSettings.isEmpty())

        viewModel.updateTimingAlertGlobalMode(TimingAlertGlobalMode.OFF)
        assertEquals(TimingAlertGlobalMode.OFF, store.savedSettings.single().timingAlertPreferences.globalMode)
        assertTrue(store.savedCurrentGameStates.isEmpty())
        assertEquals(2, store.savedProfiles.size)

        viewModel.startNewGame()
        assertTrue(store.savedCurrentGameStates.single().hasSetupDraft)
        assertEquals(2, store.savedProfiles.size)
        assertEquals(1, store.savedSettings.size)

        // The default no-op store should accept the same split-bucket writes without side effects.
        NoOpAppStateStore.saveCurrentGameState(PersistedCurrentGameState())
        NoOpAppStateStore.saveProfile(PersistedProfile())
        NoOpAppStateStore.saveSettings(PersistedSettings())
        NoOpAppStateStore.saveArchivedGames(emptyList())
    }

    // Verify archived games are persisted as pruned summaries separate from current-game state.
    @Test
    fun persistentStoreWritesArchivedSummariesSeparatelyFromCurrentGameSnapshot() {
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

        // Verify current game and archived summaries are written separately from profile/settings.
        assertTrue(File(storeDir, "current_game_state.json").exists())
        assertFalse(File(storeDir, "profile.json").exists())
        assertFalse(File(storeDir, "settings.json").exists())
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

        assertNull(store.loadCurrentGameState())
        assertTrue(store.loadArchivedGames().isEmpty())

        val setup = newGameSetupState(LocalDateTime.of(2026, 5, 11, 10, 0))
        val currentGameState = PersistedCurrentGameState(
            setupState = setup,
            liveState = null,
            setupMode = SetupMode.NEW_GAME,
        )
        store.saveCurrentGameState(currentGameState)
        assertEquals(currentGameState, store.loadCurrentGameState())

        val currentGameStateFile = File(storeDir, "current_game_state.json")
        currentGameStateFile.replaceText("\"versionCode\": 1", "\"versionCode\": 99")
        val recoveredState = store.loadCurrentGameState()!!
        assertNull(recoveredState.liveState)
        assertEquals(SetupMode.NEW_GAME, recoveredState.setupMode)
        assertEquals(setOf(PersistedDataArea.GAME_STATE), store.resetPersistedDataAreas)

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

    // Verify corrupted current-game fields reset narrowly while preserving readable profile/settings.
    @Test
    fun persistentStoreSalvagesReadableSplitStateFiles() {
        val storeDir = temporaryFolder.newFolder()
        val store = FileAppStateStore(storeDir)
        val setup = newGameSetupState(LocalDateTime.of(2026, 5, 11, 10, 0))
        val liveState = createLiveGameState(setup).beginLivePoint()
        val timingPreferences = TimingAlertPreferences(
            globalMode = TimingAlertGlobalMode.VIBRATION_ONLY,
            soundVolume = 0.4f,
        )
        val savedCurrentGameState = PersistedCurrentGameState(
            setupState = setup,
            liveState = liveState,
            setupMode = SetupMode.EDIT_CURRENT_GAME,
        )
        val savedArchive = ArchivedGame(
            createLiveGameState(setup).copy(phase = LivePhase.GAME_OVER),
            "Final",
        )
        store.saveCurrentGameState(savedCurrentGameState)
        store.saveProfile(PersistedProfile(profileName = "Casey Observer"))
        store.saveSettings(PersistedSettings(timingAlertPreferences = timingPreferences))

        val currentGameStateFile = File(storeDir, "current_game_state.json")
        currentGameStateFile.writeText(
            currentGameStateFile.readText().replace(
                "\"liveState\": {",
                "\"liveState\": \"broken\", \"ignoredLiveState\": {",
            )
        )

        val recoveredState = store.loadCurrentGameState()!!
        assertNull(recoveredState.liveState)
        assertEquals(SetupMode.NEW_GAME, recoveredState.setupMode)
        assertEquals(setOf(PersistedDataArea.GAME_STATE), store.resetPersistedDataAreas)

        val recoveredViewModel = UltiObserverAppViewModel(FileAppStateStore(storeDir))
        assertNull(recoveredViewModel.liveState)
        assertEquals("Casey Observer", recoveredViewModel.profileName)
        assertEquals(timingPreferences, recoveredViewModel.timingAlertPreferences)
        assertEquals(
            "Sorry, some phone data was corrupt, so UltiObserver had to revert to default values for Current Game.",
            recoveredViewModel.startupRecoveryNotice!!.message,
        )
        recoveredViewModel.dismissStartupRecoveryNotice()
        assertNull(recoveredViewModel.startupRecoveryNotice)

        // Other split files follow the same recovery path when their typed contents are corrupt.
        store.saveProfile(PersistedProfile(profileName = "Casey Observer"))
        File(storeDir, "profile.json").replaceText("\"profileName\": \"Casey Observer\"", "\"profileName\": 7")
        assertEquals(PersistedProfile(), store.loadProfile())

        store.saveSettings(PersistedSettings(timingAlertPreferences = TimingAlertPreferences(soundVolume = 0.35f)))
        File(storeDir, "settings.json").replaceText(
            "\"timingAlertPreferences\": {",
            "\"timingAlertPreferences\": \"broken\", \"ignoredTimingAlertPreferences\": {",
        )
        assertEquals(PersistedSettings(), store.loadSettings())

        store.saveArchivedGames(listOf(savedArchive))
        File(File(storeDir, "archived_games"), "00000.json").replaceText(
            "\"state\": {",
            "\"state\": \"broken\", \"ignoredState\": {",
        )
        assertTrue(store.loadArchivedGames().isEmpty())
        assertEquals(
            setOf(
                PersistedDataArea.GAME_STATE,
                PersistedDataArea.PROFILE,
                PersistedDataArea.SETTINGS,
                PersistedDataArea.PREVIOUS_GAMES,
            ),
            store.resetPersistedDataAreas,
        )

        // A single bad archive file should be skipped without losing the other readable summaries.
        val archiveStoreDir = temporaryFolder.newFolder()
        val archiveStore = FileAppStateStore(archiveStoreDir)
        val archivedOne = ArchivedGame(savedArchive.state, "First")
        val archivedTwo = archivedOne.copy(subtitle = "Second")
        archiveStore.saveArchivedGames(listOf(archivedOne, archivedTwo))
        File(File(archiveStoreDir, "archived_games"), "00000.json").writeText("{not-json")

        assertEquals(listOf(archivedTwo), archiveStore.loadArchivedGames())
        assertEquals(setOf(PersistedDataArea.PREVIOUS_GAMES), archiveStore.resetPersistedDataAreas)

        val archiveViewModel = UltiObserverAppViewModel(FileAppStateStore(archiveStoreDir))
        assertEquals(listOf(archivedTwo), archiveViewModel.archivedGames)
        assertEquals(
            "Sorry, some phone data was corrupt, so UltiObserver had to revert to default values for Previous Games.",
            archiveViewModel.startupRecoveryNotice!!.message,
        )

        val repairedArchiveStore = FileAppStateStore(archiveStoreDir)
        assertEquals(listOf(archivedTwo), repairedArchiveStore.loadArchivedGames())
        assertTrue(repairedArchiveStore.resetPersistedDataAreas.isEmpty())
        val restoredAfterRecovery = UltiObserverAppViewModel(FileAppStateStore(archiveStoreDir))
        assertEquals(listOf(archivedTwo), restoredAfterRecovery.archivedGames)
        assertNull(restoredAfterRecovery.startupRecoveryNotice)
    }

    // Verify app-version metadata is written and invalid versions reset only affected buckets.
    @Test
    fun persistentStoreHandlesMissingInvalidAndUnsupportedVersions() {
        val storeDir = temporaryFolder.newFolder()
        val store = FileAppStateStore(storeDir)
        val setup = newGameSetupState(LocalDateTime.of(2026, 5, 11, 10, 0))
        val savedCurrentGameState = PersistedCurrentGameState(
            setupState = setup,
            liveState = createLiveGameState(setup),
            setupMode = SetupMode.EDIT_CURRENT_GAME,
        )
        val savedProfile = PersistedProfile(profileName = "Casey Observer")
        val savedSettings = PersistedSettings(
            timingAlertPreferences = TimingAlertPreferences(soundVolume = 0.35f),
        )
        val savedArchive = ArchivedGame(
            createLiveGameState(setup).copy(phase = LivePhase.GAME_OVER),
            "Final",
        )

        assertEquals("0.1.0", BuildConfig.VERSION_NAME)
        assertEquals(APP_STATE_VERSION_NAME, PersistedCurrentGameState().versionName)
        assertEquals(APP_STATE_VERSION_CODE, PersistedCurrentGameState().versionCode)
        assertEquals(APP_STATE_VERSION_NAME, PersistedProfile().versionName)
        assertEquals(APP_STATE_VERSION_CODE, PersistedProfile().versionCode)
        assertEquals(APP_STATE_VERSION_NAME, PersistedSettings().versionName)
        assertEquals(APP_STATE_VERSION_CODE, PersistedSettings().versionCode)
        assertEquals(APP_STATE_VERSION_NAME, ArchivedGame(createLiveGameState(setup), "").versionName)
        assertEquals(APP_STATE_VERSION_CODE, ArchivedGame(createLiveGameState(setup), "").versionCode)

        store.saveProfile(savedProfile)
        File(storeDir, "profile.json").removeStoredAppVersion()
        assertEquals(PersistedProfile(), store.loadProfile())
        assertEquals(setOf(PersistedDataArea.PROFILE), store.resetPersistedDataAreas)

        store.saveSettings(savedSettings)
        File(storeDir, "settings.json").removeStoredAppVersion()
        assertEquals(PersistedSettings(), store.loadSettings())
        assertEquals(setOf(PersistedDataArea.PROFILE, PersistedDataArea.SETTINGS), store.resetPersistedDataAreas)

        store.saveArchivedGames(listOf(savedArchive))
        File(File(storeDir, "archived_games"), "00000.json").removeStoredAppVersion()
        assertTrue(store.loadArchivedGames().isEmpty())
        assertEquals(
            setOf(PersistedDataArea.PROFILE, PersistedDataArea.SETTINGS, PersistedDataArea.PREVIOUS_GAMES),
            store.resetPersistedDataAreas,
        )

        store.saveCurrentGameState(savedCurrentGameState)
        File(storeDir, "current_game_state.json").replaceText("\"versionName\": \"0.1.0\"", "\"versionName\": 1")
        assertEquals(PersistedCurrentGameState(), store.loadCurrentGameState())
        assertEquals(
            setOf(
                PersistedDataArea.GAME_STATE,
                PersistedDataArea.PROFILE,
                PersistedDataArea.SETTINGS,
                PersistedDataArea.PREVIOUS_GAMES,
            ),
            store.resetPersistedDataAreas,
        )

        store.saveProfile(savedProfile)
        File(storeDir, "profile.json").replaceText("\"versionCode\": 1", "\"versionCode\": \"bad\"")
        assertEquals(PersistedProfile(), store.loadProfile())
        assertEquals(
            setOf(
                PersistedDataArea.GAME_STATE,
                PersistedDataArea.SETTINGS,
                PersistedDataArea.PREVIOUS_GAMES,
                PersistedDataArea.PROFILE,
            ),
            store.resetPersistedDataAreas,
        )

        store.saveProfile(savedProfile)
        File(storeDir, "profile.json").removeStoredVersionCode()
        assertEquals(PersistedProfile(), store.loadProfile())
        assertEquals(
            setOf(
                PersistedDataArea.GAME_STATE,
                PersistedDataArea.SETTINGS,
                PersistedDataArea.PREVIOUS_GAMES,
                PersistedDataArea.PROFILE,
            ),
            store.resetPersistedDataAreas,
        )

        store.saveProfile(savedProfile)
        File(storeDir, "profile.json").replaceText("\"versionCode\": 1", "\"versionCode\": 99")
        assertEquals(PersistedProfile(), store.loadProfile())
        assertEquals(
            setOf(
                PersistedDataArea.GAME_STATE,
                PersistedDataArea.SETTINGS,
                PersistedDataArea.PREVIOUS_GAMES,
                PersistedDataArea.PROFILE,
            ),
            store.resetPersistedDataAreas,
        )

        store.saveSettings(savedSettings)
        File(storeDir, "settings.json").replaceText("\"versionCode\": 1", "\"versionCode\": \"bad\"")
        assertEquals(PersistedSettings(), store.loadSettings())
        assertEquals(
            setOf(
                PersistedDataArea.GAME_STATE,
                PersistedDataArea.PROFILE,
                PersistedDataArea.PREVIOUS_GAMES,
                PersistedDataArea.SETTINGS,
            ),
            store.resetPersistedDataAreas,
        )

        store.saveArchivedGames(listOf(savedArchive))
        File(File(storeDir, "archived_games"), "00000.json")
            .replaceText("\"versionCode\": 1", "\"versionCode\": \"bad\"")
        assertTrue(store.loadArchivedGames().isEmpty())
        assertEquals(
            setOf(
                PersistedDataArea.GAME_STATE,
                PersistedDataArea.PROFILE,
                PersistedDataArea.SETTINGS,
                PersistedDataArea.PREVIOUS_GAMES,
            ),
            store.resetPersistedDataAreas,
        )

        store.saveProfile(savedProfile)
        File(storeDir, "profile.json").replaceText("\"versionName\": \"0.1.0\"", "\"versionName\": \"0.1.0-debug\"")
        assertEquals(savedProfile.copy(versionName = "0.1.0-debug"), store.loadProfile())
        assertEquals(setOf(PersistedDataArea.GAME_STATE, PersistedDataArea.SETTINGS, PersistedDataArea.PREVIOUS_GAMES), store.resetPersistedDataAreas)

        store.saveSettings(savedSettings)
        File(storeDir, "settings.json").replaceText("\"versionCode\": 1", "\"versionCode\": 99")
        assertEquals(PersistedSettings(), store.loadSettings())
        assertEquals(
            setOf(
                PersistedDataArea.GAME_STATE,
                PersistedDataArea.PREVIOUS_GAMES,
                PersistedDataArea.SETTINGS,
            ),
            store.resetPersistedDataAreas,
        )

        store.saveArchivedGames(listOf(savedArchive))
        File(File(storeDir, "archived_games"), "00000.json")
            .replaceText("\"versionName\": \"0.1.0\"", "\"versionName\": \"0.1.0-debug\"")
        assertEquals(listOf(savedArchive.copy(versionName = "0.1.0-debug")), store.loadArchivedGames())
        assertEquals(
            setOf(PersistedDataArea.GAME_STATE, PersistedDataArea.SETTINGS),
            store.resetPersistedDataAreas,
        )

        store.saveArchivedGames(listOf(savedArchive))
        File(File(storeDir, "archived_games"), "00000.json")
            .replaceText("\"versionCode\": 1", "\"versionCode\": 99")
        assertTrue(store.loadArchivedGames().isEmpty())
        assertEquals(
            setOf(PersistedDataArea.GAME_STATE, PersistedDataArea.SETTINGS, PersistedDataArea.PREVIOUS_GAMES),
            store.resetPersistedDataAreas,
        )
    }

    // Verify malformed split-state JSON resets each affected app-data area without crashing startup.
    @Test
    fun persistentStoreReportsMalformedSplitStateAsRecoveredDefaults() {
        val storeDir = temporaryFolder.newFolder()
        File(storeDir, "current_game_state.json").writeText("{not-json")
        File(storeDir, "profile.json").writeText("{not-json")
        File(storeDir, "settings.json").writeText("{not-json")

        val viewModel = UltiObserverAppViewModel(FileAppStateStore(storeDir))

        assertEquals(AppScreen.HOME, viewModel.screen)
        assertNull(viewModel.liveState)
        assertEquals("", viewModel.profileName)
        assertEquals(TimingAlertPreferences(), viewModel.timingAlertPreferences)
        assertEquals(
            setOf(
                PersistedDataArea.GAME_STATE,
                PersistedDataArea.PROFILE,
                PersistedDataArea.SETTINGS,
            ),
            viewModel.startupRecoveryNotice!!.resetAreas,
        )
        assertEquals("Phone Data Reset", viewModel.startupRecoveryNotice!!.title)
        assertEquals(
            "Sorry, some phone data was corrupt, so UltiObserver had to revert to default values for Current Game, Profile, and Settings.",
            viewModel.startupRecoveryNotice!!.message,
        )

        val twoAreaNotice = PersistedDataRecoveryNotice(
            setOf(PersistedDataArea.PROFILE, PersistedDataArea.SETTINGS)
        )
        assertEquals(
            "Sorry, some phone data was corrupt, so UltiObserver had to revert to default values for Profile and Settings.",
            twoAreaNotice.message,
        )
        assertThrows(IllegalArgumentException::class.java) {
            PersistedDataRecoveryNotice(emptySet())
        }

        val unreadableStoreDir = temporaryFolder.newFolder()
        assertTrue(File(unreadableStoreDir, "profile.json").mkdir())
        val unreadableStore = FileAppStateStore(unreadableStoreDir)
        assertEquals(PersistedProfile(), unreadableStore.loadProfile())
        assertEquals(setOf(PersistedDataArea.PROFILE), unreadableStore.resetPersistedDataAreas)
    }

    // Verify failed current-game writes do not leave stale temporary files behind.
    @Test
    fun persistentStoreCleansTemporaryFileAfterFailedWrite() {
        val storeDir = temporaryFolder.newFolder()
        val currentGameStatePath = File(storeDir, "current_game_state.json")

        // Make the destination an undeletable non-empty directory so replacement fails.
        assertTrue(currentGameStatePath.mkdir())
        assertTrue(File(currentGameStatePath, "blocking-child").writeText("blocker").let { true })

        val store = FileAppStateStore(storeDir)
        val setup = newGameSetupState(LocalDateTime.of(2026, 5, 11, 10, 0))

        assertThrows(IOException::class.java) {
            store.saveCurrentGameState(
                PersistedCurrentGameState(
                    setupState = setup,
                    liveState = null,
                    setupMode = SetupMode.NEW_GAME,
                )
            )
        }
        assertFalse(File(storeDir, ".current_game_state.json.tmp").exists())
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
        val savedState = PersistedCurrentGameState(
            setupState = setup,
            liveState = null,
            setupMode = SetupMode.NEW_GAME,
        )

        // Force the atomic path to fail and verify the fallback path replaces each split file.
        store.saveCurrentGameState(savedState)
        store.saveProfile(PersistedProfile(profileName = "Casey Observer"))
        store.saveSettings(PersistedSettings(timingAlertPreferences = TimingAlertPreferences()))
        assertEquals(3, atomicMoveAttempts)
        assertFalse(File(storeDir, ".current_game_state.json.tmp").exists())
        assertFalse(File(storeDir, ".profile.json.tmp").exists())
        assertFalse(File(storeDir, ".settings.json.tmp").exists())

        // Load through a normal store to verify the fallback wrote valid serialized state.
        val restoredState = FileAppStateStore(storeDir).loadCurrentGameState()
        assertEquals(savedState, restoredState)
    }
}

private fun File.removeStoredAppVersion() {
    writeText(
        readText()
            .lines()
            .filterNot { line ->
                val trimmed = line.trim()
                trimmed == "\"versionName\": \"${BuildConfig.VERSION_NAME}\"," ||
                    trimmed == "\"versionCode\": ${BuildConfig.VERSION_CODE},"
            }
            .joinToString("\n")
    )
}

private fun File.removeStoredVersionCode() {
    writeText(
        readText()
            .lines()
            .filterNot { line ->
                line.trim() == "\"versionCode\": ${BuildConfig.VERSION_CODE},"
            }
            .joinToString("\n")
    )
}

private fun File.replaceText(oldValue: String, newValue: String) {
    writeText(readText().replace(oldValue, newValue))
}

private class RecordingAppStateStore : AppStateStore {
    val savedCurrentGameStates = mutableListOf<PersistedCurrentGameState>()
    val savedProfiles = mutableListOf<PersistedProfile>()
    val savedSettings = mutableListOf<PersistedSettings>()
    val savedArchivedGames = mutableListOf<List<ArchivedGame>>()

    override val resetPersistedDataAreas: Set<PersistedDataArea> = emptySet()

    override fun loadCurrentGameState(): PersistedCurrentGameState? = null

    override fun saveCurrentGameState(state: PersistedCurrentGameState) {
        savedCurrentGameStates += state
    }

    override fun loadProfile(): PersistedProfile? = null

    override fun saveProfile(state: PersistedProfile) {
        savedProfiles += state
    }

    override fun loadSettings(): PersistedSettings? = null

    override fun saveSettings(state: PersistedSettings) {
        savedSettings += state
    }

    override fun loadArchivedGames(): List<ArchivedGame> = emptyList()

    override fun saveArchivedGames(games: List<ArchivedGame>) {
        savedArchivedGames += games
    }
}
