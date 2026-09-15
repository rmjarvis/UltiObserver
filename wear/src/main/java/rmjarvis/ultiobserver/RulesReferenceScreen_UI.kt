package rmjarvis.ultiobserver

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.AppScaffold
import androidx.wear.compose.material3.Text
import rmjarvis.ultiobserver.ui.theme.UltiObserverTheme
import rmjarvis.ultiobserver.wearprotocol.WearRulesReferenceItemSnapshot

/** Show the phone's current rules reference, with room for long values to wrap and scroll. */
@Composable
internal fun RulesReferenceScreen(items: List<WearRulesReferenceItemSnapshot>, onBack: () -> Unit) {
    UltiObserverTheme {
        AppScaffold(
            timeText = { AppTimeText() },
            containerColor = Color.Black,
            contentColor = Color.White,
        ) {
            Column(
                modifier = Modifier.fillMaxSize().padding(top = 28.dp, start = 24.dp, end = 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = "Game rules",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                )
                Column(
                    modifier = Modifier.weight(1f).fillMaxWidth()
                        .verticalScroll(rememberScrollState()).padding(vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items.forEach { item ->
                        Text(
                            text = buildAnnotatedString {
                                withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                                    append(item.label)
                                }
                                append(" ")
                                withStyle(SpanStyle(
                                    color = if (item.heatAdjusted) Color(0xFFFF8A80) else Color.White,
                                    fontWeight = if (item.heatAdjusted) FontWeight.Bold else FontWeight.Normal,
                                )) {
                                    append(item.value)
                                }
                            },
                            fontSize = 12.sp,
                            lineHeight = 16.sp,
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
}
