package rmjarvis.ultiobserver

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlin.math.roundToLong

private enum class GenderRatioBadgeColorTarget(
    val ratio: GenderRatio,
    val label: String,
    val testTagPrefix: String,
) {
    FOUR_MEN(
        ratio = GenderRatio.FOUR_MEN_THREE_WOMEN,
        label = "Set 4M/3W indicator color",
        testTagPrefix = "settings-4m-3w-badge-color",
    ),
    FOUR_WOMEN(
        ratio = GenderRatio.FOUR_WOMEN_THREE_MEN,
        label = "Set 4W/3M indicator color",
        testTagPrefix = "settings-4w-3m-badge-color",
    ),
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    settings: Settings,
    onSettingsChange: (Settings) -> Unit,
    onOpenTimingCueSettings: () -> Unit,
    onBackHome: () -> Unit,
    onHome: () -> Unit,
) {
    val context = LocalContext.current
    val hasTimingCueHaptics = context.hasTimingCueHaptics()
    var colorTarget by remember { mutableStateOf<GenderRatioBadgeColorTarget?>(null) }
    var customColorTarget by remember { mutableStateOf<GenderRatioBadgeColorTarget?>(null) }
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    TopBarBackButton(onClick = onBackHome)
                },
                actions = {
                    TopBarHomeButton(onClick = onHome)
                },
            )
        },
        bottomBar = {
            NavigationButton(
                label = "Save and return",
                fullWidth = true,
                colors = primaryButtonColors(),
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(16.dp),
                onClick = onHome,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            RuleGuidanceModeSelector(
                selectedMode = settings.ruleGuidanceMode,
                onModeChange = {
                    onSettingsChange(settings.withRuleGuidanceMode(it))
                },
            )

            HorizontalDivider()

            TimingAlertGlobalModeSelector(
                selectedMode = settings.timingAlerts.globalMode,
                onModeChange = {
                    onSettingsChange(
                        settings.withTimingAlerts(settings.timingAlerts.withGlobalMode(it))
                    )
                },
            )

            TimingAlertSoundControls(
                timingAlertPreferences = settings.timingAlerts,
                onSoundVolumeChange = {
                    onSettingsChange(
                        settings.withTimingAlerts(settings.timingAlerts.withSoundVolume(it))
                    )
                },
                onVibrationDurationChange = {
                    onSettingsChange(
                        settings.withTimingAlerts(settings.timingAlerts.withVibrationDuration(it))
                    )
                },
                onVibrateWithSoundsChange = {
                    onSettingsChange(
                        settings.withTimingAlerts(settings.timingAlerts.withVibrateWithSounds(it))
                    )
                },
                onOpenTimingCueSettings = onOpenTimingCueSettings,
                hasTimingCueHaptics = hasTimingCueHaptics,
                onTestVibration = { durationMillis ->
                    context.performTimingCueHaptic(durationMillis)
                },
            )

            HorizontalDivider()

            SettingsSwitchRow(
                label = "Automatically start live play when a countdown expires?",
                checked = settings.automaticallyAdvanceCountdowns,
                onCheckedChange = {
                    onSettingsChange(settings.withAutomaticallyAdvanceCountdowns(it))
                },
                testTag = "settings-auto-advance-countdowns",
            )

            SettingsSwitchRow(
                label = "Automatically lock screen when play becomes live?",
                checked = settings.automaticallyLockLivePoint,
                onCheckedChange = {
                    onSettingsChange(settings.withAutomaticallyLockLivePoint(it))
                },
                testTag = "settings-auto-lock-live-point",
            )

            SettingsSwitchWithNote(
                label = "Show countdown for the defensive check after the offense is set for timeouts and misconduct penalties?",
                note = "We expect that most observers will count this off themselves with arm chops. " +
                    "Turn this on if you want UltiObserver to display the 20-second defense countdown for you.",
                checked = settings.showDefenseCountdowns,
                onCheckedChange = {
                    onSettingsChange(settings.withShowDefenseCountdowns(it))
                },
                testTag = "settings-show-defense-countdowns",
            )

            HorizontalDivider()

            SettingsSwitchWithNote(
                label = "Show ABBA gender ratio as M1/M2/W1/W2?",
                note = if (settings.showAbbaRatioAsSequence) {
                    "Ratio will display as W2, M1, M2, W1, W2... or M2, W1, W2, M1, M2..."
                } else {
                    "Ratio will display as either 4W/3M or 4M/3W."
                },
                checked = settings.showAbbaRatioAsSequence,
                onCheckedChange = {
                    onSettingsChange(settings.withShowAbbaRatioAsSequence(it))
                },
                testTag = "settings-show-abba-ratio-as-sequence",
            )

            GenderRatioBadgeColorTarget.entries.forEach { target ->
                GenderRatioBadgeColorRow(
                    target = target,
                    colorArgb = settings.genderRatioBadgeColorArgb(target.ratio),
                    onClick = {
                        colorTarget = target
                    },
                )
            }
        }
    }

    colorTarget?.let { target ->
        val colorArgb = settings.genderRatioBadgeColorArgb(target.ratio)
        val selectedPreset = presetColorForArgb(colorArgb)
        ColorChoiceDialog(
            title = target.label,
            selectedPreset = selectedPreset,
            customColorArgb = colorArgb.takeIf { selectedPreset == null },
            customColorSelected = selectedPreset == null,
            testTagPrefix = target.testTagPrefix,
            onPresetColorSelected = { color ->
                onSettingsChange(
                    settings.withGenderRatioBadgeColor(target.ratio, color.accentArgb)
                )
                colorTarget = null
            },
            onCustomColorSelected = { customColorArgb ->
                onSettingsChange(
                    settings.withGenderRatioBadgeColor(target.ratio, customColorArgb)
                )
                colorTarget = null
            },
            onMoreColors = {
                colorTarget = null
                customColorTarget = target
            },
            onDismiss = {
                colorTarget = null
            },
        )
    }

    customColorTarget?.let { target ->
        CustomColorChoiceDialog(
            title = target.label,
            initialColorArgb = settings.genderRatioBadgeColorArgb(target.ratio),
            testTagPrefix = target.testTagPrefix,
            onColorSelected = { colorArgb ->
                onSettingsChange(settings.withGenderRatioBadgeColor(target.ratio, colorArgb))
                customColorTarget = null
            },
            onDismiss = {
                customColorTarget = null
            },
        )
    }
}

