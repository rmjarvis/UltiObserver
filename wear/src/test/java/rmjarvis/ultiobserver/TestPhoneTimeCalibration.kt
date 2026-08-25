package rmjarvis.ultiobserver

import org.junit.Assert.assertEquals
import org.junit.Test

/** Tests for estimating the difference between the phone and watch clocks. */
class TestPhoneTimeCalibration {
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
    }
}
