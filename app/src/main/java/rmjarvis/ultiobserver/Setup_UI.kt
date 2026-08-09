package rmjarvis.ultiobserver

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.RemoveCircle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TimeInput
import androidx.compose.material3.TimePickerDialog
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.text
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import java.time.LocalDate
import java.time.LocalTime

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
        dialogTitle = "Game to",
        fieldLabel = "Points",
    ),
    HALFTIME(
        dialogTitle = "Halftime",
        fieldLabel = "Minutes",
    ),
    BETWEEN_POINTS(
        dialogTitle = "Time between points",
        fieldLabel = "Seconds",
    ),
    TIMEOUT_DURATION(
        dialogTitle = "Timeout duration",
        fieldLabel = "Seconds",
    ),
    HALF(
        dialogTitle = "Half cap",
        fieldLabel = "Minutes",
    ),
    SOFT(
        dialogTitle = "Soft cap",
        fieldLabel = "Minutes",
    ),
    HARD(
        dialogTitle = "Hard cap",
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
    val priorCardIndex: Int = 0,
    val priorCardDraft: PlayerRecord? = null,
)

/**
 * Prior-card row waiting for delete confirmation.
 *
 * @param teamId The team whose prior-card row may be removed.
 * @param recordIndex Original setup prior-card index for the row.
 * @param record Prior-card record shown in the confirmation text.
 */
private data class PendingPriorCardRemoval(
    val teamId: TeamId,
    val recordIndex: Int,
    val record: PlayerRecord,
)

/**
 * Notice shown when a new card-holder entry matches an existing row.
 *
 * @param teamId The team whose existing card holder matched.
 * @param record Entered values to restore if the observer goes back to edit further.
 * @param existingCardDetail Existing card counts shown in the notice.
 */
private data class ExistingPriorCardNotice(
    val teamId: TeamId,
    val record: PlayerRecord,
    val existingCardDetail: String,
)

/**
 * Confirmation state for a new player whose identity partially overlaps existing rows.
 *
 * @param teamId The team receiving the new player.
 * @param record Player record to save if the observer confirms.
 * @param existingIdentities Existing possible matches shown in the confirmation.
 * @param proposedIdentity Newly entered player identity shown in the confirmation.
 */
private data class PossiblePlayerMatchConfirmation(
    val teamId: TeamId,
    val record: PlayerRecord,
    val existingIdentities: List<String>,
    val proposedIdentity: String,
)

