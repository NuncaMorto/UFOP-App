package br.ufop.campus.core.ffi

import br.ufop.campus.core.curriculum.UfopRoomAllocationIndex
import br.ufop.campus.ui.theme.CampusAccessibilitySettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import java.io.File

/**
 * Bridge FFI / Stateful Engine Repository para o CAMPUS (UFOP).
 *
 * Mantém o repositório criptografado [InMemoryCampusRepository] (`campus_ufop_state.json`,
 * AES-256-GCM) como fonte única de verdade para os dados acadêmicos no Android — garantindo
 * paridade com o `CampusStore` do iOS e evitando *split-brain* com as APIs dos Sprints 6–14 ou
 * perda aparente de dados em `campus_ufop.db` quando a biblioteca nativa `libcampus_core.so` está
 * empacotada para o motor do Mapa 2D Offline ([br.ufop.campus.core.uniffi.CampusMapEngine]).
 *
 * O motor nativo SQLite ([br.ufop.campus.core.uniffi.CampusEngine]) permanece desabilitado por
 * padrão (`useNativeAcademicEngine = false`) e pode ser ativado explicitamente apenas para testes
 * isolados da ponte legada.
 */
class CampusEngineBridgeRepository(
    private val dbPath: String? = null,
    private val stateFile: File? = null,
    private val onStatePersisted: (() -> Unit)? = null,
    useNativeAcademicEngine: Boolean = false
) : CampusCoreRepository {

    private val lock = Any()
    private val _mutationVersion = MutableStateFlow(0L)
    override val mutationVersion: StateFlow<Long> = _mutationVersion.asStateFlow()

    private var uniffiEngine: br.ufop.campus.core.uniffi.CampusEngine? = if (useNativeAcademicEngine) {
        try {
            if (dbPath != null) {
                br.ufop.campus.core.uniffi.CampusEngine.newEmpty(dbPath)
            } else {
                br.ufop.campus.core.uniffi.CampusEngine.newInMemoryEmpty()
            }
        } catch (_: Throwable) {
            null
        }
    } else {
        null
    }

    private val fallback = InMemoryCampusRepository(
        stateFile = stateFile,
        onMutation = { _mutationVersion.update { it + 1L } },
        onStatePersisted = onStatePersisted
    )

    override val persistenceLoadError: StateFlow<PersistenceLoadErrorState>
        get() = fallback.persistenceLoadError

    override fun isPersistenceBlockedDueToLoadError(): Boolean = synchronized(lock) {
        fallback.isPersistenceBlockedDueToLoadError()
    }

    override fun retryLoadPersistedState(): Boolean = synchronized(lock) {
        val recovered = fallback.retryLoadPersistedState()
        if (recovered) {
            _mutationVersion.update { it + 1L }
        }
        recovered
    }

    override fun resetStateAfterLoadErrorAndUnlockPersistence(): Unit = synchronized(lock) {
        fallback.resetStateAfterLoadErrorAndUnlockPersistence()
        _mutationVersion.update { it + 1L }
        runCatching { onStatePersisted?.invoke() }
        Unit
    }

    @Volatile
    private var roomAllocationIndex: UfopRoomAllocationIndex? = null

    val isNativeCoreActive: Boolean
        get() = uniffiEngine != null

    private inline fun <T> readOp(
        nativeBlock: (br.ufop.campus.core.uniffi.CampusEngine) -> T,
        fallbackBlock: () -> T
    ): T = synchronized(lock) {
        val engine = uniffiEngine
        if (engine != null) {
            try {
                nativeBlock(engine)
            } catch (err: br.ufop.campus.core.uniffi.CampusException) {
                throw mapCampusException(err)
            }
        } else {
            fallbackBlock()
        }
    }

    private inline fun <T> writeOp(
        nativeBlock: (br.ufop.campus.core.uniffi.CampusEngine) -> T,
        fallbackBlock: () -> T
    ): T = synchronized(lock) {
        val engine = uniffiEngine
        val result = if (engine != null) {
            try {
                nativeBlock(engine)
            } catch (err: br.ufop.campus.core.uniffi.CampusException) {
                throw mapCampusException(err)
            }
        } else {
            fallbackBlock()
        }
        _mutationVersion.value += 1L
        if (engine != null) {
            runCatching { onStatePersisted?.invoke() }
        }
        result
    }

    override fun getAgendaDay(isoDate: String): AgendaDayOverview = readOp(
        nativeBlock = { it.getAgendaDay(isoDate).toFfi() },
        fallbackBlock = { fallback.getAgendaDay(isoDate) }
    )

    override fun getClassCheckIn(slotId: String, isoDate: String): ClassCheckInDetail = readOp(
        nativeBlock = { it.getClassCheckIn(slotId, isoDate).toFfi() },
        fallbackBlock = { fallback.getClassCheckIn(slotId, isoDate) }
    )

    override fun saveClassCheckIn(
        slotId: String,
        isoDate: String,
        status: AttendanceStatus,
        colorOverrideHex: String?
    ): DisciplineSummaryCard = writeOp(
        nativeBlock = { it.saveClassCheckIn(slotId, isoDate, status.toUniffi(), null).toFfi() },
        fallbackBlock = { fallback.saveClassCheckIn(slotId, isoDate, status, null) }
    )

    override fun listDepartments(): List<DepartmentColorOption> = readOp(
        nativeBlock = { engine -> engine.listDepartments().map { it.toFfi() } },
        fallbackBlock = { fallback.listDepartments() }
    )

    override fun listDisciplines(
        semester: String,
        departmentId: String?
    ): List<DisciplineSummaryCard> = readOp(
        nativeBlock = { engine -> engine.listDisciplines(semester, departmentId).map { it.toFfi() } },
        fallbackBlock = { fallback.listDisciplines(semester, departmentId) }
    )

    override fun getDisciplineDetail(courseId: String): DisciplineDetail = readOp(
        nativeBlock = { it.getDisciplineDetail(courseId).toFfi() },
        fallbackBlock = { fallback.getDisciplineDetail(courseId) }
    )

    override fun updateDisciplineNotes(courseId: String, notes: String): Unit = writeOp(
        nativeBlock = { it.updateDisciplineNotes(courseId, notes); Unit },
        fallbackBlock = { fallback.updateDisciplineNotes(courseId, notes) }
    )

    override fun getWeeklyTimetable(semester: String): WeeklyTimetable = readOp(
        nativeBlock = { it.getWeeklyTimetable(semester).toFfi() },
        fallbackBlock = { fallback.getWeeklyTimetable(semester) }
    )

    override fun createCommitment(
        title: String,
        description: String,
        commitmentDate: String,
        startTime: String,
        endTime: String,
        location: String,
        weatherTempC: Int?
    ): AgendaTimelineItem = writeOp(
        nativeBlock = {
            it.createCommitment(
                title = title,
                description = description,
                commitmentDate = commitmentDate,
                startTime = startTime,
                endTime = endTime,
                location = location,
                weatherTempC = weatherTempC
            ).toFfi()
        },
        fallbackBlock = {
            fallback.createCommitment(
                title = title,
                description = description,
                commitmentDate = commitmentDate,
                startTime = startTime,
                endTime = endTime,
                location = location,
                weatherTempC = weatherTempC
            )
        }
    )

    override fun deleteCommitment(commitmentId: String): Unit = writeOp(
        nativeBlock = { it.deleteCommitment(commitmentId) },
        fallbackBlock = { fallback.deleteCommitment(commitmentId) }
    )

    override fun createAssessment(
        courseId: String,
        title: String,
        kind: String,
        dueDate: String,
        dueTime: String
    ): AssessmentItem = writeOp(
        nativeBlock = { it.createAssessment(courseId, title, kind, dueDate, dueTime).toFfi() },
        fallbackBlock = { fallback.createAssessment(courseId, title, kind, dueDate, dueTime) }
    )

    override fun toggleAssessmentCompleted(
        assessmentId: String,
        completed: Boolean
    ): AssessmentItem = writeOp(
        nativeBlock = { it.toggleAssessmentCompleted(assessmentId, completed).toFfi() },
        fallbackBlock = { fallback.toggleAssessmentCompleted(assessmentId, completed) }
    )

    override fun deleteAssessment(assessmentId: String): Unit = writeOp(
        nativeBlock = { it.deleteAssessment(assessmentId) },
        fallbackBlock = { fallback.deleteAssessment(assessmentId) }
    )

    override fun listAllAssessments(
        semester: String,
        kindFilter: String?
    ): List<AssessmentWithCourse> = readOp(
        nativeBlock = { engine -> engine.listAllAssessments(semester, kindFilter).map { it.toFfi() } },
        fallbackBlock = { fallback.listAllAssessments(semester, kindFilter) }
    )

    override fun getAcademicProfileSummary(semester: String): AcademicProfileSummary = readOp(
        nativeBlock = { it.getAcademicProfileSummary(semester).toFfi() },
        fallbackBlock = { fallback.getAcademicProfileSummary(semester) }
    )

    override fun setActiveCampus(campusId: String): UfopCampusInfo = writeOp(
        nativeBlock = { it.setActiveCampus(campusId).toFfi() },
        fallbackBlock = { fallback.setActiveCampus(campusId) }
    )

    override fun exportSchedulePayload(semester: String): String = readOp(
        nativeBlock = { it.exportSchedulePayload(semester) },
        fallbackBlock = { fallback.exportSchedulePayload(semester) }
    )

    override fun importSchedulePayload(jsonPayload: String): Int = writeOp(
        nativeBlock = { it.importSchedulePayload(jsonPayload).toInt() },
        fallbackBlock = { fallback.importSchedulePayload(jsonPayload) }
    )

    override fun createCourse(
        semester: String,
        departmentId: String,
        name: String,
        professor: String,
        workloadHours: Int,
        colorOverrideHex: String?
    ): DisciplineSummaryCard = writeOp(
        nativeBlock = {
            it.createCourse(
                semester = semester,
                departmentId = departmentId,
                name = name,
                professor = professor,
                workloadHours = workloadHours.coerceAtLeast(0).toUInt(),
                colorOverrideHex = null
            ).toFfi()
        },
        fallbackBlock = {
            fallback.createCourse(
                semester = semester,
                departmentId = departmentId,
                name = name,
                professor = professor,
                workloadHours = workloadHours,
                colorOverrideHex = null
            )
        }
    )

    override fun deleteCourse(courseId: String): Unit = writeOp(
        nativeBlock = { it.deleteCourse(courseId) },
        fallbackBlock = { fallback.deleteCourse(courseId) }
    )

    override fun addScheduleSlot(
        courseId: String,
        dayOfWeek: Int,
        startTime: String,
        endTime: String,
        building: String,
        room: String,
        shortLocation: String
    ): CourseScheduleSlotItem = writeOp(
        nativeBlock = {
            it.addScheduleSlot(
                courseId = courseId,
                dayOfWeek = dayOfWeek.coerceAtLeast(0).toUInt(),
                startTime = startTime,
                endTime = endTime,
                building = building,
                room = room,
                shortLocation = shortLocation
            ).toFfi()
        },
        fallbackBlock = {
            fallback.addScheduleSlot(
                courseId = courseId,
                dayOfWeek = dayOfWeek,
                startTime = startTime,
                endTime = endTime,
                building = building,
                room = room,
                shortLocation = shortLocation
            )
        }
    )

    override fun deleteScheduleSlot(slotId: String): Unit = writeOp(
        nativeBlock = { it.deleteScheduleSlot(slotId) },
        fallbackBlock = { fallback.deleteScheduleSlot(slotId) }
    )

    override fun listCourseSlots(courseId: String): List<CourseScheduleSlotItem> = readOp(
        nativeBlock = { engine -> engine.listCourseSlots(courseId).map { it.toFfi() } },
        fallbackBlock = { fallback.listCourseSlots(courseId) }
    )

    override fun listUfopTemplates(): List<UfopCurriculumTemplate> = readOp(
        nativeBlock = { engine -> engine.listUfopTemplates().map { it.toFfi() } },
        fallbackBlock = { fallback.listUfopTemplates() }
    )

    override fun applyUfopTemplate(templateId: String, semester: String): Int = writeOp(
        nativeBlock = { it.applyUfopTemplate(templateId, semester).toInt() },
        fallbackBlock = { fallback.applyUfopTemplate(templateId, semester) }
    )

    override fun listAbsenceHistory(courseId: String): List<AbsenceHistoryEntry> = readOp(
        nativeBlock = { engine -> engine.listAbsenceHistory(courseId).map { it.toFfi() } },
        fallbackBlock = { fallback.listAbsenceHistory(courseId) }
    )

    override fun searchCampusItems(semester: String, query: String): CampusSearchResults = readOp(
        nativeBlock = { it.searchCampusItems(semester, query).toFfi() },
        fallbackBlock = { fallback.searchCampusItems(semester, query) }
    )

    override fun resetSemesterToDefaultSeed(semester: String): Int = writeOp(
        nativeBlock = { it.resetSemesterToDefaultSeed(semester).toInt() },
        fallbackBlock = { fallback.resetSemesterToDefaultSeed(semester) }
    )

    // Sprint 6 APIs
    override fun getStudentProfile(): StudentProfile = synchronized(lock) {
        fallback.getStudentProfile()
    }

    override fun saveStudentProfile(profile: StudentProfile): StudentProfile = synchronized(lock) {
        val saved = fallback.saveStudentProfile(profile)
        _mutationVersion.value += 1L
        saved
    }

    override fun updateCourse(
        courseId: String,
        name: String,
        professor: String,
        departmentId: String,
        workloadHours: Int
    ): DisciplineSummaryCard = writeOp(
        nativeBlock = {
            it.updateCourse(
                courseId = courseId,
                name = name,
                professor = professor,
                departmentId = departmentId,
                workloadHours = workloadHours.coerceAtLeast(0).toUInt()
            ).toFfi()
        },
        fallbackBlock = {
            fallback.updateCourse(
                courseId = courseId,
                name = name,
                professor = professor,
                departmentId = departmentId,
                workloadHours = workloadHours
            )
        }
    )

    override fun updateScheduleSlot(
        slotId: String,
        dayOfWeek: Int,
        startTime: String,
        endTime: String,
        building: String,
        room: String,
        shortLocation: String
    ): CourseScheduleSlotItem = writeOp(
        nativeBlock = {
            it.updateScheduleSlot(
                slotId = slotId,
                dayOfWeek = dayOfWeek.coerceAtLeast(0).toUInt(),
                startTime = startTime,
                endTime = endTime,
                building = building,
                room = room,
                shortLocation = shortLocation
            ).toFfi()
        },
        fallbackBlock = {
            fallback.updateScheduleSlot(
                slotId = slotId,
                dayOfWeek = dayOfWeek,
                startTime = startTime,
                endTime = endTime,
                building = building,
                room = room,
                shortLocation = shortLocation
            )
        }
    )

    override fun updateCommitment(
        commitmentId: String,
        title: String,
        description: String,
        commitmentDate: String,
        startTime: String,
        endTime: String,
        location: String
    ): AgendaTimelineItem = writeOp(
        nativeBlock = {
            it.updateCommitment(
                commitmentId = commitmentId,
                title = title,
                description = description,
                commitmentDate = commitmentDate,
                startTime = startTime,
                endTime = endTime,
                location = location
            ).toFfi()
        },
        fallbackBlock = {
            fallback.updateCommitment(
                commitmentId = commitmentId,
                title = title,
                description = description,
                commitmentDate = commitmentDate,
                startTime = startTime,
                endTime = endTime,
                location = location
            )
        }
    )

    override fun getMonthActivityIndicators(
        year: Int,
        month: Int,
        semester: String
    ): Map<Int, DayActivityIndicator> = synchronized(lock) {
        fallback.getMonthActivityIndicators(year, month, semester)
    }

    override fun listAvailableSemesters(): List<String> = synchronized(lock) {
        fallback.listAvailableSemesters()
    }

    override fun listAvailableCalendarSemesters(): List<String> = synchronized(lock) {
        fallback.listAvailableCalendarSemesters()
    }

    override fun listFriendSchedules(): List<FriendScheduleInfo> = synchronized(lock) {
        fallback.listFriendSchedules()
    }

    override fun importFriendScheduleJson(
        friendName: String,
        jsonPayload: String
    ): FriendScheduleInfo = synchronized(lock) {
        val result = fallback.importFriendScheduleJson(friendName, jsonPayload)
        _mutationVersion.value += 1L
        result
    }

    override fun removeFriendSchedule(friendId: String): Unit = synchronized(lock) {
        fallback.removeFriendSchedule(friendId)
        _mutationVersion.value += 1L
    }

    override fun getFriendTimetable(friendId: String): WeeklyTimetable? = synchronized(lock) {
        fallback.getFriendTimetable(friendId)
    }

    override fun refreshWeatherIfNeeded(force: Boolean): UfopCampusInfo =
        fallback.refreshWeatherIfNeeded(force)

    override fun importEnrollmentCertificatePdf(pdfBytes: ByteArray, completeOnboarding: Boolean): Int = synchronized(lock) {
        val count = fallback.importEnrollmentCertificatePdf(pdfBytes, completeOnboarding)
        _mutationVersion.value += 1L
        count
    }

    override fun importEnrollmentCertificateText(rawText: String, completeOnboarding: Boolean): Int = synchronized(lock) {
        val count = fallback.importEnrollmentCertificateText(rawText, completeOnboarding)
        _mutationVersion.value += 1L
        count
    }

    // Sprint 7 APIs
    override fun listUfopBuildings(campusId: String?): List<UfopBuildingOption> = synchronized(lock) {
        fallback.listUfopBuildings(campusId)
    }

    override fun importCourseSyllabusPdf(courseId: String, pdfBytes: ByteArray): Int = synchronized(lock) {
        val count = fallback.importCourseSyllabusPdf(courseId, pdfBytes)
        _mutationVersion.value += 1L
        count
    }

    override fun importCourseSyllabusText(courseId: String, rawText: String): Int = synchronized(lock) {
        val count = fallback.importCourseSyllabusText(courseId, rawText)
        _mutationVersion.value += 1L
        count
    }

    override fun importCourseSyllabusDetailedPdf(
        courseId: String,
        pdfBytes: ByteArray
    ): SyllabusImportReport = synchronized(lock) {
        val report = fallback.importCourseSyllabusDetailedPdf(courseId, pdfBytes)
        if (report.matchedCourse && (report.importedCount > 0 || report.extractedProfessor.isNotBlank())) {
            _mutationVersion.value += 1L
        }
        report
    }

    override fun importCourseSyllabusDetailedText(
        courseId: String,
        rawText: String
    ): SyllabusImportReport = synchronized(lock) {
        val report = fallback.importCourseSyllabusDetailedText(courseId, rawText)
        if (report.matchedCourse && (report.importedCount > 0 || report.extractedProfessor.isNotBlank())) {
            _mutationVersion.value += 1L
        }
        report
    }

    override fun getAcademicCalendarEvents(semester: String): List<AcademicCalendarEvent> = synchronized(lock) {
        fallback.getAcademicCalendarEvents(semester)
    }

    override fun isAdminUnlocked(): Boolean = synchronized(lock) {
        fallback.isAdminUnlocked()
    }

    override fun unlockAdminMode(pin: String): Boolean = synchronized(lock) {
        val unlocked = fallback.unlockAdminMode(pin)
        if (unlocked) {
            _mutationVersion.value += 1L
        }
        unlocked
    }

    override fun adminPublishCalendarPdf(
        pdfBytes: ByteArray,
        semester: String
    ): AcademicCalendarUpdatePackage = synchronized(lock) {
        val pkg = fallback.adminPublishCalendarPdf(pdfBytes, semester)
        _mutationVersion.value += 1L
        pkg
    }

    override fun exportCalendarUpdateJson(): String = synchronized(lock) {
        fallback.exportCalendarUpdateJson()
    }

    override fun importCalendarUpdateJson(jsonPayload: String): AcademicCalendarUpdatePackage = synchronized(lock) {
        val pkg = fallback.importCalendarUpdateJson(jsonPayload)
        _mutationVersion.value += 1L
        pkg
    }

    override fun getPendingCalendarUpdate(): AcademicCalendarUpdatePackage? = synchronized(lock) {
        fallback.getPendingCalendarUpdate()
    }

    override fun applyPendingCalendarUpdate(): Int = synchronized(lock) {
        val count = fallback.applyPendingCalendarUpdate()
        if (count > 0) {
            _mutationVersion.value += 1L
        }
        count
    }

    override fun exportMyGradeBundleJson(semester: String): String = synchronized(lock) {
        fallback.exportMyGradeBundleJson(semester)
    }

    // Sprint 8 APIs
    override fun listDepartmentsForSemester(semester: String): List<DepartmentColorOption> = synchronized(lock) {
        val engine = uniffiEngine
        if (engine != null) {
            val deptIds = try {
                engine.listDisciplines(semester, null).map { it.departmentId.lowercase() }.toSet()
            } catch (_: Throwable) {
                emptySet()
            }
            if (deptIds.isNotEmpty()) {
                fallback.listDepartments().filter { it.id in deptIds }
            } else {
                fallback.listDepartmentsForSemester(semester)
            }
        } else {
            fallback.listDepartmentsForSemester(semester)
        }
    }

    override fun getAcademicCreReport(): AcademicCreReport = synchronized(lock) {
        fallback.getAcademicCreReport()
    }

    override fun updateTranscriptOrCourseGrade(
        semester: String,
        codeOrCourseId: String,
        grade: Double?,
        absences: Int?
    ): AcademicCreReport = synchronized(lock) {
        val report = fallback.updateTranscriptOrCourseGrade(semester, codeOrCourseId, grade, absences)
        _mutationVersion.value += 1L
        report
    }

    override fun importAcademicTranscriptPdf(pdfBytes: ByteArray): AcademicCreReport = synchronized(lock) {
        val report = fallback.importAcademicTranscriptPdf(pdfBytes)
        _mutationVersion.value += 1L
        report
    }

    override fun importAcademicTranscriptText(rawText: String): AcademicCreReport = synchronized(lock) {
        val report = fallback.importAcademicTranscriptText(rawText)
        _mutationVersion.value += 1L
        report
    }

    // Sprint 9 APIs
    override fun updateCourseAbsences(
        courseId: String,
        totalAbsences: Int
    ): DisciplineSummaryCard = synchronized(lock) {
        val card = fallback.updateCourseAbsences(courseId, totalAbsences)
        _mutationVersion.value += 1L
        card
    }

    override fun getDarkThemePreference(): String = synchronized(lock) {
        fallback.getDarkThemePreference()
    }

    override fun setDarkThemePreference(mode: String): Unit = synchronized(lock) {
        fallback.setDarkThemePreference(mode)
        _mutationVersion.value += 1L
    }

    override fun getExportFolderConfig(): ExportFolderConfig = synchronized(lock) {
        fallback.getExportFolderConfig()
    }

    override fun setExportFolderConfig(
        treeUri: String?,
        displayLabel: String?
    ): ExportFolderConfig = synchronized(lock) {
        val config = fallback.setExportFolderConfig(treeUri, displayLabel)
        _mutationVersion.value += 1L
        config
    }

    // Sprint 10 APIs
    override fun updateAssessment(
        assessmentId: String,
        courseId: String,
        title: String,
        kind: String,
        dueDate: String,
        dueTime: String,
        completed: Boolean
    ): AssessmentItem = synchronized(lock) {
        val updated = fallback.updateAssessment(
            assessmentId = assessmentId,
            courseId = courseId,
            title = title,
            kind = kind,
            dueDate = dueDate,
            dueTime = dueTime,
            completed = completed
        )
        _mutationVersion.value += 1L
        updated
    }

    override fun setTotalRequiredHours(hours: Int): Unit = synchronized(lock) {
        fallback.setTotalRequiredHours(hours)
        _mutationVersion.value += 1L
    }

    // Sprint 11 APIs
    override fun getAccentColorPreference(): String = synchronized(lock) {
        fallback.getAccentColorPreference()
    }

    override fun setAccentColorPreference(accentKey: String): Unit = synchronized(lock) {
        fallback.setAccentColorPreference(accentKey)
        _mutationVersion.value += 1L
    }

    // Sprint 12 APIs
    override fun getAccessibilitySettings(): CampusAccessibilitySettings = synchronized(lock) {
        fallback.getAccessibilitySettings()
    }

    override fun setAccessibilitySettings(settings: CampusAccessibilitySettings): Unit = synchronized(lock) {
        fallback.setAccessibilitySettings(settings)
        _mutationVersion.value += 1L
    }

    override fun isGuidedTourCompleted(): Boolean = synchronized(lock) {
        fallback.isGuidedTourCompleted()
    }

    override fun setGuidedTourCompleted(completed: Boolean): Unit = synchronized(lock) {
        fallback.setGuidedTourCompleted(completed)
        _mutationVersion.value += 1L
    }

    override fun getLastSeenReleaseVersion(): String = synchronized(lock) {
        fallback.getLastSeenReleaseVersion()
    }

    override fun setLastSeenReleaseVersion(version: String): Unit = synchronized(lock) {
        fallback.setLastSeenReleaseVersion(version)
        _mutationVersion.value += 1L
    }

    override fun exportFullAccountSnapshotJson(): String = synchronized(lock) {
        fallback.exportFullAccountSnapshotJson()
    }

    override fun restoreFullAccountSnapshotJson(rawJson: String): Boolean = synchronized(lock) {
        val restored = fallback.restoreFullAccountSnapshotJson(rawJson)
        if (restored) {
            _mutationVersion.value += 1L
        }
        restored
    }

    override fun wipeAllUserDataForLogout(): Unit = synchronized(lock) {
        fallback.wipeAllUserDataForLogout()
        val engine = uniffiEngine
        if (engine != null) {
            runCatching { engine.close() }
            uniffiEngine = null
        }
        dbPath?.let { path ->
            runCatching { File(path).delete() }
            runCatching { File("$path-wal").delete() }
            runCatching { File("$path-shm").delete() }
            runCatching { File("$path-journal").delete() }
        }
        stateFile?.parentFile?.let { dir ->
            runCatching { File(dir, "campus_ufop.db").delete() }
            runCatching { File(dir, "campus_ufop.db-wal").delete() }
            runCatching { File(dir, "campus_ufop.db-shm").delete() }
            runCatching { File(dir, "campus_ufop.db-journal").delete() }
        }
        _mutationVersion.value += 1L
        runCatching { onStatePersisted?.invoke() }
        Unit
    }

    // Sprint 14 APIs
    override fun applyRoomAllocationIndex(
        index: UfopRoomAllocationIndex,
        enrichExisting: Boolean
    ): Int = synchronized(lock) {
        roomAllocationIndex = index
        val count = fallback.applyRoomAllocationIndex(index, enrichExisting)
        if (count > 0) {
            _mutationVersion.update { it + 1L }
        }
        count
    }

    // v1.9.8: Grade Dinâmica Interativa
    override fun getDynamicGridCompletedCodes(matrixId: String): Set<String> = synchronized(lock) {
        fallback.getDynamicGridCompletedCodes(matrixId)
    }

    override fun saveDynamicGridCompletedCodes(matrixId: String, completedCodes: Set<String>): Unit = synchronized(lock) {
        fallback.saveDynamicGridCompletedCodes(matrixId, completedCodes)
        _mutationVersion.value += 1L
    }

    override fun getDynamicGridAllCompleted(): Map<String, Set<String>> = synchronized(lock) {
        fallback.getDynamicGridAllCompleted()
    }

    private fun mapCampusException(
        err: br.ufop.campus.core.uniffi.CampusException
    ): RuntimeException = when (err) {
        is br.ufop.campus.core.uniffi.CampusException.InvalidInput ->
            IllegalArgumentException(err.message ?: "Entrada inválida.", err)
        is br.ufop.campus.core.uniffi.CampusException.NotFound ->
            IllegalArgumentException(err.message ?: "Registro não encontrado.", err)
        is br.ufop.campus.core.uniffi.CampusException.Database ->
            IllegalStateException(err.message ?: "Erro interno no banco de dados.", err)
    }
}

