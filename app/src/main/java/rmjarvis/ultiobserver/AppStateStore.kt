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
        fun decodeJson(jsonObject: JsonObject, version: PersistedAppVersion): PersistedCurrentGameState? {
            // Placeholder for future version-specific decoding/migration into the current model.
            return when {
                version.versionCode == APP_STATE_VERSION_CODE -> decodeCurrentJson(jsonObject)
                else -> null
            }
        }

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
) {
    companion object {
        fun decodeJson(jsonObject: JsonObject, version: PersistedAppVersion): PersistedProfile? {
            // Placeholder for future version-specific decoding/migration into the current model.
            return when {
                version.versionCode == APP_STATE_VERSION_CODE -> decodeCurrentJson(jsonObject)
                else -> null
            }
        }

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
    val timingAlertPreferences: TimingAlertPreferences = TimingAlertPreferences(),
) {
    companion object {
        fun decodeJson(jsonObject: JsonObject, version: PersistedAppVersion): PersistedSettings? {
            // Placeholder for future version-specific decoding/migration into the current model.
            return when {
                version.versionCode == APP_STATE_VERSION_CODE -> decodeCurrentJson(jsonObject)
                else -> null
            }
        }

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
        fun decodeJson(jsonObject: JsonObject, version: PersistedAppVersion): ArchivedGame? {
            // Placeholder for future version-specific decoding/migration into the current model.
            return when {
                version.versionCode == APP_STATE_VERSION_CODE -> decodeCurrentJson(jsonObject)
                else -> null
            }
        }

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

    fun loadCurrentGameState(): PersistedCurrentGameState?
    fun saveCurrentGameState(state: PersistedCurrentGameState)
    fun loadProfile(): PersistedProfile?
    fun saveProfile(state: PersistedProfile)
    fun loadSettings(): PersistedSettings?
    fun saveSettings(state: PersistedSettings)
    fun loadArchivedGames(): List<ArchivedGame>
    fun saveArchivedGames(games: List<ArchivedGame>)
}

internal object NoOpAppStateStore : AppStateStore {
    override val resetPersistedDataAreas: Set<PersistedDataArea> = emptySet()

    override fun loadCurrentGameState(): PersistedCurrentGameState? = null
    override fun saveCurrentGameState(state: PersistedCurrentGameState) = Unit
    override fun loadProfile(): PersistedProfile? = null
    override fun saveProfile(state: PersistedProfile) = Unit
    override fun loadSettings(): PersistedSettings? = null
    override fun saveSettings(state: PersistedSettings) = Unit
    override fun loadArchivedGames(): List<ArchivedGame> = emptyList()
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

    override fun saveCurrentGameState(state: PersistedCurrentGameState) {
        rootDir.mkdirs()
        currentGameStateFile.writeAtomically(appStateJson.encodeToString(state), moveFileAtomically, replaceFile)
    }

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

    override fun saveProfile(state: PersistedProfile) {
        rootDir.mkdirs()
        profileFile.writeAtomically(appStateJson.encodeToString(state), moveFileAtomically, replaceFile)
    }

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

    override fun saveSettings(state: PersistedSettings) {
        rootDir.mkdirs()
        settingsFile.writeAtomically(appStateJson.encodeToString(state), moveFileAtomically, replaceFile)
    }

    private fun readExistingJsonObject(file: File): JsonObject? {
        return try {
            appStateJson.parseToJsonElement(file.readText()).jsonObject
        } catch (_: IOException) {
            null
        } catch (_: RuntimeException) {
            null
        }
    }

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

private fun moveFileAtomically(source: File, target: File) {
    Files.move(source.toPath(), target.toPath(), ATOMIC_MOVE, REPLACE_EXISTING)
}

private fun replaceFile(source: File, target: File) {
    Files.move(source.toPath(), target.toPath(), REPLACE_EXISTING)
}
