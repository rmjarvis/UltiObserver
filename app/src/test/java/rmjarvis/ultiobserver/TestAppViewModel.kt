package rmjarvis.ultiobserver

import java.time.LocalTime
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Tests for app-level navigation, setup lifecycle, settings, and other non-archive
 * state owned by AppViewModel.
 */
class TestAppViewModel : GameDomainTestFixtures() {
    /**
     * Verify AppViewModel's main lifecycle from empty Home, through setup and live play,
     * into setup editing and starting over.
     */
    @Test
    fun topLevelGameFlow() {
        // Start from a clean Home state with no current or archived game.
        val viewModel = AppViewModel(NoOpAppStateStorage)
        assertEquals(AppScreen.HOME, viewModel.screen)
        assertEquals(AppScreen.HOME, viewModel.state.value.screen)
        assertNull(viewModel.currentGame)
        assertTrue(viewModel.archivedGames.isEmpty())
        assertNull(viewModel.currentGameHomeSubtitle)
        assertEquals(SetupMode.NEW_GAME, viewModel.setupMode)
        assertThrows(IllegalStateException::class.java) {
            viewModel.setupGame
        }

        // Create a setup draft and verify Home can advertise it as resumable.
        viewModel.startNewGame(now = 123_000L)
        assertEquals(AppScreen.SETUP, viewModel.screen)
        assertTrue(viewModel.hasSetupDraft)
        assertEquals("Tap to resume", viewModel.currentGameHomeSubtitle)

        // Finish setup with named teams and verify the current game is created from that draft.
        val namedSetup = viewModel.setupGame.copy(
            teamOne = TeamState("Alpha", TeamColorChoice.BLUE),
            teamTwo = TeamState("Beta", TeamColorChoice.PINK),
        )
        viewModel.updateSetup(namedSetup)
        viewModel.finishSetup(now = 123_000L)
        assertFalse(viewModel.hasSetupDraft)
        val startedGame = viewModel.currentGame
        assertNotNull(startedGame)
        assertEquals(AppScreen.LIVE, viewModel.screen)
        assertEquals("Tap to resume", viewModel.currentGameHomeSubtitle)
        assertEquals("Alpha", startedGame!!.teamOne.name)
        assertEquals("Beta", startedGame.teamTwo.name)

        // Current-game updates should keep Home resume state and store the latest score.
        viewModel.updateCurrentGame(startedGame.beginLivePoint())
        assertEquals("Tap to resume", viewModel.currentGameHomeSubtitle)
        val adjustedGame = viewModel.currentGame!!.adjustScore(teamOneScore = 2, teamTwoScore = 1)
        viewModel.updateCurrentGame(adjustedGame)
        assertEquals(2, viewModel.currentGame!!.teamOne.score)

        // Reopen setup from the current game and verify setup edits preserve live score state.
        viewModel.editCurrentGame(viewModel.currentGame!!)
        assertEquals(AppScreen.SETUP, viewModel.screen)
        assertEquals(SetupMode.EDIT_CURRENT_GAME, viewModel.setupMode)
        viewModel.updateSetup(
            viewModel.setupGame.copy(
                teamOne = viewModel.setupGame.teamOne.copy(name = "Alpha Prime"),
            )
        )
        assertFalse(viewModel.hasSetupDraft)
        viewModel.finishSetup(now = 123_000L)
        assertEquals(AppScreen.LIVE, viewModel.screen)
        assertEquals("Alpha Prime", viewModel.currentGame!!.teamOne.name)
        assertEquals(2, viewModel.currentGame!!.teamOne.score)

        // Starting over should archive the old current game and create a fresh setup draft.
        val currentGameBeforeStartingOver = viewModel.currentGame!!
        viewModel.goHome()
        assertEquals(AppScreen.HOME, viewModel.screen)
        viewModel.startNewGame(now = 123_000L)
        assertEquals(AppScreen.SETUP, viewModel.screen)
        assertTrue(viewModel.hasSetupDraft)
        assertEquals(1, viewModel.archivedGames.size)
        val archivedGame = viewModel.archivedGames.single()
        assertEquals(ArchivedGameCategory.IN_PROGRESS, archivedGame.archiveCategory)
        assertEquals(currentGameBeforeStartingOver, archivedGame)
        assertNull(archivedGame.endEpoch)
        assertEquals(GamePhase.SETUP, viewModel.currentGame?.phase)
        assertEquals("Tap to resume", viewModel.currentGameHomeSubtitle)
    }