private fun AttendanceStatus.toUniffi(): br.ufop.campus.core.uniffi.AttendanceStatus = when (this) {
    AttendanceStatus.ATTENDING -> br.ufop.campus.core.uniffi.AttendanceStatus.ATTENDING
    AttendanceStatus.ABSENT -> br.ufop.campus.core.uniffi.AttendanceStatus.ABSENT
    AttendanceStatus.CANCELLED -> br.ufop.campus.core.uniffi.AttendanceStatus.CANCELLED
}

private fun br.ufop.campus.core.uniffi.AttendanceStatus.toFfi(): AttendanceStatus = when (this) {
    br.ufop.campus.core.uniffi.AttendanceStatus.ATTENDING -> AttendanceStatus.ATTENDING
    br.ufop.campus.core.uniffi.AttendanceStatus.ABSENT -> AttendanceStatus.ABSENT
    br.ufop.campus.core.uniffi.AttendanceStatus.CANCELLED -> AttendanceStatus.CANCELLED
}

private fun br.ufop.campus.core.uniffi.TimelineItemKind.toFfi(): TimelineItemKind = when (this) {
    br.ufop.campus.core.uniffi.TimelineItemKind.CLASS_SESSION -> TimelineItemKind.CLASS_SESSION
    br.ufop.campus.core.uniffi.TimelineItemKind.COMMITMENT -> TimelineItemKind.COMMITMENT
}

