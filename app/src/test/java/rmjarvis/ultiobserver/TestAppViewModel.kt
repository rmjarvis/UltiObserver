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
import org.junit.Assert.assertSame
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/// Tests for app-level navigation, persistence coordination, and lifecycle state owned by AppViewModel.
class TestAppViewModel : GameDomainTestFixtures() {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    /// Verify the ViewModel owns the top-level flow from home, to setup, to live game, and starting over.
    @Test
    fun appStateHolderOwnsTopLevelGameFlow() {
        // Start from a clean Home state with no current or archived game.
        val viewModel = AppViewModel(NoOpAppStateStorage)
        assertEquals(AppScreen.HOME, viewModel.screen)
        assertEquals(AppScreen.HOME, viewModel.state.value.screen)
        assertNull(viewModel.liveState)
        assertTrue(viewModel.archivedGames.isEmpty())
        assertNull(viewModel.currentGameHomeSubtitle)

        // Create a setup draft and verify Home can advertise it as resumable.
        viewModel.startNewGame()
        assertEquals(AppScreen.SETUP, viewModel.screen)
        assertTrue(viewModel.hasSetupDraft)
        assertEquals("Tap to resume", viewModel.currentGameHomeSubtitle)

        // Finish setup with named teams and verify the live game is created from that draft.
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

        // Reopen setup from the live game and verify setup edits preserve live score state.
        viewModel.editCurrentGame(viewModel.liveState!!)
        assertEquals(AppScreen.SETUP, viewModel.screen)
        assertEquals(SetupMode.EDIT_CURRENT_GAME, viewModel.setupMode)
        viewModel.updateSetup(
            viewModel.setupState.copy(
                teamOne = viewModel.setupState.teamOne.copy(name = "Alpha Prime"),
            )
        )
        assertFalse(viewModel.hasSetupDraft)
        viewModel.finishSetup()
        assertEquals(AppScreen.LIVE, viewModel.screen)
        assertEquals("Alpha Prime", viewModel.liveState!!.teamOne.name)
        assertEquals(2, viewModel.liveState!!.teamOne.score)

        // Starting over should archive the old current game and create a fresh setup draft.
        viewModel.goHome()
        assertEquals(AppScreen.HOME, viewModel.screen)
        viewModel.startNewGame()
        assertEquals(AppScreen.SETUP, viewModel.screen)
        assertTrue(viewModel.hasSetupDraft)
        assertEquals(1, viewModel.archivedGames.size)
        assertEquals("Closed when new game started", viewModel.archivedGames.single().subtitle)
        assertEquals(GamePhase.GAME_OVER, viewModel.archivedGames.single().state.phase)
        assertNull(viewModel.liveState)
        assertEquals("Tap to resume", viewModel.currentGameHomeSubtitle)
    }

    /// Verify setup drafts resume through Home until a real live point has started.
    @Test
    fun setupDraftCanResumeFromHomeBeforeFirstPull() {
        // Create a blank-name setup draft and verify Home resumes it as setup, not live play.
        val viewModel = AppViewModel(NoOpAppStateStorage)
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

        // Backing out from the pre-pull live preview should restore the original editable draft.
        viewModel.finishSetup()
        assertEquals(AppScreen.LIVE, viewModel.screen)
        assertFalse(viewModel.hasSetupDraft)

        viewModel.goBackFromCurrentScreen()
        assertEquals(AppScreen.SETUP, viewModel.screen)
        assertTrue(viewModel.hasSetupDraft)
        assertNull(viewModel.liveState)
        assertEquals("", viewModel.setupState.teamOne.name)
        assertEquals("", viewModel.setupState.teamTwo.name)

        // Home resume and Update Game Setup should both still treat the pre-pull game as a draft.
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

        // Once a real point starts, Home should resume the active live game instead of setup.
        viewModel.finishSetup()
        viewModel.updateLiveGame(viewModel.liveState!!.beginLivePoint())
        viewModel.goHome()
        viewModel.resumeCurrentGame()
        assertEquals(AppScreen.LIVE, viewModel.screen)
        assertEquals(GamePhase.LIVE_POINT, viewModel.liveState!!.phase)
        assertFalse(viewModel.hasSetupDraft)
        assertEquals(livePreview.teamOne.name, viewModel.liveState!!.teamOne.name)

        // Starting over before the first real point should discard setup-only state, not archive it.
        val prePullViewModel = AppViewModel(NoOpAppStateStorage)
        prePullViewModel.startNewGame()
        prePullViewModel.finishSetup()
        prePullViewModel.goHome()
        prePullViewModel.startNewGame()
        assertEquals(AppScreen.SETUP, prePullViewModel.screen)
        assertTrue(prePullViewModel.hasSetupDraft)
        assertNull(prePullViewModel.liveState)
        assertTrue(prePullViewModel.archivedGames.isEmpty())
    }

    /// Verify archived games open as read-only summaries and ignore live-game mutation callbacks.
    @Test
    fun archivedGamesOpenReadOnlyAndIgnoreLiveUpdates() {
        val viewModel = AppViewModel(NoOpAppStateStorage)
        viewModel.startNewGame()
        viewModel.finishSetup()

        val finishedGame = viewModel.liveState!!.copy(phase = GamePhase.GAME_OVER)
        viewModel.updateLiveGame(finishedGame)
        viewModel.goHome()
        viewModel.archiveCompletedGame()

        assertNull(viewModel.liveState)
        assertEquals(1, viewModel.archivedGames.size)
        val archivedGame = viewModel.archivedGames.single().state
        assertEquals(GamePhase.GAME_OVER, archivedGame.phase)

        viewModel.openArchivedGame(0)
        assertEquals(AppScreen.LIVE, viewModel.screen)
        assertTrue(viewModel.viewingReadOnlySummary)
        assertEquals(archivedGame, viewModel.currentLiveState)

        val changedArchivedGame = archivedGame.copy(teamOne = archivedGame.teamOne.copy(score = 99))
        viewModel.updateLiveGame(changedArchivedGame)
        assertNull(viewModel.liveState)
        assertEquals(archivedGame, viewModel.currentLiveState)

        viewModel.goBackFromCurrentScreen()
        assertEquals(AppScreen.ARCHIVED_GAMES, viewModel.screen)
        assertFalse(viewModel.viewingReadOnlySummary)
        assertNull(viewModel.currentLiveState)

        viewModel.openArchivedGame(0)
        viewModel.editCurrentGame(archivedGame)
        assertEquals(AppScreen.LIVE, viewModel.screen)
        assertEquals(SetupMode.EDIT_CURRENT_GAME, viewModel.setupMode)
        assertEquals(archivedGame, viewModel.currentLiveState)

        viewModel.goHome()
        viewModel.startNewGame()
        viewModel.finishSetup()
        val currentPreview = viewModel.liveState!!
        assertTrue(currentPreview.isInitialLivePreview())
        viewModel.openArchivedGame(0)
        viewModel.restoreViewingArchivedGame()
        assertEquals(AppScreen.LIVE, viewModel.screen)
        assertFalse(viewModel.viewingReadOnlySummary)
        assertEquals(archivedGame, viewModel.liveState)
        assertEquals(1, viewModel.archivedGames.size)
        assertEquals(currentPreview.teamOne.name, viewModel.archivedGames.single().state.teamOne.name)
    }