/**
 * Render the live-game rule-guidance selector and describe the selected behavior.
 *
 * @param selectedMode The currently selected guidance mode.
 * @param onModeChange Callback receiving the newly selected mode.
 */
@Composable
private fun RuleGuidanceModeSelector(
    selectedMode: RuleGuidanceMode,
    onModeChange: (RuleGuidanceMode) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "How much rule guidance should appear during games?",
            style = MaterialTheme.typography.titleMedium,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RuleGuidanceMode.entries.forEach { mode ->
                ChoiceChipButton(
                    label = mode.label,
                    selected = mode == selectedMode,
                    tag = "settings-rule-guidance-${mode.name}",
                    onClick = {
                        onModeChange(mode)
                    },
                )
            }
        }
        Text(
            text = selectedMode.description,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.testTag("settings-rule-guidance-description"),
        )
    }
}

@Composable
private fun GenderRatioBadgeColorRow(
    target: GenderRatioBadgeColorTarget,
    colorArgb: Long,
    onClick: () -> Unit,
) {
    val background = Color(colorArgb)
    val content = readableContentColor(background)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = target.label,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        BigActionButton(
            label = colorLabel(colorArgb),
            containerColor = background,
            contentColor = content,
            borderColor = content.copy(alpha = 0.7f),
            tag = target.testTagPrefix,
            onClick = onClick,
        )
    }
}

private fun presetColorForArgb(colorArgb: Long): TeamColorChoice? {
    return TeamColorChoice.entries.firstOrNull {
        it != TeamColorChoice.CUSTOM && it.accentArgb == colorArgb
    }
}

