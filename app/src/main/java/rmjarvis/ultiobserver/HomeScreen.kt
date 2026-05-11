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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

internal data class GameListEntry(
    val title: String,
    val subtitle: String,
)

// One-line home-screen summary for a live or archived game.
internal fun gameListEntry(state: LiveGameState, subtitle: String): GameListEntry {
    return GameListEntry(
        title = "${state.teamOne.name} ${state.teamOne.score} - ${state.teamTwo.score} ${state.teamTwo.name}",
        subtitle = subtitle,
    )
}

// Home screen with quick entry points for current, completed, and archived games.
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun HomeScreen(
    currentGame: GameListEntry?,
    completedGamePendingArchive: GameListEntry?,
    previousGames: List<GameListEntry>,
    onResumeCurrentGame: () -> Unit,
    onOpenCompletedGame: () -> Unit,
    onOpenPreviousGame: (Int) -> Unit,
    onArchiveCompletedGame: () -> Unit,
    onStartNewGame: () -> Unit,
) {
    val showHomeArtwork = currentGame == null && completedGamePendingArchive == null

    // Compose the home screen as a title area followed by the game lists.
    Scaffold { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            // Show the app title and the main entry point for starting a game.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    if (showHomeArtwork) {
                        Image(
                            painter = painterResource(R.drawable.splash_observer_foul_call),
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth(0.82f),
                        )
                    } else {
                        Spacer(modifier = Modifier.weight(1f))
                    }
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
                    Button(
                        onClick = onStartNewGame,
                        modifier = Modifier.fillMaxWidth(0.8f),
                    ) {
                        Text("Start New Game")
                    }
                    Spacer(modifier = Modifier.weight(if (showHomeArtwork) 0.25f else 1f))
                }
            }

            // Show the currently active game, if there is one.
            if (currentGame != null) {
                SectionCard(
                    title = "Current Game",
                    subtitle = "Tap to resume the active game.",
                ) {
                    HomeGameRow(entry = currentGame, onClick = onResumeCurrentGame)
                }
            }

            // Show a finished-but-not-yet-archived game, if there is one.
            if (completedGamePendingArchive != null) {
                SectionCard(
                    title = "Completed Game",
                ) {
                    HomeGameRow(entry = completedGamePendingArchive, onClick = onOpenCompletedGame)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onArchiveCompletedGame,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text("Archive Completed Game")
                    }
                }
            }

            // Show older archived games at the bottom.
            SectionCard(
                title = "Previous Games",
                subtitle = "Tap a finished game to view its summary.",
            ) {
                if (previousGames.isEmpty()) {
                    Text("No completed games yet.")
                } else {
                    previousGames.forEachIndexed { index, game ->
                        HomeGameRow(entry = game, onClick = { onOpenPreviousGame(index) })
                    }
                }
            }
        }
    }
}

// Tappable row for a game listed on the home screen.
@Composable
private fun HomeGameRow(
    entry: GameListEntry,
    onClick: () -> Unit,
) {
    Surface(
        modifier = Modifier
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
            Text(entry.title, fontWeight = FontWeight.Medium)
            Text(
                text = entry.subtitle,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

