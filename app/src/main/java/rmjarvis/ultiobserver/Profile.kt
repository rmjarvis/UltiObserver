package rmjarvis.ultiobserver

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * Observer profile information stored as one persistence bucket.
 *
 * @param profileName The observer name entered in Profile.
 * @param avatarPreference The preferred Home-screen observer avatar, or random.
 */
@Serializable
internal data class Profile(
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
        fun decodeJson(jsonObject: JsonObject, version: AppVersion): Profile? {
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
        private fun decodeCurrentJson(jsonObject: JsonObject): Profile? {
            return try {
                appStateJson.decodeFromJsonElement(jsonObject)
            } catch (_: RuntimeException) {
                null
            }
        }
    }
}
