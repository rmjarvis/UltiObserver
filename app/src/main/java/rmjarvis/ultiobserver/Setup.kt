package rmjarvis.ultiobserver

import java.time.ZoneId
import kotlinx.serialization.Serializable

/// Mode describing whether setup is creating a new game or editing the current one.
@Serializable
internal enum class SetupMode {
    NEW_GAME,
    EDIT_CURRENT_GAME,
}

/**
 * Optional competition division context entered during setup.
 *
 * @param displayText User-facing division label.
 */
@Serializable
enum class GameDivision(val displayText: String) {
    OPEN("Open"),
    WOMENS("Women’s"),
    MIXED("Mixed");

    /// Return the setup-summary line for this division.
    fun setupSummaryLine(): String {
        return "$displayText Division"
    }
}

/**
 * Build a setup-phase game state for a new game.
 *
 * @param now The reference epoch millis for choosing the next half-hour start.
 * @param defaultsFrom Previous game values to carry forward for repeated tournament assignments.
 */
internal fun newSetupGameState(
    now: Long,
    defaultsFrom: GameState? = null,
): GameState {
    val timeZone = ZoneId.systemDefault()
    val localNow = localDateTimeFromEpoch(now, timeZone)
    val startTime = nextHalfHourFrom(localNow.toLocalTime())
    val startDate = if (startTime.isBefore(localNow.toLocalTime())) {
        localNow.toLocalDate().plusDays(1)
    } else {
        localNow.toLocalDate()
    }
    return GameState(
        startDate = startDate,
        startTime = startTime,
        timeZone = timeZone,
        tournamentName = defaultsFrom?.tournamentName ?: "",
        division = defaultsFrom?.division,
        level = defaultsFrom?.level ?: "",
        gameContext = defaultsFrom?.gameContext ?: "",
        observers = defaultsFrom?.observers ?: "",
        fieldName = defaultsFrom?.fieldName ?: "",
        rules = defaultsFrom?.rules ?: GameRules(),
        teamOne = TeamState(name = "", color = TeamColorChoice.WHITE),
        teamTwo = TeamState(name = "", color = TeamColorChoice.BLUE),
        teamOnePlayers = emptyList(),
        teamTwoPlayers = emptyList(),
        pullingTeam = TeamId.TEAM_ONE,
        pullingFromEnd = FieldEnd.FAR,
        pullPromptTarget = PullPromptTarget.NEAR,
        initialGenderRatio = GenderRatio.FOUR_MEN_THREE_WOMEN,
        firstHalfGenZone = FieldEnd.FAR,
        switchGenZoneAtHalftime = true,
        openingPullingTeam = TeamId.TEAM_ONE,
        openingPullingFromEnd = FieldEnd.FAR,
        phase = GamePhase.SETUP,
        countdown = null,
    )
}

/// Return division choices with real divisions first and N/A last so it is most likely to wrap.
internal fun orderedSetupDivisions(): List<GameDivision?> {
    return listOf(
        GameDivision.OPEN,
        GameDivision.WOMENS,
        GameDivision.MIXED,
        null,
    )
}

/// Return preset game-level labels offered during setup.
internal fun setupLevelPresets(): List<String> {
    return listOf(
        "Youth",
        "College",
        "Club",
        "Masters",
        "Grandmasters",
        "Great Grandmasters",
        "Legends",
    )
}

/**
 * One compact labeled row in the setup overview.
 *
 * @param label The short fixed-width label.
 * @param value The free-form text shown to the right of the label.
 */
internal class LabeledSetupSummary(
    val label: String,
    val value: String,
)

/// Return compact coach and captain rows for the setup overview.
internal fun TeamState.namesSummary(): List<LabeledSetupSummary> {
    return listOfNotNull(
        coaches.compactLabeledSummary("Coach:"),
        fieldCaptains.compactLabeledSummary("Field:"),
        spiritCaptains.compactLabeledSummary("Spirit:"),
    )
}

/**
 * Return compact labeled text for display in a two-column summary row.
 *
 * @param label The short label shown before the first line.
 */
private fun String.compactLabeledSummary(label: String): LabeledSetupSummary? {
    val lines = trim()
        .lines()
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    if (lines.isEmpty()) {
        return null
    }
    return LabeledSetupSummary(label = label, value = lines.joinToString("\n"))
}

/// Return compact prior-card text for one team in the setup overview.
internal fun List<PlayerRecord>.teamPriorCardsSummary(): String {
    return joinToString("\n") { record ->
        "${record.playerIdentity(compact = true)}: ${record.cardDetail(compact = true)}"
    }
}

