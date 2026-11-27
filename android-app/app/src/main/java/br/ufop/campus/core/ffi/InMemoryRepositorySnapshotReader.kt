package br.ufop.campus.core.ffi

import br.ufop.campus.ui.theme.CampusAccessibilitySettings
import org.json.JSONObject

internal fun InMemoryCampusRepository.restoreStateFromJsonLocked(root: JSONObject) {
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
    }.getOrDefault("RED")

    val loadedAttendanceIndicatorStyle = runCatching {
        val raw = root.optNonNullString("attendanceIndicatorStyle")
        normalizeAttendanceIndicatorStyle(raw)
    }.getOrDefault("BADGE")

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
    restoreCoursesFromJson(root, loadedCourses, loadedNotes, loadedAssessmentsByCourse)

    val loadedSlots = restoreSlotsFromJson(root)
    val loadedSessions = restoreSessionsFromJson(root)
    val loadedCommitments = restoreCommitmentsFromJson(root)
    restoreAssessmentsFromJson(root, loadedAssessmentsByCourse)

    val defaultCampusCity = ufopCampusesSeed.find { it.id == resolvedCampusId }?.city ?: "Ouro Preto"
    val loadedProfile = restoreProfileFromJson(root, defaultCampusCity, loadedCourses.isNotEmpty())
    val loadedFriends = restoreFriendSchedulesFromJson(root)
    val loadedWeather = restoreCampusWeatherFromJson(root)
    val loadedCalendarBySemester = restoreCalendarEventsFromJson(root)

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
    val loadedTranscriptEntries = restoreTranscriptEntriesFromJson(root)

    val loadedGuidedTourCompleted = runCatching {
        root.optBoolean("guidedTourCompleted", false)
    }.getOrDefault(false)
    val loadedLastSeenReleaseVersion = runCatching {
        root.optNonNullString("lastSeenReleaseVersion", "").trim()
    }.getOrDefault("")
    val loadedAccessibilitySettings = restoreAccessibilitySettingsFromJson(root)
    val loadedDynamicGrid = restoreDynamicGridFromJson(root)
    val loadedCurriculumVersions = restoreSelectedCurriculumVersionsFromJson(root)
    val loadedNotificationPreferences = restoreNotificationPreferencesFromJson(root)

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
    attendanceIndicatorStyle = loadedAttendanceIndicatorStyle
    exportFolderTreeUri = loadedExportUri
    exportFolderDisplayLabel = loadedExportLabel
    accessibilitySettings = loadedAccessibilitySettings
    guidedTourCompleted = loadedGuidedTourCompleted
    lastSeenReleaseVersion = loadedLastSeenReleaseVersion
    dynamicGridCompletedByMatrix.clear()
    dynamicGridCompletedByMatrix.putAll(loadedDynamicGrid)
    selectedCurriculumVersionsByCourse.clear()
    selectedCurriculumVersionsByCourse.putAll(loadedCurriculumVersions)
    notificationPreferences = loadedNotificationPreferences
    roomAllocationIndex?.let { index ->
        enrichExistingRoomAllocationsLocked(index)
    }
}

private fun restoreCoursesFromJson(
    root: JSONObject,
    loadedCourses: MutableMap<String, CourseSeed>,
    loadedNotes: MutableMap<String, String>,
    loadedAssessmentsByCourse: MutableMap<String, MutableList<AssessmentItem>>
) {
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
}

private fun restoreSlotsFromJson(root: JSONObject): List<SlotSeed> {
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
    return loadedSlots
}

private fun InMemoryCampusRepository.restoreSessionsFromJson(root: JSONObject): Map<String, AttendanceStatus> {
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
    return loadedSessions
}

private fun restoreCommitmentsFromJson(root: JSONObject): List<CommitmentSeed> {
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
    return loadedCommitments
}

private fun restoreAssessmentsFromJson(
    root: JSONObject,
    loadedAssessmentsByCourse: MutableMap<String, MutableList<AssessmentItem>>
) {
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
}

private fun restoreProfileFromJson(
    root: JSONObject,
    defaultCampusCity: String,
    hasCourses: Boolean
): StudentProfile = runCatching {
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
            onboardingCompleted = profileObj.optBoolean("onboardingCompleted", hasCourses)
        )
    } else {
        // Migração limpa de schema v1: mantém usuário já ativo se já havia disciplinas salvas.
        StudentProfile(
            campusName = defaultCampusCity,
            onboardingCompleted = hasCourses
        )
    }
}.getOrElse {
    StudentProfile(
        campusName = defaultCampusCity,
        onboardingCompleted = hasCourses
    )
}

