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
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.WatchLater
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Compact game row in home and archived game lists.
 *
 * @param startDateTime Compact start date/time text.
 * @param headerDetail Optional detail shown after the date/time.
 * @param summaryLine Matchup or score summary text.
 */
internal class GameListEntry(
    val startDateTime: String,
    val headerDetail: String?,
    val summaryLine: String,
)

// Define primitive component heights used to calculate the size of each home-screen section.
// This lets us scale the identity area between minimum and preferred size.

// Size and space for main buttons
private val BUTTON_HEIGHT = 48.dp  // Start new game button, and others.
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
private val GAME_ROW_SUMMARY_LINE_HEIGHT = 24.dp    // Line height for matchup/score summary
private val GAME_ROW_LINE_GAP = 4.dp                // Space between start date/time and summary

// Total size of the GameListRow
private val GAME_ROW_HEIGHT =
    GAME_ROW_VERTICAL_PADDING + GAME_ROW_DATE_LINE_HEIGHT + GAME_ROW_LINE_GAP +
        GAME_ROW_SUMMARY_LINE_HEIGHT

// Height of the three central action rows plus the gap before a bottom game section.
private val ACTIONS_HEIGHT = BUTTON_HEIGHT * 3 + BUTTON_SPACER * 3

// Home-screen summary for a live or completed game.
internal fun GameState.gameListEntry(): GameListEntry {
    return GameListEntry(
        startDateTime = compactStartDateTime(),
        headerDetail = tournamentName.trim().ifEmpty { null },
        summaryLine = gameListSummaryLine(),
    )
}

private fun GameState.compactStartDateTime(): String {
    return formatCompactStartDateTime(startDate, startTime)
}

// Home screen with quick entry points for current, completed, and archived games.
@Composable
internal fun HomeScreen(
    avatar: ObserverAvatarPreference,
    currentGame: GameListEntry?,
    currentGameSectionSubtitle: String?,
    completedGamePendingArchive: GameListEntry?,
    onResumeCurrentGame: () -> Unit,
    onOpenCompletedGame: () -> Unit,
    onArchiveCompletedGame: () -> Unit,
    onStartNewGame: () -> Unit,
    onOpenAbout: () -> Unit,
    onOpenOfficialClock: () -> Unit,
    officialClockAdjusted: Boolean,
    onOpenProfile: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenArchivedGames: () -> Unit,
) {
    val fontScale = LocalDensity.current.fontScale

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
            val hasBottomGameSection = currentGame != null || completedGamePendingArchive != null

            fun Dp.scaledByFont(): Dp {
                return (value * fontScale).dp
            }
            val identityTextHeight = MIN_IDENTITY_TEXT_HEIGHT.scaledByFont()
            val preferredIdentityTextHeight = PREFERRED_IDENTITY_TEXT_HEIGHT.scaledByFont()
            val sectionTitleLineHeight = SECTION_TITLE_LINE_HEIGHT.scaledByFont()
            val gameRowHeight =
                GAME_ROW_VERTICAL_PADDING + GAME_ROW_DATE_LINE_HEIGHT.scaledByFont() +
                    GAME_ROW_LINE_GAP + GAME_ROW_SUMMARY_LINE_HEIGHT.scaledByFont()
            val currentGameCardHeight =
                SECTION_VERTICAL_PADDING + sectionTitleLineHeight + gameRowHeight + SECTION_ITEM_GAP
            val completedGameCardHeight =
                SECTION_VERTICAL_PADDING + sectionTitleLineHeight + gameRowHeight +
                    BUTTON_SPACER + BUTTON_HEIGHT + SECTION_ITEM_GAP * 3
            val minimumArtworkHeight = if (hasBottomGameSection) 0.dp else MIN_ARTWORK_HEIGHT
            val preferredGrowthHeight =
                preferredIdentityTextHeight - identityTextHeight +
                    PREFERRED_ARTWORK_HEIGHT - minimumArtworkHeight

            // Central content is the minimum identity text plus the three home action rows.
            // The artwork itself is excluded because it is the value we solve for.
            val minCentralContentHeight =
                identityTextHeight + identitySpacing * 2 + mainSpacing + ACTIONS_HEIGHT

            // Bottom section height is based on the concrete card that will render, if any.
            val bottomGameSectionHeight = when {
                currentGame != null -> currentGameCardHeight
                completedGamePendingArchive != null -> completedGameCardHeight
                else -> 0.dp
            }

            // Figure out how much to scale the artwork and identity text between the
            // minimum sizes and the preferred sizes.
            // When this is 0, there is no extra room beyond the minimum sizes.
            // When this is 1, we can scale all the way up to the preferred sizes.
            val growthScale = (
                // Find how much height remains after the minimum identity/artwork
                // budget, then use that to scale toward preferred size.
                usableHeight -
                    pagePadding * 2 -
                    minCentralContentHeight -
                    bottomGameSectionHeight -
                    minimumArtworkHeight
                ).value
                .div(preferredGrowthHeight.value)
                .coerceIn(0f, 1f)

            // Scale artwork and identity text with the same minimum-to-preferred factor.
            fun scaledHeightValue(min: Float, max: Float): Float {
                return min + growthScale * (max - min)
            }
            val artworkHeight = scaledHeightValue(
                min = minimumArtworkHeight.value,
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
                        painter = painterResource(avatar.drawableRes),
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
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        autoSize = TextAutoSize.StepBased(
                            minFontSize = 12.sp,
                            maxFontSize = subtitleFontSize,
                            stepSize = 0.25.sp,
                        ),
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
                                title = "Current game",
                                subtitle = currentGameSectionSubtitle,
                            ) {
                                GameListRow(
                                    entry = currentGame,
                                    modifier = Modifier.testTag("current-game"),
                                    onClick = onResumeCurrentGame,
                                )
                            }
                        }

                        // Show a finished-but-not-yet-archived game, if there is one.
                        if (completedGamePendingArchive != null) {
                            SectionCard(
                                title = "Completed game",
                            ) {
                                GameListRow(
                                    entry = completedGamePendingArchive,
                                    onClick = onOpenCompletedGame
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                BigActionButton(
                                    label = "Archive completed game",
                                    fullWidth = true,
                                    containerColor = PrimaryColor,
                                    contentColor = OnPrimaryColor,
                                    borderColor = null,
                                    minHeight = BUTTON_HEIGHT,
                                    onClick = onArchiveCompletedGame,
                                )
                            }
                        }
                    }
                }
            }

            IconActionButton(
                icon = Icons.Outlined.Info,
                contentDescription = "About",
                tag = "home-about",
                onClick = onOpenAbout,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(pagePadding)
            )
            IconActionButton(
                icon = Icons.Filled.WatchLater,
                contentDescription = if (officialClockAdjusted) {
                    "Official clock adjusted"
                } else {
                    "Official clock"
                },
                iconColor = if (officialClockAdjusted) ResetColor else null,
                tag = "home-official-clock",
                onClick = onOpenOfficialClock,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(pagePadding),
            )
        }
    }
}

