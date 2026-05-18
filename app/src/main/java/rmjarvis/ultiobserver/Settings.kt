package rmjarvis.ultiobserver

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

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

@Serializable
enum class TimingAlertSound(
    val label: String,
) {
    TICK("Tick"),
    BEEP("Beep"),
    KNOCK("Knock"),
    DING("Ding"),
}

enum class TimingAlertGlobalMode(
    val label: String,
) {
    OFF("Off"),
    VIBRATION_ONLY("Vibration Only"),
    SOUNDS_ON("Sounds On"),
}

@Serializable
data class TimingAlertPreferences(
    val globalMode: TimingAlertGlobalMode = TimingAlertGlobalMode.VIBRATION_ONLY,
    val soundVolume: Float = 0.5f,
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
        TimingCueId.TIMEOUT_OFFENSE_TWENTY,
        TimingCueId.TIMEOUT_OFFENSE_TEN,
        TimingCueId.MISCONDUCT_OFFENSE_TWENTY,
        TimingCueId.MISCONDUCT_OFFENSE_TEN,
        -> TimingAlertMode.TICK
        TimingCueId.TIMEOUT_CLEAR_FIELD,
        TimingCueId.TIMEOUT_BETWEEN_POINTS_ONE_MINUTE_FOR_HAND,
        TimingCueId.TIMEOUT_BETWEEN_POINTS_ONE_MINUTE_TO_PULL,
        -> TimingAlertMode.BEEP
        TimingCueId.HALFTIME_FIVE_MINUTES,
        TimingCueId.HALFTIME_TWO_MINUTES,
        -> TimingAlertMode.KNOCK
        TimingCueId.HALF_CAP,
        TimingCueId.SOFT_CAP,
        TimingCueId.HARD_CAP,
        -> TimingAlertMode.DING
        TimingCueId.PULLING_TWENTY_TO_PULL,
        TimingCueId.MISCONDUCT_DEFENSE_TWENTY,
        -> TimingAlertMode.VIBRATE
        else -> TimingAlertMode.NONE
    }
}

/// Return the default sound/vibration repeat count for a cue.
internal fun TimingCueId.defaultRepeatCount(): Int {
    return when (this) {
        TimingCueId.RECEIVING_TWENTY_FOR_HAND,
        TimingCueId.TIMEOUT_OFFENSE_TWENTY,
        TimingCueId.MISCONDUCT_OFFENSE_TWENTY,
        TimingCueId.HALFTIME_FIVE_MINUTES,
        TimingCueId.HALFTIME_TWO_MINUTES,
        TimingCueId.HALF_CAP,
        TimingCueId.SOFT_CAP,
        -> 2
        TimingCueId.HARD_CAP -> 3
        else -> DEFAULT_TIMING_ALERT_REPEAT_COUNT
    }
}

const val MIN_TIMING_CUE_VIBRATION_MS = 100L
const val MAX_TIMING_CUE_VIBRATION_MS = 500L
const val DEFAULT_TIMING_CUE_VIBRATION_MS = 360L
const val MIN_TIMING_ALERT_REPEAT_COUNT = 1
const val MAX_TIMING_ALERT_REPEAT_COUNT = 3
const val DEFAULT_TIMING_ALERT_REPEAT_COUNT = 1

@Serializable
internal data class Settings(
    val versionName: String = APP_STATE_VERSION_NAME,
    val versionCode: Int = APP_STATE_VERSION_CODE,
    val automaticallyAdvanceCountdowns: Boolean = true,
    val automaticallyLockLivePoint: Boolean = true,
    val timingAlertPreferences: TimingAlertPreferences = TimingAlertPreferences(),
) {
    companion object {
        /**
         * Decode persisted settings state for a known storage version.
         *
         * @param jsonObject The parsed JSON object from the settings bucket.
         * @param version The version metadata read from that JSON object.
         */
        fun decodeJson(jsonObject: JsonObject, version: AppVersion): Settings? {
            // Placeholder for future version-specific decoding/migration into the current model.
            return when {
                version.versionCode == APP_STATE_VERSION_CODE -> decodeCurrentJson(jsonObject)
                else -> null
            }
        }

        /**
         * Decode current-version settings JSON, returning null when the bucket is corrupt.
         *
         * @param jsonObject The parsed settings JSON object.
         */
        private fun decodeCurrentJson(jsonObject: JsonObject): Settings? {
            return try {
                appStateJson.decodeFromJsonElement(jsonObject)
            } catch (_: RuntimeException) {
                null
            }
        }
    }
}
