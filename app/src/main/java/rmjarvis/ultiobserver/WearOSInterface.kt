package rmjarvis.ultiobserver

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import rmjarvis.ultiobserver.wearprotocol.WEAR_STATE_PATH
import rmjarvis.ultiobserver.wearprotocol.WEAR_TIME_SYNC_PATH
import rmjarvis.ultiobserver.wearprotocol.WearActiveGameSnapshot
import rmjarvis.ultiobserver.wearprotocol.WearCountdownSnapshot
import rmjarvis.ultiobserver.wearprotocol.WearCueSnapshot
import rmjarvis.ultiobserver.wearprotocol.WearRatioSnapshot
import rmjarvis.ultiobserver.wearprotocol.WearSnapshotPullDirection
import rmjarvis.ultiobserver.wearprotocol.WearSnapshotStatus
import rmjarvis.ultiobserver.wearprotocol.WearStateSnapshot
import rmjarvis.ultiobserver.wearprotocol.WearStateSnapshotCodec
import rmjarvis.ultiobserver.wearprotocol.WearTeamActionsSnapshot
import rmjarvis.ultiobserver.wearprotocol.WearTeamSnapshot
import rmjarvis.ultiobserver.wearprotocol.WearTimeSyncCodec

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
                onAvailabilityChanged(nodes.isNotEmpty())
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
    fun publish(game: GameState?, settings: Settings) {
        publish(
            buildWearStateSnapshot(
                game = game,
                settings = settings,
                now = System.currentTimeMillis(),
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

    private fun publish(snapshot: WearStateSnapshot) {
        val request = PutDataRequest.create(WEAR_STATE_PATH)
            .setData(WearStateSnapshotCodec.encode(snapshot))
            .setUrgent()
        dataClient.putDataItem(request)
    }
}

/** Answer watch time-sync requests while the phone UI may be stopped or locked. */
class WearOSRequestService : WearableListenerService() {
    override fun onRequest(
        nodeId: String,
        path: String,
        request: ByteArray,
    ): Task<ByteArray>? {
        if (path != WEAR_TIME_SYNC_PATH) {
            return null
        }
        return Tasks.forResult(WearTimeSyncCodec.encode(System.currentTimeMillis()))
    }
}

/** Build the complete read-only companion snapshot from authoritative phone state. */
internal fun buildWearStateSnapshot(
    game: GameState?,
    settings: Settings,
    now: Long,
): WearStateSnapshot {
    if (settings.timingAlerts.watchConnectionMode != WatchConnectionMode.WEAR_OS) {
        return WearStateSnapshot(
            status = WearSnapshotStatus.DISABLED,
            activeGame = null,
        )
    }
    if (
        game == null ||
        game.phase == GamePhase.SETUP ||
        game.phase == GamePhase.GAME_OVER
    ) {
        return WearStateSnapshot(
            status = WearSnapshotStatus.NO_ACTIVE_GAME,
            activeGame = null,
        )
    }

    val activeCountdown = game.activeCountdown(now)
    val capStatus = game.computeNextCapStatus(now)
    val currentRatio = game.currentGenderRatio()
    return WearStateSnapshot(
        status = WearSnapshotStatus.ACTIVE_GAME,
        activeGame = WearActiveGameSnapshot(
            officialClockOffsetMillis = game.officialClockOffsetMillis,
            officialTimeZoneId = game.timeZone.id,
            capLabel = capStatus?.let { status -> "${status.label} in" },
            capTargetEpochMillis = capStatus?.targetEpoch,
            countdown = activeCountdown?.let { countdown ->
                WearCountdownSnapshot(
                    label = countdown.label,
                    targetEpochMillis = countdown.targetEpoch,
                    pausedAtEpochMillis = countdown.pausedAtEpoch,
                    cues = countdown.upcomingTimingCues(now).map { cue ->
                        WearCueSnapshot(
                            message = cue.message,
                            targetEpochMillis = cue.targetEpoch,
                        )
                    },
                )
            },
            teamOne = game.wearTeamSnapshot(TeamId.TEAM_ONE, now),
            teamTwo = game.wearTeamSnapshot(TeamId.TEAM_TWO, now),
            pullDirection = if (game.pullingTeam == TeamId.TEAM_ONE) {
                WearSnapshotPullDirection.LEFT_TO_RIGHT
            } else {
                WearSnapshotPullDirection.RIGHT_TO_LEFT
            },
            ratio = currentRatio?.let { ratio ->
                val backgroundArgb = settings.genderRatioBadgeColorArgb(ratio)
                WearRatioSnapshot(
                    label = game.currentGenderRatioBadgeText(
                        settings.showAbbaRatioAsSequence
                    ),
                    backgroundArgb = backgroundArgb,
                    contentArgb = readableContentArgb(backgroundArgb),
                )
            },
            undoDescription = game.undoEntry?.label,
        ),
    )
}

/** Build one team and its phone-equivalent compact action labels. */
private fun GameState.wearTeamSnapshot(teamId: TeamId, now: Long): WearTeamSnapshot {
    val team = teamFor(teamId)
    val backgroundArgb = if (team.color == TeamColorChoice.CUSTOM) {
        team.customColorArgb!!
    } else {
        team.color.accentArgb
    }
    val contentArgb = if (team.color == TeamColorChoice.CUSTOM) {
        readableContentArgb(backgroundArgb)
    } else {
        team.color.contentArgb
    }
    return WearTeamSnapshot(
        name = team.name,
        score = team.score,
        backgroundArgb = backgroundArgb,
        contentArgb = contentArgb,
        actions = WearTeamActionsSnapshot(
            timeViolationLabel = team.timeViolationFieldActionLabel(),
            pullViolationLabel = pullViolationTypeFor(teamId).fieldActionLabel(team),
            cardLabel = countedActionLabel("Card", teamCardTotal(teamId)),
            technicalFoulLabel = countedActionLabel("Tech", team.technicalFouls),
            timeoutLabel = "Timeout (${timeoutsRemaining(teamId)})",
            goalEnabled = true,
            timeViolationEnabled = canAssessTimeViolation(),
            pullViolationEnabled = canRecordPullViolation(teamId),
            cardEnabled = true,
            technicalFoulEnabled = true,
            timeoutEnabled = canRequestTimeout(now),
        ),
    )
}

/** Return the same black-or-white content ARGB used for custom phone display colors. */
private fun readableContentArgb(backgroundArgb: Long): Long {
    return readableContentColor(Color(backgroundArgb)).toArgb().toLong() and 0xFFFFFFFFL
}
