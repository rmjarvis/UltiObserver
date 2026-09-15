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
import kotlinx.coroutines.delay
import rmjarvis.ultiobserver.ui.theme.UltiObserverTheme
import rmjarvis.ultiobserver.wearprotocol.WearActionConfirmation
import rmjarvis.ultiobserver.wearprotocol.WearCountdownAction
import rmjarvis.ultiobserver.wearprotocol.WearActiveGameSnapshot
import rmjarvis.ultiobserver.wearprotocol.WearSnapshotStatus
import rmjarvis.ultiobserver.wearprotocol.WearTeamAction
import rmjarvis.ultiobserver.wearprotocol.WearTeamActionPrompt

/** Route synchronized phone state to the watch's idle, game, or team-action surface. */
@Composable
internal fun UltiObserverWearApp(
    receivedState: ReceivedState?,
    connectionState: ConnectionState,
    onRetry: () -> Unit,
    onGoal: (TeamId, String, (Boolean) -> Unit) -> Unit,
    onUndo: (String, (Boolean) -> Unit) -> Unit,
    onCountdownAction: (String, WearCountdownAction, (Boolean, WearTeamActionPrompt?) -> Unit) -> Unit,
    onDecision: (String, Boolean, (Boolean) -> Unit) -> Unit,
    onTeamAction: (TeamId, String, WearTeamAction, (WearTeamActionPrompt?) -> Unit) -> Unit,
    onStartCardEntry: (TeamId, String, CardType, String, (Boolean) -> Unit) -> Unit,
    onCancelCardEntry: (TeamId, String, CardType?, String, (Boolean) -> Unit) -> Unit,
    onConfirmAction: (WearActionConfirmation, (Boolean) -> Unit) -> Unit,
) {
    var navigation by remember { mutableStateOf(NavigationState()) }
    val snapshot = receivedState?.snapshot
    val phoneReachable = connectionState == ConnectionState.CONNECTED
    val phoneCardEntry = snapshot?.activeGame?.phoneCardEntry
    LaunchedEffect(snapshot, connectionState) {
        navigation = navigation.receive(snapshot, connectionState)
    }
    BackHandler(
        enabled = navigation.handlesBack,
        onBack = {
            navigation = navigation.back()
        },
    )

    val screen = navigation.screen(snapshot, connectionState)
    when (screen) {
        WatchScreen.DISABLED -> DisabledScreen()
        WatchScreen.CONNECTING ->
            MessageScreen("Connecting…")
        WatchScreen.UNREACHABLE -> MessageScreen(
            message = "Could not find a paired phone running UltiObserver.",
            onRetry = onRetry,
        )
        WatchScreen.DISCONNECTED ->
            MessageScreen("Lost connection", onRetry)
        WatchScreen.IDLE ->
            MessageScreen("No active game")
        else -> {
            val activeGame = snapshot!!.activeGame!!
            val pendingDecision = activeGame.pendingDecision
            if (screen == WatchScreen.CONFIRMATION) {
                DecisionScreen(
                    decision = pendingDecision!!,
                    stateToken = activeGame.stateToken,
                    onDecision = onDecision,
                )
            } else if (screen == WatchScreen.PHONE_ENTRY) {
                ContinueOnPhoneScreen(
                    onCancel = { onFinished ->
                        navigation = navigation.beginPhoneCancellation(phoneCardEntry!!.team)
                        onCancelCardEntry(
                            phoneCardEntry.team,
                            activeGame.stateToken,
                            phoneCardEntry.cardType,
                            phoneCardEntry.jerseyNumber,
                        ) { cancelled ->
                            navigation = navigation.finishPhoneCancellation(cancelled)
                            onFinished(cancelled)
                        }
                    },
                )
            } else if (screen == WatchScreen.ACTION_PROMPT) {
                // No else branch: all prompt types are covered
                when (val actionPrompt = navigation.pendingActionPrompt!!) {
                    is WearActionConfirmation -> {
                        ActionConfirmationScreen(
                            confirmation = actionPrompt,
                            onConfirmationChange = {
                                navigation = navigation.copy(pendingActionPrompt = it)
                            },
                            onConfirm = { confirmedAction, onFinished ->
                                onConfirmAction(confirmedAction) { applied ->
                                    navigation = navigation.finishConfirmation(applied)
                                    onFinished(applied)
                                }
                            },
                            onCancel = {
                                navigation = navigation.copy(pendingActionPrompt = null)
                            },
                        )
                    }
                    is WearTeamActionPrompt.Notice -> {
                        ActionNoticeScreen(
                            notice = actionPrompt,
                            onDismiss = {
                                navigation = navigation.copy(pendingActionPrompt = null)
                            },
                        )
                    }
                }
            } else {
                ActiveGameScreen(
                    receivedState = receivedState,
                    activeGame = activeGame,
                    phoneReachable = phoneReachable,
                    navigation = navigation,
                    onNavigationChange = {
                        navigation = it(navigation)
                    },
                    onRetry = onRetry,
                    onGoal = onGoal,
                    onUndo = onUndo,
                    onCountdownAction = onCountdownAction,
                    onTeamAction = onTeamAction,
                    onStartCardEntry = onStartCardEntry,
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
    navigation: NavigationState,
    onNavigationChange: ((NavigationState) -> NavigationState) -> Unit,
    onRetry: () -> Unit,
    onGoal: (TeamId, String, (Boolean) -> Unit) -> Unit,
    onUndo: (String, (Boolean) -> Unit) -> Unit,
    onCountdownAction: (String, WearCountdownAction, (Boolean, WearTeamActionPrompt?) -> Unit) -> Unit,
    onTeamAction: (TeamId, String, WearTeamAction, (WearTeamActionPrompt?) -> Unit) -> Unit,
    onStartCardEntry: (TeamId, String, CardType, String, (Boolean) -> Unit) -> Unit,
) {
    var commandPending by remember { mutableStateOf(false) }
    val currentWatchEpochMillis by produceState(
        initialValue = System.currentTimeMillis(),
        receivedState,
    ) {
        while (true) {
            value = System.currentTimeMillis()
            delay(1_000L)
        }
    }
    val currentPhoneEpochMillis = currentWatchEpochMillis + receivedState.phoneClockOffsetMillis
    val display = activeGame.toGameDisplay(
        currentPhoneEpochMillis = currentPhoneEpochMillis,
        connected = phoneReachable,
    )
    val selectedTeam = navigation.selectedTeam
    val playerCard = navigation.playerCard
    val surface = navigation.gameScreen(activeGame.stateToken)
    if (surface == GameSurface.SCORE) {
        GameScreen(
            display = display,
            onTeamOne = {
                onNavigationChange { it.copy(selectedTeam = 1) }
            },
            onTeamTwo = {
                onNavigationChange { it.copy(selectedTeam = 2) }
            },
            onRetry = onRetry,
            timingControlsOpen = navigation.timingControlsOpen,
            onToggleTimingControls = {
                onNavigationChange { it.copy(timingControlsOpen = !it.timingControlsOpen) }
            },
            onCloseTimingControls = {
                onNavigationChange { it.back() }
            },
            countdownActionEnabled = !commandPending,
            onCountdownAction = { action ->
                commandPending = true
                onCountdownAction(activeGame.stateToken, action) { _, prompt ->
                    commandPending = false
                    onNavigationChange { it.copy(pendingActionPrompt = prompt) }
                }
            },
            onUndo = {
                commandPending = true
                onUndo(activeGame.stateToken) {
                    commandPending = false
                }
            },
        )
    } else {
        val selectedWearTeam = if (selectedTeam == 1) {
            TeamId.TEAM_ONE
        } else {
            TeamId.TEAM_TWO
        }
        val onRequestPrompt: (WearTeamAction) -> Unit
        onRequestPrompt = { action ->
            commandPending = true
            onTeamAction(
                selectedWearTeam,
                activeGame.stateToken,
                action,
            ) { prompt ->
                commandPending = false
                onNavigationChange { it.copy(pendingActionPrompt = prompt) }
            }
        }
        val selectedTeamSnapshot = if (selectedTeam == 1) {
            activeGame.teamOne
        } else {
            activeGame.teamTwo
        }
        if (surface == GameSurface.PLAYER_CARD) {
            PlayerCardEntryOptionsScreen(
                display = selectedTeamSnapshot.toTeamActionsDisplay(!commandPending),
                cardType = playerCard!!.cardType,
                jerseyNumber = playerCard.jerseyNumber,
                enabled = !commandPending,
                onJerseyNumberChange = { jerseyNumber ->
                    onNavigationChange { it.copy(playerCard = playerCard.copy(jerseyNumber = jerseyNumber)) }
                },
                onRecord = {
                    onRequestPrompt(playerCard)
                },
                onContinueOnPhone = {
                    commandPending = true
                    onStartCardEntry(
                        selectedWearTeam,
                        activeGame.stateToken,
                        playerCard.cardType,
                        playerCard.jerseyNumber,
                    ) { applied ->
                        commandPending = false
                        onNavigationChange { it.finishHandoff(applied) }
                    }
                },
                onCancel = {
                    onNavigationChange { it.copy(playerCard = null) }
                },
            )
        } else if (surface == GameSurface.CARD_CHOICES) {
            CardChoiceScreen(
                display = selectedTeamSnapshot.toTeamActionsDisplay(!commandPending),
                enabled = !commandPending,
                onYellow = {
                    onNavigationChange { it.copy(playerCard = WearTeamAction.PlayerCard(CardType.YELLOW, "")) }
                },
                onRed = {
                    onNavigationChange { it.copy(playerCard = WearTeamAction.PlayerCard(CardType.RED, "")) }
                },
                onBlue = {
                    onRequestPrompt(WearTeamAction.BlueCard)
                },
                onCancel = {
                    onNavigationChange { it.back() }
                },
            )
        } else {
            TeamActionsScreen(
                display = selectedTeamSnapshot.toTeamActionsDisplay(!commandPending),
                onGoal = {
                    commandPending = true
                    onGoal(
                        selectedWearTeam,
                        activeGame.stateToken,
                    ) { applied ->
                        commandPending = false
                        onNavigationChange { it.finishGoal(applied) }
                    }
                },
                onTimeViolation = {
                    onRequestPrompt(WearTeamAction.TimeViolation)
                },
                onPullViolation = {
                    onRequestPrompt(WearTeamAction.PullViolation)
                },
                onCard = {
                    onNavigationChange { it.copy(cardChoiceStateToken = activeGame.stateToken) }
                },
                onTechnicalFoul = {
                    onRequestPrompt(WearTeamAction.TechnicalFoul)
                },
                onTimeout = {
                    onRequestPrompt(WearTeamAction.Timeout)
                },
                onCancel = {
                    onNavigationChange { it.back() }
                },
            )
        }
    }
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
