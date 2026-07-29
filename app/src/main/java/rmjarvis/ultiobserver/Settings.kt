package rmjarvis.ultiobserver

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

/// Per-cue alert choice before the global alert mode is applied.
@Serializable
enum class TimingAlertMode {
    NONE,
    VIBRATE,
    TICK,
    BEEP,
    KNOCK,
    DING;

    /// Convert a sound-producing alert mode into its concrete sound clip family.
    fun toTimingAlertSound(): TimingAlertSound {
        return when (this) {
            TICK -> TimingAlertSound.TICK
            BEEP -> TimingAlertSound.BEEP
            KNOCK -> TimingAlertSound.KNOCK
            DING -> TimingAlertSound.DING
            NONE, VIBRATE -> error("$this is not a sound timing alert mode.")
        }
    }
}

/**
 * Base sound family used for audible timing alerts.
 *
 * @param label The user-facing sound name shown in settings.
 */
@Serializable
enum class TimingAlertSound(
    val label: String,
) {
    TICK("Tick"),
    BEEP("Beep"),
    KNOCK("Knock"),
    DING("Ding"),
}

/**
 * App-wide timing-alert policy that constrains per-cue settings.
 *
 * @param label The user-facing mode name shown in settings.
 */
enum class TimingAlertGlobalMode(
    val label: String,
) {
    OFF("Off"),
    VIBRATION_ONLY("Vibration only"),
    SOUNDS_ON("Sounds on"),
}

/**
 * User-configurable timing alert behavior.
 *
 * @param globalMode The app-wide mode controlling whether alerts are off, vibration-only, or sound-enabled.
 * @param soundVolume Playback volume for sound alerts.
 * @param vibrationDurationMillis Vibration length for vibration alerts.
 * @param vibrateWithSounds Whether sound alerts should also vibrate.
 * @param cueModes Per-cue alert mode overrides.
 * @param cueRepeatCounts Per-cue sound/vibration repeat counts.
 */
