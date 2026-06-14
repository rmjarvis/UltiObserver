package rmjarvis.ultiobserver

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.rememberColorPickerController

/**
 * Numeric game-rule editor dialog target.
 *
 * @param dialogTitle The title for the editor dialog.
 * @param fieldLabel The label for the numeric field in the dialog.
 */
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

/// Setup dialog currently open.
private enum class SetupDialog {
    GAME_INFORMATION,
    STARTING_PULL,
    GAME_RULES,
}

/// Team-specific setup dialog kind currently open.
private enum class TeamSetupDialog {
    COLOR,
    CUSTOM_COLOR,
    NAMES,
    PRIOR_CARDS,
    ADD_PRIOR_CARD,
    EDIT_PRIOR_CARD,
}

/**
 * Team-specific setup dialog currently open.
 *
 * @param teamId The team whose setup button opened the dialog.
 * @param dialog The team-specific setup dialog kind.
 * @param priorCardIndex Original setup prior-card index, used only by edit-player dialogs.
 * @param priorCardDraft Optional entered values to preload when reopening a prior-card edit dialog.
 */
private data class TeamDialog(
    val teamId: TeamId,
    val dialog: TeamSetupDialog,
    val priorCardIndex: Int? = null,
    val priorCardDraft: PlayerCardRecord? = null,
)

/**
 * Notice shown before reopening an already-listed card holder for editing.
 *
 * @param teamId The team whose existing card holder will be edited.
 * @param existingIndex Existing prior-card record index.
 * @param draftForEdit Entered values to preload in the edit dialog.
 * @param existingCardDetail Existing card counts shown in the notice.
 */
private data class ExistingPriorCardNotice(
    val teamId: TeamId,
    val existingIndex: Int,
    val draftForEdit: PlayerCardRecord,
    val existingCardDetail: String,
)

/**
 * Confirmation state for rare duplicate jersey numbers with different names.
 *
 * @param record Prior-card record to save if the observer confirms.
 * @param editingIndex Existing record index being edited, or null when adding.
 * @param existingIdentity Existing same-number card holder shown in the confirmation.
 * @param proposedIdentity Newly entered same-number card holder shown in the confirmation.
 */
private data class SameNumberPriorCardConfirmation(
    val record: PlayerCardRecord,
    val editingIndex: Int?,
    val existingIdentity: String,
    val proposedIdentity: String,
)

/**
 * Notice shown after two prior-card holder entries were merged into one.
 *
 * @param mergedIdentity Identity kept after the merge.
 * @param removedIdentity Identity removed by the merge.
 * @param priorCardsBeforeMerge Prior-card records to restore if the observer cancels the merge.
 * @param editingIndex Original edited record index to reopen if the observer cancels the merge.
 * @param editDraft Merging values to show again if the observer cancels the merge.
 */
private data class PriorCardMergeNotice(
    val mergedIdentity: String,
    val removedIdentity: String,
    val priorCardsBeforeMerge: List<PlayerCardRecord>,
    val editingIndex: Int,
    val editDraft: PlayerCardRecord,
)

