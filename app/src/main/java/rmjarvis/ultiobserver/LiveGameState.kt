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
/// Setup-screen fields needed to create or edit a live game.
@Serializable
data class GameSetupState(
    @Serializable(with = LocalDateAsStringSerializer::class)
    val startDate: LocalDate,
    @Serializable(with = LocalTimeAsStringSerializer::class)
    val startTime: LocalTime,
    @Serializable(with = ZoneIdAsStringSerializer::class)
    val timeZone: ZoneId,
    val rules: GameRules = GameRules(),
    val teamOne: TeamSetup = TeamSetup(name = "", color = TeamColorChoice.WHITE),
    val teamTwo: TeamSetup = TeamSetup(name = "", color = TeamColorChoice.BLUE),
    val priorCards: List<PlayerCardRecord> = emptyList(),
    val pullingTeam: TeamId = TeamId.TEAM_ONE,
    val pullingFromEnd: FieldEnd = FieldEnd.FAR,
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
 * @param nearAttackingTeam The team attacking the observer's near end.
 * @param pullingFromEnd The field end occupied by the pulling team.
 * @param openingPullingTeam The team that pulled to start the game.
 * @param openingPullingFromEnd The field end used by the opening pull.
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
    val rules: GameRules,
    val teamOne: TeamLiveState,
    val teamTwo: TeamLiveState,
    val priorCards: List<PlayerCardRecord>,
    val teamOnePlayerCards: List<InGamePlayerCardRecord> = emptyList(),
    val teamTwoPlayerCards: List<InGamePlayerCardRecord> = emptyList(),
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

    /// Drop live-only countdown and undo/redo state before archiving a completed game.
    fun pruneUndoHistory(): LiveGameState {
        return copy(
            countdown = null,
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
    fun adjustScore(teamOneScore: Int, teamTwoScore: Int): LiveGameState {
        return this.copy(
            teamOne = this.teamOne.copy(score = teamOneScore.coerceAtLeast(0)),
            teamTwo = this.teamTwo.copy(score = teamTwoScore.coerceAtLeast(0)),
            lastEvent = "Score adjusted.",
        ).withUndo(this, "Undo Score Adjustment")
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
/// Model events that need observer-facing popup text.
sealed interface GameEvent {
    /**
     * Event reporting that a timeout was successfully charged to a team.
     *
     * @param state The live state after charging the timeout.
     * @param team The team charged with the timeout.
     */
    data class TimeoutCharged(
        val state: LiveGameState,
        val team: TeamId,
    ) : GameEvent

    /**
     * Event reporting that timeout entry is not legal in the current game state.
     *
     * @param state The live state that rejected the timeout.
     */
    data class TimeoutUnavailable(
        val state: LiveGameState,
    ) : GameEvent

    /**
     * Event reporting that a team requested a timeout with none remaining.
     *
     * @param state The live state that rejected the timeout.
     * @param team The team that has no remaining timeouts.
     */
    data class TeamOutOfTimeouts(
        val state: LiveGameState,
        val team: TeamId,
    ) : GameEvent

    /**
     * Event reporting a changed team-card total, with optional player-card context.
     *
     * @param state The live state after the card action.
     * @param team The team whose card total changed.
     * @param teamCardTotal The team-card point total after the action.
     * @param playerCardType The player-card event type, or null for team-only card changes.
     * @param playerCardJerseyNumber The player jersey number when playerCardType is present.
     */
    data class TeamCardsChanged(
        val state: LiveGameState,
        val team: TeamId,
        val teamCardTotal: Int,
        val playerCardType: PlayerCardEventType? = null,
        val playerCardJerseyNumber: String? = null,
    ) : GameEvent {
        init {
            require((playerCardType == null) == (playerCardJerseyNumber == null))
        }
    }

    /**
     * Event reporting a changed technical-foul total.
     *
     * @param state The live state after the technical foul.
     * @param team The team whose technical-foul total changed.
     * @param technicalFoulTotal The team technical-foul total after the action.
     */
    data class TechnicalFoulsChanged(
        val state: LiveGameState,
        val team: TeamId,
        val technicalFoulTotal: Int,
    ) : GameEvent

    /**
     * Event reporting an offsides or false-start pull infraction.
     *
     * @param state The live state after recording the infraction.
     * @param team The team that committed the infraction.
     * @param infraction The type of pull infraction recorded.
     * @param totalPullViolations The team's combined pull-violation total after the action.
     */
    data class PullInfractionRecorded(
        val state: LiveGameState,
        val team: TeamId,
        val infraction: PullInfractionType,
        val totalPullViolations: Int,
    ) : GameEvent

    /**
     * Event reporting a pull time violation and its rule outcome.
     *
     * @param state The live state after assessing the violation.
     * @param team The team that committed the time violation.
     * @param outcome The warning, timeout, or no-timeout outcome.
     */
    data class TimeViolationRecorded(
        val state: LiveGameState,
        val team: TeamId,
        val outcome: TimeViolationOutcome,
    ) : GameEvent
}

// Keep these as extensions instead of GameEvent members. If they become members, Kotlin
// resolves same-named subtype extension helpers to the member and recursively calls this dispatcher.
/// Format UI-facing text for this model event in the current Android app.
fun GameEvent.formatMessage(): String {
    // This when block does runtime resolution to call the correct subtype's extension function.
    return when (this) {
        is GameEvent.TimeoutCharged -> this.formatMessage()
        is GameEvent.TimeoutUnavailable -> this.formatMessage()
        is GameEvent.TeamOutOfTimeouts -> this.formatMessage()
        is GameEvent.TeamCardsChanged -> this.formatMessage()
        is GameEvent.TechnicalFoulsChanged -> this.formatMessage()
        is GameEvent.PullInfractionRecorded -> this.formatMessage()
        is GameEvent.TimeViolationRecorded -> this.formatMessage()
    }
}

/// Format the title for an event-driven popup.
fun GameEvent.formatPopupTitle(): String {
    // This when block does runtime resolution to call the correct subtype's extension function.
    return when (this) {
        is GameEvent.TimeoutCharged -> this.formatPopupTitle()
        is GameEvent.TimeoutUnavailable -> this.formatPopupTitle()
        is GameEvent.TeamOutOfTimeouts -> this.formatPopupTitle()
        is GameEvent.TeamCardsChanged -> this.formatPopupTitle()
        is GameEvent.TechnicalFoulsChanged -> this.formatPopupTitle()
        is GameEvent.PullInfractionRecorded -> this.formatPopupTitle()
        is GameEvent.TimeViolationRecorded -> this.formatPopupTitle()
    }
}

/// Model prompts that require an observer decision or acknowledgement.
sealed interface GamePrompt {
    /**
     * Prompt asking whether to apply a due cap now.
     *
     * @param state The live state with a pending cap offer.
     * @param capType The cap being offered.
     */
    data class ApplyCap(
        val state: LiveGameState,
        val capType: CapType,
    ) : GamePrompt

    /**
     * Prompt asking whether live-point misconduct was against the offense or defense.
     *
     * @param event The card or technical-foul event that triggered the misconduct prompt.
     */
    data class LivePointMisconduct(
        val event: GameEvent,
    ) : GamePrompt

    /**
     * Prompt notifying the observer that halftime has started.
     *
     * @param state The live state after entering halftime.
     */
    data class HalftimeStarted(
        val state: LiveGameState,
    ) : GamePrompt

    /**
     * Prompt notifying the observer that the game has ended.
     *
     * @param state The completed live state.
     */
    data class GameOver(
        val state: LiveGameState,
    ) : GamePrompt
}

/// Format title text for prompts that need a dialog title in the current Android app.
fun GamePrompt.formatTitle(): String {
    return when (this) {
        is GamePrompt.ApplyCap -> this.formatTitle()
        is GamePrompt.LivePointMisconduct -> this.formatTitle()
        is GamePrompt.HalftimeStarted -> this.formatTitle()
        is GamePrompt.GameOver -> this.formatTitle()
    }
}

/// Format the main text shown to the observer for a prompt.
fun GamePrompt.formatMessage(): String {
    return when (this) {
        is GamePrompt.ApplyCap -> this.formatMessage()
        is GamePrompt.LivePointMisconduct -> this.formatMessage()
        is GamePrompt.HalftimeStarted -> "Announce halftime."
        is GamePrompt.GameOver -> this.formatMessage()
    }
}

/// Format the title for a halftime-started prompt.
private fun GamePrompt.HalftimeStarted.formatTitle(): String = "Halftime"

/// Format the title for a game-over prompt.
private fun GamePrompt.GameOver.formatTitle(): String = "Game Over"

/// Format the game-over prompt body with the winner first.
private fun GamePrompt.GameOver.formatMessage(): String {
    val orderedTeams = state.winnerFirstTeams()
    return buildString {
        appendLine("${orderedTeams[0].name} ${orderedTeams[0].score}")
        append("${orderedTeams[1].name} ${orderedTeams[1].score}")
    }
}
