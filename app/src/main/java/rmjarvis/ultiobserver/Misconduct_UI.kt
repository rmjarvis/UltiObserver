package rmjarvis.ultiobserver

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

private val CardChoiceYellow = Color(0xFFFFD92F)
private val CardChoiceRed = Color(0xFFE64B3C)
private val CardChoiceBlue = Color(0xFF1976D2)
private val CardReasonButtonColor = Color(0xFFFFF176)

/**
 * Entered player-card details from the yellow/red card dialog.
 *
 * @param jerseyNumber The player's jersey number, or blank for name-only.
 * @param playerName The player's name, or blank when unknown.
 * @param reason Optional observer-entered card reason text.
 */
private data class PlayerCardEntry(
    val jerseyNumber: String,
    val playerName: String = "",
    val reason: String = "",
)

/**
 * Known carded player offered for quick selection.
 *
 * @param jerseyNumber The player's jersey number, or blank for name-only.
 * @param playerName The player's name, or blank when unknown.
 * @param detail Compact card-count detail for this player.
 */
private data class PlayerCardCandidate(
    val jerseyNumber: String,
    val playerName: String,
    val detail: String,
)

/**
 * Live player-card entry with a same-number, different-name conflict awaiting confirmation.
 *
 * @param team The team receiving the card.
 * @param cardType The card being recorded.
 * @param entry Entered player-card details to record if confirmed.
 * @param conflict The conflicting known player identity.
 */
private data class PendingSameNumberPlayerCardConfirmation(
    val team: TeamId,
    val cardType: CardType,
    val entry: PlayerCardEntry,
    val conflict: SameNumberPlayerIdentityConflict,
)

/// Previous Card-dialog step to restore when dismissing a live-point misconduct choice.
private sealed interface PendingMisconductReturn {
    data class YellowEntry(val team: TeamId, val entry: PlayerCardEntry) : PendingMisconductReturn
    data class RedEntry(val team: TeamId, val entry: PlayerCardEntry) : PendingMisconductReturn
    data class BlueCard(val team: TeamId) : PendingMisconductReturn
}

/**
 * Live-point misconduct assessment waiting for the observer to choose offense or defense.
 *
 * @param result The assessed card or technical-foul result before the side choice is applied.
 * @param returnTo The previous UI step to reopen if the observer dismisses the side-choice prompt.
 */
private data class PendingMisconductChoice(
    val result: CardAssessmentResult,
    val returnTo: PendingMisconductReturn?,
)

/**
 * Live-point misconduct consequence waiting for final confirmation.
 *
 * @param choice The offense/defense choice this consequence resolves.
 * @param againstOffense Whether the misconduct was against the offense.
 */
private data class PendingMisconductResolution(
    val choice: PendingMisconductChoice,
    val againstOffense: Boolean,
)

