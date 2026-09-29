package rmjarvis.ultiobserver

import android.os.Build
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.MessageEvent
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.mock
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

        // The manifest accepts our whole path prefix; unknown messages must be ignored.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.P) return
        val message = mock(MessageEvent::class.java)
        doReturn("/ultiobserver/unknown").`when`(message).path
        service.onMessageReceived(message)
    }

    /** Exercise vibration acceptance, failure, and timeout paths. */
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.P) // Mockito requires Android 9 or later.
    fun vibrationDelivery() = runBlocking {
        val watch = mock(Node::class.java)
        doReturn("watch").`when`(watch).id
        doReturn(true).`when`(watch).isNearby
        val capability = mock(CapabilityInfo::class.java)
        doReturn(setOf(watch)).`when`(capability).nodes

        // Discover and contact the watch once for the entire three-pulse sequence.
        var accepted = true
        var discoveries = 0
        var requests = 0
        val sender = WearVibrationSender(
            findWatch = {
                discoveries++
                Tasks.forResult(capability)
            },
            sendRequest = { nodeId, bytes ->
                requests++
                assertEquals("watch", nodeId)
                assertEquals(VibrationPattern(420L, 3, 120L),
                    WearProtocolCodec.decode(WearVibrationRequest.serializer(), bytes).pattern)
                Tasks.forResult(WearProtocolCodec.encode(
                    WearVibrationResponse.serializer(), WearVibrationResponse(accepted),
                ))
            },
        )
        assertTrue(sender.vibrate(VibrationPattern(420L, 3, 120L)))
        assertEquals(1, discoveries)
        assertEquals(1, requests)

        // A watch without usable vibration hardware declines the pulse.
        accepted = false
        assertFalse(sender.vibrate(VibrationPattern(420L, 3, 120L)))

        // Discovery and transport failures return control for phone fallback.
        val discoveryFailure = WearVibrationSender(
            findWatch = { Tasks.forException(IllegalStateException("Unavailable")) },
            sendRequest = { _, _ -> error("No watch was found") },
        )
        assertFalse(discoveryFailure.vibrate(VibrationPattern(420L, 3, 120L)))
        val sendFailure = WearVibrationSender(
            findWatch = { Tasks.forResult(capability) },
            sendRequest = { _, _ -> Tasks.forException(IllegalStateException("Disconnected")) },
        )
        assertFalse(sendFailure.vibrate(VibrationPattern(420L, 3, 120L)))

        // A cancelled transport task also returns control for phone fallback.
        val cancelled = WearVibrationSender(
            findWatch = { Tasks.forResult(capability) },
            sendRequest = { _, _ -> Tasks.forCanceled() },
        )
        assertFalse(cancelled.vibrate(VibrationPattern(420L, 3, 120L)))

        // An empty discovery result never submits a vibration request.
        val empty = mock(CapabilityInfo::class.java)
        doReturn(emptySet<Node>()).`when`(empty).nodes
        assertFalse(WearVibrationSender(
            findWatch = { Tasks.forResult(empty) },
            sendRequest = { _, _ -> error("No watch was found") },
        ).vibrate(VibrationPattern(420L, 3, 120L)))

        // An unresponsive watch cannot hold up the phone indefinitely; a late reply is harmless.
        val pending = TaskCompletionSource<ByteArray>()
        assertFalse(WearVibrationSender(
            findWatch = { Tasks.forResult(capability) },
            sendRequest = { _, _ -> pending.task },
        ).vibrate(VibrationPattern(420L, 3, 120L)))
        pending.setResult(WearProtocolCodec.encode(
            WearVibrationResponse.serializer(), WearVibrationResponse(true),
        ))

        // Discovery can time out before finding a watch, allowing phone fallback.
        val pendingDiscovery = TaskCompletionSource<CapabilityInfo>()
        var lateRequests = 0
        val slowDiscovery = WearVibrationSender(
            findWatch = { pendingDiscovery.task },
            sendRequest = { _, _ ->
                lateRequests++
                Tasks.forResult(WearProtocolCodec.encode(
                    WearVibrationResponse.serializer(), WearVibrationResponse(true),
                ))
            },
        )
        assertFalse(slowDiscovery.vibrate(VibrationPattern(420L, 3, 120L)))

        // A watch found after that timeout must not receive a duplicate vibration request.
        pendingDiscovery.setResult(capability)
        InstrumentationRegistry.getInstrumentation().waitForIdleSync()
        assertEquals(0, lateRequests)
    }

}
