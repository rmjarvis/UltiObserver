package rmjarvis.ultiobserver

import org.junit.Assert.*
import org.junit.Test
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

/** Settings defaults, updates, protection policies, and observer-facing explanations. */
class TestSettings {
    /** Connection type and notification alerting are independent saved preferences. */
    @Test
    fun watchConnectionPreferences() {
        // Notifications initially request alerts; selecting another connection retains the choice.
        val notifications = TimingAlertPreferences(
            watchConnectionMode = WatchConnectionMode.NOTIFICATIONS,
        )
        assertEquals(WatchNotificationAlertMode.VIBRATION_ONLY, notifications.watchNotificationAlertMode)
        val silent = notifications.copy(watchNotificationAlertMode = WatchNotificationAlertMode.NONE)
        val disconnected = silent.withWatchConnectionMode(WatchConnectionMode.OFF)
        assertEquals(WatchNotificationAlertMode.NONE, disconnected.watchNotificationAlertMode)
        val reconnected = disconnected.withWatchConnectionMode(WatchConnectionMode.NOTIFICATIONS)
        assertEquals(silent, reconnected)

        // Saving a disconnected preference retains None for the next notification connection.
        val serialized = appStateJson.encodeToJsonElement(disconnected)
        val restored = appStateJson.decodeFromJsonElement<TimingAlertPreferences>(serialized)
        assertEquals(disconnected, restored)
        assertEquals(WatchNotificationAlertMode.NONE,
            restored.withWatchConnectionMode(WatchConnectionMode.NOTIFICATIONS)
                .watchNotificationAlertMode)

        // Vibration-only notification alerts also survive saving and switching connections.
        val vibrationOnly = notifications.copy(
            watchNotificationAlertMode = WatchNotificationAlertMode.VIBRATION_ONLY,
        )
        val saved = appStateJson.encodeToJsonElement(vibrationOnly)
        assertEquals(vibrationOnly, appStateJson.decodeFromJsonElement<TimingAlertPreferences>(saved))
        assertEquals(vibrationOnly, vibrationOnly.withWatchConnectionMode(WatchConnectionMode.WEAR_OS)
            .withWatchConnectionMode(WatchConnectionMode.NOTIFICATIONS))

        // Valid settings that omit the optional timing-alert bucket use its ordinary defaults.
        val defaultSettings = Settings.decodeJson(
            JsonObject(emptyMap()),
            AppVersion(APP_STATE_VERSION_NAME, APP_STATE_VERSION_CODE),
        )!!
        assertEquals(Settings(), defaultSettings.value)
        assertFalse(defaultSettings.wasMigrated)
    }

