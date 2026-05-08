package rmjarvis.ultiobserver

import androidx.compose.ui.graphics.Color
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max
import kotlin.math.min

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
    val accent: Color,    // The background color matching the nominal jersey color
    val content: Color,   // A text color with good contrast to the accent color.
) {
    WHITE("White", Color(0xFFF5F2E8), Color(0xFF1F1A17)),
    BLACK("Black", Color(0xFF232220), Color(0xFFF6F2E8)),
    RED("Red", Color(0xFFC23B2A), Color(0xFFFFF8F5)),
    BLUE("Blue", Color(0xFF2A5CAA), Color(0xFFF7FAFF)),
    GREEN("Green", Color(0xFF2E7D32), Color(0xFFF4FFF4)),
    YELLOW("Yellow", Color(0xFFE0B52F), Color(0xFF2E2400)),
    ORANGE("Orange", Color(0xFFCF6B17), Color(0xFFFFF6EE)),
    GRAY("Gray", Color(0xFF708090), Color(0xFFF7F8FA)),
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
    val startTime: LocalTime = nextHalfHourFromNow(),
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
    val firstHalfTimeoutsUsed: Int? = null,
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
    val targetEpochMillis: Long,    // Clock time when the countdown reaches zero.
)

enum class CountdownKind {
    BETWEEN_POINTS,
    TIME_OUT,
    HALFTIME,
}

data class LiveGameState(
    val startTime: LocalTime,
    val endTime: LocalTime? = null,
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
    val message: String,
    val needsLivePointMisconductChoice: Boolean = false,  // Set when a live-point card/TF needs O/D choice.
)

