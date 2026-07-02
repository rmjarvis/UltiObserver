package rmjarvis.ultiobserver

import java.time.LocalDate
import java.time.LocalTime
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/**
 * Tests for archived-game lifecycle behavior owned by AppViewModel.
 */
class TestArchive : GameDomainTestFixtures() {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    /**
     * Verify archive filtering exposes cascading values and keeps missing values
     * selectable as N/A.
     */
    @Test
    fun archiveFilterSelections() {
        // Build archives with overlapping tournament, division, team, and observer data.
        val ring = archiveForFilterTest(
            tournament = "Pro Elite Challenge",
            division = GameDivision.OPEN,
            level = "Club",
            teamOne = "Ring of Fire",
            teamTwo = "Truck Stop",
            observers = "Mike",
            startDate = LocalDate.of(2026, 5, 1),
            startTime = LocalTime.of(9, 0),
        )
        val dragN = archiveForFilterTest(
            tournament = "Pro Elite Challenge",
            division = GameDivision.MIXED,
            level = "Club",
            teamOne = "Drag'n Thrust",
            teamTwo = "Mixtape",
            observers = "Gary",
            startDate = LocalDate.of(2026, 5, 2),
            startTime = LocalTime.of(11, 0),
        )
        val machine = archiveForFilterTest(
            tournament = "College Nationals",
            division = GameDivision.OPEN,
            level = "College",
            teamOne = "Michigan",
            teamTwo = "Pitt",
            observers = "",
            startDate = LocalDate.of(2026, 6, 1),
            startTime = LocalTime.of(10, 0),
        )
        val unknownTournament = archiveForFilterTest(
            tournament = "",
            division = null,
            level = "",
            teamOne = "Animal",
            teamTwo = "Shame",
            observers = "",
            startDate = LocalDate.of(2025, 12, 31),
            startTime = LocalTime.of(13, 0),
        )

        // Combining tournament and division filters keeps only rows matching both filters.
        val summerOpen = getFilteredArchiveState(
            archivedGames = listOf(ring, dragN, machine, unknownTournament),
            selectedCategory = ArchivedGameCategory.COMPLETED,
            filterSelections = ArchiveFilterSelections(
                tournaments = setOf("Pro Elite Challenge"),
                divisions = setOf("Open"),
            ),
            sortMode = ArchiveSortMode.DATE_NEWEST,
        )
        assertEquals(listOf("Ring of Fire 0 - 0 Truck Stop"), summerOpen.summaryLines())
        assertEquals(
            "Pro Elite Challenge",
            summerOpen.selectedGames!!.single().entry.headerDetail,
        )
        assertEquals(
            listOf(
                "Filters:",
                "    Tournament: Pro Elite Challenge",
                "    Division: Open",
                "Sorted by date, newest first",
            ).joinToString("\n"),
            summerOpen.filterAndSortSummaryText,
        )

        // Available values include counts of games matching that value under other filters.
        val unfiltered = getFilteredArchiveState(
            archivedGames = listOf(ring, dragN, machine, unknownTournament),
            selectedCategory = ArchivedGameCategory.COMPLETED,
            filterSelections = ArchiveFilterSelections(),
            sortMode = ArchiveSortMode.DATE_NEWEST,
        )
        assertEquals(
            listOf(
                "College Nationals" to 1,
                "Pro Elite Challenge" to 2,
                ARCHIVE_FILTER_NA to 1,
            ),
            unfiltered.valueCounts(ArchiveFilterField.TOURNAMENT),
        )

        // Available values for one filter reflect all other filters, but not that same filter.
        assertEquals(
            listOf("College Nationals" to 1, "Pro Elite Challenge" to 1),
            summerOpen.valueCounts(ArchiveFilterField.TOURNAMENT),
        )
        assertEquals(
            listOf("Ring of Fire" to 1, "Truck Stop" to 1),
            summerOpen.valueCounts(ArchiveFilterField.TEAM),
        )
        assertEquals(
            listOf("Mike" to 1),
            summerOpen.valueCounts(ArchiveFilterField.OBSERVERS),
        )

        // Missing values are displayed as N/A and sorted after concrete values.
        val openDivision = getFilteredArchiveState(
            archivedGames = listOf(ring, dragN, machine, unknownTournament),
            selectedCategory = ArchivedGameCategory.COMPLETED,
            filterSelections = ArchiveFilterSelections(divisions = setOf("Open")),
            sortMode = ArchiveSortMode.DATE_NEWEST,
        )
        assertEquals(
            listOf("Mike" to 1, ARCHIVE_FILTER_NA to 1),
            openDivision.valueCounts(ArchiveFilterField.OBSERVERS),
        )

        // A date filter must include at least one bound.
        assertThrows(IllegalArgumentException::class.java) {
            ArchiveDateFilter(start = null, end = null)
        }

        // Team filters match either side, N/A values are selectable, and custom dates are inclusive.
        val animalCustomRange = getFilteredArchiveState(
            archivedGames = listOf(ring, dragN, machine, unknownTournament),
            selectedCategory = ArchivedGameCategory.COMPLETED,
            filterSelections = ArchiveFilterSelections(
                teams = setOf("Animal"),
                observers = setOf(ARCHIVE_FILTER_NA),
                dateRange = ArchiveDateFilter(
                    start = LocalDate.of(2025, 12, 1),
                    end = LocalDate.of(2025, 12, 31),
                ),
            ),
            sortMode = ArchiveSortMode.DATE_OLDEST,
        )
        assertEquals(listOf("Animal 0 - 0 Shame"), animalCustomRange.summaryLines())

        // Reversed custom dates are normalized to the same inclusive range.
        val reversedAnimalCustomRange = getFilteredArchiveState(
            archivedGames = listOf(ring, dragN, machine, unknownTournament),
            selectedCategory = ArchivedGameCategory.COMPLETED,
            filterSelections = ArchiveFilterSelections(
                teams = setOf("Animal"),
                observers = setOf(ARCHIVE_FILTER_NA),
                dateRange = ArchiveDateFilter(
                    start = LocalDate.of(2025, 12, 31),
                    end = LocalDate.of(2025, 12, 1),
                ),
            ),
            sortMode = ArchiveSortMode.DATE_OLDEST,
        )
        assertEquals(listOf("Animal 0 - 0 Shame"), reversedAnimalCustomRange.summaryLines())

        // Open-ended custom dates keep everything before an end or everything after a start.
        val throughMayTwo = getFilteredArchiveState(
            archivedGames = listOf(ring, dragN, machine, unknownTournament),
            selectedCategory = ArchivedGameCategory.COMPLETED,
            filterSelections = ArchiveFilterSelections(
                dateRange = ArchiveDateFilter(
                    start = null,
                    end = LocalDate.of(2026, 5, 2),
                ),
            ),
            sortMode = ArchiveSortMode.DATE_OLDEST,
        )
        assertEquals(
            listOf(
                "Animal 0 - 0 Shame",
                "Ring of Fire 0 - 0 Truck Stop",
                "Drag'n Thrust 0 - 0 Mixtape",
            ),
            throughMayTwo.summaryLines(),
        )
        val fromMayTwo = getFilteredArchiveState(
            archivedGames = listOf(ring, dragN, machine, unknownTournament),
            selectedCategory = ArchivedGameCategory.COMPLETED,
            filterSelections = ArchiveFilterSelections(
                dateRange = ArchiveDateFilter(
                    start = LocalDate.of(2026, 5, 2),
                    end = null,
                ),
            ),
            sortMode = ArchiveSortMode.DATE_OLDEST,
        )
        assertEquals(
            listOf("Drag'n Thrust 0 - 0 Mixtape", "Michigan 0 - 0 Pitt"),
            fromMayTwo.summaryLines(),
        )

        // Concrete date filters can express the same range as a date preset button.
        val lastSevenDays = getFilteredArchiveState(
            archivedGames = listOf(ring, dragN, machine, unknownTournament),
            selectedCategory = ArchivedGameCategory.COMPLETED,
            filterSelections = ArchiveFilterSelections(
                dateRange = ArchiveDateFilter(
                    start = LocalDate.of(2026, 5, 26),
                    end = LocalDate.of(2026, 6, 2),
                ),
            ),
            sortMode = ArchiveSortMode.DATE_NEWEST,
        )
        assertEquals(listOf("Michigan 0 - 0 Pitt"), lastSevenDays.summaryLines())
    }

