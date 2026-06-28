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
        fun decodeJson(jsonObject: JsonObject, version: AppVersion): PersistenceDecodeResult<Profile>? {
            return try {
                val migrated = migrateProfileJson(jsonObject, version) ?: return null
                val profile = appStateJson.decodeFromJsonElement<Profile>(migrated.jsonObject)
                PersistenceDecodeResult(
                    value = profile,
                    wasMigrated = migrated.wasMigrated,
                )
            } catch (_: RuntimeException) {
                null
            }
        }
    }
}
