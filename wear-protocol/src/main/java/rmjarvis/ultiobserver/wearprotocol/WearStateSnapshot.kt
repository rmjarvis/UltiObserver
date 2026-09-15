package rmjarvis.ultiobserver.wearprotocol

import java.util.UUID
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerialName
import rmjarvis.ultiobserver.CardType
import rmjarvis.ultiobserver.TeamId

/** Path of the single current-state item published by the phone. */
const val WEAR_STATE_PATH = "/ultiobserver/current-state"

/** Capability advertised by an Android phone that can provide UltiObserver game state. */
const val PHONE_STATE_CAPABILITY = "ultiobserver_phone_state"

/** Current wire-format version for phone-to-watch state snapshots. */
const val WEAR_PROTOCOL_VERSION = 1

/** Whether companion support is disabled, idle, or presenting an active game. */
@Serializable
enum class WearSnapshotStatus {
    DISABLED,
    NO_ACTIVE_GAME,
    ACTIVE_GAME,
}

/** Direction of the current pull between the watch's fixed team positions. */
@Serializable
enum class WearSnapshotPullDirection {
    LEFT_TO_RIGHT,
    RIGHT_TO_LEFT,
}

/** One future countdown cue scheduled on the phone's clock. */
@Serializable
data class WearCueSnapshot(
    val message: String,
    val targetEpochMillis: Long,
)

/** Active countdown values needed for independent once-per-second watch rendering. */
@Serializable
data class WearCountdownSnapshot(
    val label: String,
    val targetEpochMillis: Long,
    val pausedAtEpochMillis: Long?,
    val cues: List<WearCueSnapshot>,
)

/** One scheduled change to phone-formatted text shown in the watch countdown area. */
@Serializable
data class WearStatusMessageTransition(
    val targetEpochMillis: Long,
    val message: String?,
)

/** One future cap countdown shown in the watch's top status line. */
@Serializable
data class WearCapSnapshot(
    val label: String,
    val targetEpochMillis: Long,
)

/** Phone-style labels and availability for one team's action screen. */
@Serializable
data class WearTeamActionsSnapshot(
    val timeViolationLabel: String,
    val pullViolationLabel: String,
    val cardLabel: String,
    val technicalFoulLabel: String,
    val timeoutLabel: String,
    val goalEnabled: Boolean,
    val timeViolationEnabled: Boolean,
    val pullViolationEnabled: Boolean,
    val cardEnabled: Boolean,
    val technicalFoulEnabled: Boolean,
    val timeoutEnabled: Boolean,
)

/** One fixed-position watch team and its action-screen values. */
@Serializable
data class WearTeamSnapshot(
    val name: String,
    val score: Int,
    val fieldEndName: String,
    val backgroundArgb: Long,
    val contentArgb: Long,
    val actions: WearTeamActionsSnapshot,
    val nameInfo: List<WearGuidanceLineSnapshot>,
)

/** Optional mixed-division ratio badge for the current point. */
@Serializable
data class WearRatioSnapshot(
    val label: String,
    val backgroundArgb: Long,
    val contentArgb: Long,
)

/** Two colored label segments identifying the team that chooses the point's ratio. */
@Serializable
data class WearRatioChooserSnapshot(
    val team: TeamId,
    val men: WearRatioSnapshot,
    val women: WearRatioSnapshot,
)

/** Phone-selected presentation matching the configured rule-guidance mode. */
@Serializable
enum class WearGuidancePresentation {
    VISIBLE,
    VISIBLE_TIMED,
    HIDDEN_AUTO_ACCEPT,
}

/** One phone-formatted line in a pending watch decision. */
@Serializable
data class WearGuidanceLineSnapshot(
    val text: String,
    val bold: Boolean,
)

/** Phone-owned copy, controls, and presentation for one watch prompt. */
@Serializable
data class WearPromptSnapshot(
    val title: String,
    val messageLines: List<WearGuidanceLineSnapshot>,
    val confirmLabel: String,
    val dismissLabel: String,
    val presentation: WearGuidancePresentation,
    val autoAcceptDelayMillis: Long?,
)

/** Card workflow currently active on the phone, including any number entered on the watch. */
@Serializable
data class WearPhoneCardEntrySnapshot(
    val team: TeamId,
    val cardType: CardType?,
    val jerseyNumber: String,
)

/** One adjustment row followed by an optional point-action button in the watch timing panel. */
@Serializable
data class WearTimingControlsSnapshot(
    val adjustments: List<WearCountdownAction>,
    val pointAction: WearCountdownAction?,
) {
    /** Return every action available in the timing panel, in display order. */
    fun all(): List<WearCountdownAction> = adjustments + listOfNotNull(pointAction)
}

/** Complete active-game display state rendered by the watch. */
@Serializable
data class WearActiveGameSnapshot(
    val stateToken: String,
    val actionsAvailable: Boolean,
    val gameOver: Boolean = false,
    val officialClockOffsetMillis: Long,
    val officialTimeZoneId: String,
    val upcomingCaps: List<WearCapSnapshot> = emptyList(),
    val countdown: WearCountdownSnapshot?,
    val countdownActions: List<WearCountdownAction>,
    val timingControls: WearTimingControlsSnapshot?,
    val statusMessageTransitions: List<WearStatusMessageTransition> = emptyList(),
    val teamOne: WearTeamSnapshot,
    val teamTwo: WearTeamSnapshot,
    val pullDirection: WearSnapshotPullDirection,
    val ratio: WearRatioSnapshot?,
    val ratioChooser: WearRatioChooserSnapshot?,
    val undoDescription: String?,
    val pendingDecision: WearPromptSnapshot?,
    val phoneCardEntry: WearPhoneCardEntrySnapshot?,
)

