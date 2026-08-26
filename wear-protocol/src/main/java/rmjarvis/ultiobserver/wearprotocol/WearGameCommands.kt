package rmjarvis.ultiobserver.wearprotocol

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val wearProtocolJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}

/** Action requested through the watch-to-phone command channel. */
enum class WearRequestAction(val path: String) {
    STARTUP("/ultiobserver/startup"),
    GOAL("/ultiobserver/goal"),
    DECISION("/ultiobserver/decision"),
    ;

    companion object {
        /** Return the requested action represented by one Data Layer path. */
        fun fromPath(path: String): WearRequestAction? {
            return entries.firstOrNull { action -> action.path == path }
        }
    }
}

/** Team identity shared by watch action requests without exposing the phone's model types. */
@Serializable
enum class WearTeamId {
    TEAM_ONE,
    TEAM_TWO,
}

/** Request to record a goal against the exact game state displayed by the watch. */
@Serializable
data class WearGoalRequest(
    val stateToken: String,
    val scoringTeam: WearTeamId,
)

/** Observer response to the exact pending decision displayed by the watch. */
@Serializable
data class WearDecisionRequest(
    val stateToken: String,
    val accept: Boolean,
)

/** Game-action result plus the authoritative state after accepting or rejecting the request. */
@Serializable
data class WearGameActionResponse(
    val applied: Boolean,
    val snapshot: WearStateSnapshot,
)

/** Stable JSON codec shared by every phone/watch protocol payload. */
object WearProtocolCodec {
    fun <T> encode(serializer: KSerializer<T>, value: T): ByteArray {
        return wearProtocolJson.encodeToString(serializer, value)
            .encodeToByteArray()
    }

    fun <T> decode(serializer: KSerializer<T>, bytes: ByteArray): T {
        return wearProtocolJson.decodeFromString(
            serializer,
            bytes.decodeToString(),
        )
    }
}
