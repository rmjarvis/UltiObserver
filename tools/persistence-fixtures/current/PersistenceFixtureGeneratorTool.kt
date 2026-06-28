package rmjarvis.ultiobserver

import java.io.File
import java.time.LocalDate
import java.time.LocalDateTime
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
    store.saveCurrentGameState(defaultCurrentGameSnapshot())
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
    val setup = nonDefaultSetup().copy(division = GameDivision.MIXED)
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
                subtitle = "Generated short game from current code",
            ),
        )
    )
}

private fun writeCompletedArchive(dir: File) {
    val store = freshStore(dir)
    val richSetup = nonDefaultSetup().copy(division = GameDivision.MIXED)
    val richGame = activeGameWithEvents(richSetup)
        .endGameNow(setupEpoch(richSetup) + 180_000L)

    store.saveCurrentGameState(defaultCurrentGameSnapshot())
    store.saveProfile(fixtureProfile())
    store.saveSettings(fixtureSettings())
    store.saveArchivedGames(
        listOf(
            ArchivedGame(
                state = richGame.pruneUndoHistory(),
                subtitle = "Generated rich game from current code",
            ),
            ArchivedGame(
                state = shortCompletedGame().pruneUndoHistory(),
                subtitle = "Generated short game from current code",
            ),
        )
    )
}

private fun freshStore(dir: File): FileAppStateStorage {
    dir.deleteRecursively()
    dir.mkdirs()
    return FileAppStateStorage(dir)
}

private fun defaultCurrentGameSnapshot(): CurrentGameSnapshot {
    return CurrentGameSnapshot(
        setupState = newGameSetupState(
            now = LocalDateTime.of(2026, 6, 27, 16, 1),
        ).copy(
            timeZone = ZoneId.of("America/New_York"),
        ),
    )
}

private fun baseSetup(): GameSetupState {
    return GameSetupState(
        startDate = LocalDate.of(2026, 1, 3),
        startTime = LocalTime.of(9, 30),
        timeZone = ZoneId.of("America/New_York"),
        tournamentName = "Migration Classic",
        division = GameDivision.MIXED,
        level = "Club",
        gameContext = "Semifinal",
        observers = "Casey / Morgan",
        nearEndName = "Pavilion",
        farEndName = "Oak",
        rules = GameRules(
            gameTo = 5,
            useHalfCap = false,
            useSoftCap = false,
            useHardCap = false,
            timeoutsPerHalf = 2,
            hasFloaterTimeout = true,
            genderRatioRule = GenderRatioRule.GEN_ZONE,
            useMajorityPullRule = true,
        ),
        teamOne = TeamSetup(
            name = "Viscous Coupling",
            color = TeamColorChoice.CUSTOM,
            customColorArgb = 0xFF1E88E5,
            coaches = "Pat Coach",
            fieldCaptains = "Alex Seven",
            spiritCaptains = "Sam Spirit",
        ),
        teamTwo = TeamSetup(
            name = "Animal",
            color = TeamColorChoice.RED,
            coaches = "Jordan Coach",
            fieldCaptains = "Riley Twelve",
            spiritCaptains = "Taylor Spirit",
        ),
        teamOnePlayers = listOf(
            PlayerRecord("7", "Alex Seven", priorYellows = 1),
            PlayerRecord("44", "Morgan Fortyfour", priorReds = 1),
        ),
        teamTwoPlayers = listOf(
            PlayerRecord("12", "Riley Twelve", priorYellows = 1),
            PlayerRecord("", "Name Only", priorYellows = 0, priorReds = 1),
        ),
        pullingTeam = TeamId.TEAM_ONE,
        pullingFromEnd = FieldEnd.FAR,
        pullPromptTarget = PullPromptTarget.BOTH,
        initialGenderRatio = GenderRatio.FOUR_WOMEN_THREE_MEN,
        firstHalfGenZone = FieldEnd.NEAR,
        switchGenZoneAtHalftime = false,
    )
}

