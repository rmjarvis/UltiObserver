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

/**
 * Tests for player cards, team cards, technical fouls, and misconduct consequences.
 */
class TestMisconduct : GameDomainTestFixtures() {
    /**
     * Test player-name and reason details for live yellow/red card records.
     */
    @Test
    fun playerCardDetails() {
        val VC = TeamId.TEAM_ONE

        // Card reasons trim selectable presets and free-text details into the stored display text.
        assertEquals(
            "Dangerous play: late layout",
            CardReason("Dangerous play", details = " late layout ").text(),
        )
        assertEquals(
            "Custom reason: with context",
            CardReason("Other", " Custom reason ", " with context ").text(),
        )
        val detailsOnlyReason = CardReason(details = " More context only ")
        assertEquals("", detailsOnlyReason.preset)
        assertEquals("", detailsOnlyReason.otherText)
        assertEquals(" More context only ", detailsOnlyReason.details)
        assertEquals("More context only", detailsOnlyReason.text())
        assertEquals(
            "Other details only",
            CardReason("Other", details = " Other details only ").text(),
        )
        assertEquals("", CardReason("Other").text())

        // A first yellow records the normalized player identity and the selected reason.
        var state = standardLiveGameState()
        var cardResult = state.assessYellowCard(
            team = VC,
            jerseyNumber = "24",
            now = 0L,
            playerName = "Drew Handler",
            reason = CardReason(preset = "Dangerous play"),
        )
        state = cardResult.state
        assertEquals(
            "Yellow card on #24 Drew Handler.\nViscous Coupling has 1 card total.",
            cardResult.message(),
        )
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

        // A second yellow to the same player preserves the canonical name and appends the
        // new reason.
        cardResult = state.assessYellowCard(
            team = VC,
            jerseyNumber = "24",
            now = 0L,
            playerName = "  drew   handler  ",
            reason = CardReason(preset = "Taunting"),
        )
        state = cardResult.state
        assertEquals(
            "Second yellow on #24 Drew Handler.",
            cardResult.message()!!.lineSequence().first(),
        )
        assertEquals(
            listOf("Dangerous play", "Taunting"),
            state.playerCards(VC).single().cards.map { it.reason.text() },
        )

        val drewPlayer = state.playerCards(VC).single { it.playerName == "Drew Handler" }
        assertEquals(
            listOf("Dangerous play", "Taunting"),
            drewPlayer.cards.map { it.reason.text() },
        )

        // The same jersey with a different name is treated as a different player-card holder.
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

        // Name-only prior-card holders can receive live cards and tournament-suspension messages.
        val priorNameOnlyState = createLiveGameState(
            standardGameSetup(startTime = LocalTime.of(11, 0)).copy(
                teamOnePlayers = listOf(
                    priorPlayerRecord("", priorYellows = 2, playerName = "Name Only"),
                ),
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
                "Viscous Coupling has 1 card total.",
            cardResult.message(),
        )
    }

    /**
     * Test identity normalization and matching semantics for player-card records.
     */
    @Test
    fun playerRecordMatching() {
        // Player identities normalize whitespace and preserve their displayable original spelling,
        // while equality ignores name case and whitespace runs.
        val normalizedIdentity = PlayerIdentity("  7  ", "  Drew   Handler  ")
        assertEquals("7", normalizedIdentity.jerseyNumber)
        assertEquals("Drew   Handler", normalizedIdentity.playerName)
        assertEquals(PlayerIdentity("7", "drew handler"), normalizedIdentity)
        assertEquals(PlayerIdentity("7", "drew handler").hashCode(), normalizedIdentity.hashCode())
        assertEquals(
            "PlayerIdentity(jerseyNumber=7, playerName=Drew   Handler)",
            normalizedIdentity.toString(),
        )
        assertFalse(normalizedIdentity.equals("7"))

        // Exact matching requires compatible number/name combinations, not merely one shared
        // identity field.
        assertTrue(PlayerIdentity("", "Name Only").matches(PlayerIdentity("", " name   only ")))
        assertTrue(PlayerIdentity("12", "").matches(PlayerIdentity("12", "Sideline Caller")))
        assertFalse(
            PlayerIdentity("12", "Sideline Caller").matches(
                PlayerIdentity("", "sideline caller"),
            )
        )

        // Overlap catches possible same-player conflicts when either the number or name is
        // missing, but rejects conflicting numbers.
        assertTrue(
            PlayerIdentity("12", "Sideline Caller").hasOverlapWith(
                PlayerIdentity("", "sideline caller"),
            )
        )
        assertTrue(
            PlayerIdentity("", "Sideline Caller").hasOverlapWith(
                PlayerIdentity("12", "sideline caller"),
            )
        )
        assertFalse(
            PlayerIdentity("12", "Sideline Caller").hasOverlapWith(
                PlayerIdentity("13", "sideline caller"),
            )
        )
        assertFalse(
            PlayerIdentity("12", "Sideline Caller").hasOverlapWith(PlayerIdentity("13", ""))
        )
        assertFalse(PlayerIdentity("7") == PlayerIdentity("8"))

        // An exact match is not considered a mere overlap.
        assertFalse(normalizedIdentity.hasOverlapWith(PlayerIdentity("7", "drew handler")))

        // A player-card identity must have at least a jersey number or a player name.
        val invalidIdentityException = assertThrows(IllegalArgumentException::class.java) {
            PlayerIdentity(" ", " ")
        }
        assertEquals(
            "A player identity requires a jersey number or player name.",
            invalidIdentityException.message,
        )
    }

    /**
     * Test display text for player-card records, card labels, and formatted card events.
     */
    @Test
    fun playerCardDisplayText() {
        // Prior-card records without names use compact identity and card-count display text.
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
        assertEquals("Yellows: 1", priorCardRecord.cardDetail())
        assertEquals("Y 1", priorCardRecord.cardDetail(compact = true))
        assertEquals("prior Y 1", priorCardRecord.cardDetail(compact = true, includeGame = true))
        assertEquals("1 yellow card", priorCardRecord.playerCardNoticeDetail())
        assertEquals("2 yellow cards", countedNounPhrase(2, "yellow card"))

        // Named and numberless prior-card records include the most specific available identity.
        val namedPriorCardRecord = PlayerRecord(
            jerseyNumber = "12",
            priorYellows = 1,
            priorReds = 1,
            playerName = "Casey Handler",
        )
        assertEquals("#12", namedPriorCardRecord.playerIdentity(compact = true))
        assertEquals("#12 Casey Handler", namedPriorCardRecord.playerIdentity(compact = false))
        assertEquals("Yellows: 1, Reds: 1", namedPriorCardRecord.cardDetail())
        assertEquals("Y 1  R 1", namedPriorCardRecord.cardDetail(compact = true))
        assertEquals(
            "prior Y 1  R 1 + Y 1",
            namedPriorCardRecord.copy(
                cards = listOf(InGamePlayerCardEvent(CardType.YELLOW, index = 0)),
            ).cardDetail(compact = true, includeGame = true),
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
        assertEquals("Reds: 1", numberlessPriorCardRecord.cardDetail())
        assertEquals("R 1", numberlessPriorCardRecord.cardDetail(compact = true))
        assertEquals("1 red card", numberlessPriorCardRecord.playerCardNoticeDetail())

        // Empty and in-game-only records have their own concise prior-card display text.
        assertEquals("no prior cards", PlayerRecord("9").playerCardNoticeDetail())
        assertEquals("No prior cards", PlayerRecord("9").cardDetail())
        assertEquals(
            "R 1",
            playerRecordWithCards("9", reds = 1).cardDetail(compact = true, includeGame = true),
        )
        assertEquals(
            "No prior cards",
            PlayerRecord("8", priorYellows = 0, priorReds = 0).cardDetail(compact = true),
        )

        // Card-reason preset lists expose expected yellow and red reason choices.
        assertTrue(cardReasonPresets(CardType.YELLOW).contains("Dangerous play"))
        assertTrue(cardReasonPresets(CardType.RED).contains("Battery/fighting"))

        // Card labels expose the user-facing name for each card type.
        assertEquals("Yellow", CardType.YELLOW.label)

        // Formatted player-card events include the player number and updated team card count.
        assertEquals(
            "Yellow card on player 4.\nViscous Coupling has 1 card total.",
            GameEvent.TeamCardsChanged(
                state = standardLiveGameState().assessFirstYellowCard(TeamId.TEAM_ONE, "4").state,
                team = TeamId.TEAM_ONE,
                teamCardTotal = 1,
                playerCardType = PlayerCardEventType.YELLOW,
                playerCardJerseyNumber = "4",
                playerCardName = null,
            ).formatMessage(),
        )
        assertEquals(
            "Team card total: 1",
            standardLiveGameState()
                .assessFirstYellowCard(TeamId.TEAM_ONE, "4")
                .state
                .teamCardTotalDetailLine(TeamId.TEAM_ONE),
        )
        assertEquals(
            "Team card total: 2 (red cards count as 2)",
            standardLiveGameState()
                .assessRedCard(TeamId.TEAM_ONE, "4")
                .state
                .teamCardTotalDetailLine(TeamId.TEAM_ONE),
        )
    }

    /**
     * Test prior-card construction checks and allowed game-card count combinations.
     */
    @Test
    fun playerCardCountRestrictions() {
        // Prior-card records must have an identity and non-negative prior-card counts.
        assertThrows(IllegalArgumentException::class.java) {
            PlayerRecord("", priorYellows = 1, priorReds = 0)
        }
        val negativePriorYellowException = assertThrows(IllegalArgumentException::class.java) {
            PlayerRecord("8", priorYellows = -1, priorReds = 0)
        }
        assertEquals("Prior card counts cannot be negative.", negativePriorYellowException.message)
        val negativePriorRedException = assertThrows(IllegalArgumentException::class.java) {
            PlayerRecord("8", priorYellows = 0, priorReds = -1)
        }
        assertEquals("Prior card counts cannot be negative.", negativePriorRedException.message)

        // Game-card records allow only the combinations that the misconduct workflow can produce.
        assertTrue(PlayerRecord("8").hasLegalCounts())
        assertTrue(playerRecordWithCards("8", yellows = 1).hasLegalCounts())
        assertTrue(playerRecordWithCards("8", yellows = 2).hasLegalCounts())
        assertTrue(playerRecordWithCards("8", reds = 1).hasLegalCounts())
        assertTrue(playerRecordWithCards("8", yellows = 1, reds = 1).hasLegalCounts())
        assertFalse(playerRecordWithCards("8", yellows = 1, reds = 2).hasLegalCounts())
        assertFalse(playerRecordWithCards("8", reds = 2).hasLegalCounts())

        // Player-card rejection messages explain why another card cannot be added.
        assertEquals(
            "already has two yellow cards and has been suspended.",
            PlayerSuspensionStatus.TWO_YELLOWS.rejectionText,
        )
        assertEquals(
            "already has a red card and has been suspended.",
            PlayerSuspensionStatus.RED_CARD.rejectionText,
        )

        // Suspension notices explain that the just-finished adjustment created the suspension.
        assertEquals(
            "now has two yellow cards and has been suspended.",
            PlayerSuspensionStatus.TWO_YELLOWS.noticeText,
        )
        assertEquals(
            "now has a red card and has been suspended.",
            PlayerSuspensionStatus.RED_CARD.noticeText,
        )
        assertEquals(
            "now has three yellow cards in the tournament and has been suspended.",
            PlayerSuspensionStatus.THREE_TOURNAMENT_YELLOWS.noticeText,
        )

        // Team card events require player-card details when they represent a player card.
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
     * Test mid-game setup updates that edit prior-card counts and reconcile in-game card holders.
     */
    @Test
    fun midGamePriorCardUpdates() {
        val existingNameOnlyYellow = InGamePlayerCardEvent(CardType.YELLOW, index = 0)
        val cardHolderEntryChecks = listOf(
            PlayerRecord("7", priorYellows = 1, priorReds = 0, playerName = "Drew Handler"),
            PlayerRecord("00", priorYellows = 0, priorReds = 1, playerName = "Zero Hero"),
            PlayerRecord(
                "",
                priorYellows = 1,
                priorReds = 0,
                playerName = "Name Only",
                cards = listOf(existingNameOnlyYellow),
            ),
        )

        // Exact existing-holder matches normalize name case and whitespace.
        val exactDuplicate = cardHolderEntryChecks.cardHolderEntryCheck(
            proposed = PlayerRecord(
                "7",
                priorYellows = 2,
                priorReds = 0,
                playerName = "  drew   handler ",
            ),
            editingIndex = null,
        )
        assertTrue(exactDuplicate is CardHolderEntryCheck.ExistingCardHolder)
        exactDuplicate as CardHolderEntryCheck.ExistingCardHolder
        assertEquals(0, exactDuplicate.existingIndex)

        // Exact existing-holder matches also work when one side has only the number or name.
        val blankExistingName = listOf(
            PlayerRecord("9", priorYellows = 1, priorReds = 0, playerName = ""),
        ).cardHolderEntryCheck(
            proposed = PlayerRecord(
                "9",
                priorYellows = 1,
                priorReds = 1,
                playerName = "Sideline Caller",
            ),
            editingIndex = null,
        )
        assertTrue(blankExistingName is CardHolderEntryCheck.ExistingCardHolder)
        blankExistingName as CardHolderEntryCheck.ExistingCardHolder
        assertEquals(0, blankExistingName.existingIndex)
        val sameNameNoNumber = cardHolderEntryChecks.cardHolderEntryCheck(
            proposed = PlayerRecord(
                "",
                priorYellows = 2,
                priorReds = 0,
                playerName = "name   only",
            ),
            editingIndex = null,
        )
        assertTrue(sameNameNoNumber is CardHolderEntryCheck.ExistingCardHolder)
        sameNameNoNumber as CardHolderEntryCheck.ExistingCardHolder
        assertEquals(2, sameNameNoNumber.existingIndex)

        // Saving an edited setup row updates the prior-card counts while preserving in-game
        // cards already assigned to that player.
        val editedPriorCards = cardHolderEntryChecks.withSavedPriorCardRecord(
            record = PlayerRecord(
                "23",
                priorYellows = 0,
                priorReds = 1,
                playerName = "Name Only",
                cards = listOf(existingNameOnlyYellow),
            ),
            editingIndex = 2,
        )
        assertEquals(3, editedPriorCards.size)
        assertEquals(cardHolderEntryChecks[0], editedPriorCards[0])
        assertEquals(
            PlayerRecord(
                "23",
                priorYellows = 0,
                priorReds = 1,
                playerName = "Name Only",
                cards = listOf(existingNameOnlyYellow),
            ),
            editedPriorCards[2],
        )

        // Saving a new setup row appends it after existing in-game card holders.
        assertEquals(
            cardHolderEntryChecks + PlayerRecord("42", priorYellows = 1),
            cardHolderEntryChecks.withSavedPriorCardRecord(
                PlayerRecord("42", priorYellows = 1),
                editingIndex = null,
            ),
        )

        // The currently edited row is ignored for its own exact match.
        assertNull(
            cardHolderEntryChecks.cardHolderEntryCheck(
                proposed = PlayerRecord(
                    "7",
                    priorYellows = 1,
                    priorReds = 0,
                    playerName = "Drew Handler",
                ),
                editingIndex = 0,
            )
        )

        // A partial overlap with a name-only card holder needs user confirmation before merging.
        val blankExistingNumber = cardHolderEntryChecks.cardHolderEntryCheck(
            proposed = PlayerRecord(
                "23",
                priorYellows = 0,
                priorReds = 1,
                playerName = "name   only",
            ),
            editingIndex = null,
        )
        assertTrue(blankExistingNumber is CardHolderEntryCheck.PossibleDifferentPlayer)
        blankExistingNumber as CardHolderEntryCheck.PossibleDifferentPlayer
        assertEquals(listOf(2), blankExistingNumber.existingIndices)

        // A complete proposed identity can resolve two partial rows to the number match.
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

        // A shared number with a different name is a possible different-player conflict.
        val sameNumberDifferentName = cardHolderEntryChecks.cardHolderEntryCheck(
            proposed = PlayerRecord(
                "7",
                priorYellows = 1,
                priorReds = 0,
                playerName = "James Cutter",
            ),
            editingIndex = null,
        )
        assertTrue(sameNumberDifferentName is CardHolderEntryCheck.PossibleDifferentPlayer)
        sameNumberDifferentName as CardHolderEntryCheck.PossibleDifferentPlayer
        assertEquals(listOf(0), sameNumberDifferentName.existingIndices)

        // Same-number conflict reports identify the existing and proposed player names.
        val sameNumberConflict = cardHolderEntryChecks.sameNumberPlayerIdentityConflict(
            jerseyNumber = "7",
            playerName = "James Cutter",
        )
        assertEquals(
            SameNumberPlayerIdentityConflict(
                existingJerseyNumber = "7",
                existingPlayerName = "Drew Handler",
                proposedJerseyNumber = "7",
                proposedPlayerName = "James Cutter",
            ),
            sameNumberConflict,
        )
        assertEquals("7", sameNumberConflict!!.existingJerseyNumber)
        assertEquals("Drew Handler", sameNumberConflict.existingPlayerName)
        assertEquals("7", sameNumberConflict.proposedJerseyNumber)
        assertEquals("James Cutter", sameNumberConflict.proposedPlayerName)

        // Same-number conflict checks allow missing names, new numbers, and exact name matches.
        assertNull(cardHolderEntryChecks.sameNumberPlayerIdentityConflict("7", ""))
        assertNull(cardHolderEntryChecks.sameNumberPlayerIdentityConflict("99", "New Player"))
        assertNull(cardHolderEntryChecks.sameNumberPlayerIdentityConflict("7", "Drew Handler"))
        assertNull(listOf(PlayerRecord("7")).sameNumberPlayerIdentityConflict("7", "New Player"))
        val stateConflict = standardLiveGameState().copy(teamOnePlayers = cardHolderEntryChecks)
            .sameNumberPlayerIdentityConflict(TeamId.TEAM_ONE, "7", "James Cutter")
        assertNotNull(stateConflict)

        // Same name with a different number from an existing numbered player is accepted.
        assertNull(
            cardHolderEntryChecks.cardHolderEntryCheck(
                proposed = PlayerRecord(
                    "0",
                    priorYellows = 1,
                    priorReds = 0,
                    playerName = "Zero Hero",
                ),
                editingIndex = null,
            )
        )

        // Same name with a new number is accepted when the existing row already has a number.
        assertNull(
            cardHolderEntryChecks.cardHolderEntryCheck(
                proposed = PlayerRecord(
                    "24",
                    priorYellows = 1,
                    priorReds = 0,
                    playerName = "Drew Handler",
                ),
                editingIndex = null,
            )
        )
    }

    /**
     * Test yellow, red, blue, and technical-foul handling from public card assessment APIs.
     * Emphasize team card points, per-player records, and misconduct-threshold messages.
     */
    @Test
    fun misconductConsequences() {
        val VC = TeamId.TEAM_ONE
        val ANIMAL = TeamId.TEAM_TWO

        // Record a first yellow for a numbered Viscous Coupling player and verify team and
        // player state.
        var state = standardLiveGameState()
        var cardResult = state.assessYellowCard(VC, "17")
        state = cardResult.state
        assertFalse(cardResult.needsMisconductChoice)
        assertEquals(
            "Yellow card on player 17.\nViscous Coupling has 1 card total.",
            cardResult.message(),
        )
        assertEquals("Misconduct", cardResult.event.formatPopupTitle())
        assertEquals(1, state.teamYellowCards(VC))
        assertEquals(0, state.teamRedCards(VC))
        assertEquals(1, state.teamCardTotal(VC))
        assertEquals(playerRecordWithCards("17", yellows = 1), playerRecord(state, VC, "17"))
        assertEquals("Undo Yellow on #17 of Viscous Coupling", state.undoEntry?.label)
        assertEquals(EventLogType.YELLOW_CARD, state.eventLog.last().type)

        // A second yellow to the same player creates a game suspension, but adds only one more
        // team card point.
        cardResult = state.assessYellowCard(VC, "17")
        state = cardResult.state
        assertFalse(cardResult.needsMisconductChoice)
        assertEquals(
            "Second yellow on player 17.\n" +
                "Player 17 receives a game suspension.\n" +
                "Viscous Coupling has 2 cards total.",
            cardResult.message(),
        )
        assertEquals(2, state.teamYellowCards(VC))
        assertEquals(0, state.teamRedCards(VC))
        assertEquals(2, state.teamCardTotal(VC))
        assertEquals(playerRecordWithCards("17", yellows = 2), playerRecord(state, VC, "17"))
        assertEquals("Undo Second yellow on #17 of Viscous Coupling", state.undoEntry?.label)
        assertEquals(EventLogType.YELLOW_CARD, state.eventLog.last().type)
        assertUndoRestores(cardResult.state.undoEntry!!.previous, state)

        // A third team-card point between points gives the pulling-team misconduct
        // field-position cue.
        cardResult = state.assessBlueCard(VC)
        state = cardResult.state
        assertFalse(cardResult.needsMisconductChoice)
        assertEquals(1, state.teamOne.blueCards)
        assertEquals(3, state.teamCardTotal(VC))
        assertEquals(1, state.playerCards(VC).size)
        assertEquals(
            "Blue card on Viscous Coupling.\n" +
                "Viscous Coupling has 3 cards total.\n\n" +
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
        assertFalse(state.canRecordPullViolation(VC))
        assertFalse(state.canRecordPullViolation(ANIMAL))
        assertEquals(state, state.assessPullViolation(VC).state)
        assertEquals(state, state.assessPullViolation(ANIMAL).state)
        assertTrue(state.canReportOffenseSet(true))
        assertFalse(state.canReportOffenseSet(false))
        val earlySetState = state.reportOffenseSet(state.startEpoch + 70_000L)
        assertEquals(CountdownKind.DEFENSE_CHECK, earlySetState.countdown?.kind)
        assertEquals("Defense check in", earlySetState.countdown?.label)
        assertEquals(30, earlySetState.countdown?.durationSeconds)
        assertEquals(state.startEpoch + 100_000L, earlySetState.countdown?.targetEpoch)
        val livePointAfterDefenseCheck = earlySetState.applyExpiredCountdownTransitions(
            earlySetState.countdown!!.targetEpoch,
            showDefenseCountdowns = false,
        )
        assertEquals(GamePhase.LIVE_POINT, livePointAfterDefenseCheck.phase)
        assertEquals("Point is live.", livePointAfterDefenseCheck.lastEvent)
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

        // Defensive guard for stale UI actions: Offense set is only meaningful for misconduct
        // countdowns.
        val nonMisconductBetweenPointsState = standardLiveGameState()
        assertEquals(
            nonMisconductBetweenPointsState,
            nonMisconductBetweenPointsState.reportOffenseSet(
                nonMisconductBetweenPointsState.startEpoch + 20_000L,
            ),
        )
        assertEquals(
            state,
            state.applyExpiredCountdownTransitions(
                state.countdown!!.targetEpoch,
                showDefenseCountdowns = true,
            ),
        )
        state = state.applyExpiredCountdownTransitions(
            state.countdown!!.targetEpoch,
            showDefenseCountdowns = false,
        )
        assertEquals(GamePhase.LIVE_POINT, state.phase)
        assertFalse(state.pullSkippedForCurrentPoint)
        assertNull(state.countdown)
        assertEquals("Point is live.", state.lastEvent)
        assertFalse(state.canReportOffenseSet(true))
        assertEquals(state, state.reportOffenseSet(state.startEpoch + 91_000L))

        // The no-pull restriction is only for the current point sequence.
        state = recordGoalFromCurrentStateAt(state, VC, LocalTime.of(12, 5))
        assertFalse(state.pullSkippedForCurrentPoint)
        assertTrue(state.canRecordPullViolation(VC))

        // Defensive countdown helpers reject impossible third-card states that bypass normal
        // card flow.
        val missingCountdownException = assertThrows(NullPointerException::class.java) {
            val baseState = standardLiveGameState()
            baseState.copy(
                countdown = null,
                teamOne = baseState.teamOne.copy(blueCards = 2),
            ).assessBlueCard(VC)
        }
        assertNull(missingCountdownException.message)
        assertEquals(
            standardLiveGameState(),
            standardLiveGameState().startMisconductCountdown(1_010_000L),
        )

        // Defensive guard for a stale Start misconduct countdown action when no misconduct
        // countdown is pending.
        val liveStateWithoutPendingMisconduct = standardLiveGameState().beginLivePoint()
        assertEquals(
            liveStateWithoutPendingMisconduct,
            liveStateWithoutPendingMisconduct.startMisconductCountdown(1_010_000L),
        )

        // A player card that reaches the threshold between points follows the same no-pull
        // consequence.
        state = standardLiveGameState()
        state = state.copy(teamOne = state.teamOne.copy(blueCards = 2))
        cardResult = state.assessYellowCard(VC, "14")
        assertFalse(cardResult.needsMisconductChoice)
        assertEquals(
            "Yellow card on player 14.\nViscous Coupling has 3 cards total.\n\n" +
                "Penalty against Viscous Coupling. No pull. Animal starts at attacking brick.",
            cardResult.message(),
        )
        assertEquals(CountdownKind.MISCONDUCT_BETWEEN_POINTS, cardResult.state.countdown?.kind)

        // During a live point, a first yellow that reaches the misconduct threshold needs an
        // offense/defense choice.
        state = standardLiveGameState().beginLivePoint()
        state = state.assessBlueCard(VC).state
        state = state.assessBlueCard(VC).state
        cardResult = state.assessYellowCard(VC, "14")
        state = cardResult.state
        assertTrue(cardResult.needsMisconductChoice)
        assertEquals(
            "Yellow card on player 14.\nViscous Coupling has 3 cards total.",
            cardResult.message(),
        )
        assertEquals(3, state.teamCardTotal(VC))

        // A red for a player with no prior yellow counts as two team card points and records a red.
        state = standardLiveGameState()
        cardResult = state.assessRedCard(ANIMAL, "23")
        state = cardResult.state
        assertFalse(cardResult.needsMisconductChoice)
        assertEquals(
            "Red card on player 23.\n" +
                "Player 23 receives a game suspension.\n" +
                "Animal has 2 cards total (red cards count as 2).",
            cardResult.message(),
        )
        assertEquals("Misconduct", cardResult.event.formatPopupTitle())
        assertEquals(0, state.teamYellowCards(ANIMAL))
        assertEquals(1, state.teamRedCards(ANIMAL))
        assertEquals(2, state.teamCardTotal(ANIMAL))
        assertEquals(playerRecordWithCards("23", reds = 1), playerRecord(state, ANIMAL, "23"))
        assertUndoRestores(cardResult.state.undoEntry!!.previous, state)

        // During a live point, a red that reaches the misconduct threshold needs an
        // offense/defense choice.
        state = standardLiveGameState().beginLivePoint()
        state = state.assessYellowCard(ANIMAL, "8").state
        cardResult = state.assessRedCard(ANIMAL, "23")
        state = cardResult.state
        assertTrue(cardResult.needsMisconductChoice)
        assertEquals(
            "Red card on player 23.\n" +
                "Player 23 receives a game suspension.\n" +
                "Animal has 3 cards total (red cards count as 2).",
            cardResult.message(),
        )
        val misconductPrompt = cardResult.misconductPrompt()
        assertEquals("Misconduct penalty", misconductPrompt.formatTitle())
        val misconductGamePrompt: GamePrompt = misconductPrompt
        assertEquals("Misconduct penalty", misconductGamePrompt.formatTitle())
        assertEquals(
            "Red card on player 23.\n" +
                "Player 23 receives a game suspension.\n" +
                "Animal has 3 cards total (red cards count as 2).\n\nWas this against the offense or defense?",
            misconductPrompt.formatMessage(),
        )
        assertEquals(
            "Red card on player 23.\n" +
                "Player 23 receives a game suspension.\n" +
                "Animal has 3 cards total (red cards count as 2).\n\nWas this against the offense or defense?",
            misconductGamePrompt.formatMessage(),
        )
        assertEquals(
            "Red card on player 23.\n" +
                "Player 23 receives a game suspension.\n" +
                "Animal has 3 cards total (red cards count as 2).\n\n" +
                "Animal moves the disc to the reverse brick in the end zone they are defending. " +
                "Viscous Coupling may instead choose to leave the disc where it is " +
                "(keeping the current stall count +1, max 9).\n\n" +
                "Offense has 30 seconds to set. Then defense has 20 seconds to check the disc in.",
            misconductPrompt.resolutionMessage(againstOffense = true),
        )
        assertTrue(
            misconductPrompt.resolutionMessage(againstOffense = false)
                .contains(
                    "Viscous Coupling may move the disc to the brick mark nearest " +
                        "the end zone they are attacking.",
                ),
        )
        assertEquals(3, state.teamCardTotal(ANIMAL))

        // A red for a player who already has a yellow is distinct from recording the red as a
        // second yellow.
        state = standardLiveGameState()
        state = state.assessYellowCard(ANIMAL, "8").state
        cardResult = state.assessRedCard(ANIMAL, "8")
        state = cardResult.state
        assertFalse(cardResult.needsMisconductChoice)
        assertEquals(1, state.teamYellowCards(ANIMAL))
        assertEquals(1, state.teamRedCards(ANIMAL))
        assertEquals(3, state.teamCardTotal(ANIMAL))
        assertEquals(
            playerRecordWithCards("8", yellows = 1, reds = 1),
            playerRecord(state, ANIMAL, "8"),
        )
        assertEquals(
            "Red card on player 8.\n" +
                "Player 8 is suspended for the rest of the tournament.\n" +
                "Animal has 3 cards total (red cards count as 2).\n\n" +
                "Penalty against Animal. No pull. Disc at negative brick in defending end zone.",
            cardResult.message(),
        )
        assertTrue(
            cardResult.message()!!.contains(
                "Player 8 is suspended for the rest of the tournament.",
            ),
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
                "Animal has 2 cards total.",
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
                "Viscous Coupling has 1 card total.",
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
                "Animal has 2 cards total (red cards count as 2).",
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
                "Animal has 2 cards total (red cards count as 2).",
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
                "Animal has 2 cards total.",
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
                "Animal has 2 cards total (red cards count as 2).",
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
                "Animal has 2 cards total (red cards count as 2).",
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
                "Animal has 2 cards total (red cards count as 2).",
            cardResult.message(),
        )

        // Blue cards count as one team card point each and do not create player records.
        state = standardLiveGameState()
        val bluePreview = state.previewBlueCard(ANIMAL)
        assertEquals("Blue card on Animal.\nAnimal has 1 card total.", bluePreview.event.formatMessage())
        assertEquals(0, state.teamTwo.blueCards)
        cardResult = state.assessBlueCard(ANIMAL)
        state = cardResult.state
        assertFalse(cardResult.needsMisconductChoice)
        assertEquals("Blue card on Animal.\nAnimal has 1 card total.", cardResult.message())
        assertEquals(1, state.teamTwo.blueCards)
        assertEquals(1, state.teamCardTotal(ANIMAL))
        assertTrue(state.playerCards(ANIMAL).isEmpty())

        cardResult = state.assessBlueCard(ANIMAL)
        state = cardResult.state
        assertFalse(cardResult.needsMisconductChoice)
        assertEquals("Blue card on Animal.\nAnimal has 2 cards total.", cardResult.message())
        assertEquals(2, state.teamTwo.blueCards)
        assertEquals(2, state.teamCardTotal(ANIMAL))

        cardResult = state.assessBlueCard(ANIMAL)
        state = cardResult.state
        assertFalse(cardResult.needsMisconductChoice)
        assertEquals(3, state.teamTwo.blueCards)
        assertEquals(3, state.teamCardTotal(ANIMAL))
        assertEquals(
            "Blue card on Animal.\n" +
                "Animal has 3 cards total.\n\n" +
                "Penalty against Animal. No pull. Disc at negative brick in defending end zone.",
            cardResult.message(),
        )

        cardResult = state.assessBlueCard(ANIMAL)
        state = cardResult.state
        assertFalse(cardResult.needsMisconductChoice)
        assertEquals(4, state.teamTwo.blueCards)
        assertEquals(4, state.teamCardTotal(ANIMAL))
        assertEquals(
            "Blue card on Animal.\n" +
                "Animal has 4 cards total.\n\n" +
                "Penalty against Animal. No pull. Disc at negative brick in defending end zone.",
            cardResult.message(),
        )

        // After game over, stale card actions can arrive from UI timing, but should not create
        // no-pull guidance.
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
        val technicalFoulPreview = state.previewTechnicalFoul(ANIMAL)
        assertEquals(
            "This is Animal's first technical foul.",
            technicalFoulPreview.event.formatMessage(),
        )
        assertEquals(0, state.teamTwo.technicalFouls)
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
            "This is Animal's third technical foul.\n\n" +
                "Penalty against Animal. No pull. Disc at negative brick in defending end zone.",
            technicalFoulResult.message(),
        )
        assertEquals("Technical Foul", technicalFoulResult.event.formatPopupTitle())

        // After Animal scores, they are the pulling team, so the next technical foul uses the
        // pulling-team cue.
        state = recordGoalFromCurrentStateAt(state, ANIMAL, LocalTime.of(11, 5))
        assertEquals(ANIMAL, state.pullingTeam)

        technicalFoulResult = state.assessTechnicalFoul(ANIMAL)
        state = technicalFoulResult.state
        assertFalse(technicalFoulResult.needsMisconductChoice)
        assertEquals(4, state.teamTwo.technicalFouls)
        assertEquals(
            "This is Animal's 4th technical foul.\n\n" +
                "Penalty against Animal. No pull. Viscous Coupling starts at attacking brick.",
            technicalFoulResult.message(),
        )

        // During a live point, third-and-later misconduct asks for offense/defense context
        // instead of guessing.
        state = standardLiveGameState().beginLivePoint()
        state = state.assessBlueCard(VC).state
        state = state.assessBlueCard(VC).state
        cardResult = state.assessBlueCard(VC)
        state = cardResult.state
        assertEquals(GamePhase.LIVE_POINT, state.phase)
        assertTrue(cardResult.needsMisconductChoice)
        assertEquals(
            "Blue card on Viscous Coupling.\nViscous Coupling has 3 cards total.",
            cardResult.message(),
        )

        val prompt = cardResult.misconductPrompt().formatMessage()
        assertTrue(prompt.contains("Was this against the offense or defense?"))
        assertTrue(
            cardResult.misconductPrompt().resolutionMessage(againstOffense = true)
                .contains(
                    "Viscous Coupling moves the disc to the reverse brick in the end zone " +
                        "they are defending.",
                ),
        )
        assertTrue(
            cardResult.misconductPrompt().resolutionMessage(againstOffense = false)
                .contains(
                    "Animal may move the disc to the brick mark nearest the end zone " +
                        "they are attacking.",
                ),
        )

        // Technical fouls hit the same live-point misconduct choice when Viscous Coupling
        // reaches the threshold.
        state = standardLiveGameState().beginLivePoint()
        state = state.assessTechnicalFoul(VC).state
        state = state.assessTechnicalFoul(VC).state
        technicalFoulResult = state.assessTechnicalFoul(VC)
        state = technicalFoulResult.state
        assertEquals(GamePhase.LIVE_POINT, state.phase)
        assertTrue(technicalFoulResult.needsMisconductChoice)
        assertEquals(
            "This is Viscous Coupling's third technical foul.",
            technicalFoulResult.message(),
        )
        assertTrue(
            technicalFoulResult.misconductPrompt().resolutionMessage(againstOffense = true)
                .contains(
                    "Viscous Coupling moves the disc to the reverse brick in the end zone " +
                        "they are defending.",
                ),
        )
        val invalidMisconductPromptException = assertThrows(IllegalStateException::class.java) {
            GamePrompt.LivePointMisconduct(GameEvent.TimeoutUnavailable(state)).resolutionMessage(
                againstOffense = true,
            )
        }
        assertEquals(
            "Live-point misconduct prompts require a card or technical-foul event.",
            invalidMisconductPromptException.message,
        )
        val liveMisconductCountdownState = state.withPendingMisconductCountdown()
            .startMisconductCountdown(state.startEpoch + 20_000L)
        assertEquals(CountdownKind.TIME_OUT, liveMisconductCountdownState.countdown?.kind)
        val liveDefenseCheckState = liveMisconductCountdownState.reportOffenseSet(
            state.startEpoch + 55_000L,
        )
        assertEquals(CountdownKind.DEFENSE_CHECK, liveDefenseCheckState.countdown?.kind)
        assertEquals(20, liveDefenseCheckState.countdown?.durationSeconds)
        val wrongMisconductCountdownState = liveMisconductCountdownState.copy(
            countdown = liveMisconductCountdownState.countdown!!.copy(
                kind = CountdownKind.HALFTIME,
            ),
        )
        assertEquals(
            wrongMisconductCountdownState,
            wrongMisconductCountdownState.reportOffenseSet(state.startEpoch + 55_000L),
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
                    index = 0,
                    jerseyNumber = "17",
                    playerName = "",
                    cardType = CardType.YELLOW,
                    reason = CardReason(),
                ),
                EditablePlayerCard(
                    index = 1,
                    jerseyNumber = "17",
                    playerName = "",
                    cardType = CardType.RED,
                    reason = CardReason(),
                ),
            ),
            editablePlayerCards(cardAssignments),
        )
        cardAssignments = removeEditablePlayerCard(
            cardAssignments,
            editablePlayerCards(cardAssignments).first(),
        )
        assertEquals(
            listOf(
                PlayerRecord(
                    jerseyNumber = "17",
                    cards = listOf(InGamePlayerCardEvent(CardType.RED, index = 1)),
                )
            ),
            cardAssignments,
        )

        // Defensive guard for a stale UI row: normal UI flows pass cards from the current
        // record list.
        val staleEditableCardException = assertThrows(IllegalArgumentException::class.java) {
            removeEditablePlayerCard(
                cardAssignments,
                editablePlayerCards(cardAssignments).single().copy(index = 99),
            )
        }
        assertEquals(
            "Editable player-card index 99 must match exactly one card.",
            staleEditableCardException.message,
        )

        // Removing editable player cards preserves prior-card counts and drops empty in-game
        // records.
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
            removeEditablePlayerCard(
                priorCardRecord,
                editablePlayerCards(priorCardRecord).single(),
            ),
        )
        val singleInGameCardRecord = listOf(playerRecordWithCards("55", yellows = 1))
        assertTrue(
            removeEditablePlayerCard(
                singleInGameCardRecord,
                editablePlayerCards(singleInGameCardRecord).single(),
            ).isEmpty()
        )
        assertTrue(editablePlayerCards(listOf(PlayerRecord("56"))).isEmpty())
        val priorRedCardRecord = listOf(
            PlayerRecord(
                jerseyNumber = "57",
                priorReds = 1,
                cards = listOf(InGamePlayerCardEvent(CardType.RED, index = 0)),
            )
        )
        assertEquals(
            listOf(priorPlayerRecord("57", priorReds = 1)),
            removeEditablePlayerCard(
                priorRedCardRecord,
                editablePlayerCards(priorRedCardRecord).single(),
            ),
        )

        // Replacing an editable card can merge it into another player while preserving card
        // indexes.
        cardAssignments = listOf(
            playerRecordWithCards("17", yellows = 1),
            PlayerRecord(
                jerseyNumber = "8",
                cards = listOf(InGamePlayerCardEvent(CardType.RED, index = 1)),
            ),
        )
        cardAssignments = replaceEditablePlayerCard(
            records = cardAssignments,
            editableCard = editablePlayerCards(cardAssignments).first(),
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
        assertTrue(
            cardAssignments.single().cards.contains(
                InGamePlayerCardEvent(CardType.RED, index = 1),
            )
        )
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
            editableCard = editablePlayerCards(cardAssignments).first(),
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

        // Suspension-status checks allow legal additions and report terminal same-player
        // card combinations.
        assertNull(
            playerSuspensionStatus(
                emptyList(),
                PlayerIdentity("99"),
            ),
        )
        assertNull(
            playerSuspensionStatus(
                listOf(playerRecordWithCards("17", yellows = 1)),
                PlayerIdentity("17"),
            ),
        )
        assertEquals(
            PlayerSuspensionStatus.TWO_YELLOWS,
            playerSuspensionStatus(
                listOf(playerRecordWithCards("17", yellows = 2)),
                PlayerIdentity("17"),
            ),
        )
        assertEquals(
            PlayerSuspensionStatus.RED_CARD,
            playerSuspensionStatus(
                listOf(playerRecordWithCards("17", reds = 1)),
                PlayerIdentity("17"),
            ),
        )
        assertNull(
            playerSuspensionStatus(
                listOf(priorPlayerRecord("17", priorReds = 1)),
                PlayerIdentity("17"),
            ),
        )
        assertEquals(
            PlayerSuspensionStatus.THREE_TOURNAMENT_YELLOWS,
            playerSuspensionStatus(
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
            PlayerSuspensionStatus.THREE_TOURNAMENT_YELLOWS,
            playerSuspensionStatus(
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

        // Yellow-card lookup and undo labels depend on whether the player already has a yellow.
        assertFalse(standardLiveGameState().playerHasYellowThisGame(VC, "99"))
        assertFalse(
            standardLiveGameState().copy(
                teamOnePlayers = listOf(PlayerRecord("99")),
            ).playerHasYellowThisGame(
                VC,
                "99",
            )
        )
        val yellowLookupState = standardLiveGameState().assessFirstYellowCard(VC, "99").state
        assertTrue(yellowLookupState.playerHasYellowThisGame(VC, "99"))

        // Team-level player records count in-game cards without including prior cards.
        assertEquals(
            2,
            listOf(
                playerRecordWithCards(jerseyNumber = "6", yellows = 1),
                playerRecordWithCards(jerseyNumber = "9", yellows = 1, reds = 1),
                PlayerRecord(jerseyNumber = "12", priorYellows = 1),
            ).inGameCardCount(CardType.YELLOW),
        )
        assertEquals(
            1,
            listOf(
                playerRecordWithCards(jerseyNumber = "6", yellows = 1),
                playerRecordWithCards(jerseyNumber = "9", yellows = 1, reds = 1),
                PlayerRecord(jerseyNumber = "12", priorReds = 1),
            ).inGameCardCount(CardType.RED),
        )
        assertEquals(
            "Undo Yellow on #100 of Viscous Coupling",
            standardLiveGameState().playerCardAddUndoLabel(
                VC,
                CardType.YELLOW,
                PlayerIdentity("100"),
            ),
        )
        assertEquals(
            "Undo Second yellow on #99 of Viscous Coupling",
            yellowLookupState.playerCardAddUndoLabel(VC, CardType.YELLOW, PlayerIdentity("99")),
        )
        assertEquals(
            "Undo Red on #101 of Viscous Coupling",
            yellowLookupState.playerCardAddUndoLabel(VC, CardType.RED, PlayerIdentity("101")),
        )
        assertEquals(
            "Undo Edit yellow on #99 of Viscous Coupling",
            yellowLookupState.playerCardEditUndoLabel(VC, CardType.YELLOW, PlayerIdentity("99")),
        )
        assertEquals(
            "Undo Remove yellow on #99 of Viscous Coupling",
            yellowLookupState.playerCardRemoveUndoLabel(VC, CardType.YELLOW, PlayerIdentity("99")),
        )

        // The UI reconciliation flow should prevent invalid records; if one reaches the model
        // anyway, fail loudly.
        val invalidPlayerCardMessage =
            "Player records must be no cards, one yellow, second yellow, red, " +
                "or one yellow plus red."
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
        assertEquals(
            "Player records cannot contain duplicate player entries.",
            duplicateCardException.message,
        )
    }

    /**
     * Test manual corrections that adjust blue-card and technical-foul counts.
     */
    @Test
    fun blueAndTechAdjustments() {
        // Count-only blue-card and technical-foul corrections are no-ops when counts do not
        // change.
        val unchangedCardCountState = standardLiveGameState().copy(
            teamOne = standardLiveGameState().teamOne.copy(blueCards = 1, technicalFouls = 2),
            teamTwo = standardLiveGameState().teamTwo.copy(blueCards = 3, technicalFouls = 4),
        )
        assertEquals(
            unchangedCardCountState,
            unchangedCardCountState.adjustBlueCardsAndTechs(
                teamOneBlues = 1,
                teamOneTechnicalFouls = 2,
                teamTwoBlues = 3,
                teamTwoTechnicalFouls = 4,
                now = 0L,
            ),
        )

        // Count-only corrections update each technical-foul or blue-card count independently.
        assertEquals(
            5,
            unchangedCardCountState.adjustBlueCardsAndTechs(
                teamOneBlues = 1,
                teamOneTechnicalFouls = 5,
                teamTwoBlues = 3,
                teamTwoTechnicalFouls = 4,
                now = 0L,
            ).teamOne.technicalFouls,
        )
        assertEquals(
            6,
            unchangedCardCountState.adjustBlueCardsAndTechs(
                teamOneBlues = 1,
                teamOneTechnicalFouls = 2,
                teamTwoBlues = 6,
                teamTwoTechnicalFouls = 4,
                now = 0L,
            ).teamTwo.blueCards,
        )
        assertEquals(
            7,
            unchangedCardCountState.adjustBlueCardsAndTechs(
                teamOneBlues = 1,
                teamOneTechnicalFouls = 2,
                teamTwoBlues = 3,
                teamTwoTechnicalFouls = 7,
                now = 0L,
            ).teamTwo.technicalFouls,
        )

        // Blue-card count corrections for team 1 should log one specific count-delta entry.
        val correctionBefore = standardLiveGameState()
        val blueCorrectionAfter = correctionBefore.adjustCardsAndTf(
            teamOneBlues = correctionBefore.teamOne.blueCards + 1,
            teamOneTechnicalFouls = correctionBefore.teamOne.technicalFouls,
            teamTwoBlues = correctionBefore.teamTwo.blueCards,
            teamTwoTechnicalFouls = correctionBefore.teamTwo.technicalFouls,
            teamOnePlayers = correctionBefore.teamOnePlayers,
            teamTwoPlayers = correctionBefore.teamTwoPlayers,
        )
        assertEquals(correctionBefore.eventLog.size + 1, blueCorrectionAfter.eventLog.size)
        assertEquals("Cards and technical fouls adjusted.", blueCorrectionAfter.lastEvent)

        // Blue-card count corrections for team 2 should use the same one-entry event-log shape.
        val teamTwoBlueCorrectionAfter = correctionBefore.adjustCardsAndTf(
            teamOneBlues = correctionBefore.teamOne.blueCards,
            teamOneTechnicalFouls = correctionBefore.teamOne.technicalFouls,
            teamTwoBlues = correctionBefore.teamTwo.blueCards + 1,
            teamTwoTechnicalFouls = correctionBefore.teamTwo.technicalFouls,
            teamOnePlayers = correctionBefore.teamOnePlayers,
            teamTwoPlayers = correctionBefore.teamTwoPlayers,
        )
        assertEquals(correctionBefore.eventLog.size + 1, teamTwoBlueCorrectionAfter.eventLog.size)
        assertEquals("Cards and technical fouls adjusted.", teamTwoBlueCorrectionAfter.lastEvent)

        // Technical-foul count corrections should log one specific count-delta entry for either
        // team.
        val teamOneTechnicalFoulCorrectionAfter = correctionBefore.adjustCardsAndTf(
            teamOneBlues = correctionBefore.teamOne.blueCards,
            teamOneTechnicalFouls = correctionBefore.teamOne.technicalFouls + 1,
            teamTwoBlues = correctionBefore.teamTwo.blueCards,
            teamTwoTechnicalFouls = correctionBefore.teamTwo.technicalFouls,
            teamOnePlayers = correctionBefore.teamOnePlayers,
            teamTwoPlayers = correctionBefore.teamTwoPlayers,
        )
        assertEquals(
            correctionBefore.eventLog.size + 1,
            teamOneTechnicalFoulCorrectionAfter.eventLog.size,
        )
        assertEquals(
            "Cards and technical fouls adjusted.",
            teamOneTechnicalFoulCorrectionAfter.lastEvent,
        )
        val teamTwoTechnicalFoulCorrectionAfter = correctionBefore.adjustCardsAndTf(
            teamOneBlues = correctionBefore.teamOne.blueCards,
            teamOneTechnicalFouls = correctionBefore.teamOne.technicalFouls,
            teamTwoBlues = correctionBefore.teamTwo.blueCards,
            teamTwoTechnicalFouls = correctionBefore.teamTwo.technicalFouls + 1,
            teamOnePlayers = correctionBefore.teamOnePlayers,
            teamTwoPlayers = correctionBefore.teamTwoPlayers,
        )
        assertEquals(
            correctionBefore.eventLog.size + 1,
            teamTwoTechnicalFoulCorrectionAfter.eventLog.size,
        )
        assertEquals(
            "Cards and technical fouls adjusted.",
            teamTwoTechnicalFoulCorrectionAfter.lastEvent,
        )
    }

    /**
     * Test manual corrections that adjust player-card records.
     */
    @Test
    fun playerCardAdjustments() {
        val VC = TeamId.TEAM_ONE
        val ANIMAL = TeamId.TEAM_TWO

        // Player-card correction derives yellow/red totals from player records while clamping
        // independent blue-card and technical-foul counts.
        val correctedTeamOnePlayerCards = listOf(
            playerRecordWithCards("17", yellows = 1),
            playerRecordWithCards("19", yellows = 1, reds = 1),
        )
        val correctedTeamTwoPlayerCards = listOf(
            playerRecordWithCards("23", reds = 1),
        )
        var state = standardLiveGameState()
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

        // No-op manual correction from the UI should behave like cancel and leave the state
        // untouched.
        val playerCardEditBefore = standardLiveGameState().assessFirstYellowCard(VC, "71").state
        val unchangedPlayerCardAfter = playerCardEditBefore.adjustCardsAndTf(
            teamOneBlues = playerCardEditBefore.teamOne.blueCards,
            teamOneTechnicalFouls = playerCardEditBefore.teamOne.technicalFouls,
            teamTwoBlues = playerCardEditBefore.teamTwo.blueCards,
            teamTwoTechnicalFouls = playerCardEditBefore.teamTwo.technicalFouls,
            teamOnePlayers = playerCardEditBefore.teamOnePlayers,
            teamTwoPlayers = playerCardEditBefore.teamTwoPlayers,
        )
        assertEquals(playerCardEditBefore, unchangedPlayerCardAfter)

        // Changing one player-card reason preserves the original yellow-card event type while
        // leaving unchanged player-card rows alone.
        val multiCardEditBefore = playerCardEditBefore.assessFirstYellowCard(VC, "72").state
        val reasonEditAfter = multiCardEditBefore.adjustCardsAndTf(
            teamOneBlues = multiCardEditBefore.teamOne.blueCards,
            teamOneTechnicalFouls = multiCardEditBefore.teamOne.technicalFouls,
            teamTwoBlues = multiCardEditBefore.teamTwo.blueCards,
            teamTwoTechnicalFouls = multiCardEditBefore.teamTwo.technicalFouls,
            teamOnePlayers = listOf(
                PlayerRecord(
                    jerseyNumber = "71",
                    cards = listOf(
                        InGamePlayerCardEvent(
                            CardType.YELLOW,
                            index = 0,
                            reason = CardReason(preset = "Pushing"),
                        )
                    ),
                ),
                playerRecordWithCards("72", yellows = 1),
            ),
            teamTwoPlayers = multiCardEditBefore.teamTwoPlayers,
        )
        assertEquals(EventLogType.YELLOW_CARD, reasonEditAfter.eventLog.last().type)

        // Changing only the player-card identity also preserves the original yellow-card event
        // type.
        val yellowIdentityEditAfter = playerCardEditBefore.adjustCardsAndTf(
            teamOneBlues = playerCardEditBefore.teamOne.blueCards,
            teamOneTechnicalFouls = playerCardEditBefore.teamOne.technicalFouls,
            teamTwoBlues = playerCardEditBefore.teamTwo.blueCards,
            teamTwoTechnicalFouls = playerCardEditBefore.teamTwo.technicalFouls,
            teamOnePlayers = listOf(
                PlayerRecord(
                    jerseyNumber = "72",
                    cards = listOf(InGamePlayerCardEvent(CardType.YELLOW, index = 0)),
                )
            ),
            teamTwoPlayers = playerCardEditBefore.teamTwoPlayers,
        )
        assertEquals(EventLogType.YELLOW_CARD, yellowIdentityEditAfter.eventLog.last().type)

        // Changing the underlying card type is a broader manual correction with a generic
        // last-event label.
        val cardTypeEditAfter = playerCardEditBefore.adjustCardsAndTf(
            teamOneBlues = playerCardEditBefore.teamOne.blueCards,
            teamOneTechnicalFouls = playerCardEditBefore.teamOne.technicalFouls,
            teamTwoBlues = playerCardEditBefore.teamTwo.blueCards,
            teamTwoTechnicalFouls = playerCardEditBefore.teamTwo.technicalFouls,
            teamOnePlayers = listOf(
                PlayerRecord(
                    jerseyNumber = "71",
                    cards = listOf(InGamePlayerCardEvent(CardType.RED, index = 0)),
                )
            ),
            teamTwoPlayers = playerCardEditBefore.teamTwoPlayers,
        )
        assertEquals("Cards and technical fouls adjusted.", cardTypeEditAfter.lastEvent)

        // Adding or removing multiple player cards from one player records one event-log entry per
        // card.
        val twoYellowAdditionAfter = standardLiveGameState().adjustCardsAndTf(
            teamOneBlues = 0,
            teamOneTechnicalFouls = 0,
            teamTwoBlues = 0,
            teamTwoTechnicalFouls = 0,
            teamOnePlayers = listOf(playerRecordWithCards("80", yellows = 2)),
            teamTwoPlayers = emptyList(),
        )
        assertEquals(
            listOf(1, 1),
            twoYellowAdditionAfter.eventLog.takeLast(2).map { it.delta },
        )
        val twoYellowRemovalBefore = standardLiveGameState()
            .assessFirstYellowCard(VC, "80")
            .state
            .assessSecondYellowCard(VC, "80")
            .state
        val twoYellowRemovalAfter = twoYellowRemovalBefore.adjustCardsAndTf(
            teamOneBlues = twoYellowRemovalBefore.teamOne.blueCards,
            teamOneTechnicalFouls = twoYellowRemovalBefore.teamOne.technicalFouls,
            teamTwoBlues = twoYellowRemovalBefore.teamTwo.blueCards,
            teamTwoTechnicalFouls = twoYellowRemovalBefore.teamTwo.technicalFouls,
            teamOnePlayers = emptyList(),
            teamTwoPlayers = twoYellowRemovalBefore.teamTwoPlayers,
        )
        assertEquals(
            listOf(
                EventLogType.YELLOW_CARD,
                EventLogType.YELLOW_CARD,
            ),
            twoYellowRemovalAfter.eventLog.takeLast(2).map { it.type },
        )
        assertEquals(
            listOf(-1, -1),
            twoYellowRemovalAfter.eventLog.takeLast(2).map { it.delta },
        )
    }

    /**
     * Return a single player's card record from a team after a test action.
     *
     * @param state The live game state to inspect.
     * @param team The team whose player records should be searched.
     * @param jerseyNumber The player number expected to have exactly one record.
     */
    private fun playerRecord(
        state: GameState,
        team: TeamId,
        jerseyNumber: String,
    ): PlayerRecord {
        return state.playerCards(team).single { it.jerseyNumber == jerseyNumber }
    }
}
