package rmjarvis.ultiobserver

import kotlinx.serialization.SerialName
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
internal const val DEFAULT_WATER_BREAK_MINUTES = 3
internal const val AQI_DEFAULT_WATER_BREAK_MINUTES = 4
internal const val LEVEL_TWO_WATER_BREAK_MINUTES = 4
internal const val LEVEL_TWO_EXTRA_BETWEEN_POINTS_SECONDS = 60
internal const val LEVEL_TWO_MAX_HARD_CAP_MINUTES = 90
internal const val LEVEL_TWO_SOFT_CAP_LEAD_MINUTES = 20
internal val USAU_DEFAULT_GENDER_RATIO_RULE = GenderRatioRule.ABBA

private const val YOUTH_TIME_BETWEEN_POINTS_SECONDS = 80

/// Configurable rules that affect scoring, caps, halftime, timeouts, pull timing, and mixed play.
@Serializable
data class GameRules(
    val gameTo: Int = USAU_DEFAULT_GAME_TO,
    val halftimeMinutes: Int = USAU_DEFAULT_HALFTIME_MINUTES,
    @SerialName("timeBetweenPointsSeconds")
    val nominalTimeBetweenPointsSeconds: Int = USAU_DEFAULT_TIME_BETWEEN_POINTS_SECONDS,
    val useHalfCap: Boolean = USAU_DEFAULT_USE_HALF_CAP,
    val halfCapMinutes: Int = USAU_DEFAULT_HALF_CAP_MINUTES,
    val useSoftCap: Boolean = USAU_DEFAULT_USE_SOFT_CAP,
    @SerialName("softCapMinutes")
    val nominalSoftCapMinutes: Int = USAU_DEFAULT_SOFT_CAP_MINUTES,
    val useHardCap: Boolean = USAU_DEFAULT_USE_HARD_CAP,
    @SerialName("hardCapMinutes")
    val nominalHardCapMinutes: Int = USAU_DEFAULT_HARD_CAP_MINUTES,
    val timeoutsPerHalf: Int = USAU_DEFAULT_TIMEOUTS_PER_HALF,
    val hasFloaterTimeout: Boolean = USAU_DEFAULT_HAS_FLOATER_TIMEOUT,
    val timeoutSeconds: Int = USAU_DEFAULT_TIMEOUT_SECONDS,
    val genderRatioRule: GenderRatioRule = USAU_DEFAULT_GENDER_RATIO_RULE,
    val switchGenZoneAtHalftime: Boolean = USAU_DEFAULT_SWITCH_GEN_ZONE_AT_HALFTIME,
    val useAirQualityGuidelines: Boolean = false,
    val heatLevel: HeatLevel = HeatLevel.NONE,
    val waterBreakMinutes: Int = DEFAULT_WATER_BREAK_MINUTES,
) {
    /// Soft-cap offset after applying all active rules.
    val softCapMinutes: Int
        get() {
            if (heatLevel != HeatLevel.LEVEL_2) {
                return nominalSoftCapMinutes
            }
            val heatSoftCap = (hardCapMinutes - LEVEL_TWO_SOFT_CAP_LEAD_MINUTES).coerceAtLeast(0)
            return if (useSoftCap) minOf(nominalSoftCapMinutes, heatSoftCap) else heatSoftCap
        }

    /// Hard-cap offset after applying all active rules.
    val hardCapMinutes: Int
        get() = if (heatLevel == HeatLevel.LEVEL_2) {
            if (useHardCap) {
                minOf(nominalHardCapMinutes, LEVEL_TWO_MAX_HARD_CAP_MINUTES)
            } else {
                LEVEL_TWO_MAX_HARD_CAP_MINUTES
            }
        } else {
            nominalHardCapMinutes
        }

    /// Time between points after applying all active rules.
    val timeBetweenPointsSeconds: Int
        get() = nominalTimeBetweenPointsSeconds + heatExtraBetweenPointsSeconds()

    /// How water breaks should be offered for the active heat level.
    val waterBreakMode: WaterBreakMode
        get() = when (heatLevel) {
            HeatLevel.NONE,
            HeatLevel.LEVEL_3 -> WaterBreakMode.NONE
            HeatLevel.MANUAL -> WaterBreakMode.MANUAL
            HeatLevel.LEVEL_1,
            HeatLevel.LEVEL_2 -> WaterBreakMode.AUTOMATIC
        }
}

/// USA Ultimate heat level, plus an app-level disabled state.
@Serializable
enum class HeatLevel(val displayText: String) {
    NONE("None"),
    LEVEL_1("Level 1"),
    LEVEL_2("Level 2"),
    LEVEL_3("Level 3"),
    MANUAL("Manual"),
}