/**
 * Render the pregame/edit-game setup form for start time, teams, pull, rules, and prior cards.
 *
 * @param state The setup state currently being edited.
 * @param onStateChange Callback receiving setup changes from fields and dialogs.
 * @param primaryButtonLabel Label for the fixed bottom action.
 * @param onPrimaryAction Callback starting the game or returning to the live screen.
 * @param onBackHome Callback returning to Home.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SetupScreen(
    state: GameSetupState,
    onStateChange: (GameSetupState) -> Unit,
    primaryButtonLabel: String,
    onPrimaryAction: () -> Unit,
    onBackHome: () -> Unit,
) {
    var editingRule by remember { mutableStateOf<RuleEditTarget?>(null) }
    var showTimeoutRulesDialog by remember { mutableStateOf(false) }
    var setupDialog by remember { mutableStateOf<SetupDialog?>(null) }
    var teamDialog by remember { mutableStateOf<TeamDialog?>(null) }
    var existingPriorCardNotice by remember { mutableStateOf<ExistingPriorCardNotice?>(null) }
    var sameNumberPriorCardConfirmation by remember { mutableStateOf<SameNumberPriorCardConfirmation?>(null) }
    var priorCardMergeNotice by remember { mutableStateOf<PriorCardMergeNotice?>(null) }
    val scrollState = rememberScrollState()

    fun savePriorCardRecord(record: PlayerCardRecord, editingIndex: Int?, duplicateIndex: Int? = null) {
        onStateChange(
            state.copy(
                priorCards = state.priorCards.withSavedPriorCardRecord(
                    record = record,
                    editingIndex = editingIndex,
                    duplicateIndex = duplicateIndex,
                ),
            )
        )
        teamDialog = TeamDialog(record.team, TeamSetupDialog.PRIOR_CARDS)
    }

    fun handlePriorCardSave(record: PlayerCardRecord, editingIndex: Int?) {
        // No else branch: null plus every CardHolderEntryCheck subtype is handled.
        when (val entryCheck = state.priorCards.cardHolderEntryCheck(record, editingIndex)) {
            null -> savePriorCardRecord(record, editingIndex)
            is CardHolderEntryCheck.ExistingCardHolder -> {
                val existingRecord = state.priorCards[entryCheck.existingIndex]
                if (editingIndex == null) {
                    teamDialog = null
                    existingPriorCardNotice = ExistingPriorCardNotice(
                        teamId = record.team,
                        existingIndex = entryCheck.existingIndex,
                        draftForEdit = entryCheck.draftForEdit,
                        existingCardDetail = existingRecord.playerCardNoticeDetail(),
                    )
                } else {
                    savePriorCardRecord(
                        record = entryCheck.draftForEdit,
                        editingIndex = editingIndex,
                        duplicateIndex = entryCheck.existingIndex,
                    )
                    priorCardMergeNotice = PriorCardMergeNotice(
                        mergedIdentity = entryCheck.draftForEdit.playerCardIdentity(compact = false),
                        removedIdentity = existingRecord.playerCardIdentity(compact = false),
                        priorCardsBeforeMerge = state.priorCards,
                        editingIndex = editingIndex,
                        editDraft = record,
                    )
                }
            }
            is CardHolderEntryCheck.SameNumberDifferentName -> {
                val existingRecord = state.priorCards[entryCheck.existingIndex]
                teamDialog = null
                sameNumberPriorCardConfirmation = SameNumberPriorCardConfirmation(
                    record = record,
                    editingIndex = editingIndex,
                    existingIdentity = existingRecord.playerCardIdentity(compact = false),
                    proposedIdentity = record.playerCardIdentity(compact = false),
                )
            }
        }
    }

    fun returnToSameNumberPriorCardEntry(confirmation: SameNumberPriorCardConfirmation) {
        sameNumberPriorCardConfirmation = null
        teamDialog = TeamDialog(
            teamId = confirmation.record.team,
            dialog = if (confirmation.editingIndex == null) {
                TeamSetupDialog.ADD_PRIOR_CARD
            } else {
                TeamSetupDialog.EDIT_PRIOR_CARD
            },
            priorCardIndex = confirmation.editingIndex,
            priorCardDraft = confirmation.record,
        )
    }

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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp),
            ) {
                Button(
                    onClick = onPrimaryAction,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(primaryButtonLabel)
                }
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
            // Team identity.
            SetupFieldBox {
                TeamEditor(
                    fieldLabel = "Team 1",
                    team = state.teamOne,
                    priorCards = state.priorCards.filter { it.team == TeamId.TEAM_ONE },
                    onTeamChange = { onStateChange(state.copy(teamOne = it)) },
                    onEditColor = {
                        teamDialog = TeamDialog(TeamId.TEAM_ONE, TeamSetupDialog.COLOR)
                    },
                    onEditNames = {
                        teamDialog = TeamDialog(TeamId.TEAM_ONE, TeamSetupDialog.NAMES)
                    },
                    onEditCards = {
                        teamDialog = TeamDialog(TeamId.TEAM_ONE, TeamSetupDialog.PRIOR_CARDS)
                    },
                )
                TeamSetupDivider()
                TeamEditor(
                    fieldLabel = "Team 2",
                    team = state.teamTwo,
                    priorCards = state.priorCards.filter { it.team == TeamId.TEAM_TWO },
                    onTeamChange = { onStateChange(state.copy(teamTwo = it)) },
                    onEditColor = {
                        teamDialog = TeamDialog(TeamId.TEAM_TWO, TeamSetupDialog.COLOR)
                    },
                    onEditNames = {
                        teamDialog = TeamDialog(TeamId.TEAM_TWO, TeamSetupDialog.NAMES)
                    },
                    onEditCards = {
                        teamDialog = TeamDialog(TeamId.TEAM_TWO, TeamSetupDialog.PRIOR_CARDS)
                    },
                )
            }

            SetupSummaryRow(
                title = "Game Information",
                editTag = "setup-edit-game-information",
                onEdit = { setupDialog = SetupDialog.GAME_INFORMATION },
            ) {
                GameInformationSummary(state)
            }

            SetupSummaryRow(
                title = "Starting Pull",
                summary = state.startingPullSummary(),
                editTag = "setup-edit-starting-pull",
                onEdit = { setupDialog = SetupDialog.STARTING_PULL },
            )
            SetupSummaryRow(
                title = "Game Rules",
                editTag = "setup-edit-game-rules",
                onEdit = { setupDialog = SetupDialog.GAME_RULES },
            ) {
                GameRulesSummary(state.rules)
            }
        }
    }

    // If setupDialog is set, then on this re-render, open the corresponding dialog box.
    // No else branch: every SetupDialog value plus null is handled
    when (setupDialog) {
        SetupDialog.GAME_INFORMATION -> {
            GameInformationSetupDialog(
                state = state,
                onStateChange = onStateChange,
                onDismiss = { setupDialog = null },
            )
        }

        SetupDialog.STARTING_PULL -> {
            StartingPullSetupDialog(
                state = state,
                onStateChange = onStateChange,
                onDismiss = { setupDialog = null },
            )
        }

        SetupDialog.GAME_RULES -> {
            GameRulesSetupDialog(
                rules = state.rules,
                onEditRule = { editingRule = it },
                onEditTimeouts = { showTimeoutRulesDialog = true },
                onUseUsauDefaults = {
                    onStateChange(state.copy(rules = GameRules()))
                },
                onDismiss = { setupDialog = null },
            )
        }

        null -> Unit
    }

    // If teamDialog is set, then on this re-render, open the corresponding dialog box
    // for the team set in the teamId field for the dialog.
    val target = teamDialog
    if (target != null) {
        val targetLabel = target.teamId.setupName(state)
        val targetTeam = target.teamId.setupTeam(state)
        val onTargetTeamChange: (TeamSetup) -> Unit = { updatedTeam ->
            onStateChange(state.withSetupTeam(target.teamId, updatedTeam))
        }
        // No else branch: every TeamSetupDialog value is handled
        when (target.dialog) {
            TeamSetupDialog.COLOR -> {
                TeamColorSetupDialog(
                    teamLabel = targetLabel,
                    teamFieldLabel = target.teamId.setupFieldLabel(),
                    team = targetTeam,
                    onPresetColorSelected = { color ->
                        onTargetTeamChange(targetTeam.copy(color = color))
                        teamDialog = null
                    },
                    onCustomColorSelected = { colorArgb ->
                        onTargetTeamChange(
                            targetTeam.copy(
                                color = TeamColorChoice.CUSTOM,
                                customColorArgb = colorArgb,
                            ),
                        )
                        teamDialog = null
                    },
                    onMoreColors = {
                        teamDialog = TeamDialog(target.teamId, TeamSetupDialog.CUSTOM_COLOR)
                    },
                    onDismiss = { teamDialog = null },
                )
            }

            TeamSetupDialog.CUSTOM_COLOR -> {
                CustomTeamColorSetupDialog(
                    teamLabel = targetLabel,
                    teamFieldLabel = target.teamId.setupFieldLabel(),
                    team = targetTeam,
                    onCustomColorSelected = { colorArgb ->
                        onTargetTeamChange(
                            targetTeam.copy(
                                color = TeamColorChoice.CUSTOM,
                                customColorArgb = colorArgb,
                            ),
                        )
                        teamDialog = null
                    },
                    onDismiss = { teamDialog = null },
                )
            }

            TeamSetupDialog.NAMES -> {
                TeamNamesSetupDialog(
                    teamLabel = targetLabel,
                    teamFieldLabel = target.teamId.setupFieldLabel(),
                    team = targetTeam,
                    onTeamChange = onTargetTeamChange,
                    onDismiss = { teamDialog = null },
                )
            }

            TeamSetupDialog.PRIOR_CARDS -> {
                PriorCardsSetupDialog(
                    state = state,
                    teamId = target.teamId,
                    teamName = targetLabel,
                    onAddPlayer = {
                        teamDialog = TeamDialog(target.teamId, TeamSetupDialog.ADD_PRIOR_CARD)
                    },
                    onEditPlayer = { index ->
                        teamDialog = TeamDialog(target.teamId, TeamSetupDialog.EDIT_PRIOR_CARD, index)
                    },
                    onRemovePlayer = { index ->
                        onStateChange(state.copy(priorCards = state.priorCards.filterIndexed { i, _ -> i != index }))
                    },
                    onDismiss = { teamDialog = null },
                )
            }

            TeamSetupDialog.ADD_PRIOR_CARD -> {
                PriorCardPlayerDialog(
                    teamId = target.teamId,
                    teamName = targetLabel,
                    initialRecord = target.priorCardDraft,
                    isEditing = false,
                    onDismiss = {
                        teamDialog = TeamDialog(target.teamId, TeamSetupDialog.PRIOR_CARDS)
                    },
                    onConfirm = { record ->
                        handlePriorCardSave(record, editingIndex = null)
                    },
                )
            }

            TeamSetupDialog.EDIT_PRIOR_CARD -> {
                val recordIndex = requireNotNull(target.priorCardIndex) {
                    "Edit prior-card dialog requires a prior-card index."
                }
                PriorCardPlayerDialog(
                    teamId = target.teamId,
                    teamName = targetLabel,
                    initialRecord = target.priorCardDraft ?: state.priorCards[recordIndex],
                    isEditing = true,
                    onDismiss = {
                        teamDialog = TeamDialog(target.teamId, TeamSetupDialog.PRIOR_CARDS)
                    },
                    onConfirm = { updatedRecord ->
                        handlePriorCardSave(updatedRecord, editingIndex = recordIndex)
                    },
                    onRemove = {
                        onStateChange(state.copy(priorCards = state.priorCards.filterIndexed { i, _ -> i != recordIndex }))
                        teamDialog = TeamDialog(target.teamId, TeamSetupDialog.PRIOR_CARDS)
                    },
                )
            }
        }
    }

    existingPriorCardNotice?.let { notice ->
        AlertDialog(
            onDismissRequest = { existingPriorCardNotice = null },
            title = { Text("Card Holder Already Listed") },
            text = {
                Text(
                    "This player is already listed as a card holder with " +
                        "${notice.existingCardDetail}. Opening that entry for editing."
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        existingPriorCardNotice = null
                        teamDialog = TeamDialog(
                            teamId = notice.teamId,
                            dialog = TeamSetupDialog.EDIT_PRIOR_CARD,
                            priorCardIndex = notice.existingIndex,
                            priorCardDraft = notice.draftForEdit,
                        )
                    }
                ) {
                    Text("OK")
                }
            },
        )
    }

    sameNumberPriorCardConfirmation?.let { confirmation ->
        AlertDialog(
            onDismissRequest = {
                returnToSameNumberPriorCardEntry(confirmation)
            },
            title = { Text("Same Number, Different Names") },
            text = {
                Text(
                    "${confirmation.existingIdentity} is already listed. Add " +
                        "${confirmation.proposedIdentity} as a different player with the same number?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        sameNumberPriorCardConfirmation = null
                        savePriorCardRecord(confirmation.record, confirmation.editingIndex)
                    }
                ) {
                    Text(if (confirmation.editingIndex == null) "Add" else "Update")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        returnToSameNumberPriorCardEntry(confirmation)
                    }
                ) {
                    Text("Cancel")
                }
            },
        )
    }

    priorCardMergeNotice?.let { notice ->
        AlertDialog(
            onDismissRequest = { priorCardMergeNotice = null },
            title = { Text("Card Holder Entries Merged") },
            text = {
                Text(
                    "${notice.removedIdentity} matched ${notice.mergedIdentity}, " +
                        "so the duplicate entry was removed."
                )
            },
            confirmButton = {
                TextButton(onClick = { priorCardMergeNotice = null }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        priorCardMergeNotice = null
                        onStateChange(state.copy(priorCards = notice.priorCardsBeforeMerge))
                        teamDialog = TeamDialog(
                            teamId = notice.editDraft.team,
                            dialog = TeamSetupDialog.EDIT_PRIOR_CARD,
                            priorCardIndex = notice.editingIndex,
                            priorCardDraft = notice.editDraft,
                        )
                    }
                ) {
                    Text("Cancel")
                }
            },
        )
    }

    // If editingRule is set, then on this re-render, open the dialog for specifying rules.
    if (editingRule != null) {
        val target = editingRule!!
        // No else branch: every RuleEditTarget value is handled
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

    // If showTimeoutRulesDialog is true, then on this re-render, open the dialog for
    // setting the number of timeouts per half.
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

/**
 * Render a setup overview box.
 *
 * @param content The composable body rendered inside the box.
 */
