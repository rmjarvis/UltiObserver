package rmjarvis.ultiobserver

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Duration
import java.time.LocalTime

/**
 * Render the live-field unlock slider.
 *
 * @param onUnlock Callback invoked once the observer completes the required slide gesture.
 */
@Composable
internal fun FieldUnlockControl(
    onUnlock: () -> Unit,
) {
    SlideToConfirmControl(
        instructionText = "Slide right to unlock",
        trackText = "Unlock",
        testTag = "live-unlock-slider",
        onConfirmed = onUnlock,
        textColor = Color.Black,
        trackColor = Color(0x66FFFFFF),
        thumbColor = Color.White,
        borderColor = Color.Black,
    )
}

/**
 * Render a full-width confirmation slider that only activates when the drag starts on the left side.
 *
 * @param instructionText Instruction text shown above the slider.
 * @param trackText Text shown inside the slider track.
 * @param testTag Test tag attached to the draggable track.
 * @param onConfirmed Callback invoked after a successful slide.
 * @param modifier Optional layout modifier.
 * @param textColor Color used for instruction and track text.
 * @param trackColor Slider track color.
 * @param thumbColor Slider thumb color.
 * @param borderColor Slider border color.
 */
@Composable
internal fun SlideToConfirmControl(
    instructionText: String,
    trackText: String,
    testTag: String,
    onConfirmed: () -> Unit,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    trackColor: Color = MaterialTheme.colorScheme.surfaceVariant,
    thumbColor: Color = MaterialTheme.colorScheme.surface,
    borderColor: Color = MaterialTheme.colorScheme.outline,
) {
    var trackWidthPx by remember { mutableStateOf(0f) }
    var thumbOffsetPx by remember { mutableStateOf(0f) }
    var dragEnabled by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val thumbDiameter = 40.dp
    val thumbDiameterPx = with(density) { thumbDiameter.toPx() }

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            instructionText,
            style = MaterialTheme.typography.bodyMedium,
            color = textColor,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.SemiBold,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .testTag(testTag)
                .height(52.dp)
                .onSizeChanged { trackWidthPx = it.width.toFloat() }
                .background(trackColor, RoundedCornerShape(26.dp))
                .border(1.dp, borderColor, RoundedCornerShape(26.dp))
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
                                onConfirmed()
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
                },
        ) {
            Text(
                trackText,
                modifier = Modifier.align(Alignment.Center),
                color = textColor.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset { IntOffset(thumbOffsetPx.toInt(), 0) }
                    .padding(6.dp)
                    .size(thumbDiameter)
                    .background(thumbColor, RoundedCornerShape(20.dp))
                    .border(1.dp, borderColor, RoundedCornerShape(20.dp)),
            )
        }
    }
}

/**
 * Render the top status line showing the real clock and next relevant cap.
 *
 * @param currentTime The local clock time to display.
 * @param capStatus The next cap status, or null when all caps are passed or irrelevant.
 * @param height The reserved status-line height used for responsive live layout.
 */
