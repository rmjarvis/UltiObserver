package rmjarvis.ultiobserver

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import java.time.LocalTime

private data class PendingMisconductChoice(
    val baseMessage: String,
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
