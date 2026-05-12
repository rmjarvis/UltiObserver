package rmjarvis.ultiobserver

import java.time.Duration
import java.time.LocalTime
import kotlin.math.max

// Format a duration into a nice format like "0:32"
fun formatDuration(duration: Duration): String {
    val totalSeconds = max(0L, duration.seconds)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}
// Build a countdown after a goal is scored.
// This is different depending on whether the observer is on the side of the pulling
// or receiving team.  (Observer is assumed on the near end.)
internal fun buildBetweenPointsCountdown(
    pullingFromEnd: FieldEnd,
    sequenceStart: Long,
    kind: CountdownKind = CountdownKind.BETWEEN_POINTS,
): CountdownState {
    require(kind.usesBetweenPointsTarget()) {
        "Countdown kind $kind does not use between-points timing."
    }
    val target = betweenPointsCountdownTargetFor(pullingFromEnd)
    val durationSeconds = target.baseDurationSeconds(kind)
    return CountdownState(
        kind = kind,
        label = target.label,
        durationSeconds = durationSeconds,
        targetEpoch = sequenceStart + durationSeconds * 1000L,
        betweenPointsTarget = target,
    )
}
private fun betweenPointsCountdownTargetFor(pullingFromEnd: FieldEnd): BetweenPointsCountdownTarget {
    return if (pullingFromEnd == FieldEnd.NEAR) {
        BetweenPointsCountdownTarget.PULL
    } else {
        BetweenPointsCountdownTarget.OFFENSE_READY
    }
}
// Visible between-points countdown text for the currently responsible side of the field.
fun betweenPointsDisplay(
    pullingFromEnd: FieldEnd,
    sequenceStart: Long,
    now: Long,
    kind: CountdownKind = CountdownKind.BETWEEN_POINTS,
): Pair<String, Duration> {
    val countdown = buildBetweenPointsCountdown(pullingFromEnd, sequenceStart, kind)
    return countdown.label to Duration.ofMillis((countdown.targetEpoch - now).coerceAtLeast(0L))
}
// Build a countdown for half time.
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
// The default start time for a game is the next even half hour after the reference time.
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
