package rmjarvis.ultiobserver

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder

// Absolute Long timestamps in the game model are Unix epoch milliseconds.

/// Serializer for LocalDate values as stable ISO-8601 strings in app-state JSON.
object LocalDateAsStringSerializer : KSerializer<LocalDate> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LocalDateAsString", PrimitiveKind.STRING)

    /**
     * Encode a local date as its ISO-8601 string representation.
     *
     * @param encoder The kotlinx.serialization encoder receiving the string.
     * @param value The local date to serialize.
     */
    override fun serialize(encoder: Encoder, value: LocalDate) {
        encoder.encodeString(value.toString())
    }

    /**
     * Decode a local date from its ISO-8601 string representation.
     *
     * @param decoder The kotlinx.serialization decoder providing the string.
     */
    override fun deserialize(decoder: Decoder): LocalDate {
        return LocalDate.parse(decoder.decodeString())
    }
}

/// Serializer for LocalTime values as stable ISO-8601 strings in app-state JSON.
object LocalTimeAsStringSerializer : KSerializer<LocalTime> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("LocalTimeAsString", PrimitiveKind.STRING)

    /**
     * Encode a local time as its ISO-8601 string representation.
     *
     * @param encoder The kotlinx.serialization encoder receiving the string.
     * @param value The local time to serialize.
     */
    override fun serialize(encoder: Encoder, value: LocalTime) {
        encoder.encodeString(value.toString())
    }

    /**
     * Decode a local time from its ISO-8601 string representation.
     *
     * @param decoder The kotlinx.serialization decoder providing the string.
     */
    override fun deserialize(decoder: Decoder): LocalTime {
        return LocalTime.parse(decoder.decodeString())
    }
}

/// Serializer for ZoneId values by their stable time-zone id in app-state JSON.
object ZoneIdAsStringSerializer : KSerializer<ZoneId> {
    override val descriptor: SerialDescriptor =
        PrimitiveSerialDescriptor("ZoneIdAsString", PrimitiveKind.STRING)

    /**
     * Encode a time zone by its stable zone id.
     *
     * @param encoder The kotlinx.serialization encoder receiving the zone id string.
     * @param value The zone id to serialize.
     */
    override fun serialize(encoder: Encoder, value: ZoneId) {
        encoder.encodeString(value.id)
    }

    /**
     * Decode a time zone from its stable zone id.
     *
     * @param decoder The kotlinx.serialization decoder providing the zone id string.
     */
    override fun deserialize(decoder: Decoder): ZoneId {
        return ZoneId.of(decoder.decodeString())
    }
}

/**
 * Convert local game start date/time fields into epoch millis.
 *
 * @param date The local calendar date selected in setup.
 * @param time The local clock time selected in setup.
 * @param timeZone The zone that gives the local date/time its real instant.
 */
internal fun epochTimestamp(date: LocalDate, time: LocalTime, timeZone: ZoneId): Long {
    return LocalDateTime.of(date, time)
        .atZone(timeZone)
        .toInstant()
        .toEpochMilli()
}

/**
 * Convert epoch millis into a local date-time in the game's time zone.
 *
 * @param epoch The epoch millis to convert.
 * @param timeZone The zone used to present the instant as local game time.
 */
internal fun localDateTimeFromEpoch(epoch: Long, timeZone: ZoneId): LocalDateTime {
    return LocalDateTime.ofInstant(
        Instant.ofEpochMilli(epoch),
        timeZone,
    )
}

/**
 * Convert epoch millis into a local clock time in the game's time zone.
 *
 * @param epoch The epoch millis to convert.
 * @param timeZone The zone used to present the instant as local game time.
 */
internal fun localTimeFromEpoch(epoch: Long, timeZone: ZoneId): LocalTime {
    return localDateTimeFromEpoch(epoch, timeZone).toLocalTime()
}

/// Identity of one of the two teams in setup and in-progress game state.
@Serializable
enum class TeamId {
    TEAM_ONE,
    TEAM_TWO;

    /// Return the other team identifier.
    fun flip(): TeamId {
        return if (this == TEAM_ONE) TEAM_TWO else TEAM_ONE
    }

    /// Return the default display name for this team.
    fun defaultName(): String {
        return if (this == TEAM_ONE) "Team 1" else "Team 2"
    }
}
/// Identity of the field end nearest or farthest from the observer.
@Serializable
enum class FieldEnd {
    NEAR,
    FAR;

