package rmjarvis.ultiobserver

import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId

// Absolute Long timestamps in the game model are Unix epoch milliseconds.

enum class TeamId {
    TEAM_ONE,
    TEAM_TWO,
}
enum class FieldEnd {
    NEAR,
    FAR,
}
enum class LivePhase {
    PRE_GAME,
    BETWEEN_POINTS,
    LIVE_POINT,
    HALFTIME,
    GAME_OVER,
}
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
data class TeamSetup(
    val name: String = "",
    val color: TeamColorChoice = TeamColorChoice.WHITE,
)
data class PlayerCardRecord(
    val team: TeamId,
    val jerseyNumber: String,
    val priorYellows: Int,    // Cards issued in previous games of the current tournament.
    val priorReds: Int,
)
data class InGamePlayerCardRecord(
    val jerseyNumber: String,
    val yellows: Int = 0,
    val directReds: Int = 0,
)
// How to indicate cards for players when you don't know the player number.
const val UNKNOWN_PLAYER_NUMBER = "N/A"
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
data class GameSetupState(
    val startDate: LocalDate,
    val startTime: LocalTime,
    val timeZone: ZoneId,
    val rules: GameRules = GameRules(),
    val teamOne: TeamSetup = TeamSetup(name = "", color = TeamColorChoice.WHITE),
    val teamTwo: TeamSetup = TeamSetup(name = "", color = TeamColorChoice.BLUE),
    val priorCards: List<PlayerCardRecord> = emptyList(),
    val pullingTeam: TeamId = TeamId.TEAM_ONE,
    val pullingFromEnd: FieldEnd = FieldEnd.FAR,
)
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
data class CountdownState(
    val kind: CountdownKind,
    val label: String,
    val durationSeconds: Int,       // Original countdown length.
    val targetEpoch: Long,          // Clock time when the countdown reaches zero.
    val betweenPointsTarget: BetweenPointsCountdownTarget? = null,
) {
    fun swapOD(): CountdownState {
        if (kind != CountdownKind.BETWEEN_POINTS) {
            return this
        }
        val currentTarget = betweenPointsTarget
            ?: error("Between-points countdown is missing its target side.")
        val newTarget = currentTarget.flip()
        val deltaSeconds = newTarget.baseDurationSeconds - currentTarget.baseDurationSeconds
        return copy(
            label = newTarget.label,
            durationSeconds = durationSeconds + deltaSeconds,
            targetEpoch = targetEpoch + deltaSeconds * 1000L,
            betweenPointsTarget = newTarget,
        )
    }

}
enum class CountdownKind {
    BETWEEN_POINTS,
    TIME_OUT,
    HALFTIME,
}
enum class BetweenPointsCountdownTarget(
    val label: String,
    val baseDurationSeconds: Int,
) {
    OFFENSE_READY("Signal in", 60),
    PULL("Pull in", 80);

    fun flip(): BetweenPointsCountdownTarget {
        return if (this == OFFENSE_READY) PULL else OFFENSE_READY
    }
}
data class LiveGameState(
    val startDate: LocalDate,
    val startTime: LocalTime,
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
    val halftimeTaken: Boolean = false,
    val halftimeTargetScore: Int? = null,
    val winningScore: Int? = null,
    val halfCapApplied: Boolean = false,
    val softCapApplied: Boolean = false,
    val hardCapApplied: Boolean = false,
    val pendingCapOffer: CapType? = null,  // Set when asking whether to apply the next cap
    val undoEntry: UndoEntry? = null,
    val lastEvent: String = "Pregame setup complete.",
)
data class CapStatus(
    val label: String,
    val remaining: Duration,
)
data class UndoEntry(
    val label: String,
    val previous: LiveGameState,
)
data class CardAssessmentResult(
    val state: LiveGameState,
    val event: GameEvent,
    val needsLivePointMisconductChoice: Boolean =
        event.needsLivePointMisconductChoice(state),
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
enum class CapType {
    HALF,
    SOFT,
    HARD,
}
enum class PullInfractionType {
    OFFSIDES,
    FALSE_START,
}

sealed interface GameEvent {
    data object TimeoutUnavailable : GameEvent

    data class TeamOutOfTimeouts(
        val team: TeamId,
    ) : GameEvent

    data class TeamCardsChanged(
        val team: TeamId,
        val teamCardTotal: Int,
        val secondYellowJerseyNumber: String? = null,
    ) : GameEvent

    data class TechnicalFoulsChanged(
        val team: TeamId,
        val technicalFoulTotal: Int,
    ) : GameEvent

    data class PullInfractionRecorded(
        val team: TeamId,
        val infraction: PullInfractionType,
        val totalPullViolations: Int,
    ) : GameEvent
}
