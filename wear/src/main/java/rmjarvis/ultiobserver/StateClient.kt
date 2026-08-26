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
import kotlinx.serialization.SerializationException
import rmjarvis.ultiobserver.wearprotocol.PHONE_STATE_CAPABILITY
import rmjarvis.ultiobserver.wearprotocol.WEAR_PROTOCOL_VERSION
import rmjarvis.ultiobserver.wearprotocol.WEAR_STATE_PATH
import rmjarvis.ultiobserver.wearprotocol.WearDecisionRequest
import rmjarvis.ultiobserver.wearprotocol.WearGameActionResponse
import rmjarvis.ultiobserver.wearprotocol.WearGoalRequest
import rmjarvis.ultiobserver.wearprotocol.WearProtocolCodec
import rmjarvis.ultiobserver.wearprotocol.WearRequestAction
import rmjarvis.ultiobserver.wearprotocol.WearStateSnapshot
import rmjarvis.ultiobserver.wearprotocol.WearStartupResponse
import rmjarvis.ultiobserver.wearprotocol.WearTeamId

/** Snapshot plus the offset needed to display it using the phone's clock. */
internal data class ReceivedState(
    val snapshot: WearStateSnapshot,
    val phoneClockOffsetMillis: Long,
)

/** Current result of the watch app's live handshake with the phone app. */
internal enum class ConnectionState {
    CONNECTING,
    CONNECTED,
    DISCONNECTED,
}

/**
 * Estimate the offset between the phone and watch clocks from one RPC round trip.
 *
 * The watch sends a startup request to the phone and records both the time that it sent
 * the request and the time it received a response.  The assumption is that the midpoint
 * between these two times is the time that the phone responded.  Thus, we can calibrate
 * the difference between the phone and watch clocks by using the difference between the
 * reported clock time from the phone and the midpoint of the round trip.
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

/**
 * Listen for the phone's current-state item and advertised reachability while the watch app runs.
 */
