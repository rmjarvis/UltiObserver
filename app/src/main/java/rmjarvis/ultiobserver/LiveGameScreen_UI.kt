package rmjarvis.ultiobserver

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.LocalTime

/**
 * Timeout request waiting for observer confirmation.
 *
 * @param team The team requesting the timeout.
 * @param requestedAt The epoch millis when the timeout was requested.
 */
private data class PendingTimeoutRequest(
    val team: TeamId,
    val requestedAt: Long,
)

/**
 * Field technical-foul misconduct consequence waiting for final confirmation.
 *
 * @param team The team receiving the technical foul.
 * @param result The assessed result before it is committed to app state.
 * @param againstOffense Whether the misconduct was against the offense.
 */
private data class PendingFieldTechnicalFoulResolution(
    val team: TeamId,
    val result: CardAssessmentResult,
    val againstOffense: Boolean,
)

/**
 * Render the main live-game screen, including the field view, modal flows, and popup cues.
 *
 * @param state The live game state to render.
 * @param automaticallyAdvanceCountdowns Whether expired countdowns should advance model state automatically.
 * @param automaticallyLockLivePoint Whether automatic live-point transitions should lock the screen.
 * @param showDefenseCountdowns Whether timeout offense-set expirations wait for defense.
 * @param onStateChange Callback receiving updated live state from user actions and timer transitions.
 * @param onUpdateGameSetup Callback reopening setup for the current game.
 * @param onOpenGameSummary Callback opening the current game summary.
 * @param onArchiveCompletedGame Callback archiving the current completed game.
 * @param onDeleteGame Callback deleting the current game.
 * @param onBackHome Callback returning to Home or setup according to ViewModel navigation rules.
 * @param onHome Callback returning directly to Home.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LiveGameScreen(
    state: GameState,
    automaticallyAdvanceCountdowns: Boolean,
    automaticallyLockLivePoint: Boolean,
    showDefenseCountdowns: Boolean,
    onStateChange: (GameState) -> Unit,
    onUpdateGameSetup: () -> Unit,
    onOpenGameSummary: () -> Unit,
    onArchiveCompletedGame: () -> Unit,
    onDeleteGame: () -> Unit,
    onBackHome: () -> Unit,
    onHome: () -> Unit,
) {
    var pendingCardTeam by remember { mutableStateOf<TeamId?>(null) }
    var showMoreActionsDialog by remember { mutableStateOf(false) }
    var showEventLogSheet by remember { mutableStateOf(false) }
    var pendingTimeoutRequest by remember { mutableStateOf<PendingTimeoutRequest?>(null) }
    var pendingTimeViolationTeam by remember { mutableStateOf<TeamId?>(null) }
    var pendingPullViolationTeam by remember { mutableStateOf<TeamId?>(null) }
    var pendingPullViolationType by remember { mutableStateOf(PullViolationType.OFFSIDES) }
    var pendingTechnicalFoulTeam by remember { mutableStateOf<TeamId?>(null) }
    var pendingTechnicalFoulResolution by remember {
        mutableStateOf<PendingFieldTechnicalFoulResolution?>(null)
    }
    var teamInfoSheetTeam by remember { mutableStateOf<TeamId?>(null) }
    var locked by remember { mutableStateOf(false) }
    var actionInfoMessage by remember { mutableStateOf<String?>(null) }
    var actionInfoTitle by remember { mutableStateOf("") }
    var activeGamePrompt by remember { mutableStateOf<GamePrompt?>(null) }
    var previouslyObservedPhase by remember { mutableStateOf(state.phase) }
    var suppressNextPhasePrompt by remember { mutableStateOf(false) }

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
     * Apply undo while suppressing phase-change prompts caused by restored state.
     *
     * @param updatedState The state produced by undo.
     */
    fun undoWithoutPhasePrompt(updatedState: GameState) {
        suppressNextPhasePrompt = updatedState.phase != state.phase
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
        state.phase.isBeforeLivePoint || state.halftimeTransitionReady(now)
    }
    val hasExpiredPullActions = remember(state, now) {
        state.hasExpiredPullActions(now)
    }
    val canReportOffenseSet = remember(state, showDefenseCountdowns) {
        state.canReportOffenseSet(showDefenseCountdowns)
    }

    // Let countdown expiration move the model forward without requiring an observer tap.
    LaunchedEffect(
        state,
        now,
        automaticallyAdvanceCountdowns,
        automaticallyLockLivePoint,
        showDefenseCountdowns,
    ) {
        if (automaticallyAdvanceCountdowns) {
            val transitionedState = state.applyExpiredCountdownTransitions(
                now = now,
                showDefenseCountdowns = showDefenseCountdowns,
            )
            if (transitionedState != state) {
                if (automaticallyLockLivePoint) {
                    locked = true
                }
                onStateChange(transitionedState)
            }
        }
    }

    // Only show the large halftime/game-over prompts when those states first become visible.
    LaunchedEffect(state.phase) {
        val previousPhase = previouslyObservedPhase
        if (suppressNextPhasePrompt) {
            previouslyObservedPhase = state.phase
            suppressNextPhasePrompt = false
            return@LaunchedEffect
        }
        // Defensive transition guard so recomposition does not reshow the halftime prompt.
        if (state.phase == GamePhase.HALFTIME && previousPhase != GamePhase.HALFTIME) {
            activeGamePrompt = GamePrompt.HalftimeStarted(state)
        }
        // Defensive transition guard so recomposition does not reshow the game-over prompt.
        if (state.phase == GamePhase.GAME_OVER && previousPhase != GamePhase.GAME_OVER) {
            activeGamePrompt = GamePrompt.GameOver(state)
        }
        previouslyObservedPhase = state.phase
    }

    if (state.phase == GamePhase.GAME_OVER) {
        GameOverSummaryScreen(
            state = state,
            summaryActionText = "Undo End game",
            onSummaryAction = {
                undoWithoutPhasePrompt(state.undoLastAction())
            },
            secondarySummaryActionText = "Archive game",
            onSecondarySummaryAction = onArchiveCompletedGame,
            onBack = onBackHome,
            onHome = onHome,
            gameOverPrompt = activeGamePrompt,
            onDismissGameOverPrompt = {
                activeGamePrompt = null
            },
        )
        return
    }

    // Compose the major elements of the live game screen.
    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("UltiObserver") },
                navigationIcon = {
                    TopBarBackButton(onClick = onBackHome)
                },
                actions = {
                    TopBarHomeButton(onClick = onHome)
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
                    onTogglePaused = { onStateChange(state.toggleCountdownPaused(now)) },
                    expiredPullActions = if (hasExpiredPullActions && !locked) {
                        ExpiredPullActions(
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
                    timeoutEnabled = state.canRequestTimeout(now),
                    showPullIndicator = !locked,
                    metrics = layoutMetrics.field,
                    centerContent = {
                        if (locked) {
                            FieldUnlockControl(onUnlock = { locked = false })
                        } else if (canReportOffenseSet) {
                            BigActionButton(
                                label = "Offense is set",
                                onClick = { onStateChange(state.reportOffenseSet(now)) },
                                containerColor = FieldNeutralButtonColor,
                                contentColor = Color.Black,
                                height = layoutMetrics.centerButtonHeight,
                                fontSize = layoutMetrics.centerButtonFontSize,
                                tag = "live-offense-set",
                            )
                        } else if (canStartPoint) {
                            BigActionButton(
                                label = "Start point",
                                onClick = {
                                    onStateChange(state.beginLivePoint(now))
                                    if (automaticallyLockLivePoint) {
                                        locked = true
                                    }
                                },
                                containerColor = FieldNeutralButtonColor,
                                contentColor = Color.Black,
                                height = layoutMetrics.centerButtonHeight,
                                fontSize = layoutMetrics.centerButtonFontSize,
                            )
                        } else if (state.phase == GamePhase.LIVE_POINT && state.countdown != null) {
                            BigActionButton(
                                label = "Continue point",
                                onClick = {
                                    onStateChange(state.continueLivePoint())
                                    if (automaticallyLockLivePoint) {
                                        locked = true
                                    }
                                },
                                containerColor = FieldNeutralButtonColor,
                                contentColor = Color.Black,
                                height = layoutMetrics.centerButtonHeight,
                                fontSize = layoutMetrics.centerButtonFontSize,
                            )
                        }
                    },
                    onLock = { locked = true },
                    onGoal = { team -> onStateChange(state.recordGoalFromCurrentState(team, now)) },
                    onTimeout = { team ->
                        pendingTimeoutRequest = PendingTimeoutRequest(team, System.currentTimeMillis())
                    },
                    onTimeViolation = { team ->
                        pendingTimeViolationTeam = team
                    },
                    onPullViolation = { team ->
                        val violation = state.pullViolationTypeFor(team)
                        pendingPullViolationTeam = team
                        pendingPullViolationType = violation
                    },
                    onCards = { team -> pendingCardTeam = team },
                    onTechnicalFoul = { team -> pendingTechnicalFoulTeam = team },
                    onTeamInfo = { team -> teamInfoSheetTeam = team },
                )

                // More actions keeps less-common game actions out of the field action grid.
                NavigationButton(
                    label = "More actions",
                    fullWidth = true,
                    height = layoutMetrics.bottomActionHeight,
                    enabled = !locked,
                    colors = neutralOutlinedButtonColors(DarkNeutralColor),
                    borderColor = MaterialTheme.colorScheme.outline,
                    compact = true,
                    onClick = { showMoreActionsDialog = true },
                )

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

    pendingCardTeam?.let { team ->
        TeamCardDialog(
            state = state,
            team = team,
            now = now,
            onDismiss = { pendingCardTeam = null },
            onAssessment = { updatedState, message, title ->
                onStateChange(updatedState)
                showActionInfo(
                    message = message,
                    title = title,
                )
                pendingCardTeam = null
            },
            onStateOnly = { updatedState ->
                onStateChange(updatedState)
                pendingCardTeam = null
            },
            onStateUpdate = onStateChange,
        )
    }

    // Dialog for less-common actions and manual corrections.
    if (showMoreActionsDialog) {
        AlertDialog(
            onDismissRequest = { showMoreActionsDialog = false },
            title = { Text("More actions") },
            text = {
                MoreActionsContent(
                    state = state,
                    now = now,
                    onUpdateGameSetup = {
                        showMoreActionsDialog = false
                        onUpdateGameSetup()
                    },
                    onShowEventLog = {
                        showMoreActionsDialog = false
                        showEventLogSheet = true
                    },
                    onShowGameSummary = {
                        showMoreActionsDialog = false
                        onOpenGameSummary()
                    },
                    onDeleteGame = onDeleteGame,
                    onAction = { updatedState ->
                        onStateChange(updatedState)
                        showMoreActionsDialog = false
                    },
                    onStateUpdate = onStateChange,
                )
            },
            confirmButton = {
                TextActionButton(label = "Close", onClick = { showMoreActionsDialog = false })
            },
        )
    }

    if (showEventLogSheet) {
        EventLogDialog(
            state = state,
            onDismiss = { showEventLogSheet = false },
        )
    }

    teamInfoSheetTeam?.let { team ->
        TeamNamesDialog(
            team = state.teamFor(team),
            onDismiss = { teamInfoSheetTeam = null },
        )
    }

    pendingTimeoutRequest?.let { request ->
        val event = state.previewTimeout(request.team, request.requestedAt).event
        AlertDialog(
            onDismissRequest = { pendingTimeoutRequest = null },
            title = { Text(event.formatPopupTitle()) },
            text = {
                Text(
                    text = event.formatMessage(),
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            confirmButton = {
                TextActionButton(
                    label = "OK",
                    onClick = {
                        val result = state.assessTimeout(request.team, request.requestedAt)
                        onStateChange(result.state)
                        pendingTimeoutRequest = null
                    },
                )
            },
            dismissButton = {
                TextActionButton(label = "Cancel", onClick = { pendingTimeoutRequest = null })
            },
        )
    }

    pendingTimeViolationTeam?.let { team ->
        val event = state.previewTimeViolation(team).event
        AlertDialog(
            onDismissRequest = { pendingTimeViolationTeam = null },
            title = { Text(event.formatPopupTitle()) },
            text = {
                Text(
                    text = event.formatMessage(),
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            confirmButton = {
                TextActionButton(
                    label = "OK",
                    onClick = {
                        val result = state.assessTimeViolation(team, System.currentTimeMillis())
                        onStateChange(result.state)
                        pendingTimeViolationTeam = null
                    },
                )
            },
            dismissButton = {
                TextActionButton(label = "Cancel", onClick = { pendingTimeViolationTeam = null })
            },
        )
    }

    pendingPullViolationTeam?.let { team ->
        val event = state.previewPullViolation(team, pendingPullViolationType).event
        val canSwitchPullingViolation = pendingPullViolationType != PullViolationType.FALSE_START &&
            state.usesMixedDivision()
        AlertDialog(
            onDismissRequest = {
                pendingPullViolationTeam = null
                pendingPullViolationType = PullViolationType.OFFSIDES
            },
            title = { Text(event.formatPopupTitle()) },
            text = {
                Text(
                    text = event.formatMessage(),
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            confirmButton = {
                CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.End,
                    ) {
                        if (canSwitchPullingViolation) {
                            TextActionButton(
                                label = if (
                                    pendingPullViolationType == PullViolationType.MAJORITY_PULL
                                ) {
                                    "This was an Offsides"
                                } else {
                                    "This was a Majority pull rule violation"
                                },
                                onClick = {
                                    pendingPullViolationType = if (
                                        pendingPullViolationType == PullViolationType.MAJORITY_PULL
                                    ) {
                                        PullViolationType.OFFSIDES
                                    } else {
                                        PullViolationType.MAJORITY_PULL
                                    }
                                },
                                height = 32.dp,
                                compact = true,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            )
                        }
                        Row(horizontalArrangement = Arrangement.End) {
                            TextActionButton(
                                label = "Cancel",
                                onClick = {
                                    pendingPullViolationTeam = null
                                    pendingPullViolationType = PullViolationType.OFFSIDES
                                },
                                height = 32.dp,
                                compact = true,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            )
                            TextActionButton(
                                label = "OK",
                                onClick = {
                                    val result = state.assessPullViolation(
                                        team = team,
                                        now = System.currentTimeMillis(),
                                        violation = pendingPullViolationType,
                                    )
                                    onStateChange(result.state)
                                    pendingPullViolationTeam = null
                                    pendingPullViolationType = PullViolationType.OFFSIDES
                                },
                                height = 32.dp,
                                compact = true,
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 0.dp),
                            )
                        }
                    }
                }
            },
        )
    }

    pendingTechnicalFoulTeam?.let { team ->
        val event = state.previewTechnicalFoul(team).event
        val misconductPrompt = if (event.needsMisconductChoice()) {
            GamePrompt.LivePointMisconduct(event)
        } else {
            null
        }
        AlertDialog(
            onDismissRequest = { pendingTechnicalFoulTeam = null },
            title = { Text(event.formatPopupTitle()) },
            text = {
                Text(
                    text = misconductPrompt?.formatMessage() ?: event.formatMessage(),
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            confirmButton = {
                if (misconductPrompt == null) {
                    TextActionButton(
                        label = "OK",
                        onClick = {
                            val result = state.assessTechnicalFoul(team, System.currentTimeMillis())
                            onStateChange(result.state)
                            pendingTechnicalFoulTeam = null
                        },
                    )
                } else {
                    MisconductChoiceButtons(
                        firstLabel = "Cancel",
                        onFirst = { pendingTechnicalFoulTeam = null },
                        onOffense = {
                            val result = state.assessTechnicalFoul(team, System.currentTimeMillis())
                            pendingTechnicalFoulResolution = PendingFieldTechnicalFoulResolution(
                                team = team,
                                result = result,
                                againstOffense = true,
                            )
                            pendingTechnicalFoulTeam = null
                        },
                        onDefense = {
                            val result = state.assessTechnicalFoul(team, System.currentTimeMillis())
                            pendingTechnicalFoulResolution = PendingFieldTechnicalFoulResolution(
                                team = team,
                                result = result,
                                againstOffense = false,
                            )
                            pendingTechnicalFoulTeam = null
                        },
                    )
                }
            },
            dismissButton = if (misconductPrompt == null) {
                {
                    TextActionButton(
                        label = "Cancel",
                        onClick = { pendingTechnicalFoulTeam = null },
                    )
                }
            } else {
                null
            },
        )
    }

    pendingTechnicalFoulResolution?.let { pending ->
        val prompt = GamePrompt.LivePointMisconduct(pending.result.event)
        AlertDialog(
            onDismissRequest = {
                pendingTechnicalFoulTeam = pending.team
                pendingTechnicalFoulResolution = null
            },
            title = { Text(prompt.formatTitle()) },
            text = {
                Text(
                    text = prompt.resolutionMessage(pending.againstOffense),
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            confirmButton = {
                TextActionButton(
                    label = "OK",
                    onClick = {
                        onStateChange(pending.result.state.withPendingMisconductCountdown())
                        pendingTechnicalFoulResolution = null
                    },
                )
            },
            dismissButton = {
                TextActionButton(
                    label = "Back",
                    tag = "misconduct-resolution-back",
                    onClick = {
                        pendingTechnicalFoulTeam = pending.team
                        pendingTechnicalFoulResolution = null
                    },
                )
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
                TextActionButton(label = "OK", onClick = { dismissActionInfo() })
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
                TextActionButton(label = "Apply", onClick = { onStateChange(state.applyPendingCap(now)) })
            },
            dismissButton = {
                TextActionButton(label = "No", onClick = { onStateChange(state.deferPendingCap()) })
            },
        )
    }

    // Prominent game prompts that are not tied to modal workflows.
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
                TextActionButton(label = "OK", onClick = { activeGamePrompt = null })
            },
        )
    }
}

/**
 * Render coach and captain names for quick live-game reference.
 *
 * @param team The team whose setup-entered names should be displayed.
 * @param onDismiss Callback closing the dialog.
 */
@Composable
private fun TeamNamesDialog(team: TeamState, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(team.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                TeamNamesRow(singularLabel = "Coach", pluralLabel = "Coaches", value = team.coaches)
                TeamNamesRow(
                    singularLabel = "Field captain",
                    pluralLabel = "Field captains",
                    value = team.fieldCaptains,
                )
                TeamNamesRow(
                    singularLabel = "Spirit captain",
                    pluralLabel = "Spirit captains",
                    value = team.spiritCaptains,
                )
            }
        },
        confirmButton = {
            TextActionButton(label = "OK", onClick = onDismiss)
        },
    )
}

/**
 * Render one non-empty coach/captain names section.
 *
 * @param singularLabel The section label when one nonblank line was entered.
 * @param pluralLabel The section label when multiple nonblank lines were entered.
 * @param value The setup-entered names for that section.
 */
@Composable
private fun TeamNamesRow(singularLabel: String, pluralLabel: String, value: String) {
    val trimmedValue = value.trim()
    if (trimmedValue.isBlank()) {
        return
    }
    val label = if (trimmedValue.nonblankLineCount() == 1) singularLabel else pluralLabel
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = trimmedValue,
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

/// Return the number of nonblank lines in a setup-entered coach/captain field.
private fun String.nonblankLineCount(): Int {
    return lineSequence().count { it.isNotBlank() }
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
    state: GameState,
    enabled: Boolean,
    height: Dp,
    onUndo: (GameState) -> Unit,
    onRedo: (GameState) -> Unit,
) {
    val undoEntry = state.undoEntry
    val redoEntry = state.redoEntry
    if (undoEntry == null && redoEntry == null) {
        return
    }

    if (redoEntry == null) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            NavigationButton(
                label = undoEntry!!.label,
                enabled = enabled,
                fullWidth = true,
                height = height,
                colors = resetOutlinedButtonColors(),
                borderColor = ResetColor,
                compact = true,
                onClick = { onUndo(state.undoLastAction()) },
            )
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
                NavigationButton(
                    label = undoEntry.label,
                    enabled = enabled,
                    modifier = Modifier.weight(3f),
                    height = height,
                    colors = resetOutlinedButtonColors(),
                    borderColor = ResetColor,
                    compact = true,
                    onClick = { onUndo(state.undoLastAction()) },
                )
            } else {
                Spacer(modifier = Modifier.weight(3f))
            }
            NavigationButton(
                label = "Redo",
                enabled = enabled,
                modifier = Modifier.weight(1f),
                height = height,
                colors = redoOutlinedButtonColors(),
                borderColor = RedoColor,
                compact = true,
                onClick = { onRedo(state.redoLastAction()) },
            )
        }
    }
}