    /**
     * Verify archive sorting supports date and team orders without duplicating games.
     */
    @Test
    fun archiveSorts() {
        // Build archives in storage order that differs from every requested sort.
        val zeta = archiveForFilterTest(
            teamOne = "Zeta",
            teamTwo = "Beta",
            startDate = LocalDate.of(2026, 5, 1),
            startTime = LocalTime.of(9, 0),
        )
        val alpha = archiveForFilterTest(
            teamOne = "Alpha",
            teamTwo = "Omega",
            startDate = LocalDate.of(2026, 5, 3),
            startTime = LocalTime.of(9, 0),
        )
        val middle = archiveForFilterTest(
            teamOne = "Middle",
            teamTwo = "Delta",
            startDate = LocalDate.of(2026, 5, 2),
            startTime = LocalTime.of(9, 0),
        )
        val archives = listOf(zeta, alpha, middle)

        assertEquals(
            listOf("Alpha 0 - 0 Omega", "Middle 0 - 0 Delta", "Zeta 0 - 0 Beta"),
            getFilteredArchiveState(
                archivedGames = archives,
                selectedCategory = ArchivedGameCategory.COMPLETED,
                filterSelections = ArchiveFilterSelections(),
                sortMode = ArchiveSortMode.DATE_NEWEST,
            ).summaryLines(),
        )
        assertEquals(
            listOf("Zeta 0 - 0 Beta", "Middle 0 - 0 Delta", "Alpha 0 - 0 Omega"),
            getFilteredArchiveState(
                archivedGames = archives,
                selectedCategory = ArchivedGameCategory.COMPLETED,
                filterSelections = ArchiveFilterSelections(),
                sortMode = ArchiveSortMode.DATE_OLDEST,
            ).summaryLines(),
        )
        assertEquals(
            listOf("Alpha 0 - 0 Omega", "Middle 0 - 0 Delta", "Zeta 0 - 0 Beta"),
            getFilteredArchiveState(
                archivedGames = archives,
                selectedCategory = ArchivedGameCategory.COMPLETED,
                filterSelections = ArchiveFilterSelections(),
                sortMode = ArchiveSortMode.TEAM_ONE_AZ,
            ).summaryLines(),
        )
        assertEquals(
            listOf("Zeta 0 - 0 Beta", "Middle 0 - 0 Delta", "Alpha 0 - 0 Omega"),
            getFilteredArchiveState(
                archivedGames = archives,
                selectedCategory = ArchivedGameCategory.COMPLETED,
                filterSelections = ArchiveFilterSelections(),
                sortMode = ArchiveSortMode.TEAM_ONE_ZA,
            ).summaryLines(),
        )
        assertEquals(
            listOf("Zeta 0 - 0 Beta", "Middle 0 - 0 Delta", "Alpha 0 - 0 Omega"),
            getFilteredArchiveState(
                archivedGames = archives,
                selectedCategory = ArchivedGameCategory.COMPLETED,
                filterSelections = ArchiveFilterSelections(),
                sortMode = ArchiveSortMode.TEAM_TWO_AZ,
            ).summaryLines(),
        )
        assertEquals(
            listOf("Alpha 0 - 0 Omega", "Middle 0 - 0 Delta", "Zeta 0 - 0 Beta"),
            getFilteredArchiveState(
                archivedGames = archives,
                selectedCategory = ArchivedGameCategory.COMPLETED,
                filterSelections = ArchiveFilterSelections(),
                sortMode = ArchiveSortMode.TEAM_TWO_ZA,
            ).summaryLines(),
        )
    }

