package rmjarvis.ultiobserver

import java.io.ByteArrayOutputStream
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.int

// This is enough for ~3000 files. Should be plenty for real-life usagej
// Mostly it just provides a finite limit to bound memory use when reading the files.
private const val MAX_BACKUP_BYTES = 64 * 1024 * 1024

/// Apply the ordinary archive filters to completed games while the backup page is open.
internal fun AppStateSnapshot.filteredCompletedBackupGames(): FilteredArchiveState =
    getFilteredArchiveState(
        archivedGames = archivedGames,
        selectedCategory = ArchivedGameCategory.COMPLETED,
        filterSelections = archiveFilterSelections,
        sortMode = archiveSortMode,
    )

/** Portable archive envelope, independent of the app's private storage layout. */
@Serializable
private data class ArchiveBackup(
    val format: String,
    val formatVersion: Int,
    val games: List<JsonElement>,
)

/** Write a complete, independently versioned ZIP snapshot of the selected completed games. */
internal fun writeArchiveBackup(games: List<GameState>, output: OutputStream) {
    val backup = ArchiveBackup(
        format = "UltiObserver archive",
        formatVersion = 1,
        games = games.map { appStateJson.parseToJsonElement(encodeArchivedGame(it)) },
    )
    ZipOutputStream(output).use { zip ->
        val bytes = appStateJson.encodeToString(backup).toByteArray(Charsets.UTF_8)
        require(bytes.size <= MAX_BACKUP_BYTES) { "This backup is too large. Select fewer games." }
        zip.putNextEntry(ZipEntry("archive.json"))
        zip.write(bytes)
        zip.closeEntry()
    }
}

/** Validate the whole external file before offering any changes to the phone's archive. */
internal fun readArchiveBackup(input: InputStream): List<GameState> {
    val text = ZipInputStream(input).use { zip ->
        require(zip.nextEntry?.name == "archive.json") { "This is not an UltiObserver backup." }
        val bytes = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = zip.read(buffer)
            // read returns -1 when it reaches the end of the file.
            if (count < 0) break
            require(bytes.size() + count <= MAX_BACKUP_BYTES) { "This backup is too large to load." }
            bytes.write(buffer, 0, count)
        }
        require(zip.nextEntry == null) { "Unexpected files in this backup." }
        bytes.toString(Charsets.UTF_8.name())
    }
    val backup = appStateJson.decodeFromString<ArchiveBackup>(text)
    require(backup.format == "UltiObserver archive") { "This is not an UltiObserver backup." }
    require(backup.formatVersion == 1) { "Update UltiObserver to load this backup format." }
    require(backup.games.isNotEmpty()) { "This backup contains no games." }
    val games = backup.games.map { element ->
        val bucket = element.jsonObject
        val version = AppVersion(
            bucket.getValue("versionName").jsonPrimitive.content,
            bucket.getValue("versionCode").jsonPrimitive.int,
        )
        val data = bucket.getValue("data")
        // Backups start at 1.5: an ID must exist before migration, never be invented on import.
        val id = data.jsonObject.getValue("state").jsonObject.getValue("gameId").jsonPrimitive.content
        require(UUID.fromString(id).toString() == id) { "A game has an invalid ID." }
        val migrated = requireNotNull(migrateArchivedGameJson(data, version)) {
            "Update UltiObserver to load games from version ${version.versionName}."
        }
        val decoded = appStateJson.decodeFromJsonElement<SerializedGameState>(migrated.jsonElement).restore()
        val game = migrated.migrateGameState(decoded)
        require(game == game.withGameId(id)) { "A game's history has inconsistent IDs." }
        game
    }
    require(games.map { it.gameId }.toSet().size == games.size) {
        "The backup contains multiple games with the same ID."
    }
    return games
}

/** User's choice for one differing archive entry with the same permanent identity. */
internal enum class ArchiveImportChoice(val displayText: String) {
    KEEP_PHONE("Keep phone version"),
    RESTORE_BACKUP("Restore backup version"),
    KEEP_BOTH("Keep both"),
}

/** How a backup entry relates to the phone's collection. */
internal enum class ArchiveImportStatus { NEW, IDENTICAL, DIFFERENT, UNARCHIVED }

/** Checkbox selection shared by the filtered export list and new games in an import. */
internal class ArchiveBackupSelection(val games: List<GameState>, private val excludedIds: List<String>) {
    val selectedGames = games.filter { it.gameId !in excludedIds }
    val selectedIds = ArrayList(selectedGames.map { it.gameId })
    val bulkActionLabel = if (selectedGames.size == games.size) "Unselect all" else "Select all"

