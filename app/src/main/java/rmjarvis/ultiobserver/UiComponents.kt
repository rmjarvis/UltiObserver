package rmjarvis.ultiobserver

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.input.TextFieldLineLimits
import androidx.compose.foundation.text.input.rememberTextFieldState
import androidx.compose.foundation.text.input.setTextAndPlaceCursorAtEnd
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AlertDialogDefaults
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonColors
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalMinimumInteractiveComponentSize
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.ProvideTextStyle
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldColors
import androidx.compose.material3.TextButton
import androidx.compose.material3.VerticalDivider
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToDown
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import com.github.skydoves.colorpicker.compose.HsvColorPicker
import com.github.skydoves.colorpicker.compose.rememberColorPickerController

private const val KEYBOARD_DIALOG_HEIGHT_FRACTION = 0.60f

/// Width profiles for responsive dialogs.
internal enum class DialogWidthProfile {
    COMPACT,
    MODERATE,
    WIDE,
}

internal val NavigationButtonShape: Shape
    @Composable get() = ButtonDefaults.shape
private val AdjustShape = RoundedCornerShape(12.dp)
private val BigActionButtonShape = RoundedCornerShape(16.dp)
internal val PanelShape = RoundedCornerShape(8.dp)
private val MenuButtonShape = RoundedCornerShape(12.dp)
private val ChoiceButtonShape = RoundedCornerShape(12.dp)
private val SectionCardShape = RoundedCornerShape(20.dp)
internal val GameRowShape = RoundedCornerShape(16.dp)

private val SelectedColor = Color(0xFF1565C0)
private val SelectedBorderColor = Color(0xFF0D47A1)
internal val YellowCardButtonColor = Color(0xFFFFD92F)
internal val RedCardButtonColor = Color(0xFFE64B3C)
internal val BlueCardButtonColor = Color(0xFF1976D2)
private val AvatarSelectedColor = Color(0xFFF2D23C)
internal val FieldBorderColor = Color(0xFF9E9A8D)
internal val FieldActionPanelColor = Color(0xCCFFFFFF)
internal val FieldPullAreaColor = Color(0xFFA8D5A0)
internal val GoalButtonColor = Color(0xFF2E7D32)
internal val CardButtonColor = Color(0xFFFDD835)
internal val TechButtonColor = Color(0xFFFFB74D)
internal val TimeoutButtonColor = Color(0xFF90CAF9)
internal val FieldNeutralButtonColor = Color(0xFFF7F2EA)
private val WaterBreakIconColor = Color(0xFF1976D2)
internal val UnlockSliderColor = Color(0xFFCBE6C6)
private val OptionBorderLightColor = Color(0xFFD8CBA7)
private val OptionDarkModeColor = Color(0xFF3B3522)
private val OptionBorderDarkColor = Color(0xFF9A8432)
/// Return the max body height for dialog bodies that can open the keyboard.
@Composable
internal fun keyboardDialogBodyMaxHeight(): Dp {
    return screenHeightFraction(KEYBOARD_DIALOG_HEIGHT_FRACTION)
}

@Composable
internal fun dialogBodyMaxHeight(): Dp {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.toFloat()
    val screenHeight = configuration.screenHeightDp.toFloat()
    val landscapeSurplus = (screenWidth - screenHeight).coerceAtLeast(0f)
    val aspectPressure = (
        screenWidth / screenHeight.coerceAtLeast(1f) - 1f
        ).coerceIn(0f, 1f)
    val heightFraction = if (landscapeSurplus > 0f) {
        0.68f - aspectPressure * 0.04f
    } else {
        KEYBOARD_DIALOG_HEIGHT_FRACTION
    }
    return screenHeightFraction(heightFraction)
}

/**
 * Render an alert dialog with responsive landscape width and fixed title and action regions.
 *
 * Portrait retains the platform dialog width. In landscape, compact dialogs stay near their
 * natural width, ordinary action dialogs receive moderate extra width, and prose-heavy or
 * multi-column dialogs can use more of the available horizontal space. Callers explicitly choose
 * the appropriate scrollable body region.
 */
@Composable
@OptIn(ExperimentalMaterial3Api::class)
internal fun ResponsiveAlertDialog(
    onDismissRequest: () -> Unit,
    title: @Composable () -> Unit,
    text: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    dismissButton: (@Composable () -> Unit)? = null,
    widthProfile: DialogWidthProfile = DialogWidthProfile.MODERATE,
) {
    val configuration = LocalConfiguration.current
    val screenWidth = configuration.screenWidthDp.toFloat()
    val screenHeight = configuration.screenHeightDp.toFloat()
    val landscapeSurplus = (screenWidth - screenHeight).coerceAtLeast(0f)
    val usesResponsiveLandscapeWidth = landscapeSurplus > 0f
    val availableWidth = (screenWidth - 48f).coerceAtLeast(280f)
    val preferredWidth = when (widthProfile) {
        DialogWidthProfile.COMPACT -> 340f + landscapeSurplus * 0.05f
        DialogWidthProfile.MODERATE -> 420f + landscapeSurplus * 0.20f
        DialogWidthProfile.WIDE -> 520f + landscapeSurplus * 0.50f
    }
    val dialogModifier = if (usesResponsiveLandscapeWidth) {
        modifier.widthIn(
            min = minOf(availableWidth, preferredWidth).dp,
            max = minOf(availableWidth, preferredWidth).dp,
        )
    } else {
        modifier
    }
    BasicAlertDialog(
        onDismissRequest = onDismissRequest,
        modifier = dialogModifier,
        properties = DialogProperties(
            usePlatformDefaultWidth = !usesResponsiveLandscapeWidth,
        ),
    ) {
        Surface(
            shape = AlertDialogDefaults.shape,
            color = AlertDialogDefaults.containerColor,
            tonalElevation = AlertDialogDefaults.TonalElevation,
        ) {
            Column(
                modifier = Modifier.padding(
                    start = 24.dp,
                    top = 24.dp,
                    end = 24.dp,
                    bottom = 4.dp,
                ),
            ) {
                CompositionLocalProvider(
                    LocalContentColor provides AlertDialogDefaults.titleContentColor,
                ) {
                    ProvideTextStyle(MaterialTheme.typography.headlineSmall) {
                        Box(modifier = Modifier.padding(bottom = 16.dp)) {
                            title()
                        }
                    }
                }
                CompositionLocalProvider(
                    LocalContentColor provides AlertDialogDefaults.textContentColor,
                ) {
                    ProvideTextStyle(MaterialTheme.typography.bodyMedium) {
                        Box(
                            modifier = Modifier
                                .weight(1f, fill = false)
                                .padding(bottom = 4.dp),
                        ) {
                            text()
                        }
                    }
                }
                CompositionLocalProvider(
                    LocalMinimumInteractiveComponentSize provides 40.dp,
                ) {
                    FlowRow(
                        modifier = Modifier.align(Alignment.End),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        dismissButton?.invoke()
                        confirmButton()
                    }
                }
            }
        }
    }
}

