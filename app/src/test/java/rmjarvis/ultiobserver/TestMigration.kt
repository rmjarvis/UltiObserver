package rmjarvis.ultiobserver

import java.io.File
import java.nio.file.Paths
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Tests for migrating old persistence fixture directories into the current app model.
 */
class TestMigration : GameDomainTestFixtures() {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    /**
     * Verify every v1.0 fixture scenario loads without startup recovery, rewrites itself
     * as current-version JSON, and preserves the meaningful scenario data.
     *
     * The asserted fixture values come from
     * `tools/persistence-fixtures/v1.0/PersistenceFixtureGeneratorTool.kt`.
     */
    @Test
    fun migrateFixturesFromV1_0() {
        // default-buckets was created with the defaults for everything.
        val defaultBuckets = loadMigratedFixture("v1.0", "default-buckets")
        assertNull(defaultBuckets.currentGame)
        assertFalse(defaultBuckets.hasSetupDraft)
        assertEquals("", defaultBuckets.profile.name)
        assertTrue(defaultBuckets.archivedGames.isEmpty())

        // setup-draft has changes made in the setup screen, but didn't start a game yet.
        // The main tricky thing here is the different way the prior cards were stored.
        val setupDraft = loadMigratedFixture("v1.0", "setup-draft")
        assertTrue(setupDraft.hasSetupDraft)
        assertEquals("Migration Invitational", setupDraft.setupGame.tournamentName)
        assertEquals("Bees", setupDraft.setupGame.teamOne.name)
        assertEquals("Ferns", setupDraft.setupGame.teamTwo.name)
        assertEquals(TeamId.TEAM_TWO, setupDraft.setupGame.pullingTeam)
        assertEquals(FieldEnd.NEAR, setupDraft.setupGame.pullingFromEnd)
        assertEquals(PullPromptTarget.NEAR, setupDraft.setupGame.pullPromptTarget)
        assertEquals(GenderRatio.FOUR_MEN_THREE_WOMEN, setupDraft.setupGame.initialGenderRatio)
        assertEquals(FieldEnd.FAR, setupDraft.setupGame.firstHalfGenZone)
        assertTrue(setupDraft.setupGame.rules.switchGenZoneAtHalftime)
        assertEquals(
            listOf(
                PlayerRecord("7", priorYellows = 1),
                PlayerRecord("", "N/A (1)", priorReds = 1),
                PlayerRecord("", "N/A (2)", priorYellows = 1),
            ),
            setupDraft.setupGame.teamOnePlayers,
        )
        assertEquals(
            listOf(
                PlayerRecord("12", priorReds = 1),
                PlayerRecord("", "N/A (1)", priorYellows = 1),
                PlayerRecord("", "N/A (2)", priorReds = 1),
            ),
            setupDraft.setupGame.teamTwoPlayers,
        )

        // active-game is a still-active game with some of just about every kind of action
        // possible.  The hardest part for this one is dealing with the player cards, which
        // was done very differently in version 1.0. Especially there was the option for a
        // player number to be "unknown".  We convert these unknown player numbers into
        // N/A (1), N/A (2), etc. in the name field.  Or just N/A if there is only one.
        val activeGame = loadMigratedFixture("v1.0", "active-game")
        val currentState = activeGame.currentGame!!
        assertEquals("Casey Observer", activeGame.profile.name)
        assertEquals(ObserverAvatarPreference.BLUE, activeGame.profile.avatarPreference)
        assertFalse(activeGame.settings.automaticallyAdvanceCountdowns)
        assertFalse(activeGame.settings.automaticallyLockLivePoint)
        assertEquals(TimingAlertGlobalMode.SOUNDS_ON, activeGame.settings.timingAlerts.globalMode)
        assertEquals(0.35f, activeGame.settings.timingAlerts.soundVolume, 0.0001f)
        assertEquals(250L, activeGame.settings.timingAlerts.vibrationDurationMillis)
        assertTrue(activeGame.settings.timingAlerts.vibrateWithSounds)
        assertEquals(SetupMode.EDIT_CURRENT_GAME, activeGame.setupMode)
        assertNull(currentState.undoEntry)
        assertNull(currentState.redoEntry)
        assertNull(currentState.countdown)
        assertTrue(currentState.hasExpiredPullActions(currentState.startEpoch))
        assertEquals(1, activeGame.archivedGames.size)
        assertEquals(TeamId.TEAM_ONE, currentState.teamDefendingEnd(FieldEnd.FAR))
        assertEquals(TeamId.TEAM_TWO, currentState.pullingTeam)
        assertEquals(FieldEnd.NEAR, currentState.pullingFromEnd)
        assertEquals(PullPromptTarget.NEAR, currentState.pullPromptTarget)
        assertEquals(TeamId.TEAM_TWO, currentState.openingPullingTeam)
        assertEquals(FieldEnd.NEAR, currentState.openingPullingFromEnd)
        assertEquals(1, currentState.teamOne.score)
        assertEquals(1, currentState.teamTwo.score)
        assertEquals(2, currentState.teamOne.blueCards)
        assertEquals(1, currentState.teamOne.technicalFouls)
        assertEquals(1, currentState.teamTwo.blueCards)
        assertEquals(2, currentState.teamTwo.technicalFouls)
        assertEquals(1, currentState.teamOne.offsides)
        assertEquals(1, currentState.teamOne.falseStarts)
        assertEquals(1, currentState.teamOne.timeoutsUsedThisHalf)
        assertEquals(1, currentState.teamTwo.offsides)
        assertEquals(1, currentState.teamTwo.falseStarts)
        assertEquals(1, currentState.teamTwo.timeoutsUsedThisHalf)
        assertEquals(
            listOf(
                PlayerRecord(
                    jerseyNumber = "7",
                    priorYellows = 1,
                    cards = listOf(InGamePlayerCardEvent(CardType.YELLOW, 0)),
                ),
                PlayerRecord("", "N/A (1)", priorReds = 1),
                PlayerRecord(
                    jerseyNumber = "",
                    playerName = "N/A (2)",
                    priorYellows = 1,
                    cards = listOf(
                        InGamePlayerCardEvent(CardType.YELLOW, 1),
                        InGamePlayerCardEvent(CardType.YELLOW, 2),
                    ),
                ),
                PlayerRecord(
                    jerseyNumber = "22",
                    cards = listOf(InGamePlayerCardEvent(CardType.RED, 3)),
                ),
            ),
            currentState.teamOnePlayers,
        )
        assertEquals(
            listOf(
                PlayerRecord(
                    jerseyNumber = "12",
                    priorReds = 1,
                    cards = listOf(InGamePlayerCardEvent(CardType.RED, 4)),
                ),
                PlayerRecord("", "N/A (1)", priorYellows = 1),
                PlayerRecord(
                    jerseyNumber = "",
                    playerName = "N/A (2)",
                    priorReds = 1,
                    cards = listOf(InGamePlayerCardEvent(CardType.RED, 5)),
                ),
                PlayerRecord(
                    jerseyNumber = "27",
                    cards = listOf(
                        InGamePlayerCardEvent(CardType.YELLOW, 6),
                        InGamePlayerCardEvent(CardType.YELLOW, 7),
                    ),
                ),
            ),
            currentState.teamTwoPlayers,
        )
        assertTrue(currentState.eventLog.any { entry ->
            entry.type == EventLogType.YELLOW_CARD &&
                entry.player == PlayerIdentity("", "N/A (2)")
        })
        assertEquals(2, currentState.eventLog.count { it.type == EventLogType.OFFSIDES })
        assertEquals(2, currentState.eventLog.count { it.type == EventLogType.FALSE_START })
        assertEquals(2, currentState.eventLog.count { it.type == EventLogType.TIMEOUT })

        // setup-saved was a game that just got past the setup stage before being archived
        // and restored as the current game.
        val setupSaved = loadMigratedFixture("v1.0", "setup-saved")
        val setupSavedCurrentGame = setupSaved.currentGame!!
        assertEquals(GamePhase.PRE_GAME, setupSavedCurrentGame.phase)
        assertNull(setupSavedCurrentGame.undoEntry)
        assertNull(setupSavedCurrentGame.countdown)
        assertTrue(setupSavedCurrentGame.hasExpiredPullActions(setupSavedCurrentGame.startEpoch))
        assertEquals(1, setupSaved.archivedGames.size)
        val setupSavedArchive = setupSaved.archivedGames.single()
        assertEquals(ArchivedGameCategory.IN_PROGRESS, setupSavedArchive.archiveCategory)
        assertEquals(GamePhase.PRE_GAME, setupSavedArchive.phase)
        assertEquals("Simple One", setupSavedArchive.teamOne.name)
        assertEquals("Simple Two", setupSavedArchive.teamTwo.name)
        assertNull(setupSavedArchive.countdown)
        assertTrue(setupSavedArchive.hasExpiredPullActions(setupSavedArchive.startEpoch))
        assertTrue(setupSavedArchive.teamOnePlayers.isEmpty())
        assertTrue(setupSavedArchive.teamTwoPlayers.isEmpty())
        assertTrue(setupSavedArchive.eventLog.isEmpty())

        // complete-current-game is a game that has finished, but not been archived.
        // So it still has an Undo End game option to take it back to in-progress state.
        // It also exercises a few different paths for unknown players than the other
        // games do.
        val completeCurrentGame = loadMigratedFixture("v1.0", "complete-current-game")
        val completeCurrentState = completeCurrentGame.currentGame!!
        assertEquals(GamePhase.GAME_OVER, completeCurrentState.phase)
        assertEquals("Undo End game", completeCurrentState.undoEntry?.label)
        assertEquals(
            listOf(
                PlayerRecord(
                    jerseyNumber = "",
                    playerName = "N/A",
                    priorReds = 1,
                    cards = listOf(
                        InGamePlayerCardEvent(CardType.YELLOW, 0),
                        InGamePlayerCardEvent(CardType.YELLOW, 1),
                    ),
                ),
            ),
            completeCurrentState.teamOnePlayers,
        )
        assertEquals(
            listOf(
                PlayerRecord(
                    jerseyNumber = "",
                    playerName = "N/A (1)",
                    cards = listOf(InGamePlayerCardEvent(CardType.RED, 2)),
                ),
                PlayerRecord(
                    jerseyNumber = "",
                    playerName = "N/A (2)",
                ),
            ),
            completeCurrentState.teamTwoPlayers,
        )
        assertEquals(
            2,
            completeCurrentState.eventLog.count { entry ->
                entry.type == EventLogType.YELLOW_CARD &&
                    entry.player == PlayerIdentity("", "N/A")
            },
        )
        assertTrue(completeCurrentState.eventLog.any { entry ->
            entry.type == EventLogType.RED_CARD &&
                entry.player == PlayerIdentity("", "N/A (1)")
        })
        assertEquals(
            2,
            completeCurrentState.eventLog.count { entry ->
                entry.type == EventLogType.YELLOW_CARD &&
                    entry.player == PlayerIdentity("", "N/A (2)")
            },
        )
        assertFalse(completeCurrentState.eventLog.any { entry ->
            entry.type == EventLogType.RED_CARD &&
                entry.player == PlayerIdentity("", "N/A (2)")
        })
        val restoredCompleteCurrentGame = completeCurrentState.undoLastAction()
        assertEquals(GamePhase.BETWEEN_POINTS, restoredCompleteCurrentGame.phase)
        assertNull(restoredCompleteCurrentGame.undoEntry)
        assertNotNull(restoredCompleteCurrentGame.redoEntry)
        assertEquals(2, restoredCompleteCurrentGame.teamCardTotal(TeamId.TEAM_ONE))
        assertEquals(2, restoredCompleteCurrentGame.teamCardTotal(TeamId.TEAM_TWO))

        // timeout-countdown is a game that was abandoned during a live-point timeout and
        // archived, leaving a countdown that should not become an expired pull coundown.
        // There are also some more obscure patterns with the unknown players getting cards,
        // which weren't covered in other games yet.
        val timeoutCountdown = loadMigratedFixture("v1.0", "timeout-countdown")
        val timeoutCountdownCurrentGame = timeoutCountdown.currentGame!!
        assertEquals(GamePhase.LIVE_POINT, timeoutCountdownCurrentGame.phase)
        assertNull(timeoutCountdownCurrentGame.countdown)
        assertFalse(timeoutCountdownCurrentGame.hasExpiredPullActions(timeoutCountdownCurrentGame.startEpoch))
        assertEquals(1, timeoutCountdownCurrentGame.teamOne.timeoutsUsedThisHalf)
        assertEquals(
            listOf(
                PlayerRecord("5", priorYellows = 1),
                PlayerRecord(
                    jerseyNumber = "",
                    playerName = "N/A (1)",
                    priorYellows = 1,
                    cards = listOf(InGamePlayerCardEvent(CardType.YELLOW, 0)),
                ),
                PlayerRecord(
                    jerseyNumber = "",
                    playerName = "N/A (2)",
                ),
            ),
            timeoutCountdownCurrentGame.teamOnePlayers,
        )
        assertEquals(
            listOf(
                PlayerRecord("6", priorYellows = 1),
                PlayerRecord("", "N/A (1)", priorReds = 1),
                PlayerRecord(
                    jerseyNumber = "",
                    playerName = "N/A (2)",
                    priorYellows = 1,
                    cards = listOf(
                        InGamePlayerCardEvent(CardType.YELLOW, 1),
                        InGamePlayerCardEvent(CardType.RED, 2),
                    ),
                ),
                PlayerRecord("", "N/A (3)"),
            ),
            timeoutCountdownCurrentGame.teamTwoPlayers,
        )
        assertEquals(
            2,
            timeoutCountdownCurrentGame.eventLog.count { entry ->
                entry.type == EventLogType.YELLOW_CARD &&
                    entry.team == TeamId.TEAM_ONE &&
                    entry.player == PlayerIdentity("", "N/A (1)")
            },
        )
        assertTrue(timeoutCountdownCurrentGame.eventLog.any { entry ->
            entry.type == EventLogType.YELLOW_CARD &&
                entry.team == TeamId.TEAM_ONE &&
                entry.player == PlayerIdentity("", "N/A (2)")
        })
        assertTrue(timeoutCountdownCurrentGame.eventLog.any { entry ->
            entry.type == EventLogType.YELLOW_CARD &&
                entry.team == TeamId.TEAM_TWO &&
                entry.player == PlayerIdentity("", "N/A (2)")
        })
        assertTrue(timeoutCountdownCurrentGame.eventLog.any { entry ->
            entry.type == EventLogType.RED_CARD &&
                entry.team == TeamId.TEAM_TWO &&
                entry.player == PlayerIdentity("", "N/A (1)")
        })
        assertTrue(timeoutCountdownCurrentGame.eventLog.any { entry ->
            entry.type == EventLogType.RED_CARD &&
                entry.team == TeamId.TEAM_TWO &&
                entry.player == PlayerIdentity("", "N/A (2)")
        })
        assertTrue(timeoutCountdownCurrentGame.eventLog.any { entry ->
            entry.type == EventLogType.RED_CARD &&
                entry.team == TeamId.TEAM_TWO &&
                entry.player == PlayerIdentity("", "N/A (3)")
        })

        // completed-archive is the same game as active-game, but ended and then archived.
        val completedArchive = loadMigratedFixture("v1.0", "completed-archive")
        assertEquals(2, completedArchive.archivedGames.size)
        val richArchive = completedArchive.archivedGames.first()
        assertEquals(ArchivedGameCategory.COMPLETED, richArchive.archiveCategory)
        assertEquals(GamePhase.GAME_OVER, richArchive.phase)
        assertEquals("Undo End game", richArchive.undoEntry?.label)
        val restoredFromEndGame = richArchive.undoLastAction()
        assertEquals(GamePhase.BETWEEN_POINTS, restoredFromEndGame.phase)
        assertNull(restoredFromEndGame.undoEntry)
        assertNotNull(restoredFromEndGame.redoEntry)
        assertEquals(1, restoredFromEndGame.teamOne.score)
        assertEquals(1, restoredFromEndGame.teamTwo.score)
        assertEquals(7, restoredFromEndGame.teamCardTotal(TeamId.TEAM_ONE))
        assertEquals(7, restoredFromEndGame.teamCardTotal(TeamId.TEAM_TWO))

        // completed-archive-1.0.0 is the same as completed-archive, but it was generated
        // using v1.0.0 code, rather than v1.0.1.
        // v1.0.0 did not persist the End game undo entry that v1.0.1 preserved, but
        // the completed archive game facts should normalize the same way.
        val completedArchiveV1_0_0 = loadMigratedFixture("v1.0", "completed-archive-1.0.0")
        assertNull(completedArchiveV1_0_0.archivedGames.first().undoEntry)
        assertEquals(
            completedArchive.archivedGames.map { game ->
                game.copy(undoEntry = null, redoEntry = null)
            },
            completedArchiveV1_0_0.archivedGames,
        )
    }

