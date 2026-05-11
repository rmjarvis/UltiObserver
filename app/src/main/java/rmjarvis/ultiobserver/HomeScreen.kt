package rmjarvis.ultiobserver

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.format.DateTimeFormatter

internal data class GameListEntry(
    val title: String,
    val subtitle: String,
)

internal data class ArchivedGameListEntry(
    val startDateTime: String,
    val scoreLine: String,
)

// Home-screen summary for a live or completed game.
internal fun LiveGameState.gameListEntry(): GameListEntry {
    return GameListEntry(
        title = compactStartDateTime(),
        subtitle = scoreLine(),
    )
}

// Home-screen summary for a setup draft before the first pull.
internal fun GameSetupState.gameListEntry(): GameListEntry {
    return GameListEntry(
        title = compactStartDateTime(),
        subtitle = scoreLine(),
    )
}

// Previous-games row summary with compact start time above the final score.
internal fun LiveGameState.archivedGameListEntry(): ArchivedGameListEntry {
    return ArchivedGameListEntry(
        startDateTime = compactStartDateTime(),
        scoreLine = scoreLine(),
    )
}

private fun LiveGameState.compactStartDateTime(): String {
    return "${startDate.format(DateTimeFormatter.ofPattern("M/d/yy"))} ${formatClockTime(startTime)}"
}

private fun GameSetupState.compactStartDateTime(): String {
    return "${startDate.format(DateTimeFormatter.ofPattern("M/d/yy"))} ${formatClockTime(startTime)}"
}

private fun LiveGameState.scoreLine(): String {
    return "${teamOne.name} ${teamOne.score} - ${teamTwo.score} ${teamTwo.name}"
}

private fun GameSetupState.scoreLine(): String {
    return "${teamOne.name.ifBlank { "Team 1" }} 0 - 0 ${teamTwo.name.ifBlank { "Team 2" }}"
}

// Home screen with quick entry points for current, completed, and archived games.
@Composable
internal fun HomeScreen(
    currentGame: GameListEntry?,
    currentGameSectionSubtitle: String?,
    completedGamePendingArchive: GameListEntry?,
    onResumeCurrentGame: () -> Unit,
    onOpenCompletedGame: () -> Unit,
    onArchiveCompletedGame: () -> Unit,
    onStartNewGame: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenPreviousGames: () -> Unit,
) {
    // Compose the home screen as an app identity area with navigation and game resume cards.
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // Keep the observer artwork and app title visible on every home-screen state.
            Column(
                modifier = Modifier
                    .fillMaxWidth(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.splash_observer_foul_call),
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .height(255.dp)
                        .fillMaxWidth(0.9f)
                        .testTag("home-artwork"),
                )
                Text(
                    text = "UltiObserver",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Black,
                    textAlign = TextAlign.Center,
                )
                Text(
                    text = "Game management for Ultimate observers",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            // Main home actions. Start game remains primary; the others lead to early stub pages.
            Row(
                modifier = Modifier.fillMaxWidth(0.9f),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = onStartNewGame,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Start New Game", textAlign = TextAlign.Center)
                    }
                    OutlinedButton(
                        onClick = onOpenPreviousGames,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Previous Games", textAlign = TextAlign.Center)
                    }
                }
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OutlinedButton(
                        onClick = onOpenProfile,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Profile")
                    }
                    OutlinedButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Settings")
                    }
                }
            }

            Spacer(modifier = Modifier.weight(1f))

            Box(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    // Show the currently active game, if there is one.
                    if (currentGame != null) {
                        SectionCard(
                            title = "Current Game",
                            subtitle = currentGameSectionSubtitle,
                        ) {
                            GameListRow(entry = currentGame, onClick = onResumeCurrentGame)
                        }
                    }

                    // Show a finished-but-not-yet-archived game, if there is one.
                    if (completedGamePendingArchive != null) {
                        SectionCard(
                            title = "Completed Game",
                        ) {
                            GameListRow(entry = completedGamePendingArchive, onClick = onOpenCompletedGame)
                            Spacer(modifier = Modifier.height(8.dp))
                            Button(
                                onClick = onArchiveCompletedGame,
                                modifier = Modifier.fillMaxWidth(),
                            ) {
                                Text("Archive Completed Game")
                            }
                        }
                    }
                }
            }
        }
    }
}

// Tappable row for a game listed on the home or previous-games screen.
@Composable
internal fun GameListRow(
    entry: GameListEntry,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    GameListRow(
        startDateTime = entry.title,
        scoreLine = entry.subtitle,
        modifier = modifier,
        onClick = onClick,
    )
}

// Tappable game row with date/time above the score line.
@Composable
internal fun GameListRow(
    startDateTime: String,
    scoreLine: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(16.dp),
            ),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = startDateTime,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )
            Text(scoreLine, fontWeight = FontWeight.Medium)
        }
    }
}
