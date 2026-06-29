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
import androidx.compose.material3.Button
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
 * @param summaryLine Matchup or score summary text.
 */
internal data class ArchivedGameListEntry(
    val startDateTime: String,
    val summaryLine: String,
)

/// Return the archived-games row summary with compact start time above the summary line.
internal fun GameState.archivedGameListEntry(): ArchivedGameListEntry {
    return ArchivedGameListEntry(
        startDateTime = formatCompactStartDateTime(startDate, startTime),
        summaryLine = gameListSummaryLine(),
    )
}

/**
 * Render the archived/saved games area
 *
 * @param categoryCounts Number of rows in each archive category.
 * @param selectedCategory Category currently listed, or null on the category landing page.
 * @param archivedGames The archived game rows to display for the selected category.
 * @param onOpenCategory Callback opening one category from the landing page.
 * @param onOpenArchivedGame Callback opening an archived game by index.
 * @param onDeleteArchivedGame Callback deleting an archived game by index.
 * @param onDeleteAllArchivedGames Callback deleting every archived/saved game.
 * @param onDeleteAllInSelectedCategory Callback deleting every game in the selected category.
 * @param onBackHome Callback returning to Home.
 * @param onBackCategories Callback returning from a category list to the category landing page.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ArchivedGamesScreen(
    categoryCounts: Map<ArchivedGameCategory, Int>,
    selectedCategory: ArchivedGameCategory?,
    archivedGames: List<ArchivedGameListEntry>?,
    onOpenCategory: (ArchivedGameCategory) -> Unit,
    onOpenArchivedGame: (Int) -> Unit,
    onDeleteArchivedGame: (Int) -> Unit,
    onDeleteAllArchivedGames: () -> Unit,
    onDeleteAllInSelectedCategory: () -> Unit,
    onBackHome: () -> Unit,
    onBackCategories: () -> Unit,
) {
    var pendingDeleteIndex by remember { mutableStateOf<Int?>(null) }
    var pendingDeleteAll by remember { mutableStateOf(false) }
    val category = selectedCategory
    val hasAnyArchivedGames = categoryCounts.values.any { it > 0 }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(category?.displayText ?: "Archived/saved games") },
                navigationIcon = {
                    TextButton(onClick = if (category == null) onBackHome else onBackCategories) {
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
            if (category == null) {
                if (hasAnyArchivedGames) {
                    DeleteAllButton(onClick = { pendingDeleteAll = true })
                }
                ArchivedGameCategory.entries.forEach { archiveCategory ->
                    ArchiveCategoryButton(
                        category = archiveCategory,
                        count = categoryCounts.getValue(archiveCategory),
                        onOpenCategory = onOpenCategory,
                    )
                }
            } else {
                val listedGames = archivedGames!!
                if (listedGames.isEmpty()) {
                    Text(category.emptyText)
                } else {
                    DeleteAllButton(onClick = { pendingDeleteAll = true })
                    listedGames.forEachIndexed { index, game ->
                        ArchivedGameRow(
                            displayedIndex = index,
                            entry = game,
                            onClick = { onOpenArchivedGame(index) },
                            onDelete = { pendingDeleteIndex = index },
                        )
                    }
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
                if (category == null) {
                    onDeleteAllArchivedGames()
                } else {
                    onDeleteAllInSelectedCategory()
                }
            },
            title = "Delete all games?",
            message = if (category == null) {
                "Completely delete all archived and saved games? This cannot be undone."
            } else {
                "Completely delete all ${category.displayText.lowercase()}? This cannot be undone."
            },
        )
    }
}

/**
 * Render the bulk delete action for the archive landing or category page.
 *
 * @param onClick Callback requesting delete confirmation.
 */
@Composable
private fun DeleteAllButton(onClick: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
    ) {
        TextButton(
            onClick = onClick,
            modifier = Modifier.testTag("delete-all-archived-games"),
        ) {
            Text("Delete all")
        }
    }
}

/**
 * Render one category choice on the archived/saved games landing page.
 *
 * @param category The archive category represented by this button.
 * @param count Number of rows currently in the category.
 * @param onOpenCategory Callback opening this category.
 */
@Composable
private fun ArchiveCategoryButton(
    category: ArchivedGameCategory,
    count: Int,
    onOpenCategory: (ArchivedGameCategory) -> Unit,
) {
    Button(
        onClick = { onOpenCategory(category) },
        modifier = Modifier
            .fillMaxWidth()
            .testTag("archive-category-${category.name}"),
    ) {
        Text("${category.displayText} ($count)")
    }
}

/**
 * Render an archived game row with a separate right-side delete action.
 *
 * @param displayedIndex The row index in the currently visible archive category.
 * @param entry The archived game list entry to display.
 * @param onClick Callback opening this archived game.
 * @param onDelete Callback requesting deletion of this archived game.
 */
@Composable
private fun ArchivedGameRow(
    displayedIndex: Int,
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
            summaryLine = entry.summaryLine,
            onClick = onClick,
            modifier = Modifier
                .weight(1f)
                .testTag("archived-game-$displayedIndex"),
        )
        IconButton(
            onClick = onDelete,
            modifier = Modifier.testTag("delete-archived-game-$displayedIndex"),
        ) {
            Icon(
                imageVector = Icons.Filled.Delete,
                contentDescription = "Delete ${entry.summaryLine}",
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
    title: String = "Delete game?",
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
                    trackText = "Confirm delete",
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
