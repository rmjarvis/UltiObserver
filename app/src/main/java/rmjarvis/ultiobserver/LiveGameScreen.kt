package rmjarvis.ultiobserver

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import java.time.Duration
import java.time.LocalTime
import java.time.format.DateTimeFormatter

private data class PendingMisconductChoice(
    val baseMessage: String,
)

private data class PendingRedCardChoice(
    val team: TeamId,
    val jerseyNumber: String,
)

private data class PendingUnknownYellowChoice(
    val team: TeamId,
)

// Main live-game screen, including the field view, modal flows, and pop-up cues.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LiveGameScreen(
    state: LiveGameState,
    readOnlySummary: Boolean,
    onStateChange: (LiveGameState) -> Unit,
    onUpdateGameSetup: () -> Unit,
) {
    var showCardsSheet by remember { mutableStateOf(false) }
    var showOtherSheet by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(false) }
    var actionInfoMessage by remember { mutableStateOf<String?>(null) }
    var pendingMisconductChoice by remember { mutableStateOf<PendingMisconductChoice?>(null) }
    var halftimeAlert by remember { mutableStateOf(false) }
    var gameOverAlert by remember { mutableStateOf(false) }

    // Update the display clock once per second so time and cap text stay fresh.
    val currentClockTime by produceState(initialValue = LocalTime.now()) {
        while (true) {
            value = LocalTime.now()
            kotlinx.coroutines.delay(1000)
        }
    }
    val now by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            kotlinx.coroutines.delay(1000)
        }
    }

    val capStatus = remember(now, state) {
        state.computeNextCapStatus(now)
    }
    val activeCountdown = remember(state, now) {
        activeCountdownDisplay(state, now)
    }
    val canStartPoint = remember(state, now) {
        state.phase == LivePhase.BETWEEN_POINTS || halftimeTransitionReady(state, now)
    }

    // Let countdown expiration move the model forward without requiring an observer tap.
    LaunchedEffect(state, now, readOnlySummary) {
        if (!readOnlySummary) {
            val advancedState = state.advanceGameClock(now)
            if (advancedState != state) {
                onStateChange(advancedState)
            }
        }
    }

    // Only show the halftime/game-over alerts when those transitions first happen.
    LaunchedEffect(state.phase, state.lastEvent) {
        if (state.phase == LivePhase.HALFTIME && state.lastEvent == "Halftime.") {
            halftimeAlert = true
        }
        if (!readOnlySummary && state.phase == LivePhase.GAME_OVER && state.lastEvent == "Game over.") {
            gameOverAlert = true
        }
    }

    // Compose the major elements of the live game screen.
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("UltiObserver") },
                actions = {
                    if (!locked && state.phase != LivePhase.GAME_OVER) {
                        TextButton(
                            onClick = { locked = true },
                            modifier = Modifier.testTag("live-top-lock"),
                        ) {
                            Text("Lock")
                        }
                    }
                },
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            // If the game is over, replace the live controls with the summary screen.
            if (state.phase == LivePhase.GAME_OVER) {
                GameOverSummary(state = state, onUndo = {
                    if (state.undoEntry != null) {
                        onStateChange(state.undoLastAction())
                    }
                }, showUndo = !readOnlySummary && state.undoEntry != null)
            } else {
                // Show the current clock and next relevant cap.
                StatusLine(
                    currentTime = currentClockTime,
                    capStatus = capStatus,
                )

                // Show the currently active countdown, if any.
                if (activeCountdown != null) {
                    CountdownLine(
                        countdown = activeCountdown,
                        enabled = !locked,
                        onAdjust = { seconds -> onStateChange(state.addTimeToCountdown(seconds)) },
                    )
                }

                // Sketch the field with two teams and the grass strip between them.
                FieldSketchCard(
                    state = state,
                    interactionsEnabled = !locked,
                    showPullIndicator = !locked,
                    centerContent = {
                        if (locked) {
                            FieldUnlockControl(onUnlock = { locked = false })
                        } else if (canStartPoint) {
                            OutlinedButton(
                                onClick = {
                                    onStateChange(state.beginLivePoint())
                                    locked = true
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                    containerColor = Color.White,
                                    contentColor = Color.Black,
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.Black),
                            ) {
                                Text("Start Point")
                            }
                        } else if (state.phase == LivePhase.LIVE_POINT && state.countdown != null) {
                            OutlinedButton(
                                onClick = {
                                    onStateChange(state.continueLivePoint())
                                    locked = true
                                },
                                shape = RoundedCornerShape(12.dp),
                                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                    containerColor = Color.White,
                                    contentColor = Color.Black,
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.Black),
                            ) {
                                Text("Continue Point")
                            }
                        } else if (state.phase == LivePhase.LIVE_POINT) {
                            OutlinedButton(
                                onClick = { locked = true },
                                modifier = Modifier.testTag("live-center-lock"),
                                shape = RoundedCornerShape(12.dp),
                                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                    containerColor = Color.White,
                                    contentColor = Color.Black,
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.Black),
                            ) {
                                Text("Lock")
                            }
                        }
                    },
                    onGoal = { team -> onStateChange(state.recordGoalFromCurrentState(team, now)) },
                    onTimeout = { team ->
                        val result = state.assessTimeout(team, now)
                        onStateChange(result.state)
                        if (result.message != null) {
                            actionInfoMessage = result.message
                        }
                    },
                    onPullInfraction = { team ->
                        if (team == state.pullingTeam) {
                            val updatedState = state.recordOffsides()
                            onStateChange(updatedState)
                            if (updatedState != state) {
                                actionInfoMessage = updatedState.offsidesResolutionMessage(team)
                            }
                        } else {
                            val updatedState = state.recordFalseStart()
                            onStateChange(updatedState)
                            if (updatedState != state) {
                                actionInfoMessage = falseStartResolutionMessage()
                            }
                        }
                    },
                )

                // Cards / TF and Other sit directly below the field.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = { showCardsSheet = true },
                        enabled = !locked,
                        modifier = Modifier.weight(1f),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black,
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.Black),
                    ) {
                        Text("Cards / TF")
                    }
                    OutlinedButton(
                        onClick = { showOtherSheet = true },
                        enabled = !locked,
                        modifier = Modifier.weight(1f),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black,
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.Black),
                    ) {
                        Text("Other")
                    }
                }

                // Show a visible labeled undo button when the current state has undo history.
                if (state.undoEntry != null) {
                    OutlinedButton(
                        onClick = { onStateChange(state.undoLastAction()) },
                        enabled = !locked,
                        modifier = Modifier.fillMaxWidth(),
                        colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                            containerColor = Color.White,
                            contentColor = Color.Black,
                        ),
                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.Black),
                    ) {
                        Text(state.undoEntry!!.label)
                    }
                }
            }
        }
    }

    // Bottom sheet for the card / technical foul workflow.
    if (showCardsSheet) {
        ModalBottomSheet(onDismissRequest = { showCardsSheet = false }) {
            CardsSheet(
                state = state,
                onAssessment = { result ->
                    onStateChange(result.state)
                    if (result.needsLivePointMisconductChoice) {
                        pendingMisconductChoice = PendingMisconductChoice(result.message)
                    } else {
                        actionInfoMessage = result.message
                    }
                    showCardsSheet = false
                },
            )
        }
    }

    // Bottom sheet for less-common actions and manual corrections.
    if (showOtherSheet) {
        ModalBottomSheet(onDismissRequest = { showOtherSheet = false }) {
            OtherSheet(
                state = state,
                now = now,
                onUpdateGameSetup = onUpdateGameSetup,
                onAction = { updatedState ->
                    onStateChange(updatedState)
                    showOtherSheet = false
                },
            )
        }
    }

    // General informational pop-up for terse field guidance and validation messages.
    if (actionInfoMessage != null) {
        AlertDialog(
            onDismissRequest = { actionInfoMessage = null },
            text = {
                Text(
                    text = actionInfoMessage!!,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                )
            },
            confirmButton = {
                TextButton(onClick = { actionInfoMessage = null }) {
                    Text("OK")
                }
            },
        )
    }

    // Live-point misconduct needs a follow-up choice because the app cannot infer possession.
    if (pendingMisconductChoice != null) {
        AlertDialog(
            onDismissRequest = { pendingMisconductChoice = null },
            title = { Text("Misconduct Penalty") },
            text = {
                Text(
                    text = livePointMisconductPrompt(pendingMisconductChoice!!.baseMessage),
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        actionInfoMessage = livePointMisconductResolutionMessage(
                            pendingMisconductChoice!!.baseMessage,
                            againstOffense = true,
                        )
                        pendingMisconductChoice = null
                    }
                ) {
                    Text("Offense")
                }
            },
            dismissButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            actionInfoMessage = livePointMisconductResolutionMessage(
                                pendingMisconductChoice!!.baseMessage,
                                againstOffense = false,
                            )
                            pendingMisconductChoice = null
                        }
                    ) {
                        Text("Defense")
                    }
                }
            },
        )
    }

    // Cap prompts block until the observer decides whether to apply the newly eligible cap.
    if (state.pendingCapOffer != null) {
        AlertDialog(
            onDismissRequest = {},
            title = { Text("Apply ${capOfferLabel(state.pendingCapOffer!!)}?") },
            text = {
                Text(
                    text = state.capOfferExplanation(),
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            confirmButton = {
                TextButton(onClick = { onStateChange(state.applyPendingCap(now)) }) {
                    Text("Apply")
                }
            },
            dismissButton = {
                TextButton(onClick = { onStateChange(state.deferPendingCap()) }) {
                    Text("No")
                }
            },
        )
    }

    // Halftime cue popup.
    if (halftimeAlert) {
        AlertDialog(
            onDismissRequest = { halftimeAlert = false },
            text = {
                Text(
                    text = "Halftime",
                    style = MaterialTheme.typography.displaySmall,
                    fontWeight = FontWeight.Black,
                )
            },
            confirmButton = {
                TextButton(onClick = { halftimeAlert = false }) {
                    Text("OK")
                }
            },
        )
    }

    // Game-over cue popup.
    if (gameOverAlert) {
        AlertDialog(
            onDismissRequest = { gameOverAlert = false },
            text = {
                Text(
                    text = formatGameOverSummary(state),
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Black,
                )
            },
            confirmButton = {
                TextButton(onClick = { gameOverAlert = false }) {
                    Text("OK")
                }
            },
        )
    }
}

// Full-width unlock slider that only activates if the drag starts on the left side.
@Composable
private fun FieldUnlockControl(
    onUnlock: () -> Unit,
) {
    var trackWidthPx by remember { mutableStateOf(0f) }
    var thumbOffsetPx by remember { mutableStateOf(0f) }
    var dragEnabled by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val thumbDiameter = 40.dp
    val thumbDiameterPx = with(density) { thumbDiameter.toPx() }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Slide right to unlock",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Black,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.SemiBold,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("live-unlock-slider")
                .height(52.dp)
                .onSizeChanged { trackWidthPx = it.width.toFloat() }
                .background(Color(0x66FFFFFF), RoundedCornerShape(26.dp))
                .border(1.dp, Color.Black, RoundedCornerShape(26.dp))
                .pointerInput(trackWidthPx) {
                    detectDragGestures(
                        onDragStart = { offset ->
                            dragEnabled = offset.x <= trackWidthPx * 0.25f
                            if (dragEnabled) {
                                thumbOffsetPx = 0f
                            }
                        },
                        onDragEnd = {
                            val unlockThreshold = trackWidthPx * 0.75f
                            val thumbCenter = thumbOffsetPx + thumbDiameterPx / 2f
                            if (dragEnabled && thumbCenter >= unlockThreshold) {
                                thumbOffsetPx = 0f
                                dragEnabled = false
                                onUnlock()
                            } else {
                                thumbOffsetPx = 0f
                                dragEnabled = false
                            }
                        },
                        onDragCancel = {
                            thumbOffsetPx = 0f
                            dragEnabled = false
                        },
                    ) { _, dragAmount ->
                        if (dragEnabled) {
                            val maxOffset = (trackWidthPx - thumbDiameterPx - with(density) { 12.dp.toPx() }).coerceAtLeast(0f)
                            thumbOffsetPx = (thumbOffsetPx + dragAmount.x).coerceIn(0f, maxOffset)
                        }
                    }
                    // Compose cancels/restarts this pointerInput coroutine in normal use; the
                    // suspend-lambda epilogue after detectDragGestures returns is not user-reachable.
                },
        ) {
            Text(
                "Unlock",
                modifier = Modifier.align(Alignment.Center),
                color = Color.Black.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset { IntOffset(thumbOffsetPx.toInt(), 0) }
                    .padding(6.dp)
                    .size(thumbDiameter)
                    .background(Color.White, RoundedCornerShape(20.dp))
                    .border(1.dp, Color.Black, RoundedCornerShape(20.dp)),
            )
        }
    }
}

