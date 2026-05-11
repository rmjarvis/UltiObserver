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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import java.time.LocalTime

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
    var activeGamePrompt by remember { mutableStateOf<GamePrompt?>(null) }

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
        state.activeCountdownDisplay(now)
    }
    val canStartPoint = remember(state, now) {
        state.phase == LivePhase.BETWEEN_POINTS || state.halftimeTransitionReady(now)
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

    // Only show the large halftime/game-over prompts when those states first become visible.
    LaunchedEffect(state.phase, readOnlySummary) {
        if (state.phase == LivePhase.HALFTIME) {
            activeGamePrompt = GamePrompt.HalftimeStarted(state)
        }
        if (!readOnlySummary && state.phase == LivePhase.GAME_OVER) {
            activeGamePrompt = GamePrompt.GameOver(state)
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

                // Reserve the countdown row even when no timer is active so the field stays put.
                CountdownLine(
                    countdown = activeCountdown,
                    enabled = !locked,
                    onAdjust = { seconds -> onStateChange(state.addTimeToCountdown(seconds)) },
                )

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
                        val message = result.event?.formatMessage()
                        if (message != null) {
                            actionInfoMessage = message
                        }
                    },
                    onPullInfraction = { team ->
                        val result = state.assessPullInfraction(team)
                        onStateChange(result.state)
                        val message = result.event?.formatMessage()
                        if (message != null) {
                            actionInfoMessage = message
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

                UndoRedoBar(
                    state = state,
                    enabled = !locked,
                    onStateChange = onStateChange,
                )
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
                    if (result.needsMisconductChoice) {
                        activeGamePrompt = GamePrompt.LivePointMisconduct(
                            event = result.event,
                        )
                    } else {
                        actionInfoMessage = result.event.formatMessage()
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

    // Cap prompts block until the observer decides whether to apply the newly eligible cap.
    if (state.pendingCapOffer != null) {
        val capPrompt = GamePrompt.ApplyCap(state, state.pendingCapOffer!!)
        AlertDialog(
            onDismissRequest = {},
            title = { Text(capPrompt.formatTitle()!!) },
            text = {
                Text(
                    text = capPrompt.formatMessage(),
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

    // Prominent game prompts that are not tied to bottom-sheet workflows.
    if (activeGamePrompt != null) {
        val prompt = activeGamePrompt!!
        if (prompt is GamePrompt.LivePointMisconduct) {
            AlertDialog(
                onDismissRequest = { activeGamePrompt = null },
                title = { Text(prompt.formatTitle()!!) },
                text = {
                    Text(
                        text = prompt.formatMessage(),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                },
                confirmButton = {
                    TextButton(
                        onClick = {
                            actionInfoMessage = prompt.resolutionMessage(
                                againstOffense = true,
                            )
                            activeGamePrompt = null
                        }
                    ) {
                        Text("Offense")
                    }
                },
                dismissButton = {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        TextButton(
                            onClick = {
                                actionInfoMessage = prompt.resolutionMessage(
                                    againstOffense = false,
                                )
                                activeGamePrompt = null
                            }
                        ) {
                            Text("Defense")
                        }
                    }
                },
            )
        } else {
            AlertDialog(
                onDismissRequest = { activeGamePrompt = null },
                text = {
                    Text(
                        text = prompt.formatMessage(),
                        style = if (prompt is GamePrompt.HalftimeStarted) {
                            MaterialTheme.typography.displaySmall
                        } else {
                            MaterialTheme.typography.headlineSmall
                        },
                        fontWeight = FontWeight.Black,
                    )
                },
                confirmButton = {
                    TextButton(onClick = { activeGamePrompt = null }) {
                        Text("OK")
                    }
                },
            )
        }
    }
}

// Bottom action bar for undo plus immediate redo after an undo.
@Composable
private fun UndoRedoBar(
    state: LiveGameState,
    enabled: Boolean,
    onStateChange: (LiveGameState) -> Unit,
) {
    val undoEntry = state.undoEntry
    val redoEntry = state.redoEntry
    if (undoEntry == null && redoEntry == null) {
        return
    }

    if (redoEntry == null) {
        OutlinedButton(
            onClick = { onStateChange(state.undoLastAction()) },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                containerColor = Color.White,
                contentColor = Color.Black,
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.Black),
        ) {
            Text(undoEntry!!.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        return
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (undoEntry != null) {
            OutlinedButton(
                onClick = { onStateChange(state.undoLastAction()) },
                enabled = enabled,
                modifier = Modifier.weight(3f),
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black,
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.Black),
            ) {
                Text(undoEntry.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        } else {
            Spacer(modifier = Modifier.weight(3f))
        }
        OutlinedButton(
            onClick = { onStateChange(state.redoLastAction()) },
            enabled = enabled,
            modifier = Modifier.weight(1f),
            colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                containerColor = Color(0xFF343A40),
                contentColor = Color.White,
                disabledContainerColor = Color(0xFF343A40),
                disabledContentColor = Color.White,
            ),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.Black),
        ) {
            Text("Redo")
        }
    }
}