    /// Return the opposite field end.
    fun flip(): FieldEnd {
        return if (this == NEAR) FAR else NEAR
    }
}

/// Observer preference for which field end should receive pulling prompts.
@Serializable
enum class PullPromptTarget {
    NEAR,
    FAR,
    BOTH,
    NEITHER,
}
/// Broad phase of a game.
@Serializable
enum class GamePhase {
    SETUP,
    PRE_GAME,
    BETWEEN_POINTS,
    LIVE_POINT,
    HALFTIME,
    GAME_OVER;

    /// Report whether this phase is before the next point has become live.
    val isBeforeLivePoint: Boolean
        get() = this == PRE_GAME || this == BETWEEN_POINTS
}

/**
 * Selectable team jersey color and the display colors that go with it.
 *
 * @param label The user-facing color name.
 * @param accentArgb The background color matching the nominal jersey color; ignored for CUSTOM.
 * @param contentArgb A text color with good contrast to the accent color; ignored for CUSTOM.
 */
@Serializable
enum class TeamColorChoice(
    val label: String,
    val accentArgb: Long,    // The background color matching the nominal jersey color.
    val contentArgb: Long,   // A text color with good contrast to the accent color.
) {
    WHITE("White", 0xFFF8F9FA, 0xFF1F1A17),
    BLACK("Black", 0xFF232220, 0xFFF6F2E8),
    RED("Red", 0xFFC23B2A, 0xFFFFF8F5),
    BLUE("Blue", 0xFF2A5CAA, 0xFFF7FAFF),
    GREEN("Green", 0xFF2E7D32, 0xFFF4FFF4),
    YELLOW("Yellow", 0xFFE7A51E, 0xFF2E2400),
    PINK("Pink", 0xFFFF4FA3, 0xFF2F1022),
    GRAY("Gray", 0xFF708090, 0xFFF7F8FA),
    CUSTOM("Custom", 0x00000000, 0x00000000),
}

/**
 * Gender-ratio pattern used for a mixed-division game.
 *
 * @param displayText User-facing rule label.
 */
@Serializable
enum class GenderRatioRule(val displayText: String) {
    ABBA("ABBA"),
    GEN_ZONE("Gen Zone"),
    OFFENSE_DECIDES("Offense Decides"),
    FIXED_4M_3W("4M/3W"),
    FIXED_4W_3M("4W/3M"),
    NA("N/A"),
}

/**
 * Concrete four/three gender ratio for a mixed point.
 *
 * @param displayText Compact user-facing ratio label.
 */
@Serializable
enum class GenderRatio(val displayText: String) {
    FOUR_MEN_THREE_WOMEN("4M/3W"),
    FOUR_WOMEN_THREE_MEN("4W/3M");

    /// Return the opposite four/three gender ratio.
    fun flip(): GenderRatio {
        return if (this == FOUR_MEN_THREE_WOMEN) FOUR_WOMEN_THREE_MEN else FOUR_MEN_THREE_WOMEN
    }
}
/**
 * State for one team, including setup fields and game counters.
 *
 * @param name The team name; blank means no explicit setup name yet.
 * @param color The active jersey color for this team.
 * @param customColorArgb Opaque ARGB value for a saved custom jersey color, or null when none
 * has been picked.
 * @param coaches Free-form coach name/details entered for this team.
 * @param fieldCaptains Free-form field-captain name/details entered for this team.
 * @param spiritCaptains Free-form spirit-captain name/details entered for this team.
 * @param score The team's current score.
 * @param timeoutsUsedThisHalf Number of timeouts this team has used in the current half.
 * @param firstHalfTimeoutsUsed Stored after halftime so floater-timeout carryover can be derived.
 * @param offsides Number of offsides infractions recorded for this team.
 * @param falseStarts Number of false-start infractions recorded for this team.
 * @param majorityPullViolations Number of majority-pull violations recorded for this team.
 * @param timeViolations Number of time violations recorded for this team.
 */