    /**
     * Verify archive filter/sort state is kept while browsing archives and reset when
     * archive navigation is opened fresh.
     */
    @Test
    fun archiveFilterNavigation() {
        // Apply filter/sort state inside archive navigation.
        val viewModel = AppViewModel(NoOpAppStateStorage)
        viewModel.openArchivedGames()
        viewModel.openArchivedGameCategory(ArchivedGameCategory.COMPLETED)
        viewModel.updateArchiveFilterSelections(
            ArchiveFilterField.TOURNAMENT,
            setOf("Summer Solstice"),
        )
        viewModel.updateArchiveSortMode(ArchiveSortMode.TEAM_TWO_ZA)
        assertEquals(setOf("Summer Solstice"), viewModel.archiveFilterSelections.tournaments)
        assertEquals(ArchiveSortMode.TEAM_TWO_ZA, viewModel.archiveSortMode)

        // Returning to the category landing page keeps the state for continued archive browsing.
        viewModel.returnToArchivedGameCategories()
        assertNull(viewModel.selectedArchiveCategory)
        assertEquals(setOf("Summer Solstice"), viewModel.archiveFilterSelections.tournaments)
        assertEquals(ArchiveSortMode.TEAM_TWO_ZA, viewModel.archiveSortMode)

        // Opening the archive section again starts with fresh filter/sort state.
        viewModel.goHome()
        viewModel.openArchivedGames()
        assertFalse(viewModel.archiveFilterSelections.isActive())
        assertEquals(ArchiveSortMode.DATE_NEWEST, viewModel.archiveSortMode)
    }

    /**
     * Verify archived games open as summaries within archive navigation and return to the
     * archive list on Back.
     */
    @Test
    fun archivedGameSummary() {
        // Archive a completed game and open it as an archived summary.
        val viewModel = AppViewModel(NoOpAppStateStorage)
        viewModel.startNewGame(now = 123_000L)
        viewModel.finishSetup(now = 123_000L)
        val finishedGame = viewModel.liveState!!.copy(phase = GamePhase.GAME_OVER)
        viewModel.updateLiveGame(finishedGame)
        viewModel.goHome()
        viewModel.archiveCompletedGame()
        assertNull(viewModel.liveState)
        assertEquals(1, viewModel.archivedGames.size)
        val archivedGame = viewModel.archivedGames.single()
        assertEquals(GamePhase.GAME_OVER, archivedGame.phase)

        // Opening the archive should show the summary without leaving archive navigation.
        viewModel.openArchivedGame(0, now = 123_000L)
        assertEquals(AppScreen.ARCHIVED_GAMES, viewModel.screen)
        assertEquals(archivedGame, viewModel.viewingArchivedGame!!)

        // Back navigation returns from the archived summary to the archive list.
        viewModel.goBackFromCurrentScreen()
        assertEquals(AppScreen.ARCHIVED_GAMES, viewModel.screen)
        assertNull(viewModel.viewingArchivedGame)

        // Back from a selected archive category returns to the category landing page.
        viewModel.openArchivedGameCategory(ArchivedGameCategory.COMPLETED)
        assertEquals(ArchivedGameCategory.COMPLETED, viewModel.selectedArchiveCategory)
        viewModel.goBackFromCurrentScreen()
        assertEquals(AppScreen.ARCHIVED_GAMES, viewModel.screen)
        assertNull(viewModel.selectedArchiveCategory)

        // Reopening the archived summary preserves the same archive navigation state.
        viewModel.openArchivedGame(0, now = 123_000L)
        assertEquals(AppScreen.ARCHIVED_GAMES, viewModel.screen)
        assertEquals(archivedGame, viewModel.viewingArchivedGame!!)
    }

    /**
     * Verify the current game can open from archive navigation as a summary without being
     * moved into archived storage.
     */
    @Test
    fun currentGameSummary() {
        // A live current game can be viewed as a summary without creating an archive row.
        val viewModel = AppViewModel(NoOpAppStateStorage)
        viewModel.startNewGame(now = 123_000L)
        viewModel.finishSetup(now = 123_000L)
        val currentGame = viewModel.liveState!!.beginLivePoint(123_000L)
        viewModel.updateLiveGame(currentGame)
        viewModel.openArchivedGames()
        viewModel.openArchivedGameCategory(ArchivedGameCategory.IN_PROGRESS)
        viewModel.openCurrentGameSummary()
        assertEquals(AppScreen.LIVE, viewModel.screen)
        assertTrue(viewModel.viewingCurrentGameSummary)
        assertTrue(viewModel.archivedGames.isEmpty())
        assertEquals(currentGame, viewModel.currentLiveState)

        // Back returns to the in-progress archive list, while explicit resume returns to live play.
        viewModel.goBackFromCurrentScreen()
        assertEquals(AppScreen.ARCHIVED_GAMES, viewModel.screen)
        assertEquals(ArchivedGameCategory.IN_PROGRESS, viewModel.selectedArchiveCategory)
        assertFalse(viewModel.viewingCurrentGameSummary)
        viewModel.openCurrentGameSummary()
        viewModel.resumeCurrentGame()
        assertEquals(AppScreen.LIVE, viewModel.screen)
        assertFalse(viewModel.viewingCurrentGameSummary)

        // When the same summary opens from live-game navigation, Back returns to live play.
        viewModel.openCurrentGameSummary()
        viewModel.goBackFromCurrentScreen()
        assertEquals(AppScreen.LIVE, viewModel.screen)
        assertFalse(viewModel.viewingCurrentGameSummary)

        // A completed current game opens the normal current-game summary.
        val completedCurrentGame = currentGame.copy(phase = GamePhase.GAME_OVER)
        viewModel.updateLiveGame(completedCurrentGame)
        viewModel.openArchivedGames()
        viewModel.openArchivedGameCategory(ArchivedGameCategory.IN_PROGRESS)
        viewModel.openCurrentGameSummary()
        assertEquals(AppScreen.LIVE, viewModel.screen)
        assertFalse(viewModel.viewingCurrentGameSummary)
        assertEquals(GamePhase.GAME_OVER, viewModel.liveState!!.phase)
        assertEquals(completedCurrentGame, viewModel.currentLiveState)

        // A stale UI callback should not leave archive navigation when no current game exists.
        viewModel.deleteCurrentGame()
        viewModel.openArchivedGames()
        viewModel.openArchivedGameCategory(ArchivedGameCategory.IN_PROGRESS)
        viewModel.openCurrentGameSummary()
        assertEquals(AppScreen.ARCHIVED_GAMES, viewModel.screen)
        assertEquals(ArchivedGameCategory.IN_PROGRESS, viewModel.selectedArchiveCategory)
        assertFalse(viewModel.viewingCurrentGameSummary)
        assertNull(viewModel.currentLiveState)
    }

