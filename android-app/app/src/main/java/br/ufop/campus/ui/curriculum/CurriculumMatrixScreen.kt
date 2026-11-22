package br.ufop.campus.ui.curriculum

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.School
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import br.ufop.campus.core.curriculum.CurriculumCompatibilityStatus
import br.ufop.campus.core.curriculum.UfopCourseCurriculum
import br.ufop.campus.core.curriculum.UfopCurriculumCatalog
import br.ufop.campus.core.curriculum.UfopCurriculumSubject
import br.ufop.campus.core.curriculum.checkCurriculumCompatibility
import br.ufop.campus.core.ffi.CampusCoreRepository
import br.ufop.campus.core.ffi.normalizeUfopPortugueseText
import br.ufop.campus.ui.theme.DepartmentMathBlue
import br.ufop.campus.ui.theme.LocalCampusAccessibility
import br.ufop.campus.ui.theme.LocalCampusThemeController
import br.ufop.campus.ui.theme.StatusAbsentRed
import br.ufop.campus.ui.theme.StatusAttendingGreen
import br.ufop.campus.ui.theme.StatusCancelledAmber
import br.ufop.campus.ui.theme.accessibleTextColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.text.NumberFormat
import java.util.Locale

private enum class MatrixGroupMode(val label: String) {
    BY_PERIOD("Por Período"),
    BY_DEPARTMENT("Por Departamento")
}

private enum class SubjectProgressStatus {
    APPROVED,
    IN_PROGRESS,
    AVAILABLE,
    BLOCKED_PREREQ
}

private data class EvaluatedSubject(
    val subject: UfopCurriculumSubject,
    val status: SubjectProgressStatus,
    val missingPrerequisites: List<String>
)

private val MATRIX_DEPARTMENT_COLORS: Map<String, Color> = mapOf(
    "DEABI" to Color(0xFFC2410C),
    "DEACL" to Color(0xFF0284C7),
    "DEALI" to Color(0xFF16A34A),
    "DEAMB" to Color(0xFF7C3AED),
    "DEARQ" to Color(0xFFDB2777),
    "DEART" to Color(0xFFD97706),
    "DEBIO" to Color(0xFF14B8A6),
    "DECAD" to Color(0xFF4338CA),
    "DECAT" to Color(0xFF2E7D32),
    "DECBI" to Color(0xFFE11D48),
    "DECEA" to Color(0xFF2563EB),
    "DECGP" to Color(0xFF9333EA),
    "DECIV" to Color(0xFFB45309),
    "DECOM" to Color(0xFF6E56CF),
    "DECPA" to Color(0xFF0891B2),
    "DECSI" to Color(0xFF047857),
    "DECSO" to Color(0xFFDC2626),
    "DEDIR" to Color(0xFF1D4ED8),
    "DEECO" to Color(0xFFCA8A04),
    "DEEDU" to Color(0xFFBE185D),
    "DEEFD" to Color(0xFF15803D),
    "DEELT" to Color(0xFFEA580C),
    "DEEMA" to Color(0xFF4F46E5),
    "DEENP" to Color(0xFF0F766E),
    "DEEST" to Color(0xFF8B5CF6),
    "DEETE" to Color(0xFF0369A1),
    "DEFAR" to Color(0xFF65A30D),
    "DEFIL" to Color(0xFFA21CAF),
    "DEFIS" to Color(0xFF8D5B16),
    "DEGEO" to Color(0xFF9F1239),
    "DEGEP" to Color(0xFF06B6D4),
    "DEHIS" to Color(0xFFA16207),
    "DEISH" to Color(0xFF1E3A8A),
    "DEJOR" to Color(0xFFF97316),
    "DELET" to Color(0xFF6D28D9),
    "DEMAT" to Color(0xFF3B68FF),
    "DEMEC" to Color(0xFF475569),
    "DEMET" to Color(0xFFB91C1C),
    "DEMIN" to Color(0xFF00897B),
    "DEMSC" to Color(0xFFC026D3),
    "DEMUL" to Color(0xFF7C2D12),
    "DEMUS" to Color(0xFF38BDF8),
    "DENCS" to Color(0xFF4D7C0F),
    "DEPRO" to Color(0xFF5B21B6),
    "DEQUI" to Color(0xFFEC4899),
    "DESSO" to Color(0xFF006064),
    "DETUR" to Color(0xFFF59E0B),
    "DEURB" to Color(0xFF166534)
)

private val SUBJECT_CODE_EXTRACT_REGEX = Regex("\\b([A-Z]{2,5}\\d{3,4}[A-Z]?)\\b")
private val COMBINING_MARKS_REGEX = Regex("\\p{M}+")
private val MATRIX_SEMESTER_ORDER_REGEX = Regex("(\\d{2,4})[./-](\\d)")

