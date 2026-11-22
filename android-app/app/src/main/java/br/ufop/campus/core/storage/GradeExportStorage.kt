package br.ufop.campus.core.storage

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.MediaStore
import androidx.core.content.FileProvider
import br.ufop.campus.core.ffi.CampusCoreRepository
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

data class GradeExportResult(
    val displayPath: String,
    val shareUri: Uri?
)

/**
 * Gerenciador seguro de salvamento, leitura e compartilhamento de arquivos `.ufopgrade` (JSON da grade)
 * e `.ufopbak` (backup completo criptografado com AES-256-GCM).
 * Suporta pasta personalizada escolhida via SAF (`OpenDocumentTree`), salvamento direto via `MediaStore`
 * (API 29+) ou armazenamento do app (API 26-28), além de geração de `FileProvider` URI isolada em cache.
 */
object GradeExportStorage {

    const val DEFAULT_FOLDER_LABEL = "Documentos/VidaAcademica/Grades"
    const val DEFAULT_BACKUP_FOLDER_LABEL = "Documentos/VidaAcademica/Backups"

    private const val SHARED_CACHE_DIR = "shared_grades"
    private const val SHARED_BACKUPS_CACHE_DIR = "shared_backups"
    private const val GRADES_SUBFOLDER = "VidaAcademica/Grades"
    private const val BACKUPS_SUBFOLDER = "VidaAcademica/Backups"
    private const val MIME_UFOP_GRADE = "application/octet-stream"
    private const val MIME_JSON_FALLBACK = "application/json"
    private const val MAX_FILENAME_LENGTH = 100
    private const val DEFAULT_READ_MAX_BYTES = 5 * 1024 * 1024

    fun saveGradeBundle(
        context: Context,
        repository: CampusCoreRepository,
        filename: String,
        jsonPayload: String
    ): GradeExportResult {
        return savePayloadInternal(
            context = context,
            repository = repository,
            filename = filename,
            fallbackFilename = "grade_ufop.ufopgrade",
            payload = jsonPayload,
            cacheSubDir = SHARED_CACHE_DIR,
            documentsSubFolder = GRADES_SUBFOLDER,
            defaultFolderLabel = DEFAULT_FOLDER_LABEL
        )
    }

    fun saveEncryptedBackup(
        context: Context,
        repository: CampusCoreRepository,
        filename: String,
        encryptedPayload: String
    ): GradeExportResult {
        return savePayloadInternal(
            context = context,
            repository = repository,
            filename = filename,
            fallbackFilename = "backup_vida_academica.ufopbak",
            payload = encryptedPayload,
            cacheSubDir = SHARED_BACKUPS_CACHE_DIR,
            documentsSubFolder = BACKUPS_SUBFOLDER,
            defaultFolderLabel = DEFAULT_BACKUP_FOLDER_LABEL
        )
    }

    fun readPayloadFromUriSafely(
        context: Context,
        uri: Uri,
        maxBytes: Int = DEFAULT_READ_MAX_BYTES
    ): String {
        require(maxBytes > 0) { "Limite de leitura deve ser positivo." }
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalStateException("Não foi possível abrir o arquivo selecionado.")
        inputStream.use { stream ->
            val out = ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            var totalRead = 0L
            while (true) {
                val read = stream.read(buffer)
                if (read == -1) break
                totalRead += read
                if (totalRead > maxBytes.toLong()) {
                    throw SecurityException(
                        "Arquivo excede o limite seguro de leitura (${maxBytes / (1024 * 1024)} MB)."
                    )
                }
                out.write(buffer, 0, read)
            }
            return out.toByteArray().toString(Charsets.UTF_8)
        }
    }

    fun createShareableFileUri(
        context: Context,
        filename: String,
        jsonPayload: String
    ): Uri {
        return createShareableFileUriInCacheDir(
            context = context,
            filename = filename,
            fallbackFilename = "grade_ufop.ufopgrade",
            payload = jsonPayload,
            cacheSubDir = SHARED_CACHE_DIR
        )
    }

    fun resolveFolderDisplayLabel(
        context: Context,
        treeUri: Uri
    ): String {
        val docId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
        if (!docId.isNullOrBlank()) {
            val formattedFromId = formatTreeDocumentId(docId)
            if (formattedFromId != null) {
                return formattedFromId
            }
            val queriedName = runCatching {
                val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                context.contentResolver.query(
                    docUri,
                    arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                    null,
                    null,
                    null
                )?.use { cursor ->
                    if (cursor.moveToFirst()) cursor.getString(0)?.trim() else null
                }
            }.getOrNull()
            if (!queriedName.isNullOrBlank()) {
                return queriedName
            }
        }
        val segment = treeUri.lastPathSegment
            ?.substringAfterLast(':')
            ?.trim('/')
            ?.takeIf { it.isNotBlank() }
        return segment ?: DEFAULT_FOLDER_LABEL
    }