    /**
     * Verify restoring an archived game promotes it to current game while saving any
     * active current preview.
     */
    @Test
    fun archiveRestoreSavesCurrentPreview() {
        // Archive a completed game, then create a separate current preview.
        val viewModel = AppViewModel(NoOpAppStateStorage)
        viewModel.startNewGame(now = 123_000L)
        viewModel.finishSetup(now = 123_000L)
        val archivedGame = viewModel.liveState!!.copy(phase = GamePhase.GAME_OVER)
        viewModel.updateLiveGame(archivedGame)
        viewModel.archiveCompletedGame()
        val archivedState = viewModel.archivedGames.single()

        // Create a separate current preview that will be saved during restore.
        viewModel.startNewGame(now = 123_000L)
        viewModel.finishSetup(now = 123_000L)
        val previewState = viewModel.liveState!!
        assertTrue(previewState.isInitialLivePreview())

        // Restoring the archive promotes it and saves the previous current preview.
        viewModel.openArchivedGame(0, now = 123_000L)
        viewModel.restoreCompletedGame(now = 123_000L)
        assertEquals(AppScreen.LIVE, viewModel.screen)
        assertFalse(viewModel.viewingCurrentGameSummary)
        assertNull(viewModel.viewingArchivedGame)
        assertEquals(archivedState, viewModel.liveState)
        assertEquals(1, viewModel.archivedGames.size)
        assertEquals(
            ArchivedGameCategory.IN_PROGRESS,
            viewModel.archivedGames.single().archiveCategory,
        )
        assertEquals(previewState, viewModel.archivedGames.single())
    }

    /**
     * Verify setup drafts can be saved for later, removed from saved setups when loaded,
     * and used as the source for repeated tournament game-information defaults.
     */
    @Test
    fun savedSetupLifecycle() {
        // Saving a setup draft for later should move it to the archive in the SETUP category.
        val viewModel = AppViewModel(NoOpAppStateStorage)
        viewModel.startNewGame(now = 123_000L)
        val tournamentRules = GameRules(gameTo = 11, hasFloaterTimeout = true)
        val savedSetup = viewModel.setupState.copy(
            tournamentName = "Summer Solstice",
            division = GameDivision.OPEN,
            level = "Club",
            gameContext = "Pool play",
            observers = "Mike",
            rules = tournamentRules,
            teamOne = TeamState("", TeamColorChoice.GREEN),
            teamTwo = TeamState("Known Opponent", TeamColorChoice.YELLOW),
        )
        viewModel.updateSetup(savedSetup)
        viewModel.saveSetupForLater()
        assertEquals(AppScreen.HOME, viewModel.screen)
        assertFalse(viewModel.hasSetupDraft)
        assertNull(viewModel.liveState)
        assertEquals(1, viewModel.archivedGames.size)
        assertEquals(ArchivedGameCategory.SETUP, viewModel.archivedGames.single().archiveCategory)
        assertEquals(GamePhase.SETUP, viewModel.archivedGames.single().phase)
        assertEquals(savedSetup, viewModel.archivedGames.single())
        assertEquals(
            "Team 1 vs Known Opponent at ${formatClockTime(savedSetup.startTime)}",
            viewModel.archivedGames.single().gameListSummaryLine(),
        )

        // Opening a saved setup when no current game exists restores it as the current setup draft.
        val directRestoreViewModel = AppViewModel(NoOpAppStateStorage)
        directRestoreViewModel.startNewGame(now = 123_000L)
        directRestoreViewModel.updateSetup(savedSetup)
        directRestoreViewModel.saveSetupForLater()
        directRestoreViewModel.openArchivedGames()
        directRestoreViewModel.openArchivedGameCategory(ArchivedGameCategory.SETUP)
        directRestoreViewModel.openArchivedGame(0, now = 123_000L)
        assertEquals(AppScreen.SETUP, directRestoreViewModel.screen)
        assertTrue(directRestoreViewModel.hasSetupDraft)
        assertEquals(savedSetup, directRestoreViewModel.setupState)
        assertTrue(directRestoreViewModel.archivedGames.isEmpty())

        // Restoring a saved setup over an initial live preview saves that preview aside.
        val initialPreviewRestoreViewModel = AppViewModel(NoOpAppStateStorage)
        initialPreviewRestoreViewModel.startNewGame(now = 123_000L)
        initialPreviewRestoreViewModel.updateSetup(savedSetup)
        initialPreviewRestoreViewModel.saveSetupForLater()
        initialPreviewRestoreViewModel.startNewGame(now = 123_000L)
        initialPreviewRestoreViewModel.finishSetup(now = 123_000L)
        val initialPreview = initialPreviewRestoreViewModel.liveState!!
        assertTrue(initialPreview.isInitialLivePreview())
        initialPreviewRestoreViewModel.openArchivedGames()
        initialPreviewRestoreViewModel.openArchivedGameCategory(ArchivedGameCategory.SETUP)
        initialPreviewRestoreViewModel.openArchivedGame(0, now = 123_000L)
        assertEquals(AppScreen.SETUP, initialPreviewRestoreViewModel.screen)
        assertEquals(savedSetup, initialPreviewRestoreViewModel.setupState)
        assertEquals(1, initialPreviewRestoreViewModel.archivedGames.size)
        assertEquals(
            ArchivedGameCategory.IN_PROGRESS,
            initialPreviewRestoreViewModel.archivedGames.single().archiveCategory,
        )
        assertEquals(initialPreview, initialPreviewRestoreViewModel.archivedGames.single())

        // Starting another game carries forward tournament context and rules, but not teams.
        viewModel.startNewGame(now = 123_000L)
        assertEquals("Summer Solstice", viewModel.setupState.tournamentName)
        assertEquals(GameDivision.OPEN, viewModel.setupState.division)
        assertEquals("Club", viewModel.setupState.level)
        assertEquals("Pool play", viewModel.setupState.gameContext)
        assertEquals("Mike", viewModel.setupState.observers)
        assertEquals(tournamentRules, viewModel.setupState.rules)
        assertEquals("", viewModel.setupState.teamOne.name)
        assertEquals("", viewModel.setupState.teamTwo.name)

        // Restoring a saved setup saves the current setup draft aside instead of losing it.
        val unsavedDraft = viewModel.setupState.copy(
            teamOne = TeamState("Unsaved", TeamColorChoice.WHITE),
        )
        viewModel.updateSetup(unsavedDraft)
        viewModel.openArchivedGames()
        viewModel.openArchivedGameCategory(ArchivedGameCategory.SETUP)
        viewModel.openArchivedGame(0, now = 123_000L)
        assertEquals(AppScreen.SETUP, viewModel.screen)
        assertTrue(viewModel.hasSetupDraft)
        assertNull(viewModel.liveState)
        assertEquals(savedSetup, viewModel.setupState)
        assertEquals(1, viewModel.archivedGames.size)
        assertEquals(ArchivedGameCategory.SETUP, viewModel.archivedGames.single().archiveCategory)
        assertEquals(unsavedDraft, viewModel.archivedGames.single())

        // Restoring a saved setup while a real current game exists archives that current game,
        // putting it in the IN_PROGRESS category in the archive.
        viewModel.saveSetupForLater()
        viewModel.startNewGame(now = 123_000L)
        viewModel.updateSetup(
            viewModel.setupState.copy(
                teamOne = TeamState("Current", TeamColorChoice.WHITE),
                teamTwo = TeamState("Live", TeamColorChoice.BLUE),
            )
        )
        viewModel.finishSetup(now = 123_000L)
        assertEquals("Current 0 - 0 Live", viewModel.liveState!!.gameListSummaryLine())
        viewModel.updateLiveGame(viewModel.liveState!!.beginLivePoint())
        viewModel.openArchivedGames()
        viewModel.openArchivedGameCategory(ArchivedGameCategory.SETUP)
        viewModel.openArchivedGame(1, now = 123_000L)
        assertEquals(savedSetup, viewModel.setupState)
        assertEquals(2, viewModel.archivedGames.size)
        assertEquals(ArchivedGameCategory.SETUP, viewModel.archivedGames.first().archiveCategory)
        assertEquals(unsavedDraft, viewModel.archivedGames.first())
        assertEquals(
            ArchivedGameCategory.IN_PROGRESS,
            viewModel.archivedGames.last().archiveCategory,
        )
        assertEquals("Current", viewModel.archivedGames.last().teamOne.name)
    }