@Serializable
data class TeamState(
    val name: String,
    val color: TeamColorChoice,
    val customColorArgb: Long? = null,
    val coaches: String = "",
    val fieldCaptains: String = "",
    val spiritCaptains: String = "",
    val score: Int = 0,
    val timeoutsUsedThisHalf: Int = 0,
    val firstHalfTimeoutsUsed: Int = 0,
    val offsides: Int = 0,
    val falseStarts: Int = 0,
    val majorityPullViolations: Int = 0,
    val timeViolations: Int = 0,
    val technicalFouls: Int = 0,
    val blueCards: Int = 0,
) {
    init {
        require(color != TeamColorChoice.CUSTOM || customColorArgb != null) {
            "customColorArgb is required when color is CUSTOM."
        }
    }

    /// Return this team state with one additional timeout used in the current half.
    fun withAddedTimeout(): TeamState {
        return copy(timeoutsUsedThisHalf = timeoutsUsedThisHalf + 1)
    }

    /**
     * Return this team's name with its setup fallback applied.
     *
     * @param teamId The team id that supplies the side-specific fallback label.
     */
    fun normalizedName(teamId: TeamId): String {
        return name.ifBlank { teamId.defaultName() }
    }
}

/// Return whether this team has coach or captain details to show during the game.
internal fun TeamState.hasCoachOrCaptainInfo(): Boolean {
    return coaches.isNotBlank() || fieldCaptains.isNotBlank() || spiritCaptains.isNotBlank()
}

/// Count combined pull violations for display and consequence handling.
internal fun TeamState.pullViolationCount(): Int {
    return offsides + falseStarts + majorityPullViolations
}

/// Return teams ordered with the higher-scoring team first for summary display.
internal fun GameState.winnerFirstTeams(): List<TeamState> {
    val teamOneFirst = teamOne.score > teamTwo.score ||
        (teamOne.score == teamTwo.score && teamOne.name <= teamTwo.name)
    return if (teamOneFirst) {
        listOf(teamOne, teamTwo)
    } else {
        listOf(teamTwo, teamOne)
    }
}
/**
 * Offense-ready and pull deadlines for a pull-style countdown.
 *
 * @param offenseReadySeconds Seconds from countdown start until the receiving team must signal readiness.
 * @param pullSeconds Seconds from countdown start until the pulling team must pull.
 */
@Serializable
data class PullTimingSeconds(
    val offenseReadySeconds: Int,
    val pullSeconds: Int,
) {
    /// Return this timing's countdown length for one between-points target.
    fun durationSecondsFor(target: BetweenPointsCountdownTarget): Int {
        return when (target) {
            BetweenPointsCountdownTarget.OFFENSE_READY -> offenseReadySeconds
            BetweenPointsCountdownTarget.PULL,
            BetweenPointsCountdownTarget.BOTH,
            BetweenPointsCountdownTarget.NEITHER -> pullSeconds
        }
    }

    /// Return the countdown time for a cue that happens before offense readiness.
    fun remainingSecondsBeforeOffenseReady(secondsBeforeReady: Int, target: BetweenPointsCountdownTarget): Int {
        return when (target) {
            BetweenPointsCountdownTarget.OFFENSE_READY -> secondsBeforeReady
            BetweenPointsCountdownTarget.BOTH -> pullSeconds - offenseReadySeconds + secondsBeforeReady
            BetweenPointsCountdownTarget.PULL,
            BetweenPointsCountdownTarget.NEITHER -> error("Target $target does not include offense-ready cues.")
        }
    }
}

/**
 * Active countdown and the information needed to display, adjust, and transition it.
 *
 * @param kind The model meaning of this countdown.
 * @param label The short UI label shown next to the remaining time.
 * @param durationSeconds The original countdown length.
 * @param targetEpoch The epoch millis when the countdown reaches zero.
 * @param betweenPointsTarget The offense-ready or pull target for between-points countdowns.
 * @param pullTiming Offense-ready and pull deadlines for between-points-style countdowns.
 * @param pausedAtEpoch The epoch millis when the countdown was paused, or null while running.
 */
