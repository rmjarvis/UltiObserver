package rmjarvis.ultiobserver

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.ZoneId
import kotlinx.coroutines.delay

/** Screen for synchronizing UltiObserver's wall clock to an official tournament clock. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun OfficialClockScreen(
    currentOffsetMillis: Long,
    onOffsetChange: (Long) -> Unit,
    onBackHome: () -> Unit,
    onHome: () -> Unit,
) {
    var officialClockOffsetMillis by remember {
        mutableLongStateOf(currentOffsetMillis)
    }
    val phoneTimeMillis by produceState(initialValue = System.currentTimeMillis()) {
        while (true) {
            value = System.currentTimeMillis()
            delay(100L)
        }
    }
    val displayedOfficialTimeMillis = phoneTimeMillis + officialClockOffsetMillis

    fun updateOfficialClockOffset(updatedOffsetMillis: Long) {
        officialClockOffsetMillis = updatedOffsetMillis
        onOffsetChange(updatedOffsetMillis)
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Official clock") },
                navigationIcon = {
                    TopBarBackButton(onClick = onBackHome)
                },
                actions = {
                    TopBarHomeButton(onClick = onHome)
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(20.dp)
                .testTag("official-clock-screen"),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text =
                    "This screen lets you synchronize UltiObserver's clock to " +
                    "the tournament's official clock, so that start times and caps match the " +
                    "horns (as well as possible).",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = formatOfficialClockTime(
                    displayedOfficialTimeMillis,
                    ZoneId.systemDefault(),
                ),
                modifier = Modifier.testTag("official-clock-time"),
                style = MaterialTheme.typography.displaySmall,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
            BigActionButton(
                label = "Sync to nearest minute mark",
                fullWidth = true,
                containerColor = SecondaryColor,
                contentColor = OnSecondaryColor,
                borderColor = null,
                tag = "official-clock-nearest-minute",
                onClick = {
                    updateOfficialClockOffset(
                        syncOfficialClockOffsetToNearestMinute(
                            phoneTimeMillis = System.currentTimeMillis(),
                            currentOffsetMillis = officialClockOffsetMillis,
                        )
                    )
                },
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                BigActionButton(
                    label = "−1 min",
                    modifier = Modifier.weight(1f),
                    tag = "official-clock-minus-minute",
                    onClick = {
                        updateOfficialClockOffset(
                            adjustOfficialClockOffsetMinutes(officialClockOffsetMillis, -1)
                        )
                    },
                )
                BigActionButton(
                    label = "+1 min",
                    modifier = Modifier.weight(1f),
                    tag = "official-clock-plus-minute",
                    onClick = {
                        updateOfficialClockOffset(
                            adjustOfficialClockOffsetMinutes(officialClockOffsetMillis, 1)
                        )
                    },
                )
            }
            if (officialClockOffsetMillis != 0L) {
                BigActionButton(
                    label = "Reset to phone time",
                    fullWidth = true,
                    containerColor = ResetColor,
                    contentColor = MaterialTheme.colorScheme.onPrimary,
                    borderColor = null,
                    tag = "official-clock-reset",
                    onClick = {
                        updateOfficialClockOffset(0L)
                    },
                )
            }
            Text(
                text = describeOfficialClockOffset(officialClockOffsetMillis),
                modifier = Modifier.testTag("official-clock-offset"),
                color = MaterialTheme.colorScheme.onSurface,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
            )
            BigActionButton(
                label = "Close",
                fullWidth = true,
                tag = "official-clock-close",
                onClick = onHome,
            )
        }
    }
}
