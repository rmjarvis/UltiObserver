package rmjarvis.ultiobserver

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

@Serializable
internal data class Settings(
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
        fun decodeJson(jsonObject: JsonObject, version: AppVersion): Settings? {
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
        private fun decodeCurrentJson(jsonObject: JsonObject): Settings? {
            return try {
                appStateJson.decodeFromJsonElement(jsonObject)
            } catch (_: RuntimeException) {
                null
            }
        }
    }
}
