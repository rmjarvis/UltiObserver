package rmjarvis.ultiobserver

import android.content.Context
import android.net.Uri
import android.util.Base64
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.DataClient
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import rmjarvis.ultiobserver.wearprotocol.PHONE_STATE_CAPABILITY
import rmjarvis.ultiobserver.wearprotocol.WEAR_PROTOCOL_VERSION
import rmjarvis.ultiobserver.wearprotocol.WEAR_STATE_PATH
import rmjarvis.ultiobserver.wearprotocol.WEAR_TIME_SYNC_PATH
import rmjarvis.ultiobserver.wearprotocol.WearStateSnapshot
import rmjarvis.ultiobserver.wearprotocol.WearStateSnapshotCodec
import rmjarvis.ultiobserver.wearprotocol.WearTimeSyncCodec

/** Snapshot plus the offset needed to display it using the phone's clock. */
internal data class ReceivedState(
    val snapshot: WearStateSnapshot,
    val phoneClockOffsetMillis: Long,
)

/**
 * Estimate the offset between the phone and watch clocks from one RPC round trip.
 *
 * The watch sends a time check to the phone and records both the time that it sent
 * the request and the time it received a response.  The assumption is that the midpoint
 * between these two times is the time that the phone responded.  Thus, we can calibrate
 * the difference between the phone and watch clocks by using the difference between the
 * reported clock time from the phone and the midpoint of the rountrip.
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

/** Listen for the phone's current-state item and advertised reachability while the watch app runs. */
internal class StateClient(
    context: Context,
    private val onStateReceived: (ReceivedState) -> Unit,
    private val onPhoneReachabilityChanged: (Boolean) -> Unit,
) : DataClient.OnDataChangedListener, CapabilityClient.OnCapabilityChangedListener {
    private val dataClient = Wearable.getDataClient(context.applicationContext)
    private val capabilityClient = Wearable.getCapabilityClient(context.applicationContext)
    private val messageClient = Wearable.getMessageClient(context.applicationContext)
    private val cache = context.applicationContext.getSharedPreferences(
        STATE_CACHE_NAME,
        Context.MODE_PRIVATE,
    )
    private val stateUri = Uri.parse("wear://*$WEAR_STATE_PATH")
    private var latestSnapshot: WearStateSnapshot? = null
    private var phoneClockOffsetMillis: Long? = null
    private var reachablePhoneNodeId: String? = null

    /** Register live listeners and load the current cached state and connection. */
    fun start() {
        reachablePhoneNodeId = null
        cache.getString(STATE_CACHE_KEY, null)?.let { encoded ->
            receiveStateBytes(Base64.decode(encoded, Base64.NO_WRAP), saveToCache = false)
        }
        dataClient.addListener(this, stateUri, DataClient.FILTER_LITERAL)
        capabilityClient.addListener(this, PHONE_STATE_CAPABILITY)
        dataClient.getDataItems(stateUri, DataClient.FILTER_LITERAL)
            .addOnSuccessListener { items ->
                try {
                    items.firstOrNull()?.data?.let { bytes -> receiveStateBytes(bytes) }
                } finally {
                    items.release()
                }
            }
        capabilityClient.getCapability(
            PHONE_STATE_CAPABILITY,
            CapabilityClient.FILTER_REACHABLE,
        )
            .addOnSuccessListener { capability ->
                updatePhoneReachability(capability.nodes)
            }
            .addOnFailureListener {
                reachablePhoneNodeId = null
                onPhoneReachabilityChanged(false)
            }
    }

    /** Remove the live listeners when the watch screen is no longer active. */
    fun stop() {
        reachablePhoneNodeId = null
        dataClient.removeListener(this)
        capabilityClient.removeListener(this, PHONE_STATE_CAPABILITY)
    }

    override fun onDataChanged(events: DataEventBuffer) {
        events.forEach { event ->
            if (
                event.type == DataEvent.TYPE_CHANGED &&
                event.dataItem.uri.path == WEAR_STATE_PATH
            ) {
                event.dataItem.data?.let { bytes -> receiveStateBytes(bytes) }
            }
        }
    }

    override fun onCapabilityChanged(capability: CapabilityInfo) {
        if (capability.name == PHONE_STATE_CAPABILITY) {
            updatePhoneReachability(capability.nodes)
        }
    }

    private fun updatePhoneReachability(nodes: Set<Node>) {
        val phoneNode = nodes.firstOrNull { node -> node.isNearby } ?: nodes.firstOrNull()
        val previousNodeId = reachablePhoneNodeId
        reachablePhoneNodeId = phoneNode?.id
        onPhoneReachabilityChanged(phoneNode != null)
        if (phoneNode != null && phoneNode.id != previousNodeId) {
            requestPhoneTime(phoneNode.id)
        }
    }

    private fun requestPhoneTime(nodeId: String) {
        val requestSentAt = System.currentTimeMillis()
        messageClient.sendRequest(nodeId, WEAR_TIME_SYNC_PATH, byteArrayOf())
            .addOnSuccessListener { response ->
                if (reachablePhoneNodeId != nodeId) {
                    return@addOnSuccessListener
                }
                val responseReceivedAt = System.currentTimeMillis()
                phoneClockOffsetMillis = calibratePhoneClockOffset(
                    requestSentAtWatchEpochMillis = requestSentAt,
                    responseReceivedAtWatchEpochMillis = responseReceivedAt,
                    phoneEpochMillis = WearTimeSyncCodec.decode(response),
                )
                latestSnapshot?.let { snapshot -> deliverSnapshot(snapshot) }
            }
    }

    private fun receiveStateBytes(bytes: ByteArray, saveToCache: Boolean = true) {
        val snapshot = WearStateSnapshotCodec.decode(bytes)
        if (snapshot.protocolVersion != WEAR_PROTOCOL_VERSION) {
            return
        }
        if (saveToCache) {
            cache.edit()
                .putString(STATE_CACHE_KEY, Base64.encodeToString(bytes, Base64.NO_WRAP))
                .apply()
        }
        latestSnapshot = snapshot
        deliverSnapshot(snapshot)
    }

    private fun deliverSnapshot(snapshot: WearStateSnapshot) {
        onStateReceived(
            ReceivedState(
                snapshot = snapshot,
                phoneClockOffsetMillis = phoneClockOffsetMillis ?: 0L,
            )
        )
    }
}

private const val STATE_CACHE_NAME = "wear_state_cache"
private const val STATE_CACHE_KEY = "current_state"
