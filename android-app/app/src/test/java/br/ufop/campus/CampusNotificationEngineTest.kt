package br.ufop.campus

import android.content.SharedPreferences
import br.ufop.campus.core.ffi.CampusRepositoryHolder
import br.ufop.campus.core.ffi.InMemoryCampusRepository
import br.ufop.campus.core.ffi.NotificationPreferences
import br.ufop.campus.core.notifications.CampusNotificationPlanner
import br.ufop.campus.core.security.CampusCryptoEngine
import br.ufop.campus.core.notifications.CampusNotificationPlanner.LiveCardEventKind
import br.ufop.campus.core.notifications.CampusNotificationPlanner.NotificationAssessmentSnapshot
import br.ufop.campus.core.notifications.CampusNotificationPlanner.NotificationCourseSnapshot
import br.ufop.campus.core.notifications.CampusNotificationPlanner.NotificationPlanningInput
import br.ufop.campus.core.notifications.CampusNotificationPlanner.NotificationSlotSnapshot
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.io.File
import java.time.LocalDateTime
import javax.crypto.spec.SecretKeySpec

class CampusNotificationEngineTest {

    private var tempStateDir: File? = null

    @Before
    fun setUpTestCryptoKey() {
        val syntheticKeyBytes = ByteArray(32) { idx -> (0xA5 xor (idx * 7 + 3)).toByte() }
        CampusCryptoEngine.setTestKeyOverrideForJvm(SecretKeySpec(syntheticKeyBytes, "AES"))
    }

    @After
    fun tearDown() {
        CampusCryptoEngine.setTestKeyOverrideForJvm(null)
        tempStateDir?.deleteRecursively()
        tempStateDir = null
    }

    private fun newTempStateFile(name: String): File {
        val dir = tempStateDir ?: kotlin.io.path.createTempDirectory("ufop_notif_test_").toFile().also {
            tempStateDir = it
        }
        return File(dir, name)
    }

    private fun samplePlanningInput(
        cancelledKeys: Set<String> = emptySet(),
        assessments: List<NotificationAssessmentSnapshot> = emptyList()
    ): NotificationPlanningInput {
        val courses = listOf(
            NotificationCourseSnapshot(
                courseId = "c_aed1",
                code = "BCC202",
                name = "Estrutura de Dados I",
                professor = "Prof. Carlos",
                buildingAndRoom = "ICEB · Sala 14",
                shortLocation = "ICEB 14",
                currentAbsences = 2,
                maxAbsences = 15,
                section = "T11"
            ),
            NotificationCourseSnapshot(
                courseId = "c_calc",
                code = "MTM122",
                name = "Cálculo Diferencial",
                professor = "Profa. Ana",
                buildingAndRoom = "ICEB · Sala 10",
                shortLocation = "ICEB 10",
                currentAbsences = 0,
                maxAbsences = 15,
                section = "T12"
            )
        )
        // 2026-11-30 é segunda-feira (dayOfWeek = 1); 2026-12-01 é terça-feira (dayOfWeek = 2)
        val slots = listOf(
            NotificationSlotSnapshot(
                slotId = "slot_mon_1",
                courseId = "c_aed1",
                dayOfWeek = 1,
                startTime = "13:30",
                endTime = "15:10",
                building = "ICEB",
                room = "14",
                shortLocation = "ICEB 14",
                classWeight = 2
            ),
            NotificationSlotSnapshot(
                slotId = "slot_mon_2",
                courseId = "c_calc",
                dayOfWeek = 1,
                startTime = "15:20",
                endTime = "17:00",
                building = "ICEB",
                room = "10",
                shortLocation = "ICEB 10",
                classWeight = 2
            )
        )
        return NotificationPlanningInput(
            activeSemester = "2026/2",
            courses = courses,
            slots = slots,
            assessments = assessments,
            cancelledSlotDateKeys = cancelledKeys
        )
    }