    private fun savePayloadInternal(
        context: Context,
        repository: CampusCoreRepository,
        filename: String,
        fallbackFilename: String,
        payload: String,
        cacheSubDir: String,
        documentsSubFolder: String,
        defaultFolderLabel: String
    ): GradeExportResult {
        val safeFilename = sanitizeFilename(filename, fallbackFilename)
        val payloadBytes = payload.toByteArray(Charsets.UTF_8)
        val shareUri = runCatching {
            createShareableFileUriInCacheDir(
                context = context,
                filename = safeFilename,
                fallbackFilename = fallbackFilename,
                payload = payload,
                cacheSubDir = cacheSubDir
            )
        }.getOrNull()

        val config = repository.getExportFolderConfig()
        val customTreeUriStr = config.treeUri?.trim()?.takeIf { it.isNotEmpty() }
        if (customTreeUriStr != null) {
            val safResult = runCatching {
                val treeUri = Uri.parse(customTreeUriStr)
                saveToSafTree(context, treeUri, safeFilename, payloadBytes)
                val label = config.displayLabel.trim().ifBlank { resolveFolderDisplayLabel(context, treeUri) }
                GradeExportResult(
                    displayPath = "$label/$safeFilename",
                    shareUri = shareUri
                )
            }.getOrNull()
            if (safResult != null) {
                return safResult
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val mediaStoreResult = runCatching {
                saveToDefaultMediaStore(context, safeFilename, payloadBytes, documentsSubFolder)
                GradeExportResult(
                    displayPath = "$defaultFolderLabel/$safeFilename",
                    shareUri = shareUri
                )
            }.getOrNull()
            if (mediaStoreResult != null) {
                return mediaStoreResult
            }
        }

        val fallbackFile = saveToAppDocumentsFallback(context, safeFilename, payloadBytes, documentsSubFolder)
        return GradeExportResult(
            displayPath = "$defaultFolderLabel/${fallbackFile.name}",
            shareUri = shareUri
        )
    }

    private fun createShareableFileUriInCacheDir(
        context: Context,
        filename: String,
        fallbackFilename: String,
        payload: String,
        cacheSubDir: String
    ): Uri {
        val safeFilename = sanitizeFilename(filename, fallbackFilename)
        val shareDir = File(context.cacheDir, cacheSubDir).apply {
            if (!exists()) mkdirs()
        }
        val shareFile = resolveContainedFile(shareDir, safeFilename)
        FileOutputStream(shareFile).use { out ->
            out.write(payload.toByteArray(Charsets.UTF_8))
            out.flush()
        }
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            shareFile
        )
    }

    private fun resolveContainedFile(parentDir: File, safeFilename: String): File {
        val target = File(parentDir, safeFilename)
        val canonicalParent = parentDir.canonicalPath
        val canonicalTarget = target.canonicalPath
        if (!canonicalTarget.startsWith(canonicalParent + File.separator)) {
            throw SecurityException("Nome de arquivo fora do diretório permitido.")
        }
        return target
    }

    private fun formatTreeDocumentId(docId: String): String? {
        val trimmed = docId.trim()
        if (trimmed.isEmpty()) return null
        if (trimmed.startsWith("primary:", ignoreCase = true)) {
            val rel = trimmed.substringAfter(':').trim('/')
            if (rel.isEmpty()) return "Armazenamento Interno"
            return localizeTopSegment(rel)
        }
        if (trimmed.startsWith("home:", ignoreCase = true)) {
            val rel = trimmed.substringAfter(':').trim('/')
            return if (rel.isEmpty()) "Documentos" else "Documentos/$rel"
        }
        if (trimmed.contains(':')) {
            val rel = trimmed.substringAfter(':').trim('/')
            if (rel.isNotEmpty() && !rel.startsWith("msd:") && rel.length <= 80) {
                return localizeTopSegment(rel)
            }
        }
        return null
    }

    private fun localizeTopSegment(relativePath: String): String = when {
        relativePath.equals("Documents", ignoreCase = true) -> "Documentos"
        relativePath.startsWith("Documents/", ignoreCase = true) ->
            "Documentos/" + relativePath.substring("Documents/".length)
        relativePath.equals("Download", ignoreCase = true) -> "Downloads"
        relativePath.startsWith("Download/", ignoreCase = true) ->
            "Downloads/" + relativePath.substring("Download/".length)
        else -> relativePath
    }

