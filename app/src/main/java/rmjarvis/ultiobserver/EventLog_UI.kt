package rmjarvis.ultiobserver

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp

/**
 * Render a scrollable event-log dialog.
 *
 * @param state The game whose persisted event log should be shown.
 * @param onDismiss Callback closing the dialog.
 */
@Composable
internal fun EventLogDialog(state: GameState, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Event log") },
        text = {
            EventLogDialogContent(state = state)
        },
        confirmButton = {
            TextActionButton(label = "OK", onClick = onDismiss)
        },
    )
}

/**
 * Render compact event-log rows for a dialog body.
 *
 * @param state The game whose persisted event log should be shown.
 */
@Composable
private fun EventLogDialogContent(state: GameState) {
    val rows = state.formatEventLogLines()
    ScrollableDialogRegion(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (rows.isEmpty()) {
            Text("No events logged yet.", style = MaterialTheme.typography.bodyMedium)
        } else {
            rows.forEach { row ->
                Text(
                    text = row,
                    style = MaterialTheme.typography.bodyMedium,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}