// Top status line showing the real clock and the next relevant cap.
@Composable
private fun StatusLine(
    currentTime: LocalTime,
    capStatus: CapStatus?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatClockTime(currentTime),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = capStatus?.let { "${it.label} ${formatDuration(it.remaining)}" } ?: "Caps passed",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// Draw the field as top/bottom end zones plus a center strip for pull direction and controls.
@Composable
private fun FieldSketchCard(
    state: LiveGameState,
    interactionsEnabled: Boolean,
    showPullIndicator: Boolean,
    centerContent: @Composable (() -> Unit)?,
    onGoal: (TeamId) -> Unit,
    onTimeout: (TeamId) -> Unit,
    onPullInfraction: (TeamId) -> Unit,
) {
    // Translate the game's pulling orientation into fixed top/bottom screen slots.
    val topSlot = if (state.pullingFromEnd == FieldEnd.FAR) {
        state.pullingTeam
    } else {
        oppositeTeam(state.pullingTeam)
    }
    val bottomSlot = oppositeTeam(topSlot)
    val topTeam = state.teamFor(topSlot)
    val bottomTeam = state.teamFor(bottomSlot)
    val pullFrom = state.pullingFromEnd

    // Draw the top team row, center field area, and bottom team row in that order.
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant,
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Top end zone/team row.
            EndZonePanel(
                teamId = topSlot,
                team = topTeam,
                cardPoints = state.teamCardTotal(topSlot),
                timeoutsRemaining = state.timeoutsRemaining(topSlot),
                background = topTeam.color.accent.copy(alpha = 0.85f),
                interactionsEnabled = interactionsEnabled,
                isPulling = state.pullingTeam == topSlot,
                pullInfractionEnabled = if (state.pullingTeam == topSlot) {
                    !state.pullSequenceOffsidesRecorded
                } else {
                    !state.pullSequenceFalseStartRecorded
                },
                goalEnabled = state.phase != LivePhase.GAME_OVER,
                onGoal = { onGoal(topSlot) },
                onTimeout = { onTimeout(topSlot) },
                onPullInfraction = { onPullInfraction(topSlot) },
            )
            // Center field strip with pull direction and the main central control.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(Color(0xFFA8D5A0))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxSize(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    if (showPullIndicator) {
                        PullDirectionIndicator(
                            pullingFromEnd = pullFrom,
                            modifier = Modifier.padding(start = 8.dp),
                        )
                    }
                    Box(
                        modifier = Modifier.weight(1f),
                        contentAlignment = Alignment.Center,
                    ) {
                        centerContent?.invoke()
                    }
                }
            }
            // Bottom end zone/team row.
            EndZonePanel(
                teamId = bottomSlot,
                team = bottomTeam,
                cardPoints = state.teamCardTotal(bottomSlot),
                timeoutsRemaining = state.timeoutsRemaining(bottomSlot),
                background = bottomTeam.color.accent,
                interactionsEnabled = interactionsEnabled,
                isPulling = state.pullingTeam == bottomSlot,
                pullInfractionEnabled = if (state.pullingTeam == bottomSlot) {
                    !state.pullSequenceOffsidesRecorded
                } else {
                    !state.pullSequenceFalseStartRecorded
                },
                goalEnabled = state.phase != LivePhase.GAME_OVER,
                onGoal = { onGoal(bottomSlot) },
                onTimeout = { onTimeout(bottomSlot) },
                onPullInfraction = { onPullInfraction(bottomSlot) },
            )
        }
    }
}

