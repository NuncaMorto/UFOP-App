package br.ufop.campus.ui.disciplines

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import br.ufop.campus.core.ffi.AbsenceHistoryEntry
import br.ufop.campus.core.ffi.AssessmentItem
import br.ufop.campus.core.ffi.CampusCoreRepository
import br.ufop.campus.core.ffi.CourseScheduleSlotItem
import br.ufop.campus.core.ffi.DepartmentColorOption
import br.ufop.campus.core.ffi.DisciplineDetail
import br.ufop.campus.core.ffi.DisciplineSummaryCard
import br.ufop.campus.core.ffi.UfopBuildingOption
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

data class InitialCourseSlotInput(
    val dayOfWeek: Int,
    val startTime: String,
    val endTime: String,
    val building: String,
    val room: String,
    val shortLocation: String
)

data class DisciplinesUiState(
    val selectedSemester: String = DEFAULT_SEMESTER,
    val availableSemesters: List<String> = listOf(DEFAULT_SEMESTER),
    val selectedDepartmentId: String? = null,
    val searchQuery: String = "",
    val departments: List<DepartmentColorOption> = emptyList(),
    val allDepartments: List<DepartmentColorOption> = emptyList(),
    val ufopBuildings: List<UfopBuildingOption> = emptyList(),
    val disciplines: List<DisciplineSummaryCard> = emptyList(),
    val activeDetail: DisciplineDetail? = null,
    val activeDetailSlots: List<CourseScheduleSlotItem> = emptyList(),
    val activeDetailHistory: List<AbsenceHistoryEntry> = emptyList(),
    val showAddCourseSheet: Boolean = false
) {
    val selectedDepartmentLabel: String
        get() = departments.find { it.id == selectedDepartmentId }?.name
            ?: allDepartments.find { it.id == selectedDepartmentId }?.name
            ?: "Todos os departamentos"

    companion object {
        const val DEFAULT_SEMESTER = "2026.2"
    }
}

