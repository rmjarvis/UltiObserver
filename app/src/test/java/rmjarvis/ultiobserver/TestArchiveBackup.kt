package rmjarvis.ultiobserver

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Archive backup round trips, user selections, identity, and storage failures. */
class TestArchiveBackup : GameDomainTestFixtures() {
    @get:Rule val temporaryFolder = TemporaryFolder()

    /** Backups preserve archived game data and restore it without changing other app state. */
    @Test
    fun backupAndRestore() {
        // A completed game is copied unchanged to a backup.
        val setup = newSetupGameState(now = 1_000L)
        val finished = setup.startGame(OrientationPreference.PORTRAIT)
            .beginLivePoint().adjustScore(5, 3)
            .assessYellowCard(team = TeamId.TEAM_ONE, identity = PlayerIdentity("7"), now = 50_000L).state
            .endGameNow(100_000L).pruneUndoHistory()
        val output = ByteArrayOutputStream()
        writeArchiveBackup(listOf(finished), output)
        val restored = readArchiveBackup(ByteArrayInputStream(output.toByteArray())).single()
        assertEquals(finished, restored)
        assertEquals(setup.gameId, restored.gameId)
        assertEquals(restored.gameId, restored.undoEntry!!.previous.gameId)
        assertNull(restored.undoEntry!!.previous.undoEntry)

        // Loading adds the game without changing the current game, profile, or settings.
        val directory = temporaryFolder.newFolder()
        val app = AppState(FileAppStateStorage(directory))
        app.startNewGame(300_000L)
        val current = app.currentGame
        val profile = app.profile
        val settings = app.settings
        app.importArchiveBackup(listOf(restored), emptyMap())
        assertEquals(listOf(restored), app.archivedGames)
        assertEquals(current, app.currentGame)
        assertEquals(profile, app.profile)
        assertEquals(settings, app.settings)
        assertEquals(listOf(restored), AppState(FileAppStateStorage(directory)).archivedGames)

        // Reimporting the identical file does not add a second copy.
        app.importArchiveBackup(listOf(restored), emptyMap())
        assertEquals(listOf(restored), app.archivedGames)
        assertEquals(ArchiveImportStatus.IDENTICAL, restored.importStatus(app.archivedGames, current))

        // A later archive card edit and its undo history survive backup without being pruned again.
        app.openArchivedGame(0, now = 400_000L)
        val editedRecords = replaceEditablePlayerCard(
            records = restored.teamOnePlayers,
            editableCard = editablePlayerCards(restored.teamOnePlayers).single(),
            identity = PlayerIdentity("7", "Casey Handler"),
            cardType = CardType.YELLOW,
            reason = CardReason(preset = "Dangerous play"),
        )
        val edited = restored.editExistingPlayerCards(
            team = TeamId.TEAM_ONE,
            records = editedRecords,
            now = 400_000L,
            undoLabel = "Undo edit yellow card for #7 Casey Handler",
        )
        app.updateViewingArchivedGame(edited)
        assertNotEquals(edited.pruneUndoHistory(), edited)
        val editedOutput = ByteArrayOutputStream()
        writeArchiveBackup(app.archivedGames, editedOutput)
        val editedBackup = readArchiveBackup(ByteArrayInputStream(editedOutput.toByteArray())).single()
        assertEquals(edited, editedBackup)
        assertEquals("Undo edit yellow card for #7 Casey Handler", editedBackup.undoLastAction().undoEntry!!.label)
        assertEquals(ArchiveImportStatus.IDENTICAL, editedBackup.importStatus(app.archivedGames, current))
        app.importArchiveBackup(listOf(editedBackup), emptyMap())
        assertEquals(listOf(edited), app.archivedGames)
    }

