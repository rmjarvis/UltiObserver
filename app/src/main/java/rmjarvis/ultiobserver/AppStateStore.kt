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
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject

internal val APP_STATE_VERSION_NAME: String = BuildConfig.VERSION_NAME
internal val APP_STATE_VERSION_CODE: Int = BuildConfig.VERSION_CODE

internal data class PersistedAppVersion(
    val versionName: String,
    val versionCode: Int,
)

@Serializable
internal data class PersistedCurrentGameState(
    val versionName: String = APP_STATE_VERSION_NAME,
    val versionCode: Int = APP_STATE_VERSION_CODE,
    val setupState: GameSetupState = newGameSetupState(),
    val liveState: LiveGameState? = null,
    val setupMode: SetupMode = SetupMode.NEW_GAME,
    val hasSetupDraft: Boolean = false,
) {
    companion object {
        /**
         * Decode persisted current-game state for a known storage version.
         *
         * @param jsonObject The parsed JSON object from the current-game bucket.
         * @param version The version metadata read from that JSON object.
         */
        fun decodeJson(jsonObject: JsonObject, version: PersistedAppVersion): PersistedCurrentGameState? {
            // Placeholder for future version-specific decoding/migration into the current model.
            return when {
                version.versionCode == APP_STATE_VERSION_CODE -> decodeCurrentJson(jsonObject)
                else -> null
            }
        }

        /**
         * Decode current-version current-game JSON, returning null when the bucket is corrupt.
         *
         * @param jsonObject The parsed current-game JSON object.
         */
        private fun decodeCurrentJson(jsonObject: JsonObject): PersistedCurrentGameState? {
            return try {
                appStateJson.decodeFromJsonElement(jsonObject)
            } catch (_: RuntimeException) {
                null
            }
        }
    }
}

@Serializable
internal data class PersistedProfile(
    val versionName: String = APP_STATE_VERSION_NAME,
    val versionCode: Int = APP_STATE_VERSION_CODE,
    val profileName: String = "",
    val avatarPreference: ObserverAvatarPreference = ObserverAvatarPreference.RANDOM,
) {
    companion object {
        /**
         * Decode persisted profile state for a known storage version.
         *
         * @param jsonObject The parsed JSON object from the profile bucket.
         * @param version The version metadata read from that JSON object.
         */
        fun decodeJson(jsonObject: JsonObject, version: PersistedAppVersion): PersistedProfile? {
            // Placeholder for future version-specific decoding/migration into the current model.
            return when {
                version.versionCode == APP_STATE_VERSION_CODE -> decodeCurrentJson(jsonObject)
                else -> null
            }
        }

        /**
         * Decode current-version profile JSON, returning null when the bucket is corrupt.
         *
         * @param jsonObject The parsed profile JSON object.
         */
        private fun decodeCurrentJson(jsonObject: JsonObject): PersistedProfile? {
            return try {
                appStateJson.decodeFromJsonElement(jsonObject)
            } catch (_: RuntimeException) {
                null
            }
        }
    }
}

@Serializable
internal data class PersistedSettings(
    val versionName: String = APP_STATE_VERSION_NAME,
    val versionCode: Int = APP_STATE_VERSION_CODE,
    val automaticallyAdvanceCountdowns: Boolean = true,
    val automaticallyLockLivePoint: Boolean = true,
    val timingAlertPreferences: TimingAlertPreferences = TimingAlertPreferences(),
) {
    companion object {
        /**
         * Decode persisted settings state for a known storage version.
         *
         * @param jsonObject The parsed JSON object from the settings bucket.
         * @param version The version metadata read from that JSON object.
         */
        fun decodeJson(jsonObject: JsonObject, version: PersistedAppVersion): PersistedSettings? {
            // Placeholder for future version-specific decoding/migration into the current model.
            return when {
                version.versionCode == APP_STATE_VERSION_CODE -> decodeCurrentJson(jsonObject)
                else -> null
            }
        }

        /**
         * Decode current-version settings JSON, returning null when the bucket is corrupt.
         *
         * @param jsonObject The parsed settings JSON object.
         */
        private fun decodeCurrentJson(jsonObject: JsonObject): PersistedSettings? {
            return try {
                appStateJson.decodeFromJsonElement(jsonObject)
            } catch (_: RuntimeException) {
                null
            }
        }
    }
}

