package rmjarvis.ultiobserver

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import kotlinx.serialization.Serializable

// Absolute Long timestamps in the game model are Unix epoch milliseconds.

@Serializable
enum class TeamId {
    TEAM_ONE,
    TEAM_TWO;

    /// Return the other team identifier.
    fun flip(): TeamId {
        return if (this == TEAM_ONE) TEAM_TWO else TEAM_ONE
    }
}
@Serializable
enum class FieldEnd {
    NEAR,
    FAR;

    /// Return the opposite field end.
    fun flip(): FieldEnd {
        return if (this == NEAR) FAR else NEAR
    }
}
@Serializable
enum class LivePhase {
    PRE_GAME,
    BETWEEN_POINTS,
    LIVE_POINT,
    HALFTIME,
    GAME_OVER,
}
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
@Serializable
data class TeamSetup(
    val name: String = "",
    val color: TeamColorChoice = TeamColorChoice.WHITE,
)
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
    TIMEOUT_OFFENSE_TWENTY("20 seconds, offense"),
    TIMEOUT_OFFENSE_TEN("10 seconds, offense"),
    TIMEOUT_COUNTDOWN_FROM_FIVE("Countdown from 5"),
    TIMEOUT_OFFENSE_FREEZE_DEFENSE_TWENTY("Offense freeze; defense 20 seconds"),
    MISCONDUCT_OFFENSE_TWENTY("20 seconds, offense"),
    MISCONDUCT_OFFENSE_TEN("10 seconds, offense"),
    MISCONDUCT_COUNTDOWN_FROM_FIVE("Countdown from 5"),
    MISCONDUCT_OFFENSE_FREEZE_DEFENSE_TWENTY("Offense freeze; defense 20 seconds"),
    MISCONDUCT_DEFENSE_TWENTY("20 seconds, defense"),
    TIMEOUT_BETWEEN_POINTS_ONE_MINUTE_FOR_HAND("1 minute for a hand"),
    TIMEOUT_BETWEEN_POINTS_ONE_MINUTE_TO_PULL("1 minute to pull"),
    HALFTIME_FIVE_MINUTES("5 minutes"),
    HALFTIME_TWO_MINUTES("2 minutes"),
    HALF_CAP("Half cap"),
    SOFT_CAP("Soft cap"),
    HARD_CAP("Hard cap");

    /// Return the default alert mode for this cue before global settings are applied.
    fun defaultAlertMode(): TimingAlertMode {
        return when (this) {
            RECEIVING_TWENTY_FOR_HAND,
            RECEIVING_TEN_FOR_HAND,
            TIMEOUT_OFFENSE_TWENTY,
            TIMEOUT_OFFENSE_TEN,
            MISCONDUCT_OFFENSE_TWENTY,
            MISCONDUCT_OFFENSE_TEN,
            -> TimingAlertMode.TICK
            TIMEOUT_CLEAR_FIELD,
            TIMEOUT_BETWEEN_POINTS_ONE_MINUTE_FOR_HAND,
            TIMEOUT_BETWEEN_POINTS_ONE_MINUTE_TO_PULL,
            -> TimingAlertMode.BEEP
            HALFTIME_FIVE_MINUTES,
            HALFTIME_TWO_MINUTES,
            -> TimingAlertMode.KNOCK
            HALF_CAP,
            SOFT_CAP,
            HARD_CAP,
            -> TimingAlertMode.DING
            PULLING_TWENTY_TO_PULL,
            MISCONDUCT_DEFENSE_TWENTY,
            -> TimingAlertMode.VIBRATE
            else -> TimingAlertMode.NONE
        }
    }
}
@Serializable
enum class TimingAlertMode {
    NONE,
    VIBRATE,
    TICK,
    BEEP,
    KNOCK,
    DING;

