package rmjarvis.ultiobserver.wearprotocol

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import rmjarvis.ultiobserver.CardType
import rmjarvis.ultiobserver.PlayerIdentity
import rmjarvis.ultiobserver.PullViolationType
import rmjarvis.ultiobserver.TeamId

private val wearProtocolJson = Json {
    encodeDefaults = true
    ignoreUnknownKeys = true
}

/** Action requested through the watch-to-phone command channel. */
enum class WearRequestAction(val path: String) {
    STARTUP("/ultiobserver/startup"),
    GOAL("/ultiobserver/goal"),
    UNDO("/ultiobserver/undo"),
    COUNTDOWN("/ultiobserver/countdown"),
    DECISION("/ultiobserver/decision"),
    TEAM_ACTION("/ultiobserver/team-action"),
    CONFIRM_ACTION("/ultiobserver/confirm-action"),
    CARD_ENTRY("/ultiobserver/card-entry"),
    CANCEL_CARD_ENTRY("/ultiobserver/cancel-card-entry"),
    ;

    companion object {
        /** Return the requested action represented by one Data Layer path. */
        fun fromPath(path: String): WearRequestAction? {
            return entries.firstOrNull { action -> action.path == path }
        }
    }
}

/** Request to record a goal against the exact game state displayed by the watch. */
@Serializable
data class WearGoalRequest(
    val stateToken: String,
    val scoringTeam: TeamId,
)

/** Request to undo the latest action against the exact game state displayed by the watch. */
@Serializable
data class WearUndoRequest(
    val stateToken: String,
)

/** Action replacing the countdown on the phone and watch. */
@Serializable
enum class WearCountdownAction(val label: String) {
    START_MISCONDUCT("Start misconduct countdown"),
    RESTART_PULL("Restart countdown"),
}

/** Request the countdown action shown against the exact game state displayed by the watch. */
@Serializable
data class WearCountdownActionRequest(
    val stateToken: String,
    val action: WearCountdownAction,
)

/** Observer response to the exact pending decision displayed by the watch. */
@Serializable
data class WearDecisionRequest(
    val stateToken: String,
    val accept: Boolean,
)

/** Team action whose phone-owned confirmation should be shown on the watch. */
@Serializable
sealed interface WearTeamAction {
    @Serializable
    data object Timeout : WearTeamAction

    @Serializable
    data object TimeViolation : WearTeamAction

    @Serializable
    data object PullViolation : WearTeamAction

    @Serializable
    data object BlueCard : WearTeamAction

    @Serializable
    data object TechnicalFoul : WearTeamAction

    /** Yellow or red card requested with only a player number. */
    @Serializable
    data class PlayerCard(
        val cardType: CardType,
        val jerseyNumber: String,
    ) : WearTeamAction
}

/** Request one team action against the exact game state displayed by the watch. */
@Serializable
data class WearTeamActionRequest(
    val stateToken: String,
    val team: TeamId,
    val action: WearTeamAction,
)

/** One selectable pull-violation confirmation supplied by the phone. */
@Serializable
data class WearPullViolationOption(
    val violation: PullViolationType,
    val prompt: WearPromptSnapshot,
)

/** Prompt returned after a team action is selected on the watch. */
@Serializable
sealed interface WearTeamActionPrompt {
    val stateToken: String
    val prompt: WearPromptSnapshot

    /** Notice that the watch can dismiss without sending another request to the phone. */
    @Serializable
    data class Notice(
        override val stateToken: String,
        override val prompt: WearPromptSnapshot,
    ) : WearTeamActionPrompt
}

/** Phone-owned action context and prompt presented before the watch applies an action. */
@Serializable
sealed interface WearActionConfirmation : WearTeamActionPrompt {

    /** Confirmation details needed to apply a timeout after the observer selects OK. */
    @Serializable
    data class Timeout(
        override val stateToken: String,
        val team: TeamId,
        val requestedAtPhoneEpochMillis: Long,
        override val prompt: WearPromptSnapshot,
    ) : WearActionConfirmation

    /** Confirmation details for a time violation. */
    @Serializable
    data class TimeViolation(
        override val stateToken: String,
        val team: TeamId,
        val requestedAtPhoneEpochMillis: Long,
        override val prompt: WearPromptSnapshot,
    ) : WearActionConfirmation

    /** Confirmation details and selectable mixed-division alternatives for a pull violation. */
    @Serializable
    data class PullViolation(
        override val stateToken: String,
        val team: TeamId,
        val requestedAtPhoneEpochMillis: Long,
        val selectedViolation: PullViolationType,
        val options: List<WearPullViolationOption>,
        override val prompt: WearPromptSnapshot,
    ) : WearActionConfirmation

    /** Confirmation details for a blue card. */
    @Serializable
    data class BlueCard(
        override val stateToken: String,
        val team: TeamId,
        val requestedAtPhoneEpochMillis: Long,
        override val prompt: WearPromptSnapshot,
    ) : WearActionConfirmation

    /** Confirmation details for a technical foul. */
    @Serializable
    data class TechnicalFoul(
        override val stateToken: String,
        val team: TeamId,
        val requestedAtPhoneEpochMillis: Long,
        override val prompt: WearPromptSnapshot,
    ) : WearActionConfirmation

    /** Confirmation for a numbered yellow or red card. */
    @Serializable
    data class PlayerCard(
        override val stateToken: String,
        val team: TeamId,
        val cardType: CardType,
        val identity: PlayerIdentity,
        val requestedAtPhoneEpochMillis: Long,
        override val prompt: WearPromptSnapshot,
    ) : WearActionConfirmation

    /** Prompt handing ambiguous or otherwise incomplete player-card entry to the phone. */
    @Serializable
    data class CardEntryHandoff(
        override val stateToken: String,
        val team: TeamId,
        val cardType: CardType,
        val jerseyNumber: String,
        override val prompt: WearPromptSnapshot,
    ) : WearActionConfirmation
}

/** Request to apply the exact action confirmation accepted on the watch. */
@Serializable
data class WearConfirmActionRequest(
    val confirmation: WearActionConfirmation,
)

/** Request player-card entry on the phone for a color selected on the watch. */
@Serializable
data class WearCardEntryRequest(
    val stateToken: String,
    val team: TeamId,
    val cardType: CardType,
    val jerseyNumber: String,
)

/** Cancel the exact player-card entry currently active on the phone. */
@Serializable
data class WearCancelCardEntryRequest(
    val stateToken: String,
    val team: TeamId,
    val cardType: CardType?,
    val jerseyNumber: String,
)

/** Game-action result, authoritative state, and any transient prompt to show next. */
@Serializable
data class WearGameActionResponse(
    val applied: Boolean,
    val snapshot: WearStateSnapshot,
    val nextPrompt: WearTeamActionPrompt?,
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