private fun br.ufop.campus.core.uniffi.DayPillItem.toFfi(): DayPillItem = DayPillItem(
    isoDate = isoDate,
    dayShort = dayShort,
    dayNumber = dayNumber.toInt()
)

private fun br.ufop.campus.core.uniffi.AgendaTimelineItem.toFfi(): AgendaTimelineItem = AgendaTimelineItem(
    id = id,
    slotId = slotId,
    courseId = courseId,
    kind = kind.toFfi(),
    title = title,
    startTime = startTime,
    endTime = endTime,
    location = location,
    subtitle = subtitle,
    colorHex = colorHex,
    weatherTempC = weatherTempC,
    status = status?.toFfi()
)

private fun br.ufop.campus.core.uniffi.AgendaDayOverview.toFfi(): AgendaDayOverview = AgendaDayOverview(
    isoDate = isoDate,
    monthYearLabel = monthYearLabel,
    formattedDate = formattedDate,
    weekNumber = weekNumber.toInt(),
    weekDays = weekDays.map { it.toFfi() },
    classesCount = classesCount.toInt(),
    commitmentsCount = commitmentsCount.toInt(),
    items = items.map { it.toFfi() }
)

private fun br.ufop.campus.core.uniffi.DepartmentColorOption.toFfi(): DepartmentColorOption = DepartmentColorOption(
    id = id,
    name = name,
    colorHex = colorHex
)

