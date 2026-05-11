package rmjarvis.ultiobserver

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// Bottom sheet for manual corrections and less-common game actions.
@Composable
internal fun OtherSheet(
    state: LiveGameState,
    now: Long,
    onUpdateGameSetup: () -> Unit,
    onDeleteGame: () -> Unit,
    onAction: (LiveGameState) -> Unit,
) {
    var showAdjustScoreDialog by remember { mutableStateOf(false) }
    var showAdjustTimeoutsDialog by remember { mutableStateOf(false) }
    var showAdjustCardsDialog by remember { mutableStateOf(false) }
    var showAdjustPullInfractionsDialog by remember { mutableStateOf(false) }
    var showDeleteGameDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Other", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OtherMenuButton(
                    label = "Update Game Setup",
                    onClick = onUpdateGameSetup,
                )
                OtherMenuButton(
                    label = "Adjust Score",
                    onClick = { showAdjustScoreDialog = true },
                )
                OtherMenuButton(
                    label = "Adjust Timeouts",
                    onClick = { showAdjustTimeoutsDialog = true },
                )
                OtherMenuButton(
                    label = "Adjust Cards / TF",
                    onClick = { showAdjustCardsDialog = true },
                )
                OtherMenuButton(
                    label = "Adjust Pull Infractions",
                    onClick = { showAdjustPullInfractionsDialog = true },
                )
                OtherMenuButton(
                    label = "Swap Ends of Field",
                    onClick = { onAction(state.swapFieldEnds()) },
                )
                OtherMenuButton(
                    label = "Swap Pulling Team",
                    onClick = { onAction(state.swapPullingTeam()) },
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!state.halftimeTaken && state.phase == LivePhase.BETWEEN_POINTS) {
                    OtherMenuButton(
                        label = "Start Halftime",
                        onClick = { onAction(state.startHalftimeNow(now)) },
                    )
                }
                if (state.phase != LivePhase.GAME_OVER) {
                    OtherMenuButton(
                        label = "End Game",
                        onClick = { onAction(state.endGameNow(now)) },
                    )
                }
                if (!state.halftimeTaken && !state.halfCapApplied) {
                    OtherMenuButton(
                        label = "Apply Half Cap Now",
                        onClick = { onAction(state.makeCapNow(CapType.HALF, now)) },
                    )
                }
                if (!state.softCapApplied) {
                    OtherMenuButton(
                        label = "Apply Soft Cap Now",
                        onClick = { onAction(state.makeCapNow(CapType.SOFT, now)) },
                    )
                }
                if (!state.hardCapApplied && state.phase != LivePhase.GAME_OVER) {
                    OtherMenuButton(
                        label = "Apply Hard Cap Now",
                        onClick = { onAction(state.makeCapNow(CapType.HARD, now)) },
                    )
                }
                OtherMenuButton(
                    label = "Delete Game",
                    onClick = { showDeleteGameDialog = true },
                )
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }

    if (showAdjustScoreDialog) {
        AdjustScoreDialog(
            state = state,
            onDismiss = { showAdjustScoreDialog = false },
            onConfirm = { teamOneScore, teamTwoScore ->
                onAction(state.adjustScore(teamOneScore, teamTwoScore))
                showAdjustScoreDialog = false
            },
        )
    }

    if (showAdjustTimeoutsDialog) {
        AdjustTimeoutsDialog(
            state = state,
            onDismiss = { showAdjustTimeoutsDialog = false },
            onConfirm = { teamOneTimeoutsUsed, teamTwoTimeoutsUsed ->
                onAction(state.adjustTimeouts(teamOneTimeoutsUsed, teamTwoTimeoutsUsed))
                showAdjustTimeoutsDialog = false
            },
        )
    }

    if (showAdjustCardsDialog) {
        AdjustCardsDialog(
            state = state,
            onDismiss = { showAdjustCardsDialog = false },
            onConfirm = { updatedState ->
                onAction(updatedState)
                showAdjustCardsDialog = false
            },
        )
    }

    if (showAdjustPullInfractionsDialog) {
        AdjustPullInfractionsDialog(
            state = state,
            onDismiss = { showAdjustPullInfractionsDialog = false },
            onConfirm = { teamOneOffsides, teamOneFalseStarts, teamTwoOffsides, teamTwoFalseStarts ->
                onAction(
                    state.adjustPullInfractions(
                        teamOneOffsides,
                        teamOneFalseStarts,
                        teamTwoOffsides,
                        teamTwoFalseStarts,
                    )
                )
                showAdjustPullInfractionsDialog = false
            },
        )
    }

    if (showDeleteGameDialog) {
        DeleteGameDialog(
            onDismiss = { showDeleteGameDialog = false },
            onConfirmDelete = {
                showDeleteGameDialog = false
                onDeleteGame()
            },
        )
    }
}

// Simple menu button that fills the width of its column in the Other sheet.
@Composable
private fun OtherMenuButton(
    label: String,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label)
    }
}