    @Test
    fun classRemindersHonorLeadMinutesAndSkipCancelledOccurrences() {
        val mondayMorning = LocalDateTime.of(2026, 11, 30, 8, 0)
        // Segunda-feira 15:20 foi cancelada; deve planejar apenas a aula das 13:30
        val input = samplePlanningInput(cancelledKeys = setOf("slot_mon_2@2026-11-30"))

        listOf(10, 15, 30).forEach { lead ->
            val plan = CampusNotificationPlanner.planAll(
                input = input,
                now = mondayMorning,
                prefs = NotificationPreferences(classLeadMinutes = lead),
                horizonDays = 2
            )
            assertEquals(1, plan.classReminders.size)
            val reminder = plan.classReminders.single()
            assertEquals(LocalDateTime.of(2026, 11, 30, 13, 30).minusMinutes(lead.toLong()), reminder.triggerAt)
            assertEquals("Estrutura de Dados I em $lead min", reminder.title)
            assertEquals("ICEB · Sala 14 · 13:30–15:10 · Prof. Carlos", reminder.body)
            assertFalse(plan.classReminders.any { it.slotId == "slot_mon_2" })
        }

        assertEquals(
            "ICEB · Sala 14",
            CampusNotificationPlanner.formatRoomPill("ICEB", "14", "", "", isEad = false)
        )
        assertEquals(
            "ICEB · Sala 14",
            CampusNotificationPlanner.formatRoomPill("ICEB", "Sala 14", "", "", isEad = false)
        )
        assertEquals(
            "ICEB · Lab. 02",
            CampusNotificationPlanner.formatRoomPill("ICEB", "Lab. 02", "", "", isEad = false)
        )
    }

    @Test
    fun liveCardTracksPreClassOngoingProgressAndEndEvent() {
        val input = samplePlanningInput()
        val prefs = NotificationPreferences(classLeadMinutes = 15, liveActivityEnabled = true)

        // 10 minutos antes da aula (janela pré-aula de 15 min)
        val preClassNow = LocalDateTime.of(2026, 11, 30, 13, 20)
        val preCard = CampusNotificationPlanner.resolveCurrentLiveCard(input, preClassNow, prefs)
        assertNotNull(preCard)
        assertEquals("em 10 min", preCard?.countdownLabel)
        assertFalse(preCard?.isOngoing ?: true)
        assertEquals(0, preCard?.progressPercent)
        assertEquals("ICEB · Sala 14", preCard?.roomPill)

        // Metade da aula (13:30 -> 15:10 = 100 min; às 14:20 = 50%)
        val midClassNow = LocalDateTime.of(2026, 11, 30, 14, 20)
        val ongoingCard = CampusNotificationPlanner.resolveCurrentLiveCard(input, midClassNow, prefs)
        assertNotNull(ongoingCard)
        assertEquals("AGORA", ongoingCard?.countdownLabel)
        assertTrue(ongoingCard?.isOngoing ?: false)
        assertEquals(50, ongoingCard?.progressPercent)

        // Fora da janela (manhã sem aula iminente)
        val morningCard = CampusNotificationPlanner.resolveCurrentLiveCard(
            input,
            LocalDateTime.of(2026, 11, 30, 8, 0),
            prefs
        )
        assertNull(morningCard)

        // Plano inclui evento END exatamente ao final da aula
        val plan = CampusNotificationPlanner.planAll(
            input = input,
            now = LocalDateTime.of(2026, 11, 30, 13, 0),
            prefs = prefs,
            horizonDays = 1
        )
        val endEvents = plan.liveCardEvents.filter {
            it.slotId == "slot_mon_1" && it.kind == LiveCardEventKind.END
        }
        assertEquals(1, endEvents.size)
        assertEquals(LocalDateTime.of(2026, 11, 30, 15, 10), endEvents.single().triggerAt)
    }

