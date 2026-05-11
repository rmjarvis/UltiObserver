package rmjarvis.ultiobserver

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// Irreversible delete confirmation using the same drag interaction as live-screen unlock.
@Composable
internal fun DeleteGameDialog(
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Delete Game?") },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = "Completely delete the data for this game? This cannot be undone.",
                    style = MaterialTheme.typography.bodyLarge,
                )
                SlideToConfirmControl(
                    instructionText = "Slide right to confirm delete",
                    trackText = "Confirm Delete",
                    testTag = "confirm-delete-slider",
                    onConfirmed = onConfirmDelete,
                    modifier = Modifier.padding(top = 20.dp),
                )
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}
