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
        assertNull(viewModel.liveState)
        assertTrue(viewModel.archivedGames.isEmpty())
        assertNull(viewModel.currentGameHomeSubtitle)
        assertEquals(SetupMode.NEW_GAME, viewModel.setupMode)
        assertThrows(IllegalStateException::class.java) {
            viewModel.setupState
        }

        // Create a setup draft and verify Home can advertise it as resumable.
        viewModel.startNewGame(now = 123_000L)
        assertEquals(AppScreen.SETUP, viewModel.screen)
        assertTrue(viewModel.hasSetupDraft)
        assertEquals("Tap to resume", viewModel.currentGameHomeSubtitle)

        // Finish setup with named teams and verify the live game is created from that draft.
        val namedSetup = viewModel.setupState.copy(
            teamOne = TeamState("Alpha", TeamColorChoice.BLUE),
            teamTwo = TeamState("Beta", TeamColorChoice.PINK),
        )
        viewModel.updateSetup(namedSetup)
        viewModel.finishSetup(now = 123_000L)
        assertFalse(viewModel.hasSetupDraft)
        val startedGame = viewModel.liveState
        assertNotNull(startedGame)
        assertEquals(AppScreen.LIVE, viewModel.screen)
        assertEquals("Tap to resume", viewModel.currentGameHomeSubtitle)
        assertEquals("Alpha", startedGame!!.teamOne.name)
        assertEquals("Beta", startedGame.teamTwo.name)

        // Live-game updates should keep Home resume state and store the latest score.
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
        viewModel.finishSetup(now = 123_000L)
        assertEquals(AppScreen.LIVE, viewModel.screen)
        assertEquals("Alpha Prime", viewModel.liveState!!.teamOne.name)
        assertEquals(2, viewModel.liveState!!.teamOne.score)

        // Starting over should archive the old current game and create a fresh setup draft.
        val currentGameBeforeStartingOver = viewModel.liveState!!
        viewModel.goHome()
        assertEquals(AppScreen.HOME, viewModel.screen)
        viewModel.startNewGame(now = 123_000L)
        assertEquals(AppScreen.SETUP, viewModel.screen)
        assertTrue(viewModel.hasSetupDraft)
        assertEquals(1, viewModel.archivedGames.size)
        val archivedGame = viewModel.archivedGames.single()
        assertEquals(
            savedWhenNewGameStartedContext(currentGameBeforeStartingOver, 123_000L),
            archivedGame.summaryContext,
        )
        assertEquals(currentGameBeforeStartingOver, archivedGame.state)
        assertNull(archivedGame.state.endEpoch)
        assertNull(viewModel.liveState)
        assertEquals("Tap to resume", viewModel.currentGameHomeSubtitle)
    }

    /**
     * Verify setup drafts remain editable until the first live point starts, including
     * Home resume, Back from the initial preview, setup editing, and starting over.
     */
    @Test
    fun setupDraftResume() {
        // Create a blank-name setup draft and verify Home resumes it as setup, not live play.
        val viewModel = AppViewModel(NoOpAppStateStorage)
        viewModel.startNewGame(now = 123_000L)
        val draftedSetup = viewModel.setupState.copy(
            teamOne = TeamState("", TeamColorChoice.GREEN),
            teamTwo = TeamState("", TeamColorChoice.YELLOW),
        )
        viewModel.updateSetup(draftedSetup)
        viewModel.goHome()
        assertEquals(AppScreen.HOME, viewModel.screen)
        assertTrue(viewModel.hasSetupDraft)
        assertNull(viewModel.liveState)

        // Resuming from Home should reopen the setup draft, not live play.
        viewModel.resumeCurrentGame()
        assertEquals(AppScreen.SETUP, viewModel.screen)
        assertEquals(draftedSetup, viewModel.setupState)

        // Backing out from the pre-pull live preview should restore the original editable draft.
        viewModel.finishSetup(now = 123_000L)
        assertEquals(AppScreen.LIVE, viewModel.screen)
        assertFalse(viewModel.hasSetupDraft)
        viewModel.goBackFromCurrentScreen()
        assertEquals(AppScreen.SETUP, viewModel.screen)
        assertTrue(viewModel.hasSetupDraft)
        assertNull(viewModel.liveState)
        assertEquals("", viewModel.setupState.teamOne.name)
        assertEquals("", viewModel.setupState.teamTwo.name)

        // Backing out from a new setup draft returns Home while keeping the draft resumable.
        val newSetupBackViewModel = AppViewModel(NoOpAppStateStorage)
        newSetupBackViewModel.startNewGame(now = 123_000L)
        newSetupBackViewModel.goBackFromCurrentScreen()
        assertEquals(AppScreen.HOME, newSetupBackViewModel.screen)
        assertTrue(newSetupBackViewModel.hasSetupDraft)
        assertNull(newSetupBackViewModel.liveState)

        // Home resume and Update game setup should both still treat the pre-pull game as a draft.
        // Note -- once the game is started, the users can't easily get back to home without
        // going back to the setup screen first.  But if they closed the app and reopened it, they
        // would land in Home.  Then clicking the current game and then back would take them
        // to the setup page.
        viewModel.finishSetup(now = 123_000L)
        val livePreview = viewModel.liveState!!
        viewModel.goHome()
        viewModel.resumeCurrentGame()
        assertEquals(AppScreen.LIVE, viewModel.screen)
        viewModel.goBackFromCurrentScreen()
        assertEquals(AppScreen.SETUP, viewModel.screen)
        assertTrue(viewModel.hasSetupDraft)
        assertNull(viewModel.liveState)
        assertEquals("", viewModel.setupState.teamOne.name)
        assertEquals("", viewModel.setupState.teamTwo.name)

        // Update game setup should also treat the pre-pull game as a draft.
        viewModel.finishSetup(now = 123_000L)
        viewModel.editCurrentGame(viewModel.liveState!!)
        assertEquals(AppScreen.SETUP, viewModel.screen)
        assertTrue(viewModel.hasSetupDraft)
        assertNull(viewModel.liveState)
        assertEquals("", viewModel.setupState.teamOne.name)
        assertEquals("", viewModel.setupState.teamTwo.name)

        // Once a real point starts, Home should resume the active live game instead of setup.
        viewModel.finishSetup(now = 123_000L)
        viewModel.updateLiveGame(viewModel.liveState!!.beginLivePoint())
        viewModel.goHome()
        viewModel.resumeCurrentGame()
        assertEquals(AppScreen.LIVE, viewModel.screen)
        assertEquals(GamePhase.LIVE_POINT, viewModel.liveState!!.phase)
        assertFalse(viewModel.hasSetupDraft)
        assertEquals(livePreview.teamOne.name, viewModel.liveState!!.teamOne.name)

        // Starting over from an unstarted setup draft should replace it without archiving it.
        val setupDraftViewModel = AppViewModel(NoOpAppStateStorage)
        setupDraftViewModel.startNewGame(now = 123_000L)
        setupDraftViewModel.updateSetup(
            setupDraftViewModel.setupState.copy(
                teamOne = TeamState("Discarded setup", TeamColorChoice.WHITE),
            )
        )
        setupDraftViewModel.startNewGame(now = 123_000L)
        assertEquals(AppScreen.SETUP, setupDraftViewModel.screen)
        assertTrue(setupDraftViewModel.hasSetupDraft)
        assertTrue(setupDraftViewModel.archivedGames.isEmpty())
        assertEquals("", setupDraftViewModel.setupState.teamOne.name)

        // Starting over before the first real point should discard setup-only state.
        val prePullViewModel = AppViewModel(NoOpAppStateStorage)
        prePullViewModel.startNewGame(now = 123_000L)
        prePullViewModel.finishSetup(now = 123_000L)
        prePullViewModel.goHome()
        prePullViewModel.startNewGame(now = 123_000L)
        assertEquals(AppScreen.SETUP, prePullViewModel.screen)
        assertTrue(prePullViewModel.hasSetupDraft)
        assertNull(prePullViewModel.liveState)
        assertTrue(prePullViewModel.archivedGames.isEmpty())

        // Undo-backed setup edits before the opening pull are still setup-only, so starting over
        // should discard them rather than archive them.
        val setupOnlyViewModel = AppViewModel(NoOpAppStateStorage)
        setupOnlyViewModel.startNewGame(now = 123_000L)
        setupOnlyViewModel.finishSetup(now = 123_000L)
        val setupOnlyPreview = setupOnlyViewModel.liveState!!
        setupOnlyViewModel.updateLiveGame(
            applySetupToLiveGame(
                setupOnlyPreview,
                setupOnlyPreview.copy(
                    teamOne = TeamState("Edited", TeamColorChoice.WHITE),
                ),
                10_000L,
            )
        )
        setupOnlyViewModel.goHome()
        setupOnlyViewModel.startNewGame(now = 123_000L)
        assertEquals(AppScreen.SETUP, setupOnlyViewModel.screen)
        assertNull(setupOnlyViewModel.liveState)
        assertTrue(setupOnlyViewModel.archivedGames.isEmpty())

        // A logged event before the opening pull means the current game is real enough to archive.
        val prePullEventViewModel = AppViewModel(NoOpAppStateStorage)
        prePullEventViewModel.startNewGame(now = 123_000L)
        prePullEventViewModel.finishSetup(now = 123_000L)
        val prePullEventState = prePullEventViewModel.liveState!!
        prePullEventViewModel.updateLiveGame(
            prePullEventState.assessTimeout(
                TeamId.TEAM_ONE,
                prePullEventState.countdown!!.targetEpoch - 1_000L,
            ).state
        )
        prePullEventViewModel.goHome()
        prePullEventViewModel.startNewGame(now = 123_000L)
        assertEquals(AppScreen.SETUP, prePullEventViewModel.screen)
        assertNull(prePullEventViewModel.liveState)
        assertEquals(1, prePullEventViewModel.archivedGames.size)
    }

    /**
     * Verify resuming a scored current game and editing setup applies setup changes while
     * preserving live score state.
     */
    @Test
    fun currentGameSetupEdit() {
        // Resume a scored current game from Home, edit setup, and keep the live score.
        val viewModel = AppViewModel(NoOpAppStateStorage)
        viewModel.startNewGame(now = 123_000L)
        viewModel.finishSetup(now = 123_000L)
        val scoredGame = viewModel.liveState!!.adjustScore(teamOneScore = 3, teamTwoScore = 2)
        viewModel.updateLiveGame(scoredGame)

        // Home should retain the current live game until it is resumed.
        viewModel.goHome()
        assertEquals(AppScreen.HOME, viewModel.screen)
        assertEquals(scoredGame, viewModel.liveState)

        // Resuming and editing setup should keep score while applying setup changes.
        viewModel.resumeCurrentGame()
        assertEquals(AppScreen.LIVE, viewModel.screen)
        assertEquals(scoredGame, viewModel.currentLiveState)
        viewModel.editCurrentGame(viewModel.currentLiveState!!)
        assertEquals(AppScreen.SETUP, viewModel.screen)
        assertEquals(SetupMode.EDIT_CURRENT_GAME, viewModel.setupMode)
        viewModel.goBackFromCurrentScreen()
        assertEquals(AppScreen.LIVE, viewModel.screen)
        assertNull(viewModel.setupEditDraft)
        viewModel.editCurrentGame(viewModel.liveState!!)
        assertEquals(AppScreen.SETUP, viewModel.screen)
        viewModel.updateSetup(
            viewModel.setupState.copy(
                rules = viewModel.setupState.rules.copy(gameTo = 17),
            )
        )
        viewModel.finishSetup(now = 123_000L)
        assertEquals(AppScreen.LIVE, viewModel.screen)
        assertEquals(17, viewModel.liveState!!.rules.gameTo)
        assertEquals(3, viewModel.liveState!!.teamOne.score)
        assertEquals(2, viewModel.liveState!!.teamTwo.score)
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
        assertNull(viewModel.liveState)
        viewModel.openCompletedGame()
        assertEquals(AppScreen.HOME, viewModel.screen)
        assertNull(viewModel.liveState)
        assertThrows(IndexOutOfBoundsException::class.java) {
            viewModel.openArchivedGame(0, now = 123_000L)
        }
        assertEquals(AppScreen.HOME, viewModel.screen)
        assertNull(viewModel.currentLiveState)
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

        // Non-game screens return Home.
        viewModel.openProfile()
        viewModel.goBackFromCurrentScreen()
        assertEquals(AppScreen.HOME, viewModel.screen)

        // Back navigation from a resumed live game should return Home.
        viewModel.updateLiveGame(activeGame.beginLivePoint())
        viewModel.resumeCurrentGame()
        assertEquals(AppScreen.LIVE, viewModel.screen)
        viewModel.goBackFromCurrentScreen()
        assertEquals(AppScreen.HOME, viewModel.screen)
    }

    /**
     * Verify restoring timing cue defaults resets cue-level preferences while preserving
     * global sound and vibration settings.
     */
    @Test
    fun timingCueDefaults() {
        // Reset cue-level timing settings while preserving global sound/vibration preferences.
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
        assertEquals(
            2,
            viewModel.timingAlertPreferences.repeatCountFor(TimingCueId.RECEIVING_TWENTY_FOR_HAND),
        )
        assertEquals(
            TimingAlertMode.DING,
            viewModel.timingAlertPreferences.settingsModeFor(TimingCueId.HARD_CAP),
        )
        assertEquals(3, viewModel.timingAlertPreferences.repeatCountFor(TimingCueId.HARD_CAP))
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