private fun restoreFriendSchedulesFromJson(root: JSONObject): List<InMemoryCampusRepository.FriendScheduleEntry> {
    val loadedFriends = mutableListOf<InMemoryCampusRepository.FriendScheduleEntry>()
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
                val fCourses = mutableListOf<InMemoryCampusRepository.FriendCourseEntry>()
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
                                InMemoryCampusRepository.FriendCourseEntry(
                                    courseId = cId,
                                    name = cName,
                                    departmentId = resolveDepartmentOption(fc.optNonNullString("departmentId", "decom")).id,
                                    code = deriveCourseCodeFallback(fc.optNonNullString("code"), "", cId, cName)
                                )
                            )
                        }
                    }
                }
                val fSlots = mutableListOf<InMemoryCampusRepository.FriendSlotEntry>()
                val fsArr = obj.optJSONArray("slots")
                if (fsArr != null) {
                    for (j in 0 until fsArr.length()) {
                        runCatching {
                            val fs = fsArr.optJSONObject(j) ?: return@runCatching
                            val sId = fs.optNonNullString("slotId").trim()
                            val cId = fs.optNonNullString("courseId").trim()
                            if (sId.isEmpty() || cId.isEmpty()) return@runCatching
                            fSlots.add(
                                InMemoryCampusRepository.FriendSlotEntry(
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
                    InMemoryCampusRepository.FriendScheduleEntry(
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
    return loadedFriends
}

private fun restoreCampusWeatherFromJson(root: JSONObject): Map<String, InMemoryCampusRepository.CampusWeather> {
    val loadedWeather = mutableMapOf<String, InMemoryCampusRepository.CampusWeather>()
    runCatching {
        val wObj = root.optJSONObject("campusWeather") ?: return@runCatching
        val keys = wObj.keys()
        while (keys.hasNext()) {
            runCatching {
                val cId = keys.next()
                val cw = wObj.optJSONObject(cId) ?: return@runCatching
                if (cw.has("tempC")) {
                    loadedWeather[cId] = InMemoryCampusRepository.CampusWeather(
                        tempC = cw.optInt("tempC", 22),
                        condition = cw.optNonNullString("condition", "Ensolarado").ifBlank { "Ensolarado" }
                    )
                }
            }
        }
    }
    return loadedWeather
}

private fun restoreCalendarEventsFromJson(root: JSONObject): LinkedHashMap<String, MutableList<AcademicCalendarEvent>> {
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
    return loadedCalendarBySemester
}

private fun restoreTranscriptEntriesFromJson(root: JSONObject): List<TranscriptCourseEntry> {
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
    return loadedTranscriptEntries
}

private fun restoreAccessibilitySettingsFromJson(root: JSONObject): CampusAccessibilitySettings =
    runCatching {
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

private fun restoreDynamicGridFromJson(root: JSONObject): LinkedHashMap<String, MutableSet<String>> {
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
    return loadedDynamicGrid
}

private fun restoreSelectedCurriculumVersionsFromJson(root: JSONObject): LinkedHashMap<String, String> {
    val loaded = linkedMapOf<String, String>()
    runCatching {
        val obj = root.optJSONObject("selectedCurriculumVersionsByCourse") ?: return@runCatching
        val keys = obj.keys()
        while (keys.hasNext()) {
            runCatching {
                val rawKey = keys.next()
                val courseCode = rawKey.trim().uppercase()
                if (courseCode.isEmpty() || obj.isNull(rawKey)) return@runCatching
                val version = obj.optString(rawKey, "").trim()
                if (version.isNotEmpty()) {
                    loaded[courseCode] = version
                }
            }
        }
    }
    return loaded
}

private fun restoreNotificationPreferencesFromJson(root: JSONObject): NotificationPreferences =
    runCatching {
        val obj = root.optJSONObject("notificationPreferences") ?: return@runCatching NotificationPreferences()
        NotificationPreferences(
            classReminderEnabled = obj.optBoolean("classReminderEnabled", true),
            classLeadMinutes = obj.optInt("classLeadMinutes", NotificationPreferences.DEFAULT_CLASS_LEAD_MINUTES),
            liveActivityEnabled = obj.optBoolean("liveActivityEnabled", true),
            deadlineReminderEnabled = obj.optBoolean("deadlineReminderEnabled", true),
            absenceAlertEnabled = obj.optBoolean("absenceAlertEnabled", true),
            morningSummaryEnabled = obj.optBoolean("morningSummaryEnabled", true)
        ).normalized()
    }.getOrDefault(NotificationPreferences())
