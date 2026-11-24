package br.ufop.campus.core.ffi

import br.ufop.campus.core.security.SecurePdfProcessor
import org.json.JSONObject
import java.time.Instant

internal fun InMemoryCampusRepository.createAssessmentImpl(
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

internal fun InMemoryCampusRepository.updateAssessmentImpl(
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

internal fun InMemoryCampusRepository.toggleAssessmentCompletedImpl(
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

internal fun InMemoryCampusRepository.deleteAssessmentImpl(assessmentId: String) {
    synchronized(lock) {
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
}

internal fun InMemoryCampusRepository.listAllAssessmentsImpl(
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

internal fun InMemoryCampusRepository.getAcademicProfileSummaryImpl(
    semester: String
): AcademicProfileSummary = synchronized(lock) {
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

internal fun InMemoryCampusRepository.getAcademicCalendarEventsImpl(
    semester: String
): List<AcademicCalendarEvent> = synchronized(lock) {
    getAcademicCalendarEventsLocked(semester)
}

internal fun InMemoryCampusRepository.getAcademicCalendarEventsLocked(
    semester: String
): List<AcademicCalendarEvent> {
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

private fun InMemoryCampusRepository.buildSemesterBoundaryEventsLocked(
    semester: String
): List<AcademicCalendarEvent> {
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

internal fun InMemoryCampusRepository.isAdminUnlockedImpl(): Boolean = synchronized(lock) { adminUnlocked }

internal fun InMemoryCampusRepository.unlockAdminModeImpl(pin: String): Boolean = synchronized(lock) {
    val cleanPin = pin.trim()
    val ok = cleanPin == "UFOP2026" || cleanPin == "2421165"
    if (ok && !adminUnlocked) {
        adminUnlocked = true
        persistStateLocked()
        _mutationVersion.value += 1L
    }
    ok
}

internal fun InMemoryCampusRepository.adminPublishCalendarPdfImpl(
    pdfBytes: ByteArray,
    semester: String
): AcademicCalendarUpdatePackage {
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

internal fun InMemoryCampusRepository.exportCalendarUpdateJsonImpl(): String = synchronized(lock) {
    exportCalendarUpdateJsonLocked()
}

private fun InMemoryCampusRepository.exportCalendarUpdateJsonLocked(): String {
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

internal fun InMemoryCampusRepository.importCalendarUpdateJsonImpl(
    jsonPayload: String
): AcademicCalendarUpdatePackage = synchronized(lock) {
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

internal fun InMemoryCampusRepository.getPendingCalendarUpdateImpl(): AcademicCalendarUpdatePackage? =
    synchronized(lock) { pendingCalendarUpdate }

internal fun InMemoryCampusRepository.applyPendingCalendarUpdateImpl(): Int = synchronized(lock) {
    val pending = pendingCalendarUpdate ?: return@synchronized 0
    val cleanSemester = pending.semester.trim().ifBlank { studentProfile.activeSemester.ifBlank { "2026.2" } }
    calendarEventsBySemester[cleanSemester] = pending.events.sortedBy { it.eventDate }.toMutableList()
    pendingCalendarUpdate = null
    persistStateLocked()
    _mutationVersion.value += 1L
    pending.events.size
}

internal fun InMemoryCampusRepository.getAcademicCreReportImpl(): AcademicCreReport = synchronized(lock) {
    getAcademicCreReportLocked()
}

internal fun InMemoryCampusRepository.updateTranscriptOrCourseGradeImpl(
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

internal fun InMemoryCampusRepository.getAcademicCreReportLocked(): AcademicCreReport {
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
