package rmjarvis.ultiobserver

/**
 * One labeled row in the game-rules quick reference.
 *
 * @param label Short rule name.
 * @param value Compact display value.
 */
internal class GameRulesDialogRow(
    val label: String,
    val value: String,
)

/// Return compact rows for the game-rules quick reference.
internal fun GameState.gameRulesDialogRows(): List<GameRulesDialogRow> {
    val rows = mutableListOf(
        GameRulesDialogRow("Game to", gameToDialogText()),
        GameRulesDialogRow("Half at", halfAtDialogText()),
        GameRulesDialogRow("Start time", formatClockTime(startTime)),
    )
    capDialogRow(CapType.HALF)?.let { rows += it }
    capDialogRow(CapType.SOFT)?.let { rows += it }
    capDialogRow(CapType.HARD)?.let { rows += it }
    rows += GameRulesDialogRow("Halftime", halftimeDialogText())
    rows += GameRulesDialogRow("Timeouts", rules.formatTimeoutRules())
    rows += GameRulesDialogRow("Time between points", rules.formatTimeBetweenPoints())
    rows += GameRulesDialogRow("Timeout duration", rules.formatTimeoutDuration())
    rules.formatWaterBreaks()?.let { waterBreakText ->
        rows += GameRulesDialogRow("Water breaks", waterBreakText)
    }
    if (usesMixedDivision()) {
        rows += GameRulesDialogRow("Gender ratio", genderRatioDialogText())
    }
    return rows
}

/// Return the game target currently in effect, mentioning the original target after a cap change.
private fun GameState.gameToDialogText(): String {
    val currentTarget = winningScore ?: rules.gameTo
    return if (currentTarget == rules.gameTo) {
        rules.gameTo.toString()
    } else {
        "$currentTarget (was ${rules.gameTo})"
    }
}

/// Return the active halftime score target.
private fun GameState.halfAtDialogText(): String {
    val originalTarget = halftimeScore(rules)
    val currentTarget = halftimeTargetScore ?: originalTarget
    return if (currentTarget == originalTarget) {
        originalTarget.toString()
    } else {
        "$currentTarget (was $originalTarget)"
    }
}

/// Return the halftime duration.
private fun GameState.halftimeDialogText(): String {
    return "${rules.halftimeMinutes} min"
}

/// Return the row for one enabled cap, or null when that cap is disabled.
private fun GameState.capDialogRow(
    capType: CapType,
): GameRulesDialogRow? {
    val capEnabled = when (capType) {
        CapType.HALF -> rules.useHalfCap
        CapType.SOFT -> rules.useSoftCap
        CapType.HARD -> rules.useHardCap
    }
    if (!capEnabled) {
        return null
    }
    return GameRulesDialogRow(
        capType.label,
        formatClockTime(localTimeFromEpoch(capEpoch(capType), timeZone)),
    )
}

/// Return compact mixed gender-ratio rule text.
private fun GameState.genderRatioDialogText(): String {
    return if (rules.genderRatioRule == GenderRatioRule.GEN_ZONE) {
        if (rules.switchGenZoneAtHalftime) {
            "Gen Zone"
        } else {
            "Gen Zone, no switch at half"
        }
    } else {
        rules.genderRatioRule.displayText
    }
}
