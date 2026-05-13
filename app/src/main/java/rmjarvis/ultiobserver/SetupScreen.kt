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
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

private enum class RuleEditTarget(
    val dialogTitle: String,
    val fieldLabel: String,
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
    ),
    SOFT(
        dialogTitle = "Soft Cap",
        fieldLabel = "Minutes",
    ),
    HARD(
        dialogTitle = "Hard Cap",
        fieldLabel = "Minutes",
    ),
}

private enum class SetupEditor {
    START_TIME,
    STARTING_PULL,
    GAME_RULES,
    PRIOR_CARDS,
}

// Pregame/edit-game setup form for start time, teams, pull, rules, and prior cards.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SetupScreen(
    state: GameSetupState,
    onStateChange: (GameSetupState) -> Unit,
    primaryButtonLabel: String,
    onPrimaryAction: () -> Unit,
    onBackHome: () -> Unit,
) {
    var showPlayerDialog by remember { mutableStateOf(false) }
    var showStartDateDialog by remember { mutableStateOf(false) }
    var showStartTimeDialog by remember { mutableStateOf(false) }
    var editingRule by remember { mutableStateOf<RuleEditTarget?>(null) }
    var showTimeoutRulesDialog by remember { mutableStateOf(false) }
    var setupEditor by remember { mutableStateOf<SetupEditor?>(null) }
    val scrollState = rememberScrollState()

    // Compose the setup screen as compact overview rows plus modal editors.
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("UltiObserver Setup") },
                navigationIcon = {
                    TextButton(onClick = onBackHome) {
                        Text("Back")
                    }
                },
            )
        },
        bottomBar = {
            Button(
                onClick = onPrimaryAction,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
            ) {
                Text(primaryButtonLabel)
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(scrollState)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // Team names and colors.
            SetupFieldBox(title = "Teams") {
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

            SetupSummaryRow(
                title = "Start Time",
                summary = state.startTimeSummary(),
                editTag = "setup-edit-start-time",
                onEdit = { setupEditor = SetupEditor.START_TIME },
            )
            SetupSummaryRow(
                title = "Starting Pull",
                summary = state.startingPullSummary(),
                editTag = "setup-edit-starting-pull",
                onEdit = { setupEditor = SetupEditor.STARTING_PULL },
            )
            SetupSummaryRow(
                title = "Game Rules",
                editTag = "setup-edit-game-rules",
                onEdit = { setupEditor = SetupEditor.GAME_RULES },
            ) {
                GameRulesSummary(state.rules)
            }
            SetupSummaryRow(
                title = "Cards from Previous Games",
                summary = state.priorCardsSummary(),
                editTag = "setup-edit-prior-cards",
                onEdit = { setupEditor = SetupEditor.PRIOR_CARDS },
            )
        }
    }

    when (setupEditor) {
        SetupEditor.START_TIME -> {
            StartTimeSetupDialog(
                state = state,
                onStateChange = onStateChange,
                onEditDate = { showStartDateDialog = true },
                onEditTime = { showStartTimeDialog = true },
                onDismiss = { setupEditor = null },
            )
        }

        SetupEditor.STARTING_PULL -> {
            StartingPullSetupDialog(
                state = state,
                onStateChange = onStateChange,
                onDismiss = { setupEditor = null },
            )
        }

        SetupEditor.GAME_RULES -> {
            GameRulesSetupDialog(
                rules = state.rules,
                onEditRule = { editingRule = it },
                onEditTimeouts = { showTimeoutRulesDialog = true },
                onDismiss = { setupEditor = null },
            )
        }

        SetupEditor.PRIOR_CARDS -> {
            PriorCardsSetupDialog(
                state = state,
                onStateChange = onStateChange,
                onAddPlayer = { showPlayerDialog = true },
                onDismiss = { setupEditor = null },
            )
        }

        null -> Unit
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
                    prefixText = "Half Cap at:",
                    suffixText = "minutes after start time.",
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
                    prefixText = "Soft Cap at:",
                    suffixText = "minutes after start time.",
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
                    prefixText = "Hard Cap at:",
                    suffixText = "minutes after start time.",
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

// Setup overview box with an understated field label.
@Composable
private fun SetupFieldBox(
    title: String,
    content: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(8.dp),
                )
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            content()
        }
    }
}

