package rmjarvis.ultiobserver

/**
 * One labeled item in the live rules reference.
 *
 * @param label Short rule name.
 * @param value Compact display value.
 * @param heatAdjusted Whether heat guidance changes this displayed value.
 */
internal class RulesReferenceItem(
    val label: String,
    val value: String,
    val heatAdjusted: Boolean,
)

/// Return the items shown in the live rules reference.
internal fun GameState.rulesReferenceItems(): List<RulesReferenceItem> {
    val items = mutableListOf(
        RulesReferenceItem("Game to", gameToReferenceText(), heatAdjusted = false),
        RulesReferenceItem("Half at", halfAtReferenceText(), heatAdjusted = false),
        RulesReferenceItem("Start time", formatClockTime(startTime), heatAdjusted = false),
    )
    capReferenceItem(CapType.HALF)?.let { items += it }
    capReferenceItem(CapType.SOFT)?.let { items += it }
    capReferenceItem(CapType.HARD)?.let { items += it }
    items += RulesReferenceItem("Timeouts", rules.formatTimeoutRules(), heatAdjusted = false)
    if (usesMixedDivision()) {
        items += RulesReferenceItem(
            "Gender ratio",
            genderRatioReferenceText(),
            heatAdjusted = false,
        )
    }
    if (rules.heatLevel != HeatLevel.NONE) {
        items += RulesReferenceItem(
            rules.heatLevelLabel(),
            rules.formatHeatLevel(compact = true),
            heatAdjusted = rules.heatLevel == HeatLevel.LEVEL_2,
        )
    }
    items += RulesReferenceItem(
        "Time between points",
        rules.formatTimeBetweenPoints(compact = true),
        heatAdjusted = rules.heatLevel == HeatLevel.LEVEL_2,
    )
    items += RulesReferenceItem(
        "Timeout duration",
        rules.formatTimeoutDuration(),
        heatAdjusted = false,
    )
    items += RulesReferenceItem("Halftime", halftimeReferenceText(), heatAdjusted = false)
    return items
}

/// Return the game target currently in effect, mentioning the original target after a cap change.
private fun GameState.gameToReferenceText(): String {
    val currentTarget = winningScore ?: rules.gameTo
    return if (currentTarget == rules.gameTo) {
        rules.gameTo.toString()
    } else {
        "$currentTarget (was ${rules.gameTo})"
    }
}

/// Return the active halftime score target.
private fun GameState.halfAtReferenceText(): String {
    val originalTarget = halftimeScore(rules)
    val currentTarget = halftimeTargetScore ?: originalTarget
    return if (currentTarget == originalTarget) {
        originalTarget.toString()
    } else {
        "$currentTarget (was $originalTarget)"
    }
}

/// Return the halftime duration.
private fun GameState.halftimeReferenceText(): String {
    return "${rules.halftimeMinutes} min"
}

/// Return the reference item for one enabled cap, or null when that cap is disabled.
private fun GameState.capReferenceItem(
    capType: CapType,
): RulesReferenceItem? {
    if (!rules.capEnabled(capType)) {
        return null
    }
    return RulesReferenceItem(
        capType.label,
        formatClockTime(localTimeFromEpoch(capEpoch(capType), timeZone)),
        heatAdjusted = !rules.nominalCapEnabled(capType) ||
            rules.nominalCapMinutes(capType) != rules.capMinutes(capType),
    )
}

/// Return compact mixed gender-ratio rule text.
private fun GameState.genderRatioReferenceText(): String {
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
