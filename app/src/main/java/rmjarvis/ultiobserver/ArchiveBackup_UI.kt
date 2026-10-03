package rmjarvis.ultiobserver

import android.app.Activity
import android.net.Uri
import android.provider.OpenableColumns
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Cancel
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import java.io.IOException
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Select completed games, use Android's document picker, and preview archive imports. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ArchiveTransferScreen(appState: AppState, backup: Boolean, onBack: () -> Unit, onHome: () -> Unit) {
    val snapshot by appState.state.collectAsState()
    val context = LocalContext.current
    val resolver = context.contentResolver
    val backupDirectory = remember(context) { ArchiveBackupDirectory(context) }
    val scope = rememberCoroutineScope()
    var excludedIds by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var pendingExportIds by rememberSaveable { mutableStateOf(arrayListOf<String>()) }
    var source by rememberSaveable { mutableStateOf<String?>(null) }
    var readAttempt by rememberSaveable { mutableStateOf(0) }
    var imported by remember { mutableStateOf<List<GameState>?>(null) }
    var busy by remember { mutableStateOf(false) }
    var message by rememberSaveable { mutableStateOf<String?>(null) }
    var filterDialog by remember { mutableStateOf(false) }
    var sortDialog by remember { mutableStateOf(false) }
    var pickerOpened by rememberSaveable { mutableStateOf(false) }
    var choices by rememberSaveable { mutableStateOf(hashMapOf<String, ArchiveImportChoice>()) }
    var showFolderSetup by rememberSaveable { mutableStateOf(false) }

    suspend fun transfer(action: suspend () -> Unit) {
        busy = true
        try {
            action()
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (failure: Exception) {
            message = "Could not ${if (backup) "save" else "load"} backup. " +
                (failure.message ?: "Please try again.")
        } finally {
            busy = false
        }
    }

    val saveFile = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        val uri = result.data?.data.takeIf { result.resultCode == Activity.RESULT_OK }
        if (uri != null) {
            val games = snapshot.archivedGames.filter { it.gameId in pendingExportIds }
            scope.launch {
                transfer {
                    val filename = withContext(Dispatchers.IO) {
                        val stream = resolver.openOutputStream(uri)
                            ?: throw IOException("The selected file could not be opened.")
                        writeArchiveBackup(games, stream)
                        val cursor = resolver.query(
                            uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null,
                        ) ?: return@withContext null
                        cursor.use {
                            if (it.moveToFirst()) it.getString(0) else null
                        }
                    }
                    message = if (filename == null) {
                        "Backed up ${countedNounPhrase(games.size, "game")}."
                    } else {
                        "Backed up ${countedNounPhrase(games.size, "game")} to $filename."
                    }
                }
            }
        }
    }
    val openFile = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val uri = result.data?.data.takeIf { result.resultCode == Activity.RESULT_OK }
        if (uri != null) {
            source = uri.toString()
            readAttempt += 1
            choices = hashMapOf()
            excludedIds = arrayListOf()
        }
    }
    val chooseFolder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree ->
        if (tree != null) {
            scope.launch {
                transfer {
                    val directory = withContext(Dispatchers.IO) { backupDirectory.chooseParent(tree) }
                    saveFile.launch(createBackupDocumentIntent(context, "UltiObserver-${LocalDate.now()}.zip", directory))
                }
            }
        }
    }

    suspend fun chooseBackupFile() {
        transfer {
            val directory = withContext(Dispatchers.IO) { backupDirectory.directory() }
            openFile.launch(openBackupDocumentIntent(context, directory ?: documentsDirectoryUri))
        }
    }

    LaunchedEffect(backup) {
        if (!backup && !pickerOpened) {
            pickerOpened = true
            chooseBackupFile()
        }
    }
    LaunchedEffect(source, readAttempt) {
        val uri = source
        if (uri != null) {
            imported = null
            transfer {
                imported = withContext(Dispatchers.IO) {
                    val stream = resolver.openInputStream(Uri.parse(uri))
                        ?: throw IOException("The selected file could not be opened.")
                    readArchiveBackup(stream)
                }
            }
        }
    }
    BackHandler {
        if (!busy) onBack()
    }

    val filtered = snapshot.filteredCompletedBackupGames()
    val selection = filtered.backupSelection(snapshot.archivedGames, excludedIds)
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text(if (backup) "Back up archived games" else "Load games from backup") },
                navigationIcon = {
                    TopBarBackButton(onClick = {
                        if (!busy) onBack()
                    })
                },
                actions = {
                    TopBarHomeButton(onClick = {
                        if (!busy) onHome()
                    })
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            if (busy) Text("Please wait.")
            if (backup) {
                ArchiveListControls(
                    bulkActionLabel = selection.bulkActionLabel,
                    visible = !busy,
                    filterAndSortSummaryText = filtered.filterAndSortSummaryText,
                    showDeleteAll = selection.games.isNotEmpty(),
                    onOpenFilter = { filterDialog = true },
                    onOpenSort = { sortDialog = true },
                    onDeleteAll = {
                        excludedIds = selection.toggleAll()
                    },
                )
                Column(
                    Modifier.weight(1f).verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (selection.games.isEmpty()) Text("No archived games match these filters.")
                    selection.games.forEach { game ->
                        val id = game.gameId
                        SelectableBackupRow(game.gameListEntry(), id, id in selection.selectedIds, !busy) {
                            excludedIds = toggleExcludedId(excludedIds, id)
                        }
                    }
                }
                BigActionButton(
                    label = "Back up ${countedNounPhrase(selection.selectedGames.size, "game")}",
                    fullWidth = true,
                    minHeight = 48.dp,
                    containerColor = SecondaryColor,
                    contentColor = OnSecondaryColor,
                    borderColor = null,
                    enabled = selection.selectedGames.isNotEmpty() && !busy,
                    onClick = {
                        pendingExportIds = selection.selectedIds
                        scope.launch {
                            transfer {
                                val directory = withContext(Dispatchers.IO) { backupDirectory.directory() }
                                if (directory == null) {
                                    showFolderSetup = true
                                } else {
                                    saveFile.launch(createBackupDocumentIntent(
                                        context, "UltiObserver-${LocalDate.now()}.zip", directory,
                                    ))
                                }
                            }
                        }
                    },
                )
            } else {
                val preview = imported?.let {
                    ArchiveImportPreview(it, snapshot.archivedGames, snapshot.currentGame, excludedIds, choices)
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    TextButton(label = "Choose backup file", enabled = !busy, onClick = {
                        scope.launch {
                            chooseBackupFile()
                        }
                    })
                    if (preview != null && preview.selection.games.isNotEmpty()) {
                        TextButton(
                            label = preview.selection.bulkActionLabel,
                            enabled = !busy,
                            onClick = {
                                excludedIds = preview.selection.toggleAll()
                            },
                        )
                    }
                }
                if (preview != null) {
                    Column(
                        Modifier.weight(1f).verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        preview.sections.forEachIndexed { index, section ->
                            if (index > 0) HorizontalDivider()
                            val status = section.status
                            val heading = section.heading
                            if (heading != null) Text(heading, style = MaterialTheme.typography.titleMedium)
                            section.games.forEach { game ->
                                if (status == ArchiveImportStatus.NEW) {
                                    SelectableBackupRow(game.gameListEntry(), game.gameId, game.gameId in preview.selection.selectedIds, !busy) {
                                        excludedIds = toggleExcludedId(excludedIds, game.gameId)
                                    }
                                } else {
                                    BackupGameRow(entry = game.gameListEntry(), onClick = null) {
                                        if (status == ArchiveImportStatus.IDENTICAL) {
                                            Icon(
                                                imageVector = Icons.Outlined.CheckCircle,
                                                contentDescription = "Already on this phone",
                                                tint = PrimaryColor,
                                                modifier = Modifier.testTag("backup-present-${game.gameId}"),
                                            )
                                        } else if (status == ArchiveImportStatus.UNARCHIVED) {
                                            Icon(
                                                imageVector = Icons.Outlined.Cancel,
                                                contentDescription = "Cannot import — archive or delete the phone copy first",
                                                tint = ResetColor,
                                                modifier = Modifier.testTag("backup-unarchived-${game.gameId}"),
                                            )
                                        }
                                    }
                                    if (status == ArchiveImportStatus.DIFFERENT) {
                                        ArchiveImportChoice.entries.forEach { choice ->
                                            val chosen = preview.choiceFor(game)
                                            Row(
                                                Modifier.fillMaxWidth()
                                                    .testTag("import-choice-${game.gameId}-${choice.name}")
                                                    .clickable(enabled = !busy) {
                                                        choices = HashMap(choices).apply { put(game.gameId, choice) }
                                                    },
                                                verticalAlignment = Alignment.CenterVertically,
                                            ) {
                                                RadioButton(selected = chosen == choice, onClick = null)
                                                Text(choice.displayText)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                    BigActionButton(
                        label = "Load ${countedNounPhrase(preview.selectedGames.size, "game")}",
                        fullWidth = true,
                        minHeight = 48.dp,
                        containerColor = SecondaryColor,
                        contentColor = OnSecondaryColor,
                        borderColor = null,
                        enabled = preview.selectedGames.isNotEmpty() && !busy,
                        onClick = {
                            scope.launch {
                                transfer {
                                    withContext(Dispatchers.IO) {
                                        appState.importArchiveBackup(preview.selectedGames, choices)
                                    }
                                    onBack()
                                }
                            }
                        },
                    )
                }
            }
        }
    }
    if (showFolderSetup) {
        AlertDialog(
            onDismissRequest = {
                showFolderSetup = false
            },
            title = { Text("Choose your default backup location") },
            text = {
                Text(
                    "UltiObserver will create a folder called UltiObserver Backups on your phone " +
                    "to use as the default backup location. By default, this will be a " +
                    "sub-folder in Documents, but you can choose a different parent folder " +
                    "if you prefer. " +
                    "Android will ask you to allow access to the parent folder this one time. " +
                    "When saving or loading backups, you will still be able to browse to " +
                    "another location, including Google Drive, if available, " +
                    "to save files in the cloud."
                )
            },
            confirmButton = {
                TextButton(label = "Create backup folder", onClick = {
                    showFolderSetup = false
                    chooseFolder.launch(documentsDirectoryUri)
                })
            },
            dismissButton = {
                TextButton(label = "Cancel", onClick = {
                    showFolderSetup = false
                })
            },
        )
    }
    if (filterDialog) {
        ArchiveFilterDialog(
            filterSelections = snapshot.archiveFilterSelections,
            availableValues = filtered.availableFilterValues,
            onUpdateValues = { field, values ->
                appState.updateArchiveFilterSelections(field, values)
            },
            onUpdateDateFilter = { date ->
                appState.updateArchiveDateFilter(date)
            },
            onClearFilter = { field ->
                appState.clearArchiveFilter(field)
            },
            onClearAll = {
                appState.clearArchiveFilterSelections()
            },
            onDismiss = { filterDialog = false },
        )
    }
    if (sortDialog) {
        ArchiveSortDialog(
            sortMode = snapshot.archiveSortMode,
            onSortModeSelected = { mode ->
                appState.updateArchiveSortMode(mode)
            },
            onDismiss = {
                sortDialog = false
            },
        )
    }
    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            text = { Text(text) },
            confirmButton = { TextButton(label = "OK", onClick = { message = null }) },
        )
    }
}

@Composable
private fun SelectableBackupRow(
    entry: GameListEntry,
    id: String,
    selected: Boolean,
    enabled: Boolean,
    onToggle: () -> Unit,
) {
    BackupGameRow(entry, onClick = if (enabled) onToggle else null) {
        Checkbox(
            checked = selected,
            enabled = enabled,
            onCheckedChange = { onToggle() },
            modifier = Modifier.testTag("backup-select-$id").semantics {
                contentDescription = "Include ${entry.summaryLine}"
            },
        )
    }
}

@Composable
private fun BackupGameRow(entry: GameListEntry, onClick: (() -> Unit)?, trailing: @Composable () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        GameListRow(entry, Modifier.weight(1f), onClick = onClick)
        Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
            trailing()
        }
    }
}