class DisciplinesViewModel(
    internal val repository: CampusCoreRepository
) : ViewModel() {

    private val initialSemesters = repository.listAvailableSemesters()
        .ifEmpty { listOf(DisciplinesUiState.DEFAULT_SEMESTER) }
    private val initialSelectedSemester = repository.getStudentProfile().activeSemester
        .ifBlank { initialSemesters.first() }

    private val _uiState = MutableStateFlow(
        DisciplinesUiState(
            selectedSemester = initialSelectedSemester,
            availableSemesters = initialSemesters,
            departments = repository.listDepartmentsForSemester(initialSelectedSemester),
            allDepartments = repository.listDepartments(),
            ufopBuildings = repository.listUfopBuildings(),
            disciplines = repository.listDisciplines(
                semester = initialSelectedSemester,
                departmentId = null
            )
        )
    )
    val uiState: StateFlow<DisciplinesUiState> = _uiState.asStateFlow()

    val searchQuery: String
        get() = _uiState.value.searchQuery

    val activeDetailHistory: List<AbsenceHistoryEntry>
        get() = _uiState.value.activeDetailHistory

    init {
        sharedRepository = repository
        activeInstance = this
        viewModelScope.launch {
            repository.mutationVersion.collectLatest {
                refreshDisciplinesAndDetail()
            }
        }
    }

    override fun onCleared() {
        if (activeInstance === this) {
            activeInstance = null
        }
        super.onCleared()
    }

    fun onSearchQueryChange(query: String) {
        val current = _uiState.value
        val semesterDepartments = repository.listDepartmentsForSemester(current.selectedSemester)
        val effectiveDepartmentId = current.selectedDepartmentId
            ?.takeIf { id -> semesterDepartments.any { it.id == id } }
        val updatedList = filterDisciplines(
            semester = current.selectedSemester,
            departmentId = effectiveDepartmentId,
            query = query
        )
        _uiState.value = current.copy(
            searchQuery = query,
            selectedDepartmentId = effectiveDepartmentId,
            availableSemesters = repository.listAvailableSemesters()
                .ifEmpty { listOf(DisciplinesUiState.DEFAULT_SEMESTER) },
            departments = semesterDepartments,
            allDepartments = repository.listDepartments(),
            disciplines = updatedList
        )
    }

    fun selectDepartment(departmentId: String?) {
        val current = _uiState.value
        val semesterDepartments = repository.listDepartmentsForSemester(current.selectedSemester)
        val effectiveDepartmentId = departmentId
            ?.takeIf { id -> semesterDepartments.any { it.id == id } }
        val updatedList = filterDisciplines(
            semester = current.selectedSemester,
            departmentId = effectiveDepartmentId,
            query = current.searchQuery
        )
        _uiState.value = current.copy(
            selectedDepartmentId = effectiveDepartmentId,
            availableSemesters = repository.listAvailableSemesters()
                .ifEmpty { listOf(DisciplinesUiState.DEFAULT_SEMESTER) },
            departments = semesterDepartments,
            allDepartments = repository.listDepartments(),
            disciplines = updatedList
        )
    }

    fun selectSemester(semester: String) {
        val current = _uiState.value
        val semesterDepartments = repository.listDepartmentsForSemester(semester)
        val effectiveDepartmentId = current.selectedDepartmentId
            ?.takeIf { id -> semesterDepartments.any { it.id == id } }
        val updatedList = filterDisciplines(
            semester = semester,
            departmentId = effectiveDepartmentId,
            query = current.searchQuery
        )
        _uiState.value = current.copy(
            selectedSemester = semester,
            selectedDepartmentId = effectiveDepartmentId,
            availableSemesters = repository.listAvailableSemesters()
                .ifEmpty { listOf(DisciplinesUiState.DEFAULT_SEMESTER) },
            departments = semesterDepartments,
            allDepartments = repository.listDepartments(),
            disciplines = updatedList
        )
    }

    fun openAddCourseSheet() {
        _uiState.value = _uiState.value.copy(showAddCourseSheet = true)
    }

    fun dismissAddCourseSheet() {
        _uiState.value = _uiState.value.copy(showAddCourseSheet = false)
    }

    fun openDisciplineDetail(courseId: String) {
        val detail = repository.getDisciplineDetail(courseId)
        val slots = repository.listCourseSlots(courseId)
        val history = repository.listAbsenceHistory(courseId)
        _uiState.value = _uiState.value.copy(
            activeDetail = detail,
            activeDetailSlots = slots,
            activeDetailHistory = history
        )
    }

    fun dismissDisciplineDetail() {
        _uiState.value = _uiState.value.copy(
            activeDetail = null,
            activeDetailSlots = emptyList(),
            activeDetailHistory = emptyList()
        )
    }

    fun createCourse(
        departmentId: String,
        name: String,
        professor: String,
        workloadHours: Int,
        colorOverrideHex: String? = null,
        initialSlot: InitialCourseSlotInput? = null
    ): Result<DisciplineSummaryCard> = runCatching {
        val semester = _uiState.value.selectedSemester
        val created = repository.createCourse(
            semester = semester,
            departmentId = departmentId,
            name = name,
            professor = professor,
            workloadHours = workloadHours,
            colorOverrideHex = null
        )
        if (initialSlot != null) {
            try {
                repository.addScheduleSlot(
                    courseId = created.courseId,
                    dayOfWeek = initialSlot.dayOfWeek,
                    startTime = initialSlot.startTime,
                    endTime = initialSlot.endTime,
                    building = initialSlot.building,
                    room = initialSlot.room,
                    shortLocation = initialSlot.shortLocation
                )
            } catch (e: Throwable) {
                repository.deleteCourse(created.courseId)
                throw e
            }
        }
        val currentDeptFilter = _uiState.value.selectedDepartmentId
        val nextDeptFilter = if (currentDeptFilter != null && currentDeptFilter != created.departmentId) {
            null
        } else {
            currentDeptFilter
        }
        _uiState.value = _uiState.value.copy(
            selectedDepartmentId = nextDeptFilter,
            showAddCourseSheet = false
        )
        refreshDisciplinesAndDetail()
        created
    }

    fun deleteCourse(courseId: String) {
        val current = _uiState.value
        if (current.activeDetail?.summary?.courseId == courseId) {
            _uiState.value = current.copy(
                activeDetail = null,
                activeDetailSlots = emptyList(),
                activeDetailHistory = emptyList()
            )
        }
        repository.deleteCourse(courseId)
        refreshDisciplinesAndDetail()
    }

    fun addScheduleSlot(
        courseId: String,
        dayOfWeek: Int,
        startTime: String,
        endTime: String,
        building: String,
        room: String,
        shortLocation: String
    ): Result<CourseScheduleSlotItem> = runCatching {
        val created = repository.addScheduleSlot(
            courseId = courseId,
            dayOfWeek = dayOfWeek,
            startTime = startTime,
            endTime = endTime,
            building = building,
            room = room,
            shortLocation = shortLocation
        )
        refreshDisciplinesAndDetail()
        created
    }

    fun updateScheduleSlot(
        slotId: String,
        dayOfWeek: Int,
        startTime: String,
        endTime: String,
        building: String,
        room: String,
        shortLocation: String
    ): Result<CourseScheduleSlotItem> = runCatching {
        val updated = repository.updateScheduleSlot(
            slotId = slotId,
            dayOfWeek = dayOfWeek,
            startTime = startTime,
            endTime = endTime,
            building = building,
            room = room,
            shortLocation = shortLocation
        )
        refreshDisciplinesAndDetail()
        updated
    }

    fun deleteScheduleSlot(slotId: String) {
        repository.deleteScheduleSlot(slotId)
        refreshDisciplinesAndDetail()
    }

    fun saveDisciplineNotes(courseId: String, notes: String) {
        repository.updateDisciplineNotes(courseId = courseId, notes = notes)
        val refreshedDetail = repository.getDisciplineDetail(courseId)
        val refreshedSlots = repository.listCourseSlots(courseId)
        val refreshedHistory = repository.listAbsenceHistory(courseId)
        _uiState.value = _uiState.value.copy(
            activeDetail = refreshedDetail,
            activeDetailSlots = refreshedSlots,
            activeDetailHistory = refreshedHistory
        )
    }

    fun updateCourseAbsences(
        courseId: String,
        totalAbsences: Int
    ): Result<DisciplineSummaryCard> = runCatching {
        val updated = repository.updateCourseAbsences(
            courseId = courseId,
            totalAbsences = totalAbsences.coerceAtLeast(0)
        )
        refreshDisciplinesAndDetail()
        updated
    }

    fun updateCourseDetails(
        courseId: String,
        name: String,
        professor: String,
        departmentId: String,
        workloadHours: Int
    ): Result<DisciplineSummaryCard> = runCatching {
        val updated = repository.updateCourse(
            courseId = courseId,
            name = name,
            professor = professor,
            departmentId = departmentId,
            workloadHours = workloadHours
        )
        refreshDisciplinesAndDetail()
        updated
    }

    fun createAssessment(
        courseId: String,
        title: String,
        kind: String,
        dueDate: String,
        dueTime: String
    ): AssessmentItem? {
        val cleanTitle = title.trim()
        if (cleanTitle.isEmpty()) return null
        val created = repository.createAssessment(
            courseId = courseId,
            title = cleanTitle,
            kind = kind.ifBlank { "Trabalho" },
            dueDate = dueDate.ifBlank { "2026-11-28" },
            dueTime = dueTime.ifBlank { "18:00" }
        )
        refreshDisciplinesAndDetail()
        return created
    }

    fun updateAssessment(
        assessmentId: String,
        courseId: String,
        title: String,
        kind: String,
        dueDate: String,
        dueTime: String,
        completed: Boolean
    ): AssessmentItem {
        val updated = repository.updateAssessment(
            assessmentId = assessmentId,
            courseId = courseId,
            title = title,
            kind = kind,
            dueDate = dueDate,
            dueTime = dueTime,
            completed = completed
        )
        refreshDisciplinesAndDetail()
        return updated
    }

    fun toggleAssessmentCompleted(assessmentId: String, completed: Boolean): AssessmentItem {
        val updated = repository.toggleAssessmentCompleted(
            assessmentId = assessmentId,
            completed = completed
        )
        refreshDisciplinesAndDetail()
        return updated
    }

    fun deleteAssessment(assessmentId: String) {
        repository.deleteAssessment(assessmentId = assessmentId)
        refreshDisciplinesAndDetail()
    }

    fun listUfopBuildings(campusId: String? = null): List<UfopBuildingOption> =
        repository.listUfopBuildings(campusId)

    fun importSyllabusPdf(
        courseId: String = _uiState.value.activeDetail?.summary?.courseId.orEmpty(),
        pdfBytes: ByteArray
    ): Result<Int> = runCatching {
        require(courseId.isNotBlank()) { "Disciplina não selecionada." }
        val importedCount = repository.importCourseSyllabusPdf(courseId = courseId, pdfBytes = pdfBytes)
        refreshDisciplinesAndDetail()
        importedCount
    }

    fun importSyllabusText(
        courseId: String = _uiState.value.activeDetail?.summary?.courseId.orEmpty(),
        rawText: String
    ): Result<Int> = runCatching {
        require(courseId.isNotBlank()) { "Disciplina não selecionada." }
        val importedCount = repository.importCourseSyllabusText(courseId = courseId, rawText = rawText)
        refreshDisciplinesAndDetail()
        importedCount
    }

    private fun filterDisciplines(
        semester: String,
        departmentId: String?,
        query: String
    ): List<DisciplineSummaryCard> {
        val cleanQuery = query.trim()
        val baseList = if (cleanQuery.isEmpty()) {
            repository.listDisciplines(
                semester = semester,
                departmentId = departmentId
            )
        } else {
            val matched = repository.searchCampusItems(
                semester = semester,
                query = cleanQuery
            ).courses
            if (departmentId.isNullOrBlank()) {
                matched
            } else {
                matched.filter { it.departmentId == departmentId }
            }
        }
        return baseList
    }

    private fun refreshDisciplinesAndDetail() {
        val current = _uiState.value
        val updatedAllDepartments = repository.listDepartments()
        val updatedBuildings = repository.listUfopBuildings()
        val updatedSemesters = repository.listAvailableSemesters()
            .ifEmpty { listOf(DisciplinesUiState.DEFAULT_SEMESTER) }
        val effectiveSemester = if (current.selectedSemester in updatedSemesters) {
            current.selectedSemester
        } else {
            repository.getStudentProfile().activeSemester.ifBlank { updatedSemesters.first() }
        }
        val updatedDepartments = repository.listDepartmentsForSemester(effectiveSemester)
        val effectiveDepartmentId = current.selectedDepartmentId
            ?.takeIf { id -> updatedDepartments.any { it.id == id } }
        val allSemesterDisciplines = repository.listDisciplines(
            semester = effectiveSemester,
            departmentId = null
        )
        val updatedList = filterDisciplines(
            semester = effectiveSemester,
            departmentId = effectiveDepartmentId,
            query = current.searchQuery
        )
        val activeCourseId = current.activeDetail?.summary?.courseId
        val updatedDetail = activeCourseId
            ?.takeIf { id -> allSemesterDisciplines.any { it.courseId == id } }
            ?.let { id -> repository.getDisciplineDetail(id) }
        val updatedSlots = if (updatedDetail != null) {
            repository.listCourseSlots(updatedDetail.summary.courseId)
        } else {
            emptyList()
        }
        val updatedHistory = if (updatedDetail != null) {
            repository.listAbsenceHistory(updatedDetail.summary.courseId)
        } else {
            emptyList()
        }
        _uiState.value = current.copy(
            selectedSemester = effectiveSemester,
            selectedDepartmentId = effectiveDepartmentId,
            availableSemesters = updatedSemesters,
            departments = updatedDepartments,
            allDepartments = updatedAllDepartments,
            ufopBuildings = updatedBuildings,
            disciplines = updatedList,
            activeDetail = updatedDetail,
            activeDetailSlots = updatedSlots,
            activeDetailHistory = updatedHistory
        )
    }

    companion object {
        @Volatile
        internal var sharedRepository: CampusCoreRepository? = null

        @Volatile
        internal var activeInstance: DisciplinesViewModel? = null

        fun provideFactory(repository: CampusCoreRepository): ViewModelProvider.Factory {
            sharedRepository = repository
            return object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return DisciplinesViewModel(repository) as T
                }
            }
        }
    }
}
