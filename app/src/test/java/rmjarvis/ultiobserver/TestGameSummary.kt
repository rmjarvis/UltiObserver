package rmjarvis.ultiobserver

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/// Tests for in-app game summaries and the shareable text version.
class TestGameSummary : GameDomainTestFixtures() {
    /**
     * Test completed-game display text and winner-first team ordering.
     */
    @Test
    fun completedGameSummaryText() {
        // Completed-game display text combines game metadata, observer names, times, and
        // winner-first scores.
        val baseState = standardLiveGameState(
            startDate = LocalDate.of(2026, 5, 19),
            startTime = LocalTime.of(10, 0),
        )
        val state = baseState.copy(
            tournamentName = "Philly Open",
            division = GameDivision.MIXED,
            level = "Masters",
            gameContext = "Semifinal",
            observerNames = listOf("Mike", "Gary"),
            fieldName = "Field 7",
            endEpoch = timestampAt(baseState, LocalTime.of(12, 42)),
            phase = GamePhase.GAME_OVER,
            teamOne = TeamState("Viscous Coupling", TeamColorChoice.WHITE, score = 12),
            teamTwo = TeamState("Animal", TeamColorChoice.RED, score = 15),
        )

        assertEquals(
            listOf(
                "Game summary",
                "Philly Open Mixed Division Masters Semifinal",
                "Observers: Mike, Gary",
                "Field: Field 7",
                "Start May 19, 2026 10:00 AM",
                "End time 12:42 PM",
                "Animal 15",
                "Viscous Coupling 12",
            ),
            state.gameOverSummaryText().testDisplayLines(),
        )

        // Winning team is listed first if the scores are different.
        assertEquals(listOf(state.teamTwo, state.teamOne), state.winnerFirstTeams())
        assertEquals(
            listOf(state.teamTwo.copy(score = 2), state.teamOne.copy(score = 1)),
            state.copy(
                teamOne = state.teamOne.copy(score = 1),
                teamTwo = state.teamTwo.copy(score = 2),
            ).winnerFirstTeams(),
        )

        // Tied teams are listed alphabetically.
        val zTeam = state.teamOne
            .copy(name = "Z Team", score = 1)
        val aTeam = state.teamTwo
            .copy(name = "A Team", score = 1)
        assertEquals(
            listOf(aTeam, zTeam),
            state.copy(teamOne = zTeam, teamTwo = aTeam).winnerFirstTeams(),
        )
        assertEquals(
            listOf(aTeam, zTeam),
            state.copy(teamOne = aTeam, teamTwo = zTeam).winnerFirstTeams(),
        )

        // Summaries for an in-progress game do not invent an end time.
        assertEquals(
            listOf(
                "Game summary",
                "Philly Open Mixed Division Masters Semifinal",
                "Observers: Mike, Gary",
                "Field: Field 7",
                "Start May 19, 2026 10:00 AM",
                "Animal 15",
                "Viscous Coupling 12",
            ),
            state.copy(
                phase = GamePhase.LIVE_POINT,
                endEpoch = null,
            ).gameOverSummaryText().testDisplayLines(),
        )
    }

    /**
     * Test completed-game summaries show all the misconduct that happened during the game.
     * Player cards are listed individually.
     * Blue cards and technical fouls get count summaries.
     */
    @Test
    fun completedGameMisconduct() {
        // Set up a game with various cards and technical fouls assessed to team 1.
        val state = standardLiveGameState(
            startDate = LocalDate.of(2026, 5, 19),
            startTime = LocalTime.of(10, 0),
        ).copy(
            phase = GamePhase.GAME_OVER,
            teamOne = TeamState(
                name = "Viscous Coupling",
                color = TeamColorChoice.WHITE,
                blueCards = 2,
                technicalFouls = 1,
            ),
            teamTwo = TeamState("Animal", TeamColorChoice.RED),
            teamTwoPlayers = listOf(PlayerRecord(jerseyNumber = "99")),
            teamOnePlayers = listOf(
                playerRecordWithCards(
                    jerseyNumber = "7",
                    playerName = "Casey Handler",
                    yellows = 1,
                ).copy(
                    cards = listOf(
                        InGamePlayerCardEvent(
                            CardType.YELLOW,
                            index = 0,
                            reason = CardReason(preset = "Dangerous play"),
                        )
                    ),
                ),
                playerRecordWithCards(jerseyNumber = "12", yellows = 2).copy(
                    cards = listOf(
                        InGamePlayerCardEvent(
                            CardType.YELLOW,
                            index = 1,
                            reason = CardReason(preset = "Taunting"),
                        ),
                        InGamePlayerCardEvent(
                            CardType.YELLOW,
                            index = 3,
                            reason = CardReason(preset = "Dangerous play"),
                        ),
                    ),
                ),
                playerRecordWithCards(jerseyNumber = "", playerName = "No Number", reds = 1).copy(
                    cards = listOf(
                        InGamePlayerCardEvent(
                            CardType.RED,
                            index = 2,
                            reason = CardReason(preset = "Egregious dangerous play"),
                        ),
                    ),
                ),
                playerRecordWithCards(jerseyNumber = "14", reds = 1).copy(
                    cards = listOf(
                        InGamePlayerCardEvent(
                            CardType.RED,
                            index = 4,
                        ),
                    ),
                ),
            ),
        )

        // The game summary should show each card in the order they were given (index above).
        // Blues and techs just show total counts.
        assertEquals(
            listOf(
                "Viscous Coupling",
                "#7 Casey Handler: Yellow card -- Dangerous play",
                "#12: Yellow card -- Taunting",
                "No Number: Red card -- Egregious dangerous play",
                "#12: Yellow card -- Dangerous play",
                "#14: Red card",
                "Blue cards 2",
                "Technical fouls 1",
            ),
            state.gameOverTeamSummaryText(TeamId.TEAM_ONE).testDisplayLines(),
        )

        // Teams without any misconduct still display explicit zero-count rows.
        assertEquals(
            listOf(
                "Animal",
                "No yellow or red cards issued.",
                "Blue cards 0",
                "Technical fouls 0",
            ),
            state.gameOverTeamSummaryText(TeamId.TEAM_TWO).testDisplayLines(),
        )
    }