    /**
     * Verify setup drafts remain editable until the first live point starts, including
     * Home resume, Back from the pre-pull preview, setup editing, and starting over.
     */
    @Test
    fun setupDraftResume() {
        // Create a blank-name setup draft and verify Home resumes it as setup, not live play.
        val viewModel = AppViewModel(NoOpAppStateStorage)
        viewModel.startNewGame(now = 123_000L)
        val draftedSetup = viewModel.setupGame.copy(
            teamOne = TeamState("", TeamColorChoice.GREEN),
            teamTwo = TeamState("", TeamColorChoice.YELLOW),
        )
        viewModel.updateSetup(draftedSetup)
        viewModel.goHome()
        assertEquals(AppScreen.HOME, viewModel.screen)
        assertTrue(viewModel.hasSetupDraft)
        assertEquals(GamePhase.SETUP, viewModel.currentGame?.phase)

        // Resuming from Home should reopen the setup draft, not live play.
        viewModel.resumeCurrentGame()
        assertEquals(AppScreen.SETUP, viewModel.screen)
        assertEquals(draftedSetup, viewModel.setupGame)

        // Backing out from the pre-pull preview should restore the original editable draft.
        viewModel.finishSetup(now = 123_000L)
        assertEquals(AppScreen.LIVE, viewModel.screen)
        assertFalse(viewModel.hasSetupDraft)
        viewModel.goBackFromCurrentScreen()
        assertEquals(AppScreen.SETUP, viewModel.screen)
        assertTrue(viewModel.hasSetupDraft)
        assertEquals(GamePhase.SETUP, viewModel.currentGame?.phase)
        assertEquals("", viewModel.setupGame.teamOne.name)
        assertEquals("", viewModel.setupGame.teamTwo.name)

        // Backing out from a new setup draft returns Home while keeping the draft resumable.
        val newSetupBackViewModel = AppViewModel(NoOpAppStateStorage)
        newSetupBackViewModel.startNewGame(now = 123_000L)
        newSetupBackViewModel.goBackFromCurrentScreen()
        assertEquals(AppScreen.HOME, newSetupBackViewModel.screen)
        assertTrue(newSetupBackViewModel.hasSetupDraft)
        assertEquals(GamePhase.SETUP, newSetupBackViewModel.currentGame?.phase)

        // Home resume and Update game setup should both still treat the pre-pull game as a draft.
        // Note -- once the game is started, the users can't easily get back to home without
        // going back to the setup screen first.  But if they closed the app and reopened it, they
        // would land in Home.  Then clicking the current game and then back would take them
        // to the setup page.
        viewModel.finishSetup(now = 123_000L)
        val livePreview = viewModel.currentGame!!
        viewModel.goHome()
        viewModel.resumeCurrentGame()
        assertEquals(AppScreen.LIVE, viewModel.screen)
        viewModel.goBackFromCurrentScreen()
        assertEquals(AppScreen.SETUP, viewModel.screen)
        assertTrue(viewModel.hasSetupDraft)
        assertEquals(GamePhase.SETUP, viewModel.currentGame?.phase)
        assertEquals("", viewModel.setupGame.teamOne.name)
        assertEquals("", viewModel.setupGame.teamTwo.name)

        // Update game setup should also treat the pre-pull game as a draft.
        viewModel.finishSetup(now = 123_000L)
        viewModel.editCurrentGame(viewModel.currentGame!!)
        assertEquals(AppScreen.SETUP, viewModel.screen)
        assertTrue(viewModel.hasSetupDraft)
        assertEquals(GamePhase.SETUP, viewModel.currentGame?.phase)
        assertEquals("", viewModel.setupGame.teamOne.name)
        assertEquals("", viewModel.setupGame.teamTwo.name)

        // Once a real point starts, Home should resume the in-progress game instead of setup.
        viewModel.finishSetup(now = 123_000L)
        viewModel.updateCurrentGame(viewModel.currentGame!!.beginLivePoint())
        viewModel.goHome()
        viewModel.resumeCurrentGame()
        assertEquals(AppScreen.LIVE, viewModel.screen)
        assertEquals(GamePhase.LIVE_POINT, viewModel.currentGame!!.phase)
        assertFalse(viewModel.hasSetupDraft)
        assertEquals(livePreview.teamOne.name, viewModel.currentGame!!.teamOne.name)

        // Starting over from an unstarted setup draft should save the old draft aside.
        val setupDraftViewModel = AppViewModel(NoOpAppStateStorage)
        setupDraftViewModel.startNewGame(now = 123_000L)
        setupDraftViewModel.updateSetup(
            setupDraftViewModel.setupGame.copy(
                teamOne = TeamState("Saved setup", TeamColorChoice.WHITE),
            )
        )
        val savedSetupDraft = setupDraftViewModel.setupGame
        setupDraftViewModel.startNewGame(now = 123_000L)
        assertEquals(AppScreen.SETUP, setupDraftViewModel.screen)
        assertTrue(setupDraftViewModel.hasSetupDraft)
        assertEquals(1, setupDraftViewModel.archivedGames.size)
        assertEquals(
            ArchivedGameCategory.SETUP,
            setupDraftViewModel.archivedGames.single().archiveCategory,
        )
        assertEquals(savedSetupDraft, setupDraftViewModel.archivedGames.single())
        assertEquals("", setupDraftViewModel.setupGame.teamOne.name)

        // Starting over before the first real point should save the pre-pull preview aside.
        val prePullViewModel = AppViewModel(NoOpAppStateStorage)
        prePullViewModel.startNewGame(now = 123_000L)
        prePullViewModel.finishSetup(now = 123_000L)
        val prePullPreview = prePullViewModel.currentGame!!
        prePullViewModel.goHome()
        prePullViewModel.startNewGame(now = 123_000L)
        assertEquals(AppScreen.SETUP, prePullViewModel.screen)
        assertTrue(prePullViewModel.hasSetupDraft)
        assertEquals(GamePhase.SETUP, prePullViewModel.currentGame?.phase)
        assertEquals(1, prePullViewModel.archivedGames.size)
        assertEquals(
            ArchivedGameCategory.IN_PROGRESS,
            prePullViewModel.archivedGames.single().archiveCategory,
        )
        assertEquals(prePullPreview, prePullViewModel.archivedGames.single())

        // Undo-backed setup edits before the opening pull are also preserved when starting over.
        val setupOnlyViewModel = AppViewModel(NoOpAppStateStorage)
        setupOnlyViewModel.startNewGame(now = 123_000L)
        setupOnlyViewModel.finishSetup(now = 123_000L)
        val setupOnlyPreview = setupOnlyViewModel.currentGame!!
        setupOnlyViewModel.updateCurrentGame(
            applySetupToLiveGame(
                setupOnlyPreview,
                setupOnlyPreview.copy(
                    teamOne = TeamState("Edited", TeamColorChoice.WHITE),
                ),
                10_000L,
            )
        )
        setupOnlyViewModel.goHome()
        val setupOnlyEditedPreview = setupOnlyViewModel.currentGame!!
        setupOnlyViewModel.startNewGame(now = 123_000L)
        assertEquals(AppScreen.SETUP, setupOnlyViewModel.screen)
        assertEquals(GamePhase.SETUP, setupOnlyViewModel.currentGame?.phase)
        assertEquals(1, setupOnlyViewModel.archivedGames.size)
        assertEquals(
            ArchivedGameCategory.IN_PROGRESS,
            setupOnlyViewModel.archivedGames.single().archiveCategory,
        )
        assertEquals(setupOnlyEditedPreview, setupOnlyViewModel.archivedGames.single())

        // A logged event before the opening pull is preserved the same way.
        val prePullEventViewModel = AppViewModel(NoOpAppStateStorage)
        prePullEventViewModel.startNewGame(now = 123_000L)
        prePullEventViewModel.finishSetup(now = 123_000L)
        val prePullEventState = prePullEventViewModel.currentGame!!
        val prePullEventUpdatedState = prePullEventState.assessTimeout(
            TeamId.TEAM_ONE,
            prePullEventState.countdown!!.targetEpoch - 1_000L,
        )
        prePullEventViewModel.updateCurrentGame(prePullEventUpdatedState.state)
        prePullEventViewModel.goHome()
        prePullEventViewModel.startNewGame(now = 123_000L)
        assertEquals(AppScreen.SETUP, prePullEventViewModel.screen)
        assertEquals(GamePhase.SETUP, prePullEventViewModel.currentGame?.phase)
        assertEquals(1, prePullEventViewModel.archivedGames.size)
        assertEquals(
            ArchivedGameCategory.IN_PROGRESS,
            prePullEventViewModel.archivedGames.single().archiveCategory,
        )
        assertEquals(prePullEventUpdatedState.state, prePullEventViewModel.archivedGames.single())
    }

