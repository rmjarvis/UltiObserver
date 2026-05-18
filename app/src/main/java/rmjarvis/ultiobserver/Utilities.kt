package rmjarvis.ultiobserver

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.max

private val CLOCK_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("h:mm a")
private val START_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MMM d, yyyy")
private val COMPACT_START_DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("M/d/yy")

/**
 * Format a local clock time for user-facing display.
 * For example, `3:30 PM`.
 *
 * @param time The local time value to show.
 */
fun formatClockTime(time: LocalTime): String {
    return time.format(CLOCK_TIME_FORMATTER)
}

/**
 * Format a local date for user-facing display.
 * For example, `Apr 5, 2026`.
 *
 * @param date The local date to format.
 */
internal fun formatStartDate(date: LocalDate): String {
    return date.format(START_DATE_FORMATTER)
}

/**
 * Format a compact local game start date/time for list rows.
 * For example, `4/5/26 3:30 PM`.
 *
 * @param date The local date to format.
 * @param time The local time to format.
 */
internal fun formatCompactStartDateTime(date: LocalDate, time: LocalTime): String {
    return "${date.format(COMPACT_START_DATE_FORMATTER)} ${formatClockTime(time)}"
}

/**
 * Format a duration as a clamped minute-second countdown string.
 * For example, 32 seconds is shown as `0:32`.
 *
 * @param duration The duration to display; negative values are shown as zero.
 */
fun formatDuration(duration: Duration): String {
    val totalSeconds = max(0L, duration.seconds)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

/**
 * Round a reference time to the default setup start time.
 * The default game start is the next even half hour after the reference time.
 *
 * @param referenceTime The time to round up to the next half-hour boundary.
 */
fun nextHalfHourFrom(referenceTime: LocalTime): LocalTime {
    val roundedMinute = when {
        referenceTime.minute == 0 && referenceTime.second == 0 -> 0
        referenceTime.minute < 30 -> 30
        else -> 0
    }
    val baseHour = if (roundedMinute == 0 && referenceTime.minute >= 30) {
        referenceTime.hour + 1
    } else {
        referenceTime.hour
    }
    return LocalTime.of(baseHour % 24, roundedMinute)
}

/**
 * Return a singular or plural noun for a count.
 *
 * @param count The count controlling pluralization.
 * @param singular The singular noun form.
 */
internal fun pluralize(count: Int, singular: String): String {
    return if (count == 1) singular else "${singular}s"
}