    /**
     * Test the text that gets sent using the share button.
     * It should share tournament metadata, final score, and any misconduct that happened.
     */
    @Test
    fun shareText() {
        // First, a game with lots of misconduct on one team.
        var state = standardLiveGameState(
            startDate = LocalDate.of(2026, 5, 19),
            startTime = LocalTime.of(10, 0),
        ).copy(
            tournamentName = "Philly Open",
            division = GameDivision.OPEN,
            level = "Masters",
            gameContext = "Final",
            observerNames = listOf("Mike", "Gary"),
            fieldName = "Field 7",
            phase = GamePhase.GAME_OVER,
            teamOne = TeamState("Viscous Coupling", TeamColorChoice.WHITE, score = 12),
            teamTwo = TeamState(
                name = "Animal",
                color = TeamColorChoice.RED,
                score = 15,
                technicalFouls = 2,
                blueCards = 1,
            ),
            teamTwoPlayers = listOf(
                playerRecordWithCards(
                    jerseyNumber = "7",
                    playerName = "Casey Handler",
                    yellows = 2,
                ).copy(
                    cards = listOf(
                        InGamePlayerCardEvent(
                            CardType.YELLOW,
                            index = 0,
                            reason = CardReason(preset = "Taunting"),
                        ),
                        InGamePlayerCardEvent(
                            CardType.YELLOW,
                            index = 2,
                            reason = CardReason(preset = "Dangerous play"),
                        ),
                    ),
                ),
                playerRecordWithCards(jerseyNumber = "", playerName = "No Number", reds = 1).copy(
                    cards = listOf(
                        InGamePlayerCardEvent(
                            CardType.RED,
                            index = 1,
                            reason = CardReason(preset = "Egregious dangerous play"),
                        ),
                    ),
                ),
            ),
        )

        // The share text should show all the metadata and misconduct.
        assertEquals(
            """
            UltiObserver Game Summary
            Philly Open Open Division Masters Final
            Observers: Mike, Gary
            Field: Field 7
            May 19, 2026, 10:00 AM
            Animal 15, Viscous Coupling 12
            Misconduct:
              Animal:
                #7 Casey Handler Yellow -- Taunting
                No Number Red -- Egregious dangerous play
                #7 Casey Handler Yellow -- Dangerous play
                1 Blue, 2 Techs
            """.trimIndent(),
            state.gameSummaryShareText(),
        )

        // When there is no misconduct on either team, the share text just says that.
        // It also skips any metadata that doesn't have a value (e.g. tournament, observers).
        state = standardLiveGameState(
            startDate = LocalDate.of(2026, 5, 19),
            startTime = LocalTime.of(10, 0),
        ).copy(
            phase = GamePhase.GAME_OVER,
            teamOne = TeamState("Viscous Coupling", TeamColorChoice.WHITE, score = 15),
            teamTwo = TeamState("Animal", TeamColorChoice.RED, score = 12),
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

        // When both teams have some misconduct, the share text lists them separately.
        state = standardLiveGameState(
            startDate = LocalDate.of(2026, 5, 19),
            startTime = LocalTime.of(10, 0),
        ).copy(
            phase = GamePhase.GAME_OVER,
            teamOne = TeamState("Viscous Coupling", TeamColorChoice.WHITE, score = 15),
            teamTwo = TeamState(
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

}

/// Return the summary lines that this test expects the completed-game card to display.
private fun GameOverSummaryText.testDisplayLines(): List<String> {
    return buildList {
        add(title)
        gameInformationLine?.let { add(it) }
        observersLine?.let { add(it) }
        fieldLine?.let { add(it) }
        add(startLine)
        endLine?.let { add(it) }
        addAll(scoreLines)
    }
}

/// Return the summary lines that this test expects one completed-game team section to display.
private fun GameOverTeamSummaryText.testDisplayLines(): List<String> {
    return buildList {
        add(teamName)
        addAll(issuedCardLines)
        add(blueCardsLine)
        add(technicalFoulsLine)
    }
}
