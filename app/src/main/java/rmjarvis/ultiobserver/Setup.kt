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
 * Setup-screen fields needed to create or edit a live game.
 *
 * @param startDate The local date selected for the scheduled game start.
 * @param startTime The local clock time selected for the scheduled game start.
 * @param timeZone The time zone that gives the local start date/time its real instant.
 * @param rules The scoring, cap, halftime, and timeout rules selected for the game.
 * @param teamOne The setup identity for Team 1 before live field orientation is applied.
 * @param teamTwo The setup identity for Team 2 before live field orientation is applied.
 * @param priorCards Player cards carried in from previous games in the tournament.
 * @param pullingTeam The team selected to pull first.
 * @param pullingFromEnd The field end from which the first pull is selected to start.
 */
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
