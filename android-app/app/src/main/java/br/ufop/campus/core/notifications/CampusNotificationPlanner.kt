package br.ufop.campus.core.notifications

import br.ufop.campus.core.ffi.NotificationPreferences
import br.ufop.campus.core.ffi.formatAcademicAssessmentTitle
import java.time.DayOfWeek
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/**
 * Motor puro (100% livre de dependências Android) de planejamento de notificações locais offline.
 * Planeja lembretes pré-aula, cartões ao vivo, prazos (3d/1d + adiamento 20h),
 * alertas inteligentes de faltas (65% / 85% sem repetição) e resumo matinal (07:30 em dias letivos).
 */
object CampusNotificationPlanner {

    private val ISO_DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE
    private val TIME_FMT = DateTimeFormatter.ofPattern("HH:mm")
    private const val LIVE_CARD_TICK_MINUTES = 5L

    data class NotificationCourseSnapshot(
        val courseId: String,
        val code: String,
        val name: String,
        val professor: String,
        val buildingAndRoom: String,
        val shortLocation: String,
        val currentAbsences: Int,
        val maxAbsences: Int,
        val section: String = ""
    )

    data class NotificationSlotSnapshot(
        val slotId: String,
        val courseId: String,
        val dayOfWeek: Int,
        val startTime: String,
        val endTime: String,
        val building: String,
        val room: String,
        val shortLocation: String,
        val classWeight: Int = 1
    )

    data class NotificationAssessmentSnapshot(
        val id: String,
        val courseId: String,
        val courseCode: String,
        val courseName: String,
        val title: String,
        val kind: String,
        val dueDate: String,
        val dueTime: String,
        val completed: Boolean
    )

    data class NotificationPlanningInput(
        val activeSemester: String,
        val courses: List<NotificationCourseSnapshot>,
        val slots: List<NotificationSlotSnapshot>,
        val assessments: List<NotificationAssessmentSnapshot>,
        val cancelledSlotDateKeys: Set<String> = emptySet()
    )

    data class PlannedClassReminder(
        val id: String,
        val triggerAt: LocalDateTime,
        val slotId: String,
        val courseId: String,
        val courseCode: String,
        val section: String,
        val isoDate: String,
        val dayOfWeek: Int,
        val startTime: String,
        val endTime: String,
        val fallbackBuildingRaw: String,
        val roomPill: String,
        val title: String,
        val body: String
    )

    enum class LiveCardEventKind { START, UPDATE, END }

    data class PlannedLiveCardEvent(
        val id: String,
        val triggerAt: LocalDateTime,
        val kind: LiveCardEventKind,
        val slotId: String,
        val courseId: String,
        val courseName: String,
        val roomPill: String,
        val startTime: String,
        val endTime: String,
        val countdownLabel: String,
        val isOngoing: Boolean,
        val progressPercent: Int
    )

    data class PlannedDeadlineReminder(
        val id: String,
        val triggerAt: LocalDateTime,
        val assessmentId: String,
        val courseId: String,
        val daysRemaining: Int,
        val title: String,
        val body: String
    )

    data class PlannedMorningSummary(
        val id: String,
        val triggerAt: LocalDateTime,
        val isoDate: String,
        val title: String,
        val body: String
    )

    data class PlannedWidgetBoundaryRefresh(
        val id: String,
        val triggerAt: LocalDateTime
    )

    data class PlannedAbsenceAlert(
        val dedupKey: String,
        val courseId: String,
        val thresholdPercent: Int,
        val usedAbsences: Int,
        val maxAbsences: Int,
        val percentUsed: Int,
        val remainingClassDays: Int,
        val title: String,
        val body: String
    )

    data class AbsenceAlertEvaluationResult(
        val alertsToSend: List<PlannedAbsenceAlert>,
        val updatedAlertedKeys: Set<String>
    )

    data class NotificationSchedulePlan(
        val classReminders: List<PlannedClassReminder> = emptyList(),
        val liveCardEvents: List<PlannedLiveCardEvent> = emptyList(),
        val deadlineReminders: List<PlannedDeadlineReminder> = emptyList(),
        val morningSummaries: List<PlannedMorningSummary> = emptyList(),
        val widgetBoundaries: List<PlannedWidgetBoundaryRefresh> = emptyList()
    )

