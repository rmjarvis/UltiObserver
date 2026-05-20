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
/// Broad phase of a live game.
@Serializable
enum class LivePhase {
    PRE_GAME,
    BETWEEN_POINTS,
    LIVE_POINT,
    HALFTIME,
    GAME_OVER,
}
/**
 * Selectable team jersey color and the display colors that go with it.
 *
 * @param label The user-facing color name.
 * @param accentArgb The background color matching the nominal jersey color.
 * @param contentArgb A text color with good contrast to the accent color.
 */
@Serializable
enum class TeamColorChoice(
    val label: String,
    val accentArgb: Long,    // The background color matching the nominal jersey color.
    val contentArgb: Long,   // A text color with good contrast to the accent color.
) {
    WHITE("White", 0xFFF5F2E8, 0xFF1F1A17),
    BLACK("Black", 0xFF232220, 0xFFF6F2E8),
    RED("Red", 0xFFC23B2A, 0xFFFFF8F5),
    BLUE("Blue", 0xFF2A5CAA, 0xFFF7FAFF),
    GREEN("Green", 0xFF2E7D32, 0xFFF4FFF4),
    YELLOW("Yellow", 0xFFE7A51E, 0xFF2E2400),
    PINK("Pink", 0xFFFF4FA3, 0xFF2F1022),
    GRAY("Gray", 0xFF708090, 0xFFF7F8FA),
}
/**
 * Setup-screen team identity fields before a live game starts.
 *
 * @param name The team name entered in setup; blank means no explicit name yet.
 * @param color The selected jersey color for this team.
 */
@Serializable
data class TeamSetup(
    val name: String = "",
    val color: TeamColorChoice = TeamColorChoice.WHITE,
)
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
)
/**
 * Live counters and display identity for one team.
 *
 * @param firstHalfTimeoutsUsed Stored after halftime so floater-timeout carryover can be derived.
 */
@Serializable
data class TeamLiveState(
    val name: String,
    val color: TeamColorChoice,
    val score: Int = 0,
    val timeoutsUsedThisHalf: Int = 0,
    val firstHalfTimeoutsUsed: Int = 0,
    val offsides: Int = 0,
    val falseStarts: Int = 0,
    val timeViolationWarningIssued: Boolean = false,
    val technicalFouls: Int = 0,
    val blueCards: Int = 0,
) {
    /// Return this team state with one additional timeout used in the current half.
    fun withAddedTimeout(): TeamLiveState {
        return copy(timeoutsUsedThisHalf = timeoutsUsedThisHalf + 1)
    }
}

