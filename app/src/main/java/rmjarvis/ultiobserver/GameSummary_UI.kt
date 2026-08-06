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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp

/**
 * Render a game summary screen.
 *
 * @param state The game state to summarize.
 * @param completed Whether this is a completed-game summary rather than an in-progress archive.
 * @param guidanceMode Amount and duration of rule guidance shown during card-detail editing.
 * @param onStateChange Callback persisting a confirmed card-detail edit.
 * @param summaryActionText Fixed bottom action label, such as Undo End game or Restore game.
 * @param onSummaryAction Callback invoked by the fixed bottom action.
 * @param secondarySummaryActionText Optional second fixed-bottom action label.
 * @param onSecondarySummaryAction Optional callback invoked by the second fixed-bottom action.
 * @param onBack Callback returning to the previous screen.
 * @param onHome Callback returning directly to Home.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GameOverSummaryScreen(
    state: GameState,
    completed: Boolean = true,
    guidanceMode: RuleGuidanceMode,
    onStateChange: (GameState) -> Unit,
    summaryActionText: String,
    onSummaryAction: () -> Unit,
    secondarySummaryActionText: String? = null,
    onSecondarySummaryAction: (() -> Unit)? = null,
    onBack: () -> Unit,
    onHome: () -> Unit,
) {
    var showEventLogSheet by remember { mutableStateOf(false) }
    val context = LocalContext.current

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("UltiObserver") },
                navigationIcon = {
                    TopBarBackButton(onClick = onBack)
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
                .padding(8.dp),
        ) {
            GameOverSummary(
                state = state,
                completed = completed,
                guidanceMode = guidanceMode,
                onStateChange = onStateChange,
                onShowEventLog = { showEventLogSheet = true },
                onShareSummary = { context.shareGameSummary(state) },
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

}

/**
 * Render the summary content shown for completed or archived in-progress games.
 *
 * @param state The game state to summarize.
 * @param completed Whether this is a completed-game summary rather than an in-progress archive.
 * @param guidanceMode Amount and duration of rule guidance shown during card-detail editing.
 * @param onStateChange Callback persisting a confirmed card-detail edit.
 * @param onShowEventLog Callback opening the game's event log.
 * @param onShareSummary Callback opening Android's text-share sheet for this game summary.
 * @param summaryActionText Fixed bottom action label, such as Undo End game or Restore game.
 * @param onSummaryAction Callback invoked by the fixed bottom action.
 * @param secondarySummaryActionText Optional second fixed-bottom action label.
 * @param onSecondarySummaryAction Optional callback invoked by the second fixed-bottom action.
 */
@Composable
internal fun GameOverSummary(
    state: GameState,
    completed: Boolean = true,
    guidanceMode: RuleGuidanceMode,
    onStateChange: (GameState) -> Unit,
    onShowEventLog: () -> Unit,
    onShareSummary: () -> Unit,
    summaryActionText: String,
    onSummaryAction: () -> Unit,
    secondarySummaryActionText: String? = null,
    onSecondarySummaryAction: (() -> Unit)? = null,
) {
    val secondaryActionText = secondarySummaryActionText

    val summaryText = state.gameOverSummaryText()
    var teamInfoDialogTeam by remember { mutableStateOf<TeamId?>(null) }
    var cardEditorRequest by remember { mutableStateOf<SummaryCardEditorRequest?>(null) }
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
                    summaryText.fieldLine?.let { fieldLine ->
                        Text(
                            text = fieldLine,
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
                }
            }

            GameOverTeamSummary(
                teamId = TeamId.TEAM_ONE,
                team = state.teamOne,
                summaryText = state.gameOverTeamSummaryText(TeamId.TEAM_ONE),
                onTeamInfo = { teamInfoDialogTeam = TeamId.TEAM_ONE },
                onEditCards = if (editablePlayerCards(state.teamOnePlayers).isNotEmpty()) {
                    {
                        cardEditorRequest = SummaryCardEditorRequest(
                            team = TeamId.TEAM_ONE,
                            now = System.currentTimeMillis(),
                        )
                    }
                } else {
                    null
                },
            )
            GameOverTeamSummary(
                teamId = TeamId.TEAM_TWO,
                team = state.teamTwo,
                summaryText = state.gameOverTeamSummaryText(TeamId.TEAM_TWO),
                onTeamInfo = { teamInfoDialogTeam = TeamId.TEAM_TWO },
                onEditCards = if (editablePlayerCards(state.teamTwoPlayers).isNotEmpty()) {
                    {
                        cardEditorRequest = SummaryCardEditorRequest(
                            team = TeamId.TEAM_TWO,
                            now = System.currentTimeMillis(),
                        )
                    }
                } else {
                    null
                },
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

    teamInfoDialogTeam?.let { teamId ->
        TeamNamesDialog(
            team = state.teamFor(teamId),
            onDismiss = { teamInfoDialogTeam = null },
        )
    }

    cardEditorRequest?.let { request ->
        ExistingCardsEditorDialog(
            state = state,
            team = request.team,
            now = request.now,
            guidanceMode = guidanceMode,
            isLandscape = false,
            onDismiss = { cardEditorRequest = null },
            onStateUpdate = onStateChange,
        )
    }
}

/// One request to edit a team's existing cards from the summary.
private data class SummaryCardEditorRequest(
    val team: TeamId,
    val now: Long,
)

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
 * @param teamId Team id used for the optional info-button test tag.
 * @param team Team metadata used for the name and optional coach/captain info button.
 * @param summaryText Text content for the team summary section.
 * @param onTeamInfo Callback opening coach/captain details for this team.
 * @param onEditCards Optional callback opening this team's existing-card editor.
 */
@Composable
private fun GameOverTeamSummary(
    teamId: TeamId,
    team: TeamState,
    summaryText: GameOverTeamSummaryText,
    onTeamInfo: () -> Unit,
    onEditCards: (() -> Unit)?,
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
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    summaryText.teamName,
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (team.hasCoachOrCaptainInfo()) {
                    FieldInfoButton(
                        teamName = team.name,
                        contentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        onClick = onTeamInfo,
                        tag = "summary-${teamId.name}-team-info",
                    )
                }
                onEditCards?.let { editCards ->
                    IconActionButton(
                        icon = Icons.Filled.Edit,
                        contentDescription = "Edit cards for ${team.name}",
                        tag = "summary-${teamId.name}-edit-cards",
                        onClick = editCards,
                    )
                }
            }
            summaryText.issuedCardLines.forEach { line ->
                Text(line, style = MaterialTheme.typography.bodyMedium)
            }
            Text(summaryText.blueCardsLine, style = MaterialTheme.typography.bodyMedium)
            Text(summaryText.technicalFoulsLine, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