/**
 * Current phone-owned state synchronized to the companion watch.
 *
 * Timing values use absolute phone epochs. The watch compares them with its calibrated estimate
 * of current phone time, so the phone does not publish once per second.
 * The phone tags transport snapshots with its process-session identity and sequence number;
 * sequence numbers order all published snapshots within that session, including startup state.
 */
@Serializable
data class WearStateSnapshot(
    val protocolVersion: Int = WEAR_PROTOCOL_VERSION,
    val status: WearSnapshotStatus,
    val activeGame: WearActiveGameSnapshot?,
    val sessionId: String = "",
    val sequenceNumber: Long = 0L,
)

/** Identify a fresh request for the phone's current state, including across watch restarts. */
@Serializable
data class WearStartupRequest(val requestId: String)

/** Direct startup reply for connection status and clock calibration, without a game snapshot. */
@Serializable
data class WearStartupResponse(
    val enabled: Boolean,
    val phoneEpochMillis: Long,
    val protocolVersion: Int = WEAR_PROTOCOL_VERSION,
)

/**
 * A helper that tags outgoing snapshots with two bits of metadata:
 *  - sessionId is a unique id for the current session of the app on the phone.
 *  - sequenceNumber is the running number in the sequence of all snapshots sent to the
 *    watch, so we can confirm the order of messages that arrive at the watch.
 *
 * Note: successive identical snapshots do not update the sequenceNumber.
 */
class WearSnapshotTagger {
    private val sessionId = UUID.randomUUID().toString()
    private var previous: WearStateSnapshot? = null

    /**
     * Tag the given snapshot with the necessary metadata.
     *
     * Note: This function must be called atomically, since it mutates an internal variable.
     * We do this using an AppState lock.
     * */
    fun tag(payload: WearStateSnapshot): WearStateSnapshot {
        val last = previous
        val sequenceNumber = last?.sequenceNumber ?: 0L
        val snapshot = payload.copy(sessionId = sessionId, sequenceNumber = sequenceNumber)
        if (snapshot == last) return snapshot
        return snapshot.copy(sequenceNumber = sequenceNumber + 1L).also {
            previous = it
        }
    }
}

/**
 * Track the watch's current phone snapshot, ignoring duplicate or outdated updates.
 *
 * This is the watch-side handler of the tags assigned to the payload by [WearSnapshotTagger].
 *
 * Startup can establish a new phone session. Subsequent updates must belong to that
 * session and have a higher sequence number before replacing the current snapshot.
 */
class WearSnapshotReceiver(initialSnapshot: WearStateSnapshot) {
    var current: WearStateSnapshot = initialSnapshot
        private set

    fun receive(snapshot: WearStateSnapshot): Boolean {
        val previous = current
        if (snapshot.sessionId != previous.sessionId || snapshot.sequenceNumber <= previous.sequenceNumber) {
            return false
        }
        current = snapshot
        return true
    }
}

/** Identify a game command independently of its action-specific encoded arguments. */
@Serializable
data class WearCommandRequest(val requestId: String, val arguments: ByteArray)

/** Acknowledge the latest watch request, whether startup or a game command. */
@Serializable
sealed interface WearAcknowledgement {
    val requestId: String
}

/**
 * Acknowledge the latest startup request.
 */
@Serializable
@SerialName("startup")
data class WearStartupAcknowledgement(
    override val requestId: String,
) : WearAcknowledgement

/**
 * Acknowledge and report the result of the most recently handled watch command.
 * The snapshot identity identifies when transient navigation results were valid; later phone
 * changes may complete the command without allowing its old prompt to replace newer state.
 */
@Serializable
@SerialName("command")
data class WearCommandAcknowledgement(
    override val requestId: String,
    val applied: Boolean,
    private val sessionId: String,
    private val sequenceNumber: Long,
    val nextPrompt: WearTeamActionPrompt?,
) : WearAcknowledgement {
    fun matchesSnapshot(snapshot: WearStateSnapshot): Boolean {
        // The client verifies the phone session before processing its acknowledgement.
        return sequenceNumber == snapshot.sequenceNumber
    }
}

/** Latest phone state and retained acknowledgement, synchronized together at the state DataItem. */
@Serializable
data class WearStateUpdate(
    val snapshot: WearStateSnapshot,
    val acknowledgement: WearAcknowledgement?,
)

/** Match one outstanding command, including across watch restarts and unchanged snapshots. */
class WearPendingCommand {
    private var stateToken: String? = null
    var requestId: String? = null
        private set

    fun begin(stateToken: String): String {
        this.stateToken = stateToken
        return UUID.randomUUID().toString().also { requestId = it }
    }

    fun complete(acknowledgement: WearAcknowledgement?): Boolean {
        if (acknowledgement == null || acknowledgement.requestId != requestId) return false
        clear()
        return true
    }

    /** Stop waiting when authoritative state no longer matches the command's game state. */
    fun supersede(currentStateToken: String?): Boolean {
        if (requestId == null || stateToken == currentStateToken) return false
        clear()
        return true
    }

    fun clear() {
        requestId = null
        stateToken = null
    }
}
