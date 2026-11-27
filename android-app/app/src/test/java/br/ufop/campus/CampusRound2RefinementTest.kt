package br.ufop.campus

import br.ufop.campus.core.ffi.InMemoryCampusRepository
import br.ufop.campus.widget.WidgetAccentThemeHelper
import br.ufop.campus.widget.WidgetScheduleLoader
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.time.LocalDate
import java.time.LocalTime

class CampusRound2RefinementTest {

    @get:Rule
    val tempFolder = TemporaryFolder()

    @Test
    fun autoDetectSyllabus_matchesByUfopCode_andImportsAssessmentsIntoTargetCourseOnly() {
        val repo = InMemoryCampusRepository(
            stateFile = File(tempFolder.root, "syllabus_auto_match.json")
        )
        val elt = repo.createCourse(
            semester = "2026.2",
            departmentId = "deelt",
            name = "ELT201 Análise de Circuitos Elétricos",
            professor = "Prof. Responsável",
            workloadHours = 60
        )
        val mtm = repo.createCourse(
            semester = "2026.2",
            departmentId = "demat",
            name = "MTM125 Equações Diferenciais",
            professor = "Prof. Marcos",
            workloadHours = 60
        )
        val initialMtmCount = repo.getDisciplineDetail(mtm.courseId).assessments.size

        val syllabusText = """
            PLANO DE ENSINO
            Disciplina: ELT201 - Análise de Circuitos Elétricos
            Docente: Prof. Ricardo Mendes
            Distribuição dos pontos: Prova 1 (40 pts), Prova 2 (60 pts)
            15/10/2026 - Prova 1 (40 pts)
            20/11/2026 - Prova 2 (60 pts)
        """.trimIndent()

        val report = repo.importSyllabusTextAutoDetectCourse("2026.2", syllabusText)

        assertTrue(report.matchedCourse)
        assertEquals(elt.courseId, report.matchedCourseId)
        assertTrue(report.importedCount >= 2)
        assertNull(report.errorMessage)
        assertTrue(repo.getDisciplineDetail(elt.courseId).assessments.size >= 2)
        assertEquals(initialMtmCount, repo.getDisciplineDetail(mtm.courseId).assessments.size)
    }

    @Test
    fun autoDetectSyllabus_rejectsUnenrolledCourse_withoutMutatingState() {
        val repo = InMemoryCampusRepository(
            stateFile = File(tempFolder.root, "syllabus_auto_reject.json")
        )
        repo.createCourse(
            semester = "2026.2",
            departmentId = "deelt",
            name = "ELT201 Análise de Circuitos Elétricos",
            professor = "Prof. Ricardo",
            workloadHours = 60
        )
        val countBefore = repo.listAllAssessments("2026.2").size

        val unenrolledSyllabus = """
            PLANO DE ENSINO
            Disciplina: FIS999 - FÍSICA QUÂNTICA AVANÇADA
            Docente: Prof. Heisenberg
            15/10/2026 - Prova 1 (50 pts)
            22/11/2026 - Seminário Final (50 pts)
        """.trimIndent()

        val report = repo.importSyllabusTextAutoDetectCourse("2026.2", unenrolledSyllabus)

        assertFalse(report.matchedCourse)
        assertEquals(0, report.importedCount)
        assertNotNull(report.errorMessage)
        assertEquals(countBefore, repo.listAllAssessments("2026.2").size)
    }

    @Test
    fun autoDetectSyllabus_respectsActiveSemesterIsolation() {
        val repo = InMemoryCampusRepository(
            stateFile = File(tempFolder.root, "syllabus_semester_isolation.json")
        )
        val qui = repo.createCourse(
            semester = "2027.1",
            departmentId = "dequi",
            name = "QUI301 Química Orgânica",
            professor = "Prof.ª Carla",
            workloadHours = 60
        )
        val quiSyllabus = """
            PLANO DE ENSINO
            Disciplina: QUI301 - Química Orgânica
            Docente: Prof.ª Carla
            18/05/2027 - Prova 1 (50 pts)
            29/06/2027 - Prova 2 (50 pts)
        """.trimIndent()

        val wrongSemesterReport = repo.importSyllabusTextAutoDetectCourse("2026.2", quiSyllabus)
        assertFalse(wrongSemesterReport.matchedCourse)
        assertEquals(0, wrongSemesterReport.importedCount)

        val rightSemesterReport = repo.importSyllabusTextAutoDetectCourse("2027.1", quiSyllabus)
        assertTrue(rightSemesterReport.matchedCourse)
        assertEquals(qui.courseId, rightSemesterReport.matchedCourseId)
        assertTrue(rightSemesterReport.importedCount >= 2)
    }

