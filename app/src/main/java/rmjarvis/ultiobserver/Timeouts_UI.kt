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
 * @param onConfirm Callback receiving team-one and team-two timeout counts used in the current
 * half, followed by team-one and team-two timeout counts used in the first half.
 */
@Composable
internal fun AdjustTimeoutsDialog(
    state: GameState,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int, Int, Int) -> Unit,
) {
    var teamOneTimeoutsUsed by remember { mutableStateOf(state.teamOne.timeoutsUsedThisHalf) }
    var teamTwoTimeoutsUsed by remember { mutableStateOf(state.teamTwo.timeoutsUsedThisHalf) }
    var teamOneFirstHalfTimeoutsUsed by remember {
        mutableStateOf(state.teamOne.firstHalfTimeoutsUsed)
    }
    var teamTwoFirstHalfTimeoutsUsed by remember {
        mutableStateOf(state.teamTwo.firstHalfTimeoutsUsed)
    }
    val teamOneAllowed = state.rules.timeoutsAllowedThisHalf(
        halftimeTaken = state.halftimeTaken,
        firstHalfTimeoutsUsed = teamOneFirstHalfTimeoutsUsed,
    )
    val teamTwoAllowed = state.rules.timeoutsAllowedThisHalf(
        halftimeTaken = state.halftimeTaken,
        firstHalfTimeoutsUsed = teamTwoFirstHalfTimeoutsUsed,
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adjust timeouts") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("Adjust the number of timeouts used by each team this half.")
                Text(
                    "${state.teamOne.name} is allowed to use ${teamOneAllowed.timeoutCountText()}",
                )
                Text(
                    "${state.teamTwo.name} is allowed to use ${teamTwoAllowed.timeoutCountText()}",
                )
                SmallCountEditor(
                    label = "${state.teamOne.name}",
                    value = teamOneTimeoutsUsed,
                    onValueChange = { teamOneTimeoutsUsed = it },
                )
                SmallCountEditor(
                    label = "${state.teamTwo.name}",
                    value = teamTwoTimeoutsUsed,
                    onValueChange = { teamTwoTimeoutsUsed = it },
                )
                if (state.halftimeTaken) {
                    Text("Adjust the number of timeouts used by each team in the first half.")
                    SmallCountEditor(
                        label = "${state.teamOne.name}",
                        value = teamOneFirstHalfTimeoutsUsed,
                        onValueChange = { teamOneFirstHalfTimeoutsUsed = it },
                    )
                    SmallCountEditor(
                        label = "${state.teamTwo.name}",
                        value = teamTwoFirstHalfTimeoutsUsed,
                        onValueChange = { teamTwoFirstHalfTimeoutsUsed = it },
                    )
                }

            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    onConfirm(
                        teamOneTimeoutsUsed,
                        teamTwoTimeoutsUsed,
                        teamOneFirstHalfTimeoutsUsed,
                        teamTwoFirstHalfTimeoutsUsed,
                    )
                }) {
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

/// Return timeout count text with the correct singular/plural noun.
private fun Int.timeoutCountText(): String {
    return if (this == 1) "$this timeout" else "$this timeouts"
}
