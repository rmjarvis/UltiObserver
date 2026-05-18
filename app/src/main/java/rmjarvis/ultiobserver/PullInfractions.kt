package rmjarvis.ultiobserver

import kotlinx.serialization.Serializable

/// Type of pull infraction recorded during a pull sequence.
@Serializable
enum class PullInfractionType {
    OFFSIDES,
    FALSE_START,
}

/**
 * State and optional popup event from trying to record a pull infraction.
 *
 * @param state The live state after the pull-infraction attempt.
 * @param event The observer-facing event to show, or null when no popup is needed.
 */
data class PullInfractionAssessmentResult(
    val state: LiveGameState,
    val event: GameEvent? = null,
)

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

/// Format a pull-infraction event popup title.
internal fun GameEvent.PullInfractionRecorded.formatPopupTitle(): String = "Pull Infraction"

/// Format a pull-infraction event message with the field-position consequence.
internal fun GameEvent.PullInfractionRecorded.formatMessage(): String {
    return when (infraction) {
        PullInfractionType.OFFSIDES -> if (totalPullViolations <= 1) {
            "Start at brick mark"
        } else {
            "Start at midfield"
        }
        PullInfractionType.FALSE_START -> "Defense gets to set up."
    }
}
