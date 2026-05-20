package rmjarvis.ultiobserver

import kotlinx.serialization.Serializable
import kotlin.math.max

/**
 * Tournament carryover cards for one player before the current game starts.
 *
 * @param team The player's team.
 * @param jerseyNumber The player's jersey number, or `N/A` when unknown.
 * @param priorYellows Yellow cards issued in previous games of the current tournament.
 * @param priorReds Red cards issued in previous games of the current tournament.
 */
@Serializable
data class PlayerCardRecord(
    val team: TeamId,
    val jerseyNumber: String,
    val priorYellows: Int,    // Cards issued in previous games of the current tournament.
    val priorReds: Int,
)

/**
 * Player-card type being assigned or reconciled.
 *
 * @param label The user-facing card label.
 */
enum class CardType(val label: String) {
    YELLOW("Yellow"),
    RED("Red"),
}

/**
 * In-game yellow/red card record for one player.
 *
 * @param jerseyNumber The player's jersey number, or `N/A` when unknown.
 * @param yellows The number of in-game yellows represented by this player's record.
 * @param reds The number of in-game reds represented by this player's record.
 */
@Serializable
data class InGamePlayerCardRecord(
    val jerseyNumber: String,
    val yellows: Int = 0,
    val reds: Int = 0,
) {
    /// Report whether this per-player card combination is allowed by the app's card model.
    fun hasLegalCounts(): Boolean {
        return yellows <= 2 &&
            reds <= 1 &&
            (yellows < 2 || reds == 0)
    }

    /**
     * Count this player's cards of the requested type.
     *
     * @param cardType The card type whose count should be returned.
     */
    fun cardCount(cardType: CardType): Int {
        return when (cardType) {
            CardType.YELLOW -> yellows
            CardType.RED -> reds
        }
    }
}

// How to indicate cards for players when you don't know the player number.
const val UNKNOWN_PLAYER_NUMBER = "N/A"

/**
 * Format a player number for display in card and misconduct summaries.
 *
 * @param jerseyNumber The stored jersey number, or the unknown-player sentinel.
 */
internal fun displayPlayerNumber(jerseyNumber: String): String {
    return if (jerseyNumber == UNKNOWN_PLAYER_NUMBER) {
        "N/A"
    } else {
        "#$jerseyNumber"
    }
}

/**
 * State and popup needs from assessing a card or technical foul.
 *
 * @param state The live state after the assessment.
 * @param event The observer-facing event to show.
 * @param needsMisconductChoice Whether the UI must ask offense/defense before resolving live-point misconduct.
 */
data class CardAssessmentResult(
    val state: LiveGameState,
    val event: GameEvent,
    val needsMisconductChoice: Boolean =
        event.needsMisconductChoice(),
)

/// Player-card event type used when formatting card popups.
enum class PlayerCardEventType {
    YELLOW,
    RED,
    SECOND_YELLOW,
}

/**
 * Replace team card and technical-foul counts as a manual correction.
 *
 * @param teamOneBlues The corrected blue-card count for team one.
 * @param teamOneTechnicalFouls The corrected technical-foul count for team one.
 * @param teamTwoBlues The corrected blue-card count for team two.
 * @param teamTwoTechnicalFouls The corrected technical-foul count for team two.
 * @param teamOnePlayerCards The reconciled per-player yellow/red records for team one.
 * @param teamTwoPlayerCards The reconciled per-player yellow/red records for team two.
 */
fun LiveGameState.adjustCardsAndTf(
    teamOneBlues: Int,
    teamOneTechnicalFouls: Int,
    teamTwoBlues: Int,
    teamTwoTechnicalFouls: Int,
    teamOnePlayerCards: List<InGamePlayerCardRecord>,
    teamTwoPlayerCards: List<InGamePlayerCardRecord>,
    now: Long,
): LiveGameState {
    requirePlayerCardRecordsValid(teamOnePlayerCards)
    requirePlayerCardRecordsValid(teamTwoPlayerCards)
    val adjustedTeamOneBlues = teamOneBlues.coerceAtLeast(0)
    val adjustedTeamOneTechnicalFouls = teamOneTechnicalFouls.coerceAtLeast(0)
    val adjustedTeamTwoBlues = teamTwoBlues.coerceAtLeast(0)
    val adjustedTeamTwoTechnicalFouls = teamTwoTechnicalFouls.coerceAtLeast(0)
    val entries = buildCardAndTfAdjustmentEntries(
        teamOneBlues = adjustedTeamOneBlues,
        teamOneTechnicalFouls = adjustedTeamOneTechnicalFouls,
        teamTwoBlues = adjustedTeamTwoBlues,
        teamTwoTechnicalFouls = adjustedTeamTwoTechnicalFouls,
        teamOnePlayerCards = teamOnePlayerCards,
        teamTwoPlayerCards = teamTwoPlayerCards,
        now = now,
    )

    return this.copy(
        teamOne = this.teamOne.copy(
            blueCards = adjustedTeamOneBlues,
            technicalFouls = adjustedTeamOneTechnicalFouls,
        ),
        teamTwo = this.teamTwo.copy(
            blueCards = adjustedTeamTwoBlues,
            technicalFouls = adjustedTeamTwoTechnicalFouls,
        ),
        teamOnePlayerCards = teamOnePlayerCards,
        teamTwoPlayerCards = teamTwoPlayerCards,
        lastEvent = "Cards and technical fouls adjusted.",
    ).withEventLogEntries(entries).withUndo(this, "Undo Cards / TF Adjustment")
}

