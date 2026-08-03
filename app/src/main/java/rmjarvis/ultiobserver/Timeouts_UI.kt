package rmjarvis.ultiobserver

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
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

    ResponsiveAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adjust timeouts") },
        text = {
            ScrollableDialogRegion(
                maxHeight = dialogBodyMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("Adjust the number of timeouts used by each team this half.")
                Text(
                    "${state.teamOne.name} is allowed to use ${teamOneAllowed.timeoutCountText()}",
                )
                Text(
                    "${state.teamTwo.name} is allowed to use ${teamTwoAllowed.timeoutCountText()}",
                )
                CorrectionCountRow(
                    label = state.teamOne.name,
                    value = teamOneTimeoutsUsed,
                    emphasizedLabel = true,
                    incrementTag = "timeout-current-team-one-increment",
                    decrementTag = "timeout-current-team-one-decrement",
                    onIncrement = { teamOneTimeoutsUsed += 1 },
                    onDecrement = { teamOneTimeoutsUsed = maxOf(0, teamOneTimeoutsUsed - 1) },
                )
                CorrectionCountRow(
                    label = state.teamTwo.name,
                    value = teamTwoTimeoutsUsed,
                    emphasizedLabel = true,
                    incrementTag = "timeout-current-team-two-increment",
                    decrementTag = "timeout-current-team-two-decrement",
                    onIncrement = { teamTwoTimeoutsUsed += 1 },
                    onDecrement = { teamTwoTimeoutsUsed = maxOf(0, teamTwoTimeoutsUsed - 1) },
                )
                if (state.halftimeTaken) {
                    Text("Adjust the number of timeouts used by each team in the first half.")
                    CorrectionCountRow(
                        label = state.teamOne.name,
                        value = teamOneFirstHalfTimeoutsUsed,
                        emphasizedLabel = true,
                        incrementTag = "timeout-first-half-team-one-increment",
                        decrementTag = "timeout-first-half-team-one-decrement",
                        onIncrement = { teamOneFirstHalfTimeoutsUsed += 1 },
                        onDecrement = {
                            teamOneFirstHalfTimeoutsUsed =
                                maxOf(0, teamOneFirstHalfTimeoutsUsed - 1)
                        },
                    )
                    CorrectionCountRow(
                        label = state.teamTwo.name,
                        value = teamTwoFirstHalfTimeoutsUsed,
                        emphasizedLabel = true,
                        incrementTag = "timeout-first-half-team-two-increment",
                        decrementTag = "timeout-first-half-team-two-decrement",
                        onIncrement = { teamTwoFirstHalfTimeoutsUsed += 1 },
                        onDecrement = {
                            teamTwoFirstHalfTimeoutsUsed =
                                maxOf(0, teamTwoFirstHalfTimeoutsUsed - 1)
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextActionButton(
                label = "Set",
                onClick = {
                    onConfirm(
                        teamOneTimeoutsUsed,
                        teamTwoTimeoutsUsed,
                        teamOneFirstHalfTimeoutsUsed,
                        teamTwoFirstHalfTimeoutsUsed,
                    )
                },
            )
        },
        dismissButton = {
            TextActionButton(label = "Cancel", onClick = onDismiss)
        },
    )
}

/// Return timeout count text with the correct singular/plural noun.
private fun Int.timeoutCountText(): String {
    return if (this == 1) "$this timeout" else "$this timeouts"
}
