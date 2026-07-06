package rmjarvis.ultiobserver

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Tests for persisted event-log entries and compact display formatting.
 */
class TestEventLog : GameDomainTestFixtures() {
    /**
     * Verify first-pull entries use nominal start time unless the first point starts early.
     */
    @Test
    fun firstPullTime() {
        // A first pull after scheduled start uses the nominal game start time.
        val animal = TeamId.TEAM_TWO
        var state = standardLiveGameState(
            startTime = LocalTime.of(12, 0),
            pullingTeam = animal,
            pullingFromEnd = FieldEnd.NEAR,
        )
        state = state.beginLivePoint(timestampAt(state, LocalTime.of(12, 3)))
        assertEquals(
            listOf("12:00  First pull by Animal"),
            state.formatEventLogLines(),
        )

        // A first pull before scheduled start uses the actual early pull time.
        state = standardLiveGameState(
            startTime = LocalTime.of(12, 0),
            pullingTeam = animal,
            pullingFromEnd = FieldEnd.NEAR,
        )
        state = state.beginLivePoint(timestampAt(state, LocalTime.of(11, 58)))
        assertEquals(
            listOf("11:58  First pull by Animal"),
            state.formatEventLogLines(),
        )

        // A pre-first-point misconduct event does not prevent logging the first pull.
        state = standardLiveGameState(
            startTime = LocalTime.of(12, 0),
            pullingTeam = animal,
            pullingFromEnd = FieldEnd.NEAR,
        )
        state = state.assessBlueCard(animal, timestampAt(state, LocalTime.of(11, 55))).state
        state = state.beginLivePoint(timestampAt(state, LocalTime.of(12, 0)))
        assertEquals(
            listOf(
                "11:55  Blue card on Animal",
                "12:00  First pull by Animal",
            ),
            state.formatEventLogLines(),
        )
    }

    /**
     * Verify normal game actions append compact event-log entries in game-local time.
     */
    @Test
    fun significantGameEvents() {
        // Start a game whose rule settings let the narrative reach each event-log type.
        val vc = TeamId.TEAM_ONE
        val animal = TeamId.TEAM_TWO
        var state = standardLiveGameState(
            startTime = LocalTime.of(12, 0),
            rules = GameRules(
                gameTo = 5,
                useHalfCap = false,
                useSoftCap = false,
                useHardCap = false,
                timeoutsPerHalf = 1,
            ),
            pullingTeam = animal,
        )

        // Opening pull, misconduct, and timeout entries include the local game time.
        state = state.beginLivePoint(timestampAt(state, LocalTime.of(12, 0)))
        state = state.assessBlueCard(vc, timestampAt(state, LocalTime.of(12, 2))).state
        state = state.assessTimeout(vc, timestampAt(state, LocalTime.of(12, 3)))
            .state
            .continueLivePoint()
        state = state.assessYellowCard(
            animal,
            "23",
            timestampAt(state, LocalTime.of(12, 4)),
            playerName = "Jarvis",
        ).state
        state = state.assessYellowCard(
            animal,
            "23",
            timestampAt(state, LocalTime.of(12, 4)),
            playerName = "Jarvis",
        ).state
        state = state.assessTechnicalFoul(vc, timestampAt(state, LocalTime.of(12, 5))).state

        // Point, pull-violation, and time-violation entries use their specific outcomes.
        state = recordGoalAt(state, animal, LocalTime.of(12, 7))
        state = state.recordOffsides(timestampAt(state, LocalTime.of(12, 8)))
        state = state.recordFalseStart(timestampAt(state, LocalTime.of(12, 9)))
        state = state.startPullSequence(timestampAt(state, LocalTime.of(12, 10)))
        state = state.expiredPullDecisionState()
            .assessTimeViolation(animal, timestampAt(state, LocalTime.of(12, 11))).state
        state = state.expiredPullDecisionState()
            .assessTimeViolation(animal, timestampAt(state, LocalTime.of(12, 12))).state
        state = state.expiredPullDecisionState()
            .assessTimeViolation(animal, timestampAt(state, LocalTime.of(12, 13))).state

        // Halftime and game-over entries close the event-log narrative.
        state = startHalftimeNowAt(state, LocalTime.of(12, 14))
        state = endGameNowAt(state, LocalTime.of(12, 15))

        // The final event log should contain the representative entries in event order.
        assertEquals(
            listOf(
                "12:00  First pull by Animal",
                "12:02  Blue card on Viscous Coupling",
                "12:03  Timeout by Viscous Coupling",
                "12:04  Yellow card on Animal #23 Jarvis",
                "12:04  Yellow card on Animal #23 Jarvis",
                "12:05  Technical foul on Viscous Coupling",
                "12:07  Animal Goal",
                "12:08  Offsides on Animal",
                "12:09  False start on Viscous Coupling",
                "12:11  Time violation on Animal, warning",
                "12:12  Time violation on Animal, timeout charged",
                "12:13  Time violation on Animal, no timeout remaining",
                "12:14  Halftime",
                "12:15  Game over",
            ),
            state.formatEventLogLines(),
        )
    }