/// How water breaks should be offered during this game.
enum class WaterBreakMode {
    NONE,
    MANUAL,
    AUTOMATIC,
}

/// Return rules configured for the standard behavior of one heat-level selection.
internal fun GameRules.withHeatLevel(newHeatLevel: HeatLevel): GameRules {
    return when (newHeatLevel) {
        HeatLevel.NONE,
        HeatLevel.LEVEL_3 -> copy(heatLevel = newHeatLevel)
        HeatLevel.MANUAL,
        HeatLevel.LEVEL_1 -> copy(
            heatLevel = newHeatLevel,
            waterBreakMinutes = if (useAirQualityGuidelines) {
                AQI_DEFAULT_WATER_BREAK_MINUTES
            } else {
                DEFAULT_WATER_BREAK_MINUTES
            },
        )
        HeatLevel.LEVEL_2 -> copy(
            heatLevel = newHeatLevel,
            waterBreakMinutes = LEVEL_TWO_WATER_BREAK_MINUTES,
        )
    }
}

/// Switch guidance type and restore the active level's standard water-break duration.
internal fun GameRules.withAirQualityGuidelines(useAirQualityGuidelines: Boolean): GameRules {
    if (this.useAirQualityGuidelines == useAirQualityGuidelines) {
        return this
    }
    return copy(useAirQualityGuidelines = useAirQualityGuidelines)
        .withHeatLevel(heatLevel)
}

/// Return the selected heat-level name for display-only rule summaries.
internal fun GameRules.heatLevelLabel(): String {
    return if (useAirQualityGuidelines) "AQI level" else "Heat level"
}

/// Return the setup-editor label, retaining both choices until a level is selected.
internal fun GameRules.heatLevelEditorLabel(): String {
    if (heatLevel == HeatLevel.NONE) {
        return "Heat/AQI level"
    }
    return if (useAirQualityGuidelines) "AQI level" else "Heat level"
}

/// Return the extra ordinary between-points time imposed by the active heat level.
internal fun GameRules.heatExtraBetweenPointsSeconds(): Int {
    return if (heatLevel == HeatLevel.LEVEL_2) {
        LEVEL_TWO_EXTRA_BETWEEN_POINTS_SECONDS
    } else {
        0
    }
}

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
        nominalTimeBetweenPointsSeconds = usauTimeBetweenPointsSeconds(level),
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
    return if (nominalTimeBetweenPointsSeconds == previousDefault) {
        copy(nominalTimeBetweenPointsSeconds = newDefault)
    } else {
        this
    }
}

/// Return the compact half/soft/hard cap summary.
internal fun GameRules.formatCaps(): String {
    return CapType.entries.joinToString("/") { capType ->
        if (capEnabled(capType)) {
            "+${capMinutes(capType)}"
        } else {
            "-"
        }
    }
}

/// Return one cap value for the editable setup-rules list.
internal fun GameRules.formatCap(capType: CapType): String {
    if (!capEnabled(capType)) {
        return "None"
    }
    val nominalEnabled = nominalCapEnabled(capType)
    val nominalText = if (nominalEnabled) "+${nominalCapMinutes(capType)}" else "none"
    val capText = "+${capMinutes(capType)}"
    return if (nominalEnabled && capText == nominalText) {
        capText
    } else {
        "$capText (was $nominalText)"
    }
}

/// Return whether one cap is enabled in the persisted nominal rules.
internal fun GameRules.nominalCapEnabled(capType: CapType): Boolean {
    return when (capType) {
        CapType.HALF -> useHalfCap
        CapType.SOFT -> useSoftCap
        CapType.HARD -> useHardCap
    }
}

/// Return the persisted nominal offset for one cap.
internal fun GameRules.nominalCapMinutes(capType: CapType): Int {
    return when (capType) {
        CapType.HALF -> halfCapMinutes
        CapType.SOFT -> nominalSoftCapMinutes
        CapType.HARD -> nominalHardCapMinutes
    }
}

/// Return whether one cap is active after applying heat-level overrides.
internal fun GameRules.capEnabled(capType: CapType): Boolean {
    return nominalCapEnabled(capType) ||
        heatLevel == HeatLevel.LEVEL_2 && capType != CapType.HALF
}

