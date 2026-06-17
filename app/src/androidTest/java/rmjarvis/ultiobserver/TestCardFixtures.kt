package rmjarvis.ultiobserver

/**
 * Build a player record from card-count summaries for UI tests.
 *
 * @param jerseyNumber The player's jersey number, or blank for a name-only identity.
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