/// Return a modifier that parks initial dialog focus on a non-keyboard surface.
@Composable
internal fun dialogInitialFocusModifier(): Modifier {
    val focusRequester = remember { FocusRequester() }
    val clearFocusAndHideKeyboard = rememberClearFocusAndHideKeyboard()
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }
    return Modifier
        .clearFocusOnPointerDown(clearFocusAndHideKeyboard)
        .focusRequester(focusRequester)
        .focusable()
}

/**
 * Render a vertically scrollable dialog body with overflow fades.
 *
 * @param modifier Optional modifier for the outer scroll region.
 * @param maxHeight Maximum height before the body scrolls.
 * @param verticalArrangement Vertical spacing for the content column.
 * @param showBottomChevron Whether to show a down-chevron below the bottom fade.
 * @param content Dialog body content.
 */
@Composable
internal fun ScrollableDialogRegion(
    modifier: Modifier = Modifier,
    maxHeight: Dp = keyboardDialogBodyMaxHeight(),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(12.dp),
    showBottomChevron: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    val scrollState = rememberScrollState()
    val clearFocusAndHideKeyboard = rememberClearFocusAndHideKeyboard()
    val bottomChevronOffset = 22.dp
    // Dialog measurement can produce tiny scroll ranges even when no meaningful content is hidden.
    val overflowIndicatorThreshold = with(LocalDensity.current) { 24.dp.roundToPx() }
    val showScrollIndicators = scrollState.maxValue > overflowIndicatorThreshold
    Box(
        modifier = modifier
            .clearFocusOnPointerDown(clearFocusAndHideKeyboard)
            .heightIn(max = maxHeight),
    ) {
        Column(
            modifier = Modifier.verticalScroll(scrollState),
            verticalArrangement = verticalArrangement,
            content = content,
        )
        if (showScrollIndicators && scrollState.canScrollBackward) {
            DialogScrollFade(align = Alignment.TopCenter, topToBottom = true)
        }
        if (showScrollIndicators && scrollState.canScrollForward) {
            DialogScrollFade(align = Alignment.BottomCenter, topToBottom = false)
            if (showBottomChevron) {
                Icon(
                    imageVector = Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .offset(y = bottomChevronOffset)
                        .size(24.dp),
                )
            }
        }
    }
}

/**
 * Render two dialog columns that scroll together with shared overflow indicators.
 *
 * @param modifier Optional modifier for the outer scroll region.
 * @param maxHeight Maximum height before the body scrolls.
 * @param horizontalArrangement Horizontal spacing for the two-column row.
 * @param verticalArrangement Vertical spacing between the columns and optional footer.
 * @param columnArrangement Vertical spacing within each column.
 * @param showDivider Whether to draw a vertical divider between the columns.
 * @param showBottomChevron Whether to show a down-chevron below the bottom fade.
 * @param leftContent Content for the left column.
 * @param rightContent Content for the right column.
 * @param footer Optional full-width content below the columns.
 */
@Composable
internal fun TwoColumnDialogRegion(
    modifier: Modifier = Modifier,
    maxHeight: Dp = keyboardDialogBodyMaxHeight(),
    horizontalArrangement: Arrangement.Horizontal = Arrangement.spacedBy(12.dp),
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(12.dp),
    columnArrangement: Arrangement.Vertical = Arrangement.spacedBy(8.dp),
    showDivider: Boolean,
    showBottomChevron: Boolean = true,
    leftContent: @Composable ColumnScope.() -> Unit,
    rightContent: @Composable ColumnScope.() -> Unit,
    footer: (@Composable ColumnScope.() -> Unit)?,
) {
    val density = LocalDensity.current
    var columnsHeightPx by remember { mutableIntStateOf(0) }
    ScrollableDialogRegion(
        modifier = modifier,
        maxHeight = maxHeight,
        verticalArrangement = verticalArrangement,
        showBottomChevron = showBottomChevron,
    ) {
        Box {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .onSizeChanged { columnsHeightPx = it.height },
                horizontalArrangement = horizontalArrangement,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = columnArrangement,
                    content = leftContent,
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = columnArrangement,
                    content = rightContent,
                )
            }
            if (showDivider) {
                VerticalDivider(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .height(with(density) { columnsHeightPx.toDp() }),
                    color = MaterialTheme.colorScheme.outline,
                )
            }
        }
        footer?.invoke(this)
    }
}

/**
 * Render a non-scrollable dialog body that clears active text-entry focus when tapped.
 *
 * Use this for compact dialog bodies containing `TextEntry`; use `ScrollableDialogRegion` when
 * the content may need to scroll.
 *
 * @param verticalArrangement Vertical spacing for the content column.
 * @param content Dialog body content.
 */
@Composable
internal fun TextEntryDialogBody(
    verticalArrangement: Arrangement.Vertical = Arrangement.spacedBy(12.dp),
    content: @Composable ColumnScope.() -> Unit,
) {
    val clearFocusAndHideKeyboard = rememberClearFocusAndHideKeyboard()
    Column(
        modifier = Modifier
            .clearFocusOnPointerDown(clearFocusAndHideKeyboard)
            .fillMaxWidth(),
        verticalArrangement = verticalArrangement,
        content = content,
    )
}

/// Render one edge fade for a scrollable dialog body.
@Composable
private fun BoxScope.DialogScrollFade(
    align: Alignment,
    topToBottom: Boolean,
) {
    Box(
        modifier = Modifier
            .align(align)
            .fillMaxWidth()
            .height(36.dp)
            .background(
                Brush.verticalGradient(
                    colors = if (topToBottom) {
                        listOf(
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                            Color.Transparent,
                        )
                    } else {
                        listOf(
                            Color.Transparent,
                            MaterialTheme.colorScheme.surfaceContainerHigh,
                        )
                    }
                )
            ),
    )
}

/// Return this modifier with a test tag appended when one is requested.
private fun Modifier.withTag(tag: String?): Modifier {
    return if (tag == null) this else testTag(tag)
}

/**
 * Return a fraction of the current screen height in dp.
 *
 * @param fraction The portion of screen height to use, required to be in (0, 1].
 */
@Composable
private fun screenHeightFraction(fraction: Float): Dp {
    return (LocalConfiguration.current.screenHeightDp * fraction).dp
}

internal val TeamColorChoice.accent: Color
    get() {
        require(this != TeamColorChoice.CUSTOM) {
            "CUSTOM does not have a built-in accent color."
        }
        return Color(accentArgb)
    }

internal val TeamColorChoice.content: Color
    get() {
        require(this != TeamColorChoice.CUSTOM) {
            "CUSTOM does not have a built-in content color."
        }
        return Color(contentArgb)
    }

internal val TeamState.accent: Color
    get() = if (color == TeamColorChoice.CUSTOM) Color(customColorArgb!!) else color.accent

internal val TeamState.content: Color
    get() = if (color == TeamColorChoice.CUSTOM) readableContentColor(Color(customColorArgb!!)) else color.content

/// Return a black-or-white text color for custom jersey colors.
internal fun readableContentColor(background: Color): Color {
    return if (background.luminance() > 0.5f) {
        Color(0xFF1F1A17)
    } else {
        Color.White
    }
}

