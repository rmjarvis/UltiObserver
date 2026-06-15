package rmjarvis.ultiobserver

import java.time.Duration
import kotlinx.serialization.Serializable

/**
 * Between-points responsibility the near-side observer is timing.
 *
 * @param label The short countdown label shown on the live screen.
 * @param standardDurationSeconds The normal between-points duration for this target.
 * @param openingDurationSeconds The shorter opening-pull duration for this target.
 */
@Serializable
enum class BetweenPointsCountdownTarget(
    val label: String,
    private val standardDurationSeconds: Int,
    private val openingDurationSeconds: Int,
) {
    OFFENSE_READY("Signal in", 60, 20),
    PULL("Pull in", 80, 40);

    /**
     * Return the base countdown duration for this target and countdown kind.
     *
     * @param kind The countdown kind whose opening/reset rules may override the standard duration.
     */
    fun baseDurationSeconds(kind: CountdownKind): Int {
        return when (kind) {
            CountdownKind.OPENING_PULL -> openingDurationSeconds
            CountdownKind.PULL_RESET -> 30
            else -> standardDurationSeconds
        }
    }

    /// Return the opposite between-points timing target.
    fun flip(): BetweenPointsCountdownTarget {
        return if (this == OFFENSE_READY) PULL else OFFENSE_READY
    }

    /// Return the alert cue used when a timeout extension adds one minute to this target.
    fun timeoutCueId(): TimingCueId {
        return when (this) {
            OFFENSE_READY -> TimingCueId.TIMEOUT_BETWEEN_POINTS_ONE_MINUTE_FOR_HAND
            PULL -> TimingCueId.TIMEOUT_BETWEEN_POINTS_ONE_MINUTE_TO_PULL
        }
    }
}

/**
 * State and optional popup event from assessing a pull time violation.
 *
 * @param state The live state after the assessment.
 * @param event The observer-facing event to show, or null when no popup is needed.
 */
data class TimeViolationAssessmentResult(
    val state: GameState,
    val event: GameEvent? = null,
)

/// Rule outcome from assessing a team's pull time violation.
@Serializable
enum class TimeViolationOutcome {
    WARNING,
    TIMEOUT,
    NO_TIMEOUT,
}

/**
 * Build the countdown that applies between points for the observer's end of the field.
 * The exact target depends on whether the prompted end is on the pulling or receiving side.
 *
 * @param pullingFromEnd The field end the pulling team occupies, which determines the observer's responsibility.
 * @param sequenceStart The epoch millis when the between-points sequence starts.
 * @param kind The between-points countdown kind, used to distinguish normal, opening, and reset timing.
 * @param promptEnd The single field end whose timing prompts should be generated.
 */
internal fun buildBetweenPointsCountdown(
    pullingFromEnd: FieldEnd,
    sequenceStart: Long,
    kind: CountdownKind = CountdownKind.BETWEEN_POINTS,
    promptEnd: FieldEnd = FieldEnd.NEAR,
): CountdownState {
    require(kind.usesBetweenPointsTarget()) {
        "Countdown kind $kind does not use between-points timing."
    }
    val target = betweenPointsCountdownTargetFor(
        pullingFromEnd = pullingFromEnd,
        promptEnd = promptEnd,
    )
    val durationSeconds = target.baseDurationSeconds(kind)
    return CountdownState(
        kind = kind,
        label = target.label,
        durationSeconds = durationSeconds,
        targetEpoch = sequenceStart + durationSeconds * 1000L,
        betweenPointsTarget = target,
    )
}

/**
 * Select the timing target the prompted field end is responsible for between points.
 *
 * @param pullingFromEnd The field end the pulling team occupies.
 * @param promptEnd The field end whose timing prompts should be generated.
 */
private fun betweenPointsCountdownTargetFor(
    pullingFromEnd: FieldEnd,
    promptEnd: FieldEnd,
): BetweenPointsCountdownTarget {
    return if (pullingFromEnd == promptEnd) {
        BetweenPointsCountdownTarget.PULL
    } else {
        BetweenPointsCountdownTarget.OFFENSE_READY
    }
}

/**
 * Return this countdown retargeted to the chosen prompt end, preserving any timeout extension.
 *
 * @param pullingFromEnd The field end the pulling team occupies.
 * @param promptEnd The field end whose timing prompts should be generated.
 */
internal fun CountdownState.withPromptEnd(
    pullingFromEnd: FieldEnd,
    promptEnd: FieldEnd,
): CountdownState {
    if (!kind.usesBetweenPointsTarget()) {
        return this
    }
    val currentTarget = betweenPointsTarget!!
    val newTarget = betweenPointsCountdownTargetFor(
        pullingFromEnd = pullingFromEnd,
        promptEnd = promptEnd,
    )
    if (currentTarget == newTarget) {
        return this
    }
    val extensionSeconds = durationSeconds - currentTarget.baseDurationSeconds(kind)
    val newDurationSeconds = newTarget.baseDurationSeconds(kind) + extensionSeconds
    val sequenceStart = targetEpoch - durationSeconds * 1000L
    return copy(
        label = newTarget.label,
        durationSeconds = newDurationSeconds,
        targetEpoch = sequenceStart + newDurationSeconds * 1000L,
        betweenPointsTarget = newTarget,
    )
}