// Manual card/techs correction dialog, including the per-player reconciliation flow.
@Composable
internal fun AdjustCardsDialog(
    state: GameState,
    now: Long,
    onDismiss: () -> Unit,
    onConfirm: (GameState) -> Unit,
) {
    var teamOneY by remember { mutableStateOf(state.teamYellowCards(TeamId.TEAM_ONE)) }
    var teamOneB by remember { mutableStateOf(state.teamOne.blueCards) }
    var teamOneR by remember { mutableStateOf(state.teamRedCards(TeamId.TEAM_ONE)) }
    var teamOneTf by remember { mutableStateOf(state.teamOne.technicalFouls) }
    var teamTwoY by remember { mutableStateOf(state.teamYellowCards(TeamId.TEAM_TWO)) }
    var teamTwoB by remember { mutableStateOf(state.teamTwo.blueCards) }
    var teamTwoR by remember { mutableStateOf(state.teamRedCards(TeamId.TEAM_TWO)) }
    var teamTwoTf by remember { mutableStateOf(state.teamTwo.technicalFouls) }
    var workingTeamOnePlayerCards by remember { mutableStateOf(state.teamOnePlayers) }
    var workingTeamTwoPlayerCards by remember { mutableStateOf(state.teamTwoPlayers) }
    var pendingSteps by remember { mutableStateOf<List<PlayerCardAdjustmentStep>>(emptyList()) }
    var invalidCardAssignmentMessage by remember { mutableStateOf<String?>(null) }

    fun finalizeAdjustment() {
        onConfirm(
            state.adjustCardsAndTf(
                teamOneBlues = teamOneB,
                teamOneTechnicalFouls = teamOneTf,
                teamTwoBlues = teamTwoB,
                teamTwoTechnicalFouls = teamTwoTf,
                teamOnePlayers = workingTeamOnePlayerCards,
                teamTwoPlayers = workingTeamTwoPlayerCards,
                now = now,
            )
        )
    }

    fun applyCardAssignment(jerseyNumber: String) {
        // Defensive stale-callback guard for a weird timing state.
        val step = pendingSteps.firstOrNull() ?: return
        if (jerseyNumber.isBlank()) {
            invalidCardAssignmentMessage = "Enter a player number before recording this card."
            return
        }
        val currentRecords = if (step.team == TeamId.TEAM_ONE) {
            workingTeamOnePlayerCards
        } else {
            workingTeamTwoPlayerCards
        }
        val identity = PlayerIdentity(jerseyNumber)
        if (step.mode == PlayerCardAdjustmentMode.ADD) {
            val rejection = playerCardAssignmentRejection(currentRecords, identity)
            if (rejection != null) {
                invalidCardAssignmentMessage = "${identity.displayText(compact = true)} ${rejection.messageText}"
                return
            }
        }
        val updatedRecords = when (step.mode) {
            PlayerCardAdjustmentMode.ADD -> addPlayerCardAssignment(currentRecords, jerseyNumber = jerseyNumber, cardType = step.cardType)
            PlayerCardAdjustmentMode.REMOVE -> removePlayerCardAssignment(currentRecords, jerseyNumber = jerseyNumber, cardType = step.cardType)
        }
        if (step.team == TeamId.TEAM_ONE) {
            workingTeamOnePlayerCards = updatedRecords
        } else {
            workingTeamTwoPlayerCards = updatedRecords
        }
        pendingSteps = pendingSteps.drop(1)
        // Walk through the per-player add/remove prompts until all count mismatches are resolved.
        if (pendingSteps.isEmpty()) {
            finalizeAdjustment()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adjust cards / techs") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                TeamCorrectionSection(state.teamOne.name) {
                    CardCountRow("Yellow", teamOneY, { teamOneY += 1 }, { teamOneY = maxOf(0, teamOneY - 1) })
                    CardCountRow("Blue", teamOneB, { teamOneB += 1 }, { teamOneB = maxOf(0, teamOneB - 1) })
                    CardCountRow("Red", teamOneR, { teamOneR += 1 }, { teamOneR = maxOf(0, teamOneR - 1) })
                    CardCountRow("Tech", teamOneTf, { teamOneTf += 1 }, { teamOneTf = maxOf(0, teamOneTf - 1) })
                }
                TeamCorrectionSection(state.teamTwo.name) {
                    CardCountRow("Yellow", teamTwoY, { teamTwoY += 1 }, { teamTwoY = maxOf(0, teamTwoY - 1) })
                    CardCountRow("Blue", teamTwoB, { teamTwoB += 1 }, { teamTwoB = maxOf(0, teamTwoB - 1) })
                    CardCountRow("Red", teamTwoR, { teamTwoR += 1 }, { teamTwoR = maxOf(0, teamTwoR - 1) })
                    CardCountRow("Tech", teamTwoTf, { teamTwoTf += 1 }, { teamTwoTf = maxOf(0, teamTwoTf - 1) })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val steps = state.buildPlayerCardAdjustmentSteps(
                        teamOneYellows = teamOneY,
                        teamOneReds = teamOneR,
                        teamTwoYellows = teamTwoY,
                        teamTwoReds = teamTwoR,
                    )
                    workingTeamOnePlayerCards = state.teamOnePlayers
                    workingTeamTwoPlayerCards = state.teamTwoPlayers
                    if (steps.isEmpty()) {
                        finalizeAdjustment()
                    } else {
                        pendingSteps = steps
                    }
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

    pendingSteps.firstOrNull()?.let { step ->
        // No else branch: every PlayerCardAdjustmentMode value is handled
        when (step.mode) {
            PlayerCardAdjustmentMode.ADD -> {
                PlayerNumberDialog(
                    title = "Add ${step.cardType.label.lowercase()}",
                    teamName = state.teamFor(step.team).name,
                    onDismiss = { pendingSteps = emptyList() },
                    onConfirm = { jerseyNumber ->
                        applyCardAssignment(jerseyNumber)
                    },
                )
            }
            PlayerCardAdjustmentMode.REMOVE -> {
                AssignedCardRemovalDialog(
                    title = "Remove ${step.cardType.label.lowercase()}",
                    teamName = state.teamFor(step.team).name,
                    candidates = playerCardRemovalCandidates(
                        records = if (step.team == TeamId.TEAM_ONE) {
                            workingTeamOnePlayerCards
                        } else {
                            workingTeamTwoPlayerCards
                        },
                        cardType = step.cardType,
                    ),
                    cardType = step.cardType,
                    onDismiss = { pendingSteps = emptyList() },
                    onConfirm = { jerseyNumber ->
                        applyCardAssignment(jerseyNumber)
                    },
                )
            }
        }
    }

    invalidCardAssignmentMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { invalidCardAssignmentMessage = null },
            title = { Text("Invalid card assignment") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { invalidCardAssignmentMessage = null }) {
                    Text("OK")
                }
            },
        )
    }
}

