package br.ufop.campus.core.ffi

import br.ufop.campus.ui.theme.CampusAccessibilitySettings
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import kotlin.math.roundToInt

internal fun InMemoryCampusRepository.allCampusesLocked(): List<UfopCampusInfo> =
    ufopCampusesSeed.map { seed ->
        val live = campusWeatherById[seed.id]
        seed.copy(
            weatherTempC = live?.tempC ?: seed.weatherTempC,
            weatherCondition = live?.condition ?: seed.weatherCondition,
            isActive = seed.id == activeCampusId
        )
    }

internal fun InMemoryCampusRepository.activeCampusLocked(): UfopCampusInfo {
    val campuses = allCampusesLocked()
    return campuses.find { it.isActive } ?: campuses.first()
}

internal fun InMemoryCampusRepository.setActiveCampusImpl(campusId: String): UfopCampusInfo =
    synchronized(lock) {
        val target = ufopCampusesSeed.find { it.id == campusId } ?: ufopCampusesSeed.first()
        activeCampusId = target.id
        studentProfile = studentProfile.copy(campusName = target.city)
        persistStateLocked()
        _mutationVersion.value += 1L
        activeCampusLocked()
    }

internal fun InMemoryCampusRepository.getStudentProfileImpl(): StudentProfile =
    synchronized(lock) { studentProfile }

