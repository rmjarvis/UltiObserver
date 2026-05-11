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
            team = team,
            infraction = infraction,
            totalPullViolations = updatedState.pullViolationTotal(team),
        ),
    )
}
// Offsides on the pulling team
fun LiveGameState.recordOffsides(): LiveGameState {
    if (this.pullSequenceOffsidesRecorded) {
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
        pullSequenceOffsidesRecorded = true,
        lastEvent = "Offsides on ${teamName(this, team)}.",
    ).withUndo(this, "Undo Offsides on ${teamName(this, team)}")
}
// False start on the receiving team
fun LiveGameState.recordFalseStart(): LiveGameState {
    if (this.pullSequenceFalseStartRecorded) {
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
        pullSequenceFalseStartRecorded = true,
        lastEvent = "False start on ${teamName(this, team)}.",
    ).withUndo(this, "Undo False Start on ${teamName(this, team)}")
}
// Count total pull violations for a team.
private fun LiveGameState.pullViolationTotal(teamId: TeamId): Int {
    val team = if (teamId == TeamId.TEAM_ONE) this.teamOne else this.teamTwo
    return team.offsides + team.falseStarts
}
// Get the team name for a given id
internal fun teamName(state: LiveGameState, team: TeamId): String {
    return if (team == TeamId.TEAM_ONE) state.teamOne.name else state.teamTwo.name
}