@Serializable
data class CountdownState(
    val kind: CountdownKind,
    val label: String,
    val durationSeconds: Int,       // Original countdown length.
    val targetEpoch: Long,          // Clock time when the countdown reaches zero.
    val betweenPointsTarget: BetweenPointsCountdownTarget? = null,
    val pullTiming: PullTimingSeconds? = null,
    val pausedAtEpoch: Long? = null,
) {
    /// Extend the active countdown, optionally replacing its normal pull timing.
    fun extendBy(
        seconds: Int,
        newPullTiming: PullTimingSeconds? = null,
    ): CountdownState {
        return copy(
            durationSeconds = durationSeconds + seconds,
            targetEpoch = targetEpoch + seconds * 1000L,
            pullTiming = newPullTiming ?: pullTiming,
        )
    }

    /// Replace the normal pull timing while preserving any separate countdown extension.
    fun withPullTiming(newPullTiming: PullTimingSeconds): CountdownState {
        val target = betweenPointsTarget!!
        val oldTiming = pullTiming!!
        val deltaSeconds = newPullTiming.durationSecondsFor(target) -
            oldTiming.durationSecondsFor(target)
        return extendBy(deltaSeconds, newPullTiming = newPullTiming)
    }

    /// Swap the countdown's offensive/defensive between-points target when the field responsibility flips.
    fun swapOD(): CountdownState {
        if (!kind.usesBetweenPointsTarget()) {
            return this
        }
        val currentTarget = betweenPointsTarget!!
        val newTarget = currentTarget.flip()
        val timing = pullTiming!!
        val deltaSeconds = timing.durationSecondsFor(newTarget) - timing.durationSecondsFor(currentTarget)
        return copy(
            label = newTarget.label,
            durationSeconds = durationSeconds + deltaSeconds,
            targetEpoch = targetEpoch + deltaSeconds * 1000L,
            betweenPointsTarget = newTarget,
        )
    }

    /// Return whether this countdown is currently paused.
    fun isPaused(): Boolean {
        return pausedAtEpoch != null
    }

    /**
     * Return the remaining countdown duration, freezing time at the pause point when paused.
     *
     * @param now The current epoch millis used for running countdowns.
     */
    fun remainingDuration(now: Long): Duration {
        val effectiveNow = pausedAtEpoch ?: now
        return Duration.ofMillis((targetEpoch - effectiveNow).coerceAtLeast(0L))
    }

    /**
     * Return a copy paused at the given time.
     *
     * @param now The epoch millis at which the observer paused the countdown.
     */
    fun pause(now: Long): CountdownState {
        return if (pausedAtEpoch == null) {
            copy(pausedAtEpoch = now)
        } else {
            // Duplicate UI callbacks can race with recomposition; keep the original pause point.
            this
        }
    }

    /**
     * Return a copy resumed at the given time, preserving the remaining countdown duration.
     *
     * @param now The epoch millis at which the observer resumed the countdown.
     */
    fun resume(now: Long): CountdownState {
        val pausedAt = pausedAtEpoch
        if (pausedAt == null) {
            // Duplicate UI callbacks can race with recomposition; keep the already-running countdown unchanged.
            return this
        }
        return copy(
            targetEpoch = targetEpoch + (now - pausedAt),
            pausedAtEpoch = null,
        )
    }
}
/// Model behavior attached to an active countdown.
@Serializable
enum class CountdownKind {
    OPENING_PULL,
    BETWEEN_POINTS,
    PULL_RESET,
    MISCONDUCT_BETWEEN_POINTS,
    DEFENSE_CHECK,
    TIME_OUT,
    HALFTIME;

    /// Report whether this countdown kind depends on the between-points target side.
    fun usesBetweenPointsTarget(): Boolean {
        return this == OPENING_PULL || this == BETWEEN_POINTS || this == PULL_RESET
    }
}

/**
 * Complete mutable state of one setup/live/completed game.
 *
 * @param endEpoch Epoch millis when the game ended, or null while active.
 * @param tournamentName Optional tournament name used in completed-game summaries.
 * @param division Optional division context for the game.
 * @param level Optional competition level for the game.
 * @param gameContext Optional game-stage or round context, such as pool play or semifinals.
 * @param observerNames Optional observer names assigned to the game.
 * @param fieldName Optional field name or number where the game is scheduled.
 * @param nearEndName Optional custom label for the near field end.
 * @param farEndName Optional custom label for the far field end.
 * @param pullingTeam The team currently pulling.
 * @param pullingFromEnd The field end occupied by the pulling team.
 * @param topDisplayedEnd The field end shown at the top of the live field display.
 * @param pullPromptTarget Which field end or ends should receive pulling prompts.
 * @param initialGenderRatio The first ABBA point's gender ratio.
 * @param firstHalfGenZone The Gen Zone end used for the first half.
 * @param openingPullingTeam The team that pulled to start the game.
 * @param openingPullingFromEnd The field end used by the opening pull.
 * @param teamOnePlayers Team 1 known player records, including prior-card details and in-game cards.
 * @param teamTwoPlayers Team 2 known player records, including prior-card details and in-game cards.
 * @param eventLog Persisted log of significant game events and manual corrections.
 * @param halftimeHighScore Higher score when halftime began, used for the second water break.
 * @param pendingWaterBreakOffer Whether an automatic water-break offer is pending.
 * @param pendingCapOffer The cap currently being offered to the observer for yes/no application.
 */
