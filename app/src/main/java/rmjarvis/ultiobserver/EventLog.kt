@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package rmjarvis.ultiobserver

import java.time.format.DateTimeFormatter
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable

private val EVENT_LOG_TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm")
private val CARD_LABELS = mapOf(
    EventLogType.YELLOW_CARD to "Yellow card",
    EventLogType.RED_CARD to "Red card",
    EventLogType.BLUE_CARD to "Blue card",
)
private val PULL_INFRACTION_EVENT_LABELS = mapOf(
    EventLogType.OFFSIDES to "Offsides",
    EventLogType.FALSE_START to "False start",
)
private val PULL_INFRACTION_CORRECTION_LABELS = mapOf(
    EventLogType.OFFSIDES to "offsides",
    EventLogType.FALSE_START to "false starts",
)

/// Kind of persisted event-log entry recorded for later game review.
@Serializable
enum class EventLogType {
    FIRST_PULL,
    GOAL,
    YELLOW_CARD,
    RED_CARD,
    BLUE_CARD,
    BLUE_CARD_AND_TECH_ADJUSTED,
    TECHNICAL_FOUL,
    OFFSIDES,
    FALSE_START,
    TIME_VIOLATION,
    TIMEOUT,
    HALFTIME,
    GAME_OVER,
    SCORE_ADJUSTED,
}

/**
 * Persisted event-log entry for a significant game event or manual correction.
 *
 * @param timestampEpoch Epoch millis for the event.
 * @param type The kind of event recorded.
 * @param team The team most directly associated with the event, when applicable.
 * @param player The player identity for player-card entries.
 * @param previousPlayer The previous player identity for edited player-card entries.
 * @param timeViolationOutcome The warning, timeout, or no-timeout result for time violations.
 * @param teamOneScore The team-one score after a score correction.
 * @param teamTwoScore The team-two score after a score correction.
 * @param delta The signed count change for correction entries.
 */
@Serializable
data class EventLogEntry(
    val timestampEpoch: Long,
    val type: EventLogType,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val team: TeamId? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val player: PlayerIdentity? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val previousPlayer: PlayerIdentity? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val timeViolationOutcome: TimeViolationOutcome? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val teamOneScore: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val teamTwoScore: Int? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val delta: Int? = null,
)

/**
 * Return this state with one event-log entry appended.
 *
 * @param entry The event-log entry to append.
 */
internal fun GameState.withEventLogEntry(entry: EventLogEntry): GameState {
    return copy(eventLog = eventLog + entry)
}

/**
 * Return this state with multiple event-log entries appended.
 *
 * @param entries The event-log entries to append in display order.
 */
internal fun GameState.withEventLogEntries(entries: List<EventLogEntry>): GameState {
    return if (entries.isEmpty()) this else copy(eventLog = eventLog + entries)
}

/**
 * Return the timestamp to use for first-pull logging.
 *
 * @param now The clock time when the point was actually started.
 */
internal fun GameState.firstPullLogTimestamp(now: Long): Long {
    return if (now < startEpoch) now else startEpoch
}

/**
 * Format an event-log line with local game time and compact event text.
 *
 * @param entry The entry to format.
 */
fun GameState.formatEventLogLine(entry: EventLogEntry): String {
    val time = localTimeFromEpoch(entry.timestampEpoch, timeZone).format(EVENT_LOG_TIME_FORMATTER)
    return "$time  ${formatEventLogDescription(entry)}"
}

/**
 * Format every persisted event-log entry for display.
 */
fun GameState.formatEventLogLines(): List<String> {
    return eventLog.map { entry -> formatEventLogLine(entry) }
}

/**
 * Format the compact description for an event-log entry.
 *
 * @param entry The entry to format.
 */
private fun GameState.formatEventLogDescription(entry: EventLogEntry): String {
    return when (entry.type) {
        EventLogType.FIRST_PULL -> firstPullDescription(entry)
        EventLogType.GOAL -> goalDescription(entry)
        EventLogType.YELLOW_CARD,
        EventLogType.RED_CARD,
        EventLogType.BLUE_CARD -> cardEventDescription(entry)
        EventLogType.BLUE_CARD_AND_TECH_ADJUSTED -> "Adjusted blue card/tech counts"
        EventLogType.TECHNICAL_FOUL -> technicalFoulDescription(entry)
        EventLogType.OFFSIDES,
        EventLogType.FALSE_START -> pullInfractionDescription(entry)
        EventLogType.TIME_VIOLATION -> timeViolationDescription(entry)
        EventLogType.TIMEOUT -> timeoutDescription(entry)
        EventLogType.HALFTIME -> "Halftime"
        EventLogType.GAME_OVER -> "Game over"
        EventLogType.SCORE_ADJUSTED -> scoreAdjustedDescription(entry)
    }
}