data class TimeoutAssessmentResult(
    val state: LiveGameState,
    val message: String? = null,
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

// Make the first live game state after setting the initial setup parameters.
fun createLiveGameState(setup: GameSetupState): LiveGameState {
    val nearAttackingTeam = if (setup.pullingFromEnd == FieldEnd.FAR) {
        setup.pullingTeam
    } else {
        setup.pullingTeam.flip()
    }
    val initialCountdown = buildBetweenPointsCountdown(
        pullingFromEnd = setup.pullingFromEnd,
        sequenceStartMillis = nextOccurrenceMillis(setup.startTime),
    )

    return LiveGameState(
        startTime = setup.startTime,
        rules = setup.rules,
        teamOne = TeamLiveState(
            name = setup.teamOne.name.ifBlank { "Team 1" },
            color = setup.teamOne.color,
        ),
        teamTwo = TeamLiveState(
            name = setup.teamTwo.name.ifBlank { "Team 2" },
            color = setup.teamTwo.color,
        ),
        priorCards = setup.priorCards,
        nearAttackingTeam = nearAttackingTeam,
        pullingTeam = setup.pullingTeam,
        pullingFromEnd = setup.pullingFromEnd,
        openingPullingTeam = setup.pullingTeam,
        openingPullingFromEnd = setup.pullingFromEnd,
        phase = LivePhase.BETWEEN_POINTS,
        countdown = initialCountdown,
    )
}

// Initiate the countdown for edge cases where we don't automatically start it directly
// on an event.
fun startPullSequence(
    state: LiveGameState,
    nowMillis: Long,
): LiveGameState {
    val countdown = buildBetweenPointsCountdown(
        pullingFromEnd = state.pullingFromEnd,
        sequenceStartMillis = nowMillis,
    )
    return state.copy(
        phase = LivePhase.BETWEEN_POINTS,
        countdown = countdown,
        pullSequenceOffsidesRecorded = false,
        pullSequenceFalseStartRecorded = false,
        lastEvent = "Pull sequence started.",
    )
}

// Update state for someone scoring a goal.
fun recordGoal(
    state: LiveGameState,
    scoringTeam: TeamId,
    now: LocalTime,
    nowMillis: Long,
): LiveGameState {
    if (state.phase == LivePhase.GAME_OVER) {
        return state
    }

    val updatedTeamOne = if (scoringTeam == TeamId.TEAM_ONE) {
        state.teamOne.copy(score = state.teamOne.score + 1)
    } else {
        state.teamOne
    }
    val updatedTeamTwo = if (scoringTeam == TeamId.TEAM_TWO) {
        state.teamTwo.copy(score = state.teamTwo.score + 1)
    } else {
        state.teamTwo
    }
    val nextNearAttackingTeam = state.nearAttackingTeam.flip()
    val nextPullingTeam = scoringTeam
    val nextPullingFromEnd = if (scoringTeam == state.nearAttackingTeam) {
        FieldEnd.NEAR
    } else {
        FieldEnd.FAR
    }
    // Do this check every time, because if the user changes the rules.gameTo, we naturally
    // update to the updated rules.  However, if the soft cap has been applied, that takes
    // precedence.
    val gameWinningScore = state.winningScore ?: state.rules.gameTo
    val gameOver = max(updatedTeamOne.score, updatedTeamTwo.score) >= gameWinningScore

    if (gameOver) {
        return state.copy(
            teamOne = updatedTeamOne,
            teamTwo = updatedTeamTwo,
            endTime = now,
            pullingTeam = nextPullingTeam,
            nearAttackingTeam = nextNearAttackingTeam,
            pullingFromEnd = nextPullingFromEnd,
            phase = LivePhase.GAME_OVER,
            countdown = null,
            pullSequenceOffsidesRecorded = false,
            pullSequenceFalseStartRecorded = false,
            winningScore = gameWinningScore,
            pendingCapOffer = null,
            lastEvent = "Game over.",
        ).withUndo(state, "Undo Goal by ${teamName(state, scoringTeam)}")
    }

    // Caps are checked before halftime so hard cap takes precedence over soft, and soft over half.
    val pendingCapOffer = when {
        hardCapReached(state, now) -> CapType.HARD
        softCapReached(state, now) -> CapType.SOFT
        halfCapReached(
            state = state,
            teamOneScore = updatedTeamOne.score,
            teamTwoScore = updatedTeamTwo.score,
            now = now,
        ) -> CapType.HALF
        else -> null
    }

    val halftimeScore = state.halftimeTargetScore ?: halftimeScore(state.rules)
    val halftimeReached = !state.halftimeTaken &&
        max(updatedTeamOne.score, updatedTeamTwo.score) >= halftimeScore

    if (halftimeReached && pendingCapOffer != CapType.SOFT && pendingCapOffer != CapType.HARD) {
        val secondHalfPullingTeam = state.openingPullingTeam.flip()
        val secondHalfPullingFromEnd = state.openingPullingFromEnd.flip()
        val secondHalfNearAttackingTeam = if (secondHalfPullingFromEnd == FieldEnd.FAR) {
            secondHalfPullingTeam
        } else {
            secondHalfPullingTeam.flip()
        }
        val halftimeCountdown = buildHalftimeCountdown(
            halftimeMinutes = state.rules.halftimeMinutes,
            sequenceStartMillis = nowMillis,
        )
        return state.copy(
            teamOne = updatedTeamOne.copy(
                firstHalfTimeoutsUsed = state.teamOne.timeoutsUsedThisHalf,
                timeoutsUsedThisHalf = 0,
            ),
            teamTwo = updatedTeamTwo.copy(
                firstHalfTimeoutsUsed = state.teamTwo.timeoutsUsedThisHalf,
                timeoutsUsedThisHalf = 0,
            ),
            pullingTeam = secondHalfPullingTeam,
            pullingFromEnd = secondHalfPullingFromEnd,
            nearAttackingTeam = secondHalfNearAttackingTeam,
            phase = LivePhase.HALFTIME,
            countdown = halftimeCountdown,
            pullSequenceOffsidesRecorded = false,
            pullSequenceFalseStartRecorded = false,
            halftimeTaken = true,
            halftimeTargetScore = state.halftimeTargetScore,
            winningScore = state.winningScore,
            halfCapApplied = state.halfCapApplied,
            softCapApplied = state.softCapApplied,
            hardCapApplied = state.hardCapApplied,
            pendingCapOffer = capDuringHalftimeOffer(state, now),
            lastEvent = "Halftime.",
        ).withUndo(state, "Undo Goal by ${teamName(state, scoringTeam)}")
    }

    // Regular point -- not half, and not game over.
    val countdown = buildBetweenPointsCountdown(
        pullingFromEnd = nextPullingFromEnd,
        sequenceStartMillis = nowMillis,
    )

    return state.copy(
        teamOne = updatedTeamOne,
        teamTwo = updatedTeamTwo,
        pullingTeam = nextPullingTeam,
        nearAttackingTeam = nextNearAttackingTeam,
        pullingFromEnd = nextPullingFromEnd,
        phase = LivePhase.BETWEEN_POINTS,
        countdown = countdown,
        pullSequenceOffsidesRecorded = false,
        pullSequenceFalseStartRecorded = false,
        halftimeTaken = state.halftimeTaken,
        halftimeTargetScore = state.halftimeTargetScore,
        winningScore = state.winningScore,
        halfCapApplied = state.halfCapApplied,
        softCapApplied = state.softCapApplied,
        hardCapApplied = state.hardCapApplied,
        pendingCapOffer = pendingCapOffer,
        lastEvent = "${teamName(state, scoringTeam)} scored.",
    ).withUndo(state, "Undo Goal by ${teamName(state, scoringTeam)}")
}

// Undo Game Over
fun undoGameOver(
    state: LiveGameState,
    nowMillis: Long,
): LiveGameState {
    if (state.phase != LivePhase.GAME_OVER) {
        return state
    }
    return startPullSequence(
        state.copy(
            phase = LivePhase.BETWEEN_POINTS,
            countdown = null,
            endTime = null,
            lastEvent = "Game over undone.",
        ),
        nowMillis,
    )
}

// Manually start half time
fun startHalftimeNow(
    state: LiveGameState,
    now: LocalTime,
    nowMillis: Long,
): LiveGameState {
    if (state.halftimeTaken || state.phase == LivePhase.GAME_OVER || state.phase == LivePhase.HALFTIME) {
        return state
    }
    val secondHalfPullingTeam = state.openingPullingTeam.flip()
    val secondHalfPullingFromEnd = state.openingPullingFromEnd.flip()
    val secondHalfNearAttackingTeam = if (secondHalfPullingFromEnd == FieldEnd.FAR) {
        secondHalfPullingTeam
    } else {
        secondHalfPullingTeam.flip()
    }
    val halftimeCountdown = buildHalftimeCountdown(
        halftimeMinutes = state.rules.halftimeMinutes,
        sequenceStartMillis = nowMillis,
    )

    return state.copy(
        teamOne = state.teamOne.copy(
            firstHalfTimeoutsUsed = state.teamOne.timeoutsUsedThisHalf,
            timeoutsUsedThisHalf = 0,
        ),
        teamTwo = state.teamTwo.copy(
            firstHalfTimeoutsUsed = state.teamTwo.timeoutsUsedThisHalf,
            timeoutsUsedThisHalf = 0,
        ),
        pullingTeam = secondHalfPullingTeam,
        pullingFromEnd = secondHalfPullingFromEnd,
        nearAttackingTeam = secondHalfNearAttackingTeam,
        phase = LivePhase.HALFTIME,
        countdown = halftimeCountdown,
        pullSequenceOffsidesRecorded = false,
        pullSequenceFalseStartRecorded = false,
        halftimeTaken = true,
        pendingCapOffer = state.pendingCapOffer.takeIf { it == CapType.SOFT || it == CapType.HARD }
            ?: capDuringHalftimeOffer(state, now),
        lastEvent = "Halftime.",
    ).withUndo(state, "Undo Start Halftime")
}

// Manually end the game now.
fun endGameNow(
    state: LiveGameState,
    now: LocalTime,
): LiveGameState {
    if (state.phase == LivePhase.GAME_OVER) {
        return state
    }
    return state.copy(
        endTime = now,
        phase = LivePhase.GAME_OVER,
        countdown = null,
        pendingCapOffer = null,
        lastEvent = "Game over.",
    ).withUndo(state, "Undo End Game")
}

// Start a point.  I.e. indicate that the pull happened.
fun beginLivePoint(state: LiveGameState): LiveGameState {
    return state.copy(
        phase = LivePhase.LIVE_POINT,
        countdown = null,
        pullSequenceOffsidesRecorded = false,
        pullSequenceFalseStartRecorded = false,
        lastEvent = "Point is live.",
    ).withUndo(state, "Undo Start Point")
}

// If we record a goal before starting the point, start it, and then record the goal.
fun recordGoalFromCurrentState(
    state: LiveGameState,
    scoringTeam: TeamId,
    now: LocalTime,
    nowMillis: Long,
): LiveGameState {
    val livePointState = if (state.phase == LivePhase.BETWEEN_POINTS) {
        beginLivePoint(state)
    } else {
        state
    }
    return recordGoal(livePointState, scoringTeam, now, nowMillis)
}

// Resume a live point after a timeout or similar interruption.
fun continueLivePoint(state: LiveGameState): LiveGameState {
    return state.copy(
        phase = LivePhase.LIVE_POINT,
        countdown = null,
        lastEvent = "Point continued.",
    )
}

// Advance automatic clock-driven transitions that do not require an observer button press.
fun advanceGameClock(state: LiveGameState, nowMillis: Long): LiveGameState {
    val countdown = state.countdown ?: return state
    if (nowMillis < countdown.targetEpochMillis) {
        return state
    }
    return when {
        state.phase == LivePhase.BETWEEN_POINTS && countdown.kind == CountdownKind.BETWEEN_POINTS -> {
            automaticLivePointState(state)
        }
        state.phase == LivePhase.LIVE_POINT && countdown.kind == CountdownKind.TIME_OUT -> {
            automaticContinueLivePointState(state)
        }
        state.phase == LivePhase.HALFTIME && countdown.kind == CountdownKind.HALFTIME -> {
            val betweenPointsState = state.copy(
                phase = LivePhase.BETWEEN_POINTS,
                countdown = buildBetweenPointsCountdown(
                    pullingFromEnd = state.pullingFromEnd,
                    sequenceStartMillis = countdown.targetEpochMillis,
                ),
            )
            advanceGameClock(betweenPointsState, nowMillis)
        }
        else -> state
    }
}

// Adjust countdown timer (use negative number to subtract time)
fun addTimeToCountdown(state: LiveGameState, seconds: Int): LiveGameState {
    val countdown = state.countdown ?: return state
    return state.copy(
        countdown = countdown.copy(targetEpochMillis = countdown.targetEpochMillis + seconds * 1000L),
        lastEvent = "Adjusted timer by ${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}.",
    )
}

// Manually adjust the score
fun adjustScore(state: LiveGameState, teamOneScore: Int, teamTwoScore: Int): LiveGameState {
    return state.copy(
        teamOne = state.teamOne.copy(score = teamOneScore.coerceAtLeast(0)),
        teamTwo = state.teamTwo.copy(score = teamTwoScore.coerceAtLeast(0)),
        lastEvent = "Score adjusted.",
    ).withUndo(state, "Undo Score Adjustment")
}

// Manually adjust the number of timeouts
fun adjustTimeouts(
    state: LiveGameState,
    teamOneTimeoutsUsed: Int,
    teamTwoTimeoutsUsed: Int,
): LiveGameState {
    return state.copy(
        teamOne = state.teamOne.copy(
            timeoutsUsedThisHalf = teamOneTimeoutsUsed,
        ),
        teamTwo = state.teamTwo.copy(
            timeoutsUsedThisHalf = teamTwoTimeoutsUsed,
        ),
        lastEvent = "Timeouts adjusted.",
    ).withUndo(state, "Undo Timeout Adjustment")
}

// Manually adjust the cards and technical fouls that have been assigned
fun adjustCardsAndTf(
    state: LiveGameState,
    teamOneBlues: Int,
    teamOneTechnicalFouls: Int,
    teamTwoBlues: Int,
    teamTwoTechnicalFouls: Int,
    teamOnePlayerCards: List<InGamePlayerCardRecord>,
    teamTwoPlayerCards: List<InGamePlayerCardRecord>,
): LiveGameState {
    requirePlayerCardRecordsValid(teamOnePlayerCards)
    requirePlayerCardRecordsValid(teamTwoPlayerCards)
    val adjustedTeamOneBlues = teamOneBlues.coerceAtLeast(0)
    val adjustedTeamOneTechnicalFouls = teamOneTechnicalFouls.coerceAtLeast(0)
    val adjustedTeamTwoBlues = teamTwoBlues.coerceAtLeast(0)
    val adjustedTeamTwoTechnicalFouls = teamTwoTechnicalFouls.coerceAtLeast(0)

    return state.copy(
        teamOne = state.teamOne.copy(
            blueCards = adjustedTeamOneBlues,
            technicalFouls = adjustedTeamOneTechnicalFouls,
        ),
        teamTwo = state.teamTwo.copy(
            blueCards = adjustedTeamTwoBlues,
            technicalFouls = adjustedTeamTwoTechnicalFouls,
        ),
        teamOnePlayerCards = teamOnePlayerCards,
        teamTwoPlayerCards = teamTwoPlayerCards,
        lastEvent = "Cards and technical fouls adjusted.",
    ).withUndo(state, "Undo Cards / TF Adjustment")
}

// Make failures obvious if a caller bypasses the normal player-card adjustment flow.
private fun requirePlayerCardRecordsValid(records: List<InGamePlayerCardRecord>) {
    require(records.all { it.yellows >= 0 && it.directReds >= 0 }) {
        "Player card records cannot have negative card counts."
    }
    require(records.distinctBy { it.jerseyNumber }.size == records.size) {
        "Player card records cannot contain duplicate player entries."
    }
}

// Assign a card to a specific player
fun addPlayerCardAssignment(
    records: List<InGamePlayerCardRecord>,
    jerseyNumber: String,
    cardType: CardType,
): List<InGamePlayerCardRecord> {
    return updatePlayerCardRecord(records, jerseyNumber) { record ->
        when (cardType) {
            CardType.YELLOW -> record.copy(yellows = record.yellows + 1)
            CardType.RED -> record.copy(directReds = record.directReds + 1)
        }
    }
}

// Remove a card from a specific player
fun removePlayerCardAssignment(
    records: List<InGamePlayerCardRecord>,
    jerseyNumber: String,
    cardType: CardType,
): List<InGamePlayerCardRecord> {
    val existingIndex = records.indexOfFirst { it.jerseyNumber == jerseyNumber }
    if (existingIndex < 0) {
        return records
    }
    return records.mapIndexedNotNull { index, record ->
        if (index != existingIndex) {
            record
        } else {
            val updated = when (cardType) {
                CardType.YELLOW -> record.copy(yellows = max(0, record.yellows - 1))
                CardType.RED -> record.copy(directReds = max(0, record.directReds - 1))
            }
            if (updated.yellows == 0 && updated.directReds == 0) null else updated
        }
    }
}

// Manually change the number of offside or false starts each team has.
fun adjustPullInfractions(
    state: LiveGameState,
    teamOneOffsides: Int,
    teamOneFalseStarts: Int,
    teamTwoOffsides: Int,
    teamTwoFalseStarts: Int,
): LiveGameState {
    return state.copy(
        teamOne = state.teamOne.copy(
            offsides = teamOneOffsides.coerceAtLeast(0),
            falseStarts = teamOneFalseStarts.coerceAtLeast(0),
        ),
        teamTwo = state.teamTwo.copy(
            offsides = teamTwoOffsides.coerceAtLeast(0),
            falseStarts = teamTwoFalseStarts.coerceAtLeast(0),
        ),
        lastEvent = "Pull infractions adjusted.",
    ).withUndo(state, "Undo Pull Infraction Adjustment")
}

// Swap which team is on which end of the field.
fun swapFieldEnds(state: LiveGameState): LiveGameState {
    val newPullingFromEnd = state.pullingFromEnd.flip()
    return state.copy(
        nearAttackingTeam = state.nearAttackingTeam.flip(),
        pullingFromEnd = newPullingFromEnd,
        countdown = updatedCountdownForPullingFromEnd(state.countdown, newPullingFromEnd),
        pullSequenceOffsidesRecorded = false,
        pullSequenceFalseStartRecorded = false,
        lastEvent = "Field ends swapped.",
    ).withUndo(state, "Undo Swap Ends of Field")
}

// Swap which team is pulling.
fun swapPullingTeam(state: LiveGameState): LiveGameState {
    val newPullingTeam = state.pullingTeam.flip()
    val newPullingFromEnd = state.pullingFromEnd.flip()
    return state.copy(
        pullingTeam = newPullingTeam,
        pullingFromEnd = newPullingFromEnd,
        countdown = updatedCountdownForPullingFromEnd(state.countdown, newPullingFromEnd),
        pullSequenceOffsidesRecorded = false,
        pullSequenceFalseStartRecorded = false,
        lastEvent = "Pulling team swapped.",
    ).withUndo(state, "Undo Swap Pulling Team")
}

// Manually apply one of the caps
fun makeCapNow(state: LiveGameState, capType: CapType, now: LocalTime): LiveGameState {
    val offsetMinutes = when (capType) {
        CapType.HALF -> state.rules.halfCapMinutes
        CapType.SOFT -> state.rules.softCapMinutes
        CapType.HARD -> state.rules.hardCapMinutes
    }
    return state.copy(
        rules = when (capType) {
            CapType.HALF -> state.rules.copy(useHalfCap = true)
            CapType.SOFT -> state.rules.copy(useSoftCap = true)
            CapType.HARD -> state.rules.copy(useHardCap = true)
        },
        startTime = now.minusMinutes(offsetMinutes.toLong()),
        lastEvent = "${capType.name.lowercase().replaceFirstChar { it.uppercase() }} cap set to now.",
    ).withUndo(state, "Undo ${capType.name.lowercase().replaceFirstChar { it.uppercase() }} Cap Now")
}

// Apply the next cap due to its time being reached.
// This is run when we have asked the user whether to apply the next pending cap,
// and they agree to apply it.
fun applyPendingCap(
    state: LiveGameState,
    now: LocalTime,
): LiveGameState {
    val pendingCap = state.pendingCapOffer ?: return state
    val currentHigherScore = max(state.teamOne.score, state.teamTwo.score)
    return when (pendingCap) {
        CapType.HALF -> state.copy(
            halftimeTargetScore = currentHigherScore + 1,
            halfCapApplied = true,
            pendingCapOffer = null,
            lastEvent = "Half cap applied.",
        ).withUndo(state, "Undo Apply Half Cap")

        CapType.SOFT -> state.copy(
            winningScore = currentHigherScore + 1,
            softCapApplied = true,
            pendingCapOffer = null,
            lastEvent = "Soft cap applied.",
        ).withUndo(state, "Undo Apply Soft Cap")

        CapType.HARD -> {
            if (state.teamOne.score != state.teamTwo.score) {
                state.copy(
                    endTime = now,
                    phase = LivePhase.GAME_OVER,
                    countdown = null,
                    hardCapApplied = true,
                    pendingCapOffer = null,
                    lastEvent = "Game over.",
                ).withUndo(state, "Undo Apply Hard Cap")
            } else {
                state.copy(
                    winningScore = currentHigherScore + 1,
                    hardCapApplied = true,
                    pendingCapOffer = null,
                    lastEvent = "Hard cap applied.",
                ).withUndo(state, "Undo Apply Hard Cap")
            }
        }
    }
}

// Don't apply the next cap due to its time being reached.
// This is run when we have asked the user whether to apply the next pending cap,
// and they decide not to apply it yet.
fun deferPendingCap(state: LiveGameState): LiveGameState {
    return state.copy(pendingCapOffer = null, lastEvent = "Cap offer deferred.")
}

// Undo the last action.
fun undoLastAction(state: LiveGameState): LiveGameState {
    return state.undoEntry?.previous ?: state
}

// Adjust the game setup after the game has already started.
fun applySetupToLiveGame(
    existing: LiveGameState,
    setup: GameSetupState,
    nowMillis: Long,
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
        startTime = setup.startTime,
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

    return if (shouldResyncPullState) {
        val updated = base.copy(
            nearAttackingTeam = openingNearAttackingTeam,
            pullingTeam = setup.pullingTeam,
            pullingFromEnd = setup.pullingFromEnd,
        )
        if (updated.phase == LivePhase.BETWEEN_POINTS || updated.phase == LivePhase.HALFTIME) {
            startPullSequence(updated, nowMillis)
        } else {
            updated
        }
    } else {
        base
    }
}

// Go to setup screen from live game
// This just extracts the information from the live state that the setup screen needs.
fun liveGameToSetupState(state: LiveGameState): GameSetupState {
    return GameSetupState(
        startTime = state.startTime,
        rules = state.rules,
        teamOne = TeamSetup(
            name = state.teamOne.name,
            color = state.teamOne.color,
        ),
        teamTwo = TeamSetup(
            name = state.teamTwo.name,
            color = state.teamTwo.color,
        ),
        priorCards = state.priorCards,
        pullingTeam = state.openingPullingTeam,
        pullingFromEnd = state.openingPullingFromEnd,
    )
}

// Someone called a timeout.
// This records another used timeout and starts or extends the appropriate countdown.
fun assessTimeout(
    state: LiveGameState,
    team: TeamId,
    nowMillis: Long,
): TimeoutAssessmentResult {
    val timeoutState = timeoutEligibleState(state, nowMillis)
        ?: return TimeoutAssessmentResult(state, "Timeouts are not available now.")
    if (timeoutsRemaining(timeoutState, team) <= 0) {
        return TimeoutAssessmentResult(state, "${teamName(state, team)} is out of timeouts.")
    }
    return TimeoutAssessmentResult(chargeTimeout(timeoutState, team, nowMillis))
}

fun chargeTimeout(
    state: LiveGameState,
    team: TeamId,
    nowMillis: Long,
): LiveGameState {
    val timeoutState = timeoutEligibleState(state, nowMillis) ?: return state
    if (timeoutsRemaining(timeoutState, team) <= 0) {
        return state
    }

    val updatedState = timeoutState.copy(
        teamOne = if (team == TeamId.TEAM_ONE) {
            timeoutState.teamOne.withAddedTimeout()
        } else {
            timeoutState.teamOne
        },
        teamTwo = if (team == TeamId.TEAM_TWO) {
            timeoutState.teamTwo.withAddedTimeout()
        } else {
            timeoutState.teamTwo
        },
        lastEvent = "Timeout charged to ${teamName(timeoutState, team)}."
    )

    return when (timeoutState.phase) {
        LivePhase.BETWEEN_POINTS -> applyBetweenPointsTimeout(updatedState)
            .withUndo(state, "Undo Timeout by ${teamName(timeoutState, team)}")
        LivePhase.LIVE_POINT -> applyLivePointTimeout(updatedState, nowMillis)
            .withUndo(state, "Undo Timeout by ${teamName(timeoutState, team)}")
        else -> updatedState
    }
}

// Offsides on the pulling team
fun recordOffsides(state: LiveGameState): LiveGameState {
    if (state.pullSequenceOffsidesRecorded) {
        return state
    }
    val team = state.pullingTeam
    return state.copy(
        teamOne = if (team == TeamId.TEAM_ONE) {
            state.teamOne.copy(offsides = state.teamOne.offsides + 1)
        } else {
            state.teamOne
        },
        teamTwo = if (team == TeamId.TEAM_TWO) {
            state.teamTwo.copy(offsides = state.teamTwo.offsides + 1)
        } else {
            state.teamTwo
        },
        phase = LivePhase.LIVE_POINT,
        countdown = null,
        pullSequenceOffsidesRecorded = true,
        lastEvent = "Offsides on ${teamName(state, team)}.",
    ).withUndo(state, "Undo Offsides on ${teamName(state, team)}")
}

// False start on the receiving team
fun recordFalseStart(state: LiveGameState): LiveGameState {
    if (state.pullSequenceFalseStartRecorded) {
        return state
    }
    val team = state.pullingTeam.flip()
    return state.copy(
        teamOne = if (team == TeamId.TEAM_ONE) {
            state.teamOne.copy(falseStarts = state.teamOne.falseStarts + 1)
        } else {
            state.teamOne
        },
        teamTwo = if (team == TeamId.TEAM_TWO) {
            state.teamTwo.copy(falseStarts = state.teamTwo.falseStarts + 1)
        } else {
            state.teamTwo
        },
        pullSequenceFalseStartRecorded = true,
        lastEvent = "False start on ${teamName(state, team)}.",
    ).withUndo(state, "Undo False Start on ${teamName(state, team)}")
}

// Terse field-position cue shown after recording false start.
fun falseStartResolutionMessage(): String {
    return "Defense gets to set up."
}

// Prompt shown when live-point misconduct needs an offense/defense choice.
fun livePointMisconductPrompt(baseMessage: String): String {
    return "$baseMessage\n\nWas this against the offense or defense?"
}

// Full live-point misconduct message after the observer chooses offense or defense.
fun livePointMisconductResolutionMessage(baseMessage: String, againstOffense: Boolean): String {
    return "$baseMessage\n\n${livePointMisconductMessage(againstOffense)}"
}

// Short cap name for prompt titles/buttons.
fun capOfferLabel(capType: CapType): String {
    return when (capType) {
        CapType.HALF -> "half cap"
        CapType.SOFT -> "soft cap"
        CapType.HARD -> "hard cap"
    }
}

// Full text for the apply-cap confirmation dialog.
fun capOfferExplanation(state: LiveGameState): String {
    val wasAt = if (state.phase == LivePhase.HALFTIME) "is scheduled for" else "was at"
    val endWhen = if (state.phase == LivePhase.HALFTIME) "during halftime" else "now"
    return when (state.pendingCapOffer) {
        CapType.HALF -> {
            val target = max(state.teamOne.score, state.teamTwo.score) + 1
            "Half cap was at ${formatClockTime(state.startTime.plusMinutes(state.rules.halfCapMinutes.toLong()))}. Halftime target would become $target. Apply now?"
        }
        CapType.SOFT -> {
            val target = max(state.teamOne.score, state.teamTwo.score) + 1
            "Soft cap $wasAt ${formatClockTime(state.startTime.plusMinutes(state.rules.softCapMinutes.toLong()))}. Winning score would become $target. Apply now?"
        }
        CapType.HARD -> {
            if (state.teamOne.score == state.teamTwo.score) {
                "Hard cap $wasAt ${formatClockTime(state.startTime.plusMinutes(state.rules.hardCapMinutes.toLong()))}. Score is tied, so one more point would be played. Apply now?"
            } else {
                "Hard cap $wasAt ${formatClockTime(state.startTime.plusMinutes(state.rules.hardCapMinutes.toLong()))}. Score is not tied, so the game would end $endWhen. Apply now?"
            }
        }
        null -> ""
    }
}

// Terse field-position cue shown after recording offsides.
fun offsidesResolutionMessage(state: LiveGameState, teamId: TeamId): String {
    val team = if (teamId == TeamId.TEAM_ONE) state.teamOne else state.teamTwo
    val pullViolations = team.offsides + team.falseStarts
    return if (pullViolations <= 1) {
        "Start at brick mark"
    } else {
        "Start at midfield"
    }
}

// Technical Foul
fun addTechnicalFoul(state: LiveGameState, team: TeamId): LiveGameState {
    return state.copy(
        teamOne = if (team == TeamId.TEAM_ONE) {
            state.teamOne.copy(technicalFouls = state.teamOne.technicalFouls + 1)
        } else {
            state.teamOne
        },
        teamTwo = if (team == TeamId.TEAM_TWO) {
            state.teamTwo.copy(technicalFouls = state.teamTwo.technicalFouls + 1)
        } else {
            state.teamTwo
        },
        lastEvent = "Technical foul on ${teamName(state, team)}.",
    )
}

// Blue Card
fun addBlueCard(state: LiveGameState, team: TeamId): LiveGameState {
    return state.copy(
        teamOne = if (team == TeamId.TEAM_ONE) {
            state.teamOne.copy(blueCards = state.teamOne.blueCards + 1)
        } else {
            state.teamOne
        },
        teamTwo = if (team == TeamId.TEAM_TWO) {
            state.teamTwo.copy(blueCards = state.teamTwo.blueCards + 1)
        } else {
            state.teamTwo
        },
        lastEvent = "Blue card assessed to ${teamName(state, team)}.",
    )
}

// Blue cards again.  This also checks what the consequence is.
fun assessBlueCard(state: LiveGameState, team: TeamId): CardAssessmentResult {
    val updatedState = addBlueCard(state, team)
        .withUndo(state, "Undo Blue Card on ${teamName(state, team)}")
    val cardTotal = teamCardTotal(updatedState, team)
    return CardAssessmentResult(
        state = updatedState,
        message = buildCardMessage(
            baseMessage = "${teamName(updatedState, team)} has $cardTotal ${pluralize(cardTotal, "card")}.",
            state = updatedState,
            team = team,
            thresholdCount = cardTotal,
        ),
        needsLivePointMisconductChoice = cardTotal >= 3 && updatedState.phase == LivePhase.LIVE_POINT,
    )
}

// Technical Fouls again.  This also checks what the consequence is.
fun assessTechnicalFoul(state: LiveGameState, team: TeamId): CardAssessmentResult {
    val updatedState = addTechnicalFoul(state, team)
        .withUndo(state, "Undo Technical Foul on ${teamName(state, team)}")
    val technicalFouls = teamTechnicalFouls(updatedState, team)
    return CardAssessmentResult(
        state = updatedState,
        message = buildTechnicalFoulMessage(
            baseMessage = "${teamName(updatedState, team)} has $technicalFouls technical ${pluralize(technicalFouls, "foul")}.",
            state = updatedState,
            team = team,
            thresholdCount = technicalFouls,
        ),
        needsLivePointMisconductChoice = technicalFouls >= 3 && updatedState.phase == LivePhase.LIVE_POINT,
    )
}

// Yellow Card again.  This also checks what the consequence is.
// It could be one of two things depending on whether it's the first or second yellow.
fun assessYellowCard(state: LiveGameState, team: TeamId, jerseyNumber: String): CardAssessmentResult {
    val currentRecord = state.playerCardFor(team, jerseyNumber)
    return if (currentRecord?.yellows ?: 0 >= 1) {
        assessRedCard(state, team, jerseyNumber, RedCardMode.SECOND_YELLOW)
    } else {
        assessStandaloneYellowCard(state, team, jerseyNumber)
    }
}

// Figure out the consequence for a standalone yellow.
fun assessStandaloneYellowCard(state: LiveGameState, team: TeamId, jerseyNumber: String): CardAssessmentResult {
    val updatedState = addInGameYellowCard(state, team, jerseyNumber)
        .withUndo(state, "Undo Yellow Card on ${teamName(state, team)} #$jerseyNumber")
    val cardTotal = teamCardTotal(updatedState, team)
    return CardAssessmentResult(
        state = updatedState,
        message = buildCardMessage(
            baseMessage = "${teamName(updatedState, team)} has $cardTotal ${pluralize(cardTotal, "card")}.",
            state = updatedState,
            team = team,
            thresholdCount = cardTotal,
        ),
        needsLivePointMisconductChoice = cardTotal >= 3 && updatedState.phase == LivePhase.LIVE_POINT,
    )
}

// Red Card again.  This also checks what the consequence is.
fun assessRedCard(
    state: LiveGameState,
    team: TeamId,
    jerseyNumber: String,
    mode: RedCardMode,
): CardAssessmentResult {
    val updatedState = when (mode) {
        RedCardMode.DIRECT_RED -> addInGameDirectRed(state, team, jerseyNumber)
            .withUndo(state, "Undo Direct Red on ${teamName(state, team)} #$jerseyNumber")
        RedCardMode.SECOND_YELLOW -> addInGameSecondYellow(state, team, jerseyNumber)
            .withUndo(state, "Undo Second Yellow on ${teamName(state, team)} #$jerseyNumber")
    }
    val cardTotal = teamCardTotal(updatedState, team)
    val baseMessage = when (mode) {
        RedCardMode.DIRECT_RED -> "${teamName(updatedState, team)} has $cardTotal ${pluralize(cardTotal, "card")}."
        RedCardMode.SECOND_YELLOW -> {
            val ejectionMessage = if (jerseyNumber == UNKNOWN_PLAYER_NUMBER) {
                "The player is ejected."
            } else {
                "Player $jerseyNumber is ejected."
            }
            "Second yellow acts as a red card. $ejectionMessage\n${teamName(updatedState, team)} has $cardTotal ${pluralize(cardTotal, "card")}."
        }
    }
    return CardAssessmentResult(
        state = updatedState,
        message = buildCardMessage(
            baseMessage = baseMessage,
            state = updatedState,
            team = team,
            thresholdCount = cardTotal,
        ),
        needsLivePointMisconductChoice = cardTotal >= 3 && updatedState.phase == LivePhase.LIVE_POINT,
    )
}

enum class CardType(val label: String) {
    YELLOW("Yellow"),
    RED("Red"),
}

// Figure out what the next relevant cap is in a live game.
fun computeNextCapStatus(state: LiveGameState, now: LocalTime): CapStatus? {
    // `to` in Kotlin makes pairs. So `first to second` makes a pair (first, second).
    // Here we make pairs with second being another pair:
    // (isCapRelevant, (capName, capTime))
    val caps = listOf(
        halfCapRelevant(state, state.teamOne.score, state.teamTwo.score) to
            ("Half cap" to state.startTime.plusMinutes(state.rules.halfCapMinutes.toLong())),
        softCapRelevant(state) to
            ("Soft cap" to state.startTime.plusMinutes(state.rules.softCapMinutes.toLong())),
        hardCapRelevant(state) to
            ("Hard cap" to state.startTime.plusMinutes(state.rules.hardCapMinutes.toLong())),
    )
        // Keep only caps whose relevance flag is true.
        .filter { it.first }
        // Keep just the (label, time) pair.
        .map { it.second }

    return caps
        // Convert each capTime into the time left from now until the cap.
        .map { (label, capTime) -> label to durationUntil(now, capTime) }
        // Find the first one whose duration is not negative.
        .firstOrNull { (_, remaining) -> !remaining.isNegative }
        // If any are found, make a CapStatus from this cap's time remaining.
        ?.let { (label, remaining) -> CapStatus(label, remaining) }
}

// Format the time into a nice string like "3:30 PM"
fun formatClockTime(time: LocalTime): String {
    return time.format(DateTimeFormatter.ofPattern("h:mm a"))
}

// Format a duration into a nice format like "0:32"
fun formatDuration(duration: Duration): String {
    val totalSeconds = max(0L, duration.seconds)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

// Calculate the remaining duration between now and target.
private fun durationUntil(now: LocalTime, target: LocalTime): Duration {
    var remaining = Duration.between(now, target)
    if (remaining.isNegative && target.isBefore(now)) {
        remaining = Duration.between(now, target.plusHours(24))
    }
    return remaining
}

private fun halfCapRelevant(state: LiveGameState, teamOneScore: Int, teamTwoScore: Int): Boolean {
    return state.rules.useHalfCap &&
        !state.halftimeTaken &&
        !state.halfCapApplied &&
        halfCapCanChangeHalftime(state.rules, teamOneScore, teamTwoScore)
}

private fun softCapRelevant(state: LiveGameState): Boolean {
    return state.rules.useSoftCap && !state.softCapApplied
}

private fun hardCapRelevant(state: LiveGameState): Boolean {
    return state.rules.useHardCap && !state.hardCapApplied
}

private fun halfCapReached(
    state: LiveGameState,
    teamOneScore: Int,
    teamTwoScore: Int,
    now: LocalTime,
): Boolean {
    return halfCapRelevant(state, teamOneScore, teamTwoScore) &&
        !now.isBefore(state.startTime.plusMinutes(state.rules.halfCapMinutes.toLong()))
}

private fun softCapReached(state: LiveGameState, now: LocalTime): Boolean {
    return softCapRelevant(state) &&
        !now.isBefore(state.startTime.plusMinutes(state.rules.softCapMinutes.toLong()))
}

private fun hardCapReached(state: LiveGameState, now: LocalTime): Boolean {
    return hardCapRelevant(state) &&
        !now.isBefore(state.startTime.plusMinutes(state.rules.hardCapMinutes.toLong()))
}

private fun isTimeInHalfOpenInterval(time: LocalTime, start: LocalTime, end: LocalTime): Boolean {
    return !time.isBefore(start) && time.isBefore(end)
}

private fun capDuringHalftimeOffer(state: LiveGameState, halftimeStart: LocalTime): CapType? {
    return when {
        hardCapRelevant(state) && capTimeFallsDuringHalftime(
            capTime = state.startTime.plusMinutes(state.rules.hardCapMinutes.toLong()),
            halftimeStart = halftimeStart,
            halftimeMinutes = state.rules.halftimeMinutes,
        ) -> CapType.HARD
        softCapRelevant(state) && capTimeFallsDuringHalftime(
            capTime = state.startTime.plusMinutes(state.rules.softCapMinutes.toLong()),
            halftimeStart = halftimeStart,
            halftimeMinutes = state.rules.halftimeMinutes,
        ) -> CapType.SOFT
        else -> null
    }
}

private fun capTimeFallsDuringHalftime(
    capTime: LocalTime,
    halftimeStart: LocalTime,
    halftimeMinutes: Int,
): Boolean {
    return isTimeInHalfOpenInterval(
        time = capTime,
        start = halftimeStart,
        end = halftimeStart.plusMinutes(halftimeMinutes.toLong()),
    )
}

// Build a countdown after a goal is scored.
// This is different depending on whether the observer is on the side of the pulling
// or receiving team.  (Observer is assumed on the near end.)
private fun buildBetweenPointsCountdown(
    pullingFromEnd: FieldEnd,
    sequenceStartMillis: Long,
): CountdownState {
    val pullFromNearEnd = pullingFromEnd == FieldEnd.NEAR
    val durationSeconds = if (pullFromNearEnd) 80 else 60
    val label = if (pullFromNearEnd) "Pull in" else "Signal in"
    return CountdownState(
        kind = CountdownKind.BETWEEN_POINTS,
        label = label,
        durationSeconds = durationSeconds,
        targetEpochMillis = sequenceStartMillis + durationSeconds * 1000L,
    )
}

// Visible between-points countdown text for the currently responsible side of the field.
fun betweenPointsDisplay(
    pullingFromEnd: FieldEnd,
    sequenceStartMillis: Long,
    nowMillis: Long,
): Pair<String, Duration> {
    val countdown = buildBetweenPointsCountdown(pullingFromEnd, sequenceStartMillis)
    return countdown.label to Duration.ofMillis((countdown.targetEpochMillis - nowMillis).coerceAtLeast(0L))
}

// Build a countdown for half time.
private fun buildHalftimeCountdown(
    halftimeMinutes: Int,
    sequenceStartMillis: Long,
): CountdownState {
    val durationSeconds = halftimeMinutes * 60
    return CountdownState(
        kind = CountdownKind.HALFTIME,
        label = "Halftime",
        durationSeconds = durationSeconds,
        targetEpochMillis = sequenceStartMillis + durationSeconds * 1000L,
    )
}

// Calculate how many time outs are allowed in the current half according to the rules.
fun timeoutsAllowedThisHalf(state: LiveGameState, team: TeamId): Int {
    val firstHalfAllowance = state.rules.timeoutsPerHalf + if (state.rules.hasFloaterTimeout) 1 else 0
    if (!state.halftimeTaken) {
        return firstHalfAllowance
    }

    val firstHalfTimeoutsUsed = teamState(state, team).firstHalfTimeoutsUsed ?: 0
    val floaterCarries = state.rules.hasFloaterTimeout && firstHalfTimeoutsUsed < firstHalfAllowance
    return state.rules.timeoutsPerHalf + if (floaterCarries) 1 else 0
}

// Calculate how many time outs are still available in the current half.
fun timeoutsRemaining(state: LiveGameState, team: TeamId): Int {
    val usedThisHalf = teamState(state, team).timeoutsUsedThisHalf
    return (timeoutsAllowedThisHalf(state, team) - usedThisHalf).coerceAtLeast(0)
}

// Return the state in which a timeout may be charged, if the rules allow one now.
private fun timeoutEligibleState(state: LiveGameState, nowMillis: Long): LiveGameState? {
    val advancedState = advanceGameClock(state, nowMillis)
    return when (advancedState.phase) {
        LivePhase.BETWEEN_POINTS, LivePhase.LIVE_POINT -> advancedState
        LivePhase.HALFTIME -> null
        else -> null
    }
}

// Apply a timeout between points.  (Basically just adds 70 sec to the timer.)
private fun applyBetweenPointsTimeout(
    state: LiveGameState,
): LiveGameState {
    val countdown = state.countdown!!
    return state.copy(
        countdown = countdown.copy(
            durationSeconds = countdown.durationSeconds + 70,
            targetEpochMillis = countdown.targetEpochMillis + 70_000L,
        )
    )
}

// Apply a timeout by the thrower during a live point.
private fun applyLivePointTimeout(
    state: LiveGameState,
    nowMillis: Long,
): LiveGameState {
    return state.copy(
        countdown = CountdownState(
            kind = CountdownKind.TIME_OUT,
            label = "Offense set in",
            durationSeconds = 70,
            targetEpochMillis = nowMillis + 70_000L,
        ),
    )
}

// Update the running countdown when swapping pulling team or team ends.
private fun updatedCountdownForPullingFromEnd(
    countdown: CountdownState?,
    pullingFromEnd: FieldEnd,
): CountdownState? {
    countdown ?: return null
    if (countdown.kind == CountdownKind.TIME_OUT || countdown.kind == CountdownKind.HALFTIME) {
        return countdown
    }
    val sequenceStartMillis = countdown.targetEpochMillis - countdown.durationSeconds * 1000L
    return when (countdown.durationSeconds) {
        60, 80 -> buildBetweenPointsCountdown(pullingFromEnd, sequenceStartMillis)
        else -> countdown
    }
}

// Add a yellow card to a specific player
private fun addInGameYellowCard(state: LiveGameState, team: TeamId, jerseyNumber: String): LiveGameState {
    return state.withPlayerCards(
        team = team,
        records = updatePlayerCardRecord(
            records = state.playerCardsFor(team),
            jerseyNumber = jerseyNumber,
        ) { record ->
            record.copy(yellows = record.yellows + 1)
        },
        lastEvent = "Yellow card for ${teamName(state, team)} #$jerseyNumber.",
    )
}

// Add a second yellow card to a specific player
private fun addInGameSecondYellow(state: LiveGameState, team: TeamId, jerseyNumber: String): LiveGameState {
    return state.withPlayerCards(
        team = team,
        records = updatePlayerCardRecord(
            records = state.playerCardsFor(team),
            jerseyNumber = jerseyNumber,
        ) { record ->
            record.copy(yellows = record.yellows + 1)
        },
        lastEvent = "Second yellow for ${teamName(state, team)} #$jerseyNumber.",
    )
}

// Add a direct red card to a specific player
private fun addInGameDirectRed(state: LiveGameState, team: TeamId, jerseyNumber: String): LiveGameState {
    return state.withPlayerCards(
        team = team,
        records = updatePlayerCardRecord(
            records = state.playerCardsFor(team),
            jerseyNumber = jerseyNumber,
        ) { record ->
            record.copy(directReds = record.directReds + 1)
        },
        lastEvent = "Direct red for ${teamName(state, team)} #$jerseyNumber.",
    )
}

// Handle the details of updating a player's card status in the list of carded players.
private fun updatePlayerCardRecord(
    records: List<InGamePlayerCardRecord>,
    jerseyNumber: String,
    transform: (InGamePlayerCardRecord) -> InGamePlayerCardRecord,
): List<InGamePlayerCardRecord> {
    val existingIndex = records.indexOfFirst { it.jerseyNumber == jerseyNumber }
    return if (existingIndex >= 0) {
        records.mapIndexed { index, record ->
            if (index == existingIndex) transform(record) else record
        }
    } else {
        records + transform(InGamePlayerCardRecord(jerseyNumber = jerseyNumber))
    }
}

// Build the message for what happens when a team gets a card.
private fun buildCardMessage(
    baseMessage: String,
    state: LiveGameState,
    team: TeamId,
    thresholdCount: Int,
): String {
    return if (thresholdCount < 3) {
        baseMessage
    } else if (state.phase == LivePhase.LIVE_POINT) {
        baseMessage
    } else {
        "$baseMessage\n\n${betweenPointsMisconductMessage(state, team)}"
    }
}

// Build the message for what happens when a team gets a technical foul.
private fun buildTechnicalFoulMessage(
    baseMessage: String,
    state: LiveGameState,
    team: TeamId,
    thresholdCount: Int,
): String {
    return if (thresholdCount < 3) {
        baseMessage
    } else if (state.phase == LivePhase.LIVE_POINT) {
        baseMessage
    } else {
        "$baseMessage\n\n${betweenPointsMisconductMessage(state, team)}"
    }
}

// Build the portion of the message for misconduct penalty between points.
private fun betweenPointsMisconductMessage(state: LiveGameState, team: TeamId): String {
    val receivingTeam = state.pullingTeam.flip()
    return if (team == receivingTeam) {
        "Penalty against receiving team. No pull. Disc at negative brick in defending end zone."
    } else {
        "Penalty against pulling team. No pull. Receiving team starts at attacking brick."
    }
}

// Build the portion of the message for misconduct penalty during points.
fun livePointMisconductMessage(againstOffense: Boolean): String {
    return if (againstOffense) {
        "Misconduct penalty against offense.\nReverse brick. Offense 30 sec to set, defense 20 sec to check in. Defense may instead leave the disc where it stopped."
    } else {
        "Misconduct penalty against defense.\nBrick nearest attacking end zone. Offense 30 sec to set, defense 20 sec to check in. Offense may instead leave it or center it."
    }
}

// Check if a player already has a yellow card yet.
fun playerHasYellowThisGame(state: LiveGameState, team: TeamId, jerseyNumber: String): Boolean {
    return (state.playerCardFor(team, jerseyNumber)?.yellows ?: 0) > 0
}

// Return the player-card records for one team.
fun playerCards(state: LiveGameState, team: TeamId): List<InGamePlayerCardRecord> {
    return state.playerCardsFor(team)
}

// Count in-game yellow cards from the player-card records.
fun teamYellowCards(state: LiveGameState, team: TeamId): Int {
    return state.playerCardsFor(team).sumOf { it.yellows }
}

// Count in-game direct red cards from the player-card records.
fun teamRedCards(state: LiveGameState, team: TeamId): Int {
    return state.playerCardsFor(team).sumOf { it.directReds }
}

// Count the total number of cards a team has.
fun teamCardTotal(state: LiveGameState, team: TeamId): Int {
    val currentTeam = if (team == TeamId.TEAM_ONE) state.teamOne else state.teamTwo
    return teamYellowCards(state, team) + currentTeam.blueCards + (2 * teamRedCards(state, team))
}

// Count the total number of TF a team has.
private fun teamTechnicalFouls(state: LiveGameState, team: TeamId): Int {
    val currentTeam = if (team == TeamId.TEAM_ONE) state.teamOne else state.teamTwo
    return currentTeam.technicalFouls
}

// Helper function to pluralize nicely.
private fun pluralize(count: Int, singular: String): String {
    return if (count == 1) singular else "${singular}s"
}

// Get the team name for a given id
private fun teamName(state: LiveGameState, team: TeamId): String {
    return if (team == TeamId.TEAM_ONE) state.teamOne.name else state.teamTwo.name
}

// Move to live-point state while preserving the previous user-action undo entry.
private fun automaticLivePointState(state: LiveGameState): LiveGameState {
    return state.copy(
        phase = LivePhase.LIVE_POINT,
        countdown = null,
        pullSequenceOffsidesRecorded = false,
        pullSequenceFalseStartRecorded = false,
        lastEvent = "Point is live.",
    )
}

// Clear an in-point timeout countdown while preserving the timeout undo entry.
private fun automaticContinueLivePointState(state: LiveGameState): LiveGameState {
    return state.copy(
        phase = LivePhase.LIVE_POINT,
        countdown = null,
        lastEvent = "Point continued.",
    )
}

// Get the live state for one team.
private fun teamState(state: LiveGameState, team: TeamId): TeamLiveState {
    return if (team == TeamId.TEAM_ONE) state.teamOne else state.teamTwo
}

// Attach the previous game state in the undoEntry.
// Use this everywhere we want an action to be undo-able (essentially all user actions).
// Most returned states for a user-initiated action should have a .withUndo(state, label)
// at the end.
private fun LiveGameState.withUndo(previous: LiveGameState, label: String): LiveGameState {
    return copy(undoEntry = UndoEntry(label = label, previous = previous))
}

// Get the card record for a specific player.
private fun LiveGameState.playerCardsFor(team: TeamId): List<InGamePlayerCardRecord> {
    return if (team == TeamId.TEAM_ONE) teamOnePlayerCards else teamTwoPlayerCards
}

private fun LiveGameState.withPlayerCards(
    team: TeamId,
    records: List<InGamePlayerCardRecord>,
    lastEvent: String,
): LiveGameState {
    return when (team) {
        TeamId.TEAM_ONE -> copy(
            teamOnePlayerCards = records,
            lastEvent = lastEvent,
        )
        TeamId.TEAM_TWO -> copy(
            teamTwoPlayerCards = records,
            lastEvent = lastEvent,
        )
    }
}

private fun LiveGameState.playerCardFor(team: TeamId, jerseyNumber: String): InGamePlayerCardRecord? {
    return playerCardsFor(team).firstOrNull { it.jerseyNumber == jerseyNumber }
}

// Helper to flip TeamId between the two teams.
private fun TeamId.flip(): TeamId {
    return if (this == TeamId.TEAM_ONE) TeamId.TEAM_TWO else TeamId.TEAM_ONE
}

// Helper to flip FieldEnd between the two directions.
private fun FieldEnd.flip(): FieldEnd {
    return if (this == FieldEnd.NEAR) FieldEnd.FAR else FieldEnd.NEAR
}

// Calculate the halftime score as the next count over half the total.  (e.g. 15 -> 8)
private fun halftimeScore(rules: GameRules): Int {
    return (rules.gameTo / 2) + 1
}

// Half cap stops mattering once any next point would leave the target at normal halftime.
private fun halfCapCanChangeHalftime(rules: GameRules, teamOneScore: Int, teamTwoScore: Int): Boolean {
    val normalHalftimeScore = halftimeScore(rules)
    return max(teamOneScore, teamTwoScore) < normalHalftimeScore - 1 &&
        min(teamOneScore, teamTwoScore) < normalHalftimeScore - 2
}

// Get the next time the target time happens.  (Usually today, maybe tomorrow)
private fun nextOccurrenceMillis(time: LocalTime): Long {
    val now = LocalDateTime.now()
    var target = LocalDateTime.of(LocalDate.now(), time)
    if (target.isBefore(now)) {
        target = target.plusDays(1)
    }
    // Convert the target time to a unix time in milliseconds.
    return target.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

// The default start time for a game is the next even half hour after now.
// Figure out what time that is.
private fun nextHalfHourFromNow(now: LocalTime = LocalTime.now()): LocalTime {
    val roundedMinute = when {
        now.minute == 0 && now.second == 0 -> 0
        now.minute < 30 -> 30
        else -> 0
    }
    val baseHour = if (roundedMinute == 0 && now.minute >= 30) now.hour + 1 else now.hour
    return LocalTime.of(baseHour % 24, roundedMinute)
}