@Composable
internal fun StatusLine(
    currentTime: LocalTime,
    capStatus: CapStatus?,
    height: Dp,
) {
    val clockFontSize = (height.value * 0.68f).coerceIn(28f, 36f).sp
    val capFontSize = (height.value * 0.42f).coerceIn(18f, 22f).sp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatClockTime(currentTime),
            style = MaterialTheme.typography.headlineMedium.copy(fontSize = clockFontSize),
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = capStatus?.let { "${it.label} ${formatDuration(it.remaining)}" } ?: "Caps passed",
            style = MaterialTheme.typography.bodyLarge.copy(fontSize = capFontSize),
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/// Responsive measurements used to draw the live field view.
internal data class FieldLayoutMetrics(
    val fieldHeight: Dp,
    val teamRowHeight: Dp,
    val centerHeight: Dp,
    val centerVerticalPadding: Dp,
    val teamRowPadding: Dp,
    val teamRowGap: Dp,
    val titleGap: Dp,
    val detailGap: Dp,
    val actionGap: Dp,
    val actionButtonHeight: Dp,
    val titleFontSize: androidx.compose.ui.unit.TextUnit,
    val titleLineHeight: androidx.compose.ui.unit.TextUnit,
    val detailFontSize: androidx.compose.ui.unit.TextUnit,
    val detailLineHeight: androidx.compose.ui.unit.TextUnit,
) {
    companion object {
        /**
         * Derive field layout metrics from the measured available field height.
         *
         * @param fieldHeight The height available for the full field diagram.
         */
        fun fromFieldHeight(fieldHeight: Dp): FieldLayoutMetrics {
            val centerHeight = (fieldHeight.value * 0.22f)
                .coerceIn(64f, 120f)
                .coerceAtMost(fieldHeight.value)
                .dp
            val teamRowHeight = ((fieldHeight.value - centerHeight.value) / 2f)
                .coerceAtLeast(0f)
                .dp
            val rowPadding = (teamRowHeight.value * 0.045f)
                .coerceIn(2f, 12f)
                .coerceAtMost(teamRowHeight.value / 2f)
                .dp
            val contentHeight = (teamRowHeight.value - rowPadding.value * 2f)
                .coerceAtLeast(0f)
                .dp
            val detailGap = (contentHeight.value * 0.01f).coerceIn(0f, 4f).dp
            val lineBudget = (contentHeight.value - detailGap.value * 4f).coerceAtLeast(0f)
            val detailLineHeightValue = (lineBudget / 5.35f).coerceIn(0f, 18f)
            val titleLineHeightValue = (detailLineHeightValue * 1.35f).coerceAtMost(24f)
            val actionButtonHeight = (contentHeight.value / 3.1f)
                .coerceIn(0f, 34f)
                .coerceAtMost(contentHeight.value / 3f)
                .dp
            val actionGap = ((contentHeight.value - 3f * actionButtonHeight.value) / 2f)
                .coerceIn(0f, 6f)
                .dp
            return FieldLayoutMetrics(
                fieldHeight = fieldHeight,
                teamRowHeight = teamRowHeight,
                centerHeight = centerHeight,
                centerVerticalPadding = (centerHeight.value * 0.05f).dp.coerceIn(4.dp, 12.dp),
                teamRowPadding = rowPadding,
                teamRowGap = (teamRowHeight.value * 0.045f).dp.coerceIn(5.dp, 12.dp),
                titleGap = (teamRowHeight.value * 0.035f).dp.coerceIn(4.dp, 10.dp),
                detailGap = detailGap,
                actionGap = actionGap,
                actionButtonHeight = actionButtonHeight,
                titleFontSize = (titleLineHeightValue - 3f).coerceAtLeast(1f).sp,
                titleLineHeight = titleLineHeightValue.sp,
                detailFontSize = (detailLineHeightValue - 2f).coerceAtLeast(1f).sp,
                detailLineHeight = detailLineHeightValue.sp,
            )
        }
    }
}

/**
 * Draw the field as top/bottom end zones plus a center strip for pull direction and controls.
 *
 * @param state The live game state to render.
 * @param interactionsEnabled Whether team action controls should be enabled.
 * @param showPullIndicator Whether the center strip should show pull direction.
 * @param metrics The precomputed field layout metrics.
 * @param centerContent The live action or unlock content rendered in the center strip.
 * @param onGoal Callback receiving the team that scored.
 * @param onTimeout Callback receiving the team requesting timeout.
 * @param onPullInfraction Callback receiving the team with a pull infraction.
 */
@Composable
internal fun FieldSketchCard(
    state: GameState,
    interactionsEnabled: Boolean,
    showPullIndicator: Boolean,
    metrics: FieldLayoutMetrics,
    centerContent: @Composable () -> Unit,
    onGoal: (TeamId) -> Unit,
    onTimeout: (TeamId) -> Unit,
    onPullInfraction: (TeamId) -> Unit,
) {
    // Translate the game's pulling orientation into fixed top/bottom screen slots.
    val topSlot = if (state.pullingFromEnd == FieldEnd.FAR) {
        state.pullingTeam
    } else {
        state.pullingTeam.flip()
    }
    val bottomSlot = topSlot.flip()
    val topTeam = state.teamFor(topSlot)
    val bottomTeam = state.teamFor(bottomSlot)
    val pullFrom = state.pullingFromEnd

    // Draw the top team row, center field area, and bottom team row in that order.
    Card(
        modifier = Modifier
            .height(metrics.fieldHeight)
            .testTag("live-field-diagram"),
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
                pullInfractionEnabled = state.canRecordPullInfraction(topSlot),
                metrics = metrics,
                onGoal = { onGoal(topSlot) },
                onTimeout = { onTimeout(topSlot) },
                onPullInfraction = { onPullInfraction(topSlot) },
            )
            // Center field strip with pull direction and the main central control.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(metrics.centerHeight)
                    .background(Color(0xFFA8D5A0))
                    .padding(horizontal = 16.dp, vertical = metrics.centerVerticalPadding),
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
                        centerContent()
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
                pullInfractionEnabled = state.canRecordPullInfraction(bottomSlot),
                metrics = metrics,
                onGoal = { onGoal(bottomSlot) },
                onTimeout = { onTimeout(bottomSlot) },
                onPullInfraction = { onPullInfraction(bottomSlot) },
            )
        }
    }
}