@Composable
private fun SetupFieldBox(
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
            content()
        }
    }
}

/// Render the separator between the two team setup blocks.
@Composable
private fun TeamSetupDivider() {
    HorizontalDivider(
        modifier = Modifier.padding(vertical = 2.dp),
        thickness = 2.dp,
        color = MaterialTheme.colorScheme.outlineVariant,
    )
}

/**
 * Render a compact setup summary row with plain text summary content.
 *
 * @param title The row label.
 * @param summary The prominent summary text for the current setting.
 * @param editTag The test tag attached to the row's Edit button.
 * @param onEdit Callback invoked when the Edit button is tapped.
 */
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

/**
 * Render a compact setup summary row with caller-provided summary content.
 *
 * @param title The row label.
 * @param editTag The test tag attached to the row's Edit button.
 * @param onEdit Callback invoked when the Edit button is tapped.
 * @param summaryContent The composable summary body for rows that need more than plain text.
 */
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

/**
 * Render the game-information setup dialog.
 *
 * @param state The setup state whose tournament and game context are being edited.
 * @param onStateChange Callback receiving updated setup state.
 * @param onDismiss Callback closing the dialog.
 */
@Composable
private fun GameInformationSetupDialog(
    state: GameSetupState,
    onStateChange: (GameSetupState) -> Unit,
    onDismiss: () -> Unit,
) {
    var startDate by remember { mutableStateOf(state.startDate) }
    var startTime by remember { mutableStateOf(state.startTime) }
    var tournamentName by remember { mutableStateOf(state.tournamentName) }
    var division by remember { mutableStateOf(state.division) }
    var gameContext by remember { mutableStateOf(state.gameContext) }
    var showStartDateDialog by remember { mutableStateOf(false) }
    var showStartTimeDialog by remember { mutableStateOf(false) }

    fun saveAndDismiss() {
        onStateChange(
            state.copy(
                startDate = startDate,
                startTime = startTime,
                tournamentName = tournamentName,
                division = division,
                gameContext = gameContext,
            )
        )
        onDismiss()
    }

    AlertDialog(
        onDismissRequest = ::saveAndDismiss,
        title = { Text("Game Information") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "Date",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DateTimeDisplayField(
                    value = formatStartDate(startDate),
                    testTag = "setup-start-date-field",
                    onClick = { showStartDateDialog = true },
                )

                Text(
                    text = "Start time",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                DateTimeDisplayField(
                    value = formatClockTime(startTime),
                    testTag = "setup-start-time-field",
                    onClick = { showStartTimeDialog = true },
                )

                Text("Division", fontWeight = FontWeight.SemiBold)
                GameDivisionChoiceRow(
                    selected = division,
                    onSelected = { division = it },
                )

                OutlinedTextField(
                    value = tournamentName,
                    onValueChange = { tournamentName = it },
                    label = { Text("Tournament name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("setup-tournament-name"),
                )
                OutlinedTextField(
                    value = gameContext,
                    onValueChange = { gameContext = it },
                    label = { Text("Game context") },
                    placeholder = { Text("Pool play, Semi-finals, etc.") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("setup-game-context"),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = ::saveAndDismiss) {
                Text("Done")
            }
        },
    )

    if (showStartDateDialog) {
        StartDateDialog(
            initialDate = startDate,
            onDismiss = { showStartDateDialog = false },
            onConfirm = {
                startDate = it
                showStartDateDialog = false
            },
        )
    }

    if (showStartTimeDialog) {
        ExactTimeDialog(
            initialTime = startTime,
            onDismiss = { showStartTimeDialog = false },
            onConfirm = {
                startTime = it
                showStartTimeDialog = false
            },
        )
    }
}

/**
 * Render the division chooser for the game-information setup dialog.
 *
 * @param selected The currently selected division value, or null when unset.
 * @param onSelected Callback receiving the selected division, or null to clear it.
 */
@Composable
private fun GameDivisionChoiceRow(
    selected: GameDivision?,
    onSelected: (GameDivision?) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        orderedSetupDivisions().forEach { division ->
            CompactDivisionChip(
                label = division?.displayText ?: "N/A",
                selected = selected == division,
                onClick = { onSelected(division) },
                modifier = Modifier.testTag("setup-game-division-${division?.name ?: "NA"}"),
            )
        }
    }
}

/**
 * Render one compact division selector.
 *
 * @param label The user-facing division text.
 * @param selected Whether this division is currently selected.
 * @param onClick Callback selecting this division.
 * @param modifier Modifier applied by the caller, usually for test tags.
 */
@Composable
private fun CompactDivisionChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(50)
    val backgroundColor = if (selected) {
        MaterialTheme.colorScheme.secondaryContainer
    } else {
        MaterialTheme.colorScheme.surface
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        MaterialTheme.colorScheme.onSurface
    }
    val borderColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.outline
    }
    Box(
        modifier = modifier
            .border(width = 1.dp, color = borderColor, shape = shape)
            .background(color = backgroundColor, shape = shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp, vertical = 7.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = contentColor,
            style = MaterialTheme.typography.labelLarge,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Render the compact game-information summary used on the setup overview.
 *
 * @param state The current setup state to summarize.
 */
@Composable
private fun GameInformationSummary(state: GameSetupState) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        state.gameInformationSummaryLines().forEach { line ->
            Text(
                text = line,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/**
 * Render the compact game-rules summary used on the setup overview.
 *
 * @param rules The current rules to summarize.
 */
@Composable
private fun GameRulesSummary(rules: GameRules) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(28.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        SetupSummaryValue("Game to ${rules.gameTo}")
        SetupSummaryValue("Half: ${rules.halftimeMinutes} min")
        SetupSummaryValue("Caps ${rules.capRulesSummary()}")
        SetupSummaryValue("TO ${rules.timeoutSummary()}")
    }
}

/**
 * Render one prominent value inside a setup summary.
 *
 * @param text The summary value to display.
 */
@Composable
private fun SetupSummaryValue(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * Render the opening pull editor dialog.
 *
 * @param state The setup state whose pull team and pull end are being edited.
 * @param onStateChange Callback receiving updated setup state.
 * @param onDismiss Callback closing the dialog.
 */
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

/**
 * Render the game-rules editor dialog.
 *
 * @param rules The current rules to display.
 * @param onEditRule Callback opening a focused editor for one simple rule.
 * @param onEditTimeouts Callback opening the timeout-rules editor.
 * @param onUseUsauDefaults Callback resetting the rule bundle to USAU defaults.
 * @param onDismiss Callback closing the dialog.
 */
@Composable
private fun GameRulesSetupDialog(
    rules: GameRules,
    onEditRule: (RuleEditTarget) -> Unit,
    onEditTimeouts: () -> Unit,
    onUseUsauDefaults: () -> Unit,
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
                OutlinedButton(
                    onClick = onUseUsauDefaults,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("setup-usau-defaults"),
                ) {
                    Text("Reset to USAU Defaults")
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

/**
 * Render the prior-card setup dialog.
 *
 * @param state The setup state whose prior-card records are displayed.
 * @param teamId The team whose prior-card records are edited.
 * @param teamName The team label shown in the dialog title.
 * @param onAddPlayer Callback opening the add-prior-card dialog.
 * @param onEditPlayer Callback opening an existing prior-card record by original setup index.
 * @param onRemovePlayer Callback removing an existing prior-card record by original setup index.
 * @param onDismiss Callback closing the dialog.
 */
@Composable
private fun PriorCardsSetupDialog(
    state: GameSetupState,
    teamId: TeamId,
    teamName: String,
    onAddPlayer: () -> Unit,
    onEditPlayer: (Int) -> Unit,
    onRemovePlayer: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val teamPriorCards = state.priorCards.withIndex().filter { it.value.team == teamId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$teamName Cards from Previous Games") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Column(
                    modifier = Modifier
                        .heightIn(max = 320.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (teamPriorCards.isEmpty()) {
                        Text("No prior cards recorded yet.")
                    } else {
                        teamPriorCards.forEach { (index, record) ->
                            PlayerRecordRow(
                                label = record.playerCardIdentity(compact = false),
                                detail = record.playerCardDetail(),
                                editTag = "setup-prior-card-edit-$index",
                                removeTag = "setup-prior-card-remove-$index",
                                onEdit = { onEditPlayer(index) },
                                onRemove = { onRemovePlayer(index) },
                            )
                        }
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

/**
 * Render the Material date picker for setup start date.
 *
 * @param initialDate The date initially selected in the picker.
 * @param onDismiss Callback closing the picker without changing state.
 * @param onConfirm Callback receiving the selected local date.
 */
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
            title = null,
        )
    }
}

/**
 * Render the Material time input dialog for setup start time.
 *
 * @param initialTime The time initially selected in the picker.
 * @param onDismiss Callback closing the picker without changing state.
 * @param onConfirm Callback receiving the selected local time.
 */
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
        title = {},
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

/**
 * Render a reusable integer-entry dialog for simple numeric rule values.
 *
 * @param title The dialog title.
 * @param fieldLabel The text-field label.
 * @param initialValue The current numeric value shown when the dialog opens.
 * @param onDismiss Callback closing the dialog without applying a value.
 * @param onConfirm Callback receiving the parsed non-negative value.
 */
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

/**
 * Render an integer-entry dialog for a cap rule with an explicit None toggle.
 *
 * @param title The dialog title and test-tag stem.
 * @param fieldLabel The text-field label.
 * @param prefixText The explanatory text shown above the numeric field.
 * @param suffixText The explanatory text shown below the numeric field.
 * @param initialValue The current cap offset in minutes.
 * @param initiallyEnabled Whether the cap is currently enabled.
 * @param onDismiss Callback closing the dialog without applying changes.
 * @param onConfirm Callback receiving the enabled flag and parsed non-negative offset.
 */
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

/**
 * Render the timeout rules editor.
 *
 * @param rules The current rules whose timeout fields are being edited.
 * @param onDismiss Callback closing the dialog without applying changes.
 * @param onConfirm Callback receiving rules updated with the timeout values.
 */
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

/**
 * Render the setup dialog for adding or editing a player carrying cards from earlier games.
 *
 * @param teamId The team whose player is carrying prior cards.
 * @param teamName The team label shown in the dialog.
 * @param initialRecord Existing or entered values to show initially, or null for a blank add dialog.
 * @param isEditing Whether confirmation updates an existing record rather than adding a new one.
 * @param onDismiss Callback closing the dialog without applying changes.
 * @param onConfirm Callback receiving the added or updated prior-card record.
 * @param onRemove Callback removing the edited prior-card record, or null when adding.
 */
@Composable
private fun PriorCardPlayerDialog(
    teamId: TeamId,
    teamName: String,
    initialRecord: PlayerCardRecord?,
    isEditing: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (PlayerCardRecord) -> Unit,
    onRemove: (() -> Unit)? = null,
) {
    var jerseyNumber by remember(initialRecord) { mutableStateOf(initialRecord?.jerseyNumber ?: "") }
    var playerName by remember(initialRecord) { mutableStateOf(initialRecord?.playerName ?: "") }
    var priorYellows by remember(initialRecord) { mutableStateOf(initialRecord?.priorYellows ?: 1) }
    var priorReds by remember(initialRecord) { mutableStateOf(initialRecord?.priorReds ?: 0) }
    val trimmedJerseyNumber = jerseyNumber.trim()
    val trimmedPlayerName = playerName.trim()
    val hasPlayerIdentity = trimmedJerseyNumber.isNotEmpty() || trimmedPlayerName.isNotEmpty()
    val hasPriorCard = priorYellows > 0 || priorReds > 0
    val confirmLabel = when {
        !hasPriorCard && isEditing -> "Remove"
        !hasPriorCard -> "Cancel"
        isEditing -> "Update"
        else -> "Add"
    }
    val focusManager = LocalFocusManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "Edit Previous Game Card Holder" else "Add Previous Game Card Holder") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = teamName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                OutlinedTextField(
                    value = jerseyNumber,
                    onValueChange = { jerseyNumber = it.filter(Char::isDigit) },
                    label = { Text("Number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(force = true) }),
                    singleLine = true,
                    modifier = Modifier.testTag("setup-prior-card-jersey"),
                )
                OutlinedTextField(
                    value = playerName,
                    onValueChange = { playerName = it },
                    label = { Text("Name") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(force = true) }),
                    singleLine = true,
                    modifier = Modifier.testTag("setup-prior-card-name"),
                )
                SmallCountEditor(
                    label = "Yellow",
                    value = priorYellows,
                    onValueChange = { priorYellows = it.coerceAtLeast(0) },
                )
                SmallCountEditor(
                    label = "Red",
                    value = priorReds,
                    onValueChange = { priorReds = it.coerceAtLeast(0) },
                )
            }
        },
        confirmButton = {
            TextButton(
                enabled = !hasPriorCard || hasPlayerIdentity,
                onClick = {
                    if (!hasPriorCard) {
                        onRemove?.invoke() ?: onDismiss()
                    } else {
                        onConfirm(
                            PlayerCardRecord(
                                team = teamId,
                                jerseyNumber = trimmedJerseyNumber,
                                priorYellows = priorYellows,
                                priorReds = priorReds,
                                playerName = trimmedPlayerName,
                            )
                        )
                    }
                }
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            if (confirmLabel != "Cancel") {
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        },
    )
}

/**
 * Render the name-and-color editor for one setup team.
 *
 * @param fieldLabel The team field label and test-tag stem.
 * @param team The current team setup values.
 * @param priorCards Prior-card records entered for this team.
 * @param onTeamChange Callback receiving the updated team setup.
 * @param onEditColor Callback opening the color editor.
 * @param onEditNames Callback opening the coach/captain names editor.
 * @param onEditCards Callback opening the prior-card editor.
 */
@Composable
private fun TeamEditor(
    fieldLabel: String,
    team: TeamSetup,
    priorCards: List<PlayerCardRecord>,
    onTeamChange: (TeamSetup) -> Unit,
    onEditColor: () -> Unit,
    onEditNames: () -> Unit,
    onEditCards: () -> Unit,
) {
    val namesSummary = team.namesSummary()
    val cardsSummary = priorCards.teamPriorCardsSummary()

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            OutlinedTextField(
                value = team.name,
                onValueChange = {
                    onTeamChange(team.copy(name = it))
                },
                placeholder = {
                    Text(
                        text = fieldLabel,
                        color = team.content.copy(alpha = 0.65f),
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
                colors = teamNameFieldColors(team),
                modifier = Modifier
                    .weight(1f)
                    .testTag("setup-$fieldLabel-name"),
            )
            OutlinedButton(
                onClick = onEditColor,
                modifier = Modifier.testTag("setup-$fieldLabel-color-button"),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = MaterialTheme.colorScheme.primary,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                ),
                contentPadding = compactSetupButtonPadding(),
            ) {
                Text(
                    text = "Edit\nColor",
                    style = MaterialTheme.typography.labelMedium,
                    textAlign = TextAlign.Center,
                    lineHeight = MaterialTheme.typography.labelMedium.fontSize,
                )
            }
        }
        TeamSetupDetailColumns(
            fieldLabel = fieldLabel,
            namesSummary = namesSummary,
            cardsSummary = cardsSummary,
            onEditNames = onEditNames,
            onEditCards = onEditCards,
        )
    }
}

/// Return colored text-field colors that preview how the team name appears on the field screen.
@Composable
private fun teamNameFieldColors(team: TeamSetup) = OutlinedTextFieldDefaults.colors(
    focusedContainerColor = team.accent,
    unfocusedContainerColor = team.accent,
    focusedTextColor = team.content,
    unfocusedTextColor = team.content,
    focusedLabelColor = team.content.copy(alpha = 0.85f),
    unfocusedLabelColor = team.content.copy(alpha = 0.85f),
    cursorColor = team.content,
    focusedBorderColor = team.content.copy(alpha = 0.85f),
    unfocusedBorderColor = team.content.copy(alpha = 0.5f),
)

/// Return compact padding for setup team action buttons.
private fun compactSetupButtonPadding(): PaddingValues {
    return PaddingValues(horizontal = 12.dp, vertical = 6.dp)
}

/**
 * Render compact team setup detail actions and summaries.
 *
 * @param fieldLabel The team field label and test-tag stem.
 * @param namesSummary Labeled coach/captain summary rows for the left side.
 * @param cardsSummary Prior-card text summary for the right side.
 * @param onEditNames Callback opening the coach/captain names editor.
 * @param onEditCards Callback opening the prior-card editor.
 */
@Composable
private fun TeamSetupDetailColumns(
    fieldLabel: String,
    namesSummary: List<LabeledSetupSummary>,
    cardsSummary: String,
    onEditNames: () -> Unit,
    onEditCards: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Column(
            modifier = Modifier.weight(2f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            OutlinedButton(
                onClick = onEditNames,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("setup-$fieldLabel-names-button"),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.White,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                contentPadding = compactSetupButtonPadding(),
            ) {
                Text("Coach/Captains")
            }
            TeamNamesInlineSummary(namesSummary = namesSummary)
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            OutlinedButton(
                onClick = onEditCards,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("setup-$fieldLabel-cards-button"),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color(0xFFFFD600),
                    contentColor = Color.Black,
                ),
                contentPadding = compactSetupButtonPadding(),
            ) {
                Text("Cards")
            }
            Text(
                text = cardsSummary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

/**
 * Render compact team-name details with labels in a fixed-width column.
 *
 * @param namesSummary Labeled coach/captain summary rows.
 * @param modifier Modifier applied to the summary column.
 */
@Composable
private fun TeamNamesInlineSummary(
    namesSummary: List<LabeledSetupSummary>,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(1.dp),
    ) {
        namesSummary.forEach { summary ->
            Row(horizontalArrangement = Arrangement.spacedBy(3.dp)) {
                Text(
                    text = summary.label,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.width(40.dp),
                )
                Text(
                    text = summary.value,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.weight(1f),
                )
            }
        }
    }
}

/**
 * Render the team-color setup dialog.
 *
 * @param teamLabel The display name for the team being edited.
 * @param teamFieldLabel The stable setup field label used for test tags.
 * @param team The team setup values being edited.
 * @param onPresetColorSelected Callback receiving the selected preset team color.
 * @param onCustomColorSelected Callback receiving the selected custom team color as opaque ARGB.
 * @param onMoreColors Callback opening the full custom color picker.
 * @param onDismiss Callback closing the dialog.
 */
@Composable
private fun TeamColorSetupDialog(
    teamLabel: String,
    teamFieldLabel: String,
    team: TeamSetup,
    onPresetColorSelected: (TeamColorChoice) -> Unit,
    onCustomColorSelected: (Long) -> Unit,
    onMoreColors: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$teamLabel Color") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ColorChoiceRow(
                    selected = team.color.takeUnless { it == TeamColorChoice.CUSTOM },
                    testTagPrefix = "setup-$teamFieldLabel-color",
                    onSelected = {
                        onPresetColorSelected(it)
                    },
                )
                if (team.customColorArgb != null) {
                    CustomColorChoiceRow(
                        color = Color(team.customColorArgb),
                        selected = team.color == TeamColorChoice.CUSTOM,
                        testTag = "setup-$teamFieldLabel-color-custom",
                        onClick = {
                            onCustomColorSelected(team.customColorArgb)
                        },
                    )
                }
            }
        },
        confirmButton = {
            TeamColorDialogActions(
                confirmText = "More Colors",
                confirmTestTag = "setup-$teamFieldLabel-color-more",
                onCancel = onDismiss,
                onConfirm = onMoreColors,
            )
        },
    )
}

/**
 * Render the full custom team-color picker dialog.
 *
 * @param teamLabel The display name for the team being edited.
 * @param teamFieldLabel The stable setup field label used for test tags.
 * @param team The team setup values being edited.
 * @param onCustomColorSelected Callback receiving the selected custom team color as opaque ARGB.
 * @param onDismiss Callback closing the dialog.
 */
@Composable
private fun CustomTeamColorSetupDialog(
    teamLabel: String,
    teamFieldLabel: String,
    team: TeamSetup,
    onCustomColorSelected: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var customColor by remember(team.customColorArgb, team.color) {
        mutableStateOf(team.customColorArgb?.let(::Color) ?: team.accent)
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$teamLabel Color") },
        text = {
            CustomColorPicker(
                initialColor = customColor,
                testTagPrefix = "setup-$teamFieldLabel-color",
                onColorChange = {
                    customColor = it
                },
            )
        },
        confirmButton = {
            TeamColorDialogActions(
                confirmText = "Use this color",
                confirmTestTag = null,
                onCancel = onDismiss,
                onConfirm = {
                    onCustomColorSelected(customColor.toOpaqueArgbLong())
                },
            )
        },
    )
}

/**
 * Render the color dialog action row with cancel on the left and the next action on the right.
 *
 * @param confirmText Text for the right-side action.
 * @param confirmTestTag Optional test tag for the right-side action.
 * @param onCancel Callback closing the dialog without applying a new color.
 * @param onConfirm Callback running the right-side color action.
 */
@Composable
private fun TeamColorDialogActions(
    confirmText: String,
    confirmTestTag: String?,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(
            onClick = onCancel,
            modifier = Modifier.defaultMinSize(minWidth = 1.dp, minHeight = 1.dp),
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
        ) {
            Text("Cancel")
        }
        TextButton(
            onClick = onConfirm,
            modifier = (confirmTestTag?.let { Modifier.testTag(it) } ?: Modifier)
                .defaultMinSize(minWidth = 1.dp, minHeight = 1.dp),
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
        ) {
            Text(confirmText)
        }
    }
}

/**
 * Render the free-form coach and captain names dialog for one team.
 *
 * @param teamLabel The display name for the team being edited.
 * @param teamFieldLabel The stable setup field label used for test tags.
 * @param team The team setup values being edited.
 * @param onTeamChange Callback receiving updated team setup values.
 * @param onDismiss Callback closing the dialog.
 */
@Composable
private fun TeamNamesSetupDialog(
    teamLabel: String,
    teamFieldLabel: String,
    team: TeamSetup,
    onTeamChange: (TeamSetup) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$teamLabel Names") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                TeamNamesTextField(
                    value = team.coaches,
                    label = "Coach(es)",
                    testTag = "setup-$teamFieldLabel-coaches",
                    onValueChange = {
                        onTeamChange(team.copy(coaches = it))
                    },
                )
                TeamNamesTextField(
                    value = team.fieldCaptains,
                    label = "Field captain(s)",
                    testTag = "setup-$teamFieldLabel-field-captains",
                    onValueChange = {
                        onTeamChange(team.copy(fieldCaptains = it))
                    },
                )
                TeamNamesTextField(
                    value = team.spiritCaptains,
                    label = "Spirit captain(s)",
                    testTag = "setup-$teamFieldLabel-spirit-captains",
                    onValueChange = {
                        onTeamChange(team.copy(spiritCaptains = it))
                    },
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

/**
 * Render one multi-line free-form team names field.
 *
 * @param value The current free-form text.
 * @param label The field label.
 * @param testTag The test tag attached to the text field.
 * @param onValueChange Callback receiving updated text.
 */
@Composable
private fun TeamNamesTextField(
    value: String,
    label: String,
    testTag: String,
    onValueChange: (String) -> Unit,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        minLines = 2,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Words),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag),
    )
}

/**
 * Render a clickable setup field that looks like a compact form control.
 *
 * @param value The formatted value shown inside the field.
 * @param testTag The test tag attached to the clickable surface.
 * @param onClick Callback opening the focused editor for this value.
 */
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
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Text(
                text = value,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
            )
        }
    }
}

