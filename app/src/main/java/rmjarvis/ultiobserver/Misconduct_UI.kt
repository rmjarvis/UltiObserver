package rmjarvis.ultiobserver

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
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
private val CardChoiceBlue = Color(0xFF64B5F6)

/**
 * Team awaiting an unknown-player yellow-card disambiguation.
 *
 * @param team The team receiving the yellow card.
 */
private data class PendingUnknownYellowChoice(
    val team: TeamId,
)

/// Previous Card-dialog step to restore when dismissing a live-point misconduct choice.
private sealed interface PendingMisconductReturn {
    data class YellowNumber(val team: TeamId, val jerseyNumber: String) : PendingMisconductReturn
    data class RedNumber(val team: TeamId, val jerseyNumber: String) : PendingMisconductReturn
    data class UnknownYellow(val team: TeamId) : PendingMisconductReturn
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
        val currentRecords = if (step.team == TeamId.TEAM_ONE) {
            workingTeamOnePlayerCards
        } else {
            workingTeamTwoPlayerCards
        }
        if (
            step.mode == PlayerCardAdjustmentMode.ADD &&
            !canAddPlayerCardAssignment(currentRecords, jerseyNumber = jerseyNumber, cardType = step.cardType)
        ) {
            invalidCardAssignmentMessage = "That player already has the maximum valid card combination."
            return
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
    var pendingYellowInitialNumber by remember { mutableStateOf("") }
    var pendingRedTeam by remember { mutableStateOf<TeamId?>(null) }
    var pendingRedInitialNumber by remember { mutableStateOf("") }
    var pendingBlueTeam by remember { mutableStateOf<TeamId?>(null) }
    var pendingUnknownYellowChoice by remember { mutableStateOf<PendingUnknownYellowChoice?>(null) }
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
            is PendingMisconductReturn.YellowNumber -> {
                pendingYellowInitialNumber = returnTo.jerseyNumber
                pendingYellowTeam = returnTo.team
            }
            is PendingMisconductReturn.RedNumber -> {
                pendingRedInitialNumber = returnTo.jerseyNumber
                pendingRedTeam = returnTo.team
            }
            is PendingMisconductReturn.UnknownYellow -> {
                pendingUnknownYellowChoice = PendingUnknownYellowChoice(returnTo.team)
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

    val noCardSubdialogOpen = pendingYellowTeam == null &&
        pendingRedTeam == null &&
        pendingBlueTeam == null &&
        pendingUnknownYellowChoice == null &&
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
        PlayerNumberDialog(
            title = "Yellow card",
            teamName = state.teamFor(pendingYellowTeam!!).name,
            initialJerseyNumber = pendingYellowInitialNumber,
            onDismiss = {
                pendingYellowInitialNumber = ""
                pendingYellowTeam = null
            },
            onConfirm = { jerseyNumber ->
                val team = pendingYellowTeam!!
                // Yellow on N/A needs a follow-up question if an unknown player already has one.
                if (
                    jerseyNumber == UNKNOWN_PLAYER_NUMBER &&
                    state.playerHasYellowThisGame(team, UNKNOWN_PLAYER_NUMBER)
                ) {
                    pendingUnknownYellowChoice = PendingUnknownYellowChoice(team)
                } else {
                    presentAssessment(
                        state.assessYellowCard(team, jerseyNumber, now),
                        PendingMisconductReturn.YellowNumber(team, jerseyNumber),
                    )
                }
                pendingYellowInitialNumber = ""
                pendingYellowTeam = null
            },
        )
    }

    if (pendingRedTeam != null) {
        PlayerNumberDialog(
            title = "Red card",
            teamName = state.teamFor(pendingRedTeam!!).name,
            initialJerseyNumber = pendingRedInitialNumber,
            onDismiss = {
                pendingRedInitialNumber = ""
                pendingRedTeam = null
            },
            onConfirm = { jerseyNumber ->
                val team = pendingRedTeam!!
                if (canAddPlayerCardAssignment(state.playerCards(team), jerseyNumber = jerseyNumber, cardType = CardType.RED)) {
                    presentAssessment(
                        state.assessRedCard(team, jerseyNumber, now),
                        PendingMisconductReturn.RedNumber(team, jerseyNumber),
                    )
                } else {
                    invalidCardAssignmentMessage =
                        "${state.teamFor(team).name} #$jerseyNumber already has the maximum valid card combination."
                }
                pendingRedInitialNumber = ""
                pendingRedTeam = null
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

    if (pendingUnknownYellowChoice != null) {
        UnknownYellowDialog(
            teamName = state.teamFor(pendingUnknownYellowChoice!!.team).name,
            onDismiss = { pendingUnknownYellowChoice = null },
            onSamePlayer = {
                val team = pendingUnknownYellowChoice!!.team
                presentAssessment(
                    state.assessSecondYellowCard(team, UNKNOWN_PLAYER_NUMBER, now),
                    PendingMisconductReturn.UnknownYellow(team),
                )
                pendingUnknownYellowChoice = null
            },
            onDifferentPlayer = {
                val team = pendingUnknownYellowChoice!!.team
                presentAssessment(
                    state.assessFirstYellowCard(
                        team,
                        UNKNOWN_PLAYER_NUMBER,
                        now,
                    ),
                    PendingMisconductReturn.UnknownYellow(team),
                )
                pendingUnknownYellowChoice = null
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
        title = { Text("Card") },
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
                        modifier = Modifier
                            .weight(1f)
                            .testTag("card-dialog-${team.name}-blue"),
                        onClick = onBlue,
                    )
                }
                Text(
                    "Current cards: $yellowCount yellow, $redCount red, $blueCount blue. " +
                        "Team total: ${state.teamCardTotal(team)}.",
                    style = MaterialTheme.typography.bodyMedium,
                )
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
 * @param modifier Modifier applied to the button.
 * @param onClick Callback selecting this card color.
 */
@Composable
private fun CardChoiceButton(
    label: String,
    color: Color,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Button(
        onClick = onClick,
        modifier = modifier,
        colors = ButtonDefaults.buttonColors(
            containerColor = color,
            contentColor = Color.Black,
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
 * Render the jersey-number prompt shared by the card flows.
 *
 * @param title The dialog title describing the card type.
 * @param teamName The team receiving the player card.
 * @param onDismiss Callback closing the dialog without recording.
 * @param onConfirm Callback receiving the entered jersey number, or the unknown-player sentinel for blank/N/A.
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
        mutableStateOf(initialJerseyNumber.takeUnless { it == UNKNOWN_PLAYER_NUMBER } ?: "")
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
            TextButton(onClick = { onConfirm(jerseyNumber.ifBlank { UNKNOWN_PLAYER_NUMBER }) }) {
                Text("Record")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onConfirm(UNKNOWN_PLAYER_NUMBER) }) {
                    Text("N/A")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        },
    )
}

/**
 * Resolve whether a second yellow on N/A is the same unknown player as before.
 *
 * @param teamName The team receiving the unknown-player yellow.
 * @param onDismiss Callback closing the dialog without recording.
 * @param onSamePlayer Callback treating the yellow as a second yellow for the existing unknown player.
 * @param onDifferentPlayer Callback treating the yellow as a first yellow for another unknown player.
 */
@Composable
private fun UnknownYellowDialog(
    teamName: String,
    onDismiss: () -> Unit,
    onSamePlayer: () -> Unit,
    onDifferentPlayer: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Unknown player number") },
        text = {
            Text("$teamName already has a yellow assigned to N/A. Is this the same player?")
        },
        confirmButton = {
            TextButton(onClick = onSamePlayer) {
                Text("Yes")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDifferentPlayer) {
                    Text("No")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
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
