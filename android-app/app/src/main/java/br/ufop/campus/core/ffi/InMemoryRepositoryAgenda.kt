package br.ufop.campus.core.ffi

import java.time.DayOfWeek
import java.time.YearMonth
import java.time.temporal.IsoFields
import java.time.temporal.TemporalAdjusters

internal fun InMemoryCampusRepository.listAbsenceHistoryImpl(courseId: String): List<AbsenceHistoryEntry> =
    synchronized(lock) {
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

internal fun InMemoryCampusRepository.getAgendaDayImpl(isoDate: String): AgendaDayOverview =
    synchronized(lock) {
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
            .map { toCommitmentTimelineItem(it) }

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
            weekNumber = date.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR),
            weekDays = weekDays,
            classesCount = classItems.size,
            commitmentsCount = commitmentItems.size,
            items = combinedItems
        )
    }

internal fun InMemoryCampusRepository.getClassCheckInImpl(
    slotId: String,
    isoDate: String
): ClassCheckInDetail = synchronized(lock) {
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

internal fun InMemoryCampusRepository.saveClassCheckInImpl(
    slotId: String,
    isoDate: String,
    status: AttendanceStatus,
    @Suppress("UNUSED_PARAMETER") colorOverrideHex: String?
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

internal fun InMemoryCampusRepository.getWeeklyTimetableImpl(semester: String): WeeklyTimetable =
    synchronized(lock) {
        val cleanSemester = semester.trim().ifBlank { studentProfile.activeSemester.ifBlank { "2026.2" } }
        val semesterCourses = courses.values
            .filter { it.semester == cleanSemester }
            .associateBy { it.courseId }
        val entries = slots.mapNotNull { slot ->
            val course = semesterCourses[slot.courseId] ?: return@mapNotNull null
            InMemoryCampusRepository.TimetableEntry(
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

internal fun buildTimetable(
    semester: String,
    gridTitle: String,
    entries: List<InMemoryCampusRepository.TimetableEntry>
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

internal fun InMemoryCampusRepository.createCommitmentImpl(
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

internal fun InMemoryCampusRepository.updateCommitmentImpl(
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

internal fun toCommitmentTimelineItem(comm: CommitmentSeed): AgendaTimelineItem = AgendaTimelineItem(
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

internal fun InMemoryCampusRepository.deleteCommitmentImpl(commitmentId: String) {
    synchronized(lock) {
        val removed = commitments.removeAll { it.id == commitmentId }
        if (removed) {
            persistStateLocked()
            _mutationVersion.value += 1L
        }
    }
}

internal fun InMemoryCampusRepository.getMonthActivityIndicatorsImpl(
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