/**
 * Render the dialog flow for recording a card for one team.
 *
 * @param state The current live game state used for team names, card summaries, and assessments.
 * @param team The team receiving the card.
 * @param now The current epoch millis for event logging.
 * @param onDismiss Callback closing the card dialog without recording.
 * @param onAssessment Callback receiving the completed state plus popup text after a card.
 * @param onStateOnly Callback receiving the completed state when the confirmation dialog already showed the result.
 */
@Composable
internal fun TeamCardDialog(
    state: GameState,
    team: TeamId,
    now: Long,
    onDismiss: () -> Unit,
    onAssessment: (GameState, String, String) -> Unit,
    onStateOnly: (GameState) -> Unit,
) {
    var pendingYellowTeam by remember { mutableStateOf<TeamId?>(null) }
    var pendingYellowInitialEntry by remember { mutableStateOf(PlayerCardEntry("")) }
    var pendingRedTeam by remember { mutableStateOf<TeamId?>(null) }
    var pendingRedInitialEntry by remember { mutableStateOf(PlayerCardEntry("")) }
    var pendingBlueTeam by remember { mutableStateOf<TeamId?>(null) }
    var pendingSameNumberConfirmation by remember { mutableStateOf<PendingSameNumberPlayerCardConfirmation?>(null) }
    var pendingMisconductChoice by remember { mutableStateOf<PendingMisconductChoice?>(null) }
    var pendingMisconductResolution by remember { mutableStateOf<PendingMisconductResolution?>(null) }
    var invalidCardAssignmentMessage by remember { mutableStateOf<String?>(null) }

    fun completeAssessment(result: CardAssessmentResult) {
        onAssessment(
            result.state,
            result.event.formatMessage(),
            result.event.formatPopupTitle(),
        )
    }

    fun presentAssessment(result: CardAssessmentResult, returnTo: PendingMisconductReturn?) {
        if (result.needsMisconductChoice) {
            pendingMisconductChoice = PendingMisconductChoice(result, returnTo)
        } else {
            completeAssessment(result)
        }
    }

    fun restoreMisconductReturn(returnTo: PendingMisconductReturn?) {
        pendingMisconductChoice = null
        if (returnTo == null) return
        // No else branch: every PendingMisconductReturn value is handled
        when (returnTo) {
            is PendingMisconductReturn.YellowEntry -> {
                pendingYellowInitialEntry = returnTo.entry
                pendingYellowTeam = returnTo.team
            }
            is PendingMisconductReturn.RedEntry -> {
                pendingRedInitialEntry = returnTo.entry
                pendingRedTeam = returnTo.team
            }
            is PendingMisconductReturn.BlueCard -> {
                pendingBlueTeam = returnTo.team
            }
        }
    }

    fun restoreMisconductResolutionChoice(choice: PendingMisconductChoice) {
        pendingMisconductResolution = null
        if (choice.returnTo is PendingMisconductReturn.BlueCard) {
            restoreMisconductReturn(choice.returnTo)
        } else {
            pendingMisconductChoice = choice
        }
    }

    fun restorePlayerCardEntry(cardType: CardType, team: TeamId, entry: PlayerCardEntry) {
        // No else branch: every CardType value is handled.
        when (cardType) {
            CardType.YELLOW -> {
                pendingYellowInitialEntry = entry
                pendingYellowTeam = team
            }
            CardType.RED -> {
                pendingRedInitialEntry = entry
                pendingRedTeam = team
            }
        }
    }

    fun assessPlayerCardEntry(
        team: TeamId,
        cardType: CardType,
        entry: PlayerCardEntry,
        skipSameNumberWarning: Boolean = false,
    ): Boolean {
        if (entry.jerseyNumber.isBlank() && entry.playerName.isBlank()) {
            invalidCardAssignmentMessage = "Enter a player number or name before recording this card."
            return false
        }
        val identity = PlayerIdentity(entry.jerseyNumber, entry.playerName)
        val normalizedEntry = entry.copy(jerseyNumber = identity.jerseyNumber, playerName = identity.playerName)
        if (!skipSameNumberWarning) {
            val conflict = state.sameNumberPlayerIdentityConflict(team, identity.jerseyNumber, identity.playerName)
            if (conflict != null) {
                pendingSameNumberConfirmation = PendingSameNumberPlayerCardConfirmation(
                    team = team,
                    cardType = cardType,
                    entry = normalizedEntry,
                    conflict = conflict,
                )
                return true
            }
        }
        val rejection = playerCardAssignmentRejection(state.playerCards(team), identity)
        if (rejection != null) {
            invalidCardAssignmentMessage =
                "${state.teamFor(team).name} ${identity.displayText(compact = true)} ${rejection.messageText}"
            return false
        }

        // No else branch: every CardType value is handled.
        when (cardType) {
            CardType.YELLOW -> {
                presentAssessment(
                    state.assessYellowCard(team, identity.jerseyNumber, now, identity.playerName, entry.reason),
                    PendingMisconductReturn.YellowEntry(team, normalizedEntry),
                )
                return true
            }
            CardType.RED -> {
                presentAssessment(
                    state.assessRedCard(team, identity.jerseyNumber, now, identity.playerName, entry.reason),
                    PendingMisconductReturn.RedEntry(team, normalizedEntry),
                )
                return true
            }
        }
    }

    val noCardSubdialogOpen = pendingYellowTeam == null &&
        pendingRedTeam == null &&
        pendingBlueTeam == null &&
        pendingSameNumberConfirmation == null &&
        pendingMisconductChoice == null &&
        pendingMisconductResolution == null &&
        invalidCardAssignmentMessage == null

    if (noCardSubdialogOpen) {
        CardChoiceDialog(
            state = state,
            team = team,
            onYellow = { pendingYellowTeam = team },
            onRed = { pendingRedTeam = team },
            onBlue = { pendingBlueTeam = team },
            onDismiss = onDismiss,
        )
    }

    if (pendingYellowTeam != null) {
        PlayerCardEntryDialog(
            title = "Yellow card",
            teamName = state.teamFor(pendingYellowTeam!!).name,
            initialEntry = pendingYellowInitialEntry,
            candidates = state.cardedPlayerCandidates(pendingYellowTeam!!),
            cardType = CardType.YELLOW,
            onDismiss = {
                pendingYellowInitialEntry = PlayerCardEntry("")
                pendingYellowTeam = null
            },
            onConfirm = { entry ->
                val team = pendingYellowTeam!!
                if (assessPlayerCardEntry(team, CardType.YELLOW, entry)) {
                    pendingYellowInitialEntry = PlayerCardEntry("")
                    pendingYellowTeam = null
                }
            },
        )
    }

    if (pendingRedTeam != null) {
        PlayerCardEntryDialog(
            title = "Red card",
            teamName = state.teamFor(pendingRedTeam!!).name,
            initialEntry = pendingRedInitialEntry,
            candidates = state.cardedPlayerCandidates(pendingRedTeam!!),
            cardType = CardType.RED,
            onDismiss = {
                pendingRedInitialEntry = PlayerCardEntry("")
                pendingRedTeam = null
            },
            onConfirm = { entry ->
                val team = pendingRedTeam!!
                if (assessPlayerCardEntry(team, CardType.RED, entry)) {
                    pendingRedInitialEntry = PlayerCardEntry("")
                    pendingRedTeam = null
                }
            },
        )
    }

    pendingSameNumberConfirmation?.let { confirmation ->
        AlertDialog(
            onDismissRequest = {
                pendingSameNumberConfirmation = null
                restorePlayerCardEntry(confirmation.cardType, confirmation.team, confirmation.entry)
            },
            title = { Text("Same number, different names") },
            text = {
                Text(
                    "${PlayerIdentity(
                        confirmation.conflict.existingJerseyNumber,
                        confirmation.conflict.existingPlayerName,
                    ).displayText(compact = false)} is already listed. Record ${PlayerIdentity(
                        confirmation.conflict.proposedJerseyNumber,
                        confirmation.conflict.proposedPlayerName,
                    ).displayText(compact = false)} as a different player with the same number?"
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingSameNumberConfirmation = null
                        assessPlayerCardEntry(
                            team = confirmation.team,
                            cardType = confirmation.cardType,
                            entry = confirmation.entry,
                            skipSameNumberWarning = true,
                        )
                    }
                ) {
                    Text("Record")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingSameNumberConfirmation = null
                        restorePlayerCardEntry(confirmation.cardType, confirmation.team, confirmation.entry)
                    }
                ) {
                    Text("Cancel")
                }
            },
        )
    }

    invalidCardAssignmentMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { invalidCardAssignmentMessage = null },
            title = { Text("Invalid card assignment") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { invalidCardAssignmentMessage = null }) {
                    Text("OK")
                }
            },
        )
    }

    pendingBlueTeam?.let { blueTeam ->
        val event = state.previewBlueCard(blueTeam).event
        val misconductPrompt = if (event.needsMisconductChoice()) {
            GamePrompt.LivePointMisconduct(event)
        } else {
            null
        }
        AlertDialog(
            onDismissRequest = { pendingBlueTeam = null },
            title = { Text("Blue Card") },
            text = {
                Text(
                    text = misconductPrompt?.formatMessage() ?: event.formatMessage(),
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            confirmButton = {
                if (misconductPrompt == null) {
                    TextButton(
                        onClick = {
                            val result = state.assessBlueCard(blueTeam, now)
                            onStateOnly(result.state)
                            pendingBlueTeam = null
                        },
                    ) {
                        Text("OK")
                    }
                } else {
                    MisconductChoiceButtons(
                        firstLabel = "Cancel",
                        onFirst = { pendingBlueTeam = null },
                        onOffense = {
                            val result = state.assessBlueCard(blueTeam, now)
                            pendingMisconductResolution = PendingMisconductResolution(
                                choice = PendingMisconductChoice(
                                    result = result,
                                    returnTo = PendingMisconductReturn.BlueCard(blueTeam),
                                ),
                                againstOffense = true,
                            )
                            pendingBlueTeam = null
                        },
                        onDefense = {
                            val result = state.assessBlueCard(blueTeam, now)
                            pendingMisconductResolution = PendingMisconductResolution(
                                choice = PendingMisconductChoice(
                                    result = result,
                                    returnTo = PendingMisconductReturn.BlueCard(blueTeam),
                                ),
                                againstOffense = false,
                            )
                            pendingBlueTeam = null
                        },
                    )
                }
            },
            dismissButton = if (misconductPrompt == null) {
                {
                    TextButton(onClick = { pendingBlueTeam = null }) {
                        Text("Cancel")
                    }
                }
            } else {
                null
            },
        )
    }

    pendingMisconductChoice?.let { pending ->
        val prompt = GamePrompt.LivePointMisconduct(pending.result.event)
        AlertDialog(
            onDismissRequest = {
                restoreMisconductReturn(pending.returnTo)
            },
            title = { Text(prompt.formatTitle()) },
            text = {
                Text(
                    text = prompt.formatMessage(),
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            confirmButton = {
                MisconductChoiceButtons(
                    firstLabel = "Back",
                    firstModifier = Modifier.testTag("misconduct-choice-back"),
                    onFirst = { restoreMisconductReturn(pending.returnTo) },
                    onOffense = {
                        pendingMisconductResolution = PendingMisconductResolution(
                            choice = pending,
                            againstOffense = true,
                        )
                        pendingMisconductChoice = null
                    },
                    onDefense = {
                        pendingMisconductResolution = PendingMisconductResolution(
                            choice = pending,
                            againstOffense = false,
                        )
                        pendingMisconductChoice = null
                    },
                )
            },
        )
    }

    pendingMisconductResolution?.let { pending ->
        val prompt = GamePrompt.LivePointMisconduct(pending.choice.result.event)
        AlertDialog(
            onDismissRequest = {
                restoreMisconductResolutionChoice(pending.choice)
            },
            title = { Text(prompt.formatTitle()) },
            text = {
                Text(
                    text = prompt.resolutionMessage(pending.againstOffense),
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onStateOnly(pending.choice.result.state.withPendingMisconductCountdown())
                        pendingMisconductResolution = null
                    },
                ) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        restoreMisconductResolutionChoice(pending.choice)
                    },
                ) {
                    Text("Back")
                }
            },
        )
    }
}

