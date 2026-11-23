package br.ufop.campus.core.ffi

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.util.Base64
import br.ufop.campus.core.curriculum.UfopRoomAllocationIndex
import br.ufop.campus.core.security.CampusCryptoEngine
import br.ufop.campus.core.security.SecurePdfProcessor
import br.ufop.campus.ui.theme.CampusAccessibilitySettings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.nio.file.Files
import java.nio.file.StandardCopyOption
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters
import kotlin.math.roundToInt

/**
 * Implementação em memória (fallback seguro) do CAMPUS (UFOP).
 * Usada pelo [CampusEngineBridgeRepository] quando a biblioteca nativa `campus_core` (UniFFI/JNA)
 * não está disponível, mantendo os seeds de `2026.2` (`002_seed_ufop_2026_2.sql`, `003`, `004`).
 */
class InMemoryCampusRepository(
    private val stateFile: File? = null,
    private val onMutation: (() -> Unit)? = null,
    private val onStatePersisted: (() -> Unit)? = null
) : CampusCoreRepository {


    private val courses = defaultCourseSeeds()
    private val slots = defaultSlotSeeds()
    private val commitments = defaultCommitmentSeeds()

    private val lock = Any()
    private val _mutationVersion = MutableStateFlow(0L)
    override val mutationVersion: StateFlow<Long> = _mutationVersion.asStateFlow()


    /** Catálogo oficial dos 48 departamentos UFOP com paleta fixa de alto contraste. */

    // Estado mutável em memória sincronizado com mutações do usuário
    private val courseColorOverrides = mutableMapOf<String, String>()
    private val departmentColorOverrides = mutableMapOf<String, String>()
    private val checkInStatusBySessionKey = mutableMapOf<String, AttendanceStatus>()
    private val notesByCourseId = mutableMapOf<String, String>()
    private val assessmentsByCourseId = linkedMapOf<String, MutableList<AssessmentItem>>()
    private var activeCampusId: String = "op_cruzeiro"
    private var generatedIdCounter: Long = 100L

    // Sprint 6 state
    private data class FriendCourseEntry(
        val courseId: String,
        val name: String,
        val departmentId: String,
        val code: String = ""
    )

    private data class FriendSlotEntry(
        val slotId: String,
        val courseId: String,
        val dayOfWeek: Int,
        val startTime: String,
        val endTime: String,
        val shortLocation: String
    )

    private data class FriendScheduleEntry(
        val id: String,
        val friendName: String,
        val courseName: String = "",
        val semester: String,
        val avatarBase64: String? = null,
        val courses: List<FriendCourseEntry>,
        val slots: List<FriendSlotEntry>
    )

    private data class CampusWeather(val tempC: Int, val condition: String)

    private var studentProfile: StudentProfile = StudentProfile(onboardingCompleted = false)
    private val friendSchedules = mutableListOf<FriendScheduleEntry>()
    private val campusWeatherById = mutableMapOf<String, CampusWeather>()
    private var lastWeatherFetchEpochMs: Long = 0L
    private var lastWeatherAttemptEpochMs: Long = 0L
    @Volatile
    private var weatherFetchInFlight: Boolean = false

    // Sprint 7 state
    private val calendarEventsBySemester = linkedMapOf<String, MutableList<AcademicCalendarEvent>>()
    private var adminUnlocked: Boolean = false
    private var pendingCalendarUpdate: AcademicCalendarUpdatePackage? = null
    private var latestPublishedCalendarUpdate: AcademicCalendarUpdatePackage? = null

    // Sprint 8 state
    private val transcriptEntries = mutableListOf<TranscriptCourseEntry>()
    private var totalRequiredHours: Int = 3610
    private var importedCompletedHours: Int = 0

    // Sprint 9 state
    private var darkThemePreference: String = "SYSTEM"
    private var accentColorPreference: String = "GREEN"
    private var exportFolderTreeUri: String? = null
    private var exportFolderDisplayLabel: String = DEFAULT_EXPORT_FOLDER_LABEL

    // Sprint 12 state
    private var accessibilitySettings: CampusAccessibilitySettings = CampusAccessibilitySettings()
    private var guidedTourCompleted: Boolean = false
    private var lastSeenReleaseVersion: String = ""

    // Sprint 14 state
    private var roomAllocationIndex: UfopRoomAllocationIndex? = null

    // v1.9.8: Grade Dinâmica Interativa (matrixId -> códigos concluídos)
    private val dynamicGridCompletedByMatrix: MutableMap<String, MutableSet<String>> = linkedMapOf()

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
    private fun applyVirginStateLocked() {
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
        accentColorPreference = "GREEN"
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

    override fun listAbsenceHistory(courseId: String): List<AbsenceHistoryEntry> = synchronized(lock) {
        val courseSlots = slots.filter { it.courseId == courseId }.associateBy { it.slotId }
        checkInStatusBySessionKey.entries
            .mapNotNull { (key, status) ->
                if (status == AttendanceStatus.ATTENDING) return@mapNotNull null
                val separator = key.lastIndexOf('_')
                if (separator <= 0) return@mapNotNull null
                val slotId = key.substring(0, separator)
                val isoDate = key.substring(separator + 1)
                val slot = courseSlots[slotId] ?: return@mapNotNull null
                AbsenceHistoryEntry(
                    sessionId = key,
                    slotId = slotId,
                    courseId = courseId,
                    sessionDate = isoDate,
                    formattedDate = formatPortugueseDate(isoDate),
                    startTime = slot.startTime,
                    endTime = slot.endTime,
                    status = status,
                    classWeight = if (status == AttendanceStatus.ABSENT) slot.classWeight.coerceAtLeast(1) else 0
                )
            }
            .sortedWith(
                compareByDescending<AbsenceHistoryEntry> { it.sessionDate }
                    .thenByDescending { it.startTime }
            )
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
                .map { comm ->
                    AgendaTimelineItem(
                        id = comm.id,
                        slotId = null,
                        courseId = null,
                        kind = TimelineItemKind.COMMITMENT,
                        title = comm.title,
                        startTime = comm.startTime,
                        endTime = comm.endTime,
                        location = comm.location,
                        subtitle = comm.subtitle,
                        colorHex = comm.colorHex,
                        weatherTempC = comm.weatherTempC,
                        status = null
                    )
                }
        }
        return CampusSearchResults(
            query = trimmed,
            courses = matchedCourses,
            assessments = matchedAssessments,
            commitments = matchedCommitments
        )
    }

    private fun sessionKey(slotId: String, isoDate: String): String = "${slotId}_$isoDate"

    private fun resolveCourseColor(course: CourseSeed): String {
        // Sprint 6/7: cor fixa derivada exclusivamente do departamento (ignora sobrescritas do usuário).
        return resolveDepartmentOption(course.departmentId).colorHex
    }

    private fun resolveDepartmentName(course: CourseSeed): String =
        resolveDepartmentOption(course.departmentId).name

    private fun resolveCourseCode(course: CourseSeed): String {
        val currentNotes = notesByCourseId[course.courseId] ?: course.initialNotes
        return deriveCourseCodeFallback(course.code, currentNotes, course.courseId, course.name)
    }

    private fun buildDisciplineSummaryLocked(course: CourseSeed): DisciplineSummaryCard {
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

    override fun getAgendaDay(isoDate: String): AgendaDayOverview = synchronized(lock) {
        val date = parseIsoDateOrToday(isoDate)
        val normalizedDate = date.toString()
        val dayOfWeek = date.dayOfWeek.value // 1=SEG .. 7=DOM
        val formattedDate = formatPortugueseDate(date)
        val activeSemester = studentProfile.activeSemester.ifBlank { "2026.2" }
        val campusWeather = activeCampusLocked()

        val semesterCourseIds = courses.values
            .filter { it.semester == activeSemester }
            .map { it.courseId }
            .toSet()
        val daySlots = slots.filter { it.dayOfWeek == dayOfWeek && it.courseId in semesterCourseIds }
        val classItems = daySlots.mapNotNull { slot ->
            val course = courses[slot.courseId] ?: return@mapNotNull null
            val status = checkInStatusBySessionKey[sessionKey(slot.slotId, normalizedDate)]
                ?: AttendanceStatus.ATTENDING
            val slotLocation = when {
                slot.building.isNotBlank() && slot.room.isNotBlank() -> "${slot.building} · ${slot.room}"
                slot.building.isNotBlank() -> slot.building
                slot.room.isNotBlank() -> slot.room
                else -> course.buildingAndRoom
            }
            AgendaTimelineItem(
                id = "${slot.slotId}_$normalizedDate",
                slotId = slot.slotId,
                courseId = course.courseId,
                kind = TimelineItemKind.CLASS_SESSION,
                title = course.name,
                startTime = slot.startTime,
                endTime = slot.endTime,
                location = slotLocation,
                subtitle = course.professor,
                colorHex = resolveCourseColor(course),
                weatherTempC = campusWeather.weatherTempC,
                status = status
            )
        }

        val commitmentItems = commitments
            .filter { it.isoDate == normalizedDate }
            .map { comm ->
                AgendaTimelineItem(
                    id = comm.id,
                    slotId = null,
                    courseId = null,
                    kind = TimelineItemKind.COMMITMENT,
                    title = comm.title,
                    startTime = comm.startTime,
                    endTime = comm.endTime,
                    location = comm.location,
                    subtitle = comm.subtitle,
                    colorHex = comm.colorHex,
                    weatherTempC = comm.weatherTempC,
                    status = null
                )
            }

        val combinedItems = (classItems + commitmentItems).sortedBy { it.startTime }
        val sundayStart = date.with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
        val weekDays = (0 until 7).map { offset ->
            val d = sundayStart.plusDays(offset.toLong())
            DayPillItem(
                isoDate = d.toString(),
                dayShort = dayLabelFor(d.dayOfWeek.value),
                dayNumber = d.dayOfMonth
            )
        }

        AgendaDayOverview(
            isoDate = normalizedDate,
            monthYearLabel = formatMonthYearLabel(date),
            formattedDate = formattedDate,
            weekNumber = date.get(java.time.temporal.IsoFields.WEEK_OF_WEEK_BASED_YEAR),
            weekDays = weekDays,
            classesCount = classItems.size,
            commitmentsCount = commitmentItems.size,
            items = combinedItems
        )
    }

    override fun getClassCheckIn(slotId: String, isoDate: String): ClassCheckInDetail = synchronized(lock) {
        val normalizedDate = parseIsoDateOrToday(isoDate).toString()
        val slot = slots.find { it.slotId == slotId }
            ?: slots.firstOrNull()
        val course = slot?.let { courses[it.courseId] } ?: courses.values.firstOrNull()
        if (course == null) {
            val campus = activeCampusLocked()
            val fallbackDept = resolveDepartmentOption("decom")
            return@synchronized ClassCheckInDetail(
                slotId = slotId,
                courseId = "",
                departmentId = fallbackDept.id,
                departmentName = fallbackDept.name,
                courseName = "Nenhuma disciplina cadastrada",
                formattedDate = formatPortugueseDate(normalizedDate),
                startTime = "08:00",
                endTime = "09:40",
                weatherTempC = campus.weatherTempC,
                weatherCondition = campus.weatherCondition,
                buildingAndRoom = "Campus UFOP",
                professor = "",
                selectedColorHex = fallbackDept.colorHex,
                availableColors = departmentSwatches,
                status = AttendanceStatus.ATTENDING
            )
        }
        val resolvedSlotId = slot?.slotId ?: slotId
        val currentStatus = checkInStatusBySessionKey[sessionKey(resolvedSlotId, normalizedDate)]
            ?: AttendanceStatus.ATTENDING
        val slotLocation = when {
            slot != null && slot.building.isNotBlank() && slot.room.isNotBlank() -> "${slot.building} · ${slot.room}"
            slot != null && slot.building.isNotBlank() -> slot.building
            slot != null && slot.room.isNotBlank() -> slot.room
            else -> course.buildingAndRoom
        }

        val campusWeather = activeCampusLocked()
        val dept = resolveDepartmentOption(course.departmentId)
        ClassCheckInDetail(
            slotId = resolvedSlotId,
            courseId = course.courseId,
            departmentId = dept.id,
            departmentName = dept.name,
            courseName = course.name,
            formattedDate = formatPortugueseDate(normalizedDate),
            startTime = slot?.startTime ?: "08:00",
            endTime = slot?.endTime ?: "09:40",
            weatherTempC = campusWeather.weatherTempC,
            weatherCondition = campusWeather.weatherCondition,
            buildingAndRoom = slotLocation,
            professor = course.professor,
            selectedColorHex = dept.colorHex,
            availableColors = departmentSwatches,
            status = currentStatus
        )
    }

    override fun saveClassCheckIn(
        slotId: String,
        isoDate: String,
        status: AttendanceStatus,
        colorOverrideHex: String?
    ): DisciplineSummaryCard = synchronized(lock) {
        val normalizedDate = parseIsoDateOrToday(isoDate).toString()
        val slot = slots.find { it.slotId == slotId }
            ?: throw IllegalArgumentException("Horário não encontrado.")
        val course = courses[slot.courseId]
            ?: throw IllegalArgumentException("Disciplina não encontrada.")

        checkInStatusBySessionKey[sessionKey(slot.slotId, normalizedDate)] = status
        // Sprint 6: cores padronizadas por departamento — colorOverrideHex é ignorado.

        val updatedSummary = buildDisciplineSummaryLocked(course)
        persistStateLocked()
        _mutationVersion.value += 1L
        updatedSummary
    }

    override fun listDepartments(): List<DepartmentColorOption> = departmentSwatches

    override fun listDepartmentsForSemester(semester: String): List<DepartmentColorOption> = synchronized(lock) {
        val cleanSemester = semester.trim()
        val activeDeptIds = courses.values
            .filter { cleanSemester.isBlank() || it.semester == cleanSemester }
            .map { resolveDepartmentOption(it.departmentId).id }
            .toSet()
        if (activeDeptIds.isEmpty()) {
            emptyList()
        } else {
            departmentSwatches.filter { it.id in activeDeptIds }
        }
    }

    override fun listUfopBuildings(campusId: String?): List<UfopBuildingOption> {
        val raw = campusId?.trim()?.takeIf { it.isNotEmpty() } ?: return ufopBuildingsCatalog
        val resolvedId = when {
            ufopBuildingsCatalog.any { it.campusId.equals(raw, ignoreCase = true) } -> raw.lowercase()
            ufopBuildingsCatalog.any { it.campusName.equals(raw, ignoreCase = true) } ->
                ufopBuildingsCatalog.first { it.campusName.equals(raw, ignoreCase = true) }.campusId
            else -> resolveCampusIdFromText(raw)
        } ?: return emptyList()
        return ufopBuildingsCatalog.filter { it.campusId.equals(resolvedId, ignoreCase = true) }
    }

    override fun listDisciplines(
        semester: String,
        departmentId: String?
    ): List<DisciplineSummaryCard> = synchronized(lock) {
        val normalizedFilter = departmentId?.trim()?.takeIf { it.isNotEmpty() }?.let { raw ->
            normalizeKnownDepartmentId(raw) ?: raw.lowercase()
        }
        courses.values
            .filter { it.semester == semester || semester.isBlank() }
            .filter { normalizedFilter == null || resolveDepartmentOption(it.departmentId).id == normalizedFilter }
            .map { buildDisciplineSummaryLocked(it) }
    }

    override fun getDisciplineDetail(courseId: String): DisciplineDetail = synchronized(lock) {
        val course = courses[courseId] ?: courses.values.firstOrNull()
        if (course == null) {
            val fallbackDept = resolveDepartmentOption("decom")
            return@synchronized DisciplineDetail(
                summary = DisciplineSummaryCard(
                    courseId = courseId,
                    name = "Disciplina removida",
                    departmentId = fallbackDept.id,
                    departmentName = fallbackDept.name,
                    colorHex = fallbackDept.colorHex,
                    workloadHours = 60,
                    currentAbsences = 0,
                    maxAbsences = 18,
                    cancelledClasses = 0
                ),
                semester = "2026.2",
                assessments = emptyList(),
                notes = ""
            )
        }
        val courseAssessments = assessmentsByCourseId[course.courseId]
            ?.sortedWith(compareBy<AssessmentItem> { it.dueDate }.thenBy { it.dueTime })
            ?: course.assessments
        DisciplineDetail(
            summary = buildDisciplineSummaryLocked(course),
            semester = course.semester,
            assessments = courseAssessments,
            notes = notesByCourseId[course.courseId] ?: course.initialNotes,
            professor = course.professor,
            buildingAndRoom = course.buildingAndRoom,
            shortLocation = course.shortLocation,
            code = resolveCourseCode(course)
        )
    }

    override fun updateDisciplineNotes(courseId: String, notes: String) = synchronized(lock) {
        if (courseId in courses) {
            notesByCourseId[courseId] = notes
            persistStateLocked()
            _mutationVersion.value += 1L
        }
    }

    override fun getWeeklyTimetable(semester: String): WeeklyTimetable = synchronized(lock) {
        val cleanSemester = semester.trim().ifBlank { studentProfile.activeSemester.ifBlank { "2026.2" } }
        val semesterCourses = courses.values
            .filter { it.semester == cleanSemester }
            .associateBy { it.courseId }
        val entries = slots.mapNotNull { slot ->
            val course = semesterCourses[slot.courseId] ?: return@mapNotNull null
            TimetableEntry(
                dayOfWeek = slot.dayOfWeek,
                startTime = slot.startTime,
                endTime = slot.endTime,
                cell = TimetableCell(
                    slotId = slot.slotId,
                    courseId = course.courseId,
                    courseName = course.name,
                    shortLocation = slot.shortLocation.ifBlank { course.shortLocation },
                    departmentId = course.departmentId,
                    departmentName = resolveDepartmentName(course),
                    colorHex = resolveCourseColor(course),
                    code = resolveCourseCode(course)
                )
            )
        }
        buildTimetable(cleanSemester, "Semana de aulas · $cleanSemester", entries)
    }

    private data class TimetableEntry(
        val dayOfWeek: Int,
        val startTime: String,
        val endTime: String,
        val cell: TimetableCell
    )

    private fun buildTimetable(
        semester: String,
        gridTitle: String,
        entries: List<TimetableEntry>
    ): WeeklyTimetable {
        val daysHeader = buildList {
            add(1 to "SEG")
            add(2 to "TER")
            add(3 to "QUA")
            add(4 to "QUI")
            add(5 to "SEX")
            if (entries.any { it.dayOfWeek == 6 }) {
                add(6 to "SÁB")
            }
        }
        val defaultIntervals = listOf(
            "08:00" to "09:40",
            "10:00" to "11:40",
            "14:00" to "15:00"
        )
        val baseIntervals = entries.map { it.startTime to it.endTime }
        val timeIntervals = (if (baseIntervals.isEmpty()) defaultIntervals else baseIntervals)
            .distinctBy { "${it.first}-${it.second}" }
            .sortedWith(compareBy<Pair<String, String>> { it.first }.thenBy { it.second })

        val rows = timeIntervals.map { (start, end) ->
            val cellsMap = daysHeader.associate { (day, _) ->
                day to entries.find {
                    it.dayOfWeek == day && it.startTime == start && it.endTime == end
                }?.cell
            }
            TimetableRow(
                startTime = start,
                endTime = end,
                cellsByDay = cellsMap
            )
        }

        val activeLegendIds = entries.map { it.cell.departmentId }.toSet()
        val legendDepartments = if (activeLegendIds.isNotEmpty()) {
            departmentSwatches.filter { it.id in activeLegendIds }
        } else {
            emptyList()
        }

        return WeeklyTimetable(
            semester = semester,
            gridTitle = gridTitle,
            days = daysHeader,
            rows = rows,
            legend = legendDepartments
        )
    }

    override fun createCommitment(
        title: String,
        description: String,
        commitmentDate: String,
        startTime: String,
        endTime: String,
        location: String,
        weatherTempC: Int?
    ): AgendaTimelineItem = synchronized(lock) {
        val normalizedDate = parseIsoDateOrToday(commitmentDate).toString()
        val cleanTitle = title.trim().ifBlank { "Compromisso no campus" }
        val cleanLocation = location.trim().ifBlank { "Campus UFOP" }
        val cleanDescription = description.trim().ifBlank { "Compromisso acadêmico" }
        val cleanStart = startTime.trim().ifBlank { "14:00" }
        val cleanEnd = endTime.trim().ifBlank { "15:00" }
        val activeCampusTemp = activeCampusLocked().weatherTempC
        val resolvedTemp = weatherTempC ?: activeCampusTemp

        generatedIdCounter += 1L
        val newId = "comm_${normalizedDate}_$generatedIdCounter"
        val newSeed = CommitmentSeed(
            id = newId,
            isoDate = normalizedDate,
            title = cleanTitle,
            startTime = cleanStart,
            endTime = cleanEnd,
            location = cleanLocation,
            subtitle = cleanDescription,
            colorHex = COMMITMENT_COLOR_HEX,
            weatherTempC = resolvedTemp
        )
        commitments.add(newSeed)
        persistStateLocked()
        _mutationVersion.value += 1L

        toCommitmentTimelineItem(newSeed)
    }

    override fun updateCommitment(
        commitmentId: String,
        title: String,
        description: String,
        commitmentDate: String,
        startTime: String,
        endTime: String,
        location: String
    ): AgendaTimelineItem = synchronized(lock) {
        val idx = commitments.indexOfFirst { it.id == commitmentId }
        if (idx < 0) {
            throw IllegalArgumentException("Compromisso não encontrado: $commitmentId")
        }
        val existing = commitments[idx]
        val normalizedDate = parseIsoDateOrToday(commitmentDate).toString()
        val cleanTitle = title.trim().ifBlank { existing.title }
        val cleanDescription = description.trim().ifBlank { existing.subtitle }
        val cleanStart = startTime.trim().ifBlank { existing.startTime }
        val cleanEnd = endTime.trim().ifBlank { existing.endTime }
        val cleanLocation = location.trim().ifBlank { existing.location }

        val updated = existing.copy(
            isoDate = normalizedDate,
            title = cleanTitle,
            startTime = cleanStart,
            endTime = cleanEnd,
            location = cleanLocation,
            subtitle = cleanDescription
        )
        commitments[idx] = updated
        persistStateLocked()
        _mutationVersion.value += 1L
        toCommitmentTimelineItem(updated)
    }

    private fun toCommitmentTimelineItem(comm: CommitmentSeed): AgendaTimelineItem = AgendaTimelineItem(
        id = comm.id,
        slotId = null,
        courseId = null,
        kind = TimelineItemKind.COMMITMENT,
        title = comm.title,
        startTime = comm.startTime,
        endTime = comm.endTime,
        location = comm.location,
        subtitle = comm.subtitle,
        colorHex = comm.colorHex,
        weatherTempC = comm.weatherTempC,
        status = null
    )

    override fun deleteCommitment(commitmentId: String): Unit = synchronized(lock) {
        val removed = commitments.removeAll { it.id == commitmentId }
        if (removed) {
            persistStateLocked()
            _mutationVersion.value += 1L
        }
    }


    override fun createAssessment(
        courseId: String,
        title: String,
        kind: String,
        dueDate: String,
        dueTime: String
    ): AssessmentItem = synchronized(lock) {
        val targetCourse = courses[courseId] ?: courses.values.first()
        val (isoDueDate, dayNumber, monthShort) = parseDueDateParts(dueDate)
        val cleanTitle = title.trim().ifBlank { "Nova avaliação" }
        val cleanKind = kind.trim().ifBlank { "Trabalho" }
        val cleanTime = dueTime.trim().ifBlank { "18:00" }

        generatedIdCounter += 1L
        val newItem = AssessmentItem(
            id = "assess_${targetCourse.courseId}_$generatedIdCounter",
            courseId = targetCourse.courseId,
            dayNumber = dayNumber,
            monthShort = monthShort,
            title = cleanTitle,
            kind = cleanKind,
            dueTime = cleanTime,
            dueDate = isoDueDate,
            completed = false
        )

        val list = assessmentsByCourseId.getOrPut(targetCourse.courseId) { mutableListOf() }
        list.add(newItem)
        persistStateLocked()
        _mutationVersion.value += 1L
        newItem
    }

    override fun updateAssessment(
        assessmentId: String,
        courseId: String,
        title: String,
        kind: String,
        dueDate: String,
        dueTime: String,
        completed: Boolean
    ): AssessmentItem = synchronized(lock) {
        var foundCourseId: String? = null
        var foundIdx = -1
        var existing: AssessmentItem? = null
        for ((cid, list) in assessmentsByCourseId) {
            val idx = list.indexOfFirst { it.id == assessmentId }
            if (idx >= 0) {
                foundCourseId = cid
                foundIdx = idx
                existing = list[idx]
                break
            }
        }
        if (existing == null || foundCourseId == null || foundIdx < 0) {
            throw IllegalArgumentException("Avaliação não encontrada: $assessmentId")
        }

        val targetCourseId = if (courseId in courses) {
            courseId
        } else if (foundCourseId in courses) {
            foundCourseId
        } else {
            courses.keys.firstOrNull() ?: existing.courseId
        }

        val (isoDueDate, dayNumber, monthShort) = parseDueDateParts(dueDate)
        val cleanTitle = title.trim().ifBlank { existing.title }
        val cleanKind = kind.trim().ifBlank { existing.kind }
        val cleanTime = dueTime.trim().ifBlank { existing.dueTime }

        val updated = existing.copy(
            courseId = targetCourseId,
            dayNumber = dayNumber,
            monthShort = monthShort,
            title = cleanTitle,
            kind = cleanKind,
            dueTime = cleanTime,
            dueDate = isoDueDate,
            completed = completed
        )

        if (targetCourseId == foundCourseId) {
            assessmentsByCourseId[foundCourseId]?.set(foundIdx, updated)
        } else {
            assessmentsByCourseId[foundCourseId]?.removeAt(foundIdx)
            val targetList = assessmentsByCourseId.getOrPut(targetCourseId) { mutableListOf() }
            targetList.add(updated)
        }

        persistStateLocked()
        _mutationVersion.value += 1L
        updated
    }

    override fun toggleAssessmentCompleted(
        assessmentId: String,
        completed: Boolean
    ): AssessmentItem = synchronized(lock) {
        for (list in assessmentsByCourseId.values) {
            val idx = list.indexOfFirst { it.id == assessmentId }
            if (idx >= 0) {
                val updated = list[idx].copy(completed = completed)
                list[idx] = updated
                persistStateLocked()
                _mutationVersion.value += 1L
                return@synchronized updated
            }
        }
        val fallback = assessmentsByCourseId.values.flatten().first().copy(completed = completed)
        persistStateLocked()
        _mutationVersion.value += 1L
        fallback
    }

    override fun deleteAssessment(assessmentId: String): Unit = synchronized(lock) {
        var removedAny = false
        for (list in assessmentsByCourseId.values) {
            if (list.removeAll { it.id == assessmentId }) {
                removedAny = true
            }
        }
        if (removedAny) {
            persistStateLocked()
            _mutationVersion.value += 1L
        }
    }

    override fun listAllAssessments(
        semester: String,
        kindFilter: String?
    ): List<AssessmentWithCourse> = synchronized(lock) {
        val normalizedFilter = kindFilter?.trim()?.lowercase().orEmpty()
        val results = mutableListOf<AssessmentWithCourse>()

        for (course in courses.values) {
            if (semester.isNotBlank() && course.semester != semester) continue
            val color = resolveCourseColor(course)
            val list = assessmentsByCourseId[course.courseId].orEmpty()
            for (item in list) {
                val matchesFilter = when {
                    normalizedFilter.isEmpty() || normalizedFilter == "todas" || normalizedFilter == "all" -> true
                    normalizedFilter == "concluídas" || normalizedFilter == "concluidas" || normalizedFilter == "completed" -> item.completed
                    normalizedFilter == "pendentes" || normalizedFilter == "pending" -> !item.completed
                    normalizedFilter.startsWith("prova") -> item.kind.lowercase().startsWith("prova")
                    normalizedFilter.startsWith("trabalho") -> item.kind.lowercase().startsWith("trabalho")
                    normalizedFilter.startsWith("semin") -> item.kind.lowercase().startsWith("semin")
                    else -> item.kind.equals(normalizedFilter, ignoreCase = true)
                }
                if (matchesFilter) {
                    results.add(
                        AssessmentWithCourse(
                            assessment = item,
                            courseName = course.name,
                            departmentName = course.departmentName,
                            colorHex = color
                        )
                    )
                }
            }
        }

        results.sortedWith(
            compareBy<AssessmentWithCourse> { it.assessment.completed }
                .thenBy { it.assessment.dueDate }
                .thenBy { it.assessment.dueTime }
        )
    }

    private fun allCampusesLocked(): List<UfopCampusInfo> =
        ufopCampusesSeed.map { seed ->
            val live = campusWeatherById[seed.id]
            seed.copy(
                weatherTempC = live?.tempC ?: seed.weatherTempC,
                weatherCondition = live?.condition ?: seed.weatherCondition,
                isActive = seed.id == activeCampusId
            )
        }

    private fun activeCampusLocked(): UfopCampusInfo {
        val campuses = allCampusesLocked()
        return campuses.find { it.isActive } ?: campuses.first()
    }

    override fun getAcademicProfileSummary(semester: String): AcademicProfileSummary = synchronized(lock) {
        val cleanSemester = semester.ifBlank { studentProfile.activeSemester.ifBlank { "2026.2" } }
        val campusesList = allCampusesLocked()
        val activeCampus = campusesList.find { it.isActive } ?: campusesList.first()

        val summaries = courses.values
            .filter { it.semester == cleanSemester }
            .map { buildDisciplineSummaryLocked(it) }

        val allAssessments = courses.values
            .filter { it.semester == cleanSemester }
            .flatMap { assessmentsByCourseId[it.courseId].orEmpty() }

        val atRiskCount = summaries.count { summary ->
            summary.maxAbsences > 0 &&
                (summary.currentAbsences.toDouble() / summary.maxAbsences.toDouble()) >= 0.75
        }

        val events = getAcademicCalendarEventsLocked(cleanSemester)

        AcademicProfileSummary(
            semester = cleanSemester,
            activeCampus = activeCampus,
            campuses = campusesList,
            totalCourses = summaries.size,
            totalAbsences = summaries.sumOf { it.currentAbsences },
            totalMaxAbsences = summaries.sumOf { it.maxAbsences },
            totalCancelledClasses = summaries.sumOf { it.cancelledClasses },
            coursesAtRisk = atRiskCount,
            pendingAssessments = allAssessments.count { !it.completed },
            completedAssessments = allAssessments.count { it.completed },
            calendarEvents = events
        )
    }

    override fun getAcademicCalendarEvents(semester: String): List<AcademicCalendarEvent> = synchronized(lock) {
        getAcademicCalendarEventsLocked(semester)
    }

    private fun getAcademicCalendarEventsLocked(semester: String): List<AcademicCalendarEvent> {
        val cleanSemester = semester.trim().ifBlank { studentProfile.activeSemester.ifBlank { "2026.2" } }
        val custom = calendarEventsBySemester[cleanSemester]
        if (!custom.isNullOrEmpty()) {
            return custom.sortedBy { it.eventDate }
        }
        val seed = calendarEventsSeed.filter { it.semester == cleanSemester }
        if (seed.isNotEmpty()) {
            return seed.sortedBy { it.eventDate }
        }
        return buildSemesterBoundaryEventsLocked(cleanSemester)
    }

    private fun buildSemesterBoundaryEventsLocked(semester: String): List<AcademicCalendarEvent> {
        if (semester != studentProfile.activeSemester) return emptyList()
        val results = mutableListOf<AcademicCalendarEvent>()
        val startDate = parseIsoDateOrNull(studentProfile.semesterStartDate)
        if (startDate != null) {
            val (_, dayNum, monthShort) = parseDueDateParts(startDate.toString())
            results.add(
                AcademicCalendarEvent(
                    id = "cal_${semester}_inicio",
                    semester = semester,
                    eventDate = startDate.toString(),
                    dayNumber = dayNum,
                    monthShort = monthShort,
                    title = "Início das Aulas $semester",
                    category = "Semestre"
                )
            )
        }
        val endDate = parseIsoDateOrNull(studentProfile.semesterEndDate)
        if (endDate != null) {
            val (_, dayNum, monthShort) = parseDueDateParts(endDate.toString())
            results.add(
                AcademicCalendarEvent(
                    id = "cal_${semester}_termino",
                    semester = semester,
                    eventDate = endDate.toString(),
                    dayNumber = dayNum,
                    monthShort = monthShort,
                    title = "Término das Aulas $semester",
                    category = "Semestre"
                )
            )
        }
        return results
    }

    override fun setActiveCampus(campusId: String): UfopCampusInfo = synchronized(lock) {
        val target = ufopCampusesSeed.find { it.id == campusId } ?: ufopCampusesSeed.first()
        activeCampusId = target.id
        studentProfile = studentProfile.copy(campusName = target.city)
        persistStateLocked()
        _mutationVersion.value += 1L
        activeCampusLocked()
    }

    override fun exportSchedulePayload(semester: String): String = synchronized(lock) {
        val cleanSemester = semester.ifBlank { studentProfile.activeSemester.ifBlank { "2026.2" } }
        buildScheduleJsonObjectLocked(cleanSemester).toString(2)
    }

    override fun exportMyGradeBundleJson(semester: String): String = synchronized(lock) {
        val cleanSemester = semester.ifBlank { studentProfile.activeSemester.ifBlank { "2026.2" } }
        val base = buildScheduleJsonObjectLocked(cleanSemester)
        val avatarBase64 = encodeProfileAvatarBase64(studentProfile.avatarPath)
        base.apply {
            put("format", "ufopgrade-v1")
            put("friendName", studentProfile.fullName.trim().ifBlank { "Estudante UFOP" })
            put("courseName", studentProfile.courseName.trim())
            if (!avatarBase64.isNullOrBlank()) {
                put("avatarBase64", avatarBase64)
            }
        }.toString(2)
    }

    private fun buildScheduleJsonObjectLocked(cleanSemester: String): JSONObject {
        val semesterCourses = courses.values.filter { it.semester == cleanSemester }
        val courseIds = semesterCourses.map { it.courseId }.toSet()
        val semesterSlots = slots.filter { it.courseId in courseIds }

        val coursesArr = JSONArray()
        semesterCourses.forEach { c ->
            val dept = resolveDepartmentOption(c.departmentId)
            coursesArr.put(
                JSONObject().apply {
                    put("courseId", c.courseId)
                    put("code", resolveCourseCode(c))
                    put("semester", c.semester)
                    put("name", c.name)
                    put("departmentId", dept.id)
                    put("departmentName", dept.name)
                    put("colorHex", dept.colorHex)
                    put("workloadHours", c.workloadHours)
                    put("maxAbsences", c.maxAbsences)
                    put("professor", c.professor)
                    put("buildingAndRoom", c.buildingAndRoom)
                    put("shortLocation", c.shortLocation)
                }
            )
        }

        val slotsArr = JSONArray()
        semesterSlots.forEach { s ->
            slotsArr.put(
                JSONObject().apply {
                    put("slotId", s.slotId)
                    put("courseId", s.courseId)
                    put("dayOfWeek", s.dayOfWeek)
                    put("startTime", s.startTime)
                    put("endTime", s.endTime)
                    put("building", s.building)
                    put("room", s.room)
                    put("shortLocation", s.shortLocation)
                    put("classWeight", s.classWeight)
                }
            )
        }

        return JSONObject().apply {
            put("semester", cleanSemester)
            put("campusId", activeCampusId)
            put("courses", coursesArr)
            put("slots", slotsArr)
        }
    }

    private fun encodeProfileAvatarBase64(avatarPath: String?): String? {
        val path = avatarPath?.trim()?.takeIf { it.isNotEmpty() } ?: return null
        return try {
            val file = File(path)
            if (!file.exists() || file.length() <= 0L) return null
            val decoded = BitmapFactory.decodeFile(file.absolutePath) ?: return null
            val squareSize = minOf(decoded.width, decoded.height)
            val cropX = (decoded.width - squareSize) / 2
            val cropY = (decoded.height - squareSize) / 2
            val cropped = if (decoded.width != decoded.height && squareSize > 0) {
                Bitmap.createBitmap(decoded, cropX, cropY, squareSize, squareSize)
            } else {
                decoded
            }
            val scaled = Bitmap.createScaledBitmap(cropped, 120, 120, true)
            val out = ByteArrayOutputStream()
            scaled.compress(Bitmap.CompressFormat.JPEG, 70, out)
            val bytes = out.toByteArray()
            if (scaled !== cropped && !scaled.isRecycled) scaled.recycle()
            if (cropped !== decoded && !cropped.isRecycled) cropped.recycle()
            if (!decoded.isRecycled) decoded.recycle()
            if (bytes.isEmpty()) null else Base64.encodeToString(bytes, Base64.NO_WRAP)
        } catch (_: Throwable) {
            null
        }
    }

    override fun importSchedulePayload(jsonPayload: String): Int = synchronized(lock) {
        val trimmed = jsonPayload.trim()
        if (trimmed.isBlank() || !trimmed.contains("{")) {
            return@synchronized 0
        }

        var importedCount = 0
        val parsedRoot = try { JSONObject(trimmed) } catch (_: Throwable) { null }
        if (parsedRoot != null) {
            val defaultSemester = parsedRoot.optNonNullString("semester", studentProfile.activeSemester)
                .ifBlank { "2026.2" }
            val coursesArr = parsedRoot.optJSONArray("courses")
            if (coursesArr != null) {
                for (i in 0 until coursesArr.length()) {
                    val obj = coursesArr.optJSONObject(i) ?: continue
                    val id = obj.optNonNullString("courseId")
                        .ifBlank { obj.optNonNullString("id") }
                        .trim()
                    val name = obj.optNonNullString("name").trim()
                    if (id.isEmpty() || name.isEmpty()) continue
                    val semester = obj.optNonNullString("semester", defaultSemester).ifBlank { defaultSemester }
                    val rawDeptId = obj.optNonNullString("departmentId")
                        .ifBlank { obj.optNonNullString("department_id", "comp") }
                        .ifBlank { "comp" }
                    val dept = resolveDepartmentOption(rawDeptId)
                    val workloadHours = obj.optInt("workloadHours", obj.optInt("workload_hours", 60))
                        .coerceAtLeast(1)
                    val maxAbsences = obj.optInt(
                        "maxAbsences",
                        obj.optInt("max_absences", (workloadHours * 3) / 10)
                    ).coerceAtLeast(0)
                    val professor = obj.optNonNullString("professor", "").trim()
                    val buildingAndRoom = obj.optNonNullString("buildingAndRoom")
                        .ifBlank { obj.optNonNullString("building_and_room", "Campus UFOP") }
                        .ifBlank { "Campus UFOP" }
                    val shortLocation = obj.optNonNullString("shortLocation")
                        .ifBlank { obj.optNonNullString("short_location", "UFOP") }
                        .ifBlank { "UFOP" }

                    val existing = courses[id]
                    val resolvedCode = deriveCourseCodeFallback(
                        explicitCode = obj.optNonNullString("code"),
                        notes = existing?.initialNotes.orEmpty(),
                        courseId = id,
                        courseName = name
                    )
                    courses[id] = CourseSeed(
                        courseId = id,
                        semester = semester,
                        name = name,
                        departmentId = dept.id,
                        departmentName = dept.name,
                        defaultColorHex = dept.colorHex,
                        workloadHours = workloadHours,
                        baseAbsences = existing?.baseAbsences ?: 0,
                        maxAbsences = maxAbsences,
                        baseCancelledClasses = existing?.baseCancelledClasses ?: 0,
                        professor = professor,
                        buildingAndRoom = buildingAndRoom,
                        shortLocation = shortLocation,
                        assessments = existing?.assessments ?: emptyList(),
                        initialNotes = existing?.initialNotes ?: "",
                        code = resolvedCode
                    )
                    if (id !in assessmentsByCourseId) {
                        assessmentsByCourseId[id] = mutableListOf()
                    }
                    importedCount += 1
                }
            }

            val slotsArr = parsedRoot.optJSONArray("slots")
            if (slotsArr != null) {
                val activeCampus = activeCampusLocked()
                for (i in 0 until slotsArr.length()) {
                    val obj = slotsArr.optJSONObject(i) ?: continue
                    val slotId = obj.optNonNullString("slotId")
                        .ifBlank { obj.optNonNullString("slot_id") }
                        .trim()
                    val courseId = obj.optNonNullString("courseId")
                        .ifBlank { obj.optNonNullString("course_id") }
                        .trim()
                    if (slotId.isEmpty() || courseId.isEmpty()) continue
                    val dayOfWeek = obj.optInt("dayOfWeek", obj.optInt("day_of_week", 2)).coerceIn(1, 7)
                    val startTime = obj.optNonNullString("startTime")
                        .ifBlank { obj.optNonNullString("start_time", "08:00") }
                        .ifBlank { "08:00" }
                    val endTime = obj.optNonNullString("endTime")
                        .ifBlank { obj.optNonNullString("end_time", "09:40") }
                        .ifBlank { "09:40" }
                    val building = obj.optNonNullString("building", "Campus UFOP").ifBlank { "Campus UFOP" }
                    val room = obj.optNonNullString("room", "").trim()
                    val shortLocation = obj.optNonNullString("shortLocation")
                        .ifBlank { obj.optNonNullString("short_location") }
                        .ifBlank { courses[courseId]?.shortLocation ?: buildShortLocation(building, room) }
                    val classWeight = obj.optInt("classWeight", obj.optInt("class_weight", 1)).coerceAtLeast(1)

                    slots.removeAll { it.slotId == slotId }
                    slots.add(
                        SlotSeed(
                            slotId = slotId,
                            courseId = courseId,
                            dayOfWeek = dayOfWeek,
                            startTime = startTime,
                            endTime = endTime,
                            building = building,
                            room = room,
                            shortLocation = shortLocation,
                            defaultTempC = activeCampus.weatherTempC,
                            weatherCondition = activeCampus.weatherCondition,
                            classWeight = classWeight
                        )
                    )
                }
            }

            val campusIdInJson = parsedRoot.optNonNullString("campusId")
            if (campusIdInJson.isNotBlank() && ufopCampusesSeed.any { it.id == campusIdInJson }) {
                activeCampusId = campusIdInJson
                ufopCampusesSeed.find { it.id == campusIdInJson }?.let { campus ->
                    studentProfile = studentProfile.copy(campusName = campus.city)
                }
            }
        }

        if (importedCount == 0 && trimmed.startsWith("{")) {
            importedCount = courses.size
        }

        persistStateLocked()
        _mutationVersion.value += 1L
        importedCount
    }

    override fun createCourse(
        semester: String,
        departmentId: String,
        name: String,
        professor: String,
        workloadHours: Int,
        colorOverrideHex: String?
    ): DisciplineSummaryCard = synchronized(lock) {
        val cleanName = name.trim()
        require(cleanName.isNotEmpty()) { "Informe o nome da disciplina." }

        val cleanSemester = semester.trim().ifBlank { studentProfile.activeSemester.ifBlank { "2026.2" } }
        val cleanProfessor = professor.trim()
        val deptOption = resolveDepartmentOption(departmentId)
        val safeWorkload = if (workloadHours > 0) workloadHours else 60
        val maxAbsences = (safeWorkload * 3) / 10
        // Sprint 6: cor derivada exclusivamente do departamento (ignora colorOverrideHex).
        val resolvedColor = deptOption.colorHex

        generatedIdCounter += 1L
        val courseId = "course_$generatedIdCounter"
        val resolvedCode = deriveCourseCodeFallback(null, "", courseId, cleanName)
        val newCourse = CourseSeed(
            courseId = courseId,
            semester = cleanSemester,
            name = cleanName,
            departmentId = deptOption.id,
            departmentName = deptOption.name,
            defaultColorHex = resolvedColor,
            workloadHours = safeWorkload,
            baseAbsences = 0,
            maxAbsences = maxAbsences,
            baseCancelledClasses = 0,
            professor = cleanProfessor,
            buildingAndRoom = "Campus UFOP",
            shortLocation = "UFOP",
            assessments = emptyList(),
            initialNotes = "",
            code = resolvedCode
        )
        courses[courseId] = newCourse
        notesByCourseId[courseId] = ""
        assessmentsByCourseId[courseId] = mutableListOf()
        persistStateLocked()
        _mutationVersion.value += 1L
        buildDisciplineSummaryLocked(newCourse)
    }

    override fun updateCourse(
        courseId: String,
        name: String,
        professor: String,
        departmentId: String,
        workloadHours: Int
    ): DisciplineSummaryCard = synchronized(lock) {
        val existing = courses[courseId]
            ?: throw IllegalArgumentException("Disciplina não encontrada.")
        val cleanName = name.trim()
        require(cleanName.isNotEmpty()) { "Informe o nome da disciplina." }
        val cleanProfessor = professor.trim()
        val deptOption = resolveDepartmentOption(departmentId)
        val safeWorkload = if (workloadHours > 0) workloadHours else existing.workloadHours
        val maxAbsences = (safeWorkload * 3) / 10

        val updated = existing.copy(
            name = cleanName,
            professor = cleanProfessor,
            departmentId = deptOption.id,
            departmentName = deptOption.name,
            defaultColorHex = deptOption.colorHex,
            workloadHours = safeWorkload,
            maxAbsences = maxAbsences,
            code = existing.code.ifBlank {
                deriveCourseCodeFallback(null, notesByCourseId[courseId] ?: existing.initialNotes, courseId, cleanName)
            }
        )
        courses[courseId] = updated
        courseColorOverrides.remove(courseId)
        persistStateLocked()
        _mutationVersion.value += 1L
        buildDisciplineSummaryLocked(updated)
    }

    override fun updateCourseAbsences(
        courseId: String,
        totalAbsences: Int
    ): DisciplineSummaryCard = synchronized(lock) {
        val course = courses[courseId]
            ?: throw IllegalArgumentException("Disciplina não encontrada.")
        val safeTotal = totalAbsences.coerceAtLeast(0)
        val sessionAbsences = computeExtraCheckInAbsencesLocked(courseId)
        val updatedCourse = course.copy(baseAbsences = safeTotal - sessionAbsences)
        courses[courseId] = updatedCourse

        val courseCode = resolveCourseCode(updatedCourse)
        val txIdx = transcriptEntries.indexOfFirst { entry ->
            entry.semester == updatedCourse.semester &&
                entry.status == "CS" && (
                    entry.id.equals(courseId, ignoreCase = true) ||
                        entry.code.equals(courseCode, ignoreCase = true) ||
                        normalizeDedupKey(entry.name) == normalizeDedupKey(updatedCourse.name)
                    )
        }
        if (txIdx >= 0) {
            transcriptEntries[txIdx] = transcriptEntries[txIdx].copy(absences = safeTotal)
        }

        persistStateLocked()
        _mutationVersion.value += 1L
        buildDisciplineSummaryLocked(updatedCourse)
    }

    override fun getDarkThemePreference(): String = synchronized(lock) { darkThemePreference }

    override fun setDarkThemePreference(mode: String) = synchronized(lock) {
        val normalized = when (mode.trim().uppercase()) {
            "DARK" -> "DARK"
            "LIGHT" -> "LIGHT"
            else -> "SYSTEM"
        }
        darkThemePreference = normalized
        persistStateLocked()
        _mutationVersion.value += 1L
    }

    private fun normalizeAccentColorKey(raw: String?): String = when (raw?.trim()?.uppercase()) {
        "GREEN" -> "GREEN"
        "RED" -> "RED"
        "ORANGE" -> "ORANGE"
        "PURPLE" -> "PURPLE"
        "YELLOW" -> "YELLOW"
        "CYAN" -> "CYAN"
        "BROWN" -> "BROWN"
        else -> "GREEN"
    }

    override fun getAccentColorPreference(): String = synchronized(lock) { accentColorPreference }

    override fun setAccentColorPreference(accentKey: String) = synchronized(lock) {
        accentColorPreference = normalizeAccentColorKey(accentKey)
        persistStateLocked()
        _mutationVersion.value += 1L
    }

    private fun normalizeAccessibilitySettings(
        settings: CampusAccessibilitySettings
    ): CampusAccessibilitySettings {
        val safeScale = if (settings.fontScaleMultiplier.isFinite()) {
            settings.fontScaleMultiplier.coerceIn(0.85f, 2.0f)
        } else {
            1.0f
        }
        return settings.copy(fontScaleMultiplier = safeScale)
    }

    override fun getAccessibilitySettings(): CampusAccessibilitySettings = synchronized(lock) {
        accessibilitySettings
    }

    override fun setAccessibilitySettings(settings: CampusAccessibilitySettings) = synchronized(lock) {
        accessibilitySettings = normalizeAccessibilitySettings(settings)
        persistStateLocked()
        _mutationVersion.value += 1L
    }

    override fun isGuidedTourCompleted(): Boolean = synchronized(lock) { guidedTourCompleted }

    override fun setGuidedTourCompleted(completed: Boolean) = synchronized(lock) {
        guidedTourCompleted = completed
        persistStateLocked()
        _mutationVersion.value += 1L
    }

    override fun getLastSeenReleaseVersion(): String = synchronized(lock) { lastSeenReleaseVersion }

    override fun setLastSeenReleaseVersion(version: String) = synchronized(lock) {
        lastSeenReleaseVersion = version.trim()
        persistStateLocked()
        _mutationVersion.value += 1L
    }

    override fun getDynamicGridCompletedCodes(matrixId: String): Set<String> = synchronized(lock) {
        val cleanMatrixId = matrixId.trim()
        if (cleanMatrixId.isEmpty()) return@synchronized emptySet()
        dynamicGridCompletedByMatrix[cleanMatrixId]?.toSet() ?: emptySet()
    }

    override fun saveDynamicGridCompletedCodes(matrixId: String, completedCodes: Set<String>) = synchronized(lock) {
        val cleanMatrixId = matrixId.trim()
        if (cleanMatrixId.isEmpty()) return@synchronized
        val normalized = linkedSetOf<String>()
        completedCodes.forEach { raw ->
            val clean = raw.trim().uppercase()
            if (clean.isNotEmpty()) {
                normalized.add(clean)
            }
        }
        val previous = dynamicGridCompletedByMatrix[cleanMatrixId]
        if (normalized.isEmpty()) {
            if (previous == null || previous.isEmpty()) {
                dynamicGridCompletedByMatrix.remove(cleanMatrixId)
                return@synchronized
            }
            dynamicGridCompletedByMatrix.remove(cleanMatrixId)
        } else {
            if (previous != null && previous == normalized) {
                return@synchronized
            }
            dynamicGridCompletedByMatrix[cleanMatrixId] = normalized
        }
        persistStateLocked()
        _mutationVersion.value += 1L
        onMutation?.invoke()
    }

    override fun getDynamicGridAllCompleted(): Map<String, Set<String>> = synchronized(lock) {
        val snapshot = linkedMapOf<String, Set<String>>()
        dynamicGridCompletedByMatrix.forEach { (matrixId, codes) ->
            if (codes.isNotEmpty()) {
                snapshot[matrixId] = codes.toSet()
            }
        }
        snapshot
    }

    override fun exportFullAccountSnapshotJson(): String = synchronized(lock) {
        buildPersistedJsonLocked().toString()
    }

    override fun restoreFullAccountSnapshotJson(rawJson: String): Boolean = synchronized(lock) {
        val trimmed = rawJson.trim()
        if (trimmed.isEmpty()) return@synchronized false
        val root = try {
            JSONObject(trimmed)
        } catch (_: Throwable) {
            return@synchronized false
        }
        val schemaVersion = root.optInt("schemaVersion", -1)
        val hasValidData = schemaVersion >= 1 ||
            root.has("courses") ||
            root.has("studentProfile") ||
            root.has("transcriptEntries") ||
            root.has("dynamicGridCompletedByMatrix")
        if (!hasValidData) return@synchronized false

        val restored = runCatching {
            restoreStateFromJsonLocked(root)
            true
        }.getOrDefault(false)
        if (!restored) return@synchronized false

        persistStateLocked()
        _mutationVersion.value += 1L
        true
    }

    override fun wipeAllUserDataForLogout() = synchronized(lock) {
        applyVirginStateLocked()
        stateFile?.let { targetFile ->
            val parentDir = targetFile.absoluteFile.parentFile
            runCatching { targetFile.delete() }
            if (parentDir != null) {
                runCatching { File(parentDir, "${targetFile.name}.bak").delete() }
                runCatching { File(parentDir, "${targetFile.name}.tmp").delete() }
            }
        }
        _mutationVersion.value += 1L
        runCatching { onStatePersisted?.invoke() }
        Unit
    }

    override fun getExportFolderConfig(): ExportFolderConfig = synchronized(lock) {
        ExportFolderConfig(
            treeUri = exportFolderTreeUri,
            displayLabel = exportFolderDisplayLabel
        )
    }

    override fun setExportFolderConfig(
        treeUri: String?,
        displayLabel: String?
    ): ExportFolderConfig = synchronized(lock) {
        val cleanUri = treeUri?.trim()?.takeIf { it.isNotEmpty() }
        val cleanLabel = if (cleanUri == null) {
            DEFAULT_EXPORT_FOLDER_LABEL
        } else {
            displayLabel?.trim()?.takeIf { it.isNotEmpty() } ?: DEFAULT_EXPORT_FOLDER_LABEL
        }
        exportFolderTreeUri = cleanUri
        exportFolderDisplayLabel = cleanLabel
        persistStateLocked()
        _mutationVersion.value += 1L
        ExportFolderConfig(treeUri = cleanUri, displayLabel = cleanLabel)
    }

    override fun setTotalRequiredHours(hours: Int): Unit = synchronized(lock) {
        totalRequiredHours = hours.coerceIn(100, 10000)
        persistStateLocked()
        _mutationVersion.value += 1L
    }

    override fun deleteCourse(courseId: String): Unit = synchronized(lock) {
        val removed = courses.remove(courseId) != null
        val removedSlotIds = slots.filter { it.courseId == courseId }.map { it.slotId }.toSet()
        val removedSlots = slots.removeAll { it.courseId == courseId }
        if (removedSlotIds.isNotEmpty()) {
            checkInStatusBySessionKey.keys.removeAll { key ->
                removedSlotIds.any { slotId -> key.startsWith("${slotId}_") }
            }
        }
        courseColorOverrides.remove(courseId)
        notesByCourseId.remove(courseId)
        assessmentsByCourseId.remove(courseId)
        if (removed || removedSlots) {
            persistStateLocked()
            _mutationVersion.value += 1L
        }
    }

    override fun addScheduleSlot(
        courseId: String,
        dayOfWeek: Int,
        startTime: String,
        endTime: String,
        building: String,
        room: String,
        shortLocation: String
    ): CourseScheduleSlotItem = synchronized(lock) {
        val course = courses[courseId]
            ?: throw IllegalArgumentException("Disciplina não encontrada.")
        require(dayOfWeek in 1..7) { "Dia da semana inválido." }

        val cleanStart = startTime.trim().ifBlank { "08:00" }
        val cleanEnd = endTime.trim().ifBlank { "09:40" }
        validateSlotTimes(cleanStart, cleanEnd)
        val cleanBuilding = building.trim().ifBlank { "Prédio A" }
        val cleanRoom = room.trim()
        val cleanShort = shortLocation.trim().ifBlank { buildShortLocation(cleanBuilding, cleanRoom) }

        checkSlotConflictLocked(course.semester, dayOfWeek, cleanStart, cleanEnd, ignoreSlotId = null)

        generatedIdCounter += 1L
        val slotId = "slot_${dayLabelFor(dayOfWeek).lowercase()}_${cleanStart.replace(":", "")}_$generatedIdCounter"
        val activeCampus = activeCampusLocked()
        val newSlot = SlotSeed(
            slotId = slotId,
            courseId = courseId,
            dayOfWeek = dayOfWeek,
            startTime = cleanStart,
            endTime = cleanEnd,
            building = cleanBuilding,
            room = cleanRoom,
            shortLocation = cleanShort,
            defaultTempC = activeCampus.weatherTempC,
            weatherCondition = activeCampus.weatherCondition,
            classWeight = computeClassWeightFromTimes(cleanStart, cleanEnd)
        )
        slots.add(newSlot)

        if (course.buildingAndRoom == "Campus UFOP" || course.shortLocation == "UFOP") {
            courses[courseId] = course.copy(
                buildingAndRoom = if (cleanRoom.isNotBlank()) "$cleanBuilding · $cleanRoom" else cleanBuilding,
                shortLocation = cleanShort
            )
        }

        persistStateLocked()
        _mutationVersion.value += 1L
        toCourseScheduleSlotItem(newSlot)
    }

    override fun updateScheduleSlot(
        slotId: String,
        dayOfWeek: Int,
        startTime: String,
        endTime: String,
        building: String,
        room: String,
        shortLocation: String
    ): CourseScheduleSlotItem = synchronized(lock) {
        val idx = slots.indexOfFirst { it.slotId == slotId }
        if (idx < 0) {
            throw IllegalArgumentException("Horário não encontrado.")
        }
        val existing = slots[idx]
        val course = courses[existing.courseId]
            ?: throw IllegalArgumentException("Disciplina não encontrada.")
        require(dayOfWeek in 1..7) { "Dia da semana inválido." }

        val cleanStart = startTime.trim().ifBlank { existing.startTime }
        val cleanEnd = endTime.trim().ifBlank { existing.endTime }
        validateSlotTimes(cleanStart, cleanEnd)
        val cleanBuilding = building.trim().ifBlank { existing.building }
        val cleanRoom = room.trim()
        val cleanShort = shortLocation.trim().ifBlank { buildShortLocation(cleanBuilding, cleanRoom) }

        checkSlotConflictLocked(course.semester, dayOfWeek, cleanStart, cleanEnd, ignoreSlotId = slotId)

        val updated = existing.copy(
            dayOfWeek = dayOfWeek,
            startTime = cleanStart,
            endTime = cleanEnd,
            building = cleanBuilding,
            room = cleanRoom,
            shortLocation = cleanShort,
            classWeight = computeClassWeightFromTimes(cleanStart, cleanEnd)
        )
        slots[idx] = updated

        if (course.buildingAndRoom == "Campus UFOP" || course.shortLocation == "UFOP") {
            courses[course.courseId] = course.copy(
                buildingAndRoom = if (cleanRoom.isNotBlank()) "$cleanBuilding · $cleanRoom" else cleanBuilding,
                shortLocation = cleanShort
            )
        }

        persistStateLocked()
        _mutationVersion.value += 1L
        toCourseScheduleSlotItem(updated)
    }

    private fun validateSlotTimes(startTime: String, endTime: String) {
        val startMin = parseMinutes(startTime)
        val endMin = parseMinutes(endTime)
        if (startMin != null && endMin != null && startMin >= endMin) {
            throw IllegalArgumentException("O horário de término deve ser após o início.")
        }
    }

    private fun computeClassWeightFromTimes(startTime: String, endTime: String): Int {
        val startMin = parseMinutes(startTime) ?: return 1
        val endMin = parseMinutes(endTime) ?: return 1
        val dur = endMin - startMin
        if (dur <= 0) return 1
        return when {
            dur <= 60 -> 1
            dur <= 115 -> 2
            dur <= 175 -> 3
            dur <= 235 -> 4
            else -> ((dur + 25) / 55).coerceIn(1, 6)
        }
    }

    private fun checkSlotConflictLocked(
        semester: String,
        dayOfWeek: Int,
        startTime: String,
        endTime: String,
        ignoreSlotId: String?
    ) {
        val conflictSlot = slots.firstOrNull { existing ->
            if (ignoreSlotId != null && existing.slotId == ignoreSlotId) return@firstOrNull false
            val existingCourse = courses[existing.courseId] ?: return@firstOrNull false
            existingCourse.semester == semester &&
                existing.dayOfWeek == dayOfWeek &&
                (existing.startTime == startTime ||
                    timesOverlap(existing.startTime, existing.endTime, startTime, endTime))
        }
        if (conflictSlot != null) {
            val conflictName = courses[conflictSlot.courseId]?.name ?: "outra disciplina"
            throw IllegalStateException(
                "Conflito de horário: já existe $conflictName em ${dayLabelFor(dayOfWeek)} às ${conflictSlot.startTime}."
            )
        }
    }

    override fun deleteScheduleSlot(slotId: String): Unit = synchronized(lock) {
        val removed = slots.removeAll { it.slotId == slotId }
        checkInStatusBySessionKey.keys.removeAll { it.startsWith("${slotId}_") }
        if (removed) {
            persistStateLocked()
            _mutationVersion.value += 1L
        }
    }

    override fun listCourseSlots(courseId: String): List<CourseScheduleSlotItem> = synchronized(lock) {
        slots
            .filter { it.courseId == courseId }
            .sortedWith(compareBy<SlotSeed> { it.dayOfWeek }.thenBy { it.startTime })
            .map { toCourseScheduleSlotItem(it) }
    }

    override fun listUfopTemplates(): List<UfopCurriculumTemplate> = synchronized(lock) {
        ufopTemplatesSeed.map { template ->
            UfopCurriculumTemplate(
                id = template.id,
                code = template.code,
                name = template.name,
                departmentName = template.departmentName,
                campusName = template.campusName,
                description = template.description,
                coursesCount = template.courses.size
            )
        }
    }

    override fun applyUfopTemplate(templateId: String, semester: String): Int = synchronized(lock) {
        val template = ufopTemplatesSeed.find { it.id == templateId }
            ?: throw IllegalArgumentException("Template UFOP não encontrado: $templateId")
        val cleanSemester = semester.trim().ifBlank { "2026.2" }

        val removedCourseIds = courses.values
            .filter { it.semester == cleanSemester }
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

        val activeCampus = activeCampusLocked()

        template.courses.forEach { tplCourse ->
            val deptOption = resolveDepartmentOption(tplCourse.departmentId)
            val resolvedColor = deptOption.colorHex
            val firstSlot = tplCourse.slots.firstOrNull()
            val buildingAndRoom = if (firstSlot != null) {
                "${firstSlot.building} · ${firstSlot.room}"
            } else {
                "Campus UFOP"
            }
            val shortLocation = firstSlot?.shortLocation ?: "UFOP"
            val maxAbsences = (tplCourse.workloadHours * 3) / 10

            courses[tplCourse.courseId] = CourseSeed(
                courseId = tplCourse.courseId,
                semester = cleanSemester,
                name = tplCourse.name,
                departmentId = deptOption.id,
                departmentName = deptOption.name,
                defaultColorHex = resolvedColor,
                workloadHours = tplCourse.workloadHours,
                baseAbsences = 0,
                maxAbsences = maxAbsences,
                baseCancelledClasses = 0,
                professor = tplCourse.professor,
                buildingAndRoom = buildingAndRoom,
                shortLocation = shortLocation,
                assessments = emptyList(),
                initialNotes = tplCourse.notes,
                code = deriveCourseCodeFallback(null, tplCourse.notes, tplCourse.courseId, tplCourse.name)
            )
            notesByCourseId[tplCourse.courseId] = tplCourse.notes
            assessmentsByCourseId[tplCourse.courseId] = mutableListOf()

            tplCourse.slots.forEachIndexed { idx, tplSlot ->
                val slotId = "slot_${template.id}_${tplCourse.courseId}_${tplSlot.dayOfWeek}_${idx + 1}"
                slots.add(
                    SlotSeed(
                        slotId = slotId,
                        courseId = tplCourse.courseId,
                        dayOfWeek = tplSlot.dayOfWeek,
                        startTime = tplSlot.startTime,
                        endTime = tplSlot.endTime,
                        building = tplSlot.building,
                        room = tplSlot.room,
                        shortLocation = tplSlot.shortLocation,
                        defaultTempC = activeCampus.weatherTempC,
                        weatherCondition = activeCampus.weatherCondition,
                        classWeight = computeClassWeightFromTimes(tplSlot.startTime, tplSlot.endTime)
                    )
                )
            }
        }

        persistStateLocked()
        _mutationVersion.value += 1L
        template.courses.size
    }

    // ── Sprint 6: Perfil do Estudante ────────────────────────────────────────────────────────────

    override fun getStudentProfile(): StudentProfile = synchronized(lock) { studentProfile }

    override fun saveStudentProfile(profile: StudentProfile): StudentProfile = synchronized(lock) {
        val cleanSemester = profile.activeSemester.trim().ifBlank { "2026.2" }
        val defaultBounds = resolveDefaultSemesterBounds(cleanSemester)
        val parsedStart = parseIsoDateOrNull(profile.semesterStartDate)?.toString()
        val parsedEnd = parseIsoDateOrNull(profile.semesterEndDate)?.toString()
        val knownDefaultPairs = setOf(
            resolveDefaultSemesterBounds("2026.2"),
            resolveDefaultSemesterBounds("2027.1"),
            resolveDefaultSemesterBounds("2027.2")
        )
        val isStaleDefaultFromOtherSemester = parsedStart != null && parsedEnd != null &&
            (parsedStart to parsedEnd) != defaultBounds && (
                (parsedStart to parsedEnd) in knownDefaultPairs ||
                    (cleanSemester != studentProfile.activeSemester &&
                        parsedStart == studentProfile.semesterStartDate &&
                        parsedEnd == studentProfile.semesterEndDate)
                )
        val cleanStart = if (parsedStart == null || isStaleDefaultFromOtherSemester) {
            defaultBounds.first
        } else {
            parsedStart
        }
        val cleanEnd = if (parsedEnd == null || isStaleDefaultFromOtherSemester) {
            defaultBounds.second
        } else {
            parsedEnd
        }
        val matchedCampusId = resolveCampusIdFromText(profile.campusName)
        if (matchedCampusId != null) {
            activeCampusId = matchedCampusId
        }
        val resolvedCampusCity = ufopCampusesSeed.find { it.id == activeCampusId }?.city
            ?: profile.campusName.trim().ifBlank { "Ouro Preto" }

        studentProfile = profile.copy(
            fullName = profile.fullName.trim(),
            registrationNumber = profile.registrationNumber.trim(),
            courseName = profile.courseName.trim(),
            campusName = resolvedCampusCity,
            activeSemester = cleanSemester,
            semesterStartDate = cleanStart,
            semesterEndDate = cleanEnd,
            avatarPath = profile.avatarPath?.trim()?.takeIf { it.isNotEmpty() }
        )
        persistStateLocked()
        _mutationVersion.value += 1L
        studentProfile
    }

    // ── Sprint 6: Calendário Mensal, Semestres & Grade de Amigos ─────────────────────────────────

    override fun getMonthActivityIndicators(
        year: Int,
        month: Int,
        semester: String
    ): Map<Int, DayActivityIndicator> = synchronized(lock) {
        if (year !in 1900..2100 || month !in 1..12) return@synchronized emptyMap()
        val ym = YearMonth.of(year, month)
        val daysInMonth = ym.lengthOfMonth()
        val cleanSemester = semester.trim().ifBlank { studentProfile.activeSemester.ifBlank { "2026.2" } }
        val semesterCourses = courses.values
            .filter { it.semester == cleanSemester }
            .associateBy { it.courseId }
        val slotsByDay = slots
            .filter { it.courseId in semesterCourses }
            .groupBy { it.dayOfWeek }
        val assessmentsByDate = mutableMapOf<String, MutableList<CourseSeed>>()
        semesterCourses.values.forEach { course ->
            assessmentsByCourseId[course.courseId].orEmpty().forEach { assessment ->
                assessmentsByDate.getOrPut(assessment.dueDate) { mutableListOf() }.add(course)
            }
        }
        val commitmentsByDate = commitments.groupBy { it.isoDate }

        val startBound = parseIsoDateOrNull(studentProfile.semesterStartDate)
        val endBound = parseIsoDateOrNull(studentProfile.semesterEndDate)
        val enforceBounds = cleanSemester == studentProfile.activeSemester &&
            startBound != null &&
            endBound != null &&
            !endBound.isBefore(startBound)

        val result = linkedMapOf<Int, DayActivityIndicator>()
        for (day in 1..daysInMonth) {
            val date = ym.atDay(day)
            val iso = date.toString()
            val inBounds = if (enforceBounds) !date.isBefore(startBound) && !date.isAfter(endBound) else true
            val daySlots = if (inBounds) slotsByDay[date.dayOfWeek.value].orEmpty() else emptyList()
            val dayCommitments = commitmentsByDate[iso].orEmpty()
            val dayAssessmentCourses = assessmentsByDate[iso].orEmpty()

            val colors = buildList {
                daySlots.forEach { slot ->
                    semesterCourses[slot.courseId]?.let { add(resolveCourseColor(it)) }
                }
                dayAssessmentCourses.forEach { course ->
                    add(resolveCourseColor(course))
                }
                dayCommitments.forEach { comm ->
                    add(comm.colorHex)
                }
            }.distinct().take(3)

            result[day] = DayActivityIndicator(
                isoDate = iso,
                hasClasses = daySlots.isNotEmpty(),
                hasCommitments = dayCommitments.isNotEmpty(),
                hasAssessments = dayAssessmentCourses.isNotEmpty(),
                colorsHex = colors
            )
        }
        result
    }

    override fun listAvailableSemesters(): List<String> = synchronized(lock) {
        val all = linkedSetOf<String>()
        studentProfile.activeSemester.trim().takeIf { it.isNotEmpty() }?.let { all.add(it) }
        courses.values.forEach { c ->
            c.semester.trim().takeIf { it.isNotEmpty() }?.let { all.add(it) }
        }
        transcriptEntries.forEach { tx ->
            tx.semester.trim().takeIf { it.isNotEmpty() }?.let { all.add(it) }
        }
        if (all.isEmpty()) {
            all.add("2026.2")
        }
        all.sortedDescending()
    }

    override fun listAvailableCalendarSemesters(): List<String> = synchronized(lock) {
        (calendarEventsBySemester.keys +
            calendarEventsSeed.map { it.semester } +
            listOf(studentProfile.activeSemester.trim().ifBlank { "2026.2" }))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
    }

    override fun listFriendSchedules(): List<FriendScheduleInfo> = synchronized(lock) {
        friendSchedules.map { toFriendScheduleInfoLocked(it) }
    }

    override fun importFriendScheduleJson(
        friendName: String,
        jsonPayload: String
    ): FriendScheduleInfo = synchronized(lock) {
        val trimmed = jsonPayload.trim()
        require(trimmed.isNotEmpty()) { "Cole o JSON ou selecione um arquivo .ufopgrade." }

        val root = try {
            JSONObject(trimmed)
        } catch (_: Throwable) {
            throw IllegalArgumentException("JSON inválido. Use o arquivo .ufopgrade ou payload exportado pelo app.")
        }
        val jsonFriendName = root.optNonNullString("friendName")
            .ifBlank { root.optNonNullString("friend_name") }
            .ifBlank { root.optNonNullString("name") }
            .trim()
        val cleanName = friendName.trim().ifBlank { jsonFriendName }.ifBlank { "Amigo UFOP" }
        val courseName = root.optNonNullString("courseName")
            .ifBlank { root.optNonNullString("course_name") }
            .trim()
            .let { if (it.isBlank()) "" else normalizeUfopPortugueseText(it) }
        val avatarBase64 = root.optNonNullString("avatarBase64")
            .ifBlank { root.optNonNullString("avatar_base64") }
            .trim()
            .takeIf { it.isNotEmpty() }
        val semester = root.optNonNullString("semester", studentProfile.activeSemester)
            .ifBlank { "2026.2" }

        val parsedCourses = mutableListOf<FriendCourseEntry>()
        val coursesArr = root.optJSONArray("courses")
        if (coursesArr != null) {
            for (i in 0 until coursesArr.length()) {
                val obj = coursesArr.optJSONObject(i) ?: continue
                val id = obj.optNonNullString("courseId")
                    .ifBlank { obj.optNonNullString("id") }
                    .trim()
                val rawName = obj.optNonNullString("name").trim()
                if (id.isEmpty() || rawName.isEmpty()) continue
                val name = normalizeUfopPortugueseText(rawName)
                val deptId = resolveDepartmentOption(
                    obj.optNonNullString("departmentId")
                        .ifBlank { obj.optNonNullString("department_id", "decom") }
                ).id
                val code = deriveCourseCodeFallback(
                    explicitCode = obj.optNonNullString("code"),
                    notes = "",
                    courseId = id,
                    courseName = name
                )
                parsedCourses.add(FriendCourseEntry(courseId = id, name = name, departmentId = deptId, code = code))
            }
        }

        val parsedSlots = mutableListOf<FriendSlotEntry>()
        val slotsArr = root.optJSONArray("slots")
        if (slotsArr != null) {
            for (i in 0 until slotsArr.length()) {
                val obj = slotsArr.optJSONObject(i) ?: continue
                val slotId = obj.optNonNullString("slotId")
                    .ifBlank { obj.optNonNullString("slot_id", "fs_${i + 1}") }
                    .trim()
                val courseId = obj.optNonNullString("courseId")
                    .ifBlank { obj.optNonNullString("course_id") }
                    .trim()
                if (courseId.isEmpty()) continue
                val day = obj.optInt("dayOfWeek", obj.optInt("day_of_week", 1)).coerceIn(1, 7)
                val start = obj.optNonNullString("startTime")
                    .ifBlank { obj.optNonNullString("start_time", "08:00") }
                    .ifBlank { "08:00" }
                val end = obj.optNonNullString("endTime")
                    .ifBlank { obj.optNonNullString("end_time", "09:40") }
                    .ifBlank { "09:40" }
                val shortLoc = obj.optNonNullString("shortLocation")
                    .ifBlank { obj.optNonNullString("short_location") }
                    .ifBlank {
                        val b = obj.optNonNullString("building")
                        val r = obj.optNonNullString("room")
                        buildShortLocation(b, r)
                    }
                parsedSlots.add(
                    FriendSlotEntry(
                        slotId = slotId,
                        courseId = courseId,
                        dayOfWeek = day,
                        startTime = start,
                        endTime = end,
                        shortLocation = shortLoc
                    )
                )
            }
        }

        require(parsedCourses.isNotEmpty() || parsedSlots.isNotEmpty()) {
            "Nenhuma disciplina ou horário encontrado no arquivo .ufopgrade."
        }

        friendSchedules.removeAll {
            it.friendName.equals(cleanName, ignoreCase = true) && it.semester == semester
        }
        generatedIdCounter += 1L
        val entry = FriendScheduleEntry(
            id = "friend_$generatedIdCounter",
            friendName = cleanName,
            courseName = courseName,
            semester = semester,
            avatarBase64 = avatarBase64,
            courses = parsedCourses,
            slots = parsedSlots
        )
        friendSchedules.add(entry)
        persistStateLocked()
        _mutationVersion.value += 1L
        toFriendScheduleInfoLocked(entry)
    }

    override fun removeFriendSchedule(friendId: String): Unit = synchronized(lock) {
        val removed = friendSchedules.removeAll { it.id == friendId }
        if (removed) {
            persistStateLocked()
            _mutationVersion.value += 1L
        }
    }

    override fun getFriendTimetable(friendId: String): WeeklyTimetable? = synchronized(lock) {
        friendSchedules.find { it.id == friendId }?.let { toFriendScheduleInfoLocked(it).timetable }
    }

    private fun toFriendScheduleInfoLocked(entry: FriendScheduleEntry): FriendScheduleInfo {
        val courseById = entry.courses.associateBy { it.courseId }
        val entries = entry.slots.mapNotNull { slot ->
            val fc = courseById[slot.courseId]
            val dept = resolveDepartmentOption(fc?.departmentId ?: "decom")
            val resolvedName = fc?.name ?: slot.courseId
            val resolvedCode = deriveCourseCodeFallback(fc?.code, "", slot.courseId, resolvedName)
            TimetableEntry(
                dayOfWeek = slot.dayOfWeek,
                startTime = slot.startTime,
                endTime = slot.endTime,
                cell = TimetableCell(
                    slotId = slot.slotId,
                    courseId = slot.courseId,
                    courseName = resolvedName,
                    shortLocation = slot.shortLocation.ifBlank { "UFOP" },
                    departmentId = dept.id,
                    departmentName = dept.name,
                    colorHex = dept.colorHex,
                    code = resolvedCode
                )
            )
        }
        return FriendScheduleInfo(
            id = entry.id,
            friendName = entry.friendName,
            courseName = entry.courseName,
            semester = entry.semester,
            avatarBase64 = entry.avatarBase64,
            timetable = buildTimetable(
                semester = entry.semester,
                gridTitle = "Grade de ${entry.friendName} · ${entry.semester}",
                entries = entries
            )
        )
    }

    // ── Sprint 6: Clima Leve Online (Open-Meteo, Throttle >= 5 min) ──────────────────────────────

    override fun refreshWeatherIfNeeded(force: Boolean): UfopCampusInfo {
        val targetCampus = synchronized(lock) {
            val current = activeCampusLocked()
            val now = System.currentTimeMillis()
            val hasCachedCampus = current.id in campusWeatherById
            val withinCacheWindow = lastWeatherFetchEpochMs > 0L &&
                (now - lastWeatherFetchEpochMs) < WEATHER_REFRESH_INTERVAL_MS &&
                hasCachedCampus
            val withinFailureCooldown = !force &&
                lastWeatherAttemptEpochMs > 0L &&
                (now - lastWeatherAttemptEpochMs) < 15_000L

            if (weatherFetchInFlight || (!force && (withinCacheWindow || withinFailureCooldown))) {
                return current
            }
            weatherFetchInFlight = true
            lastWeatherAttemptEpochMs = now
            current
        }

        if (isMainThread()) {
            Thread(
                {
                    try {
                        performWeatherFetch(targetCampus.id)
                    } finally {
                        weatherFetchInFlight = false
                    }
                },
                "ufop-weather-refresh"
            ).apply {
                isDaemon = true
                start()
            }
            return synchronized(lock) { activeCampusLocked() }
        }

        return try {
            performWeatherFetch(targetCampus.id)
            synchronized(lock) { activeCampusLocked() }
        } finally {
            weatherFetchInFlight = false
        }
    }

    private fun performWeatherFetch(campusId: String) {
        val (lat, lon) = when (campusId) {
            "mar_ichs" -> -20.3778 to -43.4161
            "jm_icea" -> -19.8100 to -43.1736
            else -> -20.3855 to -43.5036
        }
        val endpoint = "https://api.open-meteo.com/v1/forecast" +
            "?latitude=$lat&longitude=$lon&current=temperature_2m,weather_code&forecast_days=1"

        var conn: HttpURLConnection? = null
        try {
            conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = WEATHER_TIMEOUT_MS
                readTimeout = WEATHER_TIMEOUT_MS
                setRequestProperty("Accept", "application/json")
                setRequestProperty("User-Agent", "CampusUFOP/1.0")
            }
            if (conn.responseCode !in 200..299) return
            val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }
            val current = JSONObject(body).optJSONObject("current") ?: return
            if (!current.has("temperature_2m")) return
            val tempC = current.getDouble("temperature_2m").roundToInt()
            val condition = mapWmoWeatherCode(current.optInt("weather_code", 0))

            synchronized(lock) {
                campusWeatherById[campusId] = CampusWeather(tempC = tempC, condition = condition)
                lastWeatherFetchEpochMs = System.currentTimeMillis()
                persistStateLocked()
                _mutationVersion.value += 1L
            }
            onMutation?.invoke()
        } catch (_: Throwable) {
            // Falha silenciosa offline/timeout: mantém temperatura em cache.
        } finally {
            conn?.disconnect()
        }
    }

    private fun mapWmoWeatherCode(code: Int): String = when (code) {
        0 -> "Ensolarado"
        1, 2 -> "Parcialmente nublado"
        3 -> "Nublado"
        45, 48 -> "Neblina"
        51, 53, 55, 56, 57 -> "Garoa"
        61, 63, 65, 66, 67, 80, 81, 82 -> "Chuvoso"
        71, 73, 75, 77, 85, 86 -> "Neve"
        95, 96, 99 -> "Tempestade"
        else -> "Parcialmente nublado"
    }

    private fun isMainThread(): Boolean = try {
        android.os.Looper.myLooper() == android.os.Looper.getMainLooper()
    } catch (_: Throwable) {
        false
    }

    // ── Sprint 6: Parser do ATESTADO DE MATRÍCULA UFOP (PDF com Coordenadas + Texto) ─────────────


    override fun importEnrollmentCertificatePdf(pdfBytes: ByteArray, completeOnboarding: Boolean): Int {
        require(pdfBytes.isNotEmpty()) { "Arquivo PDF vazio." }
        return SecurePdfProcessor.withZeroedPdfBytes(pdfBytes) { safeBytes ->
            val (lines, words) = extractPdfLinesAndWords(safeBytes)
            importCertificateFromLinesAndWords(lines, words, completeOnboarding)
        }
    }

    override fun importEnrollmentCertificateText(rawText: String, completeOnboarding: Boolean): Int {
        val trimmed = rawText.trim()
        require(trimmed.isNotEmpty()) { "Texto do atestado vazio." }
        val lines = trimmed.lines().map { it.trimEnd() }.filter { it.isNotBlank() }
        return importCertificateFromLinesAndWords(lines, emptyList(), completeOnboarding)
    }

    private fun importCertificateFromLinesAndWords(
        lines: List<String>,
        words: List<PdfWord>,
        completeOnboarding: Boolean = true
    ): Int {
        val meta = parseCertificateMeta(lines)
        val tableRows = parseCourseTable(lines)
        val gridTokens = if (words.isNotEmpty()) {
            parseGridTokensByCoordinates(words).ifEmpty { parseGridTokensFromText(lines) }
        } else {
            parseGridTokensFromText(lines)
        }

        val effectiveRows = if (tableRows.isNotEmpty()) {
            tableRows
        } else {
            // Sintetiza disciplinas caso apenas os tokens da grade tenham sido extraídos.
            gridTokens.distinctBy { it.courseCode }.map { token ->
                ParsedCourseTableRow(
                    code = token.courseCode,
                    turma = token.turma,
                    rawTitle = token.courseCode,
                    building = "Campus UFOP",
                    language = "PORTUGUES"
                )
            }
        }

        require(effectiveRows.isNotEmpty()) {
            "Não foi possível identificar disciplinas neste atestado de matrícula da UFOP."
        }

        return synchronized(lock) {
            applyImportedCertificateLocked(meta, effectiveRows, gridTokens, completeOnboarding)
        }
    }

    private fun applyImportedCertificateLocked(
        meta: ParsedCertificateMeta,
        tableRows: List<ParsedCourseTableRow>,
        gridTokens: List<ParsedGridToken>,
        completeOnboarding: Boolean = true
    ): Int {
        val semester = meta.semester.ifBlank { "2026.2" }

        // Remove apenas disciplinas e horários do semestre sendo reimportado (preserva semestres anteriores).
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

        if (meta.campusId != null) {
            activeCampusId = meta.campusId
        }
        val activeCampus = activeCampusLocked()

        val tokensByCourse = gridTokens.groupBy { it.courseCode }
        val semesterSlug = semester.replace(".", "_")

        for (row in tableRows) {
            val courseTokens = tokensByCourse[row.code].orEmpty()
            val weeklySlotsCount = courseTokens.size
            val workloadHours = if (weeklySlotsCount <= 0) {
                60
            } else {
                (((weeklySlotsCount * 15) + 7) / 15 * 15).coerceIn(15, 180)
            }
            val maxAbsences = (workloadHours * 3) / 10
            val dept = resolveDepartmentForCourseCode(row.code)
            val prettyName = normalizeUfopPortugueseText(row.rawTitle)
            val isEadCourse = row.isEad || courseTokens.any { it.hasEadMarker }
            val prettyBuilding = if (isEadCourse) {
                "Ensino a Distância (EAD)"
            } else {
                prettyBuildingName(row.building)
            }
            val shortLoc = if (isEadCourse) {
                "EAD"
            } else {
                buildCertificateShortLocation(prettyBuilding)
            }
            val courseId = "${row.code.lowercase()}_$semesterSlug"
            val knownTranscriptProfessor = transcriptEntries.firstOrNull {
                it.semester == semester && it.code.equals(row.code, ignoreCase = true) && it.professor.isNotBlank()
            }?.professor?.trim()
            val professorLabel = knownTranscriptProfessor.orEmpty()

            // Agrupa horários consecutivos de 50 min da mesma disciplina e mesmo tipo (T/P) no mesmo dia.
            val mergedSlots = mergeConsecutiveGridTokens(courseTokens)
            val activeAllocIndex = roomAllocationIndex?.takeIf { it.semester == semester }
            val createdCourseSlots = mutableListOf<SlotSeed>()

            mergedSlots.forEachIndexed { idx, merged ->
                val slotId = "slot_${courseId}_d${merged.dayOfWeek}_${merged.startTime.replace(":", "")}_${idx + 1}"
                val slotBuilding: String
                val slotRoom: String
                val slotShortLoc: String
                if (isEadCourse) {
                    slotBuilding = "Ensino a Distância (EAD)"
                    slotRoom = ""
                    slotShortLoc = "EAD"
                } else {
                    val match = activeAllocIndex?.lookup(
                        courseCode = row.code,
                        turma = row.turma,
                        dayOfWeek = merged.dayOfWeek,
                        startTime = merged.startTime,
                        kind = merged.kind
                    )
                    if (match != null) {
                        slotBuilding = match.building
                        slotRoom = match.room
                        slotShortLoc = buildRoomShortLocation(match.building, match.room)
                    } else {
                        slotBuilding = prettyBuilding
                        slotRoom = ""
                        slotShortLoc = buildCertificateShortLocation(prettyBuilding)
                    }
                }
                val newSlot = SlotSeed(
                    slotId = slotId,
                    courseId = courseId,
                    dayOfWeek = merged.dayOfWeek,
                    startTime = merged.startTime,
                    endTime = merged.endTime,
                    building = slotBuilding,
                    room = slotRoom,
                    shortLocation = slotShortLoc,
                    defaultTempC = activeCampus.weatherTempC,
                    weatherCondition = activeCampus.weatherCondition,
                    classWeight = merged.weight
                )
                createdCourseSlots.add(newSlot)
                slots.add(newSlot)
            }

            val buildingAndRoomLabel = if (isEadCourse) {
                "Ensino a Distância (EAD)"
            } else {
                buildCleanCourseBuildingAndRoom(prettyBuilding, createdCourseSlots)
            }
            val resolvedCourseShortLoc = if (isEadCourse) {
                "EAD"
            } else {
                createdCourseSlots.firstOrNull { it.room.isNotBlank() }?.shortLocation ?: shortLoc
            }

            courses[courseId] = CourseSeed(
                courseId = courseId,
                semester = semester,
                name = prettyName,
                departmentId = dept.id,
                departmentName = dept.name,
                defaultColorHex = dept.colorHex,
                workloadHours = workloadHours,
                baseAbsences = 0,
                maxAbsences = maxAbsences,
                baseCancelledClasses = 0,
                professor = professorLabel,
                buildingAndRoom = buildingAndRoomLabel,
                shortLocation = resolvedCourseShortLoc,
                assessments = emptyList(),
                initialNotes = "Código: ${row.code} · Turma ${row.turma} · ${normalizeUfopPortugueseText(row.language)}",
                code = row.code.uppercase()
            )
            notesByCourseId[courseId] = courses[courseId]?.initialNotes.orEmpty()
            assessmentsByCourseId[courseId] = mutableListOf()
        }

        val defaultBounds = resolveDefaultSemesterBounds(semester)
        studentProfile = studentProfile.copy(
            fullName = meta.fullName ?: studentProfile.fullName,
            registrationNumber = meta.registrationNumber ?: studentProfile.registrationNumber,
            courseName = meta.courseName ?: studentProfile.courseName,
            campusName = meta.campusName ?: activeCampus.city,
            activeSemester = semester,
            semesterStartDate = meta.semesterStartDate ?: defaultBounds.first,
            semesterEndDate = meta.semesterEndDate ?: defaultBounds.second,
            onboardingCompleted = if (completeOnboarding) true else studentProfile.onboardingCompleted
        )

        persistStateLocked()
        _mutationVersion.value += 1L
        return tableRows.size
    }


    override fun applyRoomAllocationIndex(
        index: UfopRoomAllocationIndex,
        enrichExisting: Boolean
    ): Int = synchronized(lock) {
        roomAllocationIndex = index
        if (!enrichExisting) {
            return@synchronized 0
        }
        val updatedCount = enrichExistingRoomAllocationsLocked(index)
        if (updatedCount > 0) {
            persistStateLocked()
            _mutationVersion.value += 1L
        }
        updatedCount
    }

    private fun enrichExistingRoomAllocationsLocked(index: UfopRoomAllocationIndex): Int {
        var updatedCount = 0
        val touchedCourseIds = mutableSetOf<String>()

        for (i in slots.indices) {
            val slot = slots[i]
            val course = courses[slot.courseId] ?: continue
            val isEad = isEadIndicatorText(slot.building) ||
                slot.building.contains("Ensino a Distância", ignoreCase = true) ||
                slot.building.contains("Ensino a Distancia", ignoreCase = true) ||
                isEadIndicatorText(course.buildingAndRoom) ||
                course.buildingAndRoom.contains("Ensino a Distância", ignoreCase = true)
            if (isEad) {
                val rLower = slot.room.trim().lowercase()
                if (rLower in GENERIC_REPLACEABLE_ROOMS && (slot.room.isNotEmpty() || slot.shortLocation != "EAD")) {
                    slots[i] = slot.copy(room = "", shortLocation = "EAD")
                    updatedCount += 1
                    touchedCourseIds.add(course.courseId)
                }
                continue
            }

            val roomLower = slot.room.trim().lowercase()
            if (roomLower !in GENERIC_REPLACEABLE_ROOMS) {
                continue
            }

            val courseCode = resolveCourseCode(course)
            val notes = notesByCourseId[course.courseId] ?: course.initialNotes
            val turma = NOTES_TURMA_REGEX.find(notes)?.groupValues?.getOrNull(1)?.trim()
            val kindHint = when (roomLower) {
                "aula prática", "aula pratica" -> "P"
                "aula teórica", "aula teorica" -> "T"
                else -> null
            }

            val match = if (course.semester == index.semester) {
                index.lookup(
                    courseCode = courseCode,
                    turma = turma,
                    dayOfWeek = slot.dayOfWeek,
                    startTime = slot.startTime,
                    kind = kindHint
                )
            } else {
                null
            }

            if (match != null) {
                val newBuilding = match.building
                val newRoom = match.room
                val newShort = buildRoomShortLocation(newBuilding, newRoom)
                if (slot.building != newBuilding || slot.room != newRoom || slot.shortLocation != newShort) {
                    slots[i] = slot.copy(
                        building = newBuilding,
                        room = newRoom,
                        shortLocation = newShort
                    )
                    updatedCount += 1
                }
                touchedCourseIds.add(course.courseId)
            } else {
                val cleanBuilding = slot.building.substringBefore("·").trim().ifBlank {
                    course.buildingAndRoom.substringBefore("·").trim().ifBlank { "Campus UFOP" }
                }
                val prettyB = prettyBuildingName(cleanBuilding)
                val newShort = buildCertificateShortLocation(prettyB)
                val cleanedSlot = slot.copy(
                    building = prettyB,
                    room = "",
                    shortLocation = newShort
                )
                if (cleanedSlot != slot) {
                    slots[i] = cleanedSlot
                    updatedCount += 1
                }
                touchedCourseIds.add(course.courseId)
            }
        }

        for ((courseId, course) in courses.entries.toList()) {
            val hasGenericCourseSuffix = course.buildingAndRoom.contains("·") &&
                course.buildingAndRoom.substringAfter("·").trim().lowercase() in GENERIC_REPLACEABLE_ROOMS
            if (courseId !in touchedCourseIds && !hasGenericCourseSuffix) {
                continue
            }
            val courseSlots = slots.filter { it.courseId == courseId }
            val isEad = isEadIndicatorText(course.buildingAndRoom) ||
                course.buildingAndRoom.contains("Ensino a Distância", ignoreCase = true)
            if (isEad) {
                if (course.buildingAndRoom != "Ensino a Distância (EAD)" || course.shortLocation != "EAD") {
                    courses[courseId] = course.copy(
                        buildingAndRoom = "Ensino a Distância (EAD)",
                        shortLocation = "EAD"
                    )
                }
                continue
            }
            val baseBuilding = prettyBuildingName(
                course.buildingAndRoom.substringBefore("·").trim().ifBlank {
                    courseSlots.firstOrNull()?.building?.trim().orEmpty().ifBlank { "Campus UFOP" }
                }
            )
            val newBuildingAndRoom = buildCleanCourseBuildingAndRoom(baseBuilding, courseSlots)
            val newShortLocation = courseSlots.firstOrNull { it.room.isNotBlank() }?.shortLocation
                ?: buildCertificateShortLocation(baseBuilding)
            if (course.buildingAndRoom != newBuildingAndRoom || course.shortLocation != newShortLocation) {
                courses[courseId] = course.copy(
                    buildingAndRoom = newBuildingAndRoom,
                    shortLocation = newShortLocation
                )
            }
        }

        return updatedCount
    }

    // ── Sprint 7/9: Importador de Plano de Ensino (PDF/Texto) ────────────────────────────────────

    override fun importCourseSyllabusPdf(courseId: String, pdfBytes: ByteArray): Int {
        require(pdfBytes.isNotEmpty()) { "Arquivo PDF vazio." }
        return SecurePdfProcessor.withZeroedPdfBytes(pdfBytes) { safeBytes ->
            val rawText = extractPdfTextWithPdfBox(safeBytes)
            importCourseSyllabusText(courseId, rawText)
        }
    }

    override fun importCourseSyllabusText(courseId: String, rawText: String): Int {
        val trimmed = rawText.trim()
        require(trimmed.isNotEmpty()) { "Texto do plano de ensino vazio." }

        return synchronized(lock) {
            val course = courses[courseId]
                ?: throw IllegalArgumentException("Disciplina não encontrada: $courseId")
            val (importedCount, _) = importSyllabusAssessmentsLocked(course, trimmed)
            if (importedCount > 0) {
                persistStateLocked()
                _mutationVersion.value += 1L
            }
            importedCount
        }
    }

    override fun importCourseSyllabusDetailedPdf(
        courseId: String,
        pdfBytes: ByteArray
    ): SyllabusImportReport {
        require(pdfBytes.isNotEmpty()) { "Arquivo PDF vazio." }
        return SecurePdfProcessor.withZeroedPdfBytes(pdfBytes) { safeBytes ->
            val rawText = extractPdfTextWithPdfBox(safeBytes)
            importCourseSyllabusDetailedText(courseId, rawText)
        }
    }

    override fun importCourseSyllabusDetailedText(
        courseId: String,
        rawText: String
    ): SyllabusImportReport {
        val trimmed = rawText.trim()
        require(trimmed.isNotEmpty()) { "Texto do plano de ensino vazio." }

        return synchronized(lock) {
            val initialCourse = courses[courseId]
                ?: throw IllegalArgumentException("Disciplina não encontrada: $courseId")

            val matchResult = matchSyllabusToCourse(initialCourse, trimmed, notesByCourseId)
            if (!matchResult.matched) {
                val detectedLabel = listOfNotNull(
                    matchResult.detectedCode,
                    matchResult.detectedTitle
                ).joinToString(" · ").ifBlank { "outra disciplina" }
                return@synchronized SyllabusImportReport(
                    importedCount = 0,
                    matchedCourse = false,
                    matchedCourseCode = matchResult.detectedCode,
                    matchedCourseTitle = matchResult.detectedTitle,
                    extractedProfessor = "",
                    hasGradeDistribution = false,
                    hasAssessmentDates = false,
                    warningMessage = null,
                    errorMessage = "Este PDF parece ser de outra disciplina ($detectedLabel) e não de ${initialCourse.name}. Nenhuma alteração foi feita."
                )
            }

            val extractedProfessor = extractProfessorFromSyllabus(trimmed)
            var currentCourse = initialCourse
            var professorUpdated = false
            if (extractedProfessor.isNotBlank() && extractedProfessor != initialCourse.professor) {
                currentCourse = initialCourse.copy(professor = extractedProfessor)
                courses[courseId] = currentCourse
                professorUpdated = true
            }

            val hasGradeDistribution = detectGradeDistributionInSyllabus(trimmed)
            val (importedCount, hasDatedAssessmentTokens) = importSyllabusAssessmentsLocked(currentCourse, trimmed)
            val existingCountAfter = assessmentsByCourseId[courseId]?.size ?: 0
            val hasAssessmentDates = importedCount > 0 || existingCountAfter > 0 || hasDatedAssessmentTokens

            val warningMessage = when {
                !hasGradeDistribution && !hasAssessmentDates ->
                    "Aviso: apenas a disciplina e o professor foram identificados neste plano (sem divisão de notas e sem datas de avaliações no semestre). Você precisará inserir as datas manualmente mais tarde."
                !hasGradeDistribution ->
                    "Aviso: foram encontradas datas, mas o plano não detalha a divisão de notas do semestre. Confira os pesos/datas manualmente mais tarde."
                !hasAssessmentDates ->
                    "Aviso: apenas a identificação da disciplina/professor (e critérios) foi achada — nenhuma data específica de avaliação constava no plano. Adicione as datas manualmente mais tarde."
                else -> null
            }

            if (professorUpdated || importedCount > 0) {
                persistStateLocked()
                _mutationVersion.value += 1L
            }

            SyllabusImportReport(
                importedCount = importedCount,
                matchedCourse = true,
                matchedCourseCode = matchResult.detectedCode ?: extractRealUfopCourseCode(currentCourse.code),
                matchedCourseTitle = matchResult.detectedTitle ?: currentCourse.name,
                extractedProfessor = extractedProfessor,
                hasGradeDistribution = hasGradeDistribution,
                hasAssessmentDates = hasAssessmentDates,
                warningMessage = warningMessage,
                errorMessage = null
            )
        }
    }

    private fun importSyllabusAssessmentsLocked(
        course: CourseSeed,
        trimmedText: String
    ): Pair<Int, Boolean> {
        val courseId = course.courseId
        val defaultYear = resolveSemesterDefaultYear(course.semester)

        val segments = trimmedText.lines()
            .flatMap { rawLine -> splitSyllabusLineIntoSegments(rawLine, defaultYear) }
            .filter { it.isNotEmpty() }

        val existingList = assessmentsByCourseId.getOrPut(courseId) { mutableListOf() }
        val seenKeys = existingList.map { it.dueDate to normalizeDedupKey(it.title) }.toMutableSet()
        var importedCount = 0
        var hasDatedAssessmentTokens = false
        val consumedIndices = mutableSetOf<Int>()

        fun tryAddAssessment(isoDate: String, rawTitle: String, keywordSource: String, dueTime: String?) {
            val normKeySource = normalizeSearchText(keywordSource)
            if (!syllabusEvalRegex.containsMatchIn(normKeySource)) return
            if (isSyllabusNoiseOrProse(keywordSource, rawTitle)) return

            val (formattedTitle, kind) = formatSyllabusAssessmentTitleAndKind(
                rawTitle = rawTitle,
                keywordSource = keywordSource,
                course = course
            ) ?: return

            if (formattedTitle.isBlank() || formattedTitle.length > 64) return
            hasDatedAssessmentTokens = true
            val dedupKey = isoDate to normalizeDedupKey(formattedTitle)
            if (!seenKeys.add(dedupKey)) return

            val (normalizedIso, dayNum, monthShort) = parseDueDateParts(isoDate)
            generatedIdCounter += 1L
            existingList.add(
                AssessmentItem(
                    id = "assess_${courseId}_$generatedIdCounter",
                    courseId = courseId,
                    dayNumber = dayNum,
                    monthShort = monthShort,
                    title = formattedTitle,
                    kind = kind,
                    dueTime = dueTime ?: "18:00",
                    dueDate = normalizedIso,
                    completed = false
                )
            )
            importedCount += 1
        }

        for (i in segments.indices) {
            if (i in consumedIndices) continue
            val line = segments[i]
            val parsed = extractDateFromSegment(line, defaultYear, fallbackMonth = null)

            if (parsed != null && !parsed.isBareDate) {
                tryAddAssessment(parsed.isoDate, parsed.remainingTitle, line, parsed.extractedTime)
                consumedIndices.add(i)
            } else if (parsed != null && parsed.isBareDate) {
                val nextLine = segments.getOrNull(i + 1)
                val prevLine = segments.getOrNull(i - 1)
                when {
                    nextLine != null && (i + 1) !in consumedIndices &&
                        extractDateFromSegment(nextLine, defaultYear, null) == null &&
                        syllabusEvalRegex.containsMatchIn(normalizeSearchText(nextLine)) -> {
                        tryAddAssessment(parsed.isoDate, cleanExtractedTitle(nextLine), nextLine, parsed.extractedTime)
                        consumedIndices.add(i)
                        consumedIndices.add(i + 1)
                    }
                    prevLine != null && (i - 1) !in consumedIndices &&
                        extractDateFromSegment(prevLine, defaultYear, null) == null &&
                        syllabusEvalRegex.containsMatchIn(normalizeSearchText(prevLine)) -> {
                        tryAddAssessment(parsed.isoDate, cleanExtractedTitle(prevLine), prevLine, parsed.extractedTime)
                        consumedIndices.add(i)
                        consumedIndices.add(i - 1)
                    }
                }
            } else if (parsed == null && syllabusEvalRegex.containsMatchIn(normalizeSearchText(line))) {
                val nextLine = segments.getOrNull(i + 1)
                val nextParsed = nextLine?.let { extractDateFromSegment(it, defaultYear, null) }
                if (nextParsed != null && nextParsed.isBareDate && (i + 1) !in consumedIndices) {
                    tryAddAssessment(nextParsed.isoDate, cleanExtractedTitle(line), line, nextParsed.extractedTime)
                    consumedIndices.add(i)
                    consumedIndices.add(i + 1)
                }
            }
        }

        return importedCount to hasDatedAssessmentTokens
    }

    // ── Sprint 7: Calendário Acadêmico Assinado (Admin SHA-256 & Sincronização) ───────────────────

    override fun isAdminUnlocked(): Boolean = synchronized(lock) { adminUnlocked }

    override fun unlockAdminMode(pin: String): Boolean = synchronized(lock) {
        val cleanPin = pin.trim()
        val ok = cleanPin == "UFOP2026" || cleanPin == "2421165"
        if (ok && !adminUnlocked) {
            adminUnlocked = true
            persistStateLocked()
            _mutationVersion.value += 1L
        }
        ok
    }

    override fun adminPublishCalendarPdf(pdfBytes: ByteArray, semester: String): AcademicCalendarUpdatePackage {
        require(pdfBytes.isNotEmpty()) { "Arquivo PDF vazio." }
        val rawText = SecurePdfProcessor.withZeroedPdfBytes(pdfBytes) { safeBytes ->
            extractPdfTextWithPdfBox(safeBytes)
        }
        return synchronized(lock) {
            if (!adminUnlocked) {
                throw IllegalStateException("Modo administrador bloqueado. Informe o PIN de administração antes de publicar.")
            }
            val cleanSemester = semester.trim().ifBlank { studentProfile.activeSemester.ifBlank { "2026.2" } }
            val defaultYear = resolveSemesterDefaultYear(cleanSemester)
            val parsedEvents = parseOfficialCalendarEvents(rawText, cleanSemester, defaultYear)
            require(parsedEvents.isNotEmpty()) {
                "Nenhum feriado, recesso ou prazo acadêmico identificado no PDF."
            }

            generatedIdCounter += 1L
            val version = "$cleanSemester-v$generatedIdCounter"
            val publishedBy = studentProfile.fullName.trim()
                .takeIf { it.isNotEmpty() }
                ?.let { "Admin Local · $it" }
                ?: "Admin Local"
            val publishedAt = Instant.now().toString()
            val signature = computeCalendarSignatureSha256(
                version = version,
                semester = cleanSemester,
                publishedBy = publishedBy,
                publishedAt = publishedAt,
                events = parsedEvents
            )
            val pkg = AcademicCalendarUpdatePackage(
                version = version,
                semester = cleanSemester,
                publishedBy = publishedBy,
                publishedAt = publishedAt,
                signatureSha256 = signature,
                events = parsedEvents
            )
            pendingCalendarUpdate = pkg
            latestPublishedCalendarUpdate = pkg
            exportCalendarUpdateJsonLocked()
            persistStateLocked()
            _mutationVersion.value += 1L
            pkg
        }
    }

    override fun exportCalendarUpdateJson(): String = synchronized(lock) {
        exportCalendarUpdateJsonLocked()
    }

    private fun exportCalendarUpdateJsonLocked(): String {
        val pkg = pendingCalendarUpdate ?: latestPublishedCalendarUpdate ?: run {
            val cleanSemester = studentProfile.activeSemester.ifBlank { "2026.2" }
            val activeEvents = getAcademicCalendarEventsLocked(cleanSemester)
            val version = "$cleanSemester-v1"
            val publishedBy = "Catálogo do App"
            val publishedAt = Instant.now().toString()
            val signature = computeCalendarSignatureSha256(
                version = version,
                semester = cleanSemester,
                publishedBy = publishedBy,
                publishedAt = publishedAt,
                events = activeEvents
            )
            AcademicCalendarUpdatePackage(
                version = version,
                semester = cleanSemester,
                publishedBy = publishedBy,
                publishedAt = publishedAt,
                signatureSha256 = signature,
                events = activeEvents
            )
        }
        return calendarPackageToJson(pkg).toString(2)
    }

    override fun importCalendarUpdateJson(jsonPayload: String): AcademicCalendarUpdatePackage = synchronized(lock) {
        val trimmed = jsonPayload.trim()
        require(trimmed.isNotEmpty()) { "Pacote de calendário vazio." }
        val root = try {
            JSONObject(trimmed)
        } catch (_: Throwable) {
            throw IllegalArgumentException("Arquivo de calendário JSON inválido.")
        }
        val pkg = parseCalendarPackageFromJson(root)
            ?: throw IllegalArgumentException("Pacote de calendário incompleto ou sem eventos.")

        require(pkg.signatureSha256.length == 64 && pkg.signatureSha256.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }) {
            "Assinatura SHA-256 ausente ou malformada."
        }
        val expectedSignature = computeCalendarSignatureSha256(
            version = pkg.version,
            semester = pkg.semester,
            publishedBy = pkg.publishedBy,
            publishedAt = pkg.publishedAt,
            events = pkg.events
        )
        if (!pkg.signatureSha256.equals(expectedSignature, ignoreCase = true)) {
            throw IllegalArgumentException(
                "Assinatura SHA-256 inválida: o pacote de calendário foi adulterado ou corrompido."
            )
        }

        pendingCalendarUpdate = pkg
        latestPublishedCalendarUpdate = pkg
        persistStateLocked()
        _mutationVersion.value += 1L
        pkg
    }

    override fun getPendingCalendarUpdate(): AcademicCalendarUpdatePackage? = synchronized(lock) {
        pendingCalendarUpdate
    }

    override fun applyPendingCalendarUpdate(): Int = synchronized(lock) {
        val pending = pendingCalendarUpdate ?: return@synchronized 0
        val cleanSemester = pending.semester.trim().ifBlank { studentProfile.activeSemester.ifBlank { "2026.2" } }
        calendarEventsBySemester[cleanSemester] = pending.events.sortedBy { it.eventDate }.toMutableList()
        pendingCalendarUpdate = null
        persistStateLocked()
        _mutationVersion.value += 1L
        pending.events.size
    }

    private fun resolveSemesterDefaultYear(semester: String): Int {
        val fromSem = Regex("""(\d{4})""").find(semester)?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (fromSem != null && fromSem in 1990..2100) return fromSem
        val fromActive = Regex("""(\d{4})""").find(studentProfile.activeSemester)?.groupValues?.getOrNull(1)?.toIntOrNull()
        if (fromActive != null && fromActive in 1990..2100) return fromActive
        return LocalDate.now().year
    }

    override fun getAcademicCreReport(): AcademicCreReport = synchronized(lock) {
        getAcademicCreReportLocked()
    }

    override fun updateTranscriptOrCourseGrade(
        semester: String,
        codeOrCourseId: String,
        grade: Double?,
        absences: Int?
    ): AcademicCreReport = synchronized(lock) {
        val cleanSemester = semester.trim().ifBlank { studentProfile.activeSemester.ifBlank { "2026.2" } }
        val cleanTarget = codeOrCourseId.trim()
        require(cleanTarget.isNotEmpty()) { "Informe o código ou ID da disciplina." }
        if (grade != null) {
            require(grade.isFinite()) { "Nota inválida." }
        }
        val clampedGrade = grade?.coerceIn(0.0, 10.0)

        val txIdx = transcriptEntries.indexOfFirst { entry ->
            entry.semester == cleanSemester && (
                entry.id.equals(cleanTarget, ignoreCase = true) ||
                    entry.code.equals(cleanTarget, ignoreCase = true)
                )
        }
        val txEntry = if (txIdx >= 0) transcriptEntries[txIdx] else null

        val matchedCourse = courses.values.firstOrNull { c ->
            c.semester == cleanSemester && (
                c.courseId.equals(cleanTarget, ignoreCase = true) ||
                    resolveCourseCode(c).equals(cleanTarget, ignoreCase = true) ||
                    (txEntry != null && (
                        resolveCourseCode(c).equals(txEntry.code, ignoreCase = true) ||
                            normalizeDedupKey(c.name) == normalizeDedupKey(txEntry.name)
                        ))
                )
        }

        if (absences != null) {
            val safeAbsences = absences.coerceAtLeast(0)
            if (matchedCourse != null) {
                val extraAbsences = computeExtraCheckInAbsencesLocked(matchedCourse.courseId)
                courses[matchedCourse.courseId] = matchedCourse.copy(
                    baseAbsences = safeAbsences - extraAbsences
                )
            }
        }

        if (txIdx >= 0 && txEntry != null) {
            transcriptEntries[txIdx] = txEntry.copy(
                grade = clampedGrade,
                absences = absences?.coerceAtLeast(0) ?: txEntry.absences
            )
        } else if (matchedCourse != null) {
            val resolvedAbsences = absences?.coerceAtLeast(0)
                ?: buildDisciplineSummaryLocked(matchedCourse).currentAbsences
            transcriptEntries.add(
                TranscriptCourseEntry(
                    id = matchedCourse.courseId,
                    semester = matchedCourse.semester,
                    code = resolveCourseCode(matchedCourse),
                    name = matchedCourse.name,
                    workloadHours = matchedCourse.workloadHours,
                    grade = clampedGrade,
                    absences = resolvedAbsences,
                    totalClasses = (matchedCourse.workloadHours * 12) / 10,
                    status = "CS",
                    professor = matchedCourse.professor
                )
            )
        } else {
            val semSlug = cleanSemester.replace(".", "_")
            transcriptEntries.add(
                TranscriptCourseEntry(
                    id = "tx_${semSlug}_${cleanTarget.lowercase()}",
                    semester = cleanSemester,
                    code = cleanTarget.uppercase(),
                    name = cleanTarget.uppercase(),
                    workloadHours = 60,
                    grade = clampedGrade,
                    absences = absences?.coerceAtLeast(0) ?: 0,
                    totalClasses = 72,
                    status = "CS",
                    professor = ""
                )
            )
        }

        persistStateLocked()
        _mutationVersion.value += 1L
        getAcademicCreReportLocked()
    }

    override fun importAcademicTranscriptPdf(pdfBytes: ByteArray): AcademicCreReport {
        require(pdfBytes.isNotEmpty()) { "Arquivo PDF vazio." }
        val rawText = SecurePdfProcessor.withZeroedPdfBytes(pdfBytes) { safeBytes ->
            extractPdfTextWithPdfBox(safeBytes)
        }
        return importAcademicTranscriptText(rawText)
    }

    override fun importAcademicTranscriptText(rawText: String): AcademicCreReport {
        val trimmed = rawText.trim()
        require(trimmed.isNotEmpty()) { "Texto do Histórico Escolar vazio." }

        val lines = trimmed.lines()
            .map { it.trim().replace(Regex("""\s+"""), " ") }
            .filter { it.isNotEmpty() }
        val meta = parseTranscriptMetadata(lines)

        val rowRegex = Regex(
            """^(\d{4})[/.-]([1234])\s+([A-Z]{2,4}\d{3,4}[A-Z]?)\s*-\s*(.+?)\s+(\d+)/\d+(?:\s+(?:(\d+(?:[.,]\d+)?)(?!/)|--|-))?(?:\s+(\d+)/(\d+))?\s+(AP|RN|RF|NF|CS|TR|DP|AE|DE|DJ|EA|PF|X)\b""",
            RegexOption.IGNORE_CASE
        )
        val profRowRegex = Regex(
            """^(\d{4})[/.-]([1234])\s+([A-Z]{2,4}\d{3,4}[A-Z]?)\s+(.+?)$""",
            RegexOption.IGNORE_CASE
        )
        val pendingRowPrefixRegex = Regex(
            """^\d{4}[/.-][1234]\s+[A-Z]{2,4}\d{3,4}[A-Z]?\s*-.*""",
            RegexOption.IGNORE_CASE
        )

        val parsedRows = mutableListOf<TranscriptCourseEntry>()
        val professorsBySemAndCode = mutableMapOf<Pair<String, String>, String>()
        val seenIds = mutableSetOf<String>()
        var inProfessorsSection = false
        var idx = 0

        while (idx < lines.size) {
            val line = lines[idx]
            val normLine = normalizeSearchText(line)
            if (normLine.contains("professores ministrantes")) {
                inProfessorsSection = true
                idx += 1
                continue
            }
            if (inProfessorsSection && (
                    normLine.startsWith("abreviaturas usadas") ||
                        normLine.startsWith("situacao na disciplina") ||
                        normLine.startsWith("criterio de avaliacao")
                    )
            ) {
                inProfessorsSection = false
            }

            var rowMatch = rowRegex.find(line)
            var consumedNext = false
            if (rowMatch == null && !inProfessorsSection &&
                pendingRowPrefixRegex.matches(line) && idx + 1 < lines.size
            ) {
                val combined = "$line ${lines[idx + 1]}"
                val combinedMatch = rowRegex.find(combined)
                if (combinedMatch != null) {
                    rowMatch = combinedMatch
                    consumedNext = true
                }
            }

            if (rowMatch != null) {
                val semester = "${rowMatch.groupValues[1]}.${rowMatch.groupValues[2]}"
                val code = rowMatch.groupValues[3].uppercase()
                val name = normalizeUfopPortugueseText(rowMatch.groupValues[4])
                val workload = rowMatch.groupValues[5].toIntOrNull()?.coerceAtLeast(1) ?: 60
                val grade = rowMatch.groupValues[6]
                    .takeIf { it.isNotBlank() }
                    ?.replace(',', '.')
                    ?.toDoubleOrNull()
                    ?.coerceIn(0.0, 10.0)
                val absences = rowMatch.groupValues[7].toIntOrNull()?.coerceAtLeast(0) ?: 0
                val totalClasses = rowMatch.groupValues[8].toIntOrNull()?.coerceAtLeast(0)
                    ?: ((workload * 12) / 10)
                val status = rowMatch.groupValues[9].uppercase()

                val semSlug = semester.replace(".", "_")
                var entryId = "tx_${semSlug}_${code.lowercase()}"
                if (!seenIds.add(entryId)) {
                    entryId = "${entryId}_${parsedRows.size + 1}"
                    seenIds.add(entryId)
                }

                parsedRows.add(
                    TranscriptCourseEntry(
                        id = entryId,
                        semester = semester,
                        code = code,
                        name = name,
                        workloadHours = workload,
                        grade = grade,
                        absences = absences,
                        totalClasses = totalClasses,
                        status = status,
                        professor = ""
                    )
                )
                idx += if (consumedNext) 2 else 1
                continue
            }

            val profMatch = profRowRegex.find(line)
            if (profMatch != null) {
                val rawRest = profMatch.groupValues[4].trim()
                val looksLikePage1Row = rawRest.startsWith("-") ||
                    Regex("""\d+/\d+\s+(?:AP|RN|RF|NF|CS|TR|DP|AE|DE|DJ|EA|PF|X)\b""", RegexOption.IGNORE_CASE)
                        .containsMatchIn(rawRest)
                if (!looksLikePage1Row && (inProfessorsSection || Regex("""\(\s*(?:T\s*\+\s*P|T|P)\s*\)""", RegexOption.IGNORE_CASE).containsMatchIn(rawRest))) {
                    val semester = "${profMatch.groupValues[1]}.${profMatch.groupValues[2]}"
                    val code = profMatch.groupValues[3].uppercase()
                    val cleanProf = cleanTranscriptProfessorName(rawRest)
                    if (cleanProf.isNotEmpty()) {
                        professorsBySemAndCode[semester to code] = cleanProf
                    }
                }
            }
            idx += 1
        }

        require(parsedRows.isNotEmpty()) {
            "Não foi possível identificar disciplinas neste Histórico Escolar da UFOP."
        }

        val enrichedRows = parsedRows.map { entry ->
            val prof = professorsBySemAndCode[entry.semester to entry.code] ?: entry.professor
            if (prof.isNotBlank()) entry.copy(professor = prof) else entry
        }

        return synchronized(lock) {
            applyImportedTranscriptLocked(meta, enrichedRows)
        }
    }

    private fun applyImportedTranscriptLocked(
        meta: ParsedTranscriptMeta,
        entries: List<TranscriptCourseEntry>
    ): AcademicCreReport {
        transcriptEntries.clear()
        transcriptEntries.addAll(entries)

        if (meta.totalRequiredHours != null && meta.totalRequiredHours > 0) {
            totalRequiredHours = meta.totalRequiredHours
        }
        if (meta.completedHours != null && meta.completedHours >= 0) {
            importedCompletedHours = meta.completedHours
        }

        val latestCsSemester = entries
            .filter { it.status == "CS" }
            .maxOfOrNull { it.semester }
        val latestSemester = latestCsSemester ?: entries.maxOfOrNull { it.semester }

        studentProfile = studentProfile.copy(
            fullName = meta.fullName ?: studentProfile.fullName,
            registrationNumber = meta.registrationNumber ?: studentProfile.registrationNumber,
            courseName = meta.courseName ?: studentProfile.courseName,
            activeSemester = if (!studentProfile.onboardingCompleted || studentProfile.activeSemester.isBlank()) {
                latestSemester ?: studentProfile.activeSemester.ifBlank { "2026.2" }
            } else {
                studentProfile.activeSemester
            },
            onboardingCompleted = true
        )

        for (entry in entries) {
            val matchingCourse = courses.values.firstOrNull { c ->
                c.semester == entry.semester && (
                    resolveCourseCode(c).equals(entry.code, ignoreCase = true) ||
                        c.courseId.equals("${entry.code.lowercase()}_${entry.semester.replace(".", "_")}", ignoreCase = true) ||
                        normalizeDedupKey(c.name) == normalizeDedupKey(entry.name)
                    )
            }
            val maxAbs = if (entry.totalClasses > 0) {
                (entry.totalClasses / 4).coerceAtLeast(1)
            } else {
                ((entry.workloadHours * 3) / 10).coerceAtLeast(1)
            }

            if (matchingCourse != null) {
                val updatedProf = entry.professor.ifBlank { matchingCourse.professor }
                val updatedBaseAbsences = if (entry.status != "CS" || matchingCourse.baseAbsences == 0) {
                    val extraAbs = computeExtraCheckInAbsencesLocked(matchingCourse.courseId)
                    (entry.absences - extraAbs).coerceAtLeast(0)
                } else {
                    matchingCourse.baseAbsences
                }
                courses[matchingCourse.courseId] = matchingCourse.copy(
                    name = entry.name,
                    code = entry.code,
                    workloadHours = entry.workloadHours,
                    maxAbsences = maxAbs,
                    baseAbsences = updatedBaseAbsences,
                    professor = updatedProf
                )
            } else {
                val semSlug = entry.semester.replace(".", "_")
                var courseId = "${entry.code.lowercase()}_$semSlug"
                if (courseId in courses) {
                    generatedIdCounter += 1L
                    courseId = "${courseId}_$generatedIdCounter"
                }
                val dept = resolveDepartmentForCourseCode(entry.code)
                val gradeLabel = entry.grade?.let { " · Média: $it" } ?: ""
                val initialNote = "Código: ${entry.code} · Situação: ${entry.status}$gradeLabel"
                courses[courseId] = CourseSeed(
                    courseId = courseId,
                    semester = entry.semester,
                    name = entry.name,
                    departmentId = dept.id,
                    departmentName = dept.name,
                    defaultColorHex = dept.colorHex,
                    workloadHours = entry.workloadHours,
                    baseAbsences = entry.absences,
                    maxAbsences = maxAbs,
                    baseCancelledClasses = 0,
                    professor = entry.professor,
                    buildingAndRoom = "Campus UFOP",
                    shortLocation = "UFOP",
                    assessments = emptyList(),
                    initialNotes = initialNote,
                    code = entry.code
                )
                notesByCourseId[courseId] = initialNote
                assessmentsByCourseId[courseId] = mutableListOf()
            }
        }

        persistStateLocked()
        _mutationVersion.value += 1L
        return getAcademicCreReportLocked()
    }

    private fun getAcademicCreReportLocked(): AcademicCreReport {
        val merged = mutableListOf<TranscriptCourseEntry>()

        for (entry in transcriptEntries) {
            val matchedCourse = courses.values.firstOrNull { c ->
                c.semester == entry.semester && (
                    c.courseId.equals(entry.id, ignoreCase = true) ||
                        resolveCourseCode(c).equals(entry.code, ignoreCase = true) ||
                        normalizeDedupKey(c.name) == normalizeDedupKey(entry.name)
                    )
            }
            if (matchedCourse != null) {
                val liveAbsences = if (entry.status == "CS") {
                    buildDisciplineSummaryLocked(matchedCourse).currentAbsences
                } else {
                    entry.absences
                }
                val liveProfessor = when {
                    entry.professor.isNotBlank() -> entry.professor
                    matchedCourse.professor.isNotBlank() && !matchedCourse.professor.startsWith("Turma ") -> matchedCourse.professor
                    else -> entry.professor
                }
                merged.add(
                    entry.copy(
                        absences = liveAbsences,
                        professor = liveProfessor
                    )
                )
            } else {
                merged.add(entry)
            }
        }

        for (course in courses.values) {
            val courseCode = resolveCourseCode(course)
            val alreadyPresent = merged.any { entry ->
                entry.semester == course.semester && (
                    entry.id.equals(course.courseId, ignoreCase = true) ||
                        entry.code.equals(courseCode, ignoreCase = true) ||
                        normalizeDedupKey(entry.name) == normalizeDedupKey(course.name)
                    )
            }
            if (!alreadyPresent) {
                merged.add(
                    TranscriptCourseEntry(
                        id = course.courseId,
                        semester = course.semester,
                        code = courseCode,
                        name = course.name,
                        workloadHours = course.workloadHours,
                        grade = null,
                        absences = buildDisciplineSummaryLocked(course).currentAbsences,
                        totalClasses = (course.workloadHours * 12) / 10,
                        status = "CS",
                        professor = course.professor
                    )
                )
            }
        }

        val semesters = merged
            .groupBy { it.semester }
            .entries
            .sortedByDescending { it.key }
            .map { (sem, list) ->
                val sortedCourses = list.sortedWith(
                    compareBy<TranscriptCourseEntry> { it.code }.thenBy { it.name }
                )
                val graded = sortedCourses.filter { it.grade != null && it.workloadHours > 0 }
                val gradedWorkload = graded.sumOf { it.workloadHours }
                val weightedSum = graded.sumOf { (it.grade ?: 0.0) * it.workloadHours }
                val semCre = if (gradedWorkload > 0) roundCre2(weightedSum / gradedWorkload) else 0.0
                SemesterCreSummary(
                    semester = sem,
                    cre = semCre,
                    totalWorkloadHours = sortedCourses.sumOf { it.workloadHours },
                    approvedCount = sortedCourses.count { it.status in APPROVED_STATUSES },
                    failedCount = sortedCourses.count { it.status in FAILED_STATUSES },
                    inProgressCount = sortedCourses.count { it.status == "CS" },
                    courses = sortedCourses
                )
            }

        val allGraded = merged.filter { it.grade != null && it.workloadHours > 0 }
        val allGradedWorkload = allGraded.sumOf { it.workloadHours }
        val allWeightedSum = allGraded.sumOf { (it.grade ?: 0.0) * it.workloadHours }
        val overallCre = if (allGradedWorkload > 0) roundCre2(allWeightedSum / allGradedWorkload) else 0.0

        val computedCompleted = merged
            .filter { it.status in APPROVED_STATUSES }
            .sumOf { it.workloadHours }
        val completedHours = if (computedCompleted > 0) computedCompleted else importedCompletedHours

        return AcademicCreReport(
            overallCre = overallCre,
            totalRequiredHours = totalRequiredHours,
            completedHours = completedHours,
            totalApproved = merged.count { it.status in APPROVED_STATUSES },
            totalFailedByGrade = merged.count { it.status == "RN" || it.status == "NF" },
            totalFailedByAbsence = merged.count { it.status == "RF" || it.status == "NF" },
            semesters = semesters
        )
    }

    private fun computeExtraCheckInAbsencesLocked(courseId: String): Int {
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

    private fun loadPersistedStateOrFallbackLocked(file: File) {
        val parentDir = file.absoluteFile.parentFile
        val candidates = listOf(
            file,
            File(parentDir, "${file.name}.bak"),
            File(parentDir, "${file.name}.tmp")
        )
        for (candidate in candidates) {
            if (!candidate.exists() || candidate.length() <= 2L) continue
            var wasPlaintext = false
            val root = runCatching {
                val raw = candidate.readText(Charsets.UTF_8)
                if (raw.isBlank()) return@runCatching null
                val decrypted = CampusCryptoEngine.decryptStatePayloadIfNeeded(raw)
                wasPlaintext = (decrypted == raw)
                JSONObject(decrypted)
            }.getOrNull() ?: continue

            val schemaVersion = root.optInt("schemaVersion", -1)
            val hasValidData = schemaVersion >= 1 ||
                root.has("courses") ||
                root.has("studentProfile") ||
                root.has("transcriptEntries")
            if (!hasValidData) continue

            val restored = runCatching {
                restoreStateFromJsonLocked(root)
                true
            }.getOrDefault(false)
            if (restored) {
                if (wasPlaintext) {
                    if (candidate != file) {
                        runCatching {
                            Files.copy(
                                candidate.toPath(),
                                file.toPath(),
                                StandardCopyOption.REPLACE_EXISTING
                            )
                        }
                    }
                    // Migração v1.7.5 -> v1.8.0: regrava imediatamente criptografado em disco.
                    persistStateLocked(encryptExistingPlaintextBackup = true)
                } else if (candidate != file) {
                    runCatching {
                        Files.copy(
                            candidate.toPath(),
                            file.toPath(),
                            StandardCopyOption.REPLACE_EXISTING
                        )
                    }
                }
                return
            }
        }
        applyVirginStateLocked()
    }

    private fun restoreStateFromJsonLocked(root: JSONObject) {
        val loadedCampusRaw = runCatching { root.optNonNullString("activeCampusId", "op_cruzeiro") }
            .getOrDefault("op_cruzeiro")
        val resolvedCampusId = if (ufopCampusesSeed.any { it.id == loadedCampusRaw }) {
            loadedCampusRaw
        } else {
            "op_cruzeiro"
        }
        val resolvedCounter = runCatching { root.optLong("generatedIdCounter", 100L) }
            .getOrDefault(100L)
            .coerceAtLeast(100L)
        val loadedWeatherFetchMs = runCatching { root.optLong("lastWeatherFetchEpochMs", 0L) }
            .getOrDefault(0L)
            .coerceAtLeast(0L)

        val loadedDarkPref = runCatching {
            val raw = root.optNonNullString("darkThemePreference", "SYSTEM").trim().uppercase()
            if (raw in setOf("SYSTEM", "DARK", "LIGHT")) raw else "SYSTEM"
        }.getOrDefault("SYSTEM")

        val loadedAccentColor = runCatching {
            val raw = root.optNonNullString("accentColorPreference")
                .ifBlank { root.optNonNullString("accent_color_key") }
            normalizeAccentColorKey(raw)
        }.getOrDefault("GREEN")

        val loadedExportUri = runCatching {
            root.optNonNullString("exportFolderTreeUri").trim().takeIf { it.isNotEmpty() }
        }.getOrNull()

        val loadedExportLabel = runCatching {
            if (loadedExportUri == null) {
                DEFAULT_EXPORT_FOLDER_LABEL
            } else {
                root.optNonNullString("exportFolderDisplayLabel", DEFAULT_EXPORT_FOLDER_LABEL)
                    .trim()
                    .ifBlank { DEFAULT_EXPORT_FOLDER_LABEL }
            }
        }.getOrDefault(DEFAULT_EXPORT_FOLDER_LABEL)

        val loadedDeptColors = mutableMapOf<String, String>()
        departmentSwatches.forEach { loadedDeptColors[it.id] = it.colorHex }

        val loadedCourses = linkedMapOf<String, CourseSeed>()
        val loadedNotes = mutableMapOf<String, String>()
        val loadedAssessmentsByCourse = linkedMapOf<String, MutableList<AssessmentItem>>()
        runCatching {
            val coursesArr = root.optJSONArray("courses") ?: return@runCatching
            for (i in 0 until coursesArr.length()) {
                runCatching {
                    val obj = coursesArr.optJSONObject(i) ?: return@runCatching
                    val courseId = obj.optNonNullString("id").ifBlank { obj.optNonNullString("courseId") }.trim()
                    val rawName = obj.optNonNullString("name").trim()
                    if (courseId.isEmpty() || rawName.isEmpty()) return@runCatching
                    val name = normalizeUfopPortugueseText(rawName)
                    val semester = obj.optNonNullString("semester", "2026.2").ifBlank { "2026.2" }
                    val rawDeptId = obj.optNonNullString("departmentId", "comp").ifBlank { "comp" }
                    val deptOption = resolveDepartmentOption(rawDeptId)
                    val workloadHours = obj.optInt("workloadHours", 60).coerceAtLeast(1)
                    val maxAbsences = obj.optInt("maxAbsences", (workloadHours * 3) / 10).coerceAtLeast(0)
                    // Pode ser negativo quando o aluno edita manualmente o total abaixo das faltas de check-in.
                    val manualAbsences = if (obj.has("manualAbsences")) {
                        obj.optInt("manualAbsences", 0)
                    } else {
                        obj.optInt("baseAbsences", 0)
                    }
                    val baseCancelledClasses = obj.optInt("baseCancelledClasses", 0).coerceAtLeast(0)
                    val professor = obj.optNonNullString("professor", "").trim()
                    val buildingAndRoom = obj.optNonNullString("buildingAndRoom", "Campus UFOP").ifBlank { "Campus UFOP" }
                    val shortLocation = obj.optNonNullString("shortLocation", "UFOP").ifBlank { "UFOP" }
                    val notes = obj.optNonNullString("notes", "")
                    val code = deriveCourseCodeFallback(
                        explicitCode = obj.optNonNullString("code"),
                        notes = notes,
                        courseId = courseId,
                        courseName = name
                    )

                    loadedCourses[courseId] = CourseSeed(
                        courseId = courseId,
                        semester = semester,
                        name = name,
                        departmentId = deptOption.id,
                        departmentName = deptOption.name,
                        defaultColorHex = deptOption.colorHex,
                        workloadHours = workloadHours,
                        baseAbsences = manualAbsences,
                        maxAbsences = maxAbsences,
                        baseCancelledClasses = baseCancelledClasses,
                        professor = professor,
                        buildingAndRoom = buildingAndRoom,
                        shortLocation = shortLocation,
                        assessments = emptyList(),
                        initialNotes = notes,
                        code = code
                    )
                    loadedNotes[courseId] = notes
                    loadedAssessmentsByCourse[courseId] = mutableListOf()
                }
            }
        }

        val loadedSlots = mutableListOf<SlotSeed>()
        runCatching {
            val slotsArr = root.optJSONArray("slots") ?: return@runCatching
            for (i in 0 until slotsArr.length()) {
                runCatching {
                    val obj = slotsArr.optJSONObject(i) ?: return@runCatching
                    val slotId = obj.optNonNullString("slotId").trim()
                    val courseId = obj.optNonNullString("courseId").trim()
                    if (slotId.isEmpty() || courseId.isEmpty()) return@runCatching
                    val building = obj.optNonNullString("building", "Campus UFOP").ifBlank { "Campus UFOP" }
                    val room = obj.optNonNullString("room", "").trim()
                    val shortLocation = obj.optNonNullString("shortLocation", buildShortLocation(building, room))
                        .ifBlank { buildShortLocation(building, room) }
                    loadedSlots.add(
                        SlotSeed(
                            slotId = slotId,
                            courseId = courseId,
                            dayOfWeek = obj.optInt("dayOfWeek", 1).coerceIn(1, 7),
                            startTime = obj.optNonNullString("startTime", "08:00").ifBlank { "08:00" },
                            endTime = obj.optNonNullString("endTime", "09:40").ifBlank { "09:40" },
                            building = building,
                            room = room,
                            shortLocation = shortLocation,
                            defaultTempC = obj.optInt("defaultTempC", 22),
                            weatherCondition = obj.optNonNullString("weatherCondition", "Ensolarado").ifBlank { "Ensolarado" },
                            classWeight = obj.optInt("classWeight", 1).coerceAtLeast(1)
                        )
                    )
                }
            }
        }

        val loadedSessions = mutableMapOf<String, AttendanceStatus>()
        runCatching {
            val sessionsArr = root.optJSONArray("sessions") ?: return@runCatching
            for (i in 0 until sessionsArr.length()) {
                runCatching {
                    val obj = sessionsArr.optJSONObject(i) ?: return@runCatching
                    val slotId = obj.optNonNullString("slotId").trim()
                    val sessionDate = obj.optNonNullString("sessionDate").trim()
                    val statusRaw = obj.optNonNullString("status").trim()
                    if (slotId.isEmpty() || sessionDate.isEmpty() || statusRaw.isEmpty()) return@runCatching
                    val parsedStatus = runCatching { AttendanceStatus.valueOf(statusRaw) }.getOrNull()
                    if (parsedStatus != null) {
                        loadedSessions[sessionKey(slotId, sessionDate)] = parsedStatus
                    }
                }
            }
        }

        val loadedCommitments = mutableListOf<CommitmentSeed>()
        runCatching {
            val commitmentsArr = root.optJSONArray("commitments") ?: return@runCatching
            for (i in 0 until commitmentsArr.length()) {
                runCatching {
                    val obj = commitmentsArr.optJSONObject(i) ?: return@runCatching
                    val id = obj.optNonNullString("id").trim()
                    if (id.isEmpty()) return@runCatching
                    loadedCommitments.add(
                        CommitmentSeed(
                            id = id,
                            isoDate = obj.optNonNullString("isoDate", "2026-11-17").ifBlank { "2026-11-17" },
                            title = obj.optNonNullString("title", "Compromisso no campus").ifBlank { "Compromisso no campus" },
                            startTime = obj.optNonNullString("startTime", "14:00").ifBlank { "14:00" },
                            endTime = obj.optNonNullString("endTime", "15:00").ifBlank { "15:00" },
                            location = obj.optNonNullString("location", "Campus UFOP").ifBlank { "Campus UFOP" },
                            subtitle = obj.optNonNullString("subtitle", ""),
                            colorHex = obj.optNonNullString("colorHex", COMMITMENT_COLOR_HEX).ifBlank { COMMITMENT_COLOR_HEX },
                            weatherTempC = obj.optInt("weatherTempC", 25)
                        )
                    )
                }
            }
        }

        runCatching {
            val assessmentsArr = root.optJSONArray("assessments") ?: return@runCatching
            for (i in 0 until assessmentsArr.length()) {
                runCatching {
                    val obj = assessmentsArr.optJSONObject(i) ?: return@runCatching
                    val id = obj.optNonNullString("id").trim()
                    val courseId = obj.optNonNullString("courseId").trim()
                    if (id.isEmpty() || courseId.isEmpty()) return@runCatching
                    val dueDate = obj.optNonNullString("dueDate", "2026-11-28").ifBlank { "2026-11-28" }
                    val (_, parsedDay, parsedMonth) = parseDueDateParts(dueDate)
                    val item = AssessmentItem(
                        id = id,
                        courseId = courseId,
                        dayNumber = obj.optNonNullString("dayNumber", parsedDay).ifBlank { parsedDay },
                        monthShort = obj.optNonNullString("monthShort", parsedMonth).ifBlank { parsedMonth },
                        title = obj.optNonNullString("title", "Avaliação").ifBlank { "Avaliação" },
                        kind = obj.optNonNullString("kind", "Trabalho").ifBlank { "Trabalho" },
                        dueTime = obj.optNonNullString("dueTime", "18:00").ifBlank { "18:00" },
                        dueDate = dueDate,
                        completed = obj.optBoolean("completed", false)
                    )
                    loadedAssessmentsByCourse.getOrPut(courseId) { mutableListOf() }.add(item)
                }
            }
        }

        val defaultCampusCity = ufopCampusesSeed.find { it.id == resolvedCampusId }?.city ?: "Ouro Preto"
        val loadedProfile = runCatching {
            val profileObj = root.optJSONObject("studentProfile")
            if (profileObj != null) {
                val rawCourseName = profileObj.optNonNullString("courseName").trim()
                val loadedActiveSemester = profileObj.optNonNullString("activeSemester", "2026.2").ifBlank { "2026.2" }
                val defaultBounds = resolveDefaultSemesterBounds(loadedActiveSemester)
                StudentProfile(
                    fullName = profileObj.optNonNullString("fullName"),
                    registrationNumber = profileObj.optNonNullString("registrationNumber"),
                    courseName = if (rawCourseName.isEmpty()) "" else normalizeUfopPortugueseText(rawCourseName),
                    campusName = profileObj.optNonNullString("campusName", defaultCampusCity).ifBlank { defaultCampusCity },
                    activeSemester = loadedActiveSemester,
                    semesterStartDate = profileObj.optNonNullString("semesterStartDate", defaultBounds.first).ifBlank { defaultBounds.first },
                    semesterEndDate = profileObj.optNonNullString("semesterEndDate", defaultBounds.second).ifBlank { defaultBounds.second },
                    avatarPath = profileObj.optNonNullString("avatarPath").takeIf { it.isNotBlank() },
                    onboardingCompleted = profileObj.optBoolean("onboardingCompleted", loadedCourses.isNotEmpty())
                )
            } else {
                // Migração limpa de schema v1: mantém usuário já ativo se já havia disciplinas salvas.
                StudentProfile(
                    campusName = defaultCampusCity,
                    onboardingCompleted = loadedCourses.isNotEmpty()
                )
            }
        }.getOrElse {
            StudentProfile(
                campusName = defaultCampusCity,
                onboardingCompleted = loadedCourses.isNotEmpty()
            )
        }

        val loadedFriends = mutableListOf<FriendScheduleEntry>()
        runCatching {
            val friendsArr = root.optJSONArray("friendSchedules") ?: return@runCatching
            for (i in 0 until friendsArr.length()) {
                runCatching {
                    val obj = friendsArr.optJSONObject(i) ?: return@runCatching
                    val id = obj.optNonNullString("id").trim()
                    val friendName = obj.optNonNullString("friendName").trim()
                    if (id.isEmpty() || friendName.isEmpty()) return@runCatching
                    val rawFriendCourseName = obj.optNonNullString("courseName").trim()
                    val friendCourseName = if (rawFriendCourseName.isEmpty()) "" else normalizeUfopPortugueseText(rawFriendCourseName)
                    val friendAvatarBase64 = obj.optNonNullString("avatarBase64").trim().takeIf { it.isNotEmpty() }
                    val semester = obj.optNonNullString("semester", "2026.2").ifBlank { "2026.2" }
                    val fCourses = mutableListOf<FriendCourseEntry>()
                    val fcArr = obj.optJSONArray("courses")
                    if (fcArr != null) {
                        for (j in 0 until fcArr.length()) {
                            runCatching {
                                val fc = fcArr.optJSONObject(j) ?: return@runCatching
                                val cId = fc.optNonNullString("courseId").trim()
                                val rawCName = fc.optNonNullString("name").trim()
                                if (cId.isEmpty() || rawCName.isEmpty()) return@runCatching
                                val cName = normalizeUfopPortugueseText(rawCName)
                                fCourses.add(
                                    FriendCourseEntry(
                                        courseId = cId,
                                        name = cName,
                                        departmentId = resolveDepartmentOption(fc.optNonNullString("departmentId", "decom")).id,
                                        code = deriveCourseCodeFallback(fc.optNonNullString("code"), "", cId, cName)
                                    )
                                )
                            }
                        }
                    }
                    val fSlots = mutableListOf<FriendSlotEntry>()
                    val fsArr = obj.optJSONArray("slots")
                    if (fsArr != null) {
                        for (j in 0 until fsArr.length()) {
                            runCatching {
                                val fs = fsArr.optJSONObject(j) ?: return@runCatching
                                val sId = fs.optNonNullString("slotId").trim()
                                val cId = fs.optNonNullString("courseId").trim()
                                if (sId.isEmpty() || cId.isEmpty()) return@runCatching
                                fSlots.add(
                                    FriendSlotEntry(
                                        slotId = sId,
                                        courseId = cId,
                                        dayOfWeek = fs.optInt("dayOfWeek", 1).coerceIn(1, 7),
                                        startTime = fs.optNonNullString("startTime", "08:00").ifBlank { "08:00" },
                                        endTime = fs.optNonNullString("endTime", "09:40").ifBlank { "09:40" },
                                        shortLocation = fs.optNonNullString("shortLocation", "UFOP").ifBlank { "UFOP" }
                                    )
                                )
                            }
                        }
                    }
                    loadedFriends.add(
                        FriendScheduleEntry(
                            id = id,
                            friendName = friendName,
                            courseName = friendCourseName,
                            semester = semester,
                            avatarBase64 = friendAvatarBase64,
                            courses = fCourses,
                            slots = fSlots
                        )
                    )
                }
            }
        }

        val loadedWeather = mutableMapOf<String, CampusWeather>()
        runCatching {
            val wObj = root.optJSONObject("campusWeather") ?: return@runCatching
            val keys = wObj.keys()
            while (keys.hasNext()) {
                runCatching {
                    val cId = keys.next()
                    val cw = wObj.optJSONObject(cId) ?: return@runCatching
                    if (cw.has("tempC")) {
                        loadedWeather[cId] = CampusWeather(
                            tempC = cw.optInt("tempC", 22),
                            condition = cw.optNonNullString("condition", "Ensolarado").ifBlank { "Ensolarado" }
                        )
                    }
                }
            }
        }

        val loadedCalendarBySemester = linkedMapOf<String, MutableList<AcademicCalendarEvent>>()
        runCatching {
            val calObj = root.optJSONObject("calendarEventsBySemester") ?: return@runCatching
            val semKeys = calObj.keys()
            while (semKeys.hasNext()) {
                runCatching {
                    val semKey = semKeys.next()
                    val arr = calObj.optJSONArray(semKey) ?: return@runCatching
                    val list = mutableListOf<AcademicCalendarEvent>()
                    for (i in 0 until arr.length()) {
                        runCatching {
                            val evObj = arr.optJSONObject(i) ?: return@runCatching
                            val evId = evObj.optNonNullString("id").trim()
                            val evDate = evObj.optNonNullString("eventDate").trim()
                            val evTitle = evObj.optNonNullString("title").trim()
                            if (evId.isEmpty() || evDate.isEmpty() || evTitle.isEmpty()) return@runCatching
                            val (_, parsedDay, parsedMonth) = parseDueDateParts(evDate)
                            list.add(
                                AcademicCalendarEvent(
                                    id = evId,
                                    semester = evObj.optNonNullString("semester", semKey).ifBlank { semKey },
                                    eventDate = evDate,
                                    dayNumber = evObj.optNonNullString("dayNumber", parsedDay).ifBlank { parsedDay },
                                    monthShort = evObj.optNonNullString("monthShort", parsedMonth).ifBlank { parsedMonth },
                                    title = evTitle,
                                    category = evObj.optNonNullString("category", "Acadêmico").ifBlank { "Acadêmico" }
                                )
                            )
                        }
                    }
                    if (list.isNotEmpty()) {
                        loadedCalendarBySemester[semKey] = list
                    }
                }
            }
        }
        val loadedAdminUnlocked = runCatching { root.optBoolean("adminUnlocked", false) }.getOrDefault(false)
        val loadedPendingCal = runCatching { parseCalendarPackageFromJson(root.optJSONObject("pendingCalendarUpdate")) }.getOrNull()
        val loadedLatestCal = runCatching { parseCalendarPackageFromJson(root.optJSONObject("latestPublishedCalendarUpdate")) }.getOrNull()

        val legacySeedIds = setOf(
            "cal_2026_2_trancamento",
            "cal_2026_2_autonomos",
            "cal_2026_2_encerramento",
            "cal_2026_2_exames",
            "cal_2026_2_estudos_autonomos",
            "cal_2026_2_exames_especiais"
        )
        val officialSeedBySemester = calendarEventsSeed.groupBy { it.semester }
        val existing20262 = loadedCalendarBySemester["2026.2"]
        if (!existing20262.isNullOrEmpty() &&
            existing20262.all { it.id in legacySeedIds } &&
            loadedLatestCal?.semester != "2026.2"
        ) {
            officialSeedBySemester["2026.2"]?.let { seedList ->
                loadedCalendarBySemester["2026.2"] = seedList.toMutableList()
            }
        }
        officialSeedBySemester.forEach { (sem, list) ->
            if (loadedCalendarBySemester[sem].isNullOrEmpty()) {
                loadedCalendarBySemester[sem] = list.toMutableList()
            }
        }

        val loadedTotalRequiredHours = runCatching { root.optInt("totalRequiredHours", 3610) }
            .getOrDefault(3610)
            .coerceAtLeast(1)
        val loadedImportedCompletedHours = runCatching { root.optInt("importedCompletedHours", 0) }
            .getOrDefault(0)
            .coerceAtLeast(0)
        val loadedTranscriptEntries = mutableListOf<TranscriptCourseEntry>()
        runCatching {
            val txArr = root.optJSONArray("transcriptEntries") ?: return@runCatching
            for (i in 0 until txArr.length()) {
                runCatching {
                    val txObj = txArr.optJSONObject(i) ?: return@runCatching
                    val txId = txObj.optNonNullString("id").trim()
                    val txSem = txObj.optNonNullString("semester").trim()
                    val txCode = txObj.optNonNullString("code").trim().uppercase()
                    val rawTxName = txObj.optNonNullString("name").trim()
                    if (txId.isEmpty() || txSem.isEmpty() || txCode.isEmpty() || rawTxName.isEmpty()) return@runCatching
                    val txGrade = if (txObj.isNull("grade") || !txObj.has("grade")) {
                        null
                    } else {
                        txObj.optDouble("grade").takeIf { it.isFinite() }?.coerceIn(0.0, 10.0)
                    }
                    val rawProf = txObj.optNonNullString("professor").trim()
                    loadedTranscriptEntries.add(
                        TranscriptCourseEntry(
                            id = txId,
                            semester = txSem,
                            code = txCode,
                            name = normalizeUfopPortugueseText(rawTxName),
                            workloadHours = txObj.optInt("workloadHours", 60).coerceAtLeast(1),
                            grade = txGrade,
                            absences = txObj.optInt("absences", 0).coerceAtLeast(0),
                            totalClasses = txObj.optInt("totalClasses", 72).coerceAtLeast(0),
                            status = txObj.optNonNullString("status", "CS").ifBlank { "CS" }.uppercase(),
                            professor = if (rawProf.isEmpty()) "" else normalizeUfopPortugueseText(rawProf)
                        )
                    )
                }
            }
        }

        val loadedGuidedTourCompleted = runCatching {
            root.optBoolean("guidedTourCompleted", false)
        }.getOrDefault(false)
        val loadedLastSeenReleaseVersion = runCatching {
            root.optNonNullString("lastSeenReleaseVersion", "").trim()
        }.getOrDefault("")
        val loadedAccessibilitySettings = runCatching {
            val a11yObj = root.optJSONObject("accessibilitySettings")
            if (a11yObj != null) {
                val rawScale = a11yObj.optDouble("fontScaleMultiplier", 1.0).toFloat()
                val safeScale = if (rawScale.isFinite()) rawScale.coerceIn(0.85f, 2.0f) else 1.0f
                CampusAccessibilitySettings(
                    screenReaderOptimized = a11yObj.optBoolean("screenReaderOptimized", false),
                    fontScaleMultiplier = safeScale,
                    highContrastMode = a11yObj.optBoolean("highContrastMode", false),
                    reduceMotion = a11yObj.optBoolean("reduceMotion", false),
                    largeTouchTargets = a11yObj.optBoolean("largeTouchTargets", false),
                    boldTextBoost = a11yObj.optBoolean("boldTextBoost", false)
                )
            } else {
                CampusAccessibilitySettings()
            }
        }.getOrDefault(CampusAccessibilitySettings())

        val loadedDynamicGrid = linkedMapOf<String, MutableSet<String>>()
        runCatching {
            val dgObj = root.optJSONObject("dynamicGridCompletedByMatrix") ?: return@runCatching
            val matrixKeys = dgObj.keys()
            while (matrixKeys.hasNext()) {
                runCatching {
                    val mId = matrixKeys.next().trim()
                    if (mId.isEmpty()) return@runCatching
                    val arr = dgObj.optJSONArray(mId) ?: return@runCatching
                    val set = linkedSetOf<String>()
                    for (i in 0 until arr.length()) {
                        if (!arr.isNull(i)) {
                            val code = arr.optString(i, "").trim().uppercase()
                            if (code.isNotEmpty()) {
                                set.add(code)
                            }
                        }
                    }
                    if (set.isNotEmpty()) {
                        loadedDynamicGrid[mId] = set
                    }
                }
            }
        }

        courses.clear()
        loadedCourses.forEach { (id, seed) ->
            courses[id] = seed.copy(assessments = loadedAssessmentsByCourse[id].orEmpty().toList())
        }
        slots.clear()
        slots.addAll(loadedSlots)
        commitments.clear()
        commitments.addAll(loadedCommitments)
        courseColorOverrides.clear()
        departmentColorOverrides.clear()
        departmentColorOverrides.putAll(loadedDeptColors)
        checkInStatusBySessionKey.clear()
        checkInStatusBySessionKey.putAll(loadedSessions)
        notesByCourseId.clear()
        notesByCourseId.putAll(loadedNotes)
        assessmentsByCourseId.clear()
        assessmentsByCourseId.putAll(loadedAssessmentsByCourse)
        friendSchedules.clear()
        friendSchedules.addAll(loadedFriends)
        campusWeatherById.clear()
        campusWeatherById.putAll(loadedWeather)
        calendarEventsBySemester.clear()
        calendarEventsBySemester.putAll(loadedCalendarBySemester)
        adminUnlocked = loadedAdminUnlocked
        pendingCalendarUpdate = loadedPendingCal
        latestPublishedCalendarUpdate = loadedLatestCal
        transcriptEntries.clear()
        transcriptEntries.addAll(loadedTranscriptEntries)
        totalRequiredHours = loadedTotalRequiredHours
        importedCompletedHours = loadedImportedCompletedHours
        studentProfile = loadedProfile
        activeCampusId = resolvedCampusId
        generatedIdCounter = resolvedCounter
        lastWeatherFetchEpochMs = loadedWeatherFetchMs
        darkThemePreference = loadedDarkPref
        accentColorPreference = loadedAccentColor
        exportFolderTreeUri = loadedExportUri
        exportFolderDisplayLabel = loadedExportLabel
        accessibilitySettings = loadedAccessibilitySettings
        guidedTourCompleted = loadedGuidedTourCompleted
        lastSeenReleaseVersion = loadedLastSeenReleaseVersion
        dynamicGridCompletedByMatrix.clear()
        dynamicGridCompletedByMatrix.putAll(loadedDynamicGrid)
        roomAllocationIndex?.let { index ->
            enrichExistingRoomAllocationsLocked(index)
        }
    }

    private fun buildPersistedJsonLocked(): JSONObject {
        val root = JSONObject()
        root.put("schemaVersion", PERSISTED_SCHEMA_VERSION)
        root.put("activeCampusId", activeCampusId)
        root.put("generatedIdCounter", generatedIdCounter)
        root.put("lastWeatherFetchEpochMs", lastWeatherFetchEpochMs)
        root.put("darkThemePreference", darkThemePreference)
        root.put("accentColorPreference", accentColorPreference)
        root.put("accent_color_key", accentColorPreference)
        if (exportFolderTreeUri != null) {
            root.put("exportFolderTreeUri", exportFolderTreeUri)
        } else {
            root.put("exportFolderTreeUri", JSONObject.NULL)
        }
        root.put("exportFolderDisplayLabel", exportFolderDisplayLabel)

        val profileJson = JSONObject().apply {
            put("fullName", studentProfile.fullName)
            put("registrationNumber", studentProfile.registrationNumber)
            put("courseName", studentProfile.courseName)
            put("campusName", studentProfile.campusName)
            put("activeSemester", studentProfile.activeSemester)
            put("semesterStartDate", studentProfile.semesterStartDate)
            put("semesterEndDate", studentProfile.semesterEndDate)
            if (studentProfile.avatarPath != null) {
                put("avatarPath", studentProfile.avatarPath)
            }
            put("onboardingCompleted", studentProfile.onboardingCompleted)
        }
        root.put("studentProfile", profileJson)

        val weatherJson = JSONObject()
        campusWeatherById.forEach { (cId, cw) ->
            weatherJson.put(cId, JSONObject().apply {
                put("tempC", cw.tempC)
                put("condition", cw.condition)
            })
        }
        root.put("campusWeather", weatherJson)

        val deptColorsJson = JSONObject()
        departmentColorOverrides.forEach { (deptId, colorHex) ->
            deptColorsJson.put(deptId, colorHex)
        }
        root.put("departmentColorOverrides", deptColorsJson)

        val coursesArr = JSONArray()
        courses.values.forEach { course ->
            val obj = JSONObject()
            obj.put("id", course.courseId)
            obj.put("courseId", course.courseId)
            obj.put("code", resolveCourseCode(course))
            obj.put("semester", course.semester)
            obj.put("departmentId", course.departmentId)
            obj.put("departmentName", resolveDepartmentName(course))
            obj.put("colorOverrideHex", resolveCourseColor(course))
            obj.put("defaultColorHex", resolveCourseColor(course))
            obj.put("name", course.name)
            obj.put("professor", course.professor)
            obj.put("workloadHours", course.workloadHours)
            obj.put("maxAbsences", course.maxAbsences)
            obj.put("manualAbsences", course.baseAbsences)
            obj.put("baseCancelledClasses", course.baseCancelledClasses)
            obj.put("buildingAndRoom", course.buildingAndRoom)
            obj.put("shortLocation", course.shortLocation)
            obj.put("notes", notesByCourseId[course.courseId] ?: course.initialNotes)
            coursesArr.put(obj)
        }
        root.put("courses", coursesArr)

        val slotsArr = JSONArray()
        slots.forEach { slot ->
            val obj = JSONObject()
            obj.put("slotId", slot.slotId)
            obj.put("courseId", slot.courseId)
            obj.put("dayOfWeek", slot.dayOfWeek)
            obj.put("startTime", slot.startTime)
            obj.put("endTime", slot.endTime)
            obj.put("building", slot.building)
            obj.put("room", slot.room)
            obj.put("shortLocation", slot.shortLocation)
            obj.put("defaultTempC", slot.defaultTempC)
            obj.put("weatherCondition", slot.weatherCondition)
            obj.put("classWeight", slot.classWeight)
            slotsArr.put(obj)
        }
        root.put("slots", slotsArr)

        val sessionsArr = JSONArray()
        checkInStatusBySessionKey.forEach { (key, status) ->
            val separator = key.lastIndexOf('_')
            if (separator > 0) {
                val slotId = key.substring(0, separator)
                val sessionDate = key.substring(separator + 1)
                val slot = slots.find { it.slotId == slotId }
                val obj = JSONObject()
                obj.put("slotId", slotId)
                obj.put("courseId", slot?.courseId ?: "")
                obj.put("sessionDate", sessionDate)
                obj.put("status", status.name)
                obj.put("weatherTempC", slot?.defaultTempC ?: 22)
                obj.put("weatherCondition", slot?.weatherCondition ?: "Ensolarado")
                obj.put("classWeight", if (status == AttendanceStatus.ABSENT) (slot?.classWeight ?: 1) else 0)
                obj.put("startTime", slot?.startTime ?: "08:00")
                obj.put("endTime", slot?.endTime ?: "09:40")
                sessionsArr.put(obj)
            }
        }
        root.put("sessions", sessionsArr)

        val commitmentsArr = JSONArray()
        commitments.forEach { comm ->
            val obj = JSONObject()
            obj.put("id", comm.id)
            obj.put("isoDate", comm.isoDate)
            obj.put("title", comm.title)
            obj.put("startTime", comm.startTime)
            obj.put("endTime", comm.endTime)
            obj.put("location", comm.location)
            obj.put("subtitle", comm.subtitle)
            obj.put("colorHex", comm.colorHex)
            obj.put("weatherTempC", comm.weatherTempC)
            commitmentsArr.put(obj)
        }
        root.put("commitments", commitmentsArr)

        val assessmentsArr = JSONArray()
        assessmentsByCourseId.values.forEach { list ->
            list.forEach { item ->
                val obj = JSONObject()
                obj.put("id", item.id)
                obj.put("courseId", item.courseId)
                obj.put("dayNumber", item.dayNumber)
                obj.put("monthShort", item.monthShort)
                obj.put("title", item.title)
                obj.put("kind", item.kind)
                obj.put("dueTime", item.dueTime)
                obj.put("dueDate", item.dueDate)
                obj.put("completed", item.completed)
                assessmentsArr.put(obj)
            }
        }
        root.put("assessments", assessmentsArr)

        val friendsArr = JSONArray()
        friendSchedules.forEach { friend ->
            val fObj = JSONObject()
            fObj.put("id", friend.id)
            fObj.put("friendName", friend.friendName)
            fObj.put("courseName", friend.courseName)
            fObj.put("semester", friend.semester)
            if (!friend.avatarBase64.isNullOrBlank()) {
                fObj.put("avatarBase64", friend.avatarBase64)
            }
            val fcArr = JSONArray()
            friend.courses.forEach { fc ->
                fcArr.put(JSONObject().apply {
                    put("courseId", fc.courseId)
                    put("code", deriveCourseCodeFallback(fc.code, "", fc.courseId, fc.name))
                    put("name", fc.name)
                    put("departmentId", fc.departmentId)
                })
            }
            fObj.put("courses", fcArr)
            val fsArr = JSONArray()
            friend.slots.forEach { fs ->
                fsArr.put(JSONObject().apply {
                    put("slotId", fs.slotId)
                    put("courseId", fs.courseId)
                    put("dayOfWeek", fs.dayOfWeek)
                    put("startTime", fs.startTime)
                    put("endTime", fs.endTime)
                    put("shortLocation", fs.shortLocation)
                })
            }
            fObj.put("slots", fsArr)
            friendsArr.put(fObj)
        }
        root.put("friendSchedules", friendsArr)

        val calEventsJson = JSONObject()
        calendarEventsBySemester.forEach { (sem, list) ->
            val arr = JSONArray()
            list.forEach { ev ->
                arr.put(JSONObject().apply {
                    put("id", ev.id)
                    put("semester", ev.semester)
                    put("eventDate", ev.eventDate)
                    put("dayNumber", ev.dayNumber)
                    put("monthShort", ev.monthShort)
                    put("title", ev.title)
                    put("category", ev.category)
                })
            }
            calEventsJson.put(sem, arr)
        }
        root.put("calendarEventsBySemester", calEventsJson)
        root.put("adminUnlocked", adminUnlocked)
        pendingCalendarUpdate?.let { root.put("pendingCalendarUpdate", calendarPackageToJson(it)) }
        latestPublishedCalendarUpdate?.let { root.put("latestPublishedCalendarUpdate", calendarPackageToJson(it)) }

        root.put("totalRequiredHours", totalRequiredHours)
        root.put("importedCompletedHours", importedCompletedHours)
        val txArr = JSONArray()
        transcriptEntries.forEach { entry ->
            txArr.put(JSONObject().apply {
                put("id", entry.id)
                put("semester", entry.semester)
                put("code", entry.code)
                put("name", entry.name)
                put("workloadHours", entry.workloadHours)
                if (entry.grade != null) {
                    put("grade", entry.grade)
                } else {
                    put("grade", JSONObject.NULL)
                }
                put("absences", entry.absences)
                put("totalClasses", entry.totalClasses)
                put("status", entry.status)
                put("professor", entry.professor)
            })
        }
        root.put("transcriptEntries", txArr)

        root.put("guidedTourCompleted", guidedTourCompleted)
        root.put("lastSeenReleaseVersion", lastSeenReleaseVersion)
        root.put(
            "accessibilitySettings",
            JSONObject().apply {
                put("screenReaderOptimized", accessibilitySettings.screenReaderOptimized)
                put("fontScaleMultiplier", accessibilitySettings.fontScaleMultiplier.toDouble())
                put("highContrastMode", accessibilitySettings.highContrastMode)
                put("reduceMotion", accessibilitySettings.reduceMotion)
                put("largeTouchTargets", accessibilitySettings.largeTouchTargets)
                put("boldTextBoost", accessibilitySettings.boldTextBoost)
            }
        )

        val dynamicGridJson = JSONObject()
        dynamicGridCompletedByMatrix.forEach { (matrixId, codes) ->
            val arr = JSONArray()
            codes.forEach { code -> arr.put(code) }
            dynamicGridJson.put(matrixId, arr)
        }
        root.put("dynamicGridCompletedByMatrix", dynamicGridJson)

        return root
    }

    private fun persistStateLocked(encryptExistingPlaintextBackup: Boolean = false) {
        val targetFile = stateFile ?: return
        var persisted = false
        try {
            val parentDir = targetFile.absoluteFile.parentFile
            if (parentDir != null && !parentDir.exists()) {
                parentDir.mkdirs()
            }
            val plaintextJson = buildPersistedJsonLocked().toString()
            val encryptedPayload = CampusCryptoEngine.encryptStatePayload(plaintextJson)
            val payloadBytes = encryptedPayload.toByteArray(Charsets.UTF_8)
            val tempFile = File(parentDir, "${targetFile.name}.tmp")
            val backupFile = File(parentDir, "${targetFile.name}.bak")

            FileOutputStream(tempFile).use { out ->
                out.write(payloadBytes)
                out.flush()
                runCatching { out.fd.sync() }
            }

            if (targetFile.exists() && targetFile.length() > 2L) {
                var backupWritten = false
                if (encryptExistingPlaintextBackup) {
                    backupWritten = runCatching {
                        val prevRaw = targetFile.readText(Charsets.UTF_8)
                        val prevDecrypted = CampusCryptoEngine.decryptStatePayloadIfNeeded(prevRaw)
                        val encryptedBackupBytes = CampusCryptoEngine
                            .encryptStatePayload(prevDecrypted)
                            .toByteArray(Charsets.UTF_8)
                        FileOutputStream(backupFile).use { out ->
                            out.write(encryptedBackupBytes)
                            out.flush()
                            runCatching { out.fd.sync() }
                        }
                        true
                    }.getOrDefault(false)
                }
                if (!backupWritten) {
                    runCatching {
                        Files.copy(
                            targetFile.toPath(),
                            backupFile.toPath(),
                            StandardCopyOption.REPLACE_EXISTING
                        )
                    }.onFailure {
                        runCatching {
                            targetFile.inputStream().use { input ->
                                FileOutputStream(backupFile).use { out ->
                                    input.copyTo(out)
                                    out.flush()
                                    runCatching { out.fd.sync() }
                                }
                            }
                        }
                    }
                }
            } else if (encryptExistingPlaintextBackup && backupFile.exists()) {
                runCatching {
                    val prevBakRaw = backupFile.readText(Charsets.UTF_8)
                    val prevBakDecrypted = CampusCryptoEngine.decryptStatePayloadIfNeeded(prevBakRaw)
                    if (prevBakRaw == prevBakDecrypted) {
                        val encryptedBakBytes = CampusCryptoEngine
                            .encryptStatePayload(prevBakDecrypted)
                            .toByteArray(Charsets.UTF_8)
                        FileOutputStream(backupFile).use { out ->
                            out.write(encryptedBakBytes)
                            out.flush()
                            runCatching { out.fd.sync() }
                        }
                    }
                }
            }

            val moved = runCatching {
                Files.move(
                    tempFile.toPath(),
                    targetFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING,
                    StandardCopyOption.ATOMIC_MOVE
                )
                true
            }.recoverCatching {
                Files.move(
                    tempFile.toPath(),
                    targetFile.toPath(),
                    StandardCopyOption.REPLACE_EXISTING
                )
                true
            }.getOrDefault(false)

            if (!moved) {
                FileOutputStream(targetFile).use { out ->
                    out.write(payloadBytes)
                    out.flush()
                    runCatching { out.fd.sync() }
                }
                runCatching { tempFile.delete() }
            }
            persisted = true
        } catch (_: Throwable) {
            // Persistência best-effort: mantém estado em memória se I/O falhar
        }
        if (persisted) {
            runCatching { onStatePersisted?.invoke() }
        }
    }
}
