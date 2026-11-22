package br.ufop.campus.ui.agenda

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import br.ufop.campus.core.ffi.AcademicCalendarEvent
import br.ufop.campus.core.ffi.AgendaDayOverview
import br.ufop.campus.core.ffi.AgendaTimelineItem
import br.ufop.campus.core.ffi.AttendanceStatus
import br.ufop.campus.core.ffi.CampusCoreRepository
import br.ufop.campus.core.ffi.ClassCheckInDetail
import br.ufop.campus.core.ffi.CourseScheduleSlotItem
import br.ufop.campus.core.ffi.DayActivityIndicator
import br.ufop.campus.core.ffi.StudentProfile
import br.ufop.campus.core.ffi.TimelineItemKind
import br.ufop.campus.core.ffi.UfopCampusInfo
import br.ufop.campus.ui.components.formatIsoToBrDate
import br.ufop.campus.ui.components.isEndTimeAfterStart
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.util.Locale

data class NextClassPreview(
    val courseName: String,
    val isoDate: String,
    val dayLabel: String,
    val startTime: String,
    val endTime: String,
    val location: String,
    val isHappeningNow: Boolean,
    val isToday: Boolean,
    val slotId: String? = null
)

data class AgendaUiState(
    val selectedIsoDate: String,
    val dayOverview: AgendaDayOverview,
    val activeCampus: UfopCampusInfo,
    val studentProfile: StudentProfile,
    val hasCoursesInSemester: Boolean = false,
    val nextClassPreview: NextClassPreview? = null,
    val semesterRangeNotice: String? = null,
    val actionErrorMessage: String? = null,
    val isMonthCalendarExpanded: Boolean = false,
    val displayedYearMonth: YearMonth,
    val semesterMonths: List<YearMonth> = emptyList(),
    val monthIndicators: Map<Int, DayActivityIndicator> = emptyMap(),
    val academicEvents: List<AcademicCalendarEvent> = emptyList(),
    val activeCheckIn: ClassCheckInDetail? = null,
    val activeCheckInWorkloadHours: Int = 60,
    val activeCheckInSlot: CourseScheduleSlotItem? = null,
    val checkInEditError: String? = null,
    val activeCommitmentDetail: AgendaTimelineItem? = null,
    val editingCommitment: AgendaTimelineItem? = null,
    val isAddCommitmentSheetOpen: Boolean = false,
    val statusBannerMessage: String? = null
) {
    /** `true` quando o dia selecionado é o dia de hoje (esconde o botão flutuante "Hoje"). */
    val isViewingToday: Boolean
        get() = selectedIsoDate == LocalDate.now().toString()
}