/**
 * Render the shared preset palette used for team and gender-ratio badge colors.
 *
 * @param selected The currently selected preset, or null for a custom color.
 * @param testTagPrefix Prefix used to build test tags for each color swatch.
 * @param onSelected Callback receiving the selected preset color.
 */
@Composable
internal fun ColorChoiceRow(
    selected: TeamColorChoice?,
    testTagPrefix: String,
    onSelected: (TeamColorChoice) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        TeamColorChoice.entries.filter { it != TeamColorChoice.CUSTOM }.forEach { colorChoice ->
            ColorSwatch(
                color = colorChoice.accent,
                selected = selected == colorChoice,
                testTag = "$testTagPrefix-${colorChoice.name}",
                modifier = Modifier
                    .weight(1f)
                    .height(28.dp),
                onClick = {
                    onSelected(colorChoice)
                },
            )
        }
    }
}

/**
 * Render a saved custom color as a second-row swatch.
 *
 * @param color The saved custom color.
 * @param selected Whether the custom color is currently selected.
 * @param testTag Test tag attached to the swatch.
 * @param onClick Callback invoked when the swatch is tapped.
 */
@Composable
internal fun CustomColorChoiceRow(
    color: Color,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    Row(modifier = Modifier.fillMaxWidth()) {
        ColorSwatch(
            color = color,
            selected = selected,
            testTag = testTag,
            modifier = Modifier.size(32.dp),
            onClick = onClick,
        )
    }
}

/**
 * Render the shared compact color-selection dialog.
 *
 * @param title Dialog title.
 * @param selectedPreset The currently selected preset, or null for a custom color.
 * @param customColorArgb Saved custom color to show below the presets, when one exists.
 * @param customColorSelected Whether the saved custom color is currently selected.
 * @param testTagPrefix Prefix used for palette, custom-swatch, and action test tags.
 * @param onPresetColorSelected Callback receiving the selected preset color.
 * @param onCustomColorSelected Callback receiving the selected saved custom color.
 * @param onMoreColors Callback opening the full custom color picker.
 * @param onDismiss Callback closing the dialog.
 */
@Composable
internal fun ColorChoiceDialog(
    title: String,
    selectedPreset: TeamColorChoice?,
    customColorArgb: Long?,
    customColorSelected: Boolean,
    testTagPrefix: String,
    onPresetColorSelected: (TeamColorChoice) -> Unit,
    onCustomColorSelected: (Long) -> Unit,
    onMoreColors: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                ColorChoiceRow(
                    selected = selectedPreset,
                    testTagPrefix = testTagPrefix,
                    onSelected = onPresetColorSelected,
                )
                customColorArgb?.let { colorArgb ->
                    CustomColorChoiceRow(
                        color = Color(colorArgb),
                        selected = customColorSelected,
                        testTag = "$testTagPrefix-custom",
                        onClick = {
                            onCustomColorSelected(colorArgb)
                        },
                    )
                }
            }
        },
        confirmButton = {
            ColorDialogActions(
                confirmText = "More colors",
                confirmTestTag = "$testTagPrefix-more",
                onCancel = onDismiss,
                onConfirm = onMoreColors,
            )
        },
    )
}

/**
 * Render the shared expanded custom-color picker dialog.
 *
 * @param title Dialog title.
 * @param initialColorArgb Opaque ARGB color used to initialize the picker.
 * @param testTagPrefix Prefix used for custom-picker test tags.
 * @param onColorSelected Callback receiving the applied opaque ARGB color.
 * @param onDismiss Callback closing the dialog without applying a new color.
 */
@Composable
internal fun CustomColorChoiceDialog(
    title: String,
    initialColorArgb: Long,
    testTagPrefix: String,
    onColorSelected: (Long) -> Unit,
    onDismiss: () -> Unit,
) {
    var customColor by remember(initialColorArgb) {
        mutableStateOf(Color(initialColorArgb))
    }

    fun useColorAndDismiss() {
        onColorSelected(customColor.toOpaqueArgbLong())
    }

    AlertDialog(
        onDismissRequest = {
            useColorAndDismiss()
        },
        title = { Text(title) },
        text = {
            CustomColorPicker(
                initialColor = customColor,
                testTagPrefix = testTagPrefix,
                onColorChange = {
                    customColor = it
                },
            )
        },
        confirmButton = {
            ColorDialogActions(
                confirmText = "Use this color",
                confirmTestTag = null,
                onCancel = onDismiss,
                onConfirm = ::useColorAndDismiss,
            )
        },
    )
}

@Composable
private fun ColorSwatch(
    color: Color,
    selected: Boolean,
    testTag: String,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Box(
        modifier = modifier
            .testTag(testTag)
            .background(
                color = if (selected) Color.Black else Color.Transparent,
                shape = RoundedCornerShape(6.dp),
            )
            .padding(if (selected) 1.dp else 0.dp)
            .background(
                color = if (selected) AvatarSelectedColor else Color.Transparent,
                shape = RoundedCornerShape(6.dp),
            )
            .padding(if (selected) 3.dp else 0.dp)
            .background(
                color = if (selected) Color.Black else Color.Transparent,
                shape = RoundedCornerShape(5.dp),
            )
            .padding(if (selected) 1.dp else 0.dp)
            .border(
                width = if (selected) 0.dp else 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(4.dp),
            )
            .background(color, RoundedCornerShape(4.dp))
            .clickable(onClick = onClick),
    )
}

/**
 * Render the shared HSV picker for a custom team or gender-ratio badge color.
 *
 * @param initialColor Color used to initialize the picker and preview.
 * @param testTagPrefix Prefix used to build custom picker test tags.
 * @param onColorChange Callback receiving the currently selected custom color.
 */
@Composable
internal fun CustomColorPicker(
    initialColor: Color,
    testTagPrefix: String,
    onColorChange: (Color) -> Unit,
) {
    val controller = rememberColorPickerController()
    var previewColor by remember(initialColor) {
        mutableStateOf(initialColor)
    }
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        HsvColorPicker(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
                .testTag("$testTagPrefix-custom-picker"),
            controller = controller,
            initialColor = initialColor,
            onColorChanged = { colorEnvelope ->
                previewColor = colorEnvelope.color.copy(alpha = 1f)
                onColorChange(previewColor)
            },
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .testTag("$testTagPrefix-custom-preview")
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(6.dp))
                .background(previewColor, RoundedCornerShape(6.dp))
                .clickable {
                    onColorChange(previewColor)
                },
        )
    }
}

/// Return an opaque ARGB long for a Compose color.
internal fun Color.toOpaqueArgbLong(): Long {
    return copy(alpha = 1f).toArgb().toLong() and 0xFFFFFFFFL
}

/**
 * Render the shared action row for preset and custom color dialogs.
 *
 * @param confirmText Text for the right-side action.
 * @param confirmTestTag Optional test tag for the right-side action.
 * @param onCancel Callback closing the dialog without applying a new color.
 * @param onConfirm Callback running the right-side color action.
 */