/**
 * Render a button-styled row for setup values that open an editor dialog.
 *
 * @param label The quiet row label.
 * @param value The current value shown on the right.
 * @param onClick Callback opening the editor.
 */
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

/**
 * Render a single-row palette for choosing the team color.
 *
 * @param selected The currently selected color.
 * @param testTagPrefix Prefix used to build test tags for each color swatch.
 * @param onSelected Callback receiving the newly selected color.
 */
@Composable
private fun ColorChoiceRow(
    selected: TeamColorChoice?,
    testTagPrefix: String,
    onSelected: (TeamColorChoice) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        presetTeamColorChoices.forEach { colorChoice ->
            ColorSwatch(
                color = colorChoice.accent,
                selected = selected == colorChoice,
                testTag = "$testTagPrefix-${colorChoice.name}",
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp),
                onClick = {
                    onSelected(colorChoice)
                },
            )
        }
    }
}

private val presetTeamColorChoices: List<TeamColorChoice>
    get() = TeamColorChoice.entries.filter { it != TeamColorChoice.CUSTOM }

/**
 * Render the saved custom color as a second-row swatch.
 *
 * @param color The saved custom jersey color.
 * @param selected Whether the saved custom color is currently selected.
 * @param testTag Test tag attached to the swatch.
 * @param onClick Callback invoked when the swatch is tapped.
 */
