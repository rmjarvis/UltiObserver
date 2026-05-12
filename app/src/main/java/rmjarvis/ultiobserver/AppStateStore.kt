package rmjarvis.ultiobserver

import java.io.File
import java.nio.charset.StandardCharsets
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.StandardCopyOption.ATOMIC_MOVE
import java.nio.file.StandardCopyOption.REPLACE_EXISTING
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

internal const val APP_STATE_VERSION = 1

@Serializable
internal data class PersistedActiveAppState(
    val version: Int = APP_STATE_VERSION,
    val screen: AppScreen,
    val setupState: GameSetupState,
    val liveState: LiveGameState?,
    val setupMode: SetupMode,
    val profileName: String = "",
    val hasSetupDraft: Boolean = false,
    val timingAlertPreferences: TimingAlertPreferences = TimingAlertPreferences(),
)

internal interface AppStateStore {
    fun loadActiveState(): PersistedActiveAppState?
    fun saveActiveState(state: PersistedActiveAppState)
    fun loadArchivedGames(): List<ArchivedGame>
    fun saveArchivedGames(games: List<ArchivedGame>)
}

internal object NoOpAppStateStore : AppStateStore {
    override fun loadActiveState(): PersistedActiveAppState? = null
    override fun saveActiveState(state: PersistedActiveAppState) = Unit
    override fun loadArchivedGames(): List<ArchivedGame> = emptyList()
    override fun saveArchivedGames(games: List<ArchivedGame>) = Unit
}

internal class FileAppStateStore(
    private val rootDir: File,
    // Keep file moves injectable so tests can force platform-specific fallback paths.
    private val moveFileAtomically: (File, File) -> Unit = ::moveFileAtomically,
    private val replaceFile: (File, File) -> Unit = ::replaceFile,
) : AppStateStore {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
        prettyPrint = true
    }
    private val activeStateFile = File(rootDir, "active_app_state.json")
    private val archivedGamesDir = File(rootDir, "archived_games")

    override fun loadActiveState(): PersistedActiveAppState? {
        if (!activeStateFile.exists()) {
            return null
        }
        val state = json.decodeFromString<PersistedActiveAppState>(activeStateFile.readText())
        require(state.version == APP_STATE_VERSION) {
            "Unsupported active app state version ${state.version}."
        }
        return state
    }

    override fun saveActiveState(state: PersistedActiveAppState) {
        rootDir.mkdirs()
        activeStateFile.writeAtomically(json.encodeToString(state), moveFileAtomically, replaceFile)
    }

    override fun loadArchivedGames(): List<ArchivedGame> {
        if (!archivedGamesDir.exists()) {
            return emptyList()
        }
        val archiveFiles = archivedGamesDir.listFiles { file -> file.isFile && file.extension == "json" }
            ?: return emptyList()
        return archiveFiles
            .sortedBy { it.name }
            .map { file -> json.decodeFromString<ArchivedGame>(file.readText()) }
    }

    override fun saveArchivedGames(games: List<ArchivedGame>) {
        archivedGamesDir.mkdirs()
        archivedGamesDir
            .listFiles { file -> file.isFile && file.extension == "json" }
            ?.forEach { file -> Files.delete(file.toPath()) }
        games.forEachIndexed { index, game ->
            File(archivedGamesDir, "%05d.json".format(index))
                .writeAtomically(json.encodeToString(game), moveFileAtomically, replaceFile)
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
