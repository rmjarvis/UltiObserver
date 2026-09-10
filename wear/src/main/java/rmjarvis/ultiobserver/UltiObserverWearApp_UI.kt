package rmjarvis.ultiobserver

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Text
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import rmjarvis.ultiobserver.ui.theme.UltiObserverTheme
import rmjarvis.ultiobserver.wearprotocol.WearActionConfirmation
import rmjarvis.ultiobserver.wearprotocol.WearActiveGameSnapshot
import rmjarvis.ultiobserver.wearprotocol.WearSnapshotPullDirection
import rmjarvis.ultiobserver.wearprotocol.WearSnapshotStatus
import rmjarvis.ultiobserver.wearprotocol.WearTeamAction
import rmjarvis.ultiobserver.wearprotocol.WearTeamActionPrompt
import rmjarvis.ultiobserver.wearprotocol.WearTeamSnapshot

/** Route synchronized phone state to the watch's idle, game, or team-action surface. */
@Composable
internal fun UltiObserverWearApp(
    receivedState: ReceivedState?,
    connectionState: ConnectionState,
    onRetry: () -> Unit,
    onGoal: (TeamId, String, (Boolean) -> Unit) -> Unit,
    onUndo: (String, (Boolean) -> Unit) -> Unit,
    onDecision: (String, Boolean, (Boolean) -> Unit) -> Unit,
    onTeamAction: (TeamId, String, WearTeamAction, (WearTeamActionPrompt?) -> Unit) -> Unit,
    onStartCardEntry: (TeamId, String, CardType, String, (Boolean) -> Unit) -> Unit,
    onCancelCardEntry: (TeamId, String, CardType?, String, (Boolean) -> Unit) -> Unit,
    onConfirmAction: (WearActionConfirmation, (Boolean) -> Unit) -> Unit,
) {
    var selectedTeam by remember { mutableIntStateOf(0) }
    var pendingActionPrompt by remember {
        mutableStateOf<WearTeamActionPrompt?>(null)
    }
    var cardChoiceStateToken by remember { mutableStateOf<String?>(null) }
    var playerCard by remember { mutableStateOf<WearTeamAction.PlayerCard?>(null) }
    var phoneCardEntryWasActive by remember { mutableStateOf(false) }
    var returnToCardChoicesForTeam by remember { mutableStateOf<TeamId?>(null) }
    val snapshot = receivedState?.snapshot
    val phoneReachable = connectionState == ConnectionState.CONNECTED
    val phoneCardEntry = snapshot?.activeGame?.phoneCardEntry

    LaunchedEffect(
        phoneReachable,
        snapshot?.status,
        snapshot?.activeGame?.actionsAvailable,
        snapshot?.activeGame?.stateToken,
        phoneCardEntry,
    ) {
        if (
            !phoneReachable ||
            snapshot?.status != WearSnapshotStatus.ACTIVE_GAME
        ) {
            phoneCardEntryWasActive = false
            returnToCardChoicesForTeam = null
        } else if (phoneCardEntry != null) {
            phoneCardEntryWasActive = true
        } else if (phoneCardEntryWasActive) {
            val returnTeam = returnToCardChoicesForTeam
            if (returnTeam == null) {
                selectedTeam = 0
                cardChoiceStateToken = null
                playerCard = null
            } else {
                selectedTeam = if (returnTeam == TeamId.TEAM_ONE) 1 else 2
                cardChoiceStateToken = snapshot.activeGame?.stateToken
            }
            phoneCardEntryWasActive = false
            returnToCardChoicesForTeam = null
        }
        if (
            !phoneReachable ||
            snapshot?.status != WearSnapshotStatus.ACTIVE_GAME ||
            (
                snapshot.activeGame?.actionsAvailable != true &&
                    snapshot.activeGame?.phoneCardEntry == null
                )
        ) {
            selectedTeam = 0
            cardChoiceStateToken = null
            playerCard = null
        }
        if (
            !phoneReachable ||
            pendingActionPrompt?.stateToken != snapshot?.activeGame?.stateToken
        ) {
            pendingActionPrompt = null
        }
        if (cardChoiceStateToken != snapshot?.activeGame?.stateToken) {
            if (cardChoiceStateToken != null && phoneCardEntry != null) {
                selectedTeam = 0
            }
            cardChoiceStateToken = null
            playerCard = null
        }
    }
    BackHandler(
        enabled = selectedTeam != 0 &&
            pendingActionPrompt == null &&
            playerCard == null,
    ) {
        if (cardChoiceStateToken != null) {
            cardChoiceStateToken = null
        } else {
            selectedTeam = 0
        }
    }

    when {
        snapshot == null && connectionState == ConnectionState.CONNECTING ->
            MessageScreen("Connecting…")
        snapshot == null -> MessageScreen(
            message = "Could not find a paired phone running UltiObserver.",
            onRetry = onRetry,
        )
        snapshot.status == WearSnapshotStatus.DISABLED -> DisabledScreen()
        snapshot.status == WearSnapshotStatus.NO_ACTIVE_GAME && !phoneReachable ->
            MessageScreen("Lost connection", onRetry)
        snapshot.status == WearSnapshotStatus.NO_ACTIVE_GAME ->
            MessageScreen("No active game")
        else -> {
            val activeGame = snapshot.activeGame!!
            val pendingDecision = activeGame.pendingDecision
            if (phoneReachable && pendingDecision != null) {
                DecisionScreen(
                    decision = pendingDecision,
                    stateToken = activeGame.stateToken,
                    onDecision = onDecision,
                )
            } else if (phoneReachable && phoneCardEntry != null) {
                ContinueOnPhoneScreen(
                    onCancel = { onFinished ->
                        returnToCardChoicesForTeam = phoneCardEntry.team
                        onCancelCardEntry(
                            phoneCardEntry.team,
                            activeGame.stateToken,
                            phoneCardEntry.cardType,
                            phoneCardEntry.jerseyNumber,
                        ) { cancelled ->
                            if (!cancelled) {
                                returnToCardChoicesForTeam = null
                            }
                            onFinished(cancelled)
                        }
                    },
                )
            } else if (phoneReachable && pendingActionPrompt != null) {
                when (val actionPrompt = pendingActionPrompt!!) {
                    is WearActionConfirmation -> ActionConfirmationScreen(
                        confirmation = actionPrompt,
                        onConfirmationChange = { pendingActionPrompt = it },
                        onConfirm = { confirmedAction, onFinished ->
                            onConfirmAction(confirmedAction) { applied ->
                                pendingActionPrompt = null
                                if (applied) {
                                    selectedTeam = 0
                                    cardChoiceStateToken = null
                                    playerCard = null
                                }
                                onFinished(applied)
                            }
                        },
                        onCancel = {
                            pendingActionPrompt = null
                        },
                    )
                    is WearTeamActionPrompt.Notice -> ActionNoticeScreen(
                        notice = actionPrompt,
                        onDismiss = { pendingActionPrompt = null },
                    )
                }
            } else {
                ActiveGameScreen(
                    receivedState = receivedState,
                    activeGame = activeGame,
                    phoneReachable = phoneReachable,
                    selectedTeam = selectedTeam,
                    onSelectedTeamChange = { selectedTeam = it },
                    onRetry = onRetry,
                    onGoal = onGoal,
                    onUndo = onUndo,
                    onTeamAction = onTeamAction,
                    onStartCardEntry = onStartCardEntry,
                    onPrompt = { prompt ->
                        pendingActionPrompt = prompt
                    },
                    cardChoiceStateToken = cardChoiceStateToken,
                    onCardChoiceStateTokenChange = { cardChoiceStateToken = it },
                    playerCard = playerCard,
                    onPlayerCardChange = { playerCard = it },
                )
            }
        }
    }
}