    /**
     * Verify resuming a scored current game and editing setup applies setup changes while
     * preserving current score state.
     */
    @Test
    fun currentGameSetupEdit() {
        // Resume a scored current game from Home, edit setup, and keep the live score.
        val viewModel = AppViewModel(NoOpAppStateStorage)
        viewModel.startNewGame(now = 123_000L)
        viewModel.finishSetup(now = 123_000L)
        val scoredGame = viewModel.currentGame!!.adjustScore(teamOneScore = 3, teamTwoScore = 2)
        viewModel.updateCurrentGame(scoredGame)

        // Home should retain the current game until it is resumed.
        viewModel.goHome()
        assertEquals(AppScreen.HOME, viewModel.screen)
        assertEquals(scoredGame, viewModel.currentGame)

        // Resuming and editing setup should keep score while applying setup changes.
        viewModel.resumeCurrentGame()
        assertEquals(AppScreen.LIVE, viewModel.screen)
        assertEquals(scoredGame, viewModel.displayedGame)
        viewModel.editCurrentGame(viewModel.displayedGame!!)
        assertEquals(AppScreen.SETUP, viewModel.screen)
        assertEquals(SetupMode.EDIT_CURRENT_GAME, viewModel.setupMode)
        viewModel.goBackFromCurrentScreen()
        assertEquals(AppScreen.LIVE, viewModel.screen)
        assertNull(viewModel.setupEditDraft)
        viewModel.editCurrentGame(viewModel.currentGame!!)
        assertEquals(AppScreen.SETUP, viewModel.screen)
        viewModel.updateSetup(
            viewModel.setupGame.copy(
                rules = viewModel.setupGame.rules.copy(gameTo = 17),
            )
        )
        viewModel.finishSetup(now = 123_000L)
        assertEquals(AppScreen.LIVE, viewModel.screen)
        assertEquals(17, viewModel.currentGame!!.rules.gameTo)
        assertEquals(3, viewModel.currentGame!!.teamOne.score)
        assertEquals(2, viewModel.currentGame!!.teamTwo.score)
    }