    /** Conflict choices control which copy is imported and how Keep both preserves identities. */
    @Test
    fun individualConflicts() {
        // Differing copies default to keeping the phone version and are not imported.
        val backups = (1L..3L).map { now ->
            newSetupGameState(now).startGame(OrientationPreference.PORTRAIT)
                .endGameNow(now + 100_000L).pruneUndoHistory()
        }
        val phone = backups.map { it.copy(tournamentName = "Edited on phone") }
        val preview = ArchiveImportPreview(backups, phone, null, emptyList(), emptyMap())
        backups.forEach { assertEquals(ArchiveImportChoice.KEEP_PHONE, preview.choiceFor(it)) }
        assertTrue(preview.selectedGames.isEmpty())

        // Three differing phone copies can each receive a different import choice.
        val choices = mapOf(
            backups[0].gameId to ArchiveImportChoice.KEEP_PHONE,
            backups[1].gameId to ArchiveImportChoice.RESTORE_BACKUP,
            backups[2].gameId to ArchiveImportChoice.KEEP_BOTH,
        )
        val chosen = ArchiveImportPreview(backups, phone, null, emptyList(), choices)
        assertEquals("Keep phone version", chosen.choiceFor(backups[0]).displayText)
        assertEquals("Restore backup version", chosen.choiceFor(backups[1]).displayText)
        assertEquals("Keep both", chosen.choiceFor(backups[2]).displayText)
        assertEquals(listOf(backups[1], backups[2]), chosen.selectedGames)
        val merged = mergeArchiveBackup(phone, chosen.selectedGames, choices)
        assertEquals(4, merged.size)
        assertEquals(phone[0], merged[0])
        assertEquals(backups[1], merged[1])
        assertEquals(backups[2], merged[3])
        assertNotEquals(phone[2].gameId, merged[2].gameId)
        assertEquals(phone[2].withGameId(merged[2].gameId), merged[2])
        assertEquals(merged[2].gameId, merged[2].undoEntry!!.previous.gameId)

        // The shared backup retains its identity, so repeated imports skip that exact copy.
        assertEquals(ArchiveImportStatus.IDENTICAL, backups[2].importStatus(merged, null))
        assertEquals(merged, mergeArchiveBackup(merged, listOf(backups[2]), emptyMap()))

        // Checkbox bulk actions do not change the radio-button choices for differing copies.
        val newGame = newSetupGameState(4L).startGame(OrientationPreference.PORTRAIT)
            .endGameNow(100_004L).pruneUndoHistory()
        val mixed = ArchiveImportPreview(backups + newGame, phone, null, emptyList(), choices)
        val unselected = ArchiveImportPreview(
            backups + newGame, phone, null, mixed.selection.toggleAll(), choices,
        )
        assertEquals(chosen.selectedGames, unselected.selectedGames)
        backups.forEach { assertEquals(chosen.choiceFor(it), unselected.choiceFor(it)) }
        val reselected = ArchiveImportPreview(
            backups + newGame, phone, null, unselected.selection.toggleAll(), choices,
        )
        assertEquals(chosen.selectedGames + newGame, reselected.selectedGames)
    }

    /** Checkboxes and Select all / Unselect all control selection and preserve hidden exclusions. */
    @Test
    fun selection() {
        // All displayed games start checked, with Unselect all as the bulk action.
        val games = (1L..3L).map { now ->
            newSetupGameState(now).startGame(OrientationPreference.PORTRAIT)
                .endGameNow(now + 100_000L).pruneUndoHistory()
        }
        val selected = ArchiveBackupSelection(games, emptyList())
        assertEquals(games, selected.selectedGames)
        assertEquals(games.map { it.gameId }, selected.selectedIds)
        assertEquals("Unselect all", selected.bulkActionLabel)

        // Individual checkboxes update the bulk label and selected IDs.
        val excluded = toggleExcludedId(emptyList(), games[1].gameId)
        val individual = ArchiveBackupSelection(games, excluded)
        assertEquals(listOf(games[0].gameId, games[2].gameId), individual.selectedIds)
        assertEquals("Select all", individual.bulkActionLabel)
        assertEquals(emptyList<String>(), toggleExcludedId(listOf(games[1].gameId), games[1].gameId))

        // Select all checks a partially selected list; Unselect all clears the entire list.
        val reselected = ArchiveBackupSelection(games, individual.toggleAll())
        assertEquals(games, reselected.selectedGames)
        assertEquals("Unselect all", reselected.bulkActionLabel)
        val unselected = ArchiveBackupSelection(games, reselected.toggleAll())
        assertTrue(unselected.selectedGames.isEmpty())
        assertEquals("Select all", unselected.bulkActionLabel)
        assertEquals(games, ArchiveBackupSelection(games, unselected.toggleAll()).selectedGames)

        // Bulk selection changes only displayed rows and preserves hidden exclusions.
        val filtered = ArchiveBackupSelection(games.take(2), games.map { it.gameId })
        val visibleSelected = ArchiveBackupSelection(games.take(2), filtered.toggleAll())
        assertEquals(games.take(2), visibleSelected.selectedGames)
        assertEquals(listOf(games[2].gameId), filtered.toggleAll())
        assertEquals(games.map { it.gameId }.toSet(), visibleSelected.toggleAll().toSet())
    }

