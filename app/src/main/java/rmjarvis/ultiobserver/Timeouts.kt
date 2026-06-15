package rmjarvis.ultiobserver

/**
 * State and popup event from trying to charge a timeout.
 *
 * @param state The live state after the timeout attempt.
 * @param event The observer-facing event to show.
 */
data class TimeoutAssessmentResult(
    val state: GameState,
    val event: GameEvent,
)

/**
 * Confirmation details for a timeout request before the observer applies it.
 *
 * @param event The event-shaped preview used to render the confirmation dialog.
 */
data class TimeoutAssessmentPreview(
    val event: GameEvent,
)

/**
 * Replace the number of timeouts used by each team as a manual correction.
 *
 * @param teamOneTimeoutsUsed The corrected count of team-one timeouts used in the current half.
 * @param teamTwoTimeoutsUsed The corrected count of team-two timeouts used in the current half.
 */
fun GameState.adjustTimeouts(
    teamOneTimeoutsUsed: Int,
    teamTwoTimeoutsUsed: Int,
    now: Long,
): GameState {
    val adjustedTeamOneTimeouts = teamOneTimeoutsUsed.coerceAtLeast(0)
    val adjustedTeamTwoTimeouts = teamTwoTimeoutsUsed.coerceAtLeast(0)
    val entries = buildList {
        val teamOneDelta = adjustedTeamOneTimeouts - this@adjustTimeouts.teamOne.timeoutsUsedThisHalf
        if (teamOneDelta != 0) {
            add(
                EventLogEntry(
                    timestampEpoch = now,
                    type = EventLogType.TIMEOUT,
                    team = TeamId.TEAM_ONE,
                    delta = teamOneDelta,
                )
            )
        }
        val teamTwoDelta = adjustedTeamTwoTimeouts - this@adjustTimeouts.teamTwo.timeoutsUsedThisHalf
        if (teamTwoDelta != 0) {
            add(
                EventLogEntry(
                    timestampEpoch = now,
                    type = EventLogType.TIMEOUT,
                    team = TeamId.TEAM_TWO,
                    delta = teamTwoDelta,
                )
            )
        }
    }
    return this.copy(
        teamOne = this.teamOne.copy(
            timeoutsUsedThisHalf = adjustedTeamOneTimeouts,
        ),
        teamTwo = this.teamTwo.copy(
            timeoutsUsedThisHalf = adjustedTeamTwoTimeouts,
        ),
        lastEvent = "Timeouts adjusted.",
    ).withEventLogEntries(entries).withUndo(this, "Undo Timeout adjustment")
}
/**
 * Validate and charge a timeout request, returning the popup event that should be shown.
 * This records another used timeout and reports whether the timeout was accepted or rejected.
 *
 * @param team The team requesting the timeout.
 * @param now The current epoch millis used to advance expired countdowns and start timeout timing.
 */
fun GameState.assessTimeout(
    team: TeamId,
    now: Long,
): TimeoutAssessmentResult {
    val timeoutState = this.timeoutEligibleState(now)
        ?: return TimeoutAssessmentResult(this, GameEvent.TimeoutUnavailable(this))
    if (timeoutState.timeoutsRemaining(team) <= 0) {
        return TimeoutAssessmentResult(this, GameEvent.TeamOutOfTimeouts(state = timeoutState, team = team))
    }
    val chargedState = timeoutState.chargeTimeout(team, now)
    return TimeoutAssessmentResult(
        state = chargedState,
        event = GameEvent.TimeoutCharged(state = chargedState, team = team),
    )
}

/**
 * Build confirmation details for a timeout request without changing game state.
 *
 * @param team The team requesting the timeout.
 * @param now The current epoch millis used to preview timeout eligibility.
 */
fun GameState.previewTimeout(
    team: TeamId,
    now: Long,
): TimeoutAssessmentPreview {
    val timeoutState = this.timeoutEligibleState(now)
        ?: return TimeoutAssessmentPreview(GameEvent.TimeoutUnavailable(this))
    if (timeoutState.timeoutsRemaining(team) <= 0) {
        return TimeoutAssessmentPreview(GameEvent.TeamOutOfTimeouts(state = timeoutState, team = team))
    }
    val chargedState = timeoutState.chargeTimeout(team, now)
    return TimeoutAssessmentPreview(GameEvent.TimeoutCharged(state = chargedState, team = team))
}

