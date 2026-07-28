package rmjarvis.ultiobserver

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp

private enum class MoreActionsChildDialog {
    ADJUST_SCORE,
    ADJUST_TIMEOUTS,
    ADJUST_CARDS,
    ADJUST_PULL_VIOLATIONS,
    CHANGE_PULL_PROMPTS,
    SET_HEAT_LEVEL,
}

/**
 * Render the menu content for manual corrections and less-common game actions.
 *
 * @param state The current live game state.
 * @param now The current epoch millis for event-log timestamps.
 * @param guidanceMode Amount and duration of rule guidance shown during games.
 * @param onUpdateGameSetup Callback reopening setup for the current game.
 * @param onShowEventLog Callback opening the current game's event log.
 * @param onShowGameSummary Callback opening the current game summary.
 * @param onHeatRulesChange Callback applying edited live heat/AQI rules.
 * @param onAction Callback receiving an updated live game state after a model action.
 * @param onStateUpdate Callback receiving an updated live game state without closing More actions.
 */
@Composable
internal fun MoreActionsContent(
    state: GameState,
    now: Long,
    guidanceMode: RuleGuidanceMode,
    onUpdateGameSetup: () -> Unit,
    onShowEventLog: () -> Unit,
    onShowGameSummary: () -> Unit,
    onHeatRulesChange: (GameRules) -> Unit,
    onAction: (GameState) -> Unit,
    onStateUpdate: (GameState) -> Unit,
) {
    var childDialog by remember { mutableStateOf<MoreActionsChildDialog?>(null) }

    if (childDialog == MoreActionsChildDialog.ADJUST_SCORE) {
        AdjustScoreDialog(
            state = state,
            onDismiss = { childDialog = null },
            onConfirm = { teamOneScore, teamTwoScore ->
                onAction(state.adjustScore(teamOneScore, teamTwoScore, now))
                childDialog = null
            },
        )
    } else if (childDialog == MoreActionsChildDialog.ADJUST_TIMEOUTS) {
        AdjustTimeoutsDialog(
            state = state,
            onDismiss = { childDialog = null },
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
                childDialog = null
            },
        )
    } else if (childDialog == MoreActionsChildDialog.ADJUST_CARDS) {
        AdjustCardsDialog(
            state = state,
            now = now,
            guidanceMode = guidanceMode,
            onDismiss = { childDialog = null },
            onConfirm = { updatedState ->
                onAction(updatedState)
                childDialog = null
            },
            onStateUpdate = onStateUpdate,
        )
    } else if (childDialog == MoreActionsChildDialog.ADJUST_PULL_VIOLATIONS) {
        AdjustPullViolationsDialog(
            state = state,
            onDismiss = { childDialog = null },
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
                childDialog = null
            },
        )
    } else if (childDialog == MoreActionsChildDialog.CHANGE_PULL_PROMPTS) {
        ChangePullPromptsDialog(
            state = state,
            onDismiss = { childDialog = null },
            onConfirm = { target ->
                onAction(state.withPullPromptTarget(target))
                childDialog = null
            },
        )
    } else if (childDialog == MoreActionsChildDialog.SET_HEAT_LEVEL) {
        SetHeatLevelDialog(
            rules = state.rules,
            onDismiss = { childDialog = null },
            onConfirm = { rules ->
                onHeatRulesChange(rules)
                childDialog = null
            },
        )
    } else {
        ScrollableDialogRegion(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MenuButton(
                        label = "Update game setup",
                        onClick = onUpdateGameSetup,
                    )
                    MenuButton(
                        label = "Change pull prompts",
                        onClick = {
                            childDialog = MoreActionsChildDialog.CHANGE_PULL_PROMPTS
                        },
                    )
                    MenuButton(
                        label = "Flip field display",
                        onClick = {
                            onAction(state.flipFieldDisplay())
                        },
                    )
                    MenuButton(
                        label = "Swap pulling team",
                        onClick = {
                            onAction(state.swapPullingTeam())
                        },
                    )
                    MenuButton(
                        label = "Adjust score",
                        onClick = { childDialog = MoreActionsChildDialog.ADJUST_SCORE },
                    )
                    MenuButton(
                        label = "Adjust timeouts",
                        tag = "more-actions-adjust-timeouts",
                        onClick = { childDialog = MoreActionsChildDialog.ADJUST_TIMEOUTS },
                    )
                    MenuButton(
                        label = "Adjust pull violations",
                        onClick = { childDialog = MoreActionsChildDialog.ADJUST_PULL_VIOLATIONS },
                    )
                    MenuButton(
                        label = "Adjust cards / techs",
                        onClick = { childDialog = MoreActionsChildDialog.ADJUST_CARDS },
                    )
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    MenuButton(
                        label = "Set heat/AQI level",
                        onClick = {
                            childDialog = MoreActionsChildDialog.SET_HEAT_LEVEL
                        },
                    )
                    MenuButton(
                        label = "Game summary",
                        onClick = onShowGameSummary,
                    )
                    MenuButton(
                        label = "Event log",
                        onClick = onShowEventLog,
                    )
                    if (state.halfCapRelevant(state.teamOne.score, state.teamTwo.score)) {
                        MenuButton(
                            label = "Apply half cap now",
                            onClick = {
                                onAction(
                                    state.makeCapNow(CapType.HALF, System.currentTimeMillis())
                                )
                            },
                        )
                    }
                    if (!state.halftimeTaken && state.phase == GamePhase.BETWEEN_POINTS) {
                        MenuButton(
                            label = "Start halftime",
                            onClick = {
                                onAction(state.startHalftimeNow(System.currentTimeMillis()))
                            },
                        )
                    }
                    if (state.softCapRelevant()) {
                        MenuButton(
                            label = "Apply soft cap now",
                            onClick = {
                                onAction(
                                    state.makeCapNow(CapType.SOFT, System.currentTimeMillis())
                                )
                            },
                        )
                    }
                    if (state.hardCapRelevant()) {
                        MenuButton(
                            label = "Apply hard cap now",
                            onClick = {
                                onAction(
                                    state.makeCapNow(CapType.HARD, System.currentTimeMillis())
                                )
                            },
                        )
                    }
                    MenuButton(
                        label = "End game",
                        onClick = {
                            onAction(state.endGameNow(System.currentTimeMillis()))
                        },
                    )
                }
            }
        }
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

    AlertDialog(
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
 * Render the pull-prompt target editor reachable during a live game.
 *
 * @param state The live game whose pull-prompt target is being edited.
 * @param onDismiss Callback closing the dialog without changing prompts.
 * @param onConfirm Callback receiving the selected pull-prompt target.
 */
@Composable
private fun ChangePullPromptsDialog(
    state: GameState,
    onDismiss: () -> Unit,
    onConfirm: (PullPromptTarget) -> Unit,
) {
    var selected by remember { mutableStateOf(state.pullPromptTarget) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Change pull prompts") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(
                    text = "For which end do you want timing prompts related to the pull?",
                    fontWeight = FontWeight.SemiBold,
                )
                PullPromptTargetChoiceRow(
                    selected = selected,
                    nearLabel = state.fieldEndDisplayName(FieldEnd.NEAR),
                    farLabel = state.fieldEndDisplayName(FieldEnd.FAR),
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
 * @param state The current live game state whose score is being edited.
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

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Adjust score") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
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
    )
}
