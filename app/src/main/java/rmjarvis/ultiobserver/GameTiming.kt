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

internal data class TimingCue(
    val id: TimingCueId,
    val remainingSeconds: Int,
)

internal data class TimingCueDisplay(
    val id: TimingCueId,
    val message: String,
    val remaining: Duration,
    val targetEpoch: Long,
)

internal fun CountdownState.nextTimingCue(now: Long): TimingCueDisplay? {
    return timingCues()
        .firstNotNullOfOrNull { cue ->
            val cueEpoch = targetEpoch - cue.remainingSeconds * 1000L
            if (cueEpoch >= now) {
                TimingCueDisplay(
                    id = cue.id,
                    message = cue.id.label,
                    remaining = Duration.ofMillis(cueEpoch - now),
                    targetEpoch = cueEpoch,
                )
            } else {
                null
            }
        }
}

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
                    targetEpoch = cueEpoch,
                )
            } else {
                null
            }
        }
}

private fun CountdownState.timingCues(): List<TimingCue> {
    return when (kind) {
        CountdownKind.OPENING_PULL, CountdownKind.BETWEEN_POINTS -> betweenPointsTimingCues()
        CountdownKind.TIME_OUT -> timeoutTimingCues()
        CountdownKind.HALFTIME -> halftimeTimingCues()
    }
}

private fun CountdownState.betweenPointsTimingCues(): List<TimingCue> {
    return when (betweenPointsTarget ?: error("Between-points countdown is missing its target side.")) {
        BetweenPointsCountdownTarget.OFFENSE_READY -> listOf(
            TimingCue(TimingCueId.RECEIVING_TWENTY_FOR_HAND, 20),
            TimingCue(TimingCueId.RECEIVING_TEN_FOR_HAND, 10),
            TimingCue(TimingCueId.RECEIVING_GIVE_HAND, 0),
        )
        BetweenPointsCountdownTarget.PULL -> listOf(
            TimingCue(TimingCueId.PULLING_TWENTY_TO_PULL, 20),
            TimingCue(TimingCueId.PULLING_TEN_TO_PULL, 10),
            TimingCue(TimingCueId.PULLING_DELAY_OF_GAME, 0),
        )
    }
}

private fun timeoutTimingCues(): List<TimingCue> {
    return listOf(
        TimingCue(TimingCueId.TIMEOUT_CLEAR_FIELD, 30),
        TimingCue(TimingCueId.TIMEOUT_OFFENSE_TWENTY, 20),
        TimingCue(TimingCueId.TIMEOUT_OFFENSE_TEN, 10),
        TimingCue(TimingCueId.TIMEOUT_COUNTDOWN_FROM_FIVE, 5),
        TimingCue(TimingCueId.TIMEOUT_OFFENSE_FREEZE, 0),
    )
}

private fun CountdownState.halftimeTimingCues(): List<TimingCue> {
    return listOf(
        TimingCue(TimingCueId.HALFTIME_FIVE_MINUTES, 5 * 60),
        TimingCue(TimingCueId.HALFTIME_TWO_MINUTES, 2 * 60),
    ).filter { cue -> cue.remainingSeconds <= durationSeconds }
}
