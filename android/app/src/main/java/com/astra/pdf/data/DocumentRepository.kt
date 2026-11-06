package com.astra.pdf.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.util.Log
import java.io.File
import java.io.InputStream

class DocumentRepository(private val context: Context) {
    companion object {
        private const val TAG = "DocumentRepository"
        private const val MAX_PDF_BYTES = 150L * 1024L * 1024L
    }

    fun readDocumentBytes(uri: Uri): Pair<String, ByteArray> {
        try {
            if (uri.scheme == "content") {
                try {
                    context.contentResolver.takePersistableUriPermission(
                        uri,
                        Intent.FLAG_GRANT_READ_URI_PERMISSION
                    )
                } catch (e: Exception) {
                    Log.d(TAG, "Persistable permission not grantable or not requested: ${e.message}")
                }
            }

            var displayName = "document.pdf"
            var fileSize = 0L

            if (uri.scheme == "content") {
                context.contentResolver.query(uri, null, null, null, null)?.use { cursor ->
                    val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                    if (cursor.moveToFirst()) {
                        if (nameIndex != -1 && !cursor.isNull(nameIndex)) {
                            displayName = cursor.getString(nameIndex)
                        }
                        if (sizeIndex != -1 && !cursor.isNull(sizeIndex)) {
                            fileSize = cursor.getLong(sizeIndex)
                        }
                    }
                }
            } else if (uri.scheme == "file") {
                uri.path?.let { path ->
                    val file = File(path)
                    displayName = file.name
                    fileSize = file.length()
                }
            }

            if (fileSize > MAX_PDF_BYTES) {
                throw IllegalStateException("Document exceeds 150MB limit.")
            }

            val inputStream: InputStream = if (uri.scheme == "file") {
                val path = uri.path ?: throw IllegalStateException("File URI has null path")
                File(path).inputStream()
            } else {
                context.contentResolver.openInputStream(uri)
                    ?: throw IllegalStateException("Could not open input stream for URI: $uri")
            }

            val bytes = inputStream.use { stream ->
                stream.readBytes()
            }

            if (bytes.isEmpty()) {
                throw IllegalStateException("PDF file is empty (0 bytes).")
            }

            return Pair(displayName, bytes)
        } catch (e: Exception) {
            Log.e(TAG, "Error reading document from $uri", e)
            throw e
        }
    }

    fun cleanupSessionFiles() {
        try {
            val sessionDir = File(context.cacheDir, "viewer_session")
            if (sessionDir.exists()) {
                sessionDir.deleteRecursively()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Error cleaning session files", e)
        }
    }
}
