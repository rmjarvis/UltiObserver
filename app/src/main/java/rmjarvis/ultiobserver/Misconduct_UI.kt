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

/**
 * Team awaiting an unknown-player yellow-card disambiguation.
 *
 * @param team The team receiving the yellow card.
 */
private data class PendingUnknownYellowChoice(
    val team: TeamId,
)

/// Previous Cards / TF step to restore when dismissing a live-point misconduct choice.
private sealed interface PendingMisconductReturn {
    data class YellowNumber(val team: TeamId, val jerseyNumber: String) : PendingMisconductReturn
    data class RedNumber(val team: TeamId, val jerseyNumber: String) : PendingMisconductReturn
    data class UnknownYellow(val team: TeamId) : PendingMisconductReturn
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

// Manual card/TF correction dialog, including the per-player reconciliation flow.
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
        // No else branch: every PlayerCardAdjustmentMode value is handled
        when (step.mode) {
            PlayerCardAdjustmentMode.ADD -> {
                PlayerNumberDialog(
                    title = "Add ${step.cardType.label}",
                    teamName = state.teamFor(step.team).name,
                    onDismiss = { pendingSteps = emptyList() },
                    onConfirm = { jerseyNumber ->
                        applyCardAssignment(jerseyNumber)
                    },
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
 * @param now The current epoch millis for event logging.
 * @param onAssessment Callback receiving the completed state plus popup text after a card or technical foul.
 */
@Composable
internal fun CardsSheet(
    state: GameState,
    now: Long,
    onAssessment: (GameState, String, String) -> Unit,
) {
    var pendingYellowTeam by remember { mutableStateOf<TeamId?>(null) }
    var pendingYellowInitialNumber by remember { mutableStateOf("") }
    var pendingRedTeam by remember { mutableStateOf<TeamId?>(null) }
    var pendingRedInitialNumber by remember { mutableStateOf("") }
    var pendingUnknownYellowChoice by remember { mutableStateOf<PendingUnknownYellowChoice?>(null) }
    var pendingMisconductChoice by remember { mutableStateOf<PendingMisconductChoice?>(null) }
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
        }
    }

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
            onBlue = {
                presentAssessment(state.assessBlueCard(TeamId.TEAM_ONE, now), returnTo = null)
            },
            onTech = {
                presentAssessment(state.assessTechnicalFoul(TeamId.TEAM_ONE, now), returnTo = null)
            },
        )
        TeamActionSection(
            label = "${state.teamTwo.name}${state.cardsRoleSuffix(TeamId.TEAM_TWO)}",
            issuedCards = state.playerCards(TeamId.TEAM_TWO),
            onYellow = { pendingYellowTeam = TeamId.TEAM_TWO },
            onRed = { pendingRedTeam = TeamId.TEAM_TWO },
            onBlue = {
                presentAssessment(state.assessBlueCard(TeamId.TEAM_TWO, now), returnTo = null)
            },
            onTech = {
                presentAssessment(state.assessTechnicalFoul(TeamId.TEAM_TWO, now), returnTo = null)
            },
        )
        Spacer(modifier = Modifier.height(24.dp))
    }

    if (pendingYellowTeam != null) {
        PlayerNumberDialog(
            title = "Yellow Card",
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
            title = "Red Card",
            teamName = state.teamFor(pendingRedTeam!!).name,
            initialJerseyNumber = pendingRedInitialNumber,
            onDismiss = {
                pendingRedInitialNumber = ""
                pendingRedTeam = null
            },
            onConfirm = { jerseyNumber ->
                val team = pendingRedTeam!!
                if (canAddPlayerCardAssignment(state.playerCards(team), jerseyNumber, CardType.RED)) {
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
            onDismissRequest = { restoreMisconductReturn(pending.returnTo) },
            title = { Text(prompt.formatTitle()) },
            text = {
                Text(
                    text = prompt.formatMessage(),
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        onAssessment(
                            pending.result.state.withPendingMisconductCountdown(),
                            prompt.resolutionMessage(againstOffense = true),
                            prompt.formatTitle(),
                        )
                        pendingMisconductChoice = null
                    }
                ) {
                    Text("Offense")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        onAssessment(
                            pending.result.state.withPendingMisconductCountdown(),
                            prompt.resolutionMessage(againstOffense = false),
                            prompt.formatTitle(),
                        )
                        pendingMisconductChoice = null
                    }
                ) {
                    Text("Defense")
                }
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
    initialJerseyNumber: String = "",
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var jerseyNumber by remember(initialJerseyNumber) {
        mutableStateOf(initialJerseyNumber.takeUnless { it == UNKNOWN_PLAYER_NUMBER } ?: "")
    }

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
private fun GameState.cardsRoleSuffix(team: TeamId): String {
    return if (phase == LivePhase.BETWEEN_POINTS || phase == LivePhase.HALFTIME) {
        if (team == pullingTeam) " (pulling)" else " (receiving)"
    } else {
        ""
    }
}
