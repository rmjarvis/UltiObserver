package rmjarvis.ultiobserver

import android.app.Activity
import android.content.ContentProvider
import android.content.ContentResolver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.UriPermission
import android.content.res.AssetFileDescriptor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.Parcel
import android.os.ParcelFileDescriptor
import android.provider.DocumentsContract
import android.provider.OpenableColumns
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.mutableStateOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertHasNoClickAction
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.filters.SdkSuppress
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import java.util.regex.Pattern
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Test
import org.mockito.ArgumentMatchers.any
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.eq
import org.mockito.Mockito.doAnswer
import org.mockito.Mockito.doReturn
import org.mockito.Mockito.doThrow
import org.mockito.Mockito.mock
import org.mockito.Mockito.spy
import rmjarvis.ultiobserver.ui.theme.UltiObserverTheme

/** Exercise archive controls and Android's real backup file and folder pickers. */
class TestArchiveBackupUi : MainActivityUiTestFixtures() {
    /** Backup filters, sorting, row selection, and navigation work without saving a file. */
    @Test
    fun backupControls() {
        // Two historical archives make a Today filter empty and allow filtering by tournament.
        val game = newSetupGameState(1_000L).copy(tournamentName = "Historical game")
            .startGame(OrientationPreference.PORTRAIT).endGameNow(100_000L).pruneUndoHistory()
        val otherGame = newSetupGameState(2_000L).copy(
            tournamentName = "Other tournament",
            teamOne = game.teamOne.copy(name = "Other team"),
        ).startGame(OrientationPreference.PORTRAIT).endGameNow(200_000L).pruneUndoHistory()
        clearCurrentGameProgrammatically()
        clearArchivedGamesProgrammatically()
        composeRule.activityRule.scenario.onActivity {
            it.appState.updateCurrentGame(game)
            it.appState.archiveCompletedGame()
            it.appState.updateCurrentGame(otherGame)
            it.appState.archiveCompletedGame()
        }
        composeRule.onNodeWithText("Archived/saved games").performClick()
        composeRule.onNodeWithText("Back up archived games").performClick()

        // Clicking the game row toggles the same selection as its checkbox.
        composeRule.onNodeWithText(game.gameListEntry().summaryLine).performClick()
        composeRule.onNodeWithText("Back up 1 game").assertIsEnabled()
        composeRule.onNodeWithText(game.gameListEntry().summaryLine).performClick()
        composeRule.onNodeWithText("Back up 2 games").assertIsEnabled()

        // Sorting from the backup page updates its shared archive sort summary.
        composeRule.onNodeWithTag("archive-sort-button").performClick()
        composeRule.onNodeWithTag("archive-sort-DATE_OLDEST").performClick()
        composeRule.onNodeWithTag("archive-filter-and-sort-summary")
            .assertTextContains("Sorted by date, oldest first", substring = true)

        // An empty date filter hides bulk selection and disables backup; clearing it restores the row.
        composeRule.onNodeWithTag("archive-filter-button").performClick()
        composeRule.onNodeWithTag("archive-filter-field-DATE").performClick()
        composeRule.onNodeWithTag("archive-date-preset-TODAY").performClick()
        composeRule.onNodeWithText("Done").performClick()
        composeRule.onNodeWithText("Done").performClick()
        waitForText("No archived games match these filters.")
        composeRule.onNodeWithText("Back up 0 games").assertIsNotEnabled()
        composeRule.onNodeWithText("Select all").assertDoesNotExist()
        composeRule.onNodeWithText("Unselect all").assertDoesNotExist()
        composeRule.onNodeWithTag("archive-filter-button").performClick()
        composeRule.onNodeWithTag("archive-filter-field-DATE").performClick()
        composeRule.onNodeWithTag("archive-clear-filter-DATE").performClick()
        composeRule.onNodeWithText("Done").performClick()
        composeRule.onNodeWithText("Done").performClick()
        composeRule.onNodeWithText("Back up 2 games").assertIsEnabled()

        // Clearing a tournament filter restores the other tournament's game to the backup list.
        composeRule.onNodeWithTag("archive-filter-button").performClick()
        composeRule.onNodeWithTag("archive-filter-field-TOURNAMENT").performClick()
        composeRule.onNodeWithTag("archive-filter-value-Historical game").performClick()
        composeRule.onNodeWithText("Back").performClick()
        composeRule.onNodeWithText("Done").performClick()
        composeRule.onNodeWithText(game.gameListEntry().summaryLine).assertIsDisplayed()
        composeRule.onNodeWithText(otherGame.gameListEntry().summaryLine).assertDoesNotExist()
        composeRule.onNodeWithText("Back up 1 game").assertIsEnabled()
        composeRule.onNodeWithTag("archive-filter-button").performClick()
        composeRule.onNodeWithTag("archive-filter-field-TOURNAMENT").performClick()
        composeRule.onNodeWithTag("archive-clear-filter-TOURNAMENT").performClick()
        composeRule.onNodeWithText("Back").performClick()
        composeRule.onNodeWithText("Done").performClick()
        composeRule.onNodeWithText(game.gameListEntry().summaryLine).assertIsDisplayed()
        composeRule.onNodeWithText(otherGame.gameListEntry().summaryLine).assertIsDisplayed()
        composeRule.onNodeWithText("Back up 2 games").assertIsEnabled()

        // Clearing all filters removes both tournament and date restrictions, restoring both games.
        composeRule.onNodeWithTag("archive-filter-button").performClick()
        composeRule.onNodeWithTag("archive-filter-field-TOURNAMENT").performClick()
        composeRule.onNodeWithTag("archive-filter-value-Historical game").performClick()
        composeRule.onNodeWithText("Back").performClick()
        composeRule.onNodeWithTag("archive-filter-field-DATE").performClick()
        composeRule.onNodeWithTag("archive-date-preset-TODAY").performClick()
        composeRule.onNodeWithText("Done").performClick()
        composeRule.onNodeWithText("Done").performClick()
        waitForText("No archived games match these filters.")
        composeRule.onNodeWithText("Back up 0 games").assertIsNotEnabled()
        composeRule.onNodeWithTag("archive-filter-button").performClick()
        composeRule.onNodeWithTag("archive-clear-filters").performClick()
        composeRule.onNodeWithText("Done").performClick()
        composeRule.onNodeWithText(game.gameListEntry().summaryLine).assertIsDisplayed()
        composeRule.onNodeWithText(otherGame.gameListEntry().summaryLine).assertIsDisplayed()
        composeRule.onNodeWithText("Back up 2 games").assertIsEnabled()

        // Back returns to archive categories; Home leaves the archive workflow entirely.
        tapTopBarBack()
        waitForText("Archived games (2)")
        composeRule.onNodeWithText("Back up archived games").performClick()
        tapTopBarHome()
        waitForText("Start new game")
    }