/**
 * Compute the label and remaining time for the visible between-points countdown.
 *
 * @param pullingFromEnd The field end the pulling team occupies.
 * @param sequenceStart The epoch millis when the between-points sequence started.
 * @param now The epoch millis used to compute remaining time.
 * @param kind The between-points countdown kind to display.
 * @param promptEnd The single field end whose timing prompts should be generated.
 */
fun betweenPointsDisplay(
    pullingFromEnd: FieldEnd,
    sequenceStart: Long,
    now: Long,
    kind: CountdownKind = CountdownKind.BETWEEN_POINTS,
    promptEnd: FieldEnd = FieldEnd.NEAR,
): Pair<String, Duration> {
    val countdown = buildBetweenPointsCountdown(pullingFromEnd, sequenceStart, kind, promptEnd)
    return countdown.label to Duration.ofMillis((countdown.targetEpoch - now).coerceAtLeast(0L))
}

/// Return the single field end selected for pull prompts, or null for multi/off modes.
internal fun PullPromptTarget.singlePromptEndOrNull(): FieldEnd? {
    return when (this) {
        PullPromptTarget.NEAR -> FieldEnd.NEAR
        PullPromptTarget.FAR -> FieldEnd.FAR
        PullPromptTarget.BOTH -> null
        PullPromptTarget.NEITHER -> null
    }
}

/// Return the single prompt end currently supported by countdown generation.
internal fun PullPromptTarget.countdownPromptEnd(): FieldEnd {
    return singlePromptEndOrNull() ?: FieldEnd.NEAR
}

/// List normal between-points cues, including timeout-extension cues when applicable.
internal fun CountdownState.betweenPointsTimingCues(): List<TimingCue> {
    val target = betweenPointsTarget!!
    val timeoutCues = if (durationSeconds > target.baseDurationSeconds(kind)) {
        listOf(TimingCue(target.timeoutCueId(), 60))
    } else {
        emptyList()
    }
    return timeoutCues + when (target) {
        BetweenPointsCountdownTarget.OFFENSE_READY -> listOf(
            TimingCue(TimingCueId.RECEIVING_TWENTY_FOR_HAND, 20),
            TimingCue(TimingCueId.RECEIVING_TEN_FOR_HAND, 10),
            TimingCue(TimingCueId.RECEIVING_GIVE_HAND, 0),
        )
        BetweenPointsCountdownTarget.PULL -> listOf(
            TimingCue(TimingCueId.PULLING_TWENTY_TO_PULL, 20),
            TimingCue(TimingCueId.PULLING_TEN_TO_PULL, 10),
            TimingCue(TimingCueId.PULLING_TIME_VIOLATION, 0),
        )
    }
}

/// Report whether the expired-pull action surface should be available.
fun GameState.hasExpiredPullActions(): Boolean {
    return this.phase == GamePhase.BETWEEN_POINTS && this.pullCountdownExpired
}

