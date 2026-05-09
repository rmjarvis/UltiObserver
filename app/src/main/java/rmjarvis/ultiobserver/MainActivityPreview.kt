package rmjarvis.ultiobserver

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import rmjarvis.ultiobserver.ui.theme.UltiObserverTheme

// IDE preview for the app's first screen.
@Preview(showBackground = true)
@Composable
private fun MainActivityPreview() {
    UltiObserverTheme(dynamicColor = false) {
        UltiObserverApp()
    }
}