// One team row on the field, with score/state info and the main live actions.
@Composable
private fun EndZonePanel(
    teamId: TeamId,
    team: TeamLiveState,
    cardPoints: Int,
    timeoutsRemaining: Int,
    background: Color,
    interactionsEnabled: Boolean,
    isPulling: Boolean,
    pullInfractionEnabled: Boolean,
    goalEnabled: Boolean,
    onGoal: () -> Unit,
    onTimeout: () -> Unit,
    onPullInfraction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = team.name,
                        color = team.color.content,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = team.score.toString(),
                        color = team.color.content,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = "TO $timeoutsRemaining  Cards $cardPoints  TF ${team.technicalFouls}",
                    color = team.color.content,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Pull violations ${pullViolationCount(team)}",
                    color = team.color.content,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CompactActionButton(
                label = "Goal",
                modifier = Modifier.testTag("live-${teamId.name}-goal"),
                enabled = goalEnabled && interactionsEnabled,
                onClick = onGoal,
            )
            CompactActionButton(
                label = "Timeout",
                modifier = Modifier.testTag("live-${teamId.name}-timeout"),
                enabled = interactionsEnabled,
                onClick = onTimeout,
            )
            CompactActionButton(
                label = if (isPulling) "Offsides" else "False Start",
                modifier = Modifier.testTag("live-${teamId.name}-pull-infraction"),
                enabled = interactionsEnabled && pullInfractionEnabled,
                onClick = onPullInfraction,
            )
        }
    }
}

// Center-field arrow showing which end the pull comes from.
@Composable
private fun PullDirectionIndicator(
    pullingFromEnd: FieldEnd,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .width(72.dp)
            .fillMaxHeight(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            "Pull",
            color = Color.Black.copy(alpha = if (pullingFromEnd == FieldEnd.FAR) 1f else 0f),
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = if (pullingFromEnd == FieldEnd.FAR) "↓" else "↑",
            color = Color.Black,
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Black,
        )
        Text(
            "Pull",
            color = Color.Black.copy(alpha = if (pullingFromEnd == FieldEnd.NEAR) 1f else 0f),
            fontWeight = FontWeight.Bold,
        )
    }
}

