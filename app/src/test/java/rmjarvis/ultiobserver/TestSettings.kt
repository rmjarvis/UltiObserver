package rmjarvis.ultiobserver

import org.junit.Assert.*
import org.junit.Test

/** Settings defaults, updates, protection policies, and observer-facing explanations. */
class TestSettings {
    /**
     * Test default timing-alert preferences.
     */
    @Test
    fun timingAlertPreferenceDefaults() {
        val defaultPreferences = TimingAlertPreferences()

        // Default preferences start in vibration-only mode with full sound volume.
        assertEquals(TimingAlertGlobalMode.VIBRATION_ONLY, defaultPreferences.globalMode)
        assertEquals(WatchConnectionMode.OFF, defaultPreferences.watchConnectionMode)
        assertEquals(1f, defaultPreferences.soundVolume, 0f)
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
                    watchConnectionMode = WatchConnectionMode.SILENT,
                )
            )
        )
        assertFalse(
            GameRules().hasEnabledCapTimingAlerts(
                defaultPreferences.copy(
                    globalMode = TimingAlertGlobalMode.OFF,
                    watchConnectionMode = WatchConnectionMode.SILENT,
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
            defaultPreferences.copy(watchConnectionMode = WatchConnectionMode.SILENT)
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
            watchConnectionMode = WatchConnectionMode.ALERTING,
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
        // Protection choices explain both the gesture and the scope of its effect.
        assertEquals(listOf("None", "Auto-lock", "Long press"),
            AccidentalTouchProtection.entries.map { it.label })
        assertTrue(AccidentalTouchProtection.NONE.description.contains("manually"))
        assertTrue(AccidentalTouchProtection.AUTO_LOCK.description.contains("play becomes live"))
        assertTrue(AccidentalTouchProtection.LONG_PRESS.description.contains("dialogs that you open"))
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
    }

    @Test
    fun watchVibrationGuidance() {
        // Connection choices explain whether timing cues reach a companion.
        assertTrue(WatchConnectionMode.OFF.description.contains("No notifications"))
        assertTrue(WatchConnectionMode.SILENT.description.contains("no alerts"))
        assertTrue(WatchConnectionMode.ALERTING.description.contains("trigger an alert"))
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
            listOf("Off", "Silent", "Alerting", "Wear OS"),
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

    /** Automatic prompts require holds only when no user-opened dialog takes precedence. */
    @Test
    fun dialogTouchProtection() {
        val manualDialogs = listOf(
            "card entry", "event log", "team information", "rules reference",
            "timeout", "time violation", "pull violation", "technical foul",
        )

        // Cover every combination, including manual dialogs interrupting an automatic prompt.
        // An empty set represents the ordinary automatic-prompt path; any manual dialog
        // takes precedence and must retain normal taps in every protection mode.
        for (mask in 0 until (1 shl manualDialogs.size)) {
            val openDialogs = manualDialogs.filterIndexed { index, _ ->
                mask and (1 shl index) != 0
            }.toSet()
            for (hasDecision in listOf(false, true)) {
                for (protection in AccidentalTouchProtection.entries) {
                    val expected = protection == AccidentalTouchProtection.LONG_PRESS &&
                        hasDecision && openDialogs.isEmpty()
                    assertEquals(
                        "$protection, automatic prompt=$hasDecision, manual dialogs=$openDialogs",
                        expected,
                        protection.requiresLongPressForDialog(
                            hasPendingGameDecision = hasDecision,
                            hasActiveCardEntry = "card entry" in openDialogs,
                            showEventLogSheet = "event log" in openDialogs,
                            hasTeamInfoSheet = "team information" in openDialogs,
                            showRulesReference = "rules reference" in openDialogs,
                            hasPendingTimeoutConfirmation = "timeout" in openDialogs,
                            hasPendingTimeViolation = "time violation" in openDialogs,
                            hasPendingPullViolation = "pull violation" in openDialogs,
                            hasPendingTechnicalFoul = "technical foul" in openDialogs,
                        ),
                    )
                }
            }
        }
    }
}