    fun toggleAll(): ArrayList<String> = toggleBackupSelection(excludedIds, games.map { it.gameId })
}

internal fun FilteredArchiveState.backupSelection(
    phoneGames: List<GameState>,
    excludedIds: List<String>,
): ArchiveBackupSelection = ArchiveBackupSelection(selectedGames!!.map { phoneGames[it.index] }, excludedIds)

/** One nonempty group in the import preview, with its shared explanation. */
internal data class ArchiveImportSection(val status: ArchiveImportStatus, val games: List<GameState>) {
    val heading: String? get() = when (status) {
        ArchiveImportStatus.NEW -> null
        ArchiveImportStatus.DIFFERENT -> "Already present on phone — backup differs"
        ArchiveImportStatus.IDENTICAL -> "Already present on phone"
        ArchiveImportStatus.UNARCHIVED ->
            "Unarchived ${pluralize(games.size, "game")} on phone " +
            "(archive or delete to enable restoring from backup)"
    }
}

/** Import groups and eligible selections derived from the phone's current collection. */
internal class ArchiveImportPreview(
    games: List<GameState>,
    phoneGames: List<GameState>,
    currentGame: GameState?,
    excludedIds: List<String>,
    private val choices: Map<String, ArchiveImportChoice>,
) {
    private val groups = games.groupBy { it.importStatus(phoneGames, currentGame) }
    val sections = listOf(
        ArchiveImportStatus.NEW,
        ArchiveImportStatus.DIFFERENT,
        ArchiveImportStatus.IDENTICAL,
        ArchiveImportStatus.UNARCHIVED,
    ).filter { it in groups }.map { ArchiveImportSection(it, groups.getValue(it)) }
    val selection = ArchiveBackupSelection(groups[ArchiveImportStatus.NEW].orEmpty(), excludedIds)
    val selectedGames = games.filter {
        it.selectedForImport(phoneGames, currentGame, excludedIds, choiceFor(it))
    }

    fun choiceFor(game: GameState): ArchiveImportChoice = choices[game.gameId] ?: ArchiveImportChoice.KEEP_PHONE
}

internal fun toggleExcludedId(excluded: List<String>, id: String): ArrayList<String> {
    return ArrayList(if (id in excluded) excluded - id else excluded + id)
}

internal fun GameState.importStatus(phoneGames: List<GameState>, currentGame: GameState?): ArchiveImportStatus {
    if (gameId == currentGame?.gameId) return ArchiveImportStatus.UNARCHIVED
    val existing = phoneGames.firstOrNull { it.gameId == gameId } ?: return ArchiveImportStatus.NEW
    if (existing.phase != GamePhase.GAME_OVER) return ArchiveImportStatus.UNARCHIVED
    return if (existing == this) ArchiveImportStatus.IDENTICAL else ArchiveImportStatus.DIFFERENT
}

internal fun GameState.selectedForImport(
    phoneGames: List<GameState>,
    currentGame: GameState?,
    excludedIds: List<String>,
    choice: ArchiveImportChoice,
): Boolean = when (importStatus(phoneGames, currentGame)) {
    ArchiveImportStatus.NEW -> gameId !in excludedIds
    ArchiveImportStatus.DIFFERENT -> choice != ArchiveImportChoice.KEEP_PHONE
    ArchiveImportStatus.IDENTICAL, ArchiveImportStatus.UNARCHIVED -> false
}

/// Toggle only visible rows, preserving individual exclusions when the filters change.
internal fun toggleBackupSelection(excludedIds: List<String>, visibleIds: List<String>): ArrayList<String> {
    return ArrayList(if (visibleIds.none { it in excludedIds }) {
        excludedIds.toSet() + visibleIds
    } else {
        excludedIds.toSet() - visibleIds.toSet()
    })
}

/** Merge selected entries; keeping both forks the phone copy, retaining the shared backup ID. */
internal fun mergeArchiveBackup(
    phoneGames: List<GameState>,
    backupGames: List<GameState>,
    choices: Map<String, ArchiveImportChoice>,
): List<GameState> {
    val merged = phoneGames.toMutableList()
    backupGames.forEach { backup ->
        val index = merged.indexOfFirst { it.gameId == backup.gameId }
        if (index < 0) {
            merged.add(backup)
        } else if (merged[index] != backup) {
            // Keep phone entries have already been excluded from the selected games.
            if (choices.getValue(backup.gameId) == ArchiveImportChoice.KEEP_BOTH) {
                merged[index] = merged[index].withGameId(UUID.randomUUID().toString())
                merged.add(backup)
            } else {
                merged[index] = backup
            }
        }
    }
    return merged
}
