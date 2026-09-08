package rmjarvis.ultiobserver

import androidx.compose.runtime.Composable
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