/// Build the state restored by undoing automatic start point so time violation can still be assessed.
internal fun GameState.expiredPullDecisionState(): GameState {
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
fun GameState.assessTimeViolation(team: TeamId, now: Long): TimeViolationAssessmentResult {
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
        TimeViolationOutcome.NO_TIMEOUT -> this.recordTimeViolationWithoutTimeout(team, now)
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
 * Record a team's first time violation warning and start prompted-side timing when applicable.
 * An unprompted-side warning is recorded without starting a countdown for this observer.
 *
 * @param team The team receiving its warning.
 * @param now The epoch millis used to start the warning countdown.
 */
private fun GameState.recordTimeViolationWarning(team: TeamId, now: Long): GameState {
    val violatingTeamEnd = fieldEndForTeam(team)
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
        countdown = if (violatingTeamEnd == pullPromptTarget.countdownPromptEnd()) {
            this.buildTimeViolationCountdown(
                now = now,
                durationSeconds = 30,
                kind = CountdownKind.PULL_RESET,
            )
        } else {
            null
        },
        pullCountdownExpired = false,
        lastEvent = "Time violation warning on ${this.teamName(team)}.",
    ).withEventLogEntry(
        EventLogEntry(
            timestampEpoch = now,
            type = EventLogType.TIME_VIOLATION,
            team = team,
            timeViolationOutcome = TimeViolationOutcome.WARNING,
        )
    ).withUndo(this, "Undo Time violation warning on ${this.teamName(team)}")
}

/**
 * Restart the normal pull countdown from an expired-pull decision state.
 *
 * @param now The epoch millis used as the restarted countdown's sequence start.
 */
fun GameState.restartPullCountdown(now: Long): GameState {
    if (!this.hasExpiredPullActions()) {
        return this
    }
    return this.copy(
        countdown = buildBetweenPointsCountdown(
            pullingFromEnd = this.pullingFromEnd,
            sequenceStart = now,
            promptEnd = this.pullPromptTarget.countdownPromptEnd(),
        ),
        pullCountdownExpired = false,
        lastEvent = "Pull countdown restarted.",
    ).withUndo(this, "Undo Restart pull countdown")
}

/**
 * Record a later time violation that charges a timeout and starts the appropriate reset countdown.
 * Timeout resets are 70 seconds when the prompted side is offense and 90 seconds when it is defense.
 *
 * @param team The team being charged a timeout.
 * @param now The epoch millis used to start the reset countdown.
 */
private fun GameState.recordTimeViolationTimeout(team: TeamId, now: Long): GameState {
    val target = this.currentCountdownTarget()
    val durationSeconds = when (target) {
        BetweenPointsCountdownTarget.OFFENSE_READY -> 70
        BetweenPointsCountdownTarget.PULL -> 90
    }
    return this.copy(
        teamOne = if (team == TeamId.TEAM_ONE) this.teamOne.withAddedTimeout() else this.teamOne,
        teamTwo = if (team == TeamId.TEAM_TWO) this.teamTwo.withAddedTimeout() else this.teamTwo,
        countdown = buildTimeViolationCountdown(
            now = now,
            durationSeconds = durationSeconds,
            kind = CountdownKind.BETWEEN_POINTS,
        ),
        pullCountdownExpired = false,
        lastEvent = "Timeout charged to ${this.teamName(team)} for time violation.",
    ).withEventLogEntry(
        EventLogEntry(
            timestampEpoch = now,
            type = EventLogType.TIME_VIOLATION,
            team = team,
            timeViolationOutcome = TimeViolationOutcome.TIMEOUT,
        )
    ).withUndo(this, "Undo Time violation timeout on ${this.teamName(team)}")
}

/**
 * Build the countdown used after a prompted-side time violation.
 *
 * @param now The epoch millis used as the countdown start.
 * @param durationSeconds The length of the reset countdown.
 * @param kind The countdown kind so warning and timeout resets can use different cue behavior.
 */
private fun GameState.buildTimeViolationCountdown(
    now: Long,
    durationSeconds: Int,
    kind: CountdownKind,
): CountdownState {
    val target = this.currentCountdownTarget()
    return CountdownState(
        kind = kind,
        label = target.label,
        durationSeconds = durationSeconds,
        targetEpoch = now + durationSeconds * 1000L,
        betweenPointsTarget = target,
    )
}

/**
 * Return the field end occupied by a team for the current pull.
 *
 * @param team The team whose field end should be returned.
 */
internal fun GameState.fieldEndForTeam(team: TeamId): FieldEnd {
    return if (team == pullingTeam) {
        pullingFromEnd
    } else {
        pullingFromEnd.flip()
    }
}

/// Return the between-points timing target for the currently prompted side of the field.
private fun GameState.currentCountdownTarget(): BetweenPointsCountdownTarget {
    return betweenPointsCountdownTargetFor(
        pullingFromEnd = pullingFromEnd,
        promptEnd = pullPromptTarget.countdownPromptEnd(),
    )
}

/**
 * Record a time violation when no timeout remains, producing a no-pull consequence.
 * This is the no-timeout branch from the expired-pull decision surface.
 *
 * @param team The violating team.
 */
private fun GameState.recordTimeViolationWithoutTimeout(team: TeamId, now: Long): GameState {
    return this.copy(
        countdown = null,
        pullCountdownExpired = false,
        pullSkippedForCurrentPoint = true,
        lastEvent = "Time violation on ${this.teamName(team)}.",
    ).withEventLogEntry(
        EventLogEntry(
            timestampEpoch = now,
            type = EventLogType.TIME_VIOLATION,
            team = team,
            timeViolationOutcome = TimeViolationOutcome.NO_TIMEOUT,
        )
    ).withUndo(this, "Undo Time violation on ${this.teamName(team)}")
}

/**
 * Report whether a team has already received its pull time-violation warning.
 *
 * @param team The team whose warning flag should be read.
 */
private fun GameState.timeViolationWarningIssued(team: TeamId): Boolean {
    return if (team == TeamId.TEAM_ONE) {
        this.teamOne.timeViolationWarningIssued
    } else {
        this.teamTwo.timeViolationWarningIssued
    }
}

/// Format a time-violation event popup title.
internal fun GameEvent.TimeViolationRecorded.formatPopupTitle(): String = "Time violation"

/// Format a time-violation event message with warning, timeout, or no-timeout consequences.
internal fun GameEvent.TimeViolationRecorded.formatMessage(): String {
    return when (outcome) {
        TimeViolationOutcome.WARNING -> {
            if (team == state.pullingTeam) {
                "${state.teamName(team)} now has 30 seconds to pull."
            } else {
                "${state.teamName(team)} now has 30 seconds to signal readiness."
            }
        }
        TimeViolationOutcome.TIMEOUT -> "Timeout charged to ${state.teamName(team)}. Reset pull timing."
        TimeViolationOutcome.NO_TIMEOUT -> {
            if (team == state.pullingTeam.flip()) {
                "No timeouts remaining. No pull. Receiving team starts at midpoint of defending end zone."
            } else {
                "No timeouts remaining. No pull. Receiving team starts at midfield."
            }
        }
    }
}
