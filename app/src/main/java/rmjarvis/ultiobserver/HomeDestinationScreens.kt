package rmjarvis.ultiobserver

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

// Profile placeholder with the first real user-editable field.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ProfileScreen(
    name: String,
    onNameChange: (String) -> Unit,
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
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SettingsScreen(
    timingAlertPreferences: TimingAlertPreferences,
    onGlobalModeChange: (TimingAlertGlobalMode) -> Unit,
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
                        timingAlertPlayer.play(sound)
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
                        mode = timingAlertPreferences.cueModes[cueId] ?: cueId.defaultAlertMode(),
                        onModeChange = { mode -> onTimingCueModeChange(cueId, mode) },
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
            text = "Use sounds and vibration for cues?",
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
    onModeChange: (TimingAlertMode) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = cueId.label,
            style = MaterialTheme.typography.bodyMedium,
        )
        FlowRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            timingAlertOptions.forEach { option ->
                CompactTimingAlertOption(
                    selected = option == mode,
                    onClick = { onModeChange(option) },
                    label = option.settingsLabel(),
                    modifier = Modifier.testTag("settings-${cueId.name}-${option.name}"),
                )
            }
        }
    }
}

@Composable
private fun CompactTimingAlertOption(
    selected: Boolean,
    label: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    val colorScheme = MaterialTheme.colorScheme
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) colorScheme.secondaryContainer else colorScheme.surface,
        contentColor = if (selected) colorScheme.onSecondaryContainer else colorScheme.onSurface,
        border = BorderStroke(1.dp, if (selected) colorScheme.secondary else colorScheme.outline),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = 7.dp, vertical = 5.dp),
            maxLines = 1,
            style = MaterialTheme.typography.labelMedium,
        )
    }
}

private fun TimingAlertMode.settingsLabel(): String {
    return when (this) {
        TimingAlertMode.NONE -> "Off"
        TimingAlertMode.VIBRATE -> "Vibrate"
        TimingAlertMode.TICK -> "Tick"
        TimingAlertMode.BEEP -> "Beep"
        TimingAlertMode.DING -> "Ding"
        TimingAlertMode.DOUBLE_TICK -> "2 Tick"
    }
}

private val timingAlertOptions = listOf(
    TimingAlertMode.NONE,
    TimingAlertMode.VIBRATE,
    TimingAlertMode.TICK,
    TimingAlertMode.BEEP,
    TimingAlertMode.DING,
    TimingAlertMode.DOUBLE_TICK,
)

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
            TimingCueId.PULLING_DELAY_OF_GAME,
        ),
    ),
    TimingCueSection(
        title = "Timeout During Point",
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
        title = "Halftime",
        cues = listOf(
            TimingCueId.HALFTIME_FIVE_MINUTES,
            TimingCueId.HALFTIME_TWO_MINUTES,
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
