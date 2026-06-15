package rmjarvis.ultiobserver

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import kotlin.math.roundToLong

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    automaticallyAdvanceCountdowns: Boolean,
    automaticallyLockLivePoint: Boolean,
    timingAlertPreferences: TimingAlertPreferences,
    onAutomaticallyAdvanceCountdownsChange: (Boolean) -> Unit,
    onAutomaticallyLockLivePointChange: (Boolean) -> Unit,
    onGlobalModeChange: (TimingAlertGlobalMode) -> Unit,
    onSoundVolumeChange: (Float) -> Unit,
    onVibrationDurationChange: (Long) -> Unit,
    onVibrateWithSoundsChange: (Boolean) -> Unit,
    onOpenTimingCueSettings: () -> Unit,
    onBackHome: () -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Settings") },
                navigationIcon = {
                    TextButton(onClick = onBackHome) {
                        Text("Back")
                    }
                },
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
            SettingsSwitchRow(
                label = "Automatically start live play when a countdown expires?",
                checked = automaticallyAdvanceCountdowns,
                onCheckedChange = onAutomaticallyAdvanceCountdownsChange,
                testTag = "settings-auto-advance-countdowns",
            )

            SettingsSwitchRow(
                label = "Automatically lock screen when play becomes live?",
                checked = automaticallyLockLivePoint,
                onCheckedChange = onAutomaticallyLockLivePointChange,
                testTag = "settings-auto-lock-live-point",
            )

            HorizontalDivider()

            TimingAlertGlobalModeSelector(
                selectedMode = timingAlertPreferences.globalMode,
                onModeChange = onGlobalModeChange,
            )

            TimingAlertSoundControls(
                timingAlertPreferences = timingAlertPreferences,
                onSoundVolumeChange = onSoundVolumeChange,
                onVibrationDurationChange = onVibrationDurationChange,
                onVibrateWithSoundsChange = onVibrateWithSoundsChange,
            )

            Button(
                onClick = onOpenTimingCueSettings,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("settings-open-timing-cue-settings"),
                shape = RoundedCornerShape(28.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF1565C0),
                    contentColor = Color.White,
                ),
            ) {
                Text("Sound settings for individual cues")
            }
        }
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
 * @param timingAlertPreferences The current alert preferences to display.
 * @param onTimingCueModeChange Callback receiving cue-specific mode changes.
 * @param onTimingCueRepeatCountChange Callback receiving cue-specific repeat-count changes.
 * @param onResetTimingCueSettings Callback restoring all cue settings to defaults.
 * @param onBackSettings Callback returning to the main Settings screen.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimingCueSettingsScreen(
    timingAlertPreferences: TimingAlertPreferences,
    onTimingCueModeChange: (TimingCueId, TimingAlertMode) -> Unit,
    onTimingCueRepeatCountChange: (TimingCueId, Int) -> Unit,
    onResetTimingCueSettings: () -> Unit,
    onBackSettings: () -> Unit,
) {
    val context = LocalContext.current
    val timingAlertPlayer = remember(context) { TimingAlertPlayer(context) }

    DisposableEffect(timingAlertPlayer) {
        onDispose { timingAlertPlayer.release() }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Cue sound settings") },
                navigationIcon = {
                    TextButton(onClick = onBackSettings) {
                        Text("Back")
                    }
                },
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
                note = timingAlertPreferences.soundPreviewNote(),
                onPreview = { sound ->
                    timingAlertPlayer.play(sound, timingAlertPreferences.soundVolume)
                },
            )

            timingCueSections.forEach { section ->
                HorizontalDivider()
                if (section == timingCueSections.first()) {
                    OutlinedButton(
                        onClick = onResetTimingCueSettings,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings-reset-timing-cue-defaults"),
                        shape = RoundedCornerShape(28.dp),
                    ) {
                        Text("Reset all to defaults")
                    }
                }
                Text(
                    text = section.title,
                    style = MaterialTheme.typography.titleMedium,
                )
                section.cues.forEach { cueId ->
                    TimingCueSettingRow(
                        cueId = cueId,
                        mode = timingAlertPreferences.settingsModeFor(cueId),
                        repeatCount = timingAlertPreferences.repeatCountFor(cueId),
                        onModeChange = { mode -> onTimingCueModeChange(cueId, mode) },
                        onRepeatCountChange = { repeatCount -> onTimingCueRepeatCountChange(cueId, repeatCount) },
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
                FilterChip(
                    selected = mode == selectedMode,
                    onClick = { onModeChange(mode) },
                    label = { Text(mode.label) },
                    modifier = Modifier.testTag("settings-global-alert-${mode.name}"),
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
 */
@Composable
private fun TimingAlertSoundControls(
    timingAlertPreferences: TimingAlertPreferences,
    onSoundVolumeChange: (Float) -> Unit,
    onVibrationDurationChange: (Long) -> Unit,
    onVibrateWithSoundsChange: (Boolean) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            text = timingAlertPreferences.globalMode.settingsMessage(),
            style = MaterialTheme.typography.bodyMedium,
        )
        if (timingAlertPreferences.globalMode != TimingAlertGlobalMode.OFF) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Vibration length",
                    style = MaterialTheme.typography.titleMedium,
                )
                Slider(
                    value = timingAlertPreferences.vibrationDurationMillis.toFloat(),
                    onValueChange = {
                        onVibrationDurationChange(it.roundToLong())
                    },
                    valueRange = MIN_TIMING_CUE_VIBRATION_MS.toFloat()..MAX_TIMING_CUE_VIBRATION_MS.toFloat(),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("settings-vibration-length"),
                )
            }
        }
        if (timingAlertPreferences.globalMode != TimingAlertGlobalMode.SOUNDS_ON) {
            return@Column
        }
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
                    modifier = Modifier.testTag("settings-vibrate-with-sounds"),
                )
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
                FilterChip(
                    selected = false,
                    onClick = { onPreview(sound) },
                    label = { Text(sound.label) },
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
private fun TimingAlertPreferences.soundPreviewNote(): String? {
    if (globalMode == TimingAlertGlobalMode.SOUNDS_ON) {
        return null
    }
    val vibrateInsteadSentence = if (vibrateWithSounds) {
        " The phone will currently vibrate instead for any cues with sounds."
    } else {
        ""
    }
    return "Note -- sounds are currently not enabled.$vibrateInsteadSentence " +
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
            text = cueId.settingsLabel(),
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
                        onClick = { onModeChange(option) },
                        label = option.settingsLabel(),
                        horizontalPadding = 6,
                        modifier = Modifier.testTag("settings-${cueId.name}-${option.name}"),
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
                                    if (option == repeatCount) DEFAULT_TIMING_ALERT_REPEAT_COUNT else option
                            )
                        },
                        label = "x$option",
                        horizontalPadding = 8,
                        modifier = Modifier.testTag("settings-${cueId.name}-REPEAT_$option"),
                    )
                }
                }
            }
        }
    }
}

