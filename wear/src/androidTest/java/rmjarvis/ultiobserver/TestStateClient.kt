package rmjarvis.ultiobserver

import androidx.test.platform.app.InstrumentationRegistry
import com.google.android.gms.tasks.TaskCompletionSource
import com.google.android.gms.wearable.CapabilityInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import rmjarvis.ultiobserver.wearprotocol.WearRequestAction

/** Tests of the Android transport adapter's asynchronous result delivery. */
class TestStateClient {
    /** Forward API task failures for discovery, startup, and command delivery. */
    @Test
    fun transportFailures() {
        val lookup = TaskCompletionSource<CapabilityInfo>()
        val startup = TaskCompletionSource<ByteArray>()
        val command = TaskCompletionSource<Int>()
        val successes = AtomicInteger()
        val lookupFailures = AtomicInteger()
        val startupFailures = AtomicInteger()
        val commandFailures = AtomicInteger()
        val lookupFailed = CountDownLatch(1)
        val startupFailed = CountDownLatch(1)
        val commandFailed = CountDownLatch(1)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val client = StateClient(
            instrumentation.targetContext,
            onStateReceived = { error("Transport-only test must not start a session") },
            onConnectionStateChanged = { error("Transport-only test must not start a session") },
            lookupPhone = { lookup.task },
            sendStartup = { nodeId, bytes ->
                assertEquals("phone", nodeId)
                assertTrue(bytes.contentEquals(byteArrayOf(1)))
                startup.task
            },
            sendMessage = { nodeId, path, bytes ->
                assertEquals("phone", nodeId)
                assertEquals(WearRequestAction.GOAL.path, path)
                assertTrue(bytes.contentEquals(byteArrayOf(2)))
                command.task
            },
        )

        // Failure to query Wear capabilities is different from a successful empty lookup.
        // Complete the task after registration and verify delivery through the SDK listener.
        client.findPhone(
            onSuccess = { successes.incrementAndGet() },
            onFailure = {
                lookupFailures.incrementAndGet()
                lookupFailed.countDown()
            },
        )
        lookup.setException(IOException("Wear capability service unavailable"))
        assertTrue("Lookup failure was not delivered", lookupFailed.await(10, TimeUnit.SECONDS))
        assertEquals(1, lookupFailures.get())
        assertEquals(0, successes.get())

        // A phone can disappear after discovery but before the startup request completes.
        // The failed request must notify its caller without delivering a startup reply.
        client.requestStartup(
            "phone", byteArrayOf(1),
            onSuccess = { successes.incrementAndGet() },
            onFailure = {
                startupFailures.incrementAndGet()
                startupFailed.countDown()
            },
        )
        startup.setException(IOException("Phone became unreachable during startup"))
        assertTrue("Startup failure was not delivered", startupFailed.await(10, TimeUnit.SECONDS))
        assertEquals(1, startupFailures.get())
        assertEquals(0, successes.get())

        // Command sending can fail immediately, before listener registration. An already-failed
        // SDK task must still deliver the failure, rather than waiting for our command timeout.
        command.setException(IOException("Phone became unreachable before sending"))
        client.sendCommand("phone", WearRequestAction.GOAL.path, byteArrayOf(2)) {
            commandFailures.incrementAndGet()
            commandFailed.countDown()
        }
        assertTrue("Command failure was not delivered", commandFailed.await(10, TimeUnit.SECONDS))
        instrumentation.waitForIdleSync()
        assertEquals(1, commandFailures.get())
        assertEquals(1, lookupFailures.get())
        assertEquals(1, startupFailures.get())
        assertEquals(0, successes.get())
    }
}
