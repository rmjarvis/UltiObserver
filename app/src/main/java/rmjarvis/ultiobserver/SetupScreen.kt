package rmjarvis.ultiobserver

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private enum class RuleEditTarget(
    val dialogTitle: String,
    val fieldLabel: String,
    val prefixText: String? = null,
    val suffixText: String? = null,
) {
    GAME_TO(
        dialogTitle = "Game To",
        fieldLabel = "Points",
    ),
    HALFTIME(
        dialogTitle = "Halftime",
        fieldLabel = "Minutes",
    ),
    HALF(
        dialogTitle = "Half Cap",
        fieldLabel = "Minutes",
        prefixText = "Half Cap at:",
        suffixText = "minutes after start time.",
    ),
    SOFT(
        dialogTitle = "Soft Cap",
        fieldLabel = "Minutes",
        prefixText = "Soft Cap at:",
        suffixText = "minutes after start time.",
    ),
    HARD(
        dialogTitle = "Hard Cap",
        fieldLabel = "Minutes",
        prefixText = "Hard Cap at:",
        suffixText = "minutes after start time.",
    ),
}

// Pregame/edit-game setup form for start time, teams, pull, rules, and prior cards.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SetupScreen(
    state: GameSetupState,
    onStateChange: (GameSetupState) -> Unit,
    primaryButtonLabel: String,
    onPrimaryAction: () -> Unit,
) {
    var showPlayerDialog by remember { mutableStateOf(false) }
    var showStartDateDialog by remember { mutableStateOf(false) }
    var showStartTimeDialog by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<RuleEditTarget?>(null) }
    var showTimeoutRulesDialog by remember { mutableStateOf(false) }
    val scrollState = rememberScrollState()

    // Compose the setup screen as a scrollable form plus modal editors.
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(title = { Text("UltiObserver Setup") })
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Start time entry with quick +/- 5 minute nudges.
            SectionCard(title = "Game Start") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DateTimeDisplayField(
                        label = "Date",
                        value = formatStartDate(state.startDate),
                        modifier = Modifier.weight(1f),
                        onClick = { showStartDateDialog = true },
                    )
                    SmallActionButton(label = "-1d") {
                        onStateChange(state.copy(startDate = state.startDate.minusDays(1)))
                    }
                    SmallActionButton(label = "+1d") {
                        onStateChange(state.copy(startDate = state.startDate.plusDays(1)))
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    DateTimeDisplayField(
                        label = "Start time",
                        value = formatClockTime(state.startTime),
                        modifier = Modifier.weight(1f),
                        onClick = { showStartTimeDialog = true },
                    )
                    SmallActionButton(label = "-5") {
                        onStateChange(state.copy(startTime = state.startTime.minusMinutes(5)))
                    }
                    SmallActionButton(label = "+5") {
                        onStateChange(state.copy(startTime = state.startTime.plusMinutes(5)))
                    }
                }
            }

            // Team names and colors.
            SectionCard(title = "Team Info") {
                TeamEditor(
                    fieldLabel = "Team 1",
                    team = state.teamOne,
                    onTeamChange = { onStateChange(state.copy(teamOne = it)) },
                )
                Spacer(modifier = Modifier.height(12.dp))
                TeamEditor(
                    fieldLabel = "Team 2",
                    team = state.teamTwo,
                    onTeamChange = { onStateChange(state.copy(teamTwo = it)) },
                )
            }

            // Which team pulls first, and from which end.
            SectionCard(title = "Starting Pull") {
                Text("Pulling team", fontWeight = FontWeight.SemiBold)
                TeamChoiceRow(
                    firstLabel = state.teamOne.name,
                    secondLabel = state.teamTwo.name,
                    selected = state.pullingTeam,
                    testTagPrefix = "setup-pulling-team",
                    onSelected = { onStateChange(state.copy(pullingTeam = it)) },
                )
                Spacer(modifier = Modifier.height(12.dp))
                Text("Pulling from", fontWeight = FontWeight.SemiBold)
                FieldEndChoiceRow(
                    selected = state.pullingFromEnd,
                    onSelected = { onStateChange(state.copy(pullingFromEnd = it)) },
                )
            }

            // Game-length, cap, and timeout rules.
            SectionCard(title = "Game Rules") {
                EditableValueRow(
                    label = "Game to",
                    value = state.rules.gameTo.toString(),
                    onClick = { editingRule = RuleEditTarget.GAME_TO },
                )
                EditableValueRow(
                    label = "Halftime",
                    value = "${state.rules.halftimeMinutes} min",
                    onClick = { editingRule = RuleEditTarget.HALFTIME },
                )
                EditableValueRow(
                    label = "Half cap",
                    value = if (state.rules.useHalfCap) "+${state.rules.halfCapMinutes}" else "None",
                    onClick = { editingRule = RuleEditTarget.HALF },
                )
                EditableValueRow(
                    label = "Soft cap",
                    value = if (state.rules.useSoftCap) "+${state.rules.softCapMinutes}" else "None",
                    onClick = { editingRule = RuleEditTarget.SOFT },
                )
                EditableValueRow(
                    label = "Hard cap",
                    value = if (state.rules.useHardCap) "+${state.rules.hardCapMinutes}" else "None",
                    onClick = { editingRule = RuleEditTarget.HARD },
                )
                EditableValueRow(
                    label = "Timeouts",
                    value = formatTimeoutRules(state.rules),
                    onClick = { showTimeoutRulesDialog = true },
                )
            }

            // Players who are already carrying cards from previous games.
            SectionCard(title = "Cards from Previous Games") {
                if (state.priorCards.isEmpty()) {
                    Text("No prior cards recorded yet.")
                } else {
                    state.priorCards.forEachIndexed { index, record ->
                        val teamName = if (record.team == TeamId.TEAM_ONE) {
                            state.teamOne.name
                        } else {
                            state.teamTwo.name
                        }
                        PlayerRecordRow(
                            label = "$teamName #${record.jerseyNumber}",
                            detail = buildPlayerCardDetail(record),
                            onRemove = {
                                onStateChange(
                                    state.copy(priorCards = state.priorCards.filterIndexed { i, _ -> i != index })
                                )
                            },
                        )
                    }
                }
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(onClick = { showPlayerDialog = true }) {
                    Text("Add Card Holder")
                }
            }

            // Start a new game, or return to the live screen when editing setup midgame.
            Button(
                onClick = onPrimaryAction,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(primaryButtonLabel)
            }
        }
    }

    // Modal for adding a player who already has cards from earlier games.
    if (showPlayerDialog) {
        AddPlayerCardDialog(
            firstTeamName = state.teamOne.name,
            secondTeamName = state.teamTwo.name,
            onDismiss = { showPlayerDialog = false },
            onConfirm = { record ->
                onStateChange(state.copy(priorCards = state.priorCards + record))
                showPlayerDialog = false
            },
        )
    }

    // Modal for exact start-date entry.
    if (showStartDateDialog) {
        StartDateDialog(
            initialDate = state.startDate,
            onDismiss = { showStartDateDialog = false },
            onConfirm = {
                onStateChange(state.copy(startDate = it))
                showStartDateDialog = false
            },
        )
    }

    // Modal for exact start-time entry.
    if (showStartTimeDialog) {
        ExactTimeDialog(
            initialTime = state.startTime,
            onDismiss = { showStartTimeDialog = false },
            onConfirm = {
                onStateChange(state.copy(startTime = it))
                showStartTimeDialog = false
            },
        )
    }

    // Modal rule editors for the currently selected rules field.
    if (editingRule != null) {
        val target = editingRule!!
        when (target) {
            RuleEditTarget.GAME_TO -> {
                IntegerEditDialog(
                    title = target.dialogTitle,
                    fieldLabel = target.fieldLabel,
                    initialValue = state.rules.gameTo,
                    onDismiss = { editingRule = null },
                    onConfirm = { newValue ->
                        onStateChange(
                            state.copy(
                                rules = state.rules.copy(gameTo = newValue.coerceAtLeast(1))
                            )
                        )
                        editingRule = null
                    },
                )
            }

            RuleEditTarget.HALFTIME -> {
                IntegerEditDialog(
                    title = target.dialogTitle,
                    fieldLabel = target.fieldLabel,
                    initialValue = state.rules.halftimeMinutes,
                    onDismiss = { editingRule = null },
                    onConfirm = { newValue ->
                        onStateChange(
                            state.copy(
                                rules = state.rules.copy(halftimeMinutes = newValue.coerceAtLeast(1))
                            )
                        )
                        editingRule = null
                    },
                )
            }

            RuleEditTarget.HALF -> {
                CapRuleEditDialog(
                    title = target.dialogTitle,
                    fieldLabel = target.fieldLabel,
                    prefixText = target.prefixText,
                    suffixText = target.suffixText,
                    initialValue = state.rules.halfCapMinutes,
                    initiallyEnabled = state.rules.useHalfCap,
                    onDismiss = { editingRule = null },
                    onConfirm = { enabled, newValue ->
                        onStateChange(
                            state.copy(
                                rules = state.rules.copy(useHalfCap = enabled, halfCapMinutes = newValue)
                            )
                        )
                        editingRule = null
                    },
                )
            }

            RuleEditTarget.SOFT -> {
                CapRuleEditDialog(
                    title = target.dialogTitle,
                    fieldLabel = target.fieldLabel,
                    prefixText = target.prefixText,
                    suffixText = target.suffixText,
                    initialValue = state.rules.softCapMinutes,
                    initiallyEnabled = state.rules.useSoftCap,
                    onDismiss = { editingRule = null },
                    onConfirm = { enabled, newValue ->
                        onStateChange(
                            state.copy(
                                rules = state.rules.copy(useSoftCap = enabled, softCapMinutes = newValue)
                            )
                        )
                        editingRule = null
                    },
                )
            }

            RuleEditTarget.HARD -> {
                CapRuleEditDialog(
                    title = target.dialogTitle,
                    fieldLabel = target.fieldLabel,
                    prefixText = target.prefixText,
                    suffixText = target.suffixText,
                    initialValue = state.rules.hardCapMinutes,
                    initiallyEnabled = state.rules.useHardCap,
                    onDismiss = { editingRule = null },
                    onConfirm = { enabled, newValue ->
                        onStateChange(
                            state.copy(
                                rules = state.rules.copy(useHardCap = enabled, hardCapMinutes = newValue)
                            )
                        )
                        editingRule = null
                    },
                )
            }
        }
    }

    // Modal editor for the timeout rule bundle.
    if (showTimeoutRulesDialog) {
        TimeoutRulesDialog(
            rules = state.rules,
            onDismiss = { showTimeoutRulesDialog = false },
            onConfirm = { updatedRules ->
                onStateChange(state.copy(rules = updatedRules))
                showTimeoutRulesDialog = false
            },
        )
    }
}