private fun colorLabel(colorArgb: Long): String {
    return presetColorForArgb(colorArgb)?.label ?: "Custom"
}

/**
 * Render one labeled switch row with an explanatory note below it.
 *
 * @param label The setting label.
 * @param note The explanatory note shown under the switch row.
 * @param checked Whether the switch is currently on.
 * @param onCheckedChange Callback receiving the new switch state.
 * @param testTag The test tag attached to the switch and value text.
 */
@Composable
private fun SettingsSwitchWithNote(
    label: String,
    note: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        SettingsSwitchRow(
            label = label,
            checked = checked,
            onCheckedChange = onCheckedChange,
            testTag = testTag,
        )
        Text(
            text = note,
            style = MaterialTheme.typography.bodySmall,
        )
    }
}

/**
 * Render one labeled switch row in Settings.
 *
 * @param label The setting label.
 * @param checked Whether the switch is currently on.
 * @param onCheckedChange Callback receiving the new switch state.
 * @param testTag The test tag attached to the switch and value text.
 */
@Composable
private fun SettingsSwitchRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.weight(1f),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = if (checked) "Yes" else "No",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.testTag("$testTag-value"),
            )
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange,
                modifier = Modifier.testTag(testTag),
            )
        }
    }
}

/**
 * Render the per-cue timing alert settings screen.
 *
 * @param settings The current settings to display.
 * @param onSettingsChange Callback receiving updated settings.
 * @param onBackSettings Callback returning to the main Settings screen.
 * @param onHome Callback returning directly to Home.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimingCueSettingsScreen(
    settings: Settings,
    onSettingsChange: (Settings) -> Unit,
    onBackSettings: () -> Unit,
    onHome: () -> Unit,
) {
    val context = LocalContext.current
    val hasTimingCueHaptics = context.hasTimingCueHaptics()
    val timingAlertPlayer = remember(context) {
        TimingAlertPlayer(context)
    }

    DisposableEffect(timingAlertPlayer) {
        onDispose { timingAlertPlayer.release() }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Cue sound settings") },
                navigationIcon = {
                    TopBarBackButton(onClick = onBackSettings)
                },
                actions = {
                    TopBarHomeButton(onClick = onHome)
                },
            )
        },
        bottomBar = {
            NavigationButton(
                label = "Save and return home",
                fullWidth = true,
                colors = primaryButtonColors(),
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(16.dp),
                onClick = onHome,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            SoundPreviewRow(
                title = "Sound previews",
                sounds = TimingAlertSound.entries,
                note = settings.timingAlerts.soundPreviewNote(hasTimingCueHaptics),
                onPreview = { sound ->
                    timingAlertPlayer.play(sound, settings.timingAlerts.soundVolume)
                },
            )

            timingCueSections.forEach { section ->
                HorizontalDivider()
                if (section == timingCueSections.first()) {
                    MenuButton(
                        label = "Reset all to defaults",
                        tag = "settings-reset-timing-cue-defaults",
                        colors = resetButtonColors(),
                        borderColor = null,
                        onClick = {
                            onSettingsChange(
                                settings.withTimingAlerts(settings.timingAlerts.withDefaultCueSettings())
                            )
                        },
                    )
                }
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleMedium,
                )
                if (section.isDefenseCheckCountdownSection() && !settings.showDefenseCountdowns) {
                    Text(
                        text = "Note — defensive check countdowns are not currently enabled. " +
                            "If you want these cues, enable defensive check countdowns " +
                            "on the previous page.",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                section.cues.forEach { cueId ->
                    TimingCueSettingRow(
                        cueId = cueId,
                        mode = settings.timingAlerts.settingsModeFor(cueId),
                        repeatCount = settings.timingAlerts.repeatCountFor(cueId),
                        onModeChange = { mode ->
                            onSettingsChange(
                                settings.withTimingAlerts(settings.timingAlerts.withCueMode(cueId, mode))
                            )
                        },
                        onRepeatCountChange = { repeatCount ->
                            onSettingsChange(
                                settings.withTimingAlerts(
                                    settings.timingAlerts.withCueRepeatCount(cueId, repeatCount)
                                )
                            )
                        },
                    )
                }
            }
        }
    }
}

/**
 * Render the global timing-alert mode selector.
 *
 * @param selectedMode The currently selected global alert mode.
 * @param onModeChange Callback receiving the newly selected global mode.
 */
