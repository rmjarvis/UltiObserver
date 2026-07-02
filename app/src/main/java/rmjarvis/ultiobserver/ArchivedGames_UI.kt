package rmjarvis.ultiobserver

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import java.time.LocalDate

/**
 * Render the archived/saved games area
 *
 * @param categoryCounts Number of rows in each archive category.
 * @param hasSavedOrArchivedGames Whether stored archive rows exist for bulk deletion.
 * @param selectedCategory Category currently selected, or null on the category landing page.
 * @param archiveFilterSelections Selected filters applied to the archive list.
 * @param archiveSortMode Sort order applied to the archive list.
 * @param filteredArchiveState Archive rows and filter values for the selected category.
 * @param currentInProgressGame The current game to list in the in-progress category, if any.
 * @param currentSetupDraft The current setup draft to list in the setup category, if any.
 * @param onOpenCategory Callback opening one category from the landing page.
 * @param onUpdateArchiveFilterSelections Callback replacing one checkbox filter.
 * @param onUpdateArchiveDateFilter Callback replacing the date filter.
 * @param onClearArchiveFilter Callback clearing one archive filter.
 * @param onClearArchiveFilterSelections Callback clearing all archive filters.
 * @param onUpdateArchiveSortMode Callback replacing the archive sort mode.
 * @param onOpenCurrentGame Callback opening the current game summary.
 * @param onOpenCurrentSetup Callback opening the current setup draft.
 * @param onOpenArchivedGame Callback opening an archived game by index.
 * @param onDeleteArchivedGame Callback deleting an archived game by index.
 * @param onDeleteAllArchivedGames Callback deleting every archived/saved game.
 * @param onDeleteSelectedArchivedGames Callback deleting selected archive rows.
 * @param onDeleteAllInSelectedCategory Callback deleting every game in the selected category.
 * @param onBackHome Callback returning to Home.
 * @param onBackCategories Callback returning from a category list to the category landing page.
 * @param onHome Callback returning directly to Home.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ArchivedGamesScreen(
    categoryCounts: Map<ArchivedGameCategory, Int>,
    hasSavedOrArchivedGames: Boolean,
    selectedCategory: ArchivedGameCategory?,
    archiveFilterSelections: ArchiveFilterSelections,
    archiveSortMode: ArchiveSortMode,
    filteredArchiveState: FilteredArchiveState,
    currentInProgressGame: GameListEntry?,
    currentSetupDraft: GameListEntry?,
    onOpenCategory: (ArchivedGameCategory) -> Unit,
    onUpdateArchiveFilterSelections: (ArchiveFilterField, Set<String>) -> Unit,
    onUpdateArchiveDateFilter: (ArchiveDateFilter?) -> Unit,
    onClearArchiveFilter: (ArchiveFilterField) -> Unit,
    onClearArchiveFilterSelections: () -> Unit,
    onUpdateArchiveSortMode: (ArchiveSortMode) -> Unit,
    onOpenCurrentGame: () -> Unit,
    onOpenCurrentSetup: () -> Unit,
    onOpenArchivedGame: (Int) -> Unit,
    onDeleteArchivedGame: (Int) -> Unit,
    onDeleteAllArchivedGames: () -> Unit,
    onDeleteSelectedArchivedGames: (Set<Int>) -> Unit,
    onDeleteAllInSelectedCategory: () -> Unit,
    onBackHome: () -> Unit,
    onBackCategories: () -> Unit,
    onHome: () -> Unit,
) {
    var pendingDeleteIndex by remember { mutableStateOf<Int?>(null) }
    var pendingDeleteAll by remember { mutableStateOf(false) }
    var pendingDeleteSelectedArchiveIndices by remember { mutableStateOf<Set<Int>?>(null) }
    var showFilterDialog by remember { mutableStateOf(false) }
    var showSortDialog by remember { mutableStateOf(false) }
    val category = selectedCategory
    val showArchiveControls = category == ArchivedGameCategory.COMPLETED &&
        (categoryCounts.getValue(ArchivedGameCategory.COMPLETED) > 0 || archiveFilterSelections.isActive())

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(category?.displayText ?: "Archived/saved games") },
                navigationIcon = {
                    TopBarBackButton(
                        onClick = if (category == null) onBackHome else onBackCategories,
                    )
                },
                actions = {
                    TopBarHomeButton(onClick = onHome)
                },
            )
        },
    ) { innerPadding ->
        val screenModifier = Modifier
            .fillMaxSize()
            .padding(innerPadding)
            .padding(20.dp)
            .testTag("archived-games-screen")
        if (category == ArchivedGameCategory.COMPLETED) {
            val selectedGames = filteredArchiveState.selectedGames!!
            ArchiveListWithControls(
                modifier = screenModifier,
                selectedGames = selectedGames,
                archiveFilterSelections = archiveFilterSelections,
                filterAndSortSummaryText = filteredArchiveState.filterAndSortSummaryText,
                showControls = showArchiveControls,
                showDeleteAll = selectedGames.isNotEmpty(),
                onOpenFilter = { showFilterDialog = true },
                onOpenSort = { showSortDialog = true },
                onDeleteAll = {
                    pendingDeleteSelectedArchiveIndices = selectedGames
                        .map { it.index }
                        .toSet()
                    pendingDeleteAll = true
                },
                onOpenArchivedGame = onOpenArchivedGame,
                onDeleteArchivedGame = { pendingDeleteIndex = it },
            )
        } else {
            Column(
                modifier = screenModifier
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                if (category == null) {
                    if (hasSavedOrArchivedGames) {
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
                    val selectedGames = filteredArchiveState.selectedGames!!
                    if (category == ArchivedGameCategory.IN_PROGRESS) {
                        InProgressGamesList(
                            currentGame = currentInProgressGame,
                            savedGames = selectedGames,
                            onOpenCurrentGame = onOpenCurrentGame,
                            onOpenArchivedGame = onOpenArchivedGame,
                            onDeleteSavedGame = { pendingDeleteIndex = it },
                            onDeleteAllSavedGames = { pendingDeleteAll = true },
                        )
                    } else {
                        SetupStatesList(
                            currentSetup = currentSetupDraft,
                            savedSetups = selectedGames,
                            onOpenCurrentSetup = onOpenCurrentSetup,
                            onOpenSavedSetup = onOpenArchivedGame,
                            onDeleteSavedSetup = { pendingDeleteIndex = it },
                            onDeleteAllSavedSetups = { pendingDeleteAll = true },
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
            onDismiss = {
                pendingDeleteAll = false
                pendingDeleteSelectedArchiveIndices = null
            },
            onConfirmDelete = {
                pendingDeleteAll = false
                val selectedArchiveIndices = pendingDeleteSelectedArchiveIndices
                pendingDeleteSelectedArchiveIndices = null
                if (category == null) {
                    onDeleteAllArchivedGames()
                } else if (category == ArchivedGameCategory.COMPLETED) {
                    onDeleteSelectedArchivedGames(selectedArchiveIndices!!)
                } else {
                    onDeleteAllInSelectedCategory()
                }
            },
            title = "Delete all games?",
            message = if (category == null) {
                "Completely delete all archived and saved games? This cannot be undone."
            } else if (category == ArchivedGameCategory.IN_PROGRESS) {
                "Completely delete all saved games? This cannot be undone."
            } else if (category == ArchivedGameCategory.SETUP) {
                "Completely delete all saved setup states? This cannot be undone."
            } else if (archiveFilterSelections.isActive()) {
                "Delete all the currently displayed archived games? This cannot be undone."
            } else {
                "Completely delete all archived games? This cannot be undone."
            },
        )
    }
    if (showFilterDialog) {
        ArchiveFilterDialog(
            filterSelections = archiveFilterSelections,
            availableValues = filteredArchiveState.availableFilterValues,
            onUpdateValues = onUpdateArchiveFilterSelections,
            onUpdateDateFilter = onUpdateArchiveDateFilter,
            onClearFilter = onClearArchiveFilter,
            onClearAll = onClearArchiveFilterSelections,
            onDismiss = { showFilterDialog = false },
        )
    }
    if (showSortDialog) {
        ArchiveSortDialog(
            sortMode = archiveSortMode,
            onSortModeSelected = onUpdateArchiveSortMode,
            onDismiss = { showSortDialog = false },
        )
    }
}

/**
 * Render an archive category with fixed controls above scrolling rows.
 *
 * @param modifier Modifier for the archive screen body.
 * @param selectedGames Archive rows after filtering and sorting.
 * @param archiveFilterSelections Active archive filter selections.
 * @param filterAndSortSummaryText User-facing filter/sort summary text.
 * @param showControls Whether the filter/sort/delete controls should be displayed.
 * @param showDeleteAll Whether the bulk delete action should be displayed.
 * @param onOpenFilter Callback opening the filter dialog.
 * @param onOpenSort Callback opening the sort dialog.
 * @param onDeleteAll Callback requesting deletion of all selected archive rows.
 * @param onOpenArchivedGame Callback opening one archived game.
 * @param onDeleteArchivedGame Callback deleting one archived game.
 */
