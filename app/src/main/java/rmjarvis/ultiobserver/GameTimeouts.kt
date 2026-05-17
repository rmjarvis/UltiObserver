package rmjarvis.ultiobserver

// Manually adjust the number of timeouts
fun LiveGameState.adjustTimeouts(
    teamOneTimeoutsUsed: Int,
    teamTwoTimeoutsUsed: Int,
): LiveGameState {
    return this.copy(
        teamOne = this.teamOne.copy(
            timeoutsUsedThisHalf = teamOneTimeoutsUsed,
        ),
        teamTwo = this.teamTwo.copy(
            timeoutsUsedThisHalf = teamTwoTimeoutsUsed,
        ),
        lastEvent = "Timeouts adjusted.",
    ).withUndo(this, "Undo Timeout Adjustment")
}
// Someone called a timeout.
// This records another used timeout and starts or extends the appropriate countdown.
fun LiveGameState.assessTimeout(
    team: TeamId,
    now: Long,
): TimeoutAssessmentResult {
    val timeoutState = this.timeoutEligibleState(now)
        ?: return TimeoutAssessmentResult(this, GameEvent.TimeoutUnavailable(this))
    if (timeoutState.timeoutsRemaining(team) <= 0) {
        return TimeoutAssessmentResult(this, GameEvent.TeamOutOfTimeouts(state = this, team = team))
    }
    val chargedState = timeoutState.chargeTimeout(team, now)
    return TimeoutAssessmentResult(
        state = chargedState,
        event = GameEvent.TimeoutCharged(state = chargedState, team = team),
    )
}
fun LiveGameState.chargeTimeout(
    team: TeamId,
    now: Long,
): LiveGameState {
    val timeoutState = this.timeoutEligibleState(now) ?: return this
    if (timeoutState.timeoutsRemaining(team) <= 0) {
        return this
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
        lastEvent = "Timeout charged to ${timeoutState.teamName(team)}."
    )

    if (timeoutState.phase == LivePhase.BETWEEN_POINTS) {
        return applyBetweenPointsTimeout(updatedState)
            .withUndo(this, "Undo Timeout by ${timeoutState.teamName(team)}")
    }
    return applyLivePointTimeout(updatedState, now)
        .withUndo(this, "Undo Timeout by ${timeoutState.teamName(team)}")
}
// Calculate how many time outs are allowed in the current half according to the rules.
fun LiveGameState.timeoutsAllowedThisHalf(team: TeamId): Int {
    val firstHalfAllowance = this.rules.timeoutsPerHalf + if (this.rules.hasFloaterTimeout) 1 else 0
    if (!this.halftimeTaken) {
        return firstHalfAllowance
    }

    val firstHalfTimeoutsUsed = this.teamFor(team).firstHalfTimeoutsUsed
    val floaterCarries = this.rules.hasFloaterTimeout && firstHalfTimeoutsUsed < firstHalfAllowance
    return this.rules.timeoutsPerHalf + if (floaterCarries) 1 else 0
}
// Calculate how many time outs are still available in the current half.
fun LiveGameState.timeoutsRemaining(team: TeamId): Int {
    val usedThisHalf = this.teamFor(team).timeoutsUsedThisHalf
    return (this.timeoutsAllowedThisHalf(team) - usedThisHalf).coerceAtLeast(0)
}
// Return the state in which a timeout may be charged, if the rules allow one now.
private fun LiveGameState.timeoutEligibleState(now: Long): LiveGameState? {
    val advancedState = advanceGameClock(now)
    return when (advancedState.phase) {
        LivePhase.BETWEEN_POINTS -> if (advancedState.countdown != null) advancedState else null
        LivePhase.LIVE_POINT -> advancedState
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
            targetEpoch = countdown.targetEpoch + 70_000L,
        )
    )
}
// Apply a timeout by the thrower during a live point.
private fun applyLivePointTimeout(
    state: LiveGameState,
    now: Long,
): LiveGameState {
    return state.copy(
        countdown = CountdownState(
            kind = CountdownKind.TIME_OUT,
            label = "Offense set in",
            durationSeconds = 70,
            targetEpoch = now + 70_000L,
        ),
    )
}
