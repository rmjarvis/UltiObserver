package rmjarvis.ultiobserver

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalTime

/**
 * Render the main live-game screen, including the field view, modal flows, and popup cues.
 *
 * @param state The live game state to render.
 * @param readOnlySummary Whether this is an archived summary that should ignore live controls.
 * @param automaticallyAdvanceCountdowns Whether expired countdowns should advance model state automatically.
 * @param automaticallyLockLivePoint Whether automatic live-point transitions should lock the screen.
 * @param onStateChange Callback receiving updated live state from user actions and timer transitions.
 * @param onUpdateGameSetup Callback reopening setup for the current game.
 * @param onDeleteGame Callback deleting the current game.
 * @param onBackHome Callback returning to Home or setup according to ViewModel navigation rules.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LiveGameScreen(
    state: LiveGameState,
    readOnlySummary: Boolean,
    automaticallyAdvanceCountdowns: Boolean,
    automaticallyLockLivePoint: Boolean,
    onStateChange: (LiveGameState) -> Unit,
    onUpdateGameSetup: () -> Unit,
    onDeleteGame: () -> Unit,
    onBackHome: () -> Unit,
) {
    var showCardsSheet by remember { mutableStateOf(false) }
    var showOtherSheet by remember { mutableStateOf(false) }
    var showEventLogSheet by remember { mutableStateOf(false) }
    var showTimeViolationTeamPrompt by remember { mutableStateOf(false) }
    var locked by remember { mutableStateOf(false) }
    var actionInfoMessage by remember { mutableStateOf<String?>(null) }
    var actionInfoTitle by remember { mutableStateOf("") }
    var activeGamePrompt by remember { mutableStateOf<GamePrompt?>(null) }
    var previouslyObservedPhase by remember { mutableStateOf(state.phase) }
    var suppressNextPhasePrompt by remember { mutableStateOf(false) }
    var suppressNextAutoLock by remember { mutableStateOf(false) }

    /**
     * Show a transient action-info popup.
     *
     * @param message The popup body text.
     * @param title The popup title.
     */
    fun showActionInfo(message: String, title: String) {
        actionInfoMessage = message
        actionInfoTitle = title
    }

    /// Dismiss the transient action-info popup.
    fun dismissActionInfo() {
        actionInfoMessage = null
    }

    /**
     * Apply undo while suppressing phase-change prompts and automatic locking caused by restored state.
     *
     * @param updatedState The state produced by undo.
     */
    fun undoWithoutPhasePrompt(updatedState: LiveGameState) {
        suppressNextPhasePrompt = updatedState.phase != state.phase
        suppressNextAutoLock = true
        activeGamePrompt = null
        onStateChange(updatedState)
    }

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
    val hasExpiredPullActions = remember(state) {
        state.hasExpiredPullActions()
    }
    val canReportMisconductOffenseSet = remember(state, now) {
        state.canReportMisconductOffenseSet(now)
    }

    // Let countdown expiration move the model forward without requiring an observer tap.
    LaunchedEffect(
        state,
        now,
        readOnlySummary,
        automaticallyAdvanceCountdowns,
        automaticallyLockLivePoint,
    ) {
        val suppressAutoLock = suppressNextAutoLock
        suppressNextAutoLock = false
        if (!readOnlySummary && automaticallyAdvanceCountdowns) {
            val transitionedState = state.applyExpiredCountdownTransitions(now)
            if (transitionedState != state) {
                if (
                    transitionedState.phase == LivePhase.LIVE_POINT &&
                    automaticallyLockLivePoint &&
                    !suppressAutoLock
                ) {
                    locked = true
                }
                onStateChange(transitionedState)
            }
        }
    }

    // Only show the large halftime/game-over prompts when those states first become visible.
    LaunchedEffect(state.phase, readOnlySummary) {
        val previousPhase = previouslyObservedPhase
        if (suppressNextPhasePrompt) {
            previouslyObservedPhase = state.phase
            suppressNextPhasePrompt = false
            return@LaunchedEffect
        }
        // Defensive transition guard so recomposition does not reshow the halftime prompt.
        if (state.phase == LivePhase.HALFTIME && previousPhase != LivePhase.HALFTIME) {
            activeGamePrompt = GamePrompt.HalftimeStarted(state)
        }
        // Defensive transition guard so recomposition does not reshow the game-over prompt.
        if (!readOnlySummary && state.phase == LivePhase.GAME_OVER && previousPhase != LivePhase.GAME_OVER) {
            activeGamePrompt = GamePrompt.GameOver(state)
        }
        previouslyObservedPhase = state.phase
    }

    // Compose the major elements of the live game screen.
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("UltiObserver") },
                navigationIcon = {
                    TextButton(onClick = onBackHome) {
                        Text("Back")
                    }
                },
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
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            val layoutMetrics = liveLayoutMetrics(maxHeight)

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(layoutMetrics.pagePadding),
                verticalArrangement = Arrangement.spacedBy(layoutMetrics.sectionSpacing),
            ) {
                // If the game is over, replace the live controls with the summary screen.
                if (state.phase == LivePhase.GAME_OVER) {
                    GameOverSummary(
                        state = state,
                        onShowEventLog = { showEventLogSheet = true },
                        onUndo = {
                            undoWithoutPhasePrompt(state.undoLastAction())
                        },
                        showUndo = !readOnlySummary,
                    )
                } else {
                // Show the current clock and next relevant cap.
                StatusLine(
                    currentTime = currentClockTime,
                    capStatus = capStatus,
                    height = layoutMetrics.statusHeight,
                )

                // Reserve the countdown row even when no timer is active so the field stays put.
                CountdownLine(
                    countdown = activeCountdown,
                    enabled = !locked,
                    onAdjust = { seconds -> onStateChange(state.addTimeToCountdown(seconds)) },
                    expiredPullActions = if (hasExpiredPullActions && !locked) {
                        ExpiredPullActions(
                            onTimeViolation = { showTimeViolationTeamPrompt = true },
                            onRestartPullCountdown = { onStateChange(state.restartPullCountdown(now)) },
                        )
                    } else {
                        null
                    },
                    misconductCountdownAction = if (state.pendingMisconductCountdown && !locked) {
                        MisconductCountdownAction(
                            onStart = { onStateChange(state.startMisconductCountdown(now)) },
                        )
                    } else {
                        null
                    },
                    height = layoutMetrics.countdownHeight,
                )

                // Sketch the field with two teams and the grass strip between them.
                FieldSketchCard(
                    state = state,
                    interactionsEnabled = !locked,
                    showPullIndicator = !locked,
                    metrics = layoutMetrics.field,
                    centerContent = {
                        if (locked) {
                            FieldUnlockControl(onUnlock = { locked = false })
                        } else if (canReportMisconductOffenseSet) {
                            OutlinedButton(
                                onClick = { onStateChange(state.reportMisconductOffenseSet(now)) },
                                modifier = Modifier
                                    .height(layoutMetrics.centerButtonHeight)
                                    .testTag("live-misconduct-offense-set"),
                                shape = RoundedCornerShape(12.dp),
                                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                    containerColor = Color.White,
                                    contentColor = Color.Black,
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.Black),
                            ) {
                                Text(
                                    "Offense is set",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontSize = layoutMetrics.centerButtonFontSize,
                                    ),
                                )
                            }
                        } else if (canStartPoint) {
                            OutlinedButton(
                                onClick = {
                                    onStateChange(state.beginLivePoint(now))
                                    if (automaticallyLockLivePoint) {
                                        locked = true
                                    }
                                },
                                modifier = Modifier.height(layoutMetrics.centerButtonHeight),
                                shape = RoundedCornerShape(12.dp),
                                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                    containerColor = Color.White,
                                    contentColor = Color.Black,
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.Black),
                            ) {
                                Text(
                                    "Start Point",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontSize = layoutMetrics.centerButtonFontSize,
                                    ),
                                )
                            }
                        } else if (state.phase == LivePhase.LIVE_POINT && state.countdown != null) {
                            OutlinedButton(
                                onClick = {
                                    onStateChange(state.continueLivePoint())
                                    if (automaticallyLockLivePoint) {
                                        locked = true
                                    }
                                },
                                modifier = Modifier.height(layoutMetrics.centerButtonHeight),
                                shape = RoundedCornerShape(12.dp),
                                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                    containerColor = Color.White,
                                    contentColor = Color.Black,
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.Black),
                            ) {
                                Text(
                                    "Continue Point",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontSize = layoutMetrics.centerButtonFontSize,
                                    ),
                                )
                            }
                        } else if (state.phase == LivePhase.LIVE_POINT) {
                            OutlinedButton(
                                onClick = { locked = true },
                                modifier = Modifier
                                    .height(layoutMetrics.centerButtonHeight)
                                    .testTag("live-center-lock"),
                                shape = RoundedCornerShape(12.dp),
                                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                                    containerColor = Color.White,
                                    contentColor = Color.Black,
                                ),
                                border = androidx.compose.foundation.BorderStroke(1.dp, Color.Black),
                            ) {
                                Text(
                                    "Lock",
                                    style = MaterialTheme.typography.labelLarge.copy(
                                        fontSize = layoutMetrics.centerButtonFontSize,
                                    ),
                                )
                            }
                        }
                    },
                    onGoal = { team -> onStateChange(state.recordGoalFromCurrentState(team, now)) },
                    onTimeout = { team ->
                        val result = state.assessTimeout(team, now)
                        onStateChange(result.state)
                        showActionInfo(
                            message = result.event.formatMessage(),
                            title = result.event.formatPopupTitle(),
                        )
                    },
                    onPullInfraction = { team ->
                        val result = state.assessPullInfraction(team, now)
                        onStateChange(result.state)
                        // Defensive stale-callback guard for a weird timing state.
                        val message = result.event?.formatMessage()
                        if (message != null) {
                            showActionInfo(
                                message = message,
                                title = result.event.formatPopupTitle(),
                            )
                        }
                    },
                )

                // Cards / TF and Other sit directly below the field.
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    SmallActionButton(
                        label = "Cards / TF",
                        modifier = Modifier
                            .weight(1f)
                            .height(layoutMetrics.bottomActionHeight),
                        enabled = !locked,
                        containerColor = Color.White,
                        contentColor = Color.Black,
                        borderColor = Color.Black,
                        onClick = { showCardsSheet = true },
                    )
                    SmallActionButton(
                        label = "Other",
                        modifier = Modifier
                            .weight(1f)
                            .height(layoutMetrics.bottomActionHeight),
                        enabled = !locked,
                        containerColor = Color.White,
                        contentColor = Color.Black,
                        borderColor = Color.Black,
                        onClick = { showOtherSheet = true },
                    )
                }

                UndoRedoBar(
                    state = state,
                    enabled = !locked,
                    height = layoutMetrics.undoHeight,
                    onUndo = { undoWithoutPhasePrompt(it) },
                    onRedo = onStateChange,
                )
                }
            }
        }
    }

    // Bottom sheet for the card / technical foul workflow.
    if (showCardsSheet) {
        ModalBottomSheet(onDismissRequest = { showCardsSheet = false }) {
            CardsSheet(
                state = state,
                now = now,
                onAssessment = { updatedState, message, title ->
                    onStateChange(updatedState)
                    showActionInfo(
                        message = message,
                        title = title,
                    )
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
                onShowEventLog = {
                    showOtherSheet = false
                    showEventLogSheet = true
                },
                onDeleteGame = onDeleteGame,
                onAction = { updatedState ->
                    onStateChange(updatedState)
                    showOtherSheet = false
                },
            )
        }
    }

    if (showEventLogSheet) {
        ModalBottomSheet(onDismissRequest = { showEventLogSheet = false }) {
            EventLogSheet(state = state)
        }
    }

    if (showTimeViolationTeamPrompt) {
        /**
         * Assess a time violation for the chosen team and show its result popup.
         *
         * @param team The team selected in the time-violation prompt.
         */
        fun assessTimeViolationFor(team: TeamId) {
            val result = state.assessTimeViolation(team, now)
            onStateChange(result.state)
            // Defensive stale-callback guard for a weird timing state.
            val message = result.event?.formatMessage()
            if (message != null) {
                showActionInfo(
                    message = message,
                    title = result.event.formatPopupTitle(),
                )
            }
            showTimeViolationTeamPrompt = false
        }

        AlertDialog(
            onDismissRequest = { showTimeViolationTeamPrompt = false },
            title = { Text("Time Violation") },
            text = {
                Text(
                    text = "Which team committed the time violation?",
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            confirmButton = {
                TextButton(onClick = { assessTimeViolationFor(TeamId.TEAM_TWO) }) {
                    Text(state.teamName(TeamId.TEAM_TWO))
                }
            },
            dismissButton = {
                TextButton(onClick = { assessTimeViolationFor(TeamId.TEAM_ONE) }) {
                    Text(state.teamName(TeamId.TEAM_ONE))
                }
            },
        )
    }

    // General informational pop-up for terse field guidance and validation messages.
    if (actionInfoMessage != null) {
        AlertDialog(
            onDismissRequest = { dismissActionInfo() },
            title = { Text(actionInfoTitle) },
            text = {
                Text(
                    text = actionInfoMessage!!,
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            confirmButton = {
                TextButton(onClick = { dismissActionInfo() }) {
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
            title = { Text(capPrompt.formatTitle()) },
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
        AlertDialog(
            onDismissRequest = { activeGamePrompt = null },
            title = { Text(prompt.formatTitle()) },
            text = {
                Text(
                    text = prompt.formatMessage(),
                    style = MaterialTheme.typography.bodyLarge,
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

/**
 * Responsive live-screen measurements derived from the available height.
 *
 * @param field The nested field-specific layout metrics.
 */
private data class LiveLayoutMetrics(
    val pagePadding: Dp,
    val sectionSpacing: Dp,
    val statusHeight: Dp,
    val countdownHeight: Dp,
    val bottomActionHeight: Dp,
    val undoHeight: Dp,
    val centerButtonHeight: Dp,
    val centerButtonFontSize: androidx.compose.ui.unit.TextUnit,
    val field: FieldLayoutMetrics,
)

/**
 * Derive the live screen's responsive layout metrics from available content height.
 *
 * @param contentHeight The height inside the scaffold content area.
 */
private fun liveLayoutMetrics(contentHeight: Dp): LiveLayoutMetrics {
    val pagePadding = (contentHeight.value * 0.014f).dp.coerceIn(8.dp, 16.dp)
    val sectionSpacing = (contentHeight.value * 0.011f).dp.coerceIn(6.dp, 12.dp)
    val statusHeight = (contentHeight.value * 0.075f).dp.coerceIn(42.dp, 52.dp)
    val countdownHeight = (contentHeight.value * 0.095f).dp.coerceIn(52.dp, 64.dp)
    val bottomActionHeight = 34.dp
    val undoHeight = 34.dp
    val fieldHeight = (
        contentHeight.value -
            pagePadding.value * 2f -
            sectionSpacing.value * 4f -
            statusHeight.value -
            countdownHeight.value -
            bottomActionHeight.value -
            undoHeight.value
        )
        .coerceAtLeast(0f)
        .dp
    return LiveLayoutMetrics(
        pagePadding = pagePadding,
        sectionSpacing = sectionSpacing,
        statusHeight = statusHeight,
        countdownHeight = countdownHeight,
        bottomActionHeight = bottomActionHeight,
        undoHeight = undoHeight,
        centerButtonHeight = (fieldHeight.value * 0.11f).dp.coerceIn(38.dp, 48.dp),
        centerButtonFontSize = (fieldHeight.value * 0.04f).coerceIn(14f, 16f).sp,
        field = FieldLayoutMetrics.fromFieldHeight(fieldHeight),
    )
}

/**
 * Render the bottom action bar for undo plus immediate redo after an undo.
 *
 * @param state The live state whose undo/redo entries are displayed.
 * @param enabled Whether undo/redo buttons are enabled.
 * @param height The fixed bar height.
 * @param onUndo Callback receiving the state produced by undo.
 * @param onRedo Callback receiving the state produced by redo.
 */
@Composable
private fun UndoRedoBar(
    state: LiveGameState,
    enabled: Boolean,
    height: Dp,
    onUndo: (LiveGameState) -> Unit,
    onRedo: (LiveGameState) -> Unit,
) {
    val undoEntry = state.undoEntry
    val redoEntry = state.redoEntry
    if (undoEntry == null && redoEntry == null) {
        return
    }

    if (redoEntry == null) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            OutlinedButton(
                onClick = { onUndo(state.undoLastAction()) },
                enabled = enabled,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(height)
                    .defaultMinSize(minHeight = 0.dp),
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black,
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.Black),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(undoEntry!!.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        return
    }

    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (undoEntry != null) {
                OutlinedButton(
                    onClick = { onUndo(state.undoLastAction()) },
                    enabled = enabled,
                    modifier = Modifier
                        .weight(3f)
                        .height(height)
                        .defaultMinSize(minHeight = 0.dp),
                    colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                        containerColor = Color.White,
                        contentColor = Color.Black,
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.Black),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
                ) {
                    Text(undoEntry.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            } else {
                Spacer(modifier = Modifier.weight(3f))
            }
            OutlinedButton(
                onClick = { onRedo(state.redoLastAction()) },
                enabled = enabled,
                modifier = Modifier
                    .weight(1f)
                    .height(height)
                    .defaultMinSize(minHeight = 0.dp),
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                    containerColor = Color(0xFF343A40),
                    contentColor = Color.White,
                    disabledContainerColor = Color(0xFF343A40),
                    disabledContentColor = Color.White,
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.Black),
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text("Redo")
            }
        }
    }
}