/// Return teams ordered with the higher-scoring team first for summary display.
internal fun LiveGameState.winnerFirstTeams(): List<TeamLiveState> {
    val teamOneFirst = teamOne.score > teamTwo.score ||
        (teamOne.score == teamTwo.score && teamOne.name <= teamTwo.name)
    return if (teamOneFirst) {
        listOf(teamOne, teamTwo)
    } else {
        listOf(teamTwo, teamOne)
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
 */
@Serializable
data class CountdownState(
    val kind: CountdownKind,
    val label: String,
    val durationSeconds: Int,       // Original countdown length.
    val targetEpoch: Long,          // Clock time when the countdown reaches zero.
    val betweenPointsTarget: BetweenPointsCountdownTarget? = null,
) {
    /// Swap the countdown's offensive/defensive between-points target when the field responsibility flips.
    fun swapOD(): CountdownState {
        if (!kind.usesBetweenPointsTarget()) {
            return this
        }
        val currentTarget = betweenPointsTarget!!
        val newTarget = currentTarget.flip()
        val deltaSeconds = newTarget.baseDurationSeconds(kind) - currentTarget.baseDurationSeconds(kind)
        return copy(
            label = newTarget.label,
            durationSeconds = durationSeconds + deltaSeconds,
            targetEpoch = targetEpoch + deltaSeconds * 1000L,
            betweenPointsTarget = newTarget,
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
    MISCONDUCT_DEFENSE_CHECK,
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
 * @param nearAttackingTeam The team attacking the observer's near end.
 * @param pullingFromEnd The field end occupied by the pulling team.
 * @param openingPullingTeam The team that pulled to start the game.
 * @param openingPullingFromEnd The field end used by the opening pull.
 * @param eventLog Persisted log of significant game events and manual corrections.
 * @param pendingCapOffer The cap currently being offered to the observer for yes/no application.
 */
@Serializable
data class LiveGameState(
    @Serializable(with = LocalDateAsStringSerializer::class)
    val startDate: LocalDate,
    @Serializable(with = LocalTimeAsStringSerializer::class)
    val startTime: LocalTime,
    @Serializable(with = ZoneIdAsStringSerializer::class)
    val timeZone: ZoneId,
    val startEpoch: Long,
    val endEpoch: Long? = null,
    val tournamentName: String = "",
    val rules: GameRules,
    val teamOne: TeamLiveState,
    val teamTwo: TeamLiveState,
    val priorCards: List<PlayerCardRecord>,
    val teamOnePlayerCards: List<InGamePlayerCardRecord> = emptyList(),
    val teamTwoPlayerCards: List<InGamePlayerCardRecord> = emptyList(),
    val eventLog: List<EventLogEntry> = emptyList(),
    val nearAttackingTeam: TeamId,
    val pullingTeam: TeamId,
    val pullingFromEnd: FieldEnd,
    val openingPullingTeam: TeamId,
    val openingPullingFromEnd: FieldEnd,
    val phase: LivePhase = LivePhase.PRE_GAME,
    val countdown: CountdownState? = null,
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
    val redoEntry: LiveGameState? = null,
    val lastEvent: String = "Pregame setup complete.",
) {
    /// Report whether this state is the pre-pull live preview created directly from setup.
    fun isInitialLivePreview(): Boolean {
        return phase == LivePhase.BETWEEN_POINTS &&
            teamOne.score == 0 &&
            teamTwo.score == 0 &&
            undoEntry == null &&
            !halftimeTaken
    }

    /**
     * Drop undo/redo state, optionally dropping live-only countdown state for archive summaries.
     *
     * @param clearCountdown Whether to clear an active countdown from the returned state.
     */
    fun pruneUndoHistory(clearCountdown: Boolean = true): LiveGameState {
        return copy(
            countdown = if (clearCountdown) null else countdown,
            undoEntry = null,
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

    /// Swap the teams' field ends while keeping the same team pulling.
    fun swapFieldEnds(): LiveGameState {
        val newPullingFromEnd = this.pullingFromEnd.flip()
        return this.copy(
            nearAttackingTeam = this.nearAttackingTeam.flip(),
            pullingFromEnd = newPullingFromEnd,
            countdown = this.countdown?.swapOD(),
            pullSequenceOffsidesRecorded = false,
            pullSequenceFalseStartRecorded = false,
            lastEvent = "Field ends swapped.",
        ).withUndo(this, "Undo Swap Ends of Field")
    }

    /// Swap the pulling team while leaving the teams' attacking orientation otherwise intact.
    fun swapPullingTeam(): LiveGameState {
        val newPullingTeam = this.pullingTeam.flip()
        val newPullingFromEnd = this.pullingFromEnd.flip()
        return this.copy(
            pullingTeam = newPullingTeam,
            pullingFromEnd = newPullingFromEnd,
            countdown = this.countdown?.swapOD(),
            pullSequenceOffsidesRecorded = false,
            pullSequenceFalseStartRecorded = false,
            lastEvent = "Pulling team swapped.",
        ).withUndo(this, "Undo Swap Pulling Team")
    }

    /**
     * Add or subtract seconds from the active countdown.
     *
     * @param seconds The signed countdown adjustment; negative values move the target earlier.
     */
    fun addTimeToCountdown(seconds: Int): LiveGameState {
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
     * Replace the recorded score as a manual correction.
     *
     * @param teamOneScore The corrected team-one score, clamped at zero.
     * @param teamTwoScore The corrected team-two score, clamped at zero.
     */
    fun adjustScore(teamOneScore: Int, teamTwoScore: Int, now: Long): LiveGameState {
        val adjustedTeamOneScore = teamOneScore.coerceAtLeast(0)
        val adjustedTeamTwoScore = teamTwoScore.coerceAtLeast(0)
        val entries = if (adjustedTeamOneScore != this.teamOne.score || adjustedTeamTwoScore != this.teamTwo.score) {
            listOf(
                EventLogEntry(
                    timestampEpoch = now,
                    type = EventLogType.SCORE_ADJUSTED,
                    teamOneScore = adjustedTeamOneScore,
                    teamTwoScore = adjustedTeamTwoScore,
                )
            )
        } else {
            emptyList()
        }
        return this.copy(
            teamOne = this.teamOne.copy(score = teamOneScore.coerceAtLeast(0)),
            teamTwo = this.teamTwo.copy(score = teamTwoScore.coerceAtLeast(0)),
            lastEvent = "Score adjusted.",
        ).withEventLogEntries(entries).withUndo(this, "Undo Score Adjustment")
    }
}

/**
 * Apply an edited setup form to an existing live game.
 * This is the model-side return path from the live-game setup editor.
 *
 * @param existing The live state currently being edited.
 * @param setup The setup values returned by the update-game form.
 * @param now The epoch millis for rebuilding the opening pull countdown when pre-play orientation changes.
 */
fun applySetupToLiveGame(
    existing: LiveGameState,
    setup: GameSetupState,
    now: Long,
): LiveGameState {
    val openingNearAttackingTeam = if (setup.pullingFromEnd == FieldEnd.FAR) {
        setup.pullingTeam
    } else {
        setup.pullingTeam.flip()
    }
    val shouldResyncPullState = existing.teamOne.score == 0 &&
        existing.teamTwo.score == 0 &&
        existing.phase != LivePhase.LIVE_POINT

    val base = existing.copy(
        startDate = setup.startDate,
        startTime = setup.startTime,
        timeZone = setup.timeZone,
        startEpoch = epochTimestamp(setup.startDate, setup.startTime, setup.timeZone),
        tournamentName = setup.tournamentName,
        rules = setup.rules,
        teamOne = existing.teamOne.copy(
            name = setup.teamOne.name.ifBlank { "Team 1" },
            color = setup.teamOne.color,
        ),
        teamTwo = existing.teamTwo.copy(
            name = setup.teamTwo.name.ifBlank { "Team 2" },
            color = setup.teamTwo.color,
        ),
        priorCards = setup.priorCards,
        teamOnePlayerCards = existing.teamOnePlayerCards,
        teamTwoPlayerCards = existing.teamTwoPlayerCards,
        openingPullingTeam = setup.pullingTeam,
        openingPullingFromEnd = setup.pullingFromEnd,
    )

    val updatedState = if (shouldResyncPullState) {
        base.copy(
            nearAttackingTeam = openingNearAttackingTeam,
            pullingTeam = setup.pullingTeam,
            pullingFromEnd = setup.pullingFromEnd,
        ).startPullSequence(now)
    } else {
        base
    }
    return updatedState.withUndo(existing, "Undo Update Game Setup")
}

/// Extract only the setup-screen fields from live state so the setup editor can reopen prefilled.
fun LiveGameState.toSetupState(): GameSetupState {
    return GameSetupState(
        startDate = startDate,
        startTime = startTime,
        timeZone = timeZone,
        tournamentName = tournamentName,
        rules = rules,
        teamOne = TeamSetup(
            name = teamOne.name,
            color = teamOne.color,
        ),
        teamTwo = TeamSetup(
            name = teamTwo.name,
            color = teamTwo.color,
        ),
        priorCards = priorCards,
        pullingTeam = openingPullingTeam,
        pullingFromEnd = openingPullingFromEnd,
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
    val previous: LiveGameState,
)
