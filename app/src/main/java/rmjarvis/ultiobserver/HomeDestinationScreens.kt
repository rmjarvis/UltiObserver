package rmjarvis.ultiobserver

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import kotlin.math.roundToLong

private const val SOURCE_CODE_URL = "https://github.com/rmjarvis/UltiObserver"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AboutScreen(
    versionName: String,
    onBackHome: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("About") },
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
                .padding(20.dp)
                .testTag("about-screen"),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "UltiObserver",
                style = MaterialTheme.typography.headlineMedium,
            )
            Text(
                text = "Version $versionName",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "A game management app for Ultimate observers to take the place of physical game cards and a stopwatch.",
                style = MaterialTheme.typography.bodyLarge,
            )
            HorizontalDivider()
            Text(
                text = "Source code",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = SOURCE_CODE_URL,
                modifier = Modifier.clickable { uriHandler.openUri(SOURCE_CODE_URL) },
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.bodyLarge,
            )
            Text(
                text = "If you notice any bugs or have requests for features to add, please go to the above GitHub page and make an issue.",
                style = MaterialTheme.typography.bodyLarge,
            )
        }
    }
}

// Profile fields for observer identity and home-screen avatar preference.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileScreen(
    name: String,
    avatarPreference: ObserverAvatarPreference,
    onNameChange: (String) -> Unit,
    onAvatarPreferenceChange: (ObserverAvatarPreference) -> Unit,
    onBackHome: () -> Unit,
) {
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Profile") },
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
            OutlinedTextField(
                value = name,
                onValueChange = onNameChange,
                label = { Text("Name") },
                singleLine = true,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("profile-name-field"),
            )
            AvatarPreferenceSelector(
                avatarPreference = avatarPreference,
                onAvatarPreferenceChange = { preference ->
                    onAvatarPreferenceChange(preference)
                    onBackHome()
                },
            )
        }
    }
}