    /** Vibration-only notification alerts follow the effective vibration behavior. */
    @Test
    fun watchNotificationAlerts() {
        val preferences = TimingAlertPreferences(
            globalMode = TimingAlertGlobalMode.SOUNDS_ON,
            watchConnectionMode = WatchConnectionMode.NOTIFICATIONS,
            watchNotificationAlertMode = WatchNotificationAlertMode.ALL,
            cueModes = mapOf(
                TimingCueId.HALF_CAP to TimingAlertMode.VIBRATE,
                TimingCueId.SOFT_CAP to TimingAlertMode.DING,
                TimingCueId.HARD_CAP to TimingAlertMode.NONE,
            ),
        )

        // All enabled cues includes sound cues, but Off cues stay silent.
        assertTrue(preferences.alertsCueOnWatch(TimingCueId.HALF_CAP))
        assertTrue(preferences.alertsCueOnWatch(TimingCueId.SOFT_CAP))
        assertFalse(preferences.alertsCueOnWatch(TimingCueId.HARD_CAP))

        // Vibration-only alerts leave sound cues silent without removing their notifications.
        val vibrationOnly = preferences.copy(
            watchNotificationAlertMode = WatchNotificationAlertMode.VIBRATION_ONLY,
        )
        assertTrue(vibrationOnly.alertsCueOnWatch(TimingCueId.HALF_CAP))
        assertFalse(vibrationOnly.alertsCueOnWatch(TimingCueId.SOFT_CAP))
        assertFalse(vibrationOnly.alertsCueOnWatch(TimingCueId.HARD_CAP))
        assertTrue(vibrationOnly.sendsCueToWatch(TimingCueId.SOFT_CAP, countdownSeconds = null))

        // Global Off silences watch alerts; global Vibration only includes sound cues too.
        val phoneMuted = vibrationOnly.copy(globalMode = TimingAlertGlobalMode.OFF)
        assertFalse(phoneMuted.alertsCueOnWatch(TimingCueId.HALF_CAP))
        assertFalse(phoneMuted.alertsCueOnWatch(TimingCueId.SOFT_CAP))
        assertFalse(phoneMuted.copy(vibrateWithSounds = true).alertsCueOnWatch(TimingCueId.SOFT_CAP))
        val allVibrating = vibrationOnly.copy(globalMode = TimingAlertGlobalMode.VIBRATION_ONLY)
        assertTrue(allVibrating.alertsCueOnWatch(TimingCueId.HALF_CAP))
        assertTrue(allVibrating.alertsCueOnWatch(TimingCueId.SOFT_CAP))
        assertFalse(allVibrating.alertsCueOnWatch(TimingCueId.HARD_CAP))

        // Also vibrate includes sound cues, while individually Off cues stay silent.
        val soundWithVibration = vibrationOnly.copy(vibrateWithSounds = true)
        assertTrue(soundWithVibration.alertsCueOnWatch(TimingCueId.SOFT_CAP))
        assertFalse(soundWithVibration.alertsCueOnWatch(TimingCueId.HARD_CAP))

        // None leaves even vibration cues silent, while still sending their notifications.
        val silent = preferences.copy(watchNotificationAlertMode = WatchNotificationAlertMode.NONE)
        assertFalse(silent.alertsCueOnWatch(TimingCueId.HALF_CAP))
        assertFalse(silent.alertsCueOnWatch(TimingCueId.SOFT_CAP))
        assertFalse(silent.alertsCueOnWatch(TimingCueId.HARD_CAP))
        assertTrue(silent.sendsCueToWatch(TimingCueId.HALF_CAP, countdownSeconds = null))
    }

