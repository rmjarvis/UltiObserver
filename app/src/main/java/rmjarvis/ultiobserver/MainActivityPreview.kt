package rmjarvis.ultiobserver

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import java.io.File
import rmjarvis.ultiobserver.ui.theme.UltiObserverTheme

/// Render the IDE preview for the app's first screen.
@Preview(showBackground = true)
@Composable
private fun MainActivityPreview() {
    UltiObserverTheme(dynamicColor = false) {
        UltiObserverApp(
            AppViewModel(
                FileAppStateStorage(File(System.getProperty("java.io.tmpdir"), "ultiobserver-preview"))
            )
        )
    }
}
