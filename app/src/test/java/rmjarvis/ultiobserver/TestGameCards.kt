package rmjarvis.ultiobserver

import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

/// Tests for player cards, team cards, technical fouls, and misconduct consequences in the game model.
class TestGameCards : GameDomainTestFixtures() {
    /**
     * Test yellow, red, blue, and technical-foul handling from public card assessment APIs.
     * Emphasize team card points, per-player records, and misconduct-threshold messages.
     */
    @Test
    fun cardsAndTechnicalFouls() {
        val VC = TeamId.TEAM_ONE
        val ANIMAL = TeamId.TEAM_TWO

        /**
         * Return a single player's card record from a team after a test action.
         *
         * @param state The live game state to inspect.
         * @param team The team whose player records should be searched.
         * @param jerseyNumber The player number expected to have exactly one record.
         */
        fun playerRecord(
            state: GameState,
            team: TeamId,
            jerseyNumber: String,
        ): InGamePlayerCardRecord {
            return state.playerCards(team).single { it.jerseyNumber == jerseyNumber }
        }

        // Record a first yellow for a numbered Viscous Coupling player and verify team and player state.
        var state = standardLiveGameState()
        var cardResult = state.assessYellowCard(VC, "17")
        state = cardResult.state
        assertFalse(cardResult.needsMisconductChoice)
        assertEquals("Yellow card on player 17.\nViscous Coupling has 1 blue card.", cardResult.message())
        assertEquals("Misconduct", cardResult.event.formatPopupTitle())
        assertEquals(1, state.teamYellowCards(VC))
        assertEquals(0, state.teamRedCards(VC))
        assertEquals(1, state.teamCardTotal(VC))
        assertEquals(InGamePlayerCardRecord("17", yellows = 1), playerRecord(state, VC, "17"))
        assertEquals("Undo Yellow on #17 of Viscous Coupling", state.undoEntry?.label)

        // A second yellow to the same player creates a game suspension, but adds only one more team card point.
        cardResult = state.assessYellowCard(VC, "17")
        state = cardResult.state
        assertFalse(cardResult.needsMisconductChoice)
        assertEquals(
            "Second yellow on player 17.\n" +
                "Player 17 receives a game suspension.\n" +
                "Viscous Coupling has 2 total blue cards.",
            cardResult.message(),
        )
        assertEquals(2, state.teamYellowCards(VC))
        assertEquals(0, state.teamRedCards(VC))
        assertEquals(2, state.teamCardTotal(VC))
        assertEquals(InGamePlayerCardRecord("17", yellows = 2), playerRecord(state, VC, "17"))
        assertEquals("Undo Second yellow on #17 of Viscous Coupling", state.undoEntry?.label)
        assertUndoRestores(cardResult.state.undoEntry!!.previous, state)

        // A third team-card point between points gives the pulling-team misconduct field-position cue.
        cardResult = state.assessBlueCard(VC)
        state = cardResult.state
        assertFalse(cardResult.needsMisconductChoice)
        assertEquals(1, state.teamOne.blueCards)
        assertEquals(3, state.teamCardTotal(VC))
        assertEquals(1, state.playerCards(VC).size)
        assertEquals(
            "Viscous Coupling has 3 total blue cards.\n\nPenalty against pulling team. No pull. Receiving team starts at attacking brick.",
            cardResult.message(),
        )
        assertEquals("Misconduct penalty", cardResult.event.formatPopupTitle())
        assertTrue(state.pullSkippedForCurrentPoint)
        assertEquals(CountdownKind.MISCONDUCT_BETWEEN_POINTS, state.countdown?.kind)
        assertEquals("Offense set in", state.countdown?.label)
        assertEquals(90, state.countdown?.durationSeconds)
        assertEquals(state.startEpoch + 90_000L, state.countdown?.targetEpoch)
        assertEquals(state, state.withPendingMisconductCountdown())
        assertFalse(state.canRecordPullInfraction(VC))
        assertFalse(state.canRecordPullInfraction(ANIMAL))
        assertEquals(state, state.assessPullInfraction(VC).state)
        assertEquals(state, state.assessPullInfraction(ANIMAL).state)
        assertTrue(state.canReportMisconductOffenseSet(state.startEpoch + 70_000L))
        val earlySetState = state.reportMisconductOffenseSet(state.startEpoch + 70_000L)
        assertEquals(CountdownKind.MISCONDUCT_DEFENSE_CHECK, earlySetState.countdown?.kind)
        assertEquals("Defense check in", earlySetState.countdown?.label)
        assertEquals(30, earlySetState.countdown?.durationSeconds)
        assertEquals(state.startEpoch + 100_000L, earlySetState.countdown?.targetEpoch)
        assertEquals(
            GamePhase.LIVE_POINT,
            earlySetState.applyExpiredCountdownTransitions(earlySetState.countdown!!.targetEpoch).phase,
        )
        assertEquals("Point is live.", earlySetState.applyExpiredCountdownTransitions(earlySetState.countdown!!.targetEpoch).lastEvent)
        assertFalse(state.canReportMisconductOffenseSet(state.startEpoch + 85_000L))
        assertEquals(state, state.reportMisconductOffenseSet(state.startEpoch + 85_000L))
        assertFalse(state.canReportMisconductOffenseSet(state.countdown!!.targetEpoch))
        assertFalse(standardLiveGameState().canReportMisconductOffenseSet(state.startEpoch))
        assertFalse(
            state.copy(phase = GamePhase.LIVE_POINT)
                .canReportMisconductOffenseSet(state.startEpoch + 70_000L),
        )
        state = state.applyExpiredCountdownTransitions(state.countdown!!.targetEpoch)
        assertEquals(GamePhase.LIVE_POINT, state.phase)
        assertFalse(state.pullSkippedForCurrentPoint)
        assertNull(state.countdown)
        assertEquals("Point is live.", state.lastEvent)
        assertFalse(state.canReportMisconductOffenseSet(state.startEpoch + 91_000L))
        assertEquals(state, state.reportMisconductOffenseSet(state.startEpoch + 91_000L))

        // The no-pull restriction is only for the current point sequence.
        state = recordGoalFromCurrentStateAt(state, VC, LocalTime.of(12, 5))
        assertFalse(state.pullSkippedForCurrentPoint)
        assertTrue(state.canRecordPullInfraction(VC))

        val missingCountdownException = assertThrows(NullPointerException::class.java) {
            val baseState = standardLiveGameState()
            baseState.copy(
                countdown = null,
                teamOne = baseState.teamOne.copy(blueCards = 2),
            ).assessBlueCard(VC)
        }
        assertNull(missingCountdownException.message)
        assertEquals(standardLiveGameState(), standardLiveGameState().startMisconductCountdown(1_010_000L))

        state = standardLiveGameState()
        state = state.copy(teamOne = state.teamOne.copy(blueCards = 2))
        cardResult = state.assessYellowCard(VC, "14")
        assertFalse(cardResult.needsMisconductChoice)
        assertEquals(
            "Yellow card on player 14.\nViscous Coupling has 3 total blue cards.\n\n" +
                "Penalty against pulling team. No pull. Receiving team starts at attacking brick.",
            cardResult.message(),
        )
        assertEquals(CountdownKind.MISCONDUCT_BETWEEN_POINTS, cardResult.state.countdown?.kind)

        // During a live point, a first yellow that reaches the misconduct threshold needs an offense/defense choice.
        state = standardLiveGameState().beginLivePoint()
        state = state.assessBlueCard(VC).state
        state = state.assessBlueCard(VC).state
        cardResult = state.assessYellowCard(VC, "14")
        state = cardResult.state
        assertTrue(cardResult.needsMisconductChoice)
        assertEquals("Yellow card on player 14.\nViscous Coupling has 3 total blue cards.", cardResult.message())
        assertEquals(3, state.teamCardTotal(VC))

        // A red for a player with no prior yellow counts as two team card points and records a red.
        state = standardLiveGameState()
        cardResult = state.assessRedCard(ANIMAL, "23")
        state = cardResult.state
        assertFalse(cardResult.needsMisconductChoice)
        assertEquals(
            "Red card on player 23.\n" +
                "Player 23 receives a game suspension.\n" +
                "Animal has 2 total blue cards.",
            cardResult.message(),
        )
        assertEquals("Misconduct", cardResult.event.formatPopupTitle())
        assertEquals(0, state.teamYellowCards(ANIMAL))
        assertEquals(1, state.teamRedCards(ANIMAL))
        assertEquals(2, state.teamCardTotal(ANIMAL))
        assertEquals(InGamePlayerCardRecord("23", reds = 1), playerRecord(state, ANIMAL, "23"))
        assertUndoRestores(cardResult.state.undoEntry!!.previous, state)

        // During a live point, a red that reaches the misconduct threshold needs an offense/defense choice.
        state = standardLiveGameState().beginLivePoint()
        state = state.assessYellowCard(ANIMAL, "8").state
        cardResult = state.assessRedCard(ANIMAL, "23")
        state = cardResult.state
        assertTrue(cardResult.needsMisconductChoice)
        assertEquals(
            "Red card on player 23.\n" +
                "Player 23 receives a game suspension.\n" +
                "Animal has 3 total blue cards.",
            cardResult.message(),
        )
        val misconductPrompt = cardResult.misconductPrompt()
        assertEquals("Misconduct penalty", misconductPrompt.formatTitle())
        val misconductGamePrompt: GamePrompt = misconductPrompt
        assertEquals("Misconduct penalty", misconductGamePrompt.formatTitle())
        assertEquals(
            "Red card on player 23.\n" +
                "Player 23 receives a game suspension.\n" +
                "Animal has 3 total blue cards.\n\nWas this against the offense or defense?",
            misconductPrompt.formatMessage(),
        )
        assertEquals(
            "Red card on player 23.\n" +
                "Player 23 receives a game suspension.\n" +
                "Animal has 3 total blue cards.\n\nWas this against the offense or defense?",
            misconductGamePrompt.formatMessage(),
        )
        assertEquals(
            "Red card on player 23.\n" +
                "Player 23 receives a game suspension.\n" +
                "Animal has 3 total blue cards.\n\n" +
                "Disc moves to the reverse brick in the end zone being defended. " +
                "Defense may instead leave the disc where it stopped.\n\n" +
                "Offense has 30 seconds to set. Then defense has 20 seconds to check the disc in.",
            misconductPrompt.resolutionMessage(againstOffense = true),
        )
        assertTrue(
            misconductPrompt.resolutionMessage(againstOffense = false)
                .contains("Disc moves to the brick nearest the attacking end zone."),
        )
        assertEquals(3, state.teamCardTotal(ANIMAL))

        // A red for a player who already has a yellow is distinct from recording the red as a second yellow.
        state = standardLiveGameState()
        state = state.assessYellowCard(ANIMAL, "8").state
        cardResult = state.assessRedCard(ANIMAL, "8")
        state = cardResult.state
        assertFalse(cardResult.needsMisconductChoice)
        assertEquals(1, state.teamYellowCards(ANIMAL))
        assertEquals(1, state.teamRedCards(ANIMAL))
        assertEquals(3, state.teamCardTotal(ANIMAL))
        assertEquals(InGamePlayerCardRecord("8", yellows = 1, reds = 1), playerRecord(state, ANIMAL, "8"))
        assertEquals(
            "Red card on player 8.\n" +
                "Player 8 is suspended for the rest of the tournament.\n" +
                "Animal has 3 total blue cards.\n\n" +
                "Penalty against receiving team. No pull. Disc at negative brick in defending end zone.",
            cardResult.message(),
        )
        assertTrue(
            cardResult.message()!!.contains("Player 8 is suspended for the rest of the tournament."),
        )

        state = standardLiveGameState()
        state = state.assessYellowCard(ANIMAL, "8").state
        cardResult = state.assessSecondYellowCard(ANIMAL, "8")
        state = cardResult.state
        assertFalse(cardResult.needsMisconductChoice)
        assertEquals(2, state.teamYellowCards(ANIMAL))
        assertEquals(0, state.teamRedCards(ANIMAL))
        assertEquals(2, state.teamCardTotal(ANIMAL))
        assertEquals(InGamePlayerCardRecord("8", yellows = 2), playerRecord(state, ANIMAL, "8"))
        assertEquals(
            "Second yellow on player 8.\n" +
                "Player 8 receives a game suspension.\n" +
                "Animal has 2 total blue cards.",
            cardResult.message(),
        )

        // The N/A pathways distinguish same-unknown-player second yellow from a first yellow.
        state = standardLiveGameState()
        state = state.assessYellowCard(VC, UNKNOWN_PLAYER_NUMBER).state
        assertTrue(state.playerHasYellowThisGame(VC, UNKNOWN_PLAYER_NUMBER))
        cardResult = state.assessSecondYellowCard(VC, UNKNOWN_PLAYER_NUMBER)
        state = cardResult.state
        assertEquals(2, state.teamYellowCards(VC))
        assertEquals(0, state.teamRedCards(VC))
        assertEquals(InGamePlayerCardRecord(UNKNOWN_PLAYER_NUMBER, yellows = 2), playerRecord(state, VC, UNKNOWN_PLAYER_NUMBER))
        assertEquals(
            "Second yellow on player N/A.\n" +
                "The player receives a game suspension.\n" +
                "Viscous Coupling has 2 total blue cards.",
            cardResult.message(),
        )

        state = standardLiveGameState()
        state = state.assessYellowCard(VC, UNKNOWN_PLAYER_NUMBER).state
        cardResult = state.assessFirstYellowCard(VC, UNKNOWN_PLAYER_NUMBER)
        state = cardResult.state
        assertEquals(2, state.teamYellowCards(VC))
        assertEquals(0, state.teamRedCards(VC))
        assertEquals(2, state.teamCardTotal(VC))
        assertFalse(cardResult.message()!!.startsWith("Second yellow on"))

        // Prior cards from this tournament surface the tournament suspension thresholds.
        state = createLiveGameState(
            standardGameSetup(startTime = LocalTime.of(11, 0)).copy(
                priorCards = listOf(
                    PlayerCardRecord(ANIMAL, "44", priorYellows = 2, priorReds = 0),
                    PlayerCardRecord(VC, "45", priorYellows = 2, priorReds = 0),
                    PlayerCardRecord(VC, "44", priorYellows = 2, priorReds = 0),
                ),
            )
        )
        cardResult = state.assessYellowCard(VC, "44")
        assertEquals(
            "Yellow card on player 44.\n" +
                "Player 44 is suspended for the rest of the tournament.\n" +
                "Viscous Coupling has 1 blue card.",
            cardResult.message(),
        )

        state = createLiveGameState(
            standardGameSetup(startTime = LocalTime.of(11, 0)).copy(
                priorCards = listOf(PlayerCardRecord(ANIMAL, "31", priorYellows = 0, priorReds = 1)),
            )
        )
        cardResult = state.assessRedCard(ANIMAL, "31")
        assertEquals(
            "Red card on player 31.\n" +
                "Player 31 is suspended for the rest of the tournament.\n" +
                "Animal has 2 total blue cards.",
            cardResult.message(),
        )

        state = createLiveGameState(
            standardGameSetup(startTime = LocalTime.of(11, 0)).copy(
                priorCards = listOf(PlayerCardRecord(ANIMAL, "35", priorYellows = 0, priorReds = 1)),
            )
        ).copy(
            phase = GamePhase.HALFTIME,
            halftimeTaken = true,
        )
        cardResult = state.assessRedCard(ANIMAL, "35")
        assertEquals(
            "Red card on player 35.\n" +
                "Player 35 is suspended for the rest of the tournament.\n" +
                "Animal has 2 total blue cards.",
            cardResult.message(),
        )

        state = createLiveGameState(
            standardGameSetup(startTime = LocalTime.of(11, 0)).copy(
                priorCards = listOf(PlayerCardRecord(ANIMAL, "36", priorYellows = 1, priorReds = 0)),
            )
        )
        state = state.assessYellowCard(ANIMAL, "36").state.copy(
            phase = GamePhase.HALFTIME,
            halftimeTaken = true,
        )
        cardResult = state.assessYellowCard(ANIMAL, "36")
        assertEquals(
            "Second yellow on player 36.\n" +
                "Player 36 is suspended for the rest of the tournament.\n" +
                "Animal has 2 total blue cards.",
            cardResult.message(),
        )

        state = standardLiveGameState().copy(
            phase = GamePhase.BETWEEN_POINTS,
            halftimeTaken = true,
        )
        cardResult = state.assessRedCard(ANIMAL, "29")
        assertEquals(
            "Red card on player 29.\n" +
                "Player 29 receives a game suspension.\n" +
                "Player 29 must also sit out the first half of the next game, if there is one.\n" +
                "Animal has 2 total blue cards.",
            cardResult.message(),
        )

        state = standardLiveGameState().copy(
            phase = GamePhase.HALFTIME,
            halftimeTaken = true,
        )
        cardResult = state.assessRedCard(ANIMAL, "30")
        assertEquals(
            "Red card on player 30.\n" +
                "Player 30 receives a game suspension.\n" +
                "Player 30 must also sit out the first half of the next game, if there is one.\n" +
                "Animal has 2 total blue cards.",
            cardResult.message(),
        )

        state = standardLiveGameState().copy(
            phase = GamePhase.GAME_OVER,
            halftimeTaken = true,
        )
        cardResult = state.assessRedCard(ANIMAL, "32")
        assertEquals(
            "Red card on player 32.\n" +
                "Player 32 receives a game suspension.\n" +
                "Player 32 must also sit out the first half of the next game, if there is one.\n" +
                "Animal has 2 total blue cards.",
            cardResult.message(),
        )

        // Blue cards count as one team card point each and do not create per-player card records.
        state = standardLiveGameState()
        cardResult = state.assessBlueCard(ANIMAL)
        state = cardResult.state
        assertFalse(cardResult.needsMisconductChoice)
        assertEquals("Animal has 1 blue card.", cardResult.message())
        assertEquals(1, state.teamTwo.blueCards)
        assertEquals(1, state.teamCardTotal(ANIMAL))
        assertTrue(state.playerCards(ANIMAL).isEmpty())

        cardResult = state.assessBlueCard(ANIMAL)
        state = cardResult.state
        assertFalse(cardResult.needsMisconductChoice)
        assertEquals("Animal has 2 blue cards.", cardResult.message())
        assertEquals(2, state.teamTwo.blueCards)
        assertEquals(2, state.teamCardTotal(ANIMAL))

        cardResult = state.assessBlueCard(ANIMAL)
        state = cardResult.state
        assertFalse(cardResult.needsMisconductChoice)
        assertEquals(3, state.teamTwo.blueCards)
        assertEquals(3, state.teamCardTotal(ANIMAL))
        assertEquals(
            "Animal has 3 blue cards.\n\nPenalty against receiving team. No pull. Disc at negative brick in defending end zone.",
            cardResult.message(),
        )

        cardResult = state.assessBlueCard(ANIMAL)
        state = cardResult.state
        assertFalse(cardResult.needsMisconductChoice)
        assertEquals(4, state.teamTwo.blueCards)
        assertEquals(4, state.teamCardTotal(ANIMAL))
        assertEquals(
            "Animal has 4 blue cards.\n\nPenalty against receiving team. No pull. Disc at negative brick in defending end zone.",
            cardResult.message(),
        )

        // After game over, stale card actions can arrive from UI timing, but should not create no-pull guidance.
        state = standardLiveGameState().copy(
            phase = GamePhase.GAME_OVER,
            teamOne = standardLiveGameState().teamOne.copy(blueCards = 2),
        )
        cardResult = state.assessBlueCard(VC)
        state = cardResult.state
        assertEquals(GamePhase.GAME_OVER, state.phase)
        assertEquals(3, state.teamOne.blueCards)
        assertFalse(state.pullSkippedForCurrentPoint)

        // Technical fouls use a separate count, with the same third-and-later misconduct handling.
        state = standardLiveGameState()
        var technicalFoulResult = state.assessTechnicalFoul(ANIMAL)
        state = technicalFoulResult.state
        assertFalse(technicalFoulResult.needsMisconductChoice)
        assertEquals("Animal has 1 technical foul.", technicalFoulResult.message())
        assertEquals("Misconduct", technicalFoulResult.event.formatPopupTitle())
        assertEquals(1, state.teamTwo.technicalFouls)
        assertEquals(0, state.teamCardTotal(ANIMAL))

        technicalFoulResult = state.assessTechnicalFoul(ANIMAL)
        state = technicalFoulResult.state
        assertFalse(technicalFoulResult.needsMisconductChoice)
        assertEquals("Animal has 2 technical fouls.", technicalFoulResult.message())
        assertEquals(2, state.teamTwo.technicalFouls)

        technicalFoulResult = state.assessTechnicalFoul(ANIMAL)
        state = technicalFoulResult.state
        assertFalse(technicalFoulResult.needsMisconductChoice)
        assertEquals(3, state.teamTwo.technicalFouls)
        assertEquals(
            "Animal has 3 technical fouls.\n\nPenalty against receiving team. No pull. Disc at negative brick in defending end zone.",
            technicalFoulResult.message(),
        )
        assertEquals("Misconduct penalty", technicalFoulResult.event.formatPopupTitle())

        // After Animal scores, they are the pulling team, so the next technical foul uses the pulling-team cue.
        state = recordGoalFromCurrentStateAt(state, ANIMAL, LocalTime.of(11, 5))
        assertEquals(ANIMAL, state.pullingTeam)

        technicalFoulResult = state.assessTechnicalFoul(ANIMAL)
        state = technicalFoulResult.state
        assertFalse(technicalFoulResult.needsMisconductChoice)
        assertEquals(4, state.teamTwo.technicalFouls)
        assertEquals(
            "Animal has 4 technical fouls.\n\nPenalty against pulling team. No pull. Receiving team starts at attacking brick.",
            technicalFoulResult.message(),
        )

        // During a live point, third-and-later misconduct asks for offense/defense context instead of guessing.
        state = standardLiveGameState().beginLivePoint()
        state = state.assessBlueCard(VC).state
        state = state.assessBlueCard(VC).state
        cardResult = state.assessBlueCard(VC)
        state = cardResult.state
        assertEquals(GamePhase.LIVE_POINT, state.phase)
        assertTrue(cardResult.needsMisconductChoice)
        assertEquals("Viscous Coupling has 3 blue cards.", cardResult.message())

        val prompt = cardResult.misconductPrompt().formatMessage()
        assertTrue(prompt.contains("Was this against the offense or defense?"))
        assertTrue(
            cardResult.misconductPrompt().resolutionMessage(againstOffense = true)
                .contains("Disc moves to the reverse brick in the end zone being defended."),
        )
        assertTrue(
            cardResult.misconductPrompt().resolutionMessage(againstOffense = false)
                .contains("Disc moves to the brick nearest the attacking end zone."),
        )

        // Technical fouls hit the same live-point misconduct choice when Viscous Coupling reaches the threshold.
        state = standardLiveGameState().beginLivePoint()
        state = state.assessTechnicalFoul(VC).state
        state = state.assessTechnicalFoul(VC).state
        technicalFoulResult = state.assessTechnicalFoul(VC)
        state = technicalFoulResult.state
        assertEquals(GamePhase.LIVE_POINT, state.phase)
        assertTrue(technicalFoulResult.needsMisconductChoice)
        assertEquals("Viscous Coupling has 3 technical fouls.", technicalFoulResult.message())
        assertTrue(
            technicalFoulResult.misconductPrompt().resolutionMessage(againstOffense = true)
                .contains("Disc moves to the reverse brick in the end zone being defended."),
        )

        // Exercise the player-card assignment helpers used by the UI reconciliation prompts.
        var cardAssignments = emptyList<InGamePlayerCardRecord>()
        cardAssignments = addPlayerCardAssignment(cardAssignments, "17", CardType.YELLOW)
        assertEquals(listOf(InGamePlayerCardRecord("17", yellows = 1)), cardAssignments)
        cardAssignments = addPlayerCardAssignment(cardAssignments, "17", CardType.RED)
        assertEquals(listOf(InGamePlayerCardRecord("17", yellows = 1, reds = 1)), cardAssignments)
        cardAssignments = removePlayerCardAssignment(cardAssignments, "8", CardType.YELLOW)
        assertEquals(listOf(InGamePlayerCardRecord("17", yellows = 1, reds = 1)), cardAssignments)
        cardAssignments = removePlayerCardAssignment(cardAssignments, "17", CardType.YELLOW)
        assertEquals(listOf(InGamePlayerCardRecord("17", reds = 1)), cardAssignments)
        cardAssignments = addPlayerCardAssignment(cardAssignments, "17", CardType.YELLOW)
        cardAssignments = removePlayerCardAssignment(cardAssignments, "17", CardType.RED)
        assertEquals(listOf(InGamePlayerCardRecord("17", yellows = 1)), cardAssignments)
        cardAssignments = addPlayerCardAssignment(cardAssignments, "17", CardType.RED)
        cardAssignments = removePlayerCardAssignment(cardAssignments, "17", CardType.RED)
        assertEquals(listOf(InGamePlayerCardRecord("17", yellows = 1)), cardAssignments)
        cardAssignments = listOf(
            InGamePlayerCardRecord("17", yellows = 1),
            InGamePlayerCardRecord("8", reds = 1),
        )
        cardAssignments = removePlayerCardAssignment(cardAssignments, "17", CardType.YELLOW)
        assertEquals(listOf(InGamePlayerCardRecord("8", reds = 1)), cardAssignments)
        cardAssignments = addPlayerCardAssignment(cardAssignments, "23", CardType.YELLOW)
        cardAssignments = addPlayerCardAssignment(cardAssignments, "8", CardType.YELLOW)
        assertEquals(
            listOf(
                InGamePlayerCardRecord("8", yellows = 1, reds = 1),
                InGamePlayerCardRecord("23", yellows = 1),
            ),
            cardAssignments,
        )

        assertTrue(
            canAddPlayerCardAssignment(
                emptyList(),
                "99",
                CardType.YELLOW,
            ),
        )
        assertTrue(
            canAddPlayerCardAssignment(
                listOf(InGamePlayerCardRecord("17", yellows = 1)),
                "17",
                CardType.YELLOW,
            ),
        )
        assertTrue(
            canAddPlayerCardAssignment(
                listOf(InGamePlayerCardRecord("17", yellows = 1)),
                "17",
                CardType.RED,
            ),
        )
        assertFalse(
            canAddPlayerCardAssignment(
                listOf(InGamePlayerCardRecord("17", yellows = 2)),
                "17",
                CardType.RED,
            ),
        )
        assertFalse(
            canAddPlayerCardAssignment(
                listOf(InGamePlayerCardRecord("17", reds = 1)),
                "17",
                CardType.RED,
            ),
        )

        assertFalse(standardLiveGameState().playerHasYellowThisGame(VC, "99"))
        assertFalse(
            standardLiveGameState().copy(
                teamOnePlayerCards = listOf(InGamePlayerCardRecord("99")),
            ).playerHasYellowThisGame(
                VC,
                "99",
            )
        )

        val adjustmentStepState = standardLiveGameState().copy(
            teamOnePlayerCards = listOf(
                InGamePlayerCardRecord("17", yellows = 1),
                InGamePlayerCardRecord("23", reds = 1),
            ),
            teamTwoPlayerCards = listOf(
                InGamePlayerCardRecord("8", yellows = 2),
            ),
        )
        val adjustmentSteps = adjustmentStepState.buildPlayerCardAdjustmentSteps(
            teamOneYellows = 2,
            teamOneReds = 0,
            teamTwoYellows = 1,
            teamTwoReds = 0,
        )
        val expectedAdjustmentSteps = listOf(
            PlayerCardAdjustmentStep(VC, CardType.YELLOW, PlayerCardAdjustmentMode.ADD),
            PlayerCardAdjustmentStep(VC, CardType.RED, PlayerCardAdjustmentMode.REMOVE),
            PlayerCardAdjustmentStep(ANIMAL, CardType.YELLOW, PlayerCardAdjustmentMode.REMOVE),
        )
        assertEquals(expectedAdjustmentSteps, adjustmentSteps)
        val (firstStepTeam, firstStepType, firstStepMode) = adjustmentSteps.first()
        assertEquals(VC, firstStepTeam)
        assertEquals(CardType.YELLOW, firstStepType)
        assertEquals(PlayerCardAdjustmentMode.ADD, firstStepMode)
        assertEquals(VC, adjustmentSteps.first().team)
        assertEquals(CardType.YELLOW, adjustmentSteps.first().cardType)
        assertEquals(PlayerCardAdjustmentMode.ADD, adjustmentSteps.first().mode)
        assertEquals(
            expectedAdjustmentSteps.first(),
            adjustmentSteps.first().copy(),
        )
        assertEquals(
            PlayerCardAdjustmentStep(ANIMAL, CardType.RED, PlayerCardAdjustmentMode.REMOVE),
            adjustmentSteps.first().copy(
                team = ANIMAL,
                cardType = CardType.RED,
                mode = PlayerCardAdjustmentMode.REMOVE,
            ),
        )
        assertTrue(adjustmentSteps.first().toString().contains("mode=ADD"))
        assertEquals(
            PlayerCardAdjustmentStep(VC, CardType.YELLOW, PlayerCardAdjustmentMode.ADD).hashCode(),
            adjustmentSteps.first().hashCode(),
        )
        assertFalse(adjustmentSteps.first() == adjustmentSteps.first().copy(team = ANIMAL))
        assertFalse(adjustmentSteps.first() == adjustmentSteps.first().copy(cardType = CardType.RED))
        assertFalse(adjustmentSteps.first() == adjustmentSteps.first().copy(mode = PlayerCardAdjustmentMode.REMOVE))
        assertFalse(adjustmentSteps.first().equals("not a card adjustment step"))
        val yellowRemovalCandidates = playerCardRemovalCandidates(
            adjustmentStepState.teamTwoPlayerCards,
            CardType.YELLOW,
        )
        assertEquals(
            listOf(PlayerCardRemovalCandidate("8", cardCount = 2)),
            yellowRemovalCandidates,
        )
        val (candidateJerseyNumber, candidateCardCount) = yellowRemovalCandidates.single()
        assertEquals("8", candidateJerseyNumber)
        assertEquals(2, candidateCardCount)
        assertEquals("8", yellowRemovalCandidates.single().jerseyNumber)
        assertEquals(2, yellowRemovalCandidates.single().cardCount)
        assertEquals(
            PlayerCardRemovalCandidate("8", cardCount = 2),
            yellowRemovalCandidates.single().copy(),
        )
        assertEquals(
            PlayerCardRemovalCandidate("9", cardCount = 1),
            yellowRemovalCandidates.single().copy(jerseyNumber = "9", cardCount = 1),
        )
        assertTrue(yellowRemovalCandidates.single().toString().contains("cardCount=2"))
        assertEquals(
            PlayerCardRemovalCandidate("8", cardCount = 2).hashCode(),
            yellowRemovalCandidates.single().hashCode(),
        )
        assertFalse(yellowRemovalCandidates.single() == yellowRemovalCandidates.single().copy(jerseyNumber = "9"))
        assertFalse(yellowRemovalCandidates.single() == yellowRemovalCandidates.single().copy(cardCount = 1))
        assertFalse(yellowRemovalCandidates.single().equals("not a removal candidate"))
        assertEquals(
            emptyList<PlayerCardRemovalCandidate>(),
            playerCardRemovalCandidates(adjustmentStepState.teamTwoPlayerCards, CardType.RED),
        )

        // The UI reconciliation flow should prevent invalid records; if one reaches the model anyway, fail loudly.
        val invalidPlayerCardMessage =
            "Player card records must be no cards, one yellow, second yellow, red, or one yellow plus red."
        val invalidAssignmentException = assertThrows(IllegalArgumentException::class.java) {
            addPlayerCardAssignment(
                listOf(InGamePlayerCardRecord("17", reds = 1)),
                "17",
                CardType.RED,
            )
        }
        assertEquals(invalidPlayerCardMessage, invalidAssignmentException.message)

        val negativeCardException = assertThrows(IllegalArgumentException::class.java) {
            standardLiveGameState().adjustCardsAndTf(
                teamOneBlues = 0,
                teamOneTechnicalFouls = 0,
                teamTwoBlues = 0,
                teamTwoTechnicalFouls = 0,
                teamOnePlayerCards = listOf(InGamePlayerCardRecord("17", yellows = -1)),
                teamTwoPlayerCards = emptyList(),
            )
        }
        assertEquals("Player card records cannot have negative card counts.", negativeCardException.message)

        val negativeRedException = assertThrows(IllegalArgumentException::class.java) {
            standardLiveGameState().adjustCardsAndTf(
                teamOneBlues = 0,
                teamOneTechnicalFouls = 0,
                teamTwoBlues = 0,
                teamTwoTechnicalFouls = 0,
                teamOnePlayerCards = listOf(InGamePlayerCardRecord("17", reds = -1)),
                teamTwoPlayerCards = emptyList(),
            )
        }
        assertEquals("Player card records cannot have negative card counts.", negativeRedException.message)

        val tooManyYellowsException = assertThrows(IllegalArgumentException::class.java) {
            standardLiveGameState().adjustCardsAndTf(
                teamOneBlues = 0,
                teamOneTechnicalFouls = 0,
                teamTwoBlues = 0,
                teamTwoTechnicalFouls = 0,
                teamOnePlayerCards = listOf(InGamePlayerCardRecord("17", yellows = 3)),
                teamTwoPlayerCards = emptyList(),
            )
        }
        assertEquals(invalidPlayerCardMessage, tooManyYellowsException.message)

        val tooManyRedsException = assertThrows(IllegalArgumentException::class.java) {
            standardLiveGameState().adjustCardsAndTf(
                teamOneBlues = 0,
                teamOneTechnicalFouls = 0,
                teamTwoBlues = 0,
                teamTwoTechnicalFouls = 0,
                teamOnePlayerCards = listOf(InGamePlayerCardRecord("17", reds = 2)),
                teamTwoPlayerCards = emptyList(),
            )
        }
        assertEquals(invalidPlayerCardMessage, tooManyRedsException.message)

        val secondYellowAndRedException = assertThrows(IllegalArgumentException::class.java) {
            standardLiveGameState().adjustCardsAndTf(
                teamOneBlues = 0,
                teamOneTechnicalFouls = 0,
                teamTwoBlues = 0,
                teamTwoTechnicalFouls = 0,
                teamOnePlayerCards = listOf(InGamePlayerCardRecord("17", yellows = 2, reds = 1)),
                teamTwoPlayerCards = emptyList(),
            )
        }
        assertEquals(invalidPlayerCardMessage, secondYellowAndRedException.message)

        val duplicateCardException = assertThrows(IllegalArgumentException::class.java) {
            standardLiveGameState().adjustCardsAndTf(
                teamOneBlues = 0,
                teamOneTechnicalFouls = 0,
                teamTwoBlues = 0,
                teamTwoTechnicalFouls = 0,
                teamOnePlayerCards = listOf(
                    InGamePlayerCardRecord("17", yellows = 1),
                    InGamePlayerCardRecord("17", reds = 1),
                ),
                teamTwoPlayerCards = emptyList(),
            )
        }
        assertEquals("Player card records cannot contain duplicate player entries.", duplicateCardException.message)

        // Manual cards/TF correction clamps visible team counts and derives yellow/red totals from player records.
        val correctedTeamOnePlayerCards = listOf(
            InGamePlayerCardRecord("17", yellows = 1),
            InGamePlayerCardRecord(UNKNOWN_PLAYER_NUMBER, yellows = 1, reds = 1),
        )
        val correctedTeamTwoPlayerCards = listOf(
            InGamePlayerCardRecord("23", reds = 1),
        )
        val beforeCardsAdjustment = state
        state = state.adjustCardsAndTf(
            teamOneBlues = -1,
            teamOneTechnicalFouls = 3,
            teamTwoBlues = 4,
            teamTwoTechnicalFouls = -3,
            teamOnePlayerCards = correctedTeamOnePlayerCards,
            teamTwoPlayerCards = correctedTeamTwoPlayerCards,
        )
        assertEquals(2, state.teamYellowCards(VC))
        assertEquals(0, state.teamOne.blueCards)
        assertEquals(1, state.teamRedCards(VC))
        assertEquals(3, state.teamOne.technicalFouls)
        assertEquals(4, state.teamCardTotal(VC))
        assertEquals(0, state.teamYellowCards(ANIMAL))
        assertEquals(4, state.teamTwo.blueCards)
        assertEquals(1, state.teamRedCards(ANIMAL))
        assertEquals(0, state.teamTwo.technicalFouls)
        assertEquals(6, state.teamCardTotal(ANIMAL))
        assertEquals(correctedTeamOnePlayerCards, state.playerCards(VC))
        assertEquals(correctedTeamTwoPlayerCards, state.playerCards(ANIMAL))
        assertEquals("Cards and technical fouls adjusted.", state.lastEvent)
        assertEquals("Undo Cards / TF adjustment", state.undoEntry?.label)
        assertEquals(beforeCardsAdjustment, state.undoEntry?.previous)
    }
}
