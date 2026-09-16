package rmjarvis.ultiobserver

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

internal const val DEFAULT_NEW_COUNTDOWN_ADVANCE_SECONDS = 3
internal const val MAX_NEW_COUNTDOWN_ADVANCE_SECONDS = 10

/** Protection used for touch actions on the phone's active-game screen. */
@Serializable
internal enum class AccidentalTouchProtection(val label: String, val description: String) {
    NONE(
        "None",
        "Use normal taps on the active game screen. It will not automatically lock. " +
        "You can still lock the screen manually by clicking the lock icon in the central " +
        "region of the screen.",
    ),
    AUTO_LOCK(
        "Auto-lock",
        "The screen will automatically lock whenever play becomes live.",
    ),
    LONG_PRESS(
        "Long press",
        "Press and hold controls on the active game screen and in dialogs that " +
        "appear automatically. Use normal taps on dialogs that you open.",
    ),
}

/** Require a hold for an automatic game prompt unless a user-opened dialog takes precedence. */
internal fun AccidentalTouchProtection.requiresLongPressForDialog(
    hasPendingGameDecision: Boolean,
    hasActiveCardEntry: Boolean,
    showEventLogSheet: Boolean,
    hasTeamInfoSheet: Boolean,
    showRulesReference: Boolean,
    hasPendingTimeoutConfirmation: Boolean,
    hasPendingTimeViolation: Boolean,
    hasPendingPullViolation: Boolean,
    hasPendingTechnicalFoul: Boolean,
): Boolean {
    return this == AccidentalTouchProtection.LONG_PRESS &&
        hasPendingGameDecision && !hasActiveCardEntry &&
        !showEventLogSheet && !hasTeamInfoSheet && !showRulesReference &&
        !hasPendingTimeoutConfirmation && !hasPendingTimeViolation &&
        !hasPendingPullViolation && !hasPendingTechnicalFoul
}

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

/** How UltiObserver communicates timing and game state to a watch. */
@Serializable
enum class WatchConnectionMode(
    val label: String,
    val description: String,
) {
    OFF(
        "Off",
        "No notifications will be sent to a watch.",
    ),
    SILENT(
        "Silent",
        "Timing cues will be sent to a paired watch, but no alerts will be " +
        "triggered.",
    ),
    ALERTING(
        "Alerting",
        "Timing cues will be sent to a paired watch. Cues whose individual " +
        "setting is not Off will also trigger an alert, causing a vibration if " +
        "enabled on the watch.",
    ),
    WEAR_OS(
        "Wear OS",
        "Use UltiObserver on a paired Wear OS watch.",
    ),
    ;

    /// Report whether standard Android notifications should be bridged to a watch.
    fun usesNotifications(): Boolean {
        return this == SILENT || this == ALERTING
    }

    companion object {
        const val NOTIFICATION_PERMISSION_NOTE =
            "Phone notifications must be enabled for watch notifications to work. " +
            "Selecting either Silent or Alerting will open the Android settings " +
            "page where you can enable notifications."

        const val NOTIFICATION_SHARING_WARNING =
            "Watch notifications require a paired watch and notification sharing " +
            "enabled in its companion app. UltiObserver cannot verify the " +
            "connection."

        const val NOTIFICATION_COOLDOWN_WARNING =
            "By default Android applies a \"cooldown\" to repeated notifications, " +
            "which lessens the vibration strength for alerts after the first one. " +
            "To turn it off, go to Settings — Notifications — Notification cooldown."

        const val CONNECTED_WEAR_DESCRIPTION =
            "This will let you receive timing cues on the watch as well as " +
            "record goals, timeouts and other events directly from the watch. "

        const val WATCH_VIBRATION_DESCRIPTION =
            "Any cues set to use vibration will happen on the watch rather than " +
            "on the phone."

        const val UNAVAILABLE_WEAR_TITLE =
            "No Wear OS watch is currently available.  "

        const val UNAVAILABLE_WEAR_DESCRIPTION =
            "Pair a Wear OS watch with this phone before using this setting."
    }
}

/**
 * User-configurable timing alert behavior.
 *
 * @param globalMode The app-wide mode controlling whether alerts are off, vibration-only, or sound-enabled.
 * @param soundVolume Playback volume for sound alerts.
 * @param vibrationDurationMillis Vibration length for vibration alerts.
 * @param vibrateWithSounds Whether sound alerts should also vibrate.
 * @param watchConnectionMode How UltiObserver communicates with a watch.
 * @param cueModes Per-cue alert mode overrides.
 * @param cueRepeatCounts Per-cue sound/vibration repeat counts.
 */