@Composable
private fun CustomColorChoiceRow(
    color: Color,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        ColorSwatch(
            color = color,
            selected = selected,
            testTag = testTag,
            modifier = Modifier.size(32.dp),
            onClick = onClick,
        )
    }
}

/**
 * Render one selectable color swatch using the setup palette selection highlight.
 *
 * @param color Color shown inside the swatch.
 * @param selected Whether the swatch is currently selected.
 * @param testTag Test tag attached to the swatch.
 * @param modifier Modifier controlling swatch size and placement.
 * @param onClick Callback invoked when the swatch is tapped.
 */
@Composable
private fun ColorSwatch(
    color: Color,
    selected: Boolean,
    testTag: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .testTag(testTag)
            .background(
                color = if (selected) Color.Black else Color.Transparent,
                shape = RoundedCornerShape(6.dp),
            )
            .padding(if (selected) 1.dp else 0.dp)
            .background(
                color = if (selected) Color(0xFFF2D23C) else Color.Transparent,
                shape = RoundedCornerShape(6.dp),
            )
            .padding(if (selected) 3.dp else 0.dp)
            .background(
                color = if (selected) Color.Black else Color.Transparent,
                shape = RoundedCornerShape(5.dp),
            )
            .padding(if (selected) 1.dp else 0.dp)
            .border(
                width = if (selected) 0.dp else 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(4.dp),
            )
            .background(color, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick),
    )
}

