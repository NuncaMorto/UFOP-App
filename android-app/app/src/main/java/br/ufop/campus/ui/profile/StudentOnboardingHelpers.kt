package br.ufop.campus.ui.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.ufop.campus.core.curriculum.UfopCourseCurriculum
import br.ufop.campus.core.curriculum.UfopCurriculumCatalog
import br.ufop.campus.core.curriculum.checkCurriculumCompatibility
import br.ufop.campus.core.ffi.CampusCoreRepository
import br.ufop.campus.ui.theme.DepartmentMathBlue
import br.ufop.campus.ui.theme.StatusAttendingGreen
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

internal val UFOP_CAMPUS_OPTIONS = listOf("Ouro Preto", "Mariana", "João Monlevade")
internal fun resolveUfopCampusOption(rawCampus: String, fallback: String): String {
    val lower = rawCampus.trim().lowercase()
    return when {
        lower.contains("monlevade") || lower.contains("icea") -> "João Monlevade"
        lower.contains("mariana") || lower.contains("ichs") || lower.contains("icsa") -> "Mariana"
        lower.contains("ouro preto") || lower.contains("cruzeiro") -> "Ouro Preto"
        else -> fallback
    }
}
internal fun formatCurriculumHours(hours: Int): String =
    java.text.NumberFormat.getIntegerInstance(java.util.Locale("pt", "BR")).format(hours)
private val SEMESTER_ORDER_REGEX = Regex("(\\d{2,4})[./-](\\d)")
internal fun resolveEarliestSemester(semesters: List<String>): String? {
    return semesters
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .minByOrNull { raw ->
            val match = SEMESTER_ORDER_REGEX.find(raw)
            if (match != null) {
                val rawYear = match.groupValues[1].toIntOrNull() ?: 9999
                val year = if (rawYear < 100) 2000 + rawYear else rawYear
                val sem = match.groupValues[2].toIntOrNull() ?: 9
                year * 10 + sem
            } else {
                Int.MAX_VALUE
            }
        }
}
/**
 * Só permite atualizar automaticamente `totalRequiredHours` pelo catálogo 2026/2
 * quando o estudante NÃO possui incompatibilidade com matriz legada (< 2026/1).
 */
