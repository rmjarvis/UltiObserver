package rmjarvis.ultiobserver

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.LocalTextStyle
import androidx.wear.compose.material3.Text
import androidx.wear.compose.material3.TimeSource
import rmjarvis.ultiobserver.wearprotocol.WearCountdownAction
import rmjarvis.ultiobserver.wearprotocol.WearTimingControlsSnapshot
import rmjarvis.ultiobserver.ui.theme.UltiObserverTheme

/** One team as presented on the fixed left or right side of the watch. */
internal data class TeamDisplay(
    val name: String,
    val score: Int,
    val fieldEndName: String,
    val backgroundColor: Color,
    val contentColor: Color,
)

/** The direction of the pull arrow across the two fixed team regions. */
internal enum class PullDirection {
    LEFT_TO_RIGHT,
    RIGHT_TO_LEFT,
}

/** Optional mixed-game ratio badge shown on the team boundary. */
internal data class RatioBadgeDisplay(
    val label: String,
    val backgroundColor: Color,
    val contentColor: Color,
)

/** Phone-selected team and colored segments of the ratio-choice badge. */
internal data class RatioChooserDisplay(
    val team: TeamId,
    val men: RatioBadgeDisplay,
    val women: RatioBadgeDisplay,
    val description: String,
)

/**
 * Display-only snapshot for the watch's active-game main screen.
 *
 * The phone will eventually produce this snapshot from its authoritative game state. Keeping the
 * composable dependent on a compact display model lets the Wear UI be built and previewed before
 * the phone/watch transport exists.
 */
internal data class GameDisplay(
    val officialTime: String,
    val capStatus: String?,
    val countdownActions: List<WearCountdownAction>,
    val timingControls: WearTimingControlsSnapshot?,
    val countdownLabel: String,
    val countdownValue: String?,
    val nextCue: String?,
    val statusMessage: String?,
    val teamOne: TeamDisplay,
    val teamTwo: TeamDisplay,
    val pullDirection: PullDirection,
    val ratioBadge: RatioBadgeDisplay?,
    val ratioChooser: RatioChooserDisplay?,
    val connected: Boolean,
    val actionsAvailable: Boolean,
    val gameOver: Boolean,
    val undoDescription: String?,
)

/**
 * Render the active-game watch surface and route its three main actions.
 *
 * Team 1 always stays on the left and Team 2 always stays on the right. A disconnected snapshot
 * remains readable but disables every action and replaces the countdown area with
 * `Lost connection`.
 */
@Composable
internal fun GameScreen(
    display: GameDisplay,
    onTeamOne: () -> Unit,
    onTeamTwo: () -> Unit,
    timingControlsOpen: Boolean,
    onToggleTimingControls: () -> Unit,
    onCloseTimingControls: () -> Unit,
    countdownActionEnabled: Boolean,
    onCountdownAction: (WearCountdownAction) -> Unit,
    onRetry: () -> Unit,
    onUndo: () -> Unit,
) {
    val timeSource = remember(display.officialTime) {
        DisplayTimeSource(display.officialTime)
    }
    UltiObserverTheme {
        AppScaffold(
            timeText = {
                AppTimeText(timeSource)
            },
            containerColor = Color.Black,
            contentColor = Color.White,
        ) {
            GameContent(
                display = display,
                onTeamOne = onTeamOne,
                onTeamTwo = onTeamTwo,
                timingControlsOpen = timingControlsOpen,
                onToggleTimingControls = onToggleTimingControls,
                onCloseTimingControls = onCloseTimingControls,
                countdownActionEnabled = countdownActionEnabled,
                onCountdownAction = onCountdownAction,
                onRetry = onRetry,
                onUndo = onUndo,
            )
        }
    }
}

