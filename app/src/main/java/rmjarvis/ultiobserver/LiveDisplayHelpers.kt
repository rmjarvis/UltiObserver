package rmjarvis.ultiobserver

/**
 * Format a player number for display.
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