    /// Verify a completed current game can be reopened from Home, then archived into Archived Games.
    @Test
    fun completedGameCanReopenFromHomeAndThenArchive() {
        val viewModel = AppViewModel(NoOpAppStateStorage)
        viewModel.startNewGame()
        viewModel.finishSetup()

        val completedGame = viewModel.liveState!!.copy(phase = GamePhase.GAME_OVER)
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

        viewModel.openArchivedGame(0)
        assertEquals(AppScreen.LIVE, viewModel.screen)
        assertTrue(viewModel.viewingReadOnlySummary)
        assertEquals(viewModel.archivedGames.single().state, viewModel.currentLiveState)
    }

    /// Verify a restored completed archive can undo the end-game action without restoring older undo history.
    @Test
    fun completedArchiveRestoreKeepsOnlyEndGameUndo() {
        val viewModel = AppViewModel(NoOpAppStateStorage)
        viewModel.startNewGame()
        viewModel.finishSetup()

        val initialLiveState = viewModel.liveState!!
        val beforeEndGame = initialLiveState.copy(
            undoEntry = UndoEntry("Undo Start Point", initialLiveState),
        )
        val completedGame = beforeEndGame.copy(
            phase = GamePhase.GAME_OVER,
            undoEntry = UndoEntry("Undo End Game", beforeEndGame),
            redoEntry = beforeEndGame,
        )
        viewModel.updateLiveGame(completedGame)
        viewModel.archiveCompletedGame()

        val archivedState = viewModel.archivedGames.single().state
        val prunedBeforeEndGame = beforeEndGame.pruneUndoHistory()
        assertEquals("Undo End Game", archivedState.undoEntry?.label)
        assertEquals(prunedBeforeEndGame, archivedState.undoEntry!!.previous)
        assertNull(archivedState.redoEntry)

        viewModel.openArchivedGame(0)
        viewModel.restoreViewingArchivedGame()
        val restoredGame = viewModel.liveState!!
        val restoredUndo = restoredGame.undoLastAction()

        assertEquals(GamePhase.GAME_OVER, restoredGame.phase)
        assertEquals("Undo End Game", restoredGame.undoEntry?.label)
        assertEquals(prunedBeforeEndGame, restoredUndo.copy(redoEntry = null))
        assertNotNull(restoredUndo.redoEntry)
    }

    /// Verify archived-game restore paths handle missing selections and restoration with no current game.
    @Test
    fun archivedGameRestoreHandlesMissingSelectionAndEmptyCurrentGame() {
        val viewModel = AppViewModel(NoOpAppStateStorage)

        viewModel.restoreViewingArchivedGame()
        viewModel.restoreArchivedGame(0)

        assertEquals(AppScreen.HOME, viewModel.screen)
        assertNull(viewModel.liveState)
        assertTrue(viewModel.archivedGames.isEmpty())

        viewModel.startNewGame()
        viewModel.finishSetup()
        viewModel.updateLiveGame(
            viewModel.liveState!!.copy(
                phase = GamePhase.GAME_OVER,
                teamOne = TeamLiveState("First Archive", TeamColorChoice.WHITE),
            )
        )
        viewModel.archiveCompletedGame()

        viewModel.startNewGame()
        viewModel.finishSetup()
        viewModel.updateLiveGame(
            viewModel.liveState!!.copy(
                phase = GamePhase.GAME_OVER,
                teamOne = TeamLiveState("Second Archive", TeamColorChoice.WHITE),
            )
        )
        viewModel.archiveCompletedGame()

        viewModel.restoreArchivedGame(99)
        assertNull(viewModel.liveState)
        assertEquals(2, viewModel.archivedGames.size)

        viewModel.openArchivedGame(1)
        viewModel.restoreViewingArchivedGame()

        assertEquals(AppScreen.LIVE, viewModel.screen)
        assertFalse(viewModel.viewingReadOnlySummary)
        assertEquals("Second Archive", viewModel.liveState!!.teamOne.name)
        assertEquals(1, viewModel.archivedGames.size)
        assertEquals("First Archive", viewModel.archivedGames.single().state.teamOne.name)
    }

    /// Verify deleting current, single archived, and all archived games clears the right ViewModel state.
    @Test
    fun currentAndArchivedGamesCanBeDeleted() {
        val viewModel = AppViewModel(NoOpAppStateStorage)
        viewModel.startNewGame()
        viewModel.finishSetup()
        val currentGame = viewModel.liveState!!

        viewModel.deleteCurrentGame()
        assertEquals(AppScreen.HOME, viewModel.screen)
        assertNull(viewModel.liveState)
        assertNull(viewModel.currentLiveState)

        viewModel.updateLiveGame(currentGame.copy(phase = GamePhase.GAME_OVER))
        viewModel.archiveCompletedGame()
        assertEquals(1, viewModel.archivedGames.size)

        viewModel.openArchivedGame(0)
        assertTrue(viewModel.viewingReadOnlySummary)
        viewModel.deleteArchivedGame(0)
        assertTrue(viewModel.archivedGames.isEmpty())
        assertNull(viewModel.currentLiveState)
        viewModel.deleteArchivedGame(0)
        assertTrue(viewModel.archivedGames.isEmpty())

        viewModel.updateLiveGame(currentGame.copy(phase = GamePhase.GAME_OVER))
        viewModel.archiveCompletedGame()
        viewModel.updateLiveGame(
            currentGame.copy(
                phase = GamePhase.GAME_OVER,
                teamOne = currentGame.teamOne.copy(name = "Second archived game"),
            ),
        )
        viewModel.archiveCompletedGame()
        assertEquals(2, viewModel.archivedGames.size)

        viewModel.openArchivedGame(1)
        assertTrue(viewModel.viewingReadOnlySummary)
        viewModel.deleteAllArchivedGames()
        assertTrue(viewModel.archivedGames.isEmpty())
        assertNull(viewModel.currentLiveState)
    }

