package rmjarvis.ultiobserver

import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import kotlin.math.abs
import kotlin.math.max

/**
 * Build the initial live-game state from the completed setup form.
 *
 * @param setup The pregame setup choices that define teams, rules, start time, and opening pull orientation.
 */
fun createLiveGameState(setup: GameSetupState): LiveGameState {
    val nearAttackingTeam = if (setup.pullingFromEnd == FieldEnd.FAR) {
        setup.pullingTeam
    } else {
        setup.pullingTeam.flip()
    }
    val startEpoch = epochTimestamp(setup.startDate, setup.startTime, setup.timeZone)
    val initialCountdown = buildBetweenPointsCountdown(
        pullingFromEnd = setup.pullingFromEnd,
        sequenceStart = startEpoch,
        kind = CountdownKind.OPENING_PULL,
    )

    return LiveGameState(
        startDate = setup.startDate,
        startTime = setup.startTime,
        timeZone = setup.timeZone,
        startEpoch = startEpoch,
        rules = setup.rules,
        teamOne = TeamLiveState(
            name = setup.teamOne.name.ifBlank { "Team 1" },
            color = setup.teamOne.color,
        ),
        teamTwo = TeamLiveState(
            name = setup.teamTwo.name.ifBlank { "Team 2" },
            color = setup.teamTwo.color,
        ),
        priorCards = setup.priorCards,
        nearAttackingTeam = nearAttackingTeam,
        pullingTeam = setup.pullingTeam,
        pullingFromEnd = setup.pullingFromEnd,
        openingPullingTeam = setup.pullingTeam,
        openingPullingFromEnd = setup.pullingFromEnd,
        phase = LivePhase.BETWEEN_POINTS,
        countdown = initialCountdown,
    )
}
/**
 * Start a between-points pull countdown from the current field orientation.
 * This is for edge cases where an event does not automatically start the countdown directly.
 *
 * @param now The epoch millis to use as the countdown start so tests and live clock ticks are deterministic.
 */
fun LiveGameState.startPullSequence(
    now: Long,
): LiveGameState {
    val countdown = buildBetweenPointsCountdown(
        pullingFromEnd = this.pullingFromEnd,
        sequenceStart = now,
    )
    return this.copy(
        phase = LivePhase.BETWEEN_POINTS,
        countdown = countdown,
        pullCountdownExpired = false,
        pullSequenceOffsidesRecorded = false,
        pullSequenceFalseStartRecorded = false,
        pullSkippedForCurrentPoint = false,
        pendingMisconductCountdown = false,
        lastEvent = "Pull sequence started.",
    )
}
/**
 * Record a scored goal and advances the game to halftime, game over, or the next pull sequence.
 *
 * @param scoringTeam The team that scored the just-finished point.
 * @param now The epoch millis of the goal, used for countdown starts, cap checks, and game end time.
 */
