package rmjarvis.ultiobserver.wear

import android.content.res.Configuration
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.Text
import rmjarvis.ultiobserver.wear.ui.theme.UltiObserverWearTheme

/** Display values for one team's six watch actions. */
internal data class WearTeamActionsDisplay(
    val team: WearTeamDisplay,
    val timeViolationLabel: String,
    val pullViolationLabel: String,
    val cardLabel: String,
    val technicalFoulLabel: String,
    val timeoutLabel: String,
    val goalEnabled: Boolean = true,
    val timeViolationEnabled: Boolean = true,
    val pullViolationEnabled: Boolean = true,
    val cardEnabled: Boolean = true,
    val technicalFoulEnabled: Boolean = true,
    val timeoutEnabled: Boolean = true,
)

/**
 * Show the phone-style compact action grid for one team.
 *
 * The full-screen team-color background keeps the selected team obvious. The six action callbacks
 * will eventually send commands to the authoritative phone state; Cancel and system Back return
 * to the watch's main game screen immediately.
 */
@Composable
internal fun WearTeamActionsScreen(
    display: WearTeamActionsDisplay,
    onGoal: () -> Unit,
    onTimeViolation: () -> Unit,
    onPullViolation: () -> Unit,
    onCard: () -> Unit,
    onTechnicalFoul: () -> Unit,
    onTimeout: () -> Unit,
    onCancel: () -> Unit,
) {
    UltiObserverWearTheme {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .clip(LocalConfiguration.current.wearScreenShape())
                .background(display.team.backgroundColor),
        ) {
            val contentWidth = maxWidth * 0.90f
            val titleHeight = maxHeight * 0.10f
            val primaryHeight = maxHeight * 0.27f
            val actionHeight = (primaryHeight - ActionGap) / 2f
            val timeoutHeight = maxHeight * 0.13f
            val panelHeight = PanelPadding * 2f + primaryHeight + ActionGap + timeoutHeight
            val contentTopPadding =
                (maxHeight - panelHeight) / 2f - titleHeight - ActionGap

            Column(
                modifier = Modifier
                    .width(contentWidth)
                    .align(Alignment.TopCenter)
                    .padding(top = contentTopPadding),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(ActionGap),
            ) {
                Text(
                    text = display.team.name,
                    modifier = Modifier.height(titleHeight),
                    color = display.team.contentColor,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(PanelShape)
                        .background(ActionPanelColor)
                        .padding(PanelPadding),
                    verticalArrangement = Arrangement.spacedBy(ActionGap),
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(primaryHeight),
                        horizontalArrangement = Arrangement.spacedBy(ActionGap),
                    ) {
                        WearActionButton(
                            label = "Goal",
                            enabled = display.goalEnabled,
                            background = GoalButtonColor,
                            contentColor = Color.White,
                            fontSize = 12.sp,
                            onClick = onGoal,
                            modifier = Modifier
                                .weight(0.86f)
                                .fillMaxHeight(),
                        )
                        ActionPair(
                            topLabel = display.timeViolationLabel,
                            bottomLabel = display.pullViolationLabel,
                            topEnabled = display.timeViolationEnabled,
                            bottomEnabled = display.pullViolationEnabled,
                            topColor = NeutralButtonColor,
                            bottomColor = NeutralButtonColor,
                            actionHeight = actionHeight,
                            onTop = onTimeViolation,
                            onBottom = onPullViolation,
                            modifier = Modifier.weight(1.15f),
                        )
                        ActionPair(
                            topLabel = display.cardLabel,
                            bottomLabel = display.technicalFoulLabel,
                            topEnabled = display.cardEnabled,
                            bottomEnabled = display.technicalFoulEnabled,
                            topColor = CardButtonColor,
                            bottomColor = TechButtonColor,
                            actionHeight = actionHeight,
                            onTop = onCard,
                            onBottom = onTechnicalFoul,
                            modifier = Modifier.weight(0.9f),
                        )
                    }
                    WearActionButton(
                        label = display.timeoutLabel,
                        enabled = display.timeoutEnabled,
                        background = TimeoutButtonColor,
                        contentColor = Color.Black,
                        fontSize = 11.sp,
                        onClick = onTimeout,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(timeoutHeight),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .width(maxWidth * 0.55f)
                    .height(48.dp)
                    .align(Alignment.BottomCenter)
                    .clickable(
                        role = Role.Button,
                        onClick = onCancel,
                    ),
                contentAlignment = Alignment.BottomCenter,
            ) {
                Text(
                    text = "Cancel",
                    modifier = Modifier.padding(bottom = 7.dp),
                    color = display.team.contentColor,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Render one stacked pair of compact actions. */
@Composable
private fun ActionPair(
    topLabel: String,
    bottomLabel: String,
    topEnabled: Boolean,
    bottomEnabled: Boolean,
    topColor: Color,
    bottomColor: Color,
    actionHeight: Dp,
    onTop: () -> Unit,
    onBottom: () -> Unit,
    modifier: Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(ActionGap),
    ) {
        WearActionButton(
            label = topLabel,
            enabled = topEnabled,
            background = topColor,
            contentColor = Color.Black,
            fontSize = 10.sp,
            onClick = onTop,
            modifier = Modifier
                .fillMaxWidth()
                .height(actionHeight),
        )
        WearActionButton(
            label = bottomLabel,
            enabled = bottomEnabled,
            background = bottomColor,
            contentColor = Color.Black,
            fontSize = 10.sp,
            onClick = onBottom,
            modifier = Modifier
                .fillMaxWidth()
                .height(actionHeight),
        )
    }
}

/** Render a phone-colored compact field action with Wear click semantics. */
@Composable
private fun WearActionButton(
    label: String,
    enabled: Boolean,
    background: Color,
    contentColor: Color,
    fontSize: TextUnit,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else DisabledActionAlpha)
            .clip(PanelShape)
            .background(background)
            .border(BorderStroke(1.dp, Color.Black), PanelShape)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 3.dp, vertical = 2.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = contentColor,
            fontSize = fontSize,
            fontWeight = FontWeight.Medium,
            lineHeight = fontSize,
            maxLines = 1,
            softWrap = false,
            overflow = TextOverflow.Clip,
            textAlign = TextAlign.Center,
        )
    }
}

private fun Configuration.wearScreenShape(): Shape {
    return if (isScreenRound) CircleShape else RectangleShape
}

private val PanelShape = RoundedCornerShape(8.dp)
private val ActionPanelColor = Color(0xCCFFFFFF)
private val GoalButtonColor = Color(0xFF2E7D32)
private val CardButtonColor = Color(0xFFFDD835)
private val TechButtonColor = Color(0xFFFFB74D)
private val TimeoutButtonColor = Color(0xFF90CAF9)
private val NeutralButtonColor = Color(0xFFF7F2EA)
private val ActionGap = 3.dp
private val PanelPadding = 4.dp
private const val DisabledActionAlpha = 0.45f