/**
 * Render the pregame/edit-game setup form for start time, teams, pull, rules, and prior cards.
 *
 * @param state The setup state currently being edited.
 * @param orientationPreference Orientation behavior whose field-end language should be used.
 * @param onStateChange Callback receiving setup changes from fields and dialogs.
 * @param title Title shown in the setup screen app bar.
 * @param primaryButtonLabel Label for the fixed bottom action.
 * @param onPrimaryAction Callback starting the game or returning to the live screen.
 * @param onSecondaryAction Optional second bottom action beside or below the primary action.
 * @param secondaryButtonLabel Label for the optional second bottom action.
 * @param secondaryButtonColors Colors for the optional second bottom action.
 * @param secondaryActionFullWidth Whether the optional second action should stack below primary.
 * @param onSaveGameForLater Optional callback to save this as a SETUP game in the archive.
 * @param onBackHome Callback returning to Home.
 * @param onHome Callback returning directly to Home.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun SetupScreen(
    state: GameState,
    orientationPreference: OrientationPreference,
    onStateChange: (GameState) -> Unit,
    title: String,
    primaryButtonLabel: String,
    onPrimaryAction: () -> Unit,
    onSecondaryAction: (() -> Unit)?,
    secondaryButtonLabel: String,
    secondaryButtonColors: ButtonColors,
    secondaryActionFullWidth: Boolean,
    onSaveGameForLater: (() -> Unit)?,
    onBackHome: () -> Unit,
    onHome: () -> Unit,
) {
    var editingRule by remember { mutableStateOf<RuleEditTarget?>(null) }
    var showTimeoutRulesDialog by remember { mutableStateOf(false) }
    var showWaterBreaksDialog by remember { mutableStateOf(false) }
    var showGenderRatioRuleDialog by remember { mutableStateOf(false) }
    var setupDialog by remember { mutableStateOf<SetupDialog?>(null) }
    var gameRulesDraft by remember { mutableStateOf<GameRules?>(null) }
    var teamDialog by remember { mutableStateOf<TeamDialog?>(null) }
    var existingPriorCardNotice by remember { mutableStateOf<ExistingPriorCardNotice?>(null) }
    var possiblePlayerMatchConfirmation by remember { mutableStateOf<PossiblePlayerMatchConfirmation?>(null) }
    var pendingPriorCardRemoval by remember { mutableStateOf<PendingPriorCardRemoval?>(null) }
    var playerDeleteRejectedMessage by remember { mutableStateOf<String?>(null) }
    val scrollState = rememberScrollState()

    fun savePriorCardRecord(teamId: TeamId, record: PlayerRecord, editingIndex: Int?) {
        val teamPlayers = state.playersFor(teamId)
        onStateChange(
            state.withPlayersFor(
                teamId = teamId,
                players = teamPlayers.withSavedPriorCardRecord(
                    record = record,
                    editingIndex = editingIndex,
                ),
            ),
        )
        teamDialog = TeamDialog(teamId, TeamSetupDialog.PRIOR_CARDS)
    }

    fun removePriorCardRecord(teamId: TeamId, recordIndex: Int) {
        val teamPlayers = state.playersFor(teamId)
        val record = teamPlayers[recordIndex]
        if (record.cards.isNotEmpty()) {
            playerDeleteRejectedMessage =
                "${record.playerIdentity(compact = false)} has an in-game card and cannot be deleted."
            return
        }
        onStateChange(state.withPlayersFor(teamId, teamPlayers.filterIndexed { index, _ -> index != recordIndex }))
    }

    fun handlePriorCardSave(teamId: TeamId, record: PlayerRecord, editingIndex: Int?) {
        val teamPlayers = state.playersFor(teamId)
        // No else branch: null plus every CardHolderEntryCheck subtype is handled.
        when (val entryCheck = teamPlayers.cardHolderEntryCheck(record, editingIndex)) {
            null -> savePriorCardRecord(teamId, record, editingIndex)
            is CardHolderEntryCheck.ExistingCardHolder -> {
                val existingRecord = teamPlayers[entryCheck.existingIndex]
                teamDialog = null
                existingPriorCardNotice = ExistingPriorCardNotice(
                    teamId = teamId,
                    record = record,
                    existingCardDetail = existingRecord.playerCardNoticeDetail(),
                )
            }
            is CardHolderEntryCheck.PossibleDifferentPlayer -> {
                teamDialog = null
                possiblePlayerMatchConfirmation = PossiblePlayerMatchConfirmation(
                    teamId = teamId,
                    record = record,
                    existingIdentities = entryCheck.existingIndices.map { teamPlayers[it].playerIdentity(compact = false) },
                    proposedIdentity = record.playerIdentity(compact = false),
                )
            }
        }
    }

    fun returnToPossiblePlayerMatchEntry(confirmation: PossiblePlayerMatchConfirmation) {
        possiblePlayerMatchConfirmation = null
        teamDialog = TeamDialog(
            teamId = confirmation.teamId,
            dialog = TeamSetupDialog.ADD_PRIOR_CARD,
            priorCardDraft = confirmation.record,
        )
    }

    fun returnToExistingPriorCardEntry(notice: ExistingPriorCardNotice) {
        existingPriorCardNotice = null
        teamDialog = TeamDialog(
            teamId = notice.teamId,
            dialog = TeamSetupDialog.ADD_PRIOR_CARD,
            priorCardDraft = notice.record,
        )
    }

    // Compose the setup screen as compact overview rows plus modal editors.
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    TopBarBackButton(onClick = onBackHome)
                },
                actions = {
                    TopBarHomeButton(onClick = onHome)
                },
            )
        },
        bottomBar = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (onSecondaryAction != null && secondaryActionFullWidth) {
                    NavigationButton(
                        label = primaryButtonLabel,
                        fullWidth = true,
                        onClick = onPrimaryAction,
                    )
                    NavigationButton(
                        label = secondaryButtonLabel,
                        fullWidth = true,
                        colors = secondaryButtonColors,
                        onClick = onSecondaryAction,
                    )
                } else if (onSecondaryAction != null) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        NavigationButton(
                            label = secondaryButtonLabel,
                            modifier = Modifier.weight(1f),
                            colors = secondaryButtonColors,
                            onClick = onSecondaryAction,
                        )
                        NavigationButton(
                            label = primaryButtonLabel,
                            modifier = Modifier.weight(2f),
                            onClick = onPrimaryAction,
                        )
                    }
                } else {
                    NavigationButton(
                        label = primaryButtonLabel,
                        fullWidth = true,
                        onClick = onPrimaryAction,
                    )
                }
                if (onSaveGameForLater != null) {
                    NavigationButton(
                        label = "Save as a draft",
                        fullWidth = true,
                        colors = secondaryButtonColors(),
                        onClick = onSaveGameForLater,
                    )
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
            SetupFieldBox {
                Text(
                    text = "Team information",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                TeamEditor(
                    fieldLabel = TeamId.TEAM_ONE.defaultName(),
                    team = state.teamOne,
                    priorCards = state.teamOnePlayers,
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
                    fieldLabel = TeamId.TEAM_TWO.defaultName(),
                    team = state.teamTwo,
                    priorCards = state.teamTwoPlayers,
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
                title = "Game information",
                editTag = "setup-edit-game-information",
                onEdit = { setupDialog = SetupDialog.GAME_INFORMATION },
            ) {
                SetupSummaryLines(state.gameInformationSummaryLines())
            }

            SetupSummaryRow(
                title = "Field/starting pull",
                editTag = "setup-edit-starting-pull",
                onEdit = { setupDialog = SetupDialog.STARTING_PULL },
            ) {
                SetupSummaryLines(
                    state.fieldStartingPullSummaryLines(orientationPreference),
                )
            }
            SetupSummaryRow(
                title = "Game rules",
                editTag = "setup-edit-game-rules",
                onEdit = {
                    gameRulesDraft = state.rules
                    setupDialog = SetupDialog.GAME_RULES
                },
            ) {
                SetupSummaryLines(state.gameRulesSummaryLines())
            }
        }
    }

    // If setupDialog is set, then on this re-render, open the corresponding dialog box.
    // No else branch: every SetupDialog value plus null is handled.
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
                preference = orientationPreference,
                onStateChange = onStateChange,
                onDismiss = { setupDialog = null },
            )
        }

        SetupDialog.GAME_RULES -> {
            val rulesDraft = gameRulesDraft!!
            if (showTimeoutRulesDialog) {
                TimeoutRulesDialog(
                    rules = rulesDraft,
                    onDismiss = { showTimeoutRulesDialog = false },
                    onConfirm = { updatedRules ->
                        gameRulesDraft = updatedRules
                        showTimeoutRulesDialog = false
                    },
                )
            } else if (showWaterBreaksDialog) {
                HeatLevelSetupDialog(
                    rules = rulesDraft,
                    onDismiss = { showWaterBreaksDialog = false },
                    onConfirm = { updatedRules ->
                        gameRulesDraft = updatedRules
                        showWaterBreaksDialog = false
                    },
                )
            } else if (showGenderRatioRuleDialog) {
                GenderRatioRuleDialog(
                    rules = rulesDraft,
                    onDismiss = { showGenderRatioRuleDialog = false },
                    onConfirm = { updatedRules ->
                        gameRulesDraft = updatedRules
                        showGenderRatioRuleDialog = false
                    },
                )
            } else if (editingRule == null) {
                GameRulesSetupDialog(
                    state = state.copy(rules = rulesDraft),
                    onEditRule = { editingRule = it },
                    onEditTimeouts = { showTimeoutRulesDialog = true },
                    onEditWaterBreaks = { showWaterBreaksDialog = true },
                    onEditGenderRatio = { showGenderRatioRuleDialog = true },
                    onUseUsauDefaults = {
                        gameRulesDraft = usauDefaultGameRules(state.level)
                    },
                    onConfirm = {
                        onStateChange(state.copy(rules = rulesDraft))
                        gameRulesDraft = null
                        setupDialog = null
                    },
                    onDismiss = {
                        gameRulesDraft = null
                        setupDialog = null
                    },
                )
            } else {
                // No else branch: every RuleEditTarget value is handled.
                when (val target = editingRule!!) {
                    RuleEditTarget.GAME_TO -> {
                        IntegerEditDialog(
                            title = target.dialogTitle,
                            fieldLabel = target.fieldLabel,
                            initialValue = rulesDraft.gameTo,
                            onDismiss = { editingRule = null },
                            onConfirm = { newValue ->
                                gameRulesDraft = rulesDraft.copy(
                                    gameTo = newValue.coerceAtLeast(1)
                                )
                                editingRule = null
                            },
                        )
                    }

                    RuleEditTarget.HALFTIME -> {
                        IntegerEditDialog(
                            title = target.dialogTitle,
                            fieldLabel = target.fieldLabel,
                            initialValue = rulesDraft.halftimeMinutes,
                            onDismiss = { editingRule = null },
                            onConfirm = { newValue ->
                                gameRulesDraft = rulesDraft.copy(
                                    halftimeMinutes = newValue.coerceAtLeast(1)
                                )
                                editingRule = null
                            },
                        )
                    }

                    RuleEditTarget.BETWEEN_POINTS -> {
                        IntegerEditDialog(
                            title = target.dialogTitle,
                            fieldLabel = target.fieldLabel,
                            initialValue = rulesDraft.nominalTimeBetweenPointsSeconds,
                            note = "This is the time until offense must signal readiness. " +
                                "Defense has up to 20 seconds after this time to pull.",
                            effectNote = if (rulesDraft.heatLevel == HeatLevel.LEVEL_2) {
                                "${rulesDraft.heatLevelLabel()} 2 adds an additional " +
                                    "60 seconds."
                            } else {
                                null
                            },
                            onDismiss = { editingRule = null },
                            onConfirm = { newValue ->
                                gameRulesDraft = rulesDraft.copy(
                                    nominalTimeBetweenPointsSeconds = newValue.coerceAtLeast(1)
                                )
                                editingRule = null
                            },
                        )
                    }

                    RuleEditTarget.TIMEOUT_DURATION -> {
                        IntegerEditDialog(
                            title = target.dialogTitle,
                            fieldLabel = target.fieldLabel,
                            initialValue = rulesDraft.timeoutSeconds,
                            note = "This is the time until offense must be set. " +
                                "Defense has up to 20 seconds after this time to check the disc in.",
                            onDismiss = { editingRule = null },
                            onConfirm = { newValue ->
                                gameRulesDraft = rulesDraft.copy(
                                    timeoutSeconds = newValue.coerceAtLeast(1)
                                )
                                editingRule = null
                            },
                        )
                    }

                    RuleEditTarget.HALF -> {
                        CapRuleEditDialog(
                            title = target.dialogTitle,
                            fieldLabel = target.fieldLabel,
                            prefixText = "Half cap at:",
                            suffixText = "minutes after start time.",
                            initialValue = rulesDraft.halfCapMinutes,
                            initiallyEnabled = rulesDraft.useHalfCap,
                            onDismiss = { editingRule = null },
                            onConfirm = { enabled, newValue ->
                                gameRulesDraft = rulesDraft.copy(
                                    useHalfCap = enabled,
                                    halfCapMinutes = newValue,
                                )
                                editingRule = null
                            },
                        )
                    }

                    RuleEditTarget.SOFT -> {
                        CapRuleEditDialog(
                            title = target.dialogTitle,
                            fieldLabel = target.fieldLabel,
                            prefixText = "Soft cap at:",
                            suffixText = "minutes after start time.",
                            initialValue = rulesDraft.nominalSoftCapMinutes,
                            initiallyEnabled = rulesDraft.useSoftCap,
                            note = rulesDraft.heatLevelTwoCapEffectNote(CapType.SOFT),
                            onDismiss = { editingRule = null },
                            onConfirm = { enabled, newValue ->
                                gameRulesDraft = rulesDraft.copy(
                                    useSoftCap = enabled,
                                    nominalSoftCapMinutes = newValue,
                                )
                                editingRule = null
                            },
                        )
                    }

                    RuleEditTarget.HARD -> {
                        CapRuleEditDialog(
                            title = target.dialogTitle,
                            fieldLabel = target.fieldLabel,
                            prefixText = "Hard cap at:",
                            suffixText = "minutes after start time.",
                            initialValue = rulesDraft.nominalHardCapMinutes,
                            initiallyEnabled = rulesDraft.useHardCap,
                            note = rulesDraft.heatLevelTwoCapEffectNote(CapType.HARD),
                            onDismiss = { editingRule = null },
                            onConfirm = { enabled, newValue ->
                                gameRulesDraft = rulesDraft.copy(
                                    useHardCap = enabled,
                                    nominalHardCapMinutes = newValue,
                                )
                                editingRule = null
                            },
                        )
                    }
                }
            }
        }

        null -> Unit
    }

    // If teamDialog is set, then on this re-render, open the corresponding dialog box
    // for the team set in the teamId field for the dialog.
    val target = teamDialog
    if (target != null) {
        val targetLabel = target.teamId.setupName(state)
        val targetTeam = if (target.teamId == TeamId.TEAM_ONE) state.teamOne else state.teamTwo

        fun changeTargetTeam(updatedTeam: TeamState) {
            onStateChange(
                if (target.teamId == TeamId.TEAM_ONE) {
                    state.copy(teamOne = updatedTeam)
                } else {
                    state.copy(teamTwo = updatedTeam)
                }
            )
        }

        // No else branch: every TeamSetupDialog value is handled.
        when (target.dialog) {
            TeamSetupDialog.COLOR -> {
                val testTagPrefix = "setup-${target.teamId.defaultName()}-color"
                ColorChoiceDialog(
                    title = "$targetLabel Color",
                    selectedPreset = targetTeam.color.takeUnless {
                        it == TeamColorChoice.CUSTOM
                    },
                    customColorArgb = targetTeam.customColorArgb,
                    customColorSelected = targetTeam.color == TeamColorChoice.CUSTOM,
                    testTagPrefix = testTagPrefix,
                    onPresetColorSelected = { color ->
                        changeTargetTeam(targetTeam.copy(color = color))
                        teamDialog = null
                    },
                    onCustomColorSelected = { colorArgb ->
                        changeTargetTeam(
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
                CustomColorChoiceDialog(
                    title = "$targetLabel Color",
                    initialColorArgb = targetTeam.customColorArgb
                        ?: targetTeam.accent.toOpaqueArgbLong(),
                    testTagPrefix = "setup-${target.teamId.defaultName()}-color",
                    onColorSelected = { colorArgb ->
                        changeTargetTeam(
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
                    teamFieldLabel = target.teamId.defaultName(),
                    team = targetTeam,
                    onTeamChange = { updatedTeam ->
                        changeTargetTeam(updatedTeam)
                    },
                    onDismiss = { teamDialog = null },
                )
            }

            TeamSetupDialog.PRIOR_CARDS -> {
                val pendingRemoval = pendingPriorCardRemoval
                val deleteRejectedMessage = playerDeleteRejectedMessage
                if (pendingRemoval != null) {
                    fun dismissPriorCardRemoval() {
                        pendingPriorCardRemoval = null
                    }

                    AlertDialog(
                        onDismissRequest = {
                            dismissPriorCardRemoval()
                        },
                        title = { Text("Remove card holder?") },
                        text = {
                            Text("Remove prior cards for ${pendingRemoval.record.playerIdentity(compact = false)}?")
                        },
                        confirmButton = {
                            TextActionButton(
                                label = "Remove",
                                onClick = {
                                    removePriorCardRecord(
                                        pendingRemoval.teamId,
                                        pendingRemoval.recordIndex,
                                    )
                                    dismissPriorCardRemoval()
                                },
                            )
                        },
                        dismissButton = {
                            TextActionButton(
                                label = "Cancel",
                                onClick = {
                                    dismissPriorCardRemoval()
                                },
                            )
                        },
                    )
                } else if (deleteRejectedMessage != null) {
                    fun dismissPlayerDeleteRejectedMessage() {
                        playerDeleteRejectedMessage = null
                    }
                    AlertDialog(
                        onDismissRequest = {
                            dismissPlayerDeleteRejectedMessage()
                        },
                        title = { Text("Player not deleted") },
                        text = { Text(deleteRejectedMessage) },
                        confirmButton = {
                            TextActionButton(
                                label = "OK",
                                onClick = {
                                    dismissPlayerDeleteRejectedMessage()
                                },
                            )
                        },
                    )
                } else {
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
                            pendingPriorCardRemoval = PendingPriorCardRemoval(
                                teamId = target.teamId,
                                recordIndex = index,
                                record = state.playersFor(target.teamId)[index],
                            )
                        },
                        onDismiss = { teamDialog = null },
                    )
                }
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
                        handlePriorCardSave(target.teamId, record, editingIndex = null)
                    },
                )
            }

            TeamSetupDialog.EDIT_PRIOR_CARD -> {
                val recordIndex = target.priorCardIndex
                val teamPlayers = state.playersFor(target.teamId)
                PriorCardPlayerDialog(
                    teamId = target.teamId,
                    teamName = targetLabel,
                    initialRecord = teamPlayers[recordIndex],
                    isEditing = true,
                    onDismiss = {
                        teamDialog = TeamDialog(target.teamId, TeamSetupDialog.PRIOR_CARDS)
                    },
                    onConfirm = { updatedRecord ->
                        handlePriorCardSave(target.teamId, updatedRecord, editingIndex = recordIndex)
                    },
                )
            }
        }
    }

    existingPriorCardNotice?.let { notice ->
        AlertDialog(
            onDismissRequest = {
                returnToExistingPriorCardEntry(notice)
            },
            title = { Text("Card holder already listed") },
            text = {
                Text(
                    "This player is already listed as a card holder with " +
                        "${notice.existingCardDetail}. Edit that entry if you want to update it."
                )
            },
            confirmButton = {
                TextActionButton(
                    label = "OK",
                    onClick = {
                        existingPriorCardNotice = null
                        teamDialog = TeamDialog(notice.teamId, TeamSetupDialog.PRIOR_CARDS)
                    }
                )
            },
            dismissButton = {
                TextActionButton(
                    label = "Back",
                    onClick = { returnToExistingPriorCardEntry(notice) },
                    tag = "setup-existing-card-holder-back",
                )
            },
        )
    }

    possiblePlayerMatchConfirmation?.let { confirmation ->
        AlertDialog(
            onDismissRequest = {
                returnToPossiblePlayerMatchEntry(confirmation)
            },
            title = { Text("Possible player match") },
            text = {
                val existingText = confirmation.existingIdentities.joinToString(" and ")
                Text(
                    "${confirmation.proposedIdentity} partially matches " +
                        "$existingText. Add " +
                        "${confirmation.proposedIdentity} as a different player?"
                )
            },
            confirmButton = {
                TextActionButton(
                    label = "Add",
                    onClick = {
                        possiblePlayerMatchConfirmation = null
                        savePriorCardRecord(confirmation.teamId, confirmation.record, editingIndex = null)
                    }
                )
            },
            dismissButton = {
                TextActionButton(
                    label = "Cancel",
                    onClick = {
                        returnToPossiblePlayerMatchEntry(confirmation)
                    }
                )
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
        shape = PanelShape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = PanelShape,
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
        shape = PanelShape,
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.outlineVariant,
                    shape = PanelShape,
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
            SetupEditButton(
                onClick = onEdit,
                tag = editTag,
            )
        }
    }
}

/**
 * Render the game-information setup dialog.
 *
 * @param state The setup state whose game information is being edited.
 * @param onStateChange Callback receiving updated setup state.
 * @param onDismiss Callback closing the dialog.
 */
