package rmjarvis.ultiobserver

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

private const val SOURCE_CODE_URL = "https://github.com/rmjarvis/UltiObserver"
private const val PRIVACY_POLICY_URL = "https://github.com/rmjarvis/UltiObserver/blob/main/PRIVACY.md"

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun AboutScreen(
    versionName: String,
    onBackHome: () -> Unit,
) {
    val uriHandler = LocalUriHandler.current

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("About") },
                navigationIcon = {
                    TextActionButton(label = "Back", onClick = onBackHome)
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp)
                .testTag("about-screen"),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = "UltiObserver",
                style = MaterialTheme.typography.titleLarge,
            )
            Text(
                text = "Version $versionName",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = "A game management app for Ultimate observers to take the place of physical game cards and a stopwatch.",
                style = MaterialTheme.typography.bodyMedium,
            )
            HorizontalDivider()
            Text(
                text = "Source code",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = SOURCE_CODE_URL,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        onClick = {
                            uriHandler.openUri(SOURCE_CODE_URL)
                        },
                    ),
                color = PrimaryColor,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = "If you notice any bugs or have requests for features to add, please go to the above GitHub page and make an issue.",
                style = MaterialTheme.typography.bodyMedium,
            )
            HorizontalDivider()
            Text(
                text = "Privacy policy",
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = PRIVACY_POLICY_URL,
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(
                        onClick = {
                            uriHandler.openUri(PRIVACY_POLICY_URL)
                        },
                    ),
                color = PrimaryColor,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}
