package rmjarvis.ultiobserver

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
    val showMajorityPullRows = state.usesMajorityPullRule()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adjust pull violations") },
        text = {
            Column(
                modifier = Modifier
                    .testTag("adjust-pull-violations-content")
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                TeamCorrectionSection(state.teamOne.name) {
                    SmallCountEditor("Offsides", teamOneOffsides) { teamOneOffsides = it.coerceAtLeast(0) }
                    SmallCountEditor("False starts", teamOneFalseStarts) { teamOneFalseStarts = it.coerceAtLeast(0) }
                    if (showMajorityPullRows) {
                        SmallCountEditor("Majority pull", teamOneMajorityPulls) {
                            teamOneMajorityPulls = it.coerceAtLeast(0)
                        }
                    }
                    SmallCountEditor("Time violations", teamOneTimeViolations) { teamOneTimeViolations = it.coerceAtLeast(0) }
                }
                TeamCorrectionSection(state.teamTwo.name) {
                    SmallCountEditor("Offsides", teamTwoOffsides) { teamTwoOffsides = it.coerceAtLeast(0) }
                    SmallCountEditor("False starts", teamTwoFalseStarts) { teamTwoFalseStarts = it.coerceAtLeast(0) }
                    if (showMajorityPullRows) {
                        SmallCountEditor("Majority pull", teamTwoMajorityPulls) {
                            teamTwoMajorityPulls = it.coerceAtLeast(0)
                        }
                    }
                    SmallCountEditor("Time violations", teamTwoTimeViolations) { teamTwoTimeViolations = it.coerceAtLeast(0) }
                }
            }
        },
        confirmButton = {
            TextButton(
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
                modifier = Modifier.testTag("adjust-pull-violations-confirm"),
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