@Composable
private fun GameInformationSetupDialog(
    state: GameState,
    onStateChange: (GameState) -> Unit,
    onDismiss: () -> Unit,
) {
    var startDate by remember { mutableStateOf(state.startDate) }
    var startTime by remember { mutableStateOf(state.startTime) }
    var tournamentName by remember { mutableStateOf(state.tournamentName) }
    var division by remember { mutableStateOf(state.division) }
    var level by remember { mutableStateOf(state.level) }
    var customLevelVisible by remember { mutableStateOf(state.level.isCustomSetupLevel()) }
    var gameContext by remember { mutableStateOf(state.gameContext) }
    val observerNames = remember {
        mutableStateListOf<String>().apply {
            addAll(state.observerNames.initialObserverRows())
        }
    }
    var fieldName by remember { mutableStateOf(state.fieldName) }
    var showStartDateDialog by remember { mutableStateOf(false) }
    var showStartTimeDialog by remember { mutableStateOf(false) }
    val dialogBodyMaxHeight = keyboardDialogBodyMaxHeight()

    fun saveAndDismiss() {
        onStateChange(
            state.copy(
                startDate = startDate,
                startTime = startTime,
                tournamentName = tournamentName,
                division = division,
                level = level,
                gameContext = gameContext,
                observerNames = observerNames.cleanedObserverNames(),
                fieldName = fieldName,
                rules = state.rules.withLevelDefaultTimeBetweenPoints(
                    previousLevel = state.level,
                    newLevel = level,
                ),
            )
        )
        onDismiss()
    }

    if (showStartDateDialog) {
        LocalDatePickerDialog(
            initialDate = startDate,
            setButtonTag = "setup-start-date-set",
            onDismiss = { showStartDateDialog = false },
            onConfirm = {
                startDate = it
                showStartDateDialog = false
            },
        )
    } else if (showStartTimeDialog) {
        ExactTimeDialog(
            initialTime = startTime,
            onDismiss = { showStartTimeDialog = false },
            onConfirm = {
                startTime = it
                showStartTimeDialog = false
            },
        )
    } else {
        AlertDialog(
            modifier = Modifier
                .then(dialogInitialFocusModifier()),
            onDismissRequest = {
                saveAndDismiss()
            },
            title = { Text("Game information") },
            text = {
                ScrollableDialogRegion(
                    maxHeight = dialogBodyMaxHeight,
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

                    ObserverNameEntries(
                        observerNames = observerNames,
                        onAddObserver = { observerNames += "" },
                        onUpdateObserver = { index, name ->
                            observerNames[index] = name
                        },
                        onRemoveObserver = { index ->
                            observerNames.removeAt(index)
                        },
                    )
                    TextEntry(
                        value = fieldName,
                        onValueChange = { fieldName = it },
                        labelText = "Field name/number",
                        capitalization = KeyboardCapitalization.Words,
                        tag = "setup-field-name",
                    )
                    TextEntry(
                        value = tournamentName,
                        onValueChange = { tournamentName = it },
                        labelText = "Tournament name",
                        capitalization = KeyboardCapitalization.Words,
                        tag = "setup-tournament-name",
                    )

                    Text("Division", fontWeight = FontWeight.SemiBold)
                    GameDivisionChoiceRow(
                        selected = division,
                        onSelected = { division = it },
                    )

                    Text("Level", fontWeight = FontWeight.SemiBold)
                    GameLevelChoiceRow(
                        selected = level,
                        customLevelVisible = customLevelVisible,
                        onSelected = {
                            level = it
                            customLevelVisible = false
                        },
                        onClear = {
                            level = ""
                            customLevelVisible = false
                        },
                        onOther = {
                            if (!level.isCustomSetupLevel()) {
                                level = ""
                            }
                            customLevelVisible = true
                        },
                    )
                    if (customLevelVisible) {
                        TextEntry(
                            value = level,
                            onValueChange = { level = it },
                            labelText = "Other level",
                            capitalization = KeyboardCapitalization.Words,
                            tag = "setup-game-level-other-text",
                        )
                    }

                    TextEntry(
                        value = gameContext,
                        onValueChange = { gameContext = it },
                        labelText = "Game context",
                        promptText = "Pool play, Semi-finals, etc.",
                        capitalization = KeyboardCapitalization.Sentences,
                        tag = "setup-game-context",
                    )
                }
            },
            confirmButton = {
                TextActionButton(
                    label = "Done",
                    onClick = {
                        saveAndDismiss()
                    },
                )
            },
            dismissButton = {
                TextActionButton(label = "Cancel", onClick = onDismiss)
            },
        )
    }
}

