package rmjarvis.ultiobserver

import java.time.Duration
import java.time.LocalTime
import kotlin.math.max

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
 * Build the halftime countdown from the configured halftime length.
 *
 * @param halftimeMinutes The number of minutes halftime should last.
 * @param sequenceStart The epoch millis when halftime starts.
 */
internal fun buildHalftimeCountdown(
    halftimeMinutes: Int,
    sequenceStart: Long,
): CountdownState {
    val durationSeconds = halftimeMinutes * 60
    return CountdownState(
        kind = CountdownKind.HALFTIME,
        label = "Halftime",
        durationSeconds = durationSeconds,
        targetEpoch = sequenceStart + durationSeconds * 1000L,
    )
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

internal data class TimingCue(
    val id: TimingCueId,
    val remainingSeconds: Int,
)

internal data class TimingCueDisplay(
    val id: TimingCueId,
    val message: String,
    val remaining: Duration,
    val countdownTime: Duration,
    val targetEpoch: Long,
)

/// Build the default timing-alert mode map for every cue.
internal fun defaultTimingCueModes(): Map<TimingCueId, TimingAlertMode> {
    return TimingCueId.entries.associateWith { it.defaultAlertMode() }
}

/// Build the default repeat-count map for every timing cue.
internal fun defaultTimingCueRepeatCounts(): Map<TimingCueId, Int> {
    return TimingCueId.entries.associateWith { it.defaultRepeatCount() }
}

/// Return the default sound/vibration repeat count for a cue.
internal fun TimingCueId.defaultRepeatCount(): Int {
    return when (this) {
        TimingCueId.RECEIVING_TWENTY_FOR_HAND,
        TimingCueId.TIMEOUT_OFFENSE_TWENTY,
        TimingCueId.MISCONDUCT_OFFENSE_TWENTY,
        TimingCueId.HALFTIME_FIVE_MINUTES,
        TimingCueId.HALFTIME_TWO_MINUTES,
        TimingCueId.HALF_CAP,
        TimingCueId.SOFT_CAP,
        -> 2
        TimingCueId.HARD_CAP -> 3
        else -> DEFAULT_TIMING_ALERT_REPEAT_COUNT
    }
}

/**
 * Return the next future cue within this countdown.
 *
 * @param now The current epoch millis used to compute the next cue and its time remaining.
 */
internal fun CountdownState.nextTimingCue(now: Long): TimingCueDisplay? {
    return timingCues()
        .firstNotNullOfOrNull { cue ->
            val cueEpoch = targetEpoch - cue.remainingSeconds * 1000L
            if (cueEpoch >= now) {
                TimingCueDisplay(
                    id = cue.id,
                    message = cue.id.label,
                    remaining = Duration.ofMillis(cueEpoch - now),
                    countdownTime = Duration.ofSeconds(cue.remainingSeconds.toLong()),
                    targetEpoch = cueEpoch,
                )
            } else {
                null
            }
        }
}

/**
 * Return a cue that is due now within the short alert-delivery window.
 *
 * @param now The current epoch millis used to compare cue times against the delivery window.
 */
internal fun CountdownState.dueTimingCue(now: Long): TimingCueDisplay? {
    return timingCues()
        .firstNotNullOfOrNull { cue ->
            val cueEpoch = targetEpoch - cue.remainingSeconds * 1000L
            val elapsedSinceCue = now - cueEpoch
            if (elapsedSinceCue in 0L..1_100L) {
                TimingCueDisplay(
                    id = cue.id,
                    message = cue.id.label,
                    remaining = Duration.ZERO,
                    countdownTime = Duration.ofSeconds(cue.remainingSeconds.toLong()),
                    targetEpoch = cueEpoch,
                )
            } else {
                null
            }
        }
}

/**
 * List timing alerts due at the current moment for the active countdown and relevant caps.
 *
 * @param now The current epoch millis used to evaluate countdown and cap cue windows.
 */
internal fun LiveGameState.dueTimingAlerts(now: Long): List<TimingCueDisplay> {
    return listOfNotNull(
        countdown?.dueTimingCue(now),
        dueCapTimingCue(now),
    ).sortedBy { cue -> cue.targetEpoch }
}

/**
 * Return the next visible or audible timing alert for the active game state.
 *
 * @param now The current epoch millis used to rank countdown and cap cues.
 */
internal fun LiveGameState.nextTimingAlert(now: Long): TimingCueDisplay? {
    return listOfNotNull(
        countdown?.nextTimingCue(now),
        nextCapTimingCue(now),
    )
        .sortedBy { cue -> cue.targetEpoch }
        .firstOrNull()
}

/// List the configured cue offsets for a countdown.
private fun CountdownState.timingCues(): List<TimingCue> {
    return when (kind) {
        CountdownKind.OPENING_PULL, CountdownKind.BETWEEN_POINTS, CountdownKind.PULL_RESET -> betweenPointsTimingCues()
        CountdownKind.MISCONDUCT_BETWEEN_POINTS -> misconductTimingCues()
        CountdownKind.MISCONDUCT_DEFENSE_CHECK -> misconductDefenseCheckTimingCues()
        CountdownKind.TIME_OUT -> timeoutTimingCues()
        CountdownKind.HALFTIME -> halftimeTimingCues()
    }
}

/// List cues for live-point timeout and live-point misconduct countdowns.
private fun CountdownState.timeoutTimingCues(): List<TimingCue> {
    val openingCues = if (durationSeconds > 30) {
        listOf(TimingCue(TimingCueId.TIMEOUT_CLEAR_FIELD, 30))
    } else {
        emptyList()
    }
    return openingCues + listOf(
        TimingCue(TimingCueId.TIMEOUT_OFFENSE_TWENTY, 20),
        TimingCue(TimingCueId.TIMEOUT_OFFENSE_TEN, 10),
        TimingCue(TimingCueId.TIMEOUT_COUNTDOWN_FROM_FIVE, 5),
        TimingCue(TimingCueId.TIMEOUT_OFFENSE_FREEZE_DEFENSE_TWENTY, 0),
    )
}

/// List halftime cues that fit within the configured halftime duration.
private fun CountdownState.halftimeTimingCues(): List<TimingCue> {
    return listOf(
        TimingCue(TimingCueId.HALFTIME_FIVE_MINUTES, 5 * 60),
        TimingCue(TimingCueId.HALFTIME_TWO_MINUTES, 2 * 60),
    ).filter { cue -> cue.remainingSeconds <= durationSeconds }
}
