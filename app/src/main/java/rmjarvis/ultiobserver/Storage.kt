package rmjarvis.ultiobserver

import java.io.File
import java.io.IOException
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

internal val APP_STATE_VERSION_NAME: String = BuildConfig.VERSION_NAME
internal val APP_STATE_VERSION_CODE: Int = BuildConfig.VERSION_CODE

/**
 * App version metadata read from one persisted JSON bucket.
 *
 * @param versionName The human-readable version string.
 * @param versionCode The integer app version that wrote the file.
 */
internal data class AppVersion(
    val versionName: String,
    val versionCode: Int,
)

/**
 * File-level persistence envelope for one app-data bucket.
 *
 * @param versionName The human-readable app version that wrote the file.
 * @param versionCode The integer app version that wrote the file.
 * @param data The bucket payload without file-level metadata.
 */
@Serializable
private class PersistedBucket<T>(
    private val versionName: String,
    private val versionCode: Int,
    private val data: T,
)

/**
 * Parsed persisted bucket with metadata separated from payload JSON.
 *
 * @param version The version metadata read from the file envelope.
 * @param data The bucket payload JSON.
 */
private data class StoredJsonBucket(
    val version: AppVersion,
    val data: JsonElement,
)

/**
 * Independently recoverable persisted app-data bucket.
 *
 * @param label The user-facing name shown in startup recovery notices.
 */
internal enum class PersistedData(val label: String) {
    GAME_STATE("Current game"),
    PROFILE("Profile"),
    SETTINGS("Settings"),
    ARCHIVED_GAMES("Archived games"),
}

/**
 * Description of persisted data that had to be reset or partially recovered on startup.
 *
 * @param resetAreas The nonempty set of persisted buckets that were repaired.
 */
internal data class RecoveryNotice(
    val resetAreas: Set<PersistedData>,
) {
    init {
        require(resetAreas.isNotEmpty()) {
            "Persistence recovery notices must name at least one reset area."
        }
    }

    val title: String = "Phone data reset"

    val message: String
        get() {
            val labels = resetAreas.sortedBy { it.ordinal }.map { it.label }
            val areas = when (labels.size) {
                1 -> labels.single()
                2 -> "${labels[0]} and ${labels[1]}"
                else -> labels.dropLast(1).joinToString(", ") + ", and " + labels.last()
            }
            return "Sorry, some phone data was corrupt, so UltiObserver had to revert to default values for $areas."
        }
}

/**
 * App-state persistence boundary used by AppViewModel.
 * Tests provide alternate implementations so ViewModel behavior can be exercised without Android file I/O,
 * while production uses the file-backed implementation below.
 */
internal interface AppStateStorage {
    val resetPersistedDataAreas: Set<PersistedData>

    /// Load the persisted current/setup game bucket, if one exists.
    fun loadCurrentGame(): GameState?

    /**
     * Save the current/setup game bucket.
     *
     * @param state The current-game state snapshot to persist.
     */
    fun saveCurrentGame(state: GameState?)

    /// Load the persisted profile bucket, if one exists.
    fun loadProfile(): Profile?

    /**
     * Save the profile bucket.
     *
     * @param state The profile state snapshot to persist.
     */
    fun saveProfile(state: Profile)

    /// Load the persisted settings bucket, if one exists.
    fun loadSettings(): Settings?

    /**
     * Save the settings bucket.
     *
     * @param state The settings state snapshot to persist.
     */
    fun saveSettings(state: Settings)

    /// Load all readable archived games.
    fun loadArchivedGames(): List<GameState>

    /**
     * Save the archived games.
     *
     * @param games The ordered archived games to persist.
     */
    fun saveArchivedGames(games: List<GameState>)
}

internal val appStateJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    prettyPrint = true
}

/// Encode a current-game bucket using serialized undo/redo patch chains.
internal fun encodeCurrentGame(state: GameState?): String {
    return encodePersistedBucket(state?.toSerializedGameState())
}

/// Encode a profile bucket with file-level app version metadata.
internal fun encodeProfile(state: Profile): String {
    return encodePersistedBucket(state)
}

/// Encode a settings bucket with file-level app version metadata.
internal fun encodeSettings(state: Settings): String {
    return encodePersistedBucket(state)
}

/// Encode one archived-game file with file-level app version metadata.
internal fun encodeArchivedGame(game: GameState): String {
    return encodePersistedBucket(game)
}

/// Encode one persistence payload under the standard file envelope.
private inline fun <reified T> encodePersistedBucket(data: T): String {
    return appStateJson.encodeToString(
        PersistedBucket(
            versionName = APP_STATE_VERSION_NAME,
            versionCode = APP_STATE_VERSION_CODE,
            data = data,
        )
    )
}

/// Decode a current-game bucket from serialized undo/redo patch chains.
private fun decodeCurrentGame(jsonElement: JsonElement): GameState? {
    if (jsonElement is JsonNull) {
        return null
    }
    return appStateJson.decodeFromJsonElement<SerializedGameState>(jsonElement).restore()
}

