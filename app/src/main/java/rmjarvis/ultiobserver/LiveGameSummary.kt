package rmjarvis.ultiobserver

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

// Read-only summary view shown once the game is over.
@Composable
internal fun GameOverSummary(
    state: LiveGameState,
    onUndo: () -> Unit,
    showUndo: Boolean,
) {
    val orderedTeams = winnerFirstTeams(state)
    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
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
                state.endTime?.let { endTime ->
                    Text(
                        text = "End time ${formatClockTime(endTime)}",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
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

        if (showUndo) {
            OutlinedButton(
                onClick = onUndo,
                modifier = Modifier.fillMaxWidth(),
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                    containerColor = Color.White,
                    contentColor = Color.Black,
                ),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color.Black),
            ) {
                Text("Undo End Game")
            }
        }
    }
}

// Team-level section inside the game-over summary.
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
                    Text(buildSummaryIssuedCardText(record), style = MaterialTheme.typography.bodyMedium)
                }
            }
            Text("Blue cards ${team.blueCards}", style = MaterialTheme.typography.bodyMedium)
            Text("Technical fouls ${team.technicalFouls}", style = MaterialTheme.typography.bodyMedium)
        }
    }
}

// More readable game-over summary for one player's issued cards.
private fun buildSummaryIssuedCardText(record: InGamePlayerCardRecord): String {
    val parts = buildList {
        when (record.yellows) {
            1 -> add("Yellow card")
            2 -> add("Two yellow cards")
        }
        when (record.directReds) {
            1 -> add("Direct red card")
        }
    }
    return "${displayPlayerNumber(record.jerseyNumber)}: ${parts.joinToString("; ")}"
}

// Game-over alert text with the winner listed first.
internal fun formatGameOverSummary(state: LiveGameState): String {
    val orderedTeams = winnerFirstTeams(state)
    return buildString {
        appendLine("Game is over")
        appendLine("${orderedTeams[0].name} ${orderedTeams[0].score}")
        append("${orderedTeams[1].name} ${orderedTeams[1].score}")
    }
}

// Put the higher-scoring team first for summary display.
private fun winnerFirstTeams(state: LiveGameState): List<TeamLiveState> {
    return listOf(state.teamOne, state.teamTwo).sortedWith(
        compareByDescending<TeamLiveState> { it.score }.thenBy { it.name }
    )
}
