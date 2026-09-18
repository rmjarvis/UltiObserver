package rmjarvis.ultiobserver

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.CapabilityClient
import com.google.android.gms.wearable.CapabilityInfo
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import rmjarvis.ultiobserver.wearprotocol.WATCH_VIBRATION_CAPABILITY
import rmjarvis.ultiobserver.wearprotocol.WATCH_VIBRATION_PATH
import rmjarvis.ultiobserver.wearprotocol.WEAR_STATE_PATH
import rmjarvis.ultiobserver.wearprotocol.WearProtocolCodec
import rmjarvis.ultiobserver.wearprotocol.WearStateUpdate
import rmjarvis.ultiobserver.wearprotocol.WearCommandRequest
import rmjarvis.ultiobserver.wearprotocol.WearRequestAction
import rmjarvis.ultiobserver.wearprotocol.WearStartupRequest
import rmjarvis.ultiobserver.wearprotocol.WearStartupResponse
import rmjarvis.ultiobserver.wearprotocol.WearVibrationRequest
import rmjarvis.ultiobserver.wearprotocol.WearVibrationResponse

/** Send a live pulse to a reachable watch and wait briefly for its acceptance. */
internal class WearVibrationSender(
    private val findWatch: () -> Task<CapabilityInfo>,
    private val sendRequest: (String, ByteArray) -> Task<ByteArray>,
) {
    constructor(context: Context) : this(
        findWatch = {
            Wearable.getCapabilityClient(context.applicationContext)
                .getCapability(WATCH_VIBRATION_CAPABILITY, CapabilityClient.FILTER_REACHABLE)
        },
        sendRequest = { nodeId, bytes ->
            Wearable.getMessageClient(context.applicationContext)
                .sendRequest(nodeId, WATCH_VIBRATION_PATH, bytes)
        },
    )

    suspend fun vibrate(durationMillis: Long): Boolean {
        val reply = withTimeoutOrNull(WATCH_VIBRATION_TIMEOUT_MILLIS) {
            suspendCancellableCoroutine<ByteArray?> { continuation ->
                findWatch().continueWithTask { result ->
                    val node = result.result.nodes.sortedBy { it.id }
                        .let { nodes -> nodes.firstOrNull { it.isNearby } ?: nodes.firstOrNull() }
                    if (node == null || !continuation.isActive) {
                        Tasks.forResult<ByteArray?>(null)
                    } else {
                        sendRequest(node.id, WearProtocolCodec.encode(
                            WearVibrationRequest.serializer(), WearVibrationRequest(durationMillis),
                        ))
                    }
                }.addOnSuccessListener { bytes ->
                    continuation.resume(bytes)
                }.addOnFailureListener {
                    continuation.resume(null)
                }.addOnCanceledListener {
                    continuation.resume(null)
                }
            }
        } ?: return false
        return WearProtocolCodec.decode(WearVibrationResponse.serializer(), reply).accepted
    }
}

internal const val WATCH_VIBRATION_TIMEOUT_MILLIS = 2_000L

/** Check whether this phone currently has a reachable Wear OS node. */
internal class WearOSAvailabilityChecker(
    context: Context,
    private val onAvailabilityChanged: (Boolean) -> Unit,
) {
    private val nodeClient = Wearable.getNodeClient(context.applicationContext)

    /** Query and report the current connected-node state. */
    fun refresh() {
        nodeClient.connectedNodes
            .addOnSuccessListener { nodes ->
                onAvailabilityChanged(hasAvailableWearNode(nodes.size))
            }
            .addOnFailureListener {
                onAvailabilityChanged(false)
            }
    }
}

/**
 * The outgoing path for all game updates, whether initiated on the phone or requested by the watch.
 *
 * [WearPhoneCoordinator] prepares each update with the current authoritative snapshot and the most
 * recent watch-request acknowledgement, either startup or a game-command result. It retains that
 * acknowledgement in subsequent updates until another request supplies a new one. This publisher
 * only encodes and sends the prepared
 * update; it does not retain command results or assign snapshot sequence numbers itself.
 *
 * Updates replace the DataItem at [WEAR_STATE_PATH], rather than forming a queue of individual
 * replies, so there is only ever one update for the watch to read from that location. This is why
 * we always send the latest acknowledgement along with subsequent updates -- if a later
 * phone-initiated update replaces the existing update before the watch reads it, the watch still
 * knows whether the phone handled its latest request, so it can clear the pending command.
 *
 * Startup and recovery snapshots also use this publisher. Their acknowledgement occupies the same
 * field until a subsequent request replaces it, so a later phone update can establish the connection.
 * Connection status and the clock-calibration timestamp use a direct startup reply through
 * [WearOSRequestService], without a snapshot or tags.
 */
internal class WearStatePublisher(context: Context) {
    private val dataClient = Wearable.getDataClient(context.applicationContext)

    /**
     * Synchronize the snapshot and retained acknowledgement together as the latest phone update.
     * The acknowledgement is null until the phone has handled its first enabled startup or command.
     *
     * The coordinator's snapshot tagger supplies a session ID and sequenceNumber. A changed
     * snapshot advances that number; an identical snapshot keeps its position in the sequence.
     * The acknowledgement is not part of that comparison, so acknowledging a new command can
     * change this DataItem even when the command leaves the snapshot (and tag) unchanged.
     *
     * The watch checks these independently: the snapshot's session and sequence number determine
     * whether to replace the displayed state, while the acknowledgement's request ID determines
     * whether its pending command has completed. Merely receiving newer state does not complete
     * an unrelated command, and receiving an unchanged state can still acknowledge a rejection.
     *
     * Mark the update urgent because it drives live controls. Submitting this DataItem does not
     * mean the watch has received it; the watch retains its command timeout and recovery path.
     */
    fun publish(update: WearStateUpdate) {
        val request = PutDataRequest.create(WEAR_STATE_PATH)
            .setData(WearProtocolCodec.encode(WearStateUpdate.serializer(), update))
            .setUrgent()
        dataClient.putDataItem(request)
    }
}

/** Answer watch startup and game-action requests while the phone may be stopped or locked. */
class WearOSRequestService : WearableListenerService() {
    override fun onRequest(nodeId: String, path: String, request: ByteArray): Task<ByteArray> {
        // Startup is currently the only path that uses request/reply rather than one-way messages.
        require(path == WearRequestAction.STARTUP.path) {
            "Only startup uses the request/reply path"
        }
        val startup = WearProtocolCodec.decode(WearStartupRequest.serializer(), request)
        val app = application as UltiObserverApplication
        val response = app.wearCoordinator.startup(startup, System.currentTimeMillis())
        return Tasks.forResult(WearProtocolCodec.encode(WearStartupResponse.serializer(), response))
    }

    override fun onMessageReceived(event: MessageEvent) {
        val action = WearRequestAction.fromPath(event.path) ?: return
        val app = application as UltiObserverApplication
        app.wearCoordinator.handleRequest(
            requestedActionPath = event.path,
            request = WearProtocolCodec.decode(WearCommandRequest.serializer(), event.data),
            now = System.currentTimeMillis(),
        )
    }
}
