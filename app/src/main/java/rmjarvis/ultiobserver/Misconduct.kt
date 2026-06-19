package rmjarvis.ultiobserver

import kotlinx.serialization.Serializable
import kotlin.math.max

/**
 * Normalized identity for a player based on the jersey number and name.
 *
 * Either the number or the name may be missing, but not both.
 *
 * @param jerseyNumber The player's jersey number, or blank for a name-only identity.
 * @param playerName The player's name, or blank when unknown.
 */
@Serializable
class PlayerIdentity private constructor(
    val jerseyNumber: String,
    val playerName: String = "",
) {
    init {
        require(jerseyNumber.isNotBlank() || playerName.isNotBlank()) {
            "A player identity requires a jersey number or player name."
        }
    }

    /**
     * Return the display text for this player identity.
     *
     * @param compact Whether to omit the name when a jersey number is available.
     */
    internal fun displayText(compact: Boolean): String {
        val number = jerseyNumber.trim()
        val name = playerName.trim()
        return if (number.isNotEmpty()) {
            if (!compact && name.isNotEmpty()) "#$number $name" else "#$number"
        } else {
            name
        }
    }

    /// Return the name used for player identity comparisons.
    internal fun normalizedPlayerName(): String {
        return playerName.trim()
            .split(Regex("\\s+"))
            .filter { it.isNotEmpty() }
            .joinToString(" ")
            .lowercase()
    }

    /// Return a unique key for exact player identities.
    internal fun key(): Pair<String, String> {
        val name = normalizedPlayerName()
        val number = jerseyNumber.trim()
        return number to name
    }

    /**
     * Report whether this identity matches another using player-card matching rules.
     *
     * @param other The identity to compare with this one.
     */
    internal fun matches(other: PlayerIdentity): Boolean {
        val existingNumber = key().first
        val existingName = key().second
        val proposedNumber = other.key().first
        val proposedName = other.key().second
        if (proposedNumber.isNotEmpty() && existingNumber == proposedNumber) {
            return proposedName.isEmpty() ||
                existingName.isEmpty() ||
                proposedName == existingName
        }
        return proposedName.isNotEmpty() &&
            existingName == proposedName &&
            proposedNumber.isEmpty() &&
            existingNumber.isEmpty()
    }

    /**
     * Report whether this identity overlaps another without matching it.
     *
     * This is more permissive than `matches`: it includes similar identities that
     * should not usually be treated as the same player, such as players with the
     * same number but different names. Use it when the app should warn the user
     * about a similar player they might have meant, then ask for confirmation
     * before adding a separate record.
     *
     * @param other The identity to compare with this one.
     */
    internal fun hasOverlapWith(other: PlayerIdentity): Boolean {
        if (key() == other.key()) {
            return false
        }
        val existingNumber = key().first
        val proposedNumber = other.key().first
        val existingName = key().second
        val proposedName = other.key().second
        return (proposedNumber.isNotEmpty() && proposedNumber == existingNumber) ||
            (proposedName.isNotEmpty() && proposedName == existingName &&
                (proposedNumber.isEmpty() || existingNumber.isEmpty()))
    }

    /**
     * Return this identity with blank fields filled from another identity.
     *
     * @param fallback Identity that supplies a number or name when this identity is missing it.
     */
    internal fun withMissingFieldsFrom(fallback: PlayerIdentity): PlayerIdentity {
        return PlayerIdentity(
            jerseyNumber = jerseyNumber.ifBlank { fallback.jerseyNumber },
            playerName = playerName.ifBlank { fallback.playerName },
        )
    }

    override fun equals(other: Any?): Boolean {
        return other is PlayerIdentity && key() == other.key()
    }

    override fun hashCode(): Int {
        return key().hashCode()
    }

    override fun toString(): String {
        return "PlayerIdentity(jerseyNumber=$jerseyNumber, playerName=$playerName)"
    }

    companion object {
        /**
         * Build a normalized player identity.
         *
         * Constructor-style calls like `PlayerIdentity("23", "Name")` resolve to this
         * companion `invoke` operator because the primary constructor is private.
         * That lets callers use constructor syntax while still normalizing inputs
         * before the actual stored fields are assigned.
         *
         * @param jerseyNumber The entered or stored player number, or blank for a name-only identity.
         * @param playerName The entered or stored player name, or blank when unknown.
         */
        operator fun invoke(jerseyNumber: String, playerName: String = ""): PlayerIdentity {
            return PlayerIdentity(jerseyNumber = jerseyNumber.trim(), playerName = playerName.trim())
        }

    }
}

/**
 * Player record that includes everything we might know about a player during the game,
 * including their identity, possible cards from previous games in the tournament, and
 * any cards given out during the current game.
 *
 * @param jerseyNumber The player's jersey number, or blank when unknown.
 * @param playerName The player's name, or blank when unknown.
 * @param priorYellows Yellow cards issued in previous games of the current tournament.
 * @param priorReds Red cards issued in previous games of the current tournament.
 * @param cards Card events assessed to this player during this game.
 */
@Serializable
data class PlayerRecord(
    val jerseyNumber: String,
    val playerName: String = "",
    val priorYellows: Int = 0,
    val priorReds: Int = 0,
    val cards: List<InGamePlayerCardEvent> = emptyList(),
) {
    init {
        require(jerseyNumber.isNotBlank() || playerName.isNotBlank()) {
            "A player record requires a jersey number or player name."
        }
        require(priorYellows >= 0 && priorReds >= 0) {
            "Prior card counts cannot be negative."
        }
    }

    /**
     * Return the display identity for this player.
     *
     * @param compact Whether to omit the name when a jersey number is available.
     */
    internal fun playerIdentity(compact: Boolean): String {
        return identity().displayText(compact)
    }

    /**
     * Return compact card detail for this player.
     *
     * @param includeGame Whether to include in-game card counts with previous-game card counts.
     */
    internal fun cardDetail(includeGame: Boolean = false): String {
        val priorDetail = listOfNotNull(
            if (priorYellows > 0) "Y $priorYellows" else null,
            if (priorReds > 0) "R $priorReds" else null,
        ).joinToString("  ")
        if (includeGame) {
            val labeledPriorDetail = priorDetail.takeIf { it.isNotBlank() }?.let { "prior $it" }
            val inGameDetail = listOfNotNull(
                if (yellows > 0) "Y $yellows" else null,
                if (reds > 0) "R $reds" else null,
            ).joinToString("  ")
            return listOfNotNull(
                labeledPriorDetail,
                inGameDetail.takeIf { it.isNotBlank() },
            ).joinToString(" + ")
        }
        return priorDetail.ifBlank { "No prior cards" }
    }

    /// Return a prose setup notice summary for this player's prior yellows and reds.
    internal fun playerCardNoticeDetail(): String {
        val priorDetail = listOfNotNull(
            if (priorYellows > 0) countedNounPhrase(priorYellows, "yellow card") else null,
            if (priorReds > 0) countedNounPhrase(priorReds, "red card") else null,
        ).joinToString(" and ")
        return priorDetail.ifBlank { "no prior cards" }
    }

    /// Return the number of in-game yellow cards recorded for this player.
    internal val yellows: Int
        get() = cardCount(CardType.YELLOW)

    /// Return the number of in-game red cards recorded for this player.
    internal val reds: Int
        get() = cardCount(CardType.RED)

    /// Return the total card points from prior and in-game cards.
    internal val totalCardPoints: Int
        get() = priorYellows + yellows + (2 * (priorReds + reds))

    /**
     * Count in-game cards of one type recorded for this player.
     *
     * @param cardType The card type to count.
     */
    internal fun cardCount(cardType: CardType): Int {
        return cards.count { it.cardType == cardType }
    }

    /// Report whether this player's in-game card combination is legal.
    internal fun hasLegalCounts(): Boolean {
        return (yellows == 0 && reds == 0) ||
            (yellows == 1 && reds == 0) ||
            (yellows == 2 && reds == 0) ||
            (yellows == 0 && reds == 1) ||
            (yellows == 1 && reds == 1)
    }

    /// Return this player's number/name identity without card state.
    internal fun identity(): PlayerIdentity {
        return PlayerIdentity(jerseyNumber, playerName)
    }

    /**
     * Return this record with missing identity fields filled from another identity.
     *
     * @param other Identity that supplies a number or name when this record is missing it.
     */
    internal fun withMergedIdentityFrom(other: PlayerIdentity): PlayerRecord {
        val mergedIdentity = identity().withMissingFieldsFrom(other)
        return copy(
            jerseyNumber = mergedIdentity.jerseyNumber,
            playerName = mergedIdentity.playerName,
        )
    }
}