/// Return display text for the first pull.
private fun GameState.firstPullDescription(entry: EventLogEntry): String {
    return "First pull by ${teamName(entry.team!!)}"
}

/// Return display text for a goal event.
private fun GameState.goalDescription(entry: EventLogEntry): String {
    return "${teamName(entry.team!!)} Goal"
}

/// Return display text for a card event or card correction.
private fun GameState.cardEventDescription(entry: EventLogEntry): String {
    val label = entry.type.cardLabel()
    val delta = entry.delta
    val previousPlayer = entry.previousPlayer
    return when {
        previousPlayer != null -> cardEditDescription(entry, label.lowercase(), previousPlayer)
        delta == null -> "$label on ${cardAdjustmentTarget(entry)}"
        else -> "${delta.adjustmentVerb()} ${label.lowercase()} on ${cardAdjustmentTarget(entry)}"
    }
}

/// Return display text for a player-card edit correction.
private fun GameState.cardEditDescription(
    entry: EventLogEntry,
    label: String,
    previousPlayer: PlayerIdentity,
): String {
    val teamText = teamName(entry.team!!)
    val player = entry.player!!
    val previousText = previousPlayer.displayText(compact = false)
    val playerText = player.displayText(compact = false)
    return "Changed $label on $teamText from $previousText to $playerText"
}

/// Return display text for a manual card-correction target.
private fun GameState.cardAdjustmentTarget(entry: EventLogEntry): String {
    val teamText = teamName(entry.team!!)
    val player = entry.player
    return if (player == null) teamText else "$teamText ${player.displayText(compact = false)}"
}

/// Return display text for a timeout event or timeout correction.
private fun GameState.timeoutDescription(entry: EventLogEntry): String {
    val delta = entry.delta
    return if (delta == null) {
        "Timeout by ${teamName(entry.team!!)}"
    } else {
        "Adjusted ${teamName(entry.team!!)} timeouts ${delta.formatDelta()}"
    }
}

/// Return display text for a technical-foul event or technical-foul correction.
private fun GameState.technicalFoulDescription(entry: EventLogEntry): String {
    val delta = entry.delta
    return if (delta == null) {
        "Technical foul on ${teamName(entry.team!!)}"
    } else {
        "Adjusted ${teamName(entry.team!!)} technical fouls ${delta.formatDelta()}"
    }
}

/// Return display text for a pull-infraction event or pull-infraction correction.
private fun GameState.pullInfractionDescription(entry: EventLogEntry): String {
    val delta = entry.delta
    val label = entry.type.pullInfractionCorrectionLabel()
    return if (delta == null) {
        "${entry.type.pullInfractionEventLabel()} on ${teamName(entry.team!!)}"
    } else {
        "Adjusted ${teamName(entry.team!!)} $label ${delta.formatDelta()}"
    }
}

/// Return display text for a time-violation event.
private fun GameState.timeViolationDescription(entry: EventLogEntry): String {
    val outcome = entry.timeViolationOutcome!!
    val suffix = when (outcome) {
        TimeViolationOutcome.WARNING -> " warning"
        TimeViolationOutcome.TIMEOUT -> ", timeout charged"
        TimeViolationOutcome.NO_TIMEOUT -> ", no timeout remaining"
    }
    return "Time violation on ${teamName(entry.team!!)}$suffix"
}

/// Return display text for a score correction.
private fun GameState.scoreAdjustedDescription(entry: EventLogEntry): String {
    return "Adjusted score: ${teamOne.name} ${entry.teamOneScore!!} - ${teamTwo.name} ${entry.teamTwoScore!!}"
}

/// Return a signed delta with an explicit plus sign for additions.
private fun Int.formatDelta(): String {
    return if (this > 0) "+$this" else "$this"
}

/// Return the leading verb for a manual add/remove correction.
private fun Int.adjustmentVerb(): String {
    return if (this > 0) "Added" else "Removed"
}

/// Return compact display text for a card event type.
private fun EventLogType.cardLabel(): String {
    return CARD_LABELS.getValue(this)
}

/// Return compact display text for a pull-infraction event type.
private fun EventLogType.pullInfractionEventLabel(): String {
    return PULL_INFRACTION_EVENT_LABELS.getValue(this)
}

/// Return lowercase display text for pull-infraction correction labels.
private fun EventLogType.pullInfractionCorrectionLabel(): String {
    return PULL_INFRACTION_CORRECTION_LABELS.getValue(this)
}