@Composable
private fun TimingAlertGlobalModeSelector(
    selectedMode: TimingAlertGlobalMode,
    onModeChange: (TimingAlertGlobalMode) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = "Use sounds and vibration for timing cues?",
            style = MaterialTheme.typography.titleMedium,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            TimingAlertGlobalMode.entries.forEach { mode ->
                ChoiceChipButton(
                    label = mode.label,
                    selected = mode == selectedMode,
                    tag = "settings-global-alert-${mode.name}",
                    onClick = {
                        onModeChange(mode)
                    },
                )
            }
        }
    }
}

/**
 * Render volume, vibration length, and sound-vibration controls.
 *
 * @param timingAlertPreferences The current alert preferences to display.
 * @param onSoundVolumeChange Callback receiving sound volume changes.
 * @param onVibrationDurationChange Callback receiving vibration duration changes in milliseconds.
 * @param onVibrateWithSoundsChange Callback receiving the sound-plus-vibration toggle state.
 * @param onOpenTimingCueSettings Callback opening per-cue timing alert settings.
 * @param hasTimingCueHaptics Whether this device reports usable timing-cue haptics.
 * @param onTestVibration Callback playing a haptic test for the selected duration.
 */
@Composable
private fun TimingAlertSoundControls(
    timingAlertPreferences: TimingAlertPreferences,
    onSoundVolumeChange: (Float) -> Unit,
    onVibrationDurationChange: (Long) -> Unit,
    onVibrateWithSoundsChange: (Boolean) -> Unit,
    onOpenTimingCueSettings: () -> Unit,
    hasTimingCueHaptics: Boolean,
    onTestVibration: (Long) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        timingAlertPreferences.globalMode.settingsMessages(hasTimingCueHaptics).forEach { message ->
            Text(
                text = message,
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        if (timingAlertPreferences.globalMode == TimingAlertGlobalMode.SOUNDS_ON) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = "Also vibrate on cues that use sound?",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.weight(1f),
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (timingAlertPreferences.vibrateWithSounds) "Yes" else "No",
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.testTag("settings-vibrate-with-sounds-value"),
                    )
                    Switch(
                        checked = timingAlertPreferences.vibrateWithSounds,
                        onCheckedChange = onVibrateWithSoundsChange,
                        enabled = hasTimingCueHaptics,
                        modifier = Modifier.testTag("settings-vibrate-with-sounds"),
                    )
                }
            }
        }
        MenuButton(
            label = "Sound/vibration settings for individual cues",
            tag = "settings-open-timing-cue-settings",
            colors = secondaryButtonColors(),
            borderColor = null,
            onClick = onOpenTimingCueSettings,
        )
        if (timingAlertPreferences.globalMode == TimingAlertGlobalMode.SOUNDS_ON) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Sound volume ${(timingAlertPreferences.soundVolume * 100).toInt()}%",
                    style = MaterialTheme.typography.titleMedium,
                )
                Slider(
                    value = timingAlertPreferences.soundVolume,
                    onValueChange = onSoundVolumeChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings-sound-volume"),
                )
            }
        }
        if (timingAlertPreferences.globalMode != TimingAlertGlobalMode.OFF) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Vibration length",
                    style = MaterialTheme.typography.titleMedium,
                )
                Slider(
                    value = timingAlertPreferences.vibrationDurationMillis.toFloat(),
                    onValueChange = {
                        val durationMillis = it.roundToLong()
                        onVibrationDurationChange(durationMillis)
                    },
                    valueRange = MIN_TIMING_CUE_VIBRATION_MS.toFloat()..MAX_TIMING_CUE_VIBRATION_MS.toFloat(),
                    enabled = hasTimingCueHaptics,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings-vibration-length"),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    MenuButton(
                        label = "Test",
                        enabled = hasTimingCueHaptics,
                        fullWidth = false,
                        tag = "settings-test-vibration",
                        colors = neutralOutlinedButtonColors(
                            containerColor = EmphasizedDarkNeutralColor,
                            contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                        onClick = {
                            onTestVibration(timingAlertPreferences.vibrationDurationMillis)
                        },
                    )
                    Text(
                        text = "If the test vibration is too weak, check the vibration strength in your phone's haptic settings.",
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.weight(1f),
                    )
                }
            }
        }
    }
}