/// Result of comparing an entered card holder with existing records.
sealed class CardHolderEntryCheck {
    /**
     * Existing card holder that should block adding a separate record.
     *
     * @param existingIndex Index of the matching existing record in the full card-holder list.
     */
    data class ExistingCardHolder(
        val existingIndex: Int,
    ) : CardHolderEntryCheck()

    /**
     * Existing card holders that partially match the entered identity.
     *
     * @param existingIndices Indices of possible matching records in the full card-holder list.
     */
    data class PossibleDifferentPlayer(
        val existingIndices: List<Int>,
    ) : CardHolderEntryCheck()
}

/**
 * Return the duplicate/conflict result for an entered card holder.
 *
 * @param proposed The card holder the observer is trying to save.
 * @param editingIndex Existing record index to ignore when validating an edit, or null when adding.
 */
internal fun List<PlayerRecord>.cardHolderEntryCheck(
    proposed: PlayerRecord,
    editingIndex: Int?,
): CardHolderEntryCheck? {
    if (editingIndex != null) {
        return null
    }
    val possibleMatches = mutableListOf<Int>()
    val proposedIdentity = proposed.identity()
    forEachIndexed { index, existing ->
        val existingIdentity = existing.identity()
        if (existingIdentity.matches(proposedIdentity)) {
            return CardHolderEntryCheck.ExistingCardHolder(
                existingIndex = index,
            )
        }
        if (existingIdentity.hasOverlapWith(proposedIdentity)) {
            possibleMatches.add(index)
        }
    }
    if (possibleMatches.isNotEmpty()) {
        return CardHolderEntryCheck.PossibleDifferentPlayer(possibleMatches)
    }
    return null
}

/**
 * Return prior-card records after adding or editing one card holder.
 *
 * @param record Prior-card record to save.
 * @param editingIndex Existing record index to replace, or null when adding.
 */
internal fun List<PlayerRecord>.withSavedPriorCardRecord(
    record: PlayerRecord,
    editingIndex: Int?,
): List<PlayerRecord> {
    return if (editingIndex == null) {
        this + record
    } else {
        mapIndexed { index, existing ->
            if (index == editingIndex) record else existing
        }
    }
}

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
 * Return preset card reasons for the chosen card color.
 *
 * @param cardType The card type being assessed.
 */
internal fun cardReasonPresets(cardType: CardType): List<String> {
    return when (cardType) {
        CardType.YELLOW -> listOf(
            "Dangerous play",
            "Pushing",
            "Egregious foul",
            "Deliberate infraction",
            "Repeated fouling",
            "Knowingly invalid calls",
            "Taunting",
            "Verbal abuse",
        )
        CardType.RED -> listOf(
            "Battery/fighting",
            "Egregious dangerous play",
            "Physical aggression",
            "Egregious verbal abuse",
        )
    }
}

/**
 * Structured reason entered for one yellow or red card.
 *
 * @param preset Selected preset label, `Other`, or blank for no preset selection.
 * @param otherText Custom reason text when `Other` is selected.
 * @param details Additional context text entered by the observer.
 */
@Serializable
data class CardReason(
    val preset: String = "",
    val otherText: String = "",
    val details: String = "",
) {
    /// Return printable reason text for summaries and card lists.
    internal fun text(): String {
        val base = if (preset == "Other") otherText.trim() else preset
        val detail = details.trim()
        return when {
            base.isNotEmpty() && detail.isNotEmpty() -> "$base: $detail"
            base.isNotEmpty() -> base
            else -> detail
        }
    }
}

/**
 * Persisted in-game yellow/red card event.
 *
 * @param cardType The card assessed to the player.
 * @param index Assessment-order index for this in-game player card.
 * @param reason Optional reason recorded for this individual card.
 */
@Serializable
data class InGamePlayerCardEvent(
    val cardType: CardType,
    val index: Int,
    val reason: CardReason = CardReason(),
)

/**
 * Same-number player identity conflict found while entering a live player card.
 *
 * @param existingJerseyNumber The stored jersey number for the known player.
 * @param existingPlayerName The stored name for the known player.
 * @param proposedJerseyNumber The entered jersey number for the new card.
 * @param proposedPlayerName The entered name for the new card.
 */
data class SameNumberPlayerIdentityConflict(
    val existingJerseyNumber: String,
    val existingPlayerName: String,
    val proposedJerseyNumber: String,
    val proposedPlayerName: String,
)

/**
 * Return a same-number, different-name conflict for a live player-card entry.
 *
 * @param team The team receiving the entered card.
 * @param jerseyNumber The entered player number, or blank for name-only.
 * @param playerName The entered player name, or blank when unknown.
 */
fun GameState.sameNumberPlayerIdentityConflict(
    team: TeamId,
    jerseyNumber: String,
    playerName: String,
): SameNumberPlayerIdentityConflict? {
    return playerCards(team).sameNumberPlayerIdentityConflict(jerseyNumber, playerName)
}

/**
 * Return a same-number, different-name conflict for a player-card entry.
 *
 * @param jerseyNumber The entered player number, or blank for name-only.
 * @param playerName The entered player name, or blank when unknown.
 */
fun List<PlayerRecord>.sameNumberPlayerIdentityConflict(
    jerseyNumber: String,
    playerName: String,
): SameNumberPlayerIdentityConflict? {
    val proposedIdentity = PlayerIdentity(jerseyNumber, playerName)
    val proposedName = proposedIdentity.normalizedPlayerName()
    if (proposedName.isEmpty()) {
        return null
    }
    val existingPlayer = firstOrNull { player ->
        val existingKey = player.identity().key()
        existingKey.first == proposedIdentity.jerseyNumber &&
            existingKey.second.let { it.isNotEmpty() && it != proposedName }
    } ?: return null
    return SameNumberPlayerIdentityConflict(
        existingJerseyNumber = existingPlayer.jerseyNumber,
        existingPlayerName = existingPlayer.playerName,
        proposedJerseyNumber = proposedIdentity.jerseyNumber,
        proposedPlayerName = proposedIdentity.playerName,
    )
}

/**
 * State and popup needs from assessing a card or technical foul.
 *
 * @param state The live state after the assessment.
 * @param event The observer-facing event to show.
 * @param needsMisconductChoice Whether the UI must ask offense/defense before resolving live-point misconduct.
 */
data class CardAssessmentResult(
    val state: GameState,
    val event: GameEvent,
    val needsMisconductChoice: Boolean =
        event.needsMisconductChoice(),
)

/**
 * State and popup needs from previewing a blue card before recording it.
 *
 * @param event The event describing what would be recorded.
 */
data class BlueCardAssessmentPreview(
    val event: GameEvent.TeamCardsChanged,
)