// Compact overview row for setup categories that open detailed editors.
@Composable
private fun SetupSummaryRow(
    title: String,
    summary: String,
    editTag: String,
    onEdit: () -> Unit,
) {
    SetupSummaryRow(
        title = title,
        editTag = editTag,
        onEdit = onEdit,
    ) {
        Text(
            text = summary,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// Compact overview row for setup categories with custom summary content.
@Composable
private fun SetupSummaryRow(
    title: String,
    editTag: String,
    onEdit: () -> Unit,
    summaryContent: @Composable () -> Unit,
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = RoundedCornerShape(8.dp),
                )
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp),
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                summaryContent()
            }
            OutlinedButton(
                onClick = onEdit,
                modifier = Modifier.testTag(editTag),
            ) {
                Text("Edit")
            }
        }
    }
}

// Two-column summary so Half and TO align visually.
@Composable
private fun GameRulesSummary(rules: GameRules) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            SetupSummaryValue("Game to ${rules.gameTo}")
            SetupSummaryValue("Caps: ${rules.capRulesSummary()}")
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            SetupSummaryValue("Half: ${rules.halftimeMinutes} min")
            SetupSummaryValue("TO: ${rules.timeoutSummary()}")
        }
    }
}

@Composable
private fun SetupSummaryValue(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

// Start-date and start-time controls shown from the compact setup overview.
@Composable
private fun StartTimeSetupDialog(
    state: GameSetupState,
    onStateChange: (GameSetupState) -> Unit,
    onEditDate: () -> Unit,
    onEditTime: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Start Time") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = "Date",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DateTimeDisplayField(
                    value = formatStartDate(state.startDate),
                    testTag = "setup-start-date-field",
                    onClick = onEditDate,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SmallActionButton(label = "-1d") {
                        onStateChange(state.copy(startDate = state.startDate.minusDays(1)))
                    }
                    SmallActionButton(label = "+1d") {
                        onStateChange(state.copy(startDate = state.startDate.plusDays(1)))
                    }
                }

                Text(
                    text = "Time",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DateTimeDisplayField(
                    value = formatClockTime(state.startTime),
                    testTag = "setup-start-time-field",
                    onClick = onEditTime,
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SmallActionButton(label = "-5m") {
                        onStateChange(state.copy(startTime = state.startTime.minusMinutes(5)))
                    }
                    SmallActionButton(label = "+5m") {
                        onStateChange(state.copy(startTime = state.startTime.plusMinutes(5)))
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        },
    )
}

// Opening pull controls shown from the compact setup overview.
@Composable
private fun StartingPullSetupDialog(
    state: GameSetupState,
    onStateChange: (GameSetupState) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Starting Pull") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Pulling team", fontWeight = FontWeight.SemiBold)
                TeamChoiceRow(
                    firstLabel = state.teamOne.name,
                    secondLabel = state.teamTwo.name,
                    selected = state.pullingTeam,
                    testTagPrefix = "setup-pulling-team",
                    onSelected = { onStateChange(state.copy(pullingTeam = it)) },
                )
                Text("Pulling from", fontWeight = FontWeight.SemiBold)
                FieldEndChoiceRow(
                    selected = state.pullingFromEnd,
                    onSelected = { onStateChange(state.copy(pullingFromEnd = it)) },
                )
                Text(
                    text = "Near end is the end of the field you are primarily responsible for covering.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        },
    )
}

