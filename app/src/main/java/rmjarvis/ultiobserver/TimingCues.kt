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
    OFFENSE_TWENTY("20 seconds, offense"),
    OFFENSE_TEN("10 seconds, offense"),
    OFFENSE_COUNTDOWN_FROM_FIVE("Countdown from 5"),
    OFFENSE_SET_LIMIT("Offense freeze"),
    DEFENSE_TWENTY("20 seconds, defense"),
    DEFENSE_TEN("10 seconds, defense"),
    DEFENSE_CHECK_LIMIT("Offense start when ready"),
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
 * @param message The display message for the cue, defaulting to the cue id's label.
 */
internal data class TimingCue(
    val id: TimingCueId,
    val remainingSeconds: Int,
    val message: String = id.label,
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
 * Timing-alert listener decision for an upcoming cue.
 *
 * @param readyToPlay Whether the caller should deliver the alert after waiting.
 * @param delayMillis The requested delay before continuing, or 0 when no wait is needed.
 */
internal data class TimingAlertDeliveryWindowResult(
    val readyToPlay: Boolean,
    val delayMillis: Long,
)

/**
 * Return the wait/play decision for an upcoming timing alert.
 *
 * @param millisUntilNextAlert Milliseconds between now and the next alert target.
 * @param scheduleCheckMillis Normal listener polling cadence in milliseconds.
 */
internal fun timingAlertDeliveryWindow(
    millisUntilNextAlert: Long,
    scheduleCheckMillis: Long,
): TimingAlertDeliveryWindowResult {
    if (millisUntilNextAlert > 2 * scheduleCheckMillis) {
        return TimingAlertDeliveryWindowResult(
            readyToPlay = false,
            delayMillis = scheduleCheckMillis,
        )
    }
    if (millisUntilNextAlert > 0L) {
        return TimingAlertDeliveryWindowResult(
            readyToPlay = true,
            delayMillis = millisUntilNextAlert,
        )
    }
    return TimingAlertDeliveryWindowResult(
        readyToPlay = true,
        delayMillis = 0L,
    )
}

/**
 * Return the next future cue within this countdown.
 *
 * @param now The current epoch millis used to compute the next cue and its time remaining.
 */
internal fun CountdownState.nextTimingCue(now: Long): TimingCueDisplay? {
    return upcomingTimingCues(now).firstOrNull()
}

/**
 * Return all future cues within this countdown.
 *
 * @param now The current epoch millis used to compute each cue and its time remaining.
 */
internal fun CountdownState.upcomingTimingCues(now: Long): List<TimingCueDisplay> {
    if (isPaused()) {
        return emptyList()
    }
    return timingCues()
        .mapNotNull { cue ->
            val cueEpoch = targetEpoch - cue.remainingSeconds * 1000L
            if (cueEpoch >= now) {
                TimingCueDisplay(
                    id = cue.id,
                    message = cue.message,
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
    if (isPaused()) {
        return null
    }
    return timingCues()
        .firstNotNullOfOrNull { cue ->
            val cueEpoch = targetEpoch - cue.remainingSeconds * 1000L
            val elapsedSinceCue = now - cueEpoch
            if (elapsedSinceCue in 0L..TIMING_ALERT_DUE_WINDOW_MS) {
                TimingCueDisplay(
                    id = cue.id,
                    message = cue.message,
                    remaining = Duration.ZERO,
                    countdownTime = Duration.ofSeconds(cue.remainingSeconds.toLong()),
                    targetEpoch = cueEpoch,
                )
            } else {
                null
            }
        }
}

internal const val TIMING_ALERT_DUE_WINDOW_MS = 1_100L

/**
 * List timing alerts due at the current moment for the active countdown and relevant caps.
 *
 * @param now The current epoch millis used to evaluate countdown and cap cue windows.
 */
internal fun GameState.dueTimingAlerts(now: Long): List<TimingCueDisplay> {
    val countdownCue = countdown?.dueTimingCue(now)
    val capCue = dueCapTimingCue(now)
    return orderedTimingCues(countdownCue, capCue)
}

/**
 * Return the next visible or audible timing alert for the active game state.
 *
 * @param now The current epoch millis used to rank countdown and cap cues.
 */
internal fun GameState.nextTimingAlert(now: Long): TimingCueDisplay? {
    val countdownCue = countdown?.nextTimingCue(now)
    val capCue = nextCapTimingCue(now)
    return when {
        countdownCue == null -> capCue
        capCue == null -> countdownCue
        countdownCue.targetEpoch <= capCue.targetEpoch -> countdownCue
        else -> capCue
    }
}

/**
 * Return the non-null timing cues in target-time order.
 * This compares the two possible cue sources directly instead of allocating and sorting a tiny
 * list.
 *
 * @param first The first optional timing cue.
 * @param second The second optional timing cue.
 */
private fun orderedTimingCues(
    first: TimingCueDisplay?,
    second: TimingCueDisplay?,
): List<TimingCueDisplay> {
    return when {
        first == null && second == null -> emptyList()
        first == null -> listOf(second!!)
        second == null -> listOf(first)
        first.targetEpoch <= second.targetEpoch -> listOf(first, second)
        else -> listOf(second, first)
    }
}

/// List the configured cue offsets for a countdown.
private fun CountdownState.timingCues(): List<TimingCue> {
    return when (kind) {
        CountdownKind.OPENING_PULL,
        CountdownKind.BETWEEN_POINTS,
        CountdownKind.PULL_RESET -> betweenPointsTimingCues()
        CountdownKind.MISCONDUCT_BETWEEN_POINTS -> offenseSetTimingCues()
        CountdownKind.DEFENSE_CHECK -> defenseCheckTimingCues()
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
    return openingCues + offenseSetTimingCues()
}

/// List cues for offense-set countdowns in timeout and misconduct workflows.
internal fun offenseSetTimingCues(): List<TimingCue> {
    return listOf(
        TimingCue(TimingCueId.OFFENSE_TWENTY, 20),
        TimingCue(TimingCueId.OFFENSE_TEN, 10),
        TimingCue(TimingCueId.OFFENSE_COUNTDOWN_FROM_FIVE, 5),
        TimingCue(TimingCueId.OFFENSE_SET_LIMIT, 0),
    )
}

/// List halftime cues that fit within the configured halftime duration.
private fun CountdownState.halftimeTimingCues(): List<TimingCue> {
    return listOf(
        TimingCue(TimingCueId.HALFTIME_FIVE_MINUTES, 5 * 60),
        TimingCue(TimingCueId.HALFTIME_TWO_MINUTES, 2 * 60),
    ).filter { cue -> cue.remainingSeconds <= durationSeconds }
}