@Serializable
data class TimingAlertPreferences(
    val globalMode: TimingAlertGlobalMode = TimingAlertGlobalMode.VIBRATION_ONLY,
    val soundVolume: Float = 1f,
    val vibrationDurationMillis: Long = DEFAULT_TIMING_CUE_VIBRATION_MS,
    val vibrateWithSounds: Boolean = false,
    val cueModes: Map<TimingCueId, TimingAlertMode> = defaultTimingCueModes(),
    val cueRepeatCounts: Map<TimingCueId, Int> = defaultTimingCueRepeatCounts(),
) {
    /**
     * Return the configured per-cue setting shown in Settings, before the global alert mode is applied.
     *
     * @param cueId The timing cue whose configured mode should be returned.
     */
    fun settingsModeFor(cueId: TimingCueId): TimingAlertMode {
        return cueModes[cueId] ?: cueId.defaultAlertMode()
    }

    /**
     * Return the repeat count for a cue, clamped to the supported range.
     *
     * @param cueId The timing cue whose repeat count should be returned.
     */
    fun repeatCountFor(cueId: TimingCueId): Int {
        return cueRepeatCounts[cueId]
            ?.coerceIn(MIN_TIMING_ALERT_REPEAT_COUNT, MAX_TIMING_ALERT_REPEAT_COUNT)
            ?: cueId.defaultRepeatCount()
    }

    /**
     * Return the effective alert mode to use when a timing cue fires.
     *
     * @param cueId The timing cue being delivered.
     */
    fun alertModeFor(cueId: TimingCueId): TimingAlertMode {
        val configuredMode = settingsModeFor(cueId)
        return when (globalMode) {
            TimingAlertGlobalMode.OFF -> TimingAlertMode.NONE
            TimingAlertGlobalMode.VIBRATION_ONLY -> {
                if (configuredMode == TimingAlertMode.NONE) TimingAlertMode.NONE else TimingAlertMode.VIBRATE
            }
            TimingAlertGlobalMode.SOUNDS_ON -> configuredMode
        }
    }

    /**
     * Return this timing-alert configuration with the global alert mode replaced.
     *
     * @param mode The global mode controlling whether cues are off, vibration-only, or sound-enabled.
     */
    fun withGlobalMode(mode: TimingAlertGlobalMode): TimingAlertPreferences {
        return copy(globalMode = mode)
    }

    /**
     * Return this timing-alert configuration with playback volume replaced.
     *
     * @param volume The new sound volume value from settings.
     */
    fun withSoundVolume(volume: Float): TimingAlertPreferences {
        return copy(soundVolume = volume)
    }

    /**
     * Return this timing-alert configuration with vibration length replaced.
     *
     * @param durationMillis The requested vibration duration in milliseconds.
     */
    fun withVibrationDuration(durationMillis: Long): TimingAlertPreferences {
        return copy(vibrationDurationMillis = durationMillis)
    }

    /**
     * Return this timing-alert configuration with the sound-plus-vibration toggle replaced.
     *
     * @param vibrateWithSounds Whether vibration should accompany sound alerts.
     */
    fun withVibrateWithSounds(vibrateWithSounds: Boolean): TimingAlertPreferences {
        return copy(vibrateWithSounds = vibrateWithSounds)
    }

    /**
     * Return this timing-alert configuration with one cue's alert mode replaced.
     *
     * @param cueId The cue whose alert mode should change.
     * @param mode The cue-specific alert mode selected in settings.
     */
    fun withCueMode(cueId: TimingCueId, mode: TimingAlertMode): TimingAlertPreferences {
        return copy(
            cueModes = cueModes + (cueId to mode),
            cueRepeatCounts = if (mode == TimingAlertMode.NONE) {
                cueRepeatCounts + (cueId to 1)
            } else {
                cueRepeatCounts
            },
        )
    }

    /**
     * Return this timing-alert configuration with one cue's repeat count replaced.
     *
     * @param cueId The cue whose repeat count should change.
     * @param repeatCount The requested repeat count, required to be within the supported range.
     */
    fun withCueRepeatCount(cueId: TimingCueId, repeatCount: Int): TimingAlertPreferences {
        require(repeatCount in MIN_TIMING_ALERT_REPEAT_COUNT..MAX_TIMING_ALERT_REPEAT_COUNT) {
            "Timing alert repeat count must be between $MIN_TIMING_ALERT_REPEAT_COUNT and " +
                "$MAX_TIMING_ALERT_REPEAT_COUNT."
        }
        return copy(cueRepeatCounts = cueRepeatCounts + (cueId to repeatCount))
    }

    /// Return this timing-alert configuration with all per-cue settings restored to defaults.
    fun withDefaultCueSettings(): TimingAlertPreferences {
        return copy(
            cueModes = defaultTimingCueModes(),
            cueRepeatCounts = defaultTimingCueRepeatCounts(),
        )
    }
}

/// Build the default timing-alert mode map for every cue.
internal fun defaultTimingCueModes(): Map<TimingCueId, TimingAlertMode> {
    return TimingCueId.entries.associateWith { it.defaultAlertMode() }
}

/// Build the default repeat-count map for every timing cue.
internal fun defaultTimingCueRepeatCounts(): Map<TimingCueId, Int> {
    return TimingCueId.entries.associateWith { it.defaultRepeatCount() }
}

/// Return the default alert mode for this cue before global settings are applied.
internal fun TimingCueId.defaultAlertMode(): TimingAlertMode {
    return when (this) {
        TimingCueId.RECEIVING_TWENTY_FOR_HAND,
        TimingCueId.RECEIVING_TEN_FOR_HAND,
        TimingCueId.OFFENSE_TWENTY,
        TimingCueId.OFFENSE_TEN,
        -> TimingAlertMode.TICK
        TimingCueId.TIMEOUT_CLEAR_FIELD,
        TimingCueId.TIMEOUT_BETWEEN_POINTS_ONE_MINUTE_FOR_HAND,
        TimingCueId.TIMEOUT_BETWEEN_POINTS_ONE_MINUTE_TO_PULL,
        TimingCueId.HALFTIME_OVER,
        -> TimingAlertMode.BEEP
        TimingCueId.HALFTIME_FIVE_MINUTES,
        TimingCueId.HALFTIME_TWO_MINUTES,
        -> TimingAlertMode.KNOCK
        TimingCueId.HALF_CAP,
        TimingCueId.SOFT_CAP,
        TimingCueId.HARD_CAP,
        -> TimingAlertMode.DING
        TimingCueId.PULLING_TWENTY_TO_PULL,
        TimingCueId.PULLING_TEN_TO_PULL,
        TimingCueId.DEFENSE_TWENTY,
        TimingCueId.DEFENSE_TEN,
        -> TimingAlertMode.VIBRATE
        else -> TimingAlertMode.NONE
    }
}