@Serializable
data class GameState(
    @Serializable(with = LocalDateAsStringSerializer::class)
    val startDate: LocalDate,
    @Serializable(with = LocalTimeAsStringSerializer::class)
    val startTime: LocalTime,
    @Serializable(with = ZoneIdAsStringSerializer::class)
    val timeZone: ZoneId,
    val endEpoch: Long? = null,
    val tournamentName: String,
    val division: GameDivision? = null,
    val level: String = "",
    val gameContext: String = "",
    val observerNames: List<String> = emptyList(),
    val fieldName: String = "",
    val nearEndName: String = "",
    val farEndName: String = "",
    val rules: GameRules,
    val teamOne: TeamState,
    val teamTwo: TeamState,
    val teamOnePlayers: List<PlayerRecord>,
    val teamTwoPlayers: List<PlayerRecord>,
    val eventLog: List<EventLogEntry> = emptyList(),
    val pullingTeam: TeamId,
    val pullingFromEnd: FieldEnd,
    val topDisplayedEnd: FieldEnd = FieldEnd.FAR,
    val pullPromptTarget: PullPromptTarget = PullPromptTarget.NEAR,
    val initialGenderRatio: GenderRatio = GenderRatio.FOUR_MEN_THREE_WOMEN,
    val firstHalfGenZone: FieldEnd = FieldEnd.FAR,
    val openingPullingTeam: TeamId,
    val openingPullingFromEnd: FieldEnd,
    val phase: GamePhase,
    val countdown: CountdownState?,
    val pullSequenceOffsidesRecorded: Boolean = false,
    val pullSequenceFalseStartRecorded: Boolean = false,
    val pullSkippedForCurrentPoint: Boolean = false,
    val pendingMisconductCountdown: Boolean = false,
    val halftimeTaken: Boolean = false,
    val halftimeTargetScore: Int? = null,
    val halftimeHighScore: Int? = null,
    val pendingWaterBreakOffer: Boolean = false,
    val winningScore: Int? = null,
    val halfCapApplied: Boolean = false,
    val softCapApplied: Boolean = false,
    val hardCapApplied: Boolean = false,
    val pendingCapOffer: CapType? = null,  // Set when asking whether to apply the next cap
    val undoEntry: UndoEntry? = null,
    val redoEntry: GameState? = null,
) {
    /// Epoch millis for the scheduled game start.
    val startEpoch: Long
        get() = epochTimestamp(startDate, startTime, timeZone)

    /// Report whether game events have started after setup.
    fun hasStarted(): Boolean {
        return phase != GamePhase.SETUP &&
            (phase != GamePhase.PRE_GAME || eventLog.isNotEmpty())
    }

    /**
     * Drop undo/redo state, optionally keeping the immediate end-game undo for archived games.
     *
     * @param clearCountdown Whether to clear an active countdown from the returned state.
     */
    fun pruneUndoHistory(clearCountdown: Boolean = true): GameState {
        val prunedUndoEntry = undoEntry
            ?.takeIf {
                it.label == "Undo End game" ||
                    it.label == HEAT_LEVEL_THREE_UNDO_LABEL ||
                    it.label == AQI_LEVEL_THREE_UNDO_LABEL
            }
            ?.let { entry ->
                UndoEntry(
                    label = entry.label,
                    previous = entry.previous.pruneUndoHistory(clearCountdown = clearCountdown),
                )
            }
        return copy(
            countdown = if (clearCountdown) null else countdown,
            undoEntry = prunedUndoEntry,
            redoEntry = null,
        )
    }

    /**
     * Return the team state for a team id.
     *
     * @param team The team whose state should be returned.
     */
    fun teamFor(team: TeamId): TeamState {
        return if (team == TeamId.TEAM_ONE) teamOne else teamTwo
    }

    /**
     * Return the current display name for a team.
     *
     * @param team The team whose name should be returned.
     */
    fun teamName(team: TeamId): String {
        return teamFor(team).name
    }

    /**
     * Return the team currently defending a field end.
     *
     * @param end The end whose defending team should be returned.
     */
    fun teamDefendingEnd(end: FieldEnd): TeamId {
        return if (end == pullingFromEnd) pullingTeam else pullingTeam.flip()
    }

    /// Return the matchup/score summary line for compact game list rows.
    fun gameListSummaryLine(): String {
        return if (phase == GamePhase.SETUP) {
            val matchup = "${teamOne.normalizedName(TeamId.TEAM_ONE)} vs " +
                teamTwo.normalizedName(TeamId.TEAM_TWO)
            val field = setupDraftFieldText()
            if (field == null) matchup else "$matchup on $field"
        } else {
            "${teamOne.name} ${teamOne.score} - ${teamTwo.score} ${teamTwo.name}"
        }
    }

    /**
     * Return a field-end name from game state, falling back to the default label.
     *
     * @param end The end whose name should be returned.
     */
    internal fun fieldEndName(
        end: FieldEnd,
        layout: ActiveGameOrientation,
    ): String {
        val customName = when (end) {
            FieldEnd.NEAR -> nearEndName
            FieldEnd.FAR -> farEndName
        }.trim()
        return customName.ifEmpty { end.defaultDisplayText(layout) }
    }

    /// Flip only which field end appears at the top of the live field display.
    fun flipFieldDisplay(): GameState {
        return this.copy(
            topDisplayedEnd = this.topDisplayedEnd.flip(),
        ).withUndo(this, "Undo Flip field display")
    }

    /**
     * Return this state with an updated pull-prompt target.
     *
     * @param target The field end or ends that should receive pull timing prompts.
     */
    fun withPullPromptTarget(target: PullPromptTarget): GameState {
        return if (target == pullPromptTarget) {
            this
        } else {
            val updatedCountdown = countdown?.withPullPromptTarget(
                pullingFromEnd = pullingFromEnd,
                promptTarget = target,
            )
            copy(
                pullPromptTarget = target,
                countdown = updatedCountdown,
            ).withUndo(this, "Undo Change pull prompts")
        }
    }

    /// Swap the pulling team while leaving the teams' attacking orientation otherwise intact.
    fun swapPullingTeam(): GameState {
        val newPullingTeam = this.pullingTeam.flip()
        val newPullingFromEnd = this.pullingFromEnd.flip()
        return this.copy(
            pullingTeam = newPullingTeam,
            pullingFromEnd = newPullingFromEnd,
            countdown = this.countdown?.swapOD(),
            pullSequenceOffsidesRecorded = false,
            pullSequenceFalseStartRecorded = false,
        ).withUndo(this, "Undo Swap pulling team")
    }

    /**
     * Add or subtract seconds from the active countdown.
     *
     * @param seconds The signed countdown adjustment; negative values move the target earlier.
     */
    fun addTimeToCountdown(seconds: Int): GameState {
        val countdown = this.countdown ?: return this
        return this.copy(
            countdown = countdown.copy(targetEpoch = countdown.targetEpoch + seconds * 1000L),
        )
    }

    /**
     * Pause or resume the active countdown while preserving the displayed remaining time.
     *
     * @param now The epoch millis when the observer toggled the countdown.
     */
    fun toggleCountdownPaused(now: Long): GameState {
        val countdown = this.countdown ?: return this
        val updatedCountdown = if (countdown.isPaused()) {
            countdown.resume(now)
        } else {
            countdown.pause(now)
        }
        return copy(
            countdown = updatedCountdown,
        )
    }

    /**
     * Replace the recorded score as a manual correction.
     *
     * @param teamOneScore The corrected team-one score, clamped at zero.
     * @param teamTwoScore The corrected team-two score, clamped at zero.
     */
    fun adjustScore(teamOneScore: Int, teamTwoScore: Int, now: Long): GameState {
        val adjustedTeamOneScore = teamOneScore.coerceAtLeast(0)
        val adjustedTeamTwoScore = teamTwoScore.coerceAtLeast(0)
        if (
            adjustedTeamOneScore == this.teamOne.score &&
            adjustedTeamTwoScore == this.teamTwo.score
        ) {
            return this
        }
        val entries = listOf(
            EventLogEntry(
                timestampEpoch = now,
                type = EventLogType.SCORE_ADJUSTED,
                teamOneScore = adjustedTeamOneScore,
                teamTwoScore = adjustedTeamTwoScore,
            )
        )
        return this.copy(
            teamOne = this.teamOne.copy(score = adjustedTeamOneScore),
            teamTwo = this.teamTwo.copy(score = adjustedTeamTwoScore),
        ).withEventLogEntries(entries).withUndo(this, "Undo Score adjustment")
    }
}