/**
 * Decode persisted current-game state for a known storage version.
 *
 * @param jsonElement The payload JSON from the current-game bucket.
 * @param version The version metadata read from that JSON object.
 */
private fun decodeCurrentGameJson(
    jsonElement: JsonElement,
    version: AppVersion,
): PersistenceDecodeResult<GameState?>? {
    return try {
        val migrated = migrateCurrentGameJson(jsonElement, version) ?: return null
        PersistenceDecodeResult(
            value = decodeCurrentGame(migrated.jsonElement),
            wasMigrated = migrated.wasMigrated,
        )
    } catch (_: RuntimeException) {
        null
    }
}

/**
 * Decode persisted archived game state for a known storage version.
 *
 * @param jsonElement The payload JSON from one archived-game bucket.
 * @param version The version metadata read from that JSON object.
 */
private fun decodeArchivedGameJson(
    jsonElement: JsonElement,
    version: AppVersion,
): PersistenceDecodeResult<GameState>? {
    return try {
        val migrated = migrateArchivedGameJson(jsonElement, version) ?: return null
        PersistenceDecodeResult(
            value = appStateJson.decodeFromJsonElement<GameState>(migrated.jsonElement),
            wasMigrated = migrated.wasMigrated,
        )
    } catch (_: RuntimeException) {
        null
    }
}

/**
 * File-backed app state storage using JSON files in Android app-private storage.
 *
 * @param rootDir The app-private directory where persistence files live.
 * @param moveFileAtomically File move operation injected so tests can force fallback behavior.
 * @param replaceFile Non-atomic replacement operation injected so tests can observe fallback behavior.
 */