// Active countdown plus the quick -5/+5 correction buttons.
@Composable
private fun CountdownLine(
    countdown: ActiveCountdownDisplay,
    enabled: Boolean,
    onAdjust: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${countdown.label} ${formatDuration(countdown.remaining)}",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallActionButton(label = "-5", enabled = enabled) {
                onAdjust(-5)
            }
            SmallActionButton(label = "+5", enabled = enabled) {
                onAdjust(5)
            }
        }
    }
}

// Bottom sheet for recording cards and technical fouls for either team.
@Composable
private fun CardsSheet(
    state: LiveGameState,
    onAssessment: (CardAssessmentResult) -> Unit,
) {
    var pendingYellowTeam by remember { mutableStateOf<TeamId?>(null) }
    var pendingRedTeam by remember { mutableStateOf<TeamId?>(null) }
    var pendingRedCardChoice by remember { mutableStateOf<PendingRedCardChoice?>(null) }
    var pendingUnknownYellowChoice by remember { mutableStateOf<PendingUnknownYellowChoice?>(null) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text("Cards / Technical Fouls", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        TeamActionSection(
            label = "${state.teamOne.name}${cardsRoleSuffix(state, TeamId.TEAM_ONE)}",
            issuedCards = state.playerCards(TeamId.TEAM_ONE),
            onYellow = { pendingYellowTeam = TeamId.TEAM_ONE },
            onRed = { pendingRedTeam = TeamId.TEAM_ONE },
            onBlue = { onAssessment(state.assessBlueCard(TeamId.TEAM_ONE)) },
            onTech = { onAssessment(state.assessTechnicalFoul(TeamId.TEAM_ONE)) },
        )
        TeamActionSection(
            label = "${state.teamTwo.name}${cardsRoleSuffix(state, TeamId.TEAM_TWO)}",
            issuedCards = state.playerCards(TeamId.TEAM_TWO),
            onYellow = { pendingYellowTeam = TeamId.TEAM_TWO },
            onRed = { pendingRedTeam = TeamId.TEAM_TWO },
            onBlue = { onAssessment(state.assessBlueCard(TeamId.TEAM_TWO)) },
            onTech = { onAssessment(state.assessTechnicalFoul(TeamId.TEAM_TWO)) },
        )
        Spacer(modifier = Modifier.height(24.dp))
    }

    if (pendingYellowTeam != null) {
        PlayerNumberDialog(
            title = "Yellow Card",
            teamName = state.teamFor(pendingYellowTeam!!).name,
            onDismiss = { pendingYellowTeam = null },
            onConfirm = { jerseyNumber ->
                // Yellow on N/A needs a follow-up question if an unknown player already has one.
                if (
                    jerseyNumber == UNKNOWN_PLAYER_NUMBER &&
                    state.playerHasYellowThisGame(pendingYellowTeam!!, UNKNOWN_PLAYER_NUMBER)
                ) {
                    pendingUnknownYellowChoice = PendingUnknownYellowChoice(pendingYellowTeam!!)
                } else {
                    onAssessment(state.assessYellowCard(pendingYellowTeam!!, jerseyNumber))
                }
                pendingYellowTeam = null
            },
        )
    }

    if (pendingRedTeam != null) {
        PlayerNumberDialog(
            title = "Red Card",
            teamName = state.teamFor(pendingRedTeam!!).name,
            onDismiss = { pendingRedTeam = null },
            onConfirm = { jerseyNumber ->
                // Red on a player who already has yellow needs a direct-red vs second-yellow choice.
                if (state.playerHasYellowThisGame(pendingRedTeam!!, jerseyNumber)) {
                    pendingRedCardChoice = PendingRedCardChoice(pendingRedTeam!!, jerseyNumber)
                } else {
                    onAssessment(state.assessRedCard(pendingRedTeam!!, jerseyNumber, RedCardMode.DIRECT_RED))
                }
                pendingRedTeam = null
            },
        )
    }

    if (pendingRedCardChoice != null) {
        val redCardChoice = pendingRedCardChoice!!
        val currentRecords = state.playerCards(redCardChoice.team)
        RedCardModeDialog(
            teamName = state.teamFor(redCardChoice.team).name,
            jerseyNumber = redCardChoice.jerseyNumber,
            directRedEnabled = canAddPlayerCardAssignment(
                currentRecords,
                redCardChoice.jerseyNumber,
                CardType.RED,
            ),
            secondYellowEnabled = canAddPlayerCardAssignment(
                currentRecords,
                redCardChoice.jerseyNumber,
                CardType.YELLOW,
            ),
            onDismiss = { pendingRedCardChoice = null },
            onDirectRed = {
                onAssessment(
                    state.assessRedCard(
                        redCardChoice.team,
                        redCardChoice.jerseyNumber,
                        RedCardMode.DIRECT_RED,
                    )
                )
                pendingRedCardChoice = null
            },
            onSecondYellow = {
                onAssessment(
                    state.assessRedCard(
                        redCardChoice.team,
                        redCardChoice.jerseyNumber,
                        RedCardMode.SECOND_YELLOW,
                    )
                )
                pendingRedCardChoice = null
            },
        )
    }

    if (pendingUnknownYellowChoice != null) {
        UnknownYellowDialog(
            teamName = state.teamFor(pendingUnknownYellowChoice!!.team).name,
            onDismiss = { pendingUnknownYellowChoice = null },
            onSamePlayer = {
                onAssessment(
                    state.assessRedCard(
                        pendingUnknownYellowChoice!!.team,
                        UNKNOWN_PLAYER_NUMBER,
                        RedCardMode.SECOND_YELLOW,
                    )
                )
                pendingUnknownYellowChoice = null
            },
            onDifferentPlayer = {
                onAssessment(
                    state.assessStandaloneYellowCard(
                        pendingUnknownYellowChoice!!.team,
                        UNKNOWN_PLAYER_NUMBER,
                    )
                )
                pendingUnknownYellowChoice = null
            },
        )
    }
}

// Card/TF actions and current-game issued-card summary for one team.
@Composable
private fun TeamActionSection(
    label: String,
    issuedCards: List<InGamePlayerCardRecord>,
    onYellow: () -> Unit,
    onRed: () -> Unit,
    onBlue: () -> Unit,
    onTech: () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(label, fontWeight = FontWeight.SemiBold)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallActionButton(label = "Yellow", modifier = Modifier.weight(1f), onClick = onYellow)
            SmallActionButton(label = "Red", modifier = Modifier.weight(1f), onClick = onRed)
        }
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallActionButton(label = "Blue", modifier = Modifier.weight(1f), onClick = onBlue)
            SmallActionButton(label = "Tech", modifier = Modifier.weight(1f), onClick = onTech)
        }
        if (issuedCards.isNotEmpty()) {
            Text("This game", fontWeight = FontWeight.Medium, style = MaterialTheme.typography.labelLarge)
            issuedCards.forEach { record ->
                Text(
                    text = buildIssuedCardSummary(record),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        }
    }
}

