package rmjarvis.ultiobserver

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * Stored archived game summary and its completed game state.
 *
 * @param state The completed game state saved for read-only review.
 * @param subtitle Optional archive-list context such as why an active game was closed.
 */
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
        fun decodeJson(jsonObject: JsonObject, version: AppVersion): ArchivedGame? {
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
