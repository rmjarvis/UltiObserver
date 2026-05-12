package rmjarvis.ultiobserver

// Show N/A for the unknown-player sentinel; otherwise format as a jersey number.
internal fun displayPlayerNumber(jerseyNumber: String): String {
    return if (jerseyNumber == UNKNOWN_PLAYER_NUMBER) {
        "N/A"
    } else {
        "#$jerseyNumber"
    }
}