/**
 * Render sound preview chips for the available cue sounds.
 *
 * @param title The section title.
 * @param sounds The sound choices to preview.
 * @param note Optional explanatory note shown below the chips.
 * @param onPreview Callback receiving the sound selected for preview.
 */
@Composable
private fun SoundPreviewRow(
    title: String,
    sounds: List<TimingAlertSound>,
    note: String?,
    onPreview: (TimingAlertSound) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            sounds.forEach { sound ->
                MenuButton(
                    label = sound.label,
                    tag = "settings-sound-preview-${sound.name}",
                    fullWidth = false,
                    colors = neutralOutlinedButtonColors(
                        containerColor = EmphasizedDarkNeutralColor,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                    ),
                    contentPadding = DefaultButtonContentPadding,
                    onClick = {
                        onPreview(sound)
                    },
                )
            }
        }
        if (note != null) {
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

/// Return the explanatory note to show beside sound previews under the current global mode.
private fun TimingAlertPreferences.soundPreviewNote(hasTimingCueHaptics: Boolean): String? {
    if (globalMode == TimingAlertGlobalMode.SOUNDS_ON) {
        return null
    }
    val vibrateInsteadSentence = if (vibrateWithSounds && hasTimingCueHaptics) {
        " The phone will currently vibrate instead for any cues with sounds."
    } else {
        ""
    }
    return "Note — sounds are currently not enabled.$vibrateInsteadSentence " +
        "If you want sounds, enable them on the previous page."
}

/**
 * Render one timing cue's alert-mode and repeat-count controls.
 *
 * @param cueId The cue being configured.
 * @param mode The currently selected alert mode for the cue.
 * @param repeatCount The currently selected repeat count for the cue.
 * @param onModeChange Callback receiving mode changes for the cue.
 * @param onRepeatCountChange Callback receiving repeat-count changes for the cue.
 */
@Composable
private fun TimingCueSettingRow(
    cueId: TimingCueId,
    mode: TimingAlertMode,
    repeatCount: Int,
    onModeChange: (TimingAlertMode) -> Unit,
    onRepeatCountChange: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = cueId.label,
            style = MaterialTheme.typography.bodyMedium,
        )
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                timingAlertOptions.forEach { option ->
                    CompactTimingAlertOption(
                        selected = option == mode,
                        onClick = {
                            onModeChange(option)
                        },
                        label = option.settingsLabel(),
                        horizontalPadding = 6,
                        tag = "settings-${cueId.name}-${option.name}",
                    )
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    timingAlertRepeatOptions.forEach { option ->
                        CompactTimingAlertOption(
                            selected = option == repeatCount,
                            onClick = {
                                onRepeatCountChange(
                                    // Clicking a selected x2 for instance removes the selection
                                    // so go back to x1.
                                    if (option == repeatCount) 1 else option
                                )
                            },
                            label = "x$option",
                            horizontalPadding = 8,
                            tag = "settings-${cueId.name}-REPEAT_$option",
                        )
                    }
                }
            }
        }
    }
}

/**
 * Render a compact selectable timing-alert option.
 *
 * @param selected Whether this option is currently selected.
 * @param label The visible option label.
 * @param modifier Optional layout modifier.
 * @param horizontalPadding Horizontal text padding in density-independent pixels.
 * @param tag Optional test tag.
 * @param onClick Callback selecting this option.
 */
@Composable
private fun CompactTimingAlertOption(
    selected: Boolean,
    label: String,
    horizontalPadding: Int = 5,
    tag: String? = null,
    onClick: () -> Unit,
) {
    ChoiceChipButton(
        label = label,
        selected = selected,
        tag = tag,
        horizontalPadding = horizontalPadding.dp,
        verticalPadding = 5.dp,
        onClick = onClick,
    )
}

