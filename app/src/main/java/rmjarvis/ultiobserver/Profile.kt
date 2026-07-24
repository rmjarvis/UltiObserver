package rmjarvis.ultiobserver

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement

/**
 * Observer profile information stored as one persistence bucket.
 *
 * @param name The observer name entered in Profile.
 * @param avatarPreference The preferred Home-screen observer avatar, or random.
 */
@Serializable
internal data class Profile(
    val name: String = "",
    val avatarPreference: ObserverAvatarPreference = ObserverAvatarPreference.RANDOM,
) {
    /**
     * Return this profile with the observer name replaced.
     *
     * @param updatedName The profile name entered by the user.
     */
    fun withName(updatedName: String): Profile {
        return copy(name = updatedName)
    }

    /**
     * Return this profile with the preferred observer avatar replaced.
     *
     * @param updatedPreference The newly selected avatar preference.
     */
    fun withAvatarPreference(updatedPreference: ObserverAvatarPreference): Profile {
        return copy(avatarPreference = updatedPreference)
    }

    companion object {
        /**
         * Decode persisted profile state for a known storage version.
         *
         * @param jsonElement The parsed payload JSON from the profile bucket.
         * @param version The version metadata read from that JSON object.
         */
        fun decodeJson(
            jsonElement: JsonElement,
            version: AppVersion,
        ): PersistenceDecodeResult<Profile>? {
            return try {
                val migrated = migrateProfileJson(jsonElement, version) ?: return null
                val profile = appStateJson.decodeFromJsonElement<Profile>(migrated.jsonElement)
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
