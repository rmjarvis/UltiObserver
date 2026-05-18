package rmjarvis.ultiobserver

/**
 * Replace each team's cumulative pull-infraction counts as a manual correction.
 *
 * @param teamOneOffsides The corrected offsides count for team one.
 * @param teamOneFalseStarts The corrected false-start count for team one.
 * @param teamTwoOffsides The corrected offsides count for team two.
 * @param teamTwoFalseStarts The corrected false-start count for team two.
 */
fun LiveGameState.adjustPullInfractions(
    teamOneOffsides: Int,
    teamOneFalseStarts: Int,
    teamTwoOffsides: Int,
    teamTwoFalseStarts: Int,
): LiveGameState {
    return this.copy(
        teamOne = this.teamOne.copy(
            offsides = teamOneOffsides.coerceAtLeast(0),
            falseStarts = teamOneFalseStarts.coerceAtLeast(0),
        ),
        teamTwo = this.teamTwo.copy(
            offsides = teamTwoOffsides.coerceAtLeast(0),
            falseStarts = teamTwoFalseStarts.coerceAtLeast(0),
        ),
        lastEvent = "Pull infractions adjusted.",
    ).withUndo(this, "Undo Pull Infraction Adjustment")
}
/// Swap the teams' field ends while keeping the same team pulling.
fun LiveGameState.swapFieldEnds(): LiveGameState {
    val newPullingFromEnd = this.pullingFromEnd.flip()
    return this.copy(
        nearAttackingTeam = this.nearAttackingTeam.flip(),
        pullingFromEnd = newPullingFromEnd,
        countdown = this.countdown?.swapOD(),
        pullSequenceOffsidesRecorded = false,
        pullSequenceFalseStartRecorded = false,
        lastEvent = "Field ends swapped.",
    ).withUndo(this, "Undo Swap Ends of Field")
}
/// Swap the pulling team while leaving the teams' attacking orientation otherwise intact.
fun LiveGameState.swapPullingTeam(): LiveGameState {
    val newPullingTeam = this.pullingTeam.flip()
    val newPullingFromEnd = this.pullingFromEnd.flip()
    return this.copy(
        pullingTeam = newPullingTeam,
        pullingFromEnd = newPullingFromEnd,
        countdown = this.countdown?.swapOD(),
        pullSequenceOffsidesRecorded = false,
        pullSequenceFalseStartRecorded = false,
        lastEvent = "Pulling team swapped.",
    ).withUndo(this, "Undo Swap Pulling Team")
}
/**
 * Record offsides or false start for the selected team when allowed on the current pull.
 *
 * @param team The team that committed the pull infraction.
 */
fun LiveGameState.assessPullInfraction(team: TeamId): PullInfractionAssessmentResult {
    if (!this.canRecordPullInfraction(team)) {
        return PullInfractionAssessmentResult(this)
    }
    val infraction = if (team == this.pullingTeam) {
        PullInfractionType.OFFSIDES
    } else {
        PullInfractionType.FALSE_START
    }
    val updatedState = when (infraction) {
        PullInfractionType.OFFSIDES -> this.recordOffsides()
        PullInfractionType.FALSE_START -> this.recordFalseStart()
    }
    return PullInfractionAssessmentResult(
        state = updatedState,
        event = GameEvent.PullInfractionRecorded(
            state = updatedState,
            team = team,
            infraction = infraction,
            totalPullViolations = updatedState.pullViolationTotal(team),
        ),
    )
}

/**
 * Report whether the selected team may record a pull infraction on this pull sequence.
 *
 * @param team The team whose infraction button or action is being considered.
 */
fun LiveGameState.canRecordPullInfraction(team: TeamId): Boolean {
    if (this.pullSkippedForCurrentPoint) {
        return false
    }
    return if (team == this.pullingTeam) {
        !this.pullSequenceOffsidesRecorded
    } else {
        !this.pullSequenceFalseStartRecorded
    }
}

/// Report whether the expired-pull action surface should be available.
fun LiveGameState.hasExpiredPullActions(): Boolean {
    return this.phase == LivePhase.BETWEEN_POINTS && this.pullCountdownExpired
}

/// Build the state restored by undoing automatic start point so time violation can still be assessed.
internal fun LiveGameState.expiredPullDecisionState(): LiveGameState {
    return this.copy(
        countdown = null,
        pullCountdownExpired = true,
    )
}