/** Render live time-derived values from one received phone snapshot. */
@Composable
private fun ActiveGameScreen(
    receivedState: ReceivedState,
    activeGame: WearActiveGameSnapshot,
    phoneReachable: Boolean,
    selectedTeam: Int,
    onSelectedTeamChange: (Int) -> Unit,
    onRetry: () -> Unit,
    onGoal: (TeamId, String, (Boolean) -> Unit) -> Unit,
    onUndo: (String, (Boolean) -> Unit) -> Unit,
    onTeamAction: (TeamId, String, WearTeamAction, (WearTeamActionPrompt?) -> Unit) -> Unit,
    onStartCardEntry: (TeamId, String, CardType, String, (Boolean) -> Unit) -> Unit,
    onPrompt: (WearTeamActionPrompt) -> Unit,
    cardChoiceStateToken: String?,
    onCardChoiceStateTokenChange: (String?) -> Unit,
    playerCard: WearTeamAction.PlayerCard?,
    onPlayerCardChange: (WearTeamAction.PlayerCard?) -> Unit,
) {
    var commandPending by remember { mutableStateOf(false) }
    val currentPhoneEpochMillis by produceState(
        initialValue = System.currentTimeMillis() + receivedState.phoneClockOffsetMillis,
        receivedState,
    ) {
        while (true) {
            value = System.currentTimeMillis() + receivedState.phoneClockOffsetMillis
            delay(1_000L)
        }
    }
    val display = activeGame.toGameDisplay(
        currentPhoneEpochMillis = currentPhoneEpochMillis,
        connected = phoneReachable,
    )
    if (selectedTeam == 0) {
        GameScreen(
            display = display,
            onTeamOne = {
                onSelectedTeamChange(1)
            },
            onTeamTwo = {
                onSelectedTeamChange(2)
            },
            onRetry = onRetry,
            onUndo = {
                if (!commandPending) {
                    commandPending = true
                    onUndo(activeGame.stateToken) {
                        commandPending = false
                    }
                }
            },
        )
    } else {
        val selectedWearTeam = if (selectedTeam == 1) {
            TeamId.TEAM_ONE
        } else {
            TeamId.TEAM_TWO
        }
        val requestPrompt: (WearTeamAction) -> Unit = { action ->
            if (!commandPending) {
                commandPending = true
                onTeamAction(
                    selectedWearTeam,
                    activeGame.stateToken,
                    action,
                ) { prompt ->
                    commandPending = false
                    prompt?.let(onPrompt)
                }
            }
        }
        val selectedTeamSnapshot = if (selectedTeam == 1) {
            activeGame.teamOne
        } else {
            activeGame.teamTwo
        }
        if (cardChoiceStateToken == activeGame.stateToken && playerCard != null) {
            PlayerCardEntryOptionsScreen(
                display = selectedTeamSnapshot.toTeamActionsDisplay(!commandPending),
                cardType = playerCard.cardType,
                jerseyNumber = playerCard.jerseyNumber,
                enabled = !commandPending,
                onJerseyNumberChange = { jerseyNumber ->
                    onPlayerCardChange(playerCard.copy(jerseyNumber = jerseyNumber))
                },
                onRecord = {
                    requestPrompt(playerCard)
                },
                onContinueOnPhone = {
                    if (!commandPending) {
                        commandPending = true
                        onStartCardEntry(
                            selectedWearTeam,
                            activeGame.stateToken,
                            playerCard.cardType,
                            playerCard.jerseyNumber,
                        ) { applied ->
                            commandPending = false
                            if (applied) {
                                onPlayerCardChange(null)
                            }
                        }
                    }
                },
                onCancel = {
                    onPlayerCardChange(null)
                },
            )
        } else if (cardChoiceStateToken == activeGame.stateToken) {
            CardChoiceScreen(
                display = selectedTeamSnapshot.toTeamActionsDisplay(!commandPending),
                enabled = !commandPending,
                onYellow = {
                    onPlayerCardChange(WearTeamAction.PlayerCard(CardType.YELLOW, ""))
                },
                onRed = {
                    onPlayerCardChange(WearTeamAction.PlayerCard(CardType.RED, ""))
                },
                onBlue = {
                    requestPrompt(WearTeamAction.BlueCard)
                },
                onCancel = {
                    onCardChoiceStateTokenChange(null)
                    onPlayerCardChange(null)
                },
            )
        } else {
            TeamActionsScreen(
                display = selectedTeamSnapshot.toTeamActionsDisplay(!commandPending),
                onGoal = {
                    if (!commandPending) {
                        commandPending = true
                        onGoal(
                            selectedWearTeam,
                            activeGame.stateToken,
                        ) { applied ->
                            commandPending = false
                            if (applied) {
                                onSelectedTeamChange(0)
                            }
                        }
                    }
                },
                onTimeViolation = {
                    requestPrompt(WearTeamAction.TimeViolation)
                },
                onPullViolation = {
                    requestPrompt(WearTeamAction.PullViolation)
                },
                onCard = {
                    onCardChoiceStateTokenChange(activeGame.stateToken)
                },
                onTechnicalFoul = {
                    requestPrompt(WearTeamAction.TechnicalFoul)
                },
                onTimeout = {
                    requestPrompt(WearTeamAction.Timeout)
                },
                onCancel = {
                    onSelectedTeamChange(0)
                },
            )
        }
    }
}