    /// Convert a sound-producing alert mode into its concrete sound clip family.
    fun toTimingAlertSound(): TimingAlertSound {
        return when (this) {
            TICK -> TimingAlertSound.TICK
            BEEP -> TimingAlertSound.BEEP
            KNOCK -> TimingAlertSound.KNOCK
            DING -> TimingAlertSound.DING
            NONE, VIBRATE -> error("$this is not a sound timing alert mode.")
        }
    }
}
@Serializable
enum class TimingAlertSound(
    val label: String,
) {
    TICK("Tick"),
    BEEP("Beep"),
    KNOCK("Knock"),
    DING("Ding"),
}
@Serializable
data class TimingAlertPreferences(
    val globalMode: TimingAlertGlobalMode = TimingAlertGlobalMode.VIBRATION_ONLY,
    val soundVolume: Float = 0.5f,
    val vibrationDurationMillis: Long = DEFAULT_TIMING_CUE_VIBRATION_MS,
    val vibrateWithSounds: Boolean = false,
    val cueModes: Map<TimingCueId, TimingAlertMode> = defaultTimingCueModes(),
    val cueRepeatCounts: Map<TimingCueId, Int> = defaultTimingCueRepeatCounts(),
) {
    /**
     * Return the configured per-cue setting shown in Settings, before the global alert mode is applied.
     *
     * @param cueId The timing cue whose configured mode should be returned.
     */
    fun settingsModeFor(cueId: TimingCueId): TimingAlertMode {
        return cueModes[cueId] ?: cueId.defaultAlertMode()
    }

    /**
     * Return the repeat count for a cue, clamped to the supported range.
     *
     * @param cueId The timing cue whose repeat count should be returned.
     */
    fun repeatCountFor(cueId: TimingCueId): Int {
        return cueRepeatCounts[cueId]
            ?.coerceIn(MIN_TIMING_ALERT_REPEAT_COUNT, MAX_TIMING_ALERT_REPEAT_COUNT)
            ?: cueId.defaultRepeatCount()
    }

    /**
     * Return the effective alert mode to use when a timing cue fires.
     *
     * @param cueId The timing cue being delivered.
     */
    fun alertModeFor(cueId: TimingCueId): TimingAlertMode {
        val configuredMode = settingsModeFor(cueId)
        return when (globalMode) {
            TimingAlertGlobalMode.OFF -> TimingAlertMode.NONE
            TimingAlertGlobalMode.VIBRATION_ONLY -> {
                if (configuredMode == TimingAlertMode.NONE) TimingAlertMode.NONE else TimingAlertMode.VIBRATE
            }
            TimingAlertGlobalMode.SOUNDS_ON -> configuredMode
        }
    }
}

const val MIN_TIMING_CUE_VIBRATION_MS = 100L
const val MAX_TIMING_CUE_VIBRATION_MS = 500L
const val DEFAULT_TIMING_CUE_VIBRATION_MS = 360L
const val MIN_TIMING_ALERT_REPEAT_COUNT = 1
const val MAX_TIMING_ALERT_REPEAT_COUNT = 3
const val DEFAULT_TIMING_ALERT_REPEAT_COUNT = 1

enum class TimingAlertGlobalMode(
    val label: String,
) {
    OFF("Off"),
    VIBRATION_ONLY("Vibration Only"),
    SOUNDS_ON("Sounds On"),
}

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
}
@Serializable
data class UndoEntry(
    val label: String,
    val previous: LiveGameState,
)
sealed interface GameEvent {
    data class TimeoutCharged(
        val state: LiveGameState,
        val team: TeamId,
    ) : GameEvent

    data class TimeoutUnavailable(
        val state: LiveGameState,
    ) : GameEvent

    data class TeamOutOfTimeouts(
        val state: LiveGameState,
        val team: TeamId,
    ) : GameEvent

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

    data class TechnicalFoulsChanged(
        val state: LiveGameState,
        val team: TeamId,
        val technicalFoulTotal: Int,
    ) : GameEvent

    data class PullInfractionRecorded(
        val state: LiveGameState,
        val team: TeamId,
        val infraction: PullInfractionType,
        val totalPullViolations: Int,
    ) : GameEvent

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

sealed interface GamePrompt {
    data class ApplyCap(
        val state: LiveGameState,
        val capType: CapType,
    ) : GamePrompt

    data class LivePointMisconduct(
        val event: GameEvent,
    ) : GamePrompt

    data class HalftimeStarted(
        val state: LiveGameState,
    ) : GamePrompt

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
