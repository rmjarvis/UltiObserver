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
 * @param teamOneFirstHalfTimeoutsUsed The corrected stored first-half count for team one.
 * @param teamTwoFirstHalfTimeoutsUsed The corrected stored first-half count for team two.
 *
 * The stored first-half counts only affect timeout availability after halftime has been taken.
 */
fun GameState.adjustTimeouts(
    teamOneTimeoutsUsed: Int,
    teamTwoTimeoutsUsed: Int,
    teamOneFirstHalfTimeoutsUsed: Int,
    teamTwoFirstHalfTimeoutsUsed: Int,
    now: Long,
): GameState {
    val adjustedTeamOneTimeouts = teamOneTimeoutsUsed.coerceAtLeast(0)
    val adjustedTeamTwoTimeouts = teamTwoTimeoutsUsed.coerceAtLeast(0)
    val adjustedTeamOneFirstHalfTimeouts = teamOneFirstHalfTimeoutsUsed.coerceAtLeast(0)
    val adjustedTeamTwoFirstHalfTimeouts = teamTwoFirstHalfTimeoutsUsed.coerceAtLeast(0)
    val entries = buildList {
        val teamOneDelta = adjustedTeamOneTimeouts -
            this@adjustTimeouts.teamOne.timeoutsUsedThisHalf
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
        val teamTwoDelta = adjustedTeamTwoTimeouts -
            this@adjustTimeouts.teamTwo.timeoutsUsedThisHalf
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
            firstHalfTimeoutsUsed = adjustedTeamOneFirstHalfTimeouts,
        ),
        teamTwo = this.teamTwo.copy(
            timeoutsUsedThisHalf = adjustedTeamTwoTimeouts,
            firstHalfTimeoutsUsed = adjustedTeamTwoFirstHalfTimeouts,
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
    val timeoutState = this.timeoutEligibleState()
        ?: return TimeoutAssessmentResult(this, GameEvent.TimeoutUnavailable(this))
    if (timeoutState.timeoutsRemaining(team) <= 0) {
        return TimeoutAssessmentResult(
            this,
            GameEvent.TeamOutOfTimeouts(state = timeoutState, team = team),
        )
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
    val timeoutState = this.timeoutEligibleState()
        ?: return TimeoutAssessmentPreview(GameEvent.TimeoutUnavailable(this))
    if (timeoutState.timeoutsRemaining(team) <= 0) {
        return TimeoutAssessmentPreview(
            GameEvent.TeamOutOfTimeouts(state = timeoutState, team = team)
        )
    }
    val chargedState = timeoutState.chargeTimeout(team, now)
    return TimeoutAssessmentPreview(GameEvent.TimeoutCharged(state = chargedState, team = team))
}

/// Report whether the current game state can process a timeout request.
fun GameState.canRequestTimeout(now: Long): Boolean {
    return this.timeoutEligibleState() != null
}

/**
 * Charge a timeout directly and update the relevant countdown.
 * Between points this extends the current timer. During a live point this starts an
 * offense-set timer or extends any active countdown.
 * If there is a pending misconduct countdown that hasn't started yet, start it first
 * before extending it.
 *
 * @param team The team whose used-timeout count should increase.
 * @param now The epoch millis used as the start of a live-point timeout countdown.
 */
fun GameState.chargeTimeout(
    team: TeamId,
    now: Long,
): GameState {
    val timeoutState = this.timeoutEligibleState() ?: return this
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

    if (timeoutState.phase.isBeforeLivePoint) {
        return applyBetweenPointsTimeout(updatedState, now)
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
    return this.rules.timeoutsAllowedThisHalf(
        halftimeTaken = halftimeTaken,
        firstHalfTimeoutsUsed = teamFor(team).firstHalfTimeoutsUsed,
    )
}

/**
 * Calculate a team's timeout allowance for the current half from the rule inputs.
 *
 * @param halftimeTaken Whether the game has moved past the first half.
 * @param firstHalfTimeoutsUsed The team's corrected first-half timeout count.
 */
fun GameRules.timeoutsAllowedThisHalf(halftimeTaken: Boolean, firstHalfTimeoutsUsed: Int): Int {
    val firstHalfAllowance = timeoutsPerHalf + if (hasFloaterTimeout) 1 else 0
    if (!halftimeTaken) {
        return firstHalfAllowance
    }

    val floaterCarries = hasFloaterTimeout && firstHalfTimeoutsUsed < firstHalfAllowance
    return timeoutsPerHalf + if (floaterCarries) 1 else 0
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
 * Return the state in which a timeout may be charged, if one is legal now.
 * Pre-pull states are returned directly, even when their countdown has expired or cleared.
 */
private fun GameState.timeoutEligibleState(): GameState? {
    return when {
        phase.isBeforeLivePoint -> this
        phase == GamePhase.LIVE_POINT -> this
        else -> null
    }
}
/**
 * Extend a between-points countdown after a timeout is charged before the pull.
 * If the countdown has cleared, rebuild it at zero so the same timeout extension leaves
 * the configured timeout duration on the visible timer.
 *
 * @param state The already-charged timeout state whose countdown should be extended.
 * @param now The epoch millis used when rebuilding a cleared countdown.
 */
private fun applyBetweenPointsTimeout(
    state: GameState,
    now: Long,
): GameState {
    val countdown = state.countdown ?: run {
        buildBetweenPointsCountdown(
            pullingFromEnd = state.pullingFromEnd,
            sequenceStart = now,
            kind = if (state.phase == GamePhase.PRE_GAME) {
                CountdownKind.OPENING_PULL
            } else {
                CountdownKind.BETWEEN_POINTS
            },
            promptTarget = state.pullPromptTarget,
            rules = state.rules,
        ).copy(targetEpoch = now)
    }
    val timeoutSeconds = state.rules.timeoutSeconds
    return state.copy(
        countdown = countdown.extendBy(timeoutSeconds),
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
    val misconductState = if (state.pendingMisconductCountdown) {
        state.startMisconductCountdown(now).copy(lastEvent = state.lastEvent)
    } else {
        state
    }
    val timeoutSeconds = state.rules.timeoutSeconds
    val activeCountdown = misconductState.countdown
    if (activeCountdown != null) {
        return misconductState.copy(
            pendingMisconductCountdown = false,
            countdown = activeCountdown.extendBy(timeoutSeconds),
        )
    }
    return state.copy(
        pendingMisconductCountdown = false,
        countdown = CountdownState(
            kind = CountdownKind.TIME_OUT,
            label = "Offense set in",
            durationSeconds = timeoutSeconds,
            targetEpoch = now + timeoutSeconds * 1_000L,
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