/// Return the cap offset after applying all active rules.
internal fun GameRules.capMinutes(capType: CapType): Int {
    return when (capType) {
        CapType.HALF -> halfCapMinutes
        CapType.SOFT -> softCapMinutes
        CapType.HARD -> hardCapMinutes
    }
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

/**
 * Format the time between points.
 *
 * @param compact Whether to combine all active rules into one total.
 */
internal fun GameRules.formatTimeBetweenPoints(compact: Boolean): String {
    val extraSeconds = heatExtraBetweenPointsSeconds()
    return if (compact || extraSeconds == 0) {
        "$timeBetweenPointsSeconds sec"
    } else {
        "$nominalTimeBetweenPointsSeconds +$extraSeconds sec"
    }
}

/// Format the timeout offense-set duration for setup display.
internal fun GameRules.formatTimeoutDuration(): String {
    return "$timeoutSeconds sec"
}

/**
 * Format the active heat level.
 *
 * @param compact Whether to omit the configured water-break duration.
 */
internal fun GameRules.formatHeatLevel(compact: Boolean): String {
    if (compact) {
        return heatLevel.displayText
    }
    return when (heatLevel) {
        HeatLevel.NONE,
        HeatLevel.LEVEL_3 -> heatLevel.displayText
        HeatLevel.MANUAL,
        HeatLevel.LEVEL_1,
        HeatLevel.LEVEL_2 -> "${heatLevel.displayText} ($waterBreakMinutes min)"
    }
}

/// Explain a Level 2 cap change while editing its nominal value.
internal fun GameRules.heatLevelTwoCapEffectNote(capType: CapType): String? {
    if (
        heatLevel != HeatLevel.LEVEL_2 ||
        capType == CapType.HALF ||
        nominalCapEnabled(capType) &&
        nominalCapMinutes(capType) == capMinutes(capType)
    ) {
        return null
    }
    val action = if (nominalCapEnabled(capType)) "shortening" else "overriding"
    return "${heatLevelLabel()} 2 is $action this to ${capMinutes(capType)} minutes."
}

/// Describe exactly how Level 2 changes the configured caps, or null when neither changes.
internal fun GameRules.heatLevelTwoCapGuidance(): String? {
    val levelTwoRules = this.withHeatLevel(HeatLevel.LEVEL_2)
    val softCapMinutes = levelTwoRules.softCapMinutes
    val hardCapMinutes = levelTwoRules.hardCapMinutes
    if (!useSoftCap || !useHardCap) {
        return "The soft/hard caps are set to $softCapMinutes/$hardCapMinutes minutes."
    }
    val softShortened = softCapMinutes < nominalSoftCapMinutes
    val hardShortened = hardCapMinutes < nominalHardCapMinutes
    return when {
        softShortened && hardShortened ->
            "The soft/hard caps are shortened to $softCapMinutes/$hardCapMinutes minutes."
        softShortened -> "The soft cap is shortened to $softCapMinutes minutes."
        hardShortened -> "The hard cap is shortened to $hardCapMinutes minutes."
        else -> null
    }
}

/// Describe the live-game effect of selecting one heat level.
internal fun GameRules.heatLevelSelectionDescription(newHeatLevel: HeatLevel): String {
    val selectedRules = if (heatLevel == newHeatLevel) {
        this
    } else {
        withHeatLevel(newHeatLevel)
    }
    return when (newHeatLevel) {
        HeatLevel.NONE -> "Disable water breaks."
        HeatLevel.LEVEL_1 ->
            "One ${selectedRules.waterBreakMinutes}-minute water break per half."
        HeatLevel.LEVEL_2 -> buildString {
            append(
                "One ${selectedRules.waterBreakMinutes}-minute water break per " +
                    "half. Add $LEVEL_TWO_EXTRA_BETWEEN_POINTS_SECONDS seconds between points."
            )
            val levelTwoRules =
                this@heatLevelSelectionDescription.withHeatLevel(HeatLevel.LEVEL_2)
            val softCapAffected = !useSoftCap ||
                nominalSoftCapMinutes != levelTwoRules.softCapMinutes
            val hardCapAffected = !useHardCap ||
                nominalHardCapMinutes != levelTwoRules.hardCapMinutes
            when {
                softCapAffected && hardCapAffected -> append(" Adjust soft/hard caps.")
                softCapAffected -> append(" Adjust soft cap.")
                hardCapAffected -> append(" Adjust hard cap.")
            }
        }
        HeatLevel.MANUAL ->
            "Use normal timing with ${selectedRules.waterBreakMinutes}-minute manual water " +
                "breaks available."
        HeatLevel.LEVEL_3 -> "Suspend this game because play should not continue."
    }
}

/**
 * Return the automatic water-break trigger scores for this game target.
 *
 * This is 4,12 for a game to 15.  4/10 for a game to 13.
 * In general these are the "quarter" scores rounded up.
 */
internal fun GameRules.waterBreakScores(): List<Int> {
    return listOf(
        (gameTo + 3) / 4,
        (3 * gameTo + 3) / 4,
    )
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
