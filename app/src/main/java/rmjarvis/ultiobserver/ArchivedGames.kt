package rmjarvis.ultiobserver

import java.time.LocalDate
import kotlinx.serialization.Serializable

/**
 * Category describing why a game is stored outside the current-game slot.
 *
 * @param displayText User-facing category label.
 * @param emptyText User-facing empty-state text for the category list.
 */
@Serializable
internal enum class ArchivedGameCategory(
    val displayText: String,
    val emptyText: String,
) {
    COMPLETED("Archived games", "No archived games yet."),
    IN_PROGRESS("In-progress games", "No in-progress games."),
    SETUP("Saved setup drafts", "No saved setup drafts."),
}

/// Text used for missing filter values.
internal const val ARCHIVE_FILTER_NA = "N/A"

/// Field available in archive filters.
internal enum class ArchiveFilterField(val displayText: String) {
    TOURNAMENT("Tournament"),
    DIVISION("Division"),
    LEVEL("Level"),
    TEAM("Team"),
    DATE("Date"),
    OBSERVERS("Observers"),
}

/// Convenient date range choices for archive filtering.
internal enum class ArchiveDatePreset(val displayText: String) {
    TODAY("Today"),
    LAST_7_DAYS("Last 7 days"),
    LAST_30_DAYS("Last 30 days"),
    THIS_YEAR("This year");

    /**
     * Return the first date included by this preset.
     *
     * @param today Date used to resolve this preset.
     */
    fun startDate(today: LocalDate): LocalDate {
        return when (this) {
            ArchiveDatePreset.TODAY -> today
            ArchiveDatePreset.LAST_7_DAYS -> today.minusDays(7)
            ArchiveDatePreset.LAST_30_DAYS -> today.minusDays(30)
            ArchiveDatePreset.THIS_YEAR -> LocalDate.of(today.year, 1, 1)
        }
    }

    /**
     * Return the last date included by this preset.
     *
     * @param today Date used to resolve this preset.
     */
    fun endDate(today: LocalDate): LocalDate {
        return today
    }
}

/**
 * Explicit inclusive date bounds for archive filtering.
 *
 * @param start The first date to include, or null for no lower bound.
 * @param end The last date to include, or null for no upper bound.
 */
internal data class ArchiveDateFilter(
    val start: LocalDate?,
    val end: LocalDate?,
) {
    init {
        require(start != null || end != null)
    }

    /// Return the first date included by this filter.
    fun startDate(): LocalDate? {
        return if (start != null && end != null) {
            minOf(start, end)
        } else {
            start
        }
    }

    /// Return the last date included by this filter.
    fun endDate(): LocalDate? {
        return if (start != null && end != null) {
            maxOf(start, end)
        } else {
            end
        }
    }
}

/**
 * Selected archive filter values.
 *
 * @param tournaments Tournament names to keep.
 * @param divisions Division labels to keep.
 * @param levels Level labels to keep.
 * @param teams Team names to keep, matched against either team.
 * @param observers Observer strings to keep.
 * @param dateRange Date range to keep.
 */
internal data class ArchiveFilterSelections(
    val tournaments: Set<String> = emptySet(),
    val divisions: Set<String> = emptySet(),
    val levels: Set<String> = emptySet(),
    val teams: Set<String> = emptySet(),
    val observers: Set<String> = emptySet(),
    val dateRange: ArchiveDateFilter? = null,
) {
    /// Report whether any filter is active.
    fun isActive(): Boolean {
        return archiveFilterCriteria.any { it.selectedValues(this).isNotEmpty() } ||
            dateRange != null
    }

    /// Return the selected values for one checkbox-style field.
    fun valuesFor(field: ArchiveFilterField): Set<String> {
        return archiveFilterCriterion(field).selectedValues(this)
    }

    /**
     * Return filters with one checkbox-style field replaced.
     *
     * @param field The field to replace.
     * @param values The selected values for that field.
     */
    fun withValues(field: ArchiveFilterField, values: Set<String>): ArchiveFilterSelections {
        return archiveFilterCriterion(field).replaceValues(this, values)
    }

    /// Return filters with one field cleared.
    fun without(field: ArchiveFilterField): ArchiveFilterSelections {
        return if (field == ArchiveFilterField.DATE) {
            copy(dateRange = null)
        } else {
            withValues(field, emptySet())
        }
    }
}

/**
 * Return the checkbox-style criterion for a field, failing for anything that isn't in our
 * archiveFilterCriteria helper.  Specifically, we expect this to fail if run on
 * non-checkbox filters such as DATE.
 */
