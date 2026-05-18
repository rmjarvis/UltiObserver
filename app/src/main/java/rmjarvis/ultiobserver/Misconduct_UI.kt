package rmjarvis.ultiobserver

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

private data class PendingUnknownYellowChoice(
    val team: TeamId,
)

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

/**
 * Render the bottom sheet for recording cards and technical fouls for either team.
 *
 * @param state The current live game state used for team names, card summaries, and assessments.
 * @param onAssessment Callback receiving the model assessment after the observer records a card or technical foul.
 */
@Composable
internal fun CardsSheet(
    state: LiveGameState,
    onAssessment: (CardAssessmentResult) -> Unit,
) {
    var pendingYellowTeam by remember { mutableStateOf<TeamId?>(null) }
    var pendingRedTeam by remember { mutableStateOf<TeamId?>(null) }
    var pendingUnknownYellowChoice by remember { mutableStateOf<PendingUnknownYellowChoice?>(null) }
    var invalidCardAssignmentMessage by remember { mutableStateOf<String?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Cards / Technical Fouls", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        TeamActionSection(
            label = "${state.teamOne.name}${state.cardsRoleSuffix(TeamId.TEAM_ONE)}",
            issuedCards = state.playerCards(TeamId.TEAM_ONE),
            onYellow = { pendingYellowTeam = TeamId.TEAM_ONE },
            onRed = { pendingRedTeam = TeamId.TEAM_ONE },
            onBlue = { onAssessment(state.assessBlueCard(TeamId.TEAM_ONE)) },
            onTech = { onAssessment(state.assessTechnicalFoul(TeamId.TEAM_ONE)) },
        )
        TeamActionSection(
            label = "${state.teamTwo.name}${state.cardsRoleSuffix(TeamId.TEAM_TWO)}",
            issuedCards = state.playerCards(TeamId.TEAM_TWO),
            onYellow = { pendingYellowTeam = TeamId.TEAM_TWO },
            onRed = { pendingRedTeam = TeamId.TEAM_TWO },
            onBlue = { onAssessment(state.assessBlueCard(TeamId.TEAM_TWO)) },
            onTech = { onAssessment(state.assessTechnicalFoul(TeamId.TEAM_TWO)) },
        )
        Spacer(modifier = Modifier.height(24.dp))
    }

    if (pendingYellowTeam != null) {
        PlayerNumberDialog(
            title = "Yellow Card",
            teamName = state.teamFor(pendingYellowTeam!!).name,
            onDismiss = { pendingYellowTeam = null },
            onConfirm = { jerseyNumber ->
                // Yellow on N/A needs a follow-up question if an unknown player already has one.
                if (
                    jerseyNumber == UNKNOWN_PLAYER_NUMBER &&
                    state.playerHasYellowThisGame(pendingYellowTeam!!, UNKNOWN_PLAYER_NUMBER)
                ) {
                    pendingUnknownYellowChoice = PendingUnknownYellowChoice(pendingYellowTeam!!)
                } else {
                    onAssessment(state.assessYellowCard(pendingYellowTeam!!, jerseyNumber))
                }
                pendingYellowTeam = null
            },
        )
    }

    if (pendingRedTeam != null) {
        PlayerNumberDialog(
            title = "Red Card",
            teamName = state.teamFor(pendingRedTeam!!).name,
            onDismiss = { pendingRedTeam = null },
            onConfirm = { jerseyNumber ->
                if (canAddPlayerCardAssignment(state.playerCards(pendingRedTeam!!), jerseyNumber, CardType.RED)) {
                    onAssessment(state.assessRedCard(pendingRedTeam!!, jerseyNumber, RedCardMode.RED))
                } else {
                    invalidCardAssignmentMessage =
                        "${state.teamFor(pendingRedTeam!!).name} #$jerseyNumber already has the maximum valid card combination."
                }
                pendingRedTeam = null
            },
        )
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

    if (pendingUnknownYellowChoice != null) {
        UnknownYellowDialog(
            teamName = state.teamFor(pendingUnknownYellowChoice!!.team).name,
            onDismiss = { pendingUnknownYellowChoice = null },
            onSamePlayer = {
                onAssessment(
                    state.assessRedCard(
                        pendingUnknownYellowChoice!!.team,
                        UNKNOWN_PLAYER_NUMBER,
                        RedCardMode.SECOND_YELLOW,
                    )
                )
                pendingUnknownYellowChoice = null
            },
            onDifferentPlayer = {
                onAssessment(
                    state.assessStandaloneYellowCard(
                        pendingUnknownYellowChoice!!.team,
                        UNKNOWN_PLAYER_NUMBER,
                    )
                )
                pendingUnknownYellowChoice = null
            },
        )
    }
}

/**
 * Render Card/TF actions and current-game issued-card summary for one team.
 *
 * @param label The team label, including pull/receive role when applicable.
 * @param issuedCards The team's current-game player-card records.
 * @param onYellow Callback starting the yellow-card flow.
 * @param onRed Callback starting the red-card flow.
 * @param onBlue Callback recording a blue card.
 * @param onTech Callback recording a technical foul.
 */
@Composable
private fun TeamActionSection(
    label: String,
    issuedCards: List<InGamePlayerCardRecord>,
    onYellow: () -> Unit,
    onRed: () -> Unit,
    onBlue: () -> Unit,
    onTech: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, fontWeight = FontWeight.SemiBold)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallActionButton(label = "Yellow", modifier = Modifier.weight(1f), onClick = onYellow)
            SmallActionButton(label = "Red", modifier = Modifier.weight(1f), onClick = onRed)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallActionButton(label = "Blue", modifier = Modifier.weight(1f), onClick = onBlue)
            SmallActionButton(label = "Tech", modifier = Modifier.weight(1f), onClick = onTech)
        }
        if (issuedCards.isNotEmpty()) {
            Text("This game", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.labelLarge)
            issuedCards.forEach { record ->
                Text(
                    text = record.issuedCardSummary(),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
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
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var jerseyNumber by remember { mutableStateOf("") }

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
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
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
 * @param onDifferentPlayer Callback treating the yellow as a standalone yellow for another unknown player.
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
        title = { Text("Unknown Player Number") },
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

/// Return a compact live-game summary for one player's current-game cards.
private fun InGamePlayerCardRecord.issuedCardSummary(): String {
    val parts = buildList {
        if (yellows > 0) {
            add("Y $yellows")
        }
        if (reds > 0) {
            add("R $reds")
        }
    }
    return "${displayPlayerNumber(jerseyNumber)}: ${parts.joinToString("  ")}"
}

/**
 * Return the Cards / TF role suffix for a team while between points or at halftime.
 *
 * @param team The team whose pulling/receiving role should be displayed.
 */
private fun LiveGameState.cardsRoleSuffix(team: TeamId): String {
    return if (phase == LivePhase.BETWEEN_POINTS || phase == LivePhase.HALFTIME) {
        if (team == pullingTeam) " (pulling)" else " (receiving)"
    } else {
        ""
    }
}
