package rmjarvis.ultiobserver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Tests for archived-game lifecycle behavior owned by AppViewModel.
 */
class TestArchive : GameDomainTestFixtures() {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    /**
     * Verify archived games open as read-only live summaries, ignore live-game edit
     * callbacks, and return to the archive list on Back.
     */
    @Test
    fun archivedReadOnlySummary() {
        // Archive a completed game and open it as the current read-only summary.
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

        // Opening the archive should show it as a live-screen-shaped read-only summary.
        viewModel.openArchivedGame(0)
        assertEquals(AppScreen.LIVE, viewModel.screen)
        assertTrue(viewModel.viewingReadOnlySummary)
        assertEquals(archivedGame, viewModel.currentLiveState)

        // Live-game callbacks are ignored while viewing an archived read-only summary.
        val changedArchivedGame = archivedGame.copy(teamOne = archivedGame.teamOne.copy(score = 99))
        viewModel.updateLiveGame(changedArchivedGame)
        assertNull(viewModel.liveState)
        assertEquals(archivedGame, viewModel.currentLiveState)

        // Back navigation returns from the archived summary to the archive list.
        viewModel.goBackFromCurrentScreen()
        assertEquals(AppScreen.ARCHIVED_GAMES, viewModel.screen)
        assertFalse(viewModel.viewingReadOnlySummary)
        assertNull(viewModel.currentLiveState)

        // Edit-game callbacks are ignored while viewing a read-only archive.
        viewModel.openArchivedGame(0)
        viewModel.editCurrentGame(archivedGame)
        assertEquals(AppScreen.LIVE, viewModel.screen)
        assertTrue(viewModel.viewingReadOnlySummary)
        assertEquals(archivedGame, viewModel.currentLiveState)
    }

    /**
     * Verify restoring an archived game promotes it to current game while archiving any
     * active current preview first.
     */
    @Test
    fun archiveRestoreReplacesCurrentPreview() {
        // Archive a completed game, then create a separate current preview.
        val viewModel = AppViewModel(NoOpAppStateStorage)
        viewModel.startNewGame()
        viewModel.finishSetup()
        val archivedGame = viewModel.liveState!!.copy(phase = GamePhase.GAME_OVER)
        viewModel.updateLiveGame(archivedGame)
        viewModel.archiveCompletedGame()
        val archivedState = viewModel.archivedGames.single().state

        // Create a separate current preview that will be archived during restore.
        viewModel.startNewGame()
        viewModel.finishSetup()
        val currentPreview = viewModel.liveState!!
        assertTrue(currentPreview.isInitialLivePreview())

        // Restoring the archive promotes it and archives the previous current preview.
        viewModel.openArchivedGame(0)
        viewModel.restoreViewingArchivedGame()
        assertEquals(AppScreen.LIVE, viewModel.screen)
        assertFalse(viewModel.viewingReadOnlySummary)
        assertEquals(archivedState, viewModel.liveState)
        assertEquals(1, viewModel.archivedGames.size)
        assertEquals(
            currentPreview.teamOne.name,
            viewModel.archivedGames.single().state.teamOne.name,
        )
    }

    /**
     * Verify a completed current game can be reopened from Home and then moved into
     * Archived games as a read-only summary.
     */
    @Test
    fun completedCurrentGameArchive() {
        // Complete the current game and verify Home opens it as the current summary.
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

        // Archiving the completed current game clears the current slot.
        viewModel.goHome()
        viewModel.archiveCompletedGame()
        assertNull(viewModel.liveState)
        assertEquals(1, viewModel.archivedGames.size)
        assertEquals("", viewModel.archivedGames.single().subtitle)

        // Opening the archived copy should expose it as a read-only summary.
        viewModel.openArchivedGame(0)
        assertEquals(AppScreen.LIVE, viewModel.screen)
        assertTrue(viewModel.viewingReadOnlySummary)
        assertEquals(viewModel.archivedGames.single().state, viewModel.currentLiveState)
    }

    /**
     * Verify a restored completed archive can undo End game while older undo history
     * stays pruned.
     */
    @Test
    fun completedArchiveUndo() {
        // Archive a completed game with deeper undo history.
        val viewModel = AppViewModel(NoOpAppStateStorage)
        viewModel.startNewGame()
        viewModel.finishSetup()
        val initialLiveState = viewModel.liveState!!
        val beforeEndGame = initialLiveState.copy(
            undoEntry = UndoEntry("Undo Start point", initialLiveState),
        )
        val completedGame = beforeEndGame.copy(
            phase = GamePhase.GAME_OVER,
            undoEntry = UndoEntry("Undo End game", beforeEndGame),
            redoEntry = beforeEndGame,
        )
        viewModel.updateLiveGame(completedGame)
        viewModel.archiveCompletedGame()
        val archivedState = viewModel.archivedGames.single().state
        val prunedBeforeEndGame = beforeEndGame.pruneUndoHistory()
        assertEquals("Undo End game", archivedState.undoEntry?.label)
        assertEquals(prunedBeforeEndGame, archivedState.undoEntry!!.previous)
        assertNull(archivedState.redoEntry)

        // Restoring the archive keeps the end-game undo while older undo entries stay pruned.
        viewModel.openArchivedGame(0)
        viewModel.restoreViewingArchivedGame()
        val restoredGame = viewModel.liveState!!
        val restoredUndo = restoredGame.undoLastAction()
        assertEquals(GamePhase.GAME_OVER, restoredGame.phase)
        assertEquals("Undo End game", restoredGame.undoEntry?.label)
        assertEquals(prunedBeforeEndGame, restoredUndo.copy(redoEntry = null))
        assertNotNull(restoredUndo.redoEntry)
    }

