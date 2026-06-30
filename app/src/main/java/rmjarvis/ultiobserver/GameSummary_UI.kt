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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * Render a game as a read-only summary screen.
 *
 * @param state The game state to summarize.
 * @param completed Whether this is a completed-game summary rather than an in-progress archive.
 * @param summaryContext Optional context explaining why this summary is shown.
 * @param summaryActionText Fixed bottom action label, such as Undo End game or Restore game.
 * @param onSummaryAction Callback invoked by the fixed bottom action.
 * @param secondarySummaryActionText Optional second fixed-bottom action label.
 * @param onSecondarySummaryAction Optional callback invoked by the second fixed-bottom action.
 * @param onBack Callback returning to the previous screen.
 * @param gameOverPrompt Optional prompt shown when a live game has just ended.
 * @param onDismissGameOverPrompt Callback dismissing the optional game-over prompt.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GameOverSummaryScreen(
    state: GameState,
    completed: Boolean = true,
    summaryContext: String? = null,
    summaryActionText: String,
    onSummaryAction: () -> Unit,
    secondarySummaryActionText: String? = null,
    onSecondarySummaryAction: (() -> Unit)? = null,
    onBack: () -> Unit,
    gameOverPrompt: GamePrompt?,
    onDismissGameOverPrompt: () -> Unit,
) {
    var showEventLogSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("UltiObserver") },
                navigationIcon = {
                    TextActionButton(label = "Back", onClick = onBack)
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(8.dp),
        ) {
            GameOverSummary(
                state = state,
                completed = completed,
                onShowEventLog = { showEventLogSheet = true },
                onShareSummary = { context.shareGameSummary(state) },
                summaryContext = summaryContext,
                summaryActionText = summaryActionText,
                onSummaryAction = onSummaryAction,
                secondarySummaryActionText = secondarySummaryActionText,
                onSecondarySummaryAction = onSecondarySummaryAction,
            )
        }
    }

    if (showEventLogSheet) {
        EventLogDialog(
            state = state,
            onDismiss = { showEventLogSheet = false },
        )
    }

    if (gameOverPrompt != null) {
        AlertDialog(
            onDismissRequest = onDismissGameOverPrompt,
            title = { Text(gameOverPrompt.formatTitle()) },
            text = {
                Text(
                    text = gameOverPrompt.formatMessage(),
                    style = MaterialTheme.typography.bodyLarge,
                )
            },
            confirmButton = {
                TextActionButton(label = "OK", onClick = onDismissGameOverPrompt)
            },
        )
    }
}

/**
 * Render the read-only summary view shown for completed or archived in-progress games.
 *
 * @param state The game state to summarize.
 * @param completed Whether this is a completed-game summary rather than an in-progress archive.
 * @param onShowEventLog Callback opening the game's event log.
 * @param onShareSummary Callback opening Android's text-share sheet for this game summary.
 * @param summaryContext Optional context explaining why this summary is shown.
 * @param summaryActionText Fixed bottom action label, such as Undo End game or Restore game.
 * @param onSummaryAction Callback invoked by the fixed bottom action.
 * @param secondarySummaryActionText Optional second fixed-bottom action label.
 * @param onSecondarySummaryAction Optional callback invoked by the second fixed-bottom action.
 */
@Composable
internal fun GameOverSummary(
    state: GameState,
    completed: Boolean = true,
    onShowEventLog: () -> Unit,
    onShareSummary: () -> Unit,
    summaryContext: String? = null,
    summaryActionText: String,
    onSummaryAction: () -> Unit,
    secondarySummaryActionText: String? = null,
    onSecondarySummaryAction: (() -> Unit)? = null,
) {
    val secondaryActionText = secondarySummaryActionText

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
                colors = CardDefaults.cardColors(containerColor = EmphasizedDarkNeutralColor),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        summaryText.title,
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    summaryText.gameInformationLine?.let { gameInformationLine ->
                        Text(
                            text = gameInformationLine,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    summaryText.observersLine?.let { observersLine ->
                        Text(
                            text = observersLine,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Text(
                        text = summaryText.startLine,
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    summaryText.endLine?.let { endLine ->
                        Text(
                            text = endLine,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    summaryText.scoreLines.forEach { scoreLine ->
                        Text(
                            text = scoreLine,
                            style = MaterialTheme.typography.headlineSmall,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                    if (!summaryContext.isNullOrBlank()) {
                        Text(
                            text = summaryContext,
                            style = MaterialTheme.typography.bodyMedium,
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
                NavigationButton(
                    label = "Event log",
                    modifier = Modifier.weight(1f),
                    colors = neutralOutlinedButtonColors(DarkNeutralColor),
                    borderColor = MaterialTheme.colorScheme.outline,
                    onClick = onShowEventLog,
                )
                NavigationButton(
                    label = "Share",
                    modifier = Modifier.weight(1f),
                    colors = secondaryButtonColors(),
                    onClick = onShareSummary,
                )
            }

            NavigationButton(
                label = summaryActionText,
                fullWidth = true,
                colors = if (completed) {
                    resetButtonColors()
                } else {
                    primaryButtonColors()
                },
                onClick = onSummaryAction,
            )
            if (secondaryActionText != null) {
                NavigationButton(
                    label = secondaryActionText,
                    fullWidth = true,
                    colors = neutralOutlinedButtonColors(DarkNeutralColor),
                    borderColor = MaterialTheme.colorScheme.outline,
                    onClick = onSecondarySummaryAction!!,
                )
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
    startActivity(Intent.createChooser(sendIntent, "Share game summary"))
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
            Text(
                summaryText.teamName,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            summaryText.issuedCardLines.forEach { line ->
                Text(line, style = MaterialTheme.typography.bodyMedium)
            }
            Text(summaryText.blueCardsLine, style = MaterialTheme.typography.bodyMedium)
            Text(summaryText.technicalFoulsLine, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