// Read-only summary view shown once the game is over.
@Composable
private fun GameOverSummary(
    state: LiveGameState,
    onUndo: () -> Unit,
    showUndo: Boolean,
) {
    val orderedTeams = winnerFirstTeams(state)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("Game Summary", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Text(
                    text = "Start ${formatStartDate(state.startDate)} ${formatClockTime(state.startTime)}",
                    style = MaterialTheme.typography.bodyMedium,
                )
                state.endTime?.let { endTime ->
                    Text(
                        text = "End time ${formatClockTime(endTime)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
                orderedTeams.forEach { team ->
                    Text(
                        text = "${team.name} ${team.score}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        GameOverTeamSummary(
            team = state.teamOne,
            issuedCards = state.playerCards(TeamId.TEAM_ONE),
        )
        GameOverTeamSummary(
            team = state.teamTwo,
            issuedCards = state.playerCards(TeamId.TEAM_TWO),
        )

        if (showUndo) {
            OutlinedButton(
                onClick = onUndo,
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black,
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.Black),
            ) {
                Text("Undo End Game")
            }
        }
    }
}

// Team-level section inside the game-over summary.
@Composable
private fun GameOverTeamSummary(
    team: TeamLiveState,
    issuedCards: List<InGamePlayerCardRecord>,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(team.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (issuedCards.isEmpty()) {
                Text("No yellow or red cards issued.", style = MaterialTheme.typography.bodyMedium)
            } else {
                issuedCards.forEach { record ->
                    Text(buildSummaryIssuedCardText(record), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text("Blue cards ${team.blueCards}", style = MaterialTheme.typography.bodyMedium)
            Text("Technical fouls ${team.technicalFouls}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// Manual score correction dialog.
@Composable
private fun AdjustScoreDialog(
    state: LiveGameState,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
) {
    var teamOneScore by remember { mutableStateOf(state.teamOne.score) }
    var teamTwoScore by remember { mutableStateOf(state.teamTwo.score) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adjust Score") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallCountEditor(
                    label = state.teamOne.name,
                    value = teamOneScore,
                    onValueChange = { teamOneScore = it.coerceAtLeast(0) },
                )
                SmallCountEditor(
                    label = state.teamTwo.name,
                    value = teamTwoScore,
                    onValueChange = { teamTwoScore = it.coerceAtLeast(0) },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(teamOneScore, teamTwoScore) }) {
                Text("Set")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

// Manual timeout correction dialog.
@Composable
private fun AdjustTimeoutsDialog(
    state: LiveGameState,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
) {
    val teamOneAllowed = state.timeoutsAllowedThisHalf(TeamId.TEAM_ONE)
    val teamTwoAllowed = state.timeoutsAllowedThisHalf(TeamId.TEAM_TWO)
    var teamOneTimeoutsUsed by remember { mutableStateOf(state.teamOne.timeoutsUsedThisHalf) }
    var teamTwoTimeoutsUsed by remember { mutableStateOf(state.teamTwo.timeoutsUsedThisHalf) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adjust Timeouts") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                SmallCountEditor(
                    label = "${state.teamOne.name} used (allowed $teamOneAllowed)",
                    value = teamOneTimeoutsUsed,
                    onValueChange = { teamOneTimeoutsUsed = it },
                )
                SmallCountEditor(
                    label = "${state.teamTwo.name} used (allowed $teamTwoAllowed)",
                    value = teamTwoTimeoutsUsed,
                    onValueChange = { teamTwoTimeoutsUsed = it },
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(teamOneTimeoutsUsed, teamTwoTimeoutsUsed) }) {
                Text("Set")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

// Manual card/TF correction dialog, including the per-player reconciliation flow.
@Composable
private fun AdjustCardsDialog(
    state: LiveGameState,
    onDismiss: () -> Unit,
    onConfirm: (LiveGameState) -> Unit,
) {
    var teamOneY by remember { mutableStateOf(state.teamYellowCards(TeamId.TEAM_ONE)) }
    var teamOneB by remember { mutableStateOf(state.teamOne.blueCards) }
    var teamOneR by remember { mutableStateOf(state.teamRedCards(TeamId.TEAM_ONE)) }
    var teamOneTf by remember { mutableStateOf(state.teamOne.technicalFouls) }
    var teamTwoY by remember { mutableStateOf(state.teamYellowCards(TeamId.TEAM_TWO)) }
    var teamTwoB by remember { mutableStateOf(state.teamTwo.blueCards) }
    var teamTwoR by remember { mutableStateOf(state.teamRedCards(TeamId.TEAM_TWO)) }
    var teamTwoTf by remember { mutableStateOf(state.teamTwo.technicalFouls) }
    var workingTeamOnePlayerCards by remember { mutableStateOf(state.teamOnePlayerCards) }
    var workingTeamTwoPlayerCards by remember { mutableStateOf(state.teamTwoPlayerCards) }
    var pendingSteps by remember { mutableStateOf<List<PlayerCardAdjustmentStep>>(emptyList()) }
    var invalidCardAssignmentMessage by remember { mutableStateOf<String?>(null) }

    fun finalizeAdjustment() {
        onConfirm(
            state.adjustCardsAndTf(
                teamOneBlues = teamOneB,
                teamOneTechnicalFouls = teamOneTf,
                teamTwoBlues = teamTwoB,
                teamTwoTechnicalFouls = teamTwoTf,
                teamOnePlayerCards = workingTeamOnePlayerCards,
                teamTwoPlayerCards = workingTeamTwoPlayerCards,
            )
        )
    }

    fun applyCardAssignment(jerseyNumber: String) {
        val step = pendingSteps.firstOrNull() ?: return
        val currentRecords = if (step.team == TeamId.TEAM_ONE) {
            workingTeamOnePlayerCards
        } else {
            workingTeamTwoPlayerCards
        }
        if (
            step.mode == PlayerCardAdjustmentMode.ADD &&
            !canAddPlayerCardAssignment(currentRecords, jerseyNumber, step.cardType)
        ) {
            invalidCardAssignmentMessage = "That player already has the maximum valid card combination."
            return
        }
        val updatedRecords = when (step.mode) {
            PlayerCardAdjustmentMode.ADD -> addPlayerCardAssignment(currentRecords, jerseyNumber, step.cardType)
            PlayerCardAdjustmentMode.REMOVE -> removePlayerCardAssignment(currentRecords, jerseyNumber, step.cardType)
        }
        if (step.team == TeamId.TEAM_ONE) {
            workingTeamOnePlayerCards = updatedRecords
        } else {
            workingTeamTwoPlayerCards = updatedRecords
        }
        pendingSteps = pendingSteps.drop(1)
        // Walk through the per-player add/remove prompts until all count mismatches are resolved.
        if (pendingSteps.isEmpty()) {
            finalizeAdjustment()
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adjust Cards / TF") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                TeamCorrectionSection(state.teamOne.name) {
                    CardCountRow("Yellow", teamOneY, { teamOneY += 1 }, { teamOneY = maxOf(0, teamOneY - 1) })
                    CardCountRow("Blue", teamOneB, { teamOneB += 1 }, { teamOneB = maxOf(0, teamOneB - 1) })
                    CardCountRow("Red", teamOneR, { teamOneR += 1 }, { teamOneR = maxOf(0, teamOneR - 1) })
                    CardCountRow("TF", teamOneTf, { teamOneTf += 1 }, { teamOneTf = maxOf(0, teamOneTf - 1) })
                }
                TeamCorrectionSection(state.teamTwo.name) {
                    CardCountRow("Yellow", teamTwoY, { teamTwoY += 1 }, { teamTwoY = maxOf(0, teamTwoY - 1) })
                    CardCountRow("Blue", teamTwoB, { teamTwoB += 1 }, { teamTwoB = maxOf(0, teamTwoB - 1) })
                    CardCountRow("Red", teamTwoR, { teamTwoR += 1 }, { teamTwoR = maxOf(0, teamTwoR - 1) })
                    CardCountRow("TF", teamTwoTf, { teamTwoTf += 1 }, { teamTwoTf = maxOf(0, teamTwoTf - 1) })
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val steps = state.buildPlayerCardAdjustmentSteps(
                        teamOneYellows = teamOneY,
                        teamOneReds = teamOneR,
                        teamTwoYellows = teamTwoY,
                        teamTwoReds = teamTwoR,
                    )
                    workingTeamOnePlayerCards = state.teamOnePlayerCards
                    workingTeamTwoPlayerCards = state.teamTwoPlayerCards
                    if (steps.isEmpty()) {
                        finalizeAdjustment()
                    } else {
                        pendingSteps = steps
                    }
                }
            ) {
                Text("Set")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )

    pendingSteps.firstOrNull()?.let { step ->
        when (step.mode) {
            PlayerCardAdjustmentMode.ADD -> {
                PlayerNumberDialog(
                    title = "Add ${step.cardType.label}",
                    teamName = state.teamFor(step.team).name,
                    onDismiss = { pendingSteps = emptyList() },
                    onConfirm = { applyCardAssignment(it) },
                )
            }
            PlayerCardAdjustmentMode.REMOVE -> {
                AssignedCardRemovalDialog(
                    title = "Remove ${step.cardType.label}",
                    teamName = state.teamFor(step.team).name,
                    candidates = playerCardRemovalCandidates(
                        records = if (step.team == TeamId.TEAM_ONE) {
                            workingTeamOnePlayerCards
                        } else {
                            workingTeamTwoPlayerCards
                        },
                        cardType = step.cardType,
                    ),
                    cardType = step.cardType,
                    onDismiss = { pendingSteps = emptyList() },
                    onConfirm = { applyCardAssignment(it) },
                )
            }
        }
    }

    invalidCardAssignmentMessage?.let { message ->
        AlertDialog(
            onDismissRequest = { invalidCardAssignmentMessage = null },
            title = { Text("Invalid Card Assignment") },
            text = { Text(message) },
            confirmButton = {
                TextButton(onClick = { invalidCardAssignmentMessage = null }) {
                    Text("OK")
                }
            },
        )
    }
}

// Compact +/- row for a single card or TF count.
@Composable
private fun CardCountRow(
    label: String,
    value: Int,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text("$label $value")
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallActionButton(label = "+1", enabled = true, onClick = onIncrement)
            SmallActionButton(label = "-1", enabled = value > 0, onClick = onDecrement)
        }
    }
}

// Pick which player's assigned card should be removed during a correction flow.
@Composable
private fun AssignedCardRemovalDialog(
    title: String,
    teamName: String,
    candidates: List<PlayerCardRemovalCandidate>,
    cardType: CardType,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(teamName, fontWeight = FontWeight.SemiBold)
                candidates.forEach { candidate ->
                    OutlinedButton(
                        onClick = { onConfirm(candidate.jerseyNumber) },
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            "${displayPlayerNumber(candidate.jerseyNumber)} " +
                                "(${cardType.label} ${candidate.cardCount})"
                        )
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

// Manual offsides/false-start correction dialog.
@Composable
private fun AdjustPullInfractionsDialog(
    state: LiveGameState,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int, Int, Int) -> Unit,
) {
    var teamOneOffsides by remember { mutableStateOf(state.teamOne.offsides) }
    var teamOneFalseStarts by remember { mutableStateOf(state.teamOne.falseStarts) }
    var teamTwoOffsides by remember { mutableStateOf(state.teamTwo.offsides) }
    var teamTwoFalseStarts by remember { mutableStateOf(state.teamTwo.falseStarts) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adjust Pull Infractions") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                TeamCorrectionSection(state.teamOne.name) {
                    SmallCountEditor("Offsides", teamOneOffsides) { teamOneOffsides = it.coerceAtLeast(0) }
                    SmallCountEditor("False Starts", teamOneFalseStarts) { teamOneFalseStarts = it.coerceAtLeast(0) }
                }
                TeamCorrectionSection(state.teamTwo.name) {
                    SmallCountEditor("Offsides", teamTwoOffsides) { teamTwoOffsides = it.coerceAtLeast(0) }
                    SmallCountEditor("False Starts", teamTwoFalseStarts) { teamTwoFalseStarts = it.coerceAtLeast(0) }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(teamOneOffsides, teamOneFalseStarts, teamTwoOffsides, teamTwoFalseStarts) }
            ) {
                Text("Set")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        },
    )
}

// Small labeled section used inside the adjust dialogs.
@Composable
private fun TeamCorrectionSection(
    title: String,
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        content()
    }
}

// Jersey-number prompt shared by the card flows. Blank records as N/A.
@Composable
private fun PlayerNumberDialog(
    title: String,
    teamName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var jerseyNumber by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(teamName, fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = jerseyNumber,
                    onValueChange = { jerseyNumber = it.filter(Char::isDigit) },
                    label = { Text("Player number") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.testTag("card-player-number"),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(jerseyNumber.ifBlank { UNKNOWN_PLAYER_NUMBER }) }) {
                Text("Record")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onConfirm(UNKNOWN_PLAYER_NUMBER) }) {
                    Text("N/A")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        },
    )
}

// Resolve whether a red on a player with yellow is direct red or second yellow.
@Composable
private fun RedCardModeDialog(
    teamName: String,
    jerseyNumber: String,
    directRedEnabled: Boolean,
    secondYellowEnabled: Boolean,
    onDismiss: () -> Unit,
    onDirectRed: () -> Unit,
    onSecondYellow: () -> Unit,
) {
    val hasValidChoice = directRedEnabled || secondYellowEnabled
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Player Already Has Yellow") },
        text = {
            if (hasValidChoice) {
                Text("$teamName #$jerseyNumber already has a yellow this game.")
            } else {
                Text("$teamName #$jerseyNumber already has the maximum valid card combination.")
            }
        },
        confirmButton = {
            if (directRedEnabled) {
                TextButton(onClick = onDirectRed) {
                    Text("Direct Red")
                }
            } else if (!hasValidChoice) {
                TextButton(onClick = onDismiss) {
                    Text("OK")
                }
            }
        },
        dismissButton = {
            if (hasValidChoice) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (secondYellowEnabled) {
                        TextButton(onClick = onSecondYellow) {
                            Text("Second Yellow")
                        }
                    }
                    TextButton(onClick = onDismiss) {
                        Text("Cancel")
                    }
                }
            }
        },
    )
}

// Resolve whether a second yellow on N/A is the same unknown player as before.
@Composable
private fun UnknownYellowDialog(
    teamName: String,
    onDismiss: () -> Unit,
    onSamePlayer: () -> Unit,
    onDifferentPlayer: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Unknown Player Number") },
        text = {
            Text("$teamName already has a yellow assigned to N/A. Is this the same player?")
        },
        confirmButton = {
            TextButton(onClick = onSamePlayer) {
                Text("Yes")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = onDifferentPlayer) {
                    Text("No")
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        },
    )
}

// Bottom sheet for manual corrections and less-common game actions.
@Composable
private fun OtherSheet(
    state: LiveGameState,
    now: Long,
    onUpdateGameSetup: () -> Unit,
    onAction: (LiveGameState) -> Unit,
) {
    var showAdjustScoreDialog by remember { mutableStateOf(false) }
    var showAdjustTimeoutsDialog by remember { mutableStateOf(false) }
    var showAdjustCardsDialog by remember { mutableStateOf(false) }
    var showAdjustPullInfractionsDialog by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("Other", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                OtherMenuButton(
                    label = "Update Game Setup",
                    onClick = onUpdateGameSetup,
                )
                OtherMenuButton(
                    label = "Adjust Score",
                    onClick = { showAdjustScoreDialog = true },
                )
                OtherMenuButton(
                    label = "Adjust Timeouts",
                    onClick = { showAdjustTimeoutsDialog = true },
                )
                OtherMenuButton(
                    label = "Adjust Cards / TF",
                    onClick = { showAdjustCardsDialog = true },
                )
                OtherMenuButton(
                    label = "Adjust Pull Infractions",
                    onClick = { showAdjustPullInfractionsDialog = true },
                )
                OtherMenuButton(
                    label = "Swap Ends of Field",
                    onClick = { onAction(state.swapFieldEnds()) },
                )
                OtherMenuButton(
                    label = "Swap Pulling Team",
                    onClick = { onAction(state.swapPullingTeam()) },
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (!state.halftimeTaken && state.phase == LivePhase.BETWEEN_POINTS) {
                    OtherMenuButton(
                        label = "Start Halftime",
                        onClick = { onAction(state.startHalftimeNow(now)) },
                    )
                }
                if (state.phase != LivePhase.GAME_OVER) {
                    OtherMenuButton(
                        label = "End Game",
                        onClick = { onAction(state.endGameNow(now)) },
                    )
                }
                if (!state.halftimeTaken && !state.halfCapApplied) {
                    OtherMenuButton(
                        label = "Apply Half Cap Now",
                        onClick = { onAction(state.makeCapNow(CapType.HALF, now)) },
                    )
                }
                if (!state.softCapApplied) {
                    OtherMenuButton(
                        label = "Apply Soft Cap Now",
                        onClick = { onAction(state.makeCapNow(CapType.SOFT, now)) },
                    )
                }
                if (!state.hardCapApplied && state.phase != LivePhase.GAME_OVER) {
                    OtherMenuButton(
                        label = "Apply Hard Cap Now",
                        onClick = { onAction(state.makeCapNow(CapType.HARD, now)) },
                    )
                }
            }
        }
        Spacer(modifier = Modifier.height(24.dp))
    }

    if (showAdjustScoreDialog) {
        AdjustScoreDialog(
            state = state,
            onDismiss = { showAdjustScoreDialog = false },
            onConfirm = { teamOneScore, teamTwoScore ->
                onAction(state.adjustScore(teamOneScore, teamTwoScore))
                showAdjustScoreDialog = false
            },
        )
    }

    if (showAdjustTimeoutsDialog) {
        AdjustTimeoutsDialog(
            state = state,
            onDismiss = { showAdjustTimeoutsDialog = false },
            onConfirm = { teamOneTimeoutsUsed, teamTwoTimeoutsUsed ->
                onAction(state.adjustTimeouts(teamOneTimeoutsUsed, teamTwoTimeoutsUsed))
                showAdjustTimeoutsDialog = false
            },
        )
    }

    if (showAdjustCardsDialog) {
        AdjustCardsDialog(
            state = state,
            onDismiss = { showAdjustCardsDialog = false },
            onConfirm = { updatedState ->
                onAction(updatedState)
                showAdjustCardsDialog = false
            },
        )
    }

    if (showAdjustPullInfractionsDialog) {
        AdjustPullInfractionsDialog(
            state = state,
            onDismiss = { showAdjustPullInfractionsDialog = false },
            onConfirm = { teamOneOffsides, teamOneFalseStarts, teamTwoOffsides, teamTwoFalseStarts ->
                onAction(
                    state.adjustPullInfractions(
                        teamOneOffsides,
                        teamOneFalseStarts,
                        teamTwoOffsides,
                        teamTwoFalseStarts,
                    )
                )
                showAdjustPullInfractionsDialog = false
            },
        )
    }
}