    /**
     * Verify a viewed saved in-progress summary can be moved directly to completed archives.
     */
    @Test
    fun viewedSavedInProgressDirectArchive() {
        // Build a saved in-progress archive and open its summary.
        val viewModel = AppViewModel(NoOpAppStateStorage)
        viewModel.startNewGame(now = 123_000L)
        viewModel.finishSetup(now = 123_000L)
        viewModel.updateLiveGame(
            viewModel.liveState!!.copy(
                phase = GamePhase.GAME_OVER,
                teamOne = viewModel.liveState!!.teamOne.copy(name = "Completed first"),
            )
        )
        viewModel.archiveCompletedGame()
        val existingCompletedArchive = viewModel.archivedGames.single()

        viewModel.startNewGame(now = 123_000L)
        viewModel.finishSetup(now = 123_000L)
        viewModel.updateLiveGame(viewModel.liveState!!.beginLivePoint())
        val savedLiveState = viewModel.liveState!!
        viewModel.startNewGame(now = 123_000L)
        assertEquals(2, viewModel.archivedGames.size)
        assertEquals(existingCompletedArchive, viewModel.archivedGames.first())
        val savedArchive = viewModel.archivedGames.last()
        assertEquals(savedLiveState, savedArchive)
        assertEquals(ArchivedGameCategory.IN_PROGRESS, savedArchive.archiveCategory)
        assertEquals(GamePhase.LIVE_POINT, savedArchive.phase)
        assertNull(savedArchive.endEpoch)
        viewModel.openArchivedGames()
        viewModel.openArchivedGameCategory(ArchivedGameCategory.IN_PROGRESS)
        viewModel.openArchivedGame(1, now = 123_000L)
        assertEquals(AppScreen.ARCHIVED_GAMES, viewModel.screen)
        assertEquals(
            ArchivedGameCategory.IN_PROGRESS,
            viewModel.viewingArchivedGame!!.archiveCategory,
        )

        // The summary-page archive action moves the game to the completed section and returns
        // the view to the saved in-progress games list.
        viewModel.archiveSavedInProgressGame(now = 234_000L)
        assertEquals(AppScreen.ARCHIVED_GAMES, viewModel.screen)
        assertEquals(ArchivedGameCategory.IN_PROGRESS, viewModel.selectedArchiveCategory)
        assertNull(viewModel.viewingArchivedGame)
        assertEquals(2, viewModel.archivedGames.size)
        val convertedArchive = viewModel.archivedGames.last()
        assertEquals(ArchivedGameCategory.COMPLETED, convertedArchive.archiveCategory)
        assertEquals(234_000L, convertedArchive.endEpoch)
        assertEquals(
            savedLiveState.pruneUndoHistory(clearCountdown = false),
            convertedArchive.undoLastAction().copy(redoEntry = null),
        )

        // Restoring the completed row can still undo End game back to the saved live state.
        viewModel.openArchivedGames()
        viewModel.openArchivedGameCategory(ArchivedGameCategory.COMPLETED)
        viewModel.openArchivedGame(1, now = 123_000L)
        viewModel.restoreCompletedGame(now = 123_000L)
        assertEquals(GamePhase.GAME_OVER, viewModel.liveState!!.phase)
        assertEquals(
            savedLiveState.pruneUndoHistory(clearCountdown = false),
            viewModel.liveState!!.undoLastAction().copy(redoEntry = null),
        )
    }