@Composable
internal fun ColorDialogActions(
    confirmText: String,
    confirmTestTag: String?,
    onCancel: () -> Unit,
    onConfirm: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextActionButton(
            label = "Cancel",
            compact = true,
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
            onClick = onCancel,
        )
        TextActionButton(
            label = confirmText,
            compact = true,
            tag = confirmTestTag,
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 8.dp),
            onClick = onConfirm,
        )
    }
}

/// Neutral fill for buttons inside dialogs.
internal val LightNeutralColor: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceContainerLow

/// Neutral fill for buttons on ordinary white pages.
internal val DarkNeutralColor: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceContainerHigh

/// Stronger neutral fill for buttons inside dialogs.
internal val EmphasizedLightNeutralColor: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceBright

/// Stronger neutral fill for buttons on ordinary white pages.
internal val EmphasizedDarkNeutralColor: Color
    @Composable get() = MaterialTheme.colorScheme.surfaceVariant

/// Primary action fill for navigation and other important actions.
internal val PrimaryColor: Color
    @Composable get() = MaterialTheme.colorScheme.primary

/// Content color to use on PrimaryColor.
internal val OnPrimaryColor: Color
    @Composable get() = MaterialTheme.colorScheme.onPrimary

/// Secondary action fill for non-danger actions that should stand out from neutral controls.
internal val SecondaryColor = Color(0xFF486F9E)

/// Content color to use on SecondaryColor.
internal val OnSecondaryColor = Color.White

/// Reset/undo/delete fill for actions that should signal a little caution.
internal val ResetColor = Color(0xFF9E4B3E)

/// Content color to use on ResetColor.
private val OnResetColor = Color.White

/// Redo fill for tertiary actions.
internal val RedoColor = Color(0xFF4F565C)

/// Content color to use on RedoColor.
private val OnRedoColor = Color.White

/// Unselected choice fill.
internal val OptionColor: Color
    @Composable get() = if (isSystemInDarkTheme()) OptionDarkModeColor else MaterialTheme.colorScheme.surface

/// Fill for dialog value-entry controls such as text fields and picker launchers.
internal val InputColor: Color
    @Composable get() = MaterialTheme.colorScheme.surface

/// Return app-standard outlined-button colors for neutral action buttons.
@Composable
internal fun neutralOutlinedButtonColors(
    containerColor: Color = LightNeutralColor,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
): ButtonColors {
    return ButtonDefaults.outlinedButtonColors(
        containerColor = containerColor,
        contentColor = contentColor,
        disabledContainerColor = containerColor.copy(alpha = 0.45f),
        disabledContentColor = contentColor.copy(alpha = 0.38f),
    )
}

/// Return the default filled primary button colors.
@Composable
internal fun primaryButtonColors(): ButtonColors {
    return ButtonDefaults.buttonColors(
        containerColor = PrimaryColor,
        contentColor = OnPrimaryColor,
    )
}

/// Return filled secondary action button colors.
@Composable
internal fun secondaryButtonColors(): ButtonColors {
    return ButtonDefaults.buttonColors(
        containerColor = SecondaryColor,
        contentColor = OnSecondaryColor,
    )
}

/// Return filled reset/undo/delete button colors.
@Composable
internal fun resetButtonColors(): ButtonColors {
    return ButtonDefaults.buttonColors(
        containerColor = ResetColor,
        contentColor = OnResetColor,
    )
}

/// Return outlined reset/undo button colors.
@Composable
internal fun resetOutlinedButtonColors(): ButtonColors {
    return ButtonDefaults.outlinedButtonColors(
        containerColor = ResetColor,
        contentColor = OnResetColor,
        disabledContainerColor = ResetColor.copy(alpha = 0.45f),
        disabledContentColor = OnResetColor.copy(alpha = 0.38f),
    )
}

/// Return outlined redo button colors.
@Composable
internal fun redoOutlinedButtonColors(): ButtonColors {
    return ButtonDefaults.outlinedButtonColors(
        containerColor = RedoColor,
        contentColor = OnRedoColor,
        disabledContainerColor = RedoColor.copy(alpha = 0.45f),
        disabledContentColor = OnRedoColor.copy(alpha = 0.38f),
    )
}

/// Return colors for dialog picker launchers that visually match dialog text fields.
@Composable
internal fun dialogInputButtonColors(): ButtonColors {
    return ButtonDefaults.outlinedButtonColors(
        containerColor = InputColor,
        contentColor = MaterialTheme.colorScheme.onSurface,
    )
}

/// Return colors for setup previous-card menu actions.
@Composable
internal fun setupCardsButtonColors(): ButtonColors {
    return ButtonDefaults.outlinedButtonColors(
        containerColor = CardButtonColor,
        contentColor = Color.Black,
    )
}

/// Return the standard Material button content padding.
internal val DefaultButtonContentPadding: PaddingValues
    get() = ButtonDefaults.ContentPadding

/// Return the app-standard selected/unselected choice fill.
@Composable
internal fun choiceContainerColor(selected: Boolean): Color {
    return if (selected) SelectedColor else OptionColor
}

/// Return the app-standard selected/unselected choice content color.
@Composable
internal fun choiceContentColor(selected: Boolean): Color {
    return if (selected) Color.White else MaterialTheme.colorScheme.onSurface
}

/// Return the app-standard selected/unselected choice border color.
@Composable
internal fun choiceBorderColor(selected: Boolean): Color {
    if (selected) {
        return SelectedBorderColor
    }
    return if (isSystemInDarkTheme()) OptionBorderDarkColor else OptionBorderLightColor
}

/**
 * Render a standard app selected/unselected choice chip.
 *
 * @param label The visible choice text.
 * @param selected Whether this choice is currently selected.
 * @param tag Optional test tag.
 * @param horizontalPadding Horizontal text padding in density-independent pixels.
 * @param verticalPadding Vertical text padding in density-independent pixels.
 * @param onClick Callback selecting the choice.
 */
@Composable
internal fun ChoiceChipButton(
    label: String,
    selected: Boolean,
    modifier: Modifier = Modifier,
    tag: String? = null,
    horizontalPadding: Dp = 10.dp,
    verticalPadding: Dp = 7.dp,
    maxLines: Int = 1,
    softWrap: Boolean = false,
    textOverflow: TextOverflow = TextOverflow.Ellipsis,
    onClick: () -> Unit,
) {
    val clearFocusAndHideKeyboard = rememberClearFocusAndHideKeyboard()
    Surface(
        onClick = {
            clearFocusAndHideKeyboard()
            onClick()
        },
        modifier = modifier
            .withTag(tag)
            .semantics {
                this.selected = selected
            },
        shape = ChoiceButtonShape,
        color = choiceContainerColor(selected),
        contentColor = choiceContentColor(selected),
        border = BorderStroke(1.dp, choiceBorderColor(selected)),
    ) {
        Text(
            text = label,
            modifier = Modifier.padding(horizontal = horizontalPadding, vertical = verticalPadding),
            style = MaterialTheme.typography.labelLarge,
            maxLines = maxLines,
            softWrap = softWrap,
            overflow = textOverflow,
            textAlign = TextAlign.Center,
        )
    }
}