// Simple menu button that fills the width of its column in the Other sheet.
@Composable
private fun OtherMenuButton(
    label: String,
    onClick: () -> Unit,
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.fillMaxWidth(),
    ) {
        Text(label)
    }
}

// Compact live-game summary for one player's current-game cards.
private fun buildIssuedCardSummary(record: InGamePlayerCardRecord): String {
    val parts = buildList {
        if (record.yellows > 0) {
            add("Y ${record.yellows}")
        }
        if (record.directReds > 0) {
            add("DR ${record.directReds}")
        }
    }
    return "${displayPlayerNumber(record.jerseyNumber)}: ${parts.joinToString("  ")}"
}

// More readable game-over summary for one player's issued cards.
private fun buildSummaryIssuedCardText(record: InGamePlayerCardRecord): String {
    val parts = buildList {
        when (record.yellows) {
            1 -> add("Yellow card")
            2 -> add("Two yellow cards")
        }
        when (record.directReds) {
            1 -> add("Direct red card")
        }
    }
    return "${displayPlayerNumber(record.jerseyNumber)}: ${parts.joinToString("; ")}"
}

// Game-over alert text with the winner listed first.
private fun formatGameOverSummary(state: LiveGameState): String {
    val orderedTeams = winnerFirstTeams(state)
    return buildString {
        appendLine("Game is over")
        appendLine("${orderedTeams[0].name} ${orderedTeams[0].score}")
        append("${orderedTeams[1].name} ${orderedTeams[1].score}")
    }
}

