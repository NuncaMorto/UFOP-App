package br.ufop.campus.core.ffi

import br.ufop.campus.core.curriculum.UfopRoomAllocationIndex
import br.ufop.campus.ui.theme.CampusAccessibilitySettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File
import java.time.LocalDate

/**
 * Implementação em memória (fallback seguro) do CAMPUS (UFOP).
 * Usada pelo [CampusEngineBridgeRepository] quando a biblioteca nativa `campus_core` (UniFFI/JNA)
 * não está disponível, mantendo os seeds de `2026.2` (`002_seed_ufop_2026_2.sql`, `003`, `004`).
 */
class InMemoryCampusRepository(
    internal val stateFile: File? = null,
    internal val onMutation: (() -> Unit)? = null,
    internal val onStatePersisted: (() -> Unit)? = null
) : CampusCoreRepository {

    internal val courses = defaultCourseSeeds()
    internal val slots = defaultSlotSeeds()
    internal val commitments = defaultCommitmentSeeds()

    internal val lock = Any()
    internal val _mutationVersion = MutableStateFlow(0L)
    override val mutationVersion: StateFlow<Long> = _mutationVersion.asStateFlow()

    @Volatile
    internal var persistenceBlockedDueToLoadError: Boolean = false
    internal val _persistenceLoadError = MutableStateFlow(PersistenceLoadErrorState())
    override val persistenceLoadError: StateFlow<PersistenceLoadErrorState> =
        _persistenceLoadError.asStateFlow()

    // Estado mutável em memória sincronizado com mutações do usuário
    internal val courseColorOverrides = mutableMapOf<String, String>()
    internal val departmentColorOverrides = mutableMapOf<String, String>()
    internal val checkInStatusBySessionKey = mutableMapOf<String, AttendanceStatus>()
    internal val notesByCourseId = mutableMapOf<String, String>()
    internal val assessmentsByCourseId = linkedMapOf<String, MutableList<AssessmentItem>>()
    internal var activeCampusId: String = "op_cruzeiro"
    internal var generatedIdCounter: Long = 100L

    // Sprint 6 state
    internal data class FriendCourseEntry(
        val courseId: String,
        val name: String,
        val departmentId: String,
        val code: String = ""
    )

    internal data class FriendSlotEntry(
        val slotId: String,
        val courseId: String,
        val dayOfWeek: Int,
        val startTime: String,
        val endTime: String,
        val shortLocation: String
    )

    internal data class FriendScheduleEntry(
        val id: String,
        val friendName: String,
        val courseName: String = "",
        val semester: String,
        val avatarBase64: String? = null,
        val courses: List<FriendCourseEntry>,
        val slots: List<FriendSlotEntry>
    )

    internal data class CampusWeather(val tempC: Int, val condition: String)

    internal data class TimetableEntry(
        val dayOfWeek: Int,
        val startTime: String,
        val endTime: String,
        val cell: TimetableCell
    )

    internal var studentProfile: StudentProfile = StudentProfile(onboardingCompleted = false)
    internal val friendSchedules = mutableListOf<FriendScheduleEntry>()
    internal val campusWeatherById = mutableMapOf<String, CampusWeather>()
    internal var lastWeatherFetchEpochMs: Long = 0L
    internal var lastWeatherAttemptEpochMs: Long = 0L
    @Volatile
    internal var weatherFetchInFlight: Boolean = false

    // Sprint 7 state
    internal val calendarEventsBySemester = linkedMapOf<String, MutableList<AcademicCalendarEvent>>()
    internal var adminUnlocked: Boolean = false
    internal var pendingCalendarUpdate: AcademicCalendarUpdatePackage? = null
    internal var latestPublishedCalendarUpdate: AcademicCalendarUpdatePackage? = null

    // Sprint 8 state
    internal val transcriptEntries = mutableListOf<TranscriptCourseEntry>()
    internal var totalRequiredHours: Int = 3610
    internal var importedCompletedHours: Int = 0

    // Sprint 9 state
    internal var darkThemePreference: String = "SYSTEM"
    internal var accentColorPreference: String = "RED"
    internal var exportFolderTreeUri: String? = null
    internal var exportFolderDisplayLabel: String = DEFAULT_EXPORT_FOLDER_LABEL

    // Sprint 12 state
    internal var accessibilitySettings: CampusAccessibilitySettings = CampusAccessibilitySettings()
    internal var guidedTourCompleted: Boolean = false
    internal var lastSeenReleaseVersion: String = ""

    // Sprint 14 state
    internal var roomAllocationIndex: UfopRoomAllocationIndex? = null

    // v1.9.8: Grade Dinâmica Interativa (matrixId -> códigos concluídos)
    internal val dynamicGridCompletedByMatrix: MutableMap<String, MutableSet<String>> = linkedMapOf()

    private val defaultCoursesSnapshot = LinkedHashMap(courses)
    private val defaultSlotsSnapshot = slots.toList()
    private val defaultCommitmentsSnapshot = commitments.toList()

    init {
        // Instalação nova (sem arquivo de estado): começa VIRGEM, sem disciplinas/horários/compromissos.
        applyVirginStateLocked()
        if (stateFile != null) {
            loadPersistedStateOrFallbackLocked(stateFile)
        }
    }

    /**
     * Estado virgem (primeira execução): sem cursos, horários, sessões, compromissos ou avaliações,
     * perfil com `onboardingCompleted = false`. Mantém catálogo dos 48 departamentos, prédios e campi UFOP.
     */
    internal fun applyVirginStateLocked() {
        courses.clear()
        slots.clear()
        commitments.clear()
        courseColorOverrides.clear()
        departmentColorOverrides.clear()
        checkInStatusBySessionKey.clear()
        notesByCourseId.clear()
        assessmentsByCourseId.clear()
        activeCampusId = "op_cruzeiro"
        generatedIdCounter = 100L
        studentProfile = StudentProfile(onboardingCompleted = false)
        friendSchedules.clear()
        campusWeatherById.clear()
        lastWeatherFetchEpochMs = 0L
        calendarEventsBySemester.clear()
        calendarEventsSeed.groupBy { it.semester }.forEach { (sem, list) ->
            calendarEventsBySemester[sem] = list.toMutableList()
        }
        adminUnlocked = false
        pendingCalendarUpdate = null
        latestPublishedCalendarUpdate = null
        transcriptEntries.clear()
        totalRequiredHours = 3610
        importedCompletedHours = 0
        darkThemePreference = "SYSTEM"
        accentColorPreference = "RED"
        exportFolderTreeUri = null
        exportFolderDisplayLabel = DEFAULT_EXPORT_FOLDER_LABEL
        accessibilitySettings = CampusAccessibilitySettings()
        guidedTourCompleted = false
        lastSeenReleaseVersion = ""
        dynamicGridCompletedByMatrix.clear()
        departmentSwatches.forEach { swatch ->
            departmentColorOverrides[swatch.id] = swatch.colorHex
        }
    }

    private fun applyDefaultSeedStateLocked() {
        courses.clear()
        courses.putAll(defaultCoursesSnapshot)
        slots.clear()
        slots.addAll(defaultSlotsSnapshot)
        commitments.clear()
        commitments.addAll(defaultCommitmentsSnapshot)
        courseColorOverrides.clear()
        departmentColorOverrides.clear()
        checkInStatusBySessionKey.clear()
        notesByCourseId.clear()
        assessmentsByCourseId.clear()
        activeCampusId = "op_cruzeiro"
        generatedIdCounter = 100L
        transcriptEntries.clear()
        totalRequiredHours = 3610
        importedCompletedHours = 0
        calendarEventsBySemester.clear()
        calendarEventsSeed.groupBy { it.semester }.forEach { (sem, list) ->
            calendarEventsBySemester[sem] = list.toMutableList()
        }
        courses.values.forEach { seed ->
            courseColorOverrides[seed.courseId] = seed.defaultColorHex
            notesByCourseId[seed.courseId] = seed.initialNotes
            assessmentsByCourseId[seed.courseId] = seed.assessments.toMutableList()
        }
        departmentSwatches.forEach { swatch ->
            departmentColorOverrides[swatch.id] = swatch.colorHex
        }
    }

    override fun resetSemesterToDefaultSeed(semester: String): Int = synchronized(lock) {
        applyDefaultSeedStateLocked()
        studentProfile = studentProfile.copy(
            activeSemester = "2026.2",
            semesterStartDate = "2026-08-24",
            semesterEndDate = "2026-12-23",
            onboardingCompleted = true
        )
        persistStateLocked()
        _mutationVersion.value += 1L
        courses.values.count { it.semester == "2026.2" }
    }

    override fun searchCampusItems(semester: String, query: String): CampusSearchResults {
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            return CampusSearchResults(query = trimmed, courses = emptyList(), assessments = emptyList(), commitments = emptyList())
        }
        val needle = normalizeSearchText(trimmed)
        fun matches(vararg fields: String): Boolean = fields.any { normalizeSearchText(it).contains(needle) }

        val matchedCourses = synchronized(lock) {
            courses.values
                .filter { it.semester == semester || semester.isBlank() }
                .filter { matches(it.name, it.professor, it.departmentName, it.courseId, resolveCourseCode(it)) }
                .map { buildDisciplineSummaryLocked(it) }
        }
        val matchedAssessments = listAllAssessments(semester, null)
            .filter { matches(it.assessment.title, it.assessment.kind, it.courseName, it.departmentName) }
        val matchedCommitments = synchronized(lock) {
            commitments
                .filter { matches(it.title, it.location, it.subtitle) }
                .map { comm -> toCommitmentTimelineItem(comm) }
        }
        return CampusSearchResults(
            query = trimmed,
            courses = matchedCourses,
            assessments = matchedAssessments,
            commitments = matchedCommitments
        )
    }

    internal fun sessionKey(slotId: String, isoDate: String): String = "${slotId}_$isoDate"

    internal fun resolveCourseColor(course: CourseSeed): String =
        // Sprint 6/7: cor fixa derivada exclusivamente do departamento (ignora sobrescritas do usuário).
        resolveDepartmentOption(course.departmentId).colorHex

    internal fun resolveDepartmentName(course: CourseSeed): String =
        resolveDepartmentOption(course.departmentId).name

    internal fun resolveCourseCode(course: CourseSeed): String {
        val currentNotes = notesByCourseId[course.courseId] ?: course.initialNotes
        return deriveCourseCodeFallback(course.code, currentNotes, course.courseId, course.name)
    }

    internal fun resolveSemesterDefaultYear(semester: String): Int {
        val fromSem = Regex("""(\d{4})""").find(semester)?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (fromSem != null && fromSem in 1990..2100) return fromSem
        val fromActive = Regex("""(\d{4})""").find(studentProfile.activeSemester)?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (fromActive != null && fromActive in 1990..2100) return fromActive
        return LocalDate.now().year
    }

    internal fun clearSemesterCoursesAndSlotsLocked(semester: String) {
        val removedCourseIds = courses.values
            .filter { it.semester == semester }
            .map { it.courseId }
            .toSet()
        if (removedCourseIds.isNotEmpty()) {
            val removedSlotIds = slots
                .filter { it.courseId in removedCourseIds }
                .map { it.slotId }
                .toSet()
            slots.removeAll { it.courseId in removedCourseIds }
            checkInStatusBySessionKey.keys.removeAll { key ->
                removedSlotIds.any { slotId -> key.startsWith("${slotId}_") }
            }
            removedCourseIds.forEach { oldId ->
                courses.remove(oldId)
                courseColorOverrides.remove(oldId)
                notesByCourseId.remove(oldId)
                assessmentsByCourseId.remove(oldId)
            }
        }
    }

    internal fun buildDisciplineSummaryLocked(course: CourseSeed): DisciplineSummaryCard {
        val courseSlotsById = slots.filter { it.courseId == course.courseId }.associateBy { it.slotId }
        var extraAbsences = 0
        var extraCancelled = 0

        for ((key, status) in checkInStatusBySessionKey) {
            val separator = key.lastIndexOf('_')
            if (separator <= 0) continue
            val matchedSlot = courseSlotsById[key.substring(0, separator)] ?: continue
            when (status) {
                AttendanceStatus.ATTENDING -> Unit
                AttendanceStatus.ABSENT -> extraAbsences += matchedSlot.classWeight.coerceAtLeast(1)
                AttendanceStatus.CANCELLED -> extraCancelled += 1
            }
        }

        val dept = resolveDepartmentOption(course.departmentId)
        return DisciplineSummaryCard(
            courseId = course.courseId,
            name = course.name,
            departmentId = dept.id,
            departmentName = dept.name,
            colorHex = dept.colorHex,
            workloadHours = course.workloadHours,
            currentAbsences = (course.baseAbsences + extraAbsences).coerceAtLeast(0),
            maxAbsences = course.maxAbsences,
            cancelledClasses = course.baseCancelledClasses + extraCancelled,
            code = resolveCourseCode(course)
        )
    }

    internal fun computeExtraCheckInAbsencesLocked(courseId: String): Int {
        val courseSlotsById = slots.filter { it.courseId == courseId }.associateBy { it.slotId }
        var extraAbsences = 0
        for ((key, status) in checkInStatusBySessionKey) {
            if (status != AttendanceStatus.ABSENT) continue
            val separator = key.lastIndexOf('_')
            if (separator <= 0) continue
            val matchedSlot = courseSlotsById[key.substring(0, separator)] ?: continue
            extraAbsences += matchedSlot.classWeight.coerceAtLeast(1)
        }
        return extraAbsences
    }

    // ── Delegações para os módulos coesos do pacote ─────────────────────────────────────────────

    override fun listAbsenceHistory(courseId: String): List<AbsenceHistoryEntry> =
        listAbsenceHistoryImpl(courseId)

    override fun getAgendaDay(isoDate: String): AgendaDayOverview =
        getAgendaDayImpl(isoDate)

    override fun getClassCheckIn(slotId: String, isoDate: String): ClassCheckInDetail =
        getClassCheckInImpl(slotId, isoDate)

    override fun saveClassCheckIn(
        slotId: String,
        isoDate: String,
        status: AttendanceStatus,
        colorOverrideHex: String?
    ): DisciplineSummaryCard = saveClassCheckInImpl(slotId, isoDate, status, colorOverrideHex)

    override fun getWeeklyTimetable(semester: String): WeeklyTimetable =
        getWeeklyTimetableImpl(semester)

    override fun createCommitment(
        title: String,
        description: String,
        commitmentDate: String,
        startTime: String,
        endTime: String,
        location: String,
        weatherTempC: Int?
    ): AgendaTimelineItem =
        createCommitmentImpl(title, description, commitmentDate, startTime, endTime, location, weatherTempC)

    override fun updateCommitment(
        commitmentId: String,
        title: String,
        description: String,
        commitmentDate: String,
        startTime: String,
        endTime: String,
        location: String
    ): AgendaTimelineItem =
        updateCommitmentImpl(commitmentId, title, description, commitmentDate, startTime, endTime, location)

    override fun deleteCommitment(commitmentId: String) =
        deleteCommitmentImpl(commitmentId)

    override fun getMonthActivityIndicators(
        year: Int,
        month: Int,
        semester: String
    ): Map<Int, DayActivityIndicator> = getMonthActivityIndicatorsImpl(year, month, semester)

    override fun listDepartments(): List<DepartmentColorOption> = listDepartmentsImpl()

    override fun listDepartmentsForSemester(semester: String): List<DepartmentColorOption> =
        listDepartmentsForSemesterImpl(semester)

    override fun listUfopBuildings(campusId: String?): List<UfopBuildingOption> =
        listUfopBuildingsImpl(campusId)

    override fun listDisciplines(semester: String, departmentId: String?): List<DisciplineSummaryCard> =
        listDisciplinesImpl(semester, departmentId)

    override fun getDisciplineDetail(courseId: String): DisciplineDetail =
        getDisciplineDetailImpl(courseId)

    override fun updateDisciplineNotes(courseId: String, notes: String) =
        updateDisciplineNotesImpl(courseId, notes)

    override fun createCourse(
        semester: String,
        departmentId: String,
        name: String,
        professor: String,
        workloadHours: Int,
        colorOverrideHex: String?
    ): DisciplineSummaryCard =
        createCourseImpl(semester, departmentId, name, professor, workloadHours, colorOverrideHex)

    override fun updateCourse(
        courseId: String,
        name: String,
        professor: String,
        departmentId: String,
        workloadHours: Int
    ): DisciplineSummaryCard =
        updateCourseImpl(courseId, name, professor, departmentId, workloadHours)

    override fun updateCourseAbsences(courseId: String, totalAbsences: Int): DisciplineSummaryCard =
        updateCourseAbsencesImpl(courseId, totalAbsences)

    override fun deleteCourse(courseId: String) = deleteCourseImpl(courseId)

    override fun addScheduleSlot(
        courseId: String,
        dayOfWeek: Int,
        startTime: String,
        endTime: String,
        building: String,
        room: String,
        shortLocation: String
    ): CourseScheduleSlotItem =
        addScheduleSlotImpl(courseId, dayOfWeek, startTime, endTime, building, room, shortLocation)

    override fun updateScheduleSlot(
        slotId: String,
        dayOfWeek: Int,
        startTime: String,
        endTime: String,
        building: String,
        room: String,
        shortLocation: String
    ): CourseScheduleSlotItem =
        updateScheduleSlotImpl(slotId, dayOfWeek, startTime, endTime, building, room, shortLocation)

    override fun deleteScheduleSlot(slotId: String) = deleteScheduleSlotImpl(slotId)

    override fun listCourseSlots(courseId: String): List<CourseScheduleSlotItem> =
        listCourseSlotsImpl(courseId)

    override fun listUfopTemplates(): List<UfopCurriculumTemplate> = listUfopTemplatesImpl()

    override fun applyUfopTemplate(templateId: String, semester: String): Int =
        applyUfopTemplateImpl(templateId, semester)

    override fun applyRoomAllocationIndex(index: UfopRoomAllocationIndex, enrichExisting: Boolean): Int =
        applyRoomAllocationIndexImpl(index, enrichExisting)

    override fun createAssessment(
        courseId: String,
        title: String,
        kind: String,
        dueDate: String,
        dueTime: String
    ): AssessmentItem = createAssessmentImpl(courseId, title, kind, dueDate, dueTime)

    override fun updateAssessment(
        assessmentId: String,
        courseId: String,
        title: String,
        kind: String,
        dueDate: String,
        dueTime: String,
        completed: Boolean
    ): AssessmentItem =
        updateAssessmentImpl(assessmentId, courseId, title, kind, dueDate, dueTime, completed)

    override fun toggleAssessmentCompleted(assessmentId: String, completed: Boolean): AssessmentItem =
        toggleAssessmentCompletedImpl(assessmentId, completed)

    override fun deleteAssessment(assessmentId: String) = deleteAssessmentImpl(assessmentId)

    override fun listAllAssessments(semester: String, kindFilter: String?): List<AssessmentWithCourse> =
        listAllAssessmentsImpl(semester, kindFilter)

    override fun getAcademicProfileSummary(semester: String): AcademicProfileSummary =
        getAcademicProfileSummaryImpl(semester)

    override fun getAcademicCalendarEvents(semester: String): List<AcademicCalendarEvent> =
        getAcademicCalendarEventsImpl(semester)

    override fun isAdminUnlocked(): Boolean = isAdminUnlockedImpl()

    override fun unlockAdminMode(pin: String): Boolean = unlockAdminModeImpl(pin)

    override fun adminPublishCalendarPdf(pdfBytes: ByteArray, semester: String): AcademicCalendarUpdatePackage =
        adminPublishCalendarPdfImpl(pdfBytes, semester)

    override fun exportCalendarUpdateJson(): String = exportCalendarUpdateJsonImpl()

    override fun importCalendarUpdateJson(jsonPayload: String): AcademicCalendarUpdatePackage =
        importCalendarUpdateJsonImpl(jsonPayload)

    override fun getPendingCalendarUpdate(): AcademicCalendarUpdatePackage? =
        getPendingCalendarUpdateImpl()

    override fun applyPendingCalendarUpdate(): Int = applyPendingCalendarUpdateImpl()

    override fun getAcademicCreReport(): AcademicCreReport = getAcademicCreReportImpl()

    override fun updateTranscriptOrCourseGrade(
        semester: String,
        codeOrCourseId: String,
        grade: Double?,
        absences: Int?
    ): AcademicCreReport = updateTranscriptOrCourseGradeImpl(semester, codeOrCourseId, grade, absences)

    override fun importEnrollmentCertificatePdf(pdfBytes: ByteArray, completeOnboarding: Boolean): Int =
        importEnrollmentCertificatePdfImpl(pdfBytes, completeOnboarding)

    override fun importEnrollmentCertificateText(rawText: String, completeOnboarding: Boolean): Int =
        importEnrollmentCertificateTextImpl(rawText, completeOnboarding)

    override fun importCourseSyllabusPdf(courseId: String, pdfBytes: ByteArray): Int =
        importCourseSyllabusPdfImpl(courseId, pdfBytes)

    override fun importCourseSyllabusText(courseId: String, rawText: String): Int =
        importCourseSyllabusTextImpl(courseId, rawText)

    override fun importCourseSyllabusDetailedPdf(courseId: String, pdfBytes: ByteArray): SyllabusImportReport =
        importCourseSyllabusDetailedPdfImpl(courseId, pdfBytes)

    override fun importCourseSyllabusDetailedText(courseId: String, rawText: String): SyllabusImportReport =
        importCourseSyllabusDetailedTextImpl(courseId, rawText)

    override fun importSyllabusPdfAutoDetectCourse(semester: String, pdfBytes: ByteArray): SyllabusImportReport =
        importSyllabusPdfAutoDetectCourseImpl(semester, pdfBytes)

    override fun importSyllabusTextAutoDetectCourse(semester: String, rawText: String): SyllabusImportReport =
        importSyllabusTextAutoDetectCourseImpl(semester, rawText)

    override fun importAcademicTranscriptPdf(pdfBytes: ByteArray): AcademicCreReport =
        importAcademicTranscriptPdfImpl(pdfBytes)

    override fun importAcademicTranscriptText(rawText: String): AcademicCreReport =
        importAcademicTranscriptTextImpl(rawText)

    override fun setActiveCampus(campusId: String): UfopCampusInfo = setActiveCampusImpl(campusId)

    override fun getStudentProfile(): StudentProfile = getStudentProfileImpl()

    override fun saveStudentProfile(profile: StudentProfile): StudentProfile =
        saveStudentProfileImpl(profile)

    override fun listAvailableSemesters(): List<String> = listAvailableSemestersImpl()

    override fun listAvailableCalendarSemesters(): List<String> = listAvailableCalendarSemestersImpl()

    override fun getDarkThemePreference(): String = getDarkThemePreferenceImpl()

    override fun setDarkThemePreference(mode: String) = setDarkThemePreferenceImpl(mode)

    override fun getAccentColorPreference(): String = getAccentColorPreferenceImpl()

    override fun setAccentColorPreference(accentKey: String) = setAccentColorPreferenceImpl(accentKey)

    override fun getAccessibilitySettings(): CampusAccessibilitySettings = getAccessibilitySettingsImpl()

    override fun setAccessibilitySettings(settings: CampusAccessibilitySettings) =
        setAccessibilitySettingsImpl(settings)

    override fun isGuidedTourCompleted(): Boolean = isGuidedTourCompletedImpl()

    override fun setGuidedTourCompleted(completed: Boolean) = setGuidedTourCompletedImpl(completed)

    override fun getLastSeenReleaseVersion(): String = getLastSeenReleaseVersionImpl()

    override fun setLastSeenReleaseVersion(version: String) = setLastSeenReleaseVersionImpl(version)

    override fun getDynamicGridCompletedCodes(matrixId: String): Set<String> =
        getDynamicGridCompletedCodesImpl(matrixId)

    override fun saveDynamicGridCompletedCodes(matrixId: String, completedCodes: Set<String>) =
        saveDynamicGridCompletedCodesImpl(matrixId, completedCodes)

    override fun getDynamicGridAllCompleted(): Map<String, Set<String>> =
        getDynamicGridAllCompletedImpl()

    override fun getExportFolderConfig(): ExportFolderConfig = getExportFolderConfigImpl()

    override fun setExportFolderConfig(treeUri: String?, displayLabel: String?): ExportFolderConfig =
        setExportFolderConfigImpl(treeUri, displayLabel)

    override fun setTotalRequiredHours(hours: Int) = setTotalRequiredHoursImpl(hours)

    override fun listFriendSchedules(): List<FriendScheduleInfo> = listFriendSchedulesImpl()

    override fun importFriendScheduleJson(friendName: String, jsonPayload: String): FriendScheduleInfo =
        importFriendScheduleJsonImpl(friendName, jsonPayload)

    override fun removeFriendSchedule(friendId: String) = removeFriendScheduleImpl(friendId)

    override fun getFriendTimetable(friendId: String): WeeklyTimetable? =
        getFriendTimetableImpl(friendId)

    override fun refreshWeatherIfNeeded(force: Boolean): UfopCampusInfo =
        refreshWeatherIfNeededImpl(force)

    override fun exportSchedulePayload(semester: String): String =
        exportSchedulePayloadImpl(semester)

    override fun exportMyGradeBundleJson(semester: String): String =
        exportMyGradeBundleJsonImpl(semester)

    override fun importSchedulePayload(jsonPayload: String): Int =
        importSchedulePayloadImpl(jsonPayload)

    override fun exportFullAccountSnapshotJson(): String =
        exportFullAccountSnapshotJsonImpl()

    override fun restoreFullAccountSnapshotJson(rawJson: String): Boolean =
        restoreFullAccountSnapshotJsonImpl(rawJson)

    override fun isPersistenceBlockedDueToLoadError(): Boolean = synchronized(lock) {
        persistenceBlockedDueToLoadError
    }

    override fun retryLoadPersistedState(): Boolean = retryLoadPersistedStateImpl()

    override fun resetStateAfterLoadErrorAndUnlockPersistence() =
        resetStateAfterLoadErrorAndUnlockPersistenceImpl()

    override fun wipeAllUserDataForLogout() = wipeAllUserDataForLogoutImpl()
}