    private data class ResolvedClassOccurrence(
        val date: LocalDate,
        val isoDate: String,
        val slot: NotificationSlotSnapshot,
        val course: NotificationCourseSnapshot,
        val startDateTime: LocalDateTime,
        val endDateTime: LocalDateTime,
        val roomPill: String,
        val inlineRoom: String
    )

    fun planAll(
        input: NotificationPlanningInput,
        now: LocalDateTime,
        prefs: NotificationPreferences,
        horizonDays: Int = 7
    ): NotificationSchedulePlan {
        val normalizedPrefs = prefs.normalized()
        val safeHorizon = horizonDays.coerceIn(1, 14)
        val coursesById = input.courses.associateBy { it.courseId }
        val today = now.toLocalDate()

        val occurrences = mutableListOf<ResolvedClassOccurrence>()
        for (offset in 0 until safeHorizon) {
            val date = today.plusDays(offset.toLong())
            val isoDate = date.format(ISO_DATE_FMT)
            val dayOfWeek = date.dayOfWeek.value

            val daySlots = input.slots
                .filter { it.dayOfWeek == dayOfWeek && it.courseId in coursesById }
                .sortedBy { it.startTime }

            for (slot in daySlots) {
                val course = coursesById[slot.courseId] ?: continue
                if ("${slot.slotId}@$isoDate" in input.cancelledSlotDateKeys) continue

                val startTime = parseLocalTimeOrNull(slot.startTime) ?: continue
                val endTime = parseLocalTimeOrNull(slot.endTime)?.let { parsedEnd ->
                    if (parsedEnd.isAfter(startTime)) parsedEnd else startTime.plusMinutes(50)
                } ?: startTime.plusMinutes(50)

                val startDt = LocalDateTime.of(date, startTime)
                val endDt = LocalDateTime.of(date, endTime)
                val isEad = isEadText(slot.building, slot.room, slot.shortLocation, course.buildingAndRoom)
                val roomPill = formatRoomPill(
                    building = slot.building,
                    room = slot.room,
                    shortLocation = slot.shortLocation,
                    fallbackBuildingAndRoom = course.buildingAndRoom,
                    isEad = isEad
                )
                val inlineRoom = formatInlineRoom(
                    building = slot.building,
                    room = slot.room,
                    shortLocation = slot.shortLocation,
                    fallbackBuildingAndRoom = course.buildingAndRoom,
                    isEad = isEad
                )

                occurrences.add(
                    ResolvedClassOccurrence(
                        date = date,
                        isoDate = isoDate,
                        slot = slot,
                        course = course,
                        startDateTime = startDt,
                        endDateTime = endDt,
                        roomPill = roomPill,
                        inlineRoom = inlineRoom
                    )
                )
            }
        }

        val classReminders = mutableListOf<PlannedClassReminder>()
        val liveCardEvents = mutableListOf<PlannedLiveCardEvent>()
        val widgetBoundaries = mutableListOf<PlannedWidgetBoundaryRefresh>()

        val leadMinutesLong = normalizedPrefs.classLeadMinutes.toLong()
        for (occ in occurrences) {
            if (occ.startDateTime.isAfter(now)) {
                widgetBoundaries.add(
                    PlannedWidgetBoundaryRefresh(
                        id = "widget_start_${occ.slot.slotId}_${occ.isoDate}",
                        triggerAt = occ.startDateTime
                    )
                )
            }
            if (occ.endDateTime.isAfter(now)) {
                widgetBoundaries.add(
                    PlannedWidgetBoundaryRefresh(
                        id = "widget_end_${occ.slot.slotId}_${occ.isoDate}",
                        triggerAt = occ.endDateTime
                    )
                )
            }

            if (normalizedPrefs.classReminderEnabled) {
                val reminderAt = occ.startDateTime.minusMinutes(leadMinutesLong)
                if (reminderAt.isAfter(now)) {
                    val timeRange = "${occ.slot.startTime}–${occ.slot.endTime}"
                    val profClean = occ.course.professor.trim()
                    val body = buildString {
                        append(occ.roomPill)
                        append(" · ")
                        append(timeRange)
                        if (profClean.isNotEmpty()) {
                            append(" · ")
                            append(profClean)
                        }
                    }
                    classReminders.add(
                        PlannedClassReminder(
                            id = "class_${occ.slot.slotId}_${occ.isoDate}_${normalizedPrefs.classLeadMinutes}",
                            triggerAt = reminderAt,
                            slotId = occ.slot.slotId,
                            courseId = occ.course.courseId,
                            courseCode = occ.course.code,
                            section = occ.course.section,
                            isoDate = occ.isoDate,
                            dayOfWeek = occ.slot.dayOfWeek,
                            startTime = occ.slot.startTime,
                            endTime = occ.slot.endTime,
                            fallbackBuildingRaw = occ.slot.building.ifBlank { occ.course.buildingAndRoom },
                            roomPill = occ.roomPill,
                            title = "${occ.course.name} em ${normalizedPrefs.classLeadMinutes} min",
                            body = body
                        )
                    )
                }
            }

            if (normalizedPrefs.liveActivityEnabled) {
                val windowStart = occ.startDateTime.minusMinutes(leadMinutesLong)
                if (occ.endDateTime.isAfter(now)) {
                    if (windowStart.isAfter(now)) {
                        liveCardEvents.add(
                            buildLiveCardEvent(
                                occ = occ,
                                at = windowStart,
                                kind = LiveCardEventKind.START
                            )
                        )
                    }

                    // Atualiza contagem regressiva e progresso periodicamente na janela pré-aula e durante a aula
                    var tick = if (windowStart.isAfter(now)) {
                        windowStart.plusMinutes(LIVE_CARD_TICK_MINUTES)
                    } else {
                        now.plusMinutes(LIVE_CARD_TICK_MINUTES)
                    }
                    while (tick.isBefore(occ.endDateTime)) {
                        liveCardEvents.add(
                            buildLiveCardEvent(
                                occ = occ,
                                at = tick,
                                kind = LiveCardEventKind.UPDATE
                            )
                        )
                        tick = tick.plusMinutes(LIVE_CARD_TICK_MINUTES)
                    }

                    if (occ.startDateTime.isAfter(now)) {
                        val hasExactStart = liveCardEvents.any {
                            it.slotId == occ.slot.slotId && it.triggerAt == occ.startDateTime
                        }
                        if (!hasExactStart) {
                            liveCardEvents.add(
                                buildLiveCardEvent(
                                    occ = occ,
                                    at = occ.startDateTime,
                                    kind = LiveCardEventKind.UPDATE
                                )
                            )
                        }
                    }

                    liveCardEvents.add(
                        PlannedLiveCardEvent(
                            id = "live_end_${occ.slot.slotId}_${occ.isoDate}",
                            triggerAt = occ.endDateTime,
                            kind = LiveCardEventKind.END,
                            slotId = occ.slot.slotId,
                            courseId = occ.course.courseId,
                            courseName = occ.course.name,
                            roomPill = occ.roomPill,
                            startTime = occ.slot.startTime,
                            endTime = occ.slot.endTime,
                            countdownLabel = "",
                            isOngoing = false,
                            progressPercent = 100
                        )
                    )
                }
            }
        }

        val deadlineReminders = if (normalizedPrefs.deadlineReminderEnabled) {
            planDeadlines(input = input, coursesById = coursesById, now = now)
        } else {
            emptyList()
        }

        val morningSummaries = if (normalizedPrefs.morningSummaryEnabled) {
            planMorningSummaries(
                occurrences = occurrences,
                assessments = input.assessments,
                now = now,
                horizonDays = safeHorizon
            )
        } else {
            emptyList()
        }

        return NotificationSchedulePlan(
            classReminders = classReminders.sortedBy { it.triggerAt },
            liveCardEvents = liveCardEvents.sortedBy { it.triggerAt },
            deadlineReminders = deadlineReminders.sortedBy { it.triggerAt },
            morningSummaries = morningSummaries.sortedBy { it.triggerAt },
            widgetBoundaries = widgetBoundaries.distinctBy { it.triggerAt }.sortedBy { it.triggerAt }
        )
    }

