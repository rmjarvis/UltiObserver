package rmjarvis.ultiobserver

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

internal enum class MoreActionsChild {
    ADJUST_SCORE,
    ADJUST_TIMEOUTS,
    ADJUST_CARDS,
    ADJUST_PULL_VIOLATIONS,
    CHANGE_PULL_PROMPTS,
    SET_HEAT_LEVEL,
}

internal enum class MoreActionsCategory(val title: String) {
    SETUP_CHANGES("Setup changes"),
    GAME_DETAILS("Game details"),
    FIELD_AND_PULL("Field and pull controls"),
    CORRECTIONS("Corrections"),
    MANUAL_TRANSITIONS("Manual game transitions"),
}

/// One action displayed within a grouped More actions category.
private data class MoreActionsItem(
    val label: String,
    val tag: String?,
    val onClick: () -> Unit,
)

/// Render a More actions category header using the supplied disclosure indicator and sizing.
@Composable
private fun MoreActionsCardHeader(
    modifier: Modifier,
    title: String,
    textStyle: TextStyle,
    indicator: ImageVector?,
    indicatorDescription: String?,
    indicatorSize: Dp,
    contentPadding: PaddingValues,
    onClick: () -> Unit,
) {
    Row(
        modifier = modifier
            .background(SecondaryColor)
            .clickable(onClick = onClick)
            .padding(contentPadding),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            modifier = Modifier.weight(1f),
            style = textStyle,
            fontWeight = FontWeight.SemiBold,
            color = OnSecondaryColor,
        )
        if (indicator != null) {
            Icon(
                imageVector = indicator,
                contentDescription = indicatorDescription,
                modifier = Modifier.size(indicatorSize),
                tint = OnSecondaryColor,
            )
        }
    }
}

/// Render the actions belonging to a More actions category.
@Composable
private fun MoreActionsCardContents(
    actions: List<MoreActionsItem>,
    rowSizeModifier: Modifier,
    textStyle: TextStyle,
    contentPadding: PaddingValues,
) {
    actions.forEach { action ->
        var actionModifier = Modifier
            .fillMaxWidth()
            .then(rowSizeModifier)
            .clickable(onClick = action.onClick)
            .padding(contentPadding)
        if (action.tag != null) {
            actionModifier = actionModifier.testTag(action.tag)
        }
        Box(
            modifier = actionModifier,
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                text = action.label,
                style = textStyle,
            )
        }
    }
}