private fun br.ufop.campus.core.uniffi.ClassCheckInDetail.toFfi(): ClassCheckInDetail = ClassCheckInDetail(
    slotId = slotId,
    courseId = courseId,
    departmentId = departmentId,
    departmentName = departmentName,
    courseName = courseName,
    formattedDate = formattedDate,
    startTime = startTime,
    endTime = endTime,
    weatherTempC = weatherTempC,
    weatherCondition = weatherCondition,
    buildingAndRoom = buildingAndRoom,
    professor = professor,
    selectedColorHex = selectedColorHex,
    availableColors = availableColors.map { it.toFfi() },
    status = status.toFfi()
)

private fun br.ufop.campus.core.uniffi.DisciplineSummaryCard.toFfi(): DisciplineSummaryCard = DisciplineSummaryCard(
    courseId = courseId,
    name = name,
    departmentId = departmentId,
    departmentName = departmentName,
    colorHex = colorHex,
    workloadHours = workloadHours.toInt(),
    currentAbsences = currentAbsences.toInt(),
    maxAbsences = maxAbsences.toInt(),
    cancelledClasses = cancelledClasses.toInt(),
    code = deriveCourseCodeFallback(null, "", courseId, name)
)

private fun br.ufop.campus.core.uniffi.AssessmentItem.toFfi(): AssessmentItem = AssessmentItem(
    id = id,
    courseId = courseId,
    dayNumber = dayNumber,
    monthShort = monthShort,
    title = title,
    kind = kind,
    dueTime = dueTime,
    dueDate = dueDate,
    completed = completed
)