    /**
     * Verify every v1.1 fixture scenario loads into the current app model without startup
     * recovery and preserves the standard persisted workflow shapes.
     *
     * The asserted fixture values come from
     * `tools/persistence-fixtures/v1.1/PersistenceFixtureGeneratorTool.kt`.
     */
    @Test
    fun migrateFixturesFromV1_1() {
        // default-buckets was created with the defaults for everything.
        val defaultBuckets = loadMigratedFixture("v1.1", "default-buckets")
        assertNull(defaultBuckets.currentGame)
        assertFalse(defaultBuckets.hasSetupDraft)
        assertProfileAndSettings(
            defaultBuckets,
            Profile(),
            Settings(timingAlerts = TimingAlertPreferences(soundVolume = 0.5f)),
        )
        assertTrue(defaultBuckets.archivedGames.isEmpty())

        // setup-draft has changes made in the setup screen, but did not start a game yet.
        val setupDraft = loadMigratedFixture("v1.1", "setup-draft")
        assertTrue(setupDraft.hasSetupDraft)
        assertEquals(LocalDate.of(2026, 2, 14), setupDraft.setupGame.startDate)
        assertEquals(LocalTime.of(13, 45), setupDraft.setupGame.startTime)
        assertEquals(ZoneId.of("America/New_York"), setupDraft.setupGame.timeZone)
        assertEquals("Migration Invitational", setupDraft.setupGame.tournamentName)
        assertEquals(GameDivision.OPEN, setupDraft.setupGame.division)
        assertEquals("College", setupDraft.setupGame.level)
        assertEquals("Pool play", setupDraft.setupGame.gameContext)
        assertEquals(listOf("Mike Jarvis", "Casey Lee"), setupDraft.setupGame.observerNames)
        assertEquals("Field 7", setupDraft.setupGame.fieldName)
        assertEquals("Bees", setupDraft.setupGame.teamOne.name)
        assertEquals("Ferns", setupDraft.setupGame.teamTwo.name)
        assertEquals(TeamId.TEAM_TWO, setupDraft.setupGame.pullingTeam)
        assertEquals(FieldEnd.NEAR, setupDraft.setupGame.pullingFromEnd)

        // active-game is a still-active game with persisted profile, settings, and one
        // archived completed game.
        val activeGame = loadMigratedFixture("v1.1", "active-game")
        val currentState = activeGame.currentGame!!
        assertProfileAndSettings(activeGame, fixtureProfile(), fixtureSettings())
        assertSingleDingTimingCueSettings(activeGame.settings.timingAlerts)
        assertEquals(SetupMode.EDIT_CURRENT_GAME, activeGame.setupMode)
        assertEquals(GamePhase.BETWEEN_POINTS, currentState.phase)
        assertEquals(1, currentState.teamOne.score)
        assertEquals(1, currentState.teamTwo.score)
        assertEquals(2, currentState.teamOne.blueCards)
        assertEquals(1, currentState.teamOne.technicalFouls)
        assertEquals(1, currentState.teamTwo.blueCards)
        assertEquals(2, currentState.teamTwo.technicalFouls)
        assertEquals(1, currentState.teamOne.offsides)
        assertEquals(1, currentState.teamOne.falseStarts)
        assertEquals(1, currentState.teamOne.majorityPullViolations)
        assertEquals(1, currentState.teamOne.timeoutsUsedThisHalf)
        assertEquals(1, currentState.teamTwo.offsides)
        assertEquals(1, currentState.teamTwo.falseStarts)
        assertEquals(1, currentState.teamTwo.timeViolations)
        assertEquals(1, currentState.teamTwo.timeoutsUsedThisHalf)
        assertEquals(1, activeGame.archivedGames.size)
        assertEquals(ArchivedGameCategory.COMPLETED, activeGame.archivedGames.single().archiveCategory)

        // complete-current-game is a completed game that remains current, including the
        // Undo End game path that returns it to between-points state.
        val completeCurrentGame = loadMigratedFixture("v1.1", "complete-current-game")
        val completeCurrentState = completeCurrentGame.currentGame!!
        assertTrue(completeCurrentGame.archivedGames.isEmpty())
        assertEquals(GamePhase.GAME_OVER, completeCurrentState.phase)
        assertEquals("Undo End game", completeCurrentState.undoEntry?.label)
        val restoredCompleteCurrentGame = completeCurrentState.undoLastAction()
        assertEquals(GamePhase.BETWEEN_POINTS, restoredCompleteCurrentGame.phase)
        assertEquals("Undo Fixture blue card/tech adjustment", restoredCompleteCurrentGame.undoEntry?.label)
        assertNotNull(restoredCompleteCurrentGame.redoEntry)
        assertEquals(1, restoredCompleteCurrentGame.teamOne.score)
        assertEquals(1, restoredCompleteCurrentGame.teamTwo.score)

        // completed-archive is the same rich game as active-game, but ended and archived
        // alongside one short completed archive.
        val completedArchive = loadMigratedFixture("v1.1", "completed-archive")
        assertNull(completedArchive.currentGame)
        assertEquals(2, completedArchive.archivedGames.size)
        val richArchive = completedArchive.archivedGames.first()
        assertEquals(ArchivedGameCategory.COMPLETED, richArchive.archiveCategory)
        assertEquals(GamePhase.GAME_OVER, richArchive.phase)
        assertEquals("Undo End game", richArchive.undoEntry?.label)
        val restoredFromEndGame = richArchive.undoLastAction()
        assertEquals(GamePhase.BETWEEN_POINTS, restoredFromEndGame.phase)
        assertNull(restoredFromEndGame.undoEntry)
        assertNotNull(restoredFromEndGame.redoEntry)
        assertEquals(1, restoredFromEndGame.teamOne.score)
        assertEquals(1, restoredFromEndGame.teamTwo.score)
    }

