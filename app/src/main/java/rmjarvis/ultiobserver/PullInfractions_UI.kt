package rmjarvis.ultiobserver

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.unit.dp

/**
 * Render the manual offsides/false-start correction dialog.
 *
 * @param state The live state whose current pull-infraction counts seed the dialog.
 * @param onDismiss Callback closing the dialog without changes.
 * @param onConfirm Callback receiving team-one offsides, team-one false starts, team-two offsides, and team-two false starts.
 */
@Composable
internal fun AdjustPullInfractionsDialog(
    state: GameState,
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
