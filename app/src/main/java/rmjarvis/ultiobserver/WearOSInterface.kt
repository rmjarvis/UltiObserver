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
import rmjarvis.ultiobserver.wearprotocol.WearActionConfirmation
import rmjarvis.ultiobserver.wearprotocol.WearActiveGameSnapshot
import rmjarvis.ultiobserver.wearprotocol.WearCapSnapshot
import rmjarvis.ultiobserver.wearprotocol.WearConfirmActionRequest
import rmjarvis.ultiobserver.wearprotocol.WearCountdownSnapshot
import rmjarvis.ultiobserver.wearprotocol.WearCueSnapshot
import rmjarvis.ultiobserver.wearprotocol.WearDecisionRequest
import rmjarvis.ultiobserver.wearprotocol.WearGameActionResponse
import rmjarvis.ultiobserver.wearprotocol.WearGoalRequest
import rmjarvis.ultiobserver.wearprotocol.WearGuidanceLineSnapshot
import rmjarvis.ultiobserver.wearprotocol.WearGuidancePresentation
import rmjarvis.ultiobserver.wearprotocol.WearPromptSnapshot
import rmjarvis.ultiobserver.wearprotocol.WearProtocolCodec
import rmjarvis.ultiobserver.wearprotocol.WearPullViolationOption
import rmjarvis.ultiobserver.wearprotocol.WearPullViolationType
import rmjarvis.ultiobserver.wearprotocol.WearRatioSnapshot
import rmjarvis.ultiobserver.wearprotocol.WearRequestAction
import rmjarvis.ultiobserver.wearprotocol.WearSnapshotPullDirection
import rmjarvis.ultiobserver.wearprotocol.WearSnapshotStatus
import rmjarvis.ultiobserver.wearprotocol.WearStateSnapshot
import rmjarvis.ultiobserver.wearprotocol.WearStartupResponse
import rmjarvis.ultiobserver.wearprotocol.WearStatusMessageTransition
import rmjarvis.ultiobserver.wearprotocol.WearTeamAction
import rmjarvis.ultiobserver.wearprotocol.WearTeamActionRequest
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
            WearRequestAction.TEAM_ACTION -> handleTeamActionRequest(request)
            WearRequestAction.CONFIRM_ACTION -> handleConfirmActionRequest(request)
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
        val game = snapshot.gameOnWatch(request.stateToken)
        val scoringTeam = when (request.scoringTeam) {
            WearTeamId.TEAM_ONE -> TeamId.TEAM_ONE
            WearTeamId.TEAM_TWO -> TeamId.TEAM_TWO
        }
        val applied = if (
            game != null &&
            game.pendingGameDecision() == null &&
            game.phase != GamePhase.GAME_OVER
        ) {
            app.appState.recordGoal(
                currentGame = game,
                scoringTeam = scoringTeam,
                now = now,
            )
        } else {
            false
        }
        return gameActionResponse(
            app = app,
            applied = applied,
            now = now,
            confirmation = null,
        )
    }

    private fun handleDecisionRequest(requestBytes: ByteArray): Task<ByteArray> {
        val request = WearProtocolCodec.decode(WearDecisionRequest.serializer(), requestBytes)
        val app = application as UltiObserverApplication
        val now = System.currentTimeMillis()
        val snapshot = app.appState.state.value
        val game = snapshot.gameOnWatch(request.stateToken)
        val applied = if (
            game != null
        ) {
            app.appState.resolveDecision(
                currentGame = game,
                accept = request.accept,
                now = now,
            )
        } else {
            false
        }
        return gameActionResponse(
            app = app,
            applied = applied,
            now = now,
            confirmation = null,
        )
    }

    private fun handleTeamActionRequest(requestBytes: ByteArray): Task<ByteArray> {
        val request = WearProtocolCodec.decode(
            WearTeamActionRequest.serializer(),
            requestBytes,
        )
        val app = application as UltiObserverApplication
        val now = System.currentTimeMillis()
        val snapshot = app.appState.state.value
        val game = snapshot.gameOnWatch(request.stateToken)
        val confirmation = if (
            game != null &&
            game.pendingGameDecision() == null &&
            game.phase != GamePhase.GAME_OVER
        ) {
            game.actionConfirmation(
                request = request,
                requestedAt = now,
                settings = snapshot.settings,
            )?.wearConfirmation(
                stateToken = request.stateToken,
                guidanceMode = snapshot.settings.ruleGuidanceMode,
            )
        } else {
            null
        }
        return gameActionResponse(
            app = app,
            applied = false,
            now = now,
            confirmation = confirmation,
        )
    }

    private fun handleConfirmActionRequest(requestBytes: ByteArray): Task<ByteArray> {
        val request = WearProtocolCodec.decode(
            WearConfirmActionRequest.serializer(),
            requestBytes,
        )
        val app = application as UltiObserverApplication
        val now = System.currentTimeMillis()
        val snapshot = app.appState.state.value
        val game = snapshot.gameOnWatch(request.confirmation.stateToken)
        val confirmation = if (game != null && game.pendingGameDecision() == null) {
            request.confirmation.gamePrompt(
                game = game,
            )
        } else {
            null
        }
        val applied = confirmation != null && app.appState.confirmAction(confirmation)
        return gameActionResponse(
            app = app,
            applied = applied,
            now = now,
            confirmation = null,
        )
    }

    private fun gameActionResponse(
        app: UltiObserverApplication,
        applied: Boolean,
        now: Long,
        confirmation: WearActionConfirmation?,
    ): Task<ByteArray> {
        val snapshot = app.currentWearSnapshot(now)
        app.wearStatePublisher.publish(snapshot)
        return Tasks.forResult(
            WearProtocolCodec.encode(
                WearGameActionResponse.serializer(),
                WearGameActionResponse(
                    applied = applied,
                    snapshot = snapshot,
                    confirmation = confirmation,
                )
            )
        )
    }
}