    /** Import status determines section grouping, headings, and which games are eligible to load. */
    @Test
    fun importPreview() {
        // Mixed file order is regrouped without changing order within each section.
        val games = (1L..6L).map { now ->
            newSetupGameState(now).startGame(OrientationPreference.PORTRAIT)
                .endGameNow(now + 100_000L).pruneUndoHistory()
        }
        val identical = games[0]
        val different = games[1]
        val saved = games[2].undoEntry!!.previous
        val current = games[3].undoEntry!!.previous
        val phone = listOf(identical, different.copy(tournamentName = "Phone edit"), saved)
        val preview = ArchiveImportPreview(games, phone, current, emptyList(), emptyMap())
        assertEquals(ArchiveImportStatus.NEW, games[4].importStatus(phone, current))
        assertEquals(ArchiveImportStatus.IDENTICAL, identical.importStatus(phone, current))
        assertEquals(ArchiveImportStatus.DIFFERENT, different.importStatus(phone, current))
        assertEquals(ArchiveImportStatus.UNARCHIVED, games[2].importStatus(phone, current))
        assertEquals(ArchiveImportStatus.UNARCHIVED, games[3].importStatus(phone, current))
        assertEquals(
            listOf(ArchiveImportStatus.NEW, ArchiveImportStatus.DIFFERENT,
                ArchiveImportStatus.IDENTICAL, ArchiveImportStatus.UNARCHIVED),
            preview.sections.map { it.status },
        )
        assertEquals(listOf(games[4], games[5]), preview.sections[0].games)
        assertEquals(listOf(games[2], games[3]), preview.sections[3].games)
        assertEquals(listOf(games[4], games[5]), preview.selection.games)
        assertNull(preview.sections[0].heading)
        assertEquals("Already present on phone — backup differs", preview.sections[1].heading)
        assertEquals("Already present on phone", preview.sections[2].heading)
        assertEquals(
            "Unarchived games on phone (archive or delete to enable restoring from backup)",
            preview.sections[3].heading,
        )
        assertEquals(listOf(games[4], games[5]), preview.selectedGames)

        // A saved setup draft is also an unarchived match and cannot be selected for import.
        val draft = newSetupGameState(100L).withGameId(identical.gameId)
        assertEquals(ArchiveImportStatus.UNARCHIVED, identical.importStatus(listOf(draft), null))
        val draftPreview = ArchiveImportPreview(listOf(identical), listOf(draft), null, emptyList(), emptyMap())
        assertEquals(ArchiveImportStatus.UNARCHIVED, draftPreview.sections.single().status)
        assertTrue(draftPreview.selection.games.isEmpty())
        assertTrue(draftPreview.selectedGames.isEmpty())

        // A lone unarchived match uses singular wording and has no selectable games.
        val blocked = ArchiveImportPreview(listOf(games[3]), phone, current, emptyList(), emptyMap())
        assertEquals(
            "Unarchived game on phone (archive or delete to enable restoring from backup)",
            blocked.sections.single().heading,
        )
        assertTrue(blocked.selection.games.isEmpty())
        assertTrue(blocked.selectedGames.isEmpty())
    }