    /**
     * Verify event-log share text includes identifying game context and the full log.
     */
    @Test
    fun shareText() {
        // Build a completed short game with metadata, representative event rows, and a final
        // score.
        var state = standardLiveGameState(
            startDate = LocalDate.of(2026, 7, 3),
            startTime = LocalTime.of(12, 0),
            rules = GameRules(
                gameTo = 3,
                useHalfCap = false,
                useSoftCap = false,
                useHardCap = false,
            ),
            pullingTeam = TeamId.TEAM_TWO,
        ).copy(
            tournamentName = "Fall Brawl",
            division = GameDivision.MIXED,
            level = "Club",
            observerNames = listOf("Mike Jarvis", "Bobby"),
            teamOne = TeamState("Amp", TeamColorChoice.WHITE),
            teamTwo = TeamState("Animal", TeamColorChoice.RED),
        )
        val pregameState = state
        state = state.beginLivePoint(timestampAt(state, LocalTime.of(12, 0)))
        state = state.assessTimeout(TeamId.TEAM_ONE, timestampAt(state, LocalTime.of(12, 2))).state
            .continueLivePoint()
        state = recordGoalAt(state, TeamId.TEAM_TWO, LocalTime.of(12, 5))
        state = recordGoalFromCurrentStateAt(state, TeamId.TEAM_ONE, LocalTime.of(12, 12))
        state = recordGoalFromCurrentStateAt(state, TeamId.TEAM_TWO, LocalTime.of(12, 20))
        val halftimeState = state
        state = recordGoalFromCurrentStateAt(state, TeamId.TEAM_TWO, LocalTime.of(12, 28))

        // The share text keeps the event log intact and appends final score only for game over.
        assertEquals(
            """
            UltiObserver Event Log

            Animal vs Amp
            Fall Brawl Mixed Division Club
            Observers: Mike Jarvis, Bobby
            July 3, 2026

            12:00  First pull by Animal
            12:02  Timeout by Amp
            12:05  Animal Goal
            12:12  Amp Goal
            12:20  Animal Goal
            12:20  Halftime
            12:28  Animal Goal
            12:28  Game over

            Final score:
            Animal 3
            Amp 1
            """.trimIndent(),
            state.eventLogShareText(),
        )

        // In-progress games omit the final score block.
        assertEquals(
            """
            UltiObserver Event Log

            Animal vs Amp
            Fall Brawl Mixed Division Club
            Observers: Mike Jarvis, Bobby
            July 3, 2026

            12:00  First pull by Animal
            12:02  Timeout by Amp
            12:05  Animal Goal
            12:12  Amp Goal
            12:20  Animal Goal
            12:20  Halftime
            """.trimIndent(),
            halftimeState.eventLogShareText(),
        )

        // There is still some useful share text if nothing has been logged yet.
        assertEquals(
            """
            UltiObserver Event Log

            Amp vs Animal
            Fall Brawl Mixed Division Club
            Observers: Mike Jarvis, Bobby
            July 3, 2026

            No events logged yet.
            """.trimIndent(),
            pregameState.eventLogShareText(),
        )

        // Optional game context and observers are omitted when they are blank.
        assertEquals(
            """
            UltiObserver Event Log

            Animal vs Viscous Coupling
            January 1, 2026

            No events logged yet.
            """.trimIndent(),
            standardLiveGameState().eventLogShareText(),
        )
    }

    /**
     * Verify manual Blue/Tech count corrections log specific count deltas with a broad undo label.
     */
    @Test
    fun blueCardAndTechAdjustments() {
        // Count-only card and technical-foul adjustments can change several team counts at once.
        val state = standardLiveGameState(startTime = LocalTime.of(12, 0))
        val adjusted = state.adjustBlueCardsAndTechs(
            teamOneBlues = 2,
            teamOneTechnicalFouls = 2,
            teamTwoBlues = 0,
            teamTwoTechnicalFouls = 1,
            now = timestampAt(state, LocalTime.of(12, 5)),
        )

        // The undo label stays broad, while the event log lists each changed count.
        assertEquals("Adjust blue card/tech counts.", adjusted.lastEvent)
        assertEquals("Undo Adjust blue card/tech counts", adjusted.undoEntry?.label)
        assertEquals(
            listOf(
                "12:05  Adjusted Viscous Coupling blue cards +2",
                "12:05  Adjusted Viscous Coupling technical fouls +2",
                "12:05  Adjusted Animal technical fouls +1",
            ),
            adjusted.formatEventLogLines(),
        )
        assertEquals(state, adjusted.undoEntry?.previous)
    }