    /**
     * Test default timing-alert preferences.
     */
    @Test
    fun timingAlertPreferenceDefaults() {
        val defaultPreferences = TimingAlertPreferences()

        // Default preferences start in vibration-only mode with full sound volume.
        assertEquals(TimingAlertGlobalMode.VIBRATION_ONLY, defaultPreferences.globalMode)
        assertEquals(WatchConnectionMode.OFF, defaultPreferences.watchConnectionMode)
        assertEquals(WatchNotificationAlertMode.VIBRATION_ONLY, defaultPreferences.watchNotificationAlertMode)
        assertEquals(1f, defaultPreferences.soundVolume, 0f)
        assertEquals("Sound volume 100%", defaultPreferences.soundVolumeLabel)
        assertFalse(defaultPreferences.vibrateWithSounds)

        // Cap alerts are considered enabled only when relevant rules and cue settings allow them.
        assertTrue(GameRules().hasEnabledCapTimingAlerts(defaultPreferences))
        assertFalse(
            GameRules(
                useHalfCap = false,
                useSoftCap = false,
                useHardCap = false,
            ).hasEnabledCapTimingAlerts(defaultPreferences)
        )
        assertFalse(
            GameRules().hasEnabledCapTimingAlerts(
                defaultPreferences.copy(globalMode = TimingAlertGlobalMode.OFF)
            )
        )
        assertTrue(
            GameRules().hasEnabledCapTimingAlerts(
                defaultPreferences.copy(
                    globalMode = TimingAlertGlobalMode.OFF,
                    watchConnectionMode = WatchConnectionMode.NOTIFICATIONS,
                    watchNotificationAlertMode = WatchNotificationAlertMode.NONE,
                )
            )
        )
        assertFalse(
            GameRules().hasEnabledCapTimingAlerts(
                defaultPreferences.copy(
                    globalMode = TimingAlertGlobalMode.OFF,
                    watchConnectionMode = WatchConnectionMode.NOTIFICATIONS,
                    watchNotificationAlertMode = WatchNotificationAlertMode.NONE,
                    cueModes = TimingCueId.entries.associateWith { TimingAlertMode.NONE },
                )
            )
        )
        assertFalse(
            GameRules().hasEnabledCapTimingAlerts(
                defaultPreferences.copy(
                    cueModes = defaultPreferences.cueModes +
                        (TimingCueId.HALF_CAP to TimingAlertMode.NONE) +
                        (TimingCueId.SOFT_CAP to TimingAlertMode.NONE) +
                        (TimingCueId.HARD_CAP to TimingAlertMode.NONE),
                )
            )
        )

        // Default cue modes match the timing alert settings screen.
        val expectedDefaultModes = mapOf(
            TimingCueId.RECEIVING_TWENTY_FOR_HAND to TimingAlertMode.TICK,
            TimingCueId.RECEIVING_TEN_FOR_HAND to TimingAlertMode.TICK,
            TimingCueId.RECEIVING_GIVE_HAND to TimingAlertMode.BEEP,
            TimingCueId.PULLING_TWENTY_TO_PULL to TimingAlertMode.TICK,
            TimingCueId.PULLING_TEN_TO_PULL to TimingAlertMode.TICK,
            TimingCueId.PULLING_TIME_VIOLATION to TimingAlertMode.BEEP,
            TimingCueId.TIMEOUT_CLEAR_FIELD to TimingAlertMode.BEEP,
            TimingCueId.OFFENSE_TWENTY to TimingAlertMode.TICK,
            TimingCueId.OFFENSE_TEN to TimingAlertMode.TICK,
            TimingCueId.OFFENSE_SET_LIMIT to TimingAlertMode.BEEP,
            TimingCueId.DEFENSE_TWENTY to TimingAlertMode.TICK,
            TimingCueId.DEFENSE_TEN to TimingAlertMode.TICK,
            TimingCueId.DEFENSE_CHECK_LIMIT to TimingAlertMode.BEEP,
            TimingCueId.TIMEOUT_BETWEEN_POINTS_ONE_MINUTE_FOR_HAND to TimingAlertMode.BEEP,
            TimingCueId.TIMEOUT_BETWEEN_POINTS_ONE_MINUTE_TO_PULL to TimingAlertMode.BEEP,
            TimingCueId.PRE_GAME_FIVE_MINUTES to TimingAlertMode.KNOCK,
            TimingCueId.PRE_GAME_THREE_MINUTES to TimingAlertMode.KNOCK,
            TimingCueId.PRE_GAME_ONE_MINUTE to TimingAlertMode.BEEP,
            TimingCueId.HALFTIME_TWO_MINUTES to TimingAlertMode.KNOCK,
            TimingCueId.HALFTIME_OVER to TimingAlertMode.BEEP,
            TimingCueId.HALF_CAP to TimingAlertMode.DING,
            TimingCueId.SOFT_CAP to TimingAlertMode.DING,
            TimingCueId.HARD_CAP to TimingAlertMode.DING,
        )
        TimingCueId.entries.forEach { cueId ->
            assertEquals(
                "Default alert mode for $cueId",
                expectedDefaultModes[cueId] ?: TimingAlertMode.NONE,
                defaultPreferences.settingsModeFor(cueId),
            )
        }

        // Default repeat counts are one unless a cue explicitly asks for repeated alerts.
        val expectedDefaultRepeatCounts = mapOf(
            TimingCueId.RECEIVING_TWENTY_FOR_HAND to 2,
            TimingCueId.PULLING_TWENTY_TO_PULL to 2,
            TimingCueId.OFFENSE_TWENTY to 2,
            TimingCueId.DEFENSE_TWENTY to 2,
            TimingCueId.PRE_GAME_FIVE_MINUTES to 2,
            TimingCueId.HALFTIME_TWO_MINUTES to 2,
            TimingCueId.PRE_GAME_THREE_MINUTES to 3,
            TimingCueId.HALF_CAP to 2,
            TimingCueId.SOFT_CAP to 2,
            TimingCueId.HARD_CAP to 3,
        )
        TimingCueId.entries.forEach { cueId ->
            assertEquals(
                "Default repeat count for $cueId",
                expectedDefaultRepeatCounts[cueId] ?: 1,
                defaultPreferences.repeatCountFor(cueId),
            )
        }
    }