/**
 * Render one team row on the field, with score/state info and the main live actions.
 *
 * @param teamId The team represented by this end-zone row.
 * @param team The live team state to display.
 * @param cardPoints The derived team card-point total.
 * @param timeoutsRemaining The derived timeout count remaining in this half.
 * @param background The team-color background for the row.
 * @param interactionsEnabled Whether live action buttons should be enabled.
 * @param isPulling Whether this team is currently pulling.
 * @param pullInfractionEnabled Whether this team can still record its pull infraction for this pull.
 * @param metrics The measured layout metrics for compact or roomy phone heights.
 * @param onGoal Callback recording a goal for this team.
 * @param onTimeout Callback charging a timeout to this team.
 * @param onPullInfraction Callback recording this team's pull infraction.
 */
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
    metrics: FieldLayoutMetrics,
    onGoal: () -> Unit,
    onTimeout: () -> Unit,
    onPullInfraction: () -> Unit,
) {
    val titleTextStyle = MaterialTheme.typography.titleLarge.copy(
        fontSize = metrics.titleFontSize,
        lineHeight = metrics.titleLineHeight,
    )
    val detailTextStyle = MaterialTheme.typography.bodyMedium.copy(
        fontSize = metrics.detailFontSize,
        lineHeight = metrics.detailLineHeight,
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(metrics.teamRowHeight)
            .background(background)
            .padding(metrics.teamRowPadding),
        horizontalArrangement = Arrangement.spacedBy(metrics.teamRowGap),
        verticalAlignment = Alignment.Top,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(metrics.titleGap),
            verticalAlignment = Alignment.Top,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(metrics.detailGap)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(metrics.titleGap),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = team.name,
                        color = team.color.content,
                        modifier = Modifier.weight(1f, fill = false),
                        style = titleTextStyle,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = team.score.toString(),
                        color = team.color.content,
                        style = titleTextStyle,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = "TO $timeoutsRemaining",
                    color = team.color.content,
                    style = detailTextStyle,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Cards $cardPoints",
                    color = team.color.content,
                    style = detailTextStyle,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "TF ${team.technicalFouls}",
                    color = team.color.content,
                    style = detailTextStyle,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Pull violations ${team.pullViolationCount()}",
                    color = team.color.content,
                    style = detailTextStyle,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(metrics.actionGap),
        ) {
            CompactActionButton(
                label = "Goal",
                modifier = Modifier.testTag("live-${teamId.name}-goal"),
                enabled = interactionsEnabled,
                height = metrics.actionButtonHeight,
                onClick = onGoal,
            )
            CompactActionButton(
                label = "Timeout",
                modifier = Modifier.testTag("live-${teamId.name}-timeout"),
                enabled = interactionsEnabled,
                height = metrics.actionButtonHeight,
                onClick = onTimeout,
            )
            CompactActionButton(
                label = if (isPulling) "Offsides" else "False Start",
                modifier = Modifier.testTag("live-${teamId.name}-pull-infraction"),
                enabled = interactionsEnabled && pullInfractionEnabled,
                height = metrics.actionButtonHeight,
                onClick = onPullInfraction,
            )
        }
    }
}

