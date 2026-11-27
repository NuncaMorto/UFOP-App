package br.ufop.campus.core.ffi

import br.ufop.campus.core.security.SecurePdfProcessor

// ── Importador do ATESTADO DE MATRÍCULA UFOP ────────────────────────────────────────────────

internal fun InMemoryCampusRepository.importEnrollmentCertificatePdfImpl(
    pdfBytes: ByteArray,
    completeOnboarding: Boolean
): Int {
    require(pdfBytes.isNotEmpty()) { "Arquivo PDF vazio." }
    return SecurePdfProcessor.withZeroedPdfBytes(pdfBytes) { safeBytes ->
        val (lines, words) = extractPdfLinesAndWords(safeBytes)
        importCertificateFromLinesAndWords(lines, words, completeOnboarding)
    }
}

internal fun InMemoryCampusRepository.importEnrollmentCertificateTextImpl(
    rawText: String,
    completeOnboarding: Boolean
): Int {
    val trimmed = rawText.trim()
    require(trimmed.isNotEmpty()) { "Texto do atestado vazio." }
    val lines = trimmed.lines().map { it.trimEnd() }.filter { it.isNotBlank() }
    return importCertificateFromLinesAndWords(lines, emptyList(), completeOnboarding)
}

private fun InMemoryCampusRepository.importCertificateFromLinesAndWords(
    lines: List<String>,
    words: List<PdfWord>,
    completeOnboarding: Boolean
): Int {
    val meta = parseCertificateMeta(lines)
    val tableRows = parseCourseTable(lines)
    val gridTokens = if (words.isNotEmpty()) {
        parseGridTokensByCoordinates(words).ifEmpty { parseGridTokensFromText(lines) }
    } else {
        parseGridTokensFromText(lines)
    }

    val effectiveRows = tableRows.ifEmpty {
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

private fun InMemoryCampusRepository.applyImportedCertificateLocked(
    meta: ParsedCertificateMeta,
    tableRows: List<ParsedCourseTableRow>,
    gridTokens: List<ParsedGridToken>,
    completeOnboarding: Boolean
): Int {
    val semester = meta.semester.ifBlank { "2026.2" }

    // Remove apenas disciplinas e horários do semestre sendo reimportado (preserva semestres anteriores).
    clearSemesterCoursesAndSlotsLocked(semester)

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

// ── Importador de Plano de Ensino (PDF/Texto) ───────────────────────────────────────────────

internal fun InMemoryCampusRepository.importCourseSyllabusPdfImpl(
    courseId: String,
    pdfBytes: ByteArray
): Int {
    require(pdfBytes.isNotEmpty()) { "Arquivo PDF vazio." }
    return SecurePdfProcessor.withZeroedPdfBytes(pdfBytes) { safeBytes ->
        val rawText = extractPdfTextWithPdfBox(safeBytes)
        importCourseSyllabusTextImpl(courseId, rawText)
    }
}

internal fun InMemoryCampusRepository.importCourseSyllabusTextImpl(
    courseId: String,
    rawText: String
): Int {
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

internal fun InMemoryCampusRepository.importCourseSyllabusDetailedPdfImpl(
    courseId: String,
    pdfBytes: ByteArray
): SyllabusImportReport {
    require(pdfBytes.isNotEmpty()) { "Arquivo PDF vazio." }
    return SecurePdfProcessor.withZeroedPdfBytes(pdfBytes) { safeBytes ->
        val rawText = extractPdfTextWithPdfBox(safeBytes)
        importCourseSyllabusDetailedTextImpl(courseId, rawText)
    }
}

internal fun InMemoryCampusRepository.importCourseSyllabusDetailedTextImpl(
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
            matchedCourseId = currentCourse.courseId,
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

internal fun InMemoryCampusRepository.importSyllabusPdfAutoDetectCourseImpl(
    semester: String,
    pdfBytes: ByteArray
): SyllabusImportReport {
    require(pdfBytes.isNotEmpty()) { "Arquivo PDF vazio." }
    return SecurePdfProcessor.withZeroedPdfBytes(pdfBytes) { safeBytes ->
        val rawText = extractPdfTextWithPdfBox(safeBytes)
        importSyllabusTextAutoDetectCourseImpl(semester, rawText)
    }
}

internal fun InMemoryCampusRepository.importSyllabusTextAutoDetectCourseImpl(
    semester: String,
    rawText: String
): SyllabusImportReport {
    val trimmed = rawText.trim()
    require(trimmed.isNotEmpty()) { "Texto do plano de ensino vazio." }

    val winnerCourse = synchronized(lock) {
        val targetSemester = semester.trim()
        val candidates = courses.values
            .filter { targetSemester.isBlank() || it.semester.equals(targetSemester, ignoreCase = true) }
            .sortedBy { it.name }

        if (candidates.isEmpty()) {
            return@synchronized null to SyllabusImportReport(
                importedCount = 0,
                matchedCourse = false,
                errorMessage = "Nenhuma disciplina matriculada neste semestre ($targetSemester) para vincular o plano."
            )
        }

        val evals = candidates.map { course ->
            course to matchSyllabusToCourse(course, trimmed, notesByCourseId)
        }
        val matchedPairs = evals.filter { it.second.matched }

        val codeMatchedPairs = matchedPairs.filter { (course, matchRes) ->
            val candidateCode = extractRealUfopCourseCode(course.code)
                ?: extractRealUfopCourseCode(course.name.substringBefore(" "))
                ?: Regex("""^([a-z]{2,4}\d{3,4}[a-z]?)(?:_|$)""", RegexOption.IGNORE_CASE)
                    .find(course.courseId)?.groupValues?.getOrNull(1)?.let { extractRealUfopCourseCode(it) }
            val detected = matchRes.detectedCode?.trim()
            candidateCode != null && detected != null && candidateCode.equals(detected, ignoreCase = true)
        }

        val resolvedWinner: CourseSeed? = when {
            codeMatchedPairs.size == 1 -> codeMatchedPairs.first().first
            matchedPairs.size == 1 -> matchedPairs.first().first
            matchedPairs.isEmpty() -> {
                val sample = evals.first().second
                val detectedInfo = listOfNotNull(
                    sample.detectedCode,
                    sample.detectedTitle
                ).joinToString(" · ").ifBlank { "disciplina não reconhecida" }
                return@synchronized null to SyllabusImportReport(
                    importedCount = 0,
                    matchedCourse = false,
                    matchedCourseCode = sample.detectedCode,
                    matchedCourseTitle = sample.detectedTitle,
                    errorMessage = "Não foi possível vincular este Plano de Ensino ($detectedInfo) a nenhuma disciplina matriculada em $targetSemester. Nenhuma alteração foi feita."
                )
            }
            else -> {
                return@synchronized null to SyllabusImportReport(
                    importedCount = 0,
                    matchedCourse = false,
                    matchedCourseCode = matchedPairs.first().second.detectedCode,
                    matchedCourseTitle = matchedPairs.first().second.detectedTitle,
                    errorMessage = "Mais de uma disciplina matriculada corresponde a este Plano de Ensino. Selecione a disciplina manualmente abaixo ou dentro dos detalhes dela."
                )
            }
        }
        resolvedWinner to null
    }

    val earlyFailure = winnerCourse.second
    val winner = winnerCourse.first ?: return earlyFailure!!
    return importCourseSyllabusDetailedTextImpl(winner.courseId, trimmed).copy(
        matchedCourseId = winner.courseId,
        matchedCourseTitle = winner.name
    )
}

private fun InMemoryCampusRepository.importSyllabusAssessmentsLocked(
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

// ── Importador do Histórico Escolar UFOP ────────────────────────────────────────────────────

internal fun InMemoryCampusRepository.importAcademicTranscriptPdfImpl(
    pdfBytes: ByteArray
): AcademicCreReport {
    require(pdfBytes.isNotEmpty()) { "Arquivo PDF vazio." }
    val rawText = SecurePdfProcessor.withZeroedPdfBytes(pdfBytes) { safeBytes ->
        extractPdfTextWithPdfBox(safeBytes)
    }
    return importAcademicTranscriptTextImpl(rawText)
}

internal fun InMemoryCampusRepository.importAcademicTranscriptTextImpl(
    rawText: String
): AcademicCreReport {
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

private fun InMemoryCampusRepository.applyImportedTranscriptLocked(
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
