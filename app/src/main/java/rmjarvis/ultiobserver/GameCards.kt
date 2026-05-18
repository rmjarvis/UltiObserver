package rmjarvis.ultiobserver

import kotlin.math.max

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
): LiveGameState {
    requirePlayerCardRecordsValid(teamOnePlayerCards)
    requirePlayerCardRecordsValid(teamTwoPlayerCards)
    val adjustedTeamOneBlues = teamOneBlues.coerceAtLeast(0)
    val adjustedTeamOneTechnicalFouls = teamOneTechnicalFouls.coerceAtLeast(0)
    val adjustedTeamTwoBlues = teamTwoBlues.coerceAtLeast(0)
    val adjustedTeamTwoTechnicalFouls = teamTwoTechnicalFouls.coerceAtLeast(0)

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
    ).withUndo(this, "Undo Cards / TF Adjustment")
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
fun LiveGameState.assessBlueCard(team: TeamId): CardAssessmentResult {
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
fun LiveGameState.assessTechnicalFoul(team: TeamId): CardAssessmentResult {
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
fun LiveGameState.assessYellowCard(team: TeamId, jerseyNumber: String): CardAssessmentResult {
    val currentRecord = this.playerCardFor(team, jerseyNumber)
    return if (currentRecord?.yellows ?: 0 >= 1) {
        this.assessRedCard(team, jerseyNumber, RedCardMode.SECOND_YELLOW)
    } else {
        this.assessStandaloneYellowCard(team, jerseyNumber)
    }
}
/**
 * Record a first yellow for a player and determine any misconduct consequence.
 *
 * @param team The team receiving the yellow card.
 * @param jerseyNumber The player receiving the card, or `N/A` when the player is unknown.
 */
fun LiveGameState.assessStandaloneYellowCard(team: TeamId, jerseyNumber: String): CardAssessmentResult {
    var updatedState = this.addInGameYellowCard(team, jerseyNumber)
        .withUndo(this, playerCardUndoLabel("Yellow", team, jerseyNumber))
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
 * Record a red-card outcome and determine any misconduct consequence.
 *
 * @param team The team receiving the red-card outcome.
 * @param jerseyNumber The player receiving the card, or `N/A` when the player is unknown.
 * @param mode Whether this red-card outcome came from the Red action or the second-yellow path.
 */
fun LiveGameState.assessRedCard(
    team: TeamId,
    jerseyNumber: String,
    mode: RedCardMode,
): CardAssessmentResult {
    var updatedState = when (mode) {
        RedCardMode.RED -> this.addInGameRedCard(team, jerseyNumber)
            .withUndo(this, playerCardUndoLabel("Red", team, jerseyNumber))
        RedCardMode.SECOND_YELLOW -> this.addInGameSecondYellow(team, jerseyNumber)
            .withUndo(this, playerCardUndoLabel("Second Yellow", team, jerseyNumber))
    }
    val cardTotal = updatedState.teamCardTotal(team)
    updatedState = updatedState.withSkippedPullForMisconductThreshold(cardTotal)
    return CardAssessmentResult(
        state = updatedState,
        event = GameEvent.TeamCardsChanged(
            state = updatedState,
            team = team,
            teamCardTotal = cardTotal,
            playerCardType = when (mode) {
                RedCardMode.RED -> PlayerCardEventType.RED
                RedCardMode.SECOND_YELLOW -> PlayerCardEventType.SECOND_YELLOW
            },
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
        countdown = this.countdown?.toBetweenPointsMisconductCountdown(),
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
enum class PlayerCardAdjustmentMode {
    ADD,
    REMOVE,
}
data class PlayerCardAdjustmentStep(
    val team: TeamId,
    val cardType: CardType,
    val mode: PlayerCardAdjustmentMode,
)
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
    return this.teamYellowCards(team) + currentTeam.blueCards + (2 * this.teamRedCards(team))
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