    fun resolveCurrentLiveCard(
        input: NotificationPlanningInput,
        now: LocalDateTime,
        prefs: NotificationPreferences
    ): PlannedLiveCardEvent? {
        val normalizedPrefs = prefs.normalized()
        if (!normalizedPrefs.liveActivityEnabled) return null

        val coursesById = input.courses.associateBy { it.courseId }
        val date = now.toLocalDate()
        val isoDate = date.format(ISO_DATE_FMT)
        val dayOfWeek = date.dayOfWeek.value
        val leadMinutes = normalizedPrefs.classLeadMinutes.toLong()

        val activeCandidates = input.slots
            .filter { it.dayOfWeek == dayOfWeek && it.courseId in coursesById }
            .sortedBy { it.startTime }
            .mapNotNull { slot ->
                if ("${slot.slotId}@$isoDate" in input.cancelledSlotDateKeys) return@mapNotNull null
                val course = coursesById[slot.courseId] ?: return@mapNotNull null
                val startTime = parseLocalTimeOrNull(slot.startTime) ?: return@mapNotNull null
                val endTime = parseLocalTimeOrNull(slot.endTime)?.let { parsedEnd ->
                    if (parsedEnd.isAfter(startTime)) parsedEnd else startTime.plusMinutes(50)
                } ?: startTime.plusMinutes(50)
                val startDt = LocalDateTime.of(date, startTime)
                val endDt = LocalDateTime.of(date, endTime)
                val windowStart = startDt.minusMinutes(leadMinutes)
                if (now.isBefore(windowStart) || !now.isBefore(endDt)) {
                    return@mapNotNull null
                }
                val isEad = isEadText(slot.building, slot.room, slot.shortLocation, course.buildingAndRoom)
                val roomPill = formatRoomPill(
                    building = slot.building,
                    room = slot.room,
                    shortLocation = slot.shortLocation,
                    fallbackBuildingAndRoom = course.buildingAndRoom,
                    isEad = isEad
                )
                val inlineRoom = formatInlineRoom(
                    building = slot.building,
                    room = slot.room,
                    shortLocation = slot.shortLocation,
                    fallbackBuildingAndRoom = course.buildingAndRoom,
                    isEad = isEad
                )
                ResolvedClassOccurrence(
                    date = date,
                    isoDate = isoDate,
                    slot = slot,
                    course = course,
                    startDateTime = startDt,
                    endDateTime = endDt,
                    roomPill = roomPill,
                    inlineRoom = inlineRoom
                )
            }

        val current = activeCandidates.firstOrNull { !now.isBefore(it.startDateTime) }
            ?: activeCandidates.firstOrNull()
            ?: return null

        return buildLiveCardEvent(occ = current, at = now, kind = LiveCardEventKind.UPDATE)
    }

