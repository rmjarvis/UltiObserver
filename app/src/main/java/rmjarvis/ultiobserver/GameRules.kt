package rmjarvis.ultiobserver

import kotlinx.serialization.Serializable

internal const val USAU_DEFAULT_GAME_TO = 15
internal const val USAU_DEFAULT_HALFTIME_MINUTES = 7
internal const val USAU_DEFAULT_TIME_BETWEEN_POINTS_SECONDS = 60
internal const val PULL_SECONDS_AFTER_OFFENSE_READY = 20
internal const val USAU_DEFAULT_USE_HALF_CAP = true
internal const val USAU_DEFAULT_HALF_CAP_MINUTES = 45
internal const val USAU_DEFAULT_USE_SOFT_CAP = true
internal const val USAU_DEFAULT_SOFT_CAP_MINUTES = 90
internal const val USAU_DEFAULT_USE_HARD_CAP = true
internal const val USAU_DEFAULT_HARD_CAP_MINUTES = 105
internal const val USAU_DEFAULT_TIMEOUTS_PER_HALF = 2
internal const val USAU_DEFAULT_HAS_FLOATER_TIMEOUT = false
internal const val USAU_DEFAULT_TIMEOUT_SECONDS = 70
internal const val USAU_DEFAULT_SWITCH_GEN_ZONE_AT_HALFTIME = true
internal val USAU_DEFAULT_GENDER_RATIO_RULE = GenderRatioRule.ABBA

private const val YOUTH_TIME_BETWEEN_POINTS_SECONDS = 80

/// Configurable rules that affect scoring, caps, halftime, timeouts, pull timing, and mixed play.
@Serializable
data class GameRules(
    val gameTo: Int = USAU_DEFAULT_GAME_TO,
    val halftimeMinutes: Int = USAU_DEFAULT_HALFTIME_MINUTES,
    val timeBetweenPointsSeconds: Int = USAU_DEFAULT_TIME_BETWEEN_POINTS_SECONDS,
    val useHalfCap: Boolean = USAU_DEFAULT_USE_HALF_CAP,
    val halfCapMinutes: Int = USAU_DEFAULT_HALF_CAP_MINUTES,
    val useSoftCap: Boolean = USAU_DEFAULT_USE_SOFT_CAP,
    val softCapMinutes: Int = USAU_DEFAULT_SOFT_CAP_MINUTES,
    val useHardCap: Boolean = USAU_DEFAULT_USE_HARD_CAP,
    val hardCapMinutes: Int = USAU_DEFAULT_HARD_CAP_MINUTES,
    val timeoutsPerHalf: Int = USAU_DEFAULT_TIMEOUTS_PER_HALF,
    val hasFloaterTimeout: Boolean = USAU_DEFAULT_HAS_FLOATER_TIMEOUT,
    val timeoutSeconds: Int = USAU_DEFAULT_TIMEOUT_SECONDS,
    val genderRatioRule: GenderRatioRule = USAU_DEFAULT_GENDER_RATIO_RULE,
    val switchGenZoneAtHalftime: Boolean = USAU_DEFAULT_SWITCH_GEN_ZONE_AT_HALFTIME,
)

/// Return the USAU default between-points offense-ready deadline for this level.
internal fun usauTimeBetweenPointsSeconds(level: String): Int {
    return if (level.trim() == "Youth") {
        YOUTH_TIME_BETWEEN_POINTS_SECONDS
    } else {
        USAU_DEFAULT_TIME_BETWEEN_POINTS_SECONDS
    }
}

/// Return USAU default rules for this game level.
internal fun usauDefaultGameRules(level: String): GameRules {
    return GameRules().copy(
        timeBetweenPointsSeconds = usauTimeBetweenPointsSeconds(level),
    )
}

/// Return the USAU defaults button label for this level.
internal fun usauDefaultsButtonLabel(level: String): String {
    return if (level.trim() == "Youth") {
        "Reset to USAU (Youth) defaults"
    } else {
        "Reset to USAU defaults"
    }
}

/**
 * Return rules whose default time between points follows a level change.
 *
 * Custom time between points is preserved. Only the current USAU default for the previous level
 * is updated to the USAU default for the new level.
 *
 * @param previousLevel The level before the edit.
 * @param newLevel The level after the edit.
 */
internal fun GameRules.withLevelDefaultTimeBetweenPoints(
    previousLevel: String,
    newLevel: String,
): GameRules {
    val previousDefault = usauTimeBetweenPointsSeconds(previousLevel)
    val newDefault = usauTimeBetweenPointsSeconds(newLevel)
    return if (timeBetweenPointsSeconds == previousDefault) {
        copy(timeBetweenPointsSeconds = newDefault)
    } else {
        this
    }
}

/// Return the compact half/soft/hard cap summary.
internal fun GameRules.capRulesSummary(): String {
    return "${capSummary(useHalfCap, halfCapMinutes)}/" +
        "${capSummary(useSoftCap, softCapMinutes)}/" +
        capSummary(useHardCap, hardCapMinutes)
}

/**
 * Return the compact display for one cap rule.
 *
 * @param enabled Whether the cap is enabled.
 * @param minutes The cap offset in minutes when enabled.
 */
private fun capSummary(enabled: Boolean, minutes: Int): String {
    return if (enabled) "+$minutes" else "-"
}

/// Format timeout rules for setup display.
internal fun GameRules.formatTimeoutRules(): String {
    return buildString {
        append("$timeoutsPerHalf/half")
        if (hasFloaterTimeout) {
            append(" + floater")
        }
    }
}

/// Format the between-points offense-ready deadline for setup display.
internal fun GameRules.formatTimeBetweenPoints(): String {
    return "$timeBetweenPointsSeconds sec"
}

/// Format the timeout offense-set duration for setup display.
internal fun GameRules.formatTimeoutDuration(): String {
    return "$timeoutSeconds sec"
}

/// Return the explanatory note for one mixed gender-ratio rule choice.
internal fun GenderRatioRule.explanation(): String {
    return when (this) {
        GenderRatioRule.ABBA ->
            "Alternate two at a time after the first point: ABBAABBAA..."
        GenderRatioRule.GEN_ZONE ->
            "The team in a particular end zone decides the ratio each point."
        GenderRatioRule.OFFENSE_DECIDES ->
            "The team receiving the pull decides the ratio each point."
        GenderRatioRule.FIXED_4M_3W,
        GenderRatioRule.FIXED_4W_3M ->
            "Fixed gender ratio."
        GenderRatioRule.NA ->
            "No gender-ratio prompts will be shown."
    }
}