@Serializable
internal data class ArchivedGame(
    val state: LiveGameState,
    val subtitle: String,
    val versionName: String = APP_STATE_VERSION_NAME,
    val versionCode: Int = APP_STATE_VERSION_CODE,
) {
    companion object {
        /**
         * Decode an archived game summary for a known storage version.
         *
         * @param jsonObject The parsed JSON object from one archived-game file.
         * @param version The version metadata read from that JSON object.
         */
        fun decodeJson(jsonObject: JsonObject, version: PersistedAppVersion): ArchivedGame? {
            // Placeholder for future version-specific decoding/migration into the current model.
            return when {
                version.versionCode == APP_STATE_VERSION_CODE -> decodeCurrentJson(jsonObject)
                else -> null
            }
        }

        /**
         * Decode current-version archived-game JSON, returning null when the file is corrupt.
         *
         * @param jsonObject The parsed archived-game JSON object.
         */
        private fun decodeCurrentJson(jsonObject: JsonObject): ArchivedGame? {
            return try {
                appStateJson.decodeFromJsonElement(jsonObject)
            } catch (_: RuntimeException) {
                null
            }
        }
    }
}

internal enum class PersistedDataArea(val label: String) {
    GAME_STATE("Current Game"),
    PROFILE("Profile"),
    SETTINGS("Settings"),
    PREVIOUS_GAMES("Previous Games"),
}

internal data class PersistedDataRecoveryNotice(
    val resetAreas: Set<PersistedDataArea>,
) {
    init {
        require(resetAreas.isNotEmpty()) {
            "Persistence recovery notices must name at least one reset area."
        }
    }

    val title: String = "Phone Data Reset"

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

internal interface AppStateStore {
    val resetPersistedDataAreas: Set<PersistedDataArea>

    /// Load the persisted current/setup game bucket, if one exists.
    fun loadCurrentGameState(): PersistedCurrentGameState?

    /**
     * Save the current/setup game bucket.
     *
     * @param state The current-game state snapshot to persist.
     */
    fun saveCurrentGameState(state: PersistedCurrentGameState)

    /// Load the persisted profile bucket, if one exists.
    fun loadProfile(): PersistedProfile?

    /**
     * Save the profile bucket.
     *
     * @param state The profile state snapshot to persist.
     */
    fun saveProfile(state: PersistedProfile)

    /// Load the persisted settings bucket, if one exists.
    fun loadSettings(): PersistedSettings?

    /**
     * Save the settings bucket.
     *
     * @param state The settings state snapshot to persist.
     */
    fun saveSettings(state: PersistedSettings)

    /// Load all readable archived-game summaries.
    fun loadArchivedGames(): List<ArchivedGame>

    /**
     * Save the archived-game summaries.
     *
     * @param games The ordered archived-game summaries to persist.
     */
    fun saveArchivedGames(games: List<ArchivedGame>)
}

internal object NoOpAppStateStore : AppStateStore {
    override val resetPersistedDataAreas: Set<PersistedDataArea> = emptySet()

    /// Load no current-game state for in-memory/no-persistence runs.
    override fun loadCurrentGameState(): PersistedCurrentGameState? = null

    /// Ignore current-game saves for in-memory/no-persistence runs.
    override fun saveCurrentGameState(state: PersistedCurrentGameState) = Unit

    /// Load no profile state for in-memory/no-persistence runs.
    override fun loadProfile(): PersistedProfile? = null

    /// Ignore profile saves for in-memory/no-persistence runs.
    override fun saveProfile(state: PersistedProfile) = Unit

    /// Load no settings state for in-memory/no-persistence runs.
    override fun loadSettings(): PersistedSettings? = null

    /// Ignore settings saves for in-memory/no-persistence runs.
    override fun saveSettings(state: PersistedSettings) = Unit

    /// Load no archived games for in-memory/no-persistence runs.
    override fun loadArchivedGames(): List<ArchivedGame> = emptyList()

    /// Ignore archived-game saves for in-memory/no-persistence runs.
    override fun saveArchivedGames(games: List<ArchivedGame>) = Unit
}

private val appStateJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
    prettyPrint = true
}

