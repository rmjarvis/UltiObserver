package rmjarvis.ultiobserver

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.wear.compose.material3.TimeText
import kotlinx.coroutines.delay
import rmjarvis.ultiobserver.ui.theme.UltiObserverTheme
import rmjarvis.ultiobserver.wearprotocol.WearDecisionSnapshot
import rmjarvis.ultiobserver.wearprotocol.WearGuidanceLineSnapshot
import rmjarvis.ultiobserver.wearprotocol.WearGuidancePresentation

/** Show one phone-formatted pending decision and return the observer's response to the phone. */
@Composable
internal fun DecisionScreen(
    decision: WearDecisionSnapshot,
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

    UltiObserverTheme {
        AppScaffold(
            timeText = { TimeText() },
            containerColor = Color.Black,
            contentColor = Color.White,
        ) {
            if (decision.presentation != WearGuidancePresentation.HIDDEN_AUTO_ACCEPT) {
                DecisionContent(
                    decision = decision,
                    enabled = !commandPending,
                    onAccept = {
                        submitDecision(true)
                    },
                    onNotYet = {
                        submitDecision(false)
                    },
                )
            }
        }
    }
}

@Composable
private fun DecisionContent(
    decision: WearDecisionSnapshot,
    enabled: Boolean,
    onAccept: () -> Unit,
    onNotYet: () -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(horizontal = 26.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(DialogBackgroundColor)
                .padding(top = 12.dp, bottom = 4.dp, start = 12.dp, end = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                text = decision.title,
                modifier = Modifier.fillMaxWidth(),
                color = DialogContentColor,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Start,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = decision.messageLines.toAnnotatedString(),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 76.dp)
                    .verticalScroll(rememberScrollState()),
                color = DialogContentColor,
                fontSize = 12.sp,
                lineHeight = 16.sp,
                textAlign = TextAlign.Start,
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
            ) {
                DecisionAction(
                    label = decision.dismissLabel,
                    enabled = enabled,
                    onClick = onNotYet,
                )
                DecisionAction(
                    label = decision.confirmLabel,
                    enabled = enabled,
                    onClick = onAccept,
                )
            }
        }
    }
}

@Composable
private fun DecisionAction(
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
        )
    }
}

private val DialogBackgroundColor = Color(0xFFF5F1E7)
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