/**
 * State and popup needs from previewing a technical foul before recording it.
 *
 * @param event The event describing what would be recorded.
 */
data class TechnicalFoulAssessmentPreview(
    val event: GameEvent.TechnicalFoulsChanged,
)

/// Player-card event type used when formatting card popups.
enum class PlayerCardEventType {
    YELLOW,
    RED,
    SECOND_YELLOW,
}

/**
 * Replace team blue-card and technical-foul counts as one manual correction.
 *
 * @param teamOneBlues The corrected blue-card count for team one.
 * @param teamOneTechnicalFouls The corrected technical-foul count for team one.
 * @param teamTwoBlues The corrected blue-card count for team two.
 * @param teamTwoTechnicalFouls The corrected technical-foul count for team two.
 * @param now The correction timestamp.
 */
fun GameState.adjustBlueCardsAndTechs(
    teamOneBlues: Int,
    teamOneTechnicalFouls: Int,
    teamTwoBlues: Int,
    teamTwoTechnicalFouls: Int,
    now: Long,
): GameState {
    val adjustedTeamOneBlues = teamOneBlues.coerceAtLeast(0)
    val adjustedTeamOneTechnicalFouls = teamOneTechnicalFouls.coerceAtLeast(0)
    val adjustedTeamTwoBlues = teamTwoBlues.coerceAtLeast(0)
    val adjustedTeamTwoTechnicalFouls = teamTwoTechnicalFouls.coerceAtLeast(0)
    if (
        adjustedTeamOneBlues == teamOne.blueCards &&
        adjustedTeamOneTechnicalFouls == teamOne.technicalFouls &&
        adjustedTeamTwoBlues == teamTwo.blueCards &&
        adjustedTeamTwoTechnicalFouls == teamTwo.technicalFouls
    ) {
        return this
    }

    return copy(
        teamOne = teamOne.copy(
            blueCards = adjustedTeamOneBlues,
            technicalFouls = adjustedTeamOneTechnicalFouls,
        ),
        teamTwo = teamTwo.copy(
            blueCards = adjustedTeamTwoBlues,
            technicalFouls = adjustedTeamTwoTechnicalFouls,
        ),
        lastEvent = "Adjust blue card/tech counts.",
    ).withEventLogEntry(
        EventLogEntry(
            timestampEpoch = now,
            type = EventLogType.BLUE_CARD_AND_TECH_ADJUSTED,
        )
    ).withUndo(this, "Undo Adjust blue card/tech counts")
}

/**
 * Replace team card and technical-foul counts as a manual correction.
 *
 * @param teamOneBlues The corrected blue-card count for team one.
 * @param teamOneTechnicalFouls The corrected technical-foul count for team one.
 * @param teamTwoBlues The corrected blue-card count for team two.
 * @param teamTwoTechnicalFouls The corrected technical-foul count for team two.
 * @param teamOnePlayers The reconciled per-player yellow/red records for team one.
 * @param teamTwoPlayers The reconciled per-player yellow/red records for team two.
 * @param undoLabel Label to show for undoing this correction.
 */
fun GameState.adjustCardsAndTf(
    teamOneBlues: Int,
    teamOneTechnicalFouls: Int,
    teamTwoBlues: Int,
    teamTwoTechnicalFouls: Int,
    teamOnePlayers: List<PlayerRecord>,
    teamTwoPlayers: List<PlayerRecord>,
    now: Long,
    undoLabel: String,
): GameState {
    requirePlayerRecordsValid(teamOnePlayers)
    requirePlayerRecordsValid(teamTwoPlayers)
    val adjustedTeamOneBlues = teamOneBlues.coerceAtLeast(0)
    val adjustedTeamOneTechnicalFouls = teamOneTechnicalFouls.coerceAtLeast(0)
    val adjustedTeamTwoBlues = teamTwoBlues.coerceAtLeast(0)
    val adjustedTeamTwoTechnicalFouls = teamTwoTechnicalFouls.coerceAtLeast(0)
    val entries = buildCardAndTfAdjustmentEntries(
        teamOneBlues = adjustedTeamOneBlues,
        teamOneTechnicalFouls = adjustedTeamOneTechnicalFouls,
        teamTwoBlues = adjustedTeamTwoBlues,
        teamTwoTechnicalFouls = adjustedTeamTwoTechnicalFouls,
        teamOnePlayers = teamOnePlayers,
        teamTwoPlayers = teamTwoPlayers,
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
        teamOnePlayers = teamOnePlayers,
        teamTwoPlayers = teamTwoPlayers,
        lastEvent = "Cards and technical fouls adjusted.",
    ).withEventLogEntries(entries).withUndo(this, undoLabel)
}

/**
 * Build event-log entries that describe each card and technical-foul correction delta.
 *
 * @param teamOneBlues The corrected blue-card count for team one.
 * @param teamOneTechnicalFouls The corrected technical-foul count for team one.
 * @param teamTwoBlues The corrected blue-card count for team two.
 * @param teamTwoTechnicalFouls The corrected technical-foul count for team two.
 * @param teamOnePlayers The corrected player records for team one.
 * @param teamTwoPlayers The corrected player records for team two.
 * @param now The correction timestamp.
 */
