package com.sillycrypt.exfat_android.provider

import android.content.Context
import android.content.res.AssetFileDescriptor
import android.database.Cursor
import android.database.MatrixCursor
import android.graphics.Point
import android.net.Uri
import android.os.CancellationSignal
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.os.storage.StorageManager
import android.provider.DocumentsContract
import android.provider.DocumentsProvider
import android.webkit.MimeTypeMap
import com.dev.exfat.exfat.EXFatVolumesManager
import com.dev.exfat.file.ExFATFile
import com.dev.exfat.file.SeekableOpenOptions
import kotlinx.coroutines.runBlocking
import java.io.FileNotFoundException

class ExFatDocumentsProvider : DocumentsProvider() {

    override fun onCreate(): Boolean = true

    override fun queryRoots(projection: Array<out String>?): Cursor {
        val result = MatrixCursor(resolveRootProjection(projection))
        val volumeIds = EXFatVolumesManager.activeVolumes.value

        for (volumeId in volumeIds) {
            result.newRow().apply {
                add(DocumentsContract.Root.COLUMN_ROOT_ID, volumeId.uuid)
                add(DocumentsContract.Root.COLUMN_DOCUMENT_ID, buildDocumentId(volumeId.uuid, "/"))
                add(DocumentsContract.Root.COLUMN_TITLE, "SillyCrypt")
                add(DocumentsContract.Root.COLUMN_SUMMARY, volumeId.name)
                add(
                    DocumentsContract.Root.COLUMN_FLAGS,
                    DocumentsContract.Root.FLAG_LOCAL_ONLY or
                            DocumentsContract.Root.FLAG_SUPPORTS_IS_CHILD
                )
                add(DocumentsContract.Root.COLUMN_MIME_TYPES, "*/*")
            }
        }

        return result
    }

    override fun queryDocument(documentId: String, projection: Array<out String>?): Cursor {
        val parsed = parseDocumentId(documentId)
        val result = MatrixCursor(resolveDocumentProjection(projection))

        val file = resolveFile(parsed.volumeId, parsed.path)
            ?: throw FileNotFoundException("Document not found: $documentId")

        includeDocument(result, parsed.volumeId, file)
        return result
    }

    override fun queryChildDocuments(
        parentDocumentId: String,
        projection: Array<out String>?,
        sortOrder: String?
    ): Cursor {
        val parsed = parseDocumentId(parentDocumentId)
        val result = MatrixCursor(resolveDocumentProjection(projection))

        val parent = resolveFile(parsed.volumeId, parsed.path)
            ?: throw FileNotFoundException("Parent not found: $parentDocumentId")

        if (!parent.isDirectory) {
            throw FileNotFoundException("Not a directory: $parentDocumentId")
        }

        val children = runBlocking { parent.listFiles() }
            .sortedWith(compareBy<ExFATFile> { !it.isDirectory }.thenBy { it.name.lowercase() })

        for (child in children) {
            includeDocument(result, parsed.volumeId, child)
        }

        return result
    }

    override fun getDocumentType(documentId: String): String {
        val parsed = parseDocumentId(documentId)
        val file = resolveFile(parsed.volumeId, parsed.path)
            ?: throw FileNotFoundException("Document not found: $documentId")

        return if (file.isDirectory) {
            DocumentsContract.Document.MIME_TYPE_DIR
        } else {
            guessMimeType(file.name)
        }
    }

    override fun isChildDocument(parentDocumentId: String, documentId: String): Boolean {
        val parent = parseDocumentId(parentDocumentId)
        val child = parseDocumentId(documentId)

        if (parent.volumeId != child.volumeId) return false
        if (parent.path == "/") return child.path != "/"

        return child.path == parent.path || child.path.startsWith(parent.path.trimEnd('/') + "/")
    }

    override fun openDocument(
        documentId: String,
        mode: String,
        signal: CancellationSignal?
    ): ParcelFileDescriptor {
        val parsed = parseDocumentId(documentId)
        val fs = EXFatVolumesManager.get(parsed.volumeId)
        val options = SeekableOpenOptions.fromAndroidMode(mode)

        val handle = runBlocking {
            fs.openSeekable(parsed.path, options)
        } ?: throw FileNotFoundException("Document not found: $documentId")

        val storageManager = contextOrThrow().getSystemService(StorageManager::class.java)
            ?: throw IllegalStateException("StorageManager not available")

        val callback = ExFatProxyFileDescriptorCallback(handle)

        return storageManager.openProxyFileDescriptor(
            ParcelFileDescriptor.parseMode(mode),
            callback,
            Handler(Looper.getMainLooper())
        )
    }

