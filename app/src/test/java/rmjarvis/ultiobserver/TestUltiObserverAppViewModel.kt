package rmjarvis.ultiobserver

import org.junit.Assert.assertEquals
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

        val adjustedGame = adjustScore(startedGame, teamOneScore = 2, teamTwoScore = 1)
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
    }
}
