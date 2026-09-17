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
 * state owned by AppState.
 */
class TestAppState : GameDomainTestFixtures() {
    /**
     * Verify AppState's main lifecycle from empty Home, through setup and live play,
     * into setup editing and starting over.
     */
    @Test
    fun topLevelGameFlow() {
        // Start from a clean Home state with no current or archived game.
        val appState = AppState(NoOpAppStateStorage)
        assertEquals(AppScreen.HOME, appState.screen)
        assertEquals(AppScreen.HOME, appState.state.value.screen)
        assertNull(appState.currentGame)
        assertTrue(appState.archivedGames.isEmpty())
        assertNull(appState.currentGameHomeSubtitle)
        assertEquals(SetupMode.NEW_GAME, appState.setupMode)
        assertThrows(IllegalStateException::class.java) {
            appState.setupGame
        }

        // Create a setup draft and verify Home can advertise it as resumable.
        appState.startNewGame(now = 123_000L)
        assertEquals(AppScreen.SETUP, appState.screen)
        assertTrue(appState.hasSetupDraft)
        assertEquals("Tap to resume", appState.currentGameHomeSubtitle)

        // Finish setup with named teams and verify the current game is created from that draft.
        val namedSetup = appState.setupGame.copy(
            teamOne = TeamState("Alpha", TeamColorChoice.BLUE),
            teamTwo = TeamState("Beta", TeamColorChoice.PINK),
        )
        appState.updateSetup(namedSetup)
        appState.finishSetup(now = 123_000L)
        assertFalse(appState.hasSetupDraft)
        val startedGame = appState.currentGame
        assertNotNull(startedGame)
        assertEquals(AppScreen.LIVE, appState.screen)
        assertEquals("Tap to resume", appState.currentGameHomeSubtitle)
        assertEquals("Alpha", startedGame!!.teamOne.name)
        assertEquals("Beta", startedGame.teamTwo.name)

        // Current-game updates should keep Home resume state and store the latest score.
        appState.updateCurrentGame(startedGame.beginLivePoint())
        assertEquals("Tap to resume", appState.currentGameHomeSubtitle)
        val adjustedGame = appState.currentGame!!.adjustScore(teamOneScore = 2, teamTwoScore = 1)
        appState.updateCurrentGame(adjustedGame)
        assertEquals(2, appState.currentGame!!.teamOne.score)

        // Reopen setup from the current game and verify setup edits preserve live score state.
        appState.editCurrentGame(appState.currentGame!!)
        assertEquals(AppScreen.SETUP, appState.screen)
        assertEquals(SetupMode.EDIT_CURRENT_GAME, appState.setupMode)
        appState.updateSetup(
            appState.setupGame.copy(
                teamOne = appState.setupGame.teamOne.copy(name = "Alpha Prime"),
            )
        )
        assertFalse(appState.hasSetupDraft)
        appState.finishSetup(now = 123_000L)
        assertEquals(AppScreen.LIVE, appState.screen)
        assertEquals("Alpha Prime", appState.currentGame!!.teamOne.name)
        assertEquals(2, appState.currentGame!!.teamOne.score)

        // Starting over should archive the old current game and create a fresh setup draft.
        val currentGameBeforeStartingOver = appState.currentGame!!
        appState.goHome()
        assertEquals(AppScreen.HOME, appState.screen)
        appState.startNewGame(now = 123_000L)
        assertEquals(AppScreen.SETUP, appState.screen)
        assertTrue(appState.hasSetupDraft)
        assertEquals(1, appState.archivedGames.size)
        val archivedGame = appState.archivedGames.single()
        assertEquals(ArchivedGameCategory.IN_PROGRESS, archivedGame.archiveCategory)
        assertEquals(currentGameBeforeStartingOver, archivedGame)
        assertNull(archivedGame.endEpoch)
        assertEquals(GamePhase.SETUP, appState.currentGame?.phase)
        assertEquals("Tap to resume", appState.currentGameHomeSubtitle)
    }

    /** Verify game start uses the field-end arrangement for each orientation preference. */
    @Test
    fun gameStartOrientation() {
        val landscapeViewModel = AppState(NoOpAppStateStorage)
        landscapeViewModel.updateSettings(
            landscapeViewModel.settings.withOrientationPreference(
                OrientationPreference.LANDSCAPE
            )
        )
        landscapeViewModel.startNewGame(now = 123_000L)
        landscapeViewModel.updateSetup(
            landscapeViewModel.setupGame.copy(
                // Portrait would put Near at the top for this prompt target. Landscape should
                // still keep Far on the left, independent of timing-prompt responsibility.
                pullPromptTarget = PullPromptTarget.FAR,
            )
        )
        landscapeViewModel.finishSetup(now = 123_000L)

        assertEquals(
            OrientationPreference.LANDSCAPE,
            landscapeViewModel.settings.orientationPreference
        )
        assertEquals(FieldEnd.FAR, landscapeViewModel.currentGame!!.topDisplayedEnd)

        // Auto-rotate uses the normal Portrait field-end arrangement when starting the game.
        val autoRotateViewModel = AppState(NoOpAppStateStorage)
        autoRotateViewModel.updateSettings(
            autoRotateViewModel.settings.withOrientationPreference(
                OrientationPreference.AUTO_ROTATE
            )
        )
        autoRotateViewModel.startNewGame(now = 123_000L)
        autoRotateViewModel.updateSetup(
            autoRotateViewModel.setupGame.copy(pullPromptTarget = PullPromptTarget.FAR)
        )
        autoRotateViewModel.finishSetup(now = 123_000L)

        assertEquals(FieldEnd.NEAR, autoRotateViewModel.currentGame!!.topDisplayedEnd)
    }

