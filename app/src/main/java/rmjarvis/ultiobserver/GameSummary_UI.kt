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
    state: LiveGameState,
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
    state: LiveGameState,
    onShowEventLog: () -> Unit,
    onShareSummary: () -> Unit,
    summaryActionText: String,
    onSummaryAction: () -> Unit,
) {
    val orderedTeams = state.winnerFirstTeams()
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
                    Text("Game Summary", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        text = "Start ${formatStartDate(state.startDate)} ${formatClockTime(state.startTime)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    val endTime = localTimeFromEpoch(state.endEpoch!!, state.timeZone)
                    Text(
                        text = "End time ${formatClockTime(endTime)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    orderedTeams.forEach { team ->
                        Text(
                            text = "${team.name} ${team.score}",
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }

            GameOverTeamSummary(
                team = state.teamOne,
                issuedCards = state.playerCards(TeamId.TEAM_ONE),
            )
            GameOverTeamSummary(
                team = state.teamTwo,
                issuedCards = state.playerCards(TeamId.TEAM_TWO),
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
internal fun Context.shareGameSummary(state: LiveGameState) {
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
 * @param team The team state to summarize.
 * @param issuedCards Player-specific yellow/red records issued in this game.
 */
@Composable
private fun GameOverTeamSummary(
    team: TeamLiveState,
    issuedCards: List<InGamePlayerCardRecord>,
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
            Text(team.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            if (issuedCards.isEmpty()) {
                Text("No yellow or red cards issued.", style = MaterialTheme.typography.bodyMedium)
            } else {
                issuedCards.forEach { record ->
                    Text(record.summaryIssuedCardText(), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text("Blue cards ${team.blueCards}", style = MaterialTheme.typography.bodyMedium)
            Text("Technical fouls ${team.technicalFouls}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

/// Return game-over summary text for one player's issued cards.
private fun InGamePlayerCardRecord.summaryIssuedCardText(): String {
    val parts = buildList {
        when (yellows) {
            1 -> add("Yellow card")
            2 -> add("Two yellow cards")
        }
        when (reds) {
            1 -> add("Red card")
        }
    }
    return "${displayPlayerNumber(jerseyNumber)}: ${parts.joinToString("; ")}"
}