private fun archiveFilterCriterion(field: ArchiveFilterField): ArchiveFilterCriterion {
    return archiveFilterCriteria.firstOrNull { it.field == field }
        ?: error("${field.displayText} is not a checkbox-style archive filter.")
}

private data class ArchiveFilterSummaryLine(
    val label: String,
    val value: String,
)

/// Return user-facing text summarizing archive filter and sort selections.
internal fun filterAndSortSummaryText(
    filterSelections: ArchiveFilterSelections,
    sortMode: ArchiveSortMode,
): String {
    return buildList {
        val filterSummaryLines = filterSelections.filterSummaryLines()
        if (filterSummaryLines.isNotEmpty()) {
            add("Filters:")
            filterSummaryLines.forEach { line ->
                add("    ${line.label}: ${line.value}")
            }
        }
        add(sortMode.summaryText)
    }.joinToString("\n")
}

private fun ArchiveFilterSelections.filterSummaryLines(): List<ArchiveFilterSummaryLine> {
    return buildList {
        addSelectedValues("Tournament", "Tournaments", tournaments)
        addSelectedValues("Division", "Divisions", divisions)
        addSelectedValues("Level", "Levels", levels)
        addSelectedValues("Team", "Teams", teams)
        addSelectedValues("Observer", "Observers", observers)
        dateRange?.let { dateFilter ->
            add(
                ArchiveFilterSummaryLine(
                    label = "Date range",
                    value = dateFilter.summaryText(),
                )
            )
        }
    }
}

private fun MutableList<ArchiveFilterSummaryLine>.addSelectedValues(
    singularLabel: String,
    pluralLabel: String,
    values: Set<String>,
) {
    if (values.isEmpty()) {
        return
    }
    add(
        ArchiveFilterSummaryLine(
            label = if (values.size == 1) singularLabel else pluralLabel,
            value = values.archiveFilterSummaryText(),
        )
    )
}

private fun Set<String>.archiveFilterSummaryText(): String {
    return sortedWith(
        compareBy<String> { it == ARCHIVE_FILTER_NA }
            .then(String.CASE_INSENSITIVE_ORDER)
    ).joinToString(", ")
}

private fun ArchiveDateFilter.summaryText(): String {
    val start = startDate()
    val end = endDate()
    return when {
        start == null -> "on or before ${formatStartDate(end!!)}"
        end == null -> "on or after ${formatStartDate(start)}"
        else -> "${formatStartDate(start)} - ${formatStartDate(end)}"
    }
}

/**
 * Behavior for one checkbox-style archive filter field.
 *
 * @param field Field represented by this criterion.
 * @param selectedValues Return the current selected values for this field.
 * @param replaceValues Return filter selections with this field's values replaced.
 * @param valuesForGame Return this field's candidate values for one archived game.
 */
private data class ArchiveFilterCriterion(
    val field: ArchiveFilterField,
    val selectedValues: (ArchiveFilterSelections) -> Set<String>,
    val replaceValues: (ArchiveFilterSelections, Set<String>) -> ArchiveFilterSelections,
    val valuesForGame: (GameState) -> List<String>,
) {
    /**
     * Report whether one archived game matches this criterion.
     *
     * @param state Archived game state being checked.
     * @param filterSelections Active archive filter selections.
     * @param ignoredField Field to ignore while building available values.
     */
    fun matches(
        state: GameState,
        filterSelections: ArchiveFilterSelections,
        ignoredField: ArchiveFilterField?,
    ): Boolean {
        val selected = selectedValues(filterSelections)
        return ignoredField == field ||
            selected.isEmpty() ||
            valuesForGame(state).any { it in selected }
    }
}

private val archiveFilterCriteria = listOf(
    ArchiveFilterCriterion(
        field = ArchiveFilterField.TOURNAMENT,
        selectedValues = { it.tournaments },
        replaceValues = { selections, values -> selections.copy(tournaments = values) },
        valuesForGame = { listOf(it.archiveTournamentFilterValue()) },
    ),
    ArchiveFilterCriterion(
        field = ArchiveFilterField.DIVISION,
        selectedValues = { it.divisions },
        replaceValues = { selections, values -> selections.copy(divisions = values) },
        valuesForGame = { listOf(it.archiveDivisionFilterValue()) },
    ),
    ArchiveFilterCriterion(
        field = ArchiveFilterField.LEVEL,
        selectedValues = { it.levels },
        replaceValues = { selections, values -> selections.copy(levels = values) },
        valuesForGame = { listOf(it.archiveLevelFilterValue()) },
    ),
    ArchiveFilterCriterion(
        field = ArchiveFilterField.TEAM,
        selectedValues = { it.teams },
        replaceValues = { selections, values -> selections.copy(teams = values) },
        valuesForGame = { it.archiveTeamFilterValues() },
    ),
    ArchiveFilterCriterion(
        field = ArchiveFilterField.OBSERVERS,
        selectedValues = { it.observers },
        replaceValues = { selections, values -> selections.copy(observers = values) },
        valuesForGame = { it.archiveObserverFilterValues() },
    ),
)

