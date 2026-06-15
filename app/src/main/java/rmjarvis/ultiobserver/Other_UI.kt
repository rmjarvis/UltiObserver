package rmjarvis.ultiobserver

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
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

/**
 * Render the bottom sheet for manual corrections and less-common game actions.
 *
 * @param state The current live game state.
 * @param now The current epoch millis for actions that depend on clock time.
 * @param onUpdateGameSetup Callback reopening setup for the current game.
 * @param onShowEventLog Callback opening the current game's event log.
 * @param onDeleteGame Callback deleting the current game after confirmation.
 * @param onAction Callback receiving an updated live game state after a model action.
 */
@Composable
internal fun OtherSheet(
    state: GameState,
    now: Long,
    onUpdateGameSetup: () -> Unit,
    onShowEventLog: () -> Unit,
    onDeleteGame: () -> Unit,
    onAction: (GameState) -> Unit,
) {
    var showAdjustScoreDialog by remember { mutableStateOf(false) }
    var showAdjustTimeoutsDialog by remember { mutableStateOf(false) }
    var showAdjustCardsDialog by remember { mutableStateOf(false) }
    var showAdjustPullInfractionsDialog by remember { mutableStateOf(false) }
    var showDeleteGameDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
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
                    label = "Update game setup",
                    onClick = onUpdateGameSetup,
                )
                OtherMenuButton(
                    label = "Adjust score",
                    onClick = { showAdjustScoreDialog = true },
                )
                OtherMenuButton(
                    label = "Adjust timeouts",
                    onClick = { showAdjustTimeoutsDialog = true },
                )
                OtherMenuButton(
                    label = "Adjust cards / TF",
                    onClick = { showAdjustCardsDialog = true },
                )
                OtherMenuButton(
                    label = "Adjust pull infractions",
                    onClick = { showAdjustPullInfractionsDialog = true },
                )
                OtherMenuButton(
                    label = "Swap ends of field",
                    onClick = {
                        onAction(state.swapFieldEnds())
                    },
                )
                OtherMenuButton(
                    label = "Swap pulling team",
                    onClick = {
                        onAction(state.swapPullingTeam())
                    },
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OtherMenuButton(
                    label = "Event log",
                    onClick = onShowEventLog,
                )
                if (!state.halftimeTaken && state.phase == GamePhase.BETWEEN_POINTS) {
                    OtherMenuButton(
                        label = "Start halftime",
                        onClick = {
                            onAction(state.startHalftimeNow(now))
                        },
                    )
                }
                OtherMenuButton(
                    label = "End game",
                    onClick = {
                        onAction(state.endGameNow(now))
                    },
                )
                if (!state.halftimeTaken && !state.halfCapApplied) {
                    OtherMenuButton(
                        label = "Apply half cap now",
                        onClick = {
                            onAction(state.makeCapNow(CapType.HALF, now))
                        },
                    )
                }
                if (!state.softCapApplied) {
                    OtherMenuButton(
                        label = "Apply soft cap now",
                        onClick = {
                            onAction(state.makeCapNow(CapType.SOFT, now))
                        },
                    )
                }
                if (!state.hardCapApplied) {
                    OtherMenuButton(
                        label = "Apply hard cap now",
                        onClick = {
                            onAction(state.makeCapNow(CapType.HARD, now))
                        },
                    )
                }
                OtherMenuButton(
                    label = "Delete game",
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
                onAction(state.adjustScore(teamOneScore, teamTwoScore, now))
                showAdjustScoreDialog = false
            },
        )
    }

    if (showAdjustTimeoutsDialog) {
        AdjustTimeoutsDialog(
            state = state,
            onDismiss = { showAdjustTimeoutsDialog = false },
            onConfirm = { teamOneTimeoutsUsed, teamTwoTimeoutsUsed ->
                onAction(state.adjustTimeouts(teamOneTimeoutsUsed, teamTwoTimeoutsUsed, now))
                showAdjustTimeoutsDialog = false
            },
        )
    }

    if (showAdjustCardsDialog) {
        AdjustCardsDialog(
            state = state,
            now = now,
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
                        now,
                    )
                )
                showAdjustPullInfractionsDialog = false
            },
        )
    }

    if (showDeleteGameDialog) {
        // DeleteGameDialog lives in ArchivedGames_UI.kt because game deletion is mostly archived-game UI.
        DeleteGameDialog(
            onDismiss = { showDeleteGameDialog = false },
            onConfirmDelete = {
                showDeleteGameDialog = false
                onDeleteGame()
            },
        )
    }
}

/**
 * Render the manual score correction dialog from the Other sheet.
 *
 * @param state The current live game state whose score is being edited.
 * @param onDismiss Callback closing the dialog without changing the score.
 * @param onConfirm Callback receiving the corrected team-one and team-two scores.
 */
@Composable
private fun AdjustScoreDialog(
    state: GameState,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
) {
    var teamOneScore by remember { mutableStateOf(state.teamOne.score) }
    var teamTwoScore by remember { mutableStateOf(state.teamTwo.score) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adjust score") },
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

/**
 * Render a simple menu button that fills the width of its column in the Other sheet.
 *
 * @param label The button label.
 * @param onClick Callback invoked when the button is tapped.
 */
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