/// Render independently scrollable landscape categories and selected actions.
@Composable
private fun LandscapeMoreActionsRegion(
    modifier: Modifier,
    actionsByCategory: Map<MoreActionsCategory, List<MoreActionsItem>>,
    selectedCategory: MoreActionsCategory,
    onCategorySelected: (MoreActionsCategory) -> Unit,
) {
    val categories = MoreActionsCategory.entries
    val density = LocalDensity.current
    var leftViewportHeightPx by remember { mutableIntStateOf(0) }
    var leftViewportTopPx by remember { mutableIntStateOf(0) }
    var measuredCategory by remember { mutableStateOf<MoreActionsCategory?>(null) }
    var selectedHeaderTopPx by remember { mutableIntStateOf(0) }
    var selectedHeaderHeightPx by remember { mutableIntStateOf(0) }
    var alignedCategory by remember { mutableStateOf<MoreActionsCategory?>(null) }
    var selectedHeaderCenterPx by remember { mutableIntStateOf(0) }
    var actionCardHeightPx by remember { mutableIntStateOf(0) }
    LaunchedEffect(
        selectedCategory,
        measuredCategory,
        selectedHeaderTopPx,
        selectedHeaderHeightPx,
        leftViewportTopPx,
        leftViewportHeightPx,
    ) {
        if (
            alignedCategory != selectedCategory &&
            measuredCategory == selectedCategory
        ) {
            selectedHeaderCenterPx =
                selectedHeaderTopPx - leftViewportTopPx + selectedHeaderHeightPx / 2
            alignedCategory = selectedCategory
        }
    }
    val actionCardTopPx = if (
        alignedCategory == selectedCategory && actionCardHeightPx < leftViewportHeightPx
    ) {
        (selectedHeaderCenterPx - actionCardHeightPx / 2).coerceIn(
            0,
            leftViewportHeightPx - actionCardHeightPx,
        )
    } else {
        0
    }
    val actionCardTop = with(density) { actionCardTopPx.toDp() }
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        ScrollableDialogRegion(
            modifier = Modifier
                .weight(1f)
                .onGloballyPositioned { coordinates ->
                    leftViewportTopPx = coordinates.positionInRoot().y.roundToInt()
                    leftViewportHeightPx = coordinates.size.height
                },
            maxHeight = dialogBodyMaxHeight(),
            verticalArrangement = Arrangement.Top,
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                categories.forEach { category ->
                    var cardModifier = Modifier.fillMaxWidth()
                    if (category == selectedCategory) {
                        cardModifier = cardModifier.onGloballyPositioned { coordinates ->
                            measuredCategory = category
                            selectedHeaderTopPx = coordinates.positionInRoot().y.roundToInt()
                            selectedHeaderHeightPx = coordinates.size.height
                        }
                    }
                    Card(
                        modifier = cardModifier,
                        border = BorderStroke(
                            1.dp,
                            MaterialTheme.colorScheme.outlineVariant,
                        ),
                    ) {
                        MoreActionsCardHeader(
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = 40.dp),
                            title = category.title,
                            textStyle = MaterialTheme.typography.labelMedium,
                            indicator = if (category == selectedCategory) {
                                Icons.AutoMirrored.Filled.KeyboardArrowRight
                            } else {
                                null
                            },
                            indicatorDescription = if (category == selectedCategory) {
                                "Selected"
                            } else {
                                null
                            },
                            indicatorSize = 24.dp,
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            onClick = { onCategorySelected(category) },
                        )
                    }
                }
            }
        }
        ScrollableDialogRegion(
            modifier = Modifier.weight(1f),
            maxHeight = dialogBodyMaxHeight(),
            verticalArrangement = Arrangement.Top,
        ) {
            Spacer(modifier = Modifier.height(actionCardTop))
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { actionCardHeightPx = it.height },
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                MoreActionsCardContents(
                    actions = actionsByCategory.getValue(selectedCategory),
                    rowSizeModifier = Modifier.defaultMinSize(minHeight = 44.dp),
                    textStyle = MaterialTheme.typography.bodyLarge,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                )
            }
        }
    }
}

/// Render the portrait More actions accordion.
@Composable
private fun PortraitMoreActionsRegion(
    modifier: Modifier,
    actionsByCategory: Map<MoreActionsCategory, List<MoreActionsItem>>,
    selectedCategory: MoreActionsCategory,
    onCategorySelected: (MoreActionsCategory) -> Unit,
) {
    ScrollableDialogRegion(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MoreActionsCategory.entries.forEach { category ->
            val expanded = selectedCategory == category
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    contentColor = MaterialTheme.colorScheme.onSurface,
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
            ) {
                MoreActionsCardHeader(
                    modifier = Modifier.fillMaxWidth(),
                    title = category.title,
                    textStyle = MaterialTheme.typography.labelMedium,
                    indicator = if (expanded) {
                        Icons.Filled.KeyboardArrowDown
                    } else {
                        Icons.AutoMirrored.Filled.KeyboardArrowRight
                    },
                    indicatorDescription = if (expanded) "Expanded" else "Collapsed",
                    indicatorSize = 24.dp,
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    onClick = { onCategorySelected(category) },
                )
                if (expanded) {
                    MoreActionsCardContents(
                        actions = actionsByCategory.getValue(category),
                        rowSizeModifier = Modifier.defaultMinSize(minHeight = 44.dp),
                        textStyle = MaterialTheme.typography.bodyLarge,
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 10.dp),
                    )
                }
            }
        }
    }
}