// Put the higher-scoring team first for summary display.
private fun winnerFirstTeams(state: LiveGameState): List<TeamLiveState> {
    return listOf(state.teamOne, state.teamTwo).sortedWith(
        compareByDescending<TeamLiveState> { it.score }.thenBy { it.name }
    )
}

// Show N/A for the unknown-player sentinel; otherwise format as a jersey number.
private fun displayPlayerNumber(jerseyNumber: String): String {
    return if (jerseyNumber == UNKNOWN_PLAYER_NUMBER) {
        "N/A"
    } else {
        "#$jerseyNumber"
    }
}

// Between points, tag each team as pulling or receiving in the Cards / TF sheet.
private fun cardsRoleSuffix(state: LiveGameState, team: TeamId): String {
    return if (state.phase == LivePhase.BETWEEN_POINTS || state.phase == LivePhase.HALFTIME) {
        if (team == state.pullingTeam) " (pulling)" else " (receiving)"
    } else {
        ""
    }
}

// Return the other team id.
private fun oppositeTeam(teamId: TeamId): TeamId {
    return if (teamId == TeamId.TEAM_ONE) TeamId.TEAM_TWO else TeamId.TEAM_ONE
}

// Offsides and false starts are combined for pull-violation display/rules.
private fun pullViolationCount(team: TeamLiveState): Int {
    return team.offsides + team.falseStarts
}