private fun br.ufop.campus.core.uniffi.DisciplineDetail.toFfi(): DisciplineDetail {
    val mappedSummary = summary.toFfi()
    return DisciplineDetail(
        summary = mappedSummary,
        semester = semester,
        assessments = assessments.map { it.toFfi() },
        notes = notes,
        code = mappedSummary.code
    )
}

private fun br.ufop.campus.core.uniffi.TimetableCell.toFfi(): TimetableCell = TimetableCell(
    slotId = slotId,
    courseId = courseId,
    courseName = courseName,
    shortLocation = shortLocation,
    departmentId = departmentId,
    departmentName = departmentName,
    colorHex = colorHex,
    code = deriveCourseCodeFallback(null, "", courseId, courseName)
)

private fun br.ufop.campus.core.uniffi.WeeklyTimetable.toFfi(): WeeklyTimetable {
    val mappedRows = rows.map { row ->
        TimetableRow(
            startTime = row.startTime,
            endTime = row.endTime,
            cellsByDay = row.cells.associate { it.dayOfWeek.toInt() to it.cell?.toFfi() }
        )
    }
    val mappedDays = rows.firstOrNull()?.cells?.map { it.dayOfWeek.toInt() to it.dayLabel }
        ?: days.mapIndexed { idx, label -> dayIndexForLabel(label, idx + 1) to label }
    return WeeklyTimetable(
        semester = semester,
        gridTitle = gridTitle,
        days = mappedDays,
        rows = mappedRows,
        legend = legend.map { it.toFfi() }
    )
}

