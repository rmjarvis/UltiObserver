package rmjarvis.ultiobserver

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Test
import org.junit.runner.RunWith

/** Generate the deterministic archive rows used by the release screenshot narrative. */
@RunWith(AndroidJUnit4::class)
class GenerateReleaseScreenshotArchive {
    /** Write fresh current-version archive files directly into the target app's storage. */
    @Test
    fun generateArchive() {
        val filesDir = InstrumentationRegistry.getInstrumentation().targetContext.filesDir
        val storage = FileAppStateStorage(filesDir)
        val games = releaseScreenshotArchiveGames()

        storage.saveArchivedGames(games)

        check(storage.loadArchivedGames() == games) {
            "Release screenshot archive rows did not round-trip through current storage."
        }
    }
}

private data class ReleaseArchiveGame(
    val date: String,
    val time: String,
    val tournament: String,
    val division: GameDivision?,
    val level: String,
    val context: String,
    val field: String,
    val teamOne: String,
    val teamOneScore: Int,
    val teamTwo: String,
    val teamTwoScore: Int,
    val additionalObserver: String = "",
    val hasYellowAndBlueCards: Boolean = false,
    val phase: GamePhase = GamePhase.GAME_OVER,
)

private fun releaseScreenshotArchiveGames(): List<GameState> {
    return listOf(
        completed("2026-07-06", "15:30", "Potlatch Revived", GameDivision.MIXED, "Legends", "Final", "17", "Red Fish Blue Fish", 14, "Honey Pot", 15, "Chris Watcher"),
        completed("2026-07-06", "11:30", "Potlatch Revived", GameDivision.MIXED, "Legends", "Semifinal", "17", "Red Fish Blue Fish", 14, "Rippit", 12, "Chris Watcher", hasYellowAndBlueCards = true),
        completed("2026-07-05", "15:30", "Potlatch Revived", GameDivision.MIXED, "Legends", "Pool play", "6", "Red Fish Blue Fish", 15, "Grinches", 9, "Chris Watcher"),
        completed("2026-07-05", "13:30", "Potlatch Revived", GameDivision.MIXED, "Legends", "Pool play", "6", "Yo Yo", 10, "Mama", 13, "Chris Watcher"),
        completed("2026-07-05", "11:00", "Potlatch Revived", GameDivision.MIXED, "Legends", "Pool play", "9", "Charlie Brown", 13, "Snoopy", 11, "Chris Watcher"),
        completed("2026-07-05", "09:00", "Potlatch Revived", GameDivision.MIXED, "Legends", "Pool play", "5", "Honey Pot", 15, "Creamsicle", 8, "Chris Watcher"),
        completed("2026-06-21", "13:00", "Northeast Masters Super Regionals", GameDivision.OPEN, "Great Grandmasters", "Game to Go", "D2", "MAGMA", 9, "Critical Mass", 15, "Dana Ellis"),
        completed("2026-06-21", "09:00", "Northeast Masters Super Regionals", GameDivision.OPEN, "Great Grandmasters", "Semifinal", "D1", "Black Cans GGM", 15, "MAGMA", 5, "Dana Ellis"),
        completed("2026-06-20", "13:00", "Northeast Masters Super Regionals", GameDivision.OPEN, "Grand Masters", "Pool play", "D3", "Black Cans", 10, "Rusted Metal", 15, "Chris Watcher"),
        completed("2026-06-20", "11:00", "Northeast Masters Super Regionals", GameDivision.OPEN, "Great Grandmasters", "Pool play", "D2", "No Country 50", 15, "MAGMA", 2, "Dana Ellis"),
        completed("2026-06-20", "09:00", "Northeast Masters Super Regionals", GameDivision.OPEN, "Grand Masters", "Pool play", "D2", "Black Cans", 12, "All Bashed Out GM", 15, "Chris Watcher"),
        completed("2026-05-25", "14:30", "D-I College Championships", GameDivision.OPEN, "College", "Championship", "219", "Massachusetts", 15, "Carleton College", 11, "Taylor Reed"),
        completed("2026-05-25", "12:00", "D-I College Championships", GameDivision.WOMENS, "College", "Championship", "219", "Carleton College", 15, "British Columbia", 13, "Morgan Hall"),
        completed("2026-05-24", "17:00", "D-I College Championships", GameDivision.WOMENS, "College", "Semifinal", "219", "Stanford", 11, "British Columbia", 15, "Morgan Hall"),
        completed("2026-05-24", "14:30", "D-I College Championships", GameDivision.OPEN, "College", "Semifinal", "219", "Oregon", 10, "Massachusetts", 15, "Taylor Reed"),
        completed("2026-05-24", "12:00", "D-I College Championships", GameDivision.WOMENS, "College", "Semifinal", "219", "Carleton College", 15, "Tufts", 14, "Morgan Hall"),
        completed("2026-05-24", "10:30", "D-I College Championships", GameDivision.OPEN, "College", "Quarterfinal", "201", "Colorado", 15, "Western Washington", 11, "Taylor Reed"),
        completed("2026-05-23", "17:30", "D-I College Championships", GameDivision.OPEN, "College", "Pre-Quarterfinal", "201", "Pittsburgh", 15, "Georgia Tech", 12, "Taylor Reed"),
        completed("2026-05-23", "13:00", "D-I College Championships", GameDivision.WOMENS, "College", "Round 8", "203", "Carleton College", 15, "Victoria", 4, "Morgan Hall"),
        completed("2026-05-23", "10:30", "D-I College Championships", GameDivision.OPEN, "College", "Round 7", "219", "Georgia Tech", 15, "Utah", 14, "Taylor Reed"),
        completed("2026-05-23", "08:30", "D-I College Championships", GameDivision.WOMENS, "College", "Round 6", "208", "Carleton College", 15, "UCLA", 7, "Morgan Hall"),
        completed("2026-05-22", "17:00", "D-I College Championships", GameDivision.OPEN, "College", "Round 5", "205", "North Carolina", 10, "Massachusetts", 15, "Taylor Reed"),
        completed("2026-05-22", "15:00", "D-I College Championships", GameDivision.WOMENS, "College", "Round 4", "205", "UC Santa Cruz", 14, "Tufts", 11, "Morgan Hall"),
        completed("2026-05-22", "10:30", "D-I College Championships", GameDivision.OPEN, "College", "Round 2", "207", "Carleton College", 15, "Washington", 10, "Taylor Reed"),
        completed("2026-05-22", "08:30", "D-I College Championships", GameDivision.OPEN, "College", "Round 1", "202", "Oregon", 15, "Utah", 11, "Taylor Reed"),
        draft("2026-07-05", "09:00", "5", "Honey Pot", "Creamsicle", "Katie Pickles"),
        draft("2026-07-05", "11:00", "9", "Charlie Brown", "Snoopy", "Ben Bean"),
        draft("2026-07-05", "13:30", "6", "Yo Yo", "Mama", "Chris Watcher"),
        draft("2026-07-05", "15:30", "6", "Red Fish Blue Fish", "Grinches", "Amy Young"),
    ).map(ReleaseArchiveGame::toGameState)
}