/// Return the settings label for a timing cue.
private fun TimingCueId.settingsLabel(): String {
    return when (this) {
        TimingCueId.MISCONDUCT_DEFENSE_TWENTY -> "20 seconds, defense (if offense is ready early)"
        else -> label
    }
}

/**
 * Render a compact selectable timing-alert option.
 *
 * @param selected Whether this option is currently selected.
 * @param label The visible option label.
 * @param modifier Optional layout and test-tag modifier.
 * @param horizontalPadding Horizontal text padding in density-independent pixels.
 * @param onClick Callback selecting this option.
 */
@Composable
private fun CompactTimingAlertOption(
    selected: Boolean,
    label: String,
    modifier: Modifier = Modifier,
    horizontalPadding: Int = 5,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier.semantics { this.selected = selected },
        shape = RoundedCornerShape(16.dp),
        color = if (selected) colorScheme.secondaryContainer else colorScheme.surface,
        contentColor = if (selected) colorScheme.onSecondaryContainer else colorScheme.onSurface,
        border = BorderStroke(1.dp, if (selected) colorScheme.secondary else colorScheme.outline),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = horizontalPadding.dp, vertical = 5.dp),
            maxLines = 1,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

/// Return the settings-page message for a global timing-alert mode.
private fun TimingAlertGlobalMode.settingsMessage(): String {
    return when (this) {
        TimingAlertGlobalMode.OFF -> "No sound or vibration will be used for any timing cues."
        TimingAlertGlobalMode.VIBRATION_ONLY -> {
            "Vibration will be used for any cues that are set to use sound."
        }
        TimingAlertGlobalMode.SOUNDS_ON -> "Ear buds are recommended when using sounds with UltiObserver."
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
        title = "Timeout or misconduct during point",
        cues = listOf(
            TimingCueId.TIMEOUT_CLEAR_FIELD,
            TimingCueId.TIMEOUT_OFFENSE_TWENTY,
            TimingCueId.TIMEOUT_OFFENSE_TEN,
            TimingCueId.TIMEOUT_COUNTDOWN_FROM_FIVE,
            TimingCueId.TIMEOUT_OFFENSE_FREEZE_DEFENSE_TWENTY,
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
        title = "Misconduct between points",
        cues = listOf(
            TimingCueId.MISCONDUCT_OFFENSE_TWENTY,
            TimingCueId.MISCONDUCT_OFFENSE_TEN,
            TimingCueId.MISCONDUCT_COUNTDOWN_FROM_FIVE,
            TimingCueId.MISCONDUCT_OFFENSE_FREEZE_DEFENSE_TWENTY,
            TimingCueId.MISCONDUCT_DEFENSE_TWENTY,
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