    /**
     * Verify manual corrections log the meaningful before/after deltas.
     */
    @Test
    fun manualCorrectionDeltas() {
        // Seed existing records so later corrections can log added, removed, and changed values.
        val vc = TeamId.TEAM_ONE
        val animal = TeamId.TEAM_TWO
        var state = standardLiveGameState(startTime = LocalTime.of(12, 0))
        state = state.assessYellowCard(animal, "17", timestampAt(state, LocalTime.of(12, 1)))
            .state
        state = state.assessTechnicalFoul(vc, timestampAt(state, LocalTime.of(12, 1))).state

        // Manual pull-violation corrections log one entry for each changed count.
        state = state.adjustPullViolations(
            teamOneOffsides = 0,
            teamOneFalseStarts = 1,
            teamOneMajorityPulls = 0,
            teamOneTimeViolations = state.teamOne.timeViolations,
            teamTwoOffsides = 1,
            teamTwoFalseStarts = 0,
            teamTwoMajorityPulls = 0,
            teamTwoTimeViolations = state.teamTwo.timeViolations,
            now = timestampAt(state, LocalTime.of(12, 2)),
        )

        // Manual card and technical-foul corrections log added and removed records.
        state = state.adjustCardsAndTf(
            teamOneBlues = 1,
            teamOneTechnicalFouls = 0,
            teamTwoBlues = 0,
            teamTwoTechnicalFouls = 0,
            teamOnePlayers = listOf(playerRecordWithCards("11", reds = 1)),
            teamTwoPlayers = emptyList(),
            now = timestampAt(state, LocalTime.of(12, 3)),
            undoLabel = "",
        )

        // Score and timeout corrections log the visible before/after deltas.
        state = state.adjustScore(
            teamOneScore = 3,
            teamTwoScore = 5,
            now = timestampAt(state, LocalTime.of(12, 4)),
        )
        state = state.adjustTimeouts(
            teamOneTimeoutsUsed = 1,
            teamTwoTimeoutsUsed = 0,
            teamOneFirstHalfTimeoutsUsed = state.teamOne.firstHalfTimeoutsUsed,
            teamTwoFirstHalfTimeoutsUsed = state.teamTwo.firstHalfTimeoutsUsed,
            now = timestampAt(state, LocalTime.of(12, 5)),
        )
        state = state.adjustTimeouts(
            teamOneTimeoutsUsed = 0,
            teamTwoTimeoutsUsed = 0,
            teamOneFirstHalfTimeoutsUsed = state.teamOne.firstHalfTimeoutsUsed,
            teamTwoFirstHalfTimeoutsUsed = state.teamTwo.firstHalfTimeoutsUsed,
            now = timestampAt(state, LocalTime.of(12, 6)),
        )

        // Follow-up corrections log removed pull violations and edited player-card records.
        state = state.adjustPullViolations(
            teamOneOffsides = 0,
            teamOneFalseStarts = 0,
            teamOneMajorityPulls = 0,
            teamOneTimeViolations = state.teamOne.timeViolations,
            teamTwoOffsides = 1,
            teamTwoFalseStarts = 0,
            teamTwoMajorityPulls = 0,
            teamTwoTimeViolations = state.teamTwo.timeViolations,
            now = timestampAt(state, LocalTime.of(12, 7)),
        )
        state = state.adjustCardsAndTf(
            teamOneBlues = 1,
            teamOneTechnicalFouls = 0,
            teamTwoBlues = 0,
            teamTwoTechnicalFouls = 0,
            teamOnePlayers = listOf(playerRecordWithCards("22", reds = 1, playerName = "Morgan")),
            teamTwoPlayers = emptyList(),
            now = timestampAt(state, LocalTime.of(12, 8)),
            undoLabel = "",
        )

        // The final event log should contain only the visible correction deltas.
        assertEquals(
            listOf(
                "12:01  Yellow card on Animal #17",
                "12:01  Technical foul on Viscous Coupling",
                "12:02  Adjusted Viscous Coupling false starts +1",
                "12:02  Adjusted Animal offsides +1",
                "12:03  Adjusted Viscous Coupling blue cards +1",
                "12:03  Adjusted Viscous Coupling technical fouls -1",
                "12:03  Added red card on Viscous Coupling #11",
                "12:03  Removed yellow card on Animal #17",
                "12:04  Adjusted score: Viscous Coupling 3 - Animal 5",
                "12:05  Adjusted Viscous Coupling timeouts +1",
                "12:06  Adjusted Viscous Coupling timeouts -1",
                "12:07  Adjusted Viscous Coupling false starts -1",
                "12:08  Changed red card on Viscous Coupling from #11 to #22 Morgan",
            ),
            state.formatEventLogLines(),
        )
        assertEquals(vc, state.eventLog.last().team)
    }

