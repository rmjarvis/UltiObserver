package rmjarvis.ultiobserver

import kotlinx.serialization.Serializable

/// Type of pull violation recorded during a pull sequence.
@Serializable
enum class PullViolationType {
    OFFSIDES,
    FALSE_START,
    MAJORITY_PULL,
}

/**
 * State and optional popup event from trying to record a pull violation.
 *
 * @param state The live state after the pull-violation attempt.
 * @param event The observer-facing event to show, or null when no popup is needed.
 */
data class PullViolationAssessmentResult(
    val state: GameState,
    val event: GameEvent? = null,
)

/**
 * Confirmation details for a pull violation before the observer applies it.
 *
 * @param event The event-shaped preview used to render the confirmation dialog.
 */
data class PullViolationAssessmentPreview(
    val event: GameEvent.PullViolationRecorded,
)

/**
 * Replace each team's cumulative pull-related counts as a manual correction.
 *
 * @param teamOneOffsides The corrected offsides count for team one.
 * @param teamOneFalseStarts The corrected false-start count for team one.
 * @param teamOneMajorityPulls The corrected majority-pull violation count for team one.
 * @param teamOneTimeViolations The corrected time-violation count for team one.
 * @param teamTwoOffsides The corrected offsides count for team two.
 * @param teamTwoFalseStarts The corrected false-start count for team two.
 * @param teamTwoMajorityPulls The corrected majority-pull violation count for team two.
 * @param teamTwoTimeViolations The corrected time-violation count for team two.
 * @param now The correction timestamp.
 */
fun GameState.adjustPullViolations(
    teamOneOffsides: Int,
    teamOneFalseStarts: Int,
    teamOneMajorityPulls: Int,
    teamOneTimeViolations: Int,
    teamTwoOffsides: Int,
    teamTwoFalseStarts: Int,
    teamTwoMajorityPulls: Int,
    teamTwoTimeViolations: Int,
    now: Long,
): GameState {
    val adjustedTeamOneOffsides = teamOneOffsides.coerceAtLeast(0)
    val adjustedTeamOneFalseStarts = teamOneFalseStarts.coerceAtLeast(0)
    val adjustedTeamOneMajorityPulls = teamOneMajorityPulls.coerceAtLeast(0)
    val adjustedTeamOneTimeViolations = teamOneTimeViolations.coerceAtLeast(0)
    val adjustedTeamTwoOffsides = teamTwoOffsides.coerceAtLeast(0)
    val adjustedTeamTwoFalseStarts = teamTwoFalseStarts.coerceAtLeast(0)
    val adjustedTeamTwoMajorityPulls = teamTwoMajorityPulls.coerceAtLeast(0)
    val adjustedTeamTwoTimeViolations = teamTwoTimeViolations.coerceAtLeast(0)
    val entries = buildList {
        // this@adjustPullViolations is the GameState receiver; plain this is the list being built.
        addPullViolationDelta(
            now = now,
            team = TeamId.TEAM_ONE,
            violation = PullViolationType.OFFSIDES,
            delta = adjustedTeamOneOffsides - this@adjustPullViolations.teamOne.offsides,
        )
        addPullViolationDelta(
            now = now,
            team = TeamId.TEAM_ONE,
            violation = PullViolationType.FALSE_START,
            delta = adjustedTeamOneFalseStarts - this@adjustPullViolations.teamOne.falseStarts,
        )
        addPullViolationDelta(
            now = now,
            team = TeamId.TEAM_ONE,
            violation = PullViolationType.MAJORITY_PULL,
            delta = adjustedTeamOneMajorityPulls -
                this@adjustPullViolations.teamOne.majorityPullViolations,
        )
        addTimeViolationDelta(
            now = now,
            team = TeamId.TEAM_ONE,
            delta = adjustedTeamOneTimeViolations -
                this@adjustPullViolations.teamOne.timeViolations,
        )
        addPullViolationDelta(
            now = now,
            team = TeamId.TEAM_TWO,
            violation = PullViolationType.OFFSIDES,
            delta = adjustedTeamTwoOffsides - this@adjustPullViolations.teamTwo.offsides,
        )
        addPullViolationDelta(
            now = now,
            team = TeamId.TEAM_TWO,
            violation = PullViolationType.FALSE_START,
            delta = adjustedTeamTwoFalseStarts - this@adjustPullViolations.teamTwo.falseStarts,
        )
        addPullViolationDelta(
            now = now,
            team = TeamId.TEAM_TWO,
            violation = PullViolationType.MAJORITY_PULL,
            delta = adjustedTeamTwoMajorityPulls -
                this@adjustPullViolations.teamTwo.majorityPullViolations,
        )
        addTimeViolationDelta(
            now = now,
            team = TeamId.TEAM_TWO,
            delta = adjustedTeamTwoTimeViolations -
                this@adjustPullViolations.teamTwo.timeViolations,
        )
    }
    return this.copy(
        teamOne = this.teamOne.copy(
            offsides = adjustedTeamOneOffsides,
            falseStarts = adjustedTeamOneFalseStarts,
            majorityPullViolations = adjustedTeamOneMajorityPulls,
            timeViolations = adjustedTeamOneTimeViolations,
        ),
        teamTwo = this.teamTwo.copy(
            offsides = adjustedTeamTwoOffsides,
            falseStarts = adjustedTeamTwoFalseStarts,
            majorityPullViolations = adjustedTeamTwoMajorityPulls,
            timeViolations = adjustedTeamTwoTimeViolations,
        ),
        lastEvent = "Pull violations adjusted.",
    ).withEventLogEntries(entries).withUndo(this, "Undo Pull violation adjustment")
}