    /**
     * Verify a completed current game can be reopened from Home and then moved into
     * Archived games as a summary reached from archive navigation.
     */
    @Test
    fun completedCurrentGameArchive() {
        // Complete the current game and verify Home opens it as the current summary.
        val viewModel = AppViewModel(NoOpAppStateStorage)
        viewModel.startNewGame(now = 123_000L)
        viewModel.finishSetup(now = 123_000L)
        val completedGame = viewModel.liveState!!.copy(phase = GamePhase.GAME_OVER)
        viewModel.updateLiveGame(completedGame)
        assertNull(viewModel.currentGameHomeSubtitle)
        viewModel.goHome()
        viewModel.openCompletedGame()
        assertEquals(AppScreen.LIVE, viewModel.screen)
        assertEquals(completedGame, viewModel.currentLiveState)
        assertFalse(viewModel.viewingCurrentGameSummary)
        assertNull(viewModel.viewingArchivedGame)

        // Archiving the completed current game clears the current slot.
        viewModel.goHome()
        viewModel.archiveCompletedGame()
        assertNull(viewModel.liveState)
        assertEquals(1, viewModel.archivedGames.size)
        assertEquals(
            ArchivedGameCategory.COMPLETED,
            viewModel.archivedGames.single().archiveCategory,
        )

        // Opening the archived copy should expose it as an archive summary.
        viewModel.openArchivedGame(0, now = 123_000L)
        assertEquals(AppScreen.ARCHIVED_GAMES, viewModel.screen)
        assertEquals(viewModel.archivedGames.single(), viewModel.viewingArchivedGame!!)
    }

    /**
     * Verify a restored completed archive can undo End game while older undo history
     * stays pruned.
     */
    @Test
    fun completedArchiveUndo() {
        // Archive a completed game with deeper undo history.
        val viewModel = AppViewModel(NoOpAppStateStorage)
        viewModel.startNewGame(now = 123_000L)
        viewModel.finishSetup(now = 123_000L)
        val initialLiveState = viewModel.liveState!!
        val beforeEndGame = initialLiveState.copy(
            undoEntry = UndoEntry("Undo Start point", initialLiveState),
        )
        val completedGame = beforeEndGame.copy(
            phase = GamePhase.GAME_OVER,
            undoEntry = UndoEntry("Undo End game", beforeEndGame),
            redoEntry = beforeEndGame,
        )
        viewModel.updateLiveGame(completedGame)
        viewModel.archiveCompletedGame()
        val archivedState = viewModel.archivedGames.single()
        val prunedBeforeEndGame = beforeEndGame.pruneUndoHistory()
        assertEquals("Undo End game", archivedState.undoEntry?.label)
        assertEquals(prunedBeforeEndGame, archivedState.undoEntry!!.previous)
        assertNull(archivedState.redoEntry)

        // Restoring the archive keeps the end-game undo while older undo entries stay pruned.
        viewModel.openArchivedGame(0, now = 123_000L)
        viewModel.restoreCompletedGame(now = 123_000L)
        val restoredGame = viewModel.liveState!!
        val restoredUndo = restoredGame.undoLastAction()
        assertEquals(GamePhase.GAME_OVER, restoredGame.phase)
        assertEquals("Undo End game", restoredGame.undoEntry?.label)
        assertEquals(prunedBeforeEndGame, restoredUndo.copy(redoEntry = null))
        assertNotNull(restoredUndo.redoEntry)
    }

    /**
     * Verify completed-game restore ignores a missing selection and promotes a valid
     * archived summary without automatically saving aside setup-only drafts.
     */
    @Test
    fun archiveRestoreSelection() {
        // Restore with no selected archived summary is harmless.
        val viewModel = AppViewModel(NoOpAppStateStorage)
        viewModel.restoreCompletedGame(now = 123_000L)
        assertEquals(AppScreen.HOME, viewModel.screen)
        assertNull(viewModel.liveState)
        assertTrue(viewModel.archivedGames.isEmpty())

        // Restoring from the full archive list removes the selected archive and promotes it
        // to current game.
        viewModel.startNewGame(now = 123_000L)
        viewModel.finishSetup(now = 123_000L)
        viewModel.updateLiveGame(
            viewModel.liveState!!.copy(
                phase = GamePhase.GAME_OVER,
                teamOne = TeamState("First Archive", TeamColorChoice.WHITE),
            )
        )
        viewModel.archiveCompletedGame()

        // Add a second archive so a valid restore can prove it removes only the selected game.
        viewModel.startNewGame(now = 123_000L)
        viewModel.finishSetup(now = 123_000L)
        viewModel.updateLiveGame(
            viewModel.liveState!!.copy(
                phase = GamePhase.GAME_OVER,
                teamOne = TeamState("Second Archive", TeamColorChoice.WHITE),
            )
        )
        viewModel.archiveCompletedGame()

        // Archive indexes identify stored rows directly, regardless of the selected category.
        viewModel.openArchivedGames()
        viewModel.openArchivedGameCategory(ArchivedGameCategory.SETUP)
        viewModel.openArchivedGame(1, now = 123_000L)
        viewModel.restoreCompletedGame(now = 123_000L)
        assertEquals(AppScreen.LIVE, viewModel.screen)
        assertFalse(viewModel.viewingCurrentGameSummary)
        assertNull(viewModel.viewingArchivedGame)
        assertEquals("Second Archive", viewModel.liveState!!.teamOne.name)
        assertEquals(1, viewModel.archivedGames.size)
        assertEquals("First Archive", viewModel.archivedGames.single().teamOne.name)
    }