    /**
     * Verify unavailable navigation and game actions leave existing Home, setup, and live
     * game state alone.
     */
    @Test
    fun unavailableActions() {
        // Empty-home actions should be harmless when there is no current or completed game.
        val viewModel = AppViewModel(NoOpAppStateStorage)
        viewModel.resumeCurrentGame()
        assertEquals(AppScreen.HOME, viewModel.screen)
        assertNull(viewModel.currentGame)
        viewModel.openCompletedGame()
        assertEquals(AppScreen.HOME, viewModel.screen)
        assertNull(viewModel.currentGame)
        assertThrows(IndexOutOfBoundsException::class.java) {
            viewModel.openArchivedGame(0, now = 123_000L)
        }
        assertEquals(AppScreen.HOME, viewModel.screen)
        assertNull(viewModel.displayedGame)
        viewModel.archiveCompletedGame()
        assertTrue(viewModel.archivedGames.isEmpty())

        // A synthetic transient live screen without a current game should back out to Home.
        // I'm not sure if this state is possible with race conditions in the app, so this is
        // a defensive check.  If there is no currentGame and somehow we are in the live screen,
        // it probably looks weird and might already have crashed, but if not, then back
        // will take use to the safety of the HOME screen.
        viewModel.forceUiState(
            viewModel.state.value.copy(
                screen = AppScreen.LIVE,
                currentGame = null,
                viewingArchivedGame = null,
            )
        )
        viewModel.goBackFromCurrentScreen()
        assertEquals(AppScreen.HOME, viewModel.screen)
        assertNull(viewModel.currentGame)

        // Active-game-only state should reject completed-game and archive actions.
        viewModel.startNewGame(now = 123_000L)
        viewModel.finishSetup(now = 123_000L)
        val activeGame = viewModel.currentGame!!
        viewModel.goHome()
        viewModel.openCompletedGame()
        assertEquals(AppScreen.HOME, viewModel.screen)
        assertEquals(activeGame, viewModel.currentGame)
        viewModel.archiveCompletedGame()
        assertTrue(viewModel.archivedGames.isEmpty())
        assertEquals(activeGame, viewModel.currentGame)

        // A completed current game stays on Home until opened through the completed-game path.
        val completedGame = activeGame.copy(phase = GamePhase.GAME_OVER)
        viewModel.updateCurrentGame(completedGame)
        viewModel.resumeCurrentGame()
        assertEquals(AppScreen.HOME, viewModel.screen)
        assertEquals(completedGame, viewModel.currentGame)

        // Non-game screens return Home.
        viewModel.openProfile()
        viewModel.goBackFromCurrentScreen()
        assertEquals(AppScreen.HOME, viewModel.screen)

        // Back navigation from a resumed live game should return Home.
        viewModel.updateCurrentGame(activeGame.beginLivePoint())
        viewModel.resumeCurrentGame()
        assertEquals(AppScreen.LIVE, viewModel.screen)
        viewModel.goBackFromCurrentScreen()
        assertEquals(AppScreen.HOME, viewModel.screen)
    }