@Composable
private fun ArchiveListWithControls(
    modifier: Modifier,
    selectedGames: List<ArchivedGameListItem>,
    archiveFilterSelections: ArchiveFilterSelections,
    filterAndSortSummaryText: String,
    showControls: Boolean,
    showDeleteAll: Boolean,
    onOpenFilter: () -> Unit,
    onOpenSort: () -> Unit,
    onDeleteAll: () -> Unit,
    onOpenArchivedGame: (Int) -> Unit,
    onDeleteArchivedGame: (Int) -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        ArchiveListControls(
            visible = showControls,
            filterAndSortSummaryText = filterAndSortSummaryText,
            showDeleteAll = showDeleteAll,
            onOpenFilter = onOpenFilter,
            onOpenSort = onOpenSort,
            onDeleteAll = onDeleteAll,
        )
        Column(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .testTag("completed-archive-list"),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (selectedGames.isEmpty()) {
                Text(
                    if (archiveFilterSelections.isActive()) {
                        "No archived games match these filters."
                    } else {
                        ArchivedGameCategory.COMPLETED.emptyText
                    }
                )
            } else {
                selectedGames.forEachIndexed { displayedIndex, game ->
                    ArchivedGameRow(
                        displayedIndex = displayedIndex,
                        entry = game.entry,
                        rowTagPrefix = "archived-game",
                        deleteTagPrefix = "delete-archived-game",
                        onClick = {
                            onOpenArchivedGame(game.index)
                        },
                        onDelete = {
                            onDeleteArchivedGame(game.index)
                        },
                    )
                }
            }
        }
    }
}