    /**
     * Verify deleting the current game, one archived game, and all archived games clears
     * the corresponding ViewModel state.
     */
    @Test
    fun gameDeletion() {
        // Deleting the current game clears current and currentLive state.
        val viewModel = AppViewModel(NoOpAppStateStorage)
        viewModel.startNewGame(now = 123_000L)
        viewModel.finishSetup(now = 123_000L)
        val currentGame = viewModel.liveState!!
        viewModel.deleteCurrentGame()
        assertEquals(AppScreen.HOME, viewModel.screen)
        assertNull(viewModel.liveState)
        assertNull(viewModel.currentLiveState)

        // Deleting a viewed archived game clears the selection.
        viewModel.updateLiveGame(currentGame.copy(phase = GamePhase.GAME_OVER))
        viewModel.archiveCompletedGame()
        assertEquals(1, viewModel.archivedGames.size)
        viewModel.openArchivedGame(0, now = 123_000L)
        assertNotNull(viewModel.viewingArchivedGame)
        viewModel.deleteArchivedGame(0)
        assertTrue(viewModel.archivedGames.isEmpty())
        assertNull(viewModel.currentLiveState)

        // Category bulk delete clears only the selected category.
        viewModel.updateLiveGame(currentGame.beginLivePoint())
        viewModel.startNewGame(now = 123_000L)
        assertEquals(1, viewModel.archivedGames.size)
        assertEquals(
            ArchivedGameCategory.IN_PROGRESS,
            viewModel.archivedGames.single().archiveCategory,
        )
        viewModel.updateLiveGame(currentGame.copy(phase = GamePhase.GAME_OVER))
        viewModel.archiveCompletedGame()
        assertEquals(2, viewModel.archivedGames.size)
        viewModel.openArchivedGames()
        viewModel.openArchivedGameCategory(ArchivedGameCategory.COMPLETED)
        viewModel.deleteArchivedGamesInSelectedCategory()
        assertEquals(1, viewModel.archivedGames.size)
        assertEquals(
            ArchivedGameCategory.IN_PROGRESS,
            viewModel.archivedGames.single().archiveCategory,
        )
        assertEquals(ArchivedGameCategory.COMPLETED, viewModel.selectedArchiveCategory)

        // Selected bulk delete can target the filtered row indices without removing hidden
        // archived games or other categories.
        listOf("First filtered game", "Hidden game", "Second filtered game").forEach { teamName ->
            viewModel.updateLiveGame(
                currentGame.copy(
                    phase = GamePhase.GAME_OVER,
                    teamOne = currentGame.teamOne.copy(name = teamName),
                ),
            )
            viewModel.archiveCompletedGame()
        }
        viewModel.deleteSelectedArchivedGames(setOf(1, 3))
        assertEquals(2, viewModel.archivedGames.size)
        assertEquals(
            ArchivedGameCategory.IN_PROGRESS,
            viewModel.archivedGames.first().archiveCategory,
        )
        assertEquals(ArchivedGameCategory.COMPLETED, viewModel.archivedGames.last().archiveCategory)
        assertEquals("Hidden game", viewModel.archivedGames.last().teamOne.name)

        // Deleting all archived games clears the archive list and the viewed archive.
        viewModel.updateLiveGame(currentGame.copy(phase = GamePhase.GAME_OVER))
        viewModel.archiveCompletedGame()
        viewModel.updateLiveGame(
            currentGame.copy(
                phase = GamePhase.GAME_OVER,
                teamOne = currentGame.teamOne.copy(name = "Second archived game"),
            ),
        )
        viewModel.archiveCompletedGame()
        assertEquals(4, viewModel.archivedGames.size)
        viewModel.openArchivedGame(1, now = 123_000L)
        assertNotNull(viewModel.viewingArchivedGame)
        viewModel.deleteAllArchivedGames()
        assertTrue(viewModel.archivedGames.isEmpty())
        assertNull(viewModel.currentLiveState)
    }

    /**
     * Verify starting over from an already completed game archives it without adding
     * another close-game wrapper or live-only state.
     */
    @Test
    fun startingOverFromCompletedGame() {
        // Starting over from an already completed game should not wrap End game again.
        val viewModel = AppViewModel(NoOpAppStateStorage)
        viewModel.startNewGame(now = 123_000L)
        viewModel.finishSetup(now = 123_000L)
        val beforeUndoAction = viewModel.liveState!!
        val completedGame = beforeUndoAction.copy(
            phase = GamePhase.GAME_OVER,
            countdown = CountdownState(
                kind = CountdownKind.BETWEEN_POINTS,
                label = "Pull in",
                durationSeconds = 80,
                targetEpoch = 80_000L,
                betweenPointsTarget = BetweenPointsCountdownTarget.PULL,
            ),
            undoEntry = UndoEntry("Undo End game", beforeUndoAction),
        )
        viewModel.updateLiveGame(completedGame)
        viewModel.startNewGame(now = 123_000L)
        assertEquals(AppScreen.SETUP, viewModel.screen)
        assertNull(viewModel.liveState)
        assertEquals(1, viewModel.archivedGames.size)
        assertEquals(GamePhase.GAME_OVER, viewModel.archivedGames.single().phase)
        assertNull(viewModel.archivedGames.single().countdown)
        assertEquals("Undo End game", viewModel.archivedGames.single().undoEntry?.label)
        assertEquals(
            beforeUndoAction.pruneUndoHistory(),
            viewModel.archivedGames.single().undoEntry!!.previous,
        )
        assertNull(viewModel.archivedGames.single().redoEntry)
    }

