package br.ufop.campus.ui.profile

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.AccessibilityNew
import androidx.compose.material.icons.outlined.AddAPhoto
import androidx.compose.material.icons.outlined.Badge
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.DarkMode
import androidx.compose.material.icons.outlined.Description
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material.icons.outlined.LockReset
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.Person
import androidx.compose.material.icons.outlined.PictureAsPdf
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.UploadFile
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material.icons.outlined.WarningAmber
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import br.ufop.campus.core.curriculum.UfopCourseCurriculum
import br.ufop.campus.core.curriculum.UfopCurriculumCatalog
import br.ufop.campus.core.curriculum.checkCurriculumCompatibility
import br.ufop.campus.core.ffi.CampusCoreRepository
import br.ufop.campus.core.ffi.CampusRepositoryHolder
import br.ufop.campus.core.ffi.DisciplineDetail
import br.ufop.campus.core.ffi.DisciplineSummaryCard
import br.ufop.campus.core.ffi.SyllabusImportReport
import br.ufop.campus.core.ffi.normalizeUfopPortugueseText
import br.ufop.campus.core.security.CampusCryptoEngine
import br.ufop.campus.core.security.SecurePdfProcessor
import br.ufop.campus.core.storage.GradeExportStorage
import br.ufop.campus.ui.theme.CampusAccentColor
import br.ufop.campus.ui.theme.DepartmentMathBlue
import br.ufop.campus.ui.theme.LocalCampusAccessibility
import br.ufop.campus.ui.theme.LocalCampusThemeController
import br.ufop.campus.ui.theme.StatusAbsentRed
import br.ufop.campus.ui.theme.StatusAttendingGreen
import br.ufop.campus.ui.theme.StatusCancelledAmber
import br.ufop.campus.ui.theme.parseHexColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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

