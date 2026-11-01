package ai.storyteller.pdfcraft.data

import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import kotlin.math.abs

/**
 * Manages reliable offline local persistence of opened PDF documents.
 * Protects against permission expiration when files are picked from cloud providers
 * like Microsoft OneDrive, Google Drive, or messaging apps.
 */
object DocumentStorage {
    private const val STORAGE_DIR = "saved_pdfs"

    fun getStorageDir(context: Context): File {
        return File(context.filesDir, STORAGE_DIR).apply { mkdirs() }
    }

    /**
     * Returns the locally cached file for the given URI string if it exists and is non-empty.
     */
    fun getLocalFile(context: Context, uriString: String): File? {
        val hash = abs(uriString.hashCode())
        val file = File(getStorageDir(context), "pdf_$hash.pdf")
        return if (file.exists() && file.length() > 0L) file else null
    }

    /**
     * Copies the content of the given URI into private app storage to ensure perpetual offline access.
     */
    fun savePdfLocally(context: Context, uri: Uri): File? {
        return try {
            val hash = abs(uri.toString().hashCode())
            val targetFile = File(getStorageDir(context), "pdf_$hash.pdf")
            val tempFile = File(getStorageDir(context), "temp_$hash.tmp")

            val inputStream: InputStream = context.contentResolver.openInputStream(uri) ?: return null
            inputStream.use { input ->
                FileOutputStream(tempFile).use { output ->
                    input.copyTo(output)
                }
            }

            if (tempFile.exists() && tempFile.length() > 0L) {
                if (targetFile.exists()) targetFile.delete()
                if (tempFile.renameTo(targetFile)) {
                    targetFile
                } else {
                    tempFile.copyTo(targetFile, overwrite = true)
                    tempFile.delete()
                    targetFile
                }
            } else {
                tempFile.delete()
                null
            }
        } catch (_: Exception) {
            null
        }
    }

    /**
     * Deletes the local copy when a document is removed from recent list.
     */
    fun deleteLocalCopy(context: Context, uriString: String) {
        try {
            val hash = abs(uriString.hashCode())
            val file = File(getStorageDir(context), "pdf_$hash.pdf")
            if (file.exists()) {
                file.delete()
            }
        } catch (_: Exception) {}
    }
}
