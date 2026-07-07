package rmjarvis.ultiobserver

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.North
import androidx.compose.material.icons.filled.South
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.Duration
import java.time.LocalTime

private val TopEndZoneShape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
private val BottomEndZoneShape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)

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
        trackColor = SliderOverlayColor,
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
    trackColor: Color = EmphasizedDarkNeutralColor,
    thumbColor: Color = InputColor,
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
 * @param onGameRules Callback opening the game-rules quick reference.
 */
@Composable
internal fun StatusLine(
    currentTime: LocalTime,
    capStatus: CapStatus?,
    height: Dp,
    onGameRules: () -> Unit,
) {
    val clockFontSize = (height.value * 0.68f).coerceIn(28f, 36f).sp
    val capFontSize = (height.value * 0.42f).coerceIn(18f, 22f).sp
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = formatClockTime(currentTime),
            style = MaterialTheme.typography.headlineMedium.copy(fontSize = clockFontSize),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
        Row(
            modifier = Modifier
                .weight(1f)
                .padding(start = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatusCapText(
                text = capStatus?.let { "${it.label} ${formatDuration(it.remaining)}" } ?: "Caps passed",
                modifier = Modifier.weight(1f),
                preferredFontSize = capFontSize,
            )
            GameRulesIcon(
                onClick = onGameRules,
                size = 28.dp,
                padding = 4.dp,
                tag = "live-game-rules",
            )
        }
    }
}

/**
 * Render cap status text at the largest size that fits beside the rules icon.
 *
 * @param text The cap status text to display.
 * @param preferredFontSize The normal status-line cap font size.
 * @param modifier Modifier applied by the caller.
 */
