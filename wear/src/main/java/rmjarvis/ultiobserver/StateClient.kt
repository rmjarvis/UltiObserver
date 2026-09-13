package rmjarvis.ultiobserver

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
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
) : DataClient.OnDataChangedListener, CapabilityClient.OnCapabilityChangedListener, PhoneTransport {
    private val dataClient = Wearable.getDataClient(context.applicationContext)
    private val capabilityClient = Wearable.getCapabilityClient(context.applicationContext)
    private val messageClient = Wearable.getMessageClient(context.applicationContext)
    private val handler = Handler(Looper.getMainLooper())
    private val stateUri = Uri.parse("wear://*$WEAR_STATE_PATH")
    val connection = PhoneConnectionController(
        transport = this,
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
            if (event.type == DataEvent.TYPE_CHANGED && event.dataItem.uri.path == WEAR_STATE_PATH) {
                event.dataItem.data?.let { bytes ->
                    connection.receiveStateBytes(bytes)
                }
            }
        }
    }

    override fun onCapabilityChanged(capability: CapabilityInfo) {
        if (capability.name == PHONE_STATE_CAPABILITY) {
            connection.phoneReachabilityChanged(capability.nodes.map { it.phoneNode() })
        }
    }

    override fun findPhone(onSuccess: (List<PhoneNode>) -> Unit, onFailure: () -> Unit) {
        capabilityClient.getCapability(PHONE_STATE_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
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
        messageClient.sendRequest(nodeId, WearRequestAction.STARTUP.path, bytes)
            .addOnSuccessListener { reply -> onSuccess(reply) }
            .addOnFailureListener { onFailure() }
    }

    override fun sendCommand(
        nodeId: String,
        path: String,
        bytes: ByteArray,
        onFailure: () -> Unit,
    ) {
        messageClient.sendMessage(nodeId, path, bytes)
            .addOnFailureListener { onFailure() }
    }

    override fun scheduleTimeout(delayMillis: Long, action: () -> Unit): () -> Unit {
        val runnable = Runnable { action() }
        handler.postDelayed(runnable, delayMillis)
        return { handler.removeCallbacks(runnable) }
    }
}

private fun Node.phoneNode(): PhoneNode = PhoneNode(id, isNearby)
