package br.ufop.campus.core.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationManagerCompat
import br.ufop.campus.core.ffi.AttendanceStatus
import br.ufop.campus.core.ffi.CampusRepositoryHolder
import br.ufop.campus.widget.VidaAcademicaWidgetProvider
import java.time.LocalDate
import java.time.LocalDateTime

/**
 * Dispara notificações agendadas via AlarmManager e atualiza widgets em fronteiras de aula.
 */
class CampusNotificationAlarmReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext ?: context
        val repository = CampusRepositoryHolder.getOrCreate(appContext)
        val prefs = CampusRepositoryHolder.loadNotificationPreferences(appContext, repository)
        CampusNotificationChannels.ensureChannels(appContext)

        when (intent.action) {
            CampusNotificationScheduler.ACTION_ALARM_CLASS_REMINDER -> {
                if (!prefs.classReminderEnabled) return
                val slotId = intent.getStringExtra(CampusNotificationScheduler.EXTRA_SLOT_ID).orEmpty()
                val isoDate = intent.getStringExtra(CampusNotificationScheduler.EXTRA_ISO_DATE).orEmpty()
                if (slotId.isBlank() || isoDate.isBlank()) return

                // Revalida no estado atual se a aula não foi cancelada ou excluída antes de notificar
                val dayItems = runCatching { repository.getAgendaDay(isoDate).items }.getOrDefault(emptyList())
                val matching = dayItems.firstOrNull { it.slotId == slotId }
                if (matching == null || matching.status == AttendanceStatus.CANCELLED) return

                CampusNotificationScheduler.postClassReminder(
                    context = appContext,
                    notifId = intent.getIntExtra(CampusNotificationScheduler.EXTRA_NOTIF_ID, 42001),
                    slotId = slotId,
                    courseId = intent.getStringExtra(CampusNotificationScheduler.EXTRA_COURSE_ID).orEmpty(),
                    courseCode = intent.getStringExtra(CampusNotificationScheduler.EXTRA_COURSE_CODE).orEmpty(),
                    section = intent.getStringExtra(CampusNotificationScheduler.EXTRA_COURSE_SECTION).orEmpty(),
                    isoDate = isoDate,
                    dayOfWeek = intent.getIntExtra(CampusNotificationScheduler.EXTRA_WEEKDAY, 1),
                    startTime = intent.getStringExtra(CampusNotificationScheduler.EXTRA_START_TIME).orEmpty(),
                    fallbackBuildingRaw = intent.getStringExtra(CampusNotificationScheduler.EXTRA_FALLBACK_BUILDING).orEmpty(),
                    title = intent.getStringExtra(CampusNotificationScheduler.EXTRA_TITLE).orEmpty(),
                    body = intent.getStringExtra(CampusNotificationScheduler.EXTRA_BODY).orEmpty()
                )
            }

            CampusNotificationScheduler.ACTION_ALARM_LIVE_CARD -> {
                val kindRaw = intent.getStringExtra(CampusNotificationScheduler.EXTRA_LIVE_KIND).orEmpty()
                val kind = runCatching { CampusNotificationPlanner.LiveCardEventKind.valueOf(kindRaw) }
                    .getOrDefault(CampusNotificationPlanner.LiveCardEventKind.UPDATE)

                if (!prefs.liveActivityEnabled || kind == CampusNotificationPlanner.LiveCardEventKind.END) {
                    CampusNotificationScheduler.cancelLiveCard(appContext)
                    runCatching { VidaAcademicaWidgetProvider.requestWidgetUpdate(appContext) }
                    return
                }

                val now = LocalDateTime.now()
                val input = CampusNotificationScheduler.extractPlanningInput(
                    repository = repository,
                    today = now.toLocalDate(),
                    horizonDays = 1
                )
                val activeLive = CampusNotificationPlanner.resolveCurrentLiveCard(
                    input = input,
                    now = now,
                    prefs = prefs
                )
                if (activeLive != null) {
                    CampusNotificationScheduler.postOrUpdateLiveCard(appContext, activeLive)
                } else {
                    CampusNotificationScheduler.cancelLiveCard(appContext)
                }
                runCatching { VidaAcademicaWidgetProvider.requestWidgetUpdate(appContext) }
            }

            CampusNotificationScheduler.ACTION_ALARM_DEADLINE -> {
                if (!prefs.deadlineReminderEnabled) return
                val assessmentId = intent.getStringExtra(CampusNotificationScheduler.EXTRA_ASSESSMENT_ID).orEmpty()
                if (assessmentId.isBlank()) return

                val allAssessments = runCatching { repository.listAllAssessments("", null) }.getOrDefault(emptyList())
                val matched = allAssessments.firstOrNull { it.assessment.id == assessmentId }
                if (matched == null || matched.assessment.completed) return

                CampusNotificationScheduler.postDeadlineReminder(
                    context = appContext,
                    notifId = intent.getIntExtra(CampusNotificationScheduler.EXTRA_NOTIF_ID, 43001),
                    assessmentId = assessmentId,
                    courseId = intent.getStringExtra(CampusNotificationScheduler.EXTRA_COURSE_ID).orEmpty(),
                    title = intent.getStringExtra(CampusNotificationScheduler.EXTRA_TITLE).orEmpty(),
                    body = intent.getStringExtra(CampusNotificationScheduler.EXTRA_BODY).orEmpty()
                )
            }

            CampusNotificationScheduler.ACTION_ALARM_MORNING_SUMMARY -> {
                if (!prefs.morningSummaryEnabled) return
                val isoDate = intent.getStringExtra(CampusNotificationScheduler.EXTRA_ISO_DATE)
                    ?: LocalDate.now().toString()
                val dayOverview = runCatching { repository.getAgendaDay(isoDate) }.getOrNull()
                val hasActiveClass = dayOverview?.items?.any {
                    it.slotId != null && it.status != AttendanceStatus.CANCELLED
                } == true
                if (!hasActiveClass) return

                CampusNotificationScheduler.postMorningSummary(
                    context = appContext,
                    notifId = intent.getIntExtra(CampusNotificationScheduler.EXTRA_NOTIF_ID, 45001),
                    title = intent.getStringExtra(CampusNotificationScheduler.EXTRA_TITLE).orEmpty(),
                    body = intent.getStringExtra(CampusNotificationScheduler.EXTRA_BODY).orEmpty()
                )
            }

            CampusNotificationScheduler.ACTION_ALARM_WIDGET_BOUNDARY -> {
                runCatching { VidaAcademicaWidgetProvider.requestWidgetUpdate(appContext) }
            }
        }
    }
}