    override fun querySearchDocuments(
        rootId: String,
        query: String,
        projection: Array<out String>?
    ): Cursor {
        val result = MatrixCursor(resolveDocumentProjection(projection))
        val fs = EXFatVolumesManager.get(rootId)

        fun walk(file: ExFATFile, volumeId: String) {
            if (file.path != "/" && file.name.contains(query, ignoreCase = true)) {
                includeDocument(result, volumeId, file)
            }
            if (file.isDirectory) {
                val children = runBlocking { file.listFiles() }
                for (child in children) walk(child, volumeId)
            }
        }

        val root = runBlocking { fs.root() }
        walk(root, rootId)
        return result
    }

    override fun openDocumentThumbnail(
        documentId: String,
        sizeHint: Point,
        signal: CancellationSignal?
    ): AssetFileDescriptor {
        throw FileNotFoundException("Thumbnails not supported: $documentId")
    }

    private fun includeDocument(
        cursor: MatrixCursor,
        volumeId: String,
        file: ExFATFile
    ) {
        val flags = 0

        cursor.newRow().apply {
            add(DocumentsContract.Document.COLUMN_DOCUMENT_ID, buildDocumentId(volumeId, file.path))
            add(
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                if (file.path == "/") volumeId else file.name
            )
            add(
                DocumentsContract.Document.COLUMN_MIME_TYPE,
                if (file.isDirectory) {
                    DocumentsContract.Document.MIME_TYPE_DIR
                } else {
                    guessMimeType(file.name)
                }
            )
            add(DocumentsContract.Document.COLUMN_SIZE, if (file.isDirectory) null else file.dataLength)
            add(DocumentsContract.Document.COLUMN_FLAGS, flags)
            add(DocumentsContract.Document.COLUMN_LAST_MODIFIED, null)
        }
    }

    private fun resolveFile(volumeId: String, path: String): ExFATFile? {
        val fs = EXFatVolumesManager.get(volumeId)
        return runBlocking {
            if (path == "/") fs.root() else fs.getFileFromPath(path)
        }
    }

    private fun guessMimeType(name: String): String {
        val ext = name.substringAfterLast('.', "").lowercase()
        if (ext.isEmpty()) return "application/octet-stream"
        return MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "application/octet-stream"
    }

    private fun parseDocumentId(documentId: String): ParsedDocumentId {
        val idx = documentId.indexOf(':')
        if (idx <= 0 || idx == documentId.lastIndex) {
            throw FileNotFoundException("Invalid documentId: $documentId")
        }

        val volumeId = documentId.substring(0, idx)
        val rawPath = documentId.substring(idx + 1)
        val normalizedPath = normalizeAbsolutePath(rawPath)

        return ParsedDocumentId(volumeId, normalizedPath)
    }

    private fun buildDocumentId(volumeId: String, absolutePath: String): String {
        return "$volumeId:${normalizeAbsolutePath(absolutePath)}"
    }

    private fun normalizeAbsolutePath(path: String): String {
        if (path.isEmpty() || path == "/") return "/"
        val parts = path.split('/').filter { it.isNotEmpty() }
        return "/" + parts.joinToString("/")
    }

    private fun resolveRootProjection(projection: Array<out String>?): Array<String> {
        return projection?.map { it }?.toTypedArray() ?: DEFAULT_ROOT_PROJECTION
    }

    private fun resolveDocumentProjection(projection: Array<out String>?): Array<String> {
        return projection?.map { it }?.toTypedArray() ?: DEFAULT_DOCUMENT_PROJECTION
    }

    private fun contextOrThrow(): Context = context
        ?: throw IllegalStateException("Provider context is null")

    data class ParsedDocumentId(
        val volumeId: String,
        val path: String
    )

    companion object {
        val DEFAULT_ROOT_PROJECTION = arrayOf(
            DocumentsContract.Root.COLUMN_ROOT_ID,
            DocumentsContract.Root.COLUMN_DOCUMENT_ID,
            DocumentsContract.Root.COLUMN_TITLE,
            DocumentsContract.Root.COLUMN_SUMMARY,
            DocumentsContract.Root.COLUMN_FLAGS,
            DocumentsContract.Root.COLUMN_MIME_TYPES
        )

        val DEFAULT_DOCUMENT_PROJECTION = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_FLAGS,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED
        )

        fun notifyRootsChanged(context: Context, authority: String) {
            context.contentResolver.notifyChange(
                DocumentsContract.buildRootsUri(authority),
                null
            )
        }

        fun buildDocumentUri(authority: String, volumeId: String, absolutePath: String): Uri {
            val documentId = "$volumeId:${absolutePath.ifEmpty { "/" }}"
            return DocumentsContract.buildDocumentUri(authority, documentId)
        }
    }
}