/** Decodifica o avatar em background (<= 384px) sem bloquear a thread principal. */
@Composable
internal fun rememberAvatarBitmap(avatarPath: String?, revision: Int = 0): ImageBitmap? {
    val state = produceState<ImageBitmap?>(initialValue = null, avatarPath, revision) {
        value = if (avatarPath.isNullOrBlank()) {
            null
        } else {
            withContext(Dispatchers.IO) { decodeSampledBitmapFromFile(avatarPath, 384) }
        }
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
    val parts = fullName.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
    return when {
        parts.isEmpty() -> ""
        parts.size == 1 -> parts.first().take(2).uppercase()
        else -> "${parts.first().first()}${parts.last().first()}".uppercase()
    }
}

private enum class OnboardingStep(
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun StudentOnboardingOverlay(
    repository: CampusCoreRepository,
    onCompleted: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val systemDark = isSystemInDarkTheme()
    val themeController = LocalCampusThemeController.current
    val accessibilityController = LocalCampusAccessibility.current
    val a11y = accessibilityController.settings
    val buttonMinHeight = if (a11y.largeTouchTargets) 56.dp else 50.dp

    val initialProfile = remember { repository.getStudentProfile() }

    var currentStep by rememberSaveable { mutableStateOf(OnboardingStep.PROFILE_AND_ATTESTATION) }

    var fullName by rememberSaveable { mutableStateOf(initialProfile.fullName) }
    var courseName by rememberSaveable {
        mutableStateOf(
            initialProfile.courseName.trim().let {
                if (it.isBlank()) "" else normalizeUfopPortugueseText(it)
            }
        )
    }
    var registrationNumber by rememberSaveable { mutableStateOf(initialProfile.registrationNumber) }
    var selectedCampus by rememberSaveable {
        mutableStateOf(
            UFOP_CAMPUS_OPTIONS.find {
                it.equals(initialProfile.campusName, ignoreCase = true)
            } ?: "Ouro Preto"
        )
    }
    var avatarPath by rememberSaveable { mutableStateOf(initialProfile.avatarPath) }
    var avatarRevision by remember { mutableIntStateOf(0) }
    var photoSkippedHint by remember { mutableStateOf(false) }
    var importedCoursesCount by rememberSaveable { mutableIntStateOf(0) }

    var isImportingPdf by remember { mutableStateOf(false) }
    var statusMessage by remember { mutableStateOf<String?>(null) }
    var isErrorStatus by remember { mutableStateOf(false) }
    var identifiedCourse by remember { mutableStateOf<UfopCourseCurriculum?>(null) }

    // Estado de restauração de backup criptografado (.ufopbackup)
    var pendingBackupUri by remember { mutableStateOf<Uri?>(null) }
    var backupPassword by remember { mutableStateOf("") }
    var backupPasswordVisible by remember { mutableStateOf(false) }
    var isRestoringBackup by remember { mutableStateOf(false) }
    var backupErrorMessage by remember { mutableStateOf<String?>(null) }

    // Estado da etapa 2 (Planos de Ensino por disciplina)
    var syllabusRefreshTick by remember { mutableIntStateOf(0) }
    val syllabusReports = remember { mutableStateMapOf<String, SyllabusImportReport>() }
    var activeSyllabusCourseId by remember { mutableStateOf<String?>(null) }
    var uploadingSyllabusCourseId by remember { mutableStateOf<String?>(null) }

    val catalogCourses by produceState<List<UfopCourseCurriculum>>(initialValue = emptyList()) {
        value = withContext(Dispatchers.IO) { UfopCurriculumCatalog.getAllCourses(context) }
    }

    val avatarBitmap = rememberAvatarBitmap(avatarPath, avatarRevision)

    fun goToPreviousStep() {
        currentStep = when (currentStep) {
            OnboardingStep.PROFILE_AND_ATTESTATION -> OnboardingStep.PROFILE_AND_ATTESTATION
            OnboardingStep.SYLLABUS_UPLOAD -> OnboardingStep.PROFILE_AND_ATTESTATION
            OnboardingStep.PERSONALIZATION_AND_A11Y_QUESTION -> OnboardingStep.SYLLABUS_UPLOAD
            OnboardingStep.ACCESSIBILITY_SETUP -> OnboardingStep.PERSONALIZATION_AND_A11Y_QUESTION
        }
    }

    BackHandler(enabled = currentStep != OnboardingStep.PROFILE_AND_ATTESTATION) {
        goToPreviousStep()
    }

    fun saveDraftProfileAndAdvance() {
        val normalizedCourse = courseName.trim().let {
            if (it.isBlank()) "" else normalizeUfopPortugueseText(it)
        }
        val matched = identifiedCourse ?: UfopCurriculumCatalog.findCourse(
            courses = catalogCourses,
            courseNameOrCode = normalizedCourse,
            campusHint = selectedCampus
        )
        val trimmedReg = registrationNumber.trim()
        val earliestSemester = resolveEarliestSemester(
            repository.getAcademicCreReport().semesters.map { it.semester }
        )
        val resolvedCourse = matched?.let {
            UfopCurriculumCatalog.resolveCourseForStudent(
                baseCourse = it,
                registrationNumber = trimmedReg,
                earliestTranscriptSemester = earliestSemester
            )
        }
        if (resolvedCourse != null && shouldApplyCatalogRequiredHours(
                repository = repository,
                registrationNumber = trimmedReg,
                course = resolvedCourse
            )
        ) {
            repository.setTotalRequiredHours(resolvedCourse.chTotal)
        }
        val latest = repository.getStudentProfile()
        val draftProfile = latest.copy(
            fullName = fullName.trim(),
            registrationNumber = trimmedReg,
            courseName = normalizedCourse,
            campusName = selectedCampus,
            activeSemester = latest.activeSemester.ifBlank { "2026.2" },
            semesterStartDate = latest.semesterStartDate.ifBlank { "2026-08-24" },
            semesterEndDate = latest.semesterEndDate.ifBlank { "2026-12-23" },
            avatarPath = avatarPath ?: latest.avatarPath,
            onboardingCompleted = false
        )
        repository.saveStudentProfile(draftProfile)
        syllabusRefreshTick += 1
        currentStep = OnboardingStep.SYLLABUS_UPLOAD
    }

    fun finishOnboardingAndStartTour() {
        val latest = repository.getStudentProfile()
        repository.saveStudentProfile(latest.copy(onboardingCompleted = true))
        CampusRepositoryHolder.setLastSeenReleaseVersion(
            context = context,
            repository = repository,
            version = CampusRepositoryHolder.CURRENT_APP_VERSION
        )
        CampusRepositoryHolder.setGuidedTourCompleted(
            context = context,
            repository = repository,
            completed = false
        )
        onCompleted()
    }

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        if (uri != null) {
            val savedPath = copyUriToProfileAvatarFile(context, uri)
            if (savedPath != null) {
                avatarPath = savedPath
                avatarRevision += 1
                photoSkippedHint = false
                isErrorStatus = false
                statusMessage = null
            } else {
                isErrorStatus = true
                statusMessage = "Não foi possível carregar a imagem selecionada."
            }
        }
    }

    val pdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        isImportingPdf = true
        isErrorStatus = false
        statusMessage = null

        scope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    runCatching {
                        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                            ?: throw IllegalStateException("Não foi possível abrir o arquivo PDF.")
                        val importedCount = SecurePdfProcessor.withZeroedPdfBytes(bytes) { safeBytes ->
                            repository.importEnrollmentCertificatePdf(safeBytes, completeOnboarding = false)
                        }
                        if (importedCount <= 0) {
                            throw IllegalStateException(
                                "Nenhuma disciplina encontrada. Verifique se o arquivo é um Atestado de Matrícula da UFOP."
                            )
                        }
                        val importedProfile = repository.getStudentProfile()
                        val currentAvatar = avatarPath
                        if (!currentAvatar.isNullOrBlank() && importedProfile.avatarPath != currentAvatar) {
                            repository.saveStudentProfile(importedProfile.copy(avatarPath = currentAvatar))
                        }
                        val matchedCurriculum = UfopCurriculumCatalog.findCourse(
                            context = context,
                            courseNameOrCode = importedProfile.courseName,
                            campusHint = importedProfile.campusName
                        )?.let { found ->
                            val earliestSem = resolveEarliestSemester(
                                repository.getAcademicCreReport().semesters.map { it.semester }
                            )
                            UfopCurriculumCatalog.resolveCourseForStudent(
                                baseCourse = found,
                                registrationNumber = importedProfile.registrationNumber,
                                earliestTranscriptSemester = earliestSem
                            )
                        }
                        if (matchedCurriculum != null && shouldApplyCatalogRequiredHours(
                                repository = repository,
                                registrationNumber = importedProfile.registrationNumber,
                                course = matchedCurriculum
                            )
                        ) {
                            repository.setTotalRequiredHours(matchedCurriculum.chTotal)
                        }
                        importedCount to matchedCurriculum
                    }
                } finally {
                    runCatching { SecurePdfProcessor.purgeResidualPdfFiles(context) }
                }
            }
            isImportingPdf = false
            result.onSuccess { (count, matchedCurriculum) ->
                val imported = repository.getStudentProfile()
                val currentAvatar = avatarPath
                if (!currentAvatar.isNullOrBlank() && imported.avatarPath != currentAvatar) {
                    repository.saveStudentProfile(imported.copy(avatarPath = currentAvatar))
                }
                if (imported.fullName.isNotBlank()) fullName = imported.fullName
                if (imported.courseName.isNotBlank()) {
                    courseName = normalizeUfopPortugueseText(imported.courseName.trim())
                }
                if (imported.registrationNumber.isNotBlank()) {
                    registrationNumber = imported.registrationNumber
                }
                UFOP_CAMPUS_OPTIONS.find { it.equals(imported.campusName, ignoreCase = true) }
                    ?.let { selectedCampus = it }
                identifiedCourse = matchedCurriculum
                importedCoursesCount = count
                syllabusRefreshTick += 1
                isErrorStatus = false
                statusMessage = "Atestado importado ($count disciplinas, incluindo detecção automática de EAD)! Revise seus dados e toque em \"Continuar\"."
            }.onFailure { error ->
                isErrorStatus = true
                statusMessage = error.message ?: "Erro ao ler o Atestado de Matrícula (PDF)."
            }
        }
    }

    val backupPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            pendingBackupUri = uri
            backupPassword = ""
            backupPasswordVisible = false
            backupErrorMessage = null
        }
    }

    val syllabusPdfPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        val targetCourseId = activeSyllabusCourseId
        if (uri == null || targetCourseId.isNullOrBlank()) return@rememberLauncherForActivityResult
        uploadingSyllabusCourseId = targetCourseId

        scope.launch {
            val report = withContext(Dispatchers.IO) {
                try {
                    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
                        ?: return@withContext SyllabusImportReport(
                            importedCount = 0,
                            matchedCourse = false,
                            errorMessage = "Não foi possível abrir o arquivo PDF selecionado."
                        )
                    runCatching {
                        SecurePdfProcessor.withZeroedPdfBytes(bytes) { safeBytes ->
                            repository.importCourseSyllabusDetailedPdf(targetCourseId, safeBytes)
                        }
                    }.getOrElse { err ->
                        SyllabusImportReport(
                            importedCount = 0,
                            matchedCourse = false,
                            errorMessage = err.message ?: "Erro ao ler o Plano de Ensino (PDF)."
                        )
                    }
                } finally {
                    runCatching { SecurePdfProcessor.purgeResidualPdfFiles(context) }
                }
            }
            syllabusReports[targetCourseId] = report
            uploadingSyllabusCourseId = null
            syllabusRefreshTick += 1
        }
    }

    val activeSemester = remember(currentStep, syllabusRefreshTick) {
        repository.getStudentProfile().activeSemester.ifBlank { "2026.2" }
    }
    val semesterDisciplines = remember(activeSemester, currentStep, syllabusRefreshTick) {
        if (currentStep == OnboardingStep.SYLLABUS_UPLOAD) {
            repository.listDisciplines(activeSemester, null)
        } else {
            emptyList()
        }
    }
    val disciplineDetailsById = remember(semesterDisciplines, syllabusRefreshTick) {
        semesterDisciplines.associate { card ->
            card.courseId to runCatching { repository.getDisciplineDetail(card.courseId) }.getOrNull()
        }
    }

    Surface(
        modifier = modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
        ) {
            // Barra superior de progresso do Wizard (4 etapas)
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.surface,
                tonalElevation = 2.dp
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            if (currentStep != OnboardingStep.PROFILE_AND_ATTESTATION) {
                                IconButton(
                                    onClick = { goToPreviousStep() },
                                    modifier = Modifier.semantics {
                                        contentDescription = "Voltar para etapa anterior"
                                    }
                                ) {
                                    Icon(
                                        imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                            Column {
                                Text(
                                    text = "Etapa ${currentStep.stepNumber} de 4",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                Text(
                                    text = currentStep.shortLabel,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }

                        Surface(
                            shape = RoundedCornerShape(999.dp),
                            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)
                        ) {
                            Text(
                                text = "INDEPENDENTE",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp)
                            )
                        }
                    }

                    LinearProgressIndicator(
                        progress = { currentStep.stepNumber / 4f },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(999.dp)),
                        color = MaterialTheme.colorScheme.primary,
                        trackColor = MaterialTheme.colorScheme.surfaceVariant
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Corpo rolável da etapa ativa
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                contentAlignment = Alignment.TopCenter
            ) {
                Column(
                    modifier = Modifier
                        .widthIn(max = 560.dp)
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 18.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = currentStep.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.semantics { heading() }
                    )
                    Text(
                        text = currentStep.subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center
                    )
                }

                when (currentStep) {
                    OnboardingStep.PROFILE_AND_ATTESTATION -> {
                        OnboardingStep1ProfileAndAttestation(
                            fullName = fullName,
                            onFullNameChange = { fullName = it },
                            courseName = courseName,
                            onCourseNameChange = {
                                courseName = it
                                identifiedCourse = null
                            },
                            registrationNumber = registrationNumber,
                            onRegistrationNumberChange = { registrationNumber = it },
                            selectedCampus = selectedCampus,
                            onSelectCampus = { selectedCampus = it },
                            avatarBitmap = avatarBitmap,
                            photoSkippedHint = photoSkippedHint,
                            isImportingPdf = isImportingPdf,
                            statusMessage = statusMessage,
                            isErrorStatus = isErrorStatus,
                            catalogCourses = catalogCourses,
                            identifiedCourse = identifiedCourse,
                            onSelectCatalogCourse = { chosen ->
                                courseName = normalizeUfopPortugueseText(chosen.name)
                                selectedCampus = resolveUfopCampusOption(chosen.campus, selectedCampus)
                                identifiedCourse = chosen
                            },
                            onPickPhoto = { imagePickerLauncher.launch("image/*") },
                            onSkipPhoto = {
                                avatarPath = null
                                avatarRevision += 1
                                photoSkippedHint = true
                            },
                            onImportAttestationPdf = {
                                pdfPickerLauncher.launch(arrayOf("application/pdf"))
                            },
                            onRestoreBackup = {
                                backupPickerLauncher.launch(arrayOf("*/*"))
                            },
                            onContinue = { saveDraftProfileAndAdvance() },
                            buttonMinHeight = buttonMinHeight
                        )
                    }

                    OnboardingStep.SYLLABUS_UPLOAD -> {
                        OnboardingStep2SyllabusUpload(
                            disciplines = semesterDisciplines,
                            disciplineDetailsById = disciplineDetailsById,
                            syllabusReports = syllabusReports,
                            uploadingCourseId = uploadingSyllabusCourseId,
                            onUploadSyllabus = { course ->
                                activeSyllabusCourseId = course.courseId
                                syllabusPdfPickerLauncher.launch(arrayOf("application/pdf"))
                            },
                            onBack = { goToPreviousStep() },
                            onSkipOrContinue = {
                                currentStep = OnboardingStep.PERSONALIZATION_AND_A11Y_QUESTION
                            },
                            buttonMinHeight = buttonMinHeight
                        )
                    }

                    OnboardingStep.PERSONALIZATION_AND_A11Y_QUESTION -> {
                        OnboardingStep3PersonalizationAndA11y(
                            isDark = themeController.isDark,
                            selectedAccent = themeController.accentColor,
                            onSelectDarkMode = { wantDark ->
                                if (themeController.isDark != wantDark) {
                                    themeController.toggleTheme()
                                }
                            },
                            onSelectAccent = { accent ->
                                themeController.selectAccentColor(accent)
                            },
                            onConfigureAccessibility = {
                                currentStep = OnboardingStep.ACCESSIBILITY_SETUP
                            },
                            onFinishWithoutAccessibility = {
                                finishOnboardingAndStartTour()
                            },
                            onBack = { goToPreviousStep() },
                            buttonMinHeight = buttonMinHeight
                        )
                    }

                    OnboardingStep.ACCESSIBILITY_SETUP -> {
                        OnboardingStep4AccessibilitySetup(
                            onBack = { goToPreviousStep() },
                            onFinish = { finishOnboardingAndStartTour() },
                            buttonMinHeight = buttonMinHeight
                        )
                    }
                }

                    Spacer(modifier = Modifier.height(10.dp))
                }
            }
        }
    }

    // Diálogo de senha para restauração de backup criptografado (.ufopbackup)
    val backupUri = pendingBackupUri
    if (backupUri != null) {
        AlertDialog(
            onDismissRequest = {
                if (!isRestoringBackup) {
                    pendingBackupUri = null
                    backupPassword = ""
                    backupErrorMessage = null
                }
            },
            icon = {
                Icon(
                    imageVector = Icons.Outlined.LockReset,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary
                )
            },
            title = {
                Text(
                    text = "Restaurar backup criptografado",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = "Digite a senha usada ao criar o arquivo .ufopbackup para restaurar seu perfil, disciplinas, histórico e preferências.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = backupPassword,
                        onValueChange = {
                            backupPassword = it
                            backupErrorMessage = null
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text("Senha do backup") },
                        placeholder = { Text("Digite a senha") },
                        singleLine = true,
                        enabled = !isRestoringBackup,
                        isError = backupErrorMessage != null,
                        visualTransformation = if (backupPasswordVisible) {
                            VisualTransformation.None
                        } else {
                            PasswordVisualTransformation()
                        },
                        trailingIcon = {
                            IconButton(onClick = { backupPasswordVisible = !backupPasswordVisible }) {
                                Icon(
                                    imageVector = if (backupPasswordVisible) {
                                        Icons.Outlined.VisibilityOff
                                    } else {
                                        Icons.Outlined.Visibility
                                    },
                                    contentDescription = if (backupPasswordVisible) {
                                        "Ocultar senha"
                                    } else {
                                        "Mostrar senha"
                                    }
                                )
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                        shape = RoundedCornerShape(14.dp)
                    )
                    if (!backupErrorMessage.isNullOrBlank()) {
                        Text(
                            text = backupErrorMessage!!,
                            style = MaterialTheme.typography.bodySmall,
                            color = StatusAbsentRed
                        )
                    }
                }
            },
            confirmButton = {
                Button(
                    enabled = !isRestoringBackup && backupPassword.isNotBlank(),
                    onClick = {
                        isRestoringBackup = true
                        backupErrorMessage = null
                        val passwordAttempt = backupPassword
                        scope.launch {
                            val restoreResult = withContext(Dispatchers.IO) {
                                runCatching {
                                    val rawPayload = GradeExportStorage.readPayloadFromUriSafely(
                                        context = context,
                                        uri = backupUri
                                    )
                                    val chars = passwordAttempt.toCharArray()
                                    val decryptedJson = try {
                                        CampusCryptoEngine.decryptBackupPayload(rawPayload, chars)
                                    } finally {
                                        chars.fill('\u0000')
                                    }
                                    val restored = repository.restoreFullAccountSnapshotJson(decryptedJson)
                                    if (!restored) {
                                        throw IllegalStateException(
                                            "Não foi possível aplicar o backup selecionado."
                                        )
                                    }
                                }
                            }
                            isRestoringBackup = false
                            restoreResult.onSuccess {
                                // Sincroniza preferências restauradas no CampusRepositoryHolder e tema/a11y ativos
                                val restoredDark = when (repository.getDarkThemePreference().trim().uppercase()) {
                                    "DARK" -> true
                                    "LIGHT" -> false
                                    else -> systemDark
                                }
                                CampusRepositoryHolder.saveDarkTheme(context, repository, restoredDark)
                                if (themeController.isDark != restoredDark) {
                                    themeController.toggleTheme()
                                }

                                val restoredAccent = CampusAccentColor.fromKey(
                                    repository.getAccentColorPreference()
                                )
                                CampusRepositoryHolder.saveAccentColor(
                                    context,
                                    repository,
                                    restoredAccent.storageKey
                                )
                                themeController.selectAccentColor(restoredAccent)

                                val restoredA11y = repository.getAccessibilitySettings()
                                CampusRepositoryHolder.saveAccessibilitySettings(
                                    context,
                                    repository,
                                    restoredA11y
                                )
                                accessibilityController.updateSettings(restoredA11y)

                                CampusRepositoryHolder.setGuidedTourCompleted(
                                    context,
                                    repository,
                                    repository.isGuidedTourCompleted()
                                )
                                val restoredRelease = repository.getLastSeenReleaseVersion()
                                    .ifBlank { CampusRepositoryHolder.CURRENT_APP_VERSION }
                                CampusRepositoryHolder.setLastSeenReleaseVersion(
                                    context,
                                    repository,
                                    restoredRelease
                                )

                                val restoredProfile = repository.getStudentProfile()
                                if (!restoredProfile.onboardingCompleted) {
                                    repository.saveStudentProfile(
                                        restoredProfile.copy(onboardingCompleted = true)
                                    )
                                }

                                pendingBackupUri = null
                                backupPassword = ""
                                backupErrorMessage = null
                                onCompleted()
                            }.onFailure { err ->
                                backupErrorMessage = err.message
                                    ?: "Senha incorreta ou arquivo de backup inválido."
                            }
                        }
                    }
                ) {
                    if (isRestoringBackup) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(16.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Restaurando...")
                    } else {
                        Text("Restaurar conta")
                    }
                }
            },
            dismissButton = {
                TextButton(
                    enabled = !isRestoringBackup,
                    onClick = {
                        pendingBackupUri = null
                        backupPassword = ""
                        backupErrorMessage = null
                    }
                ) {
                    Text("Cancelar")
                }
            }
        )
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OnboardingStep1ProfileAndAttestation(
    fullName: String,
    onFullNameChange: (String) -> Unit,
    courseName: String,
    onCourseNameChange: (String) -> Unit,
    registrationNumber: String,
    onRegistrationNumberChange: (String) -> Unit,
    selectedCampus: String,
    onSelectCampus: (String) -> Unit,
    avatarBitmap: ImageBitmap?,
    photoSkippedHint: Boolean,
    isImportingPdf: Boolean,
    statusMessage: String?,
    isErrorStatus: Boolean,
    catalogCourses: List<UfopCourseCurriculum>,
    identifiedCourse: UfopCourseCurriculum?,
    onSelectCatalogCourse: (UfopCourseCurriculum) -> Unit,
    onPickPhoto: () -> Unit,
    onSkipPhoto: () -> Unit,
    onImportAttestationPdf: () -> Unit,
    onRestoreBackup: () -> Unit,
    onContinue: () -> Unit,
    buttonMinHeight: androidx.compose.ui.unit.Dp
) {
    // Seletor circular de foto de perfil (96.dp Circle) + botão "Pular foto"
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(contentAlignment = Alignment.BottomEnd) {
            Surface(
                modifier = Modifier
                    .size(96.dp)
                    .clip(CircleShape)
                    .clickable(enabled = !isImportingPdf, onClick = onPickPhoto),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primaryContainer,
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.45f))
            ) {
                if (avatarBitmap != null) {
                    Image(
                        bitmap = avatarBitmap,
                        contentDescription = "Foto de perfil selecionada",
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        val initials = extractInitials(fullName)
                        if (initials.isNotEmpty()) {
                            Text(
                                text = initials,
                                style = MaterialTheme.typography.headlineSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Outlined.Person,
                                contentDescription = "Selecionar foto",
                                tint = MaterialTheme.colorScheme.onPrimaryContainer,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                    }
                }
            }

            Surface(
                modifier = Modifier.size(30.dp),
                shape = CircleShape,
                color = MaterialTheme.colorScheme.primary,
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.background)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Outlined.AddAPhoto,
                        contentDescription = "Escolher foto",
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(15.dp)
                    )
                }
            }
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(
                onClick = onPickPhoto,
                enabled = !isImportingPdf,
                shape = RoundedCornerShape(22.dp)
            ) {
                Text(
                    text = if (avatarBitmap != null) "Trocar foto" else "Escolher foto",
                    style = MaterialTheme.typography.labelLarge,
                    maxLines = 1,
                    softWrap = false
                )
            }

            TextButton(
                onClick = onSkipPhoto,
                enabled = !isImportingPdf,
                shape = RoundedCornerShape(22.dp)
            ) {
                Text(
                    text = "Pular foto",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    softWrap = false
                )
            }
        }

        if (photoSkippedHint && avatarBitmap == null) {
            Text(
                text = "Foto opcional pulada · você pode adicionar depois em Perfil.",
                style = MaterialTheme.typography.labelSmall,
                color = StatusAttendingGreen
            )
        }
    }

    // Atalho Rápido — Importar Atestado de Matrícula (PDF)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = DepartmentMathBlue.copy(alpha = 0.08f),
        border = BorderStroke(1.dp, DepartmentMathBlue.copy(alpha = 0.30f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(
                text = "ATALHO · ATESTADO DE MATRÍCULA",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Bold,
                color = DepartmentMathBlue
            )
            Text(
                text = "Compatível com o Atestado de Matrícula em PDF (ex.: Minha UFOP) para preencher nome, matrícula, curso, disciplinas, prédios e grade horária. Disciplinas EAD são detectadas automaticamente e nenhum arquivo PDF fica salvo no aparelho.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FilledTonalButton(
                onClick = onImportAttestationPdf,
                enabled = !isImportingPdf,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = buttonMinHeight),
                shape = RoundedCornerShape(20.dp),
                colors = ButtonDefaults.filledTonalButtonColors(
                    containerColor = DepartmentMathBlue,
                    contentColor = Color.White
                )
            ) {
                if (isImportingPdf) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(18.dp),
                        strokeWidth = 2.dp,
                        color = Color.White
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Lendo atestado em PDF...",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                } else {
                    Icon(
                        imageVector = Icons.Outlined.PictureAsPdf,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Importar Atestado de Matrícula (PDF)",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }

    // Card discreto — Restaurar backup criptografado (.ufopbackup)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = !isImportingPdf, onClick = onRestoreBackup)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Outlined.LockReset,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer,
                    modifier = Modifier.size(20.dp)
                )
            }
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(
                    text = "Restaurar backup criptografado (.ufopbackup)",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = "Já tinha uma conta? Recupere todos os seus dados com sua senha.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(18.dp)
            )
        }
    }

    if (statusMessage != null) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = if (isErrorStatus) {
                StatusAbsentRed.copy(alpha = 0.12f)
            } else {
                StatusAttendingGreen.copy(alpha = 0.12f)
            }
        ) {
            Text(
                text = statusMessage,
                style = MaterialTheme.typography.bodySmall,
                color = if (isErrorStatus) StatusAbsentRed else StatusAttendingGreen,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant
        )
        Text(
            text = "ou preencha manualmente",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        HorizontalDivider(
            modifier = Modifier.weight(1f),
            color = MaterialTheme.colorScheme.outlineVariant
        )
    }

    // Campos manuais de cadastro
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = fullName,
                onValueChange = onFullNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Nome completo") },
                placeholder = { Text("Ex.: Ana Carolina Silva") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Person,
                        contentDescription = null
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(18.dp)
            )

            OutlinedTextField(
                value = courseName,
                onValueChange = onCourseNameChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Curso") },
                placeholder = { Text("Ex.: Engenharia de Controle e Automação") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.School,
                        contentDescription = null
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(18.dp)
            )

            UfopCourseSuggestionAndPickerBlock(
                courseName = courseName,
                campusHint = selectedCampus,
                catalogCourses = catalogCourses,
                identifiedCourse = identifiedCourse,
                onSelectCourse = onSelectCatalogCourse
            )

            OutlinedTextField(
                value = registrationNumber,
                onValueChange = onRegistrationNumberChange,
                modifier = Modifier.fillMaxWidth(),
                label = { Text("Número de matrícula") },
                placeholder = { Text("Ex.: 24.2.1165") },
                leadingIcon = {
                    Icon(
                        imageVector = Icons.Outlined.Badge,
                        contentDescription = null
                    )
                },
                singleLine = true,
                shape = RoundedCornerShape(18.dp)
            )

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.LocationOn,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Campus universitário",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                UFOP_CAMPUS_OPTIONS.forEach { campus ->
                    val isSelected = selectedCampus == campus
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSelectCampus(campus) },
                        leadingIcon = if (isSelected) {
                            {
                                Icon(
                                    imageVector = Icons.Default.Check,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        } else {
                            null
                        },
                        label = {
                            Text(
                                text = campus,
                                style = MaterialTheme.typography.labelLarge
                            )
                        },
                        shape = RoundedCornerShape(22.dp),
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedLeadingIconColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }
        }
    }

    // CTA primário -> avança para Tela 2 (salva rascunho com onboardingCompleted = false)
    Button(
        onClick = onContinue,
        enabled = !isImportingPdf,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = buttonMinHeight),
        shape = RoundedCornerShape(22.dp)
    ) {
        Text(
            text = "Continuar",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        Spacer(modifier = Modifier.width(8.dp))
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            modifier = Modifier.size(18.dp)
        )
    }

    Text(
        text = "Aplicativo independente para estudantes, sem vínculo oficial ou afiliação com a Universidade Federal de Ouro Preto (UFOP).",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 4.dp)
    )
}

