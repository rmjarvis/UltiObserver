package rmjarvis.ultiobserver

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Text
import kotlinx.coroutines.delay
import rmjarvis.ultiobserver.ui.theme.UltiObserverTheme
import rmjarvis.ultiobserver.wearprotocol.WearActionConfirmation
import rmjarvis.ultiobserver.wearprotocol.WearTeamActionPrompt
import rmjarvis.ultiobserver.wearprotocol.WearGuidanceLineSnapshot
import rmjarvis.ultiobserver.wearprotocol.WearGuidancePresentation
import rmjarvis.ultiobserver.wearprotocol.WearPromptSnapshot

/** Show one phone-formatted pending decision and return the observer's response to the phone. */
@Composable
internal fun DecisionScreen(
    decision: WearPromptSnapshot,
    stateToken: String,
    onDecision: (String, Boolean, (Boolean) -> Unit) -> Unit,
) {
    var commandPending by remember(decision, stateToken) { mutableStateOf(false) }
    val submitDecision: (Boolean) -> Unit = { accept ->
        if (!commandPending) {
            commandPending = true
            onDecision(stateToken, accept) {
                commandPending = false
            }
        }
    }

    BackHandler {
        submitDecision(true)
    }
    LaunchedEffect(decision, stateToken, commandPending) {
        if (!commandPending) {
            when (decision.presentation) {
                WearGuidancePresentation.VISIBLE -> Unit
                WearGuidancePresentation.VISIBLE_TIMED -> {
                    delay(decision.autoAcceptDelayMillis!!)
                    submitDecision(true)
                }
                WearGuidancePresentation.HIDDEN_AUTO_ACCEPT -> submitDecision(true)
            }
        }
    }

    PromptScreen(
        prompt = decision,
        enabled = !commandPending,
        actions = listOf(
            PromptActionSpec(decision.dismissLabel) { submitDecision(false) },
            PromptActionSpec(decision.confirmLabel) { submitDecision(true) },
        ),
    )
}

/** Show an action confirmation whose Cancel action remains local to the watch. */
@Composable
internal fun ActionConfirmationScreen(
    confirmation: WearActionConfirmation,
    onConfirmationChange: (WearActionConfirmation) -> Unit,
    onConfirm: (WearActionConfirmation, (Boolean) -> Unit) -> Unit,
    onCancel: () -> Unit,
) {
    val prompt = confirmation.prompt
    var commandPending by remember(prompt) { mutableStateOf(false) }
    val submitConfirmation = {
        if (!commandPending) {
            commandPending = true
            onConfirm(confirmation) {
                commandPending = false
            }
        }
    }
    val currentSubmitConfirmation by rememberUpdatedState(submitConfirmation)

    BackHandler(enabled = !commandPending) {
        onCancel()
    }
    LaunchedEffect(confirmation.stateToken, commandPending) {
        if (!commandPending) {
            when (prompt.presentation) {
                WearGuidancePresentation.VISIBLE -> Unit
                WearGuidancePresentation.VISIBLE_TIMED -> {
                    delay(prompt.autoAcceptDelayMillis!!)
                    currentSubmitConfirmation()
                }
                WearGuidancePresentation.HIDDEN_AUTO_ACCEPT -> currentSubmitConfirmation()
            }
        }
    }

    val alternativeAction = if (confirmation is WearActionConfirmation.PullViolation) {
        val alternative = confirmation.options
            .firstOrNull { option -> option.violation != confirmation.selectedViolation }
        if (alternative == null) {
            null
        } else {
            PromptActionSpec(alternative.violation.alternativeActionLabel()) {
                onConfirmationChange(
                    confirmation.copy(
                        selectedViolation = alternative.violation,
                        prompt = alternative.prompt,
                    )
                )
            }
        }
    } else {
        null
    }
    PromptScreen(
        prompt = prompt,
        enabled = !commandPending,
        actions = listOf(
            PromptActionSpec(prompt.dismissLabel, onCancel),
            PromptActionSpec(prompt.confirmLabel, submitConfirmation),
        ),
        alternativeAction = alternativeAction,
    )
}

/** Show a notice that can be dismissed entirely on the watch. */
@Composable
internal fun ActionNoticeScreen(
    notice: WearTeamActionPrompt.Notice,
    onDismiss: () -> Unit,
) {
    BackHandler {
        onDismiss()
    }
    PromptScreen(
        prompt = notice.prompt,
        enabled = true,
        actions = listOf(PromptActionSpec(notice.prompt.dismissLabel, onDismiss)),
    )
}

