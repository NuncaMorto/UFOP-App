package br.ufop.campus.ui.curriculum

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import br.ufop.campus.core.curriculum.UfopCourseCurriculum
import br.ufop.campus.core.curriculum.UfopCurriculumCatalog
import br.ufop.campus.core.curriculum.UfopCurriculumSubject
import br.ufop.campus.core.curriculum.checkCurriculumCompatibility
import br.ufop.campus.core.ffi.CampusCoreRepository
import br.ufop.campus.ui.theme.DepartmentMathBlue
import br.ufop.campus.ui.theme.LocalCampusAccessibility
import br.ufop.campus.ui.theme.LocalCampusAdaptiveLayout
import br.ufop.campus.ui.theme.LocalCampusThemeController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.Normalizer
import java.text.NumberFormat
import java.util.Locale

internal enum class MatrixGroupMode(val label: String) {
    BY_PERIOD("Por Período"),
    BY_DEPARTMENT("Por Departamento")
}

internal enum class SubjectProgressStatus {
    APPROVED,
    IN_PROGRESS,
    AVAILABLE,
    BLOCKED_PREREQ
}

internal data class EvaluatedSubject(
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

internal fun departmentColorFor(departmentCode: String): Color {
    val upper = departmentCode.trim().uppercase()
    return MATRIX_DEPARTMENT_COLORS[upper] ?: DepartmentMathBlue
}

private fun normalizePlain(text: String): String =
    Normalizer.normalize(text.trim(), Normalizer.Form.NFD)
        .replace(COMBINING_MARKS_REGEX, "")
        .lowercase()

internal fun formatHoursPtBr(hours: Int): String =
    NumberFormat.getIntegerInstance(Locale("pt", "BR")).format(hours)

internal fun normalizeCurriculumDisplayName(raw: String): String {
    return raw
        .replace(Regex("\\bExpressao\\b"), "Expressão")
        .replace(Regex("\\bexpressao\\b"), "expressão")
        .replace(Regex("\\bEXPRESSAO\\b"), "EXPRESSÃO")
        .replace(Regex("\\bGrafica\\b"), "Gráfica")
        .replace(Regex("\\bgrafica\\b"), "gráfica")
}

internal fun extractCodeToken(raw: String): String {
    val upper = raw.trim().uppercase()
    return SUBJECT_CODE_EXTRACT_REGEX.find(upper)?.groupValues?.getOrNull(1) ?: upper
}

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
    val adaptive = LocalCampusAdaptiveLayout.current
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

    val baseCourse = remember(catalogCourses, selectedCourseCode) {
        catalogCourses.firstOrNull { it.code == selectedCourseCode }
    }

    val earliestTranscriptSemester = remember(creReport) {
        resolveEarliestTranscriptSemester(creReport.semesters.map { it.semester })
    }

    val availableVersions = remember(baseCourse) {
        baseCourse?.let { UfopCurriculumCatalog.getCourseVersions(it) }.orEmpty()
    }

    val defaultResolvedVersion = remember(
        baseCourse,
        studentProfile.registrationNumber,
        earliestTranscriptSemester
    ) {
        baseCourse?.let {
            UfopCurriculumCatalog.resolveCourseForStudent(
                baseCourse = it,
                registrationNumber = studentProfile.registrationNumber,
                earliestTranscriptSemester = earliestTranscriptSemester
            )
        }
    }

    var selectedCurriculumVersion by rememberSaveable(
        selectedCourseCode,
        defaultResolvedVersion?.curriculumVersion
    ) {
        mutableStateOf(defaultResolvedVersion?.curriculumVersion)
    }

    val selectedCourse = remember(
        availableVersions,
        selectedCurriculumVersion,
        defaultResolvedVersion,
        baseCourse
    ) {
        availableVersions.firstOrNull { it.curriculumVersion == selectedCurriculumVersion }
            ?: defaultResolvedVersion
            ?: baseCourse
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

    val studentEntryTerm = remember(studentProfile.registrationNumber, earliestTranscriptSemester) {
        val regEntry = UfopCurriculumCatalog.parseEnrollmentYearSemester(studentProfile.registrationNumber)
        val txEntry = earliestTranscriptSemester?.let { raw ->
            MATRIX_SEMESTER_ORDER_REGEX.find(raw)?.let { match ->
                val rawYear = match.groupValues[1].toIntOrNull()
                val fullYear = when {
                    rawYear == null -> null
                    match.groupValues[1].length == 2 -> if (rawYear >= 69) 1900 + rawYear else 2000 + rawYear
                    else -> rawYear
                }
                val sem = match.groupValues[2].toIntOrNull()
                if (fullYear != null && (sem == 1 || sem == 2)) fullYear to sem else null
            }
        }
        listOfNotNull(regEntry, txEntry)
            .minWithOrNull(compareBy<Pair<Int, Int>> { it.first }.thenBy { it.second })
    }

    val allCourseLegacyRefs = remember(selectedCourse?.code, catalogCourses) {
        selectedCourse?.let { UfopCurriculumCatalog.getLegacyReferences(it.code) }.orEmpty()
    }
    val relevantLegacyRefs = remember(selectedCourse?.code, studentEntryTerm, allCourseLegacyRefs) {
        val course = selectedCourse ?: return@remember emptyList()
        val filtered = if (studentEntryTerm != null) {
            UfopCurriculumCatalog.getLegacyReferences(
                courseCode = course.code,
                entryYear = studentEntryTerm.first,
                entrySemester = studentEntryTerm.second
            )
        } else {
            allCourseLegacyRefs
        }
        filtered.ifEmpty { allCourseLegacyRefs }
    }
    val officialLinks = remember(selectedCourse?.code, catalogCourses) {
        selectedCourse?.let { UfopCurriculumCatalog.getOfficialLinks(it.code) }
    }

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
                        dept to list.sortedWith(
                            compareBy<EvaluatedSubject> { if (it.subject.period <= 0) 999 else it.subject.period }
                                .thenBy { it.subject.code }
                        )
                    }
            }
        }
    }

    val isSearching = searchQuery.isNotBlank()

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding(),
            contentAlignment = Alignment.TopCenter
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 960.dp)
            ) {
                CurriculumMatrixTopBar(
                    selectedCourse = selectedCourse,
                    catalogCourseCount = catalogCourses.size,
                    minTouchTarget = minTouchTarget,
                    onDismiss = onDismiss,
                    onOpenCoursePicker = { showCoursePickerDialog = true }
                )

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
                                text = "Carregando catálogo curricular...",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                } else if (selectedCourse != null) {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(
                            start = 16.dp,
                            end = 16.dp,
                            top = 8.dp,
                            bottom = adaptive.bottomContentPaddingDp.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        if (compatibility != null && compatibility.isLegacyMatrixMismatch) {
                            item(key = "legacy_consultation_banner") {
                                LegacyMatrixConsultationBanner(
                                    compatibility = compatibility,
                                    course = selectedCourse
                                )
                            }
                            if (selectedCourse.subjects.isNotEmpty() &&
                                (relevantLegacyRefs.isNotEmpty() || !officialLinks?.officialMatrixUrl.isNullOrBlank())
                            ) {
                                item(key = "legacy_refs_${selectedCourse.code}_true") {
                                    CurriculumLegacyReferencesCard(
                                        references = relevantLegacyRefs,
                                        officialLinks = officialLinks,
                                        initiallyExpanded = true,
                                        isLegacyAlertContext = true,
                                        isDark = isDark,
                                        reduceMotion = a11y.reduceMotion,
                                        minTouchTarget = minTouchTarget,
                                        onOpenUrl = { url -> openExternalCurriculumUrl(context, url) }
                                    )
                                }
                            }
                        }

                        // Resumo oficial do curso (com versões embutidas)
                        item(key = "course_header") {
                            CourseWorkloadSummaryCard(
                                course = selectedCourse,
                                evaluatedSubjects = evaluatedSubjects,
                                isConsultationMode = isConsultationMode,
                                isDark = isDark,
                                availableVersions = availableVersions,
                                selectedCurriculumVersion = selectedCourse.curriculumVersion,
                                onSelectVersion = { selectedCurriculumVersion = it }
                            )
                        }

                        if (!isConsultationMode &&
                            selectedCourse.subjects.isNotEmpty() &&
                            allCourseLegacyRefs.isNotEmpty()
                        ) {
                            item(key = "legacy_refs_${selectedCourse.code}_false") {
                                CurriculumLegacyReferencesCard(
                                    references = allCourseLegacyRefs,
                                    officialLinks = officialLinks,
                                    initiallyExpanded = false,
                                    isLegacyAlertContext = false,
                                    isDark = isDark,
                                    reduceMotion = a11y.reduceMotion,
                                    minTouchTarget = minTouchTarget,
                                    onOpenUrl = { url -> openExternalCurriculumUrl(context, url) }
                                )
                            }
                        }

                        if (confirmationBanner != null) {
                            item(key = "confirmation_banner") {
                                CurriculumConfirmationBanner(
                                    message = confirmationBanner!!,
                                    isError = confirmationIsError,
                                    isDark = isDark,
                                    minTouchTarget = minTouchTarget,
                                    onDismiss = { confirmationBanner = null }
                                )
                            }
                        }

                        if (selectedCourse.subjects.isEmpty()) {
                            item(key = "empty_subjects_notice") {
                                CurriculumEmptySubjectsNotice(
                                    course = selectedCourse,
                                    catalogCourseCount = catalogCourses.size,
                                    officialLinks = officialLinks,
                                    legacyReferences = allCourseLegacyRefs,
                                    isDark = isDark,
                                    minTouchTarget = minTouchTarget,
                                    onOpenUrl = { url -> openExternalCurriculumUrl(context, url) },
                                    onOpenCoursePicker = { showCoursePickerDialog = true }
                                )
                            }
                        } else {
                            // Barra de busca + Segmented Button
                            item(key = "search_and_mode") {
                                CurriculumSearchAndModeBar(
                                    searchQuery = searchQuery,
                                    onSearchQueryChange = { searchQuery = it },
                                    groupMode = groupMode,
                                    onGroupModeChange = { groupMode = it },
                                    minTouchTarget = minTouchTarget
                                )
                            }

                            if (groupedSections.isEmpty()) {
                                item(key = "no_search_results") {
                                    CurriculumNoSearchResultsCard(searchQuery = searchQuery)
                                }
                            } else {
                                items(
                                    items = groupedSections,
                                    key = { (groupTitle, _) -> "${selectedCourse.code}_${selectedCourse.curriculumVersion}_${groupMode.name}_$groupTitle" }
                                ) { (groupTitle, itemsInGroup) ->
                                    val groupKey = "${selectedCourse.code}_${selectedCourse.curriculumVersion}_${groupMode.name}_$groupTitle"
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
