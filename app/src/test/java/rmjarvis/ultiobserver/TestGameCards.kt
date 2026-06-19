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
    /// Test player-name and reason details for live yellow/red card records.
    @Test
    fun namedPlayerCardAssignments() {
        val VC = TeamId.TEAM_ONE

        assertEquals("Dangerous play: late layout", CardReason("Dangerous play", details = " late layout ").text())
        assertEquals("Custom reason: with context", CardReason("Other", " Custom reason ", " with context ").text())
        assertEquals("More context only", CardReason(details = " More context only ").text())
        assertEquals("Other details only", CardReason("Other", details = " Other details only ").text())
        assertEquals("", CardReason("Other").text())

        var state = standardLiveGameState()
        var cardResult = state.assessYellowCard(
            team = VC,
            jerseyNumber = "24",
            now = 0L,
            playerName = "Drew Handler",
            reason = CardReason(preset = "Dangerous play"),
        )
        state = cardResult.state
        assertEquals("Yellow card on #24 Drew Handler.\nViscous Coupling has 1 blue card.", cardResult.message())
        assertEquals(
            PlayerRecord(
                jerseyNumber = "24",
                playerName = "Drew Handler",
                cards = listOf(
                    InGamePlayerCardEvent(
                        CardType.YELLOW,
                        index = 0,
                        reason = CardReason(preset = "Dangerous play"),
                    )
                ),
            ),
            state.playerCards(VC).single(),
        )

        cardResult = state.assessYellowCard(
            team = VC,
            jerseyNumber = "24",
            now = 0L,
            playerName = "  drew   handler  ",
            reason = CardReason(preset = "Taunting"),
        )
        state = cardResult.state
        assertEquals("Second yellow on #24 Drew Handler.", cardResult.message()!!.lineSequence().first())
        assertEquals(listOf("Dangerous play", "Taunting"), state.playerCards(VC).single().cards.map { it.reason.text() })

        val drewPlayer = state.playerCards(VC).single { it.playerName == "Drew Handler" }
        assertEquals(listOf("Dangerous play", "Taunting"), drewPlayer.cards.map { it.reason.text() })

        cardResult = state.assessYellowCard(
            team = VC,
            jerseyNumber = "24",
            now = 0L,
            playerName = "Different Player",
        )
        state = cardResult.state
        assertEquals(2, state.playerCards(VC).size)
        assertEquals(
            PlayerRecord(
                jerseyNumber = "24",
                playerName = "Different Player",
                cards = listOf(InGamePlayerCardEvent(CardType.YELLOW, index = 2)),
            ),
            state.playerCards(VC).single { it.playerName == "Different Player" },
        )

        val priorNameOnlyState = createLiveGameState(
            standardGameSetup(startTime = LocalTime.of(11, 0)).copy(
                teamOnePlayers = listOf(priorPlayerRecord("", priorYellows = 2, playerName = "Name Only")),
            )
        )
        cardResult = priorNameOnlyState.assessYellowCard(
            team = VC,
            jerseyNumber = "",
            now = 0L,
            playerName = " name   only ",
        )
        assertEquals(
            "Yellow card on Name Only.\n" +
                "Name Only is suspended for the rest of the tournament.\n" +
                "Viscous Coupling has 1 blue card.",
            cardResult.message(),
        )
    }

    /// Test player-card record display, setup-entry matching, and invalid card-event guardrails.
    @Test
    fun playerCardRecordsAndHolderEntryChecks() {
        val priorCardRecord = PlayerRecord(
            jerseyNumber = "8",
            priorYellows = 1,
            priorReds = 0,
        )
        assertEquals("8", priorCardRecord.jerseyNumber)
        assertEquals("", priorCardRecord.playerName)
        assertEquals(1, priorCardRecord.priorYellows)
        assertEquals(0, priorCardRecord.priorReds)
        assertEquals("#8", priorCardRecord.playerIdentity(compact = true))
        assertEquals("#8", priorCardRecord.playerIdentity(compact = false))
        assertEquals("Y 1", priorCardRecord.cardDetail())
        assertEquals("prior Y 1", priorCardRecord.cardDetail(includeGame = true))
        assertEquals("1 yellow card", priorCardRecord.playerCardNoticeDetail())
        assertEquals("2 yellow cards", countedNounPhrase(2, "yellow card"))
        val namedPriorCardRecord = PlayerRecord(
            jerseyNumber = "12",
            priorYellows = 1,
            priorReds = 1,
            playerName = "Casey Handler",
        )
        assertEquals("#12", namedPriorCardRecord.playerIdentity(compact = true))
        assertEquals("#12 Casey Handler", namedPriorCardRecord.playerIdentity(compact = false))
        assertEquals("Y 1  R 1", namedPriorCardRecord.cardDetail())
        assertEquals(
            "prior Y 1  R 1 + Y 1",
            namedPriorCardRecord.copy(
                cards = listOf(InGamePlayerCardEvent(CardType.YELLOW, index = 0)),
            ).cardDetail(includeGame = true),
        )
        assertEquals("1 yellow card and 1 red card", namedPriorCardRecord.playerCardNoticeDetail())
        val numberlessPriorCardRecord = PlayerRecord(
            jerseyNumber = "",
            priorYellows = 0,
            priorReds = 1,
            playerName = "No Number",
        )
        assertEquals("No Number", numberlessPriorCardRecord.playerIdentity(compact = true))
        assertEquals("No Number", numberlessPriorCardRecord.playerIdentity(compact = false))
        assertEquals("R 1", numberlessPriorCardRecord.cardDetail())
        assertEquals("1 red card", numberlessPriorCardRecord.playerCardNoticeDetail())
        assertThrows(IllegalArgumentException::class.java) {
            PlayerRecord("", priorYellows = 1, priorReds = 0)
        }
        assertEquals("No prior cards", PlayerRecord("8", priorYellows = 0, priorReds = 0).cardDetail())
        val cardHolderEntryChecks = listOf(
            PlayerRecord("7", priorYellows = 1, priorReds = 0, playerName = "Drew Handler"),
            PlayerRecord("00", priorYellows = 0, priorReds = 1, playerName = "Zero Hero"),
            PlayerRecord("", priorYellows = 1, priorReds = 0, playerName = "Name Only"),
        )
        val exactDuplicate = cardHolderEntryChecks.cardHolderEntryCheck(
            proposed = PlayerRecord("7", priorYellows = 2, priorReds = 0, playerName = "  drew   handler "),
            editingIndex = null,
        )
        assertTrue(exactDuplicate is CardHolderEntryCheck.ExistingCardHolder)
        exactDuplicate as CardHolderEntryCheck.ExistingCardHolder
        assertEquals(0, exactDuplicate.existingIndex)
        val blankExistingName = listOf(
            PlayerRecord("9", priorYellows = 1, priorReds = 0, playerName = ""),
        ).cardHolderEntryCheck(
            proposed = PlayerRecord("9", priorYellows = 1, priorReds = 1, playerName = "Sideline Caller"),
            editingIndex = null,
        )
        assertTrue(blankExistingName is CardHolderEntryCheck.ExistingCardHolder)
        blankExistingName as CardHolderEntryCheck.ExistingCardHolder
        assertEquals(0, blankExistingName.existingIndex)
        val sameNameNoNumber = cardHolderEntryChecks.cardHolderEntryCheck(
            proposed = PlayerRecord("", priorYellows = 2, priorReds = 0, playerName = "name   only"),
            editingIndex = null,
        )
        assertTrue(sameNameNoNumber is CardHolderEntryCheck.ExistingCardHolder)
        sameNameNoNumber as CardHolderEntryCheck.ExistingCardHolder
        assertEquals(2, sameNameNoNumber.existingIndex)
        val blankExistingNumber = cardHolderEntryChecks.cardHolderEntryCheck(
            proposed = PlayerRecord("23", priorYellows = 0, priorReds = 1, playerName = "name   only"),
            editingIndex = null,
        )
        assertTrue(blankExistingNumber is CardHolderEntryCheck.PossibleDifferentPlayer)
        blankExistingNumber as CardHolderEntryCheck.PossibleDifferentPlayer
        assertEquals(listOf(2), blankExistingNumber.existingIndices)
        val editedPriorCards = cardHolderEntryChecks.withSavedPriorCardRecord(
            record = PlayerRecord("23", priorYellows = 0, priorReds = 1, playerName = "Name Only"),
            editingIndex = 2,
        )
        assertEquals(3, editedPriorCards.size)
        assertEquals(PlayerRecord("23", priorYellows = 0, priorReds = 1, playerName = "Name Only"), editedPriorCards[2])
        val twoPartialMatches = listOf(
            playerRecordWithCards("23", yellows = 1),
            playerRecordWithCards("", yellows = 1, playerName = "Jarvis"),
        ).cardHolderEntryCheck(
            proposed = PlayerRecord("23", priorYellows = 1, priorReds = 0, playerName = "Jarvis"),
            editingIndex = null,
        )
        assertTrue(twoPartialMatches is CardHolderEntryCheck.ExistingCardHolder)
        twoPartialMatches as CardHolderEntryCheck.ExistingCardHolder
        assertEquals(0, twoPartialMatches.existingIndex)
        val sameNumberDifferentName = cardHolderEntryChecks.cardHolderEntryCheck(
            proposed = PlayerRecord("7", priorYellows = 1, priorReds = 0, playerName = "James Cutter"),
            editingIndex = null,
        )
        assertTrue(sameNumberDifferentName is CardHolderEntryCheck.PossibleDifferentPlayer)
        sameNumberDifferentName as CardHolderEntryCheck.PossibleDifferentPlayer
        assertEquals(listOf(0), sameNumberDifferentName.existingIndices)
        assertNull(
            cardHolderEntryChecks.cardHolderEntryCheck(
                proposed = PlayerRecord("0", priorYellows = 1, priorReds = 0, playerName = "Zero Hero"),
                editingIndex = null,
            )
        )
        assertNull(
            cardHolderEntryChecks.cardHolderEntryCheck(
                proposed = PlayerRecord("24", priorYellows = 1, priorReds = 0, playerName = "Drew Handler"),
                editingIndex = null,
            )
        )
        assertNull(
            cardHolderEntryChecks.cardHolderEntryCheck(
                proposed = PlayerRecord("7", priorYellows = 1, priorReds = 0, playerName = "Drew Handler"),
                editingIndex = 0,
            )
        )

        assertEquals("Yellow", CardType.YELLOW.label)
        val invalidCardEventException = assertThrows(IllegalArgumentException::class.java) {
            GameEvent.TeamCardsChanged(
                state = standardLiveGameState(),
                team = TeamId.TEAM_ONE,
                teamCardTotal = 1,
                playerCardType = PlayerCardEventType.YELLOW,
            )
        }
        assertEquals(
            "Failed requirement.",
            invalidCardEventException.message,
        )
    }

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
        ): PlayerRecord {
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
        assertEquals(playerRecordWithCards("17", yellows = 1), playerRecord(state, VC, "17"))
        assertEquals("Undo Yellow on #17 of Viscous Coupling", state.undoEntry?.label)
        assertEquals(EventLogType.YELLOW_CARD, state.eventLog.last().type)

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
        assertEquals(playerRecordWithCards("17", yellows = 2), playerRecord(state, VC, "17"))
        assertEquals("Undo Second yellow on #17 of Viscous Coupling", state.undoEntry?.label)
        assertEquals(EventLogType.YELLOW_CARD, state.eventLog.last().type)
        assertUndoRestores(cardResult.state.undoEntry!!.previous, state)

        // A third team-card point between points gives the pulling-team misconduct field-position cue.
        cardResult = state.assessBlueCard(VC)
        state = cardResult.state
        assertFalse(cardResult.needsMisconductChoice)
        assertEquals(1, state.teamOne.blueCards)
        assertEquals(3, state.teamCardTotal(VC))
        assertEquals(1, state.playerCards(VC).size)
        assertEquals(
            "This is Viscous Coupling's third blue card.\n\n" +
                "Penalty against Viscous Coupling. No pull. Animal starts at attacking brick.",
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
        assertTrue(state.canReportOffenseSet(true))
        assertFalse(state.canReportOffenseSet(false))
        val earlySetState = state.reportOffenseSet(state.startEpoch + 70_000L)
        assertEquals(CountdownKind.DEFENSE_CHECK, earlySetState.countdown?.kind)
        assertEquals("Defense check in", earlySetState.countdown?.label)
        assertEquals(30, earlySetState.countdown?.durationSeconds)
        assertEquals(state.startEpoch + 100_000L, earlySetState.countdown?.targetEpoch)
        assertEquals(
            GamePhase.LIVE_POINT,
            earlySetState.applyExpiredCountdownTransitions(earlySetState.countdown!!.targetEpoch, showDefenseCountdowns = false).phase,
        )
        assertEquals("Point is live.", earlySetState.applyExpiredCountdownTransitions(earlySetState.countdown!!.targetEpoch, showDefenseCountdowns = false).lastEvent)
        val laterSetState = state.reportOffenseSet(state.startEpoch + 85_000L)
        assertEquals(CountdownKind.DEFENSE_CHECK, laterSetState.countdown?.kind)
        assertEquals(20, laterSetState.countdown?.durationSeconds)
        assertEquals(state.startEpoch + 105_000L, laterSetState.countdown?.targetEpoch)
        assertTrue(state.canReportOffenseSet(true))
        assertFalse(standardLiveGameState().canReportOffenseSet(true))
        assertFalse(
            state.copy(phase = GamePhase.LIVE_POINT)
                .canReportOffenseSet(true),
        )
        assertEquals(
            state,
            state.applyExpiredCountdownTransitions(
                state.countdown!!.targetEpoch,
                showDefenseCountdowns = true,
            ),
        )
        state = state.applyExpiredCountdownTransitions(state.countdown!!.targetEpoch, showDefenseCountdowns = false)
        assertEquals(GamePhase.LIVE_POINT, state.phase)
        assertFalse(state.pullSkippedForCurrentPoint)
        assertNull(state.countdown)
        assertEquals("Point is live.", state.lastEvent)
        assertFalse(state.canReportOffenseSet(true))
        assertEquals(state, state.reportOffenseSet(state.startEpoch + 91_000L))

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
                "Penalty against Viscous Coupling. No pull. Animal starts at attacking brick.",
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
        assertEquals(playerRecordWithCards("23", reds = 1), playerRecord(state, ANIMAL, "23"))
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
                "Animal moves the disc to the reverse brick in the end zone they are defending. " +
                "Viscous Coupling may instead choose to leave the disc where it is " +
                "(keeping the current stall count +1, max 9).\n\n" +
                "Offense has 30 seconds to set. Then defense has 20 seconds to check the disc in.",
            misconductPrompt.resolutionMessage(againstOffense = true),
        )
        assertTrue(
            misconductPrompt.resolutionMessage(againstOffense = false)
                .contains("Viscous Coupling may move the disc to the brick mark nearest the end zone they are attacking."),
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
        assertEquals(playerRecordWithCards("8", yellows = 1, reds = 1), playerRecord(state, ANIMAL, "8"))
        assertEquals(
            "Red card on player 8.\n" +
                "Player 8 is suspended for the rest of the tournament.\n" +
                "Animal has 3 total blue cards.\n\n" +
                "Penalty against Animal. No pull. Disc at negative brick in defending end zone.",
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
        assertEquals(playerRecordWithCards("8", yellows = 2), playerRecord(state, ANIMAL, "8"))
        assertEquals(
            "Second yellow on player 8.\n" +
                "Player 8 receives a game suspension.\n" +
                "Animal has 2 total blue cards.",
            cardResult.message(),
        )

        // Prior cards from this tournament surface the tournament suspension thresholds.
        state = createLiveGameState(
            standardGameSetup(startTime = LocalTime.of(11, 0)).copy(
                teamOnePlayers = listOf(
                    priorPlayerRecord("45", priorYellows = 2),
                    priorPlayerRecord("44", priorYellows = 2),
                ),
                teamTwoPlayers = listOf(
                    priorPlayerRecord("44", priorYellows = 2),
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
                teamTwoPlayers = listOf(priorPlayerRecord("31", priorReds = 1)),
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
                teamTwoPlayers = listOf(priorPlayerRecord("35", priorReds = 1)),
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
                teamTwoPlayers = listOf(priorPlayerRecord("36", priorYellows = 1)),
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

        // Blue cards count as one team card point each and do not create player records.
        state = standardLiveGameState()
        val bluePreview = state.previewBlueCard(ANIMAL)
        assertEquals("This is Animal's first blue card.", bluePreview.event.formatMessage())
        assertEquals(0, state.teamTwo.blueCards)
        cardResult = state.assessBlueCard(ANIMAL)
        state = cardResult.state
        assertFalse(cardResult.needsMisconductChoice)
        assertEquals("This is Animal's first blue card.", cardResult.message())
        assertEquals(1, state.teamTwo.blueCards)
        assertEquals(1, state.teamCardTotal(ANIMAL))
        assertTrue(state.playerCards(ANIMAL).isEmpty())

        cardResult = state.assessBlueCard(ANIMAL)
        state = cardResult.state
        assertFalse(cardResult.needsMisconductChoice)
        assertEquals("This is Animal's second blue card.", cardResult.message())
        assertEquals(2, state.teamTwo.blueCards)
        assertEquals(2, state.teamCardTotal(ANIMAL))

        cardResult = state.assessBlueCard(ANIMAL)
        state = cardResult.state
        assertFalse(cardResult.needsMisconductChoice)
        assertEquals(3, state.teamTwo.blueCards)
        assertEquals(3, state.teamCardTotal(ANIMAL))
        assertEquals(
            "This is Animal's third blue card.\n\n" +
                "Penalty against Animal. No pull. Disc at negative brick in defending end zone.",
            cardResult.message(),
        )

        cardResult = state.assessBlueCard(ANIMAL)
        state = cardResult.state
        assertFalse(cardResult.needsMisconductChoice)
        assertEquals(4, state.teamTwo.blueCards)
        assertEquals(4, state.teamCardTotal(ANIMAL))
        assertEquals(
            "This is Animal's 4th blue card.\n\n" +
                "Penalty against Animal. No pull. Disc at negative brick in defending end zone.",
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
        assertEquals("This is Animal's first technical foul.", technicalFoulResult.message())
        assertEquals("Technical Foul", technicalFoulResult.event.formatPopupTitle())
        assertEquals(1, state.teamTwo.technicalFouls)
        assertEquals(0, state.teamCardTotal(ANIMAL))

        technicalFoulResult = state.assessTechnicalFoul(ANIMAL)
        state = technicalFoulResult.state
        assertFalse(technicalFoulResult.needsMisconductChoice)
        assertEquals("This is Animal's second technical foul.", technicalFoulResult.message())
        assertEquals(2, state.teamTwo.technicalFouls)

        technicalFoulResult = state.assessTechnicalFoul(ANIMAL)
        state = technicalFoulResult.state
        assertFalse(technicalFoulResult.needsMisconductChoice)
        assertEquals(3, state.teamTwo.technicalFouls)
        assertEquals(
            "This is Animal's third technical foul.\n\nPenalty against Animal. No pull. Disc at negative brick in defending end zone.",
            technicalFoulResult.message(),
        )
        assertEquals("Technical Foul", technicalFoulResult.event.formatPopupTitle())

        // After Animal scores, they are the pulling team, so the next technical foul uses the pulling-team cue.
        state = recordGoalFromCurrentStateAt(state, ANIMAL, LocalTime.of(11, 5))
        assertEquals(ANIMAL, state.pullingTeam)

        technicalFoulResult = state.assessTechnicalFoul(ANIMAL)
        state = technicalFoulResult.state
        assertFalse(technicalFoulResult.needsMisconductChoice)
        assertEquals(4, state.teamTwo.technicalFouls)
        assertEquals(
            "This is Animal's 4th technical foul.\n\nPenalty against Animal. No pull. Viscous Coupling starts at attacking brick.",
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
        assertEquals("This is Viscous Coupling's third blue card.", cardResult.message())

        val prompt = cardResult.misconductPrompt().formatMessage()
        assertTrue(prompt.contains("Was this against the offense or defense?"))
        assertTrue(
            cardResult.misconductPrompt().resolutionMessage(againstOffense = true)
                .contains("Viscous Coupling moves the disc to the reverse brick in the end zone they are defending."),
        )
        assertTrue(
            cardResult.misconductPrompt().resolutionMessage(againstOffense = false)
                .contains("Animal may move the disc to the brick mark nearest the end zone they are attacking."),
        )

        // Technical fouls hit the same live-point misconduct choice when Viscous Coupling reaches the threshold.
        state = standardLiveGameState().beginLivePoint()
        state = state.assessTechnicalFoul(VC).state
        state = state.assessTechnicalFoul(VC).state
        technicalFoulResult = state.assessTechnicalFoul(VC)
        state = technicalFoulResult.state
        assertEquals(GamePhase.LIVE_POINT, state.phase)
        assertTrue(technicalFoulResult.needsMisconductChoice)
        assertEquals("This is Viscous Coupling's third technical foul.", technicalFoulResult.message())
        assertTrue(
            technicalFoulResult.misconductPrompt().resolutionMessage(againstOffense = true)
                .contains("Viscous Coupling moves the disc to the reverse brick in the end zone they are defending."),
        )

        // Exercise the player-card assignment helpers used by the manual adjustment UI.
        var cardAssignments = emptyList<PlayerRecord>()
        cardAssignments = addPlayerCardAssignment(
            cardAssignments,
            jerseyNumber = "17",
            cardType = CardType.YELLOW,
            index = 0,
        )
        assertEquals(listOf(playerRecordWithCards("17", yellows = 1)), cardAssignments)
        cardAssignments = addPlayerCardAssignment(
            cardAssignments,
            jerseyNumber = "17",
            cardType = CardType.RED,
            index = 1,
        )
        assertEquals(listOf(playerRecordWithCards("17", yellows = 1, reds = 1)), cardAssignments)
        assertEquals(
            listOf(
                EditablePlayerCard(
                    playerIndex = 0,
                    cardIndex = 0,
                    index = 0,
                    jerseyNumber = "17",
                    playerName = "",
                    cardType = CardType.YELLOW,
                    reason = CardReason(),
                ),
                EditablePlayerCard(
                    playerIndex = 0,
                    cardIndex = 1,
                    index = 1,
                    jerseyNumber = "17",
                    playerName = "",
                    cardType = CardType.RED,
                    reason = CardReason(),
                ),
            ),
            cardAssignments.editablePlayerCards(),
        )
        cardAssignments = removeEditablePlayerCard(cardAssignments, cardAssignments.editablePlayerCards().first())
        assertEquals(
            listOf(
                PlayerRecord(
                    jerseyNumber = "17",
                    cards = listOf(InGamePlayerCardEvent(CardType.RED, index = 1)),
                )
            ),
            cardAssignments,
        )

        val priorCardRecord = listOf(
            PlayerRecord(
                jerseyNumber = "44",
                priorYellows = 1,
                cards = listOf(
                    InGamePlayerCardEvent(
                        CardType.YELLOW,
                        index = 0,
                        reason = CardReason(preset = "Dangerous play"),
                    )
                ),
            )
        )
        assertEquals(
            listOf(priorPlayerRecord("44", priorYellows = 1)),
            removeEditablePlayerCard(priorCardRecord, priorCardRecord.editablePlayerCards().single()),
        )

        cardAssignments = listOf(
            playerRecordWithCards("17", yellows = 1),
            PlayerRecord(
                jerseyNumber = "8",
                cards = listOf(InGamePlayerCardEvent(CardType.RED, index = 1)),
            ),
        )
        cardAssignments = replaceEditablePlayerCard(
            records = cardAssignments,
            editableCard = cardAssignments.editablePlayerCards().first(),
            jerseyNumber = "8",
            cardType = CardType.YELLOW,
            playerName = "",
            reason = CardReason(preset = "Other", otherText = "Corrected identity"),
        )
        assertEquals(1, cardAssignments.size)
        assertEquals("8", cardAssignments.single().jerseyNumber)
        assertTrue(
            cardAssignments.single().cards.contains(
                InGamePlayerCardEvent(
                    CardType.YELLOW,
                    index = 0,
                    reason = CardReason(preset = "Other", otherText = "Corrected identity"),
                )
            )
        )
        assertTrue(cardAssignments.single().cards.contains(InGamePlayerCardEvent(CardType.RED, index = 1)))
        cardAssignments = addPlayerCardAssignment(
            cardAssignments,
            jerseyNumber = "23",
            cardType = CardType.YELLOW,
            index = 2,
        )
        val playerEight = cardAssignments.single { it.jerseyNumber == "8" }
        assertEquals(
            setOf(
                InGamePlayerCardEvent(
                    CardType.YELLOW,
                    index = 0,
                    reason = CardReason(preset = "Other", otherText = "Corrected identity"),
                ),
                InGamePlayerCardEvent(CardType.RED, index = 1),
            ),
            playerEight.cards.toSet(),
        )
        assertEquals(
            setOf(
                playerEight,
                PlayerRecord(
                    jerseyNumber = "23",
                    cards = listOf(InGamePlayerCardEvent(CardType.YELLOW, index = 2)),
                ),
            ),
            cardAssignments.toSet(),
        )
        cardAssignments = listOf(
            playerRecordWithCards("12", yellows = 1),
            PlayerRecord(
                jerseyNumber = "23",
                cards = listOf(InGamePlayerCardEvent(CardType.RED, index = 1)),
            ),
        )
        cardAssignments = replaceEditablePlayerCard(
            records = cardAssignments,
            editableCard = cardAssignments.editablePlayerCards().first(),
            jerseyNumber = "12",
            cardType = CardType.YELLOW,
            playerName = "Mike",
            reason = CardReason(preset = "Dangerous play"),
        )
        assertEquals(
            setOf(
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
                ),
                PlayerRecord(
                    jerseyNumber = "23",
                    cards = listOf(InGamePlayerCardEvent(CardType.RED, index = 1)),
                ),
            ),
            cardAssignments.toSet(),
        )

        assertNull(
            playerCardAssignmentRejection(
                emptyList(),
                PlayerIdentity("99"),
            ),
        )
        assertNull(
            playerCardAssignmentRejection(
                listOf(playerRecordWithCards("17", yellows = 1)),
                PlayerIdentity("17"),
            ),
        )
        assertEquals(
            PlayerCardAssignmentRejection.TWO_YELLOWS,
            playerCardAssignmentRejection(
                listOf(playerRecordWithCards("17", yellows = 2)),
                PlayerIdentity("17"),
            ),
        )
        assertEquals(
            PlayerCardAssignmentRejection.RED_CARD,
            playerCardAssignmentRejection(
                listOf(playerRecordWithCards("17", reds = 1)),
                PlayerIdentity("17"),
            ),
        )
        assertNull(
            playerCardAssignmentRejection(
                listOf(priorPlayerRecord("17", priorReds = 1)),
                PlayerIdentity("17"),
            ),
        )
        assertEquals(
            PlayerCardAssignmentRejection.THREE_TOURNAMENT_YELLOWS,
            playerCardAssignmentRejection(
                listOf(
                    PlayerRecord(
                        jerseyNumber = "17",
                        priorYellows = 2,
                        cards = listOf(InGamePlayerCardEvent(CardType.YELLOW, index = 0)),
                    ),
                ),
                PlayerIdentity("17"),
            ),
        )
        assertEquals(
            PlayerCardAssignmentRejection.THREE_TOURNAMENT_YELLOWS,
            playerCardAssignmentRejection(
                listOf(
                    PlayerRecord(
                        jerseyNumber = "17",
                        priorReds = 1,
                        cards = listOf(InGamePlayerCardEvent(CardType.YELLOW, index = 0)),
                    ),
                ),
                PlayerIdentity("17"),
            ),
        )

        assertFalse(standardLiveGameState().playerHasYellowThisGame(VC, "99"))
        assertFalse(
            standardLiveGameState().copy(
                teamOnePlayers = listOf(PlayerRecord("99")),
            ).playerHasYellowThisGame(
                VC,
                "99",
            )
        )

        // The UI reconciliation flow should prevent invalid records; if one reaches the model anyway, fail loudly.
        val invalidPlayerCardMessage =
            "Player records must be no cards, one yellow, second yellow, red, or one yellow plus red."
        val invalidAssignmentException = assertThrows(IllegalArgumentException::class.java) {
            addPlayerCardAssignment(
                listOf(playerRecordWithCards("17", reds = 1)),
                jerseyNumber = "17",
                cardType = CardType.RED,
                index = 1,
            )
        }
        assertEquals(invalidPlayerCardMessage, invalidAssignmentException.message)

        val tooManyYellowsException = assertThrows(IllegalArgumentException::class.java) {
            standardLiveGameState().adjustCardsAndTf(
                teamOneBlues = 0,
                teamOneTechnicalFouls = 0,
                teamTwoBlues = 0,
                teamTwoTechnicalFouls = 0,
                teamOnePlayers = listOf(playerRecordWithCards("17", yellows = 3)),
                teamTwoPlayers = emptyList(),
            )
        }
        assertEquals(invalidPlayerCardMessage, tooManyYellowsException.message)

        val tooManyRedsException = assertThrows(IllegalArgumentException::class.java) {
            standardLiveGameState().adjustCardsAndTf(
                teamOneBlues = 0,
                teamOneTechnicalFouls = 0,
                teamTwoBlues = 0,
                teamTwoTechnicalFouls = 0,
                teamOnePlayers = listOf(playerRecordWithCards("17", reds = 2)),
                teamTwoPlayers = emptyList(),
            )
        }
        assertEquals(invalidPlayerCardMessage, tooManyRedsException.message)

        val secondYellowAndRedException = assertThrows(IllegalArgumentException::class.java) {
            standardLiveGameState().adjustCardsAndTf(
                teamOneBlues = 0,
                teamOneTechnicalFouls = 0,
                teamTwoBlues = 0,
                teamTwoTechnicalFouls = 0,
                teamOnePlayers = listOf(playerRecordWithCards("17", yellows = 2, reds = 1)),
                teamTwoPlayers = emptyList(),
            )
        }
        assertEquals(invalidPlayerCardMessage, secondYellowAndRedException.message)

        val duplicateCardException = assertThrows(IllegalArgumentException::class.java) {
            standardLiveGameState().adjustCardsAndTf(
                teamOneBlues = 0,
                teamOneTechnicalFouls = 0,
                teamTwoBlues = 0,
                teamTwoTechnicalFouls = 0,
                teamOnePlayers = listOf(
                    playerRecordWithCards("17", yellows = 1),
                    playerRecordWithCards("17", reds = 1),
                ),
                teamTwoPlayers = emptyList(),
            )
        }
        assertEquals("Player records cannot contain duplicate player entries.", duplicateCardException.message)

        // Manual cards/techs correction clamps visible team counts and derives yellow/red totals from player records.
        val correctedTeamOnePlayerCards = listOf(
            playerRecordWithCards("17", yellows = 1),
            playerRecordWithCards("19", yellows = 1, reds = 1),
        )
        val correctedTeamTwoPlayerCards = listOf(
            playerRecordWithCards("23", reds = 1),
        )
        val beforeCardsAdjustment = state
        state = state.adjustCardsAndTf(
            teamOneBlues = -1,
            teamOneTechnicalFouls = 3,
            teamTwoBlues = 4,
            teamTwoTechnicalFouls = -3,
            teamOnePlayers = correctedTeamOnePlayerCards,
            teamTwoPlayers = correctedTeamTwoPlayerCards,
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
        assertEquals("Undo Adjust blue card/tech counts", state.undoEntry?.label)
        assertEquals(beforeCardsAdjustment, state.undoEntry?.previous)
    }

}