    private fun saveToSafTree(
        context: Context,
        treeUri: Uri,
        safeFilename: String,
        payloadBytes: ByteArray
    ) {
        val resolver = context.contentResolver
        val treeDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val parentDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, treeDocId)
        val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, treeDocId)

        var targetDocUri: Uri? = null
        runCatching {
            resolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME
                ),
                null,
                null,
                null
            )?.use { cursor ->
                while (cursor.moveToNext()) {
                    val existingDocId = cursor.getString(0) ?: continue
                    val displayName = cursor.getString(1) ?: continue
                    if (displayName.equals(safeFilename, ignoreCase = true)) {
                        targetDocUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, existingDocId)
                        break
                    }
                }
            }
        }

        val resolvedDocUri = targetDocUri
            ?: DocumentsContract.createDocument(resolver, parentDocUri, MIME_UFOP_GRADE, safeFilename)
            ?: DocumentsContract.createDocument(resolver, parentDocUri, MIME_JSON_FALLBACK, safeFilename)
            ?: throw IllegalStateException("Não foi possível criar o arquivo na pasta escolhida.")

        val stream = runCatching { resolver.openOutputStream(resolvedDocUri, "wt") }.getOrNull()
            ?: resolver.openOutputStream(resolvedDocUri)
            ?: throw IllegalStateException("Não foi possível abrir o arquivo para escrita.")

        stream.use { out ->
            out.write(payloadBytes)
            out.flush()
        }
    }

    private fun saveToDefaultMediaStore(
        context: Context,
        safeFilename: String,
        payloadBytes: ByteArray,
        documentsSubFolder: String
    ) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            throw UnsupportedOperationException("MediaStore RELATIVE_PATH requer API 29+.")
        }
        val resolver = context.contentResolver
        val collection = MediaStore.Files.getContentUri("external")
        val relativePath = "${Environment.DIRECTORY_DOCUMENTS}/$documentsSubFolder"

        var existingUri: Uri? = null
        val projection = arrayOf(MediaStore.MediaColumns._ID)
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? AND ${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
        val selectionArgs = arrayOf("$relativePath%", safeFilename)
        runCatching {
            resolver.query(collection, projection, selection, selectionArgs, null)?.use { cursor ->
                if (cursor.moveToFirst()) {
                    val id = cursor.getLong(0)
                    existingUri = ContentUris.withAppendedId(collection, id)
                }
            }
        }

        if (existingUri != null) {
            val overwritten = runCatching {
                val out = resolver.openOutputStream(existingUri!!, "wt")
                    ?: resolver.openOutputStream(existingUri!!)
                    ?: return@runCatching false
                out.use {
                    it.write(payloadBytes)
                    it.flush()
                }
                true
            }.getOrDefault(false)
            if (overwritten) return
            runCatching { resolver.delete(existingUri!!, null, null) }
        }

        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, safeFilename)
            put(MediaStore.MediaColumns.MIME_TYPE, MIME_UFOP_GRADE)
            put(MediaStore.MediaColumns.RELATIVE_PATH, relativePath)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val insertedUri = resolver.insert(collection, values)
            ?: throw IllegalStateException("Não foi possível inserir arquivo no MediaStore.")

        try {
            val out = resolver.openOutputStream(insertedUri, "wt")
                ?: resolver.openOutputStream(insertedUri)
                ?: throw IllegalStateException("Não foi possível gravar no MediaStore.")
            out.use {
                it.write(payloadBytes)
                it.flush()
            }
            val publishValues = ContentValues().apply {
                put(MediaStore.MediaColumns.IS_PENDING, 0)
            }
            resolver.update(insertedUri, publishValues, null, null)
        } catch (err: Throwable) {
            runCatching { resolver.delete(insertedUri, null, null) }
            throw err
        }
    }

    private fun saveToAppDocumentsFallback(
        context: Context,
        safeFilename: String,
        payloadBytes: ByteArray,
        documentsSubFolder: String
    ): File {
        val baseDir = context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir
        val outDir = File(baseDir, documentsSubFolder).apply {
            if (!exists()) mkdirs()
        }
        val outFile = resolveContainedFile(outDir, safeFilename)
        FileOutputStream(outFile).use { out ->
            out.write(payloadBytes)
            out.flush()
        }
        return outFile
    }

    internal fun sanitizeFilename(
        raw: String,
        fallbackName: String = "grade_ufop.ufopgrade"
    ): String {
        var cleaned = raw
            .replace(Regex("""[\u0000-\u001F\u007F]"""), "")
            .trim()
            .replace(Regex("""[/\\:*?"<>|]"""), "_")
            .replace(Regex("""\s+"""), "_")

        while (cleaned.contains("..")) {
            cleaned = cleaned.replace("..", "_")
        }

        cleaned = cleaned
            .replace(Regex("""_+"""), "_")
            .trim('_', '.')

        if (cleaned.isBlank() || cleaned == "." || cleaned == "..") {
            return fallbackName
        }

        if (cleaned.length > MAX_FILENAME_LENGTH) {
            val dotIndex = cleaned.lastIndexOf('.')
            cleaned = if (dotIndex in 1 until cleaned.length - 1) {
                val ext = cleaned.substring(dotIndex).take(16)
                val stemMax = (MAX_FILENAME_LENGTH - ext.length).coerceAtLeast(1)
                cleaned.substring(0, dotIndex).take(stemMax).trimEnd('_', '.') + ext
            } else {
                cleaned.take(MAX_FILENAME_LENGTH).trimEnd('_', '.')
            }
        }

        return cleaned.ifBlank { fallbackName }
    }
}