/**
 * Record a pull time violation from the expired-pull action surface.
 * First violations are warnings, later violations charge a timeout when available, and no-timeout
 * violations skip the pull and show field-position guidance.
 *
 * @param team The team that violated the pull-readiness or pull-timing requirement.
 * @param now The epoch millis used to start any resulting countdown.
 */
fun LiveGameState.assessTimeViolation(team: TeamId, now: Long): TimeViolationAssessmentResult {
    if (!this.hasExpiredPullActions()) {
        return TimeViolationAssessmentResult(this)
    }
    val outcome = when {
        !this.timeViolationWarningIssued(team) -> TimeViolationOutcome.WARNING
        this.timeoutsRemaining(team) > 0 -> TimeViolationOutcome.TIMEOUT
        else -> TimeViolationOutcome.NO_TIMEOUT
    }
    val updatedState = when (outcome) {
        TimeViolationOutcome.WARNING -> this.recordTimeViolationWarning(team, now)
        TimeViolationOutcome.TIMEOUT -> this.recordTimeViolationTimeout(team, now)
        TimeViolationOutcome.NO_TIMEOUT -> this.recordTimeViolationWithoutTimeout(team)
    }
    return TimeViolationAssessmentResult(
        state = updatedState,
        event = GameEvent.TimeViolationRecorded(
            state = updatedState,
            team = team,
            outcome = outcome,
        ),
    )
}

/**
 * Record a team's first time violation warning and start near-side timing when applicable.
 * A far-side warning is recorded without starting a countdown for the near-side observer.
 *
 * @param team The team receiving its warning.
 * @param now The epoch millis used to start the warning countdown.
 */
private fun LiveGameState.recordTimeViolationWarning(team: TeamId, now: Long): LiveGameState {
    return this.copy(
        teamOne = if (team == TeamId.TEAM_ONE) {
            this.teamOne.copy(timeViolationWarningIssued = true)
        } else {
            this.teamOne
        },
        teamTwo = if (team == TeamId.TEAM_TWO) {
            this.teamTwo.copy(timeViolationWarningIssued = true)
        } else {
            this.teamTwo
        },
        countdown = if (this.isNearSideTeam(team)) {
            this.buildTimeViolationCountdownForCurrentSide(
                now = now,
                durationSeconds = 30,
                kind = CountdownKind.PULL_RESET,
            )
        } else {
            null
        },
        pullCountdownExpired = false,
        lastEvent = "Time violation warning on ${this.teamName(team)}.",
    ).withUndo(this, "Undo Time Violation Warning on ${this.teamName(team)}")
}

/**
 * Restart the normal pull countdown from an expired-pull decision state.
 *
 * @param now The epoch millis used as the restarted countdown's sequence start.
 */
fun LiveGameState.restartPullCountdown(now: Long): LiveGameState {
    if (!this.hasExpiredPullActions()) {
        return this
    }
    return this.copy(
        countdown = buildBetweenPointsCountdown(
            pullingFromEnd = this.pullingFromEnd,
            sequenceStart = now,
        ),
        pullCountdownExpired = false,
        lastEvent = "Pull countdown restarted.",
    ).withUndo(this, "Undo Restart Pull Countdown")
}

/**
 * Record a later time violation that charges a timeout and starts the appropriate reset countdown.
 * Timeout resets are 70 seconds when the near-side team is offense and 90 seconds when it is defense.
 *
 * @param team The team being charged a timeout.
 * @param now The epoch millis used to start the reset countdown.
 */
private fun LiveGameState.recordTimeViolationTimeout(team: TeamId, now: Long): LiveGameState {
    val durationSeconds = if (this.pullingFromEnd == FieldEnd.FAR) 70 else 90
    return this.copy(
        teamOne = if (team == TeamId.TEAM_ONE) this.teamOne.withAddedTimeout() else this.teamOne,
        teamTwo = if (team == TeamId.TEAM_TWO) this.teamTwo.withAddedTimeout() else this.teamTwo,
        countdown = this.buildTimeViolationCountdownForCurrentSide(
            now = now,
            durationSeconds = durationSeconds,
            kind = CountdownKind.BETWEEN_POINTS,
        ),
        pullCountdownExpired = false,
        lastEvent = "Timeout charged to ${this.teamName(team)} for time violation.",
    ).withUndo(this, "Undo Time Violation Timeout on ${this.teamName(team)}")
}

/**
 * Build the near-side countdown used after a time violation.
 *
 * @param now The epoch millis used as the countdown start.
 * @param durationSeconds The length of the reset countdown.
 * @param kind The countdown kind so warning and timeout resets can use different cue behavior.
 */