/**
 * Render a filled list item inside a dialog with enough edge contrast to read as a row.
 *
 * @param content Row content.
 */
@Composable
internal fun DialogListItemCard(
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = EmphasizedLightNeutralColor,
            contentColor = MaterialTheme.colorScheme.onSurface,
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
    ) {
        content()
    }
}

/// Return app-standard outlined text-field colors for fields inside dialogs.
@Composable
internal fun dialogOutlinedTextFieldColors(): TextFieldColors {
    return OutlinedTextFieldDefaults.colors(
        focusedContainerColor = InputColor,
        unfocusedContainerColor = InputColor,
        disabledContainerColor = LightNeutralColor,
        errorContainerColor = InputColor,
        unfocusedBorderColor = MaterialTheme.colorScheme.outline,
    )
}

/// Return app-standard outlined text-field colors for fields on ordinary pages.
@Composable
internal fun pageOutlinedTextFieldColors(): TextFieldColors {
    return OutlinedTextFieldDefaults.colors()
}

/**
 * Render a standard app text-entry field.
 *
 * @param value Current field text.
 * @param onValueChange Callback receiving each edited value.
 * @param labelText Optional field label.
 * @param promptText Optional placeholder text shown when the field is empty.
 * @param enabled Whether the field accepts edits.
 * @param singleLine Whether the field should stay on one line.
 * @param minLines Minimum line count for multi-line fields.
 * @param capitalization Keyboard capitalization behavior.
 * @param keyboardType Keyboard type requested from the platform.
 * @param modifier Optional layout modifier, reserved for row weight when needed.
 * @param tag Optional test tag.
 * @param colors Text-field colors.
 * @param promptTextColor Optional color for placeholder text.
 * @param onDone Optional domain action to run before clearing focus from the Done key.
 * @param onFocusLost Optional domain action to run when focus leaves the field.
 */
@Composable
internal fun TextEntry(
    value: String,
    onValueChange: (String) -> Unit,
    labelText: String? = null,
    promptText: String? = null,
    enabled: Boolean = true,
    singleLine: Boolean = true,
    minLines: Int = 1,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.None,
    keyboardType: KeyboardType = KeyboardType.Text,
    modifier: Modifier = Modifier,
    tag: String? = null,
    colors: TextFieldColors = dialogOutlinedTextFieldColors(),
    promptTextColor: Color? = null,
    onDone: (() -> Unit)? = null,
    onFocusLost: (() -> Unit)? = null,
) {
    val fieldState = rememberTextFieldState(initialText = value)
    val latestValue by rememberUpdatedState(value)
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val clearFocusAndHideKeyboard = rememberClearFocusAndHideKeyboard()
    LaunchedEffect(value) {
        if (fieldState.text.toString() != value) {
            fieldState.setTextAndPlaceCursorAtEnd(value)
        }
    }
    LaunchedEffect(fieldState) {
        snapshotFlow { fieldState.text.toString() }.collect { text ->
            if (text != latestValue) {
                latestOnValueChange(text)
            }
        }
    }
    val textScrollState = rememberScrollState()
    var textModifier = modifier.fillMaxWidth()
    if (onFocusLost != null) {
        textModifier = textModifier.onFocusChanged {
            if (!it.isFocused) {
                onFocusLost()
            }
        }
    }
    if (tag != null) {
        textModifier = textModifier.testTag(tag)
    }
    OutlinedTextField(
        state = fieldState,
        label = labelText?.let { text -> { Text(text) } },
        placeholder = promptText?.let { text ->
            {
                if (promptTextColor == null) {
                    Text(text)
                } else {
                    Text(text = text, color = promptTextColor)
                }
            }
        },
        enabled = enabled,
        lineLimits = textEntryLineLimits(
            singleLine = singleLine,
            minLines = minLines,
        ),
        keyboardOptions = KeyboardOptions(
            capitalization = capitalization,
            keyboardType = keyboardType,
            imeAction = if (singleLine) ImeAction.Done else ImeAction.Default,
        ),
        onKeyboardAction = {
            onDone?.invoke()
            clearFocusAndHideKeyboard()
        },
        colors = colors,
        scrollState = textScrollState,
        modifier = textModifier,
    )
}

/// Return a callback that clears text-field focus and hides the platform keyboard.
@Composable
private fun rememberClearFocusAndHideKeyboard(): () -> Unit {
    val focusManager = LocalFocusManager.current
    val keyboardController = LocalSoftwareKeyboardController.current
    return remember(focusManager, keyboardController) {
        {
            focusManager.clearFocus(force = true)
            // Defensive guard: Compose returns null when software keyboard control is unavailable.
            keyboardController?.hide()
        }
    }
}

/// Clear focus when the user taps otherwise inert dialog body space.
private fun Modifier.clearFocusOnPointerDown(
    clearFocusAndHideKeyboard: () -> Unit,
): Modifier {
    return pointerInput(clearFocusAndHideKeyboard) {
        awaitPointerEventScope {
            // This isn't constantly looping.  The await call waits quietly for an event.
            // The while(true) means that after that event is handled, it waits again for
            // the next one.
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                if (event.changes.any { it.changedToDown() }) {
                    clearFocusAndHideKeyboard()
                }
            }
        }
    }
}

/// Return a button layout modifier from semantic sizing options.
private fun buttonLayoutModifier(
    modifier: Modifier = Modifier,
    fullWidth: Boolean = false,
    width: Dp? = null,
    height: Dp? = null,
): Modifier {
    var result = modifier
    if (fullWidth) {
        result = result.fillMaxWidth()
    }
    if (width != null) {
        result = result.width(width)
    }
    if (height != null) {
        result = result.height(height)
    }
    return result
}

/// Return line limits for the state-backed Material text-field API.
private fun textEntryLineLimits(singleLine: Boolean, minLines: Int): TextFieldLineLimits {
    return if (singleLine) {
        TextFieldLineLimits.SingleLine
    } else {
        TextFieldLineLimits.MultiLine(minHeightInLines = minLines)
    }
}

/**
 * Render a button that opens another page or top-level app surface.
 *
 * @param label The button label.
 * @param fullWidth Whether the button should fill the available width.
 * @param height Optional fixed button height.
 * @param modifier Optional layout modifier, reserved for row weight when needed.
 * @param enabled Whether the button is enabled.
 * @param colors Button colors.
 * @param borderColor Button border color.
 * @param compact Whether to remove default minimum sizing for fixed-height bars.
 * @param tag Optional test tag.
 * @param onClick Callback invoked when the button is tapped.
 */
@Composable
internal fun NavigationButton(
    label: String,
    fullWidth: Boolean = false,
    height: Dp? = null,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    colors: ButtonColors = ButtonDefaults.buttonColors(),
    borderColor: Color? = null,
    compact: Boolean = false,
    tag: String? = null,
    onClick: () -> Unit,
) {
    StandardRoleButton(
        label = label,
        modifier = buttonLayoutModifier(
            modifier = modifier,
            fullWidth = fullWidth,
            height = height,
        ),
        enabled = enabled,
        shape = NavigationButtonShape,
        colors = colors,
        borderColor = borderColor,
        compact = compact,
        tag = tag,
        contentPadding = null,
        onClick = onClick,
    )
}

