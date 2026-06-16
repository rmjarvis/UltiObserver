package rmjarvis.ultiobserver

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
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
 * Setup-screen fields needed to create or edit a live game.
 *
 * @param startDate The local date selected for the scheduled game start.
 * @param startTime The local clock time selected for the scheduled game start.
 * @param timeZone The time zone that gives the local start date/time its real instant.
 * @param tournamentName Optional tournament name used in completed-game summaries.
 * @param division Optional division context for the game, hidden from summaries when not set.
 * @param gameContext Optional game-stage or round context, such as pool play or semifinals.
 * @param nearEndName Optional custom label for the near/bottom field end.
 * @param farEndName Optional custom label for the far/top field end.
 * @param rules The scoring, cap, halftime, and timeout rules selected for the game.
 * @param teamOne The setup identity for Team 1 before live field orientation is applied.
 * @param teamTwo The setup identity for Team 2 before live field orientation is applied.
 * @param teamOnePlayers Team 1 players with cards carried in from previous games.
 * @param teamTwoPlayers Team 2 players with cards carried in from previous games.
 * @param pullingTeam The team selected to pull first.
 * @param pullingFromEnd The field end from which the first pull is selected to start.
 * @param pullPromptTarget Which field end or ends should receive pulling prompts.
 */
@Serializable
data class GameSetupState(
    @Serializable(with = LocalDateAsStringSerializer::class)
    val startDate: LocalDate,
    @Serializable(with = LocalTimeAsStringSerializer::class)
    val startTime: LocalTime,
    @Serializable(with = ZoneIdAsStringSerializer::class)
    val timeZone: ZoneId,
    val tournamentName: String = "",
    val division: GameDivision? = null,
    val gameContext: String = "",
    val nearEndName: String = "",
    val farEndName: String = "",
    val rules: GameRules = GameRules(),
    val teamOne: TeamSetup = TeamSetup(name = "", color = TeamColorChoice.WHITE),
    val teamTwo: TeamSetup = TeamSetup(name = "", color = TeamColorChoice.BLUE),
    val teamOnePlayers: List<PlayerRecord> = emptyList(),
    val teamTwoPlayers: List<PlayerRecord> = emptyList(),
    val pullingTeam: TeamId = TeamId.TEAM_ONE,
    val pullingFromEnd: FieldEnd = FieldEnd.FAR,
    val pullPromptTarget: PullPromptTarget = PullPromptTarget.NEAR,
)

/**
 * Build the default setup state for a new game.
 *
 * @param now The reference local date-time for choosing the next half-hour start; injectable for tests.
 * @param rules The rules to prefill, usually defaults or the most recent game's rules.
 */
internal fun newGameSetupState(
    now: LocalDateTime = LocalDateTime.now(),
    rules: GameRules = GameRules(),
): GameSetupState {
    val startTime = nextHalfHourFrom(now.toLocalTime())
    val startDate = if (startTime.isBefore(now.toLocalTime())) {
        now.toLocalDate().plusDays(1)
    } else {
        now.toLocalDate()
    }
    return GameSetupState(
        startDate = startDate,
        startTime = startTime,
        timeZone = ZoneId.systemDefault(),
        rules = rules,
    )
}

/// Return whether this setup needs mixed-division rule options.
fun GameSetupState.usesMixedDivision(): Boolean {
    return division == GameDivision.MIXED
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

/**
 * One compact labeled row in the setup overview.
 *
 * @param label The short fixed-width label.
 * @param value The free-form text shown to the right of the label.
 */
internal data class LabeledSetupSummary(
    val label: String,
    val value: String,
)

/// Return compact coach and captain rows for the setup overview.
internal fun TeamSetup.namesSummary(): List<LabeledSetupSummary> {
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
        "${record.playerIdentity(compact = true)}: ${record.cardDetail()}"
    }
}

/// Return setup summary lines for optional game and tournament context.
internal fun GameSetupState.gameInformationSummaryLines(): List<String> {
    return listOfNotNull(
        tournamentName.trim().takeIf { it.isNotEmpty() },
        division?.setupSummaryLine(),
        gameContext.trim().takeIf { it.isNotEmpty() },
        formatStartDate(startDate),
        "Start at ${formatClockTime(startTime)}",
    )
}

/// Return the compact setup summary for the starting pull.
internal fun GameSetupState.startingPullSummary(): String {
    return "${pullingTeam.setupName(this)} pulls from ${fieldEndName(pullingFromEnd)}"
}

/// Return the setup summary line for the current pull-prompt preference.
internal fun GameSetupState.pullPromptSummary(): String {
    return "Pull prompts for ${pullPromptTarget.displayText(this)}"
}

/// Return the display label for one field end, falling back when no custom name is set.
internal fun GameSetupState.fieldEndName(end: FieldEnd): String {
    val customName = when (end) {
        FieldEnd.NEAR -> nearEndName
        FieldEnd.FAR -> farEndName
    }.trim()
    return customName.ifEmpty { end.defaultDisplayText() }
}

/**
 * Return a setup-display team name, using fallback labels only for display.
 *
 * @param state The setup state containing team names.
 */
internal fun TeamId.setupName(state: GameSetupState): String {
    val name = if (this == TeamId.TEAM_ONE) state.teamOne.name else state.teamTwo.name
    return name.ifBlank {
        if (this == TeamId.TEAM_ONE) "Team 1" else "Team 2"
    }
}

/// Return the stable setup field label for a team.
internal fun TeamId.setupFieldLabel(): String {
    return if (this == TeamId.TEAM_ONE) "Team 1" else "Team 2"
}

/**
 * Return the setup fields for a team.
 *
 * @param state The setup state containing both teams.
 */
internal fun TeamId.setupTeam(state: GameSetupState): TeamSetup {
    return if (this == TeamId.TEAM_ONE) state.teamOne else state.teamTwo
}

/**
 * Return setup state with one team's setup fields replaced.
 *
 * @param teamId The team to replace.
 * @param team The updated team setup fields.
 */
internal fun GameSetupState.withSetupTeam(teamId: TeamId, team: TeamSetup): GameSetupState {
    return if (teamId == TeamId.TEAM_ONE) copy(teamOne = team) else copy(teamTwo = team)
}

/// Return the player records for one setup team.
internal fun GameSetupState.playersFor(teamId: TeamId): List<PlayerRecord> {
    return if (teamId == TeamId.TEAM_ONE) teamOnePlayers else teamTwoPlayers
}

/**
 * Return this setup state with one team's player records replaced.
 *
 * @param teamId The team whose players should be replaced.
 * @param players The new player records for that team.
 */
internal fun GameSetupState.withPlayersFor(teamId: TeamId, players: List<PlayerRecord>): GameSetupState {
    return if (teamId == TeamId.TEAM_ONE) copy(teamOnePlayers = players) else copy(teamTwoPlayers = players)
}

/// Return the default user-facing text for a field end.
internal fun FieldEnd.defaultDisplayText(): String {
    return when (this) {
        FieldEnd.FAR -> "Far end"
        FieldEnd.NEAR -> "Near end"
    }
}

/// Return the setup-display text for a pull-prompt target.
internal fun PullPromptTarget.displayText(state: GameSetupState): String {
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
