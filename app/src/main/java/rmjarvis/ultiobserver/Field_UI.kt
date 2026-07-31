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
import androidx.compose.material.icons.filled.East  // These are the arrows for Pull direction
import androidx.compose.material.icons.filled.North
import androidx.compose.material.icons.filled.South
import androidx.compose.material.icons.filled.West
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
import java.time.ZoneId

private val TopEndZoneShape = RoundedCornerShape(topStart = 8.dp, topEnd = 8.dp)
private val BottomEndZoneShape = RoundedCornerShape(bottomStart = 8.dp, bottomEnd = 8.dp)
private val LeftEndZoneShape = RoundedCornerShape(topStart = 8.dp, bottomStart = 8.dp)
private val RightEndZoneShape = RoundedCornerShape(topEnd = 8.dp, bottomEnd = 8.dp)

/**
 * Render the live-field unlock slider.
 *
 * @param onUnlock Callback invoked once the observer completes the required slide gesture.
 * @param modifier Layout modifier controlling the slider's available width.
 */
@Composable
internal fun FieldUnlockControl(
    onUnlock: () -> Unit,
    modifier: Modifier,
) {
    SlideToConfirmControl(
        instructionText = "Slide right to unlock",
        trackText = "Unlock",
        testTag = "live-unlock-slider",
        onConfirmed = onUnlock,
        modifier = modifier,
        textColor = Color.Black,
        trackColor = UnlockSliderColor,
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
 * @param now Current epoch millis for the local clock time to display.
 * @param capStatus The next cap status, or null when all caps are passed or irrelevant.
 * @param allocatedHeight Vertical height allocated to this status line by its parent layout.
 * @param modifier Modifier controlling the status line's share of its parent.
 * @param pushCapToEnd Whether the cap and rules action should occupy the remaining row width.
 * @param onRulesReference Callback opening the rules reference.
 */
@Composable
internal fun StatusLine(
    now: Long,
    capStatus: CapStatus?,
    allocatedHeight: Dp,
    modifier: Modifier,
    pushCapToEnd: Boolean,
    onRulesReference: () -> Unit,
) {
    val clockText = formatClockTime(localTimeFromEpoch(now, ZoneId.systemDefault()))
    val capLabel = capStatus?.label ?: "Caps passed"
    val capCountdown = capStatus?.let { formatDuration(it.remaining) }
    val capText = listOfNotNull(capLabel, capCountdown).joinToString(" ")
    val preferredClockFontSize = (allocatedHeight.value * 0.68f).coerceIn(28f, 36f).sp
    val preferredCapFontSize = (allocatedHeight.value * 0.42f).coerceIn(18f, 22f).sp
    val preferredClockStyle = MaterialTheme.typography.headlineMedium.copy(
        fontSize = preferredClockFontSize,
        fontWeight = FontWeight.Bold,
    )
    val preferredCapStyle = MaterialTheme.typography.bodyLarge.copy(
        fontSize = preferredCapFontSize,
        lineHeight = preferredCapFontSize,
        fontWeight = FontWeight.SemiBold,
    )
    BoxWithConstraints(
        modifier = modifier.height(allocatedHeight),
    ) {
        val textMeasurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val maxWidthPx = with(density) { maxWidth.roundToPx() }
        val preferredRulesIconSize = 28.dp
        val preferredIconWidth = with(density) { preferredRulesIconSize.roundToPx() }
        val minimumRulesIconSize = 20.dp
        val minimumIconWidth = with(density) { minimumRulesIconSize.roundToPx() }
        val minimumClockCapGap = with(density) { 6.dp.roundToPx() }
        val minimumFontScale = (10f / preferredCapFontSize.value).coerceAtMost(1f)
        fun measuredStatusWidth(fontScale: Float): Int {
            val clockWidth = textMeasurer.measure(
                AnnotatedString(clockText),
                style = preferredClockStyle.copy(
                    fontSize = preferredClockFontSize * fontScale,
                ),
            ).size.width
            val capWidth = textMeasurer.measure(
                AnnotatedString(capText),
                style = preferredCapStyle.copy(
                    fontSize = preferredCapFontSize * fontScale,
                    lineHeight = preferredCapFontSize * fontScale,
                ),
            ).size.width
            val rulesIconWidth = (preferredIconWidth * fontScale)
                .toInt()
                .coerceAtLeast(minimumIconWidth)
            return clockWidth + capWidth + rulesIconWidth + minimumClockCapGap
        }
        val responsiveFontScale = when {
            measuredStatusWidth(1f) <= maxWidthPx -> 1f
            measuredStatusWidth(minimumFontScale) >= maxWidthPx -> minimumFontScale
            else -> {
                var lowerScale = minimumFontScale
                var upperScale = 1f
                repeat(8) {
                    val candidateScale = (lowerScale + upperScale) / 2f
                    if (measuredStatusWidth(candidateScale) <= maxWidthPx) {
                        lowerScale = candidateScale
                    } else {
                        upperScale = candidateScale
                    }
                }
                lowerScale
            }
        }
        val responsiveClockFontSize = preferredClockFontSize * responsiveFontScale
        val responsiveCapFontSize = preferredCapFontSize * responsiveFontScale
        val rulesIconSize = (preferredRulesIconSize * responsiveFontScale)
            .coerceAtLeast(minimumRulesIconSize)
        val rulesIconPadding = 4.dp * (rulesIconSize.value / preferredRulesIconSize.value)
        val iconWidth = with(density) { rulesIconSize.roundToPx() }
        val clockStyle = preferredClockStyle.copy(fontSize = responsiveClockFontSize)
        val capStyle = preferredCapStyle.copy(
            fontSize = responsiveCapFontSize,
            lineHeight = responsiveCapFontSize,
        )
        val clockWidth = textMeasurer.measure(
            AnnotatedString(clockText),
            style = clockStyle,
        ).size.width
        val capWidth = textMeasurer.measure(
            AnnotatedString(capText),
            style = capStyle,
        ).size.width
        val availableSpacing = (
            maxWidthPx - clockWidth - capWidth - iconWidth
            ).coerceAtLeast(0)
        val preferredCapIconGap = with(density) { 2.dp.roundToPx() }
        val capIconGapPx = (availableSpacing - minimumClockCapGap)
            .coerceIn(0, preferredCapIconGap)
        val clockCapGapPx = (availableSpacing - capIconGapPx)
            .coerceIn(
                minimumClockCapGap,
                with(density) { 30.dp.roundToPx() },
            )
        val capIconGap = with(density) { capIconGapPx.toDp() }
        val clockCapGap = with(density) { clockCapGapPx.toDp() }
        Row(
            modifier = Modifier.fillMaxSize(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = clockText,
                style = clockStyle,
                maxLines = 1,
            )
            Row(
                modifier = Modifier
                    .weight(1f, fill = pushCapToEnd)
                    .padding(start = clockCapGap),
                horizontalArrangement = Arrangement.spacedBy(capIconGap),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                StatusCapText(
                    label = capLabel,
                    countdown = capCountdown,
                    modifier = Modifier.weight(1f, fill = pushCapToEnd),
                    preferredFontSize = responsiveCapFontSize,
                    textAlign = if (pushCapToEnd) TextAlign.End else TextAlign.Start,
                )
                GameRulesIcon(
                    onClick = onRulesReference,
                    size = rulesIconSize,
                    padding = rulesIconPadding,
                    tag = "live-game-rules",
                )
            }
        }
    }
}

/**
 * Render cap status at the supplied font size, preserving the complete countdown when the label
 * must ellipsize.
 *
 * @param label The cap name, which may ellipsize under horizontal pressure.
 * @param countdown The cap countdown, which is always displayed in full, or null after all caps.
 * @param preferredFontSize The normal status-line cap font size.
 * @param modifier Modifier applied by the caller.
 */
@Composable
private fun StatusCapText(
    label: String,
    countdown: String?,
    preferredFontSize: TextUnit,
    modifier: Modifier,
    textAlign: TextAlign = TextAlign.End,
) {
    Box(
        modifier = modifier,
        contentAlignment = if (textAlign == TextAlign.Start) {
            Alignment.CenterStart
        } else {
            Alignment.CenterEnd
        },
    ) {
        val style = MaterialTheme.typography.bodyLarge.copy(
            fontSize = preferredFontSize,
            lineHeight = preferredFontSize,
            fontWeight = FontWeight.SemiBold,
        )
        Row(
            modifier = if (textAlign == TextAlign.End) {
                Modifier.fillMaxWidth()
            } else {
                Modifier
            },
            horizontalArrangement = if (textAlign == TextAlign.Start) {
                Arrangement.Start
            } else {
                Arrangement.End
            },
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = label,
                modifier = Modifier.weight(1f, fill = false),
                style = style,
                textAlign = textAlign,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (countdown != null) {
                Text(
                    text = " $countdown",
                    style = style,
                    maxLines = 1,
                )
            }
        }
    }
}

/// Responsive measurements used to draw the live field view.
internal data class PortraitFieldLayoutMetrics(
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
        fun fromFieldHeight(fieldHeight: Dp): PortraitFieldLayoutMetrics {
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
            return PortraitFieldLayoutMetrics(
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

/// Responsive measurements used to draw the landscape live field.
internal data class LandscapeFieldLayoutMetrics(
    val fieldHeight: Dp,
    val teamPanelPadding: Dp,
    val titleGap: Dp,
    val detailGap: Dp,
    val actionGap: Dp,
    val actionButtonHeight: Dp,
    val availableActionGridHeight: Dp,
    val titleFontSize: TextUnit,
    val titleLineHeight: TextUnit,
    val centerPadding: Dp,
    val centerLockSize: Dp,
    val centerLockPadding: Dp,
    val genderRatioBadgeHorizontalPadding: Dp,
    val genderRatioBadgeVerticalPadding: Dp,
    val genderRatioBadgeFontSize: TextUnit,
) {
    companion object {
        /**
         * Derive landscape field metrics from the measured available field height.
         *
         * @param fieldHeight Height available below the landscape status band.
         */
        fun fromFieldHeight(fieldHeight: Dp): LandscapeFieldLayoutMetrics {
            val titleFontSizeValue = (fieldHeight.value * 0.09f).coerceIn(18f, 24f)
            val titleLineHeightValue = titleFontSizeValue + 3f
            val teamPanelPadding = (fieldHeight.value * 0.025f).dp.coerceIn(4.dp, 8.dp)
            val detailGap = (fieldHeight.value * 0.018f).dp.coerceIn(3.dp, 6.dp)
            val actionGap = (fieldHeight.value * 0.016f).dp.coerceIn(3.dp, 5.dp)
            val preferredActionButtonHeight =
                (fieldHeight.value * 0.17f).dp.coerceIn(28.dp, 34.dp)
            val fieldEndLabelClearance = 18.dp
            val actionPanelVerticalPadding = 8.dp
            val availableActionGridHeight = (
                fieldHeight.value -
                    teamPanelPadding.value * 2f -
                    titleLineHeightValue -
                    detailGap.value -
                    fieldEndLabelClearance.value
            ).coerceAtLeast(0f)
            val maximumActionButtonHeight = (
                availableActionGridHeight -
                    actionGap.value -
                    actionPanelVerticalPadding.value
            ).coerceAtLeast(0f).dp / 2f
            return LandscapeFieldLayoutMetrics(
                fieldHeight = fieldHeight,
                teamPanelPadding = teamPanelPadding,
                titleGap = (fieldHeight.value * 0.018f).dp.coerceIn(4.dp, 7.dp),
                detailGap = detailGap,
                actionGap = actionGap,
                actionButtonHeight = minOf(
                    preferredActionButtonHeight,
                    maximumActionButtonHeight,
                ),
                availableActionGridHeight = availableActionGridHeight.dp,
                titleFontSize = titleFontSizeValue.sp,
                titleLineHeight = titleLineHeightValue.sp,
                centerPadding = (fieldHeight.value * 0.025f).dp.coerceIn(4.dp, 8.dp),
                centerLockSize = (fieldHeight.value * 0.14f).dp.coerceIn(28.dp, 36.dp),
                centerLockPadding = 4.dp,
                genderRatioBadgeHorizontalPadding = 5.dp,
                genderRatioBadgeVerticalPadding = 1.dp,
                genderRatioBadgeFontSize = (fieldHeight.value * 0.065f).coerceIn(13f, 17f).sp,
            )
        }
    }
}

/**
 * Draw the field as top/bottom end zones plus a center strip for pull direction and controls.
 *
 * @param state The live game state to render.
 * @param showAbbaRatioAsSequence Whether ABBA field badges should show sequence shorthand.
 * @param genderRatioBadgeColorArgb Color lookup for each concrete gender ratio.
 * @param interactionsEnabled Whether team action controls should be enabled.
 * @param timeoutEnabled Whether timeout handling is available in the current state.
 * @param metrics The precomputed field layout metrics.
 * @param centerContent The live action rendered in the center strip.
 * @param centerOverlayContent Locked-state content centered across the complete field.
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
internal fun PortraitFieldSketchCard(
    state: GameState,
    showAbbaRatioAsSequence: Boolean,
    genderRatioBadgeColorArgb: (GenderRatio) -> Long,
    interactionsEnabled: Boolean,
    timeoutEnabled: Boolean,
    metrics: PortraitFieldLayoutMetrics,
    centerContent: @Composable () -> Unit,
    centerOverlayContent: @Composable () -> Unit,
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
        Box(modifier = Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Top end zone/team row.
                PortraitEndZonePanel(
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
                    fieldEndName = state.fieldEndDisplayName(
                        topEnd,
                        ActiveGameOrientation.PORTRAIT,
                    ),
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
                    PortraitPullDirectionIndicator(
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
                        verticalArrangement = Arrangement.spacedBy(
                            metrics.centerAccessoryGap
                        ),
                    ) {
                        currentGenderRatio?.let { ratio ->
                            GenderRatioStatusBadge(
                                background = Color(genderRatioBadgeColorArgb(ratio)),
                                label = state.currentGenderRatioBadgeText(
                                    showAbbaRatioAsSequence
                                ),
                                horizontalPadding = metrics.genderRatioBadgeHorizontalPadding,
                                verticalPadding = metrics.genderRatioBadgeVerticalPadding,
                                fontSize = metrics.genderRatioBadgeFontSize,
                            )
                        }
                        FieldCenterLockIcon(
                            onClick = onLock,
                            size = metrics.centerLockSize,
                            padding = metrics.centerLockPadding,
                        )
                    }
                    Box(
                        modifier = Modifier.align(Alignment.Center),
                        contentAlignment = Alignment.Center,
                    ) {
                        centerContent()
                    }
                }
                // Bottom end zone/team row.
                PortraitEndZonePanel(
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
                    fieldEndName = state.fieldEndDisplayName(
                        bottomEnd,
                        ActiveGameOrientation.PORTRAIT,
                    ),
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(metrics.teamRowHeight + metrics.centerHeight)
                    .padding(
                        start = 16.dp,
                        end = 16.dp,
                        bottom = metrics.centerVerticalPadding,
                    )
                    .align(Alignment.TopCenter),
                contentAlignment = Alignment.BottomCenter,
            ) {
                centerOverlayContent()
            }
        }
    }
}

/**
 * Draw the field as left/right end zones plus a center region for pull direction and controls.
 *
 * The persisted far end is the initial left end, while the persisted near end is the initial
 * right end. `topDisplayedEnd` remains the stored display-flip value and is interpreted here as
 * the currently displayed left end. The ordinary live action stays in the center region, while
 * locked-state content is overlaid across the complete field.
 */
@Composable
internal fun LandscapeFieldSketchCard(
    state: GameState,
    showAbbaRatioAsSequence: Boolean,
    genderRatioBadgeColorArgb: (GenderRatio) -> Long,
    interactionsEnabled: Boolean,
    timeoutEnabled: Boolean,
    metrics: LandscapeFieldLayoutMetrics,
    centerButtonFontSize: TextUnit,
    centerContent: @Composable () -> Unit,
    centerOverlayContent: @Composable () -> Unit,
    onLock: () -> Unit,
    onGoal: (TeamId) -> Unit,
    onTimeout: (TeamId) -> Unit,
    onTimeViolation: (TeamId) -> Unit,
    onPullViolation: (TeamId) -> Unit,
    onCards: (TeamId) -> Unit,
    onTechnicalFoul: (TeamId) -> Unit,
    onTeamInfo: (TeamId) -> Unit,
) {
    val leftEnd = state.topDisplayedEnd
    val rightEnd = leftEnd.flip()
    val leftSlot = if (state.pullingFromEnd == leftEnd) {
        state.pullingTeam
    } else {
        state.pullingTeam.flip()
    }
    val rightSlot = leftSlot.flip()
    val leftTeam = state.teamFor(leftSlot)
    val rightTeam = state.teamFor(rightSlot)
    val currentGenderRatio = state.currentGenderRatio()
    val ratioChoosingTeam = state.ratioChoosingTeam()
    val leftCardPoints = state.teamCardTotal(leftSlot)
    val rightCardPoints = state.teamCardTotal(rightSlot)
    val leftTimeoutsRemaining = state.timeoutsRemaining(leftSlot)
    val rightTimeoutsRemaining = state.timeoutsRemaining(rightSlot)
    val leftPullViolationType = state.pullViolationTypeFor(leftSlot)
    val rightPullViolationType = state.pullViolationTypeFor(rightSlot)
    val naturalLeftActionGridWidths = teamActionGridWidths(
        team = leftTeam,
        cardPoints = leftCardPoints,
        timeoutsRemaining = leftTimeoutsRemaining,
        pullViolationType = leftPullViolationType,
        actionButtonHeight = metrics.actionButtonHeight,
        gap = metrics.actionGap,
    )
    val naturalRightActionGridWidths = teamActionGridWidths(
        team = rightTeam,
        cardPoints = rightCardPoints,
        timeoutsRemaining = rightTimeoutsRemaining,
        pullViolationType = rightPullViolationType,
        actionButtonHeight = metrics.actionButtonHeight,
        gap = metrics.actionGap,
    )
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val longestCenterButtonWordWidth = with(density) {
        textMeasurer.measure(
            AnnotatedString("Continue"),
            style = MaterialTheme.typography.labelLarge.copy(fontSize = centerButtonFontSize),
        ).size.width.toDp()
    }
    val minimumCenterWidth =
        longestCenterButtonWordWidth + 16.dp + metrics.centerPadding * 2f

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .height(metrics.fieldHeight)
            .testTag("live-field-diagram"),
        colors = CardDefaults.cardColors(
            containerColor = EmphasizedDarkNeutralColor,
        ),
        shape = PanelShape,
        border = BorderStroke(1.dp, FieldBorderColor),
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
            val preferredCenterWidth = maxWidth * 0.72f / 2.72f
            val wideRequiredTeamWidth = maxOf(
                naturalLeftActionGridWidths.panel,
                naturalRightActionGridWidths.panel,
            ) + metrics.teamPanelPadding * 2f
            val wideCenterWidth =
                (maxWidth - wideRequiredTeamWidth * 2f).coerceAtLeast(0.dp)
            val timeoutRowHeight =
                metrics.actionButtonHeight * 3f + metrics.actionGap * 2f + 8.dp
            val actionGridLayout = if (
                wideCenterWidth < minimumCenterWidth &&
                timeoutRowHeight <= metrics.availableActionGridHeight
            ) {
                TeamActionGridLayout.TIMEOUT_ROW
            } else {
                TeamActionGridLayout.WIDE
            }
            val naturalActionGridWidth = maxOf(
                naturalLeftActionGridWidths.widthFor(actionGridLayout),
                naturalRightActionGridWidths.widthFor(actionGridLayout),
            )
            val maximumActionGridWidth = (
                (maxWidth - minimumCenterWidth) / 2f - metrics.teamPanelPadding * 2f
                ).coerceAtLeast(0.dp)
            val actionGridScale = if (naturalActionGridWidth.value > 0f) {
                (maximumActionGridWidth.value / naturalActionGridWidth.value)
                    .coerceIn(0.85f, 1f)
            } else {
                1f
            }
            val leftActionGridWidths = naturalLeftActionGridWidths.scaled(
                actionGridScale,
                metrics.actionGap,
            )
            val rightActionGridWidths = naturalRightActionGridWidths.scaled(
                actionGridScale,
                metrics.actionGap,
            )
            val requiredTeamWidth = maxOf(
                leftActionGridWidths.widthFor(actionGridLayout),
                rightActionGridWidths.widthFor(actionGridLayout),
            ) + metrics.teamPanelPadding * 2f
            val pressuredCenterWidth =
                (maxWidth - requiredTeamWidth * 2f).coerceAtLeast(0.dp)
            val centerWidth = minOf(preferredCenterWidth, pressuredCenterWidth)
            val teamWidth = (maxWidth - centerWidth) / 2f
            Row(modifier = Modifier.fillMaxSize()) {
                LandscapeEndZonePanel(
                    isLeftPanel = true,
                    teamId = leftSlot,
                    team = leftTeam,
                    cardPoints = leftCardPoints,
                    timeoutsRemaining = leftTimeoutsRemaining,
                    timeoutEnabled = timeoutEnabled,
                    background = leftTeam.accent,
                    shape = LeftEndZoneShape,
                    interactionsEnabled = interactionsEnabled,
                    choosesGenderRatio = ratioChoosingTeam == leftSlot,
                    timeViolationEnabled = state.canAssessTimeViolation(),
                    pullViolationEnabled = state.canRecordPullViolation(leftSlot),
                    pullViolationType = leftPullViolationType,
                    actionGridWidths = leftActionGridWidths,
                    actionGridLayout = actionGridLayout,
                    fieldEndName = state.fieldEndDisplayName(
                        leftEnd,
                        ActiveGameOrientation.LANDSCAPE,
                    ),
                    metrics = metrics,
                    modifier = Modifier.width(teamWidth),
                    onGoal = { onGoal(leftSlot) },
                    onTimeout = { onTimeout(leftSlot) },
                    onTimeViolation = { onTimeViolation(leftSlot) },
                    onPullViolation = { onPullViolation(leftSlot) },
                    onCards = { onCards(leftSlot) },
                    onTechnicalFoul = { onTechnicalFoul(leftSlot) },
                    onTeamInfo = { onTeamInfo(leftSlot) },
                )
                Box(
                    modifier = Modifier
                        .width(centerWidth)
                        .fillMaxHeight()
                        .background(FieldPullAreaColor)
                        .padding(metrics.centerPadding),
                ) {
                    LandscapePullDirectionIndicator(
                        pullingFromEnd = state.pullingFromEnd,
                        leftDisplayedEnd = leftEnd,
                        modifier = Modifier.align(Alignment.TopCenter),
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .align(Alignment.BottomCenter),
                        horizontalArrangement = Arrangement.SpaceEvenly,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        currentGenderRatio?.let { ratio ->
                            GenderRatioStatusBadge(
                                background = Color(
                                    genderRatioBadgeColorArgb(ratio)
                                ),
                                label = state.currentGenderRatioBadgeText(
                                    showAbbaRatioAsSequence
                                ),
                                horizontalPadding =
                                    metrics.genderRatioBadgeHorizontalPadding,
                                verticalPadding = metrics.genderRatioBadgeVerticalPadding,
                                fontSize = metrics.genderRatioBadgeFontSize,
                            )
                        }
                        FieldCenterLockIcon(
                            onClick = onLock,
                            size = metrics.centerLockSize,
                            padding = metrics.centerLockPadding,
                        )
                    }
                    Box(
                        modifier = Modifier.align(Alignment.Center),
                        contentAlignment = Alignment.Center,
                    ) {
                        centerContent()
                    }
                }
                LandscapeEndZonePanel(
                    isLeftPanel = false,
                    teamId = rightSlot,
                    team = rightTeam,
                    cardPoints = rightCardPoints,
                    timeoutsRemaining = rightTimeoutsRemaining,
                    timeoutEnabled = timeoutEnabled,
                    background = rightTeam.accent,
                    shape = RightEndZoneShape,
                    interactionsEnabled = interactionsEnabled,
                    choosesGenderRatio = ratioChoosingTeam == rightSlot,
                    timeViolationEnabled = state.canAssessTimeViolation(),
                    pullViolationEnabled = state.canRecordPullViolation(rightSlot),
                    pullViolationType = rightPullViolationType,
                    actionGridWidths = rightActionGridWidths,
                    actionGridLayout = actionGridLayout,
                    fieldEndName = state.fieldEndDisplayName(
                        rightEnd,
                        ActiveGameOrientation.LANDSCAPE,
                    ),
                    metrics = metrics,
                    modifier = Modifier.width(teamWidth),
                    onGoal = { onGoal(rightSlot) },
                    onTimeout = { onTimeout(rightSlot) },
                    onTimeViolation = { onTimeViolation(rightSlot) },
                    onPullViolation = { onPullViolation(rightSlot) },
                    onCards = { onCards(rightSlot) },
                    onTechnicalFoul = { onTechnicalFoul(rightSlot) },
                    onTeamInfo = { onTeamInfo(rightSlot) },
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp)
                    .align(Alignment.Center),
                contentAlignment = Alignment.Center,
            ) {
                centerOverlayContent()
            }
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
 * Render one landscape team section with its header, six actions, and field-end status.
 */
@Composable
private fun LandscapeEndZonePanel(
    isLeftPanel: Boolean,
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
    actionGridWidths: TeamActionGridWidths,
    actionGridLayout: TeamActionGridLayout,
    fieldEndName: String,
    metrics: LandscapeFieldLayoutMetrics,
    modifier: Modifier,
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
    Box(
        modifier = modifier
            .fillMaxHeight()
            .clip(shape)
            .background(background)
            .padding(metrics.teamPanelPadding),
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.spacedBy(metrics.detailGap),
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
                TeamHeaderIdentity(
                    teamId = teamId,
                    team = team,
                    titleTextStyle = titleTextStyle,
                    modifier = Modifier.weight(1f),
                    onTeamInfo = onTeamInfo,
                )
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
                widths = actionGridWidths,
                layout = actionGridLayout,
                actionButtonHeight = metrics.actionButtonHeight,
                gap = metrics.actionGap,
                onGoal = onGoal,
                onTimeout = onTimeout,
                onTimeViolation = onTimeViolation,
                onPullViolation = onPullViolation,
                onCards = onCards,
                onTechnicalFoul = onTechnicalFoul,
            )
        }
        FieldEndCornerLabel(
            name = fieldEndName,
            contentColor = team.content,
            modifier = Modifier.align(
                if (isLeftPanel) Alignment.BottomStart else Alignment.BottomEnd
            ),
        )
        if (choosesGenderRatio) {
            GenderRatioChooserText(
                contentColor = team.content,
                modifier = Modifier.align(
                    if (isLeftPanel) Alignment.BottomEnd else Alignment.BottomStart
                ),
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
private fun PortraitEndZonePanel(
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
    metrics: PortraitFieldLayoutMetrics,
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
    val actionGridWidths = teamActionGridWidths(
        team = team,
        cardPoints = cardPoints,
        timeoutsRemaining = timeoutsRemaining,
        pullViolationType = pullViolationType,
        actionButtonHeight = metrics.actionButtonHeight,
        gap = metrics.actionGap,
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
                widths = actionGridWidths,
                actionButtonHeight = metrics.actionButtonHeight,
                gap = metrics.actionGap,
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
 * @param background The observer-selected badge background color.
 * @param label The text shown inside the badge.
 * @param horizontalPadding Horizontal badge padding.
 * @param verticalPadding Vertical badge padding.
 * @param fontSize Badge label font size.
 */
@Composable
private fun GenderRatioStatusBadge(
    background: Color,
    label: String,
    horizontalPadding: Dp,
    verticalPadding: Dp,
    fontSize: TextUnit,
) {
    val contentColor = readableContentColor(background)
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(4.dp))
            .background(background)
            .border(BorderStroke(1.dp, contentColor.copy(alpha = 0.7f)), RoundedCornerShape(4.dp))
            .padding(
                horizontal = horizontalPadding,
                vertical = verticalPadding,
            ),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = contentColor,
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

/// Resolved widths and shared font size for the team action complex.
private data class TeamActionGridWidths(
    val goal: Dp,
    val middle: Dp,
    val right: Dp,
    val timeout: Dp,
    val fullTimeout: Dp,
    val panel: Dp,
    val timeoutRowPanel: Dp,
    val fontSize: TextUnit,
)

/// Available arrangements for the six team actions in landscape.
private enum class TeamActionGridLayout {
    WIDE,
    TIMEOUT_ROW,
}

/// Return the complete panel width for one of the landscape action-grid arrangements.
private fun TeamActionGridWidths.widthFor(layout: TeamActionGridLayout): Dp {
    return if (layout == TeamActionGridLayout.TIMEOUT_ROW) timeoutRowPanel else panel
}

/// Smoothly compress the action columns while preserving their relative widths.
private fun TeamActionGridWidths.scaled(
    scale: Float,
    gap: Dp,
): TeamActionGridWidths {
    val scaledGoal = goal * scale
    val scaledMiddle = middle * scale
    val scaledRight = right * scale
    val scaledTimeout = timeout * scale
    val scaledFullTimeout = fullTimeout * scale
    val primaryColumnsWidth = scaledGoal + scaledMiddle + scaledRight + gap * 2f
    return TeamActionGridWidths(
        goal = scaledGoal,
        middle = scaledMiddle,
        right = scaledRight,
        timeout = scaledTimeout,
        fullTimeout = scaledFullTimeout,
        panel = primaryColumnsWidth + scaledTimeout + 8.dp + gap,
        timeoutRowPanel = maxOf(primaryColumnsWidth, scaledFullTimeout) + 8.dp,
        fontSize = fontSize * scale,
    )
}

/// Measure the complete team action complex at the largest shared text size that fits its rows.
@Composable
private fun teamActionGridWidths(
    team: TeamState,
    cardPoints: Int,
    timeoutsRemaining: Int,
    pullViolationType: PullViolationType,
    actionButtonHeight: Dp,
    gap: Dp,
): TeamActionGridWidths {
    val pullViolationLabel = pullViolationType.fieldActionLabel(team)
    val cardLabel = countedActionLabel("Card", cardPoints)
    val techLabel = countedActionLabel("Tech", team.technicalFouls)
    val timeViolationLabel = team.timeViolationFieldActionLabel()
    val timeoutLabel = "TO ($timeoutsRemaining)"
    val fullTimeoutLabel = "Timeout ($timeoutsRemaining)"
    val textMeasurer = rememberTextMeasurer()
    val density = LocalDensity.current
    val preferredTextStyle = MaterialTheme.typography.labelMedium
    val preferredTextHeight = textMeasurer.measure(
        text = AnnotatedString("Goal"),
        style = preferredTextStyle,
        maxLines = 1,
        softWrap = false,
    ).size.height
    val availableTextHeight = with(density) {
        (actionButtonHeight - 4.dp).coerceAtLeast(0.dp).toPx()
    }
    val heightScale = if (preferredTextHeight > 0) {
        (availableTextHeight / preferredTextHeight).coerceIn(0f, 1f)
    } else {
        1f
    }
    val fontSize = preferredTextStyle.fontSize * heightScale
    val buttonTextStyle = preferredTextStyle.copy(
        fontSize = fontSize,
        lineHeight = fontSize,
    )

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

    val goalWidth = measuredButtonWidth("Goal")
    val middleWidth = measuredButtonWidth(timeViolationLabel, pullViolationLabel)
    val rightWidth = measuredButtonWidth(cardLabel, techLabel)
    val timeoutWidth = measuredButtonWidth(timeoutLabel)
    val fullTimeoutWidth = measuredButtonWidth(fullTimeoutLabel)
    val primaryColumnsWidth = goalWidth + middleWidth + rightWidth + gap * 2f
    return TeamActionGridWidths(
        goal = goalWidth,
        middle = middleWidth,
        right = rightWidth,
        timeout = timeoutWidth,
        fullTimeout = fullTimeoutWidth,
        panel = primaryColumnsWidth + timeoutWidth + 8.dp + gap,
        timeoutRowPanel = maxOf(primaryColumnsWidth, fullTimeoutWidth) + 8.dp,
        fontSize = fontSize,
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
 * @param widths Intrinsic widths measured for the four action columns and complete panel.
 * @param layout Whether Timeout remains the fourth column or occupies a full-width third row.
 * @param actionButtonHeight Height of each compact action row.
 * @param gap Spacing between action rows and columns.
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
    widths: TeamActionGridWidths,
    layout: TeamActionGridLayout = TeamActionGridLayout.WIDE,
    actionButtonHeight: Dp,
    gap: Dp,
    onGoal: () -> Unit,
    onTimeout: () -> Unit,
    onTimeViolation: () -> Unit,
    onPullViolation: () -> Unit,
    onCards: () -> Unit,
    onTechnicalFoul: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val panelModifier = modifier
        .width(
            if (layout == TeamActionGridLayout.TIMEOUT_ROW) {
                widths.timeoutRowPanel
            } else {
                widths.panel
            }
        )
        .clip(PanelShape)
        .background(FieldActionPanelColor)
        .padding(4.dp)
    if (layout == TeamActionGridLayout.TIMEOUT_ROW) {
        Column(
            modifier = panelModifier,
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            TeamActionPrimaryColumns(
                teamId = teamId,
                team = team,
                cardPoints = cardPoints,
                interactionsEnabled = interactionsEnabled,
                timeViolationEnabled = timeViolationEnabled,
                pullViolationEnabled = pullViolationEnabled,
                pullViolationType = pullViolationType,
                widths = widths,
                actionButtonHeight = actionButtonHeight,
                gap = gap,
                onGoal = onGoal,
                onTimeViolation = onTimeViolation,
                onPullViolation = onPullViolation,
                onCards = onCards,
                onTechnicalFoul = onTechnicalFoul,
            )
            FieldControlButton(
                label = "Timeout ($timeoutsRemaining)",
                fullWidth = true,
                height = actionButtonHeight,
                fontSize = widths.fontSize,
                enabled = interactionsEnabled && timeoutEnabled,
                containerColor = TimeoutButtonColor,
                contentColor = Color.Black,
                tag = "live-${teamId.name}-timeout",
                onClick = onTimeout,
            )
        }
    } else {
        Row(
            modifier = panelModifier,
            horizontalArrangement = Arrangement.spacedBy(gap),
        ) {
            TeamActionPrimaryColumns(
                teamId = teamId,
                team = team,
                cardPoints = cardPoints,
                interactionsEnabled = interactionsEnabled,
                timeViolationEnabled = timeViolationEnabled,
                pullViolationEnabled = pullViolationEnabled,
                pullViolationType = pullViolationType,
                widths = widths,
                actionButtonHeight = actionButtonHeight,
                gap = gap,
                onGoal = onGoal,
                onTimeViolation = onTimeViolation,
                onPullViolation = onPullViolation,
                onCards = onCards,
                onTechnicalFoul = onTechnicalFoul,
            )
            FieldControlButton(
                label = "TO ($timeoutsRemaining)",
                width = widths.timeout,
                height = actionButtonHeight * 2f + gap,
                fontSize = widths.fontSize,
                enabled = interactionsEnabled && timeoutEnabled,
                containerColor = TimeoutButtonColor,
                contentColor = Color.Black,
                tag = "live-${teamId.name}-timeout",
                onClick = onTimeout,
            )
        }
    }
}

/// Render Goal and the two paired action columns shared by both landscape arrangements.
@Composable
private fun TeamActionPrimaryColumns(
    teamId: TeamId,
    team: TeamState,
    cardPoints: Int,
    interactionsEnabled: Boolean,
    timeViolationEnabled: Boolean,
    pullViolationEnabled: Boolean,
    pullViolationType: PullViolationType,
    widths: TeamActionGridWidths,
    actionButtonHeight: Dp,
    gap: Dp,
    onGoal: () -> Unit,
    onTimeViolation: () -> Unit,
    onPullViolation: () -> Unit,
    onCards: () -> Unit,
    onTechnicalFoul: () -> Unit,
) {
    val pullViolationLabel = pullViolationType.fieldActionLabel(team)
    val cardLabel = countedActionLabel("Card", cardPoints)
    val techLabel = countedActionLabel("Tech", team.technicalFouls)
    val timeViolationLabel = team.timeViolationFieldActionLabel()
    Row(
        modifier = Modifier.width(
            widths.goal + widths.middle + widths.right + gap * 2f
        ),
        horizontalArrangement = Arrangement.spacedBy(gap),
    ) {
        FieldControlButton(
            label = "Goal",
            width = widths.goal,
            height = actionButtonHeight * 2f + gap,
            fontSize = widths.fontSize,
            enabled = interactionsEnabled,
            containerColor = GoalButtonColor,
            contentColor = Color.White,
            tag = "live-${teamId.name}-goal",
            onClick = onGoal,
        )
        Column(
            modifier = Modifier.width(widths.middle),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            FieldControlButton(
                label = timeViolationLabel,
                fullWidth = true,
                height = actionButtonHeight,
                fontSize = widths.fontSize,
                enabled = interactionsEnabled && timeViolationEnabled,
                containerColor = FieldNeutralButtonColor,
                tag = "live-${teamId.name}-time-violation",
                onClick = onTimeViolation,
            )
            FieldControlButton(
                label = pullViolationLabel,
                fullWidth = true,
                height = actionButtonHeight,
                fontSize = widths.fontSize,
                enabled = interactionsEnabled && pullViolationEnabled,
                containerColor = FieldNeutralButtonColor,
                tag = "live-${teamId.name}-pull-violation",
                onClick = onPullViolation,
            )
        }
        Column(
            modifier = Modifier.width(widths.right),
            verticalArrangement = Arrangement.spacedBy(gap),
        ) {
            FieldControlButton(
                label = cardLabel,
                fullWidth = true,
                height = actionButtonHeight,
                fontSize = widths.fontSize,
                enabled = interactionsEnabled,
                containerColor = CardButtonColor,
                tag = "live-${teamId.name}-card",
                onClick = onCards,
            )
            FieldControlButton(
                label = techLabel,
                fullWidth = true,
                height = actionButtonHeight,
                fontSize = widths.fontSize,
                enabled = interactionsEnabled,
                containerColor = TechButtonColor,
                tag = "live-${teamId.name}-tech",
                onClick = onTechnicalFoul,
            )
        }
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
 * Render the horizontal pull label and arrow for the landscape field.
 *
 * @param pullingFromEnd Field end occupied by the pulling team.
 * @param leftDisplayedEnd Field end currently displayed on the left.
 */
@Composable
private fun LandscapePullDirectionIndicator(
    pullingFromEnd: FieldEnd,
    leftDisplayedEnd: FieldEnd,
    modifier: Modifier = Modifier,
) {
    val pullsFromLeft = pullingFromEnd == leftDisplayedEnd
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (pullsFromLeft) {
            PullDirectionLabel()
        }
        Icon(
            imageVector = if (pullsFromLeft) {
                Icons.Filled.East
            } else {
                Icons.Filled.West
            },
            contentDescription = null,
            modifier = Modifier
                .width(42.dp)
                .height(28.dp),
            tint = Color.Black,
        )
        if (!pullsFromLeft) {
            PullDirectionLabel()
        }
    }
}

/**
 * Render the center-field arrow showing which end the pull comes from.
 *
 * @param pullingFromEnd The field end occupied by the pulling team.
 * @param topDisplayedEnd The field end currently displayed at the top of the field.
 * @param modifier Optional layout modifier for the indicator column.
 */
@Composable
private fun PortraitPullDirectionIndicator(
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
 * @param waterBreakAction Callback for a water-break prompt, or null when disabled.
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
    waterBreakAction: (() -> Unit)? = null,
    onTogglePaused: () -> Unit,
    expiredPullActions: ExpiredPullActions? = null,
    misconductCountdownAction: MisconductCountdownAction? = null,
    statusMessage: String? = null,
    height: Dp,
    modifier: Modifier = Modifier,
) {
    val displayCountdown = countdown ?: ActiveCountdownDisplay("Pull in", Duration.ZERO, null)
    val fontScale = LocalDensity.current.fontScale
    val titleFontSize = (
        (height.value * 0.4f).coerceIn(22f, 28f) / fontScale
        ).sp
    val labelFontSize = (
        (height.value * 0.21f).coerceIn(12f, 14f) / fontScale
        ).sp
    val controlHeight = (
        34f - (fontScale - 1f).coerceAtLeast(0f) * (8f / 0.15f)
        )
        .coerceIn(26f, 34f)
        .dp
    val controlGap = controlHeight * (6f / 34f)
    val controlStartPadding = controlHeight * (8f / 34f)
    val statusFontSize = (height.value * 0.23f).coerceIn(14f, 16f).sp
    Column(
        modifier = modifier
            .fillMaxWidth()
            .height(height),
        verticalArrangement = Arrangement.spacedBy(1.dp),
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
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = titleFontSize,
                            lineHeight = titleFontSize,
                        ),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = formatDuration(displayCountdown.remaining),
                        style = MaterialTheme.typography.titleLarge.copy(
                            fontSize = titleFontSize,
                            lineHeight = titleFontSize,
                        ),
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                    )
                }
                Row(
                    modifier = Modifier.padding(start = controlStartPadding),
                    horizontalArrangement = Arrangement.spacedBy(controlGap),
                ) {
                    if (waterBreakAction != null) {
                        WaterBreakButton(
                            enabled = enabled,
                            height = controlHeight,
                            onClick = waterBreakAction,
                        )
                    }
                    PauseResumeButton(
                        isPaused = displayCountdown.isPaused,
                        enabled = enabled,
                        height = controlHeight,
                        onClick = onTogglePaused,
                    )
                    AdjustButton(
                        label = "-5",
                        enabled = enabled,
                        containerColor = DarkNeutralColor,
                        height = controlHeight,
                    ) {
                        onAdjust(-5)
                    }
                    AdjustButton(
                        label = "+5",
                        enabled = enabled,
                        containerColor = DarkNeutralColor,
                        height = controlHeight,
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
            style = MaterialTheme.typography.labelMedium.copy(
                fontSize = labelFontSize,
                lineHeight = labelFontSize,
            ),
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
                rules = rules,
            )
            val followOnCountdown = buildBetweenPointsCountdown(
                pullingFromEnd = pullingFromEnd,
                sequenceStart = countdown.targetEpoch,
                promptTarget = pullPromptTarget,
                rules = rules,
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