/**
 * Render a full-width or otherwise prominent page action button.
 *
 * @param label The button label.
 * @param fullWidth Whether the button should fill the available width.
 * @param height Optional fixed button height.
 * @param enabled Whether the button is enabled.
 * @param modifier Optional layout modifier, reserved for row weight when needed.
 * @param containerColor Button background color.
 * @param contentColor Button text color.
 * @param borderColor Optional button border color.
 * @param minHeight Minimum visual button height.
 * @param fontSize Optional button label font size.
 * @param textMaxLines Maximum button-label line count.
 * @param softWrap Whether the button label may wrap.
 * @param textOverflow Overflow behavior for the button label.
 * @param tag Optional test tag.
 * @param onClick Callback invoked when the button is tapped.
 */
@Composable
internal fun BigActionButton(
    label: String,
    fullWidth: Boolean = false,
    height: Dp? = null,
    enabled: Boolean = true,
    modifier: Modifier = Modifier,
    containerColor: Color = DarkNeutralColor,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    borderColor: Color? = MaterialTheme.colorScheme.outline,
    minHeight: Dp = 34.dp,
    fontSize: TextUnit? = null,
    textMaxLines: Int = 1,
    softWrap: Boolean = false,
    textOverflow: TextOverflow = TextOverflow.Ellipsis,
    tag: String? = null,
    onClick: () -> Unit,
) {
    StandardRoleButton(
        label = label,
        modifier = buttonLayoutModifier(
            modifier = modifier,
            fullWidth = fullWidth,
            height = height,
        )
            .defaultMinSize(minHeight = minHeight),
        enabled = enabled,
        shape = BigActionButtonShape,
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = containerColor,
            contentColor = contentColor,
            disabledContainerColor = containerColor.copy(alpha = 0.45f),
            disabledContentColor = contentColor.copy(alpha = 0.38f),
        ),
        borderColor = borderColor,
        compact = true,
        tag = tag,
        contentPadding = null,
        fontSize = fontSize,
        textMaxLines = textMaxLines,
        softWrap = softWrap,
        textOverflow = textOverflow,
        onClick = onClick,
    )
}

/**
 * Render a secondary menu/action button that performs an optional task in the current flow.
 *
 * @param label The button label.
 * @param fullWidth Whether the button should fill the available width.
 * @param enabled Whether the button is enabled.
 * @param colors Button colors.
 * @param borderColor Optional button border color.
 * @param contentPadding Padding inside the button.
 * @param maxLines Maximum menu-label line count.
 * @param trailingLabel Optional value shown on the right side of the button row.
 * @param tag Optional test tag.
 * @param onClick Callback invoked when the button is tapped.
 */
@Composable
internal fun MenuButton(
    label: String,
    fullWidth: Boolean = true,
    enabled: Boolean = true,
    colors: ButtonColors = neutralOutlinedButtonColors(),
    borderColor: Color? = MaterialTheme.colorScheme.outline,
    contentPadding: PaddingValues = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
    maxLines: Int = 3,
    trailingLabel: String? = null,
    tag: String? = null,
    onClick: () -> Unit,
) {
    StandardRoleButton(
        label = label,
        modifier = buttonLayoutModifier(fullWidth = fullWidth),
        enabled = enabled,
        shape = MenuButtonShape,
        colors = colors,
        borderColor = borderColor,
        compact = false,
        tag = tag,
        contentPadding = contentPadding,
        textMaxLines = maxLines,
        softWrap = true,
        trailingLabel = trailingLabel,
        onClick = onClick,
    )
}

/**
 * Render a plain text action used in top bars and dialog action slots.
 *
 * @param label The button label.
 * @param height Optional fixed button height.
 * @param compact Whether to remove default minimum sizing.
 * @param enabled Whether the button is enabled.
 * @param contentPadding Padding inside the button.
 * @param tag Optional test tag.
 * @param onClick Callback invoked when the button is tapped.
 */
@Composable
internal fun TextActionButton(
    label: String,
    height: Dp? = null,
    compact: Boolean = false,
    enabled: Boolean = true,
    contentPadding: PaddingValues = ButtonDefaults.TextButtonContentPadding,
    tag: String? = null,
    onClick: () -> Unit,
) {
    val clearFocusAndHideKeyboard = rememberClearFocusAndHideKeyboard()
    val taggedModifier = buttonLayoutModifier(height = height).withTag(tag)
    val buttonModifier = if (compact) {
        taggedModifier.defaultMinSize(minWidth = 1.dp, minHeight = 0.dp)
    } else {
        taggedModifier
    }
    TextButton(
        onClick = {
            clearFocusAndHideKeyboard()
            onClick()
        },
        modifier = buttonModifier,
        enabled = enabled,
        contentPadding = contentPadding,
    ) {
        Text(label, maxLines = 1, softWrap = false)
    }
}

/**
 * Render a Material date picker that reads and returns a local date.
 *
 * @param initialDate The date initially selected in the picker.
 * @param setButtonTag Test tag for the Set action.
 * @param onDismiss Callback closing the picker without changing state.
 * @param onConfirm Callback receiving the selected local date.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun LocalDatePickerDialog(
    initialDate: LocalDate,
    setButtonTag: String,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = dateToPickerTimestamp(initialDate),
    )

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextActionButton(
                label = "Set",
                tag = setButtonTag,
                onClick = {
                    val selectedTimestamp = datePickerState.selectedDateMillis!!
                    onConfirm(pickerTimestampToDate(selectedTimestamp))
                },
            )
        },
        dismissButton = {
            TextActionButton(label = "Cancel", onClick = onDismiss)
        },
    ) {
        DatePicker(
            state = datePickerState,
            title = null,
        )
    }
}

/**
 * Convert a local date into the UTC timestamp expected by the Material date picker.
 *
 * @param date The local date to convert.
 */
private fun dateToPickerTimestamp(date: LocalDate): Long {
    return date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
}

/**
 * Convert a Material date-picker timestamp back into a local date.
 *
 * @param timestamp The UTC midnight timestamp supplied by the picker.
 */
private fun pickerTimestampToDate(timestamp: Long): LocalDate {
    return Instant.ofEpochMilli(timestamp).atZone(ZoneOffset.UTC).toLocalDate()
}

/**
 * Render a compact icon-only action.
 *
 * @param icon The icon to show.
 * @param contentDescription Accessibility label for the icon action.
 * @param modifier Optional layout modifier.
 * @param size Button size.
 * @param iconSize Icon size.
 * @param iconColor Optional explicit icon color.
 * @param tag Optional test tag.
 * @param onClick Callback invoked when the icon is tapped.
 */
