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
    TIMEOUT_PREVIEW("/ultiobserver/timeout-preview"),
    TIMEOUT("/ultiobserver/timeout"),
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

/** Request the phone-owned confirmation for a timeout without changing the game. */
@Serializable
data class WearTimeoutPreviewRequest(
    val stateToken: String,
    val team: WearTeamId,
)

/** Phone-owned action context and prompt presented before the watch applies an action. */
@Serializable
sealed interface WearActionConfirmation {
    val stateToken: String
    val prompt: WearPromptSnapshot

    /** Confirmation details needed to apply a timeout after the observer selects OK. */
    @Serializable
    data class Timeout(
        override val stateToken: String,
        val team: WearTeamId,
        val requestedAtPhoneEpochMillis: Long,
        override val prompt: WearPromptSnapshot,
    ) : WearActionConfirmation
}

/** Request to apply the exact timeout confirmation accepted on the watch. */
@Serializable
data class WearTimeoutRequest(
    val stateToken: String,
    val team: WearTeamId,
    val requestedAtPhoneEpochMillis: Long,
)

/** Game-action result, authoritative state, and any transient confirmation to show next. */
@Serializable
data class WearGameActionResponse(
    val applied: Boolean,
    val snapshot: WearStateSnapshot,
    val confirmation: WearActionConfirmation?,
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
