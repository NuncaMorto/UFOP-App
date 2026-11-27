package br.ufop.campus.core.notifications

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import br.ufop.campus.MainActivity
import br.ufop.campus.R
import br.ufop.campus.core.ffi.AttendanceStatus
import br.ufop.campus.core.ffi.CampusCoreRepository
import br.ufop.campus.core.ffi.CampusRepositoryHolder
import br.ufop.campus.core.ffi.NotificationPreferences
import br.ufop.campus.core.map.extractCourseSectionFromNotes
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.Executors

/**
 * Orquestrador local 100% offline de alarmes e notificações do Vida Acadêmica no Android.
 * Usa exclusivamente AlarmManager + BroadcastReceiver + NotificationCompat (sem servidor e sem FCM).
 */
object CampusNotificationScheduler {

    const val ACTION_ALARM_CLASS_REMINDER = "io.github.nuncamorto.vidaacademica.notif.ALARM_CLASS"
    const val ACTION_ALARM_LIVE_CARD = "io.github.nuncamorto.vidaacademica.notif.ALARM_LIVE"
    const val ACTION_ALARM_DEADLINE = "io.github.nuncamorto.vidaacademica.notif.ALARM_DEADLINE"
    const val ACTION_ALARM_MORNING_SUMMARY = "io.github.nuncamorto.vidaacademica.notif.ALARM_MORNING"
    const val ACTION_ALARM_WIDGET_BOUNDARY = "io.github.nuncamorto.vidaacademica.notif.ALARM_WIDGET"

    const val ACTION_INLINE_ATTEND = "io.github.nuncamorto.vidaacademica.notif.INLINE_ATTEND"
    const val ACTION_INLINE_ABSENT = "io.github.nuncamorto.vidaacademica.notif.INLINE_ABSENT"
    const val ACTION_INLINE_COMPLETE_ASSESSMENT = "io.github.nuncamorto.vidaacademica.notif.INLINE_COMPLETE"
    const val ACTION_INLINE_SNOOZE_EVENING = "io.github.nuncamorto.vidaacademica.notif.INLINE_SNOOZE"

    const val EXTRA_ALARM_ID = "extra_alarm_id"
    const val EXTRA_NOTIF_ID = "extra_notif_id"
    const val EXTRA_TITLE = "extra_title"
    const val EXTRA_BODY = "extra_body"
    const val EXTRA_SLOT_ID = "extra_slot_id"
    const val EXTRA_COURSE_ID = "extra_course_id"
    const val EXTRA_COURSE_CODE = "extra_course_code"
    const val EXTRA_COURSE_SECTION = "extra_course_section"
    const val EXTRA_ISO_DATE = "extra_iso_date"
    const val EXTRA_WEEKDAY = "extra_weekday"
    const val EXTRA_START_TIME = "extra_start_time"
    const val EXTRA_END_TIME = "extra_end_time"
    const val EXTRA_FALLBACK_BUILDING = "extra_fallback_building"
    const val EXTRA_ROOM_PILL = "extra_room_pill"
    const val EXTRA_LIVE_KIND = "extra_live_kind"
    const val EXTRA_LIVE_ONGOING = "extra_live_ongoing"
    const val EXTRA_LIVE_PROGRESS = "extra_live_progress"
    const val EXTRA_LIVE_COUNTDOWN = "extra_live_countdown"
    const val EXTRA_ASSESSMENT_ID = "extra_assessment_id"

    const val LIVE_CARD_NOTIFICATION_ID = 41001
    private const val CLASS_NOTIFICATION_BASE_ID = 42000
    private const val DEADLINE_NOTIFICATION_BASE_ID = 43000
    private const val ABSENCE_NOTIFICATION_BASE_ID = 44000
    private const val MORNING_NOTIFICATION_BASE_ID = 45000

    private val ISO_DATE_FMT = DateTimeFormatter.ISO_LOCAL_DATE
    private val scheduleLock = Any()
    private val executor = Executors.newSingleThreadExecutor { runnable ->
        Thread(runnable, "ufop-notif-scheduler").apply { isDaemon = true }
    }

    @JvmStatic
    fun onRepositoryStateChanged(context: Context) {
        val appContext = context.applicationContext ?: context
        executor.execute {
            runCatching {
                rescheduleAll(appContext)
            }
        }
    }

