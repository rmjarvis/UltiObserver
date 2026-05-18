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
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
 * Render the archived game list, separated from Home so the launch screen has more room.
 *
 * @param previousGames The archived game rows to display.
 * @param onOpenPreviousGame Callback opening an archived game by index.
 * @param onDeletePreviousGame Callback deleting an archived game by index.
 * @param onDeleteAllPreviousGames Callback deleting every archived game after confirmation.
 * @param onBackHome Callback returning to Home.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun PreviousGamesScreen(
    previousGames: List<ArchivedGameListEntry>,
    onOpenPreviousGame: (Int) -> Unit,
    onDeletePreviousGame: (Int) -> Unit,
    onDeleteAllPreviousGames: () -> Unit,
    onBackHome: () -> Unit,
) {
    var pendingDeleteIndex by remember { mutableStateOf<Int?>(null) }
    var pendingDeleteAll by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Previous Games") },
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
                .testTag("previous-games-screen"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (previousGames.isEmpty()) {
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
                previousGames.forEachIndexed { index, game ->
                    ArchivedGameRow(
                        entry = game,
                        onClick = { onOpenPreviousGame(index) },
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
                onDeletePreviousGame(deleteIndex)
            },
        )
    }
    if (pendingDeleteAll) {
        DeleteGameDialog(
            onDismiss = { pendingDeleteAll = false },
            onConfirmDelete = {
                pendingDeleteAll = false
                onDeleteAllPreviousGames()
            },
            title = "Delete All Games?",
            message = "Completely delete all previous game data? This cannot be undone.",
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