@Composable
private fun AvatarPreferenceSelector(
    avatarPreference: ObserverAvatarPreference,
    onAvatarPreferenceChange: (ObserverAvatarPreference) -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = "Home avatar",
            style = MaterialTheme.typography.titleMedium,
        )

        RandomAvatarPreferenceButton(
            selected = avatarPreference == ObserverAvatarPreference.RANDOM,
            onClick = { onAvatarPreferenceChange(ObserverAvatarPreference.RANDOM) },
        )

        Text(
            text = "Or choose a specific avatar:",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            concreteObserverAvatarPreferences.chunked(3).forEach { rowPreferences ->
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                ) {
                    rowPreferences.forEach { preference ->
                        AvatarPreferenceButton(
                            preference = preference,
                            selected = avatarPreference == preference,
                            onClick = { onAvatarPreferenceChange(preference) },
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun RandomAvatarPreferenceButton(
    selected: Boolean,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) colorScheme.secondaryContainer else colorScheme.surface,
        contentColor = if (selected) colorScheme.onSecondaryContainer else colorScheme.onSurface,
        border = BorderStroke(1.dp, if (selected) colorScheme.secondary else colorScheme.outlineVariant),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("profile-avatar-RANDOM"),
    ) {
        Text(
            text = "Use a random avatar",
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

@Composable
private fun AvatarPreferenceButton(
    preference: ObserverAvatarPreference,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(8.dp),
        color = if (selected) colorScheme.secondaryContainer else colorScheme.surface,
        contentColor = if (selected) colorScheme.onSecondaryContainer else colorScheme.onSurface,
        border = BorderStroke(1.dp, if (selected) colorScheme.secondary else colorScheme.outlineVariant),
        modifier = modifier
            .aspectRatio(1f)
            .testTag("profile-avatar-${preference.name}"),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(2.dp),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(preference.drawableRes),
                contentDescription = preference.label,
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    timingAlertPreferences: TimingAlertPreferences,
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
                Text("Sound Settings for Individual Cues")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun TimingCueSettingsScreen(
    timingAlertPreferences: TimingAlertPreferences,
    onTimingCueModeChange: (TimingCueId, TimingAlertMode) -> Unit,
    onTimingCueRepeatCountChange: (TimingCueId, Int) -> Unit,
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
                title = { Text("Cue Sound Settings") },
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
                onPreview = { sound ->
                    if (timingAlertPreferences.globalMode == TimingAlertGlobalMode.SOUNDS_ON) {
                        timingAlertPlayer.play(sound, timingAlertPreferences.soundVolume)
                    }
                },
            )

            timingCueSections.forEach { section ->
                HorizontalDivider()
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
                    onValueChange = { onVibrationDurationChange(it.roundToLong()) },
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

@Composable
private fun SoundPreviewRow(
    title: String,
    sounds: List<TimingAlertSound>,
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
    }
}

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

private fun TimingAlertGlobalMode.settingsMessage(): String {
    return when (this) {
        TimingAlertGlobalMode.OFF -> "No sound or vibration will be used for any timing cues."
        TimingAlertGlobalMode.VIBRATION_ONLY -> {
            "Vibration will be used for any cues that are set to use sound."
        }
        TimingAlertGlobalMode.SOUNDS_ON -> "Ear buds are recommended when using sounds with UltiObserver."
    }
}

private fun TimingAlertMode.settingsLabel(): String {
    return when (this) {
        TimingAlertMode.NONE -> "Off"
        TimingAlertMode.VIBRATE -> "Vibrate"
        TimingAlertMode.TICK -> "Tick"
        TimingAlertMode.BEEP -> "Beep"
        TimingAlertMode.DING -> "Ding"
        TimingAlertMode.KNOCK -> "Knock"
    }
}

private val timingAlertOptions = listOf(
    TimingAlertMode.NONE,
    TimingAlertMode.VIBRATE,
    TimingAlertMode.TICK,
    TimingAlertMode.BEEP,
    TimingAlertMode.DING,
    TimingAlertMode.KNOCK,
)

private val timingAlertRepeatOptions = listOf(2, 3)

private data class TimingCueSection(
    val title: String,
    val cues: List<TimingCueId>,
)

private val timingCueSections = listOf(
    TimingCueSection(
        title = "Before Pull - Offense",
        cues = listOf(
            TimingCueId.RECEIVING_TWENTY_FOR_HAND,
            TimingCueId.RECEIVING_TEN_FOR_HAND,
            TimingCueId.RECEIVING_GIVE_HAND,
        ),
    ),
    TimingCueSection(
        title = "Before Pull - Defense",
        cues = listOf(
            TimingCueId.PULLING_TWENTY_TO_PULL,
            TimingCueId.PULLING_TEN_TO_PULL,
            TimingCueId.PULLING_TIME_VIOLATION,
        ),
    ),
    TimingCueSection(
        title = "Timeout or Misconduct During Point",
        cues = listOf(
            TimingCueId.TIMEOUT_CLEAR_FIELD,
            TimingCueId.TIMEOUT_OFFENSE_TWENTY,
            TimingCueId.TIMEOUT_OFFENSE_TEN,
            TimingCueId.TIMEOUT_COUNTDOWN_FROM_FIVE,
            TimingCueId.TIMEOUT_OFFENSE_FREEZE_DEFENSE_TWENTY,
        ),
    ),
    TimingCueSection(
        title = "Timeout Between Points",
        cues = listOf(
            TimingCueId.TIMEOUT_BETWEEN_POINTS_ONE_MINUTE_FOR_HAND,
            TimingCueId.TIMEOUT_BETWEEN_POINTS_ONE_MINUTE_TO_PULL,
        ),
    ),
    TimingCueSection(
        title = "Misconduct Between Points",
        cues = listOf(
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

// Archived game list, separated from Home so the launch screen has more room.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PreviousGamesScreen(
    previousGames: List<ArchivedGameListEntry>,
    onOpenPreviousGame: (Int) -> Unit,
    onDeletePreviousGame: (Int) -> Unit,
    onDeleteAllPreviousGames: () -> Unit,
    onBackHome: () -> Unit,
) {
    var pendingDeleteIndex by remember { mutableStateOf<Int?>(null) }
    var pendingDeleteAll by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Previous Games") },
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
                .padding(20.dp)
                .testTag("previous-games-screen"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (previousGames.isEmpty()) {
                Text("No completed games yet.")
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = { pendingDeleteAll = true },
                        modifier = Modifier.testTag("delete-all-archived-games"),
                    ) {
                        Text("Delete All")
                    }
                }
                previousGames.forEachIndexed { index, game ->
                    ArchivedGameRow(
                        entry = game,
                        onClick = { onOpenPreviousGame(index) },
                        onDelete = { pendingDeleteIndex = index },
                    )
                }
            }
        }
    }

    val deleteIndex = pendingDeleteIndex
    if (deleteIndex != null) {
        DeleteGameDialog(
            onDismiss = { pendingDeleteIndex = null },
            onConfirmDelete = {
                pendingDeleteIndex = null
                onDeletePreviousGame(deleteIndex)
            },
        )
    }
    if (pendingDeleteAll) {
        DeleteGameDialog(
            onDismiss = { pendingDeleteAll = false },
            onConfirmDelete = {
                pendingDeleteAll = false
                onDeleteAllPreviousGames()
            },
            title = "Delete All Games?",
            message = "Completely delete all previous game data? This cannot be undone.",
        )
    }
}

// Archived game row with a separate right-side delete action.
@Composable
private fun ArchivedGameRow(
    entry: ArchivedGameListEntry,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GameListRow(
            startDateTime = entry.startDateTime,
            scoreLine = entry.scoreLine,
            onClick = onClick,
            modifier = Modifier
                .weight(1f)
                .testTag("archived-game-${entry.scoreLine}"),
        )
        IconButton(
            onClick = onDelete,
            modifier = Modifier.testTag("delete-archived-game-${entry.scoreLine}"),
        ) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "Delete ${entry.scoreLine}",
            )
        }
    }
}
