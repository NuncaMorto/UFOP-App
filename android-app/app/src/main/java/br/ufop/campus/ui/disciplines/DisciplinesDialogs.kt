package br.ufop.campus.ui.disciplines

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import br.ufop.campus.core.ffi.AbsenceHistoryEntry
import br.ufop.campus.core.ffi.CampusCoreRepository
import br.ufop.campus.core.ffi.CourseScheduleSlotItem
import br.ufop.campus.core.ffi.DisciplineDetail
import br.ufop.campus.core.map.MapMeetingFocusRequest
import br.ufop.campus.ui.curriculum.CurriculumMatrixModal
import br.ufop.campus.ui.theme.CampusAdaptiveLayoutInfo

private class DisciplineDetailCallbacks(
    val onDismiss: () -> Unit,
    val onSaveNotes: (String) -> Unit,
    val onToggleAssessmentCompleted: (String, Boolean) -> Unit,
    val onCreateAssessment: (String, String, String, String) -> Unit,
    val onDeleteAssessment: (String) -> Unit,
    val onAddScheduleSlot: (Int, String, String, String, String, String) -> Result<CourseScheduleSlotItem>,
    val onUpdateScheduleSlot: (String, Int, String, String, String, String, String) -> Result<CourseScheduleSlotItem>,
    val onDeleteScheduleSlot: (String) -> Unit,
    val onDeleteCourse: () -> Unit,
    val onUpdateAbsences: (Int) -> Unit,
    val onUpdateCourseDetails: (String, String, String, Int) -> Unit,
    val onUpdateAssessment: (String, String, String, String, String, String, Boolean) -> Unit
)

@Composable
private fun rememberDisciplineDetailCallbacks(
    courseId: String,
    viewModel: DisciplinesViewModel
): DisciplineDetailCallbacks = remember(courseId, viewModel) {
    DisciplineDetailCallbacks(
        onDismiss = { viewModel.dismissDisciplineDetail() },
        onSaveNotes = { updatedNotes ->
            viewModel.saveDisciplineNotes(
                courseId = courseId,
                notes = updatedNotes
            )
        },
        onToggleAssessmentCompleted = { assessmentId, completed ->
            viewModel.toggleAssessmentCompleted(
                assessmentId = assessmentId,
                completed = completed
            )
        },
        onCreateAssessment = { title, kind, dueDate, dueTime ->
            viewModel.createAssessment(
                courseId = courseId,
                title = title,
                kind = kind,
                dueDate = dueDate,
                dueTime = dueTime
            )
        },
        onDeleteAssessment = { assessmentId ->
            viewModel.deleteAssessment(assessmentId)
        },
        onAddScheduleSlot = { dayOfWeek, startTime, endTime, building, room, shortLocation ->
            viewModel.addScheduleSlot(
                courseId = courseId,
                dayOfWeek = dayOfWeek,
                startTime = startTime,
                endTime = endTime,
                building = building,
                room = room,
                shortLocation = shortLocation
            )
        },
        onUpdateScheduleSlot = { slotId, dayOfWeek, startTime, endTime, building, room, shortLocation ->
            viewModel.updateScheduleSlot(
                slotId = slotId,
                dayOfWeek = dayOfWeek,
                startTime = startTime,
                endTime = endTime,
                building = building,
                room = room,
                shortLocation = shortLocation
            )
        },
        onDeleteScheduleSlot = { slotId ->
            viewModel.deleteScheduleSlot(slotId)
        },
        onDeleteCourse = {
            viewModel.deleteCourse(courseId)
        },
        onUpdateAbsences = { newTotal ->
            viewModel.updateCourseAbsences(courseId, newTotal)
        },
        onUpdateCourseDetails = { name, professor, departmentId, workloadHours ->
            viewModel.updateCourseDetails(
                courseId = courseId,
                name = name,
                professor = professor,
                departmentId = departmentId,
                workloadHours = workloadHours
            )
        },
        onUpdateAssessment = { assessmentId, targetCourseId, title, kind, dueDate, dueTime, completed ->
            viewModel.updateAssessment(
                assessmentId = assessmentId,
                courseId = targetCourseId,
                title = title,
                kind = kind,
                dueDate = dueDate,
                dueTime = dueTime,
                completed = completed
            )
        }
    )
}