    private fun buildLiveCardEvent(
        occ: ResolvedClassOccurrence,
        at: LocalDateTime,
        kind: LiveCardEventKind
    ): PlannedLiveCardEvent {
        val isOngoing = !at.isBefore(occ.startDateTime) && at.isBefore(occ.endDateTime)
        val countdownLabel = if (isOngoing) {
            "AGORA"
        } else {
            val minsUntil = Duration.between(at, occ.startDateTime).toMinutes().coerceAtLeast(1)
            "em $minsUntil min"
        }
        val progressPercent = if (!isOngoing) {
            0
        } else {
            val totalMins = Duration.between(occ.startDateTime, occ.endDateTime).toMinutes().coerceAtLeast(1)
            val elapsedMins = Duration.between(occ.startDateTime, at).toMinutes().coerceIn(0, totalMins)
            ((elapsedMins * 100L) / totalMins).toInt().coerceIn(0, 100)
        }
        val stamp = at.format(TIME_FMT).replace(":", "")
        return PlannedLiveCardEvent(
            id = "live_${kind.name.lowercase()}_${occ.slot.slotId}_${occ.isoDate}_$stamp",
            triggerAt = at,
            kind = kind,
            slotId = occ.slot.slotId,
            courseId = occ.course.courseId,
            courseName = occ.course.name,
            roomPill = occ.roomPill,
            startTime = occ.slot.startTime,
            endTime = occ.slot.endTime,
            countdownLabel = countdownLabel,
            isOngoing = isOngoing,
            progressPercent = progressPercent
        )
    }

