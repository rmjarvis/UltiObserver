package rmjarvis.ultiobserver

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Text
import rmjarvis.ultiobserver.ui.theme.UltiObserverTheme
import rmjarvis.ultiobserver.wearprotocol.WearGuidanceLineSnapshot

/** Display values for one team's six watch actions. */
internal data class TeamActionsDisplay(
    val team: TeamDisplay,
    val nameInfo: List<WearGuidanceLineSnapshot>,
    val timeViolationLabel: String,
    val pullViolationLabel: String,
    val cardLabel: String,
    val technicalFoulLabel: String,
    val timeoutLabel: String,
    val goalEnabled: Boolean,
    val timeViolationEnabled: Boolean,
    val pullViolationEnabled: Boolean,
    val cardEnabled: Boolean,
    val technicalFoulEnabled: Boolean,
    val timeoutEnabled: Boolean,
)

/** Provide the clock and team colors shared by every team-action screen. */
@Composable
private fun TeamScreenScaffold(
    team: TeamDisplay,
    content: @Composable BoxScope.() -> Unit,
) {
    UltiObserverTheme {
        AppScaffold(
            timeText = { AppTimeText() },
            containerColor = team.backgroundColor,
            contentColor = team.contentColor,
            content = content,
        )
    }
}

/**
 * Show the phone-style compact action grid for one team.
 *
 * The full-screen team-color background keeps the selected team obvious. The six action callbacks
 * will eventually send commands to the authoritative phone state; Cancel and system Back return
 * to the watch's main game screen immediately.
 */