fun LiveGameState.recordGoal(
    scoringTeam: TeamId,
    now: Long,
): LiveGameState {
    if (this.phase == LivePhase.GAME_OVER) {
        return this
    }

    val updatedTeamOne = if (scoringTeam == TeamId.TEAM_ONE) {
        this.teamOne.copy(score = this.teamOne.score + 1)
    } else {
        this.teamOne
    }
    val updatedTeamTwo = if (scoringTeam == TeamId.TEAM_TWO) {
        this.teamTwo.copy(score = this.teamTwo.score + 1)
    } else {
        this.teamTwo
    }
    val nextNearAttackingTeam = this.nearAttackingTeam.flip()
    val nextPullingTeam = scoringTeam
    val nextPullingFromEnd = if (scoringTeam == this.nearAttackingTeam) {
        FieldEnd.NEAR
    } else {
        FieldEnd.FAR
    }
    // Do this check every time, because if the user changes the rules.gameTo, we naturally
    // update to the updated rules.  However, if the soft cap has been applied, that takes
    // precedence.
    val gameWinningScore = this.winningScore ?: this.rules.gameTo
    val gameOver = max(updatedTeamOne.score, updatedTeamTwo.score) >= gameWinningScore

    if (gameOver) {
        val afterGoalState = this.copy(
            teamOne = updatedTeamOne,
            teamTwo = updatedTeamTwo,
            pullingTeam = nextPullingTeam,
            nearAttackingTeam = nextNearAttackingTeam,
            pullingFromEnd = nextPullingFromEnd,
            phase = LivePhase.BETWEEN_POINTS,
            countdown = buildBetweenPointsCountdown(
                pullingFromEnd = nextPullingFromEnd,
                sequenceStart = now,
            ),
            pullCountdownExpired = false,
            pullSequenceOffsidesRecorded = false,
            pullSequenceFalseStartRecorded = false,
            pullSkippedForCurrentPoint = false,
            pendingMisconductCountdown = false,
            winningScore = this.winningScore,
            pendingCapOffer = null,
            lastEvent = "${this.teamName(scoringTeam)} scored.",
        ).withUndo(this, "Undo Goal by ${this.teamName(scoringTeam)}")
        return afterGoalState.copy(
            endEpoch = now,
            phase = LivePhase.GAME_OVER,
            countdown = null,
            pullCountdownExpired = false,
            winningScore = gameWinningScore,
            lastEvent = "Game over.",
        ).withUndo(afterGoalState, "Undo End Game")
    }

    // Caps are checked before halftime so hard cap takes precedence over soft, and soft over half.
    val pendingCapOffer = when {
        this.hardCapReached(now) -> CapType.HARD
        this.softCapReached(now) -> CapType.SOFT
        this.halfCapReached(
            teamOneScore = updatedTeamOne.score,
            teamTwoScore = updatedTeamTwo.score,
            now = now,
        ) -> CapType.HALF
        else -> null
    }

    val halftimeScore = this.halftimeTargetScore ?: halftimeScore(this.rules)
    val halftimeReached = !this.halftimeTaken &&
        max(updatedTeamOne.score, updatedTeamTwo.score) >= halftimeScore

    // A point-end cap offer is still only pending; the observer can apply or defer it.
    // Start halftime and surface that offer from the halftime state.
    if (halftimeReached) {
        return startHalftime(
            state = this,
            teamOne = updatedTeamOne,
            teamTwo = updatedTeamTwo,
            existingCapOffer = pendingCapOffer,
            now = now,
            undoPrevious = this,
            undoLabel = "Undo Goal by ${this.teamName(scoringTeam)}",
        )
    }

    // Regular point -- not half, and not game over.
    val countdown = buildBetweenPointsCountdown(
        pullingFromEnd = nextPullingFromEnd,
        sequenceStart = now,
    )

    return this.copy(
        teamOne = updatedTeamOne,
        teamTwo = updatedTeamTwo,
        pullingTeam = nextPullingTeam,
        nearAttackingTeam = nextNearAttackingTeam,
        pullingFromEnd = nextPullingFromEnd,
        phase = LivePhase.BETWEEN_POINTS,
        countdown = countdown,
        pullCountdownExpired = false,
        pullSequenceOffsidesRecorded = false,
        pullSequenceFalseStartRecorded = false,
        pullSkippedForCurrentPoint = false,
        pendingMisconductCountdown = false,
        halftimeTaken = this.halftimeTaken,
        halftimeTargetScore = this.halftimeTargetScore,
        winningScore = this.winningScore,
        halfCapApplied = this.halfCapApplied,
        softCapApplied = this.softCapApplied,
        hardCapApplied = this.hardCapApplied,
        pendingCapOffer = pendingCapOffer,
        lastEvent = "${this.teamName(scoringTeam)} scored.",
    ).withUndo(this, "Undo Goal by ${this.teamName(scoringTeam)}")
}
/**
 * Start halftime manually from a between-points state.
 *
 * @param now The epoch millis when the observer starts halftime, used for the halftime countdown and cap checks.
 */