// Standard Material date picker for the setup start date.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun StartDateDialog(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = dateToPickerTimestamp(initialDate),
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                onClick = {
                    val selectedTimestamp = datePickerState.selectedDateMillis!!
                    onConfirm(pickerTimestampToDate(selectedTimestamp))
                }
            ) {
                Text("Set")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    ) {
        DatePicker(
            state = datePickerState,
            title = {
                Text(
                    text = "Set Start Date",
                    modifier = Modifier.padding(horizontal = 24.dp),
                )
            },
        )
    }
}

// Standard Material time input dialog for the setup start time.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ExactTimeDialog(
    initialTime: LocalTime,
    onDismiss: () -> Unit,
    onConfirm: (LocalTime) -> Unit,
) {
    val timePickerState = rememberTimePickerState(
        initialHour = initialTime.hour,
        initialMinute = initialTime.minute,
        is24Hour = false,
    )

    TimePickerDialog(
        onDismissRequest = onDismiss,
        title = { Text("Set Start Time") },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(LocalTime.of(timePickerState.hour, timePickerState.minute))
                }
            ) {
                Text("Set")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    ) {
        TimeInput(state = timePickerState)
    }
}

// Reusable integer-entry dialog for simple numeric rule values.
@Composable
private fun IntegerEditDialog(
    title: String,
    fieldLabel: String,
    initialValue: Int,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var valueText by remember { mutableStateOf(initialValue.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = valueText,
                    onValueChange = { valueText = it.filter(Char::isDigit) },
                    label = { Text(fieldLabel) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(valueText.toIntOrNull()?.coerceAtLeast(0) ?: initialValue)
                }
            ) {
                Text("Set")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

// Integer-entry dialog for a cap rule, with an explicit None toggle.
@Composable
private fun CapRuleEditDialog(
    title: String,
    fieldLabel: String,
    prefixText: String? = null,
    suffixText: String? = null,
    initialValue: Int,
    initiallyEnabled: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (Boolean, Int) -> Unit,
) {
    var valueText by remember { mutableStateOf(initialValue.toString()) }
    var enabled by remember { mutableStateOf(initiallyEnabled) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("None")
                    Checkbox(
                        checked = !enabled,
                        onCheckedChange = { enabled = !it },
                        modifier = Modifier.testTag("setup-$title-none"),
                    )
                }
                if (prefixText != null) {
                    Text(prefixText)
                }
                OutlinedTextField(
                    value = valueText,
                    onValueChange = { valueText = it.filter(Char::isDigit) },
                    label = { Text(fieldLabel) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    enabled = enabled,
                )
                if (suffixText != null) {
                    Text(suffixText)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(enabled, valueText.toIntOrNull()?.coerceAtLeast(0) ?: initialValue)
                }
            ) {
                Text("Set")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

// Editor for per-half timeouts and the optional floater.
@Composable
private fun TimeoutRulesDialog(
    rules: GameRules,
    onDismiss: () -> Unit,
    onConfirm: (GameRules) -> Unit,
) {
    var timeoutsText by remember { mutableStateOf(rules.timeoutsPerHalf.toString()) }
    var hasFloater by remember { mutableStateOf(rules.hasFloaterTimeout) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Timeout Rules") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = timeoutsText,
                    onValueChange = { timeoutsText = it.filter(Char::isDigit).take(2) },
                    label = { Text("Timeouts per half") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("+ a floater")
                    Checkbox(
                        checked = hasFloater,
                        onCheckedChange = { hasFloater = it },
                        modifier = Modifier.testTag("setup-timeouts-floater"),
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        rules.copy(
                            timeoutsPerHalf = timeoutsText.toIntOrNull()?.coerceAtLeast(0) ?: rules.timeoutsPerHalf,
                            hasFloaterTimeout = hasFloater,
                        )
                    )
                }
            ) {
                Text("Set")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

// Setup dialog for recording a player who already has cards from earlier games.
@Composable
private fun AddPlayerCardDialog(
    firstTeamName: String,
    secondTeamName: String,
    onDismiss: () -> Unit,
    onConfirm: (PlayerCardRecord) -> Unit,
) {
    var selectedTeam by remember { mutableStateOf(TeamId.TEAM_ONE) }
    var jerseyNumber by remember { mutableStateOf("") }
    var priorYellows by remember { mutableStateOf(1) }
    var priorReds by remember { mutableStateOf(0) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add player cards") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TeamChoiceRow(
                    firstLabel = firstTeamName,
                    secondLabel = secondTeamName,
                    selected = selectedTeam,
                    testTagPrefix = "setup-prior-card-team",
                    onSelected = { selectedTeam = it },
                )
                OutlinedTextField(
                    value = jerseyNumber,
                    onValueChange = { jerseyNumber = it.filter(Char::isDigit) },
                    label = { Text("Jersey number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.testTag("setup-prior-card-jersey"),
                )
                SmallCountEditor(
                    label = "Prior yellows",
                    value = priorYellows,
                    onValueChange = { priorYellows = it.coerceAtLeast(0) },
                )
                SmallCountEditor(
                    label = "Prior reds",
                    value = priorReds,
                    onValueChange = { priorReds = it.coerceAtLeast(0) },
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        PlayerCardRecord(
                            team = selectedTeam,
                            jerseyNumber = jerseyNumber.ifBlank { "0" },
                            priorYellows = priorYellows,
                            priorReds = priorReds,
                        )
                    )
                }
            ) {
                Text("Add")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

// Name-and-color editor for one setup team.
@Composable
private fun TeamEditor(
    fieldLabel: String,
    team: TeamSetup,
    onTeamChange: (TeamSetup) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = team.name,
            onValueChange = { onTeamChange(team.copy(name = it)) },
            label = { Text(fieldLabel) },
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("setup-$fieldLabel-name"),
        )
        ColorChoiceRow(
            selected = team.color,
            testTagPrefix = "setup-$fieldLabel-color",
            onSelected = { onTeamChange(team.copy(color = it)) },
        )
    }
}

// Clickable setup field that looks like a compact form control.
@Composable
private fun DateTimeDisplayField(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

// Button-styled row for setup values that open an editor dialog.
@Composable
private fun EditableValueRow(
    label: String,
    value: String,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(label)
            Text(value, fontWeight = FontWeight.Bold)
        }
    }
}

// Single-row palette for choosing the team color.
@Composable
private fun ColorChoiceRow(
    selected: TeamColorChoice,
    testTagPrefix: String,
    onSelected: (TeamColorChoice) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TeamColorChoice.entries.forEach { colorChoice ->
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp)
                    .testTag("$testTagPrefix-${colorChoice.name}")
                    .background(
                        color = if (selected == colorChoice) Color.Black else Color.Transparent,
                        shape = RoundedCornerShape(6.dp),
                    )
                    .padding(if (selected == colorChoice) 1.dp else 0.dp)
                    .background(
                        color = if (selected == colorChoice) Color(0xFFF2D23C) else Color.Transparent,
                        shape = RoundedCornerShape(6.dp),
                    )
                    .padding(if (selected == colorChoice) 3.dp else 0.dp)
                    .background(
                        color = if (selected == colorChoice) Color.Black else Color.Transparent,
                        shape = RoundedCornerShape(5.dp),
                    )
                    .padding(if (selected == colorChoice) 1.dp else 0.dp)
                    .border(
                        width = if (selected == colorChoice) 0.dp else 1.dp,
                        color = MaterialTheme.colorScheme.outlineVariant,
                        shape = RoundedCornerShape(4.dp),
                    )
                    .background(colorChoice.accent, RoundedCornerShape(4.dp))
                    .clickable { onSelected(colorChoice) }
            )
        }
    }
}

// Two-choice row for Team 1 vs Team 2 selection.
@Composable
private fun TeamChoiceRow(
    firstLabel: String,
    secondLabel: String,
    selected: TeamId,
    testTagPrefix: String? = null,
    onSelected: (TeamId) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = selected == TeamId.TEAM_ONE,
            onClick = { onSelected(TeamId.TEAM_ONE) },
            modifier = testTagPrefix?.let { Modifier.testTag("$it-${TeamId.TEAM_ONE.name}") } ?: Modifier,
            label = { Text(firstLabel.ifBlank { "Team 1" }) },
        )
        FilterChip(
            selected = selected == TeamId.TEAM_TWO,
            onClick = { onSelected(TeamId.TEAM_TWO) },
            modifier = testTagPrefix?.let { Modifier.testTag("$it-${TeamId.TEAM_TWO.name}") } ?: Modifier,
            label = { Text(secondLabel.ifBlank { "Team 2" }) },
        )
    }
}

// Two-choice row for Far end vs Near end selection.
@Composable
private fun FieldEndChoiceRow(
    selected: FieldEnd,
    onSelected: (FieldEnd) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = selected == FieldEnd.FAR,
            onClick = { onSelected(FieldEnd.FAR) },
            label = { Text("Far end") },
        )
        FilterChip(
            selected = selected == FieldEnd.NEAR,
            onClick = { onSelected(FieldEnd.NEAR) },
            label = { Text("Near end") },
        )
    }
}

