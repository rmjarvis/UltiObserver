package rmjarvis.ultiobserver

import kotlin.math.max

// Manually adjust the cards and technical fouls that have been assigned
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
// Make failures obvious if a caller bypasses the normal player-card adjustment flow.
private fun requirePlayerCardRecordsValid(records: List<InGamePlayerCardRecord>) {
    require(records.all { it.yellows >= 0 && it.directReds >= 0 }) {
        "Player card records cannot have negative card counts."
    }
    require(records.all { it.hasLegalCounts() }) {
        "Player card records must be no cards, one yellow, second yellow, direct red, or one yellow plus direct red."
    }
    require(records.distinctBy { it.jerseyNumber }.size == records.size) {
        "Player card records cannot contain duplicate player entries."
    }
}
// Check whether assigning another card would keep the player's card record legal.
fun canAddPlayerCardAssignment(
    records: List<InGamePlayerCardRecord>,
    jerseyNumber: String,
    cardType: CardType,
): Boolean {
    val existingRecord = records.firstOrNull { it.jerseyNumber == jerseyNumber }
        ?: InGamePlayerCardRecord(jerseyNumber = jerseyNumber)
    val updatedRecord = when (cardType) {
        CardType.YELLOW -> existingRecord.copy(yellows = existingRecord.yellows + 1)
        CardType.RED -> existingRecord.copy(directReds = existingRecord.directReds + 1)
    }
    return updatedRecord.hasLegalCounts()
}
// Turn requested yellow/red totals into the player-card add/remove steps needed to reconcile them.
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
// Return the players who currently have a card of the given type available to remove.
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
// Assign a card to a specific player
fun addPlayerCardAssignment(
    records: List<InGamePlayerCardRecord>,
    jerseyNumber: String,
    cardType: CardType,
): List<InGamePlayerCardRecord> {
    return updatePlayerCardRecord(records, jerseyNumber) { record ->
        when (cardType) {
            CardType.YELLOW -> record.copy(yellows = record.yellows + 1)
            CardType.RED -> record.copy(directReds = record.directReds + 1)
        }
    }
}
// Remove a card from a specific player
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
                CardType.RED -> record.copy(directReds = max(0, record.directReds - 1))
            }
            if (updated.yellows == 0 && updated.directReds == 0) null else updated
        }
    }
}
// Assess a blue card and check whether it triggers misconduct handling.
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
// Assess a technical foul and check whether it triggers misconduct handling.
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
// Assess a yellow card and check whether it triggers misconduct handling.
// It could be one of two things depending on whether it's the first or second yellow.
fun LiveGameState.assessYellowCard(team: TeamId, jerseyNumber: String): CardAssessmentResult {
    val currentRecord = this.playerCardFor(team, jerseyNumber)
    return if (currentRecord?.yellows ?: 0 >= 1) {
        this.assessRedCard(team, jerseyNumber, RedCardMode.SECOND_YELLOW)
    } else {
        this.assessStandaloneYellowCard(team, jerseyNumber)
    }
}
// Figure out the consequence for a standalone yellow.
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
// Assess a red card and check whether it triggers misconduct handling.
fun LiveGameState.assessRedCard(
    team: TeamId,
    jerseyNumber: String,
    mode: RedCardMode,
): CardAssessmentResult {
    var updatedState = when (mode) {
        RedCardMode.DIRECT_RED -> this.addInGameDirectRed(team, jerseyNumber)
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
                RedCardMode.DIRECT_RED -> PlayerCardEventType.RED
                RedCardMode.SECOND_YELLOW -> PlayerCardEventType.SECOND_YELLOW
            },
            playerCardJerseyNumber = jerseyNumber,
        ),
    )
}

private fun LiveGameState.playerCardUndoLabel(action: String, team: TeamId, jerseyNumber: String): String {
    return "Undo $action on #$jerseyNumber of ${this.teamName(team)}"
}

private fun LiveGameState.withSkippedPullForMisconductThreshold(thresholdCount: Int): LiveGameState {
    if (thresholdCount < 3 || this.phase == LivePhase.LIVE_POINT || this.phase == LivePhase.GAME_OVER) {
        return this
    }
    return this.copy(pullSkippedForCurrentPoint = true)
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
// Add a yellow card to a specific player
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
// Add a second yellow card to a specific player
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
// Add a direct red card to a specific player
private fun LiveGameState.addInGameDirectRed(team: TeamId, jerseyNumber: String): LiveGameState {
    return withPlayerCards(
        team = team,
        records = updatePlayerCardRecord(
            records = playerCardsFor(team),
            jerseyNumber = jerseyNumber,
        ) { record ->
            record.copy(directReds = record.directReds + 1)
        },
        lastEvent = "Red card for ${teamName(team)} #$jerseyNumber.",
    )
}
// Handle the details of updating a player's card status in the list of carded players.
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
// Check if a player already has a yellow card yet.
fun LiveGameState.playerHasYellowThisGame(team: TeamId, jerseyNumber: String): Boolean {
    return (this.playerCardFor(team, jerseyNumber)?.yellows ?: 0) > 0
}
// Return the player-card records for one team.
fun LiveGameState.playerCards(team: TeamId): List<InGamePlayerCardRecord> {
    return this.playerCardsFor(team)
}
// Count in-game yellow cards from the player-card records.
fun LiveGameState.teamYellowCards(team: TeamId): Int {
    return this.playerCardsFor(team).sumOf { it.yellows }
}
// Count in-game direct red cards from the player-card records.
fun LiveGameState.teamRedCards(team: TeamId): Int {
    return this.playerCardsFor(team).sumOf { it.directReds }
}
// Count the total number of cards a team has.
fun LiveGameState.teamCardTotal(team: TeamId): Int {
    val currentTeam = if (team == TeamId.TEAM_ONE) this.teamOne else this.teamTwo
    return this.teamYellowCards(team) + currentTeam.blueCards + (2 * this.teamRedCards(team))
}
// Get the card record for a specific player.
private fun LiveGameState.playerCardsFor(team: TeamId): List<InGamePlayerCardRecord> {
    return if (team == TeamId.TEAM_ONE) teamOnePlayerCards else teamTwoPlayerCards
}
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
private fun LiveGameState.playerCardFor(team: TeamId, jerseyNumber: String): InGamePlayerCardRecord? {
    return playerCardsFor(team).firstOrNull { it.jerseyNumber == jerseyNumber }
}
