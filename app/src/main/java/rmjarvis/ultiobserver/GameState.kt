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
    TEAM_TWO,
}
@Serializable
enum class FieldEnd {
    NEAR,
    FAR,
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
data class PlayerCardRecord(
    val team: TeamId,
    val jerseyNumber: String,
    val priorYellows: Int,    // Cards issued in previous games of the current tournament.
    val priorReds: Int,
)
@Serializable
data class InGamePlayerCardRecord(
    val jerseyNumber: String,
    val yellows: Int = 0,
    val directReds: Int = 0,
)
// How to indicate cards for players when you don't know the player number.
const val UNKNOWN_PLAYER_NUMBER = "N/A"
@Serializable
data class GameRules(
    val gameTo: Int = 15,
    val halftimeMinutes: Int = 7,
    val useHalfCap: Boolean = true,
    val halfCapMinutes: Int = 45,
    val useSoftCap: Boolean = true,
    val softCapMinutes: Int = 90,
    val useHardCap: Boolean = true,
    val hardCapMinutes: Int = 100,
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
    val technicalFouls: Int = 0,
    val blueCards: Int = 0,
)
fun TeamLiveState.withAddedTimeout(): TeamLiveState {
    return copy(timeoutsUsedThisHalf = timeoutsUsedThisHalf + 1)
}
@Serializable
data class CountdownState(
    val kind: CountdownKind,
    val label: String,
    val durationSeconds: Int,       // Original countdown length.
    val targetEpoch: Long,          // Clock time when the countdown reaches zero.
    val betweenPointsTarget: BetweenPointsCountdownTarget? = null,
) {
    fun swapOD(): CountdownState {
        if (!kind.usesBetweenPointsTarget()) {
            return this
        }
        val currentTarget = betweenPointsTarget
            ?: error("Between-points countdown is missing its target side.")
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
    TIME_OUT,
    HALFTIME,
}

internal fun CountdownKind.usesBetweenPointsTarget(): Boolean {
    return this == CountdownKind.OPENING_PULL || this == CountdownKind.BETWEEN_POINTS
}
@Serializable
enum class BetweenPointsCountdownTarget(
    val label: String,
    private val standardDurationSeconds: Int,
    private val openingDurationSeconds: Int,
) {
    OFFENSE_READY("Signal in", 60, 20),
    PULL("Pull in", 80, 40);

    fun baseDurationSeconds(kind: CountdownKind): Int {
        return if (kind == CountdownKind.OPENING_PULL) openingDurationSeconds else standardDurationSeconds
    }

    fun flip(): BetweenPointsCountdownTarget {
        return if (this == OFFENSE_READY) PULL else OFFENSE_READY
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
    PULLING_DELAY_OF_GAME("Delay of game?"),
    TIMEOUT_CLEAR_FIELD("Sideline players clear the field"),
    TIMEOUT_OFFENSE_TWENTY("20 seconds, offense"),
    TIMEOUT_OFFENSE_TEN("10 seconds, offense"),
    TIMEOUT_COUNTDOWN_FROM_FIVE("Countdown from 5"),
    TIMEOUT_OFFENSE_FREEZE_DEFENSE_TWENTY("Offense freeze; defense 20 seconds"),
    TIMEOUT_BETWEEN_POINTS_ONE_MINUTE("1 minute to hand/pull"),
    HALFTIME_FIVE_MINUTES("5 minutes"),
    HALFTIME_TWO_MINUTES("2 minutes"),
}
@Serializable
enum class TimingAlertMode {
    NONE,
    VIBRATE,
    TICK,
    BEEP,
    DING,
    DOUBLE_TICK,
}
@Serializable
enum class TimingAlertSound(
    val label: String,
) {
    TICK("Tick"),
    BEEP("Beep"),
    DING("Ding"),
    DOUBLE_TICK("2 Tick"),
}
@Serializable
data class TimingAlertPreferences(
    val globalMode: TimingAlertGlobalMode = TimingAlertGlobalMode.VIBRATION_ONLY,
    val cueModes: Map<TimingCueId, TimingAlertMode> = defaultTimingCueModes(),
) {
    fun alertModeFor(cueId: TimingCueId): TimingAlertMode {
        val configuredMode = cueModes[cueId] ?: cueId.defaultAlertMode()
        return when (globalMode) {
            TimingAlertGlobalMode.OFF -> TimingAlertMode.NONE
            TimingAlertGlobalMode.VIBRATION_ONLY -> {
                if (configuredMode == TimingAlertMode.NONE) TimingAlertMode.NONE else TimingAlertMode.VIBRATE
            }
            TimingAlertGlobalMode.SOUNDS_ON -> configuredMode
        }
    }
}

enum class TimingAlertGlobalMode(
    val label: String,
) {
    OFF("Off"),
    VIBRATION_ONLY("Vibration Only"),
    SOUNDS_ON("Sounds On"),
}

internal fun TimingAlertMode.soundOrNull(): TimingAlertSound? {
    return when (this) {
        TimingAlertMode.TICK -> TimingAlertSound.TICK
        TimingAlertMode.BEEP -> TimingAlertSound.BEEP
        TimingAlertMode.DING -> TimingAlertSound.DING
        TimingAlertMode.DOUBLE_TICK -> TimingAlertSound.DOUBLE_TICK
        TimingAlertMode.NONE, TimingAlertMode.VIBRATE -> null
    }
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
    val pullSequenceOffsidesRecorded: Boolean = false,
    val pullSequenceFalseStartRecorded: Boolean = false,
    val pullSkippedForCurrentPoint: Boolean = false,
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
)
data class CapStatus(
    val label: String,
    val remaining: Duration,
)
@Serializable
data class UndoEntry(
    val label: String,
    val previous: LiveGameState,
)
data class CardAssessmentResult(
    val state: LiveGameState,
    val event: GameEvent,
    val needsMisconductChoice: Boolean =
        event.needsMisconductChoice(),
)
data class TimeoutAssessmentResult(
    val state: LiveGameState,
    val event: GameEvent? = null,
)
data class PullInfractionAssessmentResult(
    val state: LiveGameState,
    val event: GameEvent? = null,
)
enum class RedCardMode {
    DIRECT_RED,
    SECOND_YELLOW,
}
@Serializable
enum class CapType {
    HALF,
    SOFT,
    HARD,
}
@Serializable
enum class PullInfractionType {
    OFFSIDES,
    FALSE_START,
}

enum class PlayerCardEventType {
    YELLOW,
    RED,
    SECOND_YELLOW,
}

sealed interface GameEvent {
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