fun LiveGameState.startHalftimeNow(
    now: Long,
): LiveGameState {
    if (this.halftimeTaken || this.phase != LivePhase.BETWEEN_POINTS) {
        return this
    }
    return startHalftime(
        state = this,
        teamOne = this.teamOne,
        teamTwo = this.teamTwo,
        existingCapOffer = this.pendingCapOffer,
        now = now,
        undoPrevious = this,
        undoLabel = "Undo Start Halftime",
    )
}
/**
 * Move a game into halftime while preserving timeout carryover, second-half pull orientation, and undo context.
 *
 * @param state The pre-halftime state to transform.
 * @param teamOne Team one state after any triggering score has already been applied.
 * @param teamTwo Team two state after any triggering score has already been applied.
 * @param existingCapOffer A cap offer that was already pending before halftime started, if any.
 * @param now The epoch millis when halftime begins.
 * @param undoPrevious The state that undo should restore.
 * @param undoLabel The user-facing undo label for the action that started halftime.
 */
private fun startHalftime(
    state: LiveGameState,
    teamOne: TeamLiveState,
    teamTwo: TeamLiveState,
    existingCapOffer: CapType?,
    now: Long,
    undoPrevious: LiveGameState,
    undoLabel: String,
): LiveGameState {
    val secondHalfPullingTeam = state.openingPullingTeam.flip()
    val secondHalfPullingFromEnd = state.openingPullingFromEnd
    val secondHalfNearAttackingTeam = if (secondHalfPullingFromEnd == FieldEnd.FAR) {
        secondHalfPullingTeam
    } else {
        secondHalfPullingTeam.flip()
    }
    val halftimeCountdown = buildHalftimeCountdown(
        halftimeMinutes = state.rules.halftimeMinutes,
        sequenceStart = now,
    )
    val halftimeEnd = now + state.rules.halftimeMinutes * 60_000L
    val hardCapTime = state.capEpoch(CapType.HARD)
    val softCapTime = state.capEpoch(CapType.SOFT)
    // Preserve an already-pending soft/hard cap. Otherwise, catch caps that became
    // due just before a manual halftime start or that are scheduled during halftime.
    val pendingCapOffer = existingCapOffer.takeIf { it == CapType.SOFT || it == CapType.HARD }
        ?: when {
            state.hardCapRelevant() && hardCapTime < halftimeEnd -> CapType.HARD
            state.softCapRelevant() && softCapTime < halftimeEnd -> CapType.SOFT
            else -> null
        }

    return state.copy(
        teamOne = teamOne.copy(
            firstHalfTimeoutsUsed = state.teamOne.timeoutsUsedThisHalf,
            timeoutsUsedThisHalf = 0,
        ),
        teamTwo = teamTwo.copy(
            firstHalfTimeoutsUsed = state.teamTwo.timeoutsUsedThisHalf,
            timeoutsUsedThisHalf = 0,
        ),
        pullingTeam = secondHalfPullingTeam,
        pullingFromEnd = secondHalfPullingFromEnd,
        nearAttackingTeam = secondHalfNearAttackingTeam,
        phase = LivePhase.HALFTIME,
        countdown = halftimeCountdown,
        pullCountdownExpired = false,
        pullSequenceOffsidesRecorded = false,
        pullSequenceFalseStartRecorded = false,
        pullSkippedForCurrentPoint = false,
        pendingMisconductCountdown = false,
        halftimeTaken = true,
        pendingCapOffer = pendingCapOffer,
        lastEvent = "Halftime.",
    ).withUndo(undoPrevious, undoLabel)
}
/**
 * End the current game immediately from any non-finished live state.
 *
 * @param now The epoch millis to store as the actual game end time.
 */
fun LiveGameState.endGameNow(
    now: Long,
): LiveGameState {
    if (this.phase == LivePhase.GAME_OVER) {
        return this
    }
    return this.copy(
        endEpoch = now,
        phase = LivePhase.GAME_OVER,
        countdown = null,
        pullCountdownExpired = false,
        pendingMisconductCountdown = false,
        pendingCapOffer = null,
        lastEvent = "Game over.",
    ).withUndo(this, "Undo End Game")
}
/// Mark the pull as complete and enter live-point play.
fun LiveGameState.beginLivePoint(): LiveGameState {
    return this.copy(
        phase = LivePhase.LIVE_POINT,
        countdown = null,
        pullCountdownExpired = false,
        pullSequenceOffsidesRecorded = false,
        pullSequenceFalseStartRecorded = false,
        pullSkippedForCurrentPoint = false,
        pendingMisconductCountdown = false,
        lastEvent = "Point is live.",
    ).withUndo(this, "Undo Start Point")
}
/**
 * Record a goal while treating a between-points state as implicitly started.
 * If a goal is recorded before the point was explicitly started, first start the point, then record the goal.
 *
 * @param scoringTeam The team that scored the point.
 * @param now The epoch millis of the goal for timers, cap checks, and game-end bookkeeping.
 */