private fun GameState.setupDraftFieldText(): String? {
    val field = fieldName.trim()
    if (field.isEmpty()) {
        return null
    }
    return if (field.contains("field", ignoreCase = true)) field else "field $field"
}

/**
 * Apply edited setup fields to an existing current game.
 * This is the model-side return path from the update-game setup editor.
 *
 * Not all edits should be applied to the existing state.
 * We need to be especially careful with an existing countdown to keep that running as it
 * was, but apply the appropriate changes to the duration if necessary.
 *
 * @param existing The current-game state being edited.
 * @param edited The setup-edited game state returned by the update-game form.
 * @param now The epoch millis for rebuilding the opening-pull countdown when its orientation
 * changes.
 */
fun applySetupEditToActiveGame(
    existing: GameState,
    edited: GameState,
    now: Long,
): GameState {
    if (edited.hasSameSetupFieldsAs(existing)) {
        return existing
    }

    // First make sure the names are normalized properly.
    var updatedState = edited.copy(
        teamOne = edited.teamOne.copy(
            name = edited.teamOne.normalizedName(TeamId.TEAM_ONE),
        ),
        teamTwo = edited.teamTwo.copy(
            name = edited.teamTwo.normalizedName(TeamId.TEAM_TWO),
        ),
    )

    // Only change the current pulling team and end if we are still in the pre-game phase.
    if (existing.phase == GamePhase.PRE_GAME) {
        updatedState = updatedState.copy(
            pullingTeam = edited.openingPullingTeam,
            pullingFromEnd = edited.openingPullingFromEnd,
        ).startPullSequence(now, phase = GamePhase.PRE_GAME)
    }

    // For an already started game, be careful about any countdown we might already have.
    // We don't want to change the existing pulling team or end like we did for pre-game.
    // But we do want to update the prompt target and possibly the duration.
    if (existing.phase == GamePhase.BETWEEN_POINTS) {
        // Change the prompt target if necessary
        var countdown = existing.countdown?.withPullPromptTarget(
            pullingFromEnd = existing.pullingFromEnd,
            promptTarget = edited.pullPromptTarget,
        )
        // Adjust the pull timing if necessary
        if (countdown?.kind == CountdownKind.BETWEEN_POINTS) {
            countdown = countdown.withPullTiming(edited.rules.standardPullTiming())
        }
        updatedState = updatedState.copy(countdown = countdown)
    }

    // This entire update is undoable as a single item.
    return updatedState.withUndo(existing, "Undo Update game setup")
}