/**
 * Render structured observer-name entry rows.
 *
 * @param observerNames Mutable row values currently shown in the dialog.
 * @param onAddObserver Callback adding one blank observer row.
 * @param onUpdateObserver Callback updating one observer row.
 * @param onRemoveObserver Callback removing one blank observer row.
 */
@Composable
private fun ObserverNameEntries(
    observerNames: List<String>,
    onAddObserver: () -> Unit,
    onUpdateObserver: (Int, String) -> Unit,
    onRemoveObserver: (Int) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Observers",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        observerNames.forEachIndexed { index, observerName ->
            val addInThisRow = index == observerNames.lastIndex
            val canRemove = index == observerNames.lastIndex &&
                observerNames.size > 1 &&
                observerName.trim().isEmpty()
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("setup-observer-row-$index"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextEntry(
                    value = observerName,
                    onValueChange = { name ->
                        onUpdateObserver(index, name)
                    },
                    promptText = "Observer ${index + 1}",
                    capitalization = KeyboardCapitalization.Words,
                    modifier = Modifier.weight(1f),
                    tag = "setup-observer-$index",
                )
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                    Row(
                        modifier = Modifier.width(56.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        if (canRemove) {
                            IconActionButton(
                                icon = Icons.Filled.RemoveCircle,
                                contentDescription = "Remove observer ${index + 1}",
                                tag = "setup-remove-observer-$index",
                                onClick = { onRemoveObserver(index) },
                            )
                        }
                        if (addInThisRow) {
                            IconActionButton(
                                icon = Icons.Filled.AddCircle,
                                contentDescription = "Add observer",
                                tag = "setup-add-observer",
                                onClick = onAddObserver,
                            )
                        }
                    }
                }
            }
        }
    }
}

/// Return observer rows to show when the game-information dialog opens.
private fun List<String>.initialObserverRows(): List<String> {
    val cleanedNames = cleanedObserverNames()
    return if (cleanedNames.size >= 2) {
        cleanedNames
    } else {
        cleanedNames + List(2 - cleanedNames.size) { "" }
    }
}

/// Return trimmed non-empty observer names.
private fun List<String>.cleanedObserverNames(): List<String> {
    return map { it.trim() }.filter { it.isNotEmpty() }
}

/**
 * Render the level chooser for the game-information setup dialog.
 *
 * @param selected The currently selected level text.
 * @param customLevelVisible Whether the custom level field is active.
 * @param onSelected Callback receiving one of the preset level values.
 * @param onClear Callback clearing the level value.
 * @param onOther Callback showing the custom level field.
 */
@Composable
private fun GameLevelChoiceRow(
    selected: String,
    customLevelVisible: Boolean,
    onSelected: (String) -> Unit,
    onClear: () -> Unit,
    onOther: () -> Unit,
) {
    val selectedLevel = selected.trim()
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        setupLevelPresets().forEach { level ->
            SetupChoiceChip(
                label = level,
                selected = selectedLevel == level && !customLevelVisible,
                onClick = { onSelected(level) },
                tag = "setup-game-level-${level.testTagText()}",
            )
        }
        SetupChoiceChip(
            label = "Other",
            selected = customLevelVisible,
            onClick = onOther,
            tag = "setup-game-level-other",
        )
        SetupChoiceChip(
            label = "N/A",
            selected = selectedLevel.isEmpty() && !customLevelVisible,
            onClick = onClear,
            tag = "setup-game-level-NA",
        )
    }
}

/// Return whether level text should be treated as custom setup input.
private fun String.isCustomSetupLevel(): Boolean {
    val level = trim()
    return level.isNotEmpty() && level !in setupLevelPresets()
}

/// Return stable setup-chip test-tag text for a display label.
private fun String.testTagText(): String {
    return replace(" ", "-")
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
            SetupChoiceChip(
                label = division?.displayText ?: "N/A",
                selected = selected == division,
                onClick = { onSelected(division) },
                tag = "setup-game-division-${division?.name ?: "NA"}",
            )
        }
    }
}

/**
 * Render the gender-ratio rule chooser for game rules.
 *
 * @param selected The currently selected gender-ratio rule.
 * @param onSelected Callback receiving the selected rule.
 */
@Composable
private fun GenderRatioRuleChoiceRow(
    selected: GenderRatioRule,
    onSelected: (GenderRatioRule) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        GenderRatioRule.entries.forEach { rule ->
            SetupChoiceChip(
                label = rule.displayText,
                selected = selected == rule,
                onClick = { onSelected(rule) },
                tag = "setup-gender-ratio-rule-${rule.name}",
            )
        }
    }
}

/**
 * Render the ABBA initial gender-ratio chooser.
 *
 * @param selected The currently selected first-point gender ratio.
 * @param onSelected Callback receiving the selected ratio.
 */
@Composable
private fun GenderRatioChoiceRow(
    selected: GenderRatio,
    onSelected: (GenderRatio) -> Unit,
) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        GenderRatio.entries.forEach { ratio ->
            SetupChoiceChip(
                label = ratio.displayText,
                selected = selected == ratio,
                onClick = { onSelected(ratio) },
                tag = "setup-initial-gender-ratio-${ratio.name}",
            )
        }
    }
}

/**
 * Render one compact setup-choice selector.
 *
 * @param label The user-facing choice text.
 * @param selected Whether this choice is currently selected.
 * @param onClick Callback selecting this choice.
 * @param tag Optional test tag.
 */
@Composable
private fun SetupChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    tag: String? = null,
) {
    ChoiceChipButton(
        label = label,
        selected = selected,
        tag = tag,
        onClick = onClick,
    )
}

