package rmjarvis.ultiobserver

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.displayCutout
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
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
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Render the active-game screen, including the field view, modal flows, and popup cues.
 *
 * @param state The live game state to render.
 * @param settings User settings that affect live-game behavior and display.
 * @param displayOrientation Readable orientation currently shown by Android.
 * @param activeCardEntry Card workflow currently active on the phone, when present.
 * @param onStateChange Callback receiving updated live state from user actions and timer transitions.
 * @param onGoal Callback recording a goal for a specific team.
 * @param onDecision Callback accepting or deferring a pending game decision.
 * @param onConfirmation Callback committing an action after the observer selects OK.
 * @param onCardEntryChange Callback conditionally replacing the active card workflow.
 * @param onCardEntryCompleted Callback committing a card action and completing its workflow.
 * @param onUpdateGameSetup Callback reopening setup for the current game.
 * @param onOpenGameSummary Callback opening the current game summary.
 * @param onBackHome Callback returning to Home or setup according to AppState navigation rules.
 * @param onHome Callback returning directly to Home.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ActiveGameScreen(
    state: GameState,
    settings: Settings,
    displayOrientation: ActiveGameFullOrientation,
    activeCardEntry: ActiveCardEntry?,
    onStateChange: (GameState) -> Unit,
    onGoal: (TeamId) -> Unit,
    onDecision: (accept: Boolean) -> Unit,
    onConfirmation: (GamePrompt.ActionConfirmation) -> Unit,
    onCardEntryChange: (ActiveCardEntry?, ActiveCardEntry?) -> Unit,
    onCardEntryCompleted: (ActiveCardEntry, GameState) -> Unit,
    onUpdateGameSetup: () -> Unit,
    onOpenGameSummary: () -> Unit,
    onBackHome: () -> Unit,
    onHome: () -> Unit,
) {
    var showMoreActionsDialog by remember { mutableStateOf(false) }
    var moreActionsChild by remember { mutableStateOf<MoreActionsChild?>(null) }
    var moreActionsCategory by remember {
        mutableStateOf(MoreActionsCategory.SETUP_CHANGES)
    }
    var showRulesReference by remember { mutableStateOf(false) }
    var showEventLogSheet by remember { mutableStateOf(false) }
    var pendingTimeoutConfirmation by remember {
        mutableStateOf<GamePrompt.TimeoutConfirmation?>(null)
    }
    var pendingTimeViolation by remember(state) {
        mutableStateOf<GamePrompt.TimeViolationConfirmation?>(null)
    }
    var pendingPullViolation by remember(state) {
        mutableStateOf<GamePrompt.PullViolationConfirmation?>(null)
    }
    var pendingTechnicalFoul by remember {
        mutableStateOf<GamePrompt.TechnicalFoulConfirmation?>(null)
    }
    var teamInfoSheetTeam by remember { mutableStateOf<TeamId?>(null) }
    var locked by remember { mutableStateOf(false) }
    var showManualWaterBreakPrompt by remember { mutableStateOf(false) }
    val activeGameDisplay = settings.orientationPreference.displayFor(
        displayOrientation = displayOrientation,
        phoneTopEnd = state.topDisplayedEnd,
    )
    val usesLandscapeOrientation =
        activeGameDisplay.orientation == ActiveGameOrientation.LANDSCAPE

    /// Dismiss the pending pull-violation confirmation.
    fun dismissPullViolation() {
        pendingPullViolation = null
    }

    /// Dismiss the pending time-violation confirmation.
    fun dismissTimeViolation() {
        pendingTimeViolation = null
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
    val capStatusMessage = remember(state, now) {
        state.capStatusMessage(now)
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
    val pendingGameDecision = state.pendingGameDecision()

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

    // Completed games move directly to their summary; the decision preceded the phase change.
    LaunchedEffect(state.phase) {
        if (state.phase == GamePhase.GAME_OVER) {
            onOpenGameSummary()
        }
    }

    val onLockedChange: (Boolean) -> Unit = { locked = it }
    val onRulesReference = { showRulesReference = true }
    val onWaterBreak = { showManualWaterBreakPrompt = true }
    val onMoreActions = {
        moreActionsCategory = MoreActionsCategory.SETUP_CHANGES
        showMoreActionsDialog = true
    }
    val onTimeout: (TeamId) -> Unit
    onTimeout = { team ->
        val requestedAt = System.currentTimeMillis()
        pendingTimeoutConfirmation = GamePrompt.TimeoutConfirmation(
            state = state,
            team = team,
            requestedAt = if (state.phase == GamePhase.LIVE_POINT) {
                settings.adjustedCountdownStartEpoch(requestedAt)
            } else {
                requestedAt
            },
        )
    }
    val onTimeViolation: (TeamId) -> Unit
    onTimeViolation = { team ->
        pendingTimeViolation = GamePrompt.TimeViolationConfirmation(
            state = state,
            team = team,
            requestedAt = System.currentTimeMillis(),
        )
    }
    val onPullViolation: (TeamId) -> Unit
    onPullViolation = { team ->
        pendingPullViolation = GamePrompt.PullViolationConfirmation(
            state = state,
            team = team,
            requestedAt = System.currentTimeMillis(),
            violation = state.pullViolationTypeFor(team),
        )
    }
    val onCards: (TeamId) -> Unit = { team ->
        onCardEntryChange(
            null,
            ActiveCardEntry(team = team, cardType = null, jerseyNumber = ""),
        )
    }
    val onTechnicalFoul: (TeamId) -> Unit
    onTechnicalFoul = { team ->
        val requestedAt = System.currentTimeMillis()
        pendingTechnicalFoul = GamePrompt.TechnicalFoulConfirmation(
            state = state,
            team = team,
            requestedAt = requestedAt,
        )
    }
    val onTeamInfo: (TeamId) -> Unit = { team ->
        teamInfoSheetTeam = team
    }
    val onUndo: (GameState) -> Unit
    onUndo = { previousState ->
        onStateChange(previousState)
    }

    // Compose the major elements of the active-game screen.
    Scaffold(
        topBar = {
            if (usesLandscapeOrientation) {
                LandscapeNavigationBar(
                    onBackHome = onBackHome,
                    onHome = onHome,
                )
            } else {
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
        }
    ) { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (usesLandscapeOrientation) {
                LandscapeActiveGameContent(
                    state = state,
                    settings = settings,
                    leftDisplayedEnd = activeGameDisplay.topOrLeftDisplayedEnd,
                    activeGameLayout = activeGameDisplay.layout,
                    now = now,
                    capStatus = capStatus,
                    activeCountdown = activeCountdown,
                    capStatusMessage = capStatusMessage,
                    canStartPoint = canStartPoint,
                    hasExpiredPullActions = hasExpiredPullActions,
                    canReportOffenseSet = canReportOffenseSet,
                    locked = locked,
                    maxWidth = maxWidth,
                    maxHeight = maxHeight,
                    onStateChange = onStateChange,
                    onGoal = onGoal,
                    onLockedChange = onLockedChange,
                    onRulesReference = onRulesReference,
                    onWaterBreak = onWaterBreak,
                    onMoreActions = onMoreActions,
                    onTimeout = onTimeout,
                    onTimeViolation = onTimeViolation,
                    onPullViolation = onPullViolation,
                    onCards = onCards,
                    onTechnicalFoul = onTechnicalFoul,
                    onTeamInfo = onTeamInfo,
                    onUndo = onUndo,
                )
            } else {
                PortraitActiveGameContent(
                    state = state,
                    settings = settings,
                    topDisplayedEnd = activeGameDisplay.topOrLeftDisplayedEnd,
                    now = now,
                    capStatus = capStatus,
                    activeCountdown = activeCountdown,
                    capStatusMessage = capStatusMessage,
                    canStartPoint = canStartPoint,
                    hasExpiredPullActions = hasExpiredPullActions,
                    canReportOffenseSet = canReportOffenseSet,
                    locked = locked,
                    maxHeight = maxHeight,
                    onStateChange = onStateChange,
                    onGoal = onGoal,
                    onLockedChange = onLockedChange,
                    onRulesReference = onRulesReference,
                    onWaterBreak = onWaterBreak,
                    onMoreActions = onMoreActions,
                    onTimeout = onTimeout,
                    onTimeViolation = onTimeViolation,
                    onPullViolation = onPullViolation,
                    onCards = onCards,
                    onTechnicalFoul = onTechnicalFoul,
                    onTeamInfo = onTeamInfo,
                    onUndo = onUndo,
                )
            }
        }
    }

    // Only show one dialog on this screen at a time. There are lots of possible dialogs,
    // so these are listed in priority order. Most can't overlap, but there are a few that
    // matter:
    // 1. pendingGameDecision chooses cap, automatic water break, or score transition in the
    //    required order.
    // 2. A pending cap is also before a manually requested water break because applying soft cap
    //    can change the water-break guidance.
    // 3. showMoreActions should be last, since it can spawn other dialogs, which should
    //    take precedence over the menu dialog.
    if (activeCardEntry != null) {
        val cardEntry = activeCardEntry
        TeamCardDialog(
            state = state,
            team = cardEntry.team,
            now = now,
            guidanceMode = settings.ruleGuidanceMode,
            isLandscape = usesLandscapeOrientation,
            initialCardType = cardEntry.cardType,
            initialJerseyNumber = cardEntry.jerseyNumber,
            onCardTypeSelected = { cardType ->
                val jerseyNumber = if (cardType == null) "" else cardEntry.jerseyNumber
                onCardEntryChange(
                    cardEntry,
                    cardEntry.copy(
                        cardType = cardType,
                        jerseyNumber = jerseyNumber,
                    ),
                )
            },
            onDismiss = {
                onCardEntryChange(cardEntry, null)
            },
            onCardEntryCompleted = { updatedState ->
                onCardEntryCompleted(cardEntry, updatedState)
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
            activeGameOrientation = activeGameDisplay.orientation,
            onDismiss = {
                showRulesReference = false
            },
        )
    } else if (pendingTimeoutConfirmation != null) {
        val confirmation = pendingTimeoutConfirmation!!
        val event = confirmation.event
        val applyTimeout = {
            onConfirmation(confirmation)
            pendingTimeoutConfirmation = null
        }
        RuleGuidanceGate(
            key = confirmation,
            mode = settings.ruleGuidanceMode,
            requiredInNone = event.requiresGuidanceInNone(),
            onAutoAccept = applyTimeout,
        ) {
            ResponsiveAlertDialog(
                onDismissRequest = { pendingTimeoutConfirmation = null },
                title = { Text(event.formatTitle()) },
                text = {
                    ScrollableDialogRegion(maxHeight = dialogBodyMaxHeight()) {
                        RuleGuidanceText(confirmation.formatMessage(settings.ruleGuidanceMode))
                    }
                },
                confirmButton = {
                    TextActionButton(
                        label = "OK",
                        onClick = applyTimeout,
                    )
                },
                dismissButton = {
                    TextActionButton(
                        label = "Cancel",
                        onClick = { pendingTimeoutConfirmation = null },
                    )
                },
                widthProfile = DialogWidthProfile.COMPACT,
            )
        }
    } else if (pendingTimeViolation != null) {
        val confirmation = pendingTimeViolation!!
        val event = confirmation.event
        val applyTimeViolation = {
            onConfirmation(confirmation)
            dismissTimeViolation()
        }
        RuleGuidanceGate(
            key = confirmation,
            mode = settings.ruleGuidanceMode,
            requiredInNone = event.requiresGuidanceInNone(),
            onAutoAccept = applyTimeViolation,
        ) {
            ResponsiveAlertDialog(
                onDismissRequest = { dismissTimeViolation() },
                title = { Text(event.formatTitle()) },
                text = {
                    ScrollableDialogRegion(maxHeight = dialogBodyMaxHeight()) {
                        RuleGuidanceText(confirmation.formatMessage(settings.ruleGuidanceMode))
                    }
                },
                confirmButton = {
                    TextActionButton(
                        label = "OK",
                        onClick = applyTimeViolation,
                    )
                },
                dismissButton = {
                    TextActionButton(label = "Cancel", onClick = { dismissTimeViolation() })
                },
            )
        }
    } else if (pendingPullViolation != null) {
        val confirmation = pendingPullViolation!!
        val event = confirmation.event
        val pullViolationAlternative = event.pullViolationSelections()
            .firstOrNull { selection -> selection.violation != confirmation.violation }
        val applyPullViolation = {
            onConfirmation(confirmation)
            dismissPullViolation()
        }
        RuleGuidanceGate(
            key = confirmation.team,
            mode = settings.ruleGuidanceMode,
            requiredInNone = event.requiresGuidanceInNone(),
            onAutoAccept = applyPullViolation,
        ) {
            ResponsiveAlertDialog(
                onDismissRequest = {
                    dismissPullViolation()
                },
                title = { Text(event.formatTitle()) },
                text = {
                    ScrollableDialogRegion(maxHeight = dialogBodyMaxHeight()) {
                        RuleGuidanceText(confirmation.formatMessage(settings.ruleGuidanceMode))
                    }
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
                                        pendingPullViolation =
                                            GamePrompt.PullViolationConfirmation(
                                                state = confirmation.state,
                                                team = confirmation.team,
                                                requestedAt = confirmation.requestedAt,
                                                violation = pullViolationAlternative.violation,
                                            )
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
                                        dismissPullViolation()
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
    } else if (pendingTechnicalFoul != null) {
        val confirmation = pendingTechnicalFoul!!
        val event = confirmation.event
        val applyTechnicalFoul = {
            onConfirmation(confirmation)
            pendingTechnicalFoul = null
        }
        RuleGuidanceGate(
            key = confirmation,
            mode = settings.ruleGuidanceMode,
            requiredInNone = event.requiresGuidanceInNone(),
            onAutoAccept = applyTechnicalFoul,
        ) {
            ResponsiveAlertDialog(
                onDismissRequest = { pendingTechnicalFoul = null },
                title = { Text(event.formatTitle()) },
                text = {
                    ScrollableDialogRegion(maxHeight = dialogBodyMaxHeight()) {
                        RuleGuidanceText(confirmation.formatMessage(settings.ruleGuidanceMode))
                    }
                },
                confirmButton = {
                    TextActionButton(
                        label = "OK",
                        onClick = applyTechnicalFoul,
                    )
                },
                dismissButton = {
                    TextActionButton(
                        label = "Cancel",
                        onClick = { pendingTechnicalFoul = null },
                    )
                },
            )
        }
    } else if (pendingGameDecision is GamePrompt.ApplyCap) {
        // Cap prompts block until the observer decides whether to apply the newly eligible cap.
        val capPrompt = pendingGameDecision
        val applyCap = {
            onDecision(true)
        }
        RuleGuidanceGate(
            key = capPrompt,
            mode = settings.ruleGuidanceMode,
            requiredInNone = capPrompt.requiresGuidanceInNone(),
            onAutoAccept = applyCap,
        ) {
            ResponsiveAlertDialog(
                onDismissRequest = {},
                title = { Text(capPrompt.formatTitle()) },
                text = {
                    ScrollableDialogRegion(maxHeight = dialogBodyMaxHeight()) {
                        RuleGuidanceText(capPrompt.formatMessage())
                    }
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
                            onDecision(false)
                        },
                    )
                },
                widthProfile = DialogWidthProfile.COMPACT,
            )
        }
    } else if (
        pendingGameDecision is GamePrompt.WaterBreak ||
        showManualWaterBreakPrompt
    ) {
        val pendingWaterBreak = pendingGameDecision as? GamePrompt.WaterBreak
        val prompt: GamePrompt.WaterBreakPrompt = pendingWaterBreak
            ?: GamePrompt.ManualWaterBreak(state)
        val applyWaterBreak = {
            if (pendingWaterBreak != null) {
                onDecision(true)
            } else {
                onStateChange(state.applyWaterBreak(now))
            }
            showManualWaterBreakPrompt = false
        }
        RuleGuidanceGate(
            key = prompt,
            mode = settings.ruleGuidanceMode,
            requiredInNone = prompt.requiresGuidanceInNone(),
            onAutoAccept = applyWaterBreak,
        ) {
            ResponsiveAlertDialog(
                onDismissRequest = {
                    if (pendingWaterBreak != null) {
                        onDecision(false)
                    }
                    showManualWaterBreakPrompt = false
                },
                title = { Text(prompt.formatTitle()) },
                text = {
                    ScrollableDialogRegion(maxHeight = dialogBodyMaxHeight()) {
                        RuleGuidanceText(prompt.formatMessage())
                    }
                },
                confirmButton = {
                    TextActionButton(
                        label = "OK",
                        onClick = applyWaterBreak,
                    )
                },
                dismissButton = {
                    TextActionButton(
                        label = if (pendingWaterBreak != null) "Not yet" else "Cancel",
                        onClick = {
                            if (pendingWaterBreak != null) {
                                onDecision(false)
                            }
                            showManualWaterBreakPrompt = false
                        },
                    )
                },
                widthProfile = DialogWidthProfile.COMPACT,
            )
        }
    } else if (pendingGameDecision != null) {
        val prompt = pendingGameDecision
        RuleGuidanceGate(
            key = prompt,
            mode = settings.ruleGuidanceMode,
            requiredInNone = prompt.requiresGuidanceInNone(),
            onAutoAccept = {
                onDecision(true)
            },
        ) {
            GamePromptDecisionDialog(
                prompt = prompt,
                guidanceMode = settings.ruleGuidanceMode,
                onAccept = {
                    onDecision(true)
                },
                onNotYet = {
                    onDecision(false)
                },
            )
        }
    } else if (moreActionsChild != null) {
        MoreActionsChildDialog(
            child = moreActionsChild!!,
            state = state,
            now = now,
            activeGameOrientation = activeGameDisplay.orientation,
            activeGameLayout = activeGameDisplay.layout,
            guidanceMode = settings.ruleGuidanceMode,
            onDismiss = { moreActionsChild = null },
            onHeatRulesChange = { rules ->
                onStateChange(
                    state.setHeatGuidance(
                        rules.heatLevel,
                        rules.useAirQualityGuidelines,
                        rules.waterBreakMinutes,
                        System.currentTimeMillis(),
                    )
                )
                moreActionsChild = null
                showMoreActionsDialog = false
            },
            onAction = { updatedState ->
                onStateChange(updatedState)
                moreActionsChild = null
                showMoreActionsDialog = false
            },
            onStateUpdate = onStateChange,
        )
    } else if (showMoreActionsDialog) {
        // Dialog for less-common actions and manual corrections.
        ResponsiveAlertDialog(
            onDismissRequest = { showMoreActionsDialog = false },
            title = { Text("More actions") },
            text = {
                MoreActionsContent(
                    state = state,
                    activeGameOrientation = activeGameDisplay.orientation,
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
                    onOpenChild = { moreActionsChild = it },
                    onAction = { updatedState ->
                        onStateChange(updatedState)
                        showMoreActionsDialog = false
                    },
                    selectedCategory = moreActionsCategory,
                    onCategorySelected = { moreActionsCategory = it },
                )
            },
            confirmButton = {
                TextActionButton(label = "Close", onClick = { showMoreActionsDialog = false })
            },
            widthProfile = DialogWidthProfile.WIDE,
        )
    }
}

/// Render a pending score-transition prompt that may be accepted or deferred.
@Composable
private fun GamePromptDecisionDialog(
    prompt: GamePrompt,
    guidanceMode: RuleGuidanceMode,
    onAccept: () -> Unit,
    onNotYet: () -> Unit,
) {
    ResponsiveAlertDialog(
        onDismissRequest = onAccept,
        title = { Text(prompt.formatTitle()) },
        text = {
            ScrollableDialogRegion(maxHeight = dialogBodyMaxHeight()) {
                RuleGuidanceText(prompt.formatMessage(guidanceMode))
            }
        },
        confirmButton = {
            TextActionButton(label = "OK", onClick = onAccept)
        },
        dismissButton = {
            TextActionButton(label = "Not yet", onClick = onNotYet)
        },
        widthProfile = DialogWidthProfile.COMPACT,
    )
}

/**
 * Render the active game's rules reference.
 *
 * @param state Current game state used to combine setup rules with live cap state.
 * @param activeGameOrientation Orientation used to arrange the rules reference.
 * @param onDismiss Callback closing the dialog.
 */
@Composable
internal fun RulesReferenceDialog(
    state: GameState,
    activeGameOrientation: ActiveGameOrientation,
    onDismiss: () -> Unit,
) {
    val items = state.rulesReferenceItems()
    ResponsiveAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Game rules") },
        text = {
            if (activeGameOrientation == ActiveGameOrientation.LANDSCAPE) {
                val splitIndex = minOf(items.size, maxOf(6, (items.size + 1) / 2))
                TwoColumnDialogRegion(
                    maxHeight = dialogBodyMaxHeight(),
                    showDivider = false,
                    leftContent = {
                        items.take(splitIndex).forEach { item ->
                            RulesReferenceRow(item)
                        }
                    },
                    rightContent = {
                        items.drop(splitIndex).forEach { item ->
                            RulesReferenceRow(item)
                        }
                    },
                    footer = null,
                )
            } else {
                ScrollableDialogRegion(
                    maxHeight = dialogBodyMaxHeight(),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items.forEach { item ->
                        RulesReferenceRow(item)
                    }
                }
            }
        },
        confirmButton = {
            TextActionButton(label = "OK", onClick = onDismiss)
        },
        widthProfile = DialogWidthProfile.WIDE,
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
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = item.value,
            style = MaterialTheme.typography.bodyLarge,
            color = if (item.heatAdjusted) ResetColor else Color.Unspecified,
            fontWeight = if (item.heatAdjusted) FontWeight.Bold else null,
            textAlign = TextAlign.End,
            maxLines = 1,
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
    ResponsiveAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(team.name) },
        text = {
            ScrollableDialogRegion(
                maxHeight = dialogBodyMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
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
        widthProfile = DialogWidthProfile.COMPACT,
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
            modifier = Modifier.padding(start = 16.dp),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

/// Return the number of nonblank lines in a setup-entered coach/captain field.
private fun String.nonblankLineCount(): Int {
    return lineSequence().count { it.isNotBlank() }
}

/// Render the standard app title bar in landscape.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LandscapeNavigationBar(
    onBackHome: () -> Unit,
    onHome: () -> Unit,
) {
    // When the screen is under height pressure due to small screen or large fonts, shrink
    // the top bar somewhat to provide more space for the main part of the screen.
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val pressureScale = (
        configuration.screenHeightDp / 360f /
            density.fontScale.coerceAtLeast(1f)
        ).coerceIn(0.7f, 1f)
    val barHeight = 40.dp * pressureScale
    val titleFontSize = MaterialTheme.typography.titleLarge.fontSize * pressureScale
    // This wrapper shrinks the home and back hit boxes so they don't keep the bar so tall.
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides barHeight) {
        CenterAlignedTopAppBar(
            modifier = Modifier.windowInsetsPadding(
                WindowInsets.displayCutout.only(WindowInsetsSides.Horizontal),
            ),
            title = {
                Text(
                    text = "UltiObserver",
                    fontSize = titleFontSize,
                )
            },
            navigationIcon = {
                TopBarBackButton(onClick = onBackHome)
            },
            actions = {
                TopBarHomeButton(onClick = onHome)
            },
            expandedHeight = barHeight,
        )
    }
}

/** Render the portrait status, field, and bottom actions for the active-game screen. */
@Composable
private fun PortraitActiveGameContent(
    state: GameState,
    settings: Settings,
    topDisplayedEnd: FieldEnd,
    now: Long,
    capStatus: CapStatus?,
    activeCountdown: ActiveCountdownDisplay?,
    capStatusMessage: String?,
    canStartPoint: Boolean,
    hasExpiredPullActions: Boolean,
    canReportOffenseSet: Boolean,
    locked: Boolean,
    maxHeight: Dp,
    onStateChange: (GameState) -> Unit,
    onGoal: (TeamId) -> Unit,
    onLockedChange: (Boolean) -> Unit,
    onRulesReference: () -> Unit,
    onWaterBreak: () -> Unit,
    onMoreActions: () -> Unit,
    onTimeout: (TeamId) -> Unit,
    onTimeViolation: (TeamId) -> Unit,
    onPullViolation: (TeamId) -> Unit,
    onCards: (TeamId) -> Unit,
    onTechnicalFoul: (TeamId) -> Unit,
    onTeamInfo: (TeamId) -> Unit,
    onUndo: (GameState) -> Unit,
) {
    val currentGenderRatio = state.currentGenderRatio()
    val genderRatioBadgeColor = if (currentGenderRatio == null) {
        null
    } else {
        Color(settings.genderRatioBadgeColorArgb(currentGenderRatio))
    }
    val metrics = portraitActiveGameLayoutMetrics(maxHeight)
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(metrics.pagePadding),
        verticalArrangement = Arrangement.spacedBy(metrics.sectionSpacing),
    ) {
        StatusLine(
            clockText = state.formatOfficialGameTime(now),
            capStatus = capStatus,
            now = now,
            allocatedHeight = metrics.statusLineHeight,
            modifier = Modifier.fillMaxWidth(),
            pushCapToEnd = true,
            onRulesReference = onRulesReference,
        )

        CountdownLine(
            countdown = activeCountdown,
            enabled = !locked,
            onAdjust = { seconds ->
                onStateChange(state.addTimeToCountdown(seconds))
            },
            waterBreakAction = if (state.canApplyWaterBreak()) {
                onWaterBreak
            } else {
                null
            },
            onTogglePaused = {
                onStateChange(
                    state.toggleCountdownPaused(System.currentTimeMillis())
                )
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
            statusMessage = capStatusMessage,
            height = metrics.countdownHeight,
        )

        PortraitFieldSketchCard(
            state = state,
            topDisplayedEnd = topDisplayedEnd,
            showAbbaRatioAsSequence = settings.showAbbaRatioAsSequence,
            genderRatioBadgeColor = genderRatioBadgeColor,
            interactionsEnabled = !locked,
            timeoutEnabled = state.canRequestTimeout(now),
            metrics = metrics.field,
            centerContent = {
                if (!locked && canReportOffenseSet) {
                    CenterActionButton(
                        label = "Offense is set",
                        minHeight = metrics.centerButtonMinHeight,
                        fontSize = metrics.centerButtonFontSize,
                        tag = "live-offense-set",
                        onClick = {
                            onStateChange(
                                state.reportOffenseSet(System.currentTimeMillis())
                            )
                        },
                    )
                } else if (!locked && canStartPoint) {
                    CenterActionButton(
                        label = "Start point",
                        minHeight = metrics.centerButtonMinHeight,
                        fontSize = metrics.centerButtonFontSize,
                        onClick = {
                            if (settings.automaticallyLockLivePoint) {
                                onLockedChange(true)
                            }
                            onStateChange(
                                state.beginLivePoint(System.currentTimeMillis())
                            )
                        },
                    )
                } else if (
                    !locked &&
                    state.phase == GamePhase.LIVE_POINT &&
                    state.countdown != null
                ) {
                    CenterActionButton(
                        label = "Continue point",
                        minHeight = metrics.centerButtonMinHeight,
                        fontSize = metrics.centerButtonFontSize,
                        onClick = {
                            if (settings.automaticallyLockLivePoint) {
                                onLockedChange(true)
                            }
                            onStateChange(state.continueLivePoint())
                        },
                    )
                }
            },
            centerOverlayContent = {
                if (locked) {
                    FieldUnlockControl(
                        onUnlock = { onLockedChange(false) },
                        modifier = Modifier,
                    )
                }
            },
            onLock = {
                onLockedChange(true)
            },
            onGoal = onGoal,
            onTimeout = onTimeout,
            onTimeViolation = onTimeViolation,
            onPullViolation = onPullViolation,
            onCards = onCards,
            onTechnicalFoul = onTechnicalFoul,
            onTeamInfo = onTeamInfo,
        )

        NavigationButton(
            label = "More actions",
            fullWidth = true,
            height = metrics.bottomActionHeight,
            enabled = !locked,
            colors = neutralOutlinedButtonColors(DarkNeutralColor),
            borderColor = MaterialTheme.colorScheme.outline,
            onClick = onMoreActions,
        )

        UndoRedoBar(
            state = state,
            enabled = !locked,
            height = metrics.undoHeight,
            onUndo = onUndo,
            onRedo = onStateChange,
        )
    }
}

/**
 * Render the landscape status band and left/center/right field.
 */
@Composable
private fun LandscapeActiveGameContent(
    state: GameState,
    settings: Settings,
    leftDisplayedEnd: FieldEnd,
    activeGameLayout: ActiveGameOrientation,
    now: Long,
    capStatus: CapStatus?,
    activeCountdown: ActiveCountdownDisplay?,
    capStatusMessage: String?,
    canStartPoint: Boolean,
    hasExpiredPullActions: Boolean,
    canReportOffenseSet: Boolean,
    locked: Boolean,
    maxWidth: Dp,
    maxHeight: Dp,
    onStateChange: (GameState) -> Unit,
    onGoal: (TeamId) -> Unit,
    onLockedChange: (Boolean) -> Unit,
    onRulesReference: () -> Unit,
    onWaterBreak: () -> Unit,
    onMoreActions: () -> Unit,
    onTimeout: (TeamId) -> Unit,
    onTimeViolation: (TeamId) -> Unit,
    onPullViolation: (TeamId) -> Unit,
    onCards: (TeamId) -> Unit,
    onTechnicalFoul: (TeamId) -> Unit,
    onTeamInfo: (TeamId) -> Unit,
    onUndo: (GameState) -> Unit,
) {
    val fontScale = LocalDensity.current.fontScale
    val currentGenderRatio = state.currentGenderRatio()
    val genderRatioBadgeColor = if (currentGenderRatio == null) {
        null
    } else {
        Color(settings.genderRatioBadgeColorArgb(currentGenderRatio))
    }
    val metrics = landscapeActiveGameLayoutMetrics(
        contentWidth = maxWidth,
        contentHeight = maxHeight,
        fontScale = fontScale,
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(
                horizontal = metrics.horizontalPadding,
                vertical = metrics.verticalPadding,
            ),
        verticalArrangement = Arrangement.spacedBy(metrics.sectionSpacing),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(metrics.topRowHeight),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusLine(
                clockText = state.formatOfficialGameTime(now),
                capStatus = capStatus,
                now = now,
                allocatedHeight = metrics.topRowHeight,
                modifier = Modifier.weight(1f),
                pushCapToEnd = false,
                onRulesReference = onRulesReference,
            )
            CountdownLine(
                countdown = activeCountdown,
                enabled = !locked,
                onAdjust = { seconds ->
                    onStateChange(state.addTimeToCountdown(seconds))
                },
                waterBreakAction = if (state.canApplyWaterBreak()) {
                    onWaterBreak
                } else {
                    null
                },
                onTogglePaused = {
                    onStateChange(
                        state.toggleCountdownPaused(System.currentTimeMillis())
                    )
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
                misconductCountdownAction = if (
                    state.pendingMisconductCountdown && !locked
                ) {
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
                statusMessage = capStatusMessage,
                height = metrics.topRowHeight,
                modifier = Modifier.weight(1f),
            )
        }

        LandscapeFieldSketchCard(
            state = state,
            leftDisplayedEnd = leftDisplayedEnd,
            activeGameLayout = activeGameLayout,
            showAbbaRatioAsSequence = settings.showAbbaRatioAsSequence,
            genderRatioBadgeColor = genderRatioBadgeColor,
            interactionsEnabled = !locked,
            timeoutEnabled = state.canRequestTimeout(now),
            metrics = metrics.field,
            centerButtonFontSize = metrics.centerButtonFontSize,
            centerContent = {
                if (!locked && canReportOffenseSet) {
                    CenterActionButton(
                        label = "Offense is set",
                        minHeight = metrics.centerButtonMinHeight,
                        fontSize = metrics.centerButtonFontSize,
                        tag = "live-offense-set",
                        onClick = {
                            onStateChange(
                                state.reportOffenseSet(System.currentTimeMillis())
                            )
                        },
                    )
                } else if (!locked && canStartPoint) {
                    CenterActionButton(
                        label = "Start point",
                        minHeight = metrics.centerButtonMinHeight,
                        fontSize = metrics.centerButtonFontSize,
                        onClick = {
                            if (settings.automaticallyLockLivePoint) {
                                onLockedChange(true)
                            }
                            onStateChange(
                                state.beginLivePoint(System.currentTimeMillis())
                            )
                        },
                    )
                } else if (
                    !locked &&
                    state.phase == GamePhase.LIVE_POINT &&
                    state.countdown != null
                ) {
                    CenterActionButton(
                        label = "Continue point",
                        minHeight = metrics.centerButtonMinHeight,
                        fontSize = metrics.centerButtonFontSize,
                        onClick = {
                            if (settings.automaticallyLockLivePoint) {
                                onLockedChange(true)
                            }
                            onStateChange(state.continueLivePoint())
                        },
                    )
                }
            },
            centerOverlayContent = {
                if (locked) {
                    FieldUnlockControl(
                        onUnlock = {
                            onLockedChange(false)
                        },
                        modifier = Modifier.widthIn(max = 320.dp),
                    )
                }
            },
            onLock = {
                onLockedChange(true)
            },
            onGoal = onGoal,
            onTimeout = onTimeout,
            onTimeViolation = onTimeViolation,
            onPullViolation = onPullViolation,
            onCards = onCards,
            onTechnicalFoul = onTechnicalFoul,
            onTeamInfo = onTeamInfo,
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(metrics.bottomBarHeight),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterStart,
            ) {
                NavigationButton(
                    label = "More actions",
                    fullWidth = true,
                    height = metrics.bottomBarHeight,
                    enabled = !locked,
                    colors = neutralOutlinedButtonColors(DarkNeutralColor),
                    borderColor = MaterialTheme.colorScheme.outline,
                    onClick = onMoreActions,
                )
            }
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.CenterEnd,
            ) {
                UndoRedoBar(
                    state = state,
                    enabled = !locked,
                    height = metrics.bottomBarHeight,
                    onUndo = onUndo,
                    onRedo = onStateChange,
                )
            }
        }
    }
}

/// Render the primary center-field action with wrapping when its available width is narrow.
@Composable
private fun CenterActionButton(
    label: String,
    minHeight: Dp,
    fontSize: androidx.compose.ui.unit.TextUnit,
    tag: String? = null,
    onClick: () -> Unit,
) {
    BigActionButton(
        label = label,
        minHeight = minHeight,
        containerColor = FieldNeutralButtonColor,
        contentColor = Color.Black,
        fontSize = fontSize,
        textMaxLines = 2,
        softWrap = true,
        tag = tag,
        onClick = onClick,
    )
}

/// Responsive measurements for the complete landscape active-game surface.
private data class LandscapeActiveGameLayoutMetrics(
    val horizontalPadding: Dp,
    val verticalPadding: Dp,
    val sectionSpacing: Dp,
    val topRowHeight: Dp,
    val bottomBarHeight: Dp,
    val centerButtonMinHeight: Dp,
    val centerButtonFontSize: androidx.compose.ui.unit.TextUnit,
    val field: LandscapeFieldLayoutMetrics,
)

/**
 * Derive landscape active-game measurements from the current app window.
 */
private fun landscapeActiveGameLayoutMetrics(
    contentWidth: Dp,
    contentHeight: Dp,
    fontScale: Float,
): LandscapeActiveGameLayoutMetrics {
    val horizontalPadding = (contentWidth.value * 0.012f).dp.coerceIn(6.dp, 12.dp)
    val verticalPadding = (contentHeight.value * 0.014f).dp.coerceIn(4.dp, 8.dp)
    val sectionSpacing = 4.dp
    val topRowHeight = (
        contentHeight.value * 0.16f +
            (fontScale - 1f).coerceAtLeast(0f) * 80f
        )
        .dp
        .coerceIn(44.dp, 60.dp)
    val bottomBarHeight = 34.dp
    val fieldHeight = (
        contentHeight.value -
            verticalPadding.value * 2f -
            sectionSpacing.value * 2f -
            topRowHeight.value -
            bottomBarHeight.value
        )
        .coerceAtLeast(0f)
        .dp
    return LandscapeActiveGameLayoutMetrics(
        horizontalPadding = horizontalPadding,
        verticalPadding = verticalPadding,
        sectionSpacing = sectionSpacing,
        topRowHeight = topRowHeight,
        bottomBarHeight = bottomBarHeight,
        centerButtonMinHeight = 48.dp,
        centerButtonFontSize = (fieldHeight.value * 0.065f).coerceIn(14f, 17f).sp,
        field = LandscapeFieldLayoutMetrics.fromFieldHeight(fieldHeight),
    )
}

/**
 * Responsive portrait active-game measurements derived from the available height.
 *
 * @param field The nested field-specific layout metrics.
 */
private data class PortraitActiveGameLayoutMetrics(
    val pagePadding: Dp,
    val sectionSpacing: Dp,
    val statusLineHeight: Dp,
    val countdownHeight: Dp,
    val bottomActionHeight: Dp,
    val undoHeight: Dp,
    val centerButtonMinHeight: Dp,
    val centerButtonFontSize: androidx.compose.ui.unit.TextUnit,
    val field: PortraitFieldLayoutMetrics,
)

/**
 * Derive the portrait active-game screen's responsive layout metrics from available height.
 *
 * @param contentHeight The height inside the scaffold content area.
 */
private fun portraitActiveGameLayoutMetrics(contentHeight: Dp): PortraitActiveGameLayoutMetrics {
    val pagePadding = (contentHeight.value * 0.014f).dp.coerceIn(8.dp, 16.dp)
    val sectionSpacing = (contentHeight.value * 0.011f).dp.coerceIn(6.dp, 12.dp)
    val statusLineHeight = (contentHeight.value * 0.075f).dp.coerceIn(42.dp, 52.dp)
    val countdownHeight = (contentHeight.value * 0.095f).dp.coerceIn(52.dp, 64.dp)
    val bottomActionHeight = 34.dp
    val undoHeight = 34.dp
    val fieldHeight = (
        contentHeight.value -
            pagePadding.value * 2f -
            sectionSpacing.value * 4f -
            statusLineHeight.value -
            countdownHeight.value -
            bottomActionHeight.value -
            undoHeight.value
        )
        .coerceAtLeast(0f)
        .dp
    return PortraitActiveGameLayoutMetrics(
        pagePadding = pagePadding,
        sectionSpacing = sectionSpacing,
        statusLineHeight = statusLineHeight,
        countdownHeight = countdownHeight,
        bottomActionHeight = bottomActionHeight,
        undoHeight = undoHeight,
        centerButtonMinHeight = (fieldHeight.value * 0.11f).dp.coerceIn(38.dp, 48.dp),
        centerButtonFontSize = (fieldHeight.value * 0.04f).coerceIn(14f, 16f).sp,
        field = PortraitFieldLayoutMetrics.fromFieldHeight(fieldHeight),
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
                onClick = {
                    onRedo(state.redoLastAction())
                },
            )
        }
    }
}
