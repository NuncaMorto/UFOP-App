package br.ufop.campus.core.ffi

import org.json.JSONArray
import org.json.JSONObject

internal fun InMemoryCampusRepository.buildPersistedJsonLocked(): JSONObject {
    val root = JSONObject()
    root.put("schemaVersion", PERSISTED_SCHEMA_VERSION)
    root.put("activeCampusId", activeCampusId)
    root.put("generatedIdCounter", generatedIdCounter)
    root.put("lastWeatherFetchEpochMs", lastWeatherFetchEpochMs)
    root.put("darkThemePreference", darkThemePreference)
    root.put("accentColorPreference", accentColorPreference)
    root.put("accent_color_key", accentColorPreference)
    root.put("attendanceIndicatorStyle", attendanceIndicatorStyle)
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

    val curriculumVersionsJson = JSONObject()
    selectedCurriculumVersionsByCourse.forEach { (courseCode, version) ->
        curriculumVersionsJson.put(courseCode, version)
    }
    root.put("selectedCurriculumVersionsByCourse", curriculumVersionsJson)

    return root
}