/**
 * Render the center-field arrow showing which end the pull comes from.
 *
 * @param pullingFromEnd The field end occupied by the pulling team.
 * @param modifier Optional layout modifier for the indicator column.
 */
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
        verticalArrangement = Arrangement.SpaceEvenly,
    ) {
        if (pullingFromEnd == FieldEnd.FAR) {
            PullDirectionLabel()
        }
        PullDirectionArrow(pointsTowardNearEnd = pullingFromEnd == FieldEnd.FAR)
        if (pullingFromEnd == FieldEnd.NEAR) {
            PullDirectionLabel()
        }
    }
}

/**
 * Draw the pull-direction arrow.
 *
 * @param pointsTowardNearEnd Whether the arrow should point toward the observer's near end.
 */
@Composable
private fun PullDirectionArrow(pointsTowardNearEnd: Boolean) {
    Canvas(
        modifier = Modifier
            .width(28.dp)
            .height(48.dp),
    ) {
        val centerX = size.width / 2f
        val strokeWidth = 5.dp.toPx()
        val headHeight = 13.dp.toPx()
        val headHalfWidth = 10.dp.toPx()
        val shaftInset = 2.dp.toPx()
        val headBaseY = if (pointsTowardNearEnd) {
            size.height - headHeight
        } else {
            headHeight
        }
        val tipY = if (pointsTowardNearEnd) size.height else 0f
        val shaftStartY = if (pointsTowardNearEnd) shaftInset else headBaseY
        val shaftEndY = if (pointsTowardNearEnd) headBaseY else size.height - shaftInset

        drawLine(
            color = Color.Black,
            start = Offset(centerX, shaftStartY),
            end = Offset(centerX, shaftEndY),
            strokeWidth = strokeWidth,
        )
        val head = Path().apply {
            moveTo(centerX, tipY)
            lineTo(centerX - headHalfWidth, headBaseY)
            lineTo(centerX + headHalfWidth, headBaseY)
            close()
        }
        drawPath(head, Color.Black)
    }
}

/// Render the `Pull` label beside the pull-direction arrow.
@Composable
private fun PullDirectionLabel() {
    Text(
        "Pull",
        color = Color.Black,
        fontWeight = FontWeight.Bold,
        lineHeight = 20.sp,
    )
}

/**
 * Render the active countdown row, expired-pull actions, or misconduct countdown action.
 *
 * @param countdown The visible countdown state, or null when no countdown is active.
 * @param enabled Whether countdown actions should be enabled.
 * @param onAdjust Callback receiving signed second adjustments from the quick buttons.
 * @param onTogglePaused Callback for pausing or resuming the countdown.
 * @param expiredPullActions Actions to show after undoing an automatic start point.
 * @param misconductCountdownAction Action to show before starting a live-point misconduct countdown.
 * @param height Reserved row height so the field layout does not shift when content changes.
 */