private fun nonDefaultSetup(): GameSetupState {
    return baseSetup().copy(
        startDate = LocalDate.of(2026, 2, 14),
        startTime = LocalTime.of(13, 45),
        tournamentName = "Migration Invitational",
        division = GameDivision.OPEN,
        level = "College",
        gameContext = "Pool play",
        observers = "Mike Jarvis",
        nearEndName = "Tent",
        farEndName = "Scoreboard",
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
            genderRatioRule = GenderRatioRule.ABBA,
            useMajorityPullRule = true,
        ),
        teamOne = TeamSetup(
            name = "Bees",
            color = TeamColorChoice.YELLOW,
            coaches = "Bee Coach",
            fieldCaptains = "Bee Captain",
            spiritCaptains = "Bee Spirit",
        ),
        teamTwo = TeamSetup(
            name = "Ferns",
            color = TeamColorChoice.GREEN,
            coaches = "Fern Coach",
            fieldCaptains = "Fern Captain",
            spiritCaptains = "Fern Spirit",
        ),
        teamOnePlayers = listOf(
            PlayerRecord("7", "Bee Seven", priorYellows = 1),
            PlayerRecord("18", priorReds = 1),
        ),
        teamTwoPlayers = listOf(
            PlayerRecord("12", "Fern Twelve", priorYellows = 1),
            PlayerRecord("27", "Fern Twentyseven", priorYellows = 2),
        ),
        pullingTeam = TeamId.TEAM_TWO,
        pullingFromEnd = FieldEnd.NEAR,
        pullPromptTarget = PullPromptTarget.FAR,
        initialGenderRatio = GenderRatio.FOUR_MEN_THREE_WOMEN,
        firstHalfGenZone = FieldEnd.FAR,
        switchGenZoneAtHalftime = true,
    )
}

private fun activeGameWithEvents(setup: GameSetupState): GameState {
    val start = setupEpoch(setup)
    var game = createLiveGameState(setup)
    game = game.recordFalseStart(start + 1_000L)
    game = game.recordMajorityPullViolation(start + 2_000L)
    game = game.assessYellowCard(
        team = TeamId.TEAM_ONE,
        jerseyNumber = "7",
        now = start + 3_000L,
        playerName = "Bee Seven",
        reason = CardReason(preset = "Dangerous play"),
    ).state
    game = game.assessRedCard(
        team = TeamId.TEAM_TWO,
        jerseyNumber = "12",
        now = start + 4_000L,
        playerName = "Fern Twelve",
        reason = CardReason(preset = "Other", otherText = "Fixture reason"),
    ).state
    game = game.assessBlueCard(TeamId.TEAM_ONE, start + 5_000L).state
    game = game.chargeTimeout(TeamId.TEAM_TWO, start + 6_000L)
    game = game.recordGoal(TeamId.TEAM_ONE, start + 60_000L)
    game = game.recordOffsides(start + 80_000L)
    game = game.assessTechnicalFoul(TeamId.TEAM_TWO, start + 90_000L).state
    game = game.recordGoal(TeamId.TEAM_TWO, start + 120_000L)
    game = game.chargeTimeout(TeamId.TEAM_ONE, start + 130_000L)
    game = game.adjustPullViolations(
        teamOneOffsides = 1,
        teamOneFalseStarts = 1,
        teamOneMajorityPulls = 1,
        teamTwoOffsides = 1,
        teamTwoFalseStarts = 1,
        teamTwoMajorityPulls = 0,
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
        teamOnePlayers = listOf(
            PlayerRecord(
                jerseyNumber = "7",
                playerName = "Bee Seven Renamed",
                priorYellows = 1,
                cards = listOf(
                    InGamePlayerCardEvent(
                        cardType = CardType.YELLOW,
                        index = 0,
                        reason = CardReason(preset = "Dangerous play"),
                    )
                ),
            ),
            PlayerRecord("18", priorReds = 1),
            PlayerRecord(
                jerseyNumber = "",
                playerName = "Name Only Yellow",
                cards = listOf(
                    InGamePlayerCardEvent(
                        cardType = CardType.YELLOW,
                        index = 2,
                        reason = CardReason(details = "Name-only fixture card"),
                    )
                ),
            ),
        ),
        teamTwoPlayers = listOf(
            PlayerRecord(
                jerseyNumber = "12",
                playerName = "Fern Twelve",
                priorYellows = 1,
                cards = listOf(
                    InGamePlayerCardEvent(
                        cardType = CardType.RED,
                        index = 1,
                        reason = CardReason(preset = "Other", otherText = "Fixture reason"),
                    )
                ),
            ),
            PlayerRecord("27", "Fern Twentyseven", priorYellows = 2),
        ),
        now = start + 160_000L,
        undoLabel = "Undo Fixture card adjustment",
    )
    game = game.adjustCardsAndTf(
        teamOneBlues = 2,
        teamOneTechnicalFouls = 1,
        teamTwoBlues = 1,
        teamTwoTechnicalFouls = 2,
        teamOnePlayers = game.teamOnePlayers,
        teamTwoPlayers = game.teamTwoPlayers,
        now = start + 170_000L,
        undoLabel = "Undo Fixture blue card/tech adjustment",
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
        showDefenseCountdowns = true,
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