/**
 * Render controls above the archive list.
 *
 * @param visible Whether this row should be displayed.
 * @param filterAndSortSummaryText User-facing filter/sort summary text.
 * @param showDeleteAll Whether the bulk delete action should be displayed.
 * @param onOpenFilter Callback opening the filter dialog.
 * @param onOpenSort Callback opening the sort dialog.
 * @param onDeleteAll Callback requesting deletion of all selected archive rows.
 */
@Composable
private fun ArchiveListControls(
    visible: Boolean,
    filterAndSortSummaryText: String,
    showDeleteAll: Boolean,
    onOpenFilter: () -> Unit,
    onOpenSort: () -> Unit,
    onDeleteAll: () -> Unit,
) {
    if (!visible) {
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        BigActionButton(
            label = "Filter",
            fullWidth = false,
            containerColor = SecondaryColor,
            contentColor = OnSecondaryColor,
            borderColor = null,
            tag = "archive-filter-button",
            onClick = onOpenFilter,
        )
        BigActionButton(
            label = "Sort",
            fullWidth = false,
            containerColor = SecondaryColor,
            contentColor = OnSecondaryColor,
            borderColor = null,
            tag = "archive-sort-button",
            onClick = onOpenSort,
        )
        Spacer(modifier = Modifier.weight(1f))
        if (showDeleteAll) {
            TextActionButton(
                label = "Delete all",
                tag = "delete-all-archived-games",
                onClick = onDeleteAll,
            )
        }
    }
    ArchiveFilterAndSortSummary(text = filterAndSortSummaryText)
}

/**
 * Render model-provided archive filter and sort summary text.
 *
 * @param text Summary text to display.
 */