    /**
     * Verify setup drafts remain editable until the first live point starts, including
     * Home resume, Back from the pre-pull preview, setup editing, and starting over.
     */
    @Test
    fun setupDraftResume() {
        // Create a blank-name setup draft and verify Home resumes it as setup, not live play.
        val appState = AppState(NoOpAppStateStorage)
        appState.startNewGame(now = 123_000L)
        val draftedSetup = appState.setupGame.copy(
            teamOne = TeamState("", TeamColorChoice.GREEN),
            teamTwo = TeamState("", TeamColorChoice.YELLOW),
        )
        appState.updateSetup(draftedSetup)
        appState.goHome()
        assertEquals(AppScreen.HOME, appState.screen)
        assertTrue(appState.hasSetupDraft)
        assertEquals(GamePhase.SETUP, appState.currentGame?.phase)

        // Resuming from Home should reopen the setup draft, not live play.
        appState.resumeCurrentGame()
        assertEquals(AppScreen.SETUP, appState.screen)
        assertEquals(draftedSetup, appState.setupGame)

        // Backing out from the pre-pull preview should restore the original editable draft.
        appState.finishSetup(now = 123_000L)
        assertEquals(AppScreen.LIVE, appState.screen)
        assertFalse(appState.hasSetupDraft)
        appState.goBackFromCurrentScreen()
        assertEquals(AppScreen.SETUP, appState.screen)
        assertTrue(appState.hasSetupDraft)
        assertEquals(GamePhase.SETUP, appState.currentGame?.phase)
        assertEquals("", appState.setupGame.teamOne.name)
        assertEquals("", appState.setupGame.teamTwo.name)

        // Backing out from a new setup draft returns Home while keeping the draft resumable.
        val newSetupBackViewModel = AppState(NoOpAppStateStorage)
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
        appState.finishSetup(now = 123_000L)
        val livePreview = appState.currentGame!!
        appState.goHome()
        appState.resumeCurrentGame()
        assertEquals(AppScreen.LIVE, appState.screen)
        appState.goBackFromCurrentScreen()
        assertEquals(AppScreen.SETUP, appState.screen)
        assertTrue(appState.hasSetupDraft)
        assertEquals(GamePhase.SETUP, appState.currentGame?.phase)
        assertEquals("", appState.setupGame.teamOne.name)
        assertEquals("", appState.setupGame.teamTwo.name)

        // Update game setup should also treat the pre-pull game as a draft.
        appState.finishSetup(now = 123_000L)
        appState.editCurrentGame(appState.currentGame!!)
        assertEquals(AppScreen.SETUP, appState.screen)
        assertTrue(appState.hasSetupDraft)
        assertEquals(GamePhase.SETUP, appState.currentGame?.phase)
        assertEquals("", appState.setupGame.teamOne.name)
        assertEquals("", appState.setupGame.teamTwo.name)

        // Once a real point starts, Home should resume the in-progress game instead of setup.
        appState.finishSetup(now = 123_000L)
        appState.updateCurrentGame(appState.currentGame!!.beginLivePoint())
        appState.goHome()
        appState.resumeCurrentGame()
        assertEquals(AppScreen.LIVE, appState.screen)
        assertEquals(GamePhase.LIVE_POINT, appState.currentGame!!.phase)
        assertFalse(appState.hasSetupDraft)
        assertEquals(livePreview.teamOne.name, appState.currentGame!!.teamOne.name)

        // Starting over from an unstarted setup draft should save the old draft aside.
        val setupDraftViewModel = AppState(NoOpAppStateStorage)
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
        val prePullViewModel = AppState(NoOpAppStateStorage)
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
        val setupOnlyViewModel = AppState(NoOpAppStateStorage)
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
        val prePullEventViewModel = AppState(NoOpAppStateStorage)
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
        val appState = AppState(NoOpAppStateStorage)
        appState.startNewGame(now = 123_000L)
        appState.finishSetup(now = 123_000L)
        val scoredGame = appState.currentGame!!.adjustScore(teamOneScore = 3, teamTwoScore = 2)
        appState.updateCurrentGame(scoredGame)

        // Home should retain the current game until it is resumed.
        appState.goHome()
        assertEquals(AppScreen.HOME, appState.screen)
        assertEquals(scoredGame, appState.currentGame)

        // Resuming and editing setup should keep score while applying setup changes.
        appState.resumeCurrentGame()
        assertEquals(AppScreen.LIVE, appState.screen)
        assertEquals(scoredGame, appState.displayedGame)
        appState.editCurrentGame(appState.displayedGame!!)
        assertEquals(AppScreen.SETUP, appState.screen)
        assertEquals(SetupMode.EDIT_CURRENT_GAME, appState.setupMode)
        appState.goBackFromCurrentScreen()
        assertEquals(AppScreen.LIVE, appState.screen)
        assertNull(appState.setupEditDraft)
        appState.editCurrentGame(appState.currentGame!!)
        assertEquals(AppScreen.SETUP, appState.screen)
        appState.updateSetup(
            appState.setupGame.copy(
                rules = appState.setupGame.rules.copy(gameTo = 17),
            )
        )
        appState.finishSetup(now = 123_000L)
        assertEquals(AppScreen.LIVE, appState.screen)
        assertEquals(17, appState.currentGame!!.rules.gameTo)
        assertEquals(3, appState.currentGame!!.teamOne.score)
        assertEquals(2, appState.currentGame!!.teamTwo.score)
    }