/// Return whether two game states have identical setup-editable fields.
private fun GameState.hasSameSetupFieldsAs(other: GameState): Boolean {
    return startDate == other.startDate &&
        startTime == other.startTime &&
        timeZone == other.timeZone &&
        tournamentName == other.tournamentName &&
        division == other.division &&
        level == other.level &&
        gameContext == other.gameContext &&
        observerNames == other.observerNames &&
        fieldName == other.fieldName &&
        nearEndName == other.nearEndName &&
        farEndName == other.farEndName &&
        rules == other.rules &&
        teamOne.hasSameSetupFieldsAs(other.teamOne) &&
        teamTwo.hasSameSetupFieldsAs(other.teamTwo) &&
        teamOnePlayers == other.teamOnePlayers &&
        teamTwoPlayers == other.teamTwoPlayers &&
        pullPromptTarget == other.pullPromptTarget &&
        initialGenderRatio == other.initialGenderRatio &&
        firstHalfGenZone == other.firstHalfGenZone &&
        openingPullingTeam == other.openingPullingTeam &&
        openingPullingFromEnd == other.openingPullingFromEnd
}

/// Return whether two team states have identical setup-editable fields.
private fun TeamState.hasSameSetupFieldsAs(other: TeamState): Boolean {
    return name == other.name &&
        color == other.color &&
        customColorArgb == other.customColorArgb &&
        coaches == other.coaches &&
        fieldCaptains == other.fieldCaptains &&
        spiritCaptains == other.spiritCaptains
}


/**
 * Undo label and previous state for a reversible current-game action.
 *
 * @param label The user-facing undo button label.
 * @param previous The current-game state restored by undoing the action.
 */
@Serializable
data class UndoEntry(
    val label: String,
    val previous: GameState,
)