internal class FileAppStateStore(
    private val rootDir: File,
    // Keep file moves injectable so tests can force platform-specific fallback paths.
    private val moveFileAtomically: (File, File) -> Unit = ::moveFileAtomically,
    private val replaceFile: (File, File) -> Unit = ::replaceFile,
) : AppStateStore {
    private val currentGameStateFile = File(rootDir, "current_game_state.json")
    private val profileFile = File(rootDir, "profile.json")
    private val settingsFile = File(rootDir, "settings.json")
    private val archivedGamesDir = File(rootDir, "archived_games")
    private val resetAreas = mutableSetOf<PersistedDataArea>()

    override val resetPersistedDataAreas: Set<PersistedDataArea>
        get() = resetAreas.toSet()

    /// Load current/setup game state from app-private JSON.
    override fun loadCurrentGameState(): PersistedCurrentGameState? {
        resetAreas.remove(PersistedDataArea.GAME_STATE)
        if (!currentGameStateFile.exists()) {
            return null
        }
        val currentGameState = readExistingJsonObject(currentGameStateFile)
            ?.let { storedCurrentGameState ->
                readVersion(storedCurrentGameState)
                    ?.let { version -> PersistedCurrentGameState.decodeJson(storedCurrentGameState, version) }
            }
        if (currentGameState == null) {
            // We get here if:
            // - json file exists but the read failed
            // - the version number could not be read from the json object
            // - our decode function didn't know how to handle that version number
            // - our decode function had an error decoding the json object
            resetAreas += PersistedDataArea.GAME_STATE
            return PersistedCurrentGameState()
        }
        return currentGameState
    }

    /**
     * Save current/setup game state to app-private JSON.
     *
     * @param state The current-game state snapshot to persist.
     */
    override fun saveCurrentGameState(state: PersistedCurrentGameState) {
        rootDir.mkdirs()
        currentGameStateFile.writeAtomically(appStateJson.encodeToString(state), moveFileAtomically, replaceFile)
    }

    /// Load profile state from app-private JSON.
    override fun loadProfile(): PersistedProfile? {
        resetAreas.remove(PersistedDataArea.PROFILE)
        if (!profileFile.exists()) {
            return null
        }
        val profile = readExistingJsonObject(profileFile)
            ?.let { storedProfile ->
                readVersion(storedProfile)
                    ?.let { version -> PersistedProfile.decodeJson(storedProfile, version) }
            }
        if (profile == null) {
            resetAreas += PersistedDataArea.PROFILE
            return PersistedProfile()
        }
        return profile
    }

    /**
     * Save profile state to app-private JSON.
     *
     * @param state The profile state snapshot to persist.
     */
    override fun saveProfile(state: PersistedProfile) {
        rootDir.mkdirs()
        profileFile.writeAtomically(appStateJson.encodeToString(state), moveFileAtomically, replaceFile)
    }

    /// Load settings state from app-private JSON.
    override fun loadSettings(): PersistedSettings? {
        resetAreas.remove(PersistedDataArea.SETTINGS)
        if (!settingsFile.exists()) {
            return null
        }
        val settings = readExistingJsonObject(settingsFile)
            ?.let { storedSettings ->
                readVersion(storedSettings)
                    ?.let { version -> PersistedSettings.decodeJson(storedSettings, version) }
            }
        if (settings == null) {
            resetAreas += PersistedDataArea.SETTINGS
            return PersistedSettings()
        }
        return settings
    }

    /**
     * Save settings state to app-private JSON.
     *
     * @param state The settings state snapshot to persist.
     */
    override fun saveSettings(state: PersistedSettings) {
        rootDir.mkdirs()
        settingsFile.writeAtomically(appStateJson.encodeToString(state), moveFileAtomically, replaceFile)
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
    private fun readVersion(jsonObject: JsonObject): PersistedAppVersion? {
        return try {
            val versionName = appStateJson.decodeFromJsonElement<String>(
                jsonObject["versionName"] ?: return null
            )
            val versionCode = appStateJson.decodeFromJsonElement<Int>(
                jsonObject["versionCode"] ?: return null
            )
            PersistedAppVersion(versionName = versionName, versionCode = versionCode)
        } catch (_: RuntimeException) {
            null
        }
    }

    /// Load all readable archived-game summaries from app-private JSON files.
    override fun loadArchivedGames(): List<ArchivedGame> {
        resetAreas.remove(PersistedDataArea.PREVIOUS_GAMES)
        if (!archivedGamesDir.exists()) {
            return emptyList()
        }
        val archiveFiles = archivedGamesDir.listFiles { file -> file.isFile && file.extension == "json" }
            ?: return emptyList()
        return archiveFiles
            .sortedBy { it.name }
            .mapNotNull { file ->
                val archivedGame = readExistingJsonObject(file)
                    ?.let { storedArchivedGame ->
                        readVersion(storedArchivedGame)
                            ?.let { version -> ArchivedGame.decodeJson(storedArchivedGame, version) }
                    }
                if (archivedGame == null) {
                    resetAreas += PersistedDataArea.PREVIOUS_GAMES
                }
                archivedGame
            }
    }

    /**
     * Replace the archived-game JSON directory with the supplied ordered summaries.
     *
     * @param games The archived-game summaries to write as numbered JSON files.
     */
    override fun saveArchivedGames(games: List<ArchivedGame>) {
        archivedGamesDir.mkdirs()
        archivedGamesDir
            .listFiles { file -> file.isFile && file.extension == "json" }
            ?.forEach { file -> Files.delete(file.toPath()) }
        games.forEachIndexed { index, game ->
            File(archivedGamesDir, "%05d.json".format(index))
                .writeAtomically(appStateJson.encodeToString(game), moveFileAtomically, replaceFile)
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
