package rmjarvis.ultiobserver

/// Return whether this game uses mixed-division gender-ratio rules.
fun GameState.usesMixedDivision(): Boolean {
    return division == GameDivision.MIXED
}

/// Return whether this rule has a point ratio the app can display directly.
fun GenderRatioRule.hasDisplayablePointRatio(): Boolean {
    return this == GenderRatioRule.ABBA ||
        this == GenderRatioRule.FIXED_4M_3W ||
        this == GenderRatioRule.FIXED_4W_3M
}

/// Return whether this rule needs a pre-game field/starting-pull choice.
fun GenderRatioRule.hasStartingPullChoice(): Boolean {
    return this == GenderRatioRule.ABBA || this == GenderRatioRule.GEN_ZONE
}

/// Return the current point number, counting the upcoming point as score total plus one.
fun GameState.currentPointNumber(): Int {
    return teamOne.score + teamTwo.score + 1
}

/// Return the mixed gender ratio to display for this point, when the app can know it.
fun GameState.currentGenderRatio(): GenderRatio? {
    if (!usesMixedDivision()) {
        return null
    }
    return when (rules.genderRatioRule) {
        GenderRatioRule.ABBA -> abbaRatioForPoint(currentPointNumber())
        GenderRatioRule.FIXED_4M_3W -> GenderRatio.FOUR_MEN_THREE_WOMEN
        GenderRatioRule.FIXED_4W_3M -> GenderRatio.FOUR_WOMEN_THREE_MEN
        GenderRatioRule.NA,
        GenderRatioRule.GEN_ZONE,
        GenderRatioRule.OFFENSE_DECIDES -> null
    }
}

/**
 * Return the compact field-badge label for this point's mixed gender ratio.
 *
 * @param showAbbaRatioAsSequence Whether ABBA badges should use M1/M2/W1/W2 shorthand.
 */
fun GameState.currentGenderRatioBadgeText(showAbbaRatioAsSequence: Boolean): String {
    val currentRatio = currentGenderRatio()
        ?: error("No current gender ratio is available for a field badge.")
    if (rules.genderRatioRule != GenderRatioRule.ABBA || !showAbbaRatioAsSequence) {
        return currentRatio.displayText
    }
    val patternIndex = (currentPointNumber() - 1).coerceAtLeast(0) % 4
    val baseLabels = when (initialGenderRatio) {
        GenderRatio.FOUR_MEN_THREE_WOMEN -> listOf("M2", "W1", "W2", "M1")
        GenderRatio.FOUR_WOMEN_THREE_MEN -> listOf("W2", "M1", "M2", "W1")
    }
    return baseLabels[patternIndex]
}

/// Return the team that chooses the point's mixed gender ratio, when the app can know it.
fun GameState.ratioChoosingTeam(): TeamId? {
    if (!usesMixedDivision()) {
        return null
    }
    return when (rules.genderRatioRule) {
        GenderRatioRule.GEN_ZONE -> {
            val activeGenZone = if (halftimeTaken && rules.switchGenZoneAtHalftime) {
                firstHalfGenZone.flip()
            } else {
                firstHalfGenZone
            }
            teamDefendingEnd(activeGenZone)
        }
        GenderRatioRule.OFFENSE_DECIDES -> pullingTeam.flip()
        GenderRatioRule.NA,
        GenderRatioRule.ABBA,
        GenderRatioRule.FIXED_4M_3W,
        GenderRatioRule.FIXED_4W_3M -> null
    }
}

/**
 * Return the ABBA gender ratio for one point number.
 *
 * @param pointNumber One-based point number, where point one uses the initial ratio.
 */
fun GameState.abbaRatioForPoint(pointNumber: Int): GenderRatio {
    val patternIndex = (pointNumber - 1).coerceAtLeast(0) % 4
    return if (patternIndex == 0 || patternIndex == 3) {
        initialGenderRatio
    } else {
        initialGenderRatio.flip()
    }
}
