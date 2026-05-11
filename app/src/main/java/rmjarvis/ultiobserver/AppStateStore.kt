package rmjarvis.ultiobserver

import java.io.File
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
    val viewingArchivedGameIndex: Int?,
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
) : AppStateStore {
    private val json = Json {
        encodeDefaults = true
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
        activeStateFile.writeAtomically(json.encodeToString(state))
    }

    override fun loadArchivedGames(): List<ArchivedGame> {
        if (!archivedGamesDir.exists()) {
            return emptyList()
        }
        return archivedGamesDir
            .listFiles { file -> file.isFile && file.extension == "json" }
            ?.sortedBy { it.name }
            ?.map { file -> json.decodeFromString<ArchivedGame>(file.readText()) }
            ?: emptyList()
    }

    override fun saveArchivedGames(games: List<ArchivedGame>) {
        archivedGamesDir.mkdirs()
        archivedGamesDir
            .listFiles { file -> file.isFile && file.extension == "json" }
            ?.forEach { file -> require(file.delete()) { "Could not delete stale archive ${file.name}." } }
        games.forEachIndexed { index, game ->
            File(archivedGamesDir, "%05d.json".format(index)).writeAtomically(json.encodeToString(game))
        }
    }
}

private fun File.writeAtomically(content: String) {
    val tmpFile = File(parentFile, ".$name.tmp")
    tmpFile.writeText(content)
    if (exists()) {
        require(delete()) { "Could not replace $path." }
    }
    require(tmpFile.renameTo(this)) { "Could not move ${tmpFile.path} to $path." }
}