/**
 * Render the first card dialog, where the observer chooses the assessed card color.
 *
 * @param state The current live game state used for team names and counts.
 * @param team The team receiving the card.
 * @param onYellow Callback starting the yellow-card workflow.
 * @param onRed Callback starting the red-card workflow.
 * @param onBlue Callback recording a blue card.
 * @param onDismiss Callback closing the dialog without recording a card.
 */
@Composable
private fun CardChoiceDialog(
    state: GameState,
    team: TeamId,
    onYellow: () -> Unit,
    onRed: () -> Unit,
    onBlue: () -> Unit,
    onDismiss: () -> Unit,
) {
    val teamState = state.teamFor(team)
    val roleSuffix = state.cardsRoleSuffix(team)
    val yellowCount = state.teamYellowCards(team)
    val redCount = state.teamRedCards(team)
    val blueCount = teamState.blueCards
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Assess a card") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("${teamState.name}$roleSuffix", fontWeight = FontWeight.SemiBold)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    CardChoiceButton(
                        label = "Yellow",
                        color = CardChoiceYellow,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("card-dialog-${team.name}-yellow"),
                        onClick = onYellow,
                    )
                    CardChoiceButton(
                        label = "Red",
                        color = CardChoiceRed,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("card-dialog-${team.name}-red"),
                        onClick = onRed,
                    )
                    CardChoiceButton(
                        label = "Blue",
                        color = CardChoiceBlue,
                        contentColor = Color.White,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("card-dialog-${team.name}-blue"),
                        onClick = onBlue,
                    )
                }
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("Current cards:", style = MaterialTheme.typography.bodyMedium)
                    Column(
                        verticalArrangement = Arrangement.spacedBy(0.dp),
                        modifier = Modifier.padding(start = 18.dp),
                    ) {
                        Text("$yellowCount yellow", style = MaterialTheme.typography.bodyMedium)
                        Text("$redCount red", style = MaterialTheme.typography.bodyMedium)
                        Text("$blueCount blue", style = MaterialTheme.typography.bodyMedium)
                    }
                    Text("Team total: ${state.teamCardTotal(team)}", style = MaterialTheme.typography.bodyMedium)
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        },
    )
}

