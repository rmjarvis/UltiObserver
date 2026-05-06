package com.example.ultiobserver

import androidx.compose.ui.graphics.Color
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.max

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
    val accent: Color,
    val content: Color,
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
    val priorYellows: Int,
    val priorReds: Int,
)

data class InGamePlayerCardRecord(
    val team: TeamId,
    val jerseyNumber: String,
    val yellows: Int = 0,
    val directReds: Int = 0,
)

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
    val timeoutsAllowedThisHalf: Int,
    val timeoutsRemaining: Int,
    val offsides: Int = 0,
    val falseStarts: Int = 0,
    val technicalFouls: Int = 0,
    val blueCards: Int = 0,
    val yellowCards: Int = 0,
    val redCards: Int = 0,
)

data class CountdownState(
    val kind: CountdownKind,
    val label: String,
    val durationSeconds: Int,
    val targetEpochMillis: Long,
)

enum class CountdownKind {
    BETWEEN_POINTS,
    HALFTIME,
}

data class LiveGameState(
    val startTime: LocalTime,
    val endTime: LocalTime? = null,
    val rules: GameRules,
    val teamOne: TeamLiveState,
    val teamTwo: TeamLiveState,
    val priorCards: List<PlayerCardRecord>,
    val playerCardsThisGame: List<InGamePlayerCardRecord> = emptyList(),
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
    val pendingCapOffer: CapType? = null,
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
    val needsLivePointMisconductChoice: Boolean = false,
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
            timeoutsAllowedThisHalf = firstHalfTimeoutAllowance(setup.rules),
            timeoutsRemaining = firstHalfTimeoutAllowance(setup.rules),
        ),
        teamTwo = TeamLiveState(
            name = setup.teamTwo.name.ifBlank { "Team 2" },
            color = setup.teamTwo.color,
            timeoutsAllowedThisHalf = firstHalfTimeoutAllowance(setup.rules),
            timeoutsRemaining = firstHalfTimeoutAllowance(setup.rules),
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

fun startPullSequence(state: LiveGameState): LiveGameState {
    val countdown = buildBetweenPointsCountdown(
        pullingFromEnd = state.pullingFromEnd,
        sequenceStartMillis = System.currentTimeMillis(),
    )
    return state.copy(
        phase = LivePhase.BETWEEN_POINTS,
        countdown = countdown,
        pullSequenceOffsidesRecorded = false,
        pullSequenceFalseStartRecorded = false,
        lastEvent = "Pull sequence started.",
    )
}

fun recordGoal(state: LiveGameState, scoringTeam: TeamId): LiveGameState {
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
    val currentHigherScore = max(updatedTeamOne.score, updatedTeamTwo.score)
    val nextNearAttackingTeam = state.nearAttackingTeam.flip()
    val nextPullingTeam = scoringTeam
    val nextPullingFromEnd = if (scoringTeam == state.nearAttackingTeam) {
        FieldEnd.NEAR
    } else {
        FieldEnd.FAR
    }
    val gameWinningScore = state.winningScore ?: state.rules.gameTo
    val gameOver = updatedTeamOne.score >= gameWinningScore || updatedTeamTwo.score >= gameWinningScore

    if (gameOver) {
        return state.copy(
            teamOne = updatedTeamOne,
            teamTwo = updatedTeamTwo,
            endTime = LocalTime.now(),
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

    val halftimeScore = state.halftimeTargetScore ?: halftimeScore(state.rules)
    val halftimeReached = !state.halftimeTaken &&
        (updatedTeamOne.score >= halftimeScore || updatedTeamTwo.score >= halftimeScore)

    if (halftimeReached) {
        val secondHalfPullingTeam = state.openingPullingTeam.flip()
        val secondHalfPullingFromEnd = state.openingPullingFromEnd.flip()
        val secondHalfNearAttackingTeam = if (secondHalfPullingFromEnd == FieldEnd.FAR) {
            secondHalfPullingTeam
        } else {
            secondHalfPullingTeam.flip()
        }
        val halftimeCountdown = buildHalftimeCountdown(
            halftimeMinutes = state.rules.halftimeMinutes,
            sequenceStartMillis = System.currentTimeMillis(),
        )

        return state.copy(
            teamOne = updatedTeamOne.copy(
                timeoutsAllowedThisHalf = secondHalfTimeoutAllowance(state.rules, state.teamOne.timeoutsRemaining),
                timeoutsRemaining = secondHalfTimeoutAllowance(state.rules, state.teamOne.timeoutsRemaining),
            ),
            teamTwo = updatedTeamTwo.copy(
                timeoutsAllowedThisHalf = secondHalfTimeoutAllowance(state.rules, state.teamTwo.timeoutsRemaining),
                timeoutsRemaining = secondHalfTimeoutAllowance(state.rules, state.teamTwo.timeoutsRemaining),
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
            pendingCapOffer = null,
            lastEvent = "Halftime.",
        ).withUndo(state, "Undo Goal by ${teamName(state, scoringTeam)}")
    }

    val now = LocalTime.now()
    val halfCapReached = state.rules.useHalfCap &&
        !state.halftimeTaken &&
        !state.halfCapApplied &&
        now >= state.startTime.plusMinutes(state.rules.halfCapMinutes.toLong())
    val softCapReached = state.rules.useSoftCap &&
        !state.softCapApplied &&
        now >= state.startTime.plusMinutes(state.rules.softCapMinutes.toLong())
    val hardCapReached = state.rules.useHardCap &&
        !state.hardCapApplied &&
        now >= state.startTime.plusMinutes(state.rules.hardCapMinutes.toLong())

    val pendingCapOffer = when {
        halfCapReached -> CapType.HALF
        softCapReached -> CapType.SOFT
        hardCapReached -> CapType.HARD
        else -> null
    }

    val countdown = buildBetweenPointsCountdown(
        pullingFromEnd = nextPullingFromEnd,
        sequenceStartMillis = System.currentTimeMillis(),
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

fun undoGameOver(state: LiveGameState): LiveGameState {
    if (state.phase != LivePhase.GAME_OVER) {
        return state
    }
    return startPullSequence(
        state.copy(
            phase = LivePhase.BETWEEN_POINTS,
            countdown = null,
            endTime = null,
            lastEvent = "Game over undone.",
        )
    )
}

fun startHalftimeNow(state: LiveGameState): LiveGameState {
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
        sequenceStartMillis = System.currentTimeMillis(),
    )

    return state.copy(
        teamOne = state.teamOne.copy(
            timeoutsAllowedThisHalf = secondHalfTimeoutAllowance(state.rules, state.teamOne.timeoutsRemaining),
            timeoutsRemaining = secondHalfTimeoutAllowance(state.rules, state.teamOne.timeoutsRemaining),
        ),
        teamTwo = state.teamTwo.copy(
            timeoutsAllowedThisHalf = secondHalfTimeoutAllowance(state.rules, state.teamTwo.timeoutsRemaining),
            timeoutsRemaining = secondHalfTimeoutAllowance(state.rules, state.teamTwo.timeoutsRemaining),
        ),
        pullingTeam = secondHalfPullingTeam,
        pullingFromEnd = secondHalfPullingFromEnd,
        nearAttackingTeam = secondHalfNearAttackingTeam,
        phase = LivePhase.HALFTIME,
        countdown = halftimeCountdown,
        pullSequenceOffsidesRecorded = false,
        pullSequenceFalseStartRecorded = false,
        halftimeTaken = true,
        pendingCapOffer = null,
        lastEvent = "Halftime.",
    ).withUndo(state, "Undo Start Halftime")
}

fun endGameNow(state: LiveGameState): LiveGameState {
    if (state.phase == LivePhase.GAME_OVER) {
        return state
    }
    return state.copy(
        endTime = LocalTime.now(),
        phase = LivePhase.GAME_OVER,
        countdown = null,
        pendingCapOffer = null,
        lastEvent = "Game over.",
    ).withUndo(state, "Undo End Game")
}

fun beginLivePoint(state: LiveGameState): LiveGameState {
    return state.copy(
        phase = LivePhase.LIVE_POINT,
        countdown = null,
        pullSequenceOffsidesRecorded = false,
        pullSequenceFalseStartRecorded = false,
        lastEvent = "Point is live.",
    ).withUndo(state, "Undo Start Point")
}

fun recordGoalFromCurrentState(state: LiveGameState, scoringTeam: TeamId): LiveGameState {
    val livePointState = if (state.phase == LivePhase.BETWEEN_POINTS) {
        beginLivePoint(state)
    } else {
        state
    }
    return recordGoal(livePointState, scoringTeam)
}

fun continueLivePoint(state: LiveGameState): LiveGameState {
    return state.copy(
        phase = LivePhase.LIVE_POINT,
        countdown = null,
        lastEvent = "Point continued.",
    )
}

fun addTimeToCountdown(state: LiveGameState, seconds: Int): LiveGameState {
    val countdown = state.countdown ?: return state
    return state.copy(
        countdown = countdown.copy(targetEpochMillis = countdown.targetEpochMillis + seconds * 1000L),
        lastEvent = "Adjusted timer by ${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}.",
    )
}

fun adjustScore(state: LiveGameState, teamOneScore: Int, teamTwoScore: Int): LiveGameState {
    return state.copy(
        teamOne = state.teamOne.copy(score = teamOneScore.coerceAtLeast(0)),
        teamTwo = state.teamTwo.copy(score = teamTwoScore.coerceAtLeast(0)),
        lastEvent = "Score adjusted.",
    ).withUndo(state, "Undo Score Adjustment")
}

fun adjustTimeouts(state: LiveGameState, teamOneTimeouts: Int, teamTwoTimeouts: Int): LiveGameState {
    return state.copy(
        teamOne = state.teamOne.copy(
            timeoutsRemaining = teamOneTimeouts.coerceIn(0, state.teamOne.timeoutsAllowedThisHalf),
        ),
        teamTwo = state.teamTwo.copy(
            timeoutsRemaining = teamTwoTimeouts.coerceIn(0, state.teamTwo.timeoutsAllowedThisHalf),
        ),
        lastEvent = "Timeouts adjusted.",
    ).withUndo(state, "Undo Timeout Adjustment")
}

fun adjustCardsAndTf(
    state: LiveGameState,
    teamOneYellows: Int,
    teamOneBlues: Int,
    teamOneReds: Int,
    teamOneTechnicalFouls: Int,
    teamTwoYellows: Int,
    teamTwoBlues: Int,
    teamTwoReds: Int,
    teamTwoTechnicalFouls: Int,
    playerCardsThisGame: List<InGamePlayerCardRecord>,
): LiveGameState {
    return state.copy(
        teamOne = state.teamOne.copy(
            yellowCards = teamOneYellows.coerceAtLeast(0),
            blueCards = teamOneBlues.coerceAtLeast(0),
            redCards = teamOneReds.coerceAtLeast(0),
            technicalFouls = teamOneTechnicalFouls.coerceAtLeast(0),
        ),
        teamTwo = state.teamTwo.copy(
            yellowCards = teamTwoYellows.coerceAtLeast(0),
            blueCards = teamTwoBlues.coerceAtLeast(0),
            redCards = teamTwoReds.coerceAtLeast(0),
            technicalFouls = teamTwoTechnicalFouls.coerceAtLeast(0),
        ),
        playerCardsThisGame = playerCardsThisGame,
        lastEvent = "Cards and technical fouls adjusted.",
    ).withUndo(state, "Undo Cards / TF Adjustment")
}

fun addPlayerCardAssignment(
    records: List<InGamePlayerCardRecord>,
    team: TeamId,
    jerseyNumber: String,
    cardType: CardType,
): List<InGamePlayerCardRecord> {
    return updatePlayerCardRecord(records, team, jerseyNumber) { record ->
        when (cardType) {
            CardType.YELLOW -> record.copy(yellows = record.yellows + 1)
            CardType.RED -> record.copy(directReds = record.directReds + 1)
        }
    }
}

fun removePlayerCardAssignment(
    records: List<InGamePlayerCardRecord>,
    team: TeamId,
    jerseyNumber: String,
    cardType: CardType,
): List<InGamePlayerCardRecord> {
    val existingIndex = records.indexOfFirst { it.team == team && it.jerseyNumber == jerseyNumber }
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

fun makeCapNow(state: LiveGameState, capType: CapType, now: LocalTime = LocalTime.now()): LiveGameState {
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

fun applyPendingCap(state: LiveGameState): LiveGameState {
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
                    endTime = LocalTime.now(),
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

fun deferPendingCap(state: LiveGameState): LiveGameState {
    return state.copy(pendingCapOffer = null, lastEvent = "Cap offer deferred.")
}

fun undoLastAction(state: LiveGameState): LiveGameState {
    return state.undoEntry?.previous ?: state
}

fun applySetupToLiveGame(existing: LiveGameState, setup: GameSetupState): LiveGameState {
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
            timeoutsAllowedThisHalf = remappedTimeoutAllowance(existing, TeamId.TEAM_ONE, setup.rules),
            timeoutsRemaining = remappedTimeoutRemaining(existing, TeamId.TEAM_ONE, setup.rules),
        ),
        teamTwo = existing.teamTwo.copy(
            name = setup.teamTwo.name.ifBlank { "Team 2" },
            color = setup.teamTwo.color,
            timeoutsAllowedThisHalf = remappedTimeoutAllowance(existing, TeamId.TEAM_TWO, setup.rules),
            timeoutsRemaining = remappedTimeoutRemaining(existing, TeamId.TEAM_TWO, setup.rules),
        ),
        priorCards = setup.priorCards,
        playerCardsThisGame = existing.playerCardsThisGame,
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
            startPullSequence(updated)
        } else {
            updated
        }
    } else {
        base
    }
}

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

fun chargeTimeout(state: LiveGameState, team: TeamId): LiveGameState {
    val updatedState = state.copy(
        teamOne = if (team == TeamId.TEAM_ONE) {
            state.teamOne.copy(timeoutsRemaining = max(0, state.teamOne.timeoutsRemaining - 1))
        } else {
            state.teamOne
        },
        teamTwo = if (team == TeamId.TEAM_TWO) {
            state.teamTwo.copy(timeoutsRemaining = max(0, state.teamTwo.timeoutsRemaining - 1))
        } else {
            state.teamTwo
        },
        lastEvent = "Timeout charged to ${teamName(state, team)}."
    )

    return when (state.phase) {
        LivePhase.BETWEEN_POINTS -> applyBetweenPointsTimeout(updatedState)
            .withUndo(state, "Undo Timeout by ${teamName(state, team)}")
        LivePhase.LIVE_POINT -> applyLivePointTimeout(updatedState)
            .withUndo(state, "Undo Timeout by ${teamName(state, team)}")
        else -> updatedState
    }
}

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
        pullSequenceOffsidesRecorded = true,
        lastEvent = "Offsides on ${teamName(state, team)}.",
    ).withUndo(state, "Undo Offsides on ${teamName(state, team)}")
}

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

fun addPlayerCard(state: LiveGameState, team: TeamId, card: CardType): LiveGameState {
    return when (team) {
        TeamId.TEAM_ONE -> {
            state.copy(
                teamOne = when (card) {
                    CardType.YELLOW -> state.teamOne.copy(yellowCards = state.teamOne.yellowCards + 1)
                    CardType.RED -> state.teamOne.copy(redCards = state.teamOne.redCards + 1)
                },
                lastEvent = "${card.label} card for ${teamName(state, team)}.",
            )
        }

        TeamId.TEAM_TWO -> {
            state.copy(
                teamTwo = when (card) {
                    CardType.YELLOW -> state.teamTwo.copy(yellowCards = state.teamTwo.yellowCards + 1)
                    CardType.RED -> state.teamTwo.copy(redCards = state.teamTwo.redCards + 1)
                },
                lastEvent = "${card.label} card for ${teamName(state, team)}.",
            )
        }
    }
}

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

fun assessYellowCard(state: LiveGameState, team: TeamId, jerseyNumber: String): CardAssessmentResult {
    val currentRecord = state.playerCardFor(team, jerseyNumber)
    return if (currentRecord?.yellows ?: 0 >= 1) {
        assessRedCard(state, team, jerseyNumber, RedCardMode.SECOND_YELLOW)
    } else {
        assessStandaloneYellowCard(state, team, jerseyNumber)
    }
}

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
        RedCardMode.SECOND_YELLOW -> "Second yellow acts as a red card.\n${teamName(updatedState, team)} has $cardTotal ${pluralize(cardTotal, "card")}."
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

fun computeNextCapStatus(startTime: LocalTime, rules: GameRules, now: LocalTime = LocalTime.now()): CapStatus? {
    val caps = listOf(
        rules.useHalfCap to ("Half cap" to startTime.plusMinutes(rules.halfCapMinutes.toLong())),
        rules.useSoftCap to ("Soft cap" to startTime.plusMinutes(rules.softCapMinutes.toLong())),
        rules.useHardCap to ("Hard cap" to startTime.plusMinutes(rules.hardCapMinutes.toLong())),
    )
        .filter { it.first }
        .map { it.second }

    return caps
        .map { (label, capTime) -> label to durationUntil(now, capTime) }
        .firstOrNull { (_, remaining) -> !remaining.isNegative }
        ?.let { (label, remaining) -> CapStatus(label, remaining) }
}

fun computeNextCapStatus(state: LiveGameState, now: LocalTime = LocalTime.now()): CapStatus? {
    val caps = buildList {
        if (state.rules.useHalfCap && !state.halftimeTaken && !state.halfCapApplied) {
            add("Half cap" to state.startTime.plusMinutes(state.rules.halfCapMinutes.toLong()))
        }
        if (state.rules.useSoftCap && !state.softCapApplied) {
            add("Soft cap" to state.startTime.plusMinutes(state.rules.softCapMinutes.toLong()))
        }
        if (state.rules.useHardCap && !state.hardCapApplied) {
            add("Hard cap" to state.startTime.plusMinutes(state.rules.hardCapMinutes.toLong()))
        }
    }

    return caps
        .map { (label, capTime) -> label to durationUntil(now, capTime) }
        .firstOrNull { (_, remaining) -> !remaining.isNegative }
        ?.let { (label, remaining) -> CapStatus(label, remaining) }
}

fun formatClockTime(time: LocalTime): String {
    return time.format(DateTimeFormatter.ofPattern("h:mm a"))
}

fun formatDuration(duration: Duration): String {
    val totalSeconds = max(0L, duration.seconds)
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

private fun durationUntil(now: LocalTime, target: LocalTime): Duration {
    var remaining = Duration.between(now, target)
    if (remaining.isNegative && target.isBefore(now)) {
        remaining = Duration.between(now, target.plusHours(24))
    }
    return remaining
}

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

private fun firstHalfTimeoutAllowance(rules: GameRules): Int {
    return rules.timeoutsPerHalf + if (rules.hasFloaterTimeout) 1 else 0
}

private fun secondHalfTimeoutAllowance(rules: GameRules, firstHalfRemaining: Int): Int {
    return rules.timeoutsPerHalf + if (rules.hasFloaterTimeout && firstHalfRemaining > 0) 1 else 0
}

private fun remappedTimeoutAllowance(existing: LiveGameState, teamId: TeamId, newRules: GameRules): Int {
    return if (!existing.halftimeTaken) {
        firstHalfTimeoutAllowance(newRules)
    } else {
        val existingTeam = if (teamId == TeamId.TEAM_ONE) existing.teamOne else existing.teamTwo
        val carriedFloater = existing.rules.hasFloaterTimeout &&
            existingTeam.timeoutsAllowedThisHalf > existing.rules.timeoutsPerHalf
        newRules.timeoutsPerHalf + if (newRules.hasFloaterTimeout && carriedFloater) 1 else 0
    }
}

private fun remappedTimeoutRemaining(existing: LiveGameState, teamId: TeamId, newRules: GameRules): Int {
    val existingTeam = if (teamId == TeamId.TEAM_ONE) existing.teamOne else existing.teamTwo
    val usedThisHalf = (existingTeam.timeoutsAllowedThisHalf - existingTeam.timeoutsRemaining).coerceAtLeast(0)
    val newAllowance = remappedTimeoutAllowance(existing, teamId, newRules)
    return (newAllowance - usedThisHalf).coerceAtLeast(0)
}

private fun applyBetweenPointsTimeout(state: LiveGameState): LiveGameState {
    val nowMillis = System.currentTimeMillis()
    val countdown = state.countdown

    val updatedCountdown = if (countdown != null && countdown.targetEpochMillis > nowMillis) {
        countdown.copy(
            durationSeconds = countdown.durationSeconds + 70,
            targetEpochMillis = countdown.targetEpochMillis + 70_000L,
        )
    } else {
        buildBetweenPointsTimeoutCountdown(
            pullingFromEnd = state.pullingFromEnd,
            sequenceStartMillis = nowMillis,
        )
    }

    return state.copy(countdown = updatedCountdown)
}

private fun applyLivePointTimeout(state: LiveGameState): LiveGameState {
    return state.copy(
        countdown = CountdownState(
            kind = CountdownKind.BETWEEN_POINTS,
            label = "Offense set in",
            durationSeconds = 70,
            targetEpochMillis = System.currentTimeMillis() + 70_000L,
        ),
    )
}

private fun buildBetweenPointsTimeoutCountdown(
    pullingFromEnd: FieldEnd,
    sequenceStartMillis: Long,
): CountdownState {
    val pullFromNearEnd = pullingFromEnd == FieldEnd.NEAR
    val durationSeconds = if (pullFromNearEnd) 90 else 70
    val label = if (pullFromNearEnd) "Pull in" else "Signal in"
    return CountdownState(
        kind = CountdownKind.BETWEEN_POINTS,
        label = label,
        durationSeconds = durationSeconds,
        targetEpochMillis = sequenceStartMillis + durationSeconds * 1000L,
    )
}

private fun updatedCountdownForPullingFromEnd(
    countdown: CountdownState?,
    pullingFromEnd: FieldEnd,
): CountdownState? {
    countdown ?: return null
    if (countdown.label == "Offense set in" || countdown.kind == CountdownKind.HALFTIME) {
        return countdown
    }
    val sequenceStartMillis = countdown.targetEpochMillis - countdown.durationSeconds * 1000L
    return when (countdown.durationSeconds) {
        60, 80 -> buildBetweenPointsCountdown(pullingFromEnd, sequenceStartMillis)
        70, 90 -> buildBetweenPointsTimeoutCountdown(pullingFromEnd, sequenceStartMillis)
        else -> countdown
    }
}

private fun addInGameYellowCard(state: LiveGameState, team: TeamId, jerseyNumber: String): LiveGameState {
    return state.copy(
        teamOne = if (team == TeamId.TEAM_ONE) {
            state.teamOne.copy(yellowCards = state.teamOne.yellowCards + 1)
        } else {
            state.teamOne
        },
        teamTwo = if (team == TeamId.TEAM_TWO) {
            state.teamTwo.copy(yellowCards = state.teamTwo.yellowCards + 1)
        } else {
            state.teamTwo
        },
        playerCardsThisGame = updatePlayerCardRecord(
            records = state.playerCardsThisGame,
            team = team,
            jerseyNumber = jerseyNumber,
        ) { record ->
            record.copy(yellows = record.yellows + 1)
        },
        lastEvent = "Yellow card for ${teamName(state, team)} #$jerseyNumber.",
    )
}

private fun addInGameSecondYellow(state: LiveGameState, team: TeamId, jerseyNumber: String): LiveGameState {
    return state.copy(
        teamOne = if (team == TeamId.TEAM_ONE) {
            state.teamOne.copy(yellowCards = state.teamOne.yellowCards + 1)
        } else {
            state.teamOne
        },
        teamTwo = if (team == TeamId.TEAM_TWO) {
            state.teamTwo.copy(yellowCards = state.teamTwo.yellowCards + 1)
        } else {
            state.teamTwo
        },
        playerCardsThisGame = updatePlayerCardRecord(
            records = state.playerCardsThisGame,
            team = team,
            jerseyNumber = jerseyNumber,
        ) { record ->
            record.copy(yellows = record.yellows + 1)
        },
        lastEvent = "Second yellow for ${teamName(state, team)} #$jerseyNumber.",
    )
}

private fun addInGameDirectRed(state: LiveGameState, team: TeamId, jerseyNumber: String): LiveGameState {
    return state.copy(
        teamOne = if (team == TeamId.TEAM_ONE) {
            state.teamOne.copy(redCards = state.teamOne.redCards + 1)
        } else {
            state.teamOne
        },
        teamTwo = if (team == TeamId.TEAM_TWO) {
            state.teamTwo.copy(redCards = state.teamTwo.redCards + 1)
        } else {
            state.teamTwo
        },
        playerCardsThisGame = updatePlayerCardRecord(
            records = state.playerCardsThisGame,
            team = team,
            jerseyNumber = jerseyNumber,
        ) { record ->
            record.copy(directReds = record.directReds + 1)
        },
        lastEvent = "Direct red for ${teamName(state, team)} #$jerseyNumber.",
    )
}

private fun updatePlayerCardRecord(
    records: List<InGamePlayerCardRecord>,
    team: TeamId,
    jerseyNumber: String,
    transform: (InGamePlayerCardRecord) -> InGamePlayerCardRecord,
): List<InGamePlayerCardRecord> {
    val existingIndex = records.indexOfFirst { it.team == team && it.jerseyNumber == jerseyNumber }
    return if (existingIndex >= 0) {
        records.mapIndexed { index, record ->
            if (index == existingIndex) transform(record) else record
        }
    } else {
        records + transform(InGamePlayerCardRecord(team = team, jerseyNumber = jerseyNumber))
    }
}

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

private fun betweenPointsMisconductMessage(state: LiveGameState, team: TeamId): String {
    val receivingTeam = state.pullingTeam.flip()
    return if (team == receivingTeam) {
        "Penalty against receiving team. No pull. Disc at negative brick in defending end zone."
    } else {
        "Penalty against pulling team. No pull. Receiving team starts at attacking brick."
    }
}

fun livePointMisconductMessage(againstOffense: Boolean): String {
    return if (againstOffense) {
        "Misconduct penalty against offense.\nReverse brick. Offense 30 sec to set, defense 20 sec to check in. Defense may instead leave the disc where it stopped."
    } else {
        "Misconduct penalty against defense.\nBrick nearest attacking end zone. Offense 30 sec to set, defense 20 sec to check in. Offense may instead leave it or center it."
    }
}

fun playerHasYellowThisGame(state: LiveGameState, team: TeamId, jerseyNumber: String): Boolean {
    return (state.playerCardFor(team, jerseyNumber)?.yellows ?: 0) > 0
}

private fun teamCardTotal(state: LiveGameState, team: TeamId): Int {
    val currentTeam = if (team == TeamId.TEAM_ONE) state.teamOne else state.teamTwo
    return currentTeam.yellowCards + currentTeam.blueCards + (2 * currentTeam.redCards)
}

private fun teamTechnicalFouls(state: LiveGameState, team: TeamId): Int {
    val currentTeam = if (team == TeamId.TEAM_ONE) state.teamOne else state.teamTwo
    return currentTeam.technicalFouls
}

private fun pluralize(count: Int, singular: String): String {
    return if (count == 1) singular else "${singular}s"
}

private fun teamName(state: LiveGameState, team: TeamId): String {
    return if (team == TeamId.TEAM_ONE) state.teamOne.name else state.teamTwo.name
}

private fun LiveGameState.withUndo(previous: LiveGameState, label: String): LiveGameState {
    return copy(undoEntry = UndoEntry(label = label, previous = previous))
}

private fun LiveGameState.playerCardFor(team: TeamId, jerseyNumber: String): InGamePlayerCardRecord? {
    return playerCardsThisGame.firstOrNull { it.team == team && it.jerseyNumber == jerseyNumber }
}

private fun TeamId.flip(): TeamId {
    return if (this == TeamId.TEAM_ONE) TeamId.TEAM_TWO else TeamId.TEAM_ONE
}

private fun FieldEnd.flip(): FieldEnd {
    return if (this == FieldEnd.NEAR) FieldEnd.FAR else FieldEnd.NEAR
}

private fun halftimeScore(rules: GameRules): Int {
    return (rules.gameTo / 2) + 1
}

private fun nextOccurrenceMillis(time: LocalTime): Long {
    val now = LocalDateTime.now()
    var target = LocalDateTime.of(LocalDate.now(), time)
    if (target.isBefore(now)) {
        target = target.plusDays(1)
    }
    return target.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

private fun nextHalfHourFromNow(now: LocalTime = LocalTime.now()): LocalTime {
    val roundedMinute = when {
        now.minute == 0 && now.second == 0 -> 0
        now.minute < 30 -> 30
        else -> 0
    }
    val baseHour = if (roundedMinute == 0 && now.minute >= 30) now.hour + 1 else now.hour
    return LocalTime.of(baseHour % 24, roundedMinute)
}
