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
        "complete-current-game" -> writeCompleteCurrentGame(root)
        "completed-archive" -> writeCompletedArchive(root)
        else -> error("Unknown persistence fixture scenario: $scenario")
    }
}

private fun writeDefaultBuckets(dir: File) {
    val store = freshStore(dir)
    store.saveCurrentGame(null)
    store.saveProfile(Profile())
    store.saveSettings(Settings())
    store.saveArchivedGames(emptyList())
}

private fun writeSetupDraft(dir: File) {
    val store = freshStore(dir)
    val setup = nonDefaultSetup().copy(
        level = "Youth",
        rules = usauDefaultGameRules("Youth"),
    )
    check(setup.rules.timeBetweenPointsSeconds == 80)

    store.saveCurrentGame(setup)
    store.saveProfile(fixtureProfile())
    store.saveSettings(fixtureSettings())
    store.saveArchivedGames(emptyList())
}

private fun writeActiveGame(dir: File) {
    val store = freshStore(dir)
    val base = nonDefaultSetup()
    val setup = base.copy(
        division = GameDivision.MIXED,
        rules = base.rules.copy(
            timeBetweenPointsSeconds = 75,
            timeoutSeconds = 90,
            waterBreakMode = WaterBreakMode.AUTOMATIC,
            waterBreakMinutes = 4,
        ),
    )
    check(setup.rules.timeBetweenPointsSeconds == 75)
    check(setup.rules.timeoutSeconds == 90)
    check(setup.rules.waterBreakMode == WaterBreakMode.AUTOMATIC)
    check(setup.rules.waterBreakMinutes == 4)
    val game = activeGameWithEvents(setup)

    store.saveCurrentGame(game)
    store.saveProfile(fixtureProfile())
    store.saveSettings(fixtureSettings())
    store.saveArchivedGames(
        listOf(
            shortCompletedGame().pruneUndoHistory(),
        )
    )
}

private fun writeCompletedArchive(dir: File) {
    val store = freshStore(dir)
    val richSetup = nonDefaultSetup().copy(division = GameDivision.MIXED)
    val richGame = activeGameWithEvents(richSetup)
        .endGameNow(setupEpoch(richSetup) + 180_000L)

    store.saveCurrentGame(null)
    store.saveProfile(fixtureProfile())
    store.saveSettings(fixtureSettings())
    store.saveArchivedGames(
        listOf(
            richGame.pruneUndoHistory(),
            shortCompletedGame().pruneUndoHistory(),
        )
    )
}

private fun writeCompleteCurrentGame(dir: File) {
    val store = freshStore(dir)
    val setup = nonDefaultSetup().copy(division = GameDivision.MIXED)
    val game = activeGameWithEvents(setup)
        .endGameNow(setupEpoch(setup) + 180_000L)
    val settings = fixtureSettings().copy(
        showAbbaRatioAsSequence = false,
    )
    check(!settings.showAbbaRatioAsSequence)

    store.saveCurrentGame(game)
    store.saveProfile(fixtureProfile())
    store.saveSettings(settings)
    store.saveArchivedGames(emptyList())
}

private fun freshStore(dir: File): FileAppStateStorage {
    dir.deleteRecursively()
    dir.mkdirs()
    return FileAppStateStorage(dir)
}

private fun baseSetup(): GameState {
    return newSetupGameState(
        now = setupEpoch(
            LocalDate.of(2026, 1, 3),
            LocalTime.of(9, 30),
            ZoneId.of("America/New_York"),
        ),
    ).copy(
        startDate = LocalDate.of(2026, 1, 3),
        startTime = LocalTime.of(9, 30),
        timeZone = ZoneId.of("America/New_York"),
        tournamentName = "Migration Classic",
        division = GameDivision.MIXED,
        level = "Club",
        gameContext = "Semifinal",
        observerNames = listOf("Casey", "Morgan"),
        fieldName = "Field 3",
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
            switchGenZoneAtHalftime = false,
        ),
        teamOne = TeamState(
            name = "Viscous Coupling",
            color = TeamColorChoice.CUSTOM,
            customColorArgb = 0xFF1E88E5,
            coaches = "Pat Coach",
            fieldCaptains = "Alex Seven",
            spiritCaptains = "Sam Spirit",
        ),
        teamTwo = TeamState(
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
        openingPullingTeam = TeamId.TEAM_ONE,
        openingPullingFromEnd = FieldEnd.FAR,
        pullPromptTarget = PullPromptTarget.BOTH,
        initialGenderRatio = GenderRatio.FOUR_WOMEN_THREE_MEN,
        firstHalfGenZone = FieldEnd.NEAR,
    )
}

private fun nonDefaultSetup(): GameState {
    return baseSetup().copy(
        startDate = LocalDate.of(2026, 2, 14),
        startTime = LocalTime.of(13, 45),
        tournamentName = "Migration Invitational",
        division = GameDivision.OPEN,
        level = "College",
        gameContext = "Pool play",
        observerNames = listOf("Mike Jarvis", "Casey Lee"),
        fieldName = "Field 7",
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
        ),
        teamOne = TeamState(
            name = "Bees",
            color = TeamColorChoice.YELLOW,
            coaches = "Bee Coach",
            fieldCaptains = "Bee Captain",
            spiritCaptains = "Bee Spirit",
        ),
        teamTwo = TeamState(
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
        openingPullingTeam = TeamId.TEAM_TWO,
        openingPullingFromEnd = FieldEnd.NEAR,
        pullPromptTarget = PullPromptTarget.FAR,
        initialGenderRatio = GenderRatio.FOUR_MEN_THREE_WOMEN,
        firstHalfGenZone = FieldEnd.FAR,
    )
}

private fun activeGameWithEvents(setup: GameState): GameState {
    val start = setupEpoch(setup)
    var game = setup.startGame()
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
        teamOneTimeViolations = 0,
        teamTwoOffsides = 1,
        teamTwoFalseStarts = 1,
        teamTwoMajorityPulls = 0,
        teamTwoTimeViolations = 1,
        now = start + 140_000L,
    )
    game = game.adjustTimeouts(
        teamOneTimeoutsUsed = 1,
        teamTwoTimeoutsUsed = 1,
        teamOneFirstHalfTimeoutsUsed = 1,
        teamTwoFirstHalfTimeoutsUsed = 0,
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
    var game = setup.startGame().beginLivePoint(start + 1_000L)
    game = game.recordGoal(TeamId.TEAM_ONE, start + 60_000L)
    return game.endGameNow(start + 70_000L)
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

private fun setupEpoch(setup: GameState): Long {
    return epochTimestamp(setup.startDate, setup.startTime, setup.timeZone)
}

private fun setupEpoch(startDate: LocalDate, startTime: LocalTime, timeZone: ZoneId): Long {
    return epochTimestamp(startDate, startTime, timeZone)
}
