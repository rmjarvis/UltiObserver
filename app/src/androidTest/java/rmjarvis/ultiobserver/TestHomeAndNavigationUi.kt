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
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.percentOffset
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.performTextReplacement
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import java.time.LocalDate
import java.time.LocalTime
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

        // Before the first pull, Back should return to setup for quick field-layout corrections.
        startGameFromSetup()
        assertLiveScreen()
        composeRule.onNodeWithTag("top-bar-home").performClick()
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

        // More actions should reopen setup in update mode and return to live.
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
            activity.appViewModel.startNewGame(now = 123_000L)
            activity.appViewModel.updateSetup(
                newSetupGameState(now = 123_000L).copy(
                    teamOne = TeamState(name = teamOneName, color = TeamColorChoice.WHITE),
                    teamTwo = TeamState(name = teamTwoName, color = TeamColorChoice.BLUE),
                )
            )
            activity.appViewModel.finishSetup(now = 123_000L)
            activity.appViewModel.updateLiveGame(
                activity.appViewModel.liveState!!.beginLivePoint(0L)
            )
            activity.appViewModel.startNewGame(now = 123_000L)
            activity.appViewModel.updateSetup(
                newSetupGameState(now = 123_000L).copy(
                    teamOne = TeamState(name = currentTeamOneName, color = TeamColorChoice.WHITE),
                    teamTwo = TeamState(name = currentTeamTwoName, color = TeamColorChoice.BLUE),
                )
            )
            activity.appViewModel.finishSetup(now = 123_000L)
            activity.appViewModel.updateLiveGame(
                activity.appViewModel.liveState!!.beginLivePoint(0L)
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
        composeRule.onNodeWithText("Saved setup states (0)").assertIsEnabled()
        composeRule.onNodeWithText("Archived games (0)").performClick()
        waitForText("No completed games yet.")
        tapTopBarBack()
        waitForText("Archived/saved games")
        tapTopBarBack()

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
        waitForText("Saved setup states (0)")
        tapTopBarBack()

        // Inside one category, delete all removes only the visible category's rows.
        seedArchivedGameProgrammatically(firstTeamOne, firstTeamTwo)
        seedArchivedGameProgrammatically(secondTeamOne, secondTeamTwo)
        openArchivedCompleteGamesScreen()
        waitForText(firstArchivedTitle)
        waitForText(secondArchivedTitle)

        // Opening an archived row should expose its summary and persisted event log.
        composeRule.onNodeWithText(firstArchivedTitle).performClick()
        waitForText("Game summary")
        composeRule.onNodeWithText("Share").assertIsDisplayed()
        composeRule.onNodeWithText("Event log").performClick()
        waitForText("Event log")
        waitForText("No events logged yet.")
        dismissDialog(text = "OK")
        tapTopBarBack()
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
        waitForText("No completed games yet.")
        assertTrue(
            composeRule.onAllNodesWithText(firstArchivedTitle).fetchSemanticsNodes().isEmpty()
        )
        assertTrue(
            composeRule.onAllNodesWithText(secondArchivedTitle).fetchSemanticsNodes().isEmpty()
        )
        tapTopBarBack()
        waitForText("Archived/saved games")

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
        waitForText("No completed games yet.")
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
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.appViewModel.deleteCurrentGame()
        }
        composeRule.waitForIdle()
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

        // A custom start date can be committed without an end date.
        composeRule.onNodeWithTag("archive-clear-filter-DATE").performClick()
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
        composeRule.onNodeWithTag("archive-filter-value-$summerTournament").performClick()
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
        selectArchiveSort(ArchiveSortMode.TEAM_ONE_AZ)
        assertArchiveRowsInOrder(summerOpenTitle, summerMixedTitle, fallOpenTitle)
        selectArchiveSort(ArchiveSortMode.TEAM_ONE_ZA)
        assertArchiveRowsInOrder(fallOpenTitle, summerMixedTitle, summerOpenTitle)
        selectArchiveSort(ArchiveSortMode.TEAM_TWO_AZ)
        assertArchiveRowsInOrder(summerMixedTitle, fallOpenTitle, summerOpenTitle)
        selectArchiveSort(ArchiveSortMode.TEAM_TWO_ZA)
        assertArchiveRowsInOrder(summerOpenTitle, fallOpenTitle, summerMixedTitle)

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

        // Other saved categories do not show archive filter controls.
        tapTopBarBack()
        composeRule.onNodeWithText("In-progress games", substring = true).performClick()
        waitForText("In-progress games")
        composeRule.onAllNodesWithTag("archive-filter-button").assertCountEquals(0)
    }

    /**
     * Test saving a setup state for later and directly archiving a saved in-progress game.
     */
    @Test
    fun savedGames() {
        clearArchivedGamesProgrammatically()
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.appViewModel.deleteCurrentGame()
        }
        composeRule.waitForIdle()

        // Start setting up a game, then save it for later.
        val suffix = System.currentTimeMillis().toString().takeLast(6)
        val setupTeamOne = "SetupA$suffix"
        val setupTeamTwo = "SetupB$suffix"
        openNewGameSetup()
        replaceSetupTeamName("Team 1", setupTeamOne)
        replaceSetupTeamName("Team 2", setupTeamTwo)
        composeRule.onNodeWithText("Save game for later").performClick()
        waitForText("Start new game")

        // Start a different current setup draft, then verify the setup archive page separates
        // that current draft from the saved setup states.
        val currentSetupTeamOne = "CurrentSetupA$suffix"
        val currentSetupTeamTwo = "CurrentSetupB$suffix"
        openNewGameSetup()
        replaceSetupTeamName("Team 1", currentSetupTeamOne)
        replaceSetupTeamName("Team 2", currentSetupTeamTwo)
        tapTopBarBack()

        openArchivedGamesScreen()
        composeRule.onNodeWithText("Saved setup states (2)").assertIsEnabled()
        composeRule.onNodeWithText("Saved setup states (2)").performClick()
        waitForText("Current setup")
        waitForText("Saved setup states")
        val currentSetupTitle = "$currentSetupTeamOne vs $currentSetupTeamTwo"
        val savedSetupTitle = "$setupTeamOne vs $setupTeamTwo"
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

        // The saved setup row restores that saved state and saves the previous current setup
        // draft into the saved setup states.
        openArchivedGamesScreen()
        composeRule.onNodeWithText("Saved setup states (2)").performClick()
        composeRule.onNodeWithTag("saved-setup-state-0").performClick()
        waitForText("Start game")
        composeRule.onNodeWithText(setupTeamOne).assertIsDisplayed()
        composeRule.onNodeWithText(setupTeamTwo).assertIsDisplayed()
        tapTopBarBack()
        openArchivedGamesScreen()
        composeRule.onNodeWithText("Saved setup states (2)").performClick()
        composeRule.onNodeWithTag("current-setup-state")
            .assertTextContains(savedSetupTitle, substring = true)
        composeRule.onNodeWithTag("saved-setup-state-0")
            .assertTextContains(currentSetupTitle, substring = true)

        // Start an in-progress game, but then start another new game.  This moves what was
        // the current game into the saved games section of the in-progress archive.
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.appViewModel.deleteCurrentGame()
            activity.appViewModel.startNewGame(now = 123_000L)
            activity.appViewModel.updateSetup(
                newSetupGameState(now = 123_000L).copy(
                    teamOne = TeamState("ProgressA$suffix", TeamColorChoice.WHITE),
                    teamTwo = TeamState("ProgressB$suffix", TeamColorChoice.BLUE),
                )
            )
            activity.appViewModel.finishSetup(now = 123_000L)
            activity.appViewModel.updateLiveGame(activity.appViewModel.liveState!!.beginLivePoint(0L))
            activity.appViewModel.startNewGame(now = 123_000L)
            activity.appViewModel.goHome()
        }
        composeRule.waitForIdle()

        // Got to the archive screen, find that saved game.  You can either reopen it or archive
        // it. Here we archive it.
        openArchivedGamesScreen()
        composeRule.onNodeWithText("In-progress games (1)").performClick()
        waitForText("ProgressA$suffix 0 - 0 ProgressB$suffix")
        composeRule.onNodeWithText("ProgressA$suffix 0 - 0 ProgressB$suffix").performClick()
        waitForText("Game summary")
        composeRule.onNodeWithText("Make current").assertIsDisplayed()
        composeRule.onNodeWithText("Archive game").performClick()
        waitForText("No in-progress games.")
        tapTopBarBack()
        composeRule.onNodeWithText("Archived games (1)").performClick()
        waitForText("ProgressA$suffix 0 - 0 ProgressB$suffix")
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
        val privacyPolicyUrl = "https://github.com/rmjarvis/UltiObserver/blob/main/PRIVACY.md"
        composeRule.onNodeWithText(sourceCodeUrl).performScrollTo().assertIsDisplayed()
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

        // Check that the source code URL and the privacy policy are active links.
        assertOpensUrl(sourceCodeUrl)
        assertOpensUrl(privacyPolicyUrl)

        // Back returns from About to Home.
        dismissDialog(tag = "top-bar-back")
        waitForText("Start new game")
    }

    /**
     * Test the Settings screen and persisted timing-cue preferences.
     */
    @Test
    fun launchSettings() {
        // Seed settings directly so this UI-focused test can start at a meaningful cue state.
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.appViewModel.updateTimingAlertGlobalMode(TimingAlertGlobalMode.VIBRATION_ONLY)
            activity.appViewModel.updateTimingAlertSoundVolume(0.5f)
            activity.appViewModel.updateTimingAlertVibrateWithSounds(true)
            activity.appViewModel.updateTimingCueMode(
                TimingCueId.RECEIVING_TWENTY_FOR_HAND,
                TimingAlertMode.NONE,
            )
        }

        // Settings should expose automatic live-play options.
        composeRule.onNodeWithText("Settings").performClick()
        val hasTimingCueHaptics = deviceHasTimingCueHaptics()
        waitForText("Automatically start live play when a countdown expires?")
        composeRule.onNodeWithTag("settings-auto-advance-countdowns-value").assertTextEquals("Yes")
        composeRule.onNodeWithTag("settings-auto-lock-live-point-value").assertTextEquals("Yes")
        composeRule.onNodeWithTag("settings-show-defense-countdowns-value")
            .assertTextEquals("No")
        composeRule.onNodeWithTag("settings-auto-advance-countdowns").performClick()
        composeRule.onNodeWithTag("settings-auto-lock-live-point").performClick()
        composeRule.onNodeWithTag("settings-show-defense-countdowns").performClick()
        composeRule.onNodeWithTag("settings-auto-advance-countdowns-value").assertTextEquals("No")
        composeRule.onNodeWithTag("settings-auto-lock-live-point-value").assertTextEquals("No")
        composeRule.onNodeWithTag("settings-show-defense-countdowns-value")
            .assertTextEquals("Yes")

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
        waitForText("Use sounds and vibration for timing cues?")

        composeRule.onNodeWithTag("settings-auto-advance-countdowns").performClick()
        composeRule.onNodeWithTag("settings-auto-lock-live-point").performClick()
        composeRule.onNodeWithTag("settings-show-defense-countdowns").performClick()
        waitForText(
            "most observers will count this off themselves with arm chops",
            substring = true,
        )

        // With vibration-only mode selected, sound-specific settings should be hidden.
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
                composeRule.activity.appViewModel.timingAlertPreferences.vibrationDurationMillis >
                    DEFAULT_TIMING_CUE_VIBRATION_MS
            }
            assertTrue(
                composeRule.activity.appViewModel.timingAlertPreferences.vibrationDurationMillis >
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
        waitForText("Note — these cues are not currently enabled.", substring = true)

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
        composeRule.onNodeWithTag("settings-reset-timing-cue-defaults").performClick()
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
            composeRule.activity.appViewModel.timingAlertPreferences.globalMode ==
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
            composeRule.activity.appViewModel.timingAlertPreferences.soundVolume > 0.5f
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
            composeRule.activity.appViewModel.timingAlertPreferences.globalMode ==
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
        pressAppBack()
        waitForText("Use sounds and vibration for timing cues?")
        composeRule.onNodeWithTag("settings-global-alert-SOUNDS_ON").performClick()
        waitForText("Sound settings for individual cues")
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
        pressAppBack()
        waitForText("Use sounds and vibration for timing cues?")

        // Back returns from Settings to Home.
        dismissDialog(tag = "top-bar-back")
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
        tapTopBarBack()
        waitForText("Start new game")
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
            activity.appViewModel.updateLiveGame(
                setup.startGame().copy(
                    phase = GamePhase.GAME_OVER,
                    endEpoch = System.currentTimeMillis(),
                    countdown = null,
                )
            )
            activity.appViewModel.archiveCompletedGame()
        }
        composeRule.waitForIdle()
    }
}