internal fun shouldApplyCatalogRequiredHours(
    repository: CampusCoreRepository,
    registrationNumber: String,
    course: UfopCourseCurriculum
): Boolean {
    val creReport = repository.getAcademicCreReport()
    val earliestSemester = resolveEarliestSemester(creReport.semesters.map { it.semester })
    val resolvedCourse = UfopCurriculumCatalog.resolveCourseForStudent(
        baseCourse = course,
        registrationNumber = registrationNumber,
        earliestTranscriptSemester = earliestSemester
    )
    if (resolvedCourse.chTotal <= 0) return false
    val transcriptCodes = buildSet {
        creReport.semesters.forEach { sem ->
            sem.courses.forEach { entry ->
                val code = entry.code.trim().uppercase()
                if (code.isNotEmpty()) add(code)
            }
        }
    }
    val compatibility = checkCurriculumCompatibility(
        registrationNumber = registrationNumber,
        earliestTranscriptSemester = earliestSemester,
        transcriptCourseCodes = transcriptCodes,
        course = resolvedCourse
    )
    return !compatibility.isLegacyMatrixMismatch
}
@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun UfopCourseSuggestionAndPickerBlock(
    courseName: String,
    campusHint: String,
    catalogCourses: List<UfopCourseCurriculum>,
    identifiedCourse: UfopCourseCurriculum?,
    onSelectCourse: (UfopCourseCurriculum) -> Unit
) {
    var showCatalogDialog by remember { mutableStateOf(false) }

    val resolvedIdentified = identifiedCourse ?: remember(catalogCourses, courseName, campusHint) {
        if (courseName.trim().length >= 3) {
            UfopCurriculumCatalog.findCourse(catalogCourses, courseName, campusHint)
        } else {
            null
        }
    }

    val liveSuggestions = remember(catalogCourses, courseName, resolvedIdentified) {
        val trimmed = courseName.trim()
        if (trimmed.length < 2 || catalogCourses.isEmpty()) {
            emptyList()
        } else {
            UfopCurriculumCatalog.searchCourses(catalogCourses, trimmed)
                .filter { it.code != resolvedIdentified?.code }
                .take(4)
        }
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (resolvedIdentified != null) {
            Surface(
                shape = RoundedCornerShape(999.dp),
                color = StatusAttendingGreen.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, StatusAttendingGreen.copy(alpha = 0.35f))
            ) {
                Text(
                    text = "✓ Matriz identificada: ${resolvedIdentified.code} (${resolvedIdentified.chTotal}h)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = StatusAttendingGreen,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }

        if (liveSuggestions.isNotEmpty()) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                liveSuggestions.forEach { suggestion ->
                    AssistChip(
                        onClick = { onSelectCourse(suggestion) },
                        label = {
                            Text(
                                text = "${suggestion.code} · ${suggestion.name}",
                                style = MaterialTheme.typography.labelSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        },
                        shape = RoundedCornerShape(18.dp)
                    )
                }
            }
        }

        AssistChip(
            onClick = { showCatalogDialog = true },
            enabled = catalogCourses.isNotEmpty(),
            leadingIcon = {
                Icon(
                    imageVector = Icons.Outlined.School,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            },
            label = {
                Text(
                    text = "Selecionar dos ${catalogCourses.size.takeIf { it > 0 } ?: 54} cursos (ref. UFOP)",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            },
            shape = RoundedCornerShape(22.dp)
        )
    }

    if (showCatalogDialog) {
        UfopCourseCatalogPickerDialog(
            courses = catalogCourses,
            selectedCourseCode = resolvedIdentified?.code,
            onDismiss = { showCatalogDialog = false },
            onSelectCourse = { chosen ->
                onSelectCourse(chosen)
                showCatalogDialog = false
            }
        )
    }
}
@Composable
private fun UfopCourseCatalogPickerDialog(
    courses: List<UfopCourseCurriculum>,
    selectedCourseCode: String?,
    onDismiss: () -> Unit,
    onSelectCourse: (UfopCourseCurriculum) -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(courses, query) {
        UfopCurriculumCatalog.searchCourses(courses, query)
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.Outlined.School,
                contentDescription = null,
                tint = DepartmentMathBlue
            )
        },
        title = {
            Text(
                text = "Cursos disponíveis (${courses.size})",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text("Buscar curso, código ou campus...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = null
                        )
                    },
                    trailingIcon = if (query.isNotEmpty()) {
                        {
                            IconButton(onClick = { query = "" }) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = "Limpar"
                                )
                            }
                        }
                    } else {
                        null
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp)
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 180.dp, max = 360.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = filtered,
                        key = { it.code }
                    ) { course ->
                        val isSelected = course.code == selectedCourseCode
                        Surface(
                            onClick = { onSelectCourse(course) },
                            modifier = Modifier.fillMaxWidth(),
                            shape = RoundedCornerShape(14.dp),
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
                            },
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = "${course.code} · ${course.name}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = buildString {
                                            if (course.campus.isNotBlank()) append(course.campus)
                                            if (course.degree.isNotBlank()) {
                                                if (isNotEmpty()) append(" · ")
                                                append(course.degree)
                                            }
                                            if (course.chTotal > 0) {
                                                if (isNotEmpty()) append(" · ")
                                                append("${formatCurriculumHours(course.chTotal)}h")
                                            }
                                        },
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selecionado",
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Fechar")
            }
        }
    )
}
internal fun copyUriToProfileAvatarFile(context: Context, uri: Uri): String? {
    return try {
        val rawBytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: return null
        if (rawBytes.isEmpty()) return null

        val avatarFile = File(context.filesDir, "profile_avatar.jpg")
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, bounds)

        val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
        var sampleSize = 1
        while (maxDim > 0 && (maxDim / sampleSize) > 720) {
            sampleSize *= 2
        }

        val decodeOpts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        val decoded = BitmapFactory.decodeByteArray(rawBytes, 0, rawBytes.size, decodeOpts)
        if (decoded != null) {
            FileOutputStream(avatarFile).use { out ->
                decoded.compress(Bitmap.CompressFormat.JPEG, 90, out)
            }
            decoded.recycle()
        } else {
            avatarFile.writeBytes(rawBytes)
        }
        avatarFile.absolutePath
    } catch (_: Throwable) {
        null
    }
}
internal fun decodeSampledBitmapFromFile(path: String, maxSidePx: Int): ImageBitmap? {
    return try {
        val file = File(path)
        if (!file.exists() || file.length() <= 0L) return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeFile(file.absolutePath, bounds)
        val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
        var sampleSize = 1
        while (maxDim > 0 && (maxDim / (sampleSize * 2)) >= maxSidePx) {
            sampleSize *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        BitmapFactory.decodeFile(file.absolutePath, opts)?.asImageBitmap()
    } catch (_: Throwable) {
        null
    }
}
internal fun decodeSampledBitmapFromUri(context: Context, uri: Uri, maxSidePx: Int): ImageBitmap? {
    return try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        context.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        val maxDim = maxOf(bounds.outWidth, bounds.outHeight)
        var sampleSize = 1
        while (maxDim > 0 && (maxDim / (sampleSize * 2)) >= maxSidePx) {
            sampleSize *= 2
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, opts)
        }?.asImageBitmap()
    } catch (_: Throwable) {
        null
    }
}
private val WHITESPACE_REGEX = Regex("\\s+")
private val avatarBitmapCache: LruCache<String, ImageBitmap> by lazy {
    LruCache<String, ImageBitmap>(8)
}

