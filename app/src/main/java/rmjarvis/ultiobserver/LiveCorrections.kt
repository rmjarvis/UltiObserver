package rmjarvis.ultiobserver

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// Manual score correction dialog.
@Composable
internal fun AdjustScoreDialog(
    state: LiveGameState,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
) {
    var teamOneScore by remember { mutableStateOf(state.teamOne.score) }
    var teamTwoScore by remember { mutableStateOf(state.teamTwo.score) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adjust Score") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallCountEditor(
                    label = state.teamOne.name,
                    value = teamOneScore,
                    onValueChange = { teamOneScore = it.coerceAtLeast(0) },
                )
                SmallCountEditor(
                    label = state.teamTwo.name,
                    value = teamTwoScore,
                    onValueChange = { teamTwoScore = it.coerceAtLeast(0) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(teamOneScore, teamTwoScore) }) {
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

// Manual card/TF correction dialog, including the per-player reconciliation flow.
@Composable
internal fun AdjustCardsDialog(
    state: LiveGameState,
    onDismiss: () -> Unit,
    onConfirm: (LiveGameState) -> Unit,
) {
    var teamOneY by remember { mutableStateOf(state.teamYellowCards(TeamId.TEAM_ONE)) }
    var teamOneB by remember { mutableStateOf(state.teamOne.blueCards) }
    var teamOneR by remember { mutableStateOf(state.teamRedCards(TeamId.TEAM_ONE)) }
    var teamOneTf by remember { mutableStateOf(state.teamOne.technicalFouls) }
    var teamTwoY by remember { mutableStateOf(state.teamYellowCards(TeamId.TEAM_TWO)) }
    var teamTwoB by remember { mutableStateOf(state.teamTwo.blueCards) }
    var teamTwoR by remember { mutableStateOf(state.teamRedCards(TeamId.TEAM_TWO)) }
    var teamTwoTf by remember { mutableStateOf(state.teamTwo.technicalFouls) }
    var workingTeamOnePlayerCards by remember { mutableStateOf(state.teamOnePlayerCards) }
    var workingTeamTwoPlayerCards by remember { mutableStateOf(state.teamTwoPlayerCards) }
    var pendingSteps by remember { mutableStateOf<List<PlayerCardAdjustmentStep>>(emptyList()) }
    var invalidCardAssignmentMessage by remember { mutableStateOf<String?>(null) }

    fun finalizeAdjustment() {
        onConfirm(
            state.adjustCardsAndTf(
                teamOneBlues = teamOneB,
                teamOneTechnicalFouls = teamOneTf,
                teamTwoBlues = teamTwoB,
                teamTwoTechnicalFouls = teamTwoTf,
                teamOnePlayerCards = workingTeamOnePlayerCards,
                teamTwoPlayerCards = workingTeamTwoPlayerCards,
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
            !canAddPlayerCardAssignment(currentRecords, jerseyNumber, step.cardType)
        ) {
            invalidCardAssignmentMessage = "That player already has the maximum valid card combination."
            return
        }
        val updatedRecords = when (step.mode) {
            PlayerCardAdjustmentMode.ADD -> addPlayerCardAssignment(currentRecords, jerseyNumber, step.cardType)
            PlayerCardAdjustmentMode.REMOVE -> removePlayerCardAssignment(currentRecords, jerseyNumber, step.cardType)
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
        title = { Text("Adjust Cards / TF") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                TeamCorrectionSection(state.teamOne.name) {
                    CardCountRow("Yellow", teamOneY, { teamOneY += 1 }, { teamOneY = maxOf(0, teamOneY - 1) })
                    CardCountRow("Blue", teamOneB, { teamOneB += 1 }, { teamOneB = maxOf(0, teamOneB - 1) })
                    CardCountRow("Red", teamOneR, { teamOneR += 1 }, { teamOneR = maxOf(0, teamOneR - 1) })
                    CardCountRow("TF", teamOneTf, { teamOneTf += 1 }, { teamOneTf = maxOf(0, teamOneTf - 1) })
                }
                TeamCorrectionSection(state.teamTwo.name) {
                    CardCountRow("Yellow", teamTwoY, { teamTwoY += 1 }, { teamTwoY = maxOf(0, teamTwoY - 1) })
                    CardCountRow("Blue", teamTwoB, { teamTwoB += 1 }, { teamTwoB = maxOf(0, teamTwoB - 1) })
                    CardCountRow("Red", teamTwoR, { teamTwoR += 1 }, { teamTwoR = maxOf(0, teamTwoR - 1) })
                    CardCountRow("TF", teamTwoTf, { teamTwoTf += 1 }, { teamTwoTf = maxOf(0, teamTwoTf - 1) })
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
                    workingTeamOnePlayerCards = state.teamOnePlayerCards
                    workingTeamTwoPlayerCards = state.teamTwoPlayerCards
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
        when (step.mode) {
            PlayerCardAdjustmentMode.ADD -> {
                PlayerNumberDialog(
                    title = "Add ${step.cardType.label}",
                    teamName = state.teamFor(step.team).name,
                    onDismiss = { pendingSteps = emptyList() },
                    onConfirm = { applyCardAssignment(it) },
                )
            }
            PlayerCardAdjustmentMode.REMOVE -> {
                AssignedCardRemovalDialog(
                    title = "Remove ${step.cardType.label}",
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
                    onConfirm = { applyCardAssignment(it) },
                )
            }
        }
    }

    invalidCardAssignmentMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { invalidCardAssignmentMessage = null },
            title = { Text("Invalid Card Assignment") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { invalidCardAssignmentMessage = null }) {
                    Text("OK")
                }
            },
        )
    }
}

// Compact +/- row for a single card or TF count.
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
                            "${displayPlayerNumber(candidate.jerseyNumber)} " +
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
 * Render the manual offsides/false-start correction dialog.
 *
 * @param state The live state whose current pull-infraction counts seed the dialog.
 * @param onDismiss Callback closing the dialog without changes.
 * @param onConfirm Callback receiving team-one offsides, team-one false starts, team-two offsides, and team-two false starts.
 */
@Composable
internal fun AdjustPullInfractionsDialog(
    state: LiveGameState,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int, Int, Int) -> Unit,
) {
    var teamOneOffsides by remember { mutableStateOf(state.teamOne.offsides) }
    var teamOneFalseStarts by remember { mutableStateOf(state.teamOne.falseStarts) }
    var teamTwoOffsides by remember { mutableStateOf(state.teamTwo.offsides) }
    var teamTwoFalseStarts by remember { mutableStateOf(state.teamTwo.falseStarts) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adjust Pull Infractions") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                TeamCorrectionSection(state.teamOne.name) {
                    SmallCountEditor("Offsides", teamOneOffsides) { teamOneOffsides = it.coerceAtLeast(0) }
                    SmallCountEditor("False Starts", teamOneFalseStarts) { teamOneFalseStarts = it.coerceAtLeast(0) }
                }
                TeamCorrectionSection(state.teamTwo.name) {
                    SmallCountEditor("Offsides", teamTwoOffsides) { teamTwoOffsides = it.coerceAtLeast(0) }
                    SmallCountEditor("False Starts", teamTwoFalseStarts) { teamTwoFalseStarts = it.coerceAtLeast(0) }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(teamOneOffsides, teamOneFalseStarts, teamTwoOffsides, teamTwoFalseStarts) }
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
 * Render a small labeled section used inside correction dialogs.
 *
 * @param title The section title, normally a team name.
 * @param content The correction controls for that section.
 */
@Composable
private fun TeamCorrectionSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        content()
    }
}