/**
 * Render the dialog selected from More actions.
 *
 * @param child The selected dialog.
 * @param state The current game state.
 * @param now The current epoch millis for correction events.
 * @param activeGameOrientation Orientation used to arrange the dialog.
 * @param activeGameLayout Layout used to label the field ends.
 * @param guidanceMode Amount and duration of rule guidance shown during games.
 * @param onDismiss Callback returning to More actions without applying a change.
 * @param onHeatRulesChange Callback applying edited heat/AQI rules.
 * @param onAction Callback applying a completed action and closing More actions.
 * @param onStateUpdate Callback applying an intermediate state change without closing the dialog.
 */
@Composable
internal fun MoreActionsChildDialog(
    child: MoreActionsChild,
    state: GameState,
    now: Long,
    activeGameOrientation: ActiveGameOrientation,
    activeGameLayout: ActiveGameOrientation,
    guidanceMode: RuleGuidanceMode,
    onDismiss: () -> Unit,
    onHeatRulesChange: (GameRules) -> Unit,
    onAction: (GameState) -> Unit,
    onStateUpdate: (GameState) -> Unit,
) {
    // No else branch: every MoreActionsChild value is handled.
    when (child) {
        MoreActionsChild.ADJUST_SCORE -> AdjustScoreDialog(
            state = state,
            onDismiss = onDismiss,
            onConfirm = { teamOneScore, teamTwoScore ->
                onAction(state.adjustScore(teamOneScore, teamTwoScore, now))
            },
        )
        MoreActionsChild.ADJUST_TIMEOUTS -> AdjustTimeoutsDialog(
            state = state,
            onDismiss = onDismiss,
            onConfirm = { teamOneCurrent, teamTwoCurrent, teamOneFirstHalf, teamTwoFirstHalf ->
                onAction(
                    state.adjustTimeouts(
                        teamOneCurrent,
                        teamTwoCurrent,
                        teamOneFirstHalf,
                        teamTwoFirstHalf,
                        now,
                    )
                )
            },
        )
        MoreActionsChild.ADJUST_CARDS -> AdjustCardsDialog(
            state = state,
            now = now,
            guidanceMode = guidanceMode,
            isLandscape = activeGameOrientation == ActiveGameOrientation.LANDSCAPE,
            onDismiss = onDismiss,
            onConfirm = onAction,
            onStateUpdate = onStateUpdate,
        )
        MoreActionsChild.ADJUST_PULL_VIOLATIONS -> AdjustPullViolationsDialog(
            state = state,
            onDismiss = onDismiss,
            onConfirm = {
                teamOneOffsides,
                teamOneFalseStarts,
                teamOneMajorityPulls,
                teamOneTimeViolations,
                teamTwoOffsides,
                teamTwoFalseStarts,
                teamTwoMajorityPulls,
                teamTwoTimeViolations ->
                onAction(
                    state.adjustPullViolations(
                        teamOneOffsides,
                        teamOneFalseStarts,
                        teamOneMajorityPulls,
                        teamOneTimeViolations,
                        teamTwoOffsides,
                        teamTwoFalseStarts,
                        teamTwoMajorityPulls,
                        teamTwoTimeViolations,
                        now,
                    )
                )
            },
        )
        MoreActionsChild.CHANGE_PULL_PROMPTS -> ChangePullPromptsDialog(
            state = state,
            layout = activeGameLayout,
            onDismiss = onDismiss,
            onConfirm = { target ->
                onAction(state.withPullPromptTarget(target))
            },
        )
        MoreActionsChild.SET_HEAT_LEVEL -> SetHeatLevelDialog(
            rules = state.rules,
            onDismiss = onDismiss,
            onConfirm = onHeatRulesChange,
        )
    }
}