@Composable
internal fun TeamActionsScreen(
    display: TeamActionsDisplay,
    onTeamInfo: () -> Unit,
    onGoal: () -> Unit,
    onTimeViolation: () -> Unit,
    onPullViolation: () -> Unit,
    onCard: () -> Unit,
    onTechnicalFoul: () -> Unit,
    onTimeout: () -> Unit,
    onCancel: () -> Unit,
) {
    TeamScreenScaffold(display.team) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
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
                Row(
                    modifier = Modifier.height(titleHeight),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp, Alignment.CenterHorizontally),
                ) {
                    Text(
                        text = display.team.name,
                        modifier = Modifier.weight(1f, fill = false),
                        color = display.team.contentColor,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Center,
                    )
                    if (display.nameInfo.isNotEmpty()) {
                        Text(
                            text = "ⓘ",
                            modifier = Modifier.clickable(role = Role.Button, onClick = onTeamInfo)
                                .semantics { contentDescription = "Coach/captain information" }
                                .padding(horizontal = 3.dp),
                            color = display.team.contentColor,
                            fontSize = 16.sp,
                            lineHeight = 18.sp,
                        )
                    }
                }
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
                        ActionButton(
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
                    ActionButton(
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

/** Show phone-formatted coach and captain names without leaving the team workflow. */
@Composable
internal fun TeamInfoScreen(
    team: TeamDisplay,
    nameInfo: List<WearGuidanceLineSnapshot>,
    onBack: () -> Unit,
) {
    TeamScreenScaffold(team) {
        Column(
            modifier = Modifier.fillMaxSize().padding(top = 28.dp, start = 24.dp, end = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = team.name,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            Column(
                modifier = Modifier.weight(1f).fillMaxWidth()
                    .verticalScroll(rememberScrollState()).padding(vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                nameInfo.forEach { line ->
                    Text(
                        text = line.text,
                        fontSize = 12.sp,
                        lineHeight = 16.sp,
                        fontWeight = if (line.bold) FontWeight.Bold else FontWeight.Normal,
                    )
                }
            }
            Box(
                modifier = Modifier.height(42.dp).clickable(role = Role.Button, onClick = onBack)
                    .padding(horizontal = 16.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "Back", fontSize = 12.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

/** Show the card colors that can be assessed from the watch. */
@Composable
internal fun CardChoiceScreen(
    display: TeamActionsDisplay,
    enabled: Boolean,
    onYellow: () -> Unit,
    onRed: () -> Unit,
    onBlue: () -> Unit,
    onCancel: () -> Unit,
) {
    TeamScreenScaffold(display.team) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(display.team.backgroundColor),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.86f)
                    .align(Alignment.Center)
                    .offset(y = (-12).dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "Assess a card",
                        color = display.team.contentColor,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = display.team.name,
                        color = display.team.contentColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
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
                            .height(32.dp),
                        horizontalArrangement = Arrangement.spacedBy(ActionGap),
                    ) {
                        ActionButton(
                            label = "Yellow",
                            enabled = enabled,
                            background = YellowCardButtonColor,
                            contentColor = Color.Black,
                            fontSize = 12.sp,
                            shape = CardChoiceButtonShape,
                            borderColor = null,
                            onClick = onYellow,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                        ActionButton(
                            label = "Red",
                            enabled = enabled,
                            background = RedCardButtonColor,
                            contentColor = Color.Black,
                            fontSize = 12.sp,
                            shape = CardChoiceButtonShape,
                            borderColor = null,
                            onClick = onRed,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                        ActionButton(
                            label = "Blue",
                            enabled = enabled,
                            background = BlueCardButtonColor,
                            contentColor = Color.White,
                            fontSize = 12.sp,
                            shape = CardChoiceButtonShape,
                            borderColor = null,
                            onClick = onBlue,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }
                }
            }
            TeamScreenCancel(
                color = display.team.contentColor,
                enabled = enabled,
                onCancel = onCancel,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }
}

/** Offer number-only entry or the complete phone workflow for a yellow or red card. */
@Composable
internal fun PlayerCardEntryOptionsScreen(
    display: TeamActionsDisplay,
    cardType: CardType,
    jerseyNumber: String,
    enabled: Boolean,
    onJerseyNumberChange: (String) -> Unit,
    onRecord: () -> Unit,
    onContinueOnPhone: () -> Unit,
    onCancel: () -> Unit,
) {
    var draftJerseyNumber by remember(jerseyNumber) {
        mutableStateOf(jerseyNumber)
    }
    val actionRows = if (jerseyNumber.isEmpty()) 2 else 3
    val focusRequester = remember { FocusRequester() }
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    BackHandler(
        enabled = enabled,
        onBack = {
            onCancel()
        },
    )
    TeamScreenScaffold(display.team) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(display.team.backgroundColor),
        ) {
            BasicTextField(
                value = draftJerseyNumber,
                onValueChange = { value ->
                    draftJerseyNumber = value.filter { character -> character.isDigit() }
                },
                modifier = Modifier
                    .width(1.dp)
                    .height(1.dp)
                    .alpha(0f)
                    .focusRequester(focusRequester),
                enabled = enabled,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done,
                ),
                keyboardActions = KeyboardActions(
                    onDone = {
                        onJerseyNumberChange(draftJerseyNumber)
                        focusManager.clearFocus()
                        // Defensive guard: Compose returns null when software keyboard control is unavailable.
                        keyboardController?.hide()
                    }
                ),
                singleLine = true,
            )
            Column(
                modifier = Modifier
                    .fillMaxWidth(0.86f)
                    .fillMaxHeight()
                    .padding(top = 26.dp, bottom = 36.dp)
                    .align(Alignment.Center),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(6.dp, Alignment.CenterVertically),
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text(
                        text = "${cardType.label} card",
                        color = display.team.contentColor,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = display.team.name,
                        color = display.team.contentColor,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false)
                        .heightIn(
                            max = 32.dp * actionRows + ActionGap * (actionRows - 1) + PanelPadding * 2,
                        )
                        .clip(PanelShape)
                        .background(ActionPanelColor)
                        .padding(PanelPadding),
                    verticalArrangement = Arrangement.spacedBy(ActionGap),
                ) {
                    ActionButton(
                        label = if (jerseyNumber.isEmpty()) {
                            "Enter player number"
                        } else {
                            "Change number ($jerseyNumber)"
                        },
                        enabled = enabled,
                        background = NeutralButtonColor,
                        contentColor = Color.Black,
                        fontSize = 12.sp,
                        onClick = {
                            draftJerseyNumber = jerseyNumber
                            focusRequester.requestFocus()
                            // Defensive guard: Compose returns null when software keyboard control is unavailable.
                            keyboardController?.show()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                    ActionButton(
                        label = "Enter details on phone",
                        enabled = enabled,
                        background = NeutralButtonColor,
                        contentColor = Color.Black,
                        fontSize = 12.sp,
                        onClick = onContinueOnPhone,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                    )
                    if (jerseyNumber.isNotEmpty()) {
                        ActionButton(
                            label = "Record card",
                            enabled = enabled,
                            background = PrimaryActionButtonColor,
                            contentColor = Color.White,
                            fontSize = 12.sp,
                            onClick = onRecord,
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                        )
                    }
                }
            }
            TeamScreenCancel(
                color = display.team.contentColor,
                enabled = enabled,
                onCancel = onCancel,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .height(36.dp),
            )
        }
    }
}

/** Bottom-edge Cancel action shared by the watch's team-colored card screens. */
@Composable
private fun TeamScreenCancel(
    color: Color,
    enabled: Boolean,
    onCancel: () -> Unit,
    modifier: Modifier,
) {
    Box(
        modifier = modifier
            .width(88.dp)
            .height(48.dp)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onCancel,
            ),
        contentAlignment = Alignment.BottomCenter,
    ) {
        Text(
            text = "Cancel",
            modifier = Modifier.padding(bottom = 7.dp),
            color = color,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
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
        ActionButton(
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
        ActionButton(
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
private fun ActionButton(
    label: String,
    enabled: Boolean,
    background: Color,
    contentColor: Color,
    fontSize: TextUnit,
    shape: Shape = PanelShape,
    borderColor: Color? = Color.Black,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    Box(
        modifier = modifier
            .alpha(if (enabled) 1f else DisabledActionAlpha)
            .clip(shape)
            .background(background)
            .then(
                if (borderColor == null) {
                    Modifier
                } else {
                    Modifier.border(BorderStroke(1.dp, borderColor), shape)
                }
            )
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

private val PanelShape = RoundedCornerShape(8.dp)
private val CardChoiceButtonShape = RoundedCornerShape(percent = 50)
private val ActionPanelColor = Color(0xCCFFFFFF)
private val GoalButtonColor = Color(0xFF2E7D32)
private val PrimaryActionButtonColor = Color(0xFF2E7D32)
private val CardButtonColor = Color(0xFFFDD835)
private val YellowCardButtonColor = Color(0xFFFFD92F)
private val RedCardButtonColor = Color(0xFFE64B3C)
private val BlueCardButtonColor = Color(0xFF1976D2)
private val TechButtonColor = Color(0xFFFFB74D)
private val TimeoutButtonColor = Color(0xFF90CAF9)
private val NeutralButtonColor = Color(0xFFF7F2EA)
private val ActionGap = 3.dp
private val PanelPadding = 4.dp
private const val DisabledActionAlpha = 0.45f