@Composable
private fun ArchiveFilterAndSortSummary(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.testTag("archive-filter-and-sort-summary"),
    )
}

/**
 * Render the archive filter dialog.
 *
 * @param filterSelections Active filter selections.
 * @param availableValues Available checkbox values for each filter field.
 * @param onUpdateValues Callback replacing one checkbox filter.
 * @param onUpdateDateFilter Callback replacing the date filter.
 * @param onClearFilter Callback clearing one filter field.
 * @param onClearAll Callback clearing all filters.
 * @param onDismiss Callback closing the dialog.
 */
@Composable
private fun ArchiveFilterDialog(
    filterSelections: ArchiveFilterSelections,
    availableValues: Map<ArchiveFilterField, List<ArchiveFilterValueOption>>,
    onUpdateValues: (ArchiveFilterField, Set<String>) -> Unit,
    onUpdateDateFilter: (ArchiveDateFilter?) -> Unit,
    onClearFilter: (ArchiveFilterField) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    var selectedField by remember { mutableStateOf<ArchiveFilterField?>(null) }
    val field = selectedField
    if (field == null) {
        ArchiveFilterFieldListDialog(
            filterSelections = filterSelections,
            onOpenField = { selectedField = it },
            onClearAll = onClearAll,
            onDismiss = onDismiss,
        )
    } else if (field == ArchiveFilterField.DATE) {
        ArchiveDateFilterDialog(
            dateFilter = filterSelections.dateRange,
            onUpdateDateFilter = onUpdateDateFilter,
            onBack = { selectedField = null },
        )
    } else {
        ArchiveValueFilterDialog(
            field = field,
            selectedValues = filterSelections.valuesFor(field),
            availableValues = availableValues.getValue(field),
            onUpdateValues = { values ->
                onUpdateValues(field, values)
            },
            onClear = {
                onClearFilter(field)
            },
            onBack = { selectedField = null },
        )
    }
}

/**
 * Render the top-level filter field chooser.
 *
 * @param filterSelections Active filter selections.
 * @param onOpenField Callback opening one filter field.
 * @param onClearAll Callback clearing all filters.
 * @param onDismiss Callback closing the dialog.
 */
@Composable
private fun ArchiveFilterFieldListDialog(
    filterSelections: ArchiveFilterSelections,
    onOpenField: (ArchiveFilterField) -> Unit,
    onClearAll: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Filter games by:") },
        text = {
            ScrollableDialogRegion(
                maxHeight = 420.dp,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ArchiveFilterField.entries.forEach { field ->
                    val active = field.isActive(filterSelections)
                    MenuButton(
                        label = field.filterDialogLabel(filterSelections),
                        colors = neutralOutlinedButtonColors(
                            containerColor = choiceContainerColor(active),
                            contentColor = choiceContentColor(active),
                        ),
                        borderColor = choiceBorderColor(active),
                        tag = "archive-filter-field-${field.name}",
                        onClick = { onOpenField(field) },
                    )
                }
            }
        },
        confirmButton = {
            TextActionButton(label = "Done", onClick = onDismiss)
        },
        dismissButton = {
            TextActionButton(
                label = "Clear filters",
                enabled = filterSelections.isActive(),
                tag = "archive-clear-filters",
                onClick = onClearAll,
            )
        },
    )
}

private fun ArchiveFilterField.filterDialogLabel(
    filterSelections: ArchiveFilterSelections,
): String {
    if (this == ArchiveFilterField.DATE) {
        return displayText
    }
    val count = filterSelections.valuesFor(this).size
    return if (count == 0) displayText else "$displayText ($count)"
}

private fun ArchiveFilterField.isActive(filterSelections: ArchiveFilterSelections): Boolean {
    return if (this == ArchiveFilterField.DATE) {
        filterSelections.dateRange != null
    } else {
        filterSelections.valuesFor(this).isNotEmpty()
    }
}

/**
 * Render one checkbox-style archive filter.
 *
 * @param field The filter field being edited.
 * @param selectedValues Currently selected values.
 * @param availableValues Values available under the other active filters.
 * @param onUpdateValues Callback replacing selected values.
 * @param onClear Callback clearing this filter.
 * @param onBack Callback returning to the filter field chooser.
 */
