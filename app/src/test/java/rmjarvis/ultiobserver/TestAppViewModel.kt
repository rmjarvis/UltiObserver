package rmjarvis.ultiobserver

import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
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

    /**
     * Verify setup drafts remain editable until the first live point starts, including
     * Home resume, Back from the initial preview, setup editing, and starting over.
     */
    @Test
    fun setupDraftResume() {
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

        // Resuming from Home should reopen the setup draft, not live play.
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

        // Home resume and Update game setup should both still treat the pre-pull game as a draft.
        viewModel.finishSetup()
        val livePreview = viewModel.liveState!!
        viewModel.goHome()
        viewModel.resumeCurrentGame()
        assertEquals(AppScreen.SETUP, viewModel.screen)
        assertTrue(viewModel.hasSetupDraft)
        assertNull(viewModel.liveState)
        assertEquals("", viewModel.setupState.teamOne.name)
        assertEquals("", viewModel.setupState.teamTwo.name)

        // Update game setup should also treat the pre-pull game as a draft.
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

        // Starting over before the first real point should discard setup-only state.
        val prePullViewModel = AppViewModel(NoOpAppStateStorage)
        prePullViewModel.startNewGame()
        prePullViewModel.finishSetup()
        prePullViewModel.goHome()
        prePullViewModel.startNewGame()
        assertEquals(AppScreen.SETUP, prePullViewModel.screen)
        assertTrue(prePullViewModel.hasSetupDraft)
        assertNull(prePullViewModel.liveState)
        assertTrue(prePullViewModel.archivedGames.isEmpty())

        // Undo-backed setup edits before the opening pull are still setup-only, so starting over
        // should discard them rather than archive them.
        val setupOnlyViewModel = AppViewModel(NoOpAppStateStorage)
        setupOnlyViewModel.startNewGame()
        setupOnlyViewModel.finishSetup()
        val setupOnlyPreview = setupOnlyViewModel.liveState!!
        setupOnlyViewModel.updateLiveGame(
            applySetupToLiveGame(
                setupOnlyPreview,
                setupOnlyPreview.toSetupState().copy(
                    teamOne = TeamSetup("Edited", TeamColorChoice.WHITE),
                ),
                10_000L,
            )
        )
        setupOnlyViewModel.goHome()
        setupOnlyViewModel.startNewGame()
        assertEquals(AppScreen.SETUP, setupOnlyViewModel.screen)
        assertNull(setupOnlyViewModel.liveState)
        assertTrue(setupOnlyViewModel.archivedGames.isEmpty())

        // A logged event before the opening pull means the current game is real enough to archive.
        val prePullEventViewModel = AppViewModel(NoOpAppStateStorage)
        prePullEventViewModel.startNewGame()
        prePullEventViewModel.finishSetup()
        val prePullEventState = prePullEventViewModel.liveState!!
        prePullEventViewModel.updateLiveGame(
            prePullEventState.assessTimeout(
                TeamId.TEAM_ONE,
                prePullEventState.countdown!!.targetEpoch - 1_000L,
            ).state
        )
        prePullEventViewModel.goHome()
        prePullEventViewModel.startNewGame()
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
        viewModel.startNewGame()
        viewModel.finishSetup()
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

        // Non-game screens return Home, and stale draft actions should not disturb live play.
        viewModel.openProfile()
        viewModel.goBackFromCurrentScreen()
        assertEquals(AppScreen.HOME, viewModel.screen)

        // A stale draft action should not disturb an active live point.
        viewModel.updateLiveGame(activeGame.beginLivePoint())
        viewModel.resumeSetupDraft()
        assertEquals(AppScreen.HOME, viewModel.screen)
        assertEquals(GamePhase.LIVE_POINT, viewModel.liveState!!.phase)

        // Back navigation from a resumed live game should return Home.
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
}
