package rmjarvis.ultiobserver

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import java.time.Duration
import java.time.LocalTime

// Full-width unlock slider that only activates if the drag starts on the left side.
@Composable
internal fun FieldUnlockControl(
    onUnlock: () -> Unit,
) {
    var trackWidthPx by remember { mutableStateOf(0f) }
    var thumbOffsetPx by remember { mutableStateOf(0f) }
    var dragEnabled by remember { mutableStateOf(false) }
    val density = LocalDensity.current
    val thumbDiameter = 40.dp
    val thumbDiameterPx = with(density) { thumbDiameter.toPx() }

    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            "Slide right to unlock",
            style = MaterialTheme.typography.bodyMedium,
            color = Color.Black,
            textAlign = TextAlign.Center,
            fontWeight = FontWeight.SemiBold,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("live-unlock-slider")
                .height(52.dp)
                .onSizeChanged { trackWidthPx = it.width.toFloat() }
                .background(Color(0x66FFFFFF), RoundedCornerShape(26.dp))
                .border(1.dp, Color.Black, RoundedCornerShape(26.dp))
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
                                onUnlock()
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
                "Unlock",
                modifier = Modifier.align(Alignment.Center),
                color = Color.Black.copy(alpha = 0.7f),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
            )
            Box(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .offset { IntOffset(thumbOffsetPx.toInt(), 0) }
                    .padding(6.dp)
                    .size(thumbDiameter)
                    .background(Color.White, RoundedCornerShape(20.dp))
                    .border(1.dp, Color.Black, RoundedCornerShape(20.dp)),
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
        oppositeTeam(state.pullingTeam)
    }
    val bottomSlot = oppositeTeam(topSlot)
    val topTeam = state.teamFor(topSlot)
    val bottomTeam = state.teamFor(bottomSlot)
    val pullFrom = state.pullingFromEnd

    // Draw the top team row, center field area, and bottom team row in that order.
    Card(
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
                pullInfractionEnabled = if (state.pullingTeam == topSlot) {
                    !state.pullSequenceOffsidesRecorded
                } else {
                    !state.pullSequenceFalseStartRecorded
                },
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
                pullInfractionEnabled = if (state.pullingTeam == bottomSlot) {
                    !state.pullSequenceOffsidesRecorded
                } else {
                    !state.pullSequenceFalseStartRecorded
                },
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
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = team.score.toString(),
                        color = team.color.content,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                    )
                }
                Text(
                    text = "TO $timeoutsRemaining  Cards $cardPoints  TF ${team.technicalFouls}",
                    color = team.color.content,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = "Pull violations ${pullViolationCount(team)}",
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
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            "Pull",
            color = Color.Black.copy(alpha = if (pullingFromEnd == FieldEnd.FAR) 1f else 0f),
            fontWeight = FontWeight.Bold,
        )
        Text(
            text = if (pullingFromEnd == FieldEnd.FAR) "↓" else "↑",
            color = Color.Black,
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.Black,
        )
        Text(
            "Pull",
            color = Color.Black.copy(alpha = if (pullingFromEnd == FieldEnd.NEAR) 1f else 0f),
            fontWeight = FontWeight.Bold,
        )
    }
}

// Active countdown plus the quick -5/+5 correction buttons.
@Composable
internal fun CountdownLine(
    countdown: ActiveCountdownDisplay,
    enabled: Boolean,
    onAdjust: (Int) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "${countdown.label} ${formatDuration(countdown.remaining)}",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            SmallActionButton(label = "-5", enabled = enabled) {
                onAdjust(-5)
            }
            SmallActionButton(label = "+5", enabled = enabled) {
                onAdjust(5)
            }
        }
    }
}

// Return the other team id.
private fun oppositeTeam(teamId: TeamId): TeamId {
    return if (teamId == TeamId.TEAM_ONE) TeamId.TEAM_TWO else TeamId.TEAM_ONE
}

// Offsides and false starts are combined for pull-violation display/rules.
private fun pullViolationCount(team: TeamLiveState): Int {
    return team.offsides + team.falseStarts
}

internal data class ActiveCountdownDisplay(
    val label: String,
    val remaining: Duration,
)

// Compute the countdown text currently visible on the live screen.
internal fun activeCountdownDisplay(state: LiveGameState, now: Long): ActiveCountdownDisplay? {
    val countdown = state.countdown ?: return null
    return if (countdown.kind == CountdownKind.HALFTIME) {
        val halftimeRemaining = countdown.targetEpoch - now
        if (halftimeRemaining > 0L) {
            ActiveCountdownDisplay(
                label = countdown.label,
                remaining = Duration.ofMillis(halftimeRemaining),
            )
        } else {
            // Once halftime expires, show the follow-on between-points countdown immediately.
            val followOn = betweenPointsDisplay(state.pullingFromEnd, countdown.targetEpoch, now)
            ActiveCountdownDisplay(label = followOn.first, remaining = followOn.second)
        }
    } else {
        ActiveCountdownDisplay(
            label = countdown.label,
            remaining = Duration.ofMillis((countdown.targetEpoch - now).coerceAtLeast(0L)),
        )
    }
}

// Halftime can become Start Point once the halftime countdown itself has elapsed.
internal fun halftimeTransitionReady(state: LiveGameState, now: Long): Boolean {
    val countdown = state.countdown ?: return false
    return state.phase == LivePhase.HALFTIME &&
        countdown.kind == CountdownKind.HALFTIME &&
        now >= countdown.targetEpoch
}