@Composable
private fun ArchiveValueFilterDialog(
    field: ArchiveFilterField,
    selectedValues: Set<String>,
    availableValues: List<ArchiveFilterValueOption>,
    onUpdateValues: (Set<String>) -> Unit,
    onClear: () -> Unit,
    onBack: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onBack,
        title = { Text(field.displayText) },
        text = {
            ScrollableDialogRegion(
                maxHeight = 420.dp,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                TextActionButton(
                    label = "Clear",
                    enabled = selectedValues.isNotEmpty(),
                    tag = "archive-clear-filter-${field.name}",
                    onClick = onClear,
                )
                if (availableValues.isEmpty()) {
                    Text("No values available.")
                }
                availableValues.forEach { option ->
                    val checked = option.value in selectedValues
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                onUpdateValues(selectedValues.toggled(option.value))
                            }
                            .testTag("archive-filter-value-${option.value}"),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Checkbox(
                            checked = checked,
                            modifier = Modifier.testTag("archive-filter-checkbox-${option.value}"),
                            onCheckedChange = {
                                onUpdateValues(selectedValues.toggled(option.value))
                            },
                        )
                        Text("${option.value} (${option.gameCount})")
                    }
                }
            }
        },
        confirmButton = {
            TextActionButton(label = "Back", onClick = onBack)
        },
    )
}

private fun Set<String>.toggled(value: String): Set<String> {
    return if (value in this) this - value else this + value
}

/**
 * Render the archive date filter.
 *
 * @param dateFilter Current date filter.
 * @param onUpdateDateFilter Callback replacing the date filter.
 * @param onBack Callback returning to the filter field chooser.
 */
@Composable
private fun ArchiveDateFilterDialog(
    dateFilter: ArchiveDateFilter?,
    onUpdateDateFilter: (ArchiveDateFilter?) -> Unit,
    onBack: () -> Unit,
) {
    val today = remember { LocalDate.now() }
    var selectedStart by remember { mutableStateOf(dateFilter?.startDate()) }
    var selectedEnd by remember { mutableStateOf(dateFilter?.endDate()) }
    var datePickerTarget by remember { mutableStateOf<ArchiveCustomDateTarget?>(null) }

    fun setDateRange(start: LocalDate?, end: LocalDate?) {
        selectedStart = start
        selectedEnd = end
    }

    AlertDialog(
        onDismissRequest = onBack,
        title = { Text("Date") },
        text = {
            ScrollableDialogRegion(
                maxHeight = 420.dp,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                TextActionButton(
                    label = "Clear",
                    enabled = selectedStart != null || selectedEnd != null,
                    tag = "archive-clear-filter-DATE",
                    onClick = {
                        setDateRange(null, null)
                    },
                )
                ArchiveDatePreset.entries.forEach { preset ->
                    MenuButton(
                        label = preset.displayText,
                        tag = "archive-date-preset-${preset.name}",
                        onClick = {
                            setDateRange(preset.startDate(today), preset.endDate(today))
                        },
                    )
                }
                HorizontalDivider()
                MenuButton(
                    label = "Start: ${selectedStart.archiveDateLabel()}",
                    tag = "archive-custom-start-date",
                    onClick = { datePickerTarget = ArchiveCustomDateTarget.START },
                )
                MenuButton(
                    label = "End: ${selectedEnd.archiveDateLabel()}",
                    tag = "archive-custom-end-date",
                    onClick = { datePickerTarget = ArchiveCustomDateTarget.END },
                )
            }
        },
        confirmButton = {
            TextActionButton(
                label = "Done",
                onClick = {
                    onUpdateDateFilter(
                        if (selectedStart == null && selectedEnd == null) {
                            null
                        } else {
                            ArchiveDateFilter(
                                start = selectedStart,
                                end = selectedEnd,
                            )
                        }
                    )
                    onBack()
                },
            )
        },
        dismissButton = {
            TextActionButton(label = "Cancel", onClick = onBack)
        },
    )

    datePickerTarget?.let { target ->
        LocalDatePickerDialog(
            initialDate = if (target == ArchiveCustomDateTarget.START) {
                selectedStart ?: today
            } else {
                selectedEnd ?: today
            },
            setButtonTag = "archive-date-set",
            onDismiss = { datePickerTarget = null },
            onConfirm = { selectedDate ->
                if (target == ArchiveCustomDateTarget.START) {
                    selectedStart = selectedDate
                } else {
                    selectedEnd = selectedDate
                }
                datePickerTarget = null
            },
        )
    }
}

