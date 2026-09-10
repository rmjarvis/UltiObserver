package rmjarvis.ultiobserver

import org.junit.Assert.assertEquals
import org.junit.Test
import rmjarvis.ultiobserver.wearprotocol.WEAR_PROTOCOL_VERSION
import rmjarvis.ultiobserver.wearprotocol.WearSnapshotStatus
import rmjarvis.ultiobserver.wearprotocol.WearStateSnapshot

/** Tests for estimating the difference between the phone and watch clocks. */
class TestPhoneConnection {
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
            ),
            ConnectionState.entries,
        )
    }
}