    /**
     * Verify unavailable navigation and game actions leave existing Home, setup, and live
     * game state alone.
     */
    @Test
    fun unavailableActions() {
        // Empty-home actions should be harmless when there is no current or completed game.
        val appState = AppState(NoOpAppStateStorage)
        appState.resumeCurrentGame()
        assertEquals(AppScreen.HOME, appState.screen)
        assertNull(appState.currentGame)
        assertThrows(IndexOutOfBoundsException::class.java) {
            appState.openArchivedGame(0, now = 123_000L)
        }
        assertEquals(AppScreen.HOME, appState.screen)
        assertNull(appState.displayedGame)
        appState.archiveCompletedGame()
        assertTrue(appState.archivedGames.isEmpty())

        // A synthetic transient live screen without a current game should back out to Home.
        // I'm not sure if this state is possible with race conditions in the app, so this is
        // a defensive check.  If there is no currentGame and somehow we are in the live screen,
        // it probably looks weird and might already have crashed, but if not, then back
        // will take use to the safety of the HOME screen.
        appState.forceUiState(
            appState.state.value.copy(
                screen = AppScreen.LIVE,
                currentGame = null,
                viewingArchivedGame = null,
            )
        )
        appState.goBackFromCurrentScreen()
        assertEquals(AppScreen.HOME, appState.screen)
        assertNull(appState.currentGame)

        // Active-game-only state should reject the completed-game archive action.
        appState.startNewGame(now = 123_000L)
        appState.finishSetup(now = 123_000L)
        val activeGame = appState.currentGame!!
        appState.goHome()
        appState.archiveCompletedGame()
        assertTrue(appState.archivedGames.isEmpty())
        assertEquals(activeGame, appState.currentGame)

        // Non-game screens return Home.
        appState.openProfile()
        appState.goBackFromCurrentScreen()
        assertEquals(AppScreen.HOME, appState.screen)

        // Back navigation from a resumed live game should return Home.
        appState.updateCurrentGame(activeGame.beginLivePoint())
        appState.resumeCurrentGame()
        assertEquals(AppScreen.LIVE, appState.screen)
        appState.goBackFromCurrentScreen()
        assertEquals(AppScreen.HOME, appState.screen)
    }

    /**
     * Verify the observer profile name seeds new setup drafts as the first observer.
     */
    @Test
    fun profileNameSeedsNewGameObserver() {
        // New game setup should start with the profile name as the first observer, since the
        // observer using the phone will usually work their own game.
        val appState = AppState(NoOpAppStateStorage)
        appState.updateProfile(appState.profile.withName(" Casey Observer "))
        appState.startNewGame(now = 123_000L)
        assertEquals(listOf("Casey Observer"), appState.setupGame.observerNames)

        // Blank or whitespace profile names should not create an empty observer entry.
        val blankProfileViewModel = AppState(NoOpAppStateStorage)
        blankProfileViewModel.updateProfile(blankProfileViewModel.profile.withName("   "))
        blankProfileViewModel.startNewGame(now = 123_000L)
        assertEquals(emptyList<String>(), blankProfileViewModel.setupGame.observerNames)
    }

    /**
     * Force an app state that has no public setup path.
     *
     * This is reserved for defensive navigation tests where the state may only be reachable as a
     * transient race during UI teardown.
     *
     * @param state The synthetic app state snapshot to install.
     */
    @Suppress("UNCHECKED_CAST")
    private fun AppState.forceUiState(state: AppStateSnapshot) {
        val stateField = AppState::class.java.getDeclaredField("_state")
        stateField.isAccessible = true
        val mutableState = stateField.get(this) as MutableStateFlow<AppStateSnapshot>
        mutableState.value = state
    }
}
