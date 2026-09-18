package rmjarvis.ultiobserver

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.google.android.gms.tasks.Task
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import rmjarvis.ultiobserver.wearprotocol.PHONE_STATE_CAPABILITY
import rmjarvis.ultiobserver.wearprotocol.WEAR_STATE_PATH
import rmjarvis.ultiobserver.wearprotocol.WearRequestAction

/** Connect Android Wear listeners and transport to the watch's phone-session controller. */
internal class StateClient(
    context: Context,
    onStateReceived: (ReceivedState) -> Unit,
    onConnectionStateChanged: (ConnectionState) -> Unit,
    // Injectable operations let tests supply controlled Tasks to simulate Wear API failures.
    private val lookupPhone: () -> Task<CapabilityInfo>,
    private val sendStartup: (String, ByteArray) -> Task<ByteArray>,
    private val sendMessage: (String, String, ByteArray) -> Task<Int>,
) : DataClient.OnDataChangedListener, CapabilityClient.OnCapabilityChangedListener, PhoneTransport {
    constructor(
        context: Context,
        onStateReceived: (ReceivedState) -> Unit,
        onConnectionStateChanged: (ConnectionState) -> Unit,
    ) : this(
        context,
        onStateReceived,
        onConnectionStateChanged,
        lookupPhone = {
            Wearable.getCapabilityClient(context.applicationContext)
                .getCapability(PHONE_STATE_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
        },
        sendStartup = { nodeId, bytes ->
            Wearable.getMessageClient(context.applicationContext)
                .sendRequest(nodeId, WearRequestAction.STARTUP.path, bytes)
        },
        sendMessage = { nodeId, path, bytes ->
            Wearable.getMessageClient(context.applicationContext).sendMessage(nodeId, path, bytes)
        },
    )

    private val dataClient = Wearable.getDataClient(context.applicationContext)
    private val capabilityClient = Wearable.getCapabilityClient(context.applicationContext)
    private val handler = Handler(Looper.getMainLooper())
    private val stateUri = Uri.parse("wear://*$WEAR_STATE_PATH")
    val connection = PhoneConnectionController(
        transport = this,
        releaseVersion = BuildConfig.VERSION_NAME,
        clock = { System.currentTimeMillis() },
        onStateReceived = onStateReceived,
        onConnectionStateChanged = onConnectionStateChanged,
    )

    /** Register listeners and begin the watch's live phone session. */
    fun start() {
        dataClient.addListener(this, stateUri, DataClient.FILTER_LITERAL)
        capabilityClient.addListener(this, PHONE_STATE_CAPABILITY)
        connection.start()
    }

    /** Stop pending work and unregister Android listeners. */
    fun stop() {
        connection.stop()
        dataClient.removeListener(this)
        capabilityClient.removeListener(this, PHONE_STATE_CAPABILITY)
    }

    override fun onDataChanged(events: DataEventBuffer) {
        events.forEach { event ->
            // Registration filters the path; deletion events have no payload to receive.
            event.dataItem.data?.let { bytes ->
                connection.receiveStateBytes(bytes)
            }
        }
    }

    override fun onCapabilityChanged(capability: CapabilityInfo) {
        connection.phoneReachabilityChanged(capability.nodes.map { it.phoneNode() })
    }

    override fun findPhone(onSuccess: (List<PhoneNode>) -> Unit, onFailure: () -> Unit) {
        lookupPhone()
            .addOnSuccessListener { capability ->
                onSuccess(capability.nodes.map { it.phoneNode() })
            }
            .addOnFailureListener { onFailure() }
    }

    override fun requestStartup(
        nodeId: String,
        bytes: ByteArray,
        onSuccess: (ByteArray) -> Unit,
        onFailure: () -> Unit,
    ) {
        sendStartup(nodeId, bytes)
            .addOnSuccessListener { reply -> onSuccess(reply) }
            .addOnFailureListener { onFailure() }
    }

    override fun sendCommand(
        nodeId: String,
        path: String,
        bytes: ByteArray,
        onFailure: () -> Unit,
    ) {
        sendMessage(nodeId, path, bytes)
            .addOnFailureListener { onFailure() }
    }

    override fun scheduleTimeout(delayMillis: Long, action: () -> Unit): () -> Unit {
        val runnable = Runnable { action() }
        handler.postDelayed(runnable, delayMillis)
        return { handler.removeCallbacks(runnable) }
    }
}

private fun Node.phoneNode(): PhoneNode = PhoneNode(id, isNearby)