private fun GameState.buildCardAndTfAdjustmentEntries(
    teamOneBlues: Int,
    teamOneTechnicalFouls: Int,
    teamTwoBlues: Int,
    teamTwoTechnicalFouls: Int,
    teamOnePlayers: List<PlayerRecord>,
    teamTwoPlayers: List<PlayerRecord>,
    now: Long,
): List<EventLogEntry> {
    return buildList {
        addCardCountDelta(now, TeamId.TEAM_ONE, EventLogType.BLUE_CARD, teamOneBlues - teamOne.blueCards)
        addTechnicalFoulDelta(now, TeamId.TEAM_ONE, teamOneTechnicalFouls - teamOne.technicalFouls)
        addPlayerCardDeltas(now, TeamId.TEAM_ONE, this@buildCardAndTfAdjustmentEntries.teamOnePlayers, teamOnePlayers)
        addCardCountDelta(now, TeamId.TEAM_TWO, EventLogType.BLUE_CARD, teamTwoBlues - teamTwo.blueCards)
        addTechnicalFoulDelta(now, TeamId.TEAM_TWO, teamTwoTechnicalFouls - teamTwo.technicalFouls)
        addPlayerCardDeltas(now, TeamId.TEAM_TWO, this@buildCardAndTfAdjustmentEntries.teamTwoPlayers, teamTwoPlayers)
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
    beforeRecords: List<PlayerRecord>,
    afterRecords: List<PlayerRecord>,
) {
    val cardChange = getSingleChangedPlayerCard(beforeRecords, afterRecords)
    if (cardChange != null) {
        val newIdentity = cardChange.after.identity()
        if (!newIdentity.matches(cardChange.before.identity())) {
            add(
                EventLogEntry(
                    timestampEpoch = now,
                    type = cardChange.after.cardType.eventLogType(),
                    team = team,
                    player = newIdentity,
                    previousPlayer = cardChange.before.identity(),
                )
            )
        }
        return
    }
    val identities = (beforeRecords + afterRecords).distinctBy { it.identity().key() }
    identities.forEach { identity ->
        val identityKey = identity.identity().key()
        val before = beforeRecords.firstOrNull { it.identity().key() == identityKey }
        val after = afterRecords.firstOrNull { it.identity().key() == identityKey }
        val eventPlayer = (before ?: after ?: identity).identity()
        addCardCountDelta(
            now = now,
            team = team,
            type = EventLogType.YELLOW_CARD,
            delta = (after?.yellows ?: 0) - (before?.yellows ?: 0),
            player = eventPlayer,
        )
        addCardCountDelta(
            now = now,
            team = team,
            type = EventLogType.RED_CARD,
            delta = (after?.reds ?: 0) - (before?.reds ?: 0),
            player = eventPlayer,
        )
    }
}

/**
 * One changed editable player-card entry from a before/after correction.
 *
 * @param before The card before correction.
 * @param after The card after correction.
 */
private data class PlayerCardChange(
    val before: EditablePlayerCard,
    val after: EditablePlayerCard,
)

/**
 * Return a single changed player-card pair, or null if there are none.
 *
 * @param beforeRecords The records before correction.
 * @param afterRecords The records after correction.
 */
private fun getSingleChangedPlayerCard(
    beforeRecords: List<PlayerRecord>,
    afterRecords: List<PlayerRecord>,
): PlayerCardChange? {
    val beforeCards = beforeRecords.editablePlayerCards()
    val afterCards = afterRecords.editablePlayerCards()
    if (beforeCards.size != afterCards.size) {
        return null
    }
    val changedCards = beforeCards.zip(afterCards).filter { (before, after) ->
        before.jerseyNumber != after.jerseyNumber ||
            before.playerName != after.playerName ||
            before.cardType != after.cardType ||
            before.reason != after.reason
    }
    if (changedCards.size != 1) {
        // Then it's 0.  It's not possible for this to be >1 in production pathways.
        return null
    }
    val (before, after) = changedCards.single()
    return PlayerCardChange(before, after)
}

/// Return the event-log type for this player-card color.
private fun CardType.eventLogType(): EventLogType {
    return when (this) {
        CardType.YELLOW -> EventLogType.YELLOW_CARD
        CardType.RED -> EventLogType.RED_CARD
    }
}

/**
 * Add one or more card-count correction entries.
 *
 * @param now The correction timestamp.
 * @param team The team whose card count changed.
 * @param type The card event type whose count changed.
 * @param delta The signed count change.
 * @param player The player identity for player-card corrections.
 */
private fun MutableList<EventLogEntry>.addCardCountDelta(
    now: Long,
    team: TeamId,
    type: EventLogType,
    delta: Int,
    player: PlayerIdentity? = null,
) {
    if (delta != 0) {
        repeat(kotlin.math.abs(delta)) {
            add(
                EventLogEntry(
                    timestampEpoch = now,
                    type = type,
                    team = team,
                    player = player,
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
 * Reject impossible player records before they enter live state.
 * This makes failures obvious if a caller bypasses the normal player-card adjustment flow.
 *
 * @param records The player records to validate.
 */
private fun requirePlayerRecordsValid(records: List<PlayerRecord>) {
    require(records.all { it.yellows >= 0 && it.reds >= 0 }) {
        "Player records cannot have negative card counts."
    }
    require(records.all { it.hasLegalCounts() }) {
        "Player records must be no cards, one yellow, second yellow, red, or one yellow plus red."
    }
    require(records.distinctBy { it.identity().key() }.size == records.size) {
        "Player records cannot contain duplicate player entries."
    }
}

/**
 * Reason a new player card cannot be added to an existing player record.
 *
 * @param messageText Text explaining the rejection after the team and player identity.
 * @param noticeText Text warning about the resulting suspension after the team and player identity.
 */
enum class PlayerCardAssignmentRejection(val messageText: String, val noticeText: String) {
    TWO_YELLOWS(
        messageText = "already has two yellow cards and has been suspended.",
        noticeText = "now has two yellow cards and has been suspended.",
    ),
    RED_CARD(
        messageText = "already has a red card and has been suspended.",
        noticeText = "now has a red card and has been suspended.",
    ),
    THREE_TOURNAMENT_YELLOWS(
        messageText = "already has three yellow cards in the tournament and has been suspended.",
        noticeText = "now has three yellow cards in the tournament and has been suspended.",
    ),
}

/**
 * Return why a player cannot receive another card, or null if the assignment can proceed.
 *
 * @param records The current player records for that team.
 * @param identity The player receiving the possible card.
 */
fun playerCardAssignmentRejection(
    records: List<PlayerRecord>,
    identity: PlayerIdentity,
): PlayerCardAssignmentRejection? {
    val existingRecord = records.firstOrNull { it.identity().matches(identity) } ?: return null
    return when {
        existingRecord.reds > 0 ->
            PlayerCardAssignmentRejection.RED_CARD
        existingRecord.yellows >= 2 ->
            PlayerCardAssignmentRejection.TWO_YELLOWS
        existingRecord.priorYellows + existingRecord.yellows + (2 * existingRecord.priorReds) >= 3 ->
            PlayerCardAssignmentRejection.THREE_TOURNAMENT_YELLOWS
        else -> null
    }
}

/// Return editable in-game yellow/red card rows for one team's player records.
fun List<PlayerRecord>.editablePlayerCards(): List<EditablePlayerCard> {
    return flatMapIndexed { playerIndex, player ->
        player.cards.mapIndexed { cardIndex, card ->
            EditablePlayerCard(
                playerIndex = playerIndex,
                cardIndex = cardIndex,
                index = card.index,
                jerseyNumber = player.jerseyNumber,
                playerName = player.playerName,
                cardType = card.cardType,
                reason = card.reason,
            )
        }
    }.sortedBy { it.index }
}

/**
 * Add a yellow or red card assignment to a specific player record.
 *
 * @param records The current player records for one team.
 * @param jerseyNumber The player receiving the card.
 * @param cardType The card type to add.
 */
fun addPlayerCardAssignment(
    records: List<PlayerRecord>,
    jerseyNumber: String,
    cardType: CardType,
    index: Int,
    playerName: String = "",
    reason: CardReason = CardReason(),
): List<PlayerRecord> {
    return updatePlayerCardRecord(records, jerseyNumber, playerName) { record ->
        record.withAddedCard(cardType, index, reason)
    }
}

/**
 * Replace one in-game player-card event, merging into an existing player when
 * identities match and preserving the card's assessment index.
 *
 * @param records The current player records for one team.
 * @param editableCard The existing card event to replace.
 * @param jerseyNumber The corrected player number.
 * @param cardType The corrected card type.
 * @param playerName The corrected player name.
 * @param reason The corrected reason.
 */
fun replaceEditablePlayerCard(
    records: List<PlayerRecord>,
    editableCard: EditablePlayerCard,
    jerseyNumber: String,
    cardType: CardType,
    playerName: String,
    reason: CardReason,
): List<PlayerRecord> {
    val originalCard = records[editableCard.playerIndex].cards[editableCard.cardIndex]
    return addPlayerCardAssignment(
        records = removeEditablePlayerCard(records, editableCard),
        jerseyNumber = jerseyNumber,
        cardType = cardType,
        index = originalCard.index,
        playerName = playerName,
        reason = reason,
    )
}

/**
 * Remove one in-game player-card event.
 *
 * @param records The current player records for one team.
 * @param editableCard The card event to remove.
 */
fun removeEditablePlayerCard(
    records: List<PlayerRecord>,
    editableCard: EditablePlayerCard,
): List<PlayerRecord> {
    val updatedRecords = records.mapIndexedNotNull { playerIndex, record ->
        if (playerIndex != editableCard.playerIndex) {
            record
        } else {
            val updatedCards = record.cards.filterIndexed { cardIndex, _ -> cardIndex != editableCard.cardIndex }
            if (updatedCards.isEmpty() && record.priorYellows == 0 && record.priorReds == 0) {
                null
            } else {
                record.copy(cards = updatedCards)
            }
        }
    }
    requirePlayerRecordsValid(updatedRecords)
    return updatedRecords
}

/**
 * Return this record with one card event added.
 *
 * @param cardType The card type assessed.
 * @param index Assessment-order index for this card.
 * @param reason Optional reason recorded for the card.
 */
private fun PlayerRecord.withAddedCard(
    cardType: CardType,
    index: Int,
    reason: CardReason = CardReason(),
): PlayerRecord {
    return copy(cards = cards + InGamePlayerCardEvent(cardType = cardType, index = index, reason = reason))
}

/// Return the next in-game player-card assessment index.
fun GameState.getNextAssessmentIndex(): Int {
    return (teamOnePlayers + teamTwoPlayers)
        .flatMap { it.cards }
        .fold(0) { nextIndex, card -> maxOf(nextIndex, card.index + 1) }
}

/**
 * Record a blue card and determine whether it triggers misconduct handling.
 *
 * @param team The team receiving the blue card.
 */
fun GameState.assessBlueCard(team: TeamId, now: Long): CardAssessmentResult {
    var updatedState = this.withAddedBlueCard(team).copy(
        lastEvent = "Blue card assessed to ${this.teamName(team)}.",
    ).withEventLogEntry(
        EventLogEntry(
            timestampEpoch = now,
            type = EventLogType.BLUE_CARD,
            team = team,
        )
    ).withUndo(this, "Undo Blue card on ${this.teamName(team)}")
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
 * Preview a blue card and its misconduct consequence without changing game state.
 *
 * @param team The team receiving the blue card.
 */
fun GameState.previewBlueCard(team: TeamId): BlueCardAssessmentPreview {
    var previewState = this.withAddedBlueCard(team)
    val cardTotal = previewState.teamCardTotal(team)
    previewState = previewState.withSkippedPullForMisconductThreshold(cardTotal)
    return BlueCardAssessmentPreview(
        event = GameEvent.TeamCardsChanged(
            state = previewState,
            team = team,
            teamCardTotal = cardTotal,
        ),
    )
}

/**
 * Return game state with one added blue card for the given team.
 *
 * @param team The team receiving the blue card.
 */
private fun GameState.withAddedBlueCard(team: TeamId): GameState {
    return copy(
        teamOne = if (team == TeamId.TEAM_ONE) {
            teamOne.copy(blueCards = teamOne.blueCards + 1)
        } else {
            teamOne
        },
        teamTwo = if (team == TeamId.TEAM_TWO) {
            teamTwo.copy(blueCards = teamTwo.blueCards + 1)
        } else {
            teamTwo
        },
    )
}
/**
 * Record a technical foul and determine whether it triggers misconduct handling.
 *
 * @param team The team receiving the technical foul.
 */
fun GameState.assessTechnicalFoul(team: TeamId, now: Long): CardAssessmentResult {
    var updatedState = this.withAddedTechnicalFoul(team).copy(
        lastEvent = "Technical foul on ${this.teamName(team)}.",
    ).withEventLogEntry(
        EventLogEntry(
            timestampEpoch = now,
            type = EventLogType.TECHNICAL_FOUL,
            team = team,
        )
    ).withUndo(this, "Undo Technical foul on ${this.teamName(team)}")
    val technicalFouls = updatedState.technicalFoulsFor(team)
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
 * Preview a technical foul and its misconduct consequence without changing game state.
 *
 * @param team The team receiving the technical foul.
 */
fun GameState.previewTechnicalFoul(team: TeamId): TechnicalFoulAssessmentPreview {
    var previewState = this.withAddedTechnicalFoul(team)
    val technicalFouls = previewState.technicalFoulsFor(team)
    previewState = previewState.withSkippedPullForMisconductThreshold(technicalFouls)
    return TechnicalFoulAssessmentPreview(
        event = GameEvent.TechnicalFoulsChanged(
            state = previewState,
            team = team,
            technicalFoulTotal = technicalFouls,
        ),
    )
}

/**
 * Return game state with one added technical foul for the given team.
 *
 * @param team The team receiving the technical foul.
 */
private fun GameState.withAddedTechnicalFoul(team: TeamId): GameState {
    return copy(
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
    )
}

/**
 * Return the technical-foul count for the given team.
 *
 * @param team The team whose technical fouls should be counted.
 */
private fun GameState.technicalFoulsFor(team: TeamId): Int {
    return if (team == TeamId.TEAM_ONE) {
        teamOne.technicalFouls
    } else {
        teamTwo.technicalFouls
    }
}

/**
 * Record a yellow-card action, promoting it to second yellow when the player already has one.
 * The same observer action can mean either a first yellow or a second yellow depending on the player record.
 *
 * @param team The team receiving the yellow-card action.
 * @param jerseyNumber The player receiving the card.
 */
fun GameState.assessYellowCard(
    team: TeamId,
    jerseyNumber: String,
    now: Long,
    playerName: String = "",
    reason: CardReason = CardReason(),
): CardAssessmentResult {
    val identity = playerIdentityForAssessment(team, jerseyNumber, playerName)
    val currentRecord = this.playerCardFor(team, identity.jerseyNumber, identity.playerName)
    return if (currentRecord?.yellows ?: 0 >= 1) {
        this.assessSecondYellowCard(team, identity.jerseyNumber, now, identity.playerName, reason)
    } else {
        this.assessFirstYellowCard(team, identity.jerseyNumber, now, identity.playerName, reason)
    }
}
/**
 * Record a first yellow for a player and determine any misconduct consequence.
 *
 * @param team The team receiving the yellow card.
 * @param jerseyNumber The player receiving the card.
 */
fun GameState.assessFirstYellowCard(
    team: TeamId,
    jerseyNumber: String,
    now: Long,
    playerName: String = "",
    reason: CardReason = CardReason(),
): CardAssessmentResult {
    val identity = playerIdentityForAssessment(team, jerseyNumber, playerName)
    var updatedState = this.addInGameYellowCard(team, identity.jerseyNumber, identity.playerName, reason)
        .withEventLogEntry(
            EventLogEntry(
                timestampEpoch = now,
                type = EventLogType.YELLOW_CARD,
                team = team,
                player = identity,
            )
        ).withUndo(this, playerCardUndoLabel("Yellow", team, identity.jerseyNumber, identity.playerName))
    val cardTotal = updatedState.teamCardTotal(team)
    updatedState = updatedState.withSkippedPullForMisconductThreshold(cardTotal)
    return CardAssessmentResult(
        state = updatedState,
        event = GameEvent.TeamCardsChanged(
            state = updatedState,
            team = team,
            teamCardTotal = cardTotal,
            playerCardType = PlayerCardEventType.YELLOW,
            playerCardJerseyNumber = identity.jerseyNumber,
            playerCardName = updatedState.playerCardFor(team, identity.jerseyNumber, identity.playerName)?.playerName,
        ),
    )
}
/**
 * Record a red card and determine any misconduct consequence.
 *
 * @param team The team receiving the red card.
 * @param jerseyNumber The player receiving the red card.
 */
fun GameState.assessRedCard(
    team: TeamId,
    jerseyNumber: String,
    now: Long,
    playerName: String = "",
    reason: CardReason = CardReason(),
): CardAssessmentResult {
    val identity = playerIdentityForAssessment(team, jerseyNumber, playerName)
    var updatedState = this.addInGameRedCard(team, identity.jerseyNumber, identity.playerName, reason)
        .withEventLogEntry(
            EventLogEntry(
                timestampEpoch = now,
                type = EventLogType.RED_CARD,
                team = team,
                player = identity,
            )
        ).withUndo(this, playerCardUndoLabel("Red", team, identity.jerseyNumber, identity.playerName))
    val cardTotal = updatedState.teamCardTotal(team)
    updatedState = updatedState.withSkippedPullForMisconductThreshold(cardTotal)
    return CardAssessmentResult(
        state = updatedState,
        event = GameEvent.TeamCardsChanged(
            state = updatedState,
            team = team,
            teamCardTotal = cardTotal,
            playerCardType = PlayerCardEventType.RED,
            playerCardJerseyNumber = identity.jerseyNumber,
            playerCardName = updatedState.playerCardFor(team, identity.jerseyNumber, identity.playerName)?.playerName,
        ),
    )
}

/**
 * Record a second yellow card and determine any misconduct consequence.
 *
 * @param team The team receiving the second yellow.
 * @param jerseyNumber The player receiving the second yellow.
 */
fun GameState.assessSecondYellowCard(
    team: TeamId,
    jerseyNumber: String,
    now: Long,
    playerName: String = "",
    reason: CardReason = CardReason(),
): CardAssessmentResult {
    val identity = playerIdentityForAssessment(team, jerseyNumber, playerName)
    var updatedState = this.addInGameSecondYellow(team, identity.jerseyNumber, identity.playerName, reason)
        .withEventLogEntry(
            EventLogEntry(
                timestampEpoch = now,
                type = EventLogType.YELLOW_CARD,
                team = team,
                player = identity,
            )
        ).withUndo(this, playerCardUndoLabel("Second yellow", team, identity.jerseyNumber, identity.playerName))
    val cardTotal = updatedState.teamCardTotal(team)
    updatedState = updatedState.withSkippedPullForMisconductThreshold(cardTotal)
    return CardAssessmentResult(
        state = updatedState,
        event = GameEvent.TeamCardsChanged(
            state = updatedState,
            team = team,
            teamCardTotal = cardTotal,
            playerCardType = PlayerCardEventType.SECOND_YELLOW,
            playerCardJerseyNumber = identity.jerseyNumber,
            playerCardName = updatedState.playerCardFor(team, identity.jerseyNumber, identity.playerName)?.playerName,
        ),
    )
}

/**
 * Build the undo label for a player-card action with the jersey number kept early for narrow UI.
 *
 * @param action The card action label, such as `Yellow`, `Second yellow`, or `Red`.
 * @param team The team whose name should appear in the undo label.
 * @param jerseyNumber The player identifier to include in the undo label.
 */
private fun GameState.playerCardUndoLabel(
    action: String,
    team: TeamId,
    jerseyNumber: String,
    playerName: String,
): String {
    return "Undo $action on ${PlayerIdentity(jerseyNumber, playerName).displayText(compact = true)} of ${this.teamName(team)}"
}

/**
 * Build the undo label for editing an existing player-card action.
 *
 * @param team The team whose name should appear in the undo label.
 * @param cardType The edited card color.
 * @param identity The edited player identity.
 */
internal fun GameState.playerCardEditUndoLabel(
    team: TeamId,
    cardType: CardType,
    identity: PlayerIdentity,
): String {
    return playerCardUndoLabel(
        action = "Edit ${cardType.label.lowercase()}",
        team = team,
        jerseyNumber = identity.jerseyNumber,
        playerName = identity.playerName,
    )
}

/**
 * Build the undo label for manually adding a player-card action.
 *
 * @param team The team whose name should appear in the undo label.
 * @param cardType The added card color.
 * @param identity The added player-card identity.
 */
internal fun GameState.playerCardAddUndoLabel(
    team: TeamId,
    cardType: CardType,
    identity: PlayerIdentity,
): String {
    val action = if (
        cardType == CardType.YELLOW &&
        (playerCardFor(team, identity.jerseyNumber, identity.playerName)?.yellows ?: 0) >= 1
    ) {
        "Second yellow"
    } else {
        cardType.label
    }
    return playerCardUndoLabel(action, team, identity.jerseyNumber, identity.playerName)
}

/**
 * Build the undo label for manually removing a player-card action.
 *
 * @param team The team whose name should appear in the undo label.
 * @param cardType The removed card color.
 * @param identity The removed player-card identity.
 */
internal fun GameState.playerCardRemoveUndoLabel(
    team: TeamId,
    cardType: CardType,
    identity: PlayerIdentity,
): String {
    return playerCardUndoLabel(
        action = "Remove ${cardType.label.lowercase()}",
        team = team,
        jerseyNumber = identity.jerseyNumber,
        playerName = identity.playerName,
    )
}

/**
 * Convert between-points misconduct threshold actions into a no-pull sequence when applicable.
 *
 * @param thresholdCount The team-card or technical-foul count after the recorded action.
 */
private fun GameState.withSkippedPullForMisconductThreshold(thresholdCount: Int): GameState {
    if (thresholdCount < 3 || this.phase == GamePhase.LIVE_POINT || this.phase == GamePhase.GAME_OVER) {
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
/**
 * One editable in-game player-card event.
 *
 * @param playerIndex Index of the player record containing this card.
 * @param cardIndex Index of the card event within that player record.
 * @param index Assessment-order index for this card event.
 * @param jerseyNumber The player's jersey number.
 * @param playerName The player's name, or blank when unknown.
 * @param cardType The card assessed.
 * @param reason Optional reason recorded for this card.
 */
data class EditablePlayerCard(
    val playerIndex: Int,
    val cardIndex: Int,
    val index: Int,
    val jerseyNumber: String,
    val playerName: String,
    val cardType: CardType,
    val reason: CardReason,
) {
    /// Return this card's player identity.
    internal fun identity(): PlayerIdentity {
        return PlayerIdentity(jerseyNumber, playerName)
    }
}
/**
 * Add a first yellow card to a team's in-game player records.
 *
 * @param team The team receiving the yellow card.
 * @param jerseyNumber The player receiving the card.
 */
private fun GameState.addInGameYellowCard(
    team: TeamId,
    jerseyNumber: String,
    playerName: String,
    reason: CardReason,
): GameState {
    return withPlayerCards(
        team = team,
        records = updatePlayerCardRecord(
            records = playerCardsFor(team),
            jerseyNumber = jerseyNumber,
            playerName = playerName,
        ) { record ->
            record.withAddedCard(CardType.YELLOW, getNextAssessmentIndex(), reason)
        },
        lastEvent = "Yellow card for ${teamName(team)} ${PlayerIdentity(jerseyNumber, playerName).displayText(compact = true)}.",
    )
}
/**
 * Add a second yellow card to a team's in-game player records.
 *
 * @param team The team receiving the second yellow.
 * @param jerseyNumber The player receiving the card.
 */
private fun GameState.addInGameSecondYellow(
    team: TeamId,
    jerseyNumber: String,
    playerName: String,
    reason: CardReason,
): GameState {
    return withPlayerCards(
        team = team,
        records = updatePlayerCardRecord(
            records = playerCardsFor(team),
            jerseyNumber = jerseyNumber,
            playerName = playerName,
        ) { record ->
            record.withAddedCard(CardType.YELLOW, getNextAssessmentIndex(), reason)
        },
        lastEvent = "Second yellow for ${teamName(team)} ${PlayerIdentity(jerseyNumber, playerName).displayText(compact = true)}.",
    )
}
/**
 * Add a red card to a team's in-game player records.
 *
 * @param team The team receiving the red card.
 * @param jerseyNumber The player receiving the card.
 */
private fun GameState.addInGameRedCard(
    team: TeamId,
    jerseyNumber: String,
    playerName: String,
    reason: CardReason,
): GameState {
    return withPlayerCards(
        team = team,
        records = updatePlayerCardRecord(
            records = playerCardsFor(team),
            jerseyNumber = jerseyNumber,
            playerName = playerName,
        ) { record ->
            record.withAddedCard(CardType.RED, getNextAssessmentIndex(), reason)
        },
        lastEvent = "Red card for ${teamName(team)} ${PlayerIdentity(jerseyNumber, playerName).displayText(compact = true)}.",
    )
}
/**
 * Update or create one player record and validate the resulting list.
 *
 * @param records The current player records for one team.
 * @param jerseyNumber The player record to update or create.
 * @param transform The exact card-count change to apply to that player's record.
 */
private fun updatePlayerCardRecord(
    records: List<PlayerRecord>,
    jerseyNumber: String,
    playerName: String = "",
    transform: (PlayerRecord) -> PlayerRecord,
): List<PlayerRecord> {
    val identity = PlayerIdentity(jerseyNumber, playerName)
    val existingIndex = records.indexOfFirst { record ->
        record.identity().matches(identity)
    }
    val updatedRecords = if (existingIndex >= 0) {
        records.mapIndexed { index, record ->
            if (index == existingIndex) {
                transform(record.withMergedIdentityFrom(identity))
            } else {
                record
            }
        }
    } else {
        records + transform(PlayerRecord(jerseyNumber = identity.jerseyNumber, playerName = identity.playerName))
    }
    requirePlayerRecordsValid(updatedRecords)
    return updatedRecords
}

/**
 * Return the player identity to use when assessing a live player card.
 *
 * @param team The team receiving the card.
 * @param jerseyNumber The entered player number, or blank for name-only.
 * @param playerName The entered player name, or blank when unknown.
 */
private fun GameState.playerIdentityForAssessment(
    team: TeamId,
    jerseyNumber: String,
    playerName: String,
): PlayerIdentity {
    val identity = PlayerIdentity(jerseyNumber, playerName)
    // Prefer an existing player record when the entry matches a known player, but fill any
    // blank number/name from the observer's newly entered details.
    return playerCards(team)
        .firstOrNull { record -> record.identity().matches(identity) }
        ?.identity()
        ?.withMissingFieldsFrom(identity)
        ?: identity
}

/**
 * Report whether a player already has a yellow card in this game.
 *
 * @param team The team whose player records should be searched.
 * @param jerseyNumber The player to check.
 */
fun GameState.playerHasYellowThisGame(team: TeamId, jerseyNumber: String, playerName: String = ""): Boolean {
    return (this.playerCardFor(team, jerseyNumber, playerName)?.yellows ?: 0) > 0
}
/**
 * Return the in-game player records for one team.
 *
 * @param team The team whose player records should be returned.
 */
fun GameState.playerCards(team: TeamId): List<PlayerRecord> {
    return this.playerCardsFor(team)
}
/**
 * Count in-game yellow cards from one team's player records.
 *
 * @param team The team whose yellow cards should be counted.
 */
fun GameState.teamYellowCards(team: TeamId): Int {
    return this.playerCardsFor(team).sumOf { it.yellows }
}
/**
 * Count in-game red cards from one team's player records.
 *
 * @param team The team whose red cards should be counted.
 */
fun GameState.teamRedCards(team: TeamId): Int {
    return this.playerCardsFor(team).sumOf { it.reds }
}
/**
 * Count total team card points: yellow plus blue plus two per red.
 *
 * @param team The team whose card total should be counted.
 */
fun GameState.teamCardTotal(team: TeamId): Int {
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
 * Return the stored player records for one team.
 *
 * @param team The team whose player-card list should be selected.
 */
private fun GameState.playerCardsFor(team: TeamId): List<PlayerRecord> {
    return if (team == TeamId.TEAM_ONE) teamOnePlayers else teamTwoPlayers
}
/**
 * Replace one team's player records and stores the related event text.
 *
 * @param team The team whose player records should be replaced.
 * @param records The validated player records to store.
 * @param lastEvent The short event text for the live state.
 */
private fun GameState.withPlayerCards(
    team: TeamId,
    records: List<PlayerRecord>,
    lastEvent: String,
): GameState {
    return when (team) {
        TeamId.TEAM_ONE -> copy(
            teamOnePlayers = records,
            lastEvent = lastEvent,
        )
        TeamId.TEAM_TWO -> copy(
            teamTwoPlayers = records,
            lastEvent = lastEvent,
        )
    }
}
/**
 * Find one player's in-game card record.
 *
 * @param team The team whose player records should be searched.
 * @param jerseyNumber The player identifier to find.
 */
private fun GameState.playerCardFor(
    team: TeamId,
    jerseyNumber: String,
    playerName: String = "",
): PlayerRecord? {
    val identity = PlayerIdentity(jerseyNumber, playerName)
    return playerCardsFor(team).firstOrNull { it.identity().matches(identity) }
}

/// Format the popup title for a team-card event.
internal fun GameEvent.TeamCardsChanged.formatPopupTitle(): String {
    return if (teamCardTotal >= 3) "Misconduct penalty" else "Misconduct"
}

/// Format the popup title for a technical-foul event.
internal fun GameEvent.TechnicalFoulsChanged.formatPopupTitle(): String {
    return "Technical Foul"
}

/// Report whether this event needs an offense/defense choice before showing the penalty cue.
fun GameEvent.needsMisconductChoice(): Boolean {
    return when (this) {
        is GameEvent.TeamCardsChanged -> teamCardTotal >= 3 && state.phase == GamePhase.LIVE_POINT
        is GameEvent.TechnicalFoulsChanged -> technicalFoulTotal >= 3 && state.phase == GamePhase.LIVE_POINT
        else -> false
    }
}

/// Format a team-card event message, including player-card and misconduct cue details.
internal fun GameEvent.TeamCardsChanged.formatMessage(): String {
    val totalMessage = this.totalBlueCardMessage()
    val baseMessage = if (playerCardType == null) {
        "This is ${state.teamName(team)}'s ${teamCardTotal.ordinalWordText()} blue card."
    } else {
        val jerseyNumber = playerCardJerseyNumber as String
        (playerCardEventLines(playerCardType, jerseyNumber, playerCardName.orEmpty()) + totalMessage).joinToString("\n")
    }
    return baseMessage.withMisconductCue(
        state = state,
        team = team,
        thresholdCount = teamCardTotal,
    )
}

/// Format the team blue-card total, marking mixed yellow/red/blue totals explicitly.
private fun GameEvent.TeamCardsChanged.totalBlueCardMessage(): String {
    val totalModifier = if (
        teamCardTotal > 1 &&
        (state.teamYellowCards(team) > 0 || state.teamRedCards(team) > 0)
    ) {
        "total "
    } else {
        ""
    }
    return "${state.teamName(team)} has ${countedNounPhrase(teamCardTotal, "${totalModifier}blue card")}."
}

/**
 * Build the player-specific message lines for a yellow, red, or second-yellow event.
 *
 * @param playerCardType The player-card event type to describe.
 * @param jerseyNumber The player number.
 */
private fun GameEvent.TeamCardsChanged.playerCardEventLines(
    playerCardType: PlayerCardEventType,
    jerseyNumber: String,
    playerName: String,
): List<String> {
    return buildList {
        val hasTournamentSuspension = state.playerHasTournamentSuspension(team, jerseyNumber, playerName)
        when (playerCardType) {
            PlayerCardEventType.YELLOW -> add("Yellow card on ${playerReference(jerseyNumber, playerName)}.")
            PlayerCardEventType.RED -> {
                add("Red card on ${playerReference(jerseyNumber, playerName)}.")
                if (!hasTournamentSuspension) {
                    add("${playerSentenceSubject(jerseyNumber, playerName)} receives a game suspension.")
                }
            }
            PlayerCardEventType.SECOND_YELLOW -> {
                add("Second yellow on ${playerReference(jerseyNumber, playerName)}.")
                if (!hasTournamentSuspension) {
                    add("${playerSentenceSubject(jerseyNumber, playerName)} receives a game suspension.")
                }
            }
        }
        if (playerCardType != PlayerCardEventType.YELLOW &&
            state.gameSuspensionStartedInSecondHalf() &&
            !hasTournamentSuspension
        ) {
            add("${playerSentenceSubject(jerseyNumber, playerName)} must also sit out the first half of the next game, if there is one.")
        }
        if (hasTournamentSuspension) {
            add("${playerSentenceSubject(jerseyNumber, playerName)} is suspended for the rest of the tournament.")
        }
    }
}

/**
 * Format a player reference for use in the middle of a sentence.
 *
 * @param jerseyNumber The player number.
 */
private fun playerReference(jerseyNumber: String, playerName: String): String {
    val name = playerName.trim()
    return when {
        jerseyNumber.isBlank() -> name
        name.isEmpty() -> "player $jerseyNumber"
        else -> "#$jerseyNumber $name"
    }
}

/**
 * Format a player reference for use as the subject of a sentence.
 *
 * @param jerseyNumber The player number.
 */
private fun playerSentenceSubject(jerseyNumber: String, playerName: String): String {
    val name = playerName.trim()
    return when {
        jerseyNumber.isBlank() -> name
        name.isEmpty() -> "Player $jerseyNumber"
        else -> "#$jerseyNumber $name"
    }
}

/// Report whether a game suspension started in the second half or later.
private fun GameState.gameSuspensionStartedInSecondHalf(): Boolean {
    return halftimeTaken
}

/**
 * Report whether the player's prior and in-game cards reach tournament suspension thresholds.
 *
 * @param team The player's team.
 * @param jerseyNumber The player number.
 */
private fun GameState.playerHasTournamentSuspension(
    team: TeamId,
    jerseyNumber: String,
    playerName: String,
): Boolean {
    val identity = PlayerIdentity(jerseyNumber, playerName)
    val player = playerCards(team).first { it.identity().matches(identity) }
    return player.totalCardPoints >= 3
}

/// Format a technical-foul event message, including misconduct cue details when needed.
internal fun GameEvent.TechnicalFoulsChanged.formatMessage(): String {
    val baseMessage = "This is ${state.teamName(team)}'s ${technicalFoulTotal.ordinalWordText()} technical foul."
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
    state: GameState,
    team: TeamId,
    thresholdCount: Int,
): String {
    return if (thresholdCount < 3 || state.phase == GamePhase.LIVE_POINT) {
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
private fun GameState.betweenPointsMisconductCue(team: TeamId): String {
    val receivingTeam = pullingTeam.flip()
    val penalizedTeamName = teamName(team)
    val receivingTeamName = teamName(receivingTeam)
    return if (team == receivingTeam) {
        "Penalty against $penalizedTeamName. No pull. Disc at negative brick in defending end zone."
    } else {
        "Penalty against $penalizedTeamName. No pull. $receivingTeamName starts at attacking brick."
    }
}

/// Format the title for a live-point misconduct prompt.
internal fun GamePrompt.LivePointMisconduct.formatTitle(): String = "Misconduct penalty"

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
private fun GamePrompt.LivePointMisconduct.misconductResolution(againstOffense: Boolean): String {
    val misconductTeam = event.misconductTeam()
    val state = event.stateAfterMisconduct()
    val offenseTeam = if (againstOffense) misconductTeam else misconductTeam.flip()
    val defenseTeam = offenseTeam.flip()
    val offenseName = state.teamName(offenseTeam)
    val defenseName = state.teamName(defenseTeam)
    val fieldPosition = if (againstOffense) {
        "$offenseName moves the disc to the reverse brick in the end zone they are defending. " +
            "$defenseName may instead choose to leave the disc where it is " +
            "(keeping the current stall count +1, max 9)."
    } else {
        "$offenseName may move the disc to the brick mark nearest the end zone they are attacking. " +
            "They may also choose to leave the disc where it is or center it."
    }
    return "$fieldPosition\n\nOffense has 30 seconds to set. " +
        "Then defense has 20 seconds to check the disc in."
}

/// Return the team that triggered the live-point misconduct prompt.
private fun GameEvent.misconductTeam(): TeamId {
    return when (this) {
        is GameEvent.TeamCardsChanged -> team
        is GameEvent.TechnicalFoulsChanged -> team
        else -> error("Live-point misconduct prompts require a card or technical-foul event.")
    }
}

/// Return the state after the event that triggered the live-point misconduct prompt.
private fun GameEvent.stateAfterMisconduct(): GameState {
    return when (this) {
        is GameEvent.TeamCardsChanged -> state
        is GameEvent.TechnicalFoulsChanged -> state
        else -> error("Live-point misconduct prompts require a card or technical-foul event.")
    }
}

/// List cues for the defense check-in window after offense is set.
internal fun defenseCheckTimingCues(): List<TimingCue> {
    return listOf(
        TimingCue(TimingCueId.DEFENSE_TWENTY, 20),
        TimingCue(TimingCueId.DEFENSE_TEN, 10),
        TimingCue(TimingCueId.DEFENSE_CHECK_LIMIT, 0),
    )
}

/// Offer a live-point misconduct countdown without starting it before the observer is ready.
fun GameState.withPendingMisconductCountdown(): GameState {
    if (phase != GamePhase.LIVE_POINT) {
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
fun GameState.startMisconductCountdown(now: Long): GameState {
    if (phase != GamePhase.LIVE_POINT || !pendingMisconductCountdown) {
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
 * Report whether the current offense-set countdown can switch to explicit defense timing.
 *
 * @param showDefenseCountdowns Whether the user setting enables this workflow.
 */
fun GameState.canReportOffenseSet(showDefenseCountdowns: Boolean): Boolean {
    val countdown = countdown ?: return false
    return showDefenseCountdowns && when {
        phase == GamePhase.LIVE_POINT && countdown.kind == CountdownKind.TIME_OUT -> true
        phase == GamePhase.BETWEEN_POINTS && countdown.kind == CountdownKind.MISCONDUCT_BETWEEN_POINTS -> true
        else -> false
    }
}

/**
 * Switch the current offense-set countdown to the defense check-in window.
 *
 * Normally the defense gets 20 seconds after the offense was required to be set, or
 * after the offense actually sets if that happens later. Between-points misconduct is
 * the odd case: offense has 90 seconds, but defense gets only until the later of 100
 * seconds total or 20 seconds after offense actually sets. If offense sets early
 * enough, the defense gets only 10 seconds beyond the time offense could have taken,
 * rather than the usual 20 seconds beyond the offense-set limit.
 *
 * @param now The epoch millis when offense is reported set.
 */
fun GameState.reportOffenseSet(now: Long): GameState {
    val countdown = countdown ?: return this
    val targetEpoch = when {
        phase == GamePhase.LIVE_POINT && countdown.kind == CountdownKind.TIME_OUT -> {
            max(countdown.targetEpoch, now) + 20_000L
        }
        phase == GamePhase.BETWEEN_POINTS && countdown.kind == CountdownKind.MISCONDUCT_BETWEEN_POINTS -> {
            max(countdown.targetEpoch + 10_000L, now + 20_000L)
        }
        else -> return this
    }
    return copy(
        countdown = CountdownState(
            kind = CountdownKind.DEFENSE_CHECK,
            label = "Defense check in",
            durationSeconds = ((targetEpoch - now) / 1000L).toInt(),
            targetEpoch = targetEpoch,
        ),
        lastEvent = "Offense set; defense check started.",
    )
}
