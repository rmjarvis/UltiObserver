package rmjarvis.ultiobserver

import rmjarvis.ultiobserver.wearprotocol.WearSnapshotTagger
import rmjarvis.ultiobserver.wearprotocol.WearCommandAcknowledgement
import rmjarvis.ultiobserver.wearprotocol.WearAcknowledgement
import rmjarvis.ultiobserver.wearprotocol.WearCommandRequest
import rmjarvis.ultiobserver.wearprotocol.WearStateUpdate
import rmjarvis.ultiobserver.wearprotocol.WearStartupAcknowledgement
import rmjarvis.ultiobserver.wearprotocol.WearStartupResponse

/**
 * Own the phone's Wear session and publish state with the most recent request acknowledgement.
 * Mutation notifications and watch requests share the AppState lock, so a concurrent phone change
 * cannot be misattributed to a watch command.
 */
internal class WearPhoneCoordinator(
    val appState: AppState,
    private val publish: (WearStateUpdate) -> Unit,
    private val clock: () -> Long,
) {
    private val snapshotTagger = WearSnapshotTagger()
    private var handlingWatchRequest = false
    private var latestAcknowledgement: WearAcknowledgement? = null

    /**
     * When we first initialize this object, set up the appState to be able to use it for
     * publishing state changes to the watch.
     */
    init {
        appState.onStateChanged = { previous, updated ->
            val watchChanged = updated.currentGame != previous.currentGame ||
                updated.settings != previous.settings ||
                updated.viewingActiveGameScreen != previous.viewingActiveGameScreen ||
                updated.activeCardEntry != previous.activeCardEntry
            val wearEnabled = previous.settings.timingAlerts.watchConnectionMode ==
                WatchConnectionMode.WEAR_OS ||
                updated.settings.timingAlerts.watchConnectionMode == WatchConnectionMode.WEAR_OS
            if (watchChanged && wearEnabled && !handlingWatchRequest) {
                publish(WearStateUpdate(
                    updated.toWearSnapshot(snapshotTagger, clock()), latestAcknowledgement,
                ))
            }
        }
    }

    /** Announce the current phone session when its activity starts or returns to the foreground. */
    fun publishCurrentState() = synchronized(appState) {
        val state = appState.state.value
        if (state.settings.timingAlerts.watchConnectionMode == WatchConnectionMode.WEAR_OS) {
            publish(WearStateUpdate(
                state.toWearSnapshot(snapshotTagger, clock()), latestAcknowledgement,
            ))
        }
    }

    /** Publish enabled startup state and return connection status with the phone time. */
    fun startup(requestId: String, now: Long): WearStartupResponse = synchronized(appState) {
        val state = appState.state.value
        if (state.settings.timingAlerts.watchConnectionMode != WatchConnectionMode.WEAR_OS) {
            return@synchronized WearStartupResponse(enabled = false, phoneEpochMillis = now)
        }
        latestAcknowledgement = WearStartupAcknowledgement(requestId)
        publish(WearStateUpdate(
            state.toWearSnapshot(snapshotTagger, now), latestAcknowledgement,
        ))
        WearStartupResponse(enabled = true, phoneEpochMillis = now)
    }

    /**
     * Handle a watch game command and publish the resulting state with its acknowledgement.
     */
    fun handleRequest(requestedActionPath: String, request: WearCommandRequest, now: Long) {
        return synchronized(appState) {
            handlingWatchRequest = true
            try {
                val response = handleWearRequest(
                    requestedActionPath, request.arguments, appState, snapshotTagger, now,
                ) ?: return@synchronized
                latestAcknowledgement = WearCommandAcknowledgement(
                    requestId = request.requestId,
                    applied = response.applied,
                    sessionId = response.snapshot.sessionId,
                    sequenceNumber = response.snapshot.sequenceNumber,
                    nextPrompt = response.nextPrompt,
                )
                publish(WearStateUpdate(
                    response.snapshot, latestAcknowledgement,
                ))
            } finally {
                handlingWatchRequest = false
            }
        }
    }
}
