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
import rmjarvis.ultiobserver.wearprotocol.WearCancelCardEntryRequest
import rmjarvis.ultiobserver.wearprotocol.WearCardEntryRequest
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
import rmjarvis.ultiobserver.wearprotocol.WearPhoneCardEntrySnapshot
import rmjarvis.ultiobserver.wearprotocol.WearPullViolationOption
import rmjarvis.ultiobserver.wearprotocol.WearRatioSnapshot
import rmjarvis.ultiobserver.wearprotocol.WearRequestAction
import rmjarvis.ultiobserver.wearprotocol.WearSnapshotPullDirection
import rmjarvis.ultiobserver.wearprotocol.WearSnapshotStatus
import rmjarvis.ultiobserver.wearprotocol.WearStateSnapshot
import rmjarvis.ultiobserver.wearprotocol.WearStartupResponse
import rmjarvis.ultiobserver.wearprotocol.WearStatusMessageTransition
import rmjarvis.ultiobserver.wearprotocol.WearTeamAction
import rmjarvis.ultiobserver.wearprotocol.WearTeamActionPrompt
import rmjarvis.ultiobserver.wearprotocol.WearTeamActionRequest
import rmjarvis.ultiobserver.wearprotocol.WearTeamActionsSnapshot
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
        val requestedAction = WearRequestAction.fromPath(requestedActionPath) ?: return null
        return when (requestedAction) {
            WearRequestAction.STARTUP -> handleStartupRequest()
            WearRequestAction.GOAL -> handleGoalRequest(request)
            WearRequestAction.DECISION -> handleDecisionRequest(request)
            WearRequestAction.TEAM_ACTION -> handleTeamActionRequest(request)
            WearRequestAction.CONFIRM_ACTION -> handleConfirmActionRequest(request)
            WearRequestAction.CARD_ENTRY -> handleCardEntryRequest(request)
            WearRequestAction.CANCEL_CARD_ENTRY -> handleCancelCardEntryRequest(request)
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
        val scoringTeam = request.scoringTeam
        val applied = if (
            game != null &&
            snapshot.activeCardEntry == null &&
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
            nextPrompt = null,
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
            nextPrompt = null,
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
        val nextPrompt = if (
            game != null &&
            snapshot.activeCardEntry == null &&
            game.pendingGameDecision() == null &&
            game.phase != GamePhase.GAME_OVER
        ) {
            game.wearActionPrompt(
                request = request,
                requestedAt = now,
                settings = snapshot.settings,
            )
        } else {
            null
        }
        return gameActionResponse(
            app = app,
            applied = false,
            now = now,
            nextPrompt = nextPrompt,
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
        val canApply = game != null &&
            snapshot.activeCardEntry == null &&
            game.pendingGameDecision() == null &&
            game.phase != GamePhase.GAME_OVER
        val confirmation = if (canApply) {
            request.confirmation.gamePrompt(game)
        } else {
            null
        }
        val applied = if (
            canApply && request.confirmation is WearActionConfirmation.CardEntryHandoff
        ) {
            val handoff = request.confirmation as WearActionConfirmation.CardEntryHandoff
            app.appState.updateCardEntry(
                currentGame = game,
                expectedCardEntry = null,
                updatedCardEntry = ActiveCardEntry(
                    team = handoff.team,
                    cardType = handoff.cardType,
                    jerseyNumber = handoff.jerseyNumber,
                ),
            )
        } else {
            confirmation != null && app.appState.confirmAction(confirmation)
        }
        return gameActionResponse(
            app = app,
            applied = applied,
            now = now,
            nextPrompt = null,
        )
    }

    private fun handleCardEntryRequest(requestBytes: ByteArray): Task<ByteArray> {
        val request = WearProtocolCodec.decode(WearCardEntryRequest.serializer(), requestBytes)
        val app = application as UltiObserverApplication
        val now = System.currentTimeMillis()
        val snapshot = app.appState.state.value
        val game = snapshot.gameOnWatch(request.stateToken)
        val applied = game != null &&
            game.pendingGameDecision() == null &&
            game.phase != GamePhase.GAME_OVER &&
            app.appState.updateCardEntry(
                currentGame = game,
                expectedCardEntry = null,
                updatedCardEntry = ActiveCardEntry(
                    team = request.team,
                    cardType = request.cardType,
                    jerseyNumber = request.jerseyNumber,
                ),
            )
        return gameActionResponse(
            app = app,
            applied = applied,
            now = now,
            nextPrompt = null,
        )
    }

    private fun handleCancelCardEntryRequest(requestBytes: ByteArray): Task<ByteArray> {
        val request = WearProtocolCodec.decode(
            WearCancelCardEntryRequest.serializer(),
            requestBytes,
        )
        val app = application as UltiObserverApplication
        val now = System.currentTimeMillis()
        val snapshot = app.appState.state.value
        val game = snapshot.gameOnWatch(request.stateToken)
        val applied = game != null && app.appState.updateCardEntry(
            currentGame = game,
            expectedCardEntry = ActiveCardEntry(
                team = request.team,
                cardType = request.cardType,
                jerseyNumber = request.jerseyNumber,
            ),
            updatedCardEntry = null,
        )
        return gameActionResponse(
            app = app,
            applied = applied,
            now = now,
            nextPrompt = null,
        )
    }

    private fun gameActionResponse(
        app: UltiObserverApplication,
        applied: Boolean,
        now: Long,
        nextPrompt: WearTeamActionPrompt?,
    ): Task<ByteArray> {
        val snapshot = app.currentWearSnapshot(now)
        app.wearStatePublisher.publish(snapshot)
        return Tasks.forResult(
            WearProtocolCodec.encode(
                WearGameActionResponse.serializer(),
                WearGameActionResponse(
                    applied = applied,
                    snapshot = snapshot,
                    nextPrompt = nextPrompt,
                )
            )
        )
    }
}

/** Build the protocol prompt requested by one watch team action. */
private fun GameState.wearActionPrompt(
    request: WearTeamActionRequest,
    requestedAt: Long,
    settings: Settings,
): WearTeamActionPrompt? {
    val team = request.team
    val confirmation = when (val action = request.action) {
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
        WearTeamAction.BlueCard -> GamePrompt.BlueCardConfirmation(
            state = this,
            team = team,
            requestedAt = requestedAt,
        )
        WearTeamAction.TechnicalFoul -> GamePrompt.TechnicalFoulConfirmation(
            state = this,
            team = team,
            requestedAt = requestedAt,
        )
        is WearTeamAction.PlayerCard -> {
            return wearPlayerCardPrompt(
                stateToken = request.stateToken,
                team = team,
                cardType = action.cardType,
                jerseyNumber = action.jerseyNumber,
                requestedAt = requestedAt,
                guidanceMode = settings.ruleGuidanceMode,
            )
        }
    }
    return confirmation?.wearConfirmation(
        stateToken = request.stateToken,
        guidanceMode = settings.ruleGuidanceMode,
    )
}

/** Build the next prompt for a numbered player-card entry. */
internal fun GameState.wearPlayerCardPrompt(
    stateToken: String,
    team: TeamId,
    cardType: CardType,
    jerseyNumber: String,
    requestedAt: Long,
    guidanceMode: RuleGuidanceMode,
): WearTeamActionPrompt? {
    val number = jerseyNumber.trim()
    if (number.isEmpty() || number.any { character -> !character.isDigit() }) {
        return null
    }
    val entry = PlayerCardEntry(number)
    if (entry.checkForMultiplePlayerMatches(playerCards(team)) != null) {
        return WearActionConfirmation.CardEntryHandoff(
            stateToken = stateToken,
            team = team,
            cardType = cardType,
            jerseyNumber = number,
            prompt = WearPromptSnapshot(
                title = "Multiple players",
                messageLines = listOf(
                    WearGuidanceLineSnapshot(
                        text = "Player number $number corresponds to multiple players already recorded.",
                        bold = false,
                    )
                ),
                confirmLabel = "Continue on phone",
                dismissLabel = "Cancel",
                presentation = WearGuidancePresentation.VISIBLE,
                autoAcceptDelayMillis = null,
            ),
        )
    }
    val identity = resolvePlayerIdentity(team, PlayerIdentity(number))
    val suspension = playerSuspensionStatus(playerCards(team), identity)
    if (suspension != null) {
        return WearTeamActionPrompt.Notice(
            stateToken = stateToken,
            prompt = WearPromptSnapshot(
                title = "Invalid card assignment",
                messageLines = listOf(
                    WearGuidanceLineSnapshot(
                        text = "${teamFor(team).name} #$number ${suspension.rejectionText}",
                        bold = false,
                    )
                ),
                confirmLabel = "",
                dismissLabel = "OK",
                presentation = WearGuidancePresentation.VISIBLE,
                autoAcceptDelayMillis = null,
            ),
        )
    } else {
        return GamePrompt.PlayerCardConfirmation(
            state = this,
            team = team,
            cardType = cardType,
            identity = identity,
            reason = CardReason(),
            requestedAt = requestedAt,
        ).wearConfirmation(stateToken, guidanceMode)
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
            team = team,
            requestedAtPhoneEpochMillis = requestedAt,
            prompt = wearSnapshot(guidanceMode),
        )
        is GamePrompt.TimeViolationConfirmation -> WearActionConfirmation.TimeViolation(
            stateToken = stateToken,
            team = team,
            requestedAtPhoneEpochMillis = requestedAt,
            prompt = wearSnapshot(guidanceMode),
        )
        is GamePrompt.PullViolationConfirmation -> {
            WearActionConfirmation.PullViolation(
                stateToken = stateToken,
                team = team,
                requestedAtPhoneEpochMillis = requestedAt,
                selectedViolation = violation,
                options = event.pullViolationSelections().map { selection ->
                    val confirmation = GamePrompt.PullViolationConfirmation(
                        state = state,
                        team = team,
                        requestedAt = requestedAt,
                        violation = selection.violation,
                    )
                    WearPullViolationOption(
                        violation = confirmation.violation,
                        prompt = confirmation.wearSnapshot(guidanceMode),
                    )
                },
                prompt = wearSnapshot(guidanceMode),
            )
        }
        is GamePrompt.BlueCardConfirmation -> WearActionConfirmation.BlueCard(
            stateToken = stateToken,
            team = team,
            requestedAtPhoneEpochMillis = requestedAt,
            prompt = wearSnapshot(guidanceMode),
        )
        is GamePrompt.PlayerCardConfirmation -> WearActionConfirmation.PlayerCard(
            stateToken = stateToken,
            team = team,
            cardType = cardType,
            identity = identity,
            requestedAtPhoneEpochMillis = requestedAt,
            prompt = wearSnapshot(guidanceMode),
        )
        is GamePrompt.TechnicalFoulConfirmation -> WearActionConfirmation.TechnicalFoul(
            stateToken = stateToken,
            team = team,
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
                    team = team,
                    requestedAt = requestedAtPhoneEpochMillis,
                )
            }
        }
        is WearActionConfirmation.TimeViolation -> {
            val phoneTeam = team
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
            val phoneTeam = team
            val phoneViolation = selectedViolation
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
        is WearActionConfirmation.BlueCard -> GamePrompt.BlueCardConfirmation(
            state = game,
            team = team,
            requestedAt = requestedAtPhoneEpochMillis,
        )
        is WearActionConfirmation.PlayerCard -> {
            GamePrompt.PlayerCardConfirmation(
                state = game,
                team = team,
                cardType = cardType,
                identity = identity,
                reason = CardReason(),
                requestedAt = requestedAtPhoneEpochMillis,
            )
        }
        is WearActionConfirmation.TechnicalFoul -> GamePrompt.TechnicalFoulConfirmation(
            state = game,
            team = team,
            requestedAt = requestedAtPhoneEpochMillis,
        )
        is WearActionConfirmation.CardEntryHandoff -> null
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

/** Build the application's current authoritative state for one direct watch response. */
private fun UltiObserverApplication.currentWearSnapshot(now: Long): WearStateSnapshot {
    val appState = appState.state.value
    return buildWearStateSnapshot(
        game = appState.currentGame,
        settings = appState.settings,
        now = now,
        actionsAvailable = appState.viewingActiveGameScreen,
        activeCardEntry = appState.activeCardEntry,
    )
}

/** Build the complete read-only companion snapshot from authoritative phone state. */
internal fun buildWearStateSnapshot(
    game: GameState?,
    settings: Settings,
    now: Long,
    actionsAvailable: Boolean = true,
    activeCardEntry: ActiveCardEntry? = null,
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
    val pendingDecision = if (actionsAvailable && activeCardEntry == null) {
        game.pendingGameDecision()
    } else {
        null
    }
    val gameOver = game.phase == GamePhase.GAME_OVER
    val gameActionsAvailable = actionsAvailable &&
        !gameOver &&
        pendingDecision == null &&
        activeCardEntry == null
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
            phoneCardEntry = activeCardEntry?.let { entry ->
                WearPhoneCardEntrySnapshot(
                    team = entry.team,
                    cardType = entry.cardType,
                    jerseyNumber = entry.jerseyNumber,
                )
            },
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
        dismissLabel = if (this is GamePrompt.PlayerCardConfirmation) "Back" else "Cancel",
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