/// Report whether the current game state can process a timeout request.
fun GameState.canRequestTimeout(now: Long): Boolean {
    return this.timeoutEligibleState(now) != null
}

/**
 * Charge a timeout directly and update the relevant countdown.
 * Between points this extends the current timer; during a live point it starts an offense-set timer.
 *
 * @param team The team whose used-timeout count should increase.
 * @param now The epoch millis used as the start of a live-point timeout countdown.
 */
fun GameState.chargeTimeout(
    team: TeamId,
    now: Long,
): GameState {
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

    if (timeoutState.phase == GamePhase.BETWEEN_POINTS) {
        return applyBetweenPointsTimeout(updatedState)
            .withEventLogEntry(
                EventLogEntry(
                    timestampEpoch = now,
                    type = EventLogType.TIMEOUT,
                    team = team,
                )
            )
            .withUndo(this, "Undo Timeout by ${timeoutState.teamName(team)}")
    }
    return applyLivePointTimeout(updatedState, now)
        .withEventLogEntry(
            EventLogEntry(
                timestampEpoch = now,
                type = EventLogType.TIMEOUT,
                team = team,
            )
        )
        .withUndo(this, "Undo Timeout by ${timeoutState.teamName(team)}")
}
/**
 * Calculate how many timeouts a team is allowed in the current half under the active rules.
 *
 * @param team The team whose first-half floater carryover must be considered.
 */
fun GameState.timeoutsAllowedThisHalf(team: TeamId): Int {
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
fun GameState.timeoutsRemaining(team: TeamId): Int {
    val usedThisHalf = this.teamFor(team).timeoutsUsedThisHalf
    return (this.timeoutsAllowedThisHalf(team) - usedThisHalf).coerceAtLeast(0)
}
/**
 * Return the countdown-expiry-adjusted state in which a timeout may be charged, if one is legal now.
 * This treats an expired between-points countdown as live play before deciding how the timeout works.
 *
 * @param now The current epoch millis, used to treat an expired between-points countdown as live play.
 */
private fun GameState.timeoutEligibleState(now: Long): GameState? {
    val transitionedState = applyExpiredCountdownTransitions(now)
    return when (transitionedState.phase) {
        GamePhase.BETWEEN_POINTS -> if (transitionedState.countdown != null) transitionedState else null
        GamePhase.LIVE_POINT -> transitionedState
        GamePhase.HALFTIME -> null
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
    state: GameState,
): GameState {
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
    state: GameState,
    now: Long,
): GameState {
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
internal fun GameEvent.TimeoutCharged.formatPopupTitle(): String = "Timeout"

/// Format an invalid-timeout event popup title.
internal fun GameEvent.TimeoutUnavailable.formatPopupTitle(): String = "Timeout not possible now"

/// Format an out-of-timeouts event popup title.
internal fun GameEvent.TeamOutOfTimeouts.formatPopupTitle(): String = "Invalid timeout"

/// Format a timeout-charged event message with the remaining timeout count.
internal fun GameEvent.TimeoutCharged.formatMessage(): String {
    val timeoutCount = state.timeoutsRemaining(team)
    return "Timeout charged to ${state.teamName(team)}. " +
        "They have ${countedNounPhrase(timeoutCount, "timeout")} remaining in this half."
}

/// Format a timeout-unavailable event message.
internal fun GameEvent.TimeoutUnavailable.formatMessage(): String {
    return "Timeouts are not available now."
}

/// Format an out-of-timeouts event message.
internal fun GameEvent.TeamOutOfTimeouts.formatMessage(): String {
    val message = "${this.state.teamName(this.team)} is out of timeouts."
    if (state.phase != GamePhase.LIVE_POINT) {
        return message
    }
    return "$message\n\nAdd three to the stall count. It is a turnover if that is 10 or more."
}