private enum class ArchiveCustomDateTarget {
    START,
    END,
}

private fun LocalDate?.archiveDateLabel(): String {
    return this?.let { formatStartDate(it) } ?: "None"
}

/**
 * Render a radio-style row for archive sort choices.
 *
 * @param label Row label.
 * @param selected Whether this row is selected.
 * @param tag Test tag for the row.
 * @param onClick Callback selecting this row.
 */
@Composable
private fun ArchiveRadioRow(
    label: String,
    selected: Boolean,
    tag: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .testTag(tag),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(
            selected = selected,
            onClick = onClick,
        )
        Text(label)
    }
}

/**
 * Render the archive sort dialog.
 *
 * @param sortMode Current sort mode.
 * @param onSortModeSelected Callback replacing the sort mode.
 * @param onDismiss Callback closing the dialog.
 */
@Composable
private fun ArchiveSortDialog(
    sortMode: ArchiveSortMode,
    onSortModeSelected: (ArchiveSortMode) -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Sort games by:") },
        text = {
            ScrollableDialogRegion(
                maxHeight = 420.dp,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                ArchiveSortMode.entries.forEach { mode ->
                    ArchiveRadioRow(
                        label = mode.displayText,
                        selected = mode == sortMode,
                        tag = "archive-sort-${mode.name}",
                        onClick = {
                            onSortModeSelected(mode)
                            onDismiss()
                        },
                    )
                }
            }
        },
        confirmButton = {
            TextActionButton(label = "Cancel", onClick = onDismiss)
        },
    )
}

/**
 * Render the in-progress category with the live current game separate from saved games.
 *
 * @param currentGame The current game row to show above saved games, if any.
 * @param savedGames Saved in-progress game rows.
 * @param onOpenCurrentGame Callback opening the current game summary.
 * @param onOpenArchivedGame Callback opening one saved in-progress game.
 * @param onDeleteSavedGame Callback requesting deletion of one saved in-progress game.
 * @param onDeleteAllSavedGames Callback requesting deletion of all saved in-progress games.
 */
@Composable
private fun InProgressGamesList(
    currentGame: GameListEntry?,
    savedGames: List<ArchivedGameListItem>,
    onOpenCurrentGame: () -> Unit,
    onOpenArchivedGame: (Int) -> Unit,
    onDeleteSavedGame: (Int) -> Unit,
    onDeleteAllSavedGames: () -> Unit,
) {
    if (currentGame == null && savedGames.isEmpty()) {
        Text(ArchivedGameCategory.IN_PROGRESS.emptyText)
        return
    }

    if (currentGame != null) {
        ArchiveSectionLabel("Current game")
        GameListRow(
            entry = currentGame,
            onClick = onOpenCurrentGame,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("current-in-progress-game"),
        )
    }

    if (savedGames.isNotEmpty()) {
        SavedArchiveSectionHeader(
            label = "Saved games",
            showTopDivider = currentGame != null,
            onDeleteAllSavedItems = onDeleteAllSavedGames,
        )
        savedGames.forEachIndexed { index, game ->
            ArchivedGameRow(
                displayedIndex = index,
                entry = game.entry,
                rowTagPrefix = "saved-in-progress-game",
                deleteTagPrefix = "delete-saved-in-progress-game",
                onClick = {
                    onOpenArchivedGame(game.index)
                },
                onDelete = {
                    onDeleteSavedGame(game.index)
                },
            )
        }
    }
}