/**
 * Render one color-coded card choice button.
 *
 * @param label The card color label.
 * @param color The button background color.
 * @param contentColor The text color to use on the button.
 * @param modifier Modifier applied to the button.
 * @param onClick Callback selecting this card color.
 */
@Composable
private fun CardChoiceButton(
    label: String,
    color: Color,
    contentColor: Color = Color.Black,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = contentColor,
        ),
        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    ) {
        Text(label, maxLines = 1, softWrap = false)
    }
}

// Compact +/- row for a single card or tech count.
@Composable
private fun CardCountRow(
    label: String,
    value: Int,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$label $value")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallActionButton(label = "+1", enabled = true, onClick = onIncrement)
            SmallActionButton(label = "-1", enabled = value > 0, onClick = onDecrement)
        }
    }
}

// Pick which player's assigned card should be removed during a correction flow.
@Composable
private fun AssignedCardRemovalDialog(
    title: String,
    teamName: String,
    candidates: List<PlayerCardRemovalCandidate>,
    cardType: CardType,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(teamName, fontWeight = FontWeight.SemiBold)
                candidates.forEach { candidate ->
                    OutlinedButton(
                        onClick = { onConfirm(candidate.jerseyNumber) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "${PlayerIdentity(candidate.jerseyNumber, candidate.playerName).displayText(compact = false)} " +
                                "(${cardType.label} ${candidate.cardCount})"
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

/**
 * Render the player details prompt for live yellow/red card flows.
 *
 * @param title The dialog title describing the card type.
 * @param teamName The team receiving the player card.
 * @param initialEntry Card details to restore when returning from a later dialog step.
 * @param candidates Known carded players for this team.
 * @param cardType The card being assessed.
 * @param onDismiss Callback closing the dialog without recording.
 * @param onConfirm Callback receiving the entered card details.
 */
@Composable
private fun PlayerCardEntryDialog(
    title: String,
    teamName: String,
    initialEntry: PlayerCardEntry,
    candidates: List<PlayerCardCandidate>,
    cardType: CardType,
    onDismiss: () -> Unit,
    onConfirm: (PlayerCardEntry) -> Unit,
) {
    var jerseyNumber by remember(initialEntry) {
        mutableStateOf(initialEntry.jerseyNumber)
    }
    var playerName by remember(initialEntry) { mutableStateOf(initialEntry.playerName) }
    var reason by remember(initialEntry) { mutableStateOf(initialEntry.reason) }
    var showingReasonDialog by remember { mutableStateOf(false) }
    val focusManager = LocalFocusManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(teamName, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = jerseyNumber,
                    onValueChange = { jerseyNumber = it.filter(Char::isDigit) },
                    label = { Text("Player number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(force = true) }),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card-player-number"),
                )
                OutlinedTextField(
                    value = playerName,
                    onValueChange = { playerName = it },
                    label = { Text("Player name") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(force = true) }),
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("card-player-name"),
                )
                if (candidates.isNotEmpty()) {
                    Column(verticalArrangement = Arrangement.spacedBy(0.dp)) {
                        Text("Players with cards:", style = MaterialTheme.typography.labelLarge)
                        candidates.forEach { candidate ->
                            PlayerCardCandidateRow(
                                candidate = candidate,
                                onCopy = {
                                    jerseyNumber = candidate.jerseyNumber
                                    playerName = candidate.playerName
                                },
                            )
                        }
                    }
                }
                Button(
                    onClick = { showingReasonDialog = true },
                    modifier = Modifier.fillMaxWidth(),
                    border = BorderStroke(1.dp, Color.Black),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CardReasonButtonColor,
                        contentColor = Color.Black,
                    ),
                ) {
                    Text(reason.ifBlank { "Reason" })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        PlayerCardEntry(
                            jerseyNumber = jerseyNumber.trim(),
                            playerName = playerName.trim(),
                            reason = reason.trim(),
                        )
                    )
                },
            ) {
                Text("Record")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )

    if (showingReasonDialog) {
        CardReasonDialog(
            cardType = cardType,
            initialReason = reason,
            onDismiss = { showingReasonDialog = false },
            onConfirm = { selectedReason ->
                reason = selectedReason
                showingReasonDialog = false
            },
        )
    }
}

/**
 * Render one known player row with an action to copy its identity into the entry fields.
 *
 * @param candidate The known player to show.
 * @param onCopy Callback copying the candidate identity into the current entry.
 */
@Composable
private fun PlayerCardCandidateRow(candidate: PlayerCardCandidate, onCopy: () -> Unit) {
    val detail = candidate.detail.takeIf { it.isNotBlank() }?.let { " ($it)" }.orEmpty()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(32.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            "${PlayerIdentity(candidate.jerseyNumber, candidate.playerName).displayText(compact = false)}$detail",
            modifier = Modifier.weight(1f),
        )
        TextButton(
            onClick = onCopy,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
        ) {
            Text("Copy")
        }
    }
}

