package rmjarvis.ultiobserver

import java.io.File
import java.io.IOException
import java.nio.file.AtomicMoveNotSupportedException
import java.time.LocalDateTime
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

/**
 * Tests for AppViewModel persistence coordination and file-backed app-state storage.
 */
class TestPersistence : GameDomainTestFixtures() {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    /**
     * Verify profile fields, timing-alert preferences, timing-cue overrides, and settings
     * navigation are saved and restored after restart.
     */
    @Test
    fun profileAndSettingsPersistence() {
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
        viewModel.updateShowDefenseCountdowns(true)
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
        viewModel.updateTimingCueMode(TimingCueId.OFFENSE_TEN, TimingAlertMode.VIBRATE)
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
            viewModel.timingAlertPreferences.alertModeFor(TimingCueId.OFFENSE_TEN),
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
        assertTrue(restored.showDefenseCountdowns)
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
            restored.timingAlertPreferences.cueModes[TimingCueId.OFFENSE_TEN],
        )
        assertEquals(
            TimingAlertMode.NONE,
            restored.timingAlertPreferences.alertModeFor(TimingCueId.PULLING_TIME_VIOLATION),
        )
        assertEquals(
            TimingAlertMode.NONE,
            restored.timingAlertPreferences.alertModeFor(TimingCueId.OFFENSE_TEN),
        )
    }

    /**
     * Verify persisted current-game state restores setup drafts, active live games, and
     * undo/redo history across ViewModel restarts.
     */
    @Test
    fun currentGamePersistence() {
        // Use real file storage so setup-draft and live-game restart behavior is exercised.
        val storeDir = temporaryFolder.newFolder()
        val store = FileAppStateStorage(storeDir)
        val viewModel = AppViewModel(store)

        // A fresh ViewModel should keep a setup draft but open at Home.
        viewModel.startNewGame(now = 123_000L)
        val persistedRules = GameRules(gameTo = 13, hardCapMinutes = 95, hasFloaterTimeout = true)
        val draftedSetup = viewModel.setupState.copy(
            rules = persistedRules,
            teamOne = TeamIdentity("Viscous Coupling", TeamColorChoice.BLUE),
            teamTwo = TeamIdentity("Animal", TeamColorChoice.PINK),
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
        draftRestored.finishSetup(now = 123_000L)
        assertFalse(draftRestored.hasSetupDraft)
        val livePointState = draftRestored.liveState!!.beginLivePoint()
        val scoredState = livePointState.recordGoal(
            scoringTeam = TeamId.TEAM_ONE,
            now = livePointState.startEpoch + 5 * 60_000L,
        )
        draftRestored.updateLiveGame(scoredState)

        // A restarted ViewModel should restore the live game and its undo history.
        val gameRestored = AppViewModel(FileAppStateStorage(storeDir))
        assertEquals(AppScreen.HOME, gameRestored.screen)
        assertEquals(scoredState, gameRestored.liveState)
        assertEquals(persistedRules, gameRestored.liveState!!.rules)
        assertNotNull(gameRestored.liveState!!.undoEntry)
        assertSame(
            gameRestored.liveState!!.rules,
            gameRestored.liveState!!.undoEntry!!.previous.rules,
        )
        gameRestored.resumeCurrentGame()
        assertEquals(AppScreen.LIVE, gameRestored.screen)
        val undoRestoredState = gameRestored.liveState!!.undoLastAction()
        assertEquals(livePointState, undoRestoredState.copy(redoEntry = null))
        assertNotNull(undoRestoredState.redoEntry)

        // Persisting the undone state should preserve the redo entry across another restart.
        gameRestored.updateLiveGame(undoRestoredState)
        val redoRestored = AppViewModel(FileAppStateStorage(storeDir))
        assertEquals(undoRestoredState, redoRestored.liveState)
        assertEquals(scoredState, redoRestored.liveState!!.redoLastAction())
    }

    /**
     * Verify live event updates persist at the ViewModel boundary without writing unrelated
     * profile or settings buckets.
     */
    @Test
    fun liveGameEventPersistence() {
        // Use a recording store so only ViewModel save requests are inspected.
        val store = RecordingAppStateStorage()
        val viewModel = AppViewModel(store)

        // Start a live game and clear the setup saves so the event assertion is focused.
        viewModel.startNewGame(now = 123_000L)
        viewModel.finishSetup(now = 123_000L)
        store.savedCurrentGameStates.clear()

        // Record an ordinary user-visible event through the same callback used by live UI actions.
        val livePointState = viewModel.liveState!!.beginLivePoint()
        viewModel.updateLiveGame(livePointState)
        assertEquals("Point is live.", store.savedCurrentGameStates.single().liveState!!.lastEvent)
        assertEquals(livePointState, store.savedCurrentGameStates.single().liveState)
        assertTrue(store.savedProfiles.isEmpty())
        assertTrue(store.savedSettings.isEmpty())
    }

    /**
     * Verify profile, settings, current-game, and no-op storage writes stay in their own
     * persistence buckets.
     */
    @Test
    fun independentPersistenceBuckets() {
        // Profile writes should not touch current-game or settings storage buckets.
        val store = RecordingAppStateStorage()
        val viewModel = AppViewModel(store)
        viewModel.updateProfileName("Casey Observer")
        assertEquals("Casey Observer", store.savedProfiles.single().profileName)
        assertTrue(store.savedCurrentGameStates.isEmpty())
        assertTrue(store.savedSettings.isEmpty())

        // Settings and current-game writes likewise stay in their own buckets.
        viewModel.updateAvatarPreference(ObserverAvatarPreference.BLUE)
        assertEquals(ObserverAvatarPreference.BLUE, store.savedProfiles.last().avatarPreference)
        assertTrue(store.savedCurrentGameStates.isEmpty())
        assertTrue(store.savedSettings.isEmpty())

        // Settings writes should not touch current-game or profile storage buckets.
        viewModel.updateTimingAlertGlobalMode(TimingAlertGlobalMode.OFF)
        assertEquals(
            TimingAlertGlobalMode.OFF,
            store.savedSettings.single().timingAlertPreferences.globalMode,
        )
        assertTrue(store.savedCurrentGameStates.isEmpty())
        assertEquals(2, store.savedProfiles.size)

        // Current-game writes should not touch profile or settings storage buckets.
        viewModel.startNewGame(now = 123_000L)
        assertTrue(store.savedCurrentGameStates.single().hasSetupDraft)
        assertEquals(2, store.savedProfiles.size)
        assertEquals(1, store.savedSettings.size)

        // The default no-op store should accept the same split-bucket writes without side effects.
        NoOpAppStateStorage.saveCurrentGameState(CurrentGameSnapshot())
        NoOpAppStateStorage.saveProfile(Profile())
        NoOpAppStateStorage.saveSettings(Settings())
        NoOpAppStateStorage.saveArchivedGames(emptyList())
    }

    /**
     * Verify archived games persist as pruned summaries with event logs, separate from
     * current-game, profile, and settings files.
     */
    @Test
    fun archivedSummaryPersistence() {
        // Use real file storage so archived and current-game files can be inspected separately.
        val storeDir = temporaryFolder.newFolder()
        val viewModel = AppViewModel(FileAppStateStorage(storeDir))

        // Complete and archive a game that still has live-only countdown and undo state.
        viewModel.startNewGame(now = 123_000L)
        viewModel.finishSetup(now = 123_000L)
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
            undoEntry = UndoEntry("Undo End game", beforeEndGame),
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

        // Restore from disk and verify the archived game keeps summary state and undo.
        val restored = AppViewModel(FileAppStateStorage(storeDir))
        assertEquals(AppScreen.HOME, restored.screen)
        assertNull(restored.liveState)
        assertEquals(1, restored.archivedGames.size)
        assertNull(restored.archivedGames.single().state.countdown)
        assertEquals("Undo End game", restored.archivedGames.single().state.undoEntry?.label)
        assertEquals(
            beforeEndGame.pruneUndoHistory(),
            restored.archivedGames.single().state.undoEntry!!.previous,
        )
        assertNull(restored.archivedGames.single().state.redoEntry)
        assertEquals(GamePhase.GAME_OVER, restored.archivedGames.single().state.phase)
        assertEquals(completedGame.eventLog, restored.archivedGames.single().state.eventLog)
    }

    /**
     * Verify file storage handles empty current/archive state, unsupported current-game
     * versions, archive cleanup, and nondirectory archive paths.
     */
    @Test
    fun filesystemStorageShapes() {
        // Empty storage loads no current game and no archived games.
        val storeDir = temporaryFolder.newFolder()
        val store = FileAppStateStorage(storeDir)
        assertNull(store.loadCurrentGameState())
        assertTrue(store.loadArchivedGames().isEmpty())

        // Current setup drafts load normally; unsupported versions recover to empty state.
        val setup = newGameSetupState(LocalDateTime.of(2026, 5, 11, 10, 0))
        val currentGameState = CurrentGameSnapshot(
            setupDraft = setup,
        )
        store.saveCurrentGameState(currentGameState)
        assertEquals(currentGameState, store.loadCurrentGameState())
        assertThrows(IllegalArgumentException::class.java) {
            CurrentGameSnapshot(
                setupDraft = setup,
                liveState = createLiveGameState(setup),
            )
        }

        // A same-persistence-version current game with a different Android version code
        // is still readable.
        val currentGameStateFile = File(storeDir, "current_game_state.json")
        store.saveCurrentGameState(currentGameState)
        currentGameStateFile.replaceText(
            "\"versionCode\": ${BuildConfig.VERSION_CODE}",
            "\"versionCode\": 99",
        )
        assertEquals(currentGameState, store.loadCurrentGameState())
        assertTrue(store.resetPersistedDataAreas.isEmpty())

        // Unsupported current-game persistence versions recover to setup-only state.
        store.saveCurrentGameState(currentGameState)
        currentGameStateFile.replaceText(
            "\"versionName\": \"${BuildConfig.VERSION_NAME}\"",
            "\"versionName\": \"99.0.0\"",
        )
        val recoveredState = store.loadCurrentGameState()!!
        assertNull(recoveredState.liveState)
        assertEquals(SetupMode.NEW_GAME, recoveredState.setupMode)
        assertEquals(setOf(PersistedData.GAME_STATE), store.resetPersistedDataAreas)

        // Invalid current-game version names follow the same reset path.
        store.saveCurrentGameState(currentGameState)
        currentGameStateFile.replaceText(
            "\"versionName\": \"${BuildConfig.VERSION_NAME}\"",
            "\"versionName\": \"not-a-version\"",
        )
        val invalidVersionRecoveredState = store.loadCurrentGameState()!!
        assertNull(invalidVersionRecoveredState.liveState)
        assertEquals(SetupMode.NEW_GAME, invalidVersionRecoveredState.setupMode)
        assertEquals(setOf(PersistedData.GAME_STATE), store.resetPersistedDataAreas)

        // Archived games load only JSON files and cleanup removes stale numbered archive files.
        val archivedOne = ArchivedGame(
            state = createLiveGameState(setup).copy(phase = GamePhase.GAME_OVER),
            summaryContext = "First",
        )
        val archivedTwo = archivedOne.copy(summaryContext = "Second")
        store.saveArchivedGames(listOf(archivedOne, archivedTwo))
        val archiveDir = File(storeDir, "archived_games")
        File(archiveDir, "not-json.txt").writeText("ignored")
        assertTrue(File(archiveDir, "directory.json").mkdir())
        assertEquals(listOf(archivedOne, archivedTwo), store.loadArchivedGames())

        // Saving fewer archives should remove stale numbered archive files.
        store.saveArchivedGames(listOf(archivedOne))
        assertEquals(listOf(archivedOne), store.loadArchivedGames())
        assertFalse(File(archiveDir, "00001.json").exists())

        // A nondirectory archive path loads as empty and is left untouched by empty saves.
        assertTrue(archiveDir.deleteRecursively())
        File(storeDir, "archived_games").writeText("not a directory")
        assertTrue(store.loadArchivedGames().isEmpty())
        store.saveArchivedGames(emptyList())
        assertTrue(File(storeDir, "archived_games").isFile)
    }

    /**
     * Verify corrupted split-state files reset only affected buckets, preserve readable
     * data, and clear recovery notices after repair.
     */
    @Test
    fun splitStateRecovery() {
        // A corrupt current-game file resets only current game.
        val storeDir = temporaryFolder.newFolder()
        val store = FileAppStateStorage(storeDir)
        val setup = newGameSetupState(LocalDateTime.of(2026, 5, 11, 10, 0))
        val liveState = createLiveGameState(setup).beginLivePoint()
        val timingPreferences = TimingAlertPreferences(
            globalMode = TimingAlertGlobalMode.VIBRATION_ONLY,
            soundVolume = 0.4f,
        )
        val savedCurrentGameState = CurrentGameSnapshot(
            liveState = liveState,
        )
        val savedArchive = ArchivedGame(
            state = createLiveGameState(setup).copy(phase = GamePhase.GAME_OVER),
            summaryContext = "Final",
        )
        store.saveCurrentGameState(savedCurrentGameState)
        store.saveProfile(Profile(profileName = "Casey Observer"))
        store.saveSettings(Settings(timingAlertPreferences = timingPreferences))

        // Corrupting the typed live-state field should reset only the current-game bucket.
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

        // App startup should preserve readable buckets and report the current-game reset.
        val recoveredViewModel = AppViewModel(FileAppStateStorage(storeDir))
        assertNull(recoveredViewModel.liveState)
        assertEquals("Casey Observer", recoveredViewModel.profileName)
        assertEquals(timingPreferences, recoveredViewModel.timingAlertPreferences)
        assertEquals(
            "Sorry, some phone data was corrupt, so UltiObserver had to revert to default " +
                "values for Current game.",
            recoveredViewModel.startupRecoveryNotice!!.message,
        )
        recoveredViewModel.dismissStartupRecoveryNotice()
        assertNull(recoveredViewModel.startupRecoveryNotice)

        // Other split files follow the same recovery path when their typed contents are corrupt.
        store.saveProfile(Profile(profileName = "Casey Observer"))
        File(storeDir, "profile.json").replaceText(
            "\"profileName\": \"Casey Observer\"",
            "\"profileName\": 7",
        )
        assertEquals(Profile(), store.loadProfile())
        store.saveSettings(
            Settings(timingAlertPreferences = TimingAlertPreferences(soundVolume = 0.35f))
        )
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
        val archivedOne = ArchivedGame(
            state = savedArchive.state,
            summaryContext = "First",
        )
        val archivedTwo = archivedOne.copy(summaryContext = "Second")
        archiveStore.saveArchivedGames(listOf(archivedOne, archivedTwo))
        File(File(archiveStoreDir, "archived_games"), "00000.json").writeText("{not-json")
        assertEquals(listOf(archivedTwo), archiveStore.loadArchivedGames())
        assertEquals(setOf(PersistedData.ARCHIVED_GAMES), archiveStore.resetPersistedDataAreas)

        // Startup should show the recovery notice until the archive files have been repaired.
        val archiveViewModel = AppViewModel(FileAppStateStorage(archiveStoreDir))
        assertEquals(listOf(archivedTwo), archiveViewModel.archivedGames)
        assertEquals(
            "Sorry, some phone data was corrupt, so UltiObserver had to revert to default " +
                "values for Archived games.",
            archiveViewModel.startupRecoveryNotice!!.message,
        )

        // Reloading after repair should not show the stale recovery notice again.
        val repairedArchiveStore = FileAppStateStorage(archiveStoreDir)
        assertEquals(listOf(archivedTwo), repairedArchiveStore.loadArchivedGames())
        assertTrue(repairedArchiveStore.resetPersistedDataAreas.isEmpty())
        val restoredAfterRecovery = AppViewModel(FileAppStateStorage(archiveStoreDir))
        assertEquals(listOf(archivedTwo), restoredAfterRecovery.archivedGames)
        assertNull(restoredAfterRecovery.startupRecoveryNotice)
    }

    /**
     * Verify app-version metadata is written, debug version names are accepted, and invalid
     * versions reset only affected persistence buckets.
     */
    @Test
    fun appVersionRecovery() {
        // Build representative saved records for every persisted app-data bucket.
        val storeDir = temporaryFolder.newFolder()
        val store = FileAppStateStorage(storeDir)
        val setup = newGameSetupState(LocalDateTime.of(2026, 5, 11, 10, 0))
        val savedCurrentGameState = CurrentGameSnapshot(
            liveState = createLiveGameState(setup),
        )
        val savedProfile = Profile(profileName = "Casey Observer")
        val savedSettings = Settings(
            timingAlertPreferences = TimingAlertPreferences(soundVolume = 0.35f),
        )
        val savedArchive = ArchivedGame(
            state = createLiveGameState(setup).copy(phase = GamePhase.GAME_OVER),
            summaryContext = "Final",
        )
        val debugVersionName = "${BuildConfig.VERSION_NAME}-debug"

        // Persistence-version helpers should accept release/debug names and reject invalid names.
        val persistedVersion = AppVersion(
            versionName = debugVersionName,
            versionCode = APP_STATE_VERSION_CODE,
        )
        assertEquals(debugVersionName, persistedVersion.versionName)
        assertEquals(APP_STATE_VERSION_CODE, persistedVersion.versionCode)
        assertEquals("1.1", currentPersistenceVersion("1.1.0alpha", 99))
        assertThrows(IllegalArgumentException::class.java) {
            currentPersistenceVersion("development-build", 99)
        }

        // Missing version names should reset each affected split-state area.
        store.saveProfile(savedProfile)
        File(storeDir, "profile.json").removeStoredAppVersion()
        assertEquals(Profile(), store.loadProfile())
        assertEquals(setOf(PersistedData.PROFILE), store.resetPersistedDataAreas)

        // Settings with missing version metadata should reset only Settings in addition.
        store.saveSettings(savedSettings)
        File(storeDir, "settings.json").removeStoredAppVersion()
        assertEquals(Settings(), store.loadSettings())
        assertEquals(
            setOf(PersistedData.PROFILE, PersistedData.SETTINGS),
            store.resetPersistedDataAreas,
        )

        // Archives with missing version metadata should reset only Archived games in addition.
        store.saveArchivedGames(listOf(savedArchive))
        File(File(storeDir, "archived_games"), "00000.json").removeStoredAppVersion()
        assertTrue(store.loadArchivedGames().isEmpty())
        assertEquals(
            setOf(PersistedData.PROFILE, PersistedData.SETTINGS, PersistedData.ARCHIVED_GAMES),
            store.resetPersistedDataAreas,
        )

        // Wrongly typed version fields and unsupported persistence versions reset narrowly.
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

        // A wrongly typed Profile version code should reset only Profile in addition.
        store.saveProfile(savedProfile)
        File(storeDir, "profile.json")
            .replaceText("\"versionCode\": ${BuildConfig.VERSION_CODE}", "\"versionCode\": \"bad\"")
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

        // A missing Profile version code follows the same narrow reset path.
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

        // A Profile with the same persistence version but a different revision number, and
        // thus a different Android version code, is still readable.
        store.saveProfile(savedProfile)
        File(storeDir, "profile.json").replaceText(
            "\"versionCode\": ${BuildConfig.VERSION_CODE}",
            "\"versionCode\": 99",
        )
        assertEquals(savedProfile, store.loadProfile())
        assertEquals(
            setOf(PersistedData.GAME_STATE, PersistedData.SETTINGS, PersistedData.ARCHIVED_GAMES),
            store.resetPersistedDataAreas,
        )

        // A later unsupported Profile persistence version should reset Profile.
        store.saveProfile(savedProfile)
        File(storeDir, "profile.json").replaceText(
            "\"versionName\": \"${BuildConfig.VERSION_NAME}\"",
            "\"versionName\": \"99.0.0\"",
        )
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

        // Settings with a wrongly typed version code should reset only Settings in addition.
        store.saveSettings(savedSettings)
        File(storeDir, "settings.json")
            .replaceText("\"versionCode\": ${BuildConfig.VERSION_CODE}", "\"versionCode\": \"bad\"")
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

        // Archives with a wrongly typed version code should reset only Archived games in addition.
        store.saveArchivedGames(listOf(savedArchive))
        File(File(storeDir, "archived_games"), "00000.json")
            .replaceText("\"versionCode\": ${BuildConfig.VERSION_CODE}", "\"versionCode\": \"bad\"")
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

        // Debug-style version names are accepted.
        store.saveProfile(savedProfile)
        File(storeDir, "profile.json")
            .replaceText(
                "\"versionName\": \"${BuildConfig.VERSION_NAME}\"",
                "\"versionName\": \"$debugVersionName\"",
            )
        assertEquals(savedProfile, store.loadProfile())
        assertEquals(
            setOf(PersistedData.GAME_STATE, PersistedData.SETTINGS, PersistedData.ARCHIVED_GAMES),
            store.resetPersistedDataAreas,
        )

        // A later unsupported Settings persistence version should reset Settings.
        store.saveSettings(savedSettings)
        File(storeDir, "settings.json").replaceText(
            "\"versionName\": \"${BuildConfig.VERSION_NAME}\"",
            "\"versionName\": \"99.0.0\"",
        )
        assertEquals(Settings(), store.loadSettings())
        assertEquals(
            setOf(
                PersistedData.GAME_STATE,
                PersistedData.ARCHIVED_GAMES,
                PersistedData.SETTINGS,
            ),
            store.resetPersistedDataAreas,
        )

        // Debug-style archive version names are accepted.
        store.saveArchivedGames(listOf(savedArchive))
        File(File(storeDir, "archived_games"), "00000.json")
            .replaceText(
                "\"versionName\": \"${BuildConfig.VERSION_NAME}\"",
                "\"versionName\": \"$debugVersionName\"",
            )
        assertEquals(
            listOf(savedArchive),
            store.loadArchivedGames(),
        )
        assertEquals(
            setOf(PersistedData.GAME_STATE, PersistedData.SETTINGS),
            store.resetPersistedDataAreas,
        )

        // A later unsupported archive persistence version should reset Archived games.
        store.saveArchivedGames(listOf(savedArchive))
        File(File(storeDir, "archived_games"), "00000.json").replaceText(
            "\"versionName\": \"${BuildConfig.VERSION_NAME}\"",
            "\"versionName\": \"99.0.0\"",
        )
        assertTrue(store.loadArchivedGames().isEmpty())
        assertEquals(
            setOf(PersistedData.GAME_STATE, PersistedData.SETTINGS, PersistedData.ARCHIVED_GAMES),
            store.resetPersistedDataAreas,
        )
    }

    /**
     * Verify malformed split-state JSON reports affected app-data areas, formats recovery
     * notices, and recovers unreadable paths.
     */
    @Test
    fun malformedSplitStateRecovery() {
        // Malformed split-state JSON reports the affected reset areas and restores defaults.
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
        assertEquals("Phone data reset", viewModel.startupRecoveryNotice!!.title)
        assertEquals(
            "Sorry, some phone data was corrupt, so UltiObserver had to revert to default " +
                "values for Current game, Profile, and Settings.",
            viewModel.startupRecoveryNotice!!.message,
        )

        // RecoveryNotice should format multi-area notices and reject empty notices.
        val twoAreaNotice = RecoveryNotice(
            setOf(PersistedData.PROFILE, PersistedData.SETTINGS)
        )
        assertEquals(
            "Sorry, some phone data was corrupt, so UltiObserver had to revert to default " +
                "values for Profile and Settings.",
            twoAreaNotice.message,
        )
        assertThrows(IllegalArgumentException::class.java) {
            RecoveryNotice(emptySet())
        }

        // Unreadable split-state paths recover the affected bucket to defaults.
        val unreadableStoreDir = temporaryFolder.newFolder()
        assertTrue(File(unreadableStoreDir, "profile.json").mkdir())
        val unreadableStore = FileAppStateStorage(unreadableStoreDir)
        assertEquals(Profile(), unreadableStore.loadProfile())
        assertEquals(setOf(PersistedData.PROFILE), unreadableStore.resetPersistedDataAreas)
    }

    /**
     * Verify failed current-game writes throw cleanly and do not leave stale temporary files
     * behind.
     */
    @Test
    fun failedWriteCleanup() {
        // Failed replacement cleans up the temporary current-game write file.
        val storeDir = temporaryFolder.newFolder()
        val currentGameStatePath = File(storeDir, "current_game_state.json")

        // Make the destination an undeletable non-empty directory so replacement fails.
        assertTrue(currentGameStatePath.mkdir())
        assertTrue(File(currentGameStatePath, "blocking-child").writeText("blocker").let { true })
        val store = FileAppStateStorage(storeDir)
        val setup = newGameSetupState(LocalDateTime.of(2026, 5, 11, 10, 0))

        // The failed save should throw and remove its temporary write file.
        assertThrows(IOException::class.java) {
            store.saveCurrentGameState(
                CurrentGameSnapshot(
                    setupDraft = setup,
                )
            )
        }
        assertFalse(File(storeDir, ".current_game_state.json.tmp").exists())
    }

    /**
     * Verify the non-atomic replace fallback cleans temporary files and still writes
     * readable state.
     */
    @Test
    fun nonAtomicReplaceFallback() {
        // Configure storage so every atomic move attempt takes the fallback path.
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
            setupDraft = setup,
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