/**
 * Build event-log entries that describe each card and technical-foul correction delta.
 *
 * @param teamOneBlues The corrected blue-card count for team one.
 * @param teamOneTechnicalFouls The corrected technical-foul count for team one.
 * @param teamTwoBlues The corrected blue-card count for team two.
 * @param teamTwoTechnicalFouls The corrected technical-foul count for team two.
 * @param teamOnePlayerCards The corrected player-card records for team one.
 * @param teamTwoPlayerCards The corrected player-card records for team two.
 * @param now The correction timestamp.
 */
private fun LiveGameState.buildCardAndTfAdjustmentEntries(
    teamOneBlues: Int,
    teamOneTechnicalFouls: Int,
    teamTwoBlues: Int,
    teamTwoTechnicalFouls: Int,
    teamOnePlayerCards: List<InGamePlayerCardRecord>,
    teamTwoPlayerCards: List<InGamePlayerCardRecord>,
    now: Long,
): List<EventLogEntry> {
    return buildList {
        addCardCountDelta(now, TeamId.TEAM_ONE, EventLogType.BLUE_CARD, teamOneBlues - teamOne.blueCards)
        addTechnicalFoulDelta(now, TeamId.TEAM_ONE, teamOneTechnicalFouls - teamOne.technicalFouls)
        addPlayerCardDeltas(now, TeamId.TEAM_ONE, this@buildCardAndTfAdjustmentEntries.teamOnePlayerCards, teamOnePlayerCards)
        addCardCountDelta(now, TeamId.TEAM_TWO, EventLogType.BLUE_CARD, teamTwoBlues - teamTwo.blueCards)
        addTechnicalFoulDelta(now, TeamId.TEAM_TWO, teamTwoTechnicalFouls - teamTwo.technicalFouls)
        addPlayerCardDeltas(now, TeamId.TEAM_TWO, this@buildCardAndTfAdjustmentEntries.teamTwoPlayerCards, teamTwoPlayerCards)
    }
}

/**
 * Add entries for player-card count differences between two record lists.
 *
 * @param now The correction timestamp.
 * @param team The team whose records changed.
 * @param beforeRecords The records before correction.
 * @param afterRecords The records after correction.
 */
private fun MutableList<EventLogEntry>.addPlayerCardDeltas(
    now: Long,
    team: TeamId,
    beforeRecords: List<InGamePlayerCardRecord>,
    afterRecords: List<InGamePlayerCardRecord>,
) {
    val jerseyNumbers = (beforeRecords.map { it.jerseyNumber } + afterRecords.map { it.jerseyNumber }).distinct()
    jerseyNumbers.forEach { jerseyNumber ->
        val before = beforeRecords.firstOrNull { it.jerseyNumber == jerseyNumber } ?: InGamePlayerCardRecord(jerseyNumber)
        val after = afterRecords.firstOrNull { it.jerseyNumber == jerseyNumber } ?: InGamePlayerCardRecord(jerseyNumber)
        addCardCountDelta(
            now = now,
            team = team,
            type = EventLogType.YELLOW_CARD,
            delta = after.yellows - before.yellows,
            playerNumber = jerseyNumber,
        )
        addCardCountDelta(
            now = now,
            team = team,
            type = EventLogType.RED_CARD,
            delta = after.reds - before.reds,
            playerNumber = jerseyNumber,
        )
    }
}

/**
 * Add one or more card-count correction entries.
 *
 * @param now The correction timestamp.
 * @param team The team whose card count changed.
 * @param type The card event type whose count changed.
 * @param delta The signed count change.
 * @param playerNumber The player number for player-card corrections.
 */
private fun MutableList<EventLogEntry>.addCardCountDelta(
    now: Long,
    team: TeamId,
    type: EventLogType,
    delta: Int,
    playerNumber: String? = null,
) {
    if (delta != 0) {
        repeat(kotlin.math.abs(delta)) {
            add(
                EventLogEntry(
                    timestampEpoch = now,
                    type = type,
                    team = team,
                    playerNumber = playerNumber,
                    delta = if (delta > 0) 1 else -1,
                )
            )
        }
    }
}

/**
 * Add a technical-foul correction entry when a count changed.
 *
 * @param now The correction timestamp.
 * @param team The team whose technical-foul count changed.
 * @param delta The signed count change.
 */
private fun MutableList<EventLogEntry>.addTechnicalFoulDelta(now: Long, team: TeamId, delta: Int) {
    if (delta != 0) {
        add(
            EventLogEntry(
                timestampEpoch = now,
                type = EventLogType.TECHNICAL_FOUL,
                team = team,
                delta = delta,
            )
        )
    }
}

/**
 * Reject impossible per-player card records before they enter live state.
 * This makes failures obvious if a caller bypasses the normal player-card adjustment flow.
 *
 * @param records The player-card records to validate.
 */