@Composable
private fun GameContent(
    display: GameDisplay,
    onTeamOne: () -> Unit,
    onTeamTwo: () -> Unit,
    timingControlsOpen: Boolean,
    onToggleTimingControls: () -> Unit,
    onCloseTimingControls: () -> Unit,
    countdownActionEnabled: Boolean,
    onCountdownAction: (WearCountdownAction) -> Unit,
    onRetry: () -> Unit,
    onUndo: () -> Unit,
) {

    BoxWithConstraints(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        val statusHeight = maxHeight * 0.48f
        val fieldHeight = maxHeight - statusHeight

        StatusRegion(
            display = display,
            countdownActionEnabled = countdownActionEnabled,
            onCountdownAction = onCountdownAction,
            onRetry = onRetry,
            onToggleTimingControls = onToggleTimingControls,
            modifier = Modifier
                .fillMaxWidth()
                .height(statusHeight)
                .align(Alignment.TopCenter),
        )
        if (timingControlsOpen && display.timingControls != null) {
            TimingControls(
                controls = display.timingControls,
                enabled = countdownActionEnabled && display.actionsAvailable && display.connected,
                onAction = onCountdownAction,
                onBack = onCloseTimingControls,
                modifier = Modifier.fillMaxWidth().height(fieldHeight).align(Alignment.BottomCenter),
            )
        } else {
            TeamField(
                display = display,
                onTeamOne = onTeamOne,
                onTeamTwo = onTeamTwo,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(fieldHeight)
                    .align(Alignment.BottomCenter),
            )
            if (display.connected && display.actionsAvailable && display.undoDescription != null) {
                UndoRegion(
                    description = display.undoDescription,
                    onUndo = onUndo,
                    modifier = Modifier.align(Alignment.BottomCenter),
                )
            }
        }
    }
}