/**
 * Archive sort order.
 *
 * @param displayText Compact label for choosing this sort mode.
 * @param summaryText Sentence-style label describing this sort mode on the archive page.
 */
internal enum class ArchiveSortMode(
    val displayText: String,
    val summaryText: String,
) {
    DATE_NEWEST("Date, newest first", "Sorted by date, newest first"),
    DATE_OLDEST("Date, oldest first", "Sorted by date, oldest first"),
    TEAM_ONE("First team", "Sorted by first team"),
    TEAM_TWO("Second team", "Sorted by second team"),
}

/**
 * One archive list row with its stable archive storage index.
 *
 * @param index The row's index in full archived-game storage.
 * @param entry The list entry to display.
 */
internal data class ArchivedGameListItem(
    val index: Int,
    val entry: GameListEntry,
)

/**
 * One available checkbox value for an archive filter option.
 *
 * @param value The filter value to display and select.
 * @param gameCount Number of games matching this value after other active filters.
 */
internal data class ArchiveFilterValueOption(
    val value: String,
    val gameCount: Int,
)

/**
 * Filtered archive rows and filter choices for the currently selected category.
 *
 * @param selectedGames Ordered archive rows to display, or null on the category landing page.
 * @param availableFilterValues Filter dialog values after applying any other selected filters.
 * @param filterAndSortSummaryText User-facing filter/sort summary text.
 */
internal data class FilteredArchiveState(
    val selectedGames: List<ArchivedGameListItem>?,
    val availableFilterValues: Map<ArchiveFilterField, List<ArchiveFilterValueOption>>,
    val filterAndSortSummaryText: String,
)

/// Return the archive category implied by this game phase.
internal val GameState.archiveCategory: ArchivedGameCategory
    get() = when (phase) {
        GamePhase.SETUP -> ArchivedGameCategory.SETUP
        GamePhase.GAME_OVER -> ArchivedGameCategory.COMPLETED
        else -> ArchivedGameCategory.IN_PROGRESS
    }

/**
 * Return this in-progress archive converted to a completed archive.
 *
 * @param now The epoch millis to store as the manual game-over time.
 */
internal fun GameState.asCompletedArchive(now: Long): GameState {
    return endGameNow(now).pruneUndoHistory()
}

/// Return archive list rows for one selected category.
private fun List<GameState>.archiveListItems(
    category: ArchivedGameCategory,
): List<ArchivedGameListItem> {
    val allGames = this
    val matchingGames = mapIndexedNotNull { index, game ->
        if (game.archiveCategory == category) {
            ArchivedGameListItem(
                index = index,
                entry = game.gameListEntry(),
            )
        } else {
            null
        }
    }
    return if (category == ArchivedGameCategory.SETUP) {
        matchingGames.sortedWith(
            compareBy<ArchivedGameListItem> { allGames[it.index].startEpoch }
                .thenBy { it.index }
        )
    } else {
        matchingGames
    }
}

/**
 * Return filtered archive rows and filter choices for the selected category.
 *
 * @param archivedGames All archived games in storage order.
 * @param selectedCategory The archive category currently selected.
 * @param filterSelections Active archive filter selections.
 * @param sortMode Sort order for matching games.
 */