internal fun InMemoryCampusRepository.saveStudentProfileImpl(profile: StudentProfile): StudentProfile =
    synchronized(lock) {
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

internal fun InMemoryCampusRepository.listAvailableSemestersImpl(): List<String> = synchronized(lock) {
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

internal fun InMemoryCampusRepository.listAvailableCalendarSemestersImpl(): List<String> =
    synchronized(lock) {
        (calendarEventsBySemester.keys +
            calendarEventsSeed.map { it.semester } +
            listOf(studentProfile.activeSemester.trim().ifBlank { "2026.2" }))
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .distinct()
            .sorted()
    }

internal fun InMemoryCampusRepository.getDarkThemePreferenceImpl(): String =
    synchronized(lock) { darkThemePreference }

internal fun InMemoryCampusRepository.setDarkThemePreferenceImpl(mode: String) {
    synchronized(lock) {
        val normalized = when (mode.trim().uppercase()) {
            "DARK" -> "DARK"
            "LIGHT" -> "LIGHT"
            else -> "SYSTEM"
        }
        darkThemePreference = normalized
        persistStateLocked()
        _mutationVersion.value += 1L
    }
}

internal fun normalizeAccentColorKey(raw: String?): String = when (raw?.trim()?.uppercase()) {
    "GREEN" -> "GREEN"
    "RED" -> "RED"
    "ORANGE" -> "ORANGE"
    "PURPLE" -> "PURPLE"
    "YELLOW" -> "YELLOW"
    "CYAN" -> "CYAN"
    "BROWN" -> "BROWN"
    "PINK" -> "PINK"
    "BLUE" -> "BLUE"
    else -> "RED"
}

internal fun InMemoryCampusRepository.getAccentColorPreferenceImpl(): String =
    synchronized(lock) { accentColorPreference }

internal fun InMemoryCampusRepository.setAccentColorPreferenceImpl(accentKey: String) {
    synchronized(lock) {
        accentColorPreference = normalizeAccentColorKey(accentKey)
        persistStateLocked()
        _mutationVersion.value += 1L
    }
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

internal fun InMemoryCampusRepository.getAccessibilitySettingsImpl(): CampusAccessibilitySettings =
    synchronized(lock) { accessibilitySettings }

internal fun InMemoryCampusRepository.setAccessibilitySettingsImpl(settings: CampusAccessibilitySettings) {
    synchronized(lock) {
        accessibilitySettings = normalizeAccessibilitySettings(settings)
        persistStateLocked()
        _mutationVersion.value += 1L
    }
}

internal fun InMemoryCampusRepository.isGuidedTourCompletedImpl(): Boolean =
    synchronized(lock) { guidedTourCompleted }

internal fun InMemoryCampusRepository.setGuidedTourCompletedImpl(completed: Boolean) {
    synchronized(lock) {
        guidedTourCompleted = completed
        persistStateLocked()
        _mutationVersion.value += 1L
    }
}

internal fun InMemoryCampusRepository.getLastSeenReleaseVersionImpl(): String =
    synchronized(lock) { lastSeenReleaseVersion }

internal fun InMemoryCampusRepository.setLastSeenReleaseVersionImpl(version: String) {
    synchronized(lock) {
        lastSeenReleaseVersion = version.trim()
        persistStateLocked()
        _mutationVersion.value += 1L
    }
}

internal fun InMemoryCampusRepository.getDynamicGridCompletedCodesImpl(matrixId: String): Set<String> =
    synchronized(lock) {
        val cleanMatrixId = matrixId.trim()
        if (cleanMatrixId.isEmpty()) return@synchronized emptySet()
        dynamicGridCompletedByMatrix[cleanMatrixId]?.toSet() ?: emptySet()
    }

internal fun InMemoryCampusRepository.saveDynamicGridCompletedCodesImpl(
    matrixId: String,
    completedCodes: Set<String>
) = synchronized(lock) {
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

internal fun normalizeAttendanceIndicatorStyle(raw: String?): String = when (raw?.trim()?.uppercase()) {
    "CARD_BORDER" -> "CARD_BORDER"
    else -> "BADGE"
}

internal fun InMemoryCampusRepository.getAttendanceIndicatorStyleImpl(): String =
    synchronized(lock) { attendanceIndicatorStyle }

internal fun InMemoryCampusRepository.setAttendanceIndicatorStyleImpl(style: String) {
    synchronized(lock) {
        val normalized = normalizeAttendanceIndicatorStyle(style)
        if (attendanceIndicatorStyle == normalized) return@synchronized
        attendanceIndicatorStyle = normalized
        persistStateLocked()
        _mutationVersion.value += 1L
        onMutation?.invoke()
    }
}

internal fun InMemoryCampusRepository.getSelectedCurriculumVersionImpl(courseCode: String): String? =
    synchronized(lock) {
        val key = courseCode.trim().uppercase()
        if (key.isEmpty()) return@synchronized null
        selectedCurriculumVersionsByCourse[key]
    }

internal fun InMemoryCampusRepository.setSelectedCurriculumVersionImpl(
    courseCode: String,
    version: String
) = synchronized(lock) {
    val key = courseCode.trim().uppercase()
    val cleanVersion = version.trim()
    if (key.isEmpty()) return@synchronized
    val previous = selectedCurriculumVersionsByCourse[key]
    if (cleanVersion.isEmpty()) {
        if (previous == null) return@synchronized
        selectedCurriculumVersionsByCourse.remove(key)
    } else {
        if (previous == cleanVersion) return@synchronized
        selectedCurriculumVersionsByCourse[key] = cleanVersion
    }
    persistStateLocked()
    _mutationVersion.value += 1L
    onMutation?.invoke()
}

internal fun InMemoryCampusRepository.getAllSelectedCurriculumVersionsImpl(): Map<String, String> =
    synchronized(lock) {
        LinkedHashMap(selectedCurriculumVersionsByCourse)
    }

internal fun InMemoryCampusRepository.getDynamicGridAllCompletedImpl(): Map<String, Set<String>> =
    synchronized(lock) {
        val snapshot = linkedMapOf<String, Set<String>>()
        dynamicGridCompletedByMatrix.forEach { (matrixId, codes) ->
            if (codes.isNotEmpty()) {
                snapshot[matrixId] = codes.toSet()
            }
        }
        snapshot
    }

internal fun InMemoryCampusRepository.getExportFolderConfigImpl(): ExportFolderConfig =
    synchronized(lock) {
        ExportFolderConfig(
            treeUri = exportFolderTreeUri,
            displayLabel = exportFolderDisplayLabel
        )
    }

internal fun InMemoryCampusRepository.setExportFolderConfigImpl(
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

internal fun InMemoryCampusRepository.setTotalRequiredHoursImpl(hours: Int) {
    synchronized(lock) {
        totalRequiredHours = hours.coerceIn(100, 10000)
        persistStateLocked()
        _mutationVersion.value += 1L
    }
}

internal fun InMemoryCampusRepository.listFriendSchedulesImpl(): List<FriendScheduleInfo> =
    synchronized(lock) {
        friendSchedules.map { toFriendScheduleInfoLocked(it) }
    }

internal fun InMemoryCampusRepository.importFriendScheduleJsonImpl(
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

    val parsedCourses = mutableListOf<InMemoryCampusRepository.FriendCourseEntry>()
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
            parsedCourses.add(
                InMemoryCampusRepository.FriendCourseEntry(
                    courseId = id,
                    name = name,
                    departmentId = deptId,
                    code = code
                )
            )
        }
    }

    val parsedSlots = mutableListOf<InMemoryCampusRepository.FriendSlotEntry>()
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
                InMemoryCampusRepository.FriendSlotEntry(
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
    val entry = InMemoryCampusRepository.FriendScheduleEntry(
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

internal fun InMemoryCampusRepository.removeFriendScheduleImpl(friendId: String) {
    synchronized(lock) {
        val removed = friendSchedules.removeAll { it.id == friendId }
        if (removed) {
            persistStateLocked()
            _mutationVersion.value += 1L
        }
    }
}

internal fun InMemoryCampusRepository.getFriendTimetableImpl(friendId: String): WeeklyTimetable? =
    synchronized(lock) {
        friendSchedules.find { it.id == friendId }?.let { toFriendScheduleInfoLocked(it).timetable }
    }

private fun toFriendScheduleInfoLocked(
    entry: InMemoryCampusRepository.FriendScheduleEntry
): FriendScheduleInfo {
    val courseById = entry.courses.associateBy { it.courseId }
    val entries = entry.slots.mapNotNull { slot ->
        val fc = courseById[slot.courseId]
        val dept = resolveDepartmentOption(fc?.departmentId ?: "decom")
        val resolvedName = fc?.name ?: slot.courseId
        val resolvedCode = deriveCourseCodeFallback(fc?.code, "", slot.courseId, resolvedName)
        InMemoryCampusRepository.TimetableEntry(
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

internal fun InMemoryCampusRepository.refreshWeatherIfNeededImpl(force: Boolean): UfopCampusInfo {
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

private fun InMemoryCampusRepository.performWeatherFetch(campusId: String) {
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
            campusWeatherById[campusId] = InMemoryCampusRepository.CampusWeather(tempC = tempC, condition = condition)
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