internal class StateClient(
    context: Context,
    private val onStateReceived: (ReceivedState) -> Unit,
    private val onConnectionStateChanged: (ConnectionState) -> Unit,
) : DataClient.OnDataChangedListener, CapabilityClient.OnCapabilityChangedListener {
    private val dataClient = Wearable.getDataClient(context.applicationContext)
    private val capabilityClient = Wearable.getCapabilityClient(context.applicationContext)
    private val messageClient = Wearable.getMessageClient(context.applicationContext)
    private val handler = Handler(Looper.getMainLooper())
    private val stateUri = Uri.parse("wear://*$WEAR_STATE_PATH")
    private var phoneClockOffsetMillis: Long? = null
    private var reachablePhoneNodeId: String? = null
    private var startupRequestNodeId: String? = null
    private var startupRequestAttemptCount = 0L
    private var currentStartupRequestAttempt: Long? = null
    private var connectionAttemptCount = 0L
    private var currentConnectionAttempt: Long? = null
    private var startupTimeout: Runnable? = null
    private var startupComplete = false
    private var started = false

    /** Register live listeners and request current state from a reachable phone. */
    fun start() {
        started = true
        reachablePhoneNodeId = null
        phoneClockOffsetMillis = null
        startupRequestNodeId = null
        startupComplete = false
        dataClient.addListener(this, stateUri, DataClient.FILTER_LITERAL)
        capabilityClient.addListener(this, PHONE_STATE_CAPABILITY)
        retry()
    }

    /** Discard an incomplete attempt and try a fresh phone lookup and startup request. */
    fun retry() {
        startupComplete = false
        finishStartupRequest()
        onConnectionStateChanged(ConnectionState.CONNECTING)
        val connectionAttempt = ++connectionAttemptCount
        currentConnectionAttempt = connectionAttempt
        capabilityClient.getCapability(
            PHONE_STATE_CAPABILITY,
            CapabilityClient.FILTER_REACHABLE,
        )
            .addOnSuccessListener { capability ->
                if (
                    started &&
                    connectionAttempt == currentConnectionAttempt
                ) {
                    currentConnectionAttempt = null
                    updatePhoneReachability(capability.nodes, replaceStartupRequest = true)
                }
            }
            .addOnFailureListener {
                if (
                    started &&
                    connectionAttempt == currentConnectionAttempt
                ) {
                    currentConnectionAttempt = null
                    reachablePhoneNodeId = null
                    onConnectionStateChanged(ConnectionState.DISCONNECTED)
                }
            }
    }

    /** Remove the live listeners when the watch screen is no longer active. */
    fun stop() {
        started = false
        currentConnectionAttempt = null
        reachablePhoneNodeId = null
        finishStartupRequest()
        startupComplete = false
        dataClient.removeListener(this)
        capabilityClient.removeListener(this, PHONE_STATE_CAPABILITY)
    }

    override fun onDataChanged(events: DataEventBuffer) {
        events.forEach { event ->
            if (
                event.type == DataEvent.TYPE_CHANGED &&
                event.dataItem.uri.path == WEAR_STATE_PATH
            ) {
                if (startupComplete) {
                    event.dataItem.data?.let { bytes -> receiveStateBytes(bytes) }
                } else {
                    reachablePhoneNodeId?.let { nodeId -> requestStartupState(nodeId) }
                }
            }
        }
    }

    override fun onCapabilityChanged(capability: CapabilityInfo) {
        if (started && capability.name == PHONE_STATE_CAPABILITY) {
            updatePhoneReachability(capability.nodes)
        }
    }

    private fun updatePhoneReachability(
        nodes: Set<Node>,
        replaceStartupRequest: Boolean = false,
    ) {
        val phoneNode = nodes.firstOrNull { node -> node.isNearby } ?: nodes.firstOrNull()
        val previousNodeId = reachablePhoneNodeId
        reachablePhoneNodeId = phoneNode?.id
        if (phoneNode == null) {
            finishStartupRequest()
            startupComplete = false
            onConnectionStateChanged(ConnectionState.DISCONNECTED)
        } else if (phoneNode.id != previousNodeId) {
            phoneClockOffsetMillis = null
            finishStartupRequest()
            startupComplete = false
            requestStartupState(phoneNode.id, replacePending = true)
        } else if (!startupComplete) {
            requestStartupState(phoneNode.id, replacePending = replaceStartupRequest)
        }
    }

    private fun requestStartupState(
        nodeId: String,
        replacePending: Boolean = false,
    ) {
        if (startupRequestNodeId == nodeId && !replacePending) {
            return
        }
        finishStartupRequest()
        startupRequestNodeId = nodeId
        val startupRequestAttempt = ++startupRequestAttemptCount
        currentStartupRequestAttempt = startupRequestAttempt
        onConnectionStateChanged(ConnectionState.CONNECTING)
        val timeout = Runnable {
            finishStartupFailure(nodeId, startupRequestAttempt)
        }
        startupTimeout = timeout
        handler.postDelayed(timeout, STARTUP_TIMEOUT_MILLIS)
        val requestSentAt = System.currentTimeMillis()
        messageClient.sendRequest(
            nodeId,
            WearRequestAction.STARTUP.path,
            byteArrayOf(),
        )
            .addOnSuccessListener { responseBytes ->
                if (
                    !started ||
                    reachablePhoneNodeId != nodeId ||
                    startupRequestAttempt != currentStartupRequestAttempt
                ) {
                    return@addOnSuccessListener
                }
                val response = try {
                    WearProtocolCodec.decode(WearStartupResponse.serializer(), responseBytes)
                } catch (_: SerializationException) {
                    finishStartupFailure(nodeId, startupRequestAttempt)
                    return@addOnSuccessListener
                }
                if (response.snapshot.protocolVersion != WEAR_PROTOCOL_VERSION) {
                    finishStartupFailure(nodeId, startupRequestAttempt)
                    return@addOnSuccessListener
                }
                finishStartupRequest()
                val responseReceivedAt = System.currentTimeMillis()
                phoneClockOffsetMillis = calibratePhoneClockOffset(
                    requestSentAtWatchEpochMillis = requestSentAt,
                    responseReceivedAtWatchEpochMillis = responseReceivedAt,
                    phoneEpochMillis = response.phoneEpochMillis,
                )
                startupComplete = true
                receiveSnapshot(response.snapshot)
                onConnectionStateChanged(ConnectionState.CONNECTED)
            }
            .addOnFailureListener {
                finishStartupFailure(nodeId, startupRequestAttempt)
            }
    }

    private fun finishStartupRequest() {
        startupTimeout?.let { timeout -> handler.removeCallbacks(timeout) }
        startupTimeout = null
        startupRequestNodeId = null
        currentStartupRequestAttempt = null
    }

    private fun finishStartupFailure(
        nodeId: String,
        startupRequestAttempt: Long,
    ) {
        if (
            !started ||
            reachablePhoneNodeId != nodeId ||
            startupRequestAttempt != currentStartupRequestAttempt
        ) {
            return
        }
        finishStartupRequest()
        startupComplete = false
        onConnectionStateChanged(ConnectionState.DISCONNECTED)
    }

    /** Send one goal request to the reachable phone and receive its resulting state. */
    fun recordGoal(
        scoringTeam: WearTeamId,
        stateToken: String,
        onFinished: (Boolean) -> Unit,
    ) {
        val nodeId = reachablePhoneNodeId
        if (nodeId == null) {
            onFinished(false)
            return
        }
        val request = WearGoalRequest(
            stateToken = stateToken,
            scoringTeam = scoringTeam,
        )
        sendGameAction(
            nodeId = nodeId,
            action = WearRequestAction.GOAL,
            request = WearProtocolCodec.encode(WearGoalRequest.serializer(), request),
            onFinished = onFinished,
        )
    }

    /** Send an OK or Not yet response for the exact pending decision shown by the watch. */
    fun resolveDecision(
        stateToken: String,
        accept: Boolean,
        onFinished: (Boolean) -> Unit,
    ) {
        val nodeId = reachablePhoneNodeId
        if (nodeId == null) {
            onFinished(false)
            return
        }
        val request = WearDecisionRequest(
            stateToken = stateToken,
            accept = accept,
        )
        sendGameAction(
            nodeId = nodeId,
            action = WearRequestAction.DECISION,
            request = WearProtocolCodec.encode(WearDecisionRequest.serializer(), request),
            onFinished = onFinished,
        )
    }

    private fun sendGameAction(
        nodeId: String,
        action: WearRequestAction,
        request: ByteArray,
        onFinished: (Boolean) -> Unit,
    ) {
        messageClient.sendRequest(nodeId, action.path, request)
            // Any response from the phone counts as success here. Even rejecting the action.
            .addOnSuccessListener { responseBytes ->
                val response = WearProtocolCodec.decode(
                    WearGameActionResponse.serializer(),
                    responseBytes,
                )
                receiveSnapshot(response.snapshot)
                if (reachablePhoneNodeId == nodeId) {
                    startupComplete = true
                    onConnectionStateChanged(ConnectionState.CONNECTED)
                }
                onFinished(response.applied)
            }
            // Failure means some kind of disconnect: timeout, phone crash, transport error, etc.
            .addOnFailureListener {
                if (reachablePhoneNodeId == nodeId) {
                    startupComplete = false
                    onConnectionStateChanged(ConnectionState.DISCONNECTED)
                    requestStartupState(nodeId)
                }
                onFinished(false)
            }
    }

    private fun receiveStateBytes(bytes: ByteArray) {
        val snapshot = try {
            WearProtocolCodec.decode(WearStateSnapshot.serializer(), bytes)
        } catch (_: SerializationException) {
            return
        }
        receiveSnapshot(snapshot)
    }

    private fun receiveSnapshot(snapshot: WearStateSnapshot) {
        if (snapshot.protocolVersion != WEAR_PROTOCOL_VERSION) {
            return
        }
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

private const val STARTUP_TIMEOUT_MILLIS = 5_000L