internal class FileAppStateStorage(
    private val rootDir: File,
    // Keep file moves injectable so tests can force platform-specific fallback paths.
    private val moveFileAtomically: (File, File) -> Unit = ::moveFileAtomically,
    private val replaceFile: (File, File) -> Unit = ::replaceFile,
) : AppStateStorage {
    private val currentGameStateFile = File(rootDir, "current_game_state.json")
    private val profileFile = File(rootDir, "profile.json")
    private val settingsFile = File(rootDir, "settings.json")
    private val archivedGamesDir = File(rootDir, "archived_games")
    private val resetAreas = mutableSetOf<PersistedData>()

    override val resetPersistedDataAreas: Set<PersistedData>
        get() = resetAreas.toSet()

    /// Load current/setup game state from app-private JSON.
    override fun loadCurrentGame(): GameState? {
        resetAreas.remove(PersistedData.GAME_STATE)
        if (!currentGameStateFile.exists()) {
            return null
        }
        val currentGameState = readExistingBucket(currentGameStateFile)
            ?.let { storedBucket ->
                decodeCurrentGameJson(storedBucket.data, storedBucket.version)
            }
        if (currentGameState == null) {
            // We get here if:
            // - json file exists but the read failed
            // - the version number could not be read from the json object
            // - our decode function didn't know how to handle that version number
            // - our decode function had an error decoding the json object
            resetAreas += PersistedData.GAME_STATE
            return null
        }
        if (currentGameState.wasMigrated) {
            saveCurrentGame(currentGameState.value)
        }
        return currentGameState.value
    }

    /**
     * Save current/setup game state to app-private JSON.
     *
     * @param state The current-game state snapshot to persist.
     */
    override fun saveCurrentGame(state: GameState?) {
        rootDir.mkdirs()
        currentGameStateFile.writeAtomically(encodeCurrentGame(state), moveFileAtomically, replaceFile)
    }

    /// Load profile state from app-private JSON.
    override fun loadProfile(): Profile? {
        resetAreas.remove(PersistedData.PROFILE)
        if (!profileFile.exists()) {
            return null
        }
        val profile = readExistingBucket(profileFile)
            ?.let { storedBucket ->
                Profile.decodeJson(storedBucket.data, storedBucket.version)
            }
        if (profile == null) {
            resetAreas += PersistedData.PROFILE
            return Profile()
        }
        if (profile.wasMigrated) {
            saveProfile(profile.value)
        }
        return profile.value
    }

    /**
     * Save profile state to app-private JSON.
     *
     * @param state The profile state snapshot to persist.
     */
    override fun saveProfile(state: Profile) {
        rootDir.mkdirs()
        profileFile.writeAtomically(encodeProfile(state), moveFileAtomically, replaceFile)
    }

    /// Load settings state from app-private JSON.
    override fun loadSettings(): Settings? {
        resetAreas.remove(PersistedData.SETTINGS)
        if (!settingsFile.exists()) {
            return null
        }
        val settings = readExistingBucket(settingsFile)
            ?.let { storedBucket ->
                Settings.decodeJson(storedBucket.data, storedBucket.version)
            }
        if (settings == null) {
            resetAreas += PersistedData.SETTINGS
            return Settings()
        }
        if (settings.wasMigrated) {
            saveSettings(settings.value)
        }
        return settings.value
    }

    /**
     * Save settings state to app-private JSON.
     *
     * @param state The settings state snapshot to persist.
     */
    override fun saveSettings(state: Settings) {
        rootDir.mkdirs()
        settingsFile.writeAtomically(encodeSettings(state), moveFileAtomically, replaceFile)
    }

    /**
     * Read an existing JSON file as a versioned bucket, returning null for unreadable shapes.
     *
     * @param file The JSON file to read.
     */
    private fun readExistingBucket(file: File): StoredJsonBucket? {
        return readExistingJsonObject(file)?.toStoredJsonBucket()
    }

    /**
     * Read an existing JSON file as an object, returning null for I/O or parse failures.
     *
     * @param file The JSON file to read.
     */
    private fun readExistingJsonObject(file: File): JsonObject? {
        return try {
            appStateJson.parseToJsonElement(file.readText()).jsonObject
        } catch (_: IOException) {
            null
        } catch (_: RuntimeException) {
            null
        }
    }

    /**
     * Read persistence version metadata from a parsed JSON object.
     *
     * @param jsonObject The parsed bucket or archived-game JSON object.
     */
    private fun readVersion(jsonObject: JsonObject): AppVersion? {
        return try {
            val versionName = appStateJson.decodeFromJsonElement<String>(
                jsonObject["versionName"] ?: return null
            )
            val versionCode = appStateJson.decodeFromJsonElement<Int>(
                jsonObject["versionCode"] ?: return null
            )
            AppVersion(versionName = versionName, versionCode = versionCode)
        } catch (_: RuntimeException) {
            null
        }
    }

    /**
     * Split one parsed file into version metadata and payload JSON.
     *
     * Version 1.0 files stored payload fields and version metadata together at top level.
     * Current files store version metadata at top level and the payload under `data`.
     */
    private fun JsonObject.toStoredJsonBucket(): StoredJsonBucket? {
        val version = readVersion(this) ?: return null
        val dataElement = this["data"]
        if (dataElement != null) {
            return StoredJsonBucket(version = version, data = dataElement)
        }
        if (version.persistenceVersion() != "1.0") {
            return null
        }
        return StoredJsonBucket(version = version, data = this)
    }

    /// Load all readable archived games from app-private JSON files.
    override fun loadArchivedGames(): List<GameState> {
        resetAreas.remove(PersistedData.ARCHIVED_GAMES)
        if (!archivedGamesDir.exists()) {
            return emptyList()
        }
        val archiveFiles = archivedGamesDir.listFiles { file -> file.isFile && file.extension == "json" }
            ?: return emptyList()
        var archivedGamesMigrated = false
        val archivedGames = archiveFiles
            .sortedBy { it.name }
            .mapNotNull { file ->
                val archivedGame = readExistingBucket(file)
                    ?.let { storedBucket ->
                        decodeArchivedGameJson(storedBucket.data, storedBucket.version)
                    }
                if (archivedGame == null) {
                    resetAreas += PersistedData.ARCHIVED_GAMES
                }
                if (archivedGame?.wasMigrated == true) {
                    archivedGamesMigrated = true
                }
                archivedGame?.value
            }
        if (archivedGamesMigrated) {
            saveArchivedGames(archivedGames)
        }
        return archivedGames
    }

    /**
     * Replace the archived-game JSON directory with the supplied ordered games.
     *
     * @param games The archived games to write as numbered JSON files.
     */
    override fun saveArchivedGames(games: List<GameState>) {
        archivedGamesDir.mkdirs()
        archivedGamesDir
            .listFiles { file -> file.isFile && file.extension == "json" }
            ?.forEach { file -> Files.delete(file.toPath()) }
        games.forEachIndexed { index, game ->
            File(archivedGamesDir, "%05d.json".format(index))
                .writeAtomically(encodeArchivedGame(game), moveFileAtomically, replaceFile)
        }
    }
}

/**
 * Write text through a temporary file and atomic rename when the platform supports it.
 *
 * @param content The UTF-8 text to write.
 * @param moveFileAtomically The move operation, injectable so tests can force fallback behavior.
 * @param replaceFile The non-atomic replacement operation used when atomic moves are unavailable.
 */
private fun File.writeAtomically(
    content: String,
    moveFileAtomically: (File, File) -> Unit,
    replaceFile: (File, File) -> Unit,
) {
    val directory = parentFile!!
    directory.mkdirs()
    val tmpFile = File(directory, ".$name.tmp")
    try {
        tmpFile.outputStream().use { output ->
            output.write(content.toByteArray(StandardCharsets.UTF_8))
            output.fd.sync()
        }
        try {
            moveFileAtomically(tmpFile, this)
        } catch (_: AtomicMoveNotSupportedException) {
            replaceFile(tmpFile, this)
        }
    } finally {
        if (tmpFile.exists()) {
            tmpFile.delete()
        }
    }
}

/**
 * Move a file into place using the platform's atomic move option.
 *
 * @param source The temporary file to move.
 * @param target The final destination file.
 */
private fun moveFileAtomically(source: File, target: File) {
    Files.move(source.toPath(), target.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
}

/**
 * Replace a file without requiring atomic move support.
 *
 * @param source The temporary file to move.
 * @param target The final destination file.
 */
private fun replaceFile(source: File, target: File) {
    Files.move(source.toPath(), target.toPath(), REPLACE_EXISTING)
}
