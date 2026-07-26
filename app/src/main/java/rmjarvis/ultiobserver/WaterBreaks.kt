package rmjarvis.ultiobserver

internal const val HEAT_LEVEL_THREE_UNDO_LABEL = "Undo Heat level 3 — game suspended"

/// Return whether a water break can add time to the current countdown.
fun GameState.canApplyWaterBreak(): Boolean {
    if (countdown == null) return false
    return rules.waterBreakMode != WaterBreakMode.NONE &&
        phase.isBeforeLivePoint
}

/// Format an ordinary or pending late-activation water-break prompt.
internal fun GameState.waterBreakPromptMessage(): RuleGuidanceMessage {
    val lateHeatLevelChange = pendingWaterBreakOffer &&
        maxOf(teamOne.score, teamTwo.score) > waterBreakScore()
    val lines = mutableListOf<RuleGuidanceLine>()
    if (lateHeatLevelChange) {
        lines += RuleGuidanceLine(
            "${rules.heatLevel.displayText} is now in effect, and no water break has been taken " +
                "this half."
        )
    } else if (
        pendingWaterBreakOffer &&
        softCapApplied &&
        maxOf(teamOne.score, teamTwo.score) < waterBreakScore()
    ) {
        val quarter = if (halftimeTaken) "third" else "first"
        lines += RuleGuidanceLine("Soft cap triggers the $quarter-quarter water break.")
    } else if (pendingWaterBreakOffer) {
        val quarter = if (halftimeTaken) "Third" else "First"
        lines += RuleGuidanceLine("$quarter quarter score reached.")
    }
    lines += RuleGuidanceLine(
        "Take a ${rules.waterBreakMinutes}-minute water break now."
    )
    return RuleGuidanceMessage(lines)
}

/**
 * Apply a water break by extending the active pre-point countdown.
 *
 * @param now The epoch millis used for the event-log entry.
 */