    @JvmStatic
    fun rescheduleAll(
        context: Context,
        repository: CampusCoreRepository = CampusRepositoryHolder.getOrCreate(context)
    ) {
        val appContext = context.applicationContext ?: context
        synchronized(scheduleLock) {
            CampusNotificationChannels.ensureChannels(appContext)

            val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            if (alarmManager != null) {
                cancelPreviouslyTrackedAlarms(appContext, alarmManager)
            }

            val prefs = CampusRepositoryHolder.loadNotificationPreferences(appContext, repository)
            val now = LocalDateTime.now()
            val input = extractPlanningInput(
                repository = repository,
                today = now.toLocalDate(),
                horizonDays = 7
            )
            val plan = CampusNotificationPlanner.planAll(
                input = input,
                now = now,
                prefs = prefs,
                horizonDays = 7
            )

            val trackedSpecs = mutableListOf<CampusNotificationTrackingStore.TrackedAlarmSpec>()
            val zone = ZoneId.systemDefault()

            if (alarmManager != null) {
                for (reminder in plan.classReminders) {
                    val reqCode = stableRequestCode("class", reminder.id)
                    val intent = Intent(appContext, CampusNotificationAlarmReceiver::class.java).apply {
                        action = ACTION_ALARM_CLASS_REMINDER
                        putExtra(EXTRA_ALARM_ID, reminder.id)
                        putExtra(EXTRA_NOTIF_ID, notificationIdFor("class", reminder.id, CLASS_NOTIFICATION_BASE_ID))
                        putExtra(EXTRA_TITLE, reminder.title)
                        putExtra(EXTRA_BODY, reminder.body)
                        putExtra(EXTRA_SLOT_ID, reminder.slotId)
                        putExtra(EXTRA_COURSE_ID, reminder.courseId)
                        putExtra(EXTRA_COURSE_CODE, reminder.courseCode)
                        putExtra(EXTRA_COURSE_SECTION, reminder.section)
                        putExtra(EXTRA_ISO_DATE, reminder.isoDate)
                        putExtra(EXTRA_WEEKDAY, reminder.dayOfWeek)
                        putExtra(EXTRA_START_TIME, reminder.startTime)
                        putExtra(EXTRA_END_TIME, reminder.endTime)
                        putExtra(EXTRA_FALLBACK_BUILDING, reminder.fallbackBuildingRaw)
                        putExtra(EXTRA_ROOM_PILL, reminder.roomPill)
                    }
                    val pi = PendingIntent.getBroadcast(
                        appContext,
                        reqCode,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    val triggerMillis = reminder.triggerAt.atZone(zone).toInstant().toEpochMilli()
                    scheduleAlarmSafely(alarmManager, triggerMillis, pi)
                    trackedSpecs.add(CampusNotificationTrackingStore.TrackedAlarmSpec(reqCode, ACTION_ALARM_CLASS_REMINDER))
                }

                for (liveEvent in plan.liveCardEvents) {
                    val reqCode = stableRequestCode("live", liveEvent.id)
                    val intent = Intent(appContext, CampusNotificationAlarmReceiver::class.java).apply {
                        action = ACTION_ALARM_LIVE_CARD
                        putExtra(EXTRA_ALARM_ID, liveEvent.id)
                        putExtra(EXTRA_LIVE_KIND, liveEvent.kind.name)
                        putExtra(EXTRA_SLOT_ID, liveEvent.slotId)
                        putExtra(EXTRA_COURSE_ID, liveEvent.courseId)
                        putExtra(EXTRA_TITLE, liveEvent.courseName)
                        putExtra(EXTRA_ROOM_PILL, liveEvent.roomPill)
                        putExtra(EXTRA_START_TIME, liveEvent.startTime)
                        putExtra(EXTRA_END_TIME, liveEvent.endTime)
                        putExtra(EXTRA_LIVE_COUNTDOWN, liveEvent.countdownLabel)
                        putExtra(EXTRA_LIVE_ONGOING, liveEvent.isOngoing)
                        putExtra(EXTRA_LIVE_PROGRESS, liveEvent.progressPercent)
                    }
                    val pi = PendingIntent.getBroadcast(
                        appContext,
                        reqCode,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    val triggerMillis = liveEvent.triggerAt.atZone(zone).toInstant().toEpochMilli()
                    scheduleAlarmSafely(alarmManager, triggerMillis, pi)
                    trackedSpecs.add(CampusNotificationTrackingStore.TrackedAlarmSpec(reqCode, ACTION_ALARM_LIVE_CARD))
                }

                for (deadline in plan.deadlineReminders) {
                    val reqCode = stableRequestCode("deadline", deadline.id)
                    val intent = Intent(appContext, CampusNotificationAlarmReceiver::class.java).apply {
                        action = ACTION_ALARM_DEADLINE
                        putExtra(EXTRA_ALARM_ID, deadline.id)
                        putExtra(EXTRA_NOTIF_ID, notificationIdFor("deadline", deadline.id, DEADLINE_NOTIFICATION_BASE_ID))
                        putExtra(EXTRA_ASSESSMENT_ID, deadline.assessmentId)
                        putExtra(EXTRA_COURSE_ID, deadline.courseId)
                        putExtra(EXTRA_TITLE, deadline.title)
                        putExtra(EXTRA_BODY, deadline.body)
                    }
                    val pi = PendingIntent.getBroadcast(
                        appContext,
                        reqCode,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    val triggerMillis = deadline.triggerAt.atZone(zone).toInstant().toEpochMilli()
                    scheduleAlarmSafely(alarmManager, triggerMillis, pi)
                    trackedSpecs.add(CampusNotificationTrackingStore.TrackedAlarmSpec(reqCode, ACTION_ALARM_DEADLINE))
                }

                for (morning in plan.morningSummaries) {
                    val reqCode = stableRequestCode("morning", morning.id)
                    val intent = Intent(appContext, CampusNotificationAlarmReceiver::class.java).apply {
                        action = ACTION_ALARM_MORNING_SUMMARY
                        putExtra(EXTRA_ALARM_ID, morning.id)
                        putExtra(EXTRA_NOTIF_ID, notificationIdFor("morning", morning.id, MORNING_NOTIFICATION_BASE_ID))
                        putExtra(EXTRA_ISO_DATE, morning.isoDate)
                        putExtra(EXTRA_TITLE, morning.title)
                        putExtra(EXTRA_BODY, morning.body)
                    }
                    val pi = PendingIntent.getBroadcast(
                        appContext,
                        reqCode,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    val triggerMillis = morning.triggerAt.atZone(zone).toInstant().toEpochMilli()
                    scheduleAlarmSafely(alarmManager, triggerMillis, pi)
                    trackedSpecs.add(CampusNotificationTrackingStore.TrackedAlarmSpec(reqCode, ACTION_ALARM_MORNING_SUMMARY))
                }

                for (boundary in plan.widgetBoundaries) {
                    val reqCode = stableRequestCode("widget", boundary.id)
                    val intent = Intent(appContext, CampusNotificationAlarmReceiver::class.java).apply {
                        action = ACTION_ALARM_WIDGET_BOUNDARY
                        putExtra(EXTRA_ALARM_ID, boundary.id)
                    }
                    val pi = PendingIntent.getBroadcast(
                        appContext,
                        reqCode,
                        intent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    val triggerMillis = boundary.triggerAt.atZone(zone).toInstant().toEpochMilli()
                    scheduleAlarmSafely(alarmManager, triggerMillis, pi)
                    trackedSpecs.add(CampusNotificationTrackingStore.TrackedAlarmSpec(reqCode, ACTION_ALARM_WIDGET_BOUNDARY))
                }
            }

            CampusNotificationTrackingStore.saveScheduledAlarms(appContext, trackedSpecs)

            val activeLive = CampusNotificationPlanner.resolveCurrentLiveCard(
                input = input,
                now = now,
                prefs = prefs
            )
            if (activeLive != null) {
                postOrUpdateLiveCard(appContext, activeLive)
            } else {
                cancelLiveCard(appContext)
            }

            checkAndPostAbsenceAlerts(appContext, repository, prefs)
        }
    }

    @JvmStatic
    fun checkAndPostAbsenceAlerts(
        context: Context,
        repository: CampusCoreRepository = CampusRepositoryHolder.getOrCreate(context),
        prefsOverride: NotificationPreferences? = null
    ) {
        val appContext = context.applicationContext ?: context
        CampusNotificationChannels.ensureChannels(appContext)
        val prefs = (prefsOverride ?: CampusRepositoryHolder.loadNotificationPreferences(appContext, repository)).normalized()
        val input = extractPlanningInput(
            repository = repository,
            today = LocalDate.now(),
            horizonDays = 1
        )
        val slotsByCourse = input.slots.groupBy { it.courseId }
        val existingKeys = CampusNotificationTrackingStore.loadAbsenceAlertedKeys(appContext)
        val evaluation = CampusNotificationPlanner.evaluateAbsenceAlerts(
            activeSemester = input.activeSemester,
            courses = input.courses,
            slotsByCourse = slotsByCourse,
            alreadyAlertedKeys = existingKeys,
            enabled = prefs.absenceAlertEnabled
        )
        CampusNotificationTrackingStore.saveAbsenceAlertedKeys(appContext, evaluation.updatedAlertedKeys)

        for (alert in evaluation.alertsToSend) {
            postAbsenceAlert(appContext, alert)
        }
    }

    fun scheduleEveningSnooze(
        context: Context,
        assessmentId: String,
        courseId: String,
        title: String,
        body: String
    ) {
        val appContext = context.applicationContext ?: context
        CampusNotificationChannels.ensureChannels(appContext)
        val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val snoozed = CampusNotificationPlanner.planEveningSnooze(
            now = LocalDateTime.now(),
            assessmentId = assessmentId,
            courseId = courseId,
            title = title,
            body = body
        )
        val reqCode = stableRequestCode("deadline_snooze", snoozed.id)
        val intent = Intent(appContext, CampusNotificationAlarmReceiver::class.java).apply {
            action = ACTION_ALARM_DEADLINE
            putExtra(EXTRA_ALARM_ID, snoozed.id)
            putExtra(EXTRA_NOTIF_ID, notificationIdFor("deadline", snoozed.id, DEADLINE_NOTIFICATION_BASE_ID))
            putExtra(EXTRA_ASSESSMENT_ID, snoozed.assessmentId)
            putExtra(EXTRA_COURSE_ID, snoozed.courseId)
            putExtra(EXTRA_TITLE, snoozed.title)
            putExtra(EXTRA_BODY, snoozed.body)
        }
        val pi = PendingIntent.getBroadcast(
            appContext,
            reqCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val triggerMillis = snoozed.triggerAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        scheduleAlarmSafely(alarmManager, triggerMillis, pi)
        CampusNotificationTrackingStore.addScheduledAlarm(
            appContext,
            CampusNotificationTrackingStore.TrackedAlarmSpec(reqCode, ACTION_ALARM_DEADLINE)
        )
    }

    @JvmStatic
    fun cancelAllAndClearTracking(context: Context) {
        val appContext = context.applicationContext ?: context
        synchronized(scheduleLock) {
            val alarmManager = appContext.getSystemService(Context.ALARM_SERVICE) as? AlarmManager
            if (alarmManager != null) {
                cancelPreviouslyTrackedAlarms(appContext, alarmManager)
            }
            runCatching {
                NotificationManagerCompat.from(appContext).cancelAll()
            }
            CampusNotificationTrackingStore.clearAll(appContext)
        }
    }

    internal fun extractPlanningInput(
        repository: CampusCoreRepository,
        today: LocalDate,
        horizonDays: Int
    ): CampusNotificationPlanner.NotificationPlanningInput {
        val profile = runCatching { repository.getStudentProfile() }.getOrNull()
        val activeSemester = profile?.activeSemester?.trim()?.ifBlank { "2026.2" } ?: "2026.2"
        val summaries = runCatching { repository.listDisciplines(activeSemester, null) }
            .getOrDefault(emptyList())

        val courses = mutableListOf<CampusNotificationPlanner.NotificationCourseSnapshot>()
        val slots = mutableListOf<CampusNotificationPlanner.NotificationSlotSnapshot>()

        for (summary in summaries) {
            val detail = runCatching { repository.getDisciplineDetail(summary.courseId) }.getOrNull()
            val courseSlots = runCatching { repository.listCourseSlots(summary.courseId) }.getOrDefault(emptyList())
            val section = detail?.notes?.let { extractCourseSectionFromNotes(it) }.orEmpty()

            courses.add(
                CampusNotificationPlanner.NotificationCourseSnapshot(
                    courseId = summary.courseId,
                    code = detail?.code?.ifBlank { summary.code } ?: summary.code,
                    name = summary.name,
                    professor = detail?.professor.orEmpty(),
                    buildingAndRoom = detail?.buildingAndRoom.orEmpty(),
                    shortLocation = detail?.shortLocation.orEmpty(),
                    currentAbsences = summary.currentAbsences,
                    maxAbsences = summary.maxAbsences,
                    section = section
                )
            )

            for (slot in courseSlots) {
                slots.add(
                    CampusNotificationPlanner.NotificationSlotSnapshot(
                        slotId = slot.slotId,
                        courseId = slot.courseId,
                        dayOfWeek = slot.dayOfWeek,
                        startTime = slot.startTime,
                        endTime = slot.endTime,
                        building = slot.building,
                        room = slot.room,
                        shortLocation = slot.shortLocation,
                        classWeight = estimateSlotClassWeight(slot.startTime, slot.endTime)
                    )
                )
            }
        }

        val cancelledKeys = mutableSetOf<String>()
        for (offset in 0 until horizonDays.coerceIn(1, 14)) {
            val iso = today.plusDays(offset.toLong()).format(ISO_DATE_FMT)
            val dayOverview = runCatching { repository.getAgendaDay(iso) }.getOrNull() ?: continue
            for (item in dayOverview.items) {
                val sid = item.slotId
                if (!sid.isNullOrBlank() && item.status == AttendanceStatus.CANCELLED) {
                    cancelledKeys.add("$sid@$iso")
                }
            }
        }

        val courseById = courses.associateBy { it.courseId }
        val assessments = runCatching { repository.listAllAssessments(activeSemester, null) }
            .getOrDefault(emptyList())
            .map { entry ->
                CampusNotificationPlanner.NotificationAssessmentSnapshot(
                    id = entry.assessment.id,
                    courseId = entry.assessment.courseId,
                    courseCode = courseById[entry.assessment.courseId]?.code.orEmpty(),
                    courseName = entry.courseName,
                    title = entry.assessment.title,
                    kind = entry.assessment.kind,
                    dueDate = entry.assessment.dueDate,
                    dueTime = entry.assessment.dueTime,
                    completed = entry.assessment.completed
                )
            }

        return CampusNotificationPlanner.NotificationPlanningInput(
            activeSemester = activeSemester,
            courses = courses,
            slots = slots,
            assessments = assessments,
            cancelledSlotDateKeys = cancelledKeys
        )
    }

    internal fun postClassReminder(
        context: Context,
        notifId: Int,
        slotId: String,
        courseId: String,
        courseCode: String,
        section: String,
        isoDate: String,
        dayOfWeek: Int,
        startTime: String,
        fallbackBuildingRaw: String,
        title: String,
        body: String
    ) {
        if (!CampusNotificationChannels.hasRuntimeNotificationPermission(context)) return

        val openAgendaIntent = buildTabPendingIntent(
            context = context,
            targetTab = MainActivity.WIDGET_TARGET_AGENDA,
            requestCode = notifId * 10 + 1
        )
        val attendIntent = PendingIntent.getBroadcast(
            context,
            notifId * 10 + 2,
            Intent(context, CampusNotificationActionReceiver::class.java).apply {
                action = ACTION_INLINE_ATTEND
                putExtra(EXTRA_NOTIF_ID, notifId)
                putExtra(EXTRA_SLOT_ID, slotId)
                putExtra(EXTRA_COURSE_ID, courseId)
                putExtra(EXTRA_ISO_DATE, isoDate)
                putExtra(EXTRA_TITLE, title)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val absentIntent = PendingIntent.getBroadcast(
            context,
            notifId * 10 + 3,
            Intent(context, CampusNotificationActionReceiver::class.java).apply {
                action = ACTION_INLINE_ABSENT
                putExtra(EXTRA_NOTIF_ID, notifId)
                putExtra(EXTRA_SLOT_ID, slotId)
                putExtra(EXTRA_COURSE_ID, courseId)
                putExtra(EXTRA_ISO_DATE, isoDate)
                putExtra(EXTRA_TITLE, title)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val mapIntent = buildMapFocusPendingIntent(
            context = context,
            requestCode = notifId * 10 + 4,
            courseCode = courseCode,
            section = section,
            weekday = dayOfWeek,
            startTime = startTime,
            fallbackBuildingRaw = fallbackBuildingRaw
        )

        val notification = NotificationCompat.Builder(context, CampusNotificationChannels.CHANNEL_CLASS_REMINDERS)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openAgendaIntent)
            .setAutoCancel(true)
            .addAction(0, "Vou à aula", attendIntent)
            .addAction(0, "Vou faltar", absentIntent)
            .addAction(0, "Ver no mapa", mapIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(notifId, notification)
        }
    }

    internal fun postClassCheckInConfirmation(
        context: Context,
        notifId: Int,
        title: String,
        confirmationText: String
    ) {
        if (!CampusNotificationChannels.hasRuntimeNotificationPermission(context)) return
        val openAgenda = buildTabPendingIntent(context, MainActivity.WIDGET_TARGET_AGENDA, notifId * 10 + 5)
        val notification = NotificationCompat.Builder(context, CampusNotificationChannels.CHANNEL_CLASS_REMINDERS)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(confirmationText)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openAgenda)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(notifId, notification)
        }
    }

    internal fun postOrUpdateLiveCard(
        context: Context,
        event: CampusNotificationPlanner.PlannedLiveCardEvent
    ) {
        if (!CampusNotificationChannels.hasRuntimeNotificationPermission(context)) return
        val openAgenda = buildTabPendingIntent(context, MainActivity.WIDGET_TARGET_AGENDA, LIVE_CARD_NOTIFICATION_ID)
        val statusPrefix = event.countdownLabel.ifBlank { if (event.isOngoing) "AGORA" else "Em breve" }
        val title = "$statusPrefix · ${event.courseName}"
        val content = "${event.roomPill} · ${event.startTime}–${event.endTime}"

        val notification = NotificationCompat.Builder(context, CampusNotificationChannels.CHANNEL_LIVE_CARD)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(content)
            .setSubText(event.roomPill)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setProgress(100, event.progressPercent.coerceIn(0, 100), false)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openAgenda)
            .build()

        CampusNotificationTrackingStore.setActiveLiveSlotId(context, event.slotId)
        runCatching {
            NotificationManagerCompat.from(context).notify(LIVE_CARD_NOTIFICATION_ID, notification)
        }
    }

    internal fun cancelLiveCard(context: Context) {
        CampusNotificationTrackingStore.setActiveLiveSlotId(context, null)
        runCatching {
            NotificationManagerCompat.from(context).cancel(LIVE_CARD_NOTIFICATION_ID)
        }
    }

    internal fun postDeadlineReminder(
        context: Context,
        notifId: Int,
        assessmentId: String,
        courseId: String,
        title: String,
        body: String
    ) {
        if (!CampusNotificationChannels.hasRuntimeNotificationPermission(context)) return
        val openActivities = buildTabPendingIntent(context, MainActivity.WIDGET_TARGET_ACTIVITIES, notifId * 10 + 1)
        val completeIntent = PendingIntent.getBroadcast(
            context,
            notifId * 10 + 2,
            Intent(context, CampusNotificationActionReceiver::class.java).apply {
                action = ACTION_INLINE_COMPLETE_ASSESSMENT
                putExtra(EXTRA_NOTIF_ID, notifId)
                putExtra(EXTRA_ASSESSMENT_ID, assessmentId)
                putExtra(EXTRA_TITLE, title)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val snoozeIntent = PendingIntent.getBroadcast(
            context,
            notifId * 10 + 3,
            Intent(context, CampusNotificationActionReceiver::class.java).apply {
                action = ACTION_INLINE_SNOOZE_EVENING
                putExtra(EXTRA_NOTIF_ID, notifId)
                putExtra(EXTRA_ASSESSMENT_ID, assessmentId)
                putExtra(EXTRA_COURSE_ID, courseId)
                putExtra(EXTRA_TITLE, title)
                putExtra(EXTRA_BODY, body)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CampusNotificationChannels.CHANNEL_DEADLINES)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openActivities)
            .setAutoCancel(true)
            .addAction(0, "Marcar concluída", completeIntent)
            .addAction(0, "Lembrar à noite", snoozeIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(notifId, notification)
        }
    }

    internal fun postDeadlineCompletedConfirmation(
        context: Context,
        notifId: Int,
        title: String
    ) {
        if (!CampusNotificationChannels.hasRuntimeNotificationPermission(context)) return
        val openActivities = buildTabPendingIntent(context, MainActivity.WIDGET_TARGET_ACTIVITIES, notifId * 10 + 4)
        val notification = NotificationCompat.Builder(context, CampusNotificationChannels.CHANNEL_DEADLINES)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText("✓ Avaliação concluída")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openActivities)
            .setAutoCancel(true)
            .setOnlyAlertOnce(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(notifId, notification)
        }
    }

    internal fun postMorningSummary(
        context: Context,
        notifId: Int,
        title: String,
        body: String
    ) {
        if (!CampusNotificationChannels.hasRuntimeNotificationPermission(context)) return
        val openAgenda = buildTabPendingIntent(context, MainActivity.WIDGET_TARGET_AGENDA, notifId)
        val notification = NotificationCompat.Builder(context, CampusNotificationChannels.CHANNEL_MORNING_SUMMARY)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openAgenda)
            .setAutoCancel(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(notifId, notification)
        }
    }

    internal fun postAbsenceAlert(
        context: Context,
        alert: CampusNotificationPlanner.PlannedAbsenceAlert
    ) {
        if (!CampusNotificationChannels.hasRuntimeNotificationPermission(context)) return
        val notifId = notificationIdFor("absence", alert.dedupKey, ABSENCE_NOTIFICATION_BASE_ID)
        val openDisciplines = buildTabPendingIntent(context, MainActivity.WIDGET_TARGET_DISCIPLINES, notifId)
        val notification = NotificationCompat.Builder(context, CampusNotificationChannels.CHANNEL_ABSENCE_ALERTS)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle(alert.title)
            .setContentText(alert.body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(alert.body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(openDisciplines)
            .setAutoCancel(true)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(notifId, notification)
        }
    }

    private fun cancelPreviouslyTrackedAlarms(context: Context, alarmManager: AlarmManager) {
        val previous = CampusNotificationTrackingStore.loadScheduledAlarms(context)
        for (spec in previous) {
            val intent = Intent(context, CampusNotificationAlarmReceiver::class.java).apply {
                action = spec.action
            }
            val existingPi = PendingIntent.getBroadcast(
                context,
                spec.requestCode,
                intent,
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
            )
            if (existingPi != null) {
                runCatching { alarmManager.cancel(existingPi) }
                runCatching { existingPi.cancel() }
            }
        }
    }

    private fun scheduleAlarmSafely(
        alarmManager: AlarmManager,
        triggerAtMillis: Long,
        pendingIntent: PendingIntent
    ) {
        val safeTrigger = triggerAtMillis.coerceAtLeast(System.currentTimeMillis() + 1_000L)
        val canExact = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching { alarmManager.canScheduleExactAlarms() }.getOrDefault(false)
        } else {
            true
        }

        if (canExact) {
            val exactResult = runCatching {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, safeTrigger, pendingIntent)
            }
            if (exactResult.isSuccess) return
        }

        runCatching {
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, safeTrigger, pendingIntent)
        }
    }

    private fun buildTabPendingIntent(
        context: Context,
        targetTab: String,
        requestCode: Int
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_WIDGET_TARGET_TAB, targetTab)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildMapFocusPendingIntent(
        context: Context,
        requestCode: Int,
        courseCode: String,
        section: String,
        weekday: Int,
        startTime: String,
        fallbackBuildingRaw: String
    ): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra(MainActivity.EXTRA_WIDGET_TARGET_TAB, MainActivity.WIDGET_TARGET_MAP)
            putExtra(MainActivity.EXTRA_MAP_COURSE_CODE, courseCode)
            putExtra(MainActivity.EXTRA_MAP_SECTION, section)
            putExtra(MainActivity.EXTRA_MAP_WEEKDAY, weekday)
            putExtra(MainActivity.EXTRA_MAP_START_TIME, startTime)
            putExtra(MainActivity.EXTRA_MAP_FALLBACK_BUILDING, fallbackBuildingRaw)
        }
        return PendingIntent.getActivity(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun estimateSlotClassWeight(startTime: String, endTime: String): Int {
        val sParts = startTime.split(":")
        val eParts = endTime.split(":")
        if (sParts.size != 2 || eParts.size != 2) return 1
        val sh = sParts[0].toIntOrNull() ?: return 1
        val sm = sParts[1].toIntOrNull() ?: return 1
        val eh = eParts[0].toIntOrNull() ?: return 1
        val em = eParts[1].toIntOrNull() ?: return 1
        val duration = ((eh * 60 + em) - (sh * 60 + sm)).coerceAtLeast(50)
        return when {
            duration >= 190 -> 4
            duration >= 140 -> 3
            duration >= 90 -> 2
            else -> 1
        }
    }

    private fun stableRequestCode(prefix: String, key: String): Int {
        val rawHash = ("$prefix:$key").hashCode() and 0x00FFFFFF
        return 200_000 + rawHash
    }

    private fun notificationIdFor(prefix: String, key: String, base: Int): Int {
        val rawHash = ("$prefix:$key").hashCode() and 0x00000FFF
        return base + rawHash
    }
}