    private fun planDeadlines(
        input: NotificationPlanningInput,
        coursesById: Map<String, NotificationCourseSnapshot>,
        now: LocalDateTime
    ): List<PlannedDeadlineReminder> {
        val list = mutableListOf<PlannedDeadlineReminder>()
        val targetLeadDays = listOf(3L, 1L)

        for (assessment in input.assessments) {
            if (assessment.completed) continue
            val dueDate = parseIsoDateOrNull(assessment.dueDate) ?: continue
            val formattedTitle = formatAcademicAssessmentTitle(assessment.title).ifBlank { assessment.title }
            val course = coursesById[assessment.courseId]
            val codeLabel = assessment.courseCode.trim()
                .ifBlank { course?.code.orEmpty().trim() }
                .ifBlank { assessment.courseName.ifBlank { course?.name.orEmpty() } }
                .ifBlank { "Disciplina" }
            val kindLabel = assessment.kind.trim().ifBlank { "Avaliação" }
            val timeLabel = assessment.dueTime.trim().ifBlank { "18:00" }

            for (daysRem in targetLeadDays) {
                val triggerDate = dueDate.minusDays(daysRem)
                val triggerAt = LocalDateTime.of(triggerDate, LocalTime.of(9, 0))
                if (!triggerAt.isAfter(now)) continue

                val daysInt = daysRem.toInt()
                val title = if (daysInt == 1) {
                    "$formattedTitle amanhã"
                } else {
                    "$formattedTitle em $daysInt dias"
                }
                val daysSuffix = if (daysInt == 1) "1 dia restante" else "$daysInt dias restantes"
                val body = "$codeLabel · $kindLabel às $timeLabel · $daysSuffix"

                list.add(
                    PlannedDeadlineReminder(
                        id = "deadline_${assessment.id}_d$daysInt",
                        triggerAt = triggerAt,
                        assessmentId = assessment.id,
                        courseId = assessment.courseId,
                        daysRemaining = daysInt,
                        title = title,
                        body = body
                    )
                )
            }
        }
        return list
    }

    fun planEveningSnooze(
        now: LocalDateTime,
        assessmentId: String,
        courseId: String,
        title: String,
        body: String
    ): PlannedDeadlineReminder {
        val tonightAt20 = LocalDateTime.of(now.toLocalDate(), LocalTime.of(20, 0))
        val triggerAt = if (tonightAt20.isAfter(now)) {
            tonightAt20
        } else {
            now.plusMinutes(30)
        }
        return PlannedDeadlineReminder(
            id = "deadline_snooze_$assessmentId",
            triggerAt = triggerAt,
            assessmentId = assessmentId,
            courseId = courseId,
            daysRemaining = 0,
            title = title,
            body = body
        )
    }

    private fun planMorningSummaries(
        occurrences: List<ResolvedClassOccurrence>,
        assessments: List<NotificationAssessmentSnapshot>,
        now: LocalDateTime,
        horizonDays: Int
    ): List<PlannedMorningSummary> {
        val byDate = occurrences.groupBy { it.date }
        val today = now.toLocalDate()
        val list = mutableListOf<PlannedMorningSummary>()

        for (offset in 0 until horizonDays) {
            val date = today.plusDays(offset.toLong())
            // Silêncio automático em fins de semana (Sábado/Domingo)
            if (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY) {
                continue
            }
            val triggerAt = LocalDateTime.of(date, LocalTime.of(7, 30))
            if (!triggerAt.isAfter(now)) continue

            // Silêncio automático em dias livres (sem aulas ativas)
            val dayClasses = byDate[date].orEmpty().sortedBy { it.slot.startTime }
            if (dayClasses.isEmpty()) continue

            val isoDate = date.format(ISO_DATE_FMT)
            val dayLabel = formatPortugueseWeekdayTitle(date.dayOfWeek)
            val classesWord = if (dayClasses.size == 1) "aula" else "aulas"
            val title = "$dayLabel no campus: ${dayClasses.size} $classesWord"

            val weekEndIso = date.plusDays(6).format(ISO_DATE_FMT)
            val weekExamsCount = assessments.count { item ->
                !item.completed && item.dueDate >= isoDate && item.dueDate <= weekEndIso
            }

            val slotsSummary = dayClasses.take(2).joinToString(" · ") { occ ->
                "${occ.slot.startTime} ${occ.inlineRoom}"
            }
            val extraSuffix = if (dayClasses.size > 2) " · +${dayClasses.size - 2}" else ""
            val examWord = if (weekExamsCount == 1) "prova" else "provas"
            val body = "$slotsSummary$extraSuffix · $weekExamsCount $examWord esta semana"

            list.add(
                PlannedMorningSummary(
                    id = "morning_$isoDate",
                    triggerAt = triggerAt,
                    isoDate = isoDate,
                    title = title,
                    body = body
                )
            )
        }
        return list
    }

