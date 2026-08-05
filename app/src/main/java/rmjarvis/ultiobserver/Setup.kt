package rmjarvis.ultiobserver

import java.time.ZoneId
import kotlinx.serialization.Serializable

/// Mode describing which setup bucket the setup screen is editing.
@Serializable
internal enum class SetupMode {
    NEW_GAME,
    EDIT_CURRENT_GAME,
    EDIT_SAVED_SETUP,
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
 * @param now The phone-clock epoch millis used to choose the next official half-hour start.
 * @param officialClockOffsetMillis Offset from phone time to the official clock.
 * @param defaultsFrom Previous game values to carry forward for repeated tournament assignments.
 * @param defaultObserverName Profile observer name to use as the first observer for a new game.
 */
internal fun newSetupGameState(
    now: Long,
    officialClockOffsetMillis: Long = 0L,
    defaultsFrom: GameState? = null,
    defaultObserverName: String = "",
): GameState {
    val timeZone = ZoneId.systemDefault()
    val officialNow = now + officialClockOffsetMillis
    val officialLocalNow = localDateTimeFromEpoch(officialNow, timeZone)
    val startTime = nextHalfHourFrom(officialLocalNow.toLocalTime())
    val startDate = if (startTime.isBefore(officialLocalNow.toLocalTime())) {
        officialLocalNow.toLocalDate().plusDays(1)
    } else {
        officialLocalNow.toLocalDate()
    }
    return GameState(
        startDate = startDate,
        startTime = startTime,
        timeZone = timeZone,
        tournamentName = defaultsFrom?.tournamentName ?: "",
        division = defaultsFrom?.division,
        level = defaultsFrom?.level ?: "",
        gameContext = "",
        observerNames = defaultObserverName.trim()
            .takeIf { it.isNotEmpty() }
            ?.let { listOf(it) }
            ?: emptyList(),
        fieldName = "",
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
        openingPullingTeam = TeamId.TEAM_ONE,
        openingPullingFromEnd = FieldEnd.FAR,
        phase = GamePhase.SETUP,
        countdown = null,
        officialClockOffsetMillis = officialClockOffsetMillis,
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
        observerNames.observersDisplayText()?.let { "Observers: $it" },
        formatStartDate(startDate),
        "Start at ${formatClockTime(startTime)}",
        fieldName.trim().takeIf { it.isNotEmpty() }?.let { "Field: $it" },
    )
}

/// Return observer names trimmed and joined for display.
internal fun List<String>.observersDisplayText(): String? {
    return map { it.trim() }
        .filter { it.isNotEmpty() }
        .joinToString(", ")
        .takeIf { it.isNotEmpty() }
}

/// Return the compact setup summary for the starting pull.
internal fun GameState.startingPullSummary(preference: OrientationPreference): String {
    return "${openingPullingTeam.setupName(this)} pulls from " +
        fieldEndName(openingPullingFromEnd, preference)
}

/// Return the setup summary line for the current pull-prompt preference.
internal fun GameState.pullPromptSummary(preference: OrientationPreference): String {
    return "Pull prompts for ${pullPromptTarget.displayText(this, preference)}"
}

/// Return the display label for one field end, falling back when no custom name is set.
internal fun GameState.fieldEndName(
    end: FieldEnd,
    preference: OrientationPreference,
): String {
    val layout = when (preference) {
        OrientationPreference.PORTRAIT,
        OrientationPreference.AUTO_ROTATE -> ActiveGameOrientation.PORTRAIT
        OrientationPreference.LANDSCAPE -> ActiveGameOrientation.LANDSCAPE
    }
    return fieldEndName(end, layout)
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
internal fun FieldEnd.defaultDisplayText(preference: OrientationPreference): String {
    return when (preference) {
        OrientationPreference.PORTRAIT,
        OrientationPreference.AUTO_ROTATE -> defaultDisplayText(ActiveGameOrientation.PORTRAIT)
        OrientationPreference.LANDSCAPE -> defaultDisplayText(ActiveGameOrientation.LANDSCAPE)
    }
}

/// Return the default user-facing text for a field end in a concrete display layout.
internal fun FieldEnd.defaultDisplayText(layout: ActiveGameOrientation): String {
    return when (layout) {
        ActiveGameOrientation.PORTRAIT -> when (this) {
            FieldEnd.FAR -> "Far end"
            FieldEnd.NEAR -> "Near end"
        }
        ActiveGameOrientation.LANDSCAPE -> when (this) {
            FieldEnd.FAR -> "Left end"
            FieldEnd.NEAR -> "Right end"
        }
    }
}

/// Return the setup-display text for a pull-prompt target.
internal fun PullPromptTarget.displayText(
    state: GameState,
    preference: OrientationPreference,
): String {
    return when (this) {
        PullPromptTarget.NEAR -> state.fieldEndName(FieldEnd.NEAR, preference)
        PullPromptTarget.FAR -> state.fieldEndName(FieldEnd.FAR, preference)
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

/// Return the initial top field end that puts the observer's prompt end at the bottom.
internal fun PullPromptTarget.initialTopDisplayedEnd(): FieldEnd {
    return when (this) {
        PullPromptTarget.FAR -> FieldEnd.NEAR
        PullPromptTarget.NEAR,
        PullPromptTarget.BOTH,
        PullPromptTarget.NEITHER -> FieldEnd.FAR
    }
}
