package rmjarvis.ultiobserver

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp

/**
 * Render the manual pull-related correction dialog.
 *
 * @param state The live state whose current counts seed the dialog.
 * @param onDismiss Callback closing the dialog without changes.
 * @param onConfirm Callback receiving team-one offsides, false starts, majority-pull violations,
 * time violations, then the same values for team two.
 */
@Composable
internal fun AdjustPullViolationsDialog(
    state: GameState,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int, Int, Int, Int, Int, Int, Int) -> Unit,
) {
    var teamOneOffsides by remember { mutableStateOf(state.teamOne.offsides) }
    var teamOneFalseStarts by remember { mutableStateOf(state.teamOne.falseStarts) }
    var teamOneMajorityPulls by remember { mutableStateOf(state.teamOne.majorityPullViolations) }
    var teamOneTimeViolations by remember { mutableStateOf(state.teamOne.timeViolations) }
    var teamTwoOffsides by remember { mutableStateOf(state.teamTwo.offsides) }
    var teamTwoFalseStarts by remember { mutableStateOf(state.teamTwo.falseStarts) }
    var teamTwoMajorityPulls by remember { mutableStateOf(state.teamTwo.majorityPullViolations) }
    var teamTwoTimeViolations by remember { mutableStateOf(state.teamTwo.timeViolations) }
    val showMajorityPullRows = state.usesMixedDivision()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adjust pull violations") },
        text = {
            ScrollableDialogRegion(
                modifier = Modifier.testTag("adjust-pull-violations-content"),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                TeamCorrectionSection(state.teamOne.name) {
                    CorrectionCountRow(
                        label = "Offsides",
                        value = teamOneOffsides,
                        onIncrement = { teamOneOffsides += 1 },
                        onDecrement = { teamOneOffsides = maxOf(0, teamOneOffsides - 1) },
                    )
                    CorrectionCountRow(
                        label = "False starts",
                        value = teamOneFalseStarts,
                        onIncrement = { teamOneFalseStarts += 1 },
                        onDecrement = { teamOneFalseStarts = maxOf(0, teamOneFalseStarts - 1) },
                    )
                    if (showMajorityPullRows) {
                        CorrectionCountRow(
                            label = "Majority pull",
                            value = teamOneMajorityPulls,
                            onIncrement = { teamOneMajorityPulls += 1 },
                            onDecrement = {
                                teamOneMajorityPulls = maxOf(0, teamOneMajorityPulls - 1)
                            },
                        )
                    }
                    CorrectionCountRow(
                        label = "Time violations",
                        value = teamOneTimeViolations,
                        onIncrement = { teamOneTimeViolations += 1 },
                        onDecrement = {
                            teamOneTimeViolations = maxOf(0, teamOneTimeViolations - 1)
                        },
                    )
                }
                TeamCorrectionSection(state.teamTwo.name) {
                    CorrectionCountRow(
                        label = "Offsides",
                        value = teamTwoOffsides,
                        onIncrement = { teamTwoOffsides += 1 },
                        onDecrement = { teamTwoOffsides = maxOf(0, teamTwoOffsides - 1) },
                    )
                    CorrectionCountRow(
                        label = "False starts",
                        value = teamTwoFalseStarts,
                        onIncrement = { teamTwoFalseStarts += 1 },
                        onDecrement = { teamTwoFalseStarts = maxOf(0, teamTwoFalseStarts - 1) },
                    )
                    if (showMajorityPullRows) {
                        CorrectionCountRow(
                            label = "Majority pull",
                            value = teamTwoMajorityPulls,
                            onIncrement = { teamTwoMajorityPulls += 1 },
                            onDecrement = {
                                teamTwoMajorityPulls = maxOf(0, teamTwoMajorityPulls - 1)
                            },
                        )
                    }
                    CorrectionCountRow(
                        label = "Time violations",
                        value = teamTwoTimeViolations,
                        onIncrement = { teamTwoTimeViolations += 1 },
                        onDecrement = {
                            teamTwoTimeViolations = maxOf(0, teamTwoTimeViolations - 1)
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
                        teamOneOffsides,
                        teamOneFalseStarts,
                        teamOneMajorityPulls,
                        teamOneTimeViolations,
                        teamTwoOffsides,
                        teamTwoFalseStarts,
                        teamTwoMajorityPulls,
                        teamTwoTimeViolations,
                    )
                },
                tag = "adjust-pull-violations-confirm",
            )
        },
        dismissButton = {
            TextActionButton(label = "Cancel", onClick = onDismiss)
        },
    )
}