/// Render JVM-formatted lines for one setup overview section.
@Composable
private fun SetupSummaryLines(lines: List<SetupSummaryLine>) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        lines.forEach { line ->
            if (line.label == null) {
                SetupSummaryValue(line.value)
            } else {
                SetupSummaryLabeledValue(
                    label = line.label,
                    value = line.value,
                )
            }
        }
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
    )
}

/**
 * Render a labeled setup value on one line when it fits, or below its label when it does not.
 *
 * The combined label and value remain one semantics string so accessibility and UI tests read the
 * summary the same way in either layout.
 */
@Composable
private fun SetupSummaryLabeledValue(
    label: String,
    value: String,
) {
    val combinedText = "$label $value"
    val textStyle = MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.SemiBold)
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    BoxWithConstraints(
        modifier = Modifier.clearAndSetSemantics {
            text = AnnotatedString(combinedText)
        },
    ) {
        val availableWidthPx = with(density) { maxWidth.roundToPx() }
        val fitsOnOneLine = textMeasurer.measure(
            text = AnnotatedString(combinedText),
            style = textStyle,
            maxLines = 1,
            softWrap = false,
        ).size.width <= availableWidthPx
        if (fitsOnOneLine) {
            Text(text = combinedText, style = textStyle)
        } else {
            Column {
                Text(text = label, style = textStyle)
                Text(
                    text = value,
                    modifier = Modifier.padding(start = 16.dp),
                    style = textStyle,
                )
            }
        }
    }
}

/**
 * Render a neutral setup edit button.
 *
 * @param onClick Callback invoked when the button is tapped.
 * @param tag Optional test tag.
 * @param contentPadding Padding inside the button.
 */
@Composable
private fun SetupEditButton(
    onClick: () -> Unit,
    label: String = "Edit",
    tag: String? = null,
    contentPadding: PaddingValues = DefaultButtonContentPadding,
) {
    MenuButton(
        label = label,
        onClick = onClick,
        fullWidth = false,
        tag = tag,
        contentPadding = contentPadding,
    )
}

/**
 * Render the field-end and opening-pull editor dialog.
 *
 * @param state The setup state whose field-end labels, pull team, and prompt target are being edited.
 * @param preference Orientation preference whose field-end language should be used.
 * @param onStateChange Callback receiving updated setup state.
 * @param onDismiss Callback closing the dialog.
 */
@Composable
private fun StartingPullSetupDialog(
    state: GameState,
    preference: OrientationPreference,
    onStateChange: (GameState) -> Unit,
    onDismiss: () -> Unit,
) {
    var nearEndName by remember { mutableStateOf(state.nearEndName) }
    var farEndName by remember { mutableStateOf(state.farEndName) }
    var committedNearEndName by remember { mutableStateOf(state.nearEndName) }
    var committedFarEndName by remember { mutableStateOf(state.farEndName) }
    var pullingTeam by remember { mutableStateOf(state.openingPullingTeam) }
    var pullingFromEnd by remember { mutableStateOf(state.openingPullingFromEnd) }
    var pullPromptTarget by remember { mutableStateOf(state.pullPromptTarget) }
    var initialGenderRatio by remember { mutableStateOf(state.initialGenderRatio) }
    var firstHalfGenZone by remember { mutableStateOf(state.firstHalfGenZone) }
    val dialogBodyMaxHeight = keyboardDialogBodyMaxHeight()

    fun commitNearEndLabel() {
        committedNearEndName = nearEndName
    }

    fun commitFarEndLabel() {
        committedFarEndName = farEndName
    }

    fun displayFieldEndName(end: FieldEnd): String {
        val customName = when (end) {
            FieldEnd.NEAR -> committedNearEndName
            FieldEnd.FAR -> committedFarEndName
        }.trim()
        return customName.ifEmpty { end.defaultDisplayText(preference) }
    }

    fun saveAndDismiss() {
        onStateChange(
            state.copy(
                nearEndName = nearEndName,
                farEndName = farEndName,
                openingPullingTeam = pullingTeam,
                openingPullingFromEnd = pullingFromEnd,
                pullPromptTarget = pullPromptTarget,
                initialGenderRatio = initialGenderRatio,
                firstHalfGenZone = firstHalfGenZone,
            )
        )
        onDismiss()
    }

    AlertDialog(
        modifier = Modifier
            .then(dialogInitialFocusModifier()),
        onDismissRequest = {
            saveAndDismiss()
        },
        title = { Text("Field/starting pull") },
        text = {
            ScrollableDialogRegion(
                maxHeight = dialogBodyMaxHeight,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    "Give whatever names you want for the two ends of the field. " +
                        "E.g. Road, Parking Lot, Trees, etc. " +
                        if (preference == OrientationPreference.LANDSCAPE) {
                            "(default is Left end and Right end)."
                        } else {
                            "(default is Near end and Far end)."
                        }
                )
                TextEntry(
                    value = farEndName,
                    onValueChange = { farEndName = it },
                    labelText = if (preference == OrientationPreference.LANDSCAPE) {
                        "Left end name"
                    } else {
                        "Far/top end name"
                    },
                    promptText = FieldEnd.FAR.defaultDisplayText(preference),
                    capitalization = KeyboardCapitalization.Sentences,
                    tag = "setup-far-end-name",
                    onDone = { commitFarEndLabel() },
                    onFocusLost = { commitFarEndLabel() },
                )
                TextEntry(
                    value = nearEndName,
                    onValueChange = { nearEndName = it },
                    labelText = if (preference == OrientationPreference.LANDSCAPE) {
                        "Right end name"
                    } else {
                        "Near/bottom end name"
                    },
                    promptText = FieldEnd.NEAR.defaultDisplayText(preference),
                    capitalization = KeyboardCapitalization.Sentences,
                    tag = "setup-near-end-name",
                    onDone = { commitNearEndLabel() },
                    onFocusLost = { commitNearEndLabel() },
                )
                Text("Pulling team", fontWeight = FontWeight.SemiBold)
                TeamChoiceRow(
                    teamOne = state.teamOne,
                    teamTwo = state.teamTwo,
                    selected = pullingTeam,
                    testTagPrefix = "setup-pulling-team",
                    onSelected = { pullingTeam = it },
                )
                Text("Pulling from", fontWeight = FontWeight.SemiBold)
                FieldEndChoiceRow(
                    selected = pullingFromEnd,
                    nearLabel = displayFieldEndName(FieldEnd.NEAR),
                    farLabel = displayFieldEndName(FieldEnd.FAR),
                    onSelected = { pullingFromEnd = it },
                )
                if (state.usesMixedDivision()) {
                    when (state.rules.genderRatioRule) {
                        GenderRatioRule.ABBA -> {
                            Text("First point gender ratio", fontWeight = FontWeight.SemiBold)
                            GenderRatioChoiceRow(
                                selected = initialGenderRatio,
                                onSelected = { initialGenderRatio = it },
                            )
                        }
                        GenderRatioRule.GEN_ZONE -> {
                            val genZonePrompt = if (state.rules.switchGenZoneAtHalftime) {
                                "End for gen zone in first half"
                            } else {
                                "End for gen zone"
                            }
                            Text(
                                genZonePrompt,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text("The team in this end zone decided the gender ratio for each point.")
                            FieldEndChoiceRow(
                                selected = firstHalfGenZone,
                                nearLabel = displayFieldEndName(FieldEnd.NEAR),
                                farLabel = displayFieldEndName(FieldEnd.FAR),
                                onSelected = { firstHalfGenZone = it },
                                testTagPrefix = "setup-first-half-gen-zone",
                            )
                        }
                        else -> Unit
                    }
                }
                Text("Timing prompts for which end?", fontWeight = FontWeight.SemiBold)
                PullPromptTargetChoiceRow(
                    selected = pullPromptTarget,
                    nearLabel = displayFieldEndName(FieldEnd.NEAR),
                    farLabel = displayFieldEndName(FieldEnd.FAR),
                    onSelected = { pullPromptTarget = it },
                )
            }
        },
        confirmButton = {
            TextActionButton(
                label = "Done",
                onClick = {
                    saveAndDismiss()
                },
            )
        },
        dismissButton = {
            TextActionButton(label = "Cancel", onClick = onDismiss)
        },
    )
}

