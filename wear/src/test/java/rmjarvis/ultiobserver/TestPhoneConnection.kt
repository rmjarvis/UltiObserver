package rmjarvis.ultiobserver

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import rmjarvis.ultiobserver.wearprotocol.WEAR_PROTOCOL_VERSION
import rmjarvis.ultiobserver.wearprotocol.WearSnapshotStatus
import rmjarvis.ultiobserver.wearprotocol.WearStateSnapshot
import rmjarvis.ultiobserver.wearprotocol.WearStateUpdate
import rmjarvis.ultiobserver.wearprotocol.WearStartupAcknowledgement
import rmjarvis.ultiobserver.wearprotocol.WearCommandAcknowledgement

/** Tests related to startup and synchronizing the connection with the phone. */
class TestPhoneConnection {
    /** Test receiving the two startup reply components (timing and snapshot) in either order. */
    @Test
    fun startupArrivalOrder() {
        // The direct reply arrives first. Calibration alone does not establish a connection;
        // the matching published snapshot supplies the authoritative state.
        val update = WearStateUpdate(
            WearStateSnapshot(
                status = WearSnapshotStatus.NO_ACTIVE_GAME, activeGame = null,
                sessionId = "phone", sequenceNumber = 1,
            ),
            acknowledgement = WearStartupAcknowledgement("startup"),
        )
        val replyFirst = PendingStartup("startup", 900_000L)
        replyFirst.receiveTiming(1_000_000L, 900_120L)
        assertNull(replyFirst.receivedState)
        replyFirst.receiveSnapshot(update)
        assertEquals(ReceivedState(update.snapshot, 99_940L), replyFirst.receivedState)

        // Reverse the order. The snapshot is held until timing arrives; its arrival time does not
        // enter the clock calculation. Both orders produce the same calibrated state.
        val snapshotFirst = PendingStartup("startup", 900_000L)
        snapshotFirst.receiveSnapshot(update)
        assertNull(snapshotFirst.receivedState)
        snapshotFirst.receiveTiming(1_000_000L, 900_120L)
        assertEquals(replyFirst.receivedState, snapshotFirst.receivedState)

        // While waiting for the reply, a phone change can publish a newer snapshot with the retained
        // startup ID. Keep that newer state even if the original publication arrives again later.
        val delayedReply = PendingStartup("startup", 900_000L)
        delayedReply.receiveSnapshot(update)
        val newer = update.copy(snapshot = update.snapshot.copy(
            status = WearSnapshotStatus.DISABLED, sequenceNumber = 2,
        ))
        delayedReply.receiveSnapshot(newer)
        delayedReply.receiveSnapshot(update)
        delayedReply.receiveTiming(1_000_000L, 900_120L)
        assertEquals(ReceivedState(newer.snapshot, 99_940L), delayedReply.receivedState)

        // A retry must not initialize from a previous attempt's publication, or an update that has
        // no startup acknowledgement. Only the retry's own ID can complete its calibrated state.
        val retry = PendingStartup("retry", 900_000L)
        retry.receiveTiming(1_000_000L, 900_120L)
        retry.receiveSnapshot(update)
        retry.receiveSnapshot(update.copy(acknowledgement = null))
        retry.receiveSnapshot(update.copy(acknowledgement = WearCommandAcknowledgement(
            "earlier-command", true, "phone", 1, null,
        )))
        assertNull(retry.receivedState)
        retry.receiveSnapshot(update.copy(acknowledgement = WearStartupAcknowledgement("retry")))
        assertEquals(replyFirst.receivedState, retry.receivedState)
    }

    @Test
    fun phoneTimeCalibration() {
        // A 120 ms round trip compares the returned phone epoch with the watch at its estimated
        // 60 ms midpoint.
        val offset = calibratePhoneClockOffset(
            requestSentAtWatchEpochMillis = 900_000L,
            responseReceivedAtWatchEpochMillis = 900_120L,
            phoneEpochMillis = 1_000_000L,
        )
        assertEquals(99_940L, offset)

        // Adding that offset to the watch clock produces the estimated current phone time.
        assertEquals(1_000_060L, 900_120L + offset)

        // An odd round trip uses the integral millisecond immediately before the half-millisecond
        // midpoint, matching the precision available from the two device clocks.
        assertEquals(
            99_940L,
            calibratePhoneClockOffset(
                requestSentAtWatchEpochMillis = 900_000L,
                responseReceivedAtWatchEpochMillis = 900_121L,
                phoneEpochMillis = 1_000_000L,
            ),
        )

        // The received-state value keeps the decoded snapshot and its calibration together for
        // the UI, while connection states describe the complete handshake lifecycle.
        val snapshot = WearStateSnapshot(
            status = WearSnapshotStatus.NO_ACTIVE_GAME,
            activeGame = null,
        )
        val receivedState = ReceivedState(snapshot, offset)
        assertEquals(snapshot, receivedState.snapshot)
        assertEquals(offset, receivedState.phoneClockOffsetMillis)
        assertEquals(WEAR_PROTOCOL_VERSION, receivedState.snapshot.protocolVersion)
        assertEquals(
            listOf(
                ConnectionState.CONNECTING,
                ConnectionState.CONNECTED,
                ConnectionState.DISCONNECTED,
                ConnectionState.DISABLED,
            ),
            ConnectionState.entries,
        )
    }
}
