package rmjarvis.ultiobserver

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
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
 * Archived-game row in the archived games list.
 *
 * @param startDateTime Compact start date/time text.
 * @param scoreLine Final score text.
 */
internal data class ArchivedGameListEntry(
    val startDateTime: String,
    val scoreLine: String,
)

/// Return the archived-games row summary with compact start time above the final score.
internal fun GameState.archivedGameListEntry(): ArchivedGameListEntry {
    return ArchivedGameListEntry(
        startDateTime = formatCompactStartDateTime(startDate, startTime),
        scoreLine = "${teamOne.name} ${teamOne.score} - ${teamTwo.score} ${teamTwo.name}",
    )
}

/**
 * Render the archived game list, separated from Home so the launch screen has more room.
 *
 * @param archivedGames The archived game rows to display.
 * @param onOpenArchivedGame Callback opening an archived game by index.
 * @param onDeleteArchivedGame Callback deleting an archived game by index.
 * @param onDeleteAllArchivedGames Callback deleting every archived game after confirmation.
 * @param onBackHome Callback returning to Home.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ArchivedGamesScreen(
    archivedGames: List<ArchivedGameListEntry>,
    onOpenArchivedGame: (Int) -> Unit,
    onDeleteArchivedGame: (Int) -> Unit,
    onDeleteAllArchivedGames: () -> Unit,
    onBackHome: () -> Unit,
) {
    var pendingDeleteIndex by remember { mutableStateOf<Int?>(null) }
    var pendingDeleteAll by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Archived Games") },
                navigationIcon = {
                    TextButton(onClick = onBackHome) {
                        Text("Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
                .testTag("archived-games-screen"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (archivedGames.isEmpty()) {
                Text("No completed games yet.")
            } else {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                ) {
                    TextButton(
                        onClick = { pendingDeleteAll = true },
                        modifier = Modifier.testTag("delete-all-archived-games"),
                    ) {
                        Text("Delete All")
                    }
                }
                archivedGames.forEachIndexed { index, game ->
                    ArchivedGameRow(
                        entry = game,
                        onClick = { onOpenArchivedGame(index) },
                        onDelete = { pendingDeleteIndex = index },
                    )
                }
            }
        }
    }

    val deleteIndex = pendingDeleteIndex
    if (deleteIndex != null) {
        DeleteGameDialog(
            onDismiss = { pendingDeleteIndex = null },
            onConfirmDelete = {
                pendingDeleteIndex = null
                onDeleteArchivedGame(deleteIndex)
            },
        )
    }
    if (pendingDeleteAll) {
        DeleteGameDialog(
            onDismiss = { pendingDeleteAll = false },
            onConfirmDelete = {
                pendingDeleteAll = false
                onDeleteAllArchivedGames()
            },
            title = "Delete All Games?",
            message = "Completely delete all archived game data? This cannot be undone.",
        )
    }
}

/**
 * Render an archived game row with a separate right-side delete action.
 *
 * @param entry The archived game list entry to display.
 * @param onClick Callback opening this archived game.
 * @param onDelete Callback requesting deletion of this archived game.
 */
@Composable
private fun ArchivedGameRow(
    entry: ArchivedGameListEntry,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GameListRow(
            startDateTime = entry.startDateTime,
            scoreLine = entry.scoreLine,
            onClick = onClick,
            modifier = Modifier
                .weight(1f)
                .testTag("archived-game-${entry.scoreLine}"),
        )
        IconButton(
            onClick = onDelete,
            modifier = Modifier.testTag("delete-archived-game-${entry.scoreLine}"),
        ) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "Delete ${entry.scoreLine}",
            )
        }
    }
}

/**
 * Render irreversible delete confirmation using the same drag interaction as live-screen unlock.
 *
 * @param onDismiss Callback closing the dialog without deleting.
 * @param onConfirmDelete Callback invoked after the confirmation slide completes.
 * @param title The dialog title.
 * @param message The warning body text.
 */
@Composable
internal fun DeleteGameDialog(
    onDismiss: () -> Unit,
    onConfirmDelete: () -> Unit,
    title: String = "Delete Game?",
    message: String = "Completely delete the data for this game? This cannot be undone.",
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = message,
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
