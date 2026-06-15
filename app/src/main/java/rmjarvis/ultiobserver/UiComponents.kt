package rmjarvis.ultiobserver

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

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

internal val TeamSetup.accent: Color
    get() = if (color == TeamColorChoice.CUSTOM) Color(customColorArgb!!) else color.accent

internal val TeamSetup.content: Color
    get() = if (color == TeamColorChoice.CUSTOM) readableContentColor(Color(customColorArgb!!)) else color.content

internal val TeamLiveState.accent: Color
    get() = if (color == TeamColorChoice.CUSTOM) Color(customColorArgb!!) else color.accent

internal val TeamLiveState.content: Color
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
 * Render a small general-purpose outlined action button.
 *
 * @param label The button label.
 * @param modifier Optional layout modifier.
 * @param enabled Whether the button is enabled.
 * @param containerColor Button background color.
 * @param contentColor Button text color.
 * @param borderColor Button border color.
 * @param onClick Callback invoked when the button is tapped.
 */
@Composable
internal fun SmallActionButton(
    label: String,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    containerColor: Color = Color.Transparent,
    contentColor: Color = MaterialTheme.colorScheme.primary,
    borderColor: Color = MaterialTheme.colorScheme.outline,
    onClick: () -> Unit,
) {
    CompositionLocalProvider(LocalMinimumInteractiveComponentSize provides 0.dp) {
        OutlinedButton(
            onClick = onClick,
            enabled = enabled,
            modifier = modifier.defaultMinSize(minHeight = 34.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = containerColor,
                contentColor = contentColor,
            ),
            border = BorderStroke(1.dp, borderColor),
            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 3.dp),
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
        shape = RoundedCornerShape(20.dp),
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
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (subtitle != null) {
                    Text(
                        subtitle,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
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
    content: @Composable () -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(title, fontWeight = FontWeight.SemiBold)
        content()
    }
}
