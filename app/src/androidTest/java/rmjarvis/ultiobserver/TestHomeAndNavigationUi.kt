package rmjarvis.ultiobserver

import android.app.Activity
import android.app.Instrumentation
import android.content.Intent
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsNotSelected
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onLast
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.percentOffset
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performImeAction
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import java.time.LocalTime
import kotlin.math.abs
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/// Tests for Home, top-level navigation, profile, settings, and archived-game UI pathways.
@RunWith(AndroidJUnit4::class)
class TestHomeAndNavigationUi : MainActivityUiTestFixtures() {
    /**
     * Test the basic launch story from home to setup to live play.
     */
    @Test
    fun launchHomeAndStartGame() {
        setAutomaticallyAdvanceCountdowns(true)
        setAutomaticallyLockLivePoint(true)

        // Verify the app opens on the home screen with the primary navigation buttons.
        composeRule.onNodeWithText("UltiObserver").assertIsDisplayed()
        composeRule.onNodeWithTag("home-artwork").assertIsDisplayed()
        composeRule.onNodeWithText("Start new game").assertIsDisplayed()
        composeRule.onNodeWithText("Profile").assertIsDisplayed()
        composeRule.onNodeWithText("Settings").assertIsDisplayed()
        composeRule.onNodeWithText("See archived/saved games").assertIsDisplayed()
        composeRule.onNodeWithTag("home-about").assertIsDisplayed()

        // New game opens the setup screen.
        openNewGameSetup()
        composeRule.onNodeWithText("Setup game").assertIsDisplayed()

        // A brand-new draft with blank setup names should use fallback names on Home.
        pressAppBack()
        waitForText("Current game")
        composeRule.onNodeWithTag("current-game").performClick()
        waitForText("Start game")
        replaceSetupTeamName("Team 1", "Draft Team")
        replaceSetupTeamName("Team 2", "Draft Opponent")

        // Can also use the top-bar Back button to go back.
        // This time with the updated team names in the current game section.
        tapTopBarBack()
        waitForText("Start new game")
        waitForText("Current game")
        composeRule.onNodeWithText("Tap to resume").assertIsDisplayed()
        composeRule.onNodeWithTag("current-game").performClick()
        waitForText("Start game")

        // The setup screen's top-bar Home button returns to the resumable current-game row.
        tapTopBarHome()
        waitForText("Current game")
        composeRule.onNodeWithTag("current-game").performClick()
        waitForText("Start game")

        // Before the first pull, Back should return to setup for quick field-layout corrections.
        startGameFromSetup()
        assertLiveScreen()
        tapTopBarHome()
        waitForText("Current game")
        composeRule.onNodeWithTag("current-game").performClick()
        assertLiveScreen()
        pressAppBack()
        waitForText("Start game")

        // Starting a point should immediately switch the phone into locked live-use mode.
        startGameFromSetup()
        composeRule.onNodeWithText("Start point").performClick()
        waitForText("Slide right to unlock")

        // After the first pull, Back navigation should expose the current-game resume path.
        pressAppBack()
        waitForText("Current game")
        composeRule.onNodeWithText("Tap to resume").assertIsDisplayed()
        composeRule.onNodeWithText("Current game").assertIsDisplayed()
        composeRule.onNodeWithTag("current-game").performClick()
        assertLiveScreen()

        // During active play, the live screen's top-bar Home button returns to the resume row.
        tapTopBarHome()
        waitForText("Current game")
        composeRule.onNodeWithTag("current-game").performClick()
        assertLiveScreen()

        // More actions should reopen setup in update mode and return to live.
        openMoreActionsDialog()
        composeRule.onNodeWithText("Update game setup").performClick()
        waitForText("Done")
        composeRule.onNodeWithText("Cancel").performClick()
        assertLiveScreen()
        openMoreActionsDialog()
        composeRule.onNodeWithText("Update game setup").performClick()
        waitForText("Done")
        composeRule.onNodeWithText("Done").performClick()
        assertLiveScreen()

        // Live screen also has a top-bar Back button, which returns to Home now.
        tapTopBarBack()
        waitForText("Current game")
    }

    /**
     * Test that an active game accidentally archived by Start new game can be restored
     * back into a live game state.
     */
    @Test
    fun archivedGamesRestoreActiveGame() {
        clearArchivedGamesProgrammatically()
        val suffix = System.currentTimeMillis().toString().takeLast(6)
        val teamOneName = "RestoreA$suffix"
        val teamTwoName = "RestoreB$suffix"
        val archivedTitle = "$teamOneName 0 - 0 $teamTwoName"
        val currentTeamOneName = "CurrentA$suffix"
        val currentTeamTwoName = "CurrentB$suffix"
        val currentArchivedTitle = "$currentTeamOneName 0 - 0 $currentTeamTwoName"

        // Archive one active live-point game, then start another current game before restoring
        // the first.
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.appViewModel.deleteCurrentGame()
            activity.appViewModel.seedCurrentInProgressGame(teamOneName, teamTwoName)
            activity.appViewModel.startNewGame(now = 123_000L)
            activity.appViewModel.seedCurrentInProgressGame(
                currentTeamOneName,
                currentTeamTwoName,
            )
            activity.appViewModel.goHome()
        }
        composeRule.waitForIdle()

        // The in-progress archive list shows the current game separately from saved games,
        // and its summary returns directly to that current live game.
        openSavedInProgressGamesScreen()
        waitForText("Current game")
        waitForText("Saved games")
        waitForText(currentArchivedTitle)
        composeRule.onNodeWithTag("current-in-progress-game").performClick()
        waitForText("Game summary")
        composeRule.onNodeWithText("Back to game").performClick()
        waitForText("More actions")
        composeRule.onNodeWithText(currentTeamOneName).assertIsDisplayed()
        composeRule.onNodeWithText(currentTeamTwoName).assertIsDisplayed()
        tapTopBarBack()

        // Restoring the archived game replaces the current game and returns to the live screen.
        // When the archived game is an active live-point game, the undo buttons are preserved,
        // unlike when the game was restored from a completed state.
        openSavedInProgressGamesScreen()
        waitForText(archivedTitle)
        composeRule.onNodeWithText(archivedTitle).performClick()
        waitForText("Game summary")
        composeRule.onNodeWithText("Make current").performClick()

        composeRule.onNodeWithTag(teamActionTag(TeamId.TEAM_ONE, "card")).assertIsDisplayed()
        waitForText("More actions")
        composeRule.onNodeWithText(teamOneName).assertIsDisplayed()
        composeRule.onNodeWithText(teamTwoName).assertIsDisplayed()
        assertTrue(
            composeRule.onAllNodesWithText(currentTeamOneName, substring = true)
                .fetchSemanticsNodes()
                .isEmpty()
        )
        assertTrue(
            composeRule.onAllNodesWithText(currentTeamTwoName, substring = true)
                .fetchSemanticsNodes()
                .isEmpty()
        )
        composeRule.onNodeWithText("Undo Start point").assertIsDisplayed()

        // The restored game appears only as the current-game row, and the replaced current game
        // becomes the single saved in-progress row.
        tapTopBarBack()
        waitForText("Current game")
        openSavedInProgressGamesScreen()
        waitForText(archivedTitle)
        waitForText(currentArchivedTitle)
        composeRule.onNodeWithText(currentTeamOneName, substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(currentTeamTwoName, substring = true).assertIsDisplayed()
        composeRule.onAllNodesWithTag("current-in-progress-game").assertCountEquals(1)
        composeRule.onAllNodesWithTag("saved-in-progress-game-0").assertCountEquals(1)
        composeRule.onAllNodesWithTag("saved-in-progress-game-1").assertCountEquals(0)
        composeRule.onNodeWithTag("current-in-progress-game")
            .assertTextContains(archivedTitle, substring = true)
        composeRule.onNodeWithTag("saved-in-progress-game-0")
            .assertTextContains(currentArchivedTitle, substring = true)
    }