class AgendaViewModel(
    private val repository: CampusCoreRepository
) : ViewModel() {

    private val _uiState: MutableStateFlow<AgendaUiState>

    init {
        val initialStudentProfile = repository.getStudentProfile()
        val activeSemester = initialStudentProfile.activeSemester.ifBlank { "2026.2" }
        val initialAcademicSummary = repository.getAcademicProfileSummary(activeSemester)
        val (initialIsoDate, initialSemesterNotice) = computeInitialIsoDateWithNotice(
            profile = initialStudentProfile,
            semester = activeSemester
        )
        val initialYearMonth = YearMonth.from(LocalDate.parse(initialIsoDate))
        val initialSemesterMonths = computeSemesterMonths(initialStudentProfile)

        _uiState = MutableStateFlow(
            AgendaUiState(
                selectedIsoDate = initialIsoDate,
                dayOverview = repository.getAgendaDay(initialIsoDate),
                activeCampus = initialAcademicSummary.activeCampus,
                studentProfile = initialStudentProfile,
                hasCoursesInSemester = repository.listDisciplines(activeSemester, null).isNotEmpty(),
                nextClassPreview = computeNextClassPreview(),
                semesterRangeNotice = initialSemesterNotice,
                actionErrorMessage = null,
                displayedYearMonth = initialYearMonth,
                semesterMonths = initialSemesterMonths,
                monthIndicators = repository.getMonthActivityIndicators(
                    year = initialYearMonth.year,
                    month = initialYearMonth.monthValue,
                    semester = activeSemester
                ),
                academicEvents = loadAcademicEvents(activeSemester)
            )
        )

        viewModelScope.launch {
            repository.mutationVersion.collectLatest {
                refreshDayOverview()
            }
        }

        // Atualização leve automática do clima a cada 5 minutos enquanto a Agenda está ativa
        viewModelScope.launch(Dispatchers.IO) {
            while (isActive) {
                runCatching { repository.refreshWeatherIfNeeded() }
                delay(WEATHER_REFRESH_INTERVAL_MS)
            }
        }
    }

    val uiState: StateFlow<AgendaUiState> = _uiState.asStateFlow()

    fun selectDay(isoDate: String) {
        val overview = repository.getAgendaDay(isoDate)
        val selectedDate = runCatching { LocalDate.parse(overview.isoDate) }.getOrNull()
        val targetYm = selectedDate?.let { YearMonth.from(it) } ?: _uiState.value.displayedYearMonth
        val semester = _uiState.value.studentProfile.activeSemester.ifBlank { "2026.2" }
        val indicators = if (targetYm != _uiState.value.displayedYearMonth || _uiState.value.monthIndicators.isEmpty()) {
            repository.getMonthActivityIndicators(targetYm.year, targetYm.monthValue, semester)
        } else {
            _uiState.value.monthIndicators
        }

        _uiState.value = _uiState.value.copy(
            selectedIsoDate = overview.isoDate,
            dayOverview = overview,
            displayedYearMonth = targetYm,
            monthIndicators = indicators,
            activeCheckIn = null,
            checkInEditError = null,
            activeCommitmentDetail = null,
            editingCommitment = null,
            isAddCommitmentSheetOpen = false,
            statusBannerMessage = null
        )
    }

    /** Volta imediatamente para o dia de hoje (semana atual). */
    fun jumpToToday() {
        selectDay(LocalDate.now().toString())
    }

    /** Navega para o dia da aula em andamento ou próxima aula encontrada nos próximos 14 dias. */
    fun jumpToNextClassDay() {
        val next = _uiState.value.nextClassPreview ?: return
        selectDay(next.isoDate)
    }

    fun dismissSemesterRangeNotice() {
        _uiState.value = _uiState.value.copy(semesterRangeNotice = null)
    }

    fun clearActionError() {
        _uiState.value = _uiState.value.copy(actionErrorMessage = null)
    }

    fun toggleMonthCalendar() {
        val current = _uiState.value
        val nextExpanded = !current.isMonthCalendarExpanded
        val semester = current.studentProfile.activeSemester.ifBlank { "2026.2" }
        val targetYm = if (nextExpanded) {
            runCatching { YearMonth.from(LocalDate.parse(current.selectedIsoDate)) }
                .getOrDefault(current.displayedYearMonth)
        } else {
            current.displayedYearMonth
        }
        val indicators = if (nextExpanded) {
            repository.getMonthActivityIndicators(targetYm.year, targetYm.monthValue, semester)
        } else {
            current.monthIndicators
        }
        _uiState.value = current.copy(
            isMonthCalendarExpanded = nextExpanded,
            displayedYearMonth = targetYm,
            monthIndicators = indicators
        )
    }

    fun selectDisplayedMonth(yearMonth: YearMonth) {
        val semester = _uiState.value.studentProfile.activeSemester.ifBlank { "2026.2" }
        val indicators = repository.getMonthActivityIndicators(
            year = yearMonth.year,
            month = yearMonth.monthValue,
            semester = semester
        )
        _uiState.value = _uiState.value.copy(
            displayedYearMonth = yearMonth,
            monthIndicators = indicators
        )
    }

    /** Seleciona [month] (1..12) preservando o ano atualmente exibido no calendário. */
    fun selectMonthInDisplayedYear(month: Int) {
        if (month !in 1..12) return
        val year = _uiState.value.displayedYearMonth.year
        selectDisplayedMonth(YearMonth.of(year, month))
    }

    fun previousMonth() {
        selectDisplayedMonth(_uiState.value.displayedYearMonth.minusMonths(1))
    }

    fun nextMonth() {
        selectDisplayedMonth(_uiState.value.displayedYearMonth.plusMonths(1))
    }

    fun openClassCheckIn(slotId: String) {
        val currentIso = _uiState.value.selectedIsoDate
        val detail = repository.getClassCheckIn(slotId = slotId, isoDate = currentIso)
        val workload = if (detail.courseId.isNotBlank()) {
            runCatching { repository.getDisciplineDetail(detail.courseId).summary.workloadHours }
                .getOrDefault(60)
        } else {
            60
        }
        val slotItem = if (detail.courseId.isNotBlank()) {
            runCatching {
                repository.listCourseSlots(detail.courseId).find { it.slotId == detail.slotId }
            }.getOrNull()
        } else {
            null
        }
        _uiState.value = _uiState.value.copy(
            activeCheckIn = detail,
            activeCheckInWorkloadHours = workload,
            activeCheckInSlot = slotItem,
            checkInEditError = null,
            activeCommitmentDetail = null,
            editingCommitment = null,
            isAddCommitmentSheetOpen = false,
            statusBannerMessage = null
        )
    }

    fun dismissClassCheckIn() {
        _uiState.value = _uiState.value.copy(
            activeCheckIn = null,
            activeCheckInSlot = null,
            checkInEditError = null
        )
    }

    fun clearCheckInEditError() {
        _uiState.value = _uiState.value.copy(checkInEditError = null)
    }

    fun saveClassCheckIn(
        slotId: String,
        status: AttendanceStatus
    ) {
        val currentIso = _uiState.value.selectedIsoDate
        runCatching {
            val updatedDiscipline = repository.saveClassCheckIn(
                slotId = slotId,
                isoDate = currentIso,
                status = status,
                colorOverrideHex = null
            )
            val refreshedDay = repository.getAgendaDay(currentIso)
            val feedback = when (status) {
                AttendanceStatus.ATTENDING ->
                    "${updatedDiscipline.name}: presença confirmada (${updatedDiscipline.currentAbsences}/${updatedDiscipline.maxAbsences} faltas)."
                AttendanceStatus.ABSENT ->
                    "${updatedDiscipline.name}: falta registrada (${updatedDiscipline.currentAbsences}/${updatedDiscipline.maxAbsences} faltas)."
                AttendanceStatus.CANCELLED ->
                    "${updatedDiscipline.name}: aula cancelada registrada (${updatedDiscipline.cancelledClasses} canceladas · não soma falta)."
            }
            Triple(refreshedDay, feedback, computeNextClassPreview())
        }.onSuccess { (refreshedDay, feedback, nextPreview) ->
            _uiState.value = _uiState.value.copy(
                dayOverview = refreshedDay,
                nextClassPreview = nextPreview,
                activeCheckIn = null,
                activeCheckInSlot = null,
                checkInEditError = null,
                actionErrorMessage = null,
                statusBannerMessage = feedback
            )
        }.onFailure { err ->
            _uiState.value = _uiState.value.copy(
                actionErrorMessage = err.message ?: "Não foi possível registrar a presença da aula."
            )
        }
    }

    fun saveEditedClassDetails(
        courseId: String,
        slotId: String,
        courseName: String,
        professor: String,
        departmentId: String,
        workloadHours: Int,
        dayOfWeek: Int,
        startTime: String,
        endTime: String,
        building: String,
        room: String
    ): Boolean {
        return try {
            require(isEndTimeAfterStart(startTime, endTime)) {
                "O horário de término deve ser após o início."
            }
            repository.updateCourse(
                courseId = courseId,
                name = courseName,
                professor = professor,
                departmentId = departmentId,
                workloadHours = workloadHours
            )
            repository.updateScheduleSlot(
                slotId = slotId,
                dayOfWeek = dayOfWeek,
                startTime = startTime,
                endTime = endTime,
                building = building,
                room = room,
                shortLocation = buildShortLocation(building, room)
            )
            val currentIso = _uiState.value.selectedIsoDate
            val updatedDetail = repository.getClassCheckIn(slotId = slotId, isoDate = currentIso)
            val updatedSlot = runCatching {
                repository.listCourseSlots(courseId).find { it.slotId == slotId }
            }.getOrNull()
            val refreshedDay = repository.getAgendaDay(currentIso)
            _uiState.value = _uiState.value.copy(
                dayOverview = refreshedDay,
                nextClassPreview = computeNextClassPreview(),
                activeCheckIn = updatedDetail,
                activeCheckInSlot = updatedSlot,
                checkInEditError = null,
                actionErrorMessage = null,
                statusBannerMessage = "Aula \"${updatedDetail.courseName}\" atualizada."
            )
            true
        } catch (e: Exception) {
            _uiState.value = _uiState.value.copy(
                checkInEditError = e.message ?: "Não foi possível salvar as alterações da aula."
            )
            false
        }
    }

    fun openCommitmentDetail(item: AgendaTimelineItem) {
        _uiState.value = _uiState.value.copy(
            activeCommitmentDetail = item,
            activeCheckIn = null,
            editingCommitment = null,
            isAddCommitmentSheetOpen = false,
            statusBannerMessage = null
        )
    }

    fun dismissCommitmentDetail() {
        _uiState.value = _uiState.value.copy(activeCommitmentDetail = null)
    }

    fun openAddCommitmentSheet() {
        _uiState.value = _uiState.value.copy(
            isAddCommitmentSheetOpen = true,
            editingCommitment = null,
            activeCommitmentDetail = null,
            activeCheckIn = null,
            statusBannerMessage = null
        )
    }

    fun openEditCommitmentSheet(item: AgendaTimelineItem) {
        _uiState.value = _uiState.value.copy(
            activeCommitmentDetail = null,
            editingCommitment = item,
            isAddCommitmentSheetOpen = true,
            activeCheckIn = null,
            statusBannerMessage = null
        )
    }

    fun dismissAddCommitmentSheet() {
        _uiState.value = _uiState.value.copy(
            isAddCommitmentSheetOpen = false,
            editingCommitment = null
        )
    }

    fun saveCommitment(
        title: String,
        description: String,
        commitmentDate: String,
        startTime: String,
        endTime: String,
        location: String
    ) {
        val currentState = _uiState.value
        val editing = currentState.editingCommitment
        val targetDate = commitmentDate.ifBlank { currentState.selectedIsoDate }

        runCatching {
            require(isEndTimeAfterStart(startTime, endTime)) {
                "O horário de término deve ser após o início."
            }
            val feedback = if (editing != null) {
                val updated = repository.updateCommitment(
                    commitmentId = editing.id,
                    title = title,
                    description = description,
                    commitmentDate = targetDate,
                    startTime = startTime,
                    endTime = endTime,
                    location = location
                )
                "Compromisso \"${updated.title}\" atualizado (${updated.startTime}–${updated.endTime})."
            } else {
                val created = repository.createCommitment(
                    title = title,
                    description = description,
                    commitmentDate = targetDate,
                    startTime = startTime,
                    endTime = endTime,
                    location = location,
                    weatherTempC = currentState.activeCampus.weatherTempC
                )
                "Compromisso \"${created.title}\" adicionado (${created.startTime}–${created.endTime})."
            }

            val refreshedDay = repository.getAgendaDay(currentState.selectedIsoDate)
            val semester = currentState.studentProfile.activeSemester.ifBlank { "2026.2" }
            val ym = currentState.displayedYearMonth
            val indicators = repository.getMonthActivityIndicators(ym.year, ym.monthValue, semester)
            Triple(refreshedDay, indicators, feedback)
        }.onSuccess { (refreshedDay, indicators, feedback) ->
            _uiState.value = currentState.copy(
                dayOverview = refreshedDay,
                monthIndicators = indicators,
                isAddCommitmentSheetOpen = false,
                editingCommitment = null,
                actionErrorMessage = null,
                statusBannerMessage = feedback
            )
        }.onFailure { err ->
            _uiState.value = currentState.copy(
                isAddCommitmentSheetOpen = false,
                editingCommitment = null,
                actionErrorMessage = err.message ?: "Não foi possível salvar o compromisso. Tente novamente."
            )
        }
    }

    fun deleteCommitment(commitmentId: String, title: String = "Compromisso") {
        val currentState = _uiState.value
        runCatching {
            repository.deleteCommitment(commitmentId)
            val refreshedDay = repository.getAgendaDay(currentState.selectedIsoDate)
            val semester = currentState.studentProfile.activeSemester.ifBlank { "2026.2" }
            val ym = currentState.displayedYearMonth
            val indicators = repository.getMonthActivityIndicators(ym.year, ym.monthValue, semester)
            refreshedDay to indicators
        }.onSuccess { (refreshedDay, indicators) ->
            _uiState.value = currentState.copy(
                dayOverview = refreshedDay,
                monthIndicators = indicators,
                activeCommitmentDetail = if (currentState.activeCommitmentDetail?.id == commitmentId) null else currentState.activeCommitmentDetail,
                actionErrorMessage = null,
                statusBannerMessage = "\"$title\" removido da agenda."
            )
        }.onFailure { err ->
            _uiState.value = currentState.copy(
                actionErrorMessage = err.message ?: "Não foi possível remover \"$title\" da agenda."
            )
        }
    }

    fun clearBannerMessage() {
        _uiState.value = _uiState.value.copy(statusBannerMessage = null)
    }

    private fun refreshDayOverview() {
        val currentState = _uiState.value
        val currentIso = currentState.selectedIsoDate
        val profile = repository.getStudentProfile()
        val activeSemester = profile.activeSemester.ifBlank { "2026.2" }
        val academicSummary = repository.getAcademicProfileSummary(activeSemester)
        val updatedOverview = repository.getAgendaDay(currentIso)
        val updatedCheckIn = currentState.activeCheckIn?.let { active ->
            runCatching {
                repository.getClassCheckIn(slotId = active.slotId, isoDate = currentIso)
            }.getOrNull()
        }
        val ym = currentState.displayedYearMonth
        val semesterMonths = computeSemesterMonths(profile)
        _uiState.value = currentState.copy(
            dayOverview = updatedOverview,
            activeCampus = academicSummary.activeCampus,
            studentProfile = profile,
            hasCoursesInSemester = repository.listDisciplines(activeSemester, null).isNotEmpty(),
            nextClassPreview = computeNextClassPreview(),
            semesterMonths = semesterMonths,
            monthIndicators = repository.getMonthActivityIndicators(ym.year, ym.monthValue, activeSemester),
            academicEvents = loadAcademicEvents(activeSemester),
            activeCheckIn = updatedCheckIn
        )
    }

    private fun loadAcademicEvents(semester: String): List<AcademicCalendarEvent> =
        runCatching { repository.getAcademicCalendarEvents(semester) }
            .getOrDefault(emptyList())
            .sortedBy { it.eventDate }

    private fun computeNextClassPreview(): NextClassPreview? {
        val today = LocalDate.now()
        val now = LocalTime.now()
        val nowMinutes = now.hour * 60 + now.minute

        for (offset in 0L..14L) {
            val targetDate = today.plusDays(offset)
            val iso = targetDate.toString()
            val overview = runCatching { repository.getAgendaDay(iso) }.getOrNull() ?: continue
            val classes = overview.items
                .filter { it.kind == TimelineItemKind.CLASS_SESSION && it.status != AttendanceStatus.CANCELLED }
                .sortedBy { parseMinutesOrZero(it.startTime) }
            if (classes.isEmpty()) continue

            if (offset == 0L) {
                val ongoing = classes.firstOrNull { item ->
                    val startMin = parseMinutesOrZero(item.startTime)
                    val endMin = parseMinutesOrZero(item.endTime).coerceAtLeast(startMin + 1)
                    nowMinutes in startMin until endMin
                }
                if (ongoing != null) {
                    return NextClassPreview(
                        courseName = ongoing.title,
                        isoDate = iso,
                        dayLabel = "Hoje",
                        startTime = ongoing.startTime,
                        endTime = ongoing.endTime,
                        location = ongoing.location,
                        isHappeningNow = true,
                        isToday = true,
                        slotId = ongoing.slotId
                    )
                }

                val upcomingToday = classes.firstOrNull { item ->
                    parseMinutesOrZero(item.startTime) >= nowMinutes
                }
                if (upcomingToday != null) {
                    return NextClassPreview(
                        courseName = upcomingToday.title,
                        isoDate = iso,
                        dayLabel = "Hoje",
                        startTime = upcomingToday.startTime,
                        endTime = upcomingToday.endTime,
                        location = upcomingToday.location,
                        isHappeningNow = false,
                        isToday = true,
                        slotId = upcomingToday.slotId
                    )
                }
            } else {
                val firstClass = classes.first()
                val dayLabel = if (offset == 1L) {
                    "Amanhã"
                } else {
                    formatShortDayLabel(targetDate)
                }
                return NextClassPreview(
                    courseName = firstClass.title,
                    isoDate = iso,
                    dayLabel = dayLabel,
                    startTime = firstClass.startTime,
                    endTime = firstClass.endTime,
                    location = firstClass.location,
                    isHappeningNow = false,
                    isToday = false,
                    slotId = firstClass.slotId
                )
            }
        }
        return null
    }

    private fun parseMinutesOrZero(hhmm: String): Int {
        val parts = hhmm.trim().split(":")
        val h = parts.getOrNull(0)?.toIntOrNull() ?: 0
        val m = parts.getOrNull(1)?.toIntOrNull() ?: 0
        return h * 60 + m
    }

    private fun formatShortDayLabel(date: LocalDate): String {
        val weekday = when (date.dayOfWeek) {
            DayOfWeek.MONDAY -> "Seg"
            DayOfWeek.TUESDAY -> "Ter"
            DayOfWeek.WEDNESDAY -> "Qua"
            DayOfWeek.THURSDAY -> "Qui"
            DayOfWeek.FRIDAY -> "Sex"
            DayOfWeek.SATURDAY -> "Sáb"
            DayOfWeek.SUNDAY -> "Dom"
        }
        val dd = String.format(Locale.ROOT, "%02d", date.dayOfMonth)
        val mm = String.format(Locale.ROOT, "%02d", date.monthValue)
        return "$weekday, $dd/$mm"
    }

    private fun computeInitialIsoDateWithNotice(
        profile: StudentProfile,
        semester: String
    ): Pair<String, String?> {
        val today = LocalDate.now()
        val start = runCatching { LocalDate.parse(profile.semesterStartDate) }.getOrNull()
        val end = runCatching { LocalDate.parse(profile.semesterEndDate) }.getOrNull()
        val adjusted = if (start != null && end != null && !end.isBefore(start)) {
            when {
                today.isBefore(start) -> start.toString()
                today.isAfter(end) -> end.toString()
                else -> today.toString()
            }
        } else {
            today.toString()
        }
        val notice = if (adjusted != today.toString()) {
            "Fora do período letivo — exibindo ${formatIsoToBrDate(adjusted)} ($semester)."
        } else {
            null
        }
        return adjusted to notice
    }

    private fun computeSemesterMonths(profile: StudentProfile): List<YearMonth> {
        val start = runCatching { LocalDate.parse(profile.semesterStartDate) }.getOrNull()
            ?: LocalDate.of(2026, 8, 24)
        val end = runCatching { LocalDate.parse(profile.semesterEndDate) }.getOrNull()
            ?: LocalDate.of(2026, 12, 23)
        val startYm = YearMonth.from(start)
        val endYm = YearMonth.from(end)
        if (endYm.isBefore(startYm)) return listOf(startYm)
        val months = mutableListOf<YearMonth>()
        var current = startYm
        while (!current.isAfter(endYm) && months.size < 12) {
            months.add(current)
            current = current.plusMonths(1)
        }
        return months
    }

    private fun buildShortLocation(building: String, room: String): String {
        val b = building.trim()
        val r = room.trim().replace("Sala ", "", ignoreCase = true).trim()
        return when {
            b.isNotBlank() && r.isNotBlank() -> "$b-$r".take(14)
            b.isNotBlank() -> b.take(14)
            r.isNotBlank() -> r.take(14)
            else -> "UFOP"
        }
    }

    companion object {
        private const val WEATHER_REFRESH_INTERVAL_MS = 5 * 60 * 1000L

        fun provideFactory(repository: CampusCoreRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return AgendaViewModel(repository) as T
                }
            }
    }
}