internal fun getFilteredArchiveState(
    archivedGames: List<GameState>,
    selectedCategory: ArchivedGameCategory?,
    filterSelections: ArchiveFilterSelections,
    sortMode: ArchiveSortMode,
): FilteredArchiveState {
    if (selectedCategory != ArchivedGameCategory.COMPLETED) {
        return FilteredArchiveState(
            selectedGames = selectedCategory?.let { archivedGames.archiveListItems(it) },
            availableFilterValues = emptyMap(),
            filterAndSortSummaryText = "",
        )
    }
    val archiveRows = archivedGames.withIndex()
        .filter { it.value.archiveCategory == ArchivedGameCategory.COMPLETED }
    val matchingRows = archiveRows
        .filter { it.matches(filterSelections, ignoredField = null) }
        .sortedWith(sortMode.comparator())
    val availableValues = archiveFilterCriteria.associate { criterion ->
        criterion.field to archiveRows
            .filter { it.matches(filterSelections, ignoredField = criterion.field) }
            .valueOptionsFor(criterion)
    }
    return FilteredArchiveState(
        selectedGames = matchingRows.map { row ->
            ArchivedGameListItem(
                index = row.index,
                entry = row.value.gameListEntry(),
            )
        },
        availableFilterValues = availableValues,
        filterAndSortSummaryText = filterAndSortSummaryText(filterSelections, sortMode),
    )
}

private fun IndexedValue<GameState>.matches(
    filterSelections: ArchiveFilterSelections,
    ignoredField: ArchiveFilterField?,
): Boolean {
    val state = value
    return archiveFilterCriteria.all { criterion ->
        criterion.matches(state, filterSelections, ignoredField)
    } && (
        filterSelections.dateRange == null ||
            filterSelections.dateRange.includes(state.startDate)
        )
}

private fun ArchiveDateFilter.includes(date: LocalDate): Boolean {
    val start = startDate()
    val end = endDate()
    return (start == null || !date.isBefore(start)) &&
        (end == null || !date.isAfter(end))
}

/**
 * Determine the number of matching games for each possible value in a given filter criterion.
 *
 * Returns a list of ArchiveFilterValueOption, which holds the string value and the count to
 * display on the screen with that value.
 */
private fun List<IndexedValue<GameState>>.valueOptionsFor(
    criterion: ArchiveFilterCriterion,
): List<ArchiveFilterValueOption> {
    return flatMap { game ->
        // Start by making a map from each possible value associated with the given criterion
        // to the games that included that value.  Some criteria can have multiple values
        // from a single game (e.g. team names or observers), but we don't want to include
        // repeats, hence the distinct() call.
        criterion.valuesForGame(game.value).distinct().map { value ->
            value to game
        }
    }
        // Now group games by each unique filter value to get a list of games that had that value.
        .groupBy(
            keySelector = { it.first },
            valueTransform = { it.second },
        )
        // Now we can get the size of each list, which counts the number of games with each value.
        .map { (value, games) ->
            ArchiveFilterValueOption(
                value = value,
                gameCount = games.size,
            )
        }
        // Sort the values alphabetically, but keep N/A last.
        .sortedWith(compareBy<ArchiveFilterValueOption> {
            it.value == ARCHIVE_FILTER_NA
        }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.value })
}

private fun GameState.archiveTournamentFilterValue(): String {
    return tournamentName.trim().ifEmpty { ARCHIVE_FILTER_NA }
}

private fun GameState.archiveDivisionFilterValue(): String {
    return division?.displayText ?: ARCHIVE_FILTER_NA
}

private fun GameState.archiveLevelFilterValue(): String {
    return level.trim().ifEmpty { ARCHIVE_FILTER_NA }
}

private fun GameState.archiveTeamFilterValues(): List<String> {
    return listOf(teamOne.name, teamTwo.name)
}

private fun GameState.archiveObserverFilterValues(): List<String> {
    val observers = observerNames
        .map { it.trim() }
        .filter { it.isNotEmpty() }
    return observers.ifEmpty { listOf(ARCHIVE_FILTER_NA) }
}

private fun ArchiveSortMode.comparator(): Comparator<IndexedValue<GameState>> {
    return when (this) {
        ArchiveSortMode.DATE_NEWEST -> compareByDescending<IndexedValue<GameState>> {
            it.value.startEpoch
        }.thenBy { it.index }
        ArchiveSortMode.DATE_OLDEST -> compareBy<IndexedValue<GameState>> {
            it.value.startEpoch
        }.thenBy { it.index }
        ArchiveSortMode.TEAM_ONE -> compareBy<IndexedValue<GameState>, String>(
            String.CASE_INSENSITIVE_ORDER,
        ) {
            it.value.teamOne.name
        }.thenBy { it.value.startEpoch }
        ArchiveSortMode.TEAM_TWO -> compareBy<IndexedValue<GameState>, String>(
            String.CASE_INSENSITIVE_ORDER,
        ) {
            it.value.teamTwo.name
        }.thenBy { it.value.startEpoch }
    }
}
