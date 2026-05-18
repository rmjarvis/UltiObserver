package rmjarvis.ultiobserver

/**
 * Represent the state and optional popup event from trying to charge a timeout.
 *
 * @param state The live state after the timeout attempt.
 * @param event The observer-facing event to show, or null when no popup is needed.
 */
data class TimeoutAssessmentResult(
    val state: LiveGameState,
    val event: GameEvent? = null,
)

/**
 * Replace the number of timeouts used by each team as a manual correction.
 *
 * @param teamOneTimeoutsUsed The corrected count of team-one timeouts used in the current half.
 * @param teamTwoTimeoutsUsed The corrected count of team-two timeouts used in the current half.
 */
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
/**
 * Validate and charge a timeout request, returning the popup event that should be shown.
 * This records another used timeout and reports whether the timeout was accepted or rejected.
 *
 * @param team The team requesting the timeout.
 * @param now The current epoch millis used to advance expired countdowns and start timeout timing.
 */
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
/**
 * Charge a timeout directly and update the relevant countdown.
 * Between points this extends the current timer; during a live point it starts an offense-set timer.
 *
 * @param team The team whose used-timeout count should increase.
 * @param now The epoch millis used as the start of a live-point timeout countdown.
 */
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
/**
 * Calculate how many timeouts a team is allowed in the current half under the active rules.
 *
 * @param team The team whose first-half floater carryover must be considered.
 */
fun LiveGameState.timeoutsAllowedThisHalf(team: TeamId): Int {
    val firstHalfAllowance = this.rules.timeoutsPerHalf + if (this.rules.hasFloaterTimeout) 1 else 0
    if (!this.halftimeTaken) {
        return firstHalfAllowance
    }

    val firstHalfTimeoutsUsed = this.teamFor(team).firstHalfTimeoutsUsed
    val floaterCarries = this.rules.hasFloaterTimeout && firstHalfTimeoutsUsed < firstHalfAllowance
    return this.rules.timeoutsPerHalf + if (floaterCarries) 1 else 0
}
/**
 * Calculate how many timeouts a team still has available in the current half.
 *
 * @param team The team whose remaining timeout count should be reported.
 */
fun LiveGameState.timeoutsRemaining(team: TeamId): Int {
    val usedThisHalf = this.teamFor(team).timeoutsUsedThisHalf
    return (this.timeoutsAllowedThisHalf(team) - usedThisHalf).coerceAtLeast(0)
}
/**
 * Return the countdown-expiry-adjusted state in which a timeout may be charged, if one is legal now.
 * This treats an expired between-points countdown as live play before deciding how the timeout works.
 *
 * @param now The current epoch millis, used to treat an expired between-points countdown as live play.
 */
private fun LiveGameState.timeoutEligibleState(now: Long): LiveGameState? {
    val transitionedState = applyExpiredCountdownTransitions(now)
    return when (transitionedState.phase) {
        LivePhase.BETWEEN_POINTS -> if (transitionedState.countdown != null) transitionedState else null
        LivePhase.LIVE_POINT -> transitionedState
        LivePhase.HALFTIME -> null
        else -> null
    }
}
/**
 * Extend a between-points countdown after a timeout is charged before the pull.
 * This is the rules branch that adds 70 seconds to the existing timer.
 *
 * @param state The already-charged timeout state whose countdown should be extended.
 */
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
/**
 * Start the offense-set countdown for a timeout during a live point.
 *
 * @param state The already-charged timeout state to decorate with an in-point countdown.
 * @param now The epoch millis used as the countdown start.
 */
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

/// Format a timeout event popup title.
internal fun GameEvent.TimeoutCharged.formatPopupTitle(): String = "Timeout Charged"

/// Format an invalid-timeout event popup title.
internal fun GameEvent.TimeoutUnavailable.formatPopupTitle(): String = "Invalid Timeout"

/// Format an out-of-timeouts event popup title.
internal fun GameEvent.TeamOutOfTimeouts.formatPopupTitle(): String = "Invalid Timeout"

/// Format a timeout-charged event message with the remaining timeout count.
internal fun GameEvent.TimeoutCharged.formatMessage(): String {
    val timeoutCount = state.timeoutsRemaining(team)
    return "Timeout charged to ${state.teamName(team)}. " +
        "They have $timeoutCount ${pluralize(timeoutCount, "timeout")} remaining in this half."
}

/// Format a timeout-unavailable event message.
internal fun GameEvent.TimeoutUnavailable.formatMessage(): String {
    return "Timeouts are not available now."
}

/// Format an out-of-timeouts event message.
internal fun GameEvent.TeamOutOfTimeouts.formatMessage(): String {
    return "${this.state.teamName(this.team)} is out of timeouts."
}