private fun requirePlayerCardRecordsValid(records: List<InGamePlayerCardRecord>) {
    require(records.all { it.yellows >= 0 && it.reds >= 0 }) {
        "Player card records cannot have negative card counts."
    }
    require(records.all { it.hasLegalCounts() }) {
        "Player card records must be no cards, one yellow, second yellow, red, or one yellow plus red."
    }
    require(records.distinctBy { it.jerseyNumber }.size == records.size) {
        "Player card records cannot contain duplicate player entries."
    }
}
/**
 * Report whether adding a card to one player would keep the player's card combination legal.
 *
 * @param records The current player-card records for that team.
 * @param jerseyNumber The player receiving the possible card, or `N/A` for an unknown player.
 * @param cardType The type of card being considered.
 */
fun canAddPlayerCardAssignment(
    records: List<InGamePlayerCardRecord>,
    jerseyNumber: String,
    cardType: CardType,
): Boolean {
    val existingRecord = records.firstOrNull { it.jerseyNumber == jerseyNumber }
        ?: InGamePlayerCardRecord(jerseyNumber = jerseyNumber)
    val updatedRecord = when (cardType) {
        CardType.YELLOW -> existingRecord.copy(yellows = existingRecord.yellows + 1)
        CardType.RED -> existingRecord.copy(reds = existingRecord.reds + 1)
    }
    return updatedRecord.hasLegalCounts()
}
/**
 * Turn requested yellow/red totals into explicit player-card add/remove steps.
 *
 * @param teamOneYellows The desired in-game yellow count for team one.
 * @param teamOneReds The desired in-game red count for team one.
 * @param teamTwoYellows The desired in-game yellow count for team two.
 * @param teamTwoReds The desired in-game red count for team two.
 */
fun LiveGameState.buildPlayerCardAdjustmentSteps(
    teamOneYellows: Int,
    teamOneReds: Int,
    teamTwoYellows: Int,
    teamTwoReds: Int,
): List<PlayerCardAdjustmentStep> {
    val stateTeamOneYellows = this.teamYellowCards(TeamId.TEAM_ONE)
    val stateTeamOneReds = this.teamRedCards(TeamId.TEAM_ONE)
    val stateTeamTwoYellows = this.teamYellowCards(TeamId.TEAM_TWO)
    val stateTeamTwoReds = this.teamRedCards(TeamId.TEAM_TWO)

    return buildList {
        /**
         * Add reconciliation steps for one team's desired card count.
         *
         * @param team The team whose player-card records need adjustment.
         * @param cardType The card type being reconciled.
         * @param desiredCount The count requested by the correction UI.
         * @param currentCount The count currently represented in model state.
         */
        fun addSteps(team: TeamId, cardType: CardType, desiredCount: Int, currentCount: Int) {
            repeat(maxOf(0, desiredCount - currentCount)) {
                add(PlayerCardAdjustmentStep(team, cardType, PlayerCardAdjustmentMode.ADD))
            }
            repeat(maxOf(0, currentCount - desiredCount)) {
                add(PlayerCardAdjustmentStep(team, cardType, PlayerCardAdjustmentMode.REMOVE))
            }
        }

        addSteps(TeamId.TEAM_ONE, CardType.YELLOW, teamOneYellows, stateTeamOneYellows)
        addSteps(TeamId.TEAM_ONE, CardType.RED, teamOneReds, stateTeamOneReds)
        addSteps(TeamId.TEAM_TWO, CardType.YELLOW, teamTwoYellows, stateTeamTwoYellows)
        addSteps(TeamId.TEAM_TWO, CardType.RED, teamTwoReds, stateTeamTwoReds)
    }
}
/**
 * List players who currently have a card of the requested type available to remove.
 *
 * @param records The current player-card records for one team.
 * @param cardType The card type the correction flow wants to remove.
 */
fun playerCardRemovalCandidates(
    records: List<InGamePlayerCardRecord>,
    cardType: CardType,
): List<PlayerCardRemovalCandidate> {
    return records.mapNotNull { record ->
        val count = record.cardCount(cardType)
        if (count > 0) {
            PlayerCardRemovalCandidate(record.jerseyNumber, count)
        } else {
            null
        }
    }
}
/**
 * Add a yellow or red card assignment to a specific player record.
 *
 * @param records The current player-card records for one team.
 * @param jerseyNumber The player receiving the card, or `N/A` for an unknown player.
 * @param cardType The card type to add.
 */
fun addPlayerCardAssignment(
    records: List<InGamePlayerCardRecord>,
    jerseyNumber: String,
    cardType: CardType,
): List<InGamePlayerCardRecord> {
    return updatePlayerCardRecord(records, jerseyNumber) { record ->
        when (cardType) {
            CardType.YELLOW -> record.copy(yellows = record.yellows + 1)
            CardType.RED -> record.copy(reds = record.reds + 1)
        }
    }
}
/**
 * Remove one yellow or red card assignment from a specific player record.
 *
 * @param records The current player-card records for one team.
 * @param jerseyNumber The player whose card should be removed.
 * @param cardType The card type to remove.
 */
fun removePlayerCardAssignment(
    records: List<InGamePlayerCardRecord>,
    jerseyNumber: String,
    cardType: CardType,
): List<InGamePlayerCardRecord> {
    val existingIndex = records.indexOfFirst { it.jerseyNumber == jerseyNumber }
    if (existingIndex < 0) {
        return records
    }
    return records.mapIndexedNotNull { index, record ->
        if (index != existingIndex) {
            record
        } else {
            val updated = when (cardType) {
                CardType.YELLOW -> record.copy(yellows = max(0, record.yellows - 1))
                CardType.RED -> record.copy(reds = max(0, record.reds - 1))
            }
            if (updated.yellows == 0 && updated.reds == 0) null else updated
        }
    }
}
/**
 * Record a blue card and determine whether it triggers misconduct handling.
 *
 * @param team The team receiving the blue card.
 */