    /**
     * Verify archived-game restore commands ignore missing selections and promote a valid
     * archive when there is no current game.
     */
    @Test
    fun archiveRestoreSelection() {
        // Restore commands with no selected archive or empty archive list are harmless.
        val viewModel = AppViewModel(NoOpAppStateStorage)
        viewModel.restoreViewingArchivedGame()
        viewModel.restoreArchivedGame(0)
        assertEquals(AppScreen.HOME, viewModel.screen)
        assertNull(viewModel.liveState)
        assertTrue(viewModel.archivedGames.isEmpty())

        // Restoring by index removes the selected archive and promotes it to current game.
        viewModel.startNewGame()
        viewModel.finishSetup()
        viewModel.updateLiveGame(
            viewModel.liveState!!.copy(
                phase = GamePhase.GAME_OVER,
                teamOne = TeamLiveState("First Archive", TeamColorChoice.WHITE),
            )
        )
        viewModel.archiveCompletedGame()

        // Add a second archive so a valid restore can prove it removes only the selected game.
        viewModel.startNewGame()
        viewModel.finishSetup()
        viewModel.updateLiveGame(
            viewModel.liveState!!.copy(
                phase = GamePhase.GAME_OVER,
                teamOne = TeamLiveState("Second Archive", TeamColorChoice.WHITE),
            )
        )
        viewModel.archiveCompletedGame()

        // Out-of-range restore requests should leave the archive list unchanged.
        viewModel.restoreArchivedGame(99)
        assertNull(viewModel.liveState)
        assertEquals(2, viewModel.archivedGames.size)

        // Restoring a valid selection removes only that archived game.
        viewModel.openArchivedGame(1)
        viewModel.restoreViewingArchivedGame()
        assertEquals(AppScreen.LIVE, viewModel.screen)
        assertFalse(viewModel.viewingReadOnlySummary)
        assertEquals("Second Archive", viewModel.liveState!!.teamOne.name)
        assertEquals(1, viewModel.archivedGames.size)
        assertEquals("First Archive", viewModel.archivedGames.single().state.teamOne.name)
    }

    /**
     * Verify deleting the current game, one archived game, and all archived games clears
     * the corresponding ViewModel state.
     */
    @Test
    fun gameDeletion() {
        // Deleting the current game clears current and currentLive state.
        val viewModel = AppViewModel(NoOpAppStateStorage)
        viewModel.startNewGame()
        viewModel.finishSetup()
        val currentGame = viewModel.liveState!!
        viewModel.deleteCurrentGame()
        assertEquals(AppScreen.HOME, viewModel.screen)
        assertNull(viewModel.liveState)
        assertNull(viewModel.currentLiveState)

        // Deleting a viewed archived game clears the selection, and missing indexes are harmless.
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

        // Deleting all archived games clears the archive list and the viewed archive.
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

    /**
     * Verify starting over from an already completed game archives it without adding
     * another close-game wrapper or live-only state.
     */
    @Test
    fun startingOverFromCompletedGame() {
        // Starting over from an already completed game should not wrap End game again.
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
            undoEntry = UndoEntry("Undo End game", beforeUndoAction),
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
        assertEquals("Undo End game", viewModel.archivedGames.single().state.undoEntry?.label)
        assertEquals(
            beforeUndoAction.pruneUndoHistory(),
            viewModel.archivedGames.single().state.undoEntry!!.previous,
        )
        assertNull(viewModel.archivedGames.single().state.redoEntry)
    }

    /**
     * Verify restoring an accidentally archived active game makes it current again, prunes
     * undo state, and preserves any replaced current game as restorable.
     */
    @Test
    fun archivedActiveGameRestore() {
        // Build an active game that will be archived with restorable live state.
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

        // Starting a new game archives a summary but keeps the live state for restore.
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

        // Restoring the archive should preserve the replacement current game as restorable.
        restoredViewModel.restoreArchivedGame(0)
        assertEquals(AppScreen.LIVE, restoredViewModel.screen)
        assertEquals(1, restoredViewModel.archivedGames.size)
        val replacementArchive = restoredViewModel.archivedGames.single()
        assertEquals(GamePhase.GAME_OVER, replacementArchive.state.phase)
        assertEquals(replacementSetup.teamOne.name, replacementArchive.state.teamOne.name)
        assertEquals(replacementSetup.teamTwo.name, replacementArchive.state.teamTwo.name)
        assertEquals(
            replacementCurrent.pruneUndoHistory(clearCountdown = false),
            replacementArchive.restorableState,
        )
        assertFalse(restoredViewModel.hasSetupDraft)
        assertEquals(SetupMode.EDIT_CURRENT_GAME, restoredViewModel.setupMode)
        assertEquals(
            activeGame.pruneUndoHistory(clearCountdown = false),
            restoredViewModel.liveState,
        )
        assertEquals(GamePhase.LIVE_POINT, restoredViewModel.liveState!!.phase)
        assertNull(restoredViewModel.liveState!!.undoEntry)
        assertNull(restoredViewModel.liveState!!.redoEntry)
        assertEquals(setup.teamOne.name, restoredViewModel.setupState.teamOne.name)
        assertEquals(setup.teamTwo.name, restoredViewModel.setupState.teamTwo.name)
    }

    /**
     * Verify a new game's default rules prefer the most recent archived completed game's
     * rules when no current game exists.
     */
    @Test
    fun newGameRulesFromArchive() {
        // Starting from Home prefers rules from the most recently archived completed game.
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

    /**
     * Verify starting over from an active current game uses that game's rules as the next
     * setup draft defaults.
     */
    @Test
    fun newGameRulesFromCurrentGame() {
        // Starting over from an active current game carries its rules into the next setup draft.
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
}
