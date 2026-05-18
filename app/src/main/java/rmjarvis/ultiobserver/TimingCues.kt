package rmjarvis.ultiobserver

import java.time.Duration
import kotlinx.serialization.Serializable

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
 * Configured timing cue and its user-facing cue label.
 *
 * @param label The message shown or spoken when the cue is delivered.
 */
@Serializable
enum class TimingCueId(
    val label: String,
) {
    RECEIVING_TWENTY_FOR_HAND("20 seconds for a hand"),
    RECEIVING_TEN_FOR_HAND("10 seconds for a hand"),
    RECEIVING_GIVE_HAND("Give hand"),
    PULLING_TWENTY_TO_PULL("20 seconds to pull"),
    PULLING_TEN_TO_PULL("10 seconds to pull"),
    PULLING_TIME_VIOLATION("Time violation?"),
    TIMEOUT_CLEAR_FIELD("Sideline players clear the field"),
    TIMEOUT_OFFENSE_TWENTY("20 seconds, offense"),
    TIMEOUT_OFFENSE_TEN("10 seconds, offense"),
    TIMEOUT_COUNTDOWN_FROM_FIVE("Countdown from 5"),
    TIMEOUT_OFFENSE_FREEZE_DEFENSE_TWENTY("Offense freeze; defense 20 seconds"),
    MISCONDUCT_OFFENSE_TWENTY("20 seconds, offense"),
    MISCONDUCT_OFFENSE_TEN("10 seconds, offense"),
    MISCONDUCT_COUNTDOWN_FROM_FIVE("Countdown from 5"),
    MISCONDUCT_OFFENSE_FREEZE_DEFENSE_TWENTY("Offense freeze; defense 20 seconds"),
    MISCONDUCT_DEFENSE_TWENTY("20 seconds, defense"),
    TIMEOUT_BETWEEN_POINTS_ONE_MINUTE_FOR_HAND("1 minute for a hand"),
    TIMEOUT_BETWEEN_POINTS_ONE_MINUTE_TO_PULL("1 minute to pull"),
    HALFTIME_FIVE_MINUTES("5 minutes"),
    HALFTIME_TWO_MINUTES("2 minutes"),
    HALF_CAP("Half cap"),
    SOFT_CAP("Soft cap"),
    HARD_CAP("Hard cap"),
}

/**
 * Cue offset within a countdown.
 *
 * @param id The timing cue to deliver.
 * @param remainingSeconds The countdown time remaining when this cue fires.
 */
internal data class TimingCue(
    val id: TimingCueId,
    val remainingSeconds: Int,
)

/**
 * Timing cue prepared for display or alert delivery.
 *
 * @param id The timing cue being displayed or delivered.
 * @param message The user-facing cue message.
 * @param remaining The real time until the cue fires.
 * @param countdownTime The countdown value associated with the cue.
 * @param targetEpoch The epoch millis when the cue should fire.
 */
internal data class TimingCueDisplay(
    val id: TimingCueId,
    val message: String,
    val remaining: Duration,
    val countdownTime: Duration,
    val targetEpoch: Long,
)

/// Build a stable deduplication key for a timing cue.
internal fun TimingCueDisplay.alertKey(): String {
    return "${id.name}:$targetEpoch"
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
