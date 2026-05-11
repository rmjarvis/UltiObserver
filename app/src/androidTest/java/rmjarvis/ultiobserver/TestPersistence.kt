package rmjarvis.ultiobserver

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class TestPersistence {
    // Test Android app-private storage for active state and archived summaries.
    @Test
    fun fileStorePersistsStateOnDeviceStorage() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val storeDir = File(context.filesDir, "persistence-test-${System.nanoTime()}")

        try {
            // Save active live state with undo history through the Android file-backed store.
            val store = FileAppStateStore(storeDir)
            val setup = GameSetupState(
                startDate = LocalDate.of(2026, 5, 11),
                startTime = LocalTime.of(10, 0),
                timeZone = ZoneId.of("America/New_York"),
                teamOne = TeamSetup("Viscous Coupling", TeamColorChoice.BLUE),
                teamTwo = TeamSetup("Animal", TeamColorChoice.PINK),
            )
            val livePointState = createLiveGameState(setup).beginLivePoint()
            val scoredState = livePointState.recordGoal(
                scoringTeam = TeamId.TEAM_ONE,
                now = livePointState.startEpoch + 5 * 60_000L,
            )
            store.saveActiveState(
                PersistedActiveAppState(
                    screen = AppScreen.LIVE,
                    setupState = setup,
                    liveState = scoredState,
                    setupMode = SetupMode.NEW_GAME,
                    viewingArchivedGameIndex = null,
                )
            )

            // Save an archived summary separately, without countdown or undo state.
            val archivedSummary = scoredState.copy(
                phase = LivePhase.GAME_OVER,
                countdown = null,
                undoEntry = null,
            )
            store.saveArchivedGames(listOf(ArchivedGame(archivedSummary, "")))

            // Load through a fresh store instance to verify the on-device files round-trip.
            val restoredStore = FileAppStateStore(storeDir)
            val restoredActiveState = restoredStore.loadActiveState()!!
            val restoredArchivedGame = restoredStore.loadArchivedGames().single()

            assertEquals(scoredState, restoredActiveState.liveState)
            assertEquals(livePointState, restoredActiveState.liveState!!.undoLastAction())
            assertEquals(archivedSummary, restoredArchivedGame.state)
            assertNull(restoredArchivedGame.state.undoEntry)
        } finally {
            storeDir.deleteRecursively()
        }
    }
}