/// Return the default sound/vibration repeat count for a cue.
internal fun TimingCueId.defaultRepeatCount(): Int {
    return when (this) {
        TimingCueId.RECEIVING_TWENTY_FOR_HAND,
        TimingCueId.OFFENSE_TWENTY,
        TimingCueId.HALFTIME_FIVE_MINUTES,
        TimingCueId.HALFTIME_TWO_MINUTES,
        TimingCueId.HALF_CAP,
        TimingCueId.SOFT_CAP,
        -> 2
        TimingCueId.HARD_CAP -> 3
        else -> 1
    }
}

const val MIN_TIMING_CUE_VIBRATION_MS = 100L
const val MAX_TIMING_CUE_VIBRATION_MS = 500L
const val DEFAULT_TIMING_CUE_VIBRATION_MS = 360L
const val MIN_TIMING_ALERT_REPEAT_COUNT = 1
const val MAX_TIMING_ALERT_REPEAT_COUNT = 3

/**
 * Amount of rule guidance shown by live-game confirmations and notices.
 *
 * @param label Short name shown by the Settings selector.
 * @param description Explanation shown for the selected mode.
 */
@Serializable
internal enum class RuleGuidanceMode(
    val label: String,
    val description: String,
) {
    FULL(
        "Full",
        "Show a short, but fairly complete, summary of the restart location, timings, and " +
        "other relevant rules and wait for confirmation.",
    ),
    BRIEF(
        "Brief",
        "Show only a brief rule reminder and wait for confirmation.",
    ),
    TIMED(
        "Timed",
        "Show a brief reminder and automatically accept or close after 5 seconds.",
    ),
    NONE(
        "None",
        "Skip optional reminders. Some required notices still appear, but close after 5 seconds.",
    ),
    ;

    /// Report whether popup copy should use the concise presentation.
    fun usesBriefGuidance(): Boolean = this != FULL

    /**
     * Choose how one live-game guidance popup should be presented.
     *
     * @param requiredInNone Whether None mode must retain the popup long enough for the observer
     * to see it or choose an alternative action.
     */
    fun presentation(requiredInNone: Boolean): RuleGuidancePresentation {
        return when (this) {
            FULL, BRIEF -> RuleGuidancePresentation.VISIBLE
            TIMED -> RuleGuidancePresentation.VISIBLE_TIMED
            NONE -> if (requiredInNone) {
                RuleGuidancePresentation.VISIBLE_TIMED
            } else {
                RuleGuidancePresentation.HIDDEN_AUTO_ACCEPT
            }
        }
    }
}

/// Presentation selected for one live-game rule-guidance popup.
internal enum class RuleGuidancePresentation {
    VISIBLE,
    VISIBLE_TIMED,
    HIDDEN_AUTO_ACCEPT,
}

/**
 * User settings stored as one persistence bucket.
 *
 * @param ruleGuidanceMode Amount and duration of rule guidance shown during games.
 * @param automaticallyAdvanceCountdowns Whether expired countdowns should drive model transitions.
 * @param automaticallyLockLivePoint Whether automatic live-point entry should lock the live screen.
 * @param showDefenseCountdowns Whether timeout offense-set expirations wait for defense.
 * @param showAbbaRatioAsSequence Whether ABBA field badges should show sequence shorthand.
 * @param fourMenThreeWomenBadgeColorArgb Background color for 4M/3W field badges.
 * @param fourWomenThreeMenBadgeColorArgb Background color for 4W/3M field badges.
 * @param timingAlerts User-configurable timing cue alert behavior.
 */
