package rmjarvis.ultiobserver

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

/// Tests for completed-game summary text shared outside the app.
class TestGameSummary : GameDomainTestFixtures() {
    /// Verify completed-game display text includes start, end, and winner-first score lines.
    @Test
    fun displayTextSummarizesTimesAndWinnerFirstScores() {
        val baseState = standardLiveGameState(
            startDate = LocalDate.of(2026, 5, 19),
            startTime = LocalTime.of(10, 0),
        )
        val state = baseState.copy(
            endEpoch = timestampAt(baseState, LocalTime.of(12, 42)),
            phase = GamePhase.GAME_OVER,
            teamOne = TeamLiveState("Viscous Coupling", TeamColorChoice.WHITE, score = 12),
            teamTwo = TeamLiveState("Animal", TeamColorChoice.RED, score = 15),
        )

        assertEquals(
            GameOverSummaryText(
                title = "Game Summary",
                startLine = "Start May 19, 2026 10:00 AM",
                endLine = "End time 12:42 PM",
                scoreLines = listOf("Animal 15", "Viscous Coupling 12"),
            ),
            state.gameOverSummaryText(),
        )
    }

    /// Verify team display text includes player cards, blue cards, and technical fouls.
    @Test
    fun teamDisplayTextSummarizesIssuedCardsAndTeamCounts() {
        val state = standardLiveGameState(
            startDate = LocalDate.of(2026, 5, 19),
            startTime = LocalTime.of(10, 0),
        ).copy(
            phase = GamePhase.GAME_OVER,
            teamOne = TeamLiveState(
                name = "Viscous Coupling",
                color = TeamColorChoice.WHITE,
                blueCards = 2,
                technicalFouls = 1,
            ),
            teamTwo = TeamLiveState("Animal", TeamColorChoice.RED),
            teamOnePlayerCards = listOf(
                InGamePlayerCardRecord(jerseyNumber = "7", yellows = 1),
                InGamePlayerCardRecord(jerseyNumber = "12", yellows = 2),
                InGamePlayerCardRecord(jerseyNumber = "18", reds = 1),
            ),
        )

        assertEquals(
            GameOverTeamSummaryText(
                teamName = "Viscous Coupling",
                issuedCardLines = listOf(
                    "#7: Yellow card",
                    "#12: Two yellow cards",
                    "#18: Red card",
                ),
                blueCardsLine = "Blue cards 2",
                technicalFoulsLine = "Technical fouls 1",
            ),
            state.gameOverTeamSummaryText(TeamId.TEAM_ONE),
        )
        assertEquals(
            GameOverTeamSummaryText(
                teamName = "Animal",
                issuedCardLines = listOf("No yellow or red cards issued."),
                blueCardsLine = "Blue cards 0",
                technicalFoulsLine = "Technical fouls 0",
            ),
            state.gameOverTeamSummaryText(TeamId.TEAM_TWO),
        )
    }

    /// Verify compact share text includes final score and only teams with in-game misconduct.
    @Test
    fun shareTextSummarizesTournamentScoreAndMisconduct() {
        val state = standardLiveGameState(
            startDate = LocalDate.of(2026, 5, 19),
            startTime = LocalTime.of(10, 0),
        ).copy(
            tournamentName = "Philly Open",
            phase = GamePhase.GAME_OVER,
            teamOne = TeamLiveState("Viscous Coupling", TeamColorChoice.WHITE, score = 12),
            teamTwo = TeamLiveState(
                name = "Animal",
                color = TeamColorChoice.RED,
                score = 15,
                technicalFouls = 2,
                blueCards = 1,
            ),
            teamTwoPlayerCards = listOf(
                InGamePlayerCardRecord(jerseyNumber = "7", yellows = 2),
                InGamePlayerCardRecord(jerseyNumber = "12", reds = 1),
            ),
        )

        assertEquals(
            """
            UltiObserver Game Summary
            Philly Open - May 19, 2026, 10:00 AM
            Animal 15, Viscous Coupling 12
            Misconduct:
              Animal #7 (2Y), #12 (R) + 1 Blue, 2 TF
            """.trimIndent(),
            state.gameSummaryShareText(),
        )
    }

    /// Verify clean games say positively that no misconduct was assessed.
    @Test
    fun shareTextOmitsBlankTournamentAndReportsNoMisconduct() {
        val state = standardLiveGameState(
            startDate = LocalDate.of(2026, 5, 19),
            startTime = LocalTime.of(10, 0),
        ).copy(
            phase = GamePhase.GAME_OVER,
            teamOne = TeamLiveState("Viscous Coupling", TeamColorChoice.WHITE, score = 15),
            teamTwo = TeamLiveState("Animal", TeamColorChoice.RED, score = 12),
            teamOnePlayerCards = listOf(InGamePlayerCardRecord(jerseyNumber = "99")),
        )

        assertEquals(
            """
            UltiObserver Game Summary
            May 19, 2026, 10:00 AM
            Viscous Coupling 15, Animal 12
            No Misconduct Assessments
            """.trimIndent(),
            state.gameSummaryShareText(),
        )
    }

    /// Verify share text handles player-only misconduct and team-only misconduct on different teams.
    @Test
    fun shareTextSummarizesSeparatePlayerAndTeamMisconductLines() {
        val state = standardLiveGameState(
            startDate = LocalDate.of(2026, 5, 19),
            startTime = LocalTime.of(10, 0),
        ).copy(
            phase = GamePhase.GAME_OVER,
            teamOne = TeamLiveState("Viscous Coupling", TeamColorChoice.WHITE, score = 15),
            teamTwo = TeamLiveState(
                name = "Animal",
                color = TeamColorChoice.RED,
                score = 12,
                technicalFouls = 1,
                blueCards = 2,
            ),
            teamOnePlayerCards = listOf(
                InGamePlayerCardRecord(jerseyNumber = "6", yellows = 1),
                InGamePlayerCardRecord(jerseyNumber = "9", yellows = 1, reds = 1),
            ),
        )

        assertEquals(
            """
            UltiObserver Game Summary
            May 19, 2026, 10:00 AM
            Viscous Coupling 15, Animal 12
            Misconduct:
              Viscous Coupling #6 (Y), #9 (Y+R)
              Animal 2 Blue, 1 TF
            """.trimIndent(),
            state.gameSummaryShareText(),
        )
    }

    /// Verify tournament name travels from setup into live state and back through setup editing.
    @Test
    fun tournamentNameTravelsThroughSetupAndLiveState() {
        val setup = standardGameSetup(
            startDate = LocalDate.of(2026, 5, 19),
            startTime = LocalTime.of(10, 0),
        ).copy(tournamentName = "Philly Open")

        val state = createLiveGameState(setup)

        assertEquals("Philly Open", state.tournamentName)
        assertEquals("Philly Open", state.toSetupState().tournamentName)
    }
}