private fun resolveEarliestTranscriptSemester(semesters: List<String>): String? {
    return semesters
        .map { it.trim() }
        .filter { it.isNotEmpty() }
        .minByOrNull { raw ->
            val match = MATRIX_SEMESTER_ORDER_REGEX.find(raw)
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

private fun departmentColorFor(departmentCode: String): Color {
    val upper = departmentCode.trim().uppercase()
    return MATRIX_DEPARTMENT_COLORS[upper] ?: DepartmentMathBlue
}

private fun normalizePlain(text: String): String =
    Normalizer.normalize(text.trim(), Normalizer.Form.NFD)
        .replace(COMBINING_MARKS_REGEX, "")
        .lowercase()

private fun formatHoursPtBr(hours: Int): String =
    NumberFormat.getIntegerInstance(Locale("pt", "BR")).format(hours)

private fun normalizeCurriculumDisplayName(raw: String): String {
    return raw
        .replace(Regex("\\bExpressao\\b"), "Expressão")
        .replace(Regex("\\bexpressao\\b"), "expressão")
        .replace(Regex("\\bEXPRESSAO\\b"), "EXPRESSÃO")
        .replace(Regex("\\bGrafica\\b"), "Gráfica")
        .replace(Regex("\\bgrafica\\b"), "gráfica")
}

private fun extractCodeToken(raw: String): String {
    val upper = raw.trim().uppercase()
    return SUBJECT_CODE_EXTRACT_REGEX.find(upper)?.groupValues?.getOrNull(1) ?: upper
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun CurriculumMatrixModal(
    repository: CampusCoreRepository,
    initialCourseQuery: String? = null,
    onDismiss: () -> Unit
) {
    BackHandler(onBack = onDismiss)

    val context = LocalContext.current
    val isDark = LocalCampusThemeController.current.isDark
    val a11y = LocalCampusAccessibility.current.settings
    val minTouchTarget = if (a11y.largeTouchTargets) 56.dp else 48.dp
    val mutationVersion by repository.mutationVersion.collectAsState()

    val studentProfile = remember(mutationVersion) { repository.getStudentProfile() }
    val activeSemester = studentProfile.activeSemester.ifBlank { "2026.2" }
    val creReport = remember(mutationVersion) { repository.getAcademicCreReport() }
    val currentDisciplines = remember(mutationVersion, activeSemester) {
        repository.listDisciplines(activeSemester, null)
    }

    val catalogCourses by produceState<List<UfopCourseCurriculum>>(initialValue = emptyList()) {
        value = withContext(Dispatchers.IO) { UfopCurriculumCatalog.getAllCourses(context) }
    }

    var selectedCourseCode by rememberSaveable { mutableStateOf<String?>(null) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var groupMode by rememberSaveable { mutableStateOf(MatrixGroupMode.BY_PERIOD) }
    var showCoursePickerDialog by remember { mutableStateOf(false) }
    var selectedSubject by remember { mutableStateOf<EvaluatedSubject?>(null) }
    var confirmationBanner by remember { mutableStateOf<String?>(null) }
    var confirmationIsError by remember { mutableStateOf(false) }

    val expandedGroups = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(catalogCourses, initialCourseQuery, studentProfile.courseName, studentProfile.campusName) {
        if (catalogCourses.isNotEmpty() && selectedCourseCode == null) {
            val query = initialCourseQuery?.takeIf { it.isNotBlank() } ?: studentProfile.courseName
            val matched = if (query.isNotBlank()) {
                UfopCurriculumCatalog.findCourse(catalogCourses, query, studentProfile.campusName)
            } else {
                null
            }
            val fallback = matched ?: catalogCourses.firstOrNull { it.subjects.isNotEmpty() } ?: catalogCourses.firstOrNull()
            selectedCourseCode = fallback?.code
        }
    }

    val selectedCourse = remember(catalogCourses, selectedCourseCode) {
        catalogCourses.firstOrNull { it.code == selectedCourseCode }
    }

    // Cross-reference Histórico Escolar & Semestre ativo
    val (approvedCodes, inProgressCodes, allStudentCourseCodes) = remember(creReport, currentDisciplines) {
        val approved = HashSet<String>()
        val inProgress = HashSet<String>()
        val allCodes = HashSet<String>()

        creReport.semesters.forEach { sem ->
            sem.courses.forEach { entry ->
                val codeUpper = entry.code.trim().uppercase()
                if (codeUpper.isNotEmpty()) allCodes.add(codeUpper)
                val statusUpper = entry.status.trim().uppercase()
                when {
                    statusUpper in setOf("AP", "AE", "DE", "DJ") -> {
                        if (codeUpper.isNotEmpty()) approved.add(codeUpper)
                    }
                    statusUpper == "CS" -> {
                        if (codeUpper.isNotEmpty()) inProgress.add(codeUpper)
                    }
                }
            }
        }

        currentDisciplines.forEach { disc ->
            val explicitCode = disc.code.trim().uppercase()
            if (explicitCode.isNotEmpty()) {
                inProgress.add(explicitCode)
                allCodes.add(explicitCode)
            }
            SUBJECT_CODE_EXTRACT_REGEX.find(disc.name.uppercase())?.groupValues?.getOrNull(1)?.let {
                inProgress.add(it)
                allCodes.add(it)
            }
        }

        Triple(approved, inProgress, allCodes)
    }

    val earliestTranscriptSemester = remember(creReport) {
        resolveEarliestTranscriptSemester(creReport.semesters.map { it.semester })
    }

    val compatibility = remember(
        studentProfile.registrationNumber,
        earliestTranscriptSemester,
        allStudentCourseCodes,
        selectedCourse
    ) {
        selectedCourse?.let { course ->
            checkCurriculumCompatibility(
                registrationNumber = studentProfile.registrationNumber,
                earliestTranscriptSemester = earliestTranscriptSemester,
                transcriptCourseCodes = allStudentCourseCodes,
                course = course
            )
        }
    }
    val isConsultationMode = compatibility?.isLegacyMatrixMismatch == true

    val evaluatedSubjects = remember(selectedCourse, approvedCodes, inProgressCodes) {
        val subjects = selectedCourse?.subjects.orEmpty()
        subjects.map { subject ->
            val codeUpper = subject.code.trim().uppercase()
            when {
                codeUpper in approvedCodes -> EvaluatedSubject(
                    subject = subject,
                    status = SubjectProgressStatus.APPROVED,
                    missingPrerequisites = emptyList()
                )
                codeUpper in inProgressCodes -> EvaluatedSubject(
                    subject = subject,
                    status = SubjectProgressStatus.IN_PROGRESS,
                    missingPrerequisites = emptyList()
                )
                else -> {
                    val missing = subject.prerequisites.filter { rawPrereq ->
                        val token = extractCodeToken(rawPrereq)
                        token.isNotEmpty() && token !in approvedCodes
                    }
                    EvaluatedSubject(
                        subject = subject,
                        status = if (missing.isEmpty()) {
                            SubjectProgressStatus.AVAILABLE
                        } else {
                            SubjectProgressStatus.BLOCKED_PREREQ
                        },
                        missingPrerequisites = missing
                    )
                }
            }
        }
    }

    val filteredSubjects = remember(evaluatedSubjects, searchQuery) {
        val q = normalizePlain(searchQuery)
        if (q.isEmpty()) {
            evaluatedSubjects
        } else {
            evaluatedSubjects.filter { item ->
                val s = item.subject
                normalizePlain(s.code).contains(q) ||
                    normalizePlain(s.name).contains(q) ||
                    normalizePlain(s.departmentCode).contains(q) ||
                    normalizePlain(s.departmentId).contains(q)
            }
        }
    }

    val groupedSections: List<Pair<String, List<EvaluatedSubject>>> = remember(filteredSubjects, groupMode) {
        when (groupMode) {
            MatrixGroupMode.BY_PERIOD -> {
                filteredSubjects
                    .groupBy { it.subject.period }
                    .entries
                    .sortedWith(compareBy<Map.Entry<Int, List<EvaluatedSubject>>> { if (it.key <= 0) 999 else it.key })
                    .map { (period, list) ->
                        val title = if (period <= 0) "Atividades Curriculares" else "${period}º Período"
                        title to list.sortedBy { it.subject.code }
                    }
            }
            MatrixGroupMode.BY_DEPARTMENT -> {
                filteredSubjects
                    .groupBy { it.subject.departmentCode.ifBlank { "UFOP" } }
                    .entries
                    .sortedBy { it.key }
                    .map { (dept, list) ->
                        dept to list.sortedWith(compareBy<EvaluatedSubject> { if (it.subject.period <= 0) 999 else it.subject.period }.thenBy { it.subject.code })
                    }
            }
        }
    }

    val isSearching = searchQuery.isNotBlank()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
        ) {
            // Top Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Surface(
                    onClick = onDismiss,
                    modifier = Modifier.size(minTouchTarget),
                    shape = CircleShape,
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                            contentDescription = "Voltar",
                            tint = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "MATRIZ CURRICULAR · UFOP 2026/2",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Text(
                        text = selectedCourse?.let { "${it.code} · ${normalizeCurriculumDisplayName(it.name)}" } ?: "Carregando matrizes...",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.semantics { heading() }
                    )
                }

                OutlinedButton(
                    onClick = { showCoursePickerDialog = true },
                    enabled = catalogCourses.isNotEmpty(),
                    modifier = Modifier.defaultMinSize(minHeight = minTouchTarget),
                    shape = RoundedCornerShape(999.dp),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "${catalogCourses.size.takeIf { it > 0 } ?: 54} cursos",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        softWrap = false
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowDown,
                        contentDescription = "Selecionar curso",
                        modifier = Modifier.size(18.dp)
                    )
                }
            }

            if (catalogCourses.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator()
                        Text(
                            text = "Carregando catálogo oficial da UFOP...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else if (selectedCourse != null) {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 136.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (compatibility != null && compatibility.isLegacyMatrixMismatch) {
                        item(key = "legacy_consultation_banner") {
                            LegacyMatrixConsultationBanner(compatibility = compatibility)
                        }
                    }

                    // Resumo oficial do curso
                    item(key = "course_header") {
                        CourseWorkloadSummaryCard(
                            course = selectedCourse,
                            evaluatedSubjects = evaluatedSubjects,
                            isConsultationMode = isConsultationMode,
                            isDark = isDark
                        )
                    }

                    if (confirmationBanner != null) {
                        item(key = "confirmation_banner") {
                            val rawColor = if (confirmationIsError) StatusAbsentRed else StatusAttendingGreen
                            val textColor = accessibleTextColor(rawColor, isDark)
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(16.dp),
                                color = rawColor.copy(alpha = 0.12f),
                                border = BorderStroke(1.dp, rawColor.copy(alpha = 0.35f))
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 14.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = confirmationBanner!!,
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = textColor,
                                        modifier = Modifier.weight(1f)
                                    )
                                    IconButton(
                                        onClick = { confirmationBanner = null },
                                        modifier = Modifier.size(minTouchTarget)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Close,
                                            contentDescription = "Fechar",
                                            modifier = Modifier.size(16.dp),
                                            tint = textColor
                                        )
                                    }
                                }
                            }
                        }
                    }

                    if (selectedCourse.subjects.isEmpty()) {
                        item(key = "empty_subjects_notice") {
                            Surface(
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(22.dp),
                                color = MaterialTheme.colorScheme.surface,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(18.dp),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Outlined.Info,
                                            contentDescription = null,
                                            tint = accessibleTextColor(StatusCancelledAmber, isDark)
                                        )
                                        Text(
                                            text = "Resumo oficial 2026/2 · Detalhamento PDF pendente na fonte UFOP",
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onSurface
                                        )
                                    }
                                    Text(
                                        text = "As cargas horárias totais oficiais de ${normalizeCurriculumDisplayName(selectedCourse.name)} já estão sincronizadas com o catálogo 2026/2 (${formatHoursPtBr(selectedCourse.chTotal)}h totais). A lista individual de componentes curriculares deste curso aguarda publicação detalhada pela Pró-Reitoria de Graduação.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    OutlinedButton(
                                        onClick = { showCoursePickerDialog = true },
                                        modifier = Modifier.defaultMinSize(minHeight = minTouchTarget),
                                        shape = RoundedCornerShape(22.dp)
                                    ) {
                                        Text("Explorar outro dos ${catalogCourses.size} cursos UFOP")
                                    }
                                }
                            }
                        }
                    } else {
                        // Barra de busca + Segmented Button
                        item(key = "search_and_mode") {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                OutlinedTextField(
                                    value = searchQuery,
                                    onValueChange = { searchQuery = it },
                                    modifier = Modifier.fillMaxWidth(),
                                    placeholder = { Text("Buscar código, nome ou departamento...") },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = Icons.Outlined.Search,
                                            contentDescription = null
                                        )
                                    },
                                    trailingIcon = if (searchQuery.isNotEmpty()) {
                                        {
                                            IconButton(
                                                onClick = { searchQuery = "" },
                                                modifier = Modifier.size(minTouchTarget)
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Default.Close,
                                                    contentDescription = "Limpar busca"
                                                )
                                            }
                                        }
                                    } else {
                                        null
                                    },
                                    singleLine = true,
                                    shape = RoundedCornerShape(20.dp)
                                )

                                SingleChoiceSegmentedButtonRow(
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    MatrixGroupMode.entries.forEachIndexed { index, mode ->
                                        val isSelected = groupMode == mode
                                        SegmentedButton(
                                            selected = isSelected,
                                            onClick = { groupMode = mode },
                                            shape = SegmentedButtonDefaults.itemShape(
                                                index = index,
                                                count = MatrixGroupMode.entries.size
                                            ),
                                            colors = SegmentedButtonDefaults.colors(
                                                activeContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                                activeContentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                                inactiveContainerColor = MaterialTheme.colorScheme.surface,
                                                inactiveContentColor = MaterialTheme.colorScheme.onSurfaceVariant
                                            ),
                                            label = {
                                                Text(
                                                    text = mode.label,
                                                    maxLines = 1,
                                                    softWrap = false,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                                                )
                                            }
                                        )
                                    }
                                }
                            }
                        }

                        if (groupedSections.isEmpty()) {
                            item(key = "no_search_results") {
                                Surface(
                                    modifier = Modifier.fillMaxWidth(),
                                    shape = RoundedCornerShape(20.dp),
                                    color = MaterialTheme.colorScheme.surface,
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
                                ) {
                                    Text(
                                        text = "Nenhum componente encontrado para \"$searchQuery\".",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(18.dp)
                                    )
                                }
                            }
                        } else {
                            items(
                                items = groupedSections,
                                key = { (groupTitle, _) -> "${selectedCourse.code}_${groupMode.name}_$groupTitle" }
                            ) { (groupTitle, itemsInGroup) ->
                                val groupKey = "${selectedCourse.code}_${groupMode.name}_$groupTitle"
                                val defaultOpen = isSearching || groupTitle.startsWith("1º") || groupTitle.startsWith("2º")
                                val isExpanded = expandedGroups[groupKey] ?: defaultOpen

                                MatrixAccordionGroup(
                                    title = groupTitle,
                                    subjects = itemsInGroup,
                                    isDepartmentGroup = groupMode == MatrixGroupMode.BY_DEPARTMENT,
                                    isConsultationMode = isConsultationMode,
                                    isExpanded = isExpanded,
                                    isDark = isDark,
                                    reduceMotion = a11y.reduceMotion,
                                    minTouchTarget = minTouchTarget,
                                    onToggleExpand = { expandedGroups[groupKey] = !isExpanded },
                                    onSelectSubject = { selectedSubject = it }
                                )
                            }
                        }
                    }

                    item(key = "bottom_spacer") {
                        Spacer(modifier = Modifier.navigationBarsPadding())
                    }
                }
            }
        }
    }

    if (showCoursePickerDialog) {
        CourseCatalogSwitcherDialog(
            courses = catalogCourses,
            selectedCourseCode = selectedCourseCode,
            isDark = isDark,
            minTouchTarget = minTouchTarget,
            onDismiss = { showCoursePickerDialog = false },
            onSelectCourse = { chosen ->
                selectedCourseCode = chosen.code
                searchQuery = ""
                showCoursePickerDialog = false
            }
        )
    }

    selectedSubject?.let { evaluated ->
        SubjectDetailDialog(
            evaluated = evaluated,
            approvedCodes = approvedCodes,
            activeSemester = activeSemester,
            isConsultationMode = isConsultationMode,
            isDark = isDark,
            minTouchTarget = minTouchTarget,
            onDismiss = { selectedSubject = null },
            onAddToActiveSemester = { subject ->
                runCatching {
                    val deptId = subject.departmentId.ifBlank { subject.departmentCode.lowercase() }
                    val cleanName = normalizeCurriculumDisplayName(subject.name)
                    val created = repository.createCourse(
                        semester = activeSemester,
                        departmentId = deptId,
                        name = "${subject.code} - $cleanName",
                        professor = "",
                        workloadHours = subject.cha.takeIf { it > 0 } ?: 60
                    )
                    repository.updateDisciplineNotes(
                        courseId = created.courseId,
                        notes = "Código: ${subject.code} · ${subject.type} · " +
                            if (subject.period > 0) "${subject.period}º período" else "Atividade Curricular"
                    )
                    created
                }.onSuccess {
                    confirmationIsError = false
                    confirmationBanner =
                        "✓ ${subject.code} — ${normalizeCurriculumDisplayName(subject.name)} adicionada ao semestre $activeSemester!"
                    selectedSubject = null
                }.onFailure { err ->
                    confirmationIsError = true
                    confirmationBanner = err.message ?: "Não foi possível adicionar a disciplina."
                    selectedSubject = null
                }
            }
        )
    }
}