private fun completed(
    date: String,
    time: String,
    tournament: String,
    division: GameDivision,
    level: String,
    context: String,
    field: String,
    teamOne: String,
    teamOneScore: Int,
    teamTwo: String,
    teamTwoScore: Int,
    additionalObserver: String,
    hasYellowAndBlueCards: Boolean = false,
): ReleaseArchiveGame {
    return ReleaseArchiveGame(
        date,
        time,
        tournament,
        division,
        level,
        context,
        field,
        teamOne,
        teamOneScore,
        teamTwo,
        teamTwoScore,
        additionalObserver,
        hasYellowAndBlueCards,
    )
}

private fun draft(
    date: String,
    time: String,
    field: String,
    teamOne: String,
    teamTwo: String,
    additionalObserver: String,
): ReleaseArchiveGame {
    return ReleaseArchiveGame(
        date,
        time,
        "Potlatch Revived",
        GameDivision.MIXED,
        "Legends",
        "",
        field,
        teamOne,
        0,
        teamTwo,
        0,
        additionalObserver,
        phase = GamePhase.SETUP,
    )
}

private fun ReleaseArchiveGame.toGameState(): GameState {
    val timeZone = ZoneId.of("America/New_York")
    val startDate = LocalDate.parse(date)
    val startTime = LocalTime.parse(time)
    val startEpoch = epochTimestamp(startDate, startTime, timeZone)
    val observerNames = listOf("Mike Jarvis") + listOfNotNull(additionalObserver.ifBlank { null })
    val setup = newSetupGameState(now = startEpoch).copy(
        startDate = startDate,
        startTime = startTime,
        timeZone = timeZone,
        tournamentName = tournament,
        division = division,
        level = level,
        gameContext = context,
        observerNames = observerNames,
        fieldName = field,
        rules = usauDefaultGameRules(level),
        teamOne = TeamState(
            teamOne,
            TeamColorChoice.WHITE,
            score = teamOneScore,
            blueCards = if (hasYellowAndBlueCards) 2 else 0,
        ),
        teamTwo = TeamState(teamTwo, TeamColorChoice.BLUE, score = teamTwoScore),
        teamOnePlayers = if (hasYellowAndBlueCards) {
            listOf(
                PlayerRecord(
                    jerseyNumber = "77",
                    playerName = "Hank Puller",
                    cards = listOf(
                        InGamePlayerCardEvent(
                            cardType = CardType.YELLOW,
                            index = 0,
                            reason = CardReason(preset = "Dangerous play"),
                        )
                    ),
                )
            )
        } else {
            emptyList()
        },
    )
    if (phase == GamePhase.SETUP) {
        return setup
    }

    val started = setup.startGame(OrientationPreference.PORTRAIT).copy(
        teamOne = setup.teamOne,
        teamTwo = setup.teamTwo,
    )
    return started.copy(
        endEpoch = startEpoch + Duration.ofMinutes(90).toMillis(),
        phase = GamePhase.GAME_OVER,
        countdown = null,
        halftimeTaken = true,
        winningScore = maxOf(teamOneScore, teamTwoScore),
    )
}