    /**
     * Test that the Archived games screen can delete games that are listed there.
     * Both single game deletion and delete all are possible.
     */
    @Test
    fun archivedGamesCanDeleteArchivedGameAfterSliderConfirmation() {
        // Archived games should be reachable from Home before archived-game flows are populated.
        clearArchivedGamesProgrammatically()
        openArchivedGamesScreen()
        composeRule.onNodeWithTag("archived-games-screen").assertIsDisplayed()
        composeRule.onNodeWithText("Archived games (0)").assertIsEnabled()
        composeRule.onNodeWithText("In-progress games (0)").assertIsEnabled()
        composeRule.onNodeWithText("Saved setup drafts (0)").assertIsEnabled()
        composeRule.onNodeWithText("Archived games (0)").performClick()
        waitForText("No archived games yet.")
        tapTopBarBack()
        waitForText("Archived/saved games")
        tapTopBarHome()
        waitForText("Start new game")

        // Build two uniquely named archived rows so delete assertions cannot match stale test data.
        val suffix = System.currentTimeMillis().toString().takeLast(6)
        val firstTeamOne = "DelA$suffix"
        val firstTeamTwo = "DelB$suffix"
        val secondTeamOne = "DelC$suffix"
        val secondTeamTwo = "DelD$suffix"
        val firstArchivedTitle = "$firstTeamOne 0 - 0 $firstTeamTwo"
        val secondArchivedTitle = "$secondTeamOne 0 - 0 $secondTeamTwo"

        // The landing page can delete every archived/saved game across categories.
        seedArchivedGameProgrammatically(firstTeamOne, firstTeamTwo)
        seedArchivedGameProgrammatically(secondTeamOne, secondTeamTwo)
        openArchivedGamesScreen()
        composeRule.onNodeWithText("Archived games (2)").assertIsDisplayed()
        composeRule.onNodeWithTag("delete-all-archived-games").performClick()
        waitForText("all archived and saved games", substring = true)
        dismissDialog(text = "Cancel")
        composeRule.onNodeWithText("Archived games (2)").assertIsDisplayed()
        composeRule.onNodeWithTag("delete-all-archived-games").performClick()
        confirmDeleteWithSlider("Delete all games?")
        waitForText("Archived games (0)")
        waitForText("In-progress games (0)")
        waitForText("Saved setup drafts (0)")
        tapTopBarBack()

        // Inside one category, delete all removes only the visible category's rows.
        seedArchivedGameProgrammatically(firstTeamOne, firstTeamTwo)
        seedArchivedGameProgrammatically(secondTeamOne, secondTeamTwo)
        openArchivedCompleteGamesScreen()
        waitForText(firstArchivedTitle)
        waitForText(secondArchivedTitle)

        // Opening an archived row should expose its summary and persisted event log.
        composeRule.onNodeWithTag("archived-game-0").performClick()
        waitForText("Game summary")
        composeRule.onNodeWithText("Share").assertIsDisplayed()
        composeRule.onNodeWithText("Event log").performClick()
        waitForText("Event log")
        waitForText("No events logged yet.")
        dismissDialog(text = "OK")
        tapTopBarHome()
        waitForText("Start new game")
        openArchivedCompleteGamesScreen()
        waitForText("Archived games")
        waitForText(firstArchivedTitle)
        waitForText(secondArchivedTitle)

        // Delete all gives a warning and requires a slide to confirm.
        // This time cancel to get out of the dialog.
        composeRule.onNodeWithTag("delete-all-archived-games").performClick()
        waitForText("This cannot be undone", substring = true)
        dismissDialog(text = "Cancel")
        composeRule.onNodeWithText(firstArchivedTitle).assertIsDisplayed()
        composeRule.onNodeWithText(secondArchivedTitle).assertIsDisplayed()

        // This time go ahead and delete all the games.
        // We should end up with no games listed.
        composeRule.onNodeWithTag("delete-all-archived-games").performClick()
        waitForText("This cannot be undone", substring = true)
        confirmDeleteWithSlider("Delete all games?")
        waitForText("No archived games yet.")
        assertTrue(
            composeRule.onAllNodesWithText(firstArchivedTitle).fetchSemanticsNodes().isEmpty()
        )
        assertTrue(
            composeRule.onAllNodesWithText(secondArchivedTitle).fetchSemanticsNodes().isEmpty()
        )
        tapTopBarHome()
        waitForText("Start new game")

        // Re-seed the archive and verify cancelling a single-game delete leaves both rows intact.
        seedArchivedGameProgrammatically(firstTeamOne, firstTeamTwo)
        seedArchivedGameProgrammatically(secondTeamOne, secondTeamTwo)
        openArchivedCompleteGamesScreen()
        waitForText(firstArchivedTitle)
        waitForText(secondArchivedTitle)
        composeRule.onNodeWithTag("delete-archived-game-0").performClick()
        waitForText("This cannot be undone", substring = true)
        dismissDialog(text = "Cancel")
        composeRule.onNodeWithText(firstArchivedTitle).assertIsDisplayed()
        composeRule.onNodeWithText(secondArchivedTitle).assertIsDisplayed()

        // Confirm a single-game delete removes only the selected archived row.
        composeRule.onNodeWithTag("delete-archived-game-0").performClick()
        confirmDeleteWithSlider()
        waitForText(secondArchivedTitle)
        assertTrue(
            composeRule.onAllNodesWithText(firstArchivedTitle).fetchSemanticsNodes().isEmpty()
        )

        // Delete the last archived row and verify Archived games returns to its empty state.
        composeRule.onNodeWithTag("delete-archived-game-0").performClick()
        confirmDeleteWithSlider()
        waitForText("No archived games yet.")
        assertTrue(
            composeRule.onAllNodesWithText(secondArchivedTitle).fetchSemanticsNodes().isEmpty()
        )
    }

