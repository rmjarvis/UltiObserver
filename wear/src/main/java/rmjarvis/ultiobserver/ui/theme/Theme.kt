package rmjarvis.ultiobserver.ui.theme

import androidx.compose.runtime.Composable
import androidx.wear.compose.material3.MaterialTheme

/** Apply the Wear Material theme to UltiObserver companion surfaces. */
@Composable
internal fun UltiObserverTheme(content: @Composable () -> Unit) {
    MaterialTheme(content = content)
}