@Composable
private fun StatusCapText(
    text: String,
    preferredFontSize: TextUnit,
    modifier: Modifier,
) {
    BoxWithConstraints(
        modifier = modifier,
        contentAlignment = Alignment.CenterEnd,
    ) {
        val textMeasurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val preferredStyle = MaterialTheme.typography.bodyLarge.copy(
            fontSize = preferredFontSize,
            fontWeight = FontWeight.SemiBold,
        )
        val maxWidthPx = with(density) { maxWidth.roundToPx() }
        val minimumFontSize = 16.sp
        val measuredWidthPx = textMeasurer.measure(
            AnnotatedString(text),
            style = preferredStyle,
        ).size.width
        val fontSize = fittedStatusCapFontSize(
            preferredFontSizeSp = preferredFontSize.value,
            minimumFontSizeSp = minimumFontSize.value,
            measuredTextWidthPx = measuredWidthPx,
            maxWidthPx = maxWidthPx,
        ).sp
        Text(
            text = text,
            style = preferredStyle.copy(fontSize = fontSize),
            textAlign = TextAlign.End,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/// Responsive measurements used to draw the live field view.
internal data class FieldLayoutMetrics(
    val fieldHeight: Dp,
    val teamRowHeight: Dp,
    val centerHeight: Dp,
    val centerVerticalPadding: Dp,
    val centerPullIndicatorStartPadding: Dp,
    val centerAccessoryEndPadding: Dp,
    val centerAccessoryGap: Dp,
    val centerLockSize: Dp,
    val centerLockPadding: Dp,
    val genderRatioBadgeHorizontalPadding: Dp,
    val genderRatioBadgeVerticalPadding: Dp,
    val genderRatioBadgeFontSize: TextUnit,
    val teamRowPadding: Dp,
    val titleGap: Dp,
    val detailGap: Dp,
    val actionGap: Dp,
    val actionButtonHeight: Dp,
    val titleFontSize: androidx.compose.ui.unit.TextUnit,
    val titleLineHeight: androidx.compose.ui.unit.TextUnit,
) {
    companion object {
        /**
         * Derive field layout metrics from the measured available field height.
         *
         * @param fieldHeight The height available for the full field diagram.
         */
        fun fromFieldHeight(fieldHeight: Dp): FieldLayoutMetrics {
            val centerHeight = (fieldHeight.value * 0.20f)
                .coerceIn(58f, 110f)
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
            val titleFontSizeValue = (teamRowHeight.value * 0.14f).coerceIn(20f, 28f)
            val centerAccessoryEndPadding = (centerHeight.value * 0.13f).dp.coerceIn(8.dp, 10.dp)
            val centerLockSize = (centerHeight.value * 0.46f).dp.coerceIn(26.dp, 36.dp)
            val centerPullIndicatorStartPadding =
                (centerAccessoryEndPadding.value + centerLockSize.value * 0.15f)
                    .dp
                    .coerceIn(10.dp, 16.dp)
            return FieldLayoutMetrics(
                fieldHeight = fieldHeight,
                teamRowHeight = teamRowHeight,
                centerHeight = centerHeight,
                centerVerticalPadding = (centerHeight.value * 0.05f).dp.coerceIn(4.dp, 12.dp),
                centerPullIndicatorStartPadding = centerPullIndicatorStartPadding,
                centerAccessoryEndPadding = centerAccessoryEndPadding,
                centerAccessoryGap = (centerHeight.value * 0.04f).dp.coerceIn(0.dp, 4.dp),
                centerLockSize = centerLockSize,
                centerLockPadding = (centerHeight.value * 0.075f).dp.coerceIn(3.dp, 6.dp),
                genderRatioBadgeHorizontalPadding = (centerHeight.value * 0.075f).dp
                    .coerceIn(4.dp, 6.dp),
                genderRatioBadgeVerticalPadding = (centerHeight.value * 0.025f).dp
                    .coerceIn(0.dp, 2.dp),
                genderRatioBadgeFontSize = (centerHeight.value * 0.20f).coerceIn(12.5f, 18f).sp,
                teamRowPadding = rowPadding,
                titleGap = (teamRowHeight.value * 0.035f).dp.coerceIn(4.dp, 10.dp),
                detailGap = detailGap,
                actionGap = (contentHeight.value * 0.035f).dp.coerceIn(4.dp, 7.dp),
                actionButtonHeight = (teamRowHeight.value * 0.17f).dp.coerceIn(28.dp, 34.dp),
                titleFontSize = titleFontSizeValue.sp,
                titleLineHeight = (titleFontSizeValue + 4f).sp,
            )
        }
    }
}

/**
 * Draw the field as top/bottom end zones plus a center strip for pull direction and controls.
 *
 * @param state The live game state to render.
 * @param interactionsEnabled Whether team action controls should be enabled.
 * @param timeoutEnabled Whether timeout handling is available in the current state.
 * @param showPullIndicator Whether the center strip should show pull direction.
 * @param metrics The precomputed field layout metrics.
 * @param centerContent The live action or unlock content rendered in the center strip.
 * @param onLock Callback locking the live screen from the field strip.
 * @param onGoal Callback receiving the team that scored.
 * @param onTimeout Callback receiving the team requesting timeout.
 * @param onTimeViolation Callback receiving the team committing a time violation.
 * @param onPullViolation Callback receiving the team with a pull violation.
 * @param onCards Callback opening the card workflow.
 * @param onTechnicalFoul Callback opening the technical-foul workflow.
 * @param onTeamInfo Callback opening the coach/captain information for a team.
 */
@Composable
internal fun FieldSketchCard(
    state: GameState,
    interactionsEnabled: Boolean,
    timeoutEnabled: Boolean,
    showPullIndicator: Boolean,
    metrics: FieldLayoutMetrics,
    centerContent: @Composable () -> Unit,
    onLock: () -> Unit,
    onGoal: (TeamId) -> Unit,
    onTimeout: (TeamId) -> Unit,
    onTimeViolation: (TeamId) -> Unit,
    onPullViolation: (TeamId) -> Unit,
    onCards: (TeamId) -> Unit,
    onTechnicalFoul: (TeamId) -> Unit,
    onTeamInfo: (TeamId) -> Unit,
) {
    // Translate the game's pulling orientation into the currently displayed top/bottom slots.
    val topEnd = state.topDisplayedEnd
    val bottomEnd = topEnd.flip()
    val topSlot = if (state.pullingFromEnd == topEnd) {
        state.pullingTeam
    } else {
        state.pullingTeam.flip()
    }
    val bottomSlot = topSlot.flip()
    val topTeam = state.teamFor(topSlot)
    val bottomTeam = state.teamFor(bottomSlot)
    val pullFrom = state.pullingFromEnd
    val currentGenderRatio = state.currentGenderRatio()
    val ratioChoosingTeam = state.ratioChoosingTeam()

    // Draw the top team row, center field area, and bottom team row in that order.
    Card(
        modifier = Modifier
            .height(metrics.fieldHeight)
            .testTag("live-field-diagram"),
        colors = CardDefaults.cardColors(
            containerColor = EmphasizedDarkNeutralColor,
        ),
        shape = PanelShape,
        border = BorderStroke(1.dp, FieldBorderColor),
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Top end zone/team row.
            EndZonePanel(
                teamId = topSlot,
                team = topTeam,
                cardPoints = state.teamCardTotal(topSlot),
                timeoutsRemaining = state.timeoutsRemaining(topSlot),
                timeoutEnabled = timeoutEnabled,
                background = topTeam.accent,
                shape = TopEndZoneShape,
                interactionsEnabled = interactionsEnabled,
                choosesGenderRatio = ratioChoosingTeam == topSlot,
                timeViolationEnabled = state.canAssessTimeViolation(),
                pullViolationEnabled = state.canRecordPullViolation(topSlot),
                pullViolationType = state.pullViolationTypeFor(topSlot),
                fieldEndName = state.fieldEndDisplayName(topEnd),
                fieldEndLabelAtTop = true,
                metrics = metrics,
                onGoal = { onGoal(topSlot) },
                onTimeout = { onTimeout(topSlot) },
                onTimeViolation = { onTimeViolation(topSlot) },
                onPullViolation = { onPullViolation(topSlot) },
                onCards = { onCards(topSlot) },
                onTechnicalFoul = { onTechnicalFoul(topSlot) },
                onTeamInfo = { onTeamInfo(topSlot) },
            )
            // Center field strip with pull direction and the main central control.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(metrics.centerHeight)
                    .background(FieldPullAreaColor)
                    .padding(horizontal = 16.dp, vertical = metrics.centerVerticalPadding),
            ) {
                if (showPullIndicator) {
                    PullDirectionIndicator(
                        pullingFromEnd = pullFrom,
                        topDisplayedEnd = topEnd,
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .padding(start = metrics.centerPullIndicatorStartPadding),
                    )
                    Column(
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .padding(end = metrics.centerAccessoryEndPadding),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(metrics.centerAccessoryGap),
                    ) {
                        FieldCenterLockIcon(
                            onClick = onLock,
                            size = metrics.centerLockSize,
                            padding = metrics.centerLockPadding,
                        )
                        currentGenderRatio?.let { ratio ->
                            GenderRatioStatusBadge(
                                ratio = ratio,
                                horizontalPadding = metrics.genderRatioBadgeHorizontalPadding,
                                verticalPadding = metrics.genderRatioBadgeVerticalPadding,
                                fontSize = metrics.genderRatioBadgeFontSize,
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier.align(Alignment.Center),
                    contentAlignment = Alignment.Center,
                ) {
                    centerContent()
                }
            }
            // Bottom end zone/team row.
            EndZonePanel(
                teamId = bottomSlot,
                team = bottomTeam,
                cardPoints = state.teamCardTotal(bottomSlot),
                timeoutsRemaining = state.timeoutsRemaining(bottomSlot),
                timeoutEnabled = timeoutEnabled,
                background = bottomTeam.accent,
                shape = BottomEndZoneShape,
                interactionsEnabled = interactionsEnabled,
                choosesGenderRatio = ratioChoosingTeam == bottomSlot,
                timeViolationEnabled = state.canAssessTimeViolation(),
                pullViolationEnabled = state.canRecordPullViolation(bottomSlot),
                pullViolationType = state.pullViolationTypeFor(bottomSlot),
                fieldEndName = state.fieldEndDisplayName(bottomEnd),
                fieldEndLabelAtTop = false,
                metrics = metrics,
                onGoal = { onGoal(bottomSlot) },
                onTimeout = { onTimeout(bottomSlot) },
                onTimeViolation = { onTimeViolation(bottomSlot) },
                onPullViolation = { onPullViolation(bottomSlot) },
                onCards = { onCards(bottomSlot) },
                onTechnicalFoul = { onTechnicalFoul(bottomSlot) },
                onTeamInfo = { onTeamInfo(bottomSlot) },
            )
        }
    }
}

/// Render the compact game-rules affordance.
@Composable
private fun GameRulesIcon(
    onClick: () -> Unit,
    size: Dp,
    padding: Dp,
    tag: String,
) {
    Icon(
        imageVector = Icons.Outlined.Description,
        contentDescription = null,
        tint = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.75f),
        modifier = Modifier
            .testTag(tag)
            .size(size)
            .semantics { contentDescription = "Game rules" }
            .clickable(onClick = onClick)
            .padding(padding),
    )
}

/// Render the compact center-field lock affordance.
@Composable
private fun FieldCenterLockIcon(
    onClick: () -> Unit,
    size: Dp,
    padding: Dp,
) {
    Icon(
        imageVector = Icons.Filled.Lock,
        contentDescription = null,
        tint = Color.Black,
        modifier = Modifier
            .testTag("live-center-lock")
            .size(size)
            .semantics { contentDescription = "Lock" }
            .clickable(onClick = onClick)
            .padding(padding),
    )
}

/**
 * Render one team row on the field, with score/state info and the main live actions.
 *
 * @param teamId The team represented by this end-zone row.
 * @param team The live team state to display.
 * @param cardPoints The derived team card-point total.
 * @param timeoutsRemaining The derived timeout count remaining in this half.
 * @param timeoutEnabled Whether timeout handling is available in the current state.
 * @param background The team-color background for the row.
 * @param interactionsEnabled Whether live action buttons should be enabled.
 * @param choosesGenderRatio Whether this team chooses the mixed gender ratio for this point.
 * @param timeViolationEnabled Whether this team can record a time violation for this pull.
 * @param pullViolationEnabled Whether this team can still record its pull violation for this pull.
 * @param pullViolationType The pull-violation type represented by this team's field button.
 * @param fieldEndName Display name for the field end represented by this row.
 * @param fieldEndLabelAtTop Whether the field-end label belongs in the top-right corner.
 * @param metrics The measured layout metrics for compact or roomy phone heights.
 * @param onGoal Callback recording a goal for this team.
 * @param onTimeout Callback charging a timeout to this team.
 * @param onTimeViolation Callback recording a time violation for this team.
 * @param onPullViolation Callback recording this team's pull violation.
 * @param onCards Callback opening the card workflow.
 * @param onTechnicalFoul Callback opening the technical-foul workflow.
 * @param onTeamInfo Callback opening coach/captain information for this team.
 */
@Composable
private fun EndZonePanel(
    teamId: TeamId,
    team: TeamState,
    cardPoints: Int,
    timeoutsRemaining: Int,
    timeoutEnabled: Boolean,
    background: Color,
    shape: RoundedCornerShape,
    interactionsEnabled: Boolean,
    choosesGenderRatio: Boolean,
    timeViolationEnabled: Boolean,
    pullViolationEnabled: Boolean,
    pullViolationType: PullViolationType,
    fieldEndName: String,
    fieldEndLabelAtTop: Boolean,
    metrics: FieldLayoutMetrics,
    onGoal: () -> Unit,
    onTimeout: () -> Unit,
    onTimeViolation: () -> Unit,
    onPullViolation: () -> Unit,
    onCards: () -> Unit,
    onTechnicalFoul: () -> Unit,
    onTeamInfo: () -> Unit,
) {
    val titleTextStyle = MaterialTheme.typography.titleLarge.copy(
        fontSize = metrics.titleFontSize,
        lineHeight = metrics.titleLineHeight,
    )
    val hasHeaderTrailingLabel = fieldEndLabelAtTop || choosesGenderRatio
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(metrics.teamRowHeight)
            .clip(shape)
            .background(background)
            .padding(metrics.teamRowPadding),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.spacedBy(metrics.detailGap.coerceAtLeast(3.dp)),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(metrics.titleGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = team.score.toString(),
                    color = team.content,
                    style = titleTextStyle,
                    fontWeight = FontWeight.Bold,
                )
                if (hasHeaderTrailingLabel) {
                    BoxWithConstraints(modifier = Modifier.weight(1f)) {
                        val contentWidth = (maxWidth - metrics.titleGap).coerceAtLeast(0.dp)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(metrics.titleGap),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            TeamHeaderIdentity(
                                teamId = teamId,
                                team = team,
                                titleTextStyle = titleTextStyle,
                                modifier = Modifier.widthIn(max = contentWidth * 0.75f),
                                onTeamInfo = onTeamInfo,
                            )
                            if (fieldEndLabelAtTop) {
                                FieldEndCornerLabel(
                                    name = fieldEndName,
                                    contentColor = team.content,
                                    modifier = Modifier.weight(1f),
                                )
                            } else {
                                GenderRatioChooserText(
                                    contentColor = team.content,
                                    modifier = Modifier.weight(1f),
                                )
                            }
                        }
                    }
                } else {
                    TeamHeaderIdentity(
                        teamId = teamId,
                        team = team,
                        titleTextStyle = titleTextStyle,
                        modifier = Modifier.weight(1f),
                        onTeamInfo = onTeamInfo,
                    )
                }
            }
            TeamActionGrid(
                teamId = teamId,
                team = team,
                cardPoints = cardPoints,
                timeoutsRemaining = timeoutsRemaining,
                timeoutEnabled = timeoutEnabled,
                interactionsEnabled = interactionsEnabled,
                timeViolationEnabled = timeViolationEnabled,
                pullViolationEnabled = pullViolationEnabled,
                pullViolationType = pullViolationType,
                metrics = metrics,
                onGoal = onGoal,
                onTimeout = onTimeout,
                onTimeViolation = onTimeViolation,
                onPullViolation = onPullViolation,
                onCards = onCards,
                onTechnicalFoul = onTechnicalFoul,
            )
            if (fieldEndLabelAtTop && choosesGenderRatio) {
                Spacer(modifier = Modifier.weight(1f))
                GenderRatioChooserLabel(
                    contentColor = team.content,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        if (!fieldEndLabelAtTop) {
            FieldEndCornerLabel(
                name = fieldEndName,
                contentColor = team.content,
                modifier = Modifier.align(Alignment.BottomEnd),
            )
        }
    }
}

/**
 * Render the team-name cluster in a field-row header.
 *
 * @param teamId Team id used for the optional info-button test tag.
 * @param team The team whose name and optional info button are shown.
 * @param titleTextStyle Text style shared with the field-row score.
 * @param modifier Modifier controlling the cluster's available width.
 * @param onTeamInfo Callback opening coach/captain details for this team.
 */
@Composable
private fun TeamHeaderIdentity(
    teamId: TeamId,
    team: TeamState,
    titleTextStyle: androidx.compose.ui.text.TextStyle,
    modifier: Modifier,
    onTeamInfo: () -> Unit,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = team.name,
            color = team.content,
            modifier = Modifier.weight(1f, fill = false),
            style = titleTextStyle,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (team.hasCoachOrCaptainInfo()) {
            FieldInfoButton(
                teamName = team.name,
                contentColor = team.content,
                onClick = onTeamInfo,
                tag = "live-${teamId.name}-team-info",
            )
        }
    }
}

/**
 * Render a compact non-interactive status badge for the current mixed gender ratio.
 *
 * @param ratio The ratio applying to this point.
 * @param horizontalPadding Horizontal badge padding.
 * @param verticalPadding Vertical badge padding.
 * @param fontSize Badge label font size.
 */
@Composable
private fun GenderRatioStatusBadge(
    ratio: GenderRatio,
    horizontalPadding: Dp,
    verticalPadding: Dp,
    fontSize: TextUnit,
) {
    val background = when (ratio) {
        GenderRatio.FOUR_MEN_THREE_WOMEN -> FourMenThreeWomenBadgeColor
        GenderRatio.FOUR_WOMEN_THREE_MEN -> FourWomenThreeMenBadgeColor
    }
    val border = when (ratio) {
        GenderRatio.FOUR_MEN_THREE_WOMEN -> FourMenThreeWomenBadgeBorderColor
        GenderRatio.FOUR_WOMEN_THREE_MEN -> FourWomenThreeMenBadgeBorderColor
    }
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(background)
            .border(BorderStroke(1.dp, border), RoundedCornerShape(4.dp))
            .padding(
                horizontal = horizontalPadding,
                vertical = verticalPadding,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = ratio.displayText,
            color = GenderRatioBadgeTextColor,
            style = MaterialTheme.typography.titleMedium.copy(
                fontSize = fontSize,
                lineHeight = fontSize,
            ),
            fontWeight = FontWeight.Bold,
            maxLines = 1,
        )
    }
}