fun LiveGameState.assessBlueCard(team: TeamId, now: Long): CardAssessmentResult {
    var updatedState = this.copy(
        teamOne = if (team == TeamId.TEAM_ONE) {
            this.teamOne.copy(blueCards = this.teamOne.blueCards + 1)
        } else {
            this.teamOne
        },
        teamTwo = if (team == TeamId.TEAM_TWO) {
            this.teamTwo.copy(blueCards = this.teamTwo.blueCards + 1)
        } else {
            this.teamTwo
        },
        lastEvent = "Blue card assessed to ${this.teamName(team)}.",
    ).withEventLogEntry(
        EventLogEntry(
            timestampEpoch = now,
            type = EventLogType.BLUE_CARD,
            team = team,
        )
    ).withUndo(this, "Undo Blue Card on ${this.teamName(team)}")
    val cardTotal = updatedState.teamCardTotal(team)
    updatedState = updatedState.withSkippedPullForMisconductThreshold(cardTotal)
    return CardAssessmentResult(
        state = updatedState,
        event = GameEvent.TeamCardsChanged(
            state = updatedState,
            team = team,
            teamCardTotal = cardTotal,
        ),
    )
}
/**
 * Record a technical foul and determine whether it triggers misconduct handling.
 *
 * @param team The team receiving the technical foul.
 */
fun LiveGameState.assessTechnicalFoul(team: TeamId, now: Long): CardAssessmentResult {
    var updatedState = this.copy(
        teamOne = if (team == TeamId.TEAM_ONE) {
            this.teamOne.copy(technicalFouls = this.teamOne.technicalFouls + 1)
        } else {
            this.teamOne
        },
        teamTwo = if (team == TeamId.TEAM_TWO) {
            this.teamTwo.copy(technicalFouls = this.teamTwo.technicalFouls + 1)
        } else {
            this.teamTwo
        },
        lastEvent = "Technical foul on ${this.teamName(team)}.",
    ).withEventLogEntry(
        EventLogEntry(
            timestampEpoch = now,
            type = EventLogType.TECHNICAL_FOUL,
            team = team,
        )
    ).withUndo(this, "Undo Technical Foul on ${this.teamName(team)}")
    val technicalFouls = if (team == TeamId.TEAM_ONE) {
        updatedState.teamOne.technicalFouls
    } else {
        updatedState.teamTwo.technicalFouls
    }
    updatedState = updatedState.withSkippedPullForMisconductThreshold(technicalFouls)
    return CardAssessmentResult(
        state = updatedState,
        event = GameEvent.TechnicalFoulsChanged(
            state = updatedState,
            team = team,
            technicalFoulTotal = technicalFouls,
        ),
    )
}
/**
 * Record a yellow-card action, promoting it to second yellow when the player already has one.
 * The same observer action can mean either a first yellow or a second yellow depending on the player record.
 *
 * @param team The team receiving the yellow-card action.
 * @param jerseyNumber The player receiving the card, or `N/A` when the player is unknown.
 */
fun LiveGameState.assessYellowCard(team: TeamId, jerseyNumber: String, now: Long): CardAssessmentResult {
    val currentRecord = this.playerCardFor(team, jerseyNumber)
    return if (currentRecord?.yellows ?: 0 >= 1) {
        this.assessSecondYellowCard(team, jerseyNumber, now)
    } else {
        this.assessFirstYellowCard(team, jerseyNumber, now)
    }
}
/**
 * Record a first yellow for a player and determine any misconduct consequence.
 *
 * @param team The team receiving the yellow card.
 * @param jerseyNumber The player receiving the card, or `N/A` when the player is unknown.
 */
fun LiveGameState.assessFirstYellowCard(team: TeamId, jerseyNumber: String, now: Long): CardAssessmentResult {
    var updatedState = this.addInGameYellowCard(team, jerseyNumber)
        .withEventLogEntry(
            EventLogEntry(
                timestampEpoch = now,
                type = EventLogType.SECOND_YELLOW,
                team = team,
                playerNumber = jerseyNumber,
            )
        ).withUndo(this, playerCardUndoLabel("Yellow", team, jerseyNumber))
    val cardTotal = updatedState.teamCardTotal(team)
    updatedState = updatedState.withSkippedPullForMisconductThreshold(cardTotal)
    return CardAssessmentResult(
        state = updatedState,
        event = GameEvent.TeamCardsChanged(
            state = updatedState,
            team = team,
            teamCardTotal = cardTotal,
            playerCardType = PlayerCardEventType.YELLOW,
            playerCardJerseyNumber = jerseyNumber,
        ),
    )
}
/**
 * Record a red card and determine any misconduct consequence.
 *
 * @param team The team receiving the red card.
 * @param jerseyNumber The player receiving the red card, or `N/A` when the player is unknown.
 */
