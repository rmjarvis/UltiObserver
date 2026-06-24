package rmjarvis.ultiobserver

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/// Tests for completed-game summary text shared outside the app.
class TestGameSummary : GameDomainTestFixtures() {
    /**
     * Test live team display helpers and model guards.
     */
    @Test
    fun teamDisplayModelGuards() {
        // Custom live-team colors require an explicit ARGB value.
        assertThrows(IllegalArgumentException::class.java) {
            TeamLiveState("Custom", TeamColorChoice.CUSTOM)
        }

        // Coach and captain fields count as team staff information.
        val teamWithoutStaff = TeamLiveState("No Staff", TeamColorChoice.WHITE)
        assertFalse(teamWithoutStaff.hasCoachOrCaptainInfo())
        assertTrue(teamWithoutStaff.copy(coaches = "Coach").hasCoachOrCaptainInfo())
        assertTrue(teamWithoutStaff.copy(fieldCaptains = "Field captain").hasCoachOrCaptainInfo())
        assertTrue(teamWithoutStaff.copy(spiritCaptains = "Spirit captain").hasCoachOrCaptainInfo())
    }

    /// Verify completed-game display text includes start, end, and winner-first score lines.
    @Test
    fun displayTextSummarizesTimesAndWinnerFirstScores() {
        val baseState = standardLiveGameState(
            startDate = LocalDate.of(2026, 5, 19),
            startTime = LocalTime.of(10, 0),
        )
        val state = baseState.copy(
            tournamentName = "Philly Open",
            division = GameDivision.MIXED,
            level = "Masters",
            gameContext = "Semifinal",
            observers = "Mike and Gary",
            endEpoch = timestampAt(baseState, LocalTime.of(12, 42)),
            phase = GamePhase.GAME_OVER,
            teamOne = TeamLiveState("Viscous Coupling", TeamColorChoice.WHITE, score = 12),
            teamTwo = TeamLiveState("Animal", TeamColorChoice.RED, score = 15),
        )

        assertEquals(
            GameOverSummaryText(
                title = "Game summary",
                gameInformationLine = "Philly Open Mixed Division Masters Semifinal",
                observersLine = "Observers: Mike and Gary",
                startLine = "Start May 19, 2026 10:00 AM",
                endLine = "End time 12:42 PM",
                scoreLines = listOf("Animal 15", "Viscous Coupling 12"),
            ),
            state.gameOverSummaryText(),
        )
        assertEquals(listOf(state.teamTwo, state.teamOne), state.winnerFirstTeams())
        assertEquals(
            listOf(state.teamTwo.copy(score = 2), state.teamOne.copy(score = 1)),
            state.copy(
                teamOne = state.teamOne.copy(score = 1),
                teamTwo = state.teamTwo.copy(score = 2),
            ).winnerFirstTeams(),
        )
        val laterAlphabeticalTeam = state.teamOne.copy(name = "Z Team", score = 1)
        val earlierAlphabeticalTeam = state.teamTwo.copy(name = "A Team", score = 1)
        assertEquals(
            listOf(earlierAlphabeticalTeam, laterAlphabeticalTeam),
            state.copy(teamOne = laterAlphabeticalTeam, teamTwo = earlierAlphabeticalTeam).winnerFirstTeams(),
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
                        InGamePlayerCardEvent(CardType.YELLOW, index = 1, reason = CardReason(preset = "Taunting")),
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
            ),
        )

        assertEquals(
            GameOverTeamSummaryText(
                teamName = "Viscous Coupling",
                issuedCardLines = listOf(
                    "#7 Casey Handler: Yellow card -- Dangerous play",
                    "#12: Yellow card -- Taunting",
                    "No Number: Red card -- Egregious dangerous play",
                    "#12: Yellow card -- Dangerous play",
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
            division = GameDivision.OPEN,
            level = "Masters",
            gameContext = "Final",
            observers = "Mike and Gary",
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
                        InGamePlayerCardEvent(CardType.YELLOW, index = 0, reason = CardReason(preset = "Taunting")),
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

        assertEquals(
            """
            UltiObserver Game Summary
            Philly Open Open Division Masters Final
            Observers: Mike and Gary
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
            level = "Masters",
            gameContext = "Semifinals",
            observers = "Mike and Gary",
            nearEndName = "Road",
            farEndName = "Trees",
            pullingFromEnd = FieldEnd.NEAR,
            pullPromptTarget = PullPromptTarget.BOTH,
        )

        val state = createLiveGameState(setup)

        assertEquals("Philly Open", state.tournamentName)
        assertEquals(GameDivision.MIXED, state.division)
        assertEquals("Masters", state.level)
        assertEquals("Semifinals", state.gameContext)
        assertEquals("Mike and Gary", state.observers)
        assertEquals("Philly Open", state.toSetupState().tournamentName)
        assertEquals(GameDivision.MIXED, state.toSetupState().division)
        assertEquals("Masters", state.toSetupState().level)
        assertEquals("Semifinals", state.toSetupState().gameContext)
        assertEquals("Mike and Gary", state.toSetupState().observers)
        assertEquals("Road", state.toSetupState().fieldEndName(FieldEnd.NEAR))
        assertEquals("Trees", state.toSetupState().fieldEndName(FieldEnd.FAR))
        assertEquals("Road", state.fieldEndDisplayName(FieldEnd.NEAR))
        assertEquals("Trees", state.fieldEndDisplayName(FieldEnd.FAR))
        assertEquals("Viscous Coupling pulls from Road", state.toSetupState().startingPullSummary())
        assertEquals("Pull prompts for both ends", state.toSetupState().pullPromptSummary())
        assertEquals(true, state.toSetupState().usesMixedDivision())

        val defaultEndsSetup = setup.copy(nearEndName = "", farEndName = "", pullPromptTarget = PullPromptTarget.NEAR)
        assertEquals("Near end", defaultEndsSetup.fieldEndName(FieldEnd.NEAR))
        assertEquals("Far end", defaultEndsSetup.fieldEndName(FieldEnd.FAR))
        val defaultEndsState = state.copy(nearEndName = "", farEndName = "")
        assertEquals("Near end", defaultEndsState.fieldEndDisplayName(FieldEnd.NEAR))
        assertEquals("Far end", defaultEndsState.fieldEndDisplayName(FieldEnd.FAR))
        assertEquals("Pull prompts for Near end", defaultEndsSetup.pullPromptSummary())
        assertEquals("Road", PullPromptTarget.NEAR.displayText(setup))
        assertEquals("Trees", PullPromptTarget.FAR.displayText(setup))
        assertEquals("both ends", PullPromptTarget.BOTH.displayText(setup))
        assertEquals("neither end", PullPromptTarget.NEITHER.displayText(setup))
    }
}
