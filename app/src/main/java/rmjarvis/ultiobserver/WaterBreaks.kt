package rmjarvis.ultiobserver

/// Return whether a water break can add time to the current countdown.
fun GameState.canApplyWaterBreak(): Boolean {
    if (countdown == null) return false
    return rules.waterBreakMode != WaterBreakMode.NONE &&
        phase.isBeforeLivePoint
}

/**
 * Apply a water break by extending the active pre-point countdown.
 *
 * @param now The epoch millis used for the event-log entry.
 */
fun GameState.applyWaterBreak(now: Long): GameState {
    val activeCountdown = countdown!!
    val extraSeconds = rules.waterBreakMinutes * 60
    val updatedCountdown = activeCountdown.copy(
        durationSeconds = activeCountdown.durationSeconds + extraSeconds,
        targetEpoch = activeCountdown.targetEpoch + extraSeconds * 1000L,
    )
    return copy(
        countdown = updatedCountdown,
        lastEvent = "Water break.",
    ).withEventLogEntry(
        EventLogEntry(
            timestampEpoch = now,
            type = EventLogType.WATER_BREAK,
            delta = rules.waterBreakMinutes,
        )
    ).withUndo(this, "Undo Water break")
}

/**
 * Report whether the just-scoring team reached an automatic water-break score.
 *
 * @param scoringTeam The team that scored the just-finished point.
 */
internal fun GameState.automaticWaterBreakReached(scoringTeam: TeamId): Boolean {
    if (rules.waterBreakMode != WaterBreakMode.AUTOMATIC || !canApplyWaterBreak()) {
        return false
    }
    val scores = rules.waterBreakScores()
    val waterBreakScore = if (!halftimeTaken) scores[0] else scores[1]
    val otherTeam = scoringTeam.flip()
    return teamFor(scoringTeam).score == waterBreakScore &&
        teamFor(otherTeam).score < waterBreakScore
}

/// Report whether a water-break prompt should be shown when soft cap applies.
internal fun GameState.softCapWaterBreakReached(): Boolean {
    val scores = rules.waterBreakScores()
    val waterBreakScore = if (!halftimeTaken) scores[0] else scores[1]
    val highScore = maxOf(teamOne.score, teamTwo.score)
    return rules.waterBreakMode == WaterBreakMode.AUTOMATIC &&
        canApplyWaterBreak() &&
        highScore < waterBreakScore
}