fun LiveGameState.assessRedCard(
    team: TeamId,
    jerseyNumber: String,
    now: Long,
): CardAssessmentResult {
    var updatedState = this.addInGameRedCard(team, jerseyNumber)
        .withEventLogEntry(
            EventLogEntry(
                timestampEpoch = now,
                type = EventLogType.RED_CARD,
                team = team,
                playerNumber = jerseyNumber,
            )
        ).withUndo(this, playerCardUndoLabel("Red", team, jerseyNumber))
    val cardTotal = updatedState.teamCardTotal(team)
    updatedState = updatedState.withSkippedPullForMisconductThreshold(cardTotal)
    return CardAssessmentResult(
        state = updatedState,
        event = GameEvent.TeamCardsChanged(
            state = updatedState,
            team = team,
            teamCardTotal = cardTotal,
            playerCardType = PlayerCardEventType.RED,
            playerCardJerseyNumber = jerseyNumber,
        ),
    )
}

/**
 * Record a second yellow card and determine any misconduct consequence.
 *
 * @param team The team receiving the second yellow.
 * @param jerseyNumber The player receiving the second yellow, or `N/A` when the player is unknown.
 */
fun LiveGameState.assessSecondYellowCard(team: TeamId, jerseyNumber: String, now: Long): CardAssessmentResult {
    var updatedState = this.addInGameSecondYellow(team, jerseyNumber)
        .withEventLogEntry(
            EventLogEntry(
                timestampEpoch = now,
                type = EventLogType.YELLOW_CARD,
                team = team,
                playerNumber = jerseyNumber,
            )
        ).withUndo(this, playerCardUndoLabel("Second Yellow", team, jerseyNumber))
    val cardTotal = updatedState.teamCardTotal(team)
    updatedState = updatedState.withSkippedPullForMisconductThreshold(cardTotal)
    return CardAssessmentResult(
        state = updatedState,
        event = GameEvent.TeamCardsChanged(
            state = updatedState,
            team = team,
            teamCardTotal = cardTotal,
            playerCardType = PlayerCardEventType.SECOND_YELLOW,
            playerCardJerseyNumber = jerseyNumber,
        ),
    )
}

/**
 * Build the undo label for a player-card action with the jersey number kept early for narrow UI.
 *
 * @param action The card action label, such as `Yellow`, `Second Yellow`, or `Red`.
 * @param team The team whose name should appear in the undo label.
 * @param jerseyNumber The player identifier to include in the undo label.
 */
private fun LiveGameState.playerCardUndoLabel(action: String, team: TeamId, jerseyNumber: String): String {
    return "Undo $action on #$jerseyNumber of ${this.teamName(team)}"
}

/**
 * Convert between-points misconduct threshold actions into a no-pull sequence when applicable.
 *
 * @param thresholdCount The team-card or technical-foul count after the recorded action.
 */
private fun LiveGameState.withSkippedPullForMisconductThreshold(thresholdCount: Int): LiveGameState {
    if (thresholdCount < 3 || this.phase == LivePhase.LIVE_POINT || this.phase == LivePhase.GAME_OVER) {
        return this
    }
    return this.copy(
        countdown = this.countdown!!.toBetweenPointsMisconductCountdown(),
        pullSkippedForCurrentPoint = true,
    )
}

/// Convert the current between-points countdown into the misconduct offense-set countdown.
private fun CountdownState.toBetweenPointsMisconductCountdown(): CountdownState {
    val sequenceStart = targetEpoch - durationSeconds * 1000L
    val durationSeconds = 90
    return CountdownState(
        kind = CountdownKind.MISCONDUCT_BETWEEN_POINTS,
        label = "Offense set in",
        durationSeconds = durationSeconds,
        targetEpoch = sequenceStart + durationSeconds * 1000L,
    )
}
/// Mode describing whether a player-card reconciliation step adds or removes a card.
enum class PlayerCardAdjustmentMode {
    ADD,
    REMOVE,
}
/**
 * Explicit player-card add/remove prompt needed to reconcile corrected totals.
 *
 * @param team The team whose player-card record is being adjusted.
 * @param cardType The card type being added or removed.
 * @param mode Whether this step adds or removes one card.
 */
data class PlayerCardAdjustmentStep(
    val team: TeamId,
    val cardType: CardType,
    val mode: PlayerCardAdjustmentMode,
)
/**
 * Player with a removable card during manual reconciliation.
 *
 * @param jerseyNumber The player's jersey number, or `N/A` when unknown.
 * @param cardCount The number of cards of the requested type currently on that record.
 */
data class PlayerCardRemovalCandidate(
    val jerseyNumber: String,
    val cardCount: Int,
)
/**
 * Add a first yellow card to a team's in-game player-card records.
 *
 * @param team The team receiving the yellow card.
 * @param jerseyNumber The player receiving the card, or `N/A` when the player is unknown.
 */
private fun LiveGameState.addInGameYellowCard(team: TeamId, jerseyNumber: String): LiveGameState {
    return withPlayerCards(
        team = team,
        records = updatePlayerCardRecord(
            records = playerCardsFor(team),
            jerseyNumber = jerseyNumber,
        ) { record ->
            record.copy(yellows = record.yellows + 1)
        },
        lastEvent = "Yellow card for ${teamName(team)} #$jerseyNumber.",
    )
}
/**
 * Add a second yellow card to a team's in-game player-card records.
 *
 * @param team The team receiving the second yellow.
 * @param jerseyNumber The player receiving the card, or `N/A` when the player is unknown.
 */
