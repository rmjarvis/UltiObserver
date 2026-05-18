package rmjarvis.ultiobserver

import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import rmjarvis.ultiobserver.ui.theme.UltiObserverTheme

/// Tests for Android file-backed persistence and startup recovery UI behavior.
@RunWith(AndroidJUnit4::class)
class TestPersistence {
    @get:Rule
    val composeRule = createComposeRule()

    /// Test Android app-private storage for each persisted app-data bucket.
    @Test
    fun fileStoragePersistsStateInAppPrivateFiles() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val storageDir = File(context.filesDir, "persistence-test-${System.nanoTime()}")

        try {
            // Save current live state with undo history through Android file-backed storage.
            val storage = FileAppStateStorage(storageDir)
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
            storage.saveCurrentGameState(
                CurrentGameSnapshot(
                    setupState = setup,
                    liveState = scoredState,
                    setupMode = SetupMode.NEW_GAME,
                )
            )
            storage.saveProfile(Profile(profileName = "Casey Observer"))
            val timingPreferences = TimingAlertPreferences(
                globalMode = TimingAlertGlobalMode.VIBRATION_ONLY,
                soundVolume = 0.4f,
            )
            storage.saveSettings(
                Settings(
                    automaticallyAdvanceCountdowns = false,
                    automaticallyLockLivePoint = false,
                    timingAlertPreferences = timingPreferences,
                )
            )

            // Save an archived summary separately, without countdown or undo state.
            val archivedSummary = scoredState.copy(
                phase = LivePhase.GAME_OVER,
                countdown = null,
                undoEntry = null,
            )
            storage.saveArchivedGames(listOf(ArchivedGame(archivedSummary, "")))

            // Load through a fresh storage instance to verify the on-device files round-trip.
            val restoredStorage = FileAppStateStorage(storageDir)
            val restoredCurrentGameState = restoredStorage.loadCurrentGameState()!!
            val restoredProfile = restoredStorage.loadProfile()!!
            val restoredSettings = restoredStorage.loadSettings()!!
            val restoredArchivedGame = restoredStorage.loadArchivedGames().single()

            assertEquals(scoredState, restoredCurrentGameState.liveState)
            val undoRestoredState = restoredCurrentGameState.liveState!!.undoLastAction()
            assertEquals(livePointState, undoRestoredState.copy(redoEntry = null))
            assertNotNull(undoRestoredState.redoEntry)
            assertEquals("Casey Observer", restoredProfile.profileName)
            assertEquals(false, restoredSettings.automaticallyAdvanceCountdowns)
            assertEquals(false, restoredSettings.automaticallyLockLivePoint)
            assertEquals(timingPreferences, restoredSettings.timingAlertPreferences)
            assertEquals(archivedSummary, restoredArchivedGame.state)
            assertNull(restoredArchivedGame.state.undoEntry)
            assertNull(restoredArchivedGame.state.redoEntry)
        } finally {
            storageDir.deleteRecursively()
        }
    }

    /// Test the startup recovery dialog that appears after persisted phone data is reset.
    @Test
    fun startupRecoveryNoticeCanBeDismissed() {
        val viewModel = AppViewModel(
            StartupRecoveryNoticeStorage(
                setOf(PersistedData.PROFILE, PersistedData.SETTINGS)
            )
        )

        composeRule.setContent {
            UltiObserverTheme(dynamicColor = false) {
                UltiObserverApp(viewModel)
            }
        }

        composeRule.onNodeWithText("Phone Data Reset").assertIsDisplayed()
        composeRule.onNodeWithText(
            "Sorry, some phone data was corrupt, so UltiObserver had to revert to default values for Profile and Settings."
        ).assertIsDisplayed()
        composeRule.onNodeWithText("Start New Game").assertIsDisplayed()

        composeRule.onNodeWithText("OK").performClick()

        composeRule.onAllNodesWithText("Phone Data Reset").assertCountEquals(0)
        composeRule.onNodeWithText("Start New Game").assertIsDisplayed()
    }
}

/**
 * Startup-recovery storage fake that reports selected reset buckets.
 *
 * @param resetPersistedDataAreas The buckets the fake should report as repaired.
 */
private class StartupRecoveryNoticeStorage(
    override val resetPersistedDataAreas: Set<PersistedData>,
) : AppStateStorage {
    /// Load no current game for the startup-recovery notice fixture.
    override fun loadCurrentGameState(): CurrentGameSnapshot? = null

    /// Ignore current-game saves for the startup-recovery notice fixture.
    override fun saveCurrentGameState(state: CurrentGameSnapshot) = Unit

    /// Load no profile for the startup-recovery notice fixture.
    override fun loadProfile(): Profile? = null

    /// Ignore profile saves for the startup-recovery notice fixture.
    override fun saveProfile(state: Profile) = Unit

    /// Load no settings for the startup-recovery notice fixture.
    override fun loadSettings(): Settings? = null

    /// Ignore settings saves for the startup-recovery notice fixture.
    override fun saveSettings(state: Settings) = Unit

    /// Load no archived games for the startup-recovery notice fixture.
    override fun loadArchivedGames(): List<ArchivedGame> = emptyList()

    /// Ignore archived-game saves for the startup-recovery notice fixture.
    override fun saveArchivedGames(games: List<ArchivedGame>) = Unit
}
