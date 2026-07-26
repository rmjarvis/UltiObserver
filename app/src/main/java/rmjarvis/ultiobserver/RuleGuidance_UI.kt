package rmjarvis.ultiobserver

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight

/**
 * Delay used by automatic rule-guidance actions.
 *
 * Instrumentation tests may temporarily shorten this production five-second value.
 */
internal var ruleGuidanceTimeoutMillis = 5_000L

/**
 * Present guidance normally, time it out, or accept it without rendering.
 *
 * @param key Stable identity for the visible dialog.
 * @param mode The observer's global guidance preference.
 * @param requiredInNone Whether None mode must retain this guidance briefly.
 * @param onAutoAccept Callback equivalent to the dialog's normal OK action.
 * @param content Dialog content rendered for visible presentations.
 */
@Composable
internal fun RuleGuidanceGate(
    key: Any?,
    mode: RuleGuidanceMode,
    requiredInNone: Boolean,
    onAutoAccept: () -> Unit,
    content: @Composable () -> Unit,
) {
    val presentation = mode.presentation(requiredInNone)
    LaunchedEffect(key, presentation) {
        when (presentation) {
            RuleGuidancePresentation.VISIBLE -> Unit
            RuleGuidancePresentation.VISIBLE_TIMED -> {
                kotlinx.coroutines.delay(ruleGuidanceTimeoutMillis)
                onAutoAccept()
            }
            RuleGuidancePresentation.HIDDEN_AUTO_ACCEPT -> onAutoAccept()
        }
    }
    if (presentation != RuleGuidancePresentation.HIDDEN_AUTO_ACCEPT) {
        content()
    }
}

/// Render the emphasis explicitly selected by the JVM guidance formatter.
@Composable
internal fun RuleGuidanceText(message: RuleGuidanceMessage) {
    val annotatedText = buildAnnotatedString {
        message.lines.forEachIndexed { index, line ->
            if (index > 0) {
                append("\n")
            }
            if (line.bold) {
                pushStyle(SpanStyle(fontWeight = FontWeight.Bold))
            }
            append(line.text)
            if (line.bold) {
                pop()
            }
        }
    }
    Text(
        text = annotatedText,
        style = MaterialTheme.typography.bodyLarge,
    )
}
