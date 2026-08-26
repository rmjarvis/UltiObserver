package rmjarvis.ultiobserver

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import java.security.MessageDigest
import rmjarvis.ultiobserver.wearprotocol.WEAR_STATE_PATH
import rmjarvis.ultiobserver.wearprotocol.WearActiveGameSnapshot
import rmjarvis.ultiobserver.wearprotocol.WearCapSnapshot
import rmjarvis.ultiobserver.wearprotocol.WearCountdownSnapshot
import rmjarvis.ultiobserver.wearprotocol.WearCueSnapshot
import rmjarvis.ultiobserver.wearprotocol.WearDecisionRequest
import rmjarvis.ultiobserver.wearprotocol.WearDecisionSnapshot
import rmjarvis.ultiobserver.wearprotocol.WearGameActionResponse
import rmjarvis.ultiobserver.wearprotocol.WearGoalRequest
import rmjarvis.ultiobserver.wearprotocol.WearGuidanceLineSnapshot
import rmjarvis.ultiobserver.wearprotocol.WearGuidancePresentation
import rmjarvis.ultiobserver.wearprotocol.WearProtocolCodec
import rmjarvis.ultiobserver.wearprotocol.WearRatioSnapshot
import rmjarvis.ultiobserver.wearprotocol.WearRequestAction
import rmjarvis.ultiobserver.wearprotocol.WearSnapshotPullDirection
import rmjarvis.ultiobserver.wearprotocol.WearSnapshotStatus
import rmjarvis.ultiobserver.wearprotocol.WearStateSnapshot
import rmjarvis.ultiobserver.wearprotocol.WearStartupResponse
import rmjarvis.ultiobserver.wearprotocol.WearStatusMessageTransition
import rmjarvis.ultiobserver.wearprotocol.WearTeamActionsSnapshot
import rmjarvis.ultiobserver.wearprotocol.WearTeamId
import rmjarvis.ultiobserver.wearprotocol.WearTeamSnapshot

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
    fun publish(
        game: GameState?,
        settings: Settings,
        actionsAvailable: Boolean,
    ) {
        publish(
            buildWearStateSnapshot(
                game = game,
                settings = settings,
                now = System.currentTimeMillis(),
                actionsAvailable = actionsAvailable,
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
        val requestedAction = WearRequestAction.fromPath(requestedActionPath) ?: return null
        return when (requestedAction) {
            WearRequestAction.STARTUP -> handleStartupRequest()
            WearRequestAction.GOAL -> handleGoalRequest(request)
            WearRequestAction.DECISION -> handleDecisionRequest(request)
        }
    }

    private fun handleStartupRequest(): Task<ByteArray> {
        val app = application as UltiObserverApplication
        val now = System.currentTimeMillis()
        return Tasks.forResult(
            WearProtocolCodec.encode(
                WearStartupResponse.serializer(),
                WearStartupResponse(
                    phoneEpochMillis = now,
                    snapshot = app.currentWearSnapshot(now),
                )
            )
        )
    }

    private fun handleGoalRequest(requestBytes: ByteArray): Task<ByteArray> {
        val request = WearProtocolCodec.decode(WearGoalRequest.serializer(), requestBytes)
        val app = application as UltiObserverApplication
        val now = System.currentTimeMillis()
        val snapshot = app.appState.state.value
        val game = snapshot.currentGame
        val scoringTeam = when (request.scoringTeam) {
            WearTeamId.TEAM_ONE -> TeamId.TEAM_ONE
            WearTeamId.TEAM_TWO -> TeamId.TEAM_TWO
        }
        val applied = if (
            game != null &&
            snapshot.settings.timingAlerts.watchConnectionMode == WatchConnectionMode.WEAR_OS &&
            snapshot.viewingActiveGameScreen &&
            game.pendingGameDecision() == null &&
            wearStateToken(game) == request.stateToken
        ) {
            app.appState.recordGoal(
                currentGame = game,
                scoringTeam = scoringTeam,
                now = now,
            )
        } else {
            false
        }
        return gameActionResponse(app, applied, now)
    }

    private fun handleDecisionRequest(requestBytes: ByteArray): Task<ByteArray> {
        val request = WearProtocolCodec.decode(WearDecisionRequest.serializer(), requestBytes)
        val app = application as UltiObserverApplication
        val now = System.currentTimeMillis()
        val snapshot = app.appState.state.value
        val game = snapshot.currentGame
        val applied = if (
            game != null &&
            snapshot.settings.timingAlerts.watchConnectionMode == WatchConnectionMode.WEAR_OS &&
            snapshot.viewingActiveGameScreen &&
            wearStateToken(game) == request.stateToken
        ) {
            app.appState.resolveDecision(
                currentGame = game,
                accept = request.accept,
                now = now,
            )
        } else {
            false
        }
        return gameActionResponse(app, applied, now)
    }

    private fun gameActionResponse(
        app: UltiObserverApplication,
        applied: Boolean,
        now: Long,
    ): Task<ByteArray> {
        val snapshot = app.currentWearSnapshot(now)
        app.wearStatePublisher.publish(snapshot)
        return Tasks.forResult(
            WearProtocolCodec.encode(
                WearGameActionResponse.serializer(),
                WearGameActionResponse(
                    applied = applied,
                    snapshot = snapshot,
                )
            )
        )
    }
}

/** Build the application's current authoritative state for one direct watch response. */
private fun UltiObserverApplication.currentWearSnapshot(now: Long): WearStateSnapshot {
    val appState = appState.state.value
    return buildWearStateSnapshot(
        game = appState.currentGame,
        settings = appState.settings,
        now = now,
        actionsAvailable = appState.viewingActiveGameScreen,
    )
}

/** Build the complete read-only companion snapshot from authoritative phone state. */
internal fun buildWearStateSnapshot(
    game: GameState?,
    settings: Settings,
    now: Long,
    actionsAvailable: Boolean,
): WearStateSnapshot {
    if (settings.timingAlerts.watchConnectionMode != WatchConnectionMode.WEAR_OS) {
        return WearStateSnapshot(
            status = WearSnapshotStatus.DISABLED,
            activeGame = null,
        )
    }
    if (
        game == null ||
        game.phase == GamePhase.SETUP
    ) {
        return WearStateSnapshot(
            status = WearSnapshotStatus.NO_ACTIVE_GAME,
            activeGame = null,
        )
    }

    val activeCountdown = game.activeCountdown(now)
    val currentRatio = game.currentGenderRatio()
    val pendingDecision = if (actionsAvailable) game.pendingGameDecision() else null
    val gameOver = game.phase == GamePhase.GAME_OVER
    val gameActionsAvailable = actionsAvailable && !gameOver && pendingDecision == null
    return WearStateSnapshot(
        status = WearSnapshotStatus.ACTIVE_GAME,
        activeGame = WearActiveGameSnapshot(
            stateToken = wearStateToken(game),
            actionsAvailable = gameActionsAvailable,
            gameOver = gameOver,
            officialClockOffsetMillis = game.officialClockOffsetMillis,
            officialTimeZoneId = game.timeZone.id,
            upcomingCaps = game.upcomingCapStatuses(now).map { status ->
                WearCapSnapshot(
                    label = status.label,
                    targetEpochMillis = status.targetEpoch,
                )
            },
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
            statusMessageTransitions = game.wearStatusMessageTransitions(now),
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
            pendingDecision = pendingDecision?.wearSnapshot(settings.ruleGuidanceMode),
        ),
    )
}

/** Build each timed change to the phone-owned status text for the current game screen. */
private fun GameState.wearStatusMessageTransitions(
    now: Long,
): List<WearStatusMessageTransition> {
    if (phase == GamePhase.GAME_OVER) {
        return listOf(
            WearStatusMessageTransition(
                targetEpochMillis = now,
                message = "Game over",
            )
        )
    }
    if (phase != GamePhase.LIVE_POINT) {
        return emptyList()
    }
    val candidates = (
        listOf(now) + CapType.entries
            .map { capType -> capEpoch(capType) }
            .filter { targetEpoch -> targetEpoch > now }
        )
        .distinct()
        .sorted()
        .map { targetEpoch ->
            WearStatusMessageTransition(
                targetEpochMillis = targetEpoch,
                message = capStatusMessage(targetEpoch),
            )
        }
    return candidates
        .filterIndexed { index, candidate ->
            index == 0 || candidate.message != candidates[index - 1].message
        }
        .dropWhile { transition -> transition.message == null }
}

/** Build one team and its phone-equivalent compact action labels. */
private fun GameState.wearTeamSnapshot(
    teamId: TeamId,
    now: Long,
): WearTeamSnapshot {
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

/** Convert the phone's existing prompt copy and guidance policy into protocol display data. */
private fun GamePrompt.PendingDecision.wearSnapshot(
    guidanceMode: RuleGuidanceMode,
): WearDecisionSnapshot {
    val presentation = guidanceMode.presentation(requiresGuidanceInNone())
    return WearDecisionSnapshot(
        title = formatTitle(),
        messageLines = formatMessage().lines.map { line ->
            WearGuidanceLineSnapshot(
                text = line.text,
                bold = line.bold,
            )
        },
        confirmLabel = "OK",
        dismissLabel = "Not yet",
        presentation = when (presentation) {
            RuleGuidancePresentation.VISIBLE -> WearGuidancePresentation.VISIBLE
            RuleGuidancePresentation.VISIBLE_TIMED -> WearGuidancePresentation.VISIBLE_TIMED
            RuleGuidancePresentation.HIDDEN_AUTO_ACCEPT ->
                WearGuidancePresentation.HIDDEN_AUTO_ACCEPT
        },
        autoAcceptDelayMillis = if (presentation == RuleGuidancePresentation.VISIBLE_TIMED) {
            ruleGuidanceTimeoutMillis
        } else {
            null
        },
    )
}

/** Stable identity used to reject commands based on an older committed current game. */
internal fun wearStateToken(game: GameState): String {
    return MessageDigest.getInstance("SHA-256")
        .digest(encodeCurrentGame(game).encodeToByteArray())
        .joinToString("") { byte -> "%02x".format(byte) }
}

/** Return the same black-or-white content ARGB used for custom phone display colors. */
private fun readableContentArgb(backgroundArgb: Long): Long {
    return readableContentColor(Color(backgroundArgb)).toArgb().toLong() and 0xFFFFFFFFL
}
