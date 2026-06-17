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
                title = "Game summary",
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
            teamTwoPlayers = listOf(PlayerRecord(jerseyNumber = "99")),
            teamOnePlayers = listOf(
                playerRecordWithCards(jerseyNumber = "7", playerName = "Casey Handler", yellows = 1).copy(
                    cards = listOf(InGamePlayerCardEvent(CardType.YELLOW, reason = CardReason(preset = "Dangerous play"))),
                ),
                playerRecordWithCards(jerseyNumber = "12", yellows = 2).copy(
                    cards = listOf(
                        InGamePlayerCardEvent(CardType.YELLOW, reason = CardReason(preset = "Taunting")),
                        InGamePlayerCardEvent(CardType.YELLOW, reason = CardReason(preset = "Dangerous play")),
                    ),
                ),
                playerRecordWithCards(jerseyNumber = "", playerName = "No Number", reds = 1).copy(
                    cards = listOf(
                        InGamePlayerCardEvent(CardType.RED, reason = CardReason(preset = "Egregious dangerous play")),
                    ),
                ),
            ),
        )

        assertEquals(
            GameOverTeamSummaryText(
                teamName = "Viscous Coupling",
                issuedCardLines = listOf(
                    "#7 Casey Handler: Yellow card -- Dangerous play",
                    "#12: Yellow card -- Taunting",
                    "#12: Yellow card -- Dangerous play",
                    "No Number: Red card -- Egregious dangerous play",
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
            teamTwoPlayers = listOf(
                playerRecordWithCards(jerseyNumber = "7", playerName = "Casey Handler", yellows = 2).copy(
                    cards = listOf(
                        InGamePlayerCardEvent(CardType.YELLOW, reason = CardReason(preset = "Taunting")),
                        InGamePlayerCardEvent(CardType.YELLOW, reason = CardReason(preset = "Dangerous play")),
                    ),
                ),
                playerRecordWithCards(jerseyNumber = "", playerName = "No Number", reds = 1).copy(
                    cards = listOf(
                        InGamePlayerCardEvent(CardType.RED, reason = CardReason(preset = "Egregious dangerous play")),
                    ),
                ),
            ),
        )

        assertEquals(
            """
            UltiObserver Game Summary
            Philly Open - May 19, 2026, 10:00 AM
            Animal 15, Viscous Coupling 12
            Misconduct:
              Animal:
                #7 Casey Handler Yellow -- Taunting
                #7 Casey Handler Yellow -- Dangerous play
                No Number Red -- Egregious dangerous play
                1 Blue, 2 Techs
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
            teamOnePlayers = listOf(PlayerRecord(jerseyNumber = "99")),
        )

        assertEquals(
            """
            UltiObserver Game Summary
            May 19, 2026, 10:00 AM
            Viscous Coupling 15, Animal 12
            No misconduct assessments
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
            teamOnePlayers = listOf(
                playerRecordWithCards(jerseyNumber = "6", yellows = 1),
                playerRecordWithCards(jerseyNumber = "9", yellows = 1, reds = 1),
            ),
        )

        assertEquals(
            """
            UltiObserver Game Summary
            May 19, 2026, 10:00 AM
            Viscous Coupling 15, Animal 12
            Misconduct:
              Viscous Coupling:
                #6 Yellow
                #9 Yellow
                #9 Red
              Animal:
                2 Blue, 1 Tech
            """.trimIndent(),
            state.gameSummaryShareText(),
        )
    }

    /// Verify game information travels from setup into live state and back through setup editing.
    @Test
    fun gameInformationTravelsThroughSetupAndLiveState() {
        val setup = standardGameSetup(
            startDate = LocalDate.of(2026, 5, 19),
            startTime = LocalTime.of(10, 0),
        ).copy(
            tournamentName = "Philly Open",
            division = GameDivision.MIXED,
            gameContext = "Semifinals",
            nearEndName = "Road",
            farEndName = "Trees",
            pullingFromEnd = FieldEnd.NEAR,
            pullPromptTarget = PullPromptTarget.BOTH,
        )

        val state = createLiveGameState(setup)

        assertEquals("Philly Open", state.tournamentName)
        assertEquals(GameDivision.MIXED, state.division)
        assertEquals("Semifinals", state.gameContext)
        assertEquals("Philly Open", state.toSetupState().tournamentName)
        assertEquals(GameDivision.MIXED, state.toSetupState().division)
        assertEquals("Semifinals", state.toSetupState().gameContext)
        assertEquals("Road", state.toSetupState().fieldEndName(FieldEnd.NEAR))
        assertEquals("Trees", state.toSetupState().fieldEndName(FieldEnd.FAR))
        assertEquals("Viscous Coupling pulls from Road", state.toSetupState().startingPullSummary())
        assertEquals("Pull prompts for both ends", state.toSetupState().pullPromptSummary())
        assertEquals(true, state.toSetupState().usesMixedDivision())

        val defaultEndsSetup = setup.copy(nearEndName = "", farEndName = "", pullPromptTarget = PullPromptTarget.NEAR)
        assertEquals("Near end", defaultEndsSetup.fieldEndName(FieldEnd.NEAR))
        assertEquals("Far end", defaultEndsSetup.fieldEndName(FieldEnd.FAR))
        assertEquals("Pull prompts for Near end", defaultEndsSetup.pullPromptSummary())
    }
}