@Serializable
data class TimingAlertPreferences(
    val globalMode: TimingAlertGlobalMode = TimingAlertGlobalMode.VIBRATION_ONLY,
    val soundVolume: Float = 1f,
    val vibrationDurationMillis: Long = DEFAULT_TIMING_CUE_VIBRATION_MS,
    val vibrateWithSounds: Boolean = false,
    val watchConnectionMode: WatchConnectionMode = WatchConnectionMode.OFF,
    val vibrateOnWatch: Boolean = true,
    val cueModes: Map<TimingCueId, TimingAlertMode> = defaultTimingCueModes(),
    val cueRepeatCounts: Map<TimingCueId, Int> = defaultTimingCueRepeatCounts(),
) {
    /// Explanation shown below the vibrateOnWatch setting.
    val vibrateOnWatchDescription: String
        get() = if (vibrateOnWatch) {
            "Send timing vibrations to the watch, including when its screen is off. " +
            "If the watch is unavailable, vibrate on the phone instead."
        } else {
            "Keep timing vibrations on the phone."
        }

    /// Explanation beside the vibration test control.
    val vibrationTestDescription: String
        get() = if (usesWatchVibration()) {
            "If the test vibration is too weak, check the vibration strength " +
            "in your watch's settings. If the watch is unavailable, the phone vibrates."
        } else {
            "If the test vibration is too weak, check the vibration strength " +
            "in your phone's haptic settings."
        }

    /** Whether timing vibrations should try the native watch before the phone. */
    fun usesWatchVibration(): Boolean {
        return watchConnectionMode == WatchConnectionMode.WEAR_OS && vibrateOnWatch
    }

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
     * Return whether a cue should be sent to a watch.
     *
     * Individually Off cues are skipped except at the end of a countdown, when a silent update
     * must return the watch notification to the score.
     *
     * @param cueId The timing cue being considered for watch delivery.
     * @param countdownSeconds Countdown value for this cue occurrence, or null for a cap cue.
     */
    fun sendsCueToWatch(cueId: TimingCueId, countdownSeconds: Int?): Boolean {
        return watchConnectionMode.usesNotifications() &&
            (countdownSeconds == 0 || settingsModeFor(cueId) != TimingAlertMode.NONE)
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

    /** Return this configuration with the watch-connection mode replaced. */
    fun withWatchConnectionMode(mode: WatchConnectionMode): TimingAlertPreferences {
        return copy(watchConnectionMode = mode)
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
        TimingCueId.PULLING_TWENTY_TO_PULL,
        TimingCueId.PULLING_TEN_TO_PULL,
        TimingCueId.OFFENSE_TWENTY,
        TimingCueId.OFFENSE_TEN,
        TimingCueId.DEFENSE_TWENTY,
        TimingCueId.DEFENSE_TEN,
        -> TimingAlertMode.TICK
        TimingCueId.RECEIVING_GIVE_HAND,
        TimingCueId.PULLING_TIME_VIOLATION,
        TimingCueId.OFFENSE_SET_LIMIT,
        TimingCueId.DEFENSE_CHECK_LIMIT,
        TimingCueId.TIMEOUT_CLEAR_FIELD,
        TimingCueId.TIMEOUT_BETWEEN_POINTS_ONE_MINUTE_FOR_HAND,
        TimingCueId.TIMEOUT_BETWEEN_POINTS_ONE_MINUTE_TO_PULL,
        TimingCueId.PRE_GAME_ONE_MINUTE,
        TimingCueId.HALFTIME_OVER,
        -> TimingAlertMode.BEEP
        TimingCueId.HALFTIME_TWO_MINUTES,
        TimingCueId.PRE_GAME_FIVE_MINUTES,
        TimingCueId.PRE_GAME_THREE_MINUTES,
        -> TimingAlertMode.KNOCK
        TimingCueId.HALF_CAP,
        TimingCueId.SOFT_CAP,
        TimingCueId.HARD_CAP,
        -> TimingAlertMode.DING
        else -> TimingAlertMode.NONE
    }
}

/// Return the default sound/vibration repeat count for a cue.
internal fun TimingCueId.defaultRepeatCount(): Int {
    return when (this) {
        TimingCueId.RECEIVING_TWENTY_FOR_HAND,
        TimingCueId.PULLING_TWENTY_TO_PULL,
        TimingCueId.OFFENSE_TWENTY,
        TimingCueId.DEFENSE_TWENTY,
        TimingCueId.PRE_GAME_FIVE_MINUTES,
        TimingCueId.HALFTIME_TWO_MINUTES,
        TimingCueId.HALF_CAP,
        TimingCueId.SOFT_CAP,
        -> 2
        TimingCueId.PRE_GAME_THREE_MINUTES,
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
 * Orientation behavior selected for the active-game screen.
 *
 * @param label Short name shown by the Settings selector.
 * @param description Explanation shown for the selected behavior.
 */
@Serializable
internal enum class OrientationPreference(
    val label: String,
    val description: String,
) {
    PORTRAIT(
        "Portrait",
        "Show teams at the top and bottom of the active game screen.",
    ),
    LANDSCAPE(
        "Landscape",
        "Show teams on the left and right of the active game screen.",
    ),
    AUTO_ROTATE(
        "Auto-rotate",
        "Follow the phone's orientation if Android's auto-rotate is enabled. " +
        "Otherwise, it will use the current phone orientation when the active " +
        "game screen opens each time.",
    ),
}

/// Portrait or landscape orientation currently used to display the active-game screen.
internal enum class ActiveGameOrientation {
    PORTRAIT,
    LANDSCAPE,
}

/**
 * Readable orientation currently shown by Android.
 *
 * The landscape names describe where the physical top of a naturally portrait phone appears on
 * the rendered screen.
 */
internal enum class ActiveGameFullOrientation(val orientation: ActiveGameOrientation) {
    PORTRAIT(ActiveGameOrientation.PORTRAIT),
    LANDSCAPE_PHONE_TOP_AT_LEFT(ActiveGameOrientation.LANDSCAPE),
    REVERSE_PORTRAIT(ActiveGameOrientation.PORTRAIT),
    LANDSCAPE_PHONE_TOP_AT_RIGHT(ActiveGameOrientation.LANDSCAPE),
}

/**
 * Concrete active-game display choices derived from the setting and current phone orientation.
 *
 * @param orientation Current Portrait or Landscape geometry used to render the screen.
 * @param layout Field-end arrangement used for Near/Far or Left/Right labels. This remains
 * Portrait for Auto-rotate even when the current screen orientation is Landscape.
 * @param topOrLeftDisplayedEnd Field end shown at the top in Portrait or left in Landscape.
 */
internal data class ActiveGameDisplay(
    val orientation: ActiveGameOrientation,
    val layout: ActiveGameOrientation,
    val topOrLeftDisplayedEnd: FieldEnd,
)

/**
 * Resolve this orientation behavior for the phone's current readable orientation.
 *
 * @param displayOrientation Readable orientation currently shown by Android.
 * @param phoneTopEnd Field end attached to the physical top of the phone.
 */
internal fun OrientationPreference.displayFor(
    displayOrientation: ActiveGameFullOrientation,
    phoneTopEnd: FieldEnd,
): ActiveGameDisplay {
    val orientation = when (this) {
        OrientationPreference.PORTRAIT -> ActiveGameOrientation.PORTRAIT
        OrientationPreference.LANDSCAPE -> ActiveGameOrientation.LANDSCAPE
        OrientationPreference.AUTO_ROTATE -> displayOrientation.orientation
    }
    val layout = if (this == OrientationPreference.LANDSCAPE) {
        ActiveGameOrientation.LANDSCAPE
    } else {
        ActiveGameOrientation.PORTRAIT
    }
    val topOrLeftDisplayedEnd = if (this != OrientationPreference.AUTO_ROTATE) {
        phoneTopEnd
    } else {
        when (displayOrientation) {
            ActiveGameFullOrientation.PORTRAIT,
            ActiveGameFullOrientation.LANDSCAPE_PHONE_TOP_AT_LEFT -> phoneTopEnd
            ActiveGameFullOrientation.REVERSE_PORTRAIT,
            ActiveGameFullOrientation.LANDSCAPE_PHONE_TOP_AT_RIGHT -> phoneTopEnd.flip()
        }
    }
    return ActiveGameDisplay(
        orientation = orientation,
        layout = layout,
        topOrLeftDisplayedEnd = topOrLeftDisplayedEnd,
    )
}

/** Which placement remains fixed on the watch as teams change ends. */
@Serializable
internal enum class WatchOrientation(val label: String, val description: String) {
    TEAMS_FIXED(
        "Teams fixed",
        "Keep Team 1 on the left and Team 2 on the right on the watch game screen."
    ),
    ENDS_FIXED(
        "Ends fixed",
        "Keep field ends fixed on the watch game screen as teams change ends. " +
        "In Landscape mode, match the phone display orientation. " +
        "In Portrait or Auto-rotate, choose the orientation in game setup.",
    ),
}

/**
 * User settings stored as one persistence bucket.
 *
 * @param orientationPreference Orientation behavior used by the active-game screen.
 * @param ruleGuidanceMode Amount and duration of rule guidance shown during games.
 * @param automaticallyAdvanceCountdowns Whether expired countdowns should drive model transitions.
 * @param accidentalTouchProtection How active-game controls guard against accidental touches.
 * @param showDefenseCountdowns Whether timeout offense-set expirations wait for defense.
 * @param automaticallyAdvanceNewCountdowns Whether newly started countdowns should compensate for
 * the delay before the observer presses the relevant button.
 * @param newCountdownAdvanceSeconds How many seconds should already be elapsed when an adjusted
 * countdown begins.
 * @param showAbbaRatioAsSequence Whether ABBA field badges should show sequence shorthand.
 * @param fourMenThreeWomenBadgeColorArgb Background color for 4M/3W field badges.
 * @param fourWomenThreeMenBadgeColorArgb Background color for 4W/3M field badges.
 * @param officialClockOffsetMillis Offset added to phone wall time for official tournament time.
 * @param timingAlerts User-configurable timing cue alert behavior.
 */
@Serializable
internal data class Settings(
    val orientationPreference: OrientationPreference = OrientationPreference.PORTRAIT,
    val watchOrientation: WatchOrientation = WatchOrientation.TEAMS_FIXED,
    val ruleGuidanceMode: RuleGuidanceMode = RuleGuidanceMode.FULL,
    val automaticallyAdvanceCountdowns: Boolean = true,
    val accidentalTouchProtection: AccidentalTouchProtection = AccidentalTouchProtection.AUTO_LOCK,
    val requireWatchLongPress: Boolean = false,
    val showDefenseCountdowns: Boolean = false,
    val automaticallyAdvanceNewCountdowns: Boolean = false,
    val newCountdownAdvanceSeconds: Int = DEFAULT_NEW_COUNTDOWN_ADVANCE_SECONDS,
    val showAbbaRatioAsSequence: Boolean = true,
    val fourMenThreeWomenBadgeColorArgb: Long = TeamColorChoice.BLUE.accentArgb,
    val fourWomenThreeMenBadgeColorArgb: Long = TeamColorChoice.RED.accentArgb,
    val officialClockOffsetMillis: Long = 0L,
    val timingAlerts: TimingAlertPreferences = TimingAlertPreferences(),
) {
    /// Explanation shown below the requireWatchLongPress setting.
    val requireWatchLongPressDescription: String
        get() = if (requireWatchLongPress) {
            "Press and hold controls on the watch's main game screen. " +
            "After opening a menu or another screen, use normal taps."
        } else {
            "Use normal taps on the watch. Note: Undo and Redo always require a long press."
        }

    /// Explanation shown below the automaticallyAdvanceCountdowns setting.
    val automaticallyAdvanceCountdownsDescription: String
        get() = if (automaticallyAdvanceCountdowns) {
            "When a pull or timeout countdown expires, UltiObserver will automatically " +
            "start or resume live play."
        } else {
            "When a countdown expires, UltiObserver will wait for you to tap Start point " +
            "or Continue point."
        }

    /// Explanation shown below the showDefenseCountdowns setting.
    val showDefenseCountdownsDescription: String
        get() = if (showDefenseCountdowns) {
            "After you mark the offense set during a timeout or misconduct penalty, " +
            "UltiObserver will display the 20-second defense countdown."
        } else {
            "UltiObserver will not display the defense countdown for timeouts or " +
            "misconduct penalties. You should count the time for the defensive check " +
            "yourself with arm chops."
        }

    /// Explanation shown below the automaticallyAdvanceNewCountdowns setting.
    val automaticallyAdvanceNewCountdownsDescription: String
        get() {
            val secondsLabel = if (newCountdownAdvanceSeconds == 1) "second" else "seconds"
            return if (automaticallyAdvanceNewCountdowns) {
                "Countdowns after a goal or in-point timeout will automatically begin " +
                "with ${newCountdownAdvanceSeconds} $secondsLabel already " +
                "elapsed to account for the time it takes to open your phone and " +
                "press the button."
            } else {
                "Countdowns start when you press the relevant button, and they take the " +
                "full time."
            }
        }

    /// Explanation shown below the showAbbaRatioAsSequence setting.
    val showAbbaRatioAsSequenceDescription: String
        get() = if (showAbbaRatioAsSequence) {
            "Ratio will display as W2, M1, M2, W1, W2... or M2, W1, W2, M1, M2..."
        } else {
            "Ratio will display as either 4W/3M or 4M/3W."
        }

    val automaticallyLockLivePoint: Boolean
        get() = accidentalTouchProtection == AccidentalTouchProtection.AUTO_LOCK

    /// Return these settings with the active-game orientation preference replaced.
    fun withOrientationPreference(preference: OrientationPreference): Settings {
        return copy(orientationPreference = preference)
    }

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
     * Return these settings with the defense-check countdown setting replaced.
     *
     * @param showDefenseCountdowns Whether to require the observer to start the defense countdown.
     */
    fun withShowDefenseCountdowns(showDefenseCountdowns: Boolean): Settings {
        return copy(showDefenseCountdowns = showDefenseCountdowns)
    }

    /**
     * Return these settings with automatic advancement of newly started countdowns replaced.
     *
     * @param automaticallyAdvance Whether supported countdowns should begin partly elapsed.
     */
    fun withAutomaticallyAdvanceNewCountdowns(automaticallyAdvance: Boolean): Settings {
        return copy(automaticallyAdvanceNewCountdowns = automaticallyAdvance)
    }

    /**
     * Return these settings with the advancement for newly started countdowns replaced.
     *
     * @param seconds The number of seconds already elapsed, from one through ten.
     */
    fun withNewCountdownAdvanceSeconds(seconds: Int): Settings {
        require(seconds in 1..MAX_NEW_COUNTDOWN_ADVANCE_SECONDS)
        return copy(newCountdownAdvanceSeconds = seconds)
    }

    /// Adjust a button-press epoch as though the observer pressed it the configured time earlier.
    fun adjustedCountdownStartEpoch(now: Long): Long {
        return if (automaticallyAdvanceNewCountdowns) {
            now - newCountdownAdvanceSeconds * 1_000L
        } else {
            now
        }
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
        const val DEFENSE_COUNTDOWN_DISABLED_NOTE =
            "Note — defensive check countdowns are not currently enabled. " +
            "If you want these cues, enable defensive check countdowns " +
            "on the previous page."

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


/// Return the explanatory note to show beside sound previews under the current global mode.
internal fun TimingAlertPreferences.soundPreviewNote(hasTimingCueHaptics: Boolean): String? {
    if (globalMode == TimingAlertGlobalMode.SOUNDS_ON) {
        return null
    }
    val vibrateInsteadSentence = if (vibrateWithSounds && hasTimingCueHaptics) {
        val device = if (usesWatchVibration()) "watch" else "phone"
        " The $device will currently vibrate instead for any cues with sounds."
    } else {
        ""
    }
    return (
        "Note — sounds are currently not enabled.$vibrateInsteadSentence " +
        "If you want sounds, enable them on the previous page."
    )
}


/// Return the settings-page messages for a global timing-alert mode and haptic capability.
internal fun TimingAlertGlobalMode.settingsMessages(hasTimingCueHaptics: Boolean): List<String> {
    if (!hasTimingCueHaptics) {
        val noHapticsMessage =
            "This phone reports that vibration is unavailable. " +
            "Check Android Settings > Sound & vibration > Vibration & haptics, then return to UltiObserver."
        return when (this) {
            TimingAlertGlobalMode.OFF -> listOf("No sound or vibration will be used for any timing cues.")
            TimingAlertGlobalMode.VIBRATION_ONLY -> listOf(noHapticsMessage)
            TimingAlertGlobalMode.SOUNDS_ON -> listOf(
                "Ear buds are recommended when using sounds with UltiObserver.",
                noHapticsMessage,
            )
        }
    }
    return when (this) {
        TimingAlertGlobalMode.OFF -> listOf("No sound or vibration will be used for any timing cues.")
        TimingAlertGlobalMode.VIBRATION_ONLY -> {
            listOf("Vibration will be used for any cues that are set to use sound.")
        }
        TimingAlertGlobalMode.SOUNDS_ON -> listOf("Ear buds are recommended when using sounds with UltiObserver.")
    }
}
