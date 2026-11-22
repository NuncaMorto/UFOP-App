package br.ufop.campus.core.security

import android.content.Context
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.pdmodel.PDDocument
import java.io.ByteArrayInputStream
import java.io.File
import java.io.RandomAccessFile
import java.util.Locale

/**
 * Processador seguro de PDFs acadêmicos (Atestado de Matrícula, Histórico, Planos de Ensino, Calendário):
 * - Carrega documentos exclusivamente em RAM (`MemoryUsageSetting.setupMainMemoryOnly()`), evitando
 *   arquivos temporários de scratch do PDFBox em disco.
 * - Zera o buffer `ByteArray` em memória ao término do processamento (sucesso ou exceção).
 * - Remove resíduos `.pdf` e temporários `pdfbox*` nos diretórios internos do aplicativo sem tocar
 *   em bancos, estado principal, avatar ou fila de feedback.
 */
object SecurePdfProcessor {

    private const val MAX_SECURE_WIPE_BYTES = 2L * 1024L * 1024L

    private val PROTECTED_DIRECTORIES = setOf(
        "feedback_queue",
        "shared_grades",
        "shared_backups"
    )

    private val PROTECTED_FILES = setOf(
        "campus_ufop_state.json",
        "campus_ufop_state.json.bak",
        "campus_ufop_state.json.tmp",
        "profile_avatar.jpg"
    )

    inline fun <T> withZeroedPdfBytes(pdfBytes: ByteArray, block: (ByteArray) -> T): T {
        return try {
            block(pdfBytes)
        } finally {
            pdfBytes.fill(0)
        }
    }

    fun loadDocumentInMemoryOnly(pdfBytes: ByteArray): PDDocument {
        return PDDocument.load(
            ByteArrayInputStream(pdfBytes),
            MemoryUsageSetting.setupMainMemoryOnly()
        )
    }

    fun purgeResidualPdfFiles(context: Context): Int {
        val candidateRoots = listOfNotNull(
            runCatching { context.cacheDir }.getOrNull(),
            runCatching { context.externalCacheDir }.getOrNull(),
            runCatching { context.filesDir }.getOrNull()
        )
        val visitedCanonicalPaths = LinkedHashSet<String>()
        var removedCount = 0

        for (root in candidateRoots) {
            if (!root.exists()) continue
            val canonical = runCatching { root.canonicalPath }.getOrDefault(root.absolutePath)
            if (!visitedCanonicalPaths.add(canonical)) continue
            removedCount += purgeDirectoryRecursively(root)
        }
        return removedCount
    }

    private fun purgeDirectoryRecursively(directory: File): Int {
        val children = runCatching { directory.listFiles() }.getOrNull() ?: return 0
        var removed = 0
        for (entry in children) {
            val lowerName = entry.name.lowercase(Locale.ROOT)
            if (entry.isDirectory) {
                if (lowerName in PROTECTED_DIRECTORIES) continue
                removed += purgeDirectoryRecursively(entry)
                continue
            }
            if (!entry.isFile) continue
            if (isProtectedFile(lowerName)) continue
            if (isResidualPdfOrPdfBoxTemp(lowerName)) {
                if (secureDeleteFile(entry)) {
                    removed++
                }
            }
        }
        return removed
    }

    private fun isProtectedFile(lowerName: String): Boolean {
        if (lowerName in PROTECTED_FILES) return true
        if (lowerName.endsWith(".db") ||
            lowerName.endsWith(".db-wal") ||
            lowerName.endsWith(".db-shm") ||
            lowerName.endsWith(".db-journal")
        ) {
            return true
        }
        return false
    }

    private fun isResidualPdfOrPdfBoxTemp(lowerName: String): Boolean {
        if (lowerName.endsWith(".pdf")) return true
        if (lowerName.startsWith("pdfbox")) return true
        if (lowerName.contains("pdf") && lowerName.endsWith(".tmp")) return true
        return false
    }

    private fun secureDeleteFile(file: File): Boolean {
        runCatching {
            val length = file.length()
            if (length in 1L..MAX_SECURE_WIPE_BYTES) {
                RandomAccessFile(file, "rws").use { raf ->
                    raf.seek(0L)
                    val zeros = ByteArray(minOf(length, 8192L).toInt())
                    var remaining = length
                    while (remaining > 0L) {
                        val toWrite = minOf(remaining, zeros.size.toLong()).toInt()
                        raf.write(zeros, 0, toWrite)
                        remaining -= toWrite
                    }
                }
            }
        }
        return runCatching { file.delete() }.getOrDefault(false)
    }
}