    /**
     * Test filtering and sorting archived games without affecting other saved categories.
     */
    @Test
    fun archivedGamesFilterAndSort() {
        clearArchivedGamesProgrammatically()
        clearCurrentGameProgrammatically()
        val suffix = System.currentTimeMillis().toString().takeLast(6)
        val summerTournament = "Summer$suffix"
        val fallTournament = "Fall$suffix"
        val summerOpenTitle = "Aone$suffix 0 - 0 Zed$suffix"
        val summerMixedTitle = "Bone$suffix 0 - 0 Alpha$suffix"
        val fallOpenTitle = "Cone$suffix 0 - 0 Mid$suffix"
        val today = LocalDate.now()

        // Seed three archives whose tournament, division, and team values differ.
        seedArchiveForFilterTest(
            tournament = summerTournament,
            division = GameDivision.OPEN,
            teamOne = "Aone$suffix",
            teamTwo = "Zed$suffix",
            startDate = LocalDate.of(2026, 5, 1),
        )
        seedArchiveForFilterTest(
            tournament = summerTournament,
            division = GameDivision.MIXED,
            teamOne = "Bone$suffix",
            teamTwo = "Alpha$suffix",
            startDate = LocalDate.of(2026, 5, 2),
        )
        seedArchiveForFilterTest(
            tournament = fallTournament,
            division = GameDivision.OPEN,
            teamOne = "Cone$suffix",
            teamTwo = "Mid$suffix",
            startDate = today,
        )

        // The date filter dialog previews each preset range.
        openArchivedCompleteGamesScreen()
        composeRule.onNodeWithTag("archive-filter-button").performClick()
        composeRule.onNodeWithTag("archive-filter-field-DATE").performClick()
        composeRule.onNodeWithTag("archive-date-preset-TODAY").performClick()
        assertArchiveDateRange(today, today)
        composeRule.onNodeWithTag("archive-date-preset-LAST_7_DAYS").performClick()
        assertArchiveDateRange(today.minusDays(7), today)
        composeRule.onNodeWithTag("archive-date-preset-LAST_30_DAYS").performClick()
        assertArchiveDateRange(today.minusDays(30), today)
        composeRule.onNodeWithTag("archive-date-preset-THIS_YEAR").performClick()
        assertArchiveDateRange(LocalDate.of(today.year, 1, 1), today)
        composeRule.onNodeWithTag("archive-custom-start-date").performClick()
        composeRule.onAllNodesWithText("Cancel").onLast().performClick()
        composeRule.onNodeWithTag("archive-custom-end-date").performClick()
        composeRule.onAllNodesWithText("Cancel").onLast().performClick()
        assertArchiveDateRange(LocalDate.of(today.year, 1, 1), today)

        // A custom start date can be committed without an end date.
        composeRule.onNodeWithTag("archive-clear-filter-DATE").performClick()
        waitForText("Start: None")
        waitForText("End: None")
        composeRule.onNodeWithTag("archive-custom-start-date").performClick()
        composeRule.onAllNodesWithText("Cancel").onLast().performClick()
        waitForText("Start: None")
        waitForText("End: None")
        composeRule.onNodeWithTag("archive-custom-start-date").performClick()
        composeRule.onNodeWithTag("archive-date-set").performClick()
        waitForText("Start: ${formatStartDate(today)}")
        waitForText("End: None")
        composeRule.onNodeWithText("Done").performClick()
        waitForText("Date range: on or after ${formatStartDate(today)}", substring = true)
        waitForText(fallOpenTitle)
        assertTrue(composeRule.onAllNodesWithText(summerOpenTitle).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText(summerMixedTitle).fetchSemanticsNodes().isEmpty())

        // A custom end date can also be committed without a start date.
        composeRule.onNodeWithTag("archive-filter-button").performClick()
        composeRule.onNodeWithTag("archive-filter-field-DATE").performClick()
        composeRule.onNodeWithTag("archive-clear-filter-DATE").performClick()
        waitForText("Start: None")
        waitForText("End: None")
        composeRule.onNodeWithTag("archive-custom-end-date").performClick()
        composeRule.onNodeWithTag("archive-date-set").performClick()
        waitForText("Start: None")
        waitForText("End: ${formatStartDate(today)}")
        composeRule.onNodeWithText("Done").performClick()
        waitForText("Date range: on or before ${formatStartDate(today)}", substring = true)
        waitForText(summerOpenTitle)
        waitForText(summerMixedTitle)
        waitForText(fallOpenTitle)

        // Filtering the games to just today shows the one game we have on that date.
        composeRule.onNodeWithTag("archive-filter-button").performClick()
        composeRule.onNodeWithTag("archive-filter-field-DATE").performClick()
        composeRule.onNodeWithTag("archive-clear-filter-DATE").performClick()
        composeRule.onNodeWithTag("archive-date-preset-TODAY").performClick()
        assertArchiveDateRange(today, today)
        composeRule.onNodeWithText("Done").performClick()
        composeRule.onNodeWithTag("archive-filter-field-DATE").assertTextEquals("Date")
        composeRule.onNodeWithText("Done").performClick()
        waitForText(
            "Date range: ${formatStartDate(today)} - ${formatStartDate(today)}",
            substring = true,
        )
        waitForText(fallOpenTitle)
        assertTrue(composeRule.onAllNodesWithText(summerOpenTitle).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText(summerMixedTitle).fetchSemanticsNodes().isEmpty())
        composeRule.onAllNodesWithTag("archived-game-0").assertCountEquals(1)
        composeRule.onAllNodesWithTag("archived-game-1").assertCountEquals(0)
        composeRule.onNodeWithTag("archive-filter-button").performClick()
        composeRule.onNodeWithTag("archive-filter-field-DATE").performClick()
        composeRule.onNodeWithTag("archive-clear-filter-DATE").performClick()
        composeRule.onNodeWithText("Done").performClick()

        // The other filter pages all use checkbox rows with counts.  Selecting compatible
        // tournament, division, level, team, and observer values narrows the list to one game.
        composeRule.onNodeWithTag("archive-filter-field-TOURNAMENT").performClick()
        composeRule.onNodeWithTag("archive-filter-value-$summerTournament")
            .assertTextContains("$summerTournament (2)", substring = true)
        // Both the checkbox and the rest of its row toggle the value.  Reselect so the combined
        // filter story below still narrows to the summer/open game.
        composeRule.onNodeWithTag("archive-filter-checkbox-$summerTournament").performClick()
        composeRule.onNodeWithTag("archive-filter-value-$summerTournament").performClick()
        composeRule.onNodeWithTag("archive-filter-checkbox-$summerTournament").performClick()
        composeRule.onNodeWithText("Back").performClick()
        composeRule.onNodeWithTag("archive-filter-field-DIVISION").performClick()
        composeRule.onNodeWithTag("archive-filter-value-Open").performClick()
        composeRule.onNodeWithText("Back").performClick()
        composeRule.onNodeWithTag("archive-filter-field-LEVEL").performClick()
        composeRule.onNodeWithTag("archive-filter-value-Club").performClick()
        composeRule.onNodeWithText("Back").performClick()
        composeRule.onNodeWithTag("archive-filter-field-TEAM").performClick()
        composeRule.onNodeWithTag("archive-filter-value-Aone$suffix").performClick()
        composeRule.onNodeWithText("Back").performClick()
        composeRule.onNodeWithTag("archive-filter-field-OBSERVERS").performClick()
        composeRule.onNodeWithTag("archive-filter-value-Mike").performClick()
        composeRule.onNodeWithText("Back").performClick()
        composeRule.onNodeWithText("Done").performClick()
        waitForText(summerOpenTitle)
        assertTrue(composeRule.onAllNodesWithText(summerMixedTitle).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText(fallOpenTitle).fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithText("Filter").assertIsDisplayed()
        composeRule.onNodeWithTag("archive-filter-and-sort-summary")
            .assertTextContains("Filters:", substring = true)
            .assertTextContains("Tournament: $summerTournament", substring = true)
            .assertTextContains("Division: Open", substring = true)
            .assertTextContains("Level: Club", substring = true)
            .assertTextContains("Team: Aone$suffix", substring = true)
            .assertTextContains("Observer: Mike", substring = true)

        // A single checkbox-style filter can be cleared from its own filter page.
        composeRule.onNodeWithTag("archive-filter-button").performClick()
        composeRule.onNodeWithTag("archive-filter-field-TOURNAMENT").performClick()
        composeRule.onNodeWithTag("archive-clear-filter-TOURNAMENT").performClick()
        composeRule.onNodeWithText("Back").performClick()
        composeRule.onNodeWithText("Done").performClick()
        composeRule.onNodeWithTag("archive-filter-and-sort-summary")
            .assertTextContains("Filters:", substring = true)
            .assertTextContains("Division: Open", substring = true)
            .assertTextContains("Level: Club", substring = true)
            .assertTextContains("Team: Aone$suffix", substring = true)
            .assertTextContains("Observer: Mike", substring = true)

        // Clearing filters restores all three rows.
        composeRule.onNodeWithTag("archive-filter-button").performClick()
        composeRule.onNodeWithTag("archive-clear-filters").performClick()
        composeRule.onNodeWithText("Done").performClick()
        waitForText(summerMixedTitle)
        waitForText(fallOpenTitle)

        // Every sort choice can reorder the restored archive list.
        selectArchiveSort(ArchiveSortMode.DATE_NEWEST)
        assertArchiveRowsInOrder(fallOpenTitle, summerMixedTitle, summerOpenTitle)
        selectArchiveSort(ArchiveSortMode.DATE_OLDEST)
        assertArchiveRowsInOrder(summerOpenTitle, summerMixedTitle, fallOpenTitle)
        selectArchiveSort(ArchiveSortMode.TEAM_ONE)
        assertArchiveRowsInOrder(summerOpenTitle, summerMixedTitle, fallOpenTitle)
        selectArchiveSort(ArchiveSortMode.TEAM_TWO)
        assertArchiveRowsInOrder(summerMixedTitle, fallOpenTitle, summerOpenTitle)

        // Delete all in a filtered archive list deletes only the currently shown rows.
        composeRule.onNodeWithTag("archive-filter-button").performClick()
        composeRule.onNodeWithTag("archive-filter-field-TOURNAMENT").performClick()
        composeRule.onNodeWithTag("archive-filter-value-$summerTournament").performClick()
        composeRule.onNodeWithText("Back").performClick()
        composeRule.onNodeWithText("Done").performClick()
        waitForText(summerOpenTitle)
        waitForText(summerMixedTitle)
        assertTrue(composeRule.onAllNodesWithText(fallOpenTitle).fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithTag("delete-all-archived-games").performClick()
        waitForText("currently displayed archived games", substring = true)
        confirmDeleteWithSlider("Delete all games?")
        waitForText("No archived games match these filters.")
        composeRule.onAllNodesWithTag("delete-all-archived-games").assertCountEquals(0)
        composeRule.onNodeWithTag("archive-filter-button").performClick()
        composeRule.onNodeWithTag("archive-clear-filters").performClick()
        composeRule.onNodeWithText("Done").performClick()
        waitForText(fallOpenTitle)
        assertTrue(composeRule.onAllNodesWithText(summerOpenTitle).fetchSemanticsNodes().isEmpty())
        assertTrue(composeRule.onAllNodesWithText(summerMixedTitle).fetchSemanticsNodes().isEmpty())
        composeRule.onAllNodesWithTag("archived-game-0").assertCountEquals(1)
        composeRule.onAllNodesWithTag("archived-game-1").assertCountEquals(0)

        // If the active filters include the last completed game and that game is deleted, the
        // controls stay available so the user can clear the filters from the empty result page.
        composeRule.onNodeWithTag("archive-filter-button").performClick()
        composeRule.onNodeWithTag("archive-filter-field-TOURNAMENT").performClick()
        composeRule.onNodeWithTag("archive-filter-value-$fallTournament").performClick()
        composeRule.onNodeWithText("Back").performClick()
        composeRule.onNodeWithText("Done").performClick()
        waitForText(fallOpenTitle)
        composeRule.onNodeWithTag("delete-all-archived-games").performClick()
        waitForText("currently displayed archived games", substring = true)
        confirmDeleteWithSlider("Delete all games?")
        waitForText("No archived games match these filters.")
        composeRule.onAllNodesWithTag("delete-all-archived-games").assertCountEquals(0)
        composeRule.onNodeWithTag("archive-filter-button").performClick()
        composeRule.onNodeWithTag("archive-filter-field-TEAM").performClick()
        waitForText("No values available.")
        composeRule.onNodeWithText("Back").performClick()
        composeRule.onNodeWithTag("archive-clear-filters").performClick()
        composeRule.onNodeWithText("Done").performClick()
        waitForText("No archived games yet.")

        // Other saved categories do not show archive filter controls.
        tapTopBarBack()
        composeRule.onNodeWithText("In-progress games", substring = true).performClick()
        waitForText("In-progress games")
        composeRule.onAllNodesWithTag("archive-filter-button").assertCountEquals(0)
        tapTopBarHome()
        waitForText("Start new game")
    }

    /**
     * Test saving a setup state for later and directly archiving a saved in-progress game.
     */
    @Test
    fun savedGames() {
        clearArchivedGamesProgrammatically()
        clearCurrentGameProgrammatically()

        // Start setting up a game, then save it for later.
        val suffix = System.currentTimeMillis().toString().takeLast(6)
        val setupTeamOne = "SetupA$suffix"
        val setupTeamTwo = "SetupB$suffix"
        val savedSetupTitle = "$setupTeamOne vs $setupTeamTwo"
        val setupField = suffix
        openNewGameSetup()
        replaceSetupTeamName("Team 1", setupTeamOne)
        replaceSetupTeamName("Team 2", setupTeamTwo)
        openGameInformationSetupEditor()
        composeRule.onNodeWithTag("setup-field-name").performTextReplacement(setupField)
        composeRule.onNodeWithTag("setup-field-name").performImeAction()
        closeSetupEditor()
        composeRule.onNodeWithText("Save as a draft").performClick()
        waitForText("Start new game")

        // Without a current setup draft, the setup archive page shows only saved setup drafts.
        openArchivedGamesScreen()
        composeRule.onNodeWithText("Saved setup drafts (1)").performClick()
        waitForText(savedSetupTitle, substring = true)
        waitForText("on field $setupField", substring = true)
        composeRule.onAllNodesWithTag("current-setup-state").assertCountEquals(0)
        composeRule.onAllNodesWithTag("saved-setup-state-0").assertCountEquals(1)
        tapTopBarBack()
        tapTopBarBack()

        // Start a different current setup draft, then verify the setup archive page separates
        // that current draft from the saved setup drafts.
        val currentSetupTeamOne = "CurrentSetupA$suffix"
        val currentSetupTeamTwo = "CurrentSetupB$suffix"
        openNewGameSetup()
        replaceSetupTeamName("Team 1", currentSetupTeamOne)
        replaceSetupTeamName("Team 2", currentSetupTeamTwo)
        tapTopBarBack()

        openArchivedGamesScreen()
        composeRule.onNodeWithText("Saved setup drafts (2)").assertIsEnabled()
        composeRule.onNodeWithText("Saved setup drafts (2)").performClick()
        waitForText("Current setup")
        waitForText("Saved setup drafts")
        val currentSetupTitle = "$currentSetupTeamOne vs $currentSetupTeamTwo"
        waitForText(currentSetupTitle, substring = true)
        waitForText(savedSetupTitle, substring = true)
        composeRule.onAllNodesWithTag("current-setup-state").assertCountEquals(1)
        composeRule.onAllNodesWithTag("saved-setup-state-0").assertCountEquals(1)
        composeRule.onAllNodesWithTag("saved-setup-state-1").assertCountEquals(0)
        composeRule.onNodeWithTag("current-setup-state")
            .assertTextContains(currentSetupTitle, substring = true)
        composeRule.onNodeWithTag("saved-setup-state-0")
            .assertTextContains(savedSetupTitle, substring = true)

        // The current setup row resumes the editable setup draft.
        composeRule.onNodeWithTag("current-setup-state").performClick()
        waitForText("Start game")
        composeRule.onNodeWithText(currentSetupTeamOne).assertIsDisplayed()
        composeRule.onNodeWithText(currentSetupTeamTwo).assertIsDisplayed()
        tapTopBarBack()

        // The saved setup row edits the saved draft in place until it is explicitly made current.
        val editedSetupTeamTwo = "EditedSetupB$suffix"
        val editedSavedSetupTitle = "$setupTeamOne vs $editedSetupTeamTwo"
        openArchivedGamesScreen()
        composeRule.onNodeWithText("Saved setup drafts (2)").performClick()
        composeRule.onNodeWithTag("saved-setup-state-0").performClick()
        waitForText("Saved setup draft")
        waitForText("Make current")
        waitForText("Save draft")
        val makeCurrentBounds = composeRule.onNodeWithText("Make current")
            .fetchSemanticsNode()
            .boundsInRoot
        val saveDraftBounds = composeRule.onNodeWithText("Save draft")
            .fetchSemanticsNode()
            .boundsInRoot
        assertTrue(makeCurrentBounds.bottom <= saveDraftBounds.top)
        assertTrue(abs(makeCurrentBounds.width - saveDraftBounds.width) < 1f)
        composeRule.onNodeWithText(setupTeamOne).assertIsDisplayed()
        composeRule.onNodeWithText(setupTeamTwo).assertIsDisplayed()
        replaceSetupTeamName("Team 2", editedSetupTeamTwo)
        tapTopBarBack()
        waitForText("Saved setup drafts")
        composeRule.onNodeWithTag("current-setup-state")
            .assertTextContains(currentSetupTitle, substring = true)
        composeRule.onNodeWithTag("saved-setup-state-0")
            .assertTextContains(editedSavedSetupTitle, substring = true)

        // Save draft returns to the saved setup draft list without making the draft current.
        composeRule.onNodeWithTag("saved-setup-state-0").performClick()
        waitForText("Saved setup draft")
        composeRule.onNodeWithText("Save draft").performClick()
        waitForText("Saved setup drafts")
        composeRule.onNodeWithTag("current-setup-state")
            .assertTextContains(currentSetupTitle, substring = true)
        composeRule.onNodeWithTag("saved-setup-state-0")
            .assertTextContains(editedSavedSetupTitle, substring = true)

        // Make current performs the swap, saving the previous current setup draft aside.
        composeRule.onNodeWithTag("saved-setup-state-0").performClick()
        waitForText("Saved setup draft")
        composeRule.onNodeWithText("Make current").performClick()
        waitForText("Start game")
        composeRule.onNodeWithText(setupTeamOne).assertIsDisplayed()
        composeRule.onNodeWithText(editedSetupTeamTwo).assertIsDisplayed()
        tapTopBarBack()
        openArchivedGamesScreen()
        composeRule.onNodeWithText("Saved setup drafts (2)").performClick()
        composeRule.onNodeWithTag("current-setup-state")
            .assertTextContains(editedSavedSetupTitle, substring = true)
        composeRule.onNodeWithTag("saved-setup-state-0")
            .assertTextContains(currentSetupTitle, substring = true)

        // A single saved setup row can be deleted without removing the current setup draft.
        composeRule.onNodeWithTag("delete-saved-setup-state-0").performClick()
        composeRule.onNodeWithText("Cancel").performClick()
        composeRule.onNodeWithTag("saved-setup-state-0")
            .assertTextContains(currentSetupTitle, substring = true)
        composeRule.onNodeWithTag("delete-saved-setup-state-0").performClick()
        confirmDeleteWithSlider()
        composeRule.onAllNodesWithTag("saved-setup-state-0").assertCountEquals(0)
        composeRule.onNodeWithTag("current-setup-state")
            .assertTextContains(editedSavedSetupTitle, substring = true)

        // Delete all in the saved setup section removes only the saved setup rows.
        val bulkCurrentSetupTitle = "BulkCurrentSetupA$suffix vs BulkCurrentSetupB$suffix"
        seedCurrentSetupAndSavePrevious("BulkCurrentSetupA$suffix", "BulkCurrentSetupB$suffix")
        openArchivedGamesScreen()
        composeRule.onNodeWithText("Saved setup drafts (2)").performClick()
        waitForText("Saved setup drafts")
        waitForText(bulkCurrentSetupTitle, substring = true)
        waitForText(editedSavedSetupTitle, substring = true)
        composeRule.onNodeWithTag("delete-all-archived-games").performClick()
        waitForText("all saved setup drafts", substring = true)
        confirmDeleteWithSlider("Delete all games?")
        composeRule.onAllNodesWithTag("saved-setup-state-0").assertCountEquals(0)
        composeRule.onNodeWithTag("current-setup-state")
            .assertTextContains(bulkCurrentSetupTitle, substring = true)

        // The current setup row can also be deleted from this category after confirmation.
        composeRule.onNodeWithTag("delete-current-setup-state").performClick()
        dismissDialog(text = "Cancel")
        composeRule.onNodeWithTag("current-setup-state")
            .assertTextContains(bulkCurrentSetupTitle, substring = true)
        composeRule.onNodeWithTag("delete-current-setup-state").performClick()
        confirmDeleteWithSlider()
        waitForText("No saved setup drafts.")
        tapTopBarHome()
        waitForText("Start new game")

        // Without saved in-progress games, the in-progress archive page shows only the current
        // active game.
        val currentProgressTitle = "CurrentProgressA$suffix 0 - 0 CurrentProgressB$suffix"
        seedCurrentInProgressGame("CurrentProgressA$suffix", "CurrentProgressB$suffix")
        openArchivedGamesScreen()
        composeRule.onNodeWithText("In-progress games (1)").performClick()
        waitForText(currentProgressTitle)
        composeRule.onAllNodesWithTag("current-in-progress-game").assertCountEquals(1)
        composeRule.onAllNodesWithTag("saved-in-progress-game-0").assertCountEquals(0)

        // The current in-progress row can also be deleted from this category after confirmation.
        composeRule.onNodeWithTag("delete-current-in-progress-game").performClick()
        dismissDialog(text = "Cancel")
        composeRule.onNodeWithTag("current-in-progress-game")
            .assertTextContains(currentProgressTitle, substring = true)
        composeRule.onNodeWithTag("delete-current-in-progress-game").performClick()
        confirmDeleteWithSlider()
        waitForText("No in-progress games.")

        // Start an in-progress game, but then start another new game.  This moves what was
        // the current game into the saved games section of the in-progress archive.
        seedSavedInProgressGame("ProgressA$suffix", "ProgressB$suffix")

        // Go to the archive screen, find that saved game.  You can either reopen it or archive
        // it. Here we archive it.
        openArchivedGamesScreen()
        composeRule.onNodeWithText("In-progress games (1)").performClick()
        waitForText("ProgressA$suffix 0 - 0 ProgressB$suffix")
        composeRule.onNodeWithTag("saved-in-progress-game-0").performClick()
        waitForText("Game summary")
        composeRule.onNodeWithText("Make current").assertIsDisplayed()
        tapTopBarBack()
        waitForText("ProgressA$suffix 0 - 0 ProgressB$suffix")
        composeRule.onNodeWithTag("saved-in-progress-game-0").performClick()
        waitForText("Game summary")
        composeRule.onNodeWithText("Archive game").performClick()
        waitForText("No in-progress games.")
        tapTopBarBack()
        composeRule.onNodeWithText("Archived games (1)").performClick()
        waitForText("ProgressA$suffix 0 - 0 ProgressB$suffix")
        composeRule.onNodeWithText("ProgressA$suffix 0 - 0 ProgressB$suffix").performClick()
        waitForText("Game summary")
        tapTopBarBack()
        waitForText("Archived games")
        waitForText("ProgressA$suffix 0 - 0 ProgressB$suffix")

        // A single saved in-progress row can be deleted without removing other archives.
        val deleteProgressTitle = "DeleteProgressA$suffix 0 - 0 DeleteProgressB$suffix"
        seedSavedInProgressGame("DeleteProgressA$suffix", "DeleteProgressB$suffix")
        openArchivedGamesScreen()
        composeRule.onNodeWithText("In-progress games (1)").performClick()
        waitForText(deleteProgressTitle)
        composeRule.onNodeWithTag("delete-saved-in-progress-game-0").performClick()
        dismissDialog(text = "Cancel")
        waitForText(deleteProgressTitle)
        composeRule.onNodeWithTag("delete-saved-in-progress-game-0").performClick()
        confirmDeleteWithSlider()
        waitForText("No in-progress games.")

        // Delete all in the saved in-progress category removes the saved rows shown there.
        val bulkProgressTitle = "BulkProgressA$suffix 0 - 0 BulkProgressB$suffix"
        seedSavedInProgressGame("BulkProgressA$suffix", "BulkProgressB$suffix")
        openArchivedGamesScreen()
        composeRule.onNodeWithText("In-progress games (1)").performClick()
        waitForText(bulkProgressTitle)
        composeRule.onNodeWithTag("delete-all-archived-games").performClick()
        waitForText("all saved games", substring = true)
        confirmDeleteWithSlider("Delete all games?")
        waitForText("No in-progress games.")
        tapTopBarHome()
        waitForText("Start new game")
    }

    /**
     * Test the About screen and its external links.
     */
    @Test
    fun launchAbout() {
        // About should behave like a quiet informational destination that returns cleanly to Home.
        composeRule.onNodeWithTag("home-about").performClick()
        composeRule.onNodeWithTag("about-screen").assertIsDisplayed()
        composeRule.onNodeWithText("Version ${BuildConfig.VERSION_NAME}").assertIsDisplayed()
        val sourceCodeUrl = "https://github.com/rmjarvis/UltiObserver"
        val documentationUrl = "https://rmjarvis.github.io/UltiObserver/"
        val privacyPolicyUrl = "https://github.com/rmjarvis/UltiObserver/blob/main/PRIVACY.md"
        composeRule.onNodeWithText(sourceCodeUrl).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(documentationUrl).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText(privacyPolicyUrl).performScrollTo().assertIsDisplayed()

        // A helper to check a URL link without actually executing the link.
        fun assertOpensUrl(url: String) {
            val instrumentation = InstrumentationRegistry.getInstrumentation()
            var openedIntent: Intent? = null
            val monitor = object : Instrumentation.ActivityMonitor() {
                override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                    if (intent.action == Intent.ACTION_VIEW && intent.dataString == url) {
                        openedIntent = intent
                        return Instrumentation.ActivityResult(Activity.RESULT_OK, null)
                    }
                    return null
                }
            }
            instrumentation.addMonitor(monitor)
            try {
                composeRule.onNodeWithText(url).performScrollTo().performClick()
                composeRule.waitUntil(timeoutMillis = 5_000) { openedIntent != null }
            } finally {
                instrumentation.removeMonitor(monitor)
            }
            assertEquals(Intent.ACTION_VIEW, openedIntent?.action)
            assertEquals(url, openedIntent?.dataString)
        }

        // Check that the source code, documentation, and privacy policy are active links.
        assertOpensUrl(sourceCodeUrl)
        assertOpensUrl(documentationUrl)
        assertOpensUrl(privacyPolicyUrl)

        // Back returns from About to Home.
        dismissDialog(tag = "top-bar-back")
        waitForText("Start new game")

        // The About top-bar Home button also returns directly to Home.
        composeRule.onNodeWithTag("home-about").performClick()
        composeRule.onNodeWithTag("about-screen").assertIsDisplayed()
        tapTopBarHome()
        waitForText("Start new game")
    }

    /**
     * Test the main preference options on the Settings screen.
     */
    @Test
    fun mainSettings() {
        // Seed settings directly so this UI-focused test can start at a meaningful cue state.
        setRuleGuidanceMode(RuleGuidanceMode.FULL)
        setAutomaticallyAdvanceCountdowns(true)
        setAutomaticallyLockLivePoint(true)
        setShowAbbaRatioAsSequence(true)
        setPortraitOrientationPreference()

        // Active games default to Portrait; exercise both alternate orientation behaviors.
        composeRule.onNodeWithText("Settings").performClick()
        composeRule.onNodeWithTag("settings-active-game-orientation-PORTRAIT").assertIsSelected()
        composeRule.onNodeWithTag("settings-active-game-orientation-LANDSCAPE")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("settings-active-game-orientation-LANDSCAPE").assertIsSelected()
        composeRule.onNodeWithTag("settings-active-game-orientation-description").assertTextEquals(
            "Show teams on the left and right of the active game screen."
        )
        composeRule.onNodeWithTag("settings-active-game-orientation-AUTO_ROTATE")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("settings-active-game-orientation-AUTO_ROTATE").assertIsSelected()
        composeRule.onNodeWithTag("settings-active-game-orientation-description").assertTextEquals(
            "Follow the phone's orientation if Android's auto-rotate is enabled. Otherwise, " +
                "it will use the current phone orientation when the active game screen opens " +
                "each time."
        )
        composeRule.onNodeWithTag("settings-active-game-orientation-PORTRAIT")
            .performScrollTo()
            .performClick()

        // Rule guidance defaults to Full. Clicking each shows a description of what it does.
        composeRule.onNodeWithTag("settings-rule-guidance-FULL")
            .performScrollTo()
            .assertIsSelected()
        composeRule.onNodeWithTag("settings-rule-guidance-BRIEF")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("settings-rule-guidance-BRIEF").assertIsSelected()
        composeRule.onNodeWithTag("settings-rule-guidance-TIMED")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("settings-rule-guidance-TIMED").assertIsSelected()
        composeRule.onNodeWithTag("settings-rule-guidance-description").assertTextEquals(
            "Show a brief reminder and automatically accept or close after 5 seconds."
        )
        composeRule.onNodeWithTag("settings-rule-guidance-NONE")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("settings-rule-guidance-NONE").assertIsSelected()
        composeRule.onNodeWithTag("settings-rule-guidance-FULL")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("settings-rule-guidance-FULL").assertIsSelected()

        // Settings should expose automatic live-play options.  The default is to
        // automatically advance to live play when countdowns expire and then lock
        // the screen.  Both aspects of this are settable.
        composeRule.onNodeWithTag("settings-auto-advance-countdowns").performScrollTo()
        waitForText("Automatically start live play?")
        waitForText(
            "When a pull or timeout countdown expires, UltiObserver will automatically start " +
                "or resume live play."
        )
        composeRule.onNodeWithTag("settings-auto-advance-countdowns-value").assertTextEquals("Yes")
        composeRule.onNodeWithTag("settings-auto-lock-live-point-value").assertTextEquals("Yes")
        waitForText("The screen will automatically lock whenever play becomes live.")
        composeRule.onNodeWithTag("settings-auto-advance-countdowns").performClick()
        composeRule.onNodeWithTag("settings-auto-advance-countdowns-value").assertTextEquals("No")
        waitForText(
            "When a countdown expires, UltiObserver will wait for you to tap Start point or " +
                "Continue point."
        )
        composeRule.onNodeWithTag("settings-auto-lock-live-point")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("settings-auto-lock-live-point-value").assertTextEquals("No")
        waitForText(
            "The screen will remain unlocked when play becomes live. You can still lock it " +
                "manually by clicking the lock icon in the central region of the screen."
        )
        composeRule.onNodeWithTag("settings-auto-advance-countdowns")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("settings-auto-lock-live-point").performClick()

        // By default we don't show countdowns for the defensive check on timeouts and
        // misconduct. The next setting can enable those.
        composeRule.onNodeWithTag("settings-show-defense-countdowns").performScrollTo()
        composeRule.onNodeWithTag("settings-show-defense-countdowns-value")
            .assertTextEquals("No")
        waitForText(
            "UltiObserver will not display the defense countdown for timeouts or misconduct " +
                "penalties. You should count the time for the defensive check yourself with arm " +
                "chops."
        )
        composeRule.onNodeWithTag("settings-show-defense-countdowns").performClick()
        composeRule.onNodeWithTag("settings-show-defense-countdowns-value")
            .assertTextEquals("Yes")
        waitForText(
            "After you mark the offense set during a timeout or misconduct penalty, " +
                "UltiObserver will display the 20-second defense countdown."
        )

        // When defense countdowns are enabled, their cue section is shown without the disabled
        // warning.
        composeRule.onNodeWithTag("settings-open-timing-cue-settings")
            .performScrollTo()
            .performClick()
        waitForText("Defense check countdown")
        composeRule.onAllNodesWithText(
            "Note — these cues are not currently enabled.",
            substring = true,
        )
            .assertCountEquals(0)
        dismissDialog(tag = "top-bar-back")
        composeRule.onNodeWithTag("settings-show-defense-countdowns").performScrollTo()
        composeRule.onNodeWithTag("settings-show-defense-countdowns").performClick()
        waitForText(
            "You should count the time for the defensive check yourself with arm chops.",
            substring = true,
        )

        // The default gender ratio badge for ABBA is to show the sequence M2, W1, W2, M1, M2...
        // or vice versa. The next setting can switch this to just the current ratio.
        composeRule.onNodeWithTag("settings-show-abba-ratio-as-sequence-value")
            .assertTextEquals("Yes")
        composeRule.onNodeWithTag("settings-show-abba-ratio-as-sequence")
            .performScrollTo()
            .performClick()
        composeRule.onNodeWithTag("settings-show-abba-ratio-as-sequence-value")
            .assertTextEquals("No")
        waitForText("Ratio will display as either 4W/3M or 4M/3W.")
        composeRule.onNodeWithTag("settings-show-abba-ratio-as-sequence")
            .performScrollTo()
            .performClick()
        waitForText("Ratio will display as W2, M1, M2, W1, W2", substring = true)

        // Gender-ratio badges default to blue and red. Each can independently use any
        // standard palette color, including black for a display without color coding.
        composeRule.onNodeWithTag("settings-4m-3w-badge-color")
            .performScrollTo()
            .assertTextEquals("Blue")
        composeRule.onNodeWithText("Set 4M/3W indicator color").assertIsDisplayed()
        composeRule.onNodeWithTag("settings-4m-3w-badge-color").performClick()
        dismissDialog(text = "Cancel")
        composeRule.onNodeWithTag("settings-4m-3w-badge-color")
            .assertTextEquals("Blue")
            .performClick()
        composeRule.onNodeWithTag("settings-4m-3w-badge-color-BLACK").performClick()
        composeRule.onNodeWithTag("settings-4m-3w-badge-color")
            .assertTextEquals("Black")
        composeRule.onNodeWithTag("settings-4w-3m-badge-color")
            .performScrollTo()
            .assertTextEquals("Red")
        composeRule.onNodeWithText("Set 4W/3M indicator color").assertIsDisplayed()
        composeRule.onNodeWithTag("settings-4w-3m-badge-color").performClick()
        composeRule.onNodeWithTag("settings-4w-3m-badge-color-BLACK").performClick()
        composeRule.onNodeWithTag("settings-4w-3m-badge-color")
            .assertTextEquals("Black")

        // Indicator colors also use the shared custom-color flow. Visible Cancel leaves the
        // current color in place, while platform Back applies the picker like Use this color.
        // Applying a custom color exposes it as a selectable custom swatch.
        composeRule.onNodeWithTag("settings-4m-3w-badge-color").performClick()
        composeRule.onNodeWithTag("settings-4m-3w-badge-color-more").performClick()
        waitForText("Use this color")
        dismissDialog(text = "Cancel")
        val colorAfterDismiss = if (shouldUsePlatformBackDismissalCoverage()) {
            "Custom"
        } else {
            "Black"
        }
        composeRule.onNodeWithTag("settings-4m-3w-badge-color")
            .assertTextEquals(colorAfterDismiss)
            .performClick()
        composeRule.onNodeWithTag("settings-4m-3w-badge-color-more").performClick()
        composeRule.onNodeWithTag("settings-4m-3w-badge-color-custom-picker")
            .performTouchInput {
                click(percentOffset(0.75f, 0.35f))
            }
        composeRule.onNodeWithTag("settings-4m-3w-badge-color-custom-preview").performClick()
        composeRule.onNodeWithText("Use this color").performClick()
        composeRule.onNodeWithTag("settings-4m-3w-badge-color")
            .assertTextEquals("Custom")
            .performClick()
        composeRule.onNodeWithTag("settings-4m-3w-badge-color-custom")
            .assertIsDisplayed()
            .performClick()
        composeRule.onNodeWithTag("settings-4m-3w-badge-color")
            .assertTextEquals("Custom")
    }

    /**
     * Test the timing-cue preferences on the Settings page.
     */
    @Test
    fun cueSettings() {
        // Seed settings directly so this UI-focused test can start at a meaningful cue state.
        val defaultPreferences = TimingAlertPreferences()
        setTimingAlertPreferences(
            defaultPreferences.copy(
                globalMode = TimingAlertGlobalMode.VIBRATION_ONLY,
                soundVolume = 0.5f,
                vibrateWithSounds = true,
                cueModes = defaultPreferences.cueModes + mapOf(
                    TimingCueId.RECEIVING_TWENTY_FOR_HAND to TimingAlertMode.NONE,
                ),
            )
        )
        val hasTimingCueHaptics = deviceHasTimingCueHaptics()

        // With vibration-only mode selected, sound-specific settings should be hidden.
        composeRule.onNodeWithText("Settings").performClick()
        waitForText("Use sounds and vibration for timing cues?")
        waitForText("Vibration only")
        composeRule.onNodeWithText("Vibration only").performClick()
        if (hasTimingCueHaptics) {
            waitForText("Vibration will be used for any cues that are set to use sound.")
            composeRule.onNodeWithTag("settings-vibration-length")
                .performScrollTo()
                .performTouchInput {
                    click(percentOffset(0.95f, 0.5f))
                }
            composeRule.waitUntil(timeoutMillis = 5_000) {
                composeRule.activity.appViewModel.settings.timingAlerts.vibrationDurationMillis >
                    DEFAULT_TIMING_CUE_VIBRATION_MS
            }
            assertTrue(
                composeRule.activity.appViewModel.settings.timingAlerts.vibrationDurationMillis >
                    DEFAULT_TIMING_CUE_VIBRATION_MS
            )
            composeRule.onNodeWithTag("settings-test-vibration")
                .performScrollTo()
                .assertIsEnabled()
                .performClick()
        } else {
            waitForText("This phone reports that vibration is unavailable.", substring = true)
            composeRule.onNodeWithTag("settings-vibration-length")
                .performScrollTo()
                .assertIsNotEnabled()
            composeRule.onNodeWithTag("settings-test-vibration")
                .performScrollTo()
                .assertIsNotEnabled()
        }
        composeRule.onAllNodesWithTag("settings-sound-volume").assertCountEquals(0)
        composeRule.onAllNodesWithTag("settings-vibrate-with-sounds").assertCountEquals(0)
        composeRule.onNodeWithTag("settings-open-timing-cue-settings")
            .performScrollTo()
            .performClick()
        waitForText("Cue sound settings")
        composeRule.onNodeWithTag("settings-DEFENSE_TWENTY-VIBRATE").performScrollTo()
        waitForText("Note — defensive check countdowns are not currently enabled.", substring = true)

        // Cue settings should show disabled-sound context, support default reset, and
        // persist per-cue edits.
        waitForText("Reset all to defaults")
        if (hasTimingCueHaptics) {
            waitForText(
                "The phone will currently vibrate instead for any cues with sounds.",
                substring = true,
            )
        } else {
            waitForText(
                "Note — sounds are currently not enabled. If you want sounds",
                substring = true,
            )
        }
        composeRule.onNodeWithTag("settings-RECEIVING_TWENTY_FOR_HAND-NONE").assertIsSelected()
        composeRule.onNodeWithTag("settings-reset-timing-cue-defaults").performScrollTo().performClick()
        composeRule.onNodeWithTag("settings-RECEIVING_TWENTY_FOR_HAND-TICK").assertIsSelected()
        composeRule.onNodeWithTag("settings-RECEIVING_TWENTY_FOR_HAND-REPEAT_2").assertIsSelected()
        composeRule.onNodeWithTag("settings-OFFENSE_TWENTY-NONE").performScrollTo().performClick()

        // Back returns from cue settings to the main Settings screen.
        dismissDialog(tag = "top-bar-back")
        waitForText("Use sounds and vibration for timing cues?")
        composeRule.onNodeWithTag("settings-global-alert-SOUNDS_ON")
            .performScrollTo()
            .performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.activity.appViewModel.settings.timingAlerts.globalMode ==
                TimingAlertGlobalMode.SOUNDS_ON
        }
        composeRule.onNodeWithTag("settings-sound-volume").performScrollTo()
        waitForText("Ear buds are recommended when using sounds with UltiObserver.")
        if (!hasTimingCueHaptics) {
            waitForText("This phone reports that vibration is unavailable.", substring = true)
            composeRule.onNodeWithTag("settings-vibration-length")
                .performScrollTo()
                .assertIsNotEnabled()
            composeRule.onNodeWithTag("settings-test-vibration")
                .performScrollTo()
                .assertIsNotEnabled()
        }
        waitForText("Sound volume 50%")
        waitForText("Also vibrate on cues that use sound?")

        // The sound-volume slider persists the selected cue volume.
        composeRule.onNodeWithTag("settings-sound-volume")
            .performScrollTo()
            .performTouchInput {
                click(percentOffset(x = 0.75f, y = 0.5f))
            }
        assertTrue(
            composeRule.activity.appViewModel.settings.timingAlerts.soundVolume > 0.5f
        )

        // Re-enabled sound settings should expose vibration, preview, and repeat-count controls.
        composeRule.onNodeWithTag("settings-vibrate-with-sounds").performScrollTo()
        composeRule.onNodeWithTag("settings-vibrate-with-sounds-value").assertTextEquals("Yes")
        composeRule.onNodeWithTag("settings-sound-volume").assertIsEnabled()
        if (hasTimingCueHaptics) {
            composeRule.onNodeWithTag("settings-vibrate-with-sounds").assertIsEnabled()
            composeRule.onNodeWithTag("settings-vibrate-with-sounds").performClick()
            composeRule.onNodeWithTag("settings-vibrate-with-sounds-value").assertTextEquals("No")
        } else {
            composeRule.onNodeWithTag("settings-vibrate-with-sounds").assertIsNotEnabled()
        }
        composeRule.onNodeWithTag("settings-global-alert-OFF").performScrollTo().performClick()
        composeRule.waitUntil(timeoutMillis = 5_000) {
            composeRule.activity.appViewModel.settings.timingAlerts.globalMode ==
                TimingAlertGlobalMode.OFF
        }
        waitForText("No sound or vibration will be used for any timing cues.")
        composeRule.onAllNodesWithTag("settings-sound-volume").assertCountEquals(0)
        composeRule.onAllNodesWithTag("settings-vibrate-with-sounds").assertCountEquals(0)
        composeRule.onNodeWithTag("settings-open-timing-cue-settings")
            .performScrollTo()
            .performClick()
        waitForText("Cue sound settings")
        waitForText(
            "Note — sounds are currently not enabled. If you want sounds",
            substring = true,
        )
        dismissDialog(tag = "top-bar-back")
        waitForText("Use sounds and vibration for timing cues?")
        composeRule.onNodeWithTag("settings-global-alert-SOUNDS_ON").performScrollTo().performClick()
        waitForText("Sound/vibration settings for individual cues")
        composeRule.onNodeWithTag("settings-open-timing-cue-settings")
            .performScrollTo()
            .performClick()
        waitForText("Cue sound settings")
        waitForText("Sound previews")
        composeRule.onAllNodesWithText("Note — sounds are currently not enabled.")
            .assertCountEquals(0)
        waitForText("Knock")
        composeRule.onNodeWithTag("settings-sound-preview-TICK").performClick()
        waitForText("x2")
        waitForText("x3")
        composeRule.onNodeWithTag("settings-HALF_CAP-REPEAT_3").performScrollTo().performClick()
        composeRule.onNodeWithTag("settings-HALF_CAP-REPEAT_3").assertIsSelected()
        composeRule.onNodeWithTag("settings-HALF_CAP-REPEAT_3").performClick()
        composeRule.onNodeWithTag("settings-HALF_CAP-REPEAT_3").assertIsNotSelected()
        waitForText("Before pull - offense")
        waitForText("Timeout between points")
        waitForText("Caps")
        waitForText("Half cap")
        waitForText("Soft cap")
        waitForText("Hard cap")
        dismissDialog(tag = "top-bar-back")
        waitForText("Use sounds and vibration for timing cues?")

        // Back returns from Settings to Home.
        dismissDialog(tag = "top-bar-back")
        waitForText("Start new game")

        // Save and return returns from Settings to Home.
        composeRule.onNodeWithText("Settings").performClick()
        waitForText("Use sounds and vibration for timing cues?")
        composeRule.onNodeWithText("Save and return").performClick()
        waitForText("Start new game")

        // Timing cue settings can return directly to Home from their top bar.
        composeRule.onNodeWithText("Settings").performClick()
        waitForText("Use sounds and vibration for timing cues?")
        composeRule.onNodeWithTag("settings-open-timing-cue-settings")
            .performScrollTo()
            .performClick()
        waitForText("Cue sound settings")
        tapTopBarHome()
        waitForText("Start new game")

        // Timing cue settings can return directly to Home from their bottom action.
        composeRule.onNodeWithText("Settings").performClick()
        waitForText("Use sounds and vibration for timing cues?")
        composeRule.onNodeWithTag("settings-open-timing-cue-settings")
            .performScrollTo()
            .performClick()
        waitForText("Cue sound settings")
        composeRule.onNodeWithText("Save and return home").performClick()
        waitForText("Start new game")

        // The main Settings screen can also still return directly to Home from its top bar.
        composeRule.onNodeWithText("Settings").performClick()
        waitForText("Use sounds and vibration for timing cues?")
        tapTopBarHome()
        waitForText("Start new game")
    }

