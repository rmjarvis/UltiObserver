package rmjarvis.ultiobserver

import java.time.Duration
import kotlinx.serialization.Serializable

/**
 * Between-points countdown mode the observer is timing.
 *
 * @param label The short countdown label shown on the live screen.
 */
@Serializable
enum class BetweenPointsCountdownTarget(val label: String) {
    OFFENSE_READY("Signal in"),
    PULL("Pull in"),
    BOTH("Pull in"),
    NEITHER("Pull in");

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

/**
 * Confirmation details for a time violation before the observer applies it.
 *
 * @param event The event-shaped preview used to render the confirmation dialog.
 */
data class TimeViolationAssessmentPreview(
    val event: GameEvent.TimeViolationRecorded,
)

/// Rule outcome from assessing a team's pull time violation.
@Serializable
enum class TimeViolationOutcome {
    WARNING,
    TIMEOUT,
    NO_TIMEOUT,
}

private val OpeningPullTiming = PullTimingSeconds(offenseReadySeconds = 20, pullSeconds = 40)
private val ReceivingTeamWarningResetTiming = PullTimingSeconds(offenseReadySeconds = 20, pullSeconds = 50)
private val PullingTeamWarningResetTiming = PullTimingSeconds(offenseReadySeconds = 0, pullSeconds = 30)

/// Return the default pull deadlines for one between-points countdown kind.
internal fun defaultPullTimingSeconds(kind: CountdownKind, rules: GameRules): PullTimingSeconds {
    return when (kind) {
        CountdownKind.OPENING_PULL -> OpeningPullTiming
        else -> rules.standardPullTiming()
    }
}

/// Return the standard between-points pull timing configured by these game rules.
internal fun GameRules.standardPullTiming(): PullTimingSeconds {
    return PullTimingSeconds(
        offenseReadySeconds = timeBetweenPointsSeconds,
        pullSeconds = timeBetweenPointsSeconds + PULL_SECONDS_AFTER_OFFENSE_READY,
    )
}

/**
 * Build the countdown that applies between points for the configured pull prompts.
 * The exact target depends on whether the prompted end is on the pulling or receiving side.
 *
 * @param pullingFromEnd The field end the pulling team occupies.
 * @param sequenceStart The epoch millis when the between-points sequence starts.
 * @param kind The between-points countdown kind, used to distinguish normal, opening, and reset timing.
 * @param promptTarget Which field end or ends should receive timing prompts.
 * @param rules The game rules that configure normal between-points timing.
 */
internal fun buildBetweenPointsCountdown(
    pullingFromEnd: FieldEnd,
    sequenceStart: Long,
    kind: CountdownKind = CountdownKind.BETWEEN_POINTS,
    promptTarget: PullPromptTarget,
    rules: GameRules,
): CountdownState {
    require(kind.usesBetweenPointsTarget()) {
        "Countdown kind $kind does not use between-points timing."
    }
    val timing = defaultPullTimingSeconds(kind, rules)
    val target = betweenPointsCountdownTarget(
        pullingFromEnd = pullingFromEnd,
        promptTarget = promptTarget,
    )
    val durationSeconds = timing.durationSecondsFor(target)
    return CountdownState(
        kind = kind,
        label = target.label,
        durationSeconds = durationSeconds,
        targetEpoch = sequenceStart + durationSeconds * 1000L,
        betweenPointsTarget = target,
        pullTiming = timing,
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
    val timing = pullTiming!!
    val extensionSeconds = durationSeconds - timing.durationSecondsFor(currentTarget)
    val newDurationSeconds = timing.durationSecondsFor(newTarget) + extensionSeconds
    val sequenceStart = targetEpoch - durationSeconds * 1000L
    return copy(
        label = newTarget.label,
        durationSeconds = newDurationSeconds,
        targetEpoch = sequenceStart + newDurationSeconds * 1000L,
        betweenPointsTarget = newTarget,
        pullTiming = timing,
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
 * @param rules The game rules that configure normal between-points timing.
 */
fun betweenPointsDisplay(
    pullingFromEnd: FieldEnd,
    sequenceStart: Long,
    now: Long,
    kind: CountdownKind = CountdownKind.BETWEEN_POINTS,
    promptTarget: PullPromptTarget,
    rules: GameRules,
): Pair<String, Duration> {
    val countdown = buildBetweenPointsCountdown(
        pullingFromEnd = pullingFromEnd,
        sequenceStart = sequenceStart,
        kind = kind,
        promptTarget = promptTarget,
        rules = rules,
    )
    return countdown.label to Duration.ofMillis((countdown.targetEpoch - now).coerceAtLeast(0L))
}

/// List normal between-points cues, including timeout-extension cues when applicable.
internal fun CountdownState.betweenPointsTimingCues(): List<TimingCue> {
    val target = betweenPointsTarget!!
    val timing = pullTiming!!
    val preGameCues = if (kind == CountdownKind.OPENING_PULL) {
        listOf(
            TimingCue(TimingCueId.PRE_GAME_FIVE_MINUTES, durationSeconds + 5 * 60),
            TimingCue(TimingCueId.PRE_GAME_THREE_MINUTES, durationSeconds + 3 * 60),
            TimingCue(TimingCueId.PRE_GAME_ONE_MINUTE, durationSeconds + 60),
        )
    } else {
        emptyList()
    }
    val timeoutCues = if (kind != CountdownKind.PULL_RESET && durationSeconds > timing.durationSecondsFor(target)) {
        target.timeoutCueIds().map { cueId -> TimingCue(cueId, 60) }
    } else {
        emptyList()
    }
    return preGameCues + timeoutCues + when (target) {
        BetweenPointsCountdownTarget.OFFENSE_READY -> listOf(
            TimingCue(TimingCueId.RECEIVING_TWENTY_FOR_HAND, timing.remainingSecondsBeforeOffenseReady(20, target)),
            TimingCue(TimingCueId.RECEIVING_TEN_FOR_HAND, timing.remainingSecondsBeforeOffenseReady(10, target)),
            TimingCue(TimingCueId.RECEIVING_GIVE_HAND, timing.remainingSecondsBeforeOffenseReady(0, target)),
        )
        BetweenPointsCountdownTarget.PULL -> listOf(
            TimingCue(TimingCueId.PULLING_TWENTY_TO_PULL, 20),
            TimingCue(TimingCueId.PULLING_TEN_TO_PULL, 10),
            TimingCue(TimingCueId.PULLING_TIME_VIOLATION, 0),
        )
        BetweenPointsCountdownTarget.BOTH -> {
            val giveHandRemaining = timing.remainingSecondsBeforeOffenseReady(0, target)
            buildList {
                add(TimingCue(TimingCueId.RECEIVING_TWENTY_FOR_HAND, timing.remainingSecondsBeforeOffenseReady(20, target)))
                add(TimingCue(TimingCueId.RECEIVING_TEN_FOR_HAND, timing.remainingSecondsBeforeOffenseReady(10, target)))
                if (giveHandRemaining == 20) {
                    add(
                        TimingCue(
                            id = TimingCueId.PULLING_TWENTY_TO_PULL,
                            remainingSeconds = 20,
                            message = "Give hand. 20 seconds to pull",
                        )
                    )
                } else {
                    add(TimingCue(TimingCueId.RECEIVING_GIVE_HAND, giveHandRemaining))
                    add(TimingCue(TimingCueId.PULLING_TWENTY_TO_PULL, 20))
                }
                add(TimingCue(TimingCueId.PULLING_TEN_TO_PULL, 10))
                add(TimingCue(TimingCueId.PULLING_TIME_VIOLATION, 0))
            }
        }
        BetweenPointsCountdownTarget.NEITHER -> emptyList()
    }
}

/**
 * Report whether the expired-pull action surface should be available.
 *
 * @param now The epoch millis used to decide whether an active pull countdown has expired.
 */
fun GameState.hasExpiredPullActions(now: Long): Boolean {
    if (!this.phase.isBeforeLivePoint || this.pullSkippedForCurrentPoint) {
        return false
    }
    val countdown = this.countdown ?: return true
    return countdown.kind.usesBetweenPointsTarget() &&
        !countdown.isPaused() &&
        now >= countdown.targetEpoch
}

/// Report whether a pull time violation can be recorded for the current pull sequence.
fun GameState.canAssessTimeViolation(): Boolean {
    return this.pendingScoreTransition == null &&
        !this.pullSkippedForCurrentPoint &&
        (this.phase.isBeforeLivePoint || this.phase == GamePhase.LIVE_POINT)
}

/// Build the state restored by undoing automatic start point so time violation can still be assessed.
internal fun GameState.expiredPullDecisionState(): GameState {
    return this.copy(
        countdown = null,
    )
}

/**
 * Record a pull time violation for a team.
 * First violations are warnings, later violations charge a timeout when available, and no-timeout
 * violations skip the pull and show field-position guidance.
 *
 * @param team The team that violated the pull-readiness or pull-timing requirement.
 * @param now The current phone epoch millis.
 */
fun GameState.assessTimeViolation(team: TeamId, now: Long): TimeViolationAssessmentResult {
    if (!this.canAssessTimeViolation()) {
        return TimeViolationAssessmentResult(this)
    }
    val outcome = this.timeViolationOutcome(team)
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
 * Build confirmation details for a pull time violation without changing game state.
 *
 * @param team The team that would receive the time violation.
 * @return The confirmation preview, or null when the action is no longer available.
 */
fun GameState.previewTimeViolation(team: TeamId): TimeViolationAssessmentPreview? {
    if (!this.canAssessTimeViolation()) {
        return null
    }
    val outcome = timeViolationOutcome(team)
    val previewState = this.withPreviewTimeViolation(team, outcome)
    return TimeViolationAssessmentPreview(
        event = GameEvent.TimeViolationRecorded(
            state = previewState,
            team = team,
            outcome = outcome,
        ),
    )
}

/// Return the time-violation outcome for the selected team in the current state.
private fun GameState.timeViolationOutcome(team: TeamId): TimeViolationOutcome {
    return when {
        this.teamFor(team).timeViolations == 0 -> TimeViolationOutcome.WARNING
        this.timeoutsRemaining(team) > 0 -> TimeViolationOutcome.TIMEOUT
        else -> TimeViolationOutcome.NO_TIMEOUT
    }
}

/**
 * Return a state shaped like the result of a time violation for message preview only.
 *
 * @param team The team that would receive the time violation.
 * @param outcome The consequence that would be applied.
 */
private fun GameState.withPreviewTimeViolation(team: TeamId, outcome: TimeViolationOutcome): GameState {
    return this.copy(
        teamOne = if (team == TeamId.TEAM_ONE) {
            teamOne.withAddedTimeViolation().let { if (outcome == TimeViolationOutcome.TIMEOUT) it.withAddedTimeout() else it }
        } else {
            teamOne
        },
        teamTwo = if (team == TeamId.TEAM_TWO) {
            teamTwo.withAddedTimeViolation().let { if (outcome == TimeViolationOutcome.TIMEOUT) it.withAddedTimeout() else it }
        } else {
            teamTwo
        },
    )
}

/**
 * Record a team's first time violation warning and start the appropriate reset countdown.
 *
 * @param team The team receiving its warning.
 * @param now The epoch millis used to start the warning countdown.
 */
private fun GameState.recordTimeViolationWarning(team: TeamId, now: Long): GameState {
    val countdownTarget = timeViolationWarningCountdownTarget(team)
    val timing = timeViolationWarningPullTiming(team)
    return this.copy(
        teamOne = if (team == TeamId.TEAM_ONE) {
            this.teamOne.withAddedTimeViolation()
        } else {
            this.teamOne
        },
        teamTwo = if (team == TeamId.TEAM_TWO) {
            this.teamTwo.withAddedTimeViolation()
        } else {
            this.teamTwo
        },
        phase = this.pullResetPhase(),
        countdown = this.buildTimeViolationCountdown(
            now = now,
            kind = CountdownKind.PULL_RESET,
            target = countdownTarget,
            timing = timing,
        ),
    ).withEventLogEntry(
        EventLogEntry(
            timeText = formatOfficialGameTime(now, EVENT_LOG_TIME_FORMATTER),
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
    if (!this.hasExpiredPullActions(now)) {
        return this
    }
    return this.copy(
        countdown = buildBetweenPointsCountdown(
            pullingFromEnd = this.pullingFromEnd,
            sequenceStart = now,
            kind = if (this.phase == GamePhase.PRE_GAME) {
                CountdownKind.OPENING_PULL
            } else {
                CountdownKind.BETWEEN_POINTS
            },
            promptTarget = this.pullPromptTarget,
            rules = this.rules,
        ),
    ).withUndo(this, "Undo Restart countdown")
}

/**
 * Record a later time violation that charges a timeout and starts the appropriate reset countdown.
 * Timeout resets use the configured timeout duration, plus defense time when the prompted side is
 * defense.
 *
 * @param team The team being charged a timeout.
 * @param now The epoch millis used to start the reset countdown.
 */
private fun GameState.recordTimeViolationTimeout(team: TeamId, now: Long): GameState {
    val countdownTarget = timeViolationTimeoutCountdownTarget()
    return this.copy(
        teamOne = if (team == TeamId.TEAM_ONE) {
            this.teamOne.withAddedTimeViolation().withAddedTimeout()
        } else {
            this.teamOne
        },
        teamTwo = if (team == TeamId.TEAM_TWO) {
            this.teamTwo.withAddedTimeViolation().withAddedTimeout()
        } else {
            this.teamTwo
        },
        phase = this.pullResetPhase(),
        countdown = buildTimeViolationCountdown(
            now = now,
            kind = CountdownKind.BETWEEN_POINTS,
            target = countdownTarget,
            timing = rules.timeoutPullTiming(),
        ),
    ).withEventLogEntry(
        EventLogEntry(
            timeText = formatOfficialGameTime(now, EVENT_LOG_TIME_FORMATTER),
            type = EventLogType.TIME_VIOLATION,
            team = team,
            timeViolationOutcome = TimeViolationOutcome.TIMEOUT,
        )
    ).withUndo(this, "Undo Time violation timeout on ${this.teamName(team)}")
}

/// Return the pull timing used after a timeout-related reset.
private fun GameRules.timeoutPullTiming(): PullTimingSeconds {
    return PullTimingSeconds(
        offenseReadySeconds = timeoutSeconds,
        pullSeconds = timeoutSeconds + PULL_SECONDS_AFTER_OFFENSE_READY,
    )
}

/**
 * Build the countdown used after a time violation.
 *
 * @param now The epoch millis used as the countdown start.
 * @param durationSeconds The length of the reset countdown.
 * @param kind The countdown kind so warning and timeout resets can use different cue behavior.
 * @param target The countdown target to use for labels and cue generation.
 */
private fun GameState.buildTimeViolationCountdown(
    now: Long,
    kind: CountdownKind,
    target: BetweenPointsCountdownTarget,
    timing: PullTimingSeconds,
): CountdownState {
    val durationSeconds = timing.durationSecondsFor(target)
    return CountdownState(
        kind = kind,
        label = target.label,
        durationSeconds = durationSeconds,
        targetEpoch = now + durationSeconds * 1000L,
        betweenPointsTarget = target,
        pullTiming = timing,
    )
}

/// Return the countdown target to show after a pull time-violation warning.
private fun GameState.timeViolationWarningCountdownTarget(team: TeamId): BetweenPointsCountdownTarget {
    return if (team == pullingTeam) {
        if (pullPromptTarget.includesEnd(pullingFromEnd)) {
            BetweenPointsCountdownTarget.PULL
        } else {
            BetweenPointsCountdownTarget.NEITHER
        }
    } else {
        currentCountdownTarget()
    }
}

/// Return the countdown target to show after a timeout charged for a pull time violation.
private fun GameState.timeViolationTimeoutCountdownTarget(): BetweenPointsCountdownTarget = currentCountdownTarget()

/// Return the pull deadlines for a time-violation warning reset.
private fun GameState.timeViolationWarningPullTiming(team: TeamId): PullTimingSeconds {
    return if (team == pullingTeam) {
        PullingTeamWarningResetTiming
    } else {
        ReceivingTeamWarningResetTiming
    }
}

/// Return the between-points timing target for the currently prompted side of the field.
private fun GameState.currentCountdownTarget(): BetweenPointsCountdownTarget {
    return betweenPointsCountdownTarget(
        pullingFromEnd = pullingFromEnd,
        promptTarget = pullPromptTarget,
    )
}

/// Report whether a pull-prompt target includes one field end.
private fun PullPromptTarget.includesEnd(end: FieldEnd): Boolean {
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
        teamOne = if (team == TeamId.TEAM_ONE) this.teamOne.withAddedTimeViolation() else this.teamOne,
        teamTwo = if (team == TeamId.TEAM_TWO) this.teamTwo.withAddedTimeViolation() else this.teamTwo,
        phase = this.pullResetPhase(),
        countdown = null,
        pullSkippedForCurrentPoint = true,
    ).withEventLogEntry(
        EventLogEntry(
            timeText = formatOfficialGameTime(now, EVENT_LOG_TIME_FORMATTER),
            type = EventLogType.TIME_VIOLATION,
            team = team,
            timeViolationOutcome = TimeViolationOutcome.NO_TIMEOUT,
        )
    ).withUndo(this, "Undo Time violation on ${this.teamName(team)}")
}

/// Return the phase to use for pull-reset states before the next point starts.
private fun GameState.pullResetPhase(): GamePhase {
    if (this.phase != GamePhase.LIVE_POINT) {
        return this.phase
    }
    val previousPhase = this.undoEntry?.previous?.phase
    return if (previousPhase?.isBeforeLivePoint == true) previousPhase else GamePhase.BETWEEN_POINTS
}

/// Return this team state with one additional time violation.
private fun TeamState.withAddedTimeViolation(): TeamState {
    return copy(timeViolations = timeViolations + 1)
}

/// Format the compact field-button label for a team's time violations.
internal fun TeamState.timeViolationFieldActionLabel(): String {
    return countedActionLabel("Time viol.", timeViolations)
}

/// Format a time-violation event popup title.
internal fun GameEvent.TimeViolationRecorded.formatPopupTitle(): String = "Time violation"

/// Format a time-violation event message with warning, timeout, or no-timeout consequences.
internal fun GameEvent.TimeViolationRecorded.formatMessage(): RuleGuidanceMessage {
    val teamName = state.teamName(team)
    val violationLine = "This is $teamName's ${state.teamFor(team).timeViolations.ordinalWordText()} time violation."
    val consequence = when (outcome) {
        TimeViolationOutcome.WARNING -> {
            if (team == state.pullingTeam) {
                "The first time violation is a warning. $teamName now has 30 seconds to pull."
            } else {
                "The first time violation is a warning. $teamName now has 20 seconds to signal readiness."
            }
        }
        TimeViolationOutcome.TIMEOUT -> {
            val timeoutsRemainingBeforeCharge = state.timeoutsRemaining(team) + 1
            val timeoutPhrase = if (timeoutsRemainingBeforeCharge == 1) {
                "their last remaining timeout for this half"
            } else {
                "one of their $timeoutsRemainingBeforeCharge remaining timeouts available for this half"
            }
            "$teamName is required to use $timeoutPhrase. Reset pull timing to the usual timeout duration."
        }
        TimeViolationOutcome.NO_TIMEOUT -> {
            val receivingTeamName = state.teamName(state.pullingTeam.flip())
            val penalty = if (team == state.pullingTeam.flip()) {
                "No pull. $receivingTeamName starts at midpoint of their defending end zone."
            } else {
                "No pull. $receivingTeamName starts at midfield."
            }
            "$teamName has no time outs remaining for this half, so a yardage penalty is assessed. $penalty"
        }
    }
    return RuleGuidanceMessage(
        listOf(
            RuleGuidanceLine(violationLine),
            RuleGuidanceLine(""),
            RuleGuidanceLine(consequence),
        )
    )
}

/// Format only the operational consequence of a pull time violation.
internal fun GameEvent.TimeViolationRecorded.formatBriefMessage(): RuleGuidanceMessage {
    val teamName = state.teamName(team)
    val line = when (outcome) {
        TimeViolationOutcome.WARNING -> if (team == state.pullingTeam) {
            "Warning only. $teamName has 30 seconds to pull."
        } else {
            "Warning only. $teamName has 20 seconds to signal readiness."
        }
        TimeViolationOutcome.TIMEOUT -> {
            "Timeout charged to $teamName. Pull timing restarted."
        }
        TimeViolationOutcome.NO_TIMEOUT -> if (team == state.pullingTeam.flip()) {
            "No pull. $teamName starts at reverse brick."
        } else {
            "No pull. ${state.teamName(state.pullingTeam.flip())} starts at midfield."
        }
    }
    return RuleGuidanceMessage(listOf(RuleGuidanceLine(line)))
}
