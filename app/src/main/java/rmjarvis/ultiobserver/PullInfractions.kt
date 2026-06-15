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
    val state: GameState,
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
fun GameState.adjustPullInfractions(
    teamOneOffsides: Int,
    teamOneFalseStarts: Int,
    teamTwoOffsides: Int,
    teamTwoFalseStarts: Int,
    now: Long,
): GameState {
    val adjustedTeamOneOffsides = teamOneOffsides.coerceAtLeast(0)
    val adjustedTeamOneFalseStarts = teamOneFalseStarts.coerceAtLeast(0)
    val adjustedTeamTwoOffsides = teamTwoOffsides.coerceAtLeast(0)
    val adjustedTeamTwoFalseStarts = teamTwoFalseStarts.coerceAtLeast(0)
    val entries = buildList {
        // this@adjustPullInfractions is the GameState receiver; plain this is the list being built.
        addPullInfractionDelta(
            now = now,
            team = TeamId.TEAM_ONE,
            infraction = PullInfractionType.OFFSIDES,
            delta = adjustedTeamOneOffsides - this@adjustPullInfractions.teamOne.offsides,
        )
        addPullInfractionDelta(
            now = now,
            team = TeamId.TEAM_ONE,
            infraction = PullInfractionType.FALSE_START,
            delta = adjustedTeamOneFalseStarts - this@adjustPullInfractions.teamOne.falseStarts,
        )
        addPullInfractionDelta(
            now = now,
            team = TeamId.TEAM_TWO,
            infraction = PullInfractionType.OFFSIDES,
            delta = adjustedTeamTwoOffsides - this@adjustPullInfractions.teamTwo.offsides,
        )
        addPullInfractionDelta(
            now = now,
            team = TeamId.TEAM_TWO,
            infraction = PullInfractionType.FALSE_START,
            delta = adjustedTeamTwoFalseStarts - this@adjustPullInfractions.teamTwo.falseStarts,
        )
    }
    return this.copy(
        teamOne = this.teamOne.copy(
            offsides = adjustedTeamOneOffsides,
            falseStarts = adjustedTeamOneFalseStarts,
        ),
        teamTwo = this.teamTwo.copy(
            offsides = adjustedTeamTwoOffsides,
            falseStarts = adjustedTeamTwoFalseStarts,
        ),
        lastEvent = "Pull infractions adjusted.",
    ).withEventLogEntries(entries).withUndo(this, "Undo Pull infraction adjustment")
}

/**
 * Record offsides or false start for the selected team when allowed on the current pull.
 *
 * @param team The team that committed the pull infraction.
 */
fun GameState.assessPullInfraction(team: TeamId, now: Long): PullInfractionAssessmentResult {
    if (!this.canRecordPullInfraction(team)) {
        return PullInfractionAssessmentResult(this)
    }
    val infraction = if (team == this.pullingTeam) {
        PullInfractionType.OFFSIDES
    } else {
        PullInfractionType.FALSE_START
    }
    val updatedState = when (infraction) {
        PullInfractionType.OFFSIDES -> this.recordOffsides(now)
        PullInfractionType.FALSE_START -> this.recordFalseStart(now)
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
fun GameState.canRecordPullInfraction(team: TeamId): Boolean {
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
fun GameState.recordOffsides(now: Long): GameState {
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
        phase = GamePhase.LIVE_POINT,
        countdown = null,
        pullCountdownExpired = false,
        pullSequenceOffsidesRecorded = true,
        lastEvent = "Offsides on ${this.teamName(team)}.",
    ).withEventLogEntry(
        EventLogEntry(
            timestampEpoch = now,
            type = EventLogType.OFFSIDES,
            team = team,
        )
    ).withUndo(this, "Undo Offsides on ${this.teamName(team)}")
}

/// Record false start against the current receiving team.
fun GameState.recordFalseStart(now: Long): GameState {
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
    ).withEventLogEntry(
        EventLogEntry(
            timestampEpoch = now,
            type = EventLogType.FALSE_START,
            team = team,
        )
    ).withUndo(this, "Undo False start on ${this.teamName(team)}")
}

/**
 * Add a pull-infraction correction entry when a count changed.
 *
 * @param now The correction timestamp.
 * @param team The team whose count changed.
 * @param infraction The pull-infraction count that changed.
 * @param delta The signed count change.
 */
private fun MutableList<EventLogEntry>.addPullInfractionDelta(
    now: Long,
    team: TeamId,
    infraction: PullInfractionType,
    delta: Int,
) {
    if (delta != 0) {
        add(
            EventLogEntry(
                timestampEpoch = now,
                type = infraction.eventLogType(),
                team = team,
                delta = delta,
            )
        )
    }
}

/// Return the event-log type represented by this pull-infraction type.
private fun PullInfractionType.eventLogType(): EventLogType {
    return when (this) {
        PullInfractionType.OFFSIDES -> EventLogType.OFFSIDES
        PullInfractionType.FALSE_START -> EventLogType.FALSE_START
    }
}

/**
 * Count all pull violations recorded for a team.
 *
 * @param teamId The team whose offsides and false-start counts should be combined.
 */
private fun GameState.pullViolationTotal(teamId: TeamId): Int {
    val team = if (teamId == TeamId.TEAM_ONE) this.teamOne else this.teamTwo
    return team.offsides + team.falseStarts
}

/// Format a pull-infraction event popup title.
internal fun GameEvent.PullInfractionRecorded.formatPopupTitle(): String = "Pull infraction"

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