    private fun loadMigratedFixture(version: String, scenario: String): AppViewModel {
        val storeDir = temporaryFolder.newFolder()
        fixtureDir(version, scenario).copyRecursively(storeDir, overwrite = true)
        val viewModel = AppViewModel(FileAppStateStorage(storeDir))
        assertNull(viewModel.startupRecoveryNotice)
        return viewModel
    }

    private fun fixtureDir(version: String, scenario: String): File {
        val resource = javaClass.classLoader!!.getResource(
            "persistence-fixtures/$version/$scenario"
        ) ?: error("Missing persistence fixture $version/$scenario")
        return Paths.get(resource.toURI()).toFile()
    }

    private fun assertProfileAndSettings(
        viewModel: AppViewModel,
        expectedProfile: Profile,
        expectedSettings: Settings,
    ) {
        assertEquals(expectedProfile.name, viewModel.profile.name)
        assertEquals(expectedProfile.avatarPreference, viewModel.profile.avatarPreference)
        assertEquals(
            expectedSettings.automaticallyAdvanceCountdowns,
            viewModel.settings.automaticallyAdvanceCountdowns,
        )
        assertEquals(
            expectedSettings.automaticallyLockLivePoint,
            viewModel.settings.automaticallyLockLivePoint,
        )
        assertEquals(expectedSettings.showDefenseCountdowns, viewModel.settings.showDefenseCountdowns)
        assertEquals(
            expectedSettings.showAbbaRatioAsSequence,
            viewModel.settings.showAbbaRatioAsSequence,
        )
        assertEquals(expectedSettings.timingAlerts, viewModel.settings.timingAlerts)
    }

    private fun fixtureProfile(): Profile {
        return Profile(
            name = "Casey Observer",
            avatarPreference = ObserverAvatarPreference.BLUE,
        )
    }

    private fun fixtureSettings(): Settings {
        return Settings(
            automaticallyAdvanceCountdowns = false,
            automaticallyLockLivePoint = false,
            showDefenseCountdowns = true,
            timingAlerts = TimingAlertPreferences(
                globalMode = TimingAlertGlobalMode.SOUNDS_ON,
                soundVolume = 0.35f,
                vibrationDurationMillis = 250L,
                vibrateWithSounds = true,
                cueModes = TimingCueId.entries.associateWith { TimingAlertMode.DING },
                cueRepeatCounts = TimingCueId.entries.associateWith { 1 },
            ),
        )
    }

    private fun assertSingleDingTimingCueSettings(timingAlertPreferences: TimingAlertPreferences) {
        TimingCueId.entries.forEach { cueId ->
            assertEquals(TimingAlertMode.DING, timingAlertPreferences.cueModes[cueId])
            assertEquals(1, timingAlertPreferences.cueRepeatCounts[cueId])
        }
    }

}
