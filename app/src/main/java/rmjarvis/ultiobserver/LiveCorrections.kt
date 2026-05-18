package rmjarvis.ultiobserver

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// Manual score correction dialog.
@Composable
internal fun AdjustScoreDialog(
    state: LiveGameState,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
) {
    var teamOneScore by remember { mutableStateOf(state.teamOne.score) }
    var teamTwoScore by remember { mutableStateOf(state.teamTwo.score) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adjust Score") },
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
 * Render a small labeled section used inside correction dialogs.
 *
 * @param title The section title, normally a team name.
 * @param content The correction controls for that section.
 */
@Composable
internal fun TeamCorrectionSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        content()
    }
}