/// Return setup summary lines for optional game and tournament context.
internal fun GameState.gameInformationSummaryLines(): List<String> {
    return listOfNotNull(
        tournamentName.trim().takeIf { it.isNotEmpty() },
        division?.setupSummaryLine(),
        level.trim().takeIf { it.isNotEmpty() },
        gameContext.trim().takeIf { it.isNotEmpty() },
        observers.trim().takeIf { it.isNotEmpty() }?.let { "Observers: $it" },
        formatStartDate(startDate),
        "Start at ${formatClockTime(startTime)}",
        fieldName.trim().takeIf { it.isNotEmpty() }?.let { "Field: $it" },
    )
}

/// Return the compact setup summary for the starting pull.
internal fun GameState.startingPullSummary(): String {
    return "${openingPullingTeam.setupName(this)} pulls from ${fieldEndName(openingPullingFromEnd)}"
}

/// Return the setup summary line for the current pull-prompt preference.
internal fun GameState.pullPromptSummary(): String {
    return "Pull prompts for ${pullPromptTarget.displayText(this)}"
}

/// Return the display label for one field end, falling back when no custom name is set.
internal fun GameState.fieldEndName(end: FieldEnd): String {
    val customName = when (end) {
        FieldEnd.NEAR -> nearEndName
        FieldEnd.FAR -> farEndName
    }.trim()
    return customName.ifEmpty { end.defaultDisplayText() }
}

/**
 * Return a setup-display team name, using fallback labels only for display.
 *
 * @param state The game state containing team names.
 */
internal fun TeamId.setupName(state: GameState): String {
    val team = if (this == TeamId.TEAM_ONE) state.teamOne else state.teamTwo
    return team.normalizedName(this)
}

/// Return the player records for one setup team.
internal fun GameState.playersFor(teamId: TeamId): List<PlayerRecord> {
    return if (teamId == TeamId.TEAM_ONE) teamOnePlayers else teamTwoPlayers
}

/**
 * Return this game state with one team's player records replaced.
 *
 * @param teamId The team whose players should be replaced.
 * @param players The new player records for that team.
 */
internal fun GameState.withPlayersFor(
    teamId: TeamId,
    players: List<PlayerRecord>,
): GameState {
    return if (teamId == TeamId.TEAM_ONE) {
        copy(teamOnePlayers = players)
    } else {
        copy(teamTwoPlayers = players)
    }
}

/// Return the default user-facing text for a field end.
internal fun FieldEnd.defaultDisplayText(): String {
    return when (this) {
        FieldEnd.FAR -> "Far end"
        FieldEnd.NEAR -> "Near end"
    }
}

/// Return the setup-display text for a pull-prompt target.
internal fun PullPromptTarget.displayText(state: GameState): String {
    return when (this) {
        PullPromptTarget.NEAR -> state.fieldEndName(FieldEnd.NEAR)
        PullPromptTarget.FAR -> state.fieldEndName(FieldEnd.FAR)
        PullPromptTarget.BOTH -> "both ends"
        PullPromptTarget.NEITHER -> "neither end"
    }
}

/**
 * Return the setup-display text for one pull-prompt target choice.
 *
 * @param nearLabel The display label for the near field end.
 * @param farLabel The display label for the far field end.
 */
internal fun PullPromptTarget.choiceLabel(nearLabel: String, farLabel: String): String {
    return when (this) {
        PullPromptTarget.NEAR -> nearLabel
        PullPromptTarget.FAR -> farLabel
        PullPromptTarget.BOTH -> "Both"
        PullPromptTarget.NEITHER -> "Neither"
    }
}

/// Return the compact half/soft/hard cap summary.
internal fun GameRules.capRulesSummary(): String {
    return "${capSummary(useHalfCap, halfCapMinutes)}/" +
        "${capSummary(useSoftCap, softCapMinutes)}/" +
        capSummary(useHardCap, hardCapMinutes)
}

/**
 * Return the compact display for one cap rule.
 *
 * @param enabled Whether the cap is enabled.
 * @param minutes The cap offset in minutes when enabled.
 */
private fun capSummary(enabled: Boolean, minutes: Int): String {
    return if (enabled) "+$minutes" else "-"
}

/// Format timeout rules for setup display.
internal fun GameRules.formatTimeoutRules(): String {
    return buildString {
        append("$timeoutsPerHalf/half")
        if (hasFloaterTimeout) {
            append(" + floater")
        }
    }
}
