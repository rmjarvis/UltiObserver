package rmjarvis.ultiobserver

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TestUltiObserverAppViewModel {
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
}