// Small +/- editor for integer setup/correction values.
@Composable
internal fun SmallCountEditor(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(label, fontWeight = FontWeight.SemiBold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            SmallActionButton(label = "-1") {
                onValueChange((value - 1).coerceAtLeast(0))
            }
            Text(
                value.toString(),
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
            )
            SmallActionButton(label = "+1") {
                onValueChange(value + 1)
            }
        }
    }
}

// One row in the setup list of players carrying prior cards.
@Composable
private fun PlayerRecordRow(
    label: String,
    detail: String,
    onRemove: () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column {
                Text(label, fontWeight = FontWeight.SemiBold)
                Text(detail)
            }
            TextButton(onClick = onRemove) {
                Text("Remove")
            }
        }
    }
}

// Compact setup summary for prior yellows/reds.
private fun buildPlayerCardDetail(record: PlayerCardRecord): String {
    return if (record.priorReds > 0) {
        "Y ${record.priorYellows}  R ${record.priorReds}"
    } else {
        "Y ${record.priorYellows}"
    }
}

internal fun formatStartDate(date: LocalDate): String {
    return date.format(DateTimeFormatter.ofPattern("MMM d, yyyy"))
}

private fun dateToPickerTimestamp(date: LocalDate): Long {
    return date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}

private fun pickerTimestampToDate(timestamp: Long): LocalDate {
    return Instant.ofEpochMilli(timestamp).atZone(ZoneOffset.UTC).toLocalDate()
}

// Display timeout rules in the compact setup format.
private fun formatTimeoutRules(rules: GameRules): String {
    return buildString {
        append("${rules.timeoutsPerHalf}/half")
        if (rules.hasFloaterTimeout) {
            append(" + floater")
        }
    }
}