    /**
     * Verify restoring an accidentally archived active game makes it current again with
     * undo state and preserves any replaced current game as restorable.
     */
    @Test
    fun archivedActiveGameRestore() {
        // Build an active game that will be archived with restorable live state.
        val storeDir = temporaryFolder.newFolder()
        val viewModel = AppViewModel(FileAppStateStorage(storeDir))
        viewModel.startNewGame(now = 123_000L)
        val setup = viewModel.setupState.copy(
            teamOne = TeamState("Viscous Coupling", TeamColorChoice.BLUE),
            teamTwo = TeamState("Animal", TeamColorChoice.PINK),
        )
        viewModel.updateSetup(setup)
        viewModel.finishSetup(now = 123_000L)
        val activeGame = viewModel.liveState!!.beginLivePoint()
        assertNotNull(activeGame.undoEntry)
        viewModel.updateLiveGame(activeGame)

        // Starting a new game saves the current game without ending it or pruning undo.
        viewModel.startNewGame(now = 123_000L)
        assertEquals(AppScreen.SETUP, viewModel.screen)
        assertTrue(viewModel.hasSetupDraft)
        assertNull(viewModel.liveState)
        assertEquals(1, viewModel.archivedGames.size)
        val archivedGame = viewModel.archivedGames.single()
        assertEquals(ArchivedGameCategory.IN_PROGRESS, archivedGame.archiveCategory)
        assertEquals(activeGame, archivedGame)
        assertEquals(GamePhase.LIVE_POINT, archivedGame.phase)
        assertEquals(activeGame.undoEntry, archivedGame.undoEntry)
        assertEquals(activeGame.redoEntry, archivedGame.redoEntry)
        assertNull(archivedGame.endEpoch)

        // Reload the ViewModel to verify the recoverable active state survives phone storage.
        val restoredViewModel = AppViewModel(FileAppStateStorage(storeDir))
        assertEquals(archivedGame, restoredViewModel.archivedGames.single())
        val replacementSetup = restoredViewModel.setupState.copy(
            teamOne = TeamState("Replacement Current", TeamColorChoice.WHITE),
            teamTwo = TeamState("Replacement Opponent", TeamColorChoice.BLUE),
        )
        restoredViewModel.updateSetup(replacementSetup)
        restoredViewModel.finishSetup(now = 123_000L)
        val replacementCurrent = restoredViewModel.liveState!!.beginLivePoint()
        restoredViewModel.updateLiveGame(replacementCurrent)

        // Restoring the archive should save the replaced current game.
        restoredViewModel.openArchivedGame(0, now = 123_000L)
        restoredViewModel.restoreCompletedGame(now = 123_000L)
        assertEquals(AppScreen.LIVE, restoredViewModel.screen)
        assertEquals(1, restoredViewModel.archivedGames.size)
        val replacementArchive = restoredViewModel.archivedGames.single()
        assertEquals(GamePhase.LIVE_POINT, replacementArchive.phase)
        assertEquals(replacementSetup.teamOne.name, replacementArchive.teamOne.name)
        assertEquals(replacementSetup.teamTwo.name, replacementArchive.teamTwo.name)
        assertEquals(replacementCurrent, replacementArchive)
        assertEquals(ArchivedGameCategory.IN_PROGRESS, replacementArchive.archiveCategory)
        assertFalse(restoredViewModel.hasSetupDraft)
        assertEquals(SetupMode.EDIT_CURRENT_GAME, restoredViewModel.setupMode)
        assertEquals(activeGame, restoredViewModel.liveState)
        assertEquals(GamePhase.LIVE_POINT, restoredViewModel.liveState!!.phase)
        assertEquals(setup.teamOne.name, restoredViewModel.setupState.teamOne.name)
        assertEquals(setup.teamTwo.name, restoredViewModel.setupState.teamTwo.name)
    }

    /**
     * Verify a new game's default rules prefer the most recent archived completed game's
     * rules when no current game exists.
     */
    @Test
    fun newGameRulesFromArchive() {
        // Starting from Home prefers rules from the most recently archived completed game.
        val viewModel = AppViewModel(NoOpAppStateStorage)
        viewModel.startNewGame(now = 123_000L)
        val tournamentRules = GameRules(
            gameTo = 13,
            halftimeMinutes = 5,
            useHalfCap = true,
            halfCapMinutes = 35,
            useSoftCap = false,
            softCapMinutes = 75,
            useHardCap = true,
            hardCapMinutes = 95,
            timeoutsPerHalf = 1,
            hasFloaterTimeout = true,
        )
        viewModel.updateSetup(viewModel.setupState.copy(rules = tournamentRules))
        viewModel.finishSetup(now = 123_000L)
        viewModel.updateLiveGame(viewModel.liveState!!.copy(phase = GamePhase.GAME_OVER))
        viewModel.archiveCompletedGame()
        viewModel.startNewGame(now = 123_000L)
        assertEquals(tournamentRules, viewModel.setupState.rules)
        assertNull(viewModel.liveState)
        assertEquals(SetupMode.NEW_GAME, viewModel.setupMode)
    }

    /**
     * Verify starting over from an active current game uses that game's rules as the next
     * setup draft defaults.
     */
    @Test
    fun newGameRulesFromCurrentGame() {
        // Starting over from an active current game carries its rules into the next setup draft.
        val viewModel = AppViewModel(NoOpAppStateStorage)
        viewModel.startNewGame(now = 123_000L)
        val currentRules = GameRules(gameTo = 11, hardCapMinutes = 80, hasFloaterTimeout = true)
        viewModel.updateSetup(viewModel.setupState.copy(rules = currentRules))
        viewModel.finishSetup(now = 123_000L)
        viewModel.updateLiveGame(viewModel.liveState!!.beginLivePoint())
        val savedState = viewModel.liveState!!
        viewModel.startNewGame(now = 123_000L)
        assertEquals(currentRules, viewModel.setupState.rules)
        assertEquals(currentRules, viewModel.archivedGames.single().rules)
        assertEquals(savedState, viewModel.archivedGames.single())
        assertEquals(
            ArchivedGameCategory.IN_PROGRESS,
            viewModel.archivedGames.single().archiveCategory,
        )
        assertNull(viewModel.archivedGames.single().endEpoch)
    }

    /**
     * Build an archive with only the fields relevant to archive filtering.
     *
     * @param tournament Tournament name for the archive.
     * @param division Division for the archive.
     * @param level Competition level for the archive.
     * @param teamOne Team 1 name.
     * @param teamTwo Team 2 name.
     * @param observers Observer text for the archive.
     * @param startDate Local start date for the archive.
     * @param startTime Local start time for the archive.
     */
    private fun archiveForFilterTest(
        tournament: String = "",
        division: GameDivision? = null,
        level: String = "",
        teamOne: String,
        teamTwo: String,
        observers: String = "",
        startDate: LocalDate,
        startTime: LocalTime,
    ): GameState {
        return standardLiveGameState(
            startDate = startDate,
            startTime = startTime,
        ).copy(
            phase = GamePhase.GAME_OVER,
            tournamentName = tournament,
            division = division,
            level = level,
            teamOne = TeamState(teamOne, TeamColorChoice.WHITE),
            teamTwo = TeamState(teamTwo, TeamColorChoice.BLUE),
            observers = observers,
        )
    }

    /// Return the visible summary lines from archive list rows.
    private fun FilteredArchiveState.summaryLines(): List<String> {
        return selectedGames.orEmpty().map { it.entry.summaryLine }
    }

    /// Return value/count pairs for one archive filter field.
    private fun FilteredArchiveState.valueCounts(
        field: ArchiveFilterField,
    ): List<Pair<String, Int>> {
        return availableFilterValues.getValue(field).map { it.value to it.gameCount }
    }
}
