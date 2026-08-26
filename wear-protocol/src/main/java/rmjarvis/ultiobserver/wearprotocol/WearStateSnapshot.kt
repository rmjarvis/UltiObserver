package rmjarvis.ultiobserver.wearprotocol

import kotlinx.serialization.Serializable

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
    val backgroundArgb: Long,
    val contentArgb: Long,
    val actions: WearTeamActionsSnapshot,
)

/** Optional mixed-division ratio badge for the current point. */
@Serializable
data class WearRatioSnapshot(
    val label: String,
    val backgroundArgb: Long,
    val contentArgb: Long,
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

/** Phone-owned prompt and controls for one decision awaiting an observer response. */
@Serializable
data class WearDecisionSnapshot(
    val title: String,
    val messageLines: List<WearGuidanceLineSnapshot>,
    val confirmLabel: String,
    val dismissLabel: String,
    val presentation: WearGuidancePresentation,
    val autoAcceptDelayMillis: Long?,
)

/** Complete active-game display state rendered by the watch. */
@Serializable
data class WearActiveGameSnapshot(
    val stateToken: String,
    val actionsAvailable: Boolean,
    val gameOver: Boolean = false,
    val officialClockOffsetMillis: Long,
    val officialTimeZoneId: String,
    val capLabel: String?,
    val capTargetEpochMillis: Long?,
    val countdown: WearCountdownSnapshot?,
    val teamOne: WearTeamSnapshot,
    val teamTwo: WearTeamSnapshot,
    val pullDirection: WearSnapshotPullDirection,
    val ratio: WearRatioSnapshot?,
    val undoDescription: String?,
    val pendingDecision: WearDecisionSnapshot?,
)

/**
 * Current phone-owned state synchronized to the companion watch.
 *
 * Timing values use absolute phone epochs. The watch compares them with its calibrated estimate
 * of current phone time, so the phone does not publish once per second.
 */
@Serializable
data class WearStateSnapshot(
    val protocolVersion: Int = WEAR_PROTOCOL_VERSION,
    val status: WearSnapshotStatus,
    val activeGame: WearActiveGameSnapshot?,
)

/** Live phone response used to initialize one run of the watch companion. */
@Serializable
data class WearStartupResponse(
    val phoneEpochMillis: Long,
    val snapshot: WearStateSnapshot,
)