    /**
     * Test timing-alert preference overrides and clamping.
     */
    @Test
    fun timingAlertPreferenceOverrides() {
        val defaultPreferences = TimingAlertPreferences()

        // Vibration-only global mode turns every configured alert into vibration.
        val vibrationDefaultCues = TimingCueId.entries.filter { cueId ->
            defaultPreferences.alertModeFor(cueId) == TimingAlertMode.VIBRATE
        }
        assertEquals(
            TimingCueId.entries.filter { cueId ->
                defaultPreferences.settingsModeFor(cueId) != TimingAlertMode.NONE
            },
            vibrationDefaultCues,
        )

        // The settings mode remains the cue-specific setting even in vibration-only mode.
        assertEquals(
            emptyList<TimingCueId>(),
            TimingCueId.entries.filter { cueId ->
                defaultPreferences.settingsModeFor(cueId) == TimingAlertMode.VIBRATE
            },
        )
        assertEquals(
            TimingAlertMode.BEEP,
            defaultPreferences.copy(
                globalMode = TimingAlertGlobalMode.SOUNDS_ON,
                cueModes = defaultPreferences.cueModes +
                    (TimingCueId.PULLING_TIME_VIOLATION to TimingAlertMode.BEEP),
            )
                .alertModeFor(TimingCueId.PULLING_TIME_VIOLATION),
        )

        // Missing cue-mode overrides fall back to default settings.
        assertEquals(
            TimingAlertMode.TICK,
            defaultPreferences.copy(cueModes = emptyMap())
                .settingsModeFor(TimingCueId.PULLING_TWENTY_TO_PULL),
        )
        assertEquals(
            TimingAlertMode.VIBRATE,
            defaultPreferences.copy(cueModes = emptyMap())
                .alertModeFor(TimingCueId.PULLING_TWENTY_TO_PULL),
        )

        // Cue-level NONE and global OFF both suppress alerts.
        assertEquals(
            TimingAlertMode.NONE,
            defaultPreferences.copy(
                cueModes = defaultPreferences.cueModes +
                    (TimingCueId.PULLING_TWENTY_TO_PULL to TimingAlertMode.NONE),
            )
                .alertModeFor(TimingCueId.PULLING_TWENTY_TO_PULL),
        )
        assertEquals(
            TimingAlertMode.NONE,
            defaultPreferences.copy(globalMode = TimingAlertGlobalMode.OFF)
                .alertModeFor(TimingCueId.PULLING_TWENTY_TO_PULL),
        )

        // Watch delivery requires watch notifications and an individually enabled cue, except that
        // an Off countdown-ending cue is still sent to return the notification to the score.
        assertTrue(
            defaultPreferences.copy(
                watchConnectionMode = WatchConnectionMode.NOTIFICATIONS,
                watchNotificationAlertMode = WatchNotificationAlertMode.NONE,
            )
                .sendsCueToWatch(
                    TimingCueId.PULLING_TWENTY_TO_PULL,
                    countdownSeconds = 20,
                )
        )
        assertFalse(
            defaultPreferences.sendsCueToWatch(
                TimingCueId.PULLING_TIME_VIOLATION,
                countdownSeconds = 0,
            )
        )
        val offCuePreferences = defaultPreferences.copy(
            watchConnectionMode = WatchConnectionMode.NOTIFICATIONS,
            cueModes = defaultPreferences.cueModes +
                (TimingCueId.PULLING_TIME_VIOLATION to TimingAlertMode.NONE),
        )
        assertFalse(
            offCuePreferences.sendsCueToWatch(
                TimingCueId.OFFENSE_COUNTDOWN_FROM_FIVE,
                countdownSeconds = 5,
            )
        )
        assertTrue(
            offCuePreferences.sendsCueToWatch(
                TimingCueId.PULLING_TIME_VIOLATION,
                countdownSeconds = 0,
            )
        )
        assertFalse(
            defaultPreferences.copy(
                watchConnectionMode = WatchConnectionMode.WEAR_OS,
            ).sendsCueToWatch(
                TimingCueId.PULLING_TWENTY_TO_PULL,
                countdownSeconds = 20,
            )
        )

        // Repeat-count overrides are clamped to the supported sound resources.
        // I.e. values can only end up between 1 and 3, inclusive.
        assertEquals(2, defaultPreferences.repeatCountFor(TimingCueId.PULLING_TWENTY_TO_PULL))
        assertEquals(
            3,
            defaultPreferences.copy(
                cueRepeatCounts = mapOf(TimingCueId.PULLING_TWENTY_TO_PULL to 3),
            )
                .repeatCountFor(TimingCueId.PULLING_TWENTY_TO_PULL),
        )
        assertEquals(
            3,
            defaultPreferences.copy(
                cueRepeatCounts = mapOf(TimingCueId.PULLING_TWENTY_TO_PULL to 99),
            )
                .repeatCountFor(TimingCueId.PULLING_TWENTY_TO_PULL),
        )
        assertEquals(
            1,
            defaultPreferences.copy(
                cueRepeatCounts = mapOf(TimingCueId.PULLING_TWENTY_TO_PULL to 0),
            )
                .repeatCountFor(TimingCueId.PULLING_TWENTY_TO_PULL),
        )
        assertEquals(
            2,
            defaultPreferences.copy(cueRepeatCounts = emptyMap())
                .repeatCountFor(TimingCueId.PULLING_TWENTY_TO_PULL),
        )
    }

