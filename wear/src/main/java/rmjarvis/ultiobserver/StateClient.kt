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
import rmjarvis.ultiobserver.wearprotocol.WearActionConfirmation
import rmjarvis.ultiobserver.wearprotocol.WearCancelCardEntryRequest
import rmjarvis.ultiobserver.wearprotocol.WearCardEntryRequest
import rmjarvis.ultiobserver.wearprotocol.WearConfirmActionRequest
import rmjarvis.ultiobserver.wearprotocol.WearDecisionRequest
import rmjarvis.ultiobserver.wearprotocol.WearGameActionResponse
import rmjarvis.ultiobserver.wearprotocol.WearGoalRequest
import rmjarvis.ultiobserver.wearprotocol.WearProtocolCodec
import rmjarvis.ultiobserver.wearprotocol.WearRequestAction
import rmjarvis.ultiobserver.wearprotocol.WearStateSnapshot
import rmjarvis.ultiobserver.wearprotocol.WearSnapshotReceiver
import rmjarvis.ultiobserver.wearprotocol.WearStartupRequest
import rmjarvis.ultiobserver.wearprotocol.WearStartupResponse
import rmjarvis.ultiobserver.wearprotocol.WearTeamAction
import rmjarvis.ultiobserver.wearprotocol.WearTeamActionPrompt
import rmjarvis.ultiobserver.wearprotocol.WearTeamActionRequest
import rmjarvis.ultiobserver.wearprotocol.WearUndoRequest
import rmjarvis.ultiobserver.wearprotocol.WearCommandRequest
import rmjarvis.ultiobserver.wearprotocol.WearCommandAcknowledgement
import rmjarvis.ultiobserver.wearprotocol.WearPendingCommand
import rmjarvis.ultiobserver.wearprotocol.WearStateUpdate

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
    private var currentStartupRequestAttempt: String? = null
    private var pendingStartup: PendingStartup? = null
    private var connectionAttemptCount = 0L
    private var currentConnectionAttempt: Long? = null
    private var startupTimeout: Runnable? = null
    private var startupComplete = false
    private var started = false
    private lateinit var snapshotReceiver: WearSnapshotReceiver
    private val pendingCommand = WearPendingCommand()
    private var commandTimeout: Runnable? = null
    private var onCommandFinished: ((WearGameActionResponse?) -> Unit)? = null

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
        clearPendingCommand()
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
                event.dataItem.data?.let { bytes -> receiveStateBytes(bytes) }
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
        startupComplete = false
        startupRequestNodeId = nodeId
        val startupRequestAttempt = UUID.randomUUID().toString()
        currentStartupRequestAttempt = startupRequestAttempt
        onConnectionStateChanged(ConnectionState.CONNECTING)
        val timeout = Runnable {
            finishStartupFailure(nodeId, startupRequestAttempt)
        }
        startupTimeout = timeout
        handler.postDelayed(timeout, STARTUP_TIMEOUT_MILLIS)
        pendingStartup = PendingStartup(startupRequestAttempt, System.currentTimeMillis())
        messageClient.sendRequest(
            nodeId,
            WearRequestAction.STARTUP.path,
            WearProtocolCodec.encode(
                WearStartupRequest.serializer(), WearStartupRequest(startupRequestAttempt),
            ),
        )
            .addOnSuccessListener { bytes ->
                if (!started || reachablePhoneNodeId != nodeId ||
                    currentStartupRequestAttempt != startupRequestAttempt) return@addOnSuccessListener
                val receivedAt = System.currentTimeMillis()
                val response = WearProtocolCodec.decode(WearStartupResponse.serializer(), bytes)
                if (response.protocolVersion != WEAR_PROTOCOL_VERSION) {
                    finishStartupFailure(nodeId, startupRequestAttempt)
                } else if (!response.enabled) {
                    finishStartupRequest()
                    onCommandFinished?.invoke(null)
                    clearPendingCommand()
                    onConnectionStateChanged(ConnectionState.DISABLED)
                } else {
                    pendingStartup!!.receiveTiming(response.phoneEpochMillis, receivedAt)
                    completeStartupIfReady()
                }
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
        pendingStartup = null
    }

    private fun completeStartupIfReady() {
        val state = pendingStartup?.receivedState ?: return
        phoneClockOffsetMillis = state.phoneClockOffsetMillis
        snapshotReceiver = WearSnapshotReceiver(state.snapshot)
        finishStartupRequest()
        startupComplete = true
        // Recovery refreshes state without replaying a potentially completed command.
        onCommandFinished?.invoke(null)
        clearPendingCommand()
        deliverSnapshot(state.snapshot)
        onConnectionStateChanged(ConnectionState.CONNECTED)
    }

    private fun finishStartupFailure(
        nodeId: String,
        startupRequestAttempt: String,
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
        scoringTeam: TeamId,
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
            stateToken = stateToken,
            request = WearProtocolCodec.encode(WearGoalRequest.serializer(), request),
            onFinished = { response -> onFinished(response?.applied == true) },
        )
    }

    /** Undo the latest action against the exact state displayed by the watch. */
    fun undo(
        stateToken: String,
        onFinished: (Boolean) -> Unit,
    ) {
        val nodeId = reachablePhoneNodeId
        if (nodeId == null) {
            onFinished(false)
            return
        }
        val request = WearUndoRequest(stateToken)
        sendGameAction(
            nodeId = nodeId,
            action = WearRequestAction.UNDO,
            stateToken = stateToken,
            request = WearProtocolCodec.encode(WearUndoRequest.serializer(), request),
            onFinished = { response -> onFinished(response?.applied == true) },
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
            stateToken = stateToken,
            request = WearProtocolCodec.encode(WearDecisionRequest.serializer(), request),
            onFinished = { response -> onFinished(response?.applied == true) },
        )
    }

    /** Apply the exact action confirmation accepted on the watch. */
    fun confirmAction(
        confirmation: WearActionConfirmation,
        onFinished: (Boolean) -> Unit,
    ) {
        val nodeId = reachablePhoneNodeId
        if (nodeId == null) {
            onFinished(false)
            return
        }
        val request = WearConfirmActionRequest(confirmation)
        sendGameAction(
            nodeId = nodeId,
            action = WearRequestAction.CONFIRM_ACTION,
            stateToken = confirmation.stateToken,
            request = WearProtocolCodec.encode(WearConfirmActionRequest.serializer(), request),
            onFinished = { response -> onFinished(response?.applied == true) },
        )
    }

    /** Request one team action and return the prompt to show next. */
    fun requestTeamAction(
        team: TeamId,
        stateToken: String,
        action: WearTeamAction,
        onFinished: (WearTeamActionPrompt?) -> Unit,
    ) {
        val nodeId = reachablePhoneNodeId
        if (nodeId == null) {
            onFinished(null)
            return
        }
        val request = WearTeamActionRequest(
            stateToken = stateToken,
            team = team,
            action = action,
        )
        sendGameAction(
            nodeId = nodeId,
            action = WearRequestAction.TEAM_ACTION,
            stateToken = stateToken,
            request = WearProtocolCodec.encode(
                WearTeamActionRequest.serializer(),
                request,
            ),
            onFinished = { response -> onFinished(response?.nextPrompt) },
        )
    }

    /** Start yellow or red card entry on the phone. */
    fun startCardEntry(
        team: TeamId,
        stateToken: String,
        cardType: CardType,
        jerseyNumber: String,
        onFinished: (Boolean) -> Unit,
    ) {
        val nodeId = reachablePhoneNodeId
        if (nodeId == null) {
            onFinished(false)
            return
        }
        val request = WearCardEntryRequest(stateToken, team, cardType, jerseyNumber)
        sendGameAction(
            nodeId = nodeId,
            action = WearRequestAction.CARD_ENTRY,
            stateToken = stateToken,
            request = WearProtocolCodec.encode(WearCardEntryRequest.serializer(), request),
            onFinished = { response -> onFinished(response?.applied == true) },
        )
    }

    /** Cancel the exact card workflow active on the phone. */
    fun cancelCardEntry(
        team: TeamId,
        stateToken: String,
        cardType: CardType?,
        jerseyNumber: String,
        onFinished: (Boolean) -> Unit,
    ) {
        val nodeId = reachablePhoneNodeId
        if (nodeId == null) {
            onFinished(false)
            return
        }
        val request = WearCancelCardEntryRequest(stateToken, team, cardType, jerseyNumber)
        sendGameAction(
            nodeId = nodeId,
            action = WearRequestAction.CANCEL_CARD_ENTRY,
            stateToken = stateToken,
            request = WearProtocolCodec.encode(
                WearCancelCardEntryRequest.serializer(),
                request,
            ),
            onFinished = { response -> onFinished(response?.applied == true) },
        )
    }

    private fun sendGameAction(
        nodeId: String,
        action: WearRequestAction,
        stateToken: String,
        request: ByteArray,
        onFinished: (WearGameActionResponse?) -> Unit,
    ) {
        val commandId = pendingCommand.begin(stateToken)
        onCommandFinished = onFinished
        val timeout = Runnable { failCommand(commandId, nodeId) }
        commandTimeout = timeout
        handler.postDelayed(timeout, COMMAND_TIMEOUT_MILLIS)
        messageClient.sendMessage(
            nodeId, action.path,
            WearProtocolCodec.encode(
                WearCommandRequest.serializer(), WearCommandRequest(commandId, request),
            ),
        )
            .addOnFailureListener {
                failCommand(commandId, nodeId)
            }
    }

    private fun clearPendingCommand() {
        commandTimeout?.let { handler.removeCallbacks(it) }
        commandTimeout = null
        pendingCommand.clear()
        onCommandFinished = null
    }

    private fun failCommand(commandId: String, nodeId: String) {
        if (!started || pendingCommand.requestId != commandId) return
        startupComplete = false
        onConnectionStateChanged(ConnectionState.DISCONNECTED)
        onCommandFinished?.invoke(null)
        clearPendingCommand()
        if (reachablePhoneNodeId == nodeId) requestStartupState(nodeId)
    }

    private fun receiveStateBytes(bytes: ByteArray) {
        if (!started) return
        val update = try {
            WearProtocolCodec.decode(WearStateUpdate.serializer(), bytes)
        } catch (_: SerializationException) {
            return
        }
        val snapshot = update.snapshot
        if (snapshot.protocolVersion != WEAR_PROTOCOL_VERSION) return
        if (!startupComplete) {
            val requestId = currentStartupRequestAttempt
            if (requestId == null) {
                reachablePhoneNodeId?.let { requestStartupState(it) }
                return
            }
            pendingStartup!!.receiveSnapshot(update)
            completeStartupIfReady()
            return
        }
        if (snapshot.sessionId != snapshotReceiver.current.sessionId) {
            reachablePhoneNodeId?.let { requestStartupState(it) }
            return
        }
        val stateChanged = snapshotReceiver.receive(snapshot)
        val acknowledgement = update.acknowledgement
        // Even an unchanged or older snapshot can carry the acknowledgement we are waiting for.
        if (pendingCommand.complete(acknowledgement)) {
            val current = snapshotReceiver.current
            val result = acknowledgement as WearCommandAcknowledgement
            // Complete the command, but never restore a prompt made obsolete by a phone change.
            onCommandFinished?.invoke(if (result.matchesSnapshot(current)) {
                WearGameActionResponse(result.applied, current, result.nextPrompt)
            } else {
                null
            })
            clearPendingCommand()
        } else if (stateChanged && pendingCommand.supersede(snapshot.activeGame?.stateToken)) {
            // The phone has moved past the requested state; release the old screen's local guard.
            onCommandFinished?.invoke(null)
            clearPendingCommand()
        }
        // Finish or supersede the command before exposing a replacement screen to the UI.
        if (stateChanged) {
            deliverSnapshot(snapshot)
        }
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
private const val COMMAND_TIMEOUT_MILLIS = 5_000L