/**
 * Render the optional reason picker for yellow/red cards.
 *
 * @param cardType The card being assessed.
 * @param initialReason Existing reason text to restore.
 * @param onDismiss Callback closing the reason dialog without changing the reason.
 * @param onConfirm Callback receiving the selected reason text.
 */
@Composable
private fun CardReasonDialog(
    cardType: CardType,
    initialReason: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val presets = cardReasonPresets(cardType)
    var selectedPreset by remember(initialReason) {
        mutableStateOf(presets.firstOrNull { it == initialReason }.orEmpty())
    }
    var otherReason by remember(initialReason) {
        mutableStateOf(if (initialReason.isNotBlank() && initialReason !in presets) initialReason else "")
    }
    var details by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("${cardType.label} card reason") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier
                    .heightIn(max = 520.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(2.dp),
                    modifier = Modifier.width(IntrinsicSize.Max),
                ) {
                    presets.forEach { preset ->
                        ReasonChoiceButton(
                            label = preset,
                            selected = selectedPreset == preset,
                            onClick = {
                                selectedPreset = preset
                                if (preset != "Other") {
                                    otherReason = ""
                                }
                            },
                        )
                    }
                    ReasonChoiceButton(
                        label = "Other",
                        selected = selectedPreset == "Other",
                        onClick = { selectedPreset = "Other" },
                    )
                }
                if (selectedPreset == "Other") {
                    OutlinedTextField(
                        value = otherReason,
                        onValueChange = { otherReason = it },
                        label = { Text("Other reason") },
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                OutlinedTextField(
                    value = details,
                    onValueChange = { details = it },
                    label = { Text("More details") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(cardReasonText(selectedPreset, otherReason, details))
                },
            ) {
                Text("Set")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Back")
            }
        },
    )
}

