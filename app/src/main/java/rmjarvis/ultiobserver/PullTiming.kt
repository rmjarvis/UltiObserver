package rmjarvis.ultiobserver

import java.time.Duration
import kotlinx.serialization.Serializable

/**
 * Between-points countdown mode the observer is timing.
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
    PULL("Pull in", 80, 40),
    BOTH("Pull in", 80, 40),
    NEITHER("Pull in", 80, 40);

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
        return when (this) {
            OFFENSE_READY -> PULL
            PULL -> OFFENSE_READY
            BOTH -> BOTH
            NEITHER -> NEITHER
        }
    }

    /// Return the alert cues used when a timeout extension adds one minute to this target.
    fun timeoutCueIds(): List<TimingCueId> {
        return when (this) {
            OFFENSE_READY -> listOf(TimingCueId.TIMEOUT_BETWEEN_POINTS_ONE_MINUTE_FOR_HAND)
            PULL -> listOf(TimingCueId.TIMEOUT_BETWEEN_POINTS_ONE_MINUTE_TO_PULL)
            BOTH -> listOf(TimingCueId.TIMEOUT_BETWEEN_POINTS_ONE_MINUTE_FOR_HAND)
            NEITHER -> emptyList()
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
 * Build the countdown that applies between points for the configured pull prompts.
 * The exact target depends on whether the prompted end is on the pulling or receiving side.
 *
 * @param pullingFromEnd The field end the pulling team occupies.
 * @param sequenceStart The epoch millis when the between-points sequence starts.
 * @param kind The between-points countdown kind, used to distinguish normal, opening, and reset timing.
 * @param promptTarget Which field end or ends should receive timing prompts.
 */