    /** Save backups, reuse the chosen folder, and load files with per-game import choices. */
    @Test
    fun backupAndLoad() {
        // Seed two completed games before exercising the visible backup selection controls.
        val games = (1L..2L).map { index ->
            newSetupGameState(index * 1_000L).copy(tournamentName = "Backup $index")
                .startGame(OrientationPreference.PORTRAIT)
                .endGameNow(index * 100_000L).pruneUndoHistory()
        }
        val savedDocuments = mutableListOf<Uri>()
        val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
        val context = composeRule.activity.applicationContext
        // Older Android releases do not create Documents automatically.
        device.executeShellCommand("mkdir -p /sdcard/Documents")
        val parentName = "archive-ui-parent-${System.nanoTime()}"
        val parentPath = "/sdcard/Documents/$parentName"
        device.executeShellCommand("mkdir -p $parentPath")
        device.executeShellCommand("touch $parentPath/unrelated.txt")
        // This clears the saved backup dir locations so that the test asks to create a backup
        // folder.
        context.getSharedPreferences("archive_backup_location", Context.MODE_PRIVATE).edit().clear().commit()
        clearCurrentGameProgrammatically()
        clearArchivedGamesProgrammatically()
        composeRule.activityRule.scenario.onActivity { activity ->
            games.forEach { game ->
                activity.appState.updateCurrentGame(game)
                activity.appState.archiveCompletedGame()
            }
        }
        composeRule.waitForIdle()
        try {
            composeRule.onNodeWithText("Archived/saved games").performClick()

            // Loading before folder setup opens the picker without asking to create a folder.
            composeRule.onNodeWithText("Load games from backup").performClick()
            catchAndDiagnoseFailure {
                composeRule.waitUntil(10_000L) {
                    device.hasObject(By.pkg(Pattern.compile("com\\.(android|google\\.android)\\.documentsui")))
                }
            }
            // Return from whatever location Android selected, including any parent directories.
            cancelPicker(device)
            waitForText("Choose backup file")
            pressAppBack()

            // Backup selection starts with both completed games checked.
            composeRule.onNodeWithText("Back up archived games").performClick()
            composeRule.onNodeWithText("Back up 2 games").assertIsEnabled()
            composeRule.onNodeWithText("Unselect all").performClick()
            composeRule.onNodeWithText("Back up 0 games").assertIsNotEnabled()
            composeRule.onNodeWithText("Select all").performClick()

            // Individual checkboxes exclude just one game and change the bulk action label.
            composeRule.onNodeWithTag("backup-select-${games[1].gameId}").performClick()
            composeRule.onNodeWithText("Back up 1 game").assertIsEnabled()
            composeRule.onNodeWithText("Select all").performClick()

            // Filtering limits the exported set while preserving checkbox choices.
            composeRule.onNodeWithTag("archive-filter-button").performClick()
            composeRule.onNodeWithTag("archive-filter-field-TOURNAMENT").performClick()
            composeRule.onNodeWithTag("archive-filter-value-Backup 1").performClick()
            composeRule.onNodeWithText("Back").performClick()
            composeRule.onNodeWithText("Done").performClick()
            composeRule.onNodeWithText("Back up 1 game").performClick()
            waitForText("Choose your default backup location")
            dismissDialog(text = "Cancel", waitForText = "Back up 1 game")
            composeRule.onNodeWithText("Back up 1 game").performClick()
            waitForText("Choose your default backup location")
            composeRule.onNodeWithText("Create backup folder").performClick()

            // Cancelling folder selection leaves the games selected and permits another attempt.
            openFolderInPicker(device, parentName)
            cancelPicker(device)
            composeRule.onNodeWithText("Back up 1 game").assertIsEnabled()
            composeRule.onNodeWithText("Back up 1 game").performClick()
            waitForText("Choose your default backup location")
            composeRule.onNodeWithText("Create backup folder").performClick()
            openFolderInPicker(device, parentName)

            // Approve the real Android folder request once; the app creates its backup folder.
            selectBackupFolder(device)
            catchAndDiagnoseFailure {
                composeRule.waitUntil(10_000L) { device.hasObject(By.clazz("android.widget.EditText")) }
            }
            cancelPicker(device)
            composeRule.onNodeWithText("Back up 1 game").assertIsEnabled()

            // A conflicting file introduced outside the app explains a failed save without losing games.
            val initialDirectory = ArchiveBackupDirectory(context).directory()!!
            val parent = DocumentsContract.buildDocumentUriUsingTree(
                initialDirectory, DocumentsContract.getTreeDocumentId(initialDirectory),
            )
            DocumentsContract.deleteDocument(context.contentResolver, initialDirectory)
            val conflictingFile = DocumentsContract.createDocument(
                context.contentResolver, parent, "application/octet-stream", BACKUP_DIRECTORY_NAME,
            )!!
            composeRule.onNodeWithText("Back up 1 game").performClick()
            waitForText("Could not save backup. A file named $BACKUP_DIRECTORY_NAME already exists in this folder.")
            dismissDialog(text = "OK", waitForText = "Back up 1 game")
            DocumentsContract.deleteDocument(context.contentResolver, conflictingFile)

            // After removing the conflicting file, saving recreates the missing backup directory.
            composeRule.onNodeWithText("Back up 1 game").performClick()
            val documentName = "archive-ui-test-${System.nanoTime()}.zip"
            val backup = saveBackupInPicker(device, documentName, 1)
            savedDocuments += backup
            assertEquals(listOf(games[0]), context.contentResolver.openInputStream(backup)!!.use(::readArchiveBackup))

            // Reopening backup remembers the folder and saves there through the real Android picker.
            pressAppBack()
            composeRule.onNodeWithText("Back up archived games").performClick()
            composeRule.onNodeWithText("Back up 2 games").performClick()
            val secondBackup = saveBackupInPicker(device, "archive-ui-test-${System.nanoTime()}.zip", 2)
            savedDocuments += secondBackup
            val directory = ArchiveBackupDirectory(context).directory()!!
            assertEquals(games.toSet(), context.contentResolver.openInputStream(secondBackup)!!
                .use(::readArchiveBackup).toSet())
            pressAppBack()

            // A changed backup offers per-game choices; Keep both retains the backup's ID.
            // In practice, this would be done by restoring the archive and then doing something
            // with it. Here we just programmatically change the tournament name.
            val changed = games[0].copy(tournamentName = "Backup version")
            val newGame = newSetupGameState(3_000L).copy(tournamentName = "New backup game")
                .startGame(OrientationPreference.PORTRAIT)
                .endGameNow(300_000L).pruneUndoHistory()
            context.contentResolver.openOutputStream(backup, "wt")!!.use {
                writeArchiveBackup(listOf(changed, newGame), it)
            }
            composeRule.onNodeWithText("Load games from backup").performClick()
            // A corrupt file reports an error; choosing it again after repair loads normally.
            context.contentResolver.openOutputStream(backup, "wt")!!.use {
                it.write("not a backup".toByteArray())
            }
            openBackupInPicker(device, documentName)
            waitForText("Could not load backup. This is not an UltiObserver backup.")
            dismissDialog(text = "OK", waitForText = "Choose backup file")
            context.contentResolver.openOutputStream(backup, "wt")!!.use {
                writeArchiveBackup(listOf(changed, newGame), it)
            }
            composeRule.onNodeWithText("Choose backup file").performClick()
            openBackupInPicker(device, documentName)
            waitForText("Already present on phone — backup differs")

            // Recreation retains the chosen file instead of opening the picker a second time.
            composeRule.activityRule.scenario.recreate()
            waitForText("Already present on phone — backup differs")
            composeRule.onNodeWithText("Load 1 game").assertIsEnabled()

            // Bulk selection controls the new-game checkboxes, leaving conflict choices separate.
            composeRule.onNodeWithText("Load 1 game").assertIsEnabled()
            composeRule.onNodeWithText("Unselect all").performClick()
            composeRule.onNodeWithText("Load 0 games").assertIsNotEnabled()
            composeRule.onNodeWithText("Select all").performClick()
            composeRule.onNodeWithText("Load 1 game").assertIsEnabled()
            composeRule.onNodeWithTag("backup-select-${newGame.gameId}").performClick()
            composeRule.onNodeWithText("Select all").assertExists()
            composeRule.onNodeWithText("Load 0 games").assertIsNotEnabled()

            // Each conflict choice updates the load count; keeping the phone copy excludes the game.
            composeRule.onNodeWithTag("import-choice-${changed.gameId}-RESTORE_BACKUP")
                .performScrollTo().performClick()
            composeRule.onNodeWithText("Load 1 game").assertIsEnabled()
            composeRule.onNodeWithTag("import-choice-${changed.gameId}-KEEP_PHONE")
                .performScrollTo().performClick()
            composeRule.onNodeWithText("Load 0 games").assertIsNotEnabled()
            composeRule.onNodeWithTag("import-choice-${changed.gameId}-KEEP_BOTH")
                .performScrollTo().performClick()
            composeRule.onNodeWithText("Load 1 game").assertIsEnabled()

            // Loading with Keep both retains the backup's identity and gives the phone copy a new one.
            composeRule.onNodeWithText("Load 1 game").performClick()
            waitForText("Archived games (3)")
            composeRule.activityRule.scenario.onActivity {
                val restored = it.appState.archivedGames
                assertEquals(3, restored.size)
                assertEquals(changed, restored.last())
                assertNotEquals(games[0].gameId, restored.first().gameId)
            }

            // Loading the same document again recognizes the retained backup identity.
            composeRule.onNodeWithText("Load games from backup").performClick()
            openBackupInPicker(device, documentName)
            waitForTag("backup-present-${changed.gameId}")
            composeRule.onNodeWithText("Already present on phone").assertExists()
            composeRule.onNodeWithContentDescription("Already on this phone").assertExists()
            composeRule.onNodeWithText("Already on this phone").assertDoesNotExist()
            composeRule.onNodeWithText("Unselect all").performClick()
            composeRule.onNodeWithText("Load 0 games").assertIsNotEnabled()

            // Revoked folder permission triggers setup again instead of keeping a stale location.
            savedDocuments.forEach { DocumentsContract.deleteDocument(context.contentResolver, it) }
            savedDocuments.clear()
            val tree = DocumentsContract.buildTreeDocumentUri(
                directory.authority, DocumentsContract.getTreeDocumentId(directory),
            )
            context.contentResolver.releasePersistableUriPermission(
                tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
            assertNull(ArchiveBackupDirectory(context).directory())

            // Select the existing backup directory itself during setup; do not nest another one.
            pressAppBack()
            composeRule.onNodeWithText("Back up archived games").performClick()
            composeRule.onNodeWithText("Back up 3 games").performClick()
            waitForText("Choose your default backup location")
            composeRule.onNodeWithText("Create backup folder").performClick()
            openFolderInPicker(device, parentName)
            openFolderInPicker(device, BACKUP_DIRECTORY_NAME)
            selectBackupFolder(device)
            val finalDocumentName = "archive-ui-test-${System.nanoTime()}.zip"
            savedDocuments += saveBackupInPicker(device, finalDocumentName, 3)
            val reused = ArchiveBackupDirectory(context).directory()!!
            assertEquals(DocumentsContract.getDocumentId(directory), DocumentsContract.getDocumentId(reused))
            assertEquals(DocumentsContract.getDocumentId(directory), DocumentsContract.getTreeDocumentId(reused))

            // Restoring a phone archive makes its backup unavailable until the phone copy is archived.
            tapTopBarBack()
            composeRule.onNodeWithText("Archived games (3)").performClick()
            composeRule.onNodeWithTag("archived-game-0").performClick()
            composeRule.onNodeWithText("Restore game").performClick()
            tapTopBarHome()
            composeRule.onNodeWithText("Archived/saved games").performClick()
            composeRule.onNodeWithText("Load games from backup").performClick()
            openBackupInPicker(device, finalDocumentName)
            waitForText("Unarchived game on phone (archive or delete to enable restoring from backup)")
            composeRule.onNodeWithContentDescription("Cannot import — archive or delete the phone copy first")
                .assertExists()
            composeRule.onNodeWithText("Load 0 games").assertIsNotEnabled()
            composeRule.onNodeWithText("Select all").assertDoesNotExist()
            composeRule.onNodeWithText("Unselect all").assertDoesNotExist()
            tapTopBarHome()
            waitForText("Start new game")

            // A deleted selected folder is forgotten, allowing backup setup again.
            savedDocuments.forEach { DocumentsContract.deleteDocument(context.contentResolver, it) }
            savedDocuments.clear()
            DocumentsContract.deleteDocument(context.contentResolver, reused)
            assertNull(ArchiveBackupDirectory(context).directory())
            composeRule.onNodeWithText("Archived/saved games").performClick()
            composeRule.onNodeWithText("Back up archived games").performClick()
            composeRule.onNodeWithText("Back up 2 games").performClick()
            waitForText("Choose your default backup location")
            dismissDialog(text = "Cancel", waitForText = "Back up 2 games")
        } finally {
            savedDocuments.forEach { DocumentsContract.deleteDocument(context.contentResolver, it) }
            device.executeShellCommand("rm -r $parentPath")
        }
    }

    /** Test rare but possible Android storage failures when setting or reusing the backup folder. */
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q) // ContentResolver.wrap requires Android 10.
    fun folderFailures() {
        val unavailableFolder = BackupProviderFixture(composeRule.activity)
        val directory = ArchiveBackupDirectory(unavailableFolder.context)

        // A grant for another folder or only one access mode cannot authorize this saved location.
        val otherTree = DocumentsContract.buildTreeDocumentUri("backup.test", "other")
        for (grant in listOf(
            backupPermission(otherTree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION),
            backupPermission(unavailableFolder.tree, Intent.FLAG_GRANT_READ_URI_PERMISSION),
            backupPermission(unavailableFolder.tree, Intent.FLAG_GRANT_WRITE_URI_PERMISSION),
        )) {
            unavailableFolder.preferences.edit().putString("parent", unavailableFolder.tree.toString()).commit()
            doReturn(listOf(grant)).`when`(unavailableFolder.resolver).persistedUriPermissions
            assertNull(directory.directory())
            assertFalse(unavailableFolder.preferences.contains("parent"))
        }

        // A provider may reject access even while Android still lists the persisted grant.
        unavailableFolder.rememberFolder()
        doThrow(SecurityException("Access revoked")).`when`(unavailableFolder.provider)
            .query(eq(unavailableFolder.parent), any(), any<Bundle>(), any<CancellationSignal>())
        assertNull(directory.directory())
        assertFalse(unavailableFolder.preferences.contains("parent"))

        // Providers can report a deleted folder with an empty cursor or a null query result.
        for (missing in listOf(MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME)), null)) {
            unavailableFolder.rememberFolder()
            doReturn(missing).`when`(unavailableFolder.provider)
                .query(eq(unavailableFolder.parent), any(), any<Bundle>(), any<CancellationSignal>())
            assertNull(directory.directory())
            assertFalse(unavailableFolder.preferences.contains("parent"))
        }

        // Failure to list a valid parent's children reports the problem instead of claiming success.
        val unreadableChildren = BackupProviderFixture(composeRule.activity)
        doReturn(null).`when`(unreadableChildren.provider)
            .query(eq(unreadableChildren.children), any(), any<Bundle>(), any<CancellationSignal>())
        assertEquals("The chosen folder could not be read.", assertThrows(IOException::class.java) {
            ArchiveBackupDirectory(unreadableChildren.context).chooseParent(unreadableChildren.tree)
        }.message)

        // A provider that cannot create the requested folder leaves setup unfinished.
        val failedCreation = BackupProviderFixture(composeRule.activity)
        failedCreation.preferences.edit().clear().commit()
        doAnswer { MatrixCursor(arrayOf(
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
        )) }.`when`(failedCreation.provider)
            .query(eq(failedCreation.children), any(), any<Bundle>(), any<CancellationSignal>())
        doReturn(Bundle()).`when`(failedCreation.provider).call(any(), any(), any(), any())
        assertEquals("The backup folder could not be created.", assertThrows(IOException::class.java) {
            ArchiveBackupDirectory(failedCreation.context).chooseParent(failedCreation.tree)
        }.message)
        assertFalse(failedCreation.preferences.contains("parent"))
    }

    /** Test backup-file read/write failures and successful saves with missing filename information. */
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    fun providerFailures() {
        val game = newSetupGameState(1_000L).startGame(OrientationPreference.PORTRAIT)
            .endGameNow(100_000L).pruneUndoHistory()
        clearCurrentGameProgrammatically()
        clearArchivedGamesProgrammatically()
        composeRule.activityRule.scenario.onActivity {
            it.appState.updateCurrentGame(game)
            it.appState.archiveCompletedGame()
        }
        val unavailableStream = BackupProviderFixture(composeRule.activity)
        val registry = BackupPickerRegistry(unavailableStream.document)
        showTransferScreen(unavailableStream.context, registry, backup = true)

        // A provider returning no output stream reports a save error and leaves the archive intact.
        doReturn(null).`when`(unavailableStream.provider)
            .openAssetFile(any(), eq("w"), any<CancellationSignal>())
        composeRule.onNodeWithText("Back up 1 game").performClick()
        waitForText("Could not save backup. The selected file could not be opened.")
        composeRule.onNodeWithText("OK").performClick()
        assertEquals(listOf(game), composeRule.activity.appState.archivedGames)

        // An exception without explanatory text uses the generic retry message.
        doThrow(FileNotFoundException()).`when`(unavailableStream.provider)
            .openAssetFile(any(), eq("w"), any<CancellationSignal>())
        composeRule.onNodeWithText("Back up 1 game").performClick()
        waitForText("Could not save backup. Please try again.")
        composeRule.onNodeWithText("OK").performClick()

        // If the filename lookup fails after saving, still confirm that the backup was saved.
        val output = File.createTempFile("backup-provider", ".zip", composeRule.activity.cacheDir)
        try {
            doAnswer {
                AssetFileDescriptor(ParcelFileDescriptor.open(output,
                    ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_TRUNCATE), 0, -1)
            }.`when`(unavailableStream.provider).openAssetFile(any(), eq("w"), any<CancellationSignal>())
            for (metadata in listOf(null, MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME)))) {
                doReturn(metadata).`when`(unavailableStream.provider)
                    .query(eq(unavailableStream.document), any(), any<Bundle>(), any<CancellationSignal>())
                composeRule.onNodeWithText("Back up 1 game").performClick()
                waitForText("Backed up 1 game.")
                composeRule.onNodeWithText("OK").performClick()
                assertEquals(listOf(game), output.inputStream().use(::readArchiveBackup))
            }
        } finally {
            output.delete()
        }

        // The equivalent input-stream failure reports a load error and imports nothing.
        showTransferScreen(unavailableStream.context, registry, backup = false)
        waitForText("Could not load backup. The selected file could not be opened.")
        composeRule.onNodeWithText("OK").performClick()
        assertEquals(listOf(game), composeRule.activity.appState.archivedGames)
    }

    /**
     * Test blocked navigation and selection during slow transfers, and reopening the screen
     * after an interrupted load.
     */
    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.Q)
    fun transferLifecycle() {
        clearCurrentGameProgrammatically()
        clearArchivedGamesProgrammatically()
        val game = newSetupGameState(1_000L).startGame(OrientationPreference.PORTRAIT)
            .endGameNow(100_000L).pruneUndoHistory()
        val input = File.createTempFile("slow-backup", ".zip", composeRule.activity.cacheDir)
        input.outputStream().use { writeArchiveBackup(listOf(game), it) }
        val slowProvider = BackupProviderFixture(composeRule.activity)
        val entered = AtomicBoolean(false)
        val release = CountDownLatch(1)
        val returned = AtomicBoolean(false)
        doAnswer {
            entered.set(true)
            try {
                check(release.await(30, TimeUnit.SECONDS)) { "Test did not release provider" }
                AssetFileDescriptor(ParcelFileDescriptor.open(input, ParcelFileDescriptor.MODE_READ_ONLY), 0, -1)
            } finally {
                returned.set(true)
            }
        }.`when`(slowProvider.provider).openAssetFile(any(), eq("r"), any<CancellationSignal>())
        val visible = mutableStateOf(true)
        val navigated = AtomicBoolean(false)
        val registry = BackupPickerRegistry(slowProvider.document)
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                CompositionLocalProvider(
                    LocalContext provides slowProvider.context,
                    LocalActivityResultRegistryOwner provides registry,
                ) {
                    UltiObserverTheme(dynamicColor = false) {
                        if (visible.value) ArchiveTransferScreen(activity.appState, backup = false,
                            onBack = { navigated.set(true) }, onHome = { navigated.set(true) })
                    }
                }
            }
        }
        try {
            // Navigation cannot leave the screen while provider work is outstanding.
            composeRule.waitUntil(10_000L) { entered.get() }
            waitForText("Please wait.")
            pressAppBack()
            tapTopBarBack()
            tapTopBarHome()
            assertFalse(navigated.get())
            composeRule.onNodeWithText("Choose backup file").assertIsNotEnabled()

            // Disposing the screen cancels its coroutine; cancellation must not become an error dialog.
            composeRule.runOnIdle { visible.value = false }
            release.countDown()
            composeRule.waitUntil(10_000L) { returned.get() }
            composeRule.runOnIdle { visible.value = true }
            waitForText("Load 1 game")
            composeRule.onNodeWithText("Could not load backup.", substring = true).assertDoesNotExist()
            assertTrue(composeRule.activity.appState.archivedGames.isEmpty())

            // Load the preview, then hold its backup write open to check disabled row selection.
            composeRule.onNodeWithText("Load 1 game").performClick()
            composeRule.waitUntil(10_000L) { composeRule.activity.appState.archivedGames.size == 1 }
            val writing = AtomicBoolean(false)
            val finishWrite = CountDownLatch(1)
            doAnswer {
                writing.set(true)
                check(finishWrite.await(30, TimeUnit.SECONDS)) { "Test did not release backup write" }
                AssetFileDescriptor(ParcelFileDescriptor.open(input,
                    ParcelFileDescriptor.MODE_READ_WRITE or ParcelFileDescriptor.MODE_TRUNCATE), 0, -1)
            }.`when`(slowProvider.provider).openAssetFile(any(), eq("w"), any<CancellationSignal>())
            showTransferScreen(slowProvider.context, registry, backup = true)
            try {
                composeRule.onNodeWithText("Back up 1 game").performClick()
                composeRule.waitUntil(10_000L) { writing.get() }
                composeRule.onNodeWithTag("backup-select-${game.gameId}").assertIsNotEnabled()
                composeRule.onNodeWithText(game.gameListEntry().summaryLine).assertHasNoClickAction()
                composeRule.onNodeWithText("Back up 1 game").assertIsNotEnabled()
            } finally {
                finishWrite.countDown()
            }
            waitForText("Backed up 1 game.")
            composeRule.onNodeWithText("OK").performClick()
            composeRule.onNodeWithTag("backup-select-${game.gameId}").assertIsEnabled()
        } finally {
            release.countDown()
            input.delete()
        }
    }

    /** Render the transfer UI with a controlled document provider and picker result. */
    private fun showTransferScreen(context: Context, registry: BackupPickerRegistry, backup: Boolean) {
        composeRule.activityRule.scenario.onActivity { activity ->
            activity.setContent {
                CompositionLocalProvider(LocalContext provides context,
                    LocalActivityResultRegistryOwner provides registry) {
                    UltiObserverTheme(dynamicColor = false) {
                        ArchiveTransferScreen(activity.appState, backup,
                            onBack = { activity.appState.goHome() },
                            onHome = { activity.appState.goHome() })
                    }
                }
            }
        }
    }

    /** Enter a test-owned folder from the requested Documents location. */
    private fun openFolderInPicker(device: UiDevice, name: String) {
        catchAndDiagnoseFailure {
            composeRule.waitUntil(10_000L) { device.hasObject(By.text(name)) }
        }
        device.findObject(By.text(name)).click()
        assertTrue(device.wait(Until.hasObject(
            By.res(Pattern.compile(".*:id/toolbar")).hasDescendant(By.text(name)),
        ), 10_000L))
    }

    /** Cancel the real picker, including any directory navigation on its Back stack. */
    private fun cancelPicker(device: UiDevice) {
        val app = By.pkg(composeRule.activity.packageName)
        catchAndDiagnoseFailure {
            composeRule.waitUntil(10_000L) {
                if (device.hasObject(app)) {
                    true
                } else {
                    device.pressBack()
                    // The lookup waits for idle; check the result of Back before timing out.
                    device.hasObject(app)
                }
            }
        }
    }

    /** Approve the displayed folder using the platform's selection and permission controls. */
    private fun selectBackupFolder(device: UiDevice) {
        val label = when {
            Build.VERSION.SDK_INT < Build.VERSION_CODES.Q -> "Select"
            Build.VERSION.SDK_INT == Build.VERSION_CODES.Q -> "Allow access to .*"
            else -> "Use this folder"
        }
        val select = device.wait(
            Until.findObject(By.text(Pattern.compile(label, Pattern.CASE_INSENSITIVE))), 10_000L,
        )
        assertNotNull(select)
        select.click()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val allow = device.wait(
                Until.findObject(By.text(Pattern.compile("Allow", Pattern.CASE_INSENSITIVE))),
                10_000L,
            )
            assertNotNull(allow)
            allow.click()
        }
    }

    /** Save in the displayed backup folder and return the document for content checks and cleanup. */
    private fun saveBackupInPicker(device: UiDevice, documentName: String, gameCount: Int): Uri {
        catchAndDiagnoseFailure {
            composeRule.waitUntil(10_000L) {
                device.hasObject(By.clazz("android.widget.EditText"))
            }
        }
        val filename = device.findObject(By.clazz("android.widget.EditText"))
        assertNotNull(filename)
        assertNotNull(device.findObject(By.text(BACKUP_DIRECTORY_NAME)))
        filename.text = documentName
        val save = device.wait(
            Until.findObject(By.text(Pattern.compile("Save", Pattern.CASE_INSENSITIVE))), 10_000L,
        )
        assertNotNull(save)
        save.click()
        assertTrue(device.wait(Until.hasObject(By.pkg(composeRule.activity.packageName)), 10_000L))
        waitForText("Backed up ${countedNounPhrase(gameCount, "game")} to $documentName.")
        composeRule.onNodeWithText("OK").performClick()
        val context = composeRule.activity.applicationContext
        val directory = ArchiveBackupDirectory(context).directory()!!
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(
            directory, DocumentsContract.getDocumentId(directory),
        )
        context.contentResolver.query(children, arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
        ), null, null, null)!!.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(1) == documentName) {
                    return DocumentsContract.buildDocumentUriUsingTree(directory, cursor.getString(0))
                }
            }
        }
        throw AssertionError("Saved backup $documentName was not found in the backup folder")
    }

    /** Choose a backup from the remembered folder through Android's file picker. */
    private fun openBackupInPicker(device: UiDevice, documentName: String) {
        catchAndDiagnoseFailure {
            composeRule.waitUntil(10_000L) {
                device.hasObject(By.desc(Pattern.compile("(List|Grid) view")))
            }
            // Grid tiles can put filenames below the viewport at large font sizes.
            device.findObject(By.desc("List view"))?.click()
            composeRule.waitUntil(10_000L) {
                device.hasObject(By.text(documentName))
            }
        }
        val document = device.findObject(By.text(documentName))
        assertNotNull("Backup file $documentName was not visible", document)
        assertNotNull(device.findObject(By.text(BACKUP_DIRECTORY_NAME)))
        document.click()
        assertTrue(device.wait(Until.hasObject(By.pkg(composeRule.activity.packageName)), 10_000L))
    }
}

