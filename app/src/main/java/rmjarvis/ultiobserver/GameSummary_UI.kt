package rmjarvis.ultiobserver

import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Render an archived game as a read-only summary with archive-level actions.
 *
 * @param state The archived game state to summarize.
 * @param onRestoreGame Callback restoring this archived game as the current game.
 * @param onBackHome Callback returning to Home.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun ArchivedGameSummaryScreen(
    state: GameState,
    onRestoreGame: () -> Unit,
    onBackHome: () -> Unit,
) {
    var showEventLogSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("UltiObserver") },
                navigationIcon = {
                    TextButton(onClick = onBackHome) {
                        Text("Back")
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
        ) {
            GameOverSummary(
                state = state,
                onShowEventLog = { showEventLogSheet = true },
                onShareSummary = { context.shareGameSummary(state) },
                summaryActionText = "Restore Game",
                onSummaryAction = onRestoreGame,
            )
        }
    }

    if (showEventLogSheet) {
        ModalBottomSheet(onDismissRequest = { showEventLogSheet = false }) {
            EventLogSheet(state = state)
        }
    }
}

/**
 * Render the read-only summary view shown once the game is over.
 *
 * @param state The completed game state to summarize.
 * @param onShowEventLog Callback opening the completed game's event log.
 * @param onShareSummary Callback opening Android's text-share sheet for this game summary.
 * @param summaryActionText Fixed bottom action label, such as Undo End Game or Restore Game.
 * @param onSummaryAction Callback invoked by the fixed bottom action.
 */
@Composable
internal fun GameOverSummary(
    state: GameState,
    onShowEventLog: () -> Unit,
    onShareSummary: () -> Unit,
    summaryActionText: String,
    onSummaryAction: () -> Unit,
) {
    val summaryText = state.gameOverSummaryText()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .navigationBarsPadding()
            .padding(bottom = 4.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(summaryText.title, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        text = summaryText.startLine,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Text(
                        text = summaryText.endLine,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    summaryText.scoreLines.forEach { scoreLine ->
                        Text(
                            text = scoreLine,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            GameOverTeamSummary(
                summaryText = state.gameOverTeamSummaryText(TeamId.TEAM_ONE),
            )
            GameOverTeamSummary(
                summaryText = state.gameOverTeamSummaryText(TeamId.TEAM_TWO),
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                OutlinedButton(
                    onClick = onShowEventLog,
                    modifier = Modifier.weight(1f),
                ) {
                    Text("Event Log")
                }
                Button(
                    onClick = onShareSummary,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF1565C0),
                        contentColor = Color.White,
                    ),
                ) {
                    Text("Share")
                }
            }

            OutlinedButton(
                onClick = onSummaryAction,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black,
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.Black),
            ) {
                Text(summaryActionText)
            }
        }
    }
}

/**
 * Open Android's standard text sharesheet for a completed-game summary.
 *
 * @param state The completed game state being shared.
 */
internal fun Context.shareGameSummary(state: GameState) {
    val sendIntent = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, "UltiObserver Game Summary")
        putExtra(Intent.EXTRA_TEXT, state.gameSummaryShareText())
    }
    startActivity(Intent.createChooser(sendIntent, "Share Game Summary"))
}

/**
 * Render one team-level section inside the game-over summary.
 *
 * @param summaryText Text content for the team summary section.
 */
@Composable
private fun GameOverTeamSummary(
    summaryText: GameOverTeamSummaryText,
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(summaryText.teamName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            summaryText.issuedCardLines.forEach { line ->
                Text(line, style = MaterialTheme.typography.bodyMedium)
            }
            Text(summaryText.blueCardsLine, style = MaterialTheme.typography.bodyMedium)
            Text(summaryText.technicalFoulsLine, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
