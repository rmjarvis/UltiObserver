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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

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
 * @param settings User settings that affect live-game behavior and display.
 * @param onStateChange Callback receiving updated live state from user actions and timer transitions.
 * @param onUpdateGameSetup Callback reopening setup for the current game.
 * @param onOpenGameSummary Callback opening the current game summary.
 * @param onArchiveCompletedGame Callback archiving the current completed game.
 * @param onBackHome Callback returning to Home or setup according to ViewModel navigation rules.
 * @param onHome Callback returning directly to Home.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LiveGameScreen(
    state: GameState,
    settings: Settings,
    onStateChange: (GameState) -> Unit,
    onUpdateGameSetup: () -> Unit,
    onOpenGameSummary: () -> Unit,
    onArchiveCompletedGame: () -> Unit,
    onBackHome: () -> Unit,
    onHome: () -> Unit,
) {
    var pendingCardTeam by remember { mutableStateOf<TeamId?>(null) }
    var showMoreActionsDialog by remember { mutableStateOf(false) }
    var showRulesReference by remember { mutableStateOf(false) }
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
    var showWaterBreakPrompt by remember { mutableStateOf(false) }
    var actionInfoEvent by remember { mutableStateOf<GameEvent?>(null) }
    var activeGamePrompt by remember { mutableStateOf<GamePrompt?>(null) }
    var previouslyObservedPhase by remember { mutableStateOf(state.phase) }
    var suppressNextPhasePrompt by remember { mutableStateOf(false) }

    /// Dismiss the transient action-info popup.
    fun dismissActionInfo() {
        actionInfoEvent = null
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

    // Keep live-game display, transitions, and event timestamps current to the nearest second.
    // Actions that establish or alter time-sensitive state capture a fresh time when invoked.
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
    val passedCapPointEndMessage = remember(state, now) {
        state.passedCapPointEndMessage(now)
    }
    val canStartPoint = remember(state, now) {
        state.phase.isBeforeLivePoint || state.halftimeTransitionReady(now)
    }
    val hasExpiredPullActions = remember(state, now) {
        state.hasExpiredPullActions(now)
    }
    val canReportOffenseSet = remember(state, settings.showDefenseCountdowns) {
        state.canReportOffenseSet(settings.showDefenseCountdowns)
    }

    // Let countdown expiration move the model forward without requiring an observer tap.
    // Coverage: 2 parameter roots (`state`, `settings`) generate Compose effect guards.
    LaunchedEffect(
        state,
        now,
        settings.automaticallyAdvanceCountdowns,
        settings.automaticallyLockLivePoint,
        settings.showDefenseCountdowns,
    ) {
        if (settings.automaticallyAdvanceCountdowns) {
            val transitionedState = state.applyExpiredCountdownTransitions(
                now = now,
                showDefenseCountdowns = settings.showDefenseCountdowns,
            )
            if (transitionedState != state) {
                if (settings.automaticallyLockLivePoint && transitionedState.phase == GamePhase.LIVE_POINT) {
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

    // A heat level can change during a live point. Offer the resulting late water break as
    // soon as the game next reaches an eligible between-points countdown.
    LaunchedEffect(state.pendingWaterBreakOffer, state.phase) {
        if (state.pendingWaterBreakOffer && state.canApplyWaterBreak()) {
            showWaterBreakPrompt = true
        }
    }

    if (state.phase == GamePhase.GAME_OVER) {
        GameOverSummaryScreen(
            state = state,
            summaryActionText = state.undoEntry!!.label,
            onSummaryAction = {
                undoWithoutPhasePrompt(state.undoLastAction())
            },
            secondarySummaryActionText = "Archive game",
            onSecondarySummaryAction = onArchiveCompletedGame,
            onBack = onBackHome,
            onHome = onHome,
        )
        if (activeGamePrompt != null) {
            val prompt = activeGamePrompt!!
            RuleGuidanceGate(
                key = prompt,
                mode = settings.ruleGuidanceMode,
                requiredInNone = prompt.requiresGuidanceInNone(),
                onAutoAccept = {
                    activeGamePrompt = null
                },
            ) {
                GamePromptNoticeDialog(
                    prompt = prompt,
                    onDismiss = {
                        activeGamePrompt = null
                    },
                )
            }
        }
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
                    now = now,
                    capStatus = capStatus,
                    height = layoutMetrics.statusHeight,
                    onRulesReference = {
                        showRulesReference = true
                    },
                )

                // Reserve the countdown row even when no timer is active so the field stays put.
                CountdownLine(
                    countdown = activeCountdown,
                    enabled = !locked,
                    onAdjust = { seconds -> onStateChange(state.addTimeToCountdown(seconds)) },
                    waterBreakAction = if (state.canApplyWaterBreak()) {
                        // Show the icon and have its action be to open the water break prompt.
                        {
                            showWaterBreakPrompt = true
                        }
                    } else {
                        // Don't show the icon.
                        null
                    },
                    onTogglePaused = {
                        onStateChange(state.toggleCountdownPaused(System.currentTimeMillis()))
                    },
                    expiredPullActions = if (hasExpiredPullActions && !locked) {
                        ExpiredPullActions(
                            onRestartPullCountdown = {
                                onStateChange(
                                    state.restartPullCountdown(System.currentTimeMillis())
                                )
                            },
                        )
                    } else {
                        null
                    },
                    misconductCountdownAction = if (state.pendingMisconductCountdown && !locked) {
                        MisconductCountdownAction(
                            onStart = {
                                onStateChange(
                                    state.startMisconductCountdown(System.currentTimeMillis())
                                )
                            },
                        )
                    } else {
                        null
                    },
                    statusMessage = passedCapPointEndMessage,
                    height = layoutMetrics.countdownHeight,
                )

                // Sketch the field with two teams and the grass strip between them.
                FieldSketchCard(
                    state = state,
                    showAbbaRatioAsSequence = settings.showAbbaRatioAsSequence,
                    genderRatioBadgeColorArgb = settings::genderRatioBadgeColorArgb,
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
                                onClick = {
                                    onStateChange(state.reportOffenseSet(System.currentTimeMillis()))
                                },
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
                                    onStateChange(state.beginLivePoint(System.currentTimeMillis()))
                                    if (settings.automaticallyLockLivePoint) {
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
                                    if (settings.automaticallyLockLivePoint) {
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
                    onLock = {
                        locked = true
                    },
                    onGoal = { team ->
                        val updatedState = state.recordGoalFromCurrentState(
                            team,
                            System.currentTimeMillis(),
                        )
                        onStateChange(updatedState)
                    },
                    onTimeout = { team ->
                        pendingTimeoutRequest = PendingTimeoutRequest(
                            team,
                            System.currentTimeMillis(),
                        )
                    },
                    onTimeViolation = { team ->
                        pendingTimeViolationTeam = team
                    },
                    onPullViolation = { team ->
                        pendingPullViolationTeam = team
                        pendingPullViolationType = state.pullViolationTypeFor(team)
                    },
                    onCards = { team ->
                        pendingCardTeam = team
                    },
                    onTechnicalFoul = { team ->
                        pendingTechnicalFoulTeam = team
                    },
                    onTeamInfo = { team ->
                        teamInfoSheetTeam = team
                    },
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

    // Only show one dialog on this screen at a time. There are lots of possible dialogs,
    // so these are listed in priority order. Most can't overlap, but there are a few that
    // matter:
    // 1. pendingCapOffer should be resolved before halftime in case the cap is hard cap,
    //    which can end the game.
    // 2. pendingCapOffer should also be before showWaterBreakPrompt, for the same reason
    //    and also because a soft cap can change the water break prompt.
    // 3. showMoreActions should be last, since it can spawn other dialogs, which should
    //    take precedence over the menu dialog.
    if (pendingCardTeam != null) {
        val team = pendingCardTeam!!
        TeamCardDialog(
            state = state,
            team = team,
            now = now,
            guidanceMode = settings.ruleGuidanceMode,
            onDismiss = { pendingCardTeam = null },
            onAssessment = { updatedState, event ->
                onStateChange(updatedState)
                actionInfoEvent = event
                pendingCardTeam = null
            },
            onStateOnly = { updatedState ->
                onStateChange(updatedState)
                pendingCardTeam = null
            },
            onStateUpdate = onStateChange,
        )
    } else if (showEventLogSheet) {
        EventLogDialog(
            state = state,
            onDismiss = { showEventLogSheet = false },
        )
    } else if (teamInfoSheetTeam != null) {
        val team = teamInfoSheetTeam!!
        TeamNamesDialog(
            team = state.teamFor(team),
            onDismiss = { teamInfoSheetTeam = null },
        )
    } else if (showRulesReference) {
        RulesReferenceDialog(
            state = state,
            onDismiss = {
                showRulesReference = false
            },
        )
    } else if (pendingTimeoutRequest != null) {
        val request = pendingTimeoutRequest!!
        val event = state.previewTimeout(request.team, request.requestedAt).event
        val applyTimeout = {
            val result = state.assessTimeout(request.team, request.requestedAt)
            onStateChange(result.state)
            pendingTimeoutRequest = null
        }
        RuleGuidanceGate(
            key = request,
            mode = settings.ruleGuidanceMode,
            requiredInNone = event.requiresGuidanceInNone(),
            onAutoAccept = applyTimeout,
        ) {
            AlertDialog(
                onDismissRequest = { pendingTimeoutRequest = null },
                title = { Text(event.formatPopupTitle()) },
                text = {
                    RuleGuidanceText(event.guidanceMessage(settings.ruleGuidanceMode))
                },
                confirmButton = {
                    TextActionButton(
                        label = "OK",
                        onClick = applyTimeout,
                    )
                },
                dismissButton = {
                    TextActionButton(label = "Cancel", onClick = { pendingTimeoutRequest = null })
                },
            )
        }
    } else if (pendingTimeViolationTeam != null) {
        val team = pendingTimeViolationTeam!!
        val event = state.previewTimeViolation(team).event
        val applyTimeViolation = {
            val result = state.assessTimeViolation(team, System.currentTimeMillis())
            onStateChange(result.state)
            pendingTimeViolationTeam = null
        }
        RuleGuidanceGate(
            key = team,
            mode = settings.ruleGuidanceMode,
            requiredInNone = event.requiresGuidanceInNone(),
            onAutoAccept = applyTimeViolation,
        ) {
            AlertDialog(
                onDismissRequest = { pendingTimeViolationTeam = null },
                title = { Text(event.formatPopupTitle()) },
                text = {
                    RuleGuidanceText(event.guidanceMessage(settings.ruleGuidanceMode))
                },
                confirmButton = {
                    TextActionButton(
                        label = "OK",
                        onClick = applyTimeViolation,
                    )
                },
                dismissButton = {
                    TextActionButton(label = "Cancel", onClick = { pendingTimeViolationTeam = null })
                },
            )
        }
    } else if (pendingPullViolationTeam != null) {
        val team = pendingPullViolationTeam!!
        val event = state.previewPullViolation(team, pendingPullViolationType).event
        val pullViolationAlternative = event.pullViolationAlternative()
        val applyPullViolation = {
            val result = state.assessPullViolation(
                team = team,
                now = now,
                violation = pendingPullViolationType,
            )
            onStateChange(result.state)
            pendingPullViolationTeam = null
            pendingPullViolationType = PullViolationType.OFFSIDES
        }
        RuleGuidanceGate(
            key = team,
            mode = settings.ruleGuidanceMode,
            requiredInNone = event.requiresGuidanceInNone(),
            onAutoAccept = applyPullViolation,
        ) {
            AlertDialog(
                onDismissRequest = {
                    pendingPullViolationTeam = null
                    pendingPullViolationType = PullViolationType.OFFSIDES
                },
                title = { Text(event.formatPopupTitle()) },
                text = {
                    RuleGuidanceText(event.guidanceMessage(settings.ruleGuidanceMode))
                },
                confirmButton = {
                    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
                        Column(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalAlignment = Alignment.End,
                        ) {
                            if (pullViolationAlternative != null) {
                                MenuButton(
                                    label = pullViolationAlternative.actionLabel,
                                    onClick = {
                                        pendingPullViolationType =
                                            pullViolationAlternative.violation
                                    },
                                    contentPadding = PaddingValues(
                                        horizontal = 16.dp,
                                        vertical = 8.dp,
                                    ),
                                )
                                Spacer(modifier = Modifier.height(8.dp))
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
                                    contentPadding = PaddingValues(
                                        horizontal = 8.dp,
                                        vertical = 0.dp,
                                    ),
                                )
                                TextActionButton(
                                    label = "OK",
                                    onClick = applyPullViolation,
                                    height = 32.dp,
                                    compact = true,
                                    contentPadding = PaddingValues(
                                        horizontal = 8.dp,
                                        vertical = 0.dp,
                                    ),
                                )
                            }
                        }
                    }
                },
            )
        }
    } else if (pendingTechnicalFoulTeam != null) {
        val team = pendingTechnicalFoulTeam!!
        val event = state.previewTechnicalFoul(team).event
        val misconductPrompt = if (
            event.needsMisconductChoice(settings.ruleGuidanceMode)
        ) {
            GamePrompt.LivePointMisconduct(event)
        } else {
            null
        }
        val applyTechnicalFoul = {
            val result = state.assessTechnicalFoul(
                team,
                now,
                settings.ruleGuidanceMode,
            )
            onStateChange(result.state)
            pendingTechnicalFoulTeam = null
        }
        RuleGuidanceGate(
            key = team,
            mode = settings.ruleGuidanceMode,
            requiredInNone = event.requiresGuidanceInNone(),
            onAutoAccept = applyTechnicalFoul,
        ) {
            AlertDialog(
                onDismissRequest = { pendingTechnicalFoulTeam = null },
                title = { Text(event.formatPopupTitle()) },
                text = {
                    RuleGuidanceText(event.misconductConfirmationMessage(settings.ruleGuidanceMode))
                },
                confirmButton = {
                    if (misconductPrompt == null) {
                        TextActionButton(
                            label = "OK",
                            onClick = applyTechnicalFoul,
                        )
                    } else {
                        MisconductChoiceButtons(
                            firstLabel = "Cancel",
                            onFirst = { pendingTechnicalFoulTeam = null },
                            onOffense = {
                                val result = state.assessTechnicalFoul(
                                    team,
                                    now,
                                    settings.ruleGuidanceMode,
                                )
                                pendingTechnicalFoulResolution = PendingFieldTechnicalFoulResolution(
                                    team = team,
                                    result = result,
                                    againstOffense = true,
                                )
                                pendingTechnicalFoulTeam = null
                            },
                            onDefense = {
                                val result = state.assessTechnicalFoul(
                                    team,
                                    now,
                                    settings.ruleGuidanceMode,
                                )
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
    } else if (pendingTechnicalFoulResolution != null) {
        val pending = pendingTechnicalFoulResolution!!
        val prompt = GamePrompt.LivePointMisconduct(pending.result.event)
        AlertDialog(
            onDismissRequest = {
                pendingTechnicalFoulTeam = pending.team
                pendingTechnicalFoulResolution = null
            },
            title = { Text(prompt.formatTitle()) },
            text = {
                RuleGuidanceText(prompt.resolutionMessage(pending.againstOffense))
            },
            confirmButton = {
                TextActionButton(
                    label = "OK",
                    onClick = {
                        onStateChange(pending.result.withResolvedMisconductPenalty().state)
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
    } else if (actionInfoEvent != null) {
        // General informational pop-up for terse field guidance and validation messages.
        val event = actionInfoEvent!!
        RuleGuidanceGate(
            key = event,
            mode = settings.ruleGuidanceMode,
            requiredInNone = event.requiresGuidanceInNone(),
            onAutoAccept = {
                dismissActionInfo()
            },
        ) {
            AlertDialog(
                onDismissRequest = { dismissActionInfo() },
                title = { Text(event.formatPopupTitle()) },
                text = {
                    RuleGuidanceText(event.resultGuidanceMessage(settings.ruleGuidanceMode))
                },
                confirmButton = {
                    TextActionButton(label = "OK", onClick = { dismissActionInfo() })
                },
            )
        }
    } else if (state.pendingCapOffer != null) {
        // Cap prompts block until the observer decides whether to apply the newly eligible cap.
        val capPrompt = GamePrompt.ApplyCap(state, state.pendingCapOffer!!)
        val applyCap = {
            onStateChange(state.applyPendingCap(System.currentTimeMillis()))
        }
        RuleGuidanceGate(
            key = state.pendingCapOffer,
            mode = settings.ruleGuidanceMode,
            requiredInNone = capPrompt.requiresGuidanceInNone(),
            onAutoAccept = applyCap,
        ) {
            AlertDialog(
                onDismissRequest = {},
                title = { Text(capPrompt.formatTitle()) },
                text = {
                    RuleGuidanceText(capPrompt.formatMessage())
                },
                confirmButton = {
                    TextActionButton(
                        label = "OK",
                        onClick = applyCap,
                    )
                },
                dismissButton = {
                    TextActionButton(
                        label = "Not yet",
                        onClick = {
                            onStateChange(state.deferPendingCap())
                        },
                    )
                },
            )
        }
    } else if (showWaterBreakPrompt) {
        val applyWaterBreak = {
            onStateChange(state.applyWaterBreak(now))
            showWaterBreakPrompt = false
        }
        RuleGuidanceGate(
            key = state.pendingWaterBreakOffer to showWaterBreakPrompt,
            mode = settings.ruleGuidanceMode,
            requiredInNone = true,
            onAutoAccept = applyWaterBreak,
        ) {
            AlertDialog(
                onDismissRequest = {
                    if (state.pendingWaterBreakOffer) {
                        onStateChange(state.declinePendingWaterBreak())
                    }
                    showWaterBreakPrompt = false
                },
                title = { Text("Water break") },
                text = {
                    RuleGuidanceText(state.waterBreakPromptMessage())
                },
                confirmButton = {
                    TextActionButton(
                        label = "OK",
                        onClick = applyWaterBreak,
                    )
                },
                dismissButton = {
                    TextActionButton(
                        label = if (state.pendingWaterBreakOffer) "Not yet" else "Cancel",
                        onClick = {
                            if (state.pendingWaterBreakOffer) {
                                onStateChange(state.declinePendingWaterBreak())
                            }
                            showWaterBreakPrompt = false
                        },
                    )
                },
            )
        }
    } else if (activeGamePrompt != null) {
        // Prominent game prompts that are not tied to modal workflows.
        val prompt = activeGamePrompt!!
        RuleGuidanceGate(
            key = prompt,
            mode = settings.ruleGuidanceMode,
            requiredInNone = prompt.requiresGuidanceInNone(),
            onAutoAccept = {
                activeGamePrompt = null
            },
        ) {
            GamePromptNoticeDialog(
                prompt = prompt,
                onDismiss = {
                    activeGamePrompt = null
                },
            )
        }
    } else if (showMoreActionsDialog) {
        // Dialog for less-common actions and manual corrections.
        AlertDialog(
            onDismissRequest = { showMoreActionsDialog = false },
            title = { Text("More actions") },
            text = {
                MoreActionsContent(
                    state = state,
                    now = now,
                    guidanceMode = settings.ruleGuidanceMode,
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
                    onHeatRulesChange = { rules ->
                        onStateChange(
                            state.setHeatGuidance(
                                rules.heatLevel,
                                rules.useAirQualityGuidelines,
                                rules.waterBreakMinutes,
                                System.currentTimeMillis(),
                            )
                        )
                        showMoreActionsDialog = false
                    },
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
}

/// Render an acknowledgement-only game prompt.
@Composable
private fun GamePromptNoticeDialog(prompt: GamePrompt, onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(prompt.formatTitle()) },
        text = {
            RuleGuidanceText(prompt.formatMessage())
        },
        confirmButton = {
            TextActionButton(label = "OK", onClick = onDismiss)
        },
    )
}

/**
 * Render the active game's rules reference.
 *
 * @param state Current game state used to combine setup rules with live cap state.
 * @param onDismiss Callback closing the dialog.
 */
@Composable
internal fun RulesReferenceDialog(
    state: GameState,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Game rules") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                state.rulesReferenceItems().forEach { item ->
                    RulesReferenceRow(item)
                }
            }
        },
        confirmButton = {
            TextActionButton(label = "OK", onClick = onDismiss)
        },
    )
}

/// Render one row in the rules reference.
@Composable
private fun RulesReferenceRow(item: RulesReferenceItem) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = item.label,
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
        )
        Text(
            text = item.value,
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            color = if (item.heatAdjusted) ResetColor else Color.Unspecified,
            fontWeight = if (item.heatAdjusted) FontWeight.Bold else null,
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Render coach and captain names for quick reference.
 *
 * @param team The team whose setup-entered names should be displayed.
 * @param onDismiss Callback closing the dialog.
 */
@Composable
internal fun TeamNamesDialog(team: TeamState, onDismiss: () -> Unit) {
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

    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(height),
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
                    onClick = {
                        onUndo(state.undoLastAction())
                    },
                )
            } else {
                Spacer(modifier = Modifier.weight(3f))
            }
            if (redoEntry != null) {
                NavigationButton(
                    label = "Redo",
                    enabled = enabled,
                    modifier = Modifier.weight(1f),
                    height = height,
                    colors = redoOutlinedButtonColors(),
                    borderColor = RedoColor,
                    compact = true,
                    onClick = {
                        onRedo(state.redoLastAction())
                    },
                )
            }
        }
    }
}