/** Controlled Android provider responses, using real resolver stream and cursor handling. */
@android.annotation.TargetApi(Build.VERSION_CODES.Q)
private class BackupProviderFixture(base: Context) {
    val tree = DocumentsContract.buildTreeDocumentUri("backup.test", "parent")
    val parent = DocumentsContract.buildDocumentUriUsingTree(tree, "parent")
    val children = DocumentsContract.buildChildDocumentsUriUsingTree(tree, "parent")
    val document = DocumentsContract.buildDocumentUriUsingTree(tree, "backup.zip")
    val provider = mock(ContentProvider::class.java)
    val resolver = spy(ContentResolver.wrap(provider))
    val preferences = base.getSharedPreferences("backup-provider-fixture", Context.MODE_PRIVATE)
    val context = object : ContextWrapper(base) {
        override fun getContentResolver(): ContentResolver = resolver
        override fun getSharedPreferences(name: String, mode: Int) = preferences
    }

    init {
        preferences.edit().clear().commit()
        doAnswer { null }.`when`(resolver).takePersistableUriPermission(any(), anyInt())
        doAnswer {
            MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME)).apply { addRow(arrayOf("Parent")) }
        }.`when`(provider).query(eq(parent), any(), any<Bundle>(), any<CancellationSignal>())
        doAnswer {
            MatrixCursor(arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE, DocumentsContract.Document.COLUMN_DOCUMENT_ID))
                .apply { addRow(arrayOf(BACKUP_DIRECTORY_NAME, DocumentsContract.Document.MIME_TYPE_DIR, "backups")) }
        }.`when`(provider).query(eq(children), any(), any<Bundle>(), any<CancellationSignal>())
        rememberFolder()
    }

    fun rememberFolder() {
        preferences.edit().putString("parent", tree.toString()).commit()
        doReturn(listOf(backupPermission(tree,
            Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)))
            .`when`(resolver).persistedUriPermissions
    }
}

/** Build Android's immutable permission value through its public Parcelable interface. */
private fun backupPermission(uri: Uri, flags: Int): UriPermission {
    val parcel = Parcel.obtain()
    return try {
        parcel.writeParcelable(uri, 0)
        parcel.writeInt(flags)
        parcel.writeLong(1L)
        parcel.setDataPosition(0)
        UriPermission.CREATOR.createFromParcel(parcel)
    } finally {
        parcel.recycle()
    }
}

/** Return a selected document without launching a real picker for provider-failure tests. */
private class BackupPickerRegistry(private val document: Uri) : ActivityResultRegistry(), ActivityResultRegistryOwner {
    override val activityResultRegistry: ActivityResultRegistry get() = this

    override fun <I, O> onLaunch(requestCode: Int, contract: ActivityResultContract<I, O>,
        input: I, options: ActivityOptionsCompat?) {
        Handler(Looper.getMainLooper()).post {
            dispatchResult(requestCode, Activity.RESULT_OK, Intent().setData(document))
        }
    }
}