    @Test
    fun morningSummaryFiresAt0730OnlyOnActiveWeekdaysAndStaysSilentOnWeekendsAndFreeDays() {
        val saturdayMorning = LocalDateTime.of(2026, 11, 28, 6, 0) // Sábado
        // Terça-feira (2026-12-01) é dia livre no samplePlanningInput; apenas segunda (2026-11-30) tem 2 aulas
        val assessments = listOf(
            NotificationAssessmentSnapshot(
                id = "a1",
                courseId = "c_aed1",
                courseCode = "BCC202",
                courseName = "Estrutura de Dados I",
                title = "Prova 1",
                kind = "Prova",
                dueDate = "2026-12-03",
                dueTime = "13:30",
                completed = false
            )
        )
        val plan = CampusNotificationPlanner.planAll(
            input = samplePlanningInput(assessments = assessments),
            now = saturdayMorning,
            prefs = NotificationPreferences(morningSummaryEnabled = true),
            horizonDays = 5 // Sáb 28, Dom 29, Seg 30, Ter 01, Qua 02
        )

        assertEquals(1, plan.morningSummaries.size)
        val summary = plan.morningSummaries.single()
        assertEquals(LocalDateTime.of(2026, 11, 30, 7, 30), summary.triggerAt)
        assertEquals("Segunda no campus: 2 aulas", summary.title)
        assertTrue(summary.body.contains("13:30 ICEB 14 · 15:20 ICEB 10"))
        assertTrue(summary.body.contains("1 prova esta semana"))

        // Se todas as aulas da segunda-feira forem canceladas, não deve disparar resumo matinal
        val allCancelledPlan = CampusNotificationPlanner.planAll(
            input = samplePlanningInput(
                cancelledKeys = setOf("slot_mon_1@2026-11-30", "slot_mon_2@2026-11-30"),
                assessments = assessments
            ),
            now = saturdayMorning,
            prefs = NotificationPreferences(morningSummaryEnabled = true),
            horizonDays = 5
        )
        assertTrue(allCancelledPlan.morningSummaries.isEmpty())
    }

    @Test
    fun deadlineRemindersTriggerAt0900ThreeAndOneDayBeforeAndSupportEveningSnooze() {
        val now = LocalDateTime.of(2026, 11, 28, 8, 0)
        val pendingExam = NotificationAssessmentSnapshot(
            id = "exam_1",
            courseId = "c_aed1",
            courseCode = "BCC202",
            courseName = "Estrutura de Dados I",
            title = "Prova 2",
            kind = "Prova",
            dueDate = "2026-12-03",
            dueTime = "13:30",
            completed = false
        )
        val completedWork = NotificationAssessmentSnapshot(
            id = "work_done",
            courseId = "c_calc",
            courseCode = "MTM122",
            courseName = "Cálculo Diferencial",
            title = "Lista 3",
            kind = "Trabalho",
            dueDate = "2026-12-02",
            dueTime = "23:59",
            completed = true
        )

        val plan = CampusNotificationPlanner.planAll(
            input = samplePlanningInput(assessments = listOf(pendingExam, completedWork)),
            now = now,
            prefs = NotificationPreferences(deadlineReminderEnabled = true),
            horizonDays = 7
        )

        assertEquals(2, plan.deadlineReminders.size)
        val d3 = plan.deadlineReminders.first { it.daysRemaining == 3 }
        val d1 = plan.deadlineReminders.first { it.daysRemaining == 1 }

        assertEquals(LocalDateTime.of(2026, 11, 30, 9, 0), d3.triggerAt)
        assertEquals("Prova 2 em 3 dias", d3.title)
        assertEquals("BCC202 · Prova às 13:30 · 3 dias restantes", d3.body)

        assertEquals(LocalDateTime.of(2026, 12, 2, 9, 0), d1.triggerAt)
        assertEquals("Prova 2 amanhã", d1.title)
        assertEquals("BCC202 · Prova às 13:30 · 1 dia restante", d1.body)

        // Adiamento ("Lembrar à noite") às 09:15 agenda para 20:00 do mesmo dia
        val snoozedToday = CampusNotificationPlanner.planEveningSnooze(
            now = LocalDateTime.of(2026, 12, 2, 9, 15),
            assessmentId = d1.assessmentId,
            courseId = d1.courseId,
            title = d1.title,
            body = d1.body
        )
        assertEquals(LocalDateTime.of(2026, 12, 2, 20, 0), snoozedToday.triggerAt)
        assertEquals("deadline_snooze_exam_1", snoozedToday.id)
    }