    /** Export filters expose only matching completed games and honor their checkbox exclusions. */
    @Test
    fun exportSelection() {
        // Export selection exposes matching completed games, excluding drafts and other tournaments.
        val draft = newSetupGameState(1_000L)
        val visible = newSetupGameState(2_000L).copy(tournamentName = "Selected tournament")
            .startGame(OrientationPreference.PORTRAIT).endGameNow(100_000L).pruneUndoHistory()
        val hidden = newSetupGameState(3_000L).copy(tournamentName = "Other tournament")
            .startGame(OrientationPreference.PORTRAIT).endGameNow(200_000L).pruneUndoHistory()
        val phone = listOf(draft, hidden, visible)
        val snapshot = AppState(NoOpAppStateStorage).state.value.copy(
            archivedGames = phone,
            archiveFilterSelections = ArchiveFilterSelections(tournaments = setOf("Selected tournament")),
            archiveSortMode = ArchiveSortMode.DATE_NEWEST,
        )
        val filtered = snapshot.filteredCompletedBackupGames()
        val selected = filtered.backupSelection(phone, listOf(hidden.gameId))
        assertEquals(listOf(visible), selected.games)
        assertEquals(listOf(visible.gameId), selected.selectedIds)
        assertEquals(listOf(visible), selected.selectedGames)

        // Excluding the visible game leaves the filtered export with nothing selected.
        val unselected = filtered.backupSelection(phone, listOf(visible.gameId, hidden.gameId))
        assertTrue(unselected.selectedGames.isEmpty())
    }

    /** Unreadable, unsupported, empty, or inconsistent backup files are rejected. */
    @Test
    fun invalidBackups() {
        // An ordinary game backup establishes the external file shape for corruption checks.
        val game = newSetupGameState(1_000L).startGame(OrientationPreference.PORTRAIT)
            .endGameNow(100_000L).pruneUndoHistory()
        val output = ByteArrayOutputStream()
        writeArchiveBackup(listOf(game), output)
        val json = ZipInputStream(ByteArrayInputStream(output.toByteArray())).use {
            it.nextEntry
            it.readBytes().toString(Charsets.UTF_8)
        }

        // Unknown formats and newer app data fail instead of silently losing game fields.
        val wrongFormat = assertThrows(IllegalArgumentException::class.java) {
            readArchiveBackup(zipJson(json.replace("UltiObserver archive", "Another archive")))
        }
        assertEquals("This is not an UltiObserver backup.", wrongFormat.message)
        assertThrows(IllegalArgumentException::class.java) {
            readArchiveBackup(zipJson(json.replace("\"formatVersion\": 1", "\"formatVersion\": 99")))
        }
        assertThrows(IllegalArgumentException::class.java) {
            readArchiveBackup(zipJson(json.replace(APP_STATE_VERSION_NAME, "99.0")))
        }

        // An empty game list is rejected with a specific message, even in a valid ZIP envelope.
        val emptyOutput = ByteArrayOutputStream()
        writeArchiveBackup(emptyList(), emptyOutput)
        val emptyFailure = assertThrows(IllegalArgumentException::class.java) {
            readArchiveBackup(ByteArrayInputStream(emptyOutput.toByteArray()))
        }
        assertEquals("This backup contains no games.", emptyFailure.message)

        // IDs must be valid and consistent throughout retained history.
        assertThrows(IllegalArgumentException::class.java) {
            readArchiveBackup(zipJson(json.replace(game.gameId, "not-an-id")))
        }
        val abbreviatedId = assertThrows(IllegalArgumentException::class.java) {
            readArchiveBackup(zipJson(json.replace(game.gameId, "1-1-1-1-1")))
        }
        assertEquals("A game has an invalid ID.", abbreviatedId.message)

        // Undo patches inherit the base ID, but a separately stored redo state must agree with it.
        val redoOutput = ByteArrayOutputStream()
        writeArchiveBackup(listOf(game.undoLastAction()), redoOutput)
        val redoJson = ZipInputStream(ByteArrayInputStream(redoOutput.toByteArray())).use {
            it.nextEntry
            it.readBytes().toString(Charsets.UTF_8)
        }
        assertThrows(IllegalArgumentException::class.java) {
            readArchiveBackup(zipJson(redoJson.replaceFirst(game.gameId, java.util.UUID.randomUUID().toString())))
        }

        // Two entries with the same identity are ambiguous and reject the complete backup.
        val duplicateOutput = ByteArrayOutputStream()
        writeArchiveBackup(listOf(game, game), duplicateOutput)
        assertThrows(IllegalArgumentException::class.java) {
            readArchiveBackup(ByteArrayInputStream(duplicateOutput.toByteArray()))
        }

        // Unrelated or broken files are not archive backups.
        assertThrows(IllegalArgumentException::class.java) {
            readArchiveBackup(ByteArrayInputStream("not a zip".toByteArray()))
        }

        // A ZIP with unexpected entries is rejected rather than partly interpreted.
        val extraFiles = ByteArrayOutputStream()
        ZipOutputStream(extraFiles).use {
            it.putNextEntry(ZipEntry("archive.json"))
            it.write(json.toByteArray())
            it.closeEntry()
            it.putNextEntry(ZipEntry("extra.json"))
            it.closeEntry()
        }
        assertThrows(IllegalArgumentException::class.java) {
            readArchiveBackup(ByteArrayInputStream(extraFiles.toByteArray()))
        }
    }

