@file:OptIn(kotlinx.serialization.ExperimentalSerializationApi::class)

package rmjarvis.ultiobserver

import java.time.format.DateTimeFormatter
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.Serializable

internal val EVENT_LOG_TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm")
private val CARD_LABELS = mapOf(
    EventLogType.YELLOW_CARD to "Yellow card",
    EventLogType.RED_CARD to "Red card",
    EventLogType.BLUE_CARD to "Blue card",
)
private val PULL_INFRACTION_EVENT_LABELS = mapOf(
    EventLogType.OFFSIDES to "Offsides",
    EventLogType.FALSE_START to "False start",
    EventLogType.MAJORITY_PULL to "Majority pull violation",
)
private val PULL_INFRACTION_CORRECTION_LABELS = mapOf(
    EventLogType.OFFSIDES to "offsides",
    EventLogType.FALSE_START to "false starts",
    EventLogType.MAJORITY_PULL to "majority pull violations",
)

/// Kind of persisted event-log entry recorded for later game review.
@Serializable
enum class EventLogType {
    FIRST_PULL,
    GOAL,
    YELLOW_CARD,
    RED_CARD,
    BLUE_CARD,
    TECHNICAL_FOUL,
    OFFSIDES,
    FALSE_START,
    MAJORITY_PULL,
    TIME_VIOLATION,
    TIMEOUT,
    WATER_BREAK,
    HEAT_LEVEL,
    HALFTIME,
    GAME_OVER,
    SCORE_ADJUSTED,
}

/**
 * Persisted event-log entry for a significant game event or manual correction.
 *
 * @param timeText Persisted official-clock time formatted when the event is recorded.
 * @param type The kind of event recorded.
 * @param team The team most directly associated with the event, when applicable.
 * @param player The player identity for player-card entries.
 * @param previousPlayer The previous player identity for edited player-card entries.
 * @param timeViolationOutcome The warning, timeout, or no-timeout result for time violations.
 * @param useAirQualityGuidelines Whether the recorded heat level represents an AQI level.
 * @param teamOneScore The team-one score after a score correction.
 * @param teamTwoScore The team-two score after a score correction.
 * @param delta The signed count change for correction entries.
 */
@Serializable
data class EventLogEntry(
    val timeText: String,
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
    val heatLevel: HeatLevel? = null,
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    val useAirQualityGuidelines: Boolean = false,
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
 * @param entries Entries to append in display order.
 */
internal fun GameState.withEventLogEntries(entries: List<EventLogEntry>): GameState {
    return if (entries.isEmpty()) {
        this
    } else {
        copy(eventLog = eventLog + entries)
    }
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
    return "${entry.timeText}  ${formatEventLogDescription(entry)}"
}

/**
 * Format every persisted event-log entry for display.
 */
fun GameState.formatEventLogLines(): List<String> {
    return eventLog.map { entry -> formatEventLogLine(entry) }
}

/**
 * Build the full event-log text used by Android sharing.
 *
 * @receiver The game state whose event log should be shared.
 */
internal fun GameState.eventLogShareText(): String {
    val rows = formatEventLogLines()
    return buildList {
        add("UltiObserver Event Log")
        add("")
        add(winnerFirstTeams().joinToString(" vs ") { team -> team.name })
        gameInformationSummaryLine()?.let { add(it) }
        observersSummaryLine()?.let { add(it) }
        add(formatStartDate(startDate))
        add("")
        if (rows.isEmpty()) {
            add("No events logged yet.")
        } else {
            addAll(rows)
        }
        if (phase == GamePhase.GAME_OVER) {
            add("")
            add("Final score:")
            addAll(winnerFirstTeams().map { team -> "${team.name} ${team.score}" })
        }
    }.joinToString("\n")
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
        EventLogType.TECHNICAL_FOUL -> technicalFoulDescription(entry)
        EventLogType.OFFSIDES,
        EventLogType.FALSE_START,
        EventLogType.MAJORITY_PULL -> pullViolationDescription(entry)
        EventLogType.TIME_VIOLATION -> timeViolationDescription(entry)
        EventLogType.TIMEOUT -> timeoutDescription(entry)
        EventLogType.WATER_BREAK -> waterBreakDescription(entry)
        EventLogType.HEAT_LEVEL -> heatLevelDescription(entry)
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
        entry.type == EventLogType.BLUE_CARD && delta != null ->
            "Adjusted ${teamName(entry.team!!)} blue cards ${delta.formatDelta()}"
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

/// Return display text for a water break.
private fun waterBreakDescription(entry: EventLogEntry): String {
    return "Water break (+${entry.delta} min)"
}

/// Return display text for a live heat-level change.
private fun heatLevelDescription(entry: EventLogEntry): String {
    val levelLabel = if (entry.useAirQualityGuidelines) "AQI level" else "Heat level"
    return when (entry.heatLevel!!) {
        HeatLevel.NONE -> "$levelLabel disabled"
        HeatLevel.LEVEL_3 -> "$levelLabel 3 — game suspended"
        else -> "$levelLabel set to ${entry.heatLevel.displayText.removePrefix("Level ")}"
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

/// Return display text for a pull-violation event or pull-violation correction.
private fun GameState.pullViolationDescription(entry: EventLogEntry): String {
    val delta = entry.delta
    val label = entry.type.pullViolationCorrectionLabel()
    return if (delta == null) {
        "${entry.type.pullViolationEventLabel()} on ${teamName(entry.team!!)}"
    } else {
        "Adjusted ${teamName(entry.team!!)} $label ${delta.formatDelta()}"
    }
}

/// Return display text for a time-violation event or correction.
private fun GameState.timeViolationDescription(entry: EventLogEntry): String {
    val delta = entry.delta
    if (delta != null) {
        return "Adjusted ${teamName(entry.team!!)} time violations ${delta.formatDelta()}"
    }
    val outcome = entry.timeViolationOutcome!!
    val suffix = when (outcome) {
        TimeViolationOutcome.WARNING -> ", warning"
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

/// Return compact display text for a pull-violation event type.
private fun EventLogType.pullViolationEventLabel(): String {
    return PULL_INFRACTION_EVENT_LABELS.getValue(this)
}

/// Return lowercase display text for pull-violation correction labels.
private fun EventLogType.pullViolationCorrectionLabel(): String {
    return PULL_INFRACTION_CORRECTION_LABELS.getValue(this)
}