/** Convert one protocol game into the existing display-only main-screen model. */
private fun WearActiveGameSnapshot.toGameDisplay(
    currentPhoneEpochMillis: Long,
    connected: Boolean,
): GameDisplay {
    val nextCap = upcomingCaps.firstOrNull { cap ->
        cap.targetEpochMillis >= currentPhoneEpochMillis
    }
    val countdownRemainingMillis = countdown?.let { state ->
        state.targetEpochMillis - (state.pausedAtEpochMillis ?: currentPhoneEpochMillis)
    }
    val nextCue = countdown?.cues?.firstOrNull { cue ->
        cue.targetEpochMillis >= currentPhoneEpochMillis
    }
    val statusMessage = statusMessageTransitions
        .lastOrNull { transition -> transition.targetEpochMillis <= currentPhoneEpochMillis }
        ?.message
    return GameDisplay(
        officialTime = formatOfficialTime(
            epochMillis = currentPhoneEpochMillis + officialClockOffsetMillis,
            timeZoneId = officialTimeZoneId,
        ),
        capStatus = nextCap?.let { cap ->
            val remainingMillis = cap.targetEpochMillis - currentPhoneEpochMillis
            "${cap.label} in ${formatDurationMillis(remainingMillis)}"
        },
        countdownLabel = countdown?.label.orEmpty(),
        countdownValue = if (gameOver) {
            null
        } else {
            countdownRemainingMillis?.let(::formatDurationMillis)
        },
        nextCue = if (gameOver) null else nextCue?.let { cue -> "Next: ${cue.message}" },
        statusMessage = statusMessage.takeIf { countdown == null },
        teamOne = teamOne.toTeamDisplay(),
        teamTwo = teamTwo.toTeamDisplay(),
        pullDirection = when (pullDirection) {
            WearSnapshotPullDirection.LEFT_TO_RIGHT -> PullDirection.LEFT_TO_RIGHT
            WearSnapshotPullDirection.RIGHT_TO_LEFT -> PullDirection.RIGHT_TO_LEFT
        },
        ratioBadge = ratio?.let { badge ->
            RatioBadgeDisplay(
                label = badge.label,
                backgroundColor = Color(badge.backgroundArgb),
                contentColor = Color(badge.contentArgb),
            )
        },
        connected = connected,
        actionsAvailable = actionsAvailable,
        gameOver = gameOver,
        undoDescription = undoDescription,
    )
}

