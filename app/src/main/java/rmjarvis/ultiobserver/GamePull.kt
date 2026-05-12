package rmjarvis.ultiobserver

// Manually change the number of offside or false starts each team has.
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
// Swap which team is on which end of the field.
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
// Swap which team is pulling.
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
// Record the pull infraction that belongs to the selected team, if not already recorded on this pull.
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
    if (updatedState == this) {
        return PullInfractionAssessmentResult(this)
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

// Expired pull actions are available after undoing an automatic start point.
fun LiveGameState.hasExpiredPullActions(): Boolean {
    return this.phase == LivePhase.BETWEEN_POINTS && this.pullCountdownExpired
}

// Undoing automatic start point returns here so the observer can assess a time violation instead.
internal fun LiveGameState.expiredPullDecisionState(): LiveGameState {
    return this.copy(
        countdown = null,
        pullCountdownExpired = true,
    )
}

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

// First time violation is a warning with 30 seconds to ready or pull.
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

// Later time violations charge a timeout when one remains: 70 seconds for offense, 90 for defense.
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

private fun LiveGameState.currentSideCountdownTarget(): BetweenPointsCountdownTarget {
    return if (this.pullingFromEnd == FieldEnd.NEAR) {
        BetweenPointsCountdownTarget.PULL
    } else {
        BetweenPointsCountdownTarget.OFFENSE_READY
    }
}

internal fun LiveGameState.isNearSideTeam(team: TeamId): Boolean {
    return team == if (this.pullingFromEnd == FieldEnd.NEAR) {
        this.pullingTeam
    } else {
        this.pullingTeam.flip()
    }
}

// If no timeout remains, skip the pull and show field-position guidance.
private fun LiveGameState.recordTimeViolationWithoutTimeout(team: TeamId): LiveGameState {
    return this.copy(
        countdown = null,
        pullCountdownExpired = false,
        pullSkippedForCurrentPoint = true,
        lastEvent = "Time violation on ${this.teamName(team)}.",
    ).withUndo(this, "Undo Time Violation on ${this.teamName(team)}")
}

private fun LiveGameState.timeViolationWarningIssued(team: TeamId): Boolean {
    return if (team == TeamId.TEAM_ONE) {
        this.teamOne.timeViolationWarningIssued
    } else {
        this.teamTwo.timeViolationWarningIssued
    }
}

// Offsides on the pulling team
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
// False start on the receiving team
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
// Count total pull violations for a team.
private fun LiveGameState.pullViolationTotal(teamId: TeamId): Int {
    val team = if (teamId == TeamId.TEAM_ONE) this.teamOne else this.teamTwo
    return team.offsides + team.falseStarts
}