/**
 * Render the marker for the team choosing the gender ratio.
 *
 * @param contentColor Text color matching the team row.
 * @param modifier Modifier applied by the caller.
 */
@Composable
private fun GenderRatioChooserLabel(
    contentColor: Color,
    modifier: Modifier,
) {
    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.End,
    ) {
        Text(
            text = "Chooses gender ratio",
            color = contentColor,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Render the gender-ratio chooser marker inline with other team-row corner labels.
 *
 * @param contentColor Text color matching the team row.
 * @param modifier Modifier applied by the caller.
 */
@Composable
private fun GenderRatioChooserText(
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = "Chooses gender ratio",
        color = contentColor,
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.End,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * Render the clustered live actions for one team.
 *
 * @param teamId The team whose test tags identify each action.
 * @param team The live team state used for count labels.
 * @param cardPoints The team's card total for misconduct thresholds.
 * @param timeoutsRemaining Timeouts remaining in the current half.
 * @param timeoutEnabled Whether timeout handling is available in the current state.
 * @param interactionsEnabled Whether the observer can press live actions.
 * @param timeViolationEnabled Whether this team may record a time violation for this pull sequence.
 * @param pullViolationEnabled Whether this team may record a pull violation for this pull sequence.
 * @param pullViolationType The pull-violation type represented by this team's field button.
 * @param metrics The measured field layout metrics.
 * @param onGoal Callback recording a goal for this team.
 * @param onTimeout Callback charging a timeout to this team.
 * @param onTimeViolation Callback recording a time violation for this team.
 * @param onPullViolation Callback recording the team's pull violation.
 * @param onCards Callback opening the card workflow.
 * @param onTechnicalFoul Callback opening the technical-foul workflow.
 * @param modifier Optional layout modifier.
 */
@Composable
private fun TeamActionGrid(
    teamId: TeamId,
    team: TeamState,
    cardPoints: Int,
    timeoutsRemaining: Int,
    timeoutEnabled: Boolean,
    interactionsEnabled: Boolean,
    timeViolationEnabled: Boolean,
    pullViolationEnabled: Boolean,
    pullViolationType: PullViolationType,
    metrics: FieldLayoutMetrics,
    onGoal: () -> Unit,
    onTimeout: () -> Unit,
    onTimeViolation: () -> Unit,
    onPullViolation: () -> Unit,
    onCards: () -> Unit,
    onTechnicalFoul: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val pullViolationLabel = pullViolationType.fieldActionLabel(team)
    val cardLabel = countedActionLabel("Card", cardPoints)
    val techLabel = countedActionLabel("Tech", team.technicalFouls)
    val timeViolationLabel = team.timeViolationFieldActionLabel()
    val timeoutLabel = "TO ($timeoutsRemaining)"
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val buttonTextStyle = MaterialTheme.typography.labelMedium
    val gap = metrics.actionGap
    fun measuredButtonWidth(firstLabel: String, secondLabel: String? = null): Dp {
        var labelWidthPx = textMeasurer
            .measure(AnnotatedString(firstLabel), style = buttonTextStyle)
            .size
            .width
        if (secondLabel != null) {
            labelWidthPx = maxOf(
                labelWidthPx,
                textMeasurer.measure(AnnotatedString(secondLabel), style = buttonTextStyle)
                    .size
                    .width,
            )
        }
        return with(density) { labelWidthPx.toDp() } + 20.dp
    }

    val panelPadding = 4.dp
    val goalWidth = measuredButtonWidth("Goal")
    val middleWidth = measuredButtonWidth(timeViolationLabel, pullViolationLabel)
    val rightWidth = measuredButtonWidth(cardLabel, techLabel)
    val timeoutWidth = measuredButtonWidth(timeoutLabel)
    val panelWidth = goalWidth + middleWidth + rightWidth + timeoutWidth +
        panelPadding * 2f + gap * 3f
    Row(
        modifier = modifier
            .width(panelWidth)
            .clip(PanelShape)
            .background(FieldActionPanelColor)
            .padding(panelPadding),
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        FieldControlButton(
            label = "Goal",
            width = goalWidth,
            height = metrics.actionButtonHeight * 2f + gap,
            enabled = interactionsEnabled,
            containerColor = GoalButtonColor,
            contentColor = Color.White,
            tag = "live-${teamId.name}-goal",
            onClick = onGoal,
        )
        Column(
            modifier = Modifier.width(middleWidth),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            FieldControlButton(
                label = timeViolationLabel,
                fullWidth = true,
                height = metrics.actionButtonHeight,
                enabled = interactionsEnabled && timeViolationEnabled,
                containerColor = FieldNeutralButtonColor,
                tag = "live-${teamId.name}-time-violation",
                onClick = onTimeViolation,
            )
            FieldControlButton(
                label = pullViolationLabel,
                fullWidth = true,
                height = metrics.actionButtonHeight,
                enabled = interactionsEnabled && pullViolationEnabled,
                containerColor = FieldNeutralButtonColor,
                tag = "live-${teamId.name}-pull-violation",
                onClick = onPullViolation,
            )
        }
        Column(
            modifier = Modifier.width(rightWidth),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            FieldControlButton(
                label = cardLabel,
                fullWidth = true,
                height = metrics.actionButtonHeight,
                enabled = interactionsEnabled,
                containerColor = CardButtonColor,
                tag = "live-${teamId.name}-card",
                onClick = onCards,
            )
            FieldControlButton(
                label = techLabel,
                fullWidth = true,
                height = metrics.actionButtonHeight,
                enabled = interactionsEnabled,
                containerColor = TechButtonColor,
                tag = "live-${teamId.name}-tech",
                onClick = onTechnicalFoul,
            )
        }
        FieldControlButton(
            label = timeoutLabel,
            width = timeoutWidth,
            height = metrics.actionButtonHeight * 2f + gap,
            enabled = interactionsEnabled && timeoutEnabled,
            containerColor = TimeoutButtonColor,
            contentColor = Color.Black,
            tag = "live-${teamId.name}-timeout",
            onClick = onTimeout,
        )
    }
}

/// Format the compact field-button label for a pull-violation type.
internal fun PullViolationType.fieldActionLabel(team: TeamState): String {
    val pullViolationCount = team.pullViolationCount()
    return when (this) {
        PullViolationType.OFFSIDES -> countedActionLabel("Offsides", pullViolationCount)
        PullViolationType.FALSE_START -> countedActionLabel("False start", pullViolationCount)
        PullViolationType.MAJORITY_PULL -> countedActionLabel(
            // Completeness only: majority pull is selected inside the offsides dialog.
            "Majority pull",
            pullViolationCount,
        )
    }
}

/**
 * Render one compact field-end name in a team-section corner.
 *
 * @param name The field-end name to display.
 * @param contentColor Color that contrasts with the team section.
 * @param modifier Modifier applied by the caller.
 */
@Composable
private fun FieldEndCornerLabel(
    name: String,
    contentColor: Color,
    modifier: Modifier = Modifier,
) {
    Text(
        text = name,
        color = contentColor,
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        textAlign = TextAlign.End,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * Render the center-field arrow showing which end the pull comes from.
 *
 * @param pullingFromEnd The field end occupied by the pulling team.
 * @param topDisplayedEnd The field end currently displayed at the top of the field.
 * @param modifier Optional layout modifier for the indicator column.
 */
@Composable
private fun PullDirectionIndicator(
    pullingFromEnd: FieldEnd,
    topDisplayedEnd: FieldEnd,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(
        modifier = modifier
            .width(64.dp)
            .fillMaxHeight(),
    ) {
        val arrowHeight = (maxHeight - 22.dp).coerceIn(24.dp, 46.dp)
        val arrowWidth = (arrowHeight.value * 34f / 46f).dp
        Column(
            modifier = Modifier.fillMaxHeight(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceEvenly,
        ) {
            if (pullingFromEnd == topDisplayedEnd) {
                PullDirectionLabel()
            }
            PullDirectionArrow(
                pointsTowardBottom = pullingFromEnd == topDisplayedEnd,
                width = arrowWidth,
                height = arrowHeight,
            )
            if (pullingFromEnd == topDisplayedEnd.flip()) {
                PullDirectionLabel()
            }
        }
    }
}

/**
 * Draw the pull-direction arrow.
 *
 * @param pointsTowardBottom Whether the arrow should point toward the displayed bottom end.
 * @param width Width for the arrow icon.
 * @param height Height for the arrow icon.
 */
@Composable
private fun PullDirectionArrow(
    pointsTowardBottom: Boolean,
    width: Dp,
    height: Dp,
) {
    Icon(
        imageVector = if (pointsTowardBottom) {
            Icons.Filled.South
        } else {
            Icons.Filled.North
        },
        contentDescription = null,
        modifier = Modifier
            .width(width)
            .height(height),
        tint = Color.Black,
    )
}

/// Render the `Pull` label beside the pull-direction arrow.
@Composable
private fun PullDirectionLabel() {
    Text(
        "Pull",
        color = Color.Black,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.Bold,
        maxLines = 1,
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
 * @param statusMessage Message to show when no countdown or countdown action occupies the row.
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
    statusMessage: String? = null,
    height: Dp,
) {
    val displayCountdown = countdown ?: ActiveCountdownDisplay("Pull in", Duration.ZERO, null)
    val titleFontSize = (height.value * 0.4f).coerceIn(22f, 28f).sp
    val labelFontSize = (height.value * 0.21f).coerceIn(12f, 14f).sp
    val statusFontSize = (height.value * 0.23f).coerceIn(14f, 16f).sp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(height),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (misconductCountdownAction != null) {
                BigActionButton(
                    label = "Start misconduct countdown",
                    enabled = enabled,
                    fullWidth = true,
                    containerColor = DarkNeutralColor,
                    tag = "live-start-misconduct-countdown",
                    onClick = misconductCountdownAction.onStart,
                )
            } else if (expiredPullActions != null) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                ) {
                    BigActionButton(
                        label = "Restart countdown",
                        enabled = enabled,
                        fullWidth = true,
                        containerColor = DarkNeutralColor,
                        tag = "live-restart-pull-countdown",
                        onClick = expiredPullActions.onRestartPullCountdown,
                    )
                }
            } else if (countdown != null) {
                Row(
                    modifier = Modifier.weight(1f),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = displayCountdown.label,
                        modifier = Modifier.weight(1f, fill = false),
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = titleFontSize),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = formatDuration(displayCountdown.remaining),
                        style = MaterialTheme.typography.titleLarge.copy(fontSize = titleFontSize),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
                Row(
                    modifier = Modifier.padding(start = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    PauseResumeButton(
                        isPaused = displayCountdown.isPaused,
                        enabled = enabled,
                        onClick = onTogglePaused,
                    )
                    AdjustButton(
                        label = "-5",
                        enabled = enabled,
                        containerColor = DarkNeutralColor,
                    ) {
                        onAdjust(-5)
                    }
                    AdjustButton(
                        label = "+5",
                        enabled = enabled,
                        containerColor = DarkNeutralColor,
                    ) {
                        onAdjust(5)
                    }
                }
            } else if (statusMessage != null) {
                Text(
                    text = statusMessage,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("live-countdown-status-message"),
                    style = MaterialTheme.typography.bodyMedium.copy(
                        fontSize = statusFontSize,
                        lineHeight = (statusFontSize.value + 2f).sp,
                    ),
                    fontWeight = FontWeight.SemiBold,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                )
            }
        }
        Text(
            text = if (countdown != null && displayCountdown.isPaused) {
                "Paused"
            } else if (countdown != null && displayCountdown.nextCue != null) {
                val cue = displayCountdown.nextCue
                "Next cue at ${formatDuration(cue.countdownTime)} - ${cue.message}"
            } else {
                ""
            },
            style = MaterialTheme.typography.labelMedium.copy(fontSize = labelFontSize),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/**
 * Callbacks shown after undoing an automatic start-point transition.
 *
 * @param onRestartPullCountdown Callback for restarting the pull countdown.
 */
internal data class ExpiredPullActions(
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
            val followOn = betweenPointsDisplay(
                pullingFromEnd = pullingFromEnd,
                sequenceStart = countdown.targetEpoch,
                now = now,
                promptTarget = pullPromptTarget,
            )
            val followOnCountdown = buildBetweenPointsCountdown(
                pullingFromEnd = pullingFromEnd,
                sequenceStart = countdown.targetEpoch,
                promptTarget = pullPromptTarget,
            )
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
