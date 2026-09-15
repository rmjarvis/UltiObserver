package rmjarvis.ultiobserver

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.wear.compose.material3.Text
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.sp
import androidx.wear.compose.material3.TimeSource
import androidx.wear.compose.material3.TimeText
import androidx.wear.compose.material3.TimeTextDefaults
import androidx.wear.compose.material3.timeTextCurvedText

/** Show the standard clock used at the top of UltiObserver watch screens. */
@Composable
internal fun AppTimeText(timeSource: TimeSource? = null) {
    val timeStyle = TimeTextDefaults.timeTextStyle(
        background = Color.Black,
        color = Color.White,
        fontSize = 11.sp,
    )
    if (timeSource == null) {
        TimeText(backgroundColor = Color.Black) { time ->
            timeTextCurvedText(time, timeStyle)
        }
    } else {
        TimeText(
            backgroundColor = Color.Black,
            timeSource = timeSource,
        ) { time ->
            timeTextCurvedText(time, timeStyle)
        }
    }
}

/** Render the action replacing a countdown, retaining its full label on up to two lines. */
@Composable
internal fun CountdownActionButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier,
) {
    val shape = RoundedCornerShape(12.dp)
    val contentColor = Color(0xFF1F1A17)
    Text(
        text = label,
        modifier = modifier
            .alpha(if (enabled) 1f else 0.55f)
            .clip(shape)
            .background(Color(0xFFFFFDF8))
            .border(1.dp, contentColor, shape)
            .clickable(
                enabled = enabled,
                role = Role.Button,
                onClick = onClick,
            )
            .padding(horizontal = 14.dp, vertical = 4.dp),
        color = contentColor,
        fontSize = 12.sp,
        lineHeight = 14.sp,
        fontWeight = FontWeight.Medium,
        maxLines = 2,
        textAlign = TextAlign.Center,
    )
}