/**
 * Render a simple HSV color picker for custom jersey colors.
 *
 * @param initialColor Color used to initialize the picker and preview.
 * @param testTagPrefix Prefix used to build custom picker test tags.
 * @param onColorChange Callback receiving the currently selected custom color.
 */
@Composable
private fun CustomColorPicker(
    initialColor: Color,
    testTagPrefix: String,
    onColorChange: (Color) -> Unit,
) {
    val controller = rememberColorPickerController()
    var previewColor by remember(initialColor) { mutableStateOf(initialColor) }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HsvColorPicker(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .testTag("$testTagPrefix-custom-picker"),
            controller = controller,
            initialColor = initialColor,
            onColorChanged = { colorEnvelope ->
                previewColor = colorEnvelope.color.copy(alpha = 1f)
                onColorChange(previewColor)
            },
        )
        CustomColorPreview(
            color = previewColor,
            testTag = "$testTagPrefix-custom-preview",
            onClick = {
                onColorChange(previewColor)
            },
        )
    }
}

/**
 * Render the selected custom color as a preview bar.
 *
 * @param color The custom jersey color to display.
 * @param testTag Test tag attached to the preview.
 * @param onClick Callback invoked when the preview is tapped.
 */
@Composable
private fun CustomColorPreview(
    color: Color,
    testTag: String,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(36.dp)
            .testTag(testTag)
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
            .background(color, RoundedCornerShape(6.dp))
            .clickable(onClick = onClick),
    )
}