@Composable
private fun LegacyMatrixConsultationBanner(
    compatibility: CurriculumCompatibilityStatus
) {
    val entryLabel = compatibility.studentEntryLabel ?: "anterior a 2026/1"
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = StatusCancelledAmber.copy(alpha = 0.12f),
        border = BorderStroke(1.dp, StatusCancelledAmber.copy(alpha = 0.42f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(
                    imageVector = Icons.Outlined.Info,
                    contentDescription = null,
                    tint = StatusCancelledAmber,
                    modifier = Modifier.size(20.dp)
                )
                Text(
                    text = "Matriz de referência ${compatibility.matrixVersionLabel} · Modo Consulta",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }
            Text(
                text = "Sua matrícula ($entryLabel) precede a atualização de matriz curricular de 2026/1. Por isso, marcações de \"Já cursada\", \"Disponível\" e \"Falta pré-requisito\" foram ocultadas para evitar divergências com sua grade original.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "Você continua podendo buscar disciplinas, abrir períodos e consultar cargas horárias e pré-requisitos livremente.",
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CourseWorkloadSummaryCard(
    course: UfopCourseCurriculum,
    evaluatedSubjects: List<EvaluatedSubject>,
    isConsultationMode: Boolean,
    isDark: Boolean
) {
    val approvedCount = evaluatedSubjects.count { it.status == SubjectProgressStatus.APPROVED }
    val inProgressCount = evaluatedSubjects.count { it.status == SubjectProgressStatus.IN_PROGRESS }
    val campusColor = accessibleTextColor(DepartmentMathBlue, isDark)

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .semantics(mergeDescendants = true) {},
        shape = RoundedCornerShape(22.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.primaryContainer
                    ) {
                        Text(
                            text = course.code,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                    if (course.degree.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceVariant
                        ) {
                            Text(
                                text = course.degree,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                    if (course.campus.isNotBlank()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = DepartmentMathBlue.copy(alpha = 0.12f)
                        ) {
                            Text(
                                text = course.campus,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = campusColor,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }
                }

                Text(
                    text = normalizeCurriculumDisplayName(course.name),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.semantics { heading() }
                )

                val visibleStatus = course.status
                    .trim()
                    .takeIf { it.isNotBlank() && !it.equals("Coletada", ignoreCase = true) }
                val versionText = listOfNotNull(
                    course.curriculumVersion.takeIf { it.isNotBlank() }?.let { "Versão $it" },
                    visibleStatus
                ).joinToString(" · ")
                if (versionText.isNotEmpty()) {
                    Text(
                        text = versionText,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                WorkloadPill(label = "Total", hours = course.chTotal, highlighted = true)
                if (course.chMandatory > 0) WorkloadPill(label = "Obrig.", hours = course.chMandatory)
                if (course.chOptional > 0) WorkloadPill(label = "Elet./Opt.", hours = course.chOptional)
                if (course.chElective > 0) WorkloadPill(label = "Facult.", hours = course.chElective)
                if (course.chExtension > 0) WorkloadPill(label = "Extensão", hours = course.chExtension)
                if (course.chActivities > 0) WorkloadPill(label = "Ativ.", hours = course.chActivities)
            }

            if (evaluatedSubjects.isNotEmpty()) {
                Text(
                    text = if (isConsultationMode) {
                        "${evaluatedSubjects.size} componentes na matriz de referência · Modo Consulta"
                    } else {
                        "${evaluatedSubjects.size} componentes na matriz · $approvedCount aprovadas · $inProgressCount cursando"
                    },
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun WorkloadPill(
    label: String,
    hours: Int,
    highlighted: Boolean = false
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = if (highlighted) {
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f)
        } else {
            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        }
    ) {
        Text(
            text = "$label ${formatHoursPtBr(hours)}h",
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (highlighted) FontWeight.Bold else FontWeight.Medium,
            color = if (highlighted) {
                MaterialTheme.colorScheme.onPrimaryContainer
            } else {
                MaterialTheme.colorScheme.onSurfaceVariant
            },
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun MatrixAccordionGroup(
    title: String,
    subjects: List<EvaluatedSubject>,
    isDepartmentGroup: Boolean,
    isConsultationMode: Boolean,
    isExpanded: Boolean,
    isDark: Boolean,
    reduceMotion: Boolean,
    minTouchTarget: Dp,
    onToggleExpand: () -> Unit,
    onSelectSubject: (EvaluatedSubject) -> Unit
) {
    val totalCha = remember(subjects) { subjects.sumOf { it.subject.cha } }
    val deptColor = if (isDepartmentGroup) departmentColorFor(title) else DepartmentMathBlue

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .defaultMinSize(minHeight = minTouchTarget)
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onToggleExpand)
                    .padding(vertical = 4.dp, horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isDepartmentGroup) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(deptColor)
                        )
                    }
                    Column {
                        Text(
                            text = title,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface,
                            modifier = Modifier.semantics { heading() }
                        )
                        Text(
                            text = "${subjects.size} componentes · ${formatHoursPtBr(totalCha)}h",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Icon(
                    imageVector = if (isExpanded) {
                        Icons.Default.KeyboardArrowUp
                    } else {
                        Icons.Default.KeyboardArrowDown
                    },
                    contentDescription = if (isExpanded) "Recolher" else "Expandir",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            AnimatedVisibility(
                visible = isExpanded,
                enter = if (reduceMotion) EnterTransition.None else androidx.compose.animation.expandVertically() + androidx.compose.animation.fadeIn(),
                exit = if (reduceMotion) ExitTransition.None else androidx.compose.animation.shrinkVertically() + androidx.compose.animation.fadeOut()
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.65f))
                    subjects.forEach { item ->
                        MatrixSubjectRow(
                            evaluated = item,
                            isConsultationMode = isConsultationMode,
                            isDark = isDark,
                            minTouchTarget = minTouchTarget,
                            onClick = { onSelectSubject(item) }
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun MatrixSubjectRow(
    evaluated: EvaluatedSubject,
    isConsultationMode: Boolean,
    isDark: Boolean,
    minTouchTarget: Dp,
    onClick: () -> Unit
) {
    val subject = evaluated.subject
    val deptColor = departmentColorFor(subject.departmentCode)
    val accessibleDeptColor = accessibleTextColor(deptColor, isDark)
    val displaySubjectName = normalizeCurriculumDisplayName(subject.name)

    val (badgeLabel, badgeBg, rawBadgeFg) = when (evaluated.status) {
        SubjectProgressStatus.APPROVED -> Triple(
            "✓ Aprovada",
            StatusAttendingGreen.copy(alpha = 0.14f),
            StatusAttendingGreen
        )
        SubjectProgressStatus.IN_PROGRESS -> Triple(
            "● Cursando",
            DepartmentMathBlue.copy(alpha = 0.14f),
            DepartmentMathBlue
        )
        SubjectProgressStatus.AVAILABLE -> Triple(
            "Disponível",
            MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.8f),
            MaterialTheme.colorScheme.onPrimaryContainer
        )
        SubjectProgressStatus.BLOCKED_PREREQ -> Triple(
            "Prereq",
            StatusCancelledAmber.copy(alpha = 0.14f),
            StatusCancelledAmber
        )
    }
    val badgeFg = if (evaluated.status == SubjectProgressStatus.AVAILABLE) {
        rawBadgeFg
    } else {
        accessibleTextColor(rawBadgeFg, isDark)
    }

    Surface(
        onClick = onClick,
        modifier = Modifier
            .fillMaxWidth()
            .defaultMinSize(minHeight = minTouchTarget)
            .semantics(mergeDescendants = true) {},
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.7f))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .clip(CircleShape)
                            .background(deptColor)
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = deptColor.copy(alpha = 0.14f)
                    ) {
                        Text(
                            text = subject.code,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = accessibleDeptColor,
                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                        )
                    }
                    Text(
                        text = "${subject.departmentCode} · ${subject.cha}h",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                if (!isConsultationMode) {
                    Surface(
                        shape = RoundedCornerShape(999.dp),
                        color = badgeBg
                    ) {
                        Text(
                            text = badgeLabel,
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = badgeFg,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                        )
                    }
                }
            }

            Text(
                text = displaySubjectName,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )

            val weeklyHoursText = "${subject.weeklyTheory} teóricas · ${subject.weeklyPractice} práticas/sem."
            if (isConsultationMode) {
                val consultInfo = buildString {
                    append(subject.type)
                    if (subject.weeklyTheory > 0 || subject.weeklyPractice > 0) {
                        append(" · $weeklyHoursText")
                    }
                    if (subject.prerequisites.isNotEmpty()) {
                        append(" · Pré-req: ${subject.prerequisites.joinToString(", ")}")
                    }
                }
                Text(
                    text = consultInfo,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            } else if (evaluated.status == SubjectProgressStatus.BLOCKED_PREREQ && evaluated.missingPrerequisites.isNotEmpty()) {
                Text(
                    text = "Falta pré-req: ${evaluated.missingPrerequisites.joinToString(", ")}",
                    style = MaterialTheme.typography.labelSmall,
                    color = accessibleTextColor(StatusCancelledAmber, isDark),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            } else if (subject.weeklyTheory > 0 || subject.weeklyPractice > 0) {
                Text(
                    text = "${subject.type} · $weeklyHoursText",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SubjectDetailDialog(
    evaluated: EvaluatedSubject,
    approvedCodes: Set<String>,
    activeSemester: String,
    isConsultationMode: Boolean,
    isDark: Boolean,
    minTouchTarget: Dp,
    onDismiss: () -> Unit,
    onAddToActiveSemester: (UfopCurriculumSubject) -> Unit
) {
    val subject = evaluated.subject
    val deptColor = departmentColorFor(subject.departmentCode)
    val accessibleDeptColor = accessibleTextColor(deptColor, isDark)
    val displaySubjectName = normalizeCurriculumDisplayName(subject.name)
    val periodLabel = if (subject.period <= 0) "Atividades Curriculares" else "${subject.period}º Período"

    AlertDialog(
        onDismissRequest = onDismiss,
        icon = {
            Icon(
                imageVector = Icons.AutoMirrored.Outlined.MenuBook,
                contentDescription = null,
                tint = accessibleDeptColor
            )
        },
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "${subject.code} · ${subject.departmentCode} · $periodLabel",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = accessibleDeptColor
                )
                Text(
                    text = displaySubjectName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Surface(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Text(
                            text = "Tipo: ${subject.type}",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Carga horária: CHS ${subject.chs}h · CHE ${subject.che}h · CHA ${subject.cha}h",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "Aulas semanais: ${subject.weeklyTheory} teóricas · ${subject.weeklyPractice} práticas/sem.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Text(
                    text = "Pré-requisitos",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold
                )
                if (subject.prerequisites.isEmpty()) {
                    Text(
                        text = "Nenhum pré-requisito obrigatório.",
                        style = MaterialTheme.typography.bodySmall,
                        color = accessibleTextColor(StatusAttendingGreen, isDark)
                    )
                } else {
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        subject.prerequisites.forEach { prereq ->
                            val token = extractCodeToken(prereq)
                            val satisfied = !isConsultationMode && token in approvedCodes
                            val chipBg = when {
                                isConsultationMode -> MaterialTheme.colorScheme.surfaceVariant
                                satisfied -> StatusAttendingGreen.copy(alpha = 0.14f)
                                else -> StatusCancelledAmber.copy(alpha = 0.14f)
                            }
                            val chipFg = when {
                                isConsultationMode -> MaterialTheme.colorScheme.onSurfaceVariant
                                satisfied -> accessibleTextColor(StatusAttendingGreen, isDark)
                                else -> accessibleTextColor(StatusCancelledAmber, isDark)
                            }
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = chipBg
                            ) {
                                Text(
                                    text = if (satisfied) "✓ $prereq" else prereq,
                                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = FontFamily.Monospace),
                                    fontWeight = FontWeight.Bold,
                                    color = chipFg,
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            if (isConsultationMode || evaluated.status != SubjectProgressStatus.IN_PROGRESS) {
                Button(
                    onClick = { onAddToActiveSemester(subject) },
                    modifier = Modifier.defaultMinSize(minHeight = minTouchTarget),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Add,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Adicionar ao semestre ativo",
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Button(
                    onClick = onDismiss,
                    modifier = Modifier.defaultMinSize(minHeight = minTouchTarget),
                    shape = RoundedCornerShape(22.dp)
                ) {
                    Text("Em curso em $activeSemester")
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.defaultMinSize(minHeight = minTouchTarget)
            ) {
                Text(
                    text = "Fechar",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}

@Composable
private fun CourseCatalogSwitcherDialog(
    courses: List<UfopCourseCurriculum>,
    selectedCourseCode: String?,
    isDark: Boolean,
    minTouchTarget: Dp,
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
                tint = accessibleTextColor(DepartmentMathBlue, isDark)
            )
        },
        title = {
            Text(
                text = "Selecionar Curso UFOP (${courses.size})",
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
                    placeholder = { Text("Buscar por nome, código ou campus...") },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Outlined.Search,
                            contentDescription = null
                        )
                    },
                    trailingIcon = if (query.isNotEmpty()) {
                        {
                            IconButton(
                                onClick = { query = "" },
                                modifier = Modifier.size(minTouchTarget)
                            ) {
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
                        .heightIn(min = 200.dp, max = 380.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = filtered,
                        key = { it.code }
                    ) { course ->
                        val isSelected = course.code == selectedCourseCode
                        Surface(
                            onClick = { onSelectCourse(course) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .defaultMinSize(minHeight = minTouchTarget),
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
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Column(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = "${course.code} · ${normalizeCurriculumDisplayName(course.name)}",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onSurface,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    val meta = buildString {
                                        if (course.campus.isNotBlank()) append(course.campus)
                                        if (course.degree.isNotBlank()) {
                                            if (isNotEmpty()) append(" · ")
                                            append(course.degree)
                                        }
                                        if (course.chTotal > 0) {
                                            if (isNotEmpty()) append(" · ")
                                            append("${formatHoursPtBr(course.chTotal)}h")
                                        }
                                        if (course.subjects.isNotEmpty()) {
                                            append(" · ${course.subjects.size} comp.")
                                        }
                                    }
                                    Text(
                                        text = meta,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        maxLines = 2,
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
            TextButton(
                onClick = onDismiss,
                modifier = Modifier.defaultMinSize(minHeight = minTouchTarget)
            ) {
                Text(
                    text = "Fechar",
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    )
}