/**
 * Render the game-rules editor dialog.
 *
 * @param state The current setup state to display.
 * @param onEditRule Callback opening a focused editor for one simple rule.
 * @param onEditTimeouts Callback opening the timeout-rules editor.
 * @param onEditWaterBreaks Callback opening the water-break rules editor.
 * @param onEditGenderRatio Callback opening the mixed gender-ratio editor.
 * @param onUseUsauDefaults Callback resetting the rule bundle to USAU defaults.
 * @param onConfirm Callback applying the rule edits and closing the dialog.
 * @param onDismiss Callback closing the dialog.
 */
@Composable
private fun GameRulesSetupDialog(
    state: GameState,
    onEditRule: (RuleEditTarget) -> Unit,
    onEditTimeouts: () -> Unit,
    onEditWaterBreaks: () -> Unit,
    onEditGenderRatio: () -> Unit,
    onUseUsauDefaults: () -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
) {
    val rules = state.rules
    AlertDialog(
        onDismissRequest = onConfirm,
        title = { Text("Game rules") },
        text = {
            ScrollableDialogRegion(
                maxHeight = 440.dp,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EditableValueRow(
                    label = "Game to",
                    value = rules.gameTo.toString(),
                    onClick = { onEditRule(RuleEditTarget.GAME_TO) },
                )
                EditableValueRow(
                    label = "Half cap",
                    value = if (rules.useHalfCap) "+${rules.halfCapMinutes}" else "None",
                    onClick = { onEditRule(RuleEditTarget.HALF) },
                )
                EditableValueRow(
                    label = "Soft cap",
                    value = rules.formatCap(CapType.SOFT),
                    onClick = { onEditRule(RuleEditTarget.SOFT) },
                )
                EditableValueRow(
                    label = "Hard cap",
                    value = rules.formatCap(CapType.HARD),
                    onClick = { onEditRule(RuleEditTarget.HARD) },
                )
                EditableValueRow(
                    label = "Timeouts",
                    value = rules.formatTimeoutRules(),
                    onClick = onEditTimeouts,
                )
                if (state.usesMixedDivision()) {
                    EditableValueRow(
                        label = "Mixed gender ratio",
                        value = rules.genderRatioRule.displayText,
                        onClick = onEditGenderRatio,
                    )
                }
                EditableValueRow(
                    label = rules.heatLevelEditorLabel(),
                    value = rules.formatHeatLevel(compact = false),
                    onClick = onEditWaterBreaks,
                )
                EditableValueRow(
                    label = "Time between points",
                    value = rules.formatTimeBetweenPoints(compact = false),
                    onClick = { onEditRule(RuleEditTarget.BETWEEN_POINTS) },
                )
                EditableValueRow(
                    label = "Timeout duration",
                    value = rules.formatTimeoutDuration(),
                    onClick = { onEditRule(RuleEditTarget.TIMEOUT_DURATION) },
                )
                EditableValueRow(
                    label = "Halftime",
                    value = "${rules.halftimeMinutes} min",
                    onClick = { onEditRule(RuleEditTarget.HALFTIME) },
                )
                MenuButton(
                    label = usauDefaultsButtonLabel(state.level),
                    tag = "setup-usau-defaults",
                    colors = resetButtonColors(),
                    borderColor = null,
                    onClick = onUseUsauDefaults,
                )
            }
        },
        confirmButton = {
            TextActionButton(label = "Done", onClick = onConfirm)
        },
        dismissButton = {
            TextActionButton(label = "Cancel", onClick = onDismiss)
        },
    )
}

/**
 * Render the mixed gender-ratio rule editor.
 *
 * @param rules The current rules whose mixed gender-ratio fields are being edited.
 * @param onDismiss Callback closing the dialog without applying changes.
 * @param onConfirm Callback receiving rules updated with the selected gender-ratio rule.
 */