/**
 * Record offsides or false start for the selected team when allowed on the current pull.
 *
 * @param team The team that committed the pull violation.
 * @param violation The pull-violation type to record.
 */
fun GameState.assessPullViolation(
    team: TeamId,
    now: Long,
    violation: PullViolationType,
): PullViolationAssessmentResult {
    require(this.isPullViolationSelectionForTeam(team, violation)) {
        "Pull violation $violation cannot be recorded for $team on this pull."
    }
    if (!this.canRecordPullViolation(team)) {
        return PullViolationAssessmentResult(this)
    }
    val updatedState = when (violation) {
        PullViolationType.OFFSIDES -> this.recordOffsides(now)
        PullViolationType.FALSE_START -> this.recordFalseStart(now)
        PullViolationType.MAJORITY_PULL -> this.recordMajorityPullViolation(now)
    }
    return PullViolationAssessmentResult(
        state = updatedState,
        event = GameEvent.PullViolationRecorded(
            state = updatedState,
            team = team,
            violation = violation,
            totalPullViolations = updatedState.pullViolationTotal(team),
        ),
    )
}

/**
 * Build confirmation details for a pull violation without changing game state.
 *
 * @param team The team that would receive the pull violation.
 * @param violation The pull-violation type to preview.
 */
fun GameState.previewPullViolation(
    team: TeamId,
    violation: PullViolationType,
): PullViolationAssessmentPreview {
    require(this.isPullViolationSelectionForTeam(team, violation)) {
        "Pull violation $violation cannot be previewed for $team on this pull."
    }
    require(this.canRecordPullViolation(team)) {
        "Pull violation cannot be previewed after the button is disabled for $team."
    }
    val previewState = this.withPreviewPullViolation(team, violation)
    return PullViolationAssessmentPreview(
        event = GameEvent.PullViolationRecorded(
            state = previewState,
            team = team,
            violation = violation,
            totalPullViolations = previewState.pullViolationTotal(team),
        ),
    )
}

/// Return the pull-violation type represented by the selected team's field button.
fun GameState.pullViolationTypeFor(team: TeamId): PullViolationType {
    return if (team == this.pullingTeam) {
        PullViolationType.OFFSIDES
    } else {
        PullViolationType.FALSE_START
    }
}

/// Return whether a concrete violation selection belongs to the selected team on this pull.
private fun GameState.isPullViolationSelectionForTeam(
    team: TeamId,
    violation: PullViolationType,
): Boolean {
    return violation == this.pullViolationTypeFor(team) ||
        (
            team == this.pullingTeam &&
                violation == PullViolationType.MAJORITY_PULL &&
                this.usesMajorityPullRule()
        )
}

/**
 * Return a state shaped like the result of a pull violation for message preview only.
 *
 * @param team The team that would receive the pull violation.
 * @param violation The pull violation that would be recorded.
 */
private fun GameState.withPreviewPullViolation(team: TeamId, violation: PullViolationType): GameState {
    return when (violation) {
        PullViolationType.OFFSIDES -> copy(
            teamOne = if (team == TeamId.TEAM_ONE) teamOne.copy(offsides = teamOne.offsides + 1) else teamOne,
            teamTwo = if (team == TeamId.TEAM_TWO) teamTwo.copy(offsides = teamTwo.offsides + 1) else teamTwo,
            pullSequenceOffsidesRecorded = true,
        )
        PullViolationType.FALSE_START -> copy(
            teamOne = if (team == TeamId.TEAM_ONE) teamOne.copy(falseStarts = teamOne.falseStarts + 1) else teamOne,
            teamTwo = if (team == TeamId.TEAM_TWO) teamTwo.copy(falseStarts = teamTwo.falseStarts + 1) else teamTwo,
            pullSequenceFalseStartRecorded = true,
        )
        PullViolationType.MAJORITY_PULL -> copy(
            teamOne = if (team == TeamId.TEAM_ONE) {
                teamOne.copy(majorityPullViolations = teamOne.majorityPullViolations + 1)
            } else {
                teamOne
            },
            teamTwo = if (team == TeamId.TEAM_TWO) {
                teamTwo.copy(majorityPullViolations = teamTwo.majorityPullViolations + 1)
            } else {
                teamTwo
            },
            pullSequenceOffsidesRecorded = true,
        )
    }
}