/**
 * Render the main Home action cluster.
 *
 * @param onStartNewGame Callback starting a new setup flow.
 * @param onOpenArchivedGames Callback opening Archived games.
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
        NavigationButton(
            label = "Start new game",
            fullWidth = true,
            onClick = onStartNewGame,
        )
        NavigationButton(
            label = "See archived/saved games",
            fullWidth = true,
            colors = neutralOutlinedButtonColors(DarkNeutralColor),
            borderColor = MaterialTheme.colorScheme.outline,
            onClick = onOpenArchivedGames,
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            NavigationButton(
                label = "Profile",
                modifier = Modifier.weight(1f),
                colors = neutralOutlinedButtonColors(DarkNeutralColor),
                borderColor = MaterialTheme.colorScheme.outline,
                onClick = onOpenProfile,
            )
            NavigationButton(
                label = "Settings",
                modifier = Modifier.weight(1f),
                colors = neutralOutlinedButtonColors(DarkNeutralColor),
                borderColor = MaterialTheme.colorScheme.outline,
                onClick = onOpenSettings,
            )
        }
    }
}

/**
 * Render a tappable game row with date/time above the matchup or score summary.
 *
 * @param entry The game-list entry to display.
 * @param modifier Optional row modifier.
 * @param onClick Callback opening the game.
 */
@Composable
internal fun GameListRow(
    entry: GameListEntry,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outline,
                shape = GameRowShape,
            ),
        shape = GameRowShape,
        color = DarkNeutralColor,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            Text(
                text = entry.headerLine(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelLarge,
                maxLines = 1,
                overflow = TextOverflow.Clip,
            )
            Text(entry.summaryLine, fontWeight = FontWeight.Medium)
        }
    }
}

private fun GameListEntry.headerLine(): String {
    return headerDetail?.let { detail ->
        "$startDateTime - $detail"
    } ?: startDateTime
}