    /** Backup size limits reject oversized exports and oversized expanded input. */
    @Test
    fun oversizedBackup() {
        // An export exceeding the JSON limit asks the observer to select fewer games.
        val game = newSetupGameState(1_000L).startGame(OrientationPreference.PORTRAIT)
            .endGameNow(100_000L).pruneUndoHistory()
            .copy(tournamentName = "Tournament ".repeat(6_000))
        val games = List(1_024) { game.withGameId(java.util.UUID.randomUUID().toString()) }
        val writeFailure = assertThrows(IllegalArgumentException::class.java) {
            writeArchiveBackup(games, ByteArrayOutputStream())
        }
        assertEquals("This backup is too large. Select fewer games.", writeFailure.message)

        // A tiny compressed file must not expand without a bound while being read.
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use { zip ->
            zip.putNextEntry(ZipEntry("archive.json"))
            val block = ByteArray(8192) { ' '.code.toByte() }
            repeat(8193) { zip.write(block) }
            zip.closeEntry()
        }
        val failure = assertThrows(IllegalArgumentException::class.java) {
            readArchiveBackup(ByteArrayInputStream(output.toByteArray()))
        }
        assertEquals("This backup is too large to load.", failure.message)
    }

    /** A failed import leaves the app's archive and its saved games unchanged. */
    @Test
    fun failedImportPreservesArchive() {
        // A failed write to the staged replacement leaves both disk and app state unchanged.
        val directory = temporaryFolder.newFolder()
        val game = newSetupGameState(1_000L).startGame(OrientationPreference.PORTRAIT)
            .endGameNow(100_000L).pruneUndoHistory()
        FileAppStateStorage(directory).saveArchivedGames(listOf(game))
        val failing = AppState(FileAppStateStorage(directory, moveFileAtomically = { _, _ ->
            throw IOException("Disk full")
        }))
        assertThrows(IOException::class.java) {
            failing.importArchiveBackup(
                listOf(game.copy(tournamentName = "Backup version")),
                mapOf(game.gameId to ArchiveImportChoice.RESTORE_BACKUP),
            )
        }
        assertEquals(listOf(game), failing.archivedGames)
        assertEquals(listOf(game), FileAppStateStorage(directory).loadArchivedGames())
    }

    private fun zipJson(json: String): ByteArrayInputStream {
        val output = ByteArrayOutputStream()
        ZipOutputStream(output).use {
            it.putNextEntry(ZipEntry("archive.json"))
            it.write(json.toByteArray())
            it.closeEntry()
        }
        return ByteArrayInputStream(output.toByteArray())
    }
}