private fun WearTeamSnapshot.toTeamDisplay(): TeamDisplay {
    return TeamDisplay(
        name = name,
        score = score,
        backgroundColor = Color(backgroundArgb),
        contentColor = Color(contentArgb),
    )
}

private fun WearTeamSnapshot.toTeamActionsDisplay(actionsAvailable: Boolean): TeamActionsDisplay {
    return TeamActionsDisplay(
        team = toTeamDisplay(),
        timeViolationLabel = actions.timeViolationLabel,
        pullViolationLabel = actions.pullViolationLabel,
        cardLabel = actions.cardLabel,
        technicalFoulLabel = actions.technicalFoulLabel,
        timeoutLabel = actions.timeoutLabel,
        goalEnabled = actionsAvailable && actions.goalEnabled,
        timeViolationEnabled = actionsAvailable && actions.timeViolationEnabled,
        pullViolationEnabled = actionsAvailable && actions.pullViolationEnabled,
        cardEnabled = actionsAvailable && actions.cardEnabled,
        technicalFoulEnabled = actionsAvailable && actions.technicalFoulEnabled,
        timeoutEnabled = actionsAvailable && actions.timeoutEnabled,
    )
}

@Composable
private fun DisabledScreen() {
    MessageScreen(
        message = buildAnnotatedString {
            append("To use, set ")
            pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
            append("Watch connection")
            pop()
            append(" to Wear OS in the UltiObserver Settings.")
        }
    )
}

@Composable
private fun MessageScreen(
    message: String,
    onRetry: (() -> Unit)? = null,
) {
    MessageScreen(
        message = buildAnnotatedString { append(message) },
        onRetry = onRetry,
    )
}

@Composable
private fun MessageScreen(
    message: androidx.compose.ui.text.AnnotatedString,
    onRetry: (() -> Unit)? = null,
) {
    UltiObserverTheme {
        AppScaffold(
            timeText = { AppTimeText() },
            containerColor = Color.Black,
            contentColor = Color.White,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = message,
                    modifier = Modifier
                        .fillMaxWidth(0.78f)
                        .padding(top = 8.dp),
                    color = Color.White,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    textAlign = TextAlign.Center,
                )
                if (onRetry != null) {
                    RetryLabel(onRetry)
                }
            }
        }
    }
}

/** Text-only retry action shared by the disconnected watch surfaces. */
@Composable
internal fun RetryLabel(
    onRetry: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .width(72.dp)
            .height(40.dp)
            .clickable(
                role = Role.Button,
                onClick = onRetry,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = "Retry",
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

private fun formatOfficialTime(epochMillis: Long, timeZoneId: String): String {
    return Instant.ofEpochMilli(epochMillis)
        .atZone(ZoneId.of(timeZoneId))
        .format(WATCH_TIME_FORMATTER)
}

private fun formatDurationMillis(durationMillis: Long): String {
    val totalSeconds = (durationMillis.coerceAtLeast(0L) / 1_000L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "$minutes:${seconds.toString().padStart(2, '0')}"
}

private val WATCH_TIME_FORMATTER = DateTimeFormatter.ofPattern("h:mm")