    /**
     * Verify restoring timing cue defaults resets cue-level preferences while preserving
     * global sound and vibration settings.
     */
    @Test
    fun timingCueDefaults() {
        // Reset cue-level timing settings while preserving global sound/vibration preferences.
        val appState = AppState(NoOpAppStateStorage)
        fun updateSettings(transform: (Settings) -> Settings) {
            appState.updateSettings(transform(appState.settings))
        }
        fun updateTimingAlerts(transform: (TimingAlertPreferences) -> TimingAlertPreferences) {
            updateSettings { it.withTimingAlerts(transform(it.timingAlerts)) }
        }
        updateTimingAlerts { it.withGlobalMode(TimingAlertGlobalMode.SOUNDS_ON) }
        updateTimingAlerts { it.withSoundVolume(0.4f) }
        updateTimingAlerts { it.withVibrationDuration(420L) }
        updateTimingAlerts { it.withVibrateWithSounds(true) }
        updateTimingAlerts {
            it.withCueMode(TimingCueId.RECEIVING_TWENTY_FOR_HAND, TimingAlertMode.NONE)
        }
        updateTimingAlerts { it.withCueRepeatCount(TimingCueId.RECEIVING_TWENTY_FOR_HAND, 3) }
        updateTimingAlerts { it.withCueMode(TimingCueId.HARD_CAP, TimingAlertMode.BEEP) }
        updateTimingAlerts { it.withCueRepeatCount(TimingCueId.HARD_CAP, 1) }
        updateTimingAlerts { it.withDefaultCueSettings() }
        assertEquals(TimingAlertGlobalMode.SOUNDS_ON, appState.settings.timingAlerts.globalMode)
        assertEquals(0.4f, appState.settings.timingAlerts.soundVolume, 0f)
        assertEquals("Sound volume 40%", appState.settings.timingAlerts.soundVolumeLabel)
        assertEquals(420L, appState.settings.timingAlerts.vibrationDurationMillis)
        assertTrue(appState.settings.timingAlerts.vibrateWithSounds)
        assertEquals(
            TimingAlertMode.TICK,
            appState.settings.timingAlerts.settingsModeFor(TimingCueId.RECEIVING_TWENTY_FOR_HAND),
        )
        assertEquals(
            2,
            appState.settings.timingAlerts.repeatCountFor(TimingCueId.RECEIVING_TWENTY_FOR_HAND),
        )
        assertEquals(
            TimingAlertMode.DING,
            appState.settings.timingAlerts.settingsModeFor(TimingCueId.HARD_CAP),
        )
        assertEquals(3, appState.settings.timingAlerts.repeatCountFor(TimingCueId.HARD_CAP))
    }