private fun dayIndexForLabel(label: String, defaultIndex: Int): Int = when (label.uppercase()) {
    "SEG" -> 1
    "TER" -> 2
    "QUA" -> 3
    "QUI" -> 4
    "SEX" -> 5
    "SÁB", "SAB" -> 6
    "DOM" -> 7
    else -> defaultIndex
}

private fun br.ufop.campus.core.uniffi.AssessmentWithCourse.toFfi(): AssessmentWithCourse = AssessmentWithCourse(
    assessment = assessment.toFfi(),
    courseName = courseName,
    departmentName = departmentName,
    colorHex = colorHex
)

private fun br.ufop.campus.core.uniffi.UfopCampusInfo.toFfi(): UfopCampusInfo = UfopCampusInfo(
    id = id,
    name = name,
    city = city,
    weatherTempC = weatherTempC,
    weatherCondition = weatherCondition,
    isActive = isActive
)

private fun br.ufop.campus.core.uniffi.AcademicCalendarEvent.toFfi(): AcademicCalendarEvent = AcademicCalendarEvent(
    id = id,
    semester = semester,
    eventDate = eventDate,
    dayNumber = dayNumber,
    monthShort = monthShort,
    title = title,
    category = category
)

private fun br.ufop.campus.core.uniffi.AcademicProfileSummary.toFfi(): AcademicProfileSummary = AcademicProfileSummary(
    semester = semester,
    activeCampus = activeCampus.toFfi(),
    campuses = campuses.map { it.toFfi() },
    totalCourses = totalCourses.toInt(),
    totalAbsences = totalAbsences.toInt(),
    totalMaxAbsences = totalMaxAbsences.toInt(),
    totalCancelledClasses = totalCancelledClasses.toInt(),
    coursesAtRisk = coursesAtRisk.toInt(),
    pendingAssessments = pendingAssessments.toInt(),
    completedAssessments = completedAssessments.toInt(),
    calendarEvents = calendarEvents.map { it.toFfi() }
)