/** Build the phone-domain confirmation requested by one watch team action. */
private fun GameState.actionConfirmation(
    request: WearTeamActionRequest,
    requestedAt: Long,
    settings: Settings,
): GamePrompt.ActionConfirmation? {
    val team = request.team.toTeamId()
    return when (request.action) {
        WearTeamAction.Timeout -> {
            if (!canRequestTimeout(requestedAt)) {
                null
            } else {
                val timeoutAt = if (phase == GamePhase.LIVE_POINT) {
                    settings.adjustedCountdownStartEpoch(requestedAt)
                } else {
                    requestedAt
                }
                GamePrompt.TimeoutConfirmation(this, team, timeoutAt)
            }
        }
        WearTeamAction.TimeViolation -> {
            if (previewTimeViolation(team) == null) {
                null
            } else {
                GamePrompt.TimeViolationConfirmation(this, team, requestedAt)
            }
        }
        WearTeamAction.PullViolation -> {
            val violation = pullViolationTypeFor(team)
            if (previewPullViolation(team, violation) == null) {
                null
            } else {
                GamePrompt.PullViolationConfirmation(this, team, requestedAt, violation)
            }
        }
        WearTeamAction.TechnicalFoul -> GamePrompt.TechnicalFoulConfirmation(
            state = this,
            team = team,
            requestedAt = requestedAt,
        )
    }
}

/** Convert one phone-domain action confirmation to its watch protocol representation. */
internal fun GamePrompt.ActionConfirmation.wearConfirmation(
    stateToken: String,
    guidanceMode: RuleGuidanceMode,
): WearActionConfirmation {
    return when (this) {
        is GamePrompt.TimeoutConfirmation -> WearActionConfirmation.Timeout(
            stateToken = stateToken,
            team = team.toWearTeamId(),
            requestedAtPhoneEpochMillis = requestedAt,
            prompt = wearSnapshot(guidanceMode),
        )
        is GamePrompt.TimeViolationConfirmation -> WearActionConfirmation.TimeViolation(
            stateToken = stateToken,
            team = team.toWearTeamId(),
            requestedAtPhoneEpochMillis = requestedAt,
            prompt = wearSnapshot(guidanceMode),
        )
        is GamePrompt.PullViolationConfirmation -> {
            WearActionConfirmation.PullViolation(
                stateToken = stateToken,
                team = team.toWearTeamId(),
                requestedAtPhoneEpochMillis = requestedAt,
                selectedViolation = violation.toWearPullViolationType(),
                options = event.pullViolationSelections().map { selection ->
                    val confirmation = GamePrompt.PullViolationConfirmation(
                        state = state,
                        team = team,
                        requestedAt = requestedAt,
                        violation = selection.violation,
                    )
                    WearPullViolationOption(
                        violation = confirmation.violation.toWearPullViolationType(),
                        actionLabel = selection.actionLabel,
                        prompt = confirmation.wearSnapshot(guidanceMode),
                    )
                },
                prompt = wearSnapshot(guidanceMode),
            )
        }
        is GamePrompt.TechnicalFoulConfirmation -> WearActionConfirmation.TechnicalFoul(
            stateToken = stateToken,
            team = team.toWearTeamId(),
            requestedAtPhoneEpochMillis = requestedAt,
            prompt = wearSnapshot(guidanceMode),
        )
    }
}

