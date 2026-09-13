package rmjarvis.ultiobserver

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test
import org.junit.runner.RunWith
import rmjarvis.ultiobserver.wearprotocol.WearRequestAction

/** Tests for the Android service's Wear transport boundaries. */
@RunWith(AndroidJUnit4::class)
class TestWearOSCommunication {
    /** Reject non-startup paths passed to the request/reply entry point. */
    @Test
    fun requestPaths() {
        // A game command belongs on the one-way message path. Reject it before decoding its bytes
        // or accessing application state; no attached service or paired watch is needed.
        val service = WearOSRequestService()
        val gameCommand = assertThrows(IllegalArgumentException::class.java) {
            service.onRequest("watch", WearRequestAction.GOAL.path, byteArrayOf())
        }
        assertEquals("Only startup uses the request/reply path", gameCommand.message)

        // An unrecognized path violates the same startup-only contract.
        val unknown = assertThrows(IllegalArgumentException::class.java) {
            service.onRequest("watch", "/unknown", byteArrayOf())
        }
        assertEquals("Only startup uses the request/reply path", unknown.message)
    }
}
