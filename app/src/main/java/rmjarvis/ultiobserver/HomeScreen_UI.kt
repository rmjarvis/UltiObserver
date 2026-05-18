package rmjarvis.ultiobserver

import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.time.format.DateTimeFormatter

internal data class GameListEntry(
    val title: String,
    val subtitle: String,
)

internal data class ArchivedGameListEntry(
    val startDateTime: String,
    val scoreLine: String,
)

// Define primitive component heights used to calculate the size of each home-screen section.
// This lets us scale the identity area between minimum and preferred size.

// Size and space for main buttons
private val BUTTON_HEIGHT = 48.dp  // Start New Game button, and others.
private val BUTTON_SPACER = 8.dp   // Vertical space between buttons and other content

// Minimum and preferred line heights for the title/subtitle text
private val MIN_TITLE_LINE_HEIGHT = 39.dp           // Line height for 32.sp font
private val PREFERRED_TITLE_LINE_HEIGHT = 52.dp     // Line height for 45.sp font
private val MIN_SUBTITLE_LINE_HEIGHT = 22.dp        // Line height for 14.sp font
private val PREFERRED_SUBTITLE_LINE_HEIGHT = 24.dp  // Line height for 16.sp font

// We use the sum of these in multiple places below for our spacing calculations.
private val MIN_IDENTITY_TEXT_HEIGHT = MIN_TITLE_LINE_HEIGHT + MIN_SUBTITLE_LINE_HEIGHT
private val PREFERRED_IDENTITY_TEXT_HEIGHT = PREFERRED_TITLE_LINE_HEIGHT + PREFERRED_SUBTITLE_LINE_HEIGHT

// Minimum and preferred artwork sizes
private val MIN_ARTWORK_HEIGHT = 75.dp
private val PREFERRED_ARTWORK_HEIGHT = 255.dp

// Elements of the SectionCard for completed and current games.
private val SECTION_VERTICAL_PADDING = 32.dp        // 16 dp padding at top and bottom
private val SECTION_ITEM_GAP = 10.dp                // Space between elements
private val SECTION_TITLE_LINE_HEIGHT = 28.dp       // Line height for title

// Elements of the GameListRow in the bottom SectionCard
private val GAME_ROW_VERTICAL_PADDING = 24.dp       // 12 dp padding at top and bottom
private val GAME_ROW_DATE_LINE_HEIGHT = 17.dp       // Line height for start date/time
private val GAME_ROW_SCORE_LINE_HEIGHT = 24.dp      // Line height for score
private val GAME_ROW_LINE_GAP = 4.dp                // Space between start date/time and score

// Total size of the GameListRow
private val GAME_ROW_HEIGHT =
    GAME_ROW_VERTICAL_PADDING + GAME_ROW_DATE_LINE_HEIGHT + GAME_ROW_LINE_GAP +
        GAME_ROW_SCORE_LINE_HEIGHT

// Height of the three central action rows plus the gap before a bottom game section.
private val ACTIONS_HEIGHT = BUTTON_HEIGHT * 3 + BUTTON_SPACER * 3

// Current-game card height: header row, game row, padding, and one SectionCard gap.
private val CURRENT_GAME_CARD_HEIGHT =
    SECTION_VERTICAL_PADDING + SECTION_TITLE_LINE_HEIGHT + GAME_ROW_HEIGHT + SECTION_ITEM_GAP

// Completed-game card height: title, row, explicit spacer, archive button, padding, and three SectionCard gaps.
private val COMPLETED_GAME_CARD_HEIGHT =
    SECTION_VERTICAL_PADDING + SECTION_TITLE_LINE_HEIGHT + GAME_ROW_HEIGHT +
        BUTTON_SPACER + BUTTON_HEIGHT + SECTION_ITEM_GAP * 3