private fun br.ufop.campus.core.uniffi.UfopCurriculumTemplate.toFfi(): UfopCurriculumTemplate = UfopCurriculumTemplate(
    id = id,
    code = code,
    name = name,
    departmentName = departmentName,
    campusName = campusName,
    description = description,
    coursesCount = coursesCount.toInt()
)

private fun br.ufop.campus.core.uniffi.CourseScheduleSlotItem.toFfi(): CourseScheduleSlotItem = CourseScheduleSlotItem(
    slotId = slotId,
    courseId = courseId,
    dayOfWeek = dayOfWeek.toInt(),
    dayLabel = dayLabel,
    startTime = startTime,
    endTime = endTime,
    building = building,
    room = room,
    shortLocation = shortLocation
)

private fun br.ufop.campus.core.uniffi.AbsenceHistoryEntry.toFfi(): AbsenceHistoryEntry = AbsenceHistoryEntry(
    sessionId = sessionId,
    slotId = slotId,
    courseId = courseId,
    sessionDate = sessionDate,
    formattedDate = formattedDate,
    startTime = startTime,
    endTime = endTime,
    status = status.toFfi(),
    classWeight = classWeight.toInt()
)

private fun br.ufop.campus.core.uniffi.CampusSearchResults.toFfi(): CampusSearchResults = CampusSearchResults(
    query = query,
    courses = courses.map { it.toFfi() },
    assessments = assessments.map { it.toFfi() },
    commitments = commitments.map { it.toFfi() }
)