@Composable
internal fun IconActionButton(
    icon: ImageVector,
    contentDescription: String,
    modifier: Modifier = Modifier,
    size: Dp = 36.dp,
    iconSize: Dp = 20.dp,
    iconColor: Color? = null,
    tag: String? = null,
    onClick: () -> Unit,
) {
    IconButton(
        onClick = onClick,
        modifier = modifier
            .withTag(tag)
            .size(size),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            modifier = Modifier.size(iconSize),
            tint = iconColor ?: LocalContentColor.current,
        )
    }
}

/// Render the shared top-bar action that navigates back from the current screen.
@Composable
internal fun TopBarBackButton(onClick: () -> Unit) {
    IconActionButton(
        icon = Icons.AutoMirrored.Filled.ArrowBack,
        contentDescription = "Back",
        tag = "top-bar-back",
        onClick = onClick,
    )
}

/// Render the shared top-bar action that returns directly to Home.
@Composable
internal fun TopBarHomeButton(onClick: () -> Unit) {
    IconActionButton(
        icon = Icons.Filled.Home,
        contentDescription = "Home",
        tag = "top-bar-home",
        onClick = onClick,
    )
}

/**
 * Render a semantic button role with the role's shape already selected by the caller.
 *
 * @param label The button label.
 * @param modifier Optional layout modifier.
 * @param enabled Whether the button is enabled.
 * @param shape Button role shape.
 * @param colors Button colors.
 * @param borderColor Optional button border color.
 * @param compact Whether to remove default minimum sizing for fixed-height bars.
 * @param tag Optional test tag.
 * @param contentPadding Optional padding inside the button.
 * @param fontSize Optional button label font size.
 * @param textMaxLines Maximum button-label line count.
 * @param softWrap Whether the button label may wrap.
 * @param textOverflow Overflow behavior for the button label.
 * @param trailingLabel Optional value shown on the right side of the button row.
 * @param onClick Callback invoked when the button is tapped.
 */
@Composable
private fun StandardRoleButton(
    label: String,
    modifier: Modifier,
    enabled: Boolean,
    shape: Shape,
    colors: ButtonColors,
    borderColor: Color?,
    compact: Boolean,
    tag: String?,
    contentPadding: PaddingValues?,
    fontSize: TextUnit? = null,
    textMaxLines: Int = 1,
    softWrap: Boolean = false,
    textOverflow: TextOverflow = TextOverflow.Ellipsis,
    trailingLabel: String? = null,
    onClick: () -> Unit,
) {
    val clearFocusAndHideKeyboard = rememberClearFocusAndHideKeyboard()
    val taggedModifier = modifier.withTag(tag)
    val buttonModifier = if (compact) {
        taggedModifier.defaultMinSize(minHeight = 0.dp)
    } else {
        taggedModifier
    }
    val resolvedContentPadding = contentPadding ?: if (compact) {
        PaddingValues(horizontal = 8.dp, vertical = 3.dp)
    } else {
        ButtonDefaults.ContentPadding
    }
    val content: @Composable () -> Unit = {
        val textStyle = if (fontSize == null) {
            MaterialTheme.typography.labelLarge
        } else {
            MaterialTheme.typography.labelLarge.copy(fontSize = fontSize)
        }
        if (trailingLabel == null) {
            Text(
                label,
                textAlign = TextAlign.Center,
                style = textStyle,
                maxLines = textMaxLines,
                softWrap = softWrap,
                overflow = textOverflow,
            )
        } else {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    label,
                    style = textStyle,
                    maxLines = textMaxLines,
                    softWrap = softWrap,
                    overflow = textOverflow,
                )
                Text(
                    trailingLabel,
                    style = textStyle,
                    fontWeight = FontWeight.Bold,
                    maxLines = textMaxLines,
                    softWrap = softWrap,
                    overflow = textOverflow,
                )
            }
        }
    }
    val button: @Composable () -> Unit = {
        if (borderColor == null) {
            Button(
                onClick = {
                    clearFocusAndHideKeyboard()
                    onClick()
                },
                enabled = enabled,
                modifier = buttonModifier,
                shape = shape,
                colors = colors,
                contentPadding = resolvedContentPadding,
            ) {
                content()
            }
        } else {
            OutlinedButton(
                onClick = {
                    clearFocusAndHideKeyboard()
                    onClick()
                },
                enabled = enabled,
                modifier = buttonModifier,
                shape = shape,
                colors = colors,
                border = BorderStroke(1.dp, borderColor),
                contentPadding = resolvedContentPadding,
            ) {
                content()
            }
        }
    }
    if (compact) {
        CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
            button()
        }
    } else {
        button()
    }
}

/**
 * Render a compact numeric adjustment button.
 *
 * @param label The button label.
 * @param enabled Whether the button is enabled.
 * @param containerColor Button background color.
 * @param contentColor Button text color.
 * @param borderColor Button border color.
 * @param tag Optional test tag.
 * @param height Minimum button height.
 * @param onClick Callback invoked when the button is tapped.
 */
@Composable
internal fun AdjustButton(
    label: String,
    enabled: Boolean = true,
    containerColor: Color = LightNeutralColor,
    contentColor: Color = MaterialTheme.colorScheme.onSurface,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    tag: String? = null,
    height: Dp = 34.dp,
    onClick: () -> Unit,
) {
    val clearFocusAndHideKeyboard = rememberClearFocusAndHideKeyboard()
    val buttonWidth = height + 4.dp
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        OutlinedButton(
            onClick = {
                clearFocusAndHideKeyboard()
                onClick()
            },
            enabled = enabled,
            modifier = buttonLayoutModifier(width = buttonWidth, height = height)
                .withTag(tag),
            shape = AdjustShape,
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = containerColor,
                contentColor = contentColor,
                disabledContainerColor = containerColor.copy(alpha = 0.45f),
                disabledContentColor = contentColor.copy(alpha = 0.38f),
            ),
            border = BorderStroke(1.dp, borderColor),
            contentPadding = PaddingValues(horizontal = 0.dp, vertical = 3.dp),
        ) {
            Text(
                label,
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/**
 * Render one field action with stable sizing inside the live-team action grid.
 *
 * @param label Text shown in the button.
 * @param fullWidth Whether the button should fill the available width.
 * @param width Optional fixed button width.
 * @param height Button height.
 * @param fontSize Button-label font size resolved for the complete action grid.
 * @param modifier Optional layout modifier, reserved for row weight when needed.
 * @param enabled Whether the button can be pressed.
 * @param containerColor Button background color.
 * @param contentColor Button text color.
 * @param borderColor Button border color.
 * @param tag Optional test tag.
 * @param onClick Callback invoked when the observer taps the action.
 */
@Composable
internal fun FieldControlButton(
    label: String,
    fullWidth: Boolean = false,
    width: Dp? = null,
    height: Dp,
    fontSize: TextUnit,
    modifier: Modifier = Modifier,
    enabled: Boolean,
    containerColor: Color = Color.White,
    contentColor: Color = Color.Black,
    borderColor: Color = Color.Black,
    tag: String? = null,
    onClick: () -> Unit,
) {
    val clearFocusAndHideKeyboard = rememberClearFocusAndHideKeyboard()
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        OutlinedButton(
            onClick = {
                clearFocusAndHideKeyboard()
                onClick()
            },
            enabled = enabled,
            modifier = buttonLayoutModifier(
                modifier = modifier,
                fullWidth = fullWidth,
                width = width,
                height = height,
            )
                .withTag(tag)
                .defaultMinSize(minWidth = 0.dp, minHeight = 0.dp),
            shape = PanelShape,
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = containerColor,
                contentColor = contentColor,
            ),
            border = BorderStroke(1.dp, borderColor),
            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium.copy(
                    fontSize = fontSize,
                    lineHeight = fontSize,
                ),
                textAlign = TextAlign.Center,
                maxLines = 1,
                softWrap = false,
                overflow = TextOverflow.Clip,
            )
        }
    }
}