@Composable
internal fun CountdownLine(
    countdown: ActiveCountdownDisplay?,
    enabled: Boolean,
    onAdjust: (Int) -> Unit,
    onTogglePaused: () -> Unit,
    expiredPullActions: ExpiredPullActions? = null,
    misconductCountdownAction: MisconductCountdownAction? = null,
    height: Dp,
) {
    val visible = countdown != null || expiredPullActions != null || misconductCountdownAction != null
    val displayCountdown = countdown ?: ActiveCountdownDisplay("Pull in", Duration.ZERO, null)
    val titleFontSize = (height.value * 0.4f).coerceIn(22f, 28f).sp
    val labelFontSize = (height.value * 0.21f).coerceIn(12f, 14f).sp
    val rowModifier = if (visible) {
        Modifier.fillMaxWidth()
    } else {
        Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { }
            .alpha(0f)
    }
    Column(
        modifier = rowModifier.height(height),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (misconductCountdownAction != null) {
                SmallActionButton(
                    label = "Start Misconduct Countdown",
                    enabled = enabled,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("live-start-misconduct-countdown"),
                    onClick = misconductCountdownAction.onStart,
                )
            } else if (expiredPullActions != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SmallActionButton(
                        label = "Time Violation",
                        enabled = enabled,
                        containerColor = Color(0xFFE53935),
                        contentColor = Color.Black,
                        borderColor = Color.Black,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("live-time-violation"),
                        onClick = expiredPullActions.onTimeViolation,
                    )
                    SmallActionButton(
                        label = "Restart Countdown",
                        enabled = enabled,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("live-restart-pull-countdown"),
                        onClick = expiredPullActions.onRestartPullCountdown,
                    )
                }
            } else {
                Text(
                    text = "${displayCountdown.label} ${formatDuration(displayCountdown.remaining)}",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge.copy(fontSize = titleFontSize),
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    CountdownPauseToggleButton(
                        isPaused = displayCountdown.isPaused,
                        enabled = enabled && visible,
                        onClick = onTogglePaused,
                    )
                    CountdownAdjustButton(label = "-5", enabled = enabled && visible) {
                        onAdjust(-5)
                    }
                    CountdownAdjustButton(label = "+5", enabled = enabled && visible) {
                        onAdjust(5)
                    }
                }
            }
        }
        Text(
            text = if (countdown == null && (expiredPullActions != null || misconductCountdownAction != null)) {
                ""
            } else if (displayCountdown.isPaused) {
                "Paused"
            } else {
                displayCountdown.nextCue?.let { cue ->
                    "Next cue at ${formatDuration(cue.countdownTime)} - ${cue.message}"
                } ?: "Next cue"
            },
            style = MaterialTheme.typography.labelMedium.copy(fontSize = labelFontSize),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Render a narrow button for five-second countdown adjustments.
 *
 * @param label The adjustment label, normally `-5` or `+5`.
 * @param enabled Whether the adjustment can be applied.
 * @param onClick Callback invoked when the observer taps the button.
 */
@Composable
private fun CountdownAdjustButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    SmallActionButton(
        label = label,
        enabled = enabled,
        modifier = Modifier.width(42.dp),
    ) {
        onClick()
    }
}

/**
 * Render the pause/resume countdown control with classic media symbols.
 *
 * @param isPaused Whether the button should show the resume/play symbol.
 * @param enabled Whether the button can be pressed.
 * @param onClick Callback invoked when the observer toggles pause state.
 */