/**
 * Report whether the selected team may record a pull violation on this pull sequence.
 *
 * @param team The team whose violation button or action is being considered.
 */
fun GameState.canRecordPullViolation(team: TeamId): Boolean {
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
    if (
        this.pullSkippedForCurrentPoint ||
        this.pullSequenceOffsidesRecorded
    ) {
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

/// Record a majority-pull violation against the current pulling team.
fun GameState.recordMajorityPullViolation(now: Long): GameState {
    if (
        this.pullSkippedForCurrentPoint ||
        !this.usesMajorityPullRule() ||
        this.pullSequenceOffsidesRecorded
    ) {
        return this
    }
    val team = this.pullingTeam
    return this.copy(
        teamOne = if (team == TeamId.TEAM_ONE) {
            this.teamOne.copy(majorityPullViolations = this.teamOne.majorityPullViolations + 1)
        } else {
            this.teamOne
        },
        teamTwo = if (team == TeamId.TEAM_TWO) {
            this.teamTwo.copy(majorityPullViolations = this.teamTwo.majorityPullViolations + 1)
        } else {
            this.teamTwo
        },
        phase = GamePhase.LIVE_POINT,
        countdown = null,
        pullSequenceOffsidesRecorded = true,
        lastEvent = "Majority pull violation on ${this.teamName(team)}.",
    ).withEventLogEntry(
        EventLogEntry(
            timestampEpoch = now,
            type = EventLogType.MAJORITY_PULL,
            team = team,
        )
    ).withUndo(this, "Undo Majority pull violation on ${this.teamName(team)}")
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
 * Add a pull-violation correction entry when a count changed.
 *
 * @param now The correction timestamp.
 * @param team The team whose count changed.
 * @param violation The pull-violation count that changed.
 * @param delta The signed count change.
 */
private fun MutableList<EventLogEntry>.addPullViolationDelta(
    now: Long,
    team: TeamId,
    violation: PullViolationType,
    delta: Int,
) {
    if (delta != 0) {
        add(
            EventLogEntry(
                timestampEpoch = now,
                type = violation.eventLogType(),
                team = team,
                delta = delta,
            )
        )
    }
}

/**
 * Add a time-violation correction entry when a count changed.
 *
 * @param now The correction timestamp.
 * @param team The team whose time-violation count changed.
 * @param delta The signed count change.
 */
private fun MutableList<EventLogEntry>.addTimeViolationDelta(
    now: Long,
    team: TeamId,
    delta: Int,
) {
    if (delta != 0) {
        add(
            EventLogEntry(
                timestampEpoch = now,
                type = EventLogType.TIME_VIOLATION,
                team = team,
                delta = delta,
            )
        )
    }
}

/// Return the event-log type represented by this pull-violation type.
private fun PullViolationType.eventLogType(): EventLogType {
    return when (this) {
        PullViolationType.OFFSIDES -> EventLogType.OFFSIDES
        PullViolationType.FALSE_START -> EventLogType.FALSE_START
        PullViolationType.MAJORITY_PULL -> EventLogType.MAJORITY_PULL
    }
}

/**
 * Count all pull violations recorded for a team.
 *
 * @param teamId The team whose offsides and false-start counts should be combined.
 */
private fun GameState.pullViolationTotal(teamId: TeamId): Int {
    val team = if (teamId == TeamId.TEAM_ONE) this.teamOne else this.teamTwo
    return team.pullViolationCount()
}

/// Format a pull-violation event popup title.
internal fun GameEvent.PullViolationRecorded.formatPopupTitle(): String {
    return when (violation) {
        PullViolationType.OFFSIDES -> "Offsides"
        PullViolationType.FALSE_START -> "False start"
        PullViolationType.MAJORITY_PULL -> "Majority pull rule violation"
    }
}

/// Format a pull-violation event message with the field-position consequence.
internal fun GameEvent.PullViolationRecorded.formatMessage(): String {
    val teamName = state.teamName(team)
    val violationLine = "This is $teamName's ${totalPullViolations.ordinalWordText()} pull violation."
    val consequence = when (violation) {
        PullViolationType.OFFSIDES,
        PullViolationType.MAJORITY_PULL -> if (totalPullViolations <= 1) {
            "${state.teamName(state.pullingTeam.flip())} starts at the brick mark."
        } else {
            "${state.teamName(state.pullingTeam.flip())} starts at midfield."
        }.let { fieldPosition ->
            if (state.pullSequenceFalseStartRecorded) {
                fieldPosition
            } else {
                "$fieldPosition\n\nThe disc is live -- no defensive check is required."
            }
        }
        PullViolationType.FALSE_START -> "${state.teamName(state.pullingTeam)} gets to set up on defense."
    }
    return "$violationLine\n\n$consequence"
}
