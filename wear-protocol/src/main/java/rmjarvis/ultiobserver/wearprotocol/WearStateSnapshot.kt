package rmjarvis.ultiobserver.wearprotocol

import java.nio.ByteBuffer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Path of the single current-state item published by the phone. */
const val WEAR_STATE_PATH = "/ultiobserver/current-state"

/** RPC path used by the watch to calibrate against the phone's wall clock. */
const val WEAR_TIME_SYNC_PATH = "/ultiobserver/time-sync"

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

/** Complete active-game display state rendered by the watch. */
@Serializable
data class WearActiveGameSnapshot(
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

/** Stable JSON codec shared by the phone publisher and watch receiver. */
object WearStateSnapshotCodec {
    private val json = Json {
        encodeDefaults = true
        ignoreUnknownKeys = true
    }

    fun encode(snapshot: WearStateSnapshot): ByteArray {
        return json.encodeToString(WearStateSnapshot.serializer(), snapshot).encodeToByteArray()
    }

    fun decode(bytes: ByteArray): WearStateSnapshot {
        return json.decodeFromString(WearStateSnapshot.serializer(), bytes.decodeToString())
    }
}

/** Encode and decode the phone epoch returned by a time-sync request. */
object WearTimeSyncCodec {
    fun encode(phoneEpochMillis: Long): ByteArray {
        return ByteBuffer.allocate(Long.SIZE_BYTES)
            .putLong(phoneEpochMillis)
            .array()
    }

    fun decode(bytes: ByteArray): Long {
        require(bytes.size == Long.SIZE_BYTES) {
            "Expected ${Long.SIZE_BYTES} time-sync bytes, received ${bytes.size}."
        }
        return ByteBuffer.wrap(bytes).long
    }
}