    /// Verify resuming and editing the current game's setup preserves existing live score state.
    @Test
    fun currentGameResumeAndSetupUpdatePreserveLiveState() {
        val viewModel = AppViewModel(NoOpAppStateStorage)
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

    /// Verify unavailable navigation and game actions leave the ViewModel on its existing state.
    @Test
    fun unavailableHomeActionsLeaveStateAlone() {
        // Empty-home actions should be harmless when there is no current or completed game.
        val viewModel = AppViewModel(NoOpAppStateStorage)

        viewModel.resumeCurrentGame()
        assertEquals(AppScreen.HOME, viewModel.screen)
        assertNull(viewModel.liveState)

        viewModel.openCompletedGame()
        assertEquals(AppScreen.HOME, viewModel.screen)
        assertNull(viewModel.liveState)

        viewModel.openArchivedGame(0)
        assertEquals(AppScreen.HOME, viewModel.screen)
        assertNull(viewModel.currentLiveState)

        viewModel.archiveCompletedGame()
        assertTrue(viewModel.archivedGames.isEmpty())

        // Active-game-only state should reject completed-game and archive actions.
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

        // A completed current game stays on Home until opened through the completed-game path.
        val completedGame = activeGame.copy(phase = GamePhase.GAME_OVER)
        viewModel.updateLiveGame(completedGame)
        viewModel.resumeCurrentGame()
        assertEquals(AppScreen.HOME, viewModel.screen)
        assertEquals(completedGame, viewModel.liveState)

        // Non-game screens should return Home, and stale draft actions should not disturb live play.
        viewModel.openProfile()
        viewModel.goBackFromCurrentScreen()
        assertEquals(AppScreen.HOME, viewModel.screen)

        viewModel.updateLiveGame(activeGame.beginLivePoint())
        viewModel.resumeSetupDraft()
        assertEquals(AppScreen.HOME, viewModel.screen)
        assertEquals(GamePhase.LIVE_POINT, viewModel.liveState!!.phase)

        viewModel.resumeCurrentGame()
        assertEquals(AppScreen.LIVE, viewModel.screen)
        viewModel.goBackFromCurrentScreen()
        assertEquals(AppScreen.HOME, viewModel.screen)
    }

    /// Verify profile and timing-alert settings are written to storage and restored after restart.
    @Test
    fun profileAndSettingsPersistAcrossRestart() {
        // Write profile values through the same ViewModel actions the UI uses.
        val storeDir = temporaryFolder.newFolder()
        val viewModel = AppViewModel(FileAppStateStorage(storeDir))

        viewModel.openProfile()
        assertEquals(AppScreen.PROFILE, viewModel.screen)
        viewModel.updateProfileName("Casey Observer")
        assertEquals("Casey Observer", viewModel.profileName)
        viewModel.updateAvatarPreference(ObserverAvatarPreference.BLUE)
        assertEquals(ObserverAvatarPreference.BLUE, viewModel.avatarPreference)
        assertEquals(ObserverAvatarPreference.BLUE, viewModel.homeAvatarPreference)

        // Exercise global timing settings and cue overrides before leaving Settings.
        viewModel.openSettings()
        assertEquals(AppScreen.SETTINGS, viewModel.screen)
        viewModel.updateTimingAlertGlobalMode(TimingAlertGlobalMode.VIBRATION_ONLY)
        viewModel.updateTimingAlertSoundVolume(0.4f)
        viewModel.updateTimingAlertVibrationDuration(420L)
        viewModel.updateTimingAlertVibrateWithSounds(true)
        viewModel.updateAutomaticallyAdvanceCountdowns(false)
        viewModel.updateAutomaticallyLockLivePoint(false)
        viewModel.updateTimingCueMode(TimingCueId.PULLING_TIME_VIOLATION, TimingAlertMode.DING)
        viewModel.updateTimingCueRepeatCount(TimingCueId.PULLING_TIME_VIOLATION, 3)
        assertEquals(
            TimingAlertMode.VIBRATE,
            viewModel.timingAlertPreferences.alertModeFor(TimingCueId.PULLING_TIME_VIOLATION),
        )
        assertEquals(
            3,
            viewModel.timingAlertPreferences.repeatCountFor(TimingCueId.PULLING_TIME_VIOLATION),
        )
        assertThrows(IllegalArgumentException::class.java) {
            viewModel.updateTimingCueRepeatCount(TimingCueId.PULLING_TIME_VIOLATION, 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            viewModel.updateTimingCueRepeatCount(
                TimingCueId.PULLING_TIME_VIOLATION,
                MAX_TIMING_ALERT_REPEAT_COUNT + 1,
            )
        }
        viewModel.updateTimingCueMode(TimingCueId.PULLING_TIME_VIOLATION, TimingAlertMode.NONE)
        assertEquals(
            1,
            viewModel.timingAlertPreferences.repeatCountFor(TimingCueId.PULLING_TIME_VIOLATION),
        )
        viewModel.updateTimingCueMode(TimingCueId.PULLING_TIME_VIOLATION, TimingAlertMode.DING)
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
        viewModel.openArchivedGames()
        assertEquals(AppScreen.ARCHIVED_GAMES, viewModel.screen)
        viewModel.openAbout()
        assertEquals(AppScreen.ABOUT, viewModel.screen)
        assertTrue(File(storeDir, "profile.json").exists())
        assertTrue(File(storeDir, "settings.json").exists())

        // Recreate the ViewModel and verify persisted values restore while startup opens at Home.
        val restored = AppViewModel(FileAppStateStorage(storeDir))
        assertEquals(AppScreen.HOME, restored.screen)
        assertEquals("Casey Observer", restored.profileName)
        assertEquals(ObserverAvatarPreference.BLUE, restored.avatarPreference)
        assertEquals(ObserverAvatarPreference.BLUE, restored.homeAvatarPreference)
        assertEquals(TimingAlertGlobalMode.OFF, restored.timingAlertPreferences.globalMode)
        assertFalse(restored.automaticallyAdvanceCountdowns)
        assertFalse(restored.automaticallyLockLivePoint)
        assertEquals(0.4f, restored.timingAlertPreferences.soundVolume, 0f)
        assertEquals(420L, restored.timingAlertPreferences.vibrationDurationMillis)
        assertTrue(restored.timingAlertPreferences.vibrateWithSounds)
        assertEquals(
            TimingAlertMode.DING,
            restored.timingAlertPreferences.cueModes[TimingCueId.PULLING_TIME_VIOLATION],
        )
        assertEquals(
            1,
            restored.timingAlertPreferences.repeatCountFor(TimingCueId.PULLING_TIME_VIOLATION),
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

    /// Verify restoring timing cue defaults resets cue choices without changing global sound settings.
    @Test
    fun timingCueDefaultsCanBeRestoredWithoutChangingGlobalSoundSettings() {
        val viewModel = AppViewModel(NoOpAppStateStorage)

        viewModel.updateTimingAlertGlobalMode(TimingAlertGlobalMode.SOUNDS_ON)
        viewModel.updateTimingAlertSoundVolume(0.4f)
        viewModel.updateTimingAlertVibrationDuration(420L)
        viewModel.updateTimingAlertVibrateWithSounds(true)
        viewModel.updateTimingCueMode(TimingCueId.RECEIVING_TWENTY_FOR_HAND, TimingAlertMode.NONE)
        viewModel.updateTimingCueRepeatCount(TimingCueId.RECEIVING_TWENTY_FOR_HAND, 3)
        viewModel.updateTimingCueMode(TimingCueId.HARD_CAP, TimingAlertMode.BEEP)
        viewModel.updateTimingCueRepeatCount(TimingCueId.HARD_CAP, 1)

        viewModel.resetTimingCueSettingsToDefaults()

        assertEquals(TimingAlertGlobalMode.SOUNDS_ON, viewModel.timingAlertPreferences.globalMode)
        assertEquals(0.4f, viewModel.timingAlertPreferences.soundVolume, 0f)
        assertEquals(420L, viewModel.timingAlertPreferences.vibrationDurationMillis)
        assertTrue(viewModel.timingAlertPreferences.vibrateWithSounds)
        assertEquals(
            TimingAlertMode.TICK,
            viewModel.timingAlertPreferences.settingsModeFor(TimingCueId.RECEIVING_TWENTY_FOR_HAND),
        )
        assertEquals(2, viewModel.timingAlertPreferences.repeatCountFor(TimingCueId.RECEIVING_TWENTY_FOR_HAND))
        assertEquals(TimingAlertMode.DING, viewModel.timingAlertPreferences.settingsModeFor(TimingCueId.HARD_CAP))
        assertEquals(3, viewModel.timingAlertPreferences.repeatCountFor(TimingCueId.HARD_CAP))
    }

    /// Verify a random avatar preference resolves to a deterministic concrete home avatar on startup.
    @Test
    fun randomAvatarPreferenceResolvesHomeAvatarOnStartup() {
        // Use a fixed chooser to verify random-avatar timing without relying on randomness.
        val viewModel = AppViewModel(
            appStateStorage = NoOpAppStateStorage,
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

    /// Verify starting a new game archives a completed game without adding another close-game wrapper.
    @Test
    fun startingNewGameArchivesCompletedGameWithoutClosingItAgain() {
        val viewModel = AppViewModel(NoOpAppStateStorage)
        viewModel.startNewGame()
        viewModel.finishSetup()

        val beforeUndoAction = viewModel.liveState!!
        val completedGame = beforeUndoAction.copy(
            phase = GamePhase.GAME_OVER,
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
        assertEquals(GamePhase.GAME_OVER, viewModel.archivedGames.single().state.phase)
        assertNull(viewModel.archivedGames.single().restorableState)
        assertNull(viewModel.archivedGames.single().state.countdown)
        assertEquals("Undo End Game", viewModel.archivedGames.single().state.undoEntry?.label)
        assertEquals(beforeUndoAction.pruneUndoHistory(), viewModel.archivedGames.single().state.undoEntry!!.previous)
        assertNull(viewModel.archivedGames.single().state.redoEntry)
    }

    /// Verify restoring an accidentally archived active game makes it current again without old undo state.
    @Test
    fun archivedActiveGameCanBeRestoredAsCurrentGame() {
        val storeDir = temporaryFolder.newFolder()
        val viewModel = AppViewModel(FileAppStateStorage(storeDir))
        viewModel.startNewGame()
        val setup = viewModel.setupState.copy(
            teamOne = TeamSetup("Viscous Coupling", TeamColorChoice.BLUE),
            teamTwo = TeamSetup("Animal", TeamColorChoice.PINK),
        )
        viewModel.updateSetup(setup)
        viewModel.finishSetup()
        val activeGame = viewModel.liveState!!.beginLivePoint()
        assertNotNull(activeGame.undoEntry)
        viewModel.updateLiveGame(activeGame)

        // Starting a new game archives a completed summary but keeps the original live state for restore.
        viewModel.startNewGame()
        assertEquals(AppScreen.SETUP, viewModel.screen)
        assertTrue(viewModel.hasSetupDraft)
        assertNull(viewModel.liveState)
        assertEquals(1, viewModel.archivedGames.size)
        val archivedGame = viewModel.archivedGames.single()
        assertEquals(GamePhase.GAME_OVER, archivedGame.state.phase)
        assertEquals(GamePhase.LIVE_POINT, archivedGame.restorableState!!.phase)
        assertNull(archivedGame.restorableState.undoEntry)
        assertNull(archivedGame.restorableState.redoEntry)

        // Reload the ViewModel to verify the recoverable active state survives phone storage.
        val restoredViewModel = AppViewModel(FileAppStateStorage(storeDir))
        assertEquals(archivedGame, restoredViewModel.archivedGames.single())
        val replacementSetup = restoredViewModel.setupState.copy(
            teamOne = TeamSetup("Replacement Current", TeamColorChoice.WHITE),
            teamTwo = TeamSetup("Replacement Opponent", TeamColorChoice.BLUE),
        )
        restoredViewModel.updateSetup(replacementSetup)
        restoredViewModel.finishSetup()
        val replacementCurrent = restoredViewModel.liveState!!.beginLivePoint()
        restoredViewModel.updateLiveGame(replacementCurrent)

        restoredViewModel.restoreArchivedGame(0)

        assertEquals(AppScreen.LIVE, restoredViewModel.screen)
        assertEquals(1, restoredViewModel.archivedGames.size)
        val replacementArchive = restoredViewModel.archivedGames.single()
        assertEquals(GamePhase.GAME_OVER, replacementArchive.state.phase)
        assertEquals(replacementSetup.teamOne.name, replacementArchive.state.teamOne.name)
        assertEquals(replacementSetup.teamTwo.name, replacementArchive.state.teamTwo.name)
        assertEquals(replacementCurrent.pruneUndoHistory(clearCountdown = false), replacementArchive.restorableState)
        assertFalse(restoredViewModel.hasSetupDraft)
        assertEquals(SetupMode.EDIT_CURRENT_GAME, restoredViewModel.setupMode)
        assertEquals(activeGame.pruneUndoHistory(clearCountdown = false), restoredViewModel.liveState)
        assertEquals(GamePhase.LIVE_POINT, restoredViewModel.liveState!!.phase)
        assertNull(restoredViewModel.liveState!!.undoEntry)
        assertNull(restoredViewModel.liveState!!.redoEntry)
        assertEquals(setup.teamOne.name, restoredViewModel.setupState.teamOne.name)
        assertEquals(setup.teamTwo.name, restoredViewModel.setupState.teamTwo.name)
    }

    /// Verify new-game setup defaults to the next half hour and rolls the setup date across midnight.
    @Test
    fun newGameSetupDefaultsUseNextHalfHourAndAdvanceDateAcrossMidnight() {
        val sameDaySetup = newGameSetupState(LocalDateTime.of(2026, 1, 1, 23, 0))
        assertEquals(LocalDate.of(2026, 1, 1), sameDaySetup.startDate)
        assertEquals(LocalTime.of(23, 0), sameDaySetup.startTime)
        assertEquals(105, sameDaySetup.rules.hardCapMinutes)

        val nextDaySetup = newGameSetupState(LocalDateTime.of(2026, 1, 1, 23, 45))
        assertEquals(LocalDate.of(2026, 1, 2), nextDaySetup.startDate)
        assertEquals(LocalTime.MIDNIGHT, nextDaySetup.startTime)
    }

    /// Verify a new game's default rules prefer the most recent archived game's rules.
    @Test
    fun newGameRulesDefaultFromArchivedGame() {
        val viewModel = AppViewModel(NoOpAppStateStorage)
        viewModel.startNewGame()
        val tournamentRules = GameRules(
            gameTo = 13,
            halftimeMinutes = 5,
            useHalfCap = true,
            halfCapMinutes = 35,
            useSoftCap = false,
            softCapMinutes = 75,
            useHardCap = true,
            hardCapMinutes = 95,
            timeoutsPerHalf = 1,
            hasFloaterTimeout = true,
        )
        viewModel.updateSetup(viewModel.setupState.copy(rules = tournamentRules))
        viewModel.finishSetup()
        viewModel.updateLiveGame(viewModel.liveState!!.copy(phase = GamePhase.GAME_OVER))
        viewModel.archiveCompletedGame()

        viewModel.startNewGame()

        assertEquals(tournamentRules, viewModel.setupState.rules)
        assertNull(viewModel.liveState)
        assertEquals(SetupMode.NEW_GAME, viewModel.setupMode)
    }

    /// Verify starting over from a current game uses that current game's rules as new defaults.
    @Test
    fun newGameRulesDefaultFromCurrentGameWhenStartingOver() {
        val viewModel = AppViewModel(NoOpAppStateStorage)
        viewModel.startNewGame()
        val currentRules = GameRules(gameTo = 11, hardCapMinutes = 80, hasFloaterTimeout = true)
        viewModel.updateSetup(viewModel.setupState.copy(rules = currentRules))
        viewModel.finishSetup()
        viewModel.updateLiveGame(viewModel.liveState!!.beginLivePoint())

        viewModel.startNewGame()

        assertEquals(currentRules, viewModel.setupState.rules)
        assertEquals(currentRules, viewModel.archivedGames.single().state.rules)
        assertEquals("Closed when new game started", viewModel.archivedGames.single().subtitle)
    }

    /// Verify persisted app state restores both setup drafts and active-game undo history.
    @Test
    fun persistentStoreRestoresSetupDraftAndActiveGameUndoHistory() {
        val storeDir = temporaryFolder.newFolder()
        val store = FileAppStateStorage(storeDir)
        val viewModel = AppViewModel(store)

        // Start a new-game setup draft and verify a fresh ViewModel keeps the draft but opens at Home.
        viewModel.startNewGame()
        val persistedRules = GameRules(gameTo = 13, hardCapMinutes = 95, hasFloaterTimeout = true)
        val draftedSetup = viewModel.setupState.copy(
            rules = persistedRules,
            teamOne = TeamSetup("Viscous Coupling", TeamColorChoice.BLUE),
            teamTwo = TeamSetup("Animal", TeamColorChoice.PINK),
        )
        viewModel.updateSetup(draftedSetup)

        val draftRestored = AppViewModel(FileAppStateStorage(storeDir))
        assertEquals(AppScreen.HOME, draftRestored.screen)
        assertEquals(draftedSetup, draftRestored.setupState)
        assertEquals(persistedRules, draftRestored.setupState.rules)
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

        val gameRestored = AppViewModel(FileAppStateStorage(storeDir))
        assertEquals(AppScreen.HOME, gameRestored.screen)
        assertEquals(scoredState, gameRestored.liveState)
        assertEquals(persistedRules, gameRestored.liveState!!.rules)
        assertNotNull(gameRestored.liveState!!.undoEntry)
        assertSame(gameRestored.liveState!!.rules, gameRestored.liveState!!.undoEntry!!.previous.rules)
        gameRestored.resumeCurrentGame()
        assertEquals(AppScreen.LIVE, gameRestored.screen)
        val undoRestoredState = gameRestored.liveState!!.undoLastAction()
        assertEquals(livePointState, undoRestoredState.copy(redoEntry = null))
        assertNotNull(undoRestoredState.redoEntry)

        gameRestored.updateLiveGame(undoRestoredState)
        val redoRestored = AppViewModel(FileAppStateStorage(storeDir))
        assertEquals(undoRestoredState, redoRestored.liveState)
        assertEquals(scoredState, redoRestored.liveState!!.redoLastAction())
    }

    /// Verify live event updates persist at the ViewModel boundary.
    @Test
    fun liveGameEventsPersistThroughUpdateLiveGame() {
        val store = RecordingAppStateStorage()
        val viewModel = AppViewModel(store)

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

    /// Verify profile, settings, and current-game changes persist through their own buckets.
    @Test
    fun appDataBucketsPersistIndependently() {
        val store = RecordingAppStateStorage()
        val viewModel = AppViewModel(store)

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
        NoOpAppStateStorage.saveCurrentGameState(CurrentGameSnapshot())
        NoOpAppStateStorage.saveProfile(Profile())
        NoOpAppStateStorage.saveSettings(Settings())
        NoOpAppStateStorage.saveArchivedGames(emptyList())
    }

    /// Verify archived games are persisted as pruned summaries separate from current-game state.
    @Test
    fun persistentStoreWritesArchivedSummariesSeparatelyFromCurrentGameSnapshot() {
        val storeDir = temporaryFolder.newFolder()
        val viewModel = AppViewModel(FileAppStateStorage(storeDir))

        // Complete and archive a game that still has live-only countdown and undo state.
        viewModel.startNewGame()
        viewModel.finishSetup()
        val beforeEndGame = viewModel.liveState!!
        val completedGame = beforeEndGame.copy(
            phase = GamePhase.GAME_OVER,
            countdown = CountdownState(
                kind = CountdownKind.BETWEEN_POINTS,
                label = "Pull in",
                durationSeconds = 80,
                targetEpoch = 80_000L,
                betweenPointsTarget = BetweenPointsCountdownTarget.PULL,
            ),
            undoEntry = UndoEntry("Undo End Game", beforeEndGame),
        ).withEventLogEntries(
            listOf(
                EventLogEntry(
                    timestampEpoch = beforeEndGame.startEpoch,
                    type = EventLogType.FIRST_PULL,
                    team = TeamId.TEAM_ONE,
                ),
                EventLogEntry(
                    timestampEpoch = beforeEndGame.startEpoch + 60_000L,
                    type = EventLogType.GOAL,
                    team = TeamId.TEAM_TWO,
                ),
            )
        )
        viewModel.updateLiveGame(completedGame)
        viewModel.goHome()
        viewModel.archiveCompletedGame()

        // Verify current game and archived summaries are written separately from profile/settings.
        assertTrue(File(storeDir, "current_game_state.json").exists())
        assertFalse(File(storeDir, "profile.json").exists())
        assertFalse(File(storeDir, "settings.json").exists())
        assertTrue(File(File(storeDir, "archived_games"), "00000.json").exists())

        // Restore from disk and verify the archived game keeps summary state plus the end-game undo.
        val restored = AppViewModel(FileAppStateStorage(storeDir))
        assertEquals(AppScreen.HOME, restored.screen)
        assertNull(restored.liveState)
        assertEquals(1, restored.archivedGames.size)
        assertNull(restored.archivedGames.single().state.countdown)
        assertEquals("Undo End Game", restored.archivedGames.single().state.undoEntry?.label)
        assertEquals(
            beforeEndGame.pruneUndoHistory(),
            restored.archivedGames.single().state.undoEntry!!.previous,
        )
        assertNull(restored.archivedGames.single().state.redoEntry)
        assertEquals(GamePhase.GAME_OVER, restored.archivedGames.single().state.phase)
        assertEquals(completedGame.eventLog, restored.archivedGames.single().state.eventLog)
    }

    /// Verify persistence handles ordinary empty/corrupt filesystem shapes without inventing app state.
    @Test
    fun persistentStoreLoadsOnlyCurrentStateAndJsonArchives() {
        val storeDir = temporaryFolder.newFolder()
        val store = FileAppStateStorage(storeDir)

        assertNull(store.loadCurrentGameState())
        assertTrue(store.loadArchivedGames().isEmpty())

        val setup = newGameSetupState(LocalDateTime.of(2026, 5, 11, 10, 0))
        val currentGameState = CurrentGameSnapshot(
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
        assertEquals(setOf(PersistedData.GAME_STATE), store.resetPersistedDataAreas)

        val archivedOne = ArchivedGame(
            createLiveGameState(setup).copy(phase = GamePhase.GAME_OVER),
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

    /// Verify corrupted current-game fields reset narrowly while preserving readable profile/settings.
    @Test
    fun persistentStoreSalvagesReadableSplitStateFiles() {
        val storeDir = temporaryFolder.newFolder()
        val store = FileAppStateStorage(storeDir)
        val setup = newGameSetupState(LocalDateTime.of(2026, 5, 11, 10, 0))
        val liveState = createLiveGameState(setup).beginLivePoint()
        val timingPreferences = TimingAlertPreferences(
            globalMode = TimingAlertGlobalMode.VIBRATION_ONLY,
            soundVolume = 0.4f,
        )
        val savedCurrentGameState = CurrentGameSnapshot(
            setupState = setup,
            liveState = liveState,
            setupMode = SetupMode.EDIT_CURRENT_GAME,
        )
        val savedArchive = ArchivedGame(
            createLiveGameState(setup).copy(phase = GamePhase.GAME_OVER),
            "Final",
        )
        store.saveCurrentGameState(savedCurrentGameState)
        store.saveProfile(Profile(profileName = "Casey Observer"))
        store.saveSettings(Settings(timingAlertPreferences = timingPreferences))

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
        assertEquals(setOf(PersistedData.GAME_STATE), store.resetPersistedDataAreas)

        val recoveredViewModel = AppViewModel(FileAppStateStorage(storeDir))
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
        store.saveProfile(Profile(profileName = "Casey Observer"))
        File(storeDir, "profile.json").replaceText("\"profileName\": \"Casey Observer\"", "\"profileName\": 7")
        assertEquals(Profile(), store.loadProfile())

        store.saveSettings(Settings(timingAlertPreferences = TimingAlertPreferences(soundVolume = 0.35f)))
        File(storeDir, "settings.json").replaceText(
            "\"timingAlertPreferences\": {",
            "\"timingAlertPreferences\": \"broken\", \"ignoredTimingAlertPreferences\": {",
        )
        assertEquals(Settings(), store.loadSettings())

        store.saveArchivedGames(listOf(savedArchive))
        File(File(storeDir, "archived_games"), "00000.json").replaceText(
            "\"state\": {",
            "\"state\": \"broken\", \"ignoredState\": {",
        )
        assertTrue(store.loadArchivedGames().isEmpty())
        assertEquals(
            setOf(
                PersistedData.GAME_STATE,
                PersistedData.PROFILE,
                PersistedData.SETTINGS,
                PersistedData.ARCHIVED_GAMES,
            ),
            store.resetPersistedDataAreas,
        )

        // A single bad archive file should be skipped without losing the other readable summaries.
        val archiveStoreDir = temporaryFolder.newFolder()
        val archiveStore = FileAppStateStorage(archiveStoreDir)
        val archivedOne = ArchivedGame(savedArchive.state, "First")
        val archivedTwo = archivedOne.copy(subtitle = "Second")
        archiveStore.saveArchivedGames(listOf(archivedOne, archivedTwo))
        File(File(archiveStoreDir, "archived_games"), "00000.json").writeText("{not-json")

        assertEquals(listOf(archivedTwo), archiveStore.loadArchivedGames())
        assertEquals(setOf(PersistedData.ARCHIVED_GAMES), archiveStore.resetPersistedDataAreas)

        val archiveViewModel = AppViewModel(FileAppStateStorage(archiveStoreDir))
        assertEquals(listOf(archivedTwo), archiveViewModel.archivedGames)
        assertEquals(
            "Sorry, some phone data was corrupt, so UltiObserver had to revert to default values for Archived Games.",
            archiveViewModel.startupRecoveryNotice!!.message,
        )

        val repairedArchiveStore = FileAppStateStorage(archiveStoreDir)
        assertEquals(listOf(archivedTwo), repairedArchiveStore.loadArchivedGames())
        assertTrue(repairedArchiveStore.resetPersistedDataAreas.isEmpty())
        val restoredAfterRecovery = AppViewModel(FileAppStateStorage(archiveStoreDir))
        assertEquals(listOf(archivedTwo), restoredAfterRecovery.archivedGames)
        assertNull(restoredAfterRecovery.startupRecoveryNotice)
    }

    /// Verify app-version metadata is written and invalid versions reset only affected buckets.
    @Test
    fun persistentStoreHandlesMissingInvalidAndUnsupportedVersions() {
        // Build representative saved records for every persisted app-data bucket.
        val storeDir = temporaryFolder.newFolder()
        val store = FileAppStateStorage(storeDir)
        val setup = newGameSetupState(LocalDateTime.of(2026, 5, 11, 10, 0))
        val savedCurrentGameState = CurrentGameSnapshot(
            setupState = setup,
            liveState = createLiveGameState(setup),
            setupMode = SetupMode.EDIT_CURRENT_GAME,
        )
        val savedProfile = Profile(profileName = "Casey Observer")
        val savedSettings = Settings(
            timingAlertPreferences = TimingAlertPreferences(soundVolume = 0.35f),
        )
        val savedArchive = ArchivedGame(
            createLiveGameState(setup).copy(phase = GamePhase.GAME_OVER),
            "Final",
        )

        val debugVersionName = "${BuildConfig.VERSION_NAME}-debug"

        assertEquals(APP_STATE_VERSION_NAME, CurrentGameSnapshot().versionName)
        assertEquals(APP_STATE_VERSION_CODE, CurrentGameSnapshot().versionCode)
        assertEquals(APP_STATE_VERSION_NAME, Profile().versionName)
        assertEquals(APP_STATE_VERSION_CODE, Profile().versionCode)
        assertEquals(APP_STATE_VERSION_NAME, Settings().versionName)
        assertEquals(APP_STATE_VERSION_CODE, Settings().versionCode)
        assertEquals(APP_STATE_VERSION_NAME, ArchivedGame(createLiveGameState(setup), "").versionName)
        assertEquals(APP_STATE_VERSION_CODE, ArchivedGame(createLiveGameState(setup), "").versionCode)
        val persistedVersion = AppVersion(
            versionName = debugVersionName,
            versionCode = APP_STATE_VERSION_CODE,
        )
        assertEquals(debugVersionName, persistedVersion.versionName)
        assertEquals(APP_STATE_VERSION_CODE, persistedVersion.versionCode)

        // Missing version names should reset each affected split-state area.
        store.saveProfile(savedProfile)
        File(storeDir, "profile.json").removeStoredAppVersion()
        assertEquals(Profile(), store.loadProfile())
        assertEquals(setOf(PersistedData.PROFILE), store.resetPersistedDataAreas)

        store.saveSettings(savedSettings)
        File(storeDir, "settings.json").removeStoredAppVersion()
        assertEquals(Settings(), store.loadSettings())
        assertEquals(setOf(PersistedData.PROFILE, PersistedData.SETTINGS), store.resetPersistedDataAreas)

        store.saveArchivedGames(listOf(savedArchive))
        File(File(storeDir, "archived_games"), "00000.json").removeStoredAppVersion()
        assertTrue(store.loadArchivedGames().isEmpty())
        assertEquals(
            setOf(PersistedData.PROFILE, PersistedData.SETTINGS, PersistedData.ARCHIVED_GAMES),
            store.resetPersistedDataAreas,
        )

        // Wrongly typed version fields and unsupported version codes should also reset narrowly.
        store.saveCurrentGameState(savedCurrentGameState)
        File(storeDir, "current_game_state.json")
            .replaceText("\"versionName\": \"${BuildConfig.VERSION_NAME}\"", "\"versionName\": 1")
        assertEquals(CurrentGameSnapshot(), store.loadCurrentGameState())
        assertEquals(
            setOf(
                PersistedData.GAME_STATE,
                PersistedData.PROFILE,
                PersistedData.SETTINGS,
                PersistedData.ARCHIVED_GAMES,
            ),
            store.resetPersistedDataAreas,
        )

        store.saveProfile(savedProfile)
        File(storeDir, "profile.json").replaceText("\"versionCode\": 1", "\"versionCode\": \"bad\"")
        assertEquals(Profile(), store.loadProfile())
        assertEquals(
            setOf(
                PersistedData.GAME_STATE,
                PersistedData.SETTINGS,
                PersistedData.ARCHIVED_GAMES,
                PersistedData.PROFILE,
            ),
            store.resetPersistedDataAreas,
        )

        store.saveProfile(savedProfile)
        File(storeDir, "profile.json").removeStoredVersionCode()
        assertEquals(Profile(), store.loadProfile())
        assertEquals(
            setOf(
                PersistedData.GAME_STATE,
                PersistedData.SETTINGS,
                PersistedData.ARCHIVED_GAMES,
                PersistedData.PROFILE,
            ),
            store.resetPersistedDataAreas,
        )

        store.saveProfile(savedProfile)
        File(storeDir, "profile.json").replaceText("\"versionCode\": 1", "\"versionCode\": 99")
        assertEquals(Profile(), store.loadProfile())
        assertEquals(
            setOf(
                PersistedData.GAME_STATE,
                PersistedData.SETTINGS,
                PersistedData.ARCHIVED_GAMES,
                PersistedData.PROFILE,
            ),
            store.resetPersistedDataAreas,
        )

        store.saveSettings(savedSettings)
        File(storeDir, "settings.json").replaceText("\"versionCode\": 1", "\"versionCode\": \"bad\"")
        assertEquals(Settings(), store.loadSettings())
        assertEquals(
            setOf(
                PersistedData.GAME_STATE,
                PersistedData.PROFILE,
                PersistedData.ARCHIVED_GAMES,
                PersistedData.SETTINGS,
            ),
            store.resetPersistedDataAreas,
        )

        store.saveArchivedGames(listOf(savedArchive))
        File(File(storeDir, "archived_games"), "00000.json")
            .replaceText("\"versionCode\": 1", "\"versionCode\": \"bad\"")
        assertTrue(store.loadArchivedGames().isEmpty())
        assertEquals(
            setOf(
                PersistedData.GAME_STATE,
                PersistedData.PROFILE,
                PersistedData.SETTINGS,
                PersistedData.ARCHIVED_GAMES,
            ),
            store.resetPersistedDataAreas,
        )

        // Debug-style version names are accepted while later unsupported version codes are rejected.
        store.saveProfile(savedProfile)
        File(storeDir, "profile.json")
            .replaceText("\"versionName\": \"${BuildConfig.VERSION_NAME}\"", "\"versionName\": \"$debugVersionName\"")
        assertEquals(savedProfile.copy(versionName = debugVersionName), store.loadProfile())
        assertEquals(setOf(PersistedData.GAME_STATE, PersistedData.SETTINGS, PersistedData.ARCHIVED_GAMES), store.resetPersistedDataAreas)

        store.saveSettings(savedSettings)
        File(storeDir, "settings.json").replaceText("\"versionCode\": 1", "\"versionCode\": 99")
        assertEquals(Settings(), store.loadSettings())
        assertEquals(
            setOf(
                PersistedData.GAME_STATE,
                PersistedData.ARCHIVED_GAMES,
                PersistedData.SETTINGS,
            ),
            store.resetPersistedDataAreas,
        )

        store.saveArchivedGames(listOf(savedArchive))
        File(File(storeDir, "archived_games"), "00000.json")
            .replaceText("\"versionName\": \"${BuildConfig.VERSION_NAME}\"", "\"versionName\": \"$debugVersionName\"")
        assertEquals(listOf(savedArchive.copy(versionName = debugVersionName)), store.loadArchivedGames())
        assertEquals(
            setOf(PersistedData.GAME_STATE, PersistedData.SETTINGS),
            store.resetPersistedDataAreas,
        )

        store.saveArchivedGames(listOf(savedArchive))
        File(File(storeDir, "archived_games"), "00000.json")
            .replaceText("\"versionCode\": 1", "\"versionCode\": 99")
        assertTrue(store.loadArchivedGames().isEmpty())
        assertEquals(
            setOf(PersistedData.GAME_STATE, PersistedData.SETTINGS, PersistedData.ARCHIVED_GAMES),
            store.resetPersistedDataAreas,
        )
    }

    /// Verify malformed split-state JSON resets each affected app-data area without crashing startup.
    @Test
    fun persistentStoreReportsMalformedSplitStateAsRecoveredDefaults() {
        val storeDir = temporaryFolder.newFolder()
        File(storeDir, "current_game_state.json").writeText("{not-json")
        File(storeDir, "profile.json").writeText("{not-json")
        File(storeDir, "settings.json").writeText("{not-json")

        val viewModel = AppViewModel(FileAppStateStorage(storeDir))

        assertEquals(AppScreen.HOME, viewModel.screen)
        assertNull(viewModel.liveState)
        assertEquals("", viewModel.profileName)
        assertEquals(TimingAlertPreferences(), viewModel.timingAlertPreferences)
        assertEquals(
            setOf(
                PersistedData.GAME_STATE,
                PersistedData.PROFILE,
                PersistedData.SETTINGS,
            ),
            viewModel.startupRecoveryNotice!!.resetAreas,
        )
        assertEquals("Phone Data Reset", viewModel.startupRecoveryNotice!!.title)
        assertEquals(
            "Sorry, some phone data was corrupt, so UltiObserver had to revert to default values for Current Game, Profile, and Settings.",
            viewModel.startupRecoveryNotice!!.message,
        )

        val twoAreaNotice = RecoveryNotice(
            setOf(PersistedData.PROFILE, PersistedData.SETTINGS)
        )
        assertEquals(
            "Sorry, some phone data was corrupt, so UltiObserver had to revert to default values for Profile and Settings.",
            twoAreaNotice.message,
        )
        assertThrows(IllegalArgumentException::class.java) {
            RecoveryNotice(emptySet())
        }

        val unreadableStoreDir = temporaryFolder.newFolder()
        assertTrue(File(unreadableStoreDir, "profile.json").mkdir())
        val unreadableStore = FileAppStateStorage(unreadableStoreDir)
        assertEquals(Profile(), unreadableStore.loadProfile())
        assertEquals(setOf(PersistedData.PROFILE), unreadableStore.resetPersistedDataAreas)
    }

    /// Verify failed current-game writes do not leave stale temporary files behind.
    @Test
    fun persistentStoreCleansTemporaryFileAfterFailedWrite() {
        val storeDir = temporaryFolder.newFolder()
        val currentGameStatePath = File(storeDir, "current_game_state.json")

        // Make the destination an undeletable non-empty directory so replacement fails.
        assertTrue(currentGameStatePath.mkdir())
        assertTrue(File(currentGameStatePath, "blocking-child").writeText("blocker").let { true })

        val store = FileAppStateStorage(storeDir)
        val setup = newGameSetupState(LocalDateTime.of(2026, 5, 11, 10, 0))

        assertThrows(IOException::class.java) {
            store.saveCurrentGameState(
                CurrentGameSnapshot(
                    setupState = setup,
                    liveState = null,
                    setupMode = SetupMode.NEW_GAME,
                )
            )
        }
        assertFalse(File(storeDir, ".current_game_state.json.tmp").exists())
    }

    /// Verify the non-atomic replace fallback still writes readable state.
    @Test
    fun persistentStoreFallsBackWhenAtomicMoveIsUnavailable() {
        val storeDir = temporaryFolder.newFolder()
        var atomicMoveAttempts = 0
        val store = FileAppStateStorage(
            rootDir = storeDir,
            moveFileAtomically = { source, target ->
                atomicMoveAttempts += 1
                throw AtomicMoveNotSupportedException(source.path, target.path, "forced fallback")
            },
        )
        val setup = newGameSetupState(LocalDateTime.of(2026, 5, 11, 10, 0))
        val savedState = CurrentGameSnapshot(
            setupState = setup,
            liveState = null,
            setupMode = SetupMode.NEW_GAME,
        )

        // Force the atomic path to fail and verify the fallback path replaces each split file.
        store.saveCurrentGameState(savedState)
        store.saveProfile(Profile(profileName = "Casey Observer"))
        store.saveSettings(Settings(timingAlertPreferences = TimingAlertPreferences()))
        assertEquals(3, atomicMoveAttempts)
        assertFalse(File(storeDir, ".current_game_state.json.tmp").exists())
        assertFalse(File(storeDir, ".profile.json.tmp").exists())
        assertFalse(File(storeDir, ".settings.json.tmp").exists())

        // Load through a normal store to verify the fallback wrote valid serialized state.
        val restoredState = FileAppStateStorage(storeDir).loadCurrentGameState()
        assertEquals(savedState, restoredState)
    }
}

/// Remove both persisted app-version fields from a JSON fixture file.
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

/// Remove only the persisted version-code field from a JSON fixture file.
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

/**
 * Replace text inside a JSON fixture file.
 *
 * @param oldValue The text to replace.
 * @param newValue The replacement text.
 */
private fun File.replaceText(oldValue: String, newValue: String) {
    writeText(readText().replace(oldValue, newValue))
}

/// Recording fake for AppViewModel persistence writes without touching the file system.
private class RecordingAppStateStorage : AppStateStorage {
    val savedCurrentGameStates = mutableListOf<CurrentGameSnapshot>()
    val savedProfiles = mutableListOf<Profile>()
    val savedSettings = mutableListOf<Settings>()
    val savedArchivedGames = mutableListOf<List<ArchivedGame>>()

    override val resetPersistedDataAreas: Set<PersistedData> = emptySet()

    /// Load no current game for this recording fake.
    override fun loadCurrentGameState(): CurrentGameSnapshot? = null

    /**
     * Record a current-game save request.
     *
     * @param state The current-game state passed by the ViewModel.
     */
    override fun saveCurrentGameState(state: CurrentGameSnapshot) {
        savedCurrentGameStates += state
    }

    /// Load no profile for this recording fake.
    override fun loadProfile(): Profile? = null

    /**
     * Record a profile save request.
     *
     * @param state The profile state passed by the ViewModel.
     */
    override fun saveProfile(state: Profile) {
        savedProfiles += state
    }

    /// Load no settings for this recording fake.
    override fun loadSettings(): Settings? = null

    /**
     * Record a settings save request.
     *
     * @param state The settings state passed by the ViewModel.
     */
    override fun saveSettings(state: Settings) {
        savedSettings += state
    }

    /// Load no archived games for this recording fake.
    override fun loadArchivedGames(): List<ArchivedGame> = emptyList()

    /**
     * Record an archived-games save request.
     *
     * @param games The archived games passed by the ViewModel.
     */
    override fun saveArchivedGames(games: List<ArchivedGame>) {
        savedArchivedGames += games
    }
}