private val avatarLatestByPath = java.util.concurrent.ConcurrentHashMap<String, ImageBitmap>()

/** Decodifica o avatar em background (<= 384px) com cache em memória (LruCache) sem I/O na main thread. */
@Composable
internal fun rememberAvatarBitmap(avatarPath: String?, revision: Int = 0): ImageBitmap? {
    val cleanPath = avatarPath?.takeIf { it.isNotBlank() }
    val initialCached = remember(cleanPath) {
        cleanPath?.let { avatarLatestByPath[it] }
    }
    val state = produceState<ImageBitmap?>(initialValue = initialCached, key1 = cleanPath, key2 = revision) {
        if (cleanPath == null) {
            avatarLatestByPath.clear()
            value = null
            return@produceState
        }
        val resolved = withContext(Dispatchers.IO) {
            val file = File(cleanPath)
            if (!file.exists() || !file.isFile) {
                avatarLatestByPath.remove(cleanPath)
                return@withContext null
            }
            val cacheKey = "$cleanPath:${file.lastModified()}:${file.length()}:$revision"
            avatarBitmapCache.get(cacheKey)?.let { hit ->
                avatarLatestByPath[cleanPath] = hit
                return@withContext hit
            }
            val decoded = decodeSampledBitmapFromFile(cleanPath, 384)
            if (decoded != null) {
                avatarBitmapCache.put(cacheKey, decoded)
                avatarLatestByPath[cleanPath] = decoded
            }
            decoded
        }
        value = resolved
    }
    return state.value
}
/** Miniatura de print decodificada em background (<= 160px). */
@Composable
internal fun rememberUriThumbnail(uri: Uri, maxSidePx: Int = 160): ImageBitmap? {
    val context = LocalContext.current
    val state = produceState<ImageBitmap?>(initialValue = null, uri) {
        value = withContext(Dispatchers.IO) { decodeSampledBitmapFromUri(context, uri, maxSidePx) }
    }
    return state.value
}
internal fun extractInitials(fullName: String): String {
    val parts = fullName.trim().split(WHITESPACE_REGEX).filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> ""
        parts.size == 1 -> parts.first().take(2).uppercase()
        else -> "${parts.first().first()}${parts.last().first()}".uppercase()
    }
}
internal enum class OnboardingStep(
    val stepNumber: Int,
    val shortLabel: String,
    val title: String,
    val subtitle: String
) {
    PROFILE_AND_ATTESTATION(
        stepNumber = 1,
        shortLabel = "Cadastro & Atestado",
        title = "Bem-vindo ao Vida Acadêmica",
        subtitle = "Importe seu Atestado de Matrícula (PDF), restaure um backup criptografado ou configure seu perfil manualmente."
    ),
    SYLLABUS_UPLOAD(
        stepNumber = 2,
        shortLabel = "Planos de Ensino",
        title = "Planos de Ensino (Opcional)",
        subtitle = "Envie o plano em PDF de cada disciplina para importar docente, critérios e datas de provas automaticamente."
    ),
    PERSONALIZATION_AND_A11Y_QUESTION(
        stepNumber = 3,
        shortLabel = "Personalização",
        title = "Personalize seu aplicativo",
        subtitle = "Escolha entre modo claro ou escuro, defina a cor de destaque e escolha se deseja ajustar a acessibilidade."
    ),
    ACCESSIBILITY_SETUP(
        stepNumber = 4,
        shortLabel = "Acessibilidade",
        title = "Opções de Acessibilidade",
        subtitle = "Ajuste leitor de tela, tamanho de fonte, alto contraste, negrito, redução de movimento e alvos de toque."
    )
}
