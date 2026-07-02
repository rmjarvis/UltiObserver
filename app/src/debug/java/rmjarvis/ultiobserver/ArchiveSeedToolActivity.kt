package rmjarvis.ultiobserver

import android.app.Activity
import android.os.Bundle
import android.widget.Toast
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

/**
 * Debug-only tool for populating the installed app with fake completed archives.
 */
internal class ArchiveSeedToolActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val seededCount = seedFakeCompletedArchive()
        Toast.makeText(
            this,
            "Seeded $seededCount fake completed archived games.",
            Toast.LENGTH_LONG,
        ).show()
        finish()
    }

    private fun seedFakeCompletedArchive(): Int {
        val storage = FileAppStateStorage(filesDir)
        val savedNonCompletedGames = storage.loadArchivedGames()
            .filter { it.archiveCategory != ArchivedGameCategory.COMPLETED }
        val generatedGames = fakeCompletedArchiveGames()
        storage.saveArchivedGames(generatedGames + savedNonCompletedGames)
        return generatedGames.size
    }
}

private data class FakeArchiveTemplate(
    val tournamentName: String,
    val division: GameDivision?,
    val level: String,
    val observerNames: List<String>,
)

private fun fakeCompletedArchiveGames(): List<GameState> {
    val timeZone = ZoneId.of("America/New_York")
    val today = LocalDate.now()
    val templates = listOf(
        FakeArchiveTemplate(
            "Summer Solstice",
            GameDivision.OPEN,
            "Club",
            listOf("Mike Jarvis", "Casey Lee"),
        ),
        FakeArchiveTemplate(
            "Summer Solstice",
            GameDivision.MIXED,
            "Club",
            listOf("Casey Lee", "Morgan Hall"),
        ),
        FakeArchiveTemplate(
            "Summer Solstice",
            GameDivision.WOMENS,
            "Club",
            listOf("Morgan Hall", "Priya Shah"),
        ),
        FakeArchiveTemplate(
            "Fall Brawl",
            GameDivision.OPEN,
            "College",
            listOf("Priya Shah", "Sam Ortiz"),
        ),
        FakeArchiveTemplate(
            "Fall Brawl",
            GameDivision.MIXED,
            "College",
            listOf("Mike Jarvis", "Lee Chen"),
        ),
        FakeArchiveTemplate("Fall Brawl", GameDivision.WOMENS, "College", emptyList()),
        FakeArchiveTemplate(
            "Winter Classic",
            GameDivision.OPEN,
            "Masters",
            listOf("Sam Ortiz", "Taylor Reed"),
        ),
        FakeArchiveTemplate(
            "Winter Classic",
            GameDivision.MIXED,
            "Masters",
            listOf("Lee Chen", "Jordan Kim"),
        ),
        FakeArchiveTemplate(
            "Regional Qualifier",
            GameDivision.OPEN,
            "Elite",
            listOf("Taylor Reed", "Mike Jarvis", "Casey Lee"),
        ),
        FakeArchiveTemplate(
            "Regional Qualifier",
            GameDivision.MIXED,
            "Elite",
            listOf("Mike Jarvis", "Morgan Hall", "Priya Shah", "Sam Ortiz"),
        ),
        FakeArchiveTemplate(
            "City League",
            GameDivision.OPEN,
            "League",
            listOf("Jordan Kim", "Lee Chen"),
        ),
        FakeArchiveTemplate("City League", null, "", listOf("Casey Lee", "Taylor Reed")),
        FakeArchiveTemplate("", GameDivision.MIXED, "Recreational", emptyList()),
        FakeArchiveTemplate("", null, "", listOf("Morgan Hall", "Jordan Kim")),
    )
    val teamNames = listOf(
        "Atlas" to "Beacon",
        "Breeze" to "Comet",
        "Cipher" to "Drift",
        "Ember" to "Fable",
        "Glacier" to "Harbor",
        "Ion" to "Juniper",
        "Keystone" to "Lantern",
        "Mosaic" to "Nimbus",
        "Orbit" to "Pioneer",
        "Quartz" to "Rally",
        "Summit" to "Tide",
        "Union" to "Vector",
        "Willow" to "Xylo",
        "Yonder" to "Zenith",
    )
    val dateOffsets = listOf(0L, 1L, 3L, 6L, 10L, 18L, 29L, 31L, 45L, 65L, 90L, 130L)
    return templates.flatMapIndexed { templateIndex, template ->
        listOf(0, 1, 2).map { repetition ->
            val gameIndex = templateIndex * 3 + repetition
            val startDate = today.minusDays(dateOffsets[gameIndex % dateOffsets.size])
            val startTime = LocalTime.of(8 + (gameIndex % 7), if (gameIndex % 2 == 0) 0 else 30)
            val teams = teamNames[(gameIndex + repetition) % teamNames.size]
            val scoreOne = 9 + (gameIndex % 7)
            val scoreTwo = 7 + ((gameIndex + repetition) % 6)
            val winningFirst = gameIndex % 4 != 0
            val teamOneScore = if (winningFirst) scoreOne else scoreTwo
            val teamTwoScore = if (winningFirst) scoreTwo else scoreOne + 1
            fakeCompletedGame(
                timeZone = timeZone,
                startDate = startDate,
                startTime = startTime,
                template = template,
                teamOneName = teams.first,
                teamTwoName = teams.second,
                teamOneScore = teamOneScore,
                teamTwoScore = teamTwoScore,
                gameIndex = gameIndex,
            )
        }
    }
}

