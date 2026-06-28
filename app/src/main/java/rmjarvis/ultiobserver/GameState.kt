package rmjarvis.ultiobserver

import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs
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

/// Identity of one of the two teams in setup and live game state.
@Serializable
enum class TeamId {
    TEAM_ONE,
    TEAM_TWO;

    /// Return the other team identifier.
    fun flip(): TeamId {
        return if (this == TEAM_ONE) TEAM_TWO else TEAM_ONE
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
 * Setup-screen team identity fields before a live game starts.
 *
 * @param name The team name entered in setup; blank means no explicit name yet.
 * @param color The active jersey color for this team.
 * @param customColorArgb Opaque ARGB value for a saved custom jersey color, or null when none
 * has been picked.
 * @param coaches Free-form coach name/details entered for this team.
 * @param fieldCaptains Free-form field-captain name/details entered for this team.
 * @param spiritCaptains Free-form spirit-captain name/details entered for this team.
 */
@Serializable
data class TeamSetup(
    val name: String,
    val color: TeamColorChoice,
    val customColorArgb: Long? = null,
    val coaches: String = "",
    val fieldCaptains: String = "",
    val spiritCaptains: String = "",
) {
    init {
        require(color != TeamColorChoice.CUSTOM || customColorArgb != null) {
            "customColorArgb is required when color is CUSTOM."
        }
    }
}
/// Configurable rules that affect scoring, caps, halftime, and timeout allowances.
@Serializable
data class GameRules(
    val gameTo: Int = 15,
    val halftimeMinutes: Int = 7,
    val useHalfCap: Boolean = true,
    val halfCapMinutes: Int = 45,
    val useSoftCap: Boolean = true,
    val softCapMinutes: Int = 90,
    val useHardCap: Boolean = true,
    val hardCapMinutes: Int = 105,
    val timeoutsPerHalf: Int = 2,
    val hasFloaterTimeout: Boolean = false,
    val genderRatioRule: GenderRatioRule = GenderRatioRule.ABBA,
    val useMajorityPullRule: Boolean = true,
)
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
 * Live counters and display identity for one team.
 *
 * @param name The team display name used during live and summary screens.
 * @param color The active jersey color for this team.
 * @param customColorArgb Opaque ARGB value for a saved custom jersey color, or null when none has been picked.
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
data class TeamLiveState(
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
    fun withAddedTimeout(): TeamLiveState {
        return copy(timeoutsUsedThisHalf = timeoutsUsedThisHalf + 1)
    }
}

/// Return whether this team has coach or captain details to show during the game.
internal fun TeamLiveState.hasCoachOrCaptainInfo(): Boolean {
    return coaches.isNotBlank() || fieldCaptains.isNotBlank() || spiritCaptains.isNotBlank()
}

/// Count combined offsides and false-start pull violations for display.
internal fun TeamLiveState.pullViolationCount(): Int {
    return offsides + falseStarts + majorityPullViolations
}

/// Return teams ordered with the higher-scoring team first for summary display.
internal fun GameState.winnerFirstTeams(): List<TeamLiveState> {
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
 * @param startEpoch Epoch millis for the scheduled game start.
 * @param endEpoch Epoch millis when the game ended, or null while active.
 * @param tournamentName Optional tournament name used in completed-game summaries.
 * @param division Optional division context for the game.
 * @param level Optional competition level for the game.
 * @param gameContext Optional game-stage or round context, such as pool play or semifinals.
 * @param observers Optional list of observers assigned to the game.
 * @param nearEndName Optional custom label for the near field end.
 * @param farEndName Optional custom label for the far field end.
 * @param nearAttackingTeam The team attacking the observer's near end.
 * @param pullingFromEnd The field end occupied by the pulling team.
 * @param topDisplayedEnd The field end shown at the top of the live field display.
 * @param pullPromptTarget Which field end or ends should receive pulling prompts.
 * @param initialGenderRatio The first ABBA point's gender ratio.
 * @param firstHalfGenZone The Gen Zone end used for the first half.
 * @param switchGenZoneAtHalftime Whether Gen Zone switches ends after halftime.
 * @param openingPullingTeam The team that pulled to start the game.
 * @param openingPullingFromEnd The field end used by the opening pull.
 * @param teamOnePlayers Team 1 known player records, including prior-card details and in-game cards.
 * @param teamTwoPlayers Team 2 known player records, including prior-card details and in-game cards.
 * @param eventLog Persisted log of significant game events and manual corrections.
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
    val startEpoch: Long,
    val endEpoch: Long? = null,
    val tournamentName: String = "",
    val division: GameDivision? = null,
    val level: String = "",
    val gameContext: String = "",
    val observers: String = "",
    val nearEndName: String = "",
    val farEndName: String = "",
    val rules: GameRules,
    val teamOne: TeamLiveState,
    val teamTwo: TeamLiveState,
    val teamOnePlayers: List<PlayerRecord>,
    val teamTwoPlayers: List<PlayerRecord>,
    val eventLog: List<EventLogEntry> = emptyList(),
    val nearAttackingTeam: TeamId,
    val pullingTeam: TeamId,
    val pullingFromEnd: FieldEnd,
    val topDisplayedEnd: FieldEnd = FieldEnd.FAR,
    val pullPromptTarget: PullPromptTarget = PullPromptTarget.NEAR,
    val initialGenderRatio: GenderRatio = GenderRatio.FOUR_MEN_THREE_WOMEN,
    val firstHalfGenZone: FieldEnd = FieldEnd.FAR,
    val switchGenZoneAtHalftime: Boolean = true,
    val openingPullingTeam: TeamId,
    val openingPullingFromEnd: FieldEnd,
    val phase: GamePhase,
    val countdown: CountdownState?,
    val pullCountdownExpired: Boolean = false,
    val pullSequenceOffsidesRecorded: Boolean = false,
    val pullSequenceFalseStartRecorded: Boolean = false,
    val pullSkippedForCurrentPoint: Boolean = false,
    val pendingMisconductCountdown: Boolean = false,
    val halftimeTaken: Boolean = false,
    val halftimeTargetScore: Int? = null,
    val winningScore: Int? = null,
    val halfCapApplied: Boolean = false,
    val softCapApplied: Boolean = false,
    val hardCapApplied: Boolean = false,
    val pendingCapOffer: CapType? = null,  // Set when asking whether to apply the next cap
    val undoEntry: UndoEntry? = null,
    val redoEntry: GameState? = null,
    val lastEvent: String = "Pregame setup complete.",
) {
    /// Report whether this state is still before the opening pull has started.
    fun isInitialLivePreview(): Boolean {
        return phase == GamePhase.PRE_GAME && eventLog.isEmpty()
    }

    /**
     * Drop undo/redo state, optionally keeping the immediate end-game undo for archived summaries.
     *
     * @param clearCountdown Whether to clear an active countdown from the returned state.
     */
    fun pruneUndoHistory(clearCountdown: Boolean = true): GameState {
        val prunedUndoEntry = undoEntry
            ?.takeIf { it.label == "Undo End game" }
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
     * Return the live team state for a team id.
     *
     * @param team The team whose live state should be returned.
     */
    fun teamFor(team: TeamId): TeamLiveState {
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
     * Return a field-end name from game state, falling back to the default label.
     *
     * @param end The end whose display name should be returned.
     */
    fun fieldEndDisplayName(end: FieldEnd): String {
        val customName = when (end) {
            FieldEnd.NEAR -> nearEndName
            FieldEnd.FAR -> farEndName
        }.trim()
        return customName.ifEmpty { end.defaultDisplayText() }
    }

    /// Flip only which field end appears at the top of the live field display.
    fun flipFieldDisplay(): GameState {
        return this.copy(
            topDisplayedEnd = this.topDisplayedEnd.flip(),
            lastEvent = "Field display flipped.",
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
                lastEvent = "Pull prompts changed.",
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
            lastEvent = "Pulling team swapped.",
        ).withUndo(this, "Undo Swap pulling team")
    }

    /**
     * Add or subtract seconds from the active countdown.
     *
     * @param seconds The signed countdown adjustment; negative values move the target earlier.
     */
    fun addTimeToCountdown(seconds: Int): GameState {
        val countdown = this.countdown ?: return this
        val sign = if (seconds < 0) "-" else ""
        val absoluteSeconds = abs(seconds)
        return this.copy(
            countdown = countdown.copy(targetEpoch = countdown.targetEpoch + seconds * 1000L),
            lastEvent = "Adjusted timer by $sign${absoluteSeconds / 60}:" +
                "${(absoluteSeconds % 60).toString().padStart(2, '0')}.",
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
            lastEvent = if (updatedCountdown.isPaused()) "Timer paused." else "Timer resumed.",
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
            lastEvent = "Score adjusted.",
        ).withEventLogEntries(entries).withUndo(this, "Undo Score adjustment")
    }
}

/**
 * Apply an edited setup form to an existing live game.
 * This is the model-side return path from the live-game setup editor.
 *
 * @param existing The live state currently being edited.
 * @param setup The setup values returned by the update-game form.
 * @param now The epoch millis for rebuilding the opening-pull countdown when its orientation
 * changes.
 */
fun applySetupToLiveGame(
    existing: GameState,
    setup: GameSetupState,
    now: Long,
): GameState {
    if (setup == existing.toSetupState()) {
        return existing
    }

    val openingNearAttackingTeam = if (setup.pullingFromEnd == FieldEnd.FAR) {
        setup.pullingTeam
    } else {
        setup.pullingTeam.flip()
    }
    val shouldResyncOpeningPullState = existing.phase == GamePhase.PRE_GAME

    val base = existing.copy(
        startDate = setup.startDate,
        startTime = setup.startTime,
        timeZone = setup.timeZone,
        startEpoch = epochTimestamp(setup.startDate, setup.startTime, setup.timeZone),
        tournamentName = setup.tournamentName,
        division = setup.division,
        level = setup.level,
        gameContext = setup.gameContext,
        observers = setup.observers,
        nearEndName = setup.nearEndName,
        farEndName = setup.farEndName,
        rules = setup.rules,
        teamOne = existing.teamOne.copy(
            name = setup.teamOne.name.ifBlank { "Team 1" },
            color = setup.teamOne.color,
            customColorArgb = setup.teamOne.customColorArgb,
            coaches = setup.teamOne.coaches,
            fieldCaptains = setup.teamOne.fieldCaptains,
            spiritCaptains = setup.teamOne.spiritCaptains,
        ),
        teamTwo = existing.teamTwo.copy(
            name = setup.teamTwo.name.ifBlank { "Team 2" },
            color = setup.teamTwo.color,
            customColorArgb = setup.teamTwo.customColorArgb,
            coaches = setup.teamTwo.coaches,
            fieldCaptains = setup.teamTwo.fieldCaptains,
            spiritCaptains = setup.teamTwo.spiritCaptains,
        ),
        teamOnePlayers = setup.teamOnePlayers,
        teamTwoPlayers = setup.teamTwoPlayers,
        pullPromptTarget = setup.pullPromptTarget,
        initialGenderRatio = setup.initialGenderRatio,
        firstHalfGenZone = setup.firstHalfGenZone,
        switchGenZoneAtHalftime = setup.switchGenZoneAtHalftime,
        openingPullingTeam = setup.pullingTeam,
        openingPullingFromEnd = setup.pullingFromEnd,
    )
    val promptAdjustedBase = base.copy(
        countdown = base.countdown?.withPullPromptTarget(
            pullingFromEnd = base.pullingFromEnd,
            promptTarget = setup.pullPromptTarget,
        ),
    )

    val updatedState = if (shouldResyncOpeningPullState) {
        promptAdjustedBase.copy(
            nearAttackingTeam = openingNearAttackingTeam,
            pullingTeam = setup.pullingTeam,
            pullingFromEnd = setup.pullingFromEnd,
        ).startPullSequence(now, phase = GamePhase.PRE_GAME)
    } else {
        promptAdjustedBase
    }
    return updatedState.withUndo(existing, "Undo Update game setup")
}

/// Extract only the setup-screen fields from live state so the setup editor can reopen prefilled.
fun GameState.toSetupState(): GameSetupState {
    return GameSetupState(
        startDate = startDate,
        startTime = startTime,
        timeZone = timeZone,
        tournamentName = tournamentName,
        division = division,
        level = level,
        gameContext = gameContext,
        observers = observers,
        nearEndName = nearEndName,
        farEndName = farEndName,
        rules = rules,
        teamOne = TeamSetup(
            name = teamOne.name,
            color = teamOne.color,
            customColorArgb = teamOne.customColorArgb,
            coaches = teamOne.coaches,
            fieldCaptains = teamOne.fieldCaptains,
            spiritCaptains = teamOne.spiritCaptains,
        ),
        teamTwo = TeamSetup(
            name = teamTwo.name,
            color = teamTwo.color,
            customColorArgb = teamTwo.customColorArgb,
            coaches = teamTwo.coaches,
            fieldCaptains = teamTwo.fieldCaptains,
            spiritCaptains = teamTwo.spiritCaptains,
        ),
        teamOnePlayers = teamOnePlayers,
        teamTwoPlayers = teamTwoPlayers,
        pullingTeam = openingPullingTeam,
        pullingFromEnd = openingPullingFromEnd,
        pullPromptTarget = pullPromptTarget,
        initialGenderRatio = initialGenderRatio,
        firstHalfGenZone = firstHalfGenZone,
        switchGenZoneAtHalftime = switchGenZoneAtHalftime,
    )
}

/**
 * Undo label and previous state for a reversible live-game action.
 *
 * @param label The user-facing undo button label.
 * @param previous The live state restored by undoing the action.
 */
@Serializable
data class UndoEntry(
    val label: String,
    val previous: GameState,
)
