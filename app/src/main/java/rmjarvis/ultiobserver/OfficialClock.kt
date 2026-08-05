package rmjarvis.ultiobserver

import java.time.Duration
import java.time.ZoneId
import java.time.format.DateTimeFormatter

private val OFFICIAL_CLOCK_FORMATTER: DateTimeFormatter =
    DateTimeFormatter.ofPattern("h:mm:ss.S a")

/**
 * Format a phone epoch as this game's official tournament clock time.
 *
 * @param phoneTimeMillis The phone-clock epoch to translate and format.
 * @param formatter The visible clock format, including AM/PM by default.
 */
internal fun GameState.formatOfficialGameTime(
    phoneTimeMillis: Long,
    formatter: DateTimeFormatter = CLOCK_TIME_FORMATTER,
): String {
    return localTimeFromEpoch(
        phoneTimeMillis + officialClockOffsetMillis,
        timeZone,
    ).format(formatter)
}

/** Return the offset that rounds the visible official time to its nearest minute boundary. */
internal fun syncOfficialClockOffsetToNearestMinute(
    phoneTimeMillis: Long,
    currentOffsetMillis: Long,
): Long {
    val officialTime = phoneTimeMillis + currentOffsetMillis
    val nearestMinute = Math.floorDiv(officialTime + 30_000L, 60_000L) * 60_000L
    return nearestMinute - phoneTimeMillis
}

/** Shift the official tournament clock by an exact number of minutes. */
internal fun adjustOfficialClockOffsetMinutes(offsetMillis: Long, minutes: Int): Long {
    return offsetMillis + minutes * 60_000L
}

/** Format an official epoch with seconds and tenths for the clock-synchronization screen. */
internal fun formatOfficialClockTime(epochMillis: Long, timeZone: ZoneId): String {
    return localTimeFromEpoch(epochMillis, timeZone).format(OFFICIAL_CLOCK_FORMATTER)
}

/** Describe the official clock's offset from phone time. */
internal fun describeOfficialClockOffset(offsetMillis: Long): String {
    if (offsetMillis == 0L) {
        return "Using phone time"
    }
    val direction = if (offsetMillis > 0L) "ahead of" else "behind"
    val magnitude = Duration.ofMillis(kotlin.math.abs(offsetMillis))
    val minutes = magnitude.toMinutes()
    val seconds = (magnitude.toMillis() % 60_000L) / 1_000.0
    val amount = when {
        minutes == 0L -> String.format("%.1f seconds", seconds)
        seconds == 0.0 -> "$minutes ${pluralize(minutes.toInt(), "minute")}"
        else -> String.format(
            "%d %s, %.1f seconds",
            minutes,
            pluralize(minutes.toInt(), "minute"),
            seconds,
        )
    }
    return "Official clock is $amount $direction phone time"
}

/** Apply a new clock mapping while keeping opening timing attached to the official start time. */
internal fun GameState.withOfficialClockOffset(offsetMillis: Long): GameState {
    val offsetDeltaMillis = offsetMillis - officialClockOffsetMillis
    val activeCountdown = countdown
    return copy(
        officialClockOffsetMillis = offsetMillis,
        countdown = if (activeCountdown?.kind == CountdownKind.OPENING_PULL) {
            activeCountdown.copy(
                targetEpoch = activeCountdown.targetEpoch - offsetDeltaMillis,
            )
        } else {
            activeCountdown
        },
    )
}