    @Test
    fun widgetAccentThemeHelper_resolvesAllNinePalettesInLightAndDark_withReadableYellowDarkOnPrimary() {
        val keys = listOf(
            "GREEN",
            "RED",
            "ORANGE",
            "PURPLE",
            "YELLOW",
            "CYAN",
            "BROWN",
            "PINK",
            "BLUE"
        )

        keys.forEach { key ->
            listOf(false, true).forEach { isDark ->
                val palette = WidgetAccentThemeHelper.resolvePalette(key, isDark)
                assertTrue("$key ($isDark) canvas != surface1", palette.canvas != palette.surface1)
                assertTrue("$key ($isDark) surface1 != surface2", palette.surface1 != palette.surface2)
                assertTrue("$key ($isDark) canvas != surface2", palette.canvas != palette.surface2)
            }
        }

        val defaultLight = WidgetAccentThemeHelper.resolvePalette(null, isDark = false)
        val redLight = WidgetAccentThemeHelper.resolvePalette("RED", isDark = false)
        assertEquals(redLight, defaultLight)

        val yellowDark = WidgetAccentThemeHelper.resolvePalette("YELLOW", isDark = true)
        assertEquals(0xFF211602.toInt(), yellowDark.onPrimary)
    }

    @Test
    fun widgetScheduleLoader_buildsCorridorSnapshot_forOngoingUpcomingFollowingDeadlineAndFreeDay() {
        val root = JSONObject().apply {
            put("studentProfile", JSONObject().apply {
                put("activeSemester", "2026.2")
            })
            put("courses", JSONArray().apply {
                put(JSONObject().apply {
                    put("courseId", "c_edo")
                    put("name", "Introdução às Equações Diferenciais")
                    put("semester", "2026.2")
                    put("buildingAndRoom", "ICEB · Sala 14")
                    put("shortLocation", "ICEB · S. 14")
                })
                put(JSONObject().apply {
                    put("courseId", "c_calc3")
                    put("name", "Cálculo Diferencial III")
                    put("semester", "2026.2")
                    put("buildingAndRoom", "ICEB · Sala 10")
                    put("shortLocation", "ICEB 10")
                })
            })
            put("slots", JSONArray().apply {
                put(JSONObject().apply {
                    put("slotId", "slot_edo_wed")
                    put("courseId", "c_edo")
                    put("dayOfWeek", 3)
                    put("startTime", "13:30")
                    put("endTime", "15:10")
                    put("building", "ICEB")
                    put("room", "Sala 14")
                    put("shortLocation", "ICEB · Sala 14")
                })
                put(JSONObject().apply {
                    put("slotId", "slot_calc_wed")
                    put("courseId", "c_calc3")
                    put("dayOfWeek", 3)
                    put("startTime", "15:20")
                    put("endTime", "17:00")
                    put("building", "ICEB")
                    put("room", "Sala 10")
                    put("shortLocation", "ICEB 10")
                })
            })
            put("sessions", JSONArray())
            put("assessments", JSONArray().apply {
                put(JSONObject().apply {
                    put("id", "ass_1")
                    put("courseId", "c_edo")
                    put("title", "Avaliação Circuitos")
                    put("dueDate", "2026-11-29")
                    put("dueTime", "18:00")
                    put("completed", false)
                })
            })
        }

        // Wednesday 2026-11-25 at 12:48 -> 42m before 13:30 class
        val upcoming = WidgetScheduleLoader.buildWidgetSnapshot(
            root = root,
            today = LocalDate.of(2026, 11, 25),
            now = LocalTime.of(12, 48)
        )
        assertEquals("QUA 25", upcoming.dayPillText)
        assertEquals(2, upcoming.todayClassesCount)
        assertFalse(upcoming.isOngoing)
        assertEquals("em 42m", upcoming.countdownLabel)
        assertNotNull(upcoming.spotlightClass)
        assertTrue(upcoming.spotlightClass!!.roomPill.contains("14"))
        assertNotNull(upcoming.followingClass)
        assertEquals("15:20", upcoming.followingClass!!.startTime)
        assertNotNull(upcoming.nextDeadline)

        // Wednesday 2026-11-25 at 14:00 -> during 13:30–15:10 class
        val ongoing = WidgetScheduleLoader.buildWidgetSnapshot(
            root = root,
            today = LocalDate.of(2026, 11, 25),
            now = LocalTime.of(14, 0)
        )
        assertTrue(ongoing.isOngoing)
        assertEquals("AGORA", ongoing.countdownLabel)
        assertEquals("AGORA", ongoing.compactCountdownBadge)

        // Saturday 2026-11-28 at 10:00 -> free day with pending deadline and next school day class
        val freeDay = WidgetScheduleLoader.buildWidgetSnapshot(
            root = root,
            today = LocalDate.of(2026, 11, 28),
            now = LocalTime.of(10, 0)
        )
        assertEquals(0, freeDay.todayClassesCount)
        assertEquals("Dia livre", freeDay.headerSummary)
        assertEquals("Sem aulas hoje", freeDay.freeDayMessage)
        assertNull(freeDay.spotlightClass)
        assertNotNull(freeDay.nextDeadline)
        assertNotNull(freeDay.nextSchoolDayClass)
    }
}
