package rmjarvis.ultiobserver

import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * Format a local clock time for user-facing display.
 * For example, `3:30 PM`.
 *
 * @param time The local time value to show.
 */
fun formatClockTime(time: LocalTime): String {
    return time.format(DateTimeFormatter.ofPattern("h:mm a"))
}

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