    fun evaluateAbsenceAlerts(
        activeSemester: String,
        courses: List<NotificationCourseSnapshot>,
        slotsByCourse: Map<String, List<NotificationSlotSnapshot>>,
        alreadyAlertedKeys: Set<String>,
        enabled: Boolean
    ): AbsenceAlertEvaluationResult {
        if (!enabled) {
            return AbsenceAlertEvaluationResult(
                alertsToSend = emptyList(),
                updatedAlertedKeys = alreadyAlertedKeys
            )
        }

        val updatedKeys = alreadyAlertedKeys.toMutableSet()
        val alerts = mutableListOf<PlannedAbsenceAlert>()
        val semSlug = activeSemester.trim().ifBlank { "default" }

        for (course in courses) {
            val maxAbs = course.maxAbsences
            if (maxAbs <= 0) continue
            val used = course.currentAbsences.coerceAtLeast(0)
            val pct = ((used * 100.0) / maxAbs.toDouble()).roundToInt().coerceAtLeast(0)

            val key65 = "${semSlug}_${course.courseId}_65"
            val key85 = "${semSlug}_${course.courseId}_85"

            // Se a porcentagem caiu (ex.: usuário ajustou faltas para baixo), re-arma o limiar
            if (pct < 65) updatedKeys.remove(key65)
            if (pct < 85) updatedKeys.remove(key85)

            val crossedThreshold = when {
                pct >= 85 && key85 !in updatedKeys -> 85
                pct in 65..84 && key65 !in updatedKeys -> 65
                else -> null
            }

            if (pct >= 85) {
                updatedKeys.add(key65)
                updatedKeys.add(key85)
            } else if (pct >= 65) {
                updatedKeys.add(key65)
            }

            if (crossedThreshold != null) {
                val remainingAbsences = (maxAbs - used).coerceAtLeast(0)
                val courseSlots = slotsByCourse[course.courseId].orEmpty()
                val avgWeight = if (courseSlots.isEmpty()) {
                    1
                } else {
                    courseSlots.map { it.classWeight.coerceAtLeast(1) }.average().roundToInt().coerceAtLeast(1)
                }
                val remainingDays = (remainingAbsences / avgWeight).coerceAtLeast(0)
                val dedupKey = if (crossedThreshold == 85) key85 else key65
                val dayWord = if (remainingDays == 1) "dia" else "dias"

                alerts.add(
                    PlannedAbsenceAlert(
                        dedupKey = dedupKey,
                        courseId = course.courseId,
                        thresholdPercent = crossedThreshold,
                        usedAbsences = used,
                        maxAbsences = maxAbs,
                        percentUsed = pct,
                        remainingClassDays = remainingDays,
                        title = "Limite próximo em ${course.name}",
                        body = "$used/$maxAbs faltas usadas ($pct%). Restam $remainingDays $dayWord de aula antes de reprovar por falta."
                    )
                )
            }
        }

        return AbsenceAlertEvaluationResult(
            alertsToSend = alerts,
            updatedAlertedKeys = updatedKeys
        )
    }

