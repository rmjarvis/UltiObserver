package rmjarvis.ultiobserver

import java.io.File
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

fun main(args: Array<String>) {
    require(args.size == 2) {
        "Usage: PersistenceFixtureGeneratorToolKt <scenario> <output-dir>"
    }
    val scenario = args[0]
    val root = File(args[1])
    when (scenario) {
        "default-buckets" -> writeDefaultBuckets(root)
        "setup-draft" -> writeSetupDraft(root)
        "active-game" -> writeActiveGame(root)
        "completed-archive" -> writeCompletedArchive(root)
        else -> error("Unknown persistence fixture scenario: $scenario")
    }
}

private fun writeDefaultBuckets(dir: File) {
    val store = freshStore(dir)
    store.saveCurrentGameState(CurrentGameSnapshot())
    store.saveProfile(Profile())
    store.saveSettings(Settings())
    store.saveArchivedGames(emptyList())
}

private fun writeSetupDraft(dir: File) {
    val store = freshStore(dir)
    store.saveCurrentGameState(
        CurrentGameSnapshot(
            setupState = nonDefaultSetup(),
            setupMode = SetupMode.NEW_GAME,
            hasSetupDraft = true,
        )
    )
    store.saveProfile(fixtureProfile())
    store.saveSettings(fixtureSettings())
    store.saveArchivedGames(emptyList())
}

private fun writeActiveGame(dir: File) {
    val store = freshStore(dir)
    val setup = nonDefaultSetup()
    val game = activeGameWithEvents(setup)

    store.saveCurrentGameState(
        CurrentGameSnapshot(
            setupState = setup,
            liveState = game,
            setupMode = SetupMode.EDIT_CURRENT_GAME,
        )
    )
    store.saveProfile(fixtureProfile())
    store.saveSettings(fixtureSettings())
    store.saveArchivedGames(
        listOf(
            ArchivedGame(
                state = shortCompletedGame().pruneUndoHistory(),
                subtitle = "Generated short v1.0 game",
            ),
        )
    )
}

private fun writeCompletedArchive(dir: File) {
    val store = freshStore(dir)
    val richSetup = nonDefaultSetup()
    val richGame = activeGameWithEvents(richSetup)
        .endGameNow(setupEpoch(richSetup) + 180_000L)

    store.saveCurrentGameState(CurrentGameSnapshot())
    store.saveProfile(fixtureProfile())
    store.saveSettings(fixtureSettings())
    store.saveArchivedGames(
        listOf(
            ArchivedGame(
                state = richGame.pruneUndoHistory(),
                subtitle = "Generated v1.0 rich game",
            ),
            ArchivedGame(
                state = shortCompletedGame().pruneUndoHistory(),
                subtitle = "Generated v1.0 short game",
            ),
        )
    )
}

private fun freshStore(dir: File): FileAppStateStorage {
    dir.deleteRecursively()
    dir.mkdirs()
    return FileAppStateStorage(dir)
}

private fun baseSetup(): GameSetupState {
    return GameSetupState(
        startDate = LocalDate.of(2026, 1, 3),
        startTime = LocalTime.of(9, 30),
        timeZone = ZoneId.of("America/New_York"),
        tournamentName = "Migration Classic",
        rules = GameRules(
            gameTo = 5,
            useHalfCap = false,
            useSoftCap = false,
            useHardCap = false,
            timeoutsPerHalf = 2,
            hasFloaterTimeout = true,
        ),
        teamOne = TeamSetup("Viscous Coupling", TeamColorChoice.WHITE),
        teamTwo = TeamSetup("Animal", TeamColorChoice.RED),
        pullingTeam = TeamId.TEAM_ONE,
        pullingFromEnd = FieldEnd.FAR,
    )
}

private fun nonDefaultSetup(): GameSetupState {
    return baseSetup().copy(
        startDate = LocalDate.of(2026, 2, 14),
        startTime = LocalTime.of(13, 45),
        tournamentName = "Migration Invitational",
        rules = GameRules(
            gameTo = 11,
            halftimeMinutes = 10,
            useHalfCap = true,
            halfCapMinutes = 50,
            useSoftCap = true,
            softCapMinutes = 80,
            useHardCap = true,
            hardCapMinutes = 95,
            timeoutsPerHalf = 1,
            hasFloaterTimeout = false,
        ),
        teamOne = TeamSetup("Bees", TeamColorChoice.YELLOW),
        teamTwo = TeamSetup("Ferns", TeamColorChoice.GREEN),
        priorCards = listOf(
            PlayerCardRecord(TeamId.TEAM_ONE, "7", priorYellows = 1, priorReds = 0),
            PlayerCardRecord(TeamId.TEAM_ONE, UNKNOWN_PLAYER_NUMBER, priorYellows = 1, priorReds = 0),
            PlayerCardRecord(TeamId.TEAM_ONE, UNKNOWN_PLAYER_NUMBER, priorYellows = 0, priorReds = 1),
            PlayerCardRecord(TeamId.TEAM_TWO, "12", priorYellows = 0, priorReds = 1),
            PlayerCardRecord(TeamId.TEAM_TWO, UNKNOWN_PLAYER_NUMBER, priorYellows = 0, priorReds = 1),
        ),
        pullingTeam = TeamId.TEAM_TWO,
        pullingFromEnd = FieldEnd.NEAR,
    )
}

