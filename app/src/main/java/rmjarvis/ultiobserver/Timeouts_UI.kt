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
 * Render the manual timeout correction dialog.
 *
 * @param state The live state whose current timeout counts seed the dialog.
 * @param onDismiss Callback closing the dialog without changes.
 * @param onConfirm Callback receiving team-one and team-two timeout counts used in the current half.
 */
@Composable
internal fun AdjustTimeoutsDialog(
    state: GameState,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
) {
    val teamOneAllowed = state.timeoutsAllowedThisHalf(TeamId.TEAM_ONE)
    val teamTwoAllowed = state.timeoutsAllowedThisHalf(TeamId.TEAM_TWO)
    var teamOneTimeoutsUsed by remember { mutableStateOf(state.teamOne.timeoutsUsedThisHalf) }
    var teamTwoTimeoutsUsed by remember { mutableStateOf(state.teamTwo.timeoutsUsedThisHalf) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adjust timeouts") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallCountEditor(
                    label = "${state.teamOne.name} used (allowed $teamOneAllowed)",
                    value = teamOneTimeoutsUsed,
                    onValueChange = { teamOneTimeoutsUsed = it },
                )
                SmallCountEditor(
                    label = "${state.teamTwo.name} used (allowed $teamTwoAllowed)",
                    value = teamTwoTimeoutsUsed,
                    onValueChange = { teamTwoTimeoutsUsed = it },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(teamOneTimeoutsUsed, teamTwoTimeoutsUsed) }) {
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