/// Return the settings-page messages for a global timing-alert mode and haptic capability.
private fun TimingAlertGlobalMode.settingsMessages(hasTimingCueHaptics: Boolean): List<String> {
    if (!hasTimingCueHaptics) {
        val noHapticsMessage = "This phone reports that vibration is unavailable. " +
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

/// Return the compact settings label for a timing-alert mode.
private fun TimingAlertMode.settingsLabel(): String {
    return when (this) {
        TimingAlertMode.NONE -> "Off"
        TimingAlertMode.VIBRATE -> "Vibrate"
        TimingAlertMode.TICK -> "Tick"
        TimingAlertMode.BEEP -> "Beep"
        TimingAlertMode.KNOCK -> "Knock"
        TimingAlertMode.DING -> "Ding"
    }
}

private val timingAlertOptions = listOf(
    TimingAlertMode.NONE,
    TimingAlertMode.VIBRATE,
    TimingAlertMode.TICK,
    TimingAlertMode.BEEP,
    TimingAlertMode.KNOCK,
    TimingAlertMode.DING,
)

private val timingAlertRepeatOptions = listOf(2, 3)

/**
 * Timing cues grouped into one settings section.
 *
 * @param title The section title shown in settings.
 * @param cues The cue ids displayed in this section.
 */
private data class TimingCueSection(
    val title: String,
    val cues: List<TimingCueId>,
)

/// Return whether this section configures the optional defense-check countdown cues.
private fun TimingCueSection.isDefenseCheckCountdownSection(): Boolean {
    return cues == listOf(
        TimingCueId.DEFENSE_TWENTY,
        TimingCueId.DEFENSE_TEN,
        TimingCueId.DEFENSE_CHECK_LIMIT,
    )
}

private val timingCueSections = listOf(
    TimingCueSection(
        title = "Before pull - offense",
        cues = listOf(
            TimingCueId.RECEIVING_TWENTY_FOR_HAND,
            TimingCueId.RECEIVING_TEN_FOR_HAND,
            TimingCueId.RECEIVING_GIVE_HAND,
        ),
    ),
    TimingCueSection(
        title = "Before pull - defense",
        cues = listOf(
            TimingCueId.PULLING_TWENTY_TO_PULL,
            TimingCueId.PULLING_TEN_TO_PULL,
            TimingCueId.PULLING_TIME_VIOLATION,
        ),
    ),
    TimingCueSection(
        title = "Timeout / misconduct",
        cues = listOf(
            TimingCueId.TIMEOUT_CLEAR_FIELD,
            TimingCueId.OFFENSE_TWENTY,
            TimingCueId.OFFENSE_TEN,
            TimingCueId.OFFENSE_COUNTDOWN_FROM_FIVE,
            TimingCueId.OFFENSE_SET_LIMIT,
        ),
    ),
    TimingCueSection(
        title = "Defense check countdown",
        cues = listOf(
            TimingCueId.DEFENSE_TWENTY,
            TimingCueId.DEFENSE_TEN,
            TimingCueId.DEFENSE_CHECK_LIMIT,
        ),
    ),
    TimingCueSection(
        title = "Timeout between points",
        cues = listOf(
            TimingCueId.TIMEOUT_BETWEEN_POINTS_ONE_MINUTE_FOR_HAND,
            TimingCueId.TIMEOUT_BETWEEN_POINTS_ONE_MINUTE_TO_PULL,
        ),
    ),
    TimingCueSection(
        title = "Halftime",
        cues = listOf(
            TimingCueId.HALFTIME_FIVE_MINUTES,
            TimingCueId.HALFTIME_TWO_MINUTES,
        ),
    ),
    TimingCueSection(
        title = "Caps",
        cues = listOf(
            TimingCueId.HALF_CAP,
            TimingCueId.SOFT_CAP,
            TimingCueId.HARD_CAP,
        ),
    ),
)
