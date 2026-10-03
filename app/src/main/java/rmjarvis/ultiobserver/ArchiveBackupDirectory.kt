package rmjarvis.ultiobserver

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import androidx.activity.result.contract.ActivityResultContracts
import java.io.FileNotFoundException
import java.io.IOException

internal const val BACKUP_DIRECTORY_NAME = "UltiObserver Backups"

internal val documentsDirectoryUri: Uri = DocumentsContract.buildDocumentUri(
    "com.android.externalstorage.documents", "primary:Documents",
)

/** Device-local folder access; document permissions must not travel in a game backup. */
internal class ArchiveBackupDirectory(private val context: Context) {
    private val resolver = context.contentResolver
    private val preferences = context.getSharedPreferences("archive_backup_location", Context.MODE_PRIVATE)

    /** Reuse the approved folder, or request setup again if its parent or permission disappeared. */
    fun directory(): Uri? {
        val tree = preferences.getString("parent", null)?.let(Uri::parse) ?: return null
        if (resolver.persistedUriPermissions.none {
                it.uri == tree && it.isReadPermission && it.isWritePermission
            }) {
            preferences.edit().remove("parent").apply()
            return null
        }
        return try {
            backupDirectoryIn(tree)
        } catch (_: SecurityException) {
            preferences.edit().remove("parent").apply()
            null
        } catch (_: FileNotFoundException) {
            preferences.edit().remove("parent").apply()
            null
        }
    }

    /** Keep Android's user-approved grant and create or reuse the named backup directory. */
    fun chooseParent(tree: Uri): Uri {
        resolver.takePersistableUriPermission(
            tree, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
        )
        val folder = backupDirectoryIn(tree)
        preferences.edit().putString("parent", tree.toString()).apply()
        return folder
    }

    private fun backupDirectoryIn(tree: Uri): Uri {
        val parent = DocumentsContract.buildDocumentUriUsingTree(
            tree, DocumentsContract.getTreeDocumentId(tree),
        )
        resolver.query(parent, arrayOf(Document.COLUMN_DISPLAY_NAME), null, null, null)?.use {
            if (!it.moveToFirst()) throw FileNotFoundException("The chosen folder no longer exists.")
            if (it.getString(0) == BACKUP_DIRECTORY_NAME) return parent
        } ?: throw FileNotFoundException("The chosen folder is unavailable. Choose it again.")
        val children = DocumentsContract.buildChildDocumentsUriUsingTree(
            tree, DocumentsContract.getTreeDocumentId(tree),
        )
        resolver.query(
            children,
            arrayOf(Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_DOCUMENT_ID),
            null, null, null,
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getString(0) == BACKUP_DIRECTORY_NAME) {
                    if (cursor.getString(1) != Document.MIME_TYPE_DIR) {
                        throw IOException("A file named $BACKUP_DIRECTORY_NAME already exists in this folder.")
                    }
                    return DocumentsContract.buildDocumentUriUsingTree(tree, cursor.getString(2))
                }
            }
        } ?: throw IOException("The chosen folder could not be read.")
        return DocumentsContract.createDocument(resolver, parent, Document.MIME_TYPE_DIR, BACKUP_DIRECTORY_NAME)
            ?: throw IOException("The backup folder could not be created.")
    }
}

internal fun createBackupDocumentIntent(context: Context, filename: String, directory: Uri): Intent =
    ActivityResultContracts.CreateDocument("application/zip").createIntent(context, filename)
        .putExtra(DocumentsContract.EXTRA_INITIAL_URI, directory)

internal fun openBackupDocumentIntent(context: Context, directory: Uri): Intent =
    ActivityResultContracts.OpenDocument()
        .createIntent(context, arrayOf("application/zip", "application/octet-stream"))
        .putExtra(DocumentsContract.EXTRA_INITIAL_URI, directory)