/**
 * Render the menu content for manual corrections and less-common game actions.
 *
 * @param state The current game state.
 * @param activeGameOrientation Orientation used to arrange the More actions region.
 * @param onUpdateGameSetup Callback reopening setup for the current game.
 * @param onShowEventLog Callback opening the current game's event log.
 * @param onShowGameSummary Callback opening the current game summary.
 * @param onOpenChild Callback opening a dialog launched from More actions.
 * @param onAction Callback receiving an updated game state after a model action.
 * @param selectedCategory The category whose items are currently shown.
 * @param onCategorySelected Callback selecting a category.
 */
@Composable
internal fun MoreActionsContent(
    state: GameState,
    activeGameOrientation: ActiveGameOrientation,
    onUpdateGameSetup: () -> Unit,
    onShowEventLog: () -> Unit,
    onShowGameSummary: () -> Unit,
    onOpenChild: (MoreActionsChild) -> Unit,
    onAction: (GameState) -> Unit,
    selectedCategory: MoreActionsCategory,
    onCategorySelected: (MoreActionsCategory) -> Unit,
) {
    val setupActions = listOf(
        MoreActionsItem(
            label = "Set heat/AQI level",
            tag = null,
            onClick = {
                onOpenChild(MoreActionsChild.SET_HEAT_LEVEL)
            },
        ),
        MoreActionsItem(
            label = "Update game setup",
            tag = null,
            onClick = {
                onUpdateGameSetup()
            },
        ),
    )
    val detailActions = listOf(
        MoreActionsItem(
            label = "Event log",
            tag = null,
            onClick = {
                onShowEventLog()
            },
        ),
        MoreActionsItem(
            label = "Game summary",
            tag = null,
            onClick = {
                onShowGameSummary()
            },
        ),
    )
    val fieldActions = listOf(
        MoreActionsItem(
            label = "Flip field display",
            tag = null,
            onClick = {
                onAction(state.flipFieldDisplay())
            },
        ),
        MoreActionsItem(
            label = "Change pull prompts",
            tag = null,
            onClick = {
                onOpenChild(MoreActionsChild.CHANGE_PULL_PROMPTS)
            },
        ),
        MoreActionsItem(
            label = "Swap pulling team",
            tag = null,
            onClick = {
                onAction(state.swapPullingTeam())
            },
        ),
    )
    val correctionActions = listOf(
        MoreActionsItem(
            label = "Adjust cards / techs",
            tag = null,
            onClick = {
                onOpenChild(MoreActionsChild.ADJUST_CARDS)
            },
        ),
        MoreActionsItem(
            label = "Adjust pull violations",
            tag = null,
            onClick = {
                onOpenChild(MoreActionsChild.ADJUST_PULL_VIOLATIONS)
            },
        ),
        MoreActionsItem(
            label = "Adjust timeouts",
            tag = "more-actions-adjust-timeouts",
            onClick = {
                onOpenChild(MoreActionsChild.ADJUST_TIMEOUTS)
            },
        ),
        MoreActionsItem(
            label = "Adjust score",
            tag = null,
            onClick = {
                onOpenChild(MoreActionsChild.ADJUST_SCORE)
            },
        ),
    )
    val transitionActions = buildList {
        if (state.halfCapRelevant()) {
            add(
                MoreActionsItem(
                    label = "Apply half cap now",
                    tag = null,
                    onClick = {
                        onAction(
                            state.makeCapNow(CapType.HALF, System.currentTimeMillis())
                        )
                    },
                )
            )
        }
        if (!state.halftimeTaken && state.phase == GamePhase.BETWEEN_POINTS) {
            add(
                MoreActionsItem(
                    label = "Start halftime",
                    tag = null,
                    onClick = {
                        onAction(state.startHalftimeNow(System.currentTimeMillis()))
                    },
                )
            )
        }
        if (state.softCapRelevant()) {
            add(
                MoreActionsItem(
                    label = "Apply soft cap now",
                    tag = null,
                    onClick = {
                        onAction(
                            state.makeCapNow(CapType.SOFT, System.currentTimeMillis())
                        )
                    },
                )
            )
        }
        if (state.hardCapRelevant()) {
            add(
                MoreActionsItem(
                    label = "Apply hard cap now",
                    tag = null,
                    onClick = {
                        onAction(
                            state.makeCapNow(CapType.HARD, System.currentTimeMillis())
                        )
                    },
                )
            )
        }
        add(
            MoreActionsItem(
                label = "End game",
                tag = null,
                onClick = {
                    onAction(state.endGameNow(System.currentTimeMillis()))
                },
            )
        )
    }
    val actionsByCategory = mapOf(
        MoreActionsCategory.SETUP_CHANGES to setupActions,
        MoreActionsCategory.GAME_DETAILS to detailActions,
        MoreActionsCategory.FIELD_AND_PULL to fieldActions,
        MoreActionsCategory.CORRECTIONS to correctionActions,
        MoreActionsCategory.MANUAL_TRANSITIONS to transitionActions,
    )
    if (activeGameOrientation == ActiveGameOrientation.LANDSCAPE) {
        LandscapeMoreActionsRegion(
            actionsByCategory = actionsByCategory,
            selectedCategory = selectedCategory,
            onCategorySelected = onCategorySelected,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        )
    } else {
        PortraitMoreActionsRegion(
            actionsByCategory = actionsByCategory,
            selectedCategory = selectedCategory,
            onCategorySelected = onCategorySelected,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
        )
    }
}