/**
 * Processa ações inline das notificações ("Vou à aula", "Vou faltar", "Marcar concluída", "Lembrar à noite").
 */
class CampusNotificationActionReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val appContext = context.applicationContext ?: context
        val repository = CampusRepositoryHolder.getOrCreate(appContext)
        val notifId = intent.getIntExtra(CampusNotificationScheduler.EXTRA_NOTIF_ID, -1)

        when (intent.action) {
            CampusNotificationScheduler.ACTION_INLINE_ATTEND -> {
                val slotId = intent.getStringExtra(CampusNotificationScheduler.EXTRA_SLOT_ID).orEmpty()
                val isoDate = intent.getStringExtra(CampusNotificationScheduler.EXTRA_ISO_DATE).orEmpty()
                val title = intent.getStringExtra(CampusNotificationScheduler.EXTRA_TITLE).orEmpty()
                if (slotId.isNotBlank() && isoDate.isNotBlank()) {
                    runCatching {
                        repository.saveClassCheckIn(slotId, isoDate, AttendanceStatus.ATTENDING, null)
                    }.onSuccess {
                        if (notifId > 0) {
                            CampusNotificationScheduler.postClassCheckInConfirmation(
                                context = appContext,
                                notifId = notifId,
                                title = title.ifBlank { "Aula confirmada" },
                                confirmationText = "✓ Presença confirmada"
                            )
                        }
                        runCatching { VidaAcademicaWidgetProvider.requestWidgetUpdate(appContext) }
                    }
                }
            }

            CampusNotificationScheduler.ACTION_INLINE_ABSENT -> {
                val slotId = intent.getStringExtra(CampusNotificationScheduler.EXTRA_SLOT_ID).orEmpty()
                val isoDate = intent.getStringExtra(CampusNotificationScheduler.EXTRA_ISO_DATE).orEmpty()
                val title = intent.getStringExtra(CampusNotificationScheduler.EXTRA_TITLE).orEmpty()
                if (slotId.isNotBlank() && isoDate.isNotBlank()) {
                    runCatching {
                        repository.saveClassCheckIn(slotId, isoDate, AttendanceStatus.ABSENT, null)
                    }.onSuccess {
                        if (notifId > 0) {
                            CampusNotificationScheduler.postClassCheckInConfirmation(
                                context = appContext,
                                notifId = notifId,
                                title = title.ifBlank { "Falta registrada" },
                                confirmationText = "Falta registrada na agenda"
                            )
                        }
                        runCatching {
                            CampusNotificationScheduler.checkAndPostAbsenceAlerts(appContext, repository)
                        }
                        runCatching { VidaAcademicaWidgetProvider.requestWidgetUpdate(appContext) }
                    }
                }
            }

            CampusNotificationScheduler.ACTION_INLINE_COMPLETE_ASSESSMENT -> {
                val assessmentId = intent.getStringExtra(CampusNotificationScheduler.EXTRA_ASSESSMENT_ID).orEmpty()
                val title = intent.getStringExtra(CampusNotificationScheduler.EXTRA_TITLE).orEmpty()
                if (assessmentId.isNotBlank()) {
                    val exists = runCatching {
                        repository.listAllAssessments("", null).any { it.assessment.id == assessmentId }
                    }.getOrDefault(false)
                    if (exists) {
                        runCatching {
                            repository.toggleAssessmentCompleted(assessmentId, true)
                        }.onSuccess {
                            if (notifId > 0) {
                                CampusNotificationScheduler.postDeadlineCompletedConfirmation(
                                    context = appContext,
                                    notifId = notifId,
                                    title = title.ifBlank { "Avaliação concluída" }
                                )
                            }
                            runCatching { VidaAcademicaWidgetProvider.requestWidgetUpdate(appContext) }
                            runCatching { CampusNotificationScheduler.onRepositoryStateChanged(appContext) }
                        }
                    }
                }
            }

            CampusNotificationScheduler.ACTION_INLINE_SNOOZE_EVENING -> {
                val assessmentId = intent.getStringExtra(CampusNotificationScheduler.EXTRA_ASSESSMENT_ID).orEmpty()
                val courseId = intent.getStringExtra(CampusNotificationScheduler.EXTRA_COURSE_ID).orEmpty()
                val title = intent.getStringExtra(CampusNotificationScheduler.EXTRA_TITLE).orEmpty()
                val body = intent.getStringExtra(CampusNotificationScheduler.EXTRA_BODY).orEmpty()
                if (notifId > 0) {
                    runCatching { NotificationManagerCompat.from(appContext).cancel(notifId) }
                }
                if (assessmentId.isNotBlank()) {
                    runCatching {
                        CampusNotificationScheduler.scheduleEveningSnooze(
                            context = appContext,
                            assessmentId = assessmentId,
                            courseId = courseId,
                            title = title,
                            body = body
                        )
                    }
                }
            }
        }
    }
}

/**
 * Reagenda alarmes locais e atualiza widgets após boot, mudança de data/hora ou fuso horário.
 */
class CampusNotificationBootReceiver : BroadcastReceiver() {

    private companion object {
        val RESCHEDULE_ACTIONS = setOf(
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_TIME_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_DATE_CHANGED
        )
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in RESCHEDULE_ACTIONS) return
        val appContext = context.applicationContext ?: context
        val pendingResult = goAsync()
        Thread(
            {
                try {
                    runCatching {
                        CampusNotificationScheduler.rescheduleAll(appContext)
                    }
                    runCatching {
                        VidaAcademicaWidgetProvider.requestWidgetUpdate(appContext)
                    }
                } finally {
                    pendingResult.finish()
                }
            },
            "ufop-notif-boot"
        ).start()
    }
}