    /**
     * Test the Profile screen and persisted observer identity.
     */
    @Test
    fun launchProfile() {
        // Profile should save both the observer name and selected avatar across navigation.
        composeRule.onNodeWithText("Profile").performClick()
        waitForText("Name")
        waitForText("Used as the default first observer when starting a new game.")
        waitForText("Home avatar")
        waitForText("Use a random avatar")
        waitForText("Or choose a specific avatar:")
        composeRule.onNodeWithTag("profile-name-field").performTextReplacement("Casey Observer")
        composeRule.onNodeWithText("Casey Observer").assertIsDisplayed()
        composeRule.onNodeWithTag("profile-avatar-RANDOM").assertIsSelected()
        composeRule.onNodeWithTag("profile-avatar-BLUE").performScrollTo().performClick()
        composeRule.onNodeWithTag("profile-avatar-BLUE").assertIsSelected()
        composeRule.onNodeWithTag("profile-avatar-RANDOM").assertIsNotSelected()
        tapTopBarBack()
        waitForText("Start new game")
        composeRule.onNodeWithText("Profile").performClick()
        composeRule.onNodeWithTag("profile-avatar-BLUE").performScrollTo()
        composeRule.onNodeWithTag("profile-avatar-BLUE").assertIsSelected()
        composeRule.onNode(hasContentDescription("Man with blue ponytail and glasses"))
            .assertIsDisplayed()
        composeRule.onNodeWithTag("profile-avatar-RANDOM").performScrollTo().performClick()
        composeRule.onNodeWithTag("profile-avatar-RANDOM").assertIsSelected()
        composeRule.onNodeWithTag("profile-avatar-BLUE").assertIsNotSelected()

        // Save and return returns directly to Home.
        composeRule.onNodeWithText("Save and return").performClick()
        waitForText("Start new game")

        // Profile can still return directly to Home from its top bar.
        composeRule.onNodeWithText("Profile").performClick()
        waitForText("Name")
        tapTopBarHome()
        waitForText("Start new game")

        // A new game's first observer defaults to the saved Profile name.
        openNewGameSetup()
        openGameInformationSetupEditor()
        composeRule.onNodeWithTag("setup-observer-0")
            .performScrollTo()
            .assertTextContains("Casey Observer")
        composeRule.onNodeWithTag("setup-observer-1")
            .performScrollTo()
            .assertTextContains("Observer 2")
    }