@Composable
private fun GenderRatioRuleDialog(
    rules: GameRules,
    onDismiss: () -> Unit,
    onConfirm: (GameRules) -> Unit,
) {
    var selectedRule by remember { mutableStateOf(rules.genderRatioRule) }
    var switchGenZoneAtHalftime by remember { mutableStateOf(rules.switchGenZoneAtHalftime) }

    AlertDialog(
        modifier = dialogInitialFocusModifier(),
        onDismissRequest = onDismiss,
        title = { Text("Mixed gender ratio") },
        text = {
            ScrollableDialogRegion(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                GenderRatioRuleChoiceRow(
                    selected = selectedRule,
                    onSelected = { selectedRule = it },
                )
                Text(selectedRule.explanation())
                if (selectedRule == GenderRatioRule.GEN_ZONE) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = "Switch Gen Zone at halftime",
                            modifier = Modifier.weight(1f),
                        )
                        Checkbox(
                            checked = switchGenZoneAtHalftime,
                            onCheckedChange = { switchGenZoneAtHalftime = it },
                            modifier = Modifier.testTag("setup-switch-gen-zone-at-halftime"),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextActionButton(
                label = "Set",
                tag = "setup-gender-ratio-set",
                onClick = {
                    onConfirm(
                        rules.copy(
                            genderRatioRule = selectedRule,
                            switchGenZoneAtHalftime = switchGenZoneAtHalftime,
                        )
                    )
                },
            )
        },
        dismissButton = {
            TextActionButton(label = "Cancel", onClick = onDismiss)
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
    state: GameState,
    teamId: TeamId,
    teamName: String,
    onAddPlayer: () -> Unit,
    onEditPlayer: (Int) -> Unit,
    onRemovePlayer: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val teamPriorCards = state.playersFor(teamId).withIndex().toList()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("$teamName Cards from Previous Games") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ScrollableDialogRegion(
                    maxHeight = 320.dp,
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    showBottomChevron = false,
                ) {
                    if (teamPriorCards.isEmpty()) {
                        Text("No card holders added yet")
                    } else {
                        teamPriorCards.forEach { (index, record) ->
                            PlayerRecordRow(
                                label = record.playerIdentity(compact = false),
                                detail = record.cardDetail(),
                                editTag = "setup-prior-card-edit-$index",
                                removeTag = "setup-prior-card-remove-$index",
                                onEdit = { onEditPlayer(index) },
                                onRemove = { onRemovePlayer(index) },
                            )
                        }
                    }
                }
                MenuButton(
                    label = "Add card holder",
                    colors = neutralOutlinedButtonColors(),
                    onClick = onAddPlayer,
                )
            }
        },
        confirmButton = {
            TextActionButton(label = "Done", onClick = onDismiss)
        },
    )
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
        modifier = dialogInitialFocusModifier(),
        onDismissRequest = onDismiss,
        title = {},
        confirmButton = {
            TextActionButton(
                label = "Set",
                tag = "setup-start-time-set",
                onClick = {
                    onConfirm(LocalTime.of(timePickerState.hour, timePickerState.minute))
                }
            )
        },
        dismissButton = {
            TextActionButton(label = "Cancel", onClick = onDismiss)
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
 * @param note Optional explanatory text shown above the numeric field.
 * @param effectNote Optional emphasized rule effect shown below the numeric field.
 * @param onDismiss Callback closing the dialog without applying a value.
 * @param onConfirm Callback receiving the parsed non-negative value.
 */
@Composable
private fun IntegerEditDialog(
    title: String,
    fieldLabel: String,
    initialValue: Int,
    note: String? = null,
    effectNote: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (Int) -> Unit,
) {
    var valueText by remember { mutableStateOf(initialValue.toString()) }

    AlertDialog(
        modifier = dialogInitialFocusModifier(),
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            ScrollableDialogRegion(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                if (note != null) {
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                TextEntry(
                    value = valueText,
                    onValueChange = { valueText = it.filter(Char::isDigit) },
                    labelText = fieldLabel,
                    keyboardType = KeyboardType.Number,
                )
                if (effectNote != null) {
                    Text(
                        text = effectNote,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ResetColor,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        },
        confirmButton = {
            TextActionButton(
                label = "Set",
                tag = "setup-integer-set",
                onClick = {
                    onConfirm(valueText.toIntOrNull()?.coerceAtLeast(0) ?: initialValue)
                }
            )
        },
        dismissButton = {
            TextActionButton(label = "Cancel", onClick = onDismiss)
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
    note: String? = null,
    onDismiss: () -> Unit,
    onConfirm: (Boolean, Int) -> Unit,
) {
    var valueText by remember { mutableStateOf(initialValue.toString()) }
    var enabled by remember { mutableStateOf(initiallyEnabled) }

    AlertDialog(
        modifier = dialogInitialFocusModifier(),
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            ScrollableDialogRegion(verticalArrangement = Arrangement.spacedBy(10.dp)) {
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
                TextEntry(
                    value = valueText,
                    onValueChange = { valueText = it.filter(Char::isDigit) },
                    labelText = fieldLabel,
                    keyboardType = KeyboardType.Number,
                    enabled = enabled,
                )
                Text(suffixText)
                if (note != null) {
                    Text(
                        text = note,
                        style = MaterialTheme.typography.bodyMedium,
                        color = ResetColor,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        },
        confirmButton = {
            TextActionButton(
                label = "Set",
                tag = "setup-$title-set",
                onClick = {
                    onConfirm(enabled, valueText.toIntOrNull()?.coerceAtLeast(0) ?: initialValue)
                }
            )
        },
        dismissButton = {
            TextActionButton(label = "Cancel", onClick = onDismiss)
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
        modifier = dialogInitialFocusModifier(),
        onDismissRequest = onDismiss,
        title = { Text("Timeout rules") },
        text = {
            ScrollableDialogRegion(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TextEntry(
                    value = timeoutsText,
                    onValueChange = { timeoutsText = it.filter(Char::isDigit).take(2) },
                    labelText = "Timeouts per half",
                    keyboardType = KeyboardType.Number,
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
            TextActionButton(
                label = "Set",
                tag = "setup-timeouts-set",
                onClick = {
                    onConfirm(
                        rules.copy(
                            timeoutsPerHalf = timeoutsText.toIntOrNull()?.coerceAtLeast(0) ?: rules.timeoutsPerHalf,
                            hasFloaterTimeout = hasFloater,
                        )
                    )
                }
            )
        },
        dismissButton = {
            TextActionButton(label = "Cancel", onClick = onDismiss)
        },
    )
}

/**
 * Render the heat-level chooser shared by setup and live More actions.
 *
 * @param includeLevelThree Whether the live-only suspension level should be available.
 */
@Composable
internal fun HeatLevelChoiceRow(
    selected: HeatLevel,
    includeLevelThree: Boolean,
    onSelected: (HeatLevel) -> Unit,
) {
    val choices = if (includeLevelThree) {
        HeatLevel.entries
    } else {
        HeatLevel.entries.filter { level -> level != HeatLevel.LEVEL_3 }
    }
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        choices.forEach { level ->
            ChoiceChipButton(
                label = level.displayText,
                selected = selected == level,
                onClick = { onSelected(level) },
                tag = "heat-level-${level.name}",
            )
        }
    }
}

/**
 * Render the detailed setup editor for heat/AQI-level and water-break rules.
 *
 * @param rules The current rules whose water-break fields are being edited.
 * @param onDismiss Callback closing the dialog without applying changes.
 * @param onConfirm Callback receiving rules updated with the heat-level values.
 */
@Composable
private fun HeatLevelSetupDialog(
    rules: GameRules,
    onDismiss: () -> Unit,
    onConfirm: (GameRules) -> Unit,
) {
    var useAirQualityGuidelines by remember {
        mutableStateOf(rules.useAirQualityGuidelines)
    }
    var selectedHeatLevel by remember { mutableStateOf(rules.heatLevel) }
    var minutesText by remember { mutableStateOf(rules.waterBreakMinutes.toString()) }
    val displayedRules = rules.copy(
        useAirQualityGuidelines = useAirQualityGuidelines,
        heatLevel = selectedHeatLevel,
        waterBreakMinutes = minutesText.toIntOrNull() ?: rules.waterBreakMinutes,
    )
    val waterBreakScores = rules.waterBreakScores()
    val firstBreakScore = waterBreakScores[0]
    val secondBreakScore = waterBreakScores[1]

    AlertDialog(
        modifier = dialogInitialFocusModifier(),
        onDismissRequest = onDismiss,
        title = {
            Text(if (useAirQualityGuidelines) "AQI level" else "Heat level")
        },
        text = {
            ScrollableDialogRegion(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    "Select the current " +
                        "${if (useAirQualityGuidelines) "AQI" else "heat"} level for automatic " +
                        "water-break timing or Manual for manual water breaks."
                )
                HeatLevelChoiceRow(
                    selected = selectedHeatLevel,
                    includeLevelThree = false,
                    onSelected = { newLevel ->
                        val standardRules = rules
                            .copy(useAirQualityGuidelines = useAirQualityGuidelines)
                            .withHeatLevel(newLevel)
                        selectedHeatLevel = newLevel
                        minutesText = standardRules.waterBreakMinutes.toString()
                    },
                )
                HeatLevelGuidance(
                    heatLevel = selectedHeatLevel,
                    minutes = displayedRules.waterBreakMinutes,
                    firstBreakScore = firstBreakScore,
                    secondBreakScore = secondBreakScore,
                    levelTwoCapGuidance = displayedRules.heatLevelTwoCapGuidance(),
                )
                if (selectedHeatLevel != HeatLevel.NONE) {
                    TextEntry(
                        value = minutesText,
                        onValueChange = { minutesText = it.filter(Char::isDigit).take(2) },
                        labelText = "Water break minutes",
                        keyboardType = KeyboardType.Number,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Use guidelines for air quality rather than heat?",
                        modifier = Modifier.weight(1f),
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (useAirQualityGuidelines) "Yes" else "No",
                            modifier = Modifier.testTag("air-quality-guidelines-value"),
                        )
                        Switch(
                            checked = useAirQualityGuidelines,
                            onCheckedChange = { useAirQuality ->
                                useAirQualityGuidelines = useAirQuality
                                minutesText = displayedRules
                                    .withAirQualityGuidelines(useAirQuality)
                                    .waterBreakMinutes
                                    .toString()
                            },
                            modifier = Modifier.testTag("air-quality-guidelines"),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextActionButton(
                label = "Set",
                tag = "setup-water-breaks-set",
                onClick = {
                    onConfirm(
                        rules.copy(
                            useAirQualityGuidelines = useAirQualityGuidelines,
                            heatLevel = selectedHeatLevel,
                            waterBreakMinutes = minutesText.toIntOrNull()?.coerceAtLeast(0)
                                ?: rules.waterBreakMinutes,
                        )
                    )
                }
            )
        },
        dismissButton = {
            TextActionButton(label = "Cancel", onClick = onDismiss)
        },
    )
}

/// Render the concise setup explanation for one primary heat-level selection.
@Composable
private fun HeatLevelGuidance(
    heatLevel: HeatLevel,
    minutes: Int,
    firstBreakScore: Int,
    secondBreakScore: Int,
    levelTwoCapGuidance: String?,
) {
    when (heatLevel) {
        HeatLevel.NONE -> Text("Standard time between points.")
        HeatLevel.LEVEL_1 -> Text(
            "One $minutes-minute water break per half, normally when a team gets to " +
            "$firstBreakScore or $secondBreakScore points (but may change due to caps). " +
            "You may also manually trigger a water break at another time using the water " +
            "drop icon."
        )
        HeatLevel.LEVEL_2 -> Text(
            buildAnnotatedString {
                append(
                    "One $minutes-minute water break per half, normally when a team gets to " +
                    "$firstBreakScore or $secondBreakScore points (but may change due to caps). " +
                    "Additionally, "
                )
                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append("add 60 seconds to the time between points")
                }
                append(".")
                if (levelTwoCapGuidance != null) {
                    append(" $levelTwoCapGuidance")
                }
                append(
                    " You may also manually trigger a water break at another time using the water " +
                    "drop icon."
                )
            }
        )
        HeatLevel.MANUAL -> Text(
            "No automatic water breaks. Use the water drop icon to trigger a $minutes-minute " +
            "water break if desired."
        )
        // Unreachable else: Level 3 is only available after a game has started.
        else -> error("Unsupported setup heat level: $heatLevel")
    }
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
 */
@Composable
private fun PriorCardPlayerDialog(
    teamId: TeamId,
    teamName: String,
    initialRecord: PlayerRecord?,
    isEditing: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (PlayerRecord) -> Unit,
) {
    var jerseyNumber by remember(initialRecord) {
        mutableStateOf(initialRecord?.jerseyNumber ?: "")
    }
    var playerName by remember(initialRecord) {
        mutableStateOf(initialRecord?.playerName ?: "")
    }
    var priorYellows by remember(initialRecord) {
        mutableStateOf(initialRecord?.priorYellows ?: 1)
    }
    var priorReds by remember(initialRecord) {
        mutableStateOf(initialRecord?.priorReds ?: 0)
    }
    val trimmedJerseyNumber = jerseyNumber.trim()
    val trimmedPlayerName = playerName.trim()
    val hasPlayerIdentity = trimmedJerseyNumber.isNotEmpty() || trimmedPlayerName.isNotEmpty()
    val confirmLabel = if (isEditing) "Update" else "Add"

    AlertDialog(
        modifier = dialogInitialFocusModifier(),
        onDismissRequest = onDismiss,
        title = { Text(if (isEditing) "Edit previous game card holder" else "Add previous game card holder") },
        text = {
            ScrollableDialogRegion(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = teamName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                TextEntry(
                    value = jerseyNumber,
                    onValueChange = { jerseyNumber = it.filter(Char::isDigit) },
                    labelText = "Number",
                    keyboardType = KeyboardType.Number,
                    tag = "setup-prior-card-jersey",
                )
                TextEntry(
                    value = playerName,
                    onValueChange = { playerName = it },
                    labelText = "Name",
                    capitalization = KeyboardCapitalization.Words,
                    tag = "setup-prior-card-name",
                )
                CorrectionCountRow(
                    label = "Yellow",
                    value = priorYellows,
                    onIncrement = { priorYellows += 1 },
                    onDecrement = { priorYellows = maxOf(0, priorYellows - 1) },
                )
                CorrectionCountRow(
                    label = "Red",
                    value = priorReds,
                    onIncrement = { priorReds += 1 },
                    onDecrement = { priorReds = maxOf(0, priorReds - 1) },
                )
            }
        },
        confirmButton = {
            TextActionButton(
                label = confirmLabel,
                enabled = hasPlayerIdentity,
                onClick = {
                    onConfirm(
                        PlayerRecord(
                            jerseyNumber = trimmedJerseyNumber,
                            priorYellows = priorYellows,
                            priorReds = priorReds,
                            playerName = trimmedPlayerName,
                            cards = initialRecord?.cards ?: emptyList(),
                        )
                    )
                }
            )
        },
        dismissButton = {
            TextActionButton(label = "Cancel", onClick = onDismiss)
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
    team: TeamState,
    priorCards: List<PlayerRecord>,
    onTeamChange: (TeamState) -> Unit,
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
            TextEntry(
                value = team.name,
                onValueChange = {
                    onTeamChange(team.copy(name = it))
                },
                promptText = fieldLabel,
                promptTextColor = team.content.copy(alpha = 0.65f),
                capitalization = KeyboardCapitalization.Words,
                modifier = Modifier.weight(1f),
                colors = teamNameFieldColors(team),
                tag = "setup-$fieldLabel-name",
            )
            SetupEditButton(
                onClick = onEditColor,
                label = "Edit\nColor",
                tag = "setup-$fieldLabel-color-button",
                contentPadding = compactSetupButtonPadding(),
            )
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
private fun teamNameFieldColors(team: TeamState) = OutlinedTextFieldDefaults.colors(
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
            MenuButton(
                label = "Coach/Captains",
                tag = "setup-$fieldLabel-names-button",
                contentPadding = compactSetupButtonPadding(),
                onClick = onEditNames,
            )
            TeamNamesInlineSummary(namesSummary = namesSummary)
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp),
        ) {
            MenuButton(
                label = "Cards",
                tag = "setup-$fieldLabel-cards-button",
                colors = setupCardsButtonColors(),
                contentPadding = compactSetupButtonPadding(),
                onClick = onEditCards,
            )
            Text(
                text = cardsSummary,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
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
                    modifier = Modifier.width(48.dp),
                    maxLines = 1,
                    softWrap = false,
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
    team: TeamState,
    onTeamChange: (TeamState) -> Unit,
    onDismiss: () -> Unit,
) {
    val dialogBodyMaxHeight = keyboardDialogBodyMaxHeight()

    AlertDialog(
        modifier = Modifier
            .then(dialogInitialFocusModifier()),
        onDismissRequest = onDismiss,
        title = { Text("$teamLabel Names") },
        text = {
            ScrollableDialogRegion(
                maxHeight = dialogBodyMaxHeight,
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
            TextActionButton(label = "Done", onClick = onDismiss)
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
    TextEntry(
        value = value,
        onValueChange = onValueChange,
        labelText = label,
        singleLine = false,
        minLines = 2,
        capitalization = KeyboardCapitalization.Words,
        tag = testTag,
    )
}

/**
 * Render a clickable setup field that looks like a compact form control.
 *
 * @param value The formatted value shown inside the field.
 * @param testTag The test tag attached to the clickable surface.
 * @param modifier Modifier applied to the clickable field surface.
 * @param onClick Callback opening the focused editor for this value.
 */
@Composable
private fun DateTimeDisplayField(
    value: String,
    testTag: String,
    onClick: () -> Unit,
) {
    MenuButton(
        label = value,
        fullWidth = false,
        tag = testTag,
        colors = dialogInputButtonColors(),
        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 12.dp),
        onClick = onClick,
    )
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
    MenuButton(
        label = label,
        trailingLabel = value,
        colors = neutralOutlinedButtonColors(),
        onClick = onClick,
    )
}

/**
 * Render a two-choice row for Team 1 vs Team 2 selection.
 *
 * @param teamOne Team-one state for the label.
 * @param teamTwo Team-two state for the label.
 * @param selected The currently selected team.
 * @param testTagPrefix Prefix for generated chip test tags.
 * @param onSelected Callback receiving the selected team.
 */
@Composable
private fun TeamChoiceRow(
    teamOne: TeamState,
    teamTwo: TeamState,
    selected: TeamId,
    testTagPrefix: String,
    onSelected: (TeamId) -> Unit,
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SetupChoiceChip(
            label = teamOne.normalizedName(TeamId.TEAM_ONE),
            selected = selected == TeamId.TEAM_ONE,
            onClick = {
                onSelected(TeamId.TEAM_ONE)
            },
            tag = "$testTagPrefix-${TeamId.TEAM_ONE.name}",
        )
        SetupChoiceChip(
            label = teamTwo.normalizedName(TeamId.TEAM_TWO),
            selected = selected == TeamId.TEAM_TWO,
            onClick = {
                onSelected(TeamId.TEAM_TWO)
            },
            tag = "$testTagPrefix-${TeamId.TEAM_TWO.name}",
        )
    }
}

/**
 * Render a two-choice row for Near end vs Far end selection.
 *
 * @param selected The currently selected field end.
 * @param nearLabel The display label for the near field end.
 * @param farLabel The display label for the far field end.
 * @param onSelected Callback receiving the selected field end.
 * @param testTagPrefix Prefix for generated chip test tags.
 */
@Composable
private fun FieldEndChoiceRow(
    selected: FieldEnd,
    nearLabel: String,
    farLabel: String,
    onSelected: (FieldEnd) -> Unit,
    testTagPrefix: String = "setup-pulling-from",
) {
    FlowRow(
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        SetupChoiceChip(
            label = nearLabel,
            selected = selected == FieldEnd.NEAR,
            onClick = {
                onSelected(FieldEnd.NEAR)
            },
            tag = "$testTagPrefix-${FieldEnd.NEAR.name}",
        )
        SetupChoiceChip(
            label = farLabel,
            selected = selected == FieldEnd.FAR,
            onClick = {
                onSelected(FieldEnd.FAR)
            },
            tag = "$testTagPrefix-${FieldEnd.FAR.name}",
        )
    }
}

/**
 * Render a choice row for which field end should receive pulling prompts.
 *
 * @param selected The currently selected prompt target.
 * @param nearLabel The display label for the near field end.
 * @param farLabel The display label for the far field end.
 * @param testTagPrefix Prefix used for choice test tags.
 * @param onSelected Callback receiving the selected prompt target.
 */
@Composable
internal fun PullPromptTargetChoiceRow(
    selected: PullPromptTarget,
    nearLabel: String,
    farLabel: String,
    testTagPrefix: String = "setup-pull-prompts",
    onSelected: (PullPromptTarget) -> Unit,
) {
    @Composable
    fun ChoiceChip(target: PullPromptTarget) {
        SetupChoiceChip(
            label = target.choiceLabel(nearLabel = nearLabel, farLabel = farLabel),
            selected = selected == target,
            onClick = {
                onSelected(target)
            },
            tag = "$testTagPrefix-${target.name}",
        )
    }

    Column(
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ChoiceChip(PullPromptTarget.NEAR)
            ChoiceChip(PullPromptTarget.FAR)
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            ChoiceChip(PullPromptTarget.BOTH)
            ChoiceChip(PullPromptTarget.NEITHER)
        }
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
    DialogListItemCard {
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
                IconActionButton(
                    icon = Icons.Filled.Edit,
                    contentDescription = "Edit $label",
                    tag = editTag,
                    onClick = onEdit,
                )
                IconActionButton(
                    icon = Icons.Filled.Delete,
                    contentDescription = "Remove $label",
                    tag = removeTag,
                    onClick = onRemove,
                )
            }
        }
    }
}