@Composable
private fun CountdownPauseToggleButton(
    isPaused: Boolean,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val contentColor = if (enabled) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
    }
    val description = if (isPaused) "Resume countdown" else "Pause countdown"
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .defaultMinSize(minWidth = 34.dp, minHeight = 34.dp)
                .testTag(if (isPaused) "live-resume-countdown" else "live-pause-countdown")
                .semantics { contentDescription = description },
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = Color.Transparent,
                contentColor = contentColor,
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
        ) {
            Canvas(modifier = Modifier.size(14.dp)) {
                if (isPaused) {
                    val path = Path().apply {
                        moveTo(size.width * 0.25f, size.height * 0.15f)
                        lineTo(size.width * 0.25f, size.height * 0.85f)
                        lineTo(size.width * 0.82f, size.height * 0.5f)
                        close()
                    }
                    drawPath(path, contentColor)
                } else {
                    val barWidth = size.width * 0.24f
                    val gap = size.width * 0.18f
                    val barHeight = size.height * 0.7f
                    val top = size.height * 0.15f
                    val left = (size.width - 2 * barWidth - gap) / 2f
                    drawRect(
                        color = contentColor,
                        topLeft = Offset(left, top),
                        size = Size(barWidth, barHeight),
                    )
                    drawRect(
                        color = contentColor,
                        topLeft = Offset(left + barWidth + gap, top),
                        size = Size(barWidth, barHeight),
                    )
                }
            }
        }
    }
}

/**
 * Callbacks shown after undoing an automatic start-point transition.
 *
 * @param onTimeViolation Callback for assessing a time violation.
 * @param onRestartPullCountdown Callback for restarting the pull countdown.
 */
internal data class ExpiredPullActions(
    val onTimeViolation: () -> Unit,
    val onRestartPullCountdown: () -> Unit,
)

/**
 * Callback for manually starting a pending misconduct countdown.
 *
 * @param onStart Callback invoked when the observer starts the misconduct countdown.
 */
internal data class MisconductCountdownAction(
    val onStart: () -> Unit,
)

/// Count combined offsides and false-start pull violations for display.
private fun TeamLiveState.pullViolationCount(): Int {
    return offsides + falseStarts
}

/**
 * Countdown text and next-cue details currently shown on the live screen.
 *
 * @param label The short countdown label.
 * @param remaining The clamped time remaining.
 * @param nextCue The next cue inside the active countdown, if one is available.
 * @param isPaused Whether the countdown is currently paused.
 */
internal data class ActiveCountdownDisplay(
    val label: String,
    val remaining: Duration,
    val nextCue: TimingCueDisplay?,
    val isPaused: Boolean = false,
)

/**
 * Compute the countdown text currently visible on the live screen.
 *
 * @param now The current epoch millis used to compute remaining time and next cue.
 */
internal fun GameState.activeCountdownDisplay(now: Long): ActiveCountdownDisplay? {
    val countdown = countdown ?: return null
    val remaining = countdown.remainingDuration(now)
    return if (countdown.kind == CountdownKind.HALFTIME) {
        if (!remaining.isZero) {
            ActiveCountdownDisplay(
                label = countdown.label,
                remaining = remaining,
                nextCue = countdown.nextTimingCue(now),
                isPaused = countdown.isPaused(),
            )
        } else {
            // Once halftime expires, show the follow-on between-points countdown immediately.
            val followOn = betweenPointsDisplay(pullingFromEnd, countdown.targetEpoch, now)
            val followOnCountdown = buildBetweenPointsCountdown(pullingFromEnd, countdown.targetEpoch)
            ActiveCountdownDisplay(
                label = followOn.first,
                remaining = followOn.second,
                nextCue = followOnCountdown.nextTimingCue(now),
                isPaused = countdown.isPaused(),
            )
        }
    } else {
        ActiveCountdownDisplay(
            label = countdown.label,
            remaining = remaining,
            nextCue = countdown.nextTimingCue(now),
            isPaused = countdown.isPaused(),
        )
    }
}

/**
 * Report whether halftime has elapsed and the live screen can show `Start Point`.
 *
 * @param now The current epoch millis used to compare against halftime's target time.
 */
internal fun GameState.halftimeTransitionReady(now: Long): Boolean {
    val countdown = countdown ?: return false
    return phase == GamePhase.HALFTIME &&
        countdown.kind == CountdownKind.HALFTIME &&
        now >= countdown.targetEpoch
}