    /// Open Archived games from Home and wait until the page is visible.
    private fun openArchivedGamesScreen() {
        composeRule.onNodeWithText("See archived/saved games").performClick()
        waitForText("Archived/saved games")
    }

    /// Open the completed archived games category from Home.
    private fun openArchivedCompleteGamesScreen() {
        openArchivedGamesScreen()
        composeRule.onNodeWithText("Archived games", substring = true).performClick()
        waitForText("Archived games")
    }

    /**
     * Select one archive sort mode through the sort dialog.
     *
     * @param sortMode The mode to select.
     */
    private fun selectArchiveSort(sortMode: ArchiveSortMode) {
        composeRule.onNodeWithTag("archive-sort-button").performClick()
        composeRule.onNodeWithTag("archive-sort-${sortMode.name}").performClick()
    }

    /**
     * Assert the date filter dialog displays one inclusive range.
     *
     * @param start The expected start date.
     * @param end The expected end date.
     */
    private fun assertArchiveDateRange(start: LocalDate, end: LocalDate) {
        waitForText("Start: ${formatStartDate(start)}")
        waitForText("End: ${formatStartDate(end)}")
    }

    /**
     * Assert the visible archive row order.
     *
     * @param titles The expected row titles, from top to bottom.
     */
    private fun assertArchiveRowsInOrder(vararg titles: String) {
        titles.forEachIndexed { index, title ->
            composeRule.onNodeWithTag("archived-game-$index")
                .assertTextContains(title, substring = true)
        }
    }