/** Rebuild the phone-domain action represented by an accepted watch confirmation. */
private fun WearActionConfirmation.gamePrompt(
    game: GameState,
): GamePrompt.ActionConfirmation? {
    return when (this) {
        is WearActionConfirmation.Timeout -> {
            if (!game.canRequestTimeout(requestedAtPhoneEpochMillis)) {
                null
            } else {
                GamePrompt.TimeoutConfirmation(
                    state = game,
                    team = team.toTeamId(),
                    requestedAt = requestedAtPhoneEpochMillis,
                )
            }
        }
        is WearActionConfirmation.TimeViolation -> {
            val phoneTeam = team.toTeamId()
            if (game.previewTimeViolation(phoneTeam) == null) {
                null
            } else {
                GamePrompt.TimeViolationConfirmation(
                    state = game,
                    team = phoneTeam,
                    requestedAt = requestedAtPhoneEpochMillis,
                )
            }
        }
        is WearActionConfirmation.PullViolation -> {
            val phoneTeam = team.toTeamId()
            val phoneViolation = selectedViolation.toPullViolationType()
            if (game.previewPullViolation(phoneTeam, phoneViolation) == null) {
                null
            } else {
                GamePrompt.PullViolationConfirmation(
                    state = game,
                    team = phoneTeam,
                    requestedAt = requestedAtPhoneEpochMillis,
                    violation = phoneViolation,
                )
            }
        }
        is WearActionConfirmation.TechnicalFoul -> GamePrompt.TechnicalFoulConfirmation(
            state = game,
            team = team.toTeamId(),
            requestedAt = requestedAtPhoneEpochMillis,
        )
    }
}

/** Return the current game if the watch may act on the supplied state token. */
private fun AppStateSnapshot.gameOnWatch(stateToken: String): GameState? {
    val game = currentGame ?: return null
    return game.takeIf {
        settings.timingAlerts.watchConnectionMode == WatchConnectionMode.WEAR_OS &&
            viewingActiveGameScreen &&
            wearStateToken(game) == stateToken
    }
}

/** Convert the protocol team identity to the phone's game model. */
private fun WearTeamId.toTeamId(): TeamId {
    return when (this) {
        WearTeamId.TEAM_ONE -> TeamId.TEAM_ONE
        WearTeamId.TEAM_TWO -> TeamId.TEAM_TWO
    }
}

/** Convert a shared pull-violation type to the phone domain. */
private fun WearPullViolationType.toPullViolationType(): PullViolationType {
    return when (this) {
        WearPullViolationType.OFFSIDES -> PullViolationType.OFFSIDES
        WearPullViolationType.FALSE_START -> PullViolationType.FALSE_START
        WearPullViolationType.MAJORITY_PULL -> PullViolationType.MAJORITY_PULL
    }
}

/** Convert the phone team identity to the shared protocol type. */
private fun TeamId.toWearTeamId(): WearTeamId {
    return when (this) {
        TeamId.TEAM_ONE -> WearTeamId.TEAM_ONE
        TeamId.TEAM_TWO -> WearTeamId.TEAM_TWO
    }
}

/** Convert a phone pull-violation type to the shared protocol. */
private fun PullViolationType.toWearPullViolationType(): WearPullViolationType {
    return when (this) {
        PullViolationType.OFFSIDES -> WearPullViolationType.OFFSIDES
        PullViolationType.FALSE_START -> WearPullViolationType.FALSE_START
        PullViolationType.MAJORITY_PULL -> WearPullViolationType.MAJORITY_PULL
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
): WearPromptSnapshot {
    return wearPromptSnapshot(
        title = formatTitle(),
        message = formatMessage(),
        confirmLabel = "OK",
        dismissLabel = "Not yet",
        guidanceMode = guidanceMode,
        requiredInNone = requiresGuidanceInNone(),
    )
}

/** Convert one action confirmation to the phone-owned copy and guidance presentation. */
internal fun GamePrompt.ActionConfirmation.wearSnapshot(
    guidanceMode: RuleGuidanceMode,
): WearPromptSnapshot {
    return wearPromptSnapshot(
        title = formatTitle(),
        message = guidanceMessage(guidanceMode),
        confirmLabel = "OK",
        dismissLabel = "Cancel",
        guidanceMode = guidanceMode,
        requiredInNone = requiresGuidanceInNone(),
    )
}

/** Build common watch prompt display data from phone-owned guidance. */
private fun wearPromptSnapshot(
    title: String,
    message: RuleGuidanceMessage,
    confirmLabel: String,
    dismissLabel: String,
    guidanceMode: RuleGuidanceMode,
    requiredInNone: Boolean,
): WearPromptSnapshot {
    val presentation = guidanceMode.presentation(requiredInNone)
    return WearPromptSnapshot(
        title = title,
        messageLines = message.lines.map { line ->
            WearGuidanceLineSnapshot(
                text = line.text,
                bold = line.bold,
            )
        },
        confirmLabel = confirmLabel,
        dismissLabel = dismissLabel,
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