private fun LiveGameState.addInGameSecondYellow(team: TeamId, jerseyNumber: String): LiveGameState {
    return withPlayerCards(
        team = team,
        records = updatePlayerCardRecord(
            records = playerCardsFor(team),
            jerseyNumber = jerseyNumber,
        ) { record ->
            record.copy(yellows = record.yellows + 1)
        },
        lastEvent = "Second yellow for ${teamName(team)} #$jerseyNumber.",
    )
}
/**
 * Add a red card to a team's in-game player-card records.
 *
 * @param team The team receiving the red card.
 * @param jerseyNumber The player receiving the card, or `N/A` when the player is unknown.
 */
private fun LiveGameState.addInGameRedCard(team: TeamId, jerseyNumber: String): LiveGameState {
    return withPlayerCards(
        team = team,
        records = updatePlayerCardRecord(
            records = playerCardsFor(team),
            jerseyNumber = jerseyNumber,
        ) { record ->
            record.copy(reds = record.reds + 1)
        },
        lastEvent = "Red card for ${teamName(team)} #$jerseyNumber.",
    )
}
/**
 * Update or create one player-card record and validate the resulting list.
 *
 * @param records The current player-card records for one team.
 * @param jerseyNumber The player record to update or create.
 * @param transform The exact card-count change to apply to that player's record.
 */
private fun updatePlayerCardRecord(
    records: List<InGamePlayerCardRecord>,
    jerseyNumber: String,
    transform: (InGamePlayerCardRecord) -> InGamePlayerCardRecord,
): List<InGamePlayerCardRecord> {
    val existingIndex = records.indexOfFirst { it.jerseyNumber == jerseyNumber }
    val updatedRecords = if (existingIndex >= 0) {
        records.mapIndexed { index, record ->
            if (index == existingIndex) transform(record) else record
        }
    } else {
        records + transform(InGamePlayerCardRecord(jerseyNumber = jerseyNumber))
    }
    requirePlayerCardRecordsValid(updatedRecords)
    return updatedRecords
}
/**
 * Report whether a player already has a yellow card in this game.
 *
 * @param team The team whose player-card records should be searched.
 * @param jerseyNumber The player to check, or `N/A` for an unknown-player record.
 */
fun LiveGameState.playerHasYellowThisGame(team: TeamId, jerseyNumber: String): Boolean {
    return (this.playerCardFor(team, jerseyNumber)?.yellows ?: 0) > 0
}
/**
 * Return the in-game player-card records for one team.
 *
 * @param team The team whose player-card records should be returned.
 */
fun LiveGameState.playerCards(team: TeamId): List<InGamePlayerCardRecord> {
    return this.playerCardsFor(team)
}
/**
 * Count in-game yellow cards from one team's player-card records.
 *
 * @param team The team whose yellow cards should be counted.
 */
fun LiveGameState.teamYellowCards(team: TeamId): Int {
    return this.playerCardsFor(team).sumOf { it.yellows }
}
/**
 * Count in-game red cards from one team's player-card records.
 *
 * @param team The team whose red cards should be counted.
 */
fun LiveGameState.teamRedCards(team: TeamId): Int {
    return this.playerCardsFor(team).sumOf { it.reds }
}
/**
 * Count total team card points: yellow plus blue plus two per red.
 *
 * @param team The team whose card total should be counted.
 */
fun LiveGameState.teamCardTotal(team: TeamId): Int {
    val currentTeam = if (team == TeamId.TEAM_ONE) this.teamOne else this.teamTwo
    var yellowCards = 0
    var redCards = 0
    playerCardsFor(team).forEach { record ->
        yellowCards += record.yellows
        redCards += record.reds
    }
    return yellowCards + currentTeam.blueCards + (2 * redCards)
}
/**
 * Return the stored player-card records for one team.
 *
 * @param team The team whose player-card list should be selected.
 */
private fun LiveGameState.playerCardsFor(team: TeamId): List<InGamePlayerCardRecord> {
    return if (team == TeamId.TEAM_ONE) teamOnePlayerCards else teamTwoPlayerCards
}
/**
 * Replace one team's player-card records and stores the related event text.
 *
 * @param team The team whose player-card records should be replaced.
 * @param records The validated player-card records to store.
 * @param lastEvent The short event text for the live state.
 */
private fun LiveGameState.withPlayerCards(
    team: TeamId,
    records: List<InGamePlayerCardRecord>,
    lastEvent: String,
): LiveGameState {
    return when (team) {
        TeamId.TEAM_ONE -> copy(
            teamOnePlayerCards = records,
            lastEvent = lastEvent,
        )
        TeamId.TEAM_TWO -> copy(
            teamTwoPlayerCards = records,
            lastEvent = lastEvent,
        )
    }
}
/**
 * Find one player's in-game card record.
 *
 * @param team The team whose player-card records should be searched.
 * @param jerseyNumber The player identifier to find.
 */
private fun LiveGameState.playerCardFor(team: TeamId, jerseyNumber: String): InGamePlayerCardRecord? {
    return playerCardsFor(team).firstOrNull { it.jerseyNumber == jerseyNumber }
}

/// Format the popup title for a team-card event.
internal fun GameEvent.TeamCardsChanged.formatPopupTitle(): String {
    return if (teamCardTotal >= 3) "Misconduct Penalty" else "Misconduct"
}

/// Format the popup title for a technical-foul event.
internal fun GameEvent.TechnicalFoulsChanged.formatPopupTitle(): String {
    return if (technicalFoulTotal >= 3) "Misconduct Penalty" else "Misconduct"
}

