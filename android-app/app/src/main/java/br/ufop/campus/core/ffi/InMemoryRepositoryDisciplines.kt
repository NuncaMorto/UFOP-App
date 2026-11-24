package br.ufop.campus.core.ffi

import br.ufop.campus.core.curriculum.UfopRoomAllocationIndex

internal fun InMemoryCampusRepository.listDepartmentsImpl(): List<DepartmentColorOption> =
    departmentSwatches

internal fun InMemoryCampusRepository.listDepartmentsForSemesterImpl(
    semester: String
): List<DepartmentColorOption> = synchronized(lock) {
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

internal fun InMemoryCampusRepository.listUfopBuildingsImpl(campusId: String?): List<UfopBuildingOption> {
    val raw = campusId?.trim()?.takeIf { it.isNotEmpty() } ?: return ufopBuildingsCatalog
    val resolvedId = when {
        ufopBuildingsCatalog.any { it.campusId.equals(raw, ignoreCase = true) } -> raw.lowercase()
        ufopBuildingsCatalog.any { it.campusName.equals(raw, ignoreCase = true) } ->
            ufopBuildingsCatalog.first { it.campusName.equals(raw, ignoreCase = true) }.campusId
        else -> resolveCampusIdFromText(raw)
    } ?: return emptyList()
    return ufopBuildingsCatalog.filter { it.campusId.equals(resolvedId, ignoreCase = true) }
}

internal fun InMemoryCampusRepository.listDisciplinesImpl(
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

internal fun InMemoryCampusRepository.getDisciplineDetailImpl(courseId: String): DisciplineDetail =
    synchronized(lock) {
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

internal fun InMemoryCampusRepository.updateDisciplineNotesImpl(courseId: String, notes: String) {
    synchronized(lock) {
        if (courseId in courses) {
            notesByCourseId[courseId] = notes
            persistStateLocked()
            _mutationVersion.value += 1L
        }
    }
}

internal fun InMemoryCampusRepository.createCourseImpl(
    semester: String,
    departmentId: String,
    name: String,
    professor: String,
    workloadHours: Int,
    @Suppress("UNUSED_PARAMETER") colorOverrideHex: String?
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

internal fun InMemoryCampusRepository.updateCourseImpl(
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

internal fun InMemoryCampusRepository.updateCourseAbsencesImpl(
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

internal fun InMemoryCampusRepository.deleteCourseImpl(courseId: String) {
    synchronized(lock) {
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
}

internal fun InMemoryCampusRepository.addScheduleSlotImpl(
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

internal fun InMemoryCampusRepository.updateScheduleSlotImpl(
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

internal fun computeClassWeightFromTimes(startTime: String, endTime: String): Int {
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

private fun InMemoryCampusRepository.checkSlotConflictLocked(
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

internal fun InMemoryCampusRepository.deleteScheduleSlotImpl(slotId: String) {
    synchronized(lock) {
        val removed = slots.removeAll { it.slotId == slotId }
        checkInStatusBySessionKey.keys.removeAll { it.startsWith("${slotId}_") }
        if (removed) {
            persistStateLocked()
            _mutationVersion.value += 1L
        }
    }
}

internal fun InMemoryCampusRepository.listCourseSlotsImpl(courseId: String): List<CourseScheduleSlotItem> =
    synchronized(lock) {
        slots
            .filter { it.courseId == courseId }
            .sortedWith(compareBy<SlotSeed> { it.dayOfWeek }.thenBy { it.startTime })
            .map { toCourseScheduleSlotItem(it) }
    }

internal fun InMemoryCampusRepository.listUfopTemplatesImpl(): List<UfopCurriculumTemplate> =
    synchronized(lock) {
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

internal fun InMemoryCampusRepository.applyUfopTemplateImpl(
    templateId: String,
    semester: String
): Int = synchronized(lock) {
    val template = ufopTemplatesSeed.find { it.id == templateId }
        ?: throw IllegalArgumentException("Template UFOP não encontrado: $templateId")
    val cleanSemester = semester.trim().ifBlank { "2026.2" }

    clearSemesterCoursesAndSlotsLocked(cleanSemester)

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

internal fun InMemoryCampusRepository.applyRoomAllocationIndexImpl(
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

internal fun InMemoryCampusRepository.enrichExistingRoomAllocationsLocked(
    index: UfopRoomAllocationIndex
): Int {
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