    /** Verify the countdown-advancement setting defaults, updates, and allowed values. */
    @Test
    fun countdownAdvancementSetting() {
        // The setting defaults off while retaining three seconds for when it is enabled.
        val appState = AppState(NoOpAppStateStorage)
        assertFalse(appState.settings.automaticallyAdvanceNewCountdowns)
        assertEquals(3, appState.settings.newCountdownAdvanceSeconds)
        assertEquals("Seconds already elapsed: 3", appState.settings.newCountdownAdvanceLabel)
        assertEquals(
            1_000_000L,
            appState.settings.adjustedCountdownStartEpoch(1_000_000L),
        )

        // Both parts of the setting can be updated through the AppState.
        appState.updateSettings(
            appState.settings.withAutomaticallyAdvanceNewCountdowns(true)
        )
        appState.updateSettings(appState.settings.withNewCountdownAdvanceSeconds(10))
        assertTrue(appState.settings.automaticallyAdvanceNewCountdowns)
        assertEquals(10, appState.settings.newCountdownAdvanceSeconds)
        assertEquals("Seconds already elapsed: 10", appState.settings.newCountdownAdvanceLabel)

        // The upshot of the 10 second advancement is that goal and timeout presses
        // are taken to have been pressed 10 seconds previous to the actual press time.
        assertEquals(
            990_000L,
            appState.settings.adjustedCountdownStartEpoch(1_000_000L),
        )

        // Values outside the range exposed by Settings are rejected.
        assertThrows(IllegalArgumentException::class.java) {
            appState.settings.withNewCountdownAdvanceSeconds(0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            appState.settings.withNewCountdownAdvanceSeconds(11)
        }
    }

    @Test
    fun gameControls() {
        // The short Rule guidance section opens first, before the other main-page categories.
        assertEquals(
            listOf(
                "Rule guidance", "Watch connection", "Sound and vibration",
                "Active game screen", "Countdowns", "Gender ratio indicator",
            ),
            SettingsCategory.entries.map { it.title },
        )

        // Protection choices explain both the gesture and the scope of its effect.
        assertEquals(listOf("None", "Auto-lock", "Long press"),
            AccidentalTouchProtection.entries.map { it.label })
        assertTrue(AccidentalTouchProtection.NONE.description.contains("manually"))
        assertTrue(AccidentalTouchProtection.AUTO_LOCK.description.contains("play becomes live"))
        assertTrue(AccidentalTouchProtection.LONG_PRESS.description.contains("Use normal taps in dialogs"))
        val settings = Settings()
        assertTrue(settings.copy(requireWatchLongPress = false).requireWatchLongPressDescription
            .contains("Undo and Redo always require a long press"))
        assertTrue(settings.copy(requireWatchLongPress = true).requireWatchLongPressDescription
            .contains("After opening a menu or another screen, use normal taps"))

        // Automatic timing and manual timing have different instructions.
        assertTrue(settings.copy(automaticallyAdvanceCountdowns = true)
            .automaticallyAdvanceCountdownsDescription.contains("start or resume live play"))
        assertTrue(settings.copy(automaticallyAdvanceCountdowns = false)
            .automaticallyAdvanceCountdownsDescription.contains("wait for you"))
        assertTrue(settings.copy(showDefenseCountdowns = true).showDefenseCountdownsDescription
            .contains("20-second defense countdown"))
        assertTrue(settings.copy(showDefenseCountdowns = false).showDefenseCountdownsDescription
            .contains("arm chops"))
        assertTrue(settings.copy(automaticallyAdvanceNewCountdowns = false)
            .automaticallyAdvanceNewCountdownsDescription.contains("full time"))
        assertTrue(settings.copy(automaticallyAdvanceNewCountdowns = true, newCountdownAdvanceSeconds = 1)
            .automaticallyAdvanceNewCountdownsDescription.contains("1 second already"))
        assertTrue(settings.copy(automaticallyAdvanceNewCountdowns = true, newCountdownAdvanceSeconds = 3)
            .automaticallyAdvanceNewCountdownsDescription.contains("3 seconds already"))

        // Field and ratio descriptions distinguish the available display conventions.
        assertEquals(listOf("Teams fixed", "Ends fixed"), WatchOrientation.entries.map { it.label })
        assertTrue(WatchOrientation.TEAMS_FIXED.description.contains("Team 1 on the left"))
        assertTrue(WatchOrientation.ENDS_FIXED.description.contains("choose the orientation in game setup"))
        assertTrue(settings.copy(showAbbaRatioAsSequence = true).showAbbaRatioAsSequenceDescription
            .contains("W2, M1, M2, W1"))
        assertTrue(settings.copy(showAbbaRatioAsSequence = false).showAbbaRatioAsSequenceDescription
            .contains("4W/3M or 4M/3W"))
        assertEquals("Set 4M/3W indicator color",
            GenderRatio.FOUR_MEN_THREE_WOMEN.badgeColorSettingLabel())
        assertEquals("Set 4W/3M indicator color",
            GenderRatio.FOUR_WOMEN_THREE_MEN.badgeColorSettingLabel())
    }

    @Test
    fun watchConnectionExplanations() {
        // Missing notification permission adds a note only when the connection is None.
        val noPermission = WatchConnectionMode.OFF.settingsDescription(
            notificationsEnabled = false, wearWatchAvailable = null, vibrateOnWatch = true,
        )
        assertEquals(listOf("Note: "), noPermission.filter { it.bold }.map { it.text })
        assertTrue(noPermission.last().text.contains("Phone notifications must be enabled"))
        assertEquals(1, WatchConnectionMode.OFF.settingsDescription(
            notificationsEnabled = true, wearWatchAvailable = null, vibrateOnWatch = true,
        ).size)

        // Notifications explain the sharing requirement and emphasize just the warning prefix.
        val notifications = WatchConnectionMode.NOTIFICATIONS.settingsDescription(
            notificationsEnabled = true, wearWatchAvailable = null, vibrateOnWatch = true,
        )
        assertEquals(listOf("Warning: "), notifications.filter { it.bold }.map { it.text })
        assertTrue(notifications.last().text.contains("notification sharing"))
        val cooldown = WatchConnectionMode.notificationCooldownNotice
        assertEquals(SettingsTextSpan("Important: ", bold = true), cooldown.first())
        assertFalse(cooldown.last().bold)
        assertTrue(cooldown.last().text.contains("Notification cooldown"))

        // A connected Wear watch explains vibration routing only when watch vibration is selected.
        val connected = WatchConnectionMode.WEAR_OS.settingsDescription(
            notificationsEnabled = true, wearWatchAvailable = true, vibrateOnWatch = false,
        )
        val vibrating = WatchConnectionMode.WEAR_OS.settingsDescription(
            notificationsEnabled = true, wearWatchAvailable = true, vibrateOnWatch = true,
        )
        assertEquals(connected, vibrating.dropLast(1))
        assertTrue(vibrating.last().text.contains("on the watch rather than"))
        assertTrue(vibrating.none { it.bold })

        // An unavailable watch gets an emphasized notice; discovery in progress keeps basic copy.
        val unavailable = WatchConnectionMode.WEAR_OS.settingsDescription(
            notificationsEnabled = true, wearWatchAvailable = false, vibrateOnWatch = true,
        )
        assertEquals(listOf("No Wear OS watch is currently available.  "),
            unavailable.filter { it.bold }.map { it.text })
        assertTrue(unavailable.last().text.contains("Pair a Wear OS watch"))
        assertEquals(1, WatchConnectionMode.WEAR_OS.settingsDescription(
            notificationsEnabled = true, wearWatchAvailable = null, vibrateOnWatch = true,
        ).size)
    }

    @Test
    fun watchVibrationGuidance() {
        // Connection choices explain whether timing cues reach a companion.
        assertTrue(WatchConnectionMode.OFF.description.contains("No connection"))
        assertTrue(WatchConnectionMode.NOTIFICATIONS.description.contains("notifications"))
        assertEquals(listOf("None", "Vibration cues only", "All enabled cues"),
            WatchNotificationAlertMode.entries.map { it.label })
        assertTrue(WatchNotificationAlertMode.NONE.description.contains("All notifications will be silent"))
        assertTrue(WatchNotificationAlertMode.VIBRATION_ONLY.description
            .contains("watch instead of the phone"))
        assertTrue(WatchNotificationAlertMode.ALL.description.contains("trigger an alert"))
        assertTrue(WatchConnectionMode.WEAR_OS.description.isNotBlank())
        val watch = TimingAlertPreferences(watchConnectionMode = WatchConnectionMode.WEAR_OS)
        assertTrue(watch.vibrateOnWatchDescription.contains("screen is off"))
        assertTrue(watch.vibrateOnWatchDescription.contains("phone instead"))
        assertTrue(watch.vibrationTestDescription.contains("watch's settings"))

        // Redirecting vibration changes both the destination note and troubleshooting advice.
        val phone = watch.copy(vibrateOnWatch = false)
        assertEquals("Keep timing vibrations on the phone.", phone.vibrateOnWatchDescription)
        assertTrue(phone.vibrationTestDescription.contains("phone's haptic settings"))
    }

    @Test
    fun soundPreviewGuidance() {
        // Sound and global-mode labels match the settings screen.
        assertEquals("Tick", TimingAlertSound.TICK.label)
        assertEquals("Sounds on", TimingAlertGlobalMode.SOUNDS_ON.label)
        assertEquals(
            listOf("None", "Notifications", "Wear OS app"),
            WatchConnectionMode.entries.map { mode -> mode.label },
        )

        // Enabled sounds need no warning; muted previews explain how to enable them.
        val sounds = TimingAlertPreferences(globalMode = TimingAlertGlobalMode.SOUNDS_ON)
        assertNull(sounds.soundPreviewNote(hasTimingCueHaptics = true))
        val muted = sounds.copy(globalMode = TimingAlertGlobalMode.VIBRATION_ONLY)
        assertFalse(muted.soundPreviewNote(hasTimingCueHaptics = true)!!.contains("vibrate instead"))
        val vibrating = muted.copy(vibrateWithSounds = true)
        assertTrue(vibrating.soundPreviewNote(hasTimingCueHaptics = true)!!.contains("phone will currently vibrate"))
        assertTrue(vibrating.copy(watchConnectionMode = WatchConnectionMode.WEAR_OS)
            .soundPreviewNote(hasTimingCueHaptics = true)!!.contains("watch will currently vibrate"))
        assertFalse(vibrating.soundPreviewNote(hasTimingCueHaptics = false)!!.contains("vibrate instead"))

        // Missing vibration hardware changes guidance only for modes that use it.
        val off = TimingAlertGlobalMode.OFF
        assertEquals(off.settingsMessages(true), off.settingsMessages(false))
        assertTrue(off.settingsMessages(true).single().contains("No sound or vibration"))
        val vibrationOnly = TimingAlertGlobalMode.VIBRATION_ONLY
        assertTrue(vibrationOnly.settingsMessages(true).single().contains("Vibration will be used"))
        assertTrue(vibrationOnly.settingsMessages(false).single().contains("vibration is unavailable"))
        val soundMode = TimingAlertGlobalMode.SOUNDS_ON
        assertEquals(1, soundMode.settingsMessages(true).size)
        assertTrue(soundMode.settingsMessages(true).single().contains("Ear buds"))
        assertEquals(2, soundMode.settingsMessages(false).size)
        assertTrue(soundMode.settingsMessages(false).last().contains("vibration is unavailable"))
    }

}