    @Test
    fun absenceAlertsFireOnceAt65And85PercentWithDedupAndRearmOnDrop() {
        val baseCourse = NotificationCourseSnapshot(
            courseId = "c_1",
            code = "BCC202",
            name = "Estrutura de Dados I",
            professor = "Prof. Carlos",
            buildingAndRoom = "ICEB 14",
            shortLocation = "ICEB 14",
            currentAbsences = 9, // 9/15 = 60% -> abaixo de 65%
            maxAbsences = 15
        )
        val slots = mapOf(
            "c_1" to listOf(
                NotificationSlotSnapshot("s1", "c_1", 1, "13:30", "15:10", "ICEB", "14", "ICEB 14", classWeight = 2)
            )
        )

        // 1) Abaixo de 65% -> nenhum alerta
        val resBelow = CampusNotificationPlanner.evaluateAbsenceAlerts(
            activeSemester = "2026/2",
            courses = listOf(baseCourse),
            slotsByCourse = slots,
            alreadyAlertedKeys = emptySet(),
            enabled = true
        )
        assertTrue(resBelow.alertsToSend.isEmpty())

        // 2) Sobe para 67% (10/15) -> dispara limiar 65%
        val res65 = CampusNotificationPlanner.evaluateAbsenceAlerts(
            activeSemester = "2026/2",
            courses = listOf(baseCourse.copy(currentAbsences = 10)),
            slotsByCourse = slots,
            alreadyAlertedKeys = resBelow.updatedAlertedKeys,
            enabled = true
        )
        assertEquals(1, res65.alertsToSend.size)
        assertEquals(65, res65.alertsToSend.single().thresholdPercent)
        assertEquals("Limite próximo em Estrutura de Dados I", res65.alertsToSend.single().title)
        assertEquals("10/15 faltas usadas (67%). Restam 2 dias de aula antes de reprovar por falta.", res65.alertsToSend.single().body)

        // 3) Mesmo patamar (11/15 = 73%) com chaves persistidas -> sem duplicação
        val res73 = CampusNotificationPlanner.evaluateAbsenceAlerts(
            activeSemester = "2026/2",
            courses = listOf(baseCourse.copy(currentAbsences = 11)),
            slotsByCourse = slots,
            alreadyAlertedKeys = res65.updatedAlertedKeys,
            enabled = true
        )
        assertTrue(res73.alertsToSend.isEmpty())

        // 4) Sobe para 87% (13/15) -> dispara limiar 85%
        val res85 = CampusNotificationPlanner.evaluateAbsenceAlerts(
            activeSemester = "2026/2",
            courses = listOf(baseCourse.copy(currentAbsences = 13)),
            slotsByCourse = slots,
            alreadyAlertedKeys = res73.updatedAlertedKeys,
            enabled = true
        )
        assertEquals(1, res85.alertsToSend.size)
        assertEquals(85, res85.alertsToSend.single().thresholdPercent)

        // 5) Reavaliação em 87% -> sem spam
        val res85Repeat = CampusNotificationPlanner.evaluateAbsenceAlerts(
            activeSemester = "2026/2",
            courses = listOf(baseCourse.copy(currentAbsences = 13)),
            slotsByCourse = slots,
            alreadyAlertedKeys = res85.updatedAlertedKeys,
            enabled = true
        )
        assertTrue(res85Repeat.alertsToSend.isEmpty())

        // 6) Usuário corrige faltas para baixo (<65%) e depois volta a >=85% em salto único
        val resDropped = CampusNotificationPlanner.evaluateAbsenceAlerts(
            activeSemester = "2026/2",
            courses = listOf(baseCourse.copy(currentAbsences = 5)),
            slotsByCourse = slots,
            alreadyAlertedKeys = res85Repeat.updatedAlertedKeys,
            enabled = true
        )
        assertTrue(resDropped.alertsToSend.isEmpty())
        assertTrue(resDropped.updatedAlertedKeys.isEmpty())

        val resJumpTo85 = CampusNotificationPlanner.evaluateAbsenceAlerts(
            activeSemester = "2026/2",
            courses = listOf(baseCourse.copy(currentAbsences = 14)),
            slotsByCourse = slots,
            alreadyAlertedKeys = resDropped.updatedAlertedKeys,
            enabled = true
        )
        assertEquals(1, resJumpTo85.alertsToSend.size)
        assertEquals(85, resJumpTo85.alertsToSend.single().thresholdPercent)
        assertEquals(setOf("2026/2_c_1_65", "2026/2_c_1_85"), resJumpTo85.updatedAlertedKeys)
    }

