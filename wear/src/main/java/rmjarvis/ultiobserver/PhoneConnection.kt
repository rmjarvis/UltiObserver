package rmjarvis.ultiobserver

import java.util.UUID
import kotlinx.serialization.SerializationException
import rmjarvis.ultiobserver.wearprotocol.WEAR_PROTOCOL_VERSION
import rmjarvis.ultiobserver.wearprotocol.WearActionConfirmation
import rmjarvis.ultiobserver.wearprotocol.WearCancelCardEntryRequest
import rmjarvis.ultiobserver.wearprotocol.WearCardEntryRequest
import rmjarvis.ultiobserver.wearprotocol.WearCommandAcknowledgement
import rmjarvis.ultiobserver.wearprotocol.WearCommandRequest
import rmjarvis.ultiobserver.wearprotocol.WearConfirmActionRequest
import rmjarvis.ultiobserver.wearprotocol.WearDecisionRequest
import rmjarvis.ultiobserver.wearprotocol.WearGameActionResponse
import rmjarvis.ultiobserver.wearprotocol.WearGoalRequest
import rmjarvis.ultiobserver.wearprotocol.WearPendingCommand
import rmjarvis.ultiobserver.wearprotocol.WearProtocolCodec
import rmjarvis.ultiobserver.wearprotocol.WearRequestAction
import rmjarvis.ultiobserver.wearprotocol.WearSnapshotReceiver
import rmjarvis.ultiobserver.wearprotocol.WearStartupRequest
import rmjarvis.ultiobserver.wearprotocol.WearStartupResponse
import rmjarvis.ultiobserver.wearprotocol.WearStateSnapshot
import rmjarvis.ultiobserver.wearprotocol.WearStateUpdate
import rmjarvis.ultiobserver.wearprotocol.WearTeamAction
import rmjarvis.ultiobserver.wearprotocol.WearTeamActionPrompt
import rmjarvis.ultiobserver.wearprotocol.WearTeamActionRequest
import rmjarvis.ultiobserver.wearprotocol.WearCountdownAction
import rmjarvis.ultiobserver.wearprotocol.WearCountdownActionRequest
import rmjarvis.ultiobserver.wearprotocol.WearUndoRequest

/** Wait for both the startup timing reply and its published snapshot, in either arrival order. */
internal class PendingStartup(
    private val requestId: String,
    private val sentAtWatchEpochMillis: Long,
) {
    private var snapshot: WearStateSnapshot? = null
    private var clockOffsetMillis: Long? = null

    fun receiveTiming(phoneEpochMillis: Long, receivedAtWatchEpochMillis: Long) {
        clockOffsetMillis = calibratePhoneClockOffset(
            sentAtWatchEpochMillis, receivedAtWatchEpochMillis, phoneEpochMillis,
        )
    }

    fun receiveSnapshot(update: WearStateUpdate) {
        val acknowledgement = update.acknowledgement
        if (acknowledgement?.requestId != requestId) return
        val previous = snapshot
        if (previous == null || update.snapshot.sequenceNumber > previous.sequenceNumber) {
            snapshot = update.snapshot
        }
    }

    val receivedState: ReceivedState?
        get() {
            val state = snapshot ?: return null
            val offset = clockOffsetMillis ?: return null
            return ReceivedState(state, offset)
        }
}

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
    DISABLED,
}

