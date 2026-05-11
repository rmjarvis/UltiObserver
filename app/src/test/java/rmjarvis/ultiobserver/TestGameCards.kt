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

class TestGameCards : GameModelTestFixtures() {
    // Test yellow, red, blue, and technical-foul handling from public card assessment APIs.
    // Emphasize team card points, per-player records, and misconduct-threshold messages.
    @Test
    fun cardsAndTechnicalFouls() {
        val VC = TeamId.TEAM_ONE
        val ANIMAL = TeamId.TEAM_TWO

        fun playerRecord(
            state: LiveGameState,
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
        assertEquals("Yellow card on player 17.\nViscous Coupling has 1 card.", cardResult.message())
        assertEquals(1, state.teamYellowCards(VC))
        assertEquals(0, state.teamRedCards(VC))
        assertEquals(1, state.teamCardTotal(VC))
        assertEquals(InGamePlayerCardRecord("17", yellows = 1), playerRecord(state, VC, "17"))
        assertEquals("Undo Yellow on #17 of Viscous Coupling", state.undoEntry?.label)

        // A second yellow to the same player acts as a red card, but adds only one more team card point.
        cardResult = state.assessYellowCard(VC, "17")
        state = cardResult.state
        assertFalse(cardResult.needsMisconductChoice)
        assertEquals("Second yellow acts as a red card. Player 17 is ejected.\nViscous Coupling has 2 cards.", cardResult.message())
        assertEquals(2, state.teamYellowCards(VC))
        assertEquals(0, state.teamRedCards(VC))
        assertEquals(2, state.teamCardTotal(VC))
        assertEquals(InGamePlayerCardRecord("17", yellows = 2), playerRecord(state, VC, "17"))
        assertEquals("Undo Second Yellow on #17 of Viscous Coupling", state.undoEntry?.label)
        assertUndoRestores(cardResult.state.undoEntry!!.previous, state)

        // A third team-card point between points gives the pulling-team misconduct field-position cue.
        cardResult = state.assessBlueCard(VC)
        state = cardResult.state
        assertFalse(cardResult.needsMisconductChoice)
        assertEquals(1, state.teamOne.blueCards)
        assertEquals(3, state.teamCardTotal(VC))
        assertEquals(1, state.playerCards(VC).size)
        assertEquals(
            "Viscous Coupling has 3 cards.\n\nPenalty against pulling team. No pull. Receiving team starts at attacking brick.",
            cardResult.message(),
        )

        state = standardLiveGameState()
        state = state.copy(teamOne = state.teamOne.copy(blueCards = 2))
        cardResult = state.assessYellowCard(VC, "14")
        assertFalse(cardResult.needsMisconductChoice)
        assertEquals(
            "Yellow card on player 14.\nViscous Coupling has 3 cards.\n\n" +
                "Penalty against pulling team. No pull. Receiving team starts at attacking brick.",
            cardResult.message(),
        )

        // During a live point, a standalone yellow that reaches the misconduct threshold needs an offense/defense choice.
        state = standardLiveGameState().beginLivePoint()
        state = state.assessBlueCard(VC).state
        state = state.assessBlueCard(VC).state
        cardResult = state.assessYellowCard(VC, "14")
        state = cardResult.state
        assertTrue(cardResult.needsMisconductChoice)
        assertEquals("Yellow card on player 14.\nViscous Coupling has 3 cards.", cardResult.message())
        assertEquals(3, state.teamCardTotal(VC))

        // A direct red for a player with no prior yellow counts as two team card points and records a direct red.
        state = standardLiveGameState()
        cardResult = state.assessRedCard(ANIMAL, "23", RedCardMode.DIRECT_RED)
        state = cardResult.state
        assertFalse(cardResult.needsMisconductChoice)
        assertEquals("Player 23 is ejected.\nAnimal has 2 cards.", cardResult.message())
        assertEquals(0, state.teamYellowCards(ANIMAL))
        assertEquals(1, state.teamRedCards(ANIMAL))
        assertEquals(2, state.teamCardTotal(ANIMAL))
        assertEquals(InGamePlayerCardRecord("23", directReds = 1), playerRecord(state, ANIMAL, "23"))
        assertUndoRestores(cardResult.state.undoEntry!!.previous, state)

        // During a live point, a direct red that reaches the misconduct threshold needs an offense/defense choice.
        state = standardLiveGameState().beginLivePoint()
        state = state.assessYellowCard(ANIMAL, "8").state
        cardResult = state.assessRedCard(ANIMAL, "23", RedCardMode.DIRECT_RED)
        state = cardResult.state
        assertTrue(cardResult.needsMisconductChoice)
        assertEquals("Player 23 is ejected.\nAnimal has 3 cards.", cardResult.message())
        assertEquals(3, state.teamCardTotal(ANIMAL))

        // A direct red for a player who already has a yellow is distinct from recording the red as a second yellow.
        state = standardLiveGameState()
        state = state.assessYellowCard(ANIMAL, "8").state
        cardResult = state.assessRedCard(ANIMAL, "8", RedCardMode.DIRECT_RED)
        state = cardResult.state
        assertFalse(cardResult.needsMisconductChoice)
        assertEquals(1, state.teamYellowCards(ANIMAL))
        assertEquals(1, state.teamRedCards(ANIMAL))
        assertEquals(3, state.teamCardTotal(ANIMAL))
        assertEquals(InGamePlayerCardRecord("8", yellows = 1, directReds = 1), playerRecord(state, ANIMAL, "8"))
        assertEquals(
            "Player 8 is ejected.\nAnimal has 3 cards.\n\n" +
                "Penalty against receiving team. No pull. Disc at negative brick in defending end zone.",
            cardResult.message(),
        )

        state = standardLiveGameState()
        state = state.assessYellowCard(ANIMAL, "8").state
        cardResult = state.assessRedCard(ANIMAL, "8", RedCardMode.SECOND_YELLOW)
        state = cardResult.state
        assertFalse(cardResult.needsMisconductChoice)
        assertEquals(2, state.teamYellowCards(ANIMAL))
        assertEquals(0, state.teamRedCards(ANIMAL))
        assertEquals(2, state.teamCardTotal(ANIMAL))
        assertEquals(InGamePlayerCardRecord("8", yellows = 2), playerRecord(state, ANIMAL, "8"))
        assertEquals("Second yellow acts as a red card. Player 8 is ejected.\nAnimal has 2 cards.", cardResult.message())

        // The N/A pathways distinguish same-unknown-player second yellow from a standalone yellow.
        state = standardLiveGameState()
        state = state.assessYellowCard(VC, UNKNOWN_PLAYER_NUMBER).state
        assertTrue(state.playerHasYellowThisGame(VC, UNKNOWN_PLAYER_NUMBER))
        cardResult = state.assessRedCard(VC, UNKNOWN_PLAYER_NUMBER, RedCardMode.SECOND_YELLOW)
        state = cardResult.state
        assertEquals(2, state.teamYellowCards(VC))
        assertEquals(0, state.teamRedCards(VC))
        assertEquals(InGamePlayerCardRecord(UNKNOWN_PLAYER_NUMBER, yellows = 2), playerRecord(state, VC, UNKNOWN_PLAYER_NUMBER))
        assertEquals("Second yellow acts as a red card. The player is ejected.\nViscous Coupling has 2 cards.", cardResult.message())

        state = standardLiveGameState()
        state = state.assessYellowCard(VC, UNKNOWN_PLAYER_NUMBER).state
        cardResult = state.assessStandaloneYellowCard(VC, UNKNOWN_PLAYER_NUMBER)
        state = cardResult.state
        assertEquals(2, state.teamYellowCards(VC))
        assertEquals(0, state.teamRedCards(VC))
        assertEquals(2, state.teamCardTotal(VC))
        assertFalse(cardResult.message()!!.startsWith("Second yellow acts as a red card."))

        // Blue cards count as one team card point each and do not create per-player card records.
        state = standardLiveGameState()
        cardResult = state.assessBlueCard(ANIMAL)
        state = cardResult.state
        assertFalse(cardResult.needsMisconductChoice)
        assertEquals("Animal has 1 card.", cardResult.message())
        assertEquals(1, state.teamTwo.blueCards)
        assertEquals(1, state.teamCardTotal(ANIMAL))
        assertTrue(state.playerCards(ANIMAL).isEmpty())

        cardResult = state.assessBlueCard(ANIMAL)
        state = cardResult.state
        assertFalse(cardResult.needsMisconductChoice)
        assertEquals("Animal has 2 cards.", cardResult.message())
        assertEquals(2, state.teamTwo.blueCards)
        assertEquals(2, state.teamCardTotal(ANIMAL))

        cardResult = state.assessBlueCard(ANIMAL)
        state = cardResult.state
        assertFalse(cardResult.needsMisconductChoice)
        assertEquals(3, state.teamTwo.blueCards)
        assertEquals(3, state.teamCardTotal(ANIMAL))
        assertEquals(
            "Animal has 3 cards.\n\nPenalty against receiving team. No pull. Disc at negative brick in defending end zone.",
            cardResult.message(),
        )

        cardResult = state.assessBlueCard(ANIMAL)
        state = cardResult.state
        assertFalse(cardResult.needsMisconductChoice)
        assertEquals(4, state.teamTwo.blueCards)
        assertEquals(4, state.teamCardTotal(ANIMAL))
        assertEquals(
            "Animal has 4 cards.\n\nPenalty against receiving team. No pull. Disc at negative brick in defending end zone.",
            cardResult.message(),
        )

        // Technical fouls use a separate count, with the same third-and-later misconduct handling.
        state = standardLiveGameState()
        var technicalFoulResult = state.assessTechnicalFoul(ANIMAL)
        state = technicalFoulResult.state
        assertFalse(technicalFoulResult.needsMisconductChoice)
        assertEquals("Animal has 1 technical foul.", technicalFoulResult.message())
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
        assertEquals(LivePhase.LIVE_POINT, state.phase)
        assertTrue(cardResult.needsMisconductChoice)
        assertEquals("Viscous Coupling has 3 cards.", cardResult.message())

        val prompt = cardResult.misconductPrompt().formatMessage()
        assertTrue(prompt.contains("Was this against the offense or defense?"))
        assertTrue(
            cardResult.misconductPrompt().resolutionMessage(againstOffense = true)
                .contains("Reverse brick"),
        )
        assertTrue(
            cardResult.misconductPrompt().resolutionMessage(againstOffense = false)
                .contains("Brick nearest attacking end zone"),
        )

        // Technical fouls hit the same live-point misconduct choice when Viscous Coupling reaches the threshold.
        state = standardLiveGameState().beginLivePoint()
        state = state.assessTechnicalFoul(VC).state
        state = state.assessTechnicalFoul(VC).state
        technicalFoulResult = state.assessTechnicalFoul(VC)
        state = technicalFoulResult.state
        assertEquals(LivePhase.LIVE_POINT, state.phase)
        assertTrue(technicalFoulResult.needsMisconductChoice)
        assertEquals("Viscous Coupling has 3 technical fouls.", technicalFoulResult.message())
        assertTrue(
            technicalFoulResult.misconductPrompt().resolutionMessage(againstOffense = true)
                .contains("Reverse brick"),
        )

        // Exercise the player-card assignment helpers used by the UI reconciliation prompts.
        var cardAssignments = emptyList<InGamePlayerCardRecord>()
        cardAssignments = addPlayerCardAssignment(cardAssignments, "17", CardType.YELLOW)
        assertEquals(listOf(InGamePlayerCardRecord("17", yellows = 1)), cardAssignments)
        cardAssignments = addPlayerCardAssignment(cardAssignments, "17", CardType.RED)
        assertEquals(listOf(InGamePlayerCardRecord("17", yellows = 1, directReds = 1)), cardAssignments)
        cardAssignments = removePlayerCardAssignment(cardAssignments, "8", CardType.YELLOW)
        assertEquals(listOf(InGamePlayerCardRecord("17", yellows = 1, directReds = 1)), cardAssignments)
        cardAssignments = removePlayerCardAssignment(cardAssignments, "17", CardType.YELLOW)
        assertEquals(listOf(InGamePlayerCardRecord("17", directReds = 1)), cardAssignments)
        cardAssignments = addPlayerCardAssignment(cardAssignments, "17", CardType.YELLOW)
        cardAssignments = removePlayerCardAssignment(cardAssignments, "17", CardType.RED)
        assertEquals(listOf(InGamePlayerCardRecord("17", yellows = 1)), cardAssignments)
        cardAssignments = addPlayerCardAssignment(cardAssignments, "17", CardType.RED)
        cardAssignments = removePlayerCardAssignment(cardAssignments, "17", CardType.RED)
        assertEquals(listOf(InGamePlayerCardRecord("17", yellows = 1)), cardAssignments)
        cardAssignments = listOf(
            InGamePlayerCardRecord("17", yellows = 1),
            InGamePlayerCardRecord("8", directReds = 1),
        )
        cardAssignments = removePlayerCardAssignment(cardAssignments, "17", CardType.YELLOW)
        assertEquals(listOf(InGamePlayerCardRecord("8", directReds = 1)), cardAssignments)
        cardAssignments = addPlayerCardAssignment(cardAssignments, "23", CardType.YELLOW)
        cardAssignments = addPlayerCardAssignment(cardAssignments, "8", CardType.YELLOW)
        assertEquals(
            listOf(
                InGamePlayerCardRecord("8", yellows = 1, directReds = 1),
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
                listOf(InGamePlayerCardRecord("17", directReds = 1)),
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
                InGamePlayerCardRecord("23", directReds = 1),
            ),
            teamTwoPlayerCards = listOf(
                InGamePlayerCardRecord("8", yellows = 2),
            ),
        )
        assertEquals(
            listOf(
                PlayerCardAdjustmentStep(VC, CardType.YELLOW, PlayerCardAdjustmentMode.ADD),
                PlayerCardAdjustmentStep(VC, CardType.RED, PlayerCardAdjustmentMode.REMOVE),
                PlayerCardAdjustmentStep(ANIMAL, CardType.YELLOW, PlayerCardAdjustmentMode.REMOVE),
            ),
            adjustmentStepState.buildPlayerCardAdjustmentSteps(
                teamOneYellows = 2,
                teamOneReds = 0,
                teamTwoYellows = 1,
                teamTwoReds = 0,
            ),
        )
        assertEquals(
            listOf(PlayerCardRemovalCandidate("8", cardCount = 2)),
            playerCardRemovalCandidates(adjustmentStepState.teamTwoPlayerCards, CardType.YELLOW),
        )
        assertEquals(
            emptyList<PlayerCardRemovalCandidate>(),
            playerCardRemovalCandidates(adjustmentStepState.teamTwoPlayerCards, CardType.RED),
        )

        // The UI reconciliation flow should prevent invalid records; if one reaches the model anyway, fail loudly.
        val invalidPlayerCardMessage =
            "Player card records must be no cards, one yellow, second yellow, direct red, or one yellow plus direct red."
        val invalidAssignmentException = assertThrows(IllegalArgumentException::class.java) {
            addPlayerCardAssignment(
                listOf(InGamePlayerCardRecord("17", directReds = 1)),
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
                teamOnePlayerCards = listOf(InGamePlayerCardRecord("17", directReds = -1)),
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

        val tooManyDirectRedsException = assertThrows(IllegalArgumentException::class.java) {
            standardLiveGameState().adjustCardsAndTf(
                teamOneBlues = 0,
                teamOneTechnicalFouls = 0,
                teamTwoBlues = 0,
                teamTwoTechnicalFouls = 0,
                teamOnePlayerCards = listOf(InGamePlayerCardRecord("17", directReds = 2)),
                teamTwoPlayerCards = emptyList(),
            )
        }
        assertEquals(invalidPlayerCardMessage, tooManyDirectRedsException.message)

        val secondYellowAndDirectRedException = assertThrows(IllegalArgumentException::class.java) {
            standardLiveGameState().adjustCardsAndTf(
                teamOneBlues = 0,
                teamOneTechnicalFouls = 0,
                teamTwoBlues = 0,
                teamTwoTechnicalFouls = 0,
                teamOnePlayerCards = listOf(InGamePlayerCardRecord("17", yellows = 2, directReds = 1)),
                teamTwoPlayerCards = emptyList(),
            )
        }
        assertEquals(invalidPlayerCardMessage, secondYellowAndDirectRedException.message)

        val duplicateCardException = assertThrows(IllegalArgumentException::class.java) {
            standardLiveGameState().adjustCardsAndTf(
                teamOneBlues = 0,
                teamOneTechnicalFouls = 0,
                teamTwoBlues = 0,
                teamTwoTechnicalFouls = 0,
                teamOnePlayerCards = listOf(
                    InGamePlayerCardRecord("17", yellows = 1),
                    InGamePlayerCardRecord("17", directReds = 1),
                ),
                teamTwoPlayerCards = emptyList(),
            )
        }
        assertEquals("Player card records cannot contain duplicate player entries.", duplicateCardException.message)

        // Manual cards/TF correction clamps visible team counts and derives yellow/red totals from player records.
        val correctedTeamOnePlayerCards = listOf(
            InGamePlayerCardRecord("17", yellows = 1),
            InGamePlayerCardRecord(UNKNOWN_PLAYER_NUMBER, yellows = 1, directReds = 1),
        )
        val correctedTeamTwoPlayerCards = listOf(
            InGamePlayerCardRecord("23", directReds = 1),
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
        assertEquals("Undo Cards / TF Adjustment", state.undoEntry?.label)
        assertEquals(beforeCardsAdjustment, state.undoEntry?.previous)
    }
}
