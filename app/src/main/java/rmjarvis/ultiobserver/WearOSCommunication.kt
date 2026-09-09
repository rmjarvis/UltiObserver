package rmjarvis.ultiobserver

import android.content.Context
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import rmjarvis.ultiobserver.wearprotocol.WEAR_STATE_PATH
import rmjarvis.ultiobserver.wearprotocol.WearProtocolCodec
import rmjarvis.ultiobserver.wearprotocol.WearSnapshotStatus
import rmjarvis.ultiobserver.wearprotocol.WearStateSnapshot

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

/** Publish changed authoritative phone state to the Wear Data Layer. */
internal class WearStatePublisher(context: Context) {
    private val dataClient = Wearable.getDataClient(context.applicationContext)

    /** Publish a fresh current snapshot. */
    fun publish(
        game: GameState?,
        settings: Settings,
        actionsAvailable: Boolean,
        activeCardEntry: ActiveCardEntry?,
    ) {
        publish(
            buildWearStateSnapshot(
                game = game,
                settings = settings,
                now = System.currentTimeMillis(),
                actionsAvailable = actionsAvailable,
                activeCardEntry = activeCardEntry,
            )
        )
    }

    /** Publish one disabled snapshot after Wear OS synchronization is turned off. */
    fun publishDisabled() {
        publish(
            WearStateSnapshot(
                status = WearSnapshotStatus.DISABLED,
                activeGame = null,
            )
        )
    }

    /** Publish an already-built snapshot returned directly with a watch command response. */
    fun publish(snapshot: WearStateSnapshot) {
        val request = PutDataRequest.create(WEAR_STATE_PATH)
            .setData(WearProtocolCodec.encode(WearStateSnapshot.serializer(), snapshot))
            .setUrgent()
        dataClient.putDataItem(request)
    }
}

/** Answer watch startup and game-action requests while the phone may be stopped or locked. */


class WearOSRequestService : WearableListenerService() {
    override fun onRequest(
        nodeId: String,
        requestedActionPath: String,
        request: ByteArray,
    ): Task<ByteArray>? {
        val app = application as UltiObserverApplication
        val response = handleWearRequest(
            requestedActionPath = requestedActionPath,
            requestBytes = request,
            appState = app.appState,
            publish = { snapshot -> app.wearStatePublisher.publish(snapshot) },
            now = System.currentTimeMillis(),
        ) ?: return null
        return Tasks.forResult(response)
    }
}
