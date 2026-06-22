package rmjarvis.ultiobserver

import kotlinx.serialization.Serializable

/// Type of pull infraction recorded during a pull sequence.
@Serializable
enum class PullInfractionType {
    OFFSIDES,
    FALSE_START,
    MAJORITY_PULL,
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
 * Confirmation details for a pull infraction before the observer applies it.
 *
 * @param event The event-shaped preview used to render the confirmation dialog.
 */
data class PullInfractionAssessmentPreview(
    val event: GameEvent? = null,
)

/**
 * Replace each team's cumulative pull-infraction counts as a manual correction.
 *
 * @param teamOneOffsides The corrected offsides count for team one.
 * @param teamOneFalseStarts The corrected false-start count for team one.
 * @param teamOneMajorityPulls The corrected majority-pull violation count for team one.
 * @param teamTwoOffsides The corrected offsides count for team two.
 * @param teamTwoFalseStarts The corrected false-start count for team two.
 * @param teamTwoMajorityPulls The corrected majority-pull violation count for team two.
 */
fun GameState.adjustPullInfractions(
    teamOneOffsides: Int,
    teamOneFalseStarts: Int,
    teamOneMajorityPulls: Int,
    teamTwoOffsides: Int,
    teamTwoFalseStarts: Int,
    teamTwoMajorityPulls: Int,
    now: Long,
): GameState {
    val adjustedTeamOneOffsides = teamOneOffsides.coerceAtLeast(0)
    val adjustedTeamOneFalseStarts = teamOneFalseStarts.coerceAtLeast(0)
    val adjustedTeamOneMajorityPulls = teamOneMajorityPulls.coerceAtLeast(0)
    val adjustedTeamTwoOffsides = teamTwoOffsides.coerceAtLeast(0)
    val adjustedTeamTwoFalseStarts = teamTwoFalseStarts.coerceAtLeast(0)
    val adjustedTeamTwoMajorityPulls = teamTwoMajorityPulls.coerceAtLeast(0)
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
            team = TeamId.TEAM_ONE,
            infraction = PullInfractionType.MAJORITY_PULL,
            delta = adjustedTeamOneMajorityPulls -
                this@adjustPullInfractions.teamOne.majorityPullViolations,
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
        addPullInfractionDelta(
            now = now,
            team = TeamId.TEAM_TWO,
            infraction = PullInfractionType.MAJORITY_PULL,
            delta = adjustedTeamTwoMajorityPulls -
                this@adjustPullInfractions.teamTwo.majorityPullViolations,
        )
    }
    return this.copy(
        teamOne = this.teamOne.copy(
            offsides = adjustedTeamOneOffsides,
            falseStarts = adjustedTeamOneFalseStarts,
            majorityPullViolations = adjustedTeamOneMajorityPulls,
        ),
        teamTwo = this.teamTwo.copy(
            offsides = adjustedTeamTwoOffsides,
            falseStarts = adjustedTeamTwoFalseStarts,
            majorityPullViolations = adjustedTeamTwoMajorityPulls,
        ),
        lastEvent = "Pull infractions adjusted.",
    ).withEventLogEntries(entries).withUndo(this, "Undo Pull infraction adjustment")
}

/**
 * Record offsides or false start for the selected team when allowed on the current pull.
 *
 * @param team The team that committed the pull infraction.
 * @param infraction The pull-infraction type to record.
 */