/**
 * Render the concise live heat-level editor, including the Level 3 suspension action
 * and the ability to switch to/from AQI.
 */
@Composable
private fun SetHeatLevelDialog(
    rules: GameRules,
    onDismiss: () -> Unit,
    onConfirm: (GameRules) -> Unit,
) {
    var useAirQualityGuidelines by remember {
        mutableStateOf(rules.useAirQualityGuidelines)
    }
    var selectedHeatLevel by remember { mutableStateOf(rules.heatLevel) }
    var minutesText by remember { mutableStateOf(rules.waterBreakMinutes.toString()) }
    val displayedRules = rules.copy(
        useAirQualityGuidelines = useAirQualityGuidelines,
        heatLevel = selectedHeatLevel,
        waterBreakMinutes = minutesText.toIntOrNull() ?: rules.waterBreakMinutes,
    )

    ResponsiveAlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                if (useAirQualityGuidelines) {
                    "Set AQI level"
                } else {
                    "Set heat level"
                }
            )
        },
        text = {
            ScrollableDialogRegion(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                HeatLevelChoiceRow(
                    selected = selectedHeatLevel,
                    includeLevelThree = true,
                    onSelected = { newLevel ->
                        if (newLevel != selectedHeatLevel) {
                            val standardRules = displayedRules.withHeatLevel(newLevel)
                            selectedHeatLevel = newLevel
                            minutesText = standardRules.waterBreakMinutes.toString()
                        }
                    },
                )
                Text(displayedRules.heatLevelSelectionDescription(selectedHeatLevel))
                if (
                    selectedHeatLevel != HeatLevel.NONE &&
                    selectedHeatLevel != HeatLevel.LEVEL_3
                ) {
                    TextEntry(
                        value = minutesText,
                        onValueChange = { minutesText = it.filter(Char::isDigit).take(2) },
                        labelText = "Water break minutes",
                        keyboardType = KeyboardType.Number,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "Use guidelines for air quality rather than heat?",
                        modifier = Modifier.weight(1f),
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = if (useAirQualityGuidelines) "Yes" else "No",
                            modifier = Modifier.testTag("air-quality-guidelines-value"),
                        )
                        Switch(
                            checked = useAirQualityGuidelines,
                            onCheckedChange = { useAirQuality ->
                                useAirQualityGuidelines = useAirQuality
                                minutesText = displayedRules
                                    .withAirQualityGuidelines(useAirQuality)
                                    .waterBreakMinutes
                                    .toString()
                            },
                            modifier = Modifier.testTag("air-quality-guidelines"),
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextActionButton(
                label = "Set",
                tag = "set-heat-level-confirm",
                onClick = {
                    onConfirm(
                        rules.copy(
                            useAirQualityGuidelines = useAirQualityGuidelines,
                            heatLevel = selectedHeatLevel,
                            waterBreakMinutes = minutesText.toIntOrNull()?.coerceAtLeast(0)
                                ?: rules.waterBreakMinutes,
                        )
                    )
                },
            )
        },
        dismissButton = {
            TextActionButton(label = "Cancel", onClick = onDismiss)
        },
    )
}

/**
 * Render the pull-prompt target editor reachable during an active game.
 *
 * @param state The game whose pull-prompt target is being edited.
 * @param layout Layout whose field-end language should be used.
 * @param onDismiss Callback closing the dialog without changing prompts.
 * @param onConfirm Callback receiving the selected pull-prompt target.
 */
@Composable
private fun ChangePullPromptsDialog(
    state: GameState,
    layout: ActiveGameOrientation,
    onDismiss: () -> Unit,
    onConfirm: (PullPromptTarget) -> Unit,
) {
    var selected by remember { mutableStateOf(state.pullPromptTarget) }

    ResponsiveAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change pull prompts") },
        text = {
            ScrollableDialogRegion(
                maxHeight = dialogBodyMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "For which end do you want timing prompts related to the pull?",
                    fontWeight = FontWeight.SemiBold,
                )
                PullPromptTargetChoiceRow(
                    selected = selected,
                    nearLabel = state.fieldEndName(FieldEnd.NEAR, layout),
                    farLabel = state.fieldEndName(FieldEnd.FAR, layout),
                    testTagPrefix = "more-actions-pull-prompts",
                    onSelected = { selected = it },
                )
            }
        },
        confirmButton = {
            TextActionButton(label = "Set", onClick = { onConfirm(selected) })
        },
        dismissButton = {
            TextActionButton(label = "Cancel", onClick = onDismiss)
        },
    )
}