/**
 * Estimate the offset between the phone and watch clocks from one startup round trip.
 *
 * The watch sends a startup request to the phone and records both the time that it sent
 * the request and the time it received a response. The assumption is that the midpoint
 * between these two times is the time that the phone responded. Thus, we can calibrate
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

/** Own the watch's phone session, request lifecycle, and authoritative state without Android APIs. */
internal class PhoneConnectionController(
    private val transport: PhoneTransport,
    private val clock: () -> Long,
    private val onStateReceived: (ReceivedState) -> Unit,
    private val onConnectionStateChanged: (ConnectionState) -> Unit,
) {
    private var phoneClockOffsetMillis: Long = 0L
    private var reachablePhoneNodeId: String? = null
    private var startupRequestNodeId: String? = null
    private var currentStartupRequestAttempt: String? = null
    private var pendingStartup: PendingStartup? = null
    private var connectionAttemptCount = 0L
    private var cancelStartupTimeout: (() -> Unit)? = null
    private var startupComplete = false
    private var started = false
    private lateinit var snapshotReceiver: WearSnapshotReceiver
    private val pendingCommand = WearPendingCommand()
    private var cancelCommandTimeout: (() -> Unit)? = null
    private var onCommandFinished: ((WearGameActionResponse?) -> Unit)? = null

    /** Start a fresh lookup when the watch screen becomes active. */
    fun start() {
        started = true
        reachablePhoneNodeId = null
        phoneClockOffsetMillis = 0L
        startupRequestNodeId = null
        startupComplete = false
        retry()
    }

    /** Discard an incomplete attempt and try a fresh phone lookup and startup request. */
    fun retry() {
        startupComplete = false
        finishStartupRequest()
        onConnectionStateChanged(ConnectionState.CONNECTING)
        val connectionAttempt = ++connectionAttemptCount
        transport.findPhone(
            onSuccess = { nodes ->
                if (started && connectionAttempt == connectionAttemptCount) {
                    updatePhoneReachability(nodes)
                }
            },
            onFailure = {
                if (started && connectionAttempt == connectionAttemptCount) {
                    reachablePhoneNodeId = null
                    onConnectionStateChanged(ConnectionState.DISCONNECTED)
                }
            },
        )
    }

    /** Discard pending work when the watch screen is no longer active. */
    fun stop() {
        clearPendingCommand()
        started = false
        reachablePhoneNodeId = null
        finishStartupRequest()
        startupComplete = false
    }

    /** React to a live phone capability change while the watch is active. */
    fun phoneReachabilityChanged(nodes: List<PhoneNode>) {
        if (started) updatePhoneReachability(nodes)
    }

    private fun updatePhoneReachability(
        nodes: List<PhoneNode>,
    ) {
        val phoneNode = nodes.firstOrNull { node -> node.isNearby } ?: nodes.firstOrNull()
        val previousNodeId = reachablePhoneNodeId
        reachablePhoneNodeId = phoneNode?.id
        if (phoneNode == null) {
            finishStartupRequest()
            startupComplete = false
            onConnectionStateChanged(ConnectionState.DISCONNECTED)
        } else if (phoneNode.id != previousNodeId) {
            phoneClockOffsetMillis = 0L
            finishStartupRequest()
            startupComplete = false
            requestStartupState(phoneNode.id)
        } else if (!startupComplete) {
            requestStartupState(phoneNode.id)
        }
    }

    private fun requestStartupState(
        nodeId: String,
    ) {
        if (startupRequestNodeId == nodeId) {
            return
        }
        finishStartupRequest()
        startupComplete = false
        startupRequestNodeId = nodeId
        val startupRequestAttempt = UUID.randomUUID().toString()
        currentStartupRequestAttempt = startupRequestAttempt
        onConnectionStateChanged(ConnectionState.CONNECTING)
        cancelStartupTimeout = transport.scheduleTimeout(STARTUP_TIMEOUT_MILLIS) {
            finishStartupFailure(nodeId, startupRequestAttempt)
        }
        pendingStartup = PendingStartup(startupRequestAttempt, clock())
        transport.requestStartup(
            nodeId,
            WearProtocolCodec.encode(
                WearStartupRequest.serializer(), WearStartupRequest(startupRequestAttempt),
            ),
            onSuccess = { bytes ->
                if (started && reachablePhoneNodeId == nodeId &&
                    currentStartupRequestAttempt == startupRequestAttempt) {
                    val response = WearProtocolCodec.decode(WearStartupResponse.serializer(), bytes)
                    if (response.protocolVersion != WEAR_PROTOCOL_VERSION) {
                        finishStartupFailure(nodeId, startupRequestAttempt)
                    } else if (!response.enabled) {
                        finishStartupRequest()
                        onCommandFinished?.invoke(null)
                        clearPendingCommand()
                        onConnectionStateChanged(ConnectionState.DISABLED)
                    } else {
                        pendingStartup!!.receiveTiming(response.phoneEpochMillis, clock())
                        completeStartupIfReady()
                    }
                }
            },
            onFailure = { finishStartupFailure(nodeId, startupRequestAttempt) },
        )
    }

    private fun finishStartupRequest() {
        cancelStartupTimeout?.invoke()
        cancelStartupTimeout = null
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
        val request = WearGoalRequest(
            stateToken = stateToken,
            scoringTeam = scoringTeam,
        )
        sendGameAction(
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
        val request = WearUndoRequest(stateToken)
        sendGameAction(
            action = WearRequestAction.UNDO,
            stateToken = stateToken,
            request = WearProtocolCodec.encode(WearUndoRequest.serializer(), request),
            onFinished = { response -> onFinished(response?.applied == true) },
        )
    }

    /** Apply the countdown action against the exact state displayed by the watch. */
    fun countdownAction(
        stateToken: String,
        action: WearCountdownAction,
        onFinished: (Boolean) -> Unit,
    ) {
        val request = WearCountdownActionRequest(stateToken, action)
        sendGameAction(
            action = WearRequestAction.COUNTDOWN,
            stateToken = stateToken,
            request = WearProtocolCodec.encode(WearCountdownActionRequest.serializer(), request),
            onFinished = { response -> onFinished(response?.applied == true) },
        )
    }

    /** Send an OK or Not yet response for the exact pending decision shown by the watch. */
    fun resolveDecision(
        stateToken: String,
        accept: Boolean,
        onFinished: (Boolean) -> Unit,
    ) {
        val request = WearDecisionRequest(
            stateToken = stateToken,
            accept = accept,
        )
        sendGameAction(
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
        val request = WearConfirmActionRequest(confirmation)
        sendGameAction(
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
        val request = WearTeamActionRequest(
            stateToken = stateToken,
            team = team,
            action = action,
        )
        sendGameAction(
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
        val request = WearCardEntryRequest(stateToken, team, cardType, jerseyNumber)
        sendGameAction(
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
        val request = WearCancelCardEntryRequest(stateToken, team, cardType, jerseyNumber)
        sendGameAction(
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
        action: WearRequestAction,
        stateToken: String,
        request: ByteArray,
        onFinished: (WearGameActionResponse?) -> Unit,
    ) {
        // Ignore repeated submissions without replacing or completing the outstanding command.
        if (pendingCommand.requestId != null) return
        val nodeId = reachablePhoneNodeId
        if (nodeId == null) {
            onFinished(null)
            return
        }
        val commandId = pendingCommand.begin(stateToken)
        onCommandFinished = onFinished
        cancelCommandTimeout = transport.scheduleTimeout(COMMAND_TIMEOUT_MILLIS) {
            failCommand(commandId, nodeId)
        }
        transport.sendCommand(
            nodeId, action.path,
            WearProtocolCodec.encode(
                WearCommandRequest.serializer(), WearCommandRequest(commandId, request),
            ),
            onFailure = { failCommand(commandId, nodeId) },
        )
    }

    private fun clearPendingCommand() {
        cancelCommandTimeout?.invoke()
        cancelCommandTimeout = null
        pendingCommand.clear()
        onCommandFinished = null
    }

    private fun failCommand(commandId: String, nodeId: String) {
        if (!started || pendingCommand.requestId != commandId) return
        startupComplete = false
        onConnectionStateChanged(ConnectionState.DISCONNECTED)
        onCommandFinished!!.invoke(null)
        clearPendingCommand()
        if (reachablePhoneNodeId == nodeId) requestStartupState(nodeId)
    }

    fun receiveStateBytes(bytes: ByteArray) {
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
            requestStartupState(reachablePhoneNodeId!!)
            return
        }
        val stateChanged = snapshotReceiver.receive(snapshot)
        val acknowledgement = update.acknowledgement
        // Even an unchanged or older snapshot can carry the acknowledgement we are waiting for.
        if (pendingCommand.complete(acknowledgement)) {
            val current = snapshotReceiver.current
            val result = acknowledgement as WearCommandAcknowledgement
            // Complete the command, but never restore a prompt made obsolete by a phone change.
            onCommandFinished!!.invoke(if (result.matchesSnapshot(current)) {
                WearGameActionResponse(result.applied, current, result.nextPrompt)
            } else {
                null
            })
            clearPendingCommand()
        } else if (stateChanged && pendingCommand.supersede(snapshot.activeGame?.stateToken)) {
            // The phone has moved past the requested state; release the old screen's local guard.
            onCommandFinished!!.invoke(null)
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
                phoneClockOffsetMillis = phoneClockOffsetMillis,
            )
        )
    }
}

private const val STARTUP_TIMEOUT_MILLIS = 5_000L
private const val COMMAND_TIMEOUT_MILLIS = 5_000L


/** Device identity supplied by the Wear capability lookup. */
internal data class PhoneNode(val id: String, val isNearby: Boolean)

/** Android transport and scheduling operations used by the phone connection controller. */
internal interface PhoneTransport {
    fun findPhone(onSuccess: (List<PhoneNode>) -> Unit, onFailure: () -> Unit)
    fun requestStartup(nodeId: String, bytes: ByteArray, onSuccess: (ByteArray) -> Unit, onFailure: () -> Unit)
    fun sendCommand(nodeId: String, path: String, bytes: ByteArray, onFailure: () -> Unit)
    fun scheduleTimeout(delayMillis: Long, action: () -> Unit): () -> Unit
}