fun LiveGameState.recordGoalFromCurrentState(
    scoringTeam: TeamId,
    now: Long,
): LiveGameState {
    val livePointState = if (this.phase == LivePhase.BETWEEN_POINTS) {
        this.beginLivePoint()
    } else {
        this
    }
    return livePointState.recordGoal(scoringTeam, now)
}
/// Clear a timeout or similar in-point interruption countdown and resume normal live-point play.
fun LiveGameState.continueLivePoint(): LiveGameState {
    return this.copy(
        phase = LivePhase.LIVE_POINT,
        countdown = null,
        pendingMisconductCountdown = false,
        lastEvent = "Point continued.",
    )
}
/// Offer a live-point misconduct countdown without starting it before the observer is ready.
fun LiveGameState.withPendingMisconductCountdown(): LiveGameState {
    if (phase != LivePhase.LIVE_POINT) {
        return this
    }
    return copy(
        countdown = null,
        pendingMisconductCountdown = true,
    )
}
/**
 * Start the 30-second offense-set countdown after an in-point misconduct penalty.
 *
 * @param now The epoch millis to use as the countdown start.
 */
fun LiveGameState.startMisconductCountdown(now: Long): LiveGameState {
    if (phase != LivePhase.LIVE_POINT || !pendingMisconductCountdown) {
        return this
    }
    return copy(
        countdown = CountdownState(
            kind = CountdownKind.TIME_OUT,
            label = "Offense set in",
            durationSeconds = 30,
            targetEpoch = now + 30_000L,
        ),
        pendingMisconductCountdown = false,
        lastEvent = "Misconduct countdown started.",
    )
}
/**
 * Report whether the between-points misconduct countdown can switch to defense check-in timing.
 * The early-set action matters only while defense can still get the fixed 100-second deadline.
 *
 * @param now The current epoch millis, used to hide the early-set option once only the normal hand count remains.
 */
fun LiveGameState.canReportMisconductOffenseSet(now: Long): Boolean {
    val countdown = countdown ?: return false
    return phase == LivePhase.BETWEEN_POINTS &&
        countdown.kind == CountdownKind.MISCONDUCT_BETWEEN_POINTS &&
        now < countdown.targetEpoch - 10_000L
}
/**
 * Switch a between-points misconduct countdown to the defense check-in window after offense sets early.
 * The resulting deadline is the later of the fixed 100-second deadline or 20 seconds after offense sets.
 *
 * @param now The epoch millis when offense is reported set, used to compute the later of the rule deadlines.
 */
fun LiveGameState.reportMisconductOffenseSet(now: Long): LiveGameState {
    val countdown = countdown ?: return this
    if (!canReportMisconductOffenseSet(now)) {
        return this
    }
    val targetEpoch = max(
        countdown.targetEpoch + 10_000L,
        now + 20_000L,
    )
    return copy(
        countdown = CountdownState(
            kind = CountdownKind.MISCONDUCT_DEFENSE_CHECK,
            label = "Defense check in",
            durationSeconds = ((targetEpoch - now) / 1000L).toInt(),
            targetEpoch = targetEpoch,
        ),
        lastEvent = "Offense set after misconduct penalty.",
    )
}
/**
 * Apply automatic timer expirations that do not require an observer button press.
 *
 * @param now The current epoch millis so tests and background ticks can drive deterministic transitions.
 */