/**
 * Render a preset card-reason selector.
 *
 * @param label The preset reason label.
 * @param selected Whether this preset is currently selected.
 * @param onClick Callback selecting this preset.
 */
@Composable
private fun ReasonChoiceButton(label: String, selected: Boolean, onClick: () -> Unit) {
    if (selected) {
        Button(
            onClick = onClick,
            modifier = Modifier
                .widthIn(min = 0.dp)
                .height(34.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
        ) {
            Text(label)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = Modifier
                .widthIn(min = 0.dp)
                .height(34.dp),
            contentPadding = PaddingValues(horizontal = 10.dp, vertical = 0.dp),
        ) {
            Text(label)
        }
    }
}

/**
 * Render the jersey-number prompt shared by the card flows.
 *
 * @param title The dialog title describing the card type.
 * @param teamName The team receiving the player card.
 * @param onDismiss Callback closing the dialog without recording.
 * @param onConfirm Callback receiving the chosen jersey number.
 */
@Composable
internal fun PlayerNumberDialog(
    title: String,
    teamName: String,
    initialJerseyNumber: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var jerseyNumber by remember(initialJerseyNumber) {
        mutableStateOf(initialJerseyNumber)
    }
    val focusManager = LocalFocusManager.current

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(teamName, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = jerseyNumber,
                    onValueChange = { jerseyNumber = it.filter(Char::isDigit) },
                    label = { Text("Player number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { focusManager.clearFocus(force = true) }),
                    singleLine = true,
                    modifier = Modifier.testTag("card-player-number"),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(jerseyNumber.trim()) }) {
                Text("Record")
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
 * Return the Card-dialog role suffix for a team while between points or at halftime.
 *
 * @param team The team whose pulling/receiving role should be displayed.
 */
private fun GameState.cardsRoleSuffix(team: TeamId): String {
    return if (phase == GamePhase.BETWEEN_POINTS || phase == GamePhase.HALFTIME) {
        if (team == pullingTeam) " (pulling)" else " (receiving)"
    } else {
        ""
    }
}

/**
 * Return known players for quick live-card selection.
 *
 * @param team The team whose known players should be listed.
 */
private fun GameState.cardedPlayerCandidates(team: TeamId): List<PlayerCardCandidate> {
    return playerCards(team).map { player ->
        PlayerCardCandidate(
            jerseyNumber = player.jerseyNumber,
            playerName = player.playerName,
            detail = player.cardDetail(includeGame = true),
        )
    }
}