// Calculate the total desired growth from min values to preferred.
// This is used to scale up from the minimum fonts and sizes once we know how much space we have.
private val MIN_TOTAL_HEIGHT = MIN_IDENTITY_TEXT_HEIGHT + MIN_ARTWORK_HEIGHT
private val PREFERRED_TOTAL_HEIGHT = PREFERRED_IDENTITY_TEXT_HEIGHT + PREFERRED_ARTWORK_HEIGHT
private val PREFERRED_GROWTH_HEIGHT = PREFERRED_TOTAL_HEIGHT - MIN_TOTAL_HEIGHT

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
    avatarPreference: ObserverAvatarPreference,
    currentGame: GameListEntry?,
    currentGameSectionSubtitle: String?,
    completedGamePendingArchive: GameListEntry?,
    onResumeCurrentGame: () -> Unit,
    onOpenCompletedGame: () -> Unit,
    onArchiveCompletedGame: () -> Unit,
    onStartNewGame: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenArchivedGames: () -> Unit,
) {
    // Compose the home screen as an app identity area with navigation and game resume cards.
    Scaffold { innerPadding ->
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            val usableHeight = maxHeight

            // Scale outer padding with screen height so tall phones get a little
            // more breathing room while smaller screens stay compact.
            val pagePadding = (usableHeight.value * 0.02f).dp.coerceIn(12.dp, 20.dp)

            // Same idea for major vertical gaps: 1.7% keeps short screens tight
            // and reaches the normal 16 dp on taller phones.
            val mainSpacing = (usableHeight.value * 0.017f).dp.coerceIn(10.dp, 16.dp)

            // Identity spacing is the tighter artwork/title/subtitle gap. 1.3%
            // scales from compact spacing on short screens to 12 dp on roomier screens.
            val identitySpacing = (usableHeight.value * 0.013f).dp.coerceIn(6.dp, 12.dp)

            // Central content is the minimum identity text plus the three home action rows.
            // The artwork itself is excluded because it is the value we solve for.
            val minCentralContentHeight =
                MIN_IDENTITY_TEXT_HEIGHT + identitySpacing * 2 + mainSpacing + ACTIONS_HEIGHT

            // Bottom section height is based on the concrete card that will render, if any.
            val bottomGameSectionHeight = when {
                currentGame != null -> CURRENT_GAME_CARD_HEIGHT
                completedGamePendingArchive != null -> COMPLETED_GAME_CARD_HEIGHT
                else -> 0.dp
            }
            val hasBottomGameSection = currentGame != null || completedGamePendingArchive != null

            // Figure out how much to scale the artwork and identity text between the
            // minimum sizes and the preferred sizes.
            // When this is 0, there is no extra room beyond the minimum sizes.
            // When this is 1, we can scale all the way up to the preferred sizes.
            val growthScale = if (hasBottomGameSection) {
                (
                    // When a bottom game card exists, find how much height remains after the min
                    // identity/artwork budget, then use that to scale toward preferred size.
                    usableHeight -
                        pagePadding * 2 -
                        minCentralContentHeight -
                        bottomGameSectionHeight -
                        MIN_ARTWORK_HEIGHT
                    ).value
                    .div(PREFERRED_GROWTH_HEIGHT.value)
                    .coerceIn(0f, 1f)
            } else {
                // With no bottom section, we can use the preferred size.
                1f
            }

            // Scale artwork and identity text with the same minimum-to-preferred factor.
            fun scaledHeightValue(min: Float, max: Float): Float {
                return min + growthScale * (max - min)
            }
            val artworkHeight = scaledHeightValue(
                min = MIN_ARTWORK_HEIGHT.value,
                max = PREFERRED_ARTWORK_HEIGHT.value,
            ).dp
            val titleFontSize = scaledHeightValue(min = 32f, max = 45f).sp
            val subtitleFontSize = scaledHeightValue(min = 14f, max = 16f).sp

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(pagePadding),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Keep the observer artwork and app title visible on every home-screen state.
                Column(
                    modifier = Modifier
                        .fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(identitySpacing),
                ) {
                    Image(
                        painter = painterResource(avatarPreference.drawableRes),
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .height(artworkHeight)
                            .fillMaxWidth(0.9f)
                            .testTag("home-artwork"),
                    )
                    Text(
                        text = "UltiObserver",
                        style = MaterialTheme.typography.displayMedium.copy(
                            fontSize = titleFontSize,
                            lineHeight = (titleFontSize.value + 7f).sp,
                        ),
                        fontWeight = FontWeight.Black,
                        textAlign = TextAlign.Center,
                    )
                    Text(
                        text = "Game management for Ultimate observers",
                        style = MaterialTheme.typography.bodyLarge.copy(
                            fontSize = subtitleFontSize,
                            lineHeight = (subtitleFontSize.value + 8f).sp,
                        ),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }

                Spacer(modifier = Modifier.height(mainSpacing))

                // Main home actions use the same one-primary layout on every phone size.
                HomeActions(
                    onStartNewGame = onStartNewGame,
                    onOpenArchivedGames = onOpenArchivedGames,
                    onOpenProfile = onOpenProfile,
                    onOpenSettings = onOpenSettings,
                    modifier = Modifier.fillMaxWidth(0.9f),
                )

                if (hasBottomGameSection) {
                    Spacer(modifier = Modifier.height(BUTTON_SPACER))
                } else {
                    Spacer(modifier = Modifier.weight(1f))
                }

                Box(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        verticalArrangement = Arrangement.spacedBy(mainSpacing),
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

            Text(
                text = "About",
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(pagePadding)
                    .clickable(onClick = onOpenAbout),
                color = MaterialTheme.colorScheme.primary,
                style = MaterialTheme.typography.labelLarge,
            )
        }
    }
}

/**
 * Render the main Home action cluster.
 *
 * @param onStartNewGame Callback starting a new setup flow.
 * @param onOpenArchivedGames Callback opening Archived Games.
 * @param onOpenProfile Callback opening Profile.
 * @param onOpenSettings Callback opening Settings.
 * @param modifier Optional layout modifier for the action column.
 */
@Composable
private fun HomeActions(
    onStartNewGame: () -> Unit,
    onOpenArchivedGames: () -> Unit,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Button(
            onClick = onStartNewGame,
            modifier = Modifier.fillMaxWidth(),
        ) {
            HomeActionText("Start New Game")
        }
        OutlinedButton(
            onClick = onOpenArchivedGames,
            modifier = Modifier.fillMaxWidth(),
        ) {
            HomeActionText("See Archived Games")
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            OutlinedButton(
                onClick = onOpenProfile,
                modifier = Modifier.weight(1f),
            ) {
                HomeActionText("Profile")
            }
            OutlinedButton(
                onClick = onOpenSettings,
                modifier = Modifier.weight(1f),
            ) {
                HomeActionText("Settings")
            }
        }
    }
}

/**
 * Render a single-line Home button label.
 *
 * @param text The label text.
 */
@Composable
private fun HomeActionText(text: String) {
    Text(
        text = text,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis,
    )
}

/**
 * Render a tappable game row from a list-entry object.
 *
 * @param entry The game-list entry containing title and subtitle.
 * @param modifier Optional row modifier.
 * @param onClick Callback opening the game.
 */
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

/**
 * Render a tappable game row with date/time above the score line.
 *
 * @param startDateTime The compact start date/time line.
 * @param scoreLine The teams and score line.
 * @param modifier Optional row modifier.
 * @param onClick Callback opening the game.
 */
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