    @Test
    fun notificationPreferencesNormalizePersistRestoreAndWipeCorrectly() {
        val normalized = NotificationPreferences(classLeadMinutes = 42).normalized()
        assertEquals(15, normalized.classLeadMinutes)

        val stateFile = newTempStateFile("campus_state_notif.bin")
        val repo1 = InMemoryCampusRepository(stateFile = stateFile)

        // Padrões iniciais
        assertEquals(NotificationPreferences(), repo1.getNotificationPreferences())

        val custom = NotificationPreferences(
            classReminderEnabled = false,
            classLeadMinutes = 30,
            liveActivityEnabled = false,
            deadlineReminderEnabled = true,
            absenceAlertEnabled = false,
            morningSummaryEnabled = false
        )
        repo1.setNotificationPreferences(custom)

        // Recarga fria a partir do snapshot criptografado em disco
        val repo2 = InMemoryCampusRepository(stateFile = stateFile)
        assertEquals(custom, repo2.getNotificationPreferences())

        // Round-trip em backup (.ufopbackup)
        val backupJson = repo2.exportFullAccountSnapshotJson()
        val repo3 = InMemoryCampusRepository(stateFile = newTempStateFile("campus_state_restore.bin"))
        assertTrue(repo3.restoreFullAccountSnapshotJson(backupJson))
        assertEquals(custom, repo3.getNotificationPreferences())

        // Sync bidirecional via CampusRepositoryHolder + SharedPreferences
        val memPrefs = InMemorySharedPreferences()
        var rescheduleCalls = 0
        val holderUpdated = custom.copy(classReminderEnabled = true, classLeadMinutes = 10)
        CampusRepositoryHolder.saveNotificationPreferencesToPrefs(
            prefs = memPrefs,
            repository = repo3,
            settings = holderUpdated,
            onReschedule = { rescheduleCalls += 1 }
        )
        assertEquals(1, rescheduleCalls)
        assertEquals(holderUpdated, repo3.getNotificationPreferences())
        assertEquals(holderUpdated, CampusRepositoryHolder.loadNotificationPreferencesFromPrefs(memPrefs, repo3))

        // Wipe no logout reseta para padrões
        repo3.wipeAllUserDataForLogout()
        assertEquals(NotificationPreferences(), repo3.getNotificationPreferences())

        // Backup legado sem a chave "notificationPreferences" restaura padrões sem falhar
        val legacyJson = backupJson.replace("\"notificationPreferences\"", "\"legacyIgnoredNotificationPrefs\"")
        assertTrue(repo3.restoreFullAccountSnapshotJson(legacyJson))
        assertEquals(NotificationPreferences(), repo3.getNotificationPreferences())
    }

    private class InMemorySharedPreferences : SharedPreferences {
        private val map = linkedMapOf<String, Any?>()

        override fun getAll(): MutableMap<String, *> = map.toMutableMap()
        override fun getString(key: String?, defValue: String?): String? = map[key] as? String ?: defValue
        override fun getStringSet(key: String?, defValues: MutableSet<String>?): MutableSet<String>? = defValues
        override fun getInt(key: String?, defValue: Int): Int = map[key] as? Int ?: defValue
        override fun getLong(key: String?, defValue: Long): Long = map[key] as? Long ?: defValue
        override fun getFloat(key: String?, defValue: Float): Float = map[key] as? Float ?: defValue
        override fun getBoolean(key: String?, defValue: Boolean): Boolean = map[key] as? Boolean ?: defValue
        override fun contains(key: String?): Boolean = map.containsKey(key)
        override fun registerOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit
        override fun unregisterOnSharedPreferenceChangeListener(listener: SharedPreferences.OnSharedPreferenceChangeListener?) = Unit

        override fun edit(): SharedPreferences.Editor = object : SharedPreferences.Editor {
            private val pending = linkedMapOf<String, Any?>()
            private var clearAll = false

            override fun putString(key: String?, value: String?) = apply { if (key != null) pending[key] = value }
            override fun putStringSet(key: String?, values: MutableSet<String>?) = apply { if (key != null) pending[key] = values }
            override fun putInt(key: String?, value: Int) = apply { if (key != null) pending[key] = value }
            override fun putLong(key: String?, value: Long) = apply { if (key != null) pending[key] = value }
            override fun putFloat(key: String?, value: Float) = apply { if (key != null) pending[key] = value }
            override fun putBoolean(key: String?, value: Boolean) = apply { if (key != null) pending[key] = value }
            override fun remove(key: String?) = apply { if (key != null) pending[key] = null }
            override fun clear() = apply { clearAll = true }
            override fun apply() { commit() }
            override fun commit(): Boolean {
                if (clearAll) map.clear()
                pending.forEach { (k, v) -> if (v == null) map.remove(k) else map[k] = v }
                return true
            }
        }
    }
}
