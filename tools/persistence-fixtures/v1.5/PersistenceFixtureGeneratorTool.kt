package rmjarvis.ultiobserver

import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/** Generate genuine 1.5 saves, including permanent identities and patch-based history. */
fun main(args: Array<String>) {
    require(args.size == 2) {
        "Usage: PersistenceFixtureGeneratorToolKt <scenario> <output-dir>"
    }
    val scenario = args[0]
    val directory = File(args[1])
    directory.deleteRecursively()
    directory.mkdirs()
    val storage = FileAppStateStorage(directory)
    val defaultBuckets = scenario == "default-buckets" || scenario == "started-game-no-events"
    storage.saveProfile(if (defaultBuckets) Profile() else Profile(name = "Casey Observer"))
    storage.saveSettings(if (defaultBuckets) Settings() else Settings(
        orientationPreference = OrientationPreference.LANDSCAPE,
        dualPullCountdowns = true,
        requireWatchLongPress = true,
        watchOrientation = WatchOrientation.ENDS_FIXED,
        timingAlerts = TimingAlertPreferences(watchConnectionMode = WatchConnectionMode.WEAR_OS),
    ))
    when (scenario) {
        "default-buckets" -> {
            storage.saveCurrentGame(null)
            storage.saveArchivedGames(emptyList())
        }
        "started-game-no-events" -> {
            storage.saveCurrentGame(fixtureSetup().startGame(OrientationPreference.PORTRAIT))
            storage.saveArchivedGames(emptyList())
        }
        "setup-draft" -> {
            storage.saveCurrentGame(fixtureSetup())
            storage.saveArchivedGames(emptyList())
        }
        "active-game" -> {
            storage.saveCurrentGame(fixtureActiveGame())
            storage.saveArchivedGames(listOf(fixtureCompletedGame().pruneUndoHistory()))
        }
        "complete-current-game" -> {
            storage.saveCurrentGame(fixtureActiveGame().endGameNow(fixtureEpoch + 180_000L))
            storage.saveArchivedGames(emptyList())
        }
        "completed-archive" -> {
            storage.saveCurrentGame(null)
            storage.saveArchivedGames(listOf(
                fixtureActiveGame().endGameNow(fixtureEpoch + 180_000L).pruneUndoHistory(),
                fixtureCompletedGame().pruneUndoHistory(),
            ))
        }
        else -> error("Unknown persistence fixture scenario: $scenario")
    }
}

private val fixtureEpoch = epochTimestamp(
    LocalDate.of(2026, 10, 13), LocalTime.of(10, 0), ZoneId.of("America/New_York"),
)

private fun fixtureSetup(): GameState {
    return newSetupGameState(fixtureEpoch).copy(
        timeZone = ZoneId.of("America/New_York"),
        tournamentName = "Migration Invitational",
        nearEndName = "Road",
        farEndName = "Trees",
        watchLeftEnd = FieldEnd.NEAR,
        teamOne = TeamState("Bees", TeamColorChoice.YELLOW),
        teamTwo = TeamState("Ferns", TeamColorChoice.GREEN),
    ).withGameId("00000000-0000-4000-8000-000000000015")
}

private fun fixtureActiveGame(): GameState {
    return fixtureSetup().startGame(OrientationPreference.LANDSCAPE)
        .beginLivePoint(fixtureEpoch + 1_000L)
        .assessYellowCard(
            team = TeamId.TEAM_ONE,
            identity = PlayerIdentity("7", "Bee Seven"),
            now = fixtureEpoch + 2_000L,
            reason = CardReason(preset = "Dangerous play"),
        ).state
        .recordGoal(TeamId.TEAM_ONE, fixtureEpoch + 60_000L)
        .recordGoal(TeamId.TEAM_TWO, fixtureEpoch + 120_000L)
}

private fun fixtureCompletedGame(): GameState {
    return fixtureSetup().withGameId("00000000-0000-4000-8000-000000000025")
        .startGame(OrientationPreference.LANDSCAPE)
        .beginLivePoint(fixtureEpoch + 1_000L)
        .recordGoal(TeamId.TEAM_TWO, fixtureEpoch + 60_000L)
        .endGameNow(fixtureEpoch + 70_000L)
}