fun LiveGameState.advanceGameClock(now: Long): LiveGameState {
    val countdown = this.countdown ?: return this
    if (now < countdown.targetEpoch) {
        return this
    }
    return when {
        this.phase == LivePhase.BETWEEN_POINTS && countdown.kind.usesBetweenPointsTarget() -> {
            this.automaticLivePointState()
        }
        this.phase == LivePhase.BETWEEN_POINTS && countdown.kind == CountdownKind.MISCONDUCT_BETWEEN_POINTS -> {
            this.automaticLivePointState()
        }
        this.phase == LivePhase.BETWEEN_POINTS && countdown.kind == CountdownKind.MISCONDUCT_DEFENSE_CHECK -> {
            this.automaticLivePointState()
        }
        this.phase == LivePhase.LIVE_POINT && countdown.kind == CountdownKind.TIME_OUT -> {
            this.automaticContinueLivePointState()
        }
        this.phase == LivePhase.HALFTIME && countdown.kind == CountdownKind.HALFTIME -> {
            val betweenPointsState = this.copy(
                phase = LivePhase.BETWEEN_POINTS,
                countdown = buildBetweenPointsCountdown(
                    pullingFromEnd = this.pullingFromEnd,
                    sequenceStart = countdown.targetEpoch,
                ),
                pullCountdownExpired = false,
                pullSkippedForCurrentPoint = false,
                pendingMisconductCountdown = false,
            )
            betweenPointsState.advanceGameClock(now)
        }
        else -> error("Countdown ${countdown.kind} is not valid while game phase is ${this.phase}.")
    }
}
/**
 * Add or subtract seconds from the active countdown.
 *
 * @param seconds The signed countdown adjustment; negative values move the target earlier.
 */
fun LiveGameState.addTimeToCountdown(seconds: Int): LiveGameState {
    val countdown = this.countdown ?: return this
    val sign = if (seconds < 0) "-" else ""
    val absoluteSeconds = abs(seconds)
    return this.copy(
        countdown = countdown.copy(targetEpoch = countdown.targetEpoch + seconds * 1000L),
        lastEvent = "Adjusted timer by $sign${absoluteSeconds / 60}:${(absoluteSeconds % 60).toString().padStart(2, '0')}.",
    )
}
/**
 * Replace the recorded score as a manual correction.
 *
 * @param teamOneScore The corrected team-one score, clamped at zero.
 * @param teamTwoScore The corrected team-two score, clamped at zero.
 */
fun LiveGameState.adjustScore(teamOneScore: Int, teamTwoScore: Int): LiveGameState {
    return this.copy(
        teamOne = this.teamOne.copy(score = teamOneScore.coerceAtLeast(0)),
        teamTwo = this.teamTwo.copy(score = teamTwoScore.coerceAtLeast(0)),
        lastEvent = "Score adjusted.",
    ).withUndo(this, "Undo Score Adjustment")
}
/// Restore the state saved by the most recent undo-backed user action.
fun LiveGameState.undoLastAction(): LiveGameState {
    val entry = this.undoEntry ?: return this
    return entry.previous.copy(
        redoEntry = this,
    )
}
/// Reapply the state that was just undone, if redo is still available.
fun LiveGameState.redoLastAction(): LiveGameState {
    return this.redoEntry ?: this
}
/**
 * Apply an edited setup form to an existing live game.
 * This is the model-side return path from the live-game setup editor.
 *
 * @param existing The live state currently being edited.
 * @param setup The setup values returned by the update-game form.
 * @param now The epoch millis for rebuilding the opening pull countdown when pre-play orientation changes.
 */
