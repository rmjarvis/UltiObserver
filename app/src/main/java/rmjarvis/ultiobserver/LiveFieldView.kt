package rmjarvis.ultiobserver

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Duration
import java.time.LocalTime

// Full-width unlock slider that only activates if the drag starts on the left side.
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

// Full-width confirmation slider that only activates if the drag starts on the left side.
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
                    // Compose cancels/restarts this pointerInput coroutine in normal use; the
                    // suspend-lambda epilogue after detectDragGestures returns is not user-reachable.
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

// Top status line showing the real clock and the next relevant cap.
@Composable
internal fun StatusLine(
    currentTime: LocalTime,
    capStatus: CapStatus?,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatClockTime(currentTime),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = capStatus?.let { "${it.label} ${formatDuration(it.remaining)}" } ?: "Caps passed",
            style = MaterialTheme.typography.bodyLarge,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

// Draw the field as top/bottom end zones plus a center strip for pull direction and controls.
@Composable
internal fun FieldSketchCard(
    state: LiveGameState,
    interactionsEnabled: Boolean,
    showPullIndicator: Boolean,
    centerContent: @Composable (() -> Unit)?,
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
        modifier = Modifier.testTag("live-field-diagram"),
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
                goalEnabled = state.phase != LivePhase.GAME_OVER,
                onGoal = { onGoal(topSlot) },
                onTimeout = { onTimeout(topSlot) },
                onPullInfraction = { onPullInfraction(topSlot) },
            )
            // Center field strip with pull direction and the main central control.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(120.dp)
                    .background(Color(0xFFA8D5A0))
                    .padding(horizontal = 16.dp, vertical = 12.dp),
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
                        centerContent?.invoke()
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
                goalEnabled = state.phase != LivePhase.GAME_OVER,
                onGoal = { onGoal(bottomSlot) },
                onTimeout = { onTimeout(bottomSlot) },
                onPullInfraction = { onPullInfraction(bottomSlot) },
            )
        }
    }
}

// One team row on the field, with score/state info and the main live actions.
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
    goalEnabled: Boolean,
    onGoal: () -> Unit,
    onTimeout: () -> Unit,
    onPullInfraction: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(background)
            .padding(12.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = team.name,
                        color = team.color.content,
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = team.score.toString(),
                        color = team.color.content,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = "TO $timeoutsRemaining",
                    color = team.color.content,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Cards $cardPoints",
                    color = team.color.content,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "TF ${team.technicalFouls}",
                    color = team.color.content,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Pull violations ${team.pullViolationCount()}",
                    color = team.color.content,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
        Column(
            horizontalAlignment = Alignment.End,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            CompactActionButton(
                label = "Goal",
                modifier = Modifier.testTag("live-${teamId.name}-goal"),
                enabled = goalEnabled && interactionsEnabled,
                onClick = onGoal,
            )
            CompactActionButton(
                label = "Timeout",
                modifier = Modifier.testTag("live-${teamId.name}-timeout"),
                enabled = interactionsEnabled,
                onClick = onTimeout,
            )
            CompactActionButton(
                label = if (isPulling) "Offsides" else "False Start",
                modifier = Modifier.testTag("live-${teamId.name}-pull-infraction"),
                enabled = interactionsEnabled && pullInfractionEnabled,
                onClick = onPullInfraction,
            )
        }
    }
}

// Center-field arrow showing which end the pull comes from.
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

@Composable
private fun PullDirectionLabel() {
    Text(
        "Pull",
        color = Color.Black,
        fontWeight = FontWeight.Bold,
        lineHeight = 20.sp,
    )
}

// Active countdown plus the quick -5/+5 correction buttons.
@Composable
internal fun CountdownLine(
    countdown: ActiveCountdownDisplay?,
    enabled: Boolean,
    onAdjust: (Int) -> Unit,
    onTimeViolation: (() -> Unit)? = null,
    onRestartPullCountdown: (() -> Unit)? = null,
) {
    val visible = countdown != null || onTimeViolation != null || onRestartPullCountdown != null
    val displayCountdown = countdown ?: ActiveCountdownDisplay("Pull in", Duration.ZERO, null)
    val rowModifier = if (visible) {
        Modifier.fillMaxWidth()
    } else {
        Modifier
            .fillMaxWidth()
            .clearAndSetSemantics { }
            .alpha(0f)
    }
    Column(
        modifier = rowModifier,
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (onTimeViolation != null) {
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
                        onClick = onTimeViolation,
                    )
                    if (onRestartPullCountdown != null) {
                        SmallActionButton(
                            label = "Restart Countdown",
                            enabled = enabled,
                            modifier = Modifier
                                .weight(1f)
                                .testTag("live-restart-pull-countdown"),
                            onClick = onRestartPullCountdown,
                        )
                    }
                }
            } else {
                Text(
                    text = "${displayCountdown.label} ${formatDuration(displayCountdown.remaining)}",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SmallActionButton(label = "-5", enabled = enabled && visible) {
                        onAdjust(-5)
                    }
                    SmallActionButton(label = "+5", enabled = enabled && visible) {
                        onAdjust(5)
                    }
                }
            }
        }
        Text(
            text = if (countdown == null && (onTimeViolation != null || onRestartPullCountdown != null)) {
                ""
            } else {
                displayCountdown.nextCue?.let { cue ->
                    "Next cue at ${formatDuration(cue.countdownTime)} - ${cue.message}"
                } ?: "Next cue"
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// Offsides and false starts are combined for pull-violation display/rules.
private fun TeamLiveState.pullViolationCount(): Int {
    return offsides + falseStarts
}

internal data class ActiveCountdownDisplay(
    val label: String,
    val remaining: Duration,
    val nextCue: TimingCueDisplay?,
)

// Compute the countdown text currently visible on the live screen.
internal fun LiveGameState.activeCountdownDisplay(now: Long): ActiveCountdownDisplay? {
    val countdown = countdown ?: return null
    return if (countdown.kind == CountdownKind.HALFTIME) {
        val halftimeRemaining = countdown.targetEpoch - now
        if (halftimeRemaining > 0L) {
            ActiveCountdownDisplay(
                label = countdown.label,
                remaining = Duration.ofMillis(halftimeRemaining),
                nextCue = countdown.nextTimingCue(now),
            )
        } else {
            // Once halftime expires, show the follow-on between-points countdown immediately.
            val followOn = betweenPointsDisplay(pullingFromEnd, countdown.targetEpoch, now)
            val followOnCountdown = buildBetweenPointsCountdown(pullingFromEnd, countdown.targetEpoch)
            ActiveCountdownDisplay(
                label = followOn.first,
                remaining = followOn.second,
                nextCue = followOnCountdown.nextTimingCue(now),
            )
        }
    } else {
        ActiveCountdownDisplay(
            label = countdown.label,
            remaining = Duration.ofMillis((countdown.targetEpoch - now).coerceAtLeast(0L)),
            nextCue = countdown.nextTimingCue(now),
        )
    }
}

// Halftime can become Start Point once the halftime countdown itself has elapsed.
internal fun LiveGameState.halftimeTransitionReady(now: Long): Boolean {
    val countdown = countdown ?: return false
    return phase == LivePhase.HALFTIME &&
        countdown.kind == CountdownKind.HALFTIME &&
        now >= countdown.targetEpoch
}
