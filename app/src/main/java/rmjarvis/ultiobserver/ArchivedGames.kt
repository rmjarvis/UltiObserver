package rmjarvis.ultiobserver

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * Category describing why a game is stored outside the current-game slot.
 *
 * @param displayText User-facing category label.
 * @param emptyText User-facing empty-state text for the category list.
 */
@Serializable
internal enum class ArchivedGameCategory(
    val displayText: String,
    val emptyText: String,
) {
    COMPLETED("Archived games", "No completed games yet."),
    IN_PROGRESS("Saved in-progress games", "No saved in-progress games."),
    SETUP("Saved setup states", "No saved setup states."),
}

/**
 * Stored archived game state and summary context.
 *
 * @param state The game state shown in archive lists and read-only summaries.
 * @param summaryContext Optional context such as why an active game was saved.
 */
@Serializable
internal data class ArchivedGame(
    val state: GameState,
    val summaryContext: String,
) {
    /// Return the archive category implied by the stored game phase.
    val category: ArchivedGameCategory
        get() = when (state.phase) {
            GamePhase.SETUP -> ArchivedGameCategory.SETUP
            GamePhase.GAME_OVER -> ArchivedGameCategory.COMPLETED
            else -> ArchivedGameCategory.IN_PROGRESS
        }

    /**
     * Return this in-progress archive converted to a completed read-only archive.
     *
     * @param now The epoch millis to store as the manual game-over time.
     */
    fun asCompletedArchive(now: Long): ArchivedGame {
        return ArchivedGame(
            state = state.endGameNow(now).pruneUndoHistory(),
            summaryContext = "",
        )
    }

    companion object {
        /**
         * Decode an archived game summary for a known storage version.
         *
         * @param jsonElement The parsed payload JSON from one archived-game file.
         * @param version The version metadata read from that JSON object.
         */
        fun decodeJson(
            jsonElement: JsonElement,
            version: AppVersion,
        ): PersistenceDecodeResult<ArchivedGame>? {
            return try {
                val migrated = migrateArchivedGameJson(jsonElement, version) ?: return null
                val archivedGame = appStateJson.decodeFromJsonElement<ArchivedGame>(
                    migrated.jsonElement,
                )
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