    /// Open the saved in-progress games category from Home.
    private fun openSavedInProgressGamesScreen() {
        openArchivedGamesScreen()
        composeRule.onNodeWithText("In-progress games", substring = true).performClick()
        waitForText("In-progress games")
    }

    /**
     * Seed one archive with fields used by archive filter/sort UI tests.
     *
     * @param tournament Tournament name for the archive.
     * @param division Division for the archive.
     * @param teamOne Team 1 name.
     * @param teamTwo Team 2 name.
     * @param startDate Start date for sorting and filtering.
     */
    private fun seedArchiveForFilterTest(
        tournament: String,
        division: GameDivision,
        teamOne: String,
        teamTwo: String,
        startDate: LocalDate,
    ) {
        composeRule.activityRule.scenario.onActivity { activity ->
            val setup = newSetupGameState(now = 123_000L).copy(
                tournamentName = tournament,
                division = division,
                level = "Club",
                observerNames = listOf("Mike"),
                startDate = startDate,
                startTime = LocalTime.of(9, 0),
                teamOne = TeamState(name = teamOne, color = TeamColorChoice.WHITE),
                teamTwo = TeamState(name = teamTwo, color = TeamColorChoice.BLUE),
            )
            activity.appViewModel.updateCurrentGame(
                setup.startGameInTestOrientation(activity).copy(
                    phase = GamePhase.GAME_OVER,
                    endEpoch = System.currentTimeMillis(),
                    countdown = null,
                )
            )
            activity.appViewModel.archiveCompletedGame()
        }
        composeRule.waitForIdle()
    }