fun applySetupToLiveGame(
    existing: LiveGameState,
    setup: GameSetupState,
    now: Long,
): LiveGameState {
    val openingNearAttackingTeam = if (setup.pullingFromEnd == FieldEnd.FAR) {
        setup.pullingTeam
    } else {
        setup.pullingTeam.flip()
    }
    val shouldResyncPullState = existing.teamOne.score == 0 &&
        existing.teamTwo.score == 0 &&
        existing.phase != LivePhase.LIVE_POINT

    val base = existing.copy(
        startDate = setup.startDate,
        startTime = setup.startTime,
        timeZone = setup.timeZone,
        startEpoch = epochTimestamp(setup.startDate, setup.startTime, setup.timeZone),
        rules = setup.rules,
        teamOne = existing.teamOne.copy(
            name = setup.teamOne.name.ifBlank { "Team 1" },
            color = setup.teamOne.color,
        ),
        teamTwo = existing.teamTwo.copy(
            name = setup.teamTwo.name.ifBlank { "Team 2" },
            color = setup.teamTwo.color,
        ),
        priorCards = setup.priorCards,
        teamOnePlayerCards = existing.teamOnePlayerCards,
        teamTwoPlayerCards = existing.teamTwoPlayerCards,
        openingPullingTeam = setup.pullingTeam,
        openingPullingFromEnd = setup.pullingFromEnd,
    )

    val updatedState = if (shouldResyncPullState) {
        base.copy(
            nearAttackingTeam = openingNearAttackingTeam,
            pullingTeam = setup.pullingTeam,
            pullingFromEnd = setup.pullingFromEnd,
        ).startPullSequence(now)
    } else {
        base
    }
    return updatedState.withUndo(existing, "Undo Update Game Setup")
}
/// Extract only the setup-screen fields from live state so the setup editor can reopen prefilled.
fun LiveGameState.toSetupState(): GameSetupState {
    return GameSetupState(
        startDate = startDate,
        startTime = startTime,
        timeZone = timeZone,
        rules = rules,
        teamOne = TeamSetup(
            name = teamOne.name,
            color = teamOne.color,
        ),
        teamTwo = TeamSetup(
            name = teamTwo.name,
            color = teamTwo.color,
        ),
        priorCards = priorCards,
        pullingTeam = openingPullingTeam,
        pullingFromEnd = openingPullingFromEnd,
    )
}
/// Enter live-point play from an expired countdown while preserving undo back to the expired-pull actions.
private fun LiveGameState.automaticLivePointState(): LiveGameState {
    val previous = this.expiredPullDecisionState()
    return copy(
        phase = LivePhase.LIVE_POINT,
        countdown = null,
        pullCountdownExpired = false,
        pullSequenceOffsidesRecorded = false,
        pullSequenceFalseStartRecorded = false,
        pullSkippedForCurrentPoint = false,
        pendingMisconductCountdown = false,
        lastEvent = "Point is live.",
    ).withUndo(previous, "Undo Start Point")
}
/// Clear an expired in-point countdown without replacing the undo entry for the action that started it.
private fun LiveGameState.automaticContinueLivePointState(): LiveGameState {
    return copy(
        phase = LivePhase.LIVE_POINT,
        countdown = null,
        pullCountdownExpired = false,
        pendingMisconductCountdown = false,
        lastEvent = "Point continued.",
    )
}
/**
 * Attach undo metadata to a state returned by a user-initiated action.
 * Use this for undoable user actions; most user-initiated state changes should end by
 * calling `.withUndo(previousState, label)`.
 *
 * @param previous The state to restore if the observer taps undo.
 * @param label The user-facing undo label that describes the action being reversed.
 */
internal fun LiveGameState.withUndo(previous: LiveGameState, label: String): LiveGameState {
    return copy(
        undoEntry = UndoEntry(label = label, previous = previous.copy(redoEntry = null)),
        redoEntry = null,
    )
}
/**
 * Convert local game start date/time fields into epoch millis.
 *
 * @param date The local calendar date selected in setup.
 * @param time The local clock time selected in setup.
 * @param timeZone The zone that gives the local date/time its real instant.
 */
internal fun epochTimestamp(date: LocalDate, time: LocalTime, timeZone: ZoneId): Long {
    return LocalDateTime.of(date, time)
        .atZone(timeZone)
        .toInstant()
        .toEpochMilli()
}
/**
 * Convert epoch millis into a local date-time in the game's time zone.
 *
 * @param epoch The epoch millis to convert.
 * @param timeZone The zone used to present the instant as local game time.
 */
internal fun localDateTimeFromEpoch(epoch: Long, timeZone: ZoneId): LocalDateTime {
    return LocalDateTime.ofInstant(
        java.time.Instant.ofEpochMilli(epoch),
        timeZone,
    )
}
/**
 * Convert epoch millis into a local clock time in the game's time zone.
 *
 * @param epoch The epoch millis to convert.
 * @param timeZone The zone used to present the instant as local game time.
 */
internal fun localTimeFromEpoch(epoch: Long, timeZone: ZoneId): LocalTime {
    return localDateTimeFromEpoch(epoch, timeZone).toLocalTime()
}
