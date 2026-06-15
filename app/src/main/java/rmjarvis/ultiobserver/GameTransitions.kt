package rmjarvis.ultiobserver

import kotlin.math.max

/**
 * Build the initial live-game state from the completed setup form.
 *
 * @param setup The pregame setup choices that define teams, rules, start time, and opening pull orientation.
 */
fun createLiveGameState(setup: GameSetupState): GameState {
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

    return GameState(
        startDate = setup.startDate,
        startTime = setup.startTime,
        timeZone = setup.timeZone,
        startEpoch = startEpoch,
        tournamentName = setup.tournamentName,
        division = setup.division,
        gameContext = setup.gameContext,
        nearEndName = setup.nearEndName,
        farEndName = setup.farEndName,
        rules = setup.rules,
        teamOne = TeamLiveState(
            name = setup.teamOne.name.ifBlank { "Team 1" },
            color = setup.teamOne.color,
            customColorArgb = setup.teamOne.customColorArgb,
            coaches = setup.teamOne.coaches,
            fieldCaptains = setup.teamOne.fieldCaptains,
            spiritCaptains = setup.teamOne.spiritCaptains,
        ),
        teamTwo = TeamLiveState(
            name = setup.teamTwo.name.ifBlank { "Team 2" },
            color = setup.teamTwo.color,
            customColorArgb = setup.teamTwo.customColorArgb,
            coaches = setup.teamTwo.coaches,
            fieldCaptains = setup.teamTwo.fieldCaptains,
            spiritCaptains = setup.teamTwo.spiritCaptains,
        ),
        priorCards = setup.priorCards,
        nearAttackingTeam = nearAttackingTeam,
        pullingTeam = setup.pullingTeam,
        pullingFromEnd = setup.pullingFromEnd,
        pullPromptTarget = setup.pullPromptTarget,
        openingPullingTeam = setup.pullingTeam,
        openingPullingFromEnd = setup.pullingFromEnd,
        phase = GamePhase.BETWEEN_POINTS,
        countdown = initialCountdown,
    )
}
/**
 * Start a between-points pull countdown from the current field orientation.
 * This is for edge cases where an event does not automatically start the countdown directly.
 *
 * @param now The epoch millis to use as the countdown start so tests and live clock ticks are deterministic.
 */
fun GameState.startPullSequence(
    now: Long,
): GameState {
    val countdown = buildBetweenPointsCountdown(
        pullingFromEnd = this.pullingFromEnd,
        sequenceStart = now,
    )
    return this.copy(
        phase = GamePhase.BETWEEN_POINTS,
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
            phase = GamePhase.BETWEEN_POINTS,
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
            pullCountdownExpired = false,
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
    )

    return this.copy(
        teamOne = updatedTeamOne,
        teamTwo = updatedTeamTwo,
        pullingTeam = nextPullingTeam,
        nearAttackingTeam = nextNearAttackingTeam,
        pullingFromEnd = nextPullingFromEnd,
        phase = GamePhase.BETWEEN_POINTS,
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
    teamOne: TeamLiveState,
    teamTwo: TeamLiveState,
    existingCapOffer: CapType?,
    now: Long,
    undoPrevious: GameState,
    undoLabel: String,
): GameState {
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
        phase = GamePhase.HALFTIME,
        countdown = halftimeCountdown,
        pullCountdownExpired = false,
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
 * End the current game immediately from any non-finished live state.
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
        pullCountdownExpired = false,
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
    val firstPullEntry = if (this.eventLog.isEmpty()) {
        listOf(
            EventLogEntry(
                timestampEpoch = this.firstPullLogTimestamp(now),
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
        pullCountdownExpired = false,
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
    val livePointState = if (this.phase == GamePhase.BETWEEN_POINTS) {
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
 * Apply automatic timer expirations that do not require an observer button press.
 *
 * @param now The current epoch millis so tests and background ticks can drive deterministic transitions.
 */
fun GameState.applyExpiredCountdownTransitions(now: Long): GameState {
    val countdown = this.countdown ?: return this
    if (countdown.isPaused()) {
        return this
    }
    if (now < countdown.targetEpoch) {
        return this
    }
    return when {
        this.phase == GamePhase.BETWEEN_POINTS && countdown.kind.usesBetweenPointsTarget() -> {
            this.automaticLivePointState(now)
        }
        this.phase == GamePhase.BETWEEN_POINTS && countdown.kind == CountdownKind.MISCONDUCT_BETWEEN_POINTS -> {
            this.automaticLivePointState(now)
        }
        this.phase == GamePhase.BETWEEN_POINTS && countdown.kind == CountdownKind.MISCONDUCT_DEFENSE_CHECK -> {
            this.automaticLivePointState(now)
        }
        this.phase == GamePhase.LIVE_POINT && countdown.kind == CountdownKind.TIME_OUT -> {
            this.automaticContinueLivePointState()
        }
        this.phase == GamePhase.HALFTIME && countdown.kind == CountdownKind.HALFTIME -> {
            val betweenPointsState = this.copy(
                phase = GamePhase.BETWEEN_POINTS,
                countdown = buildBetweenPointsCountdown(
                    pullingFromEnd = this.pullingFromEnd,
                    sequenceStart = countdown.targetEpoch,
                ),
                pullCountdownExpired = false,
                pullSkippedForCurrentPoint = false,
                pendingMisconductCountdown = false,
            )
            betweenPointsState.applyExpiredCountdownTransitions(now)
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
    val firstPullEntry = if (this.eventLog.isEmpty()) {
        listOf(
            EventLogEntry(
                timestampEpoch = this.firstPullLogTimestamp(now),
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
        pullCountdownExpired = false,
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
internal fun GameState.withUndo(previous: GameState, label: String): GameState {
    return copy(
        undoEntry = UndoEntry(label = label, previous = previous.copy(redoEntry = null)),
        redoEntry = null,
    )
}