/**
 * Render the compact information affordance next to a live team name.
 *
 * @param teamName The team name used in the accessibility label.
 * @param contentColor Color that contrasts with the team background.
 * @param tag Optional test tag.
 * @param onClick Callback opening the team information view.
 */
@Composable
internal fun FieldInfoButton(
    teamName: String,
    contentColor: Color,
    tag: String? = null,
    onClick: () -> Unit,
) {
    val clearFocusAndHideKeyboard = rememberClearFocusAndHideKeyboard()
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        IconButton(
            onClick = {
                clearFocusAndHideKeyboard()
                onClick()
            },
            modifier = Modifier
                .withTag(tag)
                .size(24.dp)
                .semantics { contentDescription = "Show $teamName names" },
        ) {
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = null,
                tint = contentColor,
                modifier = Modifier.size(20.dp),
            )
        }
    }
}

/**
 * Render the pause/resume countdown control.
 *
 * @param isPaused Whether the button is currently paused.
 * @param enabled Whether the button can be pressed.
 * @param height Minimum button height.
 * @param onClick Callback invoked when the observer toggles pause state.
 */
@Composable
internal fun PauseResumeButton(
    isPaused: Boolean,
    enabled: Boolean,
    height: Dp,
    onClick: () -> Unit,
) {
    val contentColor = MaterialTheme.colorScheme.onSurface
    val iconColor = if (enabled) contentColor else contentColor.copy(alpha = 0.38f)
    val description = if (isPaused) "Resume countdown" else "Pause countdown"
    val iconSize = height * (18f / 34f)
    val horizontalPadding = height * (8f / 34f)
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .size(height)
                .testTag(if (isPaused) "live-resume-countdown" else "live-pause-countdown")
                .semantics { contentDescription = description },
            shape = AdjustShape,
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = DarkNeutralColor,
                contentColor = contentColor,
                disabledContainerColor = DarkNeutralColor.copy(alpha = 0.45f),
                disabledContentColor = contentColor.copy(alpha = 0.38f),
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 3.dp),
        ) {
            Icon(
                imageVector = if (isPaused) Icons.Filled.PlayArrow else Icons.Filled.Pause,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = iconColor,
            )
        }
    }
}

/**
 * Render the manual water-break control.
 *
 * @param enabled Whether the button can be pressed.
 * @param height Minimum button height.
 * @param onClick Callback invoked when the observer applies the water break.
 */
@Composable
internal fun WaterBreakButton(
    enabled: Boolean,
    height: Dp,
    onClick: () -> Unit,
) {
    val contentColor = MaterialTheme.colorScheme.onSurface
    val iconColor = if (enabled) WaterBreakIconColor else WaterBreakIconColor.copy(alpha = 0.38f)
    val iconSize = height * (18f / 34f)
    val horizontalPadding = height * (8f / 34f)
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = Modifier
                .size(height)
                .testTag("live-water-break")
                .semantics { contentDescription = "Water break" },
            shape = AdjustShape,
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = DarkNeutralColor,
                contentColor = contentColor,
                disabledContainerColor = DarkNeutralColor.copy(alpha = 0.45f),
                disabledContentColor = contentColor.copy(alpha = 0.38f),
            ),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
            contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 3.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.WaterDrop,
                contentDescription = null,
                modifier = Modifier.size(iconSize),
                tint = iconColor,
            )
        }
    }
}

/**
 * Render a compact count-correction row with label/count on the left and actions on the right.
 *
 * @param label The count label.
 * @param value The current count value.
 * @param emphasizedLabel Whether the label/count text should be styled as the row's main content.
 * @param incrementTag Optional test tag for the increment button.
 * @param decrementTag Optional test tag for the decrement button.
 * @param onIncrement Callback increasing the count.
 * @param onDecrement Callback decreasing the count.
 */
@Composable
internal fun CorrectionCountRow(
    label: String,
    value: Int,
    emphasizedLabel: Boolean = false,
    incrementTag: String? = null,
    decrementTag: String? = null,
    onIncrement: () -> Unit,
    onDecrement: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = "$label: $value",
            modifier = Modifier
                .weight(1f)
                .padding(end = 8.dp),
            style = if (emphasizedLabel) {
                MaterialTheme.typography.titleMedium
            } else {
                MaterialTheme.typography.bodyLarge
            },
            fontWeight = if (emphasizedLabel) FontWeight.SemiBold else null,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            AdjustButton(
                label = "+1",
                tag = incrementTag,
                onClick = onIncrement,
            )
            AdjustButton(
                label = "-1",
                enabled = value > 0,
                tag = decrementTag,
                onClick = onDecrement,
            )
        }
    }
}

/**
 * Render compact misconduct side-choice dialog actions as one row.
 *
 * @param firstLabel The leftmost action label, usually `Cancel` or `Back`.
 * @param firstTag Optional test tag for the leftmost action.
 * @param onFirst Callback for the leftmost action.
 * @param onOffense Callback for choosing offense.
 * @param onDefense Callback for choosing defense.
 */
@Composable
internal fun MisconductChoiceButtons(
    firstLabel: String,
    firstTag: String? = null,
    onFirst: () -> Unit,
    onOffense: () -> Unit,
    onDefense: () -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.End,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextActionButton(
            label = firstLabel,
            tag = firstTag,
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            onClick = onFirst,
        )
        TextActionButton(
            label = "Offense",
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            onClick = onOffense,
        )
        TextActionButton(
            label = "Defense",
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp),
            onClick = onDefense,
        )
    }
}

/**
 * Render a shared section wrapper used across home and setup screens.
 *
 * @param title The section title.
 * @param subtitle Optional right-side subtitle.
 * @param content The section body content.
 */
@Composable
internal fun SectionCard(
    title: String,
    subtitle: String? = null,
    content: @Composable () -> Unit,
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = SectionCardShape,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    title,
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        modifier = Modifier.weight(1f),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.End,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            content()
        }
    }
}

/**
 * Render a small labeled section used inside correction dialogs.
 *
 * @param title The section title, normally a team name.
 * @param content The correction controls for that section.
 */
@Composable
internal fun TeamCorrectionSection(
    title: String,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        Column(
            modifier = Modifier.padding(start = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            content()
        }
    }
}
