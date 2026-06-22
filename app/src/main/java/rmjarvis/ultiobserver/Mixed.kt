package rmjarvis.ultiobserver

/// Return whether this live game uses mixed-division gender-ratio rules.
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

/// Return the team that chooses the point's mixed gender ratio, when the app can know it.
fun GameState.ratioChoosingTeam(): TeamId? {
    if (!usesMixedDivision()) {
        return null
    }
    return when (rules.genderRatioRule) {
        GenderRatioRule.GEN_ZONE -> {
            val activeGenZone = if (halftimeTaken && switchGenZoneAtHalftime) {
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

/// Return whether majority-pull violations may be assessed in this game.
fun GameState.usesMajorityPullRule(): Boolean {
    return usesMixedDivision() && rules.useMajorityPullRule
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

/**
 * Return the team currently defending a field end.
 *
 * @param end The end whose defending team should be returned.
 */
private fun GameState.teamDefendingEnd(end: FieldEnd): TeamId {
    return if (end == FieldEnd.NEAR) nearAttackingTeam.flip() else nearAttackingTeam
}
