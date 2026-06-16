package rmjarvis.ultiobserver

/**
 * Build a player record from card-count summaries for tests.
 *
 * @param jerseyNumber The player's jersey number, blank for a name-only identity, or `N/A` when unknown.
 * @param yellows The number of yellow-card events to create.
 * @param reds The number of red-card events to create.
 * @param playerName The player's name, or blank when unknown.
 */
internal fun playerRecordWithCards(
    jerseyNumber: String,
    yellows: Int = 0,
    reds: Int = 0,
    playerName: String = "",
): PlayerRecord {
    require(yellows >= 0 && reds >= 0) {
        "Player records cannot have negative card counts."
    }
    return PlayerRecord(
        jerseyNumber = jerseyNumber,
        playerName = playerName,
        cards = buildList {
            repeat(yellows) { add(InGamePlayerCardEvent(CardType.YELLOW)) }
            repeat(reds) { add(InGamePlayerCardEvent(CardType.RED)) }
        },
    )
}

/**
 * Build a player record with previous-game card counts for tests.
 *
 * @param jerseyNumber The player's jersey number, blank for a name-only identity, or `N/A` when unknown.
 * @param priorYellows Yellow cards from previous games.
 * @param priorReds Red cards from previous games.
 * @param playerName The player's name, or blank when unknown.
 */
internal fun priorPlayerRecord(
    jerseyNumber: String,
    priorYellows: Int = 0,
    priorReds: Int = 0,
    playerName: String = "",
): PlayerRecord {
    return PlayerRecord(
        jerseyNumber = jerseyNumber,
        playerName = playerName,
        priorYellows = priorYellows,
        priorReds = priorReds,
    )
}