    /**
     * Verify same-player card corrections do not add event-log entries.
     */
    @Test
    fun samePlayerCardCorrections() {
        // Record the original player card through the normal card pathway.
        val animal = TeamId.TEAM_TWO
        var state = standardLiveGameState(startTime = LocalTime.of(12, 0))
        state = state.assessYellowCard(animal, "12", timestampAt(state, LocalTime.of(12, 1)))
            .state

        // Editing the player's name does not create a separate event-log correction.
        state = state.adjustCardsAndTf(
            teamOneBlues = 0,
            teamOneTechnicalFouls = 0,
            teamTwoBlues = 0,
            teamTwoTechnicalFouls = 0,
            teamOnePlayers = emptyList(),
            teamTwoPlayers = listOf(playerRecordWithCards("12", yellows = 1, playerName = "Mike")),
            now = timestampAt(state, LocalTime.of(12, 2)),
            undoLabel = "",
        )

        // Editing the reason details also leaves the event log unchanged.
        state = state.adjustCardsAndTf(
            teamOneBlues = 0,
            teamOneTechnicalFouls = 0,
            teamTwoBlues = 0,
            teamTwoTechnicalFouls = 0,
            teamOnePlayers = emptyList(),
            teamTwoPlayers = listOf(
                PlayerRecord(
                    jerseyNumber = "12",
                    playerName = "Mike",
                    cards = listOf(
                        InGamePlayerCardEvent(
                            CardType.YELLOW,
                            index = 0,
                            reason = CardReason(preset = "Dangerous play"),
                        ),
                    ),
                )
            ),
            now = timestampAt(state, LocalTime.of(12, 3)),
            undoLabel = "",
        )

        // Same-player card edits leave only the original card event in the event log.
        assertEquals(
            listOf(
                "12:01  Yellow card on Animal #12",
            ),
            state.formatEventLogLines(),
        )
    }

    /**
     * Verify undo restores the previous event log along with the previous game state.
     */
    @Test
    fun undoRestoresEventLog() {
        // Record one event-log entry that will be removed by undo.
        val animal = TeamId.TEAM_TWO
        val state = standardLiveGameState(startTime = LocalTime.of(12, 0))
        val afterCard = state.assessRedCard(
            team = animal,
            jerseyNumber = "",
            now = timestampAt(state, LocalTime.of(12, 10)),
            playerName = "No Number",
        ).state

        // Undoing the only logged event returns the event log to empty.
        assertEquals(
            listOf("12:10  Red card on Animal No Number"),
            afterCard.formatEventLogLines(),
        )
        assertEquals(emptyList<String>(), afterCard.undoLastAction().formatEventLogLines())
    }

    /**
     * Verify deterministic clock and duration text used by event-log and timer displays.
     */
    @Test
    fun timeDisplays() {
        // Clock formatting covers midnight, noon, morning, and afternoon values.
        assertEquals("12:00 AM", formatClockTime(LocalTime.MIDNIGHT))
        assertEquals("12:00 PM", formatClockTime(LocalTime.NOON))
        assertEquals("9:05 AM", formatClockTime(LocalTime.of(9, 5)))
        assertEquals("3:30 PM", formatClockTime(LocalTime.of(15, 30)))

        // Duration formatting clamps negative durations to zero and formats minute/second
        // boundaries.
        assertEquals("0:00", formatDuration(Duration.ofSeconds(-3)))
        assertEquals("0:00", formatDuration(Duration.ZERO))
        assertEquals("0:59", formatDuration(Duration.ofSeconds(59)))
        assertEquals("1:00", formatDuration(Duration.ofSeconds(60)))
        assertEquals("1:01", formatDuration(Duration.ofSeconds(61)))
        assertEquals("61:01", formatDuration(Duration.ofSeconds(3661)))
    }
}