    /**
     * Verify the observer profile name seeds new setup drafts as the first observer.
     */
    @Test
    fun profileNameSeedsNewGameObserver() {
        // New game setup should start with the profile name as the first observer, since the
        // observer using the phone will usually work their own game.
        val viewModel = AppViewModel(NoOpAppStateStorage)
        viewModel.updateProfile(viewModel.profile.withName(" Casey Observer "))
        viewModel.startNewGame(now = 123_000L)
        assertEquals(listOf("Casey Observer"), viewModel.setupGame.observerNames)

        // Blank or whitespace profile names should not create an empty observer entry.
        val blankProfileViewModel = AppViewModel(NoOpAppStateStorage)
        blankProfileViewModel.updateProfile(blankProfileViewModel.profile.withName("   "))
        blankProfileViewModel.startNewGame(now = 123_000L)
        assertEquals(emptyList<String>(), blankProfileViewModel.setupGame.observerNames)
    }

    /**
     * Verify restoring timing cue defaults resets cue-level preferences while preserving
     * global sound and vibration settings.
     */
    @Test
    fun timingCueDefaults() {
        // Reset cue-level timing settings while preserving global sound/vibration preferences.
        val viewModel = AppViewModel(NoOpAppStateStorage)
        fun updateSettings(transform: (Settings) -> Settings) {
            viewModel.updateSettings(transform(viewModel.settings))
        }
        fun updateTimingAlerts(transform: (TimingAlertPreferences) -> TimingAlertPreferences) {
            updateSettings { it.withTimingAlerts(transform(it.timingAlerts)) }
        }
        updateTimingAlerts { it.withGlobalMode(TimingAlertGlobalMode.SOUNDS_ON) }
        updateTimingAlerts { it.withSoundVolume(0.4f) }
        updateTimingAlerts { it.withVibrationDuration(420L) }
        updateTimingAlerts { it.withVibrateWithSounds(true) }
        updateTimingAlerts {
            it.withCueMode(TimingCueId.RECEIVING_TWENTY_FOR_HAND, TimingAlertMode.NONE)
        }
        updateTimingAlerts { it.withCueRepeatCount(TimingCueId.RECEIVING_TWENTY_FOR_HAND, 3) }
        updateTimingAlerts { it.withCueMode(TimingCueId.HARD_CAP, TimingAlertMode.BEEP) }
        updateTimingAlerts { it.withCueRepeatCount(TimingCueId.HARD_CAP, 1) }
        updateTimingAlerts { it.withDefaultCueSettings() }
        assertEquals(TimingAlertGlobalMode.SOUNDS_ON, viewModel.settings.timingAlerts.globalMode)
        assertEquals(0.4f, viewModel.settings.timingAlerts.soundVolume, 0f)
        assertEquals(420L, viewModel.settings.timingAlerts.vibrationDurationMillis)
        assertTrue(viewModel.settings.timingAlerts.vibrateWithSounds)
        assertEquals(
            TimingAlertMode.TICK,
            viewModel.settings.timingAlerts.settingsModeFor(TimingCueId.RECEIVING_TWENTY_FOR_HAND),
        )
        assertEquals(
            2,
            viewModel.settings.timingAlerts.repeatCountFor(TimingCueId.RECEIVING_TWENTY_FOR_HAND),
        )
        assertEquals(
            TimingAlertMode.DING,
            viewModel.settings.timingAlerts.settingsModeFor(TimingCueId.HARD_CAP),
        )
        assertEquals(3, viewModel.settings.timingAlerts.repeatCountFor(TimingCueId.HARD_CAP))
    }

    /**
     * Force a ViewModel state that has no public setup path.
     *
     * This is reserved for defensive navigation tests where the state may only be reachable as a
     * transient race during UI teardown.
     *
     * @param state The synthetic UI state to install.
     */
    @Suppress("UNCHECKED_CAST")
    private fun AppViewModel.forceUiState(state: AppUiState) {
        val stateField = AppViewModel::class.java.getDeclaredField("_state")
        stateField.isAccessible = true
        val mutableState = stateField.get(this) as MutableStateFlow<AppUiState>
        mutableState.value = state
    }
}
