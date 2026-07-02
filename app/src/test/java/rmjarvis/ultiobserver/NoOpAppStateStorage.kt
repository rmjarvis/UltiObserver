package rmjarvis.ultiobserver

/**
 * Test-only storage implementation that does not persist anything.
 *
 * Unit tests use this when they want a fresh ViewModel with default app state and no file-system
 * setup, so the test can focus on ViewModel behavior rather than persistence mechanics.
 */
internal object NoOpAppStateStorage : AppStateStorage {
    override val resetPersistedDataAreas: Set<PersistedData> = emptySet()

    /// Load no current-game state for unit tests that do not exercise persistence.
    override fun loadCurrentGame(): GameState? = null

    /// Ignore current-game saves for unit tests that do not exercise persistence.
    override fun saveCurrentGame(state: GameState?) = Unit

    /// Load no profile state for unit tests that do not exercise persistence.
    override fun loadProfile(): Profile? = null

    /// Ignore profile saves for unit tests that do not exercise persistence.
    override fun saveProfile(state: Profile) = Unit

    /// Load no settings state for unit tests that do not exercise persistence.
    override fun loadSettings(): Settings? = null

    /// Ignore settings saves for unit tests that do not exercise persistence.
    override fun saveSettings(state: Settings) = Unit

    /// Load no archived games for unit tests that do not exercise persistence.
    override fun loadArchivedGames(): List<GameState> = emptyList()

    /// Ignore archived-game saves for unit tests that do not exercise persistence.
    override fun saveArchivedGames(games: List<GameState>) = Unit
}