@Composable
private fun OnboardingStep2SyllabusUpload(
    disciplines: List<DisciplineSummaryCard>,
    disciplineDetailsById: Map<String, DisciplineDetail?>,
    syllabusReports: Map<String, SyllabusImportReport>,
    uploadingCourseId: String?,
    onUploadSyllabus: (DisciplineSummaryCard) -> Unit,
    onBack: () -> Unit,
    onSkipOrContinue: () -> Unit,
    buttonMinHeight: androidx.compose.ui.unit.Dp
) {
    if (disciplines.isEmpty()) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            color = MaterialTheme.colorScheme.surface,
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(54.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.Description,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.size(26.dp)
                    )
                }
                Text(
                    text = "Sem disciplinas importadas ainda",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = "Como você optou pelo modo manual, poderá adicionar suas disciplinas na aba Disciplinas — com campo de professor em branco para preenchimento livre ou envio do Plano de Ensino (PDF) depois.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    } else {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f))
        ) {
            Text(
                text = "Encontramos ${disciplines.size} disciplinas do seu atestado. Enviar os Planos de Ensino agora é opcional — você pode fazer isso depois dentro de cada disciplina.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)
            )
        }

        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            disciplines.forEach { course ->
                val detail = disciplineDetailsById[course.courseId]
                val report = syllabusReports[course.courseId]
                val isUploading = uploadingCourseId == course.courseId
                val professorName = detail?.professor?.trim().orEmpty()
                val shortLoc = detail?.shortLocation?.trim().orEmpty()
                val isEad = shortLoc.contains("EAD", ignoreCase = true) ||
                    detail?.buildingAndRoom?.contains("EAD", ignoreCase = true) == true

                val (statusLabel, statusColor) = when {
                    report == null -> "Opcional · Pendente" to MaterialTheme.colorScheme.onSurfaceVariant
                    !report.matchedCourse -> "PDF de outra disciplina" to StatusAbsentRed
                    report.warningMessage != null -> "Aviso parcial" to StatusCancelledAmber
                    else -> "Plano completo importado" to StatusAttendingGreen
                }

                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(20.dp),
                    color = MaterialTheme.colorScheme.surface,
                    border = BorderStroke(
                        1.dp,
                        when {
                            report == null -> MaterialTheme.colorScheme.outlineVariant
                            !report.matchedCourse -> StatusAbsentRed.copy(alpha = 0.55f)
                            report.warningMessage != null -> StatusCancelledAmber.copy(alpha = 0.6f)
                            else -> StatusAttendingGreen.copy(alpha = 0.55f)
                        }
                    )
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.Top,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .width(4.dp)
                                    .height(44.dp)
                                    .clip(RoundedCornerShape(999.dp))
                                    .background(parseHexColor(course.colorHex))
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    if (course.code.isNotBlank()) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = MaterialTheme.colorScheme.surfaceVariant
                                        ) {
                                            Text(
                                                text = course.code,
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    if (isEad) {
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = DepartmentMathBlue.copy(alpha = 0.14f)
                                        ) {
                                            Text(
                                                text = "EAD",
                                                style = MaterialTheme.typography.labelSmall,
                                                fontWeight = FontWeight.Bold,
                                                color = DepartmentMathBlue,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                }

                                Text(
                                    text = course.name,
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )

                                Text(
                                    text = if (professorName.isNotBlank()) {
                                        "Docente: $professorName"
                                    } else {
                                        "Professor em branco (preencha ou envie plano)"
                                    },
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Surface(
                                shape = RoundedCornerShape(999.dp),
                                color = statusColor.copy(alpha = 0.12f)
                            ) {
                                Text(
                                    text = statusLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = statusColor,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                                )
                            }

                            FilledTonalButton(
                                onClick = { onUploadSyllabus(course) },
                                enabled = uploadingCourseId == null,
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                if (isUploading) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(15.dp),
                                        strokeWidth = 2.dp
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Lendo...",
                                        style = MaterialTheme.typography.labelMedium
                                    )
                                } else {
                                    Icon(
                                        imageVector = Icons.Outlined.UploadFile,
                                        contentDescription = null,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Enviar Plano (PDF)",
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

                        if (report != null) {
                            SyllabusImportFeedbackBanner(report = report)
                        }
                    }
                }
            }
        }
    }

    // Ações de rodapé da Tela 2
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedButton(
                onClick = onBack,
                enabled = uploadingCourseId == null,
                modifier = Modifier
                    .weight(1f)
                    .defaultMinSize(minHeight = buttonMinHeight),
                shape = RoundedCornerShape(20.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Voltar",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
            }

            Button(
                onClick = onSkipOrContinue,
                enabled = uploadingCourseId == null,
                modifier = Modifier
                    .weight(1.3f)
                    .defaultMinSize(minHeight = buttonMinHeight),
                shape = RoundedCornerShape(20.dp)
            ) {
                Text(
                    text = "Continuar",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.width(6.dp))
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowForward,
                    contentDescription = null,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        TextButton(
            onClick = onSkipOrContinue,
            enabled = uploadingCourseId == null,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = "Pular esta etapa",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun SyllabusImportFeedbackBanner(
    report: SyllabusImportReport
) {
    when {
        !report.matchedCourse -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = StatusAbsentRed.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, StatusAbsentRed.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.ErrorOutline,
                        contentDescription = null,
                        tint = StatusAbsentRed,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = report.errorMessage?.takeIf { it.isNotBlank() }
                            ?: "Este plano de ensino não parece pertencer a esta disciplina. Confira o PDF enviado.",
                        style = MaterialTheme.typography.bodySmall,
                        color = StatusAbsentRed
                    )
                }
            }
        }

        report.warningMessage != null -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = StatusCancelledAmber.copy(alpha = 0.14f),
                border = BorderStroke(1.dp, StatusCancelledAmber.copy(alpha = 0.45f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.WarningAmber,
                        contentDescription = null,
                        tint = StatusCancelledAmber,
                        modifier = Modifier.size(18.dp)
                    )
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        if (report.extractedProfessor.isNotBlank()) {
                            Text(
                                text = "Docente identificado: ${report.extractedProfessor}",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = StatusCancelledAmber
                            )
                        }
                        Text(
                            text = report.warningMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        else -> {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(12.dp),
                color = StatusAttendingGreen.copy(alpha = 0.12f),
                border = BorderStroke(1.dp, StatusAttendingGreen.copy(alpha = 0.35f))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Outlined.CheckCircle,
                        contentDescription = null,
                        tint = StatusAttendingGreen,
                        modifier = Modifier.size(18.dp)
                    )
                    val details = buildString {
                        if (report.extractedProfessor.isNotBlank()) {
                            append("Docente: ${report.extractedProfessor} · ")
                        }
                        append(
                            if (report.importedCount == 1) {
                                "1 avaliação importada"
                            } else {
                                "${report.importedCount} avaliações importadas"
                            }
                        )
                    }
                    Text(
                        text = details,
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.SemiBold,
                        color = StatusAttendingGreen
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OnboardingStep3PersonalizationAndA11y(
    isDark: Boolean,
    selectedAccent: CampusAccentColor,
    onSelectDarkMode: (Boolean) -> Unit,
    onSelectAccent: (CampusAccentColor) -> Unit,
    onConfigureAccessibility: () -> Unit,
    onFinishWithoutAccessibility: () -> Unit,
    onBack: () -> Unit,
    buttonMinHeight: androidx.compose.ui.unit.Dp
) {
    // Card 1: Modo Claro / Escuro
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Aparência do aplicativo",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                ThemeModeChoiceCard(
                    title = "Modo Claro",
                    icon = Icons.Outlined.WbSunny,
                    selected = !isDark,
                    onClick = { onSelectDarkMode(false) },
                    modifier = Modifier.weight(1f)
                )
                ThemeModeChoiceCard(
                    title = "Modo Escuro",
                    icon = Icons.Outlined.DarkMode,
                    selected = isDark,
                    onClick = { onSelectDarkMode(true) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }

    // Card 2: Paleta com todas as 7 cores de destaque (sem Azul)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Palette,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Cor de destaque",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CampusAccentColor.entries.forEach { accent ->
                    val isSelected = accent == selectedAccent
                    val swatch = parseHexColor(accent.previewHex)
                    Surface(
                        onClick = { onSelectAccent(accent) },
                        shape = RoundedCornerShape(16.dp),
                        color = if (isSelected) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
                        },
                        border = BorderStroke(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) {
                                MaterialTheme.colorScheme.primary
                            } else {
                                MaterialTheme.colorScheme.outlineVariant
                            }
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(22.dp)
                                    .clip(CircleShape)
                                    .background(swatch)
                                    .border(
                                        width = 1.dp,
                                        color = Color.Black.copy(alpha = 0.18f),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSelected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Selecionada",
                                        tint = Color.White,
                                        modifier = Modifier.size(14.dp)
                                    )
                                }
                            }
                            Text(
                                text = accent.displayName,
                                style = MaterialTheme.typography.labelLarge,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) {
                                    MaterialTheme.colorScheme.onPrimaryContainer
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                }
                            )
                        }
                    }
                }
            }
        }
    }

    // Card 3: Pergunta explícita de Acessibilidade
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f),
        border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(18.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Outlined.AccessibilityNew,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(22.dp)
                    )
                }
                Text(
                    text = "Você precisa configurar opções de acessibilidade?",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() }
                )
            }

            Text(
                text = "Disponibilizamos otimização para leitor de tela (TalkBack), fonte ampliada, alto contraste WCAG, texto em negrito, redução de movimento e áreas de toque ampliadas.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            FilledTonalButton(
                onClick = onConfigureAccessibility,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = buttonMinHeight),
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.AccessibilityNew,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Sim, configurar acessibilidade",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }

            Button(
                onClick = onFinishWithoutAccessibility,
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = buttonMinHeight),
                shape = RoundedCornerShape(18.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Não, finalizar e iniciar tour",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }

    TextButton(
        onClick = onBack,
        modifier = Modifier.fillMaxWidth()
    ) {
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
            contentDescription = null,
            modifier = Modifier.size(16.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = "Voltar para Planos de Ensino",
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun ThemeModeChoiceCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        color = if (selected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
        },
        border = BorderStroke(
            width = if (selected) 2.dp else 1.dp,
            color = if (selected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.outlineVariant
            }
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 14.dp, horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant
                },
                modifier = Modifier.size(24.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                color = if (selected) {
                    MaterialTheme.colorScheme.onPrimaryContainer
                } else {
                    MaterialTheme.colorScheme.onSurface
                }
            )
        }
    }
}

@Composable
private fun OnboardingStep4AccessibilitySetup(
    onBack: () -> Unit,
    onFinish: () -> Unit,
    buttonMinHeight: androidx.compose.ui.unit.Dp
) {
    val accessibilityController = LocalCampusAccessibility.current

    AccessibilitySettingsPanel(
        settings = accessibilityController.settings,
        onUpdateSettings = accessibilityController.updateSettings,
        modifier = Modifier.fillMaxWidth()
    )

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        OutlinedButton(
            onClick = onBack,
            modifier = Modifier
                .weight(0.9f)
                .defaultMinSize(minHeight = buttonMinHeight),
            shape = RoundedCornerShape(20.dp)
        ) {
            Icon(
                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = null,
                modifier = Modifier.size(16.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Voltar",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }

        Button(
            onClick = onFinish,
            modifier = Modifier
                .weight(1.4f)
                .defaultMinSize(minHeight = buttonMinHeight),
            shape = RoundedCornerShape(20.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            Text(
                text = "Concluir e iniciar tour",
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Bold
            )
        }
    }
}
