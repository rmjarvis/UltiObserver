package rmjarvis.ultiobserver

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.max

// Make the first live game state after setting the initial setup parameters.
fun createLiveGameState(setup: GameSetupState): LiveGameState {
    val nearAttackingTeam = if (setup.pullingFromEnd == FieldEnd.FAR) {
        setup.pullingTeam
    } else {
        setup.pullingTeam.flip()
    }
    val startEpoch = epochTimestamp(setup.startDate, setup.startTime, setup.timeZone)
    val initialCountdown = buildBetweenPointsCountdown(
        pullingFromEnd = setup.pullingFromEnd,
        sequenceStart = startEpoch,
        kind = CountdownKind.OPENING_PULL,
    )

    return LiveGameState(
        startDate = setup.startDate,
        startTime = setup.startTime,
        timeZone = setup.timeZone,
        startEpoch = startEpoch,
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
fun LiveGameState.startPullSequence(
    now: Long,
): LiveGameState {
    val countdown = buildBetweenPointsCountdown(
        pullingFromEnd = this.pullingFromEnd,
        sequenceStart = now,
    )
    return this.copy(
        phase = LivePhase.BETWEEN_POINTS,
        countdown = countdown,
        pullSequenceOffsidesRecorded = false,
        pullSequenceFalseStartRecorded = false,
        pullSkippedForCurrentPoint = false,
        lastEvent = "Pull sequence started.",
    )
}
// Update state for someone scoring a goal.
fun LiveGameState.recordGoal(
    scoringTeam: TeamId,
    now: Long,
): LiveGameState {
    if (this.phase == LivePhase.GAME_OVER) {
        return this
    }

    val updatedTeamOne = if (scoringTeam == TeamId.TEAM_ONE) {
        this.teamOne.copy(score = this.teamOne.score + 1)
    } else {
        this.teamOne
    }
    val updatedTeamTwo = if (scoringTeam == TeamId.TEAM_TWO) {
        this.teamTwo.copy(score = this.teamTwo.score + 1)
    } else {
        this.teamTwo
    }
    val nextNearAttackingTeam = this.nearAttackingTeam.flip()
    val nextPullingTeam = scoringTeam
    val nextPullingFromEnd = if (scoringTeam == this.nearAttackingTeam) {
        FieldEnd.NEAR
    } else {
        FieldEnd.FAR
    }
    // Do this check every time, because if the user changes the rules.gameTo, we naturally
    // update to the updated rules.  However, if the soft cap has been applied, that takes
    // precedence.
    val gameWinningScore = this.winningScore ?: this.rules.gameTo
    val gameOver = max(updatedTeamOne.score, updatedTeamTwo.score) >= gameWinningScore

    if (gameOver) {
        val afterGoalState = this.copy(
            teamOne = updatedTeamOne,
            teamTwo = updatedTeamTwo,
            pullingTeam = nextPullingTeam,
            nearAttackingTeam = nextNearAttackingTeam,
            pullingFromEnd = nextPullingFromEnd,
            phase = LivePhase.BETWEEN_POINTS,
            countdown = buildBetweenPointsCountdown(
                pullingFromEnd = nextPullingFromEnd,
                sequenceStart = now,
            ),
            pullSequenceOffsidesRecorded = false,
            pullSequenceFalseStartRecorded = false,
            pullSkippedForCurrentPoint = false,
            winningScore = this.winningScore,
            pendingCapOffer = null,
            lastEvent = "${this.teamName(scoringTeam)} scored.",
        ).withUndo(this, "Undo Goal by ${this.teamName(scoringTeam)}")
        return afterGoalState.copy(
            endEpoch = now,
            phase = LivePhase.GAME_OVER,
            countdown = null,
            winningScore = gameWinningScore,
            lastEvent = "Game over.",
        ).withUndo(afterGoalState, "Undo End Game")
    }

    // Caps are checked before halftime so hard cap takes precedence over soft, and soft over half.
    val pendingCapOffer = when {
        this.hardCapReached(now) -> CapType.HARD
        this.softCapReached(now) -> CapType.SOFT
        this.halfCapReached(
            teamOneScore = updatedTeamOne.score,
            teamTwoScore = updatedTeamTwo.score,
            now = now,
        ) -> CapType.HALF
        else -> null
    }

    val halftimeScore = this.halftimeTargetScore ?: halftimeScore(this.rules)
    val halftimeReached = !this.halftimeTaken &&
        max(updatedTeamOne.score, updatedTeamTwo.score) >= halftimeScore

    // A point-end cap offer is still only pending; the observer can apply or defer it.
    // Start halftime and surface that offer from the halftime state.
    if (halftimeReached) {
        return startHalftime(
            state = this,
            teamOne = updatedTeamOne,
            teamTwo = updatedTeamTwo,
            existingCapOffer = pendingCapOffer,
            now = now,
            undoPrevious = this,
            undoLabel = "Undo Goal by ${this.teamName(scoringTeam)}",
        )
    }

    // Regular point -- not half, and not game over.
    val countdown = buildBetweenPointsCountdown(
        pullingFromEnd = nextPullingFromEnd,
        sequenceStart = now,
    )

    return this.copy(
        teamOne = updatedTeamOne,
        teamTwo = updatedTeamTwo,
        pullingTeam = nextPullingTeam,
        nearAttackingTeam = nextNearAttackingTeam,
        pullingFromEnd = nextPullingFromEnd,
        phase = LivePhase.BETWEEN_POINTS,
        countdown = countdown,
        pullSequenceOffsidesRecorded = false,
        pullSequenceFalseStartRecorded = false,
        pullSkippedForCurrentPoint = false,
        halftimeTaken = this.halftimeTaken,
        halftimeTargetScore = this.halftimeTargetScore,
        winningScore = this.winningScore,
        halfCapApplied = this.halfCapApplied,
        softCapApplied = this.softCapApplied,
        hardCapApplied = this.hardCapApplied,
        pendingCapOffer = pendingCapOffer,
        lastEvent = "${this.teamName(scoringTeam)} scored.",
    ).withUndo(this, "Undo Goal by ${this.teamName(scoringTeam)}")
}
// Manually start half time
fun LiveGameState.startHalftimeNow(
    now: Long,
): LiveGameState {
    if (this.halftimeTaken || this.phase != LivePhase.BETWEEN_POINTS) {
        return this
    }
    return startHalftime(
        state = this,
        teamOne = this.teamOne,
        teamTwo = this.teamTwo,
        existingCapOffer = this.pendingCapOffer,
        now = now,
        undoPrevious = this,
        undoLabel = "Undo Start Halftime",
    )
}
private fun startHalftime(
    state: LiveGameState,
    teamOne: TeamLiveState,
    teamTwo: TeamLiveState,
    existingCapOffer: CapType?,
    now: Long,
    undoPrevious: LiveGameState,
    undoLabel: String,
): LiveGameState {
    val secondHalfPullingTeam = state.openingPullingTeam.flip()
    val secondHalfPullingFromEnd = state.openingPullingFromEnd
    val secondHalfNearAttackingTeam = if (secondHalfPullingFromEnd == FieldEnd.FAR) {
        secondHalfPullingTeam
    } else {
        secondHalfPullingTeam.flip()
    }
    val halftimeCountdown = buildHalftimeCountdown(
        halftimeMinutes = state.rules.halftimeMinutes,
        sequenceStart = now,
    )
    val halftimeEnd = now + state.rules.halftimeMinutes * 60_000L
    val hardCapTime = state.startEpoch + state.rules.hardCapMinutes * 60_000L
    val softCapTime = state.startEpoch + state.rules.softCapMinutes * 60_000L
    // Preserve an already-pending soft/hard cap. Otherwise, catch caps that became
    // due just before a manual halftime start or that are scheduled during halftime.
    val pendingCapOffer = existingCapOffer.takeIf { it == CapType.SOFT || it == CapType.HARD }
        ?: when {
            state.hardCapRelevant() && hardCapTime < halftimeEnd -> CapType.HARD
            state.softCapRelevant() && softCapTime < halftimeEnd -> CapType.SOFT
            else -> null
        }

    return state.copy(
        teamOne = teamOne.copy(
            firstHalfTimeoutsUsed = state.teamOne.timeoutsUsedThisHalf,
            timeoutsUsedThisHalf = 0,
        ),
        teamTwo = teamTwo.copy(
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
        pullSkippedForCurrentPoint = false,
        halftimeTaken = true,
        pendingCapOffer = pendingCapOffer,
        lastEvent = "Halftime.",
    ).withUndo(undoPrevious, undoLabel)
}
// Manually end the game now.
fun LiveGameState.endGameNow(
    now: Long,
): LiveGameState {
    if (this.phase == LivePhase.GAME_OVER) {
        return this
    }
    return this.copy(
        endEpoch = now,
        phase = LivePhase.GAME_OVER,
        countdown = null,
        pendingCapOffer = null,
        lastEvent = "Game over.",
    ).withUndo(this, "Undo End Game")
}
// Start a point.  I.e. indicate that the pull happened.
fun LiveGameState.beginLivePoint(): LiveGameState {
    return this.copy(
        phase = LivePhase.LIVE_POINT,
        countdown = null,
        pullSequenceOffsidesRecorded = false,
        pullSequenceFalseStartRecorded = false,
        pullSkippedForCurrentPoint = false,
        lastEvent = "Point is live.",
    ).withUndo(this, "Undo Start Point")
}
// If we record a goal before starting the point, start it, and then record the goal.
fun LiveGameState.recordGoalFromCurrentState(
    scoringTeam: TeamId,
    now: Long,
): LiveGameState {
    val livePointState = if (this.phase == LivePhase.BETWEEN_POINTS) {
        this.beginLivePoint()
    } else {
        this
    }
    return livePointState.recordGoal(scoringTeam, now)
}
// Resume a live point after a timeout or similar interruption.
fun LiveGameState.continueLivePoint(): LiveGameState {
    return this.copy(
        phase = LivePhase.LIVE_POINT,
        countdown = null,
        lastEvent = "Point continued.",
    )
}
// Advance automatic clock-driven transitions that do not require an observer button press.
fun LiveGameState.advanceGameClock(now: Long): LiveGameState {
    val countdown = this.countdown ?: return this
    if (now < countdown.targetEpoch) {
        return this
    }
    return when {
        this.phase == LivePhase.BETWEEN_POINTS && countdown.kind.usesBetweenPointsTarget() -> {
            this.automaticLivePointState()
        }
        this.phase == LivePhase.LIVE_POINT && countdown.kind == CountdownKind.TIME_OUT -> {
            this.automaticContinueLivePointState()
        }
        this.phase == LivePhase.HALFTIME && countdown.kind == CountdownKind.HALFTIME -> {
            val betweenPointsState = this.copy(
                phase = LivePhase.BETWEEN_POINTS,
                countdown = buildBetweenPointsCountdown(
                    pullingFromEnd = this.pullingFromEnd,
                    sequenceStart = countdown.targetEpoch,
                ),
                pullSkippedForCurrentPoint = false,
            )
            betweenPointsState.advanceGameClock(now)
        }
        else -> error("Countdown ${countdown.kind} is not valid while game phase is ${this.phase}.")
    }
}
// Adjust countdown timer (use negative number to subtract time)
fun LiveGameState.addTimeToCountdown(seconds: Int): LiveGameState {
    val countdown = this.countdown ?: return this
    val sign = if (seconds < 0) "-" else ""
    val absoluteSeconds = abs(seconds)
    return this.copy(
        countdown = countdown.copy(targetEpoch = countdown.targetEpoch + seconds * 1000L),
        lastEvent = "Adjusted timer by $sign${absoluteSeconds / 60}:${(absoluteSeconds % 60).toString().padStart(2, '0')}.",
    )
}
// Manually adjust the score
fun LiveGameState.adjustScore(teamOneScore: Int, teamTwoScore: Int): LiveGameState {
    return this.copy(
        teamOne = this.teamOne.copy(score = teamOneScore.coerceAtLeast(0)),
        teamTwo = this.teamTwo.copy(score = teamTwoScore.coerceAtLeast(0)),
        lastEvent = "Score adjusted.",
    ).withUndo(this, "Undo Score Adjustment")
}
// Undo the last action.
fun LiveGameState.undoLastAction(): LiveGameState {
    val entry = this.undoEntry ?: return this
    return entry.previous.copy(
        redoEntry = this,
    )
}
// Redo the last undone action.
fun LiveGameState.redoLastAction(): LiveGameState {
    return this.redoEntry ?: this
}
// Adjust the game setup after the game has already started.
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
// Go to setup screen from live game
// This just extracts the information from the live state that the setup screen needs.
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
// Move to live-point state while preserving the previous user-action undo entry.
private fun LiveGameState.automaticLivePointState(): LiveGameState {
    return copy(
        phase = LivePhase.LIVE_POINT,
        countdown = null,
        pullSequenceOffsidesRecorded = false,
        pullSequenceFalseStartRecorded = false,
        pullSkippedForCurrentPoint = false,
        lastEvent = "Point is live.",
    )
}
// Clear an in-point timeout countdown while preserving the timeout undo entry.
private fun LiveGameState.automaticContinueLivePointState(): LiveGameState {
    return copy(
        phase = LivePhase.LIVE_POINT,
        countdown = null,
        lastEvent = "Point continued.",
    )
}
// Attach the previous game state in the undoEntry.
// Use this everywhere we want an action to be undo-able (essentially all user actions).
// Most returned states for a user-initiated action should have a .withUndo(state, label)
// at the end.
internal fun LiveGameState.withUndo(previous: LiveGameState, label: String): LiveGameState {
    return copy(
        undoEntry = UndoEntry(label = label, previous = previous.copy(redoEntry = null)),
        redoEntry = null,
    )
}
// Helper to flip TeamId between the two teams.
internal fun TeamId.flip(): TeamId {
    return if (this == TeamId.TEAM_ONE) TeamId.TEAM_TWO else TeamId.TEAM_ONE
}
// Helper to flip FieldEnd between the two directions.
internal fun FieldEnd.flip(): FieldEnd {
    return if (this == FieldEnd.NEAR) FieldEnd.FAR else FieldEnd.NEAR
}
internal fun epochTimestamp(date: LocalDate, time: LocalTime, timeZone: ZoneId): Long {
    return LocalDateTime.of(date, time)
        .atZone(timeZone)
        .toInstant()
        .toEpochMilli()
}
internal fun localDateTimeFromEpoch(epoch: Long, timeZone: ZoneId): LocalDateTime {
    return LocalDateTime.ofInstant(
        java.time.Instant.ofEpochMilli(epoch),
        timeZone,
    )
}
internal fun localTimeFromEpoch(epoch: Long, timeZone: ZoneId): LocalTime {
    return localDateTimeFromEpoch(epoch, timeZone).toLocalTime()
}
