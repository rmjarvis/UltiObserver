package rmjarvis.ultiobserver

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.Node
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import rmjarvis.ultiobserver.wearprotocol.WATCH_VIBRATION_CAPABILITY
import rmjarvis.ultiobserver.wearprotocol.WearProtocolCodec
import rmjarvis.ultiobserver.wearprotocol.WearRequestAction
import rmjarvis.ultiobserver.wearprotocol.WearVibrationRequest
import rmjarvis.ultiobserver.wearprotocol.WearVibrationResponse

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

    /** Exercise vibration acceptance, failure, and timeout paths. */
    @Test
    fun vibrationDelivery() = runBlocking {
        val capability = object : CapabilityInfo {
            override fun getName() = WATCH_VIBRATION_CAPABILITY
            override fun getNodes() = setOf(object : Node {
                override fun getId() = "watch"
                override fun getDisplayName() = "Watch"
                override fun isNearby() = true
            })
        }

        // Forward the exact pulse duration and honor the watch's reply.
        var accepted = true
        val sender = WearVibrationSender(
            findWatch = { Tasks.forResult(capability) },
            sendRequest = { nodeId, bytes ->
                assertEquals("watch", nodeId)
                assertEquals(420L,
                    WearProtocolCodec.decode(WearVibrationRequest.serializer(), bytes).durationMillis)
                Tasks.forResult(WearProtocolCodec.encode(
                    WearVibrationResponse.serializer(), WearVibrationResponse(accepted),
                ))
            },
        )
        assertTrue(sender.vibrate(420L))

        // A watch without usable vibration hardware declines the pulse.
        accepted = false
        assertFalse(sender.vibrate(420L))

        // Discovery and transport failures return control for phone fallback.
        val discoveryFailure = WearVibrationSender(
            findWatch = { Tasks.forException(IllegalStateException("Unavailable")) },
            sendRequest = { _, _ -> error("No watch was found") },
        )
        assertFalse(discoveryFailure.vibrate(420L))
        val sendFailure = WearVibrationSender(
            findWatch = { Tasks.forResult(capability) },
            sendRequest = { _, _ -> Tasks.forException(IllegalStateException("Disconnected")) },
        )
        assertFalse(sendFailure.vibrate(420L))

        // An empty discovery result never submits a vibration request.
        val empty = object : CapabilityInfo {
            override fun getName() = WATCH_VIBRATION_CAPABILITY
            override fun getNodes() = emptySet<Node>()
        }
        assertFalse(WearVibrationSender(
            findWatch = { Tasks.forResult(empty) },
            sendRequest = { _, _ -> error("No watch was found") },
        ).vibrate(420L))

        // An unresponsive watch cannot hold up the phone indefinitely; a late reply is harmless.
        val pending = TaskCompletionSource<ByteArray>()
        assertFalse(WearVibrationSender(
            findWatch = { Tasks.forResult(capability) },
            sendRequest = { _, _ -> pending.task },
        ).vibrate(420L))
        pending.setResult(WearProtocolCodec.encode(
            WearVibrationResponse.serializer(), WearVibrationResponse(true),
        ))
    }

}