@Composable
private fun StatusRegion(
    display: GameDisplay,
    countdownActionEnabled: Boolean,
    onCountdownAction: (WearCountdownAction) -> Unit,
    onRetry: () -> Unit,
    onToggleTimingControls: () -> Unit,
    modifier: Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val topPadding = maxHeight * 0.22f

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(top = topPadding, start = 16.dp, end = 16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = display.capStatus.orEmpty(),
                color = StatusTextColor,
                fontSize = 10.sp,
                fontWeight = FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Spacer(modifier = Modifier.height(5.dp))
            if (!display.connected) {
                Text(
                    text = "Lost connection",
                    color = ConnectionLostColor,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
                RetryLabel(onRetry)
            } else if (!display.actionsAvailable && !display.gameOver) {
                Text(
                    text = "Resume current game on phone to enable actions",
                    modifier = Modifier.padding(top = 13.dp),
                    color = StatusTextColor,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                )
            } else if (display.countdownActions.isNotEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().weight(1f).padding(bottom = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterVertically),
                ) {
                    display.countdownActions.forEach { action ->
                        CountdownActionButton(
                            label = action.label,
                            enabled = countdownActionEnabled,
                            onClick = {
                                onCountdownAction(action)
                            },
                            modifier = Modifier.padding(horizontal = 8.dp),
                        )
                    }
                }
            } else if (display.statusMessage != null) {
                Text(
                    text = display.statusMessage,
                    modifier = Modifier.padding(top = 5.dp),
                    color = Color.White,
                    fontSize = if (display.gameOver) 18.sp else 11.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    textAlign = TextAlign.Center,
                )
            } else if (display.countdownValue != null) {
                Column(
                    modifier = Modifier.fillMaxWidth().clickable(
                        role = Role.Button,
                        onClick = onToggleTimingControls,
                    ).semantics { contentDescription = "Countdown controls" },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    CountdownStatus(display.countdownLabel, display.countdownValue)
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = display.nextCue.orEmpty(),
                        color = StatusTextColor,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Normal,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun CountdownStatus(label: String, value: String) {
    Row(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Measure the unweighted countdown first; only its label yields space when necessary.
        BoxWithConstraints(modifier = Modifier.weight(1f, fill = false)) {
            val textMeasurer = rememberTextMeasurer()
            val labelStyle = LocalTextStyle.current.copy(
                fontSize = 18.sp,
                fontWeight = FontWeight.Normal,
            )
            val naturalWidth = textMeasurer.measure(
                text = label,
                style = labelStyle,
                maxLines = 1,
                softWrap = false,
            ).size.width
            val availableWidth = with(LocalDensity.current) { maxWidth.toPx() }
            val fontSize = if (naturalWidth > availableWidth) {
                labelStyle.fontSize * (availableWidth / naturalWidth)
            } else {
                labelStyle.fontSize
            }
            Text(
                text = label,
                style = labelStyle,
                fontSize = fontSize,
                maxLines = 1,
                softWrap = false,
            )
        }
        Text(
            text = value,
            fontSize = 27.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
        )
    }
}

@Composable
private fun TeamField(
    display: GameDisplay,
    onTeamOne: () -> Unit,
    onTeamTwo: () -> Unit,
    modifier: Modifier,
) {
    BoxWithConstraints(
        modifier = modifier.alpha(
            if (display.connected && (display.actionsAvailable || display.gameOver)) {
                1f
            } else {
                DisabledContentAlpha
            }
        ),
    ) {
        Row(modifier = Modifier.fillMaxSize()) {
            TeamRegion(
                team = display.teamOne,
                endLabelAlignment = Alignment.Start,
                enabled = display.connected && display.actionsAvailable,
                onClick = onTeamOne,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
            TeamRegion(
                team = display.teamTwo,
                endLabelAlignment = Alignment.End,
                enabled = display.connected && display.actionsAvailable,
                onClick = onTeamTwo,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(FieldDividerColor)
                .align(Alignment.TopCenter),
        )
        if (!display.gameOver) {
            val centerStackHeight = PullArrowHeight + CenterStackSpacing +
                if (display.ratioBadge == null && display.ratioChooser == null) 0.dp else RatioBadgeHeight
            val centerStackTopPadding =
                (maxHeight - VisibleUndoHeight - centerStackHeight) / 2f
            PullAndRatio(
                display = display,
                topPadding = centerStackTopPadding,
                modifier = Modifier.align(Alignment.TopCenter),
            )
            display.ratioChooser?.let { chooser ->
                Row(
                    modifier = Modifier.fillMaxWidth()
                        .padding(top = centerStackTopPadding + PullArrowHeight + CenterStackSpacing),
                ) {
                    Box(modifier = Modifier.weight(1f).padding(end = 4.dp)) {
                        if (chooser.team == TeamId.TEAM_ONE) {
                            RatioChooserBadge(chooser, Modifier.align(Alignment.CenterEnd))
                        }
                    }
                    Box(modifier = Modifier.weight(1f).padding(start = 4.dp)) {
                        if (chooser.team == TeamId.TEAM_TWO) {
                            RatioChooserBadge(chooser, Modifier.align(Alignment.CenterStart))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun TeamRegion(
    team: TeamDisplay,
    endLabelAlignment: Alignment.Horizontal,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier
            .background(team.backgroundColor)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(top = 10.dp, start = 10.dp, end = 10.dp, bottom = VisibleUndoHeight),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(
            text = team.name,
            modifier = Modifier.widthIn(max = 86.dp),
            color = team.contentColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(2.dp))
        BoxWithConstraints(
            modifier = Modifier.weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            val scoreSize = with(LocalDensity.current) { maxHeight.toSp() }.value.coerceAtMost(36f).sp
            Text(
                text = team.score.toString(),
                color = team.contentColor,
                fontSize = scoreSize,
                lineHeight = scoreSize,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
        Text(
            text = team.fieldEndName,
            modifier = Modifier.fillMaxWidth().padding(
                start = if (endLabelAlignment == Alignment.Start) 26.dp else 0.dp,
                end = if (endLabelAlignment == Alignment.End) 26.dp else 0.dp,
                top = 2.dp,
                bottom = 2.dp,
            ),
            color = team.contentColor,
            fontSize = 9.sp,
            lineHeight = 11.sp,
            textAlign = if (endLabelAlignment == Alignment.Start) TextAlign.Start else TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun PullAndRatio(
    display: GameDisplay,
    topPadding: androidx.compose.ui.unit.Dp,
    modifier: Modifier,
) {
    val directionDescription = when (display.pullDirection) {
        PullDirection.LEFT_TO_RIGHT ->
            "Pull direction from ${display.teamOne.name} toward ${display.teamTwo.name}"
        PullDirection.RIGHT_TO_LEFT ->
            "Pull direction from ${display.teamTwo.name} toward ${display.teamOne.name}"
    }
    Column(
        modifier = modifier.padding(top = topPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(CenterStackSpacing),
    ) {
        PullArrow(
            direction = display.pullDirection,
            description = directionDescription,
        )
        if (display.ratioBadge != null) {
            RatioBadge(display.ratioBadge)
        }
    }
}

@Composable
private fun PullArrow(
    direction: PullDirection,
    description: String,
) {
    Canvas(
        modifier = Modifier
            .size(width = 31.dp, height = PullArrowHeight)
            .semantics {
                contentDescription = description
            },
    ) {
        val travelsRight = direction == PullDirection.LEFT_TO_RIGHT
        val startX = if (travelsRight) 1.dp.toPx() else size.width - 1.dp.toPx()
        val endX = if (travelsRight) size.width - 1.dp.toPx() else 1.dp.toPx()
        val centerY = size.height / 2f
        val arrowLength = 6.dp.toPx()
        val arrowHeight = 5.dp.toPx()
        val arrowBaseX = if (travelsRight) endX - arrowLength else endX + arrowLength

        drawLine(
            color = FieldContentColor,
            start = androidx.compose.ui.geometry.Offset(startX, centerY),
            end = androidx.compose.ui.geometry.Offset(endX, centerY),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = FieldContentColor,
            start = androidx.compose.ui.geometry.Offset(arrowBaseX, centerY - arrowHeight),
            end = androidx.compose.ui.geometry.Offset(endX, centerY),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
        drawLine(
            color = FieldContentColor,
            start = androidx.compose.ui.geometry.Offset(arrowBaseX, centerY + arrowHeight),
            end = androidx.compose.ui.geometry.Offset(endX, centerY),
            strokeWidth = 2.dp.toPx(),
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun RatioBadge(badge: RatioBadgeDisplay) {
    Box(
        modifier = Modifier
            .height(RatioBadgeHeight)
            .clip(RoundedCornerShape(4.dp))
            .background(badge.backgroundColor)
            .border(
                BorderStroke(1.dp, badge.contentColor.copy(alpha = 0.7f)),
                RoundedCornerShape(4.dp),
            )
            .padding(horizontal = 5.dp, vertical = 1.dp)
            .semantics {
                contentDescription = "ABBA ratio ${badge.label}"
            },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = badge.label,
            color = badge.contentColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}

/** Display the two configured ratio colors inside a single informational badge. */
@Composable
private fun RatioChooserBadge(badge: RatioChooserDisplay, modifier: Modifier) {
    val shape = RoundedCornerShape(2.8.dp)
    Row(
        modifier = modifier.height(RatioBadgeHeight * 0.7f)
            .clip(shape)
            .border(1.dp, Color.White.copy(alpha = 0.7f), shape)
            .semantics { contentDescription = badge.description },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf(badge.men, badge.women).forEach { segment ->
            Box(
                modifier = Modifier.fillMaxHeight().background(segment.backgroundColor)
                    .padding(horizontal = 2.1.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = segment.label,
                    color = segment.contentColor,
                    fontSize = 8.4.sp,
                    lineHeight = 8.4.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun UndoRegion(
    description: String,
    onUndo: () -> Unit,
    modifier: Modifier,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(48.dp)
            .semantics {
                contentDescription = description
            }
            .clickable(
                role = Role.Button,
                onClick = onUndo,
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(VisibleUndoHeight)
                .background(UndoBackgroundColor),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "Undo",
                color = UndoContentColor,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
            )
        }
    }
}

private class DisplayTimeSource(private val time: String) : TimeSource {
    @Composable
    override fun currentTime(): String = time
}

private val StatusTextColor = Color(0xFFC8CDD2)
private val ConnectionLostColor = Color(0xFFFFB4AB)
private val FieldDividerColor = Color(0xB3101317)
private val FieldContentColor = Color(0xFF101317)
private val UndoBackgroundColor = Color(0xFF9E4B3E)
private val UndoContentColor = Color.White
private val VisibleUndoHeight = 30.dp
private val PullArrowHeight = 19.dp
private val CenterStackSpacing = 3.dp
private val RatioBadgeHeight = 19.dp
private const val DisabledContentAlpha = 0.55f

/** Replace the scores with countdown adjustments while keeping the timer visible above. */
@Composable
private fun TimingControls(
    controls: WearTimingControlsSnapshot,
    enabled: Boolean,
    onAction: (WearCountdownAction) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier.padding(top = 3.dp, bottom = 10.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            controls.adjustments.forEach { action ->
                TimingAdjustmentButton(action, enabled) {
                    onAction(action)
                }
            }
        }
        controls.pointAction?.let { action ->
            CountdownActionButton(
                label = action.label,
                enabled = enabled,
                onClick = {
                    onAction(action)
                },
                modifier = Modifier,
            )
        }
        Text(
            text = "Back",
            modifier = Modifier.clickable(role = Role.Button, onClick = onBack)
                .padding(horizontal = 16.dp, vertical = 7.dp),
            color = Color.White,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
        )
    }
}
