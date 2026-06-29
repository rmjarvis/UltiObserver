package rmjarvis.ultiobserver

import java.io.File
import java.nio.file.Paths
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
        assertNull(defaultBuckets.liveState)
        assertFalse(defaultBuckets.hasSetupDraft)
        assertEquals("", defaultBuckets.profileName)
        assertTrue(defaultBuckets.archivedGames.isEmpty())

        // setup-draft has changes made in the setup screen, but didn't start a game yet.
        // The main tricky thing here is the different way the prior cards were stored.
        val setupDraft = loadMigratedFixture("v1.0", "setup-draft")
        assertTrue(setupDraft.hasSetupDraft)
        assertEquals("Migration Invitational", setupDraft.setupState.tournamentName)
        assertEquals("Bees", setupDraft.setupState.teamOne.name)
        assertEquals("Ferns", setupDraft.setupState.teamTwo.name)
        assertEquals(TeamId.TEAM_TWO, setupDraft.setupState.pullingTeam)
        assertEquals(FieldEnd.NEAR, setupDraft.setupState.pullingFromEnd)
        assertEquals(PullPromptTarget.NEAR, setupDraft.setupState.pullPromptTarget)
        assertEquals(GenderRatio.FOUR_MEN_THREE_WOMEN, setupDraft.setupState.initialGenderRatio)
        assertEquals(FieldEnd.FAR, setupDraft.setupState.firstHalfGenZone)
        assertTrue(setupDraft.setupState.switchGenZoneAtHalftime)
        assertEquals(
            listOf(
                PlayerRecord("7", priorYellows = 1),
                PlayerRecord("", "N/A (1)", priorReds = 1),
                PlayerRecord("", "N/A (2)", priorYellows = 1),
            ),
            setupDraft.setupState.teamOnePlayers,
        )
        assertEquals(
            listOf(
                PlayerRecord("12", priorReds = 1),
                PlayerRecord("", "N/A (1)", priorYellows = 1),
                PlayerRecord("", "N/A (2)", priorReds = 1),
            ),
            setupDraft.setupState.teamTwoPlayers,
        )

        // active-game is a still-active game with some of just about every kind of action
        // possible.  The hardest part for this one is dealing with the player cards, which
        // was done very differently in version 1.0. Especially there was the option for a
        // player number to be "unknown".  We convert these unknown player numbers into
        // N/A (1), N/A (2), etc. in the name field.  Or just N/A if there is only one.
        val activeGame = loadMigratedFixture("v1.0", "active-game")
        val liveState = activeGame.liveState!!
        assertEquals("Casey Observer", activeGame.profileName)
        assertEquals(ObserverAvatarPreference.BLUE, activeGame.avatarPreference)
        assertFalse(activeGame.automaticallyAdvanceCountdowns)
        assertFalse(activeGame.automaticallyLockLivePoint)
        assertEquals(TimingAlertGlobalMode.SOUNDS_ON, activeGame.timingAlertPreferences.globalMode)
        assertEquals(0.35f, activeGame.timingAlertPreferences.soundVolume, 0.0001f)
        assertEquals(250L, activeGame.timingAlertPreferences.vibrationDurationMillis)
        assertTrue(activeGame.timingAlertPreferences.vibrateWithSounds)
        assertEquals(SetupMode.EDIT_CURRENT_GAME, activeGame.setupMode)
        assertNull(liveState.undoEntry)
        assertNull(liveState.redoEntry)
        assertNull(liveState.countdown)
        assertTrue(liveState.pullCountdownExpired)
        assertEquals(1, activeGame.archivedGames.size)
        assertEquals(TeamId.TEAM_ONE, liveState.teamDefendingEnd(FieldEnd.FAR))
        assertEquals(TeamId.TEAM_TWO, liveState.pullingTeam)
        assertEquals(FieldEnd.NEAR, liveState.pullingFromEnd)
        assertEquals(PullPromptTarget.NEAR, liveState.pullPromptTarget)
        assertEquals(TeamId.TEAM_TWO, liveState.openingPullingTeam)
        assertEquals(FieldEnd.NEAR, liveState.openingPullingFromEnd)
        assertEquals(1, liveState.teamOne.score)
        assertEquals(1, liveState.teamTwo.score)
        assertEquals(2, liveState.teamOne.blueCards)
        assertEquals(1, liveState.teamOne.technicalFouls)
        assertEquals(1, liveState.teamTwo.blueCards)
        assertEquals(2, liveState.teamTwo.technicalFouls)
        assertEquals(1, liveState.teamOne.offsides)
        assertEquals(1, liveState.teamOne.falseStarts)
        assertEquals(1, liveState.teamOne.timeoutsUsedThisHalf)
        assertEquals(1, liveState.teamTwo.offsides)
        assertEquals(1, liveState.teamTwo.falseStarts)
        assertEquals(1, liveState.teamTwo.timeoutsUsedThisHalf)
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
            liveState.teamOnePlayers,
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
            liveState.teamTwoPlayers,
        )
        assertTrue(liveState.eventLog.any { entry ->
            entry.type == EventLogType.YELLOW_CARD &&
                entry.player == PlayerIdentity("", "N/A (2)")
        })
        assertEquals(2, liveState.eventLog.count { it.type == EventLogType.OFFSIDES })
        assertEquals(2, liveState.eventLog.count { it.type == EventLogType.FALSE_START })
        assertEquals(2, liveState.eventLog.count { it.type == EventLogType.TIMEOUT })

        // setup-saved was a game that just got past the setup stage before being archived
        // and restored as the current game.
        val setupSaved = loadMigratedFixture("v1.0", "setup-saved")
        val setupSavedLiveState = setupSaved.liveState!!
        assertEquals(GamePhase.PRE_GAME, setupSavedLiveState.phase)
        assertNull(setupSavedLiveState.undoEntry)
        assertNull(setupSavedLiveState.countdown)
        assertTrue(setupSavedLiveState.pullCountdownExpired)
        assertEquals(1, setupSaved.archivedGames.size)
        val setupSavedArchive = setupSaved.archivedGames.single()
        assertEquals(ArchivedGameCategory.IN_PROGRESS, setupSavedArchive.category)
        assertEquals(GamePhase.PRE_GAME, setupSavedArchive.state.phase)
        assertEquals("Simple One", setupSavedArchive.state.teamOne.name)
        assertEquals("Simple Two", setupSavedArchive.state.teamTwo.name)
        assertNull(setupSavedArchive.state.countdown)
        assertTrue(setupSavedArchive.state.pullCountdownExpired)
        assertTrue(setupSavedArchive.state.teamOnePlayers.isEmpty())
        assertTrue(setupSavedArchive.state.teamTwoPlayers.isEmpty())
        assertTrue(setupSavedArchive.state.eventLog.isEmpty())

        // complete-current-game is a game that has finished, but not been archived.
        // So it still has an Undo End game option to take it back to a live game state.
        // It also exercises a few different paths for unknown players than the other
        // games do.
        val completeCurrentGame = loadMigratedFixture("v1.0", "complete-current-game")
        val completeCurrentLiveState = completeCurrentGame.liveState!!
        assertEquals(GamePhase.GAME_OVER, completeCurrentLiveState.phase)
        assertEquals("Undo End game", completeCurrentLiveState.undoEntry?.label)
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
            completeCurrentLiveState.teamOnePlayers,
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
            completeCurrentLiveState.teamTwoPlayers,
        )
        assertEquals(
            2,
            completeCurrentLiveState.eventLog.count { entry ->
                entry.type == EventLogType.YELLOW_CARD &&
                    entry.player == PlayerIdentity("", "N/A")
            },
        )
        assertTrue(completeCurrentLiveState.eventLog.any { entry ->
            entry.type == EventLogType.RED_CARD &&
                entry.player == PlayerIdentity("", "N/A (1)")
        })
        assertEquals(
            2,
            completeCurrentLiveState.eventLog.count { entry ->
                entry.type == EventLogType.YELLOW_CARD &&
                    entry.player == PlayerIdentity("", "N/A (2)")
            },
        )
        assertFalse(completeCurrentLiveState.eventLog.any { entry ->
            entry.type == EventLogType.RED_CARD &&
                entry.player == PlayerIdentity("", "N/A (2)")
        })
        val restoredCompleteCurrentGame = completeCurrentLiveState.undoLastAction()
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
        val timeoutCountdownLiveState = timeoutCountdown.liveState!!
        assertEquals(GamePhase.LIVE_POINT, timeoutCountdownLiveState.phase)
        assertNull(timeoutCountdownLiveState.countdown)
        assertFalse(timeoutCountdownLiveState.pullCountdownExpired)
        assertEquals(1, timeoutCountdownLiveState.teamOne.timeoutsUsedThisHalf)
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
            timeoutCountdownLiveState.teamOnePlayers,
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
            timeoutCountdownLiveState.teamTwoPlayers,
        )
        assertEquals(
            2,
            timeoutCountdownLiveState.eventLog.count { entry ->
                entry.type == EventLogType.YELLOW_CARD &&
                    entry.team == TeamId.TEAM_ONE &&
                    entry.player == PlayerIdentity("", "N/A (1)")
            },
        )
        assertTrue(timeoutCountdownLiveState.eventLog.any { entry ->
            entry.type == EventLogType.YELLOW_CARD &&
                entry.team == TeamId.TEAM_ONE &&
                entry.player == PlayerIdentity("", "N/A (2)")
        })
        assertTrue(timeoutCountdownLiveState.eventLog.any { entry ->
            entry.type == EventLogType.YELLOW_CARD &&
                entry.team == TeamId.TEAM_TWO &&
                entry.player == PlayerIdentity("", "N/A (2)")
        })
        assertTrue(timeoutCountdownLiveState.eventLog.any { entry ->
            entry.type == EventLogType.RED_CARD &&
                entry.team == TeamId.TEAM_TWO &&
                entry.player == PlayerIdentity("", "N/A (1)")
        })
        assertTrue(timeoutCountdownLiveState.eventLog.any { entry ->
            entry.type == EventLogType.RED_CARD &&
                entry.team == TeamId.TEAM_TWO &&
                entry.player == PlayerIdentity("", "N/A (2)")
        })
        assertTrue(timeoutCountdownLiveState.eventLog.any { entry ->
            entry.type == EventLogType.RED_CARD &&
                entry.team == TeamId.TEAM_TWO &&
                entry.player == PlayerIdentity("", "N/A (3)")
        })

        // completed-archive is the same game as active-game, but ended and then archived.
        val completedArchive = loadMigratedFixture("v1.0", "completed-archive")
        assertEquals(2, completedArchive.archivedGames.size)
        val richArchive = completedArchive.archivedGames.first()
        assertEquals(ArchivedGameCategory.COMPLETED, richArchive.category)
        assertEquals("Generated v1.0 rich game", richArchive.summaryContext)
        assertEquals(GamePhase.GAME_OVER, richArchive.state.phase)
        assertEquals("Undo End game", richArchive.state.undoEntry?.label)
        val restoredFromEndGame = richArchive.state.undoLastAction()
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
        assertNull(completedArchiveV1_0_0.archivedGames.first().state.undoEntry)
        assertEquals(
            completedArchive.archivedGames.map { game ->
                game.copy(state = game.state.copy(undoEntry = null, redoEntry = null))
            },
            completedArchiveV1_0_0.archivedGames,
        )
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

}