/**
 * Render the manual score correction dialog from More actions.
 *
 * @param state The current game state whose score is being edited.
 * @param onDismiss Callback closing the dialog without changing the score.
 * @param onConfirm Callback receiving the corrected team-one and team-two scores.
 */
@Composable
private fun AdjustScoreDialog(
    state: GameState,
    onDismiss: () -> Unit,
    onConfirm: (Int, Int) -> Unit,
) {
    var teamOneScore by remember { mutableStateOf(state.teamOne.score) }
    var teamTwoScore by remember { mutableStateOf(state.teamTwo.score) }

    ResponsiveAlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adjust score") },
        text = {
            ScrollableDialogRegion(
                maxHeight = dialogBodyMaxHeight(),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                CorrectionCountRow(
                    label = state.teamOne.name,
                    value = teamOneScore,
                    emphasizedLabel = true,
                    onIncrement = { teamOneScore += 1 },
                    onDecrement = { teamOneScore = maxOf(0, teamOneScore - 1) },
                )
                CorrectionCountRow(
                    label = state.teamTwo.name,
                    value = teamTwoScore,
                    emphasizedLabel = true,
                    onIncrement = { teamTwoScore += 1 },
                    onDecrement = { teamTwoScore = maxOf(0, teamTwoScore - 1) },
                )
            }
        },
        confirmButton = {
            TextActionButton(label = "Set", onClick = { onConfirm(teamOneScore, teamTwoScore) })
        },
        dismissButton = {
            TextActionButton(label = "Cancel", onClick = onDismiss)
        },
        widthProfile = DialogWidthProfile.COMPACT,
    )
}
