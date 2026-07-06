package rmjarvis.ultiobserver

import kotlin.math.max

/**
 * Start a setup-stage game and prepare the opening-pull pre-game state.
 *
 * @receiver The setup-stage state to start.
 */
fun GameState.startGame(): GameState {
    // This is only called from a state with phase == SETUP.
    return copy(
        teamOne = teamOne.copy(name = teamOne.normalizedName(TeamId.TEAM_ONE)),
        teamTwo = teamTwo.copy(name = teamTwo.normalizedName(TeamId.TEAM_TWO)),
        pullingTeam = openingPullingTeam,
        pullingFromEnd = openingPullingFromEnd,
        topDisplayedEnd = pullPromptTarget.initialTopDisplayedEnd(),
        phase = GamePhase.PRE_GAME,
        countdown = buildBetweenPointsCountdown(
            pullingFromEnd = openingPullingFromEnd,
            sequenceStart = startEpoch,
            kind = CountdownKind.OPENING_PULL,
            promptTarget = pullPromptTarget,
        ),
    )
}

/**
 * Start a pre-pull countdown from the current field orientation.
 * This is for edge cases where an event does not automatically start the countdown directly.
 *
 * @param now The epoch millis to use as the countdown start so tests and live clock ticks are
 * deterministic.
 * @param phase The pre-pull phase to enter; `PRE_GAME` uses opening-pull timing.
 */
