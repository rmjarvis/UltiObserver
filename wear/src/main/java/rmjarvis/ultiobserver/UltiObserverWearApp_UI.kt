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
import androidx.wear.compose.material3.TimeText
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import rmjarvis.ultiobserver.ui.theme.UltiObserverTheme
import rmjarvis.ultiobserver.wearprotocol.WearActiveGameSnapshot
import rmjarvis.ultiobserver.wearprotocol.WearSnapshotPullDirection
import rmjarvis.ultiobserver.wearprotocol.WearSnapshotStatus
import rmjarvis.ultiobserver.wearprotocol.WearTeamId
import rmjarvis.ultiobserver.wearprotocol.WearTeamSnapshot

/** Route synchronized phone state to the watch's idle, game, or team-action surface. */
@Composable
internal fun UltiObserverWearApp(
    receivedState: ReceivedState?,
    connectionState: ConnectionState,
    onRetry: () -> Unit,
    onGoal: (WearTeamId, String, (Boolean) -> Unit) -> Unit,
    onDecision: (String, Boolean, (Boolean) -> Unit) -> Unit,
) {
    var selectedTeam by remember { mutableIntStateOf(0) }
    val snapshot = receivedState?.snapshot
    val phoneReachable = connectionState == ConnectionState.CONNECTED

    LaunchedEffect(
        phoneReachable,
        snapshot?.status,
        snapshot?.activeGame?.actionsAvailable,
    ) {
        if (
            !phoneReachable ||
            snapshot?.status != WearSnapshotStatus.ACTIVE_GAME ||
            snapshot.activeGame?.actionsAvailable != true
        ) {
            selectedTeam = 0
        }
    }
    BackHandler(enabled = selectedTeam != 0) {
        selectedTeam = 0
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
            } else {
                ActiveGameScreen(
                    receivedState = receivedState,
                    activeGame = activeGame,
                    phoneReachable = phoneReachable,
                    selectedTeam = selectedTeam,
                    onSelectedTeamChange = { selectedTeam = it },
                    onRetry = onRetry,
                    onGoal = onGoal,
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
    onGoal: (WearTeamId, String, (Boolean) -> Unit) -> Unit,
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
            onUndo = {},
        )
    } else {
        TeamActionsScreen(
            display = if (selectedTeam == 1) {
                activeGame.teamOne.toTeamActionsDisplay(!commandPending)
            } else {
                activeGame.teamTwo.toTeamActionsDisplay(!commandPending)
            },
            onGoal = {
                if (!commandPending) {
                    commandPending = true
                    onGoal(
                        if (selectedTeam == 1) WearTeamId.TEAM_ONE else WearTeamId.TEAM_TWO,
                        activeGame.stateToken,
                    ) { applied ->
                        commandPending = false
                        if (applied) {
                            onSelectedTeamChange(0)
                        }
                    }
                }
            },
            onTimeViolation = {},
            onPullViolation = {},
            onCard = {},
            onTechnicalFoul = {},
            onTimeout = {},
            onCancel = {
                onSelectedTeamChange(0)
            },
        )
    }
}

/** Convert one protocol game into the existing display-only main-screen model. */
private fun WearActiveGameSnapshot.toGameDisplay(
    currentPhoneEpochMillis: Long,
    connected: Boolean,
): GameDisplay {
    val remainingCapMillis = capTargetEpochMillis?.minus(currentPhoneEpochMillis)
    val countdownRemainingMillis = countdown?.let { state ->
        state.targetEpochMillis - (state.pausedAtEpochMillis ?: currentPhoneEpochMillis)
    }
    val nextCue = countdown?.cues?.firstOrNull { cue ->
        cue.targetEpochMillis >= currentPhoneEpochMillis
    }
    return GameDisplay(
        officialTime = formatOfficialTime(
            epochMillis = currentPhoneEpochMillis + officialClockOffsetMillis,
            timeZoneId = officialTimeZoneId,
        ),
        capStatus = if (remainingCapMillis != null && remainingCapMillis >= 0L) {
            "$capLabel ${formatDurationMillis(remainingCapMillis)}"
        } else {
            null
        },
        countdownLabel = if (gameOver) "Game over" else countdown?.label.orEmpty(),
        countdownValue = if (gameOver) {
            null
        } else {
            countdownRemainingMillis?.let(::formatDurationMillis)
        },
        nextCue = if (gameOver) null else nextCue?.let { cue -> "Next: ${cue.message}" },
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
            timeText = { TimeText() },
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