internal fun buildBetweenPointsCountdown(
    pullingFromEnd: FieldEnd,
    sequenceStart: Long,
    kind: CountdownKind = CountdownKind.BETWEEN_POINTS,
    promptTarget: PullPromptTarget = PullPromptTarget.NEAR,
): CountdownState {
    require(kind.usesBetweenPointsTarget()) {
        "Countdown kind $kind does not use between-points timing."
    }
    val target = betweenPointsCountdownTarget(
        pullingFromEnd = pullingFromEnd,
        promptTarget = promptTarget,
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
 * Select the timing target represented by the pull-prompt target.
 *
 * @param pullingFromEnd The field end the pulling team occupies.
 * @param promptTarget Which field end or ends should receive timing prompts.
 */
private fun betweenPointsCountdownTarget(
    pullingFromEnd: FieldEnd,
    promptTarget: PullPromptTarget,
): BetweenPointsCountdownTarget {
    return when (promptTarget) {
        PullPromptTarget.NEAR -> betweenPointsCountdownTargetForEnd(
            pullingFromEnd = pullingFromEnd,
            promptEnd = FieldEnd.NEAR,
        )
        PullPromptTarget.FAR -> betweenPointsCountdownTargetForEnd(
            pullingFromEnd = pullingFromEnd,
            promptEnd = FieldEnd.FAR,
        )
        PullPromptTarget.BOTH -> BetweenPointsCountdownTarget.BOTH
        PullPromptTarget.NEITHER -> BetweenPointsCountdownTarget.NEITHER
    }
}

/**
 * Select the timing target for one field end.
 *
 * @param pullingFromEnd The field end the pulling team occupies.
 * @param promptEnd The field end whose timing prompts should be generated.
 */
private fun betweenPointsCountdownTargetForEnd(
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
 * Return this countdown retargeted to the chosen pull-prompt target, preserving any timeout extension.
 *
 * @param pullingFromEnd The field end the pulling team occupies.
 * @param promptTarget Which field end or ends should receive timing prompts.
 */
internal fun CountdownState.withPullPromptTarget(
    pullingFromEnd: FieldEnd,
    promptTarget: PullPromptTarget,
): CountdownState {
    if (!kind.usesBetweenPointsTarget()) {
        return this
    }
    val currentTarget = betweenPointsTarget!!
    val newTarget = betweenPointsCountdownTarget(
        pullingFromEnd = pullingFromEnd,
        promptTarget = promptTarget,
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
 * @param promptTarget Which field end or ends should receive timing prompts.
 */
fun betweenPointsDisplay(
    pullingFromEnd: FieldEnd,
    sequenceStart: Long,
    now: Long,
    kind: CountdownKind = CountdownKind.BETWEEN_POINTS,
    promptTarget: PullPromptTarget = PullPromptTarget.NEAR,
): Pair<String, Duration> {
    val countdown = buildBetweenPointsCountdown(pullingFromEnd, sequenceStart, kind, promptTarget)
    return countdown.label to Duration.ofMillis((countdown.targetEpoch - now).coerceAtLeast(0L))
}

/// List normal between-points cues, including timeout-extension cues when applicable.
internal fun CountdownState.betweenPointsTimingCues(): List<TimingCue> {
    val target = betweenPointsTarget!!
    val timeoutCues = if (durationSeconds > target.baseDurationSeconds(kind)) {
        target.timeoutCueIds().map { cueId -> TimingCue(cueId, 60) }
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
        BetweenPointsCountdownTarget.BOTH -> listOf(
            TimingCue(TimingCueId.RECEIVING_TWENTY_FOR_HAND, 40),
            TimingCue(TimingCueId.RECEIVING_TEN_FOR_HAND, 30),
            TimingCue(
                id = TimingCueId.PULLING_TWENTY_TO_PULL,
                remainingSeconds = 20,
                message = "Give hand. 20 seconds to pull",
            ),
            TimingCue(TimingCueId.PULLING_TEN_TO_PULL, 10),
            TimingCue(TimingCueId.PULLING_TIME_VIOLATION, 0),
        )
        BetweenPointsCountdownTarget.NEITHER -> emptyList()
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
    val resetTarget = betweenPointsCountdownTargetForEnd(
        pullingFromEnd = pullingFromEnd,
        promptEnd = violatingTeamEnd,
    )
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
        countdown = if (pullPromptTarget.includesFieldEnd(violatingTeamEnd)) {
            this.buildTimeViolationCountdown(
                now = now,
                durationSeconds = 30,
                kind = CountdownKind.PULL_RESET,
                target = resetTarget,
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
            promptTarget = this.pullPromptTarget,
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
    val durationTarget = timeViolationTimeoutDurationTarget(team)
    val durationSeconds = when (durationTarget) {
        BetweenPointsCountdownTarget.OFFENSE_READY -> 70
        BetweenPointsCountdownTarget.PULL -> 90
        BetweenPointsCountdownTarget.BOTH,
        BetweenPointsCountdownTarget.NEITHER -> error("Team-specific time violations must resolve to one side.")
    }
    return this.copy(
        teamOne = if (team == TeamId.TEAM_ONE) this.teamOne.withAddedTimeout() else this.teamOne,
        teamTwo = if (team == TeamId.TEAM_TWO) this.teamTwo.withAddedTimeout() else this.teamTwo,
        countdown = buildTimeViolationCountdown(
            now = now,
            durationSeconds = durationSeconds,
            kind = CountdownKind.BETWEEN_POINTS,
            target = timeViolationTimeoutCountdownTarget(team),
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
 * @param target The countdown target to use for labels and cue generation.
 */
private fun GameState.buildTimeViolationCountdown(
    now: Long,
    durationSeconds: Int,
    kind: CountdownKind,
    target: BetweenPointsCountdownTarget = currentCountdownTarget(),
): CountdownState {
    return CountdownState(
        kind = kind,
        label = target.label,
        durationSeconds = durationSeconds,
        targetEpoch = now + durationSeconds * 1000L,
        betweenPointsTarget = target,
    )
}

/**
 * Return the countdown target that determines a pull-time-violation timeout reset length.
 *
 * @param team The team whose time violation caused the timeout.
 */
private fun GameState.timeViolationTimeoutDurationTarget(team: TeamId): BetweenPointsCountdownTarget {
    return when (pullPromptTarget) {
        PullPromptTarget.BOTH,
        PullPromptTarget.NEITHER -> betweenPointsCountdownTargetForEnd(
            pullingFromEnd = pullingFromEnd,
            promptEnd = fieldEndForTeam(team),
        )
        PullPromptTarget.NEAR,
        PullPromptTarget.FAR -> currentCountdownTarget()
    }
}

/**
 * Return the countdown target to show after a timeout charged for a pull time violation.
 *
 * @param team The team whose time violation caused the timeout.
 */
private fun GameState.timeViolationTimeoutCountdownTarget(team: TeamId): BetweenPointsCountdownTarget {
    return when (pullPromptTarget) {
        PullPromptTarget.BOTH -> betweenPointsCountdownTargetForEnd(
            pullingFromEnd = pullingFromEnd,
            promptEnd = fieldEndForTeam(team),
        )
        PullPromptTarget.NEITHER -> BetweenPointsCountdownTarget.NEITHER
        PullPromptTarget.NEAR,
        PullPromptTarget.FAR -> currentCountdownTarget()
    }
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
    return betweenPointsCountdownTarget(
        pullingFromEnd = pullingFromEnd,
        promptTarget = pullPromptTarget,
    )
}

/**
 * Report whether a pull-prompt target includes one field end.
 *
 * @param end The field end being checked.
 */
private fun PullPromptTarget.includesFieldEnd(end: FieldEnd): Boolean {
    return when (this) {
        PullPromptTarget.NEAR -> end == FieldEnd.NEAR
        PullPromptTarget.FAR -> end == FieldEnd.FAR
        PullPromptTarget.BOTH -> true
        PullPromptTarget.NEITHER -> false
    }
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
