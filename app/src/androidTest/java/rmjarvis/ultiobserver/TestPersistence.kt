package rmjarvis.ultiobserver

import androidx.activity.compose.setContent
import androidx.compose.runtime.key
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertIsDisplayed
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
import org.junit.Test
import org.junit.runner.RunWith
import rmjarvis.ultiobserver.ui.theme.UltiObserverTheme

/// Tests for Android file-backed persistence and startup recovery UI behavior.
@RunWith(AndroidJUnit4::class)
class TestPersistence : MainActivityUiTestFixtures() {
    /**
     * Test Android app-private storage for each persisted app-data bucket.
     */
    @Test
    fun fileStorageRoundTrip() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val storageDir = File(context.filesDir, "persistence-test-${System.nanoTime()}")

        try {
            // Save current live state with undo history through Android file-backed storage.
            val storage = FileAppStateStorage(storageDir)
            val setup = newSetupGameState(
                now = epochTimestamp(
                    LocalDate.of(2026, 5, 11),
                    LocalTime.of(10, 0),
                    ZoneId.systemDefault(),
                ),
            ).copy(
                startDate = LocalDate.of(2026, 5, 11),
                startTime = LocalTime.of(10, 0),
                timeZone = ZoneId.of("America/New_York"),
                rules = GameRules(),
                teamOne = TeamState(
                    name = "Viscous Coupling",
                    color = TeamColorChoice.CUSTOM,
                    customColorArgb = 0xFF336699L,
                    coaches = "Casey Coach",
                    fieldCaptains = "Casey Field",
                    spiritCaptains = "Casey Spirit",
                ),
                teamTwo = TeamState(
                    name = "Animal",
                    color = TeamColorChoice.PINK,
                    coaches = "Riley Coach",
                    fieldCaptains = "Riley Field",
                    spiritCaptains = "Riley Spirit",
                ),
            )
            val livePointState = setup.startGame().beginLivePoint(0L)
            val scoredState = livePointState.recordGoal(
                scoringTeam = TeamId.TEAM_ONE,
                now = livePointState.startEpoch + 5 * 60_000L,
            )
            storage.saveCurrentGame(scoredState)
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
                phase = GamePhase.GAME_OVER,
                countdown = null,
                undoEntry = null,
            )
            storage.saveArchivedGames(
                listOf(
                    archivedSummary
                )
            )

            // Load through a fresh storage instance to verify the on-device files round-trip.
            val restoredStorage = FileAppStateStorage(storageDir)
            val restoredCurrentGame = restoredStorage.loadCurrentGame()!!
            val restoredProfile = restoredStorage.loadProfile()!!
            val restoredSettings = restoredStorage.loadSettings()!!
            val restoredArchivedGame = restoredStorage.loadArchivedGames().single()

