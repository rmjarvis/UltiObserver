package rmjarvis.ultiobserver

import rmjarvis.ultiobserver.wearprotocol.WearStateSnapshot
import rmjarvis.ultiobserver.wearprotocol.WearStateUpdate

/** Wait for both the startup timing reply and its published snapshot, in either arrival order. */
internal class PendingStartup(
    private val requestId: String,
    private val sentAtWatchEpochMillis: Long,
) {
    private var snapshot: WearStateSnapshot? = null
    private var clockOffsetMillis: Long? = null

    fun receiveTiming(phoneEpochMillis: Long, receivedAtWatchEpochMillis: Long) {
        clockOffsetMillis = calibratePhoneClockOffset(
            sentAtWatchEpochMillis, receivedAtWatchEpochMillis, phoneEpochMillis,
        )
    }

    fun receiveSnapshot(update: WearStateUpdate) {
        val acknowledgement = update.acknowledgement
        if (acknowledgement?.requestId != requestId) return
        val previous = snapshot
        if (previous == null || update.snapshot.sequenceNumber > previous.sequenceNumber) {
            snapshot = update.snapshot
        }
    }

    val receivedState: ReceivedState?
        get() {
            val state = snapshot ?: return null
            val offset = clockOffsetMillis ?: return null
            return ReceivedState(state, offset)
        }
}

/** Snapshot plus the offset needed to display it using the phone's clock. */
internal data class ReceivedState(
    val snapshot: WearStateSnapshot,
    val phoneClockOffsetMillis: Long,
)

/** Current result of the watch app's live handshake with the phone app. */
internal enum class ConnectionState {
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
    DISABLED,
}

/**
 * Estimate the offset between the phone and watch clocks from one startup round trip.
 *
 * The watch sends a startup request to the phone and records both the time that it sent
 * the request and the time it received a response. The assumption is that the midpoint
 * between these two times is the time that the phone responded. Thus, we can calibrate
 * the difference between the phone and watch clocks by using the difference between the
 * reported clock time from the phone and the midpoint of the round trip.
 */
internal fun calibratePhoneClockOffset(
    requestSentAtWatchEpochMillis: Long,
    responseReceivedAtWatchEpochMillis: Long,
    phoneEpochMillis: Long,
): Long {
    val roundTripMillis = responseReceivedAtWatchEpochMillis - requestSentAtWatchEpochMillis
    val watchEpochMillisAtMidpoint = requestSentAtWatchEpochMillis + roundTripMillis / 2L
    return phoneEpochMillis - watchEpochMillisAtMidpoint
}