fun GameState.startPullSequence(
    now: Long,
    phase: GamePhase = GamePhase.BETWEEN_POINTS,
): GameState {
    val countdown = buildBetweenPointsCountdown(
        pullingFromEnd = this.pullingFromEnd,
        sequenceStart = now,
        kind = if (phase == GamePhase.PRE_GAME) {
            CountdownKind.OPENING_PULL
        } else {
            CountdownKind.BETWEEN_POINTS
        },
        promptTarget = this.pullPromptTarget,
    )
    return this.copy(
        phase = phase,
        countdown = countdown,
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
fun GameState.recordGoal(
    scoringTeam: TeamId,
    now: Long,
): GameState {
    if (this.phase == GamePhase.GAME_OVER) {
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
    val nextTeamDefendingNear = teamDefendingEnd(FieldEnd.FAR)
    val nextPullingTeam = scoringTeam
    val nextPullingFromEnd = if (nextTeamDefendingNear == nextPullingTeam) {
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
            pullingFromEnd = nextPullingFromEnd,
            phase = GamePhase.BETWEEN_POINTS,
            countdown = buildBetweenPointsCountdown(
                pullingFromEnd = nextPullingFromEnd,
                sequenceStart = now,
                promptTarget = this.pullPromptTarget,
            ),
            pullSequenceOffsidesRecorded = false,
            pullSequenceFalseStartRecorded = false,
            pullSkippedForCurrentPoint = false,
            pendingMisconductCountdown = false,
            winningScore = this.winningScore,
            pendingCapOffer = null,
            lastEvent = "${this.teamName(scoringTeam)} scored.",
        ).withEventLogEntry(
            EventLogEntry(
                timestampEpoch = now,
                type = EventLogType.GOAL,
                team = scoringTeam,
            )
        ).withUndo(this, "Undo Goal by ${this.teamName(scoringTeam)}")
        return afterGoalState.copy(
            endEpoch = now,
            phase = GamePhase.GAME_OVER,
            countdown = null,
            winningScore = gameWinningScore,
            lastEvent = "Game over.",
        ).withEventLogEntry(
            EventLogEntry(
                timestampEpoch = now,
                type = EventLogType.GAME_OVER,
            )
        ).withUndo(afterGoalState, "Undo End game")
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
        val goalState = this.copy(
            teamOne = updatedTeamOne,
            teamTwo = updatedTeamTwo,
            lastEvent = "${this.teamName(scoringTeam)} scored.",
        ).withEventLogEntry(
            EventLogEntry(
                timestampEpoch = now,
                type = EventLogType.GOAL,
                team = scoringTeam,
            )
        )
        return startHalftime(
            state = goalState,
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
        promptTarget = this.pullPromptTarget,
    )

    return this.copy(
        teamOne = updatedTeamOne,
        teamTwo = updatedTeamTwo,
        pullingTeam = nextPullingTeam,
        pullingFromEnd = nextPullingFromEnd,
        phase = GamePhase.BETWEEN_POINTS,
        countdown = countdown,
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
    ).withEventLogEntry(
        EventLogEntry(
            timestampEpoch = now,
            type = EventLogType.GOAL,
            team = scoringTeam,
        )
    ).withUndo(this, "Undo Goal by ${this.teamName(scoringTeam)}")
}
/**
 * Start halftime manually from a between-points state.
 *
 * @param now The epoch millis when the observer starts halftime, used for the halftime countdown and cap checks.
 */
fun GameState.startHalftimeNow(
    now: Long,
): GameState {
    if (this.halftimeTaken || this.phase != GamePhase.BETWEEN_POINTS) {
        return this
    }
    return startHalftime(
        state = this,
        teamOne = this.teamOne,
        teamTwo = this.teamTwo,
        existingCapOffer = this.pendingCapOffer,
        now = now,
        undoPrevious = this,
        undoLabel = "Undo Start halftime",
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
    state: GameState,
    teamOne: TeamState,
    teamTwo: TeamState,
    existingCapOffer: CapType?,
    now: Long,
    undoPrevious: GameState,
    undoLabel: String,
): GameState {
    val secondHalfPullingTeam = state.openingPullingTeam.flip()
    val secondHalfPullingFromEnd = state.openingPullingFromEnd
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
        phase = GamePhase.HALFTIME,
        countdown = halftimeCountdown,
        pullSequenceOffsidesRecorded = false,
        pullSequenceFalseStartRecorded = false,
        pullSkippedForCurrentPoint = false,
        pendingMisconductCountdown = false,
        halftimeTaken = true,
        pendingCapOffer = pendingCapOffer,
        lastEvent = "Halftime.",
    ).withEventLogEntry(
        EventLogEntry(
            timestampEpoch = now,
            type = EventLogType.HALFTIME,
        )
    ).withUndo(undoPrevious, undoLabel)
}
/**
 * End the current game immediately from any non-finished state.
 *
 * @param now The epoch millis to store as the actual game end time.
 */
fun GameState.endGameNow(
    now: Long,
): GameState {
    if (this.phase == GamePhase.GAME_OVER) {
        return this
    }
    return this.copy(
        endEpoch = now,
        phase = GamePhase.GAME_OVER,
        countdown = null,
        pendingMisconductCountdown = false,
        pendingCapOffer = null,
        lastEvent = "Game over.",
    ).withEventLogEntry(
        EventLogEntry(
            timestampEpoch = now,
            type = EventLogType.GAME_OVER,
        )
    ).withUndo(this, "Undo End game")
}
/// Mark the pull as complete and enter live-point play.
fun GameState.beginLivePoint(now: Long): GameState {
    val firstPullTimestamp = this.firstPullLogTimestamp(now)
    val firstPullEntry = if (this.teamOne.score == 0 && this.teamTwo.score == 0) {
        listOf(
            EventLogEntry(
                timestampEpoch = firstPullTimestamp,
                type = EventLogType.FIRST_PULL,
                team = this.pullingTeam,
            )
        )
    } else {
        emptyList()
    }
    return this.copy(
        phase = GamePhase.LIVE_POINT,
        countdown = null,
        pullSequenceOffsidesRecorded = false,
        pullSequenceFalseStartRecorded = false,
        pullSkippedForCurrentPoint = false,
        pendingMisconductCountdown = false,
        lastEvent = "Point is live.",
    ).withEventLogEntries(firstPullEntry).withUndo(this, "Undo Start point")
}
/**
 * Record a goal while treating a between-points state as implicitly started.
 * If a goal is recorded before the point was explicitly started, first start the point, then record the goal.
 *
 * @param scoringTeam The team that scored the point.
 * @param now The epoch millis of the goal for timers, cap checks, and game-end bookkeeping.
 */
fun GameState.recordGoalFromCurrentState(
    scoringTeam: TeamId,
    now: Long,
): GameState {
    val livePointState = if (this.phase.isBeforeLivePoint) {
        this.beginLivePoint(now)
    } else {
        this
    }
    return livePointState.recordGoal(scoringTeam, now)
}
/// Clear a timeout or similar in-point interruption countdown and resume normal live-point play.
fun GameState.continueLivePoint(): GameState {
    return this.copy(
        phase = GamePhase.LIVE_POINT,
        countdown = null,
        pendingMisconductCountdown = false,
        lastEvent = "Point continued.",
    )
}
/**
 * Report whether halftime has elapsed and the live screen can show `Start point`.
 *
 * @param now The current epoch millis used to compare against halftime's target time.
 */
internal fun GameState.halftimeTransitionReady(now: Long): Boolean {
    val countdown = countdown ?: return false
    return phase == GamePhase.HALFTIME &&
        countdown.kind == CountdownKind.HALFTIME &&
        now >= countdown.targetEpoch
}

/**
 * Apply automatic timer expirations that do not require an observer button press.
 *
 * Note -- don't apply any automatic transitions when there is a redo chain attached to
 * the current state to avoid clearing the redo path.  Otherwise, backing up into a state
 * with an expired countdown can immediately move forward, which feels awkward to the
 * user, but more importantly severs the redo chain.  So the user can't replay the path
 * back to the present anymore.
 *
 * @param now The current epoch millis so tests and background ticks can drive deterministic transitions.
 * @param showDefenseCountdowns Whether timeout offense-set expirations wait for defense.
 */
fun GameState.applyExpiredCountdownTransitions(
    now: Long,
    showDefenseCountdowns: Boolean,
): GameState {
    val countdown = this.countdown ?: return this
    if (countdown.isPaused()) {
        return this
    }
    if (now < countdown.targetEpoch) {
        return this
    }
    if (this.redoEntry != null) {
        return this
    }
    return when {
        this.phase.isBeforeLivePoint && countdown.kind.usesBetweenPointsTarget() -> {
            this.automaticLivePointState(now)
        }
        this.phase.isBeforeLivePoint &&
            countdown.kind == CountdownKind.MISCONDUCT_BETWEEN_POINTS -> {
            if (showDefenseCountdowns) this else this.automaticLivePointState(now)
        }
        this.phase.isBeforeLivePoint && countdown.kind == CountdownKind.DEFENSE_CHECK -> {
            this.automaticLivePointState(now)
        }
        this.phase == GamePhase.LIVE_POINT && countdown.kind == CountdownKind.TIME_OUT -> {
            if (showDefenseCountdowns) this else this.automaticContinueLivePointState()
        }
        this.phase == GamePhase.LIVE_POINT && countdown.kind == CountdownKind.DEFENSE_CHECK -> {
            this.automaticContinueLivePointState()
        }
        this.phase == GamePhase.HALFTIME && countdown.kind == CountdownKind.HALFTIME -> {
            val betweenPointsState = this.copy(
                phase = GamePhase.BETWEEN_POINTS,
                countdown = buildBetweenPointsCountdown(
                    pullingFromEnd = this.pullingFromEnd,
                    sequenceStart = countdown.targetEpoch,
                    promptTarget = this.pullPromptTarget,
                ),
                pullSkippedForCurrentPoint = false,
                pendingMisconductCountdown = false,
            )
            betweenPointsState.applyExpiredCountdownTransitions(
                now = now,
                showDefenseCountdowns = showDefenseCountdowns,
            )
        }
        else -> error("Countdown ${countdown.kind} is not valid while game phase is ${this.phase}.")
    }
}
/// Restore the state saved by the most recent undo-backed user action.
fun GameState.undoLastAction(): GameState {
    val entry = this.undoEntry ?: return this
    return entry.previous.copy(
        redoEntry = this,
    )
}
/// Reapply the state that was just undone, if redo is still available.
fun GameState.redoLastAction(): GameState {
    return this.redoEntry ?: this
}
/// Enter live-point play from an expired countdown while preserving undo back to the expired-pull actions.
private fun GameState.automaticLivePointState(now: Long): GameState {
    val previous = this.expiredPullDecisionState()
    val firstPullTimestamp = this.firstPullLogTimestamp(now)
    val firstPullEntry = if (this.teamOne.score == 0 && this.teamTwo.score == 0) {
        listOf(
            EventLogEntry(
                timestampEpoch = firstPullTimestamp,
                type = EventLogType.FIRST_PULL,
                team = this.pullingTeam,
            )
        )
    } else {
        emptyList()
    }
    return copy(
        phase = GamePhase.LIVE_POINT,
        countdown = null,
        pullSequenceOffsidesRecorded = false,
        pullSequenceFalseStartRecorded = false,
        pullSkippedForCurrentPoint = false,
        pendingMisconductCountdown = false,
        lastEvent = "Point is live.",
    ).withEventLogEntries(firstPullEntry).withUndo(previous, "Undo Start point")
}
/// Clear an expired in-point countdown without replacing the undo entry for the action that started it.
private fun GameState.automaticContinueLivePointState(): GameState {
    return copy(
        phase = GamePhase.LIVE_POINT,
        countdown = null,
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
internal fun GameState.withUndo(previous: GameState, label: String): GameState {
    return copy(
        undoEntry = UndoEntry(label = label, previous = previous.copy(redoEntry = null)),
        redoEntry = null,
    )
}