            assertEquals(scoredState, restoredCurrentGame)
            assertEquals(setup.startDate, restoredCurrentGame.startDate)
            assertEquals(setup.startTime, restoredCurrentGame.startTime)
            assertEquals(setup.timeZone, restoredCurrentGame.timeZone)
            assertEquals(setup.teamOne, restoredCurrentGame.teamOne.copy(score = 0))
            assertEquals(setup.teamTwo, restoredCurrentGame.teamTwo.copy(score = 0))
            val undoRestoredState = restoredCurrentGame.undoLastAction()
            assertEquals(livePointState, undoRestoredState.copy(redoEntry = null))
            assertNotNull(undoRestoredState.redoEntry)
            assertEquals("Casey Observer", restoredProfile.profileName)
            assertEquals(false, restoredSettings.automaticallyAdvanceCountdowns)
            assertEquals(false, restoredSettings.automaticallyLockLivePoint)
            assertEquals(timingPreferences, restoredSettings.timingAlertPreferences)
            assertEquals(archivedSummary, restoredArchivedGame)
            assertNull(restoredArchivedGame.undoEntry)
            assertNull(restoredArchivedGame.redoEntry)
        } finally {
            storageDir.deleteRecursively()
        }
    }

    /**
     * Test the startup recovery dialog that appears after persisted phone data is reset.
     */
    @Test
    fun startupRecoveryNotice() {
        // Use storage that reports repaired profile and settings buckets at startup.
        val viewModel = AppViewModel(
            StartupRecoveryNoticeStorage(
                setOf(PersistedData.PROFILE, PersistedData.SETTINGS)
            )
        )

        // Render the app from scratch so the startup notice is shown over Home.
        renderApp(viewModel = viewModel, previousRunCrashed = false)

        // The notice names the repaired buckets while leaving Home visible behind the dialog.
        composeRule.onNodeWithText("Phone data reset").assertIsDisplayed()
        val recoveryMessage = "Sorry, some phone data was corrupt, so UltiObserver had to " +
            "revert to default values for Profile and Settings."
        composeRule.onNodeWithText(recoveryMessage).assertIsDisplayed()
        composeRule.onNodeWithText("Start new game").assertIsDisplayed()

        // Dismissing the notice removes only the dialog.
        dismissDialog(text = "OK")
        composeRule.onAllNodesWithText("Phone data reset").assertCountEquals(0)
        composeRule.onNodeWithText("Start new game").assertIsDisplayed()

        // When both notices are pending, data recovery is shown first because it names the
        // concrete repaired data.
        val crashAfterRecoveryViewModel = AppViewModel(
            StartupRecoveryNoticeStorage(
                setOf(PersistedData.PROFILE)
            )
        )
        renderApp(viewModel = crashAfterRecoveryViewModel, previousRunCrashed = true)
        composeRule.onNodeWithText("Phone data reset").assertIsDisplayed()
        composeRule.onAllNodesWithText("Sorry, UltiObserver crashed").assertCountEquals(0)

        // Dismissing recovery reveals the crash apology next.
        dismissDialog(text = "OK")
        composeRule.onAllNodesWithText("Phone data reset").assertCountEquals(0)
        composeRule.onNodeWithText("Sorry, UltiObserver crashed").assertIsDisplayed()
    }

    /**
     * Test the previous-crash apology dialog shown after Crashlytics records a crash.
     */
    @Test
    fun previousCrashNotice() {
        val viewModel = AppViewModel(StartupRecoveryNoticeStorage(emptySet()))

        // Render the app from scratch as though MainActivity saw a previous Crashlytics crash.
        renderApp(viewModel = viewModel, previousRunCrashed = true)

        // The crash notice explains that the app noticed the previous crash and reported it.
        composeRule.onNodeWithText("Sorry, UltiObserver crashed").assertIsDisplayed()
        val crashMessage = "UltiObserver closed unexpectedly last time it ran. A crash report " +
            "was sent to the developers automatically so we can fix the problem."
        composeRule.onNodeWithText(crashMessage).assertIsDisplayed()
        composeRule.onNodeWithText("Start new game").assertIsDisplayed()

        // Dismissing the notice removes only the dialog.
        dismissDialog(text = "OK")
        composeRule.onAllNodesWithText("Sorry, UltiObserver crashed").assertCountEquals(0)
        composeRule.onNodeWithText("Start new game").assertIsDisplayed()
    }

    /**
     * Render the app with custom startup state while keeping shared activity-fixture helpers.
     *
     * @param viewModel App ViewModel to render.
     * @param previousRunCrashed Whether to show the previous-crash startup notice.
     */
    private fun renderApp(viewModel: AppViewModel, previousRunCrashed: Boolean) {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                UltiObserverTheme(dynamicColor = false) {
                    key(viewModel, previousRunCrashed) {
                        UltiObserverApp(
                            viewModel = viewModel,
                            previousRunCrashed = previousRunCrashed,
                        )
                    }
                }
            }
        }
        composeRule.waitForIdle()
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
    override fun loadCurrentGame(): GameState? = null

    /// Ignore current-game saves for the startup-recovery notice fixture.
    override fun saveCurrentGame(state: GameState?) = Unit

    /// Load no profile for the startup-recovery notice fixture.
    override fun loadProfile(): Profile? = null

    /// Ignore profile saves for the startup-recovery notice fixture.
    override fun saveProfile(state: Profile) = Unit

    /// Load no settings for the startup-recovery notice fixture.
    override fun loadSettings(): Settings? = null

    /// Ignore settings saves for the startup-recovery notice fixture.
    override fun saveSettings(state: Settings) = Unit

    /// Load no archived games for the startup-recovery notice fixture.
    override fun loadArchivedGames(): List<GameState> = emptyList()

    /// Ignore archived-game saves for the startup-recovery notice fixture.
    override fun saveArchivedGames(games: List<GameState>) = Unit
}