private fun activeGameWithEvents(setup: GameSetupState): GameState {
    val start = setupEpoch(setup)
    var game = createLiveGameState(setup)
    game = game.recordFalseStart(start + 1_000L)
    game = game.recordOffsides(start + 2_000L)
    game = game.assessYellowCard(TeamId.TEAM_ONE, "7", start + 3_000L).state
    game = game.assessYellowCard(TeamId.TEAM_ONE, UNKNOWN_PLAYER_NUMBER, start + 3_500L).state
    game = game.assessSecondYellowCard(TeamId.TEAM_ONE, UNKNOWN_PLAYER_NUMBER, start + 3_750L).state
    game = game.assessRedCard(TeamId.TEAM_TWO, "12", start + 4_000L).state
    game = game.assessRedCard(TeamId.TEAM_TWO, UNKNOWN_PLAYER_NUMBER, start + 4_500L).state
    game = game.assessBlueCard(TeamId.TEAM_ONE, start + 5_000L).state
    game = game.chargeTimeout(TeamId.TEAM_TWO, start + 6_000L)
    game = game.recordGoal(TeamId.TEAM_ONE, start + 60_000L)
    game = game.recordFalseStart(start + 70_000L)
    game = game.recordOffsides(start + 80_000L)
    game = game.assessTechnicalFoul(TeamId.TEAM_TWO, start + 90_000L).state
    game = game.recordGoal(TeamId.TEAM_TWO, start + 120_000L)
    game = game.chargeTimeout(TeamId.TEAM_ONE, start + 130_000L)
    game = game.adjustPullInfractions(
        teamOneOffsides = 1,
        teamOneFalseStarts = 1,
        teamTwoOffsides = 1,
        teamTwoFalseStarts = 1,
        now = start + 140_000L,
    )
    game = game.adjustTimeouts(
        teamOneTimeoutsUsed = 1,
        teamTwoTimeoutsUsed = 1,
        now = start + 150_000L,
    )
    game = game.adjustCardsAndTf(
        teamOneBlues = 1,
        teamOneTechnicalFouls = 0,
        teamTwoBlues = 0,
        teamTwoTechnicalFouls = 1,
        teamOnePlayerCards = listOf(
            InGamePlayerCardRecord("7", yellows = 1),
            InGamePlayerCardRecord(UNKNOWN_PLAYER_NUMBER, yellows = 2),
            InGamePlayerCardRecord("22", reds = 1),
        ),
        teamTwoPlayerCards = listOf(
            InGamePlayerCardRecord("12", reds = 1),
            InGamePlayerCardRecord(UNKNOWN_PLAYER_NUMBER, reds = 1),
            InGamePlayerCardRecord("27", yellows = 2),
        ),
        now = start + 160_000L,
    )
    game = game.adjustCardsAndTf(
        teamOneBlues = 2,
        teamOneTechnicalFouls = 1,
        teamTwoBlues = 1,
        teamTwoTechnicalFouls = 2,
        teamOnePlayerCards = game.teamOnePlayerCards,
        teamTwoPlayerCards = game.teamTwoPlayerCards,
        now = start + 170_000L,
    )
    return game
}

private fun shortCompletedGame(): GameState {
    val setup = baseSetup()
    val start = setupEpoch(setup)
    var game = createLiveGameState(setup).beginLivePoint(start + 1_000L)
    game = game.recordGoal(TeamId.TEAM_ONE, start + 60_000L)
    return game.endGameNow(start + 70_000L)
}

private fun fixtureProfile(): Profile {
    return Profile(
        profileName = "Casey Observer",
        avatarPreference = ObserverAvatarPreference.BLUE,
    )
}

private fun fixtureSettings(): Settings {
    return Settings(
        automaticallyAdvanceCountdowns = false,
        automaticallyLockLivePoint = false,
        timingAlertPreferences = TimingAlertPreferences(
            globalMode = TimingAlertGlobalMode.SOUNDS_ON,
            soundVolume = 0.35f,
            vibrationDurationMillis = 250L,
            vibrateWithSounds = true,
        ),
    )
}

private fun setupEpoch(setup: GameSetupState): Long {
    return epochTimestamp(setup.startDate, setup.startTime, setup.timeZone)
}
