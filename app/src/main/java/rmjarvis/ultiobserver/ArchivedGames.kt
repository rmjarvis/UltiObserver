package rmjarvis.ultiobserver

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * Stored archived game summary and the optional original game state for recovery.
 *
 * @param state The completed game state saved for read-only review.
 * @param subtitle Optional archive-list context such as why an active game was closed.
 * @param restorableState Original game state to restore when different from the archived summary.
 */
@Serializable
internal data class ArchivedGame(
    val state: GameState,
    val subtitle: String,
    val restorableState: GameState? = null,
    val versionName: String = APP_STATE_VERSION_NAME,
    val versionCode: Int = APP_STATE_VERSION_CODE,
) {
    /// Return the live state that should become current when this archive is restored.
    fun stateForRestore(): GameState {
        return restorableState ?: state
    }

    companion object {
        /**
         * Decode an archived game summary for a known storage version.
         *
         * @param jsonObject The parsed JSON object from one archived-game file.
         * @param version The version metadata read from that JSON object.
         */
        fun decodeJson(jsonObject: JsonObject, version: AppVersion): PersistenceDecodeResult<ArchivedGame>? {
            return try {
                val migrated = migrateArchivedGameJson(jsonObject, version) ?: return null
                val archivedGame = appStateJson.decodeFromJsonElement<ArchivedGame>(migrated.jsonObject)
                PersistenceDecodeResult(
                    value = archivedGame,
                    wasMigrated = migrated.wasMigrated,
                )
            } catch (_: RuntimeException) {
                null
            }
        }
    }
}