/// Report whether this event needs an offense/defense choice before showing the penalty cue.
fun GameEvent.needsMisconductChoice(): Boolean {
    return when (this) {
        is GameEvent.TeamCardsChanged -> teamCardTotal >= 3 && state.phase == LivePhase.LIVE_POINT
        is GameEvent.TechnicalFoulsChanged -> technicalFoulTotal >= 3 && state.phase == LivePhase.LIVE_POINT
        else -> false
    }
}

/// Format a team-card event message, including player-card and misconduct cue details.
internal fun GameEvent.TeamCardsChanged.formatMessage(): String {
    val totalMessage = "${state.teamName(team)} has $teamCardTotal ${pluralize(teamCardTotal, "card")}."
    val baseMessage = if (playerCardType == null) {
        totalMessage
    } else {
        val jerseyNumber = playerCardJerseyNumber as String
        (playerCardEventLines(playerCardType, jerseyNumber) + totalMessage).joinToString("\n")
    }
    return baseMessage.withMisconductCue(
        state = state,
        team = team,
        thresholdCount = teamCardTotal,
    )
}

/**
 * Build the player-specific message lines for a yellow, red, or second-yellow event.
 *
 * @param playerCardType The player-card event type to describe.
 * @param jerseyNumber The player number, or the unknown-player sentinel.
 */
private fun GameEvent.TeamCardsChanged.playerCardEventLines(
    playerCardType: PlayerCardEventType,
    jerseyNumber: String,
): List<String> {
    return buildList {
        val hasTournamentSuspension = state.playerHasTournamentSuspension(team, jerseyNumber)
        when (playerCardType) {
            PlayerCardEventType.YELLOW -> add("Yellow card on ${playerReference(jerseyNumber)}.")
            PlayerCardEventType.RED -> {
                add("Red card on ${playerReference(jerseyNumber)}.")
                if (!hasTournamentSuspension) {
                    add("${playerSentenceSubject(jerseyNumber)} receives a game suspension.")
                }
            }
            PlayerCardEventType.SECOND_YELLOW -> {
                add("Second yellow on ${playerReference(jerseyNumber)}.")
                if (!hasTournamentSuspension) {
                    add("${playerSentenceSubject(jerseyNumber)} receives a game suspension.")
                }
            }
        }
        if (playerCardType != PlayerCardEventType.YELLOW &&
            state.gameSuspensionStartedInSecondHalf() &&
            !hasTournamentSuspension
        ) {
            add("${playerSentenceSubject(jerseyNumber)} must also sit out the first half of the next game, if there is one.")
        }
        if (hasTournamentSuspension) {
            add("${playerSentenceSubject(jerseyNumber)} is suspended for the rest of the tournament.")
        }
    }
}

/**
 * Format a player reference for use in the middle of a sentence.
 *
 * @param jerseyNumber The player number, or the unknown-player sentinel.
 */
private fun playerReference(jerseyNumber: String): String {
    return if (jerseyNumber == UNKNOWN_PLAYER_NUMBER) "player N/A" else "player $jerseyNumber"
}

/**
 * Format a player reference for use as the subject of a sentence.
 *
 * @param jerseyNumber The player number, or the unknown-player sentinel.
 */
private fun playerSentenceSubject(jerseyNumber: String): String {
    return if (jerseyNumber == UNKNOWN_PLAYER_NUMBER) "The player" else "Player $jerseyNumber"
}

/// Report whether a game suspension started in the second half or later.
private fun LiveGameState.gameSuspensionStartedInSecondHalf(): Boolean {
    return halftimeTaken
}

/**
 * Report whether the player's prior and in-game cards reach tournament suspension thresholds.
 *
 * @param team The player's team.
 * @param jerseyNumber The player number, or the unknown-player sentinel.
 */
private fun LiveGameState.playerHasTournamentSuspension(team: TeamId, jerseyNumber: String): Boolean {
    var priorYellows = 0
    var priorReds = 0
    priorCards.forEach { record ->
        if (record.team == team && record.jerseyNumber == jerseyNumber) {
            priorYellows += record.priorYellows
            priorReds += record.priorReds
        }
    }
    val inGameRecord = playerCards(team).first { it.jerseyNumber == jerseyNumber }
    val totalYellows = priorYellows + inGameRecord.yellows
    val totalReds = priorReds + inGameRecord.reds
    return totalYellows + 2 * totalReds >= 3
}

/// Format a technical-foul event message, including misconduct cue details when needed.
internal fun GameEvent.TechnicalFoulsChanged.formatMessage(): String {
    val baseMessage =
        "${state.teamName(team)} has $technicalFoulTotal technical " +
            "${pluralize(technicalFoulTotal, "foul")}."
    return baseMessage.withMisconductCue(
        state = state,
        team = team,
        thresholdCount = technicalFoulTotal,
    )
}

/**
 * Append a between-points misconduct cue when a threshold event has an immediate no-pull consequence.
 *
 * @param state The live state after the threshold event.
 * @param team The team that reached the threshold.
 * @param thresholdCount The team-card or technical-foul count after the event.
 */
private fun String.withMisconductCue(
    state: LiveGameState,
    team: TeamId,
    thresholdCount: Int,
): String {
    return if (thresholdCount < 3 || state.phase == LivePhase.LIVE_POINT) {
        this
    } else {
        "$this\n\n${state.betweenPointsMisconductCue(team)}"
    }
}

