package br.ufop.campus.ui.activities

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import br.ufop.campus.core.ffi.AssessmentWithCourse
import br.ufop.campus.core.ffi.CampusCoreRepository
import br.ufop.campus.core.ffi.DisciplineDetail
import br.ufop.campus.ui.disciplines.DisciplineDetailBottomSheet
import br.ufop.campus.ui.disciplines.DisciplinesViewModel
import br.ufop.campus.ui.theme.CampusWindowWidthClass
import br.ufop.campus.ui.theme.LocalCampusAccessibility
import br.ufop.campus.ui.theme.LocalCampusAdaptiveLayout
import br.ufop.campus.ui.theme.LocalCampusThemeController
import kotlinx.coroutines.launch

@Composable
fun ActivitiesPlaceholderScreen(
    repository: CampusCoreRepository,
    modifier: Modifier = Modifier
) {
    SideEffect {
        DisciplinesViewModel.sharedRepository = repository
    }

    val isDark = LocalCampusThemeController.current.isDark
    val adaptive = LocalCampusAdaptiveLayout.current
    val a11y = LocalCampusAccessibility.current.settings
    val minTouchTarget = if (a11y.largeTouchTargets) 56.dp else 48.dp
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val mutationVersion by repository.mutationVersion.collectAsState()
    val activeSemester = remember(mutationVersion) {
        repository.getStudentProfile().activeSemester.ifBlank { "2026.2" }
    }
    var selectedTypesList by rememberSaveable { mutableStateOf(emptyList<String>()) }
    val selectedTypes = remember(selectedTypesList) { selectedTypesList.toSet() }
    var onlyCompleted by rememberSaveable { mutableStateOf(false) }
    var showTypeFilterDropdown by remember { mutableStateOf(false) }
    var selectedFilterCourseId by remember { mutableStateOf<String?>(null) }
    var showDisciplineDropdown by remember { mutableStateOf(false) }
    var activeAssessmentDetailId by remember { mutableStateOf<String?>(null) }
    var activeCourseDetailId by remember { mutableStateOf<String?>(null) }
    var showCreateAssessmentSheet by remember { mutableStateOf(false) }
    var emptyCoursesNotice by remember { mutableStateOf(false) }
    var syllabusImportBannerMessage by remember { mutableStateOf<String?>(null) }
    var syllabusImportBannerIsWarning by remember { mutableStateOf(false) }

    val allSemesterEntries = remember(mutationVersion, activeSemester) {
        repository.listAllAssessments(semester = activeSemester, kindFilter = null)
    }

    val disciplines = remember(mutationVersion, activeSemester) {
        repository.listDisciplines(semester = activeSemester, departmentId = null)
    }
    val courseCodeById = remember(disciplines) {
        disciplines.associate { it.courseId to it.code.trim() }
    }

    val filteredEntries = remember(
        allSemesterEntries,
        selectedTypes,
        onlyCompleted,
        selectedFilterCourseId
    ) {
        filterActivityEntries(
            entries = allSemesterEntries,
            selectedTypes = selectedTypes,
            onlyCompleted = onlyCompleted,
            selectedCourseId = selectedFilterCourseId
        )
    }
    val typeFilterLabel = remember(selectedTypes, onlyCompleted) {
        formatActivityTypeFilterLabel(selectedTypes, onlyCompleted)
    }
    val hasActiveTypeFilter = selectedTypes.isNotEmpty() || onlyCompleted

    val activeAssessmentEntry: AssessmentWithCourse? = remember(
        activeAssessmentDetailId,
        allSemesterEntries
    ) {
        activeAssessmentDetailId?.let { id ->
            allSemesterEntries.find { it.assessment.id == id }
        }
    }

    val activeDetail: DisciplineDetail? = remember(activeCourseDetailId, mutationVersion) {
        activeCourseDetailId?.let { repository.getDisciplineDetail(it) }
    }

    val pendingCount = allSemesterEntries.count { !it.assessment.completed }
    val completedCount = allSemesterEntries.count { it.assessment.completed }
    val selectedCourseLabel = disciplines.find { it.courseId == selectedFilterCourseId }?.name
        ?: "Todas as disciplinas"
    val useTwoColumnCards = adaptive.widthClass != CampusWindowWidthClass.COMPACT
    val activityRows = remember(filteredEntries) { filteredEntries.chunked(2) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        LazyColumn(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .fillMaxHeight()
                .then(
                    if (adaptive.widthClass != CampusWindowWidthClass.COMPACT) {
                        Modifier
                            .widthIn(max = 960.dp)
                            .fillMaxWidth()
                    } else {
                        Modifier.fillMaxSize()
                    }
                ),
            contentPadding = PaddingValues(
                start = 20.dp,
                end = 20.dp,
                top = 20.dp,
                bottom = adaptive.bottomContentPaddingDp.dp
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                ActivitiesHeader(
                    activeSemester = activeSemester,
                    pendingCount = pendingCount,
                    completedCount = completedCount,
                    largeTouchTargets = a11y.largeTouchTargets,
                    minTouchTarget = minTouchTarget,
                    onNewDeadlineClick = {
                        if (disciplines.isEmpty()) {
                            emptyCoursesNotice = true
                        } else {
                            emptyCoursesNotice = false
                            showCreateAssessmentSheet = true
                        }
                    }
                )
            }

            if (syllabusImportBannerMessage != null) {
                item {
                    ActivitiesSyllabusImportBanner(
                        message = syllabusImportBannerMessage!!,
                        isWarning = syllabusImportBannerIsWarning,
                        isDark = isDark,
                        minTouchTarget = minTouchTarget,
                        onDismiss = { syllabusImportBannerMessage = null }
                    )
                }
            }

            if (emptyCoursesNotice && disciplines.isEmpty()) {
                item {
                    ActivitiesEmptyCoursesBanner()
                }
            }

            item {
                ActivitiesFilterBar(
                    typeFilterLabel = typeFilterLabel,
                    hasActiveTypeFilter = hasActiveTypeFilter,
                    selectedTypes = selectedTypes,
                    onlyCompleted = onlyCompleted,
                    showTypeFilterDropdown = showTypeFilterDropdown,
                    onShowTypeFilterDropdownChange = { showTypeFilterDropdown = it },
                    onSelectAllTypes = {
                        selectedTypesList = emptyList()
                        onlyCompleted = false
                        showTypeFilterDropdown = false
                    },
                    onToggleTypeFilter = { typeKey ->
                        selectedTypesList = if (typeKey in selectedTypes) {
                            selectedTypesList.filterNot { it == typeKey }
                        } else {
                            (selectedTypesList + typeKey).distinct()
                        }
                    },
                    onToggleOnlyCompleted = {
                        onlyCompleted = !onlyCompleted
                    },
                    disciplines = disciplines,
                    selectedFilterCourseId = selectedFilterCourseId,
                    selectedCourseLabel = selectedCourseLabel,
                    showDisciplineDropdown = showDisciplineDropdown,
                    onShowDisciplineDropdownChange = { showDisciplineDropdown = it },
                    onSelectCourseId = { courseId ->
                        selectedFilterCourseId = courseId
                        showDisciplineDropdown = false
                    },
                    largeTouchTargets = a11y.largeTouchTargets,
                    minTouchTarget = minTouchTarget
                )
            }

            if (filteredEntries.isEmpty()) {
                item {
                    ActivitiesEmptyStateCard(
                        hasDisciplines = disciplines.isNotEmpty(),
                        typeFilterLabel = typeFilterLabel
                    )
                }
            } else if (useTwoColumnCards) {
                items(
                    items = activityRows,
                    key = { row -> "act_row_${row.joinToString("_") { it.assessment.id }}" }
                ) { rowEntries ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalAlignment = Alignment.Top
                    ) {
                        rowEntries.forEach { entry ->
                            Box(modifier = Modifier.weight(1f)) {
                                ConsolidatedAssessmentCard(
                                    entry = entry,
                                    courseCode = courseCodeById[entry.assessment.courseId].orEmpty(),
                                    isDark = isDark,
                                    minTouchTarget = minTouchTarget,
                                    onToggleCompleted = {
                                        repository.toggleAssessmentCompleted(
                                            assessmentId = entry.assessment.id,
                                            completed = !entry.assessment.completed
                                        )
                                    },
                                    onClick = { activeAssessmentDetailId = entry.assessment.id }
                                )
                            }
                        }
                        if (rowEntries.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            } else {
                items(
                    items = filteredEntries,
                    key = { it.assessment.id }
                ) { entry ->
                    ConsolidatedAssessmentCard(
                        entry = entry,
                        courseCode = courseCodeById[entry.assessment.courseId].orEmpty(),
                        isDark = isDark,
                        minTouchTarget = minTouchTarget,
                        onToggleCompleted = {
                            repository.toggleAssessmentCompleted(
                                assessmentId = entry.assessment.id,
                                completed = !entry.assessment.completed
                            )
                        },
                        onClick = { activeAssessmentDetailId = entry.assessment.id }
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "Toque em uma avaliação para editar data, horário ou título, excluir ou marcar como concluída.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.navigationBarsPadding())
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(start = 16.dp, end = 16.dp, bottom = 112.dp)
        )

        if (activeAssessmentEntry != null) {
            AssessmentDetailBottomSheet(
                entry = activeAssessmentEntry,
                disciplines = disciplines,
                onDismiss = { activeAssessmentDetailId = null },
                onToggleCompleted = { completed ->
                    repository.toggleAssessmentCompleted(
                        assessmentId = activeAssessmentEntry.assessment.id,
                        completed = completed
                    )
                },
                onSaveEdit = { courseId, title, kind, dueDate, dueTime, completed ->
                    repository.updateAssessment(
                        assessmentId = activeAssessmentEntry.assessment.id,
                        courseId = courseId,
                        title = title,
                        kind = kind,
                        dueDate = dueDate,
                        dueTime = dueTime,
                        completed = completed
                    )
                },
                onDelete = {
                    val deletedItem = activeAssessmentEntry.assessment
                    activeAssessmentDetailId = null
                    repository.deleteAssessment(deletedItem.id)
                    scope.launch {
                        snackbarHostState.currentSnackbarData?.dismiss()
                        val result = snackbarHostState.showSnackbar(
                            message = "Avaliação \"${deletedItem.title}\" excluída",
                            actionLabel = "Desfazer",
                            withDismissAction = true,
                            duration = SnackbarDuration.Long
                        )
                        if (result == SnackbarResult.ActionPerformed) {
                            val restored = repository.createAssessment(
                                courseId = deletedItem.courseId,
                                title = deletedItem.title,
                                kind = deletedItem.kind,
                                dueDate = deletedItem.dueDate,
                                dueTime = deletedItem.dueTime
                            )
                            if (deletedItem.completed) {
                                repository.toggleAssessmentCompleted(restored.id, true)
                            }
                        }
                    }
                },
                onOpenDiscipline = {
                    val targetCourseId = activeAssessmentEntry.assessment.courseId
                    activeAssessmentDetailId = null
                    activeCourseDetailId = targetCourseId
                }
            )
        }

        if (activeDetail != null) {
            DisciplineDetailBottomSheet(
                detail = activeDetail,
                onDismiss = { activeCourseDetailId = null },
                onSaveNotes = { notes ->
                    repository.updateDisciplineNotes(
                        courseId = activeDetail.summary.courseId,
                        notes = notes
                    )
                },
                onToggleAssessmentCompleted = { assessmentId, completed ->
                    repository.toggleAssessmentCompleted(
                        assessmentId = assessmentId,
                        completed = completed
                    )
                },
                onCreateAssessment = { title, kind, dueDate, dueTime ->
                    repository.createAssessment(
                        courseId = activeDetail.summary.courseId,
                        title = title,
                        kind = kind,
                        dueDate = dueDate,
                        dueTime = dueTime
                    )
                },
                onDeleteAssessment = { assessmentId ->
                    repository.deleteAssessment(assessmentId)
                },
                onUpdateCourseDetails = { name, professor, departmentId, workloadHours ->
                    repository.updateCourse(
                        courseId = activeDetail.summary.courseId,
                        name = name,
                        professor = professor,
                        departmentId = departmentId,
                        workloadHours = workloadHours
                    )
                },
                onUpdateAssessment = { assessmentId, courseId, title, kind, dueDate, dueTime, completed ->
                    repository.updateAssessment(
                        assessmentId = assessmentId,
                        courseId = courseId,
                        title = title,
                        kind = kind,
                        dueDate = dueDate,
                        dueTime = dueTime,
                        completed = completed
                    )
                }
            )
        }

        if (showCreateAssessmentSheet && disciplines.isNotEmpty()) {
            CreateAssessmentBottomSheet(
                repository = repository,
                semester = activeSemester,
                disciplines = disciplines,
                initialCourseId = selectedFilterCourseId ?: disciplines.first().courseId,
                onDismiss = { showCreateAssessmentSheet = false },
                onSyllabusImported = { message, isWarning ->
                    syllabusImportBannerMessage = message
                    syllabusImportBannerIsWarning = isWarning
                    showCreateAssessmentSheet = false
                },
                onSave = { courseId, title, kind, dueDate, dueTime ->
                    repository.createAssessment(
                        courseId = courseId,
                        title = title,
                        kind = kind,
                        dueDate = dueDate,
                        dueTime = dueTime
                    )
                    showCreateAssessmentSheet = false
                }
            )
        }
    }
}