private fun LiveGameState.buildTimeViolationCountdownForCurrentSide(
    now: Long,
    durationSeconds: Int,
    kind: CountdownKind,
): CountdownState {
    val target = this.currentSideCountdownTarget()
    return CountdownState(
        kind = kind,
        label = target.label,
        durationSeconds = durationSeconds,
        targetEpoch = now + durationSeconds * 1000L,
        betweenPointsTarget = target,
    )
}

/// Return the between-points timing target for the observer's current side of the field.
private fun LiveGameState.currentSideCountdownTarget(): BetweenPointsCountdownTarget {
    return if (this.pullingFromEnd == FieldEnd.NEAR) {
        BetweenPointsCountdownTarget.PULL
    } else {
        BetweenPointsCountdownTarget.OFFENSE_READY
    }
}

/**
 * Report whether the selected team is on the observer's near side for the current pull.
 *
 * @param team The team to compare with the near-side pull responsibility.
 */
internal fun LiveGameState.isNearSideTeam(team: TeamId): Boolean {
    return team == if (this.pullingFromEnd == FieldEnd.NEAR) {
        this.pullingTeam
    } else {
        this.pullingTeam.flip()
    }
}

/**
 * Record a time violation when no timeout remains, producing a no-pull consequence.
 * This is the no-timeout branch from the expired-pull decision surface.
 *
 * @param team The violating team.
 */
private fun LiveGameState.recordTimeViolationWithoutTimeout(team: TeamId): LiveGameState {
    return this.copy(
        countdown = null,
        pullCountdownExpired = false,
        pullSkippedForCurrentPoint = true,
        lastEvent = "Time violation on ${this.teamName(team)}.",
    ).withUndo(this, "Undo Time Violation on ${this.teamName(team)}")
}

/**
 * Report whether a team has already received its pull time-violation warning.
 *
 * @param team The team whose warning flag should be read.
 */
private fun LiveGameState.timeViolationWarningIssued(team: TeamId): Boolean {
    return if (team == TeamId.TEAM_ONE) {
        this.teamOne.timeViolationWarningIssued
    } else {
        this.teamTwo.timeViolationWarningIssued
    }
}

/// Record offsides against the current pulling team.
fun LiveGameState.recordOffsides(): LiveGameState {
    if (this.pullSkippedForCurrentPoint || this.pullSequenceOffsidesRecorded) {
        return this
    }
    val team = this.pullingTeam
    return this.copy(
        teamOne = if (team == TeamId.TEAM_ONE) {
            this.teamOne.copy(offsides = this.teamOne.offsides + 1)
        } else {
            this.teamOne
        },
        teamTwo = if (team == TeamId.TEAM_TWO) {
            this.teamTwo.copy(offsides = this.teamTwo.offsides + 1)
        } else {
            this.teamTwo
        },
        phase = LivePhase.LIVE_POINT,
        countdown = null,
        pullCountdownExpired = false,
        pullSequenceOffsidesRecorded = true,
        lastEvent = "Offsides on ${this.teamName(team)}.",
    ).withUndo(this, "Undo Offsides on ${this.teamName(team)}")
}
/// Record false start against the current receiving team.
fun LiveGameState.recordFalseStart(): LiveGameState {
    if (this.pullSkippedForCurrentPoint || this.pullSequenceFalseStartRecorded) {
        return this
    }
    val team = this.pullingTeam.flip()
    return this.copy(
        teamOne = if (team == TeamId.TEAM_ONE) {
            this.teamOne.copy(falseStarts = this.teamOne.falseStarts + 1)
        } else {
            this.teamOne
        },
        teamTwo = if (team == TeamId.TEAM_TWO) {
            this.teamTwo.copy(falseStarts = this.teamTwo.falseStarts + 1)
        } else {
            this.teamTwo
        },
        pullCountdownExpired = false,
        pullSequenceFalseStartRecorded = true,
        lastEvent = "False start on ${this.teamName(team)}.",
    ).withUndo(this, "Undo False Start on ${this.teamName(team)}")
}
/**
 * Count all pull violations recorded for a team.
 *
 * @param teamId The team whose offsides and false-start counts should be combined.
 */
private fun LiveGameState.pullViolationTotal(teamId: TeamId): Int {
    val team = if (teamId == TeamId.TEAM_ONE) this.teamOne else this.teamTwo
    return team.offsides + team.falseStarts
}