@Composable
internal fun DisciplineDetailSidePane(
    detail: DisciplineDetail,
    scheduleSlots: List<CourseScheduleSlotItem>,
    absenceHistory: List<AbsenceHistoryEntry>,
    viewModel: DisciplinesViewModel,
    onOpenLocationInMap: ((MapMeetingFocusRequest) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val callbacks = rememberDisciplineDetailCallbacks(
        courseId = detail.summary.courseId,
        viewModel = viewModel
    )
    DisciplineDetailPaneContent(
        detail = detail,
        modifier = modifier,
        onDismiss = callbacks.onDismiss,
        onSaveNotes = callbacks.onSaveNotes,
        onToggleAssessmentCompleted = callbacks.onToggleAssessmentCompleted,
        onCreateAssessment = callbacks.onCreateAssessment,
        onDeleteAssessment = callbacks.onDeleteAssessment,
        scheduleSlots = scheduleSlots,
        absenceHistory = absenceHistory,
        onAddScheduleSlot = callbacks.onAddScheduleSlot,
        onUpdateScheduleSlot = callbacks.onUpdateScheduleSlot,
        onDeleteScheduleSlot = callbacks.onDeleteScheduleSlot,
        onDeleteCourse = callbacks.onDeleteCourse,
        onUpdateAbsences = callbacks.onUpdateAbsences,
        onUpdateCourseDetails = callbacks.onUpdateCourseDetails,
        onUpdateAssessment = callbacks.onUpdateAssessment,
        onOpenLocationInMap = onOpenLocationInMap
    )
}

@Composable
internal fun DisciplinesModalOverlays(
    repository: CampusCoreRepository,
    uiState: DisciplinesUiState,
    adaptive: CampusAdaptiveLayoutInfo,
    showCurriculumMatrix: Boolean,
    onDismissCurriculumMatrix: () -> Unit,
    viewModel: DisciplinesViewModel,
    onOpenLocationInMap: ((MapMeetingFocusRequest) -> Unit)? = null
) {
    val activeDetail = uiState.activeDetail

    // Bottom Sheet de Detalhe da Disciplina (somente quando não estiver em layout de 2 painéis)
    if (activeDetail != null && !adaptive.useTwoPaneLayout) {
        DisciplineDetailModalSheetHost(
            detail = activeDetail,
            scheduleSlots = uiState.activeDetailSlots,
            absenceHistory = uiState.activeDetailHistory,
            viewModel = viewModel,
            onOpenLocationInMap = onOpenLocationInMap
        )
    }

    if (uiState.showAddCourseSheet) {
        AddCourseModalSheetHost(
            repository = repository,
            uiState = uiState,
            viewModel = viewModel
        )
    }

    if (showCurriculumMatrix) {
        CurriculumMatrixDialog(
            repository = repository,
            onDismiss = onDismissCurriculumMatrix
        )
    }
}

@Composable
private fun DisciplineDetailModalSheetHost(
    detail: DisciplineDetail,
    scheduleSlots: List<CourseScheduleSlotItem>,
    absenceHistory: List<AbsenceHistoryEntry>,
    viewModel: DisciplinesViewModel,
    onOpenLocationInMap: ((MapMeetingFocusRequest) -> Unit)? = null
) {
    val callbacks = rememberDisciplineDetailCallbacks(
        courseId = detail.summary.courseId,
        viewModel = viewModel
    )
    DisciplineDetailBottomSheet(
        detail = detail,
        onDismiss = callbacks.onDismiss,
        onSaveNotes = callbacks.onSaveNotes,
        onToggleAssessmentCompleted = callbacks.onToggleAssessmentCompleted,
        onCreateAssessment = callbacks.onCreateAssessment,
        onDeleteAssessment = callbacks.onDeleteAssessment,
        scheduleSlots = scheduleSlots,
        absenceHistory = absenceHistory,
        onAddScheduleSlot = callbacks.onAddScheduleSlot,
        onUpdateScheduleSlot = callbacks.onUpdateScheduleSlot,
        onDeleteScheduleSlot = callbacks.onDeleteScheduleSlot,
        onDeleteCourse = callbacks.onDeleteCourse,
        onUpdateAbsences = callbacks.onUpdateAbsences,
        onUpdateCourseDetails = callbacks.onUpdateCourseDetails,
        onUpdateAssessment = callbacks.onUpdateAssessment,
        onOpenLocationInMap = onOpenLocationInMap
    )
}

@Composable
private fun AddCourseModalSheetHost(
    repository: CampusCoreRepository,
    uiState: DisciplinesUiState,
    viewModel: DisciplinesViewModel
) {
    val addCourseDepartments = uiState.allDepartments.ifEmpty { repository.listDepartments() }
    AddCourseBottomSheet(
        semester = uiState.selectedSemester,
        departments = addCourseDepartments,
        initialDepartmentId = uiState.selectedDepartmentId
            ?: uiState.departments.firstOrNull()?.id
            ?: addCourseDepartments.firstOrNull()?.id
            ?: "decom",
        onDismiss = { viewModel.dismissAddCourseSheet() },
        onSaveCourse = { departmentId, name, professor, workloadHours, colorOverrideHex, initialSlot ->
            viewModel.createCourse(
                departmentId = departmentId,
                name = name,
                professor = professor,
                workloadHours = workloadHours,
                colorOverrideHex = colorOverrideHex,
                initialSlot = initialSlot
            )
        }
    )
}

@Composable
private fun CurriculumMatrixDialog(
    repository: CampusCoreRepository,
    onDismiss: () -> Unit
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false
        )
    ) {
        CurriculumMatrixModal(
            repository = repository,
            onDismiss = onDismiss
        )
    }
}