private fun fakeCompletedGame(
    timeZone: ZoneId,
    startDate: LocalDate,
    startTime: LocalTime,
    template: FakeArchiveTemplate,
    teamOneName: String,
    teamTwoName: String,
    teamOneScore: Int,
    teamTwoScore: Int,
    gameIndex: Int,
): GameState {
    val startEpoch = epochTimestamp(startDate, startTime, timeZone)
    val setup = newSetupGameState(now = startEpoch).copy(
        startDate = startDate,
        startTime = startTime,
        timeZone = timeZone,
        tournamentName = template.tournamentName,
        division = template.division,
        level = template.level,
        gameContext = listOf("Pool play", "Quarterfinal", "Semifinal", "Final")[gameIndex % 4],
        observerNames = template.observerNames,
        fieldName = "Field ${(gameIndex % 9) + 1}",
        nearEndName = listOf("HQ", "Trees", "Parking", "Stadium")[gameIndex % 4],
        farEndName = listOf("Scoreboard", "River", "Tents", "Track")[gameIndex % 4],
        rules = fakeArchiveRules(template.division, gameIndex),
        teamOne = TeamState(
            name = teamOneName,
            color = fakeArchiveTeamColor(gameIndex),
        ),
        teamTwo = TeamState(
            name = teamTwoName,
            color = fakeArchiveTeamColor(gameIndex + 3),
        ),
        pullingTeam = if (gameIndex % 2 == 0) TeamId.TEAM_ONE else TeamId.TEAM_TWO,
        openingPullingTeam = if (gameIndex % 2 == 0) TeamId.TEAM_ONE else TeamId.TEAM_TWO,
        openingPullingFromEnd = if (gameIndex % 2 == 0) FieldEnd.FAR else FieldEnd.NEAR,
    )
    return setup.startGame().copy(
        endEpoch = startEpoch + Duration.ofMinutes((85 + gameIndex).toLong()).toMillis(),
        teamOne = setup.teamOne.copy(
            score = teamOneScore,
            timeoutsUsedThisHalf = gameIndex % 2,
            firstHalfTimeoutsUsed = (gameIndex + 1) % 2,
            technicalFouls = gameIndex % 3,
            blueCards = (gameIndex + 2) % 3,
        ),
        teamTwo = setup.teamTwo.copy(
            score = teamTwoScore,
            timeoutsUsedThisHalf = (gameIndex + 1) % 2,
            firstHalfTimeoutsUsed = gameIndex % 2,
            technicalFouls = (gameIndex + 1) % 3,
            blueCards = gameIndex % 3,
        ),
        phase = GamePhase.GAME_OVER,
        countdown = null,
        halftimeTaken = gameIndex % 2 == 0,
        winningScore = maxOf(teamOneScore, teamTwoScore),
        lastEvent = "Game over.",
    ).pruneUndoHistory()
}

private fun fakeArchiveRules(division: GameDivision?, gameIndex: Int): GameRules {
    return GameRules(
        gameTo = if (gameIndex % 3 == 0) 13 else 15,
        halftimeMinutes = if (gameIndex % 2 == 0) 7 else 10,
        useHalfCap = gameIndex % 4 != 0,
        useSoftCap = true,
        useHardCap = gameIndex % 5 != 0,
        timeoutsPerHalf = if (gameIndex % 2 == 0) 2 else 1,
        hasFloaterTimeout = gameIndex % 3 == 0,
        genderRatioRule = if (division == GameDivision.MIXED) {
            listOf(
                GenderRatioRule.ABBA,
                GenderRatioRule.GEN_ZONE,
                GenderRatioRule.OFFENSE_DECIDES,
            )[gameIndex % 3]
        } else {
            GenderRatioRule.NA
        },
        useMajorityPullRule = gameIndex % 2 == 0,
    )
}

private fun fakeArchiveTeamColor(index: Int): TeamColorChoice {
    val colors = listOf(
        TeamColorChoice.WHITE,
        TeamColorChoice.BLACK,
        TeamColorChoice.RED,
        TeamColorChoice.BLUE,
        TeamColorChoice.GREEN,
        TeamColorChoice.YELLOW,
        TeamColorChoice.PINK,
        TeamColorChoice.GRAY,
    )
    return colors[index % colors.size]
}