private data class ActiveCountdownDisplay(
    val label: String,
    val remaining: Duration,
)

// Compute the countdown text currently visible on the live screen.
private fun activeCountdownDisplay(state: LiveGameState, now: Long): ActiveCountdownDisplay? {
    val countdown = state.countdown ?: return null
    return if (countdown.kind == CountdownKind.HALFTIME) {
        val halftimeRemaining = countdown.targetEpoch - now
        if (halftimeRemaining > 0L) {
            ActiveCountdownDisplay(
                label = countdown.label,
                remaining = Duration.ofMillis(halftimeRemaining),
            )
        } else {
            // Once halftime expires, show the follow-on between-points countdown immediately.
            val followOn = betweenPointsDisplay(state.pullingFromEnd, countdown.targetEpoch, now)
            ActiveCountdownDisplay(label = followOn.first, remaining = followOn.second)
        }
    } else {
        ActiveCountdownDisplay(
            label = countdown.label,
            remaining = Duration.ofMillis((countdown.targetEpoch - now).coerceAtLeast(0L)),
        )
    }
}

// Halftime can become Start Point once the halftime countdown itself has elapsed.
private fun halftimeTransitionReady(state: LiveGameState, now: Long): Boolean {
    val countdown = state.countdown ?: return false
    return state.phase == LivePhase.HALFTIME &&
        countdown.kind == CountdownKind.HALFTIME &&
        now >= countdown.targetEpoch
}

// Convenience lookup for Team 1 vs Team 2 in the live state.
private fun LiveGameState.teamFor(team: TeamId): TeamLiveState {
    return if (team == TeamId.TEAM_ONE) teamOne else teamTwo
}