    /**
     * Seed one saved in-progress game by starting it, then starting a replacement draft.
     *
     * This lets archive-page tests exercise saved in-progress actions without scoring through a
     * live game in the UI.
     *
     * @param teamOne Team 1 name for the saved game.
     * @param teamTwo Team 2 name for the saved game.
     */
    private fun seedSavedInProgressGame(teamOne: String, teamTwo: String) {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.appViewModel.deleteCurrentGame()
            activity.appViewModel.seedCurrentInProgressGame(teamOne, teamTwo)
            activity.appViewModel.startNewGame(now = 123_000L)
            activity.appViewModel.goHome()
        }
        composeRule.waitForIdle()
    }

    /**
     * Seed one current in-progress game without creating a saved in-progress archive.
     *
     * @param teamOne Team 1 name for the current game.
     * @param teamTwo Team 2 name for the current game.
     */
    private fun seedCurrentInProgressGame(teamOne: String, teamTwo: String) {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.appViewModel.deleteCurrentGame()
            activity.appViewModel.seedCurrentInProgressGame(teamOne, teamTwo)
            activity.appViewModel.goHome()
        }
        composeRule.waitForIdle()
    }

    /// Install an active live-point game without making the active-game screen visible.
    private fun AppViewModel.seedCurrentInProgressGame(teamOne: String, teamTwo: String) {
        updateCurrentGame(
            newSetupGameState(now = 123_000L).copy(
                teamOne = TeamState(teamOne, TeamColorChoice.WHITE),
                teamTwo = TeamState(teamTwo, TeamColorChoice.BLUE),
            ).startGame(settings.orientationPreference).beginLivePoint(0L)
        )
    }

    /**
     * Start a new setup draft, saving the previous current setup draft if one exists.
     *
     * @param teamOne Team 1 name for the new current setup draft.
     * @param teamTwo Team 2 name for the new current setup draft.
     */
    private fun seedCurrentSetupAndSavePrevious(teamOne: String, teamTwo: String) {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.appViewModel.startNewGame(now = 123_000L)
            activity.appViewModel.updateSetup(
                newSetupGameState(now = 123_000L).copy(
                    teamOne = TeamState(teamOne, TeamColorChoice.WHITE),
                    teamTwo = TeamState(teamTwo, TeamColorChoice.BLUE),
                )
            )
            activity.appViewModel.goHome()
        }
        composeRule.waitForIdle()
    }
}