/**
 * Render the setup category with the current setup draft separate from saved setup states.
 *
 * @param currentSetup The current setup draft row to show above saved setup states, if any.
 * @param savedSetups Saved setup-state rows.
 * @param onOpenCurrentSetup Callback opening the current setup draft.
 * @param onOpenSavedSetup Callback opening one saved setup state.
 * @param onDeleteSavedSetup Callback requesting deletion of one saved setup state.
 * @param onDeleteAllSavedSetups Callback requesting deletion of all saved setup states.
 */
@Composable
private fun SetupStatesList(
    currentSetup: GameListEntry?,
    savedSetups: List<ArchivedGameListItem>,
    onOpenCurrentSetup: () -> Unit,
    onOpenSavedSetup: (Int) -> Unit,
    onDeleteSavedSetup: (Int) -> Unit,
    onDeleteAllSavedSetups: () -> Unit,
) {
    if (currentSetup == null && savedSetups.isEmpty()) {
        Text(ArchivedGameCategory.SETUP.emptyText)
        return
    }

    if (currentSetup != null) {
        ArchiveSectionLabel("Current setup")
        GameListRow(
            entry = currentSetup,
            onClick = onOpenCurrentSetup,
            modifier = Modifier
                .fillMaxWidth()
                .testTag("current-setup-state"),
        )
    }

    if (savedSetups.isNotEmpty()) {
        SavedArchiveSectionHeader(
            label = "Saved setup states",
            showTopDivider = currentSetup != null,
            onDeleteAllSavedItems = onDeleteAllSavedSetups,
        )
        savedSetups.forEachIndexed { index, setup ->
            ArchivedGameRow(
                displayedIndex = index,
                entry = setup.entry,
                rowTagPrefix = "saved-setup-state",
                deleteTagPrefix = "delete-saved-setup-state",
                onClick = {
                    onOpenSavedSetup(setup.index)
                },
                onDelete = {
                    onDeleteSavedSetup(setup.index)
                },
            )
        }
    }
}

/**
 * Render a saved-items section header with its bulk delete action.
 *
 * @param label The subsection label.
 * @param showTopDivider Whether to separate the saved section from the current item.
 * @param onDeleteAllSavedItems Callback requesting deletion of all saved items in the section.
 */
@Composable
private fun SavedArchiveSectionHeader(
    label: String,
    showTopDivider: Boolean,
    onDeleteAllSavedItems: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        if (showTopDivider) {
            HorizontalDivider(modifier = Modifier.padding(top = 8.dp))
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArchiveSectionLabel(label)
            TextActionButton(
                label = "Delete all",
                compact = true,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                tag = "delete-all-archived-games",
                onClick = onDeleteAllSavedItems,
            )
        }
    }
}

/**
 * Render a subsection label in an archive category list.
 *
 * @param text The label text.
 */
@Composable
private fun ArchiveSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleMedium,
    )
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
        TextActionButton(
            label = "Delete all",
            tag = "delete-all-archived-games",
            onClick = onClick,
        )
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
    NavigationButton(
        label = "${category.displayText} ($count)",
        fullWidth = true,
        tag = "archive-category-${category.name}",
        onClick = { onOpenCategory(category) },
    )
}

/**
 * Render an archived game row with a separate right-side delete action.
 *
 * @param displayedIndex The row index in the currently visible archive category.
 * @param entry The game list entry to display.
 * @param rowTagPrefix Prefix for the row test tag.
 * @param deleteTagPrefix Prefix for the delete action test tag.
 * @param onClick Callback opening this archived game.
 * @param onDelete Callback requesting deletion of this archived game.
 */
@Composable
private fun ArchivedGameRow(
    displayedIndex: Int,
    entry: GameListEntry,
    rowTagPrefix: String = "archived-game",
    deleteTagPrefix: String = "delete-archived-game",
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        GameListRow(
            entry = entry,
            onClick = onClick,
            modifier = Modifier
                .weight(1f)
                .testTag("$rowTagPrefix-$displayedIndex"),
        )
        IconActionButton(
            icon = Icons.Filled.Delete,
            contentDescription = "Delete ${entry.summaryLine}",
            size = 48.dp,
            iconSize = 24.dp,
            tag = "$deleteTagPrefix-$displayedIndex",
            onClick = onDelete,
        )
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
            TextActionButton(label = "Cancel", onClick = onDismiss)
        },
    )
}