/**
 * Format the between-points misconduct consequence for the penalized team.
 *
 * @param team The team that reached the misconduct threshold.
 */
private fun LiveGameState.betweenPointsMisconductCue(team: TeamId): String {
    val receivingTeam = pullingTeam.flip()
    return if (team == receivingTeam) {
        "Penalty against receiving team. No pull. Disc at negative brick in defending end zone."
    } else {
        "Penalty against pulling team. No pull. Receiving team starts at attacking brick."
    }
}

/// Format the title for a live-point misconduct prompt.
internal fun GamePrompt.LivePointMisconduct.formatTitle(): String = "Misconduct Penalty"

/// Format the prompt body that asks which side committed live-point misconduct.
internal fun GamePrompt.LivePointMisconduct.formatMessage(): String {
    val baseMessage = event.formatMessage()
    return "$baseMessage\n\nWas this against the offense or defense?"
}

/**
 * Format the full live-point misconduct message after the observer chooses offense or defense.
 *
 * @param againstOffense Whether the penalty is against the offense rather than the defense.
 */
fun GamePrompt.LivePointMisconduct.resolutionMessage(againstOffense: Boolean): String {
    val baseMessage = this.event.formatMessage()
    return "$baseMessage\n\n${misconductResolution(againstOffense)}"
}

/**
 * Format the live-point misconduct consequence after offense/defense is chosen.
 *
 * @param againstOffense Whether the penalty is against the offense rather than the defense.
 */
private fun misconductResolution(againstOffense: Boolean): String {
    return if (againstOffense) {
        "Misconduct penalty against offense.\nReverse brick. Defense may instead leave the disc where it stopped.\n\n" +
            "Offense has 30 seconds to set. Then defense has 20 seconds to check the disc in."
    } else {
        "Misconduct penalty against defense.\nBrick nearest attacking end zone. Offense may instead leave it or center it.\n\n" +
            "Offense has 30 seconds to set. Then defense has 20 seconds to check the disc in."
    }
}

/// List cues for between-points misconduct offense-set timing.
internal fun misconductTimingCues(): List<TimingCue> {
    return listOf(
        TimingCue(TimingCueId.MISCONDUCT_OFFENSE_TWENTY, 20),
        TimingCue(TimingCueId.MISCONDUCT_OFFENSE_TEN, 10),
        TimingCue(TimingCueId.MISCONDUCT_COUNTDOWN_FROM_FIVE, 5),
        TimingCue(TimingCueId.MISCONDUCT_OFFENSE_FREEZE_DEFENSE_TWENTY, 0),
    )
}

/// List cues for the defense check-in window after offense sets early.
internal fun misconductDefenseCheckTimingCues(): List<TimingCue> {
    return listOf(
        TimingCue(TimingCueId.MISCONDUCT_DEFENSE_TWENTY, 20),
    )
}

/// Offer a live-point misconduct countdown without starting it before the observer is ready.
fun LiveGameState.withPendingMisconductCountdown(): LiveGameState {
    if (phase != LivePhase.LIVE_POINT) {
        return this
    }
    return copy(
        countdown = null,
        pendingMisconductCountdown = true,
    )
}

/**
 * Start the 30-second offense-set countdown after an in-point misconduct penalty.
 *
 * @param now The epoch millis to use as the countdown start.
 */
fun LiveGameState.startMisconductCountdown(now: Long): LiveGameState {
    if (phase != LivePhase.LIVE_POINT || !pendingMisconductCountdown) {
        return this
    }
    return copy(
        countdown = CountdownState(
            kind = CountdownKind.TIME_OUT,
            label = "Offense set in",
            durationSeconds = 30,
            targetEpoch = now + 30_000L,
        ),
        pendingMisconductCountdown = false,
        lastEvent = "Misconduct countdown started.",
    )
}

/**
 * Report whether the between-points misconduct countdown can switch to defense check-in timing.
 * The early-set action matters only while defense can still get the fixed 100-second deadline.
 *
 * @param now The current epoch millis, used to hide the early-set option once only the normal hand count remains.
 */
fun LiveGameState.canReportMisconductOffenseSet(now: Long): Boolean {
    val countdown = countdown ?: return false
    return phase == LivePhase.BETWEEN_POINTS &&
        countdown.kind == CountdownKind.MISCONDUCT_BETWEEN_POINTS &&
        now < countdown.targetEpoch - 10_000L
}

/**
 * Switch a between-points misconduct countdown to the defense check-in window after offense sets early.
 * The resulting deadline is the later of the fixed 100-second deadline or 20 seconds after offense sets.
 *
 * @param now The epoch millis when offense is reported set, used to compute the later of the rule deadlines.
 */
fun LiveGameState.reportMisconductOffenseSet(now: Long): LiveGameState {
    val countdown = countdown ?: return this
    if (!canReportMisconductOffenseSet(now)) {
        return this
    }
    val targetEpoch = max(
        countdown.targetEpoch + 10_000L,
        now + 20_000L,
    )
    return copy(
        countdown = CountdownState(
            kind = CountdownKind.MISCONDUCT_DEFENSE_CHECK,
            label = "Defense check in",
            durationSeconds = ((targetEpoch - now) / 1000L).toInt(),
            targetEpoch = targetEpoch,
        ),
        lastEvent = "Offense set after misconduct penalty.",
    )
}