    internal fun formatRoomPill(
        building: String,
        room: String,
        shortLocation: String,
        fallbackBuildingAndRoom: String,
        isEad: Boolean
    ): String {
        if (isEad) return "EAD"
        val cleanBuilding = simplifyBuildingName(
            building.trim().ifBlank { fallbackBuildingAndRoom.substringBefore("·").trim() }
        )
        val cleanRoom = room.trim().ifBlank {
            if (fallbackBuildingAndRoom.contains("·")) {
                fallbackBuildingAndRoom.substringAfter("·").trim()
            } else {
                ""
            }
        }
        val knownRoomPrefixes = listOf(
            "sala", "lab", "audit", "mini", "anf", "gin", "quadra",
            "bloco", "pavil", "biblio", "online", "ead", "virtual",
            "remoto", "prédio", "predio"
        )
        val formattedRoom = when {
            cleanRoom.isEmpty() -> ""
            knownRoomPrefixes.any { prefix -> cleanRoom.startsWith(prefix, ignoreCase = true) } -> cleanRoom
            else -> "Sala $cleanRoom"
        }
        return when {
            cleanBuilding.isNotEmpty() && formattedRoom.isNotEmpty() -> "$cleanBuilding · $formattedRoom"
            shortLocation.trim().isNotEmpty() -> shortLocation.trim()
            cleanBuilding.isNotEmpty() -> cleanBuilding
            else -> "Campus UFOP"
        }
    }

    internal fun formatInlineRoom(
        building: String,
        room: String,
        shortLocation: String,
        fallbackBuildingAndRoom: String,
        isEad: Boolean
    ): String {
        if (isEad) return "EAD"
        val cleanBuilding = simplifyBuildingName(
            building.trim().ifBlank { fallbackBuildingAndRoom.substringBefore("·").trim() }
        )
        val cleanRoom = room.trim().ifBlank {
            if (fallbackBuildingAndRoom.contains("·")) {
                fallbackBuildingAndRoom.substringAfter("·").trim()
            } else {
                ""
            }
        }
        val roomToken = Regex("""(?:Sala|Lab(?:orat[óo]rio)?\.?)\s+(.+)""", RegexOption.IGNORE_CASE)
            .find(cleanRoom)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
        return when {
            cleanBuilding.isNotEmpty() && !roomToken.isNullOrEmpty() && cleanRoom.startsWith("Sala", ignoreCase = true) ->
                "$cleanBuilding $roomToken"
            cleanBuilding.isNotEmpty() && cleanRoom.isNotEmpty() ->
                "$cleanBuilding $cleanRoom"
            shortLocation.trim().isNotEmpty() ->
                shortLocation.trim().replace(" · ", " ")
            cleanBuilding.isNotEmpty() ->
                cleanBuilding
            else -> "Campus"
        }
    }

    private fun simplifyBuildingName(raw: String): String {
        val trimmed = raw.trim()
        return when {
            trimmed.equals("Bloco de Salas", ignoreCase = true) -> "Bloco"
            trimmed.equals("Escola de Minas", ignoreCase = true) -> "EM"
            trimmed.startsWith("Prédio ", ignoreCase = true) -> trimmed.removePrefix("Prédio ").trim()
            else -> trimmed
        }
    }

    private fun isEadText(vararg values: String): Boolean = values.any { raw ->
        val lower = raw.lowercase()
        lower.contains("ead") || lower.contains("distância") || lower.contains("distancia") || lower.contains("remoto")
    }

    private fun formatPortugueseWeekdayTitle(dayOfWeek: DayOfWeek): String = when (dayOfWeek) {
        DayOfWeek.MONDAY -> "Segunda"
        DayOfWeek.TUESDAY -> "Terça"
        DayOfWeek.WEDNESDAY -> "Quarta"
        DayOfWeek.THURSDAY -> "Quinta"
        DayOfWeek.FRIDAY -> "Sexta"
        DayOfWeek.SATURDAY -> "Sábado"
        DayOfWeek.SUNDAY -> "Domingo"
    }

    private fun parseLocalTimeOrNull(raw: String): LocalTime? {
        val parts = raw.trim().split(":")
        if (parts.size != 2) return null
        val h = parts[0].toIntOrNull() ?: return null
        val m = parts[1].toIntOrNull() ?: return null
        if (h !in 0..23 || m !in 0..59) return null
        return LocalTime.of(h, m)
    }

    private fun parseIsoDateOrNull(raw: String): LocalDate? {
        val trimmed = raw.trim()
        if (trimmed.isEmpty()) return null
        return runCatching { LocalDate.parse(trimmed, ISO_DATE_FMT) }.getOrNull()
    }
}