// Rule rows shown from the compact setup overview.
@Composable
private fun GameRulesSetupDialog(
    rules: GameRules,
    onEditRule: (RuleEditTarget) -> Unit,
    onEditTimeouts: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Game Rules") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EditableValueRow(
                    label = "Game to",
                    value = rules.gameTo.toString(),
                    onClick = { onEditRule(RuleEditTarget.GAME_TO) },
                )
                EditableValueRow(
                    label = "Halftime",
                    value = "${rules.halftimeMinutes} min",
                    onClick = { onEditRule(RuleEditTarget.HALFTIME) },
                )
                EditableValueRow(
                    label = "Half cap",
                    value = if (rules.useHalfCap) "+${rules.halfCapMinutes}" else "None",
                    onClick = { onEditRule(RuleEditTarget.HALF) },
                )
                EditableValueRow(
                    label = "Soft cap",
                    value = if (rules.useSoftCap) "+${rules.softCapMinutes}" else "None",
                    onClick = { onEditRule(RuleEditTarget.SOFT) },
                )
                EditableValueRow(
                    label = "Hard cap",
                    value = if (rules.useHardCap) "+${rules.hardCapMinutes}" else "None",
                    onClick = { onEditRule(RuleEditTarget.HARD) },
                )
                EditableValueRow(
                    label = "Timeouts",
                    value = rules.formatTimeoutRules(),
                    onClick = onEditTimeouts,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        },
    )
}

// Prior-card controls shown from the compact setup overview.
@Composable
private fun PriorCardsSetupDialog(
    state: GameSetupState,
    onStateChange: (GameSetupState) -> Unit,
    onAddPlayer: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cards from Previous Games") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (state.priorCards.isEmpty()) {
                    Text("No prior cards recorded yet.")
                } else {
                    state.priorCards.forEachIndexed { index, record ->
                        PlayerRecordRow(
                            label = "${record.team.setupName(state)} #${record.jerseyNumber}",
                            detail = record.playerCardDetail(),
                            onRemove = {
                                onStateChange(
                                    state.copy(priorCards = state.priorCards.filterIndexed { i, _ -> i != index })
                                )
                            },
                        )
                    }
                }
                OutlinedButton(onClick = onAddPlayer) {
                    Text("Add Card Holder")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Done")
            }
        },
    )
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
    prefixText: String,
    suffixText: String,
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
                Text(prefixText)
                OutlinedTextField(
                    value = valueText,
                    onValueChange = { valueText = it.filter(Char::isDigit) },
                    label = { Text(fieldLabel) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    enabled = enabled,
                )
                Text(suffixText)
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
    value: String,
    testTag: String,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        color = MaterialTheme.colorScheme.surface,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outline,
                    shape = RoundedCornerShape(12.dp),
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleLarge,
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
private fun PlayerCardRecord.playerCardDetail(): String {
    return if (priorReds > 0) {
        "Y $priorYellows  R $priorReds"
    } else {
        "Y $priorYellows"
    }
}

private fun GameSetupState.startTimeSummary(): String {
    return "${formatStartDate(startDate)} ${formatClockTime(startTime)}"
}

private fun GameSetupState.startingPullSummary(): String {
    return "${pullingTeam.setupName(this)} pulls from ${pullingFromEnd.displayText()}"
}

private fun TeamId.setupName(state: GameSetupState): String {
    val name = if (this == TeamId.TEAM_ONE) state.teamOne.name else state.teamTwo.name
    return name.ifBlank {
        if (this == TeamId.TEAM_ONE) "Team 1" else "Team 2"
    }
}

private fun FieldEnd.displayText(): String {
    return when (this) {
        FieldEnd.FAR -> "Far end"
        FieldEnd.NEAR -> "Near end"
    }
}

private fun GameRules.capRulesSummary(): String {
    return "${capSummary(useHalfCap, halfCapMinutes)}/" +
        "${capSummary(useSoftCap, softCapMinutes)}/" +
        capSummary(useHardCap, hardCapMinutes)
}

private fun capSummary(enabled: Boolean, minutes: Int): String {
    return if (enabled) minutes.toString() else "-"
}

private fun GameRules.timeoutSummary(): String {
    return if (hasFloaterTimeout) "$timeoutsPerHalf+1" else timeoutsPerHalf.toString()
}

private fun GameSetupState.priorCardsSummary(): String {
    return when (priorCards.size) {
        0 -> "No previous cards."
        1 -> "1 player carries cards."
        else -> "${priorCards.size} players carry cards."
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
private fun GameRules.formatTimeoutRules(): String {
    return buildString {
        append("$timeoutsPerHalf/half")
        if (hasFloaterTimeout) {
            append(" + floater")
        }
    }
}
