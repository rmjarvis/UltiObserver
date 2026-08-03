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
     * Verify archive string filters expose cascading values, selectable N/A values,
     * and summaries of the selected checkbox-style values.
     */
    @Test
    fun archiveStringFilterSelections() {
        // Build archives with overlapping tournament, division, level, team, and observer data.
        val ring = archiveForFilterTest(
            tournament = "Pro Elite Challenge",
            division = GameDivision.OPEN,
            level = "Club",
            teamOne = "Ring of Fire",
            teamTwo = "Truck Stop",
            observerNames = listOf("Mike", "Gary"),
            startDate = LocalDate.of(2026, 5, 1),
            startTime = LocalTime.of(9, 0),
        )
        val dragN = archiveForFilterTest(
            tournament = "Pro Elite Challenge",
            division = GameDivision.MIXED,
            level = "Club",
            teamOne = "Drag'n Thrust",
            teamTwo = "Mixtape",
            observerNames = listOf("Gary", "Alex"),
            startDate = LocalDate.of(2026, 5, 2),
            startTime = LocalTime.of(11, 0),
        )
        val machine = archiveForFilterTest(
            tournament = "College Nationals",
            division = GameDivision.OPEN,
            level = "College",
            teamOne = "Michigan",
            teamTwo = "Pitt",
            observerNames = listOf("  Alex  ", ""),
            startDate = LocalDate.of(2026, 6, 1),
            startTime = LocalTime.of(10, 0),
        )
        val unknownTournament = archiveForFilterTest(
            tournament = "",
            division = null,
            level = "",
            teamOne = "Animal",
            teamTwo = "Shame",
            observerNames = emptyList(),
            startDate = LocalDate.of(2025, 12, 31),
            startTime = LocalTime.of(13, 0),
        )
        val savedSetup = ring.copy(phase = GamePhase.SETUP)
        val savedInProgress = dragN.copy(phase = GamePhase.LIVE_POINT)
        val archives = listOf(ring, dragN, machine, unknownTournament, savedSetup, savedInProgress)

        // The category and filter labels are the user-facing choices shown by the archive UI.
        assertEquals(
            listOf("Archived games", "In-progress games", "Saved setup drafts"),
            ArchivedGameCategory.entries.map { it.displayText },
        )
        assertEquals(
            listOf("No archived games yet.", "No in-progress games.", "No saved setup drafts."),
            ArchivedGameCategory.entries.map { it.emptyText },
        )
        assertEquals(
            listOf("Tournament", "Division", "Level", "Team", "Date", "Observers"),
            ArchiveFilterField.entries.map { it.displayText },
        )

        // Combining tournament and division filters keeps only rows matching both filters.
        val summerOpen = getFilteredArchiveState(
            archivedGames = archives,
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
        assertEquals(listOf(0), summerOpen.selectedGames.map { it.index })
        assertEquals(
            listOf(
                "Filters:",
                "    Tournament: Pro Elite Challenge",
                "    Division: Open",
                "Sorted by date, newest first",
            ).joinToString("\n"),
            summerOpen.filterAndSortSummaryText,
        )

        // Replacing selected values stores each checkbox-style filter in its own field.
        val selections = ArchiveFilterSelections()
            .withValues(ArchiveFilterField.TOURNAMENT, setOf("Pro Elite Challenge"))
            .withValues(ArchiveFilterField.DIVISION, setOf("Open", "Mixed"))
            .withValues(ArchiveFilterField.LEVEL, setOf("Club"))
            .withValues(ArchiveFilterField.TEAM, setOf("Ring of Fire"))
            .withValues(ArchiveFilterField.OBSERVERS, setOf("Gary"))
        assertTrue(selections.isActive())
        assertEquals(setOf("Pro Elite Challenge"), selections.valuesFor(ArchiveFilterField.TOURNAMENT))
        assertEquals(setOf("Open", "Mixed"), selections.valuesFor(ArchiveFilterField.DIVISION))
        assertEquals(setOf("Club"), selections.valuesFor(ArchiveFilterField.LEVEL))
        assertEquals(setOf("Ring of Fire"), selections.valuesFor(ArchiveFilterField.TEAM))
        assertEquals(setOf("Gary"), selections.valuesFor(ArchiveFilterField.OBSERVERS))

        // Clearing one checkbox-style filter leaves that field with no selected values.
        assertEquals(emptySet<String>(), selections.without(ArchiveFilterField.DIVISION).divisions)
        assertEquals(emptySet<String>(), selections.without(ArchiveFilterField.LEVEL).levels)
        assertEquals(emptySet<String>(), selections.without(ArchiveFilterField.TEAM).teams)
        assertEquals(emptySet<String>(), selections.without(ArchiveFilterField.OBSERVERS).observers)

        // Available values include counts of games matching that value under other filters.
        val unfiltered = getFilteredArchiveState(
            archivedGames = archives,
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
        assertEquals(
            listOf("Club" to 2, "College" to 1, ARCHIVE_FILTER_NA to 1),
            unfiltered.valueCounts(ArchiveFilterField.LEVEL),
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
            listOf("Gary" to 1, "Mike" to 1),
            summerOpen.valueCounts(ArchiveFilterField.OBSERVERS),
        )

        // Missing values are displayed as N/A and sorted after concrete values.
        val openDivision = getFilteredArchiveState(
            archivedGames = archives,
            selectedCategory = ArchivedGameCategory.COMPLETED,
            filterSelections = ArchiveFilterSelections(divisions = setOf("Open")),
            sortMode = ArchiveSortMode.DATE_NEWEST,
        )
        assertEquals(
            listOf("Alex" to 1, "Gary" to 1, "Mike" to 1),
            openDivision.valueCounts(ArchiveFilterField.OBSERVERS),
        )
        val noObserverGames = getFilteredArchiveState(
            archivedGames = archives,
            selectedCategory = ArchivedGameCategory.COMPLETED,
            filterSelections = ArchiveFilterSelections(
                observers = setOf(ARCHIVE_FILTER_NA),
            ),
            sortMode = ArchiveSortMode.DATE_OLDEST,
        )
        assertEquals(listOf("Animal 0 - 0 Shame"), noObserverGames.summaryLines())

        // Multiple selected values are summarized with plural labels and N/A last.
        val mixedSelections = ArchiveFilterSelections(
            tournaments = setOf(ARCHIVE_FILTER_NA, "Pro Elite Challenge"),
            divisions = setOf("Open", "Mixed"),
            levels = setOf("College", "Club"),
            teams = setOf("Ring of Fire", "Truck Stop"),
            observers = setOf("Gary", "Mike"),
        )
        assertEquals(
            listOf(
                "Filters:",
                "    Tournaments: Pro Elite Challenge, N/A",
                "    Divisions: Mixed, Open",
                "    Levels: Club, College",
                "    Teams: Ring of Fire, Truck Stop",
                "    Observers: Gary, Mike",
                "Sorted by second team",
            ).joinToString("\n"),
            filterAndSortSummaryText(mixedSelections, ArchiveSortMode.TEAM_TWO),
        )

        // Filters are only applied to completed archives; other categories keep storage order.
        val savedSetupState = getFilteredArchiveState(
            archivedGames = archives,
            selectedCategory = ArchivedGameCategory.SETUP,
            filterSelections = mixedSelections,
            sortMode = ArchiveSortMode.TEAM_TWO,
        )
        assertEquals(listOf("Ring of Fire vs Truck Stop"), savedSetupState.summaryLines())
        assertEquals(listOf(4), savedSetupState.selectedGames!!.map { it.index })
        assertTrue(savedSetupState.availableFilterValues.isEmpty())
        assertEquals("", savedSetupState.filterAndSortSummaryText)
        val savedLiveState = getFilteredArchiveState(
            archivedGames = archives,
            selectedCategory = ArchivedGameCategory.IN_PROGRESS,
            filterSelections = mixedSelections,
            sortMode = ArchiveSortMode.TEAM_TWO,
        )
        assertEquals(listOf("Drag'n Thrust 0 - 0 Mixtape"), savedLiveState.summaryLines())
        assertEquals(listOf(5), savedLiveState.selectedGames!!.map { it.index })
        val categoryLandingState = getFilteredArchiveState(
            archivedGames = archives,
            selectedCategory = null,
            filterSelections = mixedSelections,
            sortMode = ArchiveSortMode.TEAM_TWO,
        )
        assertNull(categoryLandingState.selectedGames)
        assertTrue(categoryLandingState.availableFilterValues.isEmpty())
        assertEquals("", categoryLandingState.filterAndSortSummaryText)
    }

    /**
     * Verify archive date filters handle presets, inclusive custom ranges,
     * open-ended bounds, and normalized reversed bounds.
     */
    @Test
    fun archiveDateFilterSelections() {
        // Build archives spread across the dates used by the date-range controls.
        val newYear = archiveForFilterTest(
            tournament = "New Year Kickoff",
            teamOne = "New Year",
            teamTwo = "Opponent",
            startDate = LocalDate.of(2026, 1, 1),
            startTime = LocalTime.of(9, 0),
        )
        val lastWeekEdge = archiveForFilterTest(
            tournament = "Weekly Warmup",
            teamOne = "Week Edge",
            teamTwo = "Opponent",
            startDate = LocalDate.of(2026, 5, 26),
            startTime = LocalTime.of(10, 0),
        )
        val lastThirtyEdge = archiveForFilterTest(
            tournament = "Monthly Warmup",
            teamOne = "Thirty Edge",
            teamTwo = "Opponent",
            startDate = LocalDate.of(2026, 5, 3),
            startTime = LocalTime.of(10, 0),
        )
        val todayGame = archiveForFilterTest(
            tournament = "Today Classic",
            teamOne = "Today",
            teamTwo = "Opponent",
            startDate = LocalDate.of(2026, 6, 2),
            startTime = LocalTime.of(11, 0),
        )
        val futureGame = archiveForFilterTest(
            tournament = "Tomorrow Classic",
            teamOne = "Tomorrow",
            teamTwo = "Opponent",
            startDate = LocalDate.of(2026, 6, 3),
            startTime = LocalTime.of(11, 0),
        )
        val olderGame = archiveForFilterTest(
            tournament = "Last Year Finale",
            teamOne = "Older",
            teamTwo = "Opponent",
            startDate = LocalDate.of(2025, 12, 31),
            startTime = LocalTime.of(13, 0),
        )
        val archives = listOf(
            newYear,
            lastWeekEdge,
            lastThirtyEdge,
            todayGame,
            futureGame,
            olderGame,
        )
        val today = LocalDate.of(2026, 6, 2)

        // Date preset labels and bounds match the quick-pick buttons in the UI.
        assertEquals(
            listOf("Today", "Last 7 days", "Last 30 days", "This year"),
            ArchiveDatePreset.entries.map { it.displayText },
        )
        assertEquals(today, ArchiveDatePreset.TODAY.startDate(today))
        assertEquals(today.minusDays(7), ArchiveDatePreset.LAST_7_DAYS.startDate(today))
        assertEquals(today.minusDays(30), ArchiveDatePreset.LAST_30_DAYS.startDate(today))
        assertEquals(LocalDate.of(2026, 1, 1), ArchiveDatePreset.THIS_YEAR.startDate(today))
        ArchiveDatePreset.entries.forEach { preset ->
            assertEquals(today, preset.endDate(today))
        }

        // A date filter must include at least one bound, and Date is active independently
        // from the checkbox-style fields.
        assertThrows(IllegalArgumentException::class.java) {
            ArchiveDateFilter(start = null, end = null)
        }
        val todaySelection = ArchiveFilterSelections(
            dateRange = ArchiveDateFilter(
                start = ArchiveDatePreset.TODAY.startDate(today),
                end = ArchiveDatePreset.TODAY.endDate(today),
            )
        )
        assertTrue(todaySelection.isActive())
        assertFalse(todaySelection.without(ArchiveFilterField.DATE).isActive())
        assertEquals(today, todaySelection.dateRange!!.start)
        assertEquals(today, todaySelection.dateRange.end)
        assertThrows(IllegalStateException::class.java) {
            todaySelection.withValues(ArchiveFilterField.DATE, emptySet())
        }
        assertThrows(IllegalStateException::class.java) {
            todaySelection.valuesFor(ArchiveFilterField.DATE)
        }

        // A same-day range keeps only games on that date and summarizes the exact range.
        val todayOnly = getFilteredArchiveState(
            archivedGames = archives,
            selectedCategory = ArchivedGameCategory.COMPLETED,
            filterSelections = ArchiveFilterSelections(
                dateRange = ArchiveDateFilter(
                    start = today,
                    end = today,
                ),
            ),
            sortMode = ArchiveSortMode.DATE_NEWEST,
        )
        assertEquals(listOf("Today 0 - 0 Opponent"), todayOnly.summaryLines())
        assertEquals(
            listOf(
                "Filters:",
                "    Date range: June 2, 2026 - June 2, 2026",
                "Sorted by date, newest first",
            ).joinToString("\n"),
            todayOnly.filterAndSortSummaryText,
        )

        // The other preset-equivalent ranges are inclusive of their start and end dates, while
        // excluding games after today.
        val lastSevenDays = getFilteredArchiveState(
            archivedGames = archives,
            selectedCategory = ArchivedGameCategory.COMPLETED,
            filterSelections = ArchiveFilterSelections(
                dateRange = ArchiveDateFilter(
                    start = ArchiveDatePreset.LAST_7_DAYS.startDate(today),
                    end = ArchiveDatePreset.LAST_7_DAYS.endDate(today),
                ),
            ),
            sortMode = ArchiveSortMode.DATE_OLDEST,
        )
        assertEquals(
            listOf("Week Edge 0 - 0 Opponent", "Today 0 - 0 Opponent"),
            lastSevenDays.summaryLines(),
        )
        val lastThirtyDays = getFilteredArchiveState(
            archivedGames = archives,
            selectedCategory = ArchivedGameCategory.COMPLETED,
            filterSelections = ArchiveFilterSelections(
                dateRange = ArchiveDateFilter(
                    start = ArchiveDatePreset.LAST_30_DAYS.startDate(today),
                    end = ArchiveDatePreset.LAST_30_DAYS.endDate(today),
                ),
            ),
            sortMode = ArchiveSortMode.DATE_OLDEST,
        )
        assertEquals(
            listOf(
                "Thirty Edge 0 - 0 Opponent",
                "Week Edge 0 - 0 Opponent",
                "Today 0 - 0 Opponent",
            ),
            lastThirtyDays.summaryLines(),
        )
        val thisYear = getFilteredArchiveState(
            archivedGames = archives,
            selectedCategory = ArchivedGameCategory.COMPLETED,
            filterSelections = ArchiveFilterSelections(
                dateRange = ArchiveDateFilter(
                    start = ArchiveDatePreset.THIS_YEAR.startDate(today),
                    end = ArchiveDatePreset.THIS_YEAR.endDate(today),
                ),
            ),
            sortMode = ArchiveSortMode.DATE_OLDEST,
        )
        assertEquals(
            listOf(
                "New Year 0 - 0 Opponent",
                "Thirty Edge 0 - 0 Opponent",
                "Week Edge 0 - 0 Opponent",
                "Today 0 - 0 Opponent",
            ),
            thisYear.summaryLines(),
        )

        // An end-only custom date keeps everything on or before the end date.
        val throughLastWeek = getFilteredArchiveState(
            archivedGames = archives,
            selectedCategory = ArchivedGameCategory.COMPLETED,
            filterSelections = ArchiveFilterSelections(
                dateRange = ArchiveDateFilter(
                    start = null,
                    end = LocalDate.of(2026, 5, 26),
                ),
            ),
            sortMode = ArchiveSortMode.DATE_OLDEST,
        )
        assertEquals(
            listOf(
                "Older 0 - 0 Opponent",
                "New Year 0 - 0 Opponent",
                "Thirty Edge 0 - 0 Opponent",
                "Week Edge 0 - 0 Opponent",
            ),
            throughLastWeek.summaryLines(),
        )
        assertEquals(
            listOf(
                "Filters:",
                "    Date range: on or before May 26, 2026",
                "Sorted by date, oldest first",
            ).joinToString("\n"),
            throughLastWeek.filterAndSortSummaryText,
        )

        // A start-only custom date keeps everything on or after the start date.
        val fromLastWeek = getFilteredArchiveState(
            archivedGames = archives,
            selectedCategory = ArchivedGameCategory.COMPLETED,
            filterSelections = ArchiveFilterSelections(
                dateRange = ArchiveDateFilter(
                    start = LocalDate.of(2026, 5, 26),
                    end = null,
                ),
            ),
            sortMode = ArchiveSortMode.DATE_OLDEST,
        )
        assertEquals(
            listOf(
                "Week Edge 0 - 0 Opponent",
                "Today 0 - 0 Opponent",
                "Tomorrow 0 - 0 Opponent",
            ),
            fromLastWeek.summaryLines(),
        )
        assertEquals(
            listOf(
                "Filters:",
                "    Date range: on or after May 26, 2026",
                "Sorted by date, oldest first",
            ).joinToString("\n"),
            fromLastWeek.filterAndSortSummaryText,
        )

        // Reversed custom dates are normalized to the same inclusive range.
        val reversedRange = getFilteredArchiveState(
            archivedGames = archives,
            selectedCategory = ArchivedGameCategory.COMPLETED,
            filterSelections = ArchiveFilterSelections(
                dateRange = ArchiveDateFilter(
                    start = LocalDate.of(2026, 6, 2),
                    end = LocalDate.of(2026, 5, 26),
                ),
            ),
            sortMode = ArchiveSortMode.DATE_OLDEST,
        )
        assertEquals(
            listOf("Week Edge 0 - 0 Opponent", "Today 0 - 0 Opponent"),
            reversedRange.summaryLines(),
        )

        // Active date filters narrow available string-filter values by date.
        assertEquals(
            listOf("Today Classic" to 1),
            todayOnly.valueCounts(ArchiveFilterField.TOURNAMENT),
        )
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
            listOf(
                "Date, newest first",
                "Date, oldest first",
                "First team",
                "Second team",
            ),
            ArchiveSortMode.entries.map { it.displayText },
        )
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
                sortMode = ArchiveSortMode.TEAM_ONE,
            ).summaryLines(),
        )
        assertEquals(
            listOf("Zeta 0 - 0 Beta", "Middle 0 - 0 Delta", "Alpha 0 - 0 Omega"),
            getFilteredArchiveState(
                archivedGames = archives,
                selectedCategory = ArchivedGameCategory.COMPLETED,
                filterSelections = ArchiveFilterSelections(),
                sortMode = ArchiveSortMode.TEAM_TWO,
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
        viewModel.updateArchiveSortMode(ArchiveSortMode.TEAM_TWO)
        assertEquals(setOf("Summer Solstice"), viewModel.archiveFilterSelections.tournaments)
        assertEquals(ArchiveSortMode.TEAM_TWO, viewModel.archiveSortMode)

        // ViewModel filter wrappers update and clear the same local archive filter state that
        // the filtered archive-list model reads.
        val dateFilter = ArchiveDateFilter(
            start = LocalDate.of(2026, 6, 1),
            end = LocalDate.of(2026, 6, 2),
        )
        viewModel.updateArchiveDateFilter(dateFilter)
        assertEquals(dateFilter, viewModel.archiveFilterSelections.dateRange)
        viewModel.clearArchiveFilter(ArchiveFilterField.DATE)
        assertNull(viewModel.archiveFilterSelections.dateRange)
        viewModel.updateArchiveDateFilter(dateFilter)
        viewModel.updateArchiveFilterSelections(ArchiveFilterField.DIVISION, setOf("Open"))
        val filteredState = viewModel.state.value.filteredArchiveState()
        assertTrue(filteredState.selectedGames!!.isEmpty())
        assertTrue(filteredState.availableFilterValues.containsKey(ArchiveFilterField.TOURNAMENT))
        assertTrue(filteredState.filterAndSortSummaryText.contains("Date range:"))
        viewModel.clearArchiveFilter(ArchiveFilterField.DIVISION)
        assertTrue(viewModel.archiveFilterSelections.divisions.isEmpty())
        viewModel.clearArchiveFilterSelections()
        assertFalse(viewModel.archiveFilterSelections.isActive())

        // Returning to the category landing page keeps the state for continued archive browsing.
        viewModel.updateArchiveFilterSelections(
            ArchiveFilterField.TOURNAMENT,
            setOf("Summer Solstice"),
        )
        viewModel.updateArchiveSortMode(ArchiveSortMode.TEAM_TWO)
        viewModel.returnToArchivedGameCategories()
        assertNull(viewModel.selectedArchiveCategory)
        assertEquals(setOf("Summer Solstice"), viewModel.archiveFilterSelections.tournaments)
        assertEquals(ArchiveSortMode.TEAM_TWO, viewModel.archiveSortMode)

        // Opening the archive section again starts with fresh filter/sort state.
        viewModel.goHome()
        viewModel.openArchivedGames()
        assertFalse(viewModel.archiveFilterSelections.isActive())
        assertEquals(ArchiveSortMode.DATE_NEWEST, viewModel.archiveSortMode)

        // Back from the archive category landing page leaves archive navigation.
        viewModel.goBackFromCurrentScreen()
        assertEquals(AppScreen.HOME, viewModel.screen)
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
        val finishedGame = viewModel.currentGame!!.copy(phase = GamePhase.GAME_OVER)
        viewModel.updateCurrentGame(finishedGame)
        viewModel.goHome()
        viewModel.archiveCompletedGame()
        assertNull(viewModel.currentGame)
        assertEquals(1, viewModel.archivedGames.size)
        val archivedGame = viewModel.archivedGames.single()
        assertEquals(GamePhase.GAME_OVER, archivedGame.phase)

        // Opening the archive should show the summary without leaving archive navigation.
        viewModel.openArchivedGame(0, now = 123_000L)
        assertEquals(AppScreen.ARCHIVED_GAMES, viewModel.screen)
        assertEquals(archivedGame, viewModel.viewingArchivedGame!!)
        assertEquals(archivedGame, viewModel.displayedGame)

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
        val currentGame = viewModel.currentGame!!.beginLivePoint(123_000L)
        viewModel.updateCurrentGame(currentGame)
        assertTrue(viewModel.state.value.viewingActiveGameScreen)
        viewModel.openArchivedGames()
        assertFalse(viewModel.state.value.viewingActiveGameScreen)
        viewModel.openArchivedGameCategory(ArchivedGameCategory.IN_PROGRESS)
        viewModel.openCurrentGameSummary()
        assertEquals(AppScreen.LIVE, viewModel.screen)
        assertTrue(viewModel.viewingCurrentGameSummary)
        assertFalse(viewModel.state.value.viewingActiveGameScreen)
        assertTrue(viewModel.archivedGames.isEmpty())
        assertEquals(currentGame, viewModel.displayedGame)

        // Back returns to the in-progress archive list, while explicit resume returns to live play.
        viewModel.goBackFromCurrentScreen()
        assertEquals(AppScreen.ARCHIVED_GAMES, viewModel.screen)
        assertEquals(ArchivedGameCategory.IN_PROGRESS, viewModel.selectedArchiveCategory)
        assertFalse(viewModel.viewingCurrentGameSummary)
        viewModel.openCurrentGameSummary()
        viewModel.resumeCurrentGame()
        assertEquals(AppScreen.LIVE, viewModel.screen)
        assertFalse(viewModel.viewingCurrentGameSummary)
        assertTrue(viewModel.state.value.viewingActiveGameScreen)

        // When the same summary opens from live-game navigation, Back returns to live play.
        viewModel.openCurrentGameSummary()
        viewModel.goBackFromCurrentScreen()
        assertEquals(AppScreen.LIVE, viewModel.screen)
        assertFalse(viewModel.viewingCurrentGameSummary)

        // A completed current game opens the normal current-game summary.
        val completedCurrentGame = currentGame.copy(phase = GamePhase.GAME_OVER)
        viewModel.updateCurrentGame(completedCurrentGame)
        viewModel.openArchivedGames()
        viewModel.openArchivedGameCategory(ArchivedGameCategory.IN_PROGRESS)
        viewModel.openCurrentGameSummary()
        assertEquals(AppScreen.LIVE, viewModel.screen)
        assertFalse(viewModel.viewingCurrentGameSummary)
        assertEquals(GamePhase.GAME_OVER, viewModel.currentGame!!.phase)
        assertFalse(viewModel.state.value.viewingActiveGameScreen)
        assertEquals(completedCurrentGame, viewModel.displayedGame)

        // A stale UI callback should not leave archive navigation when no current game exists.
        viewModel.deleteCurrentGame()
        viewModel.openArchivedGames()
        viewModel.openArchivedGameCategory(ArchivedGameCategory.IN_PROGRESS)
        viewModel.openCurrentGameSummary()
        assertEquals(AppScreen.ARCHIVED_GAMES, viewModel.screen)
        assertEquals(ArchivedGameCategory.IN_PROGRESS, viewModel.selectedArchiveCategory)
        assertFalse(viewModel.viewingCurrentGameSummary)
        assertNull(viewModel.displayedGame)
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
        val archivedGame = viewModel.currentGame!!.copy(phase = GamePhase.GAME_OVER)
        viewModel.updateCurrentGame(archivedGame)
        viewModel.archiveCompletedGame()
        val archivedState = viewModel.archivedGames.single()

        // Create a separate current preview that will be saved during restore.
        viewModel.startNewGame(now = 123_000L)
        viewModel.finishSetup(now = 123_000L)
        val previewState = viewModel.currentGame!!
        assertFalse(previewState.hasStarted())

        // Restoring the archive promotes it and saves the previous current preview.
        viewModel.openArchivedGame(0, now = 123_000L)
        viewModel.restoreCompletedGame()
        assertEquals(AppScreen.LIVE, viewModel.screen)
        assertFalse(viewModel.viewingCurrentGameSummary)
        assertNull(viewModel.viewingArchivedGame)
        assertEquals(archivedState, viewModel.currentGame)
        assertEquals(1, viewModel.archivedGames.size)
        assertEquals(
            ArchivedGameCategory.IN_PROGRESS,
            viewModel.archivedGames.single().archiveCategory,
        )
        assertEquals(previewState, viewModel.archivedGames.single())
    }

    /**
     * Verify setup drafts can be saved for later, edited in place, explicitly promoted
     * to current, and used as the source for repeated tournament game-information defaults.
     */
    @Test
    fun savedSetupLifecycle() {
        // Saving a setup draft for later should move it to the archive in the SETUP category.
        val viewModel = AppViewModel(NoOpAppStateStorage)
        viewModel.startNewGame(now = 123_000L)
        val tournamentRules = GameRules(gameTo = 11, hasFloaterTimeout = true)
        val savedSetup = viewModel.setupGame.copy(
            tournamentName = "Summer Solstice",
            division = GameDivision.OPEN,
            level = "Club",
            gameContext = "Pool play",
            observerNames = listOf("Mike"),
            fieldName = "Field 7",
            rules = tournamentRules,
            teamOne = TeamState("", TeamColorChoice.GREEN),
            teamTwo = TeamState("Known Opponent", TeamColorChoice.YELLOW),
        )
        viewModel.updateSetup(savedSetup)
        viewModel.saveSetupForLater()
        assertEquals(AppScreen.HOME, viewModel.screen)
        assertFalse(viewModel.hasSetupDraft)
        assertNull(viewModel.currentGame)
        assertEquals(1, viewModel.archivedGames.size)
        assertEquals(ArchivedGameCategory.SETUP, viewModel.archivedGames.single().archiveCategory)
        assertEquals(GamePhase.SETUP, viewModel.archivedGames.single().phase)
        assertEquals(savedSetup, viewModel.archivedGames.single())
        assertEquals(
            "Team 1 vs Known Opponent on Field 7",
            viewModel.archivedGames.single().gameListSummaryLine(),
        )
        assertEquals("Summer Solstice", viewModel.archivedGames.single().gameListEntry().headerDetail)
        assertEquals(
            "Team 1 vs Known Opponent on Field 7",
            viewModel.archivedGames.single().gameListEntry().summaryLine,
        )

        // Saved setup rows are ordered by scheduled time and show field detail in the wrapping
        // row text, leaving the single-line header for tournament detail.
        val laterSetup = savedSetup.copy(
            startTime = LocalTime.of(12, 0),
            fieldName = "12",
            teamOne = TeamState("Later", TeamColorChoice.GREEN),
        )
        val textFieldSetup = savedSetup.copy(
            startTime = LocalTime.of(13, 0),
            fieldName = "football field",
            teamOne = TeamState("Text", TeamColorChoice.GREEN),
        )
        val earlierSetup = savedSetup.copy(
            startTime = LocalTime.of(9, 0),
            fieldName = "",
            teamOne = TeamState("Earlier", TeamColorChoice.GREEN),
        )
        val setupArchiveState = getFilteredArchiveState(
            archivedGames = listOf(laterSetup, textFieldSetup, earlierSetup),
            selectedCategory = ArchivedGameCategory.SETUP,
            filterSelections = ArchiveFilterSelections(),
            sortMode = ArchiveSortMode.DATE_NEWEST,
        )
        assertEquals(
            listOf(
                "Earlier vs Known Opponent",
                "Later vs Known Opponent on field 12",
                "Text vs Known Opponent on football field",
            ),
            setupArchiveState.summaryLines(),
        )
        val setupArchiveRows = setupArchiveState.selectedGames!!
        assertEquals("Summer Solstice", setupArchiveRows[0].entry.headerDetail)
        assertEquals("Summer Solstice", setupArchiveRows[1].entry.headerDetail)
        assertEquals("Summer Solstice", setupArchiveRows[2].entry.headerDetail)

        // Opening a saved setup edits the archived row directly rather than making it current.
        viewModel.openArchivedGames()
        viewModel.openArchivedGameCategory(ArchivedGameCategory.SETUP)
        viewModel.openArchivedGame(0, now = 123_000L)
        assertEquals(AppScreen.SETUP, viewModel.screen)
        assertEquals(SetupMode.EDIT_SAVED_SETUP, viewModel.setupMode)
        assertFalse(viewModel.hasSetupDraft)
        assertEquals(savedSetup, viewModel.setupGame)
        assertEquals(savedSetup, viewModel.archivedGames.single())
        val editedSavedSetup = savedSetup.copy(
            fieldName = "Field 8",
            teamTwo = TeamState("Edited Opponent", TeamColorChoice.YELLOW),
        )
        viewModel.updateSetup(editedSavedSetup)
        assertEquals(editedSavedSetup, viewModel.archivedGames.single())
        viewModel.goBackFromCurrentScreen()
        assertEquals(AppScreen.ARCHIVED_GAMES, viewModel.screen)
        assertEquals(ArchivedGameCategory.SETUP, viewModel.selectedArchiveCategory)
        assertNull(viewModel.currentGame)

        // Starting another game carries forward tournament, division, level, and rules, but not
        // other fields that typically change each game.
        viewModel.startNewGame(now = 123_000L)
        assertEquals("Summer Solstice", viewModel.setupGame.tournamentName)
        assertEquals(GameDivision.OPEN, viewModel.setupGame.division)
        assertEquals("Club", viewModel.setupGame.level)
        assertEquals(tournamentRules, viewModel.setupGame.rules)
        assertEquals("", viewModel.setupGame.gameContext)
        assertEquals(emptyList<String>(), viewModel.setupGame.observerNames)
        assertEquals("", viewModel.setupGame.fieldName)
        assertEquals("", viewModel.setupGame.teamOne.name)
        assertEquals("", viewModel.setupGame.teamTwo.name)

        // Making a saved setup current saves the previous current setup draft aside.
        val unsavedDraft = viewModel.setupGame.copy(
            teamOne = TeamState("Unsaved", TeamColorChoice.WHITE),
        )
        viewModel.updateSetup(unsavedDraft)
        viewModel.openArchivedGames()
        viewModel.openArchivedGameCategory(ArchivedGameCategory.SETUP)
        viewModel.openArchivedGame(0, now = 123_000L)
        viewModel.makeEditedSetupCurrent()
        assertEquals(AppScreen.SETUP, viewModel.screen)
        assertTrue(viewModel.hasSetupDraft)
        assertEquals(GamePhase.SETUP, viewModel.currentGame?.phase)
        assertEquals(editedSavedSetup, viewModel.setupGame)
        assertEquals(SetupMode.NEW_GAME, viewModel.setupMode)
        assertEquals(1, viewModel.archivedGames.size)
        assertEquals(ArchivedGameCategory.SETUP, viewModel.archivedGames.single().archiveCategory)
        assertEquals(unsavedDraft, viewModel.archivedGames.single())

        // Making a saved setup current while a real current game exists archives that current game,
        // putting it in the IN_PROGRESS category in the archive.
        viewModel.saveSetupForLater()
        viewModel.startNewGame(now = 123_000L)
        viewModel.updateSetup(
            viewModel.setupGame.copy(
                teamOne = TeamState("Current", TeamColorChoice.WHITE),
                teamTwo = TeamState("Live", TeamColorChoice.BLUE),
            )
        )
        viewModel.finishSetup(now = 123_000L)
        assertEquals("Current 0 - 0 Live", viewModel.currentGame!!.gameListSummaryLine())
        viewModel.updateCurrentGame(viewModel.currentGame!!.beginLivePoint())
        viewModel.openArchivedGames()
        viewModel.openArchivedGameCategory(ArchivedGameCategory.SETUP)
        viewModel.openArchivedGame(1, now = 123_000L)
        viewModel.makeEditedSetupCurrent()
        assertEquals(editedSavedSetup, viewModel.setupGame)
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
        viewModel.updateCurrentGame(
            viewModel.currentGame!!.copy(
                phase = GamePhase.GAME_OVER,
                teamOne = viewModel.currentGame!!.teamOne.copy(name = "Completed first"),
            )
        )
        viewModel.archiveCompletedGame()
        val existingCompletedArchive = viewModel.archivedGames.single()

        viewModel.startNewGame(now = 123_000L)
        viewModel.finishSetup(now = 123_000L)
        viewModel.updateCurrentGame(viewModel.currentGame!!.beginLivePoint())
        val savedLiveState = viewModel.currentGame!!
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
        viewModel.restoreCompletedGame()
        assertEquals(GamePhase.GAME_OVER, viewModel.currentGame!!.phase)
        assertEquals(
            savedLiveState.pruneUndoHistory(clearCountdown = false),
            viewModel.currentGame!!.undoLastAction().copy(redoEntry = null),
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
        val completedGame = viewModel.currentGame!!.copy(phase = GamePhase.GAME_OVER)
        viewModel.updateCurrentGame(completedGame)
        assertNull(viewModel.currentGameHomeSubtitle)
        viewModel.goHome()
        viewModel.openCompletedGame()
        assertEquals(AppScreen.LIVE, viewModel.screen)
        assertEquals(completedGame, viewModel.displayedGame)
        assertFalse(viewModel.viewingCurrentGameSummary)
        assertNull(viewModel.viewingArchivedGame)

        // Archiving the completed current game clears the current slot.
        viewModel.goHome()
        viewModel.archiveCompletedGame()
        assertNull(viewModel.currentGame)
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
        val initialLiveState = viewModel.currentGame!!
        val beforeEndGame = initialLiveState.copy(
            undoEntry = UndoEntry("Undo Start point", initialLiveState),
        )
        val completedGame = beforeEndGame.copy(
            phase = GamePhase.GAME_OVER,
            undoEntry = UndoEntry("Undo End game", beforeEndGame),
            redoEntry = beforeEndGame,
        )
        viewModel.updateCurrentGame(completedGame)
        viewModel.archiveCompletedGame()
        val archivedState = viewModel.archivedGames.single()
        val prunedBeforeEndGame = beforeEndGame.pruneUndoHistory()
        assertEquals("Undo End game", archivedState.undoEntry?.label)
        assertEquals(prunedBeforeEndGame, archivedState.undoEntry!!.previous)
        assertNull(archivedState.redoEntry)

        // Restoring the archive keeps the end-game undo while older undo entries stay pruned.
        viewModel.openArchivedGame(0, now = 123_000L)
        viewModel.restoreCompletedGame()
        val restoredGame = viewModel.currentGame!!
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
        viewModel.restoreCompletedGame()
        assertEquals(AppScreen.HOME, viewModel.screen)
        assertNull(viewModel.currentGame)
        assertTrue(viewModel.archivedGames.isEmpty())

        // Restoring from the full archive list removes the selected archive and promotes it
        // to current game.
        viewModel.startNewGame(now = 123_000L)
        viewModel.finishSetup(now = 123_000L)
        viewModel.updateCurrentGame(
            viewModel.currentGame!!.copy(
                phase = GamePhase.GAME_OVER,
                teamOne = TeamState("First Archive", TeamColorChoice.WHITE),
            )
        )
        viewModel.archiveCompletedGame()

        // Add a second archive so a valid restore can prove it removes only the selected game.
        viewModel.startNewGame(now = 123_000L)
        viewModel.finishSetup(now = 123_000L)
        viewModel.updateCurrentGame(
            viewModel.currentGame!!.copy(
                phase = GamePhase.GAME_OVER,
                teamOne = TeamState("Second Archive", TeamColorChoice.WHITE),
            )
        )
        viewModel.archiveCompletedGame()

        // Archive indexes identify stored rows directly, regardless of the selected category.
        viewModel.openArchivedGames()
        viewModel.openArchivedGameCategory(ArchivedGameCategory.SETUP)
        viewModel.openArchivedGame(1, now = 123_000L)
        viewModel.restoreCompletedGame()
        assertEquals(AppScreen.LIVE, viewModel.screen)
        assertFalse(viewModel.viewingCurrentGameSummary)
        assertNull(viewModel.viewingArchivedGame)
        assertEquals("Second Archive", viewModel.currentGame!!.teamOne.name)
        assertEquals(1, viewModel.archivedGames.size)
        assertEquals("First Archive", viewModel.archivedGames.single().teamOne.name)
    }

    /**
     * Verify deleting the current game, one archived game, and all archived games clears
     * the corresponding ViewModel state.
     */
    @Test
    fun gameDeletion() {
        // Deleting the current game clears current and displayed game state.
        val viewModel = AppViewModel(NoOpAppStateStorage)
        viewModel.startNewGame(now = 123_000L)
        viewModel.finishSetup(now = 123_000L)
        val currentGame = viewModel.currentGame!!
        viewModel.deleteCurrentGame()
        assertEquals(AppScreen.HOME, viewModel.screen)
        assertNull(viewModel.currentGame)
        assertNull(viewModel.displayedGame)

        // Deleting the current game from an archive category keeps that category open.
        viewModel.updateCurrentGame(currentGame.beginLivePoint())
        viewModel.openArchivedGames()
        viewModel.openArchivedGameCategory(ArchivedGameCategory.IN_PROGRESS)
        viewModel.deleteCurrentGame()
        assertEquals(AppScreen.ARCHIVED_GAMES, viewModel.screen)
        assertEquals(ArchivedGameCategory.IN_PROGRESS, viewModel.selectedArchiveCategory)
        assertNull(viewModel.currentGame)
        assertNull(viewModel.displayedGame)

        // Deleting a viewed archived game clears the selection.
        viewModel.updateCurrentGame(currentGame.copy(phase = GamePhase.GAME_OVER))
        viewModel.archiveCompletedGame()
        assertEquals(1, viewModel.archivedGames.size)
        viewModel.openArchivedGame(0, now = 123_000L)
        assertNotNull(viewModel.viewingArchivedGame)
        viewModel.deleteArchivedGame(0)
        assertTrue(viewModel.archivedGames.isEmpty())
        assertNull(viewModel.displayedGame)

        // Category bulk delete clears only the selected category.
        viewModel.updateCurrentGame(currentGame.beginLivePoint())
        viewModel.startNewGame(now = 123_000L)
        assertEquals(1, viewModel.archivedGames.size)
        assertEquals(
            ArchivedGameCategory.IN_PROGRESS,
            viewModel.archivedGames.single().archiveCategory,
        )
        viewModel.updateCurrentGame(currentGame.copy(phase = GamePhase.GAME_OVER))
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
            viewModel.updateCurrentGame(
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
        viewModel.updateCurrentGame(currentGame.copy(phase = GamePhase.GAME_OVER))
        viewModel.archiveCompletedGame()
        viewModel.updateCurrentGame(
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
        assertNull(viewModel.displayedGame)
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
        val beforeUndoAction = viewModel.currentGame!!
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
        viewModel.updateCurrentGame(completedGame)
        viewModel.startNewGame(now = 123_000L)
        assertEquals(AppScreen.SETUP, viewModel.screen)
        assertEquals(GamePhase.SETUP, viewModel.currentGame?.phase)
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
        val setup = viewModel.setupGame.copy(
            teamOne = TeamState("Viscous Coupling", TeamColorChoice.BLUE),
            teamTwo = TeamState("Animal", TeamColorChoice.PINK),
        )
        viewModel.updateSetup(setup)
        viewModel.finishSetup(now = 123_000L)
        val activeGame = viewModel.currentGame!!.beginLivePoint()
        assertNotNull(activeGame.undoEntry)
        viewModel.updateCurrentGame(activeGame)

        // Starting a new game saves the current game without ending it or pruning undo.
        viewModel.startNewGame(now = 123_000L)
        assertEquals(AppScreen.SETUP, viewModel.screen)
        assertTrue(viewModel.hasSetupDraft)
        assertEquals(GamePhase.SETUP, viewModel.currentGame?.phase)
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
        val replacementSetup = restoredViewModel.setupGame.copy(
            teamOne = TeamState("Replacement Current", TeamColorChoice.WHITE),
            teamTwo = TeamState("Replacement Opponent", TeamColorChoice.BLUE),
        )
        restoredViewModel.updateSetup(replacementSetup)
        restoredViewModel.finishSetup(now = 123_000L)
        val replacementCurrent = restoredViewModel.currentGame!!.beginLivePoint()
        restoredViewModel.updateCurrentGame(replacementCurrent)

        // Restoring the archive should save the replaced current game.
        restoredViewModel.openArchivedGame(0, now = 123_000L)
        restoredViewModel.restoreCompletedGame()
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
        assertEquals(activeGame, restoredViewModel.currentGame)
        assertEquals(GamePhase.LIVE_POINT, restoredViewModel.currentGame!!.phase)
        assertEquals(setup.teamOne.name, restoredViewModel.setupGame.teamOne.name)
        assertEquals(setup.teamTwo.name, restoredViewModel.setupGame.teamTwo.name)
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
            nominalSoftCapMinutes = 75,
            useHardCap = true,
            nominalHardCapMinutes = 95,
            timeoutsPerHalf = 1,
            hasFloaterTimeout = true,
        )
        viewModel.updateSetup(viewModel.setupGame.copy(rules = tournamentRules))
        viewModel.finishSetup(now = 123_000L)
        viewModel.updateCurrentGame(viewModel.currentGame!!.copy(phase = GamePhase.GAME_OVER))
        viewModel.archiveCompletedGame()
        viewModel.startNewGame(now = 123_000L)
        assertEquals(tournamentRules, viewModel.setupGame.rules)
        assertEquals(GamePhase.SETUP, viewModel.currentGame?.phase)
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
        val currentRules = GameRules(gameTo = 11, nominalHardCapMinutes = 80, hasFloaterTimeout = true)
        viewModel.updateSetup(viewModel.setupGame.copy(rules = currentRules))
        viewModel.finishSetup(now = 123_000L)
        viewModel.updateCurrentGame(viewModel.currentGame!!.beginLivePoint())
        val savedState = viewModel.currentGame!!
        viewModel.startNewGame(now = 123_000L)
        assertEquals(currentRules, viewModel.setupGame.rules)
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
     * @param observerNames Observer names for the archive.
     * @param startDate Local start date for the archive.
     * @param startTime Local start time for the archive.
     */
    private fun archiveForFilterTest(
        tournament: String = "",
        division: GameDivision? = null,
        level: String = "",
        teamOne: String,
        teamTwo: String,
        observerNames: List<String> = emptyList(),
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
            observerNames = observerNames,
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