/** Hold watch interaction while the phone owns an active card workflow. */
@Composable
internal fun ContinueOnPhoneScreen(
    onCancel: ((Boolean) -> Unit) -> Unit,
) {
    var commandPending by remember { mutableStateOf(false) }
    val cancel = {
        if (!commandPending) {
            commandPending = true
            onCancel {
                commandPending = false
            }
        }
    }
    BackHandler(enabled = !commandPending) {
        cancel()
    }
    UltiObserverTheme {
        AppScaffold(
            timeText = { AppTimeText() },
            containerColor = Color.Black,
            contentColor = Color.White,
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = "Continue on phone",
                    color = Color.White,
                    fontSize = 13.sp,
                    lineHeight = 17.sp,
                    textAlign = TextAlign.Center,
                )
                Spacer(modifier = Modifier.height(12.dp))
                Box(
                    modifier = Modifier
                        .height(42.dp)
                        .clickable(
                            enabled = !commandPending,
                            role = Role.Button,
                            onClick = cancel,
                        )
                        .padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = "Cancel",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }
    }
}

@Composable
private fun PromptScreen(
    prompt: WearPromptSnapshot,
    enabled: Boolean,
    actions: List<PromptActionSpec>,
    alternativeAction: PromptActionSpec? = null,
) {
    UltiObserverTheme {
        AppScaffold(
            timeText = { AppTimeText() },
            containerColor = DialogBackgroundColor,
            contentColor = DialogContentColor,
        ) {
            if (prompt.presentation != WearGuidancePresentation.HIDDEN_AUTO_ACCEPT) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(DialogBackgroundColor)
                        .padding(top = 28.dp, start = 12.dp, end = 12.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(top = 12.dp, bottom = 4.dp, start = 12.dp, end = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = prompt.title,
                            modifier = Modifier.fillMaxWidth(),
                            color = DialogContentColor,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            contentAlignment = Alignment.TopStart,
                        ) {
                            Text(
                                text = prompt.messageLines.toAnnotatedString(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState()),
                                color = DialogContentColor,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                textAlign = TextAlign.Start,
                            )
                        }
                        Spacer(modifier = Modifier.height(4.dp))
                        if (alternativeAction != null) {
                            PromptAlternativeAction(
                                label = alternativeAction.label,
                                enabled = enabled,
                                onClick = alternativeAction.onClick,
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceEvenly,
                        ) {
                            actions.forEach { action ->
                                PromptAction(
                                    label = action.label,
                                    enabled = enabled,
                                    onClick = action.onClick,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/** One text action shown along the bottom of a watch prompt. */
private data class PromptActionSpec(
    val label: String,
    val onClick: () -> Unit,
)

@Composable
private fun PromptAction(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .height(42.dp)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 12.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (enabled) DialogContentColor else DisabledDialogContentColor,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            maxLines = 1,
            softWrap = false,
        )
    }
}

/** Compact text action that switches the selected mixed pull violation. */
@Composable
private fun PromptAlternativeAction(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Text(
        text = label,
        modifier = Modifier
            .offset(y = 6.dp)
            .clip(shape)
            .background(AlternativeActionBackgroundColor)
            .border(1.dp, DialogContentColor, shape)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 4.dp),
        color = if (enabled) DialogContentColor else DisabledDialogContentColor,
        fontSize = 12.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 1,
        softWrap = false,
    )
}

/** Format the compact action that switches a mixed pull violation on the watch. */
private fun PullViolationType.alternativeActionLabel(): String {
    return when (this) {
        PullViolationType.OFFSIDES -> "→ Offsides"
        PullViolationType.MAJORITY_PULL -> "→ Majority pull viol."
        PullViolationType.FALSE_START -> error("False start has no alternative pull violation.")
    }
}

private val DialogBackgroundColor = Color(0xFFF5F1E7)
private val AlternativeActionBackgroundColor = Color(0xFFFFFDF8)
private val DialogContentColor = Color(0xFF1F1A17)
private val DisabledDialogContentColor = Color(0xFF817B75)

private fun List<WearGuidanceLineSnapshot>.toAnnotatedString(): AnnotatedString {
    return buildAnnotatedString {
        this@toAnnotatedString.forEachIndexed { index, line ->
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
}