fun GameState.assessPullInfraction(
    team: TeamId,
    now: Long,
    infraction: PullInfractionType,
): PullInfractionAssessmentResult {
    require(this.isPullInfractionSelectionForTeam(team, infraction)) {
        "Pull infraction $infraction cannot be recorded for $team on this pull."
    }
    if (!this.canRecordPullInfraction(team)) {
        return PullInfractionAssessmentResult(this)
    }
    val updatedState = when (infraction) {
        PullInfractionType.OFFSIDES -> this.recordOffsides(now)
        PullInfractionType.FALSE_START -> this.recordFalseStart(now)
        PullInfractionType.MAJORITY_PULL -> this.recordMajorityPullViolation(now)
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
 * Build confirmation details for a pull infraction without changing game state.
 *
 * @param team The team that would receive the pull infraction.
 * @param infraction The pull-infraction type to preview.
 */
fun GameState.previewPullInfraction(
    team: TeamId,
    infraction: PullInfractionType,
): PullInfractionAssessmentPreview {
    require(this.isPullInfractionSelectionForTeam(team, infraction)) {
        "Pull infraction $infraction cannot be previewed for $team on this pull."
    }
    require(this.canRecordPullInfraction(team)) {
        "Pull infraction cannot be previewed after the button is disabled for $team."
    }
    val previewState = this.withPreviewPullInfraction(team, infraction)
    return PullInfractionAssessmentPreview(
        event = GameEvent.PullInfractionRecorded(
            state = previewState,
            team = team,
            infraction = infraction,
            totalPullViolations = previewState.pullViolationTotal(team),
        ),
    )
}

/// Return the pull-infraction type represented by the selected team's field button.
fun GameState.pullInfractionTypeFor(team: TeamId): PullInfractionType {
    return if (team == this.pullingTeam) {
        PullInfractionType.OFFSIDES
    } else {
        PullInfractionType.FALSE_START
    }
}

/// Return whether a concrete infraction selection belongs to the selected team on this pull.
private fun GameState.isPullInfractionSelectionForTeam(
    team: TeamId,
    infraction: PullInfractionType,
): Boolean {
    return infraction == this.pullInfractionTypeFor(team) ||
        (
            team == this.pullingTeam &&
                infraction == PullInfractionType.MAJORITY_PULL &&
                this.usesMajorityPullRule()
        )
}

/**
 * Return a state shaped like the result of a pull infraction for message preview only.
 *
 * @param team The team that would receive the pull infraction.
 * @param infraction The pull infraction that would be recorded.
 */
private fun GameState.withPreviewPullInfraction(team: TeamId, infraction: PullInfractionType): GameState {
    return when (infraction) {
        PullInfractionType.OFFSIDES -> copy(
            teamOne = if (team == TeamId.TEAM_ONE) teamOne.copy(offsides = teamOne.offsides + 1) else teamOne,
            teamTwo = if (team == TeamId.TEAM_TWO) teamTwo.copy(offsides = teamTwo.offsides + 1) else teamTwo,
            pullSequenceOffsidesRecorded = true,
        )
        PullInfractionType.FALSE_START -> copy(
            teamOne = if (team == TeamId.TEAM_ONE) teamOne.copy(falseStarts = teamOne.falseStarts + 1) else teamOne,
            teamTwo = if (team == TeamId.TEAM_TWO) teamTwo.copy(falseStarts = teamTwo.falseStarts + 1) else teamTwo,
            pullSequenceFalseStartRecorded = true,
        )
        PullInfractionType.MAJORITY_PULL -> copy(
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
        pullCountdownExpired = false,
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
        PullInfractionType.MAJORITY_PULL -> EventLogType.MAJORITY_PULL
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

/// Format a pull-infraction event popup title.
internal fun GameEvent.PullInfractionRecorded.formatPopupTitle(): String {
    return when (infraction) {
        PullInfractionType.OFFSIDES -> "Offsides"
        PullInfractionType.FALSE_START -> "False start"
        PullInfractionType.MAJORITY_PULL -> "Majority pull rule violation"
    }
}

/// Format a pull-infraction event message with the field-position consequence.
internal fun GameEvent.PullInfractionRecorded.formatMessage(): String {
    val teamName = state.teamName(team)
    val violationLine = "This is $teamName's ${totalPullViolations.ordinalWordText()} pull violation."
    val consequence = when (infraction) {
        PullInfractionType.OFFSIDES,
        PullInfractionType.MAJORITY_PULL -> if (totalPullViolations <= 1) {
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
        PullInfractionType.FALSE_START -> "${state.teamName(state.pullingTeam)} gets to set up on defense."
    }
    return "$violationLine\n\n$consequence"
}