/// Return an opaque ARGB long for a Compose color.
private fun Color.toOpaqueArgbLong(): Long {
    return copy(alpha = 1f).toArgb().toLong() and 0xFFFFFFFFL
}

/**
 * Render a two-choice row for Team 1 vs Team 2 selection.
 *
 * @param firstLabel Label for Team 1, with fallback display applied locally.
 * @param secondLabel Label for Team 2, with fallback display applied locally.
 * @param selected The currently selected team.
 * @param testTagPrefix Prefix for generated chip test tags.
 * @param onSelected Callback receiving the selected team.
 */
@Composable
private fun TeamChoiceRow(
    firstLabel: String,
    secondLabel: String,
    selected: TeamId,
    testTagPrefix: String,
    onSelected: (TeamId) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = selected == TeamId.TEAM_ONE,
            onClick = {
                onSelected(TeamId.TEAM_ONE)
            },
            modifier = Modifier.testTag("$testTagPrefix-${TeamId.TEAM_ONE.name}"),
            label = { Text(firstLabel.ifBlank { "Team 1" }) },
        )
        FilterChip(
            selected = selected == TeamId.TEAM_TWO,
            onClick = {
                onSelected(TeamId.TEAM_TWO)
            },
            modifier = Modifier.testTag("$testTagPrefix-${TeamId.TEAM_TWO.name}"),
            label = { Text(secondLabel.ifBlank { "Team 2" }) },
        )
    }
}