@Serializable
internal data class Settings(
    val ruleGuidanceMode: RuleGuidanceMode = RuleGuidanceMode.FULL,
    val automaticallyAdvanceCountdowns: Boolean = true,
    val automaticallyLockLivePoint: Boolean = true,
    val showDefenseCountdowns: Boolean = false,
    val showAbbaRatioAsSequence: Boolean = true,
    val fourMenThreeWomenBadgeColorArgb: Long = TeamColorChoice.BLUE.accentArgb,
    val fourWomenThreeMenBadgeColorArgb: Long = TeamColorChoice.RED.accentArgb,
    val timingAlerts: TimingAlertPreferences = TimingAlertPreferences(),
) {
    /// Return these settings with the live-game rule-guidance mode replaced.
    fun withRuleGuidanceMode(mode: RuleGuidanceMode): Settings {
        return copy(ruleGuidanceMode = mode)
    }

    /**
     * Return these settings with automatic countdown advancement replaced.
     *
     * @param automaticallyAdvance Whether timer expiry should drive model transitions.
     */
    fun withAutomaticallyAdvanceCountdowns(automaticallyAdvance: Boolean): Settings {
        return copy(automaticallyAdvanceCountdowns = automaticallyAdvance)
    }

    /**
     * Return these settings with automatic live-point locking replaced.
     *
     * @param automaticallyLock Whether automatic live-point entry should enable lock mode.
     */
    fun withAutomaticallyLockLivePoint(automaticallyLock: Boolean): Settings {
        return copy(automaticallyLockLivePoint = automaticallyLock)
    }

    /**
     * Return these settings with the defense-check countdown setting replaced.
     *
     * @param showDefenseCountdowns Whether to require the observer to start the defense countdown.
     */
    fun withShowDefenseCountdowns(showDefenseCountdowns: Boolean): Settings {
        return copy(showDefenseCountdowns = showDefenseCountdowns)
    }

    /**
     * Return these settings with ABBA field-badge display style replaced.
     *
     * @param showAsSequence Whether ABBA badges should display M1/M2/W1/W2 shorthand.
     */
    fun withShowAbbaRatioAsSequence(showAsSequence: Boolean): Settings {
        return copy(showAbbaRatioAsSequence = showAsSequence)
    }

    /**
     * Return these settings with one gender-ratio field-badge color replaced.
     *
     * @param ratio The ratio whose badge color should change.
     * @param colorArgb The new opaque ARGB background color.
     */
    fun withGenderRatioBadgeColor(ratio: GenderRatio, colorArgb: Long): Settings {
        return when (ratio) {
            GenderRatio.FOUR_MEN_THREE_WOMEN -> copy(
                fourMenThreeWomenBadgeColorArgb = colorArgb,
            )
            GenderRatio.FOUR_WOMEN_THREE_MEN -> copy(
                fourWomenThreeMenBadgeColorArgb = colorArgb,
            )
        }
    }

    /// Return the configured field-badge color for one gender ratio.
    fun genderRatioBadgeColorArgb(ratio: GenderRatio): Long {
        return when (ratio) {
            GenderRatio.FOUR_MEN_THREE_WOMEN -> fourMenThreeWomenBadgeColorArgb
            GenderRatio.FOUR_WOMEN_THREE_MEN -> fourWomenThreeMenBadgeColorArgb
        }
    }

    /**
     * Return these settings with timing-alert preferences replaced.
     *
     * @param timingAlerts The timing-alert preferences to use.
     */
    fun withTimingAlerts(timingAlerts: TimingAlertPreferences): Settings {
        return copy(timingAlerts = timingAlerts)
    }

    companion object {
        /**
         * Decode persisted settings state for a known storage version.
         *
         * @param jsonElement The parsed payload JSON from the settings bucket.
         * @param version The version metadata read from that JSON object.
         */
        fun decodeJson(
            jsonElement: JsonElement,
            version: AppVersion,
        ): PersistenceDecodeResult<Settings>? {
            return try {
                val migrated = migrateSettingsJson(jsonElement, version) ?: return null
                val settings = appStateJson.decodeFromJsonElement<Settings>(migrated.jsonElement)
                PersistenceDecodeResult(
                    value = settings,
                    wasMigrated = migrated.wasMigrated,
                )
            } catch (_: RuntimeException) {
                null
            }
        }
    }
}