fun GameState.applyWaterBreak(now: Long): GameState {
    val activeCountdown = countdown!!
    val extraSeconds = rules.waterBreakMinutes * 60
    return copy(
        countdown = activeCountdown.extendBy(extraSeconds),
        pendingWaterBreakOffer = false,
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
 * Report whether a goal by one team would trigger a scheduled automatic water break.
 *
 * @param scoringTeam The team whose next goal is being considered.
 */
internal fun GameState.goalTriggersAutomaticWaterBreak(scoringTeam: TeamId): Boolean {
    if (rules.waterBreakMode != WaterBreakMode.AUTOMATIC || phase == GamePhase.GAME_OVER) {
        return false
    }
    val scoringTeamNextScore = teamFor(scoringTeam).score + 1
    val otherTeamScore = teamFor(scoringTeam.flip()).score
    val nextHighScore = maxOf(scoringTeamNextScore, otherTeamScore)
    if (nextHighScore >= (winningScore ?: rules.gameTo)) {
        return false
    }
    val halftimeScore = halftimeTargetScore ?: halftimeScore(rules)
    if (!halftimeTaken && nextHighScore >= halftimeScore) {
        return false
    }
    val waterBreakScore = waterBreakScore()
    return scoringTeamNextScore == waterBreakScore &&
        otherTeamScore < waterBreakScore
}

/// Report whether a water-break prompt should be shown when soft cap applies.
internal fun GameState.softCapWaterBreakReached(): Boolean {
    val waterBreakScore = waterBreakScore()
    val highScore = maxOf(teamOne.score, teamTwo.score)
    return rules.waterBreakMode == WaterBreakMode.AUTOMATIC &&
        canApplyWaterBreak() &&
        highScore < waterBreakScore
}

/// Return the water-break scores, adjusting the second for the actual halftime score.
internal fun GameState.waterBreakScores(): List<Int> {
    val normalScores = rules.waterBreakScores()
    val normalHalftimeScore = halftimeScore(rules)
    val actualHalftimeScore = halftimeHighScore ?: normalHalftimeScore
    return listOf(
        normalScores[0],
        normalScores[1] - normalHalftimeScore + actualHalftimeScore,
    )
}

/// Return the scheduled water-break score for the active half.
internal fun GameState.waterBreakScore(): Int {
    return waterBreakScores()[if (halftimeTaken) 1 else 0]
}

/// Return whether any water break has been recorded during the active half.
internal fun GameState.hasWaterBreakThisHalf(): Boolean {
    val halfStartIndex = eventLog.indexOfLast { entry -> entry.type == EventLogType.HALFTIME }
    return eventLog.drop(halfStartIndex + 1).any { entry ->
        entry.type == EventLogType.WATER_BREAK
    }
}

/**
 * Return whether enabling automatic heat guidance after its scheduled score should offer a break.
 *
 * E.g. if the score is already 5-3, and we switch to Heat Level 1, then offer the first half
 * break now, even though we are after the normal break at 4.  If a water break has already
 * been taken during the half (proved by the event log), then don't offer another one now.
 *
 * Note: if this happens between points, then wait until after the next goal. (Which is why
 * the below test is >=, not >.)
 */
internal fun GameState.shouldOfferLateWaterBreak(newHeatLevel: HeatLevel): Boolean {
    if (
        newHeatLevel != HeatLevel.LEVEL_1 &&
        newHeatLevel != HeatLevel.LEVEL_2
    ) {
        return false
    }
    return phase != GamePhase.GAME_OVER &&
        maxOf(teamOne.score, teamTwo.score) >= waterBreakScore() &&
        !hasWaterBreakThisHalf()
}

/// Clear a pending automatic water-break offer when the observer declines it.
fun GameState.declinePendingWaterBreak(): GameState {
    return copy(pendingWaterBreakOffer = false)
}

/**
 * Apply a live heat-level change, including an immediate increase to an ordinary countdown.
 *
 * Decreasing the heat level deliberately leaves the active countdown unchanged. Level 3 ends the
 * current game as a heat suspension.
 */
fun GameState.setHeatLevel(newHeatLevel: HeatLevel, now: Long): GameState {
    if (rules.heatLevel == newHeatLevel) {
        return this
    }
    val previousExtraSeconds = rules.heatExtraBetweenPointsSeconds()
    val offerLateWaterBreak = shouldOfferLateWaterBreak(newHeatLevel)
    val updatedRules = rules.withHeatLevel(newHeatLevel)
    val addedSeconds = (
        updatedRules.heatExtraBetweenPointsSeconds() - previousExtraSeconds
        ).coerceAtLeast(0)
    val updatedCountdown = if (
        countdown?.kind == CountdownKind.BETWEEN_POINTS &&
        addedSeconds > 0
    ) {
        countdown.withPullTiming(updatedRules.standardPullTiming())
    } else {
        countdown
    }
    val updatedState = copy(
        rules = updatedRules,
        endEpoch = if (newHeatLevel == HeatLevel.LEVEL_3) now else endEpoch,
        phase = if (newHeatLevel == HeatLevel.LEVEL_3) GamePhase.GAME_OVER else phase,
        countdown = if (newHeatLevel == HeatLevel.LEVEL_3) null else updatedCountdown,
        pendingMisconductCountdown = if (newHeatLevel == HeatLevel.LEVEL_3) {
            false
        } else {
            pendingMisconductCountdown
        },
        pendingCapOffer = if (newHeatLevel == HeatLevel.LEVEL_3) null else pendingCapOffer,
        pendingWaterBreakOffer = when (newHeatLevel) {
            HeatLevel.LEVEL_1,
            HeatLevel.LEVEL_2 -> pendingWaterBreakOffer || offerLateWaterBreak
            else -> false
        },
        lastEvent = if (newHeatLevel == HeatLevel.LEVEL_3) {
            "Heat level 3 — game suspended."
        } else {
            "${newHeatLevel.displayText} in effect."
        },
    ).withEventLogEntry(
        EventLogEntry(
            timestampEpoch = now,
            type = EventLogType.HEAT_LEVEL,
            heatLevel = newHeatLevel,
        )
    )
    val undoLabel = if (newHeatLevel == HeatLevel.LEVEL_3) {
        HEAT_LEVEL_THREE_UNDO_LABEL
    } else {
        "Undo Heat level ${newHeatLevel.displayText.removePrefix("Level ")}"
    }
    return updatedState.withUndo(this, undoLabel)
}