/**
 * Render a two-choice row for Far end vs Near end selection.
 *
 * @param selected The currently selected field end.
 * @param onSelected Callback receiving the selected field end.
 */
@Composable
private fun FieldEndChoiceRow(
    selected: FieldEnd,
    onSelected: (FieldEnd) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        FilterChip(
            selected = selected == FieldEnd.FAR,
            onClick = {
                onSelected(FieldEnd.FAR)
            },
            label = { Text("Far end") },
        )
        FilterChip(
            selected = selected == FieldEnd.NEAR,
            onClick = {
                onSelected(FieldEnd.NEAR)
            },
            label = { Text("Near end") },
        )
    }
}

/**
 * Render a small +/- editor for integer setup and correction values.
 *
 * @param label The count label.
 * @param value The current count value.
 * @param onValueChange Callback receiving the adjusted count.
 */
@Composable
internal fun SmallCountEditor(
    label: String,
    value: Int,
    onValueChange: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = label,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.width(104.dp),
        )
        SmallActionButton(
            label = "-1",
            onClick = {
                onValueChange((value - 1).coerceAtLeast(0))
            },
        )
        Text(
            value.toString(),
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            textAlign = TextAlign.Center,
            modifier = Modifier.width(24.dp),
        )
        SmallActionButton(
            label = "+1",
            onClick = {
                onValueChange(value + 1)
            },
        )
    }
}

/**
 * Render one row in the setup list of players carrying prior cards.
 *
 * @param label The player/team label.
 * @param detail The compact prior-card detail.
 * @param editTag Test tag for the edit icon button.
 * @param removeTag Test tag for the remove icon button.
 * @param onEdit Callback opening this prior-card record for editing.
 * @param onRemove Callback removing this prior-card record.
 */
@Composable
private fun PlayerRecordRow(
    label: String,
    detail: String,
    editTag: String,
    removeTag: String,
    onEdit: () -> Unit,
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(detail)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                IconButton(
                    onClick = onEdit,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag(editTag),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Edit,
                        contentDescription = "Edit $label",
                        modifier = Modifier.size(20.dp),
                    )
                }
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag(removeTag),
                ) {
                    Icon(
                        imageVector = Icons.Filled.Delete,
                        contentDescription = "Remove $label",
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

/**
 * Convert a local date into the UTC timestamp expected by the Material date picker.
 *
 * @param date The local date to convert.
 */
private fun dateToPickerTimestamp(date: LocalDate): Long {
    return date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}

/**
 * Convert a Material date-picker timestamp back into a local date.
 *
 * @param timestamp The UTC midnight timestamp supplied by the picker.
 */
private fun pickerTimestampToDate(timestamp: Long): LocalDate {
    return Instant.ofEpochMilli(timestamp).atZone(ZoneOffset.UTC).toLocalDate()
}